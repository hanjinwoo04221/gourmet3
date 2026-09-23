package org.example.hanjinwoo.gourmet2.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.client.model.ModModelLayers;
import org.example.hanjinwoo.gourmet2.client.model.WeaponFlashModel;
import org.example.hanjinwoo.gourmet2.client.weapon.ClientWeaponFlashes;
import org.example.hanjinwoo.gourmet2.client.weapon.WeaponFlashLimb;

/**
 * Draws a cutlery shape over a player's fist or foot for the brief moment a skill casts — the
 * "hand becomes a nail/fork/knife" flash. Attached to {@code PlayerRenderer} for both player skins
 * (see {@code ClientSetup}), so it inherits the model's current swing/rotation for free.
 */
public class WeaponFlashLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURE = Gourmet2.id("textures/entity/blank.png");
    /** Base size of the flash, applied on top of its grow/shrink envelope. */
    private static final float BASE_SCALE = 1.4F;
    /** How far down the arm's local Y axis the fist sits (arm box runs from y=-2 to y=10). */
    private static final float ARM_OFFSET = 10.0F / 16.0F;
    /** How far down the leg's local Y axis the foot sits (leg box runs from y=0 to y=12). */
    private static final float LEG_OFFSET = 11.0F / 16.0F;

    private final WeaponFlashModel<AbstractClientPlayer> model;

    public WeaponFlashLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                            EntityRendererProvider.Context context) {
        super(parent);
        this.model = new WeaponFlashModel<>(context.bakeLayer(ModModelLayers.WEAPON_FLASH));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (player.isInvisible()) {
            return;
        }
        ClientWeaponFlashes.Flash flash = ClientWeaponFlashes.get(player.getId());
        if (flash == null) {
            return;
        }
        float scale = BASE_SCALE * ClientWeaponFlashes.scale(flash);
        if (scale <= 0.0F) {
            return;
        }

        poseStack.pushPose();
        if (flash.limb == WeaponFlashLimb.LEG) {
            getParentModel().rightLeg.translateAndRotate(poseStack);
            poseStack.translate(0.0F, LEG_OFFSET, 0.0F);
        } else {
            getParentModel().translateToHand(HumanoidArm.RIGHT, poseStack);
            poseStack.translate(0.0F, ARM_OFFSET, 0.0F);
        }
        // Combines the animated grow/shrink envelope with the standard Blockbench axis correction
        // that every EntityModel needs (see WeaponFlashModel's class doc for why -Y becomes +Y here).
        poseStack.scale(-scale, -scale, scale);

        VertexConsumer consumer = buffers.getBuffer(model.renderType(TEXTURE));
        model.render(flash.type, poseStack, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, flash.type.tint());
        poseStack.popPose();
    }
}
