import com.hopeless.infinity.planet.*;
/** VolcanoTest: 0.20.0 the home planet's volcanic provinces (Terrain.volcano) against the record. Saved tool. */
public class VolcanoTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) { PlanetWorld w = new PlanetWorld(1); Terrain t = w.terrain; int N = 40000, land = 0, prov = 0; double ga = Math.PI * (3 - Math.sqrt(5)); double[] cen = null;
    for (int i = 0; i < N; i++) { double y = 1 - 2 * (i + 0.5) / N, r = Math.sqrt(1 - y * y), th = ga * i; double[] s = {r * Math.cos(th), y, r * Math.sin(th)}; if (t.elevationM(s) <= 0) continue; land++; if (t.volcanic(s) > 0.5) { prov++; if (cen == null && t.volcanic(s) > 0.99) cen = s; } }
    ok(Math.abs(prov / (double) land - Terrain.VOLC_SHARE) < 0.02, String.format("volcanic provinces on %.1f %% of the land (CHOSEN 6 %%)", 100.0 * prov / land));
    // a 60 km x 60 km patch in a province: cones and volcanoes by sampling at 100 m
    double R = w.cube.R; double[] e1 = {-cen[1], cen[0], 0}; double l = Math.hypot(e1[0], e1[1]); e1[0] /= l; e1[1] /= l; double[] e2 = {cen[1] * e1[2] - cen[2] * e1[1], cen[2] * e1[0] - cen[0] * e1[2], cen[0] * e1[1] - cen[1] * e1[0]};
    int n = 600, cone = 0, strato = 0, lake = 0; double maxH = 0; long t0 = System.nanoTime();
    for (int i = 0; i < n; i++) for (int k = 0; k < n; k++) { double u = (i - n / 2) * 100 / R, v = (k - n / 2) * 100 / R; double[] s = {cen[0] + u * e1[0] + v * e2[0], cen[1] + u * e1[1] + v * e2[1], cen[2] + u * e1[2] + v * e2[2]}; double sl = Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]); for (int j = 0; j < 3; j++) s[j] /= sl;
      double[] kd = {0, 0}; double h = t.volcano(s, R, kd); maxH = Math.max(maxH, h); if (kd[0] == 1) cone++; else if (kd[0] == 2) strato++; else if (kd[0] == 3) lake++; }
    double us = (System.nanoTime() - t0) / 1e3 / (n * n);
    System.out.printf("60 x 60 km of province: cone ground %.1f %%, stratovolcano ground %.1f %%, lava-lake craters %d samples, highest volcano %.0f m; %.1f us a sample%n", 100.0 * cone / (n * n), 100.0 * strato / (n * n), lake, maxH, us);
    ok(cone > 0, "cinder cones present");
    double[] k1 = {0, 0}; System.out.printf("Wood 1980 cone: H/W 0.18, crater 0.40 W (as coded)%n");
    ok(maxH < 3600, String.format("highest volcano %.0f m (stratovolcanoes 1.5-3.5 km)", maxH));
    System.out.println("VolcanoTest: " + fail + " fail"); if (fail > 0) System.exit(1); } }
