package com.hopeless.infinity.vehicle;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.13.1 the X-15's detailed model (owner, 2026-11-02: "I want more detail on that ship"). A quad mesh in metres (x right, y up, z forward;
 * the origin on the centre line BASE_UP above the skids; station s = metres back from the nose tip), built from the photos (Wikimedia
 * Commons: NASA E-4358 rollout top view; ship 3 on the lakebed, side view; Armstrong with ship 1, canopy and nose; ship 1 in the National
 * Air and Space Museum, tail and markings) to the real dimensions (vehicle/X15). Pure Java: the game's renderer and tools/x15_view.py
 * draw the same quads.
 * Parts: the Q-ball nose (silver), the round nose cone with the reaction-control ports, the fuselage (lofted rings, smooth normals) with
 * panel joints, the clamshell canopy and its two windows, the side tunnels to the tail, the wings (5 % biconvex section, flaps marked),
 * the all-moving tailplanes (15 deg anhedral, pivot fairings), the 10 deg wedge upper fin (rudder and speed-brake joints), the lower fin
 * stub, the engine bell, the twin nose wheels and the rear steel skids on their struts; markings of ship 1 (56-6670) as in the museum:
 * "U.S. AIR FORCE", the national star and bars, "66670" and the NASA band on the fin, "USAF" and a star on the wings.
 */
public final class X15Mesh {
    private X15Mesh() {}
    /** One quad: 4 corners (x, y, z), 4 normals, a colour. */
    public record Quad(double[][] p, double[][] n, int rgb) {}
    static final int BLACK = 0x18181A, BLACK2 = 0x222226, JOINT = 0x0A0A0C, SILVER = 0xB8BCC2, STEEL = 0x8C9096, GLASS = 0x34495A, GLASS_HI = 0x6E8496, TYRE = 0x111111,
        WHITE = 0xEDEDED, BLUE = 0x1F3A7A, RED = 0xB0222E, YELLOW = 0xF2C230, NOZZLE = 0x5A5C60, SOOT = 0x0E0E0E;
    static double z(double s) { return 7.74 - s; }

