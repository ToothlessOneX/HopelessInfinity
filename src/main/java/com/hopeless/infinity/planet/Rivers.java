package com.hopeless.infinity.planet;

import java.util.*;
import java.util.stream.IntStream;

/**
 * 0.21.0 RIVERS AND LAKES (owner, 2026-11-03: "we also need to add rivers before we do this ... so town placement works correctly"). Pure
 * (tools/RiversTest). For a planet with seas (Surface.EARTH).
 *
 *  DRAINAGE   a planet-wide grid of N x N cells a cube face (seed 1: ~22 km cells) of the coast field's height (the ground without the short
 *             detail); low basins filled to their spill point and every cell given its way to the sea in one pass of Priority-Flood with an
 *             epsilon (Barnes, Lehman & Mulla 2014, Computers & Geosciences 62): the cell that floods a cell is the one it drains to.
 *  WATER      runoff per cell from the biome model's dryness rank (Terrain.wetRank: arid land ~0, the wettest the most, as rank^2: CHOSEN shape),
 *             scaled so the land's mean is RUNOFF_M = 0.3 m a year (Earth's rivers carry ~40,000 km3 a year off ~1.3e8 km2 of ice-free land:
 *             Oki & Kanae 2006, Science 313); flow adds up downstream: Q (m3/s) per cell.
 *  LAKES      the deepest-filled cells, LAKE_SHARE (3.7 %, Verpoorter et al. 2014) of the land, at least LAKE_MIN_M = 2 m deep, are lakes at the fill level; in arid land (dry rank under Terrain.ARID) a lake is a dry
 *             salt flat and the end of its basin's water (an endorheic basin: the water leaves by evaporation).
 *  CHANNELS   cells with Q >= Q_MIN = 10 m3/s (about the runoff of 1,000 km2; smaller streams are not drawn yet) carry a river to the cell they drain to. Bankfull width 7.2 Q^0.5 m and depth 0.27 Q^0.39 m
 *             (Moody & Troutman 2002, Earth Surf. Process. Landforms 27, the global law used by Andreadis et al. 2013); meanders of wavelength
 *             12.7 widths (the global median of Frasson et al. 2019, GRL 46) and amplitude 0.17 of it (sinuosity ~1.3: Leopold & Wolman 1960
 *             measured 1.53, Richards 1976 1.25-1.38), with bends of 5 km and 1.5 km on top (CHOSEN) so a river is never a straight 22 km line.
 *  IN THE GROUND  the water surface follows the filled height (falling to the sea); the channel is cut DEPTH below it; a valley floor 1 m over
 *             the water is cut round it out to max(4 W, 150 m), blending back to the land over as far again (CHOSEN), so rivers run in valleys.
 */
public final class Rivers {
    public static final int N = 512;
    public double lakeCut; public static final double LAKE_SHARE = 0.037;
    public static final double RUNOFF_M = 0.3, LAKE_MIN_M = 2, Q_MIN = 10, W_A = 7.2, W_B = 0.5, D_A = 0.27, D_B = 0.39, MEANDER_L = 12.7, MEANDER_A = 0.17, EPS = 0.01, VALLEY_K = 2.1, VALLEY_MAX = 6000;   // VALLEY_K: smoothstep's steepest slope 1.5 dh / Vb = tan 35 deg (CHOSEN, GAME)
    public final CubeSphere cube; public final double R; final Terrain t; final long seed;
    public final float[] h, fill, q; public final int[] down; public final byte[] kind;   // kind: 0 land, 1 sea, 2 lake, 3 dry lake (salt flat), 4 river
    public final double[] dist;   // metres along the rivers to where the water ends (the sea or a dry lake)
    public long buildMs; public int riverCells, lakeCells, dryCells; public double landRunoffKm3;
    final int[] nb;   // 8 neighbours each
    public static final class Seg { public final int a, b; public final double[] pa, pb; public final double qa, la, lb, da, db, len; Seg(int a, int b, double[] pa, double[] pb, double qa, double la, double lb, double da, double db, double len) { this.a = a; this.b = b; this.pa = pa; this.pb = pb; this.qa = qa; this.la = la; this.lb = lb; this.da = da; this.db = db; this.len = len; } }
    public final List<Seg> segs = new ArrayList<>(); final Map<Long, int[]> buckets = new HashMap<>(); static final double BUCKET = 0.004;

