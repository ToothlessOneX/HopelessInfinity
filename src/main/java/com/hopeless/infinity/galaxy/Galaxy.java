package com.hopeless.infinity.galaxy;

import java.util.ArrayList;
import java.util.List;

import static com.hopeless.infinity.galaxy.Research.*;

/**
 * The real-size spiral galaxy of one world seed (v2). Nothing is stored; everything comes from its key when asked.
 *
 *  stars     10 pc cubes ("cells") hold the real number of objects for their place: density(x,y,z) is the thin + thick exponential disk
 *            (2.6 kpc scale length, 300 / 900 pc heights) with four arms, plus the bar and bulge, scaled so the Sun's neighbourhood has
 *            Kirkpatrick's 0.107 objects per pc^3 and the bulge Bland-Hawthorn & Gerhard's 1.55e10 Msun. Each object is born with a Kroupa
 *            IMF mass and an age (disk: constant star formation over 10 Gyr; bulge and thick disk: 8-10 Gyr) and then aged (Body).
 *  young     massive stars (> 8 Msun) born in the last 50 Myr are made per 100 pc column instead, on the arms, so the whole galaxy's
 *            O/B stars, Wolf-Rayet stars, supernovae, remnants and young pulsars can be listed (youngColumn).
 *  centre    Sgr A* at 0,0,0.
 *  rogues    free-floating planets, 21 per star (rogues(cell)).
 * Coordinates are parsecs; the Sun's place is (0, -R0, 0); the bar lies BAR_ANGLE_DEG from the Sun - centre line.
 */
public final class Galaxy {
    public final long seed;
    public static final double CELL = 10, COLUMN = 100, YOUNG_YR = 5e7, MASSIVE = 8, YOUNG_H_PC = 50;
    public static final int COLUMNS = (int) Math.ceil(DISK_EDGE_KPC * 1000 / COLUMN);
    public Galaxy(long seed) { this.seed = seed; }

    // ------------------------------------------------------------------ density (objects per cubic parsec)
    static double N0;   // set below: the Sun's place gets Kirkpatrick's local density, counting companions as the census does
    static final double[] ARM_MEAN = new double[160];   // mean arm factor round each radius (0.1 kpc steps): arms move stars, they do not add them
    static double BULGE_NORM;                            // objects per pc^3 at the bulge centre
    static { for (int i = 0; i < ARM_MEAN.length; i++) { double r = Math.max(0.05, i * 0.1), s = 0; for (int k = 0; k < 720; k++) { double t = 2 * Math.PI * k / 720; s += armShape(r * Math.sin(t), -r * Math.cos(t)); } ARM_MEAN[i] = s / 720; }
        double sum = 0, st = 0.05; for (double x = -6; x <= 6; x += st) for (double y = -6; y <= 6; y += st) for (double z = -3; z <= 3; z += st) sum += bulgeShape(x, y, z);
        BULGE_NORM = BULGE_MASS / MEAN_OBJECT_MASS / (sum * Math.pow(st * 1000, 3)); }
    /** Arm factor at x, y (kpc): the four log spirals (Vallee pitch) leaving the bar ends, over a floor. */
    static double armShape(double x, double y) {
        double r = Math.hypot(x, y), rb = BAR_HALF_LENGTH_KPC, th = Math.atan2(x, -y), bar = Math.toRadians(BAR_ANGLE_DEG); if (r < 0.6 * rb) return 1;
        double phi = th - Math.log(r / rb) / Math.tan(Math.toRadians(ARM_PITCH_DEG)) - bar;
        double arm = Math.pow(0.5 + 0.5 * Math.cos(ARMS * phi), ARM_SHARPNESS) * smooth(0.6 * rb, 1.05 * rb, r);
        return 0.22 + ARM_CONTRAST * arm; }
    static double armFactor(double x, double y, double strength) { double r = Math.hypot(x, y); double m = ARM_MEAN[Math.min(ARM_MEAN.length - 1, (int) (r * 10))]; double a = armShape(x, y) / m; return 1 + strength * (a - 1); }
    /** Bar + bulge shape (kpc), 1 at the centre. */
    static double bulgeShape(double x, double y, double z) {
        double bar = Math.toRadians(BAR_ANGLE_DEG), c = Math.cos(bar), s = Math.sin(bar), along = x * s - y * c, across = x * c + y * s;
        double b = Math.exp(-0.5 * (sq(along / (0.55 * BAR_HALF_LENGTH_KPC)) + sq(across / (0.16 * BAR_HALF_LENGTH_KPC)) + sq(z / 0.18)));
        double g = Math.exp(-0.5 * (sq(x) + sq(y) + sq(z / 0.6)) / sq(BULGE_SIGMA_KPC));
        return 0.35 * b + g; }
    /** Disk part, objects per pc^3 (x, y, z in pc), and which share of it is thick disk. */
    static double disk(double x, double y, double z) {
        double xk = x / 1000, yk = y / 1000, r = Math.hypot(xk, yk); if (r > DISK_EDGE_KPC) return 0;
        double edge = 1 - smooth(DISK_EDGE_KPC - 2.5, DISK_EDGE_KPC, r), d = N0 * Math.exp(-(r - R0_KPC) / DISK_SCALE_LENGTH_KPC) * armFactor(xk, yk, OLD_ARM) * edge;
        return d * (Math.exp(-Math.abs(z) / (DISK_SCALE_HEIGHT_KPC * 1000)) + THICK_FRACTION * Math.exp(-Math.abs(z) / (THICK_H_KPC * 1000))); }
    /** Thin-disk surface shape (kpc), 1 at the Sun's radius. */
    static double thinShape(double xk, double yk) { double r = Math.hypot(xk, yk); if (r > DISK_EDGE_KPC) return 0; return Math.exp(-(r - R0_KPC) / DISK_SCALE_LENGTH_KPC) * (1 - smooth(DISK_EDGE_KPC - 2.5, DISK_EDGE_KPC, r)); }
    static double thickShare(double z) { double a = Math.exp(-Math.abs(z) / (DISK_SCALE_HEIGHT_KPC * 1000)), b = THICK_FRACTION * Math.exp(-Math.abs(z) / (THICK_H_KPC * 1000)); return b / (a + b); }
    static double bulge(double x, double y, double z) { return BULGE_NORM * bulgeShape(x / 1000, y / 1000, z / 1000); }
    public static double density(double x, double y, double z) { return disk(x, y, z) + bulge(x, y, z); }
    /** Face-on surface density, objects per pc^2 (for the map). */
    public static double surface(double x, double y) { double s = 0; for (double z = -3000; z <= 3000; z += 25) s += density(x, y, z) * 25; return s; }
    static double sq(double v) { return v * v; }
    static double smooth(double a, double b, double v) { double t = Math.max(0, Math.min(1, (v - a) / (b - a))); return t * t * (3 - 2 * t); }

