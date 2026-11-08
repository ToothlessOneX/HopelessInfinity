package com.hopeless.infinity.planet;

import java.util.*;

/**
 * 0.22.0 WHERE THE SETTLEMENTS GO (stage 1 of the settlements plan; owner, 2026-11-03: "rural towns, suburbs, small cities, large cities, mega
 * cities, industrial towns, scientific communities ... follow us regulations for every single thing"; rivers first so "town placement works
 * correctly"). Pure Java (tools/SitesTest, tools/SiteMap). Made once a world, from the seed alone, on the rivers' drainage grid (Rivers, about
 * 22 km cells).
 *
 * Values (STATED: from the source named; CHOSEN: a game choice; derived: worked from stated values):
 *  - US land 3,533,038 sq mi = 9,150,537 km2 (Census QuickFacts, 2020). Every number per US area below is scaled by this planet's habitable
 *    land: the sum of cell area x habitability (0..1, CHOSEN rules in habit()).
 *  - Metro cores: 510 US urban areas of 50,000 people or more (Census 2020 urban areas, Wikipedia's list of them). Their sizes follow Zipf's law
 *    ln rank = a - 1.005 ln size (Gabaix 1999, 135 US metro areas, 1991): here, per continent, size_r = 50,000 (n / r)^(1 / 1.005) for the
 *    continent's n cores (510 per US land gives a top city of 24.6 M; the largest US urban area, New York, is 19.4 M).
 *  - Towns: about 19,500 incorporated places, 780 of them of 50,000 or more (Census, "America: A Nation of Small Towns", 2020); the 18,720
 *    smaller ones per US land, placed by habitability. Their sizes: 42 % of all places under 500 people, 76 % under 5,000 (same source): of
 *    the places under 50,000, 43.75 % under 500 and 79.2 % under 5,000 (derived), log-uniform inside 100-500 (100: CHOSEN floor), 500-5,000,
 *    5,000-50,000.
 *  - Urban footprint: 4,884.8 people per sq mi = 1,886 per km2 (Miami urban area 2020, the middle of the five largest US urban areas'
 *    densities, 3,340-7,476 per sq mi); a core's radius = sqrt(P / (pi 1,886)). Suburb: a town inside 1.5 of that radius + 10 km of a core of
 *    250,000 or more (CHOSEN).
 *  - Megacity: 10 million or more (UN; the US has no term).
 *  - Industrial towns: manufacturing-concentration counties 469 and mining-concentration 216 of 3,364 (USDA ERS county typology, 2025):
 *    14 % and 6.4 % of towns of 2,000-30,000 (the plan's size range); manufacturing at water (a river of 100 m3/s or a coast), mining in
 *    volcanic provinces (CHOSEN placement).
 *  - Scientific communities: 17 DOE national laboratories per US land (DOE), 5,000-40,000 people (the plan's range), 150 km or more from a
 *    core (CHOSEN; DOE lab towns were sited remote), on flat land.
 *  - Campus towns: 4 % of small cities (CHOSEN, to be grounded in stage 5 with NCES college counts).
 *  - People per household 2.53 (Census QuickFacts, 2020-2024): housing units = people / 2.53 (used from stage 3 on; reported now).
 */
public final class Sites {
    public static final double US_LAND_KM2 = 3533038.28 * 2.589988, US_CORES = 510, US_TOWNS = 19500 - 780, ZIPF = 1.005, CORE_MIN = 50000,
        TOWN_UNDER_500 = 0.42 / 0.96, TOWN_UNDER_5000 = 0.76 / 0.96, TOWN_MIN = 100, URBAN_DENSITY = 4884.8 / 2.589988, MEGA = 10e6,
        MANUF = 469.0 / 3364, MINING = 216.0 / 3364, US_LABS = 17, PER_HOUSEHOLD = 2.53, CAMPUS = 0.04, LAB_FAR_M = 150000, CORE_SPACING_M = 40000,
        SUBURB_CORE = 250000, SPAWN_TOWN_M = 3000,
        // 0.24.0 spaceports (owner, 2026-11-03: "a destination like Starbase"): see spaceports()
        US_SPACEPORTS = 5, SPACEPORT_POP = 500, HAZARD_M = 13000 * 0.3048, DOWNRANGE_SEA_M = 300000, SPACEPORT_CORE_M = 50000, SPACEPORT_SPACING_M = 1000000, PAD_INLAND_M = 600, PRODUCTION_M = 2500, VILLAGE_M = 3200;
    public Site spawnTown; public String spawnError = "";
    public enum Kind { RURAL_TOWN, SMALL_CITY, SUBURB, LARGE_CITY, MEGACITY, INDUSTRIAL_TOWN, SCIENCE_TOWN, CAMPUS_TOWN, SPACEPORT }