    static final class B { final List<Quad> q = new ArrayList<>();
        void quad(double[] a, double[] b, double[] c, double[] d, int rgb) { double[] n = norm(cross(sub(b, a), sub(d, a))); if (len(n) < 1e-12) n = norm(cross(sub(c, b), sub(a, b))); q.add(new Quad(new double[][]{a, b, c, d}, new double[][]{n, n, n, n}, rgb)); }
        void quadN(double[] a, double[] b, double[] c, double[] d, double[] na, double[] nb, double[] nc, double[] nd, int rgb) { q.add(new Quad(new double[][]{a, b, c, d}, new double[][]{na, nb, nc, nd}, rgb)); }
        /** Rings (same point count, each a closed loop) joined in order; smooth normals from the neighbours; caps when asked. */
        void loft(List<double[][]> rings, int rgb, boolean capFirst, boolean capLast, boolean smooth) {
            int R = rings.size(), N = rings.get(0).length;
            double[][][] nr = new double[R][N][];
            for (int r = 0; r < R; r++) for (int i = 0; i < N; i++) { double[] a = rings.get(Math.max(0, r - 1))[i], b = rings.get(Math.min(R - 1, r + 1))[i], c = rings.get(r)[(i + N - 1) % N], d = rings.get(r)[(i + 1) % N];
                nr[r][i] = norm(cross(sub(d, c), sub(b, a))); }
            for (int r = 0; r + 1 < R; r++) for (int i = 0; i < N; i++) { int j = (i + 1) % N; double[] a = rings.get(r)[i], b = rings.get(r)[j], c = rings.get(r + 1)[j], d = rings.get(r + 1)[i];
                if (smooth) quadN(a, b, c, d, nr[r][i], nr[r][j], nr[r + 1][j], nr[r + 1][i], rgb); else quad(a, b, c, d, rgb); }
            if (capFirst) cap(rings.get(0), rgb, true); if (capLast) cap(rings.get(R - 1), rgb, false); }
        void cap(double[][] ring, int rgb, boolean flip) { double[] c = new double[3]; for (double[] p : ring) for (int k = 0; k < 3; k++) c[k] += p[k] / ring.length;
            for (int i = 0; i < ring.length; i++) { double[] a = ring[i], b = ring[(i + 1) % ring.length]; if (flip) quad(c, b, a, c, rgb); else quad(c, a, b, c, rgb); } }
        /** A flat decal: pixel grid (row 0 at the top) laid on a plane at o, u (columns) and v (rows down) one pixel long each, nudged along nrm. */
        void decal(int[][] px, double[] o, double[] u, double[] v, double[] nrm, double lift) {
            double[] off = mul(nrm, lift);
            for (int r = 0; r < px.length; r++) { int c = 0; while (c < px[r].length) { int col = px[r][c]; if (col < 0) { c++; continue; } int e = c; while (e + 1 < px[r].length && px[r][e + 1] == col) e++;
                double[] a = add(add(add(o, mul(u, c)), mul(v, r)), off), b = add(add(add(o, mul(u, e + 1)), mul(v, r)), off), cc = add(add(add(o, mul(u, e + 1)), mul(v, r + 1)), off), d = add(add(add(o, mul(u, c)), mul(v, r + 1)), off);
                q.add(new Quad(new double[][]{a, b, cc, d}, new double[][]{nrm, nrm, nrm, nrm}, col)); c = e + 1; } } }
        /** A box (axis-aligned) of one colour. */
        void box(double x0, double x1, double y0, double y1, double z0, double z1, int rgb) {
            double[] a = {x0, y0, z0}, b = {x1, y0, z0}, c = {x1, y1, z0}, d = {x0, y1, z0}, e = {x0, y0, z1}, f = {x1, y0, z1}, g = {x1, y1, z1}, h = {x0, y1, z1};
            quad(a, d, c, b, rgb); quad(e, f, g, h, rgb); quad(a, e, h, d, rgb); quad(b, c, g, f, rgb); quad(d, h, g, c, rgb); quad(a, b, f, e, rgb); }
        /** A bar between two points with a square section w. */
        void bar(double[] p0, double[] p1, double w, int rgb) { double[] ax = norm(sub(p1, p0)), up = Math.abs(ax[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0}, s1 = mul(norm(cross(ax, up)), w / 2), s2 = mul(norm(cross(ax, s1)), w / 2);
            List<double[][]> rs = new ArrayList<>(); for (double[] p : new double[][]{p0, p1}) rs.add(new double[][]{add(add(p, s1), s2), add(sub(p, s1), s2), sub(sub(p, s1), s2), sub(add(p, s1), s2)}); loft(rs, rgb, true, true, false); }
        /** A wheel: a cylinder on the x axis. */
        void wheel(double x, double y, double zc, double r, double w, int rgb) { List<double[][]> rs = new ArrayList<>(); int N = 14;
            for (double xx : new double[]{x - w / 2, x + w / 2}) { double[][] ring = new double[N][]; for (int i = 0; i < N; i++) { double t = 2 * Math.PI * i / N; ring[i] = new double[]{xx, y + r * Math.cos(t), zc + r * Math.sin(t)}; } rs.add(ring); }
            loft(rs, rgb, true, true, true); }
    }

    /** A superellipse ring round (cx, cy) at station s: half width a, half height b, power n (2 = ellipse), N points. */
    static double[][] ring(double s, double cx, double cy, double a, double b, double n, int N) { double[][] r = new double[N][]; double zz = z(s);
        for (int i = 0; i < N; i++) { double t = 2 * Math.PI * i / N, c = Math.cos(t), sn = Math.sin(t); r[i] = new double[]{cx + a * Math.signum(c) * Math.pow(Math.abs(c), 2 / n), cy + b * Math.signum(sn) * Math.pow(Math.abs(sn), 2 / n), zz}; } return r; }
    /** An airfoil ring (chord along -z from the leading edge at station sLE, chord c), a 'thick' biconvex section, at x, y, turned by the dihedral (rad) about z. */
    static double[][] foil(double sLE, double c, double x, double y, double thick, double dihedral) { int H = 8; double[][] r = new double[2 * H][]; double cd = Math.cos(dihedral), sd = Math.sin(dihedral);
        for (int i = 0; i < H; i++) { double f = (double) i / (H - 1), t = 2 * thick * c * f * (1 - f) + 0.004; double zz = z(sLE + f * c);
            r[i] = new double[]{x - sd * t, y + cd * t, zz}; r[2 * H - 1 - i] = new double[]{x + sd * t, y - cd * t, zz}; } return r; }
    /** A 10 deg wedge section (the X-15's tails): chord along -z from sLE, thickness growing to the blunt trailing edge, standing at height y. */
    static double[][] wedge(double sLE, double c, double y, double half) { double tTE = c * Math.tan(Math.toRadians(5)), zl = z(sLE), zt = z(sLE + c);
        return new double[][]{{0, y, zl}, {tTE * 0.5, y, z(sLE + c * 0.5)}, {tTE, y, zt}, {-tTE, y, zt}, {-tTE * 0.5, y, z(sLE + c * 0.5)}, {0, y, zl}}; }

