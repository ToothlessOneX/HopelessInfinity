package com.hopeless.infinity.world;

import com.hopeless.infinity.HopelessInfinity;
import com.hopeless.infinity.galaxy.Planet;
import com.hopeless.infinity.galaxy.Research;
import com.hopeless.infinity.planet.PlanetWorld;

/**
 * 0.4.0: tells Hopeless Tides (if it is installed) about the planet, so its waves and tide on the planet's seas follow the planet:
 * gravity, the tide its star and moons raise, and which dimensions are that planet's faces. No dependency either way: Tides is found
 * by name (reflection); without it nothing happens. Only planets with liquid seas get waves (rocky, mean surface 0..100 C at 1 bar).
 *
 * Tide: the equilibrium tide a body raises is h = (3/2) (M / Mp) (R^4 / d^3) x cos term (Newton; Pugh & Woodworth 2014, Sea-Level
 * Science, ch. 3). Tides' amplitude is set for Earth (Moon + Sun), so the planet gets the ratio of its sum to Earth's sum, and the
 * moons' share of its own sum (Earth's: 1 / 1.46). Masses and distances: NASA fact sheets (Moon 0.0123 Earth masses at 384,400 km;
 * Sun 332,946 Earth masses at 1 AU).
 */
public final class TidesLink {
    private TidesLink() {}
    static final double MOON_ME = 0.0123, MOON_KM = 384400, SUN_ME = 332946;
    public static volatile String last = "not run";
    /** Tide raised by a body (Earth masses, km) on a planet (Earth masses, km), in units that cancel in the ratio. */
    static double raise(double bodyMe, double dKm, double planetMe, double rKm) { return bodyMe / planetMe * Math.pow(rKm, 4) / Math.pow(dKm, 3); }
    static final double EARTH_SUM = raise(MOON_ME, MOON_KM, 1, Research.EARTH_R_KM) + raise(SUN_ME, Research.AU_KM, 1, Research.EARTH_R_KM);
    /** {tide ratio to Earth's, moons' share 0..1} for the start planet. */
    public static double[] tide(PlanetWorld w) {
        Planet p = w.planet; double r = p.radiusKm;
        double star = raise(w.system.primary.mass * SUN_ME, p.au * Research.AU_KM, p.massE, r), moons = 0;
        for (Planet.Moon m : p.moons) moons += raise(m.massE(), m.orbitKm(), p.massE, r);
        return new double[]{(star + moons) / EARTH_SUM, moons / (star + moons)};
    }
    /** 0.4.2: give Tides (if installed) the water's annual mean temperature on every face: the terrain's own (Column.tempC: the planet's
     *  climate at the surface, sea level for the sea). Each side calls it for itself (server: register; client: when its planet is ready). */
    public static void climate(PlanetWorld w) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("hopelesstides")) return;
        try { var m = Class.forName("com.hopeless.tides.api.TidesPlanets").getMethod("setClimate", String.class, java.util.function.ToDoubleBiFunction.class);
            for (int f = 0; f < 6; f++) { final int face = f; java.util.function.ToDoubleBiFunction<Double, Double> fn = (x, z) -> com.hopeless.infinity.planet.Column.of(w, face, (int) Math.floor(x), (int) Math.floor(z)).tempC;
                m.invoke(null, Faces.key(f).location().toString(), fn); }
            // 0.4.3: the sea map for Tides' fetch beyond the loaded chunks (is this column under the sea?)
            var mw = Class.forName("com.hopeless.tides.api.TidesPlanets").getMethod("setWater", String.class, java.util.function.BiPredicate.class);
            for (int f = 0; f < 6; f++) { final int face = f; java.util.function.BiPredicate<Double, Double> sea = (x, z) -> { var c = com.hopeless.infinity.planet.Column.of(w, face, (int) Math.floor(x), (int) Math.floor(z)); return !c.voidCol && c.surfaceY < com.hopeless.infinity.planet.Terrain.SEA; };
                mw.invoke(null, Faces.key(f).location().toString(), sea); }
            Probe.tidesClimate++;
        } catch (Throwable e) { HopelessInfinity.LOG.warn("[Hopeless Infinity] tides climate link failed: {}", String.valueOf(e)); } }
    /** 0.8.2: the equilibrium tide from the real sky (Newton; Pugh & Woodworth 2014, ch. 3): each body raises (M / d^3) (cos^2 z - 1/3) at a
     *  place where it stands z from the zenith; the sum over the star and the moons, over 2/3 of the bodies' sum (the spring maximum), is
     *  handed to Tides (TidesPlanets.setTide, 0.17.3) for each face. Two highs a lunar day, springs at new and full moon, neaps at the quarters
     *  and the daily inequality from the bodies' declinations all come from the geometry. Where: the client's camera, the server's first
     *  player on that face (or the face's centre). Worked out at most once a game tick per side. */
    public static volatile long tideEvals; static final java.util.Map<String, double[]> TIDE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    static void tideLink(PlanetWorld w) {
        try { var m = Class.forName("com.hopeless.tides.api.TidesPlanets").getMethod("setTide", String.class, java.util.function.ToDoubleFunction.class);
            for (int f = 0; f < 6; f++) { final int face = f; java.util.function.ToDoubleFunction<Object> fn = o -> tideAt((net.minecraft.world.level.Level) o, face, w);
                m.invoke(null, Faces.key(f).location().toString(), fn); }
        } catch (Throwable e) { HopelessInfinity.LOG.warn("[Hopeless Infinity] tides: real tide link failed (Tides older than 0.17.3?): {}", String.valueOf(e)); } }
    static double tideAt(net.minecraft.world.level.Level l, int face, PlanetWorld w) {
        String key = (l.isClientSide ? "c" : "s") + face; long gt = l.getGameTime(); double[] c = TIDE_CACHE.get(key); if (c != null && (long) c[0] == gt) return c[1];
        double x = w.cube.F / 2.0, z = w.cube.F / 2.0;
        if (l.isClientSide) { var cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(); x = cam.x; z = cam.z; }
        else if (!l.players().isEmpty()) { x = l.players().get(0).getX(); z = l.players().get(0).getZ(); }
        double v = equilibrium(Clock.eph(w), w, face, x, z, Clock.seconds(gt, 0)); TIDE_CACHE.put(key, new double[]{gt, v}); tideEvals++; return v; }
    /** The equilibrium tide's share of its spring maximum at a place and moment (-0.5 .. 1). */
    public static double equilibrium(com.hopeless.infinity.sky.Ephemeris e, PlanetWorld w, int face, double x, double z, double t) {
        var a = e.at(face, x, z, t); double sum = 0, kSum = 0;
        double ks = w.system.primary.mass * SUN_ME / Math.pow(a.sunDistKm, 3); sum += ks * (a.sun[1] * a.sun[1] - 1.0 / 3); kSum += ks;
        for (var b : a.bodies) if (b.kind() == 3) { int j = Integer.parseInt(b.name().substring(5)); double km = w.planet.moons.get(j).massE() / Math.pow(b.distKm(), 3); sum += km * (b.dir()[1] * b.dir()[1] - 1.0 / 3); kSum += km; }
        return kSum > 0 ? sum / (kSum * 2.0 / 3) : 0; }
    public static boolean seas(PlanetWorld w) { return w.planet.kind == Planet.Kind.ROCKY && w.meanTempC > 0 && w.meanTempC < 100; }
    public static void register(PlanetWorld w) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("hopelesstides")) { last = "Hopeless Tides not installed"; HopelessInfinity.LOG.info("[Hopeless Infinity] tides: {}", last); return; }
        try {
            Class<?> c = Class.forName("com.hopeless.tides.api.TidesPlanets");
            var clearAll = c.getMethod("clearAll"); var set = c.getMethod("set", String.class, double.class, double.class, double.class, String.class);
            clearAll.invoke(null);
            if (!seas(w)) { last = "planet has no liquid seas: no waves"; HopelessInfinity.LOG.info("[Hopeless Infinity] tides: {}", last); return; }
            double[] t = tide(w); String name = w.system.name() + " " + w.planet.index;
            for (int f = 0; f < 6; f++) set.invoke(null, Faces.key(f).location().toString(), w.planet.gravity, t[0], t[1], name);
            climate(w);   // 0.4.2: the water's temperature for Tides' water types
            tideLink(w);  // 0.8.2: the tide from the real sky
            last = String.format("6 faces sent to Hopeless Tides: g %.2f m/s2, tide x%.3f of Earth's (star %.3f, moons %.4f of it)", w.planet.gravity, t[0], 1 - t[1], t[1]);
            HopelessInfinity.LOG.info("[Hopeless Infinity] tides: {}", last); Probe.tidesLinked++;
        } catch (Throwable e) { last = "link failed: " + e; HopelessInfinity.LOG.error("[Hopeless Infinity] tides link failed (Tides older than 0.13.0?)", e); }
    }
}
