import com.hopeless.infinity.planet.*; import com.hopeless.infinity.galaxy.*; import com.hopeless.infinity.world.*; import com.hopeless.infinity.sky.Ephemeris;
/** BodiesTest: 0.18.0 the home system's other bodies as worlds (planet/Bodies, PlanetWorld.body, VenusTerrain, MoonTerrain's gravity scaling, Near.Sys). Saved tool. */
public class BodiesTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) { com.hopeless.infinity.planet.Roads.on = false; com.hopeless.infinity.planet.Towns.on = false; com.hopeless.infinity.planet.Metro.on = false;   /* 0.27.0 megacities are MetroTest's */   /* 0.23.0: roads are RoadsTest's; this test reads the terrain */
    PlanetWorld w = new PlanetWorld(1); int n = w.bodies.size(); System.out.println("seed 1: " + n + " bodies");
    ok(w.bodies.get(0).id() == 0 && w.bodies.get(1).id() == 1 && w.body(1) == w.moon() && w.body(1).body == 1, "ids 0 and 1 stay the home planet and its moon");
    for (var r : w.bodies) { PlanetWorld b = w.body(r.id()); System.out.printf("  body %2d: planet %d%s  %-8s %6.0f km  g %.2f  air %-11s %8.3g atm  %5.0f K%n", r.id(), r.planet(), r.isMoon() ? " moon " + r.moon() : "       ", b.surface, b.radiusKm, b.gravity / 9.81, b.atm.kind, b.atm.pressureAtm, b.surfaceK); }
    ok(w.body(5).surface == PlanetWorld.Surface.NONE && !w.body(5).landable(), "the mini-Neptunes have no surface");
    // Venus itself through the greenhouse rule: 0.723 AU round the Sun (insolation 1.911), 90.8 atm -> 737 K
    double teqV = Research.TEQ_1AU * Math.pow(1.911, 0.25) * Math.pow(1 - Research.ALBEDO_ROCKY, 0.25), tsV = PlanetWorld.greenhouseK(teqV, 90.8);
    ok(Math.abs(tsV - 737) < 2, String.format("the greenhouse rule gives Venus %.0f K (NASA: 737 K)", tsV));
    int venus = 0, magma = 0; for (var r : w.bodies) { var b = w.body(r.id()); if (b.surface == PlanetWorld.Surface.VENUS) venus++; if (b.surface == PlanetWorld.Surface.MAGMA) magma++; }
    ok(venus + magma == 5, String.format("seed 1's five thick-aired rocky planets: %d with solid ground, %d molten (over the basalt solidus %d K)", venus, magma, (int) PlanetWorld.SOLIDUS_K));
    // Venus-like ground against Magellan's hypsometry
    PlanetWorld v = null; for (var r : w.bodies) if (w.body(r.id()).surface == PlanetWorld.Surface.VENUS) { v = w.body(r.id()); break; }
    VenusTerrain vt = (VenusTerrain) v.terrain; int N = 20000; double ga = Math.PI * (3 - Math.sqrt(5)); double[] h = new double[N]; long t0 = System.nanoTime();
    for (int i = 0; i < N; i++) { double y = 1 - 2 * (i + 0.5) / N, rr = Math.sqrt(1 - y * y), th = ga * i; h[i] = vt.heightM(new double[]{rr * Math.cos(th), y, rr * Math.sin(th)}, v.cube.R); }
    double us = (System.nanoTime() - t0) / 1e3 / N; double[] hs = h.clone(); java.util.Arrays.sort(hs); double med = hs[N / 2]; int in1 = 0, over2 = 0; for (double x : h) { if (Math.abs(x - med) < 1000) in1++; if (x - med > 2000) over2++; }
    System.out.printf("body %d (Venus-like): heights %.1f .. %.1f km, median %.2f km; %.1f us a height%n", v.body, hs[0] / 1000, hs[N - 1] / 1000, med / 1000, us);
    ok(Math.abs(in1 / (double) N - 0.80) < 0.06, String.format("  %.1f %% of the surface within 1 km of the median (Magellan: 80 %%)", 100.0 * in1 / N));
    ok(over2 / (double) N > 0.05 && over2 / (double) N < 0.15, String.format("  %.1f %% over 2 km (Venus: ~10 %%)", 100.0 * over2 / N));
    ok((hs[N - 1] - med) > 7000 && (hs[N - 1] - med) < 14000, String.format("  the highest point %.1f km over the median (Maxwell Montes ~11 km); range %.1f km (Venus ~13)", (hs[N - 1] - med) / 1000, (hs[N - 1] - hs[0]) / 1000));
    // craters per area: count crater rims by sampling the octave lattice is hard; check the expected number from the law instead
    double area = 4 * Math.PI * v.radiusKm * v.radiusKm; System.out.printf("  expected craters: %.0f (Venus 940 per 4.60e8 km2; this body %.2e km2)%n", 940 * area / 4.60e8, area);
    // a molten world: its lava share
    for (var r : w.bodies) { var b = w.body(r.id()); if (b.surface != PlanetWorld.Surface.MAGMA) continue; VenusTerrain mt = (VenusTerrain) b.terrain; int lava = 0; for (int i = 0; i < 4000; i++) { double y = 1 - 2 * (i + 0.5) / 4000, rr = Math.sqrt(1 - y * y), th = ga * i; if (mt.lava(mt.heightM(new double[]{rr * Math.cos(th), y, rr * Math.sin(th)}, b.cube.R))) lava++; }
      ok(Math.abs(lava / 4000.0 - Math.min(1, b.meltFraction())) < 0.03, String.format("molten body %d (%.0f K): %.0f %% of its surface under lava (melt fraction %.2f)", b.body, b.surfaceK, 100.0 * lava / 4000, b.meltFraction())); }
    // columns: Venus ground dry basalt, molten seas lava, no water anywhere
    int cOk = 0, lavaCols = 0; PlanetWorld mg = null; for (var r : w.bodies) if (w.body(r.id()).surface == PlanetWorld.Surface.MAGMA) mg = w.body(r.id());
    for (int i = 0; i < 200; i++) { int x = (i * 7919) % 2000000 - 1000000, z = (i * 104729) % 2000000 - 1000000; Column c = Column.of(v, i % 6, x, z); if (c.voidCol) { cOk++; continue; }
      if (c.dry() && c.at(c.surfaceY + 1, -2560) == Column.Mat.AIR && (c.top == Column.Mat.BASALT_ROCK || c.top == Column.Mat.DEEPSLATE)) cOk++;
      if (mg != null) { Column m = Column.of(mg, i % 6, x, z); if (!m.voidCol && m.at(m.surfaceY + 1, -2560) == Column.Mat.LAVA) lavaCols++; } }
    ok(cOk == 200, String.format("200 Venus-like columns dry, basalt or deepslate on top (%d)", cOk));
    if (mg != null) ok(lavaCols > 0, String.format("molten body %d: %d of 200 columns have lava over the ground", mg.body, lavaCols));
    // the other airless moon: craters shaped for its gravity
    PlanetWorld m2 = null; for (var r : w.bodies) if (r.isMoon() && r.id() != 1) m2 = w.body(r.id());
    if (m2 != null) { System.out.printf("moon body %d: %.0f km, g %.2f m/s2, simple-to-complex transition %.1f km (Moon 10.6 x 1.62 / g)%n", m2.body, m2.radiusKm, m2.gravity, 10.6 * 1.62 / m2.gravity); ok(m2.surface == PlanetWorld.Surface.CRATERED, "the other airless moon is cratered"); }
    // spheres of influence (Laplace a (m/M)^0.4)
    Near.Sys sy = Near.Sys.of(w); for (int k = 0; k < n; k++) if (!sy.refs[k].isMoon()) System.out.printf("  planet %d SOI %.0f km%n", sy.refs[k].planet(), sy.soi[k] / 1000);
    ok(Math.abs(sy.soi[0] / 1e3 - 1.0e6) < 3e5, String.format("home planet's sphere of influence %.0f km (Earth's: 924,000 km)", sy.soi[0] / 1000));
    // the sky from a Venus-like world: a cloud deck; from the airless moon: airless
    Ephemeris e = new Ephemeris(w); var at = e.atBody(v, 0, 0.5, 0.5, 1000); ok(at.skyMode == 2 && at.body == v.body && at.bodies.stream().anyMatch(b -> b.kind() == 4), "the sky from a Venus-like world: cloud deck mode, the home planet among the bodies");
    if (m2 != null) { var am = e.atBody(m2, 0, 0.5, 0.5, 1000); ok(am.skyMode == 1, "the sky from the other moon: airless"); }
    var hm = e.atBody(w.moon(), 0, 0.5, 0.5, 1000); var hm0 = e.atMoon(0, w.moon(), 0, 0.5, 0.5, 1000); ok(Math.abs(hm.sunAlt - hm0.sunAlt) < 1e-12, "the home moon's sky unchanged");
    System.out.println("BodiesTest: " + fail + " fail"); if (fail > 0) System.exit(1); } }
