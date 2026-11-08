package com.hopeless.infinity.slope;

/**
 * Terrain slopes (from Hopeless Deep 0.26.0) - a height surface over one block with a height at each corner in quarter blocks (SlopeMesh, L = 4), so the
 * ground rises smoothly. Neighbouring columns compute each shared corner from the same four columns, so they meet with no seam. Pure
 * Java: the generator and the previews use it; the block (slope.SlopeReg) reads the same codes.
 */
public final class Slopes {
    private Slopes() {}
    public static final int L = 4, CODES = (L + 1) * (L + 1) * (L + 1) * (L + 1);   // 625
    /** Materials with a terrain slope: the full block it copies (id) -> slope block id. Every natural surface block of the generator,
     *  (Hopeless Deep's road surfaces are left out: no roads here). */
    public static final java.util.Map<String, String> MAT = new java.util.LinkedHashMap<>();
    static {
        for (String b : new String[]{"stone", "deepslate", "grass_block", "dirt", "sand", "gravel", "clay", "mud", "podzol", "coarse_dirt", "andesite", "granite",
            "diorite", "tuff", "calcite", "amethyst_block", "moss_block", "mycelium", "rooted_dirt", "basalt", "blackstone", "magma_block", "cobblestone",
            "dripstone_block", "sandstone", "smooth_basalt", "packed_mud", "mossy_cobblestone", "terracotta", "snow_block", "red_sand", "packed_ice"}) MAT.put(b, "slope_" + b);
    }
    public static volatile long placed, flat, tooSteep, clamped;   // probes

    /**
     * The terrain slope for a column whose top full block is at y = g. s9: the true surface heights (top of the ground, in blocks) of the
     * 3 x 3 columns round it, row by row from north-west (s9[4] is this column; NaN where unknown: water, walls). Returns {y, code}:
     * the piece goes at y = g (replacing the top block) or g + 1 (on it). Null when flat or too steep.
     */
    public static int[] choose(int g, double[] s9) {
        double c = s9[4]; if (Double.isNaN(c)) return null;
        double[] s = s9.clone(); for (int i = 0; i < 9; i++) if (Double.isNaN(s[i])) s[i] = c;
        double nw = (s[0] + s[1] + s[3] + s[4]) / 4, ne = (s[1] + s[2] + s[4] + s[5]) / 4, se = (s[4] + s[5] + s[7] + s[8]) / 4, sw = (s[3] + s[4] + s[6] + s[7]) / 4;
        double lo = Math.min(Math.min(nw, ne), Math.min(se, sw)), hi = Math.max(Math.max(nw, ne), Math.max(se, sw));
        if (hi - lo > 1.5) { tooSteep++; return null; }
        int y = Math.max(g, Math.min(g + 1, (int) Math.floor(lo + 1e-6)));
        int[] q = new int[4]; double[] h = {nw, ne, se, sw}; boolean cl = false;
        for (int i = 0; i < 4; i++) { int v = (int) Math.round((h[i] - y) * L); if (v > L || v < 0) cl = true; q[i] = Math.max(0, Math.min(L, v)); }
        if (cl) clamped++;
        if (y == g && q[0] == L && q[1] == L && q[2] == L && q[3] == L) { flat++; return null; }   // the full top block is the surface
        if (y == g + 1 && q[0] == 0 && q[1] == 0 && q[2] == 0 && q[3] == 0) { flat++; return null; }
        if (q[0] == 0 && q[1] == 0 && q[2] == 0 && q[3] == 0) { flat++; return null; }
        placed++; return new int[]{y, SlopeMesh.encode(q, L)};
    }
    /** The block string for a material's slope (null if the material has none). */
    public static String block(String fullBlock, int code) {
        String id = fullBlock.replace("minecraft:", ""); int b = id.indexOf('['); if (b >= 0) id = id.substring(0, b);
        String sl = MAT.get(id); return sl == null ? null : "hopelessinfinity:" + sl + "[corners=" + code + "]";
    }
}
