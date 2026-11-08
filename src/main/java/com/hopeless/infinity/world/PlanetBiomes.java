package com.hopeless.infinity.world;

import com.hopeless.infinity.planet.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.Arrays;
import java.util.stream.Stream;

/** Biomes of the start planet: the vanilla biome Terrain.biome() names for each column (the void between faces is minecraft:the_void). */
public final class PlanetBiomes extends BiomeSource {
    public static final Codec<PlanetBiomes> CODEC = RecordCodecBuilder.create(i -> i.group(RegistryOps.retrieveGetter(Registries.BIOME), Codec.INT.optionalFieldOf("face", 0).forGetter(b -> b.face), Codec.INT.optionalFieldOf("body", 0).forGetter(b -> b.body)).apply(i, i.stable(PlanetBiomes::new)));
    final Holder<Biome>[] holders; public final int face, body;
    @SuppressWarnings("unchecked")
    public PlanetBiomes(HolderGetter<Biome> g, int face, int body) { this.face = face; this.body = body;
        Terrain.Bio[] all = Terrain.Bio.values(); holders = new Holder[all.length];
        for (Terrain.Bio b : all) holders[b.ordinal()] = g.getOrThrow(ResourceKey.create(Registries.BIOME, b.key.contains(":") ? new ResourceLocation(b.key) : new ResourceLocation("minecraft", b.key)));
    }
    @Override protected Codec<? extends BiomeSource> codec() { return CODEC; }
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes() { return Arrays.stream(holders); }
    @Override public Holder<Biome> getNoiseBiome(int qx, int qy, int qz, Climate.Sampler s) { return at((qx << 2) + 2, (qz << 2) + 2); }
    public Holder<Biome> holder(Terrain.Bio b) { return holders[b.ordinal()]; }
    public Holder<Biome> at(int x, int z) {
        PlanetWorld w = PlanetWorld.current(); if (w != null && body > 0) w = w.body(body); if (w == null) return holders[body > 0 ? Terrain.Bio.LUNAR.ordinal() : Terrain.Bio.THE_VOID.ordinal()];
        return holders[Column.of(w, face, x, z).biome.ordinal()]; }
}
