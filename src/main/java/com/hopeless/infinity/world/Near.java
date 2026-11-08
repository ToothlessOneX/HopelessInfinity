package com.hopeless.infinity.world;

import com.hopeless.infinity.galaxy.Orbits;
import com.hopeless.infinity.planet.Bodies;
import com.hopeless.infinity.planet.PlanetWorld;
import com.hopeless.infinity.sky.Ephemeris;

/**
 * 0.16.0 / 0.18.0 THE BODY BELOW in space: space keeps the craft's place and motion in the home planet's turning frame (planet space: the
 * planet's cube axes, metres). The ground below is the body whose sphere of influence (a (m / M)^(2/5): Laplace) holds the craft, the
 * innermost one (a moon's inside its planet's); outside every planet's, the planet nearest in units of its sphere (the star has no ground).
 * This holds that body's centre and axes in planet space at a moment, from the real orbits and spins (sky/Ephemeris), turns points and
 * velocities between planet space and the body's own (its cube's) frame, and sums every body's pull (Sys). Pure (tools/NearTest).
 */
public final class Near {
    public final int body; public final PlanetWorld w; public final double t; public final double[] c; public final double[][] ax;   // ax: the body's x, y, z axes in planet space
    Near(int body, PlanetWorld w, double t, double[] c, double[][] ax) { this.body = body; this.w = w; this.t = t; this.c = c; this.ax = ax; }
    public static Near planet(PlanetWorld home, double t) { return new Near(0, home, t, new double[3], new double[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}); }
    /** The home moon (body 1) at t. */
    public static Near moon(PlanetWorld home, Ephemeris e, double t) { return body(home, e, t, 1); }
    /** Any body at t: its centre and axes in planet space (null: no such body). */
    public static Near body(PlanetWorld home, Ephemeris e, double t, int id) {
        if (id == 0 || e == null) return planet(home, t); PlanetWorld bw = home.body(id); if (bw == null) return null;
        double[][] pa = e.planetAxes(t); double[] hp = e.planetCentre(t), bc = e.centre(bw.ref, t), rel = {(bc[0] - hp[0]) * 1000, (bc[1] - hp[1]) * 1000, (bc[2] - hp[2]) * 1000};
        double[] c = {dot(rel, pa[0]), dot(rel, pa[1]), dot(rel, pa[2])}; double[][] ba = e.axes(bw.ref, t), ax = new double[3][];
        for (int i = 0; i < 3; i++) ax[i] = new double[]{dot(ba[i], pa[0]), dot(ba[i], pa[1]), dot(ba[i], pa[2])};
        return new Near(id, bw, t, c, ax); }
    /** The home moon's sphere of influence (m). */
    public static double soi(PlanetWorld home) { return soi(home, home.bodies.get(1)); }
    /** A body's sphere of influence (m): a planet's round the star, a moon's round its planet. */
    public static double soi(PlanetWorld home, Bodies.Ref r) { return Sys.soiOf(home, r, Sys.of(home).gmStar); }
    /** Which body is below the point p (planet space) at t. */
    public static Near of(PlanetWorld home, Ephemeris e, double t, double[] p) {
        if (e == null || home.bodies.size() < 2) return planet(home, t); Sys sy = Sys.of(home); double[][] cs = sy.centres(e, t);
        int best = -1; double bestK = 1e300; boolean inside = false;
        for (int k = 0; k < sy.ids.length; k++) { if (sy.refs[k].isMoon()) continue; double q = len(sub(p, cs[k])) / sy.soi[k]; if (q < 1) { best = k; inside = true; break; } if (q < bestK) { bestK = q; best = k; } }
        if (inside) for (int k = 0; k < sy.ids.length; k++) if (sy.refs[k].isMoon() && sy.refs[k].planet() == sy.refs[best].planet() && len(sub(p, cs[k])) < sy.soi[k]) { best = k; break; }
        return body(home, e, t, sy.ids[best]); }
    public Near at(PlanetWorld home, Ephemeris e, double t2) { return body(home, e, t2, body); }
    /** Planet space -> the body's frame (a point: its offset from the centre). */
    public double[] toBody(double[] p) { double[] d = sub(p, c); return new double[]{dot(d, ax[0]), dot(d, ax[1]), dot(d, ax[2])}; }
    public double[] toPlanet(double[] b) { double[] r = c.clone(); for (int i = 0; i < 3; i++) for (int k = 0; k < 3; k++) r[k] += b[i] * ax[i][k]; return r; }
    /** A direction (no offset) both ways. */
    public double[] dirToBody(double[] v) { return new double[]{dot(v, ax[0]), dot(v, ax[1]), dot(v, ax[2])}; }
    public double[] dirToPlanet(double[] b) { double[] r = new double[3]; for (int i = 0; i < 3; i++) for (int k = 0; k < 3; k++) r[k] += b[i] * ax[i][k]; return r; }
    /** The planet-space velocity of the body-fixed point b (the body's own motion and turn), by a 1 s central difference. */
    public double[] pointVel(PlanetWorld home, Ephemeris e, double[] b) { if (body == 0) return new double[3]; Near n1 = at(home, e, t - 0.5), n2 = at(home, e, t + 0.5); return sub(n2.toPlanet(b), n1.toPlanet(b)); }   // 0.18.0 central: far bodies swing ~500 m/s2 in the turning frame (w^2 r), a forward step was off by half that
    /** The home moon's pull on a craft at p, as felt in the planet's frame (its pull less its pull on the planet). (0.16.0; Sys.pull sums every body.) */
    public static double[] moonPull(PlanetWorld home, Near m, double[] p) {
        PlanetWorld mw = home.moon(); double gm = mw.gravity * mw.cube.R * mw.cube.R; double[] d = sub(m.c, p); double r = len(d), rc = len(m.c), a[] = new double[3];
        for (int k = 0; k < 3; k++) a[k] = gm * (d[k] / (r * r * r) - m.c[k] / (rc * rc * rc)); return a; }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double len(double[] a) { return Math.sqrt(dot(a, a)); }
    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }

    /** 0.18.0 every body of the system with mass, for pulls, limits and stops: the star first (index 0 of the arrays here: id -1). */
    public static final class Sys {
        public final int[] ids; public final Bodies.Ref[] refs; public final double[] gm, radius, soi; public final boolean[] land; public final double gmStar;
        static volatile Sys cache; static volatile long cacheSeed = Long.MIN_VALUE;
        public static Sys of(PlanetWorld home) { Sys s = cache; if (s == null || cacheSeed != home.seed) { s = new Sys(home); cache = s; cacheSeed = home.seed; } return s; }
        Sys(PlanetWorld home) {
            gmStar = new Orbits(home.system).gmStar * 1e9; int n = home.bodies.size(); ids = new int[n]; refs = new Bodies.Ref[n]; gm = new double[n]; radius = new double[n]; soi = new double[n]; land = new boolean[n];
            for (int k = 0; k < n; k++) { Bodies.Ref r = home.bodies.get(k); ids[k] = r.id(); refs[k] = r; var p = home.system.planets.get(r.planet());
                PlanetWorld bw = home.body(r.id()); gm[k] = bw.gravity * bw.cube.R * bw.cube.R; radius[k] = bw.cube.R;   // g R^2: the same GM the surface gravity (and space's 0.12-0.17 physics) uses
                land[k] = r.id() == 0 || home.body(r.id()).landable(); }
            for (int k = 0; k < n; k++) soi[k] = soiOf(home, refs[k], gmStar); }
        static double soiOf(PlanetWorld home, Bodies.Ref r, double gmStar) { var p = home.system.planets.get(r.planet());
            if (r.isMoon()) { var m = p.moons.get(r.moon()); return m.orbitKm() * 1000 * Math.pow(m.massE() / p.massE, 0.4); }
            double mStarE = gmStar / 1e9 / (Orbits.G * Orbits.M_EARTH_KG); return p.au * Orbits.AU_KM * 1000 * Math.pow(p.massE / mStarE, 0.4); }
        /** Every body's centre in planet space (the turning frame of t; [0] = the home planet: 0). */
        public double[][] centres(Ephemeris e, double t) { return centres(e, t, e.planetAxes(t)); }
        /** ... projected on the axes pa (planet axes at some moment: the non-turning frame of that moment). */
        public double[][] centres(Ephemeris e, double t, double[][] pa) { double[] hp = e.planetCentre(t); double[][] out = new double[ids.length][];
            for (int k = 0; k < ids.length; k++) { if (ids[k] == 0) { out[k] = new double[3]; continue; } double[] bc = e.centre(refs[k], t), rel = {(bc[0] - hp[0]) * 1000, (bc[1] - hp[1]) * 1000, (bc[2] - hp[2]) * 1000};
                out[k] = new double[]{dot(rel, pa[0]), dot(rel, pa[1]), dot(rel, pa[2])}; }
            return out; }
        /** The star's centre likewise. */
        public double[] star(Ephemeris e, double t, double[][] pa) { double[] hp = e.planetCentre(t), rel = {-hp[0] * 1000, -hp[1] * 1000, -hp[2] * 1000}; return new double[]{dot(rel, pa[0]), dot(rel, pa[1]), dot(rel, pa[2])}; }
        /** One body's centre on the axes pa at t (k: index here). */
        public double[] centre(Ephemeris e, int k, double t, double[][] pa) { if (ids[k] == 0) return new double[3]; double[] hp = e.planetCentre(t), bc = e.centre(refs[k], t), rel = {(bc[0] - hp[0]) * 1000, (bc[1] - hp[1]) * 1000, (bc[2] - hp[2]) * 1000};
            return new double[]{dot(rel, pa[0]), dot(rel, pa[1]), dot(rel, pa[2])}; }
        /** The planet (index) whose sphere of influence holds P, or -1 (the star's). cs: every centre. */
        public int system(double[] P, double[][] cs) { for (int k = 0; k < ids.length; k++) if (!refs[k].isMoon() && len(sub(P, cs[k])) < soi[k]) return k; return -1; }
        /**
         * The acceleration of a craft at P (the non-turning frame of axes pa, the home planet at the origin) at t, PATCHED: the bodies' paths are
         * Kepler orbits (a planet's round the star, a moon's round its planet), so a craft feels the bodies of the system it is in, the same way
         * (Kerbal Space Program's patched conics, STATED as the model; within the home planet's sphere exactly the 0.16.0 physics):
         *  - in a planet's sphere: that planet and its moons (each moon's pull less its pull on the planet: the moons' orbits use the total mass);
         *  - outside every planet's sphere: the star and every planet;
         * plus the acceleration of that system's centre in this frame (its Kepler path, by a 60 s second difference: the frame falls with the home
         * planet). Returns the system: the planet's index, or -1 the star.
         */
        public int accel(Ephemeris e, double t, double[][] pa, double[][] cs, double[] P, double[] out) {
            int X = system(P, cs); out[0] = out[1] = out[2] = 0;
            if (X >= 0) { double[] cx = cs[X]; add0(out, gm[X], cx, P);
                for (int k = 0; k < ids.length; k++) if (refs[k].isMoon() && refs[k].planet() == refs[X].planet()) { double[] cm = cs[k]; add0(out, gm[k], cm, P); add0(out, -gm[k], cm, cx); }
                if (ids[X] != 0) addDdot(e, t, pa, X, out); }
            else { double[] sc = star(e, t, pa); add0(out, gmStar, sc, P); for (int k = 0; k < ids.length; k++) if (!refs[k].isMoon()) add0(out, gm[k], cs[k], P); addDdot(e, t, pa, -1, out); }
            return X; }
        /** a += gm (C - P) / |C - P|^3. */
        static void add0(double[] a, double gm, double[] C, double[] P) { double dx = C[0] - P[0], dy = C[1] - P[1], dz = C[2] - P[2], r2 = dx * dx + dy * dy + dz * dz, k1 = gm / (r2 * Math.sqrt(r2)); a[0] += k1 * dx; a[1] += k1 * dy; a[2] += k1 * dz; }
        void addDdot(Ephemeris e, double t, double[][] pa, int k, double[] a) { double h = 60; double[] c0 = k < 0 ? star(e, t - h, pa) : centre(e, k, t - h, pa), c1 = k < 0 ? star(e, t, pa) : centre(e, k, t, pa), c2 = k < 0 ? star(e, t + h, pa) : centre(e, k, t + h, pa);
            for (int j = 0; j < 3; j++) a[j] += (c2[j] - 2 * c1[j] + c0[j]) / (h * h); }
        /** (0.18.0 first cut, kept for tools: every body's pull less its pull on the home planet, unpatched.) */
        public double[] pull(double[] P, double[][] cs, double[] sc, double[] out) {
            out[0] = out[1] = out[2] = 0;
            for (int k = 0; k < ids.length; k++) { if (ids[k] == 0) continue; add(out, gm[k], cs[k], P); }
            add(out, gmStar, sc, P); return out; }
        static void add(double[] a, double gm, double[] C, double[] P) { double dx = C[0] - P[0], dy = C[1] - P[1], dz = C[2] - P[2], r2 = dx * dx + dy * dy + dz * dz, r = Math.sqrt(r2), rc = Math.sqrt(C[0] * C[0] + C[1] * C[1] + C[2] * C[2]);
            double k1 = gm / (r2 * r), k2 = gm / (rc * rc * rc); a[0] += k1 * dx - k2 * C[0]; a[1] += k1 * dy - k2 * C[1]; a[2] += k1 * dz - k2 * C[2]; }
    }
}
