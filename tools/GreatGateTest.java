import com.hopeless.infinity.planet.*; import com.hopeless.infinity.slope.ShapeLib; import java.util.*;
/** GreatGateTest: 0.27.1 the megacity's great gate (planet/GreatGate), measured from the shapes' own geometry (ShapeLib.mesh: what the game
 *  draws) against ADA 405 / 505 (ramps, handrails), IBC 1015 (guards), the ring's size, the gate closed, and the gate in the spawn city's
 *  plaza (planet/Metro). Saved tool, part of the suite. */
public class GreatGateTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  static final double IN = 0.0254;
  /** The top of block string b in its cell at local (fx, fz) in 0..1 (a full block 1, a shape: its highest upward face over that point), or 0. */
  static double top(String b, double fx, double fz) { if (b == null || b.equals("air")) return 0; if (!b.startsWith("shape:")) return 1; String[] p = b.split(":", 4); int si = ShapeLib.INDEX.get(p[1]), o = Integer.parseInt(p[2]); double best = 0;
    for (ShapeLib.Face f : ShapeLib.mesh(si, o)) { if (f.n[1] < 0.05) continue; double d = f.n[0] * f.v[0][0] + f.n[1] * f.v[0][1] + f.n[2] * f.v[0][2]; double y = (d - f.n[0] * fx - f.n[2] * fz) / f.n[1];
      double mnx = 9, mxx = -9, mnz = 9, mxz = -9; for (double[] v : f.v) { mnx = Math.min(mnx, v[0]); mxx = Math.max(mxx, v[0]); mnz = Math.min(mnz, v[2]); mxz = Math.max(mxz, v[2]); }
      if (fx >= mnx - 1e-9 && fx <= mxx + 1e-9 && fz >= mnz - 1e-9 && fz <= mxz + 1e-9) best = Math.max(best, y); }
    return best; }
  /** The highest point of a block string's geometry in its cell (rails: the bar's top). */
  static double peak(String b) { if (b == null) return 0; if (!b.startsWith("shape:")) return 1; String[] p = b.split(":", 4); double m = 0; for (ShapeLib.Face f : ShapeLib.mesh(ShapeLib.INDEX.get(p[1]), Integer.parseInt(p[2]))) for (double[] v : f.v) m = Math.max(m, v[1]); return m; }
  /** The walking surface over the plaza at frame x + fx (fx 0..1), z: the highest top in the column. */
  static double surf(int x, double fx, int z) { double s = 0; for (int y = 0; y <= 2; y++) { double t = top(GreatGate.at(x, y, z), fx, 0.5); if (t > 0) s = Math.max(s, y + t); } return s; }
  public static void main(String[] a) throws Exception {
    // every block string real
    Set<String> kinds = new TreeSet<>(); List<String> bad = new ArrayList<>(); int cells = 0;
    for (int x = GreatGate.X0; x <= GreatGate.X1; x++) for (int y = 0; y <= GreatGate.TOP; y++) for (int z = GreatGate.Z0; z <= GreatGate.Z1; z++) { String b = GreatGate.at(x, y, z); if (b == null) continue; cells++; if (kinds.add(b)) { String w = BlockStates.bad(b); if (w != null) bad.add(b + " (" + w + ")"); } }
    ok(bad.isEmpty(), "every block string exists in the game (" + kinds.size() + " kinds, " + cells + " blocks)" + (bad.isEmpty() ? "" : ": " + bad.subList(0, Math.min(4, bad.size()))));
    // the ramps: both sides, measured along the middle line at 16 points a block
    for (int side = 0; side < 2; side++) { String nm = side == 0 ? "-x ramp" : "+x ramp"; List<double[]> prof = new ArrayList<>();   // {distance from the bottom (m), surface (m)}
      for (int i = GreatGate.RAMP_LEN - 1; i >= 0; i--) { int x = side == 0 ? GreatGate.DAIS_X0 - 1 - i : GreatGate.DAIS_X1 + 1 + i; for (int q = 0; q < 16; q++) { double fx = (q + 0.5) / 16, along = (GreatGate.RAMP_LEN - 1 - i) + (side == 0 ? fx : 1 - fx); prof.add(new double[]{along, surf(x, fx, 0)}); } }
      prof.sort(Comparator.comparingDouble(p -> p[0]));
      double maxSlope = 0, maxStep = 0; for (int k = 1; k < prof.size(); k++) { double dx = prof.get(k)[0] - prof.get(k - 1)[0], dz = prof.get(k)[1] - prof.get(k - 1)[1]; maxSlope = Math.max(maxSlope, dz / dx); maxStep = Math.max(maxStep, Math.abs(dz)); }
      ok(maxSlope <= 1 / 12.0 + 1e-6, String.format("%s: steepest running slope 1:%.1f (ADA 405.2: 1:12 at most)", nm, 1 / maxSlope));
      // runs and the landing: split where the surface is level for 1.5 m or more
      double flatStart = -1, flatLen = 0; for (double[] p : prof) if (Math.abs(p[1] - 0.5) < 1e-6) { if (flatStart < 0) flatStart = p[0]; flatLen = p[0] - flatStart + 1 / 16.0; }
      double rise1 = 0.5, rise2 = prof.get(prof.size() - 1)[1] - 0.5;
      ok(flatLen >= 60 * IN - 1e-6, String.format("%s: middle landing %.2f m long (ADA 405.7.3: 60 in = 1.52 m)", nm, flatLen));
      ok(rise1 <= 30 * IN && rise2 <= 30 * IN + 1e-6, String.format("%s: rise of each run %.2f / %.2f m (ADA 405.6: 30 in = 0.76 m at most)", nm, rise1, rise2));
      double topEnd = surf(side == 0 ? GreatGate.DAIS_X0 - 1 : GreatGate.DAIS_X1 + 1, side == 0 ? 1 : 0, 0); ok(Math.abs(topEnd - 1) < 1e-6, String.format("%s: the ramp meets the dais top (%.3f m at the dais edge)", nm, topEnd));
      // clear width and the handrails (both sides) over every point of the ramp
      int x0 = side == 0 ? GreatGate.DAIS_X0 - 1 : GreatGate.DAIS_X1 + 1; int clear = 0; for (int z = -10; z <= 10; z++) if (GreatGate.at(x0, 0, z) != null && GreatGate.at(x0, 0, z).contains("steel_grate")) clear++;
      ok(clear * 1.0 >= 36 * IN, String.format("%s: clear width %d blocks between the rails (ADA 405.5: 36 in)", nm, clear));
      double lo = 9, hi = -9; for (int i = 0; i < GreatGate.RAMP_LEN; i++) { int x = side == 0 ? GreatGate.DAIS_X0 - 1 - i : GreatGate.DAIS_X1 + 1 + i;
        for (int zs : new int[]{-1, 1}) { int zr = zs * (GreatGate.HALF_W + 1); double rail = 0; for (int y = 0; y <= 2; y++) { String b = GreatGate.at(x, y, zr); if (b != null && b.contains("rail_")) rail = Math.max(rail, y + peak(b)); }
          for (int q = 0; q < 16; q++) { double h = rail - surf(x, (q + 0.5) / 16, zs * GreatGate.HALF_W); lo = Math.min(lo, h); hi = Math.max(hi, h); } } }
      ok(lo >= 34 * IN - 1e-6 && hi <= 38 * IN + 1e-6, String.format("%s: handrail tops %.1f-%.1f in over the ramp, both sides (ADA 505.4: 34-38 in)", nm, lo / IN, hi / IN));
      int ext = side == 0 ? GreatGate.X0 : GreatGate.X1; String eb = GreatGate.at(ext, 0, GreatGate.HALF_W + 1); double eh = eb == null ? 0 : peak(eb);
      ok(eb != null && eh >= 34 * IN && eh <= 38 * IN, String.format("%s: handrail extends a block past the bottom at %.1f in (ADA 505.10.2: 12 in)", nm, eh / IN)); }
    // guards round the dais: 42 in at least over the dais top, wherever the edge is not a ramp opening
    { double lo = 9; int n = 0; for (int x = GreatGate.DAIS_X0; x <= GreatGate.DAIS_X1; x++) for (int z = -GreatGate.DAIS_Z; z <= GreatGate.DAIS_Z; z++) { boolean edge = x == GreatGate.DAIS_X0 || x == GreatGate.DAIS_X1 || Math.abs(z) == GreatGate.DAIS_Z; if (!edge) continue;
        boolean opening = (x == GreatGate.DAIS_X0 || x == GreatGate.DAIS_X1) && Math.abs(z) <= GreatGate.HALF_W; if (opening) continue; double g = 0; for (int y = 1; y <= 2; y++) { String b = GreatGate.at(x, y, z); if (b != null && b.contains("rail_")) g = Math.max(g, y + peak(b)); }
        if (GreatGate.at(x, 1, z) != null && !GreatGate.at(x, 1, z).contains("rail_")) continue;   // the ring itself
        lo = Math.min(lo, g - 1); n++; }
      ok(lo >= 42 * IN - 1e-6, String.format("dais guards: %d edge cells, lowest %.1f in over the dais (IBC 1015.3: 42 in; the drop is 39 in, over IBC 1015.2's 30 in)", n, lo / IN)); }
    // the ring: an 11-block aperture on the dais, 15 blocks across, the round edges' solid toward the band, closed
    { int wide = 1; for (int z = 1; GreatGate.at(-1, 6, z) == null && z < 20; z++) wide++; for (int z = -1; GreatGate.at(-1, 6, z) == null && z > -20; z--) wide++;   // the free run through the axis
      int tall = 0; for (int y = 1; y <= GreatGate.TOP; y++) if (GreatGate.at(-1, y, 0) == null) tall++; else if (tall > 0) break;
      int outer = 0; for (int z = -14; z <= 14; z++) for (int y = 0; y <= GreatGate.TOP; y++) if (GreatGate.at(-1, y, z) != null && GreatGate.at(-1, y, z).matches(".*(gate_frame|blackstone|sea_lantern).*")) { outer = Math.max(outer, Math.abs(z)); }
      int ap = 2 * (int) Math.floor(GreatGate.RI - 1e-9) + 1; ok(wide == ap && tall == ap, String.format("ring aperture %d wide x %d high (RI %.1f: %d), floor on the dais", wide, tall, GreatGate.RI, ap));
      ok(outer * 2 + 1 == 2 * (int) Math.floor(GreatGate.RO) + 1, "ring " + (outer * 2 + 1) + " blocks across (RO " + GreatGate.RO + ")");
      // roundness: the drawn ring (each cell's own collision, 8 x 8 samples) against the true annulus, beside whole blocks only
      int errShape = 0, errBlock = 0, shaped = 0; for (int y = 1; y <= GreatGate.TOP; y++) for (int z = -12; z <= 12; z++) { String b = GreatGate.at(-1, y, z); boolean ringCell = b != null && b.matches(".*(gate_frame|blackstone|sea_lantern).*");
          boolean[][] v = new boolean[8][8]; if (ringCell) { if (b.startsWith("shape:")) { shaped++; String[] p = b.split(":", 4); for (int[] bx : ShapeLib.boxes(ShapeLib.INDEX.get(p[1]), Integer.parseInt(p[2]))) for (int j = bx[1] / 2; j < bx[4] / 2; j++) for (int k = bx[2] / 2; k < bx[5] / 2; k++) v[k][j] = true; } else for (boolean[] r : v) Arrays.fill(r, true); }
          for (int aa = 0; aa < 8; aa++) for (int bb = 0; bb < 8; bb++) { double zz = z + (aa + 0.5) / 8 - 0.5, yy = y - (GreatGate.CY - 0.5) + (bb + 0.5) / 8 - 0.5, rr = Math.hypot(zz, yy); boolean in = rr >= GreatGate.RI && rr <= GreatGate.RO;
            if (v[aa][bb] != in) errShape++; if (ringCell != in) errBlock++; } }
      ok(shaped > 0 && errShape < 0.6 * errBlock, String.format("ring roundness: %d shaped edge cells; %d of the ring's 1/64-cell samples off the true circle (whole blocks only: %d, -%.0f %%)", shaped, errShape, errBlock, 100.0 * (errBlock - errShape) / errBlock));
      int field = 0; for (int y = 1; y <= GreatGate.TOP; y++) for (int z = -10; z <= 10; z++) for (int x = -1; x <= 0; x++) { String b = GreatGate.at(x, y, z); if (b != null && Math.hypot(z, y - (GreatGate.CY - 0.5)) < GreatGate.RI) field++; }
      ok(field == 0, "the gate stands closed: nothing in the aperture until a player sets it (" + field + ")"); }
    // 0.27.2 the controller and the field: the controller on the dais; when set, the field fills every aperture cell (both depths) and no ring cell
    { ok(GreatGate.CONTROLLER.equals(GreatGate.at(GreatGate.CTRL_X, GreatGate.CTRL_Y, GreatGate.CTRL_Z)) && GreatGate.at(GreatGate.CTRL_X, 0, GreatGate.CTRL_Z) != null, "the gate controller stands on the dais, inside the guard (" + GreatGate.CTRL_X + ", " + GreatGate.CTRL_Y + ", " + GreatGate.CTRL_Z + ")");
      List<int[]> fc = GreatGate.fieldCells(); int clash = 0; for (int[] c : fc) if (GreatGate.at(c[0], c[1], c[2]) != null) clash++;
      int ap = 0; for (int y = 1; y <= GreatGate.TOP; y++) for (int z = -12; z <= 12; z++) if (GreatGate.at(-1, y, z) == null && Math.hypot(z, y - (GreatGate.CY - 0.5)) < GreatGate.RO) ap++;
      ok(clash == 0 && fc.size() == 2 * ap, String.format("the field (when set): %d cells, both depths of the %d-cell aperture, %d on a ring or dais block", fc.size(), ap, clash));
      ok(Math.abs(GreatGate.FIELD_Y - (1 + GreatGate.RI)) < 1e-9 && GreatGate.FIELD_X == 0, String.format("the portal's centre (%.1f, %.1f, %.1f) is the aperture's middle on the ring's mid-plane", GreatGate.FIELD_X, GreatGate.FIELD_Y, GreatGate.FIELD_Z)); }
    // 0.27.2 the portal shader compiles and draws on real OpenGL 3.2 core (#version 150, as the game makes it): tools/portal_view.py, 1 frame
    { long t0 = System.nanoTime(); Process pr = new ProcessBuilder("python3", "tools/portal_view.py", "src/main/resources", "/tmp/portal_test.gif", "7.5", "0.2", "5").redirectErrorStream(true).start(); String outp = new String(pr.getInputStream().readAllBytes()); int code = pr.waitFor();
      ok(code == 0 && outp.contains("wrote"), String.format("portal.glsl compiles, links and draws (Mesa, %.1f s): %s", (System.nanoTime() - t0) / 1e9, outp.trim().replaceAll("\\s+", " "))); }
    // in the spawn city: drawn at the gate plaza's centre on the plaza's pad
    { Metro.on = true; PlanetWorld w = new PlanetWorld(1); w.playerSpawn(); Metro m = w.metro(); Metro.Plan p = null; for (Metro.Plan q : m.plans.values()) if (q.face == 0 && w.spawnKind.contains(q.site.name)) p = q;
      double[] g = p.xz(Metro.GATE_U, 0); int gx = (int) Math.floor(g[0]), gz = (int) Math.floor(g[1]); Column c = Column.of(w, 0, gx, gz); int pad = m.plazaPad(p);
      ok(c.surfaceY == pad - 1 && c.top == Column.Mat.ROAD_CONCRETE, String.format("the plaza under the gate is the paved pad (top y %d, pad %d)", c.surfaceY, pad));
      int want = 0; for (int x = GreatGate.X0; x <= GreatGate.X1; x++) for (int y = 0; y <= GreatGate.TOP; y++) for (int z = GreatGate.Z0; z <= GreatGate.Z1; z++) { String b = GreatGate.at(x, y, z); if (b != null && (b.contains("gate_frame") || b.contains("steel_grate") || b.contains("rail_"))) want++; }
      long t0 = System.nanoTime(); int[] nb = {0}, ring = {0}; for (int cx = -2; cx <= 1; cx++) for (int cz = -2; cz <= 1; cz++) { int x0 = (Math.floorDiv(gx, 16) + cx) * 16, z0 = (Math.floorDiv(gz, 16) + cz) * 16;
          m.chunk(0, x0, z0, x0 + 16, z0 + 16, w.cube.spherePoint(0, x0 + 8, z0 + 8), (x, y, z, b) -> { if (b.contains("gate_frame") || b.contains("steel_grate") || b.contains("rail_")) nb[0]++; if (b.contains("gate_frame") && y > pad + 10) ring[0]++; }); }
      ok(nb[0] == want && ring[0] > 0, String.format("the plaza's chunks draw the whole gate (%d of %d frame, grate and rail blocks; %d ring blocks up high) in %.0f ms", nb[0], want, ring[0], (System.nanoTime() - t0) / 1e6)); }
    System.out.println("GreatGateTest: " + fail + " fail"); }
}
