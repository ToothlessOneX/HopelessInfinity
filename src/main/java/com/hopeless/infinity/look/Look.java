package com.hopeless.infinity.look;

import com.hopeless.infinity.galaxy.*;
import com.hopeless.infinity.planet.*;

/**
 * How each kind of space object looks: colour per point of its surface, from its generated data. Pure Java (no game classes): the preview
 * renderer uses it now, the sky and space renderers will use the same code. Colours are linear 0-1 RGB.
 *
 *  stars        blackbody colour of Teff (Helland's fit to the CIE blackbody locus); limb darkening I = 1 - u (1 - mu), u = 0.6 in the visible
 *               (the classic solar value); giants show convection cells (noise), Wolf-Rayet stars a hot blue-white disk.
 *  rocky planet the planet's own Terrain (heights, biomes, ice) with clouds covering 67 % of the sky, 55 % over land and 72 % over sea
 *               (King et al. 2013, MODIS) and a thin blue limb of Rayleigh scattering.
 *  giants       Sudarsky, Burrows & Hubeny 2000 classes by temperature: I ammonia clouds (< 150 K, Jupiter-like tan/white bands), II water clouds
 *               (~250 K, bright white), III clear (> 350 K, deep blue: little to reflect), IV alkali (900-1500 K, very dark), V silicate clouds (> 1500 K,
 *               grey with a red thermal glow); ice giants methane blue-cyan (Uranus, Neptune). Bands follow latitude, broken by turbulence.
 *  rings        radial optical depth with gaps; bright (Saturn-type, icy) or faint (dusty).
 *  small bodies asteroids under ~400 km irregular (not round: self-gravity too weak), C-type dark (albedo ~0.06) or S-type (~0.2); craters.
 */
public final class Look {
    private Look() {}
    public static final double LIMB_U = 0.6;
    static final Noise3 N1 = new Noise3(0xB00B5L), N2 = new Noise3(0xC10D5L), N3 = new Noise3(0x5A75L);

    public static double[] blackbody(double t) {
        double k = Math.max(10, t) / 100, r, g, b;
        r = k <= 66 ? 255 : 329.698727446 * Math.pow(k - 60, -0.1332047592); g = k <= 66 ? 99.4708025861 * Math.log(k) - 161.1195681661 : 288.1221695283 * Math.pow(k - 60, -0.0755148492);
        b = k >= 66 ? 255 : k <= 19 ? 0 : 138.5177312231 * Math.log(k - 10) - 305.0447927307;
        return new double[]{c01(r / 255), c01(g / 255), c01(b / 255)}; }

    /** A star's disk at view-space normal n (n[2] = mu toward the viewer). */
    public static double[] star(Body b, double[] n, long seed) {
        double mu = Math.max(0, n[2]), limb = 1 - LIMB_U * (1 - mu); double[] c = blackbody(b.teff);
        double cells = 0;
        if (b.kind == Body.Kind.GIANT || b.kind == Body.Kind.SUPERGIANT) cells = 0.35 * N1.fbm(n[0] * 5 + seed % 97, n[1] * 5, n[2] * 5, 4);   // big convection cells (Betelgeuse-like)
        else if (b.kind == Body.Kind.MAIN_SEQUENCE) cells = 0.06 * N1.fbm(n[0] * 40, n[1] * 40, n[2] * 40, 3);                                  // granulation, faint at this scale
        double k = limb * (1 + cells); return new double[]{c[0] * k, c[1] * k, c[2] * k}; }

