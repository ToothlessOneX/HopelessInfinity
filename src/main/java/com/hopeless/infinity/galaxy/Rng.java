package com.hopeless.infinity.galaxy;

/** SplitMix64: a small, fast, seeded random source. The same seed and key give the same galaxy on every machine and every run. */
public final class Rng {
    private long s;
    public Rng(long seed) { s = seed; }
    public static long mix(long z) { z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L; z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL; return z ^ (z >>> 31); }
    public static long key(long seed, long a, long b, long c) { return mix(mix(mix(seed ^ 0x9E3779B97F4A7C15L) + a) + b * 0x632BE59BD9B4E019L + c); }
    public long next() { s += 0x9E3779B97F4A7C15L; return mix(s); }
    /** Uniform in [0,1). */
    public double u() { return (next() >>> 11) * 0x1.0p-53; }
    public double range(double lo, double hi) { return lo + (hi - lo) * u(); }
    public double gauss() { double a = Math.max(1e-300, u()), b = u(); return Math.sqrt(-2 * Math.log(a)) * Math.cos(2 * Math.PI * b); }
    /** Poisson count with mean m (Knuth for small m, normal for large). */
    public int poisson(double m) {
        if (m <= 0) return 0;
        if (m > 40) return Math.max(0, (int) Math.round(m + Math.sqrt(m) * gauss()));
        double l = Math.exp(-m), p = 1; int k = 0; do { k++; p *= u(); } while (p > l); return k - 1; }
}
