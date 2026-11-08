import com.hopeless.infinity.planet.*; import java.awt.*; import java.awt.image.BufferedImage; import java.util.*;
/** MetroView: a megacity's layout (planet/Metro) drawn from the real plan: left the plan (superblocks, streets, parks, axis, gate plaza,
 *  water measured from the terrain, every tower's podium shaded by height), right the skyline seen from the -v side (each tower's real
 *  floor spans from planet/Tower, nearer towers darker). Args: seed, site name (or "" for the largest megacity), out.png. Saved tool. */
public class MetroView {
  public static void main(String[] a) throws Exception { long seed = Long.parseLong(a[0]); PlanetWorld w = new PlanetWorld(seed); Sites st = w.sites(); Metro m = new Metro(w);
    Sites.Site s = null; for (Sites.Site q : st.all) if (q.kind == Sites.Kind.MEGACITY && (a[1].isEmpty() ? s == null || q.pop > s.pop : q.name.equals(a[1]))) s = q;
    Metro.Plan p = m.plan(s); System.out.println(p.probe());
    int ext = 0; for (long k : p.cells.keySet()) { var c = p.cells.get(k); if (c == Metro.Cell.WATER || c == Metro.Cell.BOULEVARD) continue; ext = Math.max(ext, Math.max(Math.abs((int) (k >> 32)), Math.abs((int) k))); }
    double half = (ext + 1.5) * Metro.PITCH_M; int PW = 1100; double mpp = 2 * half / PW; int EW = 1300, H = 1100; BufferedImage im = new BufferedImage(PW + EW, H, BufferedImage.TYPE_INT_RGB); Graphics2D g = im.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g.setColor(new Color(0xf4f1ea)); g.fillRect(0, 0, PW + EW, H);
    // ground: water from the terrain, every 8 px
    var R = w.cube.R; for (int py = 0; py < PW; py += 4) for (int px = 0; px < PW; px += 4) { double u = -half + px * mpp, v = -half + py * mpp; double[] xz = p.xz(u, v); double[] q = w.cube.spherePoint(p.face, xz[0], xz[1]); if (q == null) continue;
      double[] hw = w.terrain.heightAndWater(q, R); boolean wet = hw[0] < 2 || hw[2] == 3 || (!Double.isNaN(hw[1]) && hw[1] > hw[0] - 1); g.setColor(wet ? new Color(0x8fb8d8) : new Color(0xe2ddd0)); g.fillRect(px, py + (H - PW) / 2, 4, 4); }
    int oy = (H - PW) / 2; java.util.function.DoubleUnaryOperator X = u -> (u + half) / mpp, Y = v -> (v + half) / mpp + oy;
    for (var e : p.cells.entrySet()) { int i = (int) (e.getKey() >> 32), j = (int) (long) e.getKey(); Color c = switch (e.getValue()) { case PARK -> new Color(0x7fb069); case AXIS -> new Color(0x5e9e50); case GATE_PLAZA -> new Color(0xd8b56a); case BOULEVARD -> new Color(0x6b6b6b); case TOWER, TOWER_BIG -> new Color(0xcfc9bb); case WATER -> null; default -> null; };
      if (c == null) continue; double bw = e.getValue() == Metro.Cell.BOULEVARD ? Metro.PITCH_M : Metro.BLOCK_M; double u0 = i * Metro.PITCH_M - bw / 2, v0 = j * Metro.PITCH_M - bw / 2;
      if (e.getValue() == Metro.Cell.BOULEVARD) { u0 = i * Metro.PITCH_M - Metro.BOULEVARD_M / 2; g.setColor(c); g.fillRect((int) X.applyAsDouble(u0), (int) Y.applyAsDouble(v0), (int) Math.ceil(Metro.BOULEVARD_M / mpp), (int) Math.ceil(Metro.PITCH_M / mpp) + 1); continue; }
      g.setColor(c); g.fillRect((int) X.applyAsDouble(u0), (int) Y.applyAsDouble(v0), (int) Math.ceil(bw / mpp), (int) Math.ceil(bw / mpp)); }
    for (Metro.Lot l : p.towers) { float f = (float) Math.min(1, l.height / p.top); g.setColor(l.hq ? new Color(0xc0392b) : Color.getHSBColor(0.6f, 0.15f + 0.6f * f, 0.85f - 0.55f * f)); int sz = (int) Math.max(2, (2 * l.half + 1) / mpp);
      g.fillRect((int) X.applyAsDouble(l.u - l.half), (int) Y.applyAsDouble(l.v - l.half), sz, sz); }
    g.setColor(Color.black); g.setFont(new Font("SansSerif", Font.BOLD, 15)); g.drawString(s.name + "  (pop " + String.format("%,d", s.pop) + ")  plan, " + Math.round(2 * half / 1000.0) + " km across", 10, 22);
    g.setFont(new Font("SansSerif", Font.PLAIN, 12)); String[] lg = {"red: Cobble & Sons HQ", "gold: gate plaza", "dark green: 2 km axis park", "green: parks (14 %)", "grey band: 135 m boulevard", "blue: water (terrain)", "towers: darker = taller"};
    for (int i = 0; i < lg.length; i++) g.drawString(lg[i], 10, H - 12 - 15 * (lg.length - 1 - i));
    int bar = (int) (1000 / mpp); g.fillRect(PW - bar - 20, H - 20, bar, 4); g.drawString("1 km", PW - bar - 20, H - 26);
    // skyline from -v: towers far (high v) first, lighter
    double eScale = (H - 80) / (p.top + 60), eu = (EW - 40) / (2 * half); java.util.List<Metro.Lot> ls = new ArrayList<>(p.towers); ls.sort((x, y) -> Double.compare(y.v, x.v));
    g.setColor(new Color(0xdde8f0)); g.fillRect(PW, 0, EW, H); int base = H - 40; g.setColor(new Color(0x9a9a9a)); g.fillRect(PW, base, EW, 40);
    double vmin = -half, vmax = half;
    for (Metro.Lot l : ls) { Tower t = new Tower("v", l.height, l.seed); float d = (float) ((l.v - vmin) / (vmax - vmin)); g.setColor(l.hq ? new Color(0x8e2a20) : new Color(Color.HSBtoRGB(0.6f, 0.12f, 0.25f + 0.6f * d)));
      double cxp = PW + 20 + (l.u + half) * eu; for (int f = 0; f < t.floors; f += 2) { double sp = t.span(f) * eu; int y0 = (int) (base - (f + 2) * Tower.FLOOR * eScale), y1 = (int) (base - f * Tower.FLOOR * eScale); g.fillRect((int) (cxp - sp / 2), y0, Math.max(1, (int) Math.round(sp)), Math.max(1, y1 - y0)); }
      int tp = t.top(); g.fillRect((int) cxp, (int) (base - tp * eScale), 1, (int) ((tp - t.height) * eScale)); }
    g.setColor(Color.black); g.setFont(new Font("SansSerif", Font.BOLD, 15)); g.drawString("skyline from the south (each tower's real floor spans; same horizontal scale as the plan)", PW + 10, 22);
    g.setFont(new Font("SansSerif", Font.PLAIN, 12)); for (int hm = 0; hm <= p.top; hm += 250) { int y = (int) (base - hm * eScale); g.setColor(new Color(0, 0, 0, 60)); g.drawLine(PW, y, PW + EW, y); g.setColor(Color.black); g.drawString(hm + " m", PW + 4, y - 2); }
    g.dispose(); javax.imageio.ImageIO.write(im, "png", new java.io.File(a[2])); System.out.println("wrote " + a[2]); }
}
