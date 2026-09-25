package org.example.hanjinwoo.gourmet2.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.example.hanjinwoo.gourmet2.entity.MinorityWorldEntity;

/** Draws the zone as a faint, slowly breathing violet sphere. */
public class MinorityWorldRenderer extends EntityRenderer<MinorityWorldEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final int STACKS = 14;
    private static final int SLICES = 28;

    public MinorityWorldRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(MinorityWorldEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        float radius = entity.radius();
        float t = entity.tickCount + partialTick;
        int alpha = 26 + (int) (10.0F * Mth.sin(t * 0.08F));
        int color = (alpha << 24) | 0x8A5CFF;

        poseStack.pushPose();
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        for (int i = 0; i < STACKS; i++) {
            float phi0 = (float) Math.PI * i / STACKS - (float) Math.PI / 2.0F;
            float phi1 = (float) Math.PI * (i + 1) / STACKS - (float) Math.PI / 2.0F;
            for (int j = 0; j < SLICES; j++) {
                float th0 = (float) (2.0 * Math.PI) * j / SLICES;
                float th1 = (float) (2.0 * Math.PI) * (j + 1) / SLICES;
                vertex(vc, pose, radius, phi0, th0, color);
                vertex(vc, pose, radius, phi0, th1, color);
                vertex(vc, pose, radius, phi1, th1, color);
                vertex(vc, pose, radius, phi1, th0, color);
            }
        }
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float radius, float phi, float theta, int color) {
        float cp = Mth.cos(phi);
        float x = cp * Mth.cos(theta);
        float y = Mth.sin(phi);
        float z = cp * Mth.sin(theta);
        vc.addVertex(pose, x * radius, y * radius, z * radius).setColor(color).setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, x, y, z);
    }

    @Override
    public boolean shouldRender(MinorityWorldEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(MinorityWorldEntity entity) {
        return TEXTURE;
    }
}
