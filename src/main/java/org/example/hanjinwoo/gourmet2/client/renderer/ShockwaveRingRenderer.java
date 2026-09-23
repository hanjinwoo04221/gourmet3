package org.example.hanjinwoo.gourmet2.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.client.model.ModModelLayers;
import org.example.hanjinwoo.gourmet2.client.model.ShockwaveRingModel;
import org.example.hanjinwoo.gourmet2.entity.ShockwaveRingEntity;

/**
 * Expands the modelled ring outwards from the impact point and fades it out. The model is built at
 * a fixed radius, so all of the motion is in the pose scale here.
 */
public class ShockwaveRingRenderer extends EntityRenderer<ShockwaveRingEntity> {
    private static final ResourceLocation TEXTURE = Gourmet2.id("textures/entity/blank.png");
    /** Mid-radius of the modelled ring, in blocks: 10.5 model units / 16. */
    private static final float MODEL_RADIUS = 10.5F / 16.0F;
    private static final float START_RADIUS = 0.4F;
    private static final float FADE_OUT = 0.65F;

    private final ShockwaveRingModel<ShockwaveRingEntity> model;

    public ShockwaveRingRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new ShockwaveRingModel<>(context.bakeLayer(ModModelLayers.SHOCKWAVE_RING));
    }

    @Override
    public void render(ShockwaveRingEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        float age = entity.age(partialTick);
        float alpha = VisualEasing.envelope(age, 0.0F, FADE_OUT);
        if (alpha <= 0.0F) {
            return;
        }
        float radius = Mth.lerp(VisualEasing.easeOut(age), START_RADIUS, ShockwaveRingEntity.MAX_RADIUS);
        float horizontal = radius / MODEL_RADIUS;

        poseStack.pushPose();
        // Widen in the horizontal plane only, so the ring stays a flat sheet as it grows.
        poseStack.scale(-horizontal, -1.0F, horizontal);

        VertexConsumer consumer = buffers.getBuffer(model.renderType(TEXTURE));
        int color = FastColor.ARGB32.color(Mth.clamp((int) (alpha * 220.0F), 0, 255), 255, 246, 214);
        model.renderToBuffer(poseStack, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ShockwaveRingEntity entity) {
        return TEXTURE;
    }
}
