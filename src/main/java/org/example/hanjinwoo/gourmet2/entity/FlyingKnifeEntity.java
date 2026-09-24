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
 * フライングナイフ — a crescent cutting wave. Slower and wider than a Flying Fork prong, it slices
 * through a whole line of enemies and shears the undergrowth it passes through.
 */
public class FlyingKnifeEntity extends SkillProjectile {
    private static final float BASE_DAMAGE = 20.0F;

    private float damage = BASE_DAMAGE;

    public FlyingKnifeEntity(EntityType<? extends FlyingKnifeEntity> type, Level level) {
        super(type, level);
    }

    public FlyingKnifeEntity(Level level) {
        super(ModEntities.FLYING_KNIFE.get(), level);
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
        return 34;
    }

    @Override
    protected int maxPierce() {
        return 5;
    }

    /** A crescent wave keeps gaining speed, building momentum as it slices onward. */
    private static final double ACCELERATION_PER_TICK = 1.04;

    @Override
    protected double accelerationPerTick() {
        return ACCELERATION_PER_TICK;
    }

    @Override
    protected double breakSweepMargin() {
        // Wider than its hitbox: the wave visibly fans out further than what it actually collides with.
        return 0.5;
    }

    @Override
    protected void hitTarget(LivingEntity target) {
        target.invulnerableTime = 0;
        boolean landed = target.hurt(
                ModDamageTypes.source(level(), ModDamageTypes.CUTTING, this, getOwner()), damage);
        if (!landed) {
            return;
        }
        FxDispatch.at(serverLevel(), SkillFx.FLYING_KNIFE_IMPACT, target.getBoundingBox().getCenter());
        level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    @Override
    protected void dissipate(Vec3 at, boolean hitBlock) {
        FxDispatch.at(serverLevel(), SkillFx.FLYING_KNIFE_IMPACT, at, hitBlock ? 1.0F : 0.6F, getYRot(), getXRot());
        if (hitBlock) {
            level().playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, 0.6F);
        }
    }

    /**
     * A crescent of cutting wind with a wide fanned face: it shears foliage and glass apart, but against
     * anything with real hardness the same force spread over that much area barely marks it.
     */
    @Override
    protected boolean tryBreakBlock(BlockHitResult hit) {
        Vec3 blow = getDeltaMovement();
        return casterOrNull() instanceof ServerPlayer caster
                && Hurt.breakThrough(caster, level(), hit.getBlockPos(), blow, damage, impactArea(blow));
    }
}
