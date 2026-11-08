package com.hopeless.infinity.mixin;

import com.hopeless.infinity.world.PlanetGenerator;
import com.hopeless.infinity.world.Probe;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.CoralFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.KelpFeature;
import net.minecraft.world.level.levelgen.feature.SeagrassFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 0.5.0: on Hopeless Infinity's planets the sea floor's plants and corals come from the Seabed plan (world/SeabedLife: real depths, slopes and
 * water temperatures), so vanilla's kelp, seagrass and coral features (placed by biome, at any depth) are stopped there. Only when the chunk
 * generator is ours: other dimensions keep them. Vanilla sea pickles are not touched. Runtime (SRG) name: Feature.place(config, level,
 * generator, random, pos) = m_225028_.
 */
@Mixin(value = Feature.class, remap = false)
public abstract class FeatureMixin {
    @Inject(method = "m_225028_", at = @At("HEAD"), cancellable = true, remap = false)
    private void hinf$seabed(FeatureConfiguration cfg, WorldGenLevel level, ChunkGenerator gen, RandomSource r, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (gen instanceof PlanetGenerator && (self instanceof KelpFeature || self instanceof SeagrassFeature || self instanceof CoralFeature)) { Probe.vanillaStopped++; cir.setReturnValue(false); }
    }
}