    public static final class Site {
        public final int id, cell, continent; public Kind kind; public final long pop; public final double[] s; public final String name; public int rank;
        public volatile double[] exact;   // the refined spot on dry, flat ground (made when first asked: Sites.exact)
        /** 0.24.0 spaceports: the launch pad, the production site, the village's centre, the launch direction (unit, planet space). */
        public double[] pad, production, village, downrange;
        Site(int id, int cell, int continent, Kind kind, long pop, double[] s, String name) { this.id = id; this.cell = cell; this.continent = continent; this.kind = kind; this.pop = pop; this.s = s; this.name = name; }
        public double urbanRadiusM() { return Math.sqrt(pop / (Math.PI * URBAN_DENSITY)) * 1000; }
        public long housing() { return Math.round(pop / PER_HOUSEHOLD); }
        @Override public String toString() { return String.format("%s (%s, %,d people)", name, kind.name().toLowerCase().replace('_', ' '), pop); }
    }

    public final List<Site> all = new ArrayList<>(); public final float[] habit; public final int[] continent; public int continents;
    public double habitKm2; public long buildMs, people; public final long[] phaseMs = new long[4];   /* probe: ms at the end of habitability, continents, cores, towns */ public final int[] count = new int[Kind.values().length];
    final Map<Integer, List<Site>> byCell = new HashMap<>(); final PlanetWorld w; final Rivers rv; final Terrain t; final double R; final long seed; long MAXPOP;

