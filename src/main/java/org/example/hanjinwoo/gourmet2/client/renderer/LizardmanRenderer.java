package org.example.hanjinwoo.gourmet2.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.example.hanjinwoo.gourmet2.client.model.LizardmanGeoModel;
import org.example.hanjinwoo.gourmet2.entity.LizardmanEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class LizardmanRenderer extends GeoEntityRenderer<LizardmanEntity> {
    private static final float SCALE = 1.15F;

    public LizardmanRenderer(EntityRendererProvider.Context context) {
        super(context, new LizardmanGeoModel());
        this.shadowRadius = 0.7F;
        // The model is 2.0 blocks tall; the hitbox is 2.3 (see ModEntities.LIZARDMAN).
        withScale(SCALE);
    }
}
