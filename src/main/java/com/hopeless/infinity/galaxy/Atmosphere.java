package com.hopeless.infinity.galaxy;

/**
 * 0.10.0 ATMOSPHERES (owner, 2026-11-02: "not every planet or body should have oxygen ... as real as we can make it"). For every planet and
 * moon: whether it keeps an atmosphere, how thick, what it is made of, and whether a person can breathe it. Sources:
 *  KEEPING AN ATMOSPHERE - the cosmic shoreline, Berta-Thompson, Wachiraphan & Murray 2025 ("The 3D Cosmic Shoreline for Nurturing Planetary
 *   Atmospheres", eq. 1-2): log10(f_sh / f_E) = 2.62 + 5.9 log10(v_esc / 11.18 km/s) + 1.17 log10(L / L_Sun); a body gets more flux than
 *   that loses its air. Their transition is a logistic in log flux of width 0.247 dex: the chance of keeping air is drawn from it.
 *   (The shoreline itself: Zahnle & Catling 2017, ApJ 843, 122, I ~ v_esc^4.)
 *  WHAT IT IS MADE OF, by the body's kind and climate (Planet.Climate, Kopparapu et al. 2013 zones):
 *   - giants and mini-Neptunes: hydrogen and helium envelopes, no surface (Jupiter 86 % H2, 14 % He by number; NASA fact sheet);
 *   - hot rocky (past the runaway greenhouse): Venus-like, 96.5 % CO2, 3.5 % N2;
 *   - temperate rocky with liquid water: Earth-like N2 with O2 from life (below), 0.93 % Ar, 0.04 % CO2 (NASA Earth fact sheet);
 *   - cold-edge rocky: CO2-rich (95 % CO2, 3 % N2, 2 % Ar: Mars's mix, NASA fact sheet);
 *   - frozen bodies: N2 with CH4 (Titan: 95 % N2, 5 % CH4; Coustenis 2005).
 *  HOW THICK: the volatile inventory scales with the body's mass, so the surface pressure P = M_atm g / (4 pi R^2) scales as M g / R^2 from the
 *   body of the same kind it is modelled on (Earth 1.013 bar, Venus 92 bar, Mars 6.36 mbar, Titan 1.467 bar), with a log-normal spread
 *   sigma_ln 0.7 (GAME: inventories vary; the Solar System's rocky planets span 5 orders of magnitude).
 *  OXYGEN comes only from life (oxygenic photosynthesis), and late: on Earth it stayed near zero until the Great Oxidation Event, about 2.1 Gyr
 *   after the planet formed (2.4 Ga), held at under 1 % of today's level through the Proterozoic, and rose to near today's 21 % only in
 *   the Neoproterozoic (0.8-0.54 Ga: 3.7-4.0 Gyr after forming) (Lyons, Reinhard & Planavsky 2014, Nature 506, 307). Here: temperate worlds
 *   with water grow life (GAME: one example, ours, says it can; nothing yet says how often), and their O2 follows Earth's timeline by age,
 *   modern fraction 21 % with a spread (the Phanerozoic's 15-30 %, Berner 2006).
 *  BREATHABLE (US regulations): oxygen partial pressure at least 0.124 atm (14 CFR 91.211: supplemental oxygen is required above 14,000 ft,
 *   where the air's O2 is 0.2095 x 0.5875 atm), at most 0.5 atm (NOAA Diving Manual: no lung damage from oxygen below 0.5 atm on long
 *   exposure), total pressure over the Armstrong limit 0.0618 atm (body fluids boil below it), CO2 at most 0.005 atm (OSHA PEL 5,000 ppm,
 *   29 CFR 1910.1000 Table Z-1).
 *  WITH HEIGHT: P(h) = P0 exp(-h / H), H = R T / (mu g) (the isothermal barometric law), T the body's mean surface temperature.
 */
public final class Atmosphere {
    public static final double SHORE_LOG_F0 = 2.62, SHORE_P = 5.9, SHORE_Q = 1.17, SHORE_W = 0.247, V_ESC_EARTH = 11.18;
    public static final double O2_MIN_ATM = 0.124, O2_MAX_ATM = 0.5, ARMSTRONG_ATM = 0.0618, CO2_MAX_ATM = 0.005, BAR_PER_ATM = 1.01325;
    public static final double GIANT_MOON_KEEP = 1.0 / 6;
    public static final double GOE_GYR = 2.1, NOX_START_GYR = 3.7, NOX_END_GYR = 4.0, O2_MODERN = 0.21, O2_PROTEROZOIC = 0.21 * 0.005, SPREAD_LN = 0.7;

