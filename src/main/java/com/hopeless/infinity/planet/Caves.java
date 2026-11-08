package com.hopeless.infinity.planet;

import java.util.Arrays;

/**
 * 0.19.0 CAVES (owner, 2026-11-03: "Caves next"). Pure (tools/CavesTest). Two real kinds, each where it forms:
 *
 *  KARST (solution) CAVES - rain water, made acid by soil CO2, dissolving carbonate rock:
 *   - where: carbonate rocks crop out on 15.2 % of Earth's ice-free land (Goldscheider et al. 2020, "Global distribution of carbonate rocks and
 *     karst water resources", Hydrogeology J. 28): here the top 15.2 % of a broad rock mask over the land, and only where water is (wetness over
 *     0.2, CHOSEN: dry carbonate country has few caves) on a body with liquid water (the EARTH kind);
 *   - how much: Mammoth Cave, the longest system, has ~686 km of surveyed passages under its 214 km2 park, about 3 km of passage per km2;
 *     passages are typically 1-10 m across (Palmer 2007, "Cave Geology"); here noodle passages (where two noise fields both cross zero: long
 *     winding tubes, as caves branch and loop) sized and spaced to give that length density (CavesTest measures it), squashed to run
 *     level (caves form in levels along the water table: Mammoth's five), with rare big chambers (CHOSEN);
 *   - depth: from 6 blocks under the ground to 250 m down (CHOSEN: most caves lie within a few hundred m; the deepest known, Veryovkina, 2,212 m);
 *     below the water table they are full of water (phreatic caves: here the water table is the sea level, the base level, CHOSEN);
 *   - entrances: where a passage comes within 6 m of the ground in an entrance spot (2D mask, ~5 % of the karst, CHOSEN): sinkholes and mouths;
 *   - speleothems: dripstone (calcite deposited by dripping water) hangs from 3 % of the ceilings and rises from 2 % of the floors (CHOSEN).
 *
 *  LAVA TUBES - the roofed channels of basalt lava flows, left hollow when the lava drained:
 *   - where: basalt plains: the moons' maria (the Marius Hills pit, 65 m wide, opens into one: Haruyama et al. 2009) and Venus-like plains;
 *     not on the home planet yet (no volcanic fields modelled);
 *   - size: on Earth most are 1-30 m wide (Kazumura Cave, Hawaii, 65 km long); a tube's stable width grows as 1 / g (Blair et al. 2017,
 *     Icarus 282: lunar tubes could stand kilometres wide; Sauro et al. 2020, Earth-Sci. Rev.): here a typical width of 10 m x (9.81 / g)
 *     (0.5-2 x along a tube, CHOSEN): ~60 m on the Moon, matching its pits; height 0.6 of the width and a roof 0.4 of the width thick (CHOSEN);
 *   - layout: sinuous lines (noise zero-lines) ~8 km apart in the basalt (CHOSEN), broken into segments (collapses), with skylights (roof
 *     collapse pits) where a second mask allows (CHOSEN, ~1 per few km of tube).
 */
