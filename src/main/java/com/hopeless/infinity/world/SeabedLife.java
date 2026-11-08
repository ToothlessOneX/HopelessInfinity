package com.hopeless.infinity.world;

import com.hopeless.infinity.planet.Column;
import com.hopeless.infinity.planet.PlanetWorld;
import com.hopeless.infinity.planet.Seabed;
import com.hopeless.infinity.planet.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 0.5.0: the living things of the sea floor, placed by the Seabed plan (planet/Seabed, from Hopeless Seabed) in the decoration step, inside
 * the chunk only. Vanilla's kelp, seagrass and coral features are stopped on our planets (mixin/FeatureMixin); vanilla sea pickles stay.
 * Minecraft's own blocks for now (Seabed's 3D models are the next step).
 *
 *  kelp      on KELP rock: a plant on 17.5 % of columns (Macrocystis, 35 canopy plants in a 2 x 100 m belt: Rosenthal, Clarke & Dayton 1974,
 *            Fish. Bull. 72(3)), with 3 fronds (its own column and 2 next to it; GAME: one Minecraft kelp column is one frond). Height by
 *            Seabed's bands: shallower than 4 m the low Laminaria canopy, 2 blocks (stipe 1-1.5 m, frond to 1 m: MarLIN); 4-30 m giant kelp
 *            to the surface (SIMoN: canopy 3-30 m); deeper the kelp park, 40-80 % of the depth (CHOSEN). Age 25: they do not grow on.
 *  seagrass  on SEAGRASS sand: 60 % of columns (published: Zostera cover 47.7 +/- 20.4 %, Galician rias, Front. Mar. Sci. 2016 conf. abstract;
 *            GAME x1.25 as Seabed raises its densities); tall seagrass on 30 % of those with 2 blocks of water (CHOSEN; Zostera leaves to ~1 m+).
 *  coral     on reef rock: a colony on 50 % of columns (Caribbean cover ~50 % before the decline: Gardner et al. 2003, Science 301). Crest
 *            (< 6 m): elkhorn -> horn coral block and horn coral; slope (to 25 m): table corals -> mixed coral blocks with a fan on top;
 *            deep: massive and plating corals -> brain coral blocks, a plant on 20 % (CHOSEN).
 *  marsh     dry high marsh: short cordgrass (grass) on every column; dry low marsh: the tall form (tall grass) (Seabed MarshDensity 1).
 *  ice shore boulders (cobblestone) on 30 % of columns (Seabed IceBoulderDensity 0.3).
 * The choices themselves are planet/Seabed.life (pure Java: the preview draws the same ones); the densities are there.
 */
public final class SeabedLife {
    private SeabedLife() {}
    static final ConcurrentHashMap<Long, byte[]> KINDS = new ConcurrentHashMap<>();
    static long key(int face, ChunkPos cp) { return cp.toLong() * 7 + face; }
    /** The kinds the noise step worked out (the decoration step comes later; a chunk loaded half-made from disk works them out again). */
    static void remember(int face, ChunkPos cp, byte[] kinds) { if (KINDS.size() > 20000) KINDS.clear(); KINDS.put(key(face, cp), kinds); }

