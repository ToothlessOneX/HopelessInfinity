package com.hopeless.infinity.town;

import com.hopeless.infinity.HopelessInfinity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/**
 * 0.32.0: the city's working fittings, made for the mod (models, textures), in place of the vanilla stand-ins the builders used, and
 * the safety equipment US codes ask for. Each one does its job:
 *   sink, shower       - fill a bucket or a bottle; the shower runs (use) and puts out a burning player under it
 *   washer             - washes the dye out of wool, carpet and leather armour (the laundromat)
 *   atm, change machine, vending machine, drink cooler - give out cash, change, snacks and drinks until their stock runs out;
 *                        the change machine also breaks a gold ingot into 9 nuggets
 *   fridge, supply cabinet, cash register, PO box - containers with their own loot
 *   fire extinguisher  - 3 charges; each puts out fire within 6 blocks (NFPA 10: 2-A:20-B:C within 75 ft of travel)
 *   tube station       - sends what you hold to the nearest other station (a bank's drive-up pneumatic tube)
 *   metal detector     - walk-through: rings and gives a redstone pulse when someone carrying metal walks through
 *   x-ray belt         - a conveyor: carries what stands on it toward its front
 *   call button, call light - a resident's call button lights the call lights near it (nurse call); use the light to reset it
 *   menu board, chalkboard, projection screen + projector, satellite dish, roof bolt, car lift post - the shapes they stand for;
 *                        the projector switches the screens near it on and off, the menu board lights up
 *   hoist station      - the mine's hoist: use it to ride to the other station in the shaft
 *   blast door + blast door control - the shelter's steel door slides open and shut from its control lever
 *   exit sign, emergency light (IBC 1013, 1008.3), hose valve + fire department connection (NFPA 14 standpipes; the valve fills
 *                        a bucket and puts out fire), fire escape grating, escape ladder, drop ladder (pull it down from the
 *                        street), sash window (opens), crossing signal + gate arm (MUTCD 8C: flash, ring and lower the gate while a
 *                        cart is within 40 blocks), stop sign, speed limit sign, accessible parking sign (MUTCD R1-1, R2-1, R7-8),
 *                        detectable warning tiles and the ramp (ADA 705, 405), chain-link fence, barbed wire, fence danger sign,
 *                        transformer (NESC 110: it gives redstone power and shocks whoever touches it), power line
 *   fire engine        - the engine in the fire station bay; its tank fills a bucket, its horn sounds
 */
