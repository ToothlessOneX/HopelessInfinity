package com.hopeless.infinity.galaxy;

/**
 * Every published value the galaxy generator uses, with its source. Values marked GAME are our own tuning on top of the research
 * (scale, art), not physics; everything else comes from the paper named beside it. docs/RESEARCH.md lists the same table.
 */
public final class Research {
    private Research() {}

    // ------------------------------------------------------------------ the Milky Way (shape of our galaxy)
    /** Sun - Galactic Centre distance, kpc. GRAVITY Collaboration 2019, A&A 625, L10: 8178 +/- 13 (stat) +/- 22 (sys) pc. */
    public static final double R0_KPC = 8.178;
    /** Thin disk scale length, kpc. Bland-Hawthorn & Gerhard 2016 (value used in their Table 2): 2.6. */
    public static final double DISK_SCALE_LENGTH_KPC = 2.6;
    /** Thin disk scale height, kpc. Juric et al. 2008, ApJ 673, 864: H1 = 300 pc. */
    public static final double DISK_SCALE_HEIGHT_KPC = 0.300;
    /** Long bar half-length, kpc. Bland-Hawthorn & Gerhard 2016 s4.3: 5.0 +/- 0.2. */
    public static final double BAR_HALF_LENGTH_KPC = 5.0;
    /** Bar angle to the Sun - centre line, degrees. Bland-Hawthorn & Gerhard 2016 s4.2.1: 27 +/- 2. */
    public static final double BAR_ANGLE_DEG = 27;
    /** Global spiral-arm pitch angle, degrees. Vallee 2015, MNRAS 450, 4277: 13.1 +/- 0.6 (meta-analysis). */
    public static final double ARM_PITCH_DEG = 13.1;
    /** Number of major spiral arms. Vallee's four-arm model (Vallee 2015 and his earlier arm catalogues). */
    public static final int ARMS = 4;
    /** GAME: how much denser an arm is than the disk between arms, and the arm sharpness (art values, shown on the map). */
    public static final double ARM_CONTRAST = 2.6, ARM_SHARPNESS = 4.0;
    /** GAME: bulge Gaussian radius and outer disk cut, kpc. */
    public static final double BULGE_SIGMA_KPC = 0.7, DISK_EDGE_KPC = 15.0;

    // ------------------------------------------------------------------ main-sequence stars
    /** Fraction of main-sequence stars by class O B A F G K M. LeDrew 2001, JRASC 95, 32 (as tabled by Las Cumbres Observatory). */
    public static final double[] CLASS_FRACTION = {0.0000003, 0.0013, 0.006, 0.03, 0.076, 0.121, 0.765};
    public static final String CLASSES = "OBAFGKM";
    /**
     * Mean dwarf sequence, Mamajek "A Modern Mean Dwarf Stellar Color and Effective Temperature Sequence" v2022.04.16
     * (Pecaut & Mamajek 2013, ApJS 208, 9). Rows: spectral type, Teff K, log10 L/Lsun, R/Rsun, M/Msun. Hottest first.
     */
    public static final String[] MS_TYPE = {"O5V", "B0V", "B5V", "A0V", "A5V", "F0V", "F5V", "G0V", "G2V", "G5V", "K0V", "K5V", "M0V", "M3V", "M5V", "M8V"};
    public static final double[] MS_TEFF = {41400, 31400, 15700, 9700, 8100, 7220, 6550, 5930, 5770, 5660, 5270, 4440, 3850, 3430, 3060, 2570};
    public static final double[] MS_LOGL = {5.54, 4.65, 2.77, 1.58, 1.09, 0.86, 0.56, 0.13, 0.01, -0.05, -0.34, -0.76, -1.16, -1.79, -2.52, -3.28};
    public static final double[] MS_R = {11.45, 7.16, 3.36, 2.193, 1.785, 1.728, 1.473, 1.100, 1.012, 0.977, 0.813, 0.701, 0.588, 0.361, 0.196, 0.114};
    public static final double[] MS_M = {43, 17.7, 4.7, 2.18, 1.88, 1.61, 1.33, 1.06, 1.00, 0.98, 0.88, 0.70, 0.57, 0.37, 0.162, 0.085};
    /** Row index where each class starts (O at O5V ... M at M0V) and the end row of the table. Class X spans rows [START[X], START[X+1]]. */
    public static final int[] CLASS_ROW = {0, 1, 3, 5, 7, 10, 12, 15};
    /** Initial mass function slopes, Kroupa 2001, MNRAS 322, 231: dN/dM ~ M^-1.3 (0.08-0.5 Msun), M^-2.3 (above 0.5). Used inside a class. */
    public static final double IMF_LOW = 1.3, IMF_HIGH = 2.3, IMF_BREAK = 0.5;

