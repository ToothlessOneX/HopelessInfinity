import com.hopeless.infinity.planet.*; import java.util.*;
/** SitesTest: 0.22.0 settlement sites (planet/Sites) against the US record they are scaled from. Saved tool. */
public class SitesTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) { PlanetWorld w = new PlanetWorld(1); Sites st = w.sites(); Rivers rv = w.terrain.rivers; System.out.println(st.probe());
    double scale = st.habitKm2 / Sites.US_LAND_KM2; List<Sites.Site> cores = new ArrayList<>(), towns = new ArrayList<>(), labs = new ArrayList<>();
    for (Sites.Site s : st.all) { switch (s.kind) { case LARGE_CITY, MEGACITY -> cores.add(s); case SCIENCE_TOWN -> labs.add(s); default -> towns.add(s); } }
    System.out.printf("habitable land %.2f US lands%n", scale);
    ok(Math.abs(cores.size() / scale / Sites.US_CORES - 1) < 0.05, String.format("metro cores per US land %.0f (US urban areas of 50,000+: 510)", cores.size() / scale));
    ok(Math.abs(towns.size() / scale / Sites.US_TOWNS - 1) < 0.05, String.format("places under 50,000 per US land %.0f (US: 18,720)", towns.size() / scale));
    // rank-size: per continent with 20+ cores, the slope of ln rank on ln size (Gabaix 1999: -1.005)
    Map<Integer, List<Long>> pc = new HashMap<>(); for (Sites.Site s : cores) pc.computeIfAbsent(s.continent, z -> new ArrayList<>()).add(s.pop);
    double sx = 0, sy = 0, sxx = 0, sxy = 0; int m = 0, nc = 0;
    for (List<Long> l : pc.values()) { if (l.size() < 20) continue; nc++; l.sort(Comparator.reverseOrder()); double mx = 0, my = 0; for (int i = 0; i < l.size(); i++) { mx += Math.log(l.get(i)); my += Math.log(i + 1); } mx /= l.size(); my /= l.size();
      for (int i = 0; i < l.size(); i++) { double x = Math.log(l.get(i)) - mx, y = Math.log(i + 1) - my; sxx += x * x; sxy += x * y; m++; } }
    double slope = sxy / sxx; ok(Math.abs(slope + 1.005) < 0.05, String.format("rank-size slope %.3f over %d continents, %d cores (Gabaix 1999: -1.005)", slope, nc, m));
    long top = 0; for (Sites.Site s : cores) top = Math.max(top, s.pop); ok(top >= 10e6 && top < 40e6, String.format("largest city %,d (US largest urban area 19.4 M; megacity from 10 M)", top));
    int u500 = 0, u5k = 0; for (Sites.Site s : towns) { if (s.pop < 500) u500++; if (s.pop < 5000) u5k++; }
    ok(Math.abs(u500 / (double) towns.size() - Sites.TOWN_UNDER_500) < 0.02 && Math.abs(u5k / (double) towns.size() - Sites.TOWN_UNDER_5000) < 0.02,
       String.format("town sizes: %.1f %% under 500, %.1f %% under 5,000 (US places under 50,000: 43.8 %%, 79.2 %%)", 100.0 * u500 / towns.size(), 100.0 * u5k / towns.size()));
    int inRange = 0, ind = 0; for (Sites.Site s : towns) if (s.pop >= 2000 && s.pop <= 30000 && s.kind != Sites.Kind.SUBURB && s.kind != Sites.Kind.CAMPUS_TOWN) { inRange++; if (s.kind == Sites.Kind.INDUSTRIAL_TOWN) ind++; }
    double want = Sites.MANUF + Sites.MINING; ok(Math.abs(ind / (double) inRange - want) < 0.05, String.format("industrial towns %.1f %% of towns of 2,000-30,000 (US counties: manufacturing 14 %% + mining 6.4 %% = %.1f %%)", 100.0 * ind / inRange, 100 * want));
    boolean far = true; for (Sites.Site l : labs) for (Sites.Site c : cores) if (st.distM(l.s, c.s) < Sites.LAB_FAR_M) far = false;
    ok(far && Math.abs(labs.size() / scale / Sites.US_LABS - 1) < 0.25, String.format("scientific communities %d (%.1f per US land, DOE labs 17), all 150 km or more from a city", labs.size(), labs.size() / scale));
    int wet = 0; for (Sites.Site s : cores) if (st.water(s.cell) > 1) wet++; System.out.printf("cores at a coast, lake or river: %.0f %%%n", 100.0 * wet / cores.size());
    int bad = 0; for (Sites.Site s : st.all) { int k = rv.kind[s.cell]; if (s != st.spawnTown && (k == 1 || k == 2)) bad++; } ok(bad == 0, "no site's cell is sea or lake: " + bad);
    // exact spots: a sample of 2,000 sites of every kind
    Random r = new Random(4); int found = 0, dry = 0, n = 2000; long t0 = System.nanoTime();
    for (int i = 0; i < n; i++) { Sites.Site s = st.all.get(r.nextInt(st.all.size())); double[] e = st.exact(s); if (e == null) continue; found++; double[] hw = w.terrain.heightAndWater(e, w.cube.R); if (hw[0] >= 2 && (Double.isNaN(hw[1]) || hw[1] <= hw[0])) dry++; }
    ok(found >= 0.97 * n && dry == found, String.format("exact spots: %d of %d found (%.2f ms each), %d of them dry land 2 m or more over the sea", found, n, (System.nanoTime() - t0) / 1e6 / n, dry));
    int[] sp = w.spawn(); double[] so = w.cube.spherePoint(0, sp[0] + 0.5, sp[2] + 0.5); ok(st.spawnTown != null && st.distM(so, st.spawnTown.exact) < 5000, "a rural town within 5 km of the spawn (the plan's default): " + (st.spawnTown == null ? "none " + st.spawnError : String.format("%s at %.1f km", st.spawnTown, st.distM(so, st.spawnTown.exact) / 1000)));
    Sites again = new Sites(w); boolean same = again.all.size() == st.all.size(); for (int i = 0; i < Math.min(1000, st.all.size()) && same; i++) same = again.all.get(i).pop == st.all.get(i).pop && again.all.get(i).name.equals(st.all.get(i).name) && again.all.get(i).cell == st.all.get(i).cell;
    ok(same, "the same seed gives the same sites");
    ok(st.buildMs < 8000, String.format("built in %.1f s (in the background at server start)", st.buildMs / 1000.0));
    System.out.println("SitesTest: " + fail + " fail"); if (fail > 0) System.exit(1); } }
