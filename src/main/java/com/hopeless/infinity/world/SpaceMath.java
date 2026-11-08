package com.hopeless.infinity.world;

import com.hopeless.infinity.galaxy.Orbits;
import com.hopeless.infinity.planet.PlanetWorld;

/** 0.12.0: space's pure physics (no Minecraft classes, for tools/SpaceTest): see world/Space. */
public final class SpaceMath {
    private SpaceMath() {}
    public static final int SUB = 10;   // sub-steps a tick
    /** The planet's sidereal spin rate (rad/s) about its pole (planet space +z). */
    public static double omega(PlanetWorld w) { Orbits o = new Orbits(w.system); var s = o.planetSpin[w.planet.index]; return 2 * Math.PI / (s.locked() ? o.planet[w.planet.index].periodS() : s.periodS()); }
    /** One tick (0.05 s) of P, V (planet space, the turning frame) under gravity GM, spin w about +z, thrust a (m/s2). */
    public static void step(double[] p, double[] v, double gm, double w, double[] a) {
        double dt = 0.05 / SUB;
        for (int k = 0; k < SUB; k++) { double r = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]), g = -gm / (r * r * r);
            double ax = g * p[0] + 2 * w * v[1] + w * w * p[0] + a[0], ay = g * p[1] - 2 * w * v[0] + w * w * p[1] + a[1], az = g * p[2] + a[2];   // -2 w x V = (2w vy, -2w vx, 0); -w x (w x P) = (w^2 x, w^2 y, 0)
            v[0] += ax * dt; v[1] += ay * dt; v[2] += az * dt; p[0] += v[0] * dt; p[1] += v[1] * dt; p[2] += v[2] * dt; } }
}
