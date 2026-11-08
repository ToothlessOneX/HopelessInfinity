import com.hopeless.infinity.planet.*;
/** SeamWalk seed [walks] [len]: walks straight lines over face 0 of the planet (the mod's own Column plans, block by block) and lists every
 *  SEAM: a step between two neighbouring columns of 3 or more blocks where the ground on both sides is gentle (the steps just before and
 *  after are 1 block or less) - a wall, not a slope or a cliff. For each: where, the step, the parts of the height on both sides (the
 *  coast field, the short detail, the shore process on/off) so the cause shows. Saved tool. */
public class SeamWalk { public static void main(String[] a) {
  long seed = Long.parseLong(a[0]); int walks = a.length > 1 ? Integer.parseInt(a[1]) : 200, len = a.length > 2 ? Integer.parseInt(a[2]) : 4000;
  PlanetWorld w = new PlanetWorld(seed); int F = w.cube.F; java.util.Random r = new java.util.Random(7); int[] sp = w.spawn();
  int seams = 0, steps = 0; java.util.Map<String, Integer> cause = new java.util.TreeMap<>(); StringBuilder ex = new StringBuilder();
  for (int k = 0; k < walks; k++) { double x0 = k < walks / 2 ? sp[0] + r.nextGaussian() * 20000 : r.nextDouble() * F - F / 2.0, z0 = k < walks / 2 ? sp[2] + r.nextGaussian() * 20000 : r.nextDouble() * F - F / 2.0, an = r.nextDouble() * Math.PI * 2;
    boolean axis = r.nextBoolean(); int dx = axis ? 1 : 0, dz = axis ? 0 : 1; int[] y = new int[len]; int xs = (int) x0, zs = (int) z0;
    for (int i = 0; i < len; i++) { Column c = Column.of(w, 0, xs + dx * i, zs + dz * i); y[i] = c.voidCol ? Integer.MIN_VALUE : c.surfaceY; }
    for (int i = 2; i < len - 2; i++) { if (y[i] == Integer.MIN_VALUE || y[i + 1] == Integer.MIN_VALUE || y[i - 1] == Integer.MIN_VALUE || y[i + 2] == Integer.MIN_VALUE) continue; steps++;
      int d = y[i + 1] - y[i]; if (Math.abs(d) < 3) continue; if (Math.abs(y[i] - y[i - 1]) > 1 || Math.abs(y[i + 2] - y[i + 1]) > 1) continue;
      seams++; int bx = xs + dx * i, bz = zs + dz * i;
      double[] s0 = CubeSphere.sphere(CubeSphere.cube(0, w.cube.u(0, bx + 0.5), w.cube.v(bz + 0.5))), s1 = CubeSphere.sphere(CubeSphere.cube(0, w.cube.u(0, bx + dx + 0.5), w.cube.v(bz + dz + 0.5)));
      double h0 = w.terrain.heightM(s0, w.cube.R), h1 = w.terrain.heightM(s1, w.cube.R); Terrain.shores = false; double r0 = w.terrain.heightM(s0, w.cube.R), r1 = w.terrain.heightM(s1, w.cube.R); Terrain.shores = true;
      String c = Math.abs(r1 - r0) < 1 ? "shore process" : "raw terrain"; cause.merge(c, 1, Integer::sum);
      if (ex.length() < 4000) ex.append(String.format("  %d %d -> %d %d: y %d -> %d (h %.2f -> %.2f m; without the shore process %.2f -> %.2f m) [%s]%n", bx, bz, bx + dx, bz + dz, y[i], y[i + 1], h0, h1, r0, r1, c)); } }
  System.out.printf("SeamWalk seed %d: %d walks x %d blocks, %d steps checked, %d seams (walls of 3+ blocks between gentle ground); causes %s%n%s", seed, walks, len, steps, seams, cause, ex);
}}
