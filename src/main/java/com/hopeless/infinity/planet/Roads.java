package com.hopeless.infinity.planet;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;

/**
 * 0.23.0 ROADS (stage 2 of the settlements plan; owner, 2026-11-03: "local roads, rural roads (mixed between dirt and pavement), highways ...
 * follow us regulations for every single thing"). Pure Java (tools/RoadsTest, tools/RoadView).
 *
 * The network (made once a world, after the sites):
 *  - Interstates join the metro cores (Sites LARGE_CITY, MEGACITY), arterials (US and state highways) join every place of 5,000 or more,
 *    collectors join every place to its neighbours: each a relative neighbourhood graph of its places (an edge stays when no third place is
 *    nearer to both ends), on the same continent, routed over the drainage grid (Rivers, 22 km cells; not across sea or lakes).
 *  - Measured against FHWA Highway Statistics 2023, table HM-20, per US land (Sites.US_LAND_KM2) of habitable land: Interstate 48,415 mi;
 *    other freeways, expressways and principal arterials 176,104 mi; minor arterials and collectors 1,067,192 mi (most of these are in-town
 *    and county roads; the collector edges here are only the roads between places); rural local roads 2,015,981 mi.
 *  - Rural local roads: the US Public Land Survey's section lines, a road every mile (1,609.344 m) on the face's block grid, on farm land
 *    (habitable cells, outside the cities). Surfaces: FHWA HM-12 (2018): rural local roads 1,153,414 of 2,026,535 mi unpaved (56.9 %);
 *    unpaved split half gravel, half dirt (CHOSEN).
 * Each road is laid when first needed (a chunk near it): a path search on a 400 m lattice in a 6 km corridor round its coarse route (cost:
 * length, grades over the class's limit, water to bridge), simplified (Douglas-Peucker), then 0.23.2 stored as OpenDRIVE-style records
 * (Alignment): straights and spiral-arc-spiral curves at AASHTO radii and spiral lengths; the vertical alignment solved as a quadratic
 * programme (Profile: the ground smoothed over 120 m as target; held over water and over Interstates, 16.5 ft = 5.03 m clearance + deck:
 * 7 m, the plan's Interstate standard; the class's grade; AASHTO K) and stored as parabolic elevation records; sampled every 8 m from
 * the records. Bridges where the road stands 6 m or more over the ground or over water;
 * tunnels where the ground stands 18 m or more over it (CHOSEN).
 * Grades: Interstate 6 % (the Interstate standards), arterial 7 %, collector 9 %, local 12 % (CHOSEN inside AASHTO's ranges, to be
 * checked against the Green Book tables). Lanes 12 ft = 4 blocks (the plan's default). Markings (MUTCD 2023): yellow between opposite
 * directions, white lane and right edge lines, broken lines 10 ft painted, 30 ft gap (3 m / 9 m).
 */
public final class Roads {
    public enum Cls { INTERSTATE, ARTERIAL, COLLECTOR, LOCAL, RAMP, RAIL } /* 0.29.0 RAIL: freight main lines (planet/Rail notes) */   // 0.23.1 RAMP: interchange ramps and collector-distributor roads (Interchanges)
    public static final double[] GRADE = {0.06, 0.07, 0.09, 0.12, 0.06, 0.015};   // 0.29.0 rail: 1.5 % (STATED, Amtrak Specification 63 s. 5.1.2: main line grades not over 1.500 % compensated)
    public static final double US_INTERSTATE_MI = 29109 + 19306, US_ARTERIAL_MI = 6651 + 12183 + 91313 + 65957, US_COLLECTOR_MI = 134266 + 111802 + 406841 + 131774 + 259964 + 22545,
        US_RURAL_LOCAL_MI = 2015981, US_CLASS1_RAIL_MI = 92000, UNPAVED = 1153414.0 / 2026535, MILE = 1609.344, CORRIDOR_M = 6000, STEP_M = 400, SAMPLE_M = 8, OVERPASS_M = 7, BRIDGE_M = 6, TUNNEL_M = 18,
        CLEAR_BIG_M = 8, CLEAR_SMALL_M = 4, END_FREE_M = 2000, MIN_CROSS_DEG = 25, MAX_EDGE_M = 800000, ARTERIAL_MIN = 5000;
    public static volatile boolean on = true, sectionOn = true;
    /** Half widths (blocks from the centre line): paved, and with the bridge rails. */
    public static final double[] HALF = {17.5, 7.5, 5.5, 3, 4, 3.5};   // 0.29.0 rail: the ballast section, 8 ft 6 in ties with 12 in ballast shoulders and their 2:1 slopes (Amtrak Spec 63 s. 2.4.2), 7 m (CHOSEN)
    static final ThreadLocal<Boolean> BUILDING = ThreadLocal.withInitial(() -> false);
    public static boolean building() { return BUILDING.get(); }

    public static final class Edge { public final int id, a, b; public final Cls cls; public final double[][] pts; public final int[] cells; public final double lenM;
        Edge(int id, int a, int b, Cls cls, double[][] pts, int[] cells, double lenM) { this.id = id; this.a = a; this.b = b; this.cls = cls; this.pts = pts; this.cells = cells; this.lenM = lenM; } }
    public static final class Route { public final Edge e; double[] px, py, pz; public float[] along, z, ground; public byte[] kind; public double maxGrade, minRadiusM, bridgeM, tunnelM; public int overpasses, shortCurves, holdMissCm, holdRounds, holdAt; public double minK = Double.MAX_VALUE, solveMs; public int recordDevCm, endMissCm; public long ms;
        /** 0.23.2 the road as OpenDRIVE-style records (the samples are made from it), and the design values it was built to (mph, m, ft per %). */
        public Alignment al; public double designMph, rMinM, kcFt, ksFt; public final List<Integer> crossAt = new ArrayList<>();
        /** 0.23.1 ramps: the offset (toward the ramp's left) of the parent road's lane edge while the ramp runs on it (NaN: free); the side local() calls left; a stop line's distance along (NaN none). */
        public float[] clip; public int left = 1; public double stopAt = Double.NaN; final List<double[]> pending = new ArrayList<>();
        Map<Long, int[]> buckets = new HashMap<>(); Route(Edge e) { this.e = e; }
        /** Sample i's point (unit sphere) and the number of samples. */
        public double[] p(int i) { return new double[]{px[i], py[i], pz[i]}; } public int n() { return px.length; } }

    public final List<Edge> edges = new ArrayList<>(); public final double[] lenKm = new double[6]; public long buildMs, refined; public final java.util.concurrent.atomic.LongAdder samples = new java.util.concurrent.atomic.LongAdder(); public final long[] phase = new long[3], rngNs = new long[3]; public final long[] layNs = new long[6]; public volatile long refineNanos; public final java.util.concurrent.atomic.LongAdder shortPaths = new java.util.concurrent.atomic.LongAdder();
    final int[] cellStart, cellEdges; final PlanetWorld w; final Sites st; final Rivers rv; final Terrain t; final double R; final long seed;
    /** Every laid road's segments by 256 m bucket ((road id << 24) | segment), the laid roads by id, and the cells whose roads are all laid. */
    final ConcurrentHashMap<Long, long[]> gidx = new ConcurrentHashMap<>(); volatile Route[] laid; java.util.concurrent.atomic.AtomicIntegerArray ready;
    final Map<Integer, FutureTask<Route>> routes = new ConcurrentHashMap<>(); final Map<Integer, List<Sites.Site>> urban = new HashMap<>();

    public Roads(PlanetWorld w) {
        long t0 = System.nanoTime(); this.w = w; st = w.sites(); rv = w.terrain.rivers; t = w.terrain; R = w.cube.R; seed = w.seed ^ 0x40AD5L; int n = rv.h.length;
        List<Sites.Site> cores = new ArrayList<>(), big = new ArrayList<>(), all = new ArrayList<>();
        for (Sites.Site s : st.all) { if (s.continent < 0) continue; all.add(s); if (s.pop >= ARTERIAL_MIN) big.add(s); if (s.kind == Sites.Kind.LARGE_CITY || s.kind == Sites.Kind.MEGACITY) cores.add(s); }
        // 0.27.7 the megacities' plans first, on this thread (the coarse routes run in parallel and end at their boulevards: measured, a worker
        // asking PlanetWorld.metro() while this thread holds the world's lock deadlocked)
        if (Metro.on) { Metro mt = w.metro(); if (mt != null) for (Sites.Site s : cores) if (Metro.isCity(s)) mt.plan(s); }
        Set<Long> have = new HashSet<>();
        rng(cores, Cls.INTERSTATE, 14, have);
        // FHWA's Interstate length per US land of habitable land: the longest links over it become US highways (arterials)
        double target = US_INTERSTATE_MI * 1.609344 * st.habitKm2 / Sites.US_LAND_KM2; List<Edge> byLen = new ArrayList<>(edges); byLen.sort((x, y) -> Double.compare(y.lenM, x.lenM));
        for (Edge e : byLen) { if (lenKm[0] <= target) break; edges.set(e.id, new Edge(e.id, e.a, e.b, Cls.ARTERIAL, e.pts, e.cells, e.lenM)); lenKm[0] -= e.lenM / 1000; lenKm[1] += e.lenM / 1000; }
        phase[0] = Sites.ms(t0); rng(big, Cls.ARTERIAL, 8, have); phase[1] = Sites.ms(t0); rng(all, Cls.COLLECTOR, 5, have); phase[2] = Sites.ms(t0);
        rail(all);
        metroEnds();
        // the corridor index: each edge under every cell its coarse route passes and their neighbours (compressed rows)
        int[] cnt = new int[n + 1], stamp = new int[n]; int[][] nearOf = new int[edges.size()][]; int[] tmp = new int[4096];
        for (Edge e : edges) { int id = e.id + 1, m = 0; for (int c : e.cells) { if (stamp[c] != id) { stamp[c] = id; if (m == tmp.length) tmp = Arrays.copyOf(tmp, m * 2); tmp[m++] = c; } for (int q = 0; q < 8; q++) { int o = rv.nb[c * 8 + q]; if (o >= 0 && stamp[o] != id) { stamp[o] = id; if (m == tmp.length) tmp = Arrays.copyOf(tmp, m * 2); tmp[m++] = o; } } }
            nearOf[e.id] = Arrays.copyOf(tmp, m); for (int q = 0; q < m; q++) cnt[tmp[q]]++; }
        cellStart = new int[n + 1]; for (int c = 0; c < n; c++) cellStart[c + 1] = cellStart[c] + cnt[c]; cellEdges = new int[cellStart[n]]; int[] fillp = Arrays.copyOf(cellStart, n);
        for (Edge e : edges) for (int c : nearOf[e.id]) cellEdges[fillp[c]++] = e.id;
        List<Sites.Site> urb = new ArrayList<>(cores); st.mark(urb, s -> s.urbanRadiusM(), urban);
        double cellKm2 = 4 * Math.PI * (R / 1000) * (R / 1000) / n; for (int c = 0; c < n; c++) if (rv.kind[c] != 1 && rv.kind[c] != 2 && st.habit[c] >= 0.5) farmKm2 += cellKm2;
        double want = US_RURAL_LOCAL_MI * 1.609344 * st.habitKm2 / Sites.US_LAND_KM2 / (2 / MILE * 1000), lo = 0, hi = 100;   // km2 of full grid wanted
        for (int it = 0; it < 50; it++) { double k = (lo + hi) / 2, a = 0; for (int c = 0; c < n; c++) if (rv.kind[c] != 1 && rv.kind[c] != 2 && st.habit[c] >= 0.5) a += cellKm2 * Math.min(1, k * Math.pow(st.habit[c], 3)); if (a < want) lo = k; else hi = k; }
        SECTION_K = (lo + hi) / 2; SECTION_SHARE = want / farmKm2;
        icx.R = R; nextRamp.set(edges.size()); laid = new Route[edges.size()]; ready = new java.util.concurrent.atomic.AtomicIntegerArray(n);
        buildMs = (System.nanoTime() - t0) / 1_000_000; }
    /** A small int-keyed map of path costs and parents (open addressing). */
    static final class IntMap { int[] k; double[] v; int[] p; int size; IntMap(int cap) { k = new int[cap]; Arrays.fill(k, -1); v = new double[cap]; p = new int[cap]; }
        int slot(int key) { int m = k.length - 1, i = (key * 0x9E3779B1) >>> 7 & m; while (k[i] != -1 && k[i] != key) i = (i + 1) & m; return i; }
        double g(int key) { int i = slot(key); return k[i] == key ? v[i] : Double.MAX_VALUE; } int parent(int key) { int i = slot(key); return k[i] == key ? p[i] : -1; }
        void put(int key, double g, int par) { if (size * 2 >= k.length) grow(); int i = slot(key); if (k[i] != key) { k[i] = key; size++; } v[i] = g; p[i] = par; }
        void grow() { int[] ok = k; double[] ov = v; int[] op = p; k = new int[ok.length * 2]; Arrays.fill(k, -1); v = new double[k.length]; p = new int[k.length]; size = 0; for (int i = 0; i < ok.length; i++) if (ok[i] != -1) put(ok[i], ov[i], op[i]); } }
    Set<Integer> near(int[] cells) { Set<Integer> s = new HashSet<>(); for (int c : cells) { s.add(c); for (int k = 0; k < 8; k++) { int m = rv.nb[c * 8 + k]; if (m >= 0) s.add(m); } } return s; }

