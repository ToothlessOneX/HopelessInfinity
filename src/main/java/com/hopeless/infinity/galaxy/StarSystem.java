package com.hopeless.infinity.galaxy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.hopeless.infinity.galaxy.Research.*;

/**
 * One solar system, made from its key only when somebody asks for it. Planets form round main-sequence primaries: placed outward from a
 * short-period orbit, each next one a mutual-Hill-radius spacing further (Weiss et al. 2018), masses similar to the neighbour's and larger
 * beyond the snow line (Hayashi 1981). In a binary, planets stay inside 0.3 of the separation (Holman & Wiegert 1999). Hot Jupiters
 * (Wright et al. 2012), rings on every giant (bright Saturn-type on a quarter), the main belt between the 4:1 and 2:1 resonances of the
 * innermost giant, a Kuiper belt from 3:2 to 2:1 of the outermost giant where a debris disc is drawn (Sibthorpe et al. 2018).
 */
public final class StarSystem {
    public final Galaxy.Ref ref; public final Star star; public final Body primary; public final List<Planet> planets; public final List<Belt> belts;
    public record Belt(String kind, double innerAu, double outerAu, double massE, double largestKm) {
        /** The n largest bodies' diameters (km): cumulative N(>D) = (D / largest)^-2.5 (Dohnanyi 1969), so the k-th largest is largest * k^(-1/2.5). */
        public double[] largest(int n) { double[] d = new double[n]; for (int k = 1; k <= n; k++) d[k - 1] = largestKm * Math.pow(k, -1 / DOHNANYI); return d; }
        /** Bodies larger than D km. */
        public double countAbove(double dKm) { return Math.pow(dKm / largestKm, -DOHNANYI); }
    }
    public record Comet(double nucleusKm, double perihelionAu, double aphelionAu, boolean longPeriod) {}
    public final List<Comet> comets; public final double oortComets;
    /** GAME tunes, checked by tools/GalaxyTest against the research values they aim at. GIANT_P is tuned to Cumming et al. 2008's 10.5 %. */
    static final double INNER_AU_MIN = 0.03, INNER_AU_MAX = 0.3, OUTER_AU = 40, STOP = 0.13, PEA = 0.55, OUTWARD = 0.10, GIANT_P = 0.35, GIANT_INJECT = 0.093, ACTIVE_COMETS = 3, IRREGULAR_MOONS = 25;