    // ------------------------------------------------------------------ the IMF (Kirkpatrick et al. 2024, 0.01 - 150 Msun)
    static final double[] IMF_EDGE = IMF2_EDGE, IMF_SLOPE = IMF2_SLOPE; static final int NP = IMF_SLOPE.length;
    static final double[] IMF_W = new double[NP];
    static { double k = 1; for (int i = 0; i < NP; i++) { if (i > 0) k *= Math.pow(IMF_EDGE[i], IMF_SLOPE[i] - IMF_SLOPE[i - 1]); IMF_W[i] = k * Star.piece(IMF_EDGE[i], IMF_EDGE[i + 1], IMF_SLOPE[i]); } }
    /** A mass in [lo, hi) from the Kroupa IMF. */
    public static double imf(Rng r, double lo, double hi) {
        double[] w = new double[NP]; double tot = 0;
        for (int i = 0; i < NP; i++) { double a = Math.max(lo, IMF_EDGE[i]), b = Math.min(hi, IMF_EDGE[i + 1]); w[i] = a < b ? IMF_W[i] * Star.piece(a, b, IMF_SLOPE[i]) / Star.piece(IMF_EDGE[i], IMF_EDGE[i + 1], IMF_SLOPE[i]) : 0; tot += w[i]; }
        double u = r.u() * tot; for (int i = 0; i < NP; i++) { if (u < w[i] || i == NP - 1) { double a = Math.max(lo, IMF_EDGE[i]), b = Math.min(hi, IMF_EDGE[i + 1]); if (a >= b) continue; return Star.draw(r, a, b, IMF_SLOPE[i]); } u -= w[i]; }
        return lo; }
    /** Share of all objects born above m (by number). */
    public static double imfAbove(double m) { double tot = 0, s = 0; for (double w : IMF_W) tot += w;
        for (int i = 0; i < NP; i++) { double a = Math.max(m, IMF_EDGE[i]), b = IMF_EDGE[i + 1]; if (a < b) s += IMF_W[i] * Star.piece(a, b, IMF_SLOPE[i]) / Star.piece(IMF_EDGE[i], IMF_EDGE[i + 1], IMF_SLOPE[i]); } return s / tot; }

