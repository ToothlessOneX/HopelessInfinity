import com.hopeless.infinity.planet.PlanetWorld; import com.hopeless.infinity.world.SuitSpec; import com.hopeless.infinity.world.SuitSpec.Kind;
/** SuitTest: 0.11.0 suit numbers (world/SuitSpec) against their sources, and the rocket belt on the seed 1 home. Saved tool. */
public class SuitTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) {
    ok(Math.abs(Kind.EMU.psi() - 4.3) < 0.01, String.format("EMU %.2f psi (NASA 4.3)", Kind.EMU.psi()));
    ok(Math.abs(Kind.ORLAN.psi() - 5.8) < 0.01, String.format("Orlan-MKS %.2f psi (Zvezda 40 kPa = 5.8)", Kind.ORLAN.psi()));
    ok(Math.abs(Kind.A7L.psi() - 3.75) < 0.01, String.format("A7L %.2f psi (3.75)", Kind.A7L.psi()));
    ok(Kind.EMU.o2Seconds() == 30600 && Kind.XEMU.o2Seconds() == 28800 && Kind.A7L.o2Seconds() == 25200 && Kind.ORLAN.o2Seconds() == 25200, "tanks 8.5 / 8 / 7 / 7 h");
    ok(Math.abs(SuitSpec.BELT_REAL_N - 280 * 4.44822) < 1, String.format("belt real thrust %.0f N = 280 lbf", SuitSpec.BELT_REAL_N));
    ok(SuitSpec.BELT_TICKS == 420, "belt burn 21 s = 420 ticks");
    ok(Math.abs(SuitSpec.REFILL_X - 11.43) < 0.05, String.format("station refill %.2f x the use rate (8.5 h back in %.0f min)", SuitSpec.REFILL_X, 30600 / SuitSpec.REFILL_X / 60));
    // the real belt (75 kg pilot, no suit): T/W just over 1 on Earth, as flown
    double real = SuitSpec.BELT_REAL_N / ((75 + 57) * 9.80665); ok(real > 0.9 && real < 1.1, String.format("real belt with a 75 kg pilot: T/W %.2f on Earth (it barely lifted)", real));
    PlanetWorld w = new PlanetWorld(1); double g = w.planet.gravity; System.out.printf("seed 1 home g %.2f m/s2 (%.3f g)%n", g, w.gFactor);
    for (Kind k : Kind.values()) { double full = SuitSpec.thrustToWeight(k, 1, g), empty = SuitSpec.thrustToWeight(k, 0, g), real1 = SuitSpec.BELT_REAL_N / (SuitSpec.massKg(k, 1) * g);
      // net climb in Minecraft units: thrust per tick - gravity per tick (0.08 x gFactor), drag 0.98
      double net = SuitSpec.thrustPerTick(k, 1) - 0.08 * w.gFactor, vy = 0, y = 0; for (int t = 0; t < 420; t++) { vy = (vy + SuitSpec.thrustPerTick(k, 1 - t / 420.0) - 0.08 * w.gFactor) * 0.98; y += vy; }
      ok(full > 1.2, String.format("%-9s %.0f kg flying: T/W %.2f full -> %.2f empty (real thrust would be %.2f); net %.4f b/t2; a full 21 s burn climbs %.0f blocks, top speed %.1f m/s, knockback resist %.2f",
        k.shortName, SuitSpec.massKg(k, 1), full, empty, real1, net, y, vy * 20, SuitSpec.knockbackResist(k))); }
    System.out.println("SuitTest: " + fail + " fail"); if (fail > 0) System.exit(1); } }
