package com.hopeless.infinity.world;

import com.hopeless.infinity.HopelessInfinity;
import com.hopeless.infinity.galaxy.Atmosphere;
import com.hopeless.infinity.planet.PlanetWorld;
import com.hopeless.infinity.planet.Terrain;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 0.10.0 BREATHING: a player in air that is not breathable (galaxy/Atmosphere: too little oxygen, too little pressure, or poison) loses
 * consciousness after the TIME OF USEFUL CONSCIOUSNESS for that air, then takes damage until back in breathable air (or, later, in a sealed
 * suit with oxygen). The time comes from the FAA's table (Advisory Circular 61-107B, Table 1-1, midpoints): the oxygen partial pressure is
 * turned into the Earth altitude with the same O2 (the International Standard Atmosphere, ICAO Doc 7488), and the table read there:
 * 14,000 ft 60 min (the start of 14 CFR 91.211's oxygen rule; CHOSEN), 18,000 ft 25 min, 22,000 ft 10 min, 25,000 ft 4 min, 28,000 ft 2.75 min,
 * 30,000 ft 1.5 min, 35,000 ft 45 s, 40,000 ft 17.5 s, 45,000 ft 12 s, 50,000 ft and above 7.5 s. Below the Armstrong limit (no pressure to keep
 * body fluids liquid) or in air with too much CO2 (Venus): 15 s (NASA: about 15 s of useful consciousness in vacuum). The bubbles on the
 * screen show what is left. Damage after: 2 hearts a second (drowning's rate). Back in good air the time refills at 10 x the rate it drained.
 * Probes: players short of air now, ticks short, damage given.
 */
public final class Breath {
    private Breath() {}
    static final double[][] TUC = {{14000, 3600}, {18000, 1500}, {22000, 600}, {25000, 240}, {28000, 165}, {30000, 90}, {35000, 45}, {40000, 17.5}, {45000, 12}, {50000, 7.5}};
    public static final double VACUUM_S = 15, REFILL = 10;
    static final Map<UUID, double[]> LEFT = new ConcurrentHashMap<>();   // {seconds left, the air's full time}
    public static volatile long shortTicks, damageHits; public static volatile int shortNow; static volatile Atmosphere homeAir; static volatile long homeSeed = Long.MIN_VALUE;
    public static volatile String lastAir = "";

    public static Atmosphere home(PlanetWorld w) { if (homeAir == null || homeSeed != w.seed) { homeAir = Atmosphere.of(w.system)[w.planet.index][0]; homeSeed = w.seed; } return homeAir; }
    /** Height (m) above the sea for a block y (Terrain.toYd's inverse, its 0.3 compression over 4,000 m undone). */
    public static double metres(double y) { double d = y - Terrain.SEA; return d <= 4000 ? d : 4000 + (d - 4000) / 0.3; }
    /** The Earth altitude (ft) whose air has this O2 partial pressure (ISA: troposphere to 11 km, then the isothermal layer). */
    public static double equivalentFt(double pO2) { double p = pO2 / 0.2095; double h;
        if (p >= 0.2234) h = (1 - Math.pow(p, 1 / 5.25588)) / 2.25577e-5; else h = 11000 - 6341.6 * Math.log(p / 0.2234); return h / 0.3048; }
    /** Seconds of useful consciousness in this air at this height; infinite when it is breathable. */
    public static double tuc(Atmosphere a, double hM) {
        if (a.breathableAt(hM)) return Double.POSITIVE_INFINITY; double p = a.pressureAt(hM);
        if (p < Atmosphere.ARMSTRONG_ATM || p * a.co2 > 0.04) return VACUUM_S;   // vacuum or NIOSH's IDLH for CO2 (4 %): out in seconds
        if (p * a.o2 > Atmosphere.O2_MAX_ATM) return 3600;   // too much oxygen: harm over hours, not minutes (NOAA)
        return tucPO2(p * a.o2); }
    /** Seconds of useful consciousness at an O2 partial pressure (atm), by the FAA table at the Earth altitude with that O2. */
    public static double tucPO2(double pO2) {
        double ft = equivalentFt(pO2); if (ft <= TUC[0][0]) return TUC[0][1]; if (ft >= TUC[TUC.length - 1][0]) return TUC[TUC.length - 1][1];
        for (int i = 1; i < TUC.length; i++) if (ft <= TUC[i][0]) { double f = (ft - TUC[i - 1][0]) / (TUC[i][0] - TUC[i - 1][0]); return Math.exp(Math.log(TUC[i - 1][1]) + f * (Math.log(TUC[i][1]) - Math.log(TUC[i - 1][1]))); }
        return VACUUM_S; }

    /** Server, each player each tick (end). air: the air there (null = vacuum: space); hM: height above the datum. */
    public static void tick(ServerPlayer p, Atmosphere air, double hM) {
        if (p.isCreative() || p.isSpectator() || p.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) return;
        if (p.getVehicle() instanceof com.hopeless.infinity.vehicle.Spaceplane) { LEFT.remove(p.getUUID()); p.setAirSupply(p.getMaxAirSupply()); return; }   // 0.13.0 the X-15's pressurised cockpit
        double full = air == null ? VACUUM_S : tuc(air, hM);
        if (!Double.isInfinite(full) && Suits.breathe(p)) { LEFT.remove(p.getUUID()); p.setAirSupply(p.getMaxAirSupply()); return; }   // 0.11.0: a sealed suit breathes for you (its tank spends only in bad air)
        double[] s = LEFT.computeIfAbsent(p.getUUID(), k -> new double[]{Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY});
        if (Double.isInfinite(full)) { if (Double.isFinite(s[0])) { s[0] += REFILL / 20.0; if (s[0] >= s[1]) { s[0] = Double.POSITIVE_INFINITY; s[1] = Double.POSITIVE_INFINITY; } p.setAirSupply(Double.isInfinite(s[0]) ? p.getMaxAirSupply() : (int) (p.getMaxAirSupply() * s[0] / s[1])); } return; }
        if (Double.isInfinite(s[0])) { s[0] = full; s[1] = full; } else if (full < s[1]) { s[0] = Math.min(s[0], full); s[1] = full; }   // worse air: the shorter time counts
        s[0] -= 1 / 20.0; shortTicks++; p.setAirSupply((int) Math.max(-19, p.getMaxAirSupply() * s[0] / s[1]));
        if (s[0] <= 0 && p.tickCount % 20 == 0) { p.hurt(p.damageSources().drown(), 2f); damageHits++; }
        if (p.tickCount % 100 == 0) lastAir = String.format("%s: %s, %.0f m, %.1f s of %.1f left", p.getName().getString(), air == null ? "vacuum" : String.format("%.3g atm O2 %.3g atm", air.pressureAt(hM), air.pressureAt(hM) * air.o2), hM, Math.max(0, s[0]), s[1]);
    }
    public static String probe() { int n = 0; for (double[] s : LEFT.values()) if (Double.isFinite(s[0])) n++; shortNow = n;
        Atmosphere a = homeAir; return String.format("breath: home air %s | short of air now %d, ticks short %d, damage hits %d%s", a == null ? "?" : a, n, shortTicks, damageHits, lastAir.isEmpty() ? "" : " | last: " + lastAir); }
}
