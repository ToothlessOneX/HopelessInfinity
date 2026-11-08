package com.hopeless.infinity.planet;

import com.hopeless.infinity.galaxy.StarSystem;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.18.0 THE BODIES OF THE HOME SYSTEM as worlds (owner, 2026-11-03: "what about the rest of the planets"). Each planet and moon gets a body id:
 * 0 the home planet and 1 its first moon (as before), then every other planet and moon in order (planet by planet, its moons after it).
 * Bodies with no surface (giants and mini-Neptunes: hydrogen-helium envelopes) have an id for space flight but no faces. Pure.
 */
public final class Bodies {
    private Bodies() {}
    /** One body: its id, its planet's index in the system, and its moon index there (-1: the planet itself). */
    public record Ref(int id, int planet, int moon) { public boolean isMoon() { return moon >= 0; } }
    /** A short name for logs and the HUD. */
    public static String name(Ref r) { return r.id() == 0 ? "home planet" : r.id() == 1 ? "home moon" : r.isMoon() ? "moon " + (r.moon() + 1) + " of planet " + (r.planet() + 1) : "planet " + (r.planet() + 1); }
    public static List<Ref> of(StarSystem s, int home) {
        List<Ref> l = new ArrayList<>(); l.add(new Ref(0, home, -1)); if (!s.planets.get(home).moons.isEmpty()) l.add(new Ref(1, home, 0)); int next = 2;
        for (int i = 0; i < s.planets.size(); i++) { if (i != home) l.add(new Ref(next++, i, -1)); for (int j = 0; j < s.planets.get(i).moons.size(); j++) if (i != home || j != 0) l.add(new Ref(next++, i, j)); }
        return l; }
}
