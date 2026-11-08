package com.hopeless.infinity.wild;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** 0.28.0: a vent in the rock: a hot spring's steam rises from it (white clouds), or a sulfur vent's fumes (smoke), with a hiss now and
 *  then. Only looks and sounds (client side, while it is in view). */
public class Vent extends Block {
    final boolean sulfur;
    public Vent(Properties p, boolean sulfur) { super(p); this.sulfur = sulfur; }
    @Override public void animateTick(BlockState s, Level l, BlockPos p, RandomSource r) {
        if (l.getBlockState(p.above()).isSolidRender(l, p.above())) return;   // covered: nothing comes out
        for (int i = 0; i < 2; i++) l.addAlwaysVisibleParticle(sulfur ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.CLOUD, true,
            p.getX() + 0.3 + r.nextDouble() * 0.4, p.getY() + 1.05, p.getZ() + 0.3 + r.nextDouble() * 0.4, (r.nextDouble() - 0.5) * 0.02, 0.06 + r.nextDouble() * 0.06, (r.nextDouble() - 0.5) * 0.02);
        if (r.nextInt(40) == 0) l.playLocalSound(p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.25f, 0.6f + r.nextFloat() * 0.3f, false);
    }
}
