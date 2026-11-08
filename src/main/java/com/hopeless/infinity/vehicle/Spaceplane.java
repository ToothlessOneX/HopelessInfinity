package com.hopeless.infinity.vehicle;

import com.hopeless.infinity.HopelessInfinity;
import com.hopeless.infinity.galaxy.Atmosphere;
import com.hopeless.infinity.planet.Column;
import com.hopeless.infinity.planet.PlanetWorld;
import com.hopeless.infinity.world.Breath;
import com.hopeless.infinity.world.Faces;
import com.hopeless.infinity.world.Space;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 0.13.0 the X-15 in the world (vehicle/X15: numbers, shape, flight physics). The pilot (right-click to board, sneak to leave) flies it:
 * the nose follows the pilot's look (X15.TURN_DEG_S), forward/back keys move the throttle (off, then 26-100 %, as the XLR99). In the world
 * the pilot's client flies it (real units: a block is a metre, real gravity, the air's lift and drag; Minecraft's vehicle sync carries it to
 * the server) and the server spends the propellant. Fast and high (over HIGH_SPEED and HIGH_AGL over the ground) or over the world top,
 * the flight moves into world/Space's container and runs on the server from there (the ground cannot load under a craft this fast); low
 * or slow again it comes back to the same place over the ground. Hitbox: 3 x 2 blocks (a rotated 15 m box cannot be an axis-aligned one;
 * STATED). Probes: in-world ticks, crashes, hard landings, refuels, hand-overs to space.
 */
public class Spaceplane extends Entity {
    static final EntityDataAccessor<Float> FUEL = SynchedEntityData.defineId(Spaceplane.class, EntityDataSerializers.FLOAT), THROTTLE = SynchedEntityData.defineId(Spaceplane.class, EntityDataSerializers.FLOAT),
        HULL = SynchedEntityData.defineId(Spaceplane.class, EntityDataSerializers.FLOAT), HEAT = SynchedEntityData.defineId(Spaceplane.class, EntityDataSerializers.FLOAT);
    public static final double HIGH_SPEED = 80, HIGH_AGL = 500, LOW_AGL = 300;   // m/s, m: into the container over 80 m/s and 500 m above the ground; back under 300 m (CHOSEN: chunks generate at about 4 blocks a tick)
    public static volatile long ticks, crashes, hardLandings, refuels, handovers, errors; public static volatile String last = "";
    /** Client (the pilot's): velocity in m/s (the world's axes), kept here because Minecraft's own is in blocks a tick and capped on spawn. */
    public final double[] vel = new double[3]; Vec3 lastPos; final double[] srvVel = new double[3]; public double[] lastForces = new double[7];

