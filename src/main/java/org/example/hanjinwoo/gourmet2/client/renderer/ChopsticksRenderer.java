package org.example.hanjinwoo.gourmet2.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.example.hanjinwoo.gourmet2.entity.ChopsticksEntity;

/** Draws the two giant sticks as plain boxes, pointing along the entity's facing and pinching shut when gripping. */
public class ChopsticksRenderer extends EntityRenderer<ChopsticksEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final float LENGTH = 7.0F;
    private static final int WOOD = 0xFFFFE27A;
    private static final int WOOD_DARK = 0xFFF2C94C;
    private static final int GLOW = 0x55FFD84A;

    public ChopsticksRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ChopsticksEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        float t = entity.tickCount + partialTick;
        float grow = Mth.clamp(t / 5.0F, 0.0F, 1.0F);
        float gap = entity.isGripping() ? Mth.lerp(VisualEasing.easeOut(Mth.clamp(t / 8.0F, 0.0F, 1.0F)), 1.0F, 0.5F) : 0.9F;
        float scale = entity.scale();
        float length = LENGTH * grow * scale;

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot())));
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));

        // One buffer at a time: asking the buffer source for a second one closes the first.
        PoseStack.Pose pose = poseStack.last();
        float w = scale * (entity.isSingle() ? 2.2F : 1.0F);
        int[] sides = entity.isSingle() ? new int[] {0} : new int[] {-1, 1};
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (int side : sides) {
            float x = side * gap * scale / 2.0F;
            // The back half is thick, the front half tapers to the tip, with the tip at +z.
            box(vc, pose, x - 0.09F * w, -0.09F * w, -length / 2.0F, x + 0.09F * w, 0.09F * w, 0.0F, WOOD_DARK);
            box(vc, pose, x - 0.06F * w, -0.06F * w, 0.0F, x + 0.06F * w, 0.06F * w, length / 2.0F, WOOD);
        }
        // A soft yellow glow around each stick.
        VertexConsumer halo = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        for (int side : sides) {
            float x = side * gap * scale / 2.0F;
            box(halo, pose, x - 0.2F * w, -0.2F * w, -length / 2.0F - 0.1F, x + 0.2F * w, 0.2F * w, length / 2.0F + 0.1F, GLOW);
        }
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    static void box(VertexConsumer vc, PoseStack.Pose pose,
                            float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        quad(vc, pose, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1, color);
        quad(vc, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, color);
        quad(vc, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, color);
        quad(vc, pose, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0, color);
        quad(vc, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, color);
        quad(vc, pose, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, color);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float nx, float ny, float nz, int color) {
        vertex(vc, pose, ax, ay, az, nx, ny, nz, color);
        vertex(vc, pose, bx, by, bz, nx, ny, nz, color);
        vertex(vc, pose, cx, cy, cz, nx, ny, nz, color);
        vertex(vc, pose, dx, dy, dz, nx, ny, nz, color);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z,
                               float nx, float ny, float nz, int color) {
        vc.addVertex(pose, x, y, z).setColor(color).setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, nx, ny, nz);
    }

    /** The sticks are far longer than the entity's small hitbox, so its box leaving the view must not cull them. */
    @Override
    public boolean shouldRender(ChopsticksEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(ChopsticksEntity entity) {
        return TEXTURE;
    }
}
