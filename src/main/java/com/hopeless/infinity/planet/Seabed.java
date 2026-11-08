package com.hopeless.infinity.planet;

/**
 * 0.5.0: the sea floor and the shore strip, ported from Hopeless Seabed's Core/SeabedPlan.cs (our Space Engineers plugin, v0.26.0), value
 * for value. Pure Java (no game classes): the chunk generator, the tests and the preview renderer run the same code.
 *
 * Published values (each GAME value starts from the one beside it; the owner's choice, 2026-10-31: "enhance the numbers a bit to allow
 * for kelp and other features to be more prevalent under water"):
 *  - kelp on rocky reef 2-40 m deep (published to 30 m, Aquarium of the Pacific; "prefers depths less than 40 m", starthrower), in water
 *    5-24 C (published 5-20 C, starthrower; Fleischman, Bolton & Rothman 2020);
 *  - seagrass on soft bottom from 0.5 m down to 11 % of surface light (Duarte 1991): z = ln(1/0.11)/Kd, Kd 0.07 1/m (published example
 *    0.15 1/m) -> 31.5 m;
 *  - rock where the floor is steeper than 10 deg (published-based 15), bare above 70 deg;
 *  - shore strip 2.5 m above to 0.5 m under the water: sand beach under 6 deg (sand faces 1.1-5.7 deg, USGS OFR 82-537), shingle to 20 deg
 *    (gravel beaches are steeper: Bascom 1951), rock above;
 *  - salt marsh on salt shore flatter than 2 deg from 1 m above to 0.5 m under the water (Chesapeake Bay Program; low marsh to 0.3 m);
 *  - ice shore where the annual mean is at or below 0 C, 2.5 m above to 2 m under the water (bottomfast ice; Arctic 35(2) 1982);
 *  - coral reef on reef rock 0.5-30 m deep in water 20-30 C (published: survives above 18 C, best 23-29 C, NOAA); crest under 6 m
 *    (elkhorn, NOAA Fisheries), table corals to 25 m;
 *  - deep-sea floor from 200 m (NOAA Ocean Exploration): pale calcareous ooze (Globigerina ooze, Sand Atlas).
 *
 * Infinity's water is all salt sea at sea level (no lakes yet), so the fresh-water kinds are kept for later and not used.
 * Measured on seed 1 with tools/SeabedTest (the area of each kind on the floor to 60 m).
 */
public final class Seabed {
    private Seabed() {}
    public enum Kind { NONE, SEAGRASS, KELP, SAND, BEACH, SHINGLE, SALT_MARSH, LOW_MARSH, ICE_SHORE, CORAL_CREST, CORAL_SLOPE, CORAL_DEEP, DEEP_OOZE, SUBTIDAL_ROCK }

    // SeabedSettings defaults (GAME values; published values in the class comment)
    public static double KELP_MIN = 2, KELP_MAX = 40, SEAGRASS_MIN = 0.5, SEAGRASS_LIGHT = 0.11, WATER_KD = 0.07;
    public static double ROCK_SLOPE_DEG = 10, MAX_SLOPE_DEG = 70, KELP_MIN_C = 5, KELP_MAX_C = 24;
    public static double BEACH_TOP = 2.5, BEACH_BOTTOM = 0.5, BEACH_SAND_DEG = 6, SHINGLE_DEG = 20;
    public static double MARSH_TOP = 1.0, MARSH_LOW_TOP = 0.3, MARSH_DEG = 2;
    public static double ICE_MEAN_C = 0, ICE_FAST_DEPTH = 2;
    public static double CORAL_MIN_C = 20, CORAL_MAX_C = 30, CORAL_MIN = 0.5, CORAL_MAX = 30, CORAL_CREST = 6, CORAL_SLOPE = 25;
    public static double DEEP_MIN = 200;
    /** Giant kelp canopy: 3-30 m (Sanctuary SIMoN); game from 4 m. Shallower: the low Laminaria canopy (stipe ~1-1.5 m, frond to 1 m; MarLIN). */
    public static double CANOPY_TOP = 4, CANOPY_BOTTOM = 30;

    public static double seagrassMax() { return Math.log(1.0 / SEAGRASS_LIGHT) / WATER_KD; }
    static double cos(double deg) { return Math.cos(Math.toRadians(deg)); }

