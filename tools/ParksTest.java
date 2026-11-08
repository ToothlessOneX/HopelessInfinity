import com.hopeless.infinity.planet.*; import java.util.*;
/** ParksTest: 0.28.0 the parks and the megatalls' plazas (planet/Parks) in the seed-1 megacities, measured from the blocks Metro.chunk
 *  draws: every block string real, each park with its fountain (or a megatall's pond), trees, lamps, benches and bins; each megatall with
 *  its own park and a plaza with two pools and three flagpoles; parks graded level with the streets (Metro.at kind 5 = the street pad).
 *  Saved tool, part of the suite. */
public class ParksTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) throws Exception { PlanetWorld w = new PlanetWorld(1); w.playerSpawn(); Metro m = w.metro(); Metro.Plan p0 = null; for (Metro.Plan q : m.plans.values()) if (q.face == 0 && w.spawnKind.contains(q.site.name)) p0 = q; final Metro.Plan p = p0;
    int parks = 0, axis = 0, mega = 0; for (var e : p.cells.entrySet()) { if (e.getValue() == Metro.Cell.PARK) parks++; if (e.getValue() == Metro.Cell.AXIS) axis++; } for (Metro.Lot l : p.towers) if (l.height >= Parks.MEGATALL_M) mega++;
    System.out.printf("  %s: %d park superblocks (%d a megatall's own), %d on the axis, %d megatall towers%n", p.site.name, parks, p.ownedParks.size(), axis, mega);
    ok(p.ownedParks.size() >= 1 && p.ownedParks.size() <= mega, String.format("some megatalls have their own park (owner: \"some containing large parks\"): %d of %d, the tallest first where a free superblock is beside them", p.ownedParks.size(), mega));
    // draw one ordinary park and one owned park through Metro.chunk; count what they hold
    Long ordinary = null, owned = null; for (var e : p.cells.entrySet()) { if (e.getValue() != Metro.Cell.PARK) continue; if (p.ownedParks.contains(e.getKey())) { if (owned == null) owned = e.getKey(); } else if (ordinary == null) ordinary = e.getKey(); }
    Set<String> kinds = new TreeSet<>(); long t0 = System.nanoTime(); int chunks = 0;
    for (Long k : new Long[]{ordinary, owned}) { if (k == null) continue; int i = (int) (k >> 32), j = (int) (long) k; Map<String, Integer> c = new TreeMap<>(); double[] lo = p.xz(i * Metro.PITCH_M - Metro.BLOCK_M / 2, j * Metro.PITCH_M - Metro.BLOCK_M / 2), hi = p.xz(i * Metro.PITCH_M + Metro.BLOCK_M / 2, j * Metro.PITCH_M + Metro.BLOCK_M / 2);
      for (int x0 = Math.floorDiv((int) Math.min(lo[0], hi[0]), 16) * 16; x0 < Math.max(lo[0], hi[0]); x0 += 16) for (int z0 = Math.floorDiv((int) Math.min(lo[1], hi[1]), 16) * 16; z0 < Math.max(lo[1], hi[1]); z0 += 16) { chunks++;
        m.chunk(p.face, x0, z0, x0 + 16, z0 + 16, w.cube.spherePoint(p.face, x0 + 8, z0 + 8), (x, y, z, b) -> { double[] uv = p.uv(x + 0.5, z + 0.5); if ((int) Math.floor(uv[0] / Metro.PITCH_M + 0.5) != i || (int) Math.floor(uv[1] / Metro.PITCH_M + 0.5) != j) return; c.merge(b.replaceAll("\\[.*", "").replace("hopelessinfinity:", ""), 1, Integer::sum); kinds.add(b); }); }
      boolean own = k.equals(owned); System.out.println("  " + (own ? "owned" : "ordinary") + " park: " + c);
      ok(c.getOrDefault("water", 0) > (own ? 1000 : 100) && c.getOrDefault("street_tree", 0) > 150 && c.getOrDefault("street_lamp", 0) >= 20 && c.getOrDefault("bench", 0) >= 20 && c.getOrDefault("litter_bin", 0) >= 20 && c.getOrDefault("park_path", 0) > 2000,
        String.format("the %s park holds its %s (%d water), %d trees, %d lamps, %d benches, %d bins, %d path blocks", own ? "megatall's" : "ordinary", own ? "pond" : "fountain", c.getOrDefault("water", 0), c.getOrDefault("street_tree", 0), c.getOrDefault("street_lamp", 0), c.getOrDefault("bench", 0), c.getOrDefault("litter_bin", 0), c.getOrDefault("park_path", 0))); }
    // the HQ (a megatall): its plaza's pools and flagpoles
    { Metro.Lot hq = p.towers.get(0); Tower t = hq.tower(); int ph = t.podiumHalf; Map<String, Integer> c = new TreeMap<>(); double[] lo = p.xz(hq.u - 40, hq.v - ph - 30), hi = p.xz(hq.u + 40, hq.v - ph);
      for (int x0 = Math.floorDiv((int) Math.min(lo[0], hi[0]), 16) * 16; x0 < Math.max(lo[0], hi[0]); x0 += 16) for (int z0 = Math.floorDiv((int) Math.min(lo[1], hi[1]), 16) * 16; z0 < Math.max(lo[1], hi[1]); z0 += 16)
        m.chunk(p.face, x0, z0, x0 + 16, z0 + 16, w.cube.spherePoint(p.face, x0 + 8, z0 + 8), (x, y, z, b) -> { double[] uv = p.uv(x + 0.5, z + 0.5); double lx = uv[0] - hq.u, lz = uv[1] - hq.v; if (Math.abs(lx) > 40 || lz < -ph - 30 || lz >= -ph) return; c.merge(b.replaceAll("\\[.*", ""), 1, Integer::sum); kinds.add(b); });
      ok(c.getOrDefault("water", 0) >= 2 * 9 * 19 && c.getOrDefault("iron_bars", 0) == 36 && c.getOrDefault("blue_banner", 0) + c.getOrDefault("white_banner", 0) == 3, String.format("the HQ's forecourt: two reflecting pools (%d water), three 12 m flagpoles (%d pole blocks, %d banners)", c.getOrDefault("water", 0), c.getOrDefault("iron_bars", 0), c.getOrDefault("blue_banner", 0) + c.getOrDefault("white_banner", 0))); }
    List<String> bad = new ArrayList<>(); for (String b : kinds) { String why = BlockStates.bad(b); if (why != null) bad.add(b + " (" + why + ")"); } ok(bad.isEmpty(), "every block string is real (" + kinds.size() + " kinds)" + (bad.isEmpty() ? "" : ": " + bad));
    // graded level with the streets
    { int n = 0, off = 0; for (var e : p.cells.entrySet()) { if (e.getValue() != Metro.Cell.PARK && e.getValue() != Metro.Cell.AXIS) continue; int i = (int) (e.getKey() >> 32), j = (int) (long) (long) e.getKey(); double u = i * Metro.PITCH_M + 50, v = j * Metro.PITCH_M - 30; double[] xz = p.xz(u, v);
        int[] at = m.at(p.face, (int) Math.floor(xz[0]), (int) Math.floor(xz[1]), w.cube.spherePoint(p.face, xz[0], xz[1])); n++; if (at == null || at[1] != 5 || at[0] != m.streetPad(p, Math.floor(xz[0]) + 0.5 - p.cx, Math.floor(xz[1]) + 0.5 - p.cz)) off++; }
      ok(n > 0 && off == 0, String.format("%d park superblocks graded as parks, level with the streets (%d not)", n, off)); }
    ok((System.nanoTime() - t0) / 1e6 / Math.max(1, chunks) < 60, String.format("park chunks in %.1f ms each", (System.nanoTime() - t0) / 1e6 / Math.max(1, chunks)));
    System.out.println("ParksTest: " + fail + " fail"); } }
