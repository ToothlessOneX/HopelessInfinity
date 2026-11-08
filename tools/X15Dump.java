import com.hopeless.infinity.vehicle.X15Mesh;
/** X15Dump out.json: writes vehicle/X15Mesh.build() (the quads the game draws) for tools/x15_view.py. Saved tool. */
public class X15Dump { public static void main(String[] a) throws Exception { var q = X15Mesh.build(); StringBuilder b = new StringBuilder("[");
  for (X15Mesh.Quad x : q) { if (b.length() > 1) b.append(','); b.append("{\"c\":").append(x.rgb()).append(",\"p\":[");
    for (int i = 0; i < 4; i++) b.append(i > 0 ? "," : "").append(String.format("[%.4f,%.4f,%.4f,%.3f,%.3f,%.3f]", x.p()[i][0], x.p()[i][1], x.p()[i][2], x.n()[i][0], x.n()[i][1], x.n()[i][2])); b.append("]}"); }
  java.nio.file.Files.writeString(java.nio.file.Path.of(a[0]), b.append("]").toString()); System.out.println(q.size() + " quads"); } }
