package com.hopeless.infinity.planet;

/**
 * 0.27.1 THE GREAT GATE of a megacity's gate plaza (owner, 2026-11-04: "a larger fancier ring ... fit into the style and have a tamed
 * entrance"; "the mega city gate plaza should not do anything until set by the player"; "a metal grate ramp with rails"). Pure Java
 * (tools/GreatGateTest, tools/BlockIso). Block strings in the gate's frame: x along the walk-through axis (the plan's u), z across (v), y up
 * from the plaza's pad (y = 0 is the cell a player stands in on the plaza). The ring stands closed: no field until a player sets it.
 *  - RING (CHOSEN, an original design; the gate technology is the one invented thing): aperture 15 blocks (RI 7.5), outer 19 (RO 9.5),
 *    2 blocks deep, its aperture's floor at the dais top. Gate-frame alloy with a polished blackstone inner lip and 8 light nodes set in
 *    the rim. 0.27.1 measured (BlockIso): with 45-degree slopes only an 11-block ring read as a boxy frame; now each edge cell takes the
 *    shape and turn that best fits the circle there (fit(): the band sampled 8 x 8 in the cell against each candidate's own collision).
 *  - DAIS: 1 block (1 m = 39 in) high, 3 blocks of landing all round the ring (ADA 405.7: landings at least 60 in long). Its edges drop
 *    more than 30 in, so they carry guards 42 in high or more (IBC 1015.2 / 1015.3).
 *  - RAMPS, one each side (ADA 405): running slope 1:12 (405.2), at most 30 in rise a run (405.6: the 1 m rise is two runs of 0.5 m),
 *    a 60 in (2 block) landing between (405.7), clear width 7 blocks between the rails (405.5: at least 36 in), handrails both sides
 *    (405.8) with their tops 34-38 in over the ramp (505.4; here 0.875 block = 34.4 in over the high end of each cell, 37.7 in over its low
 *    end), extending 12 in past the bottom (505.10.2), the curb wall under the rails as edge protection (405.9.2). Walking surface: steel bar
 *    grating 19-W-4 (NAAMM MBG 531).
 */
public final class GreatGate {
    public static final double RI = 7.5, RO = 9.5, CY = 1 + RI, RAIL_OVER = 0.875;
    public static final int DEPTH = 2, LAND = 3, RUN = 6, MID = 2, WIDTH = 7, DAIS_X0 = -1 - LAND, DAIS_X1 = DEPTH - 2 + LAND, DAIS_Z = (int) Math.ceil(RO) + LAND - 1,
        RAMP_LEN = 2 * RUN + MID, HALF_W = WIDTH / 2;
    public static final int X0 = DAIS_X0 - RAMP_LEN - 1, X1 = DAIS_X1 + RAMP_LEN + 1, Z0 = -DAIS_Z - 1, Z1 = DAIS_Z + 1, TOP = (int) Math.ceil(CY + RO) + 1;
    public static final String FRAME = "hopelessinfinity:gate_frame", LIP = "polished_blackstone", NODE = "sea_lantern", DAIS = "polished_deepslate", GRATE = "hopelessinfinity:steel_grate", CURB = "polished_deepslate";
    static final int ALONG_X = com.hopeless.infinity.slope.ShapeLib.orient(new int[]{0, -1, 0}, new int[]{0, 0, -1}), ALONG_Z = com.hopeless.infinity.slope.ShapeLib.orient(new int[]{0, -1, 0}, new int[]{-1, 0, 0}),
        UP_PLUS_X = com.hopeless.infinity.slope.ShapeLib.orient(new int[]{0, -1, 0}, new int[]{-1, 0, 0}), UP_MINUS_X = com.hopeless.infinity.slope.ShapeLib.orient(new int[]{0, -1, 0}, new int[]{1, 0, 0});

