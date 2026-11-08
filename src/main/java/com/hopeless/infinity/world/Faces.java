package com.hopeless.infinity.world;

import com.hopeless.infinity.planet.PlanetWorld;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** The six face dimensions of the start planet: face 0 is the overworld (where the world starts), faces 1-5 are hopelessinfinity:face_1..5.
 *  0.15.0: the home moon's six faces are hopelessinfinity:moon_0..5 (body 1). face() stays the planet's only (every planet system keeps
 *  working as it was); body() and bodyFace() cover both bodies. */
public final class Faces {
    private Faces() {}
    @SuppressWarnings("unchecked") static final ResourceKey<Level>[] KEYS = new ResourceKey[6], MOON = new ResourceKey[6];
    static { KEYS[0] = Level.OVERWORLD; for (int f = 1; f < 6; f++) KEYS[f] = ResourceKey.create(Registries.DIMENSION, new ResourceLocation("hopelessinfinity", "face_" + f));
        for (int f = 0; f < 6; f++) MOON[f] = ResourceKey.create(Registries.DIMENSION, new ResourceLocation("hopelessinfinity", "moon_" + f)); }
    public static ResourceKey<Level> key(int f) { return KEYS[f]; }
    /** The planet face a dimension is, or -1 (the moon's faces are -1 here). */
    public static int face(ResourceKey<Level> k) { for (int f = 0; f < 6; f++) if (KEYS[f].equals(k)) return f; return -1; }
    /** 0.15.0: the body a dimension belongs to: 0 the home planet, 1 its moon, -1 neither. */
    public static int body(ResourceKey<Level> k) { if (face(k) >= 0) return 0; for (int f = 0; f < 6; f++) if (MOON[f].equals(k)) return 1; int[] b = DynDims.parse(k); return b == null ? -1 : b[0]; }   // 0.18.0 and every body made when needed (DynDims)
    /** 0.15.0: the face of either body, or -1. */
    public static int bodyFace(ResourceKey<Level> k) { int f = face(k); if (f >= 0) return f; for (int g = 0; g < 6; g++) if (MOON[g].equals(k)) return g; int[] b = DynDims.parse(k); return b == null ? -1 : b[1]; }
    public static ResourceKey<Level> key(int body, int f) { return body == 0 ? KEYS[f] : body == 1 ? MOON[f] : DynDims.key(body, f); }
    /** The world of a body (server or client: the same seed makes the same one). */
    public static PlanetWorld world(int body) { PlanetWorld w = PlanetWorld.current(); return w == null || body <= 0 ? w : w.body(body); }
}
