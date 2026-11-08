package com.hopeless.infinity.planet;

/**
 * Seeded 3D simplex noise (Gustavson's public-domain simplex algorithm, Ken Perlin's simplex grid; kernel radius^2 0.5, not 0.6: 0.6 reaches past the simplex and makes small jumps - measured at the cube seams; output x83.5 keeps the 0.6 version's spread, 0.427), plus fractal sums. Our own code:
 * the planet surface needs smooth 3D noise sampled on the sphere so the six cube faces join without seams.
 */
public final class Noise3 {
    private final short[] perm = new short[512];
    private static final int[][] G = {{1,1,0},{-1,1,0},{1,-1,0},{-1,-1,0},{1,0,1},{-1,0,1},{1,0,-1},{-1,0,-1},{0,1,1},{0,-1,1},{0,1,-1},{0,-1,-1}};
    public Noise3(long seed) {
        short[] p = new short[256]; for (short i = 0; i < 256; i++) p[i] = i;
        long s = seed; for (int i = 255; i > 0; i--) { s = s * 6364136223846793005L + 1442695040888963407L; int r = (int) ((s >>> 33) % (i + 1)); if (r < 0) r += i + 1; short t = p[i]; p[i] = p[r]; p[r] = t; }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
    }
    private static int fl(double x) { int i = (int) x; return x < i ? i - 1 : i; }
    /** Simplex noise in about [-1, 1]. */
    public double at(double x, double y, double z) {
        final double F3 = 1.0 / 3, G3 = 1.0 / 6;
        double s = (x + y + z) * F3; int i = fl(x + s), j = fl(y + s), k = fl(z + s);
        double t = (i + j + k) * G3, x0 = x - (i - t), y0 = y - (j - t), z0 = z - (k - t);
        int i1, j1, k1, i2, j2, k2;
        if (x0 >= y0) { if (y0 >= z0) { i1=1;j1=0;k1=0;i2=1;j2=1;k2=0; } else if (x0 >= z0) { i1=1;j1=0;k1=0;i2=1;j2=0;k2=1; } else { i1=0;j1=0;k1=1;i2=1;j2=0;k2=1; } }
        else { if (y0 < z0) { i1=0;j1=0;k1=1;i2=0;j2=1;k2=1; } else if (x0 < z0) { i1=0;j1=1;k1=0;i2=0;j2=1;k2=1; } else { i1=0;j1=1;k1=0;i2=1;j2=1;k2=0; } }
        double x1 = x0 - i1 + G3, y1 = y0 - j1 + G3, z1 = z0 - k1 + G3, x2 = x0 - i2 + 2 * G3, y2 = y0 - j2 + 2 * G3, z2 = z0 - k2 + 2 * G3, x3 = x0 - 1 + 3 * G3, y3 = y0 - 1 + 3 * G3, z3 = z0 - 1 + 3 * G3;
        int ii = i & 255, jj = j & 255, kk = k & 255;
        return 83.5 * (c(x0, y0, z0, perm[ii + perm[jj + perm[kk]]] % 12) + c(x1, y1, z1, perm[ii + i1 + perm[jj + j1 + perm[kk + k1]]] % 12)
            + c(x2, y2, z2, perm[ii + i2 + perm[jj + j2 + perm[kk + k2]]] % 12) + c(x3, y3, z3, perm[ii + 1 + perm[jj + 1 + perm[kk + 1]]] % 12));
    }
    private static double c(double x, double y, double z, int g) { double t = 0.5 - x * x - y * y - z * z; if (t < 0) return 0; t *= t; return t * t * (G[g][0] * x + G[g][1] * y + G[g][2] * z); }
    /** Fractal sum: octaves at doubling frequency, halving amplitude, normalised to about [-1, 1]. */
    public double fbm(double x, double y, double z, int oct) { double sum = 0, amp = 1, norm = 0, f = 1; for (int o = 0; o < oct; o++) { sum += amp * at(x * f + o * 17.3, y * f - o * 9.1, z * f + o * 5.7); norm += amp; amp *= 0.5; f *= 2.03; } return sum / norm; }
    /** Ridged fractal (sharp crests where the noise crosses zero) in [0, 1]. */
    public double ridged(double x, double y, double z, int oct) { double sum = 0, amp = 1, norm = 0, f = 1; for (int o = 0; o < oct; o++) { double n = 1 - Math.abs(at(x * f + o * 31.7, y * f + o * 3.3, z * f - o * 12.9)); sum += amp * n * n; norm += amp; amp *= 0.5; f *= 2.1; } return sum / norm; }
}
