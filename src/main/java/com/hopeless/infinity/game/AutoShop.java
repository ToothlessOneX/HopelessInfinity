package com.hopeless.infinity.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;

/**
 * 0.38.0: repair and paint shops (the auto repair lots' car lifts). Use a lift: the shop quotes for the nearest vehicle within 8
 * blocks - labor and parts, $60 + up to $600 by how much is broken; sneak and use: they fix everything (body, radiator, tyres, puts it
 * back on its wheels). Use it with a dye: a repaint in the paint closest to that dye ($150).
 */
public final class AutoShop {
    private AutoShop() {}
    public static volatile long quotes, repairs, repaints;   // probes
    public static void use(ServerLevel l, ServerPlayer p, BlockPos lift, InteractionHand h) {   // 0.26.0: no vehicles in Infinity yet (owner, 2026-11-04: none until he says)
        Hooks.notYet(p, "Auto shop lift"); }

    public static String probe() { return "auto shops: quotes " + quotes + " repairs " + repairs + " repaints " + repaints; }
}
