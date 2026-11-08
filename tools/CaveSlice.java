import com.hopeless.infinity.planet.*; import java.awt.image.BufferedImage; import java.awt.*;
/** CaveSlice seed out.png: plan and side cuts of real cave columns (planet/Column + planet/Caves, as PlanetGenerator.caves carves them). Saved tool. */
public class CaveSlice {
  static int col(Column.Mat m) { switch (m) { case GRASS: return 0x5a8a3a; case DIRT: return 0x7a5a3a; case STONE: return 0x7d7d7d; case DEEPSLATE: return 0x4a4a50; case SAND: return 0xd8cc90; case WATER: return 0x3060c0; case REGOLITH: return 0x8a8a8a; case MARE_REGOLITH: return 0x444448; case BASALT: return 0x3a3a3e; case ANORTHOSITE: return 0xb8b8b8; default: return 0x909090; } }
  public static void main(String[] a) throws Exception {
    PlanetWorld w = new PlanetWorld(1); Caves cv = w.caves(); int f = 0, X0 = 0, Z0 = 0; boolean found = false;
    search: for (int r = 0; r < 3000000; r += 5000) for (int k = 0; k < 16; k++) { int x = (int) (r * Math.cos(k * 0.39)), z = (int) (r * Math.sin(k * 0.39)); Column c = Column.of(w, f, x, z); if (c.voidCol || c.surfaceY <= 5) continue;
      double[] s = CubeSphere.sphere(CubeSphere.cube(f, w.cube.u(f, x + 0.5), w.cube.v(z + 0.5))); if (cv.karst(s, w.terrain.wetness(s))) { X0 = x; Z0 = z; found = true; break search; } }
    System.out.println("karst at face 0 " + X0 + "," + Z0 + " found " + found);
    int W = 600, H = 300; BufferedImage img = new BufferedImage(W * 2 + 20, H + 40 + W, BufferedImage.TYPE_INT_RGB); Graphics2D g = img.createGraphics(); g.setColor(new Color(0x10141c)); g.fillRect(0, 0, img.getWidth(), img.getHeight()); g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.PLAIN, 13));
    // side cut: 600 m along x, from 280 m under to 20 m over the ground
    int[] surf = new int[W]; for (int i = 0; i < W; i++) surf[i] = Column.of(w, f, X0 + i, Z0).surfaceY;
    int yTop = surf[W / 2] + 20; long open = 0;
    for (int i = 0; i < W; i++) { Column c = Column.of(w, f, X0 + i, Z0); for (int j = 0; j < H; j++) { int y = yTop - j; int rgb;
        if (y > c.surfaceY) rgb = y <= 0 ? 0x3060c0 : 0x9ab8d8; else { boolean v = cv.karstVoid(f, X0 + i, y, Z0, c.surfaceY); if (v) { rgb = y <= 0 ? 0x2a50a0 : 0x15151a; open++; } else rgb = col(c.at(y, -2560)); }
        img.setRGB(i, 30 + j, rgb); } }
    g.drawString(String.format("side cut, 600 m x 300 m, karst at face 0 x %d z %d (black: open cave, blue: flooded below the water table)", X0, Z0), 4, 18);
    // plan: 600 m x 600 m at 60 m under the ground
    for (int i = 0; i < W; i++) for (int k = 0; k < W; k++) { Column c = Column.of(w, f, X0 + i, Z0 + k); int top = -1; for (int y = c.surfaceY; y >= c.surfaceY - 250; y -= 2) if (cv.karstVoid(f, X0 + i, y, Z0 + k, c.surfaceY)) { top = c.surfaceY - y; break; }
      img.setRGB(i, 40 + H + k, top < 0 ? 0x7d7d7d : top < 7 ? 0xffffff : Color.HSBtoRGB(0.6f - 0.6f * top / 250f, 0.8f, 0.9f)); }
    g.drawString("cave map, 600 m x 600 m: the highest open block in each column (colour: depth, red shallow .. blue 250 m; white: open to the sky)", 4, 36 + H);
    // the moon: a lava tube, side cut across it and plan
    PlanetWorld m = w.moon(); Caves cm = m.caves(); int tx = -1; for (int x = 0; x < 400000; x += 5) { int[] t = cm.tube(0, x, 0, 0, true); if (t != null && t[1] < 0) { tx = x; break; } }
    int mx0 = tx - 150; for (int i = 0; i < W; i++) { int x = mx0 + i / 2; for (int j = 0; j < H; j++) { int y = 20 - j; int[] t = cm.tube(0, x, 0, 0, true); int rgb = y > 0 ? 0x050505 : (t != null && y >= t[0] && y <= t[1]) ? 0x15151a + 0x202020 * 0 : 0x444448; if (rgb == 0x15151a) rgb = 0xd0a040; img.setRGB(W + 20 + i, 30 + j, rgb); } }
    g.drawString("moon: across a lava tube under flat mare, 300 m x 150 m (gold: open)", W + 24, 18);
    for (int i = 0; i < W; i++) for (int k = 0; k < W; k++) { int x = tx - 3000 + i * 10, z = -3000 + k * 10; int[] t = cm.tube(0, x, z, 0, true); img.setRGB(W + 20 + i, 40 + H + k, t == null ? 0x444448 : t[1] >= 0 ? 0xffffff : 0xd0a040); }
    g.drawString("moon plan, 6 km x 6 km (gold: tube, white: skylight)", W + 24, 36 + H);
    javax.imageio.ImageIO.write(img, "png", new java.io.File(a[0])); System.out.println("open blocks in the side cut " + open + "; tube at x " + tx); } }
