package com.hopeless.infinity.planet;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 0.27.0 MEGACITY LAYOUT (owner, 2026-11-04: "large futuristic cities with super high skyscrapers like Shenzhen"; "normal width
 * skyscrapers, just really tall"; "adjust the city layout and the allowed footprint"; "large plazas ... large parks"; "a Stargate plaza in
 * the center"; "they should spawn at a set height"). Pure Java (tools/MetroTest, tools/MetroView). One plan a megacity, from the seed alone.
 *
 * THE GRID: superblocks at a pitch of PITCH_M = 246 m (derived: Futian CBD, Shenzhen, 607 ha (STATED, Modu) laid out as about 100 city
 * blocks (STATED, Civic Center master plan): sqrt(6.07 ha) = 246 m), streets AVENUE_M = 100 ft wide between them (STATED: the avenues of
 * Manhattan's 1811 Commissioners' Plan). The grid is square to the face's block grid, as the towns are.
 * THE AXIS: a park one superblock wide and AXIS_M = 2 km long (STATED: Futian's central axis, Lianhua Mountain to the Exhibition Center,
 * about 2 km) runs along u; a boulevard row (BOULEVARD_M = 135 m, STATED: Shennan Road through Futian) crosses it at the centre.
 * THE GATE PLAZA: the two axis superblocks just past the boulevard (centred on the HQ's front door); the Cobble & Sons headquarters (the city's tallest tower) on the 2 x 2
 * superblock beside it (owner: "the building that has the Stargate Plaza attached to it").
 * PARKS: PARK_SHARE = 14 % of the superblocks (STATED: NYC Parks, about 14 % of New York City's land is parkland), the axis included.
 * TOWERS: Shenzhen's skyline, scaled. Count: SZ_PER_M = 468 towers of 150 m or more (STATED, CTBUH) for 17,560,061 people (STATED,
 * 2020 China census) = 26.7 a million. Heights: the rank-height curve through Shenzhen's counts at 150 / 200 / 300 m (468 / 200 / 22,
 * STATED CTBUH) and its tallest (Ping An, 599.1 m, STATED), every height stretched by top / 599.1, so the city's skyline keeps Shenzhen's
 * shape and its tallest tower is `top` (owner: "the whole city to have that type of tower"). top = TOP_M x (pop / the planet's largest
 * megacity)^(1/3) (CHOSEN: the largest metro wins the height race; TOP_M = 1650 m, over a mile, owner).
 * WHERE: towers tallest first, each on the first lot its podium fits in, superblocks taken nearest the centre first (distance plus up to
 * JITTER = 1.5 superblocks of random, CHOSEN) - the tallest stand near the gate and height falls off outward (the density gradient).
 * A superblock takes one tower whose podium fits its block, or four on quarter lots; a 2 x 2 superblock takes one too big for one block.
 * Superblocks on sea, lake or river water are left out.
 */
public final class Metro {
    public static final double BIG_HALF = (2 * 246 - 100 * 0.3048) / 2, PITCH_M = 246, AVENUE_M = 100 * 0.3048, BLOCK_M = PITCH_M - AVENUE_M, AXIS_M = 2000, BOULEVARD_M = 135, PARK_SHARE = 0.14,
        SZ_POP = 17560061, SZ_PER_M = 468 / (SZ_POP / 1e6), SZ_TOP = 599.1, TOP_M = 1650, GRADIENT_KM = 0.081, JITTER = 1.5;
    static final double[][] SZ_CURVE = {{1, SZ_TOP}, {22, 300}, {200, 200}, {468, 150}};   // rank, height (m): Shenzhen (CTBUH)
    public static volatile boolean on = true;
    /** What a superblock is. */
    public enum Cell { FREE, BOULEVARD, AXIS, GATE_PLAZA, PARK, TOWER, TOWER_BIG, WATER, FUSION }
    /** One tower: its plan centre (u, v, m), height, seed, podium half width (m), whether it is the headquarters. */
    public static final class Lot { public final double u, v, lu, lv, lotHalf; public final int height, seed, half; public final boolean hq; public final int rank; volatile Tower tower; public volatile int padY = Integer.MIN_VALUE;
        /** lu, lv: the lot's centre; u, v: the tower's axis, on a block centre (whole metres from the plan centre); lotHalf: half the lot's side. */
        Lot(double lu, double lv, int height, int seed, int half, boolean hq, int rank, double lotHalf) { this.lu = lu; this.lv = lv; this.u = Math.round(lu); this.v = Math.round(lv); this.lotHalf = lotHalf; this.height = height; this.seed = seed; this.half = half; this.hq = hq; this.rank = rank; }
        public Tower tower() { Tower t = tower; if (t == null) tower = t = new Tower("metro " + rank, height, seed); return t; } }
    public static final class Plan {
        public final Sites.Site site; public int face; public double cx, cz; public boolean alongX; public double top; public int wantTowers, extentCells, water, parks; public long ms;
        public final Map<Long, Cell> cells = new HashMap<>(); public final List<Lot> towers = new ArrayList<>(); public String note = ""; public double reachM; public int plazaPadY = Integer.MIN_VALUE;
        /** 0.27.7 the boulevard (row i = 0) as the city's trunk road: its half length along v and its pad y every BLVD_STEP m from v = -blvdHalf. */
        public double blvdHalf; public int[] blvdY; public double[] blvdZ; public final List<double[]> portals = new ArrayList<>(); public final Set<Long> ownedParks = new HashSet<>(); public final List<Long> fusionCells = new ArrayList<>(); public int fusionUnits; volatile List<SkyBridges.Bridge> bridges; volatile float[] streetZ; int sgN; double sgHalf; double[] portalZ; public long streetMs; public int streetPasses;   /* 0.27.8 {u, v, kind (0 boulevard, 1 avenue along u, 2 along v), side} */ final double[] endZ = {Double.NaN, Double.NaN};
        /** 0.27.0 the lots whose square reaches each superblock (for the columns and the chunks). */
        final Map<Long, List<Lot>> byCell = new HashMap<>();
        Plan(Sites.Site s) { site = s; }
        /** Plan coordinates (u, v) of face block x, z. */
        public double[] uv(double x, double z) { return alongX ? new double[]{x - cx, z - cz} : new double[]{z - cz, x - cx}; }
        public double[] xz(double u, double v) { return alongX ? new double[]{cx + u, cz + v} : new double[]{cx + v, cz + u}; }
        public Cell cell(int i, int j) { Cell c = cells.get(key(i, j)); return c == null ? Cell.FREE : c; }
        public String probe() { int[] band = new int[4]; for (Lot l : towers) band[l.height >= 1000 ? 3 : l.height >= 600 ? 2 : l.height >= 300 ? 1 : 0]++;
            return String.format("metro %s (pop %,d): %d towers of %d wanted (under 300 m %d, 300-600 %d, 600-1000 %d, 1000+ %d), tallest %d m (HQ), %d superblocks (%d parks, %d on water left out), %d ms%s",
                site.name, site.pop, towers.size(), wantTowers, band[0], band[1], band[2], band[3], towers.isEmpty() ? 0 : towers.get(0).height, extentCells, parks, water, ms, note.isEmpty() ? "" : " - " + note); } }
    public static long key(int i, int j) { return ((long) i << 32) ^ (j & 0xffffffffL); }

    final PlanetWorld w; final Sites st; final Terrain t; final double R; final long seed; final long bigPop; final List<Sites.Site> mega = new ArrayList<>();
    public final ConcurrentHashMap<Integer, Plan> plans = new ConcurrentHashMap<>();
    public Metro(PlanetWorld w) { this.w = w; st = w.sites(); t = w.terrain; R = w.cube.R; seed = w.seed ^ 0x3E7A0L; long b = 0; for (Sites.Site s : st.all) { if (s.kind == Sites.Kind.MEGACITY) { b = Math.max(b, s.pop); mega.add(s); } if (s.kind == Sites.Kind.LARGE_CITY && s.pop >= LARGE_MIN) large.add(s); } bigPop = b; }
    /** 0.28.2 the cities Metro plans: megacities and large cities. */
    public static boolean isCity(Sites.Site s) { return s.kind == Sites.Kind.MEGACITY || s.kind == Sites.Kind.LARGE_CITY && s.pop >= LARGE_MIN; }
    /** 0.28.2 a large city of a million people or more gets towers (CHOSEN; measured on seed 1: 149 of the 3,530 large cities, 50,000 to 9.4 million people; below a million the towers would be 26 or fewer). */
    public static final long LARGE_MIN = 1_000_000;
    final List<Sites.Site> large = new ArrayList<>();
    public Plan plan(Sites.Site s) { return plans.computeIfAbsent(s.id, k -> make(s)); }

