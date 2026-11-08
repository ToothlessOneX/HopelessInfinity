package com.hopeless.infinity.town;

import com.hopeless.infinity.HopelessInfinity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/**
 * 0.34.0: the world made self-contained. Every machine, fitting, crop, ore and item the towns used to take from other mods
 * (Mekanism, Create, SecurityCraft, Refurbished Furniture, Farmer's Delight, Cold Sweat, TaCZ, Thirst, Refined Storage, Immersive
 * Engineering, MTR, Sophisticated Storage) is made here, with its model, textures and function:
 *  - power: geothermal generators (lit beside lava; written to GridNet), cables, battery banks (charge from the grid, comparator
 *    output), grid relays (on while the grid is live: a redstone signal out of their front to the motor they face);
 *  - machines: electric motors (run on redstone) turn drive shafts and cogwheels; pumps fill fluid tanks from water near them;
 *    vent fans push what is in front of them; millstones grind (wheat to flour); presses flatten ingots on the depot under them;
 *  - security: reinforced blocks and bars (cannot be broken), keypad doors (a code; the bank's and the evidence room's codes are in
 *    their books), cameras (a redstone signal while a player is in view), panic buttons that ring alarms, electrified fences;
 *  - the hospital's standby generator (runs while the grid is down); transit station terrazzo, ticket machines, clocks;
 *  - furniture (chairs, sofas and toilets to sit on; tables, desks; computers and TVs that switch on; a stove that cooks as a smoker;
 *    a chest freezer, mailbox and trash can that hold things) and the shop and house fittings (produce crates, flour sacks, pots,
 *    a hearth, an icebox, a sewing table (a loom), a workbench (a crafting table), a water filter, server racks);
 *  - crops (cabbage, onion, tomato), ores (tin, zinc, uranium) and the items the loot tables give.
 */
