package com.hopeless.infinity.planet;

import java.util.Arrays;

/**
 * The surface of one rocky planet at real size (1 block = 1 m across the ground). Pure Java (no game classes) so the mod, the tests
 * and the preview renderer run the same code.
 *
 *  elevation   continent-scale fractal noise on the sphere, ranked against a 40,000-point sample of the same planet and pushed through
 *              Earth's hypsometric curve (ETOPO5, NOAA NGDC 1988): Earth's land/sea split (29.7 % land) and spread of heights and depths.
 *  detail      13 octaves from 200 km down to 30 m with amplitude ~ wavelength^0.5: Earth's topography has a power spectrum ~ k^-2
 *              (Huang & Turcotte 1989, JGR 94, 7491); mountains rough, plains and ocean floors smooth.
 *  height      real metres become blocks 1:1 from -500 m to +4,000 m (99.5 % of land); deeper ocean floor x0.2, higher peaks x0.3, so the
 *              world fits y -2560..5631 (the client keeps every section in view; a full -11 km..+9 km world would cost ~2.5x more).
 *  temperature mean annual T(lat) = T0 + T2 P2(sin lat) (North 1975: T2 = -28.0 C), minus 6.5 K per km (ICAO standard atmosphere). T0 comes
 *              from where the planet sits in its habitable zone (PlanetWorld: Kopparapu 2013 limits).
 *  wetness     the Hadley/Ferrel/polar cell pattern (wet equator, dry 30 deg, wet 50-60 deg, dry poles; shape of GPCP zonal means) + noise.
 */
public class Terrain {
    public static final int SEA = 0;
    public static final double T2 = -28.0, LAPSE_K_PER_KM = 6.5, HURST = 0.5, DETAIL_L0_M = 200000, DETAIL_MIN_M = 30;
    /** ETOPO5 cumulative fraction of Earth's surface below each elevation (m), from the 500 m table. */
    static final double[] HYPSO_M =   {-9000, -7000, -6500, -6000, -5500, -5000, -4500, -4000, -3500, -3000, -2500, -2000, -1500, -1000, -500,   0,     500,   1000,  1500,  2000,  2500,  3000,  3500,  4000,  4500,  5000,  5500};
    static final double[] HYPSO_CUM = {0.000, 0.001, 0.001, 0.005, 0.045, 0.140, 0.258, 0.373, 0.470, 0.531, 0.567, 0.588, 0.605, 0.617, 0.631, 0.703, 0.861, 0.919, 0.950, 0.965, 0.976, 0.985, 0.991, 0.995, 0.996, 0.998, 1.000};
    static final double[] WET_LAT = {1.0, 0.85, 0.45, 0.20, 0.35, 0.65, 0.70, 0.45, 0.25, 0.15};

    static final int KNOTS = 512;
    final Noise3 cont, mount, detail, wet, temp; final double[] rankTable; /** surface gravity (m/s2) for the shore's waves (PlanetWorld sets it) */ public volatile double g = 9.81; /** 0.20.0 the body's radius (m), set by PlanetWorld */ public volatile double radiusM = 6.371e6; public final double t0C; public final long seed; final int octaves;

