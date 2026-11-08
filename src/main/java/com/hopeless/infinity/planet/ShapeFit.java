package com.hopeless.infinity.planet;

/** 0.28.3 the best shaped block (planet slope/ShapeLib, ported from Hopeless Deep) for one cell of a curved band in a vertical plane,
 *  the method of the great gate's ring (planet/GreatGate.fit), made general: the band is sampled 8 x 8 in the cell's (z, y) plane; each
 *  candidate shape and turn whose collision is the same all along x (the band's depth) is scored by the samples it gets wrong; a whole
 *  block scores the samples outside the band; a shape must beat the block by more than 2 of 64. Result: null (the cell is not in the
 *  band), "" (a whole block) or "<shape>:<turn>". */
public final class ShapeFit {
    public interface Band { boolean in(double z, double y); }
    static final String[] CAND = {"slope", "half_slope", "half_slope_inv", "round_slope", "round_inv_corner", "corner_square"};
    public static String band(Band f, int z, int y) { boolean[] in = new boolean[64]; int nIn = 0;
        for (int a = 0; a < 8; a++) for (int b = 0; b < 8; b++) { in[a * 8 + b] = f.in(z + (a + 0.5) / 8 - 0.5, y + (b + 0.5) / 8 - 0.5); if (in[a * 8 + b]) nIn++; }
        if (nIn == 0) return null; int best = 64 - nIn; String out = "";
        for (String c : CAND) { Integer si = com.hopeless.infinity.slope.ShapeLib.INDEX.get(c); if (si == null) continue;
            for (int o = 0; o < 24; o++) { boolean[][][] v = new boolean[8][8][8]; for (int[] bx : com.hopeless.infinity.slope.ShapeLib.boxes(si, o)) for (int i = bx[0] / 2; i < bx[3] / 2; i++) for (int j = bx[1] / 2; j < bx[4] / 2; j++) for (int k = bx[2] / 2; k < bx[5] / 2; k++) v[i][j][k] = true;
                boolean uniform = true; for (int i = 1; i < 8 && uniform; i++) for (int j = 0; j < 8 && uniform; j++) for (int k = 0; k < 8; k++) if (v[i][j][k] != v[0][j][k]) { uniform = false; break; }
                if (!uniform) continue; int wrong = 0; for (int a = 0; a < 8; a++) for (int b = 0; b < 8; b++) if (v[0][b][a] != in[a * 8 + b]) wrong++;
                if (wrong < best - 2) { best = wrong; out = c + ":" + o; } } }
        return out; }
    private ShapeFit() {}
}
