package com.hopeless.infinity.game;

import com.hopeless.infinity.HopelessInfinity;
import com.hopeless.infinity.planet.*;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/** 0.28.5 where things are, for the town game ported from Hopeless Deep (its game/Places, on Deep's settlement model): here on
 *  Infinity's towns (planet/Towns plats) and cities (planet/Metro). A place is a site with a plan; a lot is a plat's lot, keyed by
 *  the site and Towns' own lot key, with its use (house, store, post office...), its centre in block coordinates and its seed. */
public final class Places {
    private Places() {}
    public record Place(Sites.Site site, Towns.Plan plan, Metro.Plan city, int face) { public long id() { return site.id; } public String name() { return site.name; }
        public double cx() { return plan != null ? plan.cx : city != null ? city.cx : 0; } public double cz() { return plan != null ? plan.cz : city != null ? city.cz : 0; }
        public double radius() { return plan != null ? plan.extentM : city != null ? city.reachM : 500; } }
    public record Lot(Place place, long key, Towns.Use use, int bi, int bj, int row, int lot, int x, int z, long seed) {}

    static PlanetWorld w() { return PlanetWorld.current(); }
    /** The place holding block (x, z) of level l (a town's plat and its slope band, or a city's plan), or null. */
    public static Place at(ServerLevel l, int x, int z) { PlanetWorld w = w(); int f = HopelessInfinity.face(l); if (w == null || f < 0) return null; double[] s = w.cube.spherePoint(f, x + 0.5, z + 0.5); if (s == null) return null;
        Towns tw = Towns.on ? w.towns() : null; if (tw != null) { Towns.Hit h = tw.at(f, x + 0.5, z + 0.5, s); if (h != null && h.plan != null) return new Place(h.plan.site, h.plan, null, f); }
        Metro m = Metro.on ? w.metroIfMade() : null; if (m != null) for (Metro.Plan p : m.near(s)) { if (p.face != f) continue; double[] uv = p.uv(x + 0.5, z + 0.5); if (Math.abs(uv[0]) <= p.reachM && Math.abs(uv[1]) <= p.reachM) return new Place(p.site, null, p, f); }
        return null; }
    /** The lot holding block (x, z), or null (a street, a city, open land). */
    public static Lot lotAt(ServerLevel l, int x, int z) { PlanetWorld w = w(); int f = HopelessInfinity.face(l); if (w == null || f < 0) return null; double[] s = w.cube.spherePoint(f, x + 0.5, z + 0.5); if (s == null) return null;
        Towns tw = Towns.on ? w.towns() : null; if (tw == null) return null; Towns.Hit h = tw.at(f, x + 0.5, z + 0.5, s); if (h == null || h.plan == null || h.kind != Towns.Kind.LOT) return null;
        return lot(new Place(h.plan.site, h.plan, null, f), h.bi, h.bj, h.row, h.lot, h.use); }
    static Lot lot(Place pl, int i, int j, int row, int lot, Towns.Use use) { return of(pl, PlatLots.lot(pl.plan, i, j, row, lot, use)); }
    static Lot of(Place pl, PlatLots.Lot l) { return new Lot(pl, l.key(), l.use(), l.bi(), l.bj(), l.row(), l.lot(), l.x(), l.z(), l.seed()); }
    /** Every lot of a town's plat (planet/PlatLots), for the systems that look for a use. */
    public static List<Lot> lots(Place pl) { List<Lot> out = new ArrayList<>(); if (pl.plan == null) return out; for (PlatLots.Lot l : PlatLots.lots(pl.plan)) out.add(of(pl, l)); return out; }
    /** The places with plans within r blocks of (x, z) on level l's face, nearest first. */
    public static List<Place> near(ServerLevel l, int x, int z, int r) { PlanetWorld w = w(); int f = HopelessInfinity.face(l); List<Place> out = new ArrayList<>(); if (w == null || f < 0) return out; double[] s = w.cube.spherePoint(f, x + 0.5, z + 0.5); if (s == null) return out;
        Towns tw = Towns.on ? w.towns() : null; Metro m = Metro.on ? w.metroIfMade() : null;
        for (Sites.Site st : w.sites().nearest(s, null, 40)) { double d = Math.acos(Math.max(-1, Math.min(1, s[0] * st.s[0] + s[1] * st.s[1] + s[2] * st.s[2]))) * w.cube.R; if (d > r) continue;
            if (Metro.isCity(st) && m != null) { Metro.Plan p = m.plan(st); if (p.face == f) out.add(new Place(st, null, p, f)); continue; }
            if (tw != null && st.kind != Sites.Kind.LARGE_CITY) { Towns.Plan p = tw.plan(st); if (p != null && p.face == f) out.add(new Place(st, p, null, f)); } }
        return out; }
    public static String use(Towns.Use u) { String s = u.name().toLowerCase().replace('_', ' '); return Character.toUpperCase(s.charAt(0)) + s.substring(1); }
    public static String addr(Lot lt) { return use(lt.use) + ", " + lt.place.name() + " (" + lt.x + ", " + lt.z + ")"; }
    /** Compass words for a heading from (x0, z0) to (x1, z1) (north is -z). */
    public static String dir(double x0, double z0, double x1, double z1) { double a = Math.toDegrees(Math.atan2(x1 - x0, -(z1 - z0))); if (a < 0) a += 360;
        return new String[]{"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"}[(int) Math.round(a / 45) % 8]; }
    public static String far(double x0, double z0, double x1, double z1) { double d = Math.hypot(x1 - x0, z1 - z0); return (d >= 1000 ? String.format("%.1f km", d / 1000) : String.format("%.0f m", d)) + " " + dir(x0, z0, x1, z1); }
}