    // ------------------------------------------------------------------ systems
    public enum Where { CELL, YOUNG, CENTRE }
    /** One system: its place (pc), its primary body and, for a multiple, a companion (separation in AU). */
    public static final class Ref {
        public final Where where; public final long cx, cy, cz; public final int k; public final double x, y, z; public final Body primary, companion; public final double sepAu; final long key;
        Ref(Where w, long cx, long cy, long cz, int k, double x, double y, double z, Body p, Body c, double sep, long key) { where = w; this.cx = cx; this.cy = cy; this.cz = cz; this.k = k; this.x = x; this.y = y; this.z = z; primary = p; companion = c; sepAu = sep; this.key = key; }
        public String name() { return where == Where.CENTRE ? "Sgr A*" : where == Where.YOUNG ? String.format("HIN Y%+04d%+04d-%d", cx, cy, k) : String.format("HIN %+05d%+05d%+04d-%d", cx, cy, cz, k); }
        /** Light-years from the Sun's place. */
        public double lyFromSun() { return Math.sqrt(x * x + sq(y + R0_KPC * 1000) + z * z) * 3.26156; }
    }
    /** The objects of one 10 pc cell (cx, cy, cz count cells from the centre). */
    public List<Ref> cell(long cx, long cy, long cz) {
        double x0 = cx * CELL, y0 = cy * CELL, z0 = cz * CELL, mx = x0 + CELL / 2, my = y0 + CELL / 2, mz = z0 + CELL / 2;
        double dd = disk(mx, my, mz), bb = bulge(mx, my, mz), rho = dd + bb;
        Rng r = new Rng(Rng.key(seed, cx, cy, cz * 0x9E3779B1L + 0xCE11)); int n = r.poisson(rho * CELL * CELL * CELL);
        List<Ref> out = new ArrayList<>(n); double thick = thickShare(mz) * dd / Math.max(1e-30, rho), bul = bb / Math.max(1e-30, rho);
        for (int k = 0; k < n; k++) {
            long key = Rng.key(seed, cx, cy, cz * 0x9E3779B1L + k + 1); Rng s = new Rng(key);
            double x = x0 + CELL * s.u(), y = y0 + CELL * s.u(), z = z0 + CELL * s.u();
            double pop = s.u(), age = pop < bul || pop < bul + thick ? (BULGE_AGE_MIN_GYR + (DISK_AGE_GYR - BULGE_AGE_MIN_GYR) * s.u()) * 1e9 : DISK_AGE_GYR * 1e9 * s.u();
            double mi = imf(s, IMF_MIN, IMF_MAX);
            if (mi >= MASSIVE && age < YOUNG_YR && pop >= bul + thick) continue;   // young massive disk stars come from youngColumn
            out.add(system(Where.CELL, cx, cy, cz, k, x, y, z, mi, age, s, key)); }
        return out;
    }
    Ref system(Where w, long cx, long cy, long cz, int k, double x, double y, double z, double mi, double age, Rng s, long key) {
        Body p = Body.evolve(mi, age, s), c = null; double sep = 0;
        if (s.u() < multiplicity(mi)) { double q = 0.1 + 0.9 * s.u(); double m2 = Math.max(IMF_MIN, q * mi); c = Body.evolve(m2, age, s);
            double logP = LOGP_MEAN + LOGP_SIGMA * s.gauss(), pYr = Math.pow(10, logP) / 365.25; sep = Math.cbrt(pYr * pYr * (mi + m2)); }   // Kepler: a^3 = P^2 M
        return new Ref(w, cx, cy, cz, k, x, y, z, p, c, sep, key); }
    static double multiplicity(double m) { for (int i = 0; i < MULT_MASS.length; i++) if (m < MULT_MASS[i]) return MULT_FREQ[i]; return MULT_FREQ[MULT_FREQ.length - 1]; }

    /** Massive stars (> 8 Msun) born in the last 50 Myr in one 100 pc column: on the arms, close to the plane. */
    public List<Ref> youngColumn(int sx, int sy) {
        double mx = (sx + 0.5) * COLUMN, my = (sy + 0.5) * COLUMN; double xk = mx / 1000, yk = my / 1000, r = Math.hypot(xk, yk); if (r > DISK_EDGE_KPC) return List.of();
        // young stars come from today's star formation (Licquia & Newman 2015), spread like the thin disk, on the arms
        double born = SFR_NOW * YOUNG_YR / MEAN_BIRTH_MASS * imfAbove(MASSIVE);   // massive stars born in the window, whole galaxy
        double mean = born * thinShape(xk, yk) * COLUMN * COLUMN / THIN_TOTAL * armFactor(xk, yk, 1.0);
        Rng rr = new Rng(Rng.key(seed, sx, sy, 0xB0B)); int n = rr.poisson(mean); List<Ref> out = new ArrayList<>(n);
        for (int k = 0; k < n; k++) { long key = Rng.key(seed, sx, sy, 0xB0B0000L + k); Rng s = new Rng(key);
            double x = (sx + s.u()) * COLUMN, y = (sy + s.u()) * COLUMN, z = YOUNG_H_PC * Math.log(Math.max(1e-12, s.u())) * (s.u() < 0.5 ? -1 : 1);
            out.add(system(Where.YOUNG, sx, sy, 0, k, x, y, z, imf(s, MASSIVE, IMF_MAX), YOUNG_YR * s.u(), s, key)); }
        return out; }
    public Ref centre() { return new Ref(Where.CENTRE, 0, 0, 0, 0, 0, 0, 0, Body.sgrA(), null, 0, Rng.key(seed, 0, 0, 0xA)); }

