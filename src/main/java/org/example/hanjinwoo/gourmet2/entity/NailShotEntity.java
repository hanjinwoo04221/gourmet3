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
 * ネイルガン Nail Gun — one round of the barrage: a nail driven out on a line of vacuum, with the gun's own
 * effect riding it (see {@code NailGunSkill}). Small and fast where a Flying Fork prong is heavy and slow, and
 * it screws through what it hits rather than bludgeoning it.
 *
 * <p>What it is worth against terrain comes from its speed and its damage over its own tiny contact patch (see
 * {@link Hurt#impulse}), so a round fired at the end of a long charge, moving faster, gets further through a
 * wall than one fired off a tap.
 */
public class NailShotEntity extends SkillProjectile {
    private static final float BASE_DAMAGE = 3.5F;
    /** Damage per pierce on armour: a nail goes through flesh and finds the plate behind it. */
    private static final float PIERCE_FRACTION = 0.3F;

    private float damage = BASE_DAMAGE;

    /** A round gathers pace as it goes, the way the thrown techniques do. */
    private static final double ACCELERATION_PER_TICK = 1.03;

    public NailShotEntity(EntityType<? extends NailShotEntity> type, Level level) {
        super(type, level);
    }

    public NailShotEntity(Level level) {
        super(ModEntities.NAIL_SHOT.get(), level);
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
        return 22;
    }

    @Override
    protected int maxPierce() {
        return 3;
    }

    @Override
    protected double accelerationPerTick() {
        return ACCELERATION_PER_TICK;
    }

    /** A nail is a point: next to no face to spread the blow over. */
    @Override
    protected double breakSweepMargin() {
        return 0.05;
    }

    @Override
    protected void hitTarget(LivingEntity target) {
        target.invulnerableTime = 0;
        boolean landed = target.hurt(
                ModDamageTypes.source(level(), ModDamageTypes.NAIL_PIERCE, this, getOwner()), damage);
        if (!landed) {
            return;
        }
        Hurt.knockAway(target, position(), 0.15);
        FxDispatch.at(serverLevel(), SkillFx.NAIL_GUN_IMPACT, target.getBoundingBox().getCenter(), 0.7F, 0.0F, 0.0F);
        level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.5F, 1.8F);
    }

    @Override
    protected void dissipate(Vec3 at, boolean hitBlock) {
        if (hitBlock) {
            FxDispatch.at(serverLevel(), SkillFx.NAIL_GUN_IMPACT, at, 0.5F, getYRot(), getXRot());
            level().playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 0.4F, 1.7F);
        }
    }

    /**
     * A nail punches through what its impulse beats (see {@link Hurt#breakThrough}): foliage, glass and the
     * like go down to any round, and a round fired off a full charge is fast enough to screw into stone.
     */
    @Override
    protected boolean tryBreakBlock(BlockHitResult hit) {
        Vec3 blow = getDeltaMovement();
        return casterOrNull() instanceof ServerPlayer caster
                && Hurt.breakThrough(caster, level(), hit.getBlockPos(), blow, damage, impactArea(blow));
    }
}
