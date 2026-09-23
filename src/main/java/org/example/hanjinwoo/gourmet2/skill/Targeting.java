package org.example.hanjinwoo.gourmet2.skill;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.Config;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shared target selection for the skills: cones, lines, rings and crosshair locks. */
public final class Targeting {
    private Targeting() {}

    /** Whether {@code target} is a legal victim for {@code caster}'s skills. */
    public static boolean canTarget(Player caster, Entity target) {
        if (target == caster || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (!(target instanceof LivingEntity living) || living.isInvulnerable()) {
            return false;
        }
        if (target instanceof Player other) {
            if (!Config.affectPlayers) {
                return false;
            }
            return !caster.isAlliedTo(other);
        }
        return true;
    }

    /**
     * Living entities within {@code range} of the caster's eyes that sit inside a cone of
     * {@code halfAngleDeg} around {@code direction}, nearest first.
     */
    public static List<LivingEntity> inCone(Player caster, Vec3 direction, double range, double halfAngleDeg, int limit) {
        Vec3 origin = caster.getEyePosition();
        Vec3 dir = direction.normalize();
        double cosLimit = Math.cos(Math.toRadians(halfAngleDeg));
        AABB box = caster.getBoundingBox().inflate(range);

        List<LivingEntity> found = new ArrayList<>();
        for (Entity entity : caster.level().getEntities(caster, box, e -> canTarget(caster, e))) {
            Vec3 toTarget = entity.getBoundingBox().getCenter().subtract(origin);
            double distance = toTarget.length();
            if (distance > range || distance < 1.0E-4) {
                continue;
            }
            if (toTarget.scale(1.0 / distance).dot(dir) < cosLimit) {
                continue;
            }
            found.add((LivingEntity) entity);
        }
        found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(origin)));
        return limit > 0 && found.size() > limit ? found.subList(0, limit) : found;
    }

    /**
     * Living entities whose hitbox is within {@code radius} of the ray from {@code origin} along
     * {@code direction}, ordered by distance along the ray. Used by the piercing Fork stabs.
     */
    public static List<LivingEntity> alongRay(Player caster, Vec3 origin, Vec3 direction, double range, double radius) {
        Vec3 dir = direction.normalize();
        Vec3 end = origin.add(dir.scale(range));
        AABB box = new AABB(origin, end).inflate(radius + 1.0);

        List<LivingEntity> found = new ArrayList<>();
        for (Entity entity : caster.level().getEntities(caster, box, e -> canTarget(caster, e))) {
            if (entity.getBoundingBox().inflate(radius).clip(origin, end).isPresent()) {
                found.add((LivingEntity) entity);
            }
        }
        found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(origin)));
        return found;
    }

    /** Living entities within {@code radius} of {@code center}, nearest first. */
    public static List<LivingEntity> inSphere(Player caster, Vec3 center, double radius) {
        AABB box = AABB.ofSize(center, radius * 2, radius * 2, radius * 2);
        double radiusSqr = radius * radius;

        List<LivingEntity> found = new ArrayList<>();
        for (Entity entity : caster.level().getEntities(caster, box, e -> canTarget(caster, e))) {
            if (entity.getBoundingBox().getCenter().distanceToSqr(center) <= radiusSqr) {
                found.add((LivingEntity) entity);
            }
        }
        found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
        return found;
    }

    /**
     * The single entity the caster is aiming at: whatever the crosshair ray touches first, falling
     * back to the nearest thing in a narrow forward cone so that skills still feel responsive.
     */
    public static @Nullable LivingEntity lockOn(Player caster, double range) {
        Vec3 direction = caster.getLookAngle();
        List<LivingEntity> narrow = inCone(caster, direction, range, 12.0, 1);
        if (!narrow.isEmpty()) {
            return narrow.get(0);
        }
        List<LivingEntity> wide = inCone(caster, direction, range, 35.0, 1);
        return wide.isEmpty() ? null : wide.get(0);
    }

    /**
     * Where a skill's visual impact should happen: the first block the ray hits, or the far end of
     * the ray if it hits nothing.
     */
    public static Vec3 impactPoint(Player caster, Vec3 direction, double range) {
        Vec3 origin = caster.getEyePosition();
        Vec3 end = origin.add(direction.normalize().scale(range));
        BlockHitResult hit = caster.level().clip(new ClipContext(
                origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    /** True if nothing solid stands between {@code from} and {@code to}. */
    public static boolean hasLineOfSight(Level level, Entity viewer, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer)).getType() == HitResult.Type.MISS;
    }
}
