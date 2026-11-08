package com.hopeless.infinity.client;

import com.hopeless.infinity.HopelessInfinity;
import com.hopeless.infinity.planet.PlanetWorld;
import com.hopeless.infinity.sky.SkyModel;
import com.hopeless.infinity.sky.SkyShade;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;

/**
 * 0.6.0: the planet's own sky in place of Minecraft's (the dimension type's "effects" is hopelessinfinity:planet): one full-screen pass of
 * sky.glsl on the GPU, drawn where Minecraft draws its sky. The star field and the milky way are baked once per world on a worker thread
 * (SkyModel.bake, ~3 s) and uploaded as one texture; until then the sky has no stars. The game thread only works out a few directions and
 * the exposure (SkyShade). The fog colour in air is the sky's own horizon colour, so the far terrain meets the sky without a seam.
 * Off (/hinf sky off on the client: not yet; this flag): Minecraft's sky comes back. Probes: frames, bake, exposure, sun height, errors.
 */
public final class SkyRenderer {
    private SkyRenderer() {}
    public static final ResourceLocation EFFECTS = new ResourceLocation(HopelessInfinity.MODID, "planet");
    public static volatile boolean on = true;
    static volatile SkyModel model; static volatile float[] baked; static volatile long bakeSeed = Long.MIN_VALUE;
    static int prog, vao, tex, homeTex; static volatile float[] homeBaked; static boolean homeIn; static boolean failed; static final java.util.Map<String, Integer> U = new java.util.HashMap<>();
    public static volatile long frames, fogSets, errors; public static volatile double lastExposure, lastSunY; public static volatile String lastError = "", bakeNote = "not baked";
    static final FloatBuffer M = BufferUtils.createFloatBuffer(16), V4 = BufferUtils.createFloatBuffer(48);

    /** The effects: Minecraft's overworld values, the sky drawn by us. */
    public static final class Effects extends DimensionSpecialEffects {
        public Effects() { super(192f, true, SkyType.NORMAL, false, false); }
        @Override public Vec3 getBrightnessDependentFogColor(Vec3 c, float b) { return c.multiply(b * 0.94f + 0.06f, b * 0.94f + 0.06f, b * 0.91f + 0.09f); }
        @Override public boolean isFoggyAt(int x, int z) { return false; }
        @Override public float[] getSunriseColor(float t, float pt) { return on && model != null ? null : super.getSunriseColor(t, pt); }
        @Override public boolean renderSky(ClientLevel level, int ticks, float pt, PoseStack ps, Camera cam, Matrix4f proj, boolean foggy, Runnable setupFog) {
            if (!on || failed || model == null) return false;
            try { if (at(level, pt, cam.getPosition()) == null) return false;   // 0.12.0: in space before the first state
                draw(level, pt, ps, cam, proj); frames++; return true; }
            catch (Throwable t) { errors++; lastError = String.valueOf(t); if (errors < 5) HopelessInfinity.LOG.error("[Hopeless Infinity] sky failed", t); failed = errors > 20; return false; }
        }
    }

    /** A new world's planet (client): the model now, the star field on a worker. */
    public static void world(PlanetWorld w) {
        if (w == null || bakeSeed == w.seed) return; bakeSeed = w.seed; SkyModel m = new SkyModel(w); model = m; baked = null; bakeNote = "baking";
        Thread t = new Thread(() -> { try { float[] b = m.bake(); for (int i = 0; i < b.length; i++) b[i] *= 1e6f; if (model == m) { baked = b; bakeNote = m.bakeNote; HopelessInfinity.LOG.info("[Hopeless Infinity] sky: {}", m.bakeNote); }
                long h0 = System.nanoTime(); float[] hm = com.hopeless.infinity.sky.HomeMap.bake(w); if (model == m) { homeBaked = hm; HopelessInfinity.LOG.info("[Hopeless Infinity] sky: home planet map baked in {} ms", (System.nanoTime() - h0) / 1000000); } }
            catch (Throwable e) { errors++; lastError = String.valueOf(e); HopelessInfinity.LOG.error("[Hopeless Infinity] sky bake failed", e); } }, "Hopeless Infinity sky bake");
        t.setDaemon(true); t.setPriority(Thread.MIN_PRIORITY); t.start();
        HopelessInfinity.LOG.info("[Hopeless Infinity] sky: star Teff {} K, {} deg across, {} moons, {} other planets, year {} days, air scale heights x{}",
            Math.round(m.teff), String.format("%.3f", Math.toDegrees(2 * m.angR)), m.moons.size(), m.others.size(), Math.round(m.yearDays), String.format("%.2f", m.hScale)); }

