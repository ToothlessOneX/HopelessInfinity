package com.hopeless.infinity.mixin;

import com.hopeless.infinity.world.Probe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Villagers look for beds, job blocks and bells with PoiManager.getInRange (radius 48 for a job). Vanilla does that by reading every
 * 16-block section of every chunk in the square, top to bottom, then keeping records with distSqr <= r^2. Vanilla worlds have 24
 * sections a column; this one has 448, so each search did about 19x the vanilla work (measured: 19% of all server CPU, on the
 * server thread). A record more than r blocks above or below fails the distance test, so only the sections within r of the
 * searcher are read here: the same records, in the same order, as lazily (0.7.2). Runtime (SRG) names in the strings.
 */
@Mixin(value = PoiManager.class, remap = false)
public abstract class PoiManagerMixin extends SectionStorage<PoiSection> {
    private PoiManagerMixin() { super(null, null, null, null, null, false, null, null); }   // never called

    @Inject(method = "m_27181_", remap = false, cancellable = true, at = @At("HEAD"))
    private void hopelessinfinity$getInRange(Predicate<Holder<PoiType>> type, BlockPos pos, int radius, PoiManager.Occupancy occ, CallbackInfoReturnable<Stream<PoiRecord>> cir) {
        int lo = Math.max(levelHeightAccessor.getMinSection(), SectionPos.blockToSectionCoord(pos.getY() - radius));
        int hi = Math.min(levelHeightAccessor.getMaxSection() - 1, SectionPos.blockToSectionCoord(pos.getY() + radius));
        int r2 = radius * radius, ring = Math.floorDiv(radius, 16) + 1;
        Probe.poiSearches++;
        cir.setReturnValue(ChunkPos.rangeClosed(new ChunkPos(pos), ring)
            .flatMap(cp -> IntStream.rangeClosed(lo, hi).boxed()
                .map(sy -> getOrLoad(SectionPos.asLong(cp.x, sy, cp.z)))
                .filter(Optional::isPresent)
                .flatMap(o -> o.get().getRecords(type, occ)))
            .filter(rec -> { BlockPos p = rec.getPos(); return Math.abs(p.getX() - pos.getX()) <= radius && Math.abs(p.getZ() - pos.getZ()) <= radius; })   // getInSquare's test
            .filter(rec -> rec.getPos().distSqr(pos) <= r2));                                                                                                 // getInRange's test
    }
}
