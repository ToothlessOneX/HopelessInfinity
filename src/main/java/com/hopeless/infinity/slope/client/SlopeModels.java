package com.hopeless.infinity.slope.client;

import com.hopeless.infinity.slope.ShapeLib;
import com.hopeless.infinity.slope.SlopeMesh;
import com.hopeless.infinity.slope.Slopes;
import com.hopeless.infinity.slope.SlopeReg;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.pipeline.QuadBakingVertexConsumer;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 0.26.0: client models for slope blocks, made in code from the same geometry as the collision and the previews (SlopeMesh, ShapeLib).
 * They copy the textures and tint of their material's own model (top, side and bottom faces), so they look like that block.
 */
public final class SlopeModels {
    private SlopeModels() {}
    public static volatile long quadsBuilt, modelsReplaced, materialLookups;   // probes

    /** Swap the baked models of every slope block state for the code-made ones. */
    public static void onBake(ModelEvent.ModifyBakingResult e) {
        Map<ResourceLocation, BakedModel> m = e.getModels();
        for (Map.Entry<String, net.minecraftforge.registries.RegistryObject<Block>> en : SlopeReg.TERRAIN.entrySet()) {
            Block b = en.getValue().get(); Block mat = ForgeRegistries.BLOCKS.getValue(SlopeReg.MATERIAL.get(en.getKey()));
            TerrainModel tm = new TerrainModel(mat == null ? Blocks.STONE.defaultBlockState() : mat.defaultBlockState());
            for (BlockState st : b.getStateDefinition().getPossibleStates()) { m.put(BlockModelShaper.stateToModelLocation(st), tm); modelsReplaced++; }
        }
        ShapedModel sm = new ShapedModel();
        for (BlockState st : SlopeReg.SHAPED.get().getStateDefinition().getPossibleStates()) { m.put(BlockModelShaper.stateToModelLocation(st), sm); modelsReplaced++; }
        // items: the same models, drawn with a block item's hand and inventory transforms
        BakedModel ref = m.get(new net.minecraft.client.resources.model.ModelResourceLocation("minecraft", "stone", "inventory"));
        ItemTransforms tf = ref == null ? ItemTransforms.NO_TRANSFORMS : ref.getTransforms();
        for (Map.Entry<String, net.minecraftforge.registries.RegistryObject<Block>> en : SlopeReg.TERRAIN.entrySet()) {
            Block b = en.getValue().get(); BlockState st = b.defaultBlockState(); BakedModel bm = m.get(BlockModelShaper.stateToModelLocation(st));
            m.put(new net.minecraft.client.resources.model.ModelResourceLocation(ForgeRegistries.BLOCKS.getKey(b), "inventory"), new ItemWrap(bm, st, ModelData.EMPTY, tf)); modelsReplaced++;
        }
        for (Map.Entry<String, net.minecraftforge.registries.RegistryObject<net.minecraft.world.item.Item>> en : SlopeReg.SHAPE_ITEMS.entrySet()) {
            int shape = ShapeLib.INDEX.get(en.getKey()); ModelData d = ModelData.builder().with(SlopeReg.ShapeData.KEY, new SlopeReg.ShapeData(shape, 0, Blocks.STONE.defaultBlockState())).build();
            m.put(new net.minecraft.client.resources.model.ModelResourceLocation(new ResourceLocation("hopelessinfinity", "shape_" + en.getKey()), "inventory"), new ItemWrap(sm, SlopeReg.SHAPED.get().defaultBlockState(), d, tf)); modelsReplaced++;
        }
    }
    /** An item's model: a block model drawn for one state and model data, with block item transforms. */
    static final class ItemWrap extends Base {
        final BakedModel base; final BlockState st; final ModelData data; final ItemTransforms tf;
        ItemWrap(BakedModel base, BlockState st, ModelData data, ItemTransforms tf) { this.base = base; this.st = st; this.data = data; this.tf = tf; }
        @Override public List<BakedQuad> getQuads(BlockState s, Direction side, RandomSource r, ModelData d, RenderType rt) { return base.getQuads(st, side, r, data, rt); }
        @Override public TextureAtlasSprite getParticleIcon() { return base.getParticleIcon(data); }
        @Override public ItemTransforms getTransforms() { return tf; }
    }

