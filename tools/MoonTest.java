import com.hopeless.infinity.planet.*; import com.hopeless.infinity.sky.Ephemeris;
/** MoonTest: 0.15.0 the home moon (planet/MoonTerrain, PlanetWorld.moon, sky/Ephemeris from the moon) measured against the Moon's record. Saved tool. */
public class MoonTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) { com.hopeless.infinity.planet.Roads.on = false; com.hopeless.infinity.planet.Towns.on = false; com.hopeless.infinity.planet.Metro.on = false;   /* 0.27.0 megacities are MetroTest's */   /* 0.23.0: roads are RoadsTest's; this test reads the terrain */
    PlanetWorld w = new PlanetWorld(1), m = w.moon(); MoonTerrain t = (MoonTerrain) m.terrain; double R = m.cube.R;
    System.out.println(m.describe());
    ok(Math.abs(m.gravity - 1.685) < 0.01, String.format("gravity %.3f m/s2 from G M / r^2 (the Moon: 1.62)", m.gravity));
    // heights, maria over a Fibonacci sphere
    int n = 20000; double ga = Math.PI * (3 - Math.sqrt(5)), lo = 1e9, hi = -1e9, sum = 0; int mare = 0, near = 0, nearMare = 0, far = 0, farMare = 0; long t0 = System.nanoTime(); double sNear = 0, sFar = 0;
    for (int i = 0; i < n; i++) { double y = 1 - 2 * (i + 0.5) / n, r = Math.sqrt(1 - y * y), th = ga * i; double[] s = {r * Math.cos(th), y, r * Math.sin(th)}; double h = t.heightM(s, R);
      lo = Math.min(lo, h); hi = Math.max(hi, h); sum += h; boolean mr = t.mare(s); if (mr) mare++; if (s[1] > 0) { near++; sNear += h; if (mr) nearMare++; } else { far++; sFar += h; if (mr) farMare++; } }
    double us = (System.nanoTime() - t0) / 1e3 / n;
    ok(lo > -12000 && hi < 13000 && lo < -4000 && hi > 4000, String.format("heights %.1f .. %.1f km (LOLA Moon: -9.1 .. +10.8 km)", lo / 1000, hi / 1000));
    ok(Math.abs((double) mare / n - 0.16) < 0.02, String.format("maria %.1f %% of the surface (Moon: 16 %%)", 100.0 * mare / n));
    ok((double) nearMare / near > 0.2 && (double) farMare / far < 0.06, String.format("near side %.1f %% maria, far side %.1f %% (Moon: 31 %% / 2 %%)", 100.0 * nearMare / near, 100.0 * farMare / far));
    ok(sFar / far - sNear / near > 1000, String.format("far side %.2f km higher on average (Moon: the centre of figure ~1.9 km off toward the far side)", (sFar / far - sNear / near) / 1000));
    System.out.printf("cost: %.1f us a height%n", us);
    // Pike 1977 shapes
    double d5 = MoonTerrain.profile(5, 0, 1) - MoonTerrain.profile(5, 2.5, 1), d50 = MoonTerrain.profile(50, 25, 1) - MoonTerrain.profile(50, 0, 1) + 0.25 * 1.044 * Math.pow(50, 0.301);
    ok(Math.abs(-d5 / 5 - 0.199) < 0.01, String.format("a 5 km simple crater: depth/diameter %.3f (Pike: 0.196 D^1.010 -> 0.199)", -d5 / 5));
    ok(Math.abs(d50 - 1.044 * Math.pow(50, 0.301)) < 0.05, String.format("a 50 km complex crater: rim-to-floor %.2f km (Pike: 1.044 D^0.301 = %.2f)", d50, 1.044 * Math.pow(50, 0.301)));
    ok(Math.abs(MoonTerrain.profile(10, 5, 1) - 0.036 * Math.pow(10, 1.014)) < 1e-9 && MoonTerrain.profile(10, 15, 1) == 0, "rim height 0.036 D^1.014 at the rim, ejecta to zero at 3 R");
    // the sky from the landing site (the near side's centre: moon face 0's middle)
    Ephemeris e = new Ephemeris(w); double minAlt = 90, maxAlt = -90; Ephemeris.Body hb = null;
    for (double s = 0; s < 30 * 86400; s += 6 * 3600) { var at = e.atMoon(0, m, 0, 0.5, 0.5, s); for (var b : at.bodies) if (b.name().equals("home planet")) { hb = b; double alt = Math.toDegrees(Math.asin(b.dir()[1])); minAlt = Math.min(minAlt, alt); maxAlt = Math.max(maxAlt, alt); } }
    double lib = Math.toDegrees(2 * 0.014043); ok(minAlt > 90 - lib * 1.3 && minAlt < 90 - lib * 0.6, String.format("the home planet from the landing site stays overhead all month: altitude %.2f .. %.2f deg (locked; it wanders by the libration in longitude, 2e = %.2f deg)", minAlt, maxAlt, lib));
    ok(hb != null && Math.abs(Math.toDegrees(2 * hb.angR()) - 2.11) < 0.15, String.format("its disk %.2f deg across (2 asin(7,185 / 390,304) = 2.11; Earth from the Moon: 1.9)", hb == null ? 0 : Math.toDegrees(2 * hb.angR())));
    var s0 = e.atMoon(0, m, 0, 0.5, 0.5, 0); double sun0 = s0.sunAlt; int flips = 0; double prev = sun0; for (double s = 3600; s < 3 * 1975353; s += 3600) { double al = e.atMoon(0, m, 0, 0.5, 0.5, s).sunAlt; if ((al > 0) != (prev > 0)) flips++; prev = al; }
    ok(Math.abs(flips / 2.0 / 3 - 1) < 0.2, String.format("the star rises and sets once a month there: %d sunrises+sunsets in 3 orbits (22.9 d each; a locked moon's day is its month)", flips));
    // the columns the generator lays down: dry at every height (no sea on an airless body), regolith on top
    int dryOk = 0, below = 0; for (int i = 0; i < 400; i++) { int x = (i * 7919) % 200000 - 100000, z = (i * 104729) % 200000 - 100000; Column c = Column.of(m, i % 6, x, z); if (c.voidCol) continue;
      if (c.surfaceY < 0) below++; boolean ok1 = c.dry() && c.at(c.surfaceY + 1, -2560) == Column.Mat.AIR && c.at(Math.min(-1, c.surfaceY + 1), -2560) != Column.Mat.WATER && (c.top == Column.Mat.REGOLITH || c.top == Column.Mat.MARE_REGOLITH); if (ok1) dryOk++; }
    ok(dryOk == 400 && below > 0, String.format("400 moon columns: %d dry with regolith on top (%d of them below the datum, still no water)", dryOk, below));
    Column site = Column.of(m, 0, 0, 0); System.out.printf("landing site: ground y %d (%.0f m), %s%n", site.surfaceY, site.heightM, site.top);
    System.out.println("MoonTest: " + fail + " fail"); if (fail > 0) System.exit(1); } }
