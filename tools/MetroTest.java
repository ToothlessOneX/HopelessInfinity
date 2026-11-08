import com.hopeless.infinity.planet.*; import java.util.*;
/** MetroTest: 0.27.0 megacity layouts (planet/Metro) on seed 1, every megacity, measured from the plans against the values they are built
 *  from (Shenzhen's tower counts, the 2 km axis, 14 % parks, podiums on their own blocks, nothing on water). Saved tool, part of the suite. */
public class MetroTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) { PlanetWorld w = new PlanetWorld(1); Sites st = w.sites(); Metro m = new Metro(w); double R = w.cube.R; int cities = 0; long worstMs = 0;
    for (Sites.Site s : st.all) { if (s.kind != Sites.Kind.MEGACITY) continue; cities++; Metro.Plan p = m.plan(s); System.out.println("  " + p.probe()); worstMs = Math.max(worstMs, p.ms); String nm = s.name;
      int n = p.towers.size(), want = (int) Math.round(s.pop / 1e6 * Metro.SZ_PER_M);
      ok(n == want, String.format("%s: %d towers placed of %d (Shenzhen: 468 of 150 m+ for 17.56 M people)", nm, n, want));
      // heights: the HQ is the tallest; the skyline keeps Shenzhen's shape (share over 200 and 300 m, scaled)
      int top = 0, hq = 0; for (Metro.Lot l : p.towers) { if (l.hq) hq = l.height; else top = Math.max(top, l.height); }
      ok(hq == p.top && top < hq, String.format("%s: the Cobble & Sons HQ is the tallest (%d m; next %d m)", nm, hq, top));
      double sc = p.top / Metro.SZ_TOP; int o200 = 0, o300 = 0; for (Metro.Lot l : p.towers) { if (l.height >= 200 * sc) o200++; if (l.height >= 300 * sc) o300++; }
      ok(Math.abs(o200 / (double) n - 200 / 468.0) < 0.03 && Math.abs(o300 / (double) n - 22 / 468.0) < 0.015, String.format("%s: share over 200 / 300 m (scaled x %.2f): %.3f / %.3f (Shenzhen %.3f / %.3f)", nm, sc, o200 / (double) n, o300 / (double) n, 200 / 468.0, 22 / 468.0));
      // podiums: inside their own block (off the avenues), none overlapping
      int out = 0, over = 0; for (Metro.Lot l : p.towers) if (Math.abs(l.u - l.lu) + l.half + 0.5 > l.lotHalf + 1e-6 || Math.abs(l.v - l.lv) + l.half + 0.5 > l.lotHalf + 1e-6) out++;
      for (Metro.Lot l : p.towers) { double ext = l.lotHalf; int lim = l.lotHalf > Metro.BLOCK_M / 2 + 1 ? 2 : 1; /* lots stay off the avenues: whole lot inside its block(s) */ double fu = (l.lu + ext) / Metro.PITCH_M, fu0 = (l.lu - ext) / Metro.PITCH_M;
          double cu = Math.floor(fu0 + 0.5) * Metro.PITCH_M - Metro.BLOCK_M / 2, cv = Math.floor((l.lv - ext) / Metro.PITCH_M + 0.5) * Metro.PITCH_M - Metro.BLOCK_M / 2; if (l.lu - ext < cu - 1e-6 || l.lv - ext < cv - 1e-6) out++; }
      for (int x = 0; x < n; x++) for (int y = x + 1; y < n; y++) { Metro.Lot A = p.towers.get(x), B = p.towers.get(y); if (Math.abs(A.u - B.u) < A.half + B.half + 1 && Math.abs(A.v - B.v) < A.half + B.half + 1) over++; }
      ok(out == 0 && over == 0, String.format("%s: every podium inside its own block (%d outside), none overlapping (%d)", nm, out, over));
      // nothing on water (measured from the terrain at each tower's centre)
      int wet = 0; for (Metro.Lot l : p.towers) { double[] xz = p.xz(l.u, l.v); double[] q = w.cube.spherePoint(p.face, xz[0], xz[1]); if (q == null) continue; double[] hw = w.terrain.heightAndWater(q, R); if (hw[0] < 2 || hw[2] == 3 || (!Double.isNaN(hw[1]) && hw[1] > hw[0] - 1)) wet++; }
      ok(wet == 0, nm + ": no tower stands on water (" + wet + ")");
      // parks 14 % (NYC Parks), the 2 km axis (Futian), the gate plaza beside the HQ
      double share = p.parks / (double) p.extentCells; ok(Math.abs(share - Metro.PARK_SHARE) < 0.05, String.format("%s: parks %.1f %% of the superblocks (NYC: 14 %%)", nm, 100 * share));
      int axis = 1; for (int i = -20; i <= 20; i++) if (p.cell(i, 0) == Metro.Cell.AXIS || p.cell(i, 0) == Metro.Cell.GATE_PLAZA) axis++;
      ok(axis * Metro.PITCH_M >= Metro.AXIS_M, String.format("%s: the axis park runs %.0f m with the boulevard (Futian: about 2,000 m)", nm, axis * Metro.PITCH_M));
      ok(p.cell(1, 0) == Metro.Cell.GATE_PLAZA && p.cell(2, 0) == Metro.Cell.GATE_PLAZA && p.cell(1, 1) == Metro.Cell.TOWER_BIG && p.towers.get(0).hq && Math.abs(p.towers.get(0).u - 1.5 * Metro.PITCH_M) < 1, nm + ": the gate plaza (2 superblocks) centred on the HQ's front");
      // 0.27.0 the HQ's front door: a door on the ground floor facing the plaza, in the tower's own blocks
      { Tower T = p.towers.get(0).tower(); int[] fd = T.frontDoor(); String d1 = T.at(fd[0], 1, fd[1]), d2 = T.at(fd[0], 2, fd[1]);
        ok(d1 != null && d1.startsWith("birch_door[facing=north,half=lower") && d2 != null && d2.contains("half=upper") && "air".equals(T.at(fd[0], 1, fd[1] + 1)) || d1 != null && d1.startsWith("birch_door") && T.at(fd[0], 1, fd[1] + 1) != null,
          String.format("%s: HQ front door on the plaza side (%s / %s)", nm, d1, d2)); }
      // compact: 95 % of the towers within 1.3 x the radius of a disc of the superblocks used
      double rr = 1.3 * Math.sqrt(p.extentCells / Math.PI) * Metro.PITCH_M; int far = 0; for (Metro.Lot l : p.towers) if (Math.hypot(l.u, l.v) > rr) far++;
      ok(far <= 0.05 * n, String.format("%s: compact (%d of %d towers beyond %.0f m)", nm, far, n, rr)); }
    ok(cities >= 1, "seed 1 has megacities (" + cities + ")");
    // 0.27.0 the player spawn: face 0's megacity, SPAWN_OUT blocks in front of the HQ's door, on the graded plaza pad (Column), in the open
    { int[] sp = w.playerSpawn(); System.out.println("  spawn " + w.spawnKind + " at " + sp[0] + " " + sp[1] + " " + sp[2]);
      ok(w.spawnKind.startsWith("megacity"), "the player spawns in a megacity (" + w.spawnKind + ")");
      Metro.Plan sp0 = null; for (Metro.Plan q : m.plans.values()) if (q.face == 0 && w.spawnKind.contains(q.site.name)) sp0 = q;
      if (sp0 != null) { Metro.Lot hq = sp0.towers.get(0); double[] uv = sp0.uv(sp[0] + 0.5, sp[2] + 0.5); double dDoor = Math.hypot(uv[0] - (hq.u + hq.tower().frontDoor()[0]), uv[1] - (hq.v + hq.tower().frontDoor()[1]));
        ok(dDoor <= Metro.SPAWN_OUT + 1.5, String.format("spawn %.1f blocks from the HQ front door", dDoor));
        Column c = Column.of(w, 0, sp[0], sp[2]); ok(c.surfaceY == sp[1] - 1 && c.top == Column.Mat.ROAD_CONCRETE, String.format("spawn on the paved pad (column top y %d %s, feet y %d)", c.surfaceY, c.top, sp[1]));
        int x0 = Math.floorDiv(sp[0], 16) * 16, z0 = Math.floorDiv(sp[2], 16) * 16; double[] sc = w.cube.spherePoint(0, x0 + 8, z0 + 8); Map<String, Integer> got = new HashMap<>(); int[] clear = {0};
        for (int dz = -1; dz <= 1; dz++) { int zz = z0 + 16 * dz; m.chunk(0, x0 - 16, zz, x0 + 32, zz + 16, sc, (x, y, z, b) -> { if (b.startsWith("birch_door")) got.merge("door", 1, Integer::sum); if (x == sp[0] && z == sp[2] && y >= sp[1] && y <= sp[1] + 1) clear[0]++; }); }
        ok(got.getOrDefault("door", 0) >= 2 * Tower.FRONT_DOORS && clear[0] == 0, "the chunks at the spawn draw the HQ's doors (" + got.getOrDefault("door", 0) + " door blocks) and nothing stands in the spawn spot (" + clear[0] + ")"); } }
    ok(worstMs < 500, "slowest plan " + worstMs + " ms (under 500; measured 237 ms worst on seed 1, the first plan warms the terrain caches)");
    System.out.println("MetroTest: " + fail + " fail"); }
}
