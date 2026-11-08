package com.hopeless.infinity.planet;

/** 0.26.0 SUPERTALL TOWERS (megacities and large cities): one tower, every floor modelled, as block-state strings in the tower's own frame
 *  (x, z across, centred on the tower's axis; y up from the plaza level). Pure (no game classes): tools/TowerView renders it, the chunk
 *  generator places it. Owner, 2026-11-04: "every floor needs to be modeled and have a function"; "use a modified version of the us
 *  regulations"; towers past a mile, near the structural limit, normal skyscraper widths.
 *  PLAN: a buttressed core (STATED: Burj Khalifa and Jeddah Tower's structural system): a square hub round the core and four wings that
 *  brace it; the wings step back one after another as the tower rises (STATED: Burj Khalifa's setbacks spiral round its wings).
 *  - wing width 2 x LEASE_M + a 3 m corridor (LEASE_M = 13.7 m = 45 ft, CHOSEN from the usual US office core-to-glass depth of 40-45 ft),
 *    so no desk is far from a window however wide the tower's base is.
 *  - base span: height / BASE_RATIO (6:1, CHOSEN: under the 7:1 of the original World Trade Center, STATED) across the wing tips.
 *  - core: CORE_SHARE = 24 % of each floor plate (STATED: mean of 35 towers of 300 m or more, MDPI Buildings 2024), in the hub; two exit
 *    stairways at opposite corners (IBC 403.5.1: at least 30 ft apart), a third above 420 ft = 128 m (IBC 403.5.2), elevator shafts and a
 *    fire service access elevator lobby (IBC 3007, over 120 ft).
 *  - zones: 2 mechanical/refuge floors every MECH_EVERY = 15 floors (STATED: Shanghai Tower, Ping An), a sky lobby above each; a 2-floor
 *    transfer sky lobby every ROPE_M = 500 m (STATED: steel elevator rope reaches about 500 m per run).
 *  - uses, bottom to top (STATED order: Shanghai Tower, Ping An, Merdeka 118): retail podium, offices, hotel, homes, observation decks;
 *    the shares of the height CHOSEN from those towers.
 *  - floor-to-floor 4 m (STATED offices 3.0-4.3 m).
 *  - FAA AC 70/7460-1M: steady red lights at the wing tips every 150 ft = 45.7 m of height and on the mast (STATED). */
public final class Tower {
    public enum Use { RETAIL, OFFICE, HOTEL, HOME, MECHANICAL, SKY_LOBBY, OBSERVATION }
    public static final int FLOOR = 4, MECH_EVERY = 15, TIERS = 8, MULLION = 3, PODIUM_FLOORS = 6, CROWN_FLOORS = 4, MAST = 12;
    public static final int CORE_MIN = 7;   // 0.27.8 a 15 m core at least (CHOSEN: two stairways at opposite corners 30 ft apart, IBC 1007.1.1, and the fire service elevator lobby)
    public static final double LEASE_M = 13.7, CORRIDOR_M = 3, CORE_SHARE = 0.24, BASE_RATIO = 6, FAA_LIGHT_M = 45.72, ROPE_M = 500;
    public final int height, floors, wingHalf, hubHalf, podiumHalf, seed; final Use[] use; final int[][] arm; final int[] core; int stairCore;   // per floor: use, 4 arm lengths (+x, -x, +z, -z), core half
    public final String name;

