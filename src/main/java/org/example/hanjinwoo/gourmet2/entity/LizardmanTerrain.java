package org.example.hanjinwoo.gourmet2.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.skill.Hurt;

/**
 * What the Lizardman does to the ground and the walls around it: measuring a pit, working out the jump that clears
 * it, and the two ways it breaks out when a jump is not enough — a rising punch through the ceiling above, and
 * digging steps into the wall it is trying to climb.
 */
final class LizardmanTerrain {
    /** Toughest block (destroy speed) it will tear out when it is trapped and the impulse maths alone would not. */
    private static final float MAX_ESCAPE_HARDNESS = 8.0F;

    private LizardmanTerrain() {}

    static boolean solid(Level level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** Height in blocks of the solid column one body-width ahead of {@code mob} along the flat direction. */
    static int wallHeight(LizardmanEntity mob, double fx, double fz) {
        double reach = mob.getBbWidth() / 2.0 + 0.6;
        BlockPos base = BlockPos.containing(mob.getX() + fx * reach, mob.getY() + 0.1, mob.getZ() + fz * reach);
        int height = 0;
        while (height < 12 && solid(mob.level(), base.above(height))) {
            height++;
        }
        return height;
    }

    /** Whether the whole body fits past the top of an obstacle {@code wall} blocks high ahead of it. */
    static boolean roofClear(LizardmanEntity mob, double fx, double fz, int wall) {
        double reach = mob.getBbWidth() / 2.0 + 0.6;
        int body = (int) Math.ceil(mob.getBbHeight());
        for (double step : new double[] {reach, reach + 1.0}) {
            BlockPos base = BlockPos.containing(mob.getX() + fx * step, mob.getY() + 0.1, mob.getZ() + fz * step);
            for (int y = wall; y < wall + body; y++) {
                if (solid(mob.level(), base.above(y))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** A solid block within {@code range} blocks over the head (any of the 3x3 columns above it). */
    static boolean ceilingAbove(LizardmanEntity mob, int range) {
        BlockPos head = BlockPos.containing(mob.getX(), mob.getBoundingBox().maxY + 0.05, mob.getZ());
        for (int dy = 0; dy < range; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (solid(mob.level(), head.offset(dx, dy, dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** The upward speed that carries the body just past {@code height} blocks (vanilla gravity and drag). */
    static double jumpVelocityFor(double height) {
        for (double v = 0.42; v < 1.3; v += 0.02) {
            double y = 0.0;
            double vy = v;
            double top = 0.0;
            for (int tick = 0; tick < 80 && vy > 0.0; tick++) {
                y += vy;
                top = Math.max(top, y);
                vy = (vy - 0.08) * 0.98;
            }
            if (top >= height) {
                return v;
            }
        }
        return 1.3;
    }

    /**
     * The rising punch: the fist goes up through what is over the head. The ceiling (a 3x3 patch, four blocks
     * deep) is broken by the same impulse maths every blow in the mod uses and heaved up by an upheaval; when
     * {@code force} is set — the Lizardman is trapped — anything not unbreakable still gives way, so a covered pit
     * can always be punched open.
     */
    static void risePunch(LizardmanEntity mob, boolean force) {
        if (!(mob.level() instanceof ServerLevel level) || !mob.fighter().hasTwin()) {
            return;
        }
        ServerPlayer twin = mob.fighter().twin();
        double damage = mob.getAttributeValue(Attributes.ATTACK_DAMAGE) * mob.fighter().data().attackDamageSetting();
        double speed = Math.max(0.9, Math.abs(mob.getDeltaMovement().y) + 0.4);
        double impulse = Hurt.impulse(new Vec3(0.0, speed, 0.0), damage, Hurt.FIST_AREA);
        BlockPos head = BlockPos.containing(mob.getX(), mob.getBoundingBox().maxY + 0.05, mob.getZ());
        BlockPos first = null;
        for (int dy = 0; dy < 4; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = head.offset(dx, dy, dz);
                    if (!solid(level, pos)) {
                        continue;
                    }
                    if (first == null) {
                        first = pos;
                    }
                    if (!Hurt.breakByImpulse(twin, level, pos, impulse) && force) {
                        breakForced(level, pos, mob);
                    }
                }
            }
        }
        if (first != null) {
            level.playSound(null, first.getX() + 0.5, first.getY() + 0.5, first.getZ() + 0.5,
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8F, 0.7F);
            net.minecraft.world.phys.AABB none = new net.minecraft.world.phys.AABB(first);
            if (!level.getEntitiesOfClass(UpheavalEntity.class, none.inflate(2.0)).iterator().hasNext()) {
                UpheavalEntity.burst(level, twin, first, new Vec3(0.0, 1.0, 0.0), damage, speed, impulse);
            }
        }
    }

    /** Digs a two-high notch into the wall ahead (and one block beyond it) so the next jump has a step to land on. */
    static void dig(LizardmanEntity mob, double fx, double fz) {
        if (!(mob.level() instanceof ServerLevel level) || !mob.fighter().hasTwin()) {
            return;
        }
        ServerPlayer twin = mob.fighter().twin();
        double damage = mob.getAttributeValue(Attributes.ATTACK_DAMAGE) * mob.fighter().data().attackDamageSetting();
        double impulse = Hurt.impulse(new Vec3(fx, 0.0, fz).scale(1.0), damage, Hurt.FIST_AREA) + 1.0;
        for (double ahead = 0.9; ahead <= 2.0; ahead += 1.0) {
            BlockPos base = BlockPos.containing(mob.getX() + fx * ahead, mob.getY() + 0.1, mob.getZ() + fz * ahead);
            for (int dy = 0; dy < 3; dy++) {
                BlockPos pos = base.above(dy);
                if (solid(level, pos) && !Hurt.breakByImpulse(twin, level, pos, impulse)) {
                    breakForced(level, pos, mob);
                }
            }
        }
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.HOSTILE, 0.6F, 0.9F);
    }

    /** Removes a block that is not unbreakable and not absurdly hard. */
    private static void breakForced(ServerLevel level, BlockPos pos, LizardmanEntity mob) {
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness >= 0.0F && hardness <= MAX_ESCAPE_HARDNESS) {
            level.destroyBlock(pos, false, mob);
        }
    }
}
