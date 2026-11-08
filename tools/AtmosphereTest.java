import com.hopeless.infinity.galaxy.*; import com.hopeless.infinity.planet.PlanetWorld;
/** AtmosphereTest: the 0.10.0 atmospheres (galaxy/Atmosphere) against the Solar System and the rules' sources. Saved tool. */
public class AtmosphereTest { static int pass, fail;
  static void check(String w, boolean ok, String got) { if (ok) pass++; else fail++; System.out.printf("%s %-64s %s%n", ok ? "PASS" : "FAIL", w, got); }
  static double kc(double m, double r, double f) { return Atmosphere.keepChance(f, Atmosphere.vEsc(m, r), 1); }
  public static void main(String[] a) { com.hopeless.infinity.planet.Roads.on = false; com.hopeless.infinity.planet.Towns.on = false; com.hopeless.infinity.planet.Metro.on = false;   /* 0.27.0 megacities are MetroTest's */   /* 0.23.0: roads are RoadsTest's; this test reads the terrain */
    // the shoreline (Berta-Thompson et al. 2025) on Solar System bodies: mass (Me), radius (km), flux (Earth = 1); NASA fact sheets
    String[] air = {"Earth", "Venus", "Mars", "Titan", "Triton", "Pluto"}; double[][] airD = {{1, 6371, 1}, {0.815, 6051.8, 1.911}, {0.107, 3389.5, 0.431}, {0.0225, 2574.7, 0.0110}, {0.00359, 1353.4, 0.00111}, {0.00218, 1188.3, 0.00064}};
    String[] none = {"Moon", "Mercury", "Ganymede", "Callisto", "Io", "Europa"}; double[][] noneD = {{0.0123, 1737.4, 1}, {0.0553, 2439.7, 6.67}, {0.0248, 2634.1, 0.037}, {0.018, 2410.3, 0.037}, {0.015, 1821.6, 0.037}, {0.008, 1560.8, 0.037}};
    StringBuilder sa = new StringBuilder(), sn = new StringBuilder(); int okA = 0, okN = 0;
    for (int i = 0; i < air.length; i++) { double k = kc(airD[i][0], airD[i][1], airD[i][2]); sa.append(String.format(" %s %.2f", air[i], k)); if (k > 0.5) okA++; }
    for (int i = 0; i < none.length; i++) { double k = kc(noneD[i][0], noneD[i][1], noneD[i][2]) * (i >= 2 ? Atmosphere.GIANT_MOON_KEEP : 1); sn.append(String.format(" %s %.2f", none[i], k)); if (k < 0.5) okN++; }
    check("shoreline: bodies with air keep it", okA >= 5, okA + "/6:" + sa);
    check("shoreline (x 1/6 for moons of giants): airless bodies lose it", okN >= 5, okN + "/6:" + sn);
    // oxygen timeline (Lyons et al. 2014)
    check("oxygen: none before the GOE, trace in the Proterozoic, 21 % from 4.0 Gyr", Atmosphere.oxygen(1.5) == 0 && Atmosphere.oxygen(3.0) < 0.002 && Math.abs(Atmosphere.oxygen(4.54) - 0.21) < 1e-9, String.format("1.5 Gyr %.4f, 3.0 Gyr %.4f, 3.85 Gyr %.3f, 4.54 Gyr %.3f", Atmosphere.oxygen(1.5), Atmosphere.oxygen(3.0), Atmosphere.oxygen(3.85), Atmosphere.oxygen(4.54)));
    // breathability limits on Earth's air: sea level yes; 14,000 ft (4,267 m) the FAA limit; Everest (8,849 m) no
    Rng r = new Rng(1); Atmosphere e = null; for (int k = 0; k < 200 && (e == null || Math.abs(e.pressureAtm - 1) > 0.05); k++) e = Atmosphere.of(1, 6371, 1, 1, Planet.Climate.TEMPERATE, false, 288, 4.54, r);
    check("Earth-like air: breathable at sea level, not on Everest", e.breathable && e.breathableAt(3000) && !e.breathableAt(8849), String.format("%s; ceiling %.0f m (FAA: 4,267 m)", e, e.ceilingM()));
    Atmosphere v = null; for (int k = 0; k < 200 && (v == null || Math.abs(v.pressureAtm - 90.8) > 5); k++) v = Atmosphere.of(0.815, 6051.8, 1.911, 1, Planet.Climate.HOT, false, 737, 4.5, r);
    check("Venus-like air: not breathable", !v.breathable, v.toString());
    // the galaxy: how many temperate rocky worlds breathe, the seed 1 home
    int temperate = 0, breathe = 0, airless = 0, rocky = 0;
    for (long seed = 1; seed <= 300; seed++) { StarSystem s = new Galaxy(seed).spawn(); if (s == null) continue; Atmosphere[][] at = Atmosphere.of(s);
      for (int i = 0; i < s.planets.size(); i++) { Planet p = s.planets.get(i); if (p.kind != Planet.Kind.ROCKY) continue; rocky++; if (at[i][0].kind == Atmosphere.Kind.NONE) airless++; if (p.climate == Planet.Climate.TEMPERATE) { temperate++; if (at[i][0].breathable) breathe++; } } }
    System.out.printf("galaxy sample: %d rocky planets, %d airless; %d temperate, %d breathable%n", rocky, airless, temperate, breathe);
    PlanetWorld w = new PlanetWorld(1); Atmosphere[][] h = Atmosphere.of(w.system); System.out.println("seed 1 home: " + h[w.planet.index][0]);
    for (int j = 0; j < w.planet.moons.size(); j++) System.out.println("  its moon " + j + ": " + h[w.planet.index][1 + j]);
    for (int i = 0; i < w.system.planets.size(); i++) if (i != w.planet.index) System.out.printf("  planet %d (%s, %s): %s%n", i, w.system.planets.get(i).kind, w.system.planets.get(i).climate, h[i][0]);
    check("seed 1's home planet is breathable", h[w.planet.index][0].breathable, "");
    System.out.printf("AtmosphereTest: %d pass, %d fail%n", pass, fail); if (fail > 0) System.exit(1);
  } }
