package com.hopeless.infinity.planet;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.23.2 ROAD ALIGNMENT, stored the way ASAM OpenDRIVE 1.8 stores a road (owner, 2026-11-03: "switch to that storage format"):
 *  - plan view: a chain of geometry records along the reference line, each from distance s with a start pose (x, y, heading) and a
 *    length: LINE, ARC (constant curvature) and SPIRAL (an Euler spiral / clothoid: curvature changes linearly from k0 to k1), in a plane
 *    tangent to the planet at the road (gnomonic: the frame m, e1, e2, as Roads lays the road); and OFFSET: a run that follows a parent
 *    alignment at a lateral offset given as a cubic in the parent's distance (OpenDRIVE lane offset: ramps' tapers and auxiliary lanes,
 *    collector-distributor roads);
 *  - elevation: cubic records z = a + b ds + c ds^2 + d ds^3 from distance s (OpenDRIVE elevationProfile). Roads write straight grades
 *    (c = d = 0) and parabolic vertical curves (d = 0), the AASHTO form.
 * The record is the road; Route's 8 m samples are made from it for the column look-ups (as OpenDRIVE readers tessellate).
 * Heading: radians from e1 toward e2 (counter-clockwise seen from above the planet); curvature + turns left.
 */
public final class Alignment {
    public static final byte LINE = 0, ARC = 1, SPIRAL = 2, OFFSET = 3;
    public static final class Geo { public byte type; public double s, len, x, y, hdg, k0, k1;
        public Alignment parent; public double ps, plen; public int dir; public double oa, ob, oc, od;   // OFFSET: parent's distance at start, parent length covered, direction, offset cubic (m right of the parent's direction of increasing s)
        public double offAt(double u) { return oa + u * (ob + u * (oc + u * od)); } }
    public static final class Elev { public double s, a, b, c, d; Elev(double s, double a, double b, double c, double d) { this.s = s; this.a = a; this.b = b; this.c = c; this.d = d; }
        public double z(double ds) { return a + ds * (b + ds * (c + ds * d)); } public double g(double ds) { return b + ds * (2 * c + ds * 3 * d); } }
    public final List<Geo> geo = new ArrayList<>(); public final List<Elev> elev = new ArrayList<>();
    final double[] m, e1, e2; final double R;
    public Alignment(double[] m, double[] e1, double[] e2, double R) { this.m = m; this.e1 = e1; this.e2 = e2; this.R = R; }
    /** A frame at unit point o (any e1 square to it). */
    public static Alignment at(double[] o, double R) { double[] e1 = Sites.norm(Sites.cross(o, Math.abs(o[2]) < 0.9 ? new double[]{0, 0, 1} : new double[]{1, 0, 0})); return new Alignment(o.clone(), e1, Sites.cross(o, e1), R); }
    public double length() { if (geo.isEmpty()) return 0; Geo g = geo.get(geo.size() - 1); return g.s + g.len; }

    // ------------------------------------------------------------ the plane
    public double[] plane(double[] s) { double dd = Roads.dot(s, m); return new double[]{R * Roads.dot(s, e1) / dd, R * Roads.dot(s, e2) / dd}; }
    public double[] sphere(double x, double y) { return Sites.norm(new double[]{m[0] + (e1[0] * x + e2[0] * y) / R, m[1] + (e1[1] * x + e2[1] * y) / R, m[2] + (e1[2] * x + e2[2] * y) / R}); }
    /** A unit direction on the sphere at p, as a heading in this plane. */
    public double heading(double[] p, double[] t) { double[] a = plane(p), b = plane(new double[]{p[0] + t[0] * 1e-6, p[1] + t[1] * 1e-6, p[2] + t[2] * 1e-6}); return Math.atan2(b[1] - a[1], b[0] - a[0]); }

