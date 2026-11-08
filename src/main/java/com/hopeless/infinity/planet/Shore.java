package com.hopeless.infinity.planet;

/**
 * 0.7.0 THE SHORE PROCESS: the coast finished by the waves, as GaianProphesy 0.2.0 does it for our Space Engineers planets (Sections/Ocean.cs,
 * the owner's choice there: "Waves rework the seabed"), so the water meets a shore it has made. Owner's reference (vault, 2026-10-19 real beach
 * video): spilling bores over a wide surf zone on a gently sloping sandy beach -- "the look depends on the BEACH SLOPE as much as on the water".
 * Measured before this: the seed 1 spawn coast's beach slope 0.32 (17 deg; Tides log 2026-11-02 08:13), so the surf was 2 blocks wide.
 *
 *  waves      NOAA buoy 41025, Diamond Shoals (the station Tides' swell is from), 2015-2019, read with Tides' tools/buoy_stats.js
 *             2026-11-02: He (exceeded 12 h a year) 6.21 m, its period Te 11.43 s; mean Hs 1.53 m, median period 8.33 s. A planet: heights
 *             and periods x 9.81 / g (as Tides scales its swell).
 *  LOOSE      a BEACH: Dean's equilibrium profile h = A x^(2/3) (Dean 1977; A = 0.21 D50^0.48, D50 in mm) out to the closure depth
 *             hc = 2.28 He - 68.5 He^2 / (g Te^2) (Hallermeier 1978), then back to the old seabed over BLEND_M; the grain size D50 is the
 *             one whose profile reaches hc where the coast does (the sand sorts itself to its shore: Bascom 1951), fine sand 0.1 mm to
 *             cobbles 64 mm (Wentworth 1922); this keeps the sand roughly balanced across the shore; beach face tan(beta) =
 *             0.12 (Hb / (g^0.5 D^0.5 T))^-0.5 (Sunamura 1984; Hb the mean Hs), berm at the 2 % run-up (Stockdon et al. 2006), 20 m wide;
 *             land behind the berm stands. The short detail (under COAST_L_M) is the roughness the waves wear away: it goes with the change.
 *  ROCK       where the land rises steeply behind the shore (ROCK_RISE m within ROCK_RUN m: CHOSEN, the planet has no rock map yet): a
 *             CLIFF behind a WAVE-CUT PLATFORM at -1 m sloping 0.01 seaward, cut into whatever stands above it (GaianProphesy's values),
 *             PLAT_W 200 m wide (CHOSEN in the range of real shore platforms, tens to hundreds of metres: Trenhaile 1987).
 *  shoreline  the zero line of the coast field (the elevation with the detail of COAST_L_M and longer); the distance to it is c / |grad c|
 *             (1st order, from 20 m differences). Inside the band the shorter detail is faded out with the shore's change, so the beach
 *             is smooth; beyond the band the ground is as before.
 *  NOT DONE (GaianProphesy has them): the exact cross-shore sand balance (Bruun) and the Holocene retreat limit, the 120 m along-shore
 *  smoothing; the grain-size fit stands in for the balance.
 */
public final class Shore {
    private Shore() {}
    public static final double COAST_L_M = 400, STEP_M = 20, G = 9.81;
    public static final double HE = 6.21, TE = 11.43, HS_MEAN = 1.53, T_MEAN = 8.33, D50_MM = 0.25, BERM_W = 20, PLAT_Z = -1, PLAT_S = 0.01;
    public static final double PLAT_W = 200, ROCK_RISE = 60, ROCK_RUN = 300, BLEND_M = 40, D50_MIN = 0.1, D50_MAX = 64;
    public static volatile double lastD50;
    public static volatile long beaches, rocks, columns, hardCols;
    public static final double HARD_L_M = 40000, HARD_W = 0.06, CLIFF_MIN = 8, CLIFF_MAX = 35;

