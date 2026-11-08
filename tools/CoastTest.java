import com.hopeless.infinity.planet.*;
/** CoastTest: 0.20.0 cliffs on the home planet's coasts (Shore: hard-rock stretches) against the record: about half of Earth's coastline is cliffed
 *  (Young & Carilli 2019). Shore crossings are found along straight walks; a crossing is cliffed when the ground stands 8 m or more within 30 m inland. Saved tool. */
public class CoastTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) { com.hopeless.infinity.planet.Roads.on = false; com.hopeless.infinity.planet.Towns.on = false; com.hopeless.infinity.planet.Metro.on = false;   /* 0.27.0 megacities are MetroTest's */   /* 0.23.0: roads are RoadsTest's; this test reads the terrain */ PlanetWorld w = new PlanetWorld(1); int cross = 0, cliffed = 0; double hs = 0; int f = 0; long t0 = System.nanoTime();
    for (int ray = 0; ray < 400 && cross < 300; ray++) { double ang = ray * 2.399963; int x0 = (int) (Math.cos(ray * 0.7) * 2000000), z0 = (int) (Math.sin(ray * 1.3) * 2000000); double dx = Math.cos(ang), dz = Math.sin(ang);
      int prev = Column.of(w, f, x0, z0).surfaceY; for (int k = 1; k < 3000; k++) { int x = x0 + (int) (dx * k * 100), z = z0 + (int) (dz * k * 100); Column c = Column.of(w, f, x, z); if (c.voidCol) break; int y = c.surfaceY;
        if (prev < -1 && y >= 0) { int xs = x0 + (int) (dx * (k - 1) * 100), zs = z0 + (int) (dz * (k - 1) * 100), j0 = 0; for (int j = 0; j <= 25; j++) if (Column.of(w, f, xs + (int) (dx * j * 4), zs + (int) (dz * j * 4)).surfaceY >= 0) { j0 = j; break; }
          int top = -999; for (int j = j0; j <= j0 + 8; j++) top = Math.max(top, Column.of(w, f, xs + (int) (dx * j * 4), zs + (int) (dz * j * 4)).surfaceY); cross++; if (top >= 8) { cliffed++; hs += top; } k += 20; }
        prev = y; } }
    System.out.printf("%d shore crossings, %d cliffed (%.0f %%), mean cliff top %.0f m; %.1f s%n", cross, cliffed, 100.0 * cliffed / Math.max(1, cross), hs / Math.max(1, cliffed), (System.nanoTime() - t0) / 1e9);
    ok(cross >= 40 && Math.abs(cliffed / (double) cross - 0.5) < 0.15, String.format("cliffed coast %.0f %% of crossings (Young & Carilli 2019: about half)", 100.0 * cliffed / Math.max(1, cross)));
    int[] sp = w.spawn(); Column c = Column.of(w, 0, sp[0], sp[2]); ok(c.surfaceY >= 1 && c.surfaceY <= 6, String.format("spawn still on a low coast: y %d (%s, %s)", c.surfaceY, w.spawnKind, c.biome.key));
    System.out.println("CoastTest: " + fail + " fail"); if (fail > 0) System.exit(1); } }
