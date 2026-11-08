import com.hopeless.infinity.planet.*; import java.awt.image.BufferedImage; import java.awt.*; import java.util.*;
/** RoadView seed out-prefix: top-down block views (1 px = 1 block, from Column.of as the chunk generator lays it, roads included) of an
 *  Interstate bridge over water, an overpass, a mountain tunnel portal and farm country with section-line roads. Saved tool. */
public class RoadView {
  static int col(Column.Mat m, Terrain.Bio b) { return switch (m) {
    case GRASS -> 0x6f9e48; case DIRT -> 0x86603e; case SAND -> 0xdcd3a0; case STONE -> 0x7d7d7d; case GRAVEL -> 0x8a8480; case WATER -> 0x3a68c8; case SNOW_BLOCK, SNOW_LAYER -> 0xf2f6f8; case ICE, PACKED_ICE -> 0xa8c4f0;
    case ROAD_CONCRETE, DECK, LINING -> 0xa0a0a0; case ASPHALT -> 0x3a3c40; case LINE_WHITE -> 0xf4f4f4; case LINE_YELLOW -> 0xf0c020; case DIRT_PATH -> 0x9c7c48; case RAIL -> 0x6a6a6a; case MUD -> 0x3c3430; case PODZOL -> 0x5c4024;
    case RED_SAND -> 0xc0662e; case TERRACOTTA -> 0x985a40; case CALCITE -> 0xe0e0dc; case BASALT, BASALT_ROCK, BLACKSTONE -> 0x4a4a50; case CLAY -> 0xa0a6b2; case SANDSTONE -> 0xd8cc90; default -> 0xff00ff; }; }
  /** The top block at a column (overlays included) and its y. */
  static int[] top(Column c) { int ty = c.topY(); for (int y = ty; y > c.surfaceY - 3; y--) { Column.Mat m = c.at(y, -2560); if (m != Column.Mat.AIR) return new int[]{y, m.ordinal()}; } return new int[]{c.surfaceY, c.top.ordinal()}; }
  public static void view(PlanetWorld w, double[] s, String title, String out, int N) throws Exception {
    double[] loc = w.cube.locate(s); int f = (int) loc[0], cx = (int) loc[1], cz = (int) loc[2]; BufferedImage img = new BufferedImage(N, N + 24, BufferedImage.TYPE_INT_RGB); int[][] hy = new int[N][N]; int[][] rgb = new int[N][N];
    for (int j = 0; j < N; j++) for (int i = 0; i < N; i++) { Column c = Column.of(w, f, cx - N / 2 + i, cz - N / 2 + j); if (c.voidCol) continue; int[] t = top(c); hy[j][i] = t[0]; Column.Mat m = Column.Mat.values()[t[1]];
      if (m != Column.Mat.WATER && c.waterY >= t[0] && m != Column.Mat.DECK && m != Column.Mat.ROAD_CONCRETE && m != Column.Mat.ASPHALT && m != Column.Mat.LINE_WHITE && m != Column.Mat.LINE_YELLOW && m != Column.Mat.RAIL) m = Column.Mat.WATER;
      if (c.surfaceY < Terrain.SEA && t[0] <= Terrain.SEA) m = Column.Mat.WATER; rgb[j][i] = col(m, c.biome); }
    for (int j = 0; j < N; j++) for (int i = 0; i < N; i++) { double sh = i > 0 && j > 0 ? 1 + 0.12 * Math.max(-3, Math.min(3, (hy[j][i] - hy[j - 1][i - 1]))) : 1; Color cc = new Color(rgb[j][i]);
      img.setRGB(i, 24 + j, new Color(cl(cc.getRed() * sh), cl(cc.getGreen() * sh), cl(cc.getBlue() * sh)).getRGB()); }
    Graphics2D g = img.createGraphics(); g.setColor(new Color(0x10141c)); g.fillRect(0, 0, N, 24); g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.PLAIN, 12));
    g.drawString(title + String.format("  (face %d, %d, %d; %d x %d blocks)", f, cx, cz, N, N), 6, 16); javax.imageio.ImageIO.write(img, "png", new java.io.File(out)); System.out.println("wrote " + out); }
  static int cl(double v) { return (int) Math.max(0, Math.min(255, v)); }
  public static void main(String[] a) throws Exception { PlanetWorld w = new PlanetWorld(Long.parseLong(a[0])); Roads r = w.roads(); Random rnd = new Random(7);
    double[] bridge = null, tunnel = null, over = null; String bt = "", tt = "", ot = "";
    for (Roads.Edge e : r.edges) { if (bridge != null && tunnel != null && over != null) break; if (e.cls != Roads.Cls.INTERSTATE) continue; Roads.Route ro = r.route(e.id);
      for (int i = 20; i + 20 < ro.n(); i++) { if (bridge == null && ro.kind[i] == 1 && w.terrain.heightAndWater(ro.p(i), w.cube.R)[2] == 1) { bridge = ro.p(i); bt = "Interstate " + e.id + " bridge over a river"; }
        if (tunnel == null && ro.kind[i] == 2 && ro.kind[i - 10] == 0) { tunnel = ro.p(i - 4); tt = "Interstate " + e.id + " tunnel portal (the road goes under the hill)"; } }
      if (over == null && !ro.crossAt.isEmpty()) { over = ro.p(ro.crossAt.get(0)); ot = "Interstate " + e.id + " passing over another Interstate"; } }
    if (bridge != null) view(w, bridge, bt, a[1] + "-bridge.png", 320);
    if (over != null) view(w, over, ot, a[1] + "-overpass.png", 320);
    if (tunnel != null) view(w, tunnel, tt, a[1] + "-tunnel.png", 320);
    // 0.23.1 interchanges: lay Interstates until a cloverleaf and a diamond exist
    for (int id : new int[]{558, 82}) r.route(id);   // seed 1's known Interstate-over-Interstate crossings (RoadView found them by scanning; a scan of all 3,500 Interstates does not fit in memory here)
    { Random ra = new Random(9); for (int k = 0; k < 400; k++) { boolean d = false; synchronized (Interchanges.built) { for (double[] b : Interchanges.built) if (b[3] == 0) d = true; } if (d) break; Roads.Edge e = r.edges.get(ra.nextInt(r.edges.size())); if (e.cls == Roads.Cls.ARTERIAL) r.route(e.id); } }
    double[] clo = null, dia = null; synchronized (Interchanges.built) { for (double[] b : Interchanges.built) { if (b[3] == 1 && clo == null) clo = b; if (b[3] == 0 && dia == null) dia = b; } }
    System.out.println(Interchanges.probe());
    if (clo != null) view(w, new double[]{clo[0], clo[1], clo[2]}, "Interstate meets Interstate: full cloverleaf with C-D roads", a[1] + "-cloverleaf.png", 3000);
    if (dia != null) view(w, new double[]{dia[0], dia[1], dia[2]}, "Highway crosses an Interstate: diamond interchange", a[1] + "-diamond.png", 1000);
    Sites st = w.sites(); Sites.Site fs = null; for (Sites.Site x : st.all) if (x.kind == Sites.Kind.RURAL_TOWN && st.habit[x.cell] >= 0.9) { fs = x; break; }
    view(w, st.exact(fs), "Prime farm land round " + fs.name + " (habitability " + String.format("%.2f", st.habit[fs.cell]) + "): section-line roads, a mile apart", a[1] + "-farm.png", 3300);
    System.out.println(r.probe()); } }
