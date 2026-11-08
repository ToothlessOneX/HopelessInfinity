import com.hopeless.infinity.planet.*;

/** Measures the real-size start planet the mod builds for a seed (0.2.0: one dimension per face). Args: seed. Exit 1 on a failed check. */
public class PlanetTest {
    static int pass, fail;
    static void check(String w, boolean ok, String got) { if (ok) pass++; else fail++; System.out.printf("%s %-64s %s%n", ok ? "PASS" : "FAIL", w, got); }
    public static void main(String[] a) { com.hopeless.infinity.planet.Roads.on = false; com.hopeless.infinity.planet.Towns.on = false; com.hopeless.infinity.planet.Metro.on = false;   /* 0.27.0 megacities are MetroTest's */   /* 0.23.0: roads are RoadsTest's; this test reads the terrain */
        long seed = a.length > 0 ? Long.parseLong(a[0]) : 6537286697268376325L;
        long t0 = System.nanoTime(); PlanetWorld w = new PlanetWorld(seed); System.out.printf("setup %.0f ms%n%s%n", (System.nanoTime() - t0) / 1e6, w.describe());
        CubeSphere c = w.cube; Terrain t = w.terrain;
        check("face fits the lifted packing (half-width < 8,388,608)", c.F / 2 < 8_388_608, String.format("%,d blocks", c.F / 2));
        check("face width = pi R / 2 (real size, 1 block = 1 m)", Math.abs(c.F - Math.PI * w.planet.radiusKm * 1000 / 2) < 1, String.format("%,d blocks for R %,.0f km", c.F, w.planet.radiusKm));
        int n = 200000, land = 0; double ga = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < n; i++) { double y = 1 - 2 * (i + 0.5) / n, r = Math.sqrt(1 - y * y), th = ga * i; if (t.surfaceY(new double[]{r * Math.cos(th), y, r * Math.sin(th)}, c.R) >= Terrain.SEA) land++; }
        check("land share near Earth's 29.7% (ETOPO5)", Math.abs(land / (double) n - 0.297) < 0.03, String.format("%.1f%%", 100.0 * land / n));
        // seams: the block just inside every edge, folded over, is the same planet point and nearly the same height
        int seams = 0, worstH = 0, over = 0, back = 0; double worstP = 0;
        for (int f = 0; f < 6; f++) for (int side = 0; side < 4; side++) for (int k = 1; k < 600; k++) {
            double along = c.faceZ0() + (k / 600.0) * c.F + 0.5;
            double x = side == 0 ? c.faceX0(f) + c.F + 0.3 : side == 1 ? c.faceX0(f) - 0.3 : c.faceX0(f) + (k / 600.0) * c.F + 0.5, z = side == 2 ? c.faceZ0() + c.F + 0.3 : side == 3 ? c.faceZ0() - 0.3 : along;
            double xi = Math.max(c.faceX0(f) + 0.001, Math.min(c.faceX0(f) + c.F - 0.001, x)), zi = Math.max(c.faceZ0() + 0.001, Math.min(c.faceZ0() + c.F - 0.001, z));
            CubeSphere.Fold fd = c.transfer(f, x, z); seams++;
            double[] s1 = CubeSphere.sphere(CubeSphere.cube(f, c.u(f, xi), c.v(zi))), s2 = c.spherePoint(fd.face(), fd.x(), fd.z()); if (s2 == null) { worstP = 1e9; continue; }
            worstP = Math.max(worstP, Math.sqrt(Math.pow(s1[0] - s2[0], 2) + Math.pow(s1[1] - s2[1], 2) + Math.pow(s1[2] - s2[2], 2)) * c.R);
            int dh = Math.abs(t.surfaceY(s1, c.R) - t.surfaceY(s2, c.R)); worstH = Math.max(worstH, dh);
            int y1 = t.surfaceY(s1, c.R), local = 0;   // local slope: the biggest step to any neighbour 1 block away inside the face
            for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { double nx = Math.max(c.faceX0(f) + 0.001, Math.min(c.faceX0(f) + c.F - 0.001, xi + o[0])), nz = Math.max(c.faceZ0() + 0.001, Math.min(c.faceZ0() + c.F - 0.001, zi + o[1]));
                local = Math.max(local, Math.abs(y1 - t.surfaceY(CubeSphere.sphere(CubeSphere.cube(f, c.u(f, nx), c.v(nz))), c.R))); }
            if (dh > local + 1) over++;
            double[] rot = fd.rot(); double bx = fd.x() - (rot[0] * Math.signum(x - xi) + rot[1] * Math.signum(z - zi)) * 0.9, bz = fd.z() - (rot[2] * Math.signum(x - xi) + rot[3] * Math.signum(z - zi)) * 0.9;
            CubeSphere.Fold fb = c.transfer(fd.face(), bx, bz); if (fb.face() == f && Math.hypot(fb.x() - xi, fb.z() - zi) < 1.5) back++; }
        check("folded point is the same planet point (within 1 block)", worstP < 1.0, String.format("worst %.3f blocks over %d edge samples", worstP, seams));
        check("height step across an edge no bigger than the local slope + 1", over == 0, over + " over; worst step " + worstH);
        check("folding back over the same edge returns home", back == seams, back + "/" + seams);
        // ground: walkable slopes and realistic relief at real scale
        int lc = 0, steep = 0; double sum = 0; java.util.Random rr = new java.util.Random(1);
        for (int i = 0; i < 60000; i++) { int f = rr.nextInt(6); double x = c.faceX0(f) + rr.nextDouble() * (c.F - 2), z = c.faceZ0() + rr.nextDouble() * (c.F - 2);
            double[] p = c.spherePoint(f, x, z), q = c.spherePoint(f, x + 1, z); double hp = t.heightM(p, c.R); if (hp < 0 || hp > 4000) continue; lc++; double d = Math.abs(t.heightM(q, c.R) - hp); sum += d; if (d > 1) steep++; }
        check("land (0-4 km) steeper than 45 deg under 5%", steep < 0.05 * lc, String.format("%.2f%% (mean slope %.1f deg over %d samples)", 100.0 * steep / lc, Math.toDegrees(Math.atan(sum / lc)), lc));
        double[] eq = CubeSphere.sphere(CubeSphere.cube(0, 0, 0)), pole = CubeSphere.sphere(CubeSphere.cube(4, 0, 0));
        System.out.printf("T0 %.1f C | equator %.1f C | pole %.1f C%n", t.t0C, t.temperatureC(eq, 0), t.temperatureC(pole, 0));
        check("start planet mean temperature between 0 and 30 C (inside the habitable zone)", w.meanTempC > 0 && w.meanTempC < 30, String.format("%.1f C at HZ fraction %.2f", w.meanTempC, w.hzFraction));
        check("equator warmer than pole by about 42 K (North 1975)", Math.abs(t.temperatureC(eq, 0) - t.temperatureC(pole, 0) - 42) < 6, String.format("%.1f K", t.temperatureC(eq, 0) - t.temperatureC(pole, 0)));
        long t1 = System.nanoTime(); int cols = 0; for (int i = 0; i < 40000; i++) { Column.of(w, i % 6, (int) (rr.nextDouble() * c.F) - c.F / 2, (int) (rr.nextDouble() * c.F) - c.F / 2); cols++; }
        double us = (System.nanoTime() - t1) / 1e3 / cols; check("column plans under 20 ms per chunk", us * 256 / 1000 < 20, String.format("%.1f us per column, %.1f ms per chunk", us, us * 256 / 1000));
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE; for (int i = 0; i < 200000; i++) { int y = t.surfaceY(c.spherePoint(i % 6, (rr.nextDouble() - 0.5) * c.F, (rr.nextDouble() - 0.5) * c.F), c.R); lo = Math.min(lo, y); hi = Math.max(hi, y); }
        check("ground stays inside the world (y -2560..5631)", lo > -2560 && hi < 5631, "ground y " + lo + ".." + hi);
        // noise continuity: the ground at two points 1 cm apart can't differ by more than the steepest real slope allows
        double worstC = 0; for (int i = 0; i < 300000; i++) { int f = rr.nextInt(6); double x = (rr.nextDouble() - 0.5) * c.F, z = (rr.nextDouble() - 0.5) * c.F;
            worstC = Math.max(worstC, Math.abs(t.heightM(c.spherePoint(f, x, z), c.R) - t.heightM(c.spherePoint(f, x + 0.01, z), c.R))); }
        check("ground is continuous (1 cm step changes height < 5 cm)", worstC < 0.05, String.format("worst %.4f m over 300,000 pairs", worstC));
        long t2 = System.nanoTime(); int[] sp = w.spawn(); Column sc = Column.of(w, 0, sp[0], sp[2]);
        check("spawn on dry land near face 0's centre", sc.surfaceY > Terrain.SEA, String.format("%d %d %d, %s %.0f m, %.1f C (search %.0f ms)", sp[0], sp[1], sp[2], sc.biome.key, sc.heightM, sc.tempC, (System.nanoTime() - t2) / 1e6));
        // terrain slopes on dry land (Hopeless Deep's slope blocks): how many columns get one, and do neighbours share their corners
        int dry = 0, sl = 0, steepN = 0, cornerBad = 0, pairs = 0; long sp0 = com.hopeless.infinity.slope.Slopes.tooSteep, cl0 = com.hopeless.infinity.slope.Slopes.clamped;
        for (int i = 0; i < 4000; i++) { int f = rr.nextInt(6); int x0 = (int) ((rr.nextDouble() - 0.5) * c.F * 0.98), z0 = (int) ((rr.nextDouble() - 0.5) * c.F * 0.98);
            double[] tp = new double[16]; for (int k = 0; k < 16; k++) { Column cc = Column.of(w, f, x0 + k % 4, z0 + k / 4); tp[k] = cc.surfaceY < Terrain.SEA ? Double.NaN : cc.groundTop; }
            Column mid = Column.of(w, f, x0 + 1, z0 + 1), right = Column.of(w, f, x0 + 2, z0 + 1); if (mid.surfaceY < Terrain.SEA || right.surfaceY < Terrain.SEA) continue; dry++;
            double[] sa = {tp[0], tp[1], tp[2], tp[4], tp[5], tp[6], tp[8], tp[9], tp[10]}, sb = {tp[1], tp[2], tp[3], tp[5], tp[6], tp[7], tp[9], tp[10], tp[11]};
            int[] ya = com.hopeless.infinity.slope.Slopes.choose(mid.surfaceY, sa), yb = com.hopeless.infinity.slope.Slopes.choose(right.surfaceY, sb);
            if (ya != null) sl++;
            if (ya != null && yb != null) { pairs++; int[] ha = com.hopeless.infinity.slope.SlopeMesh.decode(ya[1], 4), hb = com.hopeless.infinity.slope.SlopeMesh.decode(yb[1], 4);   // a's east corners (ne, se) = b's west corners (nw, sw), in world height
                if (Math.abs((ya[0] + ha[1] / 4.0) - (yb[0] + hb[0] / 4.0)) > 0.26 || Math.abs((ya[0] + ha[2] / 4.0) - (yb[0] + hb[3] / 4.0)) > 0.26) cornerBad++; } }
        System.out.printf("slopes: %.1f%% of dry columns get a piece, too steep %d%n", 100.0 * sl / dry, com.hopeless.infinity.slope.Slopes.tooSteep - sp0);
        check("terrain slopes cover most dry ground (> 50% of columns)", sl > 0.5 * dry, String.format("%.1f%% of %d", 100.0 * sl / dry, dry));
        long clamped = com.hopeless.infinity.slope.Slopes.clamped - cl0;
        check("neighbouring slopes meet at shared corners (Deep's clamping aside: < 3% of pairs)", cornerBad < 0.03 * pairs, String.format("%d of %d pairs (%.1f%%); pieces clamped %d", cornerBad, pairs, 100.0 * cornerBad / pairs, clamped));
        System.out.printf("%d passed, %d failed%n", pass, fail); if (fail > 0) System.exit(1);
    }
}