public final class CityReg {
    private CityReg() {}
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HopelessInfinity.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HopelessInfinity.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HopelessInfinity.MODID);
    public static volatile long filled, washed, vended, soldOut, changeMade, extinguished, tubesSent, detectorRings, beltPushes, calls, callsReset, screens,
        hoistRides, blastMoves, valveUses, ladderDrops, windowsOpened, crossingTicks, crossingActive, armsMoved, shocks, barbs, engineUses, drained;   // probes

    // ------------------------------------------------------------------ shapes
    /** A box given for the block facing north (pixels), turned to f. */
    static VoxelShape turned(Direction f, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (f) {
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1); };
    }
    static VoxelShape[] four(double... b) { VoxelShape[] s = new VoxelShape[4]; for (Direction d : Direction.Plane.HORIZONTAL) s[d.get2DDataValue()] = turned(d, b[0], b[1], b[2], b[3], b[4], b[5]); return s; }

    /** A facing fitting with a fixed outline (given facing north). The front faces the player who places it, unless backToPlayer. */
    public static class Fit extends HorizontalDirectionalBlock {
        final VoxelShape[] shape; final boolean back;
        public Fit(Properties p, boolean backToPlayer, double... box) { super(p); this.back = backToPlayer; shape = box.length == 6 ? four(box) : null; registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext c) { Direction d = c.getHorizontalDirection(); return defaultBlockState().setValue(FACING, back ? d : d.getOpposite()); }
        @Override public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) { return shape == null ? Shapes.block() : shape[s.getValue(FACING).get2DDataValue()]; }
        @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    }
    /** A fitting two blocks high (HALF): the lower half does the work, the upper passes a use down; one half goes when the other does. */
    public static class Tall extends Fit {
        public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
        public Tall(Properties p, boolean back, double... box) { super(p, back, box); registerDefaultState(defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, HALF); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext c) { BlockPos p = c.getClickedPos(); if (p.getY() >= c.getLevel().getMaxBuildHeight() - 1 || !c.getLevel().getBlockState(p.above()).canBeReplaced(c)) return null; return super.getStateForPlacement(c); }
        @Override public void setPlacedBy(Level l, BlockPos p, BlockState s, LivingEntity e, ItemStack st) { l.setBlock(p.above(), s.setValue(HALF, DoubleBlockHalf.UPPER), 3); }
        @Override public BlockState updateShape(BlockState s, Direction d, BlockState n, LevelAccessor l, BlockPos p, BlockPos np) {
            boolean lower = s.getValue(HALF) == DoubleBlockHalf.LOWER;
            if (d == (lower ? Direction.UP : Direction.DOWN) && !(n.is(this) && n.getValue(HALF) != s.getValue(HALF))) return Blocks.AIR.defaultBlockState();
            return s; }
        static BlockPos base(BlockState s, BlockPos p) { return s.getValue(HALF) == DoubleBlockHalf.UPPER ? p.below() : p; }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            BlockPos b = base(s, p); BlockState bs = l.getBlockState(b); if (!bs.is(this)) return InteractionResult.PASS; return useLower(bs, l, b, pl, h); }
        InteractionResult useLower(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h) { return InteractionResult.PASS; }
    }
    static void give(Player pl, InteractionHand h, ItemStack got) {
        ItemStack held = pl.getItemInHand(h);
        if (held.isEmpty()) pl.setItemInHand(h, got); else if (!pl.getInventory().add(got)) pl.drop(got, false); }
    static boolean fillWater(Level l, BlockPos p, Player pl, InteractionHand h) {
        ItemStack it = pl.getItemInHand(h);
        if ((it.is(Items.BUCKET) || it.is(Items.GLASS_BOTTLE)) && l instanceof ServerLevel sl && com.hopeless.infinity.game.Outage.noWater(sl, p.getX(), p.getZ())) {   // 0.38.0: a water main break
            pl.displayClientMessage(Component.literal("No water - the main is broken"), true); return true; }
        if (it.is(Items.BUCKET)) { if (!l.isClientSide) { pl.setItemInHand(h, net.minecraft.world.item.ItemUtils.createFilledResult(it, pl, new ItemStack(Items.WATER_BUCKET))); l.playSound(null, p, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1f, 1f); filled++; } return true; }
        if (it.is(Items.GLASS_BOTTLE)) { if (!l.isClientSide) { pl.setItemInHand(h, net.minecraft.world.item.ItemUtils.createFilledResult(it, pl, net.minecraft.world.item.alchemy.PotionUtils.setPotion(new ItemStack(Items.POTION), net.minecraft.world.item.alchemy.Potions.WATER))); l.playSound(null, p, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1f, 1f); filled++; } return true; }
        return false; }
    /** Puts out every fire within r blocks of p (and the user). Returns how many fires. */
    static int douse(Level l, BlockPos p, int r, Player pl) {
        int n = 0; if (l instanceof ServerLevel sl) {
            for (BlockPos q : BlockPos.betweenClosed(p.offset(-r, -2, -r), p.offset(r, 4, r))) if (sl.getBlockState(q).is(BlockTags.FIRE)) { sl.removeBlock(q, false); n++; }
            if (pl != null) pl.clearFire();
            sl.playSound(null, p, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1f, 1f); }
        return n; }
    static Component msg(String s) { return Component.literal(s); }

    // ------------------------------------------------------------------ water: sink, shower, washer
    /** A sink (lavatory, kitchen or utility): its back to the wall (FACING = the wall side). Fills a bucket or bottle; use empty-handed to wash. */
    public static final class Sink extends Fit {
        public Sink(Properties p) { super(p, true, 2, 0, 0, 14, 14, 11); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (fillWater(l, p, pl, h)) return InteractionResult.sidedSuccess(l.isClientSide);
            if (pl.getItemInHand(h).isEmpty() && l instanceof ServerLevel sw && com.hopeless.infinity.game.Outage.noWater(sw, p.getX(), p.getZ())) { pl.displayClientMessage(Component.literal("No water - the main is broken"), true); return InteractionResult.CONSUME; }
            if (pl.getItemInHand(h).isEmpty()) { if (l instanceof ServerLevel sl) { pl.clearFire(); sl.sendParticles(ParticleTypes.SPLASH, p.getX() + 0.5, p.getY() + 0.95, p.getZ() + 0.5, 12, 0.2, 0.05, 0.2, 0.1); sl.playSound(null, p, SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 0.4f, 1.6f); } return InteractionResult.sidedSuccess(l.isClientSide); }
            return InteractionResult.PASS; }
    }
    /** A shower: the pan on the floor, the riser and head on the wall behind (FACING = the wall side). RUNNING sprays and puts out fire on whoever stands in it. */
    public static final class Shower extends Fit {
        public static final BooleanProperty RUNNING = BooleanProperty.create("running");
        public Shower(Properties p) { super(p, true, 0, 0, 0, 16, 2, 16); registerDefaultState(defaultBlockState().setValue(RUNNING, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, RUNNING); }
        @Override public VoxelShape getCollisionShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) { return Block.box(0, 0, 0, 16, 1, 16); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (fillWater(l, p, pl, h)) return InteractionResult.sidedSuccess(l.isClientSide);
            if (!l.isClientSide) { l.setBlock(p, s.cycle(RUNNING), 3); l.playSound(null, p, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.6f, 1.4f); }
            return InteractionResult.sidedSuccess(l.isClientSide); }
        @Override public void entityInside(BlockState s, Level l, BlockPos p, Entity e) { if (s.getValue(RUNNING) && e.isOnFire()) { e.clearFire(); extinguished++; } }
        @Override public void animateTick(BlockState s, Level l, BlockPos p, RandomSource r) {
            if (!s.getValue(RUNNING)) return; Direction f = s.getValue(FACING); double hx = p.getX() + 0.5 + f.getStepX() * 0.25, hz = p.getZ() + 0.5 + f.getStepZ() * 0.25;
            for (int i = 0; i < 4; i++) l.addParticle(ParticleTypes.FALLING_WATER, hx + (r.nextDouble() - 0.5) * 0.4, p.getY() + 1.85, hz + (r.nextDouble() - 0.5) * 0.4, 0, 0, 0); }
    }
    /** The laundromat's washer: use it with dyed wool, carpet or leather armour to wash the dye out. */
    public static final class Washer extends Fit {
        public Washer(Properties p) { super(p, false, 1, 0, 1, 15, 16, 15); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            ItemStack it = pl.getItemInHand(h); ItemStack out = null;
            if (it.is(net.minecraft.tags.ItemTags.WOOL) && !it.is(Items.WHITE_WOOL)) out = new ItemStack(Items.WHITE_WOOL, it.getCount());
            else if (it.is(net.minecraft.tags.ItemTags.WOOL_CARPETS) && !it.is(Items.WHITE_CARPET)) out = new ItemStack(Items.WHITE_CARPET, it.getCount());
            else if (it.getItem() instanceof net.minecraft.world.item.DyeableLeatherItem d && d.hasCustomColor(it)) { out = it.copy(); ((net.minecraft.world.item.DyeableLeatherItem) out.getItem()).clearColor(out); }
            if (out == null) return InteractionResult.PASS;
            if (!l.isClientSide) { pl.setItemInHand(h, out); l.playSound(null, p, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.6f, 1.2f); washed++; }
            return InteractionResult.sidedSuccess(l.isClientSide); }
    }

    // ------------------------------------------------------------------ machines that give things out
    public enum Kind { ATM, CHANGE, SNACK, DRINK }
    public static final IntegerProperty STOCK = IntegerProperty.create("stock", 0, 8);
    static ItemStack pick(Kind k, RandomSource r) {
        return switch (k) {
            case ATM -> r.nextInt(8) == 0 ? new ItemStack(Items.EMERALD) : new ItemStack(Items.GOLD_NUGGET, 2 + r.nextInt(5));
            case CHANGE -> new ItemStack(Items.GOLD_NUGGET, 1 + r.nextInt(3));
            case SNACK -> new ItemStack(new Item[]{Items.COOKIE, Items.BREAD, Items.DRIED_KELP, Items.APPLE, Items.HONEY_BOTTLE, Items.BAKED_POTATO, Items.PUMPKIN_PIE, Items.MELON_SLICE}[r.nextInt(8)]);
            case DRINK -> r.nextInt(10) == 0 ? new ItemStack(Items.MILK_BUCKET) : r.nextInt(3) == 0 ? new ItemStack(Items.HONEY_BOTTLE)
                : net.minecraft.world.item.alchemy.PotionUtils.setPotion(new ItemStack(Items.POTION), net.minecraft.world.item.alchemy.Potions.WATER); };
    }
    static InteractionResult vend(Kind k, BlockState s, Level l, BlockPos p, Player pl, InteractionHand h) {
        ItemStack held = pl.getItemInHand(h);
        if (k == Kind.CHANGE && held.is(Items.GOLD_INGOT)) { if (!l.isClientSide) { held.shrink(1); give(pl, h, new ItemStack(Items.GOLD_NUGGET, 9)); l.playSound(null, p, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1f, 1.6f); changeMade++; } return InteractionResult.sidedSuccess(l.isClientSide); }
        int st = s.getValue(STOCK);
        if (st == 0) { if (!l.isClientSide) { l.playSound(null, p, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.8f, 0.8f); soldOut++;
            pl.displayClientMessage(msg(k == Kind.ATM ? "Temporarily out of service" : k == Kind.CHANGE ? "Exact change only" : "Sold out"), true); } return InteractionResult.sidedSuccess(l.isClientSide); }
        if (!l.isClientSide) { l.setBlock(p, s.setValue(STOCK, st - 1), 3); ItemStack got = pick(k, l.random); if (!pl.getInventory().add(got)) pl.drop(got, false);
            l.playSound(null, p, k == Kind.ATM || k == Kind.CHANGE ? SoundEvents.CHAIN_PLACE : SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 0.9f, 1.2f); vended++; }
        return InteractionResult.sidedSuccess(l.isClientSide); }
    /** ATM (on a wall) and change machine: one block. */
    public static final class Vend extends Fit {
        final Kind kind;
        public Vend(Properties p, Kind k, double... box) { super(p, false, box); kind = k; registerDefaultState(defaultBlockState().setValue(STOCK, 8)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, STOCK); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) { return vend(kind, s, l, p, pl, h); }
    }
    /** Vending machine and drink cooler: two blocks high. */
    public static final class TallVend extends Tall {
        final Kind kind;
        public TallVend(Properties p, Kind k) { super(p, false, 1, 0, 1, 15, 16, 16); kind = k; registerDefaultState(defaultBlockState().setValue(STOCK, 8)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, HALF, STOCK); }
        @Override InteractionResult useLower(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h) { return vend(kind, s, l, p, pl, h); }
    }

    // ------------------------------------------------------------------ containers
    public static final class CityBox extends LootBox { public CityBox(BlockPos p, BlockState s) { super(CITY_BOX.get(), p, s); } }
    /** A container block (cash register, PO boxes, supply cabinet, the fridge's lower half). */
    public static class BoxBlock extends Fit implements EntityBlock {
        public BoxBlock(Properties p, double... box) { super(p, false, box); }
        @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new CityBox(p, s); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (!l.isClientSide && l.getBlockEntity(p) instanceof CityBox b) { pl.openMenu(b); l.playSound(null, p, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 1.2f); }
            return InteractionResult.sidedSuccess(l.isClientSide); }
        @Override public void onRemove(BlockState s, Level l, BlockPos p, BlockState now, boolean moved) {
            if (!s.is(now.getBlock()) && l.getBlockEntity(p) instanceof CityBox b) Containers.dropContents(l, p, b); super.onRemove(s, l, p, now, moved); }
    }
    /** The fridge: the lower block holds the 27 slots, the freezer on top (fridge_top) opens the same. */
    public static final class Fridge extends BoxBlock {
        public Fridge(Properties p) { super(p, 1, 0, 1, 15, 16, 16); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext c) { if (!c.getLevel().getBlockState(c.getClickedPos().above()).canBeReplaced(c)) return null; return super.getStateForPlacement(c); }
        @Override public void setPlacedBy(Level l, BlockPos p, BlockState s, LivingEntity e, ItemStack st) { l.setBlock(p.above(), FRIDGE_TOP.get().defaultBlockState().setValue(FACING, s.getValue(FACING)), 3); }
        @Override public BlockState updateShape(BlockState s, Direction d, BlockState n, LevelAccessor l, BlockPos p, BlockPos np) { return d == Direction.UP && !n.is(FRIDGE_TOP.get()) ? Blocks.AIR.defaultBlockState() : s; }
    }
    public static final class FridgeTop extends Fit {
        public FridgeTop(Properties p) { super(p, false, 1, 0, 1, 15, 16, 16); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) { BlockState b = l.getBlockState(p.below()); return b.is(FRIDGE.get()) ? b.use(l, pl, h, hit.withPosition(p.below())) : InteractionResult.PASS; }
        @Override public BlockState updateShape(BlockState s, Direction d, BlockState n, LevelAccessor l, BlockPos p, BlockPos np) { return d == Direction.DOWN && !n.is(FRIDGE.get()) ? Blocks.AIR.defaultBlockState() : s; }
    }

    // ------------------------------------------------------------------ fire: extinguisher, hose valve, connection
    public static final IntegerProperty CHARGES = IntegerProperty.create("charges", 0, 3);
    /** A fire extinguisher cabinet (NFPA 10): 3 discharges, each puts out every fire within 6 blocks. */
    public static final class Extinguisher extends Fit {
        public Extinguisher(Properties p) { super(p, false, 3, 2, 10, 13, 15, 16); registerDefaultState(defaultBlockState().setValue(CHARGES, 3)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, CHARGES); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            int c = s.getValue(CHARGES);
            if (c == 0) { if (!l.isClientSide) { l.playSound(null, p, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.8f, 1.2f); pl.displayClientMessage(msg("Empty - recharge after use (NFPA 10)"), true); } return InteractionResult.sidedSuccess(l.isClientSide); }
            if (l instanceof ServerLevel sl) { extinguished += douse(l, p, 6, pl); sl.sendParticles(ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 40, 1.5, 0.6, 1.5, 0.05); l.setBlock(p, s.setValue(CHARGES, c - 1), 3); }
            return InteractionResult.sidedSuccess(l.isClientSide); }
    }
    /** A standpipe hose valve (NFPA 14 Class I, 2 1/2 in, at each floor landing of an exit stair): fills a bucket; empty-handed it puts out fire within 8. */
    public static final class HoseValve extends Fit {
        public HoseValve(Properties p) { super(p, false, 4, 2, 9, 12, 14, 16); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (fillWater(l, p, pl, h)) { valveUses++; return InteractionResult.sidedSuccess(l.isClientSide); }
            if (!pl.getItemInHand(h).isEmpty()) return InteractionResult.PASS;
            if (l instanceof ServerLevel sl) { extinguished += douse(l, p, 8, pl); sl.sendParticles(ParticleTypes.SPLASH, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 60, 1.2, 0.5, 1.2, 0.2); valveUses++; }
            return InteractionResult.sidedSuccess(l.isClientSide); }
    }
    /** The fire department connection (NFPA 14 / IBC 912): the Siamese inlet on the street face that feeds the standpipes. */
    public static final class Fdc extends Fit {
        public Fdc(Properties p) { super(p, false, 2, 3, 10, 14, 13, 16); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (!l.isClientSide) pl.displayClientMessage(msg("STANDPIPE - fire department connection (NFPA 14). Hose valves on every stair landing."), true);
            return InteractionResult.sidedSuccess(l.isClientSide); }
    }

    // ------------------------------------------------------------------ bank tube, security
    /** A pneumatic tube station: use it holding something to send the whole stack to the nearest other station within 24 blocks. */
    public static final class TubeStation extends Fit {
        public TubeStation(Properties p) { super(p, false, 3, 0, 3, 13, 16, 13); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            ItemStack it = pl.getItemInHand(h); if (it.isEmpty()) return InteractionResult.PASS;
            if (l.isClientSide) return InteractionResult.SUCCESS;
            BlockPos best = null; double bd = 1e9;
            for (BlockPos q : BlockPos.betweenClosed(p.offset(-24, -4, -24), p.offset(24, 4, 24))) { if (q.equals(p) || !l.getBlockState(q).is(this)) continue; double d = q.distSqr(p); if (d < bd) { bd = d; best = q.immutable(); } }
            if (best == null) { pl.displayClientMessage(msg("No other tube station on this line"), true); l.playSound(null, p, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.8f, 1f); return InteractionResult.CONSUME; }
            Direction f = l.getBlockState(best).getValue(FACING); ItemEntity e = new ItemEntity(l, best.getX() + 0.5 + f.getStepX() * 0.7, best.getY() + 1.0, best.getZ() + 0.5 + f.getStepZ() * 0.7, it.copy());
            e.setDeltaMovement(f.getStepX() * 0.1, 0.1, f.getStepZ() * 0.1); l.addFreshEntity(e); pl.setItemInHand(h, ItemStack.EMPTY);
            l.playSound(null, p, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.BLOCKS, 0.4f, 2f); l.playSound(null, best, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 0.6f); tubesSent++;
            return InteractionResult.CONSUME; }
    }
    /** A walk-through metal detector (two high): rings and gives a redstone pulse when someone carrying metal walks through. */
    public static final class MetalDetector extends Tall {
        public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
        public MetalDetector(Properties p) { super(p, false); registerDefaultState(defaultBlockState().setValue(POWERED, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, HALF, POWERED); }
        static final VoxelShape[] POSTS = new VoxelShape[4];
        static { for (Direction d : Direction.Plane.HORIZONTAL) POSTS[d.get2DDataValue()] = Shapes.or(turned(d, 0, 0, 5, 2, 16, 11), turned(d, 14, 0, 5, 16, 16, 11)); }
        @Override public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) { return POSTS[s.getValue(FACING).get2DDataValue()]; }
        static boolean metal(ItemStack st) { if (st.isEmpty()) return false; String id = ForgeRegistries.ITEMS.getKey(st.getItem()).toString();
            return id.contains("iron") || id.contains("gold") || id.contains("netherite") || id.contains("chain") || id.contains("tacz") || id.contains("gun") || id.contains("sword") || id.contains("_axe") || id.contains("pickaxe") || id.contains("crossbow") || id.contains("key") || id.contains("shears") || id.contains("compass"); }
        @Override public void entityInside(BlockState s, Level l, BlockPos p, Entity e) {
            if (l.isClientSide || s.getValue(HALF) != DoubleBlockHalf.LOWER || s.getValue(POWERED) || !(e instanceof Player pl)) return;
            boolean hit = false; for (ItemStack st : pl.getInventory().items) if (metal(st)) { hit = true; break; }
            if (!hit) for (ItemStack st : pl.getInventory().armor) if (metal(st)) { hit = true; break; }
            if (!hit) return;
            l.setBlock(p, s.setValue(POWERED, true), 3); l.scheduleTick(p, this, 30); l.updateNeighborsAt(p, this);
            l.playSound(null, p, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1.5f, 1.9f); detectorRings++; }
        @Override public void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) { if (s.getValue(POWERED)) { l.setBlock(p, s.setValue(POWERED, false), 3); l.updateNeighborsAt(p, this); } }
        @Override public boolean isSignalSource(BlockState s) { return true; }
        @Override public int getSignal(BlockState s, BlockGetter g, BlockPos p, Direction d) { return s.getValue(POWERED) ? 15 : 0; }
    }
    /** The bag x-ray: a belt that carries what stands on it toward its front; HOOD is the scanner tunnel over the middle. */
    public static final class XrayBelt extends Fit {
        public static final BooleanProperty HOOD = BooleanProperty.create("hood");
        public XrayBelt(Properties p) { super(p, false, 0, 0, 2, 16, 12, 14); registerDefaultState(defaultBlockState().setValue(HOOD, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, HOOD); }
        @Override public void stepOn(Level l, BlockPos p, BlockState s, Entity e) {
            Direction f = s.getValue(FACING); Vec3 v = e.getDeltaMovement(); e.setDeltaMovement(f.getStepX() * 0.12 + v.x * 0.5, v.y, f.getStepZ() * 0.12 + v.z * 0.5); if (!l.isClientSide) beltPushes++; }
    }

    // ------------------------------------------------------------------ nurse call
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** The resident's call button: pressing it lights the call lights within 16 blocks. */
    public static final class CallButton extends ButtonBlock {
        public CallButton(Properties p) { super(p, BlockSetType.STONE, 20, false); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            InteractionResult r = super.use(s, l, p, pl, h, hit);
            if (!l.isClientSide) { calls++; for (BlockPos q : BlockPos.betweenClosed(p.offset(-16, -3, -16), p.offset(16, 3, 16))) { BlockState c = l.getBlockState(q); if (c.is(CALL_LIGHT.get()) && !c.getValue(LIT)) l.setBlock(q, c.setValue(LIT, true), 3); }
                l.playSound(null, p, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1f, 1.5f); }
            return r; }
    }
    /** The call light over a resident's door and on the nurse station board: lit by a call button, use it to answer (reset). */
    public static final class CallLight extends Block {
        public CallLight(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(LIT, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(LIT); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (!s.getValue(LIT)) return InteractionResult.PASS; if (!l.isClientSide) { l.setBlock(p, s.setValue(LIT, false), 3); callsReset++; } return InteractionResult.sidedSuccess(l.isClientSide); }
    }

    // ------------------------------------------------------------------ displays
    /** A lit sign or board (menu board, exit sign): light from the state factory; use plays nothing. */
    public static final class Screen extends Block {
        public static final BooleanProperty ON = BooleanProperty.create("on");
        public Screen(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(ON, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(ON); }
    }
    /** The cinema projector: use it to switch every projection screen within 32 blocks on or off. */
    public static final class Projector extends Fit {
        public Projector(Properties p) { super(p, false, 3, 0, 1, 13, 10, 15); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (l.isClientSide) return InteractionResult.SUCCESS; Boolean to = null; int n = 0;
            for (BlockPos q : BlockPos.betweenClosed(p.offset(-32, -8, -32), p.offset(32, 8, 32))) { BlockState c = l.getBlockState(q); if (!c.is(SCREEN.get())) continue; if (to == null) to = !c.getValue(Screen.ON); l.setBlock(q, c.setValue(Screen.ON, to), 2); n++; }
            l.playSound(null, p, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 1f, 0.6f); screens += n; pl.displayClientMessage(msg(n == 0 ? "No screen in range" : (to ? "Screen on" : "Screen off")), true);
            return InteractionResult.CONSUME; }
    }
    /** A drive-thru menu board (two high, lit) with its speaker: use it to order. */
    public static final class MenuBoard extends Tall {
        public MenuBoard(Properties p) { super(p, false, 0, 0, 6, 16, 16, 10); }
        @Override InteractionResult useLower(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h) {
            if (!l.isClientSide) { pl.displayClientMessage(msg(l.random.nextInt(3) == 0 ? "...static... pull around to the window" : "Welcome! Order when you're ready."), true); l.playSound(null, p, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 0.8f, 0.7f); }
            return InteractionResult.sidedSuccess(l.isClientSide); }
    }

    // ------------------------------------------------------------------ the mine hoist, the shelter's blast door
    /** A hoist station: use it to ride the cage to the other station in this shaft (within 2 blocks across, 96 up or down). */
    public static final class HoistStation extends Fit {
        public HoistStation(Properties p) { super(p, false, 0, 0, 0, 16, 4, 16); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (l.isClientSide) return InteractionResult.SUCCESS; BlockPos to = null;
            for (int dy = 1; dy <= 96 && to == null; dy++) for (int sg : new int[]{-1, 1}) { if (to != null) break;
                for (int dx = -2; dx <= 2 && to == null; dx++) for (int dz = -2; dz <= 2 && to == null; dz++) { BlockPos q = p.offset(dx, sg * dy, dz); if (l.getBlockState(q).is(this)) to = q; } }
            if (to == null) { pl.displayClientMessage(msg("The hoist has no other station"), true); return InteractionResult.CONSUME; }
            l.playSound(null, p, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.5f, 0.8f); pl.teleportTo(to.getX() + 0.5, to.getY() + 0.3, to.getZ() + 0.5); l.playSound(null, to, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.5f, 0.5f); hoistRides++;
            return InteractionResult.CONSUME; }
    }
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** A plate of the blast door: OPEN = slid away (no shape, not drawn). */
    public static final class BlastDoor extends Block {
        public BlastDoor(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(OPEN, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(OPEN); }
        @Override public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) { return s.getValue(OPEN) ? Shapes.empty() : Shapes.block(); }
        @Override public RenderShape getRenderShape(BlockState s) { return s.getValue(OPEN) ? RenderShape.INVISIBLE : RenderShape.MODEL; }
        @Override public boolean propagatesSkylightDown(BlockState s, BlockGetter g, BlockPos p) { return s.getValue(OPEN); }
    }
    /** The blast door's control lever: on opens every plate joined to a plate within 6 blocks of it, off shuts them. */
    public static final class BlastControl extends LeverBlock {
        public BlastControl(Properties p) { super(p); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            InteractionResult r = super.use(s, l, p, pl, h, hit); if (l.isClientSide) return r;
            boolean on = l.getBlockState(p).getValue(POWERED); java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>(); java.util.Set<BlockPos> seen = new java.util.HashSet<>();
            for (BlockPos b : BlockPos.betweenClosed(p.offset(-6, -6, -6), p.offset(6, 6, 6))) if (l.getBlockState(b).is(BLAST_DOOR.get())) { q.add(b.immutable()); seen.add(b.immutable()); }
            while (!q.isEmpty() && seen.size() < 256) { BlockPos b = q.poll(); BlockState d = l.getBlockState(b); if (d.getValue(OPEN) != on) { l.setBlock(b, d.setValue(OPEN, on), 3); blastMoves++; }
                for (Direction dd : Direction.values()) { BlockPos n = b.relative(dd); if (!seen.contains(n) && l.getBlockState(n).is(BLAST_DOOR.get())) { seen.add(n); q.add(n); } } }
            l.playSound(null, p, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.5f, 0.4f);
            return r; }
    }

    // ------------------------------------------------------------------ egress and safety
    /** An emergency light (IBC 1008.3: 90 min on battery): two heads, on by default; use presses its test switch. */
    public static final class EmergencyLight extends Fit {
        public EmergencyLight(Properties p) { super(p, false, 2, 4, 12, 14, 10, 16); registerDefaultState(defaultBlockState().setValue(LIT, true)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, LIT); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) { if (!l.isClientSide) l.setBlock(p, s.cycle(LIT), 3); return InteractionResult.sidedSuccess(l.isClientSide); }
    }
    /** A ladder that needs nothing behind it (the fire escape's steel ladder). */
    public static final class EscapeLadder extends LadderBlock {
        public EscapeLadder(Properties p) { super(p); }
        @Override public boolean canSurvive(BlockState s, LevelReader l, BlockPos p) { return true; }
        @Override public BlockState updateShape(BlockState s, Direction d, BlockState n, LevelAccessor l, BlockPos p, BlockPos np) { return s; }
    }
    public static final BooleanProperty EXTENDED = BlockStateProperties.EXTENDED;
    /** The fire escape's drop ladder (counterbalanced): use it (from the street or the landing) to let it down to the ground or pull it up. */
    public static final class DropLadder extends Fit {
        public DropLadder(Properties p) { super(p, false, 0, 13, 0, 16, 16, 16); registerDefaultState(defaultBlockState().setValue(EXTENDED, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, EXTENDED); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (l.isClientSide) return InteractionResult.SUCCESS;
            boolean down = !s.getValue(EXTENDED); BlockState rung = ESCAPE_LADDER.get().defaultBlockState().setValue(LadderBlock.FACING, s.getValue(FACING));
            int n = 0;
            if (down) { for (BlockPos q = p.below(); n < 12 && l.getBlockState(q).canBeReplaced() && l.getFluidState(q).isEmpty(); q = q.below(), n++) l.setBlock(q, rung, 3); }
            else { for (BlockPos q = p.below(); n < 12 && l.getBlockState(q).is(ESCAPE_LADDER.get()); q = q.below(), n++) l.removeBlock(q, false); }
            l.setBlock(p, s.setValue(EXTENDED, down), 3); l.playSound(null, p, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.2f, 0.6f); ladderDrops++;
            return InteractionResult.CONSUME; }
    }
    /** A double-hung window: use it to open (the lower sash goes up: no glass to stop you) or shut; the window cell above or below follows. */
    public static final class SashWindow extends Fit {
        public SashWindow(Properties p) { super(p, false, 0, 0, 7, 16, 16, 9); registerDefaultState(defaultBlockState().setValue(OPEN, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, OPEN); }
        @Override public VoxelShape getCollisionShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) { return s.getValue(OPEN) ? Shapes.empty() : super.getShape(s, g, p, c); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (l.isClientSide) return InteractionResult.SUCCESS; boolean o = !s.getValue(OPEN); l.setBlock(p, s.setValue(OPEN, o), 3);
            for (BlockPos q : new BlockPos[]{p.above(), p.below()}) { BlockState n = l.getBlockState(q); if (n.is(this)) l.setBlock(q, n.setValue(OPEN, o), 3); }
            l.playSound(null, p, o ? SoundEvents.WOODEN_TRAPDOOR_OPEN : SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 0.8f); if (o) windowsOpened++;
            return InteractionResult.CONSUME; }
        @Override public boolean propagatesSkylightDown(BlockState s, BlockGetter g, BlockPos p) { return true; }
    }
    /** Grating: the fire escape landing, 2 px thick at the top of the block, see-through. */
    public static final class Grating extends Block {
        static final VoxelShape S = Block.box(0, 13, 0, 16, 16, 16);
        public Grating(Properties p) { super(p); }
        @Override public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) { return S; }
        @Override public boolean propagatesSkylightDown(BlockState s, BlockGetter g, BlockPos p) { return true; }
    }
    /** A walking ramp (ADA 405): HEIGHT pixels high (1-15); twelve of them climb one block, a slope of 1:12. */
    public static final class Ramp extends Block {
        public static final IntegerProperty HEIGHT = IntegerProperty.create("height", 1, 15);
        static final VoxelShape[] S = new VoxelShape[16];
        static { for (int i = 1; i < 16; i++) S[i] = Block.box(0, 0, 0, 16, i, 16); }
        public Ramp(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(HEIGHT, 8)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(HEIGHT); }
        @Override public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) { return S[s.getValue(HEIGHT)]; }
        @Override public boolean useShapeForLightOcclusion(BlockState s) { return true; }
    }

    // ------------------------------------------------------------------ the railroad crossing
    /** Blocks that start their clock when world generation finishes their chunk (DeepGenerator marks them). */
    public interface Starts {}
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active"), FLASH = BooleanProperty.create("flash"), DOWN = BooleanProperty.create("down");
    public static final IntegerProperty ARM = IntegerProperty.create("arm", 0, 8);
    /**
     * Flashing-light signal with a gate (MUTCD 8C.02, 8C.04): crossbuck on top, two red lights that flash alternately (45-65 a
     * minute) and a bell while a train (a minecart) is within 40 blocks or the block is powered; the gate arm (ARM blocks long, to
     * the driver's left as he faces the signal) comes down over the approach lanes.
     */
    public static final class CrossingSignal extends Fit implements Starts {
        public CrossingSignal(Properties p) { super(p, false, 6, 0, 6, 10, 16, 10); registerDefaultState(defaultBlockState().setValue(ACTIVE, false).setValue(FLASH, false).setValue(ARM, 0)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, ACTIVE, FLASH, ARM); }
        void start(LevelAccessor l, BlockPos p) { if (!l.isClientSide() && !l.getBlockTicks().hasScheduledTick(p, this)) l.scheduleTick(p, this, 10); }
        @Override public void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean m) { start(l, p); }
        @Override public BlockState updateShape(BlockState s, Direction d, BlockState n, LevelAccessor l, BlockPos p, BlockPos np) { start(l, p); return s; }
        @Override public boolean isRandomlyTicking(BlockState s) { return true; }
        @Override public void randomTick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) { start(l, p); }
        @Override public void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
            crossingTicks++;
            boolean train = !l.getEntitiesOfClass(AbstractMinecart.class, new AABB(p).inflate(40, 6, 40)).isEmpty() || l.hasNeighborSignal(p);
            BlockState ns = s.setValue(ACTIVE, train).setValue(FLASH, train && !s.getValue(FLASH));
            if (ns != s) l.setBlock(p, ns, 3);
            if (train) { crossingActive++; l.playSound(null, p, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.2f, 1.4f); }
            if (train != s.getValue(ACTIVE)) arms(l, p, ns, train);
            l.scheduleTick(p, this, 10); }
        /** The arm cells: from beside the mast to the driver's left, at the mast's height + 0. */
        static void arms(Level l, BlockPos p, BlockState s, boolean down) {
            Direction left = s.getValue(FACING).getClockWise();   // the signal faces the driver: his left (his heading turned counter-clockwise) is the signal's facing turned clockwise
            for (int k = 1; k <= s.getValue(ARM); k++) { BlockPos q = p.relative(left, k); BlockState a = l.getBlockState(q);
                if (a.is(CROSSING_ARM.get())) { if (a.getValue(DOWN) != down) { l.setBlock(q, a.setValue(DOWN, down), 3); armsMoved++; } }
                else break; }
        }
        @Override public int getLightEmission(BlockState s, BlockGetter g, BlockPos p) { return s.getValue(ACTIVE) ? 7 : 0; }
    }
    /** A cell of the gate arm: DOWN = across the lane (a bar that stops you), up = nothing there (the arm stands up on the mast). */
    public static final class CrossingArm extends Block {
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        public CrossingArm(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(DOWN, false).setValue(AXIS, Direction.Axis.X)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(DOWN, AXIS); }
        @Override public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
            if (!s.getValue(DOWN)) return Shapes.empty(); return s.getValue(AXIS) == Direction.Axis.X ? Block.box(0, 12, 6, 16, 16, 10) : Block.box(6, 12, 0, 10, 16, 16); }
        @Override public VoxelShape getCollisionShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
            if (!s.getValue(DOWN)) return Shapes.empty(); return s.getValue(AXIS) == Direction.Axis.X ? Block.box(0, 0, 6, 16, 24, 10) : Block.box(6, 0, 0, 10, 24, 16); }
        @Override public boolean canBeReplaced(BlockState s, BlockPlaceContext c) { return !s.getValue(DOWN); }
        @Override public boolean propagatesSkylightDown(BlockState s, BlockGetter g, BlockPos p) { return true; }
    }

    // ------------------------------------------------------------------ the substation
    /** A transformer tank section: gives redstone power (15) and shocks whatever touches it (NESC: live parts behind the fence). */
    public static final class Transformer extends Block {
        static final VoxelShape COL = Block.box(0.5, 0, 0.5, 15.5, 16, 15.5);
        public Transformer(Properties p) { super(p); }
        /** 0.26.1 (Infinity): a transformer touching a power line is fed from the transmission network and keeps the grid live (GridNet), as a
         *  lit generator did in Hopeless Deep; cut the line or break the transformer and the grid round it goes down. */
        public static boolean fed(Level l, BlockPos p) { for (Direction d : Direction.values()) if (l.getBlockState(p.relative(d)).is(POWER_LINE.get())) return true; return false; }
        void update(Level l, BlockPos p) { if (l instanceof net.minecraft.server.level.ServerLevel sl) GridNet.set(sl, p, fed(l, p)); }
        @Override public void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moved) { super.onPlace(s, l, p, old, moved); update(l, p); }
        @Override public void neighborChanged(BlockState s, Level l, BlockPos p, Block b, BlockPos f, boolean m) { update(l, p); }
        @Override public void onRemove(BlockState s, Level l, BlockPos p, BlockState now, boolean moved) { if (!s.is(now.getBlock()) && l instanceof net.minecraft.server.level.ServerLevel sl) GridNet.set(sl, p, false); super.onRemove(s, l, p, now, moved); }
        @Override public VoxelShape getCollisionShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) { return COL; }
        @Override public void entityInside(BlockState s, Level l, BlockPos p, Entity e) {
            if (l.isClientSide || !(e instanceof LivingEntity le) || le.invulnerableTime > 0) return;
            le.hurt(l.damageSources().lightningBolt(), 4f); Vec3 away = e.position().subtract(Vec3.atCenterOf(p)).normalize().scale(0.6); e.setDeltaMovement(away.x, 0.3, away.z); e.hurtMarked = true;
            l.playSound(null, p, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.5f, 2f); if (l instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 1, e.getZ(), 20, 0.3, 0.5, 0.3, 0.2); shocks++; }
        @Override public boolean isSignalSource(BlockState s) { return true; }
        @Override public int getSignal(BlockState s, BlockGetter g, BlockPos p, Direction d) { return 15; }
        @Override public void animateTick(BlockState s, Level l, BlockPos p, RandomSource r) { if (r.nextInt(40) == 0) l.playLocalSound(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.3f, 0.5f, false); }
    }
    /** Chain-link fence and its danger-sign panel: they join like iron bars. */
    public static final class Bars extends IronBarsBlock { public Bars(Properties p) { super(p); } }
    /** 0.32.0: the power plant's turbine and generator casings (when no machine mod is here): the generator gives redstone power, both hum. */
    public static final class Machine extends Block {
        final boolean gen;
        public Machine(Properties p, boolean gen) { super(p); this.gen = gen; }
        @Override public boolean isSignalSource(BlockState s) { return gen; }
        @Override public int getSignal(BlockState s, BlockGetter g, BlockPos p, Direction d) { return gen ? 15 : 0; }
        @Override public void animateTick(BlockState s, Level l, BlockPos p, RandomSource r) { if (r.nextInt(60) == 0) l.playLocalSound(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.25f, gen ? 0.7f : 0.5f, false); }
    }
    /** Barbed wire on top of a fence (joins like bars): cuts and slows whatever goes through it. */
    public static final class BarbedWire extends IronBarsBlock {
        public BarbedWire(Properties p) { super(p); }
        @Override public void entityInside(BlockState s, Level l, BlockPos p, Entity e) {
            e.makeStuckInBlock(s, new Vec3(0.6, 0.6, 0.6));
            if (!l.isClientSide && e instanceof LivingEntity le && le.invulnerableTime == 0) { le.hurt(l.damageSources().cactus(), 1f); barbs++; } }
    }
    /** A power line: a conductor along its axis that shocks what touches it. */
    public static final class PowerLine extends RotatedPillarBlock {
        public PowerLine(Properties p) { super(p); }
        @Override public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
            return switch (s.getValue(AXIS)) { case X -> Block.box(0, 7, 7, 16, 9, 9); case Z -> Block.box(7, 7, 0, 9, 9, 16); default -> Block.box(7, 0, 7, 9, 16, 9); }; }
        @Override public void entityInside(BlockState s, Level l, BlockPos p, Entity e) { TRANSFORMER.get().entityInside(s, l, p, e); }
    }

    // ------------------------------------------------------------------ the fire engine
    /** The fire engine in its bay (3 wide, 8 long, the front toward FACING): its tank (NFPA 1901: 300 gal+) fills a bucket; use for the horn. */
    public static final class FireEngine extends com.hopeless.infinity.street.StreetReg.Car {
        public FireEngine(Properties p) { super(p, false); }
        public static java.util.List<BlockPos> cells3(BlockPos p, Direction f) {
            java.util.List<BlockPos> out = new java.util.ArrayList<>(); Direction right = f.getClockWise();
            for (int a = 0; a < 8; a++) for (int c = 0; c < 3; c++) for (int y = 0; y < 3; y++) out.add(p.relative(f, a).relative(right, c).above(y));
            return out; }
        @Override public void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moving) {
            if (l.isClientSide || old.is(this)) return;
            for (BlockPos q : cells3(p, s.getValue(FACING))) if (!q.equals(p) && l.getBlockState(q).canBeReplaced()) l.setBlock(q, com.hopeless.infinity.street.StreetReg.CAR_SPACE.get().defaultBlockState(), 3); }
        @Override public void onRemove(BlockState s, Level l, BlockPos p, BlockState now, boolean moving) {
            if (!s.is(now.getBlock())) for (BlockPos q : cells3(p, s.getValue(FACING))) if (l.getBlockState(q).is(com.hopeless.infinity.street.StreetReg.CAR_SPACE.get())) l.removeBlock(q, false);
            super.onRemove(s, l, p, now, moving); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (fillWater(l, p, pl, h)) { engineUses++; return InteractionResult.sidedSuccess(l.isClientSide); }
            if (!l.isClientSide) { l.playSound(null, p, SoundEvents.RAID_HORN.value(), SoundSource.BLOCKS, 1.2f, 1.6f); engineUses++; }
            return InteractionResult.sidedSuccess(l.isClientSide); }
    }

    // ------------------------------------------------------------------ registration
    static BlockBehaviour.Properties metal(MapColor c) { return BlockBehaviour.Properties.of().mapColor(c).strength(3f, 6f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion(); }
    static BlockBehaviour.Properties stone(MapColor c) { return BlockBehaviour.Properties.of().mapColor(c).strength(1.5f, 6f).requiresCorrectToolForDrops().noOcclusion(); }
    static final java.util.List<RegistryObject<Item>> TAB = new java.util.ArrayList<>();
    static RegistryObject<Block> reg(String id, Supplier<Block> s, boolean item) {
        RegistryObject<Block> b = BLOCKS.register(id, s);
        if (item) TAB.add(ITEMS.register(id, () -> new BlockItem(b.get(), new Item.Properties())));
        return b; }
    static RegistryObject<Block> reg(String id, Supplier<Block> s) { return reg(id, s, true); }

    /** 0.27.0 welded steel bar grating (NAAMM MBG 531 type 19-W-4), the gate ramps' walking surface. */
    public static final RegistryObject<Block> STEEL_GRATE = reg("steel_grate", () -> new Block(metal(MapColor.METAL)));
    public static final RegistryObject<Block> SINK = reg("sink", () -> new Sink(metal(MapColor.SNOW).sound(SoundType.STONE)));
    public static final RegistryObject<Block> SHOWER = reg("shower", () -> new Shower(metal(MapColor.SNOW)));
    public static final RegistryObject<Block> WASHER = reg("washer", () -> new Washer(metal(MapColor.SNOW)));
    public static final RegistryObject<Block> ATM = reg("atm", () -> new Vend(metal(MapColor.COLOR_GRAY).strength(10f, 600f), Kind.ATM, 1, 0, 8, 15, 16, 16));
    public static final RegistryObject<Block> CHANGE_MACHINE = reg("change_machine", () -> new Vend(metal(MapColor.COLOR_GRAY), Kind.CHANGE, 2, 0, 5, 14, 16, 16));
    public static final RegistryObject<Block> VENDING_MACHINE = reg("vending_machine", () -> new TallVend(metal(MapColor.COLOR_RED).lightLevel(s -> s.getValue(Tall.HALF) == DoubleBlockHalf.UPPER ? 6 : 0), Kind.SNACK));
    public static final RegistryObject<Block> DRINK_COOLER = reg("drink_cooler", () -> new TallVend(metal(MapColor.SNOW).lightLevel(s -> s.getValue(Tall.HALF) == DoubleBlockHalf.UPPER ? 8 : 0), Kind.DRINK));
    public static final RegistryObject<Block> FRIDGE = reg("fridge", () -> new Fridge(metal(MapColor.SNOW)));
    public static final RegistryObject<Block> FRIDGE_TOP = reg("fridge_top", () -> new FridgeTop(metal(MapColor.SNOW).noLootTable()), false);
    public static final RegistryObject<Block> SUPPLY_CABINET = reg("supply_cabinet", () -> new BoxBlock(metal(MapColor.SNOW), 1, 0, 2, 15, 16, 16));
    public static final RegistryObject<Block> CASH_REGISTER = reg("cash_register", () -> new BoxBlock(metal(MapColor.COLOR_BLACK), 2, 0, 3, 14, 9, 15));
    public static final RegistryObject<Block> PO_BOX = reg("po_box", () -> new BoxBlock(metal(MapColor.METAL).strength(5f, 60f)));
    public static final RegistryObject<Block> FIRE_EXTINGUISHER = reg("fire_extinguisher", () -> new Extinguisher(metal(MapColor.COLOR_RED)));
    public static final RegistryObject<Block> HOSE_VALVE = reg("hose_valve", () -> new HoseValve(metal(MapColor.COLOR_RED)));
    public static final RegistryObject<Block> FDC = reg("fire_department_connection", () -> new Fdc(metal(MapColor.GOLD)));
    public static final RegistryObject<Block> TUBE_STATION = reg("tube_station", () -> new TubeStation(metal(MapColor.METAL)));
    public static final RegistryObject<Block> METAL_DETECTOR = reg("metal_detector", () -> new MetalDetector(metal(MapColor.COLOR_LIGHT_GRAY)));
    public static final RegistryObject<Block> XRAY_BELT = reg("xray_belt", () -> new XrayBelt(metal(MapColor.COLOR_GRAY)));
    public static final RegistryObject<Block> CALL_BUTTON = reg("call_button", () -> new CallButton(BlockBehaviour.Properties.copy(Blocks.STONE_BUTTON)));
    public static final RegistryObject<Block> CALL_LIGHT = reg("call_light", () -> new CallLight(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.5f).sound(SoundType.GLASS).lightLevel(s -> s.getValue(LIT) ? 10 : 0)));
    public static final RegistryObject<Block> MENU_BOARD = reg("menu_board", () -> new MenuBoard(metal(MapColor.COLOR_BLACK).lightLevel(s -> 9)));
    public static final RegistryObject<Block> CHALKBOARD = reg("chalkboard", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(1f).sound(SoundType.WOOD)));
    public static final RegistryObject<Block> SCREEN = reg("projection_screen", () -> new Screen(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.8f).sound(SoundType.WOOL).lightLevel(s -> s.getValue(Screen.ON) ? 11 : 0)));
    public static final RegistryObject<Block> PROJECTOR = reg("projector", () -> new Projector(metal(MapColor.COLOR_BLACK)));
    public static final RegistryObject<Block> SATELLITE_DISH = reg("satellite_dish", () -> new Fit(metal(MapColor.SNOW), false, 1, 0, 1, 15, 16, 15));
    public static final RegistryObject<Block> ROOF_BOLT = reg("roof_bolt", () -> new Fit(metal(MapColor.METAL), false, 5, 14, 5, 11, 16, 11));
    public static final RegistryObject<Block> CAR_LIFT = reg("car_lift", () -> new CarLift(metal(MapColor.COLOR_YELLOW)));
    /** 0.38.0: the car lift runs the repair shop (game.AutoShop): use it - a quote for the nearest vehicle; sneak - repair it; with a dye - repaint it. */
    public static final class CarLift extends Fit { public CarLift(Properties p) { super(p, false, 4, 0, 4, 12, 16, 12); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (l instanceof ServerLevel sl && pl instanceof net.minecraft.server.level.ServerPlayer sp) com.hopeless.infinity.game.AutoShop.use(sl, sp, p, h); return InteractionResult.sidedSuccess(l.isClientSide); } }
    public static final RegistryObject<Block> HOIST_STATION = reg("hoist_station", () -> new HoistStation(metal(MapColor.COLOR_YELLOW)));
    public static final RegistryObject<Block> BLAST_DOOR = reg("blast_door", () -> new BlastDoor(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(50f, 1200f).requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK)));
    public static final RegistryObject<Block> BLAST_CONTROL = reg("blast_door_control", () -> new BlastControl(BlockBehaviour.Properties.copy(Blocks.LEVER)));
    public static final RegistryObject<Block> EXIT_SIGN = reg("exit_sign", () -> new Fit(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.5f).sound(SoundType.GLASS).noOcclusion().noCollission().lightLevel(s -> 10), false, 2, 5, 14, 14, 11, 16));
    public static final RegistryObject<Block> EMERGENCY_LIGHT = reg("emergency_light", () -> new EmergencyLight(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.5f).sound(SoundType.METAL).noOcclusion().noCollission().lightLevel(s -> s.getValue(LIT) ? 13 : 0)));
    public static final RegistryObject<Block> GRATING = reg("grating", () -> new Grating(metal(MapColor.COLOR_BLACK)));
    public static final RegistryObject<Block> ESCAPE_LADDER = reg("escape_ladder", () -> new EscapeLadder(BlockBehaviour.Properties.copy(Blocks.LADDER).sound(SoundType.METAL).strength(2f)));
    public static final RegistryObject<Block> DROP_LADDER = reg("drop_ladder", () -> new DropLadder(metal(MapColor.COLOR_BLACK)));
    public static final RegistryObject<Block> SASH_WINDOW = reg("sash_window", () -> new SashWindow(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.6f).sound(SoundType.GLASS).noOcclusion()));
    public static final RegistryObject<Block> CROSSING_SIGNAL = reg("crossing_signal", () -> new CrossingSignal(metal(MapColor.COLOR_LIGHT_GRAY).randomTicks()));
    public static final RegistryObject<Block> CROSSING_ARM = reg("crossing_arm", () -> new CrossingArm(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(1f).noOcclusion().noLootTable()), false);
    public static final RegistryObject<Block> STOP_SIGN = reg("stop_sign", () -> new Fit(metal(MapColor.COLOR_RED), false, 7, 0, 7, 9, 16, 9));
    public static final RegistryObject<Block> SPEED_LIMIT_25 = reg("speed_limit_25", () -> new Fit(metal(MapColor.SNOW), false, 7, 0, 7, 9, 16, 9));
    public static final RegistryObject<Block> SPEED_LIMIT_30 = reg("speed_limit_30", () -> new Fit(metal(MapColor.SNOW), false, 7, 0, 7, 9, 16, 9));
    public static final RegistryObject<Block> SPEED_LIMIT_35 = reg("speed_limit_35", () -> new Fit(metal(MapColor.SNOW), false, 7, 0, 7, 9, 16, 9));
    public static final RegistryObject<Block> ACCESSIBLE_SIGN = reg("accessible_sign", () -> new Fit(metal(MapColor.COLOR_BLUE), false, 7, 0, 7, 9, 16, 9));
    public static final RegistryObject<Block> DETECTABLE_WARNING = reg("detectable_warning", () -> new Block(stone(MapColor.COLOR_YELLOW)));
    public static final RegistryObject<Block> RAMP = reg("ramp", () -> new Ramp(stone(MapColor.STONE)));
    public static final RegistryObject<Block> CHAIN_LINK = reg("chain_link_fence", () -> new Bars(metal(MapColor.METAL).strength(2f, 6f)));
    public static final RegistryObject<Block> FENCE_SIGN = reg("fence_danger_sign", () -> new Bars(metal(MapColor.COLOR_RED).strength(2f, 6f)));
    public static final RegistryObject<Block> CHAIN_LINK_GATE = reg("chain_link_gate", () -> new FenceGateBlock(metal(MapColor.METAL).strength(2f, 6f), net.minecraft.world.level.block.state.properties.WoodType.OAK));
    public static final RegistryObject<Block> BARBED_WIRE = reg("barbed_wire", () -> new BarbedWire(metal(MapColor.METAL).strength(1.5f, 6f).noCollission()));
    public static final RegistryObject<Block> TRANSFORMER = reg("transformer", () -> new Transformer(metal(MapColor.COLOR_GRAY).strength(8f, 30f)));
    public static final RegistryObject<Block> POWER_LINE = reg("power_line", () -> new PowerLine(metal(MapColor.COLOR_BLACK).noCollission()));
    public static final RegistryObject<Block> TURBINE = reg("turbine", () -> new Machine(metal(MapColor.COLOR_LIGHT_BLUE).strength(8f, 30f), false));
    public static final RegistryObject<Block> GENERATOR = reg("generator", () -> new Machine(metal(MapColor.COLOR_GREEN).strength(8f, 30f), true));
    public static final RegistryObject<Block> FIRE_ENGINE = reg("fire_engine", () -> new FireEngine(metal(MapColor.COLOR_RED).strength(6f, 20f)));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<CityBox>> CITY_BOX = BES.register("city_box", () -> BlockEntityType.Builder.of(CityBox::new, FRIDGE.get(), SUPPLY_CABINET.get(), CASH_REGISTER.get(), PO_BOX.get()).build(null));

    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); BES.register(bus);
        bus.addListener((net.minecraftforge.event.BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) for (RegistryObject<Item> it : TAB) e.accept(it.get()); });
    }
    public static String probe() {
        return "city fittings: water filled " + filled + " washed " + washed + " vended " + vended + " sold out " + soldOut + " change " + changeMade + " fires out " + extinguished
            + " tubes " + tubesSent + " detector rings " + detectorRings + " belt pushes " + beltPushes + " calls " + calls + " (reset " + callsReset + ") screens " + screens
            + " hoist rides " + hoistRides + " blast plates moved " + blastMoves + " hose valve uses " + valveUses + " drop ladders " + ladderDrops + " windows opened " + windowsOpened
            + " | crossing ticks " + crossingTicks + " active " + crossingActive + " arms moved " + armsMoved + " | shocks " + shocks + " barbs " + barbs + " engine " + engineUses + " drains " + drained;
    }
}
