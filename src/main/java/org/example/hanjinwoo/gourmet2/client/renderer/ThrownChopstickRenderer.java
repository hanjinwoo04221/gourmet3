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
import org.example.hanjinwoo.gourmet2.entity.ThrownChopstickEntity;

/** A single chopstick, drawn along the direction it is flying (or held) and scaled with the size setting. */
public class ThrownChopstickRenderer extends EntityRenderer<ThrownChopstickEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final float LENGTH = 1.8F;
    private static final int WOOD = 0xFFFFE27A;
    private static final int WOOD_DARK = 0xFFF2C94C;
    private static final int GLOW = 0x55FFD84A;

    public ThrownChopstickRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ThrownChopstickEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        float scale = Math.max(0.2F, entity.getBbWidth() / 0.3F);
        float t = entity.thickness();
        poseStack.pushPose();
        poseStack.translate(0.0F, entity.getBbHeight() / 2.0F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot())));
        poseStack.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
        poseStack.scale(scale, scale, scale);

        PoseStack.Pose pose = poseStack.last();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        ChopsticksRenderer.box(vc, pose, -0.03F * t, -0.03F * t, -LENGTH / 2.0F, 0.03F * t, 0.03F * t, 0.0F, WOOD_DARK);
        ChopsticksRenderer.box(vc, pose, -0.02F * t, -0.02F * t, 0.0F, 0.02F * t, 0.02F * t, LENGTH / 2.0F, WOOD);
        // Fetched only after the solid part is written: a second buffer request closes the first.
        VertexConsumer halo = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        ChopsticksRenderer.box(halo, pose, -0.09F * t, -0.09F * t, -LENGTH / 2.0F - 0.05F, 0.09F * t, 0.09F * t,
                LENGTH / 2.0F + 0.05F, GLOW);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    /** The sticks are far longer than the entity's small hitbox, so its box leaving the view must not cull them. */
    @Override
    public boolean shouldRender(ThrownChopstickEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(ThrownChopstickEntity entity) {
        return TEXTURE;
    }
}
