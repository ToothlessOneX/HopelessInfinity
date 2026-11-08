package com.hopeless.infinity.wild;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 0.28.0: the sunberry bush: grows like a sweet berry bush, but ripens only at the top of the Hollows (the sky isles' and roof gardens'
 * biomes, in the cave sun's light); anywhere else it stays green. Ripe bushes glow a little. Probes: grown, held back.
 */
public class SunberryBush extends SweetBerryBushBlock {
    public static volatile long grown, heldBack;
    public SunberryBush(Properties p) { super(p); }
    static boolean sunny(ServerLevel l, BlockPos p) {
        var b = l.getBiome(p).unwrapKey(); if (b.isEmpty()) return false;
        String id = b.get().location().getPath(); return id.equals("sky_isles") || id.equals("roof_gardens");
    }
    @Override public void randomTick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        int age = s.getValue(AGE);
        if (age < 3 && r.nextInt(5) == 0 && l.getRawBrightness(p.above(), 0) >= 9) {
            if (age >= 1 && !sunny(l, p)) { heldBack++; return; }   // it flowers anywhere, ripens only in the sun's light up high
            l.setBlock(p, s.setValue(AGE, age + 1), 2); grown++;
        }
    }
    @Override public ItemStack getCloneItemStack(BlockGetter l, BlockPos p, BlockState s) { return new ItemStack(SkyReg.SUNBERRIES.get()); }
    @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
        int age = s.getValue(AGE);
        if (age > 1) {
            int n = 1 + l.random.nextInt(2); Block.popResource(l, p, new ItemStack(SkyReg.SUNBERRIES.get(), n + (age == 3 ? 1 : 0)));
            l.playSound(null, p, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1f, 0.8f + l.random.nextFloat() * 0.4f);
            l.setBlock(p, s.setValue(AGE, 1), 2);
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        return super.use(s, l, p, pl, h, hit);
    }
}
