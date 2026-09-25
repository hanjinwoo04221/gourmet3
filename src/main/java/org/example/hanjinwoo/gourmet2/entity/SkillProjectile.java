package org.example.hanjinwoo.gourmet2.entity;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.skill.Hurt;
import org.example.hanjinwoo.gourmet2.skill.Targeting;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shared flight logic for the thrown techniques: constant-velocity travel, no gravity, and the
 * ability to run through several victims before dissipating.
 *
 * <p>Collision is resolved server-side only; clients run the same movement step so the projectile
 * interpolates smoothly between position updates.
 */
public abstract class SkillProjectile extends Projectile {
    /** Guards against an unbounded loop if the hit scan keeps returning the same entity. */
    private static final int MAX_SCANS_PER_TICK = 8;

    private final IntSet alreadyHit = new IntOpenHashSet();
    protected int life;
    /** Player-tuned power scale (see the settings GUI). Widens the hitbox; visuals scale separately via FxDispatch. */
    private float sizeScale = 1.0F;

    protected SkillProjectile(EntityType<? extends SkillProjectile> type, Level level) {
        super(type, level);
    }

    /** How long the projectile flies before dissipating on its own. */
    protected abstract int maxLifeTicks();

    /** How many victims it can run through before it breaks up. */
    protected abstract int maxPierce();

    /** Apply this projectile's damage and status effects to one victim. */
    protected abstract void hitTarget(LivingEntity target);

    /**
     * How much this technique hurts. Doubles as the weight it puts behind a blow against terrain: see
     * {@link Hurt#impulse}, where a heavier technique punches deeper than a lighter one thrown as fast.
     */
    protected abstract float damage();

    /**
     * The face this technique turns toward what it runs into, in blocks² — its own hitbox as built, not as
     * tuned. The size setting widens what it collides with and how much it reaches, but how <i>deep</i> a
     * blow bites is a property of the technique's shape: a fork is a prong and a knife is a fan, and no
     * amount of dialling either up spreads that force thinner. See {@link Hurt#frontalArea}.
     */
    protected double impactArea(Vec3 blow) {
        EntityDimensions base = super.getDimensions(getPose());
        double half = base.width() / 2.0;
        return Hurt.frontalArea(new AABB(-half, 0.0, -half, half, base.height(), half), blow);
    }

    /**
     * Multiplies the flight speed by this much every tick; 1.0 (the default) is constant speed. A
     * value above 1 makes the technique visibly build momentum as it flies, the way a thrown vacuum
     * blade keeps accelerating rather than coasting.
     */
    protected double accelerationPerTick() {
        return 1.0;
    }

    /**
     * Extra margin (in blocks) added around the projectile's actual size when sweeping for blocks to
     * destroy each tick. 0 by default; techniques whose visual effect reaches further than their
     * hitbox (a wide slash, a crescent wave) should widen this instead of relying on hitbox size
     * alone, since a bigger hitbox also changes how it collides with entities.
     */
    protected double breakSweepMargin() {
        return 0.1;
    }

    /**
     * Called exactly once, when the projectile stops existing.
     *
     * @param hitBlock true if it was stopped by terrain rather than expiring or piercing out
     */
    protected abstract void dissipate(Vec3 at, boolean hitBlock);

    /**
     * Called when the flight path runs into solid terrain, before the projectile gives up and
     * dissipates. Implementations that want to punch through soft terrain (leaves, glass, ...)
     * should break the block here and return true to keep flying; the default just lets the
     * projectile stop, matching every other block.
     *
     * @return true if the block was destroyed and the projectile should keep flying through it
     */
    protected boolean tryBreakBlock(BlockHitResult hit) {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // No synched data; everything the renderer needs comes from position and rotation.
    }

    public void setSizeScale(float scale) {
        this.sizeScale = Math.max(0.2F, scale);
        refreshDimensions();
    }


    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(sizeScale);
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide()) {
            if (++life > maxLifeTicks()) {
                finish(position(), false);
                return;
            }
            if (casterOrNull() instanceof ServerPlayer caster) {
                // What it tears through depends on how hard this technique actually hits where it is now:
                // its speed is its own, growing as it accelerates, not the caster's.
                Vec3 blow = getDeltaMovement();
                Hurt.sweepBreak(caster, level(), getBoundingBox().inflate(breakSweepMargin()), blow, damage(),
                        impactArea(blow));
            }
            if (scanForHits()) {
                return;
            }
        }

        double accel = accelerationPerTick();
        Vec3 motion = getDeltaMovement();
        if (accel != 1.0) {
            motion = motion.scale(accel);
            setDeltaMovement(motion);
        }
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        updateRotation();
    }

    /** @return true if the projectile was consumed and the caller must stop ticking it. */
    private boolean scanForHits() {
        for (int scan = 0; scan < MAX_SCANS_PER_TICK; scan++) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() == HitResult.Type.MISS) {
                return false;
            }
            if (hit.getType() == HitResult.Type.BLOCK) {
                if (tryBreakBlock((BlockHitResult) hit)) {
                    // Block is gone; re-cast from the current position next scan instead of
                    // stopping, so the projectile visibly punches through.
                    continue;
                }
                finish(hit.getLocation(), true);
                return true;
            }

            Entity victim = ((EntityHitResult) hit).getEntity();
            alreadyHit.add(victim.getId());
            if (victim instanceof LivingEntity living) {
                hitTarget(living);
            }
            if (alreadyHit.size() >= maxPierce()) {
                finish(hit.getLocation(), false);
                return true;
            }
        }
        return false;
    }

    private void finish(Vec3 at, boolean hitBlock) {
        dissipate(at, hitBlock);
        discard();
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target) || alreadyHit.contains(target.getId())) {
            return false;
        }
        return !(getOwner() instanceof Player caster) || Targeting.canTarget(caster, target);
    }

    /** The caster, if they are still around and are a player. */
    protected Player casterOrNull() {
        return getOwner() instanceof Player player ? player : null;
    }

    protected ServerLevel serverLevel() {
        return (ServerLevel) level();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }
}
