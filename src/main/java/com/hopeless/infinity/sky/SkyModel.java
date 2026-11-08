package com.hopeless.infinity.sky;

import com.hopeless.infinity.galaxy.*;
import com.hopeless.infinity.look.Look;
import com.hopeless.infinity.planet.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.6.0 THE SKY of the start planet, from the world's own galaxy, star, planets and moons. Pure Java (no game classes): the game's sky shader
 * (assets/hopelessinfinity/shaders/sky.glsl, world/client SkyRenderer), the offline preview and the tests use the same numbers.
 *
 *  units      every brightness is relative to the host star's irradiance at the planet (E = 1).
 *  sun        the host star: angular radius R* / a, blackbody colour of Teff (Look.blackbody), limb darkening u = 0.6 (Look.LIMB_U).
 *  air        single scattering, Rayleigh + Mie + ozone absorption, Earth's coefficients (Bruneton 2017, "A Qualitative and Quantitative
 *             Evaluation of 8 Clear Sky Models", reference parameters): beta_R (5.802, 13.558, 33.1) e-6 /m, H_R 8 km; Mie scattering 3.996e-6,
 *             extinction 4.40e-6 /m, H_M 1.2 km, g 0.8; ozone absorption (0.650, 1.881, 0.085) e-6 /m in a 30 km tent at 25 km. A planet's
 *             scale heights go as 1/g (H = kT/(m g)), the same column of air at the ground (CHOSEN: no pressure data per planet yet).
 *  sky motion the game's own day (level.getTimeOfDay: sunrise, noon and sunset stay where Minecraft's light expects them) on a sky turned
 *             about the planet's real pole at the player's latitude (the cube sphere: latitude and north at the player). No axial tilt yet
 *             (seasons are an open item): sun, moons and planets run along the celestial equator.
 *  moons      the planet's own moons: angular radius r / orbit, geometric albedo 0.12 (the Moon), 5.145 deg inclination (the Moon's).
 *             The biggest moon keeps Minecraft's 8-day phase cycle (full at day 0) so it agrees with Hopeless Tides' tides; the others
 *             go round in (a / a_big)^1.5 of it (Kepler, same planet).
 *  planets    the system's other planets on circular orbits (period a^1.5 / M^0.5 years, one game day = one planet day of 24 h, CHOSEN),
 *             points of flux p (R / d)^2 Phi(alpha) (a_home / a)^2 with a Lambert phase function; geometric albedos: rocky 0.3, gas giant
 *             0.538 (Jupiter), ice giant and mini-Neptune 0.488 (Uranus).
 *  stars      every star of the galaxy within STAR_PC of home (Galaxy.cell), plus the young massive stars within YOUNG_PC (youngColumn),
 *             flux (L / L*) (a / d)^2 corrected to the V band with Torres (2010)'s bolometric corrections; dimmed by dust, A_V = 1.8 mag/kpc
 *             in the plane (Whittet 2003) falling off with height over 134 pc (Drimmel & Spergel 2001).
 *  milky way  the light of the rest of the galaxy along each direction: the galaxy's own density (Galaxy.density) times the mean V-band
 *             luminosity per object MEASURED from the stars round home, from STAR_PC out to 30 kpc, through the same dust.
 *  orientation the planet's orbit lies at a random angle to the galactic plane (fixed per system).
 */
public final class SkyModel {
    public static final double STAR_PC = 150, YOUNG_PC = 1500, AV_PER_KPC = 1.8, DUST_H_PC = 134, PC_AU = 206264.806;
    public static final double[] BETA_R = {5.802e-6, 13.558e-6, 33.1e-6}, OZONE = {0.650e-6, 1.881e-6, 0.085e-6};
    public static final double H_R = 8000, H_M = 1200, MIE_S = 3.996e-6, MIE_E = 4.40e-6, MIE_G = 0.8, OZ_CENTER = 25000, OZ_HALF = 15000, ATM_TOP = 60000;
    public static final double MOON_ALBEDO = 0.12, MOON_INCL = Math.toRadians(5.145), LUNAR_DAYS = 8;

