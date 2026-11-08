package com.hopeless.infinity.world;

import com.hopeless.infinity.HopelessInfinity;
import com.hopeless.infinity.Net;
import com.hopeless.infinity.galaxy.Orbits;
import com.hopeless.infinity.planet.Column;
import com.hopeless.infinity.planet.CubeSphere;
import com.hopeless.infinity.vehicle.Spaceplane;
import com.hopeless.infinity.vehicle.X15;
import com.hopeless.infinity.planet.PlanetWorld;
import com.hopeless.infinity.planet.Terrain;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 0.12.0 SPACE (owner, 2026-11-02: "top of the build height sounds right"; "fly to them seamlessly"). Above the top of the world (block y
 * TOP) a player is in space: the dimension hopelessinfinity:space, an empty container. Where the player really is lives here: P and V in the
 * home planet's own turning frame (CubeSphere's planet space, metres = blocks, z on the spin pole), moved each tick by real physics:
 * gravity G M / r^2 (G M = g R^2 from the planet's real g and radius), the turning frame's Coriolis (-2 w x V) and centrifugal (-w x (w x P))
 * terms with w the planet's real sidereal spin (Orbits), and the suit's thrust: the rocket belt along the local up (SuitSpec, 21 s), the MMU
 * in any direction (0.09 m/s2, 36.6 m/s of nitrogen; NASA). The player's Minecraft position stays still in the empty dimension; the client
 * draws the planet and the sky from P (client/SpaceClient -> SkyRenderer, FarTerrain), in the block frame of the point under the player, so
 * the ground below is the same ground seen from the top of the world. Going down past TOP puts the player back on the face below at the
 * matching point, height and motion (the speed held to TERMINAL_BT: the air at the world top, STATED until the spaceplane brings re-entry).
 * Heights in space are block heights over the sea (1 block = 1 m above the top), so the crossing is seamless both ways.
 * Probes: in space now, exits, re-entries, ticks, last state, errors.
 */
public final class Space {
    private Space() {}
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(HopelessInfinity.MODID, "space"));
    public static final double TERMINAL_BT = 4.0;   // 80 m/s: about a falling person's terminal speed (skydivers 55-90 m/s), STATED cap on re-entry for now
    static final Vec3 ANCHOR = new Vec3(0.5, 100, 0.5);
    public static final class State { public double[] p = new double[3], v = new double[3]; public int face; public double x, z, alt; public double[] thrust = new double[3]; public boolean belt; public long ticks;
        /** 0.13.0 flying the X-15: its nose (planet space), propellant (kg), throttle, hull (%), heat ratio, the entity in the container. */
        public boolean plane; public double[] nose = new double[3]; public double fuel, throttle, hull = 100, heat; public UUID planeId; public double[] forces = new double[7]; public double ground = Double.NaN;
        /** 0.16.0 the body below (0 the planet, 1 its moon: inside the moon's sphere of influence) and the speed over it (m/s). */
        public int body; public double relSpeed;
        /** 0.17.0 the highest warp level this craft allows now (world/Warp), and why lower than asked. */
        public int maxLv; public String why = ""; }
    public static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    public static volatile long exits, entries, ticks, errors; public static volatile String last = "";

    public static boolean in(Level l) { return l.dimension() == KEY; }
    /** The block y of the world's top (where space starts). */
    public static int top(ServerLevel face) { return face.getMaxBuildHeight() - 1; }
    public static double omega(PlanetWorld w) { return SpaceMath.omega(w); }
    static volatile double omegaCache = Double.NaN; static volatile long omegaSeed = Long.MIN_VALUE;
    static double w(PlanetWorld pw) { if (omegaSeed != pw.seed) { omegaCache = omega(pw); omegaSeed = pw.seed; } return omegaCache; }

    public static void step(double[] p, double[] v, double gm, double w, double[] a) { SpaceMath.step(p, v, gm, w, a); }

    /** A face's player at the top: into space with the matching place and motion. */
    public static void exit(ServerPlayer pl, ServerLevel from, int f, PlanetWorld w) { exit(pl, from, f, w, 0); }
    /** 0.16.0: from either body (body 1: the moon's faces; the place and motion carried into planet space with the moon's own motion and turn). */
    public static void exit(ServerPlayer pl, ServerLevel from, int f, PlanetWorld w, int body) {
        try { ServerLevel sp = from.getServer().getLevel(KEY); if (sp == null) { errors++; return; }
            Near nb = nearOf(from.getServer(), w, body); CubeSphere c = nb.w.cube; double[] s = c.spherePoint(f, pl.getX(), pl.getZ()); if (s == null) return; double[][] fr = c.frame(f, pl.getX(), pl.getZ());
            State st = new State(); double r = c.R + (pl.getY() - Terrain.SEA); Vec3 mv = pl.getDeltaMovement(); double[] bp = {s[0] * r, s[1] * r, s[2] * r}, lv = new double[3];
            for (int i = 0; i < 3; i++) lv[i] = (mv.x * fr[3][0] * fr[1][i] + Math.max(0, mv.y) * fr[0][i] + mv.z * fr[3][1] * fr[2][i]) * 20;   // blocks a tick -> m/s (a block's true length here), the body's frame
            double[] P = nb.toPlanet(bp), V = nb.dirToPlanet(lv), U = nb.pointVel(w, Clock.eph(w), bp); for (int i = 0; i < 3; i++) { st.p[i] = P[i]; st.v[i] = V[i] + U[i]; }
            st.body = body; st.face = f; st.x = pl.getX(); st.z = pl.getZ(); st.alt = pl.getY() - Terrain.SEA; STATES.put(pl.getUUID(), st);
            pl.teleportTo(sp, ANCHOR.x, ANCHOR.y, ANCHOR.z, Set.<RelativeMovement>of(), pl.getYRot(), pl.getXRot()); pl.setNoGravity(true); pl.setDeltaMovement(Vec3.ZERO); exits++;
            send(pl, st); HopelessInfinity.LOG.info("[Hopeless Infinity] {} went into space over face {} at {},{} (height {} m, climbing {} m/s)", pl.getName().getString(), f, (long) st.x, (long) st.z, Math.round(st.alt), String.format("%.1f", mv.y * 20));
        } catch (Throwable t) { errors++; HopelessInfinity.LOG.error("[Hopeless Infinity] going into space failed", t); } }

    /** Server, each player in space each tick. */
    public static void tick(ServerPlayer pl, PlanetWorld w) {
        try { State st = STATES.get(pl.getUUID());
            if (st == null) { st = load(pl, w); if (st == null) { back(pl, w, null, null); return; } }
            ServerLevel l = pl.serverLevel(); if (l.getGameTime() % 20 == 0) l.setDayTime(6000);   // the container's own clock: full light on the player model (the sky is drawn from P)
            double gm = w.planet.gravity * w.cube.R * w.cube.R, t = Clock.seconds(l.getServer().overworld().getGameTime(), 0); var eph = Clock.eph(w);
            Near nb = Near.of(w, eph, t, st.p); st.body = nb.body;
            double[] a = st.plane ? new double[3] : thrust(pl, st, nb); if (st.plane) planeForces(pl, st, w, nb, a);
            ServerLevel topL = l.getServer().getLevel(Faces.key(nb.body, st.face)); double stopUp = (topL == null ? 5631 : top(topL)) - Terrain.SEA + 2000;
            boolean coasting = len(st.thrust) < 1e-6 && !st.belt && (!st.plane || (st.throttle <= 0 && nb.w.atm.pressureAt(st.alt) < Warp.EDGE_ATM));
            int k = coasting && eff > 0 ? Warp.LEVELS[eff] : 1;
            if (k > 1) { int hit = Warp.coast(w, eph, t, st.p, st.v, w(w), k, 0, null, stopUp); if (hit >= 0) { warpStops++; HopelessInfinity.LOG.info("[Hopeless Infinity] {}: warp {} x stopped 2 km over the world top of body {}", pl.getName().getString(), k, hit); asked = 0; } }
            else Warp.coast(w, eph, t, st.p, st.v, w(w), 1, 0, a, Double.NaN);   // 0.17.0 the 1 x tick too: Warp's Verlet path (tools/NearTest: 0 m off a fine reference where the old step drifted 165 m in 600 s); the moon's pull is Warp's own
            st.ticks++; ticks++;
            Near n2 = Near.of(w, eph, t, st.p); double[] bp = n2.toBody(st.p);
            double r = Math.sqrt(bp[0] * bp[0] + bp[1] * bp[1] + bp[2] * bp[2]); st.alt = r - n2.w.cube.R;
            double[] uv = n2.pointVel(w, eph, bp), rvv = {st.v[0] - uv[0], st.v[1] - uv[1], st.v[2] - uv[2]}; st.relSpeed = len(rvv);
            { double sink = -n2.dirToBody(rvv)[0] * bp[0] / r - n2.dirToBody(rvv)[1] * bp[1] / r - n2.dirToBody(rvv)[2] * bp[2] / r, up = st.alt - (stopUp - 2000); double tn = t + k * 0.05;
              st.maxLv = coasting ? Warp.maxLevel(w, eph, tn, st.p, sink, up) : 0; st.why = !coasting ? (st.plane && st.throttle > 0 ? "throttle open" : st.plane && nb.body == 0 && st.alt <= Warp.KARMAN_M ? "in the air (under 100 km)" : "thrusting") : Warp.why(w, eph, tn, st.p, sink, up, Math.max(asked, 0)); }
            double[] loc = n2.w.cube.locate(bp); int nf = (int) loc[0];
            if (nf != st.face || n2.body != nb.body) {   // the point below crossed a face edge (or into the other body's ground): turn the player's heading with the block frame
                double[][] o = frame(nb, st.face, Math.max(nb.w.cube.faceX0(0), Math.min(nb.w.cube.faceX0(0) + nb.w.cube.F - 1, st.x)), Math.max(nb.w.cube.faceZ0(), Math.min(nb.w.cube.faceZ0() + nb.w.cube.F - 1, st.z))), n = frame(n2, nf, loc[1], loc[2]);
                double yr = Math.toRadians(pl.getYRot()), dx = -Math.sin(yr), dz = Math.cos(yr); double[] d = new double[3]; for (int i = 0; i < 3; i++) d[i] = dx * o[1][i] + dz * o[2][i];
                double ndx = d[0] * n[1][0] + d[1] * n[1][1] + d[2] * n[1][2], ndz = d[0] * n[2][0] + d[1] * n[2][1] + d[2] * n[2][2];
                float dy = (float) Math.toDegrees(Math.atan2(-ndx, ndz)) - pl.getYRot(); pl.connection.teleport(0, 0, 0, dy, 0, java.util.EnumSet.allOf(RelativeMovement.class)); }   // relative: the rider stays seated
            if (n2.body != nb.body) HopelessInfinity.LOG.info("[Hopeless Infinity] {} in space: the ground below is now the {}'s ({} km away)", pl.getName().getString(), n2.body == 1 ? "moon" : "planet", Math.round(r / 1000));
            st.body = n2.body; st.face = nf; st.x = loc[1]; st.z = loc[2];
            if (st.plane) planeTick(pl, st, n2, l); else if (pl.position().distanceToSqr(ANCHOR) > 64) pl.teleportTo(l, ANCHOR.x, ANCHOR.y, ANCHOR.z, Set.<RelativeMovement>of(), pl.getYRot(), pl.getXRot());   // the container stays empty: back to the anchor, unseen
            pl.setDeltaMovement(Vec3.ZERO); pl.fallDistance = 0; Suits.noKick(pl);
            send(pl, st); if (st.ticks % 20 == 0) save(pl);
            if (st.ticks % 100 == 0) last = String.format("%s over the %s, face %d at %d,%d: height %.0f m, speed over the ground %.1f m/s, climbing %.1f m/s%s", pl.getName().getString(), st.body == 1 ? "moon" : "planet", st.face, (long) st.x, (long) st.z, st.alt, len(st.v), (st.p[0] * st.v[0] + st.p[1] * st.v[1] + st.p[2] * st.v[2]) / r, st.plane ? String.format(" | X-15 Mach %.2f, q %.0f Pa, heat %.0f %%, alpha %.1f deg, thrust %.0f kN, propellant %.0f kg, hull %.0f %%", st.forces[0], st.forces[1], st.heat * 100, Math.toDegrees(st.forces[3]), st.forces[4] / 1000, st.fuel, st.hull) : "");
            ServerLevel face = l.getServer().getLevel(Faces.key(st.body, st.face)); int topY = face == null ? 5631 : top(face);
            if (st.plane) { if (st.hull <= 0) { wreckInSpace(pl, st, l); return; }
                if (st.ticks % 10 == 0 && n2.w.landable()) { Column c = Column.of(n2.w, st.face, (int) Math.floor(st.x), (int) Math.floor(st.z)); st.ground = c.dry() ? c.surfaceY : Math.max(c.surfaceY, Terrain.SEA); }
                if (!n2.w.landable()) { if (st.alt < 0) crushed(pl, st, n2); }
                else if (!Double.isNaN(st.ground) && st.alt < topY - Terrain.SEA - 2 && (st.alt - st.ground < com.hopeless.infinity.vehicle.Spaceplane.LOW_AGL || st.relSpeed < com.hopeless.infinity.vehicle.Spaceplane.HIGH_SPEED * 0.75)) back(pl, w, st, n2); }
            else if (st.alt < topY - Terrain.SEA - 2) { if (n2.w.landable()) back(pl, w, st, n2); else if (st.alt < 0) crushed(pl, st, n2); }
        } catch (Throwable t) { errors++; if (errors < 5) HopelessInfinity.LOG.error("[Hopeless Infinity] space tick failed", t); } }

    /** The suit's thrust (m/s2, planet space): the belt up the local vertical, the MMU along the client's asked direction (local block frame). */
    static double[] thrust(ServerPlayer pl, State st, Near nb) {
        double[] a = new double[3]; SuitSpec.Kind k = Suits.fullSet(pl); if (k == null || pl.isSpectator()) return a; ItemStack c = pl.getItemBySlot(EquipmentSlot.CHEST);
        double[][] fr = frame(nb, st.face, st.x, st.z); boolean creative = pl.isCreative();
        if (st.belt) { int b = Suits.belt(c); if (b > 0) { double acc = SuitSpec.BELT_N / SuitSpec.massKg(k, b / (double) SuitSpec.BELT_TICKS); for (int i = 0; i < 3; i++) a[i] += fr[0][i] * acc; if (!creative) Suits.setBelt(c, b - 1); Suits.beltTicks++; } }
        double tl = len(st.thrust); if (tl > 1e-6) { double n2 = Suits.n2(c); if (n2 > 0) { double acc = SuitSpec.MMU_ACC;
            for (int i = 0; i < 3; i++) a[i] += (st.thrust[0] * fr[1][i] + st.thrust[1] * fr[0][i] + st.thrust[2] * fr[2][i]) / tl * acc;
            if (!creative) Suits.setN2(c, n2 - acc * 0.05); Suits.mmuTicks++; } }
        return a; }

    /** Down past the top: back onto the face below at the matching place, height and motion. st null: no state (a login in space): to spawn. */
    /** 0.18.0 down into a giant's or mini-Neptune's envelope (no ground: past its 1-bar radius): crushed and burned. */
    static void crushed(ServerPlayer pl, State st, Near nb) { HopelessInfinity.LOG.info("[Hopeless Infinity] {} fell into the envelope of body {} ({}): no surface", pl.getName().getString(), nb.body, nb.w.atm.kind); crushes++;
        pl.displayClientMessage(net.minecraft.network.chat.Component.literal("[Space] no surface here: crushed in the hydrogen envelope").withStyle(net.minecraft.ChatFormatting.RED), false); pl.hurt(pl.damageSources().fellOutOfWorld(), Float.MAX_VALUE); }
    public static volatile long crushes;
    static void back(ServerPlayer pl, PlanetWorld w, State st, Near nb) {
        ServerLevel to; double x, y, z; Vec3 mv = Vec3.ZERO; float yaw = pl.getYRot(); double[] rv = st == null ? null : relVel(w, st, nb);
        if (st == null) { to = pl.getServer().overworld(); var sp = to.getSharedSpawnPos(); x = sp.getX() + 0.5; y = sp.getY() + 1; z = sp.getZ() + 0.5; }
        else { to = DynDims.level(pl.getServer(), nb.body, st.face); if (to == null) { errors++; return; } x = st.x; z = st.z; y = Terrain.SEA + st.alt; y = Math.min(y, top(to) - 3);
            double[][] fr = frame(nb, st.face, x, z); double vx = dot(rv, fr[1]) / 20 / fr[3][0], vy = dot(rv, fr[0]) / 20, vz = dot(rv, fr[2]) / 20 / fr[3][1], sp = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (sp > TERMINAL_BT) { vx *= TERMINAL_BT / sp; vy *= TERMINAL_BT / sp; vz *= TERMINAL_BT / sp; } mv = new Vec3(vx, vy, vz); }
        STATES.remove(pl.getUUID()); pl.getPersistentData().remove("hinfSpace"); pl.setNoGravity(false);
        if (st != null && st.plane) { backPlane(pl, w, st, nb, rv, to, x, y, z); return; }
        pl.teleportTo(to, x, y, z, Set.<RelativeMovement>of(), yaw, pl.getXRot()); pl.setDeltaMovement(mv); pl.hurtMarked = true; entries++;
        Net.CH.send(PacketDistributor.PLAYER.with(() -> pl), new Net.SpaceState(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        HopelessInfinity.LOG.info("[Hopeless Infinity] {} came down from space onto {} face {} at {},{} y {} ({})", pl.getName().getString(), Faces.body(to.dimension()) == 1 ? "the moon's" : "the planet's", Faces.bodyFace(to.dimension()), (long) x, (long) z, (long) y, st == null ? "no saved place: spawn" : String.format("%.1f m/s", mv.length() * 20)); }


    // ---------------- 0.13.0 the X-15 in the container ----------------
    /** A plane over the top, or fast and high: into the container with its pilot; vel: its velocity in the world (blocks a second). */
    public static void exitPlane(ServerPlayer pl, Spaceplane plane, ServerLevel from, int f, PlanetWorld w, double[] vel) { exitPlane(pl, plane, from, f, w, vel, 0); }
    public static void exitPlane(ServerPlayer pl, Spaceplane plane, ServerLevel from, int f, PlanetWorld w, double[] vel, int body) {
        try { ServerLevel sp = from.getServer().getLevel(KEY); if (sp == null) { errors++; return; }
            Near nb = nearOf(from.getServer(), w, body); CubeSphere c = nb.w.cube; double[] s = c.spherePoint(f, plane.getX(), plane.getZ()); if (s == null) return; double[][] fr = c.frame(f, plane.getX(), plane.getZ());
            State st = new State(); double r = c.R + (plane.getY() - Terrain.SEA); double[] n = Spaceplane.nose(plane.getYRot(), plane.getXRot()), bp = {s[0] * r, s[1] * r, s[2] * r}, lv = new double[3], ln = new double[3];
            for (int i = 0; i < 3; i++) { lv[i] = vel[0] * fr[3][0] * fr[1][i] + vel[1] * fr[0][i] + vel[2] * fr[3][1] * fr[2][i]; ln[i] = n[0] * fr[1][i] + n[1] * fr[0][i] + n[2] * fr[2][i]; }
            double[] P = nb.toPlanet(bp), V = nb.dirToPlanet(lv), U = nb.pointVel(w, Clock.eph(w), bp), N = nb.dirToPlanet(ln); for (int i = 0; i < 3; i++) { st.p[i] = P[i]; st.v[i] = V[i] + U[i]; st.nose[i] = N[i]; }
            st.body = body; st.face = f; st.x = plane.getX(); st.z = plane.getZ(); st.alt = plane.getY() - Terrain.SEA; st.plane = true; st.fuel = plane.fuel(); st.throttle = plane.throttle(); st.hull = plane.hull();
            pl.stopRiding(); plane.discard();
            pl.teleportTo(sp, ANCHOR.x, ANCHOR.y, ANCHOR.z, Set.<RelativeMovement>of(), pl.getYRot(), pl.getXRot()); pl.setNoGravity(true); pl.setDeltaMovement(Vec3.ZERO);
            Spaceplane np = com.hopeless.infinity.vehicle.Vehicles.X15_TYPE.get().create(sp); np.moveTo(ANCHOR.x, ANCHOR.y, ANCHOR.z, plane.getYRot(), plane.getXRot()); np.setNoGravity(true); np.setFuel((float) st.fuel); np.setThrottle((float) st.throttle); np.setHull((float) st.hull);
            sp.addFreshEntity(np); pl.startRiding(np, true); st.planeId = np.getUUID(); STATES.put(pl.getUUID(), st); exits++; send(pl, st);
            HopelessInfinity.LOG.info("[Hopeless Infinity] {} flew the X-15 into the high-flight container over face {} at {},{} (height {} m, {} m/s, {} kg propellant)", pl.getName().getString(), f, (long) st.x, (long) st.z, Math.round(st.alt), String.format("%.0f", len(st.v)), Math.round(st.fuel));
        } catch (Throwable t) { errors++; HopelessInfinity.LOG.error("[Hopeless Infinity] the X-15 hand-over failed", t); } }
    /** The real metres of a block height (compressed over 4,000 m below the top; 1:1 above it). */
    static double metres(double alt, int topY) { return alt <= topY ? Breath.metres(alt) : Breath.metres(topY) + (alt - topY); }
    /** The plane's thrust, lift and drag (m/s2, planet space) into a; its propellant spent; heat and hull. */
    static void planeForces(ServerPlayer pl, State st, PlanetWorld w, Near nb, double[] a) {
        double[][] fr = frame(nb, st.face, st.x, st.z); double yr = Math.toRadians(pl.getYRot()), pr = Math.toRadians(pl.getXRot());
        double[] look = {-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr)}, want = new double[3]; for (int i = 0; i < 3; i++) want[i] = look[0] * fr[1][i] + look[1] * fr[0][i] + look[2] * fr[2][i];
        st.nose = X15.turn(st.nose, want, Math.toRadians(X15.TURN_DEG_S) / 20);
        var air = nb.w.atm; ServerLevel face = pl.getServer().getLevel(Faces.key(st.body, st.face)); int topY = face == null ? 5631 : top(face); double hM = metres(st.alt, topY);
        double thr = st.fuel > 0 ? st.throttle : 0, mass = X15.EMPTY_KG + X15.PILOT_KG + st.fuel; double pAtm = air.pressureAt(hM);   // 0.18.0 the air of the body below (none on the moon; Venus-like worlds' thick CO2)
        st.forces = X15.forces(relVel(w, st, nb), st.nose, pAtm, air.muGPerMol > 0 ? air.muGPerMol : 29, hM, thr, mass, a);   // the air moves with its body: the flow is the speed over the ground
        if (thr > 0 && !pl.isCreative()) st.fuel = Math.max(0, st.fuel - thr * X15.MDOT * 0.05);
        st.heat = st.forces[2]; if (st.heat > 1) { st.hull -= (st.heat - 1) * 10 * 0.05; if (st.ticks % 20 == 0) pl.displayClientMessage(net.minecraft.network.chat.Component.literal(String.format("[X-15] airframe overheating: %.0f %% of the Mach 6.7 limit, hull %.0f %%", st.heat * 100, st.hull)).withStyle(net.minecraft.ChatFormatting.RED), true); } }
    /** Each tick: the container's plane at the anchor, turned to the nose (in the block frame below), its readout synced. */
    static void planeTick(ServerPlayer pl, State st, Near nb, ServerLevel l) {
        net.minecraft.world.entity.Entity e = st.planeId == null ? null : l.getEntity(st.planeId);
        if (!(e instanceof Spaceplane np)) { st.plane = false; errors++; HopelessInfinity.LOG.warn("[Hopeless Infinity] the X-15 in the container is missing: the pilot flies on in the suit"); return; }
        if (pl.getVehicle() != np) pl.startRiding(np, true);
        double[][] fr = frame(nb, st.face, st.x, st.z); double[] n = {dot(st.nose, fr[1]), dot(st.nose, fr[0]), dot(st.nose, fr[2])}; float[] yp = Spaceplane.yawPitch(n);
        np.moveTo(ANCHOR.x, ANCHOR.y, ANCHOR.z, yp[0], yp[1]); np.setDeltaMovement(Vec3.ZERO); np.setFuel((float) st.fuel); np.setHull((float) st.hull); np.setHeat((float) st.heat); np.setThrottle((float) st.throttle);
        st.throttle = np.throttle(); }
    public static void throttle(ServerPlayer pl, float t) { State st = STATES.get(pl.getUUID()); if (st != null && st.plane) st.throttle = t <= 0.01f ? 0 : Math.max(X15.MIN_THROTTLE, Math.min(1, t)); }
    static void wreckInSpace(ServerPlayer pl, State st, ServerLevel l) {
        net.minecraft.world.entity.Entity e = st.planeId == null ? null : l.getEntity(st.planeId); if (e != null) { pl.stopRiding(); e.discard(); }
        st.plane = false; Spaceplane.crashes++; Spaceplane.last = String.format("broke up in high flight (heat %.0f %% of the limit, %.0f m/s, height %.0f m)", st.heat * 100, len(st.v), st.alt);
        HopelessInfinity.LOG.info("[Hopeless Infinity] X-15 {}", Spaceplane.last); pl.hurt(pl.damageSources().onFire(), 6f); }
    /** Down low or slow: the plane and pilot back into the world at the same place and motion (no cap: the plane flies). */
    static void backPlane(ServerPlayer pl, PlanetWorld w, State st, Near nb, double[] rv, ServerLevel to, double x, double y, double z) {
        ServerLevel l = pl.serverLevel(); net.minecraft.world.entity.Entity old = st.planeId == null ? null : l.getEntity(st.planeId); pl.stopRiding(); if (old != null) old.discard();
        double[][] fr = frame(nb, st.face, x, z); double[] v = {dot(rv, fr[1]) / fr[3][0], dot(rv, fr[0]), dot(rv, fr[2]) / fr[3][1]}, n = {dot(st.nose, fr[1]), dot(st.nose, fr[0]), dot(st.nose, fr[2])}; float[] yp = Spaceplane.yawPitch(n);
        to.getChunkSource().addRegionTicket(Edge.TICKET, new net.minecraft.world.level.ChunkPos((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4), 3, new net.minecraft.world.level.ChunkPos((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4));
        pl.teleportTo(to, x, y, z, Set.<RelativeMovement>of(), pl.getYRot(), pl.getXRot());
        Spaceplane np = com.hopeless.infinity.vehicle.Vehicles.X15_TYPE.get().create(to); np.moveTo(x, y, z, yp[0], yp[1]); np.setFuel((float) st.fuel); np.setThrottle((float) st.throttle); np.setHull((float) st.hull);
        to.addFreshEntity(np); pl.startRiding(np, true); entries++;
        Net.CH.send(PacketDistributor.PLAYER.with(() -> pl), new Net.SpaceState(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        Net.CH.send(PacketDistributor.PLAYER.with(() -> pl), new Net.PlaneInit(np.getId(), v[0], v[1], v[2]));
        HopelessInfinity.LOG.info("[Hopeless Infinity] {} flew the X-15 back into the world on {} face {} at {},{} y {} ({} m/s, {} m over the ground)", pl.getName().getString(), Faces.body(to.dimension()) == 1 ? "the moon's" : "the planet's", Faces.bodyFace(to.dimension()), (long) x, (long) z, (long) y, String.format("%.0f", Math.sqrt(dot(v, v))), Math.round(y - st.ground)); }

    /** 0.16.0: the block frame at (face, x, z) of the body below, its axes in planet space ({up, ex, ez, metres per block}). */
    static double[][] frame(Near nb, int f, double x, double z) { double[][] fr = nb.w.cube.frame(f, x, z); return new double[][]{nb.dirToPlanet(fr[0]), nb.dirToPlanet(fr[1]), nb.dirToPlanet(fr[2]), fr[3]}; }
    /** The craft's velocity over the ground below (planet space): less the moving, turning body's own velocity there. */
    static double[] relVel(PlanetWorld w, State st, Near nb) { if (nb == null || nb.body == 0) return st.v.clone(); double[] u = nb.pointVel(w, Clock.eph(w), nb.toBody(st.p)); return new double[]{st.v[0] - u[0], st.v[1] - u[1], st.v[2] - u[2]}; }
    static Near nearOf(net.minecraft.server.MinecraftServer s, PlanetWorld w, int body) { double t = Clock.seconds(s.overworld().getGameTime(), 0); Near n = body >= 1 ? Near.body(w, Clock.eph(w), t, body) : null; return n != null ? n : Near.planet(w, t); }
    public static void input(ServerPlayer pl, float tx, float ty, float tz, boolean belt) { State st = STATES.get(pl.getUUID()); if (st == null) return; st.thrust[0] = tx; st.thrust[1] = ty; st.thrust[2] = tz; st.belt = belt;
        if ((Math.abs(tx) + Math.abs(ty) + Math.abs(tz) > 0 || belt) && asked > 0) { asked = 0; HopelessInfinity.LOG.info("[Hopeless Infinity] warp: {} thrust: back to 1 x", pl.getName().getString()); } }

    // ---------------- 0.17.0 time warp ----------------
    /** The warp asked (shared: any player in space sets it) and the one running this tick (the lowest any craft in space allows; 0 when a player is not in space). */
    public static volatile int asked, eff; public static volatile long warpTicks, warpStops; static int lastSentAsked = -1, lastSentEff = -1; static String lastWhy = "";
    public static void warpAsk(ServerPlayer pl, int d) { if (!STATES.containsKey(pl.getUUID())) return; int was = asked; asked = d == 0 ? 0 : Math.max(0, Math.min(Warp.LEVELS.length - 1, asked + d));
        if (asked != was) HopelessInfinity.LOG.info("[Hopeless Infinity] warp: {} asks {} x", pl.getName().getString(), Warp.LEVELS[asked]); }
    /** Server tick end: move the clock on by the warp just run, then set the next tick's warp. */
    public static void serverTick(net.minecraft.server.MinecraftServer s) {
        try { int ran = eff; if (ran > 0) { ServerLevel o = s.overworld(); Clock.addOffset(o, (Warp.LEVELS[ran] - 1) * 0.05); warpTicks++; Net.clock(s); }
            int next = asked; String why = ""; var pls = s.getPlayerList().getPlayers();
            if (pls.isEmpty()) next = 0;
            for (ServerPlayer p : pls) { State st = STATES.get(p.getUUID()); if (st == null || !in(p.level())) { if (next > 0) why = p.getName().getString() + " is not in space"; next = 0; break; } if (st.maxLv < next) { next = st.maxLv; why = st.why; } }
            if (next != eff) HopelessInfinity.LOG.info("[Hopeless Infinity] warp now {} x (asked {} x{}){}", Warp.LEVELS[next], Warp.LEVELS[asked], why.isEmpty() ? "" : ": held by " + why, ran > 0 ? String.format(" | clock offset %.0f s, %d sub-steps, %d stops", Clock.offsetS, Warp.substeps, Warp.stops) : "");
            if (next == 0 && ran > 0) Net.clock(s);
            eff = next; if (eff != lastSentEff || asked != lastSentAsked || !why.equals(lastWhy) || s.getTickCount() % 20 == 0) { lastSentEff = eff; lastSentAsked = asked; lastWhy = why;
                for (ServerPlayer p : pls) if (STATES.containsKey(p.getUUID())) Net.CH.send(PacketDistributor.PLAYER.with(() -> p), new Net.WarpState(asked, eff, why)); } }
        catch (Throwable t) { errors++; if (errors < 5) HopelessInfinity.LOG.error("[Hopeless Infinity] warp tick failed", t); } }
    static void send(ServerPlayer pl, State st) { Net.CH.send(PacketDistributor.PLAYER.with(() -> pl), new Net.SpaceState(true, st.body * 8 + st.face, st.x, st.z, st.alt, st.p[0], st.p[1], st.p[2], st.v[0], st.v[1], st.v[2])); }
    /** Logout and save: the place in space with the player. */
    public static void save(ServerPlayer pl) { State st = STATES.get(pl.getUUID()); if (st == null) return; CompoundTag t = new CompoundTag();
        for (int i = 0; i < 3; i++) { t.putDouble("p" + i, st.p[i]); t.putDouble("v" + i, st.v[i]); t.putDouble("n" + i, st.nose[i]); }
        if (st.plane) { t.putBoolean("plane", true); t.putDouble("fuel", st.fuel); t.putDouble("thr", st.throttle); t.putDouble("hull", st.hull); if (st.planeId != null) t.putUUID("pid", st.planeId); }
        pl.getPersistentData().put("hinfSpace", t); }
    static State load(ServerPlayer pl, PlanetWorld w) { CompoundTag t = pl.getPersistentData().getCompound("hinfSpace"); if (!t.contains("p0")) return null;
        State st = new State(); for (int i = 0; i < 3; i++) { st.p[i] = t.getDouble("p" + i); st.v[i] = t.getDouble("v" + i); }
        Near nb = Near.of(w, Clock.eph(w), Clock.seconds(pl.getServer().overworld().getGameTime(), 0), st.p); double[] loc = nb.w.cube.locate(nb.toBody(st.p)); st.body = nb.body; st.face = (int) loc[0]; st.x = loc[1]; st.z = loc[2];
        if (t.getBoolean("plane")) { st.plane = true; for (int i = 0; i < 3; i++) st.nose[i] = t.getDouble("n" + i); st.fuel = t.getDouble("fuel"); st.throttle = t.getDouble("thr"); st.hull = t.getDouble("hull"); if (t.hasUUID("pid")) st.planeId = t.getUUID("pid");
            if (pl.getVehicle() instanceof Spaceplane sp) st.planeId = sp.getUUID(); }
        STATES.put(pl.getUUID(), st); pl.setNoGravity(true); return st; }
    public static void logout(ServerPlayer pl) { save(pl); STATES.remove(pl.getUUID()); }
    static double len(double[] a) { return Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    public static String probe() { return String.format("space: in space now %d, exits %d, re-entries %d, ticks %d, errors %d | warp: asked %d x, running %d x, warp ticks %d, sub-steps %d, stops %d, clock offset %.0f s%s", STATES.size(), exits, entries, ticks, errors, Warp.LEVELS[asked], Warp.LEVELS[eff], warpTicks, Warp.substeps, Warp.stops, Clock.offsetS, last.isEmpty() ? "" : " | last: " + last); }
}
