import com.hopeless.infinity.planet.*; import java.util.*;
/** DepotTest: 0.29.3 the railroad depots (Towns depot zone, Buildings.depot) on seed 1, measured at the town ends of the main lines out of the
 *  spawn city and 20 others: each station town has its depot ground (a graded zone, Use.DEPOT); the buildings stage puts a depot on it (walls,
 *  the agent's bay toward the track, the freight dock, platform lamps); no depot block within 3.5 m of the track's centre line (the ballast's
 *  half width: the track stays clear); the platform level with the track (the zone's ground against the rail's height at the station end,
 *  within 1 block: UPRR plan 713000 allows a platform no more than 8 in over the rail); every block string real. Saved tool, part of the suite. */
public class DepotTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) throws Exception { long t0 = System.nanoTime(); PlanetWorld w = new PlanetWorld(1); w.playerSpawn(); Roads rd = w.roads(); Metro m = w.metro(); Towns tw = w.towns(); double R = w.cube.R;
    List<Roads.Edge> rail = new ArrayList<>(); for (Roads.Edge e : rd.edges) if (e.cls == Roads.Cls.RAIL) rail.add(e);
    Metro.Plan home = null; for (Metro.Plan q : m.plans.values()) if (q.face == 0 && w.spawnKind.contains(q.site.name)) home = q;
    List<Roads.Edge> pick = new ArrayList<>(); for (Roads.Edge e : rail) if (e.a == home.site.id || e.b == home.site.id) pick.add(e); Random rr = new Random(7); while (pick.size() < 24) { Roads.Edge e = rail.get(rr.nextInt(rail.size())); if (!pick.contains(e)) pick.add(e); }
    Map<Integer, Towns.Plan> towns = new LinkedHashMap<>(); Map<Integer, Double> endZ = new HashMap<>();
    for (Roads.Edge e : pick) for (int end = 0; end < 2; end++) { Sites.Site s = rd.st().all.get(end == 0 ? e.a : e.b); if (Metro.isCity(s) || s.kind == Sites.Kind.LARGE_CITY || s.kind == Sites.Kind.MEGACITY || s.kind == Sites.Kind.SPACEPORT) continue; Towns.Plan tp = tw.plan(s); if (tp == null) continue;
      towns.put(s.id, tp); Roads.Route r = rd.route(e.id); int i = end == 0 ? 0 : r.n() - 1; if (r.z[i] >= 0) endZ.put(s.id, (double) r.z[i]); }
    int withZone = 0, built = 0, clear = 0, bays = 0, docks = 0, lamps = 0, level = 0, levelN = 0; double worstStep = 0; String worstAt = ""; Set<String> kinds = new TreeSet<>(); String noDepot = "";
    for (Towns.Plan p : towns.values()) { int zi = -1; for (int k = 0; k < p.zones.size(); k++) if ((int) p.zones.get(k)[4] == Towns.Use.DEPOT.ordinal()) zi = k; if (zi < 0) { if (noDepot.isEmpty()) noDepot = p.site.name; continue; } withZone++;
      double[] zn = p.zones.get(zi); double uT = tw.station(p)[1]; int ext = (int) Math.ceil(Math.hypot(zn[2], zn[3])) + 12; double[] cxz = p.alongX ? new double[]{p.cx + zn[0], p.cz + zn[1]} : new double[]{p.cx + zn[1], p.cz + zn[0]};
      List<int[]> pts = new ArrayList<>(); List<String> bs = new ArrayList<>();
      for (int x0 = Math.floorDiv((int) cxz[0] - ext, 16) * 16; x0 < cxz[0] + ext; x0 += 16) for (int z0 = Math.floorDiv((int) cxz[1] - ext, 16) * 16; z0 < cxz[1] + ext; z0 += 16)
        Buildings.chunk(tw, p.face, x0, z0, x0 + 16, z0 + 16, w.cube.spherePoint(p.face, x0 + 8, z0 + 8), (x, y, z, b) -> { double[] uv = p.uv(x + 0.5, z + 0.5); if (Math.abs(uv[0] - zn[0]) <= zn[2] + 6 && Math.abs(uv[1] - zn[1]) <= zn[3] + 6) { pts.add(new int[]{x, y, z}); bs.add(b); } });
      boolean anyWall = false, bay = false, dock = false, inTrack = false; int lp = 0;
      for (int k = 0; k < pts.size(); k++) { int[] q = pts.get(k); String b = bs.get(k); kinds.add(b); double[] uv = p.uv(q[0] + 0.5, q[2] + 0.5); double off = uT - uv[0];
        if (b.equals("smooth_quartz")) anyWall = true; if (b.equals("glass_pane") && off < 4.6 + 2 && off > 4) bay = true; if (b.equals("spruce_planks")) dock = true; if (b.startsWith("lantern[hanging=false")) lp++;
        if (!b.equals("air") && Math.abs(off) < 3.5) inTrack = true; }
      if (anyWall) built++; if (bay) bays++; if (dock) docks++; if (lp > 0) lamps++; if (!inTrack) clear++;
      Double rz = endZ.get(p.site.id); if (rz != null) { double step = Math.abs(Terrain.toYd(rz) - Terrain.toYd(p.zoneZ.get(zi))); levelN++; if (step <= 1.0) level++; else System.out.printf("  step %.1f at %s: rail %.1f, platform %.1f%n", step, p.site.name, Terrain.toYd(rz), Terrain.toYd(p.zoneZ.get(zi))); if (step > worstStep) { worstStep = step; worstAt = p.site.name; } } }
    int n = towns.size(); System.out.printf("  %d station towns at the ends of %d lines%n", n, pick.size());
    ok(n > 5 && withZone == n, String.format("every station town has its depot ground: %d of %d%s", withZone, n, noDepot.isEmpty() ? "" : " (first without: " + noDepot + ")"));
    ok(built == withZone && bays == withZone && docks == withZone && lamps == withZone, String.format("a depot on each: walls %d, the agent's bay toward the track %d, freight dock %d, platform lamps %d, of %d", built, bays, docks, lamps, withZone));
    ok(clear == withZone, String.format("no depot block within 3.5 m of the track's centre line: %d of %d clear", clear, withZone));
    ok(levelN > 0 && level == levelN, String.format("the platform level with the track (within 1 block): %d of %d (worst step %.1f blocks, at %s)", level, levelN, worstStep, worstAt));
    List<String> bad = new ArrayList<>(); for (String k : kinds) { String why = BlockStates.bad(k); if (why != null) bad.add(k + " (" + why + ")"); } ok(bad.isEmpty(), "every block string is real (" + kinds.size() + " kinds)" + (bad.isEmpty() ? "" : ": " + bad.subList(0, Math.min(5, bad.size()))));
    System.out.printf("  in %.0f s%n", (System.nanoTime() - t0) / 1e9); System.out.println("DepotTest: " + fail + " fail"); } }
