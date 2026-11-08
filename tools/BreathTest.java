import com.hopeless.infinity.galaxy.*; import com.hopeless.infinity.planet.*; import com.hopeless.infinity.world.Breath;
/** BreathTest: 0.10.0 breathing (world/Breath) against the FAA table and the seed 1 home. Saved tool. */
public class BreathTest { public static void main(String[] a) { int fail = 0;
  Rng r = new Rng(1); Atmosphere e = null; for (int k = 0; k < 400 && (e == null || Math.abs(e.pressureAtm - 1) > 0.01); k++) e = Atmosphere.of(1, 6371, 1, 1, Planet.Climate.TEMPERATE, false, 288, 4.54, r);
  // the ISA's O2 at each FAA altitude, back through Breath: the same altitude and the table's time
  for (double[] q : new double[][]{{18000, 1500}, {25000, 240}, {30000, 90}, {35000, 45}, {40000, 17.5}}) { double hm = q[0] * 0.3048, pIsa = hm <= 11000 ? Math.pow(1 - 2.25577e-5 * hm, 5.25588) : 0.2234 * Math.exp(-(hm - 11000) / 6341.6);
    double pO2 = 0.2095 * pIsa, ft = Breath.equivalentFt(pO2), t = Breath.tucPO2(pO2); boolean ok = Math.abs(ft - q[0]) < 50 && Math.abs(t - q[1]) / q[1] < 0.02; if (!ok) fail++;
    System.out.printf("%s %.0f ft: O2 %.4f atm -> %.0f ft, %.1f s (FAA %.1f s)%n", ok ? "PASS" : "FAIL", q[0], pO2, ft, t, q[1]); }
  boolean eb = Double.isInfinite(Breath.tuc(e, 0)) && !Double.isInfinite(Breath.tuc(e, 5000)); if (!eb) fail++; System.out.println((eb ? "PASS" : "FAIL") + " Earth air: breathable at sea level, not at 5,000 m (" + String.format("%.0f s", Breath.tuc(e, 5000)) + ")");
  PlanetWorld w = new PlanetWorld(1); Atmosphere h = Breath.home(w); System.out.println("seed 1 home: " + h);
  for (int y : new int[]{64, 1000, 2600, 3000, 4000, 5600}) System.out.printf("  y %d (%.0f m): %s%n", y, Breath.metres(y), Double.isInfinite(Breath.tuc(h, Breath.metres(y))) ? "breathable" : String.format("%.0f s of useful consciousness", Breath.tuc(h, Breath.metres(y))));
  boolean v = Breath.tuc(Atmosphere.of(0.0123, 1737, 1, 1, Planet.Climate.COLD_EDGE, false, 250, 4.5, new Rng(3)), 0) <= 15; if (!v) fail++; System.out.println((v ? "PASS" : "FAIL") + " an airless moon: 15 s");
  System.out.println("BreathTest: " + fail + " fail"); if (fail > 0) System.exit(1); } }
