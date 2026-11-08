import com.hopeless.infinity.planet.*; import java.awt.*; import java.awt.image.BufferedImage; import java.util.*;
/** MetroRoadsView: 0.27.7 a megacity and the roads into it, drawn from the real data: each 8 m cell from planet/Metro.at (tower lots,
 *  gate plaza, boulevard) and the terrain's water; every laid road ending at the city as its own samples (planet/Roads), by class; below,
 *  the boulevard's pad and the roads' end heights along it. Args: site name, out.png. Saved tool. */
public class MetroRoadsView {
  public static void main(String[] a) throws Exception { PlanetWorld w = new PlanetWorld(1); w.playerSpawn(); Metro m = w.metro(); Roads rd = w.roads(); double R = w.cube.R;
    Metro.Plan p = null; for (Sites.Site s : w.sites().all) if (Metro.isCity(s) && s.name.equals(a[0])) p = m.plan(s);
    double half = p.reachM + Metro.LEAD_M + 1500; int PW = 1000, PH = 1000, EH = 260; double mpp = 2 * half / PW;
    BufferedImage im = new BufferedImage(PW, PH + EH + 40, BufferedImage.TYPE_INT_RGB); Graphics2D g = im.createGraphics(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(new Color(0xeae6da)); g.fillRect(0, 0, PW, PH + EH + 40);
    for (int py = 0; py < PH; py++) for (int px = 0; px < PW; px++) { double u = -half + (px + 0.5) * mpp, v = half - (py + 0.5) * mpp; double[] xz = p.xz(u, v); double[] s = w.cube.spherePoint(p.face, xz[0], xz[1]); if (s == null) continue;
      int col; int[] at = m.at(p.face, (int) Math.floor(xz[0]), (int) Math.floor(xz[1]), s);
      if (at != null) col = at[1] == 1 ? 0x8a8f99 : at[1] == 2 ? 0xd9b45a : at[1] == 4 ? 0x6b6b6b : at[1] == 5 ? 0x6fa35a : 0x3b3b3b; else if ((px + py) % 3 == 0) { double[] hw = w.terrain.heightAndWater(s, R); col = !Double.isNaN(hw[1]) && hw[1] > hw[0] ? 0x7fb2d9 : 0xeae6da; } else col = -1;
      if (col == -1) col = im.getRGB(Math.max(0, px - 1), py) & 0xffffff; im.setRGB(px, py, col); }
    Color[] cc = {new Color(0xc0392b), new Color(0xe67e22), new Color(0x27ae60), new Color(0x8e44ad), new Color(0x2c3e50), new Color(0x6d4c2a)}; g.setStroke(new BasicStroke(3f));
    java.util.List<double[]> ends = new ArrayList<>();
    for (Roads.Edge e : rd.edges) { if (e.a != p.site.id && e.b != p.site.id) continue; Roads.Route ro = rd.route(e.id); g.setColor(cc[e.cls.ordinal()]); int lx = -1, ly = -1;
      for (int i = 0; i < ro.n(); i += 4) { double[] f = w.cube.locate(ro.p(i)); if ((int) f[0] != p.face) { lx = -1; continue; } double[] uv = p.uv(f[1], f[2]); int x = (int) ((uv[0] + half) / mpp), y = (int) ((half - uv[1]) / mpp); if (Math.abs(uv[0]) > half * 1.2 || Math.abs(uv[1]) > half * 1.2) { lx = -1; continue; } if (lx >= 0) g.drawLine(lx, ly, x, y); lx = x; ly = y; }
      int ie = e.a == p.site.id ? 0 : ro.n() - 1; double[] f = w.cube.locate(ro.p(ie)); double[] uv = p.uv(f[1], f[2]); ends.add(new double[]{uv[1], Terrain.toYd(ro.z[ie]) + 1, e.cls.ordinal()}); }
    g.setColor(Color.black); g.setFont(new Font("SansSerif", Font.BOLD, 15)); g.drawString(p.site.name + ": roads end at the boulevard's two ends (Interstate red, arterial orange, collector green); towers light grey, boulevard black, avenues dark grey, parks green, gate plaza gold", 10, 20);
    g.setFont(new Font("SansSerif", Font.PLAIN, 12)); g.drawString(String.format("%.1f km across; drawn from Metro.at and the laid roads' own samples", 2 * half / 1000), 10, 38);
    // the boulevard profile
    int y0 = PH + 30; double vmin = -p.blvdHalf - 300, vmax = p.blvdHalf + 300; int ymin = Integer.MAX_VALUE, ymax = Integer.MIN_VALUE; int[] pad = new int[PW];
    for (int x = 0; x < PW; x++) { double v = vmin + (vmax - vmin) * x / PW; pad[x] = m.blvdPad(p, Math.max(-p.blvdHalf, Math.min(p.blvdHalf, v))); ymin = Math.min(ymin, pad[x]); ymax = Math.max(ymax, pad[x]); }
    for (double[] e : ends) { ymin = Math.min(ymin, (int) e[1]); ymax = Math.max(ymax, (int) e[1]); } ymin -= 3; ymax += 3; double sy = (EH - 30.0) / (ymax - ymin);
    g.setColor(Color.white); g.fillRect(0, y0, PW, EH); g.setColor(Color.black); g.drawString(String.format("boulevard pad y along v (black; %d to %d), road ends at the portals (dots); v from %.0f to %.0f m", ymin + 3, ymax - 3, vmin, vmax), 10, y0 + 14);
    g.setStroke(new BasicStroke(2f)); for (int x = 1; x < PW; x++) { double v = vmin + (vmax - vmin) * x / PW; if (Math.abs(v) > p.blvdHalf) continue; g.drawLine(x - 1, y0 + EH - 6 - (int) ((pad[x - 1] - ymin) * sy), x, y0 + EH - 6 - (int) ((pad[x] - ymin) * sy)); }
    for (double[] e : ends) { int x = (int) ((e[0] - vmin) / (vmax - vmin) * PW), y = y0 + EH - 6 - (int) ((Math.floor(e[1]) - ymin) * sy); g.setColor(cc[(int) e[2]]); g.fillOval(x - 5, y - 5, 10, 10); }
    g.dispose(); javax.imageio.ImageIO.write(im, "png", new java.io.File(a[1])); System.out.println("wrote " + a[1]); } }
