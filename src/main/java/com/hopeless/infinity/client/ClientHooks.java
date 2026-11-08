package com.hopeless.infinity.client;

import com.hopeless.infinity.HopelessInfinity;
import com.hopeless.infinity.planet.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * Client side: builds the same planet from the seed the server sends, adds Hopeless Infinity lines to F3, and measures the gravity the
 * local player really falls with (the probe: what the game does, not what the formula says).
 */
public final class ClientHooks {
    private ClientHooks() {}
    static volatile PlanetWorld world; static volatile boolean onPlanet;
    static double lastVy = Double.NaN, gSum; static int gN; public static volatile double measuredG = Double.NaN; static long lastLog, lastSky;

    public static void init(net.minecraftforge.eventbus.api.IEventBus bus) {
        com.hopeless.infinity.town.client.OwnClient.init(bus);   // 0.26.0: the ported machines' screens and renderers
        bus.addListener((net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers e) -> { e.registerEntityRenderer((net.minecraft.world.entity.EntityType) com.hopeless.infinity.wild.SkyReg.SKY_EAGLE.get(), ctx -> new com.hopeless.infinity.wild.SkyEagleRenderer(ctx));
            e.registerEntityRenderer((net.minecraft.world.entity.EntityType) com.hopeless.infinity.town.TownReg.RAT.get(), ctx -> new com.hopeless.infinity.town.RatRenderer(ctx)); });
        // slope models are made in code; terrain slopes take their material's colours (grass by biome), shaped blocks their material's
        bus.addListener(com.hopeless.infinity.slope.client.SlopeModels::onBake);
        bus.addListener((net.minecraftforge.client.event.RegisterColorHandlersEvent.Block e) -> {
            for (var en : com.hopeless.infinity.slope.SlopeReg.TERRAIN.entrySet()) { net.minecraft.resources.ResourceLocation mat = com.hopeless.infinity.slope.SlopeReg.MATERIAL.get(en.getKey());
                e.register((st, lvl, pos, tint) -> { net.minecraft.world.level.block.Block mb = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(mat); return tint < 0 || mb == null ? -1 : e.getBlockColors().getColor(mb.defaultBlockState(), lvl, pos, tint); }, en.getValue().get()); }
            e.register((st, lvl, pos, tint) -> { if (tint < 0 || lvl == null || pos == null || !(lvl.getBlockEntity(pos) instanceof com.hopeless.infinity.slope.SlopeReg.ShapedBE be)) return -1; return e.getBlockColors().getColor(be.material, lvl, pos, tint); }, com.hopeless.infinity.slope.SlopeReg.SHAPED.get()); });
        bus.addListener((net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent e) -> e.register(SkyRenderer.EFFECTS, new SkyRenderer.Effects()));   // 0.6.0 our sky
        MinecraftForge.EVENT_BUS.addListener(SkyRenderer::fog);
        MinecraftForge.EVENT_BUS.addListener(FarTerrain::render); MinecraftForge.EVENT_BUS.addListener(PortalRender::render); MinecraftForge.EVENT_BUS.addListener(ScreenRender::render);   /* 0.27.3 the video screens */   /* 0.27.2 the gate portals */ MinecraftForge.EVENT_BUS.addListener(FarTerrain::fog);   // 0.9.0 the planet at distance
        MinecraftForge.EVENT_BUS.addListener(ClientHooks::tick);
        MinecraftForge.EVENT_BUS.addListener(ClientHooks::debug);
        SuitClient.init(bus);   // 0.11.0 spacesuits
        SpaceClient.init(bus);   // 0.12.0 space
        PlaneClient.init(bus);   // 0.13.0 the X-15
    }
    /** 0.15.0: the world of the body the client is on: the moon's on its faces, else the planet's. */
    public static PlanetWorld bodyWorld() { var l = Minecraft.getInstance().level; if (world == null || l == null) return world; var v = SpaceClient.view(); int b = v != null ? v.body() : com.hopeless.infinity.world.Faces.body(l.dimension()); return b > 0 ? world.body(b) : world; }   // 0.16.0 in space: the body below
    public static void seed(long seed, boolean planet) {
        onPlanet = planet; long t0 = System.nanoTime(); world = PlanetWorld.of(seed); com.hopeless.infinity.world.TidesLink.climate(world);   // 0.4.2
        SkyRenderer.world(world);   // 0.6.0
        HopelessInfinity.LOG.info("[Hopeless Infinity] client planet ready in {} ms: {}", (System.nanoTime() - t0) / 1000000, world.describe()); }

