package com.hopeless.infinity.town;

import com.hopeless.infinity.HopelessInfinity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** 0.34.0: a code typed on a keypad door goes to the server, which checks it (the player within 6 blocks, 1-8 digits). */
public final class OwnNet {
    private OwnNet() {}
    static final String V = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(new ResourceLocation(HopelessInfinity.MODID, "own"), () -> V, V::equals, V::equals);
    public static volatile long codes, rejected;
    static void init() { CH.messageBuilder(Code.class, 0, NetworkDirection.PLAY_TO_SERVER).encoder(Code::enc).decoder(Code::dec).consumerMainThread(Code::handle).add(); }
    public static final class Code {
        final BlockPos pos; final String code;
        public Code(BlockPos pos, String code) { this.pos = pos; this.code = code; }
        void enc(FriendlyByteBuf b) { b.writeBlockPos(pos); b.writeUtf(code, 16); }
        static Code dec(FriendlyByteBuf b) { return new Code(b.readBlockPos(), b.readUtf(16)); }
        void handle(Supplier<NetworkEvent.Context> c) { ServerPlayer p = c.get().getSender(); c.get().setPacketHandled(true);
            if (p == null || !code.matches("[0-9]{1,8}") || p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 36 || !p.level().isLoaded(pos)) { rejected++; return; }
            codes++; OwnReg.KeypadDoor.tryCode(p.serverLevel(), pos, p, code); }
    }
}
