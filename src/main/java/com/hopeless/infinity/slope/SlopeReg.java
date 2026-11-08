package com.hopeless.infinity.slope;

import com.hopeless.infinity.HopelessInfinity;
import com.hopeless.infinity.slope.ShapeLib;
import com.hopeless.infinity.slope.SlopeMesh;
import com.hopeless.infinity.slope.Slopes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 0.26.0: slope blocks.
 * - Terrain slopes: one block per surface material, state = the corner heights in quarter blocks (625 codes); the generator places them.
 *   They look like their material (the client model copies the material's own textures and tint).
 * - Shaped blocks: one block for the whole building shape library (ShapeLib); the shape, its turn (24) and its material (any block)
 *   are kept in the block entity. One item per shape. Right-click a shaped block with a block in hand to give it that material;
 *   sneak-right-click with an empty hand to turn it about its floor.
 */
public final class SlopeReg {
    private SlopeReg() {}
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HopelessInfinity.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HopelessInfinity.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HopelessInfinity.MODID);
    public static final IntegerProperty CORNERS = IntegerProperty.create("corners", 0, Slopes.CODES - 1);
    public static volatile long shapedPlaced, materialsSet, turns;   // probes

    // ---------------------------------------------------------------- terrain slopes
    public static final Map<String, RegistryObject<Block>> TERRAIN = new LinkedHashMap<>();
    /** The material each terrain slope copies (slope block id -> full block id). */
    public static final Map<String, ResourceLocation> MATERIAL = new LinkedHashMap<>();
    static {
        for (Map.Entry<String, String> e : Slopes.MAT.entrySet()) {
            String sl = e.getValue(); ResourceLocation mat = e.getKey().contains(":") ? new ResourceLocation(e.getKey()) : new ResourceLocation("minecraft", e.getKey());
            boolean soft = sl.matches("slope_(grass_block|dirt|coarse_dirt|podzol|moss_block|sand|red_sand|gravel|clay|mud|snow_block|mycelium|rooted_dirt|packed_mud)");
            SoundType snd = sl.contains("sand") ? SoundType.SAND : sl.equals("slope_gravel") ? SoundType.GRAVEL : sl.matches("slope_(grass_block|moss_block|mycelium)") ? SoundType.GRASS
                : sl.equals("slope_mud") ? SoundType.MUD : sl.equals("slope_snow_block") ? SoundType.SNOW : soft ? SoundType.GRAVEL : SoundType.STONE;
            BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(soft ? 0.6f : 1.5f, soft ? 0.6f : 6f).sound(snd).noOcclusion()
                .isValidSpawn((st, lvl, pos, type) -> true);   // mobs and animals still spawn on sloped ground
            if (!soft) p = p.requiresCorrectToolForDrops();
            final BlockBehaviour.Properties fp = p;
            RegistryObject<Block> b = BLOCKS.register(sl, () -> new TerrainSlope(fp));
            ITEMS.register(sl, () -> new BlockItem(b.get(), new Item.Properties()));
            TERRAIN.put(sl, b); MATERIAL.put(sl, mat);
        }
    }
    public static class TerrainSlope extends Block {
        static final VoxelShape[] SHAPES = new VoxelShape[Slopes.CODES];
        static VoxelShape shape(int code) {
            VoxelShape s = SHAPES[code]; if (s != null) return s;
            int[] h = SlopeMesh.decode(code, Slopes.L); VoxelShape v = Shapes.empty();
            for (int[] b : SlopeMesh.boxes(h, Slopes.L, SlopeMesh.autoSplit(h), SlopeMesh.DOWN)) v = Shapes.or(v, Block.box(b[0], b[1], b[2], b[3], b[4], b[5]));
            return SHAPES[code] = v.optimize();
        }
        public TerrainSlope(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(CORNERS, SlopeMesh.encode(new int[]{4, 4, 0, 0}, Slopes.L))); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(CORNERS); }
        @Override public VoxelShape getShape(BlockState st, BlockGetter l, BlockPos pos, CollisionContext c) { return shape(st.getValue(CORNERS)); }
        @Override public boolean useShapeForLightOcclusion(BlockState st) { return true; }
        @Override public boolean isPathfindable(BlockState st, BlockGetter l, BlockPos pos, PathComputationType t) { return t == PathComputationType.LAND; }
        /** Placed by hand: a one-block ramp rising away from the player. */
        @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
            int[] h = switch (c.getHorizontalDirection()) { case NORTH -> new int[]{4, 4, 0, 0}; case SOUTH -> new int[]{0, 0, 4, 4}; case EAST -> new int[]{0, 4, 4, 0}; default -> new int[]{4, 0, 0, 4}; };
            return defaultBlockState().setValue(CORNERS, SlopeMesh.encode(h, Slopes.L));
        }
    }

    // ---------------------------------------------------------------- shaped blocks (the building shape library)
    public static final RegistryObject<Block> SHAPED = BLOCKS.register("shaped", () -> new Shaped(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 6f).noOcclusion().dynamicShape()));
    public static final RegistryObject<BlockEntityType<ShapedBE>> SHAPED_BE = BES.register("shaped", () -> BlockEntityType.Builder.of(ShapedBE::new, SHAPED.get()).build(null));
    public static final Map<String, RegistryObject<Item>> SHAPE_ITEMS = new LinkedHashMap<>();
    static { for (ShapeLib.Shape s : ShapeLib.SHAPES) { final int idx = ShapeLib.INDEX.get(s.id); SHAPE_ITEMS.put(s.id, ITEMS.register("shape_" + s.id, () -> new ShapeItem(SHAPED.get(), idx, new Item.Properties()))); } }
    static final VoxelShape[] SHAPE_CACHE = new VoxelShape[ShapeLib.SHAPES.size() * 24];
    public static VoxelShape voxel(int shape, int o) {
        int k = shape * 24 + o; VoxelShape s = SHAPE_CACHE[k]; if (s != null) return s; VoxelShape v = Shapes.empty();
        for (int[] b : ShapeLib.boxes(shape, o)) v = Shapes.or(v, Block.box(b[0], b[1], b[2], b[3], b[4], b[5]));
        return SHAPE_CACHE[k] = v.optimize();
    }

    public static class Shaped extends Block implements EntityBlock {
        public Shaped(Properties p) { super(p); }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new ShapedBE(pos, st); }
        static ShapedBE be(BlockGetter l, BlockPos pos) { return l != null && l.getBlockEntity(pos) instanceof ShapedBE b ? b : null; }
        @Override public VoxelShape getShape(BlockState st, BlockGetter l, BlockPos pos, CollisionContext c) { ShapedBE b = be(l, pos); return b == null ? Shapes.block() : voxel(b.shape, b.orient); }
        @Override public boolean useShapeForLightOcclusion(BlockState st) { return true; }
        @Override public void setPlacedBy(Level lvl, BlockPos pos, BlockState st, net.minecraft.world.entity.LivingEntity who, ItemStack stack) {
            ShapedBE b = be(lvl, pos); if (b == null || !(stack.getItem() instanceof ShapeItem si)) return;
            b.shape = si.shape; b.material = ShapeItem.material(stack); b.orient = ShapeItem.PENDING.get(); shapedPlaced++; b.changed();
        }
        @Override public InteractionResult use(BlockState st, Level lvl, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
            ShapedBE b = be(lvl, pos); if (b == null) return InteractionResult.PASS; ItemStack held = p.getItemInHand(hand);
            if (held.getItem() instanceof BlockItem bi && !(bi instanceof ShapeItem) && !(bi.getBlock() instanceof EntityBlock)) {   // a block in hand: this shape takes its look
                if (!lvl.isClientSide) { b.material = bi.getBlock().defaultBlockState(); materialsSet++; b.changed(); }
                return InteractionResult.sidedSuccess(lvl.isClientSide);
            }
            if (held.isEmpty() && p.isShiftKeyDown()) {                                             // turn about the floor
                if (!lvl.isClientSide) { b.orient = ShapeItem.turnAboutFloor(b.orient); turns++; b.changed(); }
                return InteractionResult.sidedSuccess(lvl.isClientSide);
            }
            return InteractionResult.PASS;
        }
        @Override public ItemStack getCloneItemStack(BlockGetter l, BlockPos pos, BlockState st) {
            ShapedBE b = be(l, pos); if (b == null) return ItemStack.EMPTY;
            ItemStack s = new ItemStack(SHAPE_ITEMS.get(ShapeLib.SHAPES.get(b.shape).id).get()); ShapeItem.setMaterial(s, b.material); return s;
        }
        @Override public void playerWillDestroy(Level lvl, BlockPos pos, BlockState st, Player p) {
            ShapedBE b = be(lvl, pos);
            if (!lvl.isClientSide && b != null && !p.isCreative()) { ItemStack s = getCloneItemStack(lvl, pos, st); if (!s.isEmpty()) Block.popResource(lvl, pos, s); }
            super.playerWillDestroy(lvl, pos, st, p);
        }
    }

    public static class ShapedBE extends BlockEntity {
        public int shape, orient; public BlockState material = Blocks.STONE.defaultBlockState();
        public ShapedBE(BlockPos pos, BlockState st) { super(SHAPED_BE.get(), pos, st); }
        void changed() { setChanged(); if (level != null) { level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); if (level.isClientSide) requestModelDataUpdate(); } }
        @Override protected void saveAdditional(CompoundTag t) { super.saveAdditional(t); write(t); }
        void write(CompoundTag t) { t.putString("shape", ShapeLib.SHAPES.get(shape).id); t.putInt("orient", orient); t.put("material", NbtUtils.writeBlockState(material)); }
        @Override public void load(CompoundTag t) { super.load(t);
            Integer i = ShapeLib.INDEX.get(t.getString("shape")); shape = i == null ? 0 : i; orient = Math.floorMod(t.getInt("orient"), 24);
            if (t.contains("material")) material = NbtUtils.readBlockState(level != null ? level.holderLookup(Registries.BLOCK) : net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), t.getCompound("material"));
            if (level != null && level.isClientSide) { requestModelDataUpdate(); level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8); } }
        @Override public CompoundTag getUpdateTag() { CompoundTag t = super.getUpdateTag(); write(t); return t; }
        @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
        @Override public net.minecraftforge.client.model.data.ModelData getModelData() {
            return net.minecraftforge.client.model.data.ModelData.builder().with(ShapeData.KEY, new ShapeData(shape, orient, material)).build(); }
    }
    /** What the client model needs to draw a shaped block. */
    public record ShapeData(int shape, int orient, BlockState material) {
        public static final net.minecraftforge.client.model.data.ModelProperty<ShapeData> KEY = new net.minecraftforge.client.model.data.ModelProperty<>();
    }

    public static class ShapeItem extends BlockItem {
        public final int shape; static final ThreadLocal<Integer> PENDING = ThreadLocal.withInitial(() -> 0);
        public ShapeItem(Block b, int shape, Properties p) { super(b, p); this.shape = shape; }
        @Override public void registerBlocks(Map<Block, Item> m, Item it) { if (shape == 0) super.registerBlocks(m, it); }   // one item stands for the block (pick block uses the clone stack)
        @Override public String getDescriptionId() { return "item.hopelessinfinity.shape_" + ShapeLib.SHAPES.get(shape).id; }
        @Override public Component getName(ItemStack s) { BlockState m = material(s); return Component.translatable(getDescriptionId()).append(" (").append(m.getBlock().getName()).append(")"); }
        static BlockState material(ItemStack s) {
            CompoundTag t = s.getTag(); if (t == null || !t.contains("material")) return Blocks.STONE.defaultBlockState();
            return NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), t.getCompound("material"));
        }
        static void setMaterial(ItemStack s, BlockState m) { s.getOrCreateTag().put("material", NbtUtils.writeBlockState(m)); }
        /** The turn for a placement: the floor against the face clicked, the back toward where the player looks (floor or ceiling), or
         *  up (on a wall) - so a slope rises away from the player. */
        @Override public InteractionResult place(BlockPlaceContext c) {
            Direction clicked = c.getClickedFace(); int[] down = vec(clicked.getOpposite()), back;
            if (clicked.getAxis().isVertical()) back = vec(c.getHorizontalDirection()); else back = new int[]{0, 1, 0};
            int o = ShapeLib.orient(down, back); PENDING.set(Math.max(0, o));
            return super.place(c);
        }
        static int[] vec(Direction d) { return new int[]{d.getStepX(), d.getStepY(), d.getStepZ()}; }
        /** The next of the 4 turns that keep the floor where it is. */
        static int turnAboutFloor(int o) {
            int[] down = ShapeLib.turnDir(o, new int[]{0, -1, 0}), back = ShapeLib.turnDir(o, new int[]{0, 0, -1});
            int[] nb = {down[1] * back[2] - down[2] * back[1], down[2] * back[0] - down[0] * back[2], down[0] * back[1] - down[1] * back[0]};   // a quarter turn about the floor axis
            int r = ShapeLib.orient(down, nb); return r < 0 ? o : r;
        }
    }
}
