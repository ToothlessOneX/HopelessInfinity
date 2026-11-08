import com.hopeless.infinity.planet.*; import java.awt.*; import java.awt.image.BufferedImage; import java.util.*;
/** TownIso seed out.png [site name] [half-size] [du dv]: an isometric view of a town, from the same columns (Column.of) and building blocks
 *  (Buildings.chunk) the chunk generator places; each block coloured by its material. Saved tool. */
public class TownIso {
  static int color(String b) { String n = b.contains("[") ? b.substring(0, b.indexOf('[')) : b;
    if (n.equals("air")) return -1; if (n.contains("lightning_rod")) return 0xc06a3a; if (n.contains("black_concrete")) return 0x14161a; if (n.contains("anvil")) return 0x444444; if (n.contains("iron_block")) return 0xd8d8d8; if (n.contains("glass")) return 0x9fc6d8; if (n.contains("white_terracotta")) return 0xd2b2a1; if (n.contains("light_gray_terracotta")) return 0x876a61; if (n.contains("yellow_terracotta")) return 0xba8523;
    if (n.contains("light_blue_terracotta")) return 0x716d8a; if (n.contains("green_terracotta")) return 0x4c532a; if (n.contains("brown_terracotta")) return 0x4d3324; if (n.contains("red_terracotta")) return 0x8f3d2e; if (n.contains("blue_terracotta")) return 0x4a3b5b; if (n.contains("black_terracotta")) return 0x251610;
    if (n.contains("mud_brick")) return 0x89674f; if (n.contains("red_nether")) return 0x450709; if (n.equals("bricks") || n.contains("brick_stairs") && !n.contains("stone")) return 0x966152; if (n.contains("stone_brick")) return 0x7a7a7a; if (n.contains("deepslate")) return 0x363639; if (n.contains("blackstone")) return 0x2a2328;
    if (n.contains("dark_oak")) return 0x42301a; if (n.contains("spruce")) return 0x6b4f2f; if (n.contains("oak")) return 0xa2834f; if (n.contains("birch")) return 0xd7cb8d; if (n.contains("white_concrete") || n.contains("white_banner")) return 0xe9ecec; if (n.contains("light_gray_concrete")) return 0x9b9b94;
    if (n.contains("gray_concrete")) return 0x55585b; if (n.contains("red_concrete")) return 0x8e2121; if (n.contains("smooth_stone")) return 0xa6a6a6; if (n.contains("andesite")) return 0x848686; if (n.contains("iron")) return 0x8f8f8f; if (n.contains("stone")) return 0x7d7d7d; return 0xff00ff; }
  static int matColor(Column.Mat m) { return switch (m) { case GRASS -> 0x6f9e48; case DIRT -> 0x86603e; case SAND -> 0xdcd3a0; case ASPHALT -> 0x3a3c40; case ROAD_CONCRETE -> 0xb4b4b0; case GRAVEL -> 0x8a8480; case LINE_WHITE -> 0xf4f4f4; case LINE_YELLOW -> 0xf0c020; case WATER -> 0x3a68c8; case STONE -> 0x7d7d7d; case SNOW_BLOCK -> 0xf2f6f8; default -> 0x7a9a50; }; }
  public static void main(String[] a) throws Exception { PlanetWorld w = new PlanetWorld(Long.parseLong(a[0])); Sites st = w.sites(); Towns tw = w.towns();
    Sites.Site s = st.spawnTown; if (a.length > 2 && !a[2].equals("-")) for (Sites.Site x : st.all) if (x.name.equals(a[2])) s = x;
    Towns.Plan p = tw.plan(s); int N = a.length > 3 ? Integer.parseInt(a[3]) : 70; double du = a.length > 5 && !a[4].equals("zone") ? Double.parseDouble(a[4]) : 0, dv = a.length > 5 && !a[4].equals("zone") ? Double.parseDouble(a[5]) : 0;
    if (a.length > 5 && a[4].equals("zone")) { double[] zn = p.zones.get(Integer.parseInt(a[5])); du = zn[0]; dv = zn[1]; }
    int cxB = (int) Math.floor(p.alongX ? p.cx + du : p.cx + dv), czB = (int) Math.floor(p.alongX ? p.cz + dv : p.cz + du), x0 = cxB - N, z0 = czB - N, x1 = cxB + N, z1 = czB + N, S = 2 * N;
    int cut = a.length > 6 ? Integer.parseInt(a[6]) : 0;   // 0.26.2 cutaway: draw only `cut` blocks over the ground (the ground floor's furniture shows)
    int[] ground = new int[S * S]; int[] gcol = new int[S * S]; Map<Long, Integer> vox = new HashMap<>(); int ymin = Integer.MAX_VALUE, ymax = Integer.MIN_VALUE;
    for (int z = 0; z < S; z++) for (int x = 0; x < S; x++) { Column c = Column.of(w, p.face, x0 + x, z0 + z); ground[z * S + x] = c.surfaceY; gcol[z * S + x] = matColor(c.top); ymin = Math.min(ymin, c.surfaceY); }
    double[] sc = w.cube.spherePoint(p.face, cxB + 0.5, czB + 0.5); final int fx0 = x0, fz0 = z0;
    // the buildings, chunk by chunk as the generator does (each chunk only its own blocks)
    for (int cz = Math.floorDiv(z0, 16) * 16; cz < z1; cz += 16) for (int cx = Math.floorDiv(x0, 16) * 16; cx < x1; cx += 16) { final int ccx = cx, ccz = cz;
      Buildings.chunk(tw, p.face, cx, cz, cx + 16, cz + 16, w.cube.spherePoint(p.face, cx + 8, cz + 8), (x, y, z, b) -> { if (x < fx0 || x >= fx0 + S || z < fz0 || z >= fz0 + S) return; vox.put(((long) (x - fx0) << 40) | ((long) (z - fz0) << 20) | (y + 4096), BlockColors.color(b)); }); }
    for (long k : vox.keySet()) ymax = Math.max(ymax, (int) (k & 0xFFFFF) - 4096); for (int g : ground) ymax = Math.max(ymax, g);
    int T = a.length > 7 ? Integer.parseInt(a[7]) : 4, Wd = S * T * 2 + 40, Hd = S * T + (ymax - ymin + 4) * T * 2 + 60; BufferedImage img = new BufferedImage(Wd, Hd, BufferedImage.TYPE_INT_RGB); Graphics2D g = img.createGraphics(); g.setColor(new Color(0xd9e6f2)); g.fillRect(0, 0, Wd, Hd);
    int ox = Wd / 2, oy = 40 + (ymax - ymin + 2) * T * 2;
    // painter's order: far (low x+z) to near, bottom to top
    for (int sum = 0; sum <= 2 * (S - 1); sum++) for (int x = Math.max(0, sum - S + 1); x <= Math.min(S - 1, sum); x++) { int z = sum - x;
      int gy = ground[z * S + x]; cube(g, ox, oy, x, z, gy - ymin, gcol[z * S + x], T);
      for (int y = gy + 1 - 6; y <= (cut > 0 ? gy + cut : ymax + 2); y++) { Integer cl = vox.get(((long) x << 40) | ((long) z << 20) | (y + 4096)); if (cl == null) continue; if (cl < 0) continue; cube(g, ox, oy, x, z, y - ymin, cl, T); } }
    g.setColor(Color.BLACK); g.setFont(new Font("SansSerif", Font.PLAIN, 14)); g.drawString(s + String.format(": %d x %d blocks, isometric, from the generator's own columns and building blocks", S, S), 10, 20);
    javax.imageio.ImageIO.write(img, "png", new java.io.File(a[1])); System.out.println("wrote " + a[1] + "; " + Buildings.probe() + "; colours not found: " + BlockColors.missing()); }
  static void cube(Graphics2D g, int ox, int oy, int x, int z, int y, int rgb, int T) { int sx = ox + (x - z) * T * 2 / 2 * 1, sy = oy + (x + z) * T / 2 * 1 - y * T * 2 / 2 * 1; Color c = new Color(rgb);
    int px = ox + (x - z) * T, py = oy + (x + z) * T / 2 - y * T;
    Polygon top = new Polygon(new int[]{px, px + T, px, px - T}, new int[]{py - T / 2, py, py + T / 2, py}, 4); g.setColor(c); g.fillPolygon(top);
    Polygon left = new Polygon(new int[]{px - T, px, px, px - T}, new int[]{py, py + T / 2, py + T / 2 + T, py + T}, 4); g.setColor(shade(c, 0.75)); g.fillPolygon(left);
    Polygon right = new Polygon(new int[]{px, px + T, px + T, px}, new int[]{py + T / 2, py, py + T, py + T / 2 + T}, 4); g.setColor(shade(c, 0.6)); g.fillPolygon(right); }
  static Color shade(Color c, double k) { return new Color((int) (c.getRed() * k), (int) (c.getGreen() * k), (int) (c.getBlue() * k)); } }
