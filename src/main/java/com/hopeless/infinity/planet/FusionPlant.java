package com.hopeless.infinity.planet;

/** 0.28.3 THE FUSION PLANT of a megacity or large city (owner, 2026-11-04: "megacities need fusion (or another method) to be realistically
 *  powered; add a fusion plant housed in its own building that fits the cityscape, built with the new blocks, as a visual feature").
 *  SIZED TO THE CITY (all STATED unless marked): US electricity use 4,014 TWh in 2023 (EIA Short-Term Energy Outlook) for 334.9 million
 *  people (US Census, 2023) is 1.368 kW a person on average; a plant runs at the US nuclear fleet's 93 % capacity factor (EIA, 2023);
 *  a unit is Commonwealth Fusion Systems' ARC, 400 MW to the grid. Units = ceil(people x 1.368 kW / 0.93 / 400 MW): Saltridge City's
 *  17 million people need 63. The plant stands on superblocks at the city's edge, six units a superblock (two rows of three 70 x 105 m
 *  plots: CHOSEN), level with the streets.
 *  ONE UNIT (CHOSEN where not marked): the reactor hall half the size of ITER's tokamak complex (STATED 120 x 80 x 80 m; ARC's machine is
 *  about half ITER's across), 60 m long and 40 m wide, walls 20 m, a barrel vault of radius 20 m on top (its curve fitted with the ported
 *  shaped blocks, best fit per cell as on the great gate), the tokamak's cryostat inside (a 16 m drum, magnets round it); behind it the
 *  turbine hall (60 x 35 m, 22 m high, a window band), and the switchyard (transformers every 10 m).
 *  Local coordinates of a unit: x along the plot (-35..34), z across it (0..104, the reactor hall at the front), y up from the pad. */
public final class FusionPlant {
    public static final double KW_PER_PERSON = 4014e9 / 334.9e6 / 8766.0, CAPACITY = 0.93, UNIT_MW = 400;
    public static final int PLOT_X = 70, PLOT_Z = 105, PER_BLOCK = 6, HALL_X = 30, HALL_W = 40, WALL = 20, VAULT_R = 20, TURB_Z0 = 45, TURB_Z1 = 80, TURB_H = 22, YARD_Z0 = 85, YARD_Z1 = 100, CRYO_R = 8, CRYO_H = 14;
    public static final java.util.concurrent.atomic.LongAdder BLOCKS = new java.util.concurrent.atomic.LongAdder();
    /** Units a city of pop people needs. */
    public static int units(long pop) { return (int) Math.ceil(pop * KW_PER_PERSON / CAPACITY / (UNIT_MW * 1000)); }
    /** The unit's block at (x, y, z), or null (open air over the plot's paving, which is y 0). */
    public static String at(int x, int y, int z) {
        if (x < -PLOT_X / 2 || x >= PLOT_X / 2 || z < 0 || z >= PLOT_Z || y < 0) return null;
        if (y == 0) return z < YARD_Z0 || z >= YARD_Z1 ? "gray_concrete" : "gravel";   // the plot's paving; gravel in the switchyard
        // the reactor hall: walls, the vault, the cryostat and magnets inside
        boolean inHall = Math.abs(x) <= HALL_X && z >= 0 && z < HALL_W;
        if (inHall) { int zc = z - HALL_W / 2; boolean endWall = Math.abs(x) == HALL_X;
            if (y <= WALL) { if (endWall || z == 0 || z == HALL_W - 1) return (y >= 3 && y <= 4 && endWall && Math.abs(zc) <= 1) ? "air" : y % 5 == 0 ? "light_gray_concrete" : "white_concrete";   // doors in the end walls
                return inside(x, y, z); }
            double d = y - WALL, rr = Math.hypot(zc + 0.5, d); if (rr > VAULT_R + 0.6) return null; if (rr < VAULT_R - 1) return endWall ? "white_concrete" : inside(x, y, z);   // the end walls close the vault
            String sh = vault(y - WALL, zc); if (sh == null) return null; return sh.isEmpty() ? "smooth_quartz" : "shape:" + sh + ":smooth_quartz"; }
        // the turbine hall
        if (Math.abs(x) <= HALL_X && z >= TURB_Z0 && z < TURB_Z1 && y <= TURB_H) { boolean wall = Math.abs(x) == HALL_X || z == TURB_Z0 || z == TURB_Z1 - 1;
            if (y == TURB_H) return "gray_concrete"; if (wall) return y >= 12 && y <= 16 ? (Math.floorMod(x + z, 4) == 0 ? "light_gray_concrete" : "light_blue_stained_glass") : "light_gray_concrete";
            if (y <= 6 && Math.abs(z - (TURB_Z0 + TURB_Z1) / 2) <= 3 && Math.abs(x) <= 22) return y == 6 ? "iron_block" : "light_gray_concrete";   // the turbine-generator set on its pedestal
            return null; }
        // the duct from hall to turbines
        if (Math.abs(x) <= 3 && z >= HALL_W && z < TURB_Z0 && y >= 6 && y <= 9) return y == 6 || y == 9 || Math.abs(x) == 3 ? "iron_block" : null;
        // the switchyard: transformers every 10 m
        if (z >= YARD_Z0 + 3 && z < YARD_Z1 - 3 && Math.floorMod(x, 10) <= 2 && y <= 4) return y == 4 ? "iron_bars" : "iron_block";
        return null; }
    /** Inside the hall: the cryostat (a drum of radius CRYO_R round the hall's centre), the magnet coils on it, a floor, air. */
    static String inside(int x, int y, int z) { double r = Math.hypot(x, z - HALL_W / 2.0 + 0.5);
        if (r <= CRYO_R && y <= CRYO_H) return r > CRYO_R - 1 ? (y % 4 == 2 ? "light_blue_concrete" : "iron_block") : y == CRYO_H ? "iron_block" : null;
        if (y == 1 && r > CRYO_R + 2 && Math.floorMod(x, 8) == 0 && Math.floorMod(z, 8) == 4) return "sea_lantern";
        return null; }   // 0.28.3 open space is left to the graded ground's own air (measured: writing it cost 692,000 blocks a superblock)
    /** The vault cell (dy over the wall top, zc across from the hall's middle): "" a whole block, a fitted "<shape>:<turn>", or null (outside the shell). */
    static String vault(int dy, int zc) { return VFIT.computeIfAbsent(dy * 1000 + zc, k -> ShapeFit.band((zz, yy) -> { double rr = Math.hypot(zz + 0.5, yy); return rr >= VAULT_R - 1 && rr <= VAULT_R; }, zc, dy)); }
    static final java.util.Map<Integer, String> VFIT = new java.util.concurrent.ConcurrentHashMap<>();
    private FusionPlant() {}
}
