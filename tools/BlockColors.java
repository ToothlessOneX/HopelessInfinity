import java.awt.image.BufferedImage; import java.util.*; import java.util.zip.*;
/** BlockColors: the colour of a block state, MEASURED as the mean of its texture's opaque pixels in the game's own client jar (1.20.1), not
 *  picked by hand. Stairs, slabs, walls, panes, carpets and potted plants map to the texture they show. Unknown blocks: magenta, and they
 *  are listed by missing(). Saved tool (used by TowerView). */
public class BlockColors {
  static final String JAR = "/mnt/user-data/uploads/Install/versions/1.20.1/1.20.1.jar"; static ZipFile zip; static final Map<String, Integer> cache = new HashMap<>(); static final Set<String> missing = new TreeSet<>();
  public static int color(String state) { if (state.startsWith("sign:")) return color(state.substring(5, state.indexOf('|')));   /* 0.27.4 a sign: its wood */
    if (state.startsWith("shape:")) return color(state.substring(state.indexOf(':', state.indexOf(':', 6) + 1) + 1));   /* 0.26.3 a shaped block: its material's colour */
    String n = state.contains("[") ? state.substring(0, state.indexOf('[')) : state; if (n.startsWith("minecraft:")) n = n.substring(10);
    if (n.startsWith("hopelessinfinity:")) { Integer c0 = cache.get(n); if (c0 != null) return c0; int c1 = own(n.substring(17)); if (c1 == -2) { missing.add(n); c1 = 0xff00ff; } cache.put(n, c1); return c1; }
    if (n.equals("air") || n.equals("light") || n.equals("cave_air")) return -1; Integer c = cache.get(n); if (c != null) return c; c = tinted(n); if (c == null) c = measure(n); cache.put(n, c); return c; }
  /** 0.28.0 the blocks the game tints at run time (their textures are grey): the texture's mean times the tint. Grass: the game's own grass
   *  colormap at plains (temperature 0.8, downfall 0.4, its formula); water: plains' water_color 4159204 (0x3F76E4, the game's biome data);
   *  lily pad: the game's fixed 0x208030. */
  static Integer tinted(String n) { try { if (zip == null) zip = new ZipFile(JAR); String tex; int tint;
      if (n.equals("grass_block")) { tex = "grass_block_top"; BufferedImage cm = javax.imageio.ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/colormap/grass.png"))); double t = 0.8, d = 0.4 * t; tint = cm.getRGB((int) ((1 - t) * 255), (int) ((1 - d) * 255)) & 0xffffff; }
      else if (n.equals("water")) { tex = "water_still"; tint = 0x3F76E4; } else if (n.equals("lily_pad")) { tex = "lily_pad"; tint = 0x208030; } else return null;
      BufferedImage im = javax.imageio.ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/block/" + tex + ".png"))); int m = mean(im.getSubimage(0, 0, im.getWidth(), Math.min(im.getWidth(), im.getHeight())));
      return ((((m >> 16) & 255) * ((tint >> 16) & 255) / 255) << 16) | ((((m >> 8) & 255) * ((tint >> 8) & 255) / 255) << 8) | ((m & 255) * (tint & 255) / 255); } catch (Exception e) { return null; } }
  public static Set<String> missing() { return missing; }
  static final String OWN = "src/main/resources/assets/hopelessinfinity/";
  /** 0.26.1 our own blocks: the first texture of the first model their block state names (following parents), measured the same way. */
  static int own(String n) { try { String js = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(OWN + "blockstates/" + n + ".json")));
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"model\"\\s*:\\s*\"([^\"]+)\"").matcher(js); if (!m.find()) return -2; String model = m.group(1);
      for (int d = 0; d < 6 && model != null; d++) { String[] p = model.contains(":") ? model.split(":", 2) : new String[]{"minecraft", model}; String mj;
        if (p[0].equals("hopelessinfinity")) mj = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(OWN + "models/" + p[1] + ".json"))); else { if (zip == null) zip = new ZipFile(JAR); ZipEntry e = zip.getEntry("assets/minecraft/models/" + p[1] + ".json"); if (e == null) return -2; mj = new String(zip.getInputStream(e).readAllBytes()); }
        java.util.regex.Matcher t = java.util.regex.Pattern.compile("\"(?!parent)[a-z0-9_]+\"\\s*:\\s*\"((?:[a-z0-9_]+:)?(?:block|item)/[a-z0-9_/]+)\"").matcher(mj);
        if (t.find()) { String tx = t.group(1); String[] q = tx.contains(":") ? tx.split(":", 2) : new String[]{"minecraft", tx}; BufferedImage im;
          if (q[0].equals("hopelessinfinity")) im = javax.imageio.ImageIO.read(new java.io.File(OWN + "textures/" + q[1] + ".png")); else { if (zip == null) zip = new ZipFile(JAR); ZipEntry e = zip.getEntry("assets/minecraft/textures/" + q[1] + ".png"); if (e == null) return -2; im = javax.imageio.ImageIO.read(zip.getInputStream(e)); }
          return mean(im); }
        java.util.regex.Matcher pa = java.util.regex.Pattern.compile("\"parent\"\\s*:\\s*\"([^\"]+)\"").matcher(mj); model = pa.find() ? pa.group(1) : null; } }
    catch (Exception ex) { } return -2; }
  static int mean(BufferedImage im) { long r = 0, g = 0, bl = 0, w = 0; int H = Math.min(im.getHeight(), im.getWidth());
    for (int y = 0; y < H; y++) for (int x = 0; x < im.getWidth(); x++) { int p = im.getRGB(x, y), a = p >>> 24; if (a < 16) continue; r += ((p >> 16) & 255) * a; g += ((p >> 8) & 255) * a; bl += (p & 255) * a; w += a; }
    return w == 0 ? -2 : (int) (r / w) << 16 | (int) (g / w) << 8 | (int) (bl / w); }
  static int measure(String n) {
    List<String> cand = new ArrayList<>(); String b = n;
    if (n.startsWith("potted_")) { cand.add(n + "_top"); cand.add(n + "_side"); b = n.substring(7); }
    if (n.endsWith("_wall_sign")) b = n.replace("_wall_sign", "_planks"); else if (n.endsWith("_sign")) b = n.replace("_sign", "_planks");   /* 0.27.4 signs: their wood */
    if (n.endsWith("_carpet")) b = n.replace("_carpet", "_wool"); if (n.endsWith("_banner")) b = n.replace("_banner", "_wool");   /* 0.28.0 banners: their wool */ if (n.endsWith("_bed")) b = n.replace("_bed", "_wool"); if (n.equals("moss_carpet")) b = "moss_block";
    if (n.endsWith("_pane")) b = n.substring(0, n.length() - 5); if (n.equals("glass_pane")) b = "glass"; if (n.equals("heavy_weighted_pressure_plate")) b = "iron_block"; if (n.equals("light_weighted_pressure_plate")) b = "gold_block";   /* 0.29.0 the weighted plates: their metal blocks (the game draws them so) */
    for (String suf : new String[]{"_stairs", "_slab", "_wall", "_fence_gate", "_fence", "_button", "_pressure_plate"}) if (b.endsWith(suf)) { b = b.substring(0, b.length() - suf.length()); break; }
    if (b.equals("smooth_quartz")) b = "quartz_block_bottom"; if (b.equals("smooth_stone")) b = "smooth_stone"; if (b.startsWith("smooth_sandstone")) b = "sandstone_top"; if (b.equals("quartz")) b = "quartz_block_side";
    if (b.endsWith("brick") || b.endsWith("_tile")) b = b + "s"; if (b.equals("oak") || b.equals("spruce") || b.equals("birch") || b.equals("dark_oak") || b.equals("jungle") || b.equals("acacia")) b = b + "_planks";
    cand.add(b); cand.add(b + "_top"); cand.add(b + "_side"); cand.add(b + "_front"); cand.add(b + "_block"); if (b.endsWith("s")) cand.add(b.substring(0, b.length() - 1));
    try { if (zip == null) zip = new ZipFile(JAR);
      for (String t : cand) { ZipEntry e = zip.getEntry("assets/minecraft/textures/block/" + t + ".png"); if (e == null) continue; BufferedImage im = javax.imageio.ImageIO.read(zip.getInputStream(e));
        long r = 0, g = 0, bl = 0, w = 0; int H = Math.min(im.getHeight(), im.getWidth());   // first animation frame only
        for (int y = 0; y < H; y++) for (int x = 0; x < im.getWidth(); x++) { int p = im.getRGB(x, y), a = p >>> 24; if (a < 16) continue; r += ((p >> 16) & 255) * a; g += ((p >> 8) & 255) * a; bl += (p & 255) * a; w += a; }
        if (w == 0) continue; return (int) (r / w) << 16 | (int) (g / w) << 8 | (int) (bl / w); } }
    catch (Exception ex) { }
    missing.add(n); return 0xff00ff; }
}
