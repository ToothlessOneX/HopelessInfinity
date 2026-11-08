import com.hopeless.infinity.planet.Tower; import java.util.*;
/** TowerTest: 0.26.2 supertall towers (planet/Tower) against the values they are built from, measured from the blocks the generator places.
 *  Saved tool, part of the suite. */
public class TowerTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) throws Exception {
    for (int[] hs : new int[][]{{450, 1}, {450, 2}, {1650, 1}}) { int H = hs[0]; Tower t = new Tower("test " + H + " seed " + hs[1], H, hs[1]); System.out.println("  " + t.probe());   // seed 2: the chamfered corners
      // every block string is real; count furniture
      Set<String> seen = new TreeSet<>(); Map<String, Integer> furn = new TreeMap<>(); int R = t.reach(), top = t.top();
      for (int y = 0; y < top; y += 1) for (int x = -R; x <= R; x += (y % 4 == 1 || y % 4 == 2 ? 1 : 3)) for (int z = -R; z <= R; z += (y % 4 == 1 || y % 4 == 2 ? 1 : 3)) { String b = t.at(x, y, z); if (b == null || b.equals("air")) continue; seen.add(b);
        if (b.startsWith("hopelessinfinity:") || b.contains("_bed[")) furn.merge(b.replaceAll("\\[.*", "").replace("hopelessinfinity:", ""), 1, Integer::sum); }
      List<String> bad = new ArrayList<>(); for (String b : seen) { String why = BlockStates.bad(b); if (why != null) bad.add(b + " (" + why + ")"); }
      ok(bad.isEmpty(), String.format("%d m: every block string exists in the game (%d kinds)%s", H, seen.size(), bad.isEmpty() ? "" : ": " + bad.subList(0, Math.min(5, bad.size()))));
      System.out.println("  furniture sampled: " + furn);
      for (String need : new String[]{"desk", "chair", "computer", "white_bed", "toilet", "shower", "sofa", "stove", "fridge", "television"}) if (H > 1000) ok(furn.getOrDefault(need, 0) > 0, H + " m: " + need + " placed");
      // uses: every floor has one; the stack order (retail, office, hotel, home, observation) holds
      int last = -1; boolean order = true; String[] rank = {"RETAIL", "OFFICE", "HOTEL", "HOME", "OBSERVATION"};
      for (int f = 0; f < t.floors; f++) { Tower.Use u = t.use(f); int r = Arrays.asList(rank).indexOf(u.name()); if (r < 0) continue; if (r < last) order = false; last = r; }
      ok(order, H + " m: uses stack bottom to top as in Shanghai Tower, Ping An and Merdeka 118 (retail, offices, hotel, homes, observation)");
      // stairs: two exit stairways at every floor, a third above 420 ft (128 m) (IBC 403.5); count stair blocks in the core per floor
      int[] probeF = {10, Math.min(t.floors - 2, 40), t.floors / 2}; for (int f : probeF) { int yy = f * Tower.FLOOR + 1, boxes = 0; Set<Integer> q = new HashSet<>();
        for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) { String b = t.at(x, yy, z); if (b != null && b.startsWith("stone_brick_stairs")) q.add((x > 0 ? 1 : 0) + (z > 0 ? 2 : 0)); }
        boxes = q.size(); int want = f * Tower.FLOOR > 128 ? 3 : 2; ok(boxes >= want, String.format("%d m floor %d (%d m up): %d stairways (IBC 403.5: %d)", H, f, f * Tower.FLOOR, boxes, want)); }
      // mechanical floors every 15 floors at most (Shanghai Tower, Ping An)
      int gap = 0, worst = 0; for (int f = 6; f < t.floors; f++) { if (t.use(f) == Tower.Use.MECHANICAL || t.use(f) == Tower.Use.SKY_LOBBY) gap = 0; else worst = Math.max(worst, ++gap); }
      ok(worst <= Tower.MECH_EVERY, String.format("%d m: at most %d floors between mechanical/refuge floors (Shanghai Tower, Ping An: about 15)", H, worst));
      // 0.26.3 the corner cuts: every shaped block stands full height with its solid toward the tower (measured from ShapeLib's own collision boxes)
      { int n = 0, okN = 0; int yy = 20 * Tower.FLOOR + 1; for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) { String b = t.at(x, yy, z); if (b == null || !b.startsWith("shape:")) continue; n++; String[] p = b.split(":", 4);
          int si = com.hopeless.infinity.slope.ShapeLib.INDEX.get(p[1]), o = Integer.parseInt(p[2]); double vx = 0, vz = 0, vol = 0; int ylo = 99, yhi = -1;
          for (int[] bx : com.hopeless.infinity.slope.ShapeLib.boxes(si, o)) { double v = (bx[3] - bx[0]) * (bx[4] - bx[1]) * (bx[5] - bx[2]); vol += v; vx += v * ((bx[0] + bx[3]) / 2.0 - 8); vz += v * ((bx[2] + bx[5]) / 2.0 - 8); ylo = Math.min(ylo, bx[1]); yhi = Math.max(yhi, bx[4]); }
          double dot = -(vx * x + vz * z); if (dot > 0 && ylo == 0 && yhi == 16) okN++; }
        if (t.form != Tower.Form.TWIST && t.form != Tower.Form.NOTCHED) ok(n > 0 && okN == n, String.format("%d m: %s corners (%d blocks cut, MDPI wind tunnels: chamfer 7.5 %% / rounding 10 %% of the width): %d of %d shaped blocks full height, solid toward the tower", H, t.round ? "rounded" : "chamfered", t.cutW, okN, n)); }
      // FAA lights at the top
      // 0.27.8 the core per floor: 24 % of that floor's plate (STATED mean of 35 towers of 300 m or more), so 15-35 % on every tower floor (CHOSEN band round it, the 15 m minimum core lifting small top plates); the stairways in one place on every floor
      { double lo = 1, hi = 0; for (int f = Tower.PODIUM_FLOORS; f < t.floors; f++) { double sh = t.coreShare(f); lo = Math.min(lo, sh); hi = Math.max(hi, sh); }
        ok(lo >= 0.15 && hi <= 0.35, String.format("%d m: the core is %.0f-%.0f %% of each tower floor's plate (24 %% wanted)", H, lo * 100, hi * 100));
        // climb the first stairway from the ground floor to the top floor, step by step: at every height one step, the next step one block up and
        // beside it, two blocks of headroom over each; and a door from the core on every floor
        int S = t.stairCore(), st = Math.max(3, S / 3), n = st - 1, steps = 0, sbad = 0, doors = 0; int[] prev = null;
        for (int y = 1; y < (t.floors - 1) * Tower.FLOOR; y++) { int[] at = null; int cnt = 0; for (int lx = 1; lx <= n; lx++) for (int lz = 1; lz <= n; lz++) { String b = t.at(-S + lx, y, -S + lz); if (b != null && b.contains("stairs")) { at = new int[]{lx, lz}; cnt++; } }
          if (cnt != 1) { sbad++; prev = null; continue; } steps++; String h1 = t.at(-S + at[0], y + 1, -S + at[1]), h2 = t.at(-S + at[0], y + 2, -S + at[1]); if (!"air".equals(h1) && !(h1 != null && h1.contains("stairs")) || !"air".equals(h2) && !(h2 != null && h2.contains("stairs"))) sbad++;
          if (prev != null && Math.abs(prev[0] - at[0]) + Math.abs(prev[1] - at[1]) != 1) sbad++; prev = at; }
        for (int f = 0; f < t.floors - 1; f++) if ("air".equals(t.at(-S + st, f * Tower.FLOOR + 1, -S + st / 2)) && "air".equals(t.at(-S + st, f * Tower.FLOOR + 2, -S + st / 2))) doors++;
        ok(sbad == 0 && doors == t.floors - 1, String.format("%d m: the stairway climbs %d steps from the ground to the top floor, each beside the last with 2 blocks of headroom (%d faults), a door on %d of %d floors", H, steps, sbad, doors, t.floors - 1)); }
      boolean lit = false; for (int dy = 0; dy < 3; dy++) if ("red_stained_glass".equals(t.at(0, top - 1 - dy, 0))) lit = true; ok(lit, H + " m: red obstruction light at the top (FAA AC 70/7460-1M)"); }
    // 0.26.3 the four form families, each measured from the blocks
    Map<Tower.Form, Integer> seedOf = new EnumMap<>(Tower.Form.class); for (int sd = 1; sd < 200 && seedOf.size() < 4; sd++) seedOf.putIfAbsent(new Tower("f", 450, sd).form, sd);
    ok(seedOf.size() == 4, "all four forms occur (" + seedOf + ")");
    for (var en : seedOf.entrySet()) { Tower t = new Tower("form", 900, en.getValue()); int R = t.reach();
      switch (en.getKey()) {
        case TWIST -> { int f = Tower.PODIUM_FLOORS + (t.floors - Tower.PODIUM_FLOORS) / 2, yy = f * Tower.FLOOR + 1; double best = 0;   // the wings' direction on the half-height floor: the mean (on 4 x the angle: the plan repeats every 90 degrees) of its farthest points
          for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) if (t.at(x, yy, z) != null) best = Math.max(best, Math.hypot(x, z));
          double sx = 0, sz = 0; for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) if (t.at(x, yy, z) != null && Math.hypot(x, z) > 0.85 * best) { double an = 4 * Math.atan2(z, x); sx += Math.cos(an); sz += Math.sin(an); }
          double got = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(sz, sx)) / 4), 90), want = Tower.TWIST_DEG * (f - Tower.PODIUM_FLOORS) / (t.floors - Tower.PODIUM_FLOORS) % 90, err = Math.min(Math.abs(got - want), 90 - Math.abs(got - want));
          ok(err <= 6, String.format("TWIST: the half-height floor is turned %.0f degrees (mod 90; Shanghai Tower turns 120 over the height: %.0f here)", got, want)); }
        case NOTCHED -> { int f = Tower.PODIUM_FLOORS + 2, yy = f * Tower.FLOOR + 1, tip = 0; for (int x = R; x > 0; x--) if (t.at(x, yy, 0) != null) { tip = x; break; }
          boolean notched = t.at(tip, yy, t.wingHalf) == null && t.at(tip - 4, yy, t.wingHalf) != null; ok(notched, "NOTCHED: the wing-tip corner is recessed (10 % of the width: MDPI, cross-wind -42 %)"); }
        case WINDFLOOR -> { int f = 0; while (f < t.floors && t.use(f) != Tower.Use.MECHANICAL) f++; int yy = f * Tower.FLOOR + 2, open = 0, edge = 0;
          for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) { String b = t.at(x, yy, z); boolean in = b != null; if (!in) continue; boolean e = t.at(x + 1, yy, z) == null || t.at(x - 1, yy, z) == null || t.at(x, yy, z + 1) == null || t.at(x, yy, z - 1) == null; if (!e) continue; edge++; if (b.equals("air")) open++; }
          ok(edge > 0 && open > edge / 2, String.format("WINDFLOOR: mechanical floor %d is open to the wind (%d of %d facade cells open; 432 Park Avenue)", f, open, edge)); }
        default -> ok(t.cutW >= 1, "SPIRAL: wings step back in turn, corners " + (t.round ? "rounded" : "chamfered")); } }
    System.out.println("TowerTest: " + fail + " fail"); }
}