    /** Free-floating planets of one cell: 21 per star (Sumi et al. 2023), masses from their dN/dlogM ~ M^-0.96. Real radius (Chen & Kipping). */
    public record Rogue(double x, double y, double z, double massE, double radiusKm) {}
    public List<Rogue> rogues(long cx, long cy, long cz) {
        int stars = 0; for (Ref f : cell(cx, cy, cz)) if (f.primary.kind != Body.Kind.BROWN_DWARF) stars++;
        Rng r = new Rng(Rng.key(seed, cx, cy, cz * 0x9E3779B1L + 0x2069E)); int n = r.poisson(ROGUES_PER_STAR * stars); List<Rogue> out = new ArrayList<>(n);
        double a = 1 - ROGUE_ALPHA, lo = Math.pow(ROGUE_MIN_ME, a), hi = Math.pow(ROGUE_MAX_ME, a);
        for (int i = 0; i < n; i++) { double m = Math.pow(lo + r.u() * (hi - lo), 1 / a);   // dN/dlogM ~ M^-alpha  ->  dN/dM ~ M^-(1+alpha)
            out.add(new Rogue((cx + r.u()) * CELL, (cy + r.u()) * CELL, (cz + r.u()) * CELL, m, Planet.radius(m) * EARTH_R_KM)); }
        return out; }

    public StarSystem system(Ref ref) { return new StarSystem(ref, new Rng(Rng.mix(ref.key ^ 0x51A7E7L))); }

    /** GAME: the start planet is close to Earth: 0.6-1.5 Earth masses. */
    public static final double SPAWN_MASS_MIN = 0.6, SPAWN_MASS_MAX = 1.5;
    /** Where a new world starts: the nearest G dwarf to the Sun's place with a temperate rocky planet near Earth's mass. */
    public StarSystem spawn() {
        long c0x = 0, c0y = (long) Math.floor(-R0_KPC * 1000 / CELL), c0z = 0;
        for (int ring = 0; ring < 40; ring++) { StarSystem best = null; double bd = Double.MAX_VALUE;
            for (int dx = -ring; dx <= ring; dx++) for (int dy = -ring; dy <= ring; dy++) for (int dz = -Math.min(ring, 3); dz <= Math.min(ring, 3); dz++) {
                if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != ring) continue;
                for (Ref f : cell(c0x + dx, c0y + dy, c0z + dz)) { if (f.primary.kind != Body.Kind.MAIN_SEQUENCE || Body.classOf(f.primary.initialMass) != 4) continue;
                    StarSystem st = system(f); Planet e = st.firstEarthLike(); if (e == null || e.massE < SPAWN_MASS_MIN || e.massE > SPAWN_MASS_MAX) continue;
                    if (!Atmosphere.of(st)[e.index][0].breathable) continue;   // 0.10.0: the start world must have air a person can breathe (oxygen from life)
                    double d = f.lyFromSun(); if (d < bd) { bd = d; best = st; } } }
            if (best != null) return best; }
        return null; }
    /** Last of the class's setup (the IMF weights above must exist): the companion share and the disk normalisation. */
    public static final double COMPANIONS, MEAN_BIRTH_MASS, THIN_TOTAL;
    static { Rng r = new Rng(7); double comp = 0; int n = 200000; for (int i = 0; i < n; i++) if (r.u() < multiplicity(imf(r, IMF_MIN, IMF_MAX))) comp++;
        COMPANIONS = comp / n; N0 = LOCAL_DENSITY / ((1 + THICK_FRACTION) * armFactor(0, -R0_KPC, OLD_ARM) * (1 + COMPANIONS));
        double ms = 0; for (int i = 0; i < n; i++) ms += imf(r, IMF_MIN, IMF_MAX); MEAN_BIRTH_MASS = ms / n;
        double th = 0; for (double x = -15000; x < 15000; x += 100) for (double y = -15000; y < 15000; y += 100) th += thinShape((x + 50) / 1000, (y + 50) / 1000) * 1e4; THIN_TOTAL = th; }
}