    public Sites(PlanetWorld w) {
        long t0 = System.nanoTime(); this.w = w; rv = w.terrain.rivers; t = w.terrain; R = w.cube.R; seed = w.seed ^ 0x5173DL; int n = rv.h.length;
        double cellKm2 = 4 * Math.PI * (R / 1000) * (R / 1000) / n;   // Nowell's map keeps cells near equal in area
        habit = new float[n]; java.util.stream.IntStream.range(0, n).parallel().forEach(c -> habit[c] = (float) habit(c));
        phaseMs[0] = ms(t0);
        // continents: land cells joined through their 8 neighbours
        continent = new int[n]; Arrays.fill(continent, -1); int[] stack = new int[n]; List<double[]> contHab = new ArrayList<>();
        for (int c = 0; c < n; c++) { if (continent[c] >= 0 || rv.kind[c] == 1) continue; int id = continents++; double hab = 0; int sp = 0; stack[sp++] = c; continent[c] = id;
            while (sp > 0) { int a = stack[--sp]; hab += habit[a] * cellKm2; for (int k = 0; k < 8; k++) { int m = rv.nb[a * 8 + k]; if (m >= 0 && continent[m] < 0 && rv.kind[m] != 1) { continent[m] = id; stack[sp++] = m; } } }
            contHab.add(new double[]{hab}); habitKm2 += hab; }
        phaseMs[1] = ms(t0);
        // metro cores per continent: weighted random order (Efraimidis-Spirakis: key u^(1/weight)), kept CORE_SPACING_M apart, ranked by weight
        Random rnd = new Random(seed); double[] key = new double[n]; for (int c = 0; c < n; c++) { double wt = habit[c] * water(c) * water(c); key[c] = wt <= 0 ? -1 : Math.pow(rnd.nextDouble(), 1 / wt); }
        Integer[] ord = new Integer[n]; for (int c = 0; c < n; c++) ord[c] = c; Arrays.sort(ord, (a, b) -> Double.compare(key[b], key[a]));
        Map<Integer, List<int[]>> coresBy = new HashMap<>(); int[] want = new int[continents]; for (int k = 0; k < continents; k++) want[k] = (int) Math.round(US_CORES * contHab.get(k)[0] / US_LAND_KM2);
        int[] got = new int[continents]; List<Integer> cores = new ArrayList<>(); List<double[]> corePos = new ArrayList<>();
        for (int c : ord) { if (key[c] < 0) break; int k = continent[c]; if (k < 0 || got[k] >= want[k]) continue; double[] p = rv.centre(c); boolean ok = true;
            for (int j = cores.size() - 1; j >= 0 && ok; j--) if (continent[cores.get(j)] == k && Rivers.arc(p, corePos.get(j)) * R < CORE_SPACING_M) ok = false;
            if (!ok) continue; cores.add(c); corePos.add(p); got[k]++; }
        // sizes by rank (Zipf), the best-watered cores first (weight x a 0.7-1.3 spread, CHOSEN)
        Map<Integer, List<Integer>> perCont = new HashMap<>(); for (int c : cores) perCont.computeIfAbsent(continent[c], z -> new ArrayList<>()).add(c);
        for (var e : perCont.entrySet()) { List<Integer> l = e.getValue(); Map<Integer, Double> sc = new HashMap<>(); for (int c : l) sc.put(c, habit[c] * water(c) * water(c) * (0.7 + 0.6 * MoonTerrain.u(MoonTerrain.hash(seed, 3, c, 0, 0), 0)));
            l.sort((a, b) -> Double.compare(sc.get(b), sc.get(a))); int nc = l.size();
            for (int r = 1; r <= nc; r++) { int c = l.get(r - 1); long pop = Math.round(CORE_MIN * Math.pow((double) nc / r, 1 / ZIPF)); Site st = add(c, pop >= MEGA ? Kind.MEGACITY : Kind.LARGE_CITY, pop, spot(c, 0)); st.rank = r; } }
        phaseMs[2] = ms(t0);
        List<Site> coreSites = new ArrayList<>(all), big = new ArrayList<>(); for (Site st : all) if (st.pop >= SUBURB_CORE) big.add(st);
        Map<Integer, List<Site>> sub = new HashMap<>(), labFar = new HashMap<>(); mark(big, co -> 1.5 * co.urbanRadiusM() + 10000, sub); mark(coreSites, co -> LAB_FAR_M, labFar);
        // towns: a Poisson count per cell from the US density of places, sized from the US size shares
        double perKm2 = US_TOWNS / US_LAND_KM2; List<Site> cand = new ArrayList<>(); Map<Long, List<double[]>> townGrid = new HashMap<>();
        // 0.26.0: every town's first spot has its ground checked in parallel first (the sequential pass below only applies the spacing): the
        // build stayed under its 8 s budget only when the machine was idle (measured 7.3 s alone, over 8 s with the test suite running)
        List<long[]> firstTry = new ArrayList<>(); for (int c = 0; c < n; c++) { if (habit[c] <= 0 || continent[c] < 0) continue; int m = poisson(perKm2 * cellKm2 * habit[c], MoonTerrain.u(MoonTerrain.hash(seed, 5, c, 0, 0), 0)); for (int i = 0; i < m; i++) firstTry.add(new long[]{c, i}); }
        Map<Long, Integer> first = new java.util.concurrent.ConcurrentHashMap<>(); firstTry.parallelStream().forEach(ci -> first.put((ci[0] << 20) | ci[1], ground(spot((int) ci[0], (int) ci[1] + 1))));
        for (int c = 0; c < n; c++) { if (habit[c] <= 0 || continent[c] < 0) continue; double lam = perKm2 * cellKm2 * habit[c]; long hs = MoonTerrain.hash(seed, 5, c, 0, 0); int m = poisson(lam, MoonTerrain.u(hs, 0));
            for (int i = 0; i < m; i++) { long h2 = MoonTerrain.hash(seed, 6, c, i, 0); double u = MoonTerrain.u(h2, 0); long pop = townSize(u);
                // 0.25.2 a town's spot: up to TOWN_TRIES spots in its cell, the first 7 km from every other town, not on a salt flat, in the sea or a
                // lake over 5 m deep, and under 15 % grade over 400 m (AASHTO local streets: 15 % at most, STATED); none: no town
                double[] p = null; Kind kd = null;
                for (int tr = 0; tr < TOWN_TRIES && p == null; tr++) { double[] q = spot(c, i + 1 + 1000 * tr); Kind k = pop >= 5000 ? Kind.SMALL_CITY : Kind.RURAL_TOWN;
                    for (Site co : sub.getOrDefault(c, List.of())) if (co.pop >= SUBURB_CORE && Rivers.arc(q, co.s) * R < 1.5 * co.urbanRadiusM() + 10000) { k = Kind.SUBURB; break; }
                    if (k == Kind.SMALL_CITY && MoonTerrain.u(h2, 2) < CAMPUS) k = Kind.CAMPUS_TOWN;
                    if (k != Kind.SUBURB && near(townGrid, q)) { townsSpaced++; continue; }
                    int gr = tr == 0 ? first.getOrDefault(((long) c << 20) | i, -1) : -1; if (gr < 0) gr = ground(q);   // 0.26.0: first tries checked in parallel beforehand
                    if (gr == 1) { townsDry++; continue; } boolean steep = gr == 2;
                    if (steep) { townsSteep++; continue; } p = q; kd = k; }
                if (p == null) { townsLost++; continue; } if (kd != Kind.SUBURB) put(townGrid, p);
                Site made = add(c, kd, pop, p); if (kd != Kind.SUBURB && kd != Kind.CAMPUS_TOWN && pop >= 2000 && pop <= 30000) cand.add(made); } }
        // industrial towns: the stated shares overall; manufacturing 3 times as likely at water (a coast, lake or river), mining 10 times as likely
        // in a volcanic province (CHOSEN weights; the totals stay the stated 14 % and 6.4 %)
        int nw = 0, nv = 0; boolean[] wetA = new boolean[cand.size()], volA = new boolean[cand.size()];
        for (int i = 0; i < cand.size(); i++) { Site x = cand.get(i); wetA[i] = water(x.cell) > 1; volA[i] = t.volcanic(rv.centre(x.cell)) > 0.3; if (wetA[i]) nw++; if (volA[i]) nv++; }
        double fw = cand.isEmpty() ? 0 : nw / (double) cand.size(), fv = cand.isEmpty() ? 0 : nv / (double) cand.size(), km = MANUF / (3 * fw + (1 - fw)), kn = MINING / (10 * fv + (1 - fv));
        for (int i = 0; i < cand.size(); i++) { Site x = cand.get(i); double u = MoonTerrain.u(MoonTerrain.hash(seed, 8, x.id, 0, 0), 0), pm = km * (wetA[i] ? 3 : 1), pn = kn * (volA[i] ? 10 : 1);
            if (u < pm + pn) x.kind = Kind.INDUSTRIAL_TOWN; }
        phaseMs[3] = ms(t0);
        // scientific communities: remote, flat, habitable cells, far from every core
        int labs = (int) Math.round(US_LABS * habitKm2 / US_LAND_KM2), made = 0; long[] lk = new long[n]; for (int c = 0; c < n; c++) lk[c] = (MoonTerrain.hash(seed, 9, c, 0, 0) & 0xFFFFFFFFFFE00000L) | c; Arrays.sort(lk);   // a seeded shuffle of the cells (hash high bits, cell low 21 bits)
        for (long lkc : lk) { int c = (int) (lkc & 0x1FFFFF); if (made >= labs) break; if (habit[c] < 0.4 || continent[c] < 0 || slope(c) > 0.004) continue; double[] p = spot(c, 99);
            boolean near = false; for (Site co : labFar.getOrDefault(c, List.of())) if (Rivers.arc(p, co.s) * R < LAB_FAR_M) { near = true; break; } if (near) continue;
            long pop = Math.round(5000 + 35000 * MoonTerrain.u(MoonTerrain.hash(seed, 10, c, 0, 0), 0)); add(c, Kind.SCIENCE_TOWN, pop, p); made++; }
        // the spawn town (owner's open decision, default: keep the beach spawn, with a rural town within a few km): a town of 500-5,000 people
        // on the first dry flat ground 3 km from the spawn (CHOSEN distance)
        try { int[] sp = w.spawn(); double[] o = w.cube.spherePoint(0, sp[0] + 0.5, sp[2] + 0.5); if (o != null) { double[] e1 = norm(cross(o, Math.abs(o[2]) < 0.9 ? new double[]{0, 0, 1} : new double[]{1, 0, 0})), e2 = cross(o, e1);
            for (int k = 0; k < 16 && spawnTown == null; k++) { double ang = 2 * Math.PI * k / 16, r = SPAWN_TOWN_M / R; double[] p = norm(new double[]{o[0] + (e1[0] * Math.cos(ang) + e2[0] * Math.sin(ang)) * r, o[1] + (e1[1] * Math.cos(ang) + e2[1] * Math.sin(ang)) * r, o[2] + (e1[2] * Math.cos(ang) + e2[2] * Math.sin(ang)) * r});
                if (!good(p, p, norm(cross(p, Math.abs(p[2]) < 0.9 ? new double[]{0, 0, 1} : new double[]{1, 0, 0})), cross(p, norm(cross(p, Math.abs(p[2]) < 0.9 ? new double[]{0, 0, 1} : new double[]{1, 0, 0}))))) continue;
                int c = rv.cellOf(p); long pop = townSize(TOWN_UNDER_500 + (TOWN_UNDER_5000 - TOWN_UNDER_500) * MoonTerrain.u(MoonTerrain.hash(seed, 11, 0, 0, 0), 0)); spawnTown = add(c, Kind.RURAL_TOWN, pop, p); spawnTown.exact = p; } } }
        catch (Throwable ex) { spawnError = String.valueOf(ex); }
        spaceports();   // after the spawn town: the places made before them keep their ids and names
        for (Site s : all) { count[s.kind.ordinal()]++; people += s.pop; }
        buildMs = (System.nanoTime() - t0) / 1_000_000; }

