package com.hopeless.infinity.town;

/** 0.36.0: generator positions by 512-block cell, so "is one within R" looks at the cells within R, not at every generator. Pure Java
 *  (the offline check runs it against a plain loop). Keys are the callers' (BlockPos.asLong); x and z are given with them. */
public final class GridIndex {
    final java.util.Map<Long, java.util.List<long[]>> cells = new java.util.HashMap<>();
    static long cell(long x, long z) { return (Math.floorDiv(x, 512L) << 32) ^ (Math.floorDiv(z, 512L) & 0xFFFFFFFFL); }
    public void add(long key, int x, int z) { cells.computeIfAbsent(cell(x, z), q -> new java.util.ArrayList<>()).add(new long[]{key, x, z}); }
    public void remove(long key, int x, int z) { long c = cell(x, z); var l = cells.get(c); if (l == null) return; l.removeIf(e -> e[0] == key); if (l.isEmpty()) cells.remove(c); }
    public void clear() { cells.clear(); }
    /** True when any position lies within r blocks (horizontal) of (x, z). */
    public boolean anyWithin(int x, int z, int r) {
        long r2 = (long) r * r; int R = r / 512 + 1; long cx = Math.floorDiv((long) x, 512L), cz = Math.floorDiv((long) z, 512L);
        for (long i = cx - R; i <= cx + R; i++) for (long j = cz - R; j <= cz + R; j++) { var l = cells.get((i << 32) ^ (j & 0xFFFFFFFFL)); if (l == null) continue;
            for (long[] e : l) { long dx = e[1] - x, dz = e[2] - z; if (dx * dx + dz * dz <= r2) return true; } }
        return false; }
}
