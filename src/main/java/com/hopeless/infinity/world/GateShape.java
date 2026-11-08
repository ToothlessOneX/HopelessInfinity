package com.hopeless.infinity.world;

/**
 * 0.14.0: the gate ring's shape, pure (tools/GateTest). 0.14.1 (owner, 2026-11-02: "the gate controller block cant be on bottom. most
 * people are going to want to bury the bottom layer ... it should be able to be placed anywhere in the ring as long as it is attached"):
 * the ring is 16 cells, a 5 x 5 upright square's edge; the controller is either one of those 16 cells (any one) or touches one of the
 * frame blocks face to face from outside. Found from the controller: every ring it could belong to is tried.
 */
public final class GateShape {
    private GateShape() {}
    /** True where (dx, dy, dz) from the controller is a gate frame block. */
    public interface Frame { boolean at(int dx, int dy, int dz); }
    /** A ring found: its bottom-middle cell relative to the controller (the field fills the 3 x 3 above it), and its axis (0 along x, 1 along z). */
    public record Found(int ox, int oy, int oz, int axis, boolean controllerInRing) {}
    static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    /** The 16 ring cells relative to a ring's bottom-middle cell. */
    public static int[][] cells(int axis) { int[][] c = new int[16][]; int k = 0; for (int a = -2; a <= 2; a++) for (int y = 0; y <= 4; y++) if (Math.abs(a) == 2 || y == 0 || y == 4) c[k++] = axis == 0 ? new int[]{a, y, 0} : new int[]{0, y, a}; return c; }
    /** The ring this controller belongs to, or null. A ring with the controller in it is preferred to one it only touches. */
    public static Found ring(Frame f) {
        Found touch = null;
        for (int axis = 0; axis < 2; axis++) { int[][] cs = cells(axis);
            // the controller in the ring: at each of the 16 cells in turn
            for (int[] me : cs) { int ox = -me[0], oy = -me[1], oz = -me[2]; boolean ok = true;
                for (int[] c : cs) { if (c == me) continue; if (!f.at(ox + c[0], oy + c[1], oz + c[2])) { ok = false; break; } }
                if (ok) return new Found(ox, oy, oz, axis, true); }
            // the controller outside, touching a frame block: that block at each of the 16 cells in turn
            if (touch == null) for (int[] d : FACES) { if (!f.at(d[0], d[1], d[2])) continue;
                for (int[] at : cs) { int ox = d[0] - at[0], oy = d[1] - at[1], oz = d[2] - at[2]; boolean ok = true, selfInside = false;
                    for (int[] c : cs) { int x = ox + c[0], y = oy + c[1], z = oz + c[2]; if (x == 0 && y == 0 && z == 0) { selfInside = true; break; } if (!f.at(x, y, z)) { ok = false; break; } }
                    // nor inside the field (the 3 x 3 middle)
                    int ix = -ox, iy = -oy, iz = -oz; boolean inMiddle = iy >= 1 && iy <= 3 && (axis == 0 ? (iz == 0 && Math.abs(ix) <= 1) : (ix == 0 && Math.abs(iz) <= 1));
                    if (ok && !selfInside && !inMiddle) { touch = new Found(ox, oy, oz, axis, false); break; } }
                if (touch != null) break; } }
        return touch; }
}
