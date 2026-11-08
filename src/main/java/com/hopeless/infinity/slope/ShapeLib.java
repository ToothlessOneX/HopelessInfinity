package com.hopeless.infinity.slope;

import java.util.*;

/**
 * 0.26.0: the building shape library, in pure Java (the client model, the collision and the previews use it). Each shape is one or
 * more convex parts, each given by its corner points in the unit cell (local frame: x east, y up, z south; the "floor" is y = 0 and
 * the "back" is z = 0). Faces come from the convex hull of each part; collision is the shape cut into 8 x 8 x 8 cells. Any shape
 * can stand in any of the 24 turns of the cube.
 */
public final class ShapeLib {
    private ShapeLib() {}
    public static final class Shape { public final String id, name; final double[][][] parts; Shape(String id, String name, double[][][] parts) { this.id = id; this.name = name; this.parts = parts; } }
    public static final List<Shape> SHAPES = new ArrayList<>();
    public static final Map<String, Integer> INDEX = new HashMap<>();
    static double[][] pts(double... v) { double[][] p = new double[v.length / 3][]; for (int i = 0; i < p.length; i++) p[i] = new double[]{v[3 * i], v[3 * i + 1], v[3 * i + 2]}; return p; }
    static double[][] box(double x0, double y0, double z0, double x1, double y1, double z1) { return pts(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1); }
    static double[][] join(double[][]... a) { List<double[]> l = new ArrayList<>(); for (double[][] x : a) l.addAll(Arrays.asList(x)); return l.toArray(new double[0][]); }
    static void add(String id, String name, double[][]... parts) { INDEX.put(id, SHAPES.size()); SHAPES.add(new Shape(id, name, parts)); }
    static final double[][] B4 = pts(0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1);              // the floor square
    static final double T = 0.125;                                                   // panel thickness (1/8 block)
    /** A quarter circle in the y-z plane about the back-bottom edge (radius 1), as points across x = 0 and x = 1. */
    static double[][] arcYZ(int n) { List<double[]> l = new ArrayList<>(); for (int i = 0; i <= n; i++) { double a = Math.PI / 2 * i / n, y = Math.sin(a), z = Math.cos(a); l.add(new double[]{0, y, z}); l.add(new double[]{1, y, z}); } l.add(new double[]{0, 0, 0}); l.add(new double[]{1, 0, 0}); return l.toArray(new double[0][]); }
    /** A sphere octant about the back-bottom-west corner (radius 1). */
    static double[][] octant(int n) { List<double[]> l = new ArrayList<>(); l.add(new double[]{0, 0, 0});
        for (int i = 0; i <= n; i++) for (int j = 0; j <= n; j++) { double th = Math.PI / 2 * i / n, ph = Math.PI / 2 * j / n; l.add(new double[]{Math.cos(th) * Math.sin(ph), Math.cos(ph), Math.sin(th) * Math.sin(ph)}); }
        return l.toArray(new double[0][]); }
    static double[][] arcXY(int n) { List<double[]> l = new ArrayList<>(); for (int i = 0; i <= n; i++) { double a = Math.PI / 2 * i / n, y = Math.sin(a), x = Math.cos(a); l.add(new double[]{x, y, 0}); l.add(new double[]{x, y, 1}); } l.add(new double[]{0, 0, 0}); l.add(new double[]{0, 0, 1}); return l.toArray(new double[0][]); }
    static {
        add("block", "Block", box(0, 0, 0, 1, 1, 1));
        add("slope", "Slope", join(B4, pts(0, 1, 0, 1, 1, 0)));
        add("corner", "Corner", pts(0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 1, 0));
        add("inv_corner", "Inverted Corner", pts(0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 1, 1, 0, 0, 1, 1));
        add("slope2_base", "Slope 2x1 Base", join(B4, pts(0, 1, 0, 1, 1, 0, 0, 0.5, 1, 1, 0.5, 1)));
        add("slope2_tip", "Slope 2x1 Tip", join(B4, pts(0, 0.5, 0, 1, 0.5, 0)));
        add("corner2_base", "Corner 2x1 Base", pts(0, 0, 0, 1, 0, 0, 0, 1, 0, 0.5, 0, 1, 0, 0, 1, 0, 0.5, 1));
        add("corner2_tip", "Corner 2x1 Tip", pts(0.5, 0, 0, 0, 0, 0, 0, 0.5, 0, 0, 0, 1));
        add("inv_corner2_base", "Inverted Corner 2x1 Base", join(pts(0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 1, 1, 0, 0, 1, 1), pts(1, 0.5, 1, 0.5, 1, 1)));
        add("inv_corner2_tip", "Inverted Corner 2x1 Tip", pts(0, 0, 0, 1, 0, 0, 0, 1, 0, 1, 0.5, 0, 0.5, 1, 0, 0, 0, 1, 1, 0, 1, 0, 1, 1));
        add("half_block", "Half Block", box(0, 0, 0, 1, 0.5, 1));
        add("half_slope", "Half Slope", join(B4, pts(0, 0.5, 0, 1, 0.5, 0)));
        add("half_slope_inv", "Half Slope Inverted", join(B4, pts(0, 1, 0, 1, 1, 0, 0, 0.5, 1, 1, 0.5, 1)));
        add("half_corner", "Half Corner", pts(0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0.5, 0));
        add("corner_square", "Square Corner", join(B4, pts(0, 1, 0)));
        add("round_slope", "Round Slope", arcYZ(12));
        add("round_corner", "Round Corner", octant(8));
        add("round_inv_corner", "Round Inverted Corner", arcYZ(12), arcXY(12));
        add("panel", "Panel", box(0, 0, 0, 1, T, 1));
        add("panel_center", "Center Panel", box(0, 0.5 - T / 2, 0, 1, 0.5 + T / 2, 1));
        add("panel_half", "Half Panel", box(0, 0, 0, 0.5, T, 1));
        add("panel_half_center", "Half Center Panel", box(0, 0.5 - T / 2, 0, 0.5, 0.5 + T / 2, 1));
        add("panel_quarter", "Quarter Panel", box(0, 0, 0, 0.5, T, 0.5));
        { double k = T * Math.sqrt(2); add("panel_sloped", "Sloped Panel", pts(0, 1, 0, 1, 1, 0, 0, 0, 1, 1, 0, 1, 0, 1 - k, 0, 1, 1 - k, 0, 0, 0, 1 - k, 1, 0, 1 - k)); }
        { double k = T * Math.hypot(1, 0.5); add("panel_2x1_sloped", "2x1 Sloped Panel", pts(0, 1, 0, 1, 1, 0, 0, 0.5, 1, 1, 0.5, 1, 0, 1 - k, 0, 1, 1 - k, 0, 0, 0.5 - k, 1, 1, 0.5 - k, 1));
          add("panel_2x1_sloped_tip", "2x1 Sloped Panel Tip", pts(0, 0.5, 0, 1, 0.5, 0, 0, 0, 1, 1, 0, 1, 0, 0.5 - k, 0, 1, 0.5 - k, 0, 0, 0, 1 - 2 * k, 1, 0, 1 - 2 * k)); }
        { List<double[][]> segs = new ArrayList<>(); int n = 8; for (int i = 0; i < n; i++) { double a0 = Math.PI / 2 * i / n, a1 = Math.PI / 2 * (i + 1) / n; segs.add(pts(
            0, Math.sin(a0), Math.cos(a0), 1, Math.sin(a0), Math.cos(a0), 0, Math.sin(a1), Math.cos(a1), 1, Math.sin(a1), Math.cos(a1),
            0, (1 - T) * Math.sin(a0), (1 - T) * Math.cos(a0), 1, (1 - T) * Math.sin(a0), (1 - T) * Math.cos(a0), 0, (1 - T) * Math.sin(a1), (1 - T) * Math.cos(a1), 1, (1 - T) * Math.sin(a1), (1 - T) * Math.cos(a1))); }
          add("panel_round", "Round Panel", segs.toArray(new double[0][][])); }
        // 0.27.0 ACCESSIBLE RAMPS (ADA 405.2: running slope 1:12 at most): ramp12_k rises from k/12 at the back to (k + 1)/12 at the front,
        // so 12 in a row climb one block at 1:12 (CHOSEN: the steepest the ADA allows; collision rises 1/8 at a time, under the 0.6 step)
        for (int k = 0; k < 12; k++) add("ramp12_" + k, "Ramp 1:12 (" + (k + 1) + " of 12)", join(B4, pts(0, k / 12.0, 0, 1, k / 12.0, 0, 0, (k + 1) / 12.0, 1, 1, (k + 1) / 12.0, 1)));
        // 0.27.0 RAILS: a bar 1/8 thick along x, its top at n/24 of the block, on a post at the middle (ADA 505: handrails 34-38 in; IBC 1015:
        // guards 42 in): rail tops in 1/24-block steps, a rail above a block's top split over two cells (rail_24 below, rail_n above)
        for (int n = 1; n <= 24; n++) { double t = n / 24.0, b = Math.max(0, t - 0.125);
            add("rail_" + n, "Rail (top " + n + "/24)", box(0, b, 0.4375, 1, t, 0.5625), box(0.4375, 0, 0.4375, 0.5625, Math.max(b, 1e-3), 0.5625)); }
    }

