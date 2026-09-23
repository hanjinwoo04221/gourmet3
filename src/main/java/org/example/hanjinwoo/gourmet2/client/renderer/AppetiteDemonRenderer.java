package org.example.hanjinwoo.gourmet2.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import org.example.hanjinwoo.gourmet2.client.model.AppetiteDemonGeoModel;
import org.example.hanjinwoo.gourmet2.entity.AppetiteDemonEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Draws the Crimson Demon: an oversized upper body in stage 1, the caster's whole body in stage 2. */
public class AppetiteDemonRenderer extends GeoEntityRenderer<AppetiteDemonEntity> {
    private static final float STAGE_ONE_SCALE = 1.4F;
    private static final float STAGE_TWO_SCALE = 1.0F;

    public AppetiteDemonRenderer(EntityRendererProvider.Context context) {
        super(context, new AppetiteDemonGeoModel());
    }

    @Override
    public void render(AppetiteDemonEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        boolean transformed = entity.getStage() >= 2;
        // In first person the camera sits inside the transformed body, so don't draw it around the eyes.
        Minecraft minecraft = Minecraft.getInstance();
        if (transformed && entity.owner() == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        withScale(transformed ? STAGE_TWO_SCALE : STAGE_ONE_SCALE);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /** GeckoLib only turns living entities to face their body yaw; hand it ours so the demon turns too. */
    @Override
    protected void applyRotations(AppetiteDemonEntity entity, PoseStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTick, float nativeScale) {
        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        super.applyRotations(entity, poseStack, ageInTicks, yaw, partialTick, nativeScale);
    }
}
