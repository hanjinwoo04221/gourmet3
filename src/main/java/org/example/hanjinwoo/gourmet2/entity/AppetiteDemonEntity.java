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
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * 食欲の悪魔 — the Appetite Demon of Intimidation. Stage 1 hovers its upper body over the caster;
 * stage 2 replaces the caster's own model with the whole demon.
 * Purely visual: the gameplay effect lives in the Intimidation skill.
 *
 * <p>It mirrors its owner every tick on both sides: same facing, riding a fixed spot on them, walking when they walk. Head look and arm swings are mirrored in the renderer's model.
 * It lives until the skill discards it (or its owner vanishes).
 */
public class AppetiteDemonEntity extends VisualEntity implements GeoEntity {
    private static final EntityDataAccessor<Integer> OWNER_ID =
            SynchedEntityData.defineId(AppetiteDemonEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> STAGE =
            SynchedEntityData.defineId(AppetiteDemonEntity.class, EntityDataSerializers.INT);

    /** In stage 1 the demon's upper body hovers this far above the caster's feet, clear of their back. */
    private static final double STAGE_ONE_LIFT = 1.5;

    /** Client-side: ids of players currently transformed into the demon, so their own model is hidden. */
    private static final Set<Integer> TRANSFORMED_PLAYERS = new HashSet<>();

    public static boolean isTransformed(Entity player) {
        return TRANSFORMED_PLAYERS.contains(player.getId());
    }

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.crimson_demon.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.crimson_demon.walk");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public AppetiteDemonEntity(EntityType<? extends AppetiteDemonEntity> type, Level level) {
        super(type, level);
    }

    public AppetiteDemonEntity(Level level) {
        super(ModEntities.APPETITE_DEMON.get(), level);
    }

    public void setOwner(Entity owner) {
        entityData.set(OWNER_ID, owner.getId());
        followOwner(owner);
    }

    /** 1 = upper body hovering over the caster, 2 = the caster's whole body is the demon. */
    public int getStage() {
        return entityData.get(STAGE);
    }

    public void setStage(int stage) {
        entityData.set(STAGE, stage);
    }

    @Override
    public void onRemovedFromLevel() {
        // Fires on the client whenever the server discards the demon (toggle off, death, logout).
        if (level().isClientSide()) {
            TRANSFORMED_PLAYERS.remove(entityData.get(OWNER_ID));
            DemonAuraFx.stop(this, "intimidation");
        }
        super.onRemovedFromLevel();
    }

    public @Nullable LivingEntity owner() {
        Entity entity = level().getEntity(entityData.get(OWNER_ID));
        return entity instanceof LivingEntity living ? living : null;
    }

    @Override
    public int lifetime() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_ID, -1);
        builder.define(STAGE, 1);
    }

    @Override
    public void tick() {
        super.tick();
        LivingEntity owner = owner();
        if (owner == null) {
            // The client may simply not have the owner yet; only the server decides to give up.
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
        followOwner(owner);
        if (level().isClientSide()) {
            if (getStage() >= 2) {
                TRANSFORMED_PLAYERS.add(owner.getId());
            } else {
                TRANSFORMED_PLAYERS.remove(owner.getId());
            }
            DemonAuraFx.tick(this, owner, "intimidation", getStage() == 1, 1.5F, tickCount);
        }
    }

    private void followOwner(Entity owner) {
        // The position is fixed relative to the caster; only the facing follows their view yaw.
        double lift = getStage() >= 2 ? 0.0 : STAGE_ONE_LIFT;
        setPos(owner.getX(), owner.getY() + lift, owner.getZ());
        setYRot(owner.getYRot());
        setXRot(0.0F);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 4, state -> {
            LivingEntity owner = state.getAnimatable().owner();
            boolean moving = owner != null && owner.walkAnimation.speed() > 0.02F;
            if (moving) {
                // Keep the stride in step with how fast the caster is actually travelling.
                state.getController().setAnimationSpeed(Math.max(0.6, Math.min(2.0, owner.walkAnimation.speed() * 1.6)));
            } else {
                state.getController().setAnimationSpeed(1.0);
            }
            return state.setAndContinue(moving ? WALK : IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
