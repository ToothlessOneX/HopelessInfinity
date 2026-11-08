package com.hopeless.infinity;

import com.hopeless.infinity.planet.*;
import com.hopeless.infinity.world.*;
import com.mojang.brigadier.Command;
import com.mojang.serialization.Codec;
import net.minecraft.commands.Commands;
import com.hopeless.infinity.planet.Seabed;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.DeferredRegister;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Hopeless Infinity: the overworld becomes the start planet of a seeded spiral galaxy (galaxy.*, planet.* hold all the science and
 * geometry as plain Java; this class and world.* connect it to the game).
 */
@Mod(HopelessInfinity.MODID)
public class HopelessInfinity {
    public static final String MODID = "hopelessinfinity", VERSION = "0.29.8";
    public static final Logger LOG = LogManager.getLogger("HopelessInfinity");
    static final DeferredRegister<Codec<? extends ChunkGenerator>> GENS = DeferredRegister.create(Registries.CHUNK_GENERATOR, MODID);
    static final DeferredRegister<Codec<? extends BiomeSource>> BIOMES = DeferredRegister.create(Registries.BIOME_SOURCE, MODID);
    static { GENS.register("planet", () -> PlanetGenerator.CODEC); BIOMES.register("planet", () -> PlanetBiomes.CODEC); }
    static long lastLog;

