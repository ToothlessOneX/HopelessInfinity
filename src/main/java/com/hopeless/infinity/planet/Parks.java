package com.hopeless.infinity.planet;

/** 0.28.0 PARKS AND PLAZAS of a megacity (owner, 2026-11-04: "Supertowers get large plazas attached, some containing large parks; fancy
 *  features, since rich people build them"), drawn from the street kit ported from Hopeless Deep (street_tree, street_lamp, bench,
 *  litter_bin, planter, park_path, plaza_paver, playground pieces), not new art.
 *  A PARK superblock (and the axis, a park one superblock wide: STATED, Futian's central axis): a promenade round the edge with a row of
 *  street trees, two paths crossing at the centre and a ring path, a fountain at the centre, trees on a jittered grid in the lawns, lamps
 *  and benches along the paths, a playground in one of three. A megatall tower's own park (a superblock next to it, owned) has a pond.
 *  Sizes CHOSEN unless marked: paths 5 m (ADA 403.5.1 asks 36 in at least; two people pass at 60 in, 1.5 m: wider for a city park),
 *  promenade 5 m, ring at 55 m, lamps every 24 m (IES RP-8 pedestrian ways: spacing about 4-6 times a 4-5 m mounting height), benches
 *  every 16 m, trees 9 m apart (a mature urban canopy tree's crown: 8-12 m).
 *  A MEGATALL'S PLAZA (a tower of 600 m or more: STATED, CTBUH "megatall"): its lot round the podium paved in two-tone pavers, planters
 *  with trees in rows, two reflecting pools flanking the front door, three flagpoles before it.
 *  Coordinates: lx, lz from the block's (or the tower's) centre; y 0 is the surface layer (it replaces the graded top), 1 and up stand on it. */
public final class Parks {
    static final String NS = "hopelessinfinity:";
    public static final int PROMENADE = 5, PATH_HALF = 2, RING_R = 55, RING_HALF = 2, FOUNTAIN_R = 8, POND_R = 22, TREE_GRID = 9, LAMP_EVERY = 24, BENCH_EVERY = 16, MEGATALL_M = 600;
    public static final java.util.concurrent.atomic.LongAdder PARK_BLOCKS = new java.util.concurrent.atomic.LongAdder(), PLAZA_BLOCKS = new java.util.concurrent.atomic.LongAdder();

