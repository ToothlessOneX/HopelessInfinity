package com.hopeless.infinity.town.client;

import com.hopeless.infinity.town.OwnNet;
import com.hopeless.infinity.town.OwnReg;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** 0.34.0 client side of the own blocks: the keypad screen, the item on a depot, the (unseen) seat entity. */
public final class OwnClient {
    private OwnClient() {}
    public static void init(IEventBus bus) {
        bus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerEntityRenderer(OwnReg.SEAT.get(), NoopRenderer::new);
            e.registerBlockEntityRenderer(OwnReg.DEPOT_BE.get(), DepotRenderer::new); });
    }
    public static void keypad(BlockPos p) { Minecraft.getInstance().setScreen(new Keypad(p)); }

    /** A 12-key pad: digits, clear, enter; the code shows as dots. */
    static final class Keypad extends Screen {
        final BlockPos pos; String code = "";
        Keypad(BlockPos pos) { super(Component.translatable("screen.hopelessinfinity.keypad")); this.pos = pos; }
        @Override protected void init() {
            int cx = width / 2, y0 = height / 2 - 50;
            String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "OK"};
            for (int i = 0; i < 12; i++) { String k = keys[i]; int x = cx - 46 + (i % 3) * 32, y = y0 + (i / 3) * 24;
                addRenderableWidget(Button.builder(Component.literal(k), b -> press(k)).bounds(x, y, 28, 20).build()); } }
        void press(String k) {
            if (k.equals("C")) code = ""; else if (k.equals("OK")) { if (!code.isEmpty()) OwnNet.CH.sendToServer(new OwnNet.Code(pos, code)); onClose(); }
            else if (code.length() < 8) code += k; }
        @Override public boolean charTyped(char c, int m) { if (c >= '0' && c <= '9') { press(String.valueOf(c)); return true; } return super.charTyped(c, m); }
        @Override public boolean keyPressed(int key, int sc, int m) { if (key == 257 || key == 335) { press("OK"); return true; } if (key == 259 && !code.isEmpty()) { code = code.substring(0, code.length() - 1); return true; } return super.keyPressed(key, sc, m); }
        @Override public void render(GuiGraphics g, int mx, int my, float pt) {
            renderBackground(g); g.drawCenteredString(font, title, width / 2, height / 2 - 82, 0xFFFFFF);
            g.fill(width / 2 - 46, height / 2 - 70, width / 2 + 46, height / 2 - 56, 0xFF102010);
            g.drawCenteredString(font, "*".repeat(code.length()), width / 2, height / 2 - 67, 0x40FF40); super.render(g, mx, my, pt); }
        @Override public boolean isPauseScreen() { return false; }
    }
    static final class DepotRenderer implements BlockEntityRenderer<OwnReg.DepotBE> {
        DepotRenderer(BlockEntityRendererProvider.Context c) {}
        @Override public void render(OwnReg.DepotBE be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            if (be.item.isEmpty()) return; ps.pushPose(); ps.translate(0.5, 13 / 16.0 + 0.02, 0.5); ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90)); ps.scale(0.5f, 0.5f, 0.5f);
            Minecraft.getInstance().getItemRenderer().renderStatic(be.item, ItemDisplayContext.FIXED, light, overlay, ps, buf, be.getLevel(), (int) be.getBlockPos().asLong()); ps.popPose(); }
    }
}
