package com.hopeless.infinity.wild;

import com.hopeless.infinity.HopelessInfinity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 0.27.0: sunstone, found only at the top of the Hollows, in the crystal spires the cave sun has grown on the roof above it (the Sun
 * Crown) and in the sky isles' roosts. A reason to climb: a shard burns as long as 16 coal in a furnace (the sun's light, kept), four
 * make a sunstone block that gives full light (15) and never goes out.
 */
public final class SkyReg {
    private SkyReg() {}
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HopelessInfinity.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HopelessInfinity.MODID);

    public static final RegistryObject<Block> SUNSTONE_ORE = BLOCKS.register("sunstone_ore", () -> new DropExperienceBlock(BlockBehaviour.Properties.of()
        .mapColor(MapColor.COLOR_ORANGE).strength(3.5f, 6f).requiresCorrectToolForDrops().lightLevel(s -> 9).sound(SoundType.AMETHYST), UniformInt.of(3, 7)));
    public static final RegistryObject<Block> SUNSTONE_BLOCK = BLOCKS.register("sunstone_block", () -> new Block(BlockBehaviour.Properties.of()
        .mapColor(MapColor.GOLD).strength(2f, 6f).lightLevel(s -> 15).sound(SoundType.AMETHYST)));
    public static final RegistryObject<Item> SUNSTONE_SHARD = ITEMS.register("sunstone_shard", () -> new Item(new Item.Properties()) {
        @Override public int getBurnTime(ItemStack stack, RecipeType<?> type) { return 25600; }   // 16 coal (1600 ticks each)
    });
    public static final RegistryObject<Item> SUNSTONE_ORE_ITEM = ITEMS.register("sunstone_ore", () -> new BlockItem(SUNSTONE_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> SUNSTONE_BLOCK_ITEM = ITEMS.register("sunstone_block", () -> new BlockItem(SUNSTONE_BLOCK.get(), new Item.Properties()));

    // ---- 0.28.0: the new cave biomes' blocks (textures made for the mod)
    static BlockBehaviour.Properties rock(MapColor c) { return BlockBehaviour.Properties.of().mapColor(c).strength(1.6f, 4f).requiresCorrectToolForDrops(); }
    /** Rock salt of the evaporite caves (halite), and its crystals. */
    public static final RegistryObject<Block> HALITE = BLOCKS.register("halite", () -> new Block(rock(MapColor.COLOR_PINK).sound(SoundType.CALCITE)));
    public static final RegistryObject<Block> SALT_CRYSTAL = BLOCKS.register("salt_crystal", () -> new SaltCrystal(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW)
        .noCollission().noOcclusion().instabreak().lightLevel(s -> 3).sound(SoundType.AMETHYST_CLUSTER).offsetType(BlockBehaviour.OffsetType.XZ)));
    /** The yellow crust round the sulfur vents. */
    public static final RegistryObject<Block> SULFUR = BLOCKS.register("sulfur", () -> new Block(rock(MapColor.COLOR_YELLOW).strength(0.9f, 2f).sound(SoundType.TUFF)));
    /** Vents: a hot spring's steam, a sulfur vent's fumes (they only look and sound). */
    public static final RegistryObject<Block> STEAM_VENT = BLOCKS.register("steam_vent", () -> new Vent(rock(MapColor.TERRACOTTA_WHITE).sound(SoundType.CALCITE), false));
    public static final RegistryObject<Block> SULFUR_VENT = BLOCKS.register("sulfur_vent", () -> new Vent(rock(MapColor.COLOR_YELLOW).sound(SoundType.TUFF).lightLevel(s -> 4), true));
    /** Sunberries: a bush that ripens only in the cave sun's full light at the top of the Hollows (the sky isles and roof gardens). */
    public static final RegistryObject<Block> SUNBERRY_BUSH = BLOCKS.register("sunberry_bush", () -> new SunberryBush(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT)
        .randomTicks().noCollission().sound(SoundType.SWEET_BERRY_BUSH).lightLevel(s -> s.getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE) == 3 ? 6 : 0)));
    public static final RegistryObject<Item> SUNBERRIES = ITEMS.register("sunberries", () -> new net.minecraft.world.item.ItemNameBlockItem(SUNBERRY_BUSH.get(), new Item.Properties()
        .food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(4).saturationMod(0.6f)
            .effect(() -> new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 100, 0), 1f)
            .effect(() -> new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 600, 0), 0.5f).build())));
    public static final RegistryObject<Item> HALITE_ITEM = ITEMS.register("halite", () -> new BlockItem(HALITE.get(), new Item.Properties()));
    public static final RegistryObject<Item> SALT_CRYSTAL_ITEM = ITEMS.register("salt_crystal", () -> new BlockItem(SALT_CRYSTAL.get(), new Item.Properties()));
    public static final RegistryObject<Item> SULFUR_ITEM = ITEMS.register("sulfur", () -> new BlockItem(SULFUR.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEAM_VENT_ITEM = ITEMS.register("steam_vent", () -> new BlockItem(STEAM_VENT.get(), new Item.Properties()));
    public static final RegistryObject<Item> SULFUR_VENT_ITEM = ITEMS.register("sulfur_vent", () -> new BlockItem(SULFUR_VENT.get(), new Item.Properties()));

    // ---- 0.29.0: geysers in the hot springs; brine and salt
    public static final RegistryObject<Block> GEYSER = BLOCKS.register("geyser", () -> new Geyser(rock(MapColor.TERRACOTTA_WHITE).sound(SoundType.CALCITE).randomTicks()));
    public static final RegistryObject<Item> GEYSER_ITEM = ITEMS.register("geyser", () -> new BlockItem(GEYSER.get(), new Item.Properties()));
    public static final RegistryObject<Block> BRINE_CAULDRON = BLOCKS.register("brine_cauldron", () -> new Brine(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.CAULDRON).randomTicks()));
    public static final RegistryObject<Item> SALT = ITEMS.register("salt", () -> new Item(new Item.Properties()));

    // ---- 0.28.0: the sky eagle, a raptor that nests on the sky isles (the parrot's body, larger; texture made for the mod)
    public static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HopelessInfinity.MODID);
    public static final RegistryObject<net.minecraft.world.entity.EntityType<SkyEagle>> SKY_EAGLE = ENTITIES.register("sky_eagle", () ->
        net.minecraft.world.entity.EntityType.Builder.of(SkyEagle::new, net.minecraft.world.entity.MobCategory.CREATURE).sized(0.9f, 1.4f).clientTrackingRange(10).build("sky_eagle"));
    public static final RegistryObject<Item> SKY_EAGLE_EGG = ITEMS.register("sky_eagle_spawn_egg", () -> new net.minecraftforge.common.ForgeSpawnEggItem(SKY_EAGLE, 0x5A3A22, 0xF0EEE6, new Item.Properties()));

    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus);
        bus.addListener((net.minecraftforge.event.BuildCreativeModeTabContentsEvent e) -> {   // 0.29.0: the sky and cave items in the creative menu
            if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.NATURAL_BLOCKS) { for (var it : ITEMS.getEntries()) if (it != SKY_EAGLE_EGG) e.accept(it.get()); }
            else if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.SPAWN_EGGS) e.accept(SKY_EAGLE_EGG.get()); });
        // 0.26.0: Deep's updrafts (lift over vents in its cave hollows) are not ported: they need Deep's hollow sun model
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) -> Brine.use(e));
        bus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e) -> e.put(SKY_EAGLE.get(), SkyEagle.attributes().build()));
        bus.addListener((net.minecraftforge.event.entity.SpawnPlacementRegisterEvent e) -> e.register(SKY_EAGLE.get(), net.minecraft.world.entity.SpawnPlacements.Type.ON_GROUND,
            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SkyEagle::canSpawn, net.minecraftforge.event.entity.SpawnPlacementRegisterEvent.Operation.REPLACE));
    }
}
