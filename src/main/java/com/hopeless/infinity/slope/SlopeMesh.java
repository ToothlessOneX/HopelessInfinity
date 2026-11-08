package com.hopeless.infinity.slope;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.26.0: the geometry of every slope block, in pure Java (the client model, the collision boxes and the preview exporter all use it).
 *
 * A piece is a height surface over one block cell: a height at each corner (nw, ne, se, sw: 0..L levels of 1/L block), standing on a
 * "floor" face. Terrain pieces use L = 4 (quarter blocks), floor down. Building pieces use L = 2 (half blocks) on any of the 6 faces;
 * turning a building piece about its floor is a turn of its corner list. The top is two triangles split on a diagonal (split 0: nw-se,
 * 1: ne-sw); each side is the trapezoid under its two corners; the floor face is a full square.
 *
 * Local frame (floor down): x east, y up, z south, all 0..1. A face: material (0 top, 1 side, 2 floor), 4 vertices of {x, y, z, u, v}
 * (a triangle repeats its last vertex), the outward normal, and the face it fills fully (cull) or -1.
 */
public final class SlopeMesh {
    private SlopeMesh() {}
    public static final int TOP = 0, SIDE = 1, FLOOR = 2;
    /** Directions as in Minecraft's Direction.get3DDataValue: 0 down, 1 up, 2 north, 3 south, 4 west, 5 east. */
    public static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;
    public static final class Face { public final int mat, cull; public final double[][] v; public final double[] n;
        Face(int mat, double[][] v, double[] n, int cull) { this.mat = mat; this.v = v; this.n = n; this.cull = cull; } }

    /** Corner heights of a code: code = nw + b*ne + b^2*se + b^3*sw with b = L + 1. */
    public static int[] decode(int code, int L) { int b = L + 1; return new int[]{code % b, code / b % b, code / (b * b) % b, code / (b * b * b) % b}; }
    public static int encode(int[] h, int L) { int b = L + 1; return h[0] + b * h[1] + b * b * h[2] + b * b * b * h[3]; }
    /** The split that follows the shape: through the corner pair whose heights differ most from the other pair's mean (a corner piece
     *  keeps its apex; an inverted corner keeps its notch). */
    public static int autoSplit(int[] h) { double a = Math.abs((h[0] + h[2]) / 2.0 - (h[1] + h[3]) / 2.0), d0 = Math.abs(h[0] - h[2]), d1 = Math.abs(h[1] - h[3]); return d0 >= d1 ? 0 : 1; }

