import com.hopeless.infinity.planet.*; import com.hopeless.infinity.slope.ShapeLib; import java.awt.*; import java.awt.image.BufferedImage; import java.util.*;
/** BlockIso: an isometric picture of any block source (x, y, z) -> block string, at 1/8 block: full blocks as cubes, shaped blocks as their
 *  own collision boxes (ShapeLib.boxes, the shape the game uses), colours MEASURED from the textures (BlockColors). Scenes: "gate" (the
 *  megacity's great gate, planet/GreatGate, on a strip of plaza). Args: scene out.png [pixels a block] [view: 0 from +x+z, 1 from -x+z].
 *  Run from the repo root (BlockColors reads the mod's own textures). Saved tool. */
public class BlockIso {
  public interface Src { String at(int x, int y, int z); }
  static final java.util.List<Object[]> LABELS = new ArrayList<>();   // {x, y, z, text}: drawn over the picture at those points
  static long key(int x, int y, int z) { return ((long) x << 40) | ((long) (y + 0x80000) << 20) | (z + 0x80000); }
  public static void main(String[] a) throws Exception { String scene = a[0]; int S = a.length > 2 ? Integer.parseInt(a[2]) : 24; int view = a.length > 3 ? Integer.parseInt(a[3]) : 0;
    Src src; int x0, x1, y0, y1, z0, z1; String title;
    if (scene.equals("gate")) { src = (x, y, z) -> { if (y == -1) return "light_gray_concrete"; return GreatGate.at(x, y, z); }; x0 = GreatGate.X0 - 2; x1 = GreatGate.X1 + 2; y0 = -1; y1 = GreatGate.TOP; z0 = GreatGate.Z0 - 2; z1 = GreatGate.Z1 + 2;
      title = "Great gate (planet/GreatGate): ring 19 / aperture 15, best-fit round edges, dais 1 m with 42 in guards, ADA 1:12 grate ramps, 34-38 in handrails; closed until set"; }
    else if (scene.equals("hqfront")) {   // 0.27.3 the spawn city's HQ podium front (tower blocks + planet/Frontage), turned 180 degrees so its plaza face shows
      PlanetWorld w = new PlanetWorld(1); w.playerSpawn(); Metro m = w.metro(); Metro.Plan p = null; for (Metro.Plan q : m.plans.values()) if (q.face == 0 && w.spawnKind.contains(q.site.name)) p = q;
      Tower t = p.towers.get(0).tower(); int ph = t.podiumHalf; src = (x, y, z) -> { int lx = -x, lz = -z; if (y == -1) return Math.abs(lx) <= ph + 6 && lz >= -ph - 8 ? "light_gray_concrete" : null; if (lz == -ph) { String fb = Frontage.at(lx, y, ph); if (fb != null) return fb; } return lz >= -ph && lz <= -ph + 3 ? t.at(lx, y, lz) : null; };
      x0 = -90; x1 = 90; y0 = -1; y1 = 27; z0 = ph - 3; z1 = ph + 8; title = "Cobble & Sons HQ front (" + p.site.name + "): sign, logo, two 32 x 18 m screen panels, main doors (blocks only; the picture is the shader's)"; }
    else if (scene.equals("mainstreet")) {   // 0.27.4 a stretch of the largest sampled town's Main Street: the buildings stage's own blocks; each store sign's own text as a label
      PlanetWorld w = new PlanetWorld(1); Sites st = w.sites(); Towns tw = w.towns(); Sites.Site best = null; for (Sites.Site q : st.all) if (q.kind != Sites.Kind.LARGE_CITY && q.kind != Sites.Kind.MEGACITY && q.kind != Sites.Kind.SPACEPORT && (best == null || q.pop > best.pop)) best = q;
      Towns.Plan p = tw.plan(best); Map<Long, String> got = new HashMap<>(); int cx = (int) p.cx, cz = (int) p.cz, half = 110, hu = p.alongX ? 110 : 34, hv = p.alongX ? 34 : 110; int[] ymin = {Integer.MAX_VALUE};
      for (int qx = (cx - half) >> 4 << 4; qx < cx + half; qx += 16) for (int qz = (cz - half) >> 4 << 4; qz < cz + half; qz += 16) Buildings.chunk(tw, p.face, qx, qz, qx + 16, qz + 16, w.cube.spherePoint(p.face, qx + 8, qz + 8), (x, y, z, b) -> { if (Math.abs(x - cx) > hu || Math.abs(z - cz) > hv) return; got.put(key(x - cx, y, z - cz), b); ymin[0] = Math.min(ymin[0], y); });
      int yb = ymin[0]; src = (x, y, z) -> { if (y == -1) return "gray_concrete"; return got.get(key(x, y + yb, z)); };
      for (var e : got.entrySet()) if (e.getValue().startsWith("sign:")) { long k = e.getKey(); String[] sp = e.getValue().split("\\|", -1); String t = (sp[3] + " " + sp[4]).trim(); LABELS.add(new Object[]{(double) (int) (k >> 40), (double) ((int) ((k >> 20) & 0xFFFFF) - 0x80000 - yb) + 1.4, (double) ((int) (k & 0xFFFFF) - 0x80000), t}); }
      x0 = -hu; x1 = hu; y0 = -1; y1 = 20; z0 = -hv; z1 = hv; title = best.name + " (pop " + String.format("%,d", best.pop) + "), Main Street: " + LABELS.size() + " store signs, chain and family (labels: each sign's own text)"; }
    else if (scene.equals("stairs")) {   // 0.27.8 a stairway shaft of a 1650 m tower, cut open on its -z half to show the steps climbing past three floors
      Tower t = new Tower("hq", 1650, 1); int SC = t.stairCore(), ST = Math.max(3, SC / 3); src = (x, y, z) -> (z > -SC + ST / 2 || x < -SC + 1 || x > -SC + ST || z < -SC) ? null : t.at(x, y, z);   /* the shaft only, its -x wall and +z half cut away */
      x0 = -SC - 1; x1 = -SC + ST + 2; y0 = 0; y1 = 16; z0 = -SC - 1; z1 = -SC + ST + 2; title = "Stairway shaft (planet/Tower, 1650 m): steps round the shaft, one block up per cell, landings at each floor, a door from the core; cut open"; }
    else if (scene.equals("park")) {   // 0.28.0 a park superblock (planet/Parks), ordinary or owned (arg 4: "owned"), on a flat lawn
      boolean owned = a.length > 4 && a[4].equals("owned"); int half = (int) Math.floor(Metro.BLOCK_M / 2); long sd = a.length > 5 ? Long.parseLong(a[5]) : 3;
      src = (x, y, z) -> y == -1 ? "dirt" : Parks.park(x, y, z, half, sd, owned); x0 = -half; x1 = half; y0 = -1; y1 = 4; z0 = -half; z1 = half;
      title = (owned ? "A megatall's own park" : "A park superblock") + " (planet/Parks): promenade and tree row, crossing paths and ring, " + (owned ? "pond" : "fountain") + ", lamps, benches, bins, lawn trees" + (Math.floorMod(sd, 3) == 0 && !owned ? ", playground" : ""); }
    else if (scene.equals("plaza")) {   // 0.28.0 a megatall's plaza round its podium (planet/Parks.plaza) with the podium's lower floors (planet/Tower)
      Tower t = new Tower("hq", 1650, 1); int ph = t.podiumHalf, lh = (int) Math.floor(Metro.BIG_HALF) - 1;
      src = (x, y, z) -> { if (y == -1) return Parks.plaza(x, 0, z, ph, lh) != null ? Parks.plaza(x, 0, z, ph, lh) : "light_gray_concrete"; if (Math.abs(x) <= ph && Math.abs(z) <= ph) return y < 8 ? t.at(x, y, z) : null; return Parks.plaza(x, y + 1, z, ph, lh); };
      x0 = -lh; x1 = lh; y0 = -1; y1 = 14; z0 = -lh; z1 = lh; title = "A megatall's plaza (planet/Parks): two-tone pavers, reflecting pools by the front door, three flagpoles, planted trees, benches; the podium's first 2 floors"; }
    else if (scene.equals("bridge")) {   // 0.28.1 the spawn city's first sky bridge and the floors of its two towers round it, from Metro.chunk's own blocks
      PlanetWorld w = new PlanetWorld(1); w.playerSpawn(); Metro m = w.metro(); Metro.Plan p = null; for (Metro.Plan q : m.plans.values()) if (q.face == 0 && w.spawnKind.contains(q.site.name)) p = q; final Metro.Plan P = p;
      SkyBridges.Bridge b = m.bridges(p).get(0); Map<Long, String> got = new HashMap<>(); double cs = (b.s0() + b.s1()) / 2; int half = (int) ((b.s1() - b.s0()) / 2) + 30, yb = b.deckY();
      double[] lo = b.alongU() ? p.xz(cs - half, b.across() - 30) : p.xz(b.across() - 30, cs - half), hi = b.alongU() ? p.xz(cs + half, b.across() + 30) : p.xz(b.across() + 30, cs + half); double[] c0 = b.alongU() ? p.xz(cs, b.across()) : p.xz(b.across(), cs);
      for (int x0c = Math.floorDiv((int) Math.min(lo[0], hi[0]), 16) * 16; x0c < Math.max(lo[0], hi[0]); x0c += 16) for (int z0c = Math.floorDiv((int) Math.min(lo[1], hi[1]), 16) * 16; z0c < Math.max(lo[1], hi[1]); z0c += 16)
        m.chunk(p.face, x0c, z0c, x0c + 16, z0c + 16, w.cube.spherePoint(p.face, x0c + 8, z0c + 8), (x, y, z, bk) -> { if (y < yb - 14 || y > yb + 14) return; int lx = x - (int) Math.floor(c0[0]), lz = z - (int) Math.floor(c0[1]); if (b.alongU() ? lz > 2 : lx > 2) return; got.put(key(lx, y - yb, lz), bk); });
      src = (x, y, z) -> got.get(key(x, y, z)); int hx = b.alongU() ? half : 30, hz = b.alongU() ? 30 : half; x0 = -hx; x1 = hx; y0 = -14; y1 = 14; z0 = -hz; z1 = hz;
      title = String.format("Sky bridge in %s (planet/SkyBridges): %d m long, two storeys, glass walls and roof, trusses under; towers %d m and %d m, cut open on one side", p.site.name, (int) (b.s1() - b.s0() - 2 * SkyBridges.INTO), b.a().height, b.b().height); }
    else if (scene.equals("fusion")) {   // 0.28.3 one fusion unit (planet/FusionPlant) on its plot, cut open on its +z... front half of the hall to show the cryostat
      boolean cut = a.length > 4 && a[4].equals("cut"); src = (x, y, z) -> { if (y == -1) return "gray_concrete"; if (cut && x >= 0 && z < FusionPlant.HALL_W && y > 0) return null;   /* the hall's +x half (toward the viewer) cut away */ return FusionPlant.at(x, y, z); };
      x0 = -FusionPlant.PLOT_X / 2; x1 = FusionPlant.PLOT_X / 2 - 1; y0 = -1; y1 = FusionPlant.WALL + FusionPlant.VAULT_R + 1; z0 = 0; z1 = FusionPlant.PLOT_Z - 1;
      title = "Fusion unit (planet/FusionPlant): an ARC-class 400 MW unit; reactor hall with a fitted barrel vault, cryostat inside, turbine hall, switchyard" + (cut ? "; hall cut open" : ""); }
    else if (scene.equals("quarry")) {   // 0.28.5 the Cobble & Sons origin quarry (planet/Quarry) on seed 1: the ground from Column.of (the cut pit, the land round it) and Quarry.chunk's own blocks; arg 4 "cut": the +z half cut away along the stair's line
      PlanetWorld w = new PlanetWorld(1); w.playerSpawn(); Quarry.Site q = Quarry.site(w); boolean cut = a.length > 4 && a[4].equals("cut"); int hx = Quarry.A + 14, hz = Quarry.B + Quarry.STAIR_OUT + 4, yb = q.rimY() - Quarry.BENCH_H * Quarry.BENCHES - 3;
      Map<Long, Integer> top = new HashMap<>(); Map<Long, Boolean> pit = new HashMap<>(); for (int x = -hx; x <= hx; x++) for (int z = -hz; z <= hz; z++) { Column c = Column.of(w, q.face(), q.x() + x, q.z() + z); top.put(key(x, 0, z), c.surfaceY); pit.put(key(x, 0, z), Quarry.floor(q, q.x() + x, q.z() + z) != Integer.MIN_VALUE); }
      Map<Long, String> got = new HashMap<>(); for (int x0c = Math.floorDiv(q.x() - hx, 16) * 16; x0c <= q.x() + hx; x0c += 16) for (int z0c = Math.floorDiv(q.z() - hz, 16) * 16; z0c <= q.z() + hz; z0c += 16) Quarry.chunk(w, q.face(), x0c, z0c, x0c + 16, z0c + 16, (x, y, z, b) -> got.put(key(x - q.x(), y - yb, z - q.z()), b));
      src = (x, y, z) -> { if (cut && z > 0) return null; String b = got.get(key(x, y, z)); if (b != null) return b; Integer t = top.get(key(x, 0, z)); if (t == null) return null; int ty = t - yb; if (y > ty) return null; if (y < ty - 1 && y > 0) { int lo = ty; for (int[] dd : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { Integer tn = top.get(key(x + dd[0], 0, z + dd[1])); lo = Math.min(lo, tn == null ? 0 : tn - yb); } if (y < lo) return null; }   /* only the faces that show */ boolean pt = pit.get(key(x, 0, z)); return pt ? (y == ty ? "cobblestone" : "stone") : y == ty ? "grass_block" : y >= ty - 3 ? "dirt" : "stone"; };
      int ymax = 0; for (int t : top.values()) ymax = Math.max(ymax, t - yb); x0 = -hx; x1 = hx; y0 = 0; y1 = Math.max(ymax, Math.max(q.gateY(), q.shedY()) - yb + 9); z0 = -hz; z1 = cut ? 0 : hz;
      title = String.format("Cobble & Sons origin quarry (planet/Quarry), seed 1, %.1f km from %s: pit 120 x 80 m, three benches 10 m, stair in the end wall, pool, the first gate, office shed%s", Math.hypot(q.x() - w.metro().plans.values().stream().filter(pp -> pp.site.name.equals(q.city())).findFirst().get().cx, q.z() - w.metro().plans.values().stream().filter(pp -> pp.site.name.equals(q.city())).findFirst().get().cz) / 1000, q.city(), cut ? "; cut along its long axis" : ""); }
    else if (scene.equals("rail")) {   // 0.29.0 a main line where it bridges an Interstate near the spawn city, from Column.of (ground, track, deck, rails) as the generator lays it; args 4, 5: line id and metres along (defaults: RailTest's closest crossing)
      PlanetWorld w = new PlanetWorld(1); w.playerSpawn(); Roads rd = w.roads(); int id = a.length > 4 ? Integer.parseInt(a[4]) : 188985; double at = a.length > 5 ? Double.parseDouble(a[5]) : 4559; Roads.Route r = rd.route(id);
      int i0 = 0; for (int i = 0; i < r.n(); i++) if (Math.abs(r.along[i] - at) < Math.abs(r.along[i0] - at)) i0 = i; double[] lc = w.cube.locate(r.p(i0)); int f = (int) lc[0], cx = (int) Math.floor(lc[1]), cz = (int) Math.floor(lc[2]), H = 45;
      Map<Long, Column> cols = new HashMap<>(); int ymin = Integer.MAX_VALUE, ymax = Integer.MIN_VALUE; for (int x = -H; x <= H; x++) for (int z = -H; z <= H; z++) { Column c = Column.of(w, f, cx + x, cz + z); cols.put(key(x, 0, z), c); ymin = Math.min(ymin, c.surfaceY); ymax = Math.max(ymax, c.topY()); }
      final int yb = ymin - 4; src = (x, y, z) -> { Column c = cols.get(key(x, 0, z)); if (c == null) return null; int Y = y + yb; if (c.ovY != null) for (int k = 0; k < c.ovM.length; k++) if (Y >= c.ovY[2 * k] && Y <= c.ovY[2 * k + 1]) return mat(c.ovM[k]);
        if (Y == c.surfaceY) return mat(c.top); if (Y < c.surfaceY) { int lo = c.surfaceY; for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { Column n = cols.get(key(x + d[0], 0, z + d[1])); lo = Math.min(lo, n == null ? yb : n.surfaceY); } return Y >= lo || y == 0 ? (Y >= c.surfaceY - c.fillerDepth ? mat(c.filler) : mat(c.under)) : null; }
        if (c.waterY > c.surfaceY && Y <= c.waterY) return "water"; return null; };
      x0 = -H; x1 = H; y0 = 0; y1 = ymax - yb + 1; z0 = -H; z1 = H;
      title = String.format("Freight main line %d (planet/Roads RAIL), seed 1, %.0f m along: ties on ballast, a rail each side (and a bridge deck with walls where it crosses); from Column.of", id, r.along[i0]); }
    else throw new IllegalArgumentException("scene " + scene);
    render(src, x0, x1, y0, y1, z0, z1, S, view, title, a[1]); }
  static String mat(Column.Mat m) { return switch (m) { case GRASS -> "grass_block"; case ROAD_CONCRETE, DECK, LINING -> "light_gray_concrete"; case ASPHALT -> "gray_concrete"; case LINE_WHITE -> "white_concrete"; case LINE_YELLOW -> "yellow_concrete"; case RAIL -> "stone_brick_wall"; case TIE -> "dark_oak_planks"; case RAIL_TOP -> "heavy_weighted_pressure_plate"; case SNOW_LAYER -> "snow"; case REGOLITH -> "andesite"; case MARE_REGOLITH, BASALT -> "smooth_basalt"; case ANORTHOSITE -> "diorite"; case BASALT_ROCK -> "basalt"; case MAGMA_CRUST -> "magma_block"; case AIR -> null; default -> m.name().toLowerCase(); }; }
  public static void render(Src src, int x0, int x1, int y0, int y1, int z0, int z1, int S, int view, String title, String out) throws Exception {
    double c30 = Math.cos(Math.toRadians(30)), s30 = 0.5; int sgn = view == 0 ? 1 : -1;
    // boxes in 1/16 units, world frame: {x0, y0, z0, x1, y1, z1, rgb}
    java.util.List<double[]> bx = new ArrayList<>();
    for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) { String b = src.at(x, y, z); if (b == null || b.equals("air")) continue; int c = BlockColors.color(b); if (c < 0) continue;
      if (b.startsWith("shape:")) { String[] p = b.split(":", 4); for (int[] q : ShapeLib.boxes(ShapeLib.INDEX.get(p[1]), Integer.parseInt(p[2]))) bx.add(new double[]{x + q[0] / 16.0, y + q[1] / 16.0, z + q[2] / 16.0, x + q[3] / 16.0, y + q[4] / 16.0, z + q[5] / 16.0, c}); }
      else if (b.contains("pressure_plate")) bx.add(new double[]{x + 1 / 16.0, y, z + 1 / 16.0, x + 15 / 16.0, y + 1 / 16.0, z + 15 / 16.0, c});
      else if (b.startsWith("iron_bars") || b.contains("_pane")) bx.add(new double[]{x + 7 / 16.0, y, z + 7 / 16.0, x + 9 / 16.0, y + 1, z + 9 / 16.0, c});
      else bx.add(new double[]{x, y, z, x + 1, y + 1, z + 1, c}); }
    // project the corners to find the picture's size
    double mnx = 1e9, mxx = -1e9, mny = 1e9, mxy = -1e9; for (double[] q : bx) for (int k = 0; k < 8; k++) { double X = q[(k & 1) * 3], Y = q[1 + ((k >> 1) & 1) * 3], Z = q[2 + ((k >> 2) & 1) * 3]; double sx = (sgn * X - Z) * c30, sy = (sgn * X + Z) * s30 - Y; mnx = Math.min(mnx, sx); mxx = Math.max(mxx, sx); mny = Math.min(mny, sy); mxy = Math.max(mxy, sy); }
    int W = (int) ((mxx - mnx) * S) + 40, H = (int) ((mxy - mny) * S) + 70; BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB); Graphics2D g = im.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g.setColor(new Color(0xdfe8ef)); g.fillRect(0, 0, W, H);
    final double ox = -mnx * S + 20, oy = -mny * S + 50;
    // back to front: the viewer looks along (-sgn, -1, -1) roughly; draw by the far corner's depth
    bx.sort(Comparator.comparingDouble(q -> sgn * (sgn > 0 ? q[0] : q[3]) + q[1] + q[2]));
    for (double[] q : bx) { Color base = new Color((int) q[6]); double xs = sgn > 0 ? q[3] : q[0];   // the visible x face
      face(g, base, 1.0, ox, oy, S, c30, s30, sgn, new double[][]{{q[0], q[4], q[2]}, {q[3], q[4], q[2]}, {q[3], q[4], q[5]}, {q[0], q[4], q[5]}});   // top
      face(g, base, 0.66, ox, oy, S, c30, s30, sgn, new double[][]{{q[0], q[1], q[5]}, {q[3], q[1], q[5]}, {q[3], q[4], q[5]}, {q[0], q[4], q[5]}});   // +z
      face(g, base, 0.82, ox, oy, S, c30, s30, sgn, new double[][]{{xs, q[1], q[2]}, {xs, q[1], q[5]}, {xs, q[4], q[5]}, {xs, q[4], q[2]}}); }   // the x face toward the viewer
    g.setFont(new Font("SansSerif", Font.BOLD, 11)); for (Object[] lb : LABELS) { double X = (Double) lb[0] + 0.5, Y = (Double) lb[1], Z = (Double) lb[2] + 0.5; int sx = (int) Math.round(ox + (sgn * X - Z) * c30 * S), sy = (int) Math.round(oy + ((sgn * X + Z) * s30 - Y) * S);
      String t = (String) lb[3]; int tw2 = g.getFontMetrics().stringWidth(t); g.setColor(new Color(255, 255, 255, 220)); g.fillRect(sx - tw2 / 2 - 3, sy - 13, tw2 + 6, 15); g.setColor(Color.black); g.drawString(t, sx - tw2 / 2, sy - 2); g.drawLine(sx, sy, sx, sy + 6); }
    g.setColor(Color.black); g.setFont(new Font("SansSerif", Font.BOLD, 15)); g.drawString(title, 10, 22);
    g.setFont(new Font("SansSerif", Font.PLAIN, 12)); g.drawString("rendered from the generator's own block strings; shapes as their collision boxes (1/8 block); colours measured from the textures", 10, 40);
    g.dispose(); javax.imageio.ImageIO.write(im, "png", new java.io.File(out)); System.out.println("wrote " + out + " (" + bx.size() + " boxes); missing colours: " + BlockColors.missing()); }
  static void face(Graphics2D g, Color c, double sh, double ox, double oy, int S, double c30, double s30, int sgn, double[][] p) {
    int[] xs = new int[4], ys = new int[4]; for (int i = 0; i < 4; i++) { xs[i] = (int) Math.round(ox + (sgn * p[i][0] - p[i][2]) * c30 * S); ys[i] = (int) Math.round(oy + ((sgn * p[i][0] + p[i][2]) * s30 - p[i][1]) * S); }
    g.setColor(new Color((int) Math.min(255, c.getRed() * sh), (int) Math.min(255, c.getGreen() * sh), (int) Math.min(255, c.getBlue() * sh))); g.fillPolygon(xs, ys, 4); }
}