    /** 0.24.0 SPACEPORTS, after Starbase, Texas (incorporated 20 May 2025; a Type C commission city of about 500 people on the Boca Chica
     *  peninsula, holding Boca Chica Village, SpaceX's production and test site and its launch site: Wikipedia, 2026). STATED: population 500.
     *  Count: the active US orbital launch sites per US land, 5 (Cape Canaveral/Kennedy, Vandenberg, Wallops, Starbase, Kodiak: derived from
     *  that list). Where (FAA 14 CFR 420): the flight corridor of an orbital launch runs 5,000 nm downrange and its debris risk must stay under
     *  1e-4 expected casualties, so launches go out over open sea: a coast cell whose east (the planet turns east: prograde, as at Boca Chica and
     *  the Cape) is sea for DOWNRANGE_SEA_M (300 km, CHOSEN: the first-stage fall area); low latitudes first (weight cos^2 latitude, CHOSEN);
     *  50 km or more from a metro core (CHOSEN: no city under the corridor's start); 1,000 km apart (CHOSEN). The pad 600 m in from the shore
     *  (CHOSEN); no other place within 13,000 ft of it (420.21 table 2: the launch point to the site boundary for a large orbital vehicle);
     *  the production site 2.5 km and the village 3.2 km inland (CHOSEN; like Boca Chica Village they sit inside the hazard area and are
     *  cleared for launches: 420.27(j) allows populated areas there with an evacuation agreement). */
    void spaceports() {
        int n = rv.h.length, want = (int) Math.round(US_SPACEPORTS * habitKm2 / US_LAND_KM2); spaceportWant = want; if (want <= 0) return;
        List<double[]> cand = new ArrayList<>();
        for (int c = 0; c < n; c++) { if (continent[c] < 0 || rv.kind[c] == 1 || rv.kind[c] == 2 || habit[c] <= 0) continue; boolean sea = false; for (int k = 0; k < 8; k++) { int o = rv.nb[c * 8 + k]; if (o >= 0 && rv.kind[o] == 1) sea = true; } if (!sea) continue;
            double[] p = rv.centre(c); double cl = Math.cos(Math.asin(Math.max(-1, Math.min(1, p[2])))); cand.add(new double[]{cl * cl * (0.8 + 0.4 * MoonTerrain.u(MoonTerrain.hash(seed, 12, c, 0, 0), 0)), c}); }
        cand.sort((a, b) -> Double.compare(b[0], a[0])); List<double[]> made = new ArrayList<>();
        List<Site> bigCities = new ArrayList<>(); for (Site co : all) if (co.kind == Kind.LARGE_CITY || co.kind == Kind.MEGACITY) bigCities.add(co);   // 0.26.0: the city test looked through every place for each candidate
        for (double[] cd : cand) { if (made.size() >= want) break; int c = (int) cd[1]; double[] o = rv.centre(c), e = norm(cross(new double[]{0, 0, 1}, o));
            boolean far = true; for (double[] m : made) if (Rivers.arc(m, o) * R < SPACEPORT_SPACING_M) far = false; if (!far) continue;
            // the sea downrange: the coarse cells every 10 km from 30 to 300 km east are sea (a quick first test)
            boolean open = true; for (double d = 30000; d <= DOWNRANGE_SEA_M && open; d += 10000) if (rv.kind[rv.cellOf(along(o, e, d))] != 1) open = false; if (!open) { spaceportMiss[0]++; continue; }
            boolean core = false; for (Site co : bigCities) if (Rivers.arc(co.s, o) * R < SPACEPORT_CORE_M + co.urbanRadiusM()) { core = true; break; } if (core) { spaceportMiss[1]++; continue; }
            // the shore east of the cell's centre (100 m steps, to 40 km), the pad inland of it
            double[] shore = null; for (double d = -20000; d <= 40000; d += 100) { double[] q = along(o, e, d); double[] hw = t.heightAndWater(q, R); if (hw[0] < 0 && d > -20000) { shore = along(o, e, d - 100); break; } }
            if (shore == null) { spaceportMiss[2]++; continue; }
            boolean sea = true; for (double d = 30000; d <= DOWNRANGE_SEA_M && sea; d += 5000) if (t.heightAndWater(along(shore, e, d - PAD_INLAND_M), R)[0] >= 0) sea = false; if (!sea) { spaceportMiss[0]++; continue; }   // and the real terrain every 5 km from the pad: no island under the corridor
            double[] pad = along(shore, e, -PAD_INLAND_M), prod = along(shore, e, -PAD_INLAND_M - PRODUCTION_M), vil = along(shore, e, -PAD_INLAND_M - VILLAGE_M);
            double[] f1 = norm(cross(vil, Math.abs(vil[2]) < 0.9 ? new double[]{0, 0, 1} : new double[]{1, 0, 0}));
            if (!good(pad, pad, e, norm(cross(pad, e))) || !good(vil, vil, f1, cross(vil, f1)) || t.heightAndWater(prod, R)[0] < 2) { spaceportMiss[3]++; continue; }
            boolean clear = true; for (Site x : nearest(pad, null, 8)) if (Rivers.arc(x.s, pad) * R < HAZARD_M + x.urbanRadiusM()) clear = false; if (!clear) { spaceportMiss[4]++; continue; }
            int vc = rv.cellOf(vil); if (continent[vc] < 0) { spaceportMiss[3]++; continue; }
            Site sp = add(vc, Kind.SPACEPORT, (long) SPACEPORT_POP, vil); sp.exact = vil; sp.village = vil; sp.pad = pad; sp.production = prod; sp.downrange = e; made.add(o); } }
    public int spaceportWant; public final int[] spaceportMiss = new int[5];   // probe: wanted; candidates turned down for: no open sea east, a city near, no shore, no flat dry ground, a place within the hazard distance
    /** The point d metres from p along direction e (on the sphere). */
    double[] along(double[] p, double[] e, double d) { double a = d / R; double[] t0 = norm(new double[]{e[0] - p[0] * Roads.dot(e, p), e[1] - p[1] * Roads.dot(e, p), e[2] - p[2] * Roads.dot(e, p)}); return norm(new double[]{p[0] * Math.cos(a) + t0[0] * Math.sin(a), p[1] * Math.cos(a) + t0[1] * Math.sin(a), p[2] * Math.cos(a) + t0[2] * Math.sin(a)}); }
    static long ms(long t0) { return (System.nanoTime() - t0) / 1_000_000; }
    Site add(int c, Kind k, long pop, double[] p) { Site s = new Site(all.size(), c, continent[c], k, pop, p, Names.place(seed * 31 + all.size(), k == Kind.LARGE_CITY || k == Kind.MEGACITY)); all.add(s); byCell.computeIfAbsent(c, z -> new ArrayList<>()).add(s); return s; }
    final Map<Integer, List<Site>> reach = new HashMap<>();
    /** Every cell within m(core) metres (plus a cell) of each core gets that core listed (a breadth-first walk from each core's cell). */
    void mark(List<Site> cores, java.util.function.ToDoubleFunction<Site> m, Map<Integer, List<Site>> out) {
        double cellM = Math.PI / 2 / Rivers.N * R;
        for (Site co : cores) { double lim = m.applyAsDouble(co) + 1.5 * cellM; Map<Integer, Boolean> seen = new HashMap<>(); ArrayDeque<Integer> q = new ArrayDeque<>(); q.add(co.cell); seen.put(co.cell, true);
            while (!q.isEmpty()) { int a = q.poll(); out.computeIfAbsent(a, z -> new ArrayList<>()).add(co);
                for (int k = 0; k < 8; k++) { int o = rv.nb[a * 8 + k]; if (o >= 0 && !seen.containsKey(o)) { seen.put(o, true); if (Rivers.arc(rv.centre(o), co.s) * R < lim) q.add(o); } } } } }
    static int poisson(double lam, double u) { double p = Math.exp(-lam), f = p; int k = 0; while (u > f && k < 50) { k++; p *= lam / k; f += p; } return k; }
    static long townSize(double u) {
        double a, b, v; if (u < TOWN_UNDER_500) { a = TOWN_MIN; b = 500; v = u / TOWN_UNDER_500; } else if (u < TOWN_UNDER_5000) { a = 500; b = 5000; v = (u - TOWN_UNDER_500) / (TOWN_UNDER_5000 - TOWN_UNDER_500); } else { a = 5000; b = 49999; v = (u - TOWN_UNDER_5000) / (1 - TOWN_UNDER_5000); }
        return Math.round(a * Math.pow(b / a, v)); }

