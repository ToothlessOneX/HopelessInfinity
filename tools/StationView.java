import com.hopeless.infinity.planet.*; import java.util.*;
/** StationView: 0.29.1 a town's station on seed 1 (Towns.station; Roads RAIL lines to it), a top view by RoadView from Column.of: the tracks
 *  across the head of Main Street, the street on to the depot, the lines leaving along the tracks. Args: seed out.png [lines at least, default 2]. Saved tool. */
public class StationView { public static void main(String[] a) throws Exception { PlanetWorld w = new PlanetWorld(Long.parseLong(a[0])); w.playerSpawn(); Roads rd = w.roads(); Towns tw = w.towns(); Metro m = w.metro(); int need = a.length > 2 ? Integer.parseInt(a[2]) : 2;
  Metro.Plan home = null; for (Metro.Plan q : m.plans.values()) if (q.face == 0 && w.spawnKind.contains(q.site.name)) home = q;
  Map<Integer, Integer> deg = new HashMap<>(); for (Roads.Edge e : rd.edges) if (e.cls == Roads.Cls.RAIL) { deg.merge(e.a, 1, Integer::sum); deg.merge(e.b, 1, Integer::sum); }
  for (Sites.Site s : w.sites().nearest(home.site.s, null, 400)) { if (Metro.isCity(s) || s.kind == Sites.Kind.LARGE_CITY || s.kind == Sites.Kind.MEGACITY || deg.getOrDefault(s.id, 0) < need) continue; Towns.Plan tp = tw.plan(s); if (tp == null) continue;
    for (Roads.Edge e : rd.edges) if (e.cls == Roads.Cls.RAIL && (e.a == s.id || e.b == s.id)) rd.route(e.id);
    double[] st = tw.station(tp); double[] cen = tw.sphereAt(tp, st[1] - 300, 0); int N = (int) Math.min(4000, Math.max(1600, 2 * st[2] + 1200));
    System.out.printf("%s (%s, %,d people): %d lines, tracks %.0f m past the plat's last block, plat half width %.0f m%n", s.name, s.kind, s.pop, deg.get(s.id), st[1] - st[0], st[2]);
    RoadView.view(w, cen, s.name + ": station (" + deg.get(s.id) + " main lines), " + String.format("%,d", s.pop) + " people", a[1], N); return; }
  System.out.println("no town with " + need + " lines"); } }
