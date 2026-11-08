import com.hopeless.infinity.planet.*; import java.util.*;
/** RiverWalk: 0.29.8 walks every river segment near the spawn (seed 1) along its channel's centre (meanders applied as Rivers.at does) and
 *  measures, every 25 m: the water level against the ground there before the river (Terrain without rivers: water standing above the land
 *  is a flood); the banks (the ground just outside the channel, after the rivers' cuts) against the level (water above a bank spills out
 *  in the game); and the blocks: whether Column puts at least one water block in the channel (a channel with no water block is a dry bed).
 *  Prints the worst places with face block coordinates (for WorldView). Saved tool (not in the suite: RiversTest holds the checks). */
public class RiverWalk { public static double floodPct, spillPct, dryPct, worstFloodM; public static int samplesN;
  public static void main(String[] a) throws Exception { long t0 = System.nanoTime(); PlanetWorld w = new PlanetWorld(1); int[] sp = w.playerSpawn(); Terrain t = w.terrain; Rivers rv = t.rivers; double R = w.cube.R;
    double reachKm = a.length > 0 ? Double.parseDouble(a[0]) : 40; double[] home = w.cube.spherePoint(0, sp[0] + 0.5, sp[2] + 0.5);
    int missed = 0, sea = 0, segs = 0, samples = 0, flood = 0, spill = 0, dry = 0, wetOk = 0; double worstFlood = 0, worstSpill = 0; String wf = "", ws = ""; List<double[]> floods = new ArrayList<>();
    double[] hist = new double[8];   // level minus ground before the river: <0, 0-1, 1-2, 2-5, 5-10, 10-20, 20-50, >50 m
    for (Rivers.Seg g : rv.segs) { if (arcM(g.pa, home) * R > reachKm * 1000 && arcM(g.pb, home) * R > reachKm * 1000) continue; segs++;
      double W = Rivers.width(g.qa), len = g.len; int n = Math.max(2, (int) (len / 25));
      for (int k = 0; k <= n; k++) { double tt = k / (double) n;
        double[] c = slerp(g.pa, g.pb, tt); double[] ab = sub(g.pb, g.pa); double[] side = norm(cross(ab, c));   // the lateral direction (as Rivers.local's sign)
        double D = g.da + (g.db - g.da) * tt, lvl = g.la + (g.lb - g.la) * tt;
        double off = Math.sin(Math.PI * tt) * len * (0.08 * Math.sin(2 * Math.PI * tt + phase(rv, g.a, 1)) + 0.03 * Math.sin(6 * Math.PI * tt + phase(rv, g.a, 2))) + ends(tt) * Rivers.MEANDER_A * Rivers.MEANDER_L * W * Math.sin(2 * Math.PI * D / (Rivers.MEANDER_L * W) + phase(rv, g.a, 3));
        double[] p = at(c, side, off / R); double[] hwN = t.heightAndWater(p, R); if (Double.isNaN(hwN[1]) || hwN[2] != 1) { p = at(c, side, -off / R); hwN = t.heightAndWater(p, R); if (Double.isNaN(hwN[1]) || hwN[2] != 1) { missed++; continue; } side = new double[]{-side[0], -side[1], -side[2]}; }   // the meander side's sign: whichever puts the point in this channel
        Terrain.riversOn = false; double h0 = t.heightAndWater(p, R)[0]; Terrain.riversOn = true; if (h0 < 1) { sea++; continue; }   /* the mouth over the sea floor: the sea, not a flood */ samples++;
        double lv = hwN[1], ex = lv - h0; int b = ex < 0 ? 0 : ex < 1 ? 1 : ex < 2 ? 2 : ex < 5 ? 3 : ex < 10 ? 4 : ex < 20 ? 5 : ex < 50 ? 6 : 7; hist[b]++;
        double[] lc = w.cube.locate(p);
        if (ex > 2) { flood++; if (ex > worstFlood) { worstFlood = ex; wf = String.format("%d,%d,%d (level %.1f m, ground before %.1f m, Q %.0f m3/s)", (int) lc[0], (int) lc[1], (int) lc[2], lv, h0, g.qa); } if (floods.size() < 4000) floods.add(new double[]{ex, lc[0], lc[1], lc[2]}); }
        double worstBank = Double.POSITIVE_INFINITY; for (int sg = -1; sg <= 1; sg += 2) { double[] q = at(c, side, (off + sg * (W / 2 + 3)) / R); double[] hb = t.heightAndWater(q, R); if (!Double.isNaN(hb[1])) continue; worstBank = Math.min(worstBank, hb[0]); }
        if (worstBank < lv - 0.5) { spill++; if (lv - worstBank > worstSpill) { worstSpill = lv - worstBank; ws = String.format("%d,%d,%d (level %.1f m, bank %.1f m)", (int) lc[0], (int) lc[1], (int) lc[2], lv, worstBank); } }
        Column col = Column.of(w, (int) lc[0], (int) Math.floor(lc[1]), (int) Math.floor(lc[2])); if (col.waterY > col.surfaceY) wetOk++; else dry++; } }
    System.out.printf("%d segments within %.0f km of the spawn, %d channel samples on land (25 m apart; %d over the sea floor, %d where another water wins)%n", segs, reachKm, samples, sea, missed);
    System.out.printf("level minus ground before the river: <0 %.1f%%, 0-1 %.1f%%, 1-2 %.1f%%, 2-5 %.1f%%, 5-10 %.1f%%, 10-20 %.1f%%, 20-50 %.1f%%, >50 m %.1f%%%n", 100 * hist[0] / samples, 100 * hist[1] / samples, 100 * hist[2] / samples, 100 * hist[3] / samples, 100 * hist[4] / samples, 100 * hist[5] / samples, 100 * hist[6] / samples, 100 * hist[7] / samples);
    System.out.printf("flooding (level over 2 m above the land before the river): %d of %d (%.1f%%), worst %.1f m at %s%n", flood, samples, 100.0 * flood / samples, worstFlood, wf);
    System.out.printf("spilling (a bank under the level): %d of %d (%.1f%%), worst %.1f m at %s%n", spill, samples, 100.0 * spill / samples, worstSpill, ws);
    System.out.printf("channel centre with no water block (dry bed): %d of %d (%.1f%%)%n", dry, samples, 100.0 * dry / Math.max(1, dry + wetOk));
    floods.sort((x, y) -> Double.compare(y[0], x[0])); System.out.println("worst floods (face,x,z): "); for (int i = 0; i < Math.min(8, floods.size()); i++) System.out.printf("  %.1f m at %d,%d,%d%n", floods.get(i)[0], (int) floods.get(i)[1], (int) floods.get(i)[2], (int) floods.get(i)[3]);
    floodPct = 100.0 * flood / Math.max(1, samples); spillPct = 100.0 * spill / Math.max(1, samples); dryPct = 100.0 * dry / Math.max(1, dry + wetOk); worstFloodM = worstFlood; samplesN = samples;
    System.out.printf("level profiles made: %d in %.1f s (%.1f ms each)%n", Rivers.PROFILES.sum(), Rivers.PROFILE_NS.sum() / 1e9, Rivers.PROFILE_NS.sum() / 1e6 / Math.max(1, Rivers.PROFILES.sum()));
    System.out.printf("in %.0f s%n", (System.nanoTime() - t0) / 1e9); }
  static double phase(Rivers rv, int c, int k) { try { var m = Rivers.class.getDeclaredMethod("phase", int.class, int.class); m.setAccessible(true); return (Double) m.invoke(rv, c, k); } catch (Exception e) { throw new RuntimeException(e); } }
  static double arcM(double[] a, double[] b) { return Math.acos(Math.max(-1, Math.min(1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2]))); }
  static double ends(double t) { double k = Math.min(1, Math.min(t, 1 - t) / 0.15); return k * k * (3 - 2 * k); }
  static double[] slerp(double[] a, double[] b, double t) { return norm(new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t}); }
  static double[] at(double[] c, double[] side, double ang) { return norm(new double[]{c[0] - side[0] * ang, c[1] - side[1] * ang, c[2] - side[2] * ang}); }
  static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
  static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
  static double[] norm(double[] v) { double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); return new double[]{v[0] / l, v[1] / l, v[2] / l}; } }
