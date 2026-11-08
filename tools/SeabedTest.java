import com.hopeless.infinity.planet.*;

/** SeabedTest seed [n]: measures what the 0.5.0 sea floor plan (planet/Seabed, from Hopeless Seabed) gives on a seed's start planet, from the
 *  mod's own Terrain and Column code: random points on all six faces and in a 20 km square round the spawn; the slope from the true ground
 *  heights 1 m away (as the chunk generator does). Prints the area share of each kind on the floor to 60 m and in the shore strip, the
 *  slope spread under water, and the temperature spread. Checks: the classifier agrees with Seabed's published thresholds. Saved tool. */
public class SeabedTest {
    static int pass, fail;
    static void check(String w, boolean ok, String got) { if (ok) pass++; else fail++; System.out.printf("%s %-60s %s%n", ok ? "PASS" : "FAIL", w, got); }
    public static void main(String[] a) { com.hopeless.infinity.planet.Roads.on = false; com.hopeless.infinity.planet.Towns.on = false; com.hopeless.infinity.planet.Metro.on = false;   /* 0.27.0 megacities are MetroTest's */   /* 0.23.0: roads are RoadsTest's; this test reads the terrain */
        long seed = a.length > 0 ? Long.parseLong(a[0]) : 1; int n = a.length > 1 ? Integer.parseInt(a[1]) : 400000;
        PlanetWorld w = new PlanetWorld(seed); CubeSphere c = w.cube; System.out.println(w.describe());
        // threshold checks (Seabed's published-based values as set)
        check("seagrass limit = ln(1/0.11)/0.07 = 31.5 m", Math.abs(Seabed.seagrassMax() - 31.53) < 0.05, String.format("%.2f m", Seabed.seagrassMax()));
        check("flat 10 m, 12 C: seagrass", Seabed.classify(10, 1, 12) == Seabed.Kind.SEAGRASS, "" + Seabed.classify(10, 1, 12));
        check("15 deg 10 m, 12 C: kelp", Seabed.classify(10, Math.cos(Math.toRadians(15)), 12) == Seabed.Kind.KELP, "" + Seabed.classify(10, Math.cos(Math.toRadians(15)), 12));
        check("15 deg 10 m, 22 C: coral slope", Seabed.classify(10, Math.cos(Math.toRadians(15)), 22) == Seabed.Kind.CORAL_SLOPE, "" + Seabed.classify(10, Math.cos(Math.toRadians(15)), 22));
        check("15 deg 10 m, 2 C: bare rock (too cold for kelp)", Seabed.classify(10, Math.cos(Math.toRadians(15)), 2) == Seabed.Kind.SUBTIDAL_ROCK, "" + Seabed.classify(10, Math.cos(Math.toRadians(15)), 2));
        check("flat 300 m: deep ooze", Seabed.classify(300, 1, 4) == Seabed.Kind.DEEP_OOZE, "" + Seabed.classify(300, 1, 4));
        check("flat 1 m above water, -3 C: ice shore", Seabed.classify(-1, 1, -3) == Seabed.Kind.ICE_SHORE, "" + Seabed.classify(-1, 1, -3));
        check("4 deg 1.5 m above water, 15 C: beach", Seabed.classify(-1.5, Math.cos(Math.toRadians(4)), 15) == Seabed.Kind.BEACH, "" + Seabed.classify(-1.5, Math.cos(Math.toRadians(4)), 15));
        check("1 deg 0.2 m above water: low marsh", Seabed.classify(-0.2, Math.cos(Math.toRadians(1)), 15) == Seabed.Kind.LOW_MARSH, "" + Seabed.classify(-0.2, Math.cos(Math.toRadians(1)), 15));
        int[] sp = w.spawn();
        for (int pass2 = 0; pass2 < 2; pass2++) {
            java.util.Random r = new java.util.Random(7); int K = Seabed.Kind.values().length; long[] floor = new long[K], shore = new long[K]; long nf = 0, ns = 0, under = 0;
            int[] slopeHist = new int[8]; double[] tq = new double[n]; int nt = 0;
            for (int i = 0; i < n; i++) {
                int f; double x, z;
                if (pass2 == 0) { f = r.nextInt(6); x = c.faceX0(f) + 2 + r.nextDouble() * (c.F - 4); z = c.faceZ0() + 2 + r.nextDouble() * (c.F - 4); }
                else { f = 0; x = sp[0] + (r.nextDouble() - 0.5) * 20000; z = sp[2] + (r.nextDouble() - 0.5) * 20000; }
                double[] p = c.spherePoint(f, x, z); if (p == null) continue; double h = w.terrain.heightM(p, c.R), depth = -h;
                if (depth < -Seabed.BEACH_TOP || depth > 60) { if (depth > 60) under++; continue; }
                double cs = Seabed.cosSlope(w.terrain.heightM(c.spherePoint(f, x - 1, z), c.R), w.terrain.heightM(c.spherePoint(f, x + 1, z), c.R),
                    w.terrain.heightM(c.spherePoint(f, x, z - 1), c.R), w.terrain.heightM(c.spherePoint(f, x, z + 1), c.R));
                double t = w.terrain.temperatureC(p, Math.max(0, h)); Seabed.Kind k = Seabed.classify(depth, cs, t);
                if (depth >= Seabed.BEACH_BOTTOM) { floor[k.ordinal()]++; nf++; under++; double deg = Math.toDegrees(Math.acos(cs)); slopeHist[Math.min(7, (int) (deg / 5))]++; tq[nt++] = t; }
                else { shore[k.ordinal()]++; ns++; }
            }
            System.out.printf("%n== %s ==%n", pass2 == 0 ? "whole planet (6 faces)" : "20 km square round the spawn " + sp[0] + " " + sp[2]);
            System.out.printf("floor 0.5-60 m: %d samples%n", nf); for (Seabed.Kind k : Seabed.Kind.values()) if (floor[k.ordinal()] > 0) System.out.printf("  %-14s %6.2f %%%n", k, 100.0 * floor[k.ordinal()] / nf);
            System.out.printf("shore strip (2.5 m above to 0.5 m under the water): %d samples%n", ns); for (Seabed.Kind k : Seabed.Kind.values()) if (shore[k.ordinal()] > 0) System.out.printf("  %-14s %6.2f %%%n", k, 100.0 * shore[k.ordinal()] / ns);
            System.out.print("floor slope (deg, 5 deg bins 0-35+):"); for (int b : slopeHist) System.out.printf(" %.1f%%", nf == 0 ? 0 : 100.0 * b / nf); System.out.println();
            if (nt > 0) { double[] tt = java.util.Arrays.copyOf(tq, nt); java.util.Arrays.sort(tt); System.out.printf("floor water temperature C: 5%% %.1f, median %.1f, 95%% %.1f%n", tt[nt / 20], tt[nt / 2], tt[nt * 19 / 20]); }
        }
        System.out.printf("%nSeabedTest: %d pass, %d fail%n", pass, fail); if (fail > 0) System.exit(1);
    }
}
