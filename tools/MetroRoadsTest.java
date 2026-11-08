import com.hopeless.infinity.planet.*; import java.util.*;
/** MetroRoadsTest: 0.27.7 the roads reach every seed-1 megacity by its boulevard (planet/Metro.portal, planet/Roads), measured by
 *  MetroRoadsProbe from the laid roads' samples: every road into a city ends at a boulevard end, no road runs on a tower lot, each road
 *  meets the boulevard's pad within a block, and the boulevard holds the arterial's 7 % (a 1-block rounding over 64 m allowed). Saved tool, part of the suite. */
public class MetroRoadsTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) { long t0 = System.nanoTime(); PlanetWorld w = new PlanetWorld(1); w.playerSpawn(); for (Sites.Site s : w.sites().all) if (Metro.isCity(s)) w.metro().plan(s);
    Map<String, double[]> r = MetroRoadsProbe.run(w, true); int roads = 0, off = 0, lots = 0; double step = 0, grade = 0, far = 0;
    int cross = 0, aveN = 0, par = 0; double sg = 0, aStep = 0, bStep = 0;
    for (double[] v : r.values()) { roads += (int) v[0]; off += (int) v[5]; lots += (int) v[3]; step = Math.max(step, v[2]); grade = Math.max(grade, v[4]); far = Math.max(far, v[1]); cross += (int) v[7]; sg = Math.max(sg, v[8]); aStep = Math.max(aStep, v[9]); bStep = Math.max(bStep, v[10]); aveN += (int) v[11]; par += (int) v[12]; }
    ok(r.size() == 13 && roads > 0 && off == 0, String.format("%d cities (8 megacities, the 5 largest large cities): all %d roads into them end at their portals, Interstates on the boulevard (%d off; ends up to %.1f km from a centre)", r.size(), roads, off, far / 1000));
    ok(lots == 0, "no road sample on a tower lot (" + lots + ")");
    ok(step <= 1, String.format("every Interstate meets the boulevard's pad within a block (largest step %.0f)", step));
    double lim = Roads.GRADE[Roads.Cls.ARTERIAL.ordinal()] + 1 / 64.0; ok(grade <= lim + 1e-9, String.format("the boulevards' steepest grade %.1f %% over 64 m (the arterial's 7 %% plus one block of rounding: %.1f %%)", grade * 100, lim * 100));
    // 0.27.8 Interstates on the boulevard, the rest at avenue ends; no at-grade crossing of an Interstate near a city; the streets
    ok(cross == 0, String.format("no Interstate crosses another road at grade near a city (%d places; %d road pairs share a corridor under 25 deg, merged at the junction stage, not built yet)", cross, par));
    ok(aveN > 5000 && sg <= lim + 1e-9, String.format("the avenues are paved (%d samples every 64 m) and hold 7 %% (steepest %.1f %% over 64 m)", aveN, sg * 100));
    ok(aStep <= 1 && bStep <= 1, String.format("the avenues meet the roads at their ends within a block (%.0f) and the boulevard within a block (%.0f)", aStep, bStep));
    ok((System.nanoTime() - t0) / 1e9 < 120, String.format("in %.0f s", (System.nanoTime() - t0) / 1e9));
    System.out.println("MetroRoadsTest: " + fail + " fail"); } }
