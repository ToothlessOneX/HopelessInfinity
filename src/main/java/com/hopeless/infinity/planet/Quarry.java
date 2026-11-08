package com.hopeless.infinity.planet;

/** 0.28.5 THE COBBLE & SONS ORIGIN QUARRY (owner, 2026-11-04: the company "came from humble beginnings"; AdArt's story: "from one quarry
 *  to every world"): the old stone quarry the company began in, near the spawn city, kept as a heritage site: an open pit of three
 *  benches cut into the hill, a stair down its end wall, a pool on its floor, the company's first gate on a plinth at the rim, and the
 *  old office shed with its sign.
 *  THE PIT (CHOSEN where not marked): a rounded rectangle 120 x 80 m at the rim (|u/60|^4 + |v/40|^4 <= 1), benches 10 m high (aggregate
 *  quarries cut benches of about 10-15 m) and 8 m wide; the floor three benches down, a pool on its low middle. The rim is the lowest
 *  ground round the pit, so no wall stands above the land.
 *  THE SITE: on a ring 4.5 km from the spawn city's centre (past its edge and its plant), the driest highest spot of 24 tried, off the
 *  city's built blocks and off the roads. Local coordinates u, v from the pit's centre (the face's x, z). */
public final class Quarry {
    public static volatile boolean on = true;
    public static final int A = 60, B = 40, BENCH_H = 10, BENCH_W = 8, BENCHES = 3, RING_M = 4500, STAIR_OUT = 12, SX0 = -4, SX1 = 3, SZ0 = B + 6, SZ1 = B + 11, FOUND = 4;
    public static final java.util.concurrent.atomic.LongAdder COLS = new java.util.concurrent.atomic.LongAdder(), BLOCKS = new java.util.concurrent.atomic.LongAdder();
    /** {face, x, z (centre, block coordinates), rim y (the pad y of the rim: block over the ground)}, made once a world; null if no spawn city. */
    public record Site(int face, int x, int z, int rimY, String city, int gateY, int shedY) {}   // 0.28.5 gateY, shedY: the pads the gate and the shed stand on (the highest ground under each: measured before, both stood at the rim's y, half buried where the ground rose 4 m)
    static final java.util.Map<PlanetWorld, java.util.Optional<Site>> SITES = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    static volatile Object[] LAST;   // (world, site or null): the columns ask once each, without a lock
    static final ThreadLocal<Boolean> MAKING = ThreadLocal.withInitial(() -> false);
    /** The world's quarry, made once (no site is kept too: measured before, a world with no city plans asked again in every column under
     *  the lock, and PlanetTest ran 600 s instead of 9 s). */
    public static Site site(PlanetWorld w) { Object[] l0 = LAST; if (l0 != null && l0[0] == w && on && Metro.on) return (Site) l0[1];   // the fast path first: a column asks this every time (RoadsTest times columns)
        if (!on || !Metro.on || w.body != 0 || MAKING.get() || (Roads.on && w.roadsIfMade() == null)) return null;   // make() asks the spawn, which asks the columns: no quarry while it is being placed; none before the roads exist (it is placed off them)
        Object[] l = LAST; if (l != null && l[0] == w) return (Site) l[1]; java.util.Optional<Site> o = SITES.get(w); if (o != null) { LAST = new Object[]{w, o.orElse(null)}; return o.orElse(null); }
        synchronized (SITES) { o = SITES.get(w); if (o != null) return o.orElse(null); Site s; MAKING.set(true); try { s = make(w); } finally { MAKING.set(false); } SITES.put(w, java.util.Optional.ofNullable(s)); LAST = new Object[]{w, s}; return s; } }
    static Site make(PlanetWorld w) { Metro m = w.metro(); if (m == null) return null; w.playerSpawn(); Metro.Plan p = null; for (Metro.Plan q : m.plans.values()) if (q.face == 0 && w.spawnKind.contains(q.site.name)) p = q; if (p == null) return null;
        double R = w.cube.R; Site best = null; double bestScore = -1e18;
        for (int k = 0; k < 24; k++) { double th = 2 * Math.PI * k / 24, u = Math.cos(th) * Math.max(RING_M, p.reachM + 800), v = Math.sin(th) * Math.max(RING_M, p.reachM + 800); double[] xz = p.xz(u, v); int cx = (int) Math.floor(xz[0]), cz = (int) Math.floor(xz[1]);
            double[] c = w.cube.spherePoint(p.face, cx + 0.5, cz + 0.5); if (c == null) continue; double lo = 1e18, hi = -1e18; boolean wet = false;
            for (int t = 0; t < 16; t++) { double a = 2 * Math.PI * t / 16; for (double f : new double[]{0, 0.6, 1.1}) { double[] q = w.cube.spherePoint(p.face, cx + 0.5 + Math.cos(a) * A * f, cz + 0.5 + Math.sin(a) * B * f); if (q == null) { wet = true; continue; } double[] hw = w.terrain.heightAndWater(q, R);
                if (hw[0] < 2 || (!Double.isNaN(hw[1]) && hw[1] > hw[0] - 1)) wet = true; if (f > 1) lo = Math.min(lo, hw[0]); hi = Math.max(hi, hw[0]); } }
            if (wet || m.inCity(p.face, cx, cz, c)) continue; Roads rd = w.roadsIfMade(); if (rd != null && !rd.hits(c).isEmpty()) continue;
            double score = lo - 0.2 * (hi - lo); if (score > bestScore) { bestScore = score; best = new Site(p.face, cx, cz, (int) Math.floor(Terrain.toYd(lo)) + 1, p.site.name, 0, 0); } }
        if (best == null) return null; return new Site(best.face, best.x, best.z, best.rimY, best.city, pad(w, best, A + 4, A + 8, -5, 5), pad(w, best, SX0, SX1, SZ0, SZ1)); }
    /** The pad y over the highest ground in a footprint (local x lx0..lx1, z lz0..lz1), never under the rim's. */
    static int pad(PlanetWorld w, Site s, int lx0, int lx1, int lz0, int lz1) { double hi = -1e18; for (int x = lx0; x <= lx1; x++) for (int z = lz0; z <= lz1; z++) { double[] q = w.cube.spherePoint(s.face, s.x + x + 0.5, s.z + z + 0.5); if (q != null) hi = Math.max(hi, w.terrain.heightAndWater(q, w.cube.R)[0]); }
        return Math.max(s.rimY, (int) Math.floor(Terrain.toYd(hi)) + 1); }
    /** The pad y (block over the cut ground) of face column (x, z) if the pit cuts it, else Integer.MIN_VALUE. */
    public static int floor(Site s, int x, int z) { double u = x + 0.5 - s.x - 0.5, v = z + 0.5 - s.z - 0.5; int k = -1;
        for (int b = 0; b <= BENCHES; b++) { double a = A - b * BENCH_W, c = B - b * BENCH_W; if (Math.pow(Math.abs(u) / a, 4) + Math.pow(Math.abs(v) / c, 4) <= 1) k = b; else break; }
        // 0.28.5 the stair: 3 m wide (|u| <= 1) in the -v end wall, one block down a block toward the middle, from STAIR_OUT m outside the
        // rim (cut through higher ground there) down to the floor; a ledge where the bench below is lower, a trench where it is higher
        // (measured before: it stopped on each bench, a 9-block drop to the next, and the ground at the rim stood 5 blocks over its top step)
        if (Math.abs(u) <= 1 && v < 0 && v >= -B - STAIR_OUT) { int sy = s.rimY - 1 + (int) Math.floor(-v) - B; if (k >= 0 || sy >= s.rimY - 1) return Math.max(s.rimY - BENCH_H * BENCHES, sy); }
        if (k < 0) return Integer.MIN_VALUE; int y = s.rimY - BENCH_H * Math.max(1, k);   // inside the rim: the first bench's level and down, a bench at a time
        return y; }
    /** The quarry's own blocks over its floor: (lx, y, lz) from the pit's centre, y from the floor's pad (0 = the surface layer); also the rim pieces. */
    public static String at(Site s, int lx, int y, int lz, int floorY) {
        double r4 = Math.pow(Math.abs(lx + 0.5) / A, 4) + Math.pow(Math.abs(lz + 0.5) / B, 4);
        // the pool on the floor: the middle of the bottom bench
        if (y == 0 && Math.pow(Math.abs(lx + 0.5) / (A - BENCHES * BENCH_W - 6), 4) + Math.pow(Math.abs(lz + 0.5) / (B - BENCHES * BENCH_W - 6), 4) <= 1) return "water";
        if (y == 0 && r4 <= 1) return ((lx * 7 + lz * 3) & 7) == 0 ? "gravel" : "cobblestone";   // the worked floor and benches: rubble and gravel
        return null; }
    /** Blocks on the rim, in absolute block y: the first gate on its plinth at +u, the office shed at +v; (x, z) face block coordinates. */
    public static String rim(Site s, int x, int y, int z) { int lx = x - s.x, lz = z - s.z, ry = y - s.gateY;   // the gate's and its sign's y from the gate's pad
        // the first gate: a ring 7 m across (radius 3 in blocks), gate frame round a dark, unpowered middle, on a 1 m cobblestone plinth, its plane along v, 6 m past the rim
        int gx = A + 6; if (lx >= gx - 2 && lx <= gx + 2 && Math.abs(lz) <= 5) { if (ry <= 0 && ry >= -FOUND) return Math.abs(lz) <= 4 && Math.abs(lx - gx) <= 1 ? "cobblestone" : null;   // the plinth, its foundation down to the ground
            if (lx == gx && ry >= 1 && ry <= 8) { double d = Math.hypot(lz, ry - 4.5); if (d >= 2.6 && d <= 3.9) return "hopelessinfinity:gate_frame"; return null; } }
        if (lx == gx - 2 && lz == 0 && ry == 1) return "sign:oak_sign[rotation=4]|white|0|THE FIRST GATE|COBBLE & SONS|BUILT HERE|STEP NO FURTHER";   // a standing sign before it
        // the old office shed: 8 x 6 m, cobblestone walls, an oak roof, a door toward the pit, the company sign over it
        ry = y - s.shedY; int sx0 = SX0, sx1 = SX1, sz0 = SZ0, sz1 = SZ1; if (lx >= sx0 && lx <= sx1 && lz >= sz0 && lz <= sz1 && ry >= -FOUND && ry <= 5) { boolean wall = lx == sx0 || lx == sx1 || lz == sz0 || lz == sz1;
            if (ry < 0) return "cobblestone"; /* the floor on a stone foundation down to the ground */ if (ry == 0) return "oak_planks"; if (ry == 5) return "oak_slab[type=bottom]"; if (ry == 4) return "oak_planks";
            if (wall && lz == sz0 && lx == 0 && ry <= 2) return ry == 1 ? "oak_door[facing=north,half=lower,hinge=left,open=false]" : "oak_door[facing=north,half=upper,hinge=left,open=false]";
            if (wall && ry == 2 && (lx == -2 || lx == 2) && lz == sz0) return "glass_pane"; if (wall) return "cobblestone"; return ry == 1 && lx == -3 && lz == sz1 - 1 ? "hopelessinfinity:desk[facing=south]" : null; }
        if (lz == B + 5 && lx == 0 && ry == 3) return "sign:oak_wall_sign[facing=north]|brown|0|COBBLE & SONS|QUARRY OFFICE|EST. YEAR ONE|";   // over the door
        return null; }
    /** Draws the quarry's blocks in the chunk [x0, x1) x [z0, z1) of face f. */
    public static void chunk(PlanetWorld w, int f, int x0, int z0, int x1, int z1, Metro.Sink out) { Site s = site(w); if (s == null || s.face != f) return;
        if (x1 <= s.x - A - 14 || x0 > s.x + A + 14 || z1 <= s.z - B - 14 || z0 > s.z + B + 14) return;
        for (int x = x0; x < x1; x++) for (int z = z0; z < z1; z++) { int fl = floor(s, x, z); if (fl != Integer.MIN_VALUE) for (int y = 0; y <= 1; y++) { String b = at(s, x - s.x, y, z - s.z, fl); if (b != null) { out.put(x, fl - 1 + y, z, b); BLOCKS.increment(); } }
            else for (int y = Math.min(s.gateY, s.shedY) - FOUND; y <= Math.max(s.gateY, s.shedY) + 9; y++) { String b = rim(s, x, y, z); if (b != null) { out.put(x, y, z, b); BLOCKS.increment(); } } } }
    private Quarry() {}
}
