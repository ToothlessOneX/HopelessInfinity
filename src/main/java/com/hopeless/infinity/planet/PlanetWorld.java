package com.hopeless.infinity.planet;

import com.hopeless.infinity.galaxy.*;

/** Everything one world seed means for the start planet: the galaxy, the start system and planet, its cube faces, terrain and gravity. */
public final class PlanetWorld {
    public final long seed; public final Galaxy galaxy; public final StarSystem system; public final Planet planet; public final CubeSphere cube; public final Terrain terrain;
    /** Surface gravity over Earth's standard gravity: Minecraft's own gravity is scaled by this. */
    public final double gFactor;
    /** Mean surface temperature (C) and where the planet sits in its habitable zone (0 = outer edge, 1 = inner edge). */
    public final double meanTempC, hzFraction;
    private static volatile PlanetWorld current;
    /** 0.15.0: which body this world is (0 the home planet, 1 + j its moon j), its own surface gravity (m/s2) and radius (km). */
    public final int body; public final double gravity, radiusKm; private volatile PlanetWorld moon;
    /** 0.18.0 what the ground is: EARTH the home planet's kind (seas, biomes), CRATERED an airless or thin-aired cratered body (MoonTerrain),
     *  VENUS a thick CO2 world with solid ground (VenusTerrain), MAGMA one so hot under its air that its rock is molten (VenusTerrain with lava
     *  seas), NONE no surface at all (giants and mini-Neptunes). */
    public enum Surface { EARTH, CRATERED, VENUS, MAGMA, NONE }
    public final Surface surface; public final Atmosphere atm; public final double surfaceK; public final Bodies.Ref ref; public final java.util.List<Bodies.Ref> bodies;
    private final java.util.Map<Integer, PlanetWorld> bodyCache;
    /** Venus (NASA fact sheet): Bond albedo 0.76, surface 737 K under 92 bar (90.8 atm) of CO2. A grey atmosphere, Ts = Te (1 + 0.75 tau)^(1/4)
     *  (Eddington), with Venus's Te 229 K (its cloud albedo) gives tau 141 there; tau grows with the air's column (CHOSEN: in proportion to
     *  the surface pressure). Basalt melts between about 1,100 and 1,250 C (basaltic lava erupts at 1,100-1,250 C, USGS). */
    public static final double VENUS_ALBEDO = 0.76, VENUS_TAU = 141.2, VENUS_P_ATM = 90.8, SOLIDUS_K = 1373, LIQUIDUS_K = 1523;
    public static double greenhouseK(double teqRocky, double pAtm) { double te = teqRocky * Math.pow((1 - VENUS_ALBEDO) / (1 - Research.ALBEDO_ROCKY), 0.25); return te * Math.pow(1 + 0.75 * VENUS_TAU * pAtm / VENUS_P_ATM, 0.25); }
    /** The share of a MAGMA world's surface under lava: none at the solidus, all at the liquidus. */
    public double meltFraction() { return Math.max(0, Math.min(1, (surfaceK - SOLIDUS_K) / (LIQUIDUS_K - SOLIDUS_K))); }

