import com.hopeless.infinity.planet.*; import com.hopeless.infinity.sky.Ephemeris; import com.hopeless.infinity.world.TidesLink; import com.hopeless.infinity.galaxy.Orbits;
/** TideTest: the 0.8.2 equilibrium tide from the real sky (TidesLink.equilibrium) at seed 1's spawn over 60 days: highs per lunar day,
 *  spring and neap ranges against the moons' share of the tide (springs = 1, neaps = |2 share - 1| for one moon). Saved tool. */
public class TideTest { public static void main(String[] a) { com.hopeless.infinity.planet.Roads.on = false; com.hopeless.infinity.planet.Towns.on = false; com.hopeless.infinity.planet.Metro.on = false;   /* 0.27.0 megacities are MetroTest's */   /* 0.23.0: roads are RoadsTest's; this test reads the terrain */
  PlanetWorld w = new PlanetWorld(1); Ephemeris e = new Ephemeris(w); int[] sp = w.spawn(); double share = TidesLink.tide(w)[1];
  Orbits.Elements mo = e.orb.moon[e.home][0]; double syn = 1 / (1 / mo.periodS() - 1 / e.orb.planet[e.home].periodS()), sid = e.orb.planetSpin[e.home].periodS(), lunarDay = 1 / Math.abs(1 / sid - 1 / mo.periodS());
  int highs = 0; double prev = 0, prev2 = 0, maxRange = 0, minRange = 9, hi = -9, lo = 9; double span = 60 * 86400, step = 300; java.util.List<double[]> days = new java.util.ArrayList<>(); double dayStart = 0;
  for (double t = 0; t < span; t += step) { double v = TidesLink.equilibrium(e, w, 0, sp[0], sp[2], t); if (t > step && prev > prev2 && prev > v) highs++; prev2 = prev; prev = v; hi = Math.max(hi, v); lo = Math.min(lo, v);
    if (t - dayStart >= lunarDay) { days.add(new double[]{hi - lo}); maxRange = Math.max(maxRange, hi - lo); minRange = Math.min(minRange, hi - lo); hi = -9; lo = 9; dayStart = t; } }
  double perDay = highs / (span / lunarDay);
  System.out.printf("seed 1: moons' share %.3f, lunar day %.2f h, synodic month %.2f d; highs per lunar day %.2f; daily range spring %.3f neap %.3f (ratio %.2f, expected about %.2f)%n", share, lunarDay / 3600, syn / 86400, perDay, maxRange, minRange, minRange / maxRange, Math.abs(2 * share - 1));
  boolean ok = Math.abs(perDay - 2) < 0.15 && Math.abs(minRange / maxRange - Math.abs(2 * share - 1)) < 0.2; System.out.println(ok ? "TideTest: PASS" : "TideTest: FAIL"); if (!ok) System.exit(1);
}}
