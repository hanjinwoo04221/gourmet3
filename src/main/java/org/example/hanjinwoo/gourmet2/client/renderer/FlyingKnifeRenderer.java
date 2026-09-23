package org.example.hanjinwoo.gourmet2.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.entity.FlyingKnifeEntity;

/**
 * Draws nothing. The wave's visible shape is the Effekseer knife effect bound to this entity in
 * {@code FlyingKnifeSkill} (see {@code toriko/shapes/knife.efkefc}, or the bundled
 * {@code Simple_Ribbon_Parent} fallback) — a smooth crystal blade reads far better in flight than a
 * boxed Blockbench model, and collision/damage never depended on the model either way.
 *
 * <p>A renderer still has to be registered for every entity type, so this stays as the no-op
 * placeholder rather than removing registration entirely.
 */
public class FlyingKnifeRenderer extends EntityRenderer<FlyingKnifeEntity> {
    private static final ResourceLocation TEXTURE = Gourmet2.id("textures/entity/blank.png");

    public FlyingKnifeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(FlyingKnifeEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(FlyingKnifeEntity entity) {
        return TEXTURE;
    }
}
