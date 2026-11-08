package com.hopeless.infinity.planet;

import java.util.Arrays;
import java.util.List;

/**
 * 0.23.2 VERTICAL ALIGNMENT as an optimisation problem, the way road engineers solve it (Hare, Lucet & Rahman 2015, EJOR 241(3); arXiv
 * 2508.15953: the profile is the optimum of an earthwork objective under linear design constraints), in place of the old repair loop.
 *
 * Heights z_i at nodes h metres apart. Minimise  1/2 sum w_i (z_i - t_i)^2 + 1/2 eps sum (z_{i+1} - 2 z_i + z_{i-1})^2  (t: the target,
 * the smoothed ground: a quadratic stand-in for cut and fill; eps: a small smoothness term, CHOSEN 1e-3 of the ground weight) subject to
 *  - lo_i <= z_i <= hi_i                    (holds: clearance over water and over the roads it crosses; fixed ends of ramps),
 *  - |z_{i+1} - z_i| <= G h                 (the class's maximum grade),
 *  - -h^2 / (100 Kc) <= D2 z <= h^2 / (100 Ks)  (AASHTO vertical curves: a parabola of length L over a grade change A % has K = L / A,
 *    so its second derivative is 1 / (100 K); K crest and sag in m per %).
 * Every constraint is linear, so this is a convex quadratic programme. Solved by a primal-dual interior-point method whose Newton system is pentadiagonal
 * (O(n) a step; about 20-40 steps). Then written as
 * elevation records: straight grades and parabolas, each joined to the last with the same height and grade, the parabola's curvature
 * clamped to the K limits (so K holds exactly in the stored road).
 */
public final class Profile {
    public static final double EPS = 1e-3, TOL = 0.05;   // TOL: the records' largest departure from the solved heights (m, CHOSEN)
    /** Result of a solve: heights, iterations, primal and dual residuals at the end. */
    public static final class Out { public double[] z; public int iters; public double prim, dual; public long ns; }

