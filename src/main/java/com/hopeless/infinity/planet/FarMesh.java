package com.hopeless.infinity.planet;

/**
 * 0.9.0 THE PLANET AT DISTANCE, stage 3 part 1 (owner, 2026-11-02: "you will need to work out how to render everything at distance without
 * the actual blocks running"): the home planet's ground beyond the loaded chunks, out to the horizon, as a light mesh made from the same
 * Terrain the chunks are made from. No blocks, no chunks: heights are read straight from Terrain.heightM.
 *
 * Nested square rings (a clipmap; Losasso & Hoppe 2004): LEVELS rings of G x G cells round the camera, each ring's cells twice the size of
 * the one inside it (CELL0 m at the middle), so the detail falls off with distance as the screen size of a cell does. A ring leaves out the
 * cells the finer ring covers, minus one coarse cell of overlap (it hides the cracks where the two meet). Laid out along the block world's
 * x and z at the camera and bent onto the sphere: each vertex is the true point of the planet (radius R + h), relative to the camera's
 * point, in the camera's frame (east = block x, up, south = block z), so the ground drops away over the horizon as it really does
 * (d^2 / 2R). Heights go through Terrain.toYd like the chunks (the same block y where they meet). The sea is drawn at sea level.
 * Colours: Minecraft's own biome grass colours and the top block's colour (GAME look until textures). Pure Java (tools/FarView renders it).
 */
public final class FarMesh {
    public static final int G = 64, MIN_LEVELS = 9, MAX_LEVELS = 13; public static final double CELL0 = 16;   // 16 m cells at the middle; at least 9 rings (to 131 km)
    /** 0.9.1: rings enough to reach 1.5 x the horizon seen from the camera (sqrt(2 R h): 268 km from the top of the world, y 5631), at least
     *  MIN_LEVELS (mountains stand above the sea's horizon), at most MAX_LEVELS (2,100 km). */
    public static int levels(double R, double altM) { double need = 1.5 * Math.sqrt(2 * R * Math.max(0, altM)) ; int lv = MIN_LEVELS; while (lv < MAX_LEVELS && G / 2.0 * CELL0 * (1 << (lv - 1)) < need) lv++; return lv; }
    public final int verts; public final float[] pos, nrm, col; public final int[] idx; public final int tris; public final long ms; public final double reachKm; public double[] blk; public int face, body;   // face: the face it was built on (0.12.0); blk: each vertex's block-world x, z (tests)

    FarMesh(float[] p, float[] n, float[] c, int[] i, int ti, long ms, double reach) { pos = p; nrm = n; col = c; idx = i; tris = ti; verts = p.length / 3; this.ms = ms; reachKm = reach; }

