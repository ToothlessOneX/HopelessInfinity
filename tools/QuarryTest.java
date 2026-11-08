import com.hopeless.infinity.planet.*; import java.util.*;
/** QuarryTest: 0.28.5 the Cobble & Sons origin quarry (planet/Quarry) on seed 1, measured from Column.of (the carved ground) and
 *  Quarry.chunk (the blocks the generator places): the site near the spawn city, dry, off its built blocks, off every road; the pit
 *  carved in benches 10 m apart down to the floor; a stair from the rim to the floor with no step over one block; the pool, the first
 *  gate on its plinth, the signs, the office shed with door and desk; every block string real; the cost per chunk. Saved tool, part of the suite. */
public class QuarryTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) throws Exception { long t0 = System.nanoTime(); PlanetWorld w = new PlanetWorld(1); w.playerSpawn(); Metro m = w.metro(); Roads rd = w.roads();
    Metro.Plan p = null; for (Metro.Plan q : m.plans.values()) if (q.face == 0 && w.spawnKind.contains(q.site.name)) p = q;
    long ts = System.nanoTime(); Quarry.Site s = Quarry.site(w); double sms = (System.nanoTime() - ts) / 1e6;
    ok(s != null, "the quarry has a site (" + (s == null ? "none" : s.city() + ", face " + s.face() + " at " + s.x() + ", " + s.z() + ", rim y " + s.rimY()) + String.format(", placed in %.0f ms)", sms));
    if (s == null) { System.out.println("QuarryTest: " + fail + " fail"); return; }
    double d = Math.hypot(s.x() + 0.5 - p.cx, s.z() + 0.5 - p.cz); ok(s.face() == p.face && d > p.reachM && d < Quarry.RING_M + 1500, String.format("near the spawn city %s, past its edge: %.1f km from its centre (reach %.1f km)", p.site.name, d / 1000, p.reachM / 1000));
    // off every road, off the city, dry: over the pit and its rim (A + 14, B + 14), every 8 m
    int roads = 0, city = 0, wet = 0, n = 0; for (int x = -Quarry.A - 14; x <= Quarry.A + 14; x += 8) for (int z = -Quarry.B - 14; z <= Quarry.B + 14; z += 8) { n++; double[] sp = w.cube.spherePoint(s.face(), s.x() + x + 0.5, s.z() + z + 0.5);
      if (!rd.hits(sp).isEmpty()) { boolean on = false; for (Roads.Hit h : rd.hits(sp)) if (h.kind == 0 && Math.abs(h.off) <= Roads.HALF[h.cls.ordinal()] + 4) on = true; if (on) roads++; }
      if (m.inCity(s.face(), s.x() + x, s.z() + z, sp)) city++; double[] hw = w.terrain.heightAndWater(sp, w.cube.R); if (!Double.isNaN(hw[1]) && hw[1] > hw[0]) wet++; }
    ok(roads == 0 && city == 0 && wet == 0, String.format("its ground is clear: %d of %d points on a road, %d in the city, %d in water", roads, n, city, wet));
    // the pit in the ground: Column.of's top at each bench level
    Map<Integer, Integer> lv = new TreeMap<>(); int mism = 0, cols = 0; for (int x = -Quarry.A; x <= Quarry.A; x++) for (int z = -Quarry.B; z <= Quarry.B; z++) { int fl = Quarry.floor(s, s.x() + x, s.z() + z); if (fl == Integer.MIN_VALUE) continue; cols++;
      Column c = Column.of(w, s.face(), s.x() + x, s.z() + z); if (c.surfaceY != fl - 1) mism++; lv.merge(s.rimY() - fl, 1, Integer::sum); }
    System.out.println("  depth below the rim -> columns: " + lv);
    ok(cols > 7000 && mism == 0, String.format("the ground is cut to the pit's floors in all %d columns (%d not)", cols, mism));
    ok(lv.keySet().containsAll(List.of(10, 20, 30)) && lv.keySet().stream().allMatch(k -> k >= 1 && k <= 30), "three benches 10 m apart down to the floor 30 m below the rim (and the stair's steps between)");
    // the stair: along u = 0 from the rim to the middle, no step over a block
    int worst = 0, prev = Integer.MIN_VALUE, wz = 0; StringBuilder prof = new StringBuilder(); for (int z = -Quarry.B - Quarry.STAIR_OUT - 2; z <= 0; z++) { int y = Column.of(w, s.face(), s.x(), s.z() + z).surfaceY + 1; prof.append(y - s.rimY()).append(' ');
      if (prev != Integer.MIN_VALUE && Math.abs(y - prev) > worst) { worst = Math.abs(y - prev); wz = z; } prev = y; }
    System.out.println("  the stair's ground (y over the rim) from outside to the middle: " + prof);
    ok(worst <= 1, String.format("the stair in the -v end wall goes from the ground outside to the floor a block a step (largest step %d, at v %d)", worst, wz));
    // the blocks
    Map<String, Integer> cnt = new TreeMap<>(); Set<String> kinds = new TreeSet<>(); int chunks = 0; long tc = System.nanoTime();
    for (int x0 = Math.floorDiv(s.x() - Quarry.A - 16, 16) * 16; x0 < s.x() + Quarry.A + 16; x0 += 16) for (int z0 = Math.floorDiv(s.z() - Quarry.B - 16, 16) * 16; z0 < s.z() + Quarry.B + 16; z0 += 16) { chunks++;
      Quarry.chunk(w, s.face(), x0, z0, x0 + 16, z0 + 16, (x, y, z, b) -> { kinds.add(b); cnt.merge(b.startsWith("sign:") ? "sign" : b.replaceAll("\\[.*", ""), 1, Integer::sum); }); }
    double ms = (System.nanoTime() - tc) / 1e6 / chunks; System.out.println("  blocks: " + cnt);
    ok(cnt.getOrDefault("water", 0) > 100 && cnt.getOrDefault("cobblestone", 0) > 3000 && cnt.getOrDefault("gravel", 0) > 300, String.format("the worked floor: rubble (%d cobblestone, %d gravel) and a pool (%d water)", cnt.getOrDefault("cobblestone", 0), cnt.getOrDefault("gravel", 0), cnt.getOrDefault("water", 0)));
    ok(cnt.getOrDefault("hopelessinfinity:gate_frame", 0) >= 12 && cnt.getOrDefault("sign", 0) == 2, String.format("the first gate on its plinth (%d frame blocks) and two signs (%d)", cnt.getOrDefault("hopelessinfinity:gate_frame", 0), cnt.getOrDefault("sign", 0)));
    ok(cnt.getOrDefault("oak_door", 0) == 2 && cnt.getOrDefault("hopelessinfinity:desk", 0) == 1 && cnt.getOrDefault("glass_pane", 0) == 2, "the office shed: a door, a desk, two windows");
    List<String> bad = new ArrayList<>(); for (String k : kinds) { String why = BlockStates.bad(k); if (why != null) bad.add(k + " (" + why + ")"); } ok(bad.isEmpty(), "every block string is real (" + kinds.size() + " kinds)" + (bad.isEmpty() ? "" : ": " + bad));
    // the gate and the shed stand on the ground: no ground over their pads (buried), and no gap under their foundations over FOUND blocks
    int hi = 0, buried = 0; for (int x = Quarry.A + 4; x <= Quarry.A + 8; x++) for (int z = -5; z <= 5; z++) { int g = Column.of(w, s.face(), s.x() + x, s.z() + z).surfaceY; if (g >= s.gateY()) buried++; hi = Math.max(hi, s.gateY() - 1 - g); }
    for (int x = Quarry.SX0; x <= Quarry.SX1; x++) for (int z = Quarry.SZ0; z <= Quarry.SZ1; z++) { int g = Column.of(w, s.face(), s.x() + x, s.z() + z).surfaceY; if (g >= s.shedY()) buried++; hi = Math.max(hi, s.shedY() - 1 - g); }
    ok(buried == 0, "no ground over the gate's or the shed's pad (" + buried + " columns; pads at y " + s.gateY() + " and " + s.shedY() + ", rim " + s.rimY() + ")");
    hi = hi - Quarry.FOUND + 1;
    ok(hi <= 0, "their foundations reach the ground (largest gap under them " + Math.max(0, hi) + " blocks)");
    ok(ms < 20, String.format("a quarry chunk costs %.2f ms", ms));
    System.out.printf("  in %.0f s%n", (System.nanoTime() - t0) / 1e9); System.out.println("QuarryTest: " + fail + " fail"); } }
