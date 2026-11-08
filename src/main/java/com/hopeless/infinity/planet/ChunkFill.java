package com.hopeless.infinity.planet;

import com.hopeless.infinity.world.Probe;

/** 0.29.2 THE CHUNK FILL WITHOUT THE GAME: what world/PlanetGenerator puts in a chunk, as plain Java, so the generator and the offline
 *  renderer (tools/WorldView) run the same code (owner, 2026-11-07: "combine all of your minecraft renderers into one tool so ... you and me
 *  can both get a full picture of what things actually look like in game"). The generator keeps only the game's own block setting (whole
 *  sections, heightmaps, block entities); every choice of WHICH block goes WHERE is made here:
 *   - the columns: the 18 x 18 ring of Column.of and the sea-floor step (Seabed);
 *   - each material's block (NAME, the generator's former state() table);
 *   - the caves (karst, sea caves, lava tubes) as codes (CAVE_AIR, CAVE_WATER, CAVE_HANG, CAVE_RISE);
 *   - the terrain slopes (Slopes.choose; the slope block of the column's top material);
 *   - the structures, in the generator's order: town buildings, the cities, the quarry, the yards, the billboards (block strings). */
public final class ChunkFill {
    private ChunkFill() {}
    /** The block of each material (Column.Mat order); the generator parses these once. */
    public static String name(Column.Mat m) { return switch (m) {
        case AIR -> "air"; case BEDROCK -> "bedrock"; case DEEPSLATE -> "deepslate"; case STONE -> "stone"; case DIRT -> "dirt"; case GRASS -> "grass_block"; case PODZOL -> "podzol"; case SAND -> "sand";
        case SANDSTONE -> "sandstone"; case RED_SAND -> "red_sand"; case TERRACOTTA -> "terracotta"; case GRAVEL -> "gravel"; case CLAY -> "clay"; case MUD -> "mud"; case SNOW_BLOCK -> "snow_block"; case SNOW_LAYER -> "snow";
        case ICE -> "ice"; case PACKED_ICE -> "packed_ice"; case WATER -> "water"; case CALCITE -> "calcite"; case REGOLITH -> "andesite"; case MARE_REGOLITH -> "smooth_basalt"; case ANORTHOSITE -> "diorite"; case BASALT -> "smooth_basalt";
        case BASALT_ROCK -> "basalt"; case LAVA -> "lava"; case MAGMA_CRUST -> "magma_block"; case BLACKSTONE -> "blackstone";
        case ROAD_CONCRETE, DECK, LINING -> "light_gray_concrete"; case ASPHALT -> "gray_concrete"; case LINE_WHITE -> "white_concrete"; case LINE_YELLOW -> "yellow_concrete"; case DIRT_PATH -> "dirt_path";
        case RAIL -> "stone_brick_wall"; case TIE -> "dark_oak_planks"; case RAIL_TOP -> "heavy_weighted_pressure_plate"; };   // 0.29.0 creosoted ties and steel rails; 0.23.0 roads: concrete, asphalt (grey concrete), MUTCD paint
    }
    /** The columns the generator reads: ring (18 x 18, the chunk and one column round it) and cols (16 x 16, with the sea-floor step);
     *  kinds the seabed kind of each. Returns {any land or sea column (1/0), allSea (1/0), plain (lowest plain rock), lowestTop}. */
    public static int[] columns(PlanetWorld w, int face, int x0, int z0, Column[] ring, Column[] cols, byte[] kinds) {
        for (int x = -1; x <= 16; x++) for (int z = -1; z <= 16; z++) ring[(x + 1) * 18 + z + 1] = Column.of(w, face, x0 + x, z0 + z);
        int plain = Integer.MAX_VALUE, lowestTop = Integer.MAX_VALUE; boolean any = false, allSea = true;
        for (int i = 0; i < 256; i++) { int gx = (i >> 4) + 1, gz = (i & 15) + 1; Column c = ring[gx * 18 + gz];
            if (!c.voidCol && !c.dry() && c.heightM <= Seabed.BEACH_TOP + 1) { long ts = System.nanoTime();   // the sea floor and the shore strip (Seabed)
                c = c.seabed(Seabed.cosSlope(ring[(gx - 1) * 18 + gz].heightM, ring[(gx + 1) * 18 + gz].heightM, ring[gx * 18 + gz - 1].heightM, ring[gx * 18 + gz + 1].heightM));
                Probe.seabedNanos += System.nanoTime() - ts; if (c.seabed != Seabed.Kind.NONE) Probe.seabed[c.seabed.ordinal()]++; }
            cols[i] = c; kinds[i] = (byte) c.seabed.ordinal();
            if (c.voidCol) { allSea = false; continue; } any = true; plain = Math.min(plain, c.plainBelow()); lowestTop = Math.min(lowestTop, c.surfaceY); if (c.surfaceY >= Terrain.SEA - 1 || c.ice || c.dry()) allSea = false; }
        return new int[]{any ? 1 : 0, allSea ? 1 : 0, plain, lowestTop}; }

