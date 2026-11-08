import com.hopeless.infinity.planet.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Renders the start planet from the mod's own Column/Terrain code: the six faces as laid out in the world, and a lit globe. Args: seed outDir. */
public class PlanetRender {
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]); File out = new File(a[1]); out.mkdirs(); PlanetWorld w = new PlanetWorld(seed); CubeSphere c = w.cube;
        int S = 300, pad = 20; double step = c.F / (double) S;
        // cross layout of the net for viewing: north pole above face 0, south pole below
        int[][] at = {{1, 1}, {2, 1}, {3, 1}, {0, 1}, {1, 0}, {1, 2}};   // face -> (col,row): 0 +Y centre, east to the right
        BufferedImage net = new BufferedImage(4 * S + 2 * pad, 3 * S + 2 * pad + 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = net.createGraphics(); g.setColor(new Color(10, 12, 20)); g.fillRect(0, 0, net.getWidth(), net.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (int f = 0; f < 6; f++) for (int i = 0; i < S; i++) for (int j = 0; j < S; j++) {
            int x = (int) (c.faceX0(f) + (i + 0.5) * step), z = (int) (c.faceZ0() + (j + 0.5) * step);
            Column col = Column.of(w, f, x, z), ce = Column.of(w, f, Math.min(x + (int) step, c.faceX0(f) + c.F - 1), z);
            net.setRGB(pad + at[f][0] * S + i, pad + 60 + at[f][1] * S + j, shade(col, ce.surfaceY - col.surfaceY, step)); }
        g.setColor(Color.WHITE); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g.drawString("Hopeless Infinity - start planet, six cube faces (seed " + seed + ")", pad, 30);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13)); g.drawString(w.describe(), pad, 52);
        for (int f = 0; f < 6; f++) { g.setColor(new Color(255, 255, 255, 140)); g.drawRect(pad + at[f][0] * S, pad + 60 + at[f][1] * S, S - 1, S - 1); g.drawString("face " + f + " " + CubeSphere.NAME[f], pad + at[f][0] * S + 6, pad + 60 + at[f][1] * S + 16); }
        int[] spw = w.spawn(); int sx = pad + at[0][0] * S + (int) ((spw[0] - c.faceX0(0)) / step), sy = pad + 60 + at[0][1] * S + (int) ((spw[2] - c.faceZ0()) / step); g.setColor(new Color(90, 255, 140)); g.setStroke(new BasicStroke(2)); g.drawOval(sx - 7, sy - 7, 14, 14); g.drawString("spawn", sx + 10, sy + 5);
        g.dispose(); ImageIO.write(net, "png", new File(out, "planet_faces.png"));
        // globe: orthographic view, sun from the upper left, looking at face 0 tilted 25 deg toward the north
        int G = 900; BufferedImage gl = new BufferedImage(G, G, BufferedImage.TYPE_INT_RGB); double tilt = Math.toRadians(-25), yaw = Math.toRadians(-30);
        double[] sun = norm(new double[]{-0.5, 0.6, 0.62});
        for (int py = 0; py < G; py++) for (int px = 0; px < G; px++) {
            double X = (px - G / 2.0) / (G * 0.45), Y = -(py - G / 2.0) / (G * 0.45), r2 = X * X + Y * Y; if (r2 > 1) { gl.setRGB(px, py, 0x05060c); continue; }
            double Zv = Math.sqrt(1 - r2); // view space: x right, y up, z toward viewer
            double[] v = {X, Y, Zv};
            double cy = Math.cos(tilt), sy2 = Math.sin(tilt); double[] t1 = {v[0], v[1] * cy - v[2] * sy2, v[1] * sy2 + v[2] * cy};   // tilt
            double cyw = Math.cos(yaw), syw = Math.sin(yaw); double[] t2 = {t1[0] * cyw + t1[2] * syw, t1[1], -t1[0] * syw + t1[2] * cyw};
            double[] planetP = {-t2[0], t2[2], t2[1]};   // view -> planet: +z view (toward us) = planet +Y (face 0), up = planet +Z (north), right = east (-X)
            int f = face(planetP); double[] cp = toCube(planetP, f);
            double u = dot(cp, CubeSphere.A[f]), vv = dot(cp, CubeSphere.B[f]);
            int bx = (int) c.x(f, invNowellApprox(u)), bz = (int) c.z(invNowellApprox(vv));
            Column col = Column.of(w, f, Math.min(Math.max(bx, c.faceX0(f)), c.faceX0(f) + c.F - 1), Math.min(Math.max(bz, c.faceZ0()), c.faceZ0() + c.F - 1));
            int rgb = shade(col, 0, 1); double l = Math.max(0.12, dot(new double[]{X, Y, Zv}, sun));
            gl.setRGB(px, py, mul(rgb, l)); }
        Graphics2D g2 = gl.createGraphics(); g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON); g2.setColor(Color.WHITE); g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g2.drawString("Start planet from orbit (rendered from the mod's terrain code)", 20, 32); g2.dispose();
        ImageIO.write(gl, "png", new File(out, "planet_globe.png"));
        // the ground round the spawn, 4 km square at 2 m per pixel, lit from the north-west: what the player walks on (real metres)
        int M = 2000; double mpp = 2; BufferedImage area = new BufferedImage(M, M + 70, BufferedImage.TYPE_INT_RGB); int[] hgt = new int[M * M]; Column[] cs = new Column[M * M];
        for (int j = 0; j < M; j++) for (int i = 0; i < M; i++) { Column col = Column.of(w, 0, spw[0] + (int) ((i - M / 2) * mpp), spw[2] + (int) ((j - M / 2) * mpp)); cs[j * M + i] = col; hgt[j * M + i] = col.surfaceY; }
        for (int j = 0; j < M; j++) for (int i = 0; i < M; i++) { int k = j * M + i; double dx = (hgt[Math.min(M - 1, i + 1) + j * M] - hgt[Math.max(0, i - 1) + j * M]) / (2 * mpp), dz = (hgt[i + Math.min(M - 1, j + 1) * M] - hgt[i + Math.max(0, j - 1) * M]) / (2 * mpp);
            double nl = Math.sqrt(dx * dx + dz * dz + 1), lit = Math.max(0.25, (-dx * -0.6 + -dz * -0.6 + 1) / nl / 1.3); int rgb = shade(cs[k], 0, 1); area.setRGB(i, j, cs[k].surfaceY < Terrain.SEA ? rgb : mul(rgb, lit)); }
        Graphics2D g3 = area.createGraphics(); g3.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON); g3.setColor(new Color(90, 255, 140)); g3.setStroke(new BasicStroke(2)); g3.drawOval(M / 2 - 8, M / 2 - 8, 16, 16);
        g3.setColor(Color.WHITE); g3.setStroke(new BasicStroke(3)); g3.drawLine(30, M - 30, 30 + (int) (500 / mpp), M - 30); g3.drawString("500 m", 30, M - 40);
        g3.setColor(new Color(10, 12, 20)); g3.fillRect(0, M, M, 70); g3.setColor(Color.WHITE); g3.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE; for (int v : hgt) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        g3.drawString(String.format("Ground round the spawn (green ring at %d, %d, face 0): 4 km x 4 km, 1 block = 1 m, heights %d..%d m. Rendered from the mod's Column code.", spw[0], spw[2], lo, hi), 20, M + 42);
        g3.dispose(); ImageIO.write(area, "png", new File(out, "spawn_area.png"));
        System.out.println("rendered");
    }
    /** Inverse of Nowell's map is not closed-form; normalising gives the face coordinate to within a block or two at preview size. */
    static double invNowellApprox(double u) { return Math.max(-1, Math.min(1, u)); }
    static int face(double[] p) { int best = 0; double bv = -9; for (int f = 0; f < 6; f++) { double d = dot(p, CubeSphere.N[f]); if (d > bv) { bv = d; best = f; } } return best; }
    static double[] toCube(double[] p, int f) { double k = 1 / dot(p, CubeSphere.N[f]); return new double[]{p[0] * k, p[1] * k, p[2] * k}; }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double[] norm(double[] a) { double l = Math.sqrt(dot(a, a)); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }
    static int mul(int rgb, double k) { int r = (int) Math.min(255, ((rgb >> 16) & 255) * k), g = (int) Math.min(255, ((rgb >> 8) & 255) * k), b = (int) Math.min(255, (rgb & 255) * k); return r << 16 | g << 8 | b; }
    /** Map colour: water by depth, land by top block and biome, hill shading from the east-west slope. */
    static int shade(Column c, int dh, double step) {
        if (c.voidCol) return 0;
        int y = c.surfaceY; int rgb;
        if (y < Terrain.SEA) { if (c.ice) return 0xd8e8f4; double d = Math.min(1, (Terrain.SEA - y) / 800.0); rgb = lerp(0x3b8fc4, 0x0b2350, d); return rgb; }
        rgb = switch (c.top) { case SAND -> 0xdacb91; case RED_SAND, TERRACOTTA -> 0xb8653a; case STONE -> 0x8a8a8a; case SNOW_BLOCK -> 0xf4f7fa; case PODZOL -> 0x6a5232; default -> switch (c.biome) {
            case JUNGLE, SPARSE_JUNGLE -> 0x2e8a1e; case DARK_FOREST -> 0x2d5a1c; case FOREST, FLOWER_FOREST, BIRCH_FOREST -> 0x4f8f32; case TAIGA, OLD_GROWTH_SPRUCE_TAIGA -> 0x47704a; case SAVANNA -> 0xa9a35a;
            case SWAMP -> 0x4c6a3a; case MEADOW -> 0x7fb35a; case WINDSWEPT_HILLS -> 0x7d8f6a; default -> 0x77b04c; }; };
        if (c.snowLayer) rgb = lerp(rgb, 0xffffff, 0.75);
        double k = 1 + Math.max(-0.35, Math.min(0.35, -dh / step * 0.6)); return mul(rgb, k);
    }
    static int lerp(int a, int b, double t) { int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t); return r << 16 | g << 8 | bl; }
}
