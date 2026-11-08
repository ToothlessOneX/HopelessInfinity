package com.hopeless.infinity.world;

/**
 * 0.11.0 THE FOUR SPACESUITS (owner, 2026-11-02: "build all 4 to give players choice"). Pure data and physics (no Minecraft classes), so
 * tools/SuitTest checks it off the game. Each suit is the real one's published numbers:
 *  EMU (NASA Shuttle/ISS): 4.3 psi (29.6 kPa); 8.5 h of life support including a 30 min reserve (NASA, via Wikipedia "Extravehicular
 *      Mobility Unit"); 145 kg (T. Jones, "Inside NASA's moon suit plan", Aerospace America 57/10, 2019).
 *  xEMU (NASA Artemis): 4.3 psid for EVA, settable to 8.2 psid (NASA NTRS 20230007781, ICES-2023, xEMU regulators); EVAs of up to 8 h
 *      (NASA NTRS 20200001071, lunar surface EVA concept of operations). Its mass is not published: the EMU's 145 kg, CHOSEN (same class).
 *  A7L (Apollo): 3.75 psi (25.9 kPa) pure oxygen; 83 kg with the backpack (Apollo 11; Orbital Radar's suit sheet and Wikipedia
 *      "Apollo/Skylab A7L": 81.6 kg); 7 h, CHOSEN from the Apollo 15-17 backpacks (the longest moonwalk 7 h 37 min, Apollo 17; Apollo 11's
 *      backpack was rated for 4 h).
 *  Orlan-MKS (Russia, ISS): 40 kPa (5.8 psi); 110 kg; 7 h autonomous (A. Zak, RussianSpaceWeb, "Orlan-MKS", 2013 data sheet).
 * The JETPACK in every suit (owner: "the jet pack as part of the spacesuit", chose "MMU + rocket belt"):
 *  ROCKET BELT (Bell Aerosystems, 1961): 280 lbf (1,245 N) of hydrogen peroxide thrust for 21 s; 125 lb (57 kg) with 47 lb (21 kg) of
 *      propellant (yarchive.net "jetpack", Bell's data sheet; Wikipedia "Bell Rocket Belt"). STATED GAME RULE (owner, 2026-11-02: "raise
 *      it"): the thrust is x BELT_RULE (4) = 4,980 N, so a suited player can lift (the real belt lifted a 75 kg pilot in light clothes with
 *      next to nothing to spare). The burn stays the real 21 s; the mass falls as the propellant burns (the climb gets quicker, as it did).
 *  MMU (NASA, 1984): 2 x 5.9 kg of nitrogen, delta-v 110-130 ft/s (33.5-39.6 m/s), 0.3 ft/s2 (0.09 m/s2) (Wikipedia "Manned
 *      Maneuvering Unit"). For space, when the space above the build height exists (the next step): its numbers are kept here now.
 * The wearer: 90.6 kg, the average US man (CDC NHANES 2015-2018, 199.8 lb).
 * Breathing gas from the station: a 10 L/min oxygen concentrator (the high-flow home kind) gives 0.857 kg O2 an hour (10 L x 60 x 1.429
 * g/L); a suited person at EVA work uses about 0.075 kg/h (NASA: about 1,000 BTU/h metabolic, 300 W) - so the station fills the tank at
 * REFILL_X = 11.4 x the rate it is used (8.5 h back in 45 min).
 */
public final class SuitSpec {
    private SuitSpec() {}
    public enum Kind {
        EMU("emu", "EMU", "NASA Extravehicular Mobility Unit (Shuttle, ISS)", 29.6, 8.5, 145, false),
        XEMU("xemu", "xEMU", "NASA Exploration EMU (Artemis)", 29.6, 8.0, 145, true),
        A7L("a7l", "A7L", "Apollo A7L lunar suit", 25.9, 7.0, 83, false),
        ORLAN("orlan", "Orlan-MKS", "Russian Orlan-MKS (ISS)", 40.0, 7.0, 110, false);
        public final String id, shortName, longName; public final double kPa, hours, massKg; public final boolean massChosen;
        Kind(String id, String s, String l, double kPa, double h, double m, boolean chosen) { this.id = id; shortName = s; longName = l; this.kPa = kPa; hours = h; massKg = m; massChosen = chosen; }
        public double psi() { return kPa / 6.894757; }
        public int o2Seconds() { return (int) Math.round(hours * 3600); }
    }
    public static final double BODY_KG = 90.6;
    public static final double BELT_REAL_N = 1245.5, BELT_RULE = 4, BELT_N = BELT_REAL_N * BELT_RULE, BELT_EMPTY_KG = 57 - 21.3, BELT_FUEL_KG = 21.3, BELT_S = 21;
    public static final int BELT_TICKS = (int) (BELT_S * 20);
    public static final double MMU_DV = 36.6, MMU_ACC = 0.0914, MMU_N2_KG = 11.8;
    public static final double REFILL_X = (10 * 60 * 1.429 / 1000) / 0.075;
    /** The belt refuels at the station too (STATED: hydrogen peroxide from the same station), the 21 s back in 60 s. */
    public static final double BELT_REFILL_S = 60;
    /** The share of the thrust a pilot can lean into a direction (the belt is steered by tilting it: about 20 degrees, sin 20 = 0.34, CHOSEN). */
    public static final double TILT = Math.toRadians(20);

    /** Total mass (kg) flying: the wearer, the suit, the belt with what propellant is left. */
    public static double massKg(Kind k, double fuelFrac) { return BODY_KG + k.massKg + BELT_EMPTY_KG + BELT_FUEL_KG * Math.max(0, Math.min(1, fuelFrac)); }
    /** Thrust over weight on a planet of g (m/s2). */
    public static double thrustToWeight(Kind k, double fuelFrac, double g) { return BELT_N / (massKg(k, fuelFrac) * g); }
    /** The thrust in Minecraft's units: its gravity 0.08 blocks per tick per tick stands for 9.80665 m/s2 (Gravity scales it by g), so an
     *  acceleration a (m/s2) is 0.08 a / 9.80665 a tick. */
    public static double thrustPerTick(Kind k, double fuelFrac) { return 0.08 * (BELT_N / massKg(k, fuelFrac)) / 9.80665; }
    /** Knockback resistance of the whole suit: a push of the same impulse moves a heavier body less (velocity ~ 1/m). Per piece: a quarter. */
    public static double knockbackResist(Kind k) { return 1 - BODY_KG / (BODY_KG + k.massKg); }
    public static Kind of(String id) { for (Kind k : Kind.values()) if (k.id.equals(id)) return k; return null; }
}