    // ------------------------------------------------------------------ habitable zone
    /**
     * Kopparapu et al. 2013, ApJ 765, 131, Table 3: Seff = Seff_sun + a T + b T^2 + c T^3 + d T^4, T = Teff - 5780 K, valid 2600-7200 K;
     * distance d = sqrt(L / Seff) AU. Rows: recent Venus, runaway greenhouse, moist greenhouse, maximum greenhouse, early Mars.
     */
    public static final double[][] HZ = {
        {1.7753, 1.4316e-4, 2.9875e-9, -7.5702e-12, -1.1635e-15},
        {1.0512, 1.3242e-4, 1.5418e-8, -7.9895e-12, -1.8328e-15},
        {1.0140, 8.1774e-5, 1.7063e-9, -4.3241e-12, -6.6462e-16},
        {0.3438, 5.8942e-5, 1.6558e-9, -3.0045e-12, -5.2983e-16},
        {0.3179, 5.4513e-5, 1.5313e-9, -2.7786e-12, -4.8997e-16}};
    public static final int RECENT_VENUS = 0, RUNAWAY = 1, MOIST = 2, MAX_GREENHOUSE = 3, EARLY_MARS = 4;
    public static final double HZ_TEFF_MIN = 2600, HZ_TEFF_MAX = 7200;

    // ------------------------------------------------------------------ planets
    /** Mass-radius relation, Chen & Kipping 2017, ApJ 834, 17, Table 1: R = 1.008 M^0.279 (Terran, M < 2.04 Mearth), then 0.589 (Neptunian),
     *  -0.044 (Jovian, from 0.414 Mjup), 0.881 (stellar, from 0.0800 Msun); the pieces join continuously. */
    public static final double CK_C = 1.008, CK_S1 = 0.2790, CK_S2 = 0.589, CK_S3 = -0.044, CK_S4 = 0.881;
    public static final double CK_T1 = 2.04, CK_T2 = 0.414 * 317.83, CK_T3 = 0.0800 * 332946.0;   // all in Earth masses
    /** Snow line, AU, scaled by sqrt(L): Hayashi 1981, PThPS 70, 35: 2.7 AU for the Sun. */
    public static final double SNOW_LINE_AU = 2.7;
    /** Spacing of neighbour planets in mutual Hill radii, Weiss et al. 2018, AJ 155, 48: 93% of pairs at >= 10, most common about 20. */
    public static final double HILL_MIN = 10, HILL_MODE = 20;
    /** Weiss et al. 2018: in a pair of neighbours the outer planet is the larger one in 65% of cases (the generator is checked against it). */
    public static final double OUTER_LARGER = 0.65;
    /** Average planets per star (0.5-10 AU), Cassan et al. 2012, Nature 481, 167: 1.6 (+0.72 -0.89); with close-in Kepler planets more. */
    public static final double PLANETS_PER_STAR_MICROLENS = 1.6;
    /** Earth's surface gravity, m/s^2 (standard gravity, NIST). */
    public static final double G_EARTH = 9.80665;
    /** Equilibrium temperature at 1 AU from the Sun for albedo 0: 278.6 K (T = 278.6 S^0.25 (1-A)^0.25). Bond albedo of Earth 0.306 (NASA fact sheet). */
    public static final double TEQ_1AU = 278.6, ALBEDO_ROCKY = 0.306;

    // ------------------------------------------------------------------ game scale
    /** GAME: blocks of planet radius for one Earth radius (an Earth-type planet is 1024 blocks round = 6 cube faces of 1609 blocks). */
    public static final double BLOCKS_PER_EARTH_RADIUS = 1024;
    /** GAME: the generator places a system every SECTOR_PC parsecs at most SYSTEMS_PER_SECTOR_PEAK in the densest cell. */
    public static final double SECTOR_PC = 100;
    public static final double SYSTEMS_SCALE = 110;

