package com.hopeless.infinity.world;

import com.hopeless.infinity.HopelessInfinity;
import com.hopeless.infinity.planet.PlanetWorld;
import com.hopeless.infinity.sky.Ephemeris;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 0.8.0 THE PLANET'S OWN DAY: the system clock (seconds) = the world's game time / 20 + an offset (sleeping moves it on to the next
 * sunrise; saved with the world). Each planet face's Minecraft dayTime is set every tick so that Minecraft's daylight (sky brightness, mob
 * rules, beds) sees the star at the height the real sky has it at the player (or the face's centre when nobody is there): Minecraft's
 * sun angle for a dayTime is inverted to give the real star's altitude, morning or afternoon by whether it is rising. The day lasts the
 * planet's real solar day (owner, 2026-11-02: real rotation period); polar days and nights happen where the tilt gives them.
 * Probes: ticks, last altitude, the solar day, sleeps.
 */
public final class Clock {
    private Clock() {}
    public static volatile double offsetS; public static volatile long ticks, sleeps; public static volatile double lastAlt, lastT; public static volatile long lastDayTime;
    static volatile Ephemeris eph; static volatile long ephSeed = Long.MIN_VALUE;
    public static Ephemeris eph(PlanetWorld w) { if (w == null) return null; if (eph == null || ephSeed != w.seed) { eph = new Ephemeris(w); ephSeed = w.seed; } return eph; }
    public static double seconds(long gameTime, float pt) { return (gameTime + pt) / 20.0 + offsetS; }

    /** Saved offset (overworld data). */
    public static final class Data extends SavedData {
        double off; Data() {} Data(CompoundTag t) { off = t.getDouble("offset"); }
        @Override public CompoundTag save(CompoundTag t) { t.putDouble("offset", off); return t; }
    }
    static Data data(ServerLevel any) { return any.getServer().overworld().getDataStorage().computeIfAbsent(Data::new, Data::new, "hopelessinfinity_clock"); }
    public static void load(ServerLevel any) { offsetS = data(any).off; }

    /** Server, each planet face's tick (end). */
    public static void tick(ServerLevel l, int f, PlanetWorld w) {
        Ephemeris e = eph(w); if (e == null) return; double t = seconds(l.getServer().overworld().getGameTime(), 0);
        double x = w.cube.F / 2.0, z = w.cube.F / 2.0; ServerPlayer p = l.players().isEmpty() ? null : l.players().get(0); if (p != null) { x = p.getX(); z = p.getZ(); }
        long d = dayTime(e, f, x, z, t, l.getDayTime()); l.setDayTime(d); ticks++; lastT = t; lastDayTime = d;
    }
    /** The dayTime that gives Minecraft's sun the real star's altitude, kept near 'prev' (no jumps of a whole day). */
    public static long dayTime(Ephemeris e, int f, double x, double z, double t, long prev) { return dayTime(e.at(f, x, z, t), e.at(f, x, z, t + 30), prev); }
    /** 0.15.0: the same from the sky now and 30 s later (any body: the planet or its moon). */
    public static long dayTime(Ephemeris.At a, Ephemeris.At later, long prev) {
        double alt = a.sunAlt; lastAlt = alt;
        boolean rising = later.sunAlt > alt;
        // Minecraft: sin(altitude) = cos(2 pi angle), angle 0 = noon. Today's highest and lowest star (90 - |lat - dec|, -90 + |lat + dec|) map to Minecraft's
        // noon and midnight and the horizon to its horizon, so the time runs on without a jump at noon or midnight and sunrise and sunset fall exactly
        double sMax = Math.cos(a.lat - a.sunDec), sMin = -Math.cos(a.lat + a.sunDec), sa = Math.sin(alt);
        double xv = sa >= 0 ? (sMax > 0 ? sa / sMax : 1) : (sMin < 0 ? sa / -sMin : -1);
        double ang = Math.acos(Math.max(-1, Math.min(1, xv))) / (2 * Math.PI);
        if (rising) ang = 1 - ang;                                                            // morning: angle in (0.5, 1)
        double fr = invertAngle(ang); long inDay = Math.floorMod(Math.round((fr + 0.25) * 24000), 24000L);
        long base = prev - Math.floorMod(prev, 24000L), d = base + inDay;
        if (d < prev - 12000) d += 24000; else if (d > prev + 12000) d -= 24000;
        return d;
    }
    /** 0.15.0: the home moon's faces: Minecraft's daylight there from the star's real height at the player (or the face's centre); a locked
     *  moon's day is its month (22.9 days for seed 1). */
    public static volatile double lastMoonAlt; public static volatile long moonTicks;
    public static void tickMoon(ServerLevel l, int f, PlanetWorld mw) {
        Ephemeris e = eph(PlanetWorld.current()); if (e == null || mw == null) return; double t = seconds(l.getServer().overworld().getGameTime(), 0);
        double x = mw.cube.F / 2.0 + mw.cube.faceX0(f), z = mw.cube.F / 2.0 + mw.cube.faceZ0(); ServerPlayer p = l.players().isEmpty() ? null : l.players().get(0); if (p != null) { x = p.getX(); z = p.getZ(); }
        double a = lastAlt; long d = dayTime(e.atBody(mw, f, x, z, t), e.atBody(mw, f, x, z, t + 30), l.getDayTime()); /* 0.18.0 any body's day */ lastMoonAlt = lastAlt; lastAlt = a; l.setDayTime(d); moonTicks++; }
    /** Minecraft's DimensionType.timeOfDay: f = frac(dayTime / 24000 - 0.25), angle = (2 f + 0.5 - cos(pi f) / 2) / 3; this inverts it (monotonic). */
    static double invertAngle(double ang) { double lo = 0, hi = 1; for (int k = 0; k < 40; k++) { double m = (lo + hi) / 2, a = (2 * m + 0.5 - Math.cos(Math.PI * m) / 2) / 3; if (a < ang) lo = m; else hi = m; } return (lo + hi) / 2; }

    /** Beds: everyone asleep -> the clock moves on to the next sunrise at the sleeper (Minecraft's own skip would be undone by the next tick). */
    public static void sleep(net.minecraftforge.event.level.SleepFinishedTimeEvent ev) {
        if (!(ev.getLevel() instanceof ServerLevel l)) return; int f = Faces.face(l.dimension()); PlanetWorld w = PlanetWorld.current(); Ephemeris e = eph(w); if (f < 0 || e == null) return;
        double x = w.cube.F / 2.0, z = w.cube.F / 2.0; if (!l.players().isEmpty()) { x = l.players().get(0).getX(); z = l.players().get(0).getZ(); }
        double t = seconds(l.getServer().overworld().getGameTime(), 0), day = e.solarDay(), step = 60, prev = e.at(f, x, z, t).sunAlt, add = 0;
        if (Double.isInfinite(day)) return;   // a locked planet: no sunrise to wait for
        for (double s = step; s < 2 * day; s += step) { double a = e.at(f, x, z, t + s).sunAlt; if (prev < Math.toRadians(-0.833) && a >= Math.toRadians(-0.833)) { add = s; break; } prev = a; }
        if (add <= 0) return;
        Data dd = data(l); dd.off += add; dd.setDirty(); offsetS = dd.off; sleeps++;
        long d = dayTime(e, f, x, z, t + add, l.getDayTime()); ev.setTimeAddition(d);
        com.hopeless.infinity.Net.clock(l.getServer());
        HopelessInfinity.LOG.info("[Hopeless Infinity] slept: the clock moves on {} h to sunrise (offset {} s)", String.format("%.2f", add / 3600), Math.round(offsetS));
    }
    /** 0.17.0 time warp: the clock moves on s seconds (saved with the world; the clients told by the caller). */
    public static void addOffset(ServerLevel any, double s) { Data dd = data(any); dd.off += s; dd.setDirty(); offsetS = dd.off; }
    public static String probe() { return String.format("clock: t %.0f s (offset %.0f), star altitude %.1f deg (moon %.1f deg, %d moon ticks), dayTime %d, solar day %s, ticks %d, sleeps %d, real-sky tide evaluations %d", lastT, offsetS, Math.toDegrees(lastAlt), Math.toDegrees(lastMoonAlt), moonTicks, lastDayTime,
        eph == null ? "?" : String.format("%.2f h", eph.solarDay() / 3600), ticks, sleeps, TidesLink.tideEvals); }
}
