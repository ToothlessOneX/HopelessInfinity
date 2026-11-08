import com.hopeless.infinity.planet.*; import java.awt.image.BufferedImage; import java.awt.*; import java.awt.geom.*;
/** RiverMap seed out.png [zoom.png]: the home planet's rivers, lakes and salt flats on a world map from the real Rivers object (segments drawn by
 *  discharge), and a close view (heightAndWater per pixel, as the generator reads it) round the largest river's middle reach. Saved tool. */
public class RiverMap {
  static double[] ll(double[] s) { return new double[]{Math.atan2(s[1], s[0]), Math.asin(Math.max(-1, Math.min(1, s[2])))}; }
  public static void main(String[] a) throws Exception { PlanetWorld w = new PlanetWorld(Long.parseLong(a[0])); Rivers rv = w.terrain.rivers; int W = 1600, H = 800; double R = w.cube.R;
    BufferedImage img = new BufferedImage(W, H + 26, BufferedImage.TYPE_INT_RGB);
    for (int j = 0; j < H; j++) for (int i = 0; i < W; i++) { double lat = Math.PI / 2 - Math.PI * (j + 0.5) / H, lon = 2 * Math.PI * (i + 0.5) / W - Math.PI; double[] s = {Math.cos(lat) * Math.cos(lon), Math.cos(lat) * Math.sin(lon), Math.sin(lat)};
      double h = w.terrain.heightM(s, R); int c = rv.cellOf(s); int k = rv.kind[c]; int rgb;
      if (h <= 0 && k != 2 && k != 3) { double q = Math.min(1, -h / 5000); rgb = new Color((int) (30 - 20 * q), (int) (60 - 40 * q), (int) (110 - 50 * q)).getRGB(); }
      else if (k == 2) rgb = 0x3a8ad8; else if (k == 3) rgb = 0xf0ece0;
      else { double e = Math.min(1, Math.max(0, h / 4000)); int g = (int) (200 - 120 * e); rgb = new Color((int) (150 - 40 * e + 60 * e), g, (int) (110 - 20 * e + 80 * e)).getRGB(); }
      img.setRGB(i, 26 + j, rgb); }
    Graphics2D g = img.createGraphics(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g.setColor(new Color(0x3a8ad8));
    Rivers.Seg big = null;
    for (Rivers.Seg sg : rv.segs) { if (big == null || sg.qa > big.qa) big = sg; if (sg.qa < 300) continue; double[] p = ll(sg.pa), q = ll(sg.pb); if (Math.abs(p[0] - q[0]) > 3) continue;
      float wd = (float) Math.max(0.6, 0.5 * Math.log10(sg.qa / 100)); g.setStroke(new BasicStroke(wd, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.draw(new Line2D.Double((p[0] + Math.PI) / (2 * Math.PI) * W, 26 + (Math.PI / 2 - p[1]) / Math.PI * H, (q[0] + Math.PI) / (2 * Math.PI) * W, 26 + (Math.PI / 2 - q[1]) / Math.PI * H)); }
    g.setColor(new Color(0x10141c)); g.fillRect(0, 0, W, 26); g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.PLAIN, 13));
    g.drawString(String.format("Hopeless Infinity seed %s: rivers over 300 m3/s (line width by discharge), lakes (blue), salt flats (white). %d river cells, %d lake, %d salt flat; largest %.0f m3/s", a[0], rv.riverCells, rv.lakeCells, rv.dryCells, big.qa), 6, 18);
    javax.imageio.ImageIO.write(img, "png", new java.io.File(a[1]));
    if (a.length < 3) return;
    // close view: a mid-sized river (about 2000 m3/s) so the channel and its bends show; 12 km across, 20 m a pixel, shaded relief from heightAndWater
    Rivers.Seg mid = null; for (Rivers.Seg sg : rv.segs) if (sg.qa > 1500 && sg.qa < 3000 && w.terrain.heightM(sg.pa, R) > 30) { mid = sg; break; }
    if (mid == null) mid = big; double[] c = mid.pa; double[] e1 = norm(cross(c, new double[]{0, 0, 1})), e2 = cross(e1, c); int Z = 640; double px = 20; double[][] hh = new double[Z][Z]; double[][] ww = new double[Z][Z];
    for (int j = 0; j < Z; j++) for (int i = 0; i < Z; i++) { double x = (i - Z / 2) * px, y = (Z / 2 - j) * px; double[] s = norm(new double[]{c[0] + (e1[0] * x + e2[0] * y) / R, c[1] + (e1[1] * x + e2[1] * y) / R, c[2] + (e1[2] * x + e2[2] * y) / R});
      double[] hw = w.terrain.heightAndWater(s, R); hh[j][i] = hw[0]; ww[j][i] = !Double.isNaN(hw[1]) && hw[1] > hw[0] ? hw[1] : Double.NaN; }
    BufferedImage z = new BufferedImage(Z, Z + 26, BufferedImage.TYPE_INT_RGB); double lo = 1e9, hi = -1e9; for (double[] r : hh) for (double v : r) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
    for (int j = 0; j < Z; j++) for (int i = 0; i < Z; i++) { int rgb;
      if (!Double.isNaN(ww[j][i])) { double d = Math.min(1, (ww[j][i] - hh[j][i]) / 15); rgb = new Color((int) (60 - 40 * d), (int) (130 - 60 * d), (int) (200 - 60 * d)).getRGB(); }
      else if (hh[j][i] <= 0) rgb = 0x1a3a6e;
      else { double dx = hh[j][Math.min(Z - 1, i + 1)] - hh[j][Math.max(0, i - 1)], dy = hh[Math.max(0, j - 1)][i] - hh[Math.min(Z - 1, j + 1)][i]; double sh = Math.max(0.35, Math.min(1.25, 0.85 + (-dx + dy) / (4 * px) * 2)); double t = (hh[j][i] - lo) / Math.max(1, hi - lo);
        rgb = new Color(clamp((90 + 110 * t) * sh), clamp((150 + 40 * t) * sh), clamp((70 + 90 * t) * sh)).getRGB(); }
      z.setRGB(i, 26 + j, rgb); }
    Graphics2D gz = z.createGraphics(); gz.setColor(new Color(0x10141c)); gz.fillRect(0, 0, Z, 26); gz.setColor(Color.WHITE); gz.setFont(new Font("SansSerif", Font.PLAIN, 12));
    gz.drawString(String.format("close view %.1f km across, %.0f m3/s: width %.0f m, depth %.1f m (Terrain.heightAndWater)", Z * px / 1000, mid.qa, Rivers.width(mid.qa), Rivers.depth(mid.qa)), 6, 17);
    javax.imageio.ImageIO.write(z, "png", new java.io.File(a[2])); }
  static int clamp(double v) { return (int) Math.max(0, Math.min(255, v)); }
  static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
  static double[] norm(double[] a) { double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }
}
