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
import org.example.hanjinwoo.gourmet2.client.weapon.WeaponFlashType;

/**
 * The three cutlery shapes that flash over a caster's fist or foot: a nail, a four-pronged fork
 * and a straight knife blade. Only one part is drawn per flash (see {@link #render}).
 *
 * <p>Modelled in Blockbench with each shape's tip pointing along -Y from its own local origin
 * (the grip). That is deliberate: {@code EntityModel} rendering always applies a
 * {@code scale(-1, -1, 1)} correction before drawing (see the renderer), which flips this -Y
 * geometry to point along +Y — i.e. further down the limb, past the fist/foot — once it is placed
 * at the hand or foot pivot.
 */
public class WeaponFlashModel<T extends Entity> extends EntityModel<T> {
    private final ModelPart nail;
    private final ModelPart fork;
    private final ModelPart knife;

    public WeaponFlashModel(ModelPart root) {
        super(RenderType::entitySolid);
        this.nail = root.getChild("nail");
        this.fork = root.getChild("fork");
        this.knife = root.getChild("knife");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();

        parts.addOrReplaceChild("nail", CubeListBuilder.create()
                        // head (flat, sits at the grip)
                        .texOffs(0, 0).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
                        // shaft
                        .texOffs(0, 0).addBox(-0.75F, -7.0F, -0.75F, 1.5F, 6.0F, 1.5F, new CubeDeformation(0.0F))
                        // two-stage tapered point
                        .texOffs(0, 0).addBox(-0.5F, -8.5F, -0.5F, 1.0F, 1.5F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 0).addBox(-0.2F, -9.5F, -0.2F, 0.4F, 1.0F, 0.4F, new CubeDeformation(0.0F)),
                PartPose.ZERO);

        parts.addOrReplaceChild("fork", CubeListBuilder.create()
                        // short handle at the grip
                        .texOffs(0, 0).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                        // crossbar the prongs fan out from
                        .texOffs(0, 0).addBox(-4.0F, -3.0F, -1.0F, 8.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                        // four prongs
                        .texOffs(0, 0).addBox(3.0F, -8.0F, -1.0F, 1.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 0).addBox(1.0F, -8.0F, -1.0F, 1.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 0).addBox(-2.0F, -8.0F, -1.0F, 1.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 0).addBox(-4.0F, -8.0F, -1.0F, 1.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);

        parts.addOrReplaceChild("knife", CubeListBuilder.create()
                        // guard at the grip
                        .texOffs(0, 0).addBox(-2.0F, -1.0F, -0.5F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                        // single straight blade
                        .texOffs(0, 0).addBox(-1.5F, -9.0F, -0.3F, 3.0F, 8.0F, 0.6F, new CubeDeformation(0.0F)),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // Static geometry; the layer scales/rotates it for the flash animation.
    }

    /** Renders only the requested shape. */
    public void render(WeaponFlashType type, PoseStack poseStack, VertexConsumer buffer,
                       int packedLight, int packedOverlay, int color) {
        partFor(type).render(poseStack, buffer, packedLight, packedOverlay, color);
    }

    private ModelPart partFor(WeaponFlashType type) {
        return switch (type) {
            case NAIL -> nail;
            case FORK -> fork;
            case KNIFE -> knife;
        };
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        // Unused: the layer always calls render(WeaponFlashType, ...) to draw a single shape.
    }
}