    /** A relative neighbourhood graph of the places (each place's k nearest of the set on its continent as the candidates), edges new to the
     *  network routed over the drainage grid. */
    void rng(List<Sites.Site> set, Cls cls, int k, Set<Long> have) {
        long tr0 = System.nanoTime(); int n = rv.h.length; Map<Integer, List<Sites.Site>> byCell = new HashMap<>(); for (Sites.Site s : set) byCell.computeIfAbsent(s.cell, z -> new ArrayList<>()).add(s);
        Map<Integer, List<Sites.Site>> cand = new ConcurrentHashMap<>(); ThreadLocal<int[][]> buf = ThreadLocal.withInitial(() -> new int[][]{new int[n], new int[n], new int[1]});
        set.parallelStream().forEach(a -> { int[][] bb = buf.get(); int[] stamp = bb[0], q = bb[1]; int mark = ++bb[2][0]; List<Sites.Site> found = new ArrayList<>(); int qh = 0, qt = 0; q[qt++] = a.cell; stamp[a.cell] = mark; int depthEnd = qt, depth = 0;
            while (qh < qt && depth <= 16) { int c = q[qh++]; List<Sites.Site> in = byCell.get(c); if (in != null) for (Sites.Site b : in) if (b != a && b.continent == a.continent) found.add(b);
                for (int j2 = 0; j2 < 8; j2++) { int m = rv.nb[c * 8 + j2]; if (m >= 0 && stamp[m] != mark && rv.kind[m] != 1) { stamp[m] = mark; q[qt++] = m; } }
                if (qh == depthEnd) { depth++; depthEnd = qt; if (found.size() >= k && depth >= 2) break; } }
            found.sort(Comparator.comparingDouble(b -> Rivers.arc(a.s, b.s))); cand.put(a.id, found.size() > k ? new ArrayList<>(found.subList(0, k)) : found); });
        long tq = System.nanoTime(); rngNs[0] += tq - tr0;
        List<int[]> pairs = new ArrayList<>();
        for (Sites.Site a : set) for (Sites.Site b : cand.get(a.id)) { long key = a.id < b.id ? ((long) a.id << 32) | b.id : ((long) b.id << 32) | a.id; if (have.contains(key)) continue;
            double dab = Rivers.arc(a.s, b.s); if (dab * R > MAX_EDGE_M) continue; boolean keep = true;
            for (List<Sites.Site> l : List.of(cand.get(a.id), cand.get(b.id))) { for (Sites.Site c : l) if (c != a && c != b && Rivers.arc(a.s, c.s) < dab && Rivers.arc(b.s, c.s) < dab) { keep = false; break; } if (!keep) break; }
            if (keep) { have.add(key); pairs.add(new int[]{a.id, b.id}); } }
        long tq2 = System.nanoTime(); rngNs[1] += tq2 - tq; Edge[] made = new Edge[pairs.size()];
        java.util.stream.IntStream.range(0, pairs.size()).parallel().forEach(i -> { int[] pr = pairs.get(i); made[i] = coarse(pr[0], pr[1], cls); });
        rngNs[2] += System.nanoTime() - tq2;
        for (Edge e : made) if (e != null) { Edge e2 = new Edge(edges.size(), e.a, e.b, e.cls, e.pts, e.cells, e.lenM); edges.add(e2); lenKm[cls.ordinal()] += e.lenM / 1000; } }
    /** 0.29.0 THE FREIGHT MAIN LINES: a relative neighbourhood graph of the large cities, megacities, small cities and industrial towns
     *  (their own pairs: a line may run beside a road), cut to the US Class I network for the land (STATED: about 92,000 route miles,
     *  BTS 2021 via ASCE's 2025 Report Card; the whole freight network is about 140,000), dropping the links between the smallest places
     *  first. A line into a megacity or large city ends at a freight yard 1.2 km past its edge, on the side it comes from (the city's
     *  streets are not crossed by rail); into a town it ends at the town's centre and is left off the plat (Column), like a highway.
     *  Laid like a road (planet/Alignment records, the vertical profile) to rail limits: 1.5 % grade, 3 degree curves, 250 ft per %. */
    void rail(List<Sites.Site> all) { int first = edges.size(); List<Sites.Site> set = new ArrayList<>();
        for (Sites.Site s : all) if (s.kind == Sites.Kind.LARGE_CITY || s.kind == Sites.Kind.MEGACITY || s.kind == Sites.Kind.SMALL_CITY || s.kind == Sites.Kind.INDUSTRIAL_TOWN) set.add(s);
        rng(set, Cls.RAIL, 6, new HashSet<>()); double target = US_CLASS1_RAIL_MI * 1.609344 * st.habitKm2 / Sites.US_LAND_KM2; railTargetKm = target;
        List<Edge> mine = new ArrayList<>(edges.subList(first, edges.size())); railMadeKm = lenKm[5];
        if (lenKm[5] > target) { List<Edge> by = new ArrayList<>(mine); by.sort((x, y) -> { long px = Math.min(st.all.get(x.a).pop, st.all.get(x.b).pop), py = Math.min(st.all.get(y.a).pop, st.all.get(y.b).pop); return px != py ? Long.compare(px, py) : Double.compare(y.lenM, x.lenM); });
            Set<Edge> drop = new HashSet<>(); for (Edge e : by) { if (lenKm[5] <= target) break; drop.add(e); lenKm[5] -= e.lenM / 1000; } mine.removeIf(drop::contains); }
        edges.subList(first, edges.size()).clear(); Metro mt = Metro.on ? w.metroIfMade() : null;
        for (Edge e : mine) { double[][] pts = e.pts;
            for (int end = 0; end < 2; end++) { Sites.Site s = st.all.get(end == 0 ? e.a : e.b); boolean plan = mt != null && Metro.isCity(s); if (!(plan || s.kind == Sites.Kind.LARGE_CITY || s.kind == Sites.Kind.MEGACITY) || pts.length < 3) continue;   /* 0.29.0 every large city: under 1 million people it has no plat or plan, so its yard stands past its urban radius (measured: line 188168 met Interstate 51 at grade at Riverton City's centre) */
                Metro.Plan p = plan ? mt.plan(s) : null; double[] c = plan ? mt.centre(p) : s.s; double reach = plan ? p.reachM : s.urbanRadiusM(), keep = reach + Metro.LEAD_M;
                List<double[]> l = new ArrayList<>(Arrays.asList(pts)); if (end == 1) Collections.reverse(l); double[] to = l.get(l.size() - 1); for (int k = 1; k < l.size(); k++) if (Rivers.arc(l.get(k), c) * R > keep && rv.cellOf(l.get(k)) != s.cell) { to = l.get(k); break; }
                double ang = Rivers.arc(c, to); double f = (reach + YARD_OUT_M) / R / Math.max(1e-9, ang); double[] yard = Sites.norm(new double[]{c[0] + f * (to[0] - c[0]), c[1] + f * (to[1] - c[1]), c[2] + f * (to[2] - c[2])});
                final double[] tt = to; l.removeIf(q -> q != tt && (Rivers.arc(q, c) * R < keep || rv.cellOf(q) == s.cell)); l.add(0, yard); if (end == 1) Collections.reverse(l); pts = l.toArray(new double[0][]); }
            double len = 0; for (int i = 1; i < pts.length; i++) len += Rivers.arc(pts[i - 1], pts[i]) * R; Set<Integer> cs = new LinkedHashSet<>(); for (int cc : e.cells) cs.add(cc); for (double[] q : pts) cs.add(rv.cellOf(q));   // the yards' cells join the corridor index
            edges.add(new Edge(edges.size(), e.a, e.b, Cls.RAIL, pts, cs.stream().mapToInt(Integer::intValue).toArray(), len)); } }
    /** 0.29.0 a railway is lifted over an Interstate it crosses at any angle over this (measured: line 188985 met Interstate 592 at 17 degrees, at grade, left as a shared corridor by the roads' 25 degree rule). */
    public static final double RAIL_CROSS_DEG = 3;
    public static final double YARD_OUT_M = 1200; public double railTargetKm, railMadeKm;
    /** 0.29.6 THE FREIGHT YARD at a city end of a line: a flat yard of parallel body tracks on one side of the main track (the +off side),
     *  joined to it by a ladder at each end. STATED (US DoD UFC 4-860-01, Railroad Design and Rehabilitation, which follows AREMA): body
     *  tracks 14 ft (4.27 m) apart, the first 20 ft (6.10 m) from the main track (13.3.1); 50 ft (15.24 m) of track a car (13.2.5).
     *  CHOSEN: 60 cars a track (914 m of body track), 8 tracks at a megacity or a city with a plan, 6 at a large city; No. 8 turnouts (1 in 8,
     *  the common yard turnout) on one straight ladder; the yard level (the profile pinned flat over it). */
    public static final double YARD_FIRST_M = 6.096, YARD_SPACING_M = 4.267, YARD_CAR_M = 15.24, YARD_FROG = 8; public static final int YARD_CARS = 60;
    /** Whether the end (0 the edge's a, 1 its b) of a line is at a city's yard. */
    public boolean yardAt(Edge e, int end) { if (e.cls != Cls.RAIL) return false; Sites.Site s = st.all.get(end == 0 ? e.a : e.b); Metro mt = Metro.on ? w.metroIfMade() : null; return (mt != null && Metro.isCity(s)) || s.kind == Sites.Kind.LARGE_CITY || s.kind == Sites.Kind.MEGACITY; }
    public int yardTracks(Edge e, int end) { Sites.Site s = st.all.get(end == 0 ? e.a : e.b); Metro mt = Metro.on ? w.metroIfMade() : null; return s.kind == Sites.Kind.MEGACITY || (mt != null && Metro.isCity(s)) ? 8 : 6; }
    public static double yardOuter(int n) { return YARD_FIRST_M + (n - 1) * YARD_SPACING_M; }
    public static double yardLen(int n) { return YARD_CARS * YARD_CAR_M + 2 * YARD_FROG * yardOuter(n); }
    /** The yard at a hit on a line: {u (metres from the yard's outer end along it), yard length, tracks}, or null (no yard here). */
    public double[] yard(Hit h) { if (h.cls != Cls.RAIL || h.edge < 0 || h.kind != 0) return null; Edge e = edges.get(h.edge); Route r = route(h.edge); double Lr = r.along[r.n() - 1];
        for (int end = 0; end < 2; end++) { if (!yardAt(e, end)) continue; int n = yardTracks(e, end); double Ly = yardLen(n), u = end == 0 ? h.along : Lr - h.along; if (u >= 0 && u <= Ly && Lr > 2 * Ly) return new double[]{u, Ly, n}; } return null; }
    /** The yard's top at a hit: TIE, GRAVEL (ballast), or null (past the yard's ballast); rail[0] set where a rail runs. The tracks: the main
     *  (off 0) and track k at off c_k = 6.10 + 4.27 k, each reached from the main on the ladder (off = distance from the yard's end / 8). */
    public static Column.Mat yardTop(Hit h, double[] y, boolean[] rail) { double u = y[0], Ly = y[1]; int n = (int) y[2]; double lad = Math.min(u, Ly - u) / YARD_FROG, o = h.off; rail[0] = false;
        if (o < -HALF[Cls.RAIL.ordinal()] || o > Math.min(yardOuter(n), lad) + 3.5) return null; boolean tie = Math.floorMod((long) Math.floor(h.along), 2) == 0; boolean onTrack = false;
        for (int k = -1; k < n; k++) { double c = k < 0 ? 0 : Math.min(YARD_FIRST_M + k * YARD_SPACING_M, lad), d = Math.abs(o - c); if (d < 1.5) { onTrack = true; if (d >= 0.5) rail[0] = true; } }
        return onTrack && tie ? Column.Mat.TIE : Column.Mat.GRAVEL; }
    /** 0.27.8 each megacity's roads end at its portals (planet/Metro.portals): Interstates at the boulevard's ends (side by side when two
     *  arrive at one end), the others at the avenues' ends on the city's edge toward where they go; each arrives along its line through
     *  a lead point LEAD_M out, and its coarse points within the city (and the lead) are dropped. Assigned in edge order, so the same each run.
     *  (Measured before: every road into a city ended at one of two boulevard ends, up to 4 sharing its 1.2 km lead.) */
    /* 0.28.5 a city's road aims at its first coarse point past its lead and off the city's own drainage cell, whose centre can lie 6-11 km
     * to one side (measured: Sugarwood's arterial 9545 took the west portal for its cell's centre, then looped round the south end across
     * Interstate 2913 at grade to go east; Grandstead City's Interstate 1795 to the north left by the south end and crossed 1796). */
    void metroEnds() { Metro mt = Metro.on ? w.metroIfMade() : null; if (mt == null) return;
        for (Sites.Site s : st.all) { if (!Metro.isCity(s)) continue; List<Edge> mine = new ArrayList<>(); for (Edge e : edges) if ((e.a == s.id || e.b == s.id) && e.cls != Cls.RAIL) mine.add(e); if (mine.isEmpty()) continue;
            Metro.Plan p = mt.plan(s); double[] c = mt.centre(p); double keep = p.reachM + Metro.LEAD_M; final int own = s.cell; List<double[]> to = new ArrayList<>(); List<Boolean> big = new ArrayList<>();
            for (Edge e : mine) { boolean atA = e.a == s.id; double[] q = atA ? e.pts[e.pts.length - 1] : e.pts[0]; for (int k = 0; k < e.pts.length; k++) { double[] r = e.pts[atA ? k : e.pts.length - 1 - k]; if (Rivers.arc(r, c) * R > keep && rv.cellOf(r) != own) { q = r; break; } } to.add(q); big.add(e.cls == Cls.INTERSTATE); }
            List<double[][]> pl = mt.portals(p, to, big);
            for (int k = 0; k < mine.size(); k++) { Edge e = edges.get(mine.get(k).id); boolean atA = e.a == s.id; List<double[]> pts = new ArrayList<>(Arrays.asList(e.pts)); if (!atA) Collections.reverse(pts);
                double[] far = pts.get(pts.size() - 1); pts.removeIf(q -> q != far && (Rivers.arc(q, c) * R < keep || rv.cellOf(q) == own)); pts.add(0, pl.get(k)[1]); pts.add(0, pl.get(k)[0]); if (!atA) Collections.reverse(pts);
                double len = 0; for (int i = 1; i < pts.size(); i++) len += Rivers.arc(pts.get(i - 1), pts.get(i)) * R; // 0.27.8 the corridor index (crossings, hits) must know the road now passes round the city to its portal: the cells of the portal, the lead and a ring round the plan join its cells
                // (measured: a collector's bridge met Interstate 1789's bridge 0.7 m apart near Sugarville; the crossing was never looked for)
                Set<Integer> cs = new LinkedHashSet<>(); for (int cc : e.cells) cs.add(cc); cs.add(rv.cellOf(pl.get(k)[0])); cs.add(rv.cellOf(pl.get(k)[1]));
                for (int a = 0; a < 16; a++) { double ang = a * Math.PI / 8; for (double rr : new double[]{0, 0.5, 1, 1.5}) cs.add(rv.cellOf(mt.at(p, Math.cos(ang) * (p.reachM * rr + Metro.LEAD_M * (rr > 0 ? 1 : 0)), Math.sin(ang) * (p.reachM * rr + Metro.LEAD_M * (rr > 0 ? 1 : 0))))); }
                edges.set(e.id, new Edge(e.id, e.a, e.b, e.cls, pts.toArray(new double[0][]), cs.stream().mapToInt(Integer::intValue).toArray(), len)); } } }
    /** The coarse route of a pair over the drainage cells (A*: length x (1 + 25 slope); no sea, no lakes). Null: no land route. */
    Edge coarse(int ia, int ib, Cls cls) {
        Sites.Site a = st.all.get(ia), b = st.all.get(ib); int ca = a.cell, cb = b.cell; IntMap g = new IntMap(64); PriorityQueue<double[]> pq = new PriorityQueue<>(Comparator.comparingDouble(x -> x[0]));
        g.put(ca, 0, -1); pq.add(new double[]{Rivers.arc(rv.centre(ca), b.s) * R, ca, 0}); int exp = 0; double[] cbC = rv.centre(cb);
        while (!pq.isEmpty() && exp < 6000) { double[] top = pq.poll(); int c = (int) top[1]; if (c == cb) break; double gc = g.g(c); if (top[2] > gc + 1e-6) continue; exp++; double[] pc = rv.centre(c);
            for (int j = 0; j < 8; j++) { int m = rv.nb[c * 8 + j]; if (m < 0 || (m != cb && (rv.kind[m] == 1 || rv.kind[m] == 2))) continue; double[] pm = rv.centre(m); double d = Rivers.arc(pc, pm) * R, sl = Math.abs(rv.h[m] - rv.h[c]) / d;
                double ng = gc + d * (1 + (cls == Cls.RAIL ? 100 : 25) * sl);   /* 0.29.0 rail: a slope costs 4 times as much (CHOSEN: its grade limit is a quarter of a highway's) */ if (ng < g.g(m) - 1e-9) { g.put(m, ng, c); pq.add(new double[]{ng + Rivers.arc(pm, cbC) * R, m, ng}); } } }
        if (g.g(cb) == Double.MAX_VALUE) return null; List<Integer> path = new ArrayList<>(); for (int c = cb; c >= 0; c = g.parent(c)) path.add(c); Collections.reverse(path);
        List<double[]> pts = new ArrayList<>(); pts.add(a.s); for (int i = 1; i < path.size() - 1; i++) pts.add(rv.centre(path.get(i))); pts.add(b.s);
        double len = 0; for (int i = 1; i < pts.size(); i++) len += Rivers.arc(pts.get(i - 1), pts.get(i)) * R;
        return new Edge(-1, ia, ib, cls, pts.toArray(new double[0][]), path.stream().mapToInt(Integer::intValue).toArray(), len); }