    public final PlanetWorld w; public final double teff, angR, insolation, rPlanet, hScale, yearDays; public final double[] sunRgb;
    public record Moon(double angR, double periodDays, double phase0, double incl, String kind) {}
    public record Other(double au, double radiusKm, double albedo, double yearDays, double phase0, String kind) {}
    public final List<Moon> moons = new ArrayList<>(); public final List<Other> others = new ArrayList<>();
    /** galaxy -> celestial (rows: celestial x, y, z in galactic coordinates); celestial z = the planet's pole (orbit normal). */
    public final double[][] gal2cel;

    public SkyModel(PlanetWorld w) {
        this.w = w; StarSystem sys = w.system; Planet home = w.planet; Body star = sys.primary;
        teff = star.teff; angR = star.radiusKm / (home.au * Research.AU_KM); insolation = home.insolation; rPlanet = home.radiusKm * 1000; hScale = 9.81 / home.gravity;
        sunRgb = Look.blackbody(teff); double msun = Math.max(0.05, star.mass); yearDays = Math.pow(home.au, 1.5) / Math.sqrt(msun) * 365.25;
        Rng r = new Rng(Rng.key(w.seed, sys.ref.cx, sys.ref.cy, sys.ref.cz * 31 + sys.ref.k + 0x5C7));
        // moons: the biggest one leads (8-day cycle)
        Planet.Moon big = null; for (Planet.Moon m : home.moons) if (big == null || m.radiusKm() > big.radiusKm()) big = m;
        for (Planet.Moon m : home.moons) { double per = m == big ? LUNAR_DAYS : LUNAR_DAYS * Math.pow(m.orbitKm() / big.orbitKm(), 1.5);
            moons.add(new Moon(m.radiusKm() / m.orbitKm(), per, m == big ? Math.PI : r.u() * 2 * Math.PI, MOON_INCL, m.kind().toString())); }
        for (Planet p : sys.planets) { if (p == home) continue; double alb = p.kind == Planet.Kind.ROCKY ? 0.3 : p.kind == Planet.Kind.GAS_GIANT ? 0.538 : 0.488;
            others.add(new Other(p.au, p.radiusKm, alb, Math.pow(p.au, 1.5) / Math.sqrt(msun) * 365.25, r.u() * 2 * Math.PI, p.kind.toString())); }
        // a random orientation of the orbit to the galaxy
        double a1 = r.u() * 2 * Math.PI, ct = 2 * r.u() - 1, st = Math.sqrt(1 - ct * ct), a2 = r.u() * 2 * Math.PI; double[] zc = {st * Math.cos(a1), st * Math.sin(a1), ct};
        double[] t = Math.abs(zc[2]) < 0.9 ? new double[]{0, 0, 1} : new double[]{1, 0, 0}; double[] xc = norm(cross(t, zc)), yc = cross(zc, xc);
        double[] x2 = add(mul(xc, Math.cos(a2)), mul(yc, Math.sin(a2))), y2 = cross(zc, x2); gal2cel = new double[][]{x2, y2, zc};
        homeAngle0 = r.u() * 2 * Math.PI;
    }
    final double homeAngle0;

