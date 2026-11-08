import com.hopeless.infinity.planet.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** SeabedPreview seed outDir [size]: draws the 0.5.0 sea floor from the mod's own code (Column.planned -> Seabed.classify, Seabed.life): a map
 *  of the kinds round the spawn (1 px = 1 block) with the plants each column grows, and a side cut from the spawn out to sea showing the
 *  ground, the water and the plants at their heights. Saved tool. */
public class SeabedPreview {
    static final Color[] KC = new Color[Seabed.Kind.values().length];
    static { KC[0] = null; set("SEAGRASS", 0x6fae5a); set("KELP", 0x5c5a52); set("SAND", 0xd9c98f); set("BEACH", 0xeadba6); set("SHINGLE", 0x9c958a); set("SALT_MARSH", 0x8aa05a);
        set("LOW_MARSH", 0x6b5b45); set("ICE_SHORE", 0xb7c3c8); set("CORAL_CREST", 0xe07a5f); set("CORAL_SLOPE", 0xd4a373); set("CORAL_DEEP", 0xb08968); set("DEEP_OOZE", 0xf0eee6); set("SUBTIDAL_ROCK", 0x6e6e6e); }
    static void set(String k, int rgb) { KC[Seabed.Kind.valueOf(k).ordinal()] = new Color(rgb); }
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]); File out = new File(a[1]); out.mkdirs(); int S = a.length > 2 ? Integer.parseInt(a[2]) : 384;
        PlanetWorld w = new PlanetWorld(seed); int[] sp = w.spawn(); int f = 0;
        // seaward direction: the ray from the spawn with the most water within 60 blocks
        double best = -1, dir = 0; for (int k = 0; k < 32; k++) { double t = 2 * Math.PI * k / 32; int wet = 0; for (int m = 4; m <= 60; m += 4) if (Column.of(w, f, sp[0] + (int) Math.round(m * Math.cos(t)), sp[2] + (int) Math.round(m * Math.sin(t))).surfaceY < Terrain.SEA) wet++; if (wet > best) { best = wet; dir = t; } }
        int cx = sp[0] + (int) Math.round(S * 0.3 * Math.cos(dir)), cz = sp[2] + (int) Math.round(S * 0.3 * Math.sin(dir));
        int Z = 2, top = 70, W = S * Z + 260; BufferedImage img = new BufferedImage(W, S * Z + top + 20, BufferedImage.TYPE_INT_RGB); Graphics2D g = img.createGraphics();
        g.setColor(new Color(14, 18, 26)); g.fillRect(0, 0, img.getWidth(), img.getHeight()); g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int[] count = new int[Seabed.Kind.values().length]; int[] plants = new int[Seabed.Plant.values().length];
        for (int j = 0; j < S; j++) for (int i = 0; i < S; i++) {
            int x = cx - S / 2 + i, z = cz - S / 2 + j; Column c = Column.planned(w, f, x, z); Color col;
            if (c.seabed == Seabed.Kind.NONE) { int v = c.surfaceY < Terrain.SEA ? 0x24476b : c.top == Column.Mat.SAND ? 0xd9c98f : c.top == Column.Mat.STONE ? 0x808080 : 0x5e8f3e; col = new Color(v); }
            else { col = KC[c.seabed.ordinal()]; count[c.seabed.ordinal()]++; }
            if (c.surfaceY < Terrain.SEA) { double d = Math.min(1, -c.heightM / 60.0); col = mix(col, new Color(0x1d3d5c), 0.15 + 0.45 * d); }
            Seabed.Life life = Seabed.life(w.seed, x, z, c.seabed, Terrain.SEA - c.surfaceY, -c.heightM); plants[life.plant().ordinal()]++;
            Color pc = switch (life.plant()) { case KELP -> new Color(0x8a7a2a); case SEAGRASS, TALL_SEAGRASS -> new Color(0x2f8f3a); case CORAL_PLANT, CORAL_FAN, CORAL_BLOCK_ONLY -> CORAL[life.coral()]; case GRASS, TALL_GRASS -> new Color(0x7a9a3a); case BOULDER -> new Color(0x5a5a5a); default -> null; };
            g.setColor(col); g.fillRect(i * Z, top + j * Z, Z, Z); if (pc != null) { g.setColor(pc); g.fillRect(i * Z, top + j * Z, Z - 1 > 0 ? Z - 1 : 1, Z - 1 > 0 ? Z - 1 : 1); } }
        int sx = (sp[0] - (cx - S / 2)) * Z, sz = top + (sp[2] - (cz - S / 2)) * Z; g.setColor(Color.WHITE); g.setStroke(new BasicStroke(2)); g.drawOval(sx - 6, sz - 6, 12, 12);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18)); g.drawString("Hopeless Infinity 0.5.0 - sea floor round the spawn (seed " + seed + "), from the mod's code", 10, 26);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12)); g.drawString(String.format("%d x %d blocks, 1 block = %d px; circle = spawn %d %d; darker = deeper (to 60 m); dots = plants placed", S, S, Z, sp[0], sp[2]), 10, 46);
        int ly = top + 6; g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12)); int tot = 0; for (int n : count) tot += n;
        for (Seabed.Kind k : Seabed.Kind.values()) { if (k == Seabed.Kind.NONE || count[k.ordinal()] == 0) continue; g.setColor(KC[k.ordinal()]); g.fillRect(S * Z + 14, ly, 14, 14); g.setColor(Color.WHITE);
            g.drawString(String.format("%s %.1f%%", k.name().toLowerCase(), 100.0 * count[k.ordinal()] / tot), S * Z + 34, ly + 12); ly += 20; }
        ly += 10; g.drawString("plants placed:", S * Z + 14, ly); ly += 18;
        for (Seabed.Plant p : Seabed.Plant.values()) if (p != Seabed.Plant.NONE && plants[p.ordinal()] > 0) { g.drawString(p.name().toLowerCase() + " " + plants[p.ordinal()], S * Z + 20, ly); ly += 16; }
        g.dispose(); ImageIO.write(img, "png", new File(out, "seabed_map.png"));
        // side cut: from 20 blocks inland of the spawn, 300 blocks out to sea along 'dir'
        int L = 320, H = 300, PX = 3; BufferedImage cut = new BufferedImage(L * PX + 40, H + 90, BufferedImage.TYPE_INT_RGB); g = cut.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON); g.setColor(new Color(150, 190, 225)); g.fillRect(0, 0, cut.getWidth(), cut.getHeight());
        int y0 = 60, seaPx = y0 + 20;   // sea surface row; 3 px per block vertically too
        int deepest = 0; Column[] cc = new Column[L]; int[] xs = new int[L], zs = new int[L];
        for (int s = 0; s < L; s++) { xs[s] = sp[0] + (int) Math.round((s - 20) * Math.cos(dir)); zs[s] = sp[2] + (int) Math.round((s - 20) * Math.sin(dir)); cc[s] = Column.planned(w, f, xs[s], zs[s]); deepest = Math.max(deepest, Terrain.SEA - cc[s].surfaceY); }
        int vs = Math.max(1, Math.min(6, (H - 40) / Math.max(1, deepest + 4)));
        for (int s = 0; s < L; s++) { Column c = cc[s]; int gy = seaPx + (Terrain.SEA + 1 - (c.surfaceY + 1)) * vs; int X = 20 + s * PX;
            if (c.surfaceY < Terrain.SEA) { g.setColor(new Color(40, 90, 140)); g.fillRect(X, seaPx, PX, gy - seaPx); }
            Color gc = c.seabed == Seabed.Kind.NONE ? new Color(c.top == Column.Mat.SAND ? 0xd9c98f : c.top == Column.Mat.STONE ? 0x808080 : 0x5e8f3e) : KC[c.seabed.ordinal()];
            g.setColor(gc); g.fillRect(X, gy, PX, vs); g.setColor(new Color(110, 100, 90)); g.fillRect(X, gy + vs, PX, cut.getHeight() - gy - vs);
            Seabed.Life life = Seabed.life(w.seed, xs[s], zs[s], c.seabed, Terrain.SEA - c.surfaceY, -c.heightM);
            if (life.plant() != Seabed.Plant.NONE) { Color pc = switch (life.plant()) { case KELP -> new Color(0x9a8a2a); case SEAGRASS, TALL_SEAGRASS -> new Color(0x3fb04a); case GRASS, TALL_GRASS -> new Color(0x7a9a3a); case BOULDER -> new Color(0x555555); default -> CORAL[life.coral()]; };
                g.setColor(pc); g.fillRect(X + (PX > 2 ? 1 : 0), gy - life.height() * vs, Math.max(1, PX - 1), life.height() * vs); } }
        g.setColor(Color.BLACK); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16)); g.drawString("Side cut from the spawn out to sea (seed " + seed + "), from the mod's code", 10, 22);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12)); g.drawString(String.format("%d blocks long, 1 block = %d px across, %d px up; spawn at the 20th block; kelp grows to the surface between 4 and 30 m", L, PX, vs), 10, 40);
        g.dispose(); ImageIO.write(cut, "png", new File(out, "seabed_cut.png"));
        System.out.println("seabed preview written; kinds " + java.util.Arrays.toString(count) + " plants " + java.util.Arrays.toString(plants));
    }
    static final Color[] CORAL = {new Color(0x3355dd), new Color(0xe070b0), new Color(0xb040d0), new Color(0xe03030), new Color(0xe0d040)};
    static Color mix(Color a, Color b, double t) { return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t), (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t)); }
}
