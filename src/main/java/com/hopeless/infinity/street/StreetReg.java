package com.hopeless.infinity.street;

import com.hopeless.infinity.HopelessInfinity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 0.22.0: street blocks made for Hopeless Deep (no mod has them for 1.20.1 in this pack): road surfaces with their own textures, and
 * street props modelled in Blender (blender/street_props.py -> OBJ) that work:
 * - traffic lights cycle green 20 s, yellow 4 s (ITE: 3-6 s), then the cross street; all lights run on one clock, so every
 *   intersection agrees and the two heads on a pole never both show green;
 * - street lamps light the road under their head (an invisible light block, level 15);
 * - fire hydrants put out fire within 6 blocks when used;
 * - litter bins take the item in your hand and destroy it;
 * - cars and taxis fill their 2 x 4 footprint (invisible solid blocks round the model); the horn sounds when used; breaking a
 *   car removes all of it.
 */
public final class StreetReg {
    private StreetReg() {}
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HopelessInfinity.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HopelessInfinity.MODID);
    public static volatile long lightTicks, lightStarts, hydrantUses, binUses, horns, carsBroken;   // probes

    static BlockBehaviour.Properties road(MapColor c) { return BlockBehaviour.Properties.of().mapColor(c).strength(1.5f, 6f).requiresCorrectToolForDrops(); }
    static BlockBehaviour.Properties prop(MapColor c) { return BlockBehaviour.Properties.of().mapColor(c).strength(2f, 6f).noOcclusion(); }

    static RegistryObject<Block> block(String id, java.util.function.Supplier<Block> s, boolean item) {
        RegistryObject<Block> b = BLOCKS.register(id, s);
        if (item) ITEMS.register(id, () -> new BlockItem(b.get(), new Item.Properties()));
        return b;
    }
    // road surfaces
    public static final RegistryObject<Block> ASPHALT = block("asphalt", () -> new Block(road(MapColor.COLOR_GRAY)), true);
    public static final RegistryObject<Block> ASPHALT_WORN = block("asphalt_worn", () -> new Block(road(MapColor.COLOR_GRAY)), true);
    public static final RegistryObject<Block> PAINT_WHITE = block("paint_white", () -> new Block(road(MapColor.SNOW)), true);
    public static final RegistryObject<Block> PAINT_YELLOW = block("paint_yellow", () -> new Block(road(MapColor.COLOR_YELLOW)), true);
    public static final RegistryObject<Block> BUS_LANE = block("bus_lane", () -> new Block(road(MapColor.COLOR_RED)), true);
    public static final RegistryObject<Block> SIDEWALK = block("sidewalk", () -> new Block(road(MapColor.STONE)), true);
    public static final RegistryObject<Block> CURB = block("curb", () -> new Block(road(MapColor.STONE)), true);
    public static final RegistryObject<Block> TREE_PIT = block("tree_pit", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.6f)), true);
    public static final RegistryObject<Block> BUS_WORD = block("bus_word", () -> new Facing(road(MapColor.COLOR_RED)), true);
    public static final RegistryObject<Block> ONLY_WORD = block("only_word", () -> new Facing(road(MapColor.COLOR_RED)), true);
    public static final RegistryObject<Block> MANHOLE = block("manhole", () -> new Block(road(MapColor.METAL)), true);
    public static final RegistryObject<Block> STORM_DRAIN = block("storm_drain", () -> new StormDrain(road(MapColor.METAL)), true);
    /** 0.32.0: a curb inlet grate: items dropped on it fall through (into the pipe to the sewer), and water poured on it drains away. */
    public static final class StormDrain extends Block {
        public StormDrain(Properties p) { super(p); }
        @Override public VoxelShape getCollisionShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
            return c instanceof net.minecraft.world.phys.shapes.EntityCollisionContext ec && ec.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity ? Shapes.empty() : Shapes.block(); }
        @Override public void neighborChanged(BlockState s, Level l, BlockPos p, Block b, BlockPos from, boolean moving) {
            if (!l.isClientSide && from.equals(p.above()) && l.getFluidState(from).is(net.minecraft.tags.FluidTags.WATER) && l.getBlockState(from).getBlock() instanceof LiquidBlock) {
                l.setBlock(from, Blocks.AIR.defaultBlockState(), 3); l.playSound(null, p, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.6f, 0.5f); com.hopeless.infinity.town.CityReg.drained++; } }
    }
    /** A full road block turned to the lane's direction (its word reads from a car coming the other way up the lane). */
    public static class Facing extends HorizontalDirectionalBlock {
        public Facing(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection()); }
    }
    // 0.24.0: half-block slabs of the road surfaces, for the half step where a road or sidewalk rises a block
    public static final RegistryObject<Block> ASPHALT_SLAB = block("asphalt_slab", () -> new SlabBlock(road(MapColor.COLOR_GRAY)), true);
    public static final RegistryObject<Block> ASPHALT_WORN_SLAB = block("asphalt_worn_slab", () -> new SlabBlock(road(MapColor.COLOR_GRAY)), true);
    public static final RegistryObject<Block> SIDEWALK_SLAB = block("sidewalk_slab", () -> new SlabBlock(road(MapColor.STONE)), true);
    public static final RegistryObject<Block> CURB_SLAB = block("curb_slab", () -> new SlabBlock(road(MapColor.STONE)), true);
    public static final RegistryObject<Block> PLAZA_PAVER_SLAB = block("plaza_paver_slab", () -> new SlabBlock(road(MapColor.SAND)), true);
    public static final RegistryObject<Block> LOT_ASPHALT_SLAB = block("lot_asphalt_slab", () -> new SlabBlock(road(MapColor.COLOR_GRAY)), true);
    // props
    public static final RegistryObject<Block> WATER_TANK = block("water_tank", () -> new Prop(prop(MapColor.WOOD).strength(3f), Shapes.block()), true);
    public static final RegistryObject<Block> AC_UNIT = block("ac_unit", () -> new Prop(prop(MapColor.METAL).strength(3f), Shapes.block()), true);
    public static final RegistryObject<Block> PARKING_METER = block("parking_meter", () -> new Prop(prop(MapColor.METAL), Block.box(6, 0, 6, 10, 16, 10)), true);
    public static final RegistryObject<Block> BENCH = block("bench", () -> new Prop(prop(MapColor.WOOD).strength(1.5f), Block.box(0, 0, 0, 16, 8, 16)), true);
    // 0.22.0 third pass: city open space
    public static final RegistryObject<Block> DUMPSTER = block("dumpster", () -> new Prop(prop(MapColor.COLOR_GREEN).strength(4f), Shapes.block()), true);
    public static final RegistryObject<Block> BIKE_RACK = block("bike_rack", () -> new Prop(prop(MapColor.METAL), Block.box(2, 0, 2, 14, 14, 14)), true);
    public static final RegistryObject<Block> PLANTER = block("planter", () -> new Prop(prop(MapColor.STONE), Block.box(1, 0, 1, 15, 9, 15)), true);
    public static final RegistryObject<Block> NEWSSTAND = block("newsstand", () -> new Prop(prop(MapColor.COLOR_GREEN).strength(3f), Shapes.block()), true);
    public static final RegistryObject<Block> FOOD_CART = block("food_cart", () -> new Prop(prop(MapColor.METAL), Block.box(1, 0, 2, 16, 16, 14)), true);
    public static final RegistryObject<Block> CAFE_TABLE = block("cafe_table", () -> new Prop(prop(MapColor.METAL), Block.box(3, 0, 3, 13, 12, 13)), true);
    public static final RegistryObject<Block> BASKETBALL_HOOP = block("basketball_hoop", () -> new Prop(prop(MapColor.METAL), Block.box(6, 0, 8, 10, 16, 16)), true);
    public static final RegistryObject<Block> SLIDE = block("slide", () -> new Prop(prop(MapColor.COLOR_RED), Shapes.block()), true);
    public static final RegistryObject<Block> SWINGS = block("swings", () -> new Prop(prop(MapColor.COLOR_BLUE), Block.box(0, 0, 6, 16, 16, 10)), true);
    public static final RegistryObject<Block> CLIMBER = block("climber", () -> new Prop(prop(MapColor.COLOR_YELLOW), Shapes.block()), true);
    public static final RegistryObject<Block> PICNIC_TABLE = block("picnic_table", () -> new Prop(prop(MapColor.WOOD).strength(1.5f), Block.box(0, 0, 0, 16, 12, 16)), true);
    public static final RegistryObject<Block> COURT_GREEN = block("court_green", () -> new Block(road(MapColor.COLOR_GREEN)), true);
    public static final RegistryObject<Block> COURT_RED = block("court_red", () -> new Block(road(MapColor.COLOR_RED)), true);
    public static final RegistryObject<Block> COURT_LINE = block("court_line", () -> new Block(road(MapColor.SNOW)), true);
    public static final RegistryObject<Block> PLAZA_PAVER = block("plaza_paver", () -> new Block(road(MapColor.SAND)), true);
    public static final RegistryObject<Block> PLAZA_PAVER_DARK = block("plaza_paver_dark", () -> new Block(road(MapColor.COLOR_BROWN)), true);
    public static final RegistryObject<Block> SAFETY_SURFACE = block("safety_surface", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(1f)), true);
    public static final RegistryObject<Block> PARK_PATH = block("park_path", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.8f)), true);
    public static final RegistryObject<Block> LOT_ASPHALT = block("lot_asphalt", () -> new Block(road(MapColor.COLOR_GRAY)), true);
    public static final RegistryObject<Block> STREET_TREE = block("street_tree", () -> new Prop(prop(MapColor.PLANT).strength(1f), Block.box(6, 0, 6, 10, 16, 10)), true);
    public static final RegistryObject<Block> TRAFFIC_LIGHT = block("traffic_light", () -> new TrafficLight(prop(MapColor.COLOR_BLACK).lightLevel(s -> 6).randomTicks()), true);
    public static final RegistryObject<Block> STREET_LAMP = block("street_lamp", () -> new Lamp(prop(MapColor.METAL)), true);
    public static final RegistryObject<Block> FIRE_HYDRANT = block("fire_hydrant", () -> new Hydrant(prop(MapColor.COLOR_RED)), true);
    public static final RegistryObject<Block> LITTER_BIN = block("litter_bin", () -> new Bin(prop(MapColor.COLOR_GREEN)), true);
    public static final RegistryObject<Block> MAILBOX = block("mailbox", () -> new Prop(prop(MapColor.COLOR_BLUE), Block.box(4, 0, 4, 12, 16, 12)), true);
    public static final RegistryObject<Block> BUS_STOP = block("bus_stop", () -> new Prop(prop(MapColor.METAL), Block.box(7, 0, 7, 9, 16, 9)), true);
    public static final RegistryObject<Block> CAR = block("car", () -> new Car(prop(MapColor.METAL).strength(4f, 12f), false), true);
    public static final RegistryObject<Block> TAXI = block("taxi", () -> new Car(prop(MapColor.COLOR_YELLOW).strength(4f, 12f), true), true);
    public static final RegistryObject<Block> CAR_SPACE = block("car_space", () -> new CarSpace(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4f, 12f).noOcclusion().noLootTable()), false);

    /** A facing prop with a fixed outline. */
    public static class Prop extends HorizontalDirectionalBlock {
        final VoxelShape shape;
        public Prop(Properties p, VoxelShape shape) { super(p); this.shape = shape; registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
        @Override public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) { return shape; }
        @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    }

    /** Traffic light: PHASE 0 = north-south green, 1 = north-south yellow, 2 = east-west green, 3 = east-west yellow. */
    public static final class TrafficLight extends Prop {
        public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 3);
        public static final int GREEN = 20, YELLOW = 4, CYCLE = 2 * (GREEN + YELLOW);   // seconds
        public TrafficLight(Properties p) { super(p, Block.box(6, 0, 6, 10, 16, 10)); registerDefaultState(defaultBlockState().setValue(PHASE, 0)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, PHASE); }
        /** The phase the shared clock shows now, and the ticks until it changes. */
        public static int phaseAt(long gameTime) { int t = (int) Math.floorMod(gameTime / 20, CYCLE); return t < GREEN ? 0 : t < GREEN + YELLOW ? 1 : t < 2 * GREEN + YELLOW ? 2 : 3; }
        static int ticksToNext(long gameTime) { int t = (int) Math.floorMod(gameTime, CYCLE * 20L); int[] edges = {GREEN * 20, (GREEN + YELLOW) * 20, (2 * GREEN + YELLOW) * 20, CYCLE * 20};
            for (int e : edges) if (t < e) return Math.max(1, e - t); return 1; }
        @Override public void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moving) { if (!l.isClientSide) { l.scheduleTick(p, this, 1); lightStarts++; } }
        /** World generation marks each light for post-processing; that runs this when the chunk is ready, which starts the light. */
        @Override public BlockState updateShape(BlockState s, Direction d, BlockState n, net.minecraft.world.level.LevelAccessor l, BlockPos p, BlockPos np) {
            if (!l.isClientSide() && !l.getBlockTicks().hasScheduledTick(p, this)) { l.scheduleTick(p, this, 1); lightStarts++; }
            return s; }
        @Override public void randomTick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) { if (!l.getBlockTicks().hasScheduledTick(p, this)) { l.scheduleTick(p, this, 1); lightStarts++; } }   // lights the world built start on their first random tick
        @Override public void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
            long gt = l.getGameTime(); int ph = phaseAt(gt); if (s.getValue(PHASE) != ph) l.setBlock(p, s.setValue(PHASE, ph), 2);
            l.scheduleTick(p, this, ticksToNext(gt)); lightTicks++;
        }
    }

    /** Street lamp: puts a light block at its head (2 out, 5 up) and removes it when broken. */
    public static final class Lamp extends Prop {
        public Lamp(Properties p) { super(p, Block.box(5, 0, 5, 11, 16, 11)); }
        public static BlockPos head(BlockPos p, Direction f) { return p.relative(f, 2).above(5); }
        @Override public void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moving) {
            BlockPos h = head(p, s.getValue(FACING)); if (!l.isClientSide && l.getBlockState(h).isAir()) l.setBlock(h, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3); }
        @Override public void onRemove(BlockState s, Level l, BlockPos p, BlockState now, boolean moving) {
            if (!s.is(now.getBlock())) { BlockPos h = head(p, s.getValue(FACING)); if (l.getBlockState(h).is(Blocks.LIGHT)) l.removeBlock(h, false); }
            super.onRemove(s, l, p, now, moving); }
    }

    /** Fire hydrant: open it (use) to put out every fire within 6 blocks. */
    public static final class Hydrant extends Prop {
        public Hydrant(Properties p) { super(p, Block.box(3, 0, 3, 13, 14, 13)); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (l instanceof ServerLevel sw && com.hopeless.infinity.game.Outage.noWater(sw, p.getX(), p.getZ())) { pl.displayClientMessage(net.minecraft.network.chat.Component.literal("No pressure - the water main is broken"), true); return InteractionResult.CONSUME; }   // 0.38.0
            if (l instanceof ServerLevel sl) { int n = 0;
                for (BlockPos q : BlockPos.betweenClosed(p.offset(-6, -2, -6), p.offset(6, 4, 6))) if (sl.getBlockState(q).is(net.minecraft.tags.BlockTags.FIRE)) { sl.removeBlock(q, false); n++; }
                sl.sendParticles(ParticleTypes.SPLASH, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 60, 0.6, 0.4, 0.6, 0.2);
                sl.playSound(null, p, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1f, 1f); hydrantUses++; }
            return InteractionResult.sidedSuccess(l.isClientSide); }
    }

    /** Litter bin: use it with an item in hand to throw the item away. */
    public static final class Bin extends Prop {
        public Bin(Properties p) { super(p, Block.box(3, 0, 3, 13, 13, 13)); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            ItemStack it = pl.getItemInHand(h); if (it.isEmpty()) return InteractionResult.PASS;
            if (!l.isClientSide) { pl.setItemInHand(h, ItemStack.EMPTY); l.playSound(null, p, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1f, 0.7f); binUses++; }
            return InteractionResult.sidedSuccess(l.isClientSide); }
    }

    /** A parked car: this block is its left-rear cell; it covers 2 across and 4 along toward its front (FACING). COLOR picks the paint. */
    public static class Car extends Prop {
        public static final IntegerProperty COLOR = IntegerProperty.create("color", 0, 8);
        public static final String[] COLORS = {"white", "black", "silver", "gray", "red", "blue", "green", "brown", "navy"};
        final boolean taxi;
        public Car(Properties p, boolean taxi) { super(p, Shapes.block()); this.taxi = taxi; registerDefaultState(defaultBlockState().setValue(COLOR, 0)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, COLOR); }
        /** The 8 cells of a car whose anchor is p facing f (the anchor first). The model spans x 0..2, z -3..1 when facing north. */
        public static java.util.List<BlockPos> cells(BlockPos p, Direction f) {
            java.util.List<BlockPos> out = new java.util.ArrayList<>(); Direction right = f.getClockWise();
            for (int a = 0; a < 4; a++) for (int c = 0; c < 2; c++) out.add(p.relative(f, a).relative(right, c));
            return out;
        }
        @Override public void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moving) {
            if (l.isClientSide || old.is(this)) return;
            for (BlockPos q : cells(p, s.getValue(FACING))) if (!q.equals(p) && l.getBlockState(q).canBeReplaced()) l.setBlock(q, CAR_SPACE.get().defaultBlockState(), 3); }
        @Override public void onRemove(BlockState s, Level l, BlockPos p, BlockState now, boolean moving) {
            if (!s.is(now.getBlock())) { for (BlockPos q : cells(p, s.getValue(FACING))) if (l.getBlockState(q).is(CAR_SPACE.get())) l.removeBlock(q, false); carsBroken++; }
            super.onRemove(s, l, p, now, moving); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (!l.isClientSide) { l.playSound(null, p, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 2f, 0.6f); horns++; }
            return InteractionResult.sidedSuccess(l.isClientSide); }
    }
    /** The invisible solid cells round a car model. Breaking one breaks the car whose footprint holds it. */
    public static final class CarSpace extends Block {
        public CarSpace(Properties p) { super(p); }
        /** 0.30.0: a click on an abandoned car's body opens its trunk (the anchor cell holds it). */
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            for (BlockPos q : BlockPos.betweenClosed(p.offset(-4, 0, -4), p.offset(4, 0, 4))) { BlockState c = l.getBlockState(q);
                if (c.getBlock() instanceof com.hopeless.infinity.town.TownReg.Wreck && Car.cells(q.immutable(), c.getValue(HorizontalDirectionalBlock.FACING)).contains(p)) return com.hopeless.infinity.town.TownReg.Wreck.openTrunk(l, q.immutable(), pl); }
            return InteractionResult.PASS; }
        @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.INVISIBLE; }
        @Override public void onRemove(BlockState s, Level l, BlockPos p, BlockState now, boolean moving) {
            if (!l.isClientSide && !s.is(now.getBlock()) && !now.is(CAR.get()) && !now.is(TAXI.get()) && !(now.getBlock() instanceof Car))
                for (BlockPos q : BlockPos.betweenClosed(p.offset(-4, 0, -4), p.offset(4, 0, 4))) { BlockState c = l.getBlockState(q);
                    if (c.getBlock() instanceof Car && Car.cells(q.immutable(), c.getValue(HorizontalDirectionalBlock.FACING)).contains(p)) { l.destroyBlock(q, false); break; } }
            super.onRemove(s, l, p, now, moving); }
    }
    public static String probe() { return "street props: light ticks " + lightTicks + " light starts " + lightStarts + " hydrant uses " + hydrantUses + " bin uses " + binUses + " horns " + horns + " cars broken " + carsBroken; }
}
