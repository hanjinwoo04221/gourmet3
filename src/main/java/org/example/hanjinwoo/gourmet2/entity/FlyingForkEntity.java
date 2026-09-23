package org.example.hanjinwoo.gourmet2.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.fx.FxDispatch;
import org.example.hanjinwoo.gourmet2.fx.SkillFx;
import org.example.hanjinwoo.gourmet2.registry.ModDamageTypes;
import org.example.hanjinwoo.gourmet2.registry.ModAttachments;
import org.example.hanjinwoo.gourmet2.registry.ModEntities;
import org.example.hanjinwoo.gourmet2.registry.ModMobEffects;
import org.example.hanjinwoo.gourmet2.skill.Hurt;

/**
 * フライングフォーク — one prong of the Flying Fork. Four of these are launched together in a tight
 * spread; each runs through up to three victims and leaves them bleeding.
 */
public class FlyingForkEntity extends SkillProjectile {
    private static final float BASE_DAMAGE = 7.0F;
    private static final int BLEED_DURATION = 120;

    /** Set by the skill so config scaling and Gourmet Cell awakening carry over to the projectile. */
    private float damage = BASE_DAMAGE;
    /** A thrown prong keeps gaining speed, punching harder the further it travels. */
    private static final double ACCELERATION_PER_TICK = 1.035;

    public FlyingForkEntity(EntityType<? extends FlyingForkEntity> type, Level level) {
        super(type, level);
    }

    public FlyingForkEntity(Level level) {
        super(ModEntities.FLYING_FORK.get(), level);
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    @Override
    protected double accelerationPerTick() {
        return ACCELERATION_PER_TICK;
    }

    @Override
    protected int maxLifeTicks() {
        return 30;
    }

    @Override
    protected int maxPierce() {
        return 3;
    }

    @Override
    protected void hitTarget(LivingEntity target) {
        target.invulnerableTime = 0;
        boolean landed = target.hurt(
                ModDamageTypes.source(level(), ModDamageTypes.CUTTING, this, getOwner()), damage);
        if (!landed) {
            return;
        }
        target.addEffect(new MobEffectInstance(ModMobEffects.bleeding(), BLEED_DURATION, 0, false, true, true));
        FxDispatch.at(serverLevel(), SkillFx.FLYING_FORK_IMPACT, target.getBoundingBox().getCenter());
        level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.7F, 1.6F);
    }

    @Override
    protected void dissipate(Vec3 at, boolean hitBlock) {
        if (hitBlock) {
            FxDispatch.at(serverLevel(), SkillFx.FLYING_FORK_IMPACT, at, 0.7F, getYRot(), getXRot());
            level().playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 0.5F, 1.4F);
        }
    }

    /** A vacuum-pressure spear; it punches straight through foliage, glass and the like. */
    @Override
    protected boolean tryBreakBlock(BlockHitResult hit) {
        return casterOrNull() instanceof ServerPlayer caster
                && Hurt.breakByPower(caster, ModAttachments.of(caster).cellLevel(), level(), hit.getBlockPos());
    }
}