    /** A park superblock: half its half width (blocks), seed for the trees and the playground, owned: a megatall's park (a pond for the fountain). */
    public static String park(int lx, int y, int lz, int half, long seed, boolean owned) {
        int ax = Math.abs(lx), az = Math.abs(lz), edge = Math.max(ax, az); if (edge > half) return null; double r = Math.hypot(lx, lz);
        boolean prom = edge > half - PROMENADE, cross = ax <= PATH_HALF || az <= PATH_HALF, ring = Math.abs(r - RING_R) <= RING_HALF, poolR = r <= (owned ? POND_R : FOUNTAIN_R);
        boolean playground = !owned && Math.floorMod(seed, 3) == 0; int qx = Integer.signum(lx), qz = Integer.signum(lz); boolean inPlay = playground && qx > 0 && qz > 0 && lx >= 18 && lx <= 34 && lz >= 18 && lz <= 34;
        if (y == 0) {
            if (poolR) return r > (owned ? POND_R : FOUNTAIN_R) - 1 ? "smooth_stone" : "water";
            if (prom) return Math.floorMod(lx + lz, 6) < 3 ? NS + "plaza_paver" : NS + "plaza_paver_dark";
            if (cross || ring) return NS + "park_path";
            if (inPlay) return NS + "safety_surface";
            return "grass_block"; }
        if (y < 0 || y > 4) return null;
        // the fountain: a rim, a column, its jet as a water source on top (owned: the pond, lily pads on it)
        if (poolR) { double R = owned ? POND_R : FOUNTAIN_R; if (r > R - 1) return y == 1 ? "smooth_stone_slab" : null;
            if (!owned && r < 1.5) return y <= 3 ? "quartz_pillar" : "water";
            if (owned && y == 1 && Math.floorMod(lx * 7 + lz * 13 + (int) seed, 23) == 0 && r < R - 2) return "lily_pad"; return null; }
        if (y > 1) return null;
        // the promenade's row of trees, on its inner edge every 10 m
        if (prom) { if (edge == half - PROMENADE + 1 && Math.floorMod(ax == edge ? lz : lx, 10) == 0) return NS + "street_tree[facing=north]"; return null; }
        // lamps and benches along the cross paths (both sides), lamps along the ring
        if (cross || ring) return null;
        if (ax == PATH_HALF + 1 || az == PATH_HALF + 1) { boolean alongZ = ax == PATH_HALF + 1; int t = alongZ ? lz : lx; String face = alongZ ? (lx > 0 ? "west" : "east") : (lz > 0 ? "north" : "south");
            if (Math.abs(t) <= FOUNTAIN_R + 2) return null;
            if (Math.floorMod(t, LAMP_EVERY) == 0) return NS + "street_lamp[facing=" + face + "]";
            if (Math.floorMod(t - LAMP_EVERY / 2, BENCH_EVERY) == 0) return NS + "bench[facing=" + face + "]";
            if (Math.floorMod(t - LAMP_EVERY / 2, BENCH_EVERY) == 1) return NS + "litter_bin[facing=" + face + "]"; return null; }
        if (Math.abs(r - (RING_R + RING_HALF + 1)) < 0.5 && Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(lz, lx))), 15) == 0) return NS + "street_lamp[facing=north]";
        // the playground
        if (inPlay) { int px = lx - 18, pz = lz - 18; if (px == 3 && pz == 3) return NS + "slide[facing=north]"; if (px == 10 && pz == 4) return NS + "swings[facing=north]"; if (px == 6 && pz == 11) return NS + "climber[facing=north]";
            if ((px == 0 || pz == 0) && Math.floorMod(px + pz, 6) == 3) return NS + "bench[facing=" + (px == 0 ? "east" : "south") + "]"; return null; }
        // lawn trees on a jittered grid, clear of the paths and the ring by 3 m
        if (Math.abs(r - RING_R) <= RING_HALF + 3 || ax <= PATH_HALF + 3 || az <= PATH_HALF + 3 || edge > half - PROMENADE - 3 || r <= (owned ? POND_R : FOUNTAIN_R) + 3) return null;
        int gx = Math.floorDiv(lx, TREE_GRID), gz = Math.floorDiv(lz, TREE_GRID); long h = MoonTerrain.hash(seed, 31, gx, gz, 0); int ox = (int) Math.floorMod(h, TREE_GRID - 2) + 1, oz = (int) Math.floorMod(h >> 16, TREE_GRID - 2) + 1;
        if (Math.floorMod(lx, TREE_GRID) == ox && Math.floorMod(lz, TREE_GRID) == oz && Math.floorMod(h >> 32, 5) != 0) return NS + "street_tree[facing=north]";   // 4 in 5 grid cells hold a tree (CHOSEN: open lawn between)
        return null; }

    /** A megatall's plaza round its podium: lx, lz from the tower's centre, ph its podium half width, lh its lot half width. The front is -z. */
    public static String plaza(int lx, int y, int lz, int ph, int lh) {
        int ax = Math.abs(lx), az = Math.abs(lz); if (Math.max(ax, az) > lh || (ax <= ph && az <= ph)) return null;
        boolean front = lz < -ph, poolL = front && lz <= -ph - 6 && lz >= -ph - 16 && ax >= 10 && ax <= 30;   // two reflecting pools, 20 x 10 m, flanking the front door
        if (y == 0) { if (poolL) return ax == 10 || ax == 30 || lz == -ph - 6 || lz == -ph - 16 ? "polished_andesite" : "water"; return Math.floorMod(lx, 12) < 6 == Math.floorMod(lz, 12) < 6 ? NS + "plaza_paver" : NS + "plaza_paver_dark"; }
        if (poolL || y < 1) return null;
        // three flagpoles on the front axis, 12 m tall, banners on top
        if (front && lz == -ph - 20 && (lx == -6 || lx == 0 || lx == 6)) { if (y <= 12) return "iron_bars"; if (y == 13) return lx == 0 ? "blue_banner[rotation=0]" : "white_banner[rotation=0]"; return null; }
        if (y > 1) return null;
        // planters with trees in rows every 12 m, 6 m clear of the podium and the lot edge, the front axis kept open
        int d = Math.max(ax, az); if (d < ph + 6 || d > lh - 4 || (front && ax <= 8)) return null;
        if (Math.floorMod(lx, 12) == 6 && Math.floorMod(lz, 12) == 6) return NS + "street_tree[facing=north]";
        if (Math.floorMod(lx, 12) == 6 && Math.floorMod(lz, 12) == 0 && !front) return NS + "bench[facing=north]";
        return null; }
    private Parks() {}
}
