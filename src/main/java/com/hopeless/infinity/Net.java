package com.hopeless.infinity;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/** Tells each client the world seed, so it builds the same galaxy and planet (F3 lines, the gravity probe, later the sky). */
public final class Net {
    private Net() {}
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(new ResourceLocation(HopelessInfinity.MODID, "main"), () -> "1", "1"::equals, "1"::equals);
    public record Seed(long seed, boolean planet) {
        void write(FriendlyByteBuf b) { b.writeLong(seed); b.writeBoolean(planet); }
        static Seed read(FriendlyByteBuf b) { return new Seed(b.readLong(), b.readBoolean()); }
        void handle(Supplier<NetworkEvent.Context> c) { c.get().enqueueWork(() -> com.hopeless.infinity.client.ClientHooks.seed(seed, planet)); c.get().setPacketHandled(true); }
    }
    /** 0.8.0: the system clock's offset (seconds), so the client's sky keeps the server's time. */
    public record ClockOff(double offset) {
        void write(FriendlyByteBuf b) { b.writeDouble(offset); }
        static ClockOff read(FriendlyByteBuf b) { return new ClockOff(b.readDouble()); }
        void handle(Supplier<NetworkEvent.Context> c) { c.get().enqueueWork(() -> com.hopeless.infinity.world.Clock.offsetS = offset); c.get().setPacketHandled(true); }
    }
    public static void clock(net.minecraft.server.MinecraftServer s) { CH.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(), new ClockOff(com.hopeless.infinity.world.Clock.offsetS)); }
    /** 0.11.0: the client's rocket belt burns (jump held in the air with a whole suit) or stops; the server spends the propellant. */
    public record BeltBurn(boolean on) {
        void write(FriendlyByteBuf b) { b.writeBoolean(on); }
        static BeltBurn read(FriendlyByteBuf b) { return new BeltBurn(b.readBoolean()); }
        void handle(Supplier<NetworkEvent.Context> c) { var p = c.get().getSender(); c.get().enqueueWork(() -> { if (p != null) com.hopeless.infinity.world.Suits.burn(p, on); }); c.get().setPacketHandled(true); }
    }
    /** 0.12.0: where the player really is in space (planet space P, V; the point below: face, block x, z; height over the sea), each tick; in false: back on a face. */
    public record SpaceState(boolean in, int face, double x, double z, double alt, double px, double py, double pz, double vx, double vy, double vz) {
        void write(FriendlyByteBuf b) { b.writeBoolean(in); b.writeVarInt(face); b.writeDouble(x); b.writeDouble(z); b.writeDouble(alt); b.writeDouble(px); b.writeDouble(py); b.writeDouble(pz); b.writeDouble(vx); b.writeDouble(vy); b.writeDouble(vz); }
        static SpaceState read(FriendlyByteBuf b) { return new SpaceState(b.readBoolean(), b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble()); }
        void handle(Supplier<NetworkEvent.Context> c) { c.get().enqueueWork(() -> com.hopeless.infinity.client.SpaceClient.state(this)); c.get().setPacketHandled(true); }
    }
    /** 0.12.0: the client's thrust in space: the MMU direction in the local block frame (x east, y up, z south; zero = off) and the belt. */
    public record SpaceInput(float tx, float ty, float tz, boolean belt) {
        void write(FriendlyByteBuf b) { b.writeFloat(tx); b.writeFloat(ty); b.writeFloat(tz); b.writeBoolean(belt); }
        static SpaceInput read(FriendlyByteBuf b) { return new SpaceInput(b.readFloat(), b.readFloat(), b.readFloat(), b.readBoolean()); }
        void handle(Supplier<NetworkEvent.Context> c) { var p = c.get().getSender(); c.get().enqueueWork(() -> { if (p != null) com.hopeless.infinity.world.Space.input(p, tx, ty, tz, belt); }); c.get().setPacketHandled(true); }
    }
    /** 0.13.0: the X-15 pilot's throttle (kind 0) or what the client saw: 1 a hard landing (value: m/s down), 2 a crash (value: m/s). */
    public record PlaneCtl(float throttle, int kind, float value) {
        void write(FriendlyByteBuf b) { b.writeFloat(throttle); b.writeVarInt(kind); b.writeFloat(value); }
        static PlaneCtl read(FriendlyByteBuf b) { return new PlaneCtl(b.readFloat(), b.readVarInt(), b.readFloat()); }
        void handle(Supplier<NetworkEvent.Context> c) { var p = c.get().getSender(); c.get().enqueueWork(() -> { if (p == null) return;
            if (p.getVehicle() instanceof com.hopeless.infinity.vehicle.Spaceplane sp) { if (kind == 0) sp.setThrottle(throttle); else sp.impact(kind, value); }
            if (kind == 0) com.hopeless.infinity.world.Space.throttle(p, throttle); }); c.get().setPacketHandled(true); }
    }
    /** 0.13.0: an X-15 put back into the world: its velocity (m/s, the world's axes) for the pilot's client. */
    public record PlaneInit(int id, double vx, double vy, double vz) {
        void write(FriendlyByteBuf b) { b.writeVarInt(id); b.writeDouble(vx); b.writeDouble(vy); b.writeDouble(vz); }
        static PlaneInit read(FriendlyByteBuf b) { return new PlaneInit(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble()); }
        void handle(Supplier<NetworkEvent.Context> c) { c.get().enqueueWork(() -> com.hopeless.infinity.client.PlaneClient.init(id, vx, vy, vz)); c.get().setPacketHandled(true); }
    }
    /** 0.17.0 time warp: the client asks one level up (+1), down (-1) or stop (0). */
    public record WarpAsk(int d) {
        void write(FriendlyByteBuf b) { b.writeVarInt(d); }
        static WarpAsk read(FriendlyByteBuf b) { return new WarpAsk(b.readVarInt()); }
        void handle(Supplier<NetworkEvent.Context> c) { var p = c.get().getSender(); c.get().enqueueWork(() -> { if (p != null) com.hopeless.infinity.world.Space.warpAsk(p, d); }); c.get().setPacketHandled(true); }
    }
    /** 0.17.0 the warp asked and running (level indices into world/Warp.LEVELS), and why it is held lower. */
    public record WarpState(int asked, int eff, String why) {
        void write(FriendlyByteBuf b) { b.writeVarInt(asked); b.writeVarInt(eff); b.writeUtf(why, 80); }
        static WarpState read(FriendlyByteBuf b) { return new WarpState(b.readVarInt(), b.readVarInt(), b.readUtf(80)); }
        void handle(Supplier<NetworkEvent.Context> c) { c.get().enqueueWork(() -> com.hopeless.infinity.client.SpaceClient.warp(asked, eff, why)); c.get().setPacketHandled(true); }
    }
    static void init() { CH.registerMessage(7, WarpAsk.class, WarpAsk::write, WarpAsk::read, WarpAsk::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CH.registerMessage(8, WarpState.class, WarpState::write, WarpState::read, WarpState::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH.registerMessage(5, PlaneCtl.class, PlaneCtl::write, PlaneCtl::read, PlaneCtl::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CH.registerMessage(6, PlaneInit.class, PlaneInit::write, PlaneInit::read, PlaneInit::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
 CH.registerMessage(3, SpaceState.class, SpaceState::write, SpaceState::read, SpaceState::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH.registerMessage(4, SpaceInput.class, SpaceInput::write, SpaceInput::read, SpaceInput::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CH.registerMessage(2, BeltBurn.class, BeltBurn::write, BeltBurn::read, BeltBurn::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER)); CH.registerMessage(0, Seed.class, Seed::write, Seed::read, Seed::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH.registerMessage(1, ClockOff.class, ClockOff::write, ClockOff::read, ClockOff::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT)); }
}
