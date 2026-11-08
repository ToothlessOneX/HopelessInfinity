package com.hopeless.infinity.galaxy;

import static com.hopeless.infinity.galaxy.Research.*;

/** A main-sequence star: class by LeDrew's fractions, mass inside the class by Kroupa's IMF, the rest from Mamajek's sequence. */
public final class Star {
    public final int cls; public final double mass, teff, lum, radius; public final String type;

    Star(int cls, double mass) {
        this.cls = cls; this.mass = mass;
        // Mamajek rows are in falling mass; interpolate log values on log mass between the two rows around this mass
        int i = 0; while (i < MS_M.length - 2 && MS_M[i + 1] > mass) i++;
        double t = (Math.log(mass) - Math.log(MS_M[i])) / (Math.log(MS_M[i + 1]) - Math.log(MS_M[i]));
        teff = Math.exp(lerp(Math.log(MS_TEFF[i]), Math.log(MS_TEFF[i + 1]), t));
        lum = Math.pow(10, lerp(MS_LOGL[i], MS_LOGL[i + 1], t));
        radius = Math.exp(lerp(Math.log(MS_R[i]), Math.log(MS_R[i + 1]), t));
        type = CLASSES.charAt(cls) + subtype() + "V";
    }
    /** A star of a given class and mass (tests and commands). */
    public static Star of(int cls, double mass) { return new Star(cls, mass); }
    static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    /** 0-9 subclass from where the temperature sits between the class's rows (display only). */
    String subtype() { double hi = MS_TEFF[CLASS_ROW[cls]], lo = MS_TEFF[CLASS_ROW[cls + 1]]; int s = (int) Math.floor(10 * (Math.log(hi) - Math.log(teff)) / (Math.log(hi) - Math.log(lo)));
        return Integer.toString(Math.max(cls == 0 ? 5 : 0, Math.min(9, s + (cls == 0 ? 5 : 0)))); }

    static Star make(Rng r) {
        double u = r.u() * sum(CLASS_FRACTION), a = 0; int cls = CLASS_FRACTION.length - 1;
        for (int c = 0; c < CLASS_FRACTION.length; c++) { a += CLASS_FRACTION[c]; if (u < a) { cls = c; break; } }
        double hi = MS_M[CLASS_ROW[cls]], lo = MS_M[CLASS_ROW[cls + 1]];
        return new Star(cls, imf(r, lo, hi));
    }
    static double sum(double[] v) { double s = 0; for (double x : v) s += x; return s; }
    /** A mass in [lo, hi) from Kroupa's broken power law (inverse transform on each piece, the pieces weighted by their integrals). */
    static double imf(Rng r, double lo, double hi) {
        double b = IMF_BREAK;
        double wLow = lo < b ? piece(lo, Math.min(hi, b), IMF_LOW) : 0, wHigh = hi > b ? piece(Math.max(lo, b), hi, IMF_HIGH) * Math.pow(b, IMF_HIGH - IMF_LOW) : 0;
        return r.u() * (wLow + wHigh) < wLow ? draw(r, lo, Math.min(hi, b), IMF_LOW) : draw(r, Math.max(lo, b), hi, IMF_HIGH);
    }
    static double piece(double a, double b, double s) { double k = 1 - s; return (Math.pow(b, k) - Math.pow(a, k)) / k; }
    static double draw(Rng r, double a, double b, double s) { double k = 1 - s, ak = Math.pow(a, k), bk = Math.pow(b, k); return Math.pow(ak + r.u() * (bk - ak), 1 / k); }

    /** Habitable-zone edge in AU (Kopparapu 2013; the temperature is held inside the fit's 2600-7200 K range). */
    public double hz(int edge) {
        double t = Math.max(HZ_TEFF_MIN, Math.min(HZ_TEFF_MAX, teff)) - 5780, c[] = HZ[edge];
        double seff = c[0] + c[1] * t + c[2] * t * t + c[3] * t * t * t + c[4] * t * t * t * t;
        return Math.sqrt(lum / seff); }
    /** Seff at a habitable-zone edge (for comparing a planet's insolation). */
    public double hzSeff(int edge) { double t = Math.max(HZ_TEFF_MIN, Math.min(HZ_TEFF_MAX, teff)) - 5780, c[] = HZ[edge];
        return c[0] + c[1] * t + c[2] * t * t + c[3] * t * t * t + c[4] * t * t * t * t; }
    public double snowLine() { return SNOW_LINE_AU * Math.sqrt(lum); }
}
