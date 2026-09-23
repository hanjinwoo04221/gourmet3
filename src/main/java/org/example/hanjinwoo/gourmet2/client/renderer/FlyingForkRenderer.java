package org.example.hanjinwoo.gourmet2.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.entity.FlyingForkEntity;

/**
 * Draws nothing. The fork's visible shape is the Effekseer prong effect bound to this entity in
 * {@code FlyingForkSkill} (see {@code toriko/shapes/fork.efkefc}, or the bundled {@code Laser03}
 * fallback) — a smooth crystal shard reads far better in flight than a boxed Blockbench model, and
 * collision/damage never depended on the model either way.
 *
 * <p>A renderer still has to be registered for every entity type, so this stays as the no-op
 * placeholder rather than removing registration entirely.
 */
public class FlyingForkRenderer extends EntityRenderer<FlyingForkEntity> {
    private static final ResourceLocation TEXTURE = Gourmet2.id("textures/entity/blank.png");

    public FlyingForkRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(FlyingForkEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(FlyingForkEntity entity) {
        return TEXTURE;
    }
}