    public enum Kind { NONE, ENVELOPE, VENUS_LIKE, EARTH_LIKE, MARS_LIKE, TITAN_LIKE }
    public final Kind kind; public final double pressureAtm, n2, o2, co2, ar, ch4, h2he, muGPerMol, scaleHeightM, keepChance; public final boolean breathable;
    Atmosphere(Kind k, double p, double n2, double o2, double co2, double ar, double ch4, double h2he, double mu, double H, double keep) {
        kind = k; pressureAtm = p; this.n2 = n2; this.o2 = o2; this.co2 = co2; this.ar = ar; this.ch4 = ch4; this.h2he = h2he; muGPerMol = mu; scaleHeightM = H; keepChance = keep;
        breathable = p >= ARMSTRONG_ATM && p * o2 >= O2_MIN_ATM && p * o2 <= O2_MAX_ATM && p * co2 <= CO2_MAX_ATM; }

    /** Escape velocity (km/s) of a body of mass m (Earth masses) and radius r (km). */
    public static double vEsc(double massE, double rKm) { return Math.sqrt(2 * Orbits.G * massE * Orbits.M_EARTH_KG / rKm); }
    /** The chance a body keeps its air (Berta-Thompson et al. 2025): flux f (Earth = 1), escape velocity (km/s), star luminosity (Sun = 1). */
    public static double keepChance(double fluxE, double vEsc, double lum) {
        double logSh = SHORE_LOG_F0 + SHORE_P * Math.log10(vEsc / V_ESC_EARTH) + SHORE_Q * Math.log10(Math.max(1e-6, lum)), x = (Math.log10(Math.max(1e-12, fluxE)) - logSh) / SHORE_W;
        return 1 / (1 + Math.pow(10, x)); }

    /**
     * The air of a body. massE, rKm: its mass and radius; fluxE: its insolation (Earth = 1); lum: the star's luminosity; climate: the planet's
     * zone (for moons, their planet's); giant: an envelope world; tempK: mean surface temperature; ageGyr: the system's age; r: its own stream.
     */
    public static Atmosphere of(double massE, double rKm, double fluxE, double lum, Planet.Climate climate, boolean giant, double tempK, double ageGyr, Rng r) { return of(massE, rKm, fluxE, lum, climate, giant, tempK, ageGyr, 1, r); }
    public static Atmosphere of(double massE, double rKm, double fluxE, double lum, Planet.Climate climate, boolean giant, double tempK, double ageGyr, double keepScale, Rng r) {
        double g = Research.G_EARTH * massE / Math.pow(rKm / Research.EARTH_R_KM, 2), ve = vEsc(massE, rKm), keep = giant ? 1 : keepChance(fluxE, ve, lum) * keepScale;
        double u = r.u(), spread = Math.exp(SPREAD_LN * r.gauss()), o2draw = Math.exp(0.2 * r.gauss());
        if (giant) return make(Kind.ENVELOPE, Double.POSITIVE_INFINITY, 0, 0, 0, 0, 0, 1, 2.22, tempK, g, keep);
        if (u > keep) return make(Kind.NONE, 0, 0, 0, 0, 0, 0, 0, 0, tempK, g, keep);
        double scale = massE * (g / Research.G_EARTH) / Math.pow(rKm / Research.EARTH_R_KM, 2);   // M g / R^2 over Earth's
        switch (climate) {
            case HOT -> { double ref = massE * (g / 8.87) / Math.pow(rKm / 6051.8, 2) / 0.815;   // over Venus's M g / R^2 (0.815 Me, 8.87 m/s2, 6051.8 km)
                return make(Kind.VENUS_LIKE, 92 / BAR_PER_ATM * ref * spread, 0.035, 0, 0.965, 0, 0, 0, 43.45, tempK, g, keep); }
            case WARM_EDGE, TEMPERATE -> { double p = scale * spread, o2 = oxygen(ageGyr) * o2draw; double ar = 0.0093, co2 = 0.0004, n2 = Math.max(0, 1 - o2 - ar - co2);
                return make(Kind.EARTH_LIKE, p, n2, o2, co2, ar, 0, 0, 28.0 * n2 + 32.0 * o2 + 44.0 * co2 + 39.95 * ar, tempK, g, keep); }
            case COLD_EDGE -> { double ref = massE * (g / 3.73) / Math.pow(rKm / 3389.5, 2) / 0.107;   // over Mars's (0.107 Me, 3.73 m/s2, 3389.5 km)
                return make(Kind.MARS_LIKE, 6.36e-3 / BAR_PER_ATM * ref * spread, 0.03, 0, 0.95, 0.02, 0, 0, 43.3, tempK, g, keep); }
            default -> { double ref = massE * (g / 1.352) / Math.pow(rKm / 2574.7, 2) / 0.0225;   // over Titan's (0.0225 Me, 1.352 m/s2, 2574.7 km)
                return make(Kind.TITAN_LIKE, 1.467 / BAR_PER_ATM * ref * spread, 0.95, 0, 0, 0, 0.05, 0, 27.4, tempK, g, keep); }
        }
    }
    static Atmosphere make(Kind k, double p, double n2, double o2, double co2, double ar, double ch4, double h2he, double mu, double tempK, double g, double keep) {
        double H = mu > 0 ? 8.314 * tempK / (mu / 1000 * g) : 0; return new Atmosphere(k, p, n2, o2, co2, ar, ch4, h2he, mu, H, keep); }
    /** O2 fraction by the age of a world with life, on Earth's timeline (Lyons et al. 2014). */
    public static double oxygen(double ageGyr) {
        if (ageGyr < GOE_GYR) return 0;
        if (ageGyr < NOX_START_GYR) return O2_PROTEROZOIC;
        if (ageGyr < NOX_END_GYR) return O2_PROTEROZOIC + (O2_MODERN - O2_PROTEROZOIC) * (ageGyr - NOX_START_GYR) / (NOX_END_GYR - NOX_START_GYR);
        return O2_MODERN; }
    /** Total pressure (atm) h metres above the datum. */
    public double pressureAt(double hM) { return kind == Kind.NONE ? 0 : scaleHeightM <= 0 ? pressureAtm : pressureAtm * Math.exp(-hM / scaleHeightM); }
    /** Can a person breathe here, h metres above the datum (same limits, the pressure fallen with height)? */
    public boolean breathableAt(double hM) { double p = pressureAt(hM); return p >= ARMSTRONG_ATM && p * o2 >= O2_MIN_ATM && p * o2 <= O2_MAX_ATM && p * co2 <= CO2_MAX_ATM; }
    /** The height (m) above which a person can no longer breathe it (0 when never; infinite when everywhere). */
    public double ceilingM() { if (!breathable) return 0; double pMin = Math.max(ARMSTRONG_ATM, O2_MIN_ATM / o2); return scaleHeightM * Math.log(pressureAtm / pMin); }
    @Override public String toString() {
        if (kind == Kind.NONE) return String.format("no air (chance to keep one %.2f)", keepChance);
        if (kind == Kind.ENVELOPE) return "hydrogen-helium envelope, no surface";
        return String.format("%s %.3g atm: N2 %.1f%% O2 %.2f%% CO2 %.2f%% Ar %.1f%% CH4 %.1f%%; scale height %.1f km; %s", kind, pressureAtm, n2 * 100, o2 * 100, co2 * 100, ar * 100, ch4 * 100, scaleHeightM / 1000,
            breathable ? String.format("BREATHABLE to %.1f km", ceilingM() / 1000) : "not breathable"); }

