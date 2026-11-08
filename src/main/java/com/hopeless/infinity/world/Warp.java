package com.hopeless.infinity.world;

import com.hopeless.infinity.planet.PlanetWorld;
import com.hopeless.infinity.sky.Ephemeris;

/**
 * 0.17.0 TIME WARP IN SPACE (owner, 2026-11-03: "Continue", after 0.16.0 put the moon 389,500 km away, hours of real flight). The clock and every
 * craft in space move on k times faster (the levels: Kerbal Space Program's "on rails" warp, 1 to 100,000 x, STATED as the model; the levels CHOSEN
 * as powers of ten). Pure (tools/NearTest).
 *  - Only coasting: no thrust and no throttle (KSP's rule: no forces under rails warp), and for the X-15 only above the Karman line (100 km, the
 *    FAI edge of space: no air to fly in). Thrust or the brake key drops it to 1 x.
 *  - The path: velocity Verlet (kick-drift-kick, symplectic: the orbit's energy does not drift) in the planet's non-turning frame (the turning
 *    frame's Coriolis term spoils a symplectic step), the planet's gravity plus the moon's pull (its pull on the planet taken off), the moon's
 *    place from the real orbit at each sub-step. Sub-steps: at most DT_FRAC of the shortest local orbit period (2 pi sqrt(r^3 / GM), r to each
 *    body's centre), at most MAX_SUB a tick: so the warp is held down near a body (measured in tools/NearTest: a 100 km moon orbit holds).
 *  - Near the ground: one tick may cover at most APPROACH of the height left over the world top, so a craft falling in slows to 1 x before it
 *    reaches the ground (KSP drops the warp near the surface for the same reason).
 */
public final class Warp {
    private Warp() {}
    public static final int[] LEVELS = {1, 10, 100, 1000, 10000, 100000};
    public static final double EDGE_ATM = 3.2e-7;   // 0.18.0 the edge of space on any body: the air's pressure at Earth's 100 km Karman line (US Standard Atmosphere 1976: 0.032 Pa)
    public static double DT_FRAC = 1.0 / 400; public static final double APPROACH = 0.1, KARMAN_M = 100000; public static final int MAX_SUB = 2000, HARD_SUB = 20000;
    public static long substeps, limitedNear, limitedGround, stops;

    /** The highest level index allowed for a craft at p (planet space), sinking toward the ground below at sink (m/s; climbing: 0) at height up over the world top (m). */
    public static int maxLevel(PlanetWorld home, Ephemeris e, double t, double[] p, double sink, double up) { double rel = Math.max(0, sink);
        double T = localPeriod(home, e, t, p, null);
        double kOrbit = MAX_SUB * DT_FRAC * T / 0.05, kGround = up <= 0 ? 1 : APPROACH * up / Math.max(1e-6, rel * 0.05); int lv = 0;
        for (int i = 1; i < LEVELS.length; i++) if (LEVELS[i] <= kOrbit && LEVELS[i] <= kGround) lv = i;
        if (lv > 0 && LEVELS[lv] > kOrbit * 0.999) limitedNear++; return lv; }
    /** The shortest local orbit period at p over every body (2 pi sqrt(r^3 / GM), r to each one's centre; the home planet's included). cs: centres (null: worked out). */
    static double localPeriod(PlanetWorld home, Ephemeris e, double t, double[] p, double[][] cs) {
        double gmP = home.gravity * home.cube.R * home.cube.R, rP = Near.len(p), T = 2 * Math.PI * Math.sqrt(rP * rP * rP / gmP); if (e == null || home.bodies.size() < 2) return T;
        Near.Sys sy = Near.Sys.of(home); if (cs == null) cs = sy.centres(e, t);
        for (int k = 0; k < sy.ids.length; k++) { if (sy.ids[k] == 0) continue; double r = Near.len(Near.sub(p, cs[k])); T = Math.min(T, 2 * Math.PI * Math.sqrt(r * r * r / sy.gm[k])); }
        return T; }
    /** Which limit holds (for the HUD and the log). */
    public static String why(PlanetWorld home, Ephemeris e, double t, double[] p, double sink, double up, int asked) { int m = maxLevel(home, e, t, p, sink, up); double rel = Math.max(0, sink); if (m >= asked) return "";
        double kGround = up <= 0 ? 1 : APPROACH * up / Math.max(1e-6, rel * 0.05); return LEVELS[asked] > kGround ? "near the ground" : "close to a body"; }

