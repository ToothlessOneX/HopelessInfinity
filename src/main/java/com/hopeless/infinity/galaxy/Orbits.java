package com.hopeless.infinity.galaxy;

/**
 * 0.8.0 THE REAL SOLAR SYSTEM, stage 1: every planet's and moon's orbit and spin, so the sun, planets and moons stand where they really
 * are at any moment and every place has its real time of day. Owner, 2026-11-02: "the real sun in each solar system could then create a
 * real day nite cycle"; days are the planet's real rotation period.
 *
 * Drawn from the system's key on their own random stream (the system itself does not change). Sources:
 *  ORBITS (Keplerian ellipses about the barycentre's primary; the period from Kepler's third law, P = 2 pi sqrt(a^3 / G(M + m))):
 *   - eccentricity: small planets in multi-planet systems Rayleigh, sigma 0.049 (Van Eylen et al. 2019, AJ 157, 61); giants Beta(0.867, 3.03)
 *     (Kipping 2013, MNRAS 434, L51, from radial-velocity planets);
 *   - inclination to the system's invariable plane: Rayleigh, sigma 1.4 deg (Fang & Margot 2012, ApJ 761, 92);
 *   - node, argument of periapsis and mean anomaly at t = 0: uniform.
 *  MOONS: regular moons round the planet's equator, e Rayleigh 0.01 and i Rayleigh 0.5 deg (the Galilean and Saturnian regulars: e 0.001-0.03,
 *   i under 0.5 deg); a giant-impact moon near the planet's orbit plane, e Rayleigh 0.05, i Rayleigh 5 deg (the Moon: 0.055, 5.1 deg);
 *   irregular and captured moons e uniform 0.1-0.6 and i isotropic outside the Kozai gap 55-130 deg (Jewitt & Haghighipour 2007, ARA&A 45, 261).
 *  SPIN:
 *   - rocky planets: the spin as a share of break-up (omega / sqrt(GM / R^3)), log-normal round 0.063 (Earth 0.059, Mars 0.067), sigma_ln
 *     0.5 (GAME: the spread); giants round 0.25 (Bryan et al. 2018, Nature Astronomy 2, 138: planetary-mass companions spin at a few tens of
 *     per cent of break-up; Jupiter 0.29, Saturn 0.36), sigma_ln 0.4;
 *   - obliquity isotropic (Kokubo & Genda 2010, ApJL 714, L21: giant impacts leave the spin axis random); pole longitude uniform;
 *   - TIDAL LOCKING (Gladman et al. 1996, Icarus 122, 166): t_lock = omega a^6 I Q / (3 G M^2 k2 R^5), I = 0.4 m R^2; a body whose t_lock is
 *     under its system's age turns once per orbit, with no tilt (Cassini state 1, Peale 1969). Rocky Q 100, k2 0.3 (Gladman's values for
 *     solid bodies); giants Q 1e5 (Goldreich & Soter 1966), k2 0.38 (Jupiter, Wahl et al. 2016).
 */
public final class Orbits {
    public static final double G = 6.6743e-20;                    // km^3 / (kg s^2)
    public static final double M_SUN_KG = 1.98847e30, M_EARTH_KG = 5.9722e24, AU_KM = 1.495978707e8, YEAR_S = 365.25 * 86400;
    public static final double E_SIGMA_SMALL = 0.049, KIP_A = 0.867, KIP_B = 3.03, I_SIGMA = Math.toRadians(1.4);
    public static final double SPIN_ROCKY = 0.063, SPIN_ROCKY_SD = 0.5, SPIN_GIANT = 0.25, SPIN_GIANT_SD = 0.4;
    public static final double Q_ROCKY = 100, K2_ROCKY = 0.3, Q_GIANT = 1e5, K2_GIANT = 0.38;

    /** An orbit: semi-major axis (km), e, inclination, node, argument of periapsis (rad, in the parent's reference plane), mean anomaly at t = 0, period (s). */
    public record Elements(double aKm, double e, double incl, double node, double peri, double m0, double periodS) {}
    /** A spin: sidereal period (s), obliquity (rad, to the orbit's normal), the pole's longitude round that normal (rad), the turn at t = 0, locked or not and the locking time (yr). */
    public record Spin(double periodS, double obliquity, double poleLon, double phase0, boolean locked, double lockYr) {}

    public final StarSystem sys; public final double ageYr, gmStar;
    public final Elements[] planet; public final Spin[] planetSpin; public final Elements[][] moon; public final Spin[][] moonSpin;

