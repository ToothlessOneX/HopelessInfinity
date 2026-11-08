package com.hopeless.infinity.wild;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 0.28.0: a cluster of salt crystals growing on the floor of an evaporite cave (a cross model, like a flower). */
public class SaltCrystal extends Block {
    static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 10, 13);
    public SaltCrystal(Properties p) { super(p); }
    @Override public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE.move(s.getOffset(l, p).x, 0, s.getOffset(l, p).z); }
    @Override public boolean canSurvive(BlockState s, net.minecraft.world.level.LevelReader l, BlockPos p) { return l.getBlockState(p.below()).isFaceSturdy(l, p.below(), net.minecraft.core.Direction.UP); }
}
