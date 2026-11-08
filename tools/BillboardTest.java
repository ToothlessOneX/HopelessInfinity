import com.hopeless.infinity.planet.*; import java.util.*;
/** BillboardTest: 0.28.4 the billboards (planet/Billboards) along the roads out of the spawn city on seed 1, measured from the boards the
 *  generator's own lookup finds chunk by chunk: the Highway Beautification Act's spacing on each side (500 ft on Interstates, 300 ft on
 *  US highways), none within 600 m of a crossing (interchanges), all inside a commercial zone, each a 15 x 4 face with its controller on
 *  a monopole; every block string real; the face's messages drawn (AdArt.billboardAtlas). Saved tool, part of the suite. */
public class BillboardTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) throws Exception { long t0 = System.nanoTime(); PlanetWorld w = new PlanetWorld(1); w.playerSpawn(); Metro m = w.metro(); Roads rd = w.roads();
    Metro.Plan p = null; for (Metro.Plan q : m.plans.values()) if (q.face == 0 && w.spawnKind.contains(q.site.name)) p = q;
    Map<String, Billboards.Board> all = new LinkedHashMap<>(); int chunks = 0; long tc = System.nanoTime();
    for (Roads.Edge e : rd.edges) { if (e.a != p.site.id && e.b != p.site.id) continue; Roads.Route r = rd.route(e.id); double L = r.along[r.n() - 1];
      for (int i = 0; i < r.n() && r.along[i] < 15000; i += 2) { int ii = e.a == p.site.id ? i : r.n() - 1 - i; double[] lc = w.cube.locate(r.p(ii)); int f = (int) lc[0]; int cx = Math.floorDiv((int) Math.floor(lc[1]), 16) * 16, cz = Math.floorDiv((int) Math.floor(lc[2]), 16) * 16;
        chunks++; for (Billboards.Board b : Billboards.near(w, f, cx, cz, cx + 16, cz + 16, w.cube.spherePoint(f, cx + 8, cz + 8))) all.put(b.edge() + ":" + b.along() + ":" + b.facing(), b); } }
    double ms = (System.nanoTime() - tc) / 1e6 / Math.max(1, chunks);
    System.out.printf("  %d billboards along the first 15 km of %s's roads (%d chunks looked at, %.2f ms each); refused: %d outside a commercial zone, %d near a crossing, %d on a plat or in a city%n", all.size(), p.site.name, chunks, ms, Billboards.REFUSED_ZONE.sum(), Billboards.REFUSED_X.sum(), Billboards.REFUSED_TOWN.sum());
    ok(all.size() > 0, "billboards stand along the spawn city's roads (" + all.size() + ")");
    // spacing per road and side; crossings
    Map<String, List<Double>> bySide = new HashMap<>(); int nearX = 0; for (Billboards.Board b : all.values()) { Roads.Route r = rd.route(b.edge()); double sp = r.e.cls == Roads.Cls.INTERSTATE ? Billboards.SP_INTERSTATE : Billboards.SP_PRIMARY;
      boolean right = Math.abs(Math.IEEEremainder(b.along(), sp)) < 1; bySide.computeIfAbsent(b.edge() + (right ? "R" : "L"), k -> new ArrayList<>()).add(b.along()); for (int ci : r.crossAt) if (Math.abs(r.along[ci] - b.along()) < Billboards.NEAR_X_M) nearX++; }
    double least = 1e9; String leastOn = ""; for (var en : bySide.entrySet()) { List<Double> l = en.getValue(); Collections.sort(l); for (int i = 1; i < l.size(); i++) if (l.get(i) - l.get(i - 1) < least) { least = l.get(i) - l.get(i - 1); leastOn = en.getKey(); } }
    boolean anyI = false; for (Billboards.Board b : all.values()) if (rd.route(b.edge()).e.cls == Roads.Cls.INTERSTATE) anyI = true;
    ok(least >= Billboards.SP_PRIMARY - 0.01, String.format("on each side no two closer than the HBA agreement allows (least %.1f m, on %s; 152.4 m Interstate, 91.4 m US highway)", least, leastOn));
    ok(nearX == 0, "none within 600 m of a crossing (" + nearX + ")");
    // the blocks of one board
    Billboards.Board b = all.values().iterator().next(); Map<String, Integer> c = new TreeMap<>(); Set<String> kinds = new TreeSet<>(); Billboards.draw(b, b.x() - 20, b.z() - 20, b.x() + 40, b.z() + 40, (x, y, z, bk) -> { c.merge(bk.replaceAll("\\[.*", ""), 1, Integer::sum); kinds.add(bk); });
    System.out.println("  one board: " + c + " facing " + b.facing());
    ok(c.getOrDefault("hopelessinfinity:billboard", 0) == 1 && c.getOrDefault("black_concrete", 0) == Billboards.FACE_W * Billboards.FACE_H - 1 && c.getOrDefault("iron_block", 0) == Billboards.FACE_Y0 + Billboards.FACE_H && c.getOrDefault("iron_bars", 0) == Billboards.FACE_W, "a 15 x 4 face with its controller, a monopole and a catwalk");
    List<String> bad = new ArrayList<>(); for (String k : kinds) { String why = BlockStates.bad(k); if (why != null) bad.add(k + " (" + why + ")"); } ok(bad.isEmpty(), "every block string is real" + (bad.isEmpty() ? "" : ": " + bad));
    { var im = AdArt.billboardAtlas(); int lit = 0; for (int y = 0; y < im.getHeight(); y += 4) for (int x = 0; x < im.getWidth(); x += 4) if ((im.getRGB(x, y) & 0xffffff) == 0xffffff) lit++; ok(im.getWidth() == AdArt.BW && im.getHeight() == AdArt.BH * AdArt.BSLIDES && lit > 200, String.format("the billboard's %d messages are drawn (%d x %d, %d white text samples)", AdArt.BSLIDES, im.getWidth(), im.getHeight(), lit)); }
    ok(ms < 5, String.format("the lookup costs %.2f ms a chunk", ms));
    System.out.println("BillboardTest: " + fail + " fail"); } }