    /** The rocky planet seen from space at unit sphere point s (planet frame), lit by sun direction L (planet frame); n = view-space normal for the limb. */
    public static double[] rocky(PlanetWorld w, double[] s, double[] L, double[] n) {
        double h = w.terrain.heightM(s, w.cube.R); double t = w.terrain.temperatureC(s, Math.max(0, h));
        double[] c;
        if (h < 0) { if (t < -2) c = new double[]{0.82, 0.88, 0.93}; else { double d = Math.min(1, -h / 4000); c = mix(new double[]{0.10, 0.30, 0.45}, new double[]{0.01, 0.05, 0.16}, Math.sqrt(d)); } }
        else { Terrain.Bio b = w.terrain.biome(s, h); c = switch (b) {
                case DESERT, BEACH -> new double[]{0.76, 0.66, 0.46}; case BADLANDS -> new double[]{0.62, 0.36, 0.20}; case SAVANNA -> new double[]{0.55, 0.52, 0.30};
                case JUNGLE, SPARSE_JUNGLE -> new double[]{0.10, 0.28, 0.08}; case DARK_FOREST -> new double[]{0.09, 0.22, 0.08}; case FOREST, BIRCH_FOREST, FLOWER_FOREST, SWAMP -> new double[]{0.17, 0.33, 0.12};
                case TAIGA, OLD_GROWTH_SPRUCE_TAIGA -> new double[]{0.14, 0.25, 0.15}; case PLAINS, MEADOW -> new double[]{0.33, 0.45, 0.20}; case WINDSWEPT_HILLS, STONY_PEAKS, JAGGED_PEAKS, STONY_SHORE -> new double[]{0.45, 0.43, 0.40};
                default -> new double[]{0.90, 0.93, 0.96}; };   // snow and ice
            if (t < -4 && b != Terrain.Bio.DESERT) c = mix(c, new double[]{0.92, 0.95, 0.98}, Math.min(1, (-4 - t) / 6)); }
        // relief light from the ground slope (2 km sampling)
        double e = 2000 / w.cube.R; double[] sx = norm(new double[]{s[0] + e, s[1], s[2]}), sz = norm(new double[]{s[0], s[1], s[2] + e});
        double dhx = (Math.max(0, w.terrain.heightM(sx, w.cube.R)) - Math.max(0, h)) / 2000, dhz = (Math.max(0, w.terrain.heightM(sz, w.cube.R)) - Math.max(0, h)) / 2000;
        double ndl = dot(s, L), relief = h > 0 ? Math.max(0.6, Math.min(1.4, 1 - 6 * (dhx * L[0] + dhz * L[2]))) : 1;
        // clouds: noise thresholded to King et al.'s cover (72 % sea, 55 % land), thicker where it is wet
        double q = cloudRank(N2.fbm(s[0] * 3.5, s[1] * 3.5, s[2] * 3.5, 6)) + 0.10 * (w.terrain.wetness(s) - 0.5), cover = h < 0 ? 0.72 : 0.55;   // rank of the cloud noise: cover = share of the sky
        double cloud = Math.pow(smooth(1 - cover, 1, q), 0.55) * 0.95;   // cover counts thin cloud too: opacity rises from the cloud edge (thin) to the core (thick)
        double day = Math.max(0, ndl), lit = 0.02 + 0.98 * day;
        double[] ground = scale(c, lit * relief), withCloud = mix(ground, new double[]{lit, lit, lit}, cloud);
        double rim = Math.pow(1 - Math.max(0, n[2]), 3) * day;                       // Rayleigh limb
        return add(withCloud, scale(new double[]{0.25, 0.45, 0.95}, 0.6 * rim)); }