    /** Free fall in Minecraft: vy(next) = (vy - g) * 0.98, so g = vy - vy(next) / 0.98 (blocks per tick per tick; x 400 = m/s2). */
    static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return; Player p = Minecraft.getInstance().player; if (p == null) return;
        if (world != null && Minecraft.getInstance().level != null && (onPlanet || SpaceClient.inSpace() || com.hopeless.infinity.world.Faces.body(Minecraft.getInstance().level.dimension()) >= 1)) try { FarTerrain.tick(Minecraft.getInstance().level, bodyWorld(), Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()); } catch (Throwable t) { FarTerrain.errors++; }   // 0.15.0 the moon too   // 0.9.0
        boolean free = !p.onGround() && !p.isInWater() && !p.isInLava() && !p.getAbilities().flying && !p.isFallFlying() && !p.onClimbable() && !p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING) && !p.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION) && !p.isPassenger() && !SuitClient.wasBurning && !SpaceClient.inSpace();
        double vy = p.getDeltaMovement().y;
        if (free && !Double.isNaN(lastVy) && vy < lastVy) { double g = lastVy - vy / 0.98; if (g > 0 && g < 1) { gSum += g; gN++; if (gN >= 10) { measuredG = gSum / gN * 400; gSum = 0; gN = 0; } } }
        lastVy = free ? vy : Double.NaN;
        long now = System.currentTimeMillis(); if (world != null && onPlanet && !Double.isNaN(measuredG) && now - lastLog > 60000) { lastLog = now;
            HopelessInfinity.LOG.info("[Hopeless Infinity] client gravity probe: measured {} game m/s2 in free fall, want {} (vanilla's 32 = Earth's 9.81, x {} for this planet's {} m/s2)", String.format("%.2f", measuredG), String.format("%.2f", 32 * world.gFactor), String.format("%.3f", world.gFactor), String.format("%.2f", world.planet.gravity)); }
        if (world != null && (onPlanet || SpaceClient.inSpace() || (Minecraft.getInstance().level != null && com.hopeless.infinity.world.Faces.body(Minecraft.getInstance().level.dimension()) >= 1)) && now - lastSky > 60000) { lastSky = now; HopelessInfinity.LOG.info("[Hopeless Infinity] {}", SkyRenderer.probe()); HopelessInfinity.LOG.info("[Hopeless Infinity] {}", FarTerrain.probe()); HopelessInfinity.LOG.info("[Hopeless Infinity] {}", PortalRender.probe()); HopelessInfinity.LOG.info("[Hopeless Infinity] {}", ScreenRender.probe()); HopelessInfinity.LOG.info("[Hopeless Infinity] {}", SuitClient.probe()); HopelessInfinity.LOG.info("[Hopeless Infinity] {}", SpaceClient.probe()); HopelessInfinity.LOG.info("[Hopeless Infinity] {}", PlaneClient.probe()); } }   // 0.6.0

    static void debug(CustomizeGuiOverlayEvent.DebugText e) {
        Player p = Minecraft.getInstance().player; PlanetWorld w = world; int f = com.hopeless.infinity.world.Faces.face(p == null ? null : p.level().dimension()); if (p == null || w == null || !onPlanet || f < 0) return;
        var L = e.getLeft(); L.add(""); L.add("[Hopeless Infinity] " + w.system.name() + " planet " + w.planet.index + String.format(" g %.2f m/s2 (%.2f g)", w.planet.gravity, w.gFactor)
            + (Double.isNaN(measuredG) ? "" : String.format(" measured %.2f", measuredG)));
        double[] s = w.cube.spherePoint(f, p.getX(), p.getZ());
        if (s == null) L.add("void between faces"); else L.add(String.format("face %d %s  lat %.4f  lon %.4f  %.1f km to the nearest edge", f, CubeSphere.NAME[f], CubeSphere.lat(s), CubeSphere.lon(s), Math.min(w.cube.F / 2.0 - Math.abs(p.getX()), w.cube.F / 2.0 - Math.abs(p.getZ())) / 1000));
    }
}
