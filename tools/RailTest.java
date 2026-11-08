import com.hopeless.infinity.planet.*; import java.util.*;
/** RailTest: 0.29.0 the freight main lines (Roads.Cls.RAIL) on seed 1, measured from the laid lines and Column.of: the network's length
 *  against the US Class I network for the land (about 92,000 route miles, BTS 2021); the lines out of the spawn city and 20 others laid
 *  and checked against Amtrak Specification 63 (grade 1.5 %, curves of 3 degrees or less = radius 582 m, vertical curves 250 ft per %);
 *  every crossing with an Interstate grade-separated; the city ends at yards past the city's edge, none on a city's built cells; the
 *  track drawn: ties on ballast, a rail each side, none on a town plat; the cost of a line and of a column by it. Saved tool, part of the suite. */
public class RailTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) throws Exception { long t0 = System.nanoTime(); PlanetWorld w = new PlanetWorld(1); w.playerSpawn(); Roads rd = w.roads(); Metro m = w.metro(); Towns tw = w.towns(); double R = w.cube.R;
    System.out.println("  " + rd.probe());
    List<Roads.Edge> rail = new ArrayList<>(); for (Roads.Edge e : rd.edges) if (e.cls == Roads.Cls.RAIL) rail.add(e);
    ok(rail.size() > 50 && rd.lenKm[5] <= rd.railTargetKm + 1 && rd.lenKm[5] >= 0.8 * Math.min(rd.railTargetKm, rd.railMadeKm), String.format("%d main lines, %,.0f km: the Class I network for the land is %,.0f km (92,000 route miles over US land), %,.0f km made before the cut", rail.size(), rd.lenKm[5], rd.railTargetKm, rd.railMadeKm));
    Metro.Plan home = null; for (Metro.Plan q : m.plans.values()) if (q.face == 0 && w.spawnKind.contains(q.site.name)) home = q;
    List<Roads.Edge> pick = new ArrayList<>(); for (Roads.Edge e : rail) if (e.a == home.site.id || e.b == home.site.id) pick.add(e); Random rr = new Random(7); while (pick.size() < 24) { Roads.Edge e = rail.get(rr.nextInt(rail.size())); if (!pick.contains(e)) pick.add(e); }
    long tl = System.nanoTime(); double km = 0, worstG = 0, minR = 1e9, minK = 1e9; int samples = 0; String gAt = "";
    for (Roads.Edge e : pick) { Roads.Route r = rd.route(e.id); km += r.along[r.n() - 1] / 1000; minR = Math.min(minR, r.minRadiusM); minK = Math.min(minK, r.minK);
      for (int i = 8; i < r.n(); i++) { double g = Math.abs(r.z[i] - r.z[i - 8]) / (r.along[i] - r.along[i - 8]); if (g > worstG) { worstG = g; gAt = e.id + " at " + (int) r.along[i]; } } samples += r.n(); }
    double layMs = (System.nanoTime() - tl) / 1e6;
    System.out.printf("  %d lines laid (%d from %s), %.0f km, %d samples, %.0f ms (%.1f ms a km, with the roads they cross)%n", pick.size(), pick.stream().filter(e -> e.a == pick.get(0).a || e.b == pick.get(0).a).count(), home.site.name, km, samples, layMs, layMs / km);
    ok(worstG <= 0.015 + 1.0 / 64 + 1e-6, String.format("grades hold 1.5 %% (Amtrak Spec 63 s. 5.1.2): steepest %.2f %% over 64 m (one block of rounding allowed: %.2f %%), on line %s", worstG * 100, (0.015 + 1.0 / 64) * 100, gAt));
    ok(minR >= Roads.RMIN_M[5] - 1, String.format("curves of 3 degrees or less (Amtrak Spec 63 s. 4.6.3, radius 582 m): tightest %.0f m", minR));
    ok(minK >= 250 / 1.05, String.format("vertical curves 0.400 %% per 100 ft or gentler (s. 5.5.1.2, 250 ft per %%): smallest %.0f ft per %%", minK));
    // crossings with Interstates: the rail over (or under) by a clearance
    int crossings = 0, atGrade = 0; String worstX = ""; double worstDz = 1e9;
    for (Roads.Edge e : pick) { Roads.Route r = rd.route(e.id); Set<Integer> seen = new HashSet<>();
      for (Roads.Edge o : rd.edges) { if (o.cls != Roads.Cls.INTERSTATE) continue; boolean near = false; for (int c : o.cells) for (int c2 : e.cells) if (c == c2) { near = true; break; } if (!near || !seen.add(o.id)) continue; Roads.Route ro = rd.route(o.id);
        for (int i = 0; i + 1 < r.n(); i += 1) { double[] p = r.p(i); for (int j = 0; j + 1 < ro.n(); j += 2) { double[] q = ro.p(j); double d = Math.acos(Math.min(1, p[0] * q[0] + p[1] * q[1] + p[2] * q[2])) * R; if (d > 6) continue;
            double[] lc = w.cube.locate(p); if (tw.at((int) lc[0], lc[1], lc[2], p) != null) { i += 8; break; }   /* on a town plat or its band the line is not drawn (Column) */ double dz = Math.abs(r.z[i] - ro.z[j]); crossings++; if (dz < worstDz) { worstDz = dz; worstX = e.id + "/" + o.id + " at " + (int) r.along[i]; } if (dz < 5) atGrade++; i += 8; break; } } } }
    ok(atGrade == 0, String.format("no rail crosses an Interstate at grade: %d crossing samples, %d within 5 m in height (closest %.1f m, %s)", crossings, atGrade, worstDz, worstX));
    // city ends: yards past the edge; no sample on built cells
    int cityEnds = 0, onBuilt = 0; double yardMin = 1e9, yardMax = 0;
    for (Roads.Edge e : rail) for (int end = 0; end < 2; end++) { Sites.Site s = rd.st().all.get(end == 0 ? e.a : e.b); if (!Metro.isCity(s) || !pick.contains(e)) continue; Metro.Plan p = m.plan(s); Roads.Route r = rd.route(e.id); cityEnds++;
      double[] q = r.p(end == 0 ? 0 : r.n() - 1); double[] cq = m.centre(p); double d = Math.acos(Math.min(1, q[0] * cq[0] + q[1] * cq[1] + q[2] * cq[2])) * R; yardMin = Math.min(yardMin, d - p.reachM); yardMax = Math.max(yardMax, d - p.reachM);
      for (int i = 0; i < r.n(); i++) if (m.built(p, r.p(i), 0)) onBuilt++; }
    ok(cityEnds > 0 && onBuilt == 0 && yardMin > 0, String.format("%d city ends at yards %.0f to %.0f m past the city's edge; %d rail samples on a city's built cells", cityEnds, yardMin, yardMax, onBuilt));
    // the track drawn, and none on a plat
    Roads.Route r0 = rd.route(pick.get(0).id); Map<String, Integer> top = new TreeMap<>(); int over = 0, cols = 0, onPlat = 0, platTrack = 0; long tc = System.nanoTime();
    for (int i = 40; i < r0.n() - 40; i += 25) { if (r0.kind[i] != 0) continue; double[] lc = w.cube.locate(r0.p(i)); int f = (int) lc[0]; double ang = Math.atan2(r0.p(i + 1)[0] - r0.p(i)[0], r0.p(i + 1)[2] - r0.p(i)[2]);
      double[] c2 = w.cube.locate(r0.p(i + 1)); double dx = c2[1] - lc[1], dz = c2[2] - lc[2], L = Math.hypot(dx, dz); dx /= L; dz /= L;
      for (int k = -5; k <= 5; k++) { int x = (int) Math.floor(lc[1] - dz * k), z = (int) Math.floor(lc[2] + dx * k); Column c = Column.of(w, f, x, z); cols++; top.merge(c.top.name(), 1, Integer::sum); if (c.ovM != null) for (Column.Mat mm : c.ovM) if (mm == Column.Mat.RAIL_TOP) over++;
        Towns.Hit th = tw.at(f, x + 0.5, z + 0.5, w.cube.spherePoint(f, x + 0.5, z + 0.5)); if (th != null && th.kind != Towns.Kind.SLOPE) { onPlat++; if (c.top == Column.Mat.TIE) platTrack++; } } }
    double colUs = (System.nanoTime() - tc) / 1e3 / Math.max(1, cols); System.out.println("  across line " + pick.get(0).id + ": tops " + top + ", rails " + over);
    ok(top.getOrDefault("TIE", 0) > 20 && top.getOrDefault("GRAVEL", 0) > 40 && over > 30, String.format("the track: ties (%d) on ballast (%d gravel), a rail each side of the middle tie (%d rail blocks)", top.getOrDefault("TIE", 0), top.getOrDefault("GRAVEL", 0), over));
    ok(platTrack == 0, "no track on a town plat (" + platTrack + " of " + onPlat + " plat columns by the line)");
    // 0.29.1 town ends at the station: the end of each line at a town lies on its tracks at the depot, past the plat; two lines meet there
    int townEnds = 0, atDepot = 0, onPlatEnd = 0; double worstD = 0; Map<Integer, List<double[]>> byTown = new HashMap<>();
    for (Roads.Edge e : pick) for (int end = 0; end < 2; end++) { Sites.Site s = rd.st().all.get(end == 0 ? e.a : e.b); if (Metro.isCity(s) || s.kind == Sites.Kind.LARGE_CITY || s.kind == Sites.Kind.MEGACITY) continue; Towns.Plan tp = tw.plan(s); if (tp == null) continue;
      Roads.Route r = rd.route(e.id); double[] q = r.p(end == 0 ? 0 : r.n() - 1); double[] dep = tw.sphereAt(tp, tw.station(tp)[1], 0); townEnds++; double d = Math.acos(Math.min(1, q[0] * dep[0] + q[1] * dep[1] + q[2] * dep[2])) * R; worstD = Math.max(worstD, d); if (d < 10) atDepot++;
      byTown.computeIfAbsent(s.id, k -> new ArrayList<>()).add(q); for (int i = 0; i < r.n(); i++) if (tw.onPlat(tp, r.p(i))) { onPlatEnd++; break; } }
    ok(townEnds > 0 && atDepot == townEnds && onPlatEnd == 0, String.format("%d town ends at their stations (farthest %.1f m from the depot point); %d lines over their town's plat; %d stations where 2 or more lines meet", townEnds, worstD, onPlatEnd, byTown.values().stream().filter(l -> l.size() > 1).count()));
    ok(layMs / km < 400, String.format("laying costs %.1f ms a km", layMs / km));
    System.out.printf("  in %.0f s%n", (System.nanoTime() - t0) / 1e9); System.out.println("RailTest: " + fail + " fail"); } }
