import com.hopeless.infinity.planet.*; import com.hopeless.infinity.slope.*; import java.awt.image.BufferedImage; import java.io.*; import java.util.*; import java.util.zip.*;
/** WorldView: 0.29.2 ONE RENDERER FOR THE WHOLE WORLD (owner, 2026-11-07: "combine all of your minecraft renderers into one tool so ... you
 *  and me can both get a full picture of what things actually look like in game"). It builds the blocks of an area with the generator's own
 *  code (planet/ChunkFill: the columns, the materials' blocks, caves, slopes, town buildings, cities, quarry, billboards: what
 *  world/PlanetGenerator places), then draws them with the game's own block models and textures (blockstates, models and textures read from
 *  the 1.20.1 client jar and the mod's assets: stairs, slabs, doors, panes, fences as the game shapes them; terrain slopes as their mesh,
 *  SlopeMesh; shaped blocks as their boxes, ShapeLib) by ray casting, with the game's flat face shading (up 1.0, down 0.5, north/south 0.8,
 *  east/west 0.6), sky light that dims under roofs (15 less one a block, the game's brightness curve), translucent water and glass, the
 *  game's sky and distance fog. One tool, three cameras:
 *    persp  in-game view: a camera at a place, yaw and pitch as the game's F3 shows them (yaw 0 = south, 90 = west; pitch + = down), FOV 70;
 *    iso    an isometric view (orthographic) from yaw/pitch;
 *    top    a plan (orthographic, straight down).
 *  Places: spawn (the player's spawn, facing the HQ door), hq, gate, quarry, town:<name>, station:<town>, rail:<line>:<metres>, city:<name>,
 *  f,x,z (face block coordinates). Usage: WorldView seed place view out.png [w h] [key=value ...]: radius (blocks of world built round the
 *  target, default 160), dist (camera distance for aerial views), yaw, pitch, cy (camera height over ground), fov. Saved tool. */
