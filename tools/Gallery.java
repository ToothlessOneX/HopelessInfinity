import com.hopeless.infinity.galaxy.*;
import com.hopeless.infinity.look.Look;
import com.hopeless.infinity.planet.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * The gallery: real objects from the generator near the Sun's place (found, not made up), each drawn from its own generated data with the
 * mod's Look code. Args: seed outDir. Saved tool.
 */
public class Gallery {
    static Galaxy g; static PlanetWorld pw; static final int T = 560;
    static final double[] SUN = norm(new double[]{-0.55, 0.45, 0.70});

    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]); File out = new File(a[1]); out.mkdirs(); g = new Galaxy(seed); pw = new PlanetWorld(seed);
        long t0 = System.nanoTime(); full(out); System.out.printf("full planet %.0f s%n", (System.nanoTime() - t0) / 1e9);
        // find real objects round the Sun's place
        List<Galaxy.Ref> near = new ArrayList<>(); long cy = (long) Math.floor(-Research.R0_KPC * 1000 / Galaxy.CELL);
        for (long dx = -6; dx <= 6; dx++) for (long dy = -6; dy <= 6; dy++) for (long dz = -2; dz <= 2; dz++) near.addAll(g.cell(dx, cy + dy, dz));
        near.sort(Comparator.comparingDouble(Galaxy.Ref::lyFromSun));
        List<Galaxy.Ref> young = new ArrayList<>(); int c0 = (int) Math.floor(-Research.R0_KPC * 1000 / Galaxy.COLUMN);
        for (int dx = -15; dx <= 15; dx++) for (int dy = -15; dy <= 15; dy++) young.addAll(g.youngColumn(dx, c0 + dy)); young.sort(Comparator.comparingDouble(Galaxy.Ref::lyFromSun));
        StarSystem home = pw.system;
        Galaxy.Ref mDwarf = first(near, f -> f.primary.kind == Body.Kind.MAIN_SEQUENCE && Body.classOf(f.primary.initialMass) == 6);
        Galaxy.Ref giant = first(near, f -> f.primary.kind == Body.Kind.GIANT);
        Galaxy.Ref wd = first(near, f -> f.primary.kind == Body.Kind.WHITE_DWARF);
        Galaxy.Ref ns = first(near, f -> f.primary.kind == Body.Kind.NEUTRON_STAR);
        Galaxy.Ref bh = first(near, f -> f.primary.kind == Body.Kind.BLACK_HOLE);
        Galaxy.Ref bd = first(near, f -> f.primary.kind == Body.Kind.BROWN_DWARF);
        Galaxy.Ref sg = first(young, f -> f.primary.kind == Body.Kind.SUPERGIANT && f.primary.teff < 4500);
        Galaxy.Ref ob = first(young, f -> f.primary.kind == Body.Kind.MAIN_SEQUENCE);
        Galaxy.Ref wr = first(young, f -> f.primary.kind == Body.Kind.WOLF_RAYET);
        Galaxy.Ref snr = first(young, f -> !f.primary.alive() && f.primary.deathYr <= Research.SNR_LIFE_YR);
        // planets, moons, belts and comets from systems round the Sun
        Planet ringed = null, hot = null, ice = null, mini = null; StarSystem ringSys = null, beltSys = null, cometSys = null; Planet.Moon bigMoon = null, icyMoon = null; Planet bigMoonHost = null, icyHost = null;
        for (Galaxy.Ref f : near) { if (f.primary.kind != Body.Kind.MAIN_SEQUENCE) continue; StarSystem s = g.system(f);
            for (Planet p : s.planets) {
                if (ringed == null && p.kind == Planet.Kind.GAS_GIANT && p.rings == Planet.Rings.BRIGHT && p.teq < 150) { ringed = p; ringSys = s; }
                if (hot == null && p.kind == Planet.Kind.GAS_GIANT && p.teq > 900) hot = p;
                if (ice == null && p.kind == Planet.Kind.ICE_GIANT) ice = p;
                if (mini == null && p.kind == Planet.Kind.MINI_NEPTUNE) mini = p;
                for (Planet.Moon m : p.moons) { if (bigMoon == null && m.kind() == Planet.MoonKind.GIANT_IMPACT) { bigMoon = m; bigMoonHost = p; } if (icyMoon == null && m.kind() == Planet.MoonKind.REGULAR && m.radiusKm() > 1500) { icyMoon = m; icyHost = p; } } }
            for (StarSystem.Belt b : s.belts) if (beltSys == null && b.kind().startsWith("main")) beltSys = s;
            if (cometSys == null && !s.comets.isEmpty()) cometSys = s;
            if (ringed != null && hot != null && ice != null && mini != null && bigMoon != null && icyMoon != null && beltSys != null && cometSys != null) break; }
        StarSystem.Belt belt = beltSys.belts.stream().filter(b -> b.kind().startsWith("main")).findFirst().get(); double[] sizes = belt.largest(400);
        Galaxy.Rogue rogue = g.rogues(0, cy, 0).stream().filter(r -> r.massE() > 0.5 && r.massE() < 5).findFirst().orElse(g.rogues(0, cy, 0).get(0));

        List<BufferedImage> tiles = new ArrayList<>();
        tiles.add(starTile(home.primary, "Start star " + home.name(), String.format("%s, %.0f K, R %,.0f km", home.primary.type, home.primary.teff, home.primary.radiusKm), 0.85));
        tiles.add(starTile(mDwarf.primary, "Red dwarf " + mDwarf.name(), String.format("%s %.0f K, R %,.0f km, %.1f ly", mDwarf.primary.type, mDwarf.primary.teff, mDwarf.primary.radiusKm, mDwarf.lyFromSun()), 0.85));
        tiles.add(starTile(ob.primary, "Young massive star", String.format("%s %.0f K, %.1f Msun, R %,.0f km, %.0f ly", ob.primary.type, ob.primary.teff, ob.primary.mass, ob.primary.radiusKm, ob.lyFromSun()), 0.85));
        tiles.add(starTile(giant.primary, "Red giant " + giant.name(), String.format("%.0f K, R %,.0f km (%.0f Rsun), %.0f ly", giant.primary.teff, giant.primary.radiusKm, giant.primary.radiusSun(), giant.lyFromSun()), 0.88));
        tiles.add(starTile(sg.primary, "Red supergiant", String.format("%.0f K, %.0f Msun, R %.0f Rsun, %.0f ly", sg.primary.teff, sg.primary.mass, sg.primary.radiusSun(), sg.lyFromSun()), 0.9));
        tiles.add(wrTile(wr)); tiles.add(wdTile(wd)); tiles.add(nsTile(ns)); tiles.add(bdTile(bd));
        tiles.add(bhTile(bh.primary, false, String.format("Stellar black hole, %.1f Msun", bh.primary.mass), String.format("horizon %.1f km, %.0f ly; shadow 2.6 Rs (lensed stars behind)", bh.primary.radiusKm, bh.lyFromSun())));
        tiles.add(bhTile(g.centre().primary, true, "Sgr A* (galactic centre)", String.format("%.3g Msun, horizon %.3g km; glowing gas round the shadow", g.centre().primary.mass, g.centre().primary.radiusKm)));
        tiles.add(snrTile(snr));
        tiles.add(giantTile(ringed, ringSys.name() + " planet " + ringed.index, true));
        tiles.add(giantTile(hot, "Hot Jupiter (Sudarsky class " + (hot.teq > 1500 ? "V" : "IV") + ")", false));
        tiles.add(giantTile(ice, "Ice giant", true)); tiles.add(giantTile(mini, "Mini-Neptune", false));
        tiles.add(moonTile(bigMoon, bigMoonHost, "Giant-impact moon", 3.34, new double[]{0.55, 0.53, 0.50}));
        tiles.add(moonTile(icyMoon, icyHost, "Large icy moon (regular)", 1.9, new double[]{0.70, 0.66, 0.60}));
        tiles.add(asteroidTile(sizes[0], "Largest body of a main belt", 0.09, 1)); tiles.add(asteroidTile(sizes[60], "Asteroid, 61st largest", 0.08, 2)); tiles.add(asteroidTile(sizes[399], "Asteroid, 400th largest", 0.2, 3));
        tiles.add(cometTile(cometSys)); tiles.add(rogueTile(rogue)); tiles.add(beltTile(beltSys, belt));
        int cols = 4, rows = (tiles.size() + cols - 1) / cols; BufferedImage sheet = new BufferedImage(cols * T, rows * T + 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D gg = sheet.createGraphics(); for (int i = 0; i < tiles.size(); i++) gg.drawImage(tiles.get(i), (i % cols) * T, (i / cols) * T, null);
        gg.setColor(new Color(12, 14, 22)); gg.fillRect(0, rows * T, cols * T, 80); gg.setColor(Color.WHITE); gg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        gg.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22)); gg.drawString("Hopeless Infinity - real objects from the generator near the Sun's place (seed " + seed + "), drawn from their own data with the mod's Look code", 20, rows * T + 34);
        gg.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16)); gg.drawString("Each tile is scaled to fit; true sizes are in the labels. Colour: blackbody (stars), Sudarsky classes (giants), King 2013 cloud cover (planet). Not yet in game: the sky renderer comes next.", 20, rows * T + 62);
        gg.dispose(); ImageIO.write(sheet, "png", new File(out, "gallery.png"));
        ImageIO.write(sizes(home, giant, wd, ns, sg), "png", new File(out, "sizes.png"));
        System.out.println("gallery: " + tiles.size() + " tiles");
    }
    interface P { boolean ok(Galaxy.Ref f); }
    static Galaxy.Ref first(List<Galaxy.Ref> l, P p) { for (Galaxy.Ref f : l) if (p.ok(f)) return f; throw new IllegalStateException("none found"); }

    // ---------------------------------------------------------------- the whole Earth-like planet
    static void full(File out) throws Exception {
        int N = 1800; BufferedImage img = new BufferedImage(N, N + 90, BufferedImage.TYPE_INT_RGB); double tilt = Math.toRadians(-18), yaw = Math.toRadians(-20);
        double[] L = norm(new double[]{-0.45, 0.35, 0.82});   // sunlight from the upper left, mostly behind the viewer
        int[] sp = pw.spawn(); double[] spS = pw.cube.spherePoint(0, sp[0] + 0.5, sp[2] + 0.5);
        for (int py = 0; py < N; py++) for (int px = 0; px < N; px++) {
            double X = (px - N / 2.0) / (N * 0.47), Y = -(py - N / 2.0) / (N * 0.47), r2 = X * X + Y * Y;
            if (r2 > 1) { double glow = r2 < 1.03 ? Math.pow((1.03 - r2) / 0.03, 2) * 0.35 : 0; img.setRGB(px, py, rgb(new double[]{0.15 * glow, 0.3 * glow, 0.8 * glow})); continue; }
            double[] n = {X, Y, Math.sqrt(1 - r2)};
            double[] t1 = {n[0], n[1] * Math.cos(tilt) - n[2] * Math.sin(tilt), n[1] * Math.sin(tilt) + n[2] * Math.cos(tilt)};
            double[] t2 = {t1[0] * Math.cos(yaw) + t1[2] * Math.sin(yaw), t1[1], -t1[0] * Math.sin(yaw) + t1[2] * Math.cos(yaw)};
            double[] s = {-t2[0], t2[2], t2[1]};   // view -> planet: toward the viewer = +Y (face 0), up = +Z (north)
            double[] Lp = {-L[0], L[2], L[1]};
            img.setRGB(px, py, rgb(tone(Look.rocky(pw, s, Lp, n)))); }
        Graphics2D g2 = img.createGraphics(); g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(new Color(10, 12, 20)); g2.fillRect(0, N, N, 90); g2.setColor(Color.WHITE); g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        g2.drawString("The start planet " + pw.system.name() + " " + pw.planet.index + " from space - real size, its own terrain", 20, N + 34);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16)); g2.drawString(pw.describe(), 20, N + 64); g2.dispose();
        ImageIO.write(img, "png", new File(out, "planet_full.png")); }

    // ---------------------------------------------------------------- tiles
    static BufferedImage tile() { BufferedImage t = new BufferedImage(T, T, BufferedImage.TYPE_INT_RGB); Graphics2D g2 = t.createGraphics(); g2.setColor(new Color(4, 5, 10)); g2.fillRect(0, 0, T, T);
        Random r = new Random(T); for (int i = 0; i < 160; i++) { int b = 60 + r.nextInt(120); g2.setColor(new Color(b, b, Math.min(255, b + 20))); g2.fillRect(r.nextInt(T), r.nextInt(T), 1, 1); } g2.dispose(); return t; }
    static void label(BufferedImage t, String a, String b) { Graphics2D g2 = t.createGraphics(); g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(new Color(0, 0, 0, 150)); g2.fillRect(0, T - 62, T, 62); g2.setColor(Color.WHITE); g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17)); g2.drawString(a, 12, T - 37);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13)); g2.setColor(new Color(210, 210, 215)); g2.drawString(b, 12, T - 15); g2.setColor(new Color(60, 60, 70)); g2.drawRect(0, 0, T - 1, T - 1); g2.dispose(); }
    interface Shade { double[] at(double[] n); }
    static void sphere(BufferedImage t, double cx, double cy, double R, Shade s) {
        for (int py = (int) (cy - R); py <= cy + R; py++) for (int px = (int) (cx - R); px <= cx + R; px++) { if (px < 0 || py < 0 || px >= T || py >= T) continue;
            double X = (px - cx) / R, Y = -(py - cy) / R, r2 = X * X + Y * Y; if (r2 > 1) continue; t.setRGB(px, py, rgb(tone(s.at(new double[]{X, Y, Math.sqrt(1 - r2)})))); } }
    static void glow(BufferedImage t, double cx, double cy, double R, double[] c, double k, double falloff) {
        for (int py = 0; py < T; py++) for (int px = 0; px < T; px++) { double d = Math.hypot(px - cx, py - cy) / R; if (d < 1) continue; double v = k * Math.exp(-(d - 1) * falloff);
            if (v < 0.004) continue; int o = t.getRGB(px, py); double[] base = {((o >> 16) & 255) / 255.0, ((o >> 8) & 255) / 255.0, (o & 255) / 255.0};
            t.setRGB(px, py, rgb(new double[]{base[0] + c[0] * v, base[1] + c[1] * v, base[2] + c[2] * v})); } }
    static BufferedImage starTile(Body b, String a, String l2, double size) { BufferedImage t = tile(); double R = T * 0.32 * size;
        sphere(t, T / 2.0, T / 2.0 - 25, R, n -> scale(Look.star(b, n, 7), 0.92)); glow(t, T / 2.0, T / 2.0 - 25, R, Look.blackbody(b.teff), 0.5, 5); label(t, a, l2); return t; }
    static BufferedImage wrTile(Galaxy.Ref f) { BufferedImage t = tile(); double cx = T / 2.0, cy = T / 2.0 - 25;
        for (int py = 0; py < T; py++) for (int px = 0; px < T; px++) { double x = (px - cx) / 170, y = (py - cy) / 170, r = Math.hypot(x, y);   // wind-blown shell (like M1-67 round WR 124): clumpy, Halpha red
            double sh = Math.exp(-Math.pow((r - 1) / 0.18, 2)) * (0.5 + 0.8 * Math.max(0, noise2(x * 4, y * 4))); if (sh < 0.01) continue; add(t, px, py, new double[]{0.9 * sh, 0.25 * sh, 0.3 * sh}); }
        Body b = f.primary; sphere(t, cx, cy, 28, n -> scale(Look.star(b, n, 3), 1.2)); glow(t, cx, cy, 28, Look.blackbody(b.teff), 0.8, 3);
        label(t, "Wolf-Rayet star and its wind nebula", String.format("%.0f K, %.0f Msun (born %.0f), %.0f ly; nebula schematic", b.teff, b.mass, b.initialMass, f.lyFromSun())); return t; }
    static BufferedImage wdTile(Galaxy.Ref f) { BufferedImage t = tile(); Body b = f.primary; double re = 6371, scale = 200 / Math.max(re, b.radiusKm);
        sphere(t, T * 0.30, T / 2.0 - 25, b.radiusKm * scale, n -> scale(Look.star(b, n, 1), 1.2)); glow(t, T * 0.30, T / 2.0 - 25, b.radiusKm * scale, Look.blackbody(b.teff), 0.4, 6);
        sphere(t, T * 0.74, T / 2.0 - 25, re * scale, n -> earthish(n)); label(t, "White dwarf " + f.name() + " beside Earth, to scale", String.format("%.2f Msun in R %,.0f km (Earth %,.0f km), %.0f K, %.1f ly", b.mass, b.radiusKm, re, b.teff, f.lyFromSun())); return t; }
    static double[] earthish(double[] n) { double d = Math.max(0, dot(n, SUN)); return scale(new double[]{0.2, 0.35, 0.6}, 0.05 + d); }
    static BufferedImage nsTile(Galaxy.Ref f) { BufferedImage t = tile(); double cx = T / 2.0, cy = T / 2.0 - 25; double ang = Math.toRadians(25);
        for (int py = 0; py < T; py++) for (int px = 0; px < T; px++) { double x = px - cx, y = py - cy, along = x * Math.sin(ang) - y * Math.cos(ang), across = x * Math.cos(ang) + y * Math.sin(ang);   // the two radio beams along the magnetic axis
            double w = Math.abs(across) / (Math.abs(along) * 0.12 + 2); double v = Math.exp(-w * w) * Math.exp(-Math.abs(along) / 260) * 0.8; if (v > 0.01) add(t, px, py, new double[]{0.35 * v, 0.55 * v, 1.0 * v}); }
        Body b = f.primary; sphere(t, cx, cy, 10, n -> scale(Look.blackbody(1e6), 1.3 * (0.6 + 0.4 * n[2]))); glow(t, cx, cy, 10, new double[]{0.6, 0.7, 1}, 0.9, 1.2);
        label(t, "Neutron star / pulsar " + f.name(), String.format("1.4 Msun in R %.1f km, %.0f ly; beams schematic (not to scale)", b.radiusKm, f.lyFromSun())); return t; }
    static BufferedImage bdTile(Galaxy.Ref f) { BufferedImage t = tile(); Body b = f.primary; double cx = T / 2.0, cy = T / 2.0 - 25, R = 170;
        sphere(t, cx, cy, R, n -> { double band = 0.5 + 0.5 * Math.sin((n[1] + 0.1 * noise2(n[0] * 5, n[1] * 5)) * 18); double[] c = Look.blackbody(Math.max(800, b.teff)); double k = (0.25 + 0.2 * band) * (0.5 + 0.5 * n[2]);
            return new double[]{c[0] * k * 1.1, c[1] * k * 0.5, c[2] * k * 0.6}; });
        label(t, "Brown dwarf " + f.name() + " (spectral " + b.type + ")", String.format("%.3f Msun (%.0f Jupiters), %.0f K, R %,.0f km, %.1f ly", b.mass, b.mass * 1047.6, b.teff, b.radiusKm, f.lyFromSun())); return t; }
    static BufferedImage bhTile(Body b, boolean disk, String a, String l2) { BufferedImage t = new BufferedImage(T, T, BufferedImage.TYPE_INT_RGB); double cx = T / 2.0, cy = T / 2.0 - 25, rs = 40, shadow = 2.598 * rs, thetaE = 3.4 * rs;
        for (int py = 0; py < T; py++) for (int px = 0; px < T; px++) { double x = px - cx, y = py - cy, th = Math.hypot(x, y); double[] c = {0, 0, 0};
            if (th >= shadow) { double beta = th - thetaE * thetaE / th;   // point-lens equation: each pixel sees the sky at beta (the background squeezed into an Einstein ring)
                double sx = cx + x / th * beta, sy = cy + y / th * beta, v = sky(sx, sy) * Math.min(6, th / Math.max(1, Math.abs(beta))) ; c = new double[]{v * 0.9, v * 0.92, v}; }
            if (disk) { double ring = Math.exp(-Math.pow((th - shadow * 1.05) / (0.22 * shadow), 2)) * (0.65 + 0.35 * Math.cos(Math.atan2(y, x) + 2.2)); c = new double[]{c[0] + 1.3 * ring, c[1] + 0.55 * ring, c[2] + 0.15 * ring};
                if (th < shadow) c = new double[]{c[0] * 0.02, c[1] * 0.02, c[2] * 0.02}; }
            t.setRGB(px, py, rgb(c)); }
        label(t, a, l2); return t; }
    /** A procedural star field and faint Milky Way haze (the sky behind a lens). */
    static double sky(double x, double y) { double v = 0.03 * Math.max(0, NZ.fbm(x / 160, y / 160, 1.7, 4) + 0.2); int gx = (int) Math.floor(x / 9), gy = (int) Math.floor(y / 9);
        for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++) { long h = (gx + i) * 73856093L ^ (gy + k) * 19349663L; h = h * 6364136223846793005L + 1442695040888963407L;
            double sx = (gx + i + ((h >>> 33) & 1023) / 1024.0) * 9, sy = (gy + k + ((h >>> 43) & 1023) / 1024.0) * 9, br = ((h >>> 53) & 255) / 255.0; br = br * br * br;
            v += br * 0.9 * Math.exp(-((x - sx) * (x - sx) + (y - sy) * (y - sy)) / 0.9); } return v; }
    static BufferedImage snrTile(Galaxy.Ref f) { BufferedImage t = tile(); double age = Math.max(300, f.primary.deathYr), rpc = 12.5 * Math.pow(age / 1e4, 0.4);   // Sedov-Taylor: R = 12.5 pc (t / 10^4 yr)^0.4 for 10^51 erg in 1 atom/cm^3
        double cx = T / 2.0, cy = T / 2.0 - 25, R = 190;
        for (int py = 0; py < T; py++) for (int px = 0; px < T; px++) { double x = (px - cx) / R, y = (py - cy) / R, r = Math.hypot(x, y), fil = Math.max(0, noise2(x * 6, y * 6)) + 0.5 * Math.max(0, noise2(x * 17, y * 17));
            double shell = Math.exp(-Math.pow((r - 1) / 0.09, 2)) * (0.3 + fil), inner = r < 1 ? 0.12 * fil * r : 0;
            double ha = shell * 0.9 + inner * 0.5, o3 = Math.exp(-Math.pow((r - 0.93) / 0.07, 2)) * (0.2 + fil) * 0.6 + inner;
            if (ha + o3 > 0.01) add(t, px, py, new double[]{ha * 1.0, o3 * 0.75 + ha * 0.15, o3 * 0.7}); }
        sphere(t, cx, cy, 3, n -> new double[]{0.7, 0.8, 1.2});
        label(t, "Supernova remnant (" + f.primary.kind.name().toLowerCase().replace('_', ' ') + " inside)", String.format("exploded %,.0f yr ago, shell ~%.1f pc across (Sedov), %.0f ly; Halpha red, OIII teal", age, 2 * rpc, f.lyFromSun())); return t; }
    static BufferedImage giantTile(Planet p, String a, boolean rings) { BufferedImage t = tile(); double cx = T / 2.0, cy = T / 2.0 - 25, out = p.rings != Planet.Rings.NONE ? p.ringOutKm / p.radiusKm : 1, R = Math.min(170, 245 / out), inc = Math.toRadians(62);
        double[] L = SUN; long sd = (long) (p.massE * 1000);
        drawRings(t, p, cx, cy, R, inc, true);
        sphere(t, cx, cy, R, n -> { double[] s = {n[0], n[1] * Math.cos(-0.35) - n[2] * Math.sin(-0.35), n[1] * Math.sin(-0.35) + n[2] * Math.cos(-0.35)};
            double[] col = Look.giant(p, s, L, n, sd);
            if (p.rings != Planet.Rings.NONE) { double sy = -n[1], ry = Math.cos(inc); }   // ring shadow on the planet is left out for now
            return col; });
        drawRings(t, p, cx, cy, R, inc, false);
        label(t, a, String.format("%.0f Me (%.2f MJ), R %,.0f km, %.0f K, %d moons%s", p.massE, p.massE / 317.83, p.radiusKm, p.teq, p.moons.size(), p.rings == Planet.Rings.NONE ? "" : String.format(", %s rings to %,.0f km", p.rings.name().toLowerCase(), p.ringOutKm))); return t; }
    /** Rings drawn as an ellipse: the back half before the planet, the front half after. */
    static void drawRings(BufferedImage t, Planet p, double cx, double cy, double R, double inc, boolean back) {
        if (p.rings == Planet.Rings.NONE) return; double ci = Math.cos(inc), tilt = Math.toRadians(-12), ct = Math.cos(tilt), st = Math.sin(tilt);
        for (int py = 0; py < T; py++) for (int px = 0; px < T; px++) { double x = (px - cx) / R, y = (py - cy) / R; double xr = x * ct + y * st, yr = -x * st + y * ct; double yy = yr / ci, r = Math.hypot(xr, yy);
            double tau = Look.ring(p, r); if (tau <= 0) continue; boolean isBack = yr < 0; if (isBack != back) continue;
            if (!back && Math.hypot(x, y) < 1 && false) continue; if (back && Math.hypot(x, y) < 1) continue;
            double[] col = p.rings == Planet.Rings.BRIGHT ? new double[]{0.88, 0.80, 0.66} : new double[]{0.55, 0.48, 0.42};
            int o = t.getRGB(px, py); double[] base = {((o >> 16) & 255) / 255.0, ((o >> 8) & 255) / 255.0, (o & 255) / 255.0}; double k = tau * 0.85;
            t.setRGB(px, py, rgb(new double[]{base[0] * (1 - k) + col[0] * k, base[1] * (1 - k) + col[1] * k, base[2] * (1 - k) + col[2] * k})); } }
    static BufferedImage moonTile(Planet.Moon m, Planet host, String a, double rho, double[] base) { BufferedImage t = tile(); double R = 175; long sd = (long) (m.radiusKm() * 10);
        sphere(t, T / 2.0, T / 2.0 - 25, R, n -> { double r = Look.bodyRadius(m.radiusKm() * 2, n, sd); double crater = 0.75 + 0.25 * noise2(n[0] * 9 + n[2] * 3, n[1] * 9) + 0.2 * (r - 1) * 10;
            double maria = rho > 3 ? 0.78 + 0.22 * Math.tanh(4 * noise2(n[0] * 2.2, n[1] * 2.2 + n[2])) : 1; /* dark lava plains (maria) on a rocky moon */ double d = Math.max(0, dot(n, SUN)); double k = (0.02 + d) * crater * maria; return scale(base, k); });
        label(t, a, String.format("R %,.0f km, %.4f Me, orbit %,.0f km round a %.1f Me %s", m.radiusKm(), m.massE(), m.orbitKm(), host.massE, host.kind.name().toLowerCase().replace('_', ' '))); return t; }
    static BufferedImage asteroidTile(double dKm, String a, double albedo, long sd) { BufferedImage t = tile(); double cx = T / 2.0, cy = T / 2.0 - 25, R = 150;
        // ray-march the irregular surface r(dir) from Look.bodyRadius (orthographic view)
        for (int py = 0; py < T; py++) for (int px = 0; px < T; px++) { double X = (px - cx) / R, Y = -(py - cy) / R; if (X * X + Y * Y > 2.2) continue; double hit = Double.NaN; double[] p = null;
            for (double z = 1.6; z > -1.6; z -= 0.01) { double l = Math.sqrt(X * X + Y * Y + z * z); double[] d = {X / l, Y / l, z / l}; if (l <= Look.bodyRadius(dKm, d, sd)) { hit = z; p = new double[]{X, Y, z}; break; } }
            if (p == null) continue; double e = 0.01; double l0 = len(p);
            double[] grad = new double[3]; for (int k = 0; k < 3; k++) { double[] q = p.clone(); q[k] += e; double[] q2 = p.clone(); q2[k] -= e; grad[k] = (f(q, dKm, sd) - f(q2, dKm, sd)) / (2 * e); }
            double[] n = norm(grad); double d = Math.max(0, dot(n, SUN)); double k = (0.015 + d) * (albedo / 0.15) * (0.85 + 0.15 * noise2(p[0] * 20, p[1] * 20 + p[2] * 7));
            t.setRGB(px, py, rgb(tone(new double[]{0.62 * k, 0.58 * k, 0.53 * k}))); }
        label(t, a + String.format(" (%,.0f km)", dKm), String.format("%s; %s, albedo %.2f", dKm >= 400 ? "round: its own gravity pulled it round" : "irregular: too small to pull itself round", albedo < 0.1 ? "C-type (dark carbon)" : "S-type (stony)", albedo)); return t; }
    static double f(double[] p, double dKm, long sd) { double l = len(p); return l - Look.bodyRadius(dKm, new double[]{p[0] / l, p[1] / l, p[2] / l}, sd); }
    static BufferedImage cometTile(StarSystem s) { StarSystem.Comet c = s.comets.get(0); BufferedImage t = tile(); double cx = T * 0.30, cy = T * 0.40;
        double sunAng = Math.toRadians(200), dist = Math.max(0.3, c.perihelionAu()), tail = Math.min(1, 1.4 / dist);   // tails point away from the star; brighter and longer near it
        for (int py = 0; py < T; py++) for (int px = 0; px < T; px++) { double x = px - cx, y = py - cy, along = -(x * Math.cos(sunAng) + y * Math.sin(sunAng)), across = -x * Math.sin(sunAng) + y * Math.cos(sunAng);
            double coma = Math.exp(-(x * x + y * y) / (2 * 18 * 18)) * 1.2;
            double ion = along > 0 ? Math.exp(-Math.pow(across / (3 + along * 0.02), 2)) * Math.exp(-along / (380 * tail)) * 0.7 : 0;                                      // ion tail: straight, blue
            double curve = across - 0.0012 * along * along, dust = along > 0 ? Math.exp(-Math.pow(curve / (6 + along * 0.12), 2)) * Math.exp(-along / (300 * tail)) * 0.8 : 0;   // dust tail: curved, yellow-white
            double v = coma + ion + dust; if (v < 0.01) continue; add(t, px, py, new double[]{coma + dust * 0.95 + ion * 0.2, coma + dust * 0.85 + ion * 0.45, coma * 0.9 + dust * 0.6 + ion * 1.0}); }
        label(t, "Comet in " + s.name(), String.format("nucleus %.1f km, perihelion %.2f AU, %s period; dust tail curved, ion tail straight", c.nucleusKm(), c.perihelionAu(), c.longPeriod() ? "long" : "short")); return t; }
    static BufferedImage rogueTile(Galaxy.Rogue r) { BufferedImage t = tile(); double R = 170; long sd = (long) (r.massE() * 100);
        sphere(t, T / 2.0, T / 2.0 - 25, R, n -> { double ice = 0.5 + 0.5 * noise2(n[0] * 4 + sd % 7, n[1] * 4 + n[2] * 2); double d = Math.max(0, dot(n, norm(new double[]{0.2, 0.9, 0.4}))); double k = 0.004 + 0.07 * d * (0.6 + 0.4 * ice);
            return new double[]{k * 0.8, k * 0.85, k * 1.0}; });   // only starlight reaches it
        glow(t, T / 2.0, T / 2.0 - 25, R, new double[]{0.05, 0.03, 0.02}, 0.25, 30);
        label(t, "Rogue planet (no star)", String.format("%.2f Me, R %,.0f km; lit only by starlight, frozen; 21 of these per star", r.massE(), r.radiusKm())); return t; }
    static BufferedImage beltTile(StarSystem s, StarSystem.Belt b) { BufferedImage t = tile(); double cx = T / 2.0, cy = T / 2.0 - 25, sc = 220 / (b.outerAu() * 1.15); Random r = new Random(5);
        double[] cst = Look.blackbody(s.primary.teff); Graphics2D g2 = t.createGraphics(); g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i = 0; i < 9000; i++) { double a = b.innerAu() + (b.outerAu() - b.innerAu()) * r.nextDouble(), th = r.nextDouble() * 2 * Math.PI; int px = (int) (cx + Math.cos(th) * a * sc), py = (int) (cy + Math.sin(th) * a * sc * 0.45);
            int v = 90 + r.nextInt(80); g2.setColor(new Color(v, v - 8, v - 20)); g2.fillRect(px, py, 1, 1); }
        for (Planet p : s.planets) { double a = p.au * sc; if (a > T) continue; g2.setColor(new Color(80, 90, 120)); g2.drawOval((int) (cx - a), (int) (cy - a * 0.45), (int) (2 * a), (int) (a * 0.9));
            g2.setColor(p.giant() ? new Color(220, 190, 140) : new Color(140, 170, 220)); g2.fillOval((int) (cx + a) - 4, (int) cy - 4, 8, 8); }
        g2.setColor(new Color((int) (cst[0] * 255), (int) (cst[1] * 255), (int) (cst[2] * 255))); g2.fillOval((int) cx - 7, (int) cy - 7, 14, 14); g2.dispose();
        double[] big = b.largest(3); label(t, "Main asteroid belt of " + s.name(), String.format("%.2f-%.2f AU, %.1e Me; largest %.0f, %.0f, %.0f km; %.2g over 1 km", b.innerAu(), b.outerAu(), b.massE(), big[0], big[1], big[2], b.countAbove(1))); return t; }
    static BufferedImage sizes(StarSystem home, Galaxy.Ref giant, Galaxy.Ref wd, Galaxy.Ref ns, Galaxy.Ref sg) {
        int W = 2400, H = 1100; BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB); Graphics2D g2 = img.createGraphics(); g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(new Color(6, 7, 12)); g2.fillRect(0, 0, W, H);
        // panel 1: the start star (its left part) and its planets, one scale
        double rStar = 300, kmPx = rStar / home.primary.radiusKm; int y1 = 300; disc(g2, -120, y1, rStar, Look.blackbody(home.primary.teff));
        int x = 260; g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        for (Planet p : home.planets) { double r = Math.max(1.2, p.radiusKm * kmPx); disc(g2, x + r, y1, r, p.giant() ? new double[]{0.85, 0.75, 0.6} : p.kind == Planet.Kind.MINI_NEPTUNE ? new double[]{0.6, 0.7, 0.85} : new double[]{0.35, 0.55, 0.9});
            g2.setColor(new Color(200, 200, 210)); g2.drawString(String.format("%d: %,.0f km", p.index, p.radiusKm), (int) x - 10, y1 + 40); x += 2 * r + 110; }
        g2.setColor(Color.WHITE); g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22)); g2.drawString(String.format("1. To scale: the start star %s (R %,.0f km, left) and its planets (radius each)", home.name(), home.primary.radiusKm), 30, 40);
        g2.setColor(new Color(50, 50, 60)); g2.drawLine(0, 560, W, 560);
        // panel 2: the supergiant (only its edge fits), the red giant and the start star, one scale
        double k2 = 1250 / sg.primary.radiusKm; int y2 = 850; double rsg = sg.primary.radiusKm * k2; g2.setClip(0, 562, W, H - 562);
        disc(g2, 220 - rsg, y2, rsg, Look.blackbody(sg.primary.teff));
        double rg = giant.primary.radiusKm * k2, rs = home.primary.radiusKm * k2; disc(g2, 700 + rg, y2, rg, Look.blackbody(giant.primary.teff)); disc(g2, 700 + 2 * rg + 120, y2, Math.max(1.5, rs), Look.blackbody(home.primary.teff));
        g2.setColor(Color.WHITE); g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22)); shadow(g2, "2. To scale: a red supergiant (only its edge fits), a red giant and the start star", 30, 600);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));
        shadow(g2, String.format("red supergiant: R %.0f Rsun (%,.0f km)", sg.primary.radiusSun(), sg.primary.radiusKm), 30, 1070);
        shadow(g2, String.format("red giant: R %.0f Rsun", giant.primary.radiusSun()), (int) (700 + rg - 80), (int) (y2 - rg - 15));
        shadow(g2, String.format("start star: R %.2f Rsun (dot)", home.primary.radiusSun()), (int) (700 + 2 * rg + 70), y2 - 20);
        g2.setColor(new Color(190, 190, 200)); shadow(g2, String.format("Too small to see at this scale: white dwarf R %,.0f km (Earth-size), neutron star R %.1f km.", wd.primary.radiusKm, ns.primary.radiusKm), 1300, 1070);
        g2.setClip(null); g2.dispose(); return img; }
    static void shadow(Graphics2D g, String t, int x, int y) { Color c = g.getColor(); g.setColor(new Color(0, 0, 0, 220)); for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) g.drawString(t, x + dx, y + dy); g.setColor(c); g.drawString(t, x, y); }
    static void disc(Graphics2D g2, double cx, double cy, double r, double[] c) { g2.setColor(new Color(rgb(tone(scale(c, 1.0))))); g2.fill(new java.awt.geom.Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r)); }

    // ---------------------------------------------------------------- helpers
    static final com.hopeless.infinity.planet.Noise3 NZ = new com.hopeless.infinity.planet.Noise3(42);
    static double noise2(double x, double y) { return NZ.fbm(x, y, 0.37, 4); }
    static void add(BufferedImage t, int px, int py, double[] c) { int o = t.getRGB(px, py); t.setRGB(px, py, rgb(new double[]{((o >> 16) & 255) / 255.0 + c[0], ((o >> 8) & 255) / 255.0 + c[1], (o & 255) / 255.0 + c[2]})); }
    static double[] tone(double[] c) { return new double[]{Math.pow(c01(c[0]), 1 / 1.6), Math.pow(c01(c[1]), 1 / 1.6), Math.pow(c01(c[2]), 1 / 1.6)}; }
    static int rgb(double[] c) { return (int) (c01(c[0]) * 255) << 16 | (int) (c01(c[1]) * 255) << 8 | (int) (c01(c[2]) * 255); }
    static double c01(double v) { return Math.max(0, Math.min(1, v)); }
    static double[] scale(double[] a, double k) { return new double[]{a[0] * k, a[1] * k, a[2] * k}; }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double len(double[] a) { return Math.sqrt(dot(a, a)); }
    static double[] norm(double[] a) { double l = len(a); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }
}
