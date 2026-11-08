package com.hopeless.infinity.wild;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.util.RandomSource;

/**
 * 0.28.0: the sky eagle: a big raptor of the sky isles. It flies and perches like a parrot (its body and wings), but larger, stronger and
 * silent (no imitating); it spawns on isle turf in the cave sun's light. Drops feathers (loot table entities/sky_eagle).
 */
public class SkyEagle extends Parrot {
    public SkyEagle(EntityType<? extends Parrot> t, Level l) { super(t, l); }
    public static AttributeSupplier.Builder attributes() {
        return Parrot.createAttributes().add(Attributes.MAX_HEALTH, 16.0).add(Attributes.ATTACK_DAMAGE, 4.0).add(Attributes.FLYING_SPEED, 0.6).add(Attributes.MOVEMENT_SPEED, 0.25);
    }
    public static boolean canSpawn(EntityType<SkyEagle> t, ServerLevelAccessor l, MobSpawnType why, BlockPos p, RandomSource r) {
        return l.getBlockState(p.below()).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) && l.getRawBrightness(p, 0) >= 8;
    }
    @Override public net.minecraft.sounds.SoundEvent getAmbientSound() { return random.nextInt(6) == 0 ? net.minecraft.sounds.SoundEvents.PHANTOM_AMBIENT : null; }
    @Override public net.minecraft.resources.ResourceLocation getDefaultLootTable() { return new net.minecraft.resources.ResourceLocation("hopelessinfinity", "entities/sky_eagle"); }
    @Override public boolean isBaby() { return false; }
    /** 0.29.0: a raptor: it hunts the bats of the caves (dives on the nearest one in sight and strikes). Probe: bats killed. */
    public static volatile long batKills;
    @Override protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(this, 1.6, true));
        targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.ambient.Bat.class, 10, true, false, null));
    }
    @Override public boolean doHurtTarget(net.minecraft.world.entity.Entity t) { boolean hit = super.doHurtTarget(t); if (hit && t instanceof net.minecraft.world.entity.LivingEntity le && !le.isAlive()) batKills++; return hit; }
    /** Not a pet: seeds do not tame it (it would ride a shoulder as a parrot). */
    @Override public net.minecraft.world.InteractionResult mobInteract(net.minecraft.world.entity.player.Player p, net.minecraft.world.InteractionHand h) {
        if (!isTame() && p.getItemInHand(h).is(net.minecraft.tags.ItemTags.create(new net.minecraft.resources.ResourceLocation("forge", "seeds")))) return net.minecraft.world.InteractionResult.PASS;
        var it = p.getItemInHand(h).getItem(); if (it == net.minecraft.world.item.Items.WHEAT_SEEDS || it == net.minecraft.world.item.Items.MELON_SEEDS || it == net.minecraft.world.item.Items.PUMPKIN_SEEDS || it == net.minecraft.world.item.Items.BEETROOT_SEEDS || it == net.minecraft.world.item.Items.TORCHFLOWER_SEEDS || it == net.minecraft.world.item.Items.PITCHER_POD) return net.minecraft.world.InteractionResult.PASS;
        return super.mobInteract(p, h);
    }
}
