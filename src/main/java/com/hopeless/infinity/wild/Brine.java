package com.hopeless.infinity.wild;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * 0.29.0: brine in a cauldron, and salt from it. Drop rock salt (a salt crystal or a halite block) into a full water cauldron and it
 * becomes brine. The brine dries one level at a time on random ticks: fast over heat (fire, a campfire, lava, magma, a steam or sulfur
 * vent: every tick that comes), slowly without (1 tick in 6). When the last level dries the cauldron is empty again and 3 salt drop
 * out of it. Probes: brine made, levels dried, salt given.
 */
public class Brine extends Block {
    public static final IntegerProperty LEVEL = LayeredCauldronBlock.LEVEL;
    public static volatile long made, dried, salt;
    public Brine(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(LEVEL, 3)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(LEVEL); }
    @Override public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Blocks.CAULDRON.defaultBlockState().getShape(l, p, c); }
    @Override public VoxelShape getInteractionShape(BlockState s, BlockGetter l, BlockPos p) { return Blocks.CAULDRON.defaultBlockState().getInteractionShape(l, p); }
    @Override public boolean isRandomlyTicking(BlockState s) { return true; }
    static boolean heat(BlockState b) {
        return b.is(Blocks.FIRE) || b.is(Blocks.SOUL_FIRE) || b.is(Blocks.LAVA) || b.is(Blocks.MAGMA_BLOCK) || b.is(net.minecraft.tags.BlockTags.CAMPFIRES) && b.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
            || b.getBlock() instanceof Vent;
    }
    @Override public void randomTick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        if (!heat(l.getBlockState(p.below())) && r.nextInt(6) != 0) return;
        int lv = s.getValue(LEVEL); dried++;
        if (lv > 1) { l.setBlock(p, s.setValue(LEVEL, lv - 1), 3); return; }
        l.setBlock(p, Blocks.CAULDRON.defaultBlockState(), 3);
        Block.popResource(l, p.above(), new ItemStack(SkyReg.SALT.get(), 3)); salt += 3;
        l.playSound(null, p, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.4f);
    }
    /** A salt crystal or halite into a full water cauldron: brine. */
    public static void use(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide()) return;
        BlockState s = e.getLevel().getBlockState(e.getPos()); ItemStack it = e.getItemStack();
        if (!s.is(Blocks.WATER_CAULDRON) || s.getValue(LayeredCauldronBlock.LEVEL) < 3) return;
        if (!it.is(SkyReg.SALT_CRYSTAL_ITEM.get()) && !it.is(SkyReg.HALITE_ITEM.get())) return;
        e.getLevel().setBlock(e.getPos(), SkyReg.BRINE_CAULDRON.get().defaultBlockState(), 3);
        if (!e.getEntity().getAbilities().instabuild) it.shrink(1);
        e.getLevel().playSound(null, e.getPos(), SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8f, 1.2f);
        made++; e.setCanceled(true); e.setCancellationResult(InteractionResult.SUCCESS);
    }
}
