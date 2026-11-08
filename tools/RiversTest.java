import com.hopeless.infinity.planet.*;
/** RiversTest: 0.21.0 rivers and lakes (planet/Rivers) against the record. Saved tool. */
public class RiversTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) throws Exception { PlanetWorld w = new PlanetWorld(1); Rivers r = new Rivers(w.terrain, w.cube, w.seed);
    System.out.printf("built in %d ms: %d river cells, %d lake cells, %d salt-flat cells, %d channel segments; land runoff %.0f km3 a year%n", r.buildMs, r.riverCells, r.lakeCells, r.dryCells, r.segs.size(), r.landRunoffKm3);
    int n = 6 * Rivers.N * Rivers.N, endsOk = 0, rivers = 0; double maxQ = 0; int maxC = -1;
    for (int c = 0; c < n; c++) { if (r.kind[c] != 4) continue; rivers++; int x = c, steps = 0; while (x >= 0 && r.kind[x] != 1 && r.kind[x] != 3 && steps < 100000) { x = r.down[x]; steps++; } if (x >= 0 && (r.kind[x] == 1 || r.kind[x] == 3)) endsOk++; if (r.q[c] > maxQ) { maxQ = r.q[c]; maxC = c; } }
    ok(endsOk == rivers, String.format("every river reaches the sea or a salt flat: %d of %d", endsOk, rivers));
    System.out.printf("largest river: %.0f m3/s (Amazon 209,000, Mississippi 16,800), %.0f m wide, %.1f m deep, %.0f km to its mouth%n", maxQ, Rivers.width(maxQ), Rivers.depth(maxQ), r.dist[maxC] / 1000);
    ok(maxQ > 1000 && maxQ < 300000, "the largest river's discharge is in Earth's range");
    ok(r.buildMs < 20000, String.format("built in %.1f s", r.buildMs / 1000.0));
    // 0.29.8 the water in its channel (Overlord, 2026-11-08: water 20 blocks over the land, flooding towns; dry beds): RiverWalk along every channel within 25 km of the spawn
    RiverWalk.main(new String[]{"25"});
    ok(RiverWalk.samplesN > 3000 && RiverWalk.floodPct <= 0.5, String.format("the level stands no more than 2 m over the land before the river: %.2f %% of %d channel samples over it (0.5 %% allowed), worst %.1f m", RiverWalk.floodPct, RiverWalk.samplesN, RiverWalk.worstFloodM));
    ok(RiverWalk.spillPct <= 1.0, String.format("banks hold the water: %.2f %% of the channel with a bank under the level (1 %% allowed: confluences, where the larger river's valley cuts the smaller's bank)", RiverWalk.spillPct));
    ok(RiverWalk.dryPct <= 2.0, String.format("water blocks in the channel: %.2f %% of channel centres dry (2 %% allowed)", RiverWalk.dryPct));
    System.out.println("RiversTest: " + fail + " fail"); if (fail > 0) System.exit(1); } }
