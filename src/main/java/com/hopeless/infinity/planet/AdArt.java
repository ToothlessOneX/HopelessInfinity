package com.hopeless.infinity.planet;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * 0.27.3 THE COBBLE & SONS ADVERTISEMENT (owner, 2026-11-04: "a generic advertisement on the screen ... with the company logo"): four slides
 * drawn once (java.awt, headless) with planet/Frontage's font and logo, stacked in one picture; client/ScreenRender uploads it and the
 * shader shows the slides in turn as an LED wall. Pure Java: tools/AdArtDump writes the same picture for the previews. Copy CHOSEN
 * (original): the company's story - one quarry, then gates everywhere.
 */
public final class AdArt {
    public static final int W = 512, H = 288, SLIDES = 4;
    public static final int[] PAL = {0, 0x3c4248, 0xbef0ff, 0x969696, 0x737373, 0x555555, 0x2d2d2d};   // the logo's colours by its index (Frontage.logo)
    public static BufferedImage atlas() {
        BufferedImage im = new BufferedImage(W, H * SLIDES, BufferedImage.TYPE_INT_ARGB); Graphics2D g = im.createGraphics();
        // 1: the name and logo
        bg(g, 0, new Color(0x0b1a33), new Color(0x123e74)); logo(im, 24, 0 * H + 34, 220, true);
        text(im, "COBBLE", 270, 0 * H + 64, 6, 0xffffff); text(im, "& SONS", 270, 0 * H + 124, 6, 0xffffff); text(im, "GATE COMPANY", 272, 0 * H + 190, 3, 0x9fd8ff);
        // 2: humble beginnings
        bg(g, 1, new Color(0x2b2118), new Color(0x6a5136));   // the quarry floor: cobbles along the bottom (Frontage's mortar pattern)
        for (int y = 0; y < 70; y++) for (int x = 0; x < W; x++) im.setRGB(x, 1 * H + 218 + y, Frontage.crack((x % 48) / 48.0, (y % 35) / 35.0, 2 + x / 48) ? 0xff3a2e24 : 0xff7d6a55);
        text(im, "FROM ONE QUARRY", center("FROM ONE QUARRY", 4), 1 * H + 60, 4, 0xfff2d7); text(im, "TO EVERY WORLD.", center("TO EVERY WORLD.", 4), 1 * H + 120, 4, 0xffffff);
        // 3: the gate
        bg(g, 2, new Color(0x02060f), new Color(0x0a1e3a)); ring(im, W / 2 - 108, 2 * H + 10, 216); text(im, "STEP THROUGH.", center("STEP THROUGH.", 3), 2 * H + 248, 3, 0xffffff);
        // 4: the call
        bg(g, 3, new Color(0x0d2a4a), new Color(0x0b1a33)); logo(im, W / 2 - 60, 3 * H + 18, 120, true);
        text(im, "A GATE FOR YOUR TOWN?", center("A GATE FOR YOUR TOWN?", 3), 3 * H + 160, 3, 0xffffff); text(im, "ASK COBBLE & SONS", center("ASK COBBLE & SONS", 3), 3 * H + 206, 3, 0x9fd8ff);
        g.dispose(); return im; }
    /** 0.28.4 the billboard's messages (planet/Billboards): three, 15 : 4 like the 14 x 48 ft bulletin face, the same font and logo. */
    public static final int BW = 960, BH = 256, BSLIDES = 3;
    public static BufferedImage billboardAtlas() { BufferedImage im = new BufferedImage(BW, BH * BSLIDES, BufferedImage.TYPE_INT_ARGB); Graphics2D g = im.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(0x0b1a33), 0, BH, new Color(0x123e74))); g.fillRect(0, 0, BW, BH); logo(im, 24, 18, 220, true); text(im, "COBBLE & SONS", 280, 50, 6, 0xffffff); text(im, "GATES TO EVERY WORLD", 284, 150, 4, 0x9fd8ff);
        g.setPaint(new GradientPaint(0, BH, new Color(0x02060f), 0, 2 * BH, new Color(0x0a1e3a))); g.fillRect(0, BH, BW, BH); ring(im, 40, BH + 20, 216); text(im, "STEP THROUGH.", 300, BH + 70, 7, 0xffffff); text(im, "COBBLE & SONS", 304, BH + 160, 4, 0x9fd8ff);
        g.setPaint(new GradientPaint(0, 2 * BH, new Color(0x2b2118), 0, 3 * BH, new Color(0x6a5136))); g.fillRect(0, 2 * BH, BW, BH);
        for (int y = 0; y < 50; y++) for (int x = 0; x < BW; x++) im.setRGB(x, 2 * BH + 206 + y, Frontage.crack((x % 48) / 48.0, (y % 35) / 35.0, 2 + x / 48) ? 0xff3a2e24 : 0xff7d6a55);
        text(im, "A GATE FOR YOUR TOWN?", (BW - Frontage.width("A GATE FOR YOUR TOWN?") * 5) / 2, 2 * BH + 40, 5, 0xfff2d7); text(im, "ASK AT ANY GATE SALES", (BW - Frontage.width("ASK AT ANY GATE SALES") * 4) / 2, 2 * BH + 130, 4, 0xffffff);
        g.dispose(); return im; }
    static void bg(Graphics2D g, int slide, Color a, Color b) { g.setPaint(new GradientPaint(0, slide * H, a, 0, slide * H + H, b)); g.fillRect(0, slide * H, W, H); }
    static int center(String s, int scale) { return (W - Frontage.width(s) * scale) / 2; }
    /** Text in the block font, each font pixel a scale x scale square, top-left at (x, y). */
    public static void text(BufferedImage im, String s, int x, int y, int scale, int rgb) { int w = Frontage.width(s);
        for (int px = 0; px < w; px++) for (int row = 0; row < 7; row++) if (Frontage.textPix(s, px, row)) for (int a = 0; a < scale; a++) for (int b = 0; b < scale; b++) { int X = x + px * scale + a, Y = y + row * scale + b; if (X >= 0 && Y >= 0 && X < im.getWidth() && Y < im.getHeight()) im.setRGB(X, Y, 0xff000000 | rgb); } }
    /** The logo, size x size px, top-left at (x, y); transparent where it has nothing. */
    public static void logo(BufferedImage im, int x, int y, int size, boolean glow) {
        for (int j = 0; j < size; j++) for (int i = 0; i < size; i++) { int k = Frontage.logo((i + 0.5) / size, 1 - (j + 0.5) / size); if (k == 0) continue; int c = PAL[k]; if (k == 1) c = 0x5a646e; im.setRGB(x + i, y + j, 0xff000000 | c); } }
    /** The gate ring with a glowing field (slide 3). */
    static void ring(BufferedImage im, int x, int y, int size) {
        for (int j = 0; j < size; j++) for (int i = 0; i < size; i++) { double u = (i + 0.5) / size - 0.5, v = 0.5 - (j + 0.5) / size, r = Math.hypot(u, v); int k = Frontage.logo(u + 0.5, v + 0.5);
            if (k == 1 || k == 2) { im.setRGB(x + i, y + j, 0xff000000 | (k == 2 ? PAL[2] : 0x5a646e)); continue; }
            if (r < 0.37) { double t = r / 0.37, sw = Math.sin(10 * Math.atan2(v, u) + 18 * r) * 0.5 + 0.5; int rr = (int) (10 + 70 * (1 - t) + 30 * sw), gg = (int) (60 + 120 * (1 - t) + 40 * sw), bb = (int) (170 + 70 * (1 - t)); im.setRGB(x + i, y + j, 0xff000000 | Math.min(255, rr) << 16 | Math.min(255, gg) << 8 | Math.min(255, bb)); } } }
}
