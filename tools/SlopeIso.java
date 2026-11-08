import com.hopeless.infinity.planet.*;
import com.hopeless.infinity.slope.SlopeMesh;
import com.hopeless.infinity.slope.Slopes;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Isometric view of real terrain, drawn from the mod's own Column plans and the slope geometry (SlopeMesh faces of the pieces Slopes.choose
 * picks): the same ground with full blocks only, and with terrain slopes. Args: seed outDir [x z]. Saved tool.
 */
public class SlopeIso {
    static final int N0 = 56;
    record Quad(double depth, double[][] p, Color c) {}
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]); File out = new File(a[1]); out.mkdirs(); PlanetWorld w = new PlanetWorld(seed);
        int N = 56; int[] at = a.length > 3 ? new int[]{Integer.parseInt(a[2]), Integer.parseInt(a[3])} : hilly(w);
        Column[][] col = new Column[N + 2][N + 2]; double minY = 1e9;
        for (int i = 0; i < N + 2; i++) for (int k = 0; k < N + 2; k++) { col[i][k] = Column.of(w, 0, at[0] + i - 1, at[1] + k - 1); minY = Math.min(minY, col[i][k].surfaceY); }
        int base = (int) minY - 2;
        BufferedImage img = new BufferedImage(2400, 1300, BufferedImage.TYPE_INT_RGB); Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(150, 190, 230)); g.fillRect(0, 0, 2400, 1300);
        for (int view = 0; view < 2; view++) {
            List<Quad> qs = new ArrayList<>(); int placed = 0;
            for (int i = 1; i <= N; i++) for (int k = 1; k <= N; k++) {
                Column c = col[i][k]; int top = c.surfaceY; Color mc = color(c); int[] yc = null;
                if (view == 1 && c.surfaceY >= Terrain.SEA) { double[] s9 = new double[9];
                    for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) { Column n = col[i + dx][k + dz]; s9[(dz + 1) * 3 + dx + 1] = n.surfaceY < Terrain.SEA ? Double.NaN : n.groundTop; }
                    yc = Slopes.choose(top, s9); }
                int fullTop = yc != null && yc[0] == top ? top - 1 : top;   // a piece at the top block's place replaces it
                for (int y = base; y <= fullTop; y++) box(qs, i, y, k, y == top ? mc : dirt(c), y == fullTop);
                if (c.surfaceY < Terrain.SEA) { water(qs, i, Terrain.SEA, k); continue; }
                if (view == 1) {
                    if (yc != null) { placed++;
                        int[] h = SlopeMesh.decode(yc[1], Slopes.L);
                        for (SlopeMesh.Face f : SlopeMesh.world(h, Slopes.L, SlopeMesh.autoSplit(h), SlopeMesh.DOWN)) { if (f.mat == SlopeMesh.FLOOR) continue;
                            double[][] p = new double[4][]; for (int v = 0; v < 4; v++) p[v] = new double[]{i + f.v[v][0], yc[0] + f.v[v][1], k + f.v[v][2]};
                            double lit = 0.55 + 0.45 * Math.max(0, f.n[1] * 0.8 + f.n[0] * -0.35 + f.n[2] * 0.45); Color fc = f.mat == SlopeMesh.TOP ? mc : dirt(c);
                            qs.add(new Quad(depth(p), p, shade(fc, lit))); } } } }
            qs.sort((x, y) -> Double.compare(x.depth, y.depth));
            int ox = view == 0 ? 600 : 1800, oy = 620 + (int) ((meanTop(col, N) - base) * 15 * 0.6);
            for (Quad q : qs) { Path2D path = new Path2D.Double(); for (int v = 0; v < 4; v++) { double[] s = proj(q.p[v], base); if (v == 0) path.moveTo(ox + s[0], oy + s[1]); else path.lineTo(ox + s[0], oy + s[1]); } path.closePath();
                g.setColor(q.c); g.fill(path); g.setColor(new Color(0, 0, 0, 28)); g.draw(path); }
            g.setColor(Color.BLACK); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26)); g.drawString(view == 0 ? "Full blocks only (0.2.0)" : "With Hopeless Deep's terrain slopes (0.3.0): " + placed + " pieces", ox - 420, 60);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18)); g.drawString(String.format("Real terrain of the start planet, face 0 at %d, %d (%d x %d blocks, 1 block = 1 m). Drawn from the mod's Column plans and SlopeMesh geometry.", at[0], at[1], N, N), 30, 1270);
        g.dispose(); ImageIO.write(img, "png", new File(out, "slopes_before_after.png")); System.out.println("rendered at " + at[0] + "," + at[1]);
    }
    /** A rolling hillside near the spawn: the first spot with 6-25 m of relief over 56 blocks, all dry. */
    static int[] hilly(PlanetWorld w) { int[] sp = w.spawn();
        for (int r = 0; r < 2000000; r += 3000) for (int t = 0; t < 36; t++) { int x = sp[0] + (int) (r * Math.cos(t * Math.PI / 18)), z = sp[2] + (int) (r * Math.sin(t * Math.PI / 18));
            int lo = 99999, hi = -99999; boolean wet = false; for (int i = 0; i < 56; i += 4) for (int k = 0; k < 56; k += 4) { Column c = Column.of(w, 0, x + i, z + k); lo = Math.min(lo, c.surfaceY); hi = Math.max(hi, c.surfaceY); if (c.surfaceY < Terrain.SEA) wet = true; }
            if (!wet && hi - lo >= 12 && hi - lo <= 40) return new int[]{x, z}; }
        return new int[]{sp[0], sp[2]}; }
    static double meanTop(Column[][] col, int N) { double m = 0; for (int i = 1; i <= N; i++) for (int k = 1; k <= N; k++) m += col[i][k].surfaceY; return m / (N * N); }
    static double[] proj(double[] p, int base) { double x = p[0], y = p[1] - base, z = p[2]; double s = 15; return new double[]{(x - z) * s * 0.866, (x + z) * s * 0.5 - y * s * 1.0 - N0 * s * 0.5}; }
    static double depth(double[][] p) { double d = 0; for (double[] v : p) d += v[0] + v[2] + v[1] * 0.01; return d / 4; }
    static void box(List<Quad> qs, int x, int y, int z, Color c, boolean top) {
        if (top) qs.add(new Quad(x + z + 1 + y * 0.01, new double[][]{{x, y + 1, z}, {x + 1, y + 1, z}, {x + 1, y + 1, z + 1}, {x, y + 1, z + 1}}, shade(c, 0.91)));   // same light as a flat slope top
        qs.add(new Quad(x + z + 1.5 + y * 0.01 - 0.6, new double[][]{{x, y, z + 1}, {x + 1, y, z + 1}, {x + 1, y + 1, z + 1}, {x, y + 1, z + 1}}, shade(c, 0.78)));   // south face
        qs.add(new Quad(x + z + 1.5 + y * 0.01 - 0.6, new double[][]{{x + 1, y, z}, {x + 1, y, z + 1}, {x + 1, y + 1, z + 1}, {x + 1, y + 1, z}}, shade(c, 0.62))); } // east face
    static void water(List<Quad> qs, int x, int y, int z) { qs.add(new Quad(x + z + 1 + y * 0.01, new double[][]{{x, y + 0.9, z}, {x + 1, y + 0.9, z}, {x + 1, y + 0.9, z + 1}, {x, y + 0.9, z + 1}}, new Color(50, 110, 200))); }
    static Color color(Column c) { return switch (c.top) { case SAND -> new Color(220, 205, 150); case STONE -> new Color(130, 130, 130); case SNOW_BLOCK -> new Color(245, 248, 252); case RED_SAND -> new Color(190, 105, 60); case PODZOL -> new Color(110, 80, 45);
        default -> c.biome.key.contains("jungle") ? new Color(70, 150, 40) : new Color(110, 165, 70); }; }
    static Color dirt(Column c) { return c.top == Column.Mat.SAND ? new Color(200, 185, 130) : c.top == Column.Mat.STONE ? new Color(115, 115, 115) : new Color(130, 95, 60); }
    static Color shade(Color c, double k) { return new Color(Math.min(255, (int) (c.getRed() * k)), Math.min(255, (int) (c.getGreen() * k)), Math.min(255, (int) (c.getBlue() * k))); }
}
