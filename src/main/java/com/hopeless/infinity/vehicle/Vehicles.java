package com.hopeless.infinity.vehicle;

import com.hopeless.infinity.HopelessInfinity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/** 0.13.0: the spaceplane's entity type and the item that sets one down. */
public final class Vehicles {
    private Vehicles() {}
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HopelessInfinity.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HopelessInfinity.MODID);
    public static final RegistryObject<EntityType<Spaceplane>> X15_TYPE = ENTITIES.register("x15", () -> EntityType.Builder.<Spaceplane>of(Spaceplane::new, MobCategory.MISC).sized(3f, 2f).clientTrackingRange(16).updateInterval(1).build("x15"));
    public static final RegistryObject<Item> X15_ITEM = ITEMS.register("x15", () -> new Item(new Item.Properties().stacksTo(1)) {
        @Override public InteractionResult useOn(UseOnContext c) {
            Level l = c.getLevel(); if (l.isClientSide) return InteractionResult.SUCCESS;
            Spaceplane p = X15_TYPE.get().create(l); if (p == null) return InteractionResult.FAIL; var pos = c.getClickedPos().relative(c.getClickedFace());
            p.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, c.getPlayer() == null ? 0 : c.getPlayer().getYRot(), 0); l.addFreshEntity(p);
            if (c.getPlayer() == null || !c.getPlayer().isCreative()) c.getItemInHand().shrink(1); return InteractionResult.CONSUME; }
        @Override public void appendHoverText(ItemStack s, Level l, List<Component> t, TooltipFlag f) {
            t.add(Component.literal("North American X-15: 15.47 m, 6.82 m span, 7,058 kg empty").withStyle(ChatFormatting.GRAY));
            t.add(Component.literal(String.format("Advanced engine (stated rule): %.0f km/s, %.0f s at full power", X15.ADV_DV / 1000, X15.PROP_KG / X15.MDOT)).withStyle(ChatFormatting.GOLD));
            t.add(Component.literal("Right-click to board; look to steer; forward/back: throttle").withStyle(ChatFormatting.DARK_GRAY)); } });
}