    public Tower(String name, int heightM, int seed) {
        this.name = name; this.seed = seed; floors = Math.max(10, heightM / FLOOR - CROWN_FLOORS); height = floors * FLOOR;
        wingHalf = (int) Math.round(LEASE_M + CORRIDOR_M / 2); int span = (int) Math.round(heightM / BASE_RATIO / 2);
        form = Form.values()[h(seed, 9, 0, 0) % Form.values().length];
        tc = new double[floors]; ts = new double[floors]; for (int f = 0; f < floors; f++) { double th = Math.toRadians(TWIST_DEG) * Math.max(0, f - PODIUM_FLOORS) / Math.max(1, floors - PODIUM_FLOORS); tc[f] = Math.cos(th); ts[f] = Math.sin(th); }
        round = h(seed, 7, 0, 0) % 2 == 0; cutW = Math.max(1, (int) Math.round((round ? 0.10 : 0.075) * (2 * wingHalf + 1)));
        use = new Use[floors]; arm = new int[floors][4]; core = new int[floors];
        // the hub: the core plus a lease depth all round; the core 24 % of the lowest tower floor's area (sized there, carried up: shafts run the full height)
        int c0 = 6; int hh = 0; for (int it = 0; it < 20; it++) { hh = c0 + (int) Math.round(LEASE_M); double area = 4.0 * hh * hh + 4.0 * Math.max(0, span - hh) * 2 * wingHalf; c0 = (int) Math.ceil(Math.sqrt(CORE_SHARE * area) / 2); }
        hubHalf = Math.max(hh, wingHalf + 2); podiumHalf = Math.max(span, hubHalf) + 8;
        int sinceMech = 0; double lastTransfer = 0; int[] order = {0, 2, 1, 3};
        for (int f = 0; f < floors; f++) { double s = f / (double) floors;
            // setbacks: the wings shorten one after another, TIERS times each, to the hub at 92 % of the height
            int steps = (int) Math.floor(Math.min(1, s / 0.92) * TIERS * 4);
            for (int w = 0; w < 4; w++) { int done = steps / 4 + (steps % 4 > indexOf(order, w) ? 1 : 0); arm[f][w] = hubHalf + (int) Math.round((span - hubHalf) * Math.max(0, 1 - done / (double) TIERS)); }
            if (f < PODIUM_FLOORS) { use[f] = Use.RETAIL; continue; }
            double yM = f * FLOOR;
            if (yM - lastTransfer >= ROPE_M && f + 1 < floors) { use[f] = use[f + 1] = Use.SKY_LOBBY; arm[f + 1] = arm[f].clone(); lastTransfer = yM; sinceMech = 0; f++; continue; }
            if (sinceMech >= MECH_EVERY && f + 2 < floors) { use[f] = use[f + 1] = Use.MECHANICAL; use[f + 2] = Use.SKY_LOBBY; arm[f + 1] = arm[f].clone(); arm[f + 2] = arm[f].clone(); sinceMech = 0; f += 2; continue; }
            sinceMech++; use[f] = s < 0.55 ? Use.OFFICE : s < 0.72 ? Use.HOTEL : s < 0.94 ? Use.HOME : Use.OBSERVATION; }
        // 0.27.8 the core per floor: 24 % of THAT floor's plate (the same STATED share), never growing upward and never under CORE_MIN; the
        // elevator banks end at the sky lobbies as the plate shrinks (measured before: one core sized at the base and carried up filled 51 % of a
        // 1650 m tower's top plate, 71 of 99 m); the stairways keep one place all the way up (inside the top floor's core), as IBC 1023 shafts must
        int c = Math.min(c0, hubHalf - 6); for (int f = 0; f < floors; f++) { double area = 0; int[] a = arm[f]; area = (2.0 * hubHalf + 1) * (2.0 * hubHalf + 1); for (int w = 0; w < 4; w++) area += Math.max(0, a[w] - hubHalf) * (2.0 * wingHalf + 1);
            int want = (int) Math.ceil(Math.sqrt(CORE_SHARE * area) / 2); c = Math.max(CORE_MIN, Math.min(c, want)); core[f] = c; }
        stairCore = core[floors - 1];
    }
    static int indexOf(int[] a, int v) { for (int i = 0; i < a.length; i++) if (a[i] == v) return i; return 0; }
    public Use use(int floor) { return floor < 0 || floor >= floors ? null : use[floor]; }
    public int reach() { return podiumHalf + 1; }
    public int top() { return height + CROWN_FLOORS * FLOOR + MAST; }
    public int span(int floor) { int[] a = arm[Math.max(0, Math.min(floors - 1, floor))]; return Math.max(Math.max(a[0], a[1]), Math.max(a[2], a[3])) * 2 + 1; }

