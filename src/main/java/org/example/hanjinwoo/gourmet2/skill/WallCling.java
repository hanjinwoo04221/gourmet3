package org.example.hanjinwoo.gourmet2.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Hanging on a wall and moving across it, shared by the player and the Lizardman. Nothing here is state: it finds the
 * wall the body is against and turns the movement keys (or a mob's goal) into a velocity along that wall.
 *
 * <p>The player runs it on both the client and the server every tick from the same inputs, so the two agree and the
 * body is not pulled back. Gravity needs no switching off: the velocity is set afresh before every move, which is
 * what a ladder does too.
 */
public final class WallCling {
    /** Blocks per tick going up, going down, and across the wall. */
    public static final double UP_SPEED = 0.21;
    public static final double DOWN_SPEED = 0.24;
    public static final double SIDE_SPEED = 0.18;
    /** A light push into the wall so contact is never lost while hanging still. */
    private static final double STICK = 0.04;
    /** How far from the body a block still counts as the wall it is on. */
    private static final double REACH = 0.14;
    /** The push a wall jump gives away from the wall, and up. */
    public static final double JUMP_AWAY = 0.5;
    public static final double JUMP_UP = 0.55;

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private WallCling() {}

    /** The horizontal unit vector pointing from the wall toward the body, or null if it is not against one. */
    public static @Nullable Vec3 wallNormal(Entity entity) {
        AABB box = entity.getBoundingBox();
        Level level = entity.level();
        double halfX = box.getXsize() / 2.0;
        double halfZ = box.getZsize() / 2.0;
        double centerX = (box.minX + box.maxX) / 2.0;
        double centerZ = (box.minZ + box.maxZ) / 2.0;
        double[] heights = {box.minY + 0.3, (box.minY + box.maxY) / 2.0, box.maxY - 0.3};
        double nx = 0.0;
        double nz = 0.0;
        for (int[] side : SIDES) {
            double reach = (side[0] != 0 ? halfX : halfZ) + REACH;
            for (double y : heights) {
                BlockPos pos = BlockPos.containing(centerX + side[0] * reach, y, centerZ + side[1] * reach);
                if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                    nx -= side[0];
                    nz -= side[1];
                    break;
                }
            }
        }
        double length = Math.sqrt(nx * nx + nz * nz);
        return length < 1.0E-6 ? null : new Vec3(nx / length, 0.0, nz / length);
    }

    /** Whether a solid block is right over the head: something to hang from. */
    public static boolean ceilingAbove(Entity entity) {
        AABB box = entity.getBoundingBox();
        Level level = entity.level();
        double y = box.maxY + REACH;
        double[][] points = {{(box.minX + box.maxX) / 2.0, (box.minZ + box.maxZ) / 2.0},
                {box.minX + 0.05, box.minZ + 0.05}, {box.maxX - 0.05, box.minZ + 0.05},
                {box.minX + 0.05, box.maxZ - 0.05}, {box.maxX - 0.05, box.maxZ - 0.05}};
        for (double[] point : points) {
            BlockPos pos = BlockPos.containing(point[0], y, point[1]);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Velocity along a ceiling: {@code forward} along the facing (yaw in degrees), {@code left} across it. */
    public static Vec3 ceilingVelocity(double forward, double left, float yaw) {
        double radians = Math.toRadians(yaw);
        Vec3 look = new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
        Vec3 side = new Vec3(look.z, 0.0, -look.x);
        Vec3 move = look.scale(forward).add(side.scale(left)).scale(SIDE_SPEED);
        return new Vec3(move.x, STICK, move.z);
    }

    /** Velocity along a ceiling toward a point, at full speed. */
    public static Vec3 ceilingToward(Vec3 from, Vec3 to) {
        Vec3 way = new Vec3(to.x - from.x, 0.0, to.z - from.z);
        if (way.lengthSqr() < 1.0E-4) {
            return new Vec3(0.0, STICK, 0.0);
        }
        Vec3 move = way.normalize().scale(SIDE_SPEED);
        return new Vec3(move.x, STICK, move.z);
    }

    /**
     * The velocity for hanging on a wall with this normal, moving {@code up} (+1 up, -1 down) and {@code left}
     * (+1 toward the left of someone facing the wall) as fractions of full speed.
     */
    public static Vec3 velocity(Vec3 normal, double up, double left) {
        double vertical = up > 0.0 ? up * UP_SPEED : up * DOWN_SPEED;
        Vec3 along = new Vec3(-normal.z, 0.0, normal.x).scale(left * SIDE_SPEED);
        Vec3 stick = normal.scale(-STICK);
        return new Vec3(along.x + stick.x, vertical, along.z + stick.z);
    }

    /**
     * A velocity along the wall toward a point: the part of the way to it that lies in the wall's plane, at full
     * speed, so a mob crosses the wall by the shortest way it can rather than pressing into it.
     */
    public static Vec3 velocityToward(Vec3 normal, Vec3 from, Vec3 to) {
        Vec3 way = to.subtract(from);
        Vec3 inPlane = way.subtract(normal.scale(way.dot(normal)));
        if (inPlane.lengthSqr() < 1.0E-4) {
            return velocity(normal, 0.0, 0.0);
        }
        Vec3 unit = inPlane.normalize();
        double vertical = unit.y > 0.0 ? unit.y * UP_SPEED : unit.y * DOWN_SPEED;
        double horizontal = Math.sqrt(unit.x * unit.x + unit.z * unit.z) * SIDE_SPEED;
        Vec3 flat = new Vec3(unit.x, 0.0, unit.z);
        flat = flat.lengthSqr() < 1.0E-6 ? Vec3.ZERO : flat.normalize().scale(horizontal);
        Vec3 stick = normal.scale(-STICK);
        return new Vec3(flat.x + stick.x, vertical, flat.z + stick.z);
    }
}