    /** Faces in the local frame (floor down). */
    public static List<Face> local(int[] h, int L, int split) {
        double[] c = {h[0] / (double) L, h[1] / (double) L, h[2] / (double) L, h[3] / (double) L};
        double[][] P = {{0, c[0], 0}, {1, c[1], 0}, {1, c[2], 1}, {0, c[3], 1}};   // nw ne se sw on top
        List<Face> out = new ArrayList<>();
        // top: two triangles
        int[][] tri = split == 0 ? new int[][]{{0, 2, 1}, {0, 3, 2}} : new int[][]{{1, 0, 3}, {1, 3, 2}};
        boolean flat = c[0] == c[1] && c[1] == c[2] && c[2] == c[3];
        if (flat) out.add(face(TOP, new double[][]{P[0], P[3], P[2], P[1]}, c[0] >= 1 ? UP : -1));
        else for (int[] t : tri) { double[][] q = {P[t[0]], P[t[1]], P[t[2]]}; if (area(q) > 1e-9) out.add(face(TOP, new double[][]{q[0], q[1], q[2], q[2]}, -1)); }
        // sides: north (nw, ne), east (ne, se), south (se, sw), west (sw, nw)
        int[][] sd = {{1, 0, NORTH}, {2, 1, EAST}, {3, 2, SOUTH}, {0, 3, WEST}};
        for (int[] s : sd) { double[] a = P[s[0]], b = P[s[1]]; if (a[1] <= 0 && b[1] <= 0) continue;
            double[] a0 = {a[0], 0, a[2]}, b0 = {b[0], 0, b[2]};
            double[][] q = a[1] > 0 && b[1] > 0 ? new double[][]{a0, b0, b, a} : a[1] > 0 ? new double[][]{a0, b0, a, a} : new double[][]{a0, b0, b, b};
            out.add(face(SIDE, q, a[1] >= 1 && b[1] >= 1 ? s[2] : -1)); }
        out.add(face(FLOOR, new double[][]{{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}}, DOWN));
        return out;
    }
    static double area(double[][] q) { double[] u = sub(q[1], q[0]), w = sub(q[2], q[0]); double[] cr = cross(u, w); return Math.sqrt(cr[0] * cr[0] + cr[1] * cr[1] + cr[2] * cr[2]); }
    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    static double[] cross(double[] u, double[] w) { return new double[]{u[1] * w[2] - u[2] * w[1], u[2] * w[0] - u[0] * w[2], u[0] * w[1] - u[1] * w[0]}; }
    /** A face from positions: counter-clockwise seen from outside (the normal points away from the cell centre), with UVs. */
    static Face face(int mat, double[][] pos, int cull) {
        double[] n = cross(sub(pos[1], pos[0]), sub(pos[2], pos[0])); double l = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        double[] ctr = {0, 0, 0}; for (double[] p : pos) for (int k = 0; k < 3; k++) ctr[k] += p[k] / 4;
        double[] out = {ctr[0] - 0.5, ctr[1] - (mat == FLOOR ? 1 : 0.0), ctr[2] - 0.5};
        if (mat == TOP) out = new double[]{0, 1, 0};
        if (mat == FLOOR) out = new double[]{0, -1, 0};
        if (n[0] * out[0] + n[1] * out[1] + n[2] * out[2] < 0) { double[][] r = {pos[3], pos[2], pos[1], pos[0]}; if (pos[2] == pos[3]) r = new double[][]{pos[2], pos[1], pos[0], pos[0]}; pos = r; n = new double[]{-n[0], -n[1], -n[2]}; }
        for (int k = 0; k < 3; k++) n[k] /= l;
        double[][] v = new double[4][];
        for (int i = 0; i < 4; i++) { double[] p = pos[i]; double u, vv;
            if (mat != SIDE) { u = p[0]; vv = p[2]; }                                   // top and floor: x, z
            else if (Math.abs(n[0]) > Math.abs(n[2])) { u = n[0] > 0 ? 1 - p[2] : p[2]; vv = 1 - p[1]; }   // east/west sides
            else { u = n[2] > 0 ? p[0] : 1 - p[0]; vv = 1 - p[1]; }                       // north/south sides
            v[i] = new double[]{p[0], p[1], p[2], u, vv}; }
        return new Face(mat, v, n, cull);
    }

