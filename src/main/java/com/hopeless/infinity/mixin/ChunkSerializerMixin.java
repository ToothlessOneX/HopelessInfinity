package com.hopeless.infinity.mixin;

import com.hopeless.infinity.world.Probe;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Chunk saves store each 16-block section's height as one byte ("Y"), so only sections -128..127 (y -2048..2047) survive a save;
 * in a 20,480-block world everything else wraps onto the wrong height and is lost (measured: blocks at y 9,000 and -10,900 came back
 * as air). Saved as an int instead; old byte saves still load (getInt reads either). Runtime (SRG) names, since mixin strings are not remapped.
 */
@Mixin(value = ChunkSerializer.class, remap = false)
public abstract class ChunkSerializerMixin {
    @Unique private static final ThreadLocal<int[]> hopelessinfinity$y = ThreadLocal.withInitial(() -> new int[1]);

    // write (m_63454_): the save loop asks for each section's light by SectionPos.of(chunk, y) just before writing "Y" - remember y there
    @Redirect(method = "m_63454_", remap = false, at = @At(value = "INVOKE", remap = false,
        target = "Lnet/minecraft/core/SectionPos;m_123196_(Lnet/minecraft/world/level/ChunkPos;I)Lnet/minecraft/core/SectionPos;"))
    private static SectionPos hopelessinfinity$writeSection(ChunkPos p, int y) { hopelessinfinity$y.get()[0] = y; return SectionPos.of(p, y); }

    @Redirect(method = "m_63454_", remap = false, at = @At(value = "INVOKE", remap = false, target = "Lnet/minecraft/nbt/CompoundTag;m_128344_(Ljava/lang/String;B)V"))
    private static void hopelessinfinity$writeY(CompoundTag t, String key, byte b) {
        int y = hopelessinfinity$y.get()[0];
        if ("Y".equals(key) && (byte) y == b) { t.putInt(key, y); Probe.yWritten++; }
        else { t.putByte(key, b); if ("Y".equals(key)) Probe.yMismatch++; }
    }

    // read (m_188230_): take the full height, then hand it to every use in the loop (section index and the light layers)
    @Redirect(method = "m_188230_", remap = false, at = @At(value = "INVOKE", remap = false, target = "Lnet/minecraft/nbt/CompoundTag;m_128445_(Ljava/lang/String;)B"))
    private static byte hopelessinfinity$readY(CompoundTag t, String key) { int y = t.getInt(key); hopelessinfinity$y.get()[0] = y; Probe.yRead++; return (byte) y; }

    @Redirect(method = "m_188230_", remap = false, at = @At(value = "INVOKE", remap = false, target = "Lnet/minecraft/server/level/ServerLevel;m_151566_(I)I"))
    private static int hopelessinfinity$readIndex(ServerLevel l, int truncated) { return l.getSectionIndexFromSectionY(hopelessinfinity$y.get()[0]); }

    @Redirect(method = "m_188230_", remap = false, at = @At(value = "INVOKE", remap = false,
        target = "Lnet/minecraft/core/SectionPos;m_123196_(Lnet/minecraft/world/level/ChunkPos;I)Lnet/minecraft/core/SectionPos;"))
    private static SectionPos hopelessinfinity$readSection(ChunkPos p, int truncated) { return SectionPos.of(p, hopelessinfinity$y.get()[0]); }
}
