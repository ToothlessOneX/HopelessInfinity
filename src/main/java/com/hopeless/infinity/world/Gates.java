package com.hopeless.infinity.world;

import com.hopeless.infinity.HopelessInfinity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 0.14.0 THE GATES (owner, 2026-11-02: "we will also need that gate system as well for fast travel between worlds"). STATED GAME RULE:
 * there is no real physics for this; it is the one invented technology, kept simple and fair. A gate is a 5 x 5 upright ring of gate frame
 * blocks (16), facing north-south or east-west, with the gate controller in place of any one of them or touching one from outside (0.14.1:
 * the bottom row can be buried so you walk straight in). Name a gate by using a
 * renamed name tag on its controller; use the controller with an empty hand to choose where it goes (the next gate in the network, in any
 * world: the planet's six faces, the Nether, the End; other planets when they are worlds); sneak-use lists the gates. A whole ring with a
 * destination opens: its middle fills with the gate field, and a player who steps in comes out of the other gate's field, facing out of
 * it, with a 3 s rest before the next jump (so the field you arrive in does not send you back). The network is saved with the world.
 * Probes: gates in the network, open gates, jumps, refusals.
 */
public final class Gates {
    private Gates() {}
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HopelessInfinity.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HopelessInfinity.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HopelessInfinity.MODID);
    public static final RegistryObject<Block> FRAME = BLOCKS.register("gate_frame", () -> new Block(BlockBehaviour.Properties.of().strength(25f, 1200f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().lightLevel(s -> 4)));
    public static final RegistryObject<Block> CONTROLLER = BLOCKS.register("gate_controller", () -> new Controller(BlockBehaviour.Properties.of().strength(25f, 1200f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().lightLevel(s -> 7)));
    public static final RegistryObject<Block> FIELD = BLOCKS.register("gate_field", () -> new Field(BlockBehaviour.Properties.of().noCollission().noOcclusion().strength(-1f, 3600000f).lightLevel(s -> 11).noLootTable().replaceable().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
    public static final RegistryObject<Item> FRAME_ITEM = ITEMS.register("gate_frame", () -> new BlockItem(FRAME.get(), new Item.Properties()));
    public static final RegistryObject<Item> CONTROLLER_ITEM = ITEMS.register("gate_controller", () -> new BlockItem(CONTROLLER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<ControllerBE>> CONTROLLER_BE = BES.register("gate_controller", () -> BlockEntityType.Builder.of(ControllerBE::new, CONTROLLER.get()).build(null));
    public static final TicketType<ChunkPos> TICKET = TicketType.create("hopelessinfinity_gate", Comparator.comparingLong(ChunkPos::toLong), 100);
    public static volatile long jumps, refused, opens, syncs; public static volatile int openNow; static final Map<UUID, Long> REST = new ConcurrentHashMap<>();

    /** One gate in the network. axis: 0 the ring lies along x (you walk through it north-south), 1 along z. */
    public record Gate(String id, String name, ResourceKey<Level> dim, BlockPos pos, BlockPos origin, int axis, String dest) {
        CompoundTag save() { CompoundTag t = new CompoundTag(); t.putString("id", id); t.putString("name", name); t.putString("dim", dim.location().toString()); t.putLong("pos", pos.asLong()); t.putLong("origin", origin.asLong()); t.putInt("axis", axis); t.putString("dest", dest == null ? "" : dest); return t; }
        static Gate load(CompoundTag t) { return new Gate(t.getString("id"), t.getString("name"), ResourceKey.create(Registries.DIMENSION, new ResourceLocation(t.getString("dim"))), BlockPos.of(t.getLong("pos")), BlockPos.of(t.contains("origin") ? t.getLong("origin") : t.getLong("pos")), t.getInt("axis"), t.getString("dest").isEmpty() ? null : t.getString("dest")); }
        Gate with(String n, String d) { return new Gate(id, n, dim, pos, origin, axis, d); }
        String where() { return String.format("%s at %d %d %d", dim.location().getPath(), pos.getX(), pos.getY(), pos.getZ()); }
    }
    public static final class Data extends SavedData {
        final Map<String, Gate> gates = new java.util.LinkedHashMap<>(); Data() {}
        Data(CompoundTag t) { ListTag l = t.getList("gates", Tag.TAG_COMPOUND); for (int i = 0; i < l.size(); i++) { Gate g = Gate.load(l.getCompound(i)); gates.put(g.id(), g); } }
        @Override public CompoundTag save(CompoundTag t) { ListTag l = new ListTag(); for (Gate g : gates.values()) l.add(g.save()); t.put("gates", l); return t; }
    }
    public static Data data(MinecraftServer s) { return s.overworld().getDataStorage().computeIfAbsent(Data::new, Data::new, "hopelessinfinity_gates"); }
    static String id(Level l, BlockPos p) { return l.dimension().location() + "@" + p.asLong(); }

    /** The ring round a controller at p: its axis (0 along x, 1 along z), or -1 when it is not whole. */
    /** The ring round the controller at p: {bottom-middle cell, axis}, or null (world/GateShape). */
    public record Ring(BlockPos origin, int axis) {}
    public static Ring ring(BlockGetter l, BlockPos p) { GateShape.Found f = GateShape.ring((dx, dy, dz) -> l.getBlockState(p.offset(dx, dy, dz)).is(FRAME.get())); if (f != null) return new Ring(p.offset(f.ox(), f.oy(), f.oz()), f.axis()); return greatRing(l, p); }
    /** 0.27.2 THE GREAT GATE (planet/GreatGate, a megacity's gate plaza): axis 3 = the great ring lying along z (you walk through along x);
     *  the controller at the frame's CTRL cell; the ring whole when GREAT_WHOLE of its band cells (one depth) are solid. Axis 2 (turned) is
     *  not made by the world. */
    public static final double GREAT_WHOLE = 0.95; public static final int GREAT = 3;
    static Ring greatRing(BlockGetter l, BlockPos p) { BlockPos o = p.offset(-com.hopeless.infinity.planet.GreatGate.CTRL_X, -com.hopeless.infinity.planet.GreatGate.CTRL_Y, -com.hopeless.infinity.planet.GreatGate.CTRL_Z);
        java.util.List<int[]> band = com.hopeless.infinity.planet.GreatGate.bandCells(); int solid = 0; for (int[] c : band) { BlockState s = l.getBlockState(o.offset(-1, c[0], c[1])); if (!s.isAir() && !s.is(FIELD.get())) solid++; }
        return solid >= GREAT_WHOLE * band.size() ? new Ring(o, GREAT) : null; }
    static void clearField(Level l, Gate g) { for (BlockPos q : inside(g.origin(), g.axis())) if (l.getBlockState(q).is(FIELD.get())) l.setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3); }
    static List<BlockPos> inside(BlockPos p, int axis) { List<BlockPos> r = new ArrayList<>(); if (axis == GREAT) { for (int[] c : com.hopeless.infinity.planet.GreatGate.fieldCells()) r.add(p.offset(c[0], c[1], c[2])); return r; }   // 0.27.2 the great gate's round field
        for (int a = -1; a <= 1; a++) for (int y = 1; y <= 3; y++) r.add(axis == 0 ? p.offset(a, y, 0) : p.offset(0, y, a)); return r; }

    public static final class Controller extends Block implements EntityBlock {
        Controller(Properties p) { super(p); }
        @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new ControllerBE(p, s); }
        @SuppressWarnings("unchecked") @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) { return l.isClientSide || t != CONTROLLER_BE.get() ? null : (lv, pos, st, be) -> ((ControllerBE) be).tick((ServerLevel) lv, pos); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
            if (l.isClientSide) return InteractionResult.SUCCESS; ServerLevel sl = (ServerLevel) l; Data d = data(sl.getServer()); String me = id(l, p); Ring rg = ring(l, p);
            if (rg == null) { pl.displayClientMessage(Component.literal("[Gate] the ring is not whole: a 5 x 5 upright ring of gate frame blocks, with this controller in the ring or touching it (or a megacity's great ring, near whole)").withStyle(ChatFormatting.RED), false); return InteractionResult.CONSUME; }
            Gate g = d.gates.get(me); if (g == null || g.axis() != rg.axis() || !g.origin().equals(rg.origin())) { if (g != null) clearField(l, g); g = new Gate(me, g == null ? String.format("Gate %d %d", p.getX(), p.getZ()) : g.name(), l.dimension(), p, rg.origin(), rg.axis(), g == null ? null : g.dest()); d.gates.put(me, g); d.setDirty(); }
            ItemStack held = pl.getItemInHand(h);
            if (held.is(Items.NAME_TAG) && held.hasCustomHoverName()) { g = g.with(held.getHoverName().getString(), g.dest()); d.gates.put(me, g); d.setDirty(); pl.displayClientMessage(Component.literal("[Gate] named " + g.name()).withStyle(ChatFormatting.AQUA), false); return InteractionResult.CONSUME; }
            List<Gate> others = new ArrayList<>(); for (Gate o : d.gates.values()) if (!o.id().equals(me)) others.add(o);
            if (d.gates.values().stream().noneMatch(o -> Faces.body(o.dim()) == 1) && com.hopeless.infinity.planet.PlanetWorld.current() != null && com.hopeless.infinity.planet.PlanetWorld.current().moon() != null && Faces.body(l.dimension()) != 1) others.add(moonSite());   // 0.15.0 the moon, before anyone has built a gate there
            if (pl.isSecondaryUseActive()) { StringBuilder b = new StringBuilder("[Gate] " + g.name() + " (" + g.where() + "); the network:"); for (Gate o : others) b.append("\n  ").append(o.id().equals(g.dest()) ? "> " : "  ").append(o.name()).append(" - ").append(o.where());
                if (others.isEmpty()) b.append(" no other gates yet"); pl.displayClientMessage(Component.literal(b.toString()), false); return InteractionResult.CONSUME; }
            if (others.isEmpty()) { pl.displayClientMessage(Component.literal("[Gate] " + g.name() + " is in the network; build another gate to go somewhere").withStyle(ChatFormatting.YELLOW), false); return InteractionResult.CONSUME; }
            int i = 0; for (int k = 0; k < others.size(); k++) if (others.get(k).id().equals(g.dest())) i = (k + 1) % others.size(); Gate to = others.get(i);
            g = g.with(g.name(), to.id()); d.gates.put(me, g); d.setDirty();
            String dist = to.dim().equals(l.dimension()) ? String.format("%.1f km", Math.sqrt(to.pos().distSqr(p)) / 1000) : "another world";
            pl.displayClientMessage(Component.literal("[Gate] " + g.name() + " -> " + to.name() + " (" + to.where() + ", " + dist + ")").withStyle(ChatFormatting.AQUA), false);
            ((ControllerBE) l.getBlockEntity(p)).check(sl, p); return InteractionResult.CONSUME; }
        @Override public void onRemove(BlockState s, Level l, BlockPos p, BlockState n, boolean moving) {
            if (!l.isClientSide && !s.is(n.getBlock())) { Data d = data(((ServerLevel) l).getServer()); Gate g = d.gates.remove(id(l, p)); if (g != null) { d.setDirty(); clearField(l, g); } }
            super.onRemove(s, l, p, n, moving); }
    }
    /** 0.27.2 the controllers loaded on this client (client/PortalRender draws their portals); filled by the block entities themselves. */
    public static final java.util.Set<ControllerBE> CLIENT = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** 0.27.2 the field blocks draw their old pane only when the portal shader could not be made (set by client/PortalRender). */
    public static volatile boolean fieldModel = false;
    public static final class ControllerBE extends BlockEntity {
        boolean open; public long openedAt = Long.MIN_VALUE, closedAt = Long.MIN_VALUE; public BlockPos origin; public int axis = -1;   // 0.27.2 sent to the clients
        public boolean open() { return open; }
        public ControllerBE(BlockPos p, BlockState s) { super(CONTROLLER_BE.get(), p, s); }
        @Override public void onLoad() { super.onLoad(); if (level != null && level.isClientSide) CLIENT.add(this); }
        @Override public void setRemoved() { super.setRemoved(); CLIENT.remove(this); }
        @Override public void onChunkUnloaded() { super.onChunkUnloaded(); CLIENT.remove(this); }
        @Override protected void saveAdditional(CompoundTag t) { super.saveAdditional(t); t.putBoolean("open", open); t.putLong("openedAt", openedAt); t.putLong("closedAt", closedAt); if (origin != null) t.putLong("origin", origin.asLong()); t.putInt("axis", axis); }
        @Override public void load(CompoundTag t) { super.load(t); open = t.getBoolean("open"); openedAt = t.contains("openedAt") ? t.getLong("openedAt") : Long.MIN_VALUE; closedAt = t.contains("closedAt") ? t.getLong("closedAt") : Long.MIN_VALUE; origin = t.contains("origin") ? BlockPos.of(t.getLong("origin")) : null; axis = t.contains("axis") ? t.getInt("axis") : -1; }
        @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
        @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() { return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this); }
        void tick(ServerLevel l, BlockPos p) { if (l.getGameTime() % 40 == 0) check(l, p); }
        /** Opens (fills the field) when the ring is whole and the gate has a destination that still exists; closes otherwise. */
        void check(ServerLevel l, BlockPos p) {
            Data d = data(l.getServer()); Gate g = d.gates.get(id(l, p)); if (g == null) return; Ring rg = ring(l, p); boolean same = rg != null && rg.axis() == g.axis() && rg.origin().equals(g.origin()); boolean want = same && validDest(d, g.dest());
            if (rg != null && !same) { clearField(l, g); g = new Gate(g.id(), g.name(), g.dim(), g.pos(), rg.origin(), rg.axis(), g.dest()); d.gates.put(g.id(), g); d.setDirty(); want = validDest(d, g.dest()); }   // the ring was rebuilt elsewhere round the controller
            int ax = g.axis();
            for (BlockPos q : inside(g.origin(), ax)) { BlockState s = l.getBlockState(q);
                if (want && s.isAir()) l.setBlock(q, FIELD.get().defaultBlockState().setValue(Field.AXIS, ax == 0 ? Direction.Axis.X : Direction.Axis.Z), 3); else if (!want && s.is(FIELD.get())) l.setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3); }
            if (want != open || want && (origin == null || !origin.equals(g.origin()) || axis != ax)) { boolean was = open; open = want; origin = g.origin(); axis = ax;
                if (want && !was) { opens++; openedAt = l.getGameTime(); l.playSound(null, p, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 1.2f); } else if (!want && was) { closedAt = l.getGameTime(); l.playSound(null, p, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1f, 1.2f); }
                setChanged(); BlockState st = getBlockState(); l.sendBlockUpdated(p, st, st, 3); syncs++; }   // 0.27.2 the clients' portals
            int n = 0; for (Gate o : d.gates.values()) { ServerLevel ol = l.getServer().getLevel(o.dim()); if (ol != null && ol.isLoaded(o.pos()) && ol.getBlockEntity(o.pos()) instanceof ControllerBE be && be.open) n++; } openNow = n; }
    }
    public static final class Field extends Block {
        public static final net.minecraft.world.level.block.state.properties.EnumProperty<Direction.Axis> AXIS = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS;
        Field(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X)); }
        @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> b) { b.add(AXIS); }
        @Override public boolean skipRendering(BlockState s, BlockState adj, Direction d) { return adj.is(this) || super.skipRendering(s, adj, d); }
        @Override public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState s) { return fieldModel ? net.minecraft.world.level.block.RenderShape.MODEL : net.minecraft.world.level.block.RenderShape.INVISIBLE; }   // 0.27.2 the portal shader draws the surface
        @Override public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return s.getValue(AXIS) == Direction.Axis.X ? Shapes.box(0, 0, 0.375, 1, 1, 0.625) : Shapes.box(0.375, 0, 0, 0.625, 1, 1); }
        @Override public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Shapes.empty(); }
        @Override public void animateTick(BlockState s, Level l, BlockPos p, net.minecraft.util.RandomSource r) { if (r.nextInt(3) == 0) l.addParticle(ParticleTypes.REVERSE_PORTAL, p.getX() + r.nextDouble(), p.getY() + r.nextDouble(), p.getZ() + r.nextDouble(), 0, 0.02, 0); }
        @Override public void entityInside(BlockState s, Level l, BlockPos p, Entity e) { if (!l.isClientSide && e instanceof ServerPlayer sp && !sp.isPassenger()) jump(sp, (ServerLevel) l, p); }
    }
    /** A player in a field: find its controller, then its destination; come out of the far gate's field, facing out. */
    static void jump(ServerPlayer pl, ServerLevel l, BlockPos fieldPos) {
        long now = l.getServer().overworld().getGameTime(); Long rest = REST.get(pl.getUUID()); if (rest != null && now < rest) return;
        try { Data d = data(l.getServer()); Gate from = null;
            for (Gate g : d.gates.values()) if (g.dim().equals(l.dimension()) && inside(g.origin(), g.axis()).contains(fieldPos)) { from = g; break; }
            if (from == null || from.dest() == null) { refused++; return; }
            if (MOON_SITE.equals(from.dest())) { Gate built = buildMoonGate(l.getServer(), d, from); if (built == null) { refused++; pl.displayClientMessage(Component.literal("[Gate] the moon's landing site could not be made").withStyle(ChatFormatting.RED), true); return; } from = d.gates.get(from.id()); }
            Gate to = d.gates.get(from.dest()); if (to == null) { refused++; return; }
            ServerLevel tl = l.getServer().getLevel(to.dim()); if (tl == null) { refused++; pl.displayClientMessage(Component.literal("[Gate] that world is not loaded").withStyle(ChatFormatting.RED), true); return; }
            ChunkPos cp = new ChunkPos(to.origin()); tl.getChunkSource().addRegionTicket(TICKET, cp, 2, cp); tl.getChunk(cp.x, cp.z);
            Ring tr = ring(tl, to.pos()); if (tr == null || tr.axis() != to.axis() || !tr.origin().equals(to.origin())) { refused++; pl.displayClientMessage(Component.literal("[Gate] " + to.name() + " is broken (its ring is not whole)").withStyle(ChatFormatting.RED), true); REST.put(pl.getUUID(), now + 60); return; }
            // come out on the side of the far ring that the player was walking toward in this one (the same way through), one block clear of the field
            int axisF = from.axis(); double through = axisF == 0 ? pl.getDeltaMovement().z : pl.getDeltaMovement().x; if (Math.abs(through) < 1e-3) through = axisF == 0 ? Math.cos(Math.toRadians(pl.getYRot())) : -Math.sin(Math.toRadians(pl.getYRot()));
            int sideOut = through >= 0 ? 1 : -1; double ox = to.origin().getX() + 0.5, oz = to.origin().getZ() + 0.5, oy = to.origin().getY() + 1.0;
            if (to.axis() == GREAT) { ox = to.origin().getX() + sideOut * 2.5; oz = to.origin().getZ() + 0.5; } else if (to.axis() == 0) oz += sideOut * 1.5; else ox += sideOut * 1.5;   /* 0.27.2 the great ring is 2 deep: out on the dais, clear of it */ float yaw = to.axis() == 0 ? (sideOut > 0 ? 0f : 180f) : (sideOut > 0 ? -90f : 90f);
            REST.put(pl.getUUID(), now + 60);
            pl.teleportTo(tl, ox, oy, oz, Set.<RelativeMovement>of(), yaw, pl.getXRot()); jumps++;
            tl.playSound(null, ox, oy, oz, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.9f);
            if (Faces.body(to.dim()) == 1 && !Suits.sealedWithOxygen(pl) && !pl.isCreative()) pl.displayClientMessage(Component.literal("[Gate] the moon has no air: about 15 s without a sealed suit with oxygen").withStyle(ChatFormatting.RED), false);
            HopelessInfinity.LOG.info("[Hopeless Infinity] gate: {} went from {} ({}) to {} ({})", pl.getName().getString(), from.name(), from.where(), to.name(), to.where());
        } catch (Throwable t) { refused++; HopelessInfinity.LOG.error("[Hopeless Infinity] gate jump failed", t); } }

    // ---------------- 0.15.0 the moon's landing site ----------------
    /** The moon before anyone has a gate there: a destination that builds its own gate at the landing site (the middle of the near side,
     *  moon face 0's centre: the home planet overhead) on the first jump, bottom row sunk into the ground, controller in the right side. */
    public static final String MOON_SITE = "builtin:moon";
    static Gate moonSite() { return new Gate(MOON_SITE, "Moon: landing site (near side)", Faces.key(1, 0), BlockPos.ZERO, BlockPos.ZERO, 0, null); }
    static boolean validDest(Data d, String dest) { return dest != null && (d.gates.containsKey(dest) || MOON_SITE.equals(dest)); }
    public static volatile long moonGatesBuilt;
    static Gate buildMoonGate(MinecraftServer s, Data d, Gate from) {
        ServerLevel ml = s.getLevel(Faces.key(1, 0)); var mw = Faces.world(1); if (ml == null || mw == null) return null;
        ChunkPos cp = new ChunkPos(0, 0); ml.getChunkSource().addRegionTicket(TICKET, cp, 3, cp); ml.getChunk(0, 0);
        int y0 = com.hopeless.infinity.planet.Column.of(mw, 0, 0, 0).surfaceY; BlockPos o = new BlockPos(0, y0, 0);   // the bottom row at ground level: you walk straight in
        // clear the ring's middle and a 3-block apron each side, then the ring; the controller in the right side
        for (int a = -3; a <= 3; a++) for (int y = 1; y <= 4; y++) for (int zz = -3; zz <= 3; zz++) { BlockPos q = o.offset(a, y, zz); if (!(Math.abs(a) <= 2 && zz == 0)) ml.setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2); }
        for (int a = -1; a <= 1; a++) for (int y = 1; y <= 3; y++) ml.setBlock(o.offset(a, y, 0), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        for (int[] c : GateShape.cells(0)) ml.setBlock(o.offset(c[0], c[1], c[2]), (c[0] == 2 && c[1] == 2 ? CONTROLLER : FRAME).get().defaultBlockState(), 3);
        for (int a = -3; a <= 3; a++) for (int zz = -3; zz <= 3; zz++) if (ml.getBlockState(o.offset(a, 0, zz)).isAir()) ml.setBlock(o.offset(a, 0, zz), net.minecraft.world.level.block.Blocks.ANDESITE.defaultBlockState(), 2);   // a firm apron
        BlockPos ctrl = o.offset(2, 2, 0); String id = ml.dimension().location() + "@" + ctrl.asLong();
        Gate g = new Gate(id, "Moon landing site", ml.dimension(), ctrl, o, 0, from.id()); d.gates.put(id, g); d.gates.put(from.id(), from.with(from.name(), id)); d.setDirty(); moonGatesBuilt++;
        if (ml.getBlockEntity(ctrl) instanceof ControllerBE be) be.check(ml, ctrl);
        HopelessInfinity.LOG.info("[Hopeless Infinity] gate: the moon's landing site built at moon face 0 {} (ground y {}), linked with {}", o.toShortString(), y0, from.name());
        return g; }
    public static String probe(MinecraftServer s) { return String.format("gates: %d in the network, %d open, jumps %d, refused %d, openings %d, client syncs %d, moon landing gates built %d", s == null ? 0 : data(s).gates.size(), openNow, jumps, refused, opens, syncs, moonGatesBuilt); }
}