public final class Caves {
    public static final double KARST_SHARE = 0.152, KARST_WET = 0.2, BAND_TOP = 6, BAND_DEPTH = 250, Y_SQUASH = 2.5;
    public static double NOODLE_L = 600, NOODLE_T = 0.016, CHAMBER_L = 220, CHAMBER_T = 0.78, CHAMBER_P = 1.0;   // GAME (owner, 2026-11-03: "Unnaturally large caves are OK"): passages ~1.5 x the 0.01 that matched Mammoth, a big hall in every km2   // tuned in tools/CavesTest
    public static final double ENTRANCE_SHARE = 0.05, DRIP_CEIL = 0.03, DRIP_FLOOR = 0.02;
    public static final double TUBE_W_EARTH = 10, TUBE_SPACING = 8000, TUBE_H = 0.6, TUBE_ROOF = 0.4;
    final PlanetWorld w; final Noise3 rock, n1, n2, ch, ent, tube, seg, sky, wid, seaN; public final double rockCut, entCut; public final boolean karstOn, tubesOn; public final double tubeW;
    public Caves(PlanetWorld w) {
        this.w = w; long s = w.seed ^ 0xCA7E5L * (w.body + 7);
        rock = new Noise3(s ^ 0x11L); n1 = new Noise3(s ^ 0x22L); n2 = new Noise3(s ^ 0x33L); ch = new Noise3(s ^ 0x44L); ent = new Noise3(s ^ 0x55L);
        tube = new Noise3(s ^ 0x66L); seg = new Noise3(s ^ 0x77L); sky = new Noise3(s ^ 0x88L); wid = new Noise3(s ^ 0x99L); seaN = new Noise3(s ^ 0xAAL);
        karstOn = w.surface == PlanetWorld.Surface.EARTH; tubesOn = w.surface == PlanetWorld.Surface.CRATERED || w.surface == PlanetWorld.Surface.VENUS || w.surface == PlanetWorld.Surface.EARTH;   // 0.20.0 the home planet too: under its young lava fields (basalt)
        tubeW = TUBE_W_EARTH * 9.81 / w.gravity;
        // the karst cut: the 15.2 % quantile of the rock mask over the land (a Fibonacci sample)
        int n = 6000; double ga = Math.PI * (3 - Math.sqrt(5)); double[] v = new double[n]; int k = 0;
        if (karstOn) for (int i = 0; i < n; i++) { double y = 1 - 2 * (i + 0.5) / n, r = Math.sqrt(1 - y * y), th = ga * i; double[] p = {r * Math.cos(th), y, r * Math.sin(th)}; if (w.terrain.heightM(p, w.cube.R) > 0) v[k++] = rockMask(p); }
        if (k > 10) { double[] l = Arrays.copyOf(v, k); Arrays.sort(l); rockCut = l[(int) ((1 - KARST_SHARE) * k)]; } else rockCut = 1e9;
        entCut = 0.86; }   // simplex noise is about uniform in its middle: |n| over 0.86 is ~5 % of it (CavesTest measures it)
    public double rockMask(double[] s) { return rock.fbm(s[0] * 9, s[1] * 9, s[2] * 9, 4); }
    /** Carbonate country at the sphere point s (land only; the column checks wetness). */
    public boolean karst(double[] s, double wet) { return karstOn && wet > KARST_WET && rockMask(s) > rockCut; }

