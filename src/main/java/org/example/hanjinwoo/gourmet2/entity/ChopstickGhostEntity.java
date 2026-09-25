package org.example.hanjinwoo.gourmet2.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.registry.ModEntities;

/**
 * An afterimage of a chopstick thrust: a fading, glowing pair of sticks left hanging where a jab passed. Purely
 * visual; the technique that spawns it does the hurting.
 */
public class ChopstickGhostEntity extends VisualEntity {
    private static final EntityDataAccessor<Integer> LIFETIME =
            SynchedEntityData.defineId(ChopstickGhostEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SCALE =
            SynchedEntityData.defineId(ChopstickGhostEntity.class, EntityDataSerializers.FLOAT);

    public ChopstickGhostEntity(EntityType<? extends ChopstickGhostEntity> type, Level level) {
        super(type, level);
    }

    public ChopstickGhostEntity(Level level) {
        super(ModEntities.CHOPSTICK_GHOST.get(), level);
    }

    /** Leaves an afterimage at {@code at}, pointing where the caster looks (Minecraft yaw / pitch, degrees). */
    public static void spawn(ServerLevel level, Vec3 at, float lookYaw, float lookPitch, float scale, int lifetime) {
        ChopstickGhostEntity ghost = new ChopstickGhostEntity(level);
        ghost.entityData.set(LIFETIME, Math.max(2, lifetime));
        ghost.entityData.set(SCALE, Math.max(0.25F, scale));
        ghost.moveTo(at.x, at.y, at.z, lookYaw, lookPitch);
        level.addFreshEntity(ghost);
    }

    /** As above, pointing along {@code direction} (which may not be where the caster is looking). */
    public static void spawn(ServerLevel level, Vec3 at, Vec3 direction, float scale, int lifetime) {
        Vec3 dir = direction.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, dir.y))));
        spawn(level, at, yaw, pitch, scale, lifetime);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LIFETIME, 8);
        builder.define(SCALE, 1.0F);
    }

    @Override
    public int lifetime() {
        return entityData.get(LIFETIME);
    }

    public float scale() {
        return entityData.get(SCALE);
    }
}
