package com.hopeless.infinity.vehicle;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.13.0 THE SPACEPLANE (owner, 2026-11-02: "a simple ship ... sized to fit the player body ... perportions that a real space ship would
 * have"; chose the X-15-class spaceplane and an advanced engine as a stated rule). Pure data, shape and flight physics (no Minecraft
 * classes; tools/X15Test and tools/x15_view.py use it). The North American X-15 as flown (JSBSim's NASA-derived X-15 data page):
 *  fuselage 50.75 ft (15.47 m), span 22.36 ft (6.82 m), wing area 200 ft2 (18.58 m2), empty 15,560 lb (7,058 kg), propellant 15,680 lb
 *  (7,112 kg); XLR99: 59,000 lbf (262.4 kN) in vacuum, 51,090 lbf (227.3 kN) at sea level, Isp 276 s vacuum, 90 s of burn.
 *  It flew most flights from 1963 without its lower ventral fin (dropped before landing when fitted), as here.
 * STATED GAME RULE, the advanced engine: the same airframe, masses and thrust, but the engine's propellant gives ADV_DV (20 km/s) of
 * ideal delta-v, enough to reach orbit and brake back down under power (Isp RULE_ISP s, about 10.6 x the XLR99's). The real X-15 had
 * about 2 km/s and was dropped from a B-52.
 * Aerodynamics (power off, NASA TN D-3343, Saltzman & Garringer 1966): zero-lift drag about doubles through the transonic rise (the rise
 * equals the subsonic value), then falls steadily with Mach; at Mach 5 the drag-due-to-lift factor is 1.43 x the inverse of the lift-curve
 * slope. Subsonic lift-curve slope: Helmbold's low-aspect-ratio formula (A = 2.50: 3.02 per radian); supersonic: Ackeret's 4 / sqrt(M2-1),
 * never above the subsonic value. Subsonic CD0 0.08 and Oswald e 0.8 (CHOSEN: they give the subsonic L/D max of 4.4; the X-15 landed at
 * about 4.5). Lift above 20 degrees of attack falls to 0.6 x (stall, CHOSEN).
 * Air: the planet's own pressure and molar mass (galaxy/Atmosphere), with the International Standard Atmosphere's temperature profile
 * (ICAO Doc 7488) for density and the speed of sound (STATED: no per-planet temperature profile yet).
 * Heating: Sutton-Graves stagnation heating (q ~ sqrt(rho) V^3); the limit is the X-15A-2's Mach 6.7 flight (2,020 m/s at 102,100 ft,
 * 3 October 1967), which burned through the airframe's ablative coat and dummy ramjet. Above it the airframe is damaged.
 * The cockpit was pressurised (3.5 psi, nitrogen) and the pilot fed oxygen: the pilot breathes in it (STATED).
 */
public final class X15 {
    private X15() {}
    public static final double L = 15.47, SPAN = 6.82, S = 18.58, AR = SPAN * SPAN / S, EMPTY_KG = 7058, PROP_KG = 7112, PILOT_KG = 90.6;
    public static final double F_VAC = 59000 * 4.44822, F_SL = 51090 * 4.44822, G0 = 9.80665, REAL_ISP = 276;
    public static final double ADV_DV = 20000, RULE_ISP = ADV_DV / (G0 * Math.log((EMPTY_KG + PROP_KG + PILOT_KG) / (EMPTY_KG + PILOT_KG)));
    public static final double MDOT = F_VAC / (RULE_ISP * G0);   // kg/s at full throttle
    public static final double MIN_THROTTLE = 15000.0 / 57000.0;   // the XLR99 throttled from 15,000 to 57,000 lbf
    public static final double TURN_DEG_S = 20;   // the nose follows the pilot's look at most this fast (CHOSEN; aerodynamic surfaces in air, reaction jets in space as the X-15 had)
    public static final double CD0_SUB = 0.08, OSWALD = 0.8, ALPHA_STALL = Math.toRadians(20);
    public static final double HEAT_REF_V = 2020, HEAT_REF_H = 102100 * 0.3048;
    public static final double SINK_LIMIT = 3.0, CRASH_SPEED = 15;   // m/s: a touchdown faster than 3 m/s down damages the gear (CHOSEN near the X-15's ~9 ft/s design sink rate); hitting a wall faster than 15 m/s wrecks it (CHOSEN)
    public static final double SKID_MU = 0.3;   // the steel rear skids on a dry lakebed (CHOSEN)
    public static final double REFUEL_S = 300;   // the oxygen station fills the tanks in 5 min (STATED)
    public static final double SEAT_FWD = 3.4, SEAT_UP = 0.35, BASE_UP = 1.0;   // the cockpit 4.3 m behind the nose; the model's centre line 1.0 m above its skids

    // ---------------- air ----------------
    /** ISA temperature (K) at a geometric height (m), the 1976 layers to 84.85 km. */
    public static double isaT(double h) {
        if (h < 11000) return 288.15 - 0.0065 * h; if (h < 20000) return 216.65; if (h < 32000) return 216.65 + 0.001 * (h - 20000); if (h < 47000) return 228.65 + 0.0028 * (h - 32000);
        if (h < 51000) return 270.65; if (h < 71000) return 270.65 - 0.0028 * (h - 51000); if (h < 84852) return 214.65 - 0.002 * (h - 71000); return 186.87; }
    /** Earth's ISA pressure (Pa) at h (m) (for the heating reference only). */
    public static double isaP(double h) { double p = 101325, t = 288.15, z = 0; double[][] lay = {{11000, -0.0065}, {20000, 0}, {32000, 0.001}, {47000, 0.0028}, {51000, 0}, {71000, -0.0028}, {84852, -0.002}};
        for (double[] l : lay) { double top = Math.min(h, l[0]), dz = top - z; if (dz <= 0) break; double gM = 9.80665 * 0.0289644 / 8.31446;
            if (l[1] == 0) p *= Math.exp(-gM * dz / t); else { double t2 = t + l[1] * dz; p *= Math.pow(t / t2, gM / l[1]); t = t2; } z = top; if (h <= l[0]) return p; }
        return p * Math.exp(-(h - z) / 7000); }
    public static double density(double pAtm, double muGPerMol, double h) { return pAtm * 101325 * muGPerMol / 1000 / (8.31446 * isaT(h)); }
    public static double sound(double muGPerMol, double h) { return Math.sqrt(1.4 * 8.31446 / (muGPerMol / 1000) * isaT(h)); }

    // ---------------- aerodynamics ----------------
    public static double clAlpha(double m) { double sub = 2 * Math.PI * AR / (2 + Math.sqrt(AR * AR + 4)); if (m <= 0.9) return sub; double sup = 4 / Math.sqrt(Math.max(1e-6, m * m - 1));
        if (m >= 1.2) return Math.min(sub, sup); double f = (m - 0.9) / 0.3; return sub + (Math.min(sub, 4 / Math.sqrt(1.44 - 1)) - sub) * f; }
    public static double cd0(double m) { if (m <= 0.8) return CD0_SUB; if (m <= 1.1) return CD0_SUB * (1 + (m - 0.8) / 0.3); return 2 * CD0_SUB * Math.pow(1.1 / m, 0.5); }
    public static double kInduced(double m) { double sub = 1 / (Math.PI * OSWALD * AR), sup = 1.43 / clAlpha(Math.max(m, 1.2)); if (m <= 0.9) return sub; if (m >= 1.5) return sup; return sub + (sup - sub) * (m - 0.9) / 0.6; }
    /** Sutton-Graves heating relative to the X-15A-2's Mach 6.7 flight (1 = that flight). */
    public static double heat(double rho, double v) { double rhoRef = isaP(HEAT_REF_H) * 0.0289644 / (8.31446 * isaT(HEAT_REF_H)); return Math.sqrt(Math.max(0, rho) / rhoRef) * Math.pow(v / HEAT_REF_V, 3); }

    /** One flight step's forces (no gravity): velocity v and nose (unit) in any frame, 'up' the local vertical there; out = acceleration (m/s2);
     *  returns {mach, dynamic pressure Pa, heat ratio, alpha rad, thrust N, lift N, drag N}. pAtm, mu: the air here; massKg the whole craft. */
    public static double[] forces(double[] v, double[] nose, double pAtm, double mu, double hM, double throttle, double massKg, double[] out) {
        double V = Math.sqrt(dot(v, v)), rho = pAtm > 0 ? density(pAtm, mu, hM) : 0, a = pAtm > 0 ? sound(mu, hM) : 300, m = V / a, q = 0.5 * rho * V * V;
        double thrust = throttle <= 0 ? 0 : throttle * Math.max(0, F_VAC - (F_VAC - F_SL) * Math.min(1, pAtm));
        for (int i = 0; i < 3; i++) out[i] = nose[i] * thrust / massKg;
        double alpha = 0, lift = 0, drag = 0;
        if (V > 0.5 && q > 1e-6) { double[] vh = {v[0] / V, v[1] / V, v[2] / V}; double c = Math.max(-1, Math.min(1, dot(nose, vh))); alpha = Math.acos(c);
            double[] ln = {nose[0] - c * vh[0], nose[1] - c * vh[1], nose[2] - c * vh[2]}; double ll = Math.sqrt(dot(ln, ln));
            double ae = Math.min(alpha, ALPHA_STALL), cl = clAlpha(m) * ae * (alpha > ALPHA_STALL ? 0.6 : 1) * (c < 0 ? -1 : 1), cd = cd0(m) + kInduced(m) * cl * cl + (alpha > ALPHA_STALL ? 1.2 * Math.sin(alpha) * Math.sin(alpha) : 0);   // flat-plate drag past the stall (CD ~ 1.2 sin^2)
            lift = q * S * Math.abs(cl); drag = q * S * cd;
            for (int i = 0; i < 3; i++) out[i] += (ll > 1e-9 ? ln[i] / ll * lift * Math.signum(cl == 0 ? 1 : cl) : 0) / massKg - vh[i] * drag / massKg; }
        return new double[]{m, q, heat(rho, V), alpha, thrust, lift, drag}; }
    /** Turns 'nose' toward 'want' by at most maxRad (both unit). */
    public static double[] turn(double[] nose, double[] want, double maxRad) { double c = Math.max(-1, Math.min(1, dot(nose, want))), ang = Math.acos(c); if (ang <= maxRad || ang < 1e-9) return want.clone();
        double[] p = {want[0] - c * nose[0], want[1] - c * nose[1], want[2] - c * nose[2]}; double pl = Math.sqrt(dot(p, p)); if (pl < 1e-9) return nose.clone();
        double[] r = new double[3]; for (int i = 0; i < 3; i++) r[i] = nose[i] * Math.cos(maxRad) + p[i] / pl * Math.sin(maxRad); return r; }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    // ---------------- shape ----------------
    /** A solid of 8 corners (metres; x right, y up, z forward, the origin on the centre line above the skids) and a colour. Corners: the four
     *  at the back (z min) then the four at the front, each in order bottom-left, bottom-right, top-right, top-left (seen from the front). */
    public record Part(String name, double[][] c, int rgb) {}
    static final int BLACK = 0x161616, TUNNEL = 0x1F1F1F, NOZZLE = 0x4A4A4A, GLASS = 0x2B3640, FRAME = 0x2A2A2A, METAL = 0x8C8F94, TYRE = 0x101010;
    /** From the nose (station s, m) to z. */
    static double z(double s) { return 7.74 - s; }
    static Part box(String n, double x0, double x1, double y0, double y1, double s0, double s1, int rgb) { return taper(n, s0, s1, x0, x1, y0, y1, x0, x1, y0, y1, rgb); }
    /** A tapered solid between station s0 (back... no: s0 nearer the nose) and s1, each end its own x and y range. */
    static Part taper(String n, double sF, double sB, double fx0, double fx1, double fy0, double fy1, double bx0, double bx1, double by0, double by1, int rgb) {
        double zf = z(sF), zb = z(sB);
        return new Part(n, new double[][]{{bx0, by0, zb}, {bx1, by0, zb}, {bx1, by1, zb}, {bx0, by1, zb}, {fx0, fy0, zf}, {fx1, fy0, zf}, {fx1, fy1, zf}, {fx0, fy1, zf}}, rgb); }
    /** A flat surface (wing, tail) from the root to the tip: root leading/trailing stations, tip leading/trailing, root and tip x and y, thicknesses. */
    static Part panel(String n, double rLE, double rTE, double tLE, double tTE, double rx, double tx, double ry, double ty, double rt, double tt, int rgb) {
        // corners: 'back' = trailing edge, 'front' = leading edge; left = root side for the right wing (x grows)
        double lo = Math.min(rx, tx), hi = Math.max(rx, tx); boolean right = tx > rx;
        double[] a = right ? new double[]{rx, ry, rt} : new double[]{tx, ty, tt}, b = right ? new double[]{tx, ty, tt} : new double[]{rx, ry, rt};
        double aLE = right ? rLE : tLE, aTE = right ? rTE : tTE, bLE = right ? tLE : rLE, bTE = right ? tTE : rTE;
        return new Part(n, new double[][]{{a[0], a[1] - a[2] / 2, z(aTE)}, {b[0], b[1] - b[2] / 2, z(bTE)}, {b[0], b[1] + b[2] / 2, z(bTE)}, {a[0], a[1] + a[2] / 2, z(aTE)},
            {a[0], a[1] - a[2] / 2, z(aLE)}, {b[0], b[1] - b[2] / 2, z(bLE)}, {b[0], b[1] + b[2] / 2, z(bLE)}, {a[0], a[1] + a[2] / 2, z(aLE)}}, rgb); }
    /** A wedge fin standing on y0 up (or down) to y1: root and tip chords, thin at the leading edge, thick at the trailing edge (the X-15's wedge tails). */
    static Part fin(String n, double rLE, double rTE, double tLE, double tTE, double y0, double y1, double tLEw, double tTEw, int rgb) {
        double lo = Math.min(y0, y1), hi = Math.max(y0, y1); boolean up = y1 > y0; double bLE = up ? rLE : tLE, bTE = up ? rTE : tTE, tL = up ? tLE : rLE, tT = up ? tTE : rTE;
        return new Part(n, new double[][]{{-tTEw / 2, lo, z(bTE)}, {tTEw / 2, lo, z(bTE)}, {tTEw / 2, hi, z(tT)}, {-tTEw / 2, hi, z(tT)},
            {-tLEw / 2, lo, z(bLE)}, {tLEw / 2, lo, z(bLE)}, {tLEw / 2, hi, z(tL)}, {-tLEw / 2, hi, z(tL)}}, rgb); }

    /** The X-15 as built from the photos (NASA E-4358 rollout, top view; the 1:10 models' side views) to the real dimensions. */
    public static List<Part> shape() {
        List<Part> p = new ArrayList<>(); double w = 0.66, h = 0.66;   // fuselage half width and half height (about 1.3 m across; the photos: about a twelfth of its length)
        p.add(taper("nose tip", 0.0, 1.2, -0.04, 0.04, -0.04, 0.04, -0.32, 0.32, -0.30, 0.30, BLACK));
        p.add(taper("nose", 1.2, 3.0, -0.32, 0.32, -0.30, 0.30, -w, w, -h, h, BLACK));
        p.add(box("fuselage", -w, w, -h, h, 3.0, 14.9, BLACK));
        p.add(taper("tail cone", 14.9, 15.47, -w, w, -h, h, -0.58, 0.58, -0.58, 0.58, BLACK));
        p.add(taper("nozzle", 15.47, 15.85, -0.45, 0.45, -0.45, 0.45, -0.5, 0.5, -0.5, 0.5, NOZZLE));
        // the side tunnels (propellant lines and wiring), each side, faired in at the front
        for (int sd = -1; sd <= 1; sd += 2) { double xa = sd * w, xb = sd * (w + 0.4);
            p.add(taper("tunnel front", 4.0, 5.2, Math.min(xa, xa), Math.max(xa, xa) + (sd < 0 ? 0 : 0.02), -0.2, 0.1, Math.min(xa, xb), Math.max(xa, xb), -0.35, 0.2, TUNNEL));
            p.add(box("tunnel", Math.min(xa, xb), Math.max(xa, xb), -0.35, 0.2, 5.2, 14.4, TUNNEL)); }
        // the canopy: a narrow hump behind the nose, glass at the front, the frame behind
        p.add(taper("windscreen", 3.1, 3.9, -0.30, 0.30, h - 0.05, h + 0.02, -0.36, 0.36, h - 0.05, h + 0.36, GLASS));
        p.add(taper("canopy", 3.9, 5.4, -0.36, 0.36, h - 0.05, h + 0.36, -0.30, 0.30, h - 0.05, h + 0.12, FRAME));
        p.add(taper("spine", 5.4, 11.9, -0.30, 0.30, h - 0.05, h + 0.12, -0.2, 0.2, h - 0.05, h + 0.02, BLACK));
        // the wings: root at the tunnel's outer side, 3.7 m root chord to 0.85 m at the tip, leading edge swept 30 degrees, mid-fuselage low
        for (int sd = -1; sd <= 1; sd += 2) p.add(panel("wing", 7.4, 11.1, 9.01, 9.86, sd * (w + 0.4), sd * SPAN / 2, -0.25, -0.25, 0.16, 0.05, BLACK));
        // the all-moving horizontal tail, 15 degrees of anhedral, at the very back
        for (int sd = -1; sd <= 1; sd += 2) p.add(panel("tailplane", 12.9, 15.3, 14.05, 14.85, sd * (w + 0.4), sd * 2.75, -0.15, -0.15 - (2.75 - w - 0.4) * Math.tan(Math.toRadians(15)), 0.12, 0.05, BLACK));
        // the upper vertical tail: a thick wedge, 1.5 m tall
        p.add(fin("upper fin", 11.9, 15.47, 12.87, 15.47, h - 0.05, h + 1.5, 0.06, 0.55, BLACK));
        // the gear: two steel rear skids under the tail, the twin nose wheels
        for (int sd = -1; sd <= 1; sd += 2) { p.add(box("skid strut", sd * 0.85 - 0.04, sd * 0.85 + 0.04, -BASE_UP + 0.05, -h + 0.1, 12.2, 12.4, METAL)); p.add(box("skid", sd * 0.85 - 0.06, sd * 0.85 + 0.06, -BASE_UP, -BASE_UP + 0.06, 11.6, 13.2, METAL));
            p.add(box("nose wheel", sd * 0.13 - 0.07, sd * 0.13 + 0.07, -BASE_UP, -BASE_UP + 0.42, 2.6, 3.02, TYRE)); }
        p.add(box("nose strut", -0.04, 0.04, -BASE_UP + 0.21, -h + 0.05, 2.75, 2.87, METAL));
        return p; }
}