    public Spaceplane(EntityType<? extends Spaceplane> t, Level l) { super(t, l); blocksBuilding = true; }
    @Override protected void defineSynchedData() { entityData.define(FUEL, (float) X15.PROP_KG); entityData.define(THROTTLE, 0f); entityData.define(HULL, 100f); entityData.define(HEAT, 0f); }
    public float fuel() { return entityData.get(FUEL); } public float throttle() { return entityData.get(THROTTLE); } public float hull() { return entityData.get(HULL); } public float heatNow() { return entityData.get(HEAT); }
    public void setFuel(float f) { entityData.set(FUEL, Math.max(0, f)); } public void setThrottle(float t) { entityData.set(THROTTLE, t <= 0.01f ? 0f : (float) Math.max(X15.MIN_THROTTLE, Math.min(1, t))); }
    public void setHull(float h) { entityData.set(HULL, h); } public void setHeat(float h) { entityData.set(HEAT, h); }
    @Override protected void readAdditionalSaveData(CompoundTag t) { if (t.contains("fuel")) setFuel(t.getFloat("fuel")); if (t.contains("hull")) setHull(t.getFloat("hull")); }
    @Override protected void addAdditionalSaveData(CompoundTag t) { t.putFloat("fuel", fuel()); t.putFloat("hull", hull()); }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return new ClientboundAddEntityPacket(this); }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean canBeCollidedWith() { return true; }
    @Override public boolean isPushable() { return false; }
    @Override protected boolean canAddPassenger(Entity e) { return getPassengers().isEmpty(); }
    @Override public LivingEntity getControllingPassenger() { return !Space.in(level()) && getFirstPassenger() instanceof Player p ? p : null; }
    @Override public double getPassengersRidingOffset() { return X15.BASE_UP + X15.SEAT_UP; }
    @Override protected void positionRider(Entity e, Entity.MoveFunction f) {
        if (!hasPassenger(e)) return; double[] n = nose(getYRot(), getXRot()); double up = X15.BASE_UP + X15.SEAT_UP - 0.9;   // the pilot's eyes in the canopy
        f.accept(e, getX() + n[0] * X15.SEAT_FWD, getY() + up + n[1] * X15.SEAT_FWD, getZ() + n[2] * X15.SEAT_FWD); }
    @Override public InteractionResult interact(Player p, InteractionHand h) { if (p.isSecondaryUseActive()) return InteractionResult.PASS; if (!level().isClientSide) p.startRiding(this); return InteractionResult.sidedSuccess(level().isClientSide); }
    @Override public boolean hurt(DamageSource s, float amount) {
        if (isInvulnerableTo(s) || level().isClientSide || isRemoved()) return false;
        if (s.getEntity() instanceof Player p && p.isCreative()) { discard(); return true; }
        setHull(hull() - amount * 2); if (hull() <= 0) wreck("hit", 0); return true; }
    /** MC yaw/pitch (degrees) -> unit nose (the world's axes). */
    public static double[] nose(float yaw, float pitch) { double y = Math.toRadians(yaw), p = Math.toRadians(pitch); return new double[]{-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)}; }
    public static float[] yawPitch(double[] n) { return new float[]{(float) Math.toDegrees(Math.atan2(-n[0], n[2])), (float) Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, n[1]))))}; }
    public double massKg() { return X15.EMPTY_KG + X15.PILOT_KG + fuel(); }

    @Override public void tick() {
        super.tick();
        try { if (level().isClientSide) { if (isControlledByLocalInstance()) fly(); else if (!Space.in(level())) { vel[0] = vel[1] = vel[2] = 0; } }
            else serverTick(); }
        catch (Throwable t) { errors++; if (errors < 5) HopelessInfinity.LOG.error("[Hopeless Infinity] X-15 tick failed", t); } }

    /** The pilot's client: one step of flight in the world. */
    void fly() {
        PlanetWorld home = PlanetWorld.current(); if (home == null || hull() <= 0) return; PlanetWorld w = bodyOf(home); double g = w.gravity; Atmosphere air = w.atm; double hM = Breath.metres(getY()); double pAtm = air.pressureAt(hM);   // 0.18.0 the body's own air and gravity (none on the moon)
        Entity pilot = getControllingPassenger(); double[] n = nose(getYRot(), getXRot());
        if (pilot != null) { n = X15.turn(n, nose(pilot.getYRot(), pilot.getXRot()), Math.toRadians(X15.TURN_DEG_S) / 20); float[] yp = yawPitch(n); setYRot(yp[0]); setXRot(yp[1]); }
        double[] a = new double[3]; double thr = fuel() > 0 ? throttle() : 0; lastForces = X15.forces(vel, n, pAtm, air.muGPerMol > 0 ? air.muGPerMol : 29, hM, thr, massKg(), a);
        a[1] -= g; for (int i = 0; i < 3; i++) vel[i] += a[i] * 0.05;
        if (onGround()) { if (vel[1] < 0) vel[1] = 0; double hv = Math.hypot(vel[0], vel[2]), lift = lastForces[5] / massKg(), dec = X15.SKID_MU * Math.max(0, g - lift) * 0.05;   // the skids' friction: mu (weight - lift)
            if (hv > 1e-6) { double k = Math.max(0, hv - dec) / hv; vel[0] *= k; vel[2] *= k; } }
        double vyBefore = vel[1], hBefore = Math.hypot(vel[0], vel[2]); boolean wasGround = onGround();
        move(MoverType.SELF, new Vec3(vel[0] * 0.05, vel[1] * 0.05, vel[2] * 0.05));
        if (horizontalCollision && hBefore > X15.CRASH_SPEED) { com.hopeless.infinity.Net.CH.sendToServer(new com.hopeless.infinity.Net.PlaneCtl(throttle(), 2, (float) hBefore)); vel[0] = vel[1] = vel[2] = 0; }
        else if (horizontalCollision) { vel[0] *= 0.2; vel[2] *= 0.2; }
        if (onGround() && !wasGround && -vyBefore > X15.SINK_LIMIT) com.hopeless.infinity.Net.CH.sendToServer(new com.hopeless.infinity.Net.PlaneCtl(throttle(), 1, (float) -vyBefore));
        if (verticalCollision && vel[1] < 0) vel[1] = 0; if (verticalCollision && vel[1] > 0) vel[1] = 0;
        setDeltaMovement(vel[0] * 0.05, vel[1] * 0.05, vel[2] * 0.05); ticks++; }

    /** Server: the propellant, the heat readout, the station's refill, and the hand-over to the container. */
    void serverTick() {
        Vec3 p = position(); if (lastPos != null) { srvVel[0] = (p.x - lastPos.x) * 20; srvVel[1] = (p.y - lastPos.y) * 20; srvVel[2] = (p.z - lastPos.z) * 20; } lastPos = p;
        if (Space.in(level())) return;   // the container: world/Space flies it
        if (getControllingPassenger() == null) { Vec3 d = getDeltaMovement(); setDeltaMovement(d.x * (onGround() ? 0.5 : 0.98), onGround() ? 0 : d.y - 0.04, d.z * (onGround() ? 0.5 : 0.98)); move(MoverType.SELF, getDeltaMovement()); }   // parked or empty: it settles onto the ground
        if (throttle() > 0 && fuel() > 0 && getControllingPassenger() != null) setFuel((float) (fuel() - throttle() * X15.MDOT * 0.05)); else if (getControllingPassenger() == null && throttle() > 0) setThrottle(0);
        PlanetWorld home = PlanetWorld.current(); if (home == null) return; PlanetWorld w = bodyOf(home); int f = Faces.bodyFace(level().dimension()); int body = Math.max(0, Faces.body(level().dimension()));
        if (tickCount % 20 == 0) { refuel(); if (!(getControllingPassenger() instanceof ServerPlayer)) setHeat(0); }
        if (f < 0 || !(getControllingPassenger() instanceof ServerPlayer sp)) return;
        double speed = Math.sqrt(srvVel[0] * srvVel[0] + srvVel[1] * srvVel[1] + srvVel[2] * srvVel[2]); int top = Space.top((ServerLevel) level());
        boolean high = getY() >= top + 1;
        if (!high && speed > HIGH_SPEED && tickCount % 10 == 0) { Column c = Column.of(w, f, getBlockX(), getBlockZ()); high = getY() - (c.dry() ? c.surfaceY : Math.max(c.surfaceY, 0)) > HIGH_AGL; }
        if (high) { handovers++; Space.exitPlane(sp, this, (ServerLevel) level(), f, home, srvVel.clone(), body); } }
    /** 0.16.0 the world this craft is on: the planet, or its moon on a moon face. */
    PlanetWorld bodyOf(PlanetWorld home) { int bid = Faces.body(level().dimension()); PlanetWorld m = bid > 0 ? home.body(bid) : null; return m != null ? m : home; }
    void refuel() { if (fuel() >= X15.PROP_KG || !(level() instanceof ServerLevel l)) return;
        for (var be : net.minecraft.core.BlockPos.betweenClosed(blockPosition().offset(-8, -3, -8), blockPosition().offset(8, 3, 8)))
            if (l.getBlockState(be).is(com.hopeless.infinity.world.Suits.STATION.get()) && com.hopeless.infinity.world.Suits.StationBE.airHere(l, be)) { setFuel((float) Math.min(X15.PROP_KG, fuel() + X15.PROP_KG / X15.REFUEL_S)); refuels++; return; } }
    /** Server: a hard landing or a crash the pilot's client saw. kind 1: touchdown faster than the sink limit (m/s); 2: into a wall (m/s). */
    public void impact(int kind, float speed) {
        if (kind == 1) { setHull((float) (hull() - (speed - X15.SINK_LIMIT) * 15)); hardLandings++; last = String.format("hard landing at %.1f m/s down, hull %.0f %%", speed, hull()); if (hull() <= 0) wreck("hard landing", speed); }
        if (kind == 2) wreck("crash", speed); }
    public void wreck(String why, double speed) {
        crashes++; last = String.format("wrecked (%s, %.0f m/s)", why, speed); HopelessInfinity.LOG.info("[Hopeless Infinity] X-15 {}", last);
        for (Entity e : getPassengers()) { e.stopRiding(); if (speed > 0) e.hurt(level().damageSources().flyIntoWall(), (float) Math.min(1000, 0.5 * 90 * speed * speed / 2000)); }   // the pilot takes the impact (kinetic energy, CHOSEN scale)
        if (level() instanceof ServerLevel l) l.explode(this, getX(), getY(), getZ(), 3f, Level.ExplosionInteraction.NONE);
        discard(); }
    public static String probe() { return String.format("x-15: flight ticks %d, hand-overs to space %d, crashes %d, hard landings %d, refuel steps %d, errors %d%s", ticks, handovers, crashes, hardLandings, refuels, errors, last.isEmpty() ? "" : " | last: " + last); }
}
