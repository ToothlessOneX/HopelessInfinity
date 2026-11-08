import com.hopeless.infinity.planet.*;
/** MoonPatch seed out.bin cx cz size: the home moon's block columns (planet/Column, what the generator lays down) round a point on face 0: ground y and
 *  top material per column, for tools/moon_patch.py. Saved tool. */
public class MoonPatch { public static void main(String[] a) throws Exception { PlanetWorld m = new PlanetWorld(Long.parseLong(a[0])).moon(); int cx = Integer.parseInt(a[2]), cz = Integer.parseInt(a[3]), n = Integer.parseInt(a[4]);
  java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(n * n * 5).order(java.nio.ByteOrder.LITTLE_ENDIAN); long t0 = System.nanoTime();
  for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) { Column c = Column.of(m, 0, cx - n / 2 + i, cz - n / 2 + j); b.putFloat((float) (c.groundTop - 1)); b.put((byte) c.top.ordinal()); }
  java.nio.file.Files.write(java.nio.file.Path.of(a[1]), b.array()); System.out.printf("%d columns in %d ms%n", n * n, (System.nanoTime() - t0) / 1000000); } }