    /** Habitability of a drainage cell, 0..1 (CHOSEN rules): land only; lakes 0, salt flats 0.05; height (1 to 1,500 m, 0 at 3,000 m); mean
     *  temperature (1 from 5 to 30 C, 0 at -12 C and at 35 C: Utqiagvik, Alaska, the northernmost US town, averages about -11 C); dryness
     *  (arid x 0.35, dry x 0.7, by the biome model's own classes); slope (1 under 0.2 %, 0.2 over 2 %); volcanic provinces x 0.5. */
    double habit(int c) {
        int k = rv.kind[c]; if (k == 1 || k == 2) return 0; if (k == 3) return 0.05; double[] s = rv.centre(c); double h = Math.max(0, rv.h[c]);
        double e = h <= 1500 ? 1 : Math.max(0, 1 - (h - 1500) / 1500), tc = t.temperatureC(s, h), tt = tc < 5 ? Math.max(0, (tc + 12) / 17) : tc > 30 ? Math.max(0, (35 - tc) / 5) : 1;
        double rank = t.wetRank(t.wetness(s)), warm = tc >= 5 ? Terrain.EARTH_WARM : 1, dry = rank < Terrain.ARID / warm ? 0.35 : rank < Terrain.DRY / warm ? 0.7 : 1;
        double sl = slope(c), fl = sl < 0.002 ? 1 : sl > 0.02 ? 0.2 : 1 - 0.8 * (sl - 0.002) / 0.018;
        return e * tt * dry * fl * (t.volcanic(s) > 0.5 ? 0.5 : 1); }
    /** The steepest mean slope to a neighbouring cell (the coarse ground). */
    double slope(int c) { double m = 0; double[] p = rv.centre(c); for (int k = 0; k < 8; k++) { int o = rv.nb[c * 8 + k]; if (o < 0 || rv.kind[o] == 1) continue; m = Math.max(m, Math.abs(rv.h[c] - rv.h[o]) / (Rivers.arc(p, rv.centre(o)) * R)); } return m; }
    /** How much water a cell offers a town (1 none): a coast +0.5, a lake beside +0.3, a river +0.25 per tenfold of discharge over 100 m3/s (CHOSEN). */
    public double water(int c) { double w = 1; boolean sea = false, lake = false; for (int k = 0; k < 8; k++) { int o = rv.nb[c * 8 + k]; if (o < 0) continue; sea |= rv.kind[o] == 1; lake |= rv.kind[o] == 2; }
        if (sea) w += 0.5; if (lake) w += 0.3; if (rv.kind[c] == 4) w += 0.25 * Math.log10(1 + rv.q[c] / 100); return w; }
    /** A first spot for a site in cell c (i: which site of the cell): beside the river out of its valley floor, toward the sea on a coast, else
     *  scattered in the cell. Refined to dry flat ground by exact(). */
    double[] spot(int c, int i) {
        double[] o = rv.centre(c); long hs = MoonTerrain.hash(seed, 7, c, i, 0); double cell = Math.PI / 2 / Rivers.N;   // a cell's width (radians)
        double[] e1 = norm(cross(o, Math.abs(o[2]) < 0.9 ? new double[]{0, 0, 1} : new double[]{1, 0, 0})), e2 = cross(o, e1);
        double a = 0, b = 0;
        if (rv.kind[c] == 4 && rv.down[c] >= 0 && i <= 1) { double[] d = rv.centre(rv.down[c]); double[] dir = norm(new double[]{d[0] - o[0], d[1] - o[1], d[2] - o[2]}); double[] side = norm(cross(o, dir));
            double W = Rivers.width(rv.q[c]), off = (Math.max(4 * W, 150) + W + 400) / R * (MoonTerrain.u(hs, 0) < 0.5 ? 1 : -1), along = (MoonTerrain.u(hs, 1) - 0.5) * 0.6 * cell;
            return norm(new double[]{o[0] + side[0] * off + dir[0] * along, o[1] + side[1] * off + dir[1] * along, o[2] + side[2] * off + dir[2] * along}); }
        for (int k = 0; k < 8 && i == 0; k++) { int m = rv.nb[c * 8 + k]; if (m >= 0 && rv.kind[m] == 1) { double[] d = rv.centre(m); return norm(new double[]{o[0] + 0.35 * (d[0] - o[0]), o[1] + 0.35 * (d[1] - o[1]), o[2] + 0.35 * (d[2] - o[2])}); } }
        a = (MoonTerrain.u(hs, 2) - 0.5) * 0.8 * cell; b = (MoonTerrain.u(hs, 3) - 0.5) * 0.8 * cell;
        return norm(new double[]{o[0] + e1[0] * a + e2[0] * b, o[1] + e1[1] * a + e2[1] * b, o[2] + e1[2] * a + e2[2] * b}); }

