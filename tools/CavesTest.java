import com.hopeless.infinity.planet.*;
/** CavesTest: 0.19.0 caves (planet/Caves) measured against the record. Saved tool. Args: [noodle threshold] to tune. */
public class CavesTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) {
    if (a.length >= 3) { Caves.NOODLE_L = Double.parseDouble(a[0]); Caves.NOODLE_T = Double.parseDouble(a[1]); Caves.CHAMBER_P = Double.parseDouble(a[2]); }
    PlanetWorld w = new PlanetWorld(1); Caves cv = new Caves(w); int N = 20000, land = 0, karst = 0; double ga = Math.PI * (3 - Math.sqrt(5));
    for (int i = 0; i < N; i++) { double y = 1 - 2 * (i + 0.5) / N, r = Math.sqrt(1 - y * y), th = ga * i; double[] s = {r * Math.cos(th), y, r * Math.sin(th)}; if (w.terrain.heightM(s, w.cube.R) <= 0) continue; land++; if (cv.rockMask(s) > cv.rockCut) karst++; }
    ok(Math.abs(karst / (double) land - Caves.KARST_SHARE) < 0.02, String.format("carbonate rock on %.1f %% of the land (Goldscheider 2020: 15.2 %%)", 100.0 * karst / land));
    // a 1 km x 1 km block, 6..250 m under flat ground at y 300, at 2 m steps
    int S = 2, surf = 300; long vox = 0, open = 0, runs = 0, runLen = 0; long t0 = System.nanoTime(); int ox = 123456, oz = -654321;
    for (int x = 0; x < 1000; x += S) for (int z = 0; z < 1000; z += S) { boolean prev = false; int run = 0;
      for (int y = surf - 250; y <= surf - 6; y += S) { vox++; } }
    for (int x = 0; x < 1000; x += S) for (int y = surf - 250; y <= surf - 6; y += S) { boolean prev = false; int run = 0;
      for (int z = 0; z < 1000; z += S) { boolean v = cv.karstVoid(0, ox + x, y, oz + z, surf); if (v) { open++; run += S; } else if (run > 0) { runs++; runLen += run; run = 0; } } }
    double us = (System.nanoTime() - t0) / 1e3 / vox, f = open / (double) vox, chord = runs == 0 ? 0 : runLen / (double) runs;
    double width = chord * 4 / Math.PI, lenPerKm2 = f * 1e6 * 244 / (Math.PI / 4 * width * width) / 1000;   // passage length (km) per km2: open volume / cross-section
    System.out.printf("karst block: %.3f %% open, mean chord %.1f m (passage ~%.1f m across), ~%.1f km of passage per km2; %.2f us a block%n", 100 * f, chord, width, lenPerKm2, us);
    ok(width >= 4 && width <= 20, String.format("  passages %.1f m across (Palmer 2007: typically 1-10 m; GAME: up to ~1.5 x)", width));
    ok(lenPerKm2 > 1.5 && lenPerKm2 < 8, String.format("  %.1f km of passage per km2 (Mammoth Cave: ~3)", lenPerKm2));
    // entrances
    int e = 0, en = 0; for (int x = 0; x < 200000; x += 977) for (int z = 0; z < 200000; z += 991) { en++; if (cv.entrance(0, x, z)) e++; }
    ok(Math.abs(e / (double) en - Caves.ENTRANCE_SHARE) < 0.02, String.format("entrance spots on %.1f %% of the karst (CHOSEN 5 %%)", 100.0 * e / en));
    // lava tubes on the moon: width ~60 m, roofed
    PlanetWorld m = w.moon(); Caves cm = new Caves(m); System.out.printf("moon: typical tube width %.0f m (Marius Hills pit: 65 m)%n", cm.tubeW);
    ok(cm.tubeW > 40 && cm.tubeW < 90, String.format("  lunar tube width %.0f m: 10 m x 9.81 / %.2f (Earth tubes 1-30 m)", cm.tubeW, m.gravity));
    int cols = 0, inTube = 0, sky = 0, maxW = 0; for (int x = 0; x < 400000; x += 5) { int[] t = cm.tube(0, x, 0, 0, true); cols++; if (t != null) { inTube++; if (t[1] == 0) sky++; } }
    double share = inTube / (double) cols; System.out.printf("  a 400 km line across the maria: %.2f %% of it over a tube, %d skylight columns%n", 100 * share, sky);
    ok(share > 0.001 && share < 0.05, String.format("  tubes under %.2f %% of a straight line (spacing ~8 km, ~60 m wide: ~0.75 %%)", 100 * share));
    int[] t1 = null; for (int x = 0; x < 400000 && t1 == null; x += 5) { int[] t = cm.tube(0, x, 0, 0, true); if (t != null && t[1] < 0) t1 = t; }
    ok(t1 != null && t1[1] < -10 && t1[1] - t1[0] > 5, t1 == null ? "  no roofed tube found" : String.format("  a roofed tube: open from y %d to %d under ground at y 0", t1[0], t1[1]));
    ok(new Caves(w).tubesOn && cm.karstOn == false, "lava tubes on the home planet (0.20.0: under its young lava fields); no karst on the airless moon");
    System.out.println("CavesTest: " + fail + " fail"); if (fail > 0) System.exit(1); } }
