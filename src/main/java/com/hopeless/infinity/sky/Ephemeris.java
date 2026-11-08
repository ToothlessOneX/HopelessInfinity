package com.hopeless.infinity.sky;

import com.hopeless.infinity.galaxy.Orbits;
import com.hopeless.infinity.galaxy.Planet;
import com.hopeless.infinity.planet.PlanetWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.8.0 THE REAL SKY, stage 1-2: where the star, every planet and every moon stand as seen from one place on the home planet at one moment,
 * from galaxy/Orbits (Kepler orbits, real spin and tilt). The observer turns with the planet (its real sidereal spin about its real pole),
 * so the star rises, culminates and sets where and when it really does; moons and planets show their real phase from the real geometry;
 * eclipses come from the real disks overlapping. Frames: S = the system's reference frame (x, y in the invariable plane, z its normal,
 * the frame the star field is baked in); the planet's body frame has z on its pole (CubeSphere's latitude); local = Minecraft's (y up).
 */
public final class Ephemeris {
    public final PlanetWorld w; public final Orbits orb; public final int home; public final double starKm, epochS;
    final double[] u0, v0, pole;   // the body frame's equator axes at turn 0 and its pole, in S
    final double[][] pu0, pv0, ppole;   // 0.18.0 the same for every planet of the system

