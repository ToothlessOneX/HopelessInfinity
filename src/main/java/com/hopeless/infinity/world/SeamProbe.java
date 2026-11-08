package com.hopeless.infinity.world;

import com.hopeless.infinity.HopelessInfinity;
import com.hopeless.infinity.planet.Column;
import com.hopeless.infinity.planet.PlanetWorld;
import com.hopeless.infinity.planet.Terrain;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 0.8.1 SEAM PROBE (owner, 2026-11-02: visible seams, flat walls, inland far from the spawn; the terrain plan itself has none: tools/SeamWalk
 * and tools/SeamScan found 0 over 600,000 steps and 40 inland tiles). Every chunk the server loads or generates on a planet face is checked
 * against the plan: the top of the ground in the chunk (OCEAN_FLOOR heightmap) against Column.of's surfaceY, every border column and
 * a diagonal through the middle (64 columns). A slope piece may stand one block over the plan; more than that is a SEAM. Each seam is
 * logged with its position, the plan's and the world's heights and the chunk's status, so the next play shows exactly where and how big.
 * Probes: chunks checked, columns, seams, chunks with seams, first 20 positions.
 */
public final class SeamProbe {
    private SeamProbe() {}
    public static volatile long chunks, columns, seams, seamChunks; static final java.util.ArrayDeque<String> FIRST = new java.util.ArrayDeque<>(); static long lastLog;

    public static void check(ServerLevel l, ChunkAccess c, int face, PlanetWorld w) {
        int bx = c.getPos().getMinBlockX(), bz = c.getPos().getMinBlockZ(); int n = 0;
        for (int k = 0; k < 64; k++) { int lx, lz;
            if (k < 16) { lx = k; lz = 0; } else if (k < 32) { lx = k - 16; lz = 15; } else if (k < 48) { lx = 0; lz = k - 32; } else { lx = 15; lz = k - 48; }
            if (k >= 60) { lx = (k - 60) * 5; lz = (k - 60) * 5; }
            Column col = Column.of(w, face, bx + lx, bz + lz); if (col.voidCol) continue;
            if (built(w, face, bx + lx, bz + lz)) { skippedBuilt++; continue; }   // 0.29.7 a city, a town plat, a yard: the structures stage sets the top there (towers, pavers, pools), not the terrain
            int world = c.getHeight(Heightmap.Types.OCEAN_FLOOR, lx, lz) - 1, plan = col.surfaceY; columns++;   // the heightmap is the first free y: the top block is one under it
            if (world > plan + 1) { var st = c.getBlockState(new net.minecraft.core.BlockPos(bx + lx, world, bz + lz)); if (st.is(net.minecraft.tags.BlockTags.LOGS) || st.is(net.minecraft.tags.BlockTags.LEAVES) || !st.getFluidState().isEmpty() || st.canBeReplaced()) continue; }   // trees and plants stand over the ground
            if (world < plan && world >= plan - 2) { var st = c.getBlockState(new net.minecraft.core.BlockPos(bx + lx, plan, bz + lz)); if (!st.getFluidState().isEmpty() || !st.blocksMotion()) continue; }   // 0.29.7 water or a thin block (paver, carpet, snow) at the plan's top: the heightmap passes over it
            if (world < plan || world > plan + 1) { n++; seams++;
                if (FIRST.size() < 20) synchronized (FIRST) { FIRST.add(String.format("%d %d: world %d plan %d (%s, %s)", bx + lx, bz + lz, world, plan, c.getStatus(), col.biome.key)); } } }
        chunks++; if (n > 0) seamChunks++;
        long now = System.currentTimeMillis(); if (n > 0 && now - lastLog > 10000) { lastLog = now; HopelessInfinity.LOG.warn("[Hopeless Infinity] seam probe: {}", line()); }
    }
    public static volatile long skippedBuilt;
    /** 0.29.7 whether the structures stage owns this column's top (measured: 176,836 of 240,448 columns round the megacity spawn were logged as
     *  seams, every one a tower, a plaza paver or a fountain, none in the terrain). */
    static boolean built(PlanetWorld w, int face, int x, int z) { double[] s = w.cube.spherePoint(face, x + 0.5, z + 0.5); if (s == null) return false;
        com.hopeless.infinity.planet.Metro mt = com.hopeless.infinity.planet.Metro.on ? w.metroIfMade() : null; if (mt != null && mt.inCity(face, x, z, s)) return true;
        com.hopeless.infinity.planet.Towns tw = com.hopeless.infinity.planet.Towns.on ? w.towns() : null; return tw != null && tw.at(face, x + 0.5, z + 0.5, s) != null; }
    public static String line() { synchronized (FIRST) { return String.format("seam probe: chunks %d, columns %d (%d in cities and towns left out), seams %d in %d chunks%s", chunks, columns, skippedBuilt, seams, seamChunks, FIRST.isEmpty() ? "" : " | first: " + String.join("; ", FIRST)); } }
}
