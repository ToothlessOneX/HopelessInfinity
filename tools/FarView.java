import com.hopeless.infinity.planet.*;
/** FarView seed outDir x z camY [body]: (0.15.0 body 1: the home moon) dumps planet/FarMesh (the mesh the game draws past the chunks) for tools/far_view.js, which draws
 *  it with shaders/far.glsl (the game's own shader). Saved tool. */
public class FarView { public static void main(String[] a) throws Exception {
  long seed = Long.parseLong(a[0]); java.io.File out = new java.io.File(a[1]); out.mkdirs(); PlanetWorld w = new PlanetWorld(seed); if (a.length > 5) w = w.body(Integer.parseInt(a[5]));   // 0.18.0 any body id
  double x = Double.parseDouble(a[2]), z = Double.parseDouble(a[3]), y = Double.parseDouble(a[4]); FarMesh m = FarMesh.build(w, 0, x, z, y);
  java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(m.verts * 36 + m.idx.length * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
  for (int i = 0; i < m.verts; i++) { for (int k = 0; k < 3; k++) b.putFloat(m.pos[i * 3 + k]); for (int k = 0; k < 3; k++) b.putFloat(m.nrm[i * 3 + k]); for (int k = 0; k < 3; k++) b.putFloat(m.col[i * 3 + k]); }
  for (int v : m.idx) b.putInt(v); java.nio.file.Files.write(new java.io.File(out, "far.bin").toPath(), b.array());
  java.nio.file.Files.writeString(new java.io.File(out, "far.json").toPath(), String.format("{\"verts\":%d,\"idx\":%d,\"x\":%.1f,\"z\":%.1f,\"y\":%.1f,\"reachKm\":%.1f}", m.verts, m.idx.length, x, z, y, m.reachKm));
  System.out.printf("%d vertices, %d triangles, %d ms%n", m.verts, m.tris, m.ms);
}}
