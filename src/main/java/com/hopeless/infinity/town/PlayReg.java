package com.hopeless.infinity.town;

import com.hopeless.infinity.game.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.RegistryObject;

/**
 * 0.38.0: the blocks and items of the town game (game package): the job board, the For Sale sign, the ballot box, the fire
 * sprinkler (NFPA 13), the elevator button, the fare gate, the escalator, the radio and the atlas. Registered with OwnReg's
 * registers (OwnReg.register touches this class first).
 */
public final class PlayReg {
    private PlayReg() {}
    public static void touch() {}
    public static volatile long boardUses, signUses, ballotUses, sprinklerTrips, rides, gatePasses, gateRefused, escalated;   // probes
    public static final BooleanProperty SOLD = BooleanProperty.create("sold"), OPEN = BlockStateProperties.OPEN, WET = BooleanProperty.create("wet");

    /** Job board: a cork board on the wall. */
    public static final class JobBoard extends CityReg.Fit {
        public JobBoard(BlockBehaviour.Properties p) { super(p, false, 0, 0, 14, 16, 16, 16); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (l instanceof ServerLevel sl && pl instanceof ServerPlayer sp) { boardUses++;
                if (pl.getItemInHand(h).is(net.minecraft.world.item.Items.MAP)) { Atlas.printMap(sl, sp, h, p); return InteractionResult.CONSUME; }
                Jobs.use(sl, sp, p, pl.isShiftKeyDown()); }
            return InteractionResult.sidedSuccess(l.isClientSide); }
    }
    /** For Sale sign on a post; SOLD once taken. */
    public static final class SaleSign extends CityReg.Fit {
        public SaleSign(BlockBehaviour.Properties p) { super(p, false, 6, 0, 7, 10, 16, 9); registerDefaultState(defaultBlockState().setValue(SOLD, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, SOLD); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (l instanceof ServerLevel sl && pl instanceof ServerPlayer sp) { signUses++; Realty.use(sl, sp, p, pl.isShiftKeyDown());
                boolean taken = Realty.taken(sl, p.getX(), p.getZ());
                if (taken != s.getValue(SOLD)) l.setBlock(p, s.setValue(SOLD, taken), 3); }
            return InteractionResult.sidedSuccess(l.isClientSide); }
    }
    /** Ballot box in city hall. */
    public static final class BallotBox extends CityReg.Fit {
        public BallotBox(BlockBehaviour.Properties p) { super(p, false, 2, 0, 2, 14, 15, 14); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (l instanceof ServerLevel sl && pl instanceof ServerPlayer sp) { ballotUses++; Council.use(sl, sp, p, pl.isShiftKeyDown()); }
            return InteractionResult.sidedSuccess(l.isClientSide); }
    }
    /**
     * A pendant fire sprinkler (NFPA 13: light hazard, one head per 225 sq ft, 15 ft apart at most - here every 4 blocks). It costs
     * nothing until there is fire: when fire appears within 5 blocks across and 6 below a head (Forge NeighborNotifyEvent: fire placed or
     * spreading), the heads over it open (WET, water falls), put out every fire within 5 blocks, and close again after 10 s.
     */
    public static final class Sprinkler extends Block {
        static final VoxelShape S = Block.box(6, 11, 6, 10, 16, 10);
        public Sprinkler(BlockBehaviour.Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(WET, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(WET); }
        @Override public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) { return S; }
        @Override public VoxelShape getCollisionShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) { return Shapes.empty(); }
        @Override public void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) { if (s.getValue(WET)) l.setBlock(p, s.setValue(WET, false), 3); }
        /** Fire appeared at f: open the heads over it. */
        public static void fire(ServerLevel l, BlockPos f) {
            for (BlockPos q : BlockPos.betweenClosed(f.offset(-5, 0, -5), f.offset(5, 6, 5))) { BlockState s = l.getBlockState(q); if (!(s.getBlock() instanceof Sprinkler sp)) continue;
                BlockPos p = q.immutable();
                for (BlockPos z : BlockPos.betweenClosed(p.offset(-5, -6, -5), p.offset(5, 1, 5))) if (l.getBlockState(z).is(net.minecraft.tags.BlockTags.FIRE)) l.removeBlock(z, false);
                if (!s.getValue(WET)) { l.setBlock(p, s.setValue(WET, true), 3); sprinklerTrips++; l.playSound(null, p, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1f, 1.2f); }
                l.scheduleTick(p, sp, 200); l.sendParticles(ParticleTypes.FALLING_WATER, p.getX() + 0.5, p.getY() + 0.3, p.getZ() + 0.5, 40, 1.8, 0.1, 1.8, 0); } }
        @Override public void animateTick(BlockState s, Level l, BlockPos p, RandomSource r) {
            if (s.getValue(WET)) for (int i = 0; i < 6; i++) l.addParticle(ParticleTypes.FALLING_WATER, p.getX() + 0.5 + (r.nextDouble() - 0.5) * 3, p.getY() + 0.5, p.getZ() + 0.5 + (r.nextDouble() - 0.5) * 3, 0, 0, 0); }
    }
    /** Elevator button beside each floor's lift door: use it to ride up to the next floor's button, sneak and use to ride down. */
    public static final class ElevatorButton extends ButtonBlock {
        public ElevatorButton(BlockBehaviour.Properties p) { super(p, BlockSetType.IRON, 10, false); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            InteractionResult r = super.use(s, l, p, pl, h, hit); if (l.isClientSide) return InteractionResult.SUCCESS;
            int dir = pl.isShiftKeyDown() ? -1 : 1; BlockPos to = null;
            for (int dy = 2; dy <= 96 && to == null; dy++) { BlockPos q = p.offset(0, dir * dy, 0); if (l.getBlockState(q).getBlock() instanceof ElevatorButton) to = q; }
            if (to == null) { pl.displayClientMessage(Component.literal(dir > 0 ? "Top floor" : "Ground floor"), true); return r; }
            Direction f = s.getValue(FACING); Vec3 at = Vec3.atBottomCenterOf(to.relative(f)).add(0, -1, 0);   // stand in front of the button, on the floor
            l.playSound(null, p, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1f, 1.6f);
            pl.teleportTo(at.x, at.y, at.z); l.playSound(null, to, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1f, 1.2f); rides++;
            pl.displayClientMessage(Component.literal("Floor " + (1 + Math.max(0, countBelow(l, to)))), true); return r; }
        static int countBelow(Level l, BlockPos p) { int n = 0; for (int dy = 2; dy <= 128; dy++) if (l.getBlockState(p.below(dy)).getBlock() instanceof ElevatorButton) n++; return n; }
    }
    /** Fare gate at a station: use it with a transit ticket - it takes the ticket and opens for 3 s; from the paid side (where FACING points) it opens free. */
    public static final class FareGate extends CityReg.Fit {
        public FareGate(BlockBehaviour.Properties p) { super(p, false, 0, 0, 0, 16, 16, 16); registerDefaultState(defaultBlockState().setValue(OPEN, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, OPEN); }
        @Override public VoxelShape getCollisionShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) { return s.getValue(OPEN) ? Shapes.empty() : Block.box(0, 0, 0, 16, 24, 16); }
        @Override public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) { return Block.box(0, 0, 0, 16, 16, 16); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            ItemStack it = pl.getItemInHand(h);
            Direction in = s.getValue(FACING); double side = (pl.getX() - (p.getX() + 0.5)) * in.getStepX() + (pl.getZ() - (p.getZ() + 0.5)) * in.getStepZ();
            if (side > 0.2) { if (!l.isClientSide) { l.setBlock(p, s.setValue(OPEN, true), 3); ((ServerLevel) l).scheduleTick(p, this, 60); gatePasses++; l.playSound(null, p, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1f, 1.2f); }
                return InteractionResult.sidedSuccess(l.isClientSide); }   // from the paid side (FACING points into it): out is free
            if (!it.is(OwnReg.TICKET.get())) { if (!l.isClientSide) { gateRefused++; pl.displayClientMessage(Component.literal("Ticket please - from the ticket machine"), true); l.playSound(null, p, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1f, 0.6f); } return InteractionResult.sidedSuccess(l.isClientSide); }
            if (!l.isClientSide) { if (!pl.getAbilities().instabuild) it.shrink(1); l.setBlock(p, s.setValue(OPEN, true), 3); ((ServerLevel) l).scheduleTick(p, this, 60); gatePasses++;
                l.playSound(null, p, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1f, 1.5f); if (pl instanceof ServerPlayer sp) Awards.award(sp, "commuter"); }
            return InteractionResult.sidedSuccess(l.isClientSide); }
        @Override public void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) { if (s.getValue(OPEN)) l.setBlock(p, s.setValue(OPEN, false), 3); }
    }
    /** Escalator step block: carries whoever stands on it up the way it faces (0.5 m/s, ASME A17.1 allows up to 0.75). */
    public static final class Escalator extends StairBlock {
        public static final BooleanProperty DOWN = BooleanProperty.create("down");
        public Escalator(BlockBehaviour.Properties p) { super(() -> Blocks.SMOOTH_STONE.defaultBlockState(), p); registerDefaultState(defaultBlockState().setValue(DOWN, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(DOWN); }
        /** Up: carried the way the steps climb (FACING); down (DOWN): carried back down them. */
        @Override public void stepOn(Level l, BlockPos p, BlockState s, Entity e) {
            // a steady carry, not an add each tick: half the rider's own motion plus 0.033 a tick (with ground friction 0.546: steady 0.025 blocks a tick = 0.5 m/s);
            // step height takes them up the steps, so no lift (it made riders bounce)
            Direction f = s.getValue(FACING); Vec3 v = e.getDeltaMovement(); int sg = s.getValue(DOWN) ? -1 : 1;
            e.setDeltaMovement(sg * f.getStepX() * 0.033 + v.x * 0.5, v.y, sg * f.getStepZ() * 0.033 + v.z * 0.5); escalated++; super.stepOn(l, p, s, e); }
    }
    /** Radio: use it to tune to the next station; while held it plays that station (Radio). */
    public static final class RadioItem extends Item {
        public RadioItem(Properties p) { super(p); }
        /** The station line in its NBT changes every 10 s: that must not replay the raise-to-hand animation. */
        @Override public boolean shouldCauseReequipAnimation(ItemStack a, ItemStack b, boolean slotChanged) { return slotChanged || !a.is(b.getItem()); }
        @Override public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand h) {
            if (l instanceof ServerLevel sl && p instanceof ServerPlayer sp) Radio.tune(sl, sp, p.getItemInHand(h));
            return InteractionResultHolder.sidedSuccess(p.getItemInHand(h), l.isClientSide); }
    }
    /** Atlas: use it to have it filled in for where you are (a written book of the places and landmarks round you). */
    public static final class AtlasItem extends Item {
        public AtlasItem(Properties p) { super(p); }
        @Override public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand h) {
            if (l instanceof ServerLevel sl && p instanceof ServerPlayer sp) Atlas.write(sl, sp, h);
            return InteractionResultHolder.sidedSuccess(p.getItemInHand(h), l.isClientSide); }
    }

    static BlockBehaviour.Properties wood(MapColor c) { return BlockBehaviour.Properties.of().mapColor(c).strength(1.5f, 3f).sound(net.minecraft.world.level.block.SoundType.WOOD).noOcclusion(); }
    public static final RegistryObject<Block> JOB_BOARD = OwnReg.reg("job_board", () -> new JobBoard(wood(MapColor.WOOD)));
    public static final RegistryObject<Block> FOR_SALE_SIGN = OwnReg.reg("for_sale_sign", () -> new SaleSign(wood(MapColor.COLOR_RED)));
    public static final RegistryObject<Block> BALLOT_BOX = OwnReg.reg("ballot_box", () -> new BallotBox(wood(MapColor.COLOR_BLUE)));
    public static final RegistryObject<Block> SPRINKLER = OwnReg.reg("sprinkler", () -> new Sprinkler(OwnReg.metal(MapColor.COLOR_RED).strength(1f, 3f)));
    public static final RegistryObject<Block> ELEVATOR_BUTTON = OwnReg.reg("elevator_button", () -> new ElevatorButton(BlockBehaviour.Properties.copy(Blocks.STONE_BUTTON)));
    public static final RegistryObject<Block> FARE_GATE = OwnReg.reg("fare_gate", () -> new FareGate(OwnReg.metal(MapColor.METAL)));
    public static final RegistryObject<Block> ESCALATOR = OwnReg.reg("escalator", () -> new Escalator(OwnReg.metal(MapColor.METAL).strength(3f, 6f)));
    public static final RegistryObject<Item> RADIO = OwnReg.item("radio", () -> new RadioItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TOW_STRAP = OwnReg.item("tow_strap", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> ATLAS = OwnReg.item("atlas", () -> new AtlasItem(new Item.Properties().stacksTo(16)));
    public static String probe() { return "town game blocks: board uses " + boardUses + " sign uses " + signUses + " ballots " + ballotUses + " sprinkler trips " + sprinklerTrips + " lift rides " + rides
        + " fare gate passes " + gatePasses + " refused " + gateRefused + " escalator pushes " + escalated; }
}
