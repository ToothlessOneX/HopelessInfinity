package com.hopeless.infinity.client;

import com.hopeless.infinity.HopelessInfinity;
import com.hopeless.infinity.Net;
import com.hopeless.infinity.world.SuitSpec;
import com.hopeless.infinity.world.Suits;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.EnumMap;
import java.util.Map;

/**
 * 0.11.0 the spacesuits on the client: the rocket belt's push (hold jump in the air; SuitSpec.thrustPerTick, leaning SuitSpec.TILT into the
 * movement keys), its steam (the real belt's exhaust: steam and oxygen from the peroxide), the suit readout over the hotbar, and the
 * backpack drawn on the player's back (a render layer on the player model: seen in third person and by other players). Backpack sizes:
 * PACK, from the reference photos against the wearer's torso (the body is 8 x 12 x 4 model pixels; 1 pixel = 5.6 cm on a 1.8 m player).
 * Probes: thrust ticks, packets sent, layers drawn, errors.
 */
public final class SuitClient {
    private SuitClient() {}
    /** w, h, d of the backpack (model pixels), and whether an Apollo Oxygen Purge System sits on top. */
    public static final Map<SuitSpec.Kind, int[]> PACK = new EnumMap<>(Map.of(SuitSpec.Kind.EMU, new int[]{9, 12, 4, 0}, SuitSpec.Kind.XEMU, new int[]{10, 13, 5, 0},
        SuitSpec.Kind.A7L, new int[]{8, 11, 4, 1}, SuitSpec.Kind.ORLAN, new int[]{9, 13, 5, 0}));
    static final Map<SuitSpec.Kind, ModelLayerLocation> LAYERS = new EnumMap<>(SuitSpec.Kind.class);
    static { for (SuitSpec.Kind k : SuitSpec.Kind.values()) LAYERS.put(k, new ModelLayerLocation(new ResourceLocation(HopelessInfinity.MODID, "pack_" + k.id), "main")); }
    public static volatile long thrustTicks, sent, drawn, errors; static boolean wasBurning;