    public static int idx(int f, int i, int j) { return (f * N + j) * N + i; }
    public double[] centre(int c) { int f = c / (N * N), r = c % (N * N), j = r / N, i = r % N; return CubeSphere.sphere(CubeSphere.cube(f, -1 + (i + 0.5) * 2.0 / N, -1 + (j + 0.5) * 2.0 / N)); }

    public Rivers(Terrain t, CubeSphere cube, long seed) {
        long t0 = System.nanoTime(); this.t = t; this.cube = cube; this.R = cube.R; this.seed = seed; int n = 6 * N * N;
        h = new float[n]; fill = new float[n]; q = new float[n]; down = new int[n]; kind = new byte[n]; dist = new double[n]; nb = new int[n * 8];
        IntStream.range(0, n).parallel().forEach(c -> { double[] s = centre(c); h[c] = (float) t.coastM(s, R); });
        IntStream.range(0, n).parallel().forEach(this::neighbours);
        // Priority-Flood + epsilon from the sea
        boolean[] done = new boolean[n]; PriorityQueue<long[]> pq = new PriorityQueue<>((x, y) -> Double.compare(Double.longBitsToDouble(x[0]), Double.longBitsToDouble(y[0]))); int[] order = new int[n]; int no = 0;
        Arrays.fill(down, -1);
        for (int c = 0; c < n; c++) if (h[c] < 0) { done[c] = true; fill[c] = h[c]; kind[c] = 1; order[no++] = c; }
        for (int c = 0; c < n; c++) if (kind[c] == 1) for (int k = 0; k < 8; k++) { int m = nb[c * 8 + k]; if (m >= 0 && !done[m]) { done[m] = true; fill[m] = Math.max(h[m], 0 + (float) EPS); down[m] = c; pq.add(new long[]{Double.doubleToLongBits(fill[m]), m}); } }
        while (!pq.isEmpty()) { long[] e = pq.poll(); int c = (int) e[1]; order[no++] = c;
            for (int k = 0; k < 8; k++) { int m = nb[c * 8 + k]; if (m < 0 || done[m]) continue; done[m] = true; fill[m] = Math.max(h[m], fill[c] + (float) EPS); down[m] = c; pq.add(new long[]{Double.doubleToLongBits(fill[m]), m}); } }
        // lakes, dry lakes (endorheic in arid land)
        // the lake depth cut: the filled depth that leaves LAKE_SHARE of the land under lakes (lakes cover 3.7 % of the non-glaciated land: Verpoorter et al. 2014, GRL 41)
        { int m = 0; float[] dd = new float[n]; for (int c = 0; c < n; c++) if (kind[c] != 1) dd[m++] = fill[c] - h[c]; float[] l = Arrays.copyOf(dd, m); Arrays.sort(l); lakeCut = Math.max(LAKE_MIN_M, l[(int) ((1 - LAKE_SHARE) * (m - 1))]); }
        double cellArea = 4 * Math.PI * R * R / n; double[] run = new double[n]; double sumR = 0; int land = 0;
        for (int c = 0; c < n; c++) { if (kind[c] == 1) continue; land++; double[] s = centre(c); double rank = t.wetRank(t.wetness(s)); run[c] = rank * rank; sumR += run[c];
            if (fill[c] - h[c] > lakeCut) { boolean arid = rank < Terrain.ARID / Terrain.EARTH_WARM && t.temperatureC(s, Math.max(0, h[c])) >= 5; kind[c] = (byte) (arid ? 3 : 2); } }
        double scale = sumR > 0 ? RUNOFF_M * land / sumR : 0;   // m a year per unit rank^2
        for (int c = 0; c < n; c++) if (kind[c] != 1) q[c] = (float) (run[c] * scale * cellArea / 3.156e7);   // m3/s
        // flow downstream, upstream cells first (the reverse of the flood order); a dry lake keeps its water
        for (int k = no - 1; k >= 0; k--) { int c = order[k]; if (kind[c] == 1 || kind[c] == 3) continue; int d = down[c]; if (d >= 0) q[d] += q[c]; }
        for (int k = 0; k < no; k++) { int c = order[k]; int d = down[c]; if (kind[c] == 1) { dist[c] = 0; continue; } if (kind[c] == 3 || d < 0) { dist[c] = 0; continue; } dist[c] = dist[d] + arc(centre(c), centre(d)) * R; }
        for (int c = 0; c < n; c++) { if (kind[c] == 0 && q[c] >= Q_MIN) { kind[c] = 4; riverCells++; } if (kind[c] == 2) lakeCells++; if (kind[c] == 3) dryCells++; }
        landRunoffKm3 = sumR * scale * cellArea / 1e9;
        // channel segments and their buckets
        for (int c = 0; c < n; c++) { if (kind[c] != 4) continue; int d = down[c]; if (d < 0) continue;
            double[] pa = centre(c), pb = centre(d); Seg sg = new Seg(c, d, pa, pb, q[c], Math.max(0, fill[c]), kind[d] == 1 ? 0 : Math.max(0, fill[d]), dist[c], dist[d], arc(pa, pb) * R); int id = segs.size(); segs.add(sg);
            double reach = (Math.max(4 * width(sg.qa), 150) + VALLEY_MAX + 0.12 * sg.len + 2.5 * width(sg.qa)) / R; addBuckets(pa, pb, reach, id); }
        for (int id = 0; id < segs.size(); id++) ups.computeIfAbsent(segs.get(id).b, k -> new ArrayList<>()).add(id);   // 0.29.8 the segments that flow into each cell
        buildMs = (System.nanoTime() - t0) / 1_000_000; }
    void addBuckets(double[] pa, double[] pb, double reach, int id) {
        double[] lo = new double[3], hi = new double[3]; for (int k = 0; k < 3; k++) { lo[k] = Math.min(pa[k], pb[k]) - reach; hi[k] = Math.max(pa[k], pb[k]) + reach; }
        Map<Long, List<Integer>> tmp = new HashMap<>();
        for (int x = (int) Math.floor(lo[0] / BUCKET); x <= (int) Math.floor(hi[0] / BUCKET); x++) for (int y = (int) Math.floor(lo[1] / BUCKET); y <= (int) Math.floor(hi[1] / BUCKET); y++) for (int z = (int) Math.floor(lo[2] / BUCKET); z <= (int) Math.floor(hi[2] / BUCKET); z++) {
            long key = key(x, y, z); int[] old = buckets.get(key); int[] nw = old == null ? new int[]{id} : Arrays.copyOf(old, old.length + 1); nw[nw.length - 1] = id; buckets.put(key, nw); } }
    static long key(int x, int y, int z) { return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF); }
    void neighbours(int c) {
        int f = c / (N * N), r = c % (N * N), j = r / N, i = r % N; int k = 0;
        for (int dj = -1; dj <= 1; dj++) for (int di = -1; di <= 1; di++) { if (di == 0 && dj == 0) continue; int ii = i + di, jj = j + dj;
            if (ii >= 0 && jj >= 0 && ii < N && jj < N) nb[c * 8 + k++] = idx(f, ii, jj);
            else { double[] o = centre(c), back = centre(idx(f, Math.max(0, Math.min(N - 1, i - di)), Math.max(0, Math.min(N - 1, j - dj))));   // across a face edge: the same step on the sphere as the step back into this face
                double[] pt = {2 * o[0] - back[0], 2 * o[1] - back[1], 2 * o[2] - back[2]}; double l = Math.sqrt(pt[0] * pt[0] + pt[1] * pt[1] + pt[2] * pt[2]); pt[0] /= l; pt[1] /= l; pt[2] /= l;
                int m = cellOf(pt); nb[c * 8 + k++] = m == c || arc(o, centre(m)) > 2.5 * Math.PI / 2 / N ? -1 : m; } } }
    static double arc(double[] a, double[] b) { return Math.acos(Math.max(-1, Math.min(1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2]))); }
    public static double width(double qm3) { return W_A * Math.pow(qm3, W_B); }
    public static double depth(double qm3) { return D_A * Math.pow(qm3, D_B); }

    /** What the rivers do at the sphere point s: {ground cap (m: the ground may not stand above it; +inf none), water level (m, NaN none), kind
     *  0 none / 1 channel / 2 lake / 3 salt flat}. h0: the ground height there before rivers. */
    public double[] at(double[] s, double h0) {
        double cap = Double.POSITIVE_INFINITY, water = Double.NaN, floor = Double.NEGATIVE_INFINITY; int kd = 0;
        // lakes and salt flats: the coarse cell's own
        int c = cellOf(s);   // the cell's own lake, or a neighbour's whose level this low ground lies under (so a lake's shore follows the ground, not the grid)
        for (int k = -1; k < 8; k++) { int m = k < 0 ? c : nb[c * 8 + k]; if (m < 0 || (kind[m] != 2 && kind[m] != 3)) continue; double lvl = fill[m]; if (h0 >= lvl) continue;
            if (kind[m] == 2) { if (Double.isNaN(water) || lvl > water) { water = lvl; kd = 2; } } else if (kd != 2) { cap = Math.min(cap, lvl); kd = 3; } }
        int[] ids = buckets.get(key((int) Math.floor(s[0] / BUCKET), (int) Math.floor(s[1] / BUCKET), (int) Math.floor(s[2] / BUCKET)));
        if (ids != null) for (int id : ids) { Seg g = segs.get(id); double[] e = local(g, s); if (e == null) continue;
            double tt = e[0], d = e[1], W = width(g.qa), Dp = depth(g.qa), lin = g.la + (g.lb - g.la) * tt, D = g.da + (g.db - g.da) * tt;
            double off = Math.sin(Math.PI * tt) * g.len * (0.08 * Math.sin(2 * Math.PI * tt + phase(g.a, 1)) + 0.03 * Math.sin(6 * Math.PI * tt + phase(g.a, 2))) + ends(tt) * MEANDER_A * MEANDER_L * W * Math.sin(2 * Math.PI * D / (MEANDER_L * W) + phase(g.a, 3));   // every bend goes to 0 at the segment's ends, so the channel meets the next segment's
            double r = Math.abs(d - off), V = Math.max(4 * W, 150), Vb = Math.max(V, Math.min(VALLEY_MAX, VALLEY_K * (h0 - (lin + 1))));   // the valley side: wider where the ground stands higher over the river (walls about 35 deg, CHOSEN); 0.29.8 its width from the coarse level, so the level profile is only read where the valley reaches
            if (r >= V + Vb && r >= W / 2 + BANK_M + TRANS_M) continue; double lvl = r < W / 2 + BANK_M ? local(g, id, tt) : lin;   // 0.29.8 the level profile in the channel and on its banks; the valley floor keeps the coarse level (it stands at or over the banks: the channel lies in it)
            if (r < W / 2) { double bed = lvl + 1 - (Dp + 1) * (1 - Math.pow(2 * r / W, 2)); cap = Math.min(cap, bed); if (Double.isNaN(water) || lvl > water) water = lvl; kd = 1;   /* the bed rises to the bank top (water + 1 m) at the channel's edge: no step */
                if (h0 < bed && bed - h0 <= FILL_MAX_M) floor = Math.max(floor, bed); }   // 0.29.8 a dip under the bed (between the profile's samples) is filled up to it
            else { double bank = lvl + 1; if (r < W / 2 + BANK_M && h0 < bank && bank - h0 <= FILL_MAX_M) floor = Math.max(floor, bank);   // 0.29.8 and a low bank is raised to the bank top, so the water stays in
                double vbank = r < W / 2 + BANK_M ? bank : lin + 1;
                if (r >= W / 2 + BANK_M && r < W / 2 + BANK_M + TRANS_M && profilesOn) { double rise = level(id, tt) + 1 + (r - W / 2 - BANK_M) / 2; vbank = Math.min(vbank, rise); }   // 0.29.8 from the banks the valley floor climbs 1 in 2 to the coarse level (measured: a sheer wall where the profile cut the channel under the coarse valley floor)
                if (r < V + Vb) { double k = r < V ? 0 : (r - V) / Vb, blend = vbank + (h0 - vbank) * (k * k * (3 - 2 * k)); cap = Math.min(cap, r < W / 2 + BANK_M ? bank : Math.max(vbank, blend)); } } }   /* the banks cut to the bank top; past them the slope, then the valley */
        return new double[]{cap, water, kd, floor}; }
    // ---- 0.29.8 THE WATER LEVEL FROM THE GROUND THE RIVER RUNS ON (Overlord, 2026-11-08: "the water is 20 blocks above the terrain in some cases and
    //      that is flooding towns"). The level was the straight line between the filled heights of the two coarse cells (about 22 km apart);
    //      the land between them dips under that line (tools/RiverWalk: 38 % of the channel length near the spawn more than 2 m over the land,
    //      the worst 46 m). Now each segment has a level profile from the ground along its own channel (the meandered centre line, every
    //      PROF_STEP_M): the level never rises downstream, and never stands higher than LEVEL_UNDER_M under the ground there (the river cuts
    //      its channel down to it: Rivers.at's cap). A segment starts no higher than the ends of the segments that flow into it, so the water
    //      never steps up at a junction; that chain is followed UP_DEPTH segments up (past that, the coarse level and the ground start it). Between
    //      the samples, a dip under the bed is filled to it and a low bank raised to the bank top (both up to FILL_MAX_M), so the water stays in. ----
    public static final double PROF_STEP_M = 300, LEVEL_UNDER_M = 0.5, BANK_M = 4, FILL_MAX_M = 4, TRANS_M = 60; public static final int UP_DEPTH = 2;
    final Map<Integer, List<Integer>> ups = new HashMap<>(); final java.util.concurrent.ConcurrentHashMap<Integer, float[]> prof = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.concurrent.atomic.LongAdder PROFILES = new java.util.concurrent.atomic.LongAdder(), PROFILE_NS = new java.util.concurrent.atomic.LongAdder();
    /** 0.29.8 the level at t held under both banks' ground there (the profile's samples are PROF_STEP_M apart: between them a bank can dip
     *  under the level; measured: 0.7 % of the channel near the spawn spilled, the worst 8.8 m). Two ground samples a call, in the channel and
     *  on its banks only. */
    double local(Seg g, int id, double tt) { double lv = level(id, tt); if (!profilesOn) return lv; double side = width(g.qa) / 2 + BANK_M;
        double b = Math.min(t.groundBeforeRivers(channelPoint(g, tt, side), R), t.groundBeforeRivers(channelPoint(g, tt, -side), R)) - LEVEL_UNDER_M; if (b < lv) { LOCAL_LOWER.increment(); lv = Math.max(kind[g.b] == 1 ? 0 : -1e9, Math.max(0, b)); } return lv; }
    public static final java.util.concurrent.atomic.LongAdder LOCAL_LOWER = new java.util.concurrent.atomic.LongAdder();
    /** The level (m) on segment id at t (0..1 along it). */
    public static volatile boolean profilesOn = System.getProperty("hinf.noRiverProfiles") == null;   /* A/B probe only; on by default */
    public double level(int id, double tt) { if (!profilesOn) { Seg g = segs.get(id); return g.la + (g.lb - g.la) * Math.max(0, Math.min(1, tt)); } float[] L = profile(id, UP_DEPTH); double x = Math.max(0, Math.min(1, tt)) * (L.length - 1); int i = Math.min(L.length - 2, (int) Math.floor(x)); return L[i] + (L[i + 1] - L[i]) * (x - i); }
    float[] profile(int id, int depth) { float[] L = prof.get(id); if (L != null) return L; Seg g = segs.get(id); double start = g.la;
        if (depth > 0) { List<Integer> in = ups.get(g.a); if (in != null) for (int u : in) { float[] lu = profile(u, depth - 1); start = Math.min(start, lu[lu.length - 1]); } }
        long t0 = System.nanoTime(); int n = Math.max(2, (int) Math.ceil(g.len / PROF_STEP_M) + 1); L = new float[n]; double prev = start; boolean toSea = kind[g.b] == 1;
        double side = width(g.qa) / 2 + BANK_M; for (int k = 0; k < n; k++) { double tt = k / (double) (n - 1); double h0 = Math.min(t.groundBeforeRivers(channelPoint(g, tt, 0), R), Math.min(t.groundBeforeRivers(channelPoint(g, tt, side), R), t.groundBeforeRivers(channelPoint(g, tt, -side), R))), lin = g.la + (g.lb - g.la) * tt;   /* the lowest of the centre and both banks (measured: a channel along a hillside had a bank 21 m under the centre) */
            double v = Math.min(prev, Math.min(lin, h0 - LEVEL_UNDER_M)); if (toSea || v < 0) v = Math.max(v, 0); L[k] = (float) v; prev = v; }   // never under the sea's level
        PROFILES.increment(); PROFILE_NS.add(System.nanoTime() - t0); float[] old = prof.putIfAbsent(id, L); return old != null ? old : L; }
    /** The channel's centre at t (plus extra metres to one side): the segment's line moved sideways by its bends and meanders (as at() places it). */
    public double[] channelPoint(Seg g, double tt, double extra) { double W = width(g.qa), D = g.da + (g.db - g.da) * tt;
        double off = Math.sin(Math.PI * tt) * g.len * (0.08 * Math.sin(2 * Math.PI * tt + phase(g.a, 1)) + 0.03 * Math.sin(6 * Math.PI * tt + phase(g.a, 2))) + ends(tt) * MEANDER_A * MEANDER_L * W * Math.sin(2 * Math.PI * D / (MEANDER_L * W) + phase(g.a, 3));
        double[] c = {g.pa[0] + (g.pb[0] - g.pa[0]) * tt, g.pa[1] + (g.pb[1] - g.pa[1]) * tt, g.pa[2] + (g.pb[2] - g.pa[2]) * tt}; double cl = Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]); c[0] /= cl; c[1] /= cl; c[2] /= cl;
        double[] ab = {g.pb[0] - g.pa[0], g.pb[1] - g.pa[1], g.pb[2] - g.pa[2]}, n = {ab[1] * c[2] - ab[2] * c[1], ab[2] * c[0] - ab[0] * c[2], ab[0] * c[1] - ab[1] * c[0]}; double nl = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]); if (nl < 1e-18) return c;
        double a = (off + extra) / R; double[] q = {c[0] + n[0] / nl * a, c[1] + n[1] / nl * a, c[2] + n[2] / nl * a}; double ql = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]); return new double[]{q[0] / ql, q[1] / ql, q[2] / ql}; }
    /** s in the segment's frame: {t along it 0..1 (null past its ends by more than the valley), lateral metres (signed)}. */
    double[] local(Seg g, double[] s) {
        double[] ab = {g.pb[0] - g.pa[0], g.pb[1] - g.pa[1], g.pb[2] - g.pa[2]}, as = {s[0] - g.pa[0], s[1] - g.pa[1], s[2] - g.pa[2]}; double L2 = ab[0] * ab[0] + ab[1] * ab[1] + ab[2] * ab[2]; if (L2 < 1e-24) return null;
        double tt = (as[0] * ab[0] + as[1] * ab[1] + as[2] * ab[2]) / L2;
        if (tt < 0 || tt > 1) { double[] e = tt < 0 ? g.pa : g.pb; return new double[]{tt < 0 ? 0 : 1, arc(e, s) * R}; }   // past an end: the distance to the end point (all bends are 0 there), so the valley closes round the end without a cut
        double[] cr = {ab[1] * s[2] - ab[2] * s[1], ab[2] * s[0] - ab[0] * s[2], ab[0] * s[1] - ab[1] * s[0]}; double cl = Math.sqrt(cr[0] * cr[0] + cr[1] * cr[1] + cr[2] * cr[2]); if (cl < 1e-18) return null;
        double[] p = {g.pa[0] + ab[0] * tt - s[0], g.pa[1] + ab[1] * tt - s[1], g.pa[2] + ab[2] * tt - s[2]}; double side = -(p[0] * cr[0] + p[1] * cr[1] + p[2] * cr[2]) / cl;
        return new double[]{tt, side * R}; }
    /** 1 along the segment, easing to 0 over its first and last 15 %. */
    static double ends(double t) { double k = Math.min(1, Math.min(t, 1 - t) / 0.15); return k * k * (3 - 2 * k); }
    double phase(int c, int k) { return 2 * Math.PI * MoonTerrain.u(MoonTerrain.hash(seed, 77, c, k, 0), 0); }
    /** The drainage cell a sphere point is in. */
    public int cellOf(double[] s) { double[] loc = cube.locate(s); int f = (int) loc[0], i = (int) Math.floor((cube.u(f, loc[1]) + 1) / 2 * N), j = (int) Math.floor((cube.v(loc[2]) + 1) / 2 * N); return idx(f, Math.max(0, Math.min(N - 1, i)), Math.max(0, Math.min(N - 1, j))); }
}