    /** 0.8.0: the real sky now at the camera (galaxy/Orbits through sky/Ephemeris, the server's clock), or null off a planet. */
    static com.hopeless.infinity.sky.Ephemeris.At at(ClientLevel level, float pt, Vec3 c) {
        SkyModel m = model; int f = com.hopeless.infinity.world.Faces.face(level.dimension()); if (m == null) return null;
        var e = com.hopeless.infinity.world.Clock.eph(m.w); double t = com.hopeless.infinity.world.Clock.seconds(level.getGameTime(), pt);
        int bid = com.hopeless.infinity.world.Faces.body(level.dimension());
        if (f < 0 && bid >= 1) { var mw = m.w.body(bid); return mw == null ? null : e.atBody(mw, com.hopeless.infinity.world.Faces.bodyFace(level.dimension()), c.x, c.z, t); }   // 0.18.0 any body's sky   // 0.15.0 the sky from the moon
        if (f < 0) { var v = SpaceClient.view(); if (v == null) return null; if (v.body() >= 1) { var mw = m.w.body(v.body()); if (mw == null) return null; var a = e.atBody(mw, v.face(), v.x(), v.z(), t); a.skyMode = 1; return a; } return e.at(v.face(), v.x(), v.z(), t); }   /* 0.18.0 over any body from space: its air not drawn (CHOSEN) */   // 0.16.0 over the moon: the sky from the moon   // 0.12.0 in space: the sky over the point below
        return e.at(f, c.x, c.z, t); }
    /** 0.18.0 the cloud deck's glow (thick CO2 air): the light reaching the ground is DECK_F of the star's (CHOSEN, Venus: about 17 W/m2 of
     *  2,601 at the top, Titov et al. 2007, a few percent where the star is high), spread evenly over the sky (radiance F mu / pi), orange
     *  (Venera 13/14 colour images: the air takes out the blue; CHOSEN tint of luminance 1). The exposure is held at a midday's. */
    static final double DECK_F = 0.025, DECK_EXP = 0.18 / (DECK_F * 0.5 / Math.PI); static final double[] DECK_TINT = {1.45, 0.90, 0.46};
    static double[] deckRgb(double sunY) { double l = DECK_F * Math.max(0, sunY) / Math.PI + 2e-5; return new double[]{DECK_TINT[0] * l, DECK_TINT[1] * l, DECK_TINT[2] * l}; }
    /** 0.12.0: the camera's height over the sea (m): the block y on a face, the real height in space. */
    static double alt(Vec3 c) { var v = SpaceClient.view(); return v != null ? v.camY() - com.hopeless.infinity.planet.Terrain.SEA : c.y; }

