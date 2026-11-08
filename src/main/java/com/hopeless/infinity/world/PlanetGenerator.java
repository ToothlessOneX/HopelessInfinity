package com.hopeless.infinity.world;

import com.hopeless.infinity.HopelessInfinity;
import com.hopeless.infinity.planet.*;
import com.hopeless.infinity.planet.Column;
import com.hopeless.infinity.slope.SlopeReg;
import com.hopeless.infinity.slope.Slopes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Lays down one face of the start planet at real size from Column plans. Columns run from y -2560 to the ground (up to ~2,800 blocks), so
 * sections that are all deepslate, all stone or all sea water are made whole (one value, no per-block work); only the sections where the
 * columns differ are filled block by block.
 */
public final class PlanetGenerator extends ChunkGenerator {
    public static final Codec<PlanetGenerator> CODEC = RecordCodecBuilder.create(i -> i.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
        Codec.INT.optionalFieldOf("face", 0).forGetter(g -> g.face), Codec.INT.optionalFieldOf("body", 0).forGetter(g -> g.body)).apply(i, i.stable(PlanetGenerator::new)));
    public static final int MIN_Y = -2560, HEIGHT = 8192;   // the dimension type hopelessinfinity:planet
    public final int face, body;   // 0.15.0 body: 0 the home planet, 1 its moon

    public PlanetGenerator(BiomeSource bs, int face, int body) { super(bs); this.face = face; this.body = body; }
    @Override protected Codec<? extends ChunkGenerator> codec() { return CODEC; }
    PlanetWorld world() { PlanetWorld w = PlanetWorld.current(); if (w != null && body > 0) w = w.body(body); if (w == null) { Probe.errors++; HopelessInfinity.LOG.error("[Hopeless Infinity] chunk asked for before the planet was set up"); } return w; }

    /** The world of this generator's body (no error log: biomes and heights are asked early). */
    PlanetWorld bodyWorld() { PlanetWorld w = PlanetWorld.current(); return w != null && body > 0 ? w.body(body) : w; }
    /** 0.29.2 a material's block: ChunkFill.name (the one table the offline renderer reads too), parsed once. */
    static BlockState state(Column.Mat m) { BlockState st = MAT_STATES[m.ordinal()]; if (st == null) { st = blockState(com.hopeless.infinity.planet.ChunkFill.name(m)); MAT_STATES[m.ordinal()] = st; } return st; }
    private static final BlockState[] MAT_STATES = new BlockState[Column.Mat.values().length];

    @Override public CompletableFuture<ChunkAccess> createBiomes(Executor ex, RandomState rs, Blender b, StructureManager sm, ChunkAccess chunk) {
        PlanetBiomes pb = (PlanetBiomes) biomeSource; ChunkPos cp = chunk.getPos();
        Terrain.Bio[] col = new Terrain.Bio[16]; PlanetWorld w = bodyWorld();   // biomes are per 4x4 column: work them out once, not once per vertical cell
        if (w != null) for (int i = 0; i < 4; i++) for (int k = 0; k < 4; k++) col[i * 4 + k] = Column.of(w, face, cp.getMinBlockX() + i * 4 + 2, cp.getMinBlockZ() + k * 4 + 2).biome;
        chunk.fillBiomesFromNoise((qx, qy, qz, s) -> w == null ? pb.at(qx << 2, qz << 2) : pb.holder(col[((qx & 3) << 2) | (qz & 3)]), rs.sampler());
        return CompletableFuture.completedFuture(chunk); }

    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Executor ex, Blender blender, RandomState rs, StructureManager sm, ChunkAccess chunk) {
        long t0 = System.nanoTime(); PlanetWorld w = world(); if (w == null) return CompletableFuture.completedFuture(chunk);
        ChunkPos cp = chunk.getPos(); int minY = chunk.getMinBuildHeight();
        // 0.5.0: the 18 x 18 grid (the chunk and the ring round it) once: the slopes of the dry ground and of the sea floor both read it
        Column[] ring = new Column[18 * 18], cols = new Column[256]; byte[] kinds = new byte[256];
        int[] cf = com.hopeless.infinity.planet.ChunkFill.columns(w, face, cp.getMinBlockX(), cp.getMinBlockZ(), ring, cols, kinds); Probe.ringNanos += System.nanoTime() - t0;   // 0.29.2 planet/ChunkFill (shared with tools/WorldView)
        boolean any = cf[0] == 1, allSea = cf[1] == 1; int plain = cf[2], lowestTop = cf[3];
        if (!any) { Probe.chunks++; Probe.genNanos += System.nanoTime() - t0; return CompletableFuture.completedFuture(chunk); }
        LevelChunkSection[] secs = chunk.getSections(); int s0 = chunk.getMinSection();
        // whole sections: deepslate/stone below every column's plain rock (bedrock row aside), sea water above every column's floor up to y -1
        int wholeTop = Math.floorDiv(plain, 16) * 16;   // sections whose top is below 'plain' are rock
        for (int sy = Math.floorDiv(minY, 16); sy * 16 + 15 < wholeTop; sy++) { replace(secs, sy - s0, sy * 16 + 15 < -64 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState(), secs[sy - s0]); Probe.wholeSections++; }
        int waterFrom = allSea ? Math.floorDiv(lowestTop, 16) * 16 + 16 : Integer.MAX_VALUE;   // first section fully above every floor
        if (allSea) for (int sy = Math.floorDiv(waterFrom, 16); sy * 16 + 15 < Terrain.SEA; sy++) { replace(secs, sy - s0, Blocks.WATER.defaultBlockState(), secs[sy - s0]); Probe.wholeSections++; }
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG), surf = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        long blocks = 0; long tSet = System.nanoTime();   // 0.26.0 phase probe: the column block-set loop
        for (int i = 0; i < 256; i++) { Column c = cols[i]; if (c.voidCol) continue; int lx = i >> 4, lz = i & 15, top = c.topY();   /* 0.23.0 bridge decks stand over the ground */
            sectionSet(chunk, lx, minY, lz, Blocks.BEDROCK.defaultBlockState()); blocks++;
            for (int y = Math.max(minY + 1, Math.min(wholeTop, Math.floorDiv(minY, 16) * 16 + 16 <= wholeTop ? wholeTop : minY + 1)); y <= top; y++) {
                if (allSea && y >= waterFrom && y < Terrain.SEA) continue;   // already whole water
                Column.Mat m = c.at(y, minY); if (m == Column.Mat.AIR) continue; sectionSet(chunk, lx, y, lz, state(m)); blocks++; }
            BlockState floor = state(c.top); ocean.update(lx, c.surfaceY, lz, floor); surf.update(lx, c.surfaceY, lz, floor);
            if (c.ovM != null) for (int k = 0; k < c.ovM.length; k++) if (c.ovM[k] != Column.Mat.AIR) surf.update(lx, c.ovY[2 * k + 1], lz, state(c.ovM[k]));
            if (c.surfaceY < Terrain.SEA && !c.dry()) surf.update(lx, Terrain.SEA, lz, state(c.ice ? Column.Mat.ICE : Column.Mat.WATER)); else if (c.snowLayer) surf.update(lx, c.surfaceY + 1, lz, Blocks.SNOW.defaultBlockState()); }
        Probe.setNanos += System.nanoTime() - tSet;
        caves(w, chunk, cp, cols, minY);
        slopes(w, chunk, cp, cols, ring, ocean, surf); if (body == 0) SeabedLife.remember(face, cp, kinds);
        if (body == 0) { long tb = System.nanoTime(); buildings(w, chunk, cp); Probe.buildingNanos += System.nanoTime() - tb; }
        Probe.chunks++; Probe.planetChunks++; Probe.blocksSet += blocks; Probe.genNanos += System.nanoTime() - t0;
        return CompletableFuture.completedFuture(chunk); }

    /**
     * Terrain slopes (Hopeless Deep's slope blocks): on dry ground, a piece whose corners follow the true ground height of the 3 x 3 columns
     * round it (to a quarter block), so hills rise smoothly instead of in steps. Slopes.choose leaves it out where the ground is flat or too
     * steep (corners more than 1.5 blocks apart: cliffs stay blocky). The ring of columns just outside the chunk is worked out too, so
     * neighbouring chunks agree on every shared corner.
     */
    void slopes(PlanetWorld w, ChunkAccess chunk, ChunkPos cp, Column[] cols, Column[] ring, Heightmap ocean, Heightmap surf) {
        long t0 = System.nanoTime();   // 0.29.2 the choice is planet/ChunkFill.slopes; here the blocks are set
        com.hopeless.infinity.planet.ChunkFill.slopes(cols, ring, (x, y, z, sl, corners, replacedTop) -> { var reg = SlopeReg.TERRAIN.get(sl); if (reg == null) { Probe.slopeNoMaterial++; return; }
            BlockState st = reg.get().defaultBlockState().setValue(SlopeReg.CORNERS, corners); Column c = cols[x * 16 + z]; sectionSet(chunk, x, y, z, st);
            if (replacedTop) { sectionSet(chunk, x, y - 1, z, state(c.top)); if (c.snowLayer) sectionSet(chunk, x, y + 1, z, Blocks.AIR.defaultBlockState()); }   // the piece replaced the top block: the one under it shows the same material; no snow floating over it
            ocean.update(x, y, z, st); surf.update(x, y, z, st); });
        Probe.slopeNanos += System.nanoTime() - t0; }
    /** 0.19.0 caves (planet/Caves): karst passages and halls in carbonate country under wet land (flooded below the water table, dripstone in
     *  the dry ones), lava tubes under basalt plains (maria, Venus-like plains). */
    void caves(PlanetWorld w, ChunkAccess chunk, ChunkPos cp, Column[] cols, int minY) {
        long t0 = System.nanoTime(); BlockState air = Blocks.AIR.defaultBlockState(), water = Blocks.WATER.defaultBlockState();   // 0.29.2 the choice is planet/ChunkFill.caves; here the blocks are set
        BlockState hang = Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.VERTICAL_DIRECTION, net.minecraft.core.Direction.DOWN).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DRIPSTONE_THICKNESS, net.minecraft.world.level.block.state.properties.DripstoneThickness.TIP);
        BlockState rise = hang.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.VERTICAL_DIRECTION, net.minecraft.core.Direction.UP); BlockState[] code = {air, water, hang, rise};
        com.hopeless.infinity.planet.ChunkFill.caves(w, face, cp.getMinBlockX(), cp.getMinBlockZ(), cols, minY, (lx, y, lz, k) -> sectionSet(chunk, lx, y, lz, code[k]));
        Probe.caveNanos += System.nanoTime() - t0; }
    /** 0.25.0 the towns' buildings (planet/Buildings): each lot reaching this chunk draws its part (block-state strings, parsed once). Blocks
     *  with a block entity (banners) go through the chunk so their entity is made. */
    void buildings(PlanetWorld w, ChunkAccess chunk, ChunkPos cp) {
        int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ(), minY = chunk.getMinBuildHeight(), maxY = chunk.getMaxBuildHeight(); net.minecraft.core.BlockPos.MutableBlockPos mp = new net.minecraft.core.BlockPos.MutableBlockPos();
        // 0.29.2 which blocks: planet/ChunkFill.structures (town buildings, the cities, the quarry, the billboards, in that order); here they are set
        com.hopeless.infinity.planet.ChunkFill.structures(w, face, x0, z0, (x, y, z, b) -> { if (y <= minY || y >= maxY) return; BlockState st = b.startsWith("sign:") || b.startsWith("shape:") ? null : blockState(b);
            Probe.buildingBlocks++; if (b.startsWith("hopelessinfinity:transformer")) com.hopeless.infinity.town.GridNet.pending(face, net.minecraft.core.BlockPos.asLong(x, y, z));   // 0.26.1 a substation transformer (fed by its line: Buildings.substation)
            if (st == null) { place(chunk, mp, x, y, z, b); return; }   // 0.27.4 signs with text and shaped blocks go through place()
            if (st.hasBlockEntity()) chunk.setBlockState(mp.set(x, y, z), st, false); else sectionSet(chunk, x - x0, y, z - z0, st); },
            (x, y, z, b) -> { if (y <= minY || y >= maxY) return; Probe.metroBlocks++; place(chunk, mp, x, y, z, b); }); }
    private static final java.util.concurrent.ConcurrentHashMap<String, BlockState> STATES = new java.util.concurrent.ConcurrentHashMap<>();
    public static volatile long badBlocks;
    /** A block-state string ("oak_stairs[facing=north,half=bottom]") as a BlockState, parsed once (Hopeless Deep's way). */
    static BlockState blockState(String name) { return STATES.computeIfAbsent(name, n -> { try { return net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), n.contains(":") ? n : "minecraft:" + n, false).blockState(); }
        catch (Exception e) { badBlocks++; com.hopeless.infinity.HopelessInfinity.LOG.error("[Hopeless Infinity] bad block string {}: {}", n, e.getMessage()); return Blocks.STONE.defaultBlockState(); } }); }
    /** 0.26.3 place a generator block string at (x, y, z): plain states into the section; block-entity blocks through the chunk; a shaped
     *  block ("shape:<id>:<turn>:<material>", planet/Tower) as the shaped block with its block entity's data (shape, turn, material) given
     *  to the chunk, which makes the block entity from it when the chunk is finished. */
    public static volatile long shapedPlaced;
    static void place(ChunkAccess chunk, net.minecraft.core.BlockPos.MutableBlockPos mp, int x, int y, int z, String b) {
        if (b.startsWith("shape:")) { String[] p = b.split(":", 4); BlockState sh = com.hopeless.infinity.slope.SlopeReg.SHAPED.get().defaultBlockState(); chunk.setBlockState(mp.set(x, y, z), sh, false);
            net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag(); t.putString("id", "hopelessinfinity:shaped"); t.putInt("x", x); t.putInt("y", y); t.putInt("z", z);
            t.putString("shape", p[1]); t.putInt("orient", Integer.parseInt(p[2])); t.put("material", net.minecraft.nbt.NbtUtils.writeBlockState(blockState(p[3]))); chunk.setBlockEntityNbt(t); shapedPlaced++; return; }
        if (b.startsWith("sign:")) { String[] p = b.substring(5).split("\\|", -1); BlockState st = blockState(p[0]); chunk.setBlockState(mp.set(x, y, z), st, false);   // 0.27.4 a sign with its text (planet/Businesses)
            net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag(); t.putString("id", "minecraft:sign"); t.putInt("x", x); t.putInt("y", y); t.putInt("z", z); t.putBoolean("is_waxed", true);
            net.minecraft.nbt.CompoundTag front = new net.minecraft.nbt.CompoundTag(); net.minecraft.nbt.ListTag msg = new net.minecraft.nbt.ListTag(); for (int i = 0; i < 4; i++) msg.add(net.minecraft.nbt.StringTag.valueOf(net.minecraft.network.chat.Component.Serializer.toJson(net.minecraft.network.chat.Component.literal(3 + i < p.length ? p[3 + i] : ""))));
            front.put("messages", msg); front.putString("color", p[1]); front.putBoolean("has_glowing_text", "1".equals(p[2])); t.put("front_text", front);
            net.minecraft.nbt.CompoundTag back = front.copy(); net.minecraft.nbt.ListTag none = new net.minecraft.nbt.ListTag(); for (int i = 0; i < 4; i++) none.add(net.minecraft.nbt.StringTag.valueOf("{\"text\":\"\"}")); back.put("messages", none); t.put("back_text", back);
            chunk.setBlockEntityNbt(t); signsPlaced++; return; }
        BlockState st = blockState(b); if (st.hasBlockEntity()) chunk.setBlockState(mp.set(x, y, z), st, false); else sectionSet(chunk, x & 15, y, z & 15, st); }
    public static volatile long signsPlaced;
    static void sectionSet(ChunkAccess chunk, int lx, int y, int lz, BlockState st) { chunk.getSection(chunk.getSectionIndex(y)).setBlockState(lx, y & 15, lz, st, false); }
    /** A whole section of one block state (single-value palette), keeping the section's biomes. */
    static void replace(LevelChunkSection[] secs, int i, BlockState st, LevelChunkSection old) {
        PalettedContainer<BlockState> states = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, st, PalettedContainer.Strategy.SECTION_STATES);
        LevelChunkSection n = new LevelChunkSection(states, old.getBiomes()); n.recalcBlockCounts(); secs[i] = n; }

    /** 0.5.0: vanilla's decoration (trees, flowers, ores; vanilla kelp, seagrass and coral are stopped on our planets by FeatureMixin),
     *  then the sea floor's living things from the Seabed plan (SeabedLife). */
    @Override public void applyBiomeDecoration(net.minecraft.world.level.WorldGenLevel level, ChunkAccess chunk, StructureManager sm) {
        super.applyBiomeDecoration(level, chunk, sm);
        PlanetWorld w = bodyWorld(); if (w != null && body == 0) SeabedLife.place(w, face, chunk); }
    @Override public void applyCarvers(WorldGenRegion r, long seed, RandomState rs, BiomeManager bm, StructureManager sm, ChunkAccess c, GenerationStep.Carving step) {}
    @Override public void buildSurface(WorldGenRegion r, StructureManager sm, RandomState rs, ChunkAccess c) {}
    @Override public void spawnOriginalMobs(WorldGenRegion region) {
        ChunkPos cp = region.getCenter(); Holder<Biome> b = region.getBiome(cp.getWorldPosition().atY(region.getMaxBuildHeight() - 1));
        WorldgenRandom r = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed())); r.setDecorationSeed(region.getSeed(), cp.getMinBlockX(), cp.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(region, b, cp, r); }
    @Override public int getGenDepth() { return HEIGHT; }
    @Override public int getSeaLevel() { return Terrain.SEA; }
    @Override public int getMinY() { return MIN_Y; }
    @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor lv, RandomState rs) {
        PlanetWorld w = bodyWorld(); if (w == null) return lv.getMinBuildHeight();
        Column c = Column.of(w, face, x, z); if (c.voidCol) return lv.getMinBuildHeight(); if (c.dry()) return c.surfaceY + 1;
        return switch (type) { case OCEAN_FLOOR, OCEAN_FLOOR_WG -> c.surfaceY + 1; default -> Math.max(c.surfaceY, Terrain.SEA) + 1 + (c.snowLayer && (type == Heightmap.Types.WORLD_SURFACE || type == Heightmap.Types.WORLD_SURFACE_WG) ? 1 : 0); }; }
    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor lv, RandomState rs) {
        PlanetWorld w = bodyWorld(); int minY = lv.getMinBuildHeight(); BlockState[] col = new BlockState[lv.getHeight()];
        Column c = w == null ? null : Column.planned(w, face, x, z);
        for (int i = 0; i < col.length; i++) col[i] = c == null ? Blocks.AIR.defaultBlockState() : state(c.at(minY + i, minY));
        return new NoiseColumn(minY, col); }
    @Override public void addDebugScreenInfo(List<String> out, RandomState rs, BlockPos p) {
        PlanetWorld w = bodyWorld(); if (w == null) { out.add("Hopeless Infinity: planet not set up"); return; }
        Column c = Column.planned(w, face, p.getX(), p.getZ()); out.add(String.format("Infinity plan: face %d %s ground y %d (%.0f m) %.1f C, sea floor %s", face, c.biome.key, c.surfaceY, c.heightM, c.tempC, c.seabed)); }
}