    /** The ring's centre is at z = 0, y = CY - 0.5 (cell centres): the aperture's lowest free row is y = 1, on the dais. */
    static double dy(int y) { return y - (CY - 0.5); }
    static boolean band(int y, int z) { double rr = Math.hypot(z, dy(y)); return rr >= RI && rr <= RO; }
    /** The ramp cell index from the dais outward (0 at the dais), or -1: on the -x side x = DAIS_X0 - 1 - i, on the +x side x = DAIS_X1 + 1 + i. */
    static int rampI(int x) { if (x < DAIS_X0) return DAIS_X0 - 1 - x; if (x > DAIS_X1) return x - DAIS_X1 - 1; return -1; }
    /** The ramp surface's height over the plaza at the high end of ramp cell i (blocks), or NaN past the ramp. */
    public static double surfHigh(int i) { if (i < 0) return 1; if (i < RUN) return 1 - i / 12.0; if (i < RUN + MID) return 0.5; if (i < RAMP_LEN) return 0.5 - (i - RUN - MID) / 12.0; return 0; }
    public static double surfLow(int i) { if (i < 0) return 1; if (i < RUN) return 1 - (i + 1) / 12.0; if (i < RUN + MID) return 0.5; if (i < RAMP_LEN) return 0.5 - (i - RUN - MID + 1) / 12.0; return 0; }

    /** 0.27.2 THE CONTROLLER: a gate controller (world/Gates) on the dais beside the ring's -z foot, inside the guard. The gate joins the
     *  network only when a player uses it (owner: "should not do anything until set by the player"). */
    public static final int CTRL_X = -3, CTRL_Y = 1, CTRL_Z = -(DAIS_Z - 1);
    public static final String CONTROLLER = "hopelessinfinity:gate_controller";
    /** The field's cells when the gate is open: both depths of the ring, every aperture cell over the dais ({x, y, z} in the gate's frame). */
    public static java.util.List<int[]> fieldCells() { java.util.List<int[]> l = new java.util.ArrayList<>(); for (int x = -1; x <= 0; x++) for (int y = 1; y <= TOP; y++) for (int z = -(int) RO; z <= (int) RO; z++) if (Math.hypot(z, dy(y)) < RI) l.add(new int[]{x, y, z}); return l; }
    /** The ring's band cells in one depth (x = -1): {y, z}, for the controller's check that the ring is whole. */
    public static java.util.List<int[]> bandCells() { java.util.List<int[]> l = new java.util.ArrayList<>(); for (int y = 1; y <= TOP; y++) for (int z = -(int) Math.ceil(RO); z <= (int) Math.ceil(RO); z++) if (band(y, z)) l.add(new int[]{y, z}); return l; }
    /** The field's centre in the gate's frame (m, the frame's block corner at 0): the aperture's middle, on the ring's middle plane x = 0. */
    public static final double FIELD_X = 0, FIELD_Y = CY, FIELD_Z = 0.5;

    /** The block at (x, y, z) in the gate's frame, or null (nothing: the plaza's own ground or air). */
    public static String at(int x, int y, int z) {
        if (x < X0 || x > X1 || z < Z0 || z > Z1 || y < 0 || y > TOP) return null;
        if (x == CTRL_X && y == CTRL_Y && z == CTRL_Z) return CONTROLLER;
        // the ring
        if ((x == -1 || x == 0) && band(y, z)) return ring(y, z);
        boolean onDais = x >= DAIS_X0 && x <= DAIS_X1 && Math.abs(z) <= DAIS_Z;
        if (onDais) { if (y == 0) return DAIS;
            boolean edgeX = (x == DAIS_X0 || x == DAIS_X1) && Math.abs(z) > HALF_W, edgeZ = Math.abs(z) == DAIS_Z;   // the perimeter but the ramp openings
            if ((edgeX || edgeZ) && (y == 1 || y == 2)) { int o = edgeZ ? ALONG_X : ALONG_Z; return y == 1 ? "shape:rail_24:" + o + ":" + CURB : "shape:rail_2:" + o + ":" + CURB; }   // guard top 1 + 2/24 = 1.083 block = 42.6 in over the dais
            return null; }
        int i = rampI(x); if (i < 0 || Math.abs(z) > HALF_W + 1) return null;
        if (i >= RAMP_LEN) { if (i == RAMP_LEN && Math.abs(z) == HALF_W + 1 && y == 0) return "shape:rail_21:" + ALONG_X + ":" + CURB; return null; }   // the 12 in extension past the bottom (21/24 = 0.875 over the plaza)
        boolean minus = x < DAIS_X0;
        if (Math.abs(z) <= HALF_W) {   // the walking surface
            if (y != 0) return null;
            if (i >= RUN && i < RUN + MID) return "shape:half_block:" + ALONG_X + ":" + GRATE;
            int k = i < RUN ? 11 - i : 5 - (i - RUN - MID); return "shape:ramp12_" + k + ":" + (minus ? UP_PLUS_X : UP_MINUS_X) + ":" + GRATE; }
        // the rail columns (|z| = HALF_W + 1): a handrail 0.875 over the cell's high end on a curb wall
        int n = (int) Math.round((surfHigh(i) + RAIL_OVER) * 24);
        if (n <= 24) return y == 0 ? "shape:rail_" + n + ":" + ALONG_X + ":" + CURB : null;
        if (n - 24 < 3) return y == 0 ? "shape:rail_24:" + ALONG_X + ":" + CURB : y == 1 ? "shape:rail_" + (n - 24) + ":" + ALONG_X + ":" + CURB : null;   // the bar crosses the block's top: split
        return y == 0 ? CURB : y == 1 ? "shape:rail_" + (n - 24) + ":" + ALONG_X + ":" + CURB : null; }

