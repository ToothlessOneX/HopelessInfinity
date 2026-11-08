package com.hopeless.infinity.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** 0.26.0: the town-game blocks ported from Hopeless Deep (job board, For Sale sign, ballot box, radio, atlas, awards) call the town game,
 *  which in Deep runs on Deep's settlement model. Until it runs on Infinity's towns, each use says so to the player and is counted. */
final class Hooks {
    private Hooks() {}
    static volatile long uses;
    static void notYet(ServerPlayer p, String what) { uses++; p.displayClientMessage(Component.literal(what + ": coming to Infinity's towns soon"), true); }
}