    public Terrain(long seed, double t0C) {
        this.seed = seed; this.t0C = t0C; cont = new Noise3(seed ^ 0xC0A71L); mount = new Noise3(seed ^ 0x5EAA7L); detail = new Noise3(seed ^ 0xDE7A1L); wet = new Noise3(seed ^ 0x3E7L); temp = new Noise3(seed ^ 0x7E3FL);
        octaves = (int) Math.ceil(Math.log(DETAIL_L0_M / DETAIL_MIN_M) / Math.log(2));
        int n = 40000; double[] s = new double[n]; double ga = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < n; i++) { double y = 1 - 2 * (i + 0.5) / n, r = Math.sqrt(1 - y * y), th = ga * i; s[i] = shape(r * Math.cos(th), y, r * Math.sin(th)); }
        Arrays.sort(s); rankTable = new double[KNOTS + 1];   // a smooth quantile curve: 512 knots, each the mean of ~78 samples (single samples sit at random gaps and make cliffs)
        for (int k = 0; k <= KNOTS; k++) { int i = (int) Math.round((double) k / KNOTS * (n - 1)), lo2 = Math.max(0, i - 39), hi2 = Math.min(n - 1, i + 39); double m = 0; for (int j = lo2; j <= hi2; j++) m += s[j]; rankTable[k] = k == 0 ? s[0] : k == KNOTS ? s[n - 1] : m / (hi2 - lo2 + 1); }
    }
    /** 0.15.0: for bodies with their own height rules (MoonTerrain): no hypsometric table. */
    protected Terrain(long seed, double t0C, boolean own) { this.seed = seed; this.t0C = t0C; cont = mount = detail = wet = temp = new Noise3(seed ^ 0xC0A71L); rankTable = new double[]{0}; octaves = 0; }
    double shape(double x, double y, double z) {
        double c = cont.fbm(x * 1.7, y * 1.7, z * 1.7, 6), m = mount.ridged(x * 3.1, y * 3.1, z * 3.1, 5);
        return c + 0.35 * m * smooth(-0.05, 0.25, c); }
    /** Large-scale elevation in metres (Earth's hypsometry). */
    public double elevationM(double[] s) {
        double v = shape(s[0], s[1], s[2]); int i = Arrays.binarySearch(rankTable, v); if (i < 0) i = -i - 1;
        double q; if (i <= 0) q = 0; else if (i > KNOTS) q = 1; else { double a = rankTable[i - 1], b = rankTable[i]; q = (i - 1 + (b > a ? (v - a) / (b - a) : 0.5)) / KNOTS; }
        return hypso(Math.max(0, Math.min(1, q))); }
    static double hypso(double q) {
        for (int i = 1; i < HYPSO_CUM.length; i++) if (q <= HYPSO_CUM[i]) { double t = (q - HYPSO_CUM[i - 1]) / Math.max(1e-9, HYPSO_CUM[i] - HYPSO_CUM[i - 1]); return HYPSO_M[i - 1] + t * (HYPSO_M[i] - HYPSO_M[i - 1]); }
        return HYPSO_M[HYPSO_M.length - 1]; }
    /** Ground height in real metres: large-scale elevation plus fractal detail (radiusM = planet radius in metres), finished at the coast by
     *  the shore process (0.7.0, Shore: waves rework the shore into an equilibrium beach, or cut a platform under a rock coast). */
    public double heightM(double[] s, double radiusM) { return heightAndWater(s, radiusM)[0]; }
    /** 0.21.0 {ground height (m, rivers cut in), the water surface over it (m: a river or lake; NaN none, the sea is not counted), kind 0 none / 1
     *  river channel / 2 lake / 3 salt flat}. */
    public double[] heightAndWater(double[] s, double radiusM) {
        double[] p = parts(s, radiusM); double raw = p[0] + p[1] + p[2];
        double h = !shores || !Shore.near(p[0] + p[1], g) ? raw : Shore.finish(this, s, radiusM, p, raw);
        Rivers rv = rivers; if (rv == null || !riversOn || h < -50) return new double[]{h, Double.NaN, 0};
        double[] r = rv.at(s, h); return new double[]{Math.max(Math.min(h, r[0]), r[3]), r[1], r[2]}; }   // 0.29.8 the rivers cut (cap) and fill (floor: dips under a bed, low banks)
    /** 0.21.0 the rivers and lakes (PlanetWorld builds them for planets with seas); riversOn: everything with them. */
    public volatile Rivers rivers; public static volatile boolean riversOn = true;
    /** 0.29.8 the ground before the rivers cut it (the shore process applied): the height a river's level is measured against. */
    public double groundBeforeRivers(double[] s, double radiusM) { double[] p = parts(s, radiusM); double raw = p[0] + p[1] + p[2]; return !shores || !Shore.near(p[0] + p[1], g) ? raw : Shore.finish(this, s, radiusM, p, raw); }
    /** The height without the shore process (tests and the shore's own sums). */
    public double rawHeightM(double[] s, double radiusM) { double[] p = parts(s, radiusM); return p[0] + p[1] + p[2]; }
    public static volatile boolean shores = true;
    /** {large-scale elevation, detail octaves of COAST_L_M and longer, the shorter ones}: the first two make the coast the waves shape. */
    double[] parts(double[] s, double radiusM) {
        double e = elevationM(s), rug = e > 0 ? 0.1 + 0.9 * smooth(300, 3000, e) : 0.1 + 0.15 * smooth(0, -30, e);   // GAME: ruggedness 0.1 plains .. 1 mountains, 0.25 ocean floor; 0.7.0: no step at e = 0 (it was 0.1 above and 0.25 below: a 10+ m step all along every coast); 0.7.1: the floor roughens over the first 30 m down (over 200 m it smoothed away the islands and spits off every coast)
        double lo = 0, hi = 0, L = DETAIL_L0_M, a0 = 600 * rug;                               // GAME: 600 m of relief at 200 km in mountains
        for (int o = 0; o < octaves; o++) { double k = radiusM / L, v = a0 * Math.pow(L / DETAIL_L0_M, HURST) * detail.at(s[0] * k + o * 13.1, s[1] * k - o * 7.7, s[2] * k + o * 3.3); if (L >= Shore.COAST_L_M) lo += v; else hi += v; L /= 2; }
        if (e > 0 && volcanicOn) hi += volcanoM(s, radiusM, e);   // 0.20.0 volcanoes (land only; the short part: the shore leaves them)
        return new double[]{e, lo, hi}; }
    // ---------------- 0.20.0 VOLCANIC PROVINCES (owner, 2026-11-03: "Add some interesting biomes ... like the volcanic biome") ----------------
    /* Where: belts of a broad noise mask on VOLC_SHARE of the land (CHOSEN 6 %: Earth's ~1,350 Holocene volcanoes, Smithsonian GVP, sit mostly in
     *   arcs and rifts a few hundred km wide). In them:
     *  - cinder cones, 0.13 a km2 (the San Francisco volcanic field, Arizona: ~600 vents on ~4,700 km2, USGS), base 0.3-2 km across, height 0.18 x
     *    the base and a summit crater 0.40 x the base (Wood 1980, "Morphometric evolution of cinder cones", JVGR 7), slopes at the angle of repose;
     *  - stratovolcanoes, one per ~2,500 km2 of province (CHOSEN, an arc's spacing of tens of km), 1.5-3.5 km high and 10-20 km in radius, a concave
     *    profile (h = H (1 - r/R)^2, steepening to the summit as Mount Fuji's does), a summit crater 0.4-1 km across; one in ten holds a lava lake
     *    (CHOSEN: Earth has ~8 persistent lava lakes, Erta Ale, Nyiragongo, Kilauea ...);
     *  - young lava flows (bare basalt, the VOLCANIC biome) on 40 % of the province (CHOSEN), old flows under the climate's own cover elsewhere. */
    public static volatile boolean volcanicOn = true;
    public static final double VOLC_SHARE = 0.06, CONES_PER_KM2 = 0.13, STRATO_KM2 = 2500, YOUNG_SHARE = 0.40, LAVA_LAKE_P = 0.10;
    private volatile double volcCut = Double.NaN;
    double volcMask(double[] s) { return mount.fbm(s[0] * 5.3 + 31.1, s[1] * 5.3 - 8.8, s[2] * 5.3 + 2.4, 3); }
    double volcCut() { double c = volcCut; if (!Double.isNaN(c)) return c; int n = 6000, m = 0; double ga = Math.PI * (3 - Math.sqrt(5)); double[] v = new double[n];
        for (int i = 0; i < n; i++) { double y = 1 - 2 * (i + 0.5) / n, r = Math.sqrt(1 - y * y), th = ga * i; double[] p = {r * Math.cos(th), y, r * Math.sin(th)}; if (elevationM(p) > 0) v[m++] = volcMask(p); }
        double[] l = Arrays.copyOf(v, Math.max(1, m)); Arrays.sort(l); volcCut = c = l[(int) ((1 - VOLC_SHARE) * (l.length - 1))]; return c; }
    /** How far inside a volcanic province s is (0 outside, 1 well inside: features fade in over the edge). */
    public double volcanic(double[] s) { return smooth(volcCut() - 0.005, volcCut() + 0.02, volcMask(s)); }
    public boolean youngFlow(double[] s) { return volcanic(s) > 0.5 && wet.at(s[0] * 60 + 3.3, s[1] * 60 - 1.1, s[2] * 60 + 7.7) > -0.25 + 0.5 * (1 - 2 * YOUNG_SHARE); }
    /** The volcano heights at s (m) and what is there: kind[0] 0 none, 1 cinder cone, 2 stratovolcano, 3 inside a lava-lake crater (then kind[1] = the lake level, m). */
    public double volcanoM(double[] s, double R, double e) { return volcano(s, R, null); }
    public double volcano(double[] s, double R, double[] kind) {
        double vi = volcanic(s); if (vi <= 0) return 0; double rKm = R / 1000, h = 0, best = 0;
        double area = 4 * Math.PI * rKm * rKm; int k0 = 0;
        // stratovolcanoes: cells sized for a 40 km spacing
        { double size = 25, cell = 2 * size / rKm, per = cell * cell * rKm * rKm / STRATO_KM2; int ci = (int) Math.floor(s[0] / cell), cj = (int) Math.floor(s[1] / cell), ck = (int) Math.floor(s[2] / cell);
          for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) for (int c = -1; c <= 1; c++) { long hs = MoonTerrain.hash(seed, 31, ci + a, cj + b, ck + c); if (MoonTerrain.u(hs, 0) >= per) continue;
            double[] cen = {(ci + a + MoonTerrain.u(hs, 1)) * cell, (cj + b + MoonTerrain.u(hs, 2)) * cell, (ck + c + MoonTerrain.u(hs, 3)) * cell}; double l = Math.sqrt(cen[0] * cen[0] + cen[1] * cen[1] + cen[2] * cen[2]); if (l < 1e-9 || Math.abs(l - 1) > cell) continue;
            double[] cu = {cen[0] / l, cen[1] / l, cen[2] / l}; if (volcanic(cu) < 0.5) continue;   // its centre inside the province
            double H = 1500 + 2000 * MoonTerrain.u(hs, 4), Rv = 10 + 10 * MoonTerrain.u(hs, 5), crR = 0.2 + 0.3 * MoonTerrain.u(hs, 6), r = rKm * Math.acos(Math.max(-1, Math.min(1, s[0] * cu[0] + s[1] * cu[1] + s[2] * cu[2])));
            if (r >= Rv) continue; double x = r / Rv, v = H * (1 - x) * (1 - x), rim = H * (1 - crR / Rv) * (1 - crR / Rv); boolean lake = MoonTerrain.u(hs, 7) < LAVA_LAKE_P;
            if (r < crR) { double floor = rim - 150 - 150 * MoonTerrain.u(hs, 8); v = floor + (rim - floor) * Math.pow(r / crR, 4); if (lake && kind != null) { kind[0] = 3; kind[1] = rim - 40; } k0 = 2; }   // the summit crater; a lake 40 m under its rim
            if (v > best) { best = v; if (kind != null && kind[0] != 3) kind[0] = 2; } } }
        // cinder cones: cells sized for a 3 km spacing
        { double size = 1.5, cell = 2 * size / rKm, per = cell * cell * rKm * rKm * CONES_PER_KM2; int ci = (int) Math.floor(s[0] / cell), cj = (int) Math.floor(s[1] / cell), ck = (int) Math.floor(s[2] / cell);
          for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) for (int c = -1; c <= 1; c++) { long hs = MoonTerrain.hash(seed, 37, ci + a, cj + b, ck + c);
            for (int q = 0; q < 2; q++) { if (MoonTerrain.u(hs, 10 * q) >= per / 2) continue;   // up to two a cell (per ~0.6)
              double[] cen = {(ci + a + MoonTerrain.u(hs, 10 * q + 1)) * cell, (cj + b + MoonTerrain.u(hs, 10 * q + 2)) * cell, (ck + c + MoonTerrain.u(hs, 10 * q + 3)) * cell}; double l = Math.sqrt(cen[0] * cen[0] + cen[1] * cen[1] + cen[2] * cen[2]); if (l < 1e-9 || Math.abs(l - 1) > cell) continue;
              double W = 0.3 + 1.7 * Math.pow(MoonTerrain.u(hs, 10 * q + 4), 2), r = rKm * Math.acos(Math.max(-1, Math.min(1, (s[0] * cen[0] + s[1] * cen[1] + s[2] * cen[2]) / l))), Rb = W / 2; if (r >= Rb) continue;
              double H = 0.18 * W * 1000, Rc = 0.40 * W / 2, v = r <= Rc ? H - 0.35 * H * (1 - r / Rc) * (1 - r / Rc) : H * (1 - (r - Rc) / (Rb - Rc));   // Wood 1980 cone with its crater (0.35 H deep, CHOSEN)
              v *= vi; if (v > h) { h = v; if (kind != null && kind[0] == 0) kind[0] = 1; } } } }
        return Math.max(best * vi, h); }
    /** The coast field: the elevation with the detail of COAST_L_M and longer (its zero line is the shoreline the waves shape). */
    double coastM(double[] s, double radiusM) { double[] p = parts(s, radiusM); return p[0] + p[1]; }
    /** Real metres to the block y of the top solid block. */
    public static int toY(double h) { return (int) Math.floor(toYd(h)); }
    /** The same, continuous: the true ground lies at toYd + 1 (the top of block floor(toYd) plus the fraction a terrain slope adds on top). */
    public static double toYd(double h) {
        double y = h >= 0 ? (h <= 4000 ? h : 4000 + (h - 4000) * 0.3) : (h >= -500 ? h : -500 + (h + 500) * 0.2);
        return Math.max(-2540, Math.min(5600, y)); }
    public int surfaceY(double[] s, double radiusM) { return toY(heightM(s, radiusM)); }
    public double temperatureC(double[] s, double hM) {
        double sl = s[2], p2 = (3 * sl * sl - 1) / 2;
        return t0C + T2 * p2 - LAPSE_K_PER_KM * Math.max(0, hM) / 1000 + 2.0 * temp.fbm(s[0] * 4, s[1] * 4, s[2] * 4, 3); }
    public double wetness(double[] s) {
        double lat = Math.abs(CubeSphere.lat(s)) / 10; int i = Math.min(8, (int) lat); double z = WET_LAT[i] + (WET_LAT[i + 1] - WET_LAT[i]) * (lat - i);
        return Math.max(0, Math.min(1, z + 0.35 * wet.fbm(s[0] * 3, s[1] * 3, s[2] * 3, 4))); }

    public enum Bio {
        DEEP_FROZEN_OCEAN("deep_frozen_ocean"), FROZEN_OCEAN("frozen_ocean"), DEEP_COLD_OCEAN("deep_cold_ocean"), COLD_OCEAN("cold_ocean"), DEEP_OCEAN("deep_ocean"), OCEAN("ocean"),
        DEEP_LUKEWARM_OCEAN("deep_lukewarm_ocean"), LUKEWARM_OCEAN("lukewarm_ocean"), WARM_OCEAN("warm_ocean"), BEACH("beach"), SNOWY_BEACH("snowy_beach"), STONY_SHORE("stony_shore"),
        ICE_SPIKES("ice_spikes"), SNOWY_PLAINS("snowy_plains"), SNOWY_TAIGA("snowy_taiga"), TAIGA("taiga"), OLD_GROWTH_SPRUCE_TAIGA("old_growth_spruce_taiga"), PLAINS("plains"),
        FOREST("forest"), BIRCH_FOREST("birch_forest"), DARK_FOREST("dark_forest"), FLOWER_FOREST("flower_forest"), SWAMP("swamp"), SAVANNA("savanna"), DESERT("desert"),
        JUNGLE("jungle"), SPARSE_JUNGLE("sparse_jungle"), BADLANDS("badlands"), MEADOW("meadow"), WINDSWEPT_HILLS("windswept_hills"), SNOWY_SLOPES("snowy_slopes"),
        STONY_PEAKS("stony_peaks"), JAGGED_PEAKS("jagged_peaks"), FROZEN_PEAKS("frozen_peaks"), THE_VOID("the_void"), LUNAR("hopelessinfinity:lunar"),
        VENUSIAN("hopelessinfinity:venusian"), MAGMA("hopelessinfinity:magma"),
        VOLCANIC("hopelessinfinity:volcanic"), MANGROVE_SWAMP("mangrove_swamp"), CHERRY_GROVE("cherry_grove"), BAMBOO_JUNGLE("bamboo_jungle"),
        RIVER("river"), FROZEN_RIVER("frozen_river");   // 0.21.0   // 0.20.0   // 0.18.0 thick-aired worlds: solid ground, and molten
        public final String key; Bio(String k) { key = k; }
        /** 0.18.0 a body with no water: the moon's kind of height rule (MoonTerrain.yd) and no sea. */
        public boolean dry() { return this == LUNAR || this == VENUSIAN || this == MAGMA; }
    }
    /** Biome from the real height (m), temperature and wetness; bands follow the Whittaker biome diagram in broad steps. */
    public Bio biome(double[] s, double hM) {
        double t = temperatureC(s, Math.max(0, hM)), w = wetness(s);
        if (hM < 0) { boolean deep = hM < -1500;   // below the shelf-slope break
            if (t < -2) return deep ? Bio.DEEP_FROZEN_OCEAN : Bio.FROZEN_OCEAN; if (t < 8) return deep ? Bio.DEEP_COLD_OCEAN : Bio.COLD_OCEAN;
            if (t < 20) return deep ? Bio.DEEP_OCEAN : Bio.OCEAN; if (t < 25) return deep ? Bio.DEEP_LUKEWARM_OCEAN : Bio.LUKEWARM_OCEAN; return deep ? Bio.DEEP_LUKEWARM_OCEAN : Bio.WARM_OCEAN; }
        if (hM <= 3) return t < 0 ? Bio.SNOWY_BEACH : (w < 0.2 && t > 22 ? Bio.DESERT : t >= 20 && wetRank(w) > 0.5 ? Bio.MANGROVE_SWAMP : Bio.BEACH);   // 0.20.0 mangroves on wet tropical shores
        if (volcanicOn && hM < 3000 && (youngFlow(s) || (volcanic(s) > 0.5 && volcano(s, radiusM, null) > 800))) return Bio.VOLCANIC;   // 0.20.0 young lava fields, and a stratovolcano's bare upper slopes (ash and lava rock above ~800 m of cone, CHOSEN)
        if (hM > 3000) return t < -6 ? (w > 0.5 ? Bio.FROZEN_PEAKS : Bio.JAGGED_PEAKS) : Bio.STONY_PEAKS;
        if (hM > 2000) return t < -2 ? Bio.SNOWY_SLOPES : (w > 0.5 ? Bio.MEADOW : Bio.WINDSWEPT_HILLS);
        if (hM > 600 && t >= 6 && t < 16 && wetRank(w) > 0.55 && wet.at(s[0] * 25 - 4.4, s[1] * 25 + 6.6, s[2] * 25) > 0.45) return Bio.CHERRY_GROVE;   // 0.20.0 humid temperate mountain slopes (wild mountain cherry, Prunus jamasakura, grows in such hills); patchy (CHOSEN)
        if (t < -12) return w < 0.3 ? Bio.ICE_SPIKES : Bio.SNOWY_PLAINS;
        if (t < -3) return w > 0.45 ? Bio.SNOWY_TAIGA : Bio.SNOWY_PLAINS;
        // 0.19.1 dryness by rank: a column's wetness ranked among all the land's gives its aridity zone with the shares of Earth's land
        // (UNEP World Atlas of Desertification, Middleton & Thomas 1997, by aridity index): hyper-arid 7.5 % + arid 12.1 % (deserts), semi-arid
        // 17.7 % + dry sub-humid 9.9 % (grassland, steppe, savanna), the rest humid (forests); the kind of each from the temperature as in the
        // Whittaker (1975) biome diagram (desert, temperate grassland or savanna, temperate forest or tropical forest).
        double q = wetRank(w), aridC = ARID / EARTH_WARM, dryC = DRY / EARTH_WARM;
        if (t < 5) return q < aridC ? Bio.PLAINS : q > 0.85 ? Bio.OLD_GROWTH_SPRUCE_TAIGA : Bio.TAIGA;   // boreal: cold steppe, else taiga
        if (q < aridC) return hM > 1000 ? Bio.BADLANDS : Bio.DESERT;   // hot and cold deserts (cold ones: the Great Basin, the Gobi)
        if (q < dryC) return t < 20 ? Bio.PLAINS : Bio.SAVANNA;
        if (t < 20) { if (q > 0.9 && hM < 60) return Bio.SWAMP; if (q > 0.88) return Bio.DARK_FOREST;   // wet lowland; temperate rainforest
            double v = wet.at(s[0] * 40, s[1] * 40 + 9.1, s[2] * 40); return v > 0.6 ? Bio.FLOWER_FOREST : q > 0.65 ? Bio.FOREST : Bio.BIRCH_FOREST; }   // flower forest a patchy few (CHOSEN)
        return q > 0.93 ? Bio.BAMBOO_JUNGLE : q > 0.75 ? Bio.JUNGLE : Bio.SPARSE_JUNGLE;   // 0.20.0 the wettest tropics: bamboo forest (CHOSEN share)
    }
    /** EARTH_WARM: the share of Earth's land not polar, tundra or boreal (WWF biomes: tundra ~8 %, boreal ~11 %, Antarctica and Greenland
     *  ice ~10 %: ~70 % left, CHOSEN round figure); the aridity shares above are of all land, so of the warm land they are / 0.70. */
    public static final double ARID = 0.075 + 0.121, DRY = ARID + 0.177 + 0.099, EARTH_WARM = 0.70;
    private volatile double[] landWet; private volatile double warmScale = 1;
    /** The share of the warm land (5 C or more: where Earth's aridity zones are counted dry) drier than wetness w (a 4,000-point sample, sorted
     *  once); warmScale = all land / warm land, so the cuts give Earth's shares of ALL the land (a colder planet has less warm land to hold them). */
    public double wetRank(double w) { double[] l = landWet; if (l == null) { int n = 4000, m = 0, all = 0; double ga = Math.PI * (3 - Math.sqrt(5)); double[] v = new double[n];
            for (int i = 0; i < n; i++) { double y = 1 - 2 * (i + 0.5) / n, r = Math.sqrt(1 - y * y), th = ga * i; double[] p = {r * Math.cos(th), y, r * Math.sin(th)}; double e = elevationM(p); if (e <= 0) continue; all++; if (temperatureC(p, e) >= 5) v[m++] = wetness(p); }
            l = java.util.Arrays.copyOf(v, Math.max(1, m)); java.util.Arrays.sort(l); warmScale = all / (double) Math.max(1, m); landWet = l; }
        int k = java.util.Arrays.binarySearch(l, w); if (k < 0) k = -k - 1; return k / (double) l.length; }
    public double precipScale() { return Double.NaN; }   // (0.19.1 first try: a linear wetness-to-rain scale could not give both Earth's deserts and its forests; kept for BiomesTest's line)
    static double smooth(double a, double b, double v) { double t = Math.max(0, Math.min(1, (v - a) / (b - a))); return t * t * (3 - 2 * t); }
}
