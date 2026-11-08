package com.hopeless.infinity.planet;

/**
 * 0.27.3 THE COBBLE & SONS FRONTAGE (owner, 2026-11-04: "the building would have large signage on the front, maybe a large LCD screen ...
 * it would play a generic advertisement on the screen instead with the company logo"; "they came from humble beginnings"). Pure Java: the
 * same font and logo draw the block sign here, the screens' picture (planet/AdArt) and the previews (tools/BlockIso, tools/AdArtDump).
 * On the HQ podium's front face (the tower frame's z = -podiumHalf, toward the gate plaza), flush with the facade (CHOSEN: nothing
 * projects over the plaza):
 *  - the name, "COBBLE & SONS", in a 5 x 7 block font, lit (sea lantern) letters on a dark band, centred over the main doors;
 *  - the logo over it: the company's first product, a cobblestone, inside a gate ring with 8 light nodes (the great gate's);
 *  - two video screens SCREEN_W x SCREEN_H (32 x 18 m, 16:9), one each side of the doors, each with its controller block at its lowest
 *    -x corner (world/Screens draws the picture; electric signs: UL 48, STATED as the US listing standard).
 * Only the city's HQ gets it (planet/Metro).
 */
public final class Frontage {
    public static final String NAME = "COBBLE & SONS";
    public static final int SCREEN_W = 32, SCREEN_H = 18, SCREEN_X0 = 44, SCREEN_Y0 = 4, BAND_Y0 = 4, BAND_Y1 = 12, LOGO = 11, LOGO_Y0 = 13;
    public static final String LIT = "sea_lantern", BACK = "black_concrete", PANEL = "black_concrete", CONTROLLER = "hopelessinfinity:ad_screen[facing=north]";
    // ---- the 5 x 7 font: 7 rows of 5 bits each, top row first
    static final java.util.Map<Character, int[]> FONT = new java.util.HashMap<>();
    static void g(char c, int... rows) { FONT.put(c, rows); }
    static {
        g('A', 0b01110, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001); g('B', 0b11110, 0b10001, 0b10001, 0b11110, 0b10001, 0b10001, 0b11110);
        g('C', 0b01110, 0b10001, 0b10000, 0b10000, 0b10000, 0b10001, 0b01110); g('D', 0b11110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b11110);
        g('E', 0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b11111); g('F', 0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b10000);
        g('G', 0b01110, 0b10001, 0b10000, 0b10111, 0b10001, 0b10001, 0b01111); g('H', 0b10001, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001);
        g('I', 0b01110, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110); g('J', 0b00111, 0b00010, 0b00010, 0b00010, 0b00010, 0b10010, 0b01100);
        g('K', 0b10001, 0b10010, 0b10100, 0b11000, 0b10100, 0b10010, 0b10001); g('L', 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b11111);
        g('M', 0b10001, 0b11011, 0b10101, 0b10101, 0b10001, 0b10001, 0b10001); g('N', 0b10001, 0b10001, 0b11001, 0b10101, 0b10011, 0b10001, 0b10001);
        g('O', 0b01110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110); g('P', 0b11110, 0b10001, 0b10001, 0b11110, 0b10000, 0b10000, 0b10000);
        g('Q', 0b01110, 0b10001, 0b10001, 0b10001, 0b10101, 0b10010, 0b01101); g('R', 0b11110, 0b10001, 0b10001, 0b11110, 0b10100, 0b10010, 0b10001);
        g('S', 0b01111, 0b10000, 0b10000, 0b01110, 0b00001, 0b00001, 0b11110); g('T', 0b11111, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100);
        g('U', 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110); g('V', 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01010, 0b00100);
        g('W', 0b10001, 0b10001, 0b10001, 0b10101, 0b10101, 0b10101, 0b01010); g('X', 0b10001, 0b10001, 0b01010, 0b00100, 0b01010, 0b10001, 0b10001);
        g('Y', 0b10001, 0b10001, 0b01010, 0b00100, 0b00100, 0b00100, 0b00100); g('Z', 0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b10000, 0b11111);
        g('0', 0b01110, 0b10001, 0b10011, 0b10101, 0b11001, 0b10001, 0b01110); g('1', 0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110);
        g('2', 0b01110, 0b10001, 0b00001, 0b00010, 0b00100, 0b01000, 0b11111); g('3', 0b11110, 0b00001, 0b00001, 0b01110, 0b00001, 0b00001, 0b11110);
        g('4', 0b00010, 0b00110, 0b01010, 0b10010, 0b11111, 0b00010, 0b00010); g('5', 0b11111, 0b10000, 0b11110, 0b00001, 0b00001, 0b10001, 0b01110);
        g('6', 0b00110, 0b01000, 0b10000, 0b11110, 0b10001, 0b10001, 0b01110); g('7', 0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b01000, 0b01000);
        g('8', 0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b01110); g('9', 0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00010, 0b01100);
        g('&', 0b01100, 0b10010, 0b10100, 0b01000, 0b10101, 0b10010, 0b01101); g('.', 0, 0, 0, 0, 0, 0b01100, 0b01100); g(',', 0, 0, 0, 0, 0b01100, 0b00100, 0b01000);
        g('-', 0, 0, 0, 0b11111, 0, 0, 0); g('!', 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0, 0b00100); g('\'', 0b00100, 0b00100, 0b01000, 0, 0, 0, 0); g(' ', 0, 0, 0, 0, 0, 0, 0); g('?', 0b01110, 0b10001, 0b00001, 0b00010, 0b00100, 0, 0b00100);
    }
    /** Is pixel (col, row) of character c lit (col 0..4 from the left, row 0..6 from the top)? */
    public static boolean pix(char c, int col, int row) { int[] r = FONT.get(Character.toUpperCase(c)); if (r == null || col < 0 || col > 4 || row < 0 || row > 6) return false; return (r[row] >> (4 - col) & 1) != 0; }
    /** A text's width in font pixels (6 a character, the last without its gap). */
    public static int width(String s) { return Math.max(0, 6 * s.length() - 1); }
    public static boolean textPix(String s, int x, int row) { if (x < 0) return false; int c = x / 6, col = x % 6; return c < s.length() && col < 5 && pix(s.charAt(c), col, row); }