    /** The air of every planet of a system and of each planet's moons, on the system's own stream. [planet][0] = the planet, [planet][1 + j] = moon j. */
    public static Atmosphere[][] of(StarSystem s) {
        Rng r = new Rng(Rng.mix(s.ref.key ^ 0xA7305EL)); double lum = s.star != null ? s.star.lum : s.primary.lum, age = s.primary.ageYr / 1e9; Atmosphere[][] out = new Atmosphere[s.planets.size()][];
        for (int i = 0; i < s.planets.size(); i++) { Planet p = s.planets.get(i); out[i] = new Atmosphere[1 + p.moons.size()];
            double t = p.teq + (p.climate == Planet.Climate.TEMPERATE || p.climate == Planet.Climate.WARM_EDGE ? 33 : 0);   // Earth's greenhouse warming 33 K on the temperate ones
            out[i][0] = of(p.massE, p.radiusKm, p.insolation, lum, p.climate, p.kind != Planet.Kind.ROCKY, t, age, r);
            for (int j = 0; j < p.moons.size(); j++) { Planet.Moon m = p.moons.get(j);
                Planet.Climate mc = p.climate == Planet.Climate.TEMPERATE && m.massE() < 0.05 ? Planet.Climate.COLD_EDGE : p.climate;   // a small moon of a temperate world holds no ocean (the Moon)
                // big moons of giants: the shoreline lets Ganymede, Callisto and Io keep air, but of the Solar System's six big icy moons (with Triton) only
                // Titan has a thick one (the giants' hot circumplanetary disks and magnetospheres; Zahnle & Catling 2017 s.6): GIANT_MOON_KEEP = 1/6
                out[i][1 + j] = of(m.massE(), Math.max(0.5, m.radiusKm()), p.insolation, lum, mc, false, p.teq, age, p.giant() ? GIANT_MOON_KEEP : 1, r); } }
        return out; }
}