    /** 0.25.2 a town spot's ground: 0 fine, 1 salt flat, sea or a lake over 5 m deep, 2 steeper than TOWN_MAX_GRADE over 200 m any way. */
    int ground(double[] q) {
                    double[] hw = t.heightAndWater(q, R); if (hw[2] == 3 || hw[0] < 2 || (!Double.isNaN(hw[1]) && hw[1] > hw[0] + 5)) return 1;
                    double[] e1 = norm(cross(q, Math.abs(q[2]) < 0.9 ? new double[]{0, 0, 1} : new double[]{1, 0, 0})), e2 = cross(q, e1); double dd = 200 / R;
                    boolean steep = false; for (int w4 = 0; w4 < 4 && !steep; w4++) { double ca = w4 < 2 ? (w4 == 0 ? 1 : -1) : 0, sa = w4 < 2 ? 0 : (w4 == 2 ? 1 : -1);
                        double hk = t.heightM(norm(new double[]{q[0] + (e1[0] * ca + e2[0] * sa) * dd, q[1] + (e1[1] * ca + e2[1] * sa) * dd, q[2] + (e1[2] * ca + e2[2] * sa) * dd}), R); if (Math.abs(hk - hw[0]) / 200 > TOWN_MAX_GRADE) steep = true; }   // each way (measured: a central difference passed broken plateaus)
        return steep ? 2 : 0; }
    /** 0.25.2 towns kept TOWN_SPACING_M apart: Christaller's smallest central places (market villages) lie about 7 km apart (Central Places
     *  in Southern Germany, 1933: STATED); the railroad towns of the Dakota plats, 7-10 miles (CHOSEN check). A grid of TOWN_SPACING_M cells
     *  on the unit sphere's x, y, z holds the towns made so far. */
    boolean near(Map<Long, List<double[]>> g, double[] p) { double cs = TOWN_SPACING_M / R; int gx = (int) Math.floor(p[0] / cs), gy = (int) Math.floor(p[1] / cs), gz = (int) Math.floor(p[2] / cs);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) { List<double[]> l = g.get(gkey(gx + dx, gy + dy, gz + dz)); if (l != null) for (double[] q : l) if (Rivers.arc(p, q) * R < TOWN_SPACING_M) return true; }
        return false; }
    void put(Map<Long, List<double[]>> g, double[] p) { double cs = TOWN_SPACING_M / R; g.computeIfAbsent(gkey((int) Math.floor(p[0] / cs), (int) Math.floor(p[1] / cs), (int) Math.floor(p[2] / cs)), k -> new ArrayList<>()).add(p); }
    static long gkey(int x, int y, int z) { return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF); }
    public static final double TOWN_SPACING_M = 7000; public int townsSpaced, townsDry, townsSteep, townsLost; public static final int TOWN_TRIES = 6; public static final double TOWN_MAX_GRADE = 0.15;   // TOWN_TRIES: CHOSEN.   // AASHTO local streets: 15 % at most (STATED), over 400 m
    /** The site's spot on dry, fairly flat ground (rings out 150 m at a time to 6 km from its first spot); made once. Null: none found. */
    public double[] exact(Site st) {
        double[] e = st.exact; if (e != null) return e; double[] o = st.s, e1 = norm(cross(o, Math.abs(o[2]) < 0.9 ? new double[]{0, 0, 1} : new double[]{1, 0, 0})), e2 = cross(o, e1);
        for (int ring = 0; ring <= 40; ring++) { int steps = ring == 0 ? 1 : 8 * ring; for (int k = 0; k < steps; k++) { double ang = 2 * Math.PI * k / steps, r = ring * 150 / R;
            double[] p = norm(new double[]{o[0] + (e1[0] * Math.cos(ang) + e2[0] * Math.sin(ang)) * r, o[1] + (e1[1] * Math.cos(ang) + e2[1] * Math.sin(ang)) * r, o[2] + (e1[2] * Math.cos(ang) + e2[2] * Math.sin(ang)) * r});
            if (good(p, o, e1, e2)) { st.exact = p; return p; } } }
        return null; }
    /** Dry land 2 m or more over the sea, no river or lake water, and under a 5 % grade over 50 m each way (CHOSEN; AASHTO local streets allow
     *  grades of 5-15 %: the town site itself is kept to the flat end). */
    boolean good(double[] p, double[] o, double[] e1, double[] e2) {
        double[] hw = t.heightAndWater(p, R); if (hw[0] < 2 || (!Double.isNaN(hw[1]) && hw[1] > hw[0] - 1) || hw[2] == 3) return false;
        double d = 50 / R; for (int k = 0; k < 4; k++) { double ang = Math.PI / 2 * k; double[] q = norm(new double[]{p[0] + (e1[0] * Math.cos(ang) + e2[0] * Math.sin(ang)) * d, p[1] + (e1[1] * Math.cos(ang) + e2[1] * Math.sin(ang)) * d, p[2] + (e1[2] * Math.cos(ang) + e2[2] * Math.sin(ang)) * d});
            double[] hq = t.heightAndWater(q, R); if (Math.abs(hq[0] - hw[0]) > 2.5 || (!Double.isNaN(hq[1]) && hq[1] > hq[0])) return false; }
        return true; }

    /** The nearest sites to s (any kind when kind is null), nearest first, searched over the cells out to about 300 km. */
    public List<Site> nearest(double[] s, Kind kind, int max) {
        List<Site> l = new ArrayList<>(); int c0 = rv.cellOf(s); Set<Integer> seen = new HashSet<>(); ArrayDeque<int[]> q = new ArrayDeque<>(); q.add(new int[]{c0, 0}); seen.add(c0);
        while (!q.isEmpty()) { int[] a = q.poll(); List<Site> in = byCell.get(a[0]); if (in != null) for (Site st : in) if (kind == null || st.kind == kind) l.add(st);
            if (a[1] >= 14) continue; for (int k = 0; k < 8; k++) { int m = rv.nb[a[0] * 8 + k]; if (m >= 0 && seen.add(m)) q.add(new int[]{m, a[1] + 1}); } }
        l.sort(Comparator.comparingDouble(st -> Rivers.arc(s, st.s))); return l.size() > max ? new ArrayList<>(l.subList(0, max)) : l; }
    public double distM(double[] a, double[] b) { return Rivers.arc(a, b) * R; }
    public String probe() { StringBuilder b = new StringBuilder(String.format("sites: built in %d ms (phases %s), %,d places, %,d people (%d continents, %,.0f km2 habitable)", buildMs, Arrays.toString(phaseMs), all.size(), people, continents, habitKm2));
        for (Kind k : Kind.values()) b.append(String.format(", %s %d", k.name().toLowerCase().replace('_', ' '), count[k.ordinal()])); Site top = null; for (Site s : all) if (top == null || s.pop > top.pop) top = s;
        if (top != null) b.append("; largest ").append(top); b.append("; spawn town ").append(spawnTown == null ? "none " + spawnError : spawnTown.toString()); return b.toString(); }
    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double[] norm(double[] a) { double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }
}
