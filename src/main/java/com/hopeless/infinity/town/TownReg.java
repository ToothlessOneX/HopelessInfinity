package com.hopeless.infinity.town;

import com.hopeless.infinity.HopelessInfinity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 0.30.0: things for the towns that no installed mod provides (made for the mod, textures and models included): a safe (a steel loot
 * box for pawn shops, pharmacies and basements), the abandoned car (a rusted car whose trunk holds loot), the vault lever (in the bank
 * manager's office: it opens and shuts the vault's iron doors), the night light (an unseen lamp behind city windows: it comes on after
 * dusk and some go out late at night), and the sewer rat.
 */
public final class TownReg {
    private TownReg() {}
    public static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HopelessInfinity.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HopelessInfinity.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HopelessInfinity.MODID);
    public static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HopelessInfinity.MODID);
    public static volatile long pumpsShut, pumpsDry, fuelDrawn, cageLocked, cageOpened, safesOpened, trunksOpened, vaultPulls, vaultDoors, lightsOn, lightsOff;   // probes

    /** A steel safe: a 27-slot loot box with a door on its front. */
    public static final class Safe extends HorizontalDirectionalBlock implements EntityBlock {
        public Safe(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> b) { b.add(FACING); }
        @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
        @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new LootBox(p, s); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (!l.isClientSide && l.getBlockEntity(p) instanceof LootBox b) { pl.openMenu(b); safesOpened++; l.playSound(null, p, net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 0.7f); }
            return InteractionResult.sidedSuccess(l.isClientSide); }
        @Override public void onRemove(BlockState s, Level l, BlockPos p, BlockState now, boolean moved) {
            if (!s.is(now.getBlock()) && l.getBlockEntity(p) instanceof LootBox b) Containers.dropContents(l, p, b); super.onRemove(s, l, p, now, moved); }
    }
    /** The abandoned car: a parked car gone to rust, its anchor cell holds the trunk. */
    public static final class Wreck extends com.hopeless.infinity.street.StreetReg.Car implements EntityBlock {
        public Wreck(Properties p) { super(p, false); }
        @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new LootBox(p, s); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) { return openTrunk(l, p, pl); }
        public static InteractionResult openTrunk(Level l, BlockPos p, Player pl) {
            if (!l.isClientSide && l.getBlockEntity(p) instanceof LootBox b) {
                if (l instanceof net.minecraft.server.level.ServerLevel sl) try { com.hopeless.infinity.life.LootFill.open(sl, p); } catch (Throwable t) { }
                pl.openMenu(b); trunksOpened++; l.playSound(null, p, net.minecraft.sounds.SoundEvents.CHEST_OPEN, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 0.6f); }
            return InteractionResult.sidedSuccess(l.isClientSide); }
        @Override public void onRemove(BlockState s, Level l, BlockPos p, BlockState now, boolean moved) {
            if (!s.is(now.getBlock()) && l.getBlockEntity(p) instanceof LootBox b) Containers.dropContents(l, p, b); super.onRemove(s, l, p, now, moved); }
    }
    /** The vault lever: a lever that also opens (on) or shuts (off) every iron door within 24 blocks of it. */
    public static final class VaultLever extends LeverBlock {
        public VaultLever(Properties p) { super(p); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            InteractionResult r = super.use(s, l, p, pl, h, hit);
            if (!l.isClientSide) { boolean on = l.getBlockState(p).getValue(POWERED); vaultPulls++;
                for (BlockPos q : BlockPos.betweenClosed(p.offset(-24, -4, -24), p.offset(24, 4, 24))) { BlockState d = l.getBlockState(q);
                    if (d.getBlock() instanceof DoorBlock door && d.is(Blocks.IRON_DOOR) && d.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER && d.getValue(DoorBlock.OPEN) != on) {
                        door.setOpen(pl, l, d, q.immutable(), on); vaultDoors++; } } }
            return r; }
    }
    /** An unseen lamp: no shape, no model; lit (light 11) at night. Random ticks turn it on after dusk; a third go out after midnight. */
    public static final class NightLight extends net.minecraft.world.level.block.Block {
        public static final BooleanProperty LIT = BooleanProperty.create("lit");
        public NightLight(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(LIT, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> b) { b.add(LIT); }
        @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.INVISIBLE; }
        @Override public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) { return Shapes.empty(); }
        @Override public boolean canBeReplaced(BlockState s, net.minecraft.world.item.context.BlockPlaceContext c) { return true; }
        @Override public boolean propagatesSkylightDown(BlockState s, BlockGetter g, BlockPos p) { return true; }
        @Override public boolean isRandomlyTicking(BlockState s) { return true; }
        public static boolean shouldLight(long dayTime, BlockPos p) {
            long t = Math.floorMod(dayTime, 24000L); boolean night = t >= 12800 && t <= 23200;
            boolean lateOff = t >= 18000 && t <= 23200 && Math.floorMod(p.asLong() * 0x9E3779B97F4A7C15L >>> 40, 3) == 0;
            return night && !lateOff; }
        @Override public void randomTick(BlockState s, net.minecraft.server.level.ServerLevel l, BlockPos p, net.minecraft.util.RandomSource r) {
            boolean want = shouldLight(l.getDayTime(), p); if (want != s.getValue(LIT)) { l.setBlock(p, s.setValue(LIT, want), 2); if (want) lightsOn++; else lightsOff++; } }   // 0.36.0: flag 2 - to the clients, no neighbour updates (nothing reacts to an unseen lamp)
    }

    /** 0.31.0: a fuel pump (the gas station's dispensers): use it with an empty bucket to fill a fuel bucket; it holds FUEL buckets and
     *  runs dry (the world's stock does not come back). */
    public static final class FuelPump extends HorizontalDirectionalBlock {
        public static final net.minecraft.world.level.block.state.properties.IntegerProperty FUEL = net.minecraft.world.level.block.state.properties.IntegerProperty.create("fuel", 0, 8);
        public FuelPump(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FUEL, 8)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> b) { b.add(FACING, FUEL); }
        @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
        @Override public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) { return net.minecraft.world.level.block.Block.box(2, 0, 2, 14, 16, 14); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            var it = pl.getItemInHand(h); if (!it.is(net.minecraft.world.item.Items.BUCKET)) return InteractionResult.PASS;
            if (!l.isClientSide && shutOff(l, p)) { l.playSound(null, p, net.minecraft.sounds.SoundEvents.DISPENSER_FAIL, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 0.5f); pumpsShut++; pl.displayClientMessage(net.minecraft.network.chat.Component.literal("Emergency fuel shutoff is on"), true); return InteractionResult.CONSUME; }
            if (s.getValue(FUEL) == 0) { if (!l.isClientSide) { l.playSound(null, p, net.minecraft.sounds.SoundEvents.DISPENSER_FAIL, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 0.8f); pumpsDry++; } return InteractionResult.sidedSuccess(l.isClientSide); }
            if (!l.isClientSide && !com.hopeless.infinity.game.Fuel.pay((net.minecraft.server.level.ServerLevel) l, pl)) return InteractionResult.CONSUME;   // 0.38.0: $18 a bucket
            if (!l.isClientSide) { l.setBlock(p, s.setValue(FUEL, s.getValue(FUEL) - 1), 3); pl.setItemInHand(h, net.minecraft.world.item.ItemUtils.createFilledResult(it, pl, new net.minecraft.world.item.ItemStack(FUEL_BUCKET.get())));
                l.playSound(null, p, net.minecraft.sounds.SoundEvents.BUCKET_FILL_LAVA, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.3f); fuelDrawn++; }
            return InteractionResult.sidedSuccess(l.isClientSide); }
    }
    /** Is an emergency shutoff (NFPA 30A 6.7: one per station, 20-100 ft from the dispensers) switched on within 40 blocks? */
    static boolean shutOff(Level l, BlockPos p) {
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-40, -3, -40), p.offset(40, 3, 40))) { BlockState b = l.getBlockState(q); if (b.getBlock() instanceof FuelShutoff && b.getValue(LeverBlock.POWERED)) return true; }
        return false; }
    /** 0.31.0: the emergency fuel shutoff (NFPA 30A 6.7): a lever on a post; while it is on, the station's pumps give nothing. */
    public static final class FuelShutoff extends LeverBlock { public FuelShutoff(Properties p) { super(p); } }
    /** 0.31.0: the evidence cage's door: an iron door that only opens for someone holding the evidence key. */
    public static final class CageDoor extends DoorBlock {
        public CageDoor(Properties p) { super(p, net.minecraft.world.level.block.state.properties.BlockSetType.IRON); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (!pl.getItemInHand(h).is(EVIDENCE_KEY.get())) { if (!l.isClientSide) { l.playSound(null, p, net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.6f); cageLocked++; } return InteractionResult.sidedSuccess(l.isClientSide); }
            boolean open = !s.getValue(OPEN); setOpen(pl, l, s, p, open); if (!l.isClientSide) cageOpened++; return InteractionResult.sidedSuccess(l.isClientSide); }
    }
    static BlockBehaviour.Properties steel() { return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(25f, 1200f).requiresCorrectToolForDrops().sound(net.minecraft.world.level.block.SoundType.NETHERITE_BLOCK); }
    public static final RegistryObject<net.minecraft.world.level.block.Block> SAFE = BLOCKS.register("safe", () -> new Safe(steel()));
    public static final RegistryObject<Item> SAFE_ITEM = ITEMS.register("safe", () -> new BlockItem(SAFE.get(), new Item.Properties()));
    public static final RegistryObject<net.minecraft.world.level.block.Block> WRECK = BLOCKS.register("wreck", () -> new Wreck(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(4f, 12f)));
    public static final RegistryObject<net.minecraft.world.level.block.Block> VAULT_LEVER = BLOCKS.register("vault_lever", () -> new VaultLever(BlockBehaviour.Properties.copy(Blocks.LEVER)));
    public static final RegistryObject<Item> VAULT_LEVER_ITEM = ITEMS.register("vault_lever", () -> new BlockItem(VAULT_LEVER.get(), new Item.Properties()));
    public static final RegistryObject<net.minecraft.world.level.block.Block> NIGHT_LIGHT = BLOCKS.register("night_light", () -> new NightLight(BlockBehaviour.Properties.of().noCollission().noOcclusion().noLootTable().replaceable().randomTicks()
        .lightLevel(s -> s.getValue(NightLight.LIT) ? 11 : 0)));
    public static final RegistryObject<net.minecraft.world.level.block.Block> FUEL_PUMP = BLOCKS.register("fuel_pump", () -> new FuelPump(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(3f, 6f).requiresCorrectToolForDrops().sound(net.minecraft.world.level.block.SoundType.METAL)));
    public static final RegistryObject<Item> FUEL_PUMP_ITEM = ITEMS.register("fuel_pump", () -> new BlockItem(FUEL_PUMP.get(), new Item.Properties()));
    /** A bucket of fuel: burns in a furnace as long as a lava bucket (the bucket comes back). */
    public static final RegistryObject<Item> FUEL_BUCKET = ITEMS.register("fuel_bucket", () -> new Item(new Item.Properties().stacksTo(1).craftRemainder(net.minecraft.world.item.Items.BUCKET)) {
        @Override public int getBurnTime(net.minecraft.world.item.ItemStack st, net.minecraft.world.item.crafting.RecipeType<?> t) { return 20000; } });
    public static final RegistryObject<net.minecraft.world.level.block.Block> FUEL_SHUTOFF = BLOCKS.register("fuel_shutoff", () -> new FuelShutoff(BlockBehaviour.Properties.copy(Blocks.LEVER)));
    public static final RegistryObject<Item> FUEL_SHUTOFF_ITEM = ITEMS.register("fuel_shutoff", () -> new BlockItem(FUEL_SHUTOFF.get(), new Item.Properties()));
    public static final RegistryObject<net.minecraft.world.level.block.Block> CAGE_DOOR = BLOCKS.register("cage_door", () -> new CageDoor(BlockBehaviour.Properties.copy(Blocks.IRON_DOOR).strength(50f, 1200f)));
    public static final RegistryObject<Item> EVIDENCE_KEY = ITEMS.register("evidence_key", () -> new Item(new Item.Properties().stacksTo(1)));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<LootBox>> LOOT_BOX = BES.register("loot_box", () -> BlockEntityType.Builder.of(LootBox::new, SAFE.get(), WRECK.get()).build(null));
    public static final RegistryObject<net.minecraft.world.entity.EntityType<Rat>> RAT = ENTITIES.register("rat", () ->
        net.minecraft.world.entity.EntityType.Builder.of(Rat::new, net.minecraft.world.entity.MobCategory.MONSTER).sized(0.5f, 0.35f).clientTrackingRange(8).build("rat"));
    public static final RegistryObject<Item> RAT_EGG = ITEMS.register("rat_spawn_egg", () -> new net.minecraftforge.common.ForgeSpawnEggItem(RAT, 0x5A4A3E, 0xD9A0A0, new Item.Properties()));

    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); BES.register(bus); ENTITIES.register(bus);
        bus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e) -> e.put(RAT.get(), Rat.attributes().build()));
        bus.addListener((net.minecraftforge.event.BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) { e.accept(SAFE_ITEM.get()); e.accept(VAULT_LEVER_ITEM.get()); e.accept(FUEL_PUMP_ITEM.get()); e.accept(FUEL_SHUTOFF_ITEM.get()); e.accept(FUEL_BUCKET.get()); e.accept(EVIDENCE_KEY.get()); }
            else if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.SPAWN_EGGS) e.accept(RAT_EGG.get()); });
    }
    public static String probe() { return "town things: fuel drawn " + fuelDrawn + " pumps dry " + pumpsDry + " shut off " + pumpsShut + " cage locked " + cageLocked + " opened " + cageOpened + " | safes opened " + safesOpened + " trunks opened " + trunksOpened + " vault pulls " + vaultPulls + " (doors moved " + vaultDoors + ") night lights on " + lightsOn + " off " + lightsOff + " rats killed " + Rat.killed; }
}