    // ------------------------------------------------------------------ the sky frame at the player
    /** The local frame at face f, block (x, z): {latitude rad, north x, north z} (north: where latitude grows, in the block plane). */
    public double[] frame(int f, double x, double z) {
        double[] s = w.cube.spherePoint(f, x, z), sx = w.cube.spherePoint(f, x + 16, z), sz = w.cube.spherePoint(f, x, z + 16);
        if (s == null || sx == null || sz == null) return new double[]{0, 0, -1};
        double la = Math.asin(Math.max(-1, Math.min(1, s[2]))), gx = Math.asin(Math.max(-1, Math.min(1, sx[2]))) - la, gz = Math.asin(Math.max(-1, Math.min(1, sz[2]))) - la, gl = Math.hypot(gx, gz);
        return gl > 1e-12 ? new double[]{la, gx / gl, gz / gl} : new double[]{la, 0, -1}; }
    /** The sky at a moment. days: game days since day 0 (dayTime / 24000, fraction included); tod: level.getTimeOfDay (0 = noon). */
    public final class At {
        public final double[] pole = new double[3], noon = new double[3], west = new double[3], sun = new double[3]; public double lst, lamSun;
        public final double[][] moonDir; public final double[] moonPhaseAngle; public final double[][] otherDir; public final double[] otherFlux;
        At(double lat, double nx, double nz, double days, double tod) {
            double[] up = {0, 1, 0}, north = {nx, 0, nz}; double cl = Math.cos(lat), sl = Math.sin(lat);
            for (int i = 0; i < 3; i++) { pole[i] = up[i] * sl + north[i] * cl; noon[i] = up[i] * cl - north[i] * sl; }
            double[] wv = cross(up, north); System.arraycopy(wv, 0, west, 0, 3);
            double H = 2 * Math.PI * tod; double th = homeAngle0 + 2 * Math.PI * days / yearDays; lamSun = th + Math.PI; lst = H + lamSun;
            put(sun, dir(lamSun, 0));
            moonDir = new double[moons.size()][]; moonPhaseAngle = new double[moons.size()];
            for (int i = 0; i < moons.size(); i++) { Moon m = moons.get(i); double e = m.phase0() + 2 * Math.PI * days / m.periodDays(); double ra = lamSun + e, dec = m.incl() * Math.sin(e + 0.7 * i); moonDir[i] = dir(ra, dec); moonPhaseAngle[i] = e; }
            otherDir = new double[others.size()][]; otherFlux = new double[others.size()]; double hx = Math.cos(th), hy = Math.sin(th);
            for (int i = 0; i < others.size(); i++) { Other o = others.get(i); double ang = o.phase0() + 2 * Math.PI * days / o.yearDays(); double px = o.au() * Math.cos(ang) - homeAu() * hx, py = o.au() * Math.sin(ang) - homeAu() * hy, d = Math.hypot(px, py);
                double lam = Math.atan2(py, px); otherDir[i] = dir(lam, 0.026 * Math.sin(ang * 1.3 + i));   // inclination ~1.5 deg
                double cosA = (o.au() * o.au() + d * d - homeAu() * homeAu()) / (2 * o.au() * d), alpha = Math.acos(Math.max(-1, Math.min(1, cosA)));   // star-planet-observer angle
                double phi = (Math.sin(alpha) + (Math.PI - alpha) * Math.cos(alpha)) / Math.PI, rk = o.radiusKm() / (d * Research.AU_KM);
                otherFlux[i] = o.albedo() * rk * rk * phi * Math.pow(homeAu() / o.au(), 2); }
        }
        /** A celestial point (right ascension, declination) to the local frame (y up). */
        public double[] dir(double ra, double dec) { double h = lst - ra, cd = Math.cos(dec), sd = Math.sin(dec), ch = Math.cos(h), sh = Math.sin(h);
            return new double[]{pole[0] * sd + cd * (noon[0] * ch + west[0] * sh), pole[1] * sd + cd * (noon[1] * ch + west[1] * sh), pole[2] * sd + cd * (noon[2] * ch + west[2] * sh)}; }
    }
    public double homeAu() { return w.planet.au; }
    public At at(int f, double x, double z, double days, double tod) { double[] fr = frame(f, x, z); return new At(fr[0], fr[1], fr[2], days, tod); }