    public PlanetWorld(long seed) {
        this.seed = seed; galaxy = new Galaxy(seed); system = galaxy.spawn(); planet = system.firstEarthLike(); body = 0; gravity = planet.gravity; radiusKm = planet.radiusKm;
        cube = new CubeSphere(planet.blockRadius); gFactor = planet.gravity / Research.G_EARTH;
        // Kopparapu 2013: the outer (maximum greenhouse) limit is where even the most CO2 keeps the surface at 273 K; the inner (moist greenhouse)
        // limit is a surface near 340 K; Earth (S = 1 round the Sun) sits at 288 K, 0.979 of the way from the outer to the inner limit.
        Star st = system.star; double smax = st.hzSeff(Research.MAX_GREENHOUSE), smoist = st.hzSeff(Research.MOIST), f = (planet.insolation - smax) / (smoist - smax);
        double fe = (1 - 0.3438) / (1.0140 - 0.3438), tK = f <= fe ? 273 + 15 * f / fe : 288 + 52 * (f - fe) / (1 - fe);
        hzFraction = f; meanTempC = tK - 273.15;
        terrain = new Terrain(seed ^ planet.index * 0x9E3779B97F4A7C15L, meanTempC); terrain.g = planet.gravity; terrain.radiusM = cube.R;   // 0.7.0: the shore's waves scale with g
        terrain.rivers = new Rivers(terrain, cube, seed ^ 0x51BE5L);   // 0.21.0 rivers and lakes (2-3 s, once a world)
        surface = Surface.EARTH; bodies = Bodies.of(system, planet.index); ref = bodies.get(0); atm = Atmosphere.of(system)[planet.index][0]; surfaceK = meanTempC + 273.15; bodyCache = new java.util.concurrent.ConcurrentHashMap<>(); bodyCache.put(0, this);
    }
    /** 0.15.0 / 0.18.0: any other body of the home system as a world of its own: its own cube faces at its real radius, its real gravity
     *  (G M / r^2), its air (galaxy/Atmosphere) and the ground that air and heat make (Surface); the galaxy and system shared with the home
     *  world. 'planet' stays the planet the body is or circles. */
    PlanetWorld(PlanetWorld home, Bodies.Ref r) {
        seed = home.seed; galaxy = home.galaxy; system = home.system; ref = r; body = r.id(); bodies = home.bodies; bodyCache = home.bodyCache; planet = system.planets.get(r.planet());
        Atmosphere[][] at = Atmosphere.of(system); atm = at[r.planet()][r.moon() + 1]; hzFraction = home.hzFraction;
        if (r.isMoon()) { Planet.Moon m = planet.moons.get(r.moon()); radiusKm = m.radiusKm(); gravity = com.hopeless.infinity.galaxy.Orbits.G * m.massE() * com.hopeless.infinity.galaxy.Orbits.M_EARTH_KG / (radiusKm * radiusKm) * 1000; }
        else { radiusKm = planet.radiusKm; gravity = planet.gravity; }
        cube = new CubeSphere(Math.round(radiusKm * 1000)); gFactor = gravity / Research.G_EARTH;
        double teq = planet.teq;   // a moon shares its planet's distance from the star
        if (atm.kind == Atmosphere.Kind.ENVELOPE) { surface = Surface.NONE; surfaceK = teq; }
        else if (atm.kind == Atmosphere.Kind.VENUS_LIKE) { surfaceK = greenhouseK(teq, atm.pressureAtm); surface = surfaceK >= SOLIDUS_K ? Surface.MAGMA : Surface.VENUS; }
        else if (atm.kind == Atmosphere.Kind.EARTH_LIKE) { surface = Surface.EARTH; surfaceK = teq + 33; }
        else { surface = Surface.CRATERED; surfaceK = r.id() == 1 ? 243.15 : teq; }   // airless, Mars-like and Titan-like grounds: cratered (the home moon keeps its 0.15.0 -30 C)
        meanTempC = surfaceK - 273.15;
        long ts = r.id() == 1 ? seed ^ 0x300D1L : seed ^ 0x300D1L * (r.id() + 1) ^ 0x5B0D7L;   // the home moon keeps its 0.15.0 seed
        terrain = switch (surface) {
            case CRATERED, NONE -> r.id() == 1 ? new MoonTerrain(ts, radiusKm) : new MoonTerrain(ts, radiusKm, 1.62 / gravity);
            case VENUS, MAGMA -> new VenusTerrain(ts, radiusKm, gravity, surface == Surface.MAGMA ? meltFraction() : 0, meanTempC);
            case EARTH -> new Terrain(seed ^ r.planet() * 0x9E3779B97F4A7C15L ^ (long) r.moon() << 40, meanTempC); };
        terrain.g = gravity; terrain.radiusM = cube.R; if (surface == Surface.EARTH) terrain.rivers = new Rivers(terrain, cube, ts ^ 0x51BE5L); }
    /** The home planet's first moon as a world (null when the planet has none). */
    public PlanetWorld moon() { return body(1); }
    /** 0.18.0 the body with this id as a world (null: no such body). Shared by every world of the system. */
    public PlanetWorld body(int id) { if (id == body) return this; if (id == 0) return bodyCache.get(0); PlanetWorld c = bodyCache.get(id); if (c != null) return c;
        for (Bodies.Ref r : bodies) if (r.id() == id) { PlanetWorld w = new PlanetWorld(home(), r); PlanetWorld was = bodyCache.putIfAbsent(id, w); return was != null ? was : w; } return null; }
    PlanetWorld home() { return body == 0 ? this : bodyCache.get(0); }
    /** 0.22.0 the home planet's settlement sites (made once, when first asked; null on other bodies and with no rivers). */
    private volatile Sites sites;
    /** The sites if made already (never waits). */
    public Sites sitesIfMade() { return sites; }
    public Sites sites() { Sites x = sites; if (x == null && body == 0 && terrain.rivers != null) { synchronized (this) { x = sites; if (x == null) { boolean was = Roads.BUILDING.get(); Roads.BUILDING.set(true); try { sites = x = new Sites(this); } finally { Roads.BUILDING.set(was); } } } } return x; }
    /** 0.23.0 the home planet's road network (made once, when first asked, after the sites; null on other bodies). The thread making the
     *  sites and roads reads plain columns (Roads.building), so the spawn search inside the sites does not wait on itself. */
    private volatile Roads roads;
    public Roads roadsIfMade() { return roads; }
    /** 0.24.0 the towns' plats (planet/Towns): made after the sites; each plat when first needed. */
    private volatile Towns towns;
    public Towns townsIfMade() { return towns; }
    public Towns towns() { Towns x = towns; if (x != null || body != 0 || terrain.rivers == null) return x;
        synchronized (this) { x = towns; if (x == null) { boolean was = Roads.BUILDING.get(); Roads.BUILDING.set(true); try { towns = x = new Towns(this); } finally { Roads.BUILDING.set(was); } } } return x; }
    /** 0.27.0 the megacities' plans (planet/Metro): made after the sites; each plan when first needed. */
    private volatile Metro metro;
    public Metro metroIfMade() { return metro; }
    public Metro metro() { Metro x = metro; if (x != null || body != 0 || terrain.rivers == null) return x;
        synchronized (this) { x = metro; if (x == null) { boolean was = Roads.BUILDING.get(); Roads.BUILDING.set(true); try { metro = x = new Metro(this); } finally { Roads.BUILDING.set(was); } } } return x; }
    /** 0.27.0 the player's spawn: at the Cobble & Sons HQ's front door in the megacity on face 0 nearest the coast spawn (Metro.spawn);
     *  the coast spawn when face 0 has no megacity. spawn() stays the coast spawn (the spawn town, the terrain tests and tools read it). */
    public int[] playerSpawn() { int[] c = spawn(); String coastKind = spawnKind; Metro m = Metro.on ? metro() : null; double[] o = cube.spherePoint(0, c[0] + 0.5, c[2] + 0.5);
        int[] q = m == null || o == null ? null : m.spawn(o); if (q == null) { spawnKind = coastKind + " (no megacity on face 0)"; return c; }
        spawnKind = "megacity HQ front door (" + sites().all.get(q[3]).name + ")"; return new int[]{q[0], q[1], q[2]}; }
    public Roads roads() { Roads x = roads; if (x != null || body != 0 || terrain.rivers == null) return x;
        synchronized (this) { x = roads; if (x == null) { boolean was = Roads.BUILDING.get(); Roads.BUILDING.set(true); try { roads = x = new Roads(this); } finally { Roads.BUILDING.set(was); } } } return x; }
    /** 0.19.0 this body's caves (made once). */
    private volatile Caves caves;
    public Caves caves() { Caves c = caves; if (c == null) caves = c = new Caves(this); return c; }
    /** Has ground to stand on (faces). */
    public boolean landable() { return surface != Surface.NONE; }
    /** 0.4.1: the world spawn is on a sea coast of a continent: rings out from face 0's centre (2 km steps, out to the face edge) to a coarse
     *  point where the large-scale ground crosses sea level with a continent (ground 200 m or more) and open ocean (-200 m or less)
     *  within 20 km; then a walk across that coast (4 m steps) to the first dry column 1-6 blocks above the sea, not frozen (2 C or more),
     *  with water 3 m or deeper on most of the seaward side within 40 blocks. Fallback: the old continent search. {x, y (feet), z}. */
    public int[] spawn() { int[] c = coastSpawn(); if (c != null) { spawnKind = "coast"; return c; } spawnKind = "inland (no coast found)"; return inlandSpawn(); }
    public volatile String spawnKind = "not searched"; public volatile int coastTries;
    int[] coastSpawn() {
        int half = cube.F / 2 - 30000;
        for (int r = 0; r < half; r += 2000) { int steps = Math.max(1, Math.min(2000, (int) (2 * Math.PI * r / 2000)));
            for (int a = 0; a < steps; a++) { double t = 2 * Math.PI * a / steps; int x = (int) (r * Math.cos(t)), z = (int) (r * Math.sin(t));
                double big = big(x, z); if (Double.isNaN(big) || Math.abs(big) > 150) continue;
                // the slope of the large-scale ground here: toward the land
                double gx = big(x + 4000, z) - big(x - 4000, z), gz = big(x, z + 4000) - big(x, z - 4000), gl = Math.hypot(gx, gz); if (!(gl > 0)) continue;
                gx /= gl; gz /= gl;
                if (big(x + (int) (gx * 20000), z + (int) (gz * 20000)) < 200 || big(x - (int) (gx * 20000), z - (int) (gz * 20000)) > -200) continue;   // continent inland, ocean seaward
                coastTries++;
                for (int d = -6000; d <= 6000; d += 4) { int cx = x + (int) Math.round(gx * d), cz = z + (int) Math.round(gz * d);
                    Column c = Column.of(this, 0, cx, cz); if (c.voidCol || c.surfaceY < Terrain.SEA + 1) continue;
                    if (c.surfaceY > Terrain.SEA + 6 || c.tempC < 2 || c.biome.key.contains("peaks") || c.biome.key.contains("slopes")) break;   // the first dry ground: a cliff, ice or a peak ends this walk
                    if (openSea(cx, cz, -gx, -gz)) return new int[]{cx, c.surfaceY + 1, cz};
                    break; } } }
        return null; }
    /** Most of the seaward fan (5 rays, 12-40 blocks out) is water, and the middle ray reaches water 3 m deep within 300 blocks. 0.7.0: on a
     *  beach the waves have shaped (Shore: Dean's profile), 3 m of water lies ~100-150 m out, no longer 12-40 blocks. */
    boolean openSea(int x, int z, double sx, double sz) { int wet = 0, n = 0;
        for (int k = -2; k <= 2; k++) { double an = Math.atan2(sz, sx) + k * 0.35, dx = Math.cos(an), dz = Math.sin(an);
            for (int m = 12; m <= 40; m += 14) { Column c = Column.of(this, 0, x + (int) Math.round(dx * m), z + (int) Math.round(dz * m)); n++; if (!c.voidCol && c.surfaceY < Terrain.SEA) wet++; } }
        if (wet * 4 < n * 3) return false;
        double l = Math.hypot(sx, sz); for (int m = 20; m <= 300; m += 10) { Column c = Column.of(this, 0, x + (int) Math.round(sx / l * m), z + (int) Math.round(sz / l * m)); if (!c.voidCol && c.surfaceY <= Terrain.SEA - 3) return true; }
        return false; }
    double big(int x, int z) { double[] sp = cube.spherePoint(0, x + 0.5, z + 0.5); return sp == null ? Double.NaN : terrain.elevationM(sp); }
    /** The 0.2.0 search: the first column on a continent (large-scale ground 200-1500 m), dry and not on a peak. */
    int[] inlandSpawn() {
        for (int r = 0; r < 400000; r += r < 2000 ? 16 : 500) { int steps = Math.min(720, Math.max(1, r / 2));
            for (int a = 0; a < steps; a++) { double t = 2 * Math.PI * a / steps; int x = (int) (r * Math.cos(t)), z = (int) (r * Math.sin(t));
                double[] sp = cube.spherePoint(0, x + 0.5, z + 0.5); if (sp == null) continue; double big = terrain.elevationM(sp); if (big < 200 || big > 1500) continue;
                Column c = Column.of(this, 0, x, z); if (c.voidCol || c.surfaceY <= Terrain.SEA + 1 || c.biome.key.contains("peaks") || c.biome.key.contains("slopes")) continue;
                return new int[]{x, c.surfaceY + 1, z}; } }
        return new int[]{0, Math.max(Terrain.SEA + 1, Column.of(this, 0, 0, 0).surfaceY + 1), 0}; }
    public static PlanetWorld of(long seed) { PlanetWorld c = current; if (c == null || c.seed != seed) current = c = new PlanetWorld(seed); return c; }
    public static PlanetWorld current() { return current; }
    public String describe() {
        if (body > 0) return String.format("%s planet %d moon %d: radius %,.0f km, gravity %.3f m/s2 (%.3f g), airless, cube face %,d blocks wide", system.name(), planet.index, body - 1, radiusKm, gravity, gFactor, cube.F);
        return String.format("%s planet %d (%s star): %.2f Earth masses, radius %,.0f km, gravity %.2f m/s2 (%.2f g), mean surface %.1f C (habitable zone %.2f), cube face %,d blocks wide",
            system.name(), planet.index, system.primary.type, planet.massE, planet.radiusKm, planet.gravity, gFactor, meanTempC, hzFraction, cube.F); }
}