    /** Tower heights, tallest first: Shenzhen's rank-height curve, n towers, tallest `top` (see the class note). */
    public static int[] heights(int n, double top, long sd) { int[] h = new int[n]; double scale = top / SZ_TOP;
        for (int k = 1; k <= n; k++) { double r = Math.max(1, k * 468.0 / n); int s = 0; while (s < SZ_CURVE.length - 2 && r > SZ_CURVE[s + 1][0]) s++;
            double a = Math.log(SZ_CURVE[s][0]), b = Math.log(SZ_CURVE[s + 1][0]), f = (Math.log(r) - a) / (b - a), hm = Math.exp(Math.log(SZ_CURVE[s][1]) + f * (Math.log(SZ_CURVE[s + 1][1]) - Math.log(SZ_CURVE[s][1])));
            double jit = k == 1 ? 1 : 1 + 0.04 * (MoonTerrain.u(MoonTerrain.hash(sd, 3, k, 0, 0), 0) - 0.5);   // +-2 % (CHOSEN) so equal ranks do not match
            h[k - 1] = (int) Math.round(hm * scale * jit); }
        for (int k = 1; k < n; k++) h[k] = Math.min(h[k], h[0] - 4 - 4 * Math.min(k, 3));   /* 0.27.0 measured: "- 4 k" went negative past rank 412 */   // nobody outbuilds the headquarters (owner: "whoever has the largest building wins like Cobble and Sons")
        if (n > 0) h[0] = (int) Math.round(top);   // 0.27.0 measured: with fewer than 468 towers rank 1 fell below the top
        return h; }

