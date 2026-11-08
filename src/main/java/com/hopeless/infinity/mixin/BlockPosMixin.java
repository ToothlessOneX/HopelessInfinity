package com.hopeless.infinity.mixin;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The height limit. Minecraft packs a block position into one 64-bit number: 26 bits for x, 26 for z, and what is left (12) for y,
 * so height is capped at 4,096 - and DimensionType works its whole height range out from that y width when it loads. Sizing x and z
 * for 8,000,000 instead of 30,000,000 gives them 24 bits each and y 16 bits: 65,504 blocks of height. (Width is the next job:
 * Earth needs 20,000,000 out from the centre.)
 */
@Mixin(value = BlockPos.class, remap = false)
public abstract class BlockPosMixin {
    @ModifyConstant(method = "<clinit>", constant = @Constant(intValue = 30000000), remap = false)
    private static int hopelessinfinity$packWidth(int vanilla) { return 8_000_000; }
}
