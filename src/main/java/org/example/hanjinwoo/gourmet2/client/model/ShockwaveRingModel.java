package org.example.hanjinwoo.gourmet2.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;

/**
 * A flat ring, built as twelve identical spokes rotated around the Y axis. The renderer grows and
 * fades it, so the geometry is modelled at a fixed one-block radius.
 */
public class ShockwaveRingModel<T extends Entity> extends EntityModel<T> {
    private static final int SEGMENTS = 12;

    private final ModelPart ring;

    public ShockwaveRingModel(ModelPart root) {
        super(RenderType::entityTranslucent);
        this.ring = root.getChild("ring");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();

        // Segment 0 sits on the bone itself; the other eleven are rotated child bones.
        PartDefinition ring = parts.addOrReplaceChild("ring", segment(), PartPose.ZERO);
        for (int i = 1; i < SEGMENTS; i++) {
            float angle = (float) (2.0 * Math.PI * i / SEGMENTS);
            ring.addOrReplaceChild("segment_" + i, segment(),
                    PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, angle, 0.0F));
        }

        return LayerDefinition.create(mesh, 16, 16);
    }

    /** One arc chord, slightly wider than the gap so the twelve of them close into a ring. */
    private static CubeListBuilder segment() {
        return CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-3.0F, -1.0F, -12.0F, 6.0F, 1.0F, 3.0F, new CubeDeformation(0.0F));
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // The renderer animates scale and alpha.
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        ring.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
