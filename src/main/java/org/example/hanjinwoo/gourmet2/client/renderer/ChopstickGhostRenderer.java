package org.example.hanjinwoo.gourmet2.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.example.hanjinwoo.gourmet2.entity.ChopstickGhostEntity;

/** Draws the afterimage: a pair of glowing yellow sticks that fade out over the entity's short life. */
public class ChopstickGhostRenderer extends EntityRenderer<ChopstickGhostEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final float LENGTH = 2.6F;
    private static final float GAP = 0.32F;

    public ChopstickGhostRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ChopstickGhostEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        float age = entity.age(partialTick);
        float fade = (1.0F - age) * (1.0F - age);
        if (fade <= 0.01F) {
            return;
        }
        float scale = entity.scale();
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot())));
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
        poseStack.scale(scale, scale, scale);

        // The core and the halo share one translucent buffer, so it is fetched once.
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        int coreAlpha = Mth.clamp((int) (fade * 200.0F), 0, 255);
        int haloAlpha = Mth.clamp((int) (fade * 90.0F), 0, 255);
        int core = (coreAlpha << 24) | 0xFFE680;
        int halo = (haloAlpha << 24) | 0xFFD84A;
        for (int side = -1; side <= 1; side += 2) {
            float x = side * GAP / 2.0F;
            ChopsticksRenderer.box(vc, pose, x - 0.05F, -0.05F, -LENGTH / 2.0F, x + 0.05F, 0.05F, LENGTH / 2.0F, core);
            ChopsticksRenderer.box(vc, pose, x - 0.16F, -0.16F, -LENGTH / 2.0F - 0.1F, x + 0.16F, 0.16F,
                    LENGTH / 2.0F + 0.1F, halo);
        }
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public boolean shouldRender(ChopstickGhostEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(ChopstickGhostEntity entity) {
        return TEXTURE;
    }
}
