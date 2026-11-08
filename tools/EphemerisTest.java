import com.hopeless.infinity.galaxy.*;
import com.hopeless.infinity.planet.PlanetWorld;
import com.hopeless.infinity.sky.Ephemeris;

/** EphemerisTest: the 0.8.0 real sky (sky/Ephemeris) on seed 1: frame handedness, sunrise side, day length, noon altitude, moon phases,
 *  eclipse geometry. Saved tool. */
public class EphemerisTest {
    static int pass, fail;
    static void check(String w, boolean ok, String got) { if (ok) pass++; else fail++; System.out.printf("%s %-62s %s%n", ok ? "PASS" : "FAIL", w, got); }
    public static void main(String[] a) { com.hopeless.infinity.planet.Roads.on = false; com.hopeless.infinity.planet.Towns.on = false; com.hopeless.infinity.planet.Metro.on = false;   /* 0.27.0 megacities are MetroTest's */   /* 0.23.0: roads are RoadsTest's; this test reads the terrain */
        PlanetWorld w = new PlanetWorld(1); Ephemeris e = new Ephemeris(w); int F = w.cube.F;
        Orbits.Spin sp = e.orb.planetSpin[e.home]; boolean pro = Math.cos(sp.obliquity()) >= 0;
        System.out.printf("home planet %d: solar day %.3f h, sidereal %.3f h, tilt %.1f deg (%s), year %.1f d, moons %d%n", e.home, e.solarDay() / 3600, sp.periodS() / 3600, Math.toDegrees(sp.obliquity()), pro ? "prograde" : "retrograde", e.orb.planet[e.home].periodS() / 86400, w.planet.moons.size());
        // 1. handedness: a step east in the block plane is a step east on the sphere (the frame is not mirrored), on all six faces
        double worst = 1; for (int f = 0; f < 6; f++) for (double[] q : new double[][]{{F * 0.5, F * 0.5}, {F * 0.2, F * 0.7}, {F * 0.8, F * 0.3}}) {
            Ephemeris.At at = e.at(f, q[0], q[1], 0); double nx = at.northMc[0], nz = at.northMc[1]; double[] p0 = w.cube.spherePoint(f, q[0], q[1]), p1 = w.cube.spherePoint(f, q[0] - nz * 100, q[1] + nx * 100), pn = w.cube.spherePoint(f, q[0] + nx * 100, q[1] + nz * 100);
            if (p0 == null || p1 == null || pn == null) continue;
            // body frame east at p0: pole x p0 normalised; the step should go along it
            double[] pole = {0, 0, 1}, east = {pole[1] * p0[2] - pole[2] * p0[1], pole[2] * p0[0] - pole[0] * p0[2], pole[0] * p0[1] - pole[1] * p0[0]}; double el = Math.sqrt(east[0] * east[0] + east[1] * east[1] + east[2] * east[2]);
            double[] st = {p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]}; double sl = Math.sqrt(st[0] * st[0] + st[1] * st[1] + st[2] * st[2]); double c = (st[0] * east[0] + st[1] * east[1] + st[2] * east[2]) / (sl * el); worst = Math.min(worst, c); }
        check("frame: the block plane's east is east on the planet (all faces)", worst > 0.95, String.format("worst cos %.3f", worst));
        // 2. day length: successive local noons (sun highest) at the spawn
        int[] sp0 = w.spawn(); double day = e.solarDay(), t0 = 1000; double[] noons = new double[3]; int nn = 0; double prev = -9, prev2 = -9;
        for (double t = t0; t < t0 + 3.2 * day && nn < 3; t += 60) { double y = e.at(0, sp0[0], sp0[2], t).sun[1]; if (prev > prev2 && prev > y && prev2 > -9) noons[nn++] = t - 60; prev2 = prev; prev = y; }
        double meas = (noons[2] - noons[0]) / 2;
        check("day length: measured noon to noon = the solar day", nn == 3 && Math.abs(meas - day) < 180, String.format("measured %.3f h, solar day %.3f h", meas / 3600, day / 3600));
        // 3. sunrise side: east for a prograde spin, west for a retrograde one
        double prevY = 0; int sideOk = 0, rises = 0; for (double t = t0; t < t0 + 3 * day; t += 120) { Ephemeris.At at = e.at(0, sp0[0], sp0[2], t); double y = at.sun[1];
            if (prevY < 0 && y >= 0) { rises++; double nx = at.northMc[0], nz = at.northMc[1], eastC = at.sun[0] * -nz + at.sun[2] * nx; if (eastC > 0) sideOk++; } prevY = y; }
        check("the star rises in the east (north = the spin pole, right hand)", rises >= 2 && sideOk == rises, String.format("%d of %d sunrises", sideOk, rises));
        // 4. noon altitude: 90 - |latitude - declination|
        Ephemeris.At an = e.at(0, sp0[0], sp0[2], noons[0]); double lat = e.frame(0, sp0[0], sp0[2])[0]; double[] pole = new double[3];
        double decl = Math.asin(an.sun[1] * Math.sin(lat) + 0) ; // not used directly
        double maxAlt = Math.toDegrees(an.sunAlt); double[] ps = poleS(e); double sunDec = Math.toDegrees(Math.asin(dotS(e, an, ps)));
        double expect = 90 - Math.abs(Math.toDegrees(lat) - sunDec);
        check("noon altitude = 90 - |latitude - declination|", Math.abs(maxAlt - expect) < 0.5, String.format("%.2f deg (latitude %.2f, star declination %.2f: %.2f)", maxAlt, Math.toDegrees(lat), sunDec, expect));
        // 5. the moon's phases: new to new = the synodic month 1 / (1/P_moon - 1/P_year)
        if (w.planet.moons.size() > 0) { Orbits.Elements mo = e.orb.moon[e.home][0]; double syn = 1 / (1 / mo.periodS() - 1 / e.orb.planet[e.home].periodS()); double step = syn / 2000, pv = 9, pv2 = 9; double[] news = new double[3]; int k = 0;
            for (double t = 0; t < 3.2 * syn && k < 3; t += step) { Ephemeris.At at = e.at(0, sp0[0], sp0[2], t); Ephemeris.Body b = at.bodies.get(0); double cs = b.dir()[0] * at.sun[0] + b.dir()[1] * at.sun[1] + b.dir()[2] * at.sun[2]; double ph = -cs;
                if (pv < pv2 && pv < ph && pv2 < 9 && pv < -0.9 && (k == 0 || t - news[k - 1] > syn / 2)) news[k++] = t - step; pv2 = pv; pv = ph; }
            double m = (news[2] - news[0]) / 2; check("moon: new moon to new moon = the synodic month", k == 3 && Math.abs(m - syn) / syn < 0.03, String.format("measured %.2f d, synodic %.2f d (orbit %.2f d)", m / 86400, syn / 86400, mo.periodS() / 86400)); }
        // 6. eclipse geometry: equal disks centred hide all, touching hide nothing, half-offset by the formula
        check("eclipse overlap: centred 1, touching 0, inside a bigger disk 1", Math.abs(Ephemeris.overlap(1, 1, 0) - 1) < 1e-9 && Ephemeris.overlap(1, 1, 2) == 0 && Ephemeris.overlap(1, 2, 0.5) == 1, String.format("%.3f / %.3f / %.3f; equal disks 1 radius apart %.4f (0.3910)", Ephemeris.overlap(1, 1, 0), Ephemeris.overlap(1, 1, 2), Ephemeris.overlap(1, 2, 0.5), Ephemeris.overlap(1, 1, 1)));
        System.out.printf("EphemerisTest: %d pass, %d fail%n", pass, fail); if (fail > 0) System.exit(1);
    }
    static double[] poleS(Ephemeris e) { return Orbits.pole(e.orb.planet[e.home], e.orb.planetSpin[e.home]); }
    /** sin(declination) of the star: the star's S direction against the pole, from the local direction back to S via the frame. */
    static double dotS(Ephemeris e, Ephemeris.At at, double[] pole) { double[] s = {-at.obs[0], -at.obs[1], -at.obs[2]}; double l = Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]); return (s[0] * pole[0] + s[1] * pole[1] + s[2] * pole[2]) / l; }
}
