package com.hopeless.infinity.client;

import com.hopeless.infinity.HopelessInfinity;
import com.hopeless.infinity.planet.AdArt;
import com.hopeless.infinity.planet.Frontage;
import com.hopeless.infinity.world.Screens;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;

/**
 * 0.27.3 THE VIDEO SCREENS' PICTURE: for each screen controller this client has loaded (world/Screens.CLIENT), one quad over its
 * Frontage.SCREEN_W x SCREEN_H panel, 1 cm in front of the blocks, drawn with shaders/adscreen.glsl; the advertisement (planet/AdArt) made
 * once and uploaded as a texture. World-space from the active camera: third person sees the same. On a failure the screens stay dark
 * panels and the error is logged. Probes: draws, frames, the texture size, last error (the client probe line).
 */
public final class ScreenRender {
    private ScreenRender() {}
    public static volatile boolean on = true, failed; public static volatile long draws, frames; public static volatile String lastError = "";
    static int prog, vao, tex, texB;

    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !on || failed || Screens.CLIENT.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance(); if (mc.level == null) return; frames++;
        try {
            if (prog == 0) build();
            Vec3 cam = e.getCamera().getPosition(); double secs = (mc.level.getGameTime() + e.getPartialTick()) / 20.0;
            Matrix4f mvp = new Matrix4f(e.getProjectionMatrix()).mul(e.getPoseStack().last().pose());
            boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            RenderSystem.enableDepthTest(); RenderSystem.depthMask(true); RenderSystem.depthFunc(GL11.GL_LEQUAL); RenderSystem.disableBlend(); RenderSystem.disableCull();
            GlStateManager._glUseProgram(prog); FloatBuffer fb = BufferUtils.createFloatBuffer(16); mvp.get(fb); GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(prog, "u_Mvp"), false, fb);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0); GlStateManager._bindTexture(tex); GL20.glUniform1i(GL20.glGetUniformLocation(prog, "u_Pic"), 0);
            int locInfo = GL20.glGetUniformLocation(prog, "u_Info"), locMode = GL20.glGetUniformLocation(prog, "u_Mode"); int bound = -1;
            for (Screens.ScreenBE be : Screens.CLIENT) {
                if (be.getLevel() != mc.level) continue; Direction f = be.facing(); var p = be.getBlockPos(); boolean bb = be.billboard(); double W = bb ? com.hopeless.infinity.planet.Billboards.FACE_W : Frontage.SCREEN_W, H = bb ? com.hopeless.infinity.planet.Billboards.FACE_H : Frontage.SCREEN_H, o = 0.01;
                if (bound != (bb ? 1 : 0)) { bound = bb ? 1 : 0; GlStateManager._bindTexture(bb ? texB : tex); GL20.glUniform4f(locInfo, (float) (secs % 100000.0), bb ? AdArt.BSLIDES : AdArt.SLIDES, bb ? AdArt.BW : AdArt.W, bb ? AdArt.BH : AdArt.H); GL20.glUniform1f(locMode, bb ? 1f : 0f); }   // 0.28.4 billboards
                // the picture's bottom-left as seen from in front, and its across edge: the controller is the panel's lowest -x (or -z) corner
                double ox, oz; float ux, uz;
                switch (f) { case NORTH -> { ox = p.getX() + W; oz = p.getZ() - o; ux = -1; uz = 0; } case SOUTH -> { ox = p.getX(); oz = p.getZ() + 1 + o; ux = 1; uz = 0; }
                    case WEST -> { ox = p.getX() - o; oz = p.getZ(); ux = 0; uz = 1; } default -> { ox = p.getX() + 1 + o; oz = p.getZ() + W; ux = 0; uz = -1; } }
                double oy = p.getY(); if ((ox - cam.x) * (ox - cam.x) + (oz - cam.z) * (oz - cam.z) > 768.0 * 768.0) continue;   // beyond 768 blocks: not drawn (CHOSEN)
                GL20.glUniform3f(GL20.glGetUniformLocation(prog, "u_Origin"), (float) (ox - cam.x), (float) (oy - cam.y), (float) (oz - cam.z));
                GL20.glUniform3f(GL20.glGetUniformLocation(prog, "u_U"), (float) (ux * W), 0f, (float) (uz * W)); GL20.glUniform3f(GL20.glGetUniformLocation(prog, "u_V"), 0f, (float) H, 0f);
                GlStateManager._glBindVertexArray(vao); GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6); draws++; }
            GlStateManager._glBindVertexArray(0); BufferUploader.invalidate(); GlStateManager._glUseProgram(0);
            if (blend) RenderSystem.enableBlend(); if (cull) RenderSystem.enableCull();
        } catch (Throwable t) { lastError = String.valueOf(t); if (!failed) { failed = true; HopelessInfinity.LOG.error("[Hopeless Infinity] screen shader failed; the screens stay dark", t); } } }

    static void build() {
        String src; try (InputStream in = ScreenRender.class.getResourceAsStream("/assets/hopelessinfinity/shaders/adscreen.glsl")) { src = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
        catch (Exception ex) { throw new IllegalStateException("adscreen.glsl missing", ex); }
        int v = PortalRender.compile(GL20.GL_VERTEX_SHADER, "#version 150\n#define VERTEX\n" + src), f = PortalRender.compile(GL20.GL_FRAGMENT_SHADER, "#version 150\n#define FRAGMENT\n" + src);
        int p = GL20.glCreateProgram(); GL20.glAttachShader(p, v); GL20.glAttachShader(p, f); GL20.glBindAttribLocation(p, 0, "a_uv"); GL20.glLinkProgram(p);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException("screen link: " + GL20.glGetProgramInfoLog(p));
        FloatBuffer q = BufferUtils.createFloatBuffer(12); q.put(new float[]{0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1}).flip();
        vao = GL30.glGenVertexArrays(); int vbo = GL15.glGenBuffers(); GlStateManager._glBindVertexArray(vao); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo); GL15.glBufferData(GL15.GL_ARRAY_BUFFER, q, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0); GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 8, 0L); GlStateManager._glBindVertexArray(0); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        // the advertisement: drawn once, its top row first (the shader reads slide k from the top)
        var im = AdArt.atlas(); int w = im.getWidth(), h = im.getHeight(); ByteBuffer bb = BufferUtils.createByteBuffer(w * h * 4);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) { int c = im.getRGB(x, y); bb.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) (c >>> 24)); } bb.flip();
        tex = GL11.glGenTextures(); RenderSystem.activeTexture(GL13.GL_TEXTURE0); GlStateManager._bindTexture(tex);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4); GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bb);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST); GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE); GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE); GlStateManager._bindTexture(0);
        texB = upload(AdArt.billboardAtlas());   // 0.28.4 the billboards' messages
        prog = p; HopelessInfinity.LOG.info("[Hopeless Infinity] screens: shader built, advertisement {} x {} ({} slides)", w, h, AdArt.SLIDES); }
    static int upload(java.awt.image.BufferedImage im) { int w = im.getWidth(), h = im.getHeight(); ByteBuffer bb = BufferUtils.createByteBuffer(w * h * 4);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) { int c = im.getRGB(x, y); bb.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) (c >>> 24)); } bb.flip();
        int t = GL11.glGenTextures(); RenderSystem.activeTexture(GL13.GL_TEXTURE0); GlStateManager._bindTexture(t); GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4); GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bb);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST); GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE); GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE); GlStateManager._bindTexture(0); return t; }
    public static String probe() { return String.format("screens: %s, %d loaded here (%d ever), %d draws over %d frames%s", failed ? "FAILED (dark)" : prog != 0 ? "shader built" : "not built yet", Screens.CLIENT.size(), Screens.loaded, draws, frames, lastError.isEmpty() ? "" : ", error " + lastError); }
}