    public static void init(IEventBus bus) {
        bus.addListener((EntityRenderersEvent.RegisterLayerDefinitions e) -> { for (SuitSpec.Kind k : SuitSpec.Kind.values()) e.registerLayerDefinition(LAYERS.get(k), () -> pack(k)); });
        bus.addListener((EntityRenderersEvent.AddLayers e) -> { for (String s : e.getSkins()) { if (e.getSkin(s) instanceof PlayerRenderer r) r.addLayer(new PackLayer(r, e)); } });
        bus.addListener((RegisterGuiOverlaysEvent e) -> e.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "suit", (gui, g, pt, w, h) -> {
            LocalPlayer p = Minecraft.getInstance().player; if (p == null || Minecraft.getInstance().options.hideGui) return; SuitSpec.Kind k = Suits.fullSet(p); if (k == null) return;
            ItemStack c = p.getItemBySlot(EquipmentSlot.CHEST); int o2 = Suits.o2(c), b = Suits.belt(c);
            String t = String.format("%s  O2 %d:%02d:%02d  belt %.1f s", k.shortName, o2 / 3600, (o2 / 60) % 60, o2 % 60, b / 20.0);
            g.drawString(Minecraft.getInstance().font, t, w / 2 - 91, h - 49, o2 < 600 ? 0xFF5555 : 0xE0F0FF); }));
        MinecraftForge.EVENT_BUS.addListener(SuitClient::tick);
    }

    /** The backpack model: the pack, the Apollo purge system on top (A7L), and the belt's two nozzles at the waist. */
    static LayerDefinition pack(SuitSpec.Kind k) {
        int[] d = PACK.get(k); MeshDefinition m = new MeshDefinition();
        CubeListBuilder c = CubeListBuilder.create().texOffs(0, 0).addBox(-d[0] / 2f, -0.5f, 3f, d[0], d[1], d[2]);
        if (d[3] == 1) c.texOffs(32, 0).addBox(-d[0] / 2f, -3.5f, 3f, d[0], 3, d[2]);
        c.texOffs(32, 12).addBox(-d[0] / 2f - 2f, d[1] - 4.5f, 3f + d[2] / 2f - 1f, 2, 3, 2).texOffs(32, 12).addBox(d[0] / 2f, d[1] - 4.5f, 3f + d[2] / 2f - 1f, 2, 3, 2);
        m.getRoot().addOrReplaceChild("pack", c, PartPose.ZERO); return LayerDefinition.create(m, 64, 32); }

    static final class PackLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        final Map<SuitSpec.Kind, ModelPart> parts = new EnumMap<>(SuitSpec.Kind.class);
        PackLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> r, EntityRenderersEvent.AddLayers e) { super(r);
            for (SuitSpec.Kind k : SuitSpec.Kind.values()) parts.put(k, e.getEntityModels().bakeLayer(LAYERS.get(k))); }
        @Override public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float ls, float la, float pt, float age, float yaw, float pitch) {
            try { if (!(p.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof Suits.SuitItem si) || p.isInvisible()) return;
                ps.pushPose(); getParentModel().body.translateAndRotate(ps);
                parts.get(si.kind).render(ps, buf.getBuffer(RenderType.entityCutoutNoCull(new ResourceLocation(HopelessInfinity.MODID, "textures/models/pack_" + si.kind.id + ".png"))), light, OverlayTexture.NO_OVERLAY);
                ps.popPose(); drawn++; } catch (Throwable t) { errors++; if (errors < 3) HopelessInfinity.LOG.error("[Hopeless Infinity] backpack draw failed", t); } }
    }

    /** Client tick: the belt. */
    static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return; Minecraft mc = Minecraft.getInstance(); LocalPlayer p = mc.player; if (p == null || mc.isPaused()) return;
        try { SuitSpec.Kind k = Suits.fullSet(p); ItemStack c = p.getItemBySlot(EquipmentSlot.CHEST);
            boolean burn = k != null && !SpaceClient.inSpace() && !p.onGround() && !p.isInWater() && !p.getAbilities().flying && !p.isPassenger() && mc.options.keyJump.isDown() && Suits.belt(c) > 0 && mc.screen == null;
            if (burn != wasBurning) { Net.CH.sendToServer(new Net.BeltBurn(burn)); wasBurning = burn; sent++; }
            if (!burn) return;
            double a = SuitSpec.thrustPerTick(k, Suits.belt(c) / (double) SuitSpec.BELT_TICKS); float fw = p.input.forwardImpulse, st = p.input.leftImpulse; double mv = Math.hypot(fw, st);
            double up = mv > 0.01 ? a * Math.cos(SuitSpec.TILT) : a, side = mv > 0.01 ? a * Math.sin(SuitSpec.TILT) : 0, yr = Math.toRadians(p.getYRot());
            double ix = st / Math.max(mv, 1e-6), iz = fw / Math.max(mv, 1e-6), dx = ix * Math.cos(yr) - iz * Math.sin(yr), dz = iz * Math.cos(yr) + ix * Math.sin(yr);
            p.setDeltaMovement(p.getDeltaMovement().add(dx * side, up, dz * side)); p.fallDistance = 0; thrustTicks++;
            // steam from the two nozzles, behind the hips
            double by = Math.toRadians(p.yBodyRot); Vec3 back = new Vec3(Math.sin(by), 0, -Math.cos(by)).scale(0.35), right = new Vec3(-Math.cos(by), 0, -Math.sin(by)).scale(0.3);
            for (int s = -1; s <= 1; s += 2) { Vec3 n = p.position().add(back).add(right.scale(s)).add(0, 0.75, 0); p.level().addParticle(ParticleTypes.CLOUD, n.x, n.y, n.z, (p.getRandom().nextDouble() - 0.5) * 0.05, -0.35, (p.getRandom().nextDouble() - 0.5) * 0.05); }
        } catch (Throwable t) { errors++; if (errors < 3) HopelessInfinity.LOG.error("[Hopeless Infinity] rocket belt (client) failed", t); } }
    public static String probe() { return String.format("suits (client): belt thrust ticks %d, packets %d, backpacks drawn %d, errors %d", thrustTicks, sent, drawn, errors); }
}
