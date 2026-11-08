import com.hopeless.infinity.planet.*; import java.util.*;
/** BusinessesTest: 0.27.4 who keeps each Main Street store (planet/Businesses) on seed 1, measured from the plats and the blocks the
 *  buildings stage draws: the chain share (40 %, Census-derived) where the town is big enough, every chain only where it may be and no more
 *  often than its cap, the same answer twice, and each chain store's sign drawn on its sign band with valid text. Saved tool, part of the suite. */
public class BusinessesTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) throws Exception {
    PlanetWorld w = new PlanetWorld(1); Sites st = w.sites(); Towns tw = w.towns();
    // a sample of towns of every size (the 3 largest platted places and every 400th place)
    List<Sites.Site> pick = new ArrayList<>(); List<Sites.Site> plat = new ArrayList<>(); for (Sites.Site s : st.all) if (s.kind != Sites.Kind.LARGE_CITY && s.kind != Sites.Kind.MEGACITY && s.kind != Sites.Kind.SPACEPORT) plat.add(s);
    plat.sort((x, y) -> Long.compare(y.pop, x.pop)); for (int i = 0; i < 3; i++) pick.add(plat.get(i)); for (int i = 3; i < plat.size(); i += 400) pick.add(plat.get(i));
    w.roads();   // 0.27.7 the road network (and the megacity plans it now makes first, about 1 s) before the clock: this times the plats and businesses only
    long t0 = System.nanoTime(); int lots = 0, chains = 0, wantSum = 0, overCap = 0, underPop = 0, notStreet = 0; Map<String, Integer> seen = new TreeMap<>();
    for (Sites.Site s : pick) { Towns.Plan p = tw.plan(s); Map<Long, Businesses.Chain> m = Businesses.of(p); int n = Businesses.storeLots(p).size(); lots += n; chains += m.size();
      int can = 0; for (Businesses.Chain ch : Businesses.CHAINS) if (ch.street() && s.pop >= ch.minPop()) can++; int cap = Businesses.cap(s.pop, n, can); wantSum += Math.min((int) Math.round(Businesses.CHAIN_SHARE * n), can * cap);
      Map<String, Integer> per = new HashMap<>(); for (Businesses.Chain ch : m.values()) { per.merge(ch.name(), 1, Integer::sum); seen.merge(ch.name(), 1, Integer::sum); if (s.pop < ch.minPop()) underPop++; if (!ch.street()) notStreet++; }
      for (int v : per.values()) if (v > cap) overCap++; }
    double ms = (System.nanoTime() - t0) / 1e6;
    System.out.printf("  %d towns, %d store lots, %d chain stores (%.1f %%), %d chains seen: %s%n", pick.size(), lots, chains, 100.0 * chains / Math.max(1, lots), seen.size(), seen);
    ok(chains == wantSum, String.format("chain stores %d = 40 %% of each town's store lots, or every chain the town can hold (%d)", chains, wantSum));
    ok(underPop == 0 && notStreet == 0 && overCap == 0, String.format("chains only where the town is big enough (%d too small), only street businesses (%d), at most their cap each (%d over)", underPop, notStreet, overCap));
    { Sites.Site big = pick.get(0); Towns.Plan bp = tw.plan(big); int bn = Businesses.storeLots(bp).size(), bc = Businesses.of(bp).size(); ok(Math.abs(bc - Math.round(Businesses.CHAIN_SHARE * bn)) <= 1, String.format("the largest town (%s, pop %,d): %d chain stores of %d store lots (%.1f %%; owner: more stores per chain, so big towns reach 40 %%)", big.name, big.pop, bc, bn, 100.0 * bc / bn)); }
    ok(seen.size() >= 20, "most street chains appear somewhere in the sample (" + seen.size() + " of " + Businesses.CHAINS.stream().filter(Businesses.Chain::street).count() + ")");
    // the same answer when made again
    { Sites.Site s = pick.get(0); Towns.Plan p = tw.plan(s); Map<Long, Businesses.Chain> m1 = new HashMap<>(Businesses.of(p)); java.lang.reflect.Field f = Businesses.class.getDeclaredField("BY_SITE"); f.setAccessible(true); ((Map<?, ?>) f.get(null)).clear();
      ok(m1.equals(Businesses.of(p)), "the same chains on the same lots when made again (" + m1.size() + " in " + s.name + ")"); }
    // every sign string valid; each chain's lines spell its name
    { List<String> bad = new ArrayList<>(); for (Businesses.Chain ch : Businesses.CHAINS) { if (!ch.street()) continue; String sg = Businesses.sign(ch, "north"), why = BlockStates.bad(sg); if (why != null) bad.add(ch.name() + ": " + why);
        String joined = String.join(" ", ch.lines()).replaceAll("[^A-Z&]", ""), name = ch.name().toUpperCase().replaceAll("[^A-Z&]", ""); if (!joined.equals(name)) bad.add(ch.name() + ": lines spell " + joined); }
      ok(bad.isEmpty(), "every chain's sign is a real wall sign with 4 lines of 15 characters at most, spelling its name" + (bad.isEmpty() ? "" : ": " + bad)); }
    // the buildings stage draws one sign per chain store on the largest town's plat
    { Sites.Site s = pick.get(0); Towns.Plan p = tw.plan(s); int want = Businesses.of(p).size(); int[] signs = {0}; Set<String> names = new HashSet<>(); double e = p.extentM + 60;
      double[] lo = p.alongX ? new double[]{p.cx - e, p.cz - e} : new double[]{p.cx - e, p.cz - e}; long tb = System.nanoTime();
      for (int x0 = (int) Math.floor((p.cx - e) / 16) * 16; x0 < p.cx + e; x0 += 16) for (int z0 = (int) Math.floor((p.cz - e) / 16) * 16; z0 < p.cz + e; z0 += 16) { double[] sc = w.cube.spherePoint(p.face, x0 + 8, z0 + 8); if (sc == null) continue;
        Buildings.chunk(tw, p.face, x0, z0, x0 + 16, z0 + 16, sc, (x, y, z, b) -> { if (b.startsWith("sign:")) { signs[0]++; names.add(b.split("\\|")[3]); } }); }
      int all = Businesses.storeLots(p).size(); ok(signs[0] == all, String.format("%s (pop %,d): the buildings draw %d store signs for its %d store lots (%d chain, the rest family; %d different first lines; %.0f ms)", s.name, s.pop, signs[0], all, want, names.size(), (System.nanoTime() - tb) / 1e6)); }
    // 0.27.4 the family names: drawn by the 2010 Census counts (measured over 200,000 draws against the file's own shares)
    { int n = Businesses.surnames(); Map<String, Integer> c = new HashMap<>(); int N = 200000; for (int i = 0; i < N; i++) c.merge(Businesses.surname((i + 0.5) / N), 1, Integer::sum);
      long smith = 0, johnson = 0, total = 0; try (var r = new java.io.BufferedReader(new java.io.InputStreamReader(new java.util.zip.GZIPInputStream(new java.io.FileInputStream("src/main/resources/data/hopelessinfinity/surnames_2010.txt.gz"))))) { String ln; while ((ln = r.readLine()) != null) { if (ln.startsWith("#")) continue; String[] q = ln.split(","); if (q[0].length() + 2 > Businesses.SIGN_CHARS) continue; long v = Long.parseLong(q[1]); total += v; if (q[0].equals("SMITH")) smith = v; if (q[0].equals("JOHNSON")) johnson = v; } }
      double ws = (double) smith / total, gs = c.getOrDefault("SMITH", 0) / (double) N, wj = (double) johnson / total, gj = c.getOrDefault("JOHNSON", 0) / (double) N;
      ok(n > 40000 && Math.abs(gs - ws) < 0.0005 && Math.abs(gj - wj) < 0.0005, String.format("family surnames: %,d names; SMITH drawn %.4f (file %.4f), JOHNSON %.4f (file %.4f)", n, gs, ws, gj, wj));
      List<String> bad = new ArrayList<>(); for (int i = 0; i < 2000; i++) { String sg = Businesses.familySign(i * 0x9E3779B97F4A7C15L, "south"), why = BlockStates.bad(sg); if (why != null) { bad.add(sg + ": " + why); if (bad.size() > 3) break; } }
      ok(bad.isEmpty(), "2,000 family signs: real wall signs, every line 15 characters at most" + (bad.isEmpty() ? "" : ": " + bad)); }
    ok(ms < 30000, String.format("the sample's plats and businesses in %.0f ms (limit 30 s: measured 15.5 s alone, over 20 s inside the suite on this machine)", ms));
    System.out.println("BusinessesTest: " + fail + " fail"); }
}