    public Ephemeris(PlanetWorld w) {
        this.w = w; orb = new Orbits(w.system); home = w.planet.index; starKm = w.system.primary.radiusKm;
        pole = Orbits.pole(orb.planet[home], orb.planetSpin[home]); u0 = perp(pole); v0 = cross(pole, u0);
        int np = w.system.planets.size(); pu0 = new double[np][]; pv0 = new double[np][]; ppole = new double[np][];
        for (int i = 0; i < np; i++) { ppole[i] = Orbits.pole(orb.planet[i], orb.planetSpin[i]); pu0[i] = perp(ppole[i]); pv0[i] = cross(ppole[i], pu0[i]); }
        epochS = (Math.floorMod(w.seed * 0x9E3779B97F4A7C15L, 1L << 40) / (double) (1L << 40)) * orb.planet[home].periodS();   // the world starts somewhere in its year
    }
    /** One body in the sky. kind: 0 star, 1 rocky, 2 giant, 3 moon. */
    public record Body(String name, int kind, double[] dir, double angR, double distKm, double radiusKm, double albedo, double light, double flux, long seed, double[] sunDir) {}
    /** The sky at a place and moment: the local frame in S, the star, the bodies (nearest first not needed: the shader takes the disk in front). */
    public final class At {
        public final double t; public final double[] up = new double[3], north = new double[3], east = new double[3]; public double[] obs, sun; public double sunAngR, sunDistKm, sunVisible; public final List<Body> bodies = new ArrayList<>();
        public double[] northMc;   // north in the block plane (x, z)
        public double hourAngle, sunAlt, lat, sunDec;
        /** 0.15.0: the observer's body: -1 the home planet, j its moon j. */
        public int onMoon = -1;
        /** 0.15.0 from a moon: the home planet's body axes (its cube's x, y, z) in the local frame, for its map on the disk. */
        public double[][] homeAxes;
        /** 0.18.0 the observer's body id (0 the home planet) and the sky it has: 0 air (the home planet's kind of scattering), 1 airless, 2 a cloud deck (thick CO2 air: no star disk, a dim orange sky). */
        public int body = 0, skyMode = 0;
        /** 0.18.0 on any other body of the system (its world bw): its cube point (f, x, z). */
        At(PlanetWorld bw, int f, double x, double z, double t) {
            this.t = t; body = bw.body; onMoon = bw.body;
            double[] s = bw.cube.spherePoint(f, x, z); double[] fr = frame(bw.cube, f, x, z); northMc = new double[]{fr[1], fr[2]};
            if (s == null) s = new double[]{0, 1, 0};
            double[][] ax = axes(bw.ref, t); for (int i = 0; i < 3; i++) up[i] = s[0] * ax[0][i] + s[1] * ax[1][i] + s[2] * ax[2][i];
            double[] bc = centre(bw.ref, t); double R = bw.radiusKm;
            skyMode = skyMode(bw); fillAny(ax[2], new double[]{bc[0] + R * up[0], bc[1] + R * up[1], bc[2] + R * up[2]}, bw.ref); }
        /** The local frame and the sky from any body: the star, every other planet (the home planet with its map), the moons of the observer's planet. */
        void fillAny(double[] bodyPole, double[] obsPos, com.hopeless.infinity.planet.Bodies.Ref self) {
            double pu = dot(bodyPole, up); double[] nn = {bodyPole[0] - pu * up[0], bodyPole[1] - pu * up[1], bodyPole[2] - pu * up[2]}; double nl = len(nn);
            if (nl < 1e-9) nn = perp(up); else nn = mul(nn, 1 / nl); System.arraycopy(nn, 0, north, 0, 3); double[] ee = cross(north, up); System.arraycopy(ee, 0, east, 0, 3);
            obs = obsPos; double[] toStar = {-obs[0], -obs[1], -obs[2]}; sunDistKm = len(toStar); sun = local(mul(toStar, 1 / sunDistKm)); sunAngR = Math.asin(Math.min(1, starKm / sunDistKm)); sunAlt = Math.asin(Math.max(-1, Math.min(1, sun[1])));
            double hx = dot(toStar, east), hn = dot(toStar, north), hu = dot(toStar, up); hourAngle = Math.atan2(-hx, -(hn * pu - hu * Math.sqrt(Math.max(0, 1 - pu * pu))));
            lat = Math.asin(Math.max(-1, Math.min(1, pu))); sunDec = Math.asin(Math.max(-1, Math.min(1, dot(toStar, bodyPole) / sunDistKm)));
            double occluded = 0; double[][] ha = planetAxesOf(home, t); homeAxes = new double[][]{local(ha[0]), local(ha[1]), local(ha[2])};
            for (int i = 0; i < w.system.planets.size(); i++) { if (!self.isMoon() && i == self.planet()) continue; Planet p = w.system.planets.get(i);
                double[] pp = Orbits.position(orb.planet[i], t + epochS), d = {pp[0] - obs[0], pp[1] - obs[1], pp[2] - obs[2]}; double dist = len(d), ang = Math.asin(Math.min(1, p.radiusKm / dist)); double[] dir = local(mul(d, 1 / dist));
                double alb = i == home ? 0.367 : p.kind == Planet.Kind.ROCKY ? 0.3 : p.kind == Planet.Kind.GAS_GIANT ? 0.538 : 0.488;
                double pd = len(pp), alpha = Math.acos(Math.max(-1, Math.min(1, dot(mul(pp, -1 / pd), mul(d, -1 / dist))))), flux = alb * Math.pow(p.radiusKm / dist, 2) * phaseIntegral(alpha) * Math.pow(sunDistKm / pd, 2);
                bodies.add(new Body(i == home ? "home planet" : "planet " + i, i == home ? 4 : p.giant() ? 2 : 1, dir, ang, dist, p.radiusKm, alb, 1, flux, 1000L + i, local(mul(pp, -1 / pd))));
                occluded = Math.max(occluded, overlap(sunAngR, ang, sep(dir, sun))); }
            Planet host = w.system.planets.get(self.planet()); double[] hp = Orbits.position(orb.planet[self.planet()], t + epochS);
            for (int j = 0; j < host.moons.size(); j++) { if (self.isMoon() && j == self.moon()) continue; Planet.Moon m = host.moons.get(j);
                double[] mp = moonPosOf(self.planet(), j, t), pos = {hp[0] + mp[0], hp[1] + mp[1], hp[2] + mp[2]}, d = {pos[0] - obs[0], pos[1] - obs[1], pos[2] - obs[2]}; double dist = len(d);
                double ang = Math.asin(Math.min(1, m.radiusKm() / dist)); double[] dir = local(mul(d, 1 / dist));
                double[] ms = {-pos[0], -pos[1], -pos[2]}; double msd = len(ms), sunFromMoon = Math.asin(Math.min(1, starKm / msd)), plFromMoon = Math.asin(Math.min(1, host.radiusKm / len(mp)));
                double sepM = Math.acos(Math.max(-1, Math.min(1, dot(mul(ms, 1 / msd), mul(mp, -1 / len(mp)))))), light = 1 - overlap(sunFromMoon, plFromMoon, sepM);
                double alb = m.kind() == Planet.MoonKind.REGULAR ? 0.4 : 0.12, flux = light * alb * Math.pow(m.radiusKm() / dist, 2) * phaseIntegral(sep(dir, sun)) * Math.pow(sunDistKm / msd, 2);
                bodies.add(new Body("moon " + j, 3, dir, ang, dist, m.radiusKm(), alb, light, flux, 31L * j + 7 + 101L * self.planet(), local(mul(ms, 1 / msd))));
                occluded = Math.max(occluded, overlap(sunAngR, ang, sep(dir, sun))); }
            bodies.sort((a, b) -> Double.compare(a.distKm(), b.distKm()));
            sunVisible = 1 - occluded; }
        At(int f, double x, double z, double t) {
            this.t = t;
            double[] s = w.cube.spherePoint(f, x, z); double[] fr = frame(w.cube, f, x, z); northMc = new double[]{fr[1], fr[2]};
            if (s == null) s = new double[]{1, 0, 0};
            double th = Orbits.turn(orb.planet[home], orb.planetSpin[home], t), c = Math.cos(th), sn = Math.sin(th);
            double[] e1 = add(mul(u0, c), mul(v0, sn)), e2 = add(mul(u0, -sn), mul(v0, c));
            for (int i = 0; i < 3; i++) up[i] = s[0] * e1[i] + s[1] * e2[i] + s[2] * pole[i];
            double[] hp = Orbits.position(orb.planet[home], t + epochS); double R = w.planet.radiusKm;
            fill(pole, new double[]{hp[0] + R * up[0], hp[1] + R * up[1], hp[2] + R * up[2]}, -1); }
        /** 0.15.0: on the home planet's moon j (locked: its near side, planet space +y of the moon's cube, faces the planet; its pole on its orbit's
         *  normal): the moon's cube point (f, x, z) of the moon world mw. */
        At(int j, PlanetWorld mw, int f, double x, double z, double t) {
            this.t = t; onMoon = j;
            double[] s = mw.cube.spherePoint(f, x, z); double[] fr = frame(mw.cube, f, x, z); northMc = new double[]{fr[1], fr[2]};
            if (s == null) s = new double[]{0, 1, 0};
            double[][] ax = moonAxes(j, t); for (int i = 0; i < 3; i++) up[i] = s[0] * ax[0][i] + s[1] * ax[1][i] + s[2] * ax[2][i];
            double[] mc = moonCentre(j, t); double R = mw.radiusKm;
            fill(ax[2], new double[]{mc[0] + R * up[0], mc[1] + R * up[1], mc[2] + R * up[2]}, j); }
        /** The common part: the local frame from 'up' and the observer body's pole, the star, the bodies in the sky (the observer's own body left out). */
        void fill(double[] bodyPole, double[] obsPos, int skipMoon) {
            double pu = dot(bodyPole, up); double[] nn = {bodyPole[0] - pu * up[0], bodyPole[1] - pu * up[1], bodyPole[2] - pu * up[2]}; double nl = len(nn);
            if (nl < 1e-9) nn = perp(up); else nn = mul(nn, 1 / nl); System.arraycopy(nn, 0, north, 0, 3); double[] ee = cross(north, up); System.arraycopy(ee, 0, east, 0, 3);
            obs = obsPos; double[] hp = Orbits.position(orb.planet[home], t + epochS); double R = w.planet.radiusKm;
            double[] toStar = {-obs[0], -obs[1], -obs[2]}; sunDistKm = len(toStar); sun = local(mul(toStar, 1 / sunDistKm)); sunAngR = Math.asin(Math.min(1, starKm / sunDistKm)); sunAlt = Math.asin(Math.max(-1, Math.min(1, sun[1])));
            double hx = dot(toStar, east), hn = dot(toStar, north), hu = dot(toStar, up); hourAngle = Math.atan2(-hx, -(hn * pu - hu * Math.sqrt(Math.max(0, 1 - pu * pu))));
            lat = Math.asin(Math.max(-1, Math.min(1, pu))); sunDec = Math.asin(Math.max(-1, Math.min(1, dot(toStar, bodyPole) / sunDistKm)));
            Planet hpRec = w.planet; double occluded = 0;
            // 0.15.0 from a moon: the home planet itself, a big disk with its real phase (its air's albedo: Earth's 0.367 geometric, NASA)
            if (skipMoon >= 0) { double[] d = {hp[0] - obs[0], hp[1] - obs[1], hp[2] - obs[2]}; double dist = len(d), ang = Math.asin(Math.min(1, R / dist)); double[] dir = local(mul(d, 1 / dist));
                double[] ps = {-hp[0], -hp[1], -hp[2]}; double psd = len(ps); double alb = 0.367;
                double alpha = Math.acos(Math.max(-1, Math.min(1, dot(mul(ps, 1 / psd), mul(d, -1 / dist))))), flux = alb * Math.pow(R / dist, 2) * phaseIntegral(alpha) * Math.pow(sunDistKm / psd, 2);
                bodies.add(new Body("home planet", 4, dir, ang, dist, R, alb, 1, flux, 999L, local(mul(ps, 1 / psd))));
                double th = Orbits.turn(orb.planet[home], orb.planetSpin[home], t), c = Math.cos(th), sn = Math.sin(th); double[] e1 = add(mul(u0, c), mul(v0, sn)), e2 = add(mul(u0, -sn), mul(v0, c));
                homeAxes = new double[][]{local(e1), local(e2), local(pole)};
                occluded = Math.max(occluded, overlap(sunAngR, ang, sep(dir, sun))); }
            // the home planet's moons
            for (int j = 0; j < hpRec.moons.size(); j++) { if (j == skipMoon) continue; Planet.Moon m = hpRec.moons.get(j); Orbits.Elements o = orb.moon[home][j];
                double[] mp = moonPos(m, o, t + epochS), pos = {hp[0] + mp[0], hp[1] + mp[1], hp[2] + mp[2]}, d = {pos[0] - obs[0], pos[1] - obs[1], pos[2] - obs[2]}; double dist = len(d);
                double ang = Math.asin(Math.min(1, m.radiusKm() / dist)); double[] dir = local(mul(d, 1 / dist));
                // lunar eclipse: the share of the star the moon sees past the home planet
                double[] ms = {-pos[0], -pos[1], -pos[2]}; double msd = len(ms), sunFromMoon = Math.asin(Math.min(1, starKm / msd)), plFromMoon = Math.asin(Math.min(1, R / len(mp)));
                double sepM = Math.acos(Math.max(-1, Math.min(1, dot(mul(ms, 1 / msd), mul(mp, -1 / len(mp))))));
                double light = 1 - overlap(sunFromMoon, plFromMoon, sepM);
                double alb = m.kind() == Planet.MoonKind.REGULAR ? 0.4 : 0.12;   // icy regular moons (Galilean geometric albedos 0.4-0.7, GAME low end); rocky ones the Moon's 0.12
                double phase = phaseIntegral(sep(dir, sun)), flux = light * alb * Math.pow(m.radiusKm() / dist, 2) * phase * Math.pow(sunDistKm / msd, 2);
                bodies.add(new Body("moon " + j, 3, dir, ang, dist, m.radiusKm(), alb, light, flux, 31L * j + 7, local(mul(ms, 1 / msd))));
                occluded = Math.max(occluded, overlap(sunAngR, ang, sep(dir, sun))); }
            // the other planets (and their biggest moons are too small to see: left out)
            for (int i = 0; i < w.system.planets.size(); i++) { if (i == home) continue; Planet p = w.system.planets.get(i);
                double[] pp = Orbits.position(orb.planet[i], t + epochS), d = {pp[0] - obs[0], pp[1] - obs[1], pp[2] - obs[2]}; double dist = len(d), ang = Math.asin(Math.min(1, p.radiusKm / dist)); double[] dir = local(mul(d, 1 / dist));
                double alb = p.kind == Planet.Kind.ROCKY ? 0.3 : p.kind == Planet.Kind.GAS_GIANT ? 0.538 : 0.488;   // geometric albedos: Earth 0.367/Mars 0.17 (0.3 between), Jupiter 0.538, Neptune 0.442/Uranus 0.488 (NASA fact sheets)
                double pd = len(pp), alpha = Math.acos(Math.max(-1, Math.min(1, dot(mul(pp, -1 / pd), mul(d, -1 / dist))))), flux = alb * Math.pow(p.radiusKm / dist, 2) * phaseIntegral(alpha) * Math.pow(sunDistKm / pd, 2);
                bodies.add(new Body("planet " + i, p.giant() ? 2 : 1, dir, ang, dist, p.radiusKm, alb, 1, flux, 1000L + i, local(mul(pp, -1 / pd))));
                occluded = Math.max(occluded, overlap(sunAngR, ang, sep(dir, sun))); }
            sunVisible = 1 - occluded;
        }
        /** An S direction in the local (Minecraft) frame: x east?, y up, z: the block plane's axes (north given by northMc). */
        public double[] local(double[] d) { double de = dot(d, east), du = dot(d, up), dn = dot(d, north); double nx = northMc[0], nz = northMc[1];
            double ex = -nz, ez = nx;   // east = north x up in the block plane: (nx, 0, nz) x (0, 1, 0) = (-nz, 0, nx)
            return new double[]{dn * nx + de * ex, du, dn * nz + de * ez}; }
        /** The local frame's axes for the star field (S x, y, z in local): the shader's u_Noon = S x, u_West = -S y, u_Pole = S z, lst 0. */
        public double[][] starAxes() { return new double[][]{local(new double[]{1, 0, 0}), local(new double[]{0, -1, 0}), local(new double[]{0, 0, 1})}; }
    }
    public At at(int f, double x, double z, double t) { return new At(f, x, z, t); }
    /** 0.15.0: the sky at a point (f, x, z) of the home planet's moon j (its world mw). */
    public At atMoon(int j, PlanetWorld mw, int f, double x, double z, double t) { At a = new At(j, mw, f, x, z, t); a.body = mw.body; a.skyMode = 1; return a; }
    /** 0.18.0 the sky at a point of any body's world (the home planet: at; its moon: atMoon; the rest: their own). */
    public At atBody(PlanetWorld bw, int f, double x, double z, double t) { if (bw.body == 0) return at(f, x, z, t); if (bw.body == 1) return atMoon(0, bw, f, x, z, t); return new At(bw, f, x, z, t); }
    /** Which sky a body has: thin or no air (under 0.1 atm, CHOSEN) airless, thick CO2 (Venus-like) a cloud deck, the rest the home planet's kind. */
    public static int skyMode(PlanetWorld bw) { var a = bw.atm; if (a.kind == com.hopeless.infinity.galaxy.Atmosphere.Kind.NONE || a.pressureAtm < 0.1) return 1; if (a.kind == com.hopeless.infinity.galaxy.Atmosphere.Kind.VENUS_LIKE) return 2; return 0; }
    /** 0.16.0: the home planet's body axes (its cube's x, y, z) in S at t, and its centre (S, km). */
    public double[][] planetAxes(double t) { double th = Orbits.turn(orb.planet[home], orb.planetSpin[home], t), c = Math.cos(th), sn = Math.sin(th); return new double[][]{add(mul(u0, c), mul(v0, sn)), add(mul(u0, -sn), mul(v0, c)), pole.clone()}; }
    public double[] planetCentre(double t) { return Orbits.position(orb.planet[home], t + epochS); }
    /** The moon j's centre (S, km) at t. */
    public double[] moonCentre(int j, double t) { double[] hp = Orbits.position(orb.planet[home], t + epochS), mp = moonPos(w.planet.moons.get(j), orb.moon[home][j], t + epochS); return new double[]{hp[0] + mp[0], hp[1] + mp[1], hp[2] + mp[2]}; }
    /** The moon j's body axes in S at t (its cube's x, y, z): locked, so y (the near side) points at the planet; z its pole on the orbit's normal. */
    public double[][] moonAxes(int j, double t) {
        Planet.Moon m = w.planet.moons.get(j); double[] mp = moonPos(m, orb.moon[home][j], t + epochS), mp2 = moonPos(m, orb.moon[home][j], t + epochS + 60);
        double[] toPl = mul(mp, -1 / len(mp)), nrm = cross(mp, sub(mp2, mp)); nrm = mul(nrm, 1 / len(nrm));
        double[] y = sub(toPl, mul(nrm, dot(toPl, nrm))); y = mul(y, 1 / len(y));
        // the spin is uniform (the mean motion) while the orbit is not: turn back by the equation of centre, nu - M = 2 e sin M + 1.25 e^2 sin 2M,
        // so the planet wanders round the near side's centre (optical libration in longitude, +-2e)
        Orbits.Elements o = orb.moon[home][j]; double M = o.m0() + 2 * Math.PI * (t + epochS) / o.periodS(), dnu = 2 * o.e() * Math.sin(M) + 1.25 * o.e() * o.e() * Math.sin(2 * M);
        double[] yx = cross(nrm, y); y = add(mul(y, Math.cos(-dnu)), mul(yx, Math.sin(-dnu)));
        double[] x = cross(y, nrm); return new double[][]{x, y, nrm}; }
    // ---------------- 0.18.0 every body of the system (planet/Bodies) ----------------
    /** The planet i's cube axes in S at t (its real spin about its real pole; a locked planet keeps one face to the star). */
    public double[][] planetAxesOf(int i, double t) { double th = Orbits.turn(orb.planet[i], orb.planetSpin[i], t), c = Math.cos(th), sn = Math.sin(th); return new double[][]{add(mul(pu0[i], c), mul(pv0[i], sn)), add(mul(pu0[i], -sn), mul(pv0[i], c)), ppole[i].clone()}; }
    /** The moon j of planet i: its place from the planet (S, km) at t (regular moons in the planet's equator, the rest near its orbit plane). */
    public double[] moonPosOf(int i, int j, double t) { Planet.Moon m = w.system.planets.get(i).moons.get(j); double[] q = Orbits.position(orb.moon[i][j], t + epochS), a, b, c;
        if (m.kind() == Planet.MoonKind.REGULAR) { a = pu0[i]; b = pv0[i]; c = ppole[i]; } else { c = Orbits.normal(orb.planet[i]); a = perp(c); b = cross(c, a); }
        return new double[]{q[0] * a[0] + q[1] * b[0] + q[2] * c[0], q[0] * a[1] + q[1] * b[1] + q[2] * c[1], q[0] * a[2] + q[1] * b[2] + q[2] * c[2]}; }
    /** A body's centre (S, km) at t. */
    public double[] centre(com.hopeless.infinity.planet.Bodies.Ref b, double t) { double[] pp = Orbits.position(orb.planet[b.planet()], t + epochS); if (b.moon() < 0) return pp; double[] mp = moonPosOf(b.planet(), b.moon(), t); return add(pp, mp); }
    /** A body's cube axes (x, y, z) in S at t: a planet's spin; a moon locked to its planet as moonAxes (its near side, +y, toward the planet). */
    public double[][] axes(com.hopeless.infinity.planet.Bodies.Ref b, double t) { if (b.moon() < 0) return planetAxesOf(b.planet(), t);
        int i = b.planet(), j = b.moon(); double[] mp = moonPosOf(i, j, t), mp2 = moonPosOf(i, j, t + 60);
        double[] toPl = mul(mp, -1 / len(mp)), nrm = cross(mp, sub(mp2, mp)); nrm = mul(nrm, 1 / len(nrm)); double[] y = sub(toPl, mul(nrm, dot(toPl, nrm))); y = mul(y, 1 / len(y));
        Orbits.Elements o = orb.moon[i][j]; double M = o.m0() + 2 * Math.PI * (t + epochS) / o.periodS(), dnu = 2 * o.e() * Math.sin(M) + 1.25 * o.e() * o.e() * Math.sin(2 * M);
        double[] yx = cross(nrm, y); y = add(mul(y, Math.cos(-dnu)), mul(yx, Math.sin(-dnu))); double[] x = cross(y, nrm); return new double[][]{x, y, nrm}; }
    /** As SkyModel.frame: {latitude, north x, north z} in the block plane (where latitude grows). */
    public double[] frame(int f, double x, double z) { return frame(w.cube, f, x, z); }
    public static double[] frame(com.hopeless.infinity.planet.CubeSphere cube, int f, double x, double z) {
        double[] s = cube.spherePoint(f, x, z), sx = cube.spherePoint(f, x + 16, z), sz = cube.spherePoint(f, x, z + 16);
        if (s == null || sx == null || sz == null) return new double[]{0, 0, -1};
        double la = Math.asin(Math.max(-1, Math.min(1, s[2]))), gx = Math.asin(Math.max(-1, Math.min(1, sx[2]))) - la, gz = Math.asin(Math.max(-1, Math.min(1, sz[2]))) - la, gl = Math.hypot(gx, gz);
        return gl > 1e-12 ? new double[]{la, gx / gl, gz / gl} : new double[]{la, 0, -1}; }
    /** The solar day (s) of the home planet. */
    public double solarDay() { return Orbits.solarDay(orb.planet[home], orb.planetSpin[home]); }

