import java.util.*; import java.util.regex.*;
/** BlockStates: is a block-state string real? Names, property names and values from the 1.20.1 client jar's blockstate files and this mod's
 *  own (src/main/resources/assets/hopelessinfinity/blockstates). Saved tool (TowerTest; TownsTest has its own copy of the same check). */
public class BlockStates {
  static final String JAR = "/mnt/user-data/uploads/Install/versions/1.20.1/1.20.1.jar"; static Map<String, Map<String, Set<String>>> props;
  static void read(String key, String js) { Map<String, Set<String>> pm = props.computeIfAbsent(key, k -> new HashMap<>());
    Matcher m = Pattern.compile("\"([a-z_]+=[^\"]*)\"\\s*:").matcher(js); while (m.find()) for (String kv : m.group(1).split(",")) { String[] q = kv.split("="); if (q.length == 2) pm.computeIfAbsent(q[0], k -> new HashSet<>()).addAll(Arrays.asList(q[1].split("\\|"))); }
    Matcher m2 = Pattern.compile("\"([a-z_]+)\"\\s*:\\s*\"([a-z0-9_|]+)\"").matcher(js); while (m2.find()) if (!m2.group(1).equals("model")) pm.computeIfAbsent(m2.group(1), k -> new HashSet<>()).addAll(Arrays.asList(m2.group(2).split("\\|"))); }
  static synchronized void load() throws Exception { if (props != null) return; props = new HashMap<>();
    try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(JAR)) { for (var en = z.entries(); en.hasMoreElements(); ) { var e = en.nextElement(); String nm = e.getName(); if (!nm.startsWith("assets/minecraft/blockstates/") || !nm.endsWith(".json")) continue; read(nm.substring(29, nm.length() - 5), new String(z.getInputStream(e).readAllBytes())); } }
    for (java.io.File bf : Objects.requireNonNull(new java.io.File("src/main/resources/assets/hopelessinfinity/blockstates").listFiles())) read("hopelessinfinity:" + bf.getName().replace(".json", ""), new String(java.nio.file.Files.readAllBytes(bf.toPath()))); }
  /** null when the string is a real block state, else why not. */
  public static String bad(String b) throws Exception { load();
    if (b.startsWith("shape:")) { String[] p = b.split(":", 4); if (p.length < 4) return "bad shape string"; if (!com.hopeless.infinity.slope.ShapeLib.INDEX.containsKey(p[1])) return "no shape " + p[1]; int o = Integer.parseInt(p[2]); if (o < 0 || o > 23) return "turn " + o; return bad(p[3]); }   /* 0.26.3 shape:<id>:<turn>:<material> */
    if (b.startsWith("sign:")) { String[] p = b.substring(5).split("\\|", -1); if (p.length != 7) return "sign string needs state|colour|glow|4 lines"; for (int i = 3; i < 7; i++) if (p[i].length() > 15) return "sign line over 15 characters: " + p[i];
      if (!java.util.Set.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black").contains(p[1])) return "no dye colour " + p[1]; return bad(p[0]); }   /* 0.27.4 a sign with its text */
    String name = b.contains("[") ? b.substring(0, b.indexOf('[')) : b; if (name.startsWith("minecraft:")) name = name.substring(10); if (name.equals("air")) return null;
    Map<String, Set<String>> pm = props.get(name); if (pm == null) return "no such block";
    if (name.endsWith("_bed")) { pm = new HashMap<>(pm); pm.put("facing", Set.of("north", "south", "east", "west")); pm.put("part", Set.of("head", "foot")); pm.put("occupied", Set.of("true", "false")); }   // beds draw with a block entity: their file lists no states
    if (name.endsWith("_wall_sign") || name.endsWith("_wall_hanging_sign")) { pm = new HashMap<>(pm); pm.put("facing", Set.of("north", "south", "east", "west")); pm.put("waterlogged", Set.of("true", "false")); }   // 0.27.4 signs draw with a block entity too
    if (name.endsWith("_sign") && !name.endsWith("_wall_sign") && !name.endsWith("hanging_sign")) { pm = new HashMap<>(pm); Set<String> r = new HashSet<>(); for (int i = 0; i < 16; i++) r.add("" + i); pm.put("rotation", r); pm.put("waterlogged", Set.of("true", "false")); }   // 0.28.5 standing signs (rotation 0-15)
    if (name.endsWith("_banner") && !name.endsWith("_wall_banner")) { pm = new HashMap<>(pm); Set<String> r = new HashSet<>(); for (int i = 0; i < 16; i++) r.add("" + i); pm.put("rotation", r); }   // banners too
    if (b.contains("[")) for (String kv : b.substring(b.indexOf('[') + 1, b.length() - 1).split(",")) { String[] q = kv.split("="); if (!pm.containsKey(q[0])) return "no property " + q[0]; if (!pm.get(q[0]).contains(q[1])) return q[0] + " has no value " + q[1]; }
    return null; }
}