    // ---------------------------------------------------------------- laying one road
    /** The road of edge id, laid when first asked (each road once; other threads wait for it). */
    public Route route(int id) {
        FutureTask<Route> f = routes.get(id); if (f == null) { FutureTask<Route> nf = new FutureTask<>(() -> lay(edges.get(id))); f = routes.putIfAbsent(id, nf); if (f == null) { f = nf; nf.run(); } }
        try { return f.get(); } catch (Exception ex) { throw new RuntimeException("laying road " + id + " failed", ex); } }
    /** 0.25.2 a road's plan view (its OpenDRIVE straights and curves), made once and kept apart from laying it: a town's links need only
     *  where its roads run (laying a road lays every road it crosses: measured 200 roads laid 1,087, 2.4 GB). {Alignment, int[1] short curves}. */
    public Object[] plan(int id) { FutureTask<Object[]> f = plans.get(id); if (f == null) { FutureTask<Object[]> nf = new FutureTask<>(() -> planOf(edges.get(id))); f = plans.putIfAbsent(id, nf); if (f == null) { f = nf; nf.run(); } }
        try { return f.get(); } catch (Exception ex) { throw new RuntimeException("planning road " + id + " failed", ex); } }
    final Map<Integer, FutureTask<Object[]>> plans = new ConcurrentHashMap<>();
    /** 0.27.8 the plan view, and for a road into a megacity a check that its curves stay off the city's built cells: the first curve after
     *  the lead can begin back inside the city (measured: Interstate 2911 into Fairview turned at its lead, 6 samples on tower lots); then
     *  that lead is made 1.6 times longer and the road planned again (up to 4 times). */
    Object[] planOf(Edge e) { Object[] r = planOnce(e); if (e.cls == Cls.RAIL) return r; /* 0.29.0 rail ends at its yard, off the city */ Metro mt = Metro.on ? w.metroIfMade() : null; if (mt == null || e.pts.length < 3) return r;
        for (int t = 0; t < 4; t++) { Alignment al = (Alignment) r[0]; double L = al.length(); int bad = -1;
            for (int end = 0; end < 2 && bad < 0; end++) { Sites.Site s = st.all.get(end == 0 ? e.a : e.b); if (!Metro.isCity(s)) continue; Metro.Plan p = mt.plan(s); double[] pt = end == 0 ? e.pts[0] : e.pts[e.pts.length - 1], ld = end == 0 ? e.pts[1] : e.pts[e.pts.length - 2];
                double span = Rivers.arc(pt, ld) * R + 1500; for (double d = 30; d < Math.min(span, L); d += 16) { double[] q = al.eval(end == 0 ? d : L - d); if (mt.built(p, new double[]{q[0], q[1], q[2]}, 0)) { bad = end; break; } } }
            if (bad < 0) return r; double[][] pts = e.pts.clone(); int ip = bad == 0 ? 0 : pts.length - 1, il = bad == 0 ? 1 : pts.length - 2; double[] P = pts[ip], Q = pts[il];
            pts[il] = Sites.norm(new double[]{P[0] + 1.6 * (Q[0] - P[0]), P[1] + 1.6 * (Q[1] - P[1]), P[2] + 1.6 * (Q[2] - P[2])}); leadsLonger.increment();
            e = new Edge(e.id, e.a, e.b, e.cls, pts, e.cells, e.lenM); r = planOnce(e); }
        return r; }
    public final java.util.concurrent.atomic.LongAdder leadsLonger = new java.util.concurrent.atomic.LongAdder();
    Object[] planOnce(Edge e) {
        Sites.Site sa = st.all.get(e.a), sb = st.all.get(e.b);
        double[] pa = st.exact(sa), pb = st.exact(sb); if (pa == null) pa = sa.s; if (pb == null) pb = sb.s;
        // 0.27.7 a megacity end is its boulevard's end (coarse(): pts[0] or the last), reached through its lead point: the lattice runs to the lead
        boolean ma = Metro.on && Metro.isCity(sa) && e.pts.length > 2, mb = Metro.on && Metro.isCity(sb) && e.pts.length > 2; if (ma) pa = e.pts[0]; if (mb) pb = e.pts[e.pts.length - 1];
        if (e.cls == Cls.RAIL) { pa = e.pts[0]; pb = e.pts[e.pts.length - 1]; ma = mb = false; }
        // 0.29.1 a line into a town comes to its station: along the tracks across the head of Main Street (Towns.station) to the depot, through
        // a lead STATION_LEAD_M long on the side it comes from; the plat itself is closed to the lattice (measured before: lines stopped at
        // the plat's edge, a 1-2 km gap through every town)
        double[][] P = e.pts; List<Towns.Plan> towns = new ArrayList<>(); Towns twn = e.cls == Cls.RAIL && Towns.on ? w.towns() : null;
        if (twn != null && P.length >= 2) for (int end = 0; end < 2; end++) { Sites.Site s = end == 0 ? sa : sb; if (Metro.isCity(s) || s.kind == Sites.Kind.LARGE_CITY || s.kind == Sites.Kind.MEGACITY) continue;
            Towns.Plan tp = twn.plan(s); if (tp == null) continue; double[] stn = twn.station(tp); double[] dep = twn.sphereAt(tp, stn[1], 0); if (dep == null) continue;
            double[] nx2 = end == 0 ? P[Math.min(1, P.length - 1)] : P[Math.max(0, P.length - 2)]; if (P.length == 2) nx2 = end == 0 ? P[1] : P[0]; double[] lcN = w.cube.locate(nx2);
            double sg = 1; if (lcN != null && (int) lcN[0] == tp.face) { double[] uvN = tp.uv(lcN[1], lcN[2]); sg = uvN[1] >= 0 ? 1 : -1; }
            double leadM = stn[2] + tp.band + 2 * RMIN_M[Cls.RAIL.ordinal()];   /* past the plat's side by two curve radii: a turn back behind the town clears it (measured with a 400 m lead: 11 of 39 town ends curved across their plat) */ double uL = stn[1]; double[] lead = twn.sphereAt(tp, uL, sg * leadM);
            // the straight (a great circle) from the depot to the lead bows toward the plat on the face's grid (measured: 110 m at Springbridge, its
            // 4.4 km lead over a block corner): the lead is moved out by twice the bow at the middle, twice over
            for (int it = 0; it < 2 && lead != null; it++) { double[] mid = Sites.norm(new double[]{dep[0] + lead[0], dep[1] + lead[1], dep[2] + lead[2]}); double[] lcM = w.cube.locate(mid); if (lcM == null || (int) lcM[0] != tp.face) break; double dev = stn[1] - tp.uv(lcM[1], lcM[2])[0]; uL += 2 * dev; lead = twn.sphereAt(tp, uL, sg * leadM); } if (lead == null) continue; towns.add(tp); stationEnds.increment();
            List<double[]> l = new ArrayList<>(Arrays.asList(P)); if (end == 1) Collections.reverse(l); l.set(0, lead); l.add(0, dep); if (end == 1) Collections.reverse(l); P = l.toArray(new double[0][]);
            if (end == 0) { pa = dep; ma = true; } else { pb = dep; mb = true; } }   // 0.29.0 a line's ends are its coarse ends (a city's yard)
        double[] m = Sites.norm(new double[]{pa[0] + pb[0], pa[1] + pb[1], pa[2] + pb[2]}); double[] dab = {pb[0] - pa[0], pb[1] - pa[1], pb[2] - pa[2]}; double dm = dot(dab, m); double[] e1 = Sites.norm(new double[]{dab[0] - m[0] * dm, dab[1] - m[1] * dm, dab[2] - m[2] * dm});
        if (!(Math.abs(e1[0]) + Math.abs(e1[1]) + Math.abs(e1[2]) > 0.5)) e1 = Sites.norm(Sites.cross(m, Math.abs(m[2]) < 0.9 ? new double[]{0, 0, 1} : new double[]{1, 0, 0})); double[] e2 = Sites.cross(m, e1);   // x along the road
        double[][] coarse = new double[P.length][]; for (int i = 0; i < coarse.length; i++) coarse[i] = plane(i == 0 ? pa : i == coarse.length - 1 ? pb : P[i], m, e1, e2);
        double g = GRADE[e.cls.ordinal()];
        // lattice A* in the corridor (a grid over the corridor's box, x along the road; weighted A*: the straight-line distance x 1.3, CHOSEN for speed)
        double[] A = coarse[ma ? 1 : 0], B = coarse[coarse.length - (mb ? 2 : 1)]; double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y0 = Double.MAX_VALUE, y1 = -Double.MAX_VALUE;
        for (double[] c : coarse) { x0 = Math.min(x0, c[0]); x1 = Math.max(x1, c[0]); y0 = Math.min(y0, c[1]); y1 = Math.max(y1, c[1]); }
        x0 -= CORRIDOR_M; x1 += CORRIDOR_M; y0 -= CORRIDOR_M; y1 += CORRIDOR_M; int nx = (int) Math.ceil((x1 - x0) / STEP_M) + 1, ny = (int) Math.ceil((y1 - y0) / STEP_M) + 1;
        List<double[]> path = new ArrayList<>(); boolean ok = false;
        // 0.27.7 the megacities' plans are closed to the lattice (a road reaches a city only by its boulevard; measured: Interstate 592 left
        // Saltridge City's north end and cut back across it, 245 samples on tower lots): each plan's square (reach + one lattice step) in this plane
        List<double[][]> cityQ = new ArrayList<>(); List<Metro.Plan> cityP = new ArrayList<>(); Metro cityM = Metro.on ? w.metroIfMade() : null; if (cityM != null) for (Metro.Plan pl : cityM.plans.values()) { double[] cc = cityM.centre(pl); if (dot(cc, m) < 0.98) continue; double h = pl.reachM + STEP_M;
            double[][] q = new double[4][]; for (int k = 0; k < 4; k++) q[k] = plane(cityM.at(pl, (k == 1 || k == 2 ? 1 : -1) * h, (k >= 2 ? 1 : -1) * h), m, e1, e2); cityQ.add(q); cityP.add(pl); }
        // 0.27.8 inside a plan's square, only its built cells (and one cell round them) are closed: a road may reach an avenue's end on the edge
        if ((long) nx * ny <= 4_000_000L) { int N = nx * ny; float[] hgt = new float[N]; Arrays.fill(hgt, Float.NaN); byte[] wat = new byte[N], in = new byte[N]; double[] gg = new double[N]; Arrays.fill(gg, Double.MAX_VALUE); int[] par = new int[N]; boolean[] closed = new boolean[N];
            int si = clampI((int) Math.round((A[0] - x0) / STEP_M), nx), sj = clampI((int) Math.round((A[1] - y0) / STEP_M), ny), ti = clampI((int) Math.round((B[0] - x0) / STEP_M), nx), tj = clampI((int) Math.round((B[1] - y0) / STEP_M), ny);
            int sN = si + sj * nx, tN = ti + tj * nx; Heap hp = new Heap(); gg[sN] = 0; par[sN] = -1; hp.push(sN, 1.3 * Math.hypot(ti - si, tj - sj) * STEP_M); int[][] mv = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {2, 1}, {1, 2}, {-2, 1}, {-1, 2}, {2, -1}, {1, -2}, {-2, -1}, {-1, -2}};
            while (hp.size > 0) { int u = hp.pop(); if (closed[u]) continue; closed[u] = true; if (u == tN) { ok = true; break; } int i0 = u % nx, j0 = u / nx; double hu = h(hgt, wat, u, i0, j0, x0, y0, m, e1, e2);
                for (int[] d : mv) { int ii = i0 + d[0], jj = j0 + d[1]; if (ii < 0 || jj < 0 || ii >= nx || jj >= ny) continue; int v = ii + jj * nx; if (closed[v]) continue;
                    if (v != tN) { if (in[v] == 0) in[v] = (byte) (distPoly(x0 + ii * STEP_M, y0 + jj * STEP_M, coarse) <= CORRIDOR_M && !inCity(x0 + ii * STEP_M, y0 + jj * STEP_M, cityQ, cityP, cityM, m, e1, e2) && !onPlats(x0 + ii * STEP_M, y0 + jj * STEP_M, towns, twn, m, e1, e2) ? 1 : 2); if (in[v] == 2) continue; }
                    double hv = h(hgt, wat, v, ii, jj, x0, y0, m, e1, e2); if (hv < 0 && v != tN) continue; double len = STEP_M * Math.hypot(d[0], d[1]), gr = Math.abs(hv - hu) / len;
                    double c = len * (1 + 2 * (gr / g) * (gr / g)) + (wat[v] == 2 ? 10 * len : wat[v] == 1 ? 3 * len : 0), ng = gg[u] + c;   /* grades over the limit cost (CHOSEN 2 (grade/limit)^2), lakes 10 times their width, rivers 3 times */
                    if (ng < gg[v]) { gg[v] = ng; par[v] = u; hp.push(v, ng + 1.3 * Math.hypot(ti - ii, tj - jj) * STEP_M); } } }
            if (ok) { for (int v = tN; v >= 0; v = par[v]) path.add(new double[]{x0 + (v % nx) * STEP_M, y0 + (v / nx) * STEP_M}); Collections.reverse(path); path.set(0, A); path.set(path.size() - 1, B); if (ma) path.add(0, coarse[0]); if (mb) path.add(coarse[coarse.length - 1]); } }
        if (!ok) for (double[] c : coarse) path.add(c);
        if (path.size() < 2) { path.clear(); path.add(coarse[0]); path.add(coarse[coarse.length - 1]); shortPaths.increment(); }   // 0.25.2: a search that ends in its start cell gives one point; the road is then the straight between its ends
        // 0.23.2 the plan view as OpenDRIVE records: straights and spiral-arc-spiral curves (AASHTO radii and spiral lengths) through the simplified path
        int ci = e.cls.ordinal(); double vD = V_MPH[ci] * 0.44704; Alignment al = new Alignment(m, e1, e2, R); int[] shortC = new int[1];
        final boolean kA = ma && !towns.isEmpty() && e.cls == Cls.RAIL, kB = mb && !towns.isEmpty() && e.cls == Cls.RAIL;   /* 0.29.1 a station's depot and lead are kept through the simplification (measured: Springbridge's lead dropped, the line ran 4.4 degrees off the tracks' line over a block corner) */
        al.corners(Alignment.fit(dpKeep(path, DP_M[ci], kA, kB), RMIN_M[ci], vD), RMIN_M[ci], 2 * RMIN_M[ci], vD, shortC);
        // 0.29.0 a line's curve too short for its corner is tighter than 3 degrees (measured: 403 m): the path is simplified more and fitted again
        for (double tol = 2 * DP_M[ci]; e.cls == Cls.RAIL && shortC[0] > 0 && tol <= 8 * DP_M[ci]; tol *= 2) { Alignment a2 = new Alignment(m, e1, e2, R); int[] s2 = new int[1]; a2.corners(Alignment.fit(dpKeep(path, tol, kA, kB), RMIN_M[ci], vD), RMIN_M[ci], 2 * RMIN_M[ci], vD, s2); al = a2; shortC = s2; railRefits.increment(); }
        return new Object[]{al, shortC}; }
    public final java.util.concurrent.atomic.LongAdder railRefits = new java.util.concurrent.atomic.LongAdder();
    Route lay(Edge e) {
        long t0 = System.nanoTime(); Route r = new Route(e); Object[] pl = e.id >= 0 ? plan(e.id) : planOf(e); Alignment al = (Alignment) pl[0]; int[] shortC = (int[]) pl[1];
        long tA = System.nanoTime(); layNs[0] += tA - t0; int ci = e.cls.ordinal(); double g = GRADE[ci]; r.al = al; r.designMph = V_MPH[ci]; r.rMinM = RMIN_M[ci]; r.kcFt = KCREST_FT[ci]; r.ksFt = KSAG_FT[ci]; double vD = V_MPH[ci] * 0.44704;
        // samples every 8 m (or a little less: a whole number of them) made from the records
        double Ltot = al.length(); int n = Math.max(2, (int) Math.ceil(Ltot / SAMPLE_M) + 1); double hs = Ltot / (n - 1);
        r.px = new double[n]; r.py = new double[n]; r.pz = new double[n]; r.along = new float[n]; r.ground = new float[n]; r.kind = new byte[n]; double[] water = new double[n];
        java.util.stream.IntStream.range(0, n).parallel().forEach(i -> { double[] q = al.eval(i * hs); r.px[i] = q[0]; r.py[i] = q[1]; r.pz[i] = q[2]; r.along[i] = (float) (i * hs); double[] hw = t.heightAndWater(new double[]{q[0], q[1], q[2]}, R); r.ground[i] = (float) hw[0]; water[i] = !Double.isNaN(hw[1]) && hw[1] > hw[0] ? hw[1] : Double.NaN; });   /* the samples' ground in parallel */
        long tB = System.nanoTime(); layNs[1] += tB - tA;
        // the profile: smoothed ground, held over water, over the Interstates it crosses, then the grade both ways
        double[] d = new double[n]; int win = (int) (120 / SAMPLE_M / 2);
        double[] pre = new double[n + 1]; for (int i = 0; i < n; i++) pre[i + 1] = pre[i] + Math.max(0, r.ground[i]); for (int i = 0; i < n; i++) { int lo = Math.max(0, i - win), hi = Math.min(n - 1, i + win); d[i] = (pre[hi + 1] - pre[lo]) / (hi - lo + 1); }
        // holds: the lowest the road may be (over water, over the Interstates it crosses)
        double[] hold = new double[n]; Arrays.fill(hold, Double.NEGATIVE_INFINITY); for (int i = 0; i < n; i++) if (!Double.isNaN(water[i])) hold[i] = water[i] + (riverBig(r.p(i)) ? CLEAR_BIG_M : CLEAR_SMALL_M);
        long tC = System.nanoTime(); crossings(r, hold); layNs[2] += System.nanoTime() - tC;
        // 0.23.2 the vertical alignment: the QP of Profile (the smoothed ground as target, holds, the class's grade, AASHTO K), nodes 16 m apart, then elevation records
        long tP = System.nanoTime(); int nn = Math.max(3, (int) Math.ceil(Ltot / PROFILE_M) + 1); double hn = Ltot / (nn - 1); double[] tg = new double[nn], wt = new double[nn], lo = new double[nn]; Arrays.fill(lo, Double.NEGATIVE_INFINITY);
        for (int k = 0; k < nn; k++) { double sk = k * hn; int i0 = (int) Math.floor(sk / hs); int i1 = Math.min(n - 1, i0 + 1); double f = Math.min(1, Math.max(0, sk / hs - i0)); i0 = Math.min(n - 1, i0); tg[k] = d[i0] + (d[i1] - d[i0]) * f; wt[k] = Double.isNaN(water[i0]) ? 1 : 1e-3;
            for (int i = Math.max(0, (int) Math.floor((sk - hn) / hs)); i <= Math.min(n - 1, (int) Math.ceil((sk + hn) / hs)); i++) if (hold[i] > Double.NEGATIVE_INFINITY) lo[k] = Math.max(lo[k], hold[i] + HOLD_MARGIN_M); }   // a node holds the highest hold within a node spacing either side
        // 0.27.7 a road into a megacity meets its boulevard's graded end (measured before: steps up to 25 blocks): the end node's target is that
        // height, weighted 1e4 (CHOSEN: holds it to centimetres against the K and grade limits, which still rule)
        { Metro mt = Metro.on && e.cls != Cls.RAIL ? w.metroIfMade() : null; /* 0.29.6 not a railway: it ends at its yard, off the boulevard (measured: Stonedale's yard pinned 150 m under the hills, in a tunnel) */ if (mt != null) { Sites.Site sa = st.all.get(e.a), sb = st.all.get(e.b); double za = mt.blvdEndZ(sa, al.eval(0)), zb = mt.blvdEndZ(sb, al.eval(Ltot));
            if (!Double.isNaN(za)) { tg[0] = za; wt[0] = PIN_W; } if (!Double.isNaN(zb)) { tg[nn - 1] = zb; wt[nn - 1] = PIN_W; } } }
        // 0.29.3 a line at a town station runs level with its depot's platform (Towns' depot ground): the nodes along the platform (from the
        // end out DEPOT_HALF_V) take the platform's height, pinned as above (measured before: 21 of 39 platforms a step of 2 to 13 blocks off the track)
        if (e.cls == Cls.RAIL && Towns.on) { Towns tw2 = w.towns(); for (int end = 0; end < 2; end++) { double zd = tw2.depotZ(st.all.get(end == 0 ? e.a : e.b)); if (Double.isNaN(zd)) continue;
            double prev = zd; int calm = 0; for (int k = 0; k < nn && calm < 40 && k * hn <= Towns.DEPOT_HALF_V + RAMP_MAX_M; k++) { int kk = end == 0 ? k : nn - 1 - k; if (k * hn <= Towns.DEPOT_HALF_V + CULVERT_M) { if (lo[kk] > Double.NEGATIVE_INFINITY && lo[kk] < zd + CLEAR_BIG_M + HOLD_MARGIN_M + 2) lo[kk] = Double.NEGATIVE_INFINITY; }   /* a creek at the station runs under it in a culvert: its hold is dropped (measured: Silverburg Springs, a creek 1 m deep across the platform, held the line 8 m over it) */
                if (k * hn <= Towns.DEPOT_HALF_V + 1e-6) { tg[kk] = zd; wt[kk] = PIN_W; lo[kk] = Math.max(lo[kk], zd - 0.4); continue; }   /* and held no lower than the platform (measured: the weight alone let 4 of 39 lines sag 2 to 14 m under it, pulled by the ground past it) */
                double c = Math.max(prev - STATION_RAMP * hn, Math.min(prev + STATION_RAMP * hn, tg[kk])); calm = Math.abs(c - tg[kk]) < 1e-9 ? calm + 1 : 0; tg[kk] = c; prev = c; } } }   /* past the platform the targets climb or fall no faster than STATION_RAMP (a target the grade can follow; measured: a wedge that stopped where it first met the ground left a station 3.8 m under its line where the ground climbed 2 %) */
        // 0.29.6 a city's freight yard is level: the nodes over its length take one height (the mean of their targets, raised to any hold in
        // it: the yard then stands on fill over a stream or a road under it), pinned like the station; past it the 1 % ramp back to the targets
        if (e.cls == Cls.RAIL) for (int end = 0; end < 2; end++) { if (!yardAt(e, end)) continue; double Ly = yardLen(yardTracks(e, end)); if (Ltot < 2 * Ly) continue; double sum = 0, yHold = Double.NEGATIVE_INFINITY; int m = 0;
            for (int k = 0; k < nn && k * hn <= Ly; k++) { int kk = end == 0 ? k : nn - 1 - k; sum += tg[kk]; m++; yHold = Math.max(yHold, lo[kk]); } if (m == 0) continue; double zy = Math.max(sum / m, yHold); if (System.getenv("DBG_STN") != null && st.all.get(end == 0 ? e.a : e.b).name.equals(System.getenv("DBG_STN"))) System.out.printf("DBGYARD edge %d end %d mean target %.1f hold %.1f yard %.1f%n", e.id, end, sum / m, yHold, zy);   /* probe */
            double prev = zy; int calm = 0; for (int k = 0; k < nn && calm < 40 && k * hn <= Ly + RAMP_MAX_M; k++) { int kk = end == 0 ? k : nn - 1 - k; if (k * hn <= Ly + 1e-6) { tg[kk] = zy; wt[kk] = PIN_W; lo[kk] = Math.max(lo[kk], zy - 0.4); continue; }
                double c = Math.max(prev - STATION_RAMP * hn, Math.min(prev + STATION_RAMP * hn, tg[kk])); calm = Math.abs(c - tg[kk]) < 1e-9 ? calm + 1 : 0; tg[kk] = c; prev = c; } }
        if (System.getenv("DBG_STN") != null && e.cls == Cls.RAIL) { String nm = System.getenv("DBG_STN"); for (int end = 0; end < 2; end++) if (st.all.get(end == 0 ? e.a : e.b).name.equals(nm)) { StringBuilder sb = new StringBuilder("DBG " + nm + " edge " + e.id + " end " + end + " nn " + nn + ":"); for (int k = 0; k < Math.min(nn, 40); k++) { int kk = end == 0 ? k : nn - 1 - k; sb.append(String.format(" [%.0f t%.1f w%.0f lo%.1f]", k * hn, tg[kk], wt[kk], lo[kk])); } System.out.println(sb); } }
        Profile.Out po = Profile.solve(hn, tg, wt, lo, g, KCREST_FT[ci] * 0.3048 * K_MARGIN, KSAG_FT[ci] * 0.3048 * K_MARGIN, null, 200); al.elev.clear(); double rdev = Profile.records(0, hn, po.z, al.elev); if (System.getenv("DBG_STN") != null && e.cls == Cls.RAIL) System.out.printf("DBGSOLVE edge %d nn %d iters %d z0 %.1f t0 %.1f zN %.1f tN %.1f rdev %.2f%n", e.id, nn, po.iters, po.z[0], tg[0], po.z[nn - 1], tg[nn - 1], rdev);
        r.z = new float[n]; for (int i = 0; i < n; i++) { d[i] = al.z(i * hs); r.z[i] = (float) d[i]; }
        double[] ck = Profile.check(al.elev); r.minK = Math.min(ck[0], ck[1]) / 0.3048; r.shortCurves = shortC[0]; r.holdRounds = po.iters; r.solveMs = po.ns / 1e6; r.recordDevCm = (int) Math.round(rdev * 100);
        double worst = 0; int wi = -1; for (int i = 0; i < n; i++) if (hold[i] - d[i] > worst) { worst = hold[i] - d[i]; wi = i; } r.holdMissCm = (int) Math.round(worst * 100); r.holdAt = wi; layNs[4] += System.nanoTime() - tP;
        for (int i = 0; i < n; i++) { double over = d[i] - r.ground[i]; r.kind[i] = (byte) (!Double.isNaN(water[i]) || over >= BRIDGE_M ? 1 : -over >= TUNNEL_M ? 2 : 0); }
        // short bridges and tunnels (under 30 m) become fill and cut, except over water
        for (int i = 0; i < n; ) { int j = i; while (j < n && r.kind[j] == r.kind[i]) j++; if (r.kind[i] != 0 && (j - i) * SAMPLE_M < 30) { boolean wet = false; for (int q = i; q < j; q++) wet |= !Double.isNaN(water[q]); if (!wet) for (int q = i; q < j; q++) r.kind[q] = 0; } i = j; }
        // 0.29.6 a yard is graded (cut and fill), not bridged or tunnelled, except over water (measured: Stonedale's yard in hills, 1.1 km of tunnel)
        if (e.cls == Cls.RAIL) for (int end = 0; end < 2; end++) { if (!yardAt(e, end)) continue; double Ly = yardLen(yardTracks(e, end)); if (Ltot < 2 * Ly) continue;
            for (int i = 0; i < n; i++) { double u = end == 0 ? i * hs : Ltot - i * hs; if (u <= Ly && Double.isNaN(water[i])) r.kind[i] = 0; } }
        double mg = 0; for (int i = 1; i < n; i++) mg = Math.max(mg, Math.abs(d[i] - d[i - 1]) / hs); r.maxGrade = mg;
        r.minRadiusM = al.minRadius();
        for (int i = 0; i < n; i++) { if (r.kind[i] == 1) r.bridgeM += SAMPLE_M; if (r.kind[i] == 2) r.tunnelM += SAMPLE_M; }
        long tD = System.nanoTime();
        buckets(r);
        index(r, e.id); laid[e.id] = r; samples.add(n);
        for (double[] pd : r.pending) { if (r.e.cls == Cls.INTERSTATE) icx.system(route((int) pd[0]), r, (int) pd[2], (int) pd[1]); else if (diaBuilt.add(((long) pd[0] << 32) | e.id)) icx.diamond(route((int) pd[0]), r, (int) pd[2], (int) pd[1]); }
        r.pending.clear(); if (e.cls != Cls.INTERSTATE) r.buckets = null;   // only Interstates' own buckets are read again (crossings, interchanges)
        layNs[3] += System.nanoTime() - tD; r.ms = (System.nanoTime() - t0) / 1_000_000; refined++; refineNanos += System.nanoTime() - t0; return r; }
    /** Each segment under every 256 m bucket its paved width and side slopes can reach. */
    void buckets(Route r) { double reach = (HALF[r.e.cls.ordinal()] + 2 + 40) / R; r.buckets = new HashMap<>();
        for (int i = 0; i + 1 < r.n(); i++) { double[] a = r.p(i), b = r.p(i + 1); for (int x = bk(Math.min(a[0], b[0]) - reach); x <= bk(Math.max(a[0], b[0]) + reach); x++) for (int y = bk(Math.min(a[1], b[1]) - reach); y <= bk(Math.max(a[1], b[1]) + reach); y++) for (int z = bk(Math.min(a[2], b[2]) - reach); z <= bk(Math.max(a[2], b[2]) + reach); z++) {
            long key = bkey(x, y, z); int[] old = r.buckets.get(key); int[] nw = old == null ? new int[]{i} : Arrays.copyOf(old, old.length + 1); nw[nw.length - 1] = i; r.buckets.put(key, nw); } } }
    /** Puts a laid road's (or ramp's) segments in the global index. */
    void index(Route r, int id) { for (var en : r.buckets.entrySet()) { long[] add = new long[en.getValue().length]; for (int q = 0; q < add.length; q++) add[q] = ((long) id << 24) | en.getValue()[q];
            gidx.merge(en.getKey(), add, (x, y) -> { long[] z = Arrays.copyOf(x, x.length + y.length); System.arraycopy(y, 0, z, x.length, y.length); return z; }); } }
    /** 0.23.1 interchanges: ramps by id (from edges.size() up), the builder, and the diamonds made. */
    final ConcurrentHashMap<Integer, Route> ramps = new ConcurrentHashMap<>(); final java.util.concurrent.atomic.AtomicInteger nextRamp = new java.util.concurrent.atomic.AtomicInteger();
    final Set<Long> diaBuilt = ConcurrentHashMap.newKeySet(); final Interchanges icx = new Interchanges(this); final Map<Integer, Set<Integer>> diaCache = new ConcurrentHashMap<>();
    Route any(int rid) { return rid < edges.size() ? laid[rid] : ramps.get(rid); }
    /** Registers a built ramp: its buckets, the index. */
    void addRamp(Route r) { int id = nextRamp.getAndIncrement();
        buckets(r); ramps.put(id, r); index(r, id); r.buckets = null; rampCount.increment(); samples.add(r.n()); }
    public final java.util.concurrent.atomic.LongAdder rampCount = new java.util.concurrent.atomic.LongAdder();
    /** The crossroads of Interstate I that get diamond interchanges (from the coarse routes alone, so it never depends on what is laid):
     *  arterials first, then collectors, by the larger end's people; 3 miles apart in the country, 1 mile in a metro (SCDOT Highway Design
     *  Manual ch. 10: minimum crossroad spacing 3 mi rural, 1 mi urban), and 3 miles from a system interchange with another Interstate. */
    Set<Integer> dia(int iid) { return diaCache.computeIfAbsent(iid, k -> { Edge I = edges.get(k); List<double[]> cand = new ArrayList<>(); List<Double> sysAt = new ArrayList<>(); Set<Integer> seen = new HashSet<>();
        double fa = endFree(I.a), fb = endFree(I.b);
        for (int c : I.cells) for (int q = cellStart[c]; q < cellStart[c + 1]; q++) { int oid = cellEdges[q]; if (oid == k || !seen.add(oid)) continue; Edge o = edges.get(oid); double la = 0;
            for (int i = 0; i + 1 < I.pts.length; la += Rivers.arc(I.pts[i], I.pts[i + 1]) * R, i++) for (int j = 0; j + 1 < o.pts.length; j++) { double u = cross2(I.pts[i], I.pts[i + 1], o.pts[j], o.pts[j + 1]); if (u < 0) continue;
                double at = la + crossFrac(I.pts[i], I.pts[i + 1], o.pts[j], o.pts[j + 1]) * Rivers.arc(I.pts[i], I.pts[i + 1]) * R; if (at < fa || I.lenM - at < fb || angle(I.pts[i], I.pts[i + 1], o.pts[j], o.pts[j + 1]) < MIN_CROSS_DEG) continue;
                if (o.cls == Cls.INTERSTATE) sysAt.add(at); else if (o.cls == Cls.ARTERIAL || o.cls == Cls.COLLECTOR) cand.add(new double[]{oid, at, o.cls == Cls.ARTERIAL ? 1 : 0, Math.max(st.all.get(o.a).pop, st.all.get(o.b).pop), urbanNear(I.pts[i]) ? 1 : 0}); } }
        cand.sort((x, y) -> x[2] != y[2] ? Double.compare(y[2], x[2]) : x[3] != y[3] ? Double.compare(y[3], x[3]) : Double.compare(x[0], y[0]));
        Set<Integer> out = new HashSet<>(); List<Double> at = new ArrayList<>(sysAt);
        for (double[] cd : cand) { double sp = cd[4] > 0 ? MILE : 3 * MILE; boolean ok = true; for (double a : at) if (Math.abs(a - cd[1]) < sp) { ok = false; break; } if (ok) { out.add((int) cd[0]); at.add(cd[1]); } }
        return out; }); }
    boolean urbanNear(double[] p) { List<Sites.Site> u = urban.get(rv.cellOf(p)); if (u != null) for (Sites.Site co : u) if (Rivers.arc(p, co.s) * R < 2 * co.urbanRadiusM()) return true; return false; }
    /** Where segment a-b crosses c-d: the fraction along a-b. */
    static double crossFrac(double[] a, double[] b, double[] c, double[] d) { return cross2(c, d, a, b); }
    /** Design speeds 70, 60, 50, 40, 40 mph (ramps: the middle of TxDOT table 15-1's range is 50 mph; 40 mph CHOSEN for diamond ramps and C-D
     *  roads, loops are 30 mph): minimum K for crest and sag curves, ft per % of grade change (TxDOT RDM 4.8.2). */
    public static final double[] KCREST_FT = {247, 151, 84, 44, 44, 250}, KSAG_FT = {181, 136, 96, 64, 64, 250};   // 0.29.0 rail: 0.400 % per 100 ft at most (STATED, Amtrak Spec 63 s. 5.5.1.2), 250 ft per %
    static double[] box(double[] v, int h) { int n = v.length; double[] pre = new double[n + 1], o = new double[n]; for (int i = 0; i < n; i++) pre[i + 1] = pre[i] + v[i]; for (int i = 0; i < n; i++) { int lo = Math.max(0, i - h), hi = Math.min(n - 1, i + h); o[i] = (pre[hi + 1] - pre[lo]) / (hi - lo + 1); } return o; }
    /** The smallest K (ft per % of grade change) along a profile, from grades over 40 m on each side (so a 1-block step does not count as a curve). */
    public static double minK(double[] z, double ds) { int w = Math.max(1, (int) (40 / ds)); double k = Double.MAX_VALUE; for (int i = w; i + w < z.length; i++) { double g1 = (z[i] - z[i - w]) / (w * ds), g2 = (z[i + w] - z[i]) / (w * ds), a = Math.abs(g2 - g1) * 100; if (a > 1e-6) k = Math.min(k, (w * ds / 0.3048) / a); } return k; }
    /** How far from a place a crossing is left alone: 2 km, or the city's urban radius + 1 km (CHOSEN; the junctions come with the towns). */
    static final double CULVERT_M = 30;   /* 0.29.3 how far past a station platform a small stream goes under the line in a culvert, not a bridge (CHOSEN) */
    static final double RAMP_MAX_M = 2000;   /* 0.29.6 the ramp off a station or a yard reaches no farther than this (CHOSEN; measured: unbounded, in hills it ran the whole 26 km of a line and set Stonedale's yard 150 m under the ground) */
    static final double STATION_RAMP = 0.01;   /* 0.29.3 the slope of the targets off a station platform toward the ground (CHOSEN: 1 %, under the 1.5 % ruling grade) */
    static final double PIN_W = 30;   // 0.27.7 the weight of a megacity road's end on its boulevard's height (measured: 1e3 and 1e4 gave humps of 10-30 m; 30 within a block at every portal)
    double endFree(int site) { Sites.Site s = st.all.get(site); if (Metro.on && Metro.isCity(s)) return METRO_END_FREE_M; return Math.max(END_FREE_M, s.urbanRadiusM() + 1000); }
    /** 0.27.8 a megacity's roads end at its edge (portals), so they cross each other near it as anywhere: lifted over the Interstates from 150 m past their portal (CHOSEN: measured, 400 m left an arterial at grade across three Interstates 300 m from its avenue end at Riverland; measured before: roads met at grade on the leads, up to 105 samples at Riverland). */
    static final double METRO_END_FREE_M = 150;
    /** 0.27.8 how near its own end a road crossing an Interstate may stay at grade: only within the place's plat (its urban radius) and
     *  200 m (CHOSEN), not the 2 km of endFree (measured: a 3 km collector's bridge met Interstate 1789's bridge 0.7 m apart, 1.98 km from
     *  its town end, near Sugarville: an Interstate has no at-grade crossings, FHWA). */
    double crossFree(int site) { Sites.Site s = st.all.get(site); if (Metro.on && Metro.isCity(s)) return METRO_END_FREE_M; return s.urbanRadiusM() + 200; }
    boolean riverBig(double[] p) { int c = rv.cellOf(p); return rv.kind[c] == 2 || rv.q[c] >= 1000; }
    /** Lift this road over the Interstates it crosses, away from both roads' ends (2 km: CHOSEN; junctions at the places come with the towns) (a lower-numbered Interstate passes under a higher one; arterials and collectors pass
     *  over every Interstate). */
    void crossings(Route r, double[] d) {
        Set<Integer> others = new TreeSet<>(); for (int c : r.e.cells) for (int i = cellStart[c]; i < cellStart[c + 1]; i++) { Edge o = edges.get(cellEdges[i]); if (o.cls == Cls.INTERSTATE && o.id != r.e.id && (r.e.cls != Cls.INTERSTATE || o.id < r.e.id)) others.add(o.id); }
        for (int oid : others) { Route o = route(oid);
            double fa = r.e.cls == Cls.RAIL ? 0 : crossFree(r.e.a), fb = r.e.cls == Cls.RAIL ? 0 : crossFree(r.e.b), oa = endFree(o.e.a), ob = endFree(o.e.b);   // 0.29.0 rail never crosses an Interstate at grade, even at a town
            for (int i = 0; i + 1 < r.n(); i++) { if (r.along[i] < fa || r.along[r.along.length - 1] - r.along[i] < fb) continue; double[] ri = r.p(i), ri1 = r.p(i + 1); int[] seg = o.buckets.get(bkey(bk(ri[0]), bk(ri[1]), bk(ri[2]))); if (seg == null) continue;
                for (int j : seg) { if (j + 1 >= o.n() || o.along[j] < oa || o.along[o.along.length - 1] - o.along[j] < ob) continue; double[] oj = o.p(j), oj1 = o.p(j + 1); double f = cross2(ri, ri1, oj, oj1); if (f < 0 || angle(ri, ri1, oj, oj1) < (r.e.cls == Cls.RAIL ? RAIL_CROSS_DEG : MIN_CROSS_DEG)) continue;   /* near-parallel roads share a corridor (merged at the junction stage), not an overpass */ double zo = o.z[j] + (o.z[j + 1] - o.z[j]) * f;
                    double ang = angle(ri, ri1, oj, oj1), sn = Math.sin(Math.toRadians(ang)), cot = Math.cos(Math.toRadians(ang)) / Math.max(1e-3, sn); boolean sys = r.e.cls == Cls.INTERSTATE;
                    double span = sys ? (Interchanges.CD_Y + HALF[4] + 3) / sn + (Interchanges.CD_Y + HALF[4]) * cot : (HALF[0] + 3) / sn + HALF[r.e.cls.ordinal()] * cot;   // the bridge spans the road under it (and its C-D roads at a system interchange)
                    for (int q = 0; q < r.n(); q++) if (Math.abs(r.along[q] - r.along[i]) <= span) d[q] = Math.max(d[q], zo + OVERPASS_M);
                    r.overpasses++; r.crossAt.add(i); boolean dup = false; for (double[] pd : r.pending) if ((int) pd[0] == oid && Math.abs(r.along[(int) pd[1]] - r.along[i]) < 5000) dup = true;   /* one interchange per crossing (a crossing is found on several segments) */
                    if (!dup && (sys || dia(oid).contains(r.e.id))) r.pending.add(new double[]{oid, i, j, f, sys ? 1 : 0}); } } } }
    /** Where segment a-b crosses c-d (in the plane at a): the fraction along c-d, or -1. */
    static double cross2(double[] a, double[] b, double[] c, double[] d) {
        double[] e1 = Sites.norm(Sites.cross(a, Math.abs(a[2]) < 0.9 ? new double[]{0, 0, 1} : new double[]{1, 0, 0})), e2 = Sites.cross(a, e1);
        double ax = 0, ay = 0, bx = dot(b, e1) / dot(b, a), by = dot(b, e2) / dot(b, a), cx = dot(c, e1) / dot(c, a), cy = dot(c, e2) / dot(c, a), dx = dot(d, e1) / dot(d, a), dy = dot(d, e2) / dot(d, a);
        double den = (bx - ax) * (dy - cy) - (by - ay) * (dx - cx); if (Math.abs(den) < 1e-30) return -1; double s = ((cx - ax) * (dy - cy) - (cy - ay) * (dx - cx)) / den, u = ((cx - ax) * (by - ay) - (cy - ay) * (bx - ax)) / den;
        return s >= 0 && s <= 1 && u >= 0 && u <= 1 ? u : -1; }
    /** The angle between two segments' directions (degrees, 0-90). */
    static double angle(double[] a, double[] b, double[] c, double[] d) { double[] u = {b[0] - a[0], b[1] - a[1], b[2] - a[2]}, v = {d[0] - c[0], d[1] - c[1], d[2] - c[2]}; double cs = Math.abs(dot(u, v)) / Math.sqrt(dot(u, u) * dot(v, v)); return Math.toDegrees(Math.acos(Math.min(1, cs))); }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static int clampI(int v, int n) { return Math.max(0, Math.min(n - 1, v)); }
    /** The ground at grid node v (made once; wat: 1 river, 2 lake). */
    double h(float[] hgt, byte[] wat, int v, int i, int j, double x0, double y0, double[] m, double[] e1, double[] e2) { float f = hgt[v]; if (!Float.isNaN(f)) return f;
        double[] hw = t.heightAndWater(sphere(new double[]{x0 + i * STEP_M, y0 + j * STEP_M}, m, e1, e2), R); hgt[v] = (float) hw[0]; wat[v] = (byte) (!Double.isNaN(hw[1]) && hw[1] > hw[0] ? (hw[2] == 2 ? 2 : 1) : 0); return hgt[v]; }
    /** A binary min-heap of (node, key). */
    static final class Heap { int[] n = new int[1024]; double[] k = new double[1024]; int size;
        void push(int v, double key) { if (size == n.length) { n = Arrays.copyOf(n, size * 2); k = Arrays.copyOf(k, size * 2); } int i = size++; while (i > 0) { int p = (i - 1) / 2; if (k[p] <= key) break; n[i] = n[p]; k[i] = k[p]; i = p; } n[i] = v; k[i] = key; }
        int pop() { int top = n[0]; size--; if (size > 0) { int v = n[size]; double key = k[size]; int i = 0; while (true) { int l = 2 * i + 1; if (l >= size) break; int c = l + 1 < size && k[l + 1] < k[l] ? l + 1 : l; if (k[c] >= key) break; n[i] = n[c]; k[i] = k[c]; i = c; } n[i] = v; k[i] = key; } return top; } }
    double[] node(Map<Long, double[]> hc, int i, int j, double[] m, double[] e1, double[] e2) { long k = key(i, j); double[] v = hc.get(k); if (v != null) return v;
        double[] s = sphere(new double[]{i * STEP_M, j * STEP_M}, m, e1, e2); double[] hw = t.heightAndWater(s, R); v = new double[]{hw[0], !Double.isNaN(hw[1]) && hw[1] > hw[0] ? (hw[2] == 2 ? 2 : 1) : 0}; hc.put(k, v); return v; }
    static long key(long i, long j) { return (i << 32) | (j & 0xFFFFFFFFL); }
    /** 0.27.8 is plane point (x, y) on a megacity's built cells (planet/Metro.built), tested only inside its square (cityQ)? */
    boolean inCity(double x, double y, List<double[][]> qs, List<Metro.Plan> ps, Metro mt, double[] m, double[] e1, double[] e2) { for (int k = 0; k < qs.size(); k++) { if (!inQuads(x, y, Collections.singletonList(qs.get(k)))) continue;
            double[] s = Sites.norm(new double[]{m[0] + (x * e1[0] + y * e2[0]) / R, m[1] + (x * e1[1] + y * e2[1]) / R, m[2] + (x * e1[2] + y * e2[2]) / R}); if (mt.built(ps.get(k), s, 1)) return true; } return false; }
    /** 0.29.1 is plane point (x, y) on one of the towns' plats (Towns.onPlat)? */
    boolean onPlats(double x, double y, List<Towns.Plan> ps, Towns tw, double[] m, double[] e1, double[] e2) { if (ps.isEmpty()) return false; double[] s = Sites.norm(new double[]{m[0] + (x * e1[0] + y * e2[0]) / R, m[1] + (x * e1[1] + y * e2[1]) / R, m[2] + (x * e1[2] + y * e2[2]) / R}); for (Towns.Plan p : ps) if (tw.onPlat(p, s)) return true; return false; }
    public static final double STATION_LEAD_M = 400; public final java.util.concurrent.atomic.LongAdder stationEnds = new java.util.concurrent.atomic.LongAdder();
    /** 0.27.7 is plane point (x, y) inside any of the convex quads (corners in order)? */
    static boolean inQuads(double x, double y, List<double[][]> qs) { for (double[][] q : qs) { int pos = 0, neg = 0; for (int k = 0; k < 4; k++) { double[] a = q[k], b = q[(k + 1) % 4]; double c = (b[0] - a[0]) * (y - a[1]) - (b[1] - a[1]) * (x - a[0]); if (c > 0) pos++; else if (c < 0) neg++; } if (pos == 0 || neg == 0) return true; } return false; }
    double[] plane(double[] s, double[] m, double[] e1, double[] e2) { double dd = dot(s, m); return new double[]{R * dot(s, e1) / dd, R * dot(s, e2) / dd}; }
    double[] sphere(double[] xy, double[] m, double[] e1, double[] e2) { return Sites.norm(new double[]{m[0] + (e1[0] * xy[0] + e2[0] * xy[1]) / R, m[1] + (e1[1] * xy[0] + e2[1] * xy[1]) / R, m[2] + (e1[2] * xy[0] + e2[2] * xy[1]) / R}); }
    static double distPoly(double x, double y, double[][] poly) { double best = Double.MAX_VALUE; for (int i = 0; i + 1 < poly.length; i++) best = Math.min(best, segDist(x, y, poly[i], poly[i + 1])); return poly.length == 1 ? Math.hypot(x - poly[0][0], y - poly[0][1]) : best; }
    static double segDist(double x, double y, double[] a, double[] b) { double dx = b[0] - a[0], dy = b[1] - a[1], l2 = dx * dx + dy * dy; double u = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - a[0]) * dx + (y - a[1]) * dy) / l2)); return Math.hypot(x - a[0] - u * dx, y - a[1] - u * dy); }
    /** dp keeping the second point (kA) and the next to last (kB) too: a station's lead. */
    static List<double[]> dpKeep(List<double[]> p, double tol, boolean kA, boolean kB) { if ((!kA && !kB) || p.size() < 4) return dp(p, tol); int a = kA ? 1 : 0, b = kB ? p.size() - 2 : p.size() - 1; if (b <= a) return dp(p, tol);
        List<double[]> out = new ArrayList<>(); if (kA) out.add(p.get(0)); out.addAll(dp(new ArrayList<>(p.subList(a, b + 1)), tol)); if (kB) out.add(p.get(p.size() - 1)); return out; }
    static List<double[]> dp(List<double[]> p, double tol) { if (p.size() < 3) return p; boolean[] keep = new boolean[p.size()]; keep[0] = keep[p.size() - 1] = true; dpr(p, 0, p.size() - 1, tol, keep); List<double[]> o = new ArrayList<>(); for (int i = 0; i < p.size(); i++) if (keep[i]) o.add(p.get(i)); return o; }
    static void dpr(List<double[]> p, int a, int b, double tol, boolean[] keep) { double md = -1; int mi = -1; for (int i = a + 1; i < b; i++) { double dd = segDist(p.get(i)[0], p.get(i)[1], p.get(a), p.get(b)); if (dd > md) { md = dd; mi = i; } } if (md > tol) { keep[mi] = true; dpr(p, a, mi, tol, keep); dpr(p, mi, b, tol, keep); } }
    /** Design speeds 70, 60, 50, 40 mph (CHOSEN by class: rural Interstate 70 mph is the plan's table) and their minimum radii at 8 %
     *  superelevation: 1,810, 1,200, 758, 444 ft (AASHTO, via the TxDOT Roadway Design Manual 4.7.5). DP_M: the simplifying tolerance. */
    /** Design speeds (mph) by class: 70, 60, 50, 40 (CHOSEN, as RMIN_M), ramps 40 (Interchanges sets each ramp's own). PROFILE_M: the
     *  vertical solve's node spacing (CHOSEN: a tenth of the shortest Interstate curve); HOLD_MARGIN_M: added to every hold (the records pass
     *  within mm of the nodes); K_MARGIN: the solve keeps K 3 % over the minimum (CHOSEN). */
    public static final double[] V_MPH = {70, 60, 50, 40, 40, 60}; /* 0.29.0 rail: FRA Class 4 track, 60 mph freight (STATED, 49 CFR 213.9) */ public static final double PROFILE_M = 16, HOLD_MARGIN_M = 0.05, K_MARGIN = 1.03;
    public static final double[] RMIN_M = {1810 * 0.3048, 1200 * 0.3048, 758 * 0.3048, 444 * 0.3048, 214 * 0.3048, 5729.58 / 3 * 0.3048}, DP_M = {500, 300, 200, 150, 150, 500};   // 0.29.0 rail: curves of 3 degrees at most where freight runs (STATED, Amtrak Spec 63 s. 4.6.3): radius 1,910 ft (582 m)
    static double[] unit(double[] a, double[] b) { double dx = b[0] - a[0], dy = b[1] - a[1], l = Math.hypot(dx, dy); return l < 1e-9 ? new double[]{1, 0} : new double[]{dx / l, dy / l}; }
    static double circum(double[] a, double[] b, double[] c) { double ab = Math.hypot(b[0] - a[0], b[1] - a[1]), bc = Math.hypot(c[0] - b[0], c[1] - b[1]), ca = Math.hypot(a[0] - c[0], a[1] - c[1]); double ar = Math.abs((b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])) / 2; return ar < 1e-9 ? Double.MAX_VALUE : ab * bc * ca / (4 * ar); }
    static final double BUCKET = 256;
    int bk(double v) { return (int) Math.floor(v * R / BUCKET); }
    static long bkey(int x, int y, int z) { return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF); }

    // ---------------------------------------------------------------- what a column gets
    /** A road at a column: cls (or LOCAL for section roads), z (m), lateral offset (m, signed), distance along (m), kind (0 on the ground, 1 bridge, 2 tunnel), surface. */
    public static final class Hit { public Cls cls; public double z, off, along; public int kind; public boolean unpavedGravel, unpavedDirt; public int edge = -1; public float clip = Float.NaN; public int left = 1; public double stopAt = Double.NaN;
        public double half() { return cls == Cls.LOCAL ? (unpavedDirt ? 2 : unpavedGravel ? 2.5 : 3) : HALF[cls.ordinal()]; } }
    public static final java.util.concurrent.atomic.LongAdder COLS = new java.util.concurrent.atomic.LongAdder(), SECTION_COLS = new java.util.concurrent.atomic.LongAdder(), BRIDGE_COLS = new java.util.concurrent.atomic.LongAdder(), TUNNEL_COLS = new java.util.concurrent.atomic.LongAdder();
    /** The network roads within reach of s (paved width plus 40 m), nearest first; refines the roads it needs. */
    public List<Hit> hits(double[] s) {
        int c = rv.cellOf(s); if (ready.get(c) == 0) { for (int i = cellStart[c]; i < cellStart[c + 1]; i++) route(cellEdges[i]); ready.set(c, 1); }   // first touch of a cell: lay every road whose corridor reaches it
        long[] segs = gidx.get(bkey(bk(s[0]), bk(s[1]), bk(s[2]))); if (segs == null) return List.of(); List<Hit> out = new ArrayList<>(2); List<Integer> done = new ArrayList<>(2);
        for (int q = 0; q < segs.length; q++) { int rid = (int) (segs[q] >>> 24); if (done.contains(rid)) continue; done.add(rid);
            Route r = any(rid); if (r == null) continue; Edge e = r.e; Hit best = null; double reach = HALF[e.cls.ordinal()] + 40;
            for (int q2 = q; q2 < segs.length; q2++) { if ((int) (segs[q2] >>> 24) != rid) continue; int j = (int) (segs[q2] & 0xFFFFFF); double[] lo = local(r, j, s); if (lo == null || Math.abs(lo[1]) > reach) continue;
                if (best == null || Math.abs(lo[1]) < Math.abs(best.off)) { best = new Hit(); best.cls = e.cls; best.edge = e.id; best.off = lo[1]; double f = lo[0]; best.along = r.along[j] + f * (r.along[j + 1] - r.along[j]); best.z = r.z[j] + f * (r.z[j + 1] - r.z[j]); best.kind = r.kind[f < 0.5 ? j : j + 1]; if (r.clip != null) { best.clip = r.clip[f < 0.5 ? j : j + 1]; best.left = r.left; best.stopAt = r.stopAt; } } }
            if (best != null) out.add(best); }
        out.sort(Comparator.comparingDouble(h -> Math.abs(h.off) - h.half())); return out; }
    boolean nearCoarse(Edge e, double[] s) { double best = Double.MAX_VALUE; for (int i = 0; i + 1 < e.pts.length; i++) best = Math.min(best, sphSeg(s, e.pts[i], e.pts[i + 1])); return best * R < CORRIDOR_M + 2000; }
    static double sphSeg(double[] s, double[] a, double[] b) { double[] ab = {b[0] - a[0], b[1] - a[1], b[2] - a[2]}, as = {s[0] - a[0], s[1] - a[1], s[2] - a[2]}; double l2 = dot(ab, ab); double u = l2 < 1e-30 ? 0 : Math.max(0, Math.min(1, dot(as, ab) / l2));
        double[] p = {a[0] + ab[0] * u - s[0], a[1] + ab[1] * u - s[1], a[2] + ab[2] * u - s[2]}; return Math.sqrt(dot(p, p)); }
    /** s in segment j's frame: {fraction 0..1 (null past the ends), signed lateral metres}. Past the route's own ends: the distance to the end point. */
    /** 0.29.6 a segment answers for points a little past its ends (5 % of its length), so the outer side of a bend has no gap between two
     *  segments' squares (measured: at 40 m off a 582 m curve, 960 grass holes in a yard, 0.5 m wedges at each 8 m sample). */
    static final double OVERLAP = 0.05;
    double[] local(Route r, int j, double[] s) {
        double[] a = {r.px[j], r.py[j], r.pz[j]}, b = {r.px[j + 1], r.py[j + 1], r.pz[j + 1]}, ab = {b[0] - a[0], b[1] - a[1], b[2] - a[2]}, as = {s[0] - a[0], s[1] - a[1], s[2] - a[2]}; double l2 = dot(ab, ab); if (l2 < 1e-24) return null; double u = dot(as, ab) / l2;
        if (u < -OVERLAP || u > 1 + OVERLAP) { if ((u < 0 && j == 0) || (u > 1 && j + 2 == r.n())) { double[] e = u < 0 ? a : b; return new double[]{u < 0 ? 0 : 1, Rivers.arc(e, s) * R}; } return null; }
        double[] cr = Sites.cross(ab, s); double cl = Math.sqrt(dot(cr, cr)); if (cl < 1e-18) return null; double[] p = {a[0] + ab[0] * u - s[0], a[1] + ab[1] * u - s[1], a[2] + ab[2] * u - s[2]};
        return new double[]{u, -dot(p, cr) / cl * R}; }
    /** A section-line road at block x, z of face f (a mile grid on the face's own blocks), or null. hM: the ground there. */
    public Hit section(int f, int x, int z, double[] s, double hM, boolean wet) {
        if (!sectionOn || wet || hM < 1) return null; int c = rv.cellOf(s); if (farmHabit(c) < 0.5) return null;
        double gx = x / MILE, gz = z / MILE; double fx = gx - Math.floor(gx + 0.5), fz = gz - Math.floor(gz + 0.5); double ox = fx * MILE, oz = fz * MILE; boolean alongZ = Math.abs(ox) <= Math.abs(oz);   // alongZ: a road running north-south (x near a line)
        double off = alongZ ? ox : oz; if (Math.abs(off) > 3.5) return null;
        long segI = alongZ ? Math.round(gx) : (long) Math.floor(gx), segJ = alongZ ? (long) Math.floor(gz) : Math.round(gz); long hs = MoonTerrain.hash(seed, alongZ ? 21 : 22, (int) segI, (int) segJ, f);
        if (MoonTerrain.u(hs, 0) >= Math.min(1, SECTION_K * Math.pow(farmHabit(c), 3))) return null;
        List<Sites.Site> u = urban.get(c); if (u != null) for (Sites.Site co : u) if (Rivers.arc(s, co.s) * R < co.urbanRadiusM()) return null;
        Hit h = new Hit(); h.cls = Cls.LOCAL; h.off = off; h.along = alongZ ? z : x; double v = MoonTerrain.u(hs, 1); h.unpavedGravel = v < UNPAVED / 2; h.unpavedDirt = !h.unpavedGravel && v < UNPAVED; h.z = hM; h.kind = 0;
        if (Math.abs(off) > h.half()) return null; return h; }
    /** A cell's habitability for farm roads: its own, or on a coast or lakeshore cell (no land habitability) its best land neighbour's. */
    double farmHabit(int c) { if (rv.kind[c] != 1 && rv.kind[c] != 2) return st.habit[c]; double b = 0; for (int k = 0; k < 8; k++) { int m = rv.nb[c * 8 + k]; if (m >= 0) b = Math.max(b, st.habit[m]); } return b; }
    /** The share of section lines that carry a road (derived): FHWA's rural local road length per US land of habitable land, over the full
     *  mile grid (2 / 1.609344 km of road per km2) on the farm cells. */
    public double SECTION_SHARE, SECTION_K, farmKm2;   // a line's chance of a road: min(1, K habitability^3) (the best farm land gets most of the grid, as in the US Midwest; K derived)

    /** The surface block at a lateral offset of a road (MUTCD markings on paved network roads), or null outside the paved width. */
    public static Column.Mat surface(Hit h) {
        double a = Math.abs(h.off); if (a >= h.half() && h.cls != Cls.RAMP) return null; boolean dash = Math.floorMod((long) Math.floor(h.along), 12) < 3;
        switch (h.cls) {
            case INTERSTATE -> { if (a < 5.5) return null;   // the grass median
                if (a < 6.5) return Column.Mat.LINE_YELLOW; if (a >= 10 && a < 11) return dash ? Column.Mat.LINE_WHITE : Column.Mat.ROAD_CONCRETE; if (a >= 14.5 && a < 15.5) return Column.Mat.LINE_WHITE; return Column.Mat.ROAD_CONCRETE; }
            case ARTERIAL -> { if (a < 0.5) return dash ? Column.Mat.LINE_YELLOW : Column.Mat.ASPHALT; if (a >= 4.5 && a < 5.5) return Column.Mat.LINE_WHITE; return Column.Mat.ASPHALT; }
            case COLLECTOR -> { if (a < 0.5) return dash ? Column.Mat.LINE_YELLOW : Column.Mat.ASPHALT; if (a >= 4.5) return Column.Mat.LINE_WHITE; return Column.Mat.ASPHALT; }
            case RAMP -> { double u = h.off * h.left; boolean dot = Math.floorMod((long) Math.floor(h.along), 4) < 1;   // u: toward the ramp's left; MUTCD dotted lane line (3 ft line, 9 ft gap: 1 m in 4)
                if (!Float.isNaN(h.clip)) { if (u > h.clip) return null; if (u > h.clip - 1) return h.clip <= 2.5 ? (dot ? Column.Mat.LINE_WHITE : Column.Mat.ROAD_CONCRETE) : Column.Mat.LINE_WHITE; if (u > 3) return null; if (u > 2) return Column.Mat.LINE_WHITE; }
                else { if (u > 4 || u < -4) return null; if (u > 3) return Column.Mat.ROAD_CONCRETE; if (u > 2) return Column.Mat.LINE_YELLOW; }
                if (u <= -4) return null; if (u <= -3) return Column.Mat.ROAD_CONCRETE; if (u <= -2) return Column.Mat.LINE_WHITE;
                if (!Double.isNaN(h.stopAt) && h.along >= h.stopAt - 0.6 && h.along < h.stopAt + 0.4) return Column.Mat.LINE_WHITE;   // the stop line (MUTCD 3B.16) at the ramp terminal
                return Column.Mat.ROAD_CONCRETE; }
            case RAIL -> { if (a < 1.5) return Math.floorMod((long) Math.floor(h.along), 2) == 0 ? Column.Mat.TIE : Column.Mat.GRAVEL; return Column.Mat.GRAVEL; }   // 0.29.0 ties on ballast (wood ties every 19.5 in, Amtrak Spec 63 s. 6.1.3: about half the track's length is tie; here every other block), the rails over them (Column)
            default -> { return h.unpavedDirt ? Column.Mat.DIRT_PATH : h.unpavedGravel ? Column.Mat.GRAVEL : Column.Mat.ASPHALT; } } }

    /** The nearest point of a network road to s among the roads of s's cell and its neighbours (laying them): {x, y, z (unit sphere), road height m, road id, class}, or null. */
    public double[] nearestRoad(double[] s, Cls only) {
        int c = rv.cellOf(s); Set<Integer> ids = new TreeSet<>(); for (int cc : near(new int[]{c})) for (int i = cellStart[cc]; i < cellStart[cc + 1]; i++) ids.add(cellEdges[i]);
        double best = Double.MAX_VALUE; double[] out = null;
        for (int id : ids) { if (only != null && edges.get(id).cls != only) continue; Route r = route(id); for (int i = 0; i < r.n(); i++) { double d = (r.px[i] - s[0]) * (r.px[i] - s[0]) + (r.py[i] - s[1]) * (r.py[i] - s[1]) + (r.pz[i] - s[2]) * (r.pz[i] - s[2]);
            if (d < best && r.kind[i] == 0) { best = d; out = new double[]{r.px[i], r.py[i], r.pz[i], r.z[i], id, r.e.cls.ordinal()}; } } }
        return out; }
    /** For tests: the first two Interstates whose coarse routes cross at 60 degrees or more, both 60 km or more from their ends ({lower id, higher id}), or null. */
    public int[] crossingPair() {
        List<Edge> is = new ArrayList<>(); for (Edge e : edges) if (e.cls == Cls.INTERSTATE) is.add(e);
        for (Edge a : is) for (Edge b : is) { if (b.id <= a.id || Rivers.arc(a.pts[0], b.pts[0]) * R > a.lenM + b.lenM) continue; double la = 0;
            for (int i = 0; i + 1 < a.pts.length; la += Rivers.arc(a.pts[i], a.pts[i + 1]) * R, i++) { if (la < 60000 || a.lenM - la < 60000) continue; double lb = 0;
                for (int j = 0; j + 1 < b.pts.length; lb += Rivers.arc(b.pts[j], b.pts[j + 1]) * R, j++) { if (lb < 60000 || b.lenM - lb < 60000) continue;
                    if (cross2(a.pts[i], a.pts[i + 1], b.pts[j], b.pts[j + 1]) >= 0 && angle(a.pts[i], a.pts[i + 1], b.pts[j], b.pts[j + 1]) >= 60) return new int[]{a.id, b.id}; } } }
        return null; }
    public Sites st() { return st; }
    /** The ramps built so far (tests, probes). */
    public java.util.Collection<Route> rampRoutes() { return ramps.values(); }
    public String probe() { return String.format("roads: network built in %d ms (phases %s; lay ns %s), %,d edges (Interstate %,.0f km, arterial %,.0f km, collector %,.0f km, rail %,.0f km of %,.0f made, Class I target %,.0f); %d laid, %d ramps (%.0f ms each, %,d samples, about %.0f MB); columns: %,d road, %,d section road, %,d bridge, %,d tunnel",
        buildMs, Arrays.toString(phase) + " rng " + Arrays.toString(rngNs), Arrays.toString(layNs), edges.size(), lenKm[0], lenKm[1], lenKm[2], lenKm[5], railMadeKm, railTargetKm, refined, rampCount.sum(), refined == 0 ? 0 : refineNanos / 1e6 / refined, samples.sum(), samples.sum() * 70 / 1e6, COLS.sum(), SECTION_COLS.sum(), BRIDGE_COLS.sum(), TUNNEL_COLS.sum()); }
}
