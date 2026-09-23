package org.example.hanjinwoo.gourmet2.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.example.hanjinwoo.gourmet2.client.fx.DemonAuraFx;
import org.example.hanjinwoo.gourmet2.registry.ModEntities;
import org.jetbrains.annotations.Nullable;

/**
 * Invisible carrier for the Ki Release aura: rides its owner, and on every client that can see it
 * plays the cell-coloured aura effect scaled by the output level. Removed when the toggle ends.
 */
public class KiAuraEntity extends VisualEntity {
    private static final EntityDataAccessor<Integer> OWNER_ID =
            SynchedEntityData.defineId(KiAuraEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OUTPUT =
            SynchedEntityData.defineId(KiAuraEntity.class, EntityDataSerializers.INT);

    public KiAuraEntity(EntityType<? extends KiAuraEntity> type, Level level) {
        super(type, level);
    }

    public KiAuraEntity(Level level) {
        super(ModEntities.KI_AURA.get(), level);
    }

    public void setOwner(Entity owner) {
        entityData.set(OWNER_ID, owner.getId());
        setPos(owner.position());
    }

    public @Nullable LivingEntity owner() {
        return level().getEntity(entityData.get(OWNER_ID)) instanceof LivingEntity living ? living : null;
    }

    public int output() {
        return entityData.get(OUTPUT);
    }

    public void setOutput(int output) {
        entityData.set(OUTPUT, output);
    }

    @Override
    public int lifetime() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_ID, -1);
        builder.define(OUTPUT, 1);
    }

    @Override
    public void tick() {
        super.tick();
        LivingEntity owner = owner();
        if (owner == null) {
            if (!level().isClientSide() && tickCount > 20) {
                discard();
            }
            return;
        }
        if (!level().isClientSide() && (!owner.isAlive() || owner.level() != level())) {
            discard();
            return;
        }
        setOldPosAndRot();
        setPos(owner.position());
        if (level().isClientSide()) {
            // Stronger output makes a bigger aura.
            DemonAuraFx.tick(this, owner, "ki", true, 0.8F + 0.25F * output(), tickCount);
        }
    }

    @Override
    public void onRemovedFromLevel() {
        if (level().isClientSide()) {
            DemonAuraFx.stop(this, "ki");
        }
        super.onRemovedFromLevel();
    }
}