    double[] moonPos(Planet.Moon m, Orbits.Elements o, double t) {
        double[] q = Orbits.position(o, t), a, b, c;
        if (m.kind() == Planet.MoonKind.REGULAR) { a = u0; b = v0; c = pole; }                          // regular moons orbit in the planet's equator plane
        else { c = Orbits.normal(orb.planet[home]); a = perp(c); b = cross(c, a); }                       // the rest near the planet's orbit plane
        return new double[]{q[0] * a[0] + q[1] * b[0] + q[2] * c[0], q[0] * a[1] + q[1] * b[1] + q[2] * c[1], q[0] * a[2] + q[1] * b[2] + q[2] * c[2]};
    }
    /** The Lambert sphere's phase integral at phase angle alpha (1 full, 0 new). */
    public static double phaseIntegral(double alpha) { return (Math.sin(alpha) + (Math.PI - alpha) * Math.cos(alpha)) / Math.PI; }
    static double sep(double[] a, double[] b) { return Math.acos(Math.max(-1, Math.min(1, dot(a, b)))); }
    /** The share of a disk of angular radius r1 hidden by a disk r2 whose centre is d away (small angles: plane circles). */
    public static double overlap(double r1, double r2, double d) {
        if (d >= r1 + r2) return 0; if (d <= Math.abs(r2 - r1)) return r2 >= r1 ? 1 : (r2 * r2) / (r1 * r1);
        double a = r1 * r1 * Math.acos((d * d + r1 * r1 - r2 * r2) / (2 * d * r1)) + r2 * r2 * Math.acos((d * d + r2 * r2 - r1 * r1) / (2 * d * r2)) - 0.5 * Math.sqrt(Math.max(0, (-d + r1 + r2) * (d + r1 - r2) * (d - r1 + r2) * (d + r1 + r2)));
        return a / (Math.PI * r1 * r1); }
    static double[] perp(double[] n) { double[] a = Math.abs(n[2]) < 0.9 ? new double[]{0, 0, 1} : new double[]{1, 0, 0}; double[] c = cross(a, n); return mul(c, 1 / len(c)); }
    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double len(double[] a) { return Math.sqrt(dot(a, a)); }
    static double[] mul(double[] a, double k) { return new double[]{a[0] * k, a[1] * k, a[2] * k}; }
    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    static double[] add(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
}
