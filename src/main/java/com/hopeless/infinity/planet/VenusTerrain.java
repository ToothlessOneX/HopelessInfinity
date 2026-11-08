package com.hopeless.infinity.planet;

import java.util.Arrays;

/**
 * 0.18.0 A THICK-AIRED ROCKY WORLD'S GROUND (Venus's record; owner, 2026-11-03: "what about the rest of the planets"). Pure (tools/BodiesTest).
 *  - Heights (Magellan altimetry, Ford & Pettengill 1992): 80 % of the surface within 1 km of the median radius, about 10 % above 2 km (the
 *    highlands), the whole range about 13 km, the highest peak (Maxwell Montes) about 11 km. Here: rolling plains (fbm, its height spread
 *    fitted at start so the plains alone hold 80 % within 1 km), highland plateaus on the top 10 % of a broad mask (+2.5 km, CHOSEN, to put
 *    ~10 % over 2 km) with tessera ridges on them (Venus's deformed highland crust), and mountain belts on the mask's top 0.5 % (to ~11 km).
 *  - Volcanoes: more than 100 large shields across Venus, on average about 1.5 km high, 100-700 km across (here 0.5-3 km high, CHOSEN spread).
 *  - Coronae: about 360 across Venus (Stofan et al. 1992), 100-300 km rings: a raised rim (0.6 km, CHOSEN) round a sunken middle.
 *  - Impact craters: about 940 across Venus (Magellan; Schaber et al. 1992), none under ~2 km (the thick air breaks up small bodies), few
 *    under 30 km: N(>D) ~ D^-2 above 16 km, flattened below (CHOSEN shape); mostly fresh (62 % pristine, Schaber 1992: relief x 0.6-1).
 *    Shapes: Pike's lunar craters scaled by gravity (MoonTerrain.gScale).
 *  The counts are per Venus area (4.60e8 km2) and scale with this body's area.
 *  MAGMA worlds (surface over the basalt solidus: PlanetWorld.greenhouseK) use the same ground with lava seas filling the lowest
 *  meltFraction of the surface (all of it at the liquidus).
 */
public class VenusTerrain extends Terrain {
    public static final double VENUS_AREA = 4.60e8, VOLCANOES = 100, CORONAE = 360, CRATERS = 940, PLATEAU_M = 2500, PLATEAU_SHARE = 0.10, BELT_SHARE = 0.005;
    final double rKm, gScale, plateauCut, beltCut; double plainAmp; public final double lavaLevelM, melt; final Noise3 plains, hl, tess;
    public VenusTerrain(long seed, double radiusKm, double gravity, double melt, double tempC) {
        super(seed, tempC, true); rKm = radiusKm; gScale = 1.62 / gravity; this.melt = melt; plains = new Noise3(seed ^ 0xBE705L); hl = new Noise3(seed ^ 0x416A1L); tess = new Noise3(seed ^ 0x7E55EL);
        int n = 6000; double ga = Math.PI * (3 - Math.sqrt(5)); double[] pv = new double[n], mv = new double[n];
        for (int i = 0; i < n; i++) { double[] s = fib(i, n, ga); pv[i] = plainsRaw(s); mv[i] = mask(s); }
        double[] pd = pv.clone(); Arrays.sort(pd); double med = pd[n / 2]; double[] dev = new double[n]; for (int i = 0; i < n; i++) dev[i] = Math.abs(pv[i] - med); Arrays.sort(dev);
        double amp = 1000 / dev[(int) (0.8 * n)]; Arrays.sort(mv); plateauCut = mv[(int) ((1 - PLATEAU_SHARE) * n)]; beltCut = mv[(int) ((1 - BELT_SHARE) * n)];
        // the plateaus, volcanoes, coronae and craters widen the spread: fit the plains' height so the whole surface holds 80 % within 1 km (2 passes)
        int m = 1500; double[][] sm = new double[m][]; double[] fe = new double[m], lg = new double[m]; for (int i = 0; i < m; i++) { sm[i] = fib(i, m, ga); fe[i] = features(sm[i]); }
        for (int pass = 0; pass < 2; pass++) { plainAmp = amp; double[] hv = new double[m]; for (int i = 0; i < m; i++) hv[i] = largeM(sm[i]) + fe[i]; double[] hs = hv.clone(); Arrays.sort(hs); double md = hs[m / 2]; double[] dv = new double[m]; for (int i = 0; i < m; i++) dv[i] = Math.abs(hv[i] - md); Arrays.sort(dv);
            amp *= Math.pow(1000 / dv[(int) (0.8 * m)], 1.5); }
        plainAmp = amp;
        if (melt > 0) { double[] hv = new double[n]; for (int i = 0; i < n; i++) hv[i] = rawHeightM(fib(i, n, ga), radiusKm * 1000); Arrays.sort(hv); lavaLevelM = melt >= 1 ? 1e9 : hv[(int) Math.min(n - 1, melt * n)]; }
        else lavaLevelM = Double.NEGATIVE_INFINITY; }
    static double[] fib(int i, int n, double ga) { double y = 1 - 2 * (i + 0.5) / n, r = Math.sqrt(1 - y * y), th = ga * i; return new double[]{r * Math.cos(th), y, r * Math.sin(th)}; }
    double plainsRaw(double[] s) { return plains.fbm(s[0] * 2.2, s[1] * 2.2, s[2] * 2.2, 7); }
    double mask(double[] s) { return hl.fbm(s[0] * 1.1, s[1] * 1.1, s[2] * 1.1, 4); }
    static double smooth(double a, double b, double x) { double t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }
    /** Plains, highland plateaus with tessera ridges, mountain belts (m). */
    public double largeM(double[] s) {
        double h = plainAmp * plainsRaw(s), m = mask(s), up = smooth(plateauCut - 0.02, plateauCut + 0.02, m);
        if (up > 0) h += up * (PLATEAU_M + 900 * tess.ridged(s[0] * 30, s[1] * 30, s[2] * 30, 4));
        double belt = smooth(beltCut - 0.01, beltCut + 0.03, m); if (belt > 0) h += belt * 6000 * tess.ridged(s[0] * 12, s[1] * 12, s[2] * 12, 3);
        return h; }
    public boolean tessera(double[] s) { return mask(s) > plateauCut; }
    @Override public double heightM(double[] s, double radiusM) { return rawHeightM(s, radiusM); }
    @Override public double rawHeightM(double[] s, double radiusM) { return largeM(s) + features(s); }
    @Override public double elevationM(double[] s) { return largeM(s); }
    @Override public double temperatureC(double[] s, double hM) { return t0C; }
    @Override public double wetness(double[] s) { return 0; }
    @Override public Bio biome(double[] s, double hM) { return melt > 0 ? Bio.MAGMA : Bio.VENUSIAN; }
    public boolean lava(double hM) { return hM < lavaLevelM; }

