package org.example.hanjinwoo.gourmet2.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.registry.ModDamageTypes;
import org.example.hanjinwoo.gourmet2.registry.ModEntities;
import org.example.hanjinwoo.gourmet2.skill.Hurt;

/**
 * One chopstick of Ichiryu's Fist Chopstick technique. It starts out held in the caster's fist (the skill
 * keeps it at their hand) and, once thrown, flies like the other projectiles: through up to two victims, and
 * through terrain that its impact is heavy enough to break.
 */
public class ThrownChopstickEntity extends SkillProjectile {
    public static final int FLIGHT_TICKS = 30;
    private static final EntityDataAccessor<Boolean> HELD =
            SynchedEntityData.defineId(ThrownChopstickEntity.class, EntityDataSerializers.BOOLEAN);
    /**
     * The launch velocity, sent as ordinary entity data. The game's own spawn and motion packets clamp a velocity to
     * 3.9 blocks per tick, and these are thrown faster than that: a client that trusted those packets would fly the
     * stick slower than the server does, so it would be drawn well short of where it really is (and vanish where the
     * server ends its flight) while the terrain breaks along the real path.
     */
    private static final EntityDataAccessor<org.joml.Vector3f> VELOCITY =
            SynchedEntityData.defineId(ThrownChopstickEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> VISUAL_SCALE =
            SynchedEntityData.defineId(ThrownChopstickEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> THICKNESS =
            SynchedEntityData.defineId(ThrownChopstickEntity.class, EntityDataSerializers.FLOAT);
    private static final double ACCELERATION_PER_TICK = 1.02;

    private float damage = 8.0F;

    public ThrownChopstickEntity(EntityType<? extends ThrownChopstickEntity> type, Level level) {
        super(type, level);
    }

    public ThrownChopstickEntity(Level level) {
        super(ModEntities.THROWN_CHOPSTICK.get(), level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HELD, false);
        builder.define(THICKNESS, 1.0F);
        builder.define(VISUAL_SCALE, 0.0F);
        builder.define(VELOCITY, new org.joml.Vector3f());
    }

    public boolean isHeld() {
        return entityData.get(HELD);
    }

    public void setHeld(boolean held) {
        entityData.set(HELD, held);
    }

    /** How thick the stick is drawn relative to its length; the hitbox is unaffected. */
    public float thickness() {
        return entityData.get(THICKNESS);
    }

    public void setThickness(float thickness) {
        entityData.set(THICKNESS, Math.max(0.5F, thickness));
    }

    /**
     * These fly a long way and are drawn large, so they are drawn out to a much greater distance than the default
     * for an entity with a hitbox this small, which would otherwise make them vanish partway through the flight.
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 384.0 * 384.0;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (VELOCITY.equals(key) && level().isClientSide()) {
            org.joml.Vector3f v = entityData.get(VELOCITY);
            if (v.lengthSquared() > 1.0E-6F) {
                setDeltaMovement(v.x(), v.y(), v.z());
            }
        }
    }

    /** The server's motion updates are clamped to 3.9 blocks per tick, so the synced launch velocity stands in for them. */
    @Override
    public void lerpMotion(double x, double y, double z) {
        if (entityData.get(VELOCITY).lengthSquared() <= 1.0E-6F) {
            super.lerpMotion(x, y, z);
        }
    }

    /**
     * How big the stick is drawn, in units of the 1.8-block stick. By default that follows the hitbox; a technique that
     * wants a stick drawn larger than the box it hits with sets it here.
     */
    public float visualScale() {
        float set = entityData.get(VISUAL_SCALE);
        return set > 0.0F ? set : Math.max(0.2F, getBbWidth() / 0.3F);
    }

    public void setVisualScale(float scale) {
        entityData.set(VISUAL_SCALE, scale);
    }

    /** What it breaks is what its hitbox covers, no wider. */
    @Override
    protected double breakSweepMargin() {
        return 0.0;
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    /** Sets the stick flying along {@code velocity} and lets go of the fist. */
    public void launch(Vec3 velocity) {
        setHeld(false);
        life = 0;
        setDeltaMovement(velocity);
        entityData.set(VELOCITY, velocity.toVector3f());
        setYRot((float) Math.toDegrees(Math.atan2(velocity.x, velocity.z)));
        setXRot((float) Math.toDegrees(Math.atan2(velocity.y, velocity.horizontalDistance())));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    /** Keeps a held stick at the hand, pointing where the caster looks (projectile rotation convention). */
    public void holdAt(Vec3 at, float lookYaw, float lookPitch) {
        moveTo(at.x, at.y, at.z, -lookYaw, -lookPitch);
    }

    @Override
    public void tick() {
        if (isHeld()) {
            if (!level().isClientSide() && !(getOwner() instanceof Player owner && owner.isAlive())) {
                discard();
            }
            return;
        }
        super.tick();
    }

    @Override
    protected float damage() {
        return damage;
    }

    @Override
    protected UpheavalEntity.Growth upheavalGrowth() {
        return UpheavalEntity.Growth.GENTLE;
    }

    @Override
    protected double accelerationPerTick() {
        return ACCELERATION_PER_TICK;
    }

    @Override
    protected int maxLifeTicks() {
        return FLIGHT_TICKS;
    }

    @Override
    protected int maxPierce() {
        return 2;
    }

    @Override
    protected void hitTarget(LivingEntity target) {
        target.invulnerableTime = 0;
        boolean landed = target.hurt(
                ModDamageTypes.source(level(), ModDamageTypes.CUTTING, this, getOwner()), damage);
        if (!landed) {
            return;
        }
        level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.6F, 1.8F);
    }

    @Override
    protected void dissipate(Vec3 at, boolean hitBlock) {
        if (hitBlock) {
            level().playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 0.5F, 1.6F);
        }
    }

    @Override
    protected boolean tryBreakBlock(BlockHitResult hit) {
        Vec3 blow = getDeltaMovement();
        return casterOrNull() instanceof ServerPlayer caster
                && Hurt.breakThrough(caster, level(), hit.getBlockPos(), blow, damage, impactArea(blow));
    }
}
