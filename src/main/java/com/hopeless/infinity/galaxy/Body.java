package com.hopeless.infinity.galaxy;

import static com.hopeless.infinity.galaxy.Research.*;

/**
 * One star-formation product as it is today: born with an initial mass (Kroupa IMF) at some age, then aged by stellar evolution - main
 * sequence, giant or supergiant (Wolf-Rayet for the most massive), then a white dwarf, neutron star or black hole. Brown dwarfs never
 * burn hydrogen. Every size is real: radius in km.
 */
public final class Body {
    public enum Kind { BROWN_DWARF, MAIN_SEQUENCE, GIANT, SUPERGIANT, WOLF_RAYET, WHITE_DWARF, NEUTRON_STAR, BLACK_HOLE, SUPERMASSIVE_BLACK_HOLE }
    public final Kind kind; public final double initialMass, mass, ageYr, teff, lum, radiusKm; public final String type;
    /** Years since the star died (remnants), or until it dies (living stars, negative). Supernovae and remnants are read from this. */
    public final double deathYr;
    public final Star ms;   // the Mamajek main-sequence star behind a living star (for the habitable zone etc.); null otherwise

    private Body(Kind k, double mi, double m, double age, double teff, double lum, double rkm, String type, double death, Star ms) {
        kind = k; initialMass = mi; mass = m; ageYr = age; this.teff = teff; this.lum = lum; radiusKm = rkm; this.type = type; deathYr = death; this.ms = ms; }

    public static double lifetimeYr(double m) { return Math.max(3e6, T_SUN_GYR * 1e9 * Math.pow(m, -2.5)); }

    /** Ages a star of initial mass mi (Msun) born ageYr ago. r gives the small random parts (giant radius, colour). */
    public static Body evolve(double mi, double age, Rng r) {
        if (mi < IMF_HBL) {   // brown dwarf: cools for ever; radius about Jupiter's (0.1 Rsun), Teff falls with age
            double t = 2800 * Math.pow(mi / 0.075, 0.8) * Math.pow(Math.max(1e7, age) / 1e8, -0.3);
            double rkm = 0.1 * SUN_R_KM; double lum = Math.pow(rkm / SUN_R_KM, 2) * Math.pow(t / 5772, 4);
            return new Body(Kind.BROWN_DWARF, mi, mi, age, t, lum, rkm, t > 2200 ? "L" : t > 1300 ? "T" : "Y", Double.NEGATIVE_INFINITY, null); }
        double life = lifetimeYr(mi), post = POST_MS * life, death = age - (life + post);
        if (age < life) { Star s = Star.of(classOf(mi), Math.min(mi, MS_M[0])); return new Body(Kind.MAIN_SEQUENCE, mi, mi, age, s.teff, s.lum, s.radius * SUN_R_KM, s.type, death, s); }
        if (death < 0) {   // after the main sequence
            if (mi >= WR_MIN && age > life + post / 2) { double t = 50000 + 60000 * r.u(); double rkm = 2 * SUN_R_KM * (0.5 + r.u());   // WR: hot stripped cores, a few Rsun
                return new Body(Kind.WOLF_RAYET, mi, mi * 0.5, age, t, lum(rkm, t), rkm, "WR", death, null); }
            boolean superg = mi >= NS_MIN; double t = superg ? 3500 + 20000 * Math.pow(r.u(), 2) : 3600 + 1400 * r.u();
            double rsun = superg ? Math.exp(Math.log(100) + r.u() * Math.log(10)) : Math.exp(Math.log(10) + r.u() * Math.log(10));   // supergiants 100-1000 Rsun, giants 10-100 Rsun
            return new Body(superg ? Kind.SUPERGIANT : Kind.GIANT, mi, mi, age, t, lum(rsun * SUN_R_KM, t), rsun * SUN_R_KM, superg ? "I" : "III", death, null); }
        if (mi < NS_MIN) {   // white dwarf: Kalirai IFMR; radius ~ Earth's, shrinking with mass; cooling ~ t^-0.35 (Mestel)
            double m = Math.min(1.35, IFMR_A * mi + IFMR_B), rkm = 0.0126 * SUN_R_KM * Math.pow(m / 0.6, -1.0 / 3), t = Math.max(3500, 25000 * Math.pow(Math.max(1e6, death) / 1e8, -0.35) / 2.5);
            return new Body(Kind.WHITE_DWARF, mi, m, age, t, lum(rkm, t), rkm, "D", death, null); }
        if (mi < BH_MIN) return new Body(Kind.NEUTRON_STAR, mi, NS_MASS, age, 1e6 * Math.pow(Math.max(1e3, death) / 1e3, -0.5), 0, NS_RADIUS_KM, "NS", death, null);
        double m = 0.3 * mi; return new Body(Kind.BLACK_HOLE, mi, m, age, 0, 0, schwarzschildKm(m), "BH", death, null);
    }
    public static Body sgrA() { return new Body(Kind.SUPERMASSIVE_BLACK_HOLE, SGR_A_MASS, SGR_A_MASS, 1e10, 0, 0, schwarzschildKm(SGR_A_MASS), "SMBH", 1e10, null); }
    /** Schwarzschild radius 2GM/c^2 in km (2.953 km per solar mass). */
    public static double schwarzschildKm(double m) { return 2.953 * m; }
    static double lum(double rkm, double t) { double rr = rkm / SUN_R_KM, tt = t / 5772; return rr * rr * tt * tt * tt * tt; }
    /** Spectral class (O..M index) of a main-sequence star by mass, from the class rows of the Mamajek sequence. */
    public static int classOf(double m) { for (int c = 0; c < 7; c++) if (m >= MS_M[CLASS_ROW[c + 1]]) return c; return 6; }
    public boolean alive() { return kind == Kind.MAIN_SEQUENCE || kind == Kind.GIANT || kind == Kind.SUPERGIANT || kind == Kind.WOLF_RAYET || kind == Kind.BROWN_DWARF; }
    public double radiusSun() { return radiusKm / SUN_R_KM; }
    @Override public String toString() { return String.format("%s %s %.3f Msun (born %.2f) age %.3g yr Teff %.0f K L %.3g R %.4g km", kind, type, mass, initialMass, ageYr, teff, lum, radiusKm); }
}
