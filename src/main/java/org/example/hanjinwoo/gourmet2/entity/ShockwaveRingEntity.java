package org.example.hanjinwoo.gourmet2.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.example.hanjinwoo.gourmet2.registry.ModEntities;

/**
 * The flat expanding ring laid over the Effekseer burst of Leg Knife and the final hit of the
 * 13-hit Nail Punch. Gives the impact a solid, readable silhouette that particles alone don't.
 */
public class ShockwaveRingEntity extends VisualEntity {
    public static final int LIFETIME = 14;
    /** How wide the ring grows by the end of its life, in blocks. */
    public static final float MAX_RADIUS = 3.5F;

    public ShockwaveRingEntity(EntityType<? extends ShockwaveRingEntity> type, Level level) {
        super(type, level);
    }

    public ShockwaveRingEntity(Level level) {
        super(ModEntities.SHOCKWAVE_RING.get(), level);
    }

    @Override
    public int lifetime() {
        return LIFETIME;
    }
}
