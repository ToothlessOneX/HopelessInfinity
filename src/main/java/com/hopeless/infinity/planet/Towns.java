package com.hopeless.infinity.planet;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 0.24.0 TOWNS (stage 3 of the settlements plan; owner, 2026-11-03: "start building cities and other destinations ... building the road
 * system seems kind of pointless if it goes no where"). Pure Java (tools/TownsTest, tools/TownView). Each place's plat is made when a chunk
 * near it is first built, from the seed alone.
 *
 * The plat: a US railroad "T-town" grid (half the new towns of Dakota Territory were laid out by railroads or their townsite companies),
 * values from the 1881 plat of Miller, Dakota Territory (South Dakota History 16(4), 1986) - STATED: Main Street ("Broadway") 100 ft wide,
 * other streets 80 ft, a 20 ft alley through each block, lots 165 ft deep, residential lots 50 ft wide, business lots on Main Street 25 ft
 * wide. Block length 300 ft (CHOSEN: six 50 ft lots or twelve 25 ft lots; the source gives no length), so a block is 300 x 350 ft (two lot
 * depths and the alley). The grid is square to the face's block grid (the section roads' grid, as US towns were platted on the PLSS).
 * Main Street runs toward the place's most important road. Blocks are taken nearest Main Street first (a T-town grows along it; cost
 * |along| + 1.3 |across|, CHOSEN), on dry, fairly flat ground.
 * How much town: housing units = people / 2.53 (people per household, Census QuickFacts) / 0.903 (occupied share of housing units, 2020
 * Census DP1: 126,817,580 of 140,498,736); one house a residential lot (CHOSEN for places under 50,000: towns of houses). Main Street
 * storefronts: one business lot per 40 people (CHOSEN until County Business Patterns' establishment count can be read), at least 6.
 * Public places (the plan's rural town list): a post office, a fire station and a town hall on Main Street; a water tower; churches, one
 * per 929 people (2020 U.S. Religion Census: 356,642 congregations, 331,449,281 people), at least one; a school block per 3,339 people
 * (NCES CCD 2021-22: 99,271 public schools), towns of 1,000 or more (CHOSEN floor; smaller towns send children to a consolidated school);
 * a grain elevator at the end of Main Street in farm country (habitability 0.5 or more).
 * Streets (AASHTO local urban streets: lanes 10-12 ft, parking lanes 7-8 ft; CHOSEN inside those): pavement 36 ft (two 10 ft lanes, two 8
 * ft parking lanes), then a 12 ft grass strip and a 5 ft sidewalk; Main Street pavement 60 ft (two 12 ft lanes, 18 ft angle parking each
 * side), sidewalk to the lot line. Alleys gravel.
 * 0.24.0 SPACEPORTS (owner, 2026-11-03: "a destination like Starbase"): Sites places them; here the pad apron and the production site are
 * concrete areas beside the village plat (buildings come with the buildings stage, from photographs).
 * Large cities and megacities get their own stage (downtowns, towers); this stage plats every other place.
 */
public final class Towns {
    public static final double FT = 0.3048, MAIN_W = 100 * FT, STREET_W = 80 * FT, ALLEY_W = 20 * FT, LOT_D = 165 * FT, LOT_RES = 50 * FT, LOT_BIZ = 25 * FT,
        BLOCK_U = 300 * FT, BLOCK_V = 2 * LOT_D + ALLEY_W, PAVE = 36 * FT, MAIN_PAVE = 60 * FT, STRIP = 12 * FT, WALK = 5 * FT,
        OCCUPIED = 126817580.0 / 140498736, PEOPLE_PER_BIZ = 40, PEOPLE_PER_CHURCH = 331449281.0 / 356642, PEOPLE_PER_SCHOOL = 331449281.0 / 99271, SCHOOL_MIN = 1000, ACROSS_COST = 1.3;
    public static volatile boolean on = true; public static final java.util.concurrent.atomic.LongAdder GRADED = new java.util.concurrent.atomic.LongAdder();
    /** What a lot is used for. */
    public enum Use { NONE, HOUSE, STORE, POST_OFFICE, FIRE_STATION, TOWN_HALL, CHURCH, WATER_TOWER, SCHOOL, GRAIN_ELEVATOR, PAD, PRODUCTION, SUBSTATION, DEPOT }
    /** A place's plat. Plan coordinates: u along Main Street, v across it, metres from the plat's centre (Main Street x the central cross street). */
    public static final class Plan {
        public final Sites.Site site; public final double[] c, eu, ev; public int face; public double cx, cz, depotU = Double.NaN;   /* 0.29.1 depotU: the depot's u, NaN with no railway */  public boolean alongX; public final Map<Long, Use> blocks = new HashMap<>(), lots = new HashMap<>(); public double extentM; public int units, houses, stores, schools, churches, blocksUsed; public long ms; public String note = "";
        /** 0.24.1 the graded surface: heights (m) at the street crossings (node I along, J across) and of each spaceport area; the largest cut and fill. */
        public final Map<Long, Double> nodeZ = new HashMap<>(), targets = new HashMap<>(); public final List<Double> zoneZ = new ArrayList<>(); public double maxCut, maxFill, maxGrade, band = BAND; public int coreBlocks, dropped; public double shiftM; public long routeNs; public boolean rough; public int islands;   // routeNs: time spent laying the roads into the place (shared with the chunks those roads cross)
        /** 0.24.0 spaceports: concrete areas {centre u, centre v, half u, half v, Use ordinal}. */
        public final List<double[]> zones = new ArrayList<>();
        /** 0.25.2 roads out of the plat: {u0, v0, u1, v1} (centre lines): highway connectors to the street grid, a spaceport's road to its production site and pad. */
        public final List<double[]> links = new ArrayList<>();
        Plan(Sites.Site site, double[] c, double[] eu, double[] ev) { this.site = site; this.c = c; this.eu = eu; this.ev = ev; }
        /** Plan coordinates of block x, z of the plat's face (the plat is square to the face's block grid: streets run along block rows). */
        public double[] uv(double x, double z) { return alongX ? new double[]{x - cx, z - cz} : new double[]{z - cz, x - cx}; } }
    /** What a column of a town is: zone, the lot's use, and plan coordinates. */
    public static final class Hit { public Kind kind; public Use use = Use.NONE; public double u, v, du, dv, z = Double.NaN; public Plan plan; public int bi, bj, lot, row; public boolean road; public double linkD = Double.MAX_VALUE; }   // linkD: 0.25.2 distance to a link's centre line inside the plat
    public enum Kind { MAIN_STREET, STREET, ALLEY, LOT, ZONE, SLOPE, ROAD }

    final PlanetWorld w; final Sites st; final Rivers rv; final Terrain t; final double R; final long seed;
    public final ConcurrentHashMap<Integer, Plan> plans = new ConcurrentHashMap<>(); final ConcurrentHashMap<Integer, Plan[]> near = new ConcurrentHashMap<>();
    public final java.util.concurrent.atomic.LongAdder COLS = new java.util.concurrent.atomic.LongAdder(), made = new java.util.concurrent.atomic.LongAdder(), planNs = new java.util.concurrent.atomic.LongAdder();
    public Towns(PlanetWorld w) { this.w = w; st = w.sites(); rv = w.terrain.rivers; t = w.terrain; R = w.cube.R; seed = w.seed ^ 0x70C4L; }
    static boolean platted(Sites.Kind k) { return k != Sites.Kind.LARGE_CITY && k != Sites.Kind.MEGACITY; }

    // ------------------------------------------------------------ the plat
    /** The plan of a site (made once). */
    public Plan plan(Sites.Site s) { return plans.computeIfAbsent(s.id, k -> make(s)); }
    Plan make(Sites.Site s) {
        long t0 = System.nanoTime(); double[] c = st.exact(s); if (c == null) c = s.s; if (s.village != null) c = s.village;
        double[] lc = w.cube.locate(c); double[][] fr = w.cube.frame((int) lc[0], lc[1], lc[2]); double[] ex = proj(fr[1], c), ez = proj(fr[2], c);   // the block grid's directions here (for choosing Main Street's axis)
        // Main Street toward the place's most important road (the highest class edge touching it; its first leg), squared to the block grid
        double[] toward = null; int bestCls = 9; Roads rd = Roads.on ? w.roads() : null; if (rd != null) for (Roads.Edge e : rd.edges) { if (e.a != s.id && e.b != s.id) continue; if (e.cls.ordinal() < bestCls && e.pts.length >= 2) { bestCls = e.cls.ordinal(); double[] q = e.a == s.id ? e.pts[Math.min(1, e.pts.length - 1)] : e.pts[Math.max(0, e.pts.length - 2)]; toward = new double[]{q[0] - c[0], q[1] - c[1], q[2] - c[2]}; } }
        if (s.downrange != null) toward = s.downrange;   // a spaceport's Main Street runs toward its pads
        boolean alongX = toward == null ? (MoonTerrain.u(MoonTerrain.hash(seed, 1, s.id, 0, 0), 0) < 0.5) : Math.abs(Roads.dot(toward, ex)) >= Math.abs(Roads.dot(toward, ez));
        Plan p = alongX ? new Plan(s, c, ex, ez) : new Plan(s, c, ez, ex); p.face = (int) lc[0]; p.cx = Math.floor(lc[1]) + 0.5; p.cz = Math.floor(lc[2]) + 0.5; p.alongX = alongX;
        { long ts = System.nanoTime(); if (s.kind != Sites.Kind.SPACEPORT) shift(p); PH[0].add(System.nanoTime() - ts); }
        long households = Math.round(s.pop / Sites.PER_HOUSEHOLD); p.units = (int) Math.round(households / OCCUPIED);
        int stores = Math.max(6, (int) Math.round(s.pop / PEOPLE_PER_BIZ)), churches = Math.max(1, (int) Math.round(s.pop / PEOPLE_PER_CHURCH)), schools = s.pop >= SCHOOL_MIN ? Math.max(1, (int) Math.round(s.pop / PEOPLE_PER_SCHOOL)) : 0;
        boolean farm = st.habit[s.cell] >= 0.5;
        int needStoreLots = stores + 3, needRes = p.units + churches + 1;   // + post office, fire station, town hall on Main Street; + the water tower on a house lot
        // up to 4 rounds: blocks are chosen, the plat graded, and blocks that would take more than 30 ft of cut or fill (IBC J109: terraces every
        // 30 ft of slope height; CHOSEN as the limit of one grading) are left natural and chosen round (the town's edge keeps its hills)
        Set<Long> banned = new HashSet<>(); int storeLots = 0, resLots = 0, sch = 0, ch = 0; List<int[]> bizBlocks = null;
        for (int round = 0; round < 4; round++) { p.blocks.clear(); p.lots.clear(); p.nodeZ.clear(); p.zoneZ.clear(); p.maxCut = p.maxFill = 0;
        // blocks grown out from Main Street at the centre, cheapest first, each one beside a block already taken (a town is one piece)
        storeLots = 0; resLots = 0; sch = 0; bizBlocks = new ArrayList<>(); List<int[]> resBlocks = new ArrayList<>(); Set<Long> seen = new HashSet<>();
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingDouble((int[] q) -> Math.abs(bu(q[0]) + BLOCK_U / 2) + ACROSS_COST * Math.abs(bv(q[1]) + BLOCK_V / 2)).thenComparingInt(q -> q[0]).thenComparingInt(q -> q[1]));
        for (int[] q : new int[][]{{0, 0}, {-1, 0}, {0, -1}, {-1, -1}}) { pq.add(q); seen.add(key(q[0], q[1])); }
        int tried = 0; while (!pq.isEmpty() && tried < 20000) { if (storeLots >= needStoreLots && resLots >= needRes && sch >= schools) break; int[] b = pq.poll(); tried++; boolean onMain = b[1] == 0 || b[1] == -1;
            long key = key(b[0], b[1]); boolean ban = banned.contains(key); if (!ban && !buildable(p, b[0], b[1])) continue; boolean took = !ban;   // a block left natural (too deep to grade) is grown past, not taken
            if (ban) { } else if (onMain && storeLots < needStoreLots) { p.blocks.put(key, Use.STORE); storeLots += 12; resLots += 6; bizBlocks.add(b); }   // front row: 12 storefronts on Main Street; back row: 6 houses
            else if (sch < schools && !onMain) { p.blocks.put(key, Use.SCHOOL); sch++; }
            else if (resLots < needRes) { p.blocks.put(key, Use.HOUSE); resLots += 12; resBlocks.add(b); } else took = false;   // houses on Main Street too past the stores, as in the T-towns
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { int[] nb = {b[0] + d[0], b[1] + d[1]}; if (seen.add(key(nb[0], nb[1]))) pq.add(nb); } }
        // public places: post office, fire station, town hall on the Main Street lots nearest the centre; churches on corner house lots; the water tower on a house lot near the centre
        List<long[]> mainLots = new ArrayList<>(); for (int[] b : bizBlocks) for (int l = 0; l < 12; l++) mainLots.add(new long[]{b[0], b[1], 0, l});
        mainLots.sort(Comparator.comparingDouble(q -> Math.abs(bu((int) q[0]) - BLOCK_U / 2 + (q[3] + 0.5) * LOT_BIZ)));
        Use[] civic = {Use.POST_OFFICE, Use.FIRE_STATION, Use.TOWN_HALL}; for (int k = 0; k < civic.length && k < mainLots.size(); k++) { long[] q = mainLots.get(k); p.lots.put(lotKey((int) q[0], (int) q[1], (int) q[2], (int) q[3]), civic[k]); }
        List<long[]> corners = new ArrayList<>(); for (int[] b : resBlocks) for (int row = 0; row < 2; row++) for (int l : new int[]{0, 5}) corners.add(new long[]{b[0], b[1], row, l});
        corners.sort(Comparator.comparingDouble(q -> Math.abs(bu((int) q[0])) + ACROSS_COST * Math.abs(bv((int) q[1]))));
        ch = 0; for (long[] q : corners) { if (ch >= churches) break; long lk = lotKey((int) q[0], (int) q[1], (int) q[2], (int) q[3]); if (!p.lots.containsKey(lk)) { p.lots.put(lk, Use.CHURCH); ch++; } }
        if (!resBlocks.isEmpty()) { int[] b = resBlocks.get(0); long lk = lotKey(b[0], b[1], 1, 2); if (!p.lots.containsKey(lk)) p.lots.put(lk, Use.WATER_TOWER); }
        // 0.26.1 the town's distribution substation: the back corner lot of the house block furthest from the centre (the edge of town, where
        // the transmission line comes in; one a town, CHOSEN): it keeps the power grid of the ported machines live (town/GridNet)
        if (resBlocks.size() > 1) { int[] b = resBlocks.get(resBlocks.size() - 1); long lk = lotKey(b[0], b[1], 1, 5); if (!p.lots.containsKey(lk)) p.lots.put(lk, Use.SUBSTATION); }
        // the grain elevator: past the end of the business district on Main Street, on the side of town away from the centre (farm country only)
        if (farm && !bizBlocks.isEmpty()) { int iMax = Integer.MIN_VALUE; for (int[] b : bizBlocks) iMax = Math.max(iMax, b[0]); for (int i = iMax + 1; i <= iMax + 4; i++) if (buildable(p, i, 0)) { p.blocks.put(key(i, 0), Use.GRAIN_ELEVATOR); break; } }
            long tg = System.nanoTime(); grade(p, s); PH[1].add(System.nanoTime() - tg); long td = System.nanoTime(); List<Long> deep = new ArrayList<>(); for (long k : p.blocks.keySet()) { int i = (int) (k >> 32), j = (int) k; double worst = 0;
                for (double[] q : new double[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}, {0.5, 0.5}}) { double u = bu(i) + q[0] * BLOCK_U, v = bv(j) + q[1] * BLOCK_V; worst = Math.max(worst, Math.abs(t.heightAndWater(at(p, u, v), R)[0] - gradedAt(p, u, v))); }
                if (worst > MAX_CUT) deep.add(k); }
            PH[2].add(System.nanoTime() - td); if (deep.isEmpty()) break; banned.addAll(deep); p.dropped = banned.size();
 }
        // 0.25.2 a town is one piece: blocks cut off from Main Street's centre by blocks left natural are dropped (measured: rough-country plats
        // came out in islands), and the rest graded again
        if (!p.blocks.isEmpty()) { long start = 0; double bc = 1e18; for (long k : p.blocks.keySet()) { int i = (int) (k >> 32), j = (int) k; double cst = Math.abs(bu(i) + BLOCK_U / 2) + Math.abs(bv(j) + BLOCK_V / 2); if (cst < bc) { bc = cst; start = k; } }
            Set<Long> one = new HashSet<>(); ArrayDeque<Long> qq = new ArrayDeque<>(); one.add(start); qq.add(start);
            while (!qq.isEmpty()) { long k = qq.poll(); int i = (int) (k >> 32), j = (int) k; for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { long nk = key(i + d[0], j + d[1]); if (p.blocks.containsKey(nk) && one.add(nk)) qq.add(nk); } }
            if (one.size() < p.blocks.size()) { int cut = 0; for (Iterator<Map.Entry<Long, Use>> it = p.blocks.entrySet().iterator(); it.hasNext(); ) { var en = it.next(); if (one.contains(en.getKey()) || en.getValue() == Use.GRAIN_ELEVATOR) continue;
                    if (en.getValue() == Use.STORE) { storeLots -= 12; resLots -= 6; } else if (en.getValue() == Use.HOUSE) resLots -= 12; else if (en.getValue() == Use.SCHOOL) sch--; it.remove(); cut++; }
                ch = 0; for (var le : p.lots.entrySet()) { long lk = le.getKey(); if (le.getValue() == Use.CHURCH && p.blocks.containsKey(key((short) (lk >> 48), (short) (lk >> 32)))) ch++; }
                p.islands = cut; p.nodeZ.clear(); p.zoneZ.clear(); p.maxCut = p.maxFill = 0; long ti = System.nanoTime(); grade(p, s); PH[3].add(System.nanoTime() - ti); } }
        p.stores = stores; p.churches = ch; p.schools = sch; p.houses = Math.max(0, resLots - ch - 1); p.blocksUsed = p.blocks.size();
        if (resLots < needRes || storeLots < needStoreLots) p.note = String.format("room for %d of %d house lots, %d of %d store lots", resLots, needRes, storeLots, needStoreLots);
        if (p.blocks.isEmpty() && s.kind != Sites.Kind.SPACEPORT) p.note = "no plat: no ground within 1.4 km where a block can be graded with 30 ft or less of cut or fill";   // 0.25.2 (measured: about 1 place in 400, on broken high plateaus)
        double ext = 0; for (long k : p.blocks.keySet()) { int i = (int) (k >> 32), j = (int) k; ext = Math.max(ext, Math.max(Math.abs(bu(i)) + BLOCK_U, Math.abs(bv(j)) + BLOCK_V)); }
        if (s.kind == Sites.Kind.SPACEPORT && s.pad != null) { double[] lp = w.cube.locate(s.pad), lq = w.cube.locate(s.production); double[] pu = p.uv(lp[1], lp[2]), pr = p.uv(lq[1], lq[2]);
            p.zones.add(new double[]{pu[0], pu[1], PAD_HALF, PAD_HALF, Use.PAD.ordinal()}); p.zones.add(new double[]{pr[0], pr[1], PROD_HALF_U, PROD_HALF_V, Use.PRODUCTION.ordinal()}); gradeZones(p);
            for (double[] z : p.zones) ext = Math.max(ext, Math.max(Math.abs(z[0]) + z[2], Math.abs(z[1]) + z[3])); }
        p.band = Math.max(BAND, SLOPE_HV * Math.max(p.maxCut, p.maxFill) + 4); long tl = System.nanoTime(); ext = links(p, s, ext); PH[4].add(System.nanoTime() - tl - p.routeNs); p.extentM = ext + STREET_W + p.band; p.ms = (System.nanoTime() - t0 - p.routeNs) / 1_000_000; made.increment(); planNs.add(System.nanoTime() - t0 - p.routeNs); return p; }
    // ------------------------------------------------------------ 0.24.1 grading (owner, 2026-11-03: "most cities are flattened. hill areas are ok on the edges")
    /** The plat graded as one surface on its street grid: each street crossing's height is the ground averaged round it (the crossing and
     *  12 points on circles of 150 and 300 m: a smoothing about 300 m wide, CHOSEN), then every street between two crossings is held to the
     *  grade limit (moving both ends toward each other, 60 passes): CORE_GRADE in the core (the first 70 % of the blocks, nearest Main
     *  Street: CHOSEN), EDGE_GRADE on the edge blocks (AASHTO local streets 5-15 %; CHOSEN 5 and 12). Blocks and streets are bilinear between
     *  their four crossings; lots sit at the street's level. Past the plat the graded ground meets the land at 2:1 slopes (IBC Appendix J,
     *  J106 cuts and J107 fills: no steeper than 2 horizontal to 1 vertical without a soils report), over a band BAND wide. Spaceport
     *  areas: each graded flat at its mean ground. */
    public static final double CORE_GRADE = 0.05, EDGE_GRADE = 0.12, CORE_SHARE = 0.7, BAND = 20, SLOPE_HV = 2, MAX_CUT = 30 * FT;
    static double uNode(int I) { return I * (BLOCK_U + STREET_W); }
    static double vNode(int J) { return J == 0 ? 0 : Math.signum(J) * (MAIN_W / 2 + (Math.abs(J) - 1) * (BLOCK_V + STREET_W) + BLOCK_V + STREET_W / 2); }
    void grade(Plan p, Sites.Site s) {
        // blocks by how early they were taken (cost order), the first CORE_SHARE are the core
        List<Long> order = new ArrayList<>(p.blocks.keySet()); order.sort(Comparator.comparingDouble((Long k) -> { int i = (int) (k >> 32), j = (int) (long) k; return Math.abs(bu(i) + BLOCK_U / 2) + ACROSS_COST * Math.abs(bv(j) + BLOCK_V / 2); }).thenComparingLong(k -> k));
        Set<Long> core = new HashSet<>(order.subList(0, (int) Math.ceil(order.size() * CORE_SHARE))); p.coreBlocks = core.size();
        Map<Long, Double> lim = new HashMap<>();   // each street edge between crossings: its grade limit (the gentler of the blocks it serves)
        for (long k : p.blocks.keySet()) { int i = (int) (k >> 32), j = (int) k; double g = core.contains(k) ? CORE_GRADE : EDGE_GRADE;
            for (int[] n : new int[][]{{i, j}, {i + 1, j}, {i, j + 1}, {i + 1, j + 1}}) if (!p.nodeZ.containsKey(key(n[0], n[1]))) { final int ni = n[0], nj = n[1]; p.nodeZ.put(key(ni, nj), p.targets.computeIfAbsent(key(ni, nj), kk -> target(p, uNode(ni), vNode(nj)))); }   // 0.26.1 targets kept across the regrading rounds (they do not change)
            for (long e : new long[]{edge(i, j, 0), edge(i, j + 1, 0), edge(i, j, 1), edge(i + 1, j, 1)}) lim.merge(e, g, Math::min); }
        for (int pass = 0; pass < 600; pass++) { boolean moved = false;   // 0.25.2: 600 passes (60 left rough ground 0.2 % over the limit)
            for (var en : lim.entrySet()) { long e = en.getKey(); int i = (int) ((e >> 33) & 0x7FFFFFFF) - (1 << 30), j = (int) ((e >> 1) & 0xFFFFFFFFL) - (1 << 30); boolean along = (e & 1) == 0;   // along: from (i, j) to (i+1, j); else to (i, j+1)
                long a = key(i, j), b = along ? key(i + 1, j) : key(i, j + 1); Double za = p.nodeZ.get(a), zb = p.nodeZ.get(b); if (za == null || zb == null) continue;
                double L = along ? uNode(i + 1) - uNode(i) : Math.abs(vNode(j + 1) - vNode(j)), mx = en.getValue() * L, d = zb - za;
                if (Math.abs(d) > mx + GRADE_TOL_M) { double fix = (Math.abs(d) - mx) / 2 * Math.signum(d); p.nodeZ.put(a, za + fix); p.nodeZ.put(b, zb - fix); moved = true; } }
            if (!moved) break; }
        double mg = 0; for (var en : lim.entrySet()) { long e = en.getKey(); int i = (int) ((e >> 33) & 0x7FFFFFFF) - (1 << 30), j = (int) ((e >> 1) & 0xFFFFFFFFL) - (1 << 30); boolean along = (e & 1) == 0; Double za = p.nodeZ.get(key(i, j)), zb = p.nodeZ.get(along ? key(i + 1, j) : key(i, j + 1)); if (za == null || zb == null) continue;
            double L = along ? uNode(i + 1) - uNode(i) : Math.abs(vNode(j + 1) - vNode(j)); mg = Math.max(mg, Math.abs(zb - za) / L); } p.maxGrade = mg;
        gradeZones(p);
        // the cut and fill it takes, measured at every block's corners and centre
        for (long k : p.blocks.keySet()) { int i = (int) (k >> 32), j = (int) k; for (double[] q : new double[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}, {0.5, 0.5}}) { double u = bu(i) + q[0] * BLOCK_U, v = bv(j) + q[1] * BLOCK_V; double zg = gradedAt(p, u, v), h = t.heightAndWater(at(p, u, v), R)[0]; p.maxCut = Math.max(p.maxCut, h - zg); p.maxFill = Math.max(p.maxFill, zg - h); } } }
    /** Each spaceport area graded flat at its mean ground (9 points); made whenever the areas change. */
    void gradeZones(Plan p) { p.zoneZ.clear(); for (double[] z : p.zones) { double sum = 0; int n = 0; for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) { sum += t.heightAndWater(at(p, z[0] + a * z[2], z[1] + b * z[3]), R)[0]; n++; } double zz = sum / n; if ((int) z[4] == Use.DEPOT.ordinal()) for (int a = -2; a <= 2; a++) for (int b = -2; b <= 2; b++) { double[] hw = t.heightAndWater(at(p, z[0] + a * z[2] / 2, z[1] + b * z[3] / 2), R); if (!Double.isNaN(hw[1]) && hw[1] > hw[0]) zz = Math.max(zz, hw[1] + 0.5); } p.zoneZ.add(zz); } }
    static long edge(int i, int j, int dir) { return ((long) (i + (1 << 30)) << 33) | ((long) (j + (1 << 30)) << 1) | dir; }
    double target(Plan p, double u, double v) { double sum = t.heightAndWater(at(p, u, v), R)[0], wsum = 1; for (int k = 0; k < 6; k++) { double ang = Math.PI * k / 3; for (double r : new double[]{150, 300}) { sum += 0.5 * t.heightAndWater(at(p, u + r * Math.cos(ang), v + r * Math.sin(ang)), R)[0]; wsum += 0.5; } } return sum / wsum; }
    /** The graded height at plan point u, v: bilinear between the crossings of its grid cell (the nearest crossings held where a corner has none). */
    static double gradedAt(Plan p, double u, double v) {
        int I = (int) Math.floor(u / (BLOCK_U + STREET_W)); double fu = (u - uNode(I)) / (uNode(I + 1) - uNode(I));
        int J = 0; if (v >= 0) { while (vNode(J + 1) <= v) J++; } else { J = -1; while (vNode(J) > v) J--; } double fv = (v - vNode(J)) / (vNode(J + 1) - vNode(J));
        Double z00 = p.nodeZ.get(key(I, J)), z10 = p.nodeZ.get(key(I + 1, J)), z01 = p.nodeZ.get(key(I, J + 1)), z11 = p.nodeZ.get(key(I + 1, J + 1)); double sum = 0; int n = 0; for (Double z : new Double[]{z00, z10, z01, z11}) if (z != null) { sum += z; n++; } if (n == 0) return Double.NaN; double m = sum / n;
        double a = z00 != null ? z00 : m, b = z10 != null ? z10 : m, c = z01 != null ? z01 : m, d = z11 != null ? z11 : m; fu = Math.max(0, Math.min(1, fu)); fv = Math.max(0, Math.min(1, fv));
        return a * (1 - fu) * (1 - fv) + b * fu * (1 - fv) + c * (1 - fu) * fv + d * fu * fv; }
    /** How far u, v lies outside the plat's taken blocks and their streets (0 inside), looked for among the blocks round it. */
    static double outside(Plan p, double u, double v) { double best = 1e9; double[] a = seg(u, STREET_W / 2, BLOCK_U, STREET_W), b = seg(v, MAIN_W / 2, BLOCK_V, STREET_W);
        int i0 = a[2] == 1 ? (int) a[0] : (int) (a.length > 3 ? a[3] : 0), j0 = b[2] == 1 ? (int) b[0] : (int) (b.length > 3 ? b[3] : 0);
        for (int i = i0 - 1; i <= i0 + 1; i++) for (int j = j0 - 1; j <= j0 + 1; j++) { if (!p.blocks.containsKey(key(i, j))) continue; double u0 = bu(i) - STREET_W, u1 = bu(i) + BLOCK_U + STREET_W, v0 = bv(j) - (j == 0 ? MAIN_W : STREET_W), v1 = bv(j) + BLOCK_V + (j == -1 ? MAIN_W : STREET_W);   // the streets round a block are drawn whole
            double du = Math.max(0, Math.max(u0 - u, u - u1)), dv = Math.max(0, Math.max(v0 - v, v - v1)); best = Math.min(best, Math.hypot(du, dv)); }
        return best; }
    /** 0.24.0 spaceport areas (CHOSEN until measured from photographs in the buildings stage): the pad apron 300 m square; the production site 600 x 400 m. */
    public static final double PAD_HALF = 150, PROD_HALF_U = 300, PROD_HALF_V = 200;
    static double[] proj(double[] v, double[] c) { double d = Roads.dot(v, c); return Sites.norm(new double[]{v[0] - c[0] * d, v[1] - c[1] * d, v[2] - c[2] * d}); }
    public static long key(int i, int j) { return ((long) i << 32) | (j & 0xFFFFFFFFL); }
    /** 0.28.5 is house lot (i, j, row, lot) of site siteId for sale (game/Realty and planet/Buildings decide alike: one in eight, by its seed)? */
    public static long lotSeed(long siteId, int i, int j, int row, int lot) { return MoonTerrain.hash(siteId, 61, i * 64 + lot, j * 4 + row, 0); }
    public static boolean forSale(long siteId, int i, int j, int row, int lot) { return Math.floorMod(lotSeed(siteId, i, j, row, lot) >>> 21, 8) == 0; }
    public static long lotKey(int i, int j, int row, int lot) { return ((long) (i & 0xFFFF) << 48) | ((long) (j & 0xFFFF) << 32) | ((long) row << 16) | lot; }
    /** The inner edge (nearer the centre) of block i along u, and of block j across (signed: the block's lower coordinate). */
    static double bu(int i) { return i >= 0 ? STREET_W / 2 + i * (BLOCK_U + STREET_W) : -(STREET_W / 2 + (-1 - i) * (BLOCK_U + STREET_W)) - BLOCK_U; }
    static double bv(int j) { return j >= 0 ? MAIN_W / 2 + j * (BLOCK_V + STREET_W) : -(MAIN_W / 2 + (-1 - j) * (BLOCK_V + STREET_W)) - BLOCK_V; }
    /** A block is buildable when its four corners and centre are dry land 2 m or more over the sea, out of rivers and lakes, and the streets
     *  round it (its four sides) keep a 12 % grade or less (CHOSEN inside AASHTO's 5-15 % for local streets, as the sites' rule cites). */
    boolean buildable(Plan p, int i, int j) { double u0 = bu(i), v0 = bv(j); double[] hh = new double[5]; int k = 0;
        for (double[] q : new double[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}, {0.5, 0.5}}) { double[] s = at(p, u0 + q[0] * BLOCK_U, v0 + q[1] * BLOCK_V); double[] hw = t.heightAndWater(s, R); if (hw[0] < 2 || (!Double.isNaN(hw[1]) && hw[1] > hw[0] - 1) || hw[2] == 3) return false; hh[k++] = hw[0]; }
        if (p.rough) { double lo = 1e9, hi = -1e9; for (double h : hh) { lo = Math.min(lo, h); hi = Math.max(hi, h); } return hi - lo <= 2 * MAX_CUT; }   // 0.25.2 rough country (see shift): a block whose ground spans 60 ft or less can be graded with 30 ft or less of cut or fill
        for (int e = 0; e < 4; e++) { double len = e % 2 == 0 ? BLOCK_U : BLOCK_V; if (Math.abs(hh[e] - hh[(e + 1) % 4]) > MAX_GRADE * len) return false; } return true; }
    /** 0.25.2 how many of the four blocks at Main Street's centre can be built on. */
    int seedScore(Plan p) { return seedScore(p, null); }
    /** memo (0.26.1): blocks already tested in this search, by the world position of their corner (the shifts reuse most of them; measured:
     *  plats took 48-52 ms each without it). */
    int seedScore(Plan p, java.util.Map<Long, Boolean> memo) { int n = 0; for (int[] q : new int[][]{{0, 0}, {-1, 0}, {0, -1}, {-1, -1}}) {
            if (memo == null) { if (buildable(p, q[0], q[1])) n++; continue; }
            double u0 = bu(q[0]), v0 = bv(q[1]); long k = (Math.round(p.alongX ? p.cx + u0 : p.cx + v0) << 32) ^ (Math.round(p.alongX ? p.cz + v0 : p.cz + u0) & 0xFFFFFFFFL);
            Boolean b = memo.get(k); if (b == null) { b = buildable(p, q[0], q[1]); memo.put(k, b); } if (b) n++; } return n; }
    /** 0.25.2 a town whose site is in a lake or on rough ground moves its plat, in whole blocks, to the nearest place within SHIFT_BLOCKS where
     *  all four centre blocks can be built on (else the best place found): the roads still end at the site, and the links join them to the plat. */
    void shift(Plan p) { java.util.Map<Long, Boolean> memo = new java.util.HashMap<>(); if (seedScore(p, memo) == 4) return; double cx0 = p.cx, cz0 = p.cz; int best = seedScore(p, memo); double bx = cx0, bz = cz0, bu0 = 0, bv0 = 0;
        for (int r = 1; r <= SHIFT_BLOCKS && best < 4; r++) for (int di = -r; di <= r && best < 4; di++) for (int dj = -r; dj <= r && best < 4; dj++) { if (Math.max(Math.abs(di), Math.abs(dj)) != r) continue;
            double du = di * (BLOCK_U + STREET_W), dv = dj * (BLOCK_V + STREET_W); p.cx = cx0; p.cz = cz0; p.cx = Math.floor(p.alongX ? cx0 + du : cx0 + dv) + 0.5; p.cz = Math.floor(p.alongX ? cz0 + dv : cz0 + du) + 0.5;
            int sc = seedScore(p, memo); if (sc > best) { best = sc; bx = p.cx; bz = p.cz; bu0 = du; bv0 = dv; } }
        if (best == 0 && !p.rough) { p.rough = true; p.cx = cx0; p.cz = cz0; shift(p); return; }   // no block under 12 % within reach: rough country, where blocks the grading can flatten are taken (measured: the 12 % test alone left mountain towns with no plat; the 60 ft test everywhere sprawled plats onto rough ground, 80 m cuts)
        p.cx = cx0; p.cz = cz0; double[] nc = at(p, bu0, bv0); p.cx = bx; p.cz = bz; System.arraycopy(nc, 0, p.c, 0, 3); p.shiftM = Math.hypot(bu0, bv0); }
    /** 0.26.1 probe: time in each plat phase (shift, grading, the deep-block test, regrading after islands, links without road plans). */
    public static final java.util.concurrent.atomic.LongAdder[] PH = {new java.util.concurrent.atomic.LongAdder(), new java.util.concurrent.atomic.LongAdder(), new java.util.concurrent.atomic.LongAdder(), new java.util.concurrent.atomic.LongAdder(), new java.util.concurrent.atomic.LongAdder()};
    public static String phases() { return String.format("plat phases (ms in all): shift %.0f, grade %.0f, deep test %.0f, regrade %.0f, links %.0f", PH[0].sum() / 1e6, PH[1].sum() / 1e6, PH[2].sum() / 1e6, PH[3].sum() / 1e6, PH[4].sum() / 1e6); }
    public static final double GRADE_TOL_M = 1e-4;   // 0.26.1: grading stops when no street is more than 0.1 mm over its limit (measured: at 1e-6 m the passes took 21 ms a plat)
    public static final int SHIFT_BLOCKS = 10;   // CHOSEN: about 1.4 km each way
    public static final double MAX_GRADE = 0.12;
    double[] at(Plan p, double u, double v) { double x = p.alongX ? p.cx + u : p.cx + v, z = p.alongX ? p.cz + v : p.cz + u; double[] q = w.cube.inside(x, z) ? w.cube.spherePoint(p.face, x, z) : null;
        return q != null ? q : Sites.norm(new double[]{p.c[0] + (p.eu[0] * u + p.ev[0] * v) / R, p.c[1] + (p.eu[1] * u + p.ev[1] * v) / R, p.c[2] + (p.eu[2] * u + p.ev[2] * v) / R}); }
    public double[] pointAt(Plan p, double u, double v) { return at(p, u, v); }

    // ------------------------------------------------------------ what a column gets
    /** The plats that may reach drainage cell c (its sites and its neighbours'). */
    Plan[] plansNear(int c) { return near.computeIfAbsent(c, k -> { List<Plan> l = new ArrayList<>(); Set<Integer> cells = new HashSet<>(); cells.add(k); for (int q = 0; q < 8; q++) { int o = rv.nb[k * 8 + q]; if (o >= 0) cells.add(o); }
        for (int cc : cells) { List<Sites.Site> in = st.byCell.get(cc); if (in != null) for (Sites.Site s : in) if (platted(s.kind)) l.add(plan(s)); } return l.toArray(new Plan[0]); }); }
    /** The town at block x, z of face f (unit point s), or null. A plat stays on its own face (a town across a face edge is cut there). */
    public Hit at(int f, double x, double z, double[] s) {
        if (!on) return null; for (Plan p : plansNear(rv.cellOf(s))) { if (p.face != f) continue; double[] uv = p.uv(x, z); if (Math.abs(uv[0]) > p.extentM || Math.abs(uv[1]) > p.extentM) continue; Hit h = classify(p, uv[0], uv[1]);
            if (h != null) { if (h.kind == Kind.ZONE) { for (int k = 0; k < p.zones.size(); k++) { double[] zn = p.zones.get(k); if (Math.abs(uv[0] - zn[0]) <= zn[2] && Math.abs(uv[1] - zn[1]) <= zn[3]) { h.z = p.zoneZ.get(k); break; } } } else { h.z = gradedAt(p, uv[0], uv[1]); for (double[] ln : p.links) h.linkD = Math.min(h.linkD, segDist(uv[0], uv[1], ln)); } COLS.increment(); return h; }
            // the 2:1 slope band round the plat (and round each spaceport area)
            double d = outside(p, uv[0], uv[1]), zg = Double.NaN; if (d < p.band) zg = gradedAt(p, uv[0], uv[1]);
            for (int k = 0; k < p.zones.size(); k++) { double[] zn = p.zones.get(k); double dz = Math.hypot(Math.max(0, Math.abs(uv[0] - zn[0]) - zn[2]), Math.max(0, Math.abs(uv[1] - zn[1]) - zn[3])); if (dz < p.band && dz < d) { d = dz; zg = p.zoneZ.get(k); } }
            Hit sl = null; if (d < p.band && !Double.isNaN(zg)) { sl = new Hit(); sl.kind = Kind.SLOPE; sl.plan = p; sl.u = uv[0]; sl.v = uv[1]; sl.z = zg; sl.du = d; }
            // 0.25.2 the roads out of the plat (over the slope band too: there they keep its grading)
            for (double[] ln : p.links) { double dl = segDist(uv[0], uv[1], ln); if (dl >= PAVE / 2 + STRIP) continue; if (sl != null) { sl.road = true; sl.dv = dl; return sl; }
                Hit r = new Hit(); r.kind = Kind.ROAD; r.plan = p; r.u = uv[0]; r.v = uv[1]; r.dv = dl; r.road = true; return r; }
            if (sl != null) return sl; }
        return null; }
    static double segDist(double u, double v, double[] ln) { double du = ln[2] - ln[0], dv = ln[3] - ln[1], l2 = du * du + dv * dv, t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((u - ln[0]) * du + (v - ln[1]) * dv) / l2)); return Math.hypot(u - ln[0] - t * du, v - ln[1] - t * dv); }
    /** 0.25.2 roads out of the plat. Highways (arterials and collectors ending at the place) stop where they reach the slope band (Column
     *  drops a network road's ground part over the plat and its band) and a connector of local-street pavement runs from that point to the
     *  nearest street crossing of the plat; a spaceport's road runs from the end of Main Street to the production site and on to the pad. Returns
     *  the plat's extent, widened to hold them. */
    /** 0.28.8 where each highway's ground part ends at a plat: key (edge << 1 | 0 at its a end, 1 at its b end), metres from that end. */
    public final Map<Long, Double> CUT = new java.util.concurrent.ConcurrentHashMap<>();
    public boolean cut(Roads.Hit h, double L) { if (h.edge < 0 || CUT.isEmpty()) return false; Double a = CUT.get((long) h.edge << 1), b = CUT.get(((long) h.edge << 1) | 1); return (a != null && h.along < a) || (b != null && L - h.along < b); }
    double links(Plan p, Sites.Site s, double ext) { boolean hasRail = false; double extIn = ext;
        Roads rd = Roads.on ? w.roads() : null; double[] mainEnd = null;
        if (rd != null) for (Roads.Edge e : rd.edges) { if ((e.a != s.id && e.b != s.id) || e.cls == Roads.Cls.INTERSTATE) continue; if (e.cls == Roads.Cls.RAIL) { hasRail = true; continue; }   /* 0.29.1 a railway comes to the station at the head of Main Street (station()), not to a street node */ long tr = System.nanoTime(); Alignment al = (Alignment) rd.plan(e.id)[0]; p.routeNs += System.nanoTime() - tr; double La = al.length(); int n = (int) (La / 8) + 1; boolean atA = e.a == s.id;   // 0.25.2: the road's plan view only (not laid)
            // 0.28.8 the entry is found from outside inward: the last point clear of the plat and its band before the road first meets them
            // (measured before, from the town end outward: Saltstead's north-east highway took its entry in a hole inside the plat, 600 m in)
            int kFar = 0; for (int k = 0; k < n; k++) { double[] q = al.eval(atA ? k * 8.0 : La - k * 8.0); double[] lc = w.cube.locate(new double[]{q[0], q[1], q[2]}); if ((int) lc[0] != p.face) break; double[] uv = p.uv(lc[1], lc[2]); if (Math.hypot(uv[0], uv[1]) > ext + 600) break; kFar = k; }
            double[] entry = null; int kIn = -1; for (int k = kFar; k >= 0; k--) { double[] q = al.eval(atA ? k * 8.0 : La - k * 8.0); double[] lc = w.cube.locate(new double[]{q[0], q[1], q[2]}); double[] uv = p.uv(lc[1], lc[2]); if (outside(p, uv[0], uv[1]) >= p.band + 4) { entry = uv; kIn = k; } else break; }
            if (String.valueOf(s.id).equals(System.getenv("DBG_PLAN"))) {   /* probe: DBG_PLAN=<site id> prints each road's entry search */ double[] q0 = al.eval(atA ? kFar * 8.0 : La - kFar * 8.0); double[] l0 = w.cube.locate(new double[]{q0[0], q0[1], q0[2]}); double[] u0 = p.uv(l0[1], l0[2]); System.out.printf("DBG edge %d La %.0f n %d kFar %d face %d/%d far uv %.0f,%.0f outside %.1f band %.1f entry %s%n", e.id, La, n, kFar, (int) l0[0], p.face, u0[0], u0[1], outside(p, u0[0], u0[1]), p.band, entry == null ? "null" : (int) entry[0] + "," + (int) entry[1]); }
            if (entry == null) continue; CUT.put(((long) e.id << 1) | (atA ? 0 : 1), kIn * 8.0); /* the highway's ground part ends here (Column.roads) */ double best = 1e18; double[] node = null; for (long k : p.nodeZ.keySet()) { int I = (int) (k >> 32), J = (int) k; double nu = uNode(I), nv = vNode(J), dd = Math.hypot(nu - entry[0], nv - entry[1]); if (dd < best) { best = dd; node = new double[]{nu, nv}; } }
            if (node != null) { p.links.add(new double[]{entry[0], entry[1], node[0], node[1]}); ext = Math.max(ext, Math.max(Math.abs(entry[0]), Math.abs(entry[1]))); } }
        if (s.kind == Sites.Kind.SPACEPORT && p.zones.size() == 2) { double end = 0; for (long k : p.nodeZ.keySet()) { int I = (int) (k >> 32), J = (int) k; if (J == 0 && Math.signum(uNode(I)) == Math.signum(p.zones.get(0)[0])) end = Math.abs(uNode(I)) > Math.abs(end) ? uNode(I) : end; }
            double[] pr = p.zones.get(1), pd = p.zones.get(0); p.links.add(new double[]{end, 0, pr[0], pr[1]}); p.links.add(new double[]{pr[0], pr[1], pd[0], pd[1]}); }
        if (hasRail) { double[] stn = station(p); p.links.add(new double[]{stn[0] + STREET_W, 0, stn[1] - DEPOT_SET_M, 0}); p.depotU = stn[1] - DEPOT_SET_M / 2; ext = Math.max(ext, stn[1] + 10);
            if (s.kind != Sites.Kind.SPACEPORT && !p.blocks.isEmpty()) { double u0 = stn[1] - DEPOT_SET_M, u1 = stn[1] - YARD_EDGE_M; p.zones.add(new double[]{(u0 + u1) / 2, 0, (u1 - u0) / 2, DEPOT_HALF_V, Use.DEPOT.ordinal()}); gradeZones(p); } }   // 0.29.1 Main Street runs on to the depot by the tracks; 0.29.3 the depot's ground (Buildings.depot); none for a place with no plat (measured: Redland Springs, no plat, took a depot and no road links)   // 0.29.1 Main Street runs on to the depot by the tracks
        return ext; }
    /** 0.29.1 THE STATION: in a railroad T-town the tracks cross the head of Main Street and the depot faces Main Street there (STATED: the
     *  T-town plan, Hudson, "Plains Country Towns" (1985), the depot at the head of the "T"; Miller, D.T., 1881). Here the tracks run across
     *  the +u end of the plat (the grain elevator's end), RAIL_SET_M past its last street (CHOSEN), and the depot stands between the street
     *  and the tracks. {u of the plat's last block end, u of the tracks}; plan coordinates. */
    public double[] station(Plan p) { int iMax = 0, jMax = 0; for (long k : p.blocks.keySet()) { iMax = Math.max(iMax, (int) (k >> 32)); int j = (int) k; jMax = Math.max(jMax, j >= 0 ? j : -1 - j); } double uEnd = STREET_W / 2 + iMax * (BLOCK_U + STREET_W) + BLOCK_U;
        return new double[]{uEnd, uEnd + STREET_W + RAIL_SET_M, MAIN_W / 2 + jMax * (BLOCK_V + STREET_W) + BLOCK_V + STREET_W}; }   // {.., .., the plat's half width across Main Street}
    public static final double RAIL_SET_M = 40, DEPOT_SET_M = 24;
    /** 0.29.3 the depot's ground: a graded area from the end of Main Street (DEPOT_SET_M before the tracks) to YARD_EDGE_M from the track's
     *  centre line (just past the ballast, Roads.HALF 3.5 m), DEPOT_HALF_V each way along the tracks (the platform; CHOSEN). */
    public static final double YARD_EDGE_M = 4.0, DEPOT_HALF_V = 30;
    /** 0.29.3 the height (m) of site s's depot ground, or NaN (no plat, no railway, a city). */
    public double depotZ(Sites.Site s) { if (s == null || Metro.isCity(s) || s.kind == Sites.Kind.LARGE_CITY || s.kind == Sites.Kind.MEGACITY || !platted(s.kind)) return Double.NaN; Plan p = plan(s); if (p == null) return Double.NaN;
        for (int k = 0; k < p.zones.size() && k < p.zoneZ.size(); k++) if ((int) p.zones.get(k)[4] == Use.DEPOT.ordinal()) return p.zoneZ.get(k); return Double.NaN; }
    /** The sphere point of plan coordinates (u, v). */
    public double[] sphereAt(Plan p, double u, double v) { double x = p.alongX ? p.cx + u : p.cx + v, z = p.alongX ? p.cz + v : p.cz + u; return w.cube.spherePoint(p.face, x, z); }
    /** Is sphere point s on plan p's plat proper (its blocks and streets, 4 m round them; not the slope band)? */
    public boolean onPlat(Plan p, double[] s) { double[] lc = w.cube.locate(s); if (lc == null || (int) lc[0] != p.face) return false; double[] uv = p.uv(lc[1], lc[2]); return outside(p, uv[0], uv[1]) < 4; }
    /** The ground height a town column gets from natural ground h: the graded height inside the plat; in the slope band, the land held
     *  within 1 vertical per 2 horizontal of the graded edge (unchanged where it already is). */
    public static double groundFor(Hit h, double hNatural) { if (Double.isNaN(h.z)) return hNatural; if (h.kind != Kind.SLOPE) return h.z; double m = h.du / SLOPE_HV; return Math.max(h.z - m, Math.min(h.z + m, hNatural)); }
    /** Where a coordinate falls: {index, offset}: blocks 0, 1, ... outward on the + side and -1, -2, ... on the - side, offset from the block's inner edge; streets as index Integer.MIN_VALUE + k (k = 0 the central one), offset from the street's centre line. */
    static double[] seg(double a, double half, double B, double S) {
        double m = Math.abs(a), sg = Math.signum(a); if (m < half) return new double[]{Integer.MIN_VALUE, a, 0};
        double r = m - half, P = B + S; int k = (int) Math.floor(r / P); double wv = r - k * P;
        if (wv < B) return new double[]{sg >= 0 ? k : -1 - k, wv, 1};
        double off = wv - B - S / 2; return new double[]{Integer.MIN_VALUE + k + 1, off * sg, 0, sg >= 0 ? k : -1 - k}; }
    Hit classify(Plan p, double u, double v) {
        for (double[] z : p.zones) if (Math.abs(u - z[0]) <= z[2] && Math.abs(v - z[1]) <= z[3]) { Hit h = new Hit(); h.kind = Kind.ZONE; h.use = Use.values()[(int) z[4]]; h.u = u; h.v = v; h.plan = p; return h; }
        double[] a = seg(u, STREET_W / 2, BLOCK_U, STREET_W), b = seg(v, MAIN_W / 2, BLOCK_V, STREET_W); boolean inU = a[2] == 1, inV = b[2] == 1;
        if (inU && inV) { int i = (int) a[0], j = (int) b[0]; Use bu0 = p.blocks.get(key(i, j)); if (bu0 == null) return null;
            Hit h = new Hit(); h.plan = p; h.u = u; h.v = v; h.bi = i; h.bj = j; h.du = a[1]; h.dv = b[1];
            if (bu0 == Use.SCHOOL || bu0 == Use.GRAIN_ELEVATOR) { h.kind = Kind.LOT; h.use = bu0; return h; }
            if (b[1] >= LOT_D && b[1] < LOT_D + ALLEY_W) { h.kind = Kind.ALLEY; return h; }
            h.kind = Kind.LOT; h.row = b[1] < LOT_D ? 0 : 1; boolean store = bu0 == Use.STORE && h.row == 0; double lw = store ? LOT_BIZ : LOT_RES; h.lot = Math.min((int) (a[1] / lw), store ? 11 : 5);
            Use lu = p.lots.get(lotKey(i, j, h.row, h.lot)); h.use = lu != null ? lu : store ? Use.STORE : Use.HOUSE; return h; }
        // a street: drawn where it touches a used block (both sides of it, and the four round a crossing)
        List<long[]> adj = new ArrayList<>();
        int[] us = inU ? new int[]{(int) a[0]} : sides(a), vs = inV ? new int[]{(int) b[0]} : sides(b);
        boolean any = false; for (int i : us) for (int j : vs) if (p.blocks.containsKey(key(i, j))) any = true; if (!any) return null;
        Hit h = new Hit(); h.plan = p; h.u = u; h.v = v;
        if (!inV) { boolean main = b[0] == Integer.MIN_VALUE; h.kind = main ? Kind.MAIN_STREET : Kind.STREET; h.dv = b[1]; h.du = inU ? 1e9 : a[1]; return h; }   // along Main Street's direction (or a crossing)
        h.kind = Kind.STREET; h.du = a[1]; h.dv = 1e9; return h; }
    /** The blocks either side of a street index from seg(). */
    static int[] sides(double[] r) { int k = (int) (r[0] - Integer.MIN_VALUE); if (k == 0) return new int[]{0, -1}; int b0 = (int) r[3]; return b0 >= 0 ? new int[]{b0, b0 + 1} : new int[]{b0, b0 - 1}; }
    /** The surface of a town column: null where the ground stays as it is (a lawn). */
    public static Column.Mat surface(Hit h) {
        if (h.linkD < PAVE / 2) return Column.Mat.ASPHALT;   // 0.25.2 a link's pavement runs on across the street's planting strip and sidewalk to the crossing
        switch (h.kind) {
            case SLOPE -> { return h.road ? (Math.abs(h.dv) < PAVE / 2 ? Column.Mat.ASPHALT : null) : null; }
            case ROAD -> { return Math.abs(h.dv) < PAVE / 2 ? Column.Mat.ASPHALT : null; }
            case ZONE -> { return Column.Mat.ROAD_CONCRETE; }
            case ALLEY -> { return Column.Mat.GRAVEL; }
            case MAIN_STREET -> { double d = Math.abs(h.dv); if (Math.abs(h.du) < 1e8) { double e = Math.abs(h.du); if (e < PAVE / 2) return Column.Mat.ASPHALT; } return d < MAIN_PAVE / 2 ? Column.Mat.ASPHALT : Column.Mat.ROAD_CONCRETE; }
            case STREET -> { double d = Math.min(Math.abs(h.du), Math.abs(h.dv)); if (d < PAVE / 2) return Column.Mat.ASPHALT; double e = d - PAVE / 2; return e >= STRIP && e < STRIP + WALK ? Column.Mat.ROAD_CONCRETE : null; }
            default -> { return switch (h.use) { case STORE, POST_OFFICE, FIRE_STATION, TOWN_HALL -> Column.Mat.ROAD_CONCRETE; case GRAIN_ELEVATOR -> Column.Mat.GRAVEL; default -> null; }; } } }
    public String probe() { long n = made.sum(); return String.format("towns: %,d plats made (%.1f ms each), %,d town columns, %,d graded", n, n == 0 ? 0 : planNs.sum() / 1e6 / n, COLS.sum(), GRADED.sum()); }
}
