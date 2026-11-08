package com.hopeless.infinity.world;

import com.hopeless.infinity.HopelessInfinity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 0.27.3 THE VIDEO SCREENS (planet/Frontage: the Cobble & Sons HQ's front). The world places one controller block at a screen's lowest -x
 * corner (facing out of the screen) and plain dark panel blocks over the rest; client/ScreenRender draws the picture (planet/AdArt) over
 * the whole Frontage.SCREEN_W x SCREEN_H panel with shaders/adscreen.glsl. A player can place a controller too: it shows the same size.
 */
public final class Screens {
    private Screens() {}
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HopelessInfinity.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HopelessInfinity.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HopelessInfinity.MODID);
    public static final RegistryObject<Block> SCREEN = BLOCKS.register("ad_screen", () -> new ScreenBlock(BlockBehaviour.Properties.of().strength(3f, 6f).sound(SoundType.GLASS).lightLevel(s -> 10)));
    public static final RegistryObject<Item> SCREEN_ITEM = ITEMS.register("ad_screen", () -> new BlockItem(SCREEN.get(), new Item.Properties()));
    /** 0.28.4 a digital billboard's controller (planet/Billboards): the same LED shader at bulletin size (Billboards.FACE_W x FACE_H). */
    public static final RegistryObject<Block> BILLBOARD = BLOCKS.register("billboard", () -> new ScreenBlock(BlockBehaviour.Properties.of().strength(3f, 6f).sound(SoundType.GLASS).lightLevel(s -> 10)));
    public static final RegistryObject<Item> BILLBOARD_ITEM = ITEMS.register("billboard", () -> new BlockItem(BILLBOARD.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<ScreenBE>> SCREEN_BE = BES.register("ad_screen", () -> BlockEntityType.Builder.of(ScreenBE::new, SCREEN.get(), BILLBOARD.get()).build(null));
    /** The screens loaded on this client (client/ScreenRender draws them). */
    public static final java.util.Set<ScreenBE> CLIENT = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static volatile long loaded;

    public static final class ScreenBlock extends Block implements EntityBlock {
        public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
        ScreenBlock(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
        @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new ScreenBE(p, s); }
    }
    public static final class ScreenBE extends BlockEntity {
        public ScreenBE(BlockPos p, BlockState s) { super(SCREEN_BE.get(), p, s); }
        public boolean billboard() { return getBlockState().is(BILLBOARD.get()); }
        public Direction facing() { BlockState s = getBlockState(); return s.hasProperty(ScreenBlock.FACING) ? s.getValue(ScreenBlock.FACING) : Direction.NORTH; }
        @Override public void onLoad() { super.onLoad(); if (level != null && level.isClientSide) { CLIENT.add(this); loaded++; } }
        @Override public void setRemoved() { super.setRemoved(); CLIENT.remove(this); }
        @Override public void onChunkUnloaded() { super.onChunkUnloaded(); CLIENT.remove(this); }
    }
}
