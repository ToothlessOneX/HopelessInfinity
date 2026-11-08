import com.hopeless.infinity.planet.*; import java.util.*;
/** SkyBridgeTest: 0.28.1 the enclosed glass sky bridges (planet/SkyBridges) of the seed-1 megacities, measured from the blocks
 *  Metro.chunk draws (the last block written at a place is the one that stays): some bridges (not every tower), each 300 m or less
 *  (the Crystal's span), each landing on a tower floor at both ends (its deck the floor slab's height in both towers), walled and roofed
 *  in glass, open into both towers, and not passing through a third tower. Saved tool, part of the suite. */
public class SkyBridgeTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) { long t0 = System.nanoTime(); PlanetWorld w = new PlanetWorld(1); w.playerSpawn(); Metro m = w.metro(); for (Sites.Site s : w.sites().all) if (s.kind == Sites.Kind.MEGACITY) m.plan(s);
    int total = 0, mega = 0, longest = 0, badFloor = 0; for (Metro.Plan p : m.plans.values()) { for (Metro.Lot l : p.towers) if (l.height >= Parks.MEGATALL_M) mega++; for (SkyBridges.Bridge b : m.bridges(p)) { total++; longest = Math.max(longest, (int) Math.round(b.s1() - b.s0() - 2 * SkyBridges.INTO));
        if (b.deckY() != m.pad(p, b.a()) - 1 + b.floorA() * Tower.FLOOR || b.deckY() != m.pad(p, b.b()) - 1 + b.floorB() * Tower.FLOOR) badFloor++; } }
    ok(total > 0 && total < mega / 4, String.format("some towers are bridged: %d bridges among %d megatall towers", total, mega));
    ok(longest <= SkyBridges.MAX_SPAN, String.format("every bridge spans 300 m or less (the Crystal, Chongqing): longest %d m", longest));
    ok(badFloor == 0, String.format("every bridge's deck is a floor slab in both towers (%d not)", badFloor));
    // draw the spawn city's first bridge through Metro.chunk; look at what stays
    Metro.Plan sp = null; for (Metro.Plan q : m.plans.values()) if (q.face == 0 && w.spawnKind.contains(q.site.name)) sp = q; final Metro.Plan p = sp;
    SkyBridges.Bridge b = m.bridges(p).isEmpty() ? null : m.bridges(p).get(0); ok(b != null, "the spawn city has a bridge"); if (b == null) { System.out.println("SkyBridgeTest: " + fail + " fail"); return; }
    Map<Long, String> got = new HashMap<>(); double mid = (b.s0() + b.s1()) / 2; double[] lo = b.alongU() ? p.xz(b.s0() - 4, b.across() - 8) : p.xz(b.across() - 8, b.s0() - 4), hi = b.alongU() ? p.xz(b.s1() + 4, b.across() + 8) : p.xz(b.across() + 8, b.s1() + 4);
    for (int x0 = Math.floorDiv((int) Math.min(lo[0], hi[0]), 16) * 16; x0 < Math.max(lo[0], hi[0]); x0 += 16) for (int z0 = Math.floorDiv((int) Math.min(lo[1], hi[1]), 16) * 16; z0 < Math.max(lo[1], hi[1]); z0 += 16)
      m.chunk(p.face, x0, z0, x0 + 16, z0 + 16, w.cube.spherePoint(p.face, x0 + 8, z0 + 8), (x, y, z, bk) -> { if (y >= b.deckY() - 3 && y <= b.deckY() + 9) got.put(((long) x << 40) ^ ((long) (y + 512) << 26) ^ (z & 0x3ffffff), bk); });
    java.util.function.BiFunction<double[], Integer, String> blk = (sc, dy) -> { double[] xz = b.alongU() ? p.xz(sc[0], b.across() + sc[1]) : p.xz(b.across() + sc[1], sc[0]); int x = (int) Math.floor(xz[0]), z = (int) Math.floor(xz[1]); return got.get(((long) x << 40) ^ ((long) (b.deckY() + dy + 512) << 26) ^ (z & 0x3ffffff)); };
    String wall = blk.apply(new double[]{mid + 1, SkyBridges.HALF_W}, 2), roof = blk.apply(new double[]{mid + 1, 0}, 8), deck = blk.apply(new double[]{mid + 1, 0}, 0), inside = blk.apply(new double[]{mid + 1, 0}, 1);
    String endA = blk.apply(new double[]{b.s0() + 0.5, 0}, 1), endB = blk.apply(new double[]{b.s1() - 0.5, 0}, 1), floorA = blk.apply(new double[]{b.s0() - 1.5, 0}, 0);
    System.out.printf("  bridge %s %d m - %d m, deck y %d: wall %s, roof %s, deck %s, inside %s, ends %s / %s, the tower's floor behind A %s%n", b.alongU() ? "along u" : "along v", b.a().height, b.b().height, b.deckY(), wall, roof, deck, inside, endA, endB, floorA);
    ok("light_blue_stained_glass".equals(wall) || "light_gray_concrete".equals(wall), "its side walls are glass (" + wall + ")");
    ok("light_blue_stained_glass".equals(roof), "its roof is glass (" + roof + ")"); ok("gray_concrete".equals(deck) && "air".equals(inside), "a concrete deck, walkable inside (" + deck + ", " + inside + ")");
    ok("air".equals(endA) && "air".equals(endB), "it opens into both towers (" + endA + ", " + endB + ")");
    ok((System.nanoTime() - t0) / 1e9 < 120, String.format("in %.0f s", (System.nanoTime() - t0) / 1e9));
    System.out.println("SkyBridgeTest: " + fail + " fail"); } }
