import com.hopeless.infinity.planet.*; import com.hopeless.infinity.sky.Ephemeris; import com.hopeless.infinity.world.*;
/** NearTest: 0.16.0 flight between the home planet and its moon (world/Near + SpaceMath, as world/Space runs them). Saved tool. */
public class NearTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  static double dot3(double[] a, double[] b) { return a[0]*b[0]+a[1]*b[1]+a[2]*b[2]; }
  static double[] sub(double[] a, double[] b) { return new double[]{a[0]-b[0], a[1]-b[1], a[2]-b[2]}; } static double len(double[] a) { return Math.sqrt(a[0]*a[0]+a[1]*a[1]+a[2]*a[2]); }
  public static void main(String[] x) { if (x.length > 0) Warp.DT_FRAC = 1.0 / Double.parseDouble(x[0]);
    PlanetWorld w = new PlanetWorld(1), m = w.moon(); Ephemeris e = new Ephemeris(w); double t0 = 123456, om = SpaceMath.omega(w), gmP = w.gravity * w.cube.R * w.cube.R, gmM = m.gravity * m.cube.R * m.cube.R;
    Near n = Near.moon(w, e, t0); double dist = len(n.c);
    System.out.printf("moon centre %.0f km from the planet's, SOI %.0f km, moon R %.0f km%n", dist / 1000, Near.soi(w) / 1000, m.cube.R / 1000);
    ok(Math.abs(Near.soi(w) / 1000 - 60000) < 15000, String.format("sphere of influence %.0f km (a (m/M)^0.4; Earth's Moon: 66,100 km)", Near.soi(w) / 1000));
    double[] b = {1234567, -2345678, 345678}; double rt = len(sub(n.toBody(n.toPlanet(b)), b)); ok(rt < 1e-6, String.format("body <-> planet space round trip: %.2e m", rt));
    double orth = 0; for (int i = 0; i < 3; i++) for (int k = 0; k < 3; k++) orth = Math.max(orth, Math.abs(n.ax[i][0]*n.ax[k][0]+n.ax[i][1]*n.ax[k][1]+n.ax[i][2]*n.ax[k][2] - (i == k ? 1 : 0))); ok(orth < 1e-9, String.format("moon axes orthonormal: %.1e", orth));
    ok(Near.of(w, e, t0, n.toPlanet(new double[]{0, m.cube.R + 5000, 0})).body == 1 && Near.of(w, e, t0, new double[]{w.cube.R + 5000, 0, 0}).body == 0, "the body below: the moon next to it, the planet next to the planet");
    // the moon's centre must move in planet space as space's own physics says (else a craft at rest by the moon would drift): a = -(GMp+GMm) c/r^3 - 2 w x v - w x (w x c)
    double h = 20; double[] c0 = Near.moon(w, e, t0 - h).c, c1 = n.c, c2 = Near.moon(w, e, t0 + h).c, v = new double[3], aFd = new double[3], aPh = new double[3];
    for (int k = 0; k < 3; k++) { v[k] = (c2[k] - c0[k]) / (2 * h); aFd[k] = (c2[k] - 2 * c1[k] + c0[k]) / (h * h); }
    double g = -(gmP + gmM) / Math.pow(dist, 3); aPh[0] = g * c1[0] + 2 * om * v[1] + om * om * c1[0]; aPh[1] = g * c1[1] - 2 * om * v[0] + om * om * c1[1]; aPh[2] = g * c1[2];
    double err = len(sub(aFd, aPh)); System.out.printf("moon centre: speed in planet space %.0f m/s, acceleration %.4f m/s2 (ephemeris) vs %.4f (physics)%n", len(v), len(aFd), len(aPh));
    ok(err < 2e-4 * len(aPh) + 1e-5, String.format("the ephemeris and space's physics agree on the moon's path: %.2e m/s2 apart (gravity there %.2e)", err, gmM / Math.pow(m.cube.R + 100000, 2)));
    double[] pull = Near.moonPull(w, n, n.toPlanet(new double[]{0, m.cube.R, 0})); double gS = len(pull); ok(Math.abs(gS - m.gravity) < 0.01, String.format("moon pull at its surface %.3f m/s2 (its gravity %.3f)", gS, m.gravity));
    // a low lunar orbit (100 km, circular) flown one revolution with Space.tick's scheme: moon pull once a tick at the tick's middle, SpaceMath.step
    double rO = m.cube.R + 100000, vO = Math.sqrt(gmM / rO), T = 2 * Math.PI * rO / vO; double[] p = n.toPlanet(new double[]{0, rO, 0}), dirT = n.dirToPlanet(new double[]{1, 0, 0}), vc = new double[3];
    for (int k = 0; k < 3; k++) vc[k] = v[k] + dirT[k] * vO; vc[0] += om * (p[1] - c1[1]); vc[1] -= om * (p[0] - c1[0]);   // inertial orbit velocity -> the turning frame: less w x (p - c) (the centre's own term is in v)
    double lo = 1e18, hi = -1e18, t = t0; int ticks = (int) (T / 0.05); double[] vv = vc.clone(); long ns = System.nanoTime();
    for (int i = 0; i < ticks; i++) { Near nm = Near.moon(w, e, t + 0.025); double[] a = Near.moonPull(w, nm, p); SpaceMath.step(p, vv, gmP, om, a); t += 0.05;
      if (i % 20 == 0) { double r = len(Near.moon(w, e, t).toBody(p)); lo = Math.min(lo, r); hi = Math.max(hi, r); } }
    double[] fin = Near.moon(w, e, t).toBody(p); double us = (System.nanoTime() - ns) / 1e3 / ticks;
    System.out.printf("low orbit: period %.1f min, height %.2f .. %.2f km over one revolution, end %.2f km; %.1f us a tick%n", T / 60, (lo - m.cube.R) / 1000, (hi - m.cube.R) / 1000, (len(fin) - m.cube.R) / 1000, us);
    ok(hi - lo < 3000, String.format("a 100 km circular orbit round the moon holds: height spread %.0f m over one %.0f min revolution", hi - lo, T / 60));
    // the hand-over: the body below switches at the SOI edge; a point there in either body's frame maps back to the same planet-space point
    double[] edge = n.toPlanet(new double[]{0, Near.soi(w) * 0.999, 0}); Near in = Near.of(w, e, t0, edge); double[] edge2 = n.toPlanet(new double[]{0, Near.soi(w) * 1.001, 0}); Near out = Near.of(w, e, t0, edge2);
    ok(in.body == 1 && out.body == 0 && len(sub(out.toPlanet(out.toBody(edge2)), edge2)) < 1e-6, "inside the SOI the moon is below, just outside the planet; the point is the same in both");
    // a standing point on the moon's ground: its planet-space speed is the centre's speed plus the locked spin (2 pi R / 22.9 d = few m/s)
    double[] gp = {0, m.cube.R, 0}, pv = n.pointVel(w, e, gp), pc = n.pointVel(w, e, new double[3]); double spin = len(sub(pv, pc)) - om * len(sub(n.toPlanet(gp), n.c)) * 0; System.out.printf("ground point vs centre: %.2f m/s apart (in the turning frame: the planet's turn x R + the moon's own spin)%n", len(sub(pv, pc)));
    ok(len(sub(pv, pc)) < om * m.cube.R * 1.2 + 10, "a standing point moves with the moon (no sudden jump on landing)");

    // ---- 0.17.0 time warp (world/Warp) ----
    { double[] p1 = n.toPlanet(new double[]{0, rO, 0}), v1 = vc.clone(), p2 = p1.clone(), v2 = v1.clone(); double tt = t0;
      for (int i = 0; i < 12000; i++) { double[] a = Near.moonPull(w, Near.moon(w, e, tt + 0.025), p1); SpaceMath.step(p1, v1, gmP, om, a); tt += 0.05; }
      double[] p5 = p2.clone(), v5 = v2.clone(); for (int i = 0; i < 120; i++) { Warp.coast(w, e, t0 + i * 5.0, p2, v2, om, 100); Warp.coast(w, e, t0 + i * 5.0, p5, v5, om, 100, 2000); }   // the warp's own steps, and a fine reference (0.0025 s steps)
      double d = len(sub(p1, p5)), dw = len(sub(p2, p5)); System.out.printf("600 s round the moon against a fine reference: 1 x path %.1f m off, warp path %.1f m off%n", d, dw);
      ok(dw < 50 && d < 500, String.format("warp's path within %.1f m of the fine reference after 600 s round the moon (the 1 x path: %.1f m)", dw, d)); }
    { int lv = Warp.maxLevel(w, e, t0, n.toPlanet(new double[]{0, rO, 0}), 0, 94400); System.out.printf("warp allowed in a 100 km moon orbit: %d x%n", Warp.LEVELS[lv]);
      double[] p3 = n.toPlanet(new double[]{0, rO, 0}), v3 = vc.clone(); double tt = t0, lo2 = 1e18, hi2 = -1e18; int k = Warp.LEVELS[lv]; long n0 = System.nanoTime(); int tk = 0;
      while (tt < t0 + 10 * T) { Warp.coast(w, e, tt, p3, v3, om, k); tt += k * 0.05; tk++; double r = len(Near.moon(w, e, tt).toBody(p3)); lo2 = Math.min(lo2, r); hi2 = Math.max(hi2, r); }
      double ms = (System.nanoTime() - n0) / 1e6 / tk;
      ok(lv >= 2 && hi2 - lo2 < 3000, String.format("ten revolutions of the 100 km moon orbit at %d x: height %.2f .. %.2f km; %.2f ms a tick (budget 50)", k, (lo2 - m.cube.R) / 1000, (hi2 - m.cube.R) / 1000, ms));
      ok(ms < 10, String.format("warp costs %.2f ms a tick at most sub-steps", ms)); }
    ok(Warp.maxLevel(w, e, t0, new double[]{w.cube.R + 3000, 0, 0}, 4000, 2000) == 0, "falling toward the world top fast: warp held at 1 x");
    // a transfer to the moon (Hohmann-like: from a 300 km orbit, apogee at the moon's distance, aimed where the moon will be), flown at the highest warp allowed
    { double r0 = w.cube.R + 300000, Th = Math.PI * Math.sqrt(Math.pow((r0 + dist) / 2, 3) / gmP), ts = t0, stopUp = 5631 - Terrain.SEA + 2000;
      double[] M = Warp.moonCentre(w, e, ts + Th), M2 = Warp.moonCentre(w, e, ts + Th + 10); double a1 = om * Th, a2 = om * (Th + 10);   // the moon then, in the non-turning frame of ts
      double[] Mi = {Math.cos(a1) * M[0] - Math.sin(a1) * M[1], Math.sin(a1) * M[0] + Math.cos(a1) * M[1], M[2]}, Mi2 = {Math.cos(a2) * M2[0] - Math.sin(a2) * M2[1], Math.sin(a2) * M2[0] + Math.cos(a2) * M2[1], M2[2]};
      double[] mv = sub(Mi2, Mi), mh = {Mi[0] / len(Mi), Mi[1] / len(Mi), Mi[2] / len(Mi)}; double[] nrm = {mh[1] * mv[2] - mh[2] * mv[1], mh[2] * mv[0] - mh[0] * mv[2], mh[0] * mv[1] - mh[1] * mv[0]}; double nl = len(nrm); for (int k = 0; k < 3; k++) nrm[k] /= nl;
      double[] P0 = {-mh[0] * r0, -mh[1] * r0, -mh[2] * r0}, dir = {-(nrm[1] * mh[2] - nrm[2] * mh[1]), -(nrm[2] * mh[0] - nrm[0] * mh[2]), -(nrm[0] * mh[1] - nrm[1] * mh[0])};   // prograde: n x r
      for (double miss : new double[]{0, 0.003}) {   // aimed at the moon's centre (a hit), then a little long (a pass)
        double vp = Math.sqrt(gmP * (2 / r0 - 2 / (r0 + len(Mi)))) * (1 + miss); double[] V0 = {dir[0] * vp + om * P0[1], dir[1] * vp - om * P0[0], dir[2] * vp};
        double[] p4 = P0.clone(), v4 = V0.clone(); double tt = ts; long tks = 0; int[] used = new int[Warp.LEVELS.length]; int hit = -1; double best = 1e18;
        while (tt < ts + 1.4 * Th && hit < 0) { Near nb = Near.of(w, e, tt, p4); double[] bp = nb.toBody(p4), u = nb.pointVel(w, e, bp), rv = nb.dirToBody(sub(v4, u)); double br = len(bp), sink = -(rv[0] * bp[0] + rv[1] * bp[1] + rv[2] * bp[2]) / br;
          int lv = Warp.maxLevel(w, e, tt, p4, sink, br - nb.w.cube.R - (5631 - Terrain.SEA)); int k = Warp.LEVELS[lv]; used[lv]++; hit = Warp.coast(w, e, tt, p4, v4, om, k, 0, null, stopUp); tt += k * 0.05; tks++;
          best = Math.min(best, len(sub(p4, Warp.moonCentre(w, e, tt)))); }
        double[] p6 = P0.clone(), v6 = V0.clone(); double t6 = ts; int hit6 = -1; while (t6 < ts + 1.4 * Th && hit6 < 0) { hit6 = Warp.coast(w, e, t6, p6, v6, om, 200, 40, null, stopUp); t6 += 10; }   // fine reference: 0.25 s steps (0.05 s gave the same arrival to 1 m/s, 5 x slower)
        System.out.printf("transfer aimed %+.1f %%: warp run %s after %.1f h (%d ticks = %.2f min real time; ticks at each level %s), closest seen %.0f km; fine reference %s after %.1f h%n", miss * 100,
          hit < 0 ? "no stop" : hit == 1 ? "stopped over the moon" : "stopped over the planet", (tt - ts) / 3600, tks, tks / 1200.0, java.util.Arrays.toString(used), best / 1000, hit6 < 0 ? "no stop" : hit6 == 1 ? "stopped over the moon" : "stopped over the planet", (t6 - ts) / 3600);
        double sW = 0, s6 = 0; if (hit == 1) { Near q = Near.moon(w, e, tt); sW = len(sub(v4, q.pointVel(w, e, q.toBody(p4)))); } if (hit6 == 1) { Near q = Near.moon(w, e, t6); s6 = len(sub(v6, q.pointVel(w, e, q.toBody(p6)))); }
        ok(hit == hit6 && Math.abs(sW - s6) < 0.02 * Math.max(1, s6), String.format("aimed %+.1f %%: the warp run ends as the fine one does (%d vs %d; arrival speed %.0f vs %.0f m/s)", miss * 100, hit, hit6, sW, s6));
        if (miss == 0) ok(hit == 1 && tks / 1200.0 < 10, String.format("the trip to the moon takes %.2f min of real time with warp (%.1f h without)", tks / 1200.0, (tt - ts) / 3600));
        if (hit == 1) { Near mn = Near.moon(w, e, tt); double[] bp = mn.toBody(p4); System.out.printf("  arrival: %.1f km over the moon, falling at %.0f m/s over its ground%n", (len(bp) - m.cube.R) / 1000, len(sub(v4, mn.pointVel(w, e, bp)))); } } }

    // ---- 0.18.0 to another planet: a Hohmann transfer from the home planet's orbit down to planet 6 (0.53 AU), flown at the warp allowed ----
    { Near.Sys sy = Near.Sys.of(w); int tgt = 6, tid = -1; for (var r : w.bodies) if (r.planet() == tgt && !r.isMoon()) tid = r.id();
      var orb = new com.hopeless.infinity.galaxy.Orbits(w.system); double gmS = orb.gmStar, a1 = w.planet.au * com.hopeless.infinity.galaxy.Orbits.AU_KM, a2 = w.system.planets.get(tgt).au * com.hopeless.infinity.galaxy.Orbits.AU_KM;   // km, km3/s2
      double ts = t0, Th = Math.PI * Math.sqrt(Math.pow((a1 + a2) / 2, 3) / gmS); double[] X = null, X2 = null;
      for (int it = 0; it < 5; it++) { X = com.hopeless.infinity.galaxy.Orbits.position(orb.planet[tgt], ts + Th + e.epochS); X2 = com.hopeless.infinity.galaxy.Orbits.position(orb.planet[tgt], ts + Th + 60 + e.epochS); a2 = len(X); Th = Math.PI * Math.sqrt(Math.pow((a1 + a2) / 2, 3) / gmS); }   // aim at where it really is then (its orbit is eccentric)
      double xl = len(X); double[] xh = {X[0] / xl, X[1] / xl, X[2] / xl}, xv = sub(X2, X), nrm = {xh[1] * xv[2] - xh[2] * xv[1], xh[2] * xv[0] - xh[0] * xv[2], xh[0] * xv[1] - xh[1] * xv[0]}; double nl = len(nrm); for (int k = 0; k < 3; k++) nrm[k] /= nl;
      double[] S0 = {-xh[0] * a1, -xh[1] * a1, -xh[2] * a1}, dir = {nrm[1] * S0[2] - nrm[2] * S0[1], nrm[2] * S0[0] - nrm[0] * S0[2], nrm[0] * S0[1] - nrm[1] * S0[0]}; double dl = len(dir); double va = Math.sqrt(gmS * (2 / a1 - 2 / (a1 + a2)));
      double[] Vs = {dir[0] / dl * va, dir[1] / dl * va, dir[2] / dl * va};   // km/s at aphelion, S frame
      double[] hp = e.planetCentre(ts), hp2 = e.planetCentre(ts + 1); double[][] pa = e.planetAxes(ts); double[] rel = sub(S0, hp), rv = {Vs[0] - (hp2[0] - hp[0]), Vs[1] - (hp2[1] - hp[1]), Vs[2] - (hp2[2] - hp[2])};
      double[] P = {dot3(rel, pa[0]) * 1000, dot3(rel, pa[1]) * 1000, dot3(rel, pa[2]) * 1000}, Vn = {dot3(rv, pa[0]) * 1000, dot3(rv, pa[1]) * 1000, dot3(rv, pa[2]) * 1000};
      double[] V = {Vn[0] + om * P[1], Vn[1] - om * P[0], Vn[2]};   // non-turning -> turning frame
      System.out.printf("transfer to planet %d (body %d): %.2f -> %.2f AU, %.0f days; start %.0f km from the home planet (its SOI %.0f km)%n", tgt, tid, w.planet.au, w.system.planets.get(tgt).au, Th / 86400, len(P) / 1000, sy.soi[0] / 1000);
      double stopUp = 5631 - Terrain.SEA + 2000;
      double[] p7 = P.clone(), v7 = V.clone(); double tt = ts; long tks = 0; int[] used = new int[Warp.LEVELS.length]; double maxMs = 0, best7 = 1e300;
      while (tt < ts + 1.3 * Th) { Near nb = Near.of(w, e, tt, p7); double[] bp = nb.toBody(p7), u = nb.pointVel(w, e, bp), rvv = sub(v7, u); double br = len(bp); double[] rb = nb.dirToBody(rvv); double sink = -(rb[0] * bp[0] + rb[1] * bp[1] + rb[2] * bp[2]) / br;
        int lv = Warp.maxLevel(w, e, tt, p7, sink, br - nb.w.cube.R - (5631 - Terrain.SEA)); int k = Warp.LEVELS[lv]; used[lv]++; long a0 = System.nanoTime(); Warp.coast(w, e, tt, p7, v7, om, k, 0, null, stopUp); maxMs = Math.max(maxMs, (System.nanoTime() - a0) / 1e6); tt += k * 0.05; tks++;
        best7 = Math.min(best7, len(sub(p7, sy.centres(e, tt)[tid]))); }
      double[] p8 = P.clone(), v8 = V.clone(); double t8 = ts; while (t8 < tt - 1e-6) { int kk = (int) Math.min(2000, Math.round((tt - t8) / 0.05)); Warp.coast(w, e, t8, p8, v8, om, kk, Math.max(1, kk / 400), null, stopUp); t8 += kk * 0.05; }   // reference: 20 s steps to the same end time (5 s: the same end to 1 km, 4 x slower)
      double dEnd = len(sub(p7, p8)), flown = (tt - ts) * 30000;
      System.out.printf("  warp run: %.1f days in %d ticks = %.1f min real time (ticks per level %s), slowest tick %.1f ms; closest to planet %d %.0f km; end %.0f km from the 20 s reference%n", (tt - ts) / 86400, tks, tks / 1200.0, java.util.Arrays.toString(used), maxMs, tgt, best7 / 1000, dEnd / 1000);
      ok(best7 < 2e10, String.format("the transfer passes planet %d (%.2f million km: the aim leaves out the other planets' pulls on the way)", tgt, best7 / 1e9));
      ok(dEnd < 1e-4 * flown, String.format("  the warp run ends %.0f km from the 20 s reference after %.0f million km (%.1e of the way)", dEnd / 1000, flown / 1e9, dEnd / flown));
      ok(tks / 1200.0 < 15 && maxMs < 25, String.format("  %.0f days of flight in %.1f min of real time; slowest tick %.1f ms", (tt - ts) / 86400, tks / 1200.0, maxMs));
      // falling onto planet 6 from 300,000 km (inside its sphere), at rest over its centre: the warp stops over it as the reference does
      Near n6 = Near.body(w, e, ts, tid); double[] b0 = {0, 3e8, 0}, q = n6.toPlanet(b0), qv = n6.pointVel(w, e, new double[3]); { double[] d = sub(q, n6.c); qv[0] += om * d[1]; qv[1] -= om * d[0]; }   // at rest over its centre in the star's frame, not the home planet's turning one
      double[] q2 = q.clone(), qv2 = qv.clone(); double tq = ts; int h1 = -1; long tk1 = 0;
      while (h1 < 0 && tq < ts + 30 * 86400) { Near nb = Near.of(w, e, tq, q); double[] bp = nb.toBody(q), rb = nb.dirToBody(sub(qv, nb.pointVel(w, e, bp))); double br = len(bp), sink = -(rb[0] * bp[0] + rb[1] * bp[1] + rb[2] * bp[2]) / br;
        int lv = Warp.maxLevel(w, e, tq, q, sink, br - nb.w.cube.R - (5631 - Terrain.SEA)); int k = Warp.LEVELS[lv]; h1 = Warp.coast(w, e, tq, q, qv, om, k, 0, null, stopUp); tq += k * 0.05; tk1++; }
      double tr = ts; int h2 = -1; while (h2 < 0 && tr < ts + 30 * 86400) { h2 = Warp.coast(w, e, tr, q2, qv2, om, 200, 20, null, stopUp); tr += 10; }
      double s1 = h1 < 0 ? 0 : len(sub(qv, Near.body(w, e, tq, h1).pointVel(w, e, Near.body(w, e, tq, h1).toBody(q)))), s2 = h2 < 0 ? 0 : len(sub(qv2, Near.body(w, e, tr, h2).pointVel(w, e, Near.body(w, e, tr, h2).toBody(q2))));
      System.out.printf("  fall onto planet %d: warp run stops over body %d after %.1f h (%d ticks), %.0f m/s; reference body %d after %.1f h, %.0f m/s%n", tgt, h1, (tq - ts) / 3600, tk1, s1, h2, (tr - ts) / 3600, s2);
      ok(h1 == tid && h2 == tid && Math.abs(s1 - s2) < 0.02 * s2, String.format("  falling onto planet %d: both stop over it, %.0f vs %.0f m/s", tgt, s1, s2)); }
    System.out.println("NearTest: " + fail + " fail"); if (fail > 0) System.exit(1); } }
