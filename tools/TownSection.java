import com.hopeless.infinity.planet.*; import java.awt.*; import java.awt.image.BufferedImage; import java.util.*;
/** TownSection seed out-prefix [site name]: a side section along Main Street and across the town (from Column.of as the chunk generator lays it):
 *  natural ground (thin line) and the graded ground the town gets (filled), vertical scale x5. Saved tool. */
public class TownSection {
  public static void main(String[] a) throws Exception { PlanetWorld w = new PlanetWorld(Long.parseLong(a[0])); Sites st = w.sites(); Towns tw = w.towns(); double R = w.cube.R;
    Sites.Site s = null; for (Sites.Site x : st.all) if (a.length > 2 ? x.name.equals(a[2]) : x.kind == Sites.Kind.RURAL_TOWN && x.pop > 1800 && x.pop < 2500) { s = x; break; }
    Towns.Plan p = tw.plan(s); System.out.printf("%s: %d blocks (%d core), steepest street %.1f %%, cut up to %.1f m, fill up to %.1f m%n", s, p.blocksUsed, p.coreBlocks, p.maxGrade * 100, p.maxCut, p.maxFill);
    int L = (int) p.extentM; int W = 1800, H = 760; BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB); Graphics2D g = img.createGraphics(); g.setColor(new Color(0xf4f2ee)); g.fillRect(0, 0, W, H);
    for (int pass = 0; pass < 2; pass++) { int y0 = pass == 0 ? 40 : 400; double[] nat = new double[2 * L + 1], grd = new double[2 * L + 1]; double lo = 1e9, hi = -1e9;
      for (int k = -L; k <= L; k++) { double u = pass == 0 ? k : Towns.BLOCK_U / 2 + Towns.STREET_W / 2, v = pass == 0 ? 0.1 : k; double x = p.alongX ? p.cx + u : p.cx + v, z = p.alongX ? p.cz + v : p.cz + u;
        double[] sp = w.cube.spherePoint(p.face, x, z); nat[k + L] = Terrain.toYd(w.terrain.heightAndWater(sp, R)[0]); Column c = Column.of(w, p.face, (int) Math.floor(x), (int) Math.floor(z)); grd[k + L] = c.surfaceY; lo = Math.min(lo, Math.min(nat[k + L], grd[k + L])); hi = Math.max(hi, Math.max(nat[k + L], grd[k + L])); }
      double sx = (W - 80) / (double) (2 * L), sy = Math.min(5 * sx, 300 / Math.max(1, hi - lo)); g.setColor(new Color(0x8a7a5c));
      for (int k = 0; k < 2 * L; k++) { int px = 40 + (int) (k * sx), yy = y0 + 320 - (int) ((grd[k] - lo) * sy); g.fillRect(px, yy, Math.max(1, (int) Math.ceil(sx)), y0 + 330 - yy); }
      g.setColor(new Color(0x2a6a2a)); for (int k = 1; k < 2 * L; k++) g.drawLine(40 + (int) ((k - 1) * sx), y0 + 320 - (int) ((nat[k - 1] - lo) * sy), 40 + (int) (k * sx), y0 + 320 - (int) ((nat[k] - lo) * sy));
      g.setColor(Color.BLACK); g.setFont(new Font("SansSerif", Font.PLAIN, 14)); g.drawString((pass == 0 ? "along Main Street" : "across town, mid-block") + String.format("  (%d m; heights %.0f-%.0f m; vertical x%.1f)  brown: graded ground the chunks get, green line: natural ground", 2 * L, lo, hi, sy / sx), 40, y0 - 8); }
    g.drawString(String.format("%s: steepest street %.1f %%, largest cut %.1f m, fill %.1f m (core streets 5 %%, edge 12 %%; 2:1 slopes at the edge, IBC App. J)", s, p.maxGrade * 100, p.maxCut, p.maxFill), 40, H - 12);
    javax.imageio.ImageIO.write(img, "png", new java.io.File(a[1] + "-section.png")); System.out.println("wrote " + a[1] + "-section.png"); } }
