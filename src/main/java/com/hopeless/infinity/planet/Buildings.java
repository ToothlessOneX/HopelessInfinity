package com.hopeless.infinity.planet;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 0.25.0 BUILDINGS on the town plats (stage 3, buildings; owner, 2026-11-03: build from real photographs, US rules). Pure Java: block-state
 * strings ("oak_stairs[facing=north,half=bottom,shape=straight]") the chunk generator parses and places, so tools/TownView draws the same.
 *
 * Method: a split grammar after CGA (Mueller, Wonka, Haegler, Ulmer, Van Gool 2006, "Procedural Modeling of Buildings", SIGGRAPH): a mass
 * on the lot, its facades split into floors, the floors into repeated tiles (window, door, display window). Each lot is drawn in its own
 * frame (u along the street front, v back from it, y up from the pad), so any chunk builds its own part of a building that crosses chunks.
 *
 * Grid: 1 block = 1 m. Floor to floor 4 blocks: IRC R305 wants a 7 ft (2.13 m) ceiling, which on a 1 m grid needs 3 clear blocks (CHOSEN).
 * Houses (residential lots 50 x 165 ft; front setback 25 ft, CHOSEN: a common US single-family front yard; side yards well over IRC R302's
 * 5 ft to the lot line):
 *  - American Foursquare (Wikipedia: two and a half storeys, boxy, four rooms a floor, hipped roof, centre dormer, full-width front porch
 *    with wide stairs): sizes from Sears, Roebuck "Modern Home No. 52" (Book of Modern Homes, 1908, via Wikimedia Commons): 27 ft 4 in wide,
 *    47 ft 10 in long without the porch (a 28 ft main block, the rest a rear kitchen wing: CHOSEN split from its plan's rooms, 11-14 ft).
 *  - Gable-front house (Indiana Landmarks, 19th-century vernacular: narrow, the gable end toward the street): 22 x 36 ft, 1.5 storeys (CHOSEN).
 *  - Bungalow (low, one and a half storeys, broad porch): 26 x 40 ft (CHOSEN).
 * Main Street (business lots 25 ft): the two-part commercial block (Longstreth, The Buildings of Main Street; facade parts from the
 * Washington DAHP and Indiana Landmarks "anatomy of a Main Street building" guides; checked against photographs of Main Street, Ames,
 * Iowa, Wikimedia Commons): party walls in a continuous street wall at the lot line; a storefront of bulkhead, display windows, recessed
 * entry, transom and sign band (6 blocks: CHOSEN); 1-3 upper floors (two to four floors in all: Indiana Landmarks) with three ranked windows
 * per 25 ft front; a bracketed cornice and parapet; 80 ft deep (CHOSEN), the rest of the lot a yard to the alley.
 * Public buildings and the rest: CHOSEN shapes for now, each labelled where it is built.
 */
public final class Buildings {
    public interface Sink { void set(int x, int y, int z, String block); }
    public static volatile boolean on = true;
    public static final java.util.concurrent.atomic.LongAdder LOTS = new java.util.concurrent.atomic.LongAdder(), BLOCKS = new java.util.concurrent.atomic.LongAdder(), NANOS = new java.util.concurrent.atomic.LongAdder();
    public static final double FT = Towns.FT, SETBACK = 25 * FT, STORE_D = 80 * FT;

    /** One lot in its own frame. */
    public static final class Lot {
        public final Towns.Plan p; public final int i, j, row, lot, W, D; public final Towns.Use use; public final long seed; public int y0; public int seaward = 1;   // spaceport areas: +1 when the sea (the launch direction) is toward +u
        final double u0, v0; final int su, sv; final Sink out; final int cx0, cz0, cx1, cz1; final Random r; public int set;
        Lot(Towns.Plan p, int i, int j, int row, int lot, Towns.Use use, double u0, int su, double v0, int sv, int W, int D, long seed, Sink out, int cx0, int cz0, int cx1, int cz1) {
            this.p = p; this.i = i; this.j = j; this.row = row; this.lot = lot; this.use = use; this.u0 = u0; this.su = su; this.v0 = v0; this.sv = sv; this.W = W; this.D = D; this.seed = seed; this.out = out;
            this.cx0 = cx0; this.cz0 = cz0; this.cx1 = cx1; this.cz1 = cz1; r = new Random(seed); }
        /** Plan coordinates of local block (a, b): block centres sit on whole plan metres. */
        double pu(int a) { return u0 + su * (a + 0.5); } double pv(int b) { return v0 + sv * (b + 0.5); }
        int wx(int a, int b) { double u = pu(a), v = pv(b); return (int) Math.floor((p.alongX ? p.cx + u : p.cx + v)); }
        int wz(int a, int b) { double u = pu(a), v = pv(b); return (int) Math.floor((p.alongX ? p.cz + v : p.cz + u)); }
        /** World direction name of a local direction: 0 front (toward the street: -v), 1 +u, 2 back (+v), 3 -u. */
        String dir(int local) { int du = 0, dv = 0; switch (local & 3) { case 0 -> dv = -1; case 1 -> du = 1; case 2 -> dv = 1; default -> du = -1; }
            int pu = su * du, pv = sv * dv; int dx = p.alongX ? pu : pv, dz = p.alongX ? pv : pu; return dx > 0 ? "east" : dx < 0 ? "west" : dz > 0 ? "south" : "north"; }
        void set(int a, int y, int b, String blk) { int x = wx(a, b), z = wz(a, b); if (x < cx0 || x >= cx1 || z < cz0 || z >= cz1) return; out.set(x, y0 + y, z, blk); set++; }
        void box(int a0, int y0_, int b0, int a1, int y1, int b1, String blk) { for (int y = y0_; y <= y1; y++) for (int b = b0; b <= b1; b++) for (int a = a0; a <= a1; a++) set(a, y, b, blk); }
        void walls(int a0, int y0_, int b0, int a1, int y1, int b1, String blk) { for (int y = y0_; y <= y1; y++) { for (int a = a0; a <= a1; a++) { set(a, y, b0, blk); set(a, y, b1, blk); } for (int b = b0; b <= b1; b++) { set(a0, y, b, blk); set(a1, y, b, blk); } } }
        String pick(String... o) { return o[r.nextInt(o.length)]; }
        String stairs(String mat, int facing, boolean top) { return mat + "[facing=" + dir(facing) + ",half=" + (top ? "top" : "bottom") + ",shape=straight]"; }
    }

    // ------------------------------------------------------------ the lots of a chunk
    /** Builds every lot of the plats that reach blocks x0..x1-1, z0..z1-1 of face f (clipped to that box). */
    public static void chunk(Towns tw, int f, int x0, int z0, int x1, int z1, double[] sCentre, Sink out) {
        if (!on || tw == null) return; long t0 = System.nanoTime();
        for (Towns.Plan p : tw.plansNear(tw.rv.cellOf(sCentre))) { if (p.face != f) continue;
            double[] a = p.uv(x0, z0), b = p.uv(x1, z1); double u0 = Math.min(a[0], b[0]) - 1, u1 = Math.max(a[0], b[0]) + 1, v0 = Math.min(a[1], b[1]) - 1, v1 = Math.max(a[1], b[1]) + 1;
            if (u1 < -p.extentM || u0 > p.extentM || v1 < -p.extentM || v0 > p.extentM) continue;
            for (int zi = 0; zi < p.zones.size(); zi++) { double[] zn = p.zones.get(zi); if (zn[0] + zn[2] < u0 || zn[0] - zn[2] > u1 || zn[1] + zn[3] < v0 || zn[1] - zn[3] > v1) continue;   // 0.25.1 spaceport areas
                double zu0 = zn[0] - zn[2], zv0 = zn[1] - zn[3]; double fu0 = first(zu0, 1), fv0 = first(zv0, 1); long seed = MoonTerrain.hash(tw.seed, 32, p.site.id, zi, 0);
                Lot L = new Lot(p, 0, 0, 0, zi, Towns.Use.values()[(int) zn[4]], fu0 - 0.5, 1, fv0 - 0.5, 1, count(zu0, 1, 2 * zn[2]), count(zv0, 1, 2 * zn[3]), seed, out, x0, z0, x1, z1);
                L.y0 = (int) Math.floor(Terrain.toYd(p.zoneZ.get(zi))) + 1; L.seaward = p.zones.get(0)[0] >= 0 ? 1 : -1; build(L); }
            for (var en : p.blocks.entrySet()) { long k = en.getKey(); int i = (int) (k >> 32), j = (int) k; double bu0 = Towns.bu(i), bv0 = Towns.bv(j);
                if (bu0 > u1 || bu0 + Towns.BLOCK_U < u0 || bv0 > v1 || bv0 + Towns.BLOCK_V < v0) continue; Towns.Use bu = en.getValue();
                if (bu == Towns.Use.SCHOOL || bu == Towns.Use.GRAIN_ELEVATOR) { build(lot(tw, p, i, j, 0, 0, bu, Towns.BLOCK_U, Towns.BLOCK_V, out, x0, z0, x1, z1)); continue; }
                for (int row = 0; row < 2; row++) { boolean store = bu == Towns.Use.STORE && row == 0; double lw = store ? Towns.LOT_BIZ : Towns.LOT_RES; int n = store ? 12 : 6;
                    for (int l = 0; l < n; l++) { Towns.Use u = p.lots.get(Towns.lotKey(i, j, row, l)); if (u == null) u = store ? Towns.Use.STORE : Towns.Use.HOUSE; build(lot(tw, p, i, j, row, l, u, lw, Towns.LOT_D, out, x0, z0, x1, z1)); } } } }
        NANOS.add(System.nanoTime() - t0); }
    /** A lot's frame: local u from the block's inner edge (nearer the centre), v from the lot's street. */
    static Lot lot(Towns tw, Towns.Plan p, int i, int j, int row, int l, Towns.Use use, double lw, double depth, Sink out, int x0, int z0, int x1, int z1) {
        int su = i >= 0 ? 1 : -1; double u0 = i >= 0 ? Towns.bu(i) + l * lw : Towns.bu(i) + Towns.BLOCK_U - l * lw;
        boolean inner = row == 0; int sv = (j >= 0) == inner ? 1 : -1; double v0 = inner ? (j >= 0 ? Towns.bv(j) : Towns.bv(j) + Towns.BLOCK_V) : (j >= 0 ? Towns.bv(j) + Towns.BLOCK_V : Towns.bv(j));
        // whole blocks whose centres (whole plan metres) fall inside the lot: the first such centre, and how many
        double fu0 = first(u0, su), fv0 = first(v0, sv); int W = count(u0, su, lw), D = count(v0, sv, depth);
        long seed = MoonTerrain.hash(tw.seed, 31, p.site.id, i * 64 + j, row * 64 + l);
        Lot L = new Lot(p, i, j, row, l, use, fu0 - su * 0.5, su, fv0 - sv * 0.5, sv, W, D, seed, out, x0, z0, x1, z1);
        double fu = L.pu(Math.max(0, L.W / 2)), fv = L.pv(0); L.y0 = (int) Math.floor(Terrain.toYd(Towns.gradedAt(p, fu, fv))) + 1; return L; }
    /** The first whole number at or past e going s (+1 or -1). */
    static double first(double e, int s) { return s > 0 ? Math.ceil(e - 1e-9) : Math.floor(e + 1e-9); }
    /** How many whole numbers lie from e going s for len (the end left out). */
    static int count(double e, int s, double len) { double c = first(e, s), used = Math.abs(c - e); return used >= len ? 0 : (int) Math.floor(len - used - 1e-9) + 1; }

    static void build(Lot L) {
        if (L.W <= 0 || L.D <= 0) return;
        // only a lot whose footprint (with 4 blocks for porches, cornices and eaves) reaches the chunk is drawn
        int xa = L.wx(-4, -4), xb = L.wx(L.W + 3, L.D + 3), za = L.wz(-4, -4), zb = L.wz(L.W + 3, L.D + 3); if (Math.max(xa, xb) < L.cx0 || Math.min(xa, xb) >= L.cx1 || Math.max(za, zb) < L.cz0 || Math.min(za, zb) >= L.cz1) return;
        LOTS.increment();
        switch (L.use) {
            case HOUSE -> { long h = L.seed >>> 11; int k = (int) Math.floorMod(h, 20); if (k < 9) foursquare(L); else if (k < 16) gableFront(L); else bungalow(L);
                if (Towns.forSale(L.p.site.id, L.i, L.j, L.row, L.lot)) { L.set(1, 1, 1, "hopelessinfinity:for_sale_sign[facing=" + L.dir(0) + ",sold=false]"); GAME_BLOCKS.increment(); } }   // 0.28.5 one house in eight for sale (game/Realty)
            case STORE -> store(L, 0);
            case POST_OFFICE -> { store(L, 1); L.set(L.W / 2 - 2, 1, -1, "hopelessinfinity:job_board[facing=" + L.dir(0) + "]"); GAME_BLOCKS.increment(); }   // 0.28.5 the town's job board on the sidewalk (game/Jobs)
            case FIRE_STATION -> store(L, 2);
            case TOWN_HALL -> { store(L, 3); L.set(L.W / 2 + 1, 1, 2, "hopelessinfinity:ballot_box[facing=" + L.dir(0) + "]"); GAME_BLOCKS.increment(); }   // 0.28.5 the ballot box inside the town hall (game/Council)
            case CHURCH -> church(L);
            case WATER_TOWER -> waterTower(L);
            case SUBSTATION -> substation(L);
            case SCHOOL -> school(L);
            case GRAIN_ELEVATOR -> elevator(L);
            case PAD -> launchSite(L);
            case PRODUCTION -> production(L);
            case DEPOT -> depot(L);
            default -> {} }
        BLOCKS.add(L.set); }

    // ------------------------------------------------------------ pieces
    /** The pad: foundation from 3 under the floor to the floor (the graded ground varies a little over a lot), air cleared above. */
    static void pad(Lot L, int a0, int b0, int a1, int b1, int top, String foundation) { L.box(a0, -3, b0, a1, 0, b1, foundation); L.box(a0, 1, b0, a1, top, b1, "air"); }
    static void door(Lot L, int a, int b, int y, String wood) { L.set(a, y, b, wood + "_door[facing=" + L.dir(2) + ",half=lower,hinge=left,open=false]"); L.set(a, y + 1, b, wood + "_door[facing=" + L.dir(2) + ",half=upper,hinge=left,open=false]"); }
    /** A hipped roof over a0..a1 x b0..b1 from height y: each layer a ring of stairs climbing inward, a slab ridge; 1 block of eaves (12:12 pitch on the 1 m grid). */
    static int hipped(Lot L, int a0, int b0, int a1, int b1, int y, String stairMat, String slab) {
        int lo_a = a0 - 1, hi_a = a1 + 1, lo_b = b0 - 1, hi_b = b1 + 1, k = 0;
        while (lo_a <= hi_a && lo_b <= hi_b) {
            if (lo_a == hi_a || lo_b == hi_b) { for (int a = lo_a; a <= hi_a; a++) for (int b = lo_b; b <= hi_b; b++) L.set(a, y + k, b, slab); k++; break; }
            for (int a = lo_a; a <= hi_a; a++) { L.set(a, y + k, lo_b, L.stairs(stairMat, 2, false)); L.set(a, y + k, hi_b, L.stairs(stairMat, 0, false)); }
            for (int b = lo_b + 1; b < hi_b; b++) { L.set(lo_a, y + k, b, L.stairs(stairMat, 1, false)); L.set(hi_a, y + k, b, L.stairs(stairMat, 3, false)); }
            for (int a = lo_a + 1; a < hi_a; a++) for (int b = lo_b + 1; b < hi_b; b++) if (a == lo_a + 1 || a == hi_a - 1 || b == lo_b + 1 || b == hi_b - 1) L.set(a, y + k, b, "air");
            lo_a++; hi_a--; lo_b++; hi_b--; k++; }
        return k; }
    /** A gable roof: ridge along u (alongU) or along v; gable ends filled with the wall. */
    static int gable(Lot L, int a0, int b0, int a1, int b1, int y, boolean alongU, String stairMat, String slab, String wall) {
        int lo = (alongU ? b0 : a0) - 1, hi = (alongU ? b1 : a1) + 1, l0 = (alongU ? a0 : b0) - 1, l1 = (alongU ? a1 : b1) + 1, k = 0;
        while (lo <= hi) { for (int t = l0; t <= l1; t++) { if (lo == hi) { put(L, alongU, t, lo, y + k, slab); continue; }
                put(L, alongU, t, lo, y + k, L.stairs(stairMat, alongU ? 2 : 1, false)); put(L, alongU, t, hi, y + k, L.stairs(stairMat, alongU ? 0 : 3, false)); }
            for (int c = Math.max(lo + 1, alongU ? b0 : a0); c <= Math.min(hi - 1, alongU ? b1 : a1); c++) { put(L, alongU, l0 + 1, c, y + k, wall); put(L, alongU, l1 - 1, c, y + k, wall); }
            lo++; hi--; k++; }
        return k; }
    static void put(Lot L, boolean alongU, int t, int c, int y, String blk) { if (alongU) L.set(t, y, c, blk); else L.set(c, y, t, blk); }
    /** Windows: 1 wide, 2 tall, every `every` blocks along each wall of a floor (corners and the door's bay left solid). */
    static void windows(Lot L, int a0, int b0, int a1, int b1, int y, int every, int skipA) {
        for (int a = a0 + 2; a <= a1 - 2; a += every) { if (Math.abs(a - skipA) <= 0) continue; for (int k = 0; k < 2; k++) { L.set(a, y + k, b0, "glass_pane"); L.set(a, y + k, b1, "glass_pane"); } }
        for (int b = b0 + 2; b <= b1 - 2; b += every) for (int k = 0; k < 2; k++) { L.set(a0, y + k, b, "glass_pane"); L.set(a1, y + k, b, "glass_pane"); } }
    static final String[] SIDING = {"white_terracotta", "white_terracotta", "light_gray_terracotta", "yellow_terracotta", "light_blue_terracotta", "green_terracotta", "white_terracotta", "bricks"};
    static final String[][] ROOFS = {{"deepslate_tile_stairs", "deepslate_tile_slab[type=bottom]"}, {"blackstone_stairs", "blackstone_slab[type=bottom]"}, {"dark_oak_stairs", "dark_oak_slab[type=bottom]"}, {"stone_brick_stairs", "stone_brick_slab[type=bottom]"}};

    // ------------------------------------------------------------ houses
    /** The front porch: floor, posts every 3 blocks, railing, a slab roof, steps down to the walk; then the walk to the sidewalk. */
    static void porch(Lot L, int a0, int a1, int bFront, int depth, String trim, String[] roof) {
        int pb0 = bFront - depth, pb1 = bFront - 1; L.box(a0, 0, pb0, a1, 0, pb1, "spruce_planks"); L.box(a0, 1, pb0, a1, 3, pb1, "air");
        for (int a = a0; a <= a1; a++) { boolean post = a == a0 || a == a1 || (a - a0) % 3 == 0; if (post) for (int y = 1; y <= 3; y++) L.set(a, y, pb0, trim); else L.set(a, 1, pb0, "birch_fence"); }
        int mid = (a0 + a1) / 2; L.set(mid, 1, pb0, "air"); L.set(mid + 1, 1, pb0, "air");
        L.box(a0 - 1, 4, pb0 - 1, a1 + 1, 4, pb1, roof[1]);
        L.set(mid, 0, pb0 - 1, L.stairs("spruce_stairs", 2, false)); L.set(mid + 1, 0, pb0 - 1, L.stairs("spruce_stairs", 2, false));
        for (int b = 0; b < pb0 - 1; b++) { L.set(mid, 0, b, "smooth_stone"); L.set(mid + 1, 0, b, "smooth_stone"); } }
    static void foursquare(Lot L) {
        int w = (int) Math.round(27.33 * FT), dMain = (int) Math.round(28 * FT), dAll = (int) Math.round(47.83 * FT), sb = (int) Math.round(SETBACK); w = Math.min(w, L.W - 4);
        int a0 = (L.W - w) / 2, a1 = a0 + w - 1, b0 = sb, b1 = b0 + dMain - 1, wingW = Math.max(4, w - 3), wb1 = b0 + dAll - 1; if (wb1 >= L.D - 2) return;
        String wall = L.pick(SIDING), trim = "white_concrete", fnd = L.pick("stone_bricks", "polished_andesite"); String[] roof = ROOFS[L.r.nextInt(ROOFS.length)]; int H = 8;
        pad(L, a0, b0 - 3, a1, wb1, H + 8, fnd);
        L.walls(a0, 1, b0, a1, H, b1, wall); L.box(a0 + 1, 4, b0 + 1, a1 - 1, 4, b1 - 1, "oak_planks"); L.box(a0 + 1, 0, b0 + 1, a1 - 1, 0, b1 - 1, "oak_planks");
        for (int y = 1; y <= H; y++) { L.set(a0, y, b0, trim); L.set(a1, y, b0, trim); L.set(a0, y, b1, trim); L.set(a1, y, b1, trim); }
        int dc = (a0 + a1) / 2; windows(L, a0, b0, a1, b1, 2, 3, dc); windows(L, a0, b0, a1, b1, 6, 3, -99); door(L, dc, b0, 1, L.pick("oak", "spruce", "dark_oak"));
        // the rear kitchen wing: one storey, its own hipped roof
        int wa0 = a0 + 1, wa1 = wa0 + wingW - 1; L.walls(wa0, 1, b1, wa1, 4, wb1, wall); L.box(wa0 + 1, 0, b1 + 1, wa1 - 1, 0, wb1 - 1, "oak_planks"); windows(L, wa0, b1, wa1, wb1, 2, 3, -99); hipped(L, wa0, b1 + 1, wa1, wb1, 5, roof[0], roof[1]);
        int k = hipped(L, a0, b0, a1, b1, H + 1, roof[0], roof[1]);
        living(L, a0, b0, 1); stairs(L, a1 - 1, b0 + 4, 4); bed(L, a0 + 2, b0 + 2, 5, "white"); bed(L, a0 + 5, b0 + 2, 5, "light_blue"); bath(L, a0 + 2, b1 - 1, 5); kitchen(L, wa0 + 1, wb1 - 1, 1); FURNISHED.increment();   // 0.26.2 interior: living room, stairs, two bedrooms and a bath upstairs, the kitchen in the wing
        // the centre dormer on the front slope: a small gable with a window
        int dy = H + 2; L.box(dc - 1, dy, b0 + 1, dc + 1, dy + 1, b0 + 2, wall); L.set(dc, dy, b0 + 1, "glass_pane"); gable(L, dc - 1, b0, dc + 1, b0 + 2, dy + 2, false, roof[0], roof[1], wall);
        porch(L, a0, a1, b0, 3, trim, roof);
        if (L.r.nextInt(3) == 0) for (int y = 2; y <= H + k + 1; y++) L.set(a1 - 1, y, b1 - 1, "bricks"); }
    static void gableFront(Lot L) {
        int w = (int) Math.round(22 * FT), d = (int) Math.round(36 * FT), sb = (int) Math.round(SETBACK); int a0 = (L.W - w) / 2, a1 = a0 + w - 1, b0 = sb, b1 = b0 + d - 1; if (b1 >= L.D - 2) return;
        String wall = L.pick(SIDING), trim = "white_concrete", fnd = L.pick("stone_bricks", "polished_andesite"); String[] roof = ROOFS[L.r.nextInt(ROOFS.length)]; int H = 6;   // a storey and a half: the upper half-storey under the roof
        pad(L, a0, b0 - 3, a1, b1, H + 8, fnd); L.walls(a0, 1, b0, a1, H, b1, wall); L.box(a0 + 1, 0, b0 + 1, a1 - 1, 0, b1 - 1, "oak_planks"); L.box(a0 + 1, 4, b0 + 1, a1 - 1, 4, b1 - 1, "oak_planks");
        for (int y = 1; y <= H; y++) { L.set(a0, y, b0, trim); L.set(a1, y, b0, trim); L.set(a0, y, b1, trim); L.set(a1, y, b1, trim); }
        int dc = a0 + 2; windows(L, a0, b0, a1, b1, 2, 3, dc); door(L, dc, b0, 1, L.pick("oak", "spruce"));
        living(L, a0, b0, 1); stairs(L, a1 - 1, b0 + 4, 4); kitchen(L, a0 + 1, b1 - 1, 1); bed(L, a0 + 2, b0 + 2, 5, "white"); bath(L, a0 + 2, b1 - 1, 5); FURNISHED.increment();   // 0.26.2 interior
        int k = gable(L, a0, b0, a1, b1, H + 1, false, roof[0], roof[1], wall); L.set((a0 + a1) / 2, H + 2, b0, "glass_pane"); L.set((a0 + a1) / 2, H + 3, b0, "glass_pane");   // the attic window in the front gable
        porch(L, a0, a1, b0, 2, trim, roof); }
    static void bungalow(Lot L) {
        int w = (int) Math.round(26 * FT), d = (int) Math.round(40 * FT), sb = (int) Math.round(SETBACK); int a0 = (L.W - w) / 2, a1 = a0 + w - 1, b0 = sb, b1 = b0 + d - 1; if (b1 >= L.D - 2) return;
        String wall = L.pick("bricks", "white_terracotta", "brown_terracotta", "light_gray_terracotta"), trim = "white_concrete"; String[] roof = ROOFS[L.r.nextInt(ROOFS.length)]; int H = 4;
        pad(L, a0, b0 - 3, a1, b1, H + 8, "stone_bricks"); L.walls(a0, 1, b0, a1, H, b1, wall); L.box(a0 + 1, 0, b0 + 1, a1 - 1, 0, b1 - 1, "oak_planks");
        int dc = (a0 + a1) / 2; windows(L, a0, b0, a1, b1, 2, 3, dc); door(L, dc, b0, 1, "oak");
        gable(L, a0, b0, a1, b1, H + 1, true, roof[0], roof[1], wall);   // the side-gabled roof: the ridge along the street
        living(L, a0, b0, 1); kitchen(L, a0 + 1, b1 - 1, 1); bed(L, a1 - 3, b1 - 6, 1, "white"); bath(L, a1 - 3, b1 - 1, 1); FURNISHED.increment();   // 0.26.2 interior: one storey
        porch(L, a0, a1, b0, 3, "bricks", roof); }

    // ------------------------------------------------------------ 0.26.2 interiors (the furniture ported from Hopeless Deep)
    /** A ported block facing local direction dir (0 toward the street, 1 +u, 2 toward the back, 3 -u), extra states appended. */
    static String F(Lot L, String id, int dir, String extra) { return "hopelessinfinity:" + id + "[facing=" + L.dir(dir) + extra + "]"; }
    /** Stairs from the ground floor (y 1) up to the floor at y = up, along the +u side wall rising toward the back, and the opening above. */
    static void stairs(Lot L, int a, int b, int up) { for (int y = 1; y < up; y++) { L.set(a, y, b + y, "oak_stairs[facing=" + L.dir(2) + ",half=bottom,shape=straight]"); L.set(a, up, b + y, "air"); }
        L.set(a, up, b + up, "air"); for (int y = 1; y < up; y++) for (int h = y + 1; h < up; h++) L.set(a, h, b + y, "air"); }
    /** A living room against the front: a television on the front wall facing in, a sofa and a coffee table facing it (CHOSEN layout). */
    static void living(Lot L, int a0, int b0, int y) { L.set(a0 + 3, y, b0 + 1, F(L, "television", 2, ",on=false")); L.set(a0 + 3, y, b0 + 3, F(L, "coffee_table", 1, "")); for (int a = a0 + 2; a <= a0 + 4; a++) L.set(a, y, b0 + 5, F(L, "sofa", 0, "")); }
    /** A kitchen along a back wall at b (facing the room) from a, and a table with two chairs in front of it. */
    static void kitchen(Lot L, int a, int b, int y) { L.set(a, y, b, F(L, "stove", 0, ",lit=false")); L.set(a + 1, y, b, F(L, "sink", 0, "")); L.set(a + 2, y, b, F(L, "fridge", 0, "")); L.set(a + 2, y + 1, b, F(L, "fridge_top", 0, ""));
        L.set(a + 1, y, b - 3, F(L, "table", 1, "")); L.set(a + 1, y, b - 4, F(L, "chair", 2, "")); L.set(a + 1, y, b - 2, F(L, "chair", 0, "")); }
    /** A bed (head toward the street) and a bathroom (toilet, sink, shower along a back wall at bb). */
    static void bed(Lot L, int a, int b, int y, String colour) { L.set(a, y, b, colour + "_bed[facing=" + L.dir(0) + ",part=head]"); L.set(a, y, b + 1, colour + "_bed[facing=" + L.dir(0) + ",part=foot]"); }
    static void bath(Lot L, int a, int bb, int y) { L.set(a, y, bb, F(L, "toilet", 0, "")); L.set(a + 1, y, bb, F(L, "sink", 0, "")); L.set(a + 2, y, bb, F(L, "shower", 0, ",running=false")); }
    public static final java.util.concurrent.atomic.LongAdder FURNISHED = new java.util.concurrent.atomic.LongAdder();   // probe: buildings given an interior

    // ------------------------------------------------------------ Main Street
    /** A two-part commercial block (kind 0 store, 1 post office, 2 fire station, 3 town hall) filling the lot's width at the lot line. */
    public static final java.util.concurrent.atomic.LongAdder SIGNS = new java.util.concurrent.atomic.LongAdder(), COBBLE_SIGNS = new java.util.concurrent.atomic.LongAdder(), GAME_BLOCKS = new java.util.concurrent.atomic.LongAdder();
    static void store(Lot L, int kind) {
        int W = L.W, d = Math.min((int) Math.round(STORE_D), L.D - 6), floors = kind == 1 ? 1 : kind == 3 ? 3 : 2 + (L.r.nextInt(3) == 0 ? 1 : 0), G = 6, H = G + 4 * (floors - 1);
        String brick = kind == 3 ? "stone_bricks" : L.pick("bricks", "bricks", "mud_bricks", "red_nether_bricks", "brown_terracotta"), dark = L.pick("dark_oak_planks", "spruce_planks", "black_terracotta");
        String sign = kind == 1 ? "blue_terracotta" : kind == 2 ? "red_terracotta" : L.pick("green_terracotta", "red_terracotta", "black_terracotta", "white_terracotta", "blue_terracotta", "brown_terracotta");
        pad(L, 0, 0, W - 1, d - 1, H + 3, "stone"); L.walls(0, 1, 0, W - 1, H, d - 1, brick); L.box(1, 0, 1, W - 2, 0, d - 2, "spruce_planks"); for (int f = 1; f < floors; f++) L.box(1, G + 4 * (f - 1), 1, W - 2, G + 4 * (f - 1), d - 2, "spruce_planks");
        // storefront (front wall, between the piers at the party walls): bulkhead, display glass, transom, sign band; the entry recessed 1 block at the middle
        int m = W / 2;
        if (kind == 2) { L.box(1, 1, 0, W - 2, 4, 0, "air"); for (int a = 1; a <= W - 2; a++) for (int y = 1; y <= 4; y++) L.set(a, y, 0, "white_concrete"); for (int a = 1; a <= W - 2; a++) L.set(a, 5, 0, "red_concrete"); for (int a = 2; a <= W - 3; a++) for (int y = 1; y <= 3; y++) L.set(a, y, 0, "air"); }   // the fire station's engine bay door (open)
        else { for (int a = 1; a <= W - 2; a++) { L.set(a, 1, 0, dark); L.set(a, 2, 0, "glass_pane"); L.set(a, 3, 0, "glass_pane"); L.set(a, 4, 0, "glass_pane"); L.set(a, 5, 0, a == m ? dark : sign); }
            L.set(m, 1, 0, "air"); L.set(m, 2, 0, "air"); L.set(m, 3, 0, "air"); L.set(m, 1, 1, "air"); door(L, m, 1, 1, "dark_oak");
            if (kind == 0) { Businesses.Chain ch = Businesses.at(L.p, Towns.lotKey(L.i, L.j, L.row, L.lot)); L.set(m, 5, -1, ch != null ? Businesses.sign(ch, L.dir(0)) : Businesses.familySign(MoonTerrain.hash(L.seed, 53, 0, 0, 0), L.dir(0))); SIGNS.increment();
                // 0.28.4 a Cobble & Sons store: a blade sign of the company's gate (owner: "its presence must be found everywhere") hung out from the
                // left pier over the sidewalk, 3 x 3 m, the ring in gate frame round a light-blue centre, its foot 7 m up (CHOSEN; US sign codes ask 8 ft = 2.4 m clear over a sidewalk)
                if (ch != null && ch.name().startsWith("Cobble")) { for (int dz = 1; dz <= 3; dz++) for (int y = 7; y <= 9; y++) L.set(0, y, -dz, dz == 2 && y == 8 ? "light_blue_stained_glass" : "hopelessinfinity:gate_frame"); L.set(0, 8, -4, "air"); COBBLE_SIGNS.increment(); } } }   // 0.27.4 every store's name on its sign band: a chain's, or the family's
        L.set(0, 6, 0, brick); L.set(W - 1, 6, 0, brick);
        // upper floors: three ranked windows per 25 ft front (1 wide x 2 tall), stone sills
        for (int f = 1; f < floors; f++) { int y = G + 4 * (f - 1) + 1; int[] at = W >= 7 ? new int[]{1 + (W - 2) / 6, W / 2, W - 2 - (W - 2) / 6} : new int[]{W / 2};
            for (int a : at) { L.set(a, y + 1, 0, "glass_pane"); L.set(a, y + 2, 0, "glass_pane"); L.set(a, y, -1, L.stairs("stone_brick_stairs", 2, true)); } }
        // cornice (upside-down stairs out over the street on brackets) and parapet; a flat roof; the rear wall's door to the yard
        for (int a = 0; a < W; a++) { L.set(a, H + 1, -1, L.stairs(dark.startsWith("black") ? "blackstone_stairs" : "dark_oak_stairs", 2, true)); L.set(a, H + 2, 0, "stone_brick_slab[type=bottom]"); if (a % 2 == 0) L.set(a, H, -1, L.stairs("dark_oak_stairs", 2, true)); }
        L.box(1, H + 1, 1, W - 2, H + 1, d - 2, "gray_concrete"); L.walls(0, H + 1, 0, W - 1, H + 1, d - 1, brick);
        L.set(m, 1, d - 1, "air"); L.set(m, 2, d - 1, "air");
        if (kind == 1) { for (int y = 1; y <= 7; y++) L.set(0, y, -3, "iron_bars"); L.set(0, 8, -3, "white_banner[rotation=0]"); }   // the post office's flag pole
        if (kind == 2) for (int y = H + 1; y <= H + 6; y++) { L.set(W - 2, y, d - 2, brick); L.set(W - 3, y, d - 2, brick); }   // the hose drying tower
        if (kind == 3) { for (int a = m - 1; a <= m + 1; a++) for (int y = H + 2; y <= H + 4; y++) L.set(a, y, 0, "stone_bricks"); L.set(m, H + 3, -1, "white_concrete"); } 
        // 0.26.2 interior of the ground floor: a store's counter with its register and coolers at the back; the post office's boxes and
        // counter; the town hall's desks; the fire station keeps its open bay (its engine is a vehicle: none until the owner says)
        if (kind == 0) { for (int a = 2; a <= W - 3; a++) L.set(a, 1, 5, a == m ? F(L, "cash_register", 0, "") : "spruce_slab[type=bottom]"); for (int a = 2; a <= W - 3; a += 2) { L.set(a, 1, d - 2, F(L, "drink_cooler", 0, ",half=lower,stock=1")); L.set(a, 2, d - 2, F(L, "drink_cooler", 0, ",half=upper,stock=1")); } FURNISHED.increment(); }
        else if (kind == 1) { for (int b = 3; b <= d - 3; b++) { L.set(1, 1, b, F(L, "po_box", 1, "")); L.set(1, 2, b, F(L, "po_box", 1, "")); } for (int a = 3; a <= W - 2; a++) L.set(a, 1, 6, "spruce_slab[type=bottom]"); FURNISHED.increment(); }
        else if (kind == 3) { for (int b = 4; b <= d - 4; b += 3) for (int a = 2; a <= W - 3; a += 3) { L.set(a, 1, b, F(L, "desk", 0, "")); L.set(a, 1, b + 1, F(L, "chair", 0, "")); } FURNISHED.increment(); }
}   // the town hall's raised centre and clock face

    // ------------------------------------------------------------ public buildings
    /** A frame church on a corner lot (CHOSEN for now: a 30 x 60 ft nave, gable roof, a square steeple at the front, lancet windows). */
    static void church(Lot L) {
        int w = (int) Math.round(30 * FT), d = (int) Math.round(60 * FT), a0 = (L.W - w) / 2, a1 = a0 + w - 1, b0 = 6, b1 = b0 + d - 1; if (b1 >= L.D - 2) return;
        String wall = L.pick("white_terracotta", "bricks", "white_terracotta"); String[] roof = ROOFS[L.r.nextInt(ROOFS.length)]; int H = 7;
        pad(L, a0, b0 - 4, a1, b1, H + 30, "stone_bricks"); L.walls(a0, 1, b0, a1, H, b1, wall); L.box(a0 + 1, 0, b0 + 1, a1 - 1, 0, b1 - 1, "oak_planks");
        for (int b = b0 + 3; b <= b1 - 3; b += 4) for (int y = 2; y <= 5; y++) { L.set(a0, y, b, "glass_pane"); L.set(a1, y, b, "glass_pane"); }
        gable(L, a0, b0, a1, b1, H + 1, false, roof[0], roof[1], wall);
        { int c = (a0 + a1) / 2; for (int b = b0 + 3; b <= b1 - 7; b += 2) for (int a = a0 + 2; a <= a1 - 2; a++) if (Math.abs(a - c) > 1) L.set(a, 1, b, "oak_stairs[facing=" + L.dir(0) + ",half=bottom,shape=straight]"); L.set(c, 1, b1 - 3, F(L, "table", 1, "")); FURNISHED.increment(); }   // 0.26.2 pews either side of the aisle, the altar table at the back
        int tc = (a0 + a1) / 2, t0 = tc - 1, t1 = tc + 1, tb0 = b0 - 3, tb1 = b0 - 1, tH = H + 10;
        L.walls(t0, 1, tb0, t1, tH, tb1, wall); L.set(tc, 1, tb0, "air"); L.set(tc, 2, tb0, "air"); door(L, tc, tb0, 1, "dark_oak"); L.set(tc, tH - 2, tb0, "glass_pane"); L.set(tc, tH - 3, tb0, "glass_pane");
        int k = 0; for (int s = 1; s >= 0; s--, k++) { L.walls(tc - s, tH + 1 + k, tb0 + 1 - s, tc + s, tH + 1 + k, tb1 - 1 + s, s == 0 ? "stone_brick_wall" : roof[0]); }
        for (int y = tH + 3; y <= tH + 7; y++) L.set(tc, y, b0 - 2, y < tH + 7 ? "dark_oak_fence" : "lightning_rod"); }
    /** An elevated steel water tank (CHOSEN for now: the tank 10 m across, its floor 25 m up on four legs with cross-bracing). */
    static void waterTower(Lot L) {
        int c = L.W / 2, cb = L.D / 2, r = 5, base = 25, legOff = 4;
        for (int[] q : new int[][]{{-legOff, -legOff}, {legOff, -legOff}, {-legOff, legOff}, {legOff, legOff}}) { L.box(c + q[0], -2, cb + q[1], c + q[0], 0, cb + q[1], "stone"); for (int y = 1; y < base; y++) L.set(c + q[0], y, cb + q[1], "light_gray_concrete"); }
        for (int y = 6; y < base; y += 6) for (int t = -legOff + 1; t < legOff; t++) { L.set(c + t, y, cb - legOff, "iron_bars"); L.set(c + t, y, cb + legOff, "iron_bars"); L.set(c - legOff, y, cb + t, "iron_bars"); L.set(c + legOff, y, cb + t, "iron_bars"); }
        for (int y = 1; y < base; y++) L.set(c, y, cb, "light_gray_concrete");   // the riser pipe
        for (int y = base; y <= base + 7; y++) { int rr = y == base || y == base + 7 ? r - 1 : r; for (int a = -r; a <= r; a++) for (int b = -r; b <= r; b++) { double q = Math.sqrt(a * a + b * b); if (q <= rr + 0.4 && (q > rr - 0.6 || y == base || y == base + 7)) L.set(c + a, y, cb + b, "white_concrete"); } }
        for (int k = 0; k < 3; k++) for (int a = -(r - 2 - k); a <= r - 2 - k; a++) for (int b = -(r - 2 - k); b <= r - 2 - k; b++) if (Math.sqrt(a * a + b * b) <= r - 2 - k + 0.4) L.set(c + a, base + 8 + k, cb + b, "white_concrete"); }
    /** 0.26.1 a distribution substation on its lot: a gravel yard inside a chain-link fence with danger signs (NESC Rule 110A: a fence at
     *  least 7 ft high round the live parts, signs on it; 2 blocks here = 6.6 ft, CHOSEN with the barbed wire on top for 7 ft and over),
     *  two transformers, each fed by its line from the pole above (town/CityReg: a transformer touching a power line feeds the grid). */
    static void substation(Lot L) {
        int a0 = 2, a1 = L.W - 3, b0 = 6, b1 = Math.min(L.D - 6, b0 + 20);
        L.box(a0, 0, b0, a1, 0, b1, "gravel"); String ns = L.dir(0) + "=true," + L.dir(2) + "=true", ew = L.dir(1) + "=true," + L.dir(3) + "=true";
        for (int a = a0; a <= a1; a++) for (int b : new int[]{b0, b1}) { boolean corner = a == a0 || a == a1; String st = corner ? (a == a0 ? L.dir(1) : L.dir(3)) + "=true," + (b == b0 ? L.dir(2) : L.dir(0)) + "=true" : ew;
            boolean gate = b == b0 && Math.abs(a - (a0 + a1) / 2) <= 0 && !corner; L.set(a, 1, b, gate ? "hopelessinfinity:chain_link_gate[facing=" + L.dir(2) + ",in_wall=false,open=false]" : ((a - a0) % 6 == 3 && !corner ? "hopelessinfinity:fence_danger_sign[" + st + "]" : "hopelessinfinity:chain_link_fence[" + st + "]"));
            if (!gate) { L.set(a, 2, b, "hopelessinfinity:chain_link_fence[" + st + "]"); L.set(a, 3, b, "hopelessinfinity:barbed_wire"); } }
        for (int b = b0 + 1; b < b1; b++) for (int a : new int[]{a0, a1}) { L.set(a, 1, b, "hopelessinfinity:chain_link_fence[" + ns + "]"); L.set(a, 2, b, "hopelessinfinity:chain_link_fence[" + ns + "]"); L.set(a, 3, b, "hopelessinfinity:barbed_wire"); }
        int c = (a0 + a1) / 2; for (int t : new int[]{b0 + 6, b0 + 13}) { if (t >= b1 - 1) continue; L.box(c - 1, 0, t - 1, c + 1, 0, t + 1, "smooth_stone");   // the pad
            L.set(c, 1, t, "hopelessinfinity:transformer"); for (int y = 2; y <= 7; y++) L.set(c, y, t, "hopelessinfinity:power_line[axis=y]"); }   // the feed down from the line
        for (int b = b0 + 6; b <= b1 + 6 && b < L.D; b++) L.set(c, 8, b, "hopelessinfinity:power_line[axis=" + (L.dir(0).equals("north") || L.dir(0).equals("south") ? "z" : "x") + "]");   // the line in from the back of the lot
    }
    /** A two-storey brick school across the block's front (CHOSEN for now: 60 x 18 m, entrance at the middle, flat roof with parapet), the rest lawn. */
    static void school(Lot L) {
        int w = Math.min(60, L.W - 10), d = 18, a0 = (L.W - w) / 2, a1 = a0 + w - 1, b0 = 10, b1 = b0 + d - 1, H = 8, m = (a0 + a1) / 2;
        pad(L, a0, b0, a1, b1, H + 3, "stone_bricks"); L.walls(a0, 1, b0, a1, H, b1, "bricks"); L.box(a0 + 1, 0, b0 + 1, a1 - 1, 0, b1 - 1, "smooth_stone"); L.box(a0 + 1, 4, b0 + 1, a1 - 1, 4, b1 - 1, "smooth_stone");
        for (int f = 0; f < 2; f++) for (int a = a0 + 2; a <= a1 - 2; a++) if ((a - a0) % 4 != 0) for (int k = 0; k < 2; k++) { L.set(a, 2 + 4 * f + k, b0, "glass_pane"); L.set(a, 2 + 4 * f + k, b1, "glass_pane"); }
        L.box(m - 1, 1, b0, m + 1, 3, b0, "air"); door(L, m - 1, b0, 1, "dark_oak"); door(L, m + 1, b0, 1, "dark_oak"); L.box(m - 2, H + 1, b0, m + 2, H + 2, b0, "stone_bricks");
        L.box(a0 + 1, H + 1, b0 + 1, a1 - 1, H + 1, b1 - 1, "gray_concrete"); L.walls(a0, H + 1, b0, a1, H + 1, b1, "bricks");
        // 0.26.2 classrooms on both floors: rows of desks and chairs facing a chalkboard on the back wall (rooms 10 m wide, CHOSEN)
        for (int y : new int[]{1, 5}) for (int ra = a0 + 2; ra + 8 <= a1 - 1; ra += 10) { if (ra <= m + 1 && ra + 8 >= m - 1 && y == 1) continue;
            for (int a = ra + 1; a <= ra + 7; a += 2) for (int b = b0 + 4; b <= b1 - 5; b += 2) { L.set(a, y, b, F(L, "desk", 0, "")); L.set(a, y, b - 1, F(L, "chair", 2, "")); }
            L.set(ra + 4, y + 1, b1 - 1, "hopelessinfinity:chalkboard"); }
        FURNISHED.increment();
        for (int b = 0; b < b0; b++) for (int a = m - 1; a <= m + 1; a++) L.set(a, 0, b, "smooth_stone");
        for (int y = 1; y <= 9; y++) L.set(m + 6, y, 3, "iron_bars"); L.set(m + 6, 10, 3, "white_banner[rotation=0]"); }
    /** 0.29.3 THE DEPOT: a railroad's standard one-storey frame combination depot (STATED: Great Northern standard plans, 24 x 48 ft (1899)
     *  and 30 x 60 ft, GNRHS; the form common to US lines: long side along the tracks, waiting room at one end, the agent's office in the
     *  middle with a bay window onto the track "for viewing the track in both directions", the freight room at the other end with big sliding
     *  doors on the track side and the street side (Georgia depot guide, railga.com)). Colours: white walls, green trim (STATED: GN scheme
     *  after 1950). The smaller plan for towns under 5,000 people, the larger above (CHOSEN). Walls 4 blocks (about 13 ft to the eaves),
     *  a low hipped roof over eaves 2 blocks wide (CHOSEN, after photos: wide bracketed eaves over the platform). Brick platform between the
     *  depot and the track, flush with the ground (UPRR platform guidelines, plan 713000: a platform by freight tracks no more than 8 in over
     *  the rail); a raised wooden freight dock at the freight end, about wagon-bed height (1 block, CHOSEN); lamps along the platform every
     *  10 blocks (CHOSEN). Local frame: a across the tracks (+a toward them), b along them; the zone's far edge (a = W - 1) is YARD_EDGE_M
     *  from the track's centre line. */
    static void depot(Lot L) {
        boolean big = L.p.site.pop >= 5000; int dep = big ? 9 : 7, len = big ? 18 : 15;   // 30 x 60 ft or 24 x 48 ft on the 1 m grid
        int aT = L.W - 4, aB = aT - dep + 1, bMid = L.D / 2, b0 = bMid - len / 2, b1 = b0 + len - 1; if (aB < 1) return;
        int nW = big ? 6 : 5, nA = big ? 4 : 4, bw1 = b0 + nW, ba1 = bw1 + nA;   // partitions: waiting room b0..bw1, agent bw1..ba1, freight ba1..b1
        String wall = "smooth_quartz", trim = "green_terracotta", roofS = "deepslate_tile_stairs", roofSl = "deepslate_tile_slab[type=bottom]"; int H = 4;
        // the platform (brick, flush with the ground) from the depot to the zone's edge by the track, the whole zone long
        L.box(aT + 1, -1, 0, L.W - 1, -1, L.D - 1, "bricks");
        for (int b = 2; b < L.D - 1; b += 10) { if (b >= b0 - 2 && b <= b1 + 2) continue; L.set(L.W - 2, 0, b, "dark_oak_fence"); L.set(L.W - 2, 1, b, "dark_oak_fence"); L.set(L.W - 2, 2, b, "lantern[hanging=false]"); LAMPS.increment(); }
        pad(L, aB, b0, aT, b1 + 2, H + 9, "stone_bricks");
        L.walls(aB, 1, b0, aT, H, b1, wall); L.box(aB + 1, 0, b0 + 1, aT - 1, 0, b1 - 1, "oak_planks");
        for (int y = 1; y <= H; y++) { L.set(aB, y, b0, trim); L.set(aT, y, b0, trim); L.set(aB, y, b1, trim); L.set(aT, y, b1, trim); }
        for (int b = b0; b <= b1; b++) { L.set(aB, 1, b, trim); L.set(aT, 1, b, trim); } for (int a = aB; a <= aT; a++) { L.set(a, 1, b0, trim); L.set(a, 1, b1, trim); }   // the wainscot belt
        // partitions
        for (int a = aB + 1; a < aT; a++) for (int y = 1; y <= H; y++) { L.set(a, y, bw1, wall); L.set(a, y, ba1, wall); }
        L.set((aB + aT) / 2, 2, bw1, "glass_pane");   // the ticket window from the agent's office to the waiting room
        // waiting room: doors on both sides, windows, benches along the end wall, a hanging lamp
        int dw = b0 + 2; doorA(L, aT, dw, 1); doorA(L, aB, dw, 3); for (int b = b0 + 4; b < bw1; b += 2) { L.set(aT, 2, b, "glass_pane"); L.set(aT, 3, b, "glass_pane"); L.set(aB, 2, b, "glass_pane"); L.set(aB, 3, b, "glass_pane"); }
        for (int a = aB + 2; a <= aT - 2; a++) L.set(a, 1, b0 + 1, L.stairs("spruce_stairs", 2, false)); L.set((aB + aT) / 2, H, (b0 + bw1) / 2, "lantern[hanging=true]");
        // the agent's office: the bay window onto the track, a desk, a lamp; the chimney
        int bc = (bw1 + ba1) / 2; L.box(aT + 1, 0, bc - 1, aT + 1, 0, bc + 1, "oak_planks"); L.box(aT, 1, bc - 1, aT + 1, H, bc + 1, "air");   // the bay: a block deep, 3 wide, glazed on its front and its two sides
        for (int b = bc - 1; b <= bc + 1; b++) { L.set(aT + 2, 0, b, "stone_bricks"); L.set(aT + 2, 1, b, trim); L.set(aT + 2, 2, b, "glass_pane"); L.set(aT + 2, 3, b, "glass_pane"); L.set(aT + 2, 4, b, wall); }
        for (int bs : new int[]{bc - 2, bc + 2}) { L.set(aT + 1, 0, bs, "stone_bricks"); L.set(aT + 1, 1, bs, trim); L.set(aT + 1, 2, bs, "glass_pane"); L.set(aT + 1, 3, bs, "glass_pane"); L.set(aT + 1, 4, bs, wall); }
        L.box(aT + 1, H + 1, bc - 2, aT + 2, H + 1, bc + 2, roofSl);
        L.set(aT - 1, 1, bc, L.stairs("oak_stairs", 3, true)); L.set(aT - 2, 1, bc, "oak_stairs[facing=" + L.dir(1) + ",half=bottom,shape=straight]"); L.set(aB + 1, H, bc, "lantern[hanging=true]"); L.set(aB, 2, bc, "glass_pane"); L.set(aB, 3, bc, "glass_pane");
        // the freight room: the floor raised a block (wagon-bed height), big doors both sides (closed: plank panels), the dock outside the end
        for (int a = aB + 1; a < aT; a++) for (int b = ba1 + 1; b < b1; b++) L.set(a, 1, b, "spruce_planks");
        int fd = (ba1 + b1) / 2; for (int b = fd - 1; b <= fd + 1; b++) for (int y = 1; y <= 3; y++) { L.set(aT, y, b, "spruce_planks"); L.set(aB, y, b, "spruce_planks"); }
        L.box(aB, 1, b1 + 1, aT, 1, b1 + 2, "spruce_planks"); L.box(aB, 2, b1 + 1, aT, 4, b1 + 2, "air"); for (int a = aB; a <= aT; a += Math.max(1, dep - 1)) L.set(a, 0, b1 + 2, "spruce_log");
        L.set((aB + aT) / 2, 1, b1, "spruce_door[facing=" + L.dir(2) + ",half=lower,hinge=left,open=false]"); L.set((aB + aT) / 2, 2, b1, "spruce_door[facing=" + L.dir(2) + ",half=upper,hinge=left,open=false]");
        L.set(aT + 1, 0, b1 + 1, L.stairs("spruce_stairs", 3, false));
        // the roof: eaves 2 blocks wide (slabs, the outer ring), then the hipped roof; brick chimney over the office stove
        for (int a = aB - 2; a <= aT + 2; a++) { L.set(a, H + 1, b0 - 2, roofSl); L.set(a, H + 1, b1 + 2, roofSl); } for (int b = b0 - 1; b <= b1 + 1; b++) { L.set(aB - 2, H + 1, b, roofSl); L.set(aT + 2, H + 1, b, roofSl); }
        int k = hipped(L, aB, b0, aT, b1, H + 1, roofS, roofSl); for (int y = H + 1; y <= H + k + 1; y++) L.set(aB + 2, y, bc, "bricks");
        DEPOTS.increment(); }
    static void doorA(Lot L, int a, int b, int facingLocal) { L.set(a, 1, b, "oak_door[facing=" + L.dir(facingLocal) + ",half=lower,hinge=left,open=false]"); L.set(a, 2, b, "oak_door[facing=" + L.dir(facingLocal) + ",half=upper,hinge=left,open=false]"); }
    public static final java.util.concurrent.atomic.LongAdder DEPOTS = new java.util.concurrent.atomic.LongAdder(), LAMPS = new java.util.concurrent.atomic.LongAdder();
    /** A concrete grain elevator (CHOSEN for now: four silos 8 m across and 35 m tall in a row, a headhouse 45 m tall at one end). */
    static void elevator(Lot L) {
        int r = 4, H = 35, b = 20, a = 10;
        for (int s = 0; s < 4; s++) { int ca = a + s * 2 * r; for (int y = -2; y <= H; y++) for (int da = -r; da <= r; da++) for (int db = -r; db <= r; db++) { double q = Math.sqrt(da * da + db * db); if (q <= r + 0.3 && (q > r - 0.8 || y == H || y <= 0)) L.set(ca + da, y, b + db, y <= 0 ? "stone" : "smooth_stone"); } }
        int ha = a + 8 * r, hH = 45; L.box(ha - 1, -2, b - 3, ha + 4, 0, b + 3, "stone"); L.walls(ha - 1, 1, b - 3, ha + 4, hH, b + 3, "light_gray_concrete"); L.box(ha - 1, hH + 1, b - 3, ha + 4, hH + 1, b + 3, "gray_concrete");
        for (int y = 4; y < hH; y += 6) L.set(ha + 1, y, b - 3, "glass_pane"); L.box(ha, 1, b - 3, ha + 2, 4, b - 3, "air"); }

    // ------------------------------------------------------------ 0.25.1 the spaceport (after Starbase, Texas)
    /** The launch site, from the record and a photograph (Wikimedia Commons "Starship full stack with Jeep", SN20 on BN4 at Starbase, 2022;
     *  scaled by the 9 m vehicle diameter across and the tower's height up):
     *  - the integration tower: 469 ft of steel truss with a 10 ft lightning rod (FAA, via the Starship wiki; Guinness: 146 m), about 13 m
     *    square (measured), with the booster-catch arms ("chopsticks") about 84 m up and the ship's quick-disconnect arm about 123 m up (measured);
     *  - the orbital launch mount: a table about 15 m across about 14 m up on legs (measured; the dune hides its foot), a ring of 20 clamps
     *    round the booster (STATED), a water-cooled steel flame deflector under it (STATED), its centre about 14 m from the tower's (measured);
     *  - the vehicle on the mount: a 9 m stainless steel stack about 120 m tall (the photograph's stack: booster about 70 m, ship about 50 m
     *    with its black heat-shield tiles on the windward side);
     *  - a tank farm of eight tanks (STATED: "each tank farm consists of eight tanks"; 8 m across and 30 m tall: CHOSEN), 60 m inland of the mount.
     *  The tower stands on the landward side of the mount, as at Starbase. */
    static void launchSite(Lot L) {
        int cu = L.W / 2 + L.seaward * 30, cv = L.D / 2, sw = L.seaward; int tu = cu - sw * 14, tv = cv, T = 6, H = 143;   // mount centre; tower centre (landward)
        // the integration tower: corner columns, X bracing every 9 m, landings
        for (int y = 1; y <= H; y++) { for (int a = -T; a <= T; a += 2 * T) for (int b = -T; b <= T; b += 2 * T) L.set(tu + a, y, tv + b, "gray_concrete");
            int ph = Math.floorMod(y, 9); for (int t = -T + 1; t < T; t++) { boolean xb = Math.abs(t) == Math.abs(T - 1 - 2 * ph * (T - 1) / 8) || ph == 0; if (xb) { L.set(tu + t, y, tv - T, "iron_bars"); L.set(tu + t, y, tv + T, "iron_bars"); L.set(tu - T, y, tv + t, "iron_bars"); L.set(tu + T, y, tv + t, "iron_bars"); } }
            if (y % 27 == 0) L.box(tu - T + 1, y, tv - T + 1, tu + T - 1, y, tv + T - 1, "iron_trapdoor[facing=north,half=bottom,open=false]"); }
        for (int y = H + 1; y <= H + 3; y++) L.set(tu, y, tv, "lightning_rod");
        // the chopsticks (two arms reaching past the mount's centre) at 84 m, the ship's arm at 123 m
        for (int side = -1; side <= 1; side += 2) for (int k = 0; k <= 26; k++) { int a = tu + sw * (T + k), b = tv + side * 7; for (int y = 84; y <= 86; y++) L.set(a, y, b, k % 4 == 0 ? "gray_concrete" : "iron_block"); }
        for (int k = 0; k <= 12; k++) { int a = tu + sw * (T + k); L.set(a, 123, tv - 5, "iron_block"); L.set(a, 124, tv - 5, "iron_bars"); }
        // the launch mount: six legs, the table with its 9 m hole, the clamps, the flame deflector, the vehicle
        int mt = 14, R = 7; for (int k = 0; k < 6; k++) { double ang = Math.PI * 2 * k / 6; int a = cu + (int) Math.round(Math.cos(ang) * 7), b = cv + (int) Math.round(Math.sin(ang) * 7); L.box(a, 1, b, a + 1, mt - 1, b + 1, "gray_concrete"); }
        for (int a = -R; a <= R; a++) for (int b = -R; b <= R; b++) { double q = Math.hypot(a, b); if (q <= R + 0.3 && q > 4.6) L.set(cu + a, mt, cv + b, "iron_block"); }
        for (int k = 0; k < 20; k++) { double ang = Math.PI * 2 * k / 20; L.set(cu + (int) Math.round(Math.cos(ang) * 5), mt + 1, cv + (int) Math.round(Math.sin(ang) * 5), "anvil[facing=" + L.dir(1) + "]"); }
        for (int a = -6; a <= 6; a++) for (int b = -6; b <= 6; b++) if (Math.hypot(a, b) <= 6.3) L.set(cu + a, 1, cv + b, "iron_block");   // the steel deflector plate (water-cooled)
        int vb = mt + 1, boost = 70, ship = 50; for (int y = vb; y <= vb + boost + ship; y++) { boolean shipPart = y > vb + boost; int top = vb + boost + ship, r = y > top - 6 ? Math.max(1, 4 - (y - (top - 6)) * 4 / 6) : 4;
            for (int a = -r; a <= r; a++) for (int b = -r; b <= r; b++) { double q = Math.hypot(a, b); if (q > r + 0.3 || q < r - 1.2) continue; boolean wind = shipPart && b * 1 >= 0; L.set(cu + a, y, cv + b, wind ? "black_concrete" : "iron_block"); } }
        for (int side = -1; side <= 1; side += 2) for (int y = vb + boost + 4; y <= vb + boost + 9; y++) { L.set(cu + side * 5, y, cv, "iron_block"); L.set(cu + side * 6, y + 1, cv, "iron_block"); }   // the ship's forward flaps
        for (int side = -1; side <= 1; side += 2) for (int y = vb + boost + ship - 22; y <= vb + boost + ship - 16; y++) L.set(cu + side * 5, y, cv, "iron_block");   // and its aft flaps
        // the tank farm
        int fu = cu - sw * 60; for (int t = 0; t < 8; t++) { int a = fu + (t % 4) * 11 * (-sw), b = cv - 16 + (t / 4) * 32; for (int y = 1; y <= 30; y++) for (int da = -4; da <= 4; da++) for (int db = -4; db <= 4; db++) { double q = Math.hypot(da, db); if (q <= 4.3 && (q > 3.2 || y == 30)) L.set(a + da, y, b + db, "white_concrete"); } } }
    /** The production site (Gigabay: 380 ft = 116 m tall, STATED, FAA documents via the Starship wiki; its 60 x 40 m footprint and the long
     *  low assembly hall, 200 x 60 m and 25 m tall, CHOSEN until measured from aerial photographs). */
    static void production(Lot L) {
        int a0 = 30, b0 = L.D / 2 - 20, H = 116; L.walls(a0, 1, b0, a0 + 59, H, b0 + 39, "light_gray_concrete"); L.box(a0, H + 1, b0, a0 + 59, H + 1, b0 + 39, "gray_concrete");
        for (int y = 1; y <= 90; y++) for (int b = b0 + 8; b <= b0 + 31; b++) L.set(a0, y, b, y > 88 ? "gray_concrete" : "white_concrete");   // the tall door
        for (int y = 10; y < H; y += 12) for (int a = a0 + 4; a < a0 + 56; a += 8) L.set(a, y, b0 + 39, "glass_pane");
        int h0 = a0 + 80, hb = L.D / 2 - 30; L.walls(h0, 1, hb, h0 + 199, 25, hb + 59, "white_concrete"); L.box(h0, 26, hb, h0 + 199, 26, hb + 59, "gray_concrete");
        for (int a = h0 + 6; a < h0 + 195; a += 6) for (int y = 18; y <= 22; y++) L.set(a, y, hb, "glass_pane"); }
    public static String probe() { long n = LOTS.sum(); return String.format("buildings: %,d lots built, %,d blocks set, %.2f ms of chunk time", n, BLOCKS.sum(), NANOS.sum() / 1e6); }
}
