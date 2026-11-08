import com.hopeless.infinity.planet.*; import java.util.*;
/** TownView seed out-prefix: top-down block views (1 px = 1 block, from Column.of as the chunk generator lays it) of the spawn town, a town
 *  of about 2,000, a small city and a spaceport, with each plat's numbers. Saved tool (uses RoadView's renderer). */
public class TownView {
  public static void main(String[] a) throws Exception { PlanetWorld w = new PlanetWorld(Long.parseLong(a[0])); Sites st = w.sites(); Towns tw = w.towns(); System.out.println(st.probe());
    System.out.printf("spaceports wanted %d, made %d; turned down: no open sea east %d, city near %d, no shore %d, no flat dry ground %d, place in hazard distance %d%n", st.spaceportWant, st.count[Sites.Kind.SPACEPORT.ordinal()], st.spaceportMiss[0], st.spaceportMiss[1], st.spaceportMiss[2], st.spaceportMiss[3], st.spaceportMiss[4]);
    Sites.Site mid = null, city = null, port = null; for (Sites.Site s : st.all) { if (mid == null && s.kind == Sites.Kind.RURAL_TOWN && s.pop > 1800 && s.pop < 2500) mid = s; if (city == null && s.kind == Sites.Kind.SMALL_CITY && s.pop > 9000 && s.pop < 15000) city = s; if (port == null && s.kind == Sites.Kind.SPACEPORT) port = s; }
    Object[][] todo = {{st.spawnTown, "spawn"}, {mid, "town"}, {city, "city"}, {port, "spaceport"}};
    for (Object[] td : todo) { Sites.Site s = (Sites.Site) td[0]; if (s == null) { System.out.println("no " + td[1]); continue; } long t0 = System.nanoTime(); Towns.Plan p = tw.plan(s);
      System.out.printf("%s: %s: %d housing units, %d house lots, %d stores, %d churches, %d schools, %d blocks, extent %.0f m, planned in %d ms %s%n", td[1], s, p.units, p.houses, p.stores, p.churches, p.schools, p.blocksUsed, p.extentM, p.ms, p.note);
      int N = (int) Math.min(4000, Math.max(400, 2 * p.extentM + 200)); double[] cen = s.kind == Sites.Kind.SPACEPORT ? tw.pointAt(p, (p.zones.get(0)[0]) / 2, 0) : p.c; if (s.kind == Sites.Kind.SPACEPORT) N = 4400;
      RoadView.view(w, cen, s.name + ": " + s.kind.name().toLowerCase().replace('_', ' ') + ", " + String.format("%,d", s.pop) + " people", a[1] + "-" + td[1] + ".png", N); }
    System.out.println(tw.probe()); } }
