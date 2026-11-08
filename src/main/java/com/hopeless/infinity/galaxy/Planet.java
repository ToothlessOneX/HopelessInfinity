package com.hopeless.infinity.galaxy;

import static com.hopeless.infinity.galaxy.Research.*;

/** One planet: orbit, mass, radius (Chen & Kipping), surface gravity, insolation and climate band (Kopparapu 2013: temperate = their conservative zone, moist to maximum greenhouse), size in blocks. */
public final class Planet {
    public enum Kind { ROCKY, MINI_NEPTUNE, ICE_GIANT, GAS_GIANT }   // Chen & Kipping classes; Neptunian worlds under 10 Me are mini-Neptunes (Neptune is 17 Me)
    public enum Climate { HOT, WARM_EDGE, TEMPERATE, COLD_EDGE, FROZEN }
    public int index; public final double au, massE, radiusE, gravity, insolation, teq; public final Kind kind; public final Climate climate;
    public final long blockRadius;   // real size: 1 block = 1 m
    public final double radiusKm;
    public enum Rings { NONE, FAINT, BRIGHT }
    public Rings rings = Rings.NONE; public double ringInKm, ringOutKm;
    public enum MoonKind { REGULAR, IRREGULAR, GIANT_IMPACT, CAPTURED }
    /** A moon: mass (Earth masses), real radius (km) and orbit radius (km). */
    public record Moon(MoonKind kind, double massE, double radiusKm, double orbitKm) {}
    public final java.util.List<Moon> moons = new java.util.ArrayList<>();
    /** Radius (km) of a body of mass m (Earth masses) and density rho (g/cm^3). */
    public static double radiusKm(double m, double rho) { return Math.cbrt(3 * m * 5.9722e27 / (4 * Math.PI * rho)) / 1e5; }

    Planet(int index, Star s, double au, double massE) {
        this.index = index; this.au = au; this.massE = massE;
        radiusE = radius(massE);
        gravity = G_EARTH * massE / (radiusE * radiusE);
        kind = massE < CK_T1 ? Kind.ROCKY : massE < 10 ? Kind.MINI_NEPTUNE : massE < CK_T2 ? Kind.ICE_GIANT : Kind.GAS_GIANT;
        insolation = s.lum / (au * au);
        teq = TEQ_1AU * Math.pow(insolation, 0.25) * Math.pow(1 - ALBEDO_ROCKY, 0.25);
        climate = insolation > s.hzSeff(RECENT_VENUS) ? Climate.HOT : insolation > s.hzSeff(MOIST) ? Climate.WARM_EDGE
            : insolation >= s.hzSeff(MAX_GREENHOUSE) ? Climate.TEMPERATE : insolation >= s.hzSeff(EARLY_MARS) ? Climate.COLD_EDGE : Climate.FROZEN;
        radiusKm = radiusE * EARTH_R_KM; blockRadius = Math.round(radiusKm * 1000);
    }
    /** Chen & Kipping 2017 median radius (Earth radii) for a mass (Earth masses); the four pieces join where the paper's transitions are. */
    public static double radius(double m) {
        double r1 = CK_C * Math.pow(Math.min(m, CK_T1), CK_S1);
        if (m <= CK_T1) return r1;
        double r2 = r1 * Math.pow(Math.min(m, CK_T2) / CK_T1, CK_S2);
        if (m <= CK_T2) return r2;
        double r3 = r2 * Math.pow(Math.min(m, CK_T3) / CK_T2, CK_S3);
        if (m <= CK_T3) return r3;
        return r3 * Math.pow(m / CK_T3, CK_S4);
    }
    /** A planet you can stand on: rocky (Chen & Kipping's Terran class). */
    public boolean landable() { return kind == Kind.ROCKY; }
    public boolean giant() { return kind == Kind.ICE_GIANT || kind == Kind.GAS_GIANT; }
    public boolean earthLike() { return kind == Kind.ROCKY && climate == Climate.TEMPERATE; }
    @Override public String toString() {
        return String.format("%d  %7.3f AU  %8.2f Me  %6.2f Re  g %5.2f m/s2  S %8.3f  Teq %5.0f K  %-9s %-10s r=%.0f km%s", index, au, massE, radiusE, gravity, insolation, teq, kind, climate, radiusKm,
            rings == Rings.NONE ? "" : String.format("  %s rings %.0f-%.0f km", rings, ringInKm, ringOutKm)) + (moons.isEmpty() ? "" : "  moons " + moons.size()); }
}
