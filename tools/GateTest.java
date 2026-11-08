import com.hopeless.infinity.world.GateShape;
import java.util.*;
/** GateTest: the gate ring's shape (world/GateShape) on built rings, whole and broken; 0.14.1 the controller anywhere in the ring or touching it. Saved tool. */
public class GateTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  /** A ring's 16 cells (world positions) with its bottom-middle at the origin. */
  static Set<String> ring(int axis) { Set<String> s = new HashSet<>(); for (int[] c : GateShape.cells(axis)) s.add(c[0] + "," + c[1] + "," + c[2]); return s; }
  /** The shape seen from a controller at (cx, cy, cz): frames are the set (the controller's own cell is not a frame). */
  static GateShape.Found test(Set<String> frames, int cx, int cy, int cz) { return GateShape.ring((x, y, z) -> { String k = (cx + x) + "," + (cy + y) + "," + (cz + z); return frames.contains(k); }); }
  static Set<String> without(Set<String> s, String k) { Set<String> r = new HashSet<>(s); r.remove(k); return r; }
  static boolean at(GateShape.Found f, int cx, int cy, int cz, int axis) { return f != null && f.axis() == axis && cx + f.ox() == 0 && cy + f.oy() == 0 && cz + f.oz() == 0; }
  public static void main(String[] a) {
    ok(GateShape.cells(0).length == 16, "a ring is 16 cells");
    // the controller in place of a frame block, anywhere in the ring
    int n = 0, good = 0; for (int axis = 0; axis < 2; axis++) for (int[] c : GateShape.cells(axis)) { n++; GateShape.Found f = test(without(ring(axis), c[0] + "," + c[1] + "," + c[2]), c[0], c[1], c[2]); if (at(f, c[0], c[1], c[2], axis) && f.controllerInRing()) good++; }
    ok(good == n, String.format("the controller in place of any of the 16 frame blocks, both facings: %d of %d found with the right ring", good, n));
    // touching from outside: behind the top middle, beside the right side, under the buried bottom row
    ok(at(test(ring(0), 0, 4, 1), 0, 4, 1, 0), "controller behind the top middle (touching from the back): found");
    ok(at(test(ring(0), 3, 2, 0), 3, 2, 0, 0), "controller beside the right side: found");
    ok(at(test(ring(0), 0, -1, 0), 0, -1, 0, 0), "controller under the bottom row (the bottom row buried): found");
    ok(at(test(ring(1), 0, 2, -3), 0, 2, -3, 1), "controller beside a ring facing the other way: found");
    // not attached, or in the field's middle, or a broken ring
    ok(test(ring(0), 0, 6, 0) == null, "controller one block clear of the ring: none");
    ok(test(ring(0), 0, 2, 0) == null, "controller in the middle of the ring (where the field goes): none");
    ok(test(without(ring(0), "2,2,0"), 0, -1, 0) == null, "a ring missing a side block: none");
    ok(test(without(ring(0), "-2,0,0"), 0, 5, 0) == null, "a ring missing a bottom corner: none");
    System.out.println("GateTest: " + fail + " fail"); if (fail > 0) System.exit(1); } }