    public static void place(PlanetWorld w, int face, ChunkAccess chunk) {
        long t0 = System.nanoTime(); ChunkPos cp = chunk.getPos(); byte[] k = KINDS.remove(key(face, cp));
        Column[] cols = new Column[256]; int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
        for (int i = 0; i < 256; i++) cols[i] = k == null ? Column.planned(w, face, x0 + (i >> 4), z0 + (i & 15)) : Column.of(w, face, x0 + (i >> 4), z0 + (i & 15));
        if (k == null) Probe.lifeMissed++;
        Seabed.Kind[] K = Seabed.Kind.values(); BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(); long seed = w.seed;
        for (int i = 0; i < 256; i++) {
            Column c = cols[i]; if (c.voidCol) continue; Seabed.Kind kind = k == null ? c.seabed : K[k[i]]; if (kind == Seabed.Kind.NONE) continue;
            int lx = i >> 4, lz = i & 15, x = x0 + lx, z = z0 + lz, y1 = c.surfaceY + 1, water = Terrain.SEA - c.surfaceY;   // water: blocks of water over the ground
            if (!chunk.getBlockState(m.set(x, c.surfaceY, z)).isSolidRender(chunk, m)) continue;   // a terrain slope or a structure took the top: leave it
            Seabed.Life life = Seabed.life(seed, x, z, kind, water, -c.heightM); if (life.plant() == Seabed.Plant.NONE) continue;
            BlockState above = chunk.getBlockState(m.set(x, y1, z));
            switch (life.plant()) {
                case KELP -> {
                    if (!above.is(Blocks.WATER)) break; Probe.kelpPlants++; int h = life.height(); kelp(chunk, m, x, z, y1, h, water); int fr = 0;
                    for (int t = 0; t < 8 && fr < 2; t++) { int dx = (int) (Seabed.rnd(seed, x, z, 10 + t) * 3) - 1, dz = (int) (Seabed.rnd(seed, x, z, 20 + t) * 3) - 1; if (dx == 0 && dz == 0) continue;
                        int nx = lx + dx, nz = lz + dz; if (nx < 0 || nz < 0 || nx > 15 || nz > 15) continue; int j = nx * 16 + nz; Column n = cols[j];
                        if (n.voidCol || (k == null ? n.seabed : K[k[j]]) != Seabed.Kind.KELP) continue; int nw = Terrain.SEA - n.surfaceY; if (nw < 2) continue;
                        if (!chunk.getBlockState(m.set(x0 + nx, n.surfaceY + 1, z0 + nz)).is(Blocks.WATER)) continue;
                        kelp(chunk, m, x0 + nx, z0 + nz, n.surfaceY + 1, Math.min(nw, Math.max(1, h - (int) (Seabed.rnd(seed, x, z, 30 + t) * 3))), nw); fr++; } }
                case SEAGRASS -> { if (!above.is(Blocks.WATER)) break; set(chunk, m, x, y1, z, Blocks.SEAGRASS.defaultBlockState()); Probe.seagrass++; }
                case TALL_SEAGRASS -> { if (!above.is(Blocks.WATER)) break; set(chunk, m, x, y1, z, Blocks.TALL_SEAGRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
                    set(chunk, m, x, y1 + 1, z, Blocks.TALL_SEAGRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER)); Probe.tallSeagrass++; }
                case CORAL_BLOCK_ONLY, CORAL_PLANT, CORAL_FAN -> { if (!above.is(Blocks.WATER)) break; int sp = life.coral(); set(chunk, m, x, y1, z, CORAL_BLOCK[sp]); Probe.coralBlocks++;
                    if (life.plant() == Seabed.Plant.CORAL_FAN) { set(chunk, m, x, y1 + 1, z, CORAL_FAN[sp].setValue(BlockStateProperties.WATERLOGGED, true)); Probe.coralFans++; }
                    else if (life.plant() == Seabed.Plant.CORAL_PLANT) { set(chunk, m, x, y1 + 1, z, CORAL_PLANT[sp].setValue(BlockStateProperties.WATERLOGGED, true)); Probe.coralPlants++; } }
                case GRASS -> { if (!above.isAir()) break; set(chunk, m, x, y1, z, Blocks.GRASS.defaultBlockState()); Probe.marshGrass++; }   // dry marsh only (grass cannot stand in water)
                case TALL_GRASS -> { if (!above.isAir()) break;
                    if (chunk.getBlockState(m.set(x, y1 + 1, z)).isAir()) { set(chunk, m, x, y1, z, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER)); set(chunk, m, x, y1 + 1, z, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER)); }
                    else set(chunk, m, x, y1, z, Blocks.GRASS.defaultBlockState());
                    Probe.marshGrass++; }
                case BOULDER -> { if (!above.isAir() && !above.is(Blocks.WATER)) break; set(chunk, m, x, y1, z, Blocks.COBBLESTONE.defaultBlockState()); Probe.boulders++; }
                default -> {}
            }
        }
        Probe.lifeChunks++; Probe.lifeNanos += System.nanoTime() - t0;
    }
    static final BlockState[] CORAL_BLOCK = {Blocks.TUBE_CORAL_BLOCK.defaultBlockState(), Blocks.BRAIN_CORAL_BLOCK.defaultBlockState(), Blocks.BUBBLE_CORAL_BLOCK.defaultBlockState(), Blocks.FIRE_CORAL_BLOCK.defaultBlockState(), Blocks.HORN_CORAL_BLOCK.defaultBlockState()};
    static final BlockState[] CORAL_PLANT = {Blocks.TUBE_CORAL.defaultBlockState(), Blocks.BRAIN_CORAL.defaultBlockState(), Blocks.BUBBLE_CORAL.defaultBlockState(), Blocks.FIRE_CORAL.defaultBlockState(), Blocks.HORN_CORAL.defaultBlockState()};
    static final BlockState[] CORAL_FAN = {Blocks.TUBE_CORAL_FAN.defaultBlockState(), Blocks.BRAIN_CORAL_FAN.defaultBlockState(), Blocks.BUBBLE_CORAL_FAN.defaultBlockState(), Blocks.FIRE_CORAL_FAN.defaultBlockState(), Blocks.HORN_CORAL_FAN.defaultBlockState()};
    /** A kelp frond h blocks tall from y (kelp plant below, the kelp head on top, age 25 so it stays as planned), never above the water. */
    static void kelp(ChunkAccess chunk, BlockPos.MutableBlockPos m, int x, int z, int y, int h, int water) {
        h = Math.max(1, Math.min(h, water));
        for (int j = 0; j < h; j++) { if (!chunk.getBlockState(m.set(x, y + j, z)).is(Blocks.WATER)) { h = j; break; } }
        if (h <= 0) return;
        for (int j = 0; j < h - 1; j++) set(chunk, m, x, y + j, z, Blocks.KELP_PLANT.defaultBlockState());
        set(chunk, m, x, y + h - 1, z, Blocks.KELP.defaultBlockState().setValue(KelpBlock.AGE, 25)); Probe.kelpBlocks += h; }
    static void set(ChunkAccess chunk, BlockPos.MutableBlockPos m, int x, int y, int z, BlockState s) { chunk.setBlockState(m.set(x, y, z), s, false); }
}
