package org.example.hanjinwoo.gourmet2.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.example.hanjinwoo.gourmet2.registry.ModEntities;

/**
 * The giant pair of chopsticks of Ichiryu's Chopsticks technique. The skill moves and aims it every tick
 * from the server; the entity itself only carries whether it is hovering open or gripping something.
 */
public class ChopsticksEntity extends VisualEntity {
    public static final int LIFETIME = 400;
    private static final EntityDataAccessor<Boolean> SINGLE =
            SynchedEntityData.defineId(ChopsticksEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> SCALE =
            SynchedEntityData.defineId(ChopsticksEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> GRIPPING =
            SynchedEntityData.defineId(ChopsticksEntity.class, EntityDataSerializers.BOOLEAN);

    public ChopsticksEntity(EntityType<? extends ChopsticksEntity> type, Level level) {
        super(type, level);
    }

    public ChopsticksEntity(Level level) {
        super(ModEntities.CHOPSTICKS.get(), level);
    }

    @Override
    public int lifetime() {
        return LIFETIME;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(GRIPPING, false);
        builder.define(SCALE, 1.0F);
        builder.define(SINGLE, false);
    }

    /** The size setting the sticks were summoned at: scales their length and thickness. */
    public float scale() {
        return entityData.get(SCALE);
    }

    public void setScale(float scale) {
        entityData.set(SCALE, Math.max(0.25F, scale));
    }

    /** One thick stick instead of a pair (Single Chopstick). */
    public boolean isSingle() {
        return entityData.get(SINGLE);
    }

    public void setSingle(boolean single) {
        entityData.set(SINGLE, single);
    }

    public boolean isGripping() {
        return entityData.get(GRIPPING);
    }

    public void setGripping(boolean gripping) {
        entityData.set(GRIPPING, gripping);
    }
}