    /**
     * depth: metres of water over the ground (negative: the ground is above the water); cosSlope: the ground normal's up part (1 flat,
     * 0 vertical); meanC: the water's annual mean temperature (C; the air at the sea surface, as Seabed uses Water's climate grid).
     * Seabed's Classify for a salt sea, then its ice, coral and kelp temperature rules (Game/SeabedVoxel order: ice, coral, kelp water).
     */
    public static Kind classify(double depth, double cosSlope, double meanC) {
        Kind k = salt(depth, cosSlope);
        if (k == Kind.NONE) return k;
        if (meanC <= ICE_MEAN_C && depth >= -BEACH_TOP && depth < ICE_FAST_DEPTH && cosSlope > cos(SHINGLE_DEG)) return Kind.ICE_SHORE;
        boolean reefRock = depth >= CORAL_MIN && depth <= CORAL_MAX && cosSlope <= cos(ROCK_SLOPE_DEG) && cosSlope >= cos(MAX_SLOPE_DEG);
        if (reefRock && meanC >= CORAL_MIN_C && meanC <= CORAL_MAX_C) return depth < CORAL_CREST ? Kind.CORAL_CREST : depth < CORAL_SLOPE ? Kind.CORAL_SLOPE : Kind.CORAL_DEEP;
        if (k == Kind.KELP && (meanC < KELP_MIN_C || meanC > KELP_MAX_C)) return Kind.SUBTIDAL_ROCK;
        return k;
    }
    static Kind salt(double depth, double cosSlope) {
        if (depth >= -MARSH_TOP && depth < BEACH_BOTTOM && cosSlope > cos(MARSH_DEG)) return depth >= -MARSH_LOW_TOP ? Kind.LOW_MARSH : Kind.SALT_MARSH;
        if (depth >= -BEACH_TOP && depth < BEACH_BOTTOM)
            return cosSlope > cos(BEACH_SAND_DEG) ? Kind.BEACH : cosSlope > cos(SHINGLE_DEG) ? Kind.SHINGLE : depth > 0 ? Kind.SUBTIDAL_ROCK : Kind.NONE;
        if (depth <= 0) return Kind.NONE;
        boolean soft = cosSlope > cos(ROCK_SLOPE_DEG), rock = !soft && cosSlope >= cos(MAX_SLOPE_DEG);
        if (rock && depth >= KELP_MIN && depth <= KELP_MAX) return Kind.KELP;
        if (soft && depth >= SEAGRASS_MIN && depth <= seagrassMax()) return Kind.SEAGRASS;
        if (soft) return depth >= DEEP_MIN ? Kind.DEEP_OOZE : Kind.SAND;
        return Kind.SUBTIDAL_ROCK;
    }
    // ---- living things (world/SeabedLife places them as blocks; tools/SeabedPreview draws the same choices). Values and sources: SeabedLife.
    public static double KELP_PLANT = 0.175, SEAGRASS = 0.60, TALL_SEAGRASS = 0.30, CORAL = 0.50, CORAL_DEEP_PLANT = 0.20, BOULDER = 0.30;
    public enum Plant { NONE, KELP, SEAGRASS, TALL_SEAGRASS, CORAL_PLANT, CORAL_FAN, CORAL_BLOCK_ONLY, GRASS, TALL_GRASS, BOULDER }
    /** What one column grows: the plant, its height in blocks (kelp) and the coral species (0 tube, 1 brain, 2 bubble, 3 fire, 4 horn). */
    public record Life(Plant plant, int height, int coral) { static final Life NONE = new Life(Plant.NONE, 0, 0); }
    /** The choice for a column of this kind with 'water' blocks of water over the ground (<= 0: dry) and depth in real metres. */
    public static Life life(long seed, int x, int z, Kind kind, int water, double depth) {
        switch (kind) {
            case KELP -> { if (water < 2 || rnd(seed, x, z, 1) >= KELP_PLANT) return Life.NONE;
                int h = depth < CANOPY_TOP ? 2 : depth <= CANOPY_BOTTOM ? water : (int) Math.round(water * (0.4 + 0.4 * rnd(seed, x, z, 2))); return new Life(Plant.KELP, Math.max(1, Math.min(h, water)), 0); }
            case SEAGRASS -> { if (water < 1 || rnd(seed, x, z, 3) >= SEAGRASS) return Life.NONE; return water >= 2 && rnd(seed, x, z, 4) < TALL_SEAGRASS ? new Life(Plant.TALL_SEAGRASS, 2, 0) : new Life(Plant.SEAGRASS, 1, 0); }
            case CORAL_CREST, CORAL_SLOPE, CORAL_DEEP -> { if (water < 2 || rnd(seed, x, z, 5) >= CORAL) return Life.NONE;
                int sp = kind == Kind.CORAL_CREST ? 4 : kind == Kind.CORAL_DEEP ? 1 : (int) (rnd(seed, x, z, 6) * 5);
                if (water < 3 || (kind == Kind.CORAL_DEEP && rnd(seed, x, z, 7) >= CORAL_DEEP_PLANT)) return new Life(Plant.CORAL_BLOCK_ONLY, 1, sp);
                return new Life(kind == Kind.CORAL_SLOPE ? Plant.CORAL_FAN : Plant.CORAL_PLANT, 2, sp); }
            case SALT_MARSH -> { return water > 0 ? Life.NONE : new Life(Plant.GRASS, 1, 0); }
            case LOW_MARSH -> { return water > 0 ? Life.NONE : new Life(Plant.TALL_GRASS, 2, 0); }
            case ICE_SHORE -> { return rnd(seed, x, z, 8) < BOULDER ? new Life(Plant.BOULDER, 1, 0) : Life.NONE; }
            default -> { return Life.NONE; }
        } }
    /** A hash of the world seed, the block position and a salt to [0, 1): a chunk always gets the same life. */
    public static double rnd(long seed, int x, int z, int salt) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        h ^= h >>> 33; h *= 0xFF51AFD7ED558CCDL; h ^= h >>> 33; h *= 0xC4CEB9FE1A85EC53L; h ^= h >>> 33; return (h >>> 11) * 0x1.0p-53; }
    /** The ground's up part from the height differences to the neighbours 1 m away (central differences, metres). */
    public static double cosSlope(double hW, double hE, double hN, double hS) {
        double gx = (hE - hW) / 2, gz = (hS - hN) / 2; return 1 / Math.sqrt(1 + gx * gx + gz * gz); }
}