    public static final int CAVE_AIR = 0, CAVE_WATER = 1, CAVE_HANG = 2, CAVE_RISE = 3;
    public interface CaveSink { void set(int lx, int y, int lz, int code); }
    /** 0.19.0 the caves (planet/Caves): karst passages and halls in carbonate country under wet land (flooded below the water table,
     *  dripstone in the dry ones), sea caves along the cliffs' joints, lava tubes under basalt plains. */
    public static void caves(PlanetWorld w, int face, int x0, int z0, Column[] cols, int minY, CaveSink out) {
        Caves cv = w.caves(); if (!cv.karstOn && !cv.tubesOn) return;
        for (int i = 0; i < 256; i++) { Column c = cols[i]; if (c.voidCol) continue; int lx = i >> 4, lz = i & 15, x = x0 + lx, z = z0 + lz;
            if (cv.karstOn) { int[] sp = cv.seaSpan(face, x, z, c.surfaceY);   // 0.20.0 sea caves along joints in the cliffs
                if (sp != null) { Probe.seaCaveCols++; for (int y = sp[0]; y <= sp[1]; y++) { out.set(lx, y, lz, y <= Terrain.SEA ? CAVE_WATER : CAVE_AIR); Probe.caveBlocks++; Probe.seaCave++; if (y <= Terrain.SEA) Probe.caveWater++; } } }
            if (cv.karstOn && c.surfaceY > Terrain.SEA) { double[] s = CubeSphere.sphere(CubeSphere.cube(face, w.cube.u(face, x + 0.5), w.cube.v(z + 0.5)));
                if (cv.karst(s, w.terrain.wetness(s))) { Probe.karstCols++; int y0 = Math.max(minY + 2, c.surfaceY - (int) Caves.BAND_DEPTH), n = c.surfaceY - y0 + 2; boolean[] open = new boolean[n + 1];
                    for (int k = 0; k <= n; k++) { int y = y0 - 1 + k; open[k] = y <= c.surfaceY && cv.karstVoid(face, x, y, z, c.surfaceY); }
                    for (int k = 1; k < n; k++) { if (!open[k]) continue; int y = y0 - 1 + k; boolean wet = y <= Terrain.SEA;
                        if (wet) { out.set(lx, y, lz, CAVE_WATER); Probe.caveWater++; Probe.caveBlocks++; continue; }
                        int d = Caves.drip(x, y, z, !open[k + 1] && y + 1 <= c.surfaceY, !open[k - 1]);
                        out.set(lx, y, lz, d == 1 ? CAVE_HANG : d == 2 ? CAVE_RISE : CAVE_AIR); if (d != 0) Probe.drips++; Probe.caveBlocks++; } } }
            if (cv.tubesOn) { int[] t = cv.tube(face, x, z, c.surfaceY, c.top == Column.Mat.MARE_REGOLITH || c.top == Column.Mat.BASALT_ROCK);
                if (t != null) { Probe.tubeCols++; for (int y = Math.max(minY + 2, t[0]); y <= Math.min(c.surfaceY, t[1]); y++) { out.set(lx, y, lz, CAVE_AIR); Probe.caveBlocks++; } } } } }

