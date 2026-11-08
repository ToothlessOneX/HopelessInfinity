import com.hopeless.infinity.planet.*; import java.util.*;
/** TownGameTest: 0.28.5 the town game ported from Hopeless Deep (game/Places on planet/PlatLots; Realty, Jobs, Council) on Infinity's towns, seed 1, measured on the
 *  five largest towns near the spawn: every lot Places lists lies on that same lot by Towns.at (use, block, row, lot); lot keys unique; about one
 *  house in eight for sale (Towns.forSale); the buildings stage places a for-sale sign on each house for sale and none elsewhere, one job board
 *  at each post office and one ballot box in each town hall; every block string real. Saved tool, part of the suite. */
public class TownGameTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) throws Exception { long t0 = System.nanoTime(); PlanetWorld w = new PlanetWorld(1); w.playerSpawn(); Towns tw = w.towns(); Sites st = w.sites();
    Metro m = w.metro(); Metro.Plan city = null; for (Metro.Plan q : m.plans.values()) if (q.face == 0 && w.spawnKind.contains(q.site.name)) city = q;
    List<Sites.Site> cand = new ArrayList<>(); for (Sites.Site s : st.nearest(city.site.s, null, 400)) if (s.kind != Sites.Kind.LARGE_CITY && s.kind != Sites.Kind.MEGACITY && s.kind != Sites.Kind.SPACEPORT && !Metro.isCity(s)) cand.add(s);
    cand.removeIf(s -> s.pop > 20000); cand.sort((x, y) -> Long.compare(y.pop, x.pop));   /* towns up to 20,000 people (measured: the five largest held 111,000 lots, 107 s) */ List<Towns.Plan> towns = new ArrayList<>(); for (Sites.Site s : cand) { if (towns.size() == 5) break; Towns.Plan p = tw.plan(s); if (p != null && p.face == 0) towns.add(p); }
    ok(towns.size() == 5, "five towns with plans near the spawn (" + towns.size() + ")");
    int lots = 0, off = 0, houses = 0, sale = 0; Set<Long> keys = new HashSet<>(); int dup = 0; String firstOff = null; Set<String> kinds = new TreeSet<>(); int signs = 0, signsOk = 0, boards = 0, boxes = 0, posts = 0, halls = 0;
    for (Towns.Plan p : towns) { List<PlatLots.Lot> ls = PlatLots.lots(p);
      for (PlatLots.Lot lt : ls) { lots++; if (!keys.add(lt.key())) dup++; double[] s = w.cube.spherePoint(p.face, lt.x() + 0.5, lt.z() + 0.5); Towns.Hit h = s == null ? null : tw.at(p.face, lt.x() + 0.5, lt.z() + 0.5, s);
        boolean same = h != null && h.plan == p && h.kind == Towns.Kind.LOT && h.bi == lt.bi() && h.bj == lt.bj() && h.row == lt.row() && h.lot == lt.lot(); if (!same) { off++; if (firstOff == null) firstOff = p.site.name + " lot " + lt.bi() + "," + lt.bj() + "," + lt.row() + "," + lt.lot() + " -> " + (h == null ? "nothing" : h.kind + " " + h.bi + "," + h.bj + "," + h.row + "," + h.lot); }
        if (lt.use() == Towns.Use.HOUSE) { houses++; if (Towns.forSale(p.site.id, lt.bi(), lt.bj(), lt.row(), lt.lot())) sale++; } if (lt.use() == Towns.Use.POST_OFFICE) posts++; if (lt.use() == Towns.Use.TOWN_HALL) halls++; }
      // the buildings stage over the whole plat: the game's blocks, and which lot each stands on
      int ext = (int) Math.ceil(p.extentM) + 16, cx = (int) Math.floor(p.cx), cz = (int) Math.floor(p.cz);
      List<int[]> got = new ArrayList<>(); List<String> gb = new ArrayList<>();
      for (int x0 = Math.floorDiv(cx - ext, 16) * 16; x0 < cx + ext; x0 += 16) for (int z0 = Math.floorDiv(cz - ext, 16) * 16; z0 < cz + ext; z0 += 16)
        Buildings.chunk(tw, p.face, x0, z0, x0 + 16, z0 + 16, w.cube.spherePoint(p.face, x0 + 8, z0 + 8), (x, y, z, b) -> { kinds.add(b); if (b.contains("for_sale_sign") || b.contains("job_board") || b.contains("ballot_box")) { got.add(new int[]{x, y, z}); gb.add(b); } });
      for (int k = 0; k < got.size(); k++) { int[] q = got.get(k); String b = gb.get(k); double[] s = w.cube.spherePoint(p.face, q[0] + 0.5, q[2] + 0.5); Towns.Hit h = tw.at(p.face, q[0] + 0.5, q[2] + 0.5, s);
        if (b.contains("for_sale_sign")) { signs++; if (h != null && h.kind == Towns.Kind.LOT && h.use == Towns.Use.HOUSE && Towns.forSale(p.site.id, h.bi, h.bj, h.row, h.lot)) signsOk++; }
        else if (b.contains("job_board")) boards++; else boxes++; } }
    System.out.printf("  %d lots in %d towns (%s); %d houses, %d for sale; %d for-sale signs, %d job boards (%d post offices), %d ballot boxes (%d town halls)%n", lots, towns.size(), towns.stream().map(p -> p.site.name).reduce((x, y) -> x + ", " + y).orElse(""), houses, sale, signs, boards, posts, boxes, halls);
    ok(lots > 500 && off == 0, "every lot Places lists lies on that lot by Towns.at (" + off + " of " + lots + " not" + (firstOff == null ? "" : "; first: " + firstOff) + ")");
    ok(dup == 0, "lot keys are unique (" + dup + " repeats)");
    double share = houses == 0 ? 0 : sale / (double) houses; ok(houses > 300 && share > 0.08 && share < 0.17, String.format("about one house in eight is for sale: %d of %d (%.1f %%; 12.5 %% expected)", sale, houses, share * 100));
    ok(signs == sale && signsOk == signs, String.format("a for-sale sign on each house for sale and nowhere else (%d signs, %d on a house for sale, %d houses for sale)", signs, signsOk, sale));
    ok(boards == posts && boxes == halls && posts > 0 && halls > 0, String.format("a job board at each post office (%d of %d) and a ballot box in each town hall (%d of %d)", boards, posts, boxes, halls));
    List<String> bad = new ArrayList<>(); for (String k : kinds) { String why = BlockStates.bad(k); if (why != null) bad.add(k + " (" + why + ")"); } ok(bad.isEmpty(), "every block string is real (" + kinds.size() + " kinds)" + (bad.isEmpty() ? "" : ": " + bad.subList(0, Math.min(5, bad.size()))));
    System.out.printf("  in %.0f s%n", (System.nanoTime() - t0) / 1e9); System.out.println("TownGameTest: " + fail + " fail"); } }
