import com.hopeless.infinity.galaxy.*;
import com.hopeless.infinity.planet.PlanetWorld;

/** OrbitTest: the 0.8.0 orbits and spins (galaxy/Orbits) against the Solar System and their published distributions. Saved tool. */
public class OrbitTest {
    static int pass, fail;
    static void check(String w, boolean ok, String got) { if (ok) pass++; else fail++; System.out.printf("%s %-60s %s%n", ok ? "PASS" : "FAIL", w, got); }
    public static void main(String[] a) { com.hopeless.infinity.planet.Roads.on = false; com.hopeless.infinity.planet.Towns.on = false; com.hopeless.infinity.planet.Metro.on = false;   /* 0.27.0 megacities are MetroTest's */   /* 0.23.0: roads are RoadsTest's; this test reads the terrain */
        double msun = Orbits.M_SUN_KG, me = Orbits.M_EARTH_KG;
        // the Earth: Kepler's third law and the solar day
        Orbits.Elements earth = new Orbits.Elements(Orbits.AU_KM, 0.0167, 0, 0, 0, 0, 2 * Math.PI * Math.sqrt(Math.pow(Orbits.AU_KM, 3) / (Orbits.G * (msun + me))));
        check("Kepler: the Earth's year", Math.abs(earth.periodS() / 86400 - 365.256) < 0.05, String.format("%.3f d (365.256)", earth.periodS() / 86400));
        Orbits.Spin es = new Orbits.Spin(86164.1, Math.toRadians(23.44), 0, 0, false, 0);
        check("solar day from the sidereal spin", Math.abs(Orbits.solarDay(earth, es) / 3600 - 24.0) < 0.01, String.format("%.4f h (24.0)", Orbits.solarDay(earth, es) / 3600));
        double[] p0 = Orbits.position(earth, 0), p1 = Orbits.position(earth, earth.periodS() / 2); double r0 = Math.hypot(p0[0], p0[1]), r1 = Math.hypot(p1[0], p1[1]);
        check("ellipse: perihelion and aphelion", Math.abs(r0 / Orbits.AU_KM - 0.9833) < 1e-3 && Math.abs(r1 / Orbits.AU_KM - 1.0167) < 1e-3, String.format("%.4f AU and %.4f AU", r0 / Orbits.AU_KM, r1 / Orbits.AU_KM));
        // tidal locking (Gladman et al. 1996): the Moon locks to the Earth well inside 4.5 Gyr, the Earth does not lock to the Sun
        double moonLock = Orbits.lockYr(2 * Math.PI / (12 * 3600), 384400, 7.342e22, 1737.4, me, true), earthLock = Orbits.lockYr(2 * Math.PI / 86164, Orbits.AU_KM, me, 6371, msun, true);
        check("locking: the Moon to the Earth, not the Earth to the Sun", moonLock < 1e9 && earthLock > 4.5e9, String.format("Moon %.2g yr, Earth %.2g yr", moonLock, earthLock));
        // distributions over many systems (spawn systems of seeds 1-400 and their planets)
        double es1 = 0, eg = 0, inc = 0, cosO = 0; int ns = 0, ng = 0, no = 0, lockedIn = 0, nin = 0, lockedOut = 0, nout = 0;
        PlanetWorld w1 = null;
        for (long seed = 1; seed <= 400; seed++) { StarSystem s = new Galaxy(seed).spawn(); if (s == null) continue; Orbits o = new Orbits(s);
            for (int i = 0; i < s.planets.size(); i++) { Planet p = s.planets.get(i); Orbits.Elements e = o.planet[i]; Orbits.Spin sp = o.planetSpin[i];
                if (p.giant()) { eg += e.e(); ng++; } else { es1 += e.e(); ns++; } inc += Math.toDegrees(e.incl());
                if (!sp.locked()) { cosO += Math.cos(sp.obliquity()); no++; }
                if (p.au < 0.1) { nin++; if (sp.locked()) lockedIn++; } if (p.au > 1 && p.kind == Planet.Kind.ROCKY) { nout++; if (sp.locked()) lockedOut++; } } }
        es1 /= ns; eg /= ng; inc /= (ns + ng); cosO /= no;
        check("small planets: mean e = 0.049 sqrt(pi/2) = 0.061 (Van Eylen 2019)", Math.abs(es1 - 0.0614) < 0.006, String.format("%.4f over %d", es1, ns));
        check("giants: mean e = 0.867/3.897 = 0.222 (Kipping 2013)", Math.abs(eg - 0.2225) < 0.03, String.format("%.4f over %d", eg, ng));
        check("inclination: mean 1.4 deg x 1.2533 = 1.75 deg (Fang & Margot)", Math.abs(inc - 1.755) < 0.12, String.format("%.3f deg", inc));
        check("obliquity isotropic: mean cos near 0 (Kokubo & Genda)", Math.abs(cosO) < 0.05, String.format("%.3f over %d", cosO, no));
        check("close-in planets (under 0.1 AU) mostly locked, rocky beyond 1 AU rarely", lockedIn > 0.5 * nin && lockedOut < 0.1 * Math.max(1, nout), String.format("%d of %d inside, %d of %d rocky beyond 1 AU", lockedIn, nin, lockedOut, nout));
        // the seed 1 home system
        PlanetWorld w = new PlanetWorld(1); Orbits o = new Orbits(w.system); System.out.println(w.system.name() + ", age " + String.format("%.2g", o.ageYr) + " yr");
        for (int i = 0; i < w.system.planets.size(); i++) System.out.println((i == w.planet.index ? " HOME " : "      ") + o.describe(i));
        int h = w.planet.index; double day = Orbits.solarDay(o.planet[h], o.planetSpin[h]);
        check("seed 1's home planet has a day and night", Double.isFinite(day) && day > 3600, String.format("solar day %.2f h", day / 3600));
        System.out.printf("OrbitTest: %d pass, %d fail%n", pass, fail); if (fail > 0) System.exit(1);
    }
}