    /** A material's faces: sprite and tint index for up, down and the sides. */
    record Look(TextureAtlasSprite up, int upTint, TextureAtlasSprite down, int downTint, TextureAtlasSprite side, int sideTint, TextureAtlasSprite particle) {}
    static final Map<BlockState, Look> LOOKS = new ConcurrentHashMap<>();
    static Look look(BlockState mat) {
        return LOOKS.computeIfAbsent(mat, s -> { materialLookups++;
            BakedModel bm = Minecraft.getInstance().getBlockRenderer().getBlockModel(s); RandomSource r = RandomSource.create(42);
            BakedQuad u = first(bm, s, Direction.UP, r), d = first(bm, s, Direction.DOWN, r), n = first(bm, s, Direction.NORTH, r);
            TextureAtlasSprite p = bm.getParticleIcon(ModelData.EMPTY);
            return new Look(u == null ? p : u.getSprite(), u == null ? -1 : u.getTintIndex(), d == null ? p : d.getSprite(), d == null ? -1 : d.getTintIndex(),
                n == null ? p : n.getSprite(), n == null ? -1 : n.getTintIndex(), p); });
    }
    static BakedQuad first(BakedModel bm, BlockState s, Direction d, RandomSource r) {
        List<BakedQuad> q = bm.getQuads(s, d, r, ModelData.EMPTY, null); if (!q.isEmpty()) return q.get(0);
        for (BakedQuad x : bm.getQuads(s, null, r, ModelData.EMPTY, null)) if (x.getDirection() == d) return x;
        return null;
    }
    static Direction dir(int i) { return i < 0 ? null : Direction.from3DDataValue(i); }
    /** One quad: 4 points {x, y, z, u, v} (0..1), normal, sprite, tint. */
    static BakedQuad quad(double[][] v, double[] n, TextureAtlasSprite sp, int tint) {
        QuadBakingVertexConsumer.Buffered b = new QuadBakingVertexConsumer.Buffered();
        b.setSprite(sp); b.setTintIndex(tint); b.setShade(true); b.setHasAmbientOcclusion(true);
        b.setDirection(Direction.getNearest((float) n[0], (float) n[1], (float) n[2]));
        for (double[] p : v) b.vertex((float) p[0], (float) p[1], (float) p[2]).color(1f, 1f, 1f, 1f).uv(sp.getU(p[3] * 16), sp.getV(p[4] * 16)).uv2(0, 0).normal((float) n[0], (float) n[1], (float) n[2]).endVertex();
        quadsBuilt++; return b.getQuad();
    }

    static abstract class Base implements IDynamicBakedModel {
        @Override public boolean useAmbientOcclusion() { return true; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
        @Override public ItemTransforms getTransforms() { return ItemTransforms.NO_TRANSFORMS; }
        @Override public List<BakedQuad> getQuads(BlockState st, Direction side, RandomSource r) { return getQuads(st, side, r, ModelData.EMPTY, null); }
    }

    /** Terrain slope: corner heights from the state. Quads cached per code, split by the face they fill (for culling). */
    static final class TerrainModel extends Base {
        final BlockState mat; final Map<Integer, List<BakedQuad>[]> cache = new ConcurrentHashMap<>();
        TerrainModel(BlockState mat) { this.mat = mat; }
        @Override public TextureAtlasSprite getParticleIcon() { return look(mat).particle(); }
        @SuppressWarnings("unchecked")
        @Override public List<BakedQuad> getQuads(BlockState st, Direction side, RandomSource r, ModelData data, RenderType rt) {
            if (st == null) return List.of();
            int code = st.getValue(SlopeReg.CORNERS);
            List<BakedQuad>[] byFace = cache.computeIfAbsent(code, c -> {
                List<BakedQuad>[] out = new List[7]; for (int i = 0; i < 7; i++) out[i] = new ArrayList<>();
                Look lk = look(mat); int[] h = SlopeMesh.decode(c, Slopes.L);
                for (SlopeMesh.Face f : SlopeMesh.world(h, Slopes.L, SlopeMesh.autoSplit(h), SlopeMesh.DOWN)) {
                    TextureAtlasSprite sp = f.mat == SlopeMesh.TOP ? lk.up() : f.mat == SlopeMesh.FLOOR ? lk.down() : lk.side();
                    int tint = f.mat == SlopeMesh.TOP ? lk.upTint() : f.mat == SlopeMesh.FLOOR ? lk.downTint() : -1;   // the material's side texture with its top tint would turn grass sides green: no tint on sides
                    out[f.cull < 0 ? 6 : f.cull].add(quad(f.v, f.n, sp, tint));
                }
                return out;
            });
            return byFace[side == null ? 6 : side.get3DDataValue()];
        }
    }

    /** Shaped block: shape, turn and material from the block entity's model data. */
    static final class ShapedModel extends Base {
        final Map<String, List<BakedQuad>[]> cache = new ConcurrentHashMap<>();
        @Override public TextureAtlasSprite getParticleIcon() { return look(Blocks.STONE.defaultBlockState()).particle(); }
        @Override public TextureAtlasSprite getParticleIcon(ModelData data) { SlopeReg.ShapeData d = data.get(SlopeReg.ShapeData.KEY); return look(d == null ? Blocks.STONE.defaultBlockState() : d.material()).particle(); }
        @SuppressWarnings("unchecked")
        @Override public List<BakedQuad> getQuads(BlockState st, Direction side, RandomSource r, ModelData data, RenderType rt) {
            SlopeReg.ShapeData d = data == null ? null : data.get(SlopeReg.ShapeData.KEY);
            int shape = d == null ? 0 : d.shape(), o = d == null ? 0 : d.orient(); BlockState mat = d == null ? Blocks.STONE.defaultBlockState() : d.material();
            List<BakedQuad>[] byFace = cache.computeIfAbsent(shape + ":" + o + ":" + mat, k -> {
                List<BakedQuad>[] out = new List[7]; for (int i = 0; i < 7; i++) out[i] = new ArrayList<>();
                Look lk = look(mat);
                for (ShapeLib.Face f : ShapeLib.mesh(shape, o)) {
                    boolean up = f.n[1] > 0.7, down = f.n[1] < -0.7;
                    TextureAtlasSprite sp = up ? lk.up() : down ? lk.down() : lk.side(); int tint = up ? lk.upTint() : down ? lk.downTint() : lk.sideTint();
                    out[f.cull < 0 ? 6 : f.cull].add(quad(f.v, f.n, sp, tint));
                }
                return out;
            });
            return byFace[side == null ? 6 : side.get3DDataValue()];
        }
    }
}
