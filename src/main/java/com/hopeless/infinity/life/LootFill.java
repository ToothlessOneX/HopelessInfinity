package com.hopeless.infinity.life;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** 0.26.0: Hopeless Deep restocks loot boxes from its towns' state (TownLife); Infinity's towns do not feed that yet, so a box keeps the
 *  loot its loot table gave it. Opens are counted. */
public final class LootFill { private LootFill() {} public static volatile long opens; public static void open(ServerLevel l, BlockPos p) { opens++; } }