    /** Volcanoes, coronae and craters at s (m). */
    double features(double[] s) {
        double area = 4 * Math.PI * rKm * rKm / VENUS_AREA, h = 0;
        h += kind(s, 0, 350, VOLCANOES * area, 100, 700);
        h += kind(s, 1, 200, CORONAE * area, 100, 300);
        double ntot = CRATERS * area / 1.45;   // N(>D) = ntot f(D): f = (D/16)^-2 over 16 km, 1 + 0.15 log2(16/D) below; f(2 km) = 1.45
        for (int o = 0; o < 8; o++) { double D = 256.0 / (1 << o), N1 = ntot * f(D), N2 = ntot * f(2 * D); h += kind(s, 10 + o, D, N1 - N2, D, 2 * D); }
        return h; }
    static double f(double D) { return D >= 16 ? Math.pow(D / 16, -2) : 1 + 0.15 * Math.log(16 / D) / Math.log(2); }
    /** One kind of feature (type 0 shield, 1 corona, 10+ crater octave): count n over the whole body, diameters dLo..dHi (km), cells sized for size (km). */
    double kind(double[] s, int type, double size, double n, double dLo, double dHi) {
        double cell = 2 * size / rKm, per = n / (4 * Math.PI / (cell * cell)); if (per <= 0) return 0; double h = 0;
        int ci = (int) Math.floor(s[0] / cell), cj = (int) Math.floor(s[1] / cell), ck = (int) Math.floor(s[2] / cell);
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) for (int c = -1; c <= 1; c++) {
            long hsh = MoonTerrain.hash(seed, 50 + type, ci + a, cj + b, ck + c); if (MoonTerrain.u(hsh, 0) >= per) continue;
            double[] cen = {(ci + a + MoonTerrain.u(hsh, 1)) * cell, (cj + b + MoonTerrain.u(hsh, 2)) * cell, (ck + c + MoonTerrain.u(hsh, 3)) * cell}; double l = Math.sqrt(cen[0] * cen[0] + cen[1] * cen[1] + cen[2] * cen[2]);
            if (l < 1e-9 || Math.abs(l - 1) > cell) continue;
            double D = type >= 10 ? dLo / Math.sqrt(1 - MoonTerrain.u(hsh, 4) * 0.75) : dLo + (dHi - dLo) * MoonTerrain.u(hsh, 4), R = D / 2;
            double cosA = (s[0] * cen[0] + s[1] * cen[1] + s[2] * cen[2]) / l, r = rKm * Math.acos(Math.max(-1, Math.min(1, cosA))), x = r / R; if (x > 3) continue;
            if (type == 0) { double hh = 500 + 2500 * MoonTerrain.u(hsh, 5); if (x < 1) h += hh * Math.pow(1 - x, 1.5) - (x < 0.04 ? hh * 0.05 : 0); }   // a shield, its summit caldera
            else if (type == 1) { double rim = Math.exp(-Math.pow((x - 1) / 0.12, 2)); h += 600 * rim - (x < 1 ? 300 * (1 - x * x) : 0); }
            else h += gScale * MoonTerrain.profile(D / gScale, r / gScale, 0.6 + 0.4 * MoonTerrain.u(hsh, 5)) * 1000; }
        return h; }
}