    StarSystem(Galaxy.Ref ref, Rng r) {
        this.ref = ref; primary = ref.primary; star = primary.ms;
        List<Planet> ps = new ArrayList<>(); List<Belt> bs = new ArrayList<>(); List<Comet> cs = new ArrayList<>(); double oort = 0;
        if (star != null && primary.kind == Body.Kind.MAIN_SEQUENCE) {
            double msun = star.mass, outer = OUTER_AU * Math.sqrt(Math.max(0.1, star.lum));
            if (ref.companion != null) outer = Math.min(outer, S_TYPE_LIMIT * ref.sepAu);
            boolean fgk = star.cls >= 3 && star.cls <= 5, hot = fgk && r.u() < HOT_JUPITER;
            double a = Math.exp(r.range(Math.log(INNER_AU_MIN), Math.log(INNER_AU_MAX))) * Math.cbrt(msun), prevLog = Double.NaN, prevM = 0; int idx = 0;
            if (hot && outer > 0.1) {   // a hot Jupiter: 0.5-3 Jupiter masses at 0.02-0.1 AU, then a wide gap (they are usually alone inside 1 AU)
                double m = 317.83 * Math.exp(r.range(Math.log(0.5), Math.log(3))), ah = r.range(0.02, 0.1); ps.add(new Planet(idx++, star, ah, m)); prevLog = Math.log10(m); prevM = m; a = Math.max(1.0, ah * 10); }
            for (int i = 0; i < 16 && outer > a; i++) {
                double draw = a < star.snowLine() ? 0.15 + 0.5 * r.gauss() : (r.u() < GIANT_P * Math.min(1.5, Math.pow(msun, GIANT_MSTAR_EXP)) ? 2.4 + 0.4 * r.gauss() : 1.1 + 0.5 * r.gauss());
                double lg = Double.isNaN(prevLog) ? draw : PEA * prevLog + (1 - PEA) * draw + OUTWARD;
                lg = Math.max(-1.3, Math.min(Math.min(3.6, Math.log10(DISK_MASS_FRACTION * msun * 332946)), lg));   // 0.05 Earth masses (below Mercury's 0.055) to 13 Jupiter masses (the deuterium limit)
                double m = Math.pow(10, lg);
                if (!ps.isEmpty()) { double k = Math.max(HILL_MIN, HILL_MODE * Math.exp(0.35 * r.gauss())), x = Math.cbrt((prevM + m) / 332946.0 / (3 * msun)), h = k * x / 2;
                    if (h >= 0.9) break; a = a * (1 + h) / (1 - h); }
                if (a > outer) break;
                ps.add(new Planet(idx++, star, a, m)); prevLog = lg; prevM = m;
                if (r.u() < STOP) break; }
            // a giant of Cumming's box (0.3-10 MJ, 2-2000 d) for FGK stars: dN ~ M^-0.31 dlnM, P^0.26 dlnP; neighbours inside 0.25 of its orbit give way
            if (fgk && !hot && r.u() < GIANT_INJECT) { double lnP = draw(r, Math.log(10), Math.log(2000), CUMMING_P_BETA), m = 317.83 * Math.exp(draw(r, Math.log(0.3), Math.log(10), CUMMING_M_ALPHA));
                double ag = Math.cbrt(Math.pow(Math.exp(lnP) / 365.25, 2) * msun); if (ag < outer) { ps.removeIf(p -> Math.abs(p.au - ag) < 0.25 * ag); ps.add(new Planet(0, star, ag, m)); ps.sort((x, y) -> Double.compare(x.au, y.au)); renumber(ps); } }
            // rings: every giant; a quarter bright and Saturn-sized (scaled from Saturn's ring edges to the planet's radius), the rest faint
            for (Planet p : ps) if (p.giant() && p.au >= star.snowLine() && r.u() < RING_ANY) { /* icy rings only where ice lasts (all four Solar giants are beyond the snow line) */ boolean bright = r.u() < RING_BRIGHT; p.rings = bright ? Planet.Rings.BRIGHT : Planet.Rings.FAINT;
                p.ringInKm = p.radiusKm * (bright ? SAT_RING_IN : 1.4 + 0.3 * r.u()); p.ringOutKm = p.radiusKm * (bright ? SAT_RING_OUT : 1.8 + 0.6 * r.u()); }
            // belts: from the giants' resonances (a_res = a_giant * (period ratio)^(2/3))
            Planet first = null, last = null; for (Planet p : ps) if (p.giant()) { if (first == null) first = p; last = p; }
            if (first != null) { double in = first.au * Math.pow(1 / MAIN_BELT_RES[0], 2.0 / 3), out = first.au * Math.pow(1 / MAIN_BELT_RES[1], 2.0 / 3); boolean clear = true;
                for (Planet p : ps) if (p.au > in && p.au < out) clear = false;
                if (clear && in > 0.05) { double mb = MAIN_BELT_ME * Math.exp(1.0 * r.gauss()); bs.add(new Belt("main asteroid belt", in, out, mb, CERES_D_KM * Math.cbrt(mb / MAIN_BELT_ME))); } }
            if (r.u() < DEBRIS_DISC && !ps.isEmpty()) { double o = (last != null ? last : ps.get(ps.size() - 1)).au, mk = KUIPER_ME * Math.exp(1.0 * r.gauss());
                bs.add(new Belt(last != null ? "Kuiper belt" : "debris belt", o / Math.pow(KUIPER_RES[0], 2.0 / 3), o / Math.pow(KUIPER_RES[1], 2.0 / 3), mk, CERES_D_KM * 2.5 * Math.cbrt(mk / KUIPER_ME))); }   // Pluto/Eris ~2,400 km in 0.0197 Me
            // moons: regular systems of ~1e-4 of a giant's mass; a 1-in-12 giant-impact moon round rocky planets over 0.5 Me; small captured ones
            for (Planet p : ps) moons(p, r);
            // comets: an Oort cloud scaled from the Sun's (Boe 2019) by stellar mass; a few active comets inside 5 AU now (GAME), nuclei from Boe's size slope
            oort = OORT_COMETS * msun; int active = r.poisson(ACTIVE_COMETS);
            for (int i = 0; i < active; i++) { double d = 1 * Math.pow(1 - r.u() * 0.999, -1 / COMET_ALPHA) ; boolean lp = r.u() < 0.5; double q = r.range(0.2, 4.5);
                cs.add(new Comet(Math.min(60, d), q, lp ? r.range(1000, 50000) : r.range(4, 7) * Math.sqrt(Math.max(0.1, star.lum)), lp)); }
        }
        planets = Collections.unmodifiableList(ps); belts = Collections.unmodifiableList(bs); comets = Collections.unmodifiableList(cs); oortComets = oort;
    }
    static void renumber(List<Planet> ps) { for (int i = 0; i < ps.size(); i++) ps.get(i).index = i; }
    /** ln-value in [a, b] with density ~ e^(beta x) (a power law in the linear quantity). */
    static double draw(Rng r, double a, double b, double beta) { if (Math.abs(beta) < 1e-9) return r.range(a, b); double ea = Math.exp(beta * a), eb = Math.exp(beta * b); return Math.log(ea + r.u() * (eb - ea)) / beta; }
    void moons(Planet p, Rng r) {
        if (p.giant()) {
            int n = 1 + r.poisson(3); double total = MOON_MASS_RATIO * p.massE, orbit = p.radiusKm * r.range(4, 7); double[] w = new double[n]; double ws = 0; for (int i = 0; i < n; i++) { w[i] = -Math.log(Math.max(1e-9, r.u())); ws += w[i]; }
            for (int i = 0; i < n; i++) { double m = total * w[i] / ws; p.moons.add(new Planet.Moon(Planet.MoonKind.REGULAR, m, Planet.radiusKm(m, 1.9), orbit)); orbit *= r.range(1.5, 2.0); }   // icy, Ganymede's 1.9 g/cm3; Galilean spacing ~1.6-2
            int irr = r.poisson(IRREGULAR_MOONS * Math.min(3, hill(p) / (0.355 * AU_KM))); /* irregulars live in the Hill sphere: scaled to Jupiter's (0.355 AU) */ for (int i = 0; i < irr; i++) { double dkm = 2 * Math.pow(1 - r.u() * 0.99, -1 / 2.0); double m = 4.0 / 3 * Math.PI * Math.pow(dkm * 5e4, 3) * 1.5 / 5.9722e27;
                p.moons.add(new Planet.Moon(Planet.MoonKind.IRREGULAR, m, dkm / 2, hill(p) * r.range(0.1, 0.5))); }
        } else {
            if (p.kind == Planet.Kind.ROCKY && p.massE > 0.5 && r.u() < BIG_MOON) { double m = LUNAR_MASS_E * r.range(0.5, 2.0); p.moons.add(new Planet.Moon(Planet.MoonKind.GIANT_IMPACT, m, Planet.radiusKm(m, 3.34), p.radiusKm * r.range(30, 65))); }   // Moon: 3.34 g/cm3 at 60 Earth radii
            int c = r.poisson(0.5); for (int i = 0; i < c; i++) { double dkm = r.range(8, 30); double m = 4.0 / 3 * Math.PI * Math.pow(dkm * 5e4, 3) * 1.9 / 5.9722e27; p.moons.add(new Planet.Moon(Planet.MoonKind.CAPTURED, m, dkm / 2, p.radiusKm * r.range(2.5, 7))); }   // Phobos/Deimos-like
        }
    }
    /** Hill radius of a planet, km. */
    double hill(Planet p) { return p.au * AU_KM * Math.cbrt(p.massE / 332946.0 / (3 * star.mass)); }
    public Planet firstEarthLike() { for (Planet p : planets) if (p.earthLike()) return p; return null; }
    public boolean hasGiantWithin(double au) { for (Planet p : planets) if (p.kind == Planet.Kind.GAS_GIANT && p.au <= au) return true; return false; }
    public String name() { return ref.name(); }
    @Override public String toString() {
        StringBuilder b = new StringBuilder(String.format("%s  %s  at (%.0f, %.0f, %.0f) pc, %.1f ly from the Sun's place%n   primary %s%n", name(), primary.type, ref.x, ref.y, ref.z, ref.lyFromSun(), primary));
        if (ref.companion != null) b.append(String.format("   companion %s at %.2f AU%n", ref.companion, ref.sepAu));
        if (star != null) b.append(String.format("   HZ %.3f-%.3f AU, snow line %.2f AU%n", star.hz(MOIST), star.hz(MAX_GREENHOUSE), star.snowLine()));
        for (Planet p : planets) b.append("   ").append(p).append('\n');
        for (Belt t : belts) b.append(String.format("   %s %.2f-%.2f AU, %.2e Earth masses, largest body %.0f km, %.3g bodies over 1 km%n", t.kind, t.innerAu, t.outerAu, t.massE, t.largestKm, t.countAbove(1)));
        b.append(String.format("   Oort cloud %.2g comets; active comets now %d%n", oortComets, comets.size()));
        return b.toString(); }
}
