package org.example.hanjinwoo.gourmet2.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.compat.CombatAnimations;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.registry.ModAttachments;

/**
 * The charged leap: hold the leap key while supported by a surface to wind up, let go to launch at
 * speed in the direction the player is looking.
 *
 * <p>Two things decide where it ends. The wind-up decides how far it *can* go — a tap barely hops, a
 * full charge reaches {@link #MAX_CHARGE_TICKS} ticks' worth — and the attributes decide what a full
 * charge is worth: attack damage ("힘") and movement speed ("속도") each add reach on top of the
 * baseline, up to {@link #DISTANCE_CAP}. If the crosshair is on an entity that is actually visible,
 * the leap homes at it instead and stops just short, even when the charge would have carried further.
 *
 * <p>The dash is server-driven and re-aimed every tick, so it lands on the mark rather than wherever
 * an impulse happened to end up, and it is capped in speed so it still reads as a leap and not a
 * teleport. Everything is transient state on {@link TorikoData}; nothing here persists.
 */
public final class LeapEngine {
    /** Ticks of holding the key that reach a full charge. Matches the length of the wind-up clip. */
    public static final int MAX_CHARGE_TICKS = 20;

    /** How far ahead the crosshair can pick an entity to fly at. */
    private static final double TARGET_RANGE = 24.0;
    /** Blocks covered by a tap / by a full charge at baseline attributes. */
    private static final double MIN_DISTANCE = 2.5;
    private static final double MAX_DISTANCE = 6.5;
    /** Whatever the attributes say, a leap never reaches further than this. */
    private static final double DISTANCE_CAP = 16.0;
    /** Baseline player attributes; only what a player has above these lengthens the leap. */
    private static final double BASE_ATTACK_DAMAGE = 1.0;
    private static final double BASE_MOVEMENT_SPEED = 0.1;
    /** Extra blocks of reach per point of attack damage ("힘") and per point of movement speed ("속도"). */
    private static final double BLOCKS_PER_DAMAGE = 0.35;
    private static final double BLOCKS_PER_SPEED = 30.0;
    /** Cruise speed of the dash in blocks per tick, and the hard cap it is never allowed to exceed. */
    private static final double CRUISE_SPEED = 1.2;
    private static final double MAX_SPEED = 1.8;
    /** Close enough to the aim point to have arrived, and how far short of a target entity to stop. */
    private static final double ARRIVE_EPSILON = 0.75;
    private static final double ENTITY_STAND_OFF = 0.7;
    /** Fall damage is ignored for this long after a leap, so a dive off a ceiling onto the floor is free. */
    private static final int FALL_IMMUNITY_TICKS = 60;

    private LeapEngine() {}

    /** The leap key went down: plant and wind up, if there is a surface to push off from. */
    public static void charge(ServerPlayer player) {
        TorikoData data = ModAttachments.of(player);
        if (data.isLeapCharging() || data.isLeaping() || !supported(player)) {
            return;
        }
        data.setLeapCharging(true);
        data.setGuarding(false);
        CombatAnimations.playSkill(player, "leap_charge");
    }

