package com.hopeless.infinity.planet;

import java.util.Arrays;

/**
 * 0.15.0 THE HOME MOON'S SURFACE (owner, 2026-11-02: "home moon next of course"). An airless, cratered body; pure Java (tools/MoonTest).
 * The Moon's own record is the reference, scaled to this moon (seed 1: a giant-impact moon of 1,805 km, as Earth's 1,737 km one formed):
 *  - Shape (LRO LOLA, Smith et al. 2010): heights from -9.1 to +10.8 km round the mean radius; the near side about 1.9 km lower than the far
 *    side (the centre of figure is offset from the centre of mass toward the far side): -1.9 km x the near-side direction; long-wavelength
 *    relief and the largest basins on top.
 *  - Craters: diameters from 640 km basins down to 39 m in 15 octaves; the number per area of each octave is the empirical saturation of
 *    lunar highlands, about 5 % of geometric saturation N(>D) = 1.54 D^-2 per km2 (Gault 1970; Melosh 1989, "Impact Cratering"): N(>D)
 *    = 0.077 D^-2 (D in km). That gives about 32 basins over 300 km on a Moon-sized body (the Moon has ~40-50, Wilhelms 1987) and 1.4e-4
 *    craters over 20 km per km2 (Head et al. 2010 counted 5,185 on the Moon: 1.37e-4). Within an octave the sizes follow the same D^-2 law.
 *  - Crater shape (Pike 1977, fresh lunar craters): simple bowls under 10.6 km with depth 0.196 D^1.010 and rim height 0.036 D^1.014;
 *    complex craters above 10.6 km with depth 1.044 D^0.301, rim height 0.236 D^0.399 and a flat floor 0.187 D^1.249 across, with a
 *    central peak (0.25 of the depth, 0.15 of the radius: CHOSEN). Ejecta round the rim thin as (r/R)^-3 (McGetchin, Settle & Head 1973)
 *    to zero at 3 R. Most craters are old and worn: each one's relief is x 0.2-1 (Fassett & Thomson 2014: diffusive degradation; CHOSEN
 *    spread).
 *  - Maria: dark basalt plains in 16 % of the surface (31 % of the near side, 2 % of the far side), the lava that filled the low basins of the
 *    thin near-side crust: where the large-scale height (with the far side counted 2.5 km higher, so the near side fills first) is in its
 *    lowest 16 %, the ground is a flat lava plain at that level, with far fewer craters (maria are ~3.5 Gyr old to the highlands' ~4.3:
 *    1/20 of the larger craters; the smallest, under 250 m, still saturate).
 *  - Temperature: no air to carry heat: the mean of the Diviner day (~390 K) and night (~95 K) surface (Williams et al. 2017), -30 C here
 *    (the generator needs one number; the surface is never ice-capped or wet).
 * The near side faces the home planet (tidally locked): planet space +y (cube face 0's centre) points at it.
 */
