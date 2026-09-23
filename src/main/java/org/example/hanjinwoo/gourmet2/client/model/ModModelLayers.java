package org.example.hanjinwoo.gourmet2.client.model;

import net.minecraft.client.model.geom.ModelLayerLocation;
import org.example.hanjinwoo.gourmet2.Gourmet2;

public final class ModModelLayers {
    public static final ModelLayerLocation SHOCKWAVE_RING = layer("shockwave_ring");
    public static final ModelLayerLocation WEAPON_FLASH = layer("weapon_flash");

    private ModModelLayers() {}

    private static ModelLayerLocation layer(String path) {
        return new ModelLayerLocation(Gourmet2.id(path), "main");
    }
}
