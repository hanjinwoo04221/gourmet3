package org.example.hanjinwoo.gourmet2.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.fx.FxDispatch;
import org.example.hanjinwoo.gourmet2.fx.SkillFx;
import org.example.hanjinwoo.gourmet2.registry.ModDamageTypes;
import org.example.hanjinwoo.gourmet2.registry.ModEntities;
import org.example.hanjinwoo.gourmet2.skill.Hurt;

/**
 * レッグナイフ — the single crescent of cutting wind a Leg Knife kick throws. A charged kick makes it
 * bigger (see {@link #setSizeScale}) and harder-hitting; it lifts what it cuts off the ground.
 */
public class LegKnifeSlashEntity extends SkillProjectile {
    private float damage = 12.0F;

    public LegKnifeSlashEntity(EntityType<? extends LegKnifeSlashEntity> type, Level level) {
        super(type, level);
    }

    public LegKnifeSlashEntity(Level level) {
        super(ModEntities.LEG_KNIFE_SLASH.get(), level);
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    @Override
    protected float damage() {
        return damage;
    }

    @Override
    protected int maxLifeTicks() {
        return 28;
    }

    @Override
    protected int maxPierce() {
        return 8;
    }

    /** A kicked crescent keeps gaining speed as it whips outward. */
    private static final double ACCELERATION_PER_TICK = 1.05;

    @Override
    protected double accelerationPerTick() {
        return ACCELERATION_PER_TICK;
    }

    @Override
    protected double breakSweepMargin() {
        // Reaches well above and below its own hitbox, matching the tall charged crescent visual.
        return 0.7;
    }

    @Override
    protected void hitTarget(LivingEntity target) {
        target.invulnerableTime = 0;
        boolean landed = target.hurt(
                ModDamageTypes.source(level(), ModDamageTypes.CUTTING, this, getOwner()), damage);
        if (!landed) {
            return;
        }
        Hurt.launch(target, getDeltaMovement().normalize().add(0.0, 0.7, 0.0), 0.55);
        FxDispatch.at(serverLevel(), SkillFx.KNIFE_IMPACT, target.getBoundingBox().getCenter(), 0.8F, 0.0F, 0.0F);
        level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    @Override
    protected void dissipate(Vec3 at, boolean hitBlock) {
        FxDispatch.at(serverLevel(), SkillFx.KNIFE_IMPACT, at, hitBlock ? 1.0F : 0.6F, getYRot(), getXRot());
    }

    /**
     * A crescent of cutting wind. The tallest and widest of the three, so it spreads its force thinnest:
     * it cuts through foliage and glass, but a charged kick widening it further buys damage, not penetration.
     */
    @Override
    protected boolean tryBreakBlock(BlockHitResult hit) {
        Vec3 blow = getDeltaMovement();
        return casterOrNull() instanceof ServerPlayer caster
                && Hurt.breakThrough(caster, level(), hit.getBlockPos(), blow, damage, impactArea(blow));
    }
}