    /** The QP above, by a primal-dual interior-point method (Mehrotra predictor-corrector; Nocedal & Wright, Numerical Optimization,
     *  2nd ed., 16.6): each Newton step solves (H + G' W G) dx = r, pentadiagonal here, by banded Cholesky. lo may hold -infinity (no
     *  hold); fixed heights are given as heavy weights on the target instead (w FIX_W). x0: a start (may be null). */
    public static Out solve(double h, double[] t, double[] w, double[] lo, double G, double kc, double ks, double[] x0, int maxIt) {
        long t0 = System.nanoTime(); int n = t.length; Out o = new Out();
        if (n < 3) { o.z = new double[n]; for (int i = 0; i < n; i++) o.z[i] = Math.max(lo[i], t[i]); return o; }
        double c1 = 1 / (G * h), kmin = Math.min(kc, ks), c2 = 100 * kmin / (h * h), bs = kmin / ks, bc = kmin / kc; double eps = EPS * avgW(w);
        // rows: [0, nb): -z_i <= -lo_i (finite holds); then n-1 up and n-1 down grade rows; then n-2 sag and n-2 crest curvature rows
        int nb = 0; for (int i = 0; i < n; i++) if (!Double.isInfinite(lo[i])) nb++; int[] bi = new int[nb]; for (int i = 0, k = 0; i < n; i++) if (!Double.isInfinite(lo[i])) bi[k++] = i;
        int m1 = n - 1, m2 = n - 2, M = nb + 2 * m1 + 2 * m2; double[] hv = new double[M];
        for (int k = 0; k < nb; k++) hv[k] = -lo[bi[k]]; for (int k = 0; k < 2 * m1; k++) hv[nb + k] = 1; for (int k = 0; k < m2; k++) { hv[nb + 2 * m1 + k] = bs; hv[nb + 2 * m1 + m2 + k] = bc; }
        double[] x = new double[n]; for (int i = 0; i < n; i++) x[i] = x0 != null ? x0[i] : t[i];
        double[] gx = new double[M], s = new double[M], lam = new double[M]; gmul(x, bi, c1, c2, gx); for (int k = 0; k < M; k++) { s[k] = Math.max(hv[k] - gx[k], 1e-2); lam[k] = 1; }
        double[] rd = new double[n], rp = new double[M], rc = new double[M], d0 = new double[n], d1 = new double[n], d2 = new double[n], rhs = new double[n], dx = new double[n], tmpM = new double[M], gdx = new double[M], dl = new double[M], ds = new double[M], wv = new double[M];
        double[] dxa = new double[n], dla = new double[M], dsa = new double[M], xGood = x.clone(); int it;
        for (it = 1; it <= maxIt; it++) {
            gmul(x, bi, c1, c2, gx); double mu = 0; for (int k = 0; k < M; k++) { rp[k] = gx[k] + s[k] - hv[k]; mu += s[k] * lam[k]; } mu /= M;
            Arrays.fill(rd, 0); gtmul(lam, bi, n, c1, c2, rd); for (int i = 0; i < n; i++) rd[i] += w[i] * (x[i] - t[i]) + eps * d2td2(x, i);
            double pr = 0, du = 0; for (int k = 0; k < M; k++) pr = Math.max(pr, Math.abs(rp[k])); for (int i = 0; i < n; i++) du = Math.max(du, Math.abs(rd[i]));
            o.prim = pr; o.dual = du; if (DEBUG) System.out.printf("it %d pr %.3e du %.3e mu %.3e%n", it, pr, du, mu); if (Double.isNaN(pr + du + mu)) { System.arraycopy(xGood, 0, x, 0, n); break; } System.arraycopy(x, 0, xGood, 0, n); if (pr < 1e-6 && du < 1e-5 * Math.max(1, avgW(w)) && mu < 1e-8) break;
            for (int k = 0; k < M; k++) wv[k] = lam[k] / s[k];
            normal(n, w, eps, wv, bi, c1, c2, d0, d1, d2); factorBand(n, d0, d1, d2);
            // predictor (affine) step
            for (int k = 0; k < M; k++) rc[k] = s[k] * lam[k];
            newton(n, M, d0, d1, d2, rd, rp, rc, s, lam, wv, bi, c1, c2, rhs, dxa, gdx, dla, dsa, tmpM);
            double ap = Math.min(1, maxStep(s, dsa)), ad = Math.min(1, maxStep(lam, dla)); double muA = 0; for (int k = 0; k < M; k++) muA += (s[k] + ap * dsa[k]) * (lam[k] + ad * dla[k]); muA /= M; double sig = Math.pow(muA / Math.max(1e-300, mu), 3);
            // corrector
            for (int k = 0; k < M; k++) rc[k] = s[k] * lam[k] + dsa[k] * dla[k] - sig * mu;
            newton(n, M, d0, d1, d2, rd, rp, rc, s, lam, wv, bi, c1, c2, rhs, dx, gdx, dl, ds, tmpM);
            ap = Math.min(1, 0.995 * maxStep(s, ds)); ad = Math.min(1, 0.995 * maxStep(lam, dl));
            for (int i = 0; i < n; i++) x[i] += ap * dx[i]; for (int k = 0; k < M; k++) { s[k] += ap * ds[k]; lam[k] += ad * dl[k]; } }
        o.z = x; o.iters = Math.min(it, maxIt); o.ns = System.nanoTime() - t0; return o; }
    public static final double FIX_W = 1e6; public static boolean DEBUG = false;
    /** One Newton solve: (H + G'WG) dx = -rd - G'(W rp - rc / s); dl = W (G dx + rp) - rc / s; ds = -(rc + s dl) / lam. */
    static void newton(int n, int M, double[] L0, double[] L1, double[] L2, double[] rd, double[] rp, double[] rc, double[] s, double[] lam, double[] wv, int[] bi, double c1, double c2, double[] rhs, double[] dx, double[] gdx, double[] dl, double[] ds, double[] tmpM) {
        for (int k = 0; k < M; k++) tmpM[k] = wv[k] * rp[k] - rc[k] / s[k]; Arrays.fill(rhs, 0); gtmul(tmpM, bi, n, c1, c2, rhs); for (int i = 0; i < n; i++) rhs[i] = -rd[i] - rhs[i];
        solveBand(n, L0, L1, L2, rhs, dx); gmul(dx, bi, c1, c2, gdx);
        for (int k = 0; k < M; k++) { dl[k] = wv[k] * (gdx[k] + rp[k]) - rc[k] / s[k]; ds[k] = -(rc[k] + s[k] * dl[k]) / lam[k]; } }
    static double maxStep(double[] v, double[] dv) { double a = 1e30; for (int k = 0; k < v.length; k++) if (dv[k] < 0) a = Math.min(a, -v[k] / dv[k]); return a; }
    /** gx = G x (rows as in solve). */
    static void gmul(double[] x, int[] bi, double c1, double c2, double[] gx) { int n = x.length, nb = bi.length, m1 = n - 1, m2 = n - 2;
        for (int k = 0; k < nb; k++) gx[k] = -x[bi[k]]; for (int i = 0; i < m1; i++) { double v = c1 * (x[i + 1] - x[i]); gx[nb + i] = v; gx[nb + m1 + i] = -v; }
        for (int i = 0; i < m2; i++) { double v = c2 * (x[i] - 2 * x[i + 1] + x[i + 2]); gx[nb + 2 * m1 + i] = v; gx[nb + 2 * m1 + m2 + i] = -v; } }
    /** out += G' y. */
    static void gtmul(double[] y, int[] bi, int n, double c1, double c2, double[] out) { int nb = bi.length, m1 = n - 1, m2 = n - 2;
        for (int k = 0; k < nb; k++) out[bi[k]] -= y[k]; for (int i = 0; i < m1; i++) { double v = c1 * (y[nb + i] - y[nb + m1 + i]); out[i] -= v; out[i + 1] += v; }
        for (int i = 0; i < m2; i++) { double v = c2 * (y[nb + 2 * m1 + i] - y[nb + 2 * m1 + m2 + i]); out[i] += v; out[i + 1] -= 2 * v; out[i + 2] += v; } }
    /** H + G' W G, pentadiagonal (d0 diagonal, d1, d2 off-diagonals). */
    static void normal(int n, double[] w, double eps, double[] wv, int[] bi, double c1, double c2, double[] d0, double[] d1, double[] d2) { int nb = bi.length, m1 = n - 1, m2 = n - 2;
        Arrays.fill(d0, 0); Arrays.fill(d1, 0); Arrays.fill(d2, 0); for (int i = 0; i < n; i++) d0[i] = w[i];
        for (int k = 0; k < nb; k++) d0[bi[k]] += wv[k];
        for (int i = 0; i < m1; i++) { double g = c1 * c1 * (wv[nb + i] + wv[nb + m1 + i]); d0[i] += g; d0[i + 1] += g; d1[i] -= g; }
        for (int i = 0; i < m2; i++) { double k = c2 * c2 * (wv[nb + 2 * m1 + i] + wv[nb + 2 * m1 + m2 + i]) + eps; d0[i] += k; d0[i + 1] += 4 * k; d0[i + 2] += k; d1[i] -= 2 * k; d1[i + 1] -= 2 * k; d2[i] += k; } }
    /** In place: banded Cholesky of the pentadiagonal (d0, d1, d2) to L (diagonal d0, sub-diagonals d1, d2). */
    static void factorBand(int n, double[] d0, double[] d1, double[] d2) {
        for (int i = 0; i < n; i++) { double sv = d0[i]; if (i >= 1) sv -= d1[i - 1] * d1[i - 1]; if (i >= 2) sv -= d2[i - 2] * d2[i - 2]; d0[i] = Math.sqrt(Math.max(1e-300, sv));
            if (i + 1 < n) { double v = d1[i]; if (i >= 1) v -= d1[i - 1] * d2[i - 1]; d1[i] = v / d0[i]; } if (i + 2 < n) d2[i] = d2[i] / d0[i]; } }
    static void solveBand(int n, double[] L0, double[] L1, double[] L2, double[] b, double[] x) {
        for (int i = 0; i < n; i++) { double sv = b[i]; if (i >= 1) sv -= L1[i - 1] * x[i - 1]; if (i >= 2) sv -= L2[i - 2] * x[i - 2]; x[i] = sv / L0[i]; }
        for (int i = n - 1; i >= 0; i--) { double sv = x[i]; if (i + 1 < n) sv -= L1[i] * x[i + 1]; if (i + 2 < n) sv -= L2[i] * x[i + 2]; x[i] = sv / L0[i]; } }
    static double avgW(double[] P) { double a = 0; int c = 0; for (double v : P) if (v < FIX_W / 10) { a += v; c++; } return Math.max(1e-6, c == 0 ? 1 : a / c); }
    /** (D2' D2 x)_i. */
    static double d2td2(double[] x, int i) { int n = x.length; double sv = 0; for (int r = i - 2; r <= i; r++) { if (r < 0 || r + 2 >= n) continue; double v = x[r] - 2 * x[r + 1] + x[r + 2]; sv += v * (i == r + 1 ? -2 : 1); } return sv; }