    /** The fuselage: half width / half height by station from the photos (the round nose cone to 3.4 m, a constant 1.32 m body, the base). */
    static final double[][] BODY = {{0.12, 0.10}, {0.30, 0.15}, {0.7, 0.24}, {1.2, 0.33}, {1.8, 0.42}, {2.5, 0.51}, {3.2, 0.59}, {4.0, 0.645}, {4.8, 0.66}, {14.2, 0.66}, {14.9, 0.64}, {15.47, 0.60}};

    public static List<Quad> build() {
        B m = new B(); int N = 28;
        // Q-ball nose: a silver ball
        { List<double[][]> rs = new ArrayList<>(); for (int k = 0; k <= 6; k++) { double t = Math.PI / 2 * k / 6, s = 0.12 - 0.12 * Math.cos(t), r = Math.max(0.002, 0.12 * Math.sin(t)); rs.add(ring(s, 0, 0, r, r, 2, N)); } m.loft(rs, SILVER, false, false, true); }
        // fuselage rings (round), panel joints as darker bands
        List<double[][]> body = new ArrayList<>(); for (double[] b : BODY) body.add(ring(b[0], 0, 0, b[1], b[1], 2.15, N)); m.loft(body, BLACK, false, true, true);
        for (double s : new double[]{2.0, 3.2, 6.3, 7.9, 9.6, 11.2, 12.9, 14.4}) { double r = radius(s) + 0.006; List<double[][]> j = new ArrayList<>(); j.add(ring(s, 0, 0, r, r, 2.15, N)); j.add(ring(s + 0.025, 0, 0, r, r, 2.15, N)); m.loft(j, JOINT, false, false, true); }
        // reaction-control ports on the nose (pitch up/down, yaw left/right): small dark squares
        for (double[] d : new double[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { double s = 1.35, r = radius(s) + 0.005; double[] c = {d[0] * r, d[1] * r, z(s)}, u = d[0] != 0 ? new double[]{0, 0, -0.035} : new double[]{0.035, 0, 0}, v = d[0] != 0 ? new double[]{0, -0.035, 0} : new double[]{0, 0, -0.035};
            for (int k = -1; k <= 1; k += 2) m.decal(new int[][]{{SOOT, SOOT, SOOT}, {SOOT, SOOT, SOOT}, {SOOT, SOOT, SOOT}}, add(add(c, mul(u, k * 2.2 - 1.5)), mul(v, -1.5)), u, v, new double[]{d[0], d[1], 0}, 0.002); }
        // the canopy: a clamshell hump from 3.0 to 5.9 m, two windows each side
        { double[][] prof = {{3.0, 0.20, 0.58}, {3.3, 0.30, 0.74}, {3.7, 0.37, 0.90}, {4.2, 0.40, 0.99}, {4.8, 0.40, 1.00}, {5.3, 0.36, 0.93}, {5.9, 0.24, 0.72}, {6.6, 0.12, 0.64}};
            List<double[][]> rs = new ArrayList<>(); for (double[] p : prof) { double base = 0.40; rs.add(ring(p[0], 0, (p[2] + base) / 2, p[1], (p[2] - base) / 2, 2.6, N)); } m.loft(rs, BLACK2, true, true, true);
            for (int sd = -1; sd <= 1; sd += 2) { double[] v = {sd * 0.0042, -0.03, 0};
                m.decal(oval(15, 6, GLASS, GLASS_HI), new double[]{sd * 0.385, 0.87, z(3.65)}, new double[]{0, 0, -0.045}, v, new double[]{sd, 0.2, 0}, 0.012);
                m.decal(oval(10, 6, GLASS, GLASS_HI), new double[]{sd * 0.392, 0.87, z(4.45)}, new double[]{0, 0, -0.045}, v, new double[]{sd, 0.2, 0}, 0.012); }
            m.decal(oval(12, 7, GLASS, GLASS_HI), new double[]{-0.18, 0.86, z(3.6)}, new double[]{0.03, 0, 0}, new double[]{0, -0.022, 0.016}, norm(new double[]{0, 0.7, 0.32}), 0.02);   // the small front pane
            m.box(-0.012, 0.012, 0.985, 1.006, z(5.2), z(4.6), JOINT); }   // the canopy's centre seam
        // side tunnels: from behind the canopy to the tail, faired in at the front
        for (int sd = -1; sd <= 1; sd += 2) { List<double[][]> rs = new ArrayList<>(); double[][] prof = {{4.4, 0.01}, {5.0, 0.45}, {5.8, 0.85}, {6.8, 1}, {14.6, 1}, {15.1, 0.7}, {15.3, 0.25}};
            for (double[] p : prof) rs.add(ring(p[0], sd * 0.70, -0.06, 0.24 * p[1], 0.30 * Math.max(0.15, p[1]), 2.2, 18)); m.loft(rs, BLACK2, true, true, true); }
        // wings: root inside the tunnel (x 0.85), 3.7 m root chord, tip 0.85 m at 3.41 m, leading edge 30 deg; flaps on the inner trailing edge
        double wy = -0.28;
        for (int sd = -1; sd <= 1; sd += 2) { List<double[][]> rs = new ArrayList<>(); for (double f : new double[]{0, 0.5, 1}) { double x = 0.85 + f * (SPAN2 - 0.85), le = 7.4 + f * 1.61, c = 3.7 + f * (0.85 - 3.7); rs.add(foil(le, c, sd * x, wy, 0.05, 0)); }
            for (double[][] r : rs) mirrorWinding(r, sd); m.loft(rs, BLACK, true, true, true);
            // flap joint (inner 60 % of the span, 22 % chord) and a tip light (none: plain) as dark lines on the top
            double[] a0 = {sd * 1.0, wy + 0.10, z(11.1 - 0.22 * 3.6)}, a1 = {sd * 2.3, wy + 0.06, z(10.15 - 0.22 * 2.0)};
            m.decal(line(30, JOINT), a0, mul(sub(a1, a0), 1 / 30.0), new double[]{0, 0, -0.02}, new double[]{0, 1, 0}, 0.004); }
        // tailplanes: all-moving, 15 deg anhedral, pivot fairing at the root
        for (int sd = -1; sd <= 1; sd += 2) { List<double[][]> rs = new ArrayList<>(); double an = Math.toRadians(15);
            for (double f : new double[]{0, 1}) { double span = 0.9 + f * (2.75 - 0.9), x = sd * span, y = -0.18 - (span - 0.9) * Math.tan(an), le = 12.9 + f * 1.15, c = 2.4 + f * (0.8 - 2.4); rs.add(foil(le, c, x, y, 0.045, -sd * an)); }
            for (double[][] r : rs) mirrorWinding(r, sd); m.loft(rs, BLACK, true, true, true);
            List<double[][]> pv = new ArrayList<>(); for (double s : new double[]{13.4, 13.8, 14.6, 15.0}) pv.add(ring(s, sd * 0.86, -0.18, 0.11 * (s > 13.5 && s < 14.9 ? 1 : 0.5), 0.11 * (s > 13.5 && s < 14.9 ? 1 : 0.5), 2, 10)); m.loft(pv, BLACK2, true, true, true); }
        // upper fin: 10 deg wedge, root 3.55 m chord at the top of the body, tip 2.6 m, 1.5 m tall; joint of the upper rudder; speed brakes marked
        { List<double[][]> rs = new ArrayList<>(); for (double f : new double[]{0, 1}) rs.add(wedge(11.92 + f * 0.95, 3.55 - f * 0.95, 0.60 + f * 1.55, 0)); m.loft(rs, BLACK, true, true, false);
            for (int sd = -1; sd <= 1; sd += 2) { double y = 1.12; double c0 = 11.92 + 0.95 * (y - 0.6) / 1.55, c1 = 15.47; double t0 = 0.01, t1 = (3.55 - 0.95 * (y - 0.6) / 1.55) * Math.tan(Math.toRadians(5));
                double[] a = {sd * (t0 + 0.004), y, z(c0)}, b = {sd * (t1 + 0.004), y, z(c1)}; m.decal(line(40, JOINT), a, mul(sub(b, a), 1 / 40.0), new double[]{0, -0.02, 0}, new double[]{sd, 0, 0}, 0.003);
                // speed brake: the trailing part of the fixed fin, a rectangle outline
                double sb0 = 14.3; double tt = (3.55 - 0.95 * 0.25) * Math.tan(Math.toRadians(5)) * (sb0 - 11.92) / 3.55; double[] o = {sd * (tt + 0.006), 1.04, z(sb0)};
                m.decal(rectOutline(30, 9, JOINT), o, new double[]{0, 0, -0.04}, new double[]{0, -0.045, 0}, new double[]{sd, 0, 0}, 0.004); } }
        // lower fin stub (the fixed part kept when the ventral rudder was off), with its jettison placard area
        { List<double[][]> rs = new ArrayList<>(); rs.add(wedge(12.5, 2.97, -0.58, 0)); rs.add(wedge(13.4, 2.07, -1.15, 0)); m.loft(rs, BLACK, true, true, false); }
        // the engine: the base ring, the bell, the dark throat
        { List<double[][]> base = new ArrayList<>(); base.add(ring(15.47, 0, 0, 0.60, 0.60, 2.15, N)); base.add(ring(15.47, 0, 0, 0.50, 0.50, 2, N)); m.loft(base, NOZZLE, false, false, false);
            List<double[][]> bell = new ArrayList<>(); bell.add(ring(15.47, 0, 0, 0.47, 0.47, 2, N)); bell.add(ring(15.65, 0, 0, 0.49, 0.49, 2, N)); bell.add(ring(15.92, 0, 0, 0.53, 0.53, 2, N)); m.loft(bell, NOZZLE, false, false, true);
            List<double[][]> in = new ArrayList<>(); in.add(ring(15.91, 0, 0, 0.51, 0.51, 2, N)); in.add(ring(15.6, 0, 0, 0.40, 0.40, 2, N)); in.add(ring(15.35, 0, 0, 0.18, 0.18, 2, N)); m.loft(in, SOOT, false, true, true); }
        // gear: twin nose wheels on a strut; the rear skids on angled struts under the tunnels
        double gy = -X15.BASE_UP;
        for (int sd = -1; sd <= 1; sd += 2) m.wheel(sd * 0.12, gy + 0.21, z(2.85), 0.21, 0.11, TYRE);
        m.bar(new double[]{0, gy + 0.21, z(2.85)}, new double[]{0, -0.5, z(2.7)}, 0.07, STEEL); m.bar(new double[]{-0.2, gy + 0.21, z(2.85)}, new double[]{0.2, gy + 0.21, z(2.85)}, 0.05, STEEL);
        for (int sd = -1; sd <= 1; sd += 2) { double x = sd * 0.86; m.box(x - 0.07, x + 0.07, gy, gy + 0.05, z(13.3), z(11.6), STEEL); m.bar(new double[]{x, gy + 0.02, z(11.6)}, new double[]{x, gy + 0.12, z(11.45)}, 0.05, STEEL);   // the skid plate and its upturned toe
            m.bar(new double[]{x, gy + 0.05, z(12.2)}, new double[]{x * 0.95, -0.30, z(12.0)}, 0.06, STEEL); m.bar(new double[]{x, gy + 0.05, z(12.9)}, new double[]{x * 0.9, -0.30, z(12.3)}, 0.05, STEEL); }
        // markings (ship 1, 56-6670, as displayed): U.S. AIR FORCE on the tunnels, the national insignia ahead of the fin, 66670 and the NASA band on the fin, USAF and a star on the wings
        for (int sd = -1; sd <= 1; sd += 2) {
            side(m, text("U.S. AIR FORCE", WHITE), sd, 10.2, 0.06, 0.034, 0.945, 0);
            side(m, insignia(), sd, 13.0, 0.12, 0.02, 0.945, 0);
            int[][] band = new int[9][60]; for (int[] r : band) java.util.Arrays.fill(r, YELLOW); stamp(band, text("NASA", BLACK), 21, 1);
            side(m, band, sd, finMid(1.82), 2.0, 0.042, -1, 1);
            side(m, text("66670", WHITE), sd, finMid(1.2) + 0.15, 1.38, 0.05, -1, 1); }
        { int[][] usaf = text("USAF", WHITE); double ps = 0.075; m.decal(rot90(usaf), new double[]{-1.2 - 19 * ps, wy + 0.085, z(8.6)}, new double[]{0, 0, -ps}, new double[]{ps, 0, 0}, new double[]{0, 1, 0}, 0.004);
            int[][] ins = insignia(); double ps2 = 0.03; m.decal(ins, new double[]{1.05, wy + 0.085, z(9.25)}, new double[]{ps2, 0, 0}, new double[]{0, 0, -ps2}, new double[]{0, 1, 0}, 0.004); }
        return m.q; }

    static final double SPAN2 = X15.SPAN / 2;
    /** The upper fin's leading-edge station at height y and its middle station there. */
    static double finLE(double y) { return 11.92 + 0.95 * (y - 0.60) / 1.55; } static double finMid(double y) { return (finLE(y) + 15.47) / 2; }
    /** A side marking centred at station sc, top row at yTop, pixel p: on the plane x = xs (tunnel; read from the outside on each side) or
     *  (fin = 1) on the fin's 10 deg wedge face (the plane tilts with the wedge). Right side reads nose to tail, left side tail to nose. */
    static void side(B m, int[][] px, int sd, double sc, double yTop, double p, double xs, int fin) {
        double w = px[0].length * p, sStart = sd > 0 ? sc - w / 2 : sc + w / 2, ds = sd > 0 ? p : -p, tan = Math.tan(Math.toRadians(5));
        double x0 = fin == 1 ? sd * ((sStart - finLE(yTop - px.length * p / 2)) * tan + 0.006) : sd * xs, dx = fin == 1 ? sd * ds * tan : 0;
        m.decal(px, new double[]{x0, yTop, z(sStart)}, new double[]{dx, 0, -ds}, new double[]{0, -p, 0}, new double[]{sd, 0, 0}, 0.004); }
    static double radius(double s) { for (int i = 1; i < BODY.length; i++) if (s <= BODY[i][0]) { double f = (s - BODY[i - 1][0]) / (BODY[i][0] - BODY[i - 1][0]); return BODY[i - 1][1] + f * (BODY[i][1] - BODY[i - 1][1]); } return BODY[BODY.length - 1][1]; }
    /** Left-side rings run the other way round, so the loft's faces still face out. */
    static void mirrorWinding(double[][] r, int sd) { if (sd > 0) return; for (int i = 0, j = r.length - 1; i < j; i++, j--) { double[] t = r[i]; r[i] = r[j]; r[j] = t; } }

    // ---------------- markings ----------------
    static final String[] FONT_KEYS = {"U", "S", ".", "A", "I", "R", "F", "O", "C", "E", "N", "6", "7", "0", " "};
    static final String[][] FONT = {
        {"10001", "10001", "10001", "10001", "10001", "10001", "01110"}, {"01111", "10000", "10000", "01110", "00001", "00001", "11110"}, {"0", "0", "0", "0", "0", "0", "1"},
        {"01110", "10001", "10001", "11111", "10001", "10001", "10001"}, {"111", "010", "010", "010", "010", "010", "111"}, {"11110", "10001", "10001", "11110", "10100", "10010", "10001"},
        {"11111", "10000", "10000", "11110", "10000", "10000", "10000"}, {"01110", "10001", "10001", "10001", "10001", "10001", "01110"}, {"01110", "10001", "10000", "10000", "10000", "10001", "01110"},
        {"11111", "10000", "10000", "11110", "10000", "10000", "11111"}, {"10001", "11001", "10101", "10011", "10001", "10001", "10001"}, {"00110", "01000", "10000", "11110", "10001", "10001", "01110"},
        {"11111", "00001", "00010", "00100", "01000", "01000", "01000"}, {"01110", "10001", "10011", "10101", "11001", "10001", "01110"}, {"0", "0", "0", "0", "0", "0", "0"}};
    /** Text as a pixel grid (7 rows; -1 = clear), 1 pixel between letters. */
    static int[][] text(String s, int col) { List<String[]> gl = new ArrayList<>(); int w = 0;
        for (char ch : s.toCharArray()) { String k = String.valueOf(ch); int i = java.util.Arrays.asList(FONT_KEYS).indexOf(k); String[] g = FONT[Math.max(0, i)]; gl.add(g); w += g[0].length() + 1; }
        int[][] px = new int[7][Math.max(1, w - 1)]; for (int[] r : px) java.util.Arrays.fill(r, -1); int x = 0;
        for (String[] g : gl) { for (int r = 0; r < 7; r++) for (int c = 0; c < g[r].length(); c++) if (g[r].charAt(c) == '1') px[r][x + c] = col; x += g[0].length() + 1; } return px; }
    static void stamp(int[][] dst, int[][] src, int x0, int y0) { for (int r = 0; r < src.length; r++) for (int c = 0; c < src[r].length; c++) if (src[r][c] >= 0 && y0 + r < dst.length && x0 + c < dst[0].length) dst[y0 + r][x0 + c] = src[r][c]; }
    static int[][] rot90(int[][] a) { int[][] b = new int[a[0].length][a.length]; for (int r = 0; r < a.length; r++) for (int c = 0; c < a[0].length; c++) b[c][a.length - 1 - r] = a[r][c]; return b; }
    /** The US national insignia (star and bars, 1947 on): a blue disc with a white star, white bars with a red stripe, a blue outline. */
    static int[][] insignia() { int W = 48, H = 18; int[][] p = new int[H][W]; double cx = W / 2.0, cy = H / 2.0, R = 8.6;
        for (int r = 0; r < H; r++) for (int c = 0; c < W; c++) { double x = c + 0.5 - cx, y = r + 0.5 - cy, d = Math.hypot(x, y); p[r][c] = -1;
            boolean bar = Math.abs(y) <= R * 0.36 && Math.abs(x) <= R * 2.5, barIn = Math.abs(y) <= R * 0.28 && Math.abs(x) <= R * 2.42;
            if (bar) p[r][c] = BLUE; if (barIn) p[r][c] = Math.abs(y) <= R * 0.09 ? RED : WHITE; if (d <= R) p[r][c] = BLUE; if (d <= R * 0.95 && star(x / (R * 0.95), -y / (R * 0.95))) p[r][c] = WHITE; }
        return p; }
    static boolean star(double x, double y) { double a = Math.atan2(y, x) - Math.PI / 2, r = Math.hypot(x, y); double k = ((a % (2 * Math.PI / 5)) + 2 * Math.PI / 5) % (2 * Math.PI / 5) - Math.PI / 5; double inner = 0.382;
        double edge = inner * Math.cos(Math.PI / 5) / Math.cos(Math.abs(k) - Math.PI / 5 * 0 ) ; double rr = 1 / (Math.cos(Math.abs(k)) + Math.sin(Math.abs(k)) * (1 - inner * Math.cos(Math.PI / 5)) / (inner * Math.sin(Math.PI / 5))); return r <= rr; }
    static int[][] oval(int w, int h, int col, int hi) { int[][] p = new int[h][w]; for (int r = 0; r < h; r++) for (int c = 0; c < w; c++) { double x = (c + 0.5) / w * 2 - 1, y = (r + 0.5) / h * 2 - 1; p[r][c] = x * x + y * y <= 1 ? ((r <= 1 && c < w / 2) ? hi : col) : -1; } return p; }
    static int[][] line(int w, int col) { int[][] p = new int[1][w]; java.util.Arrays.fill(p[0], col); return p; }
    static int[][] rectOutline(int w, int h, int col) { int[][] p = new int[h][w]; for (int r = 0; r < h; r++) for (int c = 0; c < w; c++) p[r][c] = r == 0 || r == h - 1 || c == 0 || c == w - 1 ? col : -1; return p; }

    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    static double[] add(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
    static double[] mul(double[] a, double k) { return new double[]{a[0] * k, a[1] * k, a[2] * k}; }
    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double len(double[] a) { return Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); }
    static double[] norm(double[] a) { double l = len(a); return l < 1e-12 ? new double[]{0, 1, 0} : new double[]{a[0] / l, a[1] / l, a[2] / l}; }
}