    public HopelessInfinity() {
        LOG.info("[Hopeless Infinity] {} - real-size planets, height y -2560..5631, packing {}", Credit.line(), packing());
        var bus = FMLJavaModLoadingContext.get().getModEventBus(); GENS.register(bus); BIOMES.register(bus); Net.init();
        com.hopeless.infinity.slope.SlopeReg.BLOCKS.register(bus); com.hopeless.infinity.slope.SlopeReg.ITEMS.register(bus); com.hopeless.infinity.slope.SlopeReg.BES.register(bus);
        com.hopeless.infinity.world.Screens.BLOCKS.register(bus); com.hopeless.infinity.world.Screens.ITEMS.register(bus); com.hopeless.infinity.world.Screens.BES.register(bus);   /* 0.27.3 the video screens */ com.hopeless.infinity.world.Suits.ITEMS.register(bus); com.hopeless.infinity.world.Suits.BLOCKS.register(bus); com.hopeless.infinity.world.Suits.BES.register(bus); com.hopeless.infinity.vehicle.Vehicles.ENTITIES.register(bus); com.hopeless.infinity.vehicle.Vehicles.ITEMS.register(bus); com.hopeless.infinity.world.Gates.BLOCKS.register(bus); com.hopeless.infinity.world.Gates.ITEMS.register(bus); com.hopeless.infinity.world.Gates.BES.register(bus);   // 0.14.0 gates; 0.13.0 X-15; 0.11.0 spacesuits and the oxygen station   // Hopeless Deep's slope and shaped blocks, built in (no dependency)
        bus.addListener((net.minecraftforge.event.BuildCreativeModeTabContentsEvent e) -> { if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.BUILDING_BLOCKS) for (var it : com.hopeless.infinity.slope.SlopeReg.ITEMS.getEntries()) e.accept(it.get());
            if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.COMBAT) for (var m : com.hopeless.infinity.world.Suits.PIECES.values()) for (var it : m.values()) e.accept(it.get());
            if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) { e.accept(com.hopeless.infinity.world.Suits.STATION_ITEM.get()); e.accept(com.hopeless.infinity.world.Gates.FRAME_ITEM.get()); e.accept(com.hopeless.infinity.world.Gates.CONTROLLER_ITEM.get()); }
            if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES) e.accept(com.hopeless.infinity.vehicle.Vehicles.X15_ITEM.get()); });
        // 0.26.0: Hopeless Deep's blocks, models and machines, ported (owner, 2026-11-04: "grab all the models that we made for hopeless deep"; reuse its power grid and infrastructure)
        com.hopeless.infinity.street.StreetReg.BLOCKS.register(bus); com.hopeless.infinity.street.StreetReg.ITEMS.register(bus); com.hopeless.infinity.wild.SkyReg.register(bus); com.hopeless.infinity.town.TownReg.register(bus); com.hopeless.infinity.town.CityReg.register(bus); com.hopeless.infinity.town.OwnReg.register(bus); com.hopeless.infinity.town.CitySounds.SOUNDS.register(bus);
        bus.addListener((net.minecraftforge.event.BuildCreativeModeTabContentsEvent e) -> { if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.BUILDING_BLOCKS) for (var it : com.hopeless.infinity.street.StreetReg.ITEMS.getEntries()) e.accept(it.get()); });
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) com.hopeless.infinity.client.ClientHooks.init(bus);
        var ev = MinecraftForge.EVENT_BUS;
        ev.addListener((ServerAboutToStartEvent e) -> {
            long seed = e.getServer().getWorldData().worldGenOptions().seed(); long t0 = System.nanoTime(); PlanetWorld w = PlanetWorld.of(seed);
            LOG.info("[Hopeless Infinity] world seed {} -> {} (set up in {} ms)", seed, w.describe(), (System.nanoTime() - t0) / 1000000);
            com.hopeless.infinity.world.TidesLink.register(w); });   // 0.4.0: Hopeless Tides on the planet's seas (when installed)
        ev.addListener((ServerStoppedEvent e) -> LOG.info("[Hopeless Infinity] stopped | {} | {} | {} | {} | {}", Probe.line(), Probe.seabedLine(), Probe.caveLine(), Probe.riverLine(), com.hopeless.infinity.world.SiteCmd.probe()));
        ev.addListener(HopelessInfinity::spawn);
        ev.addListener((LevelEvent.Load e) -> { if (e.getLevel() instanceof ServerLevel l && moonFace(l) >= 0) { PlanetWorld mw = bodyWorld(l); if (mw != null) LOG.info("[Hopeless Infinity] moon face {} ({}) loaded: {}", moonFace(l), l.dimension().location(), mw.describe()); } });
        ev.addListener((LevelEvent.Load e) -> { if (!(e.getLevel() instanceof ServerLevel l) || face(l) < 0) return; PlanetWorld w = PlanetWorld.current(); if (w == null) return;
            if (face(l) == 0) { l.getWorldBorder().setCenter(0, 0); l.getWorldBorder().setSize(w.cube.F + 2048); }   // face dimensions share the overworld's border
            LOG.info("[Hopeless Infinity] face {} ({}) loaded: build y {}..{}, face {} blocks wide, border {}, packing {}", face(l), l.dimension().location(), l.getMinBuildHeight(), l.getMaxBuildHeight() - 1, w.cube.F, (long) l.getWorldBorder().getSize(), packing()); });
        ev.addListener(HopelessInfinity::tick);
        ev.addListener((EntityJoinLevelEvent e) -> { if (e.getLevel().isClientSide || !(e.getEntity() instanceof LivingEntity le)) return; /* server only: the attribute syncs to clients */ PlanetWorld w = bodyWorld(e.getLevel());
            if (w != null) Gravity.apply(le, w.gFactor); else Gravity.remove(le); });   // 0.15.0: the planet's or the moon's own gravity
        ev.addListener((LivingFallEvent e) -> { PlanetWorld w = bodyWorld(e.getEntity().level()); if (w != null) { e.setDamageMultiplier((float) (e.getDamageMultiplier() * w.gFactor)); Probe.fallAdjusted++; } });
        ev.addListener((BlockEvent.EntityPlaceEvent e) -> { if (!(e.getLevel() instanceof Level l)) return; PlanetWorld w = bodyWorld(l); if (w != null && !w.cube.inside(e.getPos().getX() + 0.5, e.getPos().getZ() + 0.5)) { e.setCanceled(true); Probe.placeBlocked++; } });
        ev.addListener((PlayerEvent.PlayerLoggedInEvent e) -> { sendSeed(e.getEntity()); if (e.getEntity() instanceof ServerPlayer sp) Net.CH.send(PacketDistributor.PLAYER.with(() -> sp), new Net.ClockOff(com.hopeless.infinity.world.Clock.offsetS)); });
        ev.addListener(com.hopeless.infinity.world.Clock::sleep);   // 0.8.0
        ev.addListener((TickEvent.PlayerTickEvent e) -> { if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer sp) com.hopeless.infinity.world.Suits.serverTick(sp); });   // 0.11.0 rocket belt
        ev.addListener((TickEvent.PlayerTickEvent e) -> { if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp)) return; PlanetWorld w = PlanetWorld.current(); if (w == null) return;   // 0.12.0 space
            try { int mf = moonFace(sp.level()); if (mf >= 0) { PlanetWorld bw = bodyWorld(sp.level()); com.hopeless.infinity.world.Breath.tick(sp, bw == null || bw.atm.kind == com.hopeless.infinity.galaxy.Atmosphere.Kind.NONE ? null : bw.atm, com.hopeless.infinity.world.Breath.metres(sp.getY())); /* 0.18.0 the body's own air (breathable or not) */ if (!sp.isSpectator() && sp.level() != null && sp.getY() >= com.hopeless.infinity.world.Space.top(sp.serverLevel()) + 1) com.hopeless.infinity.world.Space.exit(sp, sp.serverLevel(), mf, w, Faces.body(sp.level().dimension())); return; }   // 0.16.0 off the moon's top: into space   // 0.15.0 the moon: no air (a sealed suit, or 15 s)
                if (com.hopeless.infinity.world.Space.in(sp.level())) { com.hopeless.infinity.world.Space.tick(sp, w); if (sp.level() == null || com.hopeless.infinity.world.Space.in(sp.level())) com.hopeless.infinity.world.Breath.tick(sp, null, 0); }
                else { int f = face(sp.level()); if (f >= 0 && !sp.isSpectator() && sp.getY() >= com.hopeless.infinity.world.Space.top(sp.serverLevel()) + 1) com.hopeless.infinity.world.Space.exit(sp, sp.serverLevel(), f, w); } }
            catch (Throwable t) { Probe.errors++; } });
        ev.addListener((net.minecraftforge.event.server.ServerStartedEvent e) -> { if (PlanetWorld.current() != null) { com.hopeless.infinity.world.DynDims.restore(e.getServer()); com.hopeless.infinity.world.SiteCmd.warm(); } });   /* 0.22.0 the settlement sites, made in the background */   // 0.18.0 the bodies' faces made before
        ev.addListener((TickEvent.ServerTickEvent e) -> { if (e.phase == TickEvent.Phase.END && PlanetWorld.current() != null) com.hopeless.infinity.game.GameTicks.tick(e.getServer()); });   // 0.28.5 the town game's clock
        ev.addListener((TickEvent.ServerTickEvent e) -> { if (e.phase == TickEvent.Phase.END && PlanetWorld.current() != null) com.hopeless.infinity.world.Space.serverTick(e.getServer()); });   // 0.17.0 time warp
        ev.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> { if (e.getEntity() instanceof ServerPlayer sp) com.hopeless.infinity.world.Space.logout(sp); });
        ev.addListener((net.minecraftforge.event.entity.living.LivingDeathEvent e) -> { if (e.getEntity() instanceof ServerPlayer sp && com.hopeless.infinity.world.Space.STATES.remove(sp.getUUID()) != null) { sp.getPersistentData().remove("hinfSpace"); sp.setNoGravity(false); } });
        ev.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> { if (e.getFrom() == com.hopeless.infinity.world.Space.KEY && e.getEntity() instanceof ServerPlayer sp && com.hopeless.infinity.world.Space.STATES.remove(sp.getUUID()) != null) { sp.getPersistentData().remove("hinfSpace"); sp.setNoGravity(false); } });
        ev.addListener((TickEvent.PlayerTickEvent e) -> { if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp) || face(sp.level()) < 0) return; PlanetWorld w = PlanetWorld.current(); if (w == null) return;
            try { com.hopeless.infinity.world.Breath.tick(sp, com.hopeless.infinity.world.Breath.home(w), com.hopeless.infinity.world.Breath.metres(sp.getEyeY())); } catch (Throwable t) { Probe.errors++; } });   // 0.10.0 breathing
        ev.addListener((net.minecraftforge.event.level.ChunkEvent.Load e) -> { if (!(e.getLevel() instanceof ServerLevel l) || !(e.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk ch)) return; int f = face(l); PlanetWorld w = PlanetWorld.current(); if (f < 0 || w == null) return;
            try { com.hopeless.infinity.world.SeamProbe.check(l, ch, f, w); } catch (Throwable t) { Probe.errors++; } });   // 0.8.1
        ev.addListener((LevelEvent.Load e) -> { if (e.getLevel() instanceof ServerLevel l && l.dimension() == net.minecraft.world.level.Level.OVERWORLD) com.hopeless.infinity.world.Clock.load(l); });   // 0.8.0
        ev.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> sendSeed(e.getEntity()));
        ev.addListener((PlayerEvent.PlayerRespawnEvent e) -> sendSeed(e.getEntity()));
        ev.addListener((RegisterCommandsEvent e) -> { var hinf = Commands.literal("hinf").executes(c -> { c.getSource().sendSuccess(() -> Component.literal(report(c.getSource().getPlayer())), false); return Command.SINGLE_SUCCESS; });
            var sb = Commands.literal("seabed");   // 0.5.0: /hinf seabed <kind> takes you to the nearest sea floor of that kind (Seabed's own command, owner 2026-10-31)
            for (Seabed.Kind k : Seabed.Kind.values()) if (k != Seabed.Kind.NONE) sb.then(Commands.literal(k.name().toLowerCase()).executes(c -> { c.getSource().sendSuccess(() -> Component.literal(goSeabed(c.getSource().getPlayer(), k)), false); return Command.SINGLE_SUCCESS; }));
            var town = Commands.literal("town").executes(c -> { c.getSource().sendSuccess(() -> Component.literal(com.hopeless.infinity.world.SiteCmd.run(c.getSource().getPlayer(), false, false)), false); return Command.SINGLE_SUCCESS; })   // 0.22.0 settlement sites near you
                .then(Commands.literal("go").executes(c -> { c.getSource().sendSuccess(() -> Component.literal(com.hopeless.infinity.world.SiteCmd.run(c.getSource().getPlayer(), false, true)), false); return Command.SINGLE_SUCCESS; }));
            var city = Commands.literal("city").executes(c -> { c.getSource().sendSuccess(() -> Component.literal(com.hopeless.infinity.world.SiteCmd.run(c.getSource().getPlayer(), true, false)), false); return Command.SINGLE_SUCCESS; })
                .then(Commands.literal("go").executes(c -> { c.getSource().sendSuccess(() -> Component.literal(com.hopeless.infinity.world.SiteCmd.run(c.getSource().getPlayer(), true, true)), false); return Command.SINGLE_SUCCESS; }));
            var road = Commands.literal("road").executes(c -> { c.getSource().sendSuccess(() -> Component.literal(com.hopeless.infinity.world.SiteCmd.road(c.getSource().getPlayer(), false)), false); return Command.SINGLE_SUCCESS; })   // 0.23.0 stand on the nearest road
                .then(Commands.literal("interstate").executes(c -> { c.getSource().sendSuccess(() -> Component.literal(com.hopeless.infinity.world.SiteCmd.road(c.getSource().getPlayer(), true)), false); return Command.SINGLE_SUCCESS; }));
            var port = Commands.literal("spaceport").executes(c -> { c.getSource().sendSuccess(() -> Component.literal(com.hopeless.infinity.world.SiteCmd.spaceport(c.getSource().getPlayer(), false)), false); return Command.SINGLE_SUCCESS; })   // 0.24.0
                .then(Commands.literal("go").executes(c -> { c.getSource().sendSuccess(() -> Component.literal(com.hopeless.infinity.world.SiteCmd.spaceport(c.getSource().getPlayer(), true)), false); return Command.SINGLE_SUCCESS; }));
            e.getDispatcher().register(hinf.then(sb).then(town).then(city).then(road).then(port)); });
    }
    /** The planet's six face dimensions (the world preset puts our generator in all six; face 0 is the overworld). */
    public static int face(Level l) { int f = Faces.face(l.dimension()); if (f < 0) return -1; if (l.isClientSide) return f; return l instanceof ServerLevel s && s.getChunkSource().getGenerator() instanceof PlanetGenerator ? f : -1; }
    public static boolean onPlanet(Level l) { return face(l) >= 0; }
    /** 0.15.0: the home moon's face a level is (its generator ours), or -1. */
    public static int moonFace(Level l) { if (Faces.body(l.dimension()) < 1) return -1; /* 0.18.0 any body but the home planet */ int f = Faces.bodyFace(l.dimension()); if (l.isClientSide) return f; return l instanceof ServerLevel s && s.getChunkSource().getGenerator() instanceof PlanetGenerator ? f : -1; }
    /** 0.15.0: the world of the body a level is on (the planet or its moon), or null. */
    public static PlanetWorld bodyWorld(Level l) { if (onPlanet(l)) return PlanetWorld.current(); if (moonFace(l) >= 0) { PlanetWorld w = PlanetWorld.current(); return w == null ? null : w.body(Faces.body(l.dimension())); } return null; }
    /** The height lift: a block position must survive packing at the planet's extremes (y 5600 / -2560, x 6.2 million). */
    static String packing() { long p = BlockPos.asLong(6_200_000, 5600, -6_200_000), q = BlockPos.asLong(-6_200_000, -2560, 6_200_000);
        return BlockPos.getX(p) == 6_200_000 && BlockPos.getY(p) == 5600 && BlockPos.getZ(q) == 6_200_000 && BlockPos.getY(q) == -2560 ? "ok" : "BAD (height lift not applied)"; }
    static void sendSeed(net.minecraft.world.entity.player.Player p) {
        if (!(p instanceof ServerPlayer sp)) return; PlanetWorld w = PlanetWorld.current(); if (w == null) return;
        Net.CH.send(PacketDistributor.PLAYER.with(() -> sp), new Net.Seed(w.seed, onPlanet(sp.level()))); }

    static void spawn(LevelEvent.CreateSpawnPosition e) {
        if (!(e.getLevel() instanceof ServerLevel l) || !onPlanet(l)) return; PlanetWorld w = PlanetWorld.current(); if (w == null) return;
        long t0 = System.nanoTime(); int[] sp = w.playerSpawn();   /* 0.27.0 the HQ front door */ BlockPos best = new BlockPos(sp[0], sp[1], sp[2]); Probe.spawnSearch = (System.nanoTime() - t0) / 1000000;
        e.getSettings().setSpawn(best, 0f); e.setCanceled(true);
        LOG.info("[Hopeless Infinity] world spawn {} (search {} ms, {}, coasts tried {}) on face {} {}", best.toShortString(), Probe.spawnSearch, w.spawnKind, w.coastTries, 0, Column.of(w, 0, best.getX(), best.getZ()).biome.key); }

    static void tick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel l)) return; int f = face(l);
        if (f < 0) { int mf = moonFace(l); PlanetWorld mw = mf < 0 ? null : bodyWorld(l); if (mw == null) return;   // 0.15.0 the moon's faces: its edges, gravity and day
            try { Edge.tick(l, mf, mw); Gravity.tick(l, mw); com.hopeless.infinity.world.Clock.tickMoon(l, mf, mw); } catch (Throwable t) { Probe.errors++; if (Probe.errors < 10) LOG.error("[Hopeless Infinity] moon tick failed", t); } return; }
        PlanetWorld w = PlanetWorld.current(); if (w == null) return;
        try { Edge.tick(l, f, w); Gravity.tick(l, w); com.hopeless.infinity.world.Clock.tick(l, f, w); } catch (Throwable t) { Probe.errors++; if (Probe.errors < 10) LOG.error("[Hopeless Infinity] tick failed", t); }
        long now = System.currentTimeMillis(); if (now - lastLog > 60000) { lastLog = now; LOG.info("[Hopeless Infinity] {}", Probe.line()); LOG.info("[Hopeless Infinity] {}", Probe.seabedLine()); LOG.info("[Hopeless Infinity] {}", Probe.caveLine()); LOG.info("[Hopeless Infinity] {}", Probe.riverLine()); LOG.info("[Hopeless Infinity] {}", com.hopeless.infinity.world.SiteCmd.probe()); LOG.info("[Hopeless Infinity] {}", com.hopeless.infinity.world.DynDims.probe()); LOG.info("[Hopeless Infinity] {}", com.hopeless.infinity.world.Clock.probe()); LOG.info("[Hopeless Infinity] {}", com.hopeless.infinity.world.SeamProbe.line()); LOG.info("[Hopeless Infinity] {}", com.hopeless.infinity.world.Breath.probe()); LOG.info("[Hopeless Infinity] {}", com.hopeless.infinity.world.Suits.probe()); LOG.info("[Hopeless Infinity] {}", com.hopeless.infinity.world.Space.probe()); LOG.info("[Hopeless Infinity] {}", com.hopeless.infinity.vehicle.Spaceplane.probe()); LOG.info("[Hopeless Infinity] {}", com.hopeless.infinity.world.Gates.probe(l.getServer())); } }

    /** The nearest column of a sea floor kind: rings out from the player every 24 blocks to 3 km (the planned column, slope included), then a
     *  teleport to just over it (under water: 1.5 blocks over the floor, so the floor is in view). */
    static String goSeabed(ServerPlayer p, Seabed.Kind k) {
        PlanetWorld w = PlanetWorld.current(); if (p == null || w == null || !onPlanet(p.level())) return "[Hopeless Infinity] not on a planet"; int f = face(p.level());
        long t0 = System.nanoTime(); int px = p.getBlockX(), pz = p.getBlockZ(), tried = 0;
        for (int r = 0; r <= 3000; r += 24) { int steps = Math.max(1, (int) (2 * Math.PI * r / 24));
            for (int a = 0; a < steps; a++) { double t = 2 * Math.PI * a / steps; int x = px + (int) Math.round(r * Math.cos(t)), z = pz + (int) Math.round(r * Math.sin(t));
                Column c = Column.of(w, f, x, z); tried++; if (c.voidCol || c.heightM > Seabed.BEACH_TOP + 1 || -c.heightM > (k == Seabed.Kind.DEEP_OOZE ? 2000 : 60) || (k == Seabed.Kind.DEEP_OOZE) != (-c.heightM >= Seabed.DEEP_MIN)) continue;
                if (Column.planned(w, f, x, z).seabed != k) continue;
                p.teleportTo(x + 0.5, c.surfaceY + 1.5, z + 0.5);
                return String.format("[Hopeless Infinity] %s at %d %d %d, %d blocks away (%.0f m deep, %.1f C; %d columns tried in %d ms)", k.name().toLowerCase(), x, c.surfaceY + 1, z, r, -c.heightM, c.tempC, tried, (System.nanoTime() - t0) / 1000000); } }
        return String.format("[Hopeless Infinity] no %s within 3 km (%d columns tried in %d ms)", k.name().toLowerCase(), tried, (System.nanoTime() - t0) / 1000000); }

    static String report(ServerPlayer p) {
        PlanetWorld w = PlanetWorld.current(); StringBuilder b = new StringBuilder("[Hopeless Infinity] " + Credit.line());
        if (w == null) return b.append(" | planet not set up").toString();
        b.append("\n").append(w.describe());
        b.append("\ntides: ").append(com.hopeless.infinity.world.TidesLink.last);
        if (p != null && onPlanet(p.level())) { int f = face(p.level()); double[] s = w.cube.spherePoint(f, p.getX(), p.getZ());
            if (s != null) { Column c = Column.planned(w, f, p.getBlockX(), p.getBlockZ());
                b.append(String.format("\nyou: face %d (%s) u %.4f v %.4f | lat %.3f lon %.3f | ground y %d (%.0f m real) | %s %.1f C, sea floor %s | gravity attribute %.4f (Earth 0.08) | build y %d..%d",
                    f, CubeSphere.NAME[f], w.cube.u(f, p.getX()), w.cube.v(p.getZ()), CubeSphere.lat(s), CubeSphere.lon(s), c.surfaceY, c.heightM, c.biome.key, c.tempC, c.seabed,
                    p.getAttributeValue(net.minecraftforge.common.ForgeMod.ENTITY_GRAVITY.get()), p.level().getMinBuildHeight(), p.level().getMaxBuildHeight() - 1)); }
            else b.append("\nyou: over the void between faces"); }
        return b.append("\n").append(Probe.line()).append("\n").append(Probe.seabedLine()).toString(); }
}