    static String ring(int y, int z) {
        double d = dy(y), rr = Math.hypot(z, d); String mat = rr < RI + 0.8 ? LIP : FRAME;
        double th = Math.atan2(d, z); for (int k = 0; k < 8; k++) { double tk = Math.toRadians(22.5 + 45 * k), da = Math.abs(Math.IEEEremainder(th - tk, 2 * Math.PI)); if (rr > RO - 1.0 && da * rr < 0.75) mat = NODE; }
        if (y < 1) return mat;   // set into the dais
        String sh = FIT.computeIfAbsent(y * 1000 + z, k -> fit(y, z)); return sh.isEmpty() ? mat : "shape:" + sh + ":" + mat; }
    static final java.util.Map<Integer, String> FIT = new java.util.concurrent.ConcurrentHashMap<>();
    static final String[] CAND = {"slope", "half_slope", "half_slope_inv", "round_slope", "round_inv_corner", "corner_square"};
    /** 0.27.1 the best shape for ring cell (y, z): "" a whole block, else "<shape>:<turn>". The band is sampled at 8 x 8 points in the
     *  (z, y) plane; each candidate (and turn) whose collision is the same all along x (the ring's depth) is scored by the samples it gets
     *  wrong; a whole block scores the samples outside the band. */
    static String fit(int y, int z) { boolean[] in = new boolean[64]; int nIn = 0;
        for (int a = 0; a < 8; a++) for (int b = 0; b < 8; b++) { double zz = z + (a + 0.5) / 8 - 0.5, yy = dy(y) + (b + 0.5) / 8 - 0.5, rr = Math.hypot(zz, yy); in[a * 8 + b] = rr >= RI && rr <= RO; if (in[a * 8 + b]) nIn++; }
        int best = 64 - nIn; String out = "";
        for (String c : CAND) { Integer si = com.hopeless.infinity.slope.ShapeLib.INDEX.get(c); if (si == null) continue;
            for (int o = 0; o < 24; o++) { boolean[][][] v = new boolean[8][8][8]; for (int[] bx : com.hopeless.infinity.slope.ShapeLib.boxes(si, o)) for (int i = bx[0] / 2; i < bx[3] / 2; i++) for (int j = bx[1] / 2; j < bx[4] / 2; j++) for (int k = bx[2] / 2; k < bx[5] / 2; k++) v[i][j][k] = true;
                boolean uniform = true; for (int i = 1; i < 8 && uniform; i++) for (int j = 0; j < 8 && uniform; j++) for (int k = 0; k < 8; k++) if (v[i][j][k] != v[0][j][k]) { uniform = false; break; }
                if (!uniform) continue; int wrong = 0; for (int a = 0; a < 8; a++) for (int b = 0; b < 8; b++) if (v[0][b][a] != in[a * 8 + b]) wrong++;   // cell z = a (east-west of the ring: world z), y = b
                if (wrong < best - 2) { best = wrong; out = c + ":" + o; } } }   // a shape must beat the plain block by more than 2 of 64 samples (CHOSEN: no fiddly shapes for a sliver)
        return out; }
}
