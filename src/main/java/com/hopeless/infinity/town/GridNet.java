package com.hopeless.infinity.town;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 0.34.0: the town power grid without other mods. Every lit geothermal generator is written here (the world's data,
 * hopelessinfinity_power) when its chunk loads or its lava comes or goes; it stays written while its chunk is unloaded. A place is on
 * the grid when a lit generator is within REACH blocks (a regional grid: towns with no plant draw from the city plant, as
 * Settle.gridSite does). Grid relays, battery banks and standby generators ask live().
 */
public final class GridNet extends SavedData {
    public static final int REACH = 3000;
    public static volatile long adds, removes, asks, liveAnswers;   // probes
    final java.util.Set<Long> gens = new java.util.HashSet<>();
    /** 0.36.0: the generators by 512-block cell (GridIndex, built from gens when first needed). */
    final GridIndex cells = new GridIndex(); boolean cellsBuilt;
    void index(long k, boolean add) { if (add) cells.add(k, BlockPos.getX(k), BlockPos.getZ(k)); else cells.remove(k, BlockPos.getX(k), BlockPos.getZ(k)); }
    void buildCells() { if (cellsBuilt) return; cells.clear(); for (long k : gens) index(k, true); cellsBuilt = true; }
    /** Lit generators placed by world generation (off the server thread, no level there); written to the level's data at its next ask. */
    public static final java.util.Map<Integer, java.util.Set<Long>> PENDING_BY_FACE = new java.util.concurrent.ConcurrentHashMap<>();   // 0.26.1: per planet face (each face is its own level)
    public static void pending(int face, long pos) { PENDING_BY_FACE.computeIfAbsent(face, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(pos); }
    public static volatile long fromWorldgen;

    static GridNet of(ServerLevel l) {
        GridNet g = l.getDataStorage().computeIfAbsent(GridNet::load, GridNet::new, "hopelessinfinity_power");
        java.util.Set<Long> PENDING = l.getChunkSource().getGenerator() instanceof com.hopeless.infinity.world.PlanetGenerator pg && pg.body == 0 ? PENDING_BY_FACE.get(pg.face) : null;
        if (PENDING != null && !PENDING.isEmpty()) synchronized (g.gens) {
            for (java.util.Iterator<Long> it = PENDING.iterator(); it.hasNext(); ) { long k = it.next(); it.remove(); if (g.gens.add(k)) { fromWorldgen++; adds++; g.setDirty(); if (g.cellsBuilt) g.index(k, true); } } }
        return g; }
    static GridNet load(CompoundTag t) { GridNet g = new GridNet(); for (long v : t.getLongArray("gens")) g.gens.add(v); return g; }
    @Override public CompoundTag save(CompoundTag t) { t.putLongArray("gens", gens.stream().mapToLong(Long::longValue).toArray()); return t; }

    public static void set(ServerLevel l, BlockPos p, boolean lit) {
        GridNet g = of(l); long k = p.asLong();
        synchronized (g.gens) { if (lit ? g.gens.add(k) : g.gens.remove(k)) { g.setDirty(); if (lit) adds++; else removes++; if (g.cellsBuilt) g.index(k, lit); } } }
    /** True when a lit generator is within REACH blocks (horizontal) of p. */
    public static boolean live(ServerLevel l, BlockPos p) {
        asks++; if (com.hopeless.infinity.game.Outage.noPower(l, p.getX(), p.getZ())) return false;   // 0.38.0: an outage
        return g(of(l), p.getX(), p.getZ()); }
    /** The test itself (0.36.0: by cells), for live() and the offline check. */
    static boolean g(GridNet g, int px, int pz) { synchronized (g.gens) { g.buildCells(); boolean r = g.cells.anyWithin(px, pz, REACH); if (r) liveAnswers++; return r; } }
    public static int count(ServerLevel l) { GridNet g = of(l); synchronized (g.gens) { return g.gens.size(); } }
    public static String probe() { return "power grid: generator adds " + adds + " (from world generation " + fromWorldgen + ")" + " removes " + removes + " asks " + asks + " live " + liveAnswers; }
}
