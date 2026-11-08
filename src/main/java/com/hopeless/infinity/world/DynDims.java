package com.hopeless.infinity.world;

import com.hopeless.infinity.HopelessInfinity;
import com.hopeless.infinity.planet.PlanetWorld;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.Executor;

/**
 * 0.18.0 DIMENSIONS MADE WHEN NEEDED (owner, 2026-11-03: the whole system's bodies; up to 172 in one system, too many to list in the world
 * preset). A body's six face dimensions (hopelessinfinity:b{id}_{face}) are made the first time anyone goes there, built into the mod (no
 * library). The server keeps them in its own level map (Forge's forgeGetWorldMap), so they tick, save and close with the rest; the list of
 * the ones made is saved with the world and they are made again at the next start, before anyone joins.
 */
public final class DynDims {
    private DynDims() {}
    public static volatile long made, failed; public static volatile String lastError = "";
    public static ResourceKey<Level> key(int body, int face) { return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(HopelessInfinity.MODID, "b" + body + "_" + face)); }
    /** The body and face of a made dimension's key, or null. */
    public static int[] parse(ResourceKey<Level> k) { if (!k.location().getNamespace().equals(HopelessInfinity.MODID)) return null; String p = k.location().getPath(); if (!p.startsWith("b")) return null; int u = p.indexOf('_'); if (u < 2) return null;
        try { return new int[]{Integer.parseInt(p.substring(1, u)), Integer.parseInt(p.substring(u + 1))}; } catch (NumberFormatException e) { return null; } }

    /** The level of face f of body id, made now if it is not there yet (server thread). Null: no such body, no ground, or it could not be made. */
    public static ServerLevel level(MinecraftServer s, int body, int f) {
        if (body <= 1) return s.getLevel(Faces.key(body, f)); ResourceKey<Level> k = key(body, f); ServerLevel l = s.getLevel(k); if (l != null) return l;
        PlanetWorld home = PlanetWorld.current(); PlanetWorld bw = home == null ? null : home.body(body); if (bw == null || !bw.landable()) return null;
        l = make(s, k, bw, f); if (l != null) { Data d = data(s); if (d.bodies.add(body)) d.setDirty(); } return l; }

    static ServerLevel make(MinecraftServer s, ResourceKey<Level> k, PlanetWorld bw, int f) {
        try { ServerLevel ow = s.overworld(); var types = s.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE);
            String tn = bw.surfaceK > 373 ? "hot" : "moon";   // ultrawarm over 373 K: water boils away, lava runs as in the Nether (CHOSEN)
            var type = types.getHolderOrThrow(ResourceKey.create(Registries.DIMENSION_TYPE, new ResourceLocation(HopelessInfinity.MODID, tn)));
            var biomes = s.registryAccess().lookupOrThrow(Registries.BIOME);
            PlanetGenerator gen = new PlanetGenerator(new PlanetBiomes(biomes, f, bw.body), f, bw.body); LevelStem stem = new LevelStem(type, gen);
            WorldData wd = s.getWorldData(); DerivedLevelData ld = new DerivedLevelData(wd, wd.overworldData());
            ServerLevel l = new ServerLevel(s, field(s, Executor.class), field(s, LevelStorageSource.LevelStorageAccess.class), ld, k, stem, NO_PROGRESS, wd.isDebugWorld(), ow.getSeed(), List.of(), false, null);
            ow.getWorldBorder().addListener(new BorderChangeListener.DelegateBorderChangeListener(l.getWorldBorder()));
            Map<ResourceKey<Level>, ServerLevel> map = s.forgeGetWorldMap(); map.put(k, l); s.markWorldsDirty();
            MinecraftForge.EVENT_BUS.post(new LevelEvent.Load(l)); made++;
            HopelessInfinity.LOG.info("[Hopeless Infinity] made dimension {} ({} face {}: {}, {} K, dimension type {})", k.location(), bw.body, f, bw.surface, Math.round(bw.surfaceK), tn);
            return l; }
        catch (Throwable t) { failed++; lastError = String.valueOf(t); HopelessInfinity.LOG.error("[Hopeless Infinity] could not make dimension " + k.location(), t); return null; } }
    @SuppressWarnings("unchecked") static <T> T field(Object o, Class<T> type) throws IllegalAccessException {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) for (Field fl : c.getDeclaredFields()) if (fl.getType() == type) { fl.setAccessible(true); return (T) fl.get(o); }
        throw new IllegalStateException("no " + type.getSimpleName() + " field on " + o.getClass().getName()); }
    static final ChunkProgressListener NO_PROGRESS = new ChunkProgressListener() {
        @Override public void updateSpawnPos(ChunkPos p) {} @Override public void onStatusChange(ChunkPos p, ChunkStatus st) {} @Override public void start() {} @Override public void stop() {} };

    /** Server start: make again every body's faces made before (players may be standing there). */
    public static void restore(MinecraftServer s) {
        try { Data d = data(s); for (int b : d.bodies) for (int f = 0; f < 6; f++) level(s, b, f); if (!d.bodies.isEmpty()) HopelessInfinity.LOG.info("[Hopeless Infinity] dimensions made again at start: bodies {}", d.bodies); }
        catch (Throwable t) { failed++; lastError = String.valueOf(t); HopelessInfinity.LOG.error("[Hopeless Infinity] restoring dimensions failed", t); } }
    public static String probe() { return String.format("dynamic dimensions: made %d, failed %d%s", made, failed, lastError.isEmpty() ? "" : " (" + lastError + ")"); }

    public static final class Data extends SavedData {
        final TreeSet<Integer> bodies = new TreeSet<>();
        Data() {} Data(CompoundTag t) { for (int b : t.getIntArray("bodies")) bodies.add(b); }
        @Override public CompoundTag save(CompoundTag t) { t.put("bodies", new IntArrayTag(bodies.stream().mapToInt(Integer::intValue).toArray())); return t; }
    }
    static Data data(MinecraftServer s) { return s.overworld().getDataStorage().computeIfAbsent(Data::new, Data::new, "hopelessinfinity_bodies"); }
}