    // ------------------------------------------------------------ records
    Geo add(byte type, double x, double y, double hdg, double len, double k0, double k1) { Geo g = new Geo(); g.type = type; g.s = length(); g.x = x; g.y = y; g.hdg = hdg; g.len = len; g.k0 = k0; g.k1 = k1; geo.add(g); return g; }
    /** The pose {x, y, heading, curvature} at u along a plane record (spiral: Simpson's rule on the heading, 1 m steps or finer). */
    public static double[] pose(Geo g, double u) {
        switch (g.type) {
            case LINE -> { return new double[]{g.x + u * Math.cos(g.hdg), g.y + u * Math.sin(g.hdg), g.hdg, 0}; }
            case ARC -> { double k = g.k0; if (Math.abs(k) < 1e-12) return new double[]{g.x + u * Math.cos(g.hdg), g.y + u * Math.sin(g.hdg), g.hdg, 0}; double h = g.hdg + k * u;
                return new double[]{g.x + (Math.sin(h) - Math.sin(g.hdg)) / k, g.y - (Math.cos(h) - Math.cos(g.hdg)) / k, h, k}; }
            default -> { double dk = (g.k1 - g.k0) / g.len; int n = Math.max(2, 2 * (int) Math.ceil(u / 2)); double hh = u / n, sx = 0, sy = 0;
                for (int i = 0; i <= n; i++) { double t = i * hh, th = g.hdg + g.k0 * t + dk * t * t / 2, wgt = i == 0 || i == n ? 1 : (i % 2 == 1 ? 4 : 2); sx += wgt * Math.cos(th); sy += wgt * Math.sin(th); }
                return new double[]{g.x + sx * hh / 3, g.y + sy * hh / 3, g.hdg + g.k0 * u + dk * u * u / 2, g.k0 + dk * u}; } } }
    int find(double s) { int lo = 0, hi = geo.size() - 1; while (lo < hi) { int md = (lo + hi + 1) >>> 1; if (geo.get(md).s <= s) lo = md; else hi = md - 1; } return lo; }
    /** At distance s: {point x, y, z (unit sphere), unit tangent x, y, z, curvature (1/m, + left)}. */
    public double[] eval(double s) {
        s = Math.max(0, Math.min(length(), s)); Geo g = geo.get(find(s)); double u = s - g.s;
        if (g.type == OFFSET) { double f = g.len > 0 ? g.plen / g.len : 1; double pu = u * f; double[] p = g.parent.point(g.ps + g.dir * pu, g.offAt(pu)), q = g.parent.point(g.ps + g.dir * (pu + 0.5), g.offAt(pu + 0.5)), b = g.parent.point(g.ps + g.dir * (pu - 0.5), g.offAt(pu - 0.5));
            double[] t = Sites.norm(new double[]{q[0] - b[0], q[1] - b[1], q[2] - b[2]}); return new double[]{p[0], p[1], p[2], t[0], t[1], t[2], Double.NaN}; }
        double[] ps = pose(g, u), p = sphere(ps[0], ps[1]), q = sphere(ps[0] + Math.cos(ps[2]) * 0.5, ps[1] + Math.sin(ps[2]) * 0.5); double[] t = Sites.norm(new double[]{q[0] - p[0], q[1] - p[1], q[2] - p[2]});
        return new double[]{p[0], p[1], p[2], t[0], t[1], t[2], ps[3]}; }
    /** The point y metres right of the reference line at s (right of the direction of increasing s). */
    public double[] point(double s, double y) { double[] f = eval(s); double[] p = {f[0], f[1], f[2]}, rt = Sites.norm(Sites.cross(new double[]{f[3], f[4], f[5]}, p)); return Sites.norm(new double[]{p[0] + rt[0] * y / R, p[1] + rt[1] * y / R, p[2] + rt[2] * y / R}); }
    /** The height at s from the elevation records (NaN if none). */
    public double z(double s) { if (elev.isEmpty()) return Double.NaN; int lo = 0, hi = elev.size() - 1; while (lo < hi) { int md = (lo + hi + 1) >>> 1; if (elev.get(md).s <= s) lo = md; else hi = md - 1; } Elev e = elev.get(lo); return e.z(s - e.s); }
    public double grade(double s) { if (elev.isEmpty()) return Double.NaN; int lo = 0, hi = elev.size() - 1; while (lo < hi) { int md = (lo + hi + 1) >>> 1; if (elev.get(md).s <= s) lo = md; else hi = md - 1; } Elev e = elev.get(lo); return e.g(s - e.s); }
    /** OFFSET records: the parent's height at the parent point s maps to (NaN outside them). */
    public double parentZ(double s) { Geo g = geo.get(find(Math.max(0, Math.min(length(), s)))); if (g.type != OFFSET) return Double.NaN; double f = g.len > 0 ? g.plen / g.len : 1; return g.parent.z(g.ps + g.dir * (s - g.s) * f); }