public class MoonTerrain extends Terrain {
    public static final double NEAR_LOW_M = 1900, LONG_RELIEF_M = 1500, D0_KM = 640, MARE_FRACTION = 0.16, MARE_FAR_BIAS_M = 2500;
    public static final int OCTAVES = 15;
    final double rKm; final double mareLevel; final Noise3 longN, wrinkle;
    /** 0.18.0 gravity scale: the Moon's gravity over this body's. Crater shapes are self-similar in g: the simple-to-complex transition and the
     *  depths of complex craters scale as 1/g (Pike 1980, "Control of crater morphology by gravity and target type: Mars, Earth, Moon"; the
     *  transition: Moon ~15 km, Mars ~7 km, Earth 2-4 km), so a crater of diameter D here is shaped as the lunar crater D / gScale, x gScale. */
    final double gScale;
    public MoonTerrain(long seed, double radiusKm) { this(seed, radiusKm, 1); }
    public MoonTerrain(long seed, double radiusKm, double gScale) {
        super(seed, -30, true); this.gScale = gScale; rKm = radiusKm; longN = new Noise3(seed ^ 0x10C0A1L); wrinkle = new Noise3(seed ^ 0x3A11EL);
        // the mare level: the 16 % quantile of the mare score over a Fibonacci sphere sample
        int n = 6000; double[] sc = new double[n]; double ga = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < n; i++) { double y = 1 - 2 * (i + 0.5) / n, r = Math.sqrt(1 - y * y), th = ga * i; sc[i] = mareScore(new double[]{r * Math.cos(th), y, r * Math.sin(th)}); }
        Arrays.sort(sc); mareLevel = sc[(int) (MARE_FRACTION * n)]; }

    /** Large-scale height (m): the near-side low, long-wavelength relief and the basins (the three largest crater octaves). */
    public double largeM(double[] s) { return -NEAR_LOW_M * s[1] + LONG_RELIEF_M * longN.fbm(s[0] * 1.3, s[1] * 1.3, s[2] * 1.3, 4) + craters(s, 0, 3, false); }
    /** The far side counted MARE_FAR_BIAS_M higher (its thick crust kept the lava down), so the near side's low basins fill first. */
    double bias(double[] s) { return MARE_FAR_BIAS_M * (1 - s[1]) / 2; }
    double mareScore(double[] s) { return largeM(s) + bias(s); }
    public boolean mare(double[] s) { return mareScore(s) < mareLevel; }

    /** 0.15.0 block heights on the moon: 1 block = 1 m from -2,400 m to +5,000 m (most of its surface: the lowlands and maria near -1..-3 km, the
     *  highlands to +5 km), compressed beyond (deep basin floors x 0.03, the highest far-side peaks x 0.1) to fit y -2560..5631. (The planet's
     *  rule, 1:1 only from -500 m, would squash the moon's craters 5 x: its sea-floor rule.) */
    public static double yd(double h) { double y = h < -2400 ? -2400 + (h + 2400) * 0.03 : h <= 5000 ? h : 5000 + (h - 5000) * 0.1; return Math.max(-2540, Math.min(5600, y)); }
    @Override public double heightM(double[] s, double radiusM) { return rawHeightM(s, radiusM); }
    @Override public double rawHeightM(double[] s, double radiusM) {
        double big = largeM(s);
        if (big + bias(s) < mareLevel) { double floor = mareLevel - bias(s);   // the lava plain: the basin filled up to the fill level
            return floor + 40 * wrinkle.ridged(s[0] * 60, s[1] * 60, s[2] * 60, 3) + craters(s, 3, OCTAVES, true); }   // wrinkle ridges (tens of metres), the young crater population
        return big + craters(s, 3, OCTAVES, false); }
    @Override public double elevationM(double[] s) { return largeM(s); }
    @Override public double temperatureC(double[] s, double hM) { return -30; }
    @Override public double wetness(double[] s) { return 0; }
    @Override public Bio biome(double[] s, double hM) { return Bio.LUNAR; }

    /** The sum of the craters of octaves [o0, o1) at s (m). */
    double craters(double[] s, int o0, int o1, boolean mare) {
        double h = 0;
        for (int o = o0; o < o1; o++) {
            double D = D0_KM / (1 << o), cell = 2 * D / rKm;   // octave diameter (km) and the cell side on the unit sphere
            double per = (0.077 / (D * D) - 0.077 / (4 * D * D)) * (2 * D) * (2 * D);   // craters of this octave per cell (~0.23)
            if (mare) per *= D < 0.25 ? 1 : D < 1 ? 0.25 : 0.05;
            int ci = (int) Math.floor(s[0] / cell), cj = (int) Math.floor(s[1] / cell), ck = (int) Math.floor(s[2] / cell);
            for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) for (int c = -1; c <= 1; c++) {
                long hsh = hash(seed, o, ci + a, cj + b, ck + c); if (u(hsh, 0) >= per) continue;
                double[] cen = {(ci + a + u(hsh, 1)) * cell, (cj + b + u(hsh, 2)) * cell, (ck + c + u(hsh, 3)) * cell}; double l = Math.sqrt(cen[0] * cen[0] + cen[1] * cen[1] + cen[2] * cen[2]);
                if (l < 1e-9 || Math.abs(l - 1) > cell) continue;   // only cells the sphere passes through
                double dk = D / Math.sqrt(1 - u(hsh, 4) * 0.75);   // within the octave: N ~ D^-2 (diameter D..2D)
                double cosA = (s[0] * cen[0] + s[1] * cen[1] + s[2] * cen[2]) / l, r = rKm * Math.acos(Math.max(-1, Math.min(1, cosA)));
                if (r > 1.5 * dk) continue;
                h += gScale * profile(dk / gScale, r / gScale, 0.2 + 0.8 * u(hsh, 5)) * 1000; } }
        return h; }
    /** Pike 1977 crater profile (km): diameter D, distance from the centre r (km), wear factor k (x relief). */
    public static double profile(double D, double r, double k) {
        double R = D / 2, x = r / R, d, hr, floorR = 0;
        if (D < 10.6) { d = 0.196 * Math.pow(D, 1.010); hr = 0.036 * Math.pow(D, 1.014); }
        else { d = 1.044 * Math.pow(D, 0.301); hr = 0.236 * Math.pow(D, 0.399); floorR = Math.min(0.7 * R, 0.187 * Math.pow(D, 1.249) / 2); }
        double z;
        if (x <= 1) {
            if (floorR <= 0) z = hr - d + d * x * x;   // the simple bowl: the floor d below the rim crest, the crest hr over the plain
            else { double xf = floorR / R; if (x <= xf) { z = hr - d; double pk = 0.25 * d * Math.exp(-0.5 * Math.pow(r / (0.15 * R), 2)); z += pk; }
                else { double t = (x - xf) / (1 - xf); z = hr - d + d * t * t * (3 - 2 * t); } } }
        else if (x < 3) z = hr * Math.pow(x, -3) * smoothFade(x); else z = 0;
        return z * k; }
    static double smoothFade(double x) { double t = Math.max(0, Math.min(1, (3 - x) / 2)); return t * t * (3 - 2 * t); }
    static long hash(long seed, int o, int i, int j, int k) { long h = seed * 0x9E3779B97F4A7C15L ^ o * 0xC2B2AE3D27D4EB4FL; h ^= i * 0x165667B19E3779F9L; h = Long.rotateLeft(h, 27) * 0x94D049BB133111EBL; h ^= j * 0x27D4EB2F165667C5L; h = Long.rotateLeft(h, 31) * 0xBF58476D1CE4E5B9L; h ^= k * 0x9E3779B97F4A7C15L; h ^= h >>> 29; return h * 0xD6E8FEB86659FD93L; }
    static double u(long h, int n) { long x = h + n * 0x9E3779B97F4A7C15L; x ^= x >>> 33; x *= 0xFF51AFD7ED558CCDL; x ^= x >>> 33; x *= 0xC4CEB9FE1A85EC53L; x ^= x >>> 33; return (x >>> 11) * 0x1.0p-53; }
}