    /** The leap key came up: launch, however far the wind-up earned. */
    public static void release(ServerPlayer player) {
        TorikoData data = ModAttachments.of(player);
        if (!data.isLeapCharging()) {
            return;
        }
        int chargeTicks = data.leapChargeTicks();
        data.setLeapCharging(false);

        float fraction = Math.min(1.0F, chargeTicks / (float) MAX_CHARGE_TICKS);
        double reach = Charge.lerp((float) MIN_DISTANCE, (float) maxDistance(player), fraction);

        Entity target = aimedEntity(player);
        Vec3 aim = target == null
                ? player.getEyePosition().add(player.getLookAngle().scale(reach))
                : stopPoint(player, target);
        double travel = Math.min(reach, player.position().distanceTo(aim));
        if (travel < 0.5) {
            return;
        }

        data.setLeapAim(aim);
        data.setLeapTargetId(target == null ? -1 : target.getId());
        data.setLeapTicks(Math.max(2, (int) Math.ceil(travel / CRUISE_SPEED)));
        data.setFallImmuneTicks(FALL_IMMUNITY_TICKS);
        data.setGuarding(false);
        CombatAnimations.playSkill(player, "leap");
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.9F, 1.4F);
    }

    /** Winds the charge up while the key is held, then flies the dash a tick at a time. */
    public static void tick(ServerPlayer player, TorikoData data) {
        if (data.isLeapCharging()) {
            data.tickLeapCharge();
            // Planted for the wind-up: the horizontal motion of whatever they were doing is dropped, but
            // falling still settles them onto the floor.
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(0.0, motion.y, 0.0);
            player.hurtMarked = true;
            return;
        }
        if (!data.isLeaping()) {
            return;
        }
        Vec3 aim = data.leapAim();
        int targetId = data.leapTargetId();
        if (targetId >= 0) {
            Entity target = player.level().getEntity(targetId);
            if (target != null && target.isAlive()) {
                aim = stopPoint(player, target);
                data.setLeapAim(aim);
            } else {
                data.setLeapTargetId(-1);
            }
        }
        Vec3 toAim = aim.subtract(player.position());
        double remaining = toAim.length();
        int ticksLeft = data.leapTicks();
        if (remaining <= ARRIVE_EPSILON || ticksLeft <= 1) {
            data.stopLeap();
            // Arrive, don't stop dead: a little of the speed is kept so the landing still slides.
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x * 0.2, motion.y, motion.z * 0.2);
            player.hurtMarked = true;
            return;
        }
        // Speed is recomputed from what is left, so the dash slows into the last couple of blocks, and
        // something that got in the way is pushed past rather than stranding the leap.
        double speed = Math.min(MAX_SPEED, Math.max(0.4, remaining / ticksLeft));
        player.setDeltaMovement(toAim.scale(speed / remaining));
        // The dash carries the player itself, so the fall it would otherwise count is not a fall.
        player.fallDistance = 0.0F;
        player.hurtMarked = true;
        data.setLeapTicks(ticksLeft - 1);
    }

    /** Clears the leap when something else takes over, whatever half of it was running. */
    public static void stop(TorikoData data) {
        data.stopLeap();
    }

    /**
     * Whether the player has something to push off: the floor under them, or anything they are holding
     * on to (a ladder or a vine). A ceiling or wall to cling to does not exist in this mod yet, so those
     * are covered only as far as vanilla lets a player be attached to one.
     */
    private static boolean supported(ServerPlayer player) {
        return player.onGround() || player.onClimbable();
    }

    /** Blocks a full charge reaches with this player's attributes. */
    private static double maxDistance(ServerPlayer player) {
        double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double reach = MAX_DISTANCE
                + Math.max(0.0, damage - BASE_ATTACK_DAMAGE) * BLOCKS_PER_DAMAGE
                + Math.max(0.0, speed - BASE_MOVEMENT_SPEED) * BLOCKS_PER_SPEED;
        return Math.min(DISTANCE_CAP, reach);
    }

    /**
     * The entity under the crosshair, or null. Only entities in plain sight count: one behind terrain is
     * passed over, so a leap at something through a wall just carries the player at the wall instead.
     */
    private static Entity aimedEntity(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(TARGET_RANGE));
        AABB sweep = player.getBoundingBox().expandTowards(look.scale(TARGET_RANGE)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, sweep,
                candidate -> candidate != player && candidate.isPickable() && !candidate.isSpectator(),
                TARGET_RANGE * TARGET_RANGE);
        if (hit == null) {
            return null;
        }
        BlockHitResult block = player.level().clip(new ClipContext(eye, hit.getLocation(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        boolean blocked = block.getType() != HitResult.Type.MISS
                && block.getLocation().distanceToSqr(eye) < hit.getLocation().distanceToSqr(eye);
        return blocked ? null : hit.getEntity();
    }

    /** Where a leap aimed at this entity ends: level with its middle, just short of its hitbox. */
    private static Vec3 stopPoint(ServerPlayer player, Entity target) {
        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 flat = new Vec3(center.x - player.getX(), 0.0, center.z - player.getZ());
        if (flat.lengthSqr() < 1.0E-4) {
            return center;
        }
        Vec3 back = flat.normalize().scale(target.getBbWidth() * 0.5 + ENTITY_STAND_OFF);
        return new Vec3(center.x - back.x, center.y, center.z - back.z);
    }
}
