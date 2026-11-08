import com.hopeless.infinity.planet.*; import java.io.*; import java.util.*;
/** ChunkSim seed out-prefix [chunksRadius] [towerHeightM] [latest.log]: the chunk tick simulator (after the Space Engineers tick tool, D:\Claude\Tools\TickSim).
 *  It runs the REAL world-generation code that has no game classes, chunk by chunk, and times it:
 *   - planet columns (Column.of over the 18 x 18 ring the generator reads) and town buildings (Buildings.chunk), for the chunks round the
 *     spawn (the area a first visit generates);
 *   - a supertall tower (planet/Tower) over every chunk of its footprint: the at() calls the generator would make and the blocks it sets.
 *  The game's own block setting cannot run here: it is MODELLED per block from the in-game probe (latest.log "chunks N (avg X ms, blocks
 *  set B)": the time left over after the measured pure part, shared over the blocks set), and the model is labelled in the output.
 *  Writes out-prefix.csv (one row a chunk), out-prefix.txt (the summary). Saved tool. */
public class ChunkSim {
  public static void main(String[] a) throws Exception { long seed = Long.parseLong(a[0]); String out = a[1]; int R = a.length > 2 ? Integer.parseInt(a[2]) : 16; int TH = a.length > 3 ? Integer.parseInt(a[3]) : 1650;
    // the in-game probe this model is fitted to (0.25.1 log, 2026-11-04 09:36): 3556 chunks, avg 1.91 ms, 15,101,869 blocks set
    double GAME_MS = 1.91, GAME_BLOCKS = 15101869.0 / 3556, gameNsBlock = Double.NaN, gameRingMs = Double.NaN;
    // 0.26.0: the game's own split (Probe "chunk phases: column ring X ms, block-set loop Y ms (Z ns a block)") when a log has it
    if (a.length > 4) for (String ln : java.nio.file.Files.readAllLines(java.nio.file.Path.of(a[4]))) { java.util.regex.Matcher m = java.util.regex.Pattern.compile("column ring ([0-9.]+) ms, block-set loop [0-9.]+ ms \\(([0-9.]+) ns a block\\)").matcher(ln); if (m.find()) { gameRingMs = Double.parseDouble(m.group(1)); gameNsBlock = Double.parseDouble(m.group(2)); } }
    PlanetWorld w = new PlanetWorld(seed); Towns tw = w.towns(); int[] sp = w.playerSpawn();   /* 0.27.0 the player's spawn (the HQ door in a megacity): the chunks a first visit makes */ int scx = Math.floorDiv(sp[0], 16), scz = Math.floorDiv(sp[2], 16);
    // warm-up (caches, sites, roads), not timed
    for (int i = -2; i <= 2; i++) for (int k = -2; k <= 2; k++) planetChunk(w, tw, (scx + i) * 16, (scz + k) * 16);
    PrintWriter csv = new PrintWriter(new FileWriter(out + ".csv")); csv.println("kind,cx,cz,pure_ms,blocks,model_ms");
    List<double[]> base = new ArrayList<>();
    for (int i = -R; i < R; i++) for (int k = -R; k < R; k++) { long t0 = System.nanoTime(); int blocks = planetChunk(w, tw, (scx + i) * 16, (scz + k) * 16); double ms = (System.nanoTime() - t0) / 1e6; base.add(new double[]{ms, blocks}); }
    double pureMean = base.stream().mapToDouble(x -> x[0]).average().orElse(0);
    double perBlock = Double.isNaN(gameNsBlock) ? Double.NaN : gameNsBlock / 1e6;   // MEASURED in game (Probe), not estimated: none until a 0.26.0+ log is given
    for (int j = 0; j < base.size(); j++) { double[] b = base.get(j); csv.printf(Locale.ROOT, "planet,%d,%d,%.3f,%d,%.3f%n", j / (2 * R), j % (2 * R), b[0], (int) b[1], b[0] + (GAME_BLOCKS + b[1]) * perBlock); }
    // the tower: every chunk its footprint touches
    Tower t = new Tower("sim", TH, 1); int r = t.reach(), c0 = Math.floorDiv(-r, 16), c1 = Math.floorDiv(r, 16), top = t.top(); List<double[]> tow = new ArrayList<>();
    for (int i = 0; i < 2; i++) towerChunk(t, 0, 0, top);   // warm-up
    for (int cx = c0; cx <= c1; cx++) for (int cz = c0; cz <= c1; cz++) { long t0 = System.nanoTime(); int n = towerChunk(t, cx * 16, cz * 16, top); double ms = (System.nanoTime() - t0) / 1e6; tow.add(new double[]{ms, n, cx, cz});
      csv.printf(Locale.ROOT, "tower,%d,%d,%.3f,%d,%.3f%n", cx, cz, ms, n, ms + n * perBlock); }
    csv.close();
    double[] pm = stats(base, 0), tp = stats(tow, 0), tb = stats(tow, 1); double[] model = new double[tow.size()]; for (int i = 0; i < tow.size(); i++) model[i] = tow.get(i)[0] + tow.get(i)[1] * perBlock; Arrays.sort(model);
    double mm = Arrays.stream(model).average().orElse(0), m99 = model[(int) Math.min(model.length - 1, Math.floor(0.99 * model.length))], mw = model[model.length - 1];
    String s = String.format(Locale.ROOT, "ChunkSim seed %d: %d planet chunks round the spawn (%d, %d)%n  planet pure part (columns + buildings): mean %.2f ms, 99%% %.2f, worst %.2f%n" +
      "  game block-set cost: %s%n" +
      "  tower %d m (%s): %d chunks; at() part mean %.2f ms, 99%% %.2f, worst %.2f; blocks set a chunk mean %.0f, worst %.0f%n" +
      "  tower chunk, pure + MODEL: mean %.1f ms, 99%% %.1f, worst %.1f (an ordinary chunk: %.2f ms in game)%n  whole tower: %.1f s of chunk work%n",
      seed, base.size(), sp[0], sp[2], pm[0], pm[1], pm[2], Double.isNaN(perBlock) ? "NOT MEASURED YET (needs a 0.26.0 game log with the chunk-phase probe; the tower rows below are the pure part only)" : String.format(Locale.ROOT, "%.1f ns a block (measured in game; column ring %.2f ms in game)", gameNsBlock, gameRingMs), TH, t.probe(), tow.size(), tp[0], tp[1], tp[2], tb[0], tb[2], mm, m99, mw, GAME_MS, Arrays.stream(model).sum() / 1000);
    try (PrintWriter p = new PrintWriter(new FileWriter(out + ".txt"))) { p.print(s); } System.out.print(s); }
  static double[] stats(List<double[]> l, int k) { double[] v = l.stream().mapToDouble(x -> x[k]).sorted().toArray(); return new double[]{Arrays.stream(v).average().orElse(0), v[(int) Math.min(v.length - 1, Math.floor(0.99 * v.length))], v[v.length - 1]}; }
  /** What the generator does for one chunk that needs no game classes: the column ring, then the buildings. Returns the blocks the buildings set. */
  static int planetChunk(PlanetWorld w, Towns tw, int x0, int z0) { int face = 0; long sum = 0;
    for (int x = -1; x <= 16; x++) for (int z = -1; z <= 16; z++) sum += Column.of(w, face, x0 + x, z0 + z).surfaceY;
    int[] n = {0}; double[] sc = w.cube.spherePoint(face, x0 + 8, z0 + 8); Buildings.chunk(tw, face, x0, z0, x0 + 16, z0 + 16, sc, (x, y, z, b) -> n[0]++);
    Metro m = w.metro(); if (m != null) m.chunk(face, x0, z0, x0 + 16, z0 + 16, sc, (x, y, z, b) -> { if (!b.equals("air")) n[0]++; });   // 0.27.0 the megacity's towers
    return n[0] + (int) (sum & 0); }
  /** The tower's blocks in one chunk (tower frame = world frame here): the at() calls the generator would make; returns the non-air blocks. */
  static int towerChunk(Tower t, int x0, int z0, int top) { int n = 0;
    for (int x = x0; x < x0 + 16; x++) for (int z = z0; z < z0 + 16; z++) { if (t.at(x, 0, z) == null && t.at(x, Tower.PODIUM_FLOORS * Tower.FLOOR + 1, z) == null) continue;
      for (int y = 0; y < top; y++) { String b = t.at(x, y, z); if (b != null && !b.equals("air")) n++; } }
    return n; }
}
