package com.hopeless.infinity.client;

import com.hopeless.infinity.HopelessInfinity;
import com.hopeless.infinity.Net;
import com.hopeless.infinity.vehicle.Spaceplane;
import com.hopeless.infinity.vehicle.Vehicles;
import com.hopeless.infinity.vehicle.X15;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 0.13.0 the X-15 on the client: its renderer (0.13.1: vehicle/X15Mesh, the same quads and vertex normals tools/x15_view.py draws; plain colours on a white
 * texture, lit by the world's light, seen from outside and in third person), the throttle keys, the engine's exhaust, the readout.
 * Probes: frames drawn, throttle sends, errors.
 */
public final class PlaneClient {
    private PlaneClient() {}
    static final ResourceLocation TEX = new ResourceLocation(HopelessInfinity.MODID, "textures/entity/x15.png");
        static float[] mp, mn; static int[] mc;   // the mesh (vehicle/X15Mesh) as flat arrays: 4 corners a quad
    public static volatile long frames, sends, errors; static float sentThrottle = -1;

    public static void init(IEventBus bus) {
        bus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerEntityRenderer(Vehicles.X15_TYPE.get(), Renderer::new));
        bus.addListener((RegisterGuiOverlaysEvent e) -> e.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "x15", (gui, g, pt, w, h) -> {
            LocalPlayer p = Minecraft.getInstance().player; if (p == null || Minecraft.getInstance().options.hideGui || !(p.getVehicle() instanceof Spaceplane sp)) return;
            double[] f = sp.lastForces; boolean inSpace = SpaceClient.inSpace(); double speed = inSpace && SpaceClient.cur != null ? Math.sqrt(SpaceClient.cur.vx() * SpaceClient.cur.vx() + SpaceClient.cur.vy() * SpaceClient.cur.vy() + SpaceClient.cur.vz() * SpaceClient.cur.vz()) : Math.sqrt(sp.vel[0] * sp.vel[0] + sp.vel[1] * sp.vel[1] + sp.vel[2] * sp.vel[2]);
            double alt = inSpace && SpaceClient.cur != null ? SpaceClient.cur.alt() : sp.getY();
            String t = String.format("X-15  %.0f m/s%s  height %.2f km  throttle %.0f%%  propellant %.0f kg (%.0f s)  hull %.0f%%%s", speed, inSpace ? "" : String.format(" (Mach %.2f)", f[0]), alt / 1000, sp.throttle() * 100, sp.fuel(), sp.fuel() / Math.max(1e-6, X15.MDOT), sp.hull(), sp.heatNow() > 0.5 ? String.format("  HEAT %.0f%%", sp.heatNow() * 100) : "");
            g.drawString(Minecraft.getInstance().font, t, w / 2 - 91, h - 69, sp.heatNow() > 1 || sp.hull() < 30 ? 0xFF5555 : 0xFFE0A0); }));
        MinecraftForge.EVENT_BUS.addListener(PlaneClient::tick);
    }
    /** A plane put back into the world: its real velocity (the spawn packet caps it). */
    public static void init(int id, double vx, double vy, double vz) { var l = Minecraft.getInstance().level; if (l != null && l.getEntity(id) instanceof Spaceplane sp) { sp.vel[0] = vx; sp.vel[1] = vy; sp.vel[2] = vz; } else pending = new double[]{id, vx, vy, vz}; }
    static double[] pending;

    static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return; Minecraft mc = Minecraft.getInstance(); LocalPlayer p = mc.player; if (p == null || mc.isPaused()) return;
        try { if (pending != null && mc.level != null && mc.level.getEntity((int) pending[0]) instanceof Spaceplane sp) { sp.vel[0] = pending[1]; sp.vel[1] = pending[2]; sp.vel[2] = pending[3]; pending = null; }
            if (!(p.getVehicle() instanceof Spaceplane sp)) { sentThrottle = -1; return; }
            float t = sentThrottle < 0 ? sp.throttle() : sentThrottle; float fw = mc.screen == null ? p.input.forwardImpulse : 0;
            if (fw > 0.1f) t = t <= 0 ? (float) X15.MIN_THROTTLE : Math.min(1f, t + 0.025f); else if (fw < -0.1f) t = t <= X15.MIN_THROTTLE + 1e-3 ? 0f : Math.max((float) X15.MIN_THROTTLE, t - 0.025f);   // 0.5 a second; below the minimum: off
            if (Math.abs(t - sentThrottle) > 1e-4) { Net.CH.sendToServer(new Net.PlaneCtl(t, 0, 0)); sentThrottle = t; sends++; }
            // the exhaust: from the nozzle, back along the nose, when burning
            if (sp.throttle() > 0 && sp.fuel() > 0) { double[] n = Spaceplane.nose(sp.getYRot(), sp.getXRot()); double bx = sp.getX() - n[0] * 8.0, by = sp.getY() + X15.BASE_UP - n[1] * 8.0, bz = sp.getZ() - n[2] * 8.0;
                for (int i = 0; i < 2; i++) mc.level.addParticle(ParticleTypes.FLAME, bx, by, bz, -n[0] * 0.8 + (p.getRandom().nextDouble() - 0.5) * 0.05, -n[1] * 0.8, -n[2] * 0.8 + (p.getRandom().nextDouble() - 0.5) * 0.05);
                mc.level.addParticle(ParticleTypes.CLOUD, bx - n[0], by - n[1], bz - n[2], -n[0] * 0.3, -n[1] * 0.3, -n[2] * 0.3); }
        } catch (Throwable t) { errors++; if (errors < 3) HopelessInfinity.LOG.error("[Hopeless Infinity] X-15 (client) failed", t); } }

    public static final class Renderer extends EntityRenderer<Spaceplane> {
        public Renderer(EntityRendererProvider.Context c) { super(c); shadowRadius = 2f; }
        @Override public ResourceLocation getTextureLocation(Spaceplane e) { return TEX; }
        @Override public boolean shouldRender(Spaceplane e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) { return true; }   // 15 m long: the 3 m hitbox would cull it early
        @Override public void render(Spaceplane e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
            try { if (mc == null) mesh();
                ps.pushPose(); ps.translate(0, X15.BASE_UP, 0); ps.mulPose(Axis.YP.rotationDegrees(-e.getViewYRot(pt))); ps.mulPose(Axis.XP.rotationDegrees(e.getViewXRot(pt)));
                Matrix4f m = ps.last().pose(); Matrix3f nm = ps.last().normal(); VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
                for (int q = 0, nq = mc.length; q < nq; q++) { int rgb = mc[q]; float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
                    for (int k = 0; k < 4; k++) { int i = (q * 4 + k) * 3; vc.vertex(m, mp[i], mp[i + 1], mp[i + 2]).color(r, g, b, 1f).uv(0.5f, 0.5f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nm, mn[i], mn[i + 1], mn[i + 2]).endVertex(); } }
                ps.popPose(); frames++;
            } catch (Throwable t) { errors++; if (errors < 3) HopelessInfinity.LOG.error("[Hopeless Infinity] X-15 draw failed", t); }
            super.render(e, yaw, pt, ps, buf, light); }
    }
    static void mesh() { var qs = com.hopeless.infinity.vehicle.X15Mesh.build(); float[] p = new float[qs.size() * 12], n = new float[qs.size() * 12]; int[] c = new int[qs.size()];
        for (int q = 0; q < qs.size(); q++) { var x = qs.get(q); c[q] = x.rgb(); for (int k = 0; k < 4; k++) for (int j = 0; j < 3; j++) { p[q * 12 + k * 3 + j] = (float) x.p()[k][j]; n[q * 12 + k * 3 + j] = (float) x.n()[k][j]; } }
        mp = p; mn = n; mc = c; HopelessInfinity.LOG.info("[Hopeless Infinity] X-15 model: {} quads", c.length); }
    public static String probe() { return String.format("x-15 (client): frames %d, throttle sends %d, errors %d", frames, sends, errors); }
}