    // ---- hulls
    public static final class Poly { public final double[] n; public final double[][] v; Poly(double[] n, double[][] v) { this.n = n; this.v = v; } }
    static final double EPS = 1e-7;
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    static double[] cross(double[] u, double[] w) { return new double[]{u[1] * w[2] - u[2] * w[1], u[2] * w[0] - u[0] * w[2], u[0] * w[1] - u[1] * w[0]}; }
    static double[] norm(double[] a) { double l = Math.sqrt(dot(a, a)); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }
    /** The faces of a convex point set: outward polygons, counter-clockwise seen from outside. */
    public static List<Poly> hull(double[][] p) {
        List<double[]> pl = new ArrayList<>(); List<Poly> out = new ArrayList<>(); int n = p.length;
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) for (int k = j + 1; k < n; k++) {
            double[] c = cross(sub(p[j], p[i]), sub(p[k], p[i])); if (dot(c, c) < 1e-12) continue; c = norm(c); double d = dot(c, p[i]);
            boolean pos = false, neg = false; for (double[] q : p) { double s = dot(c, q) - d; if (s > 1e-6) pos = true; if (s < -1e-6) neg = true; if (pos && neg) break; }
            if (pos && neg) continue; if (pos) { c = new double[]{-c[0], -c[1], -c[2]}; d = -d; }
            boolean dup = false; for (double[] e : pl) if (Math.abs(e[0] - c[0]) < 1e-6 && Math.abs(e[1] - c[1]) < 1e-6 && Math.abs(e[2] - c[2]) < 1e-6 && Math.abs(e[3] - d) < 1e-6) { dup = true; break; }
            if (dup) continue; pl.add(new double[]{c[0], c[1], c[2], d});
            List<double[]> on = new ArrayList<>(); for (double[] q : p) if (Math.abs(dot(c, q) - d) < 1e-6) { boolean seen = false; for (double[] r : on) if (dot(sub(r, q), sub(r, q)) < 1e-12) seen = true; if (!seen) on.add(q); }
            double[] ctr = {0, 0, 0}; for (double[] q : on) for (int t = 0; t < 3; t++) ctr[t] += q[t] / on.size();
            double[] ax = norm(sub(on.get(0), ctr)), ay = cross(c, ax);
            on.sort(Comparator.comparingDouble(q -> Math.atan2(dot(sub(q, ctr), ay), dot(sub(q, ctr), ax))));
            out.add(new Poly(c, on.toArray(new double[0][])));
        }
        return out;
    }
    public static boolean inside(double[][][] parts, List<List<double[]>> planes, double x, double y, double z) {
        for (List<double[]> pp : planes) { boolean in = true; for (double[] e : pp) if (e[0] * x + e[1] * y + e[2] * z > e[3] + 1e-9) { in = false; break; } if (in) return true; }
        return false;
    }

    // ---- the 24 turns
    public static final int[][][] ROT = new int[24][][];
    static {
        List<int[][]> l = new ArrayList<>(); int[][] I = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        int[][] rx = {{1, 0, 0}, {0, 0, -1}, {0, 1, 0}}, ry = {{0, 0, 1}, {0, 1, 0}, {-1, 0, 0}};
        ArrayDeque<int[][]> q = new ArrayDeque<>(); q.add(I);
        while (!q.isEmpty()) { int[][] m = q.poll(); boolean have = false; for (int[][] e : l) if (Arrays.deepEquals(e, m)) have = true; if (have) continue; l.add(m); q.add(mul(rx, m)); q.add(mul(ry, m)); }
        for (int i = 0; i < 24; i++) ROT[i] = l.get(i);
    }
    static int[][] mul(int[][] a, int[][] b) { int[][] c = new int[3][3]; for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) for (int k = 0; k < 3; k++) c[i][j] += a[i][k] * b[k][j]; return c; }
    public static double[] turn(int o, double[] p) { int[][] m = ROT[o]; double x = p[0] - 0.5, y = p[1] - 0.5, z = p[2] - 0.5;
        return new double[]{m[0][0] * x + m[0][1] * y + m[0][2] * z + 0.5, m[1][0] * x + m[1][1] * y + m[1][2] * z + 0.5, m[2][0] * x + m[2][1] * y + m[2][2] * z + 0.5}; }
    public static int[] turnDir(int o, int[] d) { int[][] m = ROT[o]; return new int[]{m[0][0] * d[0] + m[0][1] * d[1] + m[0][2] * d[2], m[1][0] * d[0] + m[1][1] * d[1] + m[1][2] * d[2], m[2][0] * d[0] + m[2][1] * d[1] + m[2][2] * d[2]}; }
    /** The turn that puts the local floor (-y) toward `down` and the local back (-z) toward `back` (unit axis vectors), or -1. */
    public static int orient(int[] down, int[] back) {
        for (int o = 0; o < 24; o++) if (Arrays.equals(turnDir(o, new int[]{0, -1, 0}), down) && Arrays.equals(turnDir(o, new int[]{0, 0, -1}), back)) return o;
        return -1;
    }

    // ---- meshes and collision, cached per shape and turn
    public static final class Face { public final double[][] v; public final double[] n; public final int cull; Face(double[][] v, double[] n, int cull) { this.v = v; this.n = n; this.cull = cull; } }
    private static final Map<Integer, List<Face>> MESH = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Integer, List<int[]>> BOXES = new java.util.concurrent.ConcurrentHashMap<>();
    /** Faces in the world frame for a shape and turn: each a quad {x, y, z, u, v} x 4 (a triangle repeats its last point), its normal,
     *  and the face of the cell it fills fully (0 down, 1 up, 2 north, 3 south, 4 west, 5 east) or -1. */
    public static List<Face> mesh(int shape, int o) {
        return MESH.computeIfAbsent(shape * 24 + o, k -> {
            List<Face> out = new ArrayList<>(); double[][][] parts = SHAPES.get(shape).parts;
            List<List<Poly>> hulls = new ArrayList<>(); for (double[][] part : parts) hulls.add(hull(part));
            List<List<double[]>> planes = new ArrayList<>(); for (List<Poly> h : hulls) { List<double[]> pp = new ArrayList<>(); for (Poly pg : h) pp.add(new double[]{pg.n[0], pg.n[1], pg.n[2], dot(pg.n, pg.v[0])}); planes.add(pp); }
            for (int pi = 0; pi < parts.length; pi++) for (Poly pg : hulls.get(pi)) {
                if (parts.length > 1 && hidden(pi, pg, hulls, planes)) { hiddenFaces++; continue; }   // inside another part, or the smaller of two faces on one plane
                double[][] w = new double[pg.v.length][]; for (int i = 0; i < w.length; i++) w[i] = turn(o, pg.v[i]);
                double[] n = norm(sub(turn(o, new double[]{pg.n[0] + 0.5, pg.n[1] + 0.5, pg.n[2] + 0.5}), new double[]{0.5, 0.5, 0.5}));
                int cull = fullFace(w, n);
                for (int i = 1; i + 1 < w.length; i += 2) {   // fan: quads (i, i+1, i+2) with the first point; a last odd triangle repeats a point
                    double[] a = w[0], b = w[i], c = w[i + 1], d = i + 2 < w.length ? w[i + 2] : w[i + 1];
                    out.add(new Face(new double[][]{uv(a, n), uv(b, n), uv(c, n), uv(d, n)}, n, cull));
                }
            }
            return out;
        });
    }
    public static volatile long hiddenFaces;
    static double area(Poly pg) { double[] a = {0, 0, 0}; for (int i = 0; i < pg.v.length; i++) { double[] c = cross(pg.v[i], pg.v[(i + 1) % pg.v.length]); a[0] += c[0]; a[1] += c[1]; a[2] += c[2]; } return Math.abs(dot(a, pg.n)) / 2; }
    static boolean insideOne(List<double[]> pp, double[] q, double tol) { for (double[] e : pp) if (e[0] * q[0] + e[1] * q[1] + e[2] * q[2] > e[3] + tol) return false; return true; }
    static boolean hidden(int pi, Poly pg, List<List<Poly>> hulls, List<List<double[]>> planes) {
        double[] c = {0, 0, 0}; for (double[] v : pg.v) for (int k = 0; k < 3; k++) c[k] += v[k] / pg.v.length;
        double[] out = {c[0] + pg.n[0] * 1e-4, c[1] + pg.n[1] * 1e-4, c[2] + pg.n[2] * 1e-4}; double d = dot(pg.n, pg.v[0]), a = area(pg);
        for (int pj = 0; pj < hulls.size(); pj++) { if (pj == pi) continue;
            if (insideOne(planes.get(pj), out, -1e-9)) return true;                                   // just outside this face is inside the other part
            if (!insideOne(planes.get(pj), c, 1e-7)) continue;
            for (Poly q : hulls.get(pj)) if (Math.abs(dot(q.n, pg.n) - 1) < 1e-7 && Math.abs(dot(q.n, q.v[0]) - d) < 1e-7) { double aq = area(q); if (a < aq - 1e-9 || Math.abs(a - aq) < 1e-9 && pi > pj) return true; }
        }
        return false;
    }
    static double[] uv(double[] p, double[] n) {
        double ax = Math.abs(n[0]), ay = Math.abs(n[1]), az = Math.abs(n[2]); double u, v;
        if (ay >= ax && ay >= az) { u = p[0]; v = n[1] > 0 ? p[2] : 1 - p[2]; }
        else if (ax >= az) { u = n[0] > 0 ? 1 - p[2] : p[2]; v = 1 - p[1]; }
        else { u = n[2] > 0 ? p[0] : 1 - p[0]; v = 1 - p[1]; }
        return new double[]{p[0], p[1], p[2], u, v};
    }
    static int fullFace(double[][] w, double[] n) {
        int axis = Math.abs(n[0]) > 0.999 ? 0 : Math.abs(n[1]) > 0.999 ? 1 : Math.abs(n[2]) > 0.999 ? 2 : -1; if (axis < 0) return -1;
        double c = w[0][axis]; if (Math.abs(c) > 1e-6 && Math.abs(c - 1) > 1e-6) return -1;
        double area = 0; for (int i = 0; i < w.length; i++) { double[] a = w[i], b = w[(i + 1) % w.length]; int u = (axis + 1) % 3, v = (axis + 2) % 3; area += a[u] * b[v] - b[u] * a[v]; }
        if (Math.abs(Math.abs(area) / 2 - 1) > 1e-6) return -1;
        return axis == 1 ? (n[1] < 0 ? 0 : 1) : axis == 2 ? (n[2] < 0 ? 2 : 3) : (n[0] < 0 ? 4 : 5);
    }
    /** Collision boxes in sixteenths {x0, y0, z0, x1, y1, z1}: the shape sampled on an 8 x 8 x 8 grid, runs merged along z. */
    public static List<int[]> boxes(int shape, int o) {
        return BOXES.computeIfAbsent(shape * 24 + o, k -> {
            List<List<double[]>> planes = new ArrayList<>();
            for (double[][] part : SHAPES.get(shape).parts) { List<double[]> pp = new ArrayList<>(); for (Poly pg : hull(part)) { double[] wv = turn(o, pg.v[0]);
                double[] n = norm(sub(turn(o, new double[]{pg.n[0] + 0.5, pg.n[1] + 0.5, pg.n[2] + 0.5}), new double[]{0.5, 0.5, 0.5})); pp.add(new double[]{n[0], n[1], n[2], dot(n, wv)}); } planes.add(pp); }
            List<int[]> out = new ArrayList<>(); int N = 8;
            for (int x = 0; x < N; x++) for (int y = 0; y < N; y++) { int z = 0;
                while (z < N) { if (!inside(null, planes, (x + 0.5) / N, (y + 0.5) / N, (z + 0.5) / N)) { z++; continue; }
                    int z0 = z; while (z < N && inside(null, planes, (x + 0.5) / N, (y + 0.5) / N, (z + 0.5) / N)) z++;
                    out.add(new int[]{x * 2, y * 2, z0 * 2, x * 2 + 2, y * 2 + 2, z * 2}); } }
            if (out.isEmpty()) out.add(new int[]{0, 0, 0, 16, 2, 16});
            return out;
        });
    }
}
