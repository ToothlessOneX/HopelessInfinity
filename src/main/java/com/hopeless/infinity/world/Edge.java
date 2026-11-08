package com.hopeless.infinity.world;

import com.hopeless.infinity.Credit;
import com.hopeless.infinity.HopelessInfinity;
import com.hopeless.infinity.planet.*;
import com.hopeless.infinity.planet.Column;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * The fold over a face edge. Each face is its own dimension; every server tick each entity that has left its face square is moved to
 * the neighbour face's dimension at the same planet point, heading and motion turned with the edge (CubeSphere.transfer). Players near an
 * edge get a short chunk ticket on the far side, so the ground there is loaded before they arrive.
 */
public final class Edge {
    private Edge() {}
    public static final TicketType<ChunkPos> TICKET = TicketType.create("hopelessinfinity_edge", Comparator.comparingLong(ChunkPos::toLong), 200);
    static final int NEAR = 48;   // blocks from an edge at which the far side is loaded

    public static void tick(ServerLevel l, int f, PlanetWorld w) {
        CubeSphere c = w.cube; List<Entity> move = new ArrayList<>();
        for (Entity e : l.getAllEntities()) { if (e.isPassenger() || e.isRemoved()) continue; if (!c.inside(e.getX(), e.getZ())) move.add(e); }
        for (Entity e : move) cross(l, f, w, e);
        if (l.getGameTime() % 20 == 0) for (ServerPlayer p : l.players()) preload(l.getServer(), f, c, p, w.body);
    }
    static void cross(ServerLevel l, int f, PlanetWorld w, Entity e) {
        try {
            CubeSphere c = w.cube; CubeSphere.Fold fd = c.transfer(f, e.getX(), e.getZ());
            if (!c.inside(fd.x(), fd.z())) { Probe.crossFails++; HopelessInfinity.LOG.warn("[Hopeless Infinity] fold from face {} at {},{} landed outside face {}", f, e.getX(), e.getZ(), fd.face()); return; }
            ServerLevel to = DynDims.level(l.getServer(), w.body, fd.face()); if (to == null) { Probe.crossFails++; HopelessInfinity.LOG.error("[Hopeless Infinity] face dimension {} missing", fd.face()); return; }
            Column dest = Column.of(w, fd.face(), (int) Math.floor(fd.x()), (int) Math.floor(fd.z()));
            double y = e.getY(); if (e.onGround() || y < dest.surfaceY + 1) y = Math.max(y, (dest.dry() ? dest.surfaceY : Math.max(dest.surfaceY, dest.surfaceY < Terrain.SEA ? Terrain.SEA - 1 : dest.surfaceY)) + 1);
            Vec3 v = e.getDeltaMovement(); double[] r = fd.rot(); Vec3 nv = new Vec3(r[0] * v.x + r[1] * v.z, v.y, r[2] * v.x + r[3] * v.z);
            float yaw = CubeSphere.yaw(r, e.getYRot());
            to.getChunkSource().addRegionTicket(TICKET, new ChunkPos((int) Math.floor(fd.x()) >> 4, (int) Math.floor(fd.z()) >> 4), 2, new ChunkPos((int) Math.floor(fd.x()) >> 4, (int) Math.floor(fd.z()) >> 4));
            boolean ok = e.teleportTo(to, fd.x(), y, fd.z(), Set.<RelativeMovement>of(), yaw, e.getXRot());
            if (!ok) { Probe.crossFails++; return; }
            Entity moved = e instanceof ServerPlayer ? e : to.getEntity(e.getUUID()); if (moved != null) { moved.setDeltaMovement(nv); moved.hurtMarked = true; }
            Probe.crossings++; Probe.lastCross = String.format("%s face %d -> %d", e.getType().toShortString(), f, fd.face());
            if (e instanceof ServerPlayer) { to.playSound(null, fd.x(), y, fd.z(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.35f, Credit.chimePitch(e.getId(), Probe.crossings));
                HopelessInfinity.LOG.info("[Hopeless Infinity] {} crossed from face {} ({}) to face {} ({}) at {},{} -> {},{} y {} yaw turn {}", e.getName().getString(), f, CubeSphere.NAME[f], fd.face(), CubeSphere.NAME[fd.face()],
                    (long) e.getX(), (long) e.getZ(), (long) fd.x(), (long) fd.z(), (int) y, Math.round(fd.yawTurn())); }
        } catch (Throwable t) { Probe.errors++; HopelessInfinity.LOG.error("[Hopeless Infinity] edge crossing failed", t); } }
    /** A player within NEAR blocks of an edge: load chunks round the fold target of the point just past that edge, in the neighbour face. */
    static void preload(MinecraftServer s, int f, CubeSphere c, ServerPlayer p, int body) {
        double x0 = c.faceX0(f), z0 = c.faceZ0(), x = p.getX(), z = p.getZ();
        double[][] out = {{x0 + c.F + 1, z}, {x0 - 1, z}, {x, z0 + c.F + 1}, {x, z0 - 1}};
        double[] dist = {x0 + c.F - x, x - x0, z0 + c.F - z, z - z0};
        for (int i = 0; i < 4; i++) if (dist[i] < NEAR) {
            CubeSphere.Fold fd = c.transfer(f, out[i][0], out[i][1]); ServerLevel to = s.getLevel(Faces.key(body, fd.face())); if (to == null) continue;
            ChunkPos cp = new ChunkPos((int) Math.floor(fd.x()) >> 4, (int) Math.floor(fd.z()) >> 4); to.getChunkSource().addRegionTicket(TICKET, cp, 3, cp); Probe.tickets++; } }
}