    /** Where a cloud-noise value ranks (0-1) among the values over the whole sphere (measured once, 20,000 equal-area points), so a threshold at
     *  1 - cover clouds exactly that share of the sky. */
    static double cloudRank(double v) { int i = java.util.Arrays.binarySearch(CLOUD_RANK, v); if (i < 0) i = -i - 1; return i / (double) CLOUD_RANK.length; }
    static final double[] CLOUD_RANK; static { int n = 20000; CLOUD_RANK = new double[n]; double ga = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < n; i++) { double y = 1 - 2 * (i + 0.5) / n, r = Math.sqrt(1 - y * y), th = ga * i; CLOUD_RANK[i] = N2.fbm(r * Math.cos(th) * 3.5, y * 3.5, r * Math.sin(th) * 3.5, 6); } java.util.Arrays.sort(CLOUD_RANK); }
    /** Gas, ice giants and mini-Neptunes at sphere point s, lit by L; seed varies the bands. */
    public static double[] giant(Planet p, double[] s, double[] L, double[] n, long seed) {
        double lat = s[1], turb = 0.15 * N3.fbm(s[0] * 6 + seed % 31, s[1] * 6, s[2] * 6, 5);
        double band = Math.sin((lat + turb) * Math.PI * 7) * 0.5 + 0.5;
        double[] a, b;
        if (p.kind == Planet.Kind.GAS_GIANT) {
            if (p.teq < 150) { a = new double[]{0.85, 0.80, 0.70}; b = new double[]{0.70, 0.50, 0.32}; }          // I ammonia (Jupiter)
            else if (p.teq < 350) { a = new double[]{0.95, 0.95, 0.96}; b = new double[]{0.82, 0.84, 0.88}; }      // II water clouds: bright
            else if (p.teq < 900) { a = new double[]{0.12, 0.25, 0.55}; b = new double[]{0.08, 0.18, 0.42}; }      // III clear: deep blue
            else if (p.teq < 1500) { a = new double[]{0.08, 0.07, 0.08}; b = new double[]{0.05, 0.04, 0.05}; }     // IV alkali: very dark
            else { a = new double[]{0.55, 0.52, 0.50}; b = new double[]{0.45, 0.40, 0.38}; } }                    // V silicate clouds
        else if (p.kind == Planet.Kind.ICE_GIANT) { a = new double[]{0.55, 0.80, 0.86}; b = new double[]{0.30, 0.55, 0.82}; band = 0.5 + 0.15 * (band - 0.5); }   // methane: Uranus to Neptune
        else { a = new double[]{0.78, 0.80, 0.84}; b = new double[]{0.66, 0.70, 0.76}; band = 0.5 + 0.1 * (band - 0.5); }                                        // mini-Neptune: hazy
        double[] c = mix(b, a, band); double day = Math.max(0, dot(s, L)), limb = 0.75 + 0.25 * Math.max(0, n[2]);
        double[] col = scale(c, (0.02 + 0.98 * day) * limb);
        if (p.kind == Planet.Kind.GAS_GIANT && p.teq >= 1500) col = add(col, scale(new double[]{0.6, 0.12, 0.02}, 0.35));   // a planet that hot glows
        return col; }

    /** Ring optical depth (0-1) at r (in planet radii) for a planet's rings. */
    public static double ring(Planet p, double r) {
        if (p.rings == Planet.Rings.NONE) return 0; double in = p.ringInKm / p.radiusKm, out = p.ringOutKm / p.radiusKm; if (r < in || r > out) return 0;
        double x = (r - in) / (out - in), d = p.rings == Planet.Rings.BRIGHT ? 0.85 : 0.25;
        double struct = 0.65 + 0.35 * N1.at(r * 40, 3.1, 7.7) + 0.2 * N1.at(r * 200, 1.3, 2.2);
        if (p.rings == Planet.Rings.BRIGHT && x > 0.70 && x < 0.76) struct *= 0.08;   // a Cassini-Division-like gap (Saturn's lies about 70 % of the way out of the main rings)
        return c01(d * struct * smooth(0, 0.05, x) * smooth(1, 0.95, x)); }

    /** An irregular small body: radius factor of the surface in direction d (unit), from its size (round above ~400 km). */
    public static double bodyRadius(double diameterKm, double[] d, long seed) {
        // small bodies are lumpy and stretched (Ida 60 x 25 x 19 km, Eros 34 x 11 x 11 km); their lightcurves give a typical b/a ~ 0.7: axes 1 : 0.75 : 0.6
        double irr = diameterKm >= 400 ? 0.02 : diameterKm >= 150 ? 0.08 : 0.16, el = diameterKm >= 400 ? 0 : diameterKm >= 150 ? 0.5 : 1;
        double ax = 1, ay = 1 - 0.25 * el, az = 1 - 0.40 * el, ell = 1 / Math.sqrt(d[0] * d[0] / (ax * ax) + d[1] * d[1] / (ay * ay) + d[2] * d[2] / (az * az));
        double r = ell * (1 + irr * N2.fbm(d[0] * 1.3 + seed % 53, d[1] * 1.3, d[2] * 1.3, 3));
        double crater = Math.max(0, N3.at(d[0] * 4 + seed % 17, d[1] * 4, d[2] * 4) - 0.35); r -= 0.6 * crater * crater * Math.max(0.03, irr);   // bowl craters
        return r; }

    static double c01(double v) { return Math.max(0, Math.min(1, v)); }
    static double smooth(double a, double b, double v) { double t = c01((v - a) / (b - a)); return t * t * (3 - 2 * t); }
    static double[] mix(double[] a, double[] b, double t) { return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t}; }
    static double[] scale(double[] a, double k) { return new double[]{a[0] * k, a[1] * k, a[2] * k}; }
    static double[] add(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double[] norm(double[] a) { double l = Math.sqrt(dot(a, a)); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }
}
