package org.example.hanjinwoo.gourmet2.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.example.hanjinwoo.gourmet2.client.model.LizardmanGeoModel;
import org.example.hanjinwoo.gourmet2.entity.LizardmanEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class LizardmanRenderer extends GeoEntityRenderer<LizardmanEntity> {
    public LizardmanRenderer(EntityRendererProvider.Context context) {
        super(context, new LizardmanGeoModel());
        this.shadowRadius = 0.6F;
    }
}
