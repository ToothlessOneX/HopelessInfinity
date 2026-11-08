import com.hopeless.infinity.planet.*; import java.util.*;
/** FusionTest: 0.28.3 the fusion plants (planet/FusionPlant) of the seed-1 cities, measured from the plans and the blocks Metro.chunk
 *  draws: units sized to the city (US 2023 use per person, 93 % capacity factor, 400 MW ARC units), one connected group of dry
 *  superblocks touching the city, six units a superblock, each with its vaulted reactor hall (fitted shaped blocks), cryostat and
 *  magnets, turbine hall and switchyard; graded level (Metro.at kind 6); every block string real. Saved tool, part of the suite. */
public class FusionTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) throws Exception { long t0 = System.nanoTime(); PlanetWorld w = new PlanetWorld(1); w.playerSpawn(); Metro m = w.metro();
    Metro.Plan sp = null; for (Metro.Plan q : m.plans.values()) if (q.face == 0 && w.spawnKind.contains(q.site.name)) sp = q; final Metro.Plan p = sp;
    double kw = FusionPlant.KW_PER_PERSON; int want = (int) Math.ceil(p.site.pop * kw / 0.93 / 400000);
    ok(Math.abs(kw - 1.368) < 0.001 && p.fusionUnits == want, String.format("%s (%,d people): %.3f kW a person (EIA 4,014 TWh / 334.9 M), %d units of 400 MW at 93 %% (%,.0f MW to the grid for %,.0f MW used)", p.site.name, p.site.pop, kw, p.fusionUnits, p.fusionUnits * 400 * 0.93, p.site.pop * kw / 1000));
    int cells = p.fusionCells.size(), needC = (int) Math.ceil(want / 6.0); Set<Long> fs = new HashSet<>(p.fusionCells);
    // one connected group, touching the city
    Set<Long> seen = new HashSet<>(); Deque<Long> dq = new ArrayDeque<>(); if (!p.fusionCells.isEmpty()) { dq.add(p.fusionCells.get(0)); seen.add(p.fusionCells.get(0)); }
    while (!dq.isEmpty()) { long k = dq.poll(); int i = (int) (k >> 32), j = (int) (long) k; for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { long nk = Metro.key(i + d[0], j + d[1]); if (fs.contains(nk) && seen.add(nk)) dq.add(nk); } }
    boolean touches = false; for (long k : fs) { int i = (int) (k >> 32), j = (int) (long) k; for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { Metro.Cell c = p.cells.get(Metro.key(i + d[0], j + d[1])); if (c == Metro.Cell.TOWER || c == Metro.Cell.TOWER_BIG || c == Metro.Cell.PARK || c == Metro.Cell.AXIS) touches = true; } }
    ok(cells == needC && seen.size() == cells && touches, String.format("the plant takes %d superblocks (%d wanted, 6 units each), one connected group (%d reached), touching the city (%b)", cells, needC, seen.size(), touches));
    // draw the first superblock: six units
    long k0 = p.fusionCells.get(0); int i0 = (int) (k0 >> 32), j0 = (int) (long) k0; Map<String, Integer> c = new TreeMap<>(); Set<String> kinds = new TreeSet<>(); int[] cryo = {0}; long tc = System.nanoTime(); int chunks = 0;
    double[] lo = p.xz(i0 * Metro.PITCH_M - 108, j0 * Metro.PITCH_M - 108), hi = p.xz(i0 * Metro.PITCH_M + 108, j0 * Metro.PITCH_M + 108);
    for (int x0 = Math.floorDiv((int) Math.min(lo[0], hi[0]), 16) * 16; x0 < Math.max(lo[0], hi[0]); x0 += 16) for (int z0 = Math.floorDiv((int) Math.min(lo[1], hi[1]), 16) * 16; z0 < Math.max(lo[1], hi[1]); z0 += 16) { chunks++;
      m.chunk(p.face, x0, z0, x0 + 16, z0 + 16, w.cube.spherePoint(p.face, x0 + 8, z0 + 8), (x, y, z, b) -> { double[] uv = p.uv(x + 0.5, z + 0.5); if ((int) Math.floor(uv[0] / Metro.PITCH_M + 0.5) != i0 || (int) Math.floor(uv[1] / Metro.PITCH_M + 0.5) != j0) return; kinds.add(b); c.merge(b.startsWith("shape:") ? "shape" : b.replaceAll("\\[.*", ""), 1, Integer::sum); if (b.equals("light_blue_concrete")) cryo[0]++; }); }
    System.out.println("  first plant superblock: " + c);
    int units = Math.min(6, p.fusionUnits); ok(c.getOrDefault("shape", 0) > units * 300 && c.getOrDefault("smooth_quartz", 0) > units * 300 && cryo[0] > units * 80 && c.getOrDefault("light_blue_stained_glass", 0) > units * 100,
      String.format("%d units drawn: %d fitted vault shapes, %d whole vault blocks, %d magnet blocks, %d window blocks", units, c.getOrDefault("shape", 0), c.getOrDefault("smooth_quartz", 0), cryo[0], c.getOrDefault("light_blue_stained_glass", 0)));
    List<String> bad = new ArrayList<>(); for (String b : kinds) { String why = BlockStates.bad(b); if (why != null) bad.add(b + " (" + why + ")"); if (bad.size() > 4) break; } ok(bad.isEmpty(), "every block string is real (" + kinds.size() + " kinds)" + (bad.isEmpty() ? "" : ": " + bad));
    { double[] xz = p.xz(i0 * Metro.PITCH_M + 10, j0 * Metro.PITCH_M - 20); int[] at = m.at(p.face, (int) Math.floor(xz[0]), (int) Math.floor(xz[1]), w.cube.spherePoint(p.face, xz[0], xz[1])); ok(at != null && at[1] == 6 && at[0] == m.streetPad(p, i0 * Metro.PITCH_M, j0 * Metro.PITCH_M), "the plant's superblock is graded level, at the street height of its centre (" + (at == null ? "null" : at[0] + ", kind " + at[1]) + ")"); }
    // every city of the plans gets a plant
    int noPlant = 0, totalU = 0, cramped = 0; for (Sites.Site s : w.sites().all) if (Metro.isCity(s)) { Metro.Plan q = m.plan(s); totalU += q.fusionUnits; if (q.towers.size() < q.wantTowers) { cramped++; continue; } if (q.fusionCells.size() * 6 < q.fusionUnits) { noPlant++; System.out.printf("  short: %s (%,d people), %d units, %d superblocks; %s%n", s.name, s.pop, q.fusionUnits, q.fusionCells.size(), q.note); } }
    ok(noPlant == 0, String.format("every city whose plan found lots for all its towers has room for its plant (%d short; %d cities on too little dry ground left out, as their towers are; %,d units, %,.0f GW in all)", noPlant, cramped, totalU, totalU * 0.4));
    ok((System.nanoTime() - tc) / 1e6 / chunks < 80, String.format("plant chunks in %.1f ms each", (System.nanoTime() - tc) / 1e6 / chunks));
    System.out.println("FusionTest: " + fail + " fail"); } }