    /** Karst: is block (x, y, z) of face f open cave, for a column whose ground is at surfaceY? (y in metres = blocks on the karst bodies.) */
    public boolean karstVoid(int f, int x, int y, int z, int surfaceY) {
        int depth = surfaceY - y; if (depth > BAND_DEPTH || depth < 0) return false;
        double X = (x + f * 1.0e7) / NOODLE_L, Y = y * Y_SQUASH / NOODLE_L, Z = z / NOODLE_L;
        boolean v = Math.abs(n1.at(X, Y, Z)) < NOODLE_T && Math.abs(n2.at(X + 31.7, Y - 11.3, Z + 7.1)) < NOODLE_T;
        if (!v && depth > 20) v = chamber(f, x, y, z, surfaceY);
        if (!v) return false;
        if (depth < BAND_TOP) return entrance(f, x, z);
        return true; }
    /** Big chambers: half of the 1 km cells of karst hold one (CHOSEN: big rooms are rare; Sarawak Chamber, the largest, 600 x 415 x 80 m), an
     *  ellipsoid 30-150 m across (GAME: larger than nature's usual) and 0.4 as tall, 30-200 m down. */
    boolean chamber(int f, int x, int y, int z, int surfaceY) {
        int cx = Math.floorDiv(x, 1000), cz = Math.floorDiv(z, 1000);
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) { long h = MoonTerrain.hash(w.seed, 700 + f, cx + a, cz + b, 0); if (MoonTerrain.u(h, 0) >= CHAMBER_P) continue;
            double r = 15 + 60 * MoonTerrain.u(h, 1), px = (cx + a + MoonTerrain.u(h, 2)) * 1000, pz = (cz + b + MoonTerrain.u(h, 3)) * 1000, py = surfaceY - 30 - 170 * MoonTerrain.u(h, 4);
            double dx = (x - px) / r, dz = (z - pz) / r, dy = (y - py) / (0.4 * r); if (dx * dx + dy * dy + dz * dz < 1) return true; }
        return false; }
    public boolean entrance(int f, int x, int z) { return Math.abs(ent.at((x + f * 1.0e7) / 400.0, 0.5, z / 400.0)) > entCut; }

    /** Lava tube in the column (f, x, z) with its ground at surfaceY (block y): {lowest y, highest y} of the open space, or null. */
    public int[] tube(int f, int x, int z, int surfaceY, boolean basalt) {
        if (!tubesOn || !basalt) return null; double L = TUBE_SPACING, X = (x + f * 1.0e7) / L, Z = z / L;
        double n = tube.at(X, 0.3, Z), e = 1.0 / L, gx = (tube.at(X + e, 0.3, Z) - tube.at(X - e, 0.3, Z)) / 2, gz = (tube.at(X, 0.3, Z + e) - tube.at(X, 0.3, Z - e)) / 2, g = Math.hypot(gx, gz);
        if (g < 1e-12) return null; double d = Math.abs(n) / g;   // metres to the tube's centre line
        double W = tubeW * Math.pow(2, wid.at(X * 3, 0.7, Z * 3)), half = W / 2; if (d >= half) return null;
        if (seg.at(X * 4, 1.9, Z * 4) < -0.35) return null;   // a collapsed or never-roofed stretch
        double H = TUBE_H * W, roof = TUBE_ROOF * W, yc = surfaceY - roof - H / 2, hh = H / 2 * Math.sqrt(1 - (d / half) * (d / half));
        int lo = (int) Math.round(yc - hh), hi = (int) Math.round(yc + hh); if (hi < lo) return null;
        if (sky.at(X * 6, 2.3, Z * 6) > 0.82) hi = surfaceY;   // a skylight: the roof fell in
        return new int[]{lo, hi}; }
    /**
     * 0.19.2 SEA CAVES: waves cut into a weak zone (a joint, fault or dike) at the foot of a sea cliff (Wikipedia "Sea cave", after Bunnell 1988:
     * most are a single passage or chamber, surveyed ones over 200 m are rare, ~45 worldwide; Painted Cave's entrance is almost 40 m high;
     * they open at the waterline). Here: weak zones are straight-ish lines ~60 m apart in random directions (a noise zero-line field, CHOSEN);
     * where one meets a cliff (ground 6 m or more over the sea), a slot 6-20 m wide is cut in from the water up to SEA_L = 80 m inland (CHOSEN:
     * GAME-large, owner 2026-11-03), from 4 m under the water to 4-22 m over it, lowering inland, with 3 m of rock kept over it; the part
     * under sea level is sea water. dSea: this column's distance to open water (m), from the chunk's coarse coast grid.
     */
    public static final double SEA_L = 80, SEA_SPACING = 60;
    /** A joint that a sea cave follows: its line (point, unit way inland), half width, mouth height, and where it meets open water. */
    record Joint(double px, double pz, double ux, double uz, double half, double mouth) {}
    static final Joint NONE = new Joint(0, 0, 0, 0, 0, 0);
    final java.util.Map<Long, Joint> joints = java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(4096, 0.75f, true) { @Override protected boolean removeEldestEntry(java.util.Map.Entry<Long, Joint> e) { return size() > 8192; } });
    /** The joint of a 60 m cell (null: none, or it meets no sea within reach). It is walked once in 8 m steps both ways up to SEA_L + 40 m to the
     *  first open water (a column with water over it); the cave runs from there inland along the joint. Pure in the cell: chunks agree. */
    Joint joint(int f, int cx, int cz) {
        long key = ((long) f << 58) ^ ((long) cx << 29) ^ (cz & 0x1FFFFFFFL); Joint j = joints.get(key); if (j != null) return j == NONE ? null : j;
        long h = MoonTerrain.hash(w.seed, 900 + f, cx, cz, 7); Joint out = null;
        if (MoonTerrain.u(h, 0) < SEA_P) { double px = (cx + MoonTerrain.u(h, 1)) * SEA_SPACING, pz = (cz + MoonTerrain.u(h, 2)) * SEA_SPACING, th = 2 * Math.PI * MoonTerrain.u(h, 3), ux = Math.cos(th), uz = Math.sin(th);
            Column c0 = Column.of(w, f, (int) px, (int) pz);
            if (!c0.voidCol && c0.surfaceY >= Terrain.SEA + 6) for (int k = 1; k * 8 <= SEA_L + 40 && out == null; k++) for (int sg = -1; sg <= 1 && out == null; sg += 2) {
                double qx = px + sg * k * 8 * ux, qz = pz + sg * k * 8 * uz; Column c = Column.of(w, f, (int) Math.floor(qx), (int) Math.floor(qz));
                if (!c.voidCol && c.surfaceY < Terrain.SEA) out = new Joint(qx, qz, -sg * ux, -sg * uz, 3 + 7 * MoonTerrain.u(h, 4), 4 + 18 * MoonTerrain.u(h, 5)); } }
        joints.put(key, out == null ? NONE : out); return out; }
    /** The open span {lowest y, highest y} of the sea cave in column (f, x, z), or null: in a joint, from its mouth at the water up to SEA_L
     *  inland, from 4 m under the sea to the mouth height (lowering inland), 3 m of rock kept over it. */
    public int[] seaSpan(int f, int x, int z, int surfaceY) {
        if (!karstOn || surfaceY < Terrain.SEA - 3) return null; boolean low = surfaceY < Terrain.SEA + 6; int cx = Math.floorDiv(x, (int) SEA_SPACING), cz = Math.floorDiv(z, (int) SEA_SPACING); double best = -1;
        for (int a = -2; a <= 2; a++) for (int b = -2; b <= 2; b++) { Joint jt = joint(f, cx + a, cz + b); if (jt == null) continue;
            double rx = x + 0.5 - jt.px(), rz = z + 0.5 - jt.pz(), along = rx * jt.ux() + rz * jt.uz(), d = Math.abs(rx * jt.uz() - rz * jt.ux());
            if (along < -2 || along > (low ? 16 : SEA_L) || d >= jt.half()) continue;   // low ground (the cliff foot) only in the mouth: opened to the water
            double k = 1 - Math.max(0, along) / SEA_L, top = Terrain.SEA + jt.mouth() * k * Math.sqrt(1 - (d / jt.half()) * (d / jt.half())); best = Math.max(best, top); }
        if (best < 0) return null; if (low) return new int[]{Terrain.SEA - 4, surfaceY}; int hi = (int) Math.min(Math.floor(best), surfaceY - 3), lo = Terrain.SEA - 4; return hi < lo + 1 ? null : new int[]{lo, hi}; }
    public static final double SEA_P = 0.33;
    /** Speleothem draw for an open karst block (0 none, 1 hanging tip, 2 rising tip), from its position (stable). */
    public static int drip(int x, int y, int z, boolean solidAbove, boolean solidBelow) {
        long h = (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L); h ^= h >>> 31; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 29; double u = (h >>> 11) * 0x1.0p-53;
        if (solidAbove && u < DRIP_CEIL) return 1; if (solidBelow && u > 1 - DRIP_FLOOR) return 2; return 0; }
}
