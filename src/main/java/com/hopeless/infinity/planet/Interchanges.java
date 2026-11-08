package com.hopeless.infinity.planet;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.23.1 INTERCHANGES (owner, 2026-11-03: "this is not an acceptable intersection. this is why entrance and exit ramps exist. look up
 * interchange examples"). Built from the published design rules; values in feet are converted, never rounded to fit.
 *
 * Kinds (SCDOT Highway Design Manual ch. 10, after AASHTO's Green Book: a system interchange joins freeway to freeway, a service
 * interchange joins a freeway to a lesser road):
 *  - Interstate meets Interstate: a full cloverleaf with collector-distributor (C-D) roads ("for two access-controlled highways the
 *    minimum acceptable design is a full cloverleaf with C-D roads"). Each Interstate gets a C-D road each way, offset CD_Y from its
 *    centre (a 4-block grass strip past the shoulder: CHOSEN); left turns take the four loops, right turns the four outer ramps.
 *  - Interstate meets an arterial or collector that gets an interchange (Roads.dia: 3 mi apart rural, 1 mi urban): a diamond, "the
 *    simplest and most common" service interchange. Ramp terminals YT out from the Interstate's centre on each side (CHOSEN: 400 ft, about
 *    800 ft between terminals, a conventional rural diamond; SCDOT's compressed diamonds put them 500-700 ft apart).
 *  Other crossroads pass over the Interstate without access, as most do.
 * Ramp parts (SCDOT ch. 10, AASHTO): parallel exit: a 300 ft taper (25:1), then the deceleration length (70 mph highway: 615 ft to a stop,
 * 340 ft to a 50 mph curve); parallel entrance: the acceleration length (70 mph highway: 1,620 ft from a stop, 580 ft from a 50 mph
 * curve) then a 300 ft taper. Ramp lane 12 ft (4 blocks: the plan's lane width; TxDOT allows 12 ft lanes on ramps) with shoulders.
 * Ramp grades: 6 % for 35-40 mph ramps (TxDOT RDM table 15-2). Loops: 30 mph, radius 214 ft (TxDOT RDM 4.7.5, e max 8 %; loop design
 * speed at least 20 mph above 50 mph highways). Outer ramps: the smallest radius that clears the loop by GAP (40 m, CHOSEN; works out to
 * 1,770 ft at 90 degrees: over the 1,200 ft of a 60 mph ramp, the upper range for a 70 mph highway, TxDOT table 15-1).
 * Markings (MUTCD 2023): dotted white lane lines (3 ft line, 9 ft gap) beside auxiliary lanes, solid white gore lines, yellow left edge
 * and white right edge on ramps, a stop line at each exit ramp's terminal (the terminal is stop-controlled).
 * 0.23.2: every ramp is stored as OpenDRIVE-style records (Alignment): tapers, auxiliary lanes and C-D roads as OFFSET runs of their
 * road (cubic offsets), free parts as spiral-arc-spiral curves and arcs; its heights from the vertical QP (Profile) with fixed ends;
 * diamond ramps are as long as the vertical budget needs (Profile.minLength), measured after building.
 */
public final class Interchanges {
    /** 0.27.6 outer ramps turning more than ARC_TURN (CHOSEN 135 deg) are one arc; the C-D roads are sized for outer radii up to OUTER_SPAN_R (CHOSEN 1.5 x the 90-degree OUTER_R). */
    public static final double ARC_TURN = Math.toRadians(135); public static volatile int arcOuters, nearLoop, swings;
    /** 0.27.6 probe: what each interchange road is ("C-D", "loop", "outer", "outer arc", "outer swung"), for the tests' messages. */
    public static final java.util.Map<Roads.Route, String> KIND = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    public static final double FT = 0.3048, TAPER = 300 * FT, DECEL_STOP = 615 * FT, ACCEL_STOP = 1620 * FT, DECEL_50 = 340 * FT, ACCEL_50 = 580 * FT, LANE_EDGE = 14.5, AUX_Y = 16.5,
        CD_Y = 29.5, LOOP_R = 214 * FT, GAP = 40, OUTER_R = (Math.sqrt(2) + 1) / (Math.sqrt(2) - 1) * LOOP_R + GAP / (Math.sqrt(2) - 1), YT = 400 * FT, CURVE = 400, DIV = 250, FLAT = 16, RAMP_GRADE = 0.06,
        BUDGET_MARGIN = 1.1;   // diamond ramps: 10 % over the shortest profile (CHOSEN)
    final Roads rd; double R;   // set by Roads once its radius is known (this is made in Roads' field setup, before its constructor runs)
    public static volatile long diamonds, systems, skipped; public static final List<String> notes = java.util.Collections.synchronizedList(new ArrayList<>());
    /** Built interchanges: {x, y, z (unit sphere), 0 diamond / 1 cloverleaf}. */
    public static final List<double[]> built = java.util.Collections.synchronizedList(new ArrayList<>());
    Interchanges(Roads rd) { this.rd = rd; this.R = rd.R; }

    // ------------------------------------------------------------ geometry along a road
    /** At distance a along r: {point x, y, z (unit sphere), tangent x, y, z, height} (from its records when it has them). */
    double[] frame(Roads.Route r, double a) {
        if (r.al != null && !r.al.elev.isEmpty()) { double[] f = r.al.eval(a); return new double[]{f[0], f[1], f[2], f[3], f[4], f[5], r.al.z(a)}; }
        int n = r.n(); a = Math.max(r.along[0], Math.min(r.along[n - 1], a)); int lo = 0, hi = n - 1; while (hi - lo > 1) { int m = (lo + hi) >>> 1; if (r.along[m] <= a) lo = m; else hi = m; }
        double f = r.along[hi] > r.along[lo] ? (a - r.along[lo]) / (r.along[hi] - r.along[lo]) : 0; double[] p = Sites.norm(new double[]{r.px[lo] + (r.px[hi] - r.px[lo]) * f, r.py[lo] + (r.py[hi] - r.py[lo]) * f, r.pz[lo] + (r.pz[hi] - r.pz[lo]) * f});
        double[] t = Sites.norm(new double[]{r.px[hi] - r.px[lo], r.py[hi] - r.py[lo], r.pz[hi] - r.pz[lo]}); return new double[]{p[0], p[1], p[2], t[0], t[1], t[2], r.z[lo] + (r.z[hi] - r.z[lo]) * f}; }
    /** p in r's frame: {a along, y to the right}. */
    double[] ay(Roads.Route r, double[] p) {
        int n = r.n(), best = 0; double bd = Double.MAX_VALUE; for (int i = 0; i < n; i += 16) { double d = sq(r, i, p); if (d < bd) { bd = d; best = i; } }
        for (int i = Math.max(0, best - 16); i < Math.min(n, best + 17); i++) { double d = sq(r, i, p); if (d < bd) { bd = d; best = i; } }
        int j = Math.min(best, n - 2); if (best > 0 && sq(r, best - 1, p) < sq(r, Math.min(n - 1, best + 1), p)) j = best - 1;
        double[] a = r.p(j), b = r.p(j + 1), ab = {b[0] - a[0], b[1] - a[1], b[2] - a[2]}, ap = {p[0] - a[0], p[1] - a[1], p[2] - a[2]}; double l2 = Roads.dot(ab, ab), u = l2 < 1e-30 ? 0 : Roads.dot(ap, ab) / l2;
        double[] t = Sites.norm(ab), rt = Sites.norm(Sites.cross(t, a)); double y = Roads.dot(new double[]{p[0] - a[0] - ab[0] * u, p[1] - a[1] - ab[1] * u, p[2] - a[2] - ab[2] * u}, rt) * R;
        return new double[]{r.along[j] + u * (r.along[j + 1] - r.along[j]), y}; }
    static double sq(Roads.Route r, int i, double[] p) { double dx = r.px[i] - p[0], dy = r.py[i] - p[1], dz = r.pz[i] - p[2]; return dx * dx + dy * dy + dz * dz; }
    static boolean within(Roads.Route r, double a) { return a > 0 && a < r.along[r.n() - 1]; }

    /** Design values by ramp speed (mph): AASHTO minimum radius at 8 % superelevation (TxDOT RDM 4.7.5: 30 mph 214 ft, 40 mph 444 ft) and
     *  K crest / sag (TxDOT RDM 4.8.2: 30 mph 19 / 37, 40 mph 44 / 64 ft per %). */
    public static final double OUTER_SPAN_R = 1.5 * OUTER_R;
    /** 0.27.6 the length of an easing out to D m and back: a smoothstep's tightest radius is L^2 / (6 (D - 4)); kept to the 40 mph minimum
     *  radius x 1.3 (CHOSEN margin). 0 for D = 4. */
    static double swingLen(double D) { return D <= 4 ? 0 : Math.ceil(Math.sqrt(6 * (D - 4) * rMinFor(40) * 1.3)); }
    /** 0.27.6 the swing that clears a loop of radius lr: past its far side (4 + 2 lr from the C-D road) by GAP. */
    static double swingFor(double lr) { return 4 + 2 * lr + GAP; }
    static double rMinFor(double mph) { return (mph <= 30 ? 214 : 444) * FT; } static double kcFor(double mph) { return mph <= 30 ? 19 : 44; } static double ksFor(double mph) { return mph <= 30 ? 37 : 64; }

    /** A ramp from its alignment (OpenDRIVE records), sampled at a whole number of steps of 8 m or less; clip from the parent roads (lane
     *  edge at edge metres from their centre). Heights: beside a parent road, the parent's (lane to lane); between, the vertical QP (Profile)
     *  at the ramp's grade and K, joined to the parent's height and grade at each end, or to zStart / zEnd {height, grade} at a free end;
     *  follow: the free part's target is the parent's height (a C-D road keeps its main road's profile); over: roads it must clear by
     *  16.5 ft + deck where it passes over them. Then elevation records. */
    Roads.Route ramp(Alignment al, Roads.Route[] parents, double[] edges, boolean stop, double stopBack, boolean follow, double mph, double[] zStart, double[] zEnd, Roads.Route[] over) {
        double total = al.length(); if (total < 20) return null; int n = (int) Math.ceil(total / Roads.SAMPLE_M) + 1; double hr = total / (n - 1);
        Roads.Route r = new Roads.Route(new Roads.Edge(-1, -1, -1, Roads.Cls.RAMP, new double[0][], new int[0], total)); r.px = new double[n]; r.py = new double[n]; r.pz = new double[n]; r.along = new float[n]; r.z = new float[n]; r.ground = new float[n]; r.kind = new byte[n]; r.clip = new float[n];
        r.al = al; r.designMph = mph; r.rMinM = rMinFor(mph); r.kcFt = kcFor(mph); r.ksFt = ksFor(mph); double[] zz = new double[n], pz0 = new double[n];
        boolean[] wetA = new boolean[n], onParent = new boolean[n]; double[] hold = new double[n]; java.util.Arrays.fill(hold, Double.NEGATIVE_INFINITY);
        for (int i = 0; i < n; i++) { double[] f = al.eval(i * hr); double[] p = {f[0], f[1], f[2]}; r.px[i] = p[0]; r.py[i] = p[1]; r.pz[i] = p[2]; r.along[i] = (float) (i * hr); pz0[i] = al.parentZ(i * hr); zz[i] = Double.NaN;
            double[] hw = rd.t.heightAndWater(p, R); r.ground[i] = (float) hw[0]; wetA[i] = !Double.isNaN(hw[1]) && hw[1] > hw[0];
            double c = Double.NaN; int pk = -1; for (int q = 0; q < parents.length; q++) { if (parents.length == 2 && q != (i < n / 2 ? 0 : 1)) continue;   /* a link leaves its first road and joins its second: never the other road (it is at another level) */ double[] ya = ay(parents[q], p); double cl = Math.abs(ya[1]) - edges[q]; if (cl < 7 && (Double.isNaN(c) || cl < c)) { c = cl; pk = q; } }
            r.clip[i] = (float) c; if (pk >= 0 && c <= 2.5) { onParent[i] = true; double[] ya = ay(parents[pk], p); zz[i] = frame(parents[pk], ya[0])[6];   /* beside its parent road: the parent's height, lane to lane */ r.kind[i] = parents[pk].kind[Math.max(0, Math.min(parents[pk].n() - 1, idx(parents[pk], ya[0])))]; }
            if (over != null) for (Roads.Route L : over) { if (L == null) continue; double[] ya = ay(L, p); double hl = L.e.cls == Roads.Cls.RAMP ? Roads.HALF[4] : Roads.HALF[L.e.cls.ordinal()];
                if (Math.abs(ya[1]) < hl + 3 && ya[0] > L.along[0] && ya[0] < L.along[L.n() - 1]) hold[i] = Math.max(hold[i], frame(L, ya[0])[6] + Roads.OVERPASS_M); } }
        // attached only at the two ends (a ramp leaves its road once and joins one once): the attached runs from each end, everything between free
        { int pre = 0; while (pre < n && onParent[pre]) pre++; int suf = n - 1; while (suf >= 0 && onParent[suf]) suf--; for (int i = pre; i <= suf; i++) onParent[i] = false; }
        double endMiss = 0;
        for (int i = 0; i < n; ) { if (onParent[i]) { i++; continue; } int j = i; while (j < n && !onParent[j]) j++;   // free run [i, j)
            int lo = i > 0 ? i - 1 : 0, hi = j < n ? j : n - 1, m = hi - lo + 1; double[] tg = new double[m], w = new double[m], lb = new double[m]; java.util.Arrays.fill(lb, Double.NEGATIVE_INFINITY); boolean hasHold = false;
            for (int k = 0; k < m; k++) { int q = lo + k; boolean pz = follow && !Double.isNaN(pz0[q]); tg[k] = pz ? pz0[q] : r.ground[q]; w[k] = pz ? 1 : 0.01; if (hold[q] > Double.NEGATIVE_INFINITY) { lb[k] = hold[q] + Roads.HOLD_MARGIN_M; hasHold = true; } }
            if (follow && !hasHold) { for (int q = i; q < j; q++) zz[q] = Double.isNaN(pz0[q]) ? r.ground[q] : pz0[q]; i = j; continue; }
            List<double[]> fix = new ArrayList<>();   // {node, height}: the joins at height and grade
            if (i > 0) { double g = lo >= 1 ? (zz[lo] - zz[lo - 1]) / hr : 0; fix.add(new double[]{0, zz[lo]}); fix.add(new double[]{1, zz[lo] + g * hr}); } else if (zStart != null) { fix.add(new double[]{0, zStart[0]}); fix.add(new double[]{1, zStart[0] + zStart[1] * hr}); }
            if (j < n) { double g = hi + 1 < n && onParent[hi + 1] ? (zz[hi + 1] - zz[hi]) / hr : 0; fix.add(new double[]{m - 1, zz[hi]}); fix.add(new double[]{m - 2, zz[hi] - g * hr}); } else if (zEnd != null) { fix.add(new double[]{m - 1, zEnd[0]}); fix.add(new double[]{m - 2, zEnd[0] - zEnd[1] * hr}); }
            for (double[] fx : fix) { int k = (int) fx[0]; if (k < 0 || k >= m) continue; tg[k] = fx[1]; w[k] = Profile.FIX_W; }
            Profile.Out po = Profile.solve(hr, tg, w, lb, RAMP_GRADE, kcFor(mph) * FT * Roads.K_MARGIN, ksFor(mph) * FT * Roads.K_MARGIN, null, 200);
            for (double[] fx : fix) { int k = (int) fx[0]; if (k >= 0 && k < m) endMiss = Math.max(endMiss, Math.abs(po.z[k] - fx[1])); }
            for (int q = i; q < j; q++) zz[q] = po.z[q - lo]; r.holdRounds = Math.max(r.holdRounds, po.iters); r.solveMs += po.ns / 1e6; i = j; }
        r.endMissCm = (int) Math.round(endMiss * 100);
        al.elev.clear(); r.recordDevCm = (int) Math.round(Profile.records(0, hr, zz, al.elev) * 100); double[] ck = Profile.check(al.elev); r.minK = Math.min(ck[0], ck[1]) / FT;
        double hm = 0; for (int i = 0; i < n; i++) { r.z[i] = (float) al.z(i * hr); if (hold[i] > Double.NEGATIVE_INFINITY) hm = Math.max(hm, hold[i] - r.z[i]); } r.holdMissCm = (int) Math.round(hm * 100);
        double mg = 0; for (int i = 0; i < n; i++) { if (!onParent[i]) { double ov = r.z[i] - r.ground[i]; r.kind[i] = (byte) (wetA[i] || ov >= Roads.BRIDGE_M || hold[i] > Double.NEGATIVE_INFINITY ? 1 : -ov >= Roads.TUNNEL_M ? 2 : 0); }
            if (i > 0) mg = Math.max(mg, Math.abs(r.z[i] - r.z[i - 1]) / hr); }
        r.maxGrade = mg; double minR = al.minRadius();   // plane records: exact; OFFSET runs: measured from the samples (16 m chords)
        for (int i = 2; i + 2 < n; i++) if (al.geo.get(al.find((i - 2) * hr)).type == Alignment.OFFSET && al.geo.get(al.find(i * hr)).type == Alignment.OFFSET && al.geo.get(al.find((i + 2) * hr)).type == Alignment.OFFSET) minR = Math.min(minR, circ(r.p(i - 2), r.p(i), r.p(i + 2)));
        r.minRadiusM = minR; if (stop) r.stopAt = total - stopBack;
        double[] p0 = r.p(0), p1 = r.p(Math.min(n - 1, 2)), t = Sites.norm(new double[]{p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]}), lv = Sites.norm(Sites.cross(p0, t)); double[] q = Sites.norm(new double[]{p0[0] + lv[0] * 2 / R, p0[1] + lv[1] * 2 / R, p0[2] + lv[2] * 2 / R});
        double[] lo = rd.local(r, 0, q); r.left = lo == null || lo[1] >= 0 ? 1 : -1; return r; }
    static int idx(Roads.Route r, double a) { int lo = 0, hi = r.n() - 1; while (hi - lo > 1) { int m = (lo + hi) >>> 1; if (r.along[m] <= a) lo = m; else hi = m; } return lo; }
    double circ(double[] a, double[] b, double[] c) { double ab = Rivers.arc(a, b) * R, bc = Rivers.arc(b, c) * R, ca = Rivers.arc(c, a) * R, s = (ab + bc + ca) / 2, ar = Math.sqrt(Math.max(0, s * (s - ab) * (s - bc) * (s - ca))); return ar < 1e-6 ? Double.MAX_VALUE : ab * bc * ca / (4 * ar); }
    /** An S of two spiral-arc-spiral curves from pose A (plane, heading ha) to point B arriving with heading hb: corners a third of the way
     *  out along each end direction, radii from the ramp's minimum up to 1,000 m (CHOSEN), as large as fit. */
    void sCurve(Alignment al, double[] A, double ha, double[] B, double hb, double mph, int[] shortC) { double L = Math.hypot(B[0] - A[0], B[1] - A[1]), rm = rMinFor(mph), v = mph * 0.44704, d = 0.1 * L;
        for (int it = 0; it < 8; it++) { double[] c1 = {A[0] + d * Math.cos(ha), A[1] + d * Math.sin(ha)}, c2 = {B[0] - d * Math.cos(hb), B[1] - d * Math.sin(hb)};   // each leg holds twice its curve's tangent at the minimum radius (corners() gives a curve at most half the next straight)
            double t1 = Alignment.tangent(rm, Alignment.defl(A, c1, c2), Alignment.spiralLen(rm, v))[0], t2 = Alignment.tangent(rm, Alignment.defl(c1, c2, B), Alignment.spiralLen(rm, v))[0]; d = Math.min(0.45 * L, Math.max(0.1 * L, 2.1 * Math.max(t1, t2) + 5)); }   /* 0.27.6 at most 0.45 L: past L / 2 the legs cross, the turns go to 180 deg and d ran away (measured: d 2.7-7.5 km on a 414 m S, ramps 2.7 km); a curve that does not fit counts as short and the caller lengthens D */
        List<double[]> pts = List.of(A, new double[]{A[0] + d * Math.cos(ha), A[1] + d * Math.sin(ha)}, new double[]{B[0] - d * Math.cos(hb), B[1] - d * Math.sin(hb)}, B);
        { double[] c1 = pts.get(1), c2 = pts.get(2); lastS = String.format("L %.0f, d %.0f, turn %.0f deg, defl %.0f / %.0f deg, rm %.0f", L, d, Math.toDegrees(Math.atan2(Math.sin(hb - ha), Math.cos(hb - ha))), Math.toDegrees(Alignment.defl(A, c1, c2)), Math.toDegrees(Alignment.defl(c1, c2, B)), rm); }
        al.corners(pts, rm, 1000, v, shortC); }

    // ------------------------------------------------------------ diamond (service interchange)
    /** A diamond where crossroad c passes over Interstate I (I's segment j, c's segment i). Each ramp's length along I comes from the vertical
     *  budget: the shortest profile that makes its climb at 6 % and 40 mph K (Profile.minLength) plus the landing, BUDGET_MARGIN over; then
     *  the ramp is built and kept only if its profile met both ends at height and grade (measured), else lengthened 50 m and built again. */
    /** 0.27.6 ramps may not meet at grade: does r come within CLASH_XY across and CLASH_Z in height of a ramp in `others`, where both run
     *  free (off their parents) and 40 m or more from their ends (tools/RoadsTest's rule, now a design rule: measured, 19 such places at
     *  seed 1's diamonds, ramps of 1-3 km on skewed crossroads)? */
    public static final double CLASH_XY = 6, CLASH_Z = 5, CLASH_END = 40; public static volatile int clashRefused; public static final double MAX_SKEW = Math.toRadians(30); public static volatile double skewSum; public static volatile int skewN; static volatile String lastClash = ""; static volatile String lastS = "";
    boolean clashes(Roads.Route r, List<Roads.Route> others) { double cxy = Math.cos(CLASH_XY / R);
        for (Roads.Route q : others) for (int i = 0; i < r.n(); i++) { if (!Float.isNaN(r.clip[i]) || r.along[i] < CLASH_END || r.along[r.n() - 1] - r.along[i] < CLASH_END) continue; double[] a = r.p(i);
            for (int j = 0; j < q.n(); j++) { if (!Float.isNaN(q.clip[j]) || q.along[j] < CLASH_END || q.along[q.n() - 1] - q.along[j] < CLASH_END) continue; double[] b = q.p(j);
                if (a[0] * b[0] + a[1] * b[1] + a[2] * b[2] > cxy && Math.abs(r.z[i] - q.z[j]) < CLASH_Z) { clashRefused++; lastClash = String.format("met %s at %.0f of %.0f m (this ramp %.0f of %.0f m), z %.1f / %.1f", KIND.getOrDefault(q, "?"), q.along[j], q.along[q.n() - 1], r.along[i], r.along[r.n() - 1], q.z[j], r.z[i]); return true; } } }
        return false; }
    void diamond(Roads.Route I, Roads.Route c, int j, int i) {
        try { int made = 0; List<Roads.Route> here = new ArrayList<>(); final double mph = 40, kc = kcFor(mph) * FT * Roads.K_MARGIN, ks = ksFor(mph) * FT * Roads.K_MARGIN;
            for (int s = -1; s <= 1; s += 2) { double[] q = terminal(I, c, i, s * YT); if (q == null) { skipped++; continue; }
                double[] Q = {q[0], q[1], q[2]}, tc = {q[3], q[4], q[5]}; double aQ = q[6], zQ = q[7], cHalf = Roads.HALF[c.e.cls.ordinal()];
                double[] fI = frame(I, aQ), tI = {fI[3] * s, fI[4] * s, fI[5] * s}; double[] n = Sites.norm(Sites.cross(Q, tc)); if (Roads.dot(n, tI) < 0) n = new double[]{-n[0], -n[1], -n[2]};
                // 0.27.6 the ramps meet the crossroad at 60 degrees or more (STATED: AASHTO Green Book, intersecting roads at 60 deg or more), at the angle nearest
                // the Interstate's own heading (CHOSEN; measured: square to a skewed crossroad, the 40 mph S-curves swung out up to 3 km and met each other at grade)
                { double bx = Roads.dot(tI, n), by = Roads.dot(tI, tc), beta = Math.max(-MAX_SKEW, Math.min(MAX_SKEW, Math.atan2(by, bx))); n = Sites.norm(new double[]{n[0] * Math.cos(beta) + tc[0] * Math.sin(beta), n[1] * Math.cos(beta) + tc[1] * Math.sin(beta), n[2] * Math.cos(beta) + tc[2] * Math.sin(beta)}); skewSum += Math.abs(beta); skewN++; }
                // exit: taper, deceleration lane (OFFSET runs of I), then the S up to the terminal (stop)
                Roads.Route ex = null; double tried = 0; int[] why = new int[6]; double accD = 0, accOff = 0, accS = 0; String accWhy = "";
                for (double D = CURVE; D <= 2500 && ex == null; D += 50) { double a3 = aQ - s * D, a1 = a3 - s * (DECEL_STOP + TAPER); if (!within(I, a1)) break;
                    Alignment al = Alignment.at(Q, R); al.ease(I.al, a1, s, TAPER, s * (AUX_Y - 4), s * AUX_Y); al.offset(I.al, a1 + s * TAPER, s, DECEL_STOP, s * AUX_Y, 0, 0, 0); double off = al.length(); int[] sc = new int[1];
                    double[] e = al.end(); sCurve(al, new double[]{e[0], e[1]}, e[2], al.plane(Q), al.heading(Q, n), mph, sc);
                    double need = Profile.minLength(zQ - I.al.z(a3), s * I.al.grade(a3), 0, RAMP_GRADE, kc, ks) + FLAT; tried = D; if (al.length() - off < BUDGET_MARGIN * need) { why[0]++; continue; }
                    Roads.Route r = ramp(al, new Roads.Route[]{I}, new double[]{LANE_EDGE}, true, cHalf + 2, false, mph, null, new double[]{zQ, 0}, null);
                    if (r == null) why[1]++; else if (r.endMissCm > 5) why[2]++; else if (r.maxGrade > RAMP_GRADE + 5e-4) why[3]++; else if (sc[0] != 0) why[4]++; else if (clashes(r, here)) why[5]++;
                    if (r != null && r.endMissCm <= 5 && r.maxGrade <= RAMP_GRADE + 5e-4 && sc[0] == 0 && !clashes(r, here)) { ex = r; r.shortCurves = sc[0]; accD = D; accOff = off; accS = al.length() - off; accWhy = lastS; } }
                if (ex != null && ex.along[ex.n() - 1] > 1500) notes.add(String.format("diamond %d/%d: long exit %.0f m; shorter tries refused: budget %d, no profile %d, end miss %d, grade %d, short curves %d, clash %d; D %.0f, lanes %.0f m, S %.0f m (%s), need %.0f", I.e.id, c.e.id, ex.along[ex.n() - 1], why[0], why[1], why[2], why[3], why[4], why[5], accD, accOff, accS, accWhy, 0.0));
                if (ex != null) { rd.addRamp(ex); KIND.put(ex, "diamond exit"); here.add(ex); made++; } else notes.add(String.format("diamond %d/%d: no exit ramp fits up to %.0f m (climb %.1f m)", I.e.id, c.e.id, tried, zQ - frame(I, aQ)[6]));
                // entrance: from the terminal down the S, the acceleration lane, the taper (OFFSET runs of I)
                Roads.Route en = null; tried = 0;
                for (double D = CURVE; D <= 2500 && en == null; D += 50) { double a4 = aQ + s * D; if (!within(I, a4 + s * (ACCEL_STOP + TAPER))) break;
                    Alignment al = Alignment.at(Q, R); int[] sc = new int[1]; double[] f4 = frame(I, a4), P4 = I.al.point(a4, s * AUX_Y);
                    sCurve(al, al.plane(Q), al.heading(Q, n), al.plane(P4), al.heading(P4, new double[]{f4[3] * s, f4[4] * s, f4[5] * s}), mph, sc); double free = al.length();
                    al.offset(I.al, a4, s, ACCEL_STOP, s * AUX_Y, 0, 0, 0); al.ease(I.al, a4 + s * ACCEL_STOP, s, TAPER, s * AUX_Y, s * (AUX_Y - 4));
                    double need = Profile.minLength(I.al.z(a4) - zQ, 0, s * I.al.grade(a4), RAMP_GRADE, kc, ks) + FLAT; tried = D; if (free < BUDGET_MARGIN * need) continue;
                    Roads.Route r = ramp(al, new Roads.Route[]{I}, new double[]{LANE_EDGE}, false, 0, false, mph, new double[]{zQ, 0}, null, null); if (r != null && r.endMissCm <= 5 && r.maxGrade <= RAMP_GRADE + 5e-4 && sc[0] == 0 && !clashes(r, here)) { en = r; r.shortCurves = sc[0]; } }
                if (en != null) { rd.addRamp(en); KIND.put(en, "diamond entrance"); here.add(en); made++; } else notes.add(String.format("diamond %d/%d: no entrance ramp fits up to %.0f m (drop %.1f m; last refusal: %s)", I.e.id, c.e.id, tried, zQ - frame(I, aQ)[6], lastClash)); }
            if (made > 0) { diamonds++; double[] o = c.p(i); built.add(new double[]{o[0], o[1], o[2], 0}); } }
        catch (Exception ex) { skipped++; notes.add("diamond " + I.e.id + "/" + c.e.id + ": " + ex); } }
    /** Where crossroad c is y (signed) to the right of I: {point, c's tangent there (away from the crossing), I's along there, c's height}, or null. */
    double[] terminal(Roads.Route I, Roads.Route c, int i, double y) {
        for (int dir = -1; dir <= 1; dir += 2) { double prev = ay(I, c.p(i))[1];
            for (int k = i + dir; k >= 0 && k < c.n() && Math.abs(c.along[k] - c.along[i]) < 2000; k += dir) { double[] ya = ay(I, c.p(k)); if ((prev - y) * (ya[1] - y) <= 0 && prev != ya[1]) {
                double f = (y - prev) / (ya[1] - prev); int k0 = k - dir; double[] a = c.p(k0), b = c.p(k); double[] p = Sites.norm(new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f});
                double[] t = Sites.norm(new double[]{b[0] - a[0], b[1] - a[1], b[2] - a[2]}); double z = c.z[k0] + (c.z[k] - c.z[k0]) * f; return new double[]{p[0], p[1], p[2], t[0], t[1], t[2], ay(I, p)[0], z}; } prev = ya[1]; } }
        return null; }

    // ------------------------------------------------------------ full cloverleaf with C-D roads (system interchange)
    /** A cloverleaf where Interstate Y passes over Interstate X (X's segment jx, Y's segment iy). */
    synchronized void system(Roads.Route X, Roads.Route Y, int jx, int iy) {
        try { double ax = X.along[jx], ay0 = Y.along[iy]; Roads.Route[][] cd = new Roads.Route[2][2]; Roads.Route[] roads = {X, Y}; double[] aw = {ax, ay0}; double[] O0 = X.p(jx);
            // each turn's quadrant angle (between the two road branches it sits between) sets its radius and how far out it meets the C-D roads
            double span = 0; double[][] plan = new double[8][];
            for (int a = 0, q = 0; a < 2; a++) for (int sa = 0; sa < 2; sa++) for (int sb = 0; sb < 2; sb++, q++) { int b = 1 - a; double[] ta = frame(roads[a], aw[a]), tb = frame(roads[b], aw[b]);
                double[] da = scale(new double[]{ta[3], ta[4], ta[5]}, sa == 0 ? 1 : -1), db = scale(new double[]{tb[3], tb[4], tb[5]}, sb == 0 ? 1 : -1); boolean left = Roads.dot(Sites.cross(da, db), O0) > 0;
                double phi = Math.acos(Math.max(-1, Math.min(1, -Roads.dot(da, db)))); double sn = Math.sin(phi / 2);
                double dzL = Math.abs(frame(roads[1], aw[1])[6] - frame(roads[0], aw[0])[6]), loopR = Math.max(LOOP_R, dzL / (0.045 * 4.0));   // a loop climbs between the two levels at 4.5 % or less over its arc (about 4 radians of it free): a wider loop where the levels are far apart
                double rr = left ? loopR : Math.min(20000, Math.max(444 * FT, (loopR * (1 + sn) + GAP * sn) / Math.max(1e-3, 1 - sn)));   // outer ramp: clears this quadrant's loop by GAP (the 90-degree case gives OUTER_R); at least a 40 mph radius
                double re = left ? rr : Math.min(rr, OUTER_SPAN_R), turnO = Math.PI - phi;   // 0.27.6 measured: at skewed crossings (phi 140-154 deg) the square-crossing formula asked 2.7-6.3 km
                double reach = left ? (CD_Y + 4 + rr) / Math.max(0.05, Math.tan(phi / 2))
                    : (CD_Y + 4 + re) / Math.max(0.05, Math.tan(phi / 2)) + (turnO > ARC_TURN ? re : re * Math.max(1, Math.tan(turnO / 2)) + 50) + swingLen(swingFor(loopR)) + (swingFor(loopR) - 4) / Math.max(0.05, Math.tan(phi / 2));   // 0.27.6 an outer ramp's legs reach this far (outer(): the tangent points moved out rr tan(turn / 2) + 50 m); measured: at 1.6 rr they ran off the C-D roads
                span = Math.max(span, reach + TAPER + 400); plan[q] = new double[]{rr, left ? 1 : 0, phi}; }
            // (the outer ramps below start at the 40 mph radius and widen until they clear the loops; the plan's radius only sizes the C-D roads)
            span = Math.min(span, 5000);
            for (int w = 0; w < 2; w++) for (int si = 0; si < 2; si++) { int s = si == 0 ? 1 : -1; Roads.Route W = roads[w];
                double u0 = -(span + DIV + DECEL_50 + TAPER), a0 = aw[w] + s * u0; if (!within(W, a0) || !within(W, aw[w] + s * (span + DIV + ACCEL_50 + TAPER))) throw new IllegalStateException("C-D road runs past its road's end");
                // the C-D road as OFFSET runs of its Interstate: taper out, auxiliary lane, out to the C-D offset, along, back, auxiliary lane, taper in (each change of offset a smoothstep: a cubic)
                Alignment al = Alignment.at(O0, R); double pa = a0; double[][] runs = {{TAPER, AUX_Y - 4, AUX_Y}, {DECEL_50, AUX_Y, AUX_Y}, {DIV, AUX_Y, CD_Y}, {2 * span, CD_Y, CD_Y}, {DIV, CD_Y, AUX_Y}, {ACCEL_50, AUX_Y, AUX_Y}, {TAPER, AUX_Y, AUX_Y - 4}};
                for (double[] rn : runs) { if (rn[1] == rn[2]) al.offset(W.al, pa, s, rn[0], s * rn[1], 0, 0, 0); else al.ease(W.al, pa, s, rn[0], s * rn[1], s * rn[2]); pa += s * rn[0]; }
                // the upper road's C-D roads pass over the lower road and its C-D roads: 16.5 ft clear + deck (OVERPASS_M), at their own grade and K
                Roads.Route r = ramp(al, new Roads.Route[]{W}, new double[]{LANE_EDGE}, false, 0, true, 40, null, null, w == 1 ? new Roads.Route[]{X, cd[0][0], cd[0][1]} : null); if (r == null) throw new IllegalStateException("C-D road too short");
                rd.addRamp(r); cd[w][si] = r; KIND.put(r, "C-D " + (w == 0 ? "lower" : "upper")); }
            double[] O = O0; int made = 4; List<Object[]> pend = new ArrayList<>(); loops.clear();
            for (int a = 0, q = 0; a < 2; a++) for (int sa = 0; sa < 2; sa++) for (int sb = 0; sb < 2; sb++, q++) { int b = 1 - a; Roads.Route A = cd[a][sa], B = cd[b][sb];
                double[] ta = frame(roads[a], aw[a]), tb = frame(roads[b], aw[b]); double[] da = scale(new double[]{ta[3], ta[4], ta[5]}, sa == 0 ? 1 : -1), db = scale(new double[]{tb[3], tb[4], tb[5]}, sb == 0 ? 1 : -1);
                if (plan[q][1] > 0) { if (link(A, B, O, da, db, plan[q][0], true, null)) made++; }
                else pend.add(new Object[]{A, B, da, db, plan[q][0]}); }
            // outer ramps after the loops: the smallest radius from the plan up that keeps GAP clear of every loop (the roads curve; the plan assumed straight ones)
            double lrMax = 0; for (double[] lp : loops) lrMax = Math.max(lrMax, lp[3]);
            for (Object[] o : pend) { boolean ok = false; for (double D : new double[]{4, swingFor(lrMax)}) { if (ok) break;
                for (double rr = rMinFor(40); rr < 30000; rr *= 1.2) { int n0 = notes.size(); if (link((Roads.Route) o[0], (Roads.Route) o[1], O, (double[]) o[2], (double[]) o[3], rr, false, loops, D)) { made++; ok = true; break; }
                  if (notes.size() > n0 && notes.get(notes.size() - 1).contains("off the C-D roads")) break; } } }   // 0.27.6 from the 40 mph radius up (a wider one only runs further off); then swung wide round the loop
            { StringBuilder pl = new StringBuilder(); for (double[] pq : plan) pl.append(String.format(" %s r%.0f phi%.0f", pq[1] > 0 ? "loop" : "outer", pq[0], Math.toDegrees(pq[2]))); notes.add("plan of cloverleaf " + X.e.id + "/" + Y.e.id + ": span " + Math.round(span) + " m," + pl); }
            systems++; built.add(new double[]{O[0], O[1], O[2], 1}); if (made < 12) notes.add("cloverleaf " + X.e.id + "/" + Y.e.id + ": " + made + " of 12 roads"); }
        catch (Exception ex) { skipped++; notes.add("cloverleaf " + X.e.id + "/" + Y.e.id + ": " + ex); } }
    static double[] scale(double[] v, double k) { return new double[]{v[0] * k, v[1] * k, v[2] * k}; }
    /** A loop (left turn, 270 degrees) or outer ramp (right turn, 90 degrees) from C-D road A to C-D road B: a circle of radius rr tangent to
     *  the lines 4 m right of both, its centre right of both travel directions; a 300 ft taper off A before it and onto B after it. */
    final List<double[]> loops = new ArrayList<>();   // the loops of the cloverleaf being built: {centre x, y, z, radius}
    synchronized boolean link(Roads.Route A, Roads.Route B, double[] O, double[] da, double[] db, double rr, boolean loop, List<double[]> avoid) { return link(A, B, O, da, db, rr, loop, avoid, 4); }
    /** D: how far right of A and B the ramp's own lines run (4 m: beside them; more: an outer ramp swung wide round its quadrant's loop). */
    synchronized boolean link(Roads.Route A, Roads.Route B, double[] O, double[] da, double[] db, double rr, boolean loop, List<double[]> avoid, double D) {
        double[] ra = Sites.norm(Sites.cross(da, O)), rb = Sites.norm(Sites.cross(db, O));
        // the centre in the plane at O: (c - O).ra = CD_Y + 4 + rr and (c - O).rb = the same (straight roads), then fitted to the real C-D roads
        double k = CD_Y + D + rr, det = 1 - Math.pow(Roads.dot(ra, rb), 2); if (det < 1e-3) { notes.add("parallel roads"); return false; } double ab = Roads.dot(ra, rb), x = k * (1 - ab) / det, y = k * (1 - ab) / det;
        double[] cpl = {ra[0] * x + rb[0] * y, ra[1] * x + rb[1] * y, ra[2] * x + rb[2] * y}; double[] c = Sites.norm(new double[]{O[0] + cpl[0] / R, O[1] + cpl[1] / R, O[2] + cpl[2] / R});
        // fitted to the real C-D roads: both offsets at once (a 2 x 2 solve along the two roads' normals each step; Newton on the two lateral offsets)
        for (int it = 0; it < 30; it++) { double[] qa = ay(A, c), qb = ay(B, c); double dA = (D + rr) - qa[1], dB = (D + rr) - qb[1]; if (Math.abs(dA) < 1e-3 && Math.abs(dB) < 1e-3) break;
            double[] fa = frame(A, qa[0]), fb = frame(B, qb[0]); double[] na = Sites.norm(Sites.cross(new double[]{fa[3], fa[4], fa[5]}, new double[]{fa[0], fa[1], fa[2]})), nb = Sites.norm(Sites.cross(new double[]{fb[3], fb[4], fb[5]}, new double[]{fb[0], fb[1], fb[2]}));
            double cs = Roads.dot(na, nb), dt = 1 - cs * cs; if (dt < 1e-4) break; double al0 = (dA - cs * dB) / dt, be = (dB - cs * dA) / dt;
            c = Sites.norm(new double[]{c[0] + (na[0] * al0 + nb[0] * be) / R, c[1] + (na[1] * al0 + nb[1] * be) / R, c[2] + (na[2] * al0 + nb[2] * be) / R}); }
        // 0.24.0 the arc keeps its design radius rr; where it leaves A is solved (secant on A's distance) so that it lands on B's line 4 m out
        // at B's heading: one arc cannot touch two curved roads at a fixed radius, so the start slides along A instead (the centre fit above is
        // only the first guess)
        if (!loop) { double[] fa = A.al.eval(ay(A, c)[0]), fb = B.al.eval(ay(B, c)[0]); double turn = Math.acos(Math.max(-1, Math.min(1, fa[3] * fb[3] + fa[4] * fb[4] + fa[5] * fb[5])));
            if (turn <= ARC_TURN || D > 4) { int n0 = notes.size(); if (outer(A, B, O, c, rr, avoid, D)) return true; if (D > 4) return false; if (notes.size() == n0 || notes.get(notes.size() - 1).contains("off the C-D roads")) return false; }   // refused near a loop or off the road: the caller widens or stops
            arcOuters++; }   // 0.27.6 the corner construction failed on geometry (measured: tangents crossing behind at 34-degree turns): one arc, as the loops   // 0.27.6 a near U-turn (a skewed crossing's sharp quadrant): one arc, as the loops (the corner construction's tangents run out R tan(turn / 2): 836 m at 161 deg, 10 km at 178)
        Alignment al = Alignment.at(O, R); double a0 = ay(A, c)[0], f0 = landing(al, A, B, a0, rr)[0], a1 = a0 + 20, f1 = landing(al, A, B, a1, rr)[0];
        for (int it = 0; it < 30 && Math.abs(f1) > 0.01; it++) { if (Math.abs(f1 - f0) < 1e-9) break; double a2 = a1 - f1 * (a1 - a0) / (f1 - f0); a0 = a1; f0 = f1; a1 = Math.max(A.along[0] + TAPER, Math.min(A.along[A.n() - 1] - 1, a2)); f1 = landing(al, A, B, a1, rr)[0]; }
        double[] ld = landing(al, A, B, a1, rr); double aA = a1, sweep = ld[1], aB = ld[2];
        if (Math.abs(ld[0]) > 0.05) { StringBuilder sb = new StringBuilder(); double g0 = ay(A, c)[0]; for (int kk = -6; kk <= 6; kk++) { double[] q = landing(al, A, B, g0 + kk * 100, rr); sb.append(String.format(" %.0f:%.0f/%.0f", g0 + kk * 100, q[0], Math.toDegrees(q[1]))); }
            notes.add(String.format("%s: the arc cannot land on B (%.2f m off; rr %.0f; offset/turn by A distance:%s)", loop ? "loop" : "outer", ld[0], rr, sb)); return false; }
        if (aA - TAPER < A.along[0] || aB + TAPER > B.along[B.n() - 1]) { notes.add(String.format("%s: off the C-D roads (A %.0f of %.0f..%.0f, B %.0f of ..%.0f)", loop ? "loop" : "outer", aA, A.along[0], A.along[A.n() - 1], aB, B.along[B.n() - 1])); return false; }
        if (loop ? sweep < Math.PI : sweep > Math.PI) { notes.add(String.format("%s: sweep %.0f deg", loop ? "loop" : "outer", Math.toDegrees(sweep))); return false; }
        // the ramp as records: a taper off A (OFFSET, 0 to 4 m), the arc (clockwise: curvature -1/rr), a taper onto B (OFFSET, 4 to 0 m)
        al.ease(A.al, aA - TAPER, 1, TAPER, 0, 4); double[] e = al.end(); al.add(Alignment.ARC, e[0], e[1], e[2], sweep * rr, -1 / rr, -1 / rr);
        { double[] ae = al.end(), pb = al.plane(B.al.point(aB, 4)); double gap = Math.hypot(ae[0] - pb[0], ae[1] - pb[1]); if (gap > 0.5) { notes.add(String.format("%s: arc ends %.2f m from B's taper", loop ? "loop" : "outer", gap)); return false; } }
        al.ease(B.al, aB, 1, TAPER, 4, 0);
        if (avoid != null) for (double[] lp : avoid) for (double u = 0; u <= al.length(); u += 8) { double[] p = al.eval(u); if (Rivers.arc(new double[]{p[0], p[1], p[2]}, lp) * R < lp[3] + GAP) { nearLoop++; return false; } }   // too close to a loop: the caller tries a wider radius
        Roads.Route r = ramp(al, new Roads.Route[]{A, B}, new double[]{2, 2}, false, 0, false, loop ? 30 : 40, null, null, null); if (r == null) { notes.add(String.format("%s (arc): no vertical design (rr %.0f)", loop ? "loop" : "outer", rr)); return false; } rd.addRamp(r); KIND.put(r, loop ? "loop" : "outer arc"); if (loop) loops.add(new double[]{c[0], c[1], c[2], rr}); return true; }
    /** 0.24.0 an outer ramp (a right turn under 180 degrees) by the corner construction: tangent lines along A and B (4 m out) from the
     *  circle's tangent points, joined where they cross, a spiral-arc-spiral curve at the largest radius from the 40 mph minimum to rr that
     *  fits (Alignment.corners), tapers off A and onto B. Every join is tangent whatever the roads' curvature. */
    boolean outer(Roads.Route A, Roads.Route B, double[] O, double[] c, double rr, List<double[]> avoid, double D) {
        double LE = swingLen(D);   // 0.27.6 the swing out to D and back (smoothstep easings; none at D = 4)
        double aA = ay(A, c)[0], aB = ay(B, c)[0];
        { double[] fa = A.al.eval(aA), fb = B.al.eval(aB); double cs = fa[3] * fb[3] + fa[4] * fb[4] + fa[5] * fb[5], turn = Math.acos(Math.max(-1, Math.min(1, cs))), ext = rr * Math.max(1, Math.tan(turn / 2)) + 50;
          aA -= ext; aB += ext; }   // the tangent points moved out along both roads so each leg holds the curve's tangent twice over (corners() takes at most half the last leg)
        if (aA - TAPER - LE < A.along[0] || aB + LE + TAPER > B.along[B.n() - 1]) { notes.add(String.format("outer: off the C-D roads (A %.0f of %.0f, B %.0f of %.0f; rr %.0f, swing %.0f m)", aA - LE, A.along[A.n() - 1], aB + LE, B.along[B.n() - 1], rr, D)); return false; }
        Alignment al = Alignment.at(O, R); al.ease(A.al, aA - TAPER - LE, 1, TAPER, 0, 4); if (LE > 0) al.ease(A.al, aA - LE, 1, LE, 4, D);
        double[] e = al.end(); double[] sB = B.al.point(aB, D), fb = B.al.eval(aB), P = al.plane(sB); double hb = al.heading(sB, new double[]{fb[3], fb[4], fb[5]});
        double ca = Math.cos(e[2]), sa = Math.sin(e[2]), cb = Math.cos(hb), sbn = Math.sin(hb), den = ca * sbn - sa * cb; if (Math.abs(den) < 1e-6) { notes.add("outer: parallel tangents"); return false; }
        double t1 = ((P[0] - e[0]) * sbn - (P[1] - e[1]) * cb) / den, t2 = ((P[0] - e[0]) * sa - (P[1] - e[1]) * ca) / den; if (t1 <= 0 || t2 >= 0) { notes.add(String.format("outer: tangents cross behind (%.0f, %.0f)", t1, t2)); return false; }
        double[] C = {e[0] + t1 * ca, e[1] + t1 * sa}; int[] sc = new int[1]; al.corners(List.of(new double[]{e[0], e[1]}, C, P), rMinFor(40), Math.max(rMinFor(40), rr), 40 * 0.44704, sc);
        if (sc[0] > 0) { double hOut = Math.atan2(P[1] - C[1], P[0] - C[0]), dd = hOut - e[2]; while (dd > Math.PI) dd -= 2 * Math.PI; while (dd < -Math.PI) dd += 2 * Math.PI; double[] tg = Alignment.tangent(rMinFor(40), dd, Alignment.spiralLen(rMinFor(40), 40 * 0.44704));
            notes.add(String.format("outer: no room for the 40 mph radius (rr %.0f; legs %.0f / %.0f m; turn %.0f deg; T at 40 mph %.0f m)", rr, Math.hypot(C[0] - e[0], C[1] - e[1]), Math.hypot(P[0] - C[0], P[1] - C[1]), Math.toDegrees(dd), tg[0])); return false; }
        double[] ae = al.end(); if (Math.hypot(ae[0] - P[0], ae[1] - P[1]) > 0.5) { notes.add(String.format("outer: ends %.2f m from B", Math.hypot(ae[0] - P[0], ae[1] - P[1]))); return false; }
        if (LE > 0) al.ease(B.al, aB, 1, LE, D, 4); al.ease(B.al, aB + LE, 1, TAPER, 4, 0); if (D > 4) swings++;
        if (avoid != null) for (double[] lp : avoid) for (double u = 0; u <= al.length(); u += 8) { double[] p = al.eval(u); if (Rivers.arc(new double[]{p[0], p[1], p[2]}, lp) * R < lp[3] + GAP) { nearLoop++; return false; } }
        Roads.Route r = ramp(al, new Roads.Route[]{A, B}, new double[]{2, 2}, false, 0, false, 40, null, null, null); if (r == null) { notes.add(String.format("outer: no vertical design (rr %.0f)", rr)); return false; } rd.addRamp(r); KIND.put(r, D > 4 ? "outer swung" : "outer"); return true; }
    /** An arc of radius rr turning right from 4 m right of A at aA (A's heading there) until its heading is B's where it ends: {how far its end
     *  lies from B's line 4 m out (m, signed), the turn (rad), B's distance at the end}. */
    double[] landing(Alignment al, Roads.Route A, Roads.Route B, double aA, double rr) {
        double[] sA = A.al.point(aA, 4), fa = A.al.eval(aA); double hA = al.heading(sA, new double[]{fa[3], fa[4], fa[5]}); double[] p0 = al.plane(sA);
        double aB = ay(B, sA)[0], turn = 0, off = 0;
        for (int it = 0; it < 6; it++) { double[] fb = B.al.eval(aB); double hB = al.heading(new double[]{fb[0], fb[1], fb[2]}, new double[]{fb[3], fb[4], fb[5]}); turn = ((hA - hB) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
            double h = hA - turn, x = p0[0] - (Math.sin(h) - Math.sin(hA)) * rr, y = p0[1] + (Math.cos(h) - Math.cos(hA)) * rr; double[] E = al.sphere(x, y); double[] q = ay(B, E); aB = q[0]; off = q[1] - 4; }
        return new double[]{off, turn, aB}; }
    public static String probe() { return String.format("interchanges: %d cloverleafs, %d diamonds, %d skipped, %d outer ramps as one arc, %d swung wide round a loop, %d ramp tries refused near a loop, %d for meeting another ramp at grade%s", systems, diamonds, skipped, arcOuters, swings, nearLoop, clashRefused, notes.isEmpty() ? "" : " (last: " + notes.get(notes.size() - 1) + ")"); }
}
