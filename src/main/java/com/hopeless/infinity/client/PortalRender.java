package com.hopeless.infinity.client;

import com.hopeless.infinity.HopelessInfinity;
import com.hopeless.infinity.world.Gates;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

/**
 * 0.27.2 THE GATE PORTALS: for each open gate controller this client has loaded (world/Gates.CLIENT, synced by the server), one grid across
 * the gate's aperture drawn with shaders/portal.glsl after the translucent blocks. The CPU sends the grid once and a few uniforms a frame;
 * the surface's motion and light are all in the shader (owner: "it should only be on the gpu"). World-space drawing from the active camera,
 * so third person sees the same. If the shader cannot be made, the field blocks draw their old pane again (Gates.fieldModel) and the error is
 * logged. Probes: draws, programs built, last error (in the client probe line).
 */
public final class PortalRender {
    private PortalRender() {}
    public static volatile boolean on = true, failed; public static volatile long draws, frames; public static volatile String lastError = "";
    static int prog, vaoSmall, vaoGreat, nSmall, nGreat;
    static final int GRID_SMALL = 48, GRID_GREAT = 128;   // CHOSEN: about 6 cm a cell across a 3-block field, 12 cm across the great gate's 15

    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !on || failed || Gates.CLIENT.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance(); if (mc.level == null) return; frames++;
        try {
            if (prog == 0) build();
            Vec3 cam = e.getCamera().getPosition(); float pt = e.getPartialTick(); long now = mc.level.getGameTime();
            Matrix4f mvp = new Matrix4f(e.getProjectionMatrix()).mul(e.getPoseStack().last().pose());
            boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.depthFunc(GL11.GL_LEQUAL); RenderSystem.enableBlend(); RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA); RenderSystem.disableCull();
            GlStateManager._glUseProgram(prog); FloatBuffer fb = BufferUtils.createFloatBuffer(16); mvp.get(fb); GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(prog, "u_Mvp"), false, fb);
            float[] fc = RenderSystem.getShaderFogColor(); float day = mc.level.getSkyDarken(pt); GL20.glUniform4f(GL20.glGetUniformLocation(prog, "u_Sky"), fc[0], fc[1], fc[2], Math.max(0f, Math.min(1f, 1f - day / 11f)));
            for (Gates.ControllerBE be : Gates.CLIENT) {
                if (be.getLevel() != mc.level || be.origin == null || be.axis < 0) continue;
                double since = be.open() ? (now - be.openedAt + pt) / 20.0 : -1, closing = be.open() ? -1 : (now - be.closedAt + pt) / 20.0; if (!be.open() && (be.closedAt == Long.MIN_VALUE || closing > 0.6)) continue;
                if (!be.open()) since = Math.max(0, (be.closedAt - be.openedAt) / 20.0 + closing);
                double cx, cy, cz, R; float shape; float[] U, V, N; int vao, n;
                if (be.axis == Gates.GREAT) { cx = be.origin.getX() + com.hopeless.infinity.planet.GreatGate.FIELD_X; cy = be.origin.getY() + com.hopeless.infinity.planet.GreatGate.FIELD_Y; cz = be.origin.getZ() + com.hopeless.infinity.planet.GreatGate.FIELD_Z;
                    R = com.hopeless.infinity.planet.GreatGate.RI; shape = 1f; U = new float[]{0, 0, 1}; V = new float[]{0, 1, 0}; N = new float[]{1, 0, 0}; vao = vaoGreat; n = nGreat; }
                else { cx = be.origin.getX() + 0.5; cy = be.origin.getY() + 2.5; cz = be.origin.getZ() + 0.5; R = 1.5; shape = 0f; vao = vaoSmall; n = nSmall;   // the 3 x 3 field over the bottom-middle cell
                    if (be.axis == 0) { U = new float[]{1, 0, 0}; N = new float[]{0, 0, 1}; } else { U = new float[]{0, 0, 1}; N = new float[]{1, 0, 0}; } V = new float[]{0, 1, 0}; }
                if ((cx - cam.x) * (cx - cam.x) + (cy - cam.y) * (cy - cam.y) + (cz - cam.z) * (cz - cam.z) > 512.0 * 512.0) continue;   // beyond 512 blocks: not drawn (CHOSEN: past the usual view distance)
                GL20.glUniform3f(GL20.glGetUniformLocation(prog, "u_Centre"), (float) (cx - cam.x), (float) (cy - cam.y), (float) (cz - cam.z));
                GL20.glUniform3f(GL20.glGetUniformLocation(prog, "u_U"), U[0], U[1], U[2]); GL20.glUniform3f(GL20.glGetUniformLocation(prog, "u_V"), V[0], V[1], V[2]); GL20.glUniform3f(GL20.glGetUniformLocation(prog, "u_N"), N[0], N[1], N[2]);
                GL20.glUniform4f(GL20.glGetUniformLocation(prog, "u_Size"), (float) R, shape, (float) since, (float) closing);
                GlStateManager._glBindVertexArray(vao); GL11.glDrawElements(GL11.GL_TRIANGLES, n, GL11.GL_UNSIGNED_INT, 0L); draws++; }
            GlStateManager._glBindVertexArray(0); BufferUploader.invalidate(); GlStateManager._glUseProgram(0);
            RenderSystem.depthMask(true); if (!blend) RenderSystem.disableBlend(); else RenderSystem.defaultBlendFunc(); if (cull) RenderSystem.enableCull(); if (!depth) RenderSystem.disableDepthTest();
        } catch (Throwable t) { lastError = String.valueOf(t); fail(t); } }

    static void fail(Throwable t) { if (failed) return; failed = true; Gates.fieldModel = true; HopelessInfinity.LOG.error("[Hopeless Infinity] portal shader failed; the gate fields draw their old pane", t);
        try { Minecraft.getInstance().levelRenderer.allChanged(); } catch (Throwable ignored) { } }

    static void build() {
        String src; try (InputStream in = PortalRender.class.getResourceAsStream("/assets/hopelessinfinity/shaders/portal.glsl")) { src = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
        catch (Exception ex) { throw new IllegalStateException("portal.glsl missing", ex); }
        int v = compile(GL20.GL_VERTEX_SHADER, "#version 150\n#define VERTEX\n" + src), f = compile(GL20.GL_FRAGMENT_SHADER, "#version 150\n#define FRAGMENT\n" + src);
        int p = GL20.glCreateProgram(); GL20.glAttachShader(p, v); GL20.glAttachShader(p, f); GL20.glBindAttribLocation(p, 0, "a_ab"); GL20.glLinkProgram(p);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException("portal link: " + GL20.glGetProgramInfoLog(p));
        int[] a = grid(GRID_SMALL); vaoSmall = a[0]; nSmall = a[1]; a = grid(GRID_GREAT); vaoGreat = a[0]; nGreat = a[1]; prog = p;
        HopelessInfinity.LOG.info("[Hopeless Infinity] portal: shader built (grids {} and {} cells a side)", GRID_SMALL, GRID_GREAT); }
    static int compile(int type, String src) { int s = GL20.glCreateShader(type); GL20.glShaderSource(s, src); GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == 0) throw new IllegalStateException("portal shader: " + GL20.glGetShaderInfoLog(s)); return s; }
    /** A grid of (n + 1)^2 points over -1..1, as triangles: {vao, index count}. */
    static int[] grid(int n) {
        FloatBuffer vb = BufferUtils.createFloatBuffer((n + 1) * (n + 1) * 2); for (int j = 0; j <= n; j++) for (int i = 0; i <= n; i++) { vb.put(-1f + 2f * i / n); vb.put(-1f + 2f * j / n); } vb.flip();
        IntBuffer ib = BufferUtils.createIntBuffer(n * n * 6); for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) { int a = j * (n + 1) + i, b = a + 1, c = a + n + 1, d = c + 1; ib.put(a).put(b).put(c).put(b).put(d).put(c); } ib.flip();
        int vao = GL30.glGenVertexArrays(), vbo = GL15.glGenBuffers(), ibo = GL15.glGenBuffers(); GlStateManager._glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo); GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vb, GL15.GL_STATIC_DRAW); GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo); GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, ib, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0); GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 8, 0L); GlStateManager._glBindVertexArray(0); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return new int[]{vao, n * n * 6}; }
    public static String probe() { return String.format("portal: %s, %d gates loaded here, %d draws over %d frames%s", failed ? "FAILED (old pane)" : prog != 0 ? "shader built" : "not built yet", Gates.CLIENT.size(), draws, frames, lastError.isEmpty() ? "" : ", error " + lastError); }
}
