package org.example.hanjinwoo.gourmet2.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Base for the short-lived, purely decorative entities that carry the hand-modelled parts of a
 * skill's visual (the Appetite Demon, the shockwave ring). They collide with nothing, save
 * nothing, and remove themselves after a fixed number of ticks.
 *
 * <p>Animation is driven off {@link #tickCount}, which starts at zero on the client the moment the
 * spawn packet arrives — accurate enough for effects that last a couple of seconds, and it keeps
 * these entities off the synched-data budget entirely.
 */
public abstract class VisualEntity extends Entity {
    protected VisualEntity(EntityType<? extends VisualEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** How long the visual lives, in ticks. */
    public abstract int lifetime();

    /** 0.0 on spawn, 1.0 on the last tick. Safe to call with a partial tick on the client. */
    public float age(float partialTick) {
        return Math.min(1.0F, (tickCount + partialTick) / Math.max(1, lifetime()));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // Nothing to sync: position, rotation and tick count are enough.
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount > lifetime()) {
            discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }
}
