package com.hopeless.infinity.world;

import com.hopeless.infinity.planet.PlanetWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraftforge.common.ForgeMod;

import java.util.UUID;

/**
 * The planet's own gravity. Minecraft's gravity stands for Earth's 9.81 m/s2; on the planet it is scaled by gFactor = g / 9.80665 (the
 * planet's mass and radius, Planet.gravity). Living things: Forge's entity_gravity attribute (synced to the client, so movement
 * prediction agrees). Items, arrows, falling blocks, TNT, boats, carts, orbs: the extra pull is added to their motion each tick, sized by
 * each one's own vanilla gravity. Falls: the hit scales with g too (impact energy m g h).
 */
public final class Gravity {
    private Gravity() {}
    static final UUID MOD = UUID.fromString("3b1f6c2e-9d47-4a8e-b0c5-71e2f4a9d6b3");
    public static void apply(LivingEntity e, double g) {
        AttributeInstance a = e.getAttribute(ForgeMod.ENTITY_GRAVITY.get()); if (a == null) return;
        AttributeModifier old = a.getModifier(MOD); double want = g - 1;
        if (old != null && Math.abs(old.getAmount() - want) < 1e-9) return;
        if (old != null) a.removeModifier(MOD);
        a.addPermanentModifier(new AttributeModifier(MOD, "Hopeless Infinity planet gravity", want, AttributeModifier.Operation.MULTIPLY_TOTAL)); Probe.gravityLiving++; }
    public static void remove(LivingEntity e) { AttributeInstance a = e.getAttribute(ForgeMod.ENTITY_GRAVITY.get()); if (a != null && a.getModifier(MOD) != null) a.removeModifier(MOD); }
    /** Vanilla's own per-tick gravity for the non-living kinds (blocks per tick per tick). */
    static double base(Entity e) {
        if (e instanceof ItemEntity || e instanceof FallingBlockEntity || e instanceof PrimedTnt || e instanceof AbstractMinecart || e instanceof Boat) return 0.04;
        if (e instanceof AbstractArrow) return 0.05; if (e instanceof ThrowableProjectile || e instanceof ExperienceOrb) return 0.03; return 0; }
    public static void tick(ServerLevel l, PlanetWorld w) {
        double k = w.gFactor - 1; if (Math.abs(k) < 1e-6) return;
        for (Entity e : l.getAllEntities()) { if (e instanceof LivingEntity || e.isNoGravity() || e.onGround()) continue; double b = base(e); if (b == 0) continue;
            e.setDeltaMovement(e.getDeltaMovement().add(0, -b * k, 0)); Probe.gravityOther++; } }
}
