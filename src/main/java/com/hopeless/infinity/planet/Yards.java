package com.hopeless.infinity.planet;

import java.util.*;

/** 0.29.6 THE YARDS' BUILDINGS: at each city freight yard (Roads.yard: the body tracks on the main track's +off side), light towers along the
 *  yard's outer edge and a yard office on the main track's other side.
 *   - Light towers: high-mast lighting, used for freight yards, on poles of about 15 to 50 m (STATED: Wikipedia, High-mast lighting; the
 *     first towers were 100 ft). CHOSEN: 30 blocks (about 100 ft) of pole with a lamp head, every 150 m along the yard's outer edge, 2.5 m past
 *     the last track's ballast.
 *   - Yard office: a one-storey brick office beside the yard (after photos of US yard offices: low brick buildings with a window band toward
 *     the tracks). CHOSEN: 12 x 8 m, 4 blocks to the roof, a flat roof, windows on all four sides, a door, at the middle of the yard 10 m
 *     off the main track on the side away from the body tracks.
 *  Found from the chunk: the rail hits at five points of the chunk name the yards near it; each yard's features are worked out once and kept. */
public final class Yards {
    private Yards() {}
    public static boolean on = true;
    public static final double LAMP_STEP_M = 150, LAMP_OUT_M = 2.5, OFFICE_OFF_M = 16; public static final int LAMP_H = 30, OFFICE_W = 12, OFFICE_D = 8, OFFICE_H = 4;
    /** A yard's features in face block coordinates: lamps {x, y, z} and the office {x0, z0, y} (its floor y; axis-aligned, OFFICE_W along x). */
    public record Yard(int face, List<int[]> lamps, int[] office) {}
    static final Map<Long, Optional<Yard>> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.concurrent.atomic.LongAdder LAMPS = new java.util.concurrent.atomic.LongAdder(), OFFICES = new java.util.concurrent.atomic.LongAdder();

    /** The yard at an edge's end, or null. */
    public static Yard yard(PlanetWorld w, int edge, int end) { return CACHE.computeIfAbsent(((long) edge << 1) | end, k -> Optional.ofNullable(make(w, edge, end))).orElse(null); }
    static Yard make(PlanetWorld w, int edge, int end) { Roads rd = w.roads(); Roads.Edge e = rd.edges.get(edge); if (!rd.yardAt(e, end)) return null; Roads.Route r = rd.route(edge); int n = rd.yardTracks(e, end); double Ly = Roads.yardLen(n), Lr = r.along[r.n() - 1]; if (Lr < 2 * Ly) return null;
        double outer = Roads.yardOuter(n) + 3.5 + LAMP_OUT_M; List<int[]> lamps = new ArrayList<>(); int[] office = null; int face = -1;
        for (double u = 60; u <= Ly - 60; u += LAMP_STEP_M) { double[] q = place(w, rd, r, end == 0 ? u : Lr - u, outer); if (q == null) continue; face = (int) q[0]; lamps.add(new int[]{(int) Math.floor(q[1]), (int) Math.floor(q[3]), (int) Math.floor(q[2])}); }
        double[] q = place(w, rd, r, end == 0 ? Ly / 2 : Lr - Ly / 2, -OFFICE_OFF_M); if (q != null) { face = (int) q[0]; office = new int[]{(int) Math.floor(q[1]) - OFFICE_W / 2, (int) Math.floor(q[2]) - OFFICE_D / 2, (int) Math.floor(q[3])}; }
        return face < 0 ? null : new Yard(face, lamps, office); }
    /** The face block point off the line at an along: {face, x, z, y (the yard's level + 1)}; the side's sign found from the line's own hit. */
    static double[] place(PlanetWorld w, Roads rd, Roads.Route r, double along, double off) { int i = 0; for (int k = 0; k < r.n(); k++) if (Math.abs(r.along[k] - along) < Math.abs(r.along[i] - along)) i = k; if (i + 1 >= r.n()) i = r.n() - 2;
        double[] a = w.cube.locate(r.p(i)), b = w.cube.locate(r.p(i + 1)); if ((int) a[0] != (int) b[0]) return null; double dx = b[1] - a[1], dz = b[2] - a[2], L = Math.hypot(dx, dz); if (L < 1e-9) return null; dx /= L; dz /= L;
        double nx = -dz, nz = dx; double[] s3 = w.cube.spherePoint((int) a[0], a[1] + nx * 3, a[2] + nz * 3); double sg = 1; if (s3 != null) for (Roads.Hit h : rd.hits(s3)) if (h.edge == r.e.id) { sg = h.off > 0 ? 1 : -1; break; }
        double px = a[1] + nx * off * sg, pz = a[2] + nz * off * sg;
        for (int it = 0; it < 4; it++) { double[] sp = w.cube.spherePoint((int) a[0], px, pz); if (sp == null) break; Roads.Hit hh = null; for (Roads.Hit h : rd.hits(sp)) if (h.edge == r.e.id) { hh = h; break; } if (hh == null) break;   /* settle on the wanted offset, measured from the line's own hit (the samples' chord is not square to the line) */
            double err = off - hh.off; if (Math.abs(err) < 0.2) break; px += nx * err * sg; pz += nz * err * sg; }
        return new double[]{a[0], px, pz, Math.floor(Terrain.toYd(r.z[i])) + 1}; }