    // ---- the logo: u, v in 0..1 (v up). 0 none, 1 the ring, 2 a light node, 3-5 the cobblestone's top, left and right faces, 6 its cracks
    public static int logo(double u, double v) {
        double x = u - 0.5, y = v - 0.5, r = Math.hypot(x, y);
        for (int k = 0; k < 8; k++) { double a = Math.toRadians(22.5 + 45 * k); if (Math.hypot(x - 0.43 * Math.cos(a), y - 0.43 * Math.sin(a)) < 0.045) return 2; }
        if (r >= 0.37 && r <= 0.49) return 1;
        double R = 0.25, c30 = Math.cos(Math.toRadians(30));   // the cube (isometric hexagon) round the centre
        double[] top = face(x, y, new double[]{R * c30, R / 2}, null, new double[]{-R * c30, R / 2}), lf = face(x, y, new double[]{-R * c30, R / 2}, null, new double[]{0, -R}), rt = face(x, y, new double[]{R * c30, R / 2}, null, new double[]{0, -R});
        double[] f = top != null ? top : lf != null ? lf : rt; if (f == null) return 0;
        if (crack(f[0], f[1], top != null ? 1 : lf != null ? 2 : 3)) return 6; return top != null ? 3 : lf != null ? 4 : 5; }
    /** Face coordinates (s, t in 0..1) of point (x, y) on the rhombus with the corner at the centre and edges to a (via b) and c, or null. */
    static double[] face(double x, double y, double[] a, double[] b, double[] c) {   // the rhombus 0, a, b = a + c', c: corner at the origin, sides along a and c (b is the far corner, unused)
        double det = a[0] * c[1] - a[1] * c[0]; double s = (x * c[1] - y * c[0]) / det, t = (a[0] * y - a[1] * x) / det; return s >= 0 && t >= 0 && s <= 1 && t <= 1 ? new double[]{s, t} : null; }
    /** Mortar lines between the cobbles: a jittered 3 x 3 cell pattern on each face (CHOSEN). */
    static boolean crack(double s, double t, int faceId) { double best = 9, second = 9;
        for (int i = -1; i <= 3; i++) for (int j = -1; j <= 3; j++) { double h1 = Math.abs(Math.sin(faceId * 12.9898 + i * 78.233 + j * 37.719) * 43758.5453) % 1, h2 = Math.abs(Math.sin(faceId * 39.346 + i * 11.135 + j * 83.155) * 24634.6345) % 1;
            double px = (i + 0.2 + 0.6 * h1) / 3, py = (j + 0.2 + 0.6 * h2) / 3, d = Math.hypot(s - px, t - py); if (d < best) { second = best; best = d; } else if (d < second) second = d; }
        return second - best < 0.045; }

    /** The HQ front's block at frame column lx, height y (on the podium's front face), or null (the tower's own facade). ph: podium half. */
    public static String at(int lx, int y, int ph) {
        // the screens (only where the podium is wide enough for both and a margin)
        if (ph >= SCREEN_X0 + SCREEN_W + 2) for (int side = -1; side <= 1; side += 2) { int x0 = side > 0 ? SCREEN_X0 : -SCREEN_X0 - SCREEN_W + 1; if (lx >= x0 && lx < x0 + SCREEN_W && y >= SCREEN_Y0 && y < SCREEN_Y0 + SCREEN_H) return lx == x0 && y == SCREEN_Y0 ? CONTROLLER : PANEL; }
        // the logo
        int lh = LOGO / 2; if (Math.abs(lx) <= lh && y >= LOGO_Y0 && y < LOGO_Y0 + LOGO) { int k = logo((lx + lh + 0.5) / LOGO, (y - LOGO_Y0 + 0.5) / LOGO);
            return switch (k) { case 1 -> "hopelessinfinity:gate_frame"; case 2 -> LIT; case 3 -> "cobblestone"; case 4 -> "stone"; case 5 -> "andesite"; case 6 -> "gray_concrete"; default -> BACK; }; }
        // the name on its band
        int w = width(NAME), x0 = -w / 2; if (y >= BAND_Y0 && y <= BAND_Y1 && Math.abs(lx) <= w / 2 + 2) { int row = BAND_Y1 - 1 - y; return row >= 0 && row <= 6 && textPix(NAME, lx - x0, row) ? LIT : BACK; }
        return null; }
}
