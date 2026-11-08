import com.hopeless.infinity.planet.*; import java.util.*;
/** FrontageTest: 0.27.3 the Cobble & Sons HQ front (planet/Frontage): the sign reads back as the name from its own blocks, the logo has its
 *  ring and stone, the doors stay clear, both screens are whole and on the podium, the spawn city's HQ chunks draw them facing the plaza,
 *  and the screen shader compiles and draws on real OpenGL (tools/screen_view.py). Saved tool, part of the suite. */
public class FrontageTest { static int fail = 0; static void ok(boolean c, String m) { if (!c) fail++; System.out.println((c ? "PASS " : "FAIL ") + m); }
  public static void main(String[] a) throws Exception {
    int ph = 120;   // a podium wide enough for everything (the smallest seed-1 HQ's is 98: measured below)
    Set<String> kinds = new TreeSet<>(); for (int x = -ph; x <= ph; x++) for (int y = 0; y < 24; y++) { String b = Frontage.at(x, y, ph); if (b != null) kinds.add(b); }
    List<String> bad = new ArrayList<>(); for (String b : kinds) { String w = BlockStates.bad(b); if (w != null) bad.add(b + " (" + w + ")"); }
    ok(bad.isEmpty(), "every block string exists (" + kinds + ")" + (bad.isEmpty() ? "" : ": " + bad));
    // read the sign back: each 6-column cell matched against the font
    StringBuilder read = new StringBuilder(); int w = Frontage.width(Frontage.NAME), x0 = -w / 2;
    for (int c = 0; c < Frontage.NAME.length(); c++) { char best = '#'; for (char ch : "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789&.,-!' ?".toCharArray()) { boolean same = true;
        for (int col = 0; col < 5 && same; col++) for (int row = 0; row < 7; row++) { boolean lit = Frontage.LIT.equals(Frontage.at(x0 + c * 6 + col, Frontage.BAND_Y1 - 1 - row, ph)); if (lit != Frontage.pix(ch, col, row)) { same = false; break; } }
        if (same) { best = ch; break; } } read.append(best); }
    ok(read.toString().equals(Frontage.NAME), "the sign's blocks read \"" + read + "\"");
    int doors = 0; for (int x = -2; x <= 2; x++) for (int y = 0; y <= 3; y++) if (Frontage.at(x, y, ph) != null) doors++; ok(doors == 0, "the main doors stay clear (" + doors + " frontage blocks over them)");
    int ring = 0, stone = 0, node = 0; for (int x = -5; x <= 5; x++) for (int y = Frontage.LOGO_Y0; y < Frontage.LOGO_Y0 + Frontage.LOGO; y++) { String b = Frontage.at(x, y, ph); if ("hopelessinfinity:gate_frame".equals(b)) ring++; if (b != null && b.matches("cobblestone|stone|andesite")) stone++; if (Frontage.LIT.equals(b)) node++; }
    ok(ring >= 20 && stone >= 9 && node >= 4, String.format("the logo: %d ring, %d stone, %d light-node blocks", ring, stone, node));
    // the screens: two controllers, each the lowest -x corner of a whole 32 x 18 panel, clear of the sign and the logo
    int ctl = 0, panels = 0; for (int x = -ph; x <= ph; x++) for (int y = 0; y < 24; y++) { String b = Frontage.at(x, y, ph); if (Frontage.CONTROLLER.equals(b)) { ctl++; int full = 0; for (int i = 0; i < Frontage.SCREEN_W; i++) for (int j = 0; j < Frontage.SCREEN_H; j++) { String q = Frontage.at(x + i, y + j, ph); if (Frontage.PANEL.equals(q) || Frontage.CONTROLLER.equals(q)) full++; } if (full == Frontage.SCREEN_W * Frontage.SCREEN_H) panels++; } }
    ok(ctl == 2 && panels == 2, "two screens, each a whole " + Frontage.SCREEN_W + " x " + Frontage.SCREEN_H + " panel with its controller (" + ctl + " controllers, " + panels + " whole)");
    // the spawn city's HQ: its chunks draw the front on the podium face toward the plaza
    { PlanetWorld wd = new PlanetWorld(1); wd.playerSpawn(); Metro m = wd.metro(); Metro.Plan p = null; for (Metro.Plan q : m.plans.values()) if (q.face == 0 && wd.spawnKind.contains(q.site.name)) p = q;
      int minPh = 9999; for (Metro.Plan q : m.plans.values()) if (q.site.kind == Sites.Kind.MEGACITY) minPh = Math.min(minPh, q.towers.get(0).tower().podiumHalf);   /* 0.28.2 the HQ fronts are the megacities' (large cities have none) */
      ok(minPh >= Frontage.SCREEN_X0 + Frontage.SCREEN_W + 2, "every seed-1 HQ podium is wide enough for both screens (smallest half width " + minPh + ")");
      Metro.Lot hq = p.towers.get(0); int hph = hq.tower().podiumHalf; double[] f = p.xz(hq.u, hq.v - hph); int fz = (int) Math.floor(f[1]); int[] got = {0, 0, 0};
      long t0 = System.nanoTime(); for (int dx = -112; dx <= 112; dx += 16) { int x0c = Math.floorDiv((int) Math.floor(f[0]) + dx, 16) * 16, z0c = Math.floorDiv(fz, 16) * 16;
        m.chunk(0, x0c, z0c, x0c + 16, z0c + 16, wd.cube.spherePoint(0, x0c + 8, z0c + 8), (x, y, z, b) -> { if (z != fz) return; if (b.startsWith("hopelessinfinity:ad_screen[facing=north")) got[0]++; if (b.equals(Frontage.LIT)) got[1]++; if (b.equals(Frontage.PANEL)) got[2]++; }); }
      ok(got[0] == 2 && got[1] > 100 && got[2] >= 2 * Frontage.SCREEN_W * Frontage.SCREEN_H - 2, String.format("the spawn city's HQ chunks draw 2 screen controllers facing the plaza, %d lit sign blocks, %d panel blocks on the podium face (%.0f ms)", got[1], got[2], (System.nanoTime() - t0) / 1e6)); }
    // the screen shader on real OpenGL 3.2 core
    { Process pr = new ProcessBuilder("python3", "tools/screen_view.py", "src/main/resources", "/tmp/screen_test.gif", "0.4", "5").redirectErrorStream(true).start(); String o = new String(pr.getInputStream().readAllBytes()); int code = pr.waitFor();
      ok(code == 0 && o.contains("wrote"), "adscreen.glsl compiles, links and draws (Mesa): " + o.trim().replaceAll("\\s+", " ")); }
    System.out.println("FrontageTest: " + fail + " fail"); }
}
