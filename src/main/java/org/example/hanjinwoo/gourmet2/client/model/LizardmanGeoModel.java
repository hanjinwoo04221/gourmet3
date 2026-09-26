package org.example.hanjinwoo.gourmet2.client.model;

import net.minecraft.resources.ResourceLocation;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.entity.LizardmanEntity;
import software.bernie.geckolib.model.GeoModel;

/** The Lizardman model (modelCollection/red_nitro1): idle/walk plus the claw and tail-slam attack clips. */
public class LizardmanGeoModel extends GeoModel<LizardmanEntity> {
    private static final ResourceLocation MODEL = Gourmet2.id("geo/entity/lizardman.geo.json");
    private static final ResourceLocation TEXTURE = Gourmet2.id("textures/entity/lizardman.png");
    private static final ResourceLocation ANIMATION = Gourmet2.id("animations/entity/lizardman.animation.json");

    @Override
    public ResourceLocation getModelResource(LizardmanEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(LizardmanEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(LizardmanEntity animatable) {
        return ANIMATION;
    }
}