    // ------------------------------------------------------------------ v2: the real galaxy (added 2026-11-02)
    /** Sgr A*, solar masses. GRAVITY Collaboration 2019, A&A 625, L10, Table 1: 4.154 +/- 0.014 x 10^6. */
    public static final double SGR_A_MASS = 4.154e6;
    /** Objects (stars, brown dwarfs, remnants) per cubic parsec at the Sun: Kirkpatrick et al. 2024, ApJS 271, 55: ~3,600 within 20 pc. */
    public static final double LOCAL_DENSITY = 3600 / (4.0 / 3 * Math.PI * 20 * 20 * 20);
    /** Thick disk: scale height 900 pc, 12 % of the thin disk's density at the plane (Juric et al. 2008). */
    public static final double THICK_H_KPC = 0.900, THICK_FRACTION = 0.12;
    /** Total stellar mass of the Galaxy, solar masses (Bland-Hawthorn & Gerhard 2016 s2.2: 5 +/- 1 x 10^10); bulge+bar 1.4-1.7 x 10^10 (s4.2.4). */
    public static final double GALAXY_STELLAR_MASS = 5e10, BULGE_MASS = 1.55e10;
    /** Present star formation rate of the Milky Way, Msun/yr: Licquia & Newman 2015, ApJ 806, 96: 1.65 +/- 0.19 (Kroupa IMF). Young massive stars come from it. */
    public static final double SFR_NOW = 1.65;
    /** Spiral arm strength in the old stellar disk (arms are weak in old stars, roughly 15 %; strong in young stars). */
    public static final double OLD_ARM = 0.15;
    /** Mean mass per object, solar masses (Kirkpatrick et al. 2024: 0.41). */
    public static final double MEAN_OBJECT_MASS = 0.41;
    /** IMF of the 20 pc census, Kirkpatrick et al. 2024 (quadripartite power law): alpha 0.6 (0.01-0.05), 0.25 (0.05-0.22), 1.3 (0.22-0.55),
     *  2.3 (0.55-8); 2.3 continued to the 150 Msun cut (Kroupa 2001's high-mass slope). Hydrogen burning starts at 0.08 Msun. */
    public static final double[] IMF2_EDGE = {0.01, 0.05, 0.22, 0.55, 150}, IMF2_SLOPE = {0.6, 0.25, 1.3, 2.3};
    public static final double IMF_MIN = 0.01, IMF_HBL = 0.08, IMF_MAX = 150;
    /** 10 pc census (Reyle et al. 2021, A&A 650, A201, Table 3): A 4, F 8, G 18, K 38, M 249 (+36 untyped, mostly M), white dwarfs 20 (+1), brown dwarfs 85 (+4). */
    public static final double[] REYLE_CLASS = {0, 0, 4, 8, 18, 38, 285}; public static final double REYLE_WD = 21, REYLE_BD = 89;
    /** Giant planets 0.3-10 Jupiter masses with periods 2-2000 days round solar-type stars: 10.5 % (Cumming et al. 2008, PASP 120, 531);
     *  dN ~ M^-0.31 dlnM, P^0.26 dlnP within that box. */
    public static final double CUMMING = 0.105, CUMMING_M_ALPHA = -0.31, CUMMING_P_BETA = 0.26;
    /** Giant planet occurrence rises with stellar mass, about linearly (Johnson et al. 2010, PASP 122, 905: f ~ M*^1.0). A planet can't outweigh the disk it
     *  formed in: protoplanetary disks hold about 0.2-0.6 % of their star's mass (Andrews et al. 2013, ApJ 771, 129), so planets are capped at 0.5 % of M*. */
    public static final double GIANT_MSTAR_EXP = 1.0, DISK_MASS_FRACTION = 0.005;
    /** Regular moon systems of giant planets hold about 1e-4 of the planet's mass (Canup & Ward 2006, Nature 441, 834). */
    public static final double MOON_MASS_RATIO = 1e-4;
    /** A rocky planet over 0.5 Earth masses gets a Moon-like giant-impact moon (> 0.5 lunar masses) about 1 time in 12 (Elser et al. 2011, Icarus 214, 357). Lunar mass 0.0123 Earth masses. */
    public static final double BIG_MOON = 1.0 / 12, LUNAR_MASS_E = 0.0123;
    /** Belt masses for a Solar-System-like architecture: main belt 4.008e-4, Kuiper belt 1.97e-2 Earth masses (Pitjeva & Pitjev 2018). Sizes: collisional
     *  equilibrium, cumulative N(>D) ~ D^-2.5 (Dohnanyi 1969); the largest body holds about 39 % of the main belt (Ceres, 939 km). */
    public static final double MAIN_BELT_ME = 4.008e-4, KUIPER_ME = 1.97e-2, DOHNANYI = 2.5, CERES_D_KM = 939;
    /** Oort cloud: (1.5 +/- 1) x 10^12 comets of 1 km or more round the Sun, 1.3 Earth masses (Boe et al. 2019, Icarus 333, 252); size slope 0.72 above 3.7 km. */
    public static final double OORT_COMETS = 1.5e12, COMET_ALPHA = 0.72;
    /** Star to brown dwarf number ratio (Kirkpatrick et al. 2024): 4:1 - the generator is checked against it. */
    public static final double STAR_TO_BD = 4.0;
    /** Main-sequence lifetime t = 10 Gyr (M/Msun)^-2.5 (standard mass-luminosity scaling), never below 3 Myr. Disk ages: constant star formation over 10 Gyr. */
    public static final double T_SUN_GYR = 10, DISK_AGE_GYR = 10, BULGE_AGE_MIN_GYR = 8;
    /** After the main sequence: giant / supergiant phases last about 10 % of the main-sequence life. */
    public static final double POST_MS = 0.10;
    /** Initial-final mass relation of white dwarfs, Kalirai et al. 2008, ApJ 676, 594: Mf = 0.109 Mi + 0.394. */
    public static final double IFMR_A = 0.109, IFMR_B = 0.394;
    /** Remnant split by initial mass: white dwarf below 8 Msun, neutron star 8-20, black hole above 20 (PopSyCLE, Lam et al. 2020, Table 1 places the split near 15-18). */
    public static final double NS_MIN = 8, BH_MIN = 20;
    /** Neutron star: 1.4 Msun, radius 12.4 km (NICER, Miller et al. 2021). */
    public static final double NS_MASS = 1.4, NS_RADIUS_KM = 12.4;
    /** Galaxy totals checked against: neutron stars 4.4e8, black holes 2.2e8 (Lam et al. 2020, PopSyCLE); white dwarfs 4.49e-3 per pc^3 at the Sun (Hollands et al. 2018). */
    public static final double CHECK_NS = 4.4e8, CHECK_BH = 2.2e8, CHECK_WD_DENSITY = 4.49e-3;
    /** Milky Way supernova rate, per century: Rozwadowska, Vissani & Cappellaro 2021, New Astron. 83, 101498: 1.63 +/- 0.46. */
    public static final double CHECK_SN_PER_CENTURY = 1.63;
    /** Galactic Wolf-Rayet stars: 1200 (+300, -100) (Rosslowe & Crowther 2015, MNRAS 447, 2322). Massive stars (> 25 Msun) spend the last part of their life as WR stars. */
    public static final double CHECK_WR = 1200, WR_MIN = 25;
    /** A supernova remnant stays visible about 10^5 yr before it merges into the interstellar gas. */
    public static final double SNR_LIFE_YR = 1e5;
    /** Multiplicity frequency by primary mass (Duchene & Kraus 2013, ARA&A 51, 269, Table 1): BD 22 %, M 26 %, FGK 44 % (Raghavan 2010), A 50 %, B (8-16) 60 %, O 80 %. */
    public static final double[] MULT_MASS = {0.08, 0.5, 1.3, 5, 16}, MULT_FREQ = {0.22, 0.26, 0.44, 0.50, 0.60, 0.80};
    /** Binary periods: log-normal, mean log10 P = 5.03 (days), sigma 2.28 (Raghavan et al. 2010); mass ratio about flat. */
    public static final double LOGP_MEAN = 5.03, LOGP_SIGMA = 2.28;
    /** Planets round one star of a binary stay stable inside about 0.3 of the binary separation (Holman & Wiegert 1999, AJ 117, 621). */
    public static final double S_TYPE_LIMIT = 0.3;
    /** Hot Jupiters (giant planet within 0.1 AU) round FGK dwarfs: 1.2 +/- 0.38 % (Wright et al. 2012, ApJ 753, 160). */
    public static final double HOT_JUPITER = 0.012;
    /** Cold debris (Kuiper-type) belts detected round FGK stars: 17.1 % (Sibthorpe et al. 2018, MNRAS 475, 3046). */
    public static final double DEBRIS_DISC = 0.171;
    /** Free-floating (rogue) planets per star, 0.33-6660 Earth masses: 21 (+23, -13); mass function index 0.96 (Sumi et al. 2023, AJ 166, 108). */
    public static final double ROGUES_PER_STAR = 21, ROGUE_MIN_ME = 0.33, ROGUE_MAX_ME = 6660, ROGUE_ALPHA = 0.96;
    /** Saturn's rings: inner edge of the D ring 66,900 km, outer edge of the A ring 136,775 km, Saturn equatorial radius 60,268 km (NASA Saturn fact sheet). */
    public static final double SAT_RING_IN = 66900 / 60268.0, SAT_RING_OUT = 136775 / 60268.0;
    /** Ring systems: all four giant planets of the Solar System have rings; one of the four (Saturn) has massive bright rings. */
    public static final double RING_ANY = 1.0, RING_BRIGHT = 0.25;
    /** Main asteroid belt: between the 4:1 and 2:1 mean-motion resonances of the innermost giant (Kirkwood gaps, 2.06-3.28 AU for Jupiter). Kuiper belt: 3:2 to 2:1 of the outermost giant (39.4-47.7 AU for Neptune). */
    public static final double[] MAIN_BELT_RES = {4, 2}, KUIPER_RES = {2.0 / 3, 1.0 / 2};
    /** Earth radius, km (IUGG mean radius); Sun radius 695,700 km (IAU 2015 nominal); AU 149,597,870.7 km (IAU 2012). */
    public static final double EARTH_R_KM = 6371.0, SUN_R_KM = 695700, AU_KM = 149597870.7;
}