    // ------------------------------------------------------------------ the star field and the milky way (baked once, celestial equirect)
    public static final int TW = 2048, TH = 1024;
    public volatile int starsBaked, starsInTexture, nakedEye; public volatile double localLV; public volatile double brightestFlux, meanLV, bakeMs; public volatile String bakeNote = "not baked";
    /** RGB radiance (E/sr) per texel, rows from declination +90 to -90, columns right ascension 0..2 pi. */
    public float[] bake() {
        long t0 = System.nanoTime(); float[] tex = new float[TW * TH * 3]; Galaxy g = w.galaxy; Galaxy.Ref home = w.system.ref; double Lh = w.system.primary.lum, a = homeAu();
        double sumLV = 0, sumR = 0, sumG = 0, sumB = 0; long count = 0; int n = 0, inTex = 0, eye = 0; double best = 0;
        long c0x = (long) Math.floor(home.x / Galaxy.CELL), c0y = (long) Math.floor(home.y / Galaxy.CELL), c0z = (long) Math.floor(home.z / Galaxy.CELL); int R = (int) Math.ceil(STAR_PC / Galaxy.CELL);
        // per slab of cells (parallel): sums for the local light, and the stars bright enough to matter (mag 9: 2.5 mag under the eye's limit)
        double fluxMin = Math.pow(10, -0.4 * (9 - (-26.74 - 2.5 * Math.log10(insolation))));
        record Pt(double ra, double dec, double flux, double teff) {}
        record Acc(double lv, double r, double g, double b, long stars, long objs, List<Pt> pts) {}
        final long fx = c0x, fy = c0y, fz = c0z; final int RR = R;
        List<Acc> accs = java.util.stream.IntStream.rangeClosed(-R, R).parallel().mapToObj(dx -> { double lv = 0, sr = 0, sg = 0, sb = 0; long st = 0, ob = 0; List<Pt> pts = new ArrayList<>();
            for (int dy = -RR; dy <= RR; dy++) for (int dz = -RR; dz <= RR; dz++) { if ((dx * dx + dy * dy + dz * dz - 3) * Galaxy.CELL * Galaxy.CELL > STAR_PC * STAR_PC) continue;
                for (Galaxy.Ref f : g.cell(fx + dx, fy + dy, fz + dz)) { double ex = f.x - home.x, ey = f.y - home.y, ez = f.z - home.z, d = Math.sqrt(ex * ex + ey * ey + ez * ez); if (d < 1e-6 || d > STAR_PC) continue; ob++;
                    for (Body bd : new Body[]{f.primary, f.companion}) { if (bd == null || !(bd.lum > 0) || !(bd.teff > 0)) continue; double l = bd.lum * Math.pow(10, 0.4 * (bc(bd.teff) - bc(5772)));
                        double[] c = Look.blackbody(bd.teff); double y = lum(c); lv += l; sr += l * c[0] / y; sg += l * c[1] / y; sb += l * c[2] / y; st++;
                        double flux = l / Lh * Math.pow(a / (d * PC_AU), 2) * Math.pow(10, -0.4 * dust(home.z, f.z, d)); if (flux < fluxMin) continue;
                        double[] cel = toCel(ex / d, ey / d, ez / d); pts.add(new Pt(Math.atan2(cel[1], cel[0]), Math.asin(Math.max(-1, Math.min(1, cel[2]))), flux, bd.teff)); } } }
            return new Acc(lv, sr, sg, sb, st, ob, pts); }).toList();
        long objs = 0; List<Pt> all = new ArrayList<>();
        for (Acc x : accs) { sumLV += x.lv(); sumR += x.r(); sumG += x.g(); sumB += x.b(); count += x.stars(); objs += x.objs(); all.addAll(x.pts()); }
        int sxc = (int) Math.floor(home.x / Galaxy.COLUMN), syc = (int) Math.floor(home.y / Galaxy.COLUMN), RY = (int) Math.ceil(YOUNG_PC / Galaxy.COLUMN);
        for (int dx = -RY; dx <= RY; dx++) for (int dy = -RY; dy <= RY; dy++) for (Galaxy.Ref f : g.youngColumn(sxc + dx, syc + dy)) {
            double ex = f.x - home.x, ey = f.y - home.y, ez = f.z - home.z, d = Math.sqrt(ex * ex + ey * ey + ez * ez); if (d < 1e-6 || d > YOUNG_PC) continue;
            for (Body bd : new Body[]{f.primary, f.companion}) { if (bd == null || !(bd.lum > 0) || !(bd.teff > 0)) continue; double l = bd.lum * Math.pow(10, 0.4 * (bc(bd.teff) - bc(5772)));
                double flux = l / Lh * Math.pow(a / (d * PC_AU), 2) * Math.pow(10, -0.4 * dust(home.z, f.z, d)); if (flux < fluxMin) continue;
                double[] cel = toCel(ex / d, ey / d, ez / d); all.add(new Pt(Math.atan2(cel[1], cel[0]), Math.asin(Math.max(-1, Math.min(1, cel[2]))), flux, bd.teff)); } }
        for (Pt pt : all) { n++; if (splat(tex, pt.ra(), pt.dec(), pt.flux(), Look.blackbody(pt.teff()))) inTex++; best = Math.max(best, pt.flux()); if (mag(pt.flux()) <= 6.5) eye++; }
        double vol = 4.0 / 3 * Math.PI * Math.pow(STAR_PC, 3), lvPerObj = objs == 0 ? 0 : sumLV / objs; meanLV = lvPerObj;
        double[] mwc = count == 0 ? new double[]{1, 1, 1} : new double[]{sumR / sumLV, sumG / sumLV, sumB / sumLV};
        milkyWay(tex, home, lvPerObj, Lh, a, mwc);
        starsBaked = n; starsInTexture = inTex; brightestFlux = best; bakeMs = (System.nanoTime() - t0) / 1e6;
        nakedEye = eye; localLV = sumLV / vol; bakeNote = String.format("%d naked-eye (mag 6.5 or brighter; Earth about 9,000), local V light %.3f Lsun/pc^3 | %d stars drawn to mag 9 (%d within %.0f pc counted, young massive within %.0f pc), brightest %.2e E (mag %.1f), V-band light per object %.3f Lsun over %.0f pc^3, %.0f ms",
            eye, sumLV / vol, n, count, STAR_PC, YOUNG_PC, best, mag(best), lvPerObj, vol, bakeMs);
        return tex; }
    /** Apparent magnitude of a flux (E units), the host star's own magnitude from a 1 AU Earth-Sun -26.74 scaled by its insolation. */
    public double mag(double flux) { return -26.74 - 2.5 * Math.log10(insolation) - 2.5 * Math.log10(Math.max(1e-300, flux)); }
    double[] toCel(double x, double y, double z) { double[][] m = gal2cel; return new double[]{m[0][0] * x + m[0][1] * y + m[0][2] * z, m[1][0] * x + m[1][1] * y + m[1][2] * z, m[2][0] * x + m[2][1] * y + m[2][2] * z}; }
    /** Dust between home (height z0) and a point at height z1, d pc away: A_V (mag) = 1.8/kpc x d x the mean of exp(-|z|/134 pc). */
    static double dust(double z0, double z1, double d) { int n = 8; double s = 0; for (int i = 0; i < n; i++) { double z = z0 + (z1 - z0) * (i + 0.5) / n; s += Math.exp(-Math.abs(z) / DUST_H_PC); } return AV_PER_KPC * d / 1000 * s / n; }
    /** Bilinear splat of a star's flux into the texel radiance (flux / texel solid angle). */
    static boolean splat(float[] tex, double ra, double dec, double flux, double[] c) {
        double u = (ra / (2 * Math.PI) - Math.floor(ra / (2 * Math.PI))) * TW - 0.5, v = (0.5 - dec / Math.PI) * TH - 0.5; int i0 = (int) Math.floor(u), j0 = (int) Math.floor(v); double fu = u - i0, fv = v - j0; double y = lum(c);
        boolean any = false;
        for (int q = 0; q < 4; q++) { int i = Math.floorMod(i0 + (q & 1), TW), j = j0 + (q >> 1); if (j < 0 || j >= TH) continue; double wq = ((q & 1) == 1 ? fu : 1 - fu) * ((q >> 1) == 1 ? fv : 1 - fv); if (wq <= 0) continue;
            double decJ = Math.PI * (0.5 - (j + 0.5) / TH), sa = (2 * Math.PI / TW) * (Math.PI / TH) * Math.cos(decJ); double rad = flux * wq / Math.max(1e-9, sa);
            int k = 3 * (i + TW * j); tex[k] += (float) (rad * c[0] / y); tex[k + 1] += (float) (rad * c[1] / y); tex[k + 2] += (float) (rad * c[2] / y); any = true; }
        return any; }
    static double lum(double[] c) { return Math.max(1e-6, 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]); }
    /** The milky way at 512 x 256, bilinear into the texture: per direction, sum of density x V light per object over 50 pc steps from STAR_PC
     *  to 30 kpc (step grows past 2 kpc), through the dust; radiance ratio = sum rho L dl x a^2 / (L* PC_AU^2). */
    void milkyWay(float[] tex, Galaxy.Ref home, double lvPerObj, double Lh, double a, double[] col) {
        int MW = 512, MH = 256; float[] mw = new float[MW * MH]; double[][] c2g = {{gal2cel[0][0], gal2cel[1][0], gal2cel[2][0]}, {gal2cel[0][1], gal2cel[1][1], gal2cel[2][1]}, {gal2cel[0][2], gal2cel[1][2], gal2cel[2][2]}};
        java.util.stream.IntStream.range(0, MH).parallel().forEach(j -> { double dec = Math.PI * (0.5 - (j + 0.5) / MH);
            for (int i = 0; i < MW; i++) { double ra = 2 * Math.PI * (i + 0.5) / MW, cx = Math.cos(dec) * Math.cos(ra), cy = Math.cos(dec) * Math.sin(ra), cz = Math.sin(dec);
                double gx = c2g[0][0] * cx + c2g[0][1] * cy + c2g[0][2] * cz, gy = c2g[1][0] * cx + c2g[1][1] * cy + c2g[1][2] * cz, gz = c2g[2][0] * cx + c2g[2][1] * cy + c2g[2][2] * cz;
                double s = 0, av = AV_PER_KPC / 1000 * STAR_PC * Math.exp(-Math.abs(home.z) / DUST_H_PC), l = STAR_PC;
                while (l < 30000) { double dl = l < 2000 ? 50 : 200, lm = l + dl / 2, x = home.x + gx * lm, y = home.y + gy * lm, z = home.z + gz * lm;
                    double rho = Galaxy.density(x, y, z); s += rho * lvPerObj * dl * Math.pow(10, -0.4 * av); av += AV_PER_KPC / 1000 * dl * Math.exp(-Math.abs(z) / DUST_H_PC); l += dl; }
                mw[i + MW * j] = (float) (s * a * a / (Lh * PC_AU * PC_AU)); } });
        double y = lum(col);
        for (int j = 0; j < TH; j++) { double v = (j + 0.5) / TH * MH - 0.5; int j0 = Math.max(0, Math.min(MH - 1, (int) Math.floor(v))), j1 = Math.min(MH - 1, j0 + 1); double fv = Math.max(0, Math.min(1, v - j0));
            for (int i = 0; i < TW; i++) { double u = (i + 0.5) / TW * MW - 0.5; int i0 = Math.floorMod((int) Math.floor(u), MW), i1 = (i0 + 1) % MW; double fu = u - Math.floor(u);
                double m = (mw[i0 + MW * j0] * (1 - fu) + mw[i1 + MW * j0] * fu) * (1 - fv) + (mw[i0 + MW * j1] * (1 - fu) + mw[i1 + MW * j1] * fu) * fv; int k = 3 * (i + TW * j);
                tex[k] += (float) (m * col[0] / y); tex[k + 1] += (float) (m * col[1] / y); tex[k + 2] += (float) (m * col[2] / y); } } }
    /** Torres (2010, AJ 140, 1158) bolometric correction BC_V from Teff (his Table 1 polynomials in log Teff, after Flower 1996). */
    public static double bc(double teff) {
        double lt = Math.log10(Math.max(2000, Math.min(60000, teff)));
        double[] c = lt < 3.70 ? new double[]{-0.190537291496456e5, 0.155144866764412e5, -0.421278819301717e4, 0.381476328422343e3}
            : lt < 3.90 ? new double[]{-0.370510203809015e5, 0.385672629965804e5, -0.150651486316025e5, 0.261724637119416e4, -0.170623810323864e3}
            : new double[]{-0.118115450538963e6, 0.137145973583929e6, -0.636233812100225e5, 0.147412923562646e5, -0.170587278406872e4, 0.788731721804990e2};
        double s = 0, p = 1; for (double ci : c) { s += ci * p; p *= lt; } return s; }

    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double[] norm(double[] a) { double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }
    static double[] mul(double[] a, double k) { return new double[]{a[0] * k, a[1] * k, a[2] * k}; }
    static double[] add(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
    static void put(double[] o, double[] v) { System.arraycopy(v, 0, o, 0, 3); }
}
