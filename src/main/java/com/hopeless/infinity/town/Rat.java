package com.hopeless.infinity.town;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.level.Level;

/** 0.30.0: the sewer rat: small, quick, bites; lives in the sewers under downtown (placed there). It does not hide in stone (the
 *  silverfish's habit is taken out). Drops: entities/rat. Probe: rats killed. */
public class Rat extends Silverfish {
    public static volatile long killed;
    public Rat(EntityType<? extends Silverfish> t, Level l) { super(t, l); }
    public static AttributeSupplier.Builder attributes() { return Silverfish.createAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.ATTACK_DAMAGE, 1.5); }
    @Override protected void registerGoals() {
        super.registerGoals();
        goalSelector.getAvailableGoals().removeIf(g -> g.getGoal().getClass().getSimpleName().contains("MergeWithStone"));
    }
    @Override public void die(net.minecraft.world.damagesource.DamageSource s) { super.die(s); if (!level().isClientSide) killed++; }
    @Override public net.minecraft.resources.ResourceLocation getDefaultLootTable() { return new net.minecraft.resources.ResourceLocation("hopelessinfinity", "entities/rat"); }
    @Override protected net.minecraft.sounds.SoundEvent getAmbientSound() { return random.nextInt(4) == 0 ? net.minecraft.sounds.SoundEvents.SILVERFISH_AMBIENT : null; }
}
