package com.hopeless.infinity.town;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SilverfishRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Silverfish;

/** 0.30.0: the rat: the silverfish's body with the rat's own fur texture, a little larger. */
public class RatRenderer extends SilverfishRenderer {
    static final ResourceLocation TEX = new ResourceLocation("hopelessinfinity", "textures/entity/rat.png");
    public RatRenderer(EntityRendererProvider.Context c) { super(c); }
    @Override public ResourceLocation getTextureLocation(Silverfish e) { return TEX; }
    @Override protected void scale(Silverfish e, com.mojang.blaze3d.vertex.PoseStack ps, float pt) { ps.scale(1.25f, 1.25f, 1.25f); }
}
