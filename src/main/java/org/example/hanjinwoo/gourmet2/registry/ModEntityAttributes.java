package org.example.hanjinwoo.gourmet2.registry;

import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.example.hanjinwoo.gourmet2.entity.LizardmanEntity;

/** Default attribute suppliers for this mod's living entities; fired on the mod bus before common setup. */
public final class ModEntityAttributes {
    private ModEntityAttributes() {}

    public static void register(EntityAttributeCreationEvent event) {
        event.put(ModEntities.LIZARDMAN.get(), LizardmanEntity.createAttributes().build());
    }
}