    /** The yards' blocks in the chunk [x0, x1) x [z0, z1) of face f. */
    public static void chunk(PlanetWorld w, int f, int x0, int z0, int x1, int z1, Metro.Sink out) {
        if (!on || !Roads.on || w.body != 0) return; Roads rd = w.roadsIfMade(); if (rd == null) return; Set<Long> seen = new HashSet<>();
        int[][] pts = {{x0 + 8, z0 + 8}, {x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}};
        for (int[] p : pts) { double[] s = w.cube.spherePoint(f, p[0] + 0.5, p[1] + 0.5); if (s == null) continue;
            for (Roads.Hit h : rd.hits(s)) { if (h.cls != Roads.Cls.RAIL || h.kind != 0 || rd.yard(h) == null) continue; Roads.Route r = rd.route(h.edge); int end = h.along < r.along[r.n() - 1] / 2 ? 0 : 1; if (!seen.add(((long) h.edge << 1) | end)) continue;
                Yard y = yard(w, h.edge, end); if (y == null || y.face() != f) continue; draw(y, x0, z0, x1, z1, out); } } }
    static void draw(Yard y, int x0, int z0, int x1, int z1, Metro.Sink out) {
        for (int[] l : y.lamps()) { if (l[0] < x0 || l[0] >= x1 || l[2] < z0 || l[2] >= z1) continue; int b = l[1];
            for (int k = -4; k < 0; k++) out.put(l[0], b + k, l[2], "stone");   // a footing
            for (int k = 0; k < LAMP_H; k++) out.put(l[0], b + k, l[2], "andesite_wall");
            out.put(l[0], b + LAMP_H, l[2], "sea_lantern"); out.put(l[0], b + LAMP_H + 1, l[2], "smooth_stone_slab[type=bottom]"); LAMPS.increment(); }
        int[] o = y.office(); if (o == null) return; int fy = o[2];
        for (int x = Math.max(x0, o[0] - 1); x < Math.min(x1, o[0] + OFFICE_W + 1); x++) for (int z = Math.max(z0, o[1] - 1); z < Math.min(z1, o[1] + OFFICE_D + 1); z++) {
            boolean in = x >= o[0] && x < o[0] + OFFICE_W && z >= o[1] && z < o[1] + OFFICE_D, edge = in && (x == o[0] || x == o[0] + OFFICE_W - 1 || z == o[1] || z == o[1] + OFFICE_D - 1);
            for (int k = -6; k < 0; k++) out.put(x, fy + k, z, in ? "stone_bricks" : "gravel");   // the footing, a gravel apron round it
            out.put(x, fy - 1, z, in ? "polished_andesite" : "gravel");
            for (int k = 0; k <= OFFICE_H + 3; k++) out.put(x, fy + k, z, "air");
            if (!in) continue;
            if (edge) { boolean corner = (x == o[0] || x == o[0] + OFFICE_W - 1) && (z == o[1] || z == o[1] + OFFICE_D - 1); boolean door = z == o[1] && x == o[0] + 2;
                for (int k = 0; k < OFFICE_H; k++) { String bl = "bricks"; if (!corner && (k == 1 || k == 2) && !door) bl = "glass_pane"; if (door && k < 2) bl = "oak_door[facing=north,half=" + (k == 0 ? "lower" : "upper") + ",hinge=left,open=false]"; out.put(x, fy + k, z, bl); } }
            else { out.put(x, fy - 1, z, "polished_andesite"); if (x == o[0] + OFFICE_W / 2 && z == o[1] + OFFICE_D / 2) out.put(x, fy + OFFICE_H - 1, z, "lantern[hanging=true]"); }
            out.put(x, fy + OFFICE_H, z, "smooth_stone_slab[type=bottom]"); }
        if (o[0] + 2 >= x0 && o[0] + 2 < x1 && o[1] - 1 >= z0 && o[1] - 1 < z1) OFFICES.increment(); }
}
