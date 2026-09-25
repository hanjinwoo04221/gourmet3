package org.example.hanjinwoo.gourmet2.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.example.hanjinwoo.gourmet2.registry.ModEntities;

/** The visible shell of a Minority World zone. It lasts until the skill discards it. */
public class MinorityWorldEntity extends VisualEntity {
    private static final EntityDataAccessor<Float> RADIUS =
            SynchedEntityData.defineId(MinorityWorldEntity.class, EntityDataSerializers.FLOAT);

    public MinorityWorldEntity(EntityType<? extends MinorityWorldEntity> type, Level level) {
        super(type, level);
    }

    public MinorityWorldEntity(Level level) {
        super(ModEntities.MINORITY_WORLD.get(), level);
    }

    @Override
    public int lifetime() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(RADIUS, 10.0F);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public void setRadius(float radius) {
        entityData.set(RADIUS, radius);
    }
}