    static void draw(ClientLevel level, float pt, PoseStack ps, Camera cam, Matrix4f proj) {
        SkyModel m = model; var a = at(level, pt, cam.getPosition()); if (a == null) throw new IllegalStateException("no planet frame");
        if (prog == 0) build();
        if (baked != null) upload();
        if (homeBaked != null) uploadHome();
        boolean airless = a.skyMode == 1 || (a.onMoon >= 0 && a.skyMode != 2), deck = a.skyMode == 2; double alt = alt(cam.getPosition()), exp = airless ? SkyShade.exposureAirless(m, a.sun) : SkyShade.exposure(m, a.sun, alt); if (deck) exp = DECK_EXP; lastExposure = exp; lastSunY = a.sun[1]; lastAt = a;
        float rain = SpaceClient.inSpace() || airless ? 0f : Math.min(1f, level.getRainLevel(pt) + 0.5f * level.getThunderLevel(pt));
        Matrix4f inv = new Matrix4f(proj).mul(ps.last().pose()).invert();
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        RenderSystem.depthMask(false); RenderSystem.disableDepthTest(); RenderSystem.disableBlend();
        GlStateManager._glUseProgram(prog);
        M.clear(); inv.get(M); GL20.glUniformMatrix4fv(u("u_InvViewProj"), false, M);
        GL20.glUniform4f(u("u_Sun"), (float) a.sun[0], (float) a.sun[1], (float) a.sun[2], (float) a.sunAngR);
        GL20.glUniform4f(u("u_SunRgb"), (float) (m.sunRgb[0] / lum(m.sunRgb)), (float) (m.sunRgb[1] / lum(m.sunRgb)), (float) (m.sunRgb[2] / lum(m.sunRgb)), (float) com.hopeless.infinity.look.Look.LIMB_U);
        double[] air = SkyShade.air(m); GL20.glUniform4f(u("u_Air"), (float) air[0], (float) air[1], (float) air[2], airless ? 0f : (float) air[3]); { double[] dk = deckRgb(a.sun[1]); GL20.glUniform4f(u("u_Deck"), (float) dk[0], (float) dk[1], (float) dk[2], deck ? 1f : 0f); }   // 0.15.0: no air on the moon (the shader skips the scattering)
        GL20.glUniform4f(u("u_View"), (float) alt, (float) exp, rain, tex != 0 ? 1e-6f : 0f);
        double[][] ax = a.starAxes();   // the star field is baked in the system's frame: x -> u_Noon, -y -> u_West, z -> u_Pole, lst 0
        GL20.glUniform4f(u("u_Pole"), (float) ax[2][0], (float) ax[2][1], (float) ax[2][2], 0f);
        GL20.glUniform4f(u("u_Noon"), (float) ax[0][0], (float) ax[0][1], (float) ax[0][2], 0f); GL20.glUniform4f(u("u_West"), (float) ax[1][0], (float) ax[1][1], (float) ax[1][2], 0f);
        GL20.glUniform4f(u("u_Ecl"), (float) a.sunVisible, 0f, 0f, 0f);
        // disks: the bodies wider than DISK_MIN (rad), nearest first, at most 8; the rest as points of light
        java.util.List<com.hopeless.infinity.sky.Ephemeris.Body> disks = new java.util.ArrayList<>(), points = new java.util.ArrayList<>();
        for (var b : a.bodies) (b.angR() > DISK_MIN ? disks : points).add(b);
        disks.sort((p, q) -> Double.compare(p.distKm(), q.distKm())); while (disks.size() > 8) points.add(disks.remove(disks.size() - 1)); lastDisks = disks.size();
        FloatBuffer d0 = BufferUtils.createFloatBuffer(32), d1 = BufferUtils.createFloatBuffer(32), d2 = BufferUtils.createFloatBuffer(32);
        for (int i = 0; i < 8; i++) { if (i < disks.size()) { var b = disks.get(i); d0.put((float) b.dir()[0]).put((float) b.dir()[1]).put((float) b.dir()[2]).put((float) b.angR()); d1.put((float) b.light()).put((float) b.albedo()).put(b.kind()).put((float) (b.seed() % 97)); d2.put((float) b.sunDir()[0]).put((float) b.sunDir()[1]).put((float) b.sunDir()[2]).put(0f); }
            else { d0.put(0).put(0).put(0).put(0); d1.put(0).put(0).put(0).put(0); d2.put(0).put(0).put(0).put(0); } }
        d0.flip(); d1.flip(); d2.flip(); GL20.glUniform4fv(u("u_Moon"), d0); GL20.glUniform4fv(u("u_MoonL"), d1); GL20.glUniform4fv(u("u_MoonS"), d2);
        V4.clear(); for (int i = 0; i < 12; i++) { if (i < points.size()) { var b = points.get(i); V4.put((float) b.dir()[0]).put((float) b.dir()[1]).put((float) b.dir()[2]).put((float) b.flux()); } else V4.put(0).put(0).put(0).put(0); }
        V4.flip(); GL20.glUniform4fv(u("u_Planet"), V4);
        // 0.15.0 the home planet's map and axes (its disk from the moon)
        double[][] ha = a.homeAxes; GL20.glUniform4f(u("u_HomeX"), ha == null ? 1f : (float) ha[0][0], ha == null ? 0f : (float) ha[0][1], ha == null ? 0f : (float) ha[0][2], homeIn ? 1f : 0f);
        GL20.glUniform4f(u("u_HomeY"), ha == null ? 0f : (float) ha[1][0], ha == null ? 1f : (float) ha[1][1], ha == null ? 0f : (float) ha[1][2], 0f); GL20.glUniform4f(u("u_HomeZ"), ha == null ? 0f : (float) ha[2][0], ha == null ? 0f : (float) ha[2][1], ha == null ? 1f : (float) ha[2][2], 0f);
        RenderSystem.activeTexture(GL13.GL_TEXTURE1); GlStateManager._bindTexture(homeTex); GL20.glUniform1i(u("u_Home"), 1);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0); GlStateManager._bindTexture(tex); GL20.glUniform1i(u("u_Stars"), 0);
        GlStateManager._glBindVertexArray(vao); GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        GlStateManager._glBindVertexArray(0); BufferUploader.invalidate(); GlStateManager._glUseProgram(0); GlStateManager._bindTexture(0); RenderSystem.activeTexture(GL13.GL_TEXTURE1); GlStateManager._bindTexture(0); RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.depthMask(true); if (depth) RenderSystem.enableDepthTest(); if (blend) RenderSystem.enableBlend();
    }
    /** A body is drawn as a disk when wider than this (rad): about a third of a pixel at 1440 lines and 70 deg (CHOSEN); smaller ones are points of light. */
    public static final double DISK_MIN = 3e-4;
    public static volatile int lastDisks; public static volatile com.hopeless.infinity.sky.Ephemeris.At lastAt;
    static double lum(double[] c) { return SkyShade.lum(c[0], c[1], c[2]); }
    static int u(String n) { return U.computeIfAbsent(n, k -> GL20.glGetUniformLocation(prog, k)); }
    static void upload() {
        float[] b = baked; baked = null; if (b == null) return;
        if (tex == 0) tex = GL11.glGenTextures();
        RenderSystem.activeTexture(GL13.GL_TEXTURE0); GlStateManager._bindTexture(tex);
        int ua = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT), ur = GL11.glGetInteger(GL12.GL_UNPACK_ROW_LENGTH), us = GL11.glGetInteger(GL12.GL_UNPACK_SKIP_ROWS), up = GL11.glGetInteger(GL12.GL_UNPACK_SKIP_PIXELS);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4); GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, 0); GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS, 0); GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS, 0);
        FloatBuffer fb = BufferUtils.createFloatBuffer(b.length); fb.put(b).flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGB16F, SkyModel.TW, SkyModel.TH, 0, GL11.GL_RGB, GL11.GL_FLOAT, fb);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, ua); GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, ur); GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS, us); GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS, up);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR); GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT); GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GlStateManager._bindTexture(0); HopelessInfinity.LOG.info("[Hopeless Infinity] sky: star field uploaded ({} x {})", SkyModel.TW, SkyModel.TH); }
    /** 0.15.0: the home planet's albedo map onto texture unit 1 (linear, wrapping round in longitude). */
    static void uploadHome() { float[] b = homeBaked; homeBaked = null; if (b == null) return; if (homeTex == 0) homeTex = GL11.glGenTextures();
        RenderSystem.activeTexture(GL13.GL_TEXTURE1); GlStateManager._bindTexture(homeTex); int ua = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT), ur = GL11.glGetInteger(GL12.GL_UNPACK_ROW_LENGTH);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4); GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, 0); FloatBuffer fb = BufferUtils.createFloatBuffer(b.length); fb.put(b).flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGB16F, com.hopeless.infinity.sky.HomeMap.W, com.hopeless.infinity.sky.HomeMap.H, 0, GL11.GL_RGB, GL11.GL_FLOAT, fb);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, ua); GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, ur);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR); GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT); GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE); GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GlStateManager._bindTexture(0); RenderSystem.activeTexture(GL13.GL_TEXTURE0); homeIn = true; HopelessInfinity.LOG.info("[Hopeless Infinity] sky: home planet map uploaded"); }
    /** The program: a full-screen triangle; the fragment shader makes the view ray from the inverse view-projection. */
    static void build() {
        String body; try (InputStream in = SkyRenderer.class.getResourceAsStream("/assets/hopelessinfinity/shaders/sky.glsl")) { body = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
        catch (Exception e) { failed = true; throw new IllegalStateException("sky.glsl missing", e); }
        String vs = "#version 150\nout vec2 sk_ndc;\nvoid main() { vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2)) * 2.0 - 1.0; sk_ndc = p; gl_Position = vec4(p, 1.0, 1.0); }\n";
        String fs = "#version 150\nin vec2 sk_ndc;\nout vec4 fragColor;\n" + body + "\nvoid main() { vec4 w = u_InvViewProj * vec4(sk_ndc, 1.0, 1.0); vec3 v = normalize(w.xyz / w.w); fragColor = vec4(sk_display(sk_radiance(v)), 1.0); }\n";
        int v = shader(GL20.GL_VERTEX_SHADER, vs), f = shader(GL20.GL_FRAGMENT_SHADER, fs); prog = GL20.glCreateProgram(); GL20.glAttachShader(prog, v); GL20.glAttachShader(prog, f); GL20.glLinkProgram(prog);
        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == 0) { String log = GL20.glGetProgramInfoLog(prog); failed = true; throw new IllegalStateException("sky link: " + log); }
        GL20.glDeleteShader(v); GL20.glDeleteShader(f); vao = GL30.glGenVertexArrays();
        if (tex == 0) { tex = GL11.glGenTextures(); RenderSystem.activeTexture(GL13.GL_TEXTURE0); GlStateManager._bindTexture(tex);   // a black 1 x 1 until the bake is in
            FloatBuffer z = BufferUtils.createFloatBuffer(3); GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGB16F, 1, 1, 0, GL11.GL_RGB, GL11.GL_FLOAT, z); GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST); GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST); GlStateManager._bindTexture(0); }
        if (homeTex == 0) { homeTex = GL11.glGenTextures(); RenderSystem.activeTexture(GL13.GL_TEXTURE1); GlStateManager._bindTexture(homeTex); FloatBuffer z = BufferUtils.createFloatBuffer(3); GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGB16F, 1, 1, 0, GL11.GL_RGB, GL11.GL_FLOAT, z); GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST); GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST); GlStateManager._bindTexture(0); RenderSystem.activeTexture(GL13.GL_TEXTURE0); }
        HopelessInfinity.LOG.info("[Hopeless Infinity] sky: shader built"); }
    static int shader(int type, String src) { int s = GL20.glCreateShader(type); GL20.glShaderSource(s, src); GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == 0) { String log = GL20.glGetShaderInfoLog(s); failed = true; throw new IllegalStateException("sky shader: " + log); } return s; }

    /** The fog in air takes the sky's own colour at the horizon in the way the camera looks (so the far terrain meets the sky). */
    static void fog(net.minecraftforge.client.event.ViewportEvent.ComputeFogColor e) {
        if (!on || model == null || failed || e.getCamera().getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE) return;
        ClientLevel l = Minecraft.getInstance().level; if (l == null) return; float pt = (float) e.getPartialTick(); var a = at(l, pt, e.getCamera().getPosition()); if (a == null) return;
        float yaw = (float) Math.toRadians(e.getCamera().getYRot()); double[] v = {-Math.sin(yaw) * 0.995, 0.0998, Math.cos(yaw) * 0.995};   // ~5.7 deg up, the way the camera faces
        if (a.skyMode == 2) { double[] dk = deckRgb(a.sun[1]); double[] c = SkyShade.display(dk, DECK_EXP, 0); e.setRed((float) c[0]); e.setGreen((float) c[1]); e.setBlue((float) c[2]); fogSets++; return; }   // 0.18.0 the deck's glow
        if (a.onMoon >= 0) { e.setRed(0f); e.setGreen(0f); e.setBlue(0f); fogSets++; return; }   // 0.15.0 no air: no fog colour
        double alt = alt(e.getCamera().getPosition()); double[] sc = SkyShade.scatter(v, a.sun, alt, SkyShade.air(model));
        float rain = Math.min(1f, l.getRainLevel(pt) + 0.5f * l.getThunderLevel(pt)); double[] c = SkyShade.display(new double[]{sc[0] * a.sunVisible, sc[1] * a.sunVisible, sc[2] * a.sunVisible}, SkyShade.exposure(model, a.sun, alt), rain);
        e.setRed((float) c[0]); e.setGreen((float) c[1]); e.setBlue((float) c[2]); fogSets++; }
    public static String probe() { var a = lastAt; return String.format("sky %s: frames %d, fog colours %d, exposure %.3g, sun height %.3f, errors %d%s | %s", on ? "on" : "off", frames, fogSets, lastExposure, lastSunY, errors, errors > 0 ? " (" + lastError + ")" : "", bakeNote)
        + (a == null ? "" : String.format(" | real sky: t %.0f s, star %.2f deg wide at %.4f AU, altitude %.1f deg, visible %.3f, %d bodies (%d disks)%s", a.t, Math.toDegrees(2 * a.sunAngR), a.sunDistKm / com.hopeless.infinity.galaxy.Orbits.AU_KM, Math.toDegrees(a.sunAlt), a.sunVisible, a.bodies.size(), lastDisks, moonNote(a))); }
    static String moonNote(com.hopeless.infinity.sky.Ephemeris.At a) { StringBuilder b = new StringBuilder(); for (var x : a.bodies) if (x.kind() == 3) b.append(String.format(", %s %.2f deg wide, altitude %.1f deg, lit %.2f", x.name(), Math.toDegrees(2 * x.angR()), Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, x.dir()[1])))), (1 + (x.dir()[0] * x.sunDir()[0] + x.dir()[1] * x.sunDir()[1] + x.dir()[2] * x.sunDir()[2]) * -1) / 2)); return b.toString(); }
}