    /** The moon's centre in planet space (the turning frame) at t. */
    public static double[] moonCentre(PlanetWorld home, Ephemeris e, double t) { double[][] pa = e.planetAxes(t); double[] hp = e.planetCentre(t), mc = e.moonCentre(0, t), rel = {(mc[0] - hp[0]) * 1000, (mc[1] - hp[1]) * 1000, (mc[2] - hp[2]) * 1000};
        return new double[]{Near.dot(rel, pa[0]), Near.dot(rel, pa[1]), Near.dot(rel, pa[2])}; }

    /** k ticks of coasting from t: p, v (planet space, the turning frame) moved on k * 0.05 s. */
    public static int coast(PlanetWorld home, Ephemeris e, double t, double[] p, double[] v, double omega, int k) { return coast(home, e, t, p, v, omega, k, 0, null, Double.NaN); }
    /** nForce > 0: that many sub-steps (tools/NearTest's fine reference). */
    public static int coast(PlanetWorld home, Ephemeris e, double t, double[] p, double[] v, double omega, int k, int nForce) { return coast(home, e, t, p, v, omega, k, nForce, null, Double.NaN); }
    /**
     * extra: a steady push (m/s2) in the turning frame's axes over the span (thrust, the X-15's air forces; only at 1 x). stopUp (m over a body's
     * radius; NaN: none): if the path comes that low over either body inside the span, the coast stops there and the craft rides the rest of the
     * span fixed to that body (its place and motion over the ground kept: so one long warp tick never jumps through a moon or past the landing).
     * Returns the body hit (0 the planet, 1 the moon), or -1.
     */
    public static int coast(PlanetWorld home, Ephemeris e, double t, double[] p, double[] v, double omega, int k, int nForce, double[] extra, double stopUp) {
        double span = k * 0.05, gmP = home.gravity * home.cube.R * home.cube.R; boolean many = e != null && home.bodies.size() >= 2; Near.Sys sy = many ? Near.Sys.of(home) : null;
        double[][] pa0 = many ? e.planetAxes(t) : null;   // the non-turning frame: the planet's axes at t
        double[] P = p.clone(), V = {v[0] - omega * p[1], v[1] + omega * p[0], v[2]}, a = new double[3], tmp = new double[3]; double[] dmin = new double[1]; int[] near = new int[1];   // the turning frame at t -> the non-turning one: V = v + w x p
        acc(home, e, sy, pa0, t, 0, P, gmP, omega, extra, a, tmp, dmin, near, stopUp);
        int hit = -1; double used = 0, vClose = 0, tl = dmin[0]; int i = 0;
        while (used < span - 1e-9) {
            double dt; if (nForce > 0) dt = span / nForce; else { dt = Math.max(DT_FRAC * tl, (span - used) / Math.max(1, HARD_SUB - i)); if (i < SpaceMath.SUB) dt = Math.min(dt, span / SpaceMath.SUB); }
            if (nForce <= 0 && !Double.isNaN(stopUp) && vClose > 0) dt = Math.min(dt, Math.max(0.05, 0.25 * hGap / vClose));   // closing on a stop height: shorter steps
            dt = Math.min(dt, span - used); double[] P0 = P.clone(), V0 = V.clone(); double h0 = hGap;
            for (int j = 0; j < 3; j++) { V[j] += a[j] * dt / 2; P[j] += V[j] * dt; }
            acc(home, e, sy, pa0, t, used + dt, P, gmP, omega, extra, a, tmp, dmin, near, stopUp); tl = dmin[0]; for (int j = 0; j < 3; j++) V[j] += a[j] * dt / 2; i++;
            if (!Double.isNaN(stopUp) && used + dt < span - 1e-9 && hGap < 0) { hit = near[0]; double f = Math.max(0, Math.min(1, h0 / Math.max(1e-9, h0 - hGap)));
                for (int j = 0; j < 3; j++) { P[j] = P0[j] + (P[j] - P0[j]) * f; V[j] = V0[j] + (V[j] - V0[j]) * f; } used += dt * f; break; }   // back to the crossing (linear within the sub-step)
            if (!Double.isNaN(stopUp)) vClose = (h0 - hGap) / dt;
            used += dt; }
        substeps += i;
        double th = omega * used, c = Math.cos(th), s = Math.sin(th); double[] pr = {c * P[0] + s * P[1], -s * P[0] + c * P[1], P[2]}, Vr = {c * V[0] + s * V[1], -s * V[0] + c * V[1], V[2]};
        p[0] = pr[0]; p[1] = pr[1]; p[2] = pr[2]; v[0] = Vr[0] + omega * pr[1]; v[1] = Vr[1] - omega * pr[0]; v[2] = Vr[2];   // back to the turning frame at t + used: turned by -w used about z, and v = V - w x P
        if (hit > 0 && used < span) {   // ride the rest of the span with that body: the same place and motion in its own frame
            Near a1 = Near.body(home, e, t + used, hit), a2 = Near.body(home, e, t + span, hit); double[] b = a1.toBody(p), u1 = a1.pointVel(home, e, b), bv = a1.dirToBody(Near.sub(v, u1));
            double[] p2 = a2.toPlanet(b), v2 = a2.dirToPlanet(bv), u2 = a2.pointVel(home, e, b); for (int j = 0; j < 3; j++) { p[j] = p2[j]; v[j] = v2[j] + u2[j]; } stops++; }
        else if (hit == 0) stops++;   // the planet's frame is this frame: nothing to carry
        return hit; }
    /** The height left over the nearest stop surface (m; set by acc). Per thread: coast is called from the server thread and tests. */
    static double hGap; public static int lastSystem;
    /** The acceleration at the non-turning P, s seconds after t: the home planet, every other body and the star (their places on the axes pa0);
     *  dmin: the shortest local orbit period there; near / hGap: the body whose stop height is closest and the height over it. */
    static void acc(PlanetWorld home, Ephemeris e, Near.Sys sy, double[][] pa0, double t, double s, double[] P, double gmP, double omega, double[] extra, double[] a, double[] tmp, double[] dmin, int[] near, double stopUp) {
        double r = Near.len(P), T = 2 * Math.PI * Math.sqrt(r * r * r / gmP), gap = r - home.cube.R - (Double.isNaN(stopUp) ? 0 : stopUp); int nb = 0;
        if (sy == null) { double g = -gmP / (r * r * r); a[0] = g * P[0]; a[1] = g * P[1]; a[2] = g * P[2]; }
        if (extra != null) { double th = omega * s, c = Math.cos(th), sn = Math.sin(th); tmp[0] = c * extra[0] - sn * extra[1]; tmp[1] = sn * extra[0] + c * extra[1]; tmp[2] = extra[2]; } else tmp[0] = tmp[1] = tmp[2] = 0;
        double ex0 = tmp[0], ex1 = tmp[1], ex2 = tmp[2];
        if (sy == null) { a[0] += ex0; a[1] += ex1; a[2] += ex2; }
        if (sy != null) { double[][] cs = sy.centres(e, t + s, pa0); lastSystem = sy.accel(e, t + s, pa0, cs, P, a); a[0] += ex0; a[1] += ex1; a[2] += ex2;
            for (int k = 0; k < sy.ids.length; k++) { if (sy.ids[k] == 0) continue; double d = Near.len(Near.sub(P, cs[k])); T = Math.min(T, 2 * Math.PI * Math.sqrt(d * d * d / sy.gm[k])); double gp = d - sy.radius[k] - (Double.isNaN(stopUp) ? 0 : stopUp); if (gp < gap) { gap = gp; nb = sy.ids[k]; } } }
        dmin[0] = T; near[0] = nb; hGap = gap; }
}