    public static double deanA(double d50mm) { return 0.21 * Math.pow(Math.max(0.06, d50mm), 0.48); }
    public static double closure(double He, double Te) { return Math.max(0, 2.28 * He - 68.5 * He * He / (G * Te * Te)); }
    public static double faceSlope(double Hb, double T, double d50mm) { double D = d50mm / 1000, x = Math.max(1e-6, Hb) / (Math.sqrt(G) * Math.sqrt(D) * Math.max(0.5, T)); return 0.12 * Math.pow(x, -0.5); }
    public static double runUp2(double Hs, double T, double tanB) {
        double L0 = G * T * T / (2 * Math.PI), HL = Math.max(0, Hs) * L0, xi = tanB / Math.sqrt(Math.max(1e-9, Hs) / L0);
        if (xi < 0.3) return 0.043 * Math.sqrt(HL);
        return 1.1 * (0.35 * tanB * Math.sqrt(HL) + Math.sqrt(HL * (0.563 * tanB * tanB + 0.004)) / 2); }

    /** The beach for a planet of gravity g: {A, hc, xc, tan beta, berm}. */
    public static double[] beach(double g) { double k = G / g, He = HE * k, Te = TE * k, Hs = HS_MEAN * k, T = T_MEAN * k;
        double A = deanA(D50_MM), hc = closure(He, Te), xc = Math.pow(hc / A, 1.5), tb = Math.max(0.01, Math.min(0.3, faceSlope(Hs, T, D50_MM))), berm = Math.max(0.1, runUp2(Hs, T, tb));
        return new double[]{A, hc, xc, tb, berm}; }
    static volatile double cachedG = Double.NaN; static volatile double[] cached;
    static double[] beachFor(double g) { if (g != cachedG) { cached = beach(g); cachedG = g; } return cached; }