    public interface SlopeSink { void set(int lx, int y, int lz, String slopeId, int corners, boolean replacedTop); }
    /** The terrain slopes (Hopeless Deep's slope blocks): on dry ground a piece whose corners follow the true ground height of the 3 x 3
     *  columns round it (to a quarter block); left out where the ground is flat or too steep. slopeId: "slope_<material>" (Slopes.MAT), or
     *  null when the top material has no slope (counted). replacedTop: the piece took the top block's place (the block under it then shows
     *  the top material, and a snow layer over it is cleared). */
    public static void slopes(Column[] cols, Column[] ring, SlopeSink out) {
        double[] top = new double[18 * 18];
        boolean[] wet = new boolean[18 * 18];
        for (int x = -1; x <= 16; x++) for (int z = -1; z <= 16; z++) { Column c = ring[(x + 1) * 18 + z + 1]; wet[(x + 1) * 18 + z + 1] = c.waterY > c.surfaceY; top[(x + 1) * 18 + z + 1] = c.voidCol || (c.surfaceY < Terrain.SEA && !c.dry()) || c.waterY > c.surfaceY ? Double.NaN : c.groundTop; }
        double[] s9 = new double[9];
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            Column c = cols[x * 16 + z]; if (c.voidCol || (c.surfaceY < Terrain.SEA && !c.dry())) continue;
            boolean byWater = false; for (int dz = -1; dz <= 1 && !byWater; dz++) for (int dx = -1; dx <= 1; dx++) if (wet[(x + 1 + dx) * 18 + z + 1 + dz]) { byWater = true; break; }
            if (byWater) { Probe.slopeByWater++; continue; }   // 0.29.8 no slope piece in a river's or lake's water or beside it (measured: the pieces took the water block's place in the channels, leaving dry sand beds)
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) s9[(dz + 1) * 3 + dx + 1] = top[(x + 1 + dx) * 18 + z + 1 + dz];
            int[] yc = com.hopeless.infinity.slope.Slopes.choose(c.surfaceY, s9); if (yc == null) continue;
            String sl = com.hopeless.infinity.slope.Slopes.MAT.get(name(c.top)); if (sl == null) { Probe.slopeNoMaterial++; continue; }
            out.set(x, yc[0], z, sl, yc[1], yc[0] == c.surfaceY); Probe.slopes++; } }

    /** The structures over the chunk [x0, x0 + 16) x [z0, z0 + 16), in the generator's order: town buildings (Buildings), the cities
     *  (Metro: towers, gate, parks, plants), the quarry, the billboards. Block strings into out (absolute x, y, z). */
    public static void structures(PlanetWorld w, int face, int x0, int z0, Buildings.Sink town, Metro.Sink other) {
        Towns tw = w.towns(); if (tw == null) return; double[] sc = w.cube.spherePoint(face, x0 + 8, z0 + 8); if (sc == null) return;
        Buildings.chunk(tw, face, x0, z0, x0 + 16, z0 + 16, sc, town);
        Metro mt = w.metro(); if (mt != null) mt.chunk(face, x0, z0, x0 + 16, z0 + 16, sc, other);
        Quarry.chunk(w, face, x0, z0, x0 + 16, z0 + 16, other);
        Yards.chunk(w, face, x0, z0, x0 + 16, z0 + 16, other);   // 0.29.6 the freight yards' light towers and offices
        for (Billboards.Board bd : Billboards.near(w, face, x0, z0, x0 + 16, z0 + 16, sc)) Billboards.draw(bd, x0, z0, x0 + 16, z0 + 16, other); }
}
