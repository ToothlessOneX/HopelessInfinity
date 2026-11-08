import com.hopeless.infinity.planet.*; import java.util.*;
/** YardTest: 0.29.6 the freight yards at the city ends of the main lines (Roads.yard, Column), seed 1, measured on the lines out of the spawn
 *  city and 20 others: each yard level over its length (the profile pinned: within 0.5 m); across the middle of the body tracks, the drawn
 *  columns show the main and every body track (tie runs), 14 ft apart and the first 20 ft from the main (UFC 4-860-01 13.3.1; one block of
 *  rounding allowed); the ladder: at the yard's ends fewer tracks (they join the main 1 in 8); the yard clear of the city's built cells; the
 *  share of yard columns taken by another road. Saved tool, part of the suite. */
public class YardTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) throws Exception { long t0 = System.nanoTime(); PlanetWorld w = new PlanetWorld(1); w.playerSpawn(); Roads rd = w.roads(); Metro m = w.metro(); double R = w.cube.R;
    List<Roads.Edge> rail = new ArrayList<>(); for (Roads.Edge e : rd.edges) if (e.cls == Roads.Cls.RAIL) rail.add(e);
    Metro.Plan home = null; for (Metro.Plan q : m.plans.values()) if (q.face == 0 && w.spawnKind.contains(q.site.name)) home = q;
    List<Roads.Edge> pick = new ArrayList<>(); for (Roads.Edge e : rail) if (e.a == home.site.id || e.b == home.site.id) pick.add(e); Random rr = new Random(7); while (pick.size() < 24) { Roads.Edge e = rail.get(rr.nextInt(rail.size())); if (!pick.contains(e)) pick.add(e); }
    int holes = 0, yards = 0, level = 0, fullCount = 0, spacingOk = 0, ladderOk = 0, onBuilt = 0, cols = 0, lost = 0; double worstStep = 0; String worstAt = "", firstBad = "", sample = "";
    for (Roads.Edge e : pick) for (int end = 0; end < 2; end++) { if (!rd.yardAt(e, end)) continue; Roads.Route r = rd.route(e.id); int n = rd.yardTracks(e, end); double Ly = Roads.yardLen(n), Lr = r.along[r.n() - 1]; if (Lr < 2 * Ly) continue; yards++;
      if (sample.isEmpty()) sample = "rail:" + e.id + ":" + (int) (end == 0 ? Ly / 2 : Lr - Ly / 2);
      // level
      double zMin = 1e9, zMax = -1e9; for (int i = 0; i < r.n(); i++) { double u = end == 0 ? r.along[i] : Lr - r.along[i]; if (u > Ly) continue; if (r.kind[i] != 0) continue; zMin = Math.min(zMin, r.z[i]); zMax = Math.max(zMax, r.z[i]); }
      double step = zMax - zMin; if (step <= 1.0) level++; if (step > worstStep) { worstStep = step; worstAt = e.id + "/" + end; }
      // across the body's middle and near the ends: the tracks drawn (TIE runs), sampled on even and odd along to see both tie and gap
      int[] got = new int[3]; double[] us = {Ly / 2, YARD_U(n, 3), Ly - YARD_U(n, 3)}; List<Double> centres = new ArrayList<>();
      for (int q = 0; q < 3; q++) { double u = us[q]; int i0 = 0; double want = end == 0 ? u : Lr - u; for (int i = 0; i < r.n(); i++) if (Math.abs(r.along[i] - want) < Math.abs(r.along[i0] - want)) i0 = i; if (i0 + 1 >= r.n()) i0--;
        double[] p = r.p(i0), p2 = r.p(i0 + 1); double[] lc = w.cube.locate(p), lc2 = w.cube.locate(p2); int f = (int) lc[0]; double dx = lc2[1] - lc[1], dz = lc2[2] - lc[2], L = Math.hypot(dx, dz); dx /= L; dz /= L;
        double side = SIDE(rd, w, f, lc[1], lc[2], -dz, dx); List<Double> railOff = new ArrayList<>(); Set<Long> seen = new HashSet<>();
        for (double sAl = -3; sAl <= 3; sAl += 0.5) for (double k = -6; k <= Roads.yardOuter(n) + 8; k += 0.2) { int x = (int) Math.floor(lc[1] + dx * sAl - dz * k * side), z = (int) Math.floor(lc[2] + dz * sAl + dx * k * side); if (!seen.add(((long) x << 32) ^ (z & 0xffffffffL))) continue;
          Column c = Column.of(w, f, x, z); double[] sp = w.cube.spherePoint(f, x + 0.5, z + 0.5); Roads.Hit rh = null; boolean other = false; for (Roads.Hit h : rd.hits(sp)) { if (h.cls == Roads.Cls.RAIL && h.edge == e.id) rh = h; else if (h.cls != Roads.Cls.RAIL && Math.abs(h.off) < h.half()) other = true; }
          if (q == 0 && sAl == 0) { cols++; if (other) lost++; }
          if (q == 0 && c.top == Column.Mat.GRASS && rh != null && rh.off > -3 && rh.off < Roads.yardOuter(n) + 2) { int nb = 0; for (int[] d4 : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { Column cn = Column.of(w, f, x + d4[0], z + d4[1]); if (cn.top == Column.Mat.TIE || cn.top == Column.Mat.GRAVEL) nb++; } if (nb >= 3) holes++; } boolean isRail = false; if (c.ovM != null) for (Column.Mat mm : c.ovM) if (mm == Column.Mat.RAIL_TOP) isRail = true; if (isRail && rh != null) railOff.add(rh.off); }
        Collections.sort(railOff); List<Double> strips = new ArrayList<>(); int i1 = 0; while (i1 < railOff.size()) { int j1 = i1; while (j1 + 1 < railOff.size() && railOff.get(j1 + 1) - railOff.get(j1) < 0.75) j1++; double sm = 0; for (int t = i1; t <= j1; t++) sm += railOff.get(t); strips.add(sm / (j1 - i1 + 1)); i1 = j1 + 1; }
        List<Double> cs = new ArrayList<>(); for (int t = 0; t + 1 < strips.size(); ) { if (strips.get(t + 1) - strips.get(t) < 3.2) { cs.add((strips.get(t) + strips.get(t + 1)) / 2); t += 2; } else t++; }
        int best = cs.size(); if (q == 0) centres = cs; if (q == 0 && System.getenv("DBG_YARD") != null && String.valueOf(e.id).equals(System.getenv("DBG_YARD"))) System.out.println("  DBG rail offs " + railOff + "\n  strips " + strips + "\n  centres " + cs);   /* probe */
        got[q] = best; }
      if (got[0] == n + 1) fullCount++; else if (firstBad.isEmpty()) firstBad = e.id + "/" + end + ": " + got[0] + " tracks across the body (want " + (n + 1) + ")";
      boolean sp = centres.size() == n + 1; for (int k = 1; sp && k < centres.size(); k++) { double d = Math.abs(centres.get(k) - centres.get(k - 1)), want = k == 1 ? Roads.YARD_FIRST_M : Roads.YARD_SPACING_M; if (Math.abs(d - want) > 1.01) sp = false; } if (sp) spacingOk++;
      if (got[1] < got[0] && got[2] < got[0]) ladderOk++;
      Sites.Site s = rd.st().all.get(end == 0 ? e.a : e.b); if (Metro.isCity(s)) { Metro.Plan p = m.plan(s); for (int i = 0; i < r.n(); i++) { double u = end == 0 ? r.along[i] : Lr - r.along[i]; if (u <= Ly && m.built(p, r.p(i), 1)) { onBuilt++; double[] cq = m.centre(p), pq = r.p(i); System.out.printf("  yard %d/%d near %s built cells: %.0f m from its centre (reach %.0f m), u %.0f%n", e.id, end, s.name, Math.acos(Math.min(1, cq[0] * pq[0] + cq[1] * pq[1] + cq[2] * pq[2])) * R, p.reachM, u); break; } } } }
    // 0.29.6 the buildings: each yard's light towers and office placed by the structure stage, every block string real, none on a track
    int wantL = 0, gotL = 0, offices = 0, onTrack = 0; Set<String> kinds = new TreeSet<>(); String noOffice = "";
    for (Roads.Edge e : pick) for (int end = 0; end < 2; end++) { if (!rd.yardAt(e, end)) continue; Yards.Yard y = Yards.yard(w, e.id, end); if (y == null) continue; wantL += y.lamps().size(); int f = y.face();
      List<int[]> spots = new ArrayList<>(y.lamps()); if (y.office() != null) spots.add(new int[]{y.office()[0] + 2, y.office()[2], y.office()[1] - 1}); else if (noOffice.isEmpty()) noOffice = e.id + "/" + end;
      for (int k = 0; k < spots.size(); k++) { int[] sp = spots.get(k); boolean isOffice = k == y.lamps().size(); int cx = Math.floorDiv(sp[0], 16) * 16, cz = Math.floorDiv(isOffice ? sp[2] + 1 : sp[2], 16) * 16; boolean[] hit = {false};
        int[] want = isOffice ? new int[]{y.office()[0] + 2, y.office()[2], y.office()[1]} : new int[]{sp[0], sp[1] + Yards.LAMP_H, sp[2]};
        ChunkFill.structures(w, f, cx, cz, (x, yy, z, b) -> { }, (x, yy, z, b) -> { kinds.add(b); if (x == want[0] && z == want[2] && yy == want[1] && (b.equals("sea_lantern") || b.startsWith("oak_door"))) hit[0] = true; });
        if (hit[0]) { if (isOffice) offices++; else gotL++; }
        Column c = Column.of(w, f, want[0], want[2]); if (c.top == Column.Mat.TIE || (c.ovM != null && Arrays.asList(c.ovM).contains(Column.Mat.RAIL_TOP))) onTrack++; } }
    ok(wantL > 20 && gotL == wantL, String.format("light towers (high-mast, 30 blocks, every 150 m): %d of %d drawn with their lamp", gotL, wantL));
    ok(offices == yards && noOffice.isEmpty(), String.format("a yard office at each yard: %d of %d%s", offices, yards, noOffice.isEmpty() ? "" : " (none at " + noOffice + ")"));
    ok(onTrack == 0, "no tower or office door on a track (" + onTrack + ")");
    List<String> bad = new ArrayList<>(); for (String k : kinds) { String why = BlockStates.bad(k); if (why != null) bad.add(k + " (" + why + ")"); } ok(bad.isEmpty(), "every block string is real (" + kinds.size() + " kinds)" + (bad.isEmpty() ? "" : ": " + bad));
    System.out.printf("  %d yards on %d lines; sample place for WorldView: %s%n", yards, pick.size(), sample);
    ok(yards > 3 && level == yards, String.format("each yard level over its length (within 1 block): %d of %d (worst %.2f m, %s)", level, yards, worstStep, worstAt));
    ok(fullCount == yards, String.format("across the body tracks the main and every body track drawn: %d of %d%s", fullCount, yards, firstBad.isEmpty() ? "" : " (first short: " + firstBad + ")"));
    ok(spacingOk == yards, String.format("track centres 20 ft from the main, then 14 ft apart (UFC 4-860-01 13.3.1; 1 block of rounding): %d of %d", spacingOk, yards));
    ok(ladderOk == yards, String.format("ladders: fewer tracks near both ends of the yard (joining the main 1 in 8): %d of %d", ladderOk, yards));
    ok(holes == 0, "no grass holes inside the yards' track beds (" + holes + ")");
    ok(onBuilt == 0, String.format("no yard on a city's built cells or the cell next to them: %d", onBuilt));
    ok(lost <= 0.02 * Math.max(1, cols), String.format("yard columns where another road runs: %d of %d (2 %% allowed: a road under the yard on its bridge)", lost, cols));
    System.out.printf("  in %.0f s%n", (System.nanoTime() - t0) / 1e9); System.out.println("YardTest: " + fail + " fail"); }
  /** u from a yard end where track 3 has not yet left the ladder fully (half its ladder length). */
  static double YARD_U(int n, int k) { return (Roads.YARD_FIRST_M + k * Roads.YARD_SPACING_M) * Roads.YARD_FROG * 0.5; }
  /** +1 when the normal (nx, nz) points to the +off side of the rail at (ax, az), else -1. */
  static double SIDE(Roads rd, PlanetWorld w, int f, double ax, double az, double nx, double nz) { double[] s = w.cube.spherePoint(f, ax + nx * 3, az + nz * 3); for (Roads.Hit h : rd.hits(s)) if (h.cls == Roads.Cls.RAIL) return h.off > 0 ? 1 : -1; return 1; } }
