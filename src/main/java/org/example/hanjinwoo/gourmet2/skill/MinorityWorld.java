package org.example.hanjinwoo.gourmet2.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shared definitions of Ichiryu's Minority World: the setting flags, the spherical zone, and the rules for who a
 * zone reaches. Both sides use these; each side keeps its own map of zones (the server's here, the client's in
 * {@code ClientMinorityWorld}).
 */
public final class MinorityWorld {
    public static final int VISION = 1;
    public static final int HEAL = 2;
    public static final int ATTACK_DIRECTION = 4;
    public static final int FLIGHT = 8;
    public static final int SOLID_TO_LIQUID = 16;
    public static final int LIQUID_TO_SOLID = 32;
    /** Mask of the six abilities. */
    public static final int EFFECTS = 63;
    /** Who the abilities reach: the caster, and/or everybody else in the zone. */
    public static final int AFFECT_SELF = 64;
    public static final int AFFECT_OTHERS = 128;
    public static final int DEFAULT = AFFECT_OTHERS;

    /** The zones running on the server, by caster. Only touched from the server thread. */
    public static final Map<UUID, Zone> SERVER_ZONES = new HashMap<>();

    private MinorityWorld() {}

    /** A sphere in one dimension with the abilities its caster switched on. */
    public record Zone(UUID caster, ResourceLocation dimension, Vec3 center, double radius, int flags) {
        public boolean contains(Entity entity) {
            if (!entity.level().dimension().location().equals(dimension)) {
                return false;
            }
            Vec3 middle = entity.position().add(0.0, entity.getBbHeight() / 2.0, 0.0);
            return middle.distanceToSqr(center) <= radius * radius;
        }

        public Zone withFlags(int newFlags) {
            return new Zone(caster, dimension, center, radius, newFlags);
        }
    }

    /**
     * Where {@code caster} aims their attacks: the way they look, unless a zone with inverted attack direction has
     * them in it, in which case the exact opposite. Server only; the client always reads its own view.
     */
    public static Vec3 aim(net.minecraft.world.entity.player.Player caster) {
        Vec3 look = caster.getLookAngle();
        if (caster.level().isClientSide() || SERVER_ZONES.isEmpty()) {
            return look;
        }
        return (flagsFor(SERVER_ZONES.values(), caster) & ATTACK_DIRECTION) != 0 ? look.scale(-1.0) : look;
    }

    /** Every ability that some zone in {@code zones} applies to {@code entity}. */
    public static int flagsFor(Collection<Zone> zones, Entity entity) {
        int out = 0;
        for (Zone zone : zones) {
            if (!zone.contains(entity)) {
                continue;
            }
            int reach = entity.getUUID().equals(zone.caster()) ? AFFECT_SELF : AFFECT_OTHERS;
            if ((zone.flags() & reach) != 0) {
                out |= zone.flags() & EFFECTS;
            }
        }
        return out;
    }

    /**
     * Liquid made solid: keeps {@code entity} standing on the surface of the fluid it is at or just above, unless it
     * is rising or crouching (which lets it dive). The one place the rule lives; the client runs it for its own
     * player (which moves itself) and the server for everything else.
     */
    public static void standOnFluid(LivingEntity entity) {
        if (entity.getDeltaMovement().y > 0.05 || entity.isShiftKeyDown()) {
            return;
        }
        Level level = entity.level();
        BlockPos feet = entity.blockPosition();
        for (int k = 2; k >= -1; k--) {
            BlockPos pos = feet.above(k);
            FluidState fluid = level.getFluidState(pos);
            if (fluid.isEmpty() || !level.getFluidState(pos.above()).isEmpty()) {
                continue;
            }
            double surface = pos.getY() + fluid.getHeight(level, pos);
            if (entity.getY() <= surface + 0.1 && entity.getY() >= surface - 1.5) {
                entity.setPos(entity.getX(), surface, entity.getZ());
                Vec3 motion = entity.getDeltaMovement();
                entity.setDeltaMovement(motion.x, 0.0, motion.z);
                entity.setOnGround(true);
                entity.resetFallDistance();
            }
            return;
        }
    }
}