    Plan make(Sites.Site s) {
        long t0 = System.nanoTime(); Plan p = new Plan(s); double[] c = st.exact(s); if (c == null) { c = s.s; p.note = "no dry flat spot: centred on the site"; }
        double[] lc = w.cube.locate(c); p.face = (int) lc[0]; p.cx = Math.floor(lc[1]) + 0.5; p.cz = Math.floor(lc[2]) + 0.5; long sd = seed ^ (s.id * 0x9E3779B97F4A7C15L);
        p.alongX = true;   // 0.27.0: u along the face's x always (a turned plan would mirror the towers' doors, stairs and shaped blocks)
        { int need = (int) Math.round(s.pop / 1e6 * SZ_PER_M / 1.4 / (1 - PARK_SHARE)), r0 = (int) Math.ceil(Math.sqrt(need / Math.PI)); shiftDry(p, r0); }
        p.top = Math.round(TOP_M * Math.cbrt(s.pop / (double) Math.max(s.pop, bigPop)));
        // 0.28.2 a large city (owner: "large cities also get towers, laid out in a way that makes sense for each city"): its tallest from the US
        // large cities (STATED, CTBUH: Chicago 2.7 M people, 442 m; New York 8.3 M, 541 m; a power law through both, exponent 0.18), no gate
        // plaza and no headquarters front (those are the megacities'), the same superblocks, boulevard, towers, parks and streets
        boolean large = s.kind == Sites.Kind.LARGE_CITY; if (large) p.top = Math.round(442 * Math.pow(s.pop / 2.7e6, Math.log(541.0 / 442) / Math.log(8.3 / 2.7)));
        int n = (int) Math.round(s.pop / 1e6 * SZ_PER_M); p.wantTowers = n; int[] hs = heights(n, p.top, sd);
        // the fixed cells: the boulevard row (i = 0), the axis (j = 0, |u| <= AXIS_M / 2), the gate plaza (1, 0)
        int ax = (int) Math.round(AXIS_M / 2 / PITCH_M);
        // superblocks in weighted order, ring by ring out to where all the towers fit
        int ring = (int) Math.ceil(Math.sqrt(n / 2.0)) + ax + 2, R2 = ring * 3;
        List<double[]> order = new ArrayList<>();
        for (int i = -R2; i <= R2; i++) for (int j = -R2; j <= R2; j++) { double d = Math.hypot(i, j);   // 0.27.0 measured (MetroView): the weighted order spread towers 39 km; now nearest first, jitter JITTER cells (CHOSEN)
            double u = MoonTerrain.u(MoonTerrain.hash(sd, 2, i, j, 0), 0); order.add(new double[]{-(d + JITTER * u), i, j}); }
        order.sort((x, y) -> Double.compare(y[0], x[0]));
        for (int j = -R2; j <= R2; j++) p.cells.put(key(0, j), Cell.BOULEVARD);
        for (int i = -ax; i <= ax; i++) if (i != 0) p.cells.put(key(i, 0), Cell.AXIS);
        if (!large) { p.cells.put(key(1, 0), Cell.GATE_PLAZA); p.cells.put(key(2, 0), Cell.GATE_PLAZA); } else { p.cells.put(key(1, 0), Cell.AXIS); p.cells.put(key(2, 0), Cell.AXIS); }   // 0.27.0 two superblocks, centred on the HQ's front
        // water: superblocks whose centre is on sea, lake or river water are left out (the fixed cells stay: they are bridged later)
        Map<Long, Boolean> wet = new HashMap<>();
        java.util.function.BiPredicate<Integer, Integer> isWet = (i, j) -> wet.computeIfAbsent(key(i, j), k -> wetBlock(p, i, j));
        // parks: PARK_SHARE of the superblocks, the axis counting toward it
        // towers: the headquarters first on the 2 x 2 beside the gate plaza (cells (1..2, 1..2)), then tallest first
        double lotQ = BLOCK_M / 2;   // a quarter lot's side
        Map<Long, int[]> quarters = new LinkedHashMap<>();   // a TOWER superblock with quarter lots: which quarters are used (bit mask) in [0]
        int placed = 0; int[] tried = {0};
        Tower hqT = new Tower("hq", hs.length > 0 ? hs[0] : (int) p.top, (int) (sd & 0x7fffffff)); int hqHalf = hqT.reach();
        if (n > 0) { for (int i = 1; i <= 2; i++) for (int j = 1; j <= 2; j++) p.cells.put(key(i, j), Cell.TOWER_BIG); p.towers.add(new Lot(1.5 * PITCH_M, 1.5 * PITCH_M, hs[0], (int) (sd & 0x7fffffff), hqHalf, !large, 1, BIG_HALF)); placed = 1; }
        int oi = 0; int parkN = 2 * ax, visited = 2 * ax + 1 + (n > 0 ? 4 : 0);   // 0.27.0 measured: a hash share gave 19 % parks; now PARK_SHARE of the superblocks taken, in order
        for (int k = placed; k < n; k++) { int sdk = (int) (MoonTerrain.hash(sd, 4, k, 0, 0) & 0x7fffffff); Tower tw = new Tower("t", hs[k], sdk); int half = tw.reach(); double side = 2 * half + 2;   // + 1 m: the axis is rounded to a block centre
            boolean done = false;
            if (side <= lotQ) {   // a quarter lot in a superblock already taken by quarter towers, else a fresh superblock
                for (var e : quarters.entrySet()) { int m = e.getValue()[0]; if (m == 15) continue; int q = Integer.numberOfTrailingZeros(~m); e.getValue()[0] |= 1 << q; int i = (int) (e.getKey() >> 32), j = (int) (long) e.getKey();
                    p.towers.add(new Lot(i * PITCH_M + (q % 2 == 0 ? -1 : 1) * lotQ / 2, j * PITCH_M + (q / 2 == 0 ? -1 : 1) * lotQ / 2, hs[k], sdk, half, false, k + 1, lotQ / 2)); done = true; break; } }
            while (!done && oi < order.size()) { double[] o = order.get(oi); int i = (int) o[1], j = (int) o[2];
                if (p.cell(i, j) != Cell.FREE) { oi++; continue; }
                if (isWet.test(i, j)) { p.cells.put(key(i, j), Cell.WATER); p.water++; oi++; continue; }
                if (parkN < PARK_SHARE * (visited + 1)) { p.cells.put(key(i, j), Cell.PARK); parkN++; visited++; oi++; continue; }
                if (side <= lotQ) { visited++; p.cells.put(key(i, j), Cell.TOWER); quarters.put(key(i, j), new int[]{1}); p.towers.add(new Lot(i * PITCH_M - lotQ / 2, j * PITCH_M - lotQ / 2, hs[k], sdk, half, false, k + 1, lotQ / 2)); done = true; oi++; break; }
                if (side <= BLOCK_M) { visited++; p.cells.put(key(i, j), Cell.TOWER); p.towers.add(new Lot(i * PITCH_M, j * PITCH_M, hs[k], sdk, half, false, k + 1, BLOCK_M / 2)); done = true; oi++; break; }
                // a 2 x 2: this cell and the three toward +i, +j, all free and dry
                boolean fits = side <= 2 * PITCH_M - AVENUE_M; for (int a = 0; a < 2 && fits; a++) for (int b = 0; b < 2 && fits; b++) if ((a | b) != 0 && (p.cell(i + a, j + b) != Cell.FREE || isWet.test(i + a, j + b))) fits = false;
                if (fits) { visited += 4; for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) p.cells.put(key(i + a, j + b), Cell.TOWER_BIG); p.towers.add(new Lot((i + 0.5) * PITCH_M, (j + 0.5) * PITCH_M, hs[k], sdk, half, false, k + 1, BIG_HALF)); done = true; break; }
                break; }   // a big tower that did not fit here tries the next cells without using this one up
            if (!done) { // the big tower walks on through the order past cells it cannot use
                for (int oj = oi + 1; oj < order.size() && !done; oj++) { double[] o = order.get(oj); int i = (int) o[1], j = (int) o[2]; boolean fits = side <= 2 * PITCH_M - AVENUE_M;
                    for (int a = 0; a < 2 && fits; a++) for (int b = 0; b < 2 && fits; b++) if (p.cell(i + a, j + b) != Cell.FREE || isWet.test(i + a, j + b)) fits = false;
                    if (fits) { visited += 4; for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) p.cells.put(key(i + a, j + b), Cell.TOWER_BIG); p.towers.add(new Lot((i + 0.5) * PITCH_M, (j + 0.5) * PITCH_M, hs[k], sdk, half, false, k + 1, BIG_HALF)); done = true; } } }
            if (!done) { p.note += (p.note.isEmpty() ? "" : "; ") + "tower " + (k + 1) + " found no lot"; break; } }
        // 0.28.0 a megatall's park (planet/Parks): the first free superblock beside its lot (4-neighbours, in order +u, -u, +v, -v) becomes its park
        for (Lot l : p.towers) { if (l.height < Parks.MEGATALL_M) continue; int i0 = (int) Math.floor((l.lu - l.lotHalf) / PITCH_M + 0.5), i1 = (int) Math.floor((l.lu + l.lotHalf) / PITCH_M + 0.5), j0 = (int) Math.floor((l.lv - l.lotHalf) / PITCH_M + 0.5), j1 = (int) Math.floor((l.lv + l.lotHalf) / PITCH_M + 0.5);
            boolean done = false; for (int[] nc : new int[][]{{i1 + 1, j0}, {i0 - 1, j0}, {i0, j1 + 1}, {i0, j0 - 1}, {i1 + 1, j1}, {i0 - 1, j1}, {i1, j1 + 1}, {i1, j0 - 1}}) { if (done) break; long k = key(nc[0], nc[1]); Cell cl = p.cells.get(k);
                if ((cl == null || cl == Cell.FREE) && !wetBlock(p, nc[0], nc[1])) { p.cells.put(k, Cell.PARK); p.ownedParks.add(k); p.parks++; done = true; } else if (cl == Cell.PARK) { p.ownedParks.add(k); done = true; } } }
        // 0.28.3 the fusion plant (planet/FusionPlant): the superblocks it needs, one compact group on dry free ground at the city's edge: seeded at
        // the free superblock beside the built area nearest the -u end of the axis (CHOSEN: away from the gate plaza at +u), grown nearest-first
        { p.fusionUnits = FusionPlant.units(s.pop); int need = (int) Math.ceil(p.fusionUnits / (double) FusionPlant.PER_BLOCK); long seedK = Long.MIN_VALUE; double bestD = 1e18;
            for (var e : new ArrayList<>(p.cells.entrySet())) { Cell cl = e.getValue(); if (cl != Cell.TOWER && cl != Cell.TOWER_BIG && cl != Cell.PARK && cl != Cell.AXIS) continue; int ci = (int) (e.getKey() >> 32), cj = (int) (long) e.getKey();
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { int ni = ci + d[0], nj = cj + d[1]; if (ni == 0) continue; Cell c2 = p.cells.get(key(ni, nj)); if (c2 != null && c2 != Cell.FREE) continue;
                    double dd = Math.hypot(ni + 1000, nj) + (wetBlock(p, ni, nj) ? 1e6 : 0); if (dd < bestD) { bestD = dd; seedK = key(ni, nj); } } }   // a dry seed first; a wet one only if none (the growth then crosses the water)
            if (seedK != Long.MIN_VALUE) { int si = (int) (seedK >> 32), sj = (int) (long) seedK; PriorityQueue<long[]> pq = new PriorityQueue<>(Comparator.comparingLong(q -> q[1])); Set<Long> seenF = new HashSet<>(); pq.add(new long[]{seedK, 0}); seenF.add(seedK);   // grown across free dry ground (and on past water within FUSION_REACH cells, measured: 6 of 157 cities walled in by water found too little room)
                while (!pq.isEmpty() && p.fusionCells.size() < need) { long k = pq.poll()[0]; int ci = (int) (k >> 32), cj = (int) (long) k; Cell c2 = p.cells.get(k); if (Math.abs(ci - si) + Math.abs(cj - sj) > FUSION_REACH) continue; boolean ok = (c2 == null || c2 == Cell.FREE) && ci != 0 && !wetBlock(p, ci, cj);
                    if (!ok) { if (c2 == null || c2 == Cell.FREE || c2 == Cell.WATER) for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { long nk = key(ci + d[0], cj + d[1]); if (seenF.add(nk)) pq.add(new long[]{nk, (long) ((ci + d[0] - si) * (ci + d[0] - si) + (cj + d[1] - sj) * (cj + d[1] - sj))}); } continue; }
                    p.cells.put(k, Cell.FUSION); p.fusionCells.add(k);
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { long nk = key(ci + d[0], cj + d[1]); if (seenF.add(nk)) pq.add(new long[]{nk, (long) ((ci + d[0] - si) * (ci + d[0] - si) + (cj + d[1] - sj) * (cj + d[1] - sj))}); } } } }
        int maxI = 0; for (var e : p.cells.entrySet()) { Cell cl = e.getValue(); if (cl == Cell.TOWER || cl == Cell.TOWER_BIG || cl == Cell.PARK || cl == Cell.AXIS || cl == Cell.GATE_PLAZA || cl == Cell.FUSION) { p.extentCells++; int i = (int) (e.getKey() >> 32), j = (int) (long) e.getKey(); maxI = Math.max(maxI, Math.max(Math.abs(i), Math.abs(j))); }
            if (cl == Cell.PARK || cl == Cell.AXIS) p.parks++; }
        // the boulevard only as long as the city
        final int lim = maxI + 1; p.cells.entrySet().removeIf(e -> e.getValue() == Cell.BOULEVARD && Math.abs((int) (long) e.getKey()) > lim);
        for (Lot l : p.towers) { int i0 = (int) Math.floor((l.lu - l.lotHalf) / PITCH_M + 0.5), i1 = (int) Math.floor((l.lu + l.lotHalf) / PITCH_M + 0.5), j0 = (int) Math.floor((l.lv - l.lotHalf) / PITCH_M + 0.5), j1 = (int) Math.floor((l.lv + l.lotHalf) / PITCH_M + 0.5);
            for (int i = i0; i <= i1; i++) for (int j = j0; j <= j1; j++) p.byCell.computeIfAbsent(key(i, j), z -> new ArrayList<>()).add(l); }
        int ext = 0; for (var e : p.cells.entrySet()) if (e.getValue() != Cell.WATER && e.getValue() != Cell.BOULEVARD) ext = Math.max(ext, Math.max(Math.abs((int) (e.getKey() >> 32)), Math.abs((int) (long) e.getKey())));
        p.reachM = (ext + 1) * PITCH_M; boulevard(p); p.reachM = Math.max(p.reachM, p.blvdHalf);
        p.ms = (System.nanoTime() - t0) / 1_000_000; return p; }

    /** 0.27.0 a superblock is on water when any point of a 5 x 5 grid over its block (at 0, +-0.22, +-0.45 of the block) is (measured:
     *  centre-only, then centre and four edge points, let quarter-lot towers stand on a river and on shore under 2 m). */
    static final double[] WET_AT = {-0.45, -0.22, 0, 0.22, 0.45};
    boolean wetBlock(Plan p, int i, int j) { for (double a : WET_AT) for (double b : WET_AT) if (wetAt(p, i * PITCH_M + a * BLOCK_M, j * PITCH_M + b * BLOCK_M)) return true; return false; }
    boolean wetAt(Plan p, double u, double v) { double[] hw = t.heightAndWater(sphere(p, u, v), R); return hw[0] < 2 || hw[2] == 3 || (!Double.isNaN(hw[1]) && hw[1] > hw[0] - 1); }
    /** 0.27.0 a city centred on water (measured: Riverland, Fairview grew as rings round a bay): when under 90 % of the superblocks within
     *  r0 of the centre are dry, the centre moves (2-superblock steps, up to SHIFT_CELLS) to the spot with the most dry ground within r0. */
    static final int SHIFT_CELLS = 40;   // about 10 km (CHOSEN; measured: Riverland found 4 of 149 dry within 4 km)
    void shiftDry(Plan p, int r0) { Map<Long, Boolean> c = new HashMap<>();
        java.util.function.ToIntBiFunction<Integer, Integer> score = (si, sj) -> { int d = 0; for (int a = -r0; a <= r0; a += 2) for (int b = -r0; b <= r0; b += 2) { if (a * a + b * b > r0 * r0) continue; int ia = si + a, jb = sj + b; if (!c.computeIfAbsent(key(ia, jb), k -> wetAt(p, ia * PITCH_M, jb * PITCH_M))) d++; } return d; };
        int all = 0; for (int a = -r0; a <= r0; a += 2) for (int b = -r0; b <= r0; b += 2) if (a * a + b * b <= r0 * r0) all++;
        int s0 = score.applyAsInt(0, 0); if (s0 >= 0.9 * all) return; int best = s0, bi = 0, bj = 0;
        for (int si = -SHIFT_CELLS; si <= SHIFT_CELLS; si += 2) for (int sj = -SHIFT_CELLS; sj <= SHIFT_CELLS; sj += 2) { int sc = score.applyAsInt(si, sj); if (sc > best || (sc == best && Math.hypot(si, sj) < Math.hypot(bi, bj))) { best = sc; bi = si; bj = sj; } }
        if (bi == 0 && bj == 0) return; double[] xz = p.xz(bi * PITCH_M, bj * PITCH_M); p.cx = Math.floor(xz[0]) + 0.5; p.cz = Math.floor(xz[1]) + 0.5;
        p.note += (p.note.isEmpty() ? "" : "; ") + String.format("centre moved %.1f km to dry ground (%d -> %d of %d dry)", Math.hypot(bi, bj) * PITCH_M / 1000, s0, best, all); }

    /** 0.27.7 the boulevard, graded as a road: the ground along its centre line (water: 1 m over it, a causeway: CHOSEN until bridges),
     *  smoothed over 120 m as Roads smooths its target (CHOSEN, same window), then held to the arterial grade (Roads.GRADE: 7 %) both ways.
     *  The roads into the city end at its two ends (portals), so the network reaches the city's centre (measured before: Riverland's
     *  roads ended 13.5 km from its moved centre, Richbridge's on 6 tower lots). */
    static final int FUSION_REACH = 12;   // 0.28.3 superblocks from the plant's seed it may spread (CHOSEN: about 3 km)
    public static final double BLVD_STEP = 16, LEAD_M = 1200, END_WATER_M = 120;   // END_WATER_M: water this close past an end is bridged at the end's height (CHOSEN: the road profile's node window, 3 x 16 m, and a margin)   // LEAD_M: the road's last straight along the boulevard line (CHOSEN: about 2 x the Interstate's 552 m minimum radius)
    void boulevard(Plan p) { int lim = 0; for (var e : p.cells.entrySet()) if (e.getValue() == Cell.BOULEVARD) lim = Math.max(lim, Math.abs((int) (long) e.getKey()));
        p.blvdHalf = (lim + 0.5) * PITCH_M; int n = (int) Math.ceil(2 * p.blvdHalf / BLVD_STEP) + 1; double[] g = new double[n];
        for (int k = 0; k < n; k++) { double[] hw = t.heightAndWater(sphere(p, 0, -p.blvdHalf + k * BLVD_STEP), R); g[k] = Double.isNaN(hw[1]) || hw[1] <= hw[0] ? hw[0] : hw[1] + 1; }
        int win = (int) Math.round(60 / BLVD_STEP); double[] z = new double[n]; for (int k = 0; k < n; k++) { double sm = 0; int c = 0; for (int q = Math.max(0, k - win); q <= Math.min(n - 1, k + win); q++) { sm += g[q]; c++; } z[k] = sm / c; }
        double dz = Roads.GRADE[Roads.Cls.ARTERIAL.ordinal()] * BLVD_STEP; for (int k = 1; k < n; k++) z[k] = Math.max(Math.min(z[k], z[k - 1] + dz), z[k - 1] - dz); for (int k = n - 2; k >= 0; k--) z[k] = Math.max(Math.min(z[k], z[k + 1] + dz), z[k + 1] - dz);
        // a road's bridge just past an end: the road holds over water (Roads: water + 8 m on a big river or lake, else 4 m) and the boulevard's
        // end meets it, ramped down inward at the grade (measured: Saltridge City's north end, a river 50 m out, the roads' end 16 blocks over the pad)
        for (int sg = -1; sg <= 1; sg += 2) { double need = Double.NEGATIVE_INFINITY; for (double o = 0; o <= END_WATER_M; o += 8) { double[] q = sphere(p, 0, sg * (p.blvdHalf + o)); double[] hw = t.heightAndWater(q, R);
                if (!Double.isNaN(hw[1]) && hw[1] > hw[0]) { int c = t.rivers.cellOf(q); boolean big = t.rivers.kind[c] == 2 || t.rivers.q[c] >= 1000; need = Math.max(need, hw[1] + (big ? Roads.CLEAR_BIG_M : Roads.CLEAR_SMALL_M) + 0.3); } }
            if (need == Double.NEGATIVE_INFINITY) continue; int e = sg < 0 ? 0 : n - 1; z[e] = Math.max(z[e], need); if (sg < 0) for (int k = 1; k < n; k++) z[k] = Math.max(z[k], z[k - 1] - dz); else for (int k = n - 2; k >= 0; k--) z[k] = Math.max(z[k], z[k + 1] - dz); }
        p.blvdZ = z; p.blvdY = new int[n]; for (int k = 0; k < n; k++) p.blvdY[k] = (int) Math.floor(Terrain.toYd(z[k])) + 1; }
    /** 0.27.7 the sphere point of plan point (u, v), and of the plan's centre (for Roads). */
    public double[] at(Plan p, double u, double v) { return sphere(p, u, v); }
    public double[] centre(Plan p) { return sphere(p, 0, 0); }
    /** 0.27.7 the boulevard's graded height (m) at the end nearest sphere point q (the height a road into the city meets), or NaN if site s is no megacity. */
    public double blvdEndZ(Sites.Site s, double[] q) { if (!isCity(s)) return Double.NaN; Plan p = plan(s); if (p.blvdZ == null) return Double.NaN;
        double[] a = sphere(p, 0, -p.blvdHalf), b = sphere(p, 0, p.blvdHalf); double da = 0, db = 0; for (int k = 0; k < 3; k++) { da += (q[k] - a[k]) * (q[k] - a[k]); db += (q[k] - b[k]) * (q[k] - b[k]); }
        if (Math.sqrt(Math.min(da, db)) * R > 100) return Double.NaN;   // 0.27.8 an avenue's end: its street meets the road (planet/Metro streets)
        return da < db ? p.blvdZ[0] : p.blvdZ[p.blvdZ.length - 1]; }
    /** 0.27.7 the boulevard's pad y at plan v: its graded height, and near each end a ramp (at the arterial's 7 %) to the highest end of the
     *  laid roads that arrive there (the roads are the authority at the portal: an Interstate's crest K can hold its end metres over the
     *  graded ground; measured: Saltridge City's north end, road 592 ends 43.4 m over a 40.0 m end). */
    public int blvdPad(Plan p, double v) { return (int) Math.floor(Terrain.toYd(blvdZAt(p, v))) + 1; }
    public int blvdPad(Plan p, double u, double v) { return (int) Math.floor(Terrain.toYd(blvdZAt(p, u, v))) + 1; }
    /** 0.27.7 the highest end height of the roads arriving at boulevard end e (0: v = -blvdHalf, 1: +blvdHalf), laying them if need be
     *  (each road the same whenever it is laid, so the answer does not hang on the order chunks are made); NaN while the network builds. */
    double roadEndZ(Plan p, int e) { double z = p.endZ[e]; if (!Double.isNaN(z)) return z; Roads rd = w.roadsIfMade(); if (rd == null || Roads.building()) return Double.NaN;
        double[] end = sphere(p, 0, (e == 0 ? -1 : 1) * p.blvdHalf); double mx = Double.NEGATIVE_INFINITY;
        for (Roads.Edge ed : rd.edges) { if (ed.a != p.site.id && ed.b != p.site.id) continue; double[] q = ed.a == p.site.id ? ed.pts[0] : ed.pts[ed.pts.length - 1]; if (Rivers.arc(q, end) * R > 100) continue;   /* the boulevard's slots: up to 80 m off its line */
            Roads.Route r = rd.route(ed.id); mx = Math.max(mx, ed.a == p.site.id ? r.z[0] : r.z[r.n() - 1]); }
        z = mx == Double.NEGATIVE_INFINITY ? p.blvdZ[e == 0 ? 0 : p.blvdZ.length - 1] : mx; p.endZ[e] = z; return z; }
    /** 0.27.8 the portals of a city's roads, one per road, in the order given: {portal, lead (LEAD_M further out on its line)}. An
     *  Interstate (big) ends at the boulevard's end on the side it goes toward, side by side across the boulevard (slots 0, +40, -40 m:
     *  the 35 m Interstate and a 5 m gap, CHOSEN; three fit the 135 m boulevard); any other road at the end of the avenue nearest the line
     *  toward where it goes, on the city's edge (the last built cell of the two rows the avenue runs between), one road an avenue end, the
     *  avenues within 200 m of the boulevard left to the Interstates. The plan keeps them (Plan.portals) for the streets and the tests. */
    public static final double[] SLOTS = {0, 40, -40, 80, -80}; public static final double PORTAL_GAP_M = 150;   // CHOSEN: two road ends at least this far apart (more than half a superblock)
    public List<double[][]> portals(Plan p, List<double[]> to, List<Boolean> big) { double[] c = sphere(p, 0, 0), eu = sphere(p, 1, 0), ev = sphere(p, 0, 1);
        List<double[][]> out = new ArrayList<>(); int[] slot = new int[2]; Set<Long> used = new HashSet<>(); p.portals.clear(); int lim = (int) Math.floor(p.reachM / PITCH_M);
        // 0.28.5 the Interstates at one end take the slots in the order of where they go (measured: given in edge order, two Interstates out of
        // Grandstead City swapped sides and crossed 360 m past the end, too near for a cloverleaf's C-D roads)
        Map<Integer, Double> slotU = new HashMap<>(); for (int sg = -1; sg <= 1; sg += 2) { List<double[]> mine = new ArrayList<>();
            for (int k = 0; k < to.size(); k++) { if (!big.get(k)) continue; double du = 0, dv = 0; for (int q = 0; q < 3; q++) { du += (to.get(k)[q] - c[q]) * (eu[q] - c[q]); dv += (to.get(k)[q] - c[q]) * (ev[q] - c[q]); } if ((dv >= 0 ? 1 : -1) == sg) mine.add(new double[]{k, du / Math.max(1e-12, Math.abs(dv))}); }
            mine.sort((a, b) -> Double.compare(a[1], b[1])); double[] us = new double[mine.size()]; for (int i = 0; i < us.length; i++) us[i] = SLOTS[Math.min(SLOTS.length - 1, i)]; Arrays.sort(us);
            for (int i = 0; i < us.length; i++) slotU.put((int) mine.get(i)[0], us[i]); }
        for (int k = 0; k < to.size(); k++) { double du = 0, dv = 0; for (int q = 0; q < 3; q++) { du += (to.get(k)[q] - c[q]) * (eu[q] - c[q]); dv += (to.get(k)[q] - c[q]) * (ev[q] - c[q]); }
            if (big.get(k)) { int sg = dv >= 0 ? 1 : -1; double u = slotU.get(k);
                out.add(new double[][]{sphere(p, u, sg * p.blvdHalf), sphere(p, u, sg * (p.blvdHalf + LEAD_M))}); p.portals.add(new double[]{u, sg * p.blvdHalf, 0, sg}); continue; }
            boolean alongU = Math.abs(du) >= Math.abs(dv); int sg = (alongU ? du : dv) >= 0 ? 1 : -1; double cross = (alongU ? dv / Math.abs(du) : du / Math.abs(dv)) * p.reachM;
            int j0 = (int) Math.round(cross / PITCH_M - 0.5); double[][] got = null;
            for (int t = 0; t < 4 * lim && got == null; t++) { int j = j0 + ((t & 1) == 0 ? t / 2 : -(t + 1) / 2); double line = (j + 0.5) * PITCH_M; if (Math.abs(line) > p.reachM - PITCH_M) continue; if (!alongU && Math.abs(line) < 200) continue;
                long key = ((alongU ? 1L : 2L) << 40) ^ ((long) (sg + 1) << 32) ^ (j & 0xffffffffL); if (used.contains(key)) continue;
                int edge = 0; for (var e : p.cells.entrySet()) { Cell cl = e.getValue(); if (cl == Cell.FREE || cl == Cell.WATER || cl == Cell.BOULEVARD) continue; int ci = (int) (e.getKey() >> 32), cj = (int) (long) e.getKey(); int along = alongU ? ci : cj, acr = alongU ? cj : ci;
                    if ((acr == j || acr == j + 1) && along * sg > edge) edge = along * sg; }
                double ed = (Math.max(edge, 1) + 0.5) * PITCH_M; double pu = alongU ? sg * ed : line, pv = alongU ? line : sg * ed; boolean near = false;
                for (double[] q : p.portals) if (Math.hypot(q[0] - pu, q[1] - pv) < PORTAL_GAP_M) near = true; if (near) continue; used.add(key);   /* measured: an avenue's end along u and one along v met at a city corner, two roads on one point */
                got = alongU ? new double[][]{sphere(p, sg * ed, line), sphere(p, sg * (ed + LEAD_M), line)} : new double[][]{sphere(p, line, sg * ed), sphere(p, line, sg * (ed + LEAD_M))};
                p.portals.add(alongU ? new double[]{sg * ed, line, 1, sg} : new double[]{line, sg * ed, 2, sg}); }
            if (got == null) { int sg2 = dv >= 0 ? 1 : -1; got = new double[][]{sphere(p, 0, sg2 * p.blvdHalf), sphere(p, 0, sg2 * (p.blvdHalf + LEAD_M))}; p.portals.add(new double[]{0, sg2 * p.blvdHalf, 0, sg2}); }
            out.add(got); }
        return out; }
    /** 0.27.8 THE STREETS: the avenues (AVENUE_M = 100 ft, STATED in the layout) between the superblocks, paved where a built cell
     *  (tower, park, axis, plaza) is on either side, under the lots (a 2 x 2 tower lot spans the avenue between its blocks: the avenue
     *  stops there, as in a superblock). Their height: one field over the plan, the ground on a STREET_GRID grid (water: 1 m over it, a
     *  causeway: CHOSEN until bridges), smoothed over +-64 m as the roads smooth theirs, held to the arterial's 7 % between neighbours,
     *  then near the boulevard and near each road's end an envelope at 7 % to their heights, so the streets meet them level. */
    public static final double STREET_GRID = 32;
    public boolean onAvenue(Plan p, double u, double v) { double fu = u / PITCH_M - 0.5, fv = v / PITCH_M - 0.5; long iu = Math.round(fu), iv = Math.round(fv);
        boolean au = Math.abs(fu - iu) * PITCH_M <= AVENUE_M / 2, av = Math.abs(fv - iv) * PITCH_M <= AVENUE_M / 2; if (!au && !av) return false;
        int ci = (int) Math.floor(u / PITCH_M + 0.5), cj = (int) Math.floor(v / PITCH_M + 0.5);
        if (au && (builtCell(p, (int) iu, cj) || builtCell(p, (int) iu + 1, cj))) return true; if (av && (builtCell(p, ci, (int) iv) || builtCell(p, ci, (int) iv + 1))) return true;
        return au && av && (builtCell(p, (int) iu, (int) iv) || builtCell(p, (int) iu + 1, (int) iv) || builtCell(p, (int) iu, (int) iv + 1) || builtCell(p, (int) iu + 1, (int) iv + 1)); }
    boolean builtCell(Plan p, int i, int j) { Cell c = p.cells.get(key(i, j)); return c != null && c != Cell.FREE && c != Cell.WATER && c != Cell.BOULEVARD; }   // FUSION counts: streets round the plant
    /** The streets' pad y at plan (u, v). */
    public int streetPad(Plan p, double u, double v) { float[] f = streetField(p); double gx = (u + p.sgHalf) / STREET_GRID, gy = (v + p.sgHalf) / STREET_GRID; int n = p.sgN;
        int x0 = Math.max(0, Math.min(n - 2, (int) Math.floor(gx))), y0 = Math.max(0, Math.min(n - 2, (int) Math.floor(gy))); double fx = Math.max(0, Math.min(1, gx - x0)), fy = Math.max(0, Math.min(1, gy - y0));
        double z = (f[x0 + y0 * n] * (1 - fx) + f[x0 + 1 + y0 * n] * fx) * (1 - fy) + (f[x0 + (y0 + 1) * n] * (1 - fx) + f[x0 + 1 + (y0 + 1) * n] * fx) * fy;
        double g = Roads.GRADE[Roads.Cls.ARTERIAL.ordinal()];
        if (p.blvdZ != null) { double d = Math.max(0, Math.abs(u) - BOULEVARD_M / 2); double bz = blvdZAt(p, Math.max(-p.blvdHalf, Math.min(p.blvdHalf, v))); z = Math.max(bz - g * d, Math.min(bz + g * d, z)); }
        // the road ends, farthest first so the nearest has the last word (measured: two ends 43 m apart in height 550 m apart cannot both hold 7 %; in
        // edge order the far one overrode the near one, a 5-block step at Sugarwood's avenue end)
        int np = p.portals.size(); Integer[] ord = new Integer[np]; for (int k = 0; k < np; k++) ord[k] = k; final double uu = u, vv = v; java.util.Arrays.sort(ord, (a, b) -> Double.compare(Math.hypot(uu - p.portals.get(b)[0], vv - p.portals.get(b)[1]), Math.hypot(uu - p.portals.get(a)[0], vv - p.portals.get(a)[1])));
        for (int k : ord) { double[] pt = p.portals.get(k); if (pt[2] == 0) continue; double rz = portalZ(p, k); if (Double.isNaN(rz)) continue; double d = Math.hypot(u - pt[0], v - pt[1]); z = Math.max(rz - g * d, Math.min(rz + g * d, z)); }
        return (int) Math.floor(Terrain.toYd(z)) + 1; }
    /** The boulevard's height (m) at v, with its ramps to the roads at its ends (blvdPad's metres). */
    double blvdZAt(Plan p, double v) { return blvdZAt(p, Double.NaN, v); }
    /** 0.28.2 at plan u too: within 20 m of an Interstate's slot (SLOTS: 40 m apart) the end ramp meets THAT road's end, elsewhere the
     *  highest (measured: two Interstates at one end of a large city's boulevard ended 4 blocks apart; one ramp could meet only one). */
    double blvdZAt(Plan p, double u, double v) { int k = Math.max(0, Math.min(p.blvdZ.length - 1, (int) Math.round((v + p.blvdHalf) / BLVD_STEP))); double z = p.blvdZ[k]; double g = Roads.GRADE[Roads.Cls.ARTERIAL.ordinal()];
        for (int e = 0; e < 2; e++) { double rz = roadEndZ(p, e); if (!Double.isNaN(u)) for (int q = 0; q < p.portals.size(); q++) { double[] pt = p.portals.get(q); if (pt[2] != 0 || (pt[3] > 0) != (e == 1) || Math.abs(u - pt[0]) > 20) continue; double pz = portalZ(p, q); if (!Double.isNaN(pz)) rz = pz; }
            if (Double.isNaN(rz)) continue; double d = Math.max(0, p.blvdHalf - (e == 0 ? -v : v)); z = rz >= p.blvdZ[e == 0 ? 0 : p.blvdZ.length - 1] ? Math.max(z, rz - g * d) : Math.min(z, rz + g * d); } return z; }
    /** The end height of the road at avenue portal k (laid if need be), NaN while the network builds. */
    double portalZ(Plan p, int k) { double[] pz = p.portalZ; if (pz == null || pz.length != p.portals.size()) { pz = new double[p.portals.size()]; Arrays.fill(pz, Double.NaN); p.portalZ = pz; } if (!Double.isNaN(pz[k])) return pz[k];
        Roads rd = w.roadsIfMade(); if (rd == null || Roads.building()) return Double.NaN; double[] pt = p.portals.get(k); double[] at = sphere(p, pt[0], pt[1]); double z = Double.NaN;
        for (Roads.Edge ed : rd.edges) { if (ed.a != p.site.id && ed.b != p.site.id) continue; double[] q = ed.a == p.site.id ? ed.pts[0] : ed.pts[ed.pts.length - 1]; if (Rivers.arc(q, at) * R > 30) continue; Roads.Route r = rd.route(ed.id); z = ed.a == p.site.id ? r.z[0] : r.z[r.n() - 1]; }
        if (Double.isNaN(z)) z = Double.NEGATIVE_INFINITY; pz[k] = z; return z == Double.NEGATIVE_INFINITY ? Double.NaN : z; }
    float[] streetField(Plan p) { float[] f = p.streetZ; if (f != null) return f; synchronized (p) { if (p.streetZ != null) return p.streetZ; long t0 = System.nanoTime();
            double half = p.reachM + PITCH_M; int n = (int) Math.ceil(2 * half / STREET_GRID) + 1; double[] g = new double[n * n];
            for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) { double[] hw = t.heightAndWater(sphere(p, -half + x * STREET_GRID, -half + y * STREET_GRID), R); g[x + y * n] = Double.isNaN(hw[1]) || hw[1] <= hw[0] ? hw[0] : hw[1] + 1; }
            int win = (int) Math.round(64 / STREET_GRID); double[] a = new double[n * n];
            for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) { double sm = 0; int c = 0; for (int q = Math.max(0, x - win); q <= Math.min(n - 1, x + win); q++) { sm += g[q + y * n]; c++; } a[x + y * n] = sm / c; }
            for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) { double sm = 0; int c = 0; for (int q = Math.max(0, y - win); q <= Math.min(n - 1, y + win); q++) { sm += a[x + q * n]; c++; } g[x + y * n] = sm / c; }
            double dz = Roads.GRADE[Roads.Cls.ARTERIAL.ordinal()] * STREET_GRID; int passes = 0; boolean ch = true;
            while (ch && passes < 200) { ch = false; passes++; for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) { int i = x + y * n; double z = g[i];
                    if (x > 0) z = Math.max(Math.min(z, g[i - 1] + dz), g[i - 1] - dz); if (y > 0) z = Math.max(Math.min(z, g[i - n] + dz), g[i - n] - dz); if (x < n - 1) z = Math.max(Math.min(z, g[i + 1] + dz), g[i + 1] - dz); if (y < n - 1) z = Math.max(Math.min(z, g[i + n] + dz), g[i + n] - dz);
                    if (Math.abs(z - g[i]) > 1e-6) { g[i] = z; ch = true; } } }
            f = new float[n * n]; for (int i = 0; i < n * n; i++) f[i] = (float) g[i]; p.sgN = n; p.sgHalf = half; p.streetMs = (System.nanoTime() - t0) / 1_000_000; p.streetPasses = passes; p.streetZ = f; return f; } }
    /** 0.27.8 is face column (x, z) in a megacity's built area (its built cells and one cell round them)? The section-line roads stop there (the streets carry the traffic, as in a town). */
    public boolean inCity(int f, int x, int z, double[] s) { if (!on) return false; for (Plan p : near(s)) { if (p.face != f) continue; double[] uv = p.uv(x + 0.5, z + 0.5); if (Math.abs(uv[0]) > p.reachM + PITCH_M || Math.abs(uv[1]) > p.reachM + PITCH_M) continue;
            int i = (int) Math.floor(uv[0] / PITCH_M + 0.5), j = (int) Math.floor(uv[1] / PITCH_M + 0.5); for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) { Cell c = p.cells.get(key(i + a, j + b)); if (c != null && c != Cell.FREE && c != Cell.WATER) return true; } } return false; }
    /** 0.27.8 is sphere point s on plan p's built cells (towers, parks, axis, plaza, boulevard), or within `grow` cells of one? */
    public boolean built(Plan p, double[] s, int grow) { double[] lc = w.cube.locate(s); if ((int) lc[0] != p.face) return false; double[] uv = p.uv(lc[1], lc[2]); int i = (int) Math.floor(uv[0] / PITCH_M + 0.5), j = (int) Math.floor(uv[1] / PITCH_M + 0.5);
        for (int a = -grow; a <= grow; a++) for (int b = -grow; b <= grow; b++) { Cell cl = p.cells.get(key(i + a, j + b)); if (cl != null && cl != Cell.FREE && cl != Cell.WATER) return true; }
        // 0.27.8 and the Interstates' approach past the boulevard's ends (their leads' inner 60 %, 100 m each side of the slots: CHOSEN), so no
        // other road crosses them at the city's door (measured: an arterial looped back over three Interstates 80 m past Riverland's boulevard end)
        if (grow > 0 && p.blvdY != null && Math.abs(uv[0]) < 180) { double out = Math.abs(uv[1]) - p.blvdHalf; if (out >= 0 && out < 0.6 * LEAD_M) return true; }
        return false; }

    // ------------------------------------------------------------ 0.27.0 into the world
    /** The plans of the megacities near sphere point s (within reach of their centre). */
    public List<Plan> near(double[] s) { List<Plan> l = new ArrayList<>(2); double cn = cosNear; if (cn == 0) cosNear = cn = Math.cos(NEAR_M / R);   // 0.28.2 a dot product, not acos, per city (measured: 157 cities by arc() cost 3.7 us a column)
        for (Sites.Site c : mega) if (s[0] * c.s[0] + s[1] * c.s[1] + s[2] * c.s[2] > cn) l.add(plan(c)); for (Sites.Site c : large) if (s[0] * c.s[0] + s[1] * c.s[1] + s[2] * c.s[2] > cn) l.add(plan(c)); return l; }
    double cosNear;
    static final double NEAR_M = 30000;   // CHOSEN: past the widest plan (about 8 km) plus the centre's move to dry ground (up to 10 km)
    /** The pad height (block y of the paving's top + 1, the y a player stands at) of a lot: the mean ground of its centre and four corners
     *  (CHOSEN), from the plain terrain. */
    public int pad(Plan p, Lot l) { int y = l.padY; if (y != Integer.MIN_VALUE) return y; double m = 0; for (int k = 0; k < 5; k++) { double du = k == 0 ? 0 : (k % 2 == 0 ? 1 : -1) * 0.8 * l.lotHalf, dv = k == 0 ? 0 : (k < 3 ? 1 : -1) * 0.8 * l.lotHalf; m += t.heightM(sphere(p, l.lu + du, l.lv + dv), R); }
        l.padY = y = (int) Math.floor(Terrain.toYd(m / 5)) + 1; return y; }
    /** The gate plaza's pad: the HQ's (the front door opens onto it level). */
    public int plazaPad(Plan p) { int y = p.plazaPadY; if (y == Integer.MIN_VALUE && !p.towers.isEmpty()) p.plazaPadY = y = pad(p, p.towers.get(0)); return y; }
    /** What a column of face f at block x, z is in a megacity: {pad y, kind (1 tower lot, 2 gate plaza)}, or null (not graded). */
    public int[] at(int f, int x, int z, double[] s) { if (!on) return null; for (Plan p : near(s)) { if (p.face != f) continue; double[] uv = p.uv(x + 0.5, z + 0.5); if (Math.abs(uv[0]) > p.reachM || Math.abs(uv[1]) > p.reachM) continue;
            int i = (int) Math.floor(uv[0] / PITCH_M + 0.5), j = (int) Math.floor(uv[1] / PITCH_M + 0.5);
            List<Lot> ls = p.byCell.get(key(i, j)); if (ls != null) for (Lot l : ls) if (Math.abs(uv[0] - l.lu) <= l.lotHalf && Math.abs(uv[1] - l.lv) <= l.lotHalf) return new int[]{pad(p, l), 1};
            if (p.blvdY != null && Math.abs(uv[0]) <= BOULEVARD_M / 2 && Math.abs(uv[1]) <= p.blvdHalf) return new int[]{blvdPad(p, uv[0], uv[1]), 3};   // 0.27.7 the boulevard
            if (p.site.kind == Sites.Kind.MEGACITY && Math.abs(uv[1]) <= BLOCK_M / 2 && uv[0] >= PITCH_M - BLOCK_M / 2 && uv[0] <= 2 * PITCH_M + BLOCK_M / 2) return new int[]{plazaPad(p), 2};
            if (onAvenue(p, uv[0], uv[1])) return new int[]{streetPad(p, uv[0], uv[1]), 4};   // 0.27.8 the avenues
            { Cell pc = p.cells.get(key(i, j)); if ((pc == Cell.PARK || pc == Cell.AXIS) && Math.abs(uv[0] - i * PITCH_M) <= BLOCK_M / 2 && Math.abs(uv[1] - j * PITCH_M) <= BLOCK_M / 2) return new int[]{streetPad(p, uv[0], uv[1]), 5};
                if (pc == Cell.FUSION && Math.abs(uv[0] - i * PITCH_M) <= BLOCK_M / 2 && Math.abs(uv[1] - j * PITCH_M) <= BLOCK_M / 2) return new int[]{streetPad(p, i * PITCH_M, j * PITCH_M), 6}; } }   // 0.28.0 the parks, level with the streets; 0.28.3 the fusion plant's superblocks, one level each   // 0.27.1 the gate plaza: both superblocks and the avenue between (measured: the gate stood on that avenue)
        return null; }
    /** Every tower block in blocks x0..x1-1, z0..z1-1 of face f (sCentre: the box's centre on the sphere): sink(x, y, z, block). */
    public interface Sink { void put(int x, int y, int z, String b); }
    public final java.util.concurrent.atomic.LongAdder chunkNs = new java.util.concurrent.atomic.LongAdder(), blocks = new java.util.concurrent.atomic.LongAdder();
    public void chunk(int f, int x0, int z0, int x1, int z1, double[] sCentre, Sink out) { if (!on) return; long t0 = System.nanoTime(); long n = 0;
        for (Plan p : near(sCentre)) { if (p.face != f) continue; double[] a = p.uv(x0, z0), b = p.uv(x1, z1); double u0 = Math.min(a[0], b[0]), u1 = Math.max(a[0], b[0]), v0 = Math.min(a[1], b[1]), v1 = Math.max(a[1], b[1]);
            if (u1 < -p.reachM || u0 > p.reachM || v1 < -p.reachM || v0 > p.reachM) continue;
            Set<Lot> seen = new HashSet<>(); for (int i = (int) Math.floor(u0 / PITCH_M + 0.5); i <= (int) Math.floor(u1 / PITCH_M + 0.5); i++) for (int j = (int) Math.floor(v0 / PITCH_M + 0.5); j <= (int) Math.floor(v1 / PITCH_M + 0.5); j++) { List<Lot> ls = p.byCell.get(key(i, j)); if (ls != null) seen.addAll(ls); }
            for (Lot l : seen) { Tower tw = l.tower(); int r = tw.reach(); if (l.u + r < u0 || l.u - r > u1 || l.v + r < v0 || l.v - r > v1) continue; int y0 = pad(p, l) - 1, top = tw.top();
                for (int x = x0; x < x1; x++) for (int z = z0; z < z1; z++) { double[] uv = p.uv(x + 0.5, z + 0.5); int lx = (int) Math.round(uv[0] - l.u), lz = (int) Math.round(uv[1] - l.v); if (Math.abs(lx) > r || Math.abs(lz) > r) continue;
                    for (int y = 0; y < top; y++) { String bk = tw.at(lx, y, lz); if (bk == null) continue; out.put(x, y0 + y, z, bk); n++; }
                    if (l.hq && lz == -tw.podiumHalf) for (int y = 0; y < Tower.PODIUM_FLOORS * Tower.FLOOR; y++) { String fb = Frontage.at(lx, y, tw.podiumHalf); if (fb != null) { out.put(x, y0 + y, z, fb); n++; frontBlocks.increment(); } } } }   // 0.27.3 the HQ's sign, logo and screens
            // 0.28.0 the megatalls' plazas (their lots round the podium) and the parks (planet/Parks)
            for (Lot l : seen) { if (l.height < Parks.MEGATALL_M) continue; Tower tw = l.tower(); int ph = tw.podiumHalf, lh = (int) Math.floor(l.lotHalf) - 1, y0 = pad(p, l) - 1; if (l.u + lh < u0 || l.u - lh > u1 || l.v + lh < v0 || l.v - lh > v1) continue;
                for (int x = x0; x < x1; x++) for (int z = z0; z < z1; z++) { double[] uv = p.uv(x + 0.5, z + 0.5); int lx = (int) Math.round(uv[0] - l.u), lz = (int) Math.round(uv[1] - l.v); if (Math.max(Math.abs(lx), Math.abs(lz)) > lh) continue;
                    for (int y = 0; y <= 13; y++) { String bk = Parks.plaza(lx, y, lz, ph, lh); if (bk != null) { out.put(x, y0 + y, z, bk); n++; Parks.PLAZA_BLOCKS.increment(); } } } }
            for (int x = x0; x < x1; x++) for (int z = z0; z < z1; z++) { double[] uv = p.uv(x + 0.5, z + 0.5); int ci = (int) Math.floor(uv[0] / PITCH_M + 0.5), cj = (int) Math.floor(uv[1] / PITCH_M + 0.5); Cell pc = p.cells.get(key(ci, cj)); if (pc != Cell.PARK && pc != Cell.AXIS) continue;
                int lx = (int) Math.round(uv[0] - ci * PITCH_M), lz = (int) Math.round(uv[1] - cj * PITCH_M), half = (int) Math.floor(BLOCK_M / 2); if (Math.max(Math.abs(lx), Math.abs(lz)) > half) continue;
                int y0 = streetPad(p, uv[0], uv[1]) - 1; long sd = seed ^ key(ci, cj) * 0x9E3779B97F4A7C15L; boolean owned = p.ownedParks.contains(key(ci, cj));
                for (int y = 0; y <= 4; y++) { String bk = Parks.park(lx, y, lz, half, sd, owned); if (bk != null) { out.put(x, y0 + y, z, bk); n++; Parks.PARK_BLOCKS.increment(); } } }
            // 0.28.3 the fusion plant's units, six a superblock, numbered in the order the superblocks were taken
            for (int fk = 0; fk < p.fusionCells.size(); fk++) { long k = p.fusionCells.get(fk); int ci = (int) (k >> 32), cj = (int) (long) k; double cu = ci * PITCH_M, cv = cj * PITCH_M; if (cu + BLOCK_M / 2 < u0 || cu - BLOCK_M / 2 > u1 || cv + BLOCK_M / 2 < v0 || cv - BLOCK_M / 2 > v1) continue;
                int y0 = streetPad(p, cu, cv) - 1;
                for (int x = x0; x < x1; x++) for (int z = z0; z < z1; z++) { double[] uv = p.uv(x + 0.5, z + 0.5); int lx = (int) Math.floor(uv[0] - cu + 105), lz = (int) Math.floor(uv[1] - cv + 105); if (lx < 0 || lz < 0 || lx >= 3 * FusionPlant.PLOT_X || lz >= 2 * FusionPlant.PLOT_Z) continue;
                    int col = lx / FusionPlant.PLOT_X, row = lz / FusionPlant.PLOT_Z, unit = fk * FusionPlant.PER_BLOCK + row * 3 + col; if (unit >= p.fusionUnits) continue;
                    int px = lx - col * FusionPlant.PLOT_X - FusionPlant.PLOT_X / 2, pz = lz - row * FusionPlant.PLOT_Z;
                    for (int y = 0; y <= FusionPlant.WALL + FusionPlant.VAULT_R + 1; y++) { String bk = FusionPlant.at(px, y, pz); if (bk != null) { out.put(x, y0 + y, z, bk); n++; FusionPlant.BLOCKS.increment(); } } } }
            // 0.28.1 the sky bridges (planet/SkyBridges)
            for (SkyBridges.Bridge br : bridges(p)) { double bs0 = br.s0(), bs1 = br.s1(), ac = br.across(); boolean au = br.alongU(); double bu0 = au ? bs0 : ac - SkyBridges.HALF_W, bu1 = au ? bs1 : ac + SkyBridges.HALF_W, bv0 = au ? ac - SkyBridges.HALF_W : bs0, bv1 = au ? ac + SkyBridges.HALF_W : bs1;
                if (bu1 < u0 || bu0 > u1 || bv1 < v0 || bv0 > v1) continue;
                for (int x = x0; x < x1; x++) for (int z = z0; z < z1; z++) { double[] uv = p.uv(x + 0.5, z + 0.5); double sa = au ? uv[0] : uv[1], cr = (au ? uv[1] : uv[0]) - ac;
                    for (int dy = -2; dy <= SkyBridges.STOREYS * Tower.FLOOR; dy++) { String bk = SkyBridges.at(br, sa, cr, dy); if (bk != null) { out.put(x, br.deckY() + dy, z, bk); n++; SkyBridges.BLOCKS.increment(); } } } }
            // 0.27.1 the great gate at the gate plaza's centre (planet/GreatGate), standing on the plaza's pad
            if (!p.towers.isEmpty() && p.site.kind == Sites.Kind.MEGACITY) { double gu = GATE_U, gv = 0; if (gu + GreatGate.X1 >= u0 && gu + GreatGate.X0 <= u1 && gv + GreatGate.Z1 >= v0 && gv + GreatGate.Z0 <= v1) { int py = plazaPad(p);
                for (int x = x0; x < x1; x++) for (int z = z0; z < z1; z++) { double[] uv = p.uv(x + 0.5, z + 0.5); int gx = (int) Math.round(uv[0] - gu), gz = (int) Math.round(uv[1] - gv); if (gx < GreatGate.X0 || gx > GreatGate.X1 || gz < GreatGate.Z0 || gz > GreatGate.Z1) continue;
                    for (int y = 0; y <= GreatGate.TOP; y++) { String bk = GreatGate.at(gx, y, gz); if (bk == null) continue; out.put(x, py + y, z, bk); n++; gateBlocks.increment(); } } } } }
        chunkNs.add(System.nanoTime() - t0); blocks.add(n); }
    /** 0.28.1 plan p's sky bridges, made once. */
    public List<SkyBridges.Bridge> bridges(Plan p) { List<SkyBridges.Bridge> b = p.bridges; if (b != null) return b; synchronized (p) { if (p.bridges == null) p.bridges = SkyBridges.plan(this, p); return p.bridges; } }
    /** 0.27.1 the great gate's centre on the plan: the middle of the two gate-plaza superblocks (1, 0) and (2, 0). */
    public static final double GATE_U = 1.5 * PITCH_M; public final java.util.concurrent.atomic.LongAdder gateBlocks = new java.util.concurrent.atomic.LongAdder(), frontBlocks = new java.util.concurrent.atomic.LongAdder();
    /** 0.27.0 THE SPAWN (owner, 2026-11-04: "one of the megacities to act as the player spawn location. just spawn me close to the front
     *  door of the cobble and sons building"): the megacity on face 0 (the overworld) nearest the old coast spawn; the spot SPAWN_OUT blocks
     *  out from the HQ's front door, on the gate plaza side. {x, y (feet), z, site id}, or null (no megacity on face 0). */
    public static final int SPAWN_OUT = 4;
    public int[] spawn(double[] near) { Plan best = null; double bd = 1e18; for (Sites.Site c : mega) { Plan p = plan(c); if (p.face != 0 || p.towers.isEmpty()) continue; double d = Rivers.arc(near, c.s); if (d < bd) { bd = d; best = p; } }
        if (best == null) return null; Lot hq = best.towers.get(0); int[] fd = hq.tower().frontDoor(); double[] xz = best.xz(hq.u + fd[0], hq.v + fd[1] - SPAWN_OUT);
        return new int[]{(int) Math.floor(xz[0]), pad(best, hq), (int) Math.floor(xz[1]), best.site.id}; }

    /** The sphere point of plan (u, v) metres. */
    double[] sphere(Plan p, double u, double v) { double[] xz = p.xz(u, v); double[] q = w.cube.spherePoint(p.face, xz[0], xz[1]); if (q != null) return q; double[] c = st.exact(p.site); return c != null ? c : p.site.s; }
}