    public Orbits(StarSystem s) {
        sys = s; ageYr = s.primary.ageYr; double mStar = (s.star != null ? s.star.mass : s.primary.mass) * M_SUN_KG; gmStar = G * mStar;
        Rng r = new Rng(Rng.mix(s.ref.key ^ 0x0EB175L)); int n = s.planets.size();
        planet = new Elements[n]; planetSpin = new Spin[n]; moon = new Elements[n][]; moonSpin = new Spin[n][];
        for (int i = 0; i < n; i++) { Planet p = s.planets.get(i); double mp = p.massE * M_EARTH_KG, a = p.au * AU_KM;
            double e = p.giant() || p.kind == Planet.Kind.MINI_NEPTUNE && p.massE > 30 ? beta(r, KIP_A, KIP_B) : rayleigh(r, E_SIGMA_SMALL); e = Math.min(e, 0.9);
            planet[i] = new Elements(a, e, rayleigh(r, I_SIGMA), r.range(0, 2 * Math.PI), r.range(0, 2 * Math.PI), r.range(0, 2 * Math.PI), period(a, mStar + mp));
            boolean solid = p.kind == Planet.Kind.ROCKY; double R = p.radiusKm, wBreak = Math.sqrt(G * mp / (R * R * R));
            double w = wBreak * (solid ? SPIN_ROCKY * Math.exp(SPIN_ROCKY_SD * r.gauss()) : SPIN_GIANT * Math.exp(SPIN_GIANT_SD * r.gauss())); w = Math.min(w, 0.6 * wBreak);
            double obl = Math.acos(r.range(-1, 1)), poleLon = r.range(0, 2 * Math.PI), ph = r.range(0, 2 * Math.PI);
            double tl = lockYr(w, a, mp, R, mStar, solid);
            planetSpin[i] = tl < ageYr ? new Spin(planet[i].periodS, 0, poleLon, ph, true, tl) : new Spin(2 * Math.PI / w, obl, poleLon, ph, false, tl);
            int nm = p.moons.size(); moon[i] = new Elements[nm]; moonSpin[i] = new Spin[nm];
            for (int j = 0; j < nm; j++) { Planet.Moon m = p.moons.get(j); double mm = m.massE() * M_EARTH_KG, am = m.orbitKm(), em, im;
                switch (m.kind()) {
                    case REGULAR -> { em = rayleigh(r, 0.01); im = rayleigh(r, Math.toRadians(0.5)); }
                    case GIANT_IMPACT -> { em = rayleigh(r, 0.05); im = rayleigh(r, Math.toRadians(5)); }
                    default -> { em = r.range(0.1, 0.6); double c; do { c = r.range(-1, 1); } while (Math.acos(c) > Math.toRadians(55) && Math.acos(c) < Math.toRadians(130)); im = Math.acos(c); }
                }
                moon[i][j] = new Elements(am, em, im, r.range(0, 2 * Math.PI), r.range(0, 2 * Math.PI), r.range(0, 2 * Math.PI), period(am, mp + mm));
                double rm = Math.max(0.5, m.radiusKm()), wm = Math.sqrt(G * Math.max(mm, 1) / (rm * rm * rm)) * SPIN_ROCKY * Math.exp(SPIN_ROCKY_SD * r.gauss()), tm = lockYr(wm, am, mm, rm, mp, true);
                double om = Math.acos(r.range(-1, 1)), pl = r.range(0, 2 * Math.PI), pm = r.range(0, 2 * Math.PI);
                moonSpin[i][j] = tm < ageYr ? new Spin(moon[i][j].periodS, 0, pl, pm, true, tm) : new Spin(2 * Math.PI / wm, om, pl, pm, false, tm); } }
    }
    static double period(double aKm, double massKg) { return 2 * Math.PI * Math.sqrt(aKm * aKm * aKm / (G * massKg)); }
    static double rayleigh(Rng r, double s) { return s * Math.sqrt(-2 * Math.log(Math.max(1e-300, 1 - r.u()))); }
    /** Beta(a, b) by two gamma draws (Marsaglia-Tsang; a < 1 boosted). */
    static double beta(Rng r, double a, double b) { double x = gamma(r, a), y = gamma(r, b); return x / (x + y); }
    static double gamma(Rng r, double k) {
        if (k < 1) return gamma(r, k + 1) * Math.pow(Math.max(1e-300, r.u()), 1 / k);
        double d = k - 1.0 / 3, c = 1 / Math.sqrt(9 * d);
        while (true) { double x = r.gauss(), v = 1 + c * x; if (v <= 0) continue; v = v * v * v; double u = r.u(); if (Math.log(Math.max(1e-300, u)) < 0.5 * x * x + d - d * v + d * Math.log(v)) return d * v; } }
    /** Gladman et al. (1996) eq. 9: years for a body (mass m, radius R km, spin w rad/s) at a km from a perturber of mass M to lock. */
    public static double lockYr(double w, double aKm, double m, double R, double M, boolean solid) {
        double Q = solid ? Q_ROCKY : Q_GIANT, k2 = solid ? K2_ROCKY : K2_GIANT, I = 0.4 * m * R * R;
        return w * Math.pow(aKm, 6) * I * Q / (3 * G * M * M * k2 * Math.pow(R, 5)) / YEAR_S; }

