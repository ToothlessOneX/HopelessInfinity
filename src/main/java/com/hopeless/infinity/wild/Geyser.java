package com.hopeless.infinity.wild;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 0.29.0: a geyser in the middle of a hot spring. Now and then (a random tick, about once a minute or two at the normal tick speed) it
 * erupts for 3 seconds: a column of steam and water 14 high, and anything standing on it or in the column is thrown up (about 12
 * blocks for a player). It only throws when erupting, so it can be walked over. Probes: eruptions, entities launched.
 */
public class Geyser extends Block {
    public static final BooleanProperty ERUPTING = BooleanProperty.create("erupting");
    public static final IntegerProperty STAGE = BlockStateProperties.AGE_15;
    public static volatile long eruptions, launched;
    public Geyser(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(ERUPTING, false).setValue(STAGE, 0)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(ERUPTING, STAGE); }
    @Override public boolean isRandomlyTicking(BlockState s) { return !s.getValue(ERUPTING); }
    @Override public void randomTick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        if (r.nextInt(3) != 0) return;
        l.setBlock(p, s.setValue(ERUPTING, true).setValue(STAGE, 0), 3); l.scheduleTick(p, this, 4); eruptions++;
        l.playSound(null, p, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.5f, 0.6f);
    }
    /** Placed already erupting (a command, a test): it starts its own clock. */
    @Override public void onPlace(BlockState s, net.minecraft.world.level.Level l, BlockPos p, BlockState old, boolean moved) { if (s.getValue(ERUPTING) && !l.isClientSide()) l.scheduleTick(p, this, 4); }
    @Override public void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        if (!s.getValue(ERUPTING)) return;
        int st = s.getValue(STAGE);
        double cx = p.getX() + 0.5, cz = p.getZ() + 0.5;
        l.sendParticles(ParticleTypes.CLOUD, cx, p.getY() + 1.5, cz, 20, 0.3, 4, 0.3, 0.15);
        l.sendParticles(ParticleTypes.SPLASH, cx, p.getY() + 2, cz, 40, 0.4, 3, 0.4, 0.4);
        if (st % 4 == 0) l.playSound(null, p, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 1.2f, 0.7f);
        for (Entity e : l.getEntities((Entity) null, new AABB(p.getX() - 0.3, p.getY() + 0.9, p.getZ() - 0.3, p.getX() + 1.3, p.getY() + 14, p.getZ() + 1.3), x -> !x.isSpectator())) {
            Vec3 v = e.getDeltaMovement(); if (v.y < 1.6) { e.setDeltaMovement(v.x * 0.5, 1.6, v.z * 0.5); e.hurtMarked = true; e.fallDistance = 0; launched++; } }
        if (st >= 15) l.setBlock(p, s.setValue(ERUPTING, false).setValue(STAGE, 0), 3);
        else { l.setBlock(p, s.setValue(STAGE, st + 1), 2); l.scheduleTick(p, this, 4); }
    }
    @Override public void animateTick(BlockState s, net.minecraft.world.level.Level l, BlockPos p, RandomSource r) {
        if (r.nextInt(3) == 0) l.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.getX() + 0.5 + r.nextGaussian() * 0.15, p.getY() + 1.05, p.getZ() + 0.5 + r.nextGaussian() * 0.15, 0, 0.03, 0);
        if (r.nextInt(5) == 0) l.addParticle(ParticleTypes.BUBBLE_POP, p.getX() + r.nextDouble(), p.getY() + 1.02, p.getZ() + r.nextDouble(), 0, 0.05, 0);
    }
}