public class WorldView {
  // ---------------------------------------------------------------- minimal JSON
  static int jp; static String js;
  static Object json(String s) { js = s; jp = 0; return val(); }
  static void ws() { while (jp < js.length() && Character.isWhitespace(js.charAt(jp))) jp++; }
  static Object val() { ws(); char c = js.charAt(jp);
    if (c == '{') { jp++; Map<String, Object> m = new LinkedHashMap<>(); ws(); if (js.charAt(jp) == '}') { jp++; return m; } while (true) { ws(); String k = (String) val(); ws(); jp++; m.put(k, val()); ws(); char d = js.charAt(jp++); if (d == '}') return m; } }
    if (c == '[') { jp++; List<Object> l = new ArrayList<>(); ws(); if (js.charAt(jp) == ']') { jp++; return l; } while (true) { l.add(val()); ws(); char d = js.charAt(jp++); if (d == ']') return l; } }
    if (c == '"') { StringBuilder b = new StringBuilder(); jp++; while (js.charAt(jp) != '"') { char ch = js.charAt(jp++); if (ch == '\\') { char e = js.charAt(jp++); b.append(e == 'n' ? '\n' : e == 'u' ? (char) Integer.parseInt(js.substring(jp, (jp += 4)), 16) : e); } else b.append(ch); } jp++; return b.toString(); }
    if (js.startsWith("true", jp)) { jp += 4; return true; } if (js.startsWith("false", jp)) { jp += 5; return false; } if (js.startsWith("null", jp)) { jp += 4; return null; }
    int s0 = jp; while (jp < js.length() && "+-0123456789.eE".indexOf(js.charAt(jp)) >= 0) jp++; return Double.parseDouble(js.substring(s0, jp)); }
  // ---------------------------------------------------------------- assets
  /** The game's client jar (vanilla assets): -Dwv.mcjar or WV_MCJAR, else the usual places (CurseForge, the vanilla launcher, this workspace). */
  static String JAR = findMcJar(); static final String OWN = new File("src/main/resources/assets/hopelessinfinity/").isDirectory() ? "src/main/resources/assets/hopelessinfinity/" : "/home/claude/infinity/src/main/resources/assets/hopelessinfinity/";   /* the source tree from any working folder (else the mod jar on the class path) */ static ZipFile zip;
  static String findMcJar() { String e = System.getProperty("wv.mcjar", System.getenv("WV_MCJAR")); if (e != null) return e; String home = System.getProperty("user.home"), app = System.getenv("APPDATA");
    for (String c : new String[]{"/mnt/user-data/uploads/Install/versions/1.20.1/1.20.1.jar", home + "/curseforge/minecraft/Install/versions/1.20.1/1.20.1.jar", app == null ? "" : app + "/.minecraft/versions/1.20.1/1.20.1.jar", home + "/.minecraft/versions/1.20.1/1.20.1.jar"}) if (new File(c).isFile()) return c; return null; }
  /** The mod's own file: from the source tree when it is here, else from the mod jar on the class path (the packaged tool). */
  static byte[] own(String path) throws IOException { File f = new File(OWN + path); if (f.exists()) return java.nio.file.Files.readAllBytes(f.toPath());
    try (InputStream in = WorldView.class.getClassLoader().getResourceAsStream("assets/hopelessinfinity/" + path)) { return in == null ? null : in.readAllBytes(); } }
  /** 0.29.8 (SaveView merged in) the save's mod jars: assets of any other mod, after the client jar (modded worlds drawn from their saves). */
  static final List<ZipFile> MODJARS = new ArrayList<>(); static String VERSION = "1.20.1";
  static void openModJars(File mods) { File[] js = mods == null ? null : mods.listFiles((d, nm) -> nm.endsWith(".jar")); int n = 0; if (js != null) { Arrays.sort(js); for (File j : js) try { MODJARS.add(new ZipFile(j)); n++; } catch (IOException ex) { System.out.println("mod jar not readable: " + j.getName()); } }
    System.out.println("assets: client jar " + JAR + (mods == null ? "; no mods folder" : "; " + n + " mod jars in " + mods)); }
  /** Any asset's bytes: the client jar, then the mod jars. */
  static byte[] assetBytes(String ns, String path) { try { if (zip == null) zip = new ZipFile(JAR); ZipEntry e = zip.getEntry("assets/" + ns + "/" + path); if (e != null) try (InputStream in = zip.getInputStream(e)) { return in.readAllBytes(); } } catch (IOException ex) { } return modAsset(ns, path); }
  static byte[] modAsset(String ns, String path) { String k = "assets/" + ns + "/" + path; synchronized (MODJARS) { for (ZipFile z : MODJARS) { ZipEntry e = z.getEntry(k); if (e != null) try (InputStream in = z.getInputStream(e)) { return in.readAllBytes(); } catch (IOException ex) { } } } return null; }
  /** The client jar of a version: -Dwv.mcjar or WV_MCJAR, else versions/<v>/<v>.jar in CurseForge, .minecraft, the Store launcher, the instance. */
  static String findMcJar(String v, File inst) { String e = System.getProperty("wv.mcjar", System.getenv("WV_MCJAR")); if (e != null) return e; String home = System.getProperty("user.home"), app = System.getenv("APPDATA"), loc = System.getenv("LOCALAPPDATA");
    List<String> c = new ArrayList<>(List.of("/mnt/user-data/uploads/Install/versions/" + v + "/" + v + ".jar", home + "/curseforge/minecraft/Install/versions/" + v + "/" + v + ".jar", home + "/.minecraft/versions/" + v + "/" + v + ".jar"));
    if (app != null) c.add(app + "/.minecraft/versions/" + v + "/" + v + ".jar"); if (loc != null) c.add(loc + "/Packages/Microsoft.4297127D64EC6_8wekyb3d8bbwe/LocalCache/Local/versions/" + v + "/" + v + ".jar");
    if (inst != null) { c.add(inst + "/versions/" + v + "/" + v + ".jar"); c.add(inst.getParent() + "/versions/" + v + "/" + v + ".jar"); }
    for (String x : c) if (new File(x).isFile()) return x; System.out.println("client jar " + v + " not found; looked in: " + c); return null; }
  static String asset(String ns, String path) { try { if (ns.equals("hopelessinfinity")) { byte[] b = own(path); if (b == null) b = modAsset(ns, path); return b == null ? null : new String(b); }
      if (zip == null) zip = new ZipFile(JAR); ZipEntry e = zip.getEntry("assets/" + ns + "/" + path); if (e == null) { byte[] b = modAsset(ns, path); return b == null ? null : new String(b); } return new String(zip.getInputStream(e).readAllBytes()); } catch (IOException ex) { return null; } }
  static final Map<String, int[]> TEX = new HashMap<>(); static final Set<String> MISSING = new TreeSet<>();
  /** A texture as 16 x 16 ARGB (an animated strip's first frame; larger textures sampled down), or null. */
  static synchronized int[] tex(String ref) { if (ref == null) return null; String r = ref.contains(":") ? ref : "minecraft:" + ref; return TEX.computeIfAbsent(r, k -> { String[] p = k.split(":", 2);
      try { BufferedImage im = null; if (p[0].equals("hopelessinfinity")) { byte[] b = own("textures/" + p[1] + ".png"); if (b != null) im = javax.imageio.ImageIO.read(new ByteArrayInputStream(b)); } else { if (zip == null) zip = new ZipFile(JAR); ZipEntry e = zip.getEntry("assets/" + p[0] + "/textures/" + p[1] + ".png"); if (e != null) im = javax.imageio.ImageIO.read(zip.getInputStream(e)); else { byte[] b = modAsset(p[0], "textures/" + p[1] + ".png"); if (b != null) im = javax.imageio.ImageIO.read(new ByteArrayInputStream(b)); } }
        if (im == null) { MISSING.add(k); return null; } int W = im.getWidth(); int[] t = new int[256]; for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) t[y * 16 + x] = im.getRGB(x * W / 16, y * W / 16); return t; } catch (Exception ex) { MISSING.add(k); return null; } }); }
  // ---------------------------------------------------------------- block looks
  /** A face of a block's shape: a quad (or triangle: p3 == p2) in block units 0..1 with its UVs (0..1), texture, tint, normal. */
  static final class Quad { double[][] p = new double[4][]; double[][] uv = new double[4][]; int[] tex; int tint = -1; double[] n; }
  static final class Look { boolean glow; List<Quad> quads = new ArrayList<>(); boolean full, opaque, water, lava, air; int[] fullTex = new int[6]; int[][] faceTex = new int[6][]; int[] faceTint = {-1, -1, -1, -1, -1, -1}; }
  static final Map<String, Look> LOOKS = new HashMap<>(); static final Set<String> NO_MODEL = new TreeSet<>(), BUILTIN = new TreeSet<>();
  static final Set<String> EMPTY_BY_DESIGN = new HashSet<>();   /* blocks whose model has no elements on purpose (the top of a two-block model) */
  static int[] BTINT;   /* each column's grass tint */
  static int[] FTINT;   /* each column's foliage colour (from a save's biomes) */
  static int gt(int tint, int x, int z) { int[] m = tint == GRASS ? BTINT : tint == FOLIAGE ? FTINT : null; if (m == null) return tint; int i = x - X0, k = z - Z0; return i < 0 || k < 0 || i >= NX || k >= NZ ? tint : m[k * NX + i]; }
  static final int GRASS = 0x91BD59, FOLIAGE = 0x77AB2F, WATER = 0x3F76E4;   // the plains biome's colours (the game's colormaps at 0.8, 0.4) and water
  static synchronized Look look(String b) { Look L = LOOKS.get(b); if (L == null) { L = makeLook(b); LOOKS.put(b, L); } return L; }   // not computeIfAbsent: a slope's or shaped block's look asks for its material's
  static Look makeLook(String b) { Look L = new Look();
    if (b.equals("air") || b.equals("cave_air") || b.equals("void_air")) { L.air = true; return L; }
    if (b.equals("water") || b.startsWith("water[")) { L.water = true; return L; } if (b.equals("lava") || b.startsWith("lava[")) { L.lava = true; L.opaque = true; return L; }
    if (b.startsWith("sign:")) { String w = b.substring(5, b.indexOf('|')); String base = w.replaceAll("\\[.*", ""), wood = base.replace("_wall_sign", "").replace("_sign", ""); boolean wall = base.contains("wall");   // a sign: its board (the game draws it with a block-entity renderer)
      box(L, wall ? 0 : 7 / 16.0, wall ? 4.5 / 16 : 7 / 16.0, wall ? 14 / 16.0 : 7 / 16.0, wall ? 16 / 16.0 : 9 / 16.0, wall ? 12.5 / 16 : 16 / 16.0, wall ? 16 / 16.0 : 9 / 16.0, tex("block/" + wood + "_planks")); return L; }
    if (b.startsWith("shape:")) { String[] q = b.split(":", 4); Integer sh = ShapeLib.INDEX.get(q[1]); int turn = Integer.parseInt(q[2]); Look m = look(q[3]); if (sh == null) { NO_MODEL.add("shape " + q[1]); return m; }   // a shaped block: its mesh (ShapeLib.mesh, what the client draws) in its material
      for (ShapeLib.Face fa : ShapeLib.mesh(sh, turn)) { double[] n = fa.n; int d = dirOf(n); int ax = Math.abs(n[1]) >= Math.abs(n[0]) && Math.abs(n[1]) >= Math.abs(n[2]) ? 1 : Math.abs(n[0]) >= Math.abs(n[2]) ? 0 : 2; int[] t = m.faceTex[ax == 1 ? (n[1] > 0 ? 1 : 0) : 2]; if (t == null) t = m.faceTex[1];
        for (int k = 1; k + 1 < fa.v.length; k++) { Quad qd = new Quad(); double[][] tri = {fa.v[0], fa.v[k], fa.v[k + 1], fa.v[k + 1]}; for (int j = 0; j < 4; j++) { double[] p = tri[j]; qd.p[j] = new double[]{p[0], p[1], p[2]}; qd.uv[j] = ax == 1 ? new double[]{p[0], p[2]} : ax == 0 ? new double[]{p[2], 1 - p[1]} : new double[]{p[0], 1 - p[1]}; }
          qd.n = n; qd.tex = t; qd.tint = m.faceTint[1]; L.quads.add(qd); } } return L; }
    String ns = "minecraft", nm = b; int br = nm.indexOf('['); Map<String, String> props = new HashMap<>(); if (br >= 0) { for (String kv : nm.substring(br + 1, nm.length() - 1).split(",")) { String[] e = kv.split("="); if (e.length == 2) props.put(e[0].trim(), e[1].trim()); } nm = nm.substring(0, br); }
    if (nm.contains(":")) { ns = nm.substring(0, nm.indexOf(':')); nm = nm.substring(nm.indexOf(':') + 1); }
    if (ns.equals("hopelessinfinity") && nm.startsWith("slope_")) { slope(L, nm, Integer.parseInt(props.getOrDefault("corners", "0"))); return L; }
    // blocks the game draws with block-entity renderers (no block model): their shapes from the vanilla renderers' boxes, the colour's wool
    if (ns.equals("minecraft") && nm.endsWith("_banner")) { boolean wall = nm.endsWith("_wall_banner"); String col = nm.replace("_wall_banner", "").replace("_banner", ""); int[] wool = tex("block/" + col + "_wool"), pole = tex("block/oak_planks");
      if (wall) { box(L, 2 / 16.0, -8 / 16.0, 14 / 16.0, 14 / 16.0, 24 / 16.0, 15 / 16.0, wool); rotY(L, props.getOrDefault("facing", "north")); }
      else { box(L, 7 / 16.0, 0, 7 / 16.0, 9 / 16.0, 42 / 16.0, 9 / 16.0, pole); box(L, -2 / 16.0, 40 / 16.0, 7 / 16.0, 18 / 16.0, 42 / 16.0, 9 / 16.0, pole); box(L, -2 / 16.0, 12 / 16.0, 6 / 16.0, 18 / 16.0, 40 / 16.0, 7 / 16.0, wool); rotY(L, Integer.parseInt(rot16(props.get("rotation"))) * 360 / 16 + 180); }
      BUILTIN.add(nm); return L; }
    if (ns.equals("minecraft") && nm.endsWith("_bed")) { String col = nm.replace("_bed", ""); box(L, 0, 3 / 16.0, 0, 1, 9 / 16.0, 1, tex("block/" + col + "_wool")); for (double[] lg : new double[][]{{0, 0}, {13, 0}, {0, 13}, {13, 13}}) box(L, lg[0] / 16, 0, lg[1] / 16, (lg[0] + 3) / 16, 3 / 16.0, (lg[1] + 3) / 16, tex("block/oak_planks")); BUILTIN.add(nm); return L; }
    if (ns.equals("minecraft") && (nm.equals("chest") || nm.equals("trapped_chest") || nm.equals("ender_chest"))) { box(L, 1 / 16.0, 0, 1 / 16.0, 15 / 16.0, 14 / 16.0, 15 / 16.0, tex(nm.equals("ender_chest") ? "block/obsidian" : "block/oak_planks")); BUILTIN.add(nm); return L; }
    if (ns.equals("minecraft") && nm.endsWith("_wall") && !props.containsKey("up")) { props = new HashMap<>(props); props.put("up", "true"); }   /* a wall's default state has its post (up=true) */
    String bs = asset(ns, "blockstates/" + nm + ".json"); if (bs == null) { NO_MODEL.add(ns + ":" + nm); box(L, 0, 0, 0, 1, 1, 1, null); L.full = true; L.opaque = true; return L; }
    @SuppressWarnings("unchecked") Map<String, Object> root = (Map<String, Object>) json(bs); List<Map<String, Object>> picks = new ArrayList<>();
    if (root.containsKey("variants")) { @SuppressWarnings("unchecked") Map<String, Object> vs = (Map<String, Object>) root.get("variants"); Object best = null; int bestN = -1;
      for (var en : vs.entrySet()) { int n = 0; boolean ok = true; if (!en.getKey().isEmpty()) for (String kv : en.getKey().split(",")) { String[] e = kv.split("="); String have = props.get(e[0]); if (have == null) continue; if (have.equals(e[1])) n++; else { ok = false; break; } } if (ok && n > bestN) { bestN = n; best = en.getValue(); } }
      if (best instanceof List<?> l) best = l.get(0); if (best != null) { @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) best; picks.add(m); } }
    else if (root.containsKey("multipart")) for (Object o : (List<?>) root.get("multipart")) { @SuppressWarnings("unchecked") Map<String, Object> part = (Map<String, Object>) o; Object when = part.get("when"); if (when != null && !matches(when, props)) continue; Object ap = part.get("apply"); if (ap instanceof List<?> l) ap = l.get(0); @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) ap; picks.add(m); }
    for (Map<String, Object> pk : picks) model(L, (String) pk.get("model"), num(pk.get("x")), num(pk.get("y")), ns + ":" + nm);
    if (L.quads.isEmpty() && !EMPTY_BY_DESIGN.contains(ns + ":" + nm)) NO_MODEL.add(ns + ":" + nm);
    return L; }
  /** Turn a look's quads about the vertical axis: facing north (0), east, south, west; or a banner's rotation in 16ths. */
  static void rotY(Look L, String facing) { int d = switch (facing) { case "east" -> 90; case "south" -> 180; case "west" -> 270; default -> 0; }; for (Quad q : L.quads) rotate(q, 0, d); }
  static void rotY(Look L, int deg) { if (deg == 0) return; double a = Math.toRadians(deg), c = Math.cos(a), sn = Math.sin(a); for (Quad q : L.quads) { for (int k = 0; k < 4; k++) { double x = q.p[k][0] - 0.5, z = q.p[k][2] - 0.5; q.p[k] = new double[]{0.5 + x * c - z * sn, q.p[k][1], 0.5 + x * sn + z * c}; } double nx = q.n[0], nz = q.n[2]; q.n = new double[]{nx * c - nz * sn, q.n[1], nx * sn + nz * c}; } }
  static String rot16(String r) { return r == null ? "0" : r; }
  static void rotY(Look L, String r16, boolean dummy) { }
  static int num(Object o) { return o == null ? 0 : ((Double) o).intValue(); }
  @SuppressWarnings("unchecked") static boolean matches(Object when, Map<String, String> props) { Map<String, Object> w = (Map<String, Object>) when;
    if (w.containsKey("OR")) { for (Object o : (List<?>) w.get("OR")) if (matches(o, props)) return true; return false; } if (w.containsKey("AND")) { for (Object o : (List<?>) w.get("AND")) if (!matches(o, props)) return false; return true; }
    for (var en : w.entrySet()) { String have = props.get(en.getKey()); String want = String.valueOf(en.getValue()); if (have == null) { if (!want.equals("false") && !want.equals("none")) return false; continue; } boolean any = false; for (String v : want.split("\\|")) if (v.equals(have)) any = true; if (!any) return false; } return true; }
  /** A model (parents followed for textures and elements), rotated x then y by multiples of 90, into L. */
  @SuppressWarnings("unchecked") static void model(Look L, String ref, int rx, int ry, String blockName) {
    Map<String, String> texs = new HashMap<>(); List<Object> elems = null; String r = ref;
    for (int depth = 0; r != null && depth < 12; depth++) { String[] p = (r.contains(":") ? r : "minecraft:" + r).split(":", 2); String src = asset(p[0], "models/" + p[1] + ".json"); if (src == null) break; Map<String, Object> m = (Map<String, Object>) json(src);
      if ("forge:obj".equals(m.get("loader"))) { Map<String, String> tt = new HashMap<>(); if (m.containsKey("textures")) for (var en : ((Map<String, Object>) m.get("textures")).entrySet()) tt.put(en.getKey(), (String) en.getValue()); obj(L, (String) m.get("model"), tt, !Boolean.FALSE.equals(m.get("flip_v")), rx, ry); return; }
      if (m.containsKey("textures")) for (var en : ((Map<String, Object>) m.get("textures")).entrySet()) texs.putIfAbsent(en.getKey(), (String) en.getValue()); if (elems == null && m.containsKey("elements")) elems = (List<Object>) m.get("elements"); if (elems != null && elems.isEmpty()) { String bn = blockName.replaceAll("\\[.*", ""); EMPTY_BY_DESIGN.add(bn.contains(":") ? bn : "minecraft:" + bn); } r = (String) m.get("parent"); }
    if (elems == null) { String pt = resolve(texs, "#particle"); if (pt != null) { int[] t = tex(pt); box(L, 0, 0, 0, 1, 1, 1, t); L.full = true; L.opaque = true; Arrays.fill(L.faceTex, t); BUILTIN.add(blockName); } return; }   // a block drawn by a block-entity renderer (banners, chests, beds): its particle texture on a cube (marked in the log)
    for (Object eo : elems) { Map<String, Object> e = (Map<String, Object>) eo; List<Object> f = (List<Object>) e.get("from"), t = (List<Object>) e.get("to"); double x0 = (Double) f.get(0) / 16, y0 = (Double) f.get(1) / 16, z0 = (Double) f.get(2) / 16, x1 = (Double) t.get(0) / 16, y1 = (Double) t.get(1) / 16, z1 = (Double) t.get(2) / 16;
      if (e.containsKey("rotation")) { Map<String, Object> rot = (Map<String, Object>) e.get("rotation"); double ang = (Double) rot.get("angle"); if (Math.abs(ang) > 1) { /* angled elements (crossed plants): drawn as their box */ } }
      Map<String, Object> faces = (Map<String, Object>) e.get("faces"); if (faces == null) continue;
      for (var fe : faces.entrySet()) { Map<String, Object> fd = (Map<String, Object>) fe.getValue(); int[] tx = tex(resolve(texs, (String) fd.get("texture"))); if (tx == null) continue; List<Object> uvl = (List<Object>) fd.get("uv");
        Quad q = faceQuad(fe.getKey(), x0, y0, z0, x1, y1, z1, uvl); q.tex = tx; if (fd.containsKey("tintindex")) q.tint = tint(blockName);
        rotate(q, rx, ry); L.quads.add(q); } }
    // a full cube (the first element spans the block; a second may be an overlay, as grass's): its faces for the fast path and for slopes
    if (!elems.isEmpty()) { Map<String, Object> e = (Map<String, Object>) elems.get(0); List<Object> f = (List<Object>) e.get("from"), t = (List<Object>) e.get("to"); if ((Double) f.get(0) == 0 && (Double) f.get(1) == 0 && (Double) f.get(2) == 0 && (Double) t.get(0) == 16 && (Double) t.get(1) == 16 && (Double) t.get(2) == 16) { L.full = true; L.opaque = true; int nFirst = 0;
      Map<String, Object> faces = (Map<String, Object>) e.get("faces"); if (faces != null) nFirst = faces.size();
      for (int i = 0; i < Math.min(nFirst, L.quads.size()); i++) { Quad q = L.quads.get(i); int d = dirOf(q.n); if (d >= 0) { L.faceTex[d] = q.tex; L.faceTint[d] = q.tint; if (hasAlpha(q.tex)) L.opaque = false; } }
      if (nFirst < 6) L.opaque = false; } } }
  /** A Forge OBJ model (blocks in block units; may reach past its cell: drawn in every cell it covers, see OVER): its faces as triangles,
   *  each material's texture from the .mtl's map_Kd ("#key" into the model's textures). */
  static void obj(Look L, String ref, Map<String, String> texs, boolean flipV, int rx, int ry) { String[] p = ref.split(":", 2); String src = asset(p[0], p[1]); if (src == null) { NO_MODEL.add(ref); return; }
    String dir = p[1].substring(0, p[1].lastIndexOf('/') + 1); Map<String, int[]> mats = new HashMap<>(); List<double[]> v = new ArrayList<>(), vt = new ArrayList<>(); int[] cur = null;
    for (String line : src.split("\n")) { String[] q = line.trim().split("\\s+"); if (q.length == 0) continue;
      switch (q[0]) { case "mtllib" -> { String mt = asset(p[0], dir + q[1]); if (mt != null) { String nm = null; for (String ml : mt.split("\n")) { String[] w = ml.trim().split("\\s+"); if (w[0].equals("newmtl")) nm = w[1]; else if (w[0].equals("map_Kd") && nm != null) mats.put(nm, tex(resolve(texs, w[1]))); } } }
        case "v" -> v.add(new double[]{Double.parseDouble(q[1]), Double.parseDouble(q[2]), Double.parseDouble(q[3])}); case "vt" -> vt.add(new double[]{Double.parseDouble(q[1]), Double.parseDouble(q[2])});
        case "usemtl" -> cur = mats.get(q[1]);
        case "f" -> { int n = q.length - 1; int[] vi = new int[n], ti = new int[n]; for (int k = 0; k < n; k++) { String[] e = q[k + 1].split("/"); vi[k] = Integer.parseInt(e[0]) - 1; ti[k] = e.length > 1 && !e[1].isEmpty() ? Integer.parseInt(e[1]) - 1 : -1; }
          for (int k = 1; k + 1 < n; k++) { Quad qd = new Quad(); int[] id = {0, k, k + 1, k + 1}; for (int j = 0; j < 4; j++) { qd.p[j] = v.get(vi[id[j]]).clone(); double[] uv = ti[id[j]] >= 0 ? vt.get(ti[id[j]]) : new double[]{0, 0}; qd.uv[j] = new double[]{uv[0] - Math.floor(uv[0]), flipV ? 1 - (uv[1] - Math.floor(uv[1])) : uv[1] - Math.floor(uv[1])}; }
            double[] nn = cross(sub(qd.p[1], qd.p[0]), sub(qd.p[2], qd.p[0])); double l = Math.sqrt(dot(nn, nn)); if (l < 1e-12) continue; qd.n = new double[]{nn[0] / l, nn[1] / l, nn[2] / l}; qd.tex = cur; rotate(qd, rx, ry); L.quads.add(qd); } }
        default -> { } } } }
  static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
  static boolean hasAlpha(int[] t) { for (int c : t) if ((c >>> 24) < 250) return true; return false; }
  static String resolve(Map<String, String> texs, String r) { for (int i = 0; r != null && r.startsWith("#") && i < 10; i++) r = texs.get(r.substring(1)); return r; }
  static int tint(String n) { if (n.contains("spruce_leaves")) return 0x619961; if (n.contains("birch_leaves")) return 0x80A755; if (n.contains("leaves") || n.contains("vine")) return FOLIAGE; if (n.equals("lily_pad") || n.equals("minecraft:lily_pad")) return 0x208030; if (n.contains("water")) return WATER; return GRASS; }
  /** 0 down, 1 up, 2 north (-z), 3 south (+z), 4 west (-x), 5 east (+x). */
  static int dirOf(double[] n) { if (n[1] < -0.9) return 0; if (n[1] > 0.9) return 1; if (n[2] < -0.9) return 2; if (n[2] > 0.9) return 3; if (n[0] < -0.9) return 4; if (n[0] > 0.9) return 5; return -1; }
  static Quad faceQuad(String dir, double x0, double y0, double z0, double x1, double y1, double z1, List<Object> uvl) { Quad q = new Quad(); double u0, v0, u1, v1;
    switch (dir) {   // corners in order (u0,v0) (u1,v0) (u1,v1) (u0,v1); default UVs from the element's bounds as the game makes them
      case "up" -> { q.p = new double[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}}; q.n = new double[]{0, 1, 0}; u0 = x0; v0 = z0; u1 = x1; v1 = z1; }
      case "down" -> { q.p = new double[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}}; q.n = new double[]{0, -1, 0}; u0 = x0; v0 = 1 - z1; u1 = x1; v1 = 1 - z0; }
      case "north" -> { q.p = new double[][]{{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}}; q.n = new double[]{0, 0, -1}; u0 = 1 - x1; v0 = 1 - y1; u1 = 1 - x0; v1 = 1 - y0; }
      case "south" -> { q.p = new double[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}}; q.n = new double[]{0, 0, 1}; u0 = x0; v0 = 1 - y1; u1 = x1; v1 = 1 - y0; }
      case "west" -> { q.p = new double[][]{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}}; q.n = new double[]{-1, 0, 0}; u0 = z0; v0 = 1 - y1; u1 = z1; v1 = 1 - y0; }
      default -> { q.p = new double[][]{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}}; q.n = new double[]{1, 0, 0}; u0 = 1 - z1; v0 = 1 - y1; u1 = 1 - z0; v1 = 1 - y0; } }
    if (uvl != null) { u0 = (Double) uvl.get(0) / 16; v0 = (Double) uvl.get(1) / 16; u1 = (Double) uvl.get(2) / 16; v1 = (Double) uvl.get(3) / 16; }
    q.uv = new double[][]{{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}}; return q; }
  static void rotate(Quad q, int rx, int ry) { for (int k = 0; k < 4; k++) q.p[k] = rot(q.p[k], rx, ry, true); q.n = rot(q.n, rx, ry, false); }
  static double[] rot(double[] p, int rx, int ry, boolean point) { double c = point ? 0.5 : 0; double x = p[0] - c, y = p[1] - c, z = p[2] - c;
    for (int i = 0; i < ((rx / 90) % 4 + 4) % 4; i++) { double ny = -z, nz = y; y = ny; z = nz; }   // x: 90 turns the top toward north (the game's model rotation)
    for (int i = 0; i < ((ry / 90) % 4 + 4) % 4; i++) { double nx = -z, nz = x; x = nx; z = nz; }   // y: 90 turns north to east
    return new double[]{x + c, y + c, z + c}; }
  static void box(Look L, double x0, double y0, double z0, double x1, double y1, double z1, int[] t) { for (String d : new String[]{"up", "down", "north", "south", "west", "east"}) { Quad q = faceQuad(d, x0, y0, z0, x1, y1, z1, null); q.tex = t; L.quads.add(q); } }
  /** A terrain slope: its mesh (SlopeMesh.local: the sloped top, the sides, the floor) with its material's textures (top, side, bottom). */
  static void slope(Look L, String nm, int code) { String mat = nm.substring(6); Look m = look(mat); int[] h = SlopeMesh.decode(code, Slopes.L);
    for (SlopeMesh.Face f : SlopeMesh.local(h, Slopes.L, SlopeMesh.autoSplit(h))) { Quad q = new Quad(); for (int k = 0; k < 4; k++) { q.p[k] = new double[]{f.v[k][0], f.v[k][1], f.v[k][2]}; q.uv[k] = new double[]{f.v[k][3], f.v[k][4]}; } q.n = f.n;
      int d = f.mat == SlopeMesh.TOP ? 1 : f.mat == SlopeMesh.FLOOR ? 0 : 2; q.tex = m.faceTex[d] != null ? m.faceTex[d] : m.faceTex[1]; q.tint = m.faceTint[d]; L.quads.add(q); } }
  // ---------------------------------------------------------------- the world: blocks round a point, from the generator's code
  static int X0, Y0, Z0, NX, NY, NZ; static short[] cells; static final List<String> PAL = new ArrayList<>(List.of("air")); static final Map<String, Short> PIDX = new HashMap<>(Map.of("air", (short) 0)); static int[] skyTop; static Look[] LA; static byte[] blockLight; static final Map<String, Integer> EMIT = new HashMap<>(); static final Set<String> GLOW = Set.of("sea_lantern", "glowstone", "lantern", "shroomlight", "jack_o_lantern", "redstone_lamp", "ochre_froglight", "pearlescent_froglight", "verdant_froglight", "lava", "magma_block", "end_rod", "beacon", "torch", "wall_torch");
  static short id(String b) { Short s = PIDX.get(b); if (s == null) { s = (short) PAL.size(); PAL.add(b); PIDX.put(b, s); } return s; }
  static void put(int x, int y, int z, String b) { int i = x - X0, j = y - Y0, k = z - Z0; if (i < 0 || j < 0 || k < 0 || i >= NX || j >= NY || k >= NZ) return; cells[(j * NZ + k) * NX + i] = id(b); }
  static short get(int x, int y, int z) { int i = x - X0, j = y - Y0, k = z - Z0; if (i < 0 || j < 0 || k < 0 || i >= NX || j >= NY || k >= NZ) return 0; return cells[(j * NZ + k) * NX + i]; }
  static void build(PlanetWorld w, int f, int cx, int cz, int R, int yHint) { long t0 = System.nanoTime(); int cx0 = Math.floorDiv(cx - R, 16), cx1 = Math.floorDiv(cx + R, 16), cz0 = Math.floorDiv(cz - R, 16), cz1 = Math.floorDiv(cz + R, 16);
    X0 = cx0 * 16; Z0 = cz0 * 16; NX = (cx1 - cx0 + 1) * 16; NZ = (cz1 - cz0 + 1) * 16; BTINT = new int[NX * NZ]; int minS = Integer.MAX_VALUE, maxS = Integer.MIN_VALUE; List<Object[]> chunks = new ArrayList<>();
    for (int a = cx0; a <= cx1; a++) for (int b = cz0; b <= cz1; b++) { Column[] ring = new Column[324], cols = new Column[256]; byte[] kinds = new byte[256]; int[] cf = ChunkFill.columns(w, f, a * 16, b * 16, ring, cols, kinds); chunks.add(new Object[]{a, b, ring, cols, cf});
      for (int i = 0; i < 256; i++) { int g = FarMesh.grassRgb(cols[i].biome); BTINT[(b * 16 + (i & 15) - Z0) * NX + a * 16 + (i >> 4) - X0] = g < 0 ? GRASS : g; }   /* the column's biome grass tint (the game's biome source: Column.biome) */
      for (Column c : cols) if (!c.voidCol) { minS = Math.min(minS, c.surfaceY); maxS = Math.max(maxS, c.topY()); } }
    Y0 = minS - 12; int yTop = Math.max(maxS + 8, yHint + 700); NY = yTop - Y0; cells = new short[NX * NY * NZ]; final int MINY = -2560;   // world/PlanetGenerator.MIN_Y
    for (Object[] ch : chunks) { int a = (Integer) ch[0], b = (Integer) ch[1]; Column[] ring = (Column[]) ch[2], cols = (Column[]) ch[3]; int x0 = a * 16, z0 = b * 16;
      for (int i = 0; i < 256; i++) { Column c = cols[i]; if (c.voidCol) continue; int x = x0 + (i >> 4), z = z0 + (i & 15); for (int y = Y0; y <= c.topY(); y++) { Column.Mat m = c.at(y, MINY); if (m != Column.Mat.AIR) put(x, y, z, ChunkFill.name(m)); }
        if (c.surfaceY < Terrain.SEA && !c.dry()) for (int y = c.surfaceY + 1; y <= Terrain.SEA; y++) if (get(x, y, z) == 0) put(x, y, z, c.ice && y == Terrain.SEA ? "ice" : "water"); }
      ChunkFill.caves(w, f, x0, z0, cols, MINY, (lx, y, lz, k) -> put(x0 + lx, y, z0 + lz, k == 0 ? "air" : k == 1 ? "water" : k == 2 ? "pointed_dripstone[vertical_direction=down]" : "pointed_dripstone[vertical_direction=up]"));
      ChunkFill.slopes(cols, ring, (lx, y, lz, sl, corners, rep) -> { Column c = cols[lx * 16 + lz]; put(x0 + lx, y, z0 + lz, "hopelessinfinity:" + sl + "[corners=" + corners + "]"); if (rep) { put(x0 + lx, y - 1, z0 + lz, ChunkFill.name(c.top)); if (c.snowLayer) put(x0 + lx, y + 1, z0 + lz, "air"); } });
      ChunkFill.structures(w, f, x0, z0, (x, y, z, s) -> put(x, y, z, s), (x, y, z, s) -> put(x, y, z, s));
      if (SAVE != null) overlaySaved(f, a, b); }
    if (SAVE != null) System.out.printf("save: %d chunks drawn from the save's blocks (%d sections; %d blocks differ from the generator: what was built, dug or changed in the game), %d chunks not in the save drawn from the generator, %d not finished in the save (generator)%n", savedChunks, savedSections, changed, genChunks, partChunks);
    finishBuild(t0, chunks.size(), "with the generator's code"); }
  /** After the blocks: the sky-light heights, each block's look, block light, models reaching past their cell, a report. */
  static void finishBuild(long t0, int nChunks, String from) {
    skyTop = new int[NX * NZ]; for (int k = 0; k < NZ; k++) for (int i = 0; i < NX; i++) { int t = Y0 - 1; for (int j = NY - 1; j >= 0; j--) { short s = cells[(j * NZ + k) * NX + i]; if (s != 0 && look(PAL.get(s)).opaque) { t = Y0 + j; break; } } skyTop[k * NX + i] = t; }
    LA = new Look[PAL.size()]; for (int i = 0; i < LA.length; i++) LA[i] = look(PAL.get(i));
    spreadLight();   // the looks by palette index, for the ray caster (no lock)
    // models reaching past their cell (Forge OBJ street furniture, lamps): listed in every cell their bounds cover
    { Map<Long, List<int[]>> ov = new HashMap<>(); boolean[] big = new boolean[LA.length]; double[][] bb = new double[LA.length][]; for (int i = 1; i < LA.length; i++) { double[] b6 = {1e9, 1e9, 1e9, -1e9, -1e9, -1e9}; for (Quad q : LA[i].quads) for (double[] pt : q.p) for (int k = 0; k < 3; k++) { b6[k] = Math.min(b6[k], pt[k]); b6[k + 3] = Math.max(b6[k + 3], pt[k]); } if (b6[0] < -0.01 || b6[1] < -0.01 || b6[2] < -0.01 || b6[3] > 1.01 || b6[4] > 1.01 || b6[5] > 1.01) { big[i] = true; bb[i] = b6; } }
      for (int j = 0; j < NY; j++) for (int k = 0; k < NZ; k++) for (int i = 0; i < NX; i++) { short sid = cells[(j * NZ + k) * NX + i]; if (sid == 0 || !big[sid]) continue; int x = X0 + i, y = Y0 + j, z = Z0 + k; double[] b6 = bb[sid];
        for (int a = (int) Math.floor(b6[0]); a <= (int) Math.floor(b6[3] - 1e-9); a++) for (int c = (int) Math.floor(b6[1]); c <= (int) Math.floor(b6[4] - 1e-9); c++) for (int e = (int) Math.floor(b6[2]); e <= (int) Math.floor(b6[5] - 1e-9); e++) { if (a == 0 && c == 0 && e == 0) continue; int X = x + a, Y = y + c, Z = z + e; if (X < X0 || Y < Y0 || Z < Z0 || X >= X0 + NX || Y >= Y0 + NY || Z >= Z0 + NZ) continue; ov.computeIfAbsent(cellKey(X, Y, Z), q -> new ArrayList<>()).add(new int[]{sid, x, y, z}); } }
      for (var en : ov.entrySet()) OVER.put(en.getKey(), en.getValue().toArray(new int[0][])); if (!ov.isEmpty()) System.out.println("models past their cell: in " + ov.size() + " cells"); }
    { Set<String> magenta = new TreeSet<>(); for (int i = 1; i < LA.length; i++) { if (LA[i].air || LA[i].water || LA[i].lava) continue; boolean bad = false; for (Quad q : LA[i].quads) if (q.tex == null) bad = true; if (bad) magenta.add(PAL.get(i).replaceAll("\\|.*", "")); } if (!magenta.isEmpty()) System.out.println("drawn magenta (no model or texture found): " + magenta); }
    System.out.printf("built %d x %d x %d blocks (%d chunks, y %d..%d) %s in %.1f s; %d block kinds%n", NX, NY, NZ, nChunks, Y0, Y0 + NY, from, (System.nanoTime() - t0) / 1e9, PAL.size()); }
  // ---- 0.29.8 SAVE-ONLY MODE (SaveView merged in; Overlord, 2026-11-08: one Minecraft renderer, updated, never a new one): any 1.18+ save drawn
  //      from its own region files with its mods' models; a world that is not Hopeless Infinity's is drawn this way. ----
  static File MODSDIR; static File REGION; static String DIM = "minecraft:overworld"; static boolean SAVEONLY; static int[] SPAWN = {0, 0, 0}; static String PLAYER_DIM; static double YMIN = Double.NaN, YMAX = Double.NaN;
  static int savedChunks2, noChunks, partChunks2, savedSections2, badChunks;
  /** The region folder of a dimension id: the overworld region/, the nether DIM-1/region, the end DIM1/region, any other ns:path in dimensions/ns/path/region. */
  static File regionDirOf(String dim) { return switch (dim) { case "minecraft:overworld" -> new File(SAVE, "region"); case "minecraft:the_nether" -> new File(SAVE, "DIM-1/region"); case "minecraft:the_end" -> new File(SAVE, "DIM1/region");
      default -> { String[] p = dim.split(":", 2); yield new File(SAVE, "dimensions/" + p[0] + "/" + p[1] + "/region"); } }; }
  /** A saved chunk's NBT from a region folder, or null (as savedChunk). */
  @SuppressWarnings("unchecked") static Map<String, Object> savedChunkIn(File regionDir, int cx, int cz) throws IOException {
    File rf = new File(regionDir, "r." + (cx >> 5) + "." + (cz >> 5) + ".mca"); if (!rf.isFile()) return null;
    RandomAccessFile raf; synchronized (REGIONS) { raf = REGIONS.get(rf.getPath()); if (raf == null) { raf = new RandomAccessFile(rf, "r"); REGIONS.put(rf.getPath(), raf); } }
    if (raf.length() < 8192) return null; raf.seek(4L * ((cx & 31) + (cz & 31) * 32)); int loc = raf.readInt(); if (loc == 0) return null; long off = (long) (loc >>> 8) * 4096;
    raf.seek(off); int len = raf.readInt(), comp = raf.readByte(); if (len <= 1 || (comp & 0x80) != 0) return null; byte[] buf = new byte[len - 1]; raf.readFully(buf);
    InputStream in = comp == 1 ? new GZIPInputStream(new ByteArrayInputStream(buf)) : comp == 2 ? new InflaterInputStream(new ByteArrayInputStream(buf)) : new ByteArrayInputStream(buf);
    Map<String, Object> ch = (Map<String, Object>) nbt(in); String st = String.valueOf(ch.get("Status")); if (!st.equals("minecraft:full") && !st.equals("full")) { partChunks2++; return null; } return ch; }
  /** Unpack a paletted container: n entries of max(minBits, ceil(log2 palette)) bits, no entry across two longs (the 1.16+ layout). */
  static int[] unpack(long[] data, int palSize, int n, int minBits) { int[] o = new int[n]; if (data == null || palSize <= 1) return o; int bits = Math.max(minBits, 32 - Integer.numberOfLeadingZeros(palSize - 1)), per = 64 / bits; long mask = (1L << bits) - 1;
    for (int i = 0; i < n && i / per < data.length; i++) { int v = (int) ((data[i / per] >>> ((i % per) * bits)) & mask); o[i] = v < palSize ? v : 0; } return o; }
  /** The blocks of the saved chunks round (cx, cz), radius R: each section's palette and indices (order y, z, x), its biomes (4 x 4 x 4
   *  cells); the box spans the lowest to the highest non-air block found. */
  @SuppressWarnings("unchecked") static void buildFromSave(int cx, int cz, int R) { long t0 = System.nanoTime(); int cx0 = Math.floorDiv(cx - R, 16), cx1 = Math.floorDiv(cx + R, 16), cz0 = Math.floorDiv(cz - R, 16), cz1 = Math.floorDiv(cz + R, 16);
    X0 = cx0 * 16; Z0 = cz0 * 16; NX = (cx1 - cx0 + 1) * 16; NZ = (cz1 - cz0 + 1) * 16; Map<Long, short[]> secs = new HashMap<>(); Map<Long, String[]> bio = new HashMap<>(); int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE, lowTop = Integer.MAX_VALUE;
    for (int a = cx0; a <= cx1; a++) for (int b = cz0; b <= cz1; b++) { Map<String, Object> ch; try { ch = savedChunkIn(REGION, a, b); } catch (IOException | RuntimeException ex) { badChunks++; System.out.println("chunk " + a + "," + b + " could not be read: " + ex); continue; }
      if (ch == null) { noChunks++; continue; } savedChunks2++; List<Object> sl = (List<Object>) ch.get("sections"); if (sl == null) continue; int chTop = Integer.MIN_VALUE;
      for (Object so : sl) { Map<String, Object> sec = (Map<String, Object>) so; int sy = ((Number) sec.get("Y")).intValue(); long key = ((long) (a - cx0) * 4096 + (b - cz0)) * 4096 + (sy + 2048);
        Map<String, Object> bs = (Map<String, Object>) sec.get("block_states"); if (bs != null) { List<Object> pal = (List<Object>) bs.get("palette"); short[] ids = new short[pal.size()]; boolean any = false;
          for (int k = 0; k < ids.length; k++) { ids[k] = id(blockString((Map<String, Object>) pal.get(k))); if (ids[k] != 0) any = true; }
          if (any) { int[] ix = unpack((long[]) bs.get("data"), ids.length, 4096, 4); short[] cs = new short[4096]; boolean got = false; for (int i = 0; i < 4096; i++) { cs[i] = ids[ix[i]]; if (cs[i] != 0) got = true; }
            if (got) { secs.put(key, cs); savedSections2++; minY = Math.min(minY, sy * 16); maxY = Math.max(maxY, sy * 16 + 15); chTop = Math.max(chTop, sy * 16 + 15); } } }
        Map<String, Object> bm = (Map<String, Object>) sec.get("biomes"); if (bm != null) { List<Object> pal = (List<Object>) bm.get("palette"); int[] ix = unpack((long[]) bm.get("data"), pal.size(), 64, 1); String[] bn = new String[64]; for (int i = 0; i < 64; i++) bn[i] = String.valueOf(pal.get(ix[i])); bio.put(key, bn); } }
      if (chTop > Integer.MIN_VALUE) lowTop = Math.min(lowTop, chTop); }
    /* the box's floor: ymin= when given, else 96 blocks under the lowest chunk top (deep worlds hold thousands of blocks of rock no camera sees) */
    if (minY <= maxY) minY = Double.isNaN(YMIN) ? Math.max(minY, Math.floorDiv(lowTop - 96, 16) * 16) : Math.max(minY, (int) YMIN); if (!Double.isNaN(YMAX)) maxY = Math.min(maxY, (int) YMAX);
    if (minY > maxY) { minY = 0; maxY = 15; } Y0 = minY; NY = maxY - minY + 1; cells = new short[NX * NY * NZ];
    for (var en : secs.entrySet()) { long key = en.getKey(); int sy = (int) (key % 4096) - 2048, b = (int) ((key / 4096) % 4096), a = (int) (key / 4096 / 4096); short[] cs = en.getValue();
      if (sy * 16 + 15 < Y0 || sy * 16 >= Y0 + NY) continue;
      for (int i = 0; i < 4096; i++) { if (cs[i] == 0) continue; int x = i & 15, z = (i >> 4) & 15, y = sy * 16 + (i >> 8); if (y < Y0 || y >= Y0 + NY) continue; cells[((y - Y0) * NZ + b * 16 + z) * NX + a * 16 + x] = cs[i]; } }
    finishBuild(t0, (cx1 - cx0 + 1) * (cz1 - cz0 + 1), "from the save");
    BTINT = new int[NX * NZ]; FTINT = new int[NX * NZ]; Set<String> unknown = new TreeSet<>();
    for (int k = 0; k < NZ; k++) for (int i = 0; i < NX; i++) { int y = Math.max(Y0, skyTop[k * NX + i]); long key = ((long) (i >> 4) * 4096 + (k >> 4)) * 4096 + (Math.floorDiv(y, 16) + 2048); String[] bn = bio.get(key);
      String b = bn == null ? "minecraft:plains" : bn[(((y & 15) >> 2) * 4 + ((k & 15) >> 2)) * 4 + ((i & 15) >> 2)]; int[] gf = biomeColours(b, unknown); BTINT[k * NX + i] = gf[0]; FTINT[k * NX + i] = gf[1]; }
    System.out.printf("save: %d chunks read (%d sections with blocks), %d not in the save, %d not finished, %d unreadable; y %d..%d%s%n", savedChunks2, savedSections2, noChunks, partChunks2, badChunks, Y0, Y0 + NY - 1, unknown.isEmpty() ? "" : "; biomes not in the colour table (drawn as plains): " + unknown); }
  /** Vanilla biomes' temperature and downfall (the game's biome files, 1.18 to 1.20): the grass and foliage colours come from the colormaps at
   *  these; swamps, dark forest and badlands have the game's fixed or modified colours. Modded biomes: plains (listed). */
  static final Map<String, double[]> BIOME_TD = new HashMap<>(); static { Object[][] t = {{"plains", .8, .4}, {"sunflower_plains", .8, .4}, {"snowy_plains", 0., .5}, {"ice_spikes", 0., .5}, {"desert", 2., 0.}, {"swamp", .8, .9}, {"mangrove_swamp", .8, .9}, {"forest", .7, .8}, {"flower_forest", .7, .8}, {"birch_forest", .6, .6}, {"dark_forest", .7, .8}, {"old_growth_birch_forest", .6, .6}, {"old_growth_pine_taiga", .3, .8}, {"old_growth_spruce_taiga", .25, .8}, {"taiga", .25, .8}, {"snowy_taiga", -.5, .4}, {"savanna", 2., 0.}, {"savanna_plateau", 2., 0.}, {"windswept_hills", .2, .3}, {"windswept_gravelly_hills", .2, .3}, {"windswept_forest", .2, .3}, {"windswept_savanna", 2., 0.}, {"jungle", .95, .9}, {"sparse_jungle", .95, .8}, {"bamboo_jungle", .95, .9}, {"badlands", 2., 0.}, {"eroded_badlands", 2., 0.}, {"wooded_badlands", 2., 0.}, {"meadow", .5, .8}, {"cherry_grove", .5, .8}, {"grove", -.2, .8}, {"snowy_slopes", -.3, .9}, {"frozen_peaks", -.7, .9}, {"jagged_peaks", -.7, .9}, {"stony_peaks", 1., .3}, {"river", .5, .5}, {"frozen_river", 0., .5}, {"beach", .8, .4}, {"snowy_beach", .05, .3}, {"stony_shore", .2, .3}, {"warm_ocean", .5, .5}, {"lukewarm_ocean", .5, .5}, {"deep_lukewarm_ocean", .5, .5}, {"ocean", .5, .5}, {"deep_ocean", .5, .5}, {"cold_ocean", .5, .5}, {"deep_cold_ocean", .5, .5}, {"frozen_ocean", 0., .5}, {"deep_frozen_ocean", .5, .5}, {"mushroom_fields", .9, 1.}, {"dripstone_caves", .8, .4}, {"lush_caves", .5, .5}, {"deep_dark", .8, .4}, {"nether_wastes", 2., 0.}, {"crimson_forest", 2., 0.}, {"warped_forest", 2., 0.}, {"soul_sand_valley", 2., 0.}, {"basalt_deltas", 2., 0.}, {"the_end", .5, .5}, {"end_highlands", .5, .5}, {"end_midlands", .5, .5}, {"small_end_islands", .5, .5}, {"end_barrens", .5, .5}, {"the_void", .5, .5}};
    for (Object[] e : t) BIOME_TD.put("minecraft:" + e[0], new double[]{(Double) e[1], (Double) e[2]}); }
  static BufferedImage GRASS_MAP, FOLIAGE_MAP; static final Map<String, int[]> BIOME_COL = new HashMap<>();
  static int[] biomeColours(String b, Set<String> unknown) { int[] c = BIOME_COL.get(b); if (c != null) return c; double[] td = BIOME_TD.get(b); if (td == null) { unknown.add(b); td = new double[]{.8, .4}; }
    try { if (GRASS_MAP == null) { GRASS_MAP = javax.imageio.ImageIO.read(new ByteArrayInputStream(assetBytes("minecraft", "textures/colormap/grass.png"))); FOLIAGE_MAP = javax.imageio.ImageIO.read(new ByteArrayInputStream(assetBytes("minecraft", "textures/colormap/foliage.png"))); } } catch (Exception ex) { }
    double tt = Math.max(0, Math.min(1, td[0])), dd = Math.max(0, Math.min(1, td[1])) * tt; int px = (int) ((1 - tt) * 255), py = (int) ((1 - dd) * 255);
    int g = GRASS_MAP == null ? GRASS : GRASS_MAP.getRGB(px, py) & 0xFFFFFF, f = FOLIAGE_MAP == null ? FOLIAGE : FOLIAGE_MAP.getRGB(px, py) & 0xFFFFFF;
    if (b.endsWith(":swamp") || b.endsWith(":mangrove_swamp")) { g = 0x6A7039; f = b.endsWith("mangrove_swamp") ? 0x8DB127 : 0x6A7039; } if (b.endsWith(":dark_forest")) g = (((g & 0xFEFEFE) + 0x28340A) >> 1);
    if (b.contains("badlands")) { g = 0x90814D; f = 0x9E814D; } c = new int[]{g, f}; BIOME_COL.put(b, c); return c; }
  // ---------------------------------------------------------------- the ray caster
  static int SKY = 0x78A7FF, FOG = 0xC0D8FF; static int SKYMAX = 15; static double DAY = 1;   /* night=1: the sky gives light 4 (the game's night sky light), a dark sky */ static double fogStart, fogEnd;
  static double[] eye, fwd, right, up; static boolean ortho; static double scale, tanF;
  static int shade(double ox, double oy, double oz, double dx, double dy, double dz) { double r = 0, g = 0, b = 0, keep = 1; double maxT = ortho ? 4000 : HAZE > 0 ? 1e9 : fogEnd * 1.05;   /* with the far terrain the ray runs to the box edge (the far mesh lies past it) */
    // enter the box
    double t0 = 0, t1 = maxT; double[] lo = {X0, Y0, Z0}, hi = {X0 + NX, Y0 + NY, Z0 + NZ}, o = {ox, oy, oz}, d = {dx, dy, dz};
    for (int a = 0; a < 3; a++) { if (Math.abs(d[a]) < 1e-12) { if (o[a] < lo[a] || o[a] > hi[a]) return fog(0, 0, 0, 1, dy, 0, ox, oz, dx, dz); continue; } double ta = (lo[a] - o[a]) / d[a], tb = (hi[a] - o[a]) / d[a]; if (ta > tb) { double s = ta; ta = tb; tb = s; } t0 = Math.max(t0, ta); t1 = Math.min(t1, tb); }
    if (t0 > t1) return fog(0, 0, 0, 1, dy, 0, ox, oz, dx, dz); double t = t0 + 1e-6; int x = (int) Math.floor(ox + dx * t), y = (int) Math.floor(oy + dy * t), z = (int) Math.floor(oz + dz * t);
    int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1; double tdx = Math.abs(1 / dx), tdy = Math.abs(1 / dy), tdz = Math.abs(1 / dz);
    double tmx = dx == 0 ? 1e30 : ((dx > 0 ? x + 1 - ox : ox - x) * tdx), tmy = dy == 0 ? 1e30 : ((dy > 0 ? y + 1 - oy : oy - y) * tdy), tmz = dz == 0 ? 1e30 : ((dz > 0 ? z + 1 - oz : oz - z) * tdz);
    int face = -1; boolean inWater = false; double waterFrom = 0;
    for (int step = 0; step < 6000 && t < t1 && keep > 0.02; step++) { short s = get(x, y, z); double tExit = Math.min(tmx, Math.min(tmy, tmz));
      if (s != 0) { Look L = LA[s];
        if (L.water) { if (!inWater) { inWater = true; waterFrom = t; boolean top = get(x, y + 1, z) == 0 || !LA[get(x, y + 1, z)].water; int[] wt = tex("block/water_still"); int c = wt == null ? WATER : mul(wt[((int) Math.floor((ox + dx * t - x) * 16) & 15) + 16 * ((int) Math.floor((oz + dz * t - z) * 16) & 15)], WATER);
            double a = top ? 0.55 : 0.35, sh = light(x, y, z, top ? 1 : face) * faceShade(top ? 1 : face); r += keep * a * ((c >> 16 & 255) * sh); g += keep * a * ((c >> 8 & 255) * sh); b += keep * a * ((c & 255) * sh); keep *= 1 - a; } }
        else { if (inWater) { double depth = t - waterFrom; double a = 1 - Math.exp(-depth / 6); r += keep * a * 25; g += keep * a * 55; b += keep * a * 110; keep *= 1 - a; inWater = false; }
          if (L.full && L.opaque) { int fc = face < 0 ? 1 : face; int[] tx = L.faceTex[fc]; double hx = ox + dx * t - x, hy = oy + dy * t - y, hz = oz + dz * t - z; int c = sample(tx, faceUV(fc, hx, hy, hz)); if (L.faceTint[fc] >= 0) c = mul(c, gt(L.faceTint[fc], x, z)); double sh = L.glow ? 1.0 : light(x + nOff(fc, 0), y + nOff(fc, 1), z + nOff(fc, 2), fc) * faceShade(fc);   /* glowing blocks show full bright (block light is not spread in this version) */
            r += keep * (c >> 16 & 255) * sh; g += keep * (c >> 8 & 255) * sh; b += keep * (c & 255) * sh; keep = 0; return fog(r, g, b, keep, dy, t, ox, oz, dx, dz); }
          if (!L.air || OVER.containsKey(cellKey(x, y, z))) { double[] acc = {r, g, b, keep, t}; boolean done = cellModels(acc, L.air ? null : L, x, y, z, ox, oy, oz, dx, dy, dz, t, tExit); r = acc[0]; g = acc[1]; b = acc[2]; keep = acc[3]; if (done) return fog(r, g, b, 0, dy, acc[4], ox, oz, dx, dz); } } }
      else if (OVER.containsKey(cellKey(x, y, z))) { double[] acc = {r, g, b, keep, t}; boolean done = cellModels(acc, null, x, y, z, ox, oy, oz, dx, dy, dz, t, tExit); r = acc[0]; g = acc[1]; b = acc[2]; keep = acc[3]; if (done) return fog(r, g, b, 0, dy, acc[4], ox, oz, dx, dz); }
      else if (inWater) { double depth = t - waterFrom; double a = 1 - Math.exp(-depth / 6); r += keep * a * 25; g += keep * a * 55; b += keep * a * 110; keep *= 1 - a; inWater = false; }
      if (tmx < tmy && tmx < tmz) { x += sx; t = tmx; tmx += tdx; face = sx > 0 ? 4 : 5; } else if (tmy < tmz) { y += sy; t = tmy; tmy += tdy; face = sy > 0 ? 0 : 1; } else { z += sz; t = tmz; tmz += tdz; face = sz > 0 ? 2 : 3; } }
    return fog(r, g, b, keep, dy, t, ox, oz, dx, dz); }
  /** The model faces met in cell (x, y, z): its own block's (L, may be null) and those of models reaching into it from other cells (OVER),
   *  nearest first, translucent ones blended into acc {r, g, b, keep}; true when nothing more shows through (acc[4] the distance). */
  static boolean cellModels(double[] acc, Look L, int x, int y, int z, double ox, double oy, double oz, double dx, double dy, double dz, double t, double tExit) {
    int[][] ov = OVER.get(cellKey(x, y, z)); double tc = t - 1e-6;
    for (int pass = 0; pass < 6 && acc[3] > 0.02; pass++) { double best = tExit + 1e-6; Quad hq = null; double[] huv = null; int lx = x, ly = y, lz = z;
      if (L != null) for (Quad q : L.quads) { double[] hit = hitQuad(q, ox - x, oy - y, oz - z, dx, dy, dz, tc); if (hit != null && hit[0] < best) { best = hit[0]; hq = q; huv = hit; lx = x; ly = y; lz = z; } }
      if (ov != null) for (int[] e : ov) { Look M = LA[e[0]]; for (Quad q : M.quads) { double[] hit = hitQuad(q, ox - e[1], oy - e[2], oz - e[3], dx, dy, dz, tc); if (hit != null && hit[0] < best) { best = hit[0]; hq = q; huv = hit; lx = e[1]; ly = e[2]; lz = e[3]; } } }
      if (hq == null) return false; int c = sample(hq.tex, new double[]{huv[1], huv[2]}); int al = c >>> 24; if (al < 16) { tc = best + 1e-6; continue; } if (hq.tint >= 0) c = mul(c, gt(hq.tint, x, z));
      int fd = dirOf(hq.n); double sh = light(x, y, z, fd) * (fd >= 0 ? faceShade(fd) : Math.min(1, Math.abs(hq.n[0]) * 0.6 + Math.abs(hq.n[2]) * 0.8 + (hq.n[1] > 0 ? hq.n[1] : -hq.n[1] * 0.5))); double a = al / 255.0;
      acc[0] += acc[3] * a * (c >> 16 & 255) * sh; acc[1] += acc[3] * a * (c >> 8 & 255) * sh; acc[2] += acc[3] * a * (c & 255) * sh; acc[3] *= 1 - a; tc = best + 1e-6; if (acc[3] <= 0.02) { acc[4] = best; return true; } }
    return false; }
  /** The light each block gives (the game's values): vanilla's, and the mod's own read from its source (blocks registered with a constant
   *  lightLevel), so the table follows the code. */
  static void emitTable() { for (String[] e : new String[][]{{"sea_lantern", "15"}, {"glowstone", "15"}, {"lantern", "15"}, {"soul_lantern", "10"}, {"torch", "14"}, {"wall_torch", "14"}, {"end_rod", "14"}, {"shroomlight", "15"}, {"ochre_froglight", "15"}, {"pearlescent_froglight", "15"}, {"verdant_froglight", "15"}, {"beacon", "15"}, {"jack_o_lantern", "15"}, {"lava", "15"}, {"magma_block", "3"}, {"campfire", "15"}, {"sea_pickle", "6"}, {"light", "15"}}) EMIT.put(e[0], Integer.parseInt(e[1]));
    try (var st = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java"))) { java.util.regex.Pattern pt = java.util.regex.Pattern.compile("(?:block|reg|register)\\(\"([a-z_]+)\".*?lightLevel\\(s? ?-> ?(\\d+)\\)");
      for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) st.filter(x -> x.toString().endsWith(".java"))::iterator) for (String line : java.nio.file.Files.readAllLines(f)) { var m = pt.matcher(line); if (m.find()) EMIT.put("hopelessinfinity:" + m.group(1), Integer.parseInt(m.group(2))); } }
    catch (IOException ex) { try (InputStream in = WorldView.class.getClassLoader().getResourceAsStream("worldview/emit.txt")) {   /* the packaged tool: the table written from the source when it was packed (writeEmit) */
        if (in != null) for (String line : new String(in.readAllBytes()).split("\n")) { String[] kv = line.trim().split("="); if (kv.length == 2) EMIT.put(kv[0], Integer.parseInt(kv[1])); } } catch (IOException e2) { } } }
  /** For packing: write the mod's own light table (from the source) to a file. */
  static void writeEmit(String out) throws IOException { emitTable(); StringBuilder sb = new StringBuilder(); for (var e : new TreeMap<>(EMIT).entrySet()) if (e.getKey().startsWith("hopelessinfinity:")) sb.append(e.getKey()).append('=').append(e.getValue()).append('\n'); java.nio.file.Files.writeString(java.nio.file.Path.of(out), sb.toString()); System.out.println("wrote " + out + ": " + sb.toString().split("\n").length + " of the mod's blocks give light"); }
  static int emits(String b) { String base = b.replaceAll("\\[.*", "").replace("minecraft:", ""); if (b.contains("lit=false")) return 0; if (base.equals("redstone_lamp")) return b.contains("lit=true") ? 15 : 0; Integer v = EMIT.get(base); return v == null ? 0 : v; }
  /** Block light spread through the open cells from every emitting block, one level less a block (the game's rule), into blockLight. */
  static void spreadLight() { emitTable(); long t0 = System.nanoTime(); blockLight = new byte[NX * NY * NZ]; int[] lv = new int[LA.length]; boolean[] open = new boolean[LA.length]; int nEm = 0;
    for (int i = 0; i < LA.length; i++) { lv[i] = emits(PAL.get(i)); open[i] = !LA[i].opaque; } open[0] = true;
    java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>(); for (int i = 0; i < cells.length; i++) { int e = lv[cells[i]]; if (e > 0) { blockLight[i] = (byte) e; q.add(i); nEm++; } }
    int[] dd = {1, -1, NX, -NX, NX * NZ, -NX * NZ}; while (!q.isEmpty()) { int i = q.poll(); int l = blockLight[i] - 1; if (l <= 0) continue; int xi = i % NX, rest = i / NX, zk = rest % NZ, yj = rest / NZ;
      for (int k = 0; k < 6; k++) { if ((k == 0 && xi == NX - 1) || (k == 1 && xi == 0) || (k == 2 && zk == NZ - 1) || (k == 3 && zk == 0) || (k == 4 && yj == NY - 1) || (k == 5 && yj == 0)) continue; int j = i + dd[k]; if (!open[cells[j]] || blockLight[j] >= l) continue; blockLight[j] = (byte) l; q.add(j); } }
    System.out.printf("block light: %d emitting blocks spread in %.1f s%n", nEm, (System.nanoTime() - t0) / 1e9); }
  static long cellKey(int x, int y, int z) { return ((long) (x - X0) * NZ + (z - Z0)) * NY + (y - Y0); }
  static final Map<Long, int[][]> OVER = new HashMap<>();
  static int nOff(int fc, int axis) { return switch (fc) { case 0 -> axis == 1 ? -1 : 0; case 1 -> axis == 1 ? 1 : 0; case 2 -> axis == 2 ? -1 : 0; case 3 -> axis == 2 ? 1 : 0; case 4 -> axis == 0 ? -1 : 0; default -> axis == 0 ? 1 : 0; }; }
  static double faceShade(int fc) { return fc == 1 ? 1.0 : fc == 0 ? 0.5 : fc <= 3 ? 0.8 : 0.6; }
  /** Sky light at air cell (x, y, z): 15 where the sky is open above, one less a block away from the nearest open column (to 6), the game's brightness curve. */
  static double light(int x, int y, int z, int fc) { int L = 0; outer: for (int r = 0; r <= 8; r++) { for (int i = -r; i <= r; i++) for (int k = -r; k <= r; k++) { if (Math.abs(i) + Math.abs(k) != r) continue; int xi = x + i - X0, zk = z + k - Z0; if (xi < 0 || zk < 0 || xi >= NX || zk >= NZ) { L = 15 - r; break outer; } if (skyTop[zk * NX + xi] < y) { L = 15 - r; break outer; } } }
    int bl = 0; { int xi = x - X0, yj = y - Y0, zk = z - Z0; if (blockLight != null && xi >= 0 && yj >= 0 && zk >= 0 && xi < NX && yj < NY && zk < NZ) bl = blockLight[(yj * NZ + zk) * NX + xi]; }
    double l = Math.max(Math.min(SKYMAX, Math.max(0, L)), bl) / 15.0; double br = l / (4 - 3 * l); return 0.08 + 0.92 * br; }
  static int fog(double r, double g, double b, double keep, double dy, double t, double ox, double oz, double dx, double dz) { int sk = sky(dy); boolean hit = keep < 0.99; r += keep * (sk >> 16 & 255); g += keep * (sk >> 8 & 255); b += keep * (sk & 255);
    if (!ortho) { double hd = t * Math.hypot(dx, dz); double fa = HAZE > 0 ? (hit ? airFade(t, t * dy) : 0) : Math.max(0, Math.min(1, (hd - fogStart) / (fogEnd - fogStart))); r = r * (1 - fa) + (FOG >> 16 & 255) * fa; g = g * (1 - fa) + (FOG >> 8 & 255) * fa; b = b * (1 - fa) + (FOG & 255) * fa; }
    return (hit ? HIT : 0) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b); }
  static final int HIT = 0x1000000; static double HAZE = 0;   // HAZE: the distance haze (blocks) used with the far terrain (0: the game's fog at the built radius)
  static int sky(double dy) { double k = Math.max(0, Math.min(1, dy * 3 + 0.3)); return lerp(FOG, SKY, k); }
  static int lerp(int a, int b, double k) { return (clamp((a >> 16 & 255) * (1 - k) + (b >> 16 & 255) * k) << 16) | (clamp((a >> 8 & 255) * (1 - k) + (b >> 8 & 255) * k) << 8) | clamp((a & 255) * (1 - k) + (b & 255) * k); }
  static int clamp(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }
  static int mul(int c, int t) { return (c & 0xFF000000) | (((c >> 16 & 255) * (t >> 16 & 255) / 255) << 16) | (((c >> 8 & 255) * (t >> 8 & 255) / 255) << 8) | ((c & 255) * (t & 255) / 255); }
  static double[] faceUV(int fc, double hx, double hy, double hz) { return switch (fc) { case 1 -> new double[]{hx, hz}; case 0 -> new double[]{hx, 1 - hz}; case 2 -> new double[]{1 - hx, 1 - hy}; case 3 -> new double[]{hx, 1 - hy}; case 4 -> new double[]{hz, 1 - hy}; default -> new double[]{1 - hz, 1 - hy}; }; }
  static int sample(int[] tx, double[] uv) { if (tx == null) return 0xFFFF00FF; int u = (int) Math.floor(uv[0] * 16), v = (int) Math.floor(uv[1] * 16); return tx[(Math.max(0, Math.min(15, v))) * 16 + Math.max(0, Math.min(15, u))]; }
  /** Ray (origin in cell units) against a quad: {t, u, v} or null; t over tMin. */
  static double[] hitQuad(Quad q, double ox, double oy, double oz, double dx, double dy, double dz, double tMin) { double[] n = q.n, p0 = q.p[0]; double den = n[0] * dx + n[1] * dy + n[2] * dz; if (Math.abs(den) < 1e-12) return null;
    double t = (n[0] * (p0[0] - ox) + n[1] * (p0[1] - oy) + n[2] * (p0[2] - oz)) / den; if (t <= tMin) return null; double hx = ox + dx * t, hy = oy + dy * t, hz = oz + dz * t;
    for (int tri = 0; tri < 2; tri++) { int a = 0, b = tri == 0 ? 1 : 2, c = tri == 0 ? 2 : 3; double[] w = bary(q.p[a], q.p[b], q.p[c], hx, hy, hz); if (w == null) continue;
      return new double[]{t, w[0] * q.uv[a][0] + w[1] * q.uv[b][0] + w[2] * q.uv[c][0], w[0] * q.uv[a][1] + w[1] * q.uv[b][1] + w[2] * q.uv[c][1]}; } return null; }
  static double[] bary(double[] a, double[] b, double[] c, double px, double py, double pz) { double[] v0 = {b[0] - a[0], b[1] - a[1], b[2] - a[2]}, v1 = {c[0] - a[0], c[1] - a[1], c[2] - a[2]}, v2 = {px - a[0], py - a[1], pz - a[2]};
    double d00 = dot(v0, v0), d01 = dot(v0, v1), d11 = dot(v1, v1), d20 = dot(v2, v0), d21 = dot(v2, v1), den = d00 * d11 - d01 * d01; if (Math.abs(den) < 1e-14) return null; double v = (d11 * d20 - d01 * d21) / den, w = (d00 * d21 - d01 * d20) / den, u = 1 - v - w;
    return u >= -1e-6 && v >= -1e-6 && w >= -1e-6 ? new double[]{u, v, w} : null; }
  static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
  // ---------------------------------------------------------------- places and cameras
  // ---- a saved world (the packaged tool): its seed, where the player stands, and the mod version it was made with ----
  /** Read a gzip NBT file (level.dat) into maps, lists and boxed values. Plain Java, no library. */
  static Object nbt(File f) throws IOException { return nbt(new GZIPInputStream(new FileInputStream(f))); }
  static Object nbt(InputStream raw) throws IOException { try (DataInputStream in = new DataInputStream(new BufferedInputStream(raw))) { int t = in.readByte(); in.readUTF(); return nbtTag(in, t); } }
  // ---- the saved blocks: region files (.mca) of the save over the generator's blocks ----
  static File SAVE;   /* the world folder, when one was given */
  static int savedChunks, genChunks, partChunks, savedSections, keptSections, changed;
  /** The region folder of planet face f in the save (face 0 the overworld, faces 1-5 hopelessinfinity:face_N). */
  static File regionDir(int f) { return f == 0 ? new File(SAVE, "region") : new File(SAVE, "dimensions/hopelessinfinity/face_" + f + "/region"); }
  static final Map<String, RandomAccessFile> REGIONS = new HashMap<>();
  /** One saved chunk's NBT, or null (not in the save, or not finished: Status other than full). Region format: a 4 KiB table of
   *  (offset in 4 KiB sectors << 8 | sector count), then each chunk: length, compression (1 gzip, 2 zlib, 3 none), NBT. */
  @SuppressWarnings("unchecked") static Map<String, Object> savedChunk(int f, int cx, int cz) throws IOException {
    File rf = new File(regionDir(f), "r." + (cx >> 5) + "." + (cz >> 5) + ".mca"); if (!rf.isFile()) return null;
    RandomAccessFile raf = REGIONS.get(rf.getPath()); if (raf == null) { raf = new RandomAccessFile(rf, "r"); REGIONS.put(rf.getPath(), raf); }
    if (raf.length() < 8192) return null; raf.seek(4L * ((cx & 31) + (cz & 31) * 32)); int loc = raf.readInt(); if (loc == 0) return null; long off = (long) (loc >>> 8) * 4096;
    raf.seek(off); int len = raf.readInt(), comp = raf.readByte(); if (len <= 1 || (comp & 0x80) != 0) return null; byte[] buf = new byte[len - 1]; raf.readFully(buf);
    InputStream in = comp == 1 ? new GZIPInputStream(new ByteArrayInputStream(buf)) : comp == 2 ? new InflaterInputStream(new ByteArrayInputStream(buf)) : new ByteArrayInputStream(buf);
    Map<String, Object> ch = (Map<String, Object>) nbt(in); String st = String.valueOf(ch.get("Status")); if (!st.equals("minecraft:full") && !st.equals("full")) { partChunks++; return null; } return ch; }
  /** The game's block string of a palette entry: name without "minecraft:", properties in brackets (water and lava by name only). */
  @SuppressWarnings("unchecked") static String blockString(Map<String, Object> e) { String n = String.valueOf(e.get("Name")); if (n.startsWith("minecraft:")) n = n.substring(10);
    if (n.equals("water") || n.equals("lava") || n.equals("air") || n.equals("cave_air") || n.equals("void_air")) return n.endsWith("air") ? "air" : n;
    Map<String, Object> pr = (Map<String, Object>) e.get("Properties"); if (pr == null || pr.isEmpty()) return n; StringBuilder sb = new StringBuilder(n).append('[');
    for (var p : new TreeMap<>(pr).entrySet()) sb.append(p.getKey()).append('=').append(p.getValue()).append(','); sb.setCharAt(sb.length() - 1, ']'); return sb.toString(); }
  /** Put the saved chunk's blocks over the generator's, section by section in the built box. A section the save does not hold keeps the
   *  generator's blocks. Block states: palette and packed indices (bits = max(4, ceil(log2 palette)), no entry across two longs, order y z x). */
  @SuppressWarnings("unchecked") static boolean overlaySaved(int f, int a, int b) { try { Map<String, Object> ch = savedChunk(f, a, b); if (ch == null) { genChunks++; return false; } savedChunks++;
      List<Object> secs = (List<Object>) ch.get("sections"); if (secs == null) return true;
      for (Object so : secs) { Map<String, Object> sec = (Map<String, Object>) so; int sy = ((Number) sec.get("Y")).intValue(), y0 = sy * 16; if (y0 + 15 < Y0 || y0 >= Y0 + NY) continue;
        Map<String, Object> bs = (Map<String, Object>) sec.get("block_states"); if (bs == null) { keptSections++; continue; } List<Object> pal = (List<Object>) bs.get("palette"); long[] data = (long[]) bs.get("data");
        short[] ids = new short[pal.size()]; for (int k = 0; k < ids.length; k++) ids[k] = id(blockString((Map<String, Object>) pal.get(k)));
        int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(Math.max(1, pal.size() - 1))), per = 64 / bits; long mask = (1L << bits) - 1; savedSections++;
        for (int i = 0; i < 4096; i++) { int pi = data == null ? 0 : (int) ((data[i / per] >>> ((i % per) * bits)) & mask); if (pi >= ids.length) pi = 0; int x = i & 15, z = (i >> 4) & 15, y = y0 + (i >> 8);
          int ci = x + a * 16 - X0, cj = y - Y0, ck = z + b * 16 - Z0; if (cj < 0 || cj >= NY) continue; int at = (cj * NZ + ck) * NX + ci; if (cells[at] != ids[pi]) { changed++; cells[at] = ids[pi]; } } }
      return true; } catch (IOException | RuntimeException ex) { System.out.println("saved chunk " + a + "," + b + " could not be read: " + ex); genChunks++; return false; } }
  static Object nbtTag(DataInputStream in, int t) throws IOException { switch (t) {
      case 1: return in.readByte(); case 2: return in.readShort(); case 3: return in.readInt(); case 4: return in.readLong(); case 5: return in.readFloat(); case 6: return in.readDouble();
      case 7: { byte[] b = new byte[in.readInt()]; in.readFully(b); return b; } case 8: return in.readUTF();
      case 9: { int et = in.readByte(), n = in.readInt(); List<Object> l = new ArrayList<>(); for (int i = 0; i < n; i++) l.add(nbtTag(in, et)); return l; }
      case 10: { Map<String, Object> m = new LinkedHashMap<>(); for (;;) { int ct = in.readByte(); if (ct == 0) return m; String k = in.readUTF(); m.put(k, nbtTag(in, ct)); } }
      case 11: { int n = in.readInt(); int[] v = new int[n]; for (int i = 0; i < n; i++) v[i] = in.readInt(); return v; }
      case 12: { int n = in.readInt(); long[] v = new long[n]; for (int i = 0; i < n; i++) v[i] = in.readLong(); return v; }
      default: throw new IOException("NBT tag " + t); } }
  @SuppressWarnings("unchecked") static Object path(Object o, String... keys) { for (String k : keys) { if (!(o instanceof Map)) return null; o = ((Map<String, Object>) o).get(k); } return o; }
  static int num0(Object o) { return o instanceof Number n ? n.intValue() : 0; }
  static double[] PLAYER;   /* x, y, z, yaw, pitch, face of the player in the save (place "player") */
  /** A world folder in place of a seed: the seed from level.dat, the player's place, and a warning when the save's mod jar is not the version
   *  this tool draws (the render is the generator's code: another version may lay the world differently). Returns the seed. */
  @SuppressWarnings("unchecked") static long fromWorld(File dir) throws IOException { File ld = new File(dir, "level.dat"); if (!ld.isFile()) throw new IOException("no level.dat in " + dir);
    SAVE = dir; Object root = nbt(ld), data = path(root, "Data"); Object sd = path(data, "WorldGenSettings", "seed"); if (sd == null) sd = path(data, "RandomSeed"); if (sd == null) throw new IOException("no seed in " + ld);
    long seed = (Long) sd; Object vn = path(data, "Version", "Name"); if (vn != null) VERSION = String.valueOf(vn); SPAWN = new int[]{num0(path(data, "SpawnX")), num0(path(data, "SpawnY")), num0(path(data, "SpawnZ"))};
    Object gt = path(data, "WorldGenSettings", "dimensions", "minecraft:overworld", "generator", "type"); SAVEONLY = gt == null || !String.valueOf(gt).startsWith("hopelessinfinity:") || System.getProperty("wv.saveonly") != null;
    System.out.println("world " + dir.getName() + " (\"" + path(data, "LevelName") + "\"): seed " + seed + ", Minecraft " + VERSION + ", overworld generator " + gt + (SAVEONLY ? " -> drawn from the save's blocks alone" : " -> Hopeless Infinity: the generator, with the save's blocks over it"));
    Object pl = path(data, "Player"); String plFrom = "level.dat";
    if (pl == null) { File[] pd = new File(dir, "playerdata").listFiles((d, n) -> n.endsWith(".dat")); if (pd != null && pd.length > 0) { Arrays.sort(pd, (x, y) -> Long.compare(y.lastModified(), x.lastModified())); pl = nbt(pd[0]); plFrom = "playerdata/" + pd[0].getName() + " (a server world: the player saved last)"; } }   /* a server keeps its players in playerdata/ */
    if (pl != null) { System.out.println("player from " + plFrom); List<Object> pos = (List<Object>) path(pl, "Pos"), rot = (List<Object>) path(pl, "Rotation"); Object dm0 = path(pl, "Dimension"); String dim = dm0 instanceof Integer di ? (di == -1 ? "minecraft:the_nether" : di == 1 ? "minecraft:the_end" : "minecraft:overworld") : String.valueOf(dm0); PLAYER_DIM = dim; int face = dim.equals("minecraft:overworld") ? 0 : dim.startsWith("hopelessinfinity:face_") ? Integer.parseInt(dim.substring(22)) : -1;
      if (pos != null) { PLAYER = new double[]{((Number) pos.get(0)).doubleValue(), ((Number) pos.get(1)).doubleValue(), ((Number) pos.get(2)).doubleValue(), rot == null ? 0 : ((Number) rot.get(0)).doubleValue(), rot == null ? 0 : ((Number) rot.get(1)).doubleValue(), face};
        System.out.printf("player at %.0f %.0f %.0f in %s%s%n", PLAYER[0], PLAYER[1], PLAYER[2], dim, face < 0 ? " (not a planet face: \"player\" can not be drawn; the tool draws the home planet's six faces)" : " (face " + face + ")"); } }
    else System.out.println("no player in level.dat or playerdata/: use a place other than \"player\"");
    File up1 = dir.getAbsoluteFile().getParentFile(), mods = new File(up1.getParentFile(), "mods"); if (!mods.isDirectory()) mods = new File(up1, "mods"); String me = System.getProperty("wv.mods", System.getenv("WV_MODS")); if (me != null) mods = new File(me); MODSDIR = mods.isDirectory() ? mods : null; if (SAVEONLY) return seed;   /* a client instance: <instance>/saves/<world>; a server: <server>/<world> */ String mine = com.hopeless.infinity.HopelessInfinity.VERSION, theirs = null;
    File[] js = mods.listFiles((d, n) -> n.startsWith("hopelessinfinity") && n.endsWith(".jar")); if (js != null) for (File j : js) try (ZipFile z = new ZipFile(j)) { ZipEntry e = z.getEntry("META-INF/mods.toml"); if (e == null) continue; var m = java.util.regex.Pattern.compile("version=\"([^\"]+)\"").matcher(new String(z.getInputStream(e).readAllBytes())); while (m.find()) if (!m.group(1).contains("$")) theirs = m.group(1); }
    if (theirs == null) System.out.println("mod version of the save: not found (no " + mods + "\\hopelessinfinity*.jar)"); else if (!theirs.equals(mine)) System.out.println("WARNING: the save's instance runs Hopeless Infinity " + theirs + "; this tool draws " + mine + ". Put the same version in both, or the picture can differ from the game.");
    else System.out.println("mod version " + mine + ": the same as the save's instance");
    return seed; }
  /** 0.29.8 THE ONE MINECRAFT RENDERER (Overlord, 2026-11-08: "combine all the renderers into one that we can continuously update ... if the
   *  renderer or tools cant do something in minecraft you adapt them instead of making a new tool"). Its own views: persp, iso, top (the
   *  world in 3D, generated or saved). The older views live on as WorldView views while they are folded in (each: view name -> its class):
   *  map:biome, map:rivers, map:sites, map:metro, map:metroroads, map:roads, planet, galaxy, sky, seabed, section:town, section:cave,
   *  seacave, seams, adart, blocks (a block scene, isometric). Usage for those: WorldView <view> <its own arguments>. */
  static final String[][] VIEWS = {{"map:biome", "BiomeMap"}, {"map:rivers", "RiverMap"}, {"map:sites", "SiteMap"}, {"map:metro", "MetroView"}, {"map:metroroads", "MetroRoadsView"}, {"map:roads", "RoadView"},
    {"planet", "PlanetRender"}, {"galaxy", "GalaxyRender"}, {"sky", "Gallery"}, {"seabed", "SeabedPreview"}, {"section:town", "TownSection"}, {"section:cave", "CaveSlice"}, {"seacave", "SeaCaveView"}, {"seams", "SeamScan"}, {"adart", "AdArtDump"}, {"blocks", "BlockIso"}};
  public static void main(String[] a) throws Exception { long T0 = System.nanoTime(); if (a.length > 0 && a[0].equals("--write-emit")) { writeEmit(a[1]); return; }
    if (a.length > 0 && (a[0].equals("views") || a[0].equals("--help"))) { System.out.println("WorldView views: persp, iso, top (WorldView <seed|world folder> <place> <view> <out.png> [w h] [k=v]); and " + Arrays.stream(VIEWS).map(v -> v[0]).reduce((x, y) -> x + ", " + y).orElse("") + " (WorldView <view> <its arguments>)"); return; }
    for (String[] v : VIEWS) if (a.length > 0 && a[0].equals(v[0])) { Class.forName(v[1]).getMethod("main", String[].class).invoke(null, (Object) Arrays.copyOfRange(a, 1, a.length)); return; }
    if (JAR == null) { System.out.println("Minecraft 1.20.1 client jar not found: set WV_MCJAR to its path (for example %USERPROFILE%\\curseforge\\minecraft\\Install\\versions\\1.20.1\\1.20.1.jar)"); return; }
    long seed = new File(a[0]).isDirectory() ? fromWorld(new File(a[0])) : Long.parseLong(a[0]);
    if (SAVE != null) { String mj = findMcJar(VERSION, SAVE.getAbsoluteFile().getParentFile().getParentFile()); if (mj != null) JAR = mj; openModJars(MODSDIR); }
    if (SAVEONLY) { saveMain(a, T0); return; } String place = a[1], view = a[2], out = a[3]; int W = a.length > 4 && a[4].matches("\\d+") ? Integer.parseInt(a[4]) : 1280, H = a.length > 5 && a[5].matches("\\d+") ? Integer.parseInt(a[5]) : 720;
    Map<String, Double> kv = new HashMap<>(); for (String s : a) if (s.contains("=") && !s.startsWith("probe=")) { String[] e = s.split("="); kv.put(e[0], Double.parseDouble(e[1])); }
    PlanetWorld w = new PlanetWorld(seed); int[] sp = w.playerSpawn(); int f = 0; double tx = sp[0] + 0.5, ty = sp[1], tz = sp[2] + 0.5, yaw = 180, pitch = 10; String label = place; double dist = 0;
    Roads rd = w.roads(); Towns tw = w.towns(); Metro mt = w.metro();
    if (place.equals("player")) { if (PLAYER == null || PLAYER[5] < 0) { System.out.println("no player on a planet face in this save"); return; } f = (int) PLAYER[5]; tx = PLAYER[0]; ty = PLAYER[1] + 1.62; tz = PLAYER[2]; yaw = PLAYER[3]; pitch = PLAYER[4]; label = "where the player stands (as saved)"; }
    else if (place.equals("spawn")) { label = "the player's spawn (" + w.spawnKind + ")"; ty += 1.62; }
    else if (place.equals("hq") || place.equals("gate")) { Metro.Plan p = home(w); double[] c = p.xz(place.equals("gate") ? -p.blvdHalf + 60 : 0, 0); tx = c[0]; tz = c[1]; ty = Column.of(w, p.face, (int) tx, (int) tz).surfaceY; f = p.face; dist = 220; pitch = 25; label = place + " of " + p.site.name; }
    else if (place.equals("quarry")) { Quarry.Site q = Quarry.site(w); tx = q.x(); tz = q.z(); ty = q.rimY() - 15; f = q.face(); dist = 170; pitch = 35; label = "Cobble & Sons origin quarry"; }
    else if (place.startsWith("town:") || place.startsWith("station:")) { String nm = place.substring(place.indexOf(':') + 1); Sites.Site s = null; for (Sites.Site q : w.sites().all) if (q.name.equalsIgnoreCase(nm) && !Metro.isCity(q) && (s == null || q.pop > s.pop)) s = q; /* names repeat: the largest place of that name */ Towns.Plan p = tw.plan(s); f = p.face;
      double[] uv = place.startsWith("station:") ? new double[]{tw.station(p)[1] - 20, 0} : new double[]{0, 0}; double x = p.alongX ? p.cx + uv[0] : p.cx + uv[1], z = p.alongX ? p.cz + uv[1] : p.cz + uv[0]; tx = x; tz = z; ty = Column.of(w, f, (int) x, (int) z).surfaceY; dist = place.startsWith("station:") ? 160 : 260; pitch = 30; label = place + " (" + s.kind + ", " + s.pop + " people)"; }
    else if (place.startsWith("rail:")) { String[] q = place.split(":"); Roads.Route r = rd.route(Integer.parseInt(q[1])); double at = Double.parseDouble(q[2]); int i0 = 0; for (int i = 0; i < r.n(); i++) if (Math.abs(r.along[i] - at) < Math.abs(r.along[i0] - at)) i0 = i; double[] lc = w.cube.locate(r.p(i0)); f = (int) lc[0]; tx = lc[1]; tz = lc[2]; ty = r.z[i0] >= 0 ? Terrain.toYd(r.z[i0]) : Column.of(w, f, (int) tx, (int) tz).surfaceY; dist = 60; pitch = 30; label = "main line " + q[1] + " at " + (int) at + " m"; }
    else if (place.startsWith("city:")) { String nm = place.substring(5); Metro.Plan p = null; for (Metro.Plan q : mt.plans.values()) if (q.site.name.equalsIgnoreCase(nm)) p = q; f = p.face; double[] c = p.xz(0, 0); tx = c[0]; tz = c[1]; ty = Column.of(w, f, (int) tx, (int) tz).surfaceY; dist = 900; pitch = 30; label = "city " + p.site.name; }
    else { String[] q = place.split(","); f = Integer.parseInt(q[0]); tx = Double.parseDouble(q[1]) + 0.5; tz = Double.parseDouble(q[2]) + 0.5; ty = Column.of(w, f, (int) tx, (int) tz).surfaceY + 1.62; }
    if (kv.getOrDefault("night", 0.0) > 0) { SKYMAX = 4; DAY = 0; SKY = 0x0A0E1C; FOG = 0x101828; }
    yaw = kv.getOrDefault("yaw", yaw); pitch = kv.getOrDefault("pitch", view.equals("top") ? 90 : view.equals("iso") ? 35 : pitch); dist = kv.getOrDefault("dist", dist); int R = (int) (double) kv.getOrDefault("radius", Math.max(160.0, dist + 120)); double fov = kv.getOrDefault("fov", 70.0);
    build(w, f, (int) Math.floor(tx), (int) Math.floor(tz), R, (int) ty);
    double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch); fwd = new double[]{-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr)}; right = norm(cross(fwd, new double[]{0, 1, 0})); if (Double.isNaN(right[0]) || Math.abs(fwd[1]) > 0.999) right = new double[]{-Math.cos(yr), 0, -Math.sin(yr)}; up = cross(right, fwd);
    ortho = !view.equals("persp"); eye = new double[]{tx - fwd[0] * dist, ty + kv.getOrDefault("cy", 0.0) - fwd[1] * dist, tz - fwd[2] * dist}; if (ortho) { eye = new double[]{tx - fwd[0] * 3000, ty - fwd[1] * 3000, tz - fwd[2] * 3000}; scale = kv.getOrDefault("span", 2.0 * R * 0.9) / W; }
    tanF = Math.tan(Math.toRadians(fov) / 2); fogEnd = R; fogStart = 0.75 * R;
    // the far terrain past the built radius: the mesh the game draws there (planet/FarMesh, built for this camera), rasterised
    float[] farZ = null; int[] farC = null; if (!ortho && kv.getOrDefault("far", 1.0) > 0) { long tf = System.nanoTime(); FarMesh fm = FarMesh.build(w, f, eye[0], eye[2], eye[1]); farZ = new float[W * H]; farC = new int[W * H]; Arrays.fill(farZ, Float.MAX_VALUE); HAZE = kv.getOrDefault("haze", 0.6931 * 60000 / 3.912); camAlt = eye[1] - Terrain.SEA; raster(fm, farZ, farC, W, H);
      System.out.printf("far terrain: %d triangles to %.0f km (FarMesh), drawn in %.1f s%n", fm.tris, fm.reachKm, (System.nanoTime() - tf) / 1e9); }
    final float[] FZ = farZ; final int[] FC = farC;
    BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB); final int WW = W, HH = H; java.util.concurrent.atomic.AtomicInteger rowN = new java.util.concurrent.atomic.AtomicInteger(); long tr = System.nanoTime();
    java.util.stream.IntStream.range(0, H).parallel().forEach(py -> { for (int px = 0; px < WW; px++) { int c;
        if (ortho) { double sx = (px - WW / 2.0) * scale, sy = (HH / 2.0 - py) * scale; c = shade(eye[0] + right[0] * sx + up[0] * sy, eye[1] + right[1] * sx + up[1] * sy, eye[2] + right[2] * sx + up[2] * sy, fwd[0], fwd[1], fwd[2]); }
        else { double sx = (2.0 * (px + 0.5) / WW - 1) * tanF * WW / HH, sy = (1 - 2.0 * (py + 0.5) / HH) * tanF; double[] d = norm(new double[]{fwd[0] + right[0] * sx + up[0] * sy, fwd[1] + right[1] * sx + up[1] * sy, fwd[2] + right[2] * sx + up[2] * sy}); c = shade(eye[0], eye[1], eye[2], d[0], d[1], d[2]); }
        if ((c & HIT) == 0 && FZ != null && FZ[py * WW + px] < Float.MAX_VALUE) c = FC[py * WW + px];   // nothing built hit: the far terrain behind
        im.setRGB(px, py, c & 0xFFFFFF); } rowN.incrementAndGet(); });
    for (String pa : a) if (pa.startsWith("probe=")) { String[] q = pa.substring(6).split(","); int px = Integer.parseInt(q[0]), py = Integer.parseInt(q[1]);   /* probe=px,py: what the pixel shows and why */
      double sx = (2.0 * (px + 0.5) / W - 1) * tanF * W / H, sy = (1 - 2.0 * (py + 0.5) / H) * tanF; double[] d = norm(new double[]{fwd[0] + right[0] * sx + up[0] * sy, fwd[1] + right[1] * sx + up[1] * sy, fwd[2] + right[2] * sx + up[2] * sy}); int c = shade(eye[0], eye[1], eye[2], d[0], d[1], d[2]);
      System.out.printf("probe %d,%d: ray dir %.3f %.3f %.3f; blocks %s colour %06X; far depth %s colour %06X; shown %06X%n", px, py, d[0], d[1], d[2], (c & HIT) != 0 ? "hit" : "miss", c & 0xFFFFFF, FZ == null || FZ[py * W + px] == Float.MAX_VALUE ? "none" : String.format("%.0f", FZ[py * W + px]), FC == null ? 0 : FC[py * W + px], im.getRGB(px, py) & 0xFFFFFF); }
    java.awt.Graphics2D g = im.createGraphics(); g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON); g.setColor(new java.awt.Color(0, 0, 0, 140)); g.fillRect(0, 0, W, 20); g.setColor(java.awt.Color.WHITE); g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 12));
    g.drawString(String.format("WorldView seed %d: %s; %s, yaw %.0f pitch %.0f; generator code (planet/ChunkFill), game models and textures; face %d (%.0f, %.0f, %.0f)", seed, label, view, yaw, pitch, f, tx, ty, tz), 6, 14); g.dispose();
    javax.imageio.ImageIO.write(im, "png", new File(out));
    System.out.printf("drew %d x %d in %.1f s; total %.1f s; %d block kinds, models missing for %s; drawn with its own shape (the game draws these with block-entity renderers) %s; textures missing %s; wrote %s%n", W, H, (System.nanoTime() - tr) / 1e9, (System.nanoTime() - T0) / 1e9, PAL.size(), NO_MODEL, BUILTIN, MISSING, out); }
  /** The save-only render (a world that is not Hopeless Infinity's): places player, spawn, x,z, x,y,z; dim=, ymin=, ymax= (see the head comment). */
  static void saveMain(String[] a, long T0) throws Exception { File dir = SAVE; String place = a[1], view = a[2], out = a[3]; int W = a.length > 4 && a[4].matches("\\d+") ? Integer.parseInt(a[4]) : 1280, H = a.length > 5 && a[5].matches("\\d+") ? Integer.parseInt(a[5]) : 720;
    Map<String, Double> kv = new HashMap<>(); String dimArg = null; for (String s : a) { if (s.startsWith("dim=")) dimArg = s.substring(4); else if (s.contains("=") && !s.startsWith("probe=") && s.indexOf('=') > 0 && s.substring(s.indexOf('=') + 1).matches("-?[0-9.]+")) { String[] e = s.split("="); kv.put(e[0], Double.parseDouble(e[1])); } }
    int[] spawn = SPAWN; if (JAR == null) { System.out.println("set WV_MCJAR to the Minecraft " + VERSION + " client jar"); return; }
    DIM = dimArg != null ? dimArg : place.equals("player") && PLAYER_DIM != null ? PLAYER_DIM : "minecraft:overworld"; REGION = regionDirOf(DIM); System.out.println("dimension " + DIM + ": " + REGION + (REGION.isDirectory() ? "" : " (NOT FOUND)"));
    double tx, ty = Double.NaN, tz, yaw = 180, pitch = 10, dist = 0; String label;
    if (place.equals("player")) { if (PLAYER == null) { System.out.println("no player in this save: use spawn or x,z"); return; } tx = PLAYER[0]; ty = PLAYER[1] + 1.62; tz = PLAYER[2]; yaw = PLAYER[3]; pitch = PLAYER[4]; label = "where the player stands (as saved)"; }
    else if (place.equals("spawn")) { tx = spawn[0] + 0.5; tz = spawn[2] + 0.5; label = "the world spawn"; dist = 40; pitch = 30; }
    else { String[] q = place.split(","); if (q.length == 2) { tx = Double.parseDouble(q[0]) + 0.5; tz = Double.parseDouble(q[1]) + 0.5; dist = 40; pitch = 30; } else { tx = Double.parseDouble(q[0]) + 0.5; ty = Double.parseDouble(q[1]); tz = Double.parseDouble(q[2]) + 0.5; } label = "block " + place; }
    if (kv.getOrDefault("night", 0.0) > 0) { SKYMAX = 4; DAY = 0; SKY = 0x0A0E1C; FOG = 0x101828; }
    if (DIM.equals("minecraft:the_nether")) { SKYMAX = 0; SKY = 0x330808; FOG = 0x3A0E0E; } else if (DIM.equals("minecraft:the_end")) { SKYMAX = 0; SKY = 0x14101C; FOG = 0x1C1626; }
    yaw = kv.getOrDefault("yaw", yaw); pitch = kv.getOrDefault("pitch", view.equals("top") ? 90 : view.equals("iso") ? 35 : pitch); dist = kv.getOrDefault("dist", dist); int R = (int) (double) kv.getOrDefault("radius", Math.max(160.0, dist + 120)); double fov = kv.getOrDefault("fov", 70.0);
    YMIN = kv.getOrDefault("ymin", Double.NaN); YMAX = kv.getOrDefault("ymax", Double.NaN); buildFromSave((int) Math.floor(tx), (int) Math.floor(tz), R);
    if (Double.isNaN(ty)) { int i = (int) Math.floor(tx) - X0, k = (int) Math.floor(tz) - Z0; ty = (i >= 0 && k >= 0 && i < NX && k < NZ ? skyTop[k * NX + i] : Y0 + NY / 2) + 1.62; }
    ty += kv.getOrDefault("cy", 0.0);
    double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch); fwd = new double[]{-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr)}; right = norm(cross(fwd, new double[]{0, 1, 0})); if (Double.isNaN(right[0]) || Math.abs(fwd[1]) > 0.999) right = new double[]{-Math.cos(yr), 0, -Math.sin(yr)}; up = cross(right, fwd);
    ortho = !view.equals("persp"); eye = new double[]{tx - fwd[0] * dist, ty - fwd[1] * dist, tz - fwd[2] * dist}; if (ortho) { eye = new double[]{tx - fwd[0] * 3000, ty - fwd[1] * 3000, tz - fwd[2] * 3000}; scale = kv.getOrDefault("span", 2.0 * R * 0.9) / W; }
    tanF = Math.tan(Math.toRadians(fov) / 2); fogEnd = R; fogStart = 0.75 * R;
    BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB); final int WW = W, HH = H; long tr = System.nanoTime();
    java.util.stream.IntStream.range(0, H).parallel().forEach(py -> { for (int px = 0; px < WW; px++) im.setRGB(px, py, pixel(px, py, WW, HH) & 0xFFFFFF); });
    for (String pa : a) if (pa.startsWith("probe=")) { String[] q = pa.substring(6).split(","); int px = Integer.parseInt(q[0]), py = Integer.parseInt(q[1]); int c = pixel(px, py, W, H);
      System.out.printf("probe %d,%d: blocks %s, colour %06X%n", px, py, (c & HIT) != 0 ? "hit" : "missed (sky or fog)", c & 0xFFFFFF); }
    java.awt.Graphics2D g = im.createGraphics(); g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON); g.setColor(new java.awt.Color(0, 0, 0, 140)); g.fillRect(0, 0, W, 20); g.setColor(java.awt.Color.WHITE); g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 12));
    g.drawString(String.format("WorldView (save) %s: %s; %s, yaw %.0f pitch %.0f; blocks from the save, game models and textures; %s (%.0f, %.0f, %.0f)", dir.getName(), label, view, yaw, pitch, DIM, tx, ty, tz), 6, 14); g.dispose();
    javax.imageio.ImageIO.write(im, "png", new File(out));
    System.out.printf("drew %d x %d in %.1f s; total %.1f s; %d block kinds; models missing for %s; drawn with own simple shapes %s; textures missing %s; wrote %s%n", W, H, (System.nanoTime() - tr) / 1e9, (System.nanoTime() - T0) / 1e9, PAL.size(), NO_MODEL, BUILTIN, MISSING, out); }

  static int pixel(int px, int py, int W, int H) { if (ortho) { double sx = (px - W / 2.0) * scale, sy = (H / 2.0 - py) * scale; return shade(eye[0] + right[0] * sx + up[0] * sy, eye[1] + right[1] * sx + up[1] * sy, eye[2] + right[2] * sx + up[2] * sy, fwd[0], fwd[1], fwd[2]); }
    double sx = (2.0 * (px + 0.5) / W - 1) * tanF * W / H, sy = (1 - 2.0 * (py + 0.5) / H) * tanF; double[] d = norm(new double[]{fwd[0] + right[0] * sx + up[0] * sy, fwd[1] + right[1] * sx + up[1] * sy, fwd[2] + right[2] * sx + up[2] * sy}); return shade(eye[0], eye[1], eye[2], d[0], d[1], d[2]); }
  /** Rasterise the far mesh (vertex positions relative to the camera, blocks) with the camera's projection: depth-tested, vertex colours,
   *  the flat face shade of its normal, the distance haze. Cells inside the built radius are left out (the blocks are drawn there). */
  static final double[] SUNV = norm(new double[]{0.35, 0.85, 0.40});   /* CHOSEN: a mid-morning sun for the far mesh's shade (the game's far.glsl: ambient 0.35 + direct) */
  static double camAlt = 0;   /* the camera's height over the sea (m), for the haze's thinning with height */
  /** The game's haze (shaders/far.glsl, client/FarTerrain): half the ground hidden at 0.6931 VIS_M / 3.912 (VIS_M 60 km), thinning with
   *  height (scale height 1200 m); dist and dy (the point's height over the camera) in blocks. Returns the fog fraction 0..1. */
  static double airFade(double dist, double dy) { double half = HAZE > 0 ? HAZE : 0.6931 * 60000 / 3.912, H = 1200, hc = Math.max(0, camAlt), hp = Math.max(0, camAlt + dy), dh = hc - hp;
    double mean = Math.abs(dh) < 1 ? Math.exp(-hc / H) : H * (Math.exp(-hp / H) - Math.exp(-hc / H)) / dh; return 1 - Math.exp(-0.6931 * dist / Math.max(1, half) * mean); }
  /** Rasterise the far mesh (vertex positions relative to the camera, blocks) with the camera's projection: clipped at the near plane,
   *  depth-tested, colours and the game shader's light per vertex (far.glsl: ambient 0.35 + sun), the game's haze per pixel. Triangles
   *  wholly inside the built box are left out (the blocks are drawn there). */
  static void raster(FarMesh fm, float[] zb, int[] cb, int W, int H) { double asp = (double) W / H; int built = 0;
    for (int t = 0; t < fm.tris; t++) { boolean inside = true; double[][] cv = new double[3][];   /* camera space x, y, z, then r, g, b, world dy, dist */
      for (int k = 0; k < 3; k++) { int vi = fm.idx[t * 3 + k]; double x = fm.pos[vi * 3], y = fm.pos[vi * 3 + 1], z = fm.pos[vi * 3 + 2]; if (Math.abs(x + eye[0] - (X0 + NX / 2.0)) > NX / 2.0 - 8 || Math.abs(z + eye[2] - (Z0 + NZ / 2.0)) > NZ / 2.0 - 8) inside = false;
        double nx = fm.nrm[vi * 3], ny = fm.nrm[vi * 3 + 1], nz = fm.nrm[vi * 3 + 2]; double sun = Math.max(0, nx * SUNV[0] + ny * SUNV[1] + nz * SUNV[2]), li = (0.35 + 0.65 * sun) * (0.15 + 0.85 * DAY);   /* far.glsl: daylight 0..1 */
        cv[k] = new double[]{x * right[0] + y * right[1] + z * right[2], x * up[0] + y * up[1] + z * up[2], x * fwd[0] + y * fwd[1] + z * fwd[2], fm.col[vi * 3] * li, fm.col[vi * 3 + 1] * li, fm.col[vi * 3 + 2] * li, y, Math.sqrt(x * x + y * y + z * z)}; }
      if (inside) { built++; continue; }
      List<double[]> poly = new ArrayList<>(4); final double NEAR = 0.5;   /* clip at the near plane (Sutherland-Hodgman): big near cells reach behind the camera */
      for (int k = 0; k < 3; k++) { double[] p = cv[k], q = cv[(k + 1) % 3]; boolean pin = p[2] >= NEAR, qin = q[2] >= NEAR; if (pin) poly.add(p);
        if (pin != qin) { double s = (NEAR - p[2]) / (q[2] - p[2]); double[] m = new double[8]; for (int c = 0; c < 8; c++) m[c] = p[c] + (q[c] - p[c]) * s; poly.add(m); } }
      if (poly.size() < 3) continue;
      double[][] v = new double[poly.size()][]; for (int k = 0; k < v.length; k++) { double[] p = poly.get(k); v[k] = new double[]{(p[0] / p[2] / (tanF * asp) + 1) / 2 * W, (1 - p[1] / p[2] / tanF) / 2 * H, p[2]}; }
      for (int k = 1; k + 1 < v.length; k++) tri(v[0], v[k], v[k + 1], poly.get(0), poly.get(k), poly.get(k + 1), zb, cb, W, H); } }
  static void tri(double[] a, double[] b, double[] c, double[] pa, double[] pb, double[] pc, float[] zb, int[] cb, int W, int H) {
    int x0 = (int) Math.max(0, Math.floor(Math.min(a[0], Math.min(b[0], c[0])))), x1 = (int) Math.min(W - 1, Math.ceil(Math.max(a[0], Math.max(b[0], c[0])))), y0 = (int) Math.max(0, Math.floor(Math.min(a[1], Math.min(b[1], c[1])))), y1 = (int) Math.min(H - 1, Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
    double area = (b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1]); if (Math.abs(area) < 1e-9) return;
    for (int py = y0; py <= y1; py++) for (int px = x0; px <= x1; px++) { double cx = px + 0.5, cy = py + 0.5; double w0 = ((b[0] - cx) * (c[1] - cy) - (c[0] - cx) * (b[1] - cy)) / area, w1 = ((c[0] - cx) * (a[1] - cy) - (a[0] - cx) * (c[1] - cy)) / area, w2 = 1 - w0 - w1;
      if (w0 < 0 || w1 < 0 || w2 < 0) continue;
      double iz = w0 / a[2] + w1 / b[2] + w2 / c[2], z = 1 / iz; int i = py * W + px; if (z >= zb[i]) continue; zb[i] = (float) z;   /* perspective-correct: attributes over z */
      double q0 = w0 / a[2] * z, q1 = w1 / b[2] * z, q2 = w2 / c[2] * z; double[] at = new double[8]; for (int k = 3; k < 8; k++) at[k] = q0 * pa[k] + q1 * pb[k] + q2 * pc[k];
      double fa = airFade(at[7], at[6]); int r = clamp(at[3] * 255 * (1 - fa) + (FOG >> 16 & 255) * fa), g = clamp(at[4] * 255 * (1 - fa) + (FOG >> 8 & 255) * fa), bl = clamp(at[5] * 255 * (1 - fa) + (FOG & 255) * fa); cb[i] = (r << 16) | (g << 8) | bl; } }
  static Metro.Plan home(PlanetWorld w) { for (Metro.Plan q : w.metro().plans.values()) if (q.face == 0 && w.spawnKind.contains(q.site.name)) return q; return null; }
  static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
  static double[] norm(double[] v) { double l = Math.sqrt(dot(v, v)); return new double[]{v[0] / l, v[1] / l, v[2] / l}; }
}
