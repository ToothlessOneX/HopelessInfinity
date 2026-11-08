import com.hopeless.infinity.galaxy.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Renders galaxy v2 from the generator itself: the face-on star density (Galaxy.surface), every young massive star of the galaxy
 * (youngColumn) coloured by temperature, Wolf-Rayet stars, supernova remnants, Sgr A* and the start system; then the start system.
 * Args: seed outDir. Saved tool.
 */
public class GalaxyRender {
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]); File out = new File(a[1]); out.mkdirs(); Galaxy g = new Galaxy(seed);
        int N = 1400; double half = 15500; float[] dens = new float[N * N]; float mx = 0;
        for (int py = 0; py < N; py++) for (int px = 0; px < N; px++) { double x = (px + 0.5) / N * 2 * half - half, y = half - (py + 0.5) / N * 2 * half; float d = (float) Galaxy.surface(x, y); dens[py * N + px] = d; mx = Math.max(mx, d); }
        float[][] acc = new float[3][N * N];
        for (int i = 0; i < N * N; i++) { double v = Math.log1p(dens[i]) / Math.log1p(mx); float[] c = {1f, 0.86f, 0.70f}; for (int k = 0; k < 3; k++) acc[k][i] = (float) (Math.pow(v, 1.6) * c[k] * 0.85); }
        int young = 0, wr = 0, snr = 0; java.util.List<double[]> snrs = new java.util.ArrayList<>(), wrs = new java.util.ArrayList<>();
        for (int sx = -Galaxy.COLUMNS; sx < Galaxy.COLUMNS; sx++) for (int sy = -Galaxy.COLUMNS; sy < Galaxy.COLUMNS; sy++) for (Galaxy.Ref f : g.youngColumn(sx, sy)) {
            int px = (int) ((f.x + half) / (2 * half) * N), py = (int) ((half - f.y) / (2 * half) * N); if (px < 0 || py < 0 || px >= N || py >= N) continue; Body b = f.primary;
            if (b.alive()) { young++; float[] c = rgb(b.teff); float w = (float) Math.min(0.5, 0.03 * Math.pow(b.lum, 0.15)); for (int k = 0; k < 3; k++) acc[k][py * N + px] += c[k] * w; if (b.kind == Body.Kind.WOLF_RAYET) { wr++; wrs.add(new double[]{px, py}); } }
            else if (b.deathYr <= Research.SNR_LIFE_YR) { snr++; snrs.add(new double[]{px, py}); } }
        BufferedImage img = new BufferedImage(N, N + 120, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < N * N; i++) { int rgb = 0; for (int k = 0; k < 3; k++) rgb = (rgb << 8) | Math.min(255, (int) (Math.pow(Math.min(1, acc[k][i]), 0.85) * 255)); img.setRGB(i % N, i / N, rgb); }
        Graphics2D gr = img.createGraphics(); gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); gr.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        gr.setColor(new Color(255, 80, 80, 170)); for (double[] p : snrs) gr.drawOval((int) p[0] - 3, (int) p[1] - 3, 6, 6);
        gr.setColor(new Color(120, 220, 255)); for (double[] p : wrs) gr.fillRect((int) p[0] - 1, (int) p[1] - 1, 3, 3);
        gr.setColor(Color.WHITE); gr.setStroke(new BasicStroke(2)); gr.drawOval(N / 2 - 9, N / 2 - 9, 18, 18); gr.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16)); gr.drawString("Sgr A* (4.15 million Msun)", N / 2 + 14, N / 2 + 5);
        StarSystem sp = g.spawn(); int ssx = (int) ((sp.ref.x + half) / (2 * half) * N), ssy = (int) ((half - sp.ref.y) / (2 * half) * N);
        gr.setColor(new Color(90, 255, 140)); gr.drawOval(ssx - 10, ssy - 10, 20, 20); gr.drawLine(ssx + 10, ssy + 10, ssx + 50, ssy + 50); gr.drawString("Start: " + sp.name() + " (" + sp.primary.type + ")", ssx + 54, ssy + 64);
        gr.setColor(new Color(10, 12, 20)); gr.fillRect(0, N, N, 120); gr.setColor(Color.WHITE); gr.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        gr.drawString("Hopeless Infinity v2 - real-size galaxy of seed " + seed, 20, N + 34); gr.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16)); gr.setColor(new Color(200, 200, 200));
        gr.drawString(String.format("Glow: face-on star density from the generator (~1.3e11 objects). Dots: all %,d young massive stars (O/B, supergiants), coloured by temperature.", young), 20, N + 62);
        gr.drawString(String.format("Blue squares: %d Wolf-Rayet stars.  Red rings: %d supernova remnants younger than 100,000 yr.  Scale bar 5 kpc.", wr, snr), 20, N + 86);
        int bar = (int) (5000 / (2 * half) * N); gr.setColor(Color.WHITE); gr.setStroke(new BasicStroke(3)); gr.drawLine(20, N - 30, 20 + bar, N - 30);
        gr.dispose(); ImageIO.write(img, "png", new File(out, "galaxy_v2.png"));
        ImageIO.write(system(sp), "png", new File(out, "start_system_v2.png"));
        // the nearest system to the Sun's place with a bright-ringed giant and a main asteroid belt
        StarSystem show = null; long scy = (long) Math.floor(-Research.R0_KPC * 1000 / Galaxy.CELL);
        search: for (int ring = 0; ring < 30; ring++) for (long dx = -ring; dx <= ring; dx++) for (long dy = -ring; dy <= ring; dy++) { if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) continue;
            for (Galaxy.Ref f : g.cell(dx, scy + dy, 0)) { if (f.primary.kind != Body.Kind.MAIN_SEQUENCE) continue; StarSystem s = g.system(f); boolean ring2 = false, belt = false;
                for (Planet p : s.planets) if (p.rings == Planet.Rings.BRIGHT) ring2 = true; for (StarSystem.Belt b : s.belts) if (b.kind().startsWith("main")) belt = true;
                if (ring2 && belt) { show = s; break search; } } }
        if (show != null) { ImageIO.write(system(show), "png", new File(out, "ringed_system_v2.png")); System.out.print(show); }
        System.out.printf("rendered: young %d, WR %d, SNR %d, start %s%n", young, wr, snr, sp.name());
    }
    static BufferedImage system(StarSystem s) {
        int W = 2200, H = 900; BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB); Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(8, 10, 18)); g.fillRect(0, 0, W, H);
        double lo = Math.log10(0.03), hi = Math.log10(60); int x0 = 200, x1 = W - 60, cy = 430;
        java.util.function.DoubleUnaryOperator X = au -> x0 + (Math.log10(au) - lo) / (hi - lo) * (x1 - x0);
        Star st = s.star; int hz0 = (int) X.applyAsDouble(st.hz(Research.MOIST)), hz1 = (int) X.applyAsDouble(st.hz(Research.MAX_GREENHOUSE));
        g.setColor(new Color(30, 110, 50)); g.fillRect(hz0, 150, hz1 - hz0, 560); g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16)); g.setColor(new Color(150, 230, 160)); g.drawString("habitable zone", hz0 + 4, 170);
        for (StarSystem.Belt b : s.belts) { int a0 = (int) X.applyAsDouble(b.innerAu()), a1 = (int) X.applyAsDouble(b.outerAu()); java.util.Random rr = new java.util.Random(a0);
            g.setColor(new Color(170, 150, 120)); for (int i = 0; i < 900; i++) { int px = a0 + rr.nextInt(Math.max(1, a1 - a0)), py = cy + (int) (rr.nextGaussian() * 60); g.fillRect(px, py, 2, 2); }
            g.drawString(String.format("%s (largest body %.0f km)", b.kind(), b.largestKm()), a0, cy + 230); }
        g.setColor(new Color(90, 90, 110)); g.drawLine(x0, cy, x1, cy);
        for (double t : new double[]{0.03, 0.1, 0.3, 1, 3, 10, 30}) { int x = (int) X.applyAsDouble(t); g.drawLine(x, 760, x, 770); g.drawString(t + " AU", x - 20, 790); }
        float[] sc = rgb(s.primary.teff); g.setColor(new Color(sc[0], sc[1], sc[2])); g.fillOval(30, cy - 70, 140, 140);
        for (Planet p : s.planets) {
            int x = (int) X.applyAsDouble(p.au), r = (int) Math.max(5, Math.min(55, 6 * Math.pow(p.radiusE, 0.6) * 2));
            if (p.rings != Planet.Rings.NONE) { g.setColor(p.rings == Planet.Rings.BRIGHT ? new Color(230, 210, 160) : new Color(150, 140, 120)); g.setStroke(new BasicStroke(p.rings == Planet.Rings.BRIGHT ? 4 : 1.5f));
                int rr2 = (int) (r * p.ringOutKm / p.radiusKm); g.drawOval(x - rr2, cy - rr2 / 4, 2 * rr2, rr2 / 2); g.setStroke(new BasicStroke(1)); }
            g.setColor(switch (p.kind) { case ROCKY -> p.climate == Planet.Climate.TEMPERATE ? new Color(80, 160, 255) : p.climate == Planet.Climate.HOT || p.climate == Planet.Climate.WARM_EDGE ? new Color(220, 120, 70) : new Color(220, 230, 240);
                case MINI_NEPTUNE -> new Color(150, 180, 220); case ICE_GIANT -> new Color(120, 200, 210); case GAS_GIANT -> new Color(215, 175, 120); });
            g.fillOval(x - r, cy - r, 2 * r, 2 * r);
            int regular = 0; for (Planet.Moon m : p.moons) if (m.kind() == Planet.MoonKind.REGULAR || m.kind() == Planet.MoonKind.GIANT_IMPACT) regular++;
            g.setColor(Color.WHITE); boolean up = p.index % 2 == 0; int ty = up ? cy - r - 92 : cy + r + 34;
            g.drawString(p.index + ": " + p.kind.name().toLowerCase().replace('_', ' '), x - 45, ty); g.drawString(String.format("%.2f Me  %.0f km", p.massE, p.radiusKm), x - 45, ty + 19);
            g.drawString(String.format("%.2f g  %.0f K", p.gravity / Research.G_EARTH, p.teq), x - 45, ty + 38); g.drawString(p.moons.isEmpty() ? "no moons" : (regular + " major + " + (p.moons.size() - regular) + " small moons"), x - 45, ty + 57); }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26)); g.setColor(Color.WHITE);
        g.drawString(String.format("%s - %s, %.2f Msun, %.0f K, radius %,.0f km, %.1f ly from the Sun's place", s.name(), s.primary.type, s.primary.mass, s.primary.teff, s.primary.radiusKm, s.ref.lyFromSun()), 30, 48);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17)); g.setColor(new Color(200, 200, 200));
        g.drawString(String.format("%s.  Oort cloud ~%.1e comets; %d active comets now.  Distances log scale; planet sizes not to scale (real radii in km).", s.ref.companion == null ? "Single star" : String.format("Binary: companion %s at %.1f AU", s.ref.companion.type, s.ref.sepAu), s.oortComets, s.comets.size()), 30, 80);
        g.dispose(); return img; }
    static float[] rgb(double t) { double k = t / 100, r, g, b;
        r = k <= 66 ? 255 : 329.698727446 * Math.pow(k - 60, -0.1332047592); g = k <= 66 ? 99.4708025861 * Math.log(k) - 161.1195681661 : 288.1221695283 * Math.pow(k - 60, -0.0755148492);
        b = k >= 66 ? 255 : k <= 19 ? 0 : 138.5177312231 * Math.log(k - 10) - 305.0447927307; return new float[]{(float) c(r / 255), (float) c(g / 255), (float) c(b / 255)}; }
    static double c(double v) { return Math.max(0, Math.min(1, v)); }
}