    // ------------------------------------------------------------ elevation records from solved heights
    /** Elevation records through heights z at nodes s0 + i h: the quadratic spline with knots half-way between nodes, whose curvature on
     *  the piece round node i is the node's second difference (z[i-1] - 2 z[i] + z[i+1]) / h^2 (so the records keep exactly the K the
     *  solve kept), grade continuous; the first and last half pieces straight. It passes within curvature h^2 / 8 of each node (mm).
     *  Neighbouring pieces whose curvature differs by under 1e-9 per m are one record (straight grades and curves held at the K limit come
     *  out as single records); each record starts on the spline (a join is off by under 1 cm). Returns the largest departure from z. */
    public static double records(double s0, double h, double[] z, List<Alignment.Elev> out) {
        int n = z.length; if (n == 0) return 0; if (n == 1) { out.add(new Alignment.Elev(s0, z[0], 0, 0, 0)); return 0; }
        int K = n + 1; double[] ks = new double[K], kk = new double[K], kh = new double[K], kg = new double[K];   // pieces: start s, curvature, start height, start grade
        ks[0] = s0; kk[0] = 0; kh[0] = z[0]; kg[0] = (z[1] - z[0]) / h;
        for (int i = 1; i < n - 1; i++) { ks[i] = s0 + (i - 0.5) * h; kk[i] = (z[i - 1] - 2 * z[i] + z[i + 1]) / (h * h); }
        ks[n - 1] = s0 + (n - 1.5) * h; kk[n - 1] = 0; ks[n] = s0 + (n - 1) * h;
        for (int i = 1; i < n; i++) { double L = ks[i] - ks[i - 1]; kh[i] = kh[i - 1] + kg[i - 1] * L + kk[i - 1] * L * L / 2; kg[i] = kg[i - 1] + kk[i - 1] * L; }
        double dev = 0; for (int i = 0; i < n; i++) { int p = i == 0 ? 0 : i; double u = s0 + i * h - ks[p]; dev = Math.max(dev, Math.abs(kh[p] + kg[p] * u + kk[p] * u * u / 2 - z[i])); }
        for (int i = 0; i < n; ) { int j = i + 1; double sum = kk[i]; while (j < n && Math.abs(kk[j] - kk[i]) < 1e-9) { sum += kk[j]; j++; } double c = sum / (j - i) / 2; if (j - i == 1) c = kk[i] / 2;
            out.add(new Alignment.Elev(ks[i], kh[i], kg[i], c, 0)); i = j; }
        Alignment.Elev last = out.get(out.size() - 1); double L = ks[n] - last.s; out.add(new Alignment.Elev(ks[n], last.z(L), last.g(L), 0, 0)); return dev; }
    /** The least K (m per %) of the elevation records' curves (crest, sag) and the steepest grade at their ends. */
    public static double[] check(List<Alignment.Elev> el) { double kc = Double.MAX_VALUE, ks = Double.MAX_VALUE, g = 0;
        for (int i = 0; i < el.size(); i++) { Alignment.Elev e = el.get(i); double L = i + 1 < el.size() ? el.get(i + 1).s - e.s : 0; g = Math.max(g, Math.max(Math.abs(e.g(0)), Math.abs(e.g(L))));
            if (e.c < -1e-12) kc = Math.min(kc, 1 / (100 * -2 * e.c)); if (e.c > 1e-12) ks = Math.min(ks, 1 / (100 * 2 * e.c)); } return new double[]{kc, ks, g}; }

