package com.hopeless.infinity.client;

import com.hopeless.infinity.HopelessInfinity;
import com.hopeless.infinity.planet.FarMesh;
import com.hopeless.infinity.planet.PlanetWorld;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
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
 * 0.9.0: the planet at distance on the client (planet/FarMesh; shaders/far.glsl). Drawn right after the sky, with its own far plane (the
 * game's ends at 4 x the view distance) and then the depth cleared, so the real chunks always draw over it: where a chunk is loaded you see
 * the chunk, beyond them (and where one is still loading) the far ground out to the horizon. Built on a worker when the camera has moved a
 * ring's cell or changed height a quarter; the GPU only draws it. Minecraft's own distance fog is pushed out on planet faces (the far
 * ground carries the air's haze instead: Koschmieder's law with a visibility of VIS_M, CHOSEN for clear air, 40-100 km), so the edge of the
 * loaded chunks does not show as a fog wall. Probes: builds, ms, vertices, frames, errors.
 */
public final class FarTerrain {
    private FarTerrain() {}
    public static volatile boolean on = true; public static final double VIS_M = 60000;
    public static volatile long builds, frames, errors, uploads; public static volatile long lastMs; public static volatile String lastError = "";
    static volatile FarMesh ready; static volatile double rx, ry, rz; static volatile boolean busy; static double bx = Double.NaN, by, bz, bf = Double.NaN; static int bb;
    static FarMesh uploaded; static double ux, uy, uz; static int prog, vao, vbo, ibo, count; static boolean failed;
    static final java.util.concurrent.ExecutorService WORKER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "Infinity far terrain"); t.setDaemon(true); t.setPriority(Thread.MIN_PRIORITY + 1); return t; });

    /** Client tick: start a build when the camera has moved enough. */
    public static void tick(ClientLevel l, PlanetWorld w, Vec3 cam) {
        if (!on || w == null || busy) return; int f = com.hopeless.infinity.world.Faces.bodyFace(l.dimension());
        if (f < 0) { SpaceClient.View v = SpaceClient.view(); if (v == null) return; f = v.face(); cam = new Vec3(v.x(), v.camY(), v.z()); if (Double.isNaN(bf) || bf != f || bb != w.body) bx = Double.NaN; }   // 0.12.0 in space: the ground below
        bf = f; bb = w.body;
        double alt = Math.max(10, cam.y - com.hopeless.infinity.planet.Terrain.SEA);
        if (!Double.isNaN(bx) && Math.hypot(cam.x - bx, cam.z - bz) < FarMesh.CELL0 * 2 && Math.abs(Math.log(alt / Math.max(10, by - com.hopeless.infinity.planet.Terrain.SEA))) < 0.25) return;
        busy = true; bx = cam.x; by = cam.y; bz = cam.z; final double x = cam.x, y = cam.y, z = cam.z; final int ff = f;
        WORKER.submit(() -> { try { FarMesh m = FarMesh.build(w, ff, x, z, y); m.face = ff; m.body = w.body; rx = x; ry = y; rz = z; ready = m; builds++; lastMs = m.ms; }
            catch (Throwable t) { errors++; lastError = String.valueOf(t); if (errors < 3) HopelessInfinity.LOG.error("[Hopeless Infinity] far terrain build failed", t); } finally { busy = false; } });
    }

    /** After the sky: draw the far ground with a long far plane, then clear the depth for the chunks. */
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || !on || failed) return;
        ClientLevel l = Minecraft.getInstance().level; if (l == null) return; SpaceClient.View sv = SpaceClient.view(); int body = com.hopeless.infinity.world.Faces.body(l.dimension()); if (body < 0 && sv == null) return; PlanetWorld bwv = ClientHooks.bodyWorld(); boolean airless = sv != null ? sv.body() >= 1 : bwv != null && bwv.body >= 1 && com.hopeless.infinity.sky.Ephemeris.skyMode(bwv) == 1;   // 0.18.0 by the body's air
        if (sv != null) try { globe(e, sv); } catch (Throwable t) { errors++; lastError = String.valueOf(t); if (errors < 5) HopelessInfinity.LOG.error("[Hopeless Infinity] globe draw failed", t); }   // 0.14.0 the whole planet behind the near ground
        FarMesh m = ready; if (m == null || (sv != null && (m.face != sv.face() || m.body != sv.body())) || (sv == null && (m.body != body || m.face != com.hopeless.infinity.world.Faces.bodyFace(l.dimension())))) return;
        try {
            if (prog == 0) build();
            if (m != uploaded) upload(m);
            Vec3 c = sv != null ? new Vec3(sv.x(), sv.camY(), sv.z()) : e.getCamera().getPosition();   // 0.12.0 in space: the eye is the real place over the ground
            Matrix4f proj = new Matrix4f(e.getProjectionMatrix()); float n = 8f, fa = 3000000f; proj.m22(-(fa + n) / (fa - n)); proj.m32(-2 * fa * n / (fa - n));   // the same view, a far plane past the horizon
            Matrix4f mv = new Matrix4f(e.getPoseStack().last().pose()).translate((float) (ux - c.x), (float) (uy - c.y), (float) (uz - c.z));
            Matrix4f mvp = new Matrix4f(proj).mul(mv);
            boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            RenderSystem.enableDepthTest(); RenderSystem.depthMask(true); RenderSystem.depthFunc(GL11.GL_LEQUAL); RenderSystem.disableBlend(); RenderSystem.disableCull();
            GlStateManager._glUseProgram(prog);
            FloatBuffer fb = BufferUtils.createFloatBuffer(16); mvp.get(fb); GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(prog, "u_Mvp"), false, fb);
            var at = SkyRenderer.lastAt; double[] s = at == null ? new double[]{0.3, 0.8, 0.2} : at.sun; float day = sv != null || airless ? 1f : l.getSkyDarken(e.getPartialTick());
            GL20.glUniform4f(GL20.glGetUniformLocation(prog, "u_Sun"), (float) s[0], (float) s[1], (float) s[2], Math.max(0f, Math.min(1f, (day - 0.2f) / 0.8f)));
            GL20.glUniform4f(GL20.glGetUniformLocation(prog, "u_Air"), (float) Math.max(0, c.y - com.hopeless.infinity.planet.Terrain.SEA), (float) (1200 * 9.81 / ClientHooks.world.planet.gravity), sv != null || airless ? 1f : 0f, 0f);   // Mie scale height x 9.81/g, as the sky; z: in space or on an airless body (no sky light at night)
            float[] fc = RenderSystem.getShaderFogColor(); GL20.glUniform4f(GL20.glGetUniformLocation(prog, "u_Fog"), fc[0], fc[1], fc[2], airless ? 1e12f : (float) (0.6931 * VIS_M / 3.912));   // half-visibility distance: ln2 / (3.912 / V); no air, no haze
            GlStateManager._glBindVertexArray(vao); GL11.glDrawElements(GL11.GL_TRIANGLES, count, GL11.GL_UNSIGNED_INT, 0L);
            GlStateManager._glBindVertexArray(0); BufferUploader.invalidate(); GlStateManager._glUseProgram(0);
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);   // the chunks draw over the far ground
            if (!depth) RenderSystem.disableDepthTest(); if (blend) RenderSystem.enableBlend(); if (cull) RenderSystem.enableCull();
            frames++;
        } catch (Throwable t) { errors++; lastError = String.valueOf(t); if (errors < 5) HopelessInfinity.LOG.error("[Hopeless Infinity] far terrain draw failed", t); failed = errors > 20; }
    }

    // ---------------- 0.14.0 the globe ----------------
    public static final double GLOBE_ALT = 150000;   // m: over this the whole planet is drawn behind the near ground (FarMesh's 2,100 km no longer reaches the horizon past ~1.5 x)
    /** 0.16.0 one globe per body (0 the planet, 1 its moon), each built once per seed. */
    static final java.util.Map<Integer, com.hopeless.infinity.planet.GlobeMesh> globes = new java.util.concurrent.ConcurrentHashMap<>(); static final java.util.Map<Integer, Long> globeSeeds = new java.util.concurrent.ConcurrentHashMap<>();   // 0.18.0 one per body
    static volatile com.hopeless.infinity.planet.GlobeMesh globe; static volatile boolean globeBusy; static long globeSeed = Long.MIN_VALUE; static int gprog, gvao, gvbo, gibo, gcount; static com.hopeless.infinity.planet.GlobeMesh gUploaded;
    public static volatile long globeFrames, globeBuildMs;
    static void globe(RenderLevelStageEvent e, SpaceClient.View sv) {
        PlanetWorld w = ClientHooks.bodyWorld(); if (w == null) return; double alt = sv.camY() - com.hopeless.infinity.planet.Terrain.SEA; final int bi = w.body;
        if (globeSeeds.getOrDefault(bi, Long.MIN_VALUE) != w.seed && !globeBusy) { globeBusy = true; globeSeeds.put(bi, w.seed); WORKER.submit(() -> { try { var g = com.hopeless.infinity.planet.GlobeMesh.build(w); globes.put(bi, g); globeBuildMs = g.ms; HopelessInfinity.LOG.info("[Hopeless Infinity] globe of the {} built: {} vertices in {} ms", bi == 0 ? "home planet" : "body " + bi, g.verts, g.ms); } catch (Throwable t) { errors++; lastError = String.valueOf(t); } finally { globeBusy = false; } }); }
        var g = globes.get(bi); globe = g; if (g == null || alt < GLOBE_ALT) return;
        if (gprog == 0) { String src; try (InputStream in = FarTerrain.class.getResourceAsStream("/assets/hopelessinfinity/shaders/globe.glsl")) { src = new String(in.readAllBytes(), StandardCharsets.UTF_8); } catch (Exception ex) { throw new IllegalStateException("globe.glsl missing", ex); }
            int v = SkyRenderer.shader(GL20.GL_VERTEX_SHADER, "#version 150\n#define VERTEX\n" + src), f = SkyRenderer.shader(GL20.GL_FRAGMENT_SHADER, "#version 150\n#define FRAGMENT\n" + src);
            gprog = GL20.glCreateProgram(); GL20.glAttachShader(gprog, v); GL20.glAttachShader(gprog, f); GL20.glBindAttribLocation(gprog, 0, "a_pos"); GL20.glBindAttribLocation(gprog, 1, "a_nrm"); GL20.glBindAttribLocation(gprog, 2, "a_col"); GL20.glLinkProgram(gprog);
            if (GL20.glGetProgrami(gprog, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException("globe link: " + GL20.glGetProgramInfoLog(gprog)); GL20.glDeleteShader(v); GL20.glDeleteShader(f); }
        if (g != gUploaded) { if (gvao == 0) { gvao = GL30.glGenVertexArrays(); gvbo = GL15.glGenBuffers(); gibo = GL15.glGenBuffers(); }
            FloatBuffer vb = BufferUtils.createFloatBuffer(g.verts * 9); for (int i = 0; i < g.verts; i++) vb.put(g.pos, i * 3, 3).put(g.nrm, i * 3, 3).put(g.col, i * 3, 3); vb.flip(); IntBuffer ib = BufferUtils.createIntBuffer(g.idx.length); ib.put(g.idx).flip();
            GlStateManager._glBindVertexArray(gvao); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, gvbo); GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vb, GL15.GL_STATIC_DRAW); GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, gibo); GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, ib, GL15.GL_STATIC_DRAW);
            for (int a = 0; a < 3; a++) { GL20.glEnableVertexAttribArray(a); GL20.glVertexAttribPointer(a, 3, GL11.GL_FLOAT, false, 36, a * 12L); } GlStateManager._glBindVertexArray(0); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0); gUploaded = g; gcount = g.idx.length; }
        double[][] fr = w.cube.frame(sv.face(), sv.x(), sv.z()); double R = w.cube.R, camR = R + alt;
        Matrix4f model = new Matrix4f(); // planet space -> the camera's block frame (rows ex, up, ez), the camera at the origin
        model.m00((float) fr[1][0]); model.m10((float) fr[1][1]); model.m20((float) fr[1][2]); model.m01((float) fr[0][0]); model.m11((float) fr[0][1]); model.m21((float) fr[0][2]);
        model.m02((float) fr[2][0]); model.m12((float) fr[2][1]); model.m22((float) fr[2][2]); model.m30(0); model.m31((float) -camR); model.m32(0);
        Matrix4f proj = new Matrix4f(e.getProjectionMatrix()); float n = (float) Math.max(100, alt * 0.25), fa = (float) (alt + 2.2 * R); proj.m22(-(fa + n) / (fa - n)); proj.m32(-2 * fa * n / (fa - n));
        Matrix4f mvp = new Matrix4f(proj).mul(new Matrix4f(e.getPoseStack().last().pose()));
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(true); RenderSystem.depthFunc(GL11.GL_LEQUAL); RenderSystem.disableBlend(); RenderSystem.disableCull();
        GlStateManager._glUseProgram(gprog); FloatBuffer fb = BufferUtils.createFloatBuffer(16);
        mvp.get(fb); GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(gprog, "u_Mvp"), false, fb); fb.clear(); model.get(fb); GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(gprog, "u_Model"), false, fb);
        var at = SkyRenderer.lastAt; double[] s = at == null ? new double[]{0.3, 0.8, 0.2} : at.sun;
        GL20.glUniform4f(GL20.glGetUniformLocation(gprog, "u_Sun"), (float) s[0], (float) s[1], (float) s[2], 1f);
        GL20.glUniform4f(GL20.glGetUniformLocation(gprog, "u_Air"), (float) alt, (float) (1200 * 9.81 / w.gravity), 1f, 0f);
        float[] fc = RenderSystem.getShaderFogColor(); GL20.glUniform4f(GL20.glGetUniformLocation(gprog, "u_Fog"), fc[0], fc[1], fc[2], bi >= 1 ? 1e30f : (float) (0.6931 * VIS_M / 3.912));   // 0.16.0 the moon: no air, no haze
        GlStateManager._glBindVertexArray(gvao); GL11.glDrawElements(GL11.GL_TRIANGLES, gcount, GL11.GL_UNSIGNED_INT, 0L); GlStateManager._glBindVertexArray(0); BufferUploader.invalidate(); GlStateManager._glUseProgram(0);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);   // the near ground draws over it
        if (!depth) RenderSystem.disableDepthTest(); if (blend) RenderSystem.enableBlend(); if (cull) RenderSystem.enableCull(); globeFrames++; }
    /** On planet faces Minecraft's distance fog would hide the loaded chunks' edge in a wall of fog: pushed out to the far plane (the far ground carries the haze). */
    public static void fog(ViewportEvent.RenderFog e) {
        if (!on || e.getType() != net.minecraft.world.level.material.FogType.NONE) return; ClientLevel l = Minecraft.getInstance().level;
        if (l != null && SpaceClient.inSpace()) { e.setNearPlaneDistance(1e7f); e.setFarPlaneDistance(2e7f); e.setCanceled(true); return; }   // 0.12.0 no fog in space
        if (l == null || com.hopeless.infinity.world.Faces.body(l.dimension()) < 0 || e.getMode() != net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN) return;
        if (ready == null) return; double d = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0; float start = (float) (d * 4), end = (float) (d * 8);   // past the chunks: their own fog never shows
        e.setNearPlaneDistance(start); e.setFarPlaneDistance(end); e.setCanceled(true);
    }
    static void upload(FarMesh m) {
        if (vao == 0) { vao = GL30.glGenVertexArrays(); vbo = GL15.glGenBuffers(); ibo = GL15.glGenBuffers(); }
        FloatBuffer v = BufferUtils.createFloatBuffer(m.verts * 9); for (int i = 0; i < m.verts; i++) { v.put(m.pos, i * 3, 3).put(m.nrm, i * 3, 3).put(m.col, i * 3, 3); } v.flip();
        IntBuffer ib = BufferUtils.createIntBuffer(m.idx.length); ib.put(m.idx).flip();
        GlStateManager._glBindVertexArray(vao); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo); GL15.glBufferData(GL15.GL_ARRAY_BUFFER, v, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo); GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, ib, GL15.GL_STATIC_DRAW);
        for (int a = 0; a < 3; a++) { GL20.glEnableVertexAttribArray(a); GL20.glVertexAttribPointer(a, 3, GL11.GL_FLOAT, false, 36, a * 12L); }
        GlStateManager._glBindVertexArray(0); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        uploaded = m; count = m.idx.length; ux = rx; uy = ry; uz = rz; uploads++; }
    static void build() {
        String src; try (InputStream in = FarTerrain.class.getResourceAsStream("/assets/hopelessinfinity/shaders/far.glsl")) { src = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
        catch (Exception ex) { failed = true; throw new IllegalStateException("far.glsl missing", ex); }
        int v = SkyRenderer.shader(GL20.GL_VERTEX_SHADER, "#version 150\n#define VERTEX\n" + src), f = SkyRenderer.shader(GL20.GL_FRAGMENT_SHADER, "#version 150\n#define FRAGMENT\n" + src);
        prog = GL20.glCreateProgram(); GL20.glAttachShader(prog, v); GL20.glAttachShader(prog, f);
        GL20.glBindAttribLocation(prog, 0, "a_pos"); GL20.glBindAttribLocation(prog, 1, "a_nrm"); GL20.glBindAttribLocation(prog, 2, "a_col"); GL20.glLinkProgram(prog);
        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == 0) { failed = true; throw new IllegalStateException("far link: " + GL20.glGetProgramInfoLog(prog)); }
        GL20.glDeleteShader(v); GL20.glDeleteShader(f); HopelessInfinity.LOG.info("[Hopeless Infinity] far terrain: shader built"); }
    public static String probe() { FarMesh m = ready; return String.format("far terrain %s: builds %d (last %d ms), uploads %d, frames %d, globe frames %d (built in %d ms), errors %d%s%s", on ? "on" : "off", builds, lastMs, uploads, frames, globeFrames, globeBuildMs, errors,
        m == null ? "" : String.format(", %d vertices %d triangles to %.0f km", m.verts, m.tris, m.reachKm), errors > 0 ? " (" + lastError + ")" : ""); }
}