    /** Inside the floor plate of floor f. */
    boolean inside(int f, int x, int z) { return rect(f, x, z) && cornerCut(f, x, z) <= 0; }   // the cut line itself is part of the plate (its shaped blocks)
    /** 0.26.3 WIND SHAPING (owner, 2026-11-04: "a lot of [super towers] have weird shapes ... to combat wind"): every convex corner of the
     *  plate is chamfered or rounded. Measured in wind tunnels (MDPI Appl. Sci. 13(2):1049 and 12(19):9739): a chamfer of 7.5 % of the width
     *  cut the vortex-induced sway by 60-70 %, the best of 2.5-12.5 %; rounding 10 % of the width cut the cross-wind base moment by 51 %.
     *  So: CHAMFER = 7.5 % or ROUND = 10 % of the face's width (one or the other a tower, by its seed), at least 1 block. Returns, for a
     *  cell on a corner's cut, the cut's depth p + q relative to the cut line: -1 not cut; 0 on the line (a shaped block); 1 outside. */
    int cornerCut(int f, int x, int z) {
        if (form == Form.TWIST) return -1;   // the twist turns the plan: its corners stay square
        if (f < PODIUM_FLOORS) return -1;
        { int c0 = form == Form.NOTCHED ? Math.max(2, (int) Math.round(0.10 * (2 * wingHalf + 1))) : cutW; if (Math.abs(z) <= wingHalf - c0 && Math.abs(x) <= wingHalf - c0) return -1;   // 0.27.0 measured (JFR): cornerCut was the HQ chunk's hot spot; a corner is
          if (Math.abs(z) > wingHalf && Math.abs(x) > wingHalf) return -1; }                                                                                         //   within c of a wing's side line (|z| or |x| near wingHalf)
        if (f >= PODIUM_FLOORS && form == Form.NOTCHED) { int[] a = arm[f]; int c = Math.max(2, (int) Math.round(0.10 * (2 * wingHalf + 1)));
            int[][] cs = {{a[0], wingHalf, 1, 1}, {a[0], -wingHalf, 1, -1}, {-a[1], wingHalf, -1, 1}, {-a[1], -wingHalf, -1, -1}, {wingHalf, a[2], 1, 1}, {-wingHalf, a[2], -1, 1}, {wingHalf, -a[3], 1, -1}, {-wingHalf, -a[3], -1, -1}};
            for (int[] k : cs) { int p = (k[0] - x) * k[2], q = (k[1] - z) * k[3]; if (p < 0 || q < 0) continue; if ((p < c && q < c / 2) || (p < c / 2 && q < c)) return 1; }   // the double step (two recessions) at each corner
            return -1; }
        if (f >= PODIUM_FLOORS) { int[] a = arm[f]; int c = cutW;
            int[][] cs = {{a[0], wingHalf, 1, 1}, {a[0], -wingHalf, 1, -1}, {-a[1], wingHalf, -1, 1}, {-a[1], -wingHalf, -1, -1}, {wingHalf, a[2], 1, 1}, {-wingHalf, a[2], -1, 1}, {wingHalf, -a[3], 1, -1}, {-wingHalf, -a[3], -1, -1}};
            for (int[] k : cs) { int p = (k[0] - x) * k[2], q = (k[1] - z) * k[3]; if (p < 0 || q < 0 || p >= c || q >= c) continue; return round ? (Math.hypot(c - 0.5 - p, c - 0.5 - q) > c - 0.25 ? (Math.hypot(c - 0.5 - p, c - 0.5 - q) > c + 0.6 ? 1 : 0) : -1) : (p + q < c - 1 ? 1 : p + q == c - 1 ? 0 : -1); } }
        return -1; }
    /** The shaped block on a corner's cut at (x, z): its shape id and turn (solid toward the inside), or null. */
    String cutShape(int f, int x, int z, String material) {
        int[] a = arm[Math.max(0, Math.min(floors - 1, f))]; int c = cutW;
        int[][] cs = {{a[0], wingHalf, 1, 1}, {a[0], -wingHalf, 1, -1}, {-a[1], wingHalf, -1, 1}, {-a[1], -wingHalf, -1, -1}, {wingHalf, a[2], 1, 1}, {-wingHalf, a[2], -1, 1}, {wingHalf, -a[3], 1, -1}, {-wingHalf, -a[3], -1, -1}};
        for (int[] k : cs) { int p = (k[0] - x) * k[2], q = (k[1] - z) * k[3]; if (p < 0 || q < 0 || p >= c || q >= c) continue;
            int o = com.hopeless.infinity.slope.ShapeLib.orient(new int[]{-k[2], 0, 0}, new int[]{0, 0, -k[3]}); return "shape:" + (round ? "round_slope" : "slope") + ":" + o + ":" + material; }
        return null; }
    public final boolean round; public final int cutW;
    /** 0.26.3 the tower's form family, each from a measured wind-shaping result (research note 2026-11-04):
     *  SPIRAL   - Burj Khalifa: wings that step back one after another ("the wind vortices never get organized", SOM), rounded or chamfered tips;
     *  TWIST    - Shanghai Tower: the plan turns 120 degrees over the height (76 % of a tapered box's wind load, Thornton Tomasetti studies);
     *  NOTCHED  - corner recessions of 10 % of the width (cross-wind base moment -42 %, MDPI 12(19):9739; Taipei 101's notches: -25 %);
     *  WINDFLOOR - 432 Park Avenue: the mechanical floors left open so the wind passes through (5 open double floors, STATED). */
    public enum Form { SPIRAL, TWIST, NOTCHED, WINDFLOOR }
    public final Form form; public static final double TWIST_DEG = 120; final double[] tc, ts;
    static int h(int seed, int a, int b, int c) { long x = seed * 0x9E3779B97F4A7C15L + a * 0xC2B2AE3D27D4EB4FL + b * 0x165667B19E3779F9L + c * 0x27D4EB2F165667C5L; x ^= x >>> 31; x *= 0x7FB5D329728EA185L; x ^= x >>> 27; return (int) (x >>> 33); }
    boolean rect(int f, int x, int z) {
        if (f < PODIUM_FLOORS) return Math.abs(x) <= podiumHalf && Math.abs(z) <= podiumHalf;
        int[] a = arm[f]; if (Math.abs(x) <= hubHalf && Math.abs(z) <= hubHalf) return true;
        if (Math.abs(z) <= wingHalf && (x > 0 ? x <= a[0] : -x <= a[1])) return true;
        return Math.abs(x) <= wingHalf && (z > 0 ? z <= a[2] : -z <= a[3]); }
    /** Distance in from the nearest facade (straight lines along x and z), and the axis it runs along (0: facade faces z, 1: faces x). */
    int[] depth(int f, int x, int z) {   // the plate is a union of rectangles: the run through the point along x and along z, analytically
        // 0.26.2 in a wing the rooms run off its long sides only (the tip is one end room): measured, the nearest-facade rule cut the rooms
        // near the tips into crosses
        if (f >= PODIUM_FLOORS) { if (Math.abs(z) <= wingHalf && Math.abs(x) > hubHalf) return new int[]{wingHalf - Math.abs(z), 0, z < 0 ? 3 : 2};
            if (Math.abs(x) <= wingHalf && Math.abs(z) > hubHalf) return new int[]{wingHalf - Math.abs(x), 1, x < 0 ? 1 : 0}; }
        int[] rx = run(f, z, 0), rz = run(f, x, 1); int dx = Math.min(x - rx[0], rx[1] - x), dz = Math.min(z - rz[0], rz[1] - z);
        // {depth, facade axis (1: the facade faces x), outward direction: 0 east, 1 west, 2 south, 3 north}
        return dx <= dz ? new int[]{dx, 1, x - rx[0] < rx[1] - x ? 1 : 0} : new int[]{dz, 0, z - rz[0] < rz[1] - z ? 3 : 2}; }
    static final String[] DIR = {"east", "west", "south", "north"}; static int back(int o) { return o ^ 1; }
    /** The plate's extent along x at this z (axis 0), or along z at this x (axis 1). */
    int[] run(int f, int c, int axis) { if (f < PODIUM_FLOORS) return new int[]{-podiumHalf, podiumHalf}; int[] a = arm[f]; int lo = axis == 0 ? 1 : 3, hi = axis == 0 ? 0 : 2;
        if (Math.abs(c) <= wingHalf) return new int[]{-a[lo], a[hi]}; if (Math.abs(c) <= hubHalf) return new int[]{-hubHalf, hubHalf}; return new int[]{-wingHalf, wingHalf}; }
    /** The block at (x, y, z) in the tower's frame, or null (outside the tower). Inside, air is "air". */
    /** The block at world (x, y, z) in the tower's frame: for a TWIST tower the floor's plan is turned (TWIST_DEG over the height) and a cell
     *  is on the facade when a world neighbour is outside the turned plan; the rest is the unturned plan's rooms at the turned point. */
    public String at(int x, int y, int z) {
        if (y < 0) return null;
        if (y >= height) { if (form != Form.TWIST) return atLocal(x, y, z, null); double th = Math.toRadians(TWIST_DEG) * (floors - 1 - PODIUM_FLOORS) / Math.max(1, floors - PODIUM_FLOORS);   // the crown turned with the top floor
            return atLocal((int) Math.round(x * Math.cos(th) + z * Math.sin(th)), y, (int) Math.round(-x * Math.sin(th) + z * Math.cos(th)), null); }
        int f = y / FLOOR; if (form == Form.TWIST && f >= PODIUM_FLOORS) { int C = core[Math.min(f, floors - 1)]; if (Math.abs(x) <= C && Math.abs(z) <= C) return atLocal(x, y, z, false); }   // 0.27.8 the core does not turn (STATED: Shanghai Tower's core is straight, its facade turns), so its shafts and stairs stack
        int[] m = LAST.get();   // 0.26.3 the (turned) point and its facade test, made once a floor a column (the generator asks 4 heights in a row)
        if (m[0] != x || m[1] != z || m[2] != f || m[6] != seed || m[7] != height) { boolean turn = form == Form.TWIST && f >= PODIUM_FLOORS; double c = turn ? tc[f] : 1, sn = turn ? ts[f] : 0;   // 0.27.0 every form (measured: the untwisted forms skipped the cache and cost about 1 us a block, 311,860 blocks in 300 ms in the HQ's chunk)
            int lx = turn ? (int) Math.round(x * c + z * sn) : x, lz = turn ? (int) Math.round(-x * sn + z * c) : z; boolean in = inside(f, lx, lz), edge = false;
            if (in) for (int[] d : NB) { int nx = x + d[0], nz = z + d[1]; if (!inside(f, turn ? (int) Math.round(nx * c + nz * sn) : nx, turn ? (int) Math.round(-nx * sn + nz * c) : nz)) { edge = true; break; } }
            m[0] = x; m[1] = z; m[2] = f; m[3] = lx; m[4] = lz; m[5] = in ? (edge ? 2 : 1) : 0; m[6] = seed; m[7] = height; m[8] = Integer.MIN_VALUE; }
        if (m[5] == 0) return null; return atLocal(m[3], y, m[4], m[5] == 2); }
    static final int[][] NB = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}; static final ThreadLocal<int[]> LAST = ThreadLocal.withInitial(() -> new int[]{Integer.MIN_VALUE, 0, 0, 0, 0, 0, 0, 0, Integer.MIN_VALUE, 0, 0});   // x, z, floor, local x, local z, in/edge, seed, height, 0.27.0 the room depth {d, axis, outward} (8..10)
    String atLocal(int x, int y, int z, Boolean edgeIn) {
        if (y < 0) return null; if (y >= height) return crown(x, y - height, z);
        int f = y / FLOOR, k = y % FLOOR; Use u = use[f]; if (!inside(f, x, z)) return null;
        boolean edge = edgeIn != null ? edgeIn : !inside(f, x + 1, z) || !inside(f, x - 1, z) || !inside(f, x, z + 1) || !inside(f, x, z - 1);
        if (f >= PODIUM_FLOORS && rect(f, x, z) && cornerCut(f, x, z) == 0) return cutShape(f, x, z, u == Use.MECHANICAL ? "iron_block" : k == 0 ? "gray_concrete" : "light_blue_stained_glass");   // the chamfered or rounded corner
        if (edge && f == 0 && k >= 1 && k <= 3) { String dr = entrance(x, k, z); if (dr != null) return dr; }   // 0.27.0 the street entrances
        if (edge) { boolean tip = isTip(f, x, z); if (tip && Math.floorMod(y, (int) Math.round(FAA_LIGHT_M)) == 2) return "red_stained_glass";
            if (u == Use.MECHANICAL && form == Form.WINDFLOOR && k > 0) { int t0 = (!inside(f, x + 1, z) || !inside(f, x - 1, z)) ? z : x; return Math.floorMod(t0, 6) == 0 || isTip(f, x, z) ? "gray_concrete" : "air"; }   // the open wind floor: columns only
            if (u == Use.MECHANICAL) return k == 0 ? "gray_concrete" : "iron_bars";
            if (k == 0) return u == Use.RETAIL ? "polished_andesite" : "gray_concrete";
            int t = (!inside(f, x + 1, z) || !inside(f, x - 1, z)) ? z : x; if (Math.floorMod(t, MULLION) == 0) return "light_gray_concrete";
            return u == Use.RETAIL ? "glass" : "light_blue_stained_glass"; }
        // the roof of the podium where the tower above is narrower
        if (k == 0 && f > 0 && !inside(f - 1, x, z)) return "smooth_stone";
        if (f < floors - 1 && k == FLOOR - 1 && !inside(f + 1, x, z) && f + 1 < floors) return "smooth_stone";   // roof under a setback (a terrace above)
        if ((k == 0 || k == FLOOR - 1) && inShaft(x, z, f)) return core(x, k, z, core[Math.min(f, floors - 1)], f);   // 0.27.8 the stairway shafts run through the slabs
        if (k == 0) return slab(u);
        if (k == FLOOR - 1) return Math.floorMod(x, 6) == 3 && Math.floorMod(z, 6) == 3 ? (u == Use.MECHANICAL ? "glowstone" : "sea_lantern") : "smooth_quartz";   // ceiling, lights on a 6 m grid (CHOSEN)
        int C = core[Math.min(f, floors - 1)]; if (Math.abs(x) <= C && Math.abs(z) <= C) return core(x, k, z, C, f);
        int[] m = LAST.get(); int d0, d1, d2; if (edgeIn != null && m[3] == x && m[4] == z && m[2] == f) { if (m[8] == Integer.MIN_VALUE) { int[] dp = depth(f, x, z); m[8] = dp[0]; m[9] = dp[1]; m[10] = dp[2]; } d0 = m[8]; d1 = m[9]; d2 = m[10]; }   // 0.27.0 the depth once a floor a column
        else { int[] dp = depth(f, x, z); d0 = dp[0]; d1 = dp[1]; d2 = dp[2]; }
        return room(u, x, k, z, d0, d1, d2, C, f); }
    boolean isTip(int f, int x, int z) { if (f < PODIUM_FLOORS) return Math.abs(x) == podiumHalf && Math.abs(z) == podiumHalf; int[] a = arm[f];
        return (Math.abs(z) == wingHalf && (x == a[0] || -x == a[1])) || (Math.abs(x) == wingHalf && (z == a[2] || -z == a[3])); }

    /** 0.27.0 ENTRANCES (owner, 2026-11-04: "spawn me close to the front door"): on the ground floor, in the middle of each podium face, a row
     *  of ENTRANCE_DOORS glass-and-birch doors (IBC 1010.1.1: at least 32 in clear, 80 in high: one block wide, two high), a transom of glass
     *  above. The front (-z, toward the plaza) has FRONT_DOORS. Null: not an entrance cell. */
    public static final int ENTRANCE_DOORS = 3, FRONT_DOORS = 5;
    String entrance(int x, int k, int z) { int ph = podiumHalf; String face; int along;
        if (z == -ph) { face = "north"; along = x; } else if (z == ph) { face = "south"; along = x; } else if (x == ph) { face = "east"; along = z; } else if (x == -ph) { face = "west"; along = z; } else return null;
        int n = face.equals("north") ? FRONT_DOORS : ENTRANCE_DOORS; if (Math.abs(along) > n / 2) return null;
        if (k == 3) return "glass";
        boolean left = Math.floorMod(along, 2) == 0; return "birch_door[facing=" + face + ",half=" + (k == 1 ? "lower" : "upper") + ",hinge=" + (left ? "left" : "right") + ",open=false]"; }
    /** 0.27.0 the front door's cell in the tower frame (ground floor, outside face): {x, z}. */
    public int[] frontDoor() { return new int[]{0, -podiumHalf}; }

    static String slab(Use u) { switch (u) {
        case RETAIL: return "polished_diorite"; case OFFICE: return "gray_carpet"; case HOTEL: return "red_carpet"; case HOME: return "oak_planks";
        case MECHANICAL: return "smooth_stone"; case SKY_LOBBY: return "white_concrete"; default: return "polished_deepslate"; } }

    /** The core: concrete walls with doors, two (three over 420 ft) stairways at opposite corners, elevator shafts on the middle line, the lobby. */
    String core(int x, int k, int z, int C, int f) {
        int ax = Math.abs(x), az = Math.abs(z);
        if (Math.max(ax, az) == C) return (ax <= 1 || az <= 1) && k <= 2 ? "air" : "white_concrete";
        int S = stairCore, st = Math.max(3, S / 3); boolean a = x <= -S + st && x > -S - 1 && z <= -S + st && z > -S - 1, b = x >= S - st && x < S + 1 && z >= S - st && z < S + 1, c = f * FLOOR > 128 && x >= S - st && x < S + 1 && z <= -S + st && z > -S - 1;   // 0.27.8 the stairways at the top core's corners on every floor
        if (a || b || c) { int lx = a ? x + S : x - (S - st), lz = (a || c) ? z + S : z - (S - st);
            if (lx <= 0 || lz <= 0 || lx >= st || lz >= st) { boolean innerX = a ? lx == st : lx == 0, innerZ = (a || c) ? lz == st : lz == 0;   // 0.27.8 a door into the core on each inner wall, every floor
                if ((k == 1 || k == 2) && ((innerX && lz == st / 2) || (innerZ && lx == st / 2))) return "air"; return "stone_bricks"; }
            return stairShaft(lx, lz, f * FLOOR + k, st - 1); }
        if (az <= 1) return Math.floorMod(x, 3) == 0 ? "iron_block" : "air";   // elevator cars (iron) in their shafts
        if (az == 2) return k <= 2 && Math.floorMod(x, 3) != 0 ? "air" : "light_gray_concrete";   // lobby wall, open at the doors
        return "air"; }

    /** 0.27.8 a stairway shaft that climbs: the steps run round the shaft's edge cells, one block up per cell (a stair's facing is the way up),
     *  the middle a column; at each floor's level a landing on every edge cell the steps do not pass through or under (headroom 2 blocks:
     *  a landing is never where a step is, nor 1 or 2 blocks over one). Measured before: the floor and ceiling slabs ran through the shafts
     *  and their walls had no doors, so no stairway could be climbed. n: the shaft's inside width (cells 1..n). */
    boolean inShaft(int x, int z, int f) { int S = stairCore, st = Math.max(3, S / 3); boolean a = x < -S + st && x > -S && z < -S + st && z > -S, b = x > S - st && x < S && z > S - st && z < S, c = f * FLOOR > 128 && x > S - st && x < S && z < -S + st && z > -S; return a || b || c; }
    static String stairShaft(int lx, int lz, int y, int n) { if (n < 2) return "ladder[facing=north]";
        boolean rim = lx == 1 || lz == 1 || lx == n || lz == n; if (!rim) return "stone_bricks";
        int P = 4 * (n - 1), i = rimIndex(lx, lz, n); int h = Math.floorMod(y - i, P);
        if (h == 0) { int[] nx = rimCell((i + 1) % P, n); int dx = nx[0] - lx, dz = nx[1] - lz; return "stone_brick_stairs[facing=" + (dx > 0 ? "east" : dx < 0 ? "west" : dz > 0 ? "south" : "north") + "]"; }
        if (Math.floorMod(y, FLOOR) == 0 && h > 2) return "smooth_stone";   // the landing
        return "air"; }
    static int rimIndex(int lx, int lz, int n) { if (lz == 1) return lx - 1; if (lx == n) return (n - 1) + (lz - 1); if (lz == n) return 2 * (n - 1) + (n - lx); return 3 * (n - 1) + (n - lz); }
    static int[] rimCell(int i, int n) { int e = n - 1; if (i < e) return new int[]{1 + i, 1}; if (i < 2 * e) return new int[]{n, 1 + (i - e)}; if (i < 3 * e) return new int[]{n - (i - 2 * e), n}; return new int[]{1, n - (i - 3 * e)}; }
    /** The rooms between the core and the facade, by use. d: distance in from the facade; ax: the facade's direction (t runs along it). */
    /** 0.26.2 the rooms between the core and the facade, by use, furnished with the blocks ported from Hopeless Deep (desks, chairs,
     *  computers, beds, kitchens, bathrooms). d: distance in from the facade; ax: the facade's axis (t runs along it); o: outward (OUT
     *  faces the glass, IN faces the core). Furniture spacing CHOSEN; the bays: hotel rooms 5 m, apartments 8 m wide. */
    String room(Use u, int x, int k, int z, int d, int ax, int o, int C, int f) {
        int t = ax == 1 ? z : x; int fromCore = Math.max(Math.abs(x), Math.abs(z)) - C; String OUT = DIR[o], IN = DIR[back(o)], ALONG = ax == 1 ? "south" : "east";
        String NS = "hopelessinfinity:";
        switch (u) {
            case RETAIL: { int r = Math.floorMod(t, 10);
                if (d == 9) return r == 0 ? "white_concrete" : "glass_pane"; if (d < 9 && r == 0) return "white_concrete";   // shop fronts and party walls
                if (d == 4 && k == 1 && r >= 2 && r <= 6) return r == 4 ? NS + "cash_register[facing=" + IN + "]" : "oak_slab";   // counters, the register
                if (d == 1 && k <= 2 && r >= 2 && r <= 7) return k == 1 ? NS + "drink_cooler[facing=" + IN + ",half=lower,stock=1]" : NS + "drink_cooler[facing=" + IN + ",half=upper,stock=1]";   // coolers along the back wall
                if (d > 12 && k == 1 && Math.floorMod(x * 7 + z * 3, 23) == 0) return "potted_azalea_bush"; return "air"; }
            case OFFICE: {
                if (fromCore >= 1 && fromCore <= 5 && d > 6) { if (fromCore == 5) return "glass_pane";   // meeting rooms on the core
                    int r = Math.floorMod(t, 8); if (k == 1 && fromCore == 3 && r >= 2 && r <= 5) return NS + "table[facing=" + ALONG + "]"; if (k == 1 && (fromCore == 2 || fromCore == 4) && r >= 2 && r <= 5) return NS + "chair[facing=" + (fromCore == 2 ? IN : OUT) + "]";
                    if (r == 0) return "white_concrete"; return "air"; }
                if (d >= 2 && d <= (int) LEASE_M && Math.floorMod(t, 6) != 0) { int m3 = Math.floorMod(d, 3);   // desk rows along the glass, 3 m apart
                    if (m3 == 0 && k == 1) return NS + "desk[facing=" + IN + "]"; if (m3 == 0 && k == 2 && Math.floorMod(t, 2) == 0) return NS + "computer[facing=" + IN + ",on=true]";
                    if (m3 == 1 && k == 1) return NS + "chair[facing=" + OUT + "]"; }
                if (d == 1 && k == 1 && Math.floorMod(t, 12) == 6) return NS + "supply_cabinet[facing=" + IN + "]";
                return "air"; }
            case HOTEL: case HOME: { int bay = u == Use.HOTEL ? 5 : 8; int corr = (int) LEASE_M; int r = Math.floorMod(t, bay);
                if (d == corr) return r == bay / 2 && k <= 2 ? "air" : "white_concrete";   // corridor wall with a door to each room
                if (d > corr) return d <= corr + 2 ? (k == 1 && d == corr + 1 && Math.floorMod(t, 15) == 7 ? NS + "fire_extinguisher[facing=" + OUT + ",charges=3]" : "air") : (r == 0 ? "white_concrete" : "air");
                if (r == 0) return "white_concrete";   // party walls
                if (d == corr - 3 && r >= bay / 2 - 1 && r <= bay / 2 + 1 && r != bay / 2) return "white_concrete";   // the bathroom wall by the door
                if (k == 1 && d > corr - 3 && d < corr) {   // the bathroom: toilet, sink, shower
                    if (r == 1) return NS + "toilet[facing=" + OUT + "]"; if (r == bay - 1) return NS + "shower[facing=" + OUT + ",running=false]"; if (d == corr - 1 && r == 2 && bay > 5) return NS + "sink[facing=" + OUT + "]"; return "air"; }
                if (u == Use.HOTEL) { if (k == 1) { if (d == 3 && r >= 1 && r <= 2) return "white_bed[facing=" + OUT + ",part=head]"; if (d == 4 && r >= 1 && r <= 2) return "white_bed[facing=" + OUT + ",part=foot]";
                        if (d == 7 && r == 1) return NS + "television[facing=" + OUT + ",on=false]"; if (d == 1 && r == 3) return NS + "chair[facing=" + OUT + "]"; if (d == 1 && r == 4) return NS + "desk[facing=" + IN + "]"; } return "air"; }
                // an apartment: living room at the glass, kitchen and dining in the middle, bedroom on the side
                if (k == 1) { if (d == 2 && r >= 2 && r <= 4) return NS + "sofa[facing=" + IN + "]"; if (d == 4 && r == 3) return NS + "coffee_table[facing=" + ALONG + "]"; if (d == 6 && r == 3) return NS + "television[facing=" + OUT + ",on=false]";
                    if (d == 8 && r >= 1 && r <= 2) return NS + "table[facing=" + ALONG + "]"; if (d == 9 && r >= 1 && r <= 2) return NS + "chair[facing=" + OUT + "]";
                    if (d == corr - 4 && r == 4) return NS + "stove[facing=" + OUT + ",lit=false]"; if (d == corr - 4 && r == 5) return NS + "sink[facing=" + OUT + "]"; if (d == corr - 4 && r == 6) return NS + "fridge[facing=" + OUT + "]";
                    if (d >= 2 && d <= 3 && r == 6) return "light_blue_bed[facing=" + ALONG + ",part=" + (d == 2 ? "head" : "foot") + "]"; }
                if (k == 2 && d == corr - 4 && r == 6) return NS + "fridge_top[facing=" + OUT + "]";
                return "air"; }
            case MECHANICAL: { if (Math.floorMod(t, 5) <= 1 && d >= 2 && d <= 6) return k <= 2 ? "iron_block" : "chain"; if (d == 8 && k == 2) return "iron_bars"; if (d == 10 && k == 1 && Math.floorMod(t, 4) == 0) return NS + "server_rack[facing=" + OUT + "]"; return "air"; }
            case SKY_LOBBY: { if (k == 1 && d >= 3 && Math.floorMod(x * 5 + z * 11, 13) == 0) return "potted_flowering_azalea_bush"; if (k == 1 && d == 2 && Math.floorMod(t, 4) != 0) return NS + "sofa[facing=" + IN + "]";
                if (d == 8 && Math.floorMod(t, 20) == 10 && k <= 2) return NS + "vending_machine[facing=" + OUT + ",half=" + (k == 1 ? "lower" : "upper") + ",stock=1]"; return "air"; }
            default: return d == 1 && k == 1 ? "iron_bars" : "air"; } }   // observation: a rail inside the glass

    /** The crown: CROWN_FLOORS stepped glass floors over the last plate's hub, then a mast, its tip lit red. */
    String crown(int x, int dy, int z) {
        int lh = CROWN_FLOORS * FLOOR, ax = Math.abs(x), az = Math.abs(z), m = Math.max(ax, az);
        if (dy < lh) { int hh = hubHalf - (dy / FLOOR) * Math.max(2, hubHalf / 5); if (m > hh) return null; if (m == hh) return dy % FLOOR == 0 ? "gray_concrete" : (Math.floorMod(ax == m ? z : x, MULLION) == 0 ? "light_gray_concrete" : "white_stained_glass"); return dy % FLOOR == 0 ? "smooth_stone" : "air"; }
        int md = dy - lh; if (md >= MAST || ax > 0 || az > 0) return null; return md == MAST - 1 ? "red_stained_glass" : "iron_block"; }

    /** 0.27.8 the core's share of floor f's plate (measured from the floor's arms). */
    public int stairCore() { return stairCore; }
    public double coreShare(int f) { int[] a = arm[f]; double area = (2.0 * hubHalf + 1) * (2.0 * hubHalf + 1); for (int w = 0; w < 4; w++) area += Math.max(0, a[w] - hubHalf) * (2.0 * wingHalf + 1); return (2.0 * core[f] + 1) * (2.0 * core[f] + 1) / area; }
    public String probe() { int[] n = new int[Use.values().length]; for (Use u : use) n[u.ordinal()]++; StringBuilder sb = new StringBuilder(String.format("%s: " + form + ", %d m (%d floors + crown), base span %d m, hub %d m, wings %d m wide, core %d m at the base to %d m at the top (%.0f %% to %.0f %% of the plate), top plate %d m;", name, top(), floors, span(PODIUM_FLOORS), 2 * hubHalf + 1, 2 * wingHalf + 1, 2 * core[0] + 1, 2 * core[floors - 1] + 1, 100 * coreShare(PODIUM_FLOORS), 100 * coreShare(floors - 1), span(floors - 1)));
        for (Use u : Use.values()) if (n[u.ordinal()] > 0) sb.append(' ').append(u.name().toLowerCase()).append(' ').append(n[u.ordinal()]); return sb.toString(); }
}
