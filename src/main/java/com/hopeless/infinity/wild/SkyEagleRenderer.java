package com.hopeless.infinity.wild;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ParrotModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** 0.28.0: the sky eagle drawn with the parrot's model, 1.8 times larger, in its own brown-and-white plumage (texture made for the mod). */
public class SkyEagleRenderer extends MobRenderer<net.minecraft.world.entity.animal.Parrot, ParrotModel> {
    static final ResourceLocation TEX = new ResourceLocation("hopelessinfinity", "textures/entity/sky_eagle.png");
    public SkyEagleRenderer(EntityRendererProvider.Context c) { super(c, new ParrotModel(c.bakeLayer(ModelLayers.PARROT)), 0.6f); }
    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.animal.Parrot e) { return TEX; }
    @Override protected void scale(net.minecraft.world.entity.animal.Parrot e, PoseStack p, float pt) { p.scale(1.8f, 1.8f, 1.8f); }
    @Override protected float getBob(net.minecraft.world.entity.animal.Parrot e, float pt) { float f = Mth.lerp(pt, e.oFlap, e.flap), g = Mth.lerp(pt, e.oFlapSpeed, e.flapSpeed); return (Mth.sin(f) + 1.0F) * g; }
}
