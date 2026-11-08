import com.hopeless.infinity.planet.*; import java.awt.image.BufferedImage; import java.awt.*; import java.awt.geom.*; import java.util.*;
/** SiteMap seed world.png region.png: the home planet's settlement sites (planet/Sites) on a world map, and a 700 km region round the largest
 *  city with names, rivers (Rivers segments) and every site drawn at its spot. Saved tool. */
public class SiteMap {
  static final Map<Sites.Kind, Color> COL = new EnumMap<>(Map.of(Sites.Kind.RURAL_TOWN, new Color(0xf0e6c8), Sites.Kind.SMALL_CITY, new Color(0xffc861), Sites.Kind.SUBURB, new Color(0xff9a5c),
    Sites.Kind.LARGE_CITY, new Color(0xff5050), Sites.Kind.MEGACITY, new Color(0xff2bd0), Sites.Kind.INDUSTRIAL_TOWN, new Color(0x9aa0a8), Sites.Kind.SCIENCE_TOWN, new Color(0x5cf0ff), Sites.Kind.CAMPUS_TOWN, new Color(0x9b7bff)));
  static int land(double h, double t) { double e = Math.min(1, Math.max(0, h / 3000)); return new Color((int) (95 + 90 * e), (int) (135 + 40 * e - 20 * Math.max(0, -t) / 20), (int) (80 + 90 * e)).getRGB(); }
  public static void main(String[] a) throws Exception { PlanetWorld w = new PlanetWorld(Long.parseLong(a[0])); Sites st = w.sites(); Rivers rv = w.terrain.rivers; double R = w.cube.R;
    int W = 1800, H = 900; BufferedImage img = new BufferedImage(W, H + 28, BufferedImage.TYPE_INT_RGB);
    for (int j = 0; j < H; j++) for (int i = 0; i < W; i++) { double lat = Math.PI / 2 - Math.PI * (j + 0.5) / H, lon = 2 * Math.PI * (i + 0.5) / W - Math.PI; double[] s = {Math.cos(lat) * Math.cos(lon), Math.cos(lat) * Math.sin(lon), Math.sin(lat)};
      int c = rv.cellOf(s); float hb = st.habit[c]; int rgb; if (rv.kind[c] == 1) rgb = 0x0c1c38; else if (rv.kind[c] == 2) rgb = 0x2f6fb0; else { int g = (int) (40 + 60 * hb); rgb = new Color(g + 20, (int) (g + 30 * hb), g).getRGB(); } img.setRGB(i, 28 + j, rgb); }
    Graphics2D g = img.createGraphics(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    java.util.List<Sites.Site> order = new ArrayList<>(st.all); order.sort(Comparator.comparingLong(x -> x.pop));
    for (Sites.Site s : order) { double lon = Math.atan2(s.s[1], s.s[0]), lat = Math.asin(s.s[2]); double x = (lon + Math.PI) / (2 * Math.PI) * W, y = 28 + (Math.PI / 2 - lat) / Math.PI * H; double r = Math.max(0.7, 0.55 * Math.log10(s.pop) - 1.6);
      if (s.kind == Sites.Kind.RURAL_TOWN && s.pop < 2000) continue; g.setColor(COL.get(s.kind)); g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r)); }
    g.setColor(new Color(0x10141c)); g.fillRect(0, 0, W, 28); g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.PLAIN, 13));
    g.drawString(String.format("Hopeless Infinity seed %s settlement sites (stage 1): %,d places, %,d people. Land shaded by habitability. Towns under 2,000 not drawn.", a[0], st.all.size(), st.people), 6, 19);
    legend(g, W - 760, 50); javax.imageio.ImageIO.write(img, "png", new java.io.File(a[1]));
    // region: 700 km round the largest city, 1 px = 700 m, ground from the drainage grid's heights and the rivers' segments
    Sites.Site top = order.get(order.size() - 1); double[] c0 = top.s, e1 = norm(cross(c0, new double[]{0, 0, 1})), e2 = cross(c0, e1); int Z = 1000; double px = 700;
    BufferedImage z = new BufferedImage(Z, Z + 28, BufferedImage.TYPE_INT_RGB);
    for (int j = 0; j < Z; j++) for (int i = 0; i < Z; i++) { double[] s = at(c0, e1, e2, (i - Z / 2) * px / R, (Z / 2 - j) * px / R); double h = w.terrain.heightM(s, R); int c = rv.cellOf(s);
      z.setRGB(i, 28 + j, h < 0 ? 0x0c1c38 : rv.kind[c] == 2 && h < rv.fill[c] ? 0x2f6fb0 : land(h, w.terrain.temperatureC(s, h))); }
    Graphics2D gz = z.createGraphics(); gz.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); gz.setColor(new Color(0x4f9be8));
    for (Rivers.Seg sg : rv.segs) { if (sg.qa < 30 || st.distM(sg.pa, c0) > 520000) continue; double[] p = proj(sg.pa, c0, e1, e2, R, px, Z), q = proj(sg.pb, c0, e1, e2, R, px, Z);
      gz.setStroke(new BasicStroke((float) Math.max(0.8, 0.9 * Math.log10(sg.qa / 10)))); gz.draw(new Line2D.Double(p[0], p[1], q[0], q[1])); }
    java.util.List<Sites.Site> near = new ArrayList<>(); for (Sites.Site s : st.all) if (st.distM(s.s, c0) < 520000) near.add(s); near.sort(Comparator.comparingLong(x -> x.pop));
    for (Sites.Site s : near) { double[] p = proj(s.s, c0, e1, e2, R, px, Z); double r = s.kind == Sites.Kind.LARGE_CITY || s.kind == Sites.Kind.MEGACITY ? Math.max(3, s.urbanRadiusM() / px) : Math.max(1.6, 0.9 * Math.log10(s.pop) - 1.2);
      gz.setColor(COL.get(s.kind)); gz.fill(new Ellipse2D.Double(p[0] - r, p[1] - r, 2 * r, 2 * r)); }
    gz.setFont(new Font("SansSerif", Font.BOLD, 11)); for (Sites.Site s : near) { if (s.pop < 20000 && s.kind != Sites.Kind.SCIENCE_TOWN) continue; double[] p = proj(s.s, c0, e1, e2, R, px, Z);
      String t = s.name + (s.pop >= 1e6 ? String.format(" %.1fM", s.pop / 1e6) : String.format(" %,dk", s.pop / 1000)); gz.setColor(Color.BLACK); gz.drawString(t, (float) p[0] + 5, (float) p[1] - 3); gz.setColor(Color.WHITE); gz.drawString(t, (float) p[0] + 4, (float) p[1] - 4); }
    gz.setColor(new Color(0x10141c)); gz.fillRect(0, 0, Z, 28); gz.setColor(Color.WHITE); gz.setFont(new Font("SansSerif", Font.PLAIN, 13));
    gz.drawString(String.format("700 km round %s: %d places; cities drawn at their urban footprint (1,886 people/km2); rivers over 30 m3/s", top, near.size()), 6, 19);
    gz.setColor(Color.WHITE); gz.fillRect(20, Z + 28 - 30, (int) (100000 / px), 4); gz.drawString("100 km", 20, Z + 28 - 36); legend(gz, Z - 250, 50);
    javax.imageio.ImageIO.write(z, "png", new java.io.File(a[2])); }
  static void legend(Graphics2D g, int x, int y) { g.setFont(new Font("SansSerif", Font.PLAIN, 12)); int i = 0; boolean wide = x > 1000;
    for (Sites.Kind k : Sites.Kind.values()) { int xx = wide ? x + (i % 4) * 190 : x, yy = wide ? y + (i / 4) * 18 : y + i * 18; g.setColor(new Color(0, 0, 0, 150)); g.fillRect(xx - 4, yy - 12, 186, 18); g.setColor(COL.get(k)); g.fillOval(xx, yy - 9, 10, 10); g.setColor(Color.WHITE); g.drawString(k.name().toLowerCase().replace('_', ' '), xx + 16, yy); i++; } }
  static double[] at(double[] c, double[] e1, double[] e2, double x, double y) { return norm(new double[]{c[0] + e1[0] * x + e2[0] * y, c[1] + e1[1] * x + e2[1] * y, c[2] + e1[2] * x + e2[2] * y}); }
  static double[] proj(double[] s, double[] c, double[] e1, double[] e2, double R, double px, int Z) { double d = s[0] * c[0] + s[1] * c[1] + s[2] * c[2]; double x = (s[0] * e1[0] + s[1] * e1[1] + s[2] * e1[2]) / d, y = (s[0] * e2[0] + s[1] * e2[1] + s[2] * e2[2]) / d; return new double[]{Z / 2 + x * R / px, 28 + Z / 2 - y * R / px}; }
  static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
  static double[] norm(double[] a) { double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }
}