public final class OwnReg {
    private OwnReg() {}
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HopelessInfinity.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HopelessInfinity.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HopelessInfinity.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HopelessInfinity.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HopelessInfinity.MODID);
    static final java.util.List<RegistryObject<Item>> TAB = new java.util.ArrayList<>();
    static final java.util.List<RegistryObject<Block>> WORKERS = new java.util.ArrayList<>(), BOXES = new java.util.ArrayList<>(), KEYPADS = new java.util.ArrayList<>();
    public static final BooleanProperty LIT = BlockStateProperties.LIT, POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty ON = BooleanProperty.create("on");

    // probes
    public static volatile long genLit, genOut, relayChanges, motorChanges, pumped, tankUses, fanPushes, milled, pressed, depotUses, keypadOpens, keypadBad, keypadSet,
        cameraTrips, panics, alarmRings, fenceShocks, standbyStarts, standbyStops, sits, ticketsSold, clockReads, cooked, filtered, wrenched, drinks, skinFills, readings;

    // ================================================================ machines: one block entity type ticks them all
    /** A block that works every period() ticks (its block entity counts; the phase comes from its position). */
    interface Worker extends EntityBlock {
        int period();
        void work(BlockState s, Level l, BlockPos p);
        default boolean client() { return false; }
        @Override default BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new MachineBE(p, s); }
        @SuppressWarnings("unchecked")
        @Override default <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
            if (l.isClientSide && !client()) return null;
            return t == MACHINE.get() ? (BlockEntityTicker<T>) (BlockEntityTicker<MachineBE>) MachineBE::tick : null; }
    }
    public static final class MachineBE extends BlockEntity {
        int t;
        public MachineBE(BlockPos p, BlockState s) { super(MACHINE.get(), p, s); t = (int) Math.floorMod(p.asLong() * 31 + 7, 997L); }
        static void tick(Level l, BlockPos p, BlockState s, MachineBE be) {
            if (!(s.getBlock() instanceof Worker w)) return; be.t++; int per = w.period(); if (per <= 1 || be.t % per == 0) w.work(s, l, p); }
    }
    /** Shafts and cogwheels carry a motor's turning to what touches them. */
    public interface Turns {}
    /** True when p is turned: a redstone signal on it, or a running motor touching it or reached through shafts and cogwheels. */
    public static boolean driven(Level l, BlockPos p) {
        if (l.hasNeighborSignal(p)) return true;
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>(); java.util.Set<BlockPos> seen = new java.util.HashSet<>(); q.add(p); seen.add(p); int n = 0;
        while (!q.isEmpty() && n++ < 48) { BlockPos c = q.poll();
            for (Direction d : Direction.values()) { BlockPos o = c.relative(d); if (!seen.add(o)) continue; BlockState s = l.getBlockState(o);
                if (s.getBlock() instanceof Motor) { if (s.getValue(ON)) return true; } else if (s.getBlock() instanceof Turns) q.add(o); } }
        return false; }

    // ---------------------------------------------------------------- power
    /** Geothermal generator: lit while lava touches it (it stands on the lava under the turbine hall). A lit one keeps the region's grid live. */
    public static final class GeoGen extends CityReg.Fit implements Worker {
        public GeoGen(Properties p) { super(p, false, 0, 0, 0, 16, 16, 16); registerDefaultState(defaultBlockState().setValue(LIT, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, LIT); }
        public int period() { return 100; }
        static boolean lava(Level l, BlockPos p) { for (Direction d : Direction.values()) if (l.getFluidState(p.relative(d)).is(net.minecraft.tags.FluidTags.LAVA)) return true; return false; }
        public void work(BlockState s, Level l, BlockPos p) {
            boolean lit = lava(l, p); if (lit != s.getValue(LIT)) { l.setBlock(p, s.setValue(LIT, lit), 3); if (lit) genLit++; else genOut++; }
            GridNet.set((ServerLevel) l, p, lit); }
        @Override public void neighborChanged(BlockState s, Level l, BlockPos p, Block b, BlockPos f, boolean m) { if (!l.isClientSide) work(s, l, p); }
        @Override public void onRemove(BlockState s, Level l, BlockPos p, BlockState now, boolean moved) {
            if (!s.is(now.getBlock()) && l instanceof ServerLevel sl) GridNet.set(sl, p, false); super.onRemove(s, l, p, now, moved); }
        @Override public void animateTick(BlockState s, Level l, BlockPos p, RandomSource r) {
            if (s.getValue(LIT) && r.nextInt(4) == 0) l.addParticle(ParticleTypes.SMOKE, p.getX() + 0.3 + r.nextDouble() * 0.4, p.getY() + 1.05, p.getZ() + 0.3 + r.nextDouble() * 0.4, 0, 0.03, 0); }
    }
    /** A cable, pipe or shaft along one axis, `r` px from its centre line. */
    public static class Line extends RotatedPillarBlock {
        final VoxelShape x, y, z;
        public Line(Properties p, double r) { super(p); double a = 8 - r, b = 8 + r;
            x = Block.box(0, a, a, 16, b, b); y = Block.box(a, 0, a, b, 16, b); z = Block.box(a, a, 0, b, b, 16); }
        @Override public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) { return switch (s.getValue(AXIS)) { case X -> x; case Y -> y; default -> z; }; }
    }
    public static final class Shaft extends Line implements Turns { public Shaft(Properties p, double r) { super(p, r); } }
    /** Battery bank: charges a step every 5 s while the grid is live, loses one every 5 s while it is not; the comparator reads the charge. */
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 15);
    public static final class Battery extends CityReg.Fit implements Worker {
        public Battery(Properties p) { super(p, false, 1, 0, 2, 15, 16, 14); registerDefaultState(defaultBlockState().setValue(CHARGE, 8)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, CHARGE); }
        public int period() { return 100; }
        public void work(BlockState s, Level l, BlockPos p) { int c = s.getValue(CHARGE), n = Math.max(0, Math.min(15, c + (GridNet.live((ServerLevel) l, p) ? 1 : -1)));
            if (n != c) l.setBlock(p, s.setValue(CHARGE, n), 3); }
        @Override public boolean hasAnalogOutputSignal(BlockState s) { return true; }
        @Override public int getAnalogOutputSignal(BlockState s, Level l, BlockPos p) { return s.getValue(CHARGE); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (!l.isClientSide) pl.displayClientMessage(Component.translatable("message.hopelessinfinity.battery", s.getValue(CHARGE) * 100 / 15), true); return InteractionResult.sidedSuccess(l.isClientSide); }
    }
    /** Grid relay: lit while the grid is live; then a full redstone signal out of its front (into the motor it faces). */
    public static final class Relay extends DirectionalBlock implements Worker {
        public Relay(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(LIT, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, LIT); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getClickedFace().getOpposite()); }
        public int period() { return 40; }
        public void work(BlockState s, Level l, BlockPos p) { boolean on = GridNet.live((ServerLevel) l, p);
            if (on != s.getValue(LIT)) { l.setBlock(p, s.setValue(LIT, on), 3); l.updateNeighborsAt(p.relative(s.getValue(FACING)), this); relayChanges++; } }
        @Override public boolean isSignalSource(BlockState s) { return true; }
        @Override public int getSignal(BlockState s, BlockGetter g, BlockPos p, Direction d) { return s.getValue(LIT) && d == s.getValue(FACING).getOpposite() ? 15 : 0; }
        @Override public int getDirectSignal(BlockState s, BlockGetter g, BlockPos p, Direction d) { return getSignal(s, g, p, d); }
    }

    // ---------------------------------------------------------------- water, air, mechanical
    /** Fluid tank: 0-8 buckets of water. A bucket fills from it or empties into it; tanks stacked in a column fill from the bottom. */
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 8);
    public static final class Tank extends Block {
        public Tank(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(LEVEL, 0)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(LEVEL); }
        @Override public boolean hasAnalogOutputSignal(BlockState s) { return true; }
        @Override public int getAnalogOutputSignal(BlockState s, Level l, BlockPos p) { return (s.getValue(LEVEL) * 15 + 7) / 8; }
        /** Adds one bucket to the lowest tank of the column at p that has room. True when it went in. */
        public static boolean add(Level l, BlockPos p) {
            BlockPos q = p; while (l.getBlockState(q.below()).getBlock() instanceof Tank) q = q.below();
            for (; l.getBlockState(q).getBlock() instanceof Tank; q = q.above()) { BlockState s = l.getBlockState(q); if (s.getValue(LEVEL) < 8) { l.setBlock(q, s.setValue(LEVEL, s.getValue(LEVEL) + 1), 3); return true; } }
            return false; }
        /** Takes one bucket from the highest tank of the column at p that holds water. */
        static boolean take(Level l, BlockPos p) {
            BlockPos q = p; while (l.getBlockState(q.above()).getBlock() instanceof Tank) q = q.above();
            for (; l.getBlockState(q).getBlock() instanceof Tank; q = q.below()) { BlockState s = l.getBlockState(q); if (s.getValue(LEVEL) > 0) { l.setBlock(q, s.setValue(LEVEL, s.getValue(LEVEL) - 1), 3); return true; } }
            return false; }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            ItemStack it = pl.getItemInHand(h);
            if (it.is(Items.WATER_BUCKET)) { if (!l.isClientSide && add(l, p)) { if (!pl.getAbilities().instabuild) pl.setItemInHand(h, new ItemStack(Items.BUCKET)); l.playSound(null, p, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1f, 1f); tankUses++; } return InteractionResult.sidedSuccess(l.isClientSide); }
            if (it.is(Items.BUCKET)) { if (!l.isClientSide && take(l, p)) { pl.setItemInHand(h, ItemUtils.createFilledResult(it, pl, new ItemStack(Items.WATER_BUCKET))); l.playSound(null, p, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1f, 1f); tankUses++; } return InteractionResult.sidedSuccess(l.isClientSide); }
            if (it.is(Items.GLASS_BOTTLE) && s.getValue(LEVEL) > 0) { if (!l.isClientSide) { pl.setItemInHand(h, ItemUtils.createFilledResult(it, pl, net.minecraft.world.item.alchemy.PotionUtils.setPotion(new ItemStack(Items.POTION), net.minecraft.world.item.alchemy.Potions.WATER)));
                if (l.random.nextInt(3) == 0) take(l, p); l.playSound(null, p, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1f, 1f); tankUses++; } return InteractionResult.sidedSuccess(l.isClientSide); }
            if (!l.isClientSide) pl.displayClientMessage(Component.translatable("message.hopelessinfinity.tank", s.getValue(LEVEL)), true);
            return InteractionResult.sidedSuccess(l.isClientSide); }
    }
    /** Pump: while turned, every 2 s it lifts a bucket of water from a water source within 3 blocks (below or beside it) into the tank it touches. */
    public static final class Pump extends CityReg.Fit implements Worker {
        public Pump(Properties p) { super(p, false, 1, 0, 1, 15, 14, 15); registerDefaultState(defaultBlockState().setValue(ON, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, ON); }
        public int period() { return 40; }
        static boolean water(Level l, BlockPos p) {
            for (BlockPos q : BlockPos.betweenClosed(p.offset(-3, -4, -3), p.offset(3, 1, 3))) { FluidState f = l.getFluidState(q); if (f.is(Fluids.WATER) && f.isSource()) return true; } return false; }
        public void work(BlockState s, Level l, BlockPos p) {
            boolean on = driven(l, p); if (on != s.getValue(ON)) l.setBlock(p, s = s.setValue(ON, on), 3); if (!on || !water(l, p)) return;
            for (Direction d : Direction.values()) if (l.getBlockState(p.relative(d)).getBlock() instanceof Tank && Tank.add(l, p.relative(d))) { pumped++; l.playSound(null, p, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.3f, 0.7f); return; } }
    }
    /** Electric motor: runs while it has a redstone signal (a grid relay, a lever); turns what touches it. */
    public static final class Motor extends DirectionalBlock {
        public Motor(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, ON); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getNearestLookingDirection().getOpposite()).setValue(ON, c.getLevel().hasNeighborSignal(c.getClickedPos())); }
        @Override public void neighborChanged(BlockState s, Level l, BlockPos p, Block b, BlockPos f, boolean m) {
            boolean on = l.hasNeighborSignal(p); if (on != s.getValue(ON)) { l.setBlock(p, s.setValue(ON, on), 3); motorChanges++; } }
        @Override public void animateTick(BlockState s, Level l, BlockPos p, RandomSource r) { if (s.getValue(ON) && r.nextInt(8) == 0) l.playLocalSound(p, SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 0.15f, 0.5f, false); }
    }
    /** Vent fan: while turned, pushes what is in the 8 blocks in front of it (stopped by a solid block). */
    public static final class Fan extends DirectionalBlock implements Worker {
        public Fan(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, ON); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getNearestLookingDirection().getOpposite()); }
        public int period() { return 1; }
        @Override public boolean client() { return true; }
        public void work(BlockState s, Level l, BlockPos p) {
            if (!l.isClientSide && l.getGameTime() % 20 == Math.floorMod(p.asLong(), 20L)) { boolean on = driven(l, p); if (on != s.getValue(ON)) l.setBlock(p, s = s.setValue(ON, on), 3); }
            if (!s.getValue(ON)) return; Direction d = s.getValue(FACING); int n = 0;
            while (n < 8 && !l.getBlockState(p.relative(d, n + 1)).isCollisionShapeFullBlock(l, p.relative(d, n + 1))) n++;
            if (n == 0) return;
            AABB box = new AABB(p.relative(d)).minmax(new AABB(p.relative(d, n)));
            for (Entity e : l.getEntities((Entity) null, box, e -> !e.isSpectator() && !(e instanceof Player pl && pl.getAbilities().flying))) {
                if (e instanceof Player && !l.isClientSide) continue;                // a player moves himself: pushed on his own client
                if (!(e instanceof Player) && l.isClientSide) continue;
                Vec3 v = e.getDeltaMovement(); double k = d == Direction.UP ? 0.14 : 0.07;
                Vec3 add = new Vec3(d.getStepX() * k, d.getStepY() * k, d.getStepZ() * k); Vec3 nv = v.add(add);
                double sp = nv.dot(new Vec3(d.getStepX(), d.getStepY(), d.getStepZ())); if (sp > 0.7) nv = nv.subtract(add.scale((sp - 0.7) / k));
                e.setDeltaMovement(nv); if (d == Direction.UP) e.resetFallDistance(); e.hurtMarked = true; fanPushes++; } }
    }
    /** Millstone: grinds wheat to flour (also sugar cane to sugar, a bone to bone meal) - by hand, or while turned, what is dropped on it. */
    public static final class Millstone extends Block implements Worker {
        static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 12, 15);
        public Millstone(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(ON, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(ON); }
        @Override public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) { return SHAPE; }
        static ItemStack grind(ItemStack in) {
            if (in.is(Items.WHEAT)) return new ItemStack(FLOUR.get()); if (in.is(Items.SUGAR_CANE)) return new ItemStack(Items.SUGAR); if (in.is(Items.BONE)) return new ItemStack(Items.BONE_MEAL, 3);
            if (in.is(Items.GRAVEL)) return new ItemStack(Items.FLINT); return ItemStack.EMPTY; }
        public int period() { return 20; }
        public void work(BlockState s, Level l, BlockPos p) {
            boolean on = driven(l, p); if (on != s.getValue(ON)) l.setBlock(p, s = s.setValue(ON, on), 3); if (!on) return;
            for (ItemEntity ie : l.getEntitiesOfClass(ItemEntity.class, new AABB(p).expandTowards(0, 0.6, 0))) { ItemStack out = grind(ie.getItem()); if (out.isEmpty()) continue;
                ItemStack left = ie.getItem().copy(); left.shrink(1); if (left.isEmpty()) ie.discard(); else ie.setItem(left);
                ItemEntity o = new ItemEntity(l, p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, out); o.setDeltaMovement(0, 0.1, 0); l.addFreshEntity(o);
                l.playSound(null, p, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.4f, 1.2f); milled++; return; } }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            ItemStack it = pl.getItemInHand(h), out = grind(it); if (out.isEmpty()) return InteractionResult.PASS;
            if (!l.isClientSide) { if (!pl.getAbilities().instabuild) it.shrink(1); if (!pl.getInventory().add(out)) pl.drop(out, false); l.playSound(null, p, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.6f, 1f); milled++; }
            return InteractionResult.sidedSuccess(l.isClientSide); }
    }
    /** Press: while turned, every 2 s it flattens the ingot on the depot under it into a plate. */
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final class Press extends CityReg.Fit implements Worker, Turns {   // a press passes the line shaft's turning on to the next shaft
        public Press(Properties p) { super(p, false, 1, 0, 1, 15, 16, 15); registerDefaultState(defaultBlockState().setValue(DOWN, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, DOWN); }
        public int period() { return 10; }
        static ItemStack plate(ItemStack in) { if (in.is(Items.IRON_INGOT)) return new ItemStack(IRON_PLATE.get()); if (in.is(Items.COPPER_INGOT)) return new ItemStack(COPPER_PLATE.get()); return ItemStack.EMPTY; }
        public void work(BlockState s, Level l, BlockPos p) {
            if (s.getValue(DOWN)) { l.setBlock(p, s.setValue(DOWN, false), 3); return; }
            if ((l.getGameTime() / 10) % 4 != 0 || !driven(l, p)) return;
            for (int dy = 1; dy <= 2; dy++) if (l.getBlockEntity(p.below(dy)) instanceof DepotBE d) { ItemStack out = plate(d.item); if (out.isEmpty()) return;
                out.setCount(d.item.getCount()); d.item = out; d.changed(); l.setBlock(p, s.setValue(DOWN, true), 3); l.playSound(null, p, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.35f, 1.4f); pressed++; return; } }
    }
    /** Depot: holds one stack, shown on top; a hopper can fill and empty it. */
    public static final class Depot extends Block implements EntityBlock {
        static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 13, 16);
        public Depot(Properties p) { super(p); }
        @Override public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) { return SHAPE; }
        @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new DepotBE(p, s); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (!(l.getBlockEntity(p) instanceof DepotBE d)) return InteractionResult.PASS; ItemStack it = pl.getItemInHand(h);
            if (!l.isClientSide) { if (!d.item.isEmpty()) { ItemStack got = d.item; d.item = ItemStack.EMPTY; if (!pl.getInventory().add(got)) pl.drop(got, false); }
                else if (!it.isEmpty()) { d.item = it.split(it.getCount()); } d.changed(); depotUses++; }
            return InteractionResult.sidedSuccess(l.isClientSide); }
        @Override public void onRemove(BlockState s, Level l, BlockPos p, BlockState now, boolean moved) {
            if (!s.is(now.getBlock()) && l.getBlockEntity(p) instanceof DepotBE d) Containers.dropItemStack(l, p.getX(), p.getY(), p.getZ(), d.item); super.onRemove(s, l, p, now, moved); }
    }
    public static final class DepotBE extends BlockEntity implements net.minecraft.world.Container {
        public ItemStack item = ItemStack.EMPTY;
        public DepotBE(BlockPos p, BlockState s) { super(DEPOT_BE.get(), p, s); }
        void changed() { setChanged(); if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
        @Override protected void saveAdditional(CompoundTag t) { super.saveAdditional(t); t.put("Item", item.save(new CompoundTag())); }
        @Override public void load(CompoundTag t) { super.load(t); item = ItemStack.of(t.getCompound("Item")); }
        @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
        @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() { return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this); }
        public int getContainerSize() { return 1; } public boolean isEmpty() { return item.isEmpty(); } public ItemStack getItem(int i) { return item; }
        public ItemStack removeItem(int i, int n) { ItemStack r = item.split(n); changed(); return r; }
        public ItemStack removeItemNoUpdate(int i) { ItemStack r = item; item = ItemStack.EMPTY; return r; }
        public void setItem(int i, ItemStack s) { item = s; changed(); }
        public boolean stillValid(Player p) { return net.minecraft.world.Container.stillValidBlockEntity(this, p); }
        public void clearContent() { item = ItemStack.EMPTY; }
    }

    // ---------------------------------------------------------------- security
    /** Keypad door: an iron door that opens for 4 s to the right code (typed on its keypad). A door with no code takes the first one typed. */
    public static final class KeypadDoor extends DoorBlock implements EntityBlock {
        public KeypadDoor(Properties p) { super(p, BlockSetType.IRON); }
        @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new KeypadBE(p, s); }
        static BlockPos lower(BlockState s, BlockPos p) { return s.getValue(HALF) == DoubleBlockHalf.UPPER ? p.below() : p; }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (isOpen(s)) { if (!l.isClientSide) setOpen(pl, l, s, p, false); return InteractionResult.sidedSuccess(l.isClientSide); }
            if (l.isClientSide) net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> com.hopeless.infinity.town.client.OwnClient.keypad(lower(s, p)));
            return InteractionResult.sidedSuccess(l.isClientSide); }
        @Override public void neighborChanged(BlockState s, Level l, BlockPos p, Block b, BlockPos f, boolean m) {}   // redstone does not open it
        @Override public void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
            if (isOpen(s) && l.getBlockEntity(p) instanceof KeypadBE be && l.getGameTime() >= be.openedAt + 80) setOpen(null, l, s, p, false); }
        /** Only the player who set the code breaks it (a town's doors have no such player: they cannot be broken, like the vault walls). */
        @Override public float getDestroyProgress(BlockState s, Player pl, BlockGetter g, BlockPos p) {
            if (g.getBlockEntity(lower(s, p)) instanceof KeypadBE be && be.owner != null && be.owner.equals(pl.getUUID())) return super.getDestroyProgress(s, pl, g, p);
            if (g.getBlockEntity(lower(s, p)) instanceof KeypadBE be && be.code.isEmpty() && be.owner == null) return super.getDestroyProgress(s, pl, g, p);
            return 0f; }
        @Override public float getExplosionResistance(BlockState s, BlockGetter g, BlockPos p, net.minecraft.world.level.Explosion e) { return 3600000f; }
        /** The server's answer to a typed code (OwnNet). */
        public static void tryCode(ServerLevel l, BlockPos p, Player pl, String code) {
            BlockState s = l.getBlockState(p); if (!(s.getBlock() instanceof KeypadDoor kd) || !(l.getBlockEntity(p) instanceof KeypadBE be)) return;
            if (be.code.isEmpty()) { be.code = code; be.owner = pl.getUUID(); be.setChanged(); keypadSet++; pl.displayClientMessage(Component.translatable("message.hopelessinfinity.keypad_set"), true); }
            else if (!be.code.equals(code)) { keypadBad++; l.playSound(null, p, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1f, 0.5f); pl.displayClientMessage(Component.translatable("message.hopelessinfinity.keypad_bad"), true); return; }
            be.openedAt = l.getGameTime(); kd.setOpen(pl, l, s, p, true); l.scheduleTick(p, kd, 80); keypadOpens++; l.playSound(null, p, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1f, 1.6f); }
    }
    public static final class KeypadBE extends BlockEntity {
        public String code = ""; public java.util.UUID owner; long openedAt;
        public KeypadBE(BlockPos p, BlockState s) { super(KEYPAD_BE.get(), p, s); }
        @Override protected void saveAdditional(CompoundTag t) { super.saveAdditional(t); t.putString("passcode", code); if (owner != null) t.putUUID("owner", owner); }
        @Override public void load(CompoundTag t) { super.load(t); code = t.getString("passcode"); owner = t.hasUUID("owner") ? t.getUUID("owner") : null; }
    }
    /** Security camera: its redstone signal is on while a player is in view (10 blocks, a 120 degree cone, nothing solid between). */
    public static final class Camera extends DirectionalBlock implements Worker {
        static final VoxelShape[] SHAPES = new VoxelShape[6];
        static { for (Direction d : Direction.values()) { double[] b = {5, 5, 5, 11, 11, 11}; int ax = d.getAxis().ordinal(); if (d.getAxisDirection() == Direction.AxisDirection.POSITIVE) { b[ax] = 0; b[ax + 3] = 8; } else { b[ax] = 8; b[ax + 3] = 16; }
            SHAPES[d.get3DDataValue()] = Block.box(b[0], b[1], b[2], b[3], b[4], b[5]); } }
        public Camera(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(POWERED, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, POWERED); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getClickedFace()); }
        @Override public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) { return SHAPES[s.getValue(FACING).get3DDataValue()]; }
        public int period() { return 10; }
        public void work(BlockState s, Level l, BlockPos p) {
            Direction d = s.getValue(FACING); Vec3 eye = Vec3.atCenterOf(p), look = new Vec3(d.getStepX(), d.getStepY(), d.getStepZ()); boolean see = false;
            for (Player pl : l.players()) { if (pl.isSpectator()) continue; Vec3 to = pl.getEyePosition().subtract(eye); double len = to.length(); if (len > 10 || len < 0.1) continue;
                if (to.dot(look) / len < 0.5) continue;
                BlockHitResult hr = l.clip(new ClipContext(eye.add(look.scale(0.6)), pl.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
                if (hr.getType() == HitResult.Type.MISS) { see = true; break; } }
            if (see != s.getValue(POWERED)) { l.setBlock(p, s.setValue(POWERED, see), 3); l.updateNeighborsAt(p, this); if (see) cameraTrips++; } }
        @Override public boolean isSignalSource(BlockState s) { return true; }
        @Override public int getSignal(BlockState s, BlockGetter g, BlockPos p, Direction d) { return s.getValue(POWERED) ? 15 : 0; }
    }
    /** Panic button: held in for 5 s; rings every alarm within 24 blocks for 30 s. */
    public static final class Panic extends ButtonBlock {
        public Panic(Properties p) { super(p, BlockSetType.IRON, 100, false); }
        @Override public void press(BlockState s, Level l, BlockPos p) { super.press(s, l, p); if (!l.isClientSide) { panics++; Alarm.ring(l, p, 24, 600); } }
    }
    static final java.util.Map<Long, Long> RINGING = new java.util.concurrent.ConcurrentHashMap<>();
    /** Alarm: sounds while it has a redstone signal or after a panic button, in the NFPA 72 (18.4.2) temporal-3 pattern - 0.5 s on,
     *  0.5 s off, three times, then 1.5 s off - and its light flashes with the tone (the visible signal ADA 702 / NFPA 72 ask for). */
    public static final class Alarm extends CityReg.Fit implements Worker {
        public Alarm(Properties p) { super(p, false, 4, 4, 12, 12, 12, 16); registerDefaultState(defaultBlockState().setValue(LIT, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, LIT); }
        static void ring(Level l, BlockPos p, int r, int ticks) { long until = l.getGameTime() + ticks;
            for (BlockPos q : BlockPos.betweenClosed(p.offset(-r, -6, -r), p.offset(r, 6, r))) if (l.getBlockState(q).getBlock() instanceof Alarm) RINGING.put(q.asLong(), until); }
        public int period() { return 1; }
        /** Temporal-3 in 0.5 s slots: on, off, on, off, on, off, off, off (a 4 s cycle). */
        static boolean slotOn(long gameTime) { int k = (int) Math.floorMod(gameTime / 10, 8L); return k == 0 || k == 2 || k == 4; }
        public void work(BlockState s, Level l, BlockPos p) {
            if (l.getGameTime() % 10 != 0) return;                       // every alarm on the same 0.5 s slots: they flash together (NFPA 72 18.5.5.5.2)
            Long u = RINGING.get(p.asLong()); boolean ring = l.hasNeighborSignal(p) || u != null && u > l.getGameTime(); if (u != null && u <= l.getGameTime()) RINGING.remove(p.asLong());
            boolean on = ring && slotOn(l.getGameTime());
            if (on != s.getValue(LIT)) l.setBlock(p, s.setValue(LIT, on), 3);
            if (on) { l.playSound(null, p, ALARM_SOUND.get(), SoundSource.BLOCKS, 2f, 1f); alarmRings++; } }
    }
    /** Electrified fence: shocks what touches its bars (2 damage, a push back). */
    public static final class ElectricFence extends IronBarsBlock {
        public ElectricFence(Properties p) { super(p); }
        @Override public void entityInside(BlockState s, Level l, BlockPos p, Entity e) {
            if (l.isClientSide || !(e instanceof LivingEntity le) || le.invulnerableTime > 0) return;
            AABB bb = e.getBoundingBox(); boolean touch = false; for (AABB b : s.getShape(l, p).toAabbs()) if (b.move(p).inflate(0.06).intersects(bb)) { touch = true; break; }
            if (!touch) return; if (le.hurt(l.damageSources().lightningBolt(), 2f)) { fenceShocks++; Vec3 away = e.position().subtract(Vec3.atCenterOf(p)).multiply(1, 0, 1).normalize().scale(0.5);
                le.push(away.x, 0.15, away.z); le.hurtMarked = true; l.playSound(null, p, SoundEvents.BEE_STING, SoundSource.BLOCKS, 0.8f, 0.6f); } }
    }
    /** Standby generator: starts when the grid goes down (it powers the hospital's emergency circuit: a redstone signal while it runs). */
    public static final class Standby extends CityReg.Fit implements Worker {
        public Standby(Properties p) { super(p, false, 0, 0, 1, 16, 14, 15); registerDefaultState(defaultBlockState().setValue(LIT, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, LIT); }
        public int period() { return 40; }
        public void work(BlockState s, Level l, BlockPos p) { boolean run = !GridNet.live((ServerLevel) l, p);
            if (run != s.getValue(LIT)) { l.setBlock(p, s.setValue(LIT, run), 3); l.updateNeighborsAt(p, this); if (run) standbyStarts++; else standbyStops++; }
            if (run) l.playSound(null, p, CitySounds.ENGINE_DIESEL.get(), SoundSource.BLOCKS, 0.5f, 0.8f); }
        @Override public boolean isSignalSource(BlockState s) { return true; }
        @Override public int getSignal(BlockState s, BlockGetter g, BlockPos p, Direction d) { return s.getValue(LIT) ? 15 : 0; }
    }

    // ---------------------------------------------------------------- transit
    public static final class TicketMachine extends CityReg.Tall {
        public TicketMachine(Properties p) { super(p, false, 2, 0, 3, 14, 16, 16); }
        @Override InteractionResult useLower(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h) {
            ItemStack it = pl.getItemInHand(h); int n = it.is(Items.EMERALD) ? 4 : it.is(Items.GOLD_NUGGET) ? 1 : 0;
            if (n == 0) { if (!l.isClientSide) pl.displayClientMessage(Component.translatable("message.hopelessinfinity.ticket_price"), true); return InteractionResult.sidedSuccess(l.isClientSide); }
            if (!l.isClientSide) { if (!pl.getAbilities().instabuild) it.shrink(1); CityReg.give(pl, h, new ItemStack(TICKET.get(), n)); ticketsSold += n; l.playSound(null, p, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 0.8f, 1.4f); }
            return InteractionResult.sidedSuccess(l.isClientSide); }
    }
    /** "06:00" for a game time (0 = 6 am). */
    public static String clock(long dayTime) { long t = Math.floorMod(dayTime + 6000, 24000L); return String.format("%02d:%02d", t / 1000, (t % 1000) * 60 / 1000); }
    public static final class StationClock extends CityReg.Fit {
        public StationClock(Properties p) { super(p, false, 1, 1, 6, 15, 15, 10); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (!l.isClientSide) { pl.displayClientMessage(Component.translatable("message.hopelessinfinity.clock", clock(l.getDayTime())), true); clockReads++; } return InteractionResult.sidedSuccess(l.isClientSide); }
    }

    // ---------------------------------------------------------------- furniture
    /** A seat: right-click to sit (the hips `h` blocks over the floor of the block). */
    public static class Seat extends CityReg.Fit {
        final double h;
        public Seat(Properties p, double h, double... box) { super(p, false, box); this.h = h; }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h0, BlockHitResult hit) {
            if (pl.isPassenger() || pl.isShiftKeyDown()) return InteractionResult.PASS;
            if (!l.isClientSide) { if (!l.getEntitiesOfClass(SeatEntity.class, new AABB(p)).isEmpty()) return InteractionResult.CONSUME;
                SeatEntity e = new SeatEntity(SEAT.get(), l); e.setPos(p.getX() + 0.5, p.getY() + h, p.getZ() + 0.5); e.setYRot(s.getValue(FACING).toYRot()); l.addFreshEntity(e);
                pl.startRiding(e); pl.setYRot(s.getValue(FACING).toYRot()); sits++; }
            return InteractionResult.sidedSuccess(l.isClientSide); }
        /** 0.38.0: a townsperson takes a free seat within 3 blocks (chairs, sofas): true when they sat down. */
        public static boolean seatNear(Level l, BlockPos at, Entity who) {
            for (BlockPos q : BlockPos.betweenClosed(at.offset(-3, -1, -3), at.offset(3, 1, 3))) { BlockState s = l.getBlockState(q);
                if (!(s.getBlock() instanceof Seat st) || !l.getEntitiesOfClass(SeatEntity.class, new AABB(q)).isEmpty()) continue;
                SeatEntity e = new SeatEntity(SEAT.get(), l); e.setPos(q.getX() + 0.5, q.getY() + st.h, q.getZ() + 0.5); e.setYRot(s.getValue(FACING).toYRot()); l.addFreshEntity(e);
                who.startRiding(e, true); who.setYRot(s.getValue(FACING).toYRot()); npcSits++; return true; }
            return false; }
    }
    public static volatile long npcSits;
    public static final class SeatEntity extends Entity {
        public SeatEntity(EntityType<?> t, Level l) { super(t, l); noPhysics = true; }
        @Override protected void defineSynchedData() {}
        @Override protected void readAdditionalSaveData(CompoundTag t) {} @Override protected void addAdditionalSaveData(CompoundTag t) {}
        @Override public void tick() { super.tick(); if (!level().isClientSide && (getPassengers().isEmpty() || !(level().getBlockState(blockPosition()).getBlock() instanceof Seat))) discard(); }
        @Override protected void positionRider(Entity p, Entity.MoveFunction f) { f.accept(p, getX(), getY() - 0.70, getZ()); }   // the feet 0.70 under the hips, as the vehicles measured
        @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() { return new net.minecraft.network.protocol.game.ClientboundAddEntityPacket(this); }
    }
    /** Computer, TV: right-click switches it on or off (lit when on). */
    public static final class Screen extends CityReg.Fit {
        public Screen(Properties p, double... box) { super(p, false, box); registerDefaultState(defaultBlockState().setValue(ON, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, ON); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (!l.isClientSide) { l.setBlock(p, s.cycle(ON), 3); l.playSound(null, p, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4f, 1.6f); } return InteractionResult.sidedSuccess(l.isClientSide); }
    }
    /** Kitchen stove: cooks food as a smoker does (fuel below, food on top). */
    public static final class Stove extends AbstractFurnaceBlock {
        public Stove(Properties p) { super(p); }
        @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new StoveBE(p, s); }
        @Override protected void openContainer(Level l, BlockPos p, Player pl) { if (l.getBlockEntity(p) instanceof StoveBE b) pl.openMenu(b); }
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) { return createFurnaceTicker(l, t, STOVE_BE.get()); }
        @Override public void animateTick(BlockState s, Level l, BlockPos p, RandomSource r) { if (s.getValue(LIT) && r.nextInt(6) == 0) l.addParticle(ParticleTypes.SMOKE, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 0, 0.02, 0); }
    }
    public static final class StoveBE extends AbstractFurnaceBlockEntity {
        public StoveBE(BlockPos p, BlockState s) { super(STOVE_BE.get(), p, s, RecipeType.SMOKING); }
        @Override protected Component getDefaultName() { return Component.translatable("block.hopelessinfinity.stove"); }
        @Override protected AbstractContainerMenu createMenu(int id, Inventory inv) { cooked++; return new SmokerMenu(id, inv, this, this.dataAccess); }
    }
    /** A container fitting (chest freezer, mailbox, trash can, icebox): 27 slots. */
    public static final class Box extends CityReg.Fit implements EntityBlock {
        public Box(Properties p, double... box) { super(p, false, box); }
        @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new OwnBox(p, s); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (!l.isClientSide && l.getBlockEntity(p) instanceof OwnBox b) { pl.openMenu(b); l.playSound(null, p, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.5f, 1.3f); }
            return InteractionResult.sidedSuccess(l.isClientSide); }
        @Override public void onRemove(BlockState s, Level l, BlockPos p, BlockState now, boolean moved) {
            if (!s.is(now.getBlock()) && l.getBlockEntity(p) instanceof OwnBox b) Containers.dropContents(l, p, b); super.onRemove(s, l, p, now, moved); }
    }
    public static final class OwnBox extends LootBox { public OwnBox(BlockPos p, BlockState s) { super(OWN_BOX.get(), p, s); } }

    // ---------------------------------------------------------------- shop and house fittings
    public enum Crop implements StringRepresentable { CABBAGE, TOMATO, ONION, CARROT, POTATO, BEETROOT;
        @Override public String getSerializedName() { return name().toLowerCase(); } }
    public static final EnumProperty<Crop> CROP = EnumProperty.create("crop", Crop.class);
    /** Produce crate: a crate of nine of a crop (it drops them when broken). */
    public static final class Crate extends CityReg.Fit {
        public Crate(Properties p) { super(p, false, 0, 0, 0, 16, 16, 16); registerDefaultState(defaultBlockState().setValue(CROP, Crop.CARROT)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, CROP); }
    }
    /** Hearth: a fireplace; flint and steel lights it, an empty hand puts it out. Lit, it is a strong light. */
    public static final class Hearth extends CityReg.Fit {
        public Hearth(Properties p) { super(p, false, 0, 0, 0, 16, 16, 16); registerDefaultState(defaultBlockState().setValue(LIT, true)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, LIT); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return super.getStateForPlacement(c).setValue(LIT, false); }   // placed cold; flint and steel lights it
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            ItemStack it = pl.getItemInHand(h);
            if (!s.getValue(LIT) && (it.is(Items.FLINT_AND_STEEL) || it.is(Items.FIRE_CHARGE))) { if (!l.isClientSide) { l.setBlock(p, s.setValue(LIT, true), 3); l.playSound(null, p, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1f, 1f);
                if (it.is(Items.FIRE_CHARGE)) it.shrink(1); else it.hurtAndBreak(1, pl, e -> e.broadcastBreakEvent(h)); } return InteractionResult.sidedSuccess(l.isClientSide); }
            if (s.getValue(LIT) && it.isEmpty()) { if (!l.isClientSide) { l.setBlock(p, s.setValue(LIT, false), 3); l.playSound(null, p, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1f); } return InteractionResult.sidedSuccess(l.isClientSide); }
            return InteractionResult.PASS; }
        @Override public void animateTick(BlockState s, Level l, BlockPos p, RandomSource r) {
            if (!s.getValue(LIT)) return; if (r.nextInt(10) == 0) l.playLocalSound(p, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.6f, 1f, false);
            Direction f = s.getValue(FACING); l.addParticle(ParticleTypes.FLAME, p.getX() + 0.5 + f.getStepX() * 0.2 + (r.nextDouble() - 0.5) * 0.4, p.getY() + 0.3, p.getZ() + 0.5 + f.getStepZ() * 0.2 + (r.nextDouble() - 0.5) * 0.4, 0, 0.01, 0); }
    }
    /** Sewing table (a loom) and workbench (a crafting table): the vanilla menus, kept open by this block. */
    public static final class Station extends CityReg.Fit {
        final boolean loom;
        public Station(Properties p, boolean loom) { super(p, false, 0, 0, 0, 16, 16, 16); this.loom = loom; }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (!l.isClientSide) { ContainerLevelAccess a = ContainerLevelAccess.create(l, p);
                pl.openMenu(new SimpleMenuProvider((id, inv, who) -> loom ? new Loom(id, inv, a, p) : new Work(id, inv, a, p), Component.translatable(loom ? "container.loom" : "container.crafting"))); }
            return InteractionResult.sidedSuccess(l.isClientSide); }
    }
    static final class Loom extends LoomMenu { final BlockPos at; Loom(int id, Inventory inv, ContainerLevelAccess a, BlockPos at) { super(id, inv, a); this.at = at; }
        @Override public boolean stillValid(Player p) { return p.distanceToSqr(Vec3.atCenterOf(at)) < 64; } }
    static final class Work extends CraftingMenu { final BlockPos at; Work(int id, Inventory inv, ContainerLevelAccess a, BlockPos at) { super(id, inv, a); this.at = at; }
        @Override public boolean stillValid(Player p) { return p.distanceToSqr(Vec3.atCenterOf(at)) < 64; } }
    /** Water filter: a sand and gravel filter; a bucket or a bottle fills with clean water from it. */
    public static final class Filter extends CityReg.Fit {
        public Filter(Properties p) { super(p, false, 2, 0, 2, 14, 16, 14); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (CityReg.fillWater(l, p, pl, h)) { if (!l.isClientSide) filtered++; return InteractionResult.sidedSuccess(l.isClientSide); } return InteractionResult.PASS; }
    }
    public static final class Crop3 extends CropBlock {
        final Supplier<? extends ItemLike> seed;
        public Crop3(Properties p, Supplier<? extends ItemLike> seed) { super(p); this.seed = seed; }
        @Override protected ItemLike getBaseSeedId() { return seed.get(); }
    }

    // ---------------------------------------------------------------- items
    /** Wrench: turns a fitting a quarter round (sneak: the other way). */
    public static final class Wrench extends Item {
        public Wrench(Properties p) { super(p); }
        @Override public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext c) {
            Level l = c.getLevel(); BlockPos p = c.getClickedPos(); BlockState s = l.getBlockState(p);
            if (l instanceof ServerLevel sl && c.getPlayer() instanceof net.minecraft.server.level.ServerPlayer sp && com.hopeless.infinity.game.Outage.wrench(sl, sp, p)) return InteractionResult.CONSUME;   // 0.38.0: a repair
            if (!ForgeRegistries.BLOCKS.getKey(s.getBlock()).getNamespace().equals(HopelessInfinity.MODID)) return InteractionResult.PASS;
            BlockState n = null;
            if (s.hasProperty(BlockStateProperties.ATTACH_FACE)) return InteractionResult.PASS;   // a button turned off its wall would break off
            if (s.hasProperty(HorizontalDirectionalBlock.FACING) && !s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) { Direction f = s.getValue(HorizontalDirectionalBlock.FACING); n = s.setValue(HorizontalDirectionalBlock.FACING, c.getPlayer() != null && c.getPlayer().isShiftKeyDown() ? f.getCounterClockWise() : f.getClockWise()); }
            else if (s.hasProperty(DirectionalBlock.FACING)) { Direction[] all = Direction.values(); n = s.setValue(DirectionalBlock.FACING, all[(s.getValue(DirectionalBlock.FACING).ordinal() + 1) % 6]); }
            else if (s.hasProperty(RotatedPillarBlock.AXIS)) { Direction.Axis a = s.getValue(RotatedPillarBlock.AXIS); n = s.setValue(RotatedPillarBlock.AXIS, Direction.Axis.values()[(a.ordinal() + 1) % 3]); }
            if (n == null) return InteractionResult.PASS;
            if (!l.isClientSide) { l.setBlock(p, n, 3); l.playSound(null, p, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.5f, 1.6f); wrenched++; }
            return InteractionResult.sidedSuccess(l.isClientSide); }
    }
    /** Waterskin: holds four drinks; fill it at water. A drink puts out fire on you. */
    public static final class Waterskin extends Item {
        public Waterskin(Properties p) { super(p); }
        static int drinks(ItemStack s) { return s.hasTag() ? s.getTag().getInt("Drinks") : 0; }
        @Override public InteractionResultHolder<ItemStack> use(Level l, Player pl, InteractionHand h) {
            ItemStack s = pl.getItemInHand(h); BlockHitResult hit = getPlayerPOVHitResult(l, pl, ClipContext.Fluid.SOURCE_ONLY);
            if (hit.getType() == HitResult.Type.BLOCK && l.getFluidState(hit.getBlockPos()).is(net.minecraft.tags.FluidTags.WATER) && drinks(s) < 4) {
                if (!l.isClientSide) { s.getOrCreateTag().putInt("Drinks", 4); skinFills++; } l.playSound(pl, pl.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1f, 0.8f); return InteractionResultHolder.sidedSuccess(s, l.isClientSide); }
            if (drinks(s) > 0) { pl.startUsingItem(h); return InteractionResultHolder.consume(s); }
            return InteractionResultHolder.pass(s); }
        @Override public int getUseDuration(ItemStack s) { return 32; }
        @Override public UseAnim getUseAnimation(ItemStack s) { return UseAnim.DRINK; }
        @Override public ItemStack finishUsingItem(ItemStack s, Level l, LivingEntity e) {
            if (!l.isClientSide) { s.getOrCreateTag().putInt("Drinks", Math.max(0, drinks(s) - 1)); e.clearFire(); drinks++; } return s; }
        @Override public boolean isBarVisible(ItemStack s) { return true; }
        @Override public int getBarWidth(ItemStack s) { return Math.round(13f * drinks(s) / 4f); }
        @Override public int getBarColor(ItemStack s) { return 0x3F76E4; }
    }
    /** Thermometer: the air temperature where you stand (the Deep's air model: depth, lava, mines). */
    public static final class Thermometer extends Item {
        public Thermometer(Properties p) { super(p); }
        @Override public InteractionResultHolder<ItemStack> use(Level l, Player pl, InteractionHand h) {
            if (l instanceof ServerLevel sl) { double c = com.hopeless.infinity.life.Air.read(sl, pl.blockPosition()).temp; readings++;
                pl.displayClientMessage(Component.translatable("message.hopelessinfinity.thermometer", Math.round(c * 9 / 5 + 32), Math.round(c)), true); }
            return InteractionResultHolder.sidedSuccess(pl.getItemInHand(h), l.isClientSide); }
    }

    // ================================================================ registration
    static BlockBehaviour.Properties metal(MapColor c) { return CityReg.metal(c); }
    static BlockBehaviour.Properties wood(MapColor c) { return BlockBehaviour.Properties.of().mapColor(c).strength(2f, 3f).sound(SoundType.WOOD).noOcclusion(); }
    static BlockBehaviour.Properties cloth(MapColor c) { return BlockBehaviour.Properties.of().mapColor(c).strength(0.8f).sound(SoundType.WOOL).noOcclusion(); }
    static BlockBehaviour.Properties hard() { return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1f, 3600000f).noLootTable().sound(SoundType.METAL); }
    static RegistryObject<Block> reg(String id, Supplier<Block> s) { return reg(id, s, true); }
    static RegistryObject<Block> reg(String id, Supplier<Block> s, boolean item) {
        RegistryObject<Block> b = BLOCKS.register(id, s); if (item) TAB.add(ITEMS.register(id, () -> new BlockItem(b.get(), new Item.Properties()))); return b; }
    static RegistryObject<Block> worker(String id, Supplier<Block> s) { RegistryObject<Block> b = reg(id, s); WORKERS.add(b); return b; }
    static RegistryObject<Block> box(String id, Supplier<Block> s) { RegistryObject<Block> b = reg(id, s); BOXES.add(b); return b; }
    static RegistryObject<Item> item(String id, Supplier<Item> s) { RegistryObject<Item> i = ITEMS.register(id, s); TAB.add(i); return i; }
    static RegistryObject<Item> plain(String id) { return item(id, () -> new Item(new Item.Properties())); }
    static RegistryObject<Item> food(String id, int n, float sat) { return item(id, () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(n).saturationMod(sat).build()))); }

    public static final RegistryObject<SoundEvent> ALARM_SOUND = SOUNDS.register("block.alarm", () -> SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(HopelessInfinity.MODID, "block.alarm")));
    public static final RegistryObject<EntityType<SeatEntity>> SEAT = ENTITIES.register("seat", () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC).sized(0.2f, 0.2f).noSummon().clientTrackingRange(8).build("seat"));

    // power
    public static final RegistryObject<Block> GEO_GEN = worker("geothermal_generator", () -> new GeoGen(metal(MapColor.COLOR_GRAY).strength(5f, 12f).lightLevel(s -> s.getValue(LIT) ? 9 : 0)));
    public static final RegistryObject<Block> POWER_CABLE = reg("power_cable", () -> new Line(metal(MapColor.COLOR_BLACK).strength(1f, 3f), 2.5));
    public static final RegistryObject<Block> BATTERY = worker("battery_bank", () -> new Battery(metal(MapColor.COLOR_GRAY)));
    public static final RegistryObject<Block> GRID_RELAY = worker("grid_relay", () -> new Relay(metal(MapColor.COLOR_LIGHT_GRAY).lightLevel(s -> s.getValue(LIT) ? 5 : 0)));
    // water, air, mechanical
    public static final RegistryObject<Block> FLUID_TANK = reg("fluid_tank", () -> new Tank(metal(MapColor.METAL).sound(SoundType.GLASS)));
    public static final RegistryObject<Block> OXYGEN_TANK = reg("oxygen_tank", () -> new CityReg.Fit(metal(MapColor.SNOW), false, 2, 0, 2, 14, 16, 14));
    public static final RegistryObject<Block> PIPE = reg("pipe", () -> new Line(metal(MapColor.COLOR_LIGHT_GRAY).strength(1.5f, 6f), 4));
    public static final RegistryObject<Block> PUMP = worker("pump", () -> new Pump(metal(MapColor.COLOR_BLUE)));
    public static final RegistryObject<Block> MOTOR = reg("electric_motor", () -> new Motor(metal(MapColor.COLOR_GREEN)));
    public static final RegistryObject<Block> FAN = worker("vent_fan", () -> new Fan(metal(MapColor.COLOR_GRAY)));
    public static final RegistryObject<Block> STEEL_BEAM = reg("steel_beam", () -> new Line(metal(MapColor.COLOR_GREEN).strength(5f, 12f), 6));      // 0.37.0 bridges
    public static final RegistryObject<Block> BRIDGE_CABLE = reg("bridge_cable", () -> new Line(metal(MapColor.METAL).strength(4f, 10f), 3));
    public static final RegistryObject<Block> SHAFT = reg("drive_shaft", () -> new Shaft(metal(MapColor.METAL).strength(1.5f, 6f), 2));
    public static final RegistryObject<Block> COGWHEEL = reg("cogwheel", () -> new Shaft(metal(MapColor.METAL).strength(1.5f, 6f), 7));
    public static final RegistryObject<Block> MILLSTONE = worker("millstone", () -> new Millstone(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2f, 6f).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> WINCH = reg("mine_winch", () -> new Winch(metal(MapColor.COLOR_YELLOW)));
    /** 0.38.0: the shaft hoist: a winch at every station of a working mine's shaft. Use it to ride the cage down to the next station's
     *  winch (sneak: up); with a minecart near (8 blocks), the cart rides with you (skip hoist). */
    public static final class Winch extends CityReg.Fit {
        public Winch(Properties p) { super(p, false, 0, 0, 1, 16, 14, 15); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (l.isClientSide) return InteractionResult.SUCCESS; int dir = pl.isShiftKeyDown() ? 1 : -1; BlockPos to = null;
            for (int dy = 3; dy <= 200 && to == null; dy++) { BlockPos q = p.offset(0, dir * dy, 0); if (l.getBlockState(q).getBlock() instanceof Winch) to = q; }
            if (to == null) { pl.displayClientMessage(Component.literal(dir < 0 ? "The lowest station" : "The top station"), true); return InteractionResult.CONSUME; }
            Direction f = l.getBlockState(to).getValue(FACING); double tx = to.getX() + 0.5 + f.getStepX(), tz = to.getZ() + 0.5 + f.getStepZ();
            l.playSound(null, p, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1f, 0.5f);
            for (net.minecraft.world.entity.vehicle.AbstractMinecart c : l.getEntitiesOfClass(net.minecraft.world.entity.vehicle.AbstractMinecart.class, new AABB(p).inflate(8))) { c.teleportTo(tx, to.getY(), tz); skipHoists++; break; }
            pl.teleportTo(tx, to.getY(), tz); l.playSound(null, to, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1f, 0.6f); hoistRides++;
            return InteractionResult.CONSUME; }
    }
    public static volatile long hoistRides, skipHoists;
    public static final RegistryObject<Block> PRESS = worker("mechanical_press", () -> new Press(metal(MapColor.COLOR_GRAY)));
    public static final RegistryObject<Block> DEPOT = reg("depot", () -> new Depot(metal(MapColor.COLOR_GRAY)));
    // security
    public static final RegistryObject<Block> REINFORCED_IRON = reg("reinforced_iron_block", () -> new Block(hard()));
    public static final RegistryObject<Block> REINFORCED_BRICKS = reg("reinforced_stone_bricks", () -> new Block(hard().mapColor(MapColor.STONE).sound(SoundType.STONE)));
    public static final RegistryObject<Block> REINFORCED_BARS = reg("reinforced_iron_bars", () -> new CityReg.Bars(hard().noOcclusion()));
    public static final RegistryObject<Block> KEYPAD_DOOR = reg("keypad_door", () -> new KeypadDoor(metal(MapColor.METAL).strength(50f, 1200f)), false);
    public static final RegistryObject<Item> KEYPAD_DOOR_ITEM = item("keypad_door", () -> new DoubleHighBlockItem(KEYPAD_DOOR.get(), new Item.Properties()));
    public static final RegistryObject<Block> CAMERA = worker("security_camera", () -> new Camera(metal(MapColor.COLOR_GRAY).strength(1.5f, 6f)));
    public static final RegistryObject<Block> PANIC_BUTTON = reg("panic_button", () -> new Panic(BlockBehaviour.Properties.copy(Blocks.STONE_BUTTON).mapColor(MapColor.COLOR_RED)));
    public static final RegistryObject<Block> ALARM = worker("alarm", () -> new Alarm(metal(MapColor.COLOR_RED).strength(1f, 6f).noCollission().lightLevel(s -> s.getValue(LIT) ? 7 : 0)));
    public static final RegistryObject<Block> ELECTRIC_FENCE = reg("electrified_fence", () -> new ElectricFence(metal(MapColor.METAL).strength(5f, 6f)));
    public static final RegistryObject<Block> STANDBY = worker("standby_generator", () -> new Standby(metal(MapColor.COLOR_YELLOW).strength(5f, 12f)));
    // transit
    public static final RegistryObject<Block> TERRAZZO = reg("terrazzo", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(1.5f, 6f).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> TICKET_MACHINE = reg("ticket_machine", () -> new TicketMachine(metal(MapColor.COLOR_BLUE).lightLevel(s -> s.getValue(CityReg.Tall.HALF) == DoubleBlockHalf.UPPER ? 6 : 0)));
    public static final RegistryObject<Block> STATION_CLOCK = reg("station_clock", () -> new StationClock(metal(MapColor.SNOW).strength(1f, 6f)));
    public static final RegistryObject<Block> CLOCK_POLE = reg("clock_pole", () -> new Line(metal(MapColor.COLOR_BLACK), 1.5));
    // furniture
    public static final RegistryObject<Block> CHAIR = reg("chair", () -> new Seat(wood(MapColor.WOOD), 0.47, 2, 0, 2, 14, 16, 14));
    public static final RegistryObject<Block> SOFA = reg("sofa", () -> new Seat(cloth(MapColor.COLOR_GRAY), 0.45, 0, 0, 1, 16, 14, 16));
    public static final RegistryObject<Block> TOILET = reg("toilet", () -> new Seat(CityReg.stone(MapColor.SNOW).sound(SoundType.STONE), 0.44, 3, 0, 1, 13, 15, 16));
    public static final RegistryObject<Block> TABLE = reg("table", () -> new CityReg.Fit(wood(MapColor.WOOD), false, 0, 0, 0, 16, 12, 16));   // top 0.75 m: ADA 902.3, 28-34 in
    public static final RegistryObject<Block> COFFEE_TABLE = reg("coffee_table", () -> new CityReg.Fit(wood(MapColor.WOOD), false, 1, 0, 1, 15, 7, 15));
    public static final RegistryObject<Block> DESK = reg("desk", () -> new CityReg.Fit(wood(MapColor.WOOD), false, 0, 0, 0, 16, 12, 16));
    public static final RegistryObject<Block> COMPUTER = reg("computer", () -> new Screen(metal(MapColor.SNOW).strength(1f, 3f).lightLevel(s -> s.getValue(ON) ? 6 : 0), 2, 0, 3, 14, 12, 13));
    public static final RegistryObject<Block> TELEVISION = reg("television", () -> new Screen(metal(MapColor.COLOR_BLACK).strength(1f, 3f).lightLevel(s -> s.getValue(ON) ? 8 : 0), 0, 0, 5, 16, 14, 11));
    public static final RegistryObject<Block> STOVE = reg("stove", () -> new Stove(metal(MapColor.SNOW).lightLevel(s -> s.getValue(LIT) ? 10 : 0)));
    public static final RegistryObject<Block> CHEST_FREEZER = box("chest_freezer", () -> new Box(metal(MapColor.SNOW), 0, 0, 1, 16, 14, 16));
    public static final RegistryObject<Block> MAILBOX = box("home_mailbox", () -> new Box(metal(MapColor.COLOR_BLUE), 3, 0, 2, 13, 16, 14));
    public static final RegistryObject<Block> TRASH_CAN = box("trash_can", () -> new Box(metal(MapColor.COLOR_GRAY), 2, 0, 2, 14, 15, 14));
    // shop and house fittings
    public static final RegistryObject<Block> PRODUCE_CRATE = reg("produce_crate", () -> new Crate(wood(MapColor.WOOD)));
    public static final RegistryObject<Block> FLOUR_SACK = reg("flour_sack", () -> new CityReg.Fit(cloth(MapColor.SAND), false, 2, 0, 3, 14, 14, 13));
    public static final RegistryObject<Block> COOKING_POT = reg("cooking_pot", () -> new CityReg.Fit(metal(MapColor.METAL).strength(1f, 3f), false, 3, 0, 3, 13, 9, 13));
    public static final RegistryObject<Block> SKILLET = reg("skillet", () -> new CityReg.Fit(metal(MapColor.COLOR_BLACK).strength(1f, 3f), false, 3, 0, 1, 13, 2, 15));
    public static final RegistryObject<Block> HEARTH = reg("hearth", () -> new Hearth(CityReg.stone(MapColor.COLOR_RED).lightLevel(s -> s.getValue(LIT) ? 15 : 0)));
    public static final RegistryObject<Block> HEARTH_CHIMNEY = reg("hearth_chimney", () -> new CityReg.Fit(CityReg.stone(MapColor.COLOR_RED), false, 2, 0, 2, 14, 16, 16));
    public static final RegistryObject<Block> ICEBOX = box("icebox", () -> new Box(wood(MapColor.WOOD), 1, 0, 2, 15, 15, 14));
    public static final RegistryObject<Block> SEWING_TABLE = reg("sewing_table", () -> new Station(wood(MapColor.WOOD), true));
    public static final RegistryObject<Block> WORKBENCH = reg("workbench", () -> new Station(wood(MapColor.WOOD), false));
    public static final RegistryObject<Block> WATER_FILTER = reg("water_filter", () -> new Filter(metal(MapColor.COLOR_LIGHT_BLUE)));
    public static final RegistryObject<Block> SERVER_RACK = reg("server_rack", () -> new CityReg.Fit(metal(MapColor.COLOR_BLACK).lightLevel(s -> 3), false, 1, 0, 1, 15, 16, 15));
    // crops (the seeds and the onion plant them)
    public static final RegistryObject<Block> CABBAGES = reg("cabbages", () -> new Crop3(BlockBehaviour.Properties.copy(Blocks.WHEAT), () -> OwnReg.CABBAGE_SEEDS.get()), false);
    public static final RegistryObject<Block> ONIONS = reg("onions", () -> new Crop3(BlockBehaviour.Properties.copy(Blocks.CARROTS), () -> OwnReg.ONION.get()), false);
    public static final RegistryObject<Block> TOMATOES = reg("tomatoes", () -> new Crop3(BlockBehaviour.Properties.copy(Blocks.WHEAT), () -> OwnReg.TOMATO_SEEDS.get()), false);
    // ores
    static BlockBehaviour.Properties ore(boolean deep) { return BlockBehaviour.Properties.of().mapColor(deep ? MapColor.DEEPSLATE : MapColor.STONE).strength(deep ? 4.5f : 3f, 3f).requiresCorrectToolForDrops().sound(deep ? SoundType.DEEPSLATE : SoundType.STONE); }
    public static final RegistryObject<Block> TIN_ORE = reg("tin_ore", () -> new DropExperienceBlock(ore(false)));
    public static final RegistryObject<Block> DEEPSLATE_TIN_ORE = reg("deepslate_tin_ore", () -> new DropExperienceBlock(ore(true)));
    public static final RegistryObject<Block> ZINC_ORE = reg("zinc_ore", () -> new DropExperienceBlock(ore(false)));
    public static final RegistryObject<Block> DEEPSLATE_URANIUM_ORE = reg("deepslate_uranium_ore", () -> new DropExperienceBlock(ore(true), net.minecraft.util.valueproviders.UniformInt.of(1, 3)));

    // items
    public static final RegistryObject<Item> CABBAGE_SEEDS = item("cabbage_seeds", () -> new ItemNameBlockItem(CABBAGES.get(), new Item.Properties()));
    public static final RegistryObject<Item> TOMATO_SEEDS = item("tomato_seeds", () -> new ItemNameBlockItem(TOMATOES.get(), new Item.Properties()));
    public static final RegistryObject<Item> ONION = item("onion", () -> new ItemNameBlockItem(ONIONS.get(), new Item.Properties().food(new FoodProperties.Builder().nutrition(2).saturationMod(0.4f).build())));
    public static final RegistryObject<Item> CABBAGE = food("cabbage", 2, 0.4f), TOMATO = food("tomato", 1, 0.3f);
    public static final RegistryObject<Item> FLOUR = plain("flour"), IRON_PLATE = plain("iron_plate"), COPPER_PLATE = plain("copper_plate"), STEEL_INGOT = plain("steel_ingot"),
        RAW_TIN = plain("raw_tin"), TIN_INGOT = plain("tin_ingot"), RAW_ZINC = plain("raw_zinc"), ZINC_INGOT = plain("zinc_ingot"), RAW_URANIUM = plain("raw_uranium"), URANIUM_INGOT = plain("uranium_ingot"),
        COPPER_WIRE = plain("copper_wire"), CIRCUIT_BOARD = plain("circuit_board"), TICKET = plain("transit_ticket");
    public static final RegistryObject<Item> WRENCH = item("wrench", () -> new Wrench(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SCREWDRIVER = item("screwdriver", () -> new Wrench(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> KITCHEN_KNIFE = item("kitchen_knife", () -> new SwordItem(Tiers.IRON, 1, -2.0f, new Item.Properties()));
    public static final RegistryObject<Item> WATERSKIN = item("waterskin", () -> new Waterskin(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> THERMOMETER = item("thermometer", () -> new Thermometer(new Item.Properties().stacksTo(1)));

    // block entities
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<MachineBE>> MACHINE = BES.register("own_machine", () -> BlockEntityType.Builder.of(MachineBE::new, WORKERS.stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<DepotBE>> DEPOT_BE = BES.register("depot", () -> BlockEntityType.Builder.of(DepotBE::new, DEPOT.get()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<KeypadBE>> KEYPAD_BE = BES.register("keypad_door", () -> BlockEntityType.Builder.of(KeypadBE::new, KEYPAD_DOOR.get()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<StoveBE>> STOVE_BE = BES.register("stove", () -> BlockEntityType.Builder.of(StoveBE::new, STOVE.get()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<OwnBox>> OWN_BOX = BES.register("own_box", () -> BlockEntityType.Builder.of(OwnBox::new, BOXES.stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) {
        PlayReg.touch();   // 0.38.0: the town game's blocks and items, on these registers
        BLOCKS.register(bus); ITEMS.register(bus); BES.register(bus); ENTITIES.register(bus); SOUNDS.register(bus); OwnNet.init();
        bus.addListener((net.minecraftforge.event.BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) for (RegistryObject<Item> it : TAB) e.accept(it.get()); });
    }
    public static String probe() {
        return "own blocks: generators lit " + genLit + " out " + genOut + " relay changes " + relayChanges + " motor changes " + motorChanges + " pumped " + pumped + " tank uses " + tankUses
            + " fan pushes " + fanPushes + " milled " + milled + " pressed " + pressed + " depot " + depotUses + " | keypad opens " + keypadOpens + " bad " + keypadBad + " set " + keypadSet
            + " camera trips " + cameraTrips + " panics " + panics + " alarm rings " + alarmRings + " shocks " + fenceShocks + " standby starts " + standbyStarts + " stops " + standbyStops
            + " | sits " + sits + " tickets " + ticketsSold + " clock reads " + clockReads + " stove menus " + cooked + " filtered " + filtered + " wrenched " + wrenched
            + " drinks " + drinks + " skin fills " + skinFills + " readings " + readings + " | " + GridNet.probe();
    }
}
