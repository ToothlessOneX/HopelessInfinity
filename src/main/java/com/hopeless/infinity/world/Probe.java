package com.hopeless.infinity.world;

/** Counters the log line, /hinf and the tests read: every moving part of the mod reports here. */
public final class Probe {
    private Probe() {}
    public static volatile long caveBlocks, caveWater, karstCols, tubeCols, drips, caveNanos, seaCave, seaCaveCols;   // 0.19.0 caves
    public static volatile long metroBlocks, ringNanos, setNanos, buildingNanos, buildingBlocks, slopes, slopeNanos, slopeNoMaterial, slopeByWater, yWritten, yMismatch, yRead, poiSearches, wholeSections, blocksSet, chunks, planetChunks, genNanos, crossings, crossFails, tickets, gravityLiving, gravityOther, fallAdjusted, placeBlocked, errors, spawnSearch, tidesLinked, tidesClimate;
    public static volatile String lastCross = "none";
    /** 0.5.0 sea floor: columns of each Seabed kind laid down, time spent classifying, and what SeabedLife placed or removed. */
    public static final long[] seabed = new long[com.hopeless.infinity.planet.Seabed.Kind.values().length];
    public static volatile long seabedNanos, lifeChunks, lifeMissed, lifeNanos, vanillaStopped, kelpPlants, kelpBlocks, seagrass, tallSeagrass, coralBlocks, coralPlants, coralFans, marshGrass, boulders;
    public static String seabedLine() {
        StringBuilder b = new StringBuilder(String.format("sea floor: classify %d ms, life %d chunks (%d not remembered, %d ms), vanilla water plants stopped %d | kelp %d plants %d blocks, seagrass %d tall %d, coral %d blocks %d plants %d fans, marsh grass %d, ice-shore boulders %d | columns",
            seabedNanos / 1000000, lifeChunks, lifeMissed, lifeNanos / 1000000, vanillaStopped, kelpPlants, kelpBlocks, seagrass, tallSeagrass, coralBlocks, coralPlants, coralFans, marshGrass, boulders));
        for (var k : com.hopeless.infinity.planet.Seabed.Kind.values()) if (seabed[k.ordinal()] > 0) b.append(' ').append(k).append(' ').append(seabed[k.ordinal()]);
        return b.toString(); }
    /** 0.21.0 rivers and lakes: the home planet's drainage map and the columns made from it. */
    public static String riverLine() { com.hopeless.infinity.planet.PlanetWorld w = com.hopeless.infinity.planet.PlanetWorld.current(); com.hopeless.infinity.planet.Rivers r = w == null ? null : w.terrain.rivers;
        String m = r == null ? "no drainage map" : String.format("map built in %d ms: %d river cells, %d lake cells, %d salt-flat cells, land runoff %.0f km3/yr", r.buildMs, r.riverCells, r.lakeCells, r.dryCells, r.landRunoffKm3);
        return String.format("rivers (%s): %s; columns made: %d river, %d lake, %d salt flat", com.hopeless.infinity.planet.Terrain.riversOn ? "on" : "off", m, com.hopeless.infinity.planet.Column.RIVER_COLS.sum(), com.hopeless.infinity.planet.Column.LAKE_COLS.sum(), com.hopeless.infinity.planet.Column.DRY_COLS.sum()); }
    public static String caveLine() { return String.format("caves: %d blocks opened (%d flooded below the water table), %d karst columns, %d lava-tube columns, %d sea-cave columns (%d blocks), %d dripstones, %.2f ms a planet chunk", caveBlocks, caveWater, karstCols, tubeCols, seaCaveCols, seaCave, drips, planetChunks == 0 ? 0 : caveNanos / 1e6 / planetChunks); }
    /** 0.27.0 the megacities: towers' blocks placed, their part of the buildings time, graded pad columns. */
    static String metroLine() { var w = com.hopeless.infinity.planet.PlanetWorld.current(); var m = w == null ? null : w.metroIfMade();
        return m == null ? "metro not made | " : String.format("metro: %d plans, %d tower blocks (%.1f ms in all), %d pad columns, %d gate blocks, %d HQ front blocks | ", m.plans.size(), metroBlocks, m.chunkNs.sum() / 1e6, com.hopeless.infinity.planet.Column.METRO_COLS.sum(), m.gateBlocks.sum(), m.frontBlocks.sum()); }
    public static String line() {
        return String.format("chunk phases: column ring %.3f ms, block-set loop %.3f ms (%.1f ns a block), buildings %.3f ms (%d blocks) a chunk | ", ringNanos / 1e6 / Math.max(1, planetChunks), setNanos / 1e6 / Math.max(1, planetChunks), setNanos / (double) Math.max(1, blocksSet), buildingNanos / 1e6 / Math.max(1, planetChunks), buildingBlocks) + metroLine() + String.format("chunks %d (planet %d, avg %.2f ms, whole sections %d, blocks set %d) | slopes %d (%d ms, chosen %d flat %d too steep %d, no material %d, by water %d) | rivers: %d level profiles (%.0f ms), %d beds stepped under water | saves Y int %d read %d mismatch %d | poi searches %d | crossings %d (failed %d) last %s | edge tickets %d | gravity on %d living, %d other pushes | falls scaled %d | void builds blocked %d%s",
            chunks, planetChunks, chunks == 0 ? 0 : genNanos / 1e6 / chunks, wholeSections, blocksSet, slopes, slopeNanos / 1000000, com.hopeless.infinity.slope.Slopes.placed, com.hopeless.infinity.slope.Slopes.flat, com.hopeless.infinity.slope.Slopes.tooSteep, slopeNoMaterial, slopeByWater, com.hopeless.infinity.planet.Rivers.PROFILES.sum(), com.hopeless.infinity.planet.Rivers.PROFILE_NS.sum() / 1e6, com.hopeless.infinity.planet.Column.WET_STEP.sum(), yWritten, yRead, yMismatch, poiSearches, crossings, crossFails, lastCross, tickets, gravityLiving, gravityOther, fallAdjusted, placeBlocked, errors > 0 ? " | ERRORS " + errors : ""); }
}
