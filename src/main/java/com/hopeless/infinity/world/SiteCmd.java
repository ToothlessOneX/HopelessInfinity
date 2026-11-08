package com.hopeless.infinity.world;

import com.hopeless.infinity.HopelessInfinity;
import com.hopeless.infinity.planet.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 0.22.0 /hinf town and /hinf city: the settlement sites near you (stage 1 has no buildings yet: these show where the places will stand, for
 * your tests), and "go" to stand at the nearest one's spot. The sites are made in the background when the server starts (Sites, about 2.5 s).
 */
public final class SiteCmd {
    private SiteCmd() {}
    public static volatile long asked, went;
    /** Start making the sites off the server thread (server start). */
    public static void warm() { PlanetWorld w = PlanetWorld.current(); if (w == null) return;
        Thread th = new Thread(() -> { try { Sites s = w.sites(); if (s != null) HopelessInfinity.LOG.info("[Hopeless Infinity] {}", s.probe()); Roads r = w.roads(); if (r != null) HopelessInfinity.LOG.info("[Hopeless Infinity] {}", r.probe()); Towns tw = w.towns(); if (tw != null && s != null) HopelessInfinity.LOG.info("[Hopeless Infinity] spaceports: {} wanted, {} made, turned down {}", s.spaceportWant, s.count[Sites.Kind.SPACEPORT.ordinal()], java.util.Arrays.toString(s.spaceportMiss)); } catch (Throwable t) { HopelessInfinity.LOG.error("[Hopeless Infinity] making the sites failed", t); } }, "hinf-sites");
        th.setDaemon(true); th.start(); }
    static String names(Sites.Kind[] ks) { return ks == null ? "places" : "large cities"; }
    /** The nearest places (kinds null: any; else those kinds), or go to the nearest. */
    public static String run(ServerPlayer p, boolean cities, boolean go) {
        asked++; PlanetWorld w = PlanetWorld.current(); if (p == null || w == null || !HopelessInfinity.onPlanet(p.level()) || p.level().dimension() != Faces.key(0, HopelessInfinity.face(p.level()))) return "[Hopeless Infinity] towns are on the home planet only";
        Sites st = w.sitesIfMade(); if (st == null) return "[Hopeless Infinity] the settlement sites are still being made (a few seconds after start); try again";
        int f = HopelessInfinity.face(p.level()); double[] me = w.cube.spherePoint(f, p.getX(), p.getZ()); if (me == null) return "[Hopeless Infinity] over the void between faces";
        List<Sites.Site> near = st.nearest(me, null, 400); near.removeIf(s -> cities ? s.kind != Sites.Kind.LARGE_CITY && s.kind != Sites.Kind.MEGACITY : false);
        if (near.isEmpty()) return "[Hopeless Infinity] no " + (cities ? "large city" : "place") + " within about 300 km";
        double[][] fr = w.cube.frame(f, p.getX(), p.getZ());
        if (go) { Sites.Site s = near.get(0); double[] e = st.exact(s); if (e == null) return "[Hopeless Infinity] " + s + ": no dry flat ground found near its spot";
            double[] loc = w.cube.locate(e); int tf = (int) loc[0]; ServerLevel l = p.getServer().getLevel(Faces.key(0, tf)); if (l == null) return "[Hopeless Infinity] face " + tf + " is not loaded";
            int y = Terrain.toY(w.terrain.heightM(e, w.cube.R)) + 2; p.teleportTo(l, loc[1], y, loc[2], p.getYRot(), p.getXRot()); went++;
            return String.format("[Hopeless Infinity] at %s, face %d %d %d (%.1f km from you). Streets and lots: buildings come next", s, tf, (int) loc[1], y, st.distM(me, s.s) / 1000); }
        StringBuilder b = new StringBuilder("[Hopeless Infinity] nearest " + (cities ? "large cities" : "places") + " (streets and lots; buildings come next):");
        for (int i = 0; i < Math.min(6, near.size()); i++) { Sites.Site s = near.get(i); double[] d = {s.s[0] - me[0], s.s[1] - me[1], s.s[2] - me[2]};
            double dx = d[0] * fr[1][0] + d[1] * fr[1][1] + d[2] * fr[1][2], dz = d[0] * fr[2][0] + d[1] * fr[2][1] + d[2] * fr[2][2], brg = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
            b.append(String.format("\n  %s: %.1f km %s", s, st.distM(me, s.s) / 1000, compass(brg))); }
        return b.append("\n  /hinf ").append(cities ? "city" : "town").append(" go: stand at the nearest").toString(); }
    /** 0.23.0 /hinf road [interstate]: stand on the nearest network road (on the ground, not a bridge or tunnel). */
    public static String road(ServerPlayer p, boolean interstate) {
        asked++; PlanetWorld w = PlanetWorld.current(); if (p == null || w == null || !HopelessInfinity.onPlanet(p.level()) || p.level().dimension() != Faces.key(0, HopelessInfinity.face(p.level()))) return "[Hopeless Infinity] roads are on the home planet only";
        Roads rd = w.roadsIfMade(); if (rd == null) return "[Hopeless Infinity] the roads are still being made (a few seconds after start); try again";
        int f = HopelessInfinity.face(p.level()); double[] me = w.cube.spherePoint(f, p.getX(), p.getZ()); if (me == null) return "[Hopeless Infinity] over the void between faces";
        long t0 = System.nanoTime(); double[] r = rd.nearestRoad(me, interstate ? Roads.Cls.INTERSTATE : null); if (r == null) return "[Hopeless Infinity] no " + (interstate ? "Interstate" : "road") + " within about 30 km (" + (System.nanoTime() - t0) / 1_000_000 + " ms)";
        double[] e = {r[0], r[1], r[2]}; double[] loc = w.cube.locate(e); int tf = (int) loc[0]; ServerLevel l = p.getServer().getLevel(Faces.key(0, tf)); if (l == null) return "[Hopeless Infinity] face " + tf + " is not loaded";
        int y = (int) Math.floor(Terrain.toYd(r[3])) + 2; p.teleportTo(l, loc[1], y, loc[2], p.getYRot(), p.getXRot()); went++;
        return String.format("[Hopeless Infinity] on %s %d at face %d %d %d (%.1f km from you; %d ms to find)", Roads.Cls.values()[(int) r[5]].name().toLowerCase(), (int) r[4], tf, (int) loc[1], y, rd.st().distM(me, e) / 1000, (System.nanoTime() - t0) / 1_000_000); }
    /** 0.24.0 /hinf spaceport [go]: the nearest spaceports (anywhere on the planet), or stand in the nearest one's village. */
    public static String spaceport(ServerPlayer p, boolean go) {
        asked++; PlanetWorld w = PlanetWorld.current(); if (p == null || w == null || !HopelessInfinity.onPlanet(p.level()) || p.level().dimension() != Faces.key(0, HopelessInfinity.face(p.level()))) return "[Hopeless Infinity] spaceports are on the home planet only";
        Sites st = w.sitesIfMade(); if (st == null) return "[Hopeless Infinity] the settlement sites are still being made (a few seconds after start); try again";
        int f = HopelessInfinity.face(p.level()); double[] me = w.cube.spherePoint(f, p.getX(), p.getZ()); if (me == null) return "[Hopeless Infinity] over the void between faces";
        List<Sites.Site> ports = new java.util.ArrayList<>(); for (Sites.Site s : st.all) if (s.kind == Sites.Kind.SPACEPORT) ports.add(s); if (ports.isEmpty()) return "[Hopeless Infinity] this world has no spaceport";
        ports.sort(java.util.Comparator.comparingDouble(s -> st.distM(me, s.s)));
        if (go) { Sites.Site s = ports.get(0); double[] loc = w.cube.locate(s.village); int tf = (int) loc[0]; ServerLevel l = p.getServer().getLevel(Faces.key(0, tf)); if (l == null) return "[Hopeless Infinity] face " + tf + " is not loaded";
            int y = Terrain.toY(w.terrain.heightM(s.village, w.cube.R)) + 2; p.teleportTo(l, loc[1], y, loc[2], p.getYRot(), p.getXRot()); went++;
            return String.format("[Hopeless Infinity] in %s, face %d %d %d (%.0f km from you); the pads are %.1f km toward the sea", s, tf, (int) loc[1], y, st.distM(me, s.s) / 1000, st.distM(s.village, s.pad) / 1000); }
        double[][] fr = w.cube.frame(f, p.getX(), p.getZ()); StringBuilder b = new StringBuilder("[Hopeless Infinity] nearest spaceports (" + ports.size() + " on the planet):");
        for (int i = 0; i < Math.min(5, ports.size()); i++) { Sites.Site s = ports.get(i); double[] d = {s.s[0] - me[0], s.s[1] - me[1], s.s[2] - me[2]};
            double dx = d[0] * fr[1][0] + d[1] * fr[1][1] + d[2] * fr[1][2], dz = d[0] * fr[2][0] + d[1] * fr[2][1] + d[2] * fr[2][2], brg = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
            b.append(String.format("\n  %s: %.0f km %s", s, st.distM(me, s.s) / 1000, compass(brg))); }
        return b.append("\n  /hinf spaceport go: stand in the nearest one's village").toString(); }
    static String compass(double b) { String[] n = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"}; return n[(int) Math.round(b / 45) % 8] + String.format(" (%.0f deg)", b); }
    public static String probe() { PlanetWorld w = PlanetWorld.current(); Sites s = w == null ? null : w.sitesIfMade(); Roads r = w == null ? null : w.roadsIfMade(); return (s == null ? "sites: not made yet" : s.probe()) + String.format("; /hinf town asked %d, went %d", asked, went) + " | " + (r == null ? "roads: not made yet" : r.probe()) + " | " + Interchanges.probe() + " | " + (w == null || w.townsIfMade() == null ? "towns: not made yet" : w.townsIfMade().probe()); }
}