    // ------------------------------------------------------------ building
    /** The end pose {x, y, heading, curvature} of the last plane record. */
    public double[] end() { Geo g = geo.get(geo.size() - 1); if (g.type == OFFSET) { double[] f = eval(length()); double[] p = {f[0], f[1], f[2]}, xy = plane(p); return new double[]{xy[0], xy[1], heading(p, new double[]{f[3], f[4], f[5]}), 0}; } return pose(g, g.len); }
    /** An OFFSET run of parent from ps, dir, parent length plen, offset cubic; its own length measured (2 m steps). */
    public Geo offset(Alignment parent, double ps, int dir, double plen, double oa, double ob, double oc, double od) {
        Geo g = new Geo(); g.type = OFFSET; g.s = length(); g.parent = parent; g.ps = ps; g.dir = dir; g.plen = plen; g.oa = oa; g.ob = ob; g.oc = oc; g.od = od;
        int n = Math.max(1, (int) Math.ceil(plen / 2)); double L = 0; double[] prev = parent.point(ps, oa); for (int i = 1; i <= n; i++) { double u = plen * i / n; double[] p = parent.point(ps + dir * u, g.offAt(u)); L += Rivers.arc(prev, p) * R; prev = p; }
        g.len = L; geo.add(g); return g; }
    /** OFFSET run whose offset eases from y0 to y1 by the smoothstep 3t^2 - 2t^3 over the parent length plen (a cubic). */
    public Geo ease(Alignment parent, double ps, int dir, double plen, double y0, double y1) { double D = y1 - y0; return offset(parent, ps, dir, plen, y0, 0, 3 * D / (plen * plen), -2 * D / (plen * plen * plen)); }

