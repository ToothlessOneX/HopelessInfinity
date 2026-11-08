import com.hopeless.infinity.planet.*; import com.hopeless.infinity.sky.Ephemeris;
/** GlobeView seed outDir face x z altM: dumps planet/GlobeMesh (the mesh the game draws from space), the planet->camera matrix the game
 *  builds (FarTerrain.globe) and the real star's direction there (sky/Ephemeris, at a time with the star 20-30 deg up) for tools/globe_view.js. Saved tool. */
public class GlobeView { public static void main(String[] a) throws Exception {
  PlanetWorld w0 = new PlanetWorld(Long.parseLong(a[0])), w = a.length > 6 ? w0.body(Integer.parseInt(a[6])) : w0; java.io.File out = new java.io.File(a[1]); out.mkdirs(); int f = Integer.parseInt(a[2]); double x = Double.parseDouble(a[3]), z = Double.parseDouble(a[4]), alt = Double.parseDouble(a[5]);
  GlobeMesh g = GlobeMesh.build(w); double[][] fr = w.cube.frame(f, x, z); double camR = w.cube.R + alt;
  Ephemeris e = new Ephemeris(w0); double t = 0; Ephemeris.At at = w.body > 0 ? e.atBody(w, f, x, z, 0) : e.at(f, x, z, 0); for (double s = 0; s < (w.body > 0 ? 400 : 2) * 86400; s += w.body > 0 ? 3600 : 300) { Ephemeris.At q = w.body > 0 ? e.atBody(w, f, x, z, s) : e.at(f, x, z, s); if (Math.toDegrees(q.sunAlt) > 20 && Math.toDegrees(q.sunAlt) < 30) { at = q; t = s; break; } }
  java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(g.verts * 36 + g.idx.length * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
  for (int i = 0; i < g.verts; i++) { for (int k = 0; k < 3; k++) b.putFloat(g.pos[i * 3 + k]); for (int k = 0; k < 3; k++) b.putFloat(g.nrm[i * 3 + k]); for (int k = 0; k < 3; k++) b.putFloat(g.col[i * 3 + k]); }
  for (int v : g.idx) b.putInt(v); java.nio.file.Files.write(new java.io.File(out, "globe.bin").toPath(), b.array());
  double[] M = {fr[1][0], fr[0][0], fr[2][0], 0, fr[1][1], fr[0][1], fr[2][1], 0, fr[1][2], fr[0][2], fr[2][2], 0, 0, -camR, 0, 1};   // column-major, as FarTerrain.globe
  StringBuilder ms = new StringBuilder(); for (double d : M) ms.append(ms.length() > 0 ? "," : "").append(d);
  java.nio.file.Files.writeString(new java.io.File(out, "globe.json").toPath(), String.format("{\"verts\":%d,\"idx\":%d,\"alt\":%.1f,\"R\":%.1f,\"g\":%.3f,\"model\":[%s],\"sun\":[%.5f,%.5f,%.5f],\"sunAlt\":%.1f,\"t\":%.0f,\"body\":%d}", g.verts, g.idx.length, alt, w.cube.R, w.gravity, ms, at.sun[0], at.sun[1], at.sun[2], Math.toDegrees(at.sunAlt), t, w.body));
  System.out.printf("%d vertices, %d triangles, %d ms; star %.1f deg up at t %.0f s%n", g.verts, g.idx.length / 3, g.ms, Math.toDegrees(at.sunAlt), t); } }
