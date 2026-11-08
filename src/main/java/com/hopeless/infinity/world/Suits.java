package com.hopeless.infinity.world;

import com.hopeless.infinity.HopelessInfinity;
import com.hopeless.infinity.planet.PlanetWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 0.11.0 THE SPACESUITS in the game (data and physics: SuitSpec). Each of the four suits is four pieces (helmet; the upper torso with the
 * life-support backpack and the jetpack; the lower torso; the boots). Only a whole suit of one kind seals: then, in air that is not breathable,
 * the backpack's oxygen breathes for you (world/Breath), 1 s of tank a second; the tank is the real suit's hours. The OXYGEN STATION (a
 * concentrator and compressor) refills the suits of players within 4 blocks of it, where its own air is breathable (SuitSpec.REFILL_X), and
 * refuels the rocket belts. A new suit comes filled (STATED: charged at the factory).
 * The rocket belt: the client holds jump in the air (client/SuitClient pushes the player by SuitSpec.thrustPerTick); it tells the server it
 * burns (Net.BeltBurn); the server spends the propellant (1 tick a tick, 21 s), clears the fall, and keeps the anti-fly check from kicking.
 * Probes: suits sealed now, oxygen seconds used, refills, belt ticks burned, kicks prevented, errors.
 */
public final class Suits {
    private Suits() {}
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HopelessInfinity.MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HopelessInfinity.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HopelessInfinity.MODID);
    public static final Map<SuitSpec.Kind, Map<ArmorItem.Type, RegistryObject<Item>>> PIECES = new EnumMap<>(SuitSpec.Kind.class);
    static final String[] PIECE_ID = {"helmet", "upper", "lower", "boots"};
    static {
        for (SuitSpec.Kind k : SuitSpec.Kind.values()) { Map<ArmorItem.Type, RegistryObject<Item>> m = new EnumMap<>(ArmorItem.Type.class); Mat mat = new Mat(k);
            ArmorItem.Type[] ts = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};
            for (int i = 0; i < 4; i++) { ArmorItem.Type t = ts[i]; m.put(t, ITEMS.register(k.id + "_" + PIECE_ID[i], () -> new SuitItem(mat, t, k))); }
            PIECES.put(k, m); } }
    public static final RegistryObject<Block> STATION = BLOCKS.register("oxygen_station", () -> new Station(BlockBehaviour.Properties.of().strength(3.5f).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Item> STATION_ITEM = ITEMS.register("oxygen_station", () -> new BlockItem(STATION.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<StationBE>> STATION_BE = BES.register("oxygen_station", () -> BlockEntityType.Builder.of(StationBE::new, STATION.get()).build(null));

    public static volatile long o2Used, refills, beltTicks, mmuTicks, kicksPrevented, errors; public static volatile int sealedNow;
    static final Set<UUID> SEALED = ConcurrentHashMap.newKeySet();
    public static final Map<UUID, Boolean> BURNING = new ConcurrentHashMap<>();

    /** A suit's armor material: leather's protection (a suit is not armor; CHOSEN), iron's durability (CHOSEN), the knockback the suit's mass resists. */
    record Mat(SuitSpec.Kind k) implements ArmorMaterial {
        public int getDurabilityForType(ArmorItem.Type t) { return switch (t) { case HELMET -> 11; case CHESTPLATE -> 16; case LEGGINGS -> 15; case BOOTS -> 13; } * 15; }
        public int getDefenseForType(ArmorItem.Type t) { return switch (t) { case HELMET -> 1; case CHESTPLATE -> 3; case LEGGINGS -> 2; case BOOTS -> 1; }; }
        public int getEnchantmentValue() { return 9; }
        public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_IRON; }
        public Ingredient getRepairIngredient() { return Ingredient.of(Items.WHITE_WOOL); }
        public String getName() { return HopelessInfinity.MODID + ":" + k.id; }
        public float getToughness() { return 0; }
        public float getKnockbackResistance() { return (float) (SuitSpec.knockbackResist(k) / 4); }
    }

    public static final class SuitItem extends ArmorItem {
        public final SuitSpec.Kind kind;
        SuitItem(ArmorMaterial m, Type t, SuitSpec.Kind k) { super(m, t, new Item.Properties()); kind = k; }
        @Override public void appendHoverText(ItemStack s, Level l, List<Component> tip, TooltipFlag f) {
            tip.add(Component.literal(kind.longName).withStyle(ChatFormatting.GRAY));
            if (getType() == Type.CHESTPLATE) {
                int o2 = o2(s); tip.add(Component.literal(String.format("Oxygen %s of %.1f h", hms(o2), kind.hours)).withStyle(ChatFormatting.AQUA));
                tip.add(Component.literal(String.format("Rocket belt %.1f s of %.0f s", belt(s) / 20.0, SuitSpec.BELT_S)).withStyle(ChatFormatting.GOLD));
                tip.add(Component.literal(String.format("MMU nitrogen %.1f of %.1f m/s", n2(s), SuitSpec.MMU_DV)).withStyle(ChatFormatting.GOLD));
                tip.add(Component.literal(String.format("%.1f kPa (%.2f psi)%s, %.0f kg%s", kind.kPa, kind.psi(), kind == SuitSpec.Kind.XEMU ? ", up to 8.2 psid" : "", kind.massKg, kind.massChosen ? " (EMU's; not published)" : "")).withStyle(ChatFormatting.DARK_GRAY)); }
            else tip.add(Component.literal("Wear all four " + kind.shortName + " pieces to seal the suit").withStyle(ChatFormatting.DARK_GRAY)); }
    }
    static String hms(int s) { return String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60); }

    /** Oxygen left (s) in an upper torso; a new one is full. */
    public static int o2(ItemStack s) { if (!(s.getItem() instanceof SuitItem si)) return 0; CompoundTag t = s.getTag(); return t != null && t.contains("o2") ? t.getInt("o2") : si.kind.o2Seconds(); }
    public static int belt(ItemStack s) { CompoundTag t = s.getTag(); return t != null && t.contains("belt") ? t.getInt("belt") : SuitSpec.BELT_TICKS; }
    static void setO2(ItemStack s, int v) { s.getOrCreateTag().putInt("o2", v); }
    static void setBelt(ItemStack s, int v) { s.getOrCreateTag().putInt("belt", v); }
    /** 0.12.0: the MMU's nitrogen as delta-v left (m/s); a new suit is full. */
    public static double n2(ItemStack s) { CompoundTag t = s.getTag(); return t != null && t.contains("n2") ? t.getDouble("n2") : SuitSpec.MMU_DV; }
    static void setN2(ItemStack s, double v) { s.getOrCreateTag().putDouble("n2", Math.max(0, v)); }

    /** The kind of the whole suit the player wears, or null when the four pieces are not all one kind. */
    public static SuitSpec.Kind fullSet(Player p) { SuitSpec.Kind k = null;
        for (EquipmentSlot e : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!(p.getItemBySlot(e).getItem() instanceof SuitItem si)) return null; if (k == null) k = si.kind; else if (k != si.kind) return null; }
        return k; }
    public static boolean sealedWithOxygen(ServerPlayer p) { return fullSet(p) != null && o2(p.getItemBySlot(EquipmentSlot.CHEST)) > 0; }

    /** Breath: in air that is not breathable. True when the suit breathes for the player (and spends a second of oxygen each second). */
    public static boolean breathe(ServerPlayer p) {
        if (!sealedWithOxygen(p)) { if (SEALED.remove(p.getUUID())) sealedNow = SEALED.size(); return false; }
        if (SEALED.add(p.getUUID())) sealedNow = SEALED.size();
        if (p.tickCount % 20 == 0) { ItemStack c = p.getItemBySlot(EquipmentSlot.CHEST); setO2(c, o2(c) - 1); o2Used++;
            int left = o2(c); if (left == 600 || left == 60) p.displayClientMessage(Component.literal(String.format("[%s] oxygen: %d min left", ((SuitItem) c.getItem()).kind.shortName, left / 60)).withStyle(ChatFormatting.RED), true); }
        return true; }

    static java.lang.reflect.Field aboveGround; static boolean aboveGroundTried;
    /** Server, every player every tick (any dimension): the rocket belt's burn. */
    public static void serverTick(ServerPlayer p) {
        if (p.getVehicle() instanceof com.hopeless.infinity.vehicle.Spaceplane) noKick(p);   // 0.13.0 a flying X-15
        if (!BURNING.getOrDefault(p.getUUID(), false) || Space.in(p.level())) return;   // in space the belt is part of Space's thrust
        try { SuitSpec.Kind k = fullSet(p); ItemStack c = p.getItemBySlot(EquipmentSlot.CHEST); int b = k == null ? 0 : belt(c);
            if (k == null || b <= 0 || p.isSpectator()) { BURNING.put(p.getUUID(), false); return; }
            if (!p.isCreative()) setBelt(c, b - 1); beltTicks++; p.fallDistance = 0;
            noKick(p);
        } catch (Throwable t) { errors++; if (errors < 5) HopelessInfinity.LOG.error("[Hopeless Infinity] rocket belt tick failed", t); } }
    /** Keeps Minecraft's anti-fly check (80 ticks off the ground) from kicking a player the belt or space holds up. */
    public static void noKick(ServerPlayer p) { try {
        if (!aboveGroundTried) { aboveGroundTried = true; for (String n : new String[]{"aboveGroundTickCount", "f_9737_"}) try { aboveGround = net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField(n); aboveGround.setAccessible(true); break; } catch (NoSuchFieldException ignored) {} }
        if (aboveGround != null && p.connection != null && aboveGround.getInt(p.connection) > 40) { aboveGround.setInt(p.connection, 0); kicksPrevented++; }
        if (aboveVehicle == null && !aboveVehicleTried) { aboveVehicleTried = true; for (String n : new String[]{"aboveGroundVehicleTickCount", "f_9739_"}) try { aboveVehicle = net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField(n); aboveVehicle.setAccessible(true); break; } catch (NoSuchFieldException ignored) {} }
        if (aboveVehicle != null && p.connection != null && aboveVehicle.getInt(p.connection) > 40) { aboveVehicle.setInt(p.connection, 0); kicksPrevented++; } } catch (Throwable t) { errors++; } }
    static java.lang.reflect.Field aboveVehicle; static boolean aboveVehicleTried;
    public static void burn(ServerPlayer p, boolean on) { BURNING.put(p.getUUID(), on && fullSet(p) != null); }

    /** The oxygen station: a concentrator and compressor; refills suits within 4 blocks where its own air is breathable. */
    public static final class Station extends Block implements EntityBlock {
        Station(Properties p) { super(p); }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState s) { return new StationBE(pos, s); }
        @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
        @SuppressWarnings("unchecked") @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
            return l.isClientSide || t != STATION_BE.get() ? null : (lv, pos, st, be) -> ((StationBE) be).tick((ServerLevel) lv, pos); }
    }
    public static final class StationBE extends BlockEntity {
        public StationBE(BlockPos p, BlockState s) { super(STATION_BE.get(), p, s); }
        void tick(ServerLevel l, BlockPos pos) {
            if (l.getGameTime() % 20 != 0) return;
            try { if (!airHere(l, pos)) return;
                for (Player pl : l.getEntitiesOfClass(Player.class, new AABB(pos).inflate(4))) { ItemStack c = pl.getItemBySlot(EquipmentSlot.CHEST); if (!(c.getItem() instanceof SuitItem si)) continue;
                    int o = o2(c), full = si.kind.o2Seconds(), b = belt(c); boolean did = false;
                    if (o < full) { setO2(c, (int) Math.min(full, o + Math.round(SuitSpec.REFILL_X))); did = true; }
                    if (b < SuitSpec.BELT_TICKS) { setBelt(c, (int) Math.min(SuitSpec.BELT_TICKS, b + Math.round(SuitSpec.BELT_TICKS / SuitSpec.BELT_REFILL_S))); did = true; }
                    if (n2(c) < SuitSpec.MMU_DV) { setN2(c, Math.min(SuitSpec.MMU_DV, n2(c) + SuitSpec.MMU_DV / SuitSpec.BELT_REFILL_S)); did = true; }   // STATED: nitrogen from the same station, full in 60 s
                    if (did) { refills++; if (o2(c) >= full && belt(c) >= SuitSpec.BELT_TICKS) pl.displayClientMessage(Component.literal("[" + si.kind.shortName + "] oxygen and belt full").withStyle(ChatFormatting.AQUA), true); } }
            } catch (Throwable t) { errors++; } }
        /** Breathable air at the station: on a planet face, the planet's air at that height; elsewhere (vanilla dimensions) yes. */
        public static boolean airHere(ServerLevel l, BlockPos pos) { if (Space.in(l)) return false; int bid = Faces.body(l.dimension()); if (bid >= 1) { PlanetWorld bw = Faces.world(bid); return bw != null && bw.atm.breathableAt(Breath.metres(pos.getY() + 0.5)); } /* 0.18.0 each body's own air */ if (Faces.face(l.dimension()) < 0) return true; /* 0.15.0 no air on the moon or in space */ PlanetWorld w = PlanetWorld.current(); return w == null || Breath.home(w).breathableAt(Breath.metres(pos.getY() + 0.5)); }
    }

    public static String probe() { return String.format("suits: sealed now %d, oxygen used %d s, station refills %d, belt ticks burned %d, MMU ticks %d, kicks prevented %d, errors %d", sealedNow, o2Used, refills, beltTicks, mmuTicks, kicksPrevented, errors); }
}