    // ------------------------------------------------------------ ramp length budget
    /** The shortest profile that changes height by dz from grade g0 to grade g1 with grades within G and AASHTO curves (K crest kc, sag ks,
     *  m per %): bang-bang, the grade bent at the K limit to its peak, held, and bent back (time-optimal control of z'' bounded). */
    public static double minLength(double dz, double g0, double g1, double G, double kc, double ks) { double lo = 0, hi = 20000; if (reach(hi, g0, g1, G, kc, ks, dz) == false) return Double.POSITIVE_INFINITY;
        for (int it = 0; it < 60; it++) { double md = (lo + hi) / 2; if (reach(md, g0, g1, G, kc, ks, dz)) hi = md; else lo = md; } return hi; }
    static boolean reach(double L, double g0, double g1, double G, double kc, double ks, double dz) { double as = 1 / (100 * ks), ac = 1 / (100 * kc);
        double up = most(L, g0, g1, G, as, ac), down = -most(L, -g0, -g1, G, ac, as); return !Double.isNaN(up) && !Double.isNaN(down) && dz <= up + 1e-9 && dz >= down - 1e-9; }
    /** The most height over L: up at curvature a1 to the peak grade, held, down at a2 (NaN if the end grade cannot be reached). */
    static double most(double L, double g0, double g1, double G, double a1, double a2) {
        double lo = Math.max(g0, g1), hi = G; if (lo > G + 1e-12) return Double.NaN; if ((lo - g0) / a1 + (lo - g1) / a2 > L) { return Double.NaN; }
        double gp = hi; if ((gp - g0) / a1 + (gp - g1) / a2 > L) { double a = lo, b = hi; for (int it = 0; it < 60; it++) { double md = (a + b) / 2; if ((md - g0) / a1 + (md - g1) / a2 > L) b = md; else a = md; } gp = a; }
        double l1 = (gp - g0) / a1, l3 = (gp - g1) / a2, l2 = L - l1 - l3; return (g0 + gp) / 2 * l1 + gp * l2 + (gp + g1) / 2 * l3; }
}
