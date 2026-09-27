package org.example.hanjinwoo.gourmet2.client.model;

import net.minecraft.resources.ResourceLocation;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.entity.LizardmanEntity;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

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

    /** The head follows where the entity is looking on top of whatever the clip does, so it stays on its target. */
    @Override
    public void setCustomAnimations(LizardmanEntity animatable, long instanceId, AnimationState<LizardmanEntity> state) {
        super.setCustomAnimations(animatable, instanceId, state);
        var head = getAnimationProcessor().getBone("Head");
        EntityModelData look = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head != null && look != null) {
            head.setRotX(head.getRotX() + look.headPitch() * Mth.DEG_TO_RAD);
            head.setRotY(head.getRotY() + look.netHeadYaw() * Mth.DEG_TO_RAD);
        }
    }

    @Override
    public ResourceLocation getAnimationResource(LizardmanEntity animatable) {
        return ANIMATION;
    }
}
