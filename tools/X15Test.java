import com.hopeless.infinity.vehicle.X15; import com.hopeless.infinity.planet.PlanetWorld; import com.hopeless.infinity.galaxy.Atmosphere; import com.hopeless.infinity.world.Breath;
/** X15Test: 0.13.0 the X-15's numbers (vehicle/X15) against the sources, and flights on the seed 1 home with the game's own step. Saved tool. */
public class X15Test { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) {
    ok(Math.abs(X15.F_VAC - 262446) < 50 && Math.abs(X15.F_SL - 227262) < 50, String.format("XLR99 %.1f kN vacuum, %.1f kN sea level (59,000 / 51,090 lbf)", X15.F_VAC / 1000, X15.F_SL / 1000));
    double realDv = X15.REAL_ISP * X15.G0 * Math.log((X15.EMPTY_KG + X15.PROP_KG + X15.PILOT_KG) / (X15.EMPTY_KG + X15.PILOT_KG)), realBurn = X15.PROP_KG / (X15.F_VAC / (X15.REAL_ISP * X15.G0));
    ok(Math.abs(realBurn - 15680.0 / 13000 * 60) < 3, String.format("with the real Isp 276 s: %.0f s of burn at full power (the data page's 13,000 lb/min fuel flow gives %.0f s; its 90 s and Wikipedia's 83 s include throttling back), %.0f m/s of delta-v", realBurn, 15680.0 / 13000 * 60, realDv));
    System.out.printf("advanced engine (stated): Isp %.0f s, %.0f s of burn at full power, %.1f kg/s%n", X15.RULE_ISP, X15.PROP_KG / X15.MDOT, X15.MDOT);
    double ld = 1 / (2 * Math.sqrt(X15.kInduced(0.5) * X15.cd0(0.5))); ok(ld > 4.0 && ld < 5.0, String.format("subsonic L/D max %.2f (the X-15 landed at about 4.5)", ld));
    ok(Math.abs(X15.cd0(1.1) / X15.cd0(0.8) - 2) < 1e-9, "zero-lift drag doubles through the transonic rise (TN D-3343)");
    ok(Math.abs(X15.kInduced(5) * X15.clAlpha(5) - 1.43) < 1e-9, String.format("Mach 5 drag-due-to-lift = 1.43 / CL-alpha (TN D-3343): CLa %.3f /rad", X15.clAlpha(5)));
    ok(Math.abs(X15.heat(X15.isaP(X15.HEAT_REF_H) * 0.0289644 / (8.31446 * X15.isaT(X15.HEAT_REF_H)), 2020) - 1) < 1e-9, String.format("heat ratio 1 at the X-15A-2's Mach 6.7 point (ISA %.0f Pa, %.1f K at %.0f m)", X15.isaP(X15.HEAT_REF_H), X15.isaT(X15.HEAT_REF_H), X15.HEAT_REF_H));
    ok(Math.abs(X15.isaP(11000) - 22632) < 30 && Math.abs(X15.isaP(20000) - 5475) < 20, String.format("ISA pressure 11 km %.0f Pa (22,632), 20 km %.0f Pa (5,475)", X15.isaP(11000), X15.isaP(20000)));
    PlanetWorld w = new PlanetWorld(1); Atmosphere air = Breath.home(w); double g = w.planet.gravity;
    // a take-off: full throttle on the runway, nose 8 degrees up once at 80 m/s; the game's own step (Spaceplane.fly without the blocks)
    double[] v = {0, 0, 0}, p = {0, 2, 0}, acc = new double[3]; double fuel = X15.PROP_KG, t = 0, rot = -1, lift = -1; boolean air1 = false;
    while (t < 120 && p[1] < 5000) { double hM = Breath.metres(p[1]); double V = Math.hypot(v[2], v[1]); double pitch = V > 80 ? Math.toRadians(Math.min(8 + (p[1] > 30 ? 12 : 0), 20)) : 0; double[] n = {0, Math.sin(pitch), Math.cos(pitch)};
      double[] f = X15.forces(v, n, air.pressureAt(hM), air.muGPerMol, hM, 1, X15.EMPTY_KG + X15.PILOT_KG + fuel, acc); acc[1] -= g;
      for (int i = 0; i < 3; i++) v[i] += acc[i] * 0.05; boolean ground = p[1] <= 2.0001; if (ground) { if (v[1] < 0) v[1] = 0; v[2] = Math.max(0, v[2] - X15.SKID_MU * Math.max(0, g - f[5] / (X15.EMPTY_KG + fuel)) * 0.05); }
      for (int i = 0; i < 3; i++) p[i] += v[i] * 0.05; if (p[1] < 2) p[1] = 2; if (!air1 && p[1] > 2.5) { air1 = true; lift = t; rot = p[2]; } fuel -= X15.MDOT * 0.05; t += 0.05; }
    ok(air1 && rot < 3000, String.format("take-off: off the ground at %.1f s after %.0f m of run (T/W %.2f full on this planet's %.2f m/s2); at 5 km after %.0f s at %.0f m/s", lift, rot, X15.F_SL / ((X15.EMPTY_KG + X15.PROP_KG + X15.PILOT_KG) * g), g, t, Math.hypot(v[1], v[2])));
    // terminal (top) level speed at 10 km: full thrust equals drag at L = W
    double hM = 10000, pA = air.pressureAt(hM), rho = X15.density(pA, air.muGPerMol, hM), m = X15.EMPTY_KG + X15.PILOT_KG + X15.PROP_KG / 2, Wt = m * g, Vb = 0;
    for (double V = 100; V < 4000; V += 5) { double M = V / X15.sound(air.muGPerMol, hM), q = 0.5 * rho * V * V, cl = Wt / (q * X15.S), d = q * X15.S * (X15.cd0(M) + X15.kInduced(M) * cl * cl), T = X15.F_VAC - (X15.F_VAC - X15.F_SL) * pA; if (T > d) Vb = V; }
    System.out.printf("level flight at 10 km on seed 1 (air %.3f atm there, mu %.1f): full thrust holds up to %.0f m/s (Mach %.1f)%n", pA, air.muGPerMol, Vb, Vb / X15.sound(air.muGPerMol, hM));
    System.out.println("X15Test: " + fail + " fail"); if (fail > 0) System.exit(1); } }