    // ------------------------------------------------------------ AASHTO horizontal curves with spiral transitions
    /** Spiral length for radius r (m) at design speed v (m/s): the desirable 2.0 s of travel (AASHTO Green Book 2001 exhibit 3-34), held
     *  between the Green Book's minimum sqrt(24 p_min r), p_min = 0.20 m (eq. 3-28: the lateral shift a driver notices) and maximum
     *  sqrt(24 p_max r), p_max = 1.0 m (eq. 3-29). */
    public static double spiralLen(double r, double v) { return Math.max(Math.sqrt(24 * 0.20 * r), Math.min(Math.sqrt(24 * 1.0 * r), 2.0 * v)); }
    /** A curve of radius r turning |delta| with spirals of length ls (cut to r |delta| when they would meet): {tangent length T, ls used}.
     *  T = (r + p) tan(delta/2) + k from the spiral's end (xs, ys): p = ys - r (1 - cos ts), k = xs - r sin ts, ts = ls / 2r. */
    public static double[] tangent(double r, double delta, double ls) { delta = Math.abs(delta); if (delta < 1e-9) return new double[]{0, 0}; if (ls / r > delta) ls = r * delta;
        double ts = ls / (2 * r); Geo sp = new Geo(); sp.type = SPIRAL; sp.len = ls; sp.k0 = 0; sp.k1 = 1 / r; double[] e = ls > 0 ? pose(sp, ls) : new double[]{0, 0};
        double p = e[1] - r * (1 - Math.cos(ts)), k = e[0] - r * Math.sin(ts); return new double[]{(r + p) * Math.tan(delta / 2) + k, ls}; }
    /** The corners of a polyline dropped until every straight holds the tangents of the curves at both its ends at radius rMin (of a
     *  straight too short, the end corner whose removal moves the line least goes). */
    public static List<double[]> fit(List<double[]> p, double rMin, double v) {
        p = new ArrayList<>(p);
        for (int guard = 0; guard < 10000 && p.size() > 2; guard++) { int n = p.size(); double[] T = new double[n];
            for (int i = 1; i + 1 < n; i++) { double d = defl(p.get(i - 1), p.get(i), p.get(i + 1)); T[i] = Math.abs(d) > 3.0 ? 1e9 : tangent(rMin, d, spiralLen(rMin, v))[0]; }
            int worst = -1; double over = 0; for (int i = 0; i + 1 < n; i++) { double L = dist(p.get(i), p.get(i + 1)), need = T[i] + T[i + 1]; if (need - L > over) { over = need - L; worst = i; } }
            if (worst < 0) break; int a = worst, b = worst + 1, drop;
            if (a == 0) drop = b; else if (b == n - 1) drop = a; else drop = Roads.segDist(p.get(a)[0], p.get(a)[1], p.get(a - 1), p.get(a + 1)) < Roads.segDist(p.get(b)[0], p.get(b)[1], p.get(b - 1), p.get(b + 1)) ? a : b;
            if (drop == 0 || drop == n - 1) break; p.remove(drop); }
        return p; }
    static double dist(double[] a, double[] b) { return Math.hypot(b[0] - a[0], b[1] - a[1]); }
    /** The signed turn at b (radians, + left). */
    public static double defl(double[] a, double[] b, double[] c) { double h1 = Math.atan2(b[1] - a[1], b[0] - a[0]), h2 = Math.atan2(c[1] - b[1], c[0] - b[0]), d = h2 - h1; while (d > Math.PI) d -= 2 * Math.PI; while (d < -Math.PI) d += 2 * Math.PI; return d; }
    /** Appends straights and spiral-arc-spiral curves through the corners of pts (plane, this frame), from the current end (or pts[0] if
     *  empty) to the last point. Each curve's radius: the largest from rMin to rMax (or, for a small turn, up to the radius whose two
     *  spirals alone make the turn at the desirable spiral length) whose tangent fits in the straight before it (what is
     *  left) and half the straight after it; below rMin only where even rMin does not fit (counted in short[0]). */
    public void corners(List<double[]> pts, double rMin, double rMax, double v, int[] shortCurves) {
        double cx, cy, ch; if (geo.isEmpty()) { cx = pts.get(0)[0]; cy = pts.get(0)[1]; ch = Math.atan2(pts.get(1)[1] - cy, pts.get(1)[0] - cx); } else { double[] e = end(); cx = e[0]; cy = e[1]; ch = e[2]; }
        for (int i = 1; i < pts.size(); i++) { double[] b = pts.get(i); double along = (b[0] - cx) * Math.cos(ch) + (b[1] - cy) * Math.sin(ch);
            if (i == pts.size() - 1) { if (along > 1e-6) add(LINE, cx, cy, ch, along, 0, 0); break; }
            double[] c = pts.get(i + 1); double hOut = Math.atan2(c[1] - b[1], c[0] - b[0]), d = hOut - ch; while (d > Math.PI) d -= 2 * Math.PI; while (d < -Math.PI) d += 2 * Math.PI;
            if (Math.abs(d) < 1e-5) continue; double room = Math.min(along, (i + 1 == pts.size() - 1 ? 1.0 : 0.5) * dist(b, c));   // 0.27.6 the last leg has no curve after it: all of it is this corner's (measured: a 161-degree outer ramp needing 836 m was refused 1,574 m of leg)
            double r = rMin; if (tangent(rMin, d, spiralLen(rMin, v))[0] > room) { double lo = 1, hi = rMin; for (int it = 0; it < 40; it++) { double md = (lo + hi) / 2; if (tangent(md, d, spiralLen(md, v))[0] > room) hi = md; else lo = md; } r = lo; if (shortCurves != null) shortCurves[0]++; }
            else { double lo = rMin, hi = Math.min(20000, Math.max(rMax, 2.0 * v / Math.abs(d))); for (int it = 0; it < 40; it++) { double md = (lo + hi) / 2; if (tangent(md, d, spiralLen(md, v))[0] > room) hi = md; else lo = md; } r = lo; }
            double[] tg = tangent(r, d, spiralLen(r, v)); double T = tg[0], ls = tg[1], k = Math.signum(d) / r; double lin = along - T;
            if (lin > 1e-6) { add(LINE, cx, cy, ch, lin, 0, 0); cx += lin * Math.cos(ch); cy += lin * Math.sin(ch); }
            double arc = r * Math.abs(d) - ls;   // the arc between the spirals: r (|delta| - 2 ts), ts = ls / 2r
            if (ls > 1e-6) { Geo g = add(SPIRAL, cx, cy, ch, ls, 0, k); double[] e = pose(g, ls); cx = e[0]; cy = e[1]; ch = e[2]; }
            if (arc > 1e-6) { Geo g = add(ARC, cx, cy, ch, arc, k, k); double[] e = pose(g, arc); cx = e[0]; cy = e[1]; ch = e[2]; }
            if (ls > 1e-6) { Geo g = add(SPIRAL, cx, cy, ch, ls, k, 0); double[] e = pose(g, ls); cx = e[0]; cy = e[1]; ch = e[2]; } } }
    /** The smallest radius of the plane records (m). */
    public double minRadius() { double k = 0; for (Geo g : geo) if (g.type == ARC || g.type == SPIRAL) k = Math.max(k, Math.max(Math.abs(g.k0), Math.abs(g.k1))); return k == 0 ? Double.MAX_VALUE : 1 / k; }
    /** The largest gap between one record's end and the next one's start (m; plane records only), and the largest heading change there (rad). */
    public double[] gaps() { double gp = 0, gh = 0; for (int i = 0; i + 1 < geo.size(); i++) { Geo a = geo.get(i), b = geo.get(i + 1); if (a.type == OFFSET || b.type == OFFSET) { double[] p = eval(b.s - 1e-6), q = eval(b.s + 1e-6); gp = Math.max(gp, Rivers.arc(new double[]{p[0], p[1], p[2]}, new double[]{q[0], q[1], q[2]}) * R); continue; }
        double[] e = pose(a, a.len); gp = Math.max(gp, Math.hypot(e[0] - b.x, e[1] - b.y)); double dh = e[2] - b.hdg; while (dh > Math.PI) dh -= 2 * Math.PI; while (dh < -Math.PI) dh += 2 * Math.PI; gh = Math.max(gh, Math.abs(dh)); } return new double[]{gp, gh}; }
    /** 0.27.6 probe: which record holds station s (type, start, length, curvatures) */
    public String geoAt(double s) { for (Geo g : geo) if (s <= g.s + g.len) return String.format("%s from %.0f, %.0f m, k %.5f to %.5f", new String[]{"line", "arc", "spiral", "offset"}[g.type], g.s, g.len, g.k0, g.k1); return "past the end"; }
    public String summary() { int[] c = new int[4]; for (Geo g : geo) c[g.type]++; int par = 0; for (Elev e : elev) if (Math.abs(e.c) > 1e-12) par++; return String.format("%d lines, %d arcs, %d spirals, %d offsets; %d elevation records (%d vertical curves)", c[0], c[1], c[2], c[3], elev.size(), par); }
}
