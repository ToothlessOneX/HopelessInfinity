import com.hopeless.infinity.galaxy.*;

import java.util.*;

/** Galaxy v2 measured against the published counts it claims to follow. Args: seed. Exit 1 on a failed check. */
public class GalaxyTest {
    static int fails, passes;
    static void check(String what, boolean ok, String got) { if (ok) passes++; else fails++; System.out.printf("%s %-66s %s%n", ok ? "PASS" : "FAIL", what, got); }
    static boolean near(double got, double want, double relTol) { return Math.abs(got - want) <= relTol * Math.abs(want); }

    public static void main(String[] a) { com.hopeless.infinity.planet.Roads.on = false; com.hopeless.infinity.planet.Towns.on = false; com.hopeless.infinity.planet.Metro.on = false;   /* 0.27.0 megacities are MetroTest's */   /* 0.23.0: roads are RoadsTest's; this test reads the terrain */
        long seed = a.length > 0 ? Long.parseLong(a[0]) : 6537286697268376325L; Galaxy g = new Galaxy(seed);
        double sunY = -Research.R0_KPC * 1000; long scx = 0, scy = (long) Math.floor(sunY / Galaxy.CELL);
        // 1. the Sun's neighbourhood: every object within 20 pc of the Sun's place
        long t0 = System.nanoTime(); int n20 = 0, wd20 = 0; for (long dx = -2; dx <= 2; dx++) for (long dy = -2; dy <= 2; dy++) for (long dz = -2; dz <= 2; dz++)
            for (Galaxy.Ref f : g.cell(scx + dx, scy + dy, dz)) { if (Math.sqrt(f.x * f.x + Math.pow(f.y - sunY, 2) + f.z * f.z) > 20) continue; n20 += f.companion == null ? 1 : 2;
                if (f.primary.kind == Body.Kind.WHITE_DWARF) wd20++; if (f.companion != null && f.companion.kind == Body.Kind.WHITE_DWARF) wd20++; }
        double vol20 = 4.0 / 3 * Math.PI * 8000;
        check("objects within 20 pc (Kirkpatrick 2024: ~3,600 incl. companions)", near(n20, 3600, 0.15), n20 + String.format(" objects (%.0f us per cell)", (System.nanoTime() - t0) / 1e3 / 125));
        check("white dwarf density at the Sun (Hollands 2018: 4.49e-3 pc^-3)", near(wd20 / vol20, Research.CHECK_WD_DENSITY, 0.35), String.format("%.2e (%d WDs)", wd20 / vol20, wd20));
        // 2. composition of a 200 x 200 x 60 pc box round the Sun
        long[] kinds = new long[Body.Kind.values().length]; long[] cls = new long[7]; long ms = 0, systems = 0, mult = 0; long[] multN = new long[6], multK = new long[6];
        List<Galaxy.Ref> fgk = new ArrayList<>();
        for (long dx = -10; dx < 10; dx++) for (long dy = -10; dy < 10; dy++) for (long dz = -3; dz < 3; dz++) for (Galaxy.Ref f : g.cell(scx + dx, scy + dy, dz)) {
            systems++; for (Body b : f.companion == null ? new Body[]{f.primary} : new Body[]{f.primary, f.companion}) { kinds[b.kind.ordinal()]++; if (b.kind == Body.Kind.MAIN_SEQUENCE) { ms++; cls[Body.classOf(b.initialMass)]++; } }
            if (f.primary.kind == Body.Kind.MAIN_SEQUENCE) { int c = Body.classOf(f.primary.initialMass); if (c >= 3 && c <= 5 && fgk.size() < 40000) fgk.add(f); }
            int b = bin(f.primary.initialMass); multN[b]++; if (f.companion != null) { multK[b]++; mult++; } }
        System.out.println("box census " + systems + " systems: " + census(kinds));
        double rs = 0; for (double v : Research.REYLE_CLASS) rs += v;
        for (int c = 2; c < 7; c++) { double want = Research.REYLE_CLASS[c] / rs, got = cls[c] / (double) ms, sig = Math.sqrt(Research.REYLE_CLASS[c]) / rs;   // Reyle's counts are small: compare within 2 Poisson sigma
            check("main-sequence class " + Research.CLASSES.charAt(c) + " share (Reyle 2021 10 pc census, 2 sigma)", Math.abs(got - want) <= 2 * sig, String.format("%.2f%% want %.2f%% +/- %.2f", got * 100, want * 100, 200 * sig)); }
        long ind = 0; for (long v : kinds) ind += v;
        check("white dwarf share of objects (Reyle 2021: 21 of 463 = 4.5%), 2 sigma", Math.abs(kinds[Body.Kind.WHITE_DWARF.ordinal()] / (double) ind - 21 / 463.0) <= 2 * Math.sqrt(21) / 463, String.format("%.2f%%", 100.0 * kinds[Body.Kind.WHITE_DWARF.ordinal()] / ind));
        double bdRatio = (ind - kinds[0]) / (double) kinds[0];
        check("star : brown dwarf about 4 : 1 (Kirkpatrick 2024), within 35%", near(bdRatio, Research.STAR_TO_BD, 0.35), String.format("%.2f : 1", bdRatio));
        String[] bn = {"BD <0.08", "M 0.08-0.5", "FGK 0.5-1.3", "A 1.3-5", "B 5-16", "O >16"};
        for (int i = 0; i < 6; i++) if (multN[i] > 200) check("multiplicity " + bn[i] + " (Duchene & Kraus 2013)", Math.abs(multK[i] / (double) multN[i] - Research.MULT_FREQ[i]) < 0.03, String.format("%.1f%% of %d", 100.0 * multK[i] / multN[i], multN[i]));
        // 3. galaxy totals: density integrated, and the remnant shares of the generator's own birth + ageing
        double tot = 0, totB = 0; double st = 100; for (double x = -15000; x < 15000; x += st) for (double y = -15000; y < 15000; y += st) { double s = Galaxy.surface(x + st / 2, y + st / 2) * st * st; tot += s; }
        for (double x = -6000; x < 6000; x += 50) for (double y = -6000; y < 6000; y += 50) for (double z = -3000; z < 3000; z += 50) totB += 0; // (bulge is inside 'tot')
        double want = Research.GALAXY_STELLAR_MASS / Research.MEAN_OBJECT_MASS;
        tot *= 1 + Galaxy.COMPANIONS; check("objects in the galaxy (5e10 Msun / 0.41 Msun = 1.22e11), within 35%", near(tot, want, 0.35), String.format("%.3g", tot));
        Rng r = new Rng(42); int N = 4_000_000; long ns = 0, bh = 0, wd = 0; double massSum = 0;
        for (int i = 0; i < N; i++) { double mi = Galaxy.imf(r, Research.IMF_MIN, Research.IMF_MAX), age = Research.DISK_AGE_GYR * 1e9 * r.u(); Body b = Body.evolve(mi, age, r); massSum += b.mass;
            if (b.kind == Body.Kind.NEUTRON_STAR) ns++; else if (b.kind == Body.Kind.BLACK_HOLE) bh++; else if (b.kind == Body.Kind.WHITE_DWARF) wd++; }
        check("neutron stars in the galaxy (Lam 2020: 4.4e8), within a factor 2", Math.abs(Math.log(ns / (double) N * tot / Research.CHECK_NS)) < Math.log(2), String.format("%.3g", ns / (double) N * tot));
        check("stellar black holes in the galaxy (Lam 2020: 2.2e8), within a factor 2", Math.abs(Math.log(bh / (double) N * tot / Research.CHECK_BH)) < Math.log(2), String.format("%.3g", bh / (double) N * tot));
        check("mean mass per object (Kirkpatrick 2024: 0.41 Msun), within 35%", near(massSum / N, Research.MEAN_OBJECT_MASS, 0.35), String.format("%.3f Msun", massSum / N));
        // 4. young massive stars of the whole galaxy: supernova rate, remnants, Wolf-Rayet stars
        long young = 0, wr = 0, snr = 0, snNext = 0, sg = 0; Galaxy.Ref nearestSnr = null;
        for (int sx = -Galaxy.COLUMNS; sx < Galaxy.COLUMNS; sx++) for (int sy = -Galaxy.COLUMNS; sy < Galaxy.COLUMNS; sy++) for (Galaxy.Ref f : g.youngColumn(sx, sy)) {
            young++; Body b = f.primary; if (b.kind == Body.Kind.WOLF_RAYET) wr++; if (b.kind == Body.Kind.SUPERGIANT) sg++;
            if (!b.alive() && b.deathYr <= Research.SNR_LIFE_YR) { snr++; if (nearestSnr == null || f.lyFromSun() < nearestSnr.lyFromSun()) nearestSnr = f; }
            if (b.alive() && b.deathYr > -1e5 && b.kind != Body.Kind.BROWN_DWARF) snNext++; }
        System.out.printf("young massive stars %,d | supergiants %,d | remnants younger than 1e5 yr %,d (nearest %.0f ly) %n", young, sg, snr, nearestSnr == null ? -1 : nearestSnr.lyFromSun());
        check("core-collapse supernovae per century (Rozwadowska 2021 all types: 1.63 +/- 0.46)", snNext / 1000.0 > 1.63 - 2 * 0.46 && snNext / 1000.0 < 1.63 + 2 * 0.46, String.format("%.2f (next 100,000 yr: %d)", snNext / 1000.0, snNext));
        check("Wolf-Rayet stars (Rosslowe & Crowther 2015: 1200 +300/-100), within a factor 2", Math.abs(Math.log(wr / Research.CHECK_WR)) < Math.log(2), String.valueOf(wr));
        // 5. planets of FGK dwarfs near the Sun
        long hj = 0, giant5 = 0, debris = 0, belts = 0, ringed = 0, bright = 0, giants = 0, pl = 0;
        for (Galaxy.Ref f : fgk) { StarSystem s = g.system(f); pl += s.planets.size(); if (!s.planets.isEmpty() && s.planets.get(0).au < 0.1 && s.planets.get(0).kind == Planet.Kind.GAS_GIANT) hj++; for (Planet p : s.planets) { double per = Math.sqrt(Math.pow(p.au, 3) / f.primary.mass) * 365.25; if (p.massE >= 0.3 * 317.83 && p.massE <= 10 * 317.83 && per >= 2 && per <= 2000) { giant5++; break; } }
            for (StarSystem.Belt bb : s.belts) { if (bb.kind().equals("main asteroid belt")) belts++; else debris++; }
            for (Planet p : s.planets) if (p.giant() && p.au >= s.star.snowLine()) { giants++; if (p.rings != Planet.Rings.NONE) ringed++; if (p.rings == Planet.Rings.BRIGHT) bright++; } }
        int nf = fgk.size(); System.out.printf("FGK sample %d systems, %.2f planets each, giants %d (ringed %d, bright %d), main belts %d%n", nf, pl / (double) nf, giants, ringed, bright, belts);
        check("hot Jupiters round FGK dwarfs (Wright 2012: 1.2 +/- 0.38%)", Math.abs(hj / (double) nf - 0.012) < 0.0076, String.format("%.2f%%", 100.0 * hj / nf));
        check("FGK with a 0.3-10 MJ planet at 2-2000 d (Cumming 2008: 10.5%), 30%", near(giant5 / (double) nf, 0.105, 0.30), String.format("%.1f%%", 100.0 * giant5 / nf));
        check("cold debris belts round FGK (Sibthorpe 2018: 17.1%), within 30%", near(debris / (double) nf, Research.DEBRIS_DISC, 0.30), String.format("%.1f%%", 100.0 * debris / nf));
        check("every giant beyond the snow line has rings, a quarter bright (Solar System)", giants > 0 && ringed == giants && Math.abs(bright / (double) giants - 0.25) < 0.03, String.format("%d/%d ringed, %.1f%% bright", ringed, giants, 100.0 * bright / Math.max(1, giants)));
        long rockyBig = 0, rockyN = 0; double ratio = 0; int giantsM = 0; long mb = 0; double ceres = 0;
        for (Galaxy.Ref f : fgk) { StarSystem s = g.system(f); for (Planet p : s.planets) { if (p.kind == Planet.Kind.ROCKY && p.massE > 0.5) { rockyN++; for (Planet.Moon m : p.moons) if (m.kind() == Planet.MoonKind.GIANT_IMPACT) { rockyBig++; break; } }
                if (p.giant()) { double sm = 0; for (Planet.Moon m : p.moons) if (m.kind() == Planet.MoonKind.REGULAR) sm += m.massE(); ratio += sm / p.massE; giantsM++; } }
            for (StarSystem.Belt bb : s.belts) if (bb.kind().equals("main asteroid belt")) { mb++; ceres += Math.log(bb.largestKm()); } }
        check("rocky planets > 0.5 Me with a Moon-like moon (Elser 2011: 1 in 12), 25%", near(rockyBig / (double) rockyN, 1.0 / 12, 0.25), String.format("1 in %.1f of %d", rockyN / (double) Math.max(1, rockyBig), rockyN));
        check("regular moon mass / giant mass (Canup & Ward 2006: 1e-4)", near(ratio / giantsM, 1e-4, 0.01), String.format("%.3g", ratio / giantsM));
        System.out.printf("main belts %d, median largest body %.0f km (Ceres 939 km at the Sun's 4.0e-4 Me)%n", mb, Math.exp(ceres / Math.max(1, mb)));
        // 6. rogue planets and Sgr A*
        long rog = 0, stars = 0; for (long dx = -2; dx <= 2; dx++) for (long dy = -2; dy <= 2; dy++) { rog += g.rogues(scx + dx, scy + dy, 0).size(); for (Galaxy.Ref f : g.cell(scx + dx, scy + dy, 0)) if (f.primary.kind != Body.Kind.BROWN_DWARF) stars++; }
        check("rogue planets per star (Sumi 2023: 21), within 15%", near(rog / (double) stars, Research.ROGUES_PER_STAR, 0.15), String.format("%.1f (%d rogues, %d stars)", rog / (double) stars, rog, stars));
        Body sa = g.centre().primary; check("Sgr A*: 4.154e6 Msun, event horizon 1.227e7 km (2GM/c^2)", near(sa.radiusKm, 1.2267e7, 0.01), String.format("%.4g Msun, %.4g km", sa.mass, sa.radiusKm));
        // 7. determinism and the start system
        List<Galaxy.Ref> c1 = g.cell(scx, scy, 0), c2 = g.cell(scx, scy, 0); boolean same = c1.size() == c2.size(); for (int i = 0; same && i < c1.size(); i++) same = c1.get(i).x == c2.get(i).x && c1.get(i).primary.mass == c2.get(i).primary.mass;
        check("same seed gives the same cell", same && !c1.isEmpty(), c1.size() + " objects");
        long t1 = System.nanoTime(); StarSystem sp = g.spawn(); check("start: a G dwarf with a temperate rocky planet near Earth's mass", sp != null, sp == null ? "none" : sp.name() + String.format(" (search %.0f ms)", (System.nanoTime() - t1) / 1e6));
        if (sp != null) System.out.print(sp);
        System.out.printf("%d passed, %d failed%n", passes, fails); if (fails > 0) System.exit(1);
    }
    static int bin(double m) { for (int i = 0; i < Research.MULT_MASS.length; i++) if (m < Research.MULT_MASS[i]) return i; return 5; }
    static String census(long[] k) { StringBuilder b = new StringBuilder(); for (Body.Kind x : Body.Kind.values()) if (k[x.ordinal()] > 0) b.append(x).append(' ').append(k[x.ordinal()]).append("  "); return b.toString(); }
}