    /** The mesh round a camera at face f, block (x, z), height camY (block y), from Terrain of w. Vertex positions relative to the camera (blocks). */
    public static FarMesh build(PlanetWorld w, int f, double x, double z, double camY) {
        long t0 = System.nanoTime(); CubeSphere cs = w.cube; double R = cs.R; final int LEVELS = levels(R, camY - Terrain.SEA);
        double[] s0 = CubeSphere.sphere(CubeSphere.cube(f, cs.u(f, x), cs.v(z))), sx = CubeSphere.sphere(CubeSphere.cube(f, cs.u(f, x + 1), cs.v(z))), sz = CubeSphere.sphere(CubeSphere.cube(f, cs.u(f, x), cs.v(z + 1)));
        double[] up = s0, ex = norm(sub(sx, s0)); ex = norm(sub(ex, mul(up, dot(ex, up)))); double[] ez = cross(ex, up);   // ez = ex x up: block +z (south), right-handed with x east, y up
        if (dot(ez, sub(sz, s0)) < 0) ez = mul(ez, -1);
        int N = G + 1, perLevel = N * N; double[] bk = new double[LEVELS * perLevel * 2]; float[] p = new float[LEVELS * perLevel * 3], n = new float[LEVELS * perLevel * 3], c = new float[LEVELS * perLevel * 3];
        double camR = R + metresAt(camY);
        for (int lv = 0; lv < LEVELS; lv++) { double cell = CELL0 * (1 << lv), half = G / 2.0 * cell;
            // snap each ring to its own cell grid (so it does not swim as the camera moves)
            double ox = Math.floor(x / cell) * cell - x, oz = Math.floor(z / cell) * cell - z;
            for (int j = 0; j < N; j++) for (int i = 0; i < N; i++) { double a = ox - half + i * cell, b = oz - half + j * cell;
                // a tangent-plane step (a, b) metres from the camera, onto the sphere along the great circle (true ground distance)
                // the block world's own point (x + a, z + b) on this face, so the mesh meets the chunks exactly; past the face's edge a great-circle
                // step of the same ground distance from the camera (the neighbour face's ground: the sphere does not end)
                double[] dir = cs.spherePoint(f, x + a, z + b);
                if (dir == null) { double d = Math.hypot(a, b), ang = d / R; dir = d < 1e-9 ? up : add(mul(up, Math.cos(ang)), mul(norm(add(mul(ex, a), mul(ez, b))), Math.sin(ang))); }
                double[] hw = w.terrain.heightAndWater(dir, R); double h = hw[0]; boolean wet = !Double.isNaN(hw[1]) && hw[1] > h; bk[(lv * perLevel + i + N * j) * 2] = x + a; bk[(lv * perLevel + i + N * j) * 2 + 1] = z + b;
                double yb = h < 0 && w.surface == PlanetWorld.Surface.EARTH ? Terrain.SEA + 0.888 : wet ? Terrain.toY(hw[1]) + 0.888 : (w.surface != PlanetWorld.Surface.EARTH ? MoonTerrain.yd(h) : Terrain.toYd(h)) + 1;   // block y of the ground (the chunks' rule) or the water surface
                double[] rel = sub(mul(dir, R + (yb - Terrain.SEA)), mul(up, camR));   // a block is a metre; sea level is radius R
                int k = (lv * perLevel + i + N * j) * 3; p[k] = (float) a; p[k + 1] = (float) dot(rel, up); p[k + 2] = (float) b;   // sideways in blocks (the chunks' own grid: a block is not exactly a metre on a cube face), up the true drop of the sphere
                float[] cc = colour(w, dir, hw); c[k] = cc[0]; c[k + 1] = cc[1]; c[k + 2] = cc[2]; }
            for (int j = 0; j < N; j++) for (int i = 0; i < N; i++) { int k = (lv * perLevel + i + N * j) * 3;
                int il = Math.max(0, i - 1), ir = Math.min(G, i + 1), jl = Math.max(0, j - 1), jr = Math.min(G, j + 1);
                int a1 = (lv * perLevel + ir + N * j) * 3, a0 = (lv * perLevel + il + N * j) * 3, b1 = (lv * perLevel + i + N * jr) * 3, b0 = (lv * perLevel + i + N * jl) * 3;
                double[] tx = {p[a1] - p[a0], p[a1 + 1] - p[a0 + 1], p[a1 + 2] - p[a0 + 2]}, tz = {p[b1] - p[b0], p[b1 + 1] - p[b0 + 1], p[b1 + 2] - p[b0 + 2]}; double[] nn = norm(cross(tz, tx));
                n[k] = (float) nn[0]; n[k + 1] = (float) nn[1]; n[k + 2] = (float) nn[2]; } }
        // triangles: each ring without the cells the finer ring covers (minus one coarse cell of overlap)
        int[] ix = new int[LEVELS * G * G * 6]; int t = 0;
        for (int lv = 0; lv < LEVELS; lv++) { double cell = CELL0 * (1 << lv), ox = Math.floor(x / cell) * cell - x, oz = Math.floor(z / cell) * cell - z, inner = lv == 0 ? -1 : G / 2.0 * CELL0 * (1 << (lv - 1)) - cell;
            for (int j = 0; j < G; j++) for (int i = 0; i < G; i++) { double a0 = ox - G / 2.0 * cell + i * cell, b0 = oz - G / 2.0 * cell + j * cell, a1 = a0 + cell, b1 = b0 + cell;
                if (inner > 0 && a0 >= -inner && a1 <= inner && b0 >= -inner && b1 <= inner) continue;
                int v00 = lv * perLevel + i + N * j, v10 = v00 + 1, v01 = v00 + N, v11 = v01 + 1;
                ix[t++] = v00; ix[t++] = v01; ix[t++] = v10; ix[t++] = v10; ix[t++] = v01; ix[t++] = v11; } }
        int[] idx = java.util.Arrays.copyOf(ix, t);
        FarMesh fm = new FarMesh(p, n, c, idx, t / 3, (System.nanoTime() - t0) / 1_000_000, G / 2.0 * CELL0 * (1 << (LEVELS - 1)) / 1000); fm.blk = bk; return fm;
    }
    static double metresAt(double y) { return y - Terrain.SEA; }
    /** 0.21.0 the colour with rivers, lakes and salt flats ({height, water, kind} from Terrain.heightAndWater). */
    public static float[] colour(PlanetWorld w, double[] s, double[] hw) {
        if (!Double.isNaN(hw[1]) && hw[1] > hw[0]) { double k = Math.min(1, (hw[1] - hw[0]) / 20); return new float[]{(float) (0.14 - 0.06 * k), (float) (0.36 - 0.14 * k), (float) (0.60 - 0.12 * k)}; }   // fresh water (a little lighter than the sea)
        if (hw[2] == 3) return new float[]{0.87f, 0.85f, 0.80f};   // a salt flat (calcite)
        return colour(w, s, hw[0]); }
    /** Minecraft's biome grass colours (its colormap at the biome's temperature and downfall) and the top blocks' map colours; the sea by depth. */
    public static float[] colour(PlanetWorld w, double[] s, double h) {
        if (w.terrain instanceof VenusTerrain vt) { if (vt.lava(h)) return new float[]{0.85f, 0.33f, 0.05f}; return vt.tessera(s) ? new float[]{0.31f, 0.31f, 0.33f} : new float[]{0.34f, 0.33f, 0.33f}; }   // 0.18.0 lava, deepslate tesserae, basalt plains (map colours)
        if (w.terrain instanceof MoonTerrain mt) { boolean m = mt.mare(s); return m ? new float[]{0.29f, 0.29f, 0.31f} : new float[]{0.53f, 0.53f, 0.53f}; }   // 0.15.0 the moon: mare (smooth basalt) and highland regolith (andesite), the blocks' own greys (ratio 0.55; the Moon's mare/highland albedo 0.07/0.12 = 0.6)
        if (h < 0) { double k = Math.min(1, -h / 60); return new float[]{(float) (0.12 - 0.08 * k), (float) (0.32 - 0.18 * k), (float) (0.55 - 0.18 * k)}; }
        if (Terrain.volcanicOn && w.surface == PlanetWorld.Surface.EARTH && w.terrain.volcanic(s) > 0) { double[] k = {0, 0}; w.terrain.volcano(s, w.cube.R, k);   // 0.20.0 the generator's own choice of top block (Column)
            if (k[0] == 1) return new float[]{0.17f, 0.15f, 0.16f}; if (k[0] == 3) return Terrain.toY(k[1]) > Terrain.toY(h) ? new float[]{0.85f, 0.33f, 0.05f} : new float[]{0.42f, 0.18f, 0.08f}; }
        Terrain.Bio bio = w.terrain.biome(s, h); int g = grassRgb(bio);
        if (g >= 0) return new float[]{(float) (((g >> 16) & 255) / 255.0 * GRASS_TEX), (float) (((g >> 8) & 255) / 255.0 * GRASS_TEX), (float) ((g & 255) / 255.0 * GRASS_TEX)};   // 0.29.3 a grass block shows the tint times its grey texture
        int rgb = switch (bio) {
            case VOLCANIC -> 0x4C4C50; case CHERRY_GROVE -> 0xE8A0C0;
            case BEACH, DESERT -> 0xDBD3A0; case SNOWY_BEACH, SNOWY_PLAINS, SNOWY_SLOPES, ICE_SPIKES, FROZEN_PEAKS, SNOWY_TAIGA -> 0xF0F4F8; case STONY_SHORE, STONY_PEAKS, JAGGED_PEAKS -> 0x8A8A8A;
            case BADLANDS -> 0xC0662E; default -> 0x91BD59; };
        return new float[]{((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f};
    }
    /** 0.29.3 the mean of the game's grass_block_top texture (grey, 16 x 16; measured from the 1.20.1 client jar: 0.578 in each channel). The
     *  game multiplies the biome tint by the texture, so the far ground took the bare tint and stood 1.7 times brighter than the blocks next to it
     *  (seen in tools/WorldView, 2026-11-07). STATED (measured). */
    public static final double GRASS_TEX = 0.578;
    /** The biome's grass tint (Minecraft's colormap at its temperature and downfall), or -1 where the ground is not grass. The game's grass
     *  blocks take this tint; tools/WorldView uses it per column. */
    public static int grassRgb(Terrain.Bio b) { return switch (b) {
            case MANGROVE_SWAMP -> 0x5A6A30; case BAMBOO_JUNGLE -> 0x6AB830; case SAVANNA -> 0xBFB755; case JUNGLE, SPARSE_JUNGLE -> 0x59C93C; case SWAMP -> 0x6A7039; case DARK_FOREST -> 0x507A32; case BIRCH_FOREST -> 0x88BB67;
            case FOREST, FLOWER_FOREST -> 0x79C05A; case TAIGA, OLD_GROWTH_SPRUCE_TAIGA -> 0x86B783; case MEADOW -> 0x83BB6D; case WINDSWEPT_HILLS -> 0x8AB689;
            case VOLCANIC, CHERRY_GROVE, BEACH, DESERT, SNOWY_BEACH, SNOWY_PLAINS, SNOWY_SLOPES, ICE_SPIKES, FROZEN_PEAKS, SNOWY_TAIGA, STONY_SHORE, STONY_PEAKS, JAGGED_PEAKS, BADLANDS -> -1;
            default -> 0x91BD59; }; }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double[] norm(double[] a) { double l = Math.sqrt(dot(a, a)); return l < 1e-30 ? new double[]{0, 1, 0} : new double[]{a[0] / l, a[1] / l, a[2] / l}; }
    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    static double[] add(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
    static double[] mul(double[] a, double k) { return new double[]{a[0] * k, a[1] * k, a[2] * k}; }
}