    /** Is a coast-field height within reach of the shore's change (the shoreface down to the closure depth and the wedge's foot under it;
     *  the beach up to its berm and the blend behind it)? Elsewhere the ground is unchanged and costs nothing more. */
    static boolean near(double c, double g) { double[] b = beachFor(g); return c > -(b[1] + 40) && c < 40; }
    /** The finished height of a column near the coast. p: Terrain.parts (e, long detail, short detail); raw = their sum. */
    static double finish(Terrain t, double[] s, double R, double[] p, double raw) {
        double c = p[0] + p[1];
        double[] t1 = tangent(s), t2 = cross(s, t1); double d = STEP_M / R;
        double cx = t.coastM(norm(add(s, t1, d)), R), cz = t.coastM(norm(add(s, t2, d)), R), gx = (cx - c) / STEP_M, gz = (cz - c) / STEP_M, gl = Math.hypot(gx, gz);
        if (!(gl > 1e-9)) return raw;
        double[] b = beachFor(t.g); double glMin = b[1] / Math.pow(b[1] / deanA(D50_MIN), 1.5);   // the gentlest coast a beach can have (fine sand all the way to the closure depth)
        double ge = Math.max(gl, glMin), x = -c / ge;   // metres from the shoreline, + seaward (a flatter coast field: x as on the gentlest beach, so x never jumps)
        // rock or loose: the coast field ROCK_RUN landward of the shoreline
        double[] up = {(gx * t1[0] + gz * t2[0]) / gl, (gx * t1[1] + gz * t2[1]) / gl, (gx * t1[2] + gz * t2[2]) / gl};   // toward higher ground
        double land = t.coastM(norm(add(s, up, (x + ROCK_RUN) / R)), R);
        // the sand sorts itself to the shore it has (Bascom 1951: the coarser the sand, the steeper the beach; Dean's A grows with grain size):
        // the grain whose equilibrium profile reaches the closure depth where the coast field does, A = hc^(1/3) gl^(2/3), D50 = (A / 0.21)^(1/0.48),
        // from fine sand (0.1 mm) to cobbles (64 mm, the top of gravel: Wentworth 1922); steeper than cobbles can make, or a steep hinterland:
        // a rock coast. The two blend over a margin (no seam where one becomes the other).
        double A = Math.cbrt(b[1]) * Math.pow(ge, 2.0 / 3.0), d50raw = Math.pow(A / 0.21, 1 / 0.48), d50 = Math.max(D50_MIN, Math.min(D50_MAX, d50raw)); A = deanA(d50);
        double rockW = Math.max(smooth((land - (ROCK_RISE - 20)) / 40), smooth(Math.log(d50raw / (D50_MAX / 2)) / Math.log(4)));
        // 0.20.0 HARD-ROCK COASTS (owner, 2026-11-03: "Add the cliffs"): about half of the world's coastline is cliffed (Young & Carilli 2019,
        // "Global distribution of coastal cliffs", ESPL 44); here stretches of hard rock tens of km long (a noise mask, half the coast) stand as a
        // CLIFF over the wave-cut platform: the ground rises to CLIFF_H within 5 m of the shoreline and keeps that height 150 m inland, then blends
        // back to the land over 150 m more (heights 8-35 m, CHOSEN: typical sea cliffs are tens of metres).
        double kq = R / HARD_L_M, hard = smooth((t.mount.at(s[0] * kq + 71.3, s[1] * kq - 12.9, s[2] * kq + 40.1) + HARD_W) / (2 * HARD_W));
        double cliffH = CLIFF_MIN + (CLIFF_MAX - CLIFF_MIN) * (0.5 + 0.5 * t.mount.at(s[0] * kq * 3 - 5.5, s[1] * kq * 3 + 2.2, s[2] * kq * 3 + 9.9));
        rockW = Math.max(rockW, hard); if (hard > 0.5) hardCols++;
        double xc = Math.pow(b[1] / A, 1.5), k = G / t.g, tb = Math.max(0.01, Math.min(0.3, faceSlope(HS_MEAN * k, T_MEAN * k, d50))), berm = Math.max(0.1, runUp2(HS_MEAN * k, T_MEAN * k, tb));
        columns++; lastD50 = d50; if (rockW > 0.5) rocks++; else beaches++;
        double loose, wl;   // the beach's height and its weight over the old ground
        if (x >= 0) { loose = -A * Math.pow(Math.min(x, xc), 2.0 / 3.0); wl = x <= xc ? 1 : 1 - smooth((x - xc) / BLEND_M); }   // past the closure depth the old seabed returns
        else { double back = -x, zone = berm / tb + BERM_W; loose = Math.min(berm, tb * back); wl = 1 - smooth((back - zone) / BLEND_M); }   // behind the berm the land returns (a bluff where it is high)
        double beach = raw + (loose + p[2] - raw) * wl;   // 0.7.1: the profile replaces the coast's long shape (e + lo) only; the short detail (islands, bars, under 400 m) stays on it
        double bluff = x < 0 ? cliffH * smooth(-x / 5) * (1 - smooth((-x - 150) / 150)) : 0;
        double cliff = x < 0 ? raw + Math.max(0, bluff - raw) * hard : raw + (Math.min(raw, PLAT_Z - PLAT_S * x) - raw) * smooth(x / 4) * (1 - smooth((x - PLAT_W) / BLEND_M));   // the platform cut into what stands above it, PLAT_W wide; the land behind the cliff stands
        double target = beach + (cliff - beach) * rockW, inBand = 1;
        return raw + (target - raw) * inBand; }
    static double smooth(double u) { u = Math.max(0, Math.min(1, u)); return u * u * (3 - 2 * u); }
    static double[] tangent(double[] s) { double[] a = Math.abs(s[2]) < 0.9 ? new double[]{0, 0, 1} : new double[]{1, 0, 0}; return norm(cross(a, s)); }
    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double[] norm(double[] a) { double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }
    static double[] add(double[] a, double[] b, double k) { return new double[]{a[0] + b[0] * k, a[1] + b[1] * k, a[2] + b[2] * k}; }
}
