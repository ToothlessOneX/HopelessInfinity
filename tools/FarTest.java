import com.hopeless.infinity.planet.*;
/** FarTest: the 0.9.0 distant planet mesh (planet/FarMesh) at seed 1's spawn: build time and size; near the camera the mesh stands where
 *  the chunks do (block y of Column.of); far away the sea drops below the camera by d^2 / 2R (the horizon); no crack-sized gaps between
 *  rings (every cell edge of a coarse ring next to the finer one is overlapped); the far grass the blocks' colour (0.29.3). Saved tool. */
public class FarTest { public static void main(String[] a) {
  PlanetWorld w = new PlanetWorld(1); int[] sp = w.spawn(); double camY = 80; int pass = 0, fail = 0;
  FarMesh m = null; long best = Long.MAX_VALUE; for (int k = 0; k < 3; k++) { m = FarMesh.build(w, 0, sp[0] + 0.5, sp[2] + 0.5, camY); best = Math.min(best, m.ms); }
  System.out.printf("mesh: %d vertices, %d triangles, out to %.0f km, built in %d ms (best of 3)%n", m.verts, m.tris, m.reachKm, best);
  boolean t1 = best < 2000; System.out.println((t1 ? "PASS" : "FAIL") + " builds on a worker in under 2 s"); if (t1) pass++; else fail++;
  double worst = 0; int n = 0; for (int v = 0; v < m.verts; v++) { double px = m.pos[v * 3], py = m.pos[v * 3 + 1], pz = m.pos[v * 3 + 2]; if (Math.hypot(px, pz) > 300) continue;
    double bx = m.blk[v * 2], bz = m.blk[v * 2 + 1]; double[] sp3 = w.cube.spherePoint(0, bx, bz); double h = w.terrain.heightM(sp3, w.cube.R);
    double want = (h < 0 ? Terrain.SEA + 0.888 : Terrain.toYd(h) + 1) - camY; double horiz = Math.hypot(px - (bx - sp[0] - 0.5), pz - (bz - sp[2] - 0.5));
    if (Math.abs(py - want) + horiz > worst) { worst = Math.abs(py - want) + horiz; System.out.printf("  worse: block %.1f %.1f, mesh y %.3f vs terrain %.3f, horizontal offset %.3f%n", bx, bz, py + camY, want + camY, horiz); } n++; }
  boolean t2 = worst < 1.5; System.out.printf("%s near the camera the mesh is where the chunks' ground is (height + sideways): worst %.3f blocks over %d vertices within 300 m%n", t2 ? "PASS" : "FAIL", worst, n); if (t2) pass++; else fail++;
  double dev = 0; int ns = 0; double R = w.cube.R; for (int v = 0; v < m.verts; v++) { double px = m.pos[v * 3], py = m.pos[v * 3 + 1], pz = m.pos[v * 3 + 2], d = Math.hypot(px, pz); if (d < 50000 || m.col[v * 3 + 2] < 0.3 || m.col[v * 3] > 0.2) continue;   // sea vertices 50+ km away
    double[] dv = w.cube.spherePoint(0, m.blk[v * 2], m.blk[v * 2 + 1]), u0 = w.cube.spherePoint(0, sp[0] + 0.5, sp[2] + 0.5); double dt = R * Math.acos(Math.min(1, dv[0] * u0[0] + dv[1] * u0[1] + dv[2] * u0[2]));   // the true ground distance
    double[] hw = w.terrain.heightAndWater(dv, R); if (hw[0] >= 0 || (!Double.isNaN(hw[1]) && hw[1] > hw[0])) continue;   // 0.21.0 the sea only: lakes and rivers are blue too
    double want = (Terrain.SEA + 0.888 - camY) - dt * dt / (2 * R); dev = Math.max(dev, Math.abs(py - want) / (dt * dt / (2 * R))); ns++; }
  boolean t3 = ns == 0 || dev < 0.02; System.out.printf("%s far sea drops by d^2/2R (the horizon): worst %.2f %% over %d sea vertices 50+ km out%n", t3 ? "PASS" : "FAIL", dev * 100, ns); if (t3) pass++; else fail++;
  // 0.29.3 the far grass has the blocks' colour: on grass columns within 2 km the vertex colour is the column biome's tint times the grass texture's mean
  //   (the game's own block colour); measured texture mean 0.578 (1.20.1 grass_block_top.png)
  int nc = 0, off = 0; String firstOff = ""; for (int v = 0; v < m.verts; v++) { if (Math.hypot(m.pos[v * 3], m.pos[v * 3 + 2]) > 2000) continue; Column c = Column.of(w, 0, (int) Math.floor(m.blk[v * 2]), (int) Math.floor(m.blk[v * 2 + 1]));
    if (c.voidCol || c.top != Column.Mat.GRASS) continue; int g = FarMesh.grassRgb(c.biome); if (g < 0) continue; double e = 0; for (int k = 0; k < 3; k++) e = Math.max(e, Math.abs(m.col[v * 3 + k] - ((g >> (16 - 8 * k)) & 255) / 255.0 * 0.578));
    nc++; if (e > 0.03) { off++; if (firstOff.isEmpty()) firstOff = String.format("block %.0f %.0f: column biome %s, mesh colour %.3f %.3f %.3f", m.blk[v * 2], m.blk[v * 2 + 1], c.biome, m.col[v * 3], m.col[v * 3 + 1], m.col[v * 3 + 2]); } }
  boolean t4 = nc > 100 && off <= 0.05 * nc; System.out.printf("%s the far grass has the blocks' colour (biome tint x grass texture mean 0.578): %d of %d grass vertices within 2 km off by more than 0.03 (5 %% allowed: biome edges, where the column's biome and the mesh point's differ)%s%n", t4 ? "PASS" : "FAIL", off, nc, firstOff.isEmpty() ? "" : "; first: " + firstOff); if (t4) pass++; else fail++;
  System.out.printf("FarTest: %d pass, %d fail%n", pass, fail); if (fail > 0) System.exit(1);
}}
