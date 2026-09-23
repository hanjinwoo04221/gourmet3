package org.example.hanjinwoo.gourmet2.client.model;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.entity.AppetiteDemonEntity;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * The Crimson Demon model (modelCollection/red_demon). On top of the baked idle/walk animations it
 * copies the caster's head pitch and arm swing onto the demon every frame, so it moves as one with
 * the player.
 */
public class AppetiteDemonGeoModel extends GeoModel<AppetiteDemonEntity> {
    private static final ResourceLocation MODEL = Gourmet2.id("geo/entity/crimson_demon.geo.json");
    private static final ResourceLocation TEXTURE = Gourmet2.id("textures/entity/crimson_demon.png");
    private static final ResourceLocation ANIMATION = Gourmet2.id("animations/entity/crimson_demon.animation.json");

    /** Peak raise of the right arm at the top of a swing, in radians. */
    private static final float SWING_ARM_RAISE = 1.9F;
    private static final float SWING_FOREARM_BEND = 0.9F;

    @Override
    public ResourceLocation getModelResource(AppetiteDemonEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(AppetiteDemonEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(AppetiteDemonEntity animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(AppetiteDemonEntity demon, long instanceId, AnimationState<AppetiteDemonEntity> state) {
        super.setCustomAnimations(demon, instanceId, state);
        // Stage 1 is only the upper body, so the legs are cut off at the waist.
        boolean upperBodyOnly = demon.getStage() < 2;
        getBone("leg_left").ifPresent(leg -> leg.setHidden(upperBodyOnly));
        getBone("leg_right").ifPresent(leg -> leg.setHidden(upperBodyOnly));
        LivingEntity owner = demon.owner();
        if (owner == null) {
            return;
        }
        float partial = state.getPartialTick();

        getBone("head").ifPresent(head -> {
            float pitch = Mth.lerp(partial, owner.xRotO, owner.getXRot());
            head.setRotX(head.getRotX() - pitch * Mth.DEG_TO_RAD);
        });

        float swing = owner.getAttackAnim(partial);
        if (swing > 0.0F) {
            float arc = Mth.sin(swing * Mth.PI);
            // The caster's main hand is their right arm, which is the demon's -X side bone.
            getBone("arm_right").ifPresent(arm -> arm.setRotX(arm.getRotX() + arc * SWING_ARM_RAISE));
            getBone("forearm_right").ifPresent(forearm -> forearm.setRotX(forearm.getRotX() + arc * SWING_FOREARM_BEND));
        }
    }
}