    /** Kepler: the position (km) on an orbit at time t (s), in the parent's reference plane (x, y in the plane, z its normal). */
    public static double[] position(Elements o, double t) {
        double M = o.m0 + 2 * Math.PI * (t / o.periodS - Math.floor(t / o.periodS)), E = M;
        for (int k = 0; k < 30; k++) { double d = (E - o.e * Math.sin(E) - M) / (1 - o.e * Math.cos(E)); E -= d; if (Math.abs(d) < 1e-13) break; }
        double xo = o.aKm * (Math.cos(E) - o.e), yo = o.aKm * Math.sqrt(1 - o.e * o.e) * Math.sin(E);
        double cw = Math.cos(o.peri), sw = Math.sin(o.peri), cO = Math.cos(o.node), sO = Math.sin(o.node), ci = Math.cos(o.incl), si = Math.sin(o.incl);
        double x1 = cw * xo - sw * yo, y1 = sw * xo + cw * yo;
        return new double[]{cO * x1 - sO * ci * y1, sO * x1 + cO * ci * y1, si * y1};
    }
    /** The unit normal of an orbit's plane in its parent's reference frame. */
    public static double[] normal(Elements o) { return new double[]{Math.sin(o.incl) * Math.sin(o.node), -Math.sin(o.incl) * Math.cos(o.node), Math.cos(o.incl)}; }
    /** A spin's pole in its parent's reference frame: tilted 'obliquity' from the orbit normal toward the pole longitude. */
    public static double[] pole(Elements o, Spin s) {
        double[] nrm = normal(o), u = perp(nrm), v = cross(nrm, u); double so = Math.sin(s.obliquity), co = Math.cos(s.obliquity), cl = Math.cos(s.poleLon), sl = Math.sin(s.poleLon);
        return new double[]{co * nrm[0] + so * (cl * u[0] + sl * v[0]), co * nrm[1] + so * (cl * u[1] + sl * v[1]), co * nrm[2] + so * (cl * u[2] + sl * v[2])}; }
    /** How far a body has turned (rad, sidereal) at time t. A locked body keeps one face to its parent: its turn follows the orbit's mean anomaly. */
    public static double turn(Elements o, Spin s, double t) {
        if (s.locked) return s.phase0 + o.m0 + 2 * Math.PI * (t / o.periodS - Math.floor(t / o.periodS));
        return s.phase0 + 2 * Math.PI * (t / s.periodS - Math.floor(t / s.periodS)); }
    /** The solar day (s): the time between two noons, 1 / (1 / P_spin - 1 / P_orbit) for a prograde spin (a locked body has none: infinite). */
    public static double solarDay(Elements o, Spin s) { if (s.locked) return Double.POSITIVE_INFINITY; double sign = Math.cos(s.obliquity) >= 0 ? 1 : -1; return 1 / Math.abs(1 / s.periodS - sign / o.periodS); }

    static double[] perp(double[] n) { double[] a = Math.abs(n[2]) < 0.9 ? new double[]{0, 0, 1} : new double[]{1, 0, 0}; double[] c = cross(a, n); double l = Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]); return new double[]{c[0] / l, c[1] / l, c[2] / l}; }
    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }

    public String describe(int i) {
        Planet p = sys.planets.get(i); Elements o = planet[i]; Spin s = planetSpin[i];
        return String.format("planet %d (%s, %.2f Me): a %.3f AU, e %.3f, i %.2f deg, year %.1f d; %s, day %s, tilt %.1f deg (lock time %.2g yr, age %.2g yr); moons %d",
            i, p.kind, p.massE, o.aKm / AU_KM, o.e, Math.toDegrees(o.incl), o.periodS / 86400, s.locked ? "LOCKED" : String.format("spin %.2f h", s.periodS / 3600),
            Double.isInfinite(solarDay(o, s)) ? "none (one face to the star)" : String.format("%.2f h", solarDay(o, s) / 3600), Math.toDegrees(s.obliquity), s.lockYr, ageYr, moon[i].length); }
}