    /** Local (floor down) to world, for a floor face: rotates the piece so its floor lies on that face (its top points the other way). */
    public static double[] toWorld(int floor, double x, double y, double z) {
        return switch (floor) {
            case UP -> new double[]{x, 1 - y, 1 - z};      // upside down (a ceiling piece)
            case NORTH -> new double[]{x, 1 - z, y};       // floor on the north face: local up is south
            case SOUTH -> new double[]{x, z, 1 - y};       // floor on the south face: local up is north
            case WEST -> new double[]{y, 1 - z, x};        // floor on the west face: local up is east... (x <-> y)
            case EAST -> new double[]{1 - y, z, x};
            default -> new double[]{x, y, z};
        };
    }
    /** A local face index (as in cull) turned to the world for a floor face. */
    public static int dirToWorld(int floor, int d) {
        if (d < 0) return d;
        double[] v = switch (d) { case DOWN -> new double[]{0, -1, 0}; case UP -> new double[]{0, 1, 0}; case NORTH -> new double[]{0, 0, -1}; case SOUTH -> new double[]{0, 0, 1}; case WEST -> new double[]{-1, 0, 0}; default -> new double[]{1, 0, 0}; };
        double[] o = toWorld(floor, 0.5, 0.5, 0.5), p = toWorld(floor, 0.5 + v[0], 0.5 + v[1], 0.5 + v[2]); double[] w = sub(p, o);
        if (w[1] < -0.5) return DOWN; if (w[1] > 0.5) return UP; if (w[2] < -0.5) return NORTH; if (w[2] > 0.5) return SOUTH; if (w[0] < -0.5) return WEST; return EAST;
    }
    /** World faces for a piece. */
    public static List<Face> world(int[] h, int L, int split, int floor) {
        List<Face> out = new ArrayList<>();
        for (Face f : local(h, L, split)) {
            double[][] v = new double[4][]; for (int i = 0; i < 4; i++) { double[] p = toWorld(floor, f.v[i][0], f.v[i][1], f.v[i][2]); v[i] = new double[]{p[0], p[1], p[2], f.v[i][3], f.v[i][4]}; }
            double[] n0 = toWorld(floor, 0, 0, 0), n1 = toWorld(floor, f.n[0], f.n[1], f.n[2]); double[] n = sub(n1, n0);
            double[] e1 = sub(v[1], v[0]), e2 = sub(v[2], v[0]); double[] cr = cross(e1, e2);
            if (cr[0] * n[0] + cr[1] * n[1] + cr[2] * n[2] < 0) { v = v[2] == v[3] || java.util.Arrays.equals(v[2], v[3]) ? new double[][]{v[2], v[1], v[0], v[0]} : new double[][]{v[3], v[2], v[1], v[0]}; }   // mirror turns keep faces outward
            out.add(new Face(f.mat, v, n, dirToWorld(floor, f.cull)));
        }
        return out;
    }
    /** Collision: the cell cut into 4 x 4 columns along the floor's up axis, each as high as the surface at its centre (to 1/16, at least
     *  1/16). Returns boxes {x0, y0, z0, x1, y1, z1} in sixteenths, world frame. */
    public static List<int[]> boxes(int[] h, int L, int split, int floor) {
        List<int[]> out = new ArrayList<>(); double[] c = {h[0] / (double) L, h[1] / (double) L, h[2] / (double) L, h[3] / (double) L};
        for (int i = 0; i < 4; i++) for (int k = 0; k < 4; k++) {
            double x = (i + 0.5) / 4, z = (k + 0.5) / 4, y = height(c, split, x, z); int hh = Math.max(1, (int) Math.round(y * 16)); if (hh <= 0) continue;
            double[] a = toWorld(floor, i / 4.0, 0, k / 4.0), b = toWorld(floor, (i + 1) / 4.0, hh / 16.0, (k + 1) / 4.0);
            out.add(new int[]{(int) Math.round(Math.min(a[0], b[0]) * 16), (int) Math.round(Math.min(a[1], b[1]) * 16), (int) Math.round(Math.min(a[2], b[2]) * 16),
                (int) Math.round(Math.max(a[0], b[0]) * 16), (int) Math.round(Math.max(a[1], b[1]) * 16), (int) Math.round(Math.max(a[2], b[2]) * 16)});
        }
        return out;
    }
    /** Surface height at (x, z) in the local frame (the two triangles). */
    public static double height(double[] c, int split, double x, double z) {
        if (split == 0) { // nw(0,0) - se(1,1) diagonal: upper-right triangle nw ne se when x >= z
            return x >= z ? c[0] + (c[1] - c[0]) * x + (c[2] - c[1]) * z : c[0] + (c[2] - c[3]) * x + (c[3] - c[0]) * z;
        } // ne(1,0) - sw(0,1): triangle nw ne sw when x + z <= 1
        return x + z <= 1 ? c[0] + (c[1] - c[0]) * x + (c[3] - c[0]) * z : c[2] + (c[2] - c[3]) * (x - 1) + (c[2] - c[1]) * (z - 1);
    }
}
