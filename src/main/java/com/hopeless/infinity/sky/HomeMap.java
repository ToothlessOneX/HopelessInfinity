package com.hopeless.infinity.sky;

import com.hopeless.infinity.planet.CubeSphere;
import com.hopeless.infinity.planet.PlanetWorld;
import com.hopeless.infinity.planet.Terrain;

/**
 * 0.15.0 the home planet seen from its moon: its real surface as an albedo map (longitude x latitude, CubeSphere's lat/lon), baked once from
 * the same Terrain the ground is made of, for the planet's disk in the moon's sky (sky.glsl, disk kind 4, turned with the planet's real spin).
 * Albedos by surface type (Coakley 2003, "Reflectance and albedo, surface", Encyclopedia of Atmospheric Sciences: ocean ~0.06, forest
 * 0.08-0.15, grassland ~0.25, desert ~0.35-0.40, fresh snow ~0.8, sea ice ~0.5-0.7), tinted to each surface's colour. No clouds yet (the
 * planet has no cloud model): it looks clearer and darker than a real cloudy world (Earth's 0.367 geometric albedo is half cloud).
 */
public final class HomeMap {
    private HomeMap() {}
    public static final int W = 512, H = 256;
    public static float[] bake(PlanetWorld w) {
        float[] out = new float[W * H * 3]; double R = w.cube.R;
        for (int j = 0; j < H; j++) { double lat = Math.PI / 2 - (j + 0.5) / H * Math.PI;
            for (int i = 0; i < W; i++) { double lon = (i + 0.5) / W * 2 * Math.PI - Math.PI; double[] s = {-Math.sin(lon) * Math.cos(lat), Math.cos(lon) * Math.cos(lat), Math.sin(lat)};
                double h = w.terrain.heightM(s, R); float[] a = albedo(w.terrain.biome(s, h), h); int k = (j * W + i) * 3; out[k] = a[0]; out[k + 1] = a[1]; out[k + 2] = a[2]; } }
        return out; }
    static float[] albedo(Terrain.Bio b, double h) {
        return switch (b) {
            case FROZEN_OCEAN, DEEP_FROZEN_OCEAN, ICE_SPIKES, SNOWY_PLAINS, SNOWY_SLOPES, FROZEN_PEAKS, SNOWY_BEACH, SNOWY_TAIGA -> new float[]{0.78f, 0.80f, 0.84f};
            case DEEP_OCEAN, DEEP_COLD_OCEAN, DEEP_LUKEWARM_OCEAN -> new float[]{0.03f, 0.05f, 0.09f};
            case OCEAN, COLD_OCEAN, LUKEWARM_OCEAN, WARM_OCEAN -> new float[]{0.04f, 0.07f, 0.12f};
            case DESERT, BEACH -> new float[]{0.42f, 0.36f, 0.25f};
            case BADLANDS -> new float[]{0.32f, 0.19f, 0.11f};
            case SAVANNA -> new float[]{0.22f, 0.20f, 0.11f};
            case STONY_PEAKS, JAGGED_PEAKS, STONY_SHORE, WINDSWEPT_HILLS -> new float[]{0.20f, 0.20f, 0.19f};
            case JUNGLE, SPARSE_JUNGLE, DARK_FOREST -> new float[]{0.05f, 0.09f, 0.04f};
            case FOREST, BIRCH_FOREST, FLOWER_FOREST, TAIGA, OLD_GROWTH_SPRUCE_TAIGA, SWAMP -> new float[]{0.08f, 0.12f, 0.06f};
            default -> new float[]{0.16f, 0.22f, 0.10f}; }; }
}
