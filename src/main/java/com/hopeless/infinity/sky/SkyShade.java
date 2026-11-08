package com.hopeless.infinity.sky;

/**
 * 0.6.0: the Java twin of sky.glsl's sk_scatter (single scattering, number for number; tools/sky_parity.js checks them against each other),
 * for what the game thread needs from the sky: the eye's exposure and the fog colour at the horizon.
 *
 *  exposure   the eye adapts to the sky's mean luminance (9 directions: the zenith, 4 at 30 deg and 4 at 8 deg up): exposure =
 *             KEY / (L_mean + KEY / EXP_MAX), the photographic key of middle grey, 0.18 (Reinhard et al. 2002). EXP_MAX: the dark-adapted
 *             eye, NIGHT_GAIN x the exposure at which a magnitude 0 star's texel reaches 1; NIGHT_GAIN 12 (CHOSEN: the milky way's bright
 *             parts, about magnitude 6 a texel, then show as plainly as at a dark site, Bortle class 1; bright stars stay white).
 */
public final class SkyShade {
    private SkyShade() {}
    public static final double KEY = 0.18, NIGHT_GAIN = 12, K_MS = 0.3, MIE_G = 0.8, OZ_C = 25000, OZ_H = 15000;
    public static final double[] BETA_R = SkyModel.BETA_R, OZONE = SkyModel.OZONE; public static final double MIE_S = SkyModel.MIE_S, MIE_E = SkyModel.MIE_E;
    /** Air: {planet radius m, H_R, H_M, top} for a model (scale heights x 9.81/g). */
    public static double[] air(SkyModel m) { return new double[]{m.rPlanet, SkyModel.H_R * m.hScale, SkyModel.H_M * m.hScale, SkyModel.ATM_TOP * m.hScale}; }
    static double[] sphere(double[] o, double[] d, double r) { double b = o[0] * d[0] + o[1] * d[1] + o[2] * d[2], c = o[0] * o[0] + o[1] * o[1] + o[2] * o[2] - r * r, h = b * b - c; if (h < 0) return new double[]{-1, -1}; h = Math.sqrt(h); return new double[]{-b - h, -b + h}; }
    static double[] density(double h, double[] air) { return new double[]{Math.exp(-h / air[1]), Math.exp(-h / air[2]), Math.max(0, 1 - Math.abs(h - OZ_C * air[1] / 8000) / (OZ_H * air[1] / 8000))}; }
    static double[] ext(double[] od) { double[] e = new double[3]; for (int k = 0; k < 3; k++) e[k] = BETA_R[k] * od[0] + MIE_E * od[1] + OZONE[k] * od[2]; return e; }
    /** sk_scatter: radiance (E/sr) along v with the star at s; out[3..5] the view ray's transmittance, out[6] 1 when the ray meets the ground. */
    public static double[] scatter(double[] v, double[] s, double alt, double[] air) {
        double R = air[0], top = R + air[3]; double[] o = {0, R + Math.max(1, alt), 0}, out = new double[7]; out[3] = out[4] = out[5] = 1; if (air[3] <= 0) return out;   // 0.15.0 no air
        double[] ta = sphere(o, v, top), tg = sphere(o, v, R); if (ta[1] <= 0) return out;
        double t0 = Math.max(0, ta[0]), t1 = ta[1]; if (tg[0] > 0) { t1 = tg[0]; out[6] = 1; }
        int N = 16, M = 6; double ds = (t1 - t0) / N, mu = v[0] * s[0] + v[1] * s[1] + v[2] * s[2];
        double pr = 3.0 / (16 * Math.PI) * (1 + mu * mu), pm = (1 - MIE_G * MIE_G) / (4 * Math.PI * Math.pow(1 + MIE_G * MIE_G - 2 * MIE_G * mu, 1.5));
        double[] od = new double[3]; double[] L = new double[3];
        for (int i = 0; i < N; i++) { double t = t0 + (i + 0.5) * ds; double[] p = {o[0] + v[0] * t, o[1] + v[1] * t, o[2] + v[2] * t}; double h = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]) - R;
            double[] dn = density(h, air); for (int k = 0; k < 3; k++) { dn[k] *= ds; od[k] += dn[k] * 0.5; }
            double bb = p[0] * s[0] + p[1] * s[1] + p[2] * s[2], dmin = bb > 0 ? R + 1e9 : Math.sqrt(Math.max(0, p[0] * p[0] + p[1] * p[1] + p[2] * p[2] - bb * bb)), lit = smooth(R - 2 * air[1], R, dmin);
            if (lit > 0) { double tl = sphere(p, s, top)[1], dl = tl / M; double[] ol = new double[3];
                for (int j = 0; j < M; j++) { double tq = (j + 0.5) * dl; double[] q = {p[0] + s[0] * tq, p[1] + s[1] * tq, p[2] + s[2] * tq}; double[] dq = density(Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]) - R, air); for (int k = 0; k < 3; k++) ol[k] += dq[k] * dl; }
                double[] e = ext(new double[]{od[0] + ol[0], od[1] + ol[1], od[2] + ol[2]}); for (int k = 0; k < 3; k++) L[k] += lit * Math.exp(-e[k]) * (BETA_R[k] * dn[0] * pr + MIE_S * dn[1] * pm); }
            double lit2 = smooth(R - 6 * air[1], R + 2 * air[1], dmin); double[] ev = ext(od); for (int k = 0; k < 3; k++) L[k] += lit2 * K_MS * Math.exp(-ev[k]) * (BETA_R[k] * dn[0] + MIE_S * dn[1]) / (4 * Math.PI);
            for (int k = 0; k < 3; k++) od[k] += dn[k] * 0.5; }
        double[] e = ext(od); out[0] = L[0]; out[1] = L[1]; out[2] = L[2]; out[3] = Math.exp(-e[0]); out[4] = Math.exp(-e[1]); out[5] = Math.exp(-e[2]); return out; }
    static double smooth(double a, double b, double x) { double t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }
    public static double lum(double r, double g, double b) { return 0.2126 * r + 0.7152 * g + 0.0722 * b; }
    /** The dark-adapted limit: a magnitude 0 star's texel radiance (x the texel's solid angle at the equator, bilinear peak 1/4 at worst) to 1. */
    public static double expMax(SkyModel m) { double f0 = Math.pow(10, -0.4 * (0 - (-26.74 - 2.5 * Math.log10(m.insolation)))), sa = (2 * Math.PI / SkyModel.TW) * (Math.PI / SkyModel.TH); return NIGHT_GAIN * sa / f0; }
    /** Exposure from the zenith's luminance with the star at s. */
    public static double exposure(SkyModel m, double[] s, double alt) { double[] air = air(m); double sum = 0; int n = 0;
        for (int k = 0; k < 9; k++) { double el = k == 0 ? Math.PI / 2 : Math.toRadians(k <= 4 ? 30 : 8), az = (k % 4) * Math.PI / 2; double[] v = {Math.cos(el) * Math.cos(az), Math.sin(el), Math.cos(el) * Math.sin(az)};
            double[] z = scatter(v, s, alt, air); sum += lum(z[0], z[1], z[2]); n++; }
        return KEY / (sum / n + KEY / expMax(m)); }
    /** 0.15.0: on an airless body the eye adapts to the sunlit ground, not to a sky: the luminance of regolith of the Moon's Bond albedo 0.11
     *  (NASA Moon fact sheet) under the star at its height, L = 0.11 E sin(alt) / pi, the night side falling to the dark-adapted limit. */
    public static double exposureAirless(SkyModel m, double[] s) { double g = 0.11 * Math.max(0, s[1]) / Math.PI; return KEY / (g + KEY / expMax(m)); }
    /** ACES fit and 2.2 display curve, the shader's sk_display (rain: darker and greyer). */
    public static double[] display(double[] L, double exposure, double rain) {
        double[] c = L.clone(); for (int k = 0; k < 3; k++) c[k] *= 1 - 0.55 * rain; double y = lum(c[0], c[1], c[2]); double[] o = new double[3];
        for (int k = 0; k < 3; k++) { double x = (c[k] + (y - c[k]) * 0.6 * rain) * exposure; double a = Math.max(0, Math.min(1, (x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14))); o[k] = Math.pow(a, 1 / 2.2); }
        return o; }
}
