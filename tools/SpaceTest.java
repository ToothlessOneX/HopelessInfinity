import com.hopeless.infinity.planet.*; import com.hopeless.infinity.world.SpaceMath;
/** SpaceTest: 0.12.0 space (world/Space) off the game: the face <-> space mapping, the block frame, and the physics. Saved tool. */
public class SpaceTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) {
    PlanetWorld w = new PlanetWorld(1); CubeSphere c = w.cube; java.util.Random r = new java.util.Random(1); double worst = 0;
    for (int i = 0; i < 20000; i++) { int f = r.nextInt(6); double x = c.faceX0(f) + r.nextDouble() * c.F, z = c.faceZ0() + r.nextDouble() * c.F; double[] s = c.spherePoint(f, x, z), l = c.locate(new double[]{s[0] * 7, s[1] * 7, s[2] * 7});
      if ((int) l[0] != f) { worst = 1e9; continue; } worst = Math.max(worst, Math.hypot(l[1] - x, l[2] - z)); }
    ok(worst < 1e-3, String.format("locate inverts spherePoint on all six faces: worst %.2e blocks of 20,000 points (face %,d blocks)", worst, c.F));
    // the block frame: ex along +x, ez along +z, up = the sphere point, all unit and square
    double[][] fr = c.frame(0, 239446, 85338); double[] s0 = c.spherePoint(0, 239446, 85338), sx = c.spherePoint(0, 239447, 85338), sz = c.spherePoint(0, 239446, 85339);
    double ex = dot(fr[1], sub(sx, s0)) * c.R, ez = dot(fr[2], sub(sz, s0)) * c.R, sq = Math.abs(dot(fr[0], fr[1])) + Math.abs(dot(fr[1], fr[2])) + Math.abs(dot(fr[0], fr[2]));
    ok(ex > 0.9 && ex < 1.1 && ez > 0.9 && ez < 1.1 && sq < 1e-9, String.format("block frame at spawn: a block east is %.4f m along ex, south %.4f m along ez, square to %.1e", ex, ez, sq));
    double gm = w.planet.gravity * c.R * c.R, om = SpaceMath.omega(w); System.out.printf("seed 1: g %.2f m/s2, R %,.0f m, GM %.4e, sidereal spin %.3f h%n", w.planet.gravity, c.R, gm, 2 * Math.PI / om / 3600);
    // a circular orbit with no spin keeps its radius over one revolution
    double R0 = c.R + 400000, v0 = Math.sqrt(gm / R0), T = 2 * Math.PI * R0 / v0; double[] p = {R0, 0, 0}, v = {0, v0, 0}, z3 = new double[3]; double dr = 0;
    for (int t = 0; t < T * 20; t++) { SpaceMath.step(p, v, gm, 0, z3); dr = Math.max(dr, Math.abs(Math.sqrt(dot(p, p)) - R0)); }
    ok(dr < 50, String.format("circular orbit at 400 km (%.0f min, %.0f m/s): radius held to %.1f m over a revolution", T / 60, v0, dr));
    // the same orbit seen from the turning frame: still circular (the Coriolis and centrifugal terms are right)
    p = new double[]{R0, 0, 0}; v = new double[]{0, v0 - om * R0, 0}; dr = 0; for (int t = 0; t < T * 20; t++) { SpaceMath.step(p, v, gm, om, z3); dr = Math.max(dr, Math.abs(Math.sqrt(dot(p, p)) - R0)); }
    ok(dr < 50, String.format("the same orbit in the planet's turning frame: radius held to %.1f m", dr));
    // synchronous orbit: at rest in the turning frame
    double rs = Math.cbrt(gm / (om * om)); p = new double[]{rs, 0, 0}; v = new double[3]; for (int t = 0; t < 20 * 3600; t++) SpaceMath.step(p, v, gm, om, z3);
    ok(Math.abs(Math.sqrt(dot(p, p)) - rs) < 100 && Math.sqrt(dot(v, v)) < 1, String.format("synchronous orbit (%,.0f km): stays put for an hour (moved %.1f m, speed %.3f m/s)", rs / 1000, Math.sqrt(sub(p, new double[]{rs, 0, 0})[0] * sub(p, new double[]{rs, 0, 0})[0] + p[1] * p[1]), Math.sqrt(dot(v, v))));
    // a drop from 1,000 m over the top (pole: no spin terms) falls the first 1,000 m in sqrt(2h/g)
    double top = 5631; p = new double[]{0, 0, c.R + top + 1000}; v = new double[3]; int n = 0; while (p[2] > c.R + top) { SpaceMath.step(p, v, gm, om, z3); n++; }
    double gTop = gm / Math.pow(c.R + top + 500, 2), want = Math.sqrt(2 * 1000 / gTop); ok(Math.abs(n / 20.0 - want) < 0.1, String.format("a 1,000 m fall over the pole back to the world top: %.2f s (sqrt(2h/g) %.2f s), speed then %.1f m/s", n / 20.0, want, Math.sqrt(dot(v, v))));
    System.out.println("SpaceTest: " + fail + " fail"); if (fail > 0) System.exit(1); }
  static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; } static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; } }
