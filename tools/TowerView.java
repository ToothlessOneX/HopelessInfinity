import com.hopeless.infinity.planet.Tower; import java.awt.*; import java.awt.image.BufferedImage; import java.util.*;
/** TowerView out.png heightM [seed]: one supertall tower from planet/Tower (the blocks the generator places): its front elevation, a section
 *  through its middle (every floor's rooms), and a plan of one floor of each use. Colours per block as TownIso. Saved tool. */
public class TowerView {
  public static void main(String[] a) throws Exception { if (a.length > 3 && a[3].equals("zoom")) { zoom(a); return; } int H = Integer.parseInt(a[1]); Tower t = new Tower("Tower " + H + " m", H, a.length > 2 ? Integer.parseInt(a[2]) : 1);
    int R = t.reach(), top = t.top(), W = 2 * R + 1, S = Math.max(1, 1700 / top + (top < 400 ? 1 : 0)); int pw = W * S, ph = top * S;
    // plans: one floor of each use, at 3 px a block
    java.util.List<Integer> planFloors = new ArrayList<>(); Set<Tower.Use> seen = new HashSet<>(); for (int f = 0; f < t.floors; f++) if (seen.add(t.use(f))) planFloors.add(f);
    int P = 3, planW = (2 * t.reach() + 1) * P; int cols = 2, planH = ((planFloors.size() + cols - 1) / cols) * (planW + 20);
    BufferedImage img = new BufferedImage(2 * pw + 60 + cols * (planW + 10), Math.max(ph + 60, planH + 60), BufferedImage.TYPE_INT_RGB); Graphics2D g = img.createGraphics();
    g.setColor(new Color(0xd9e6f2)); g.fillRect(0, 0, img.getWidth(), img.getHeight()); g.setColor(Color.BLACK); g.setFont(new Font("SansSerif", Font.PLAIN, 13));
    g.drawString(t.probe(), 10, 16); g.drawString("elevation", 10, 34); g.drawString("section at z = 0", pw + 30, 34);
    int oy = 40 + ph;
    for (int y = 0; y < top; y++) for (int x = -R; x <= R; x++) {
      // elevation: the first block met looking from -z
      String b = null; for (int z = -R; z <= R && b == null; z++) { String s = t.at(x, y, z); if (s != null && !s.equals("air")) b = s; }
      if (b != null) fill(g, 10 + (x + R) * S, oy - (y + 1) * S, S, BlockColors.color(b));
      String c = t.at(x, y, 0); if (c != null) fill(g, pw + 30 + (x + R) * S, oy - (y + 1) * S, S, c.equals("air") ? 0xf4f1ea : BlockColors.color(c)); }
    int px0 = 2 * pw + 50, k = 0;
    for (int f : planFloors) { int cx = px0 + (k % cols) * (planW + 10), cy = 40 + (k / cols) * (planW + 20); g.setColor(Color.BLACK); g.drawString("floor " + f + ": " + t.use(f).name().toLowerCase() + ", span " + t.span(f) + " m", cx, cy + 12);
      for (int z = -R; z <= R; z++) for (int x = -R; x <= R; x++) { String b = t.at(x, f * Tower.FLOOR + 1, z), fl = t.at(x, f * Tower.FLOOR, z); if (fl == null) continue;
        String show = b != null && !b.equals("air") ? b : fl; fill(g, cx + (x + R) * P, cy + 16 + (z + R) * P, P, BlockColors.color(show)); } k++; }
    javax.imageio.ImageIO.write(img, "png", new java.io.File(a[0])); System.out.println("wrote " + a[0] + "; " + t.probe() + "; colours not found: " + BlockColors.missing()); }
  /** zoom: TowerView out heightM seed zoom USE x z half: the first floor of that use, the square round (x, z), 10 px a block; furniture
   *  (the block one up from the slab) drawn over the floor, with a dark outline so single blocks read. */
  static void zoom(String[] a) throws Exception { Tower t = new Tower("Tower", Integer.parseInt(a[1]), Integer.parseInt(a[2])); Tower.Use want = Tower.Use.valueOf(a[4]); int cx = Integer.parseInt(a[5]), cz = Integer.parseInt(a[6]), hf = Integer.parseInt(a[7]), P = 10;
    int f = 0; while (f < t.floors && t.use(f) != want) f++; int S = 2 * hf + 1; BufferedImage img = new BufferedImage(S * P, S * P + 24, BufferedImage.TYPE_INT_RGB); Graphics2D g = img.createGraphics(); g.setColor(new Color(0xd9e6f2)); g.fillRect(0, 0, img.getWidth(), img.getHeight());
    g.setColor(Color.BLACK); g.drawString("floor " + f + " (" + want.name().toLowerCase() + "), " + S + " x " + S + " m round " + cx + "," + cz + ", from planet/Tower", 6, 16); Map<String, Integer> count = new TreeMap<>();
    for (int z = -hf; z <= hf; z++) for (int x = -hf; x <= hf; x++) { String fl = t.at(cx + x, f * Tower.FLOOR, cz + z); if (fl == null) continue; String b1 = t.at(cx + x, f * Tower.FLOOR + 1, cz + z), b2 = t.at(cx + x, f * Tower.FLOOR + 2, cz + z);
      String show = b1 != null && !b1.equals("air") ? b1 : b2 != null && !b2.equals("air") ? b2 : fl; int px = (x + hf) * P, py = 24 + (z + hf) * P;
      if (show.startsWith("shape:")) { String[] q = show.split(":", 4); int si = com.hopeless.infinity.slope.ShapeLib.INDEX.get(q[1]), o = Integer.parseInt(q[2]); g.setColor(new Color(0xd9e6f2)); g.fillRect(px, py, P, P); g.setColor(new Color(BlockColors.color(show)));   // 0.26.3 a shaped block: its footprint at mid height, from ShapeLib's boxes
        for (int[] bx : com.hopeless.infinity.slope.ShapeLib.boxes(si, o)) if (bx[1] <= 8 && bx[4] > 8) g.fillRect(px + bx[0] * P / 16, py + bx[2] * P / 16, Math.max(1, (bx[3] - bx[0]) * P / 16), Math.max(1, (bx[5] - bx[2]) * P / 16));
        count.merge(q[1], 1, Integer::sum); continue; }
      fill(g, px, py, P, BlockColors.color(show));
      if (show != fl) { g.setColor(new Color(0x202020)); g.drawRect(px, py, P - 1, P - 1); String nm = show.replaceAll("\\[.*", "").replace("hopelessinfinity:", ""); count.merge(nm, 1, Integer::sum); } }
    javax.imageio.ImageIO.write(img, "png", new java.io.File(a[0])); System.out.println("wrote " + a[0] + "; " + count + "; colours not found: " + BlockColors.missing()); }
  static void fill(Graphics2D g, int x, int y, int s, int rgb) { if (rgb < 0) return; g.setColor(new Color(rgb)); g.fillRect(x, y, s, s); } }
