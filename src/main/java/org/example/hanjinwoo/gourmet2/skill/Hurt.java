package org.example.hanjinwoo.gourmet2.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.Config;
import org.example.hanjinwoo.gourmet2.entity.UpheavalEntity;
import org.example.hanjinwoo.gourmet2.registry.ModDamageTypes;

/** Damage, knockback and terrain-breaking helpers shared by the skills. */
public final class Hurt {
    /**
     * Impulse a blow has to land per point of a block's own hardness to break it (see {@link #impulse}).
     * Hardness is a single small number per block — dirt 0.5, glass 0.3, stone 1.5, iron ore 3, obsidian
     * 50 — so this is the one dial that decides how hard-hitting a technique has to be to punch through
     * what. Raising it leaves only the softest blocks breakable; lowering it opens up the world.
     */
    private static final double IMPULSE_PER_HARDNESS = 20.0;

    /**
     * How much of a blow's bite comes from the speed it was thrown at, out of the whole of it. Kept well
     * under 1: a thrown technique carries mostly speed rather than weight, and one that accelerates over
     * its flight would otherwise scuff a wall on leaving and tear straight through it by the time it
     * arrives. Raising this hands more of the decision to speed; lowering it hands it to the damage.
     */
    private static final double SPEED_WEIGHT = 0.6;

    /**
     * Floor on the area a blow is spread over, in blocks² — roughly the contact patch of a fist. A blow
     * concentrated on less than this still counts as this, so nothing divides its force by almost nothing.
     */
    public static final double FIST_AREA = 0.25;

    private Hurt() {}

    /**
     * A nail punch: mostly blunt drilling damage, plus a fraction that screws straight past armour.
     *
     * @param pierceFraction how much of {@code total} uses the armour-bypassing damage type, 0..1
     * @return true if any of it landed
     */
    public static boolean nail(SkillContext ctx, LivingEntity target, float total, float pierceFraction) {
        float scaled = ctx.damage(total);
        float pierce = scaled * Math.max(0.0F, Math.min(1.0F, pierceFraction));
        float blunt = scaled - pierce;

        boolean landed = false;
        if (blunt > 0.0F) {
            landed = apply(ctx, target, ModDamageTypes.NAIL, blunt);
        }
        if (pierce > 0.0F) {
            landed |= apply(ctx, target, ModDamageTypes.NAIL_PIERCE, pierce);
        }
        return landed;
    }

    /** A clean cut from Knife / Fork / Leg Knife. */
    public static boolean cut(SkillContext ctx, LivingEntity target, float amount) {
        return apply(ctx, target, ModDamageTypes.CUTTING, ctx.damage(amount));
    }

    /** Applies {@code amount} of {@code type}, ignoring the victim's hit-cooldown. */
    public static boolean apply(SkillContext ctx, LivingEntity target, ResourceKey<DamageType> type, float amount) {
        if (amount <= 0.0F) {
            return false;
        }
        // Skills land many hits per tick; vanilla i-frames would silently swallow all but the first.
        target.invulnerableTime = 0;
        return target.hurt(ModDamageTypes.source(ctx.level(), type, ctx.player()), amount);
    }

    /** Pushes {@code target} along {@code direction}, including vertically. */
    public static void launch(LivingEntity target, Vec3 direction, double strength) {
        Vec3 push = direction.normalize().scale(strength);
        target.push(push.x, push.y, push.z);
        target.hurtMarked = true;
    }

    /** Standard horizontal knockback away from the caster. */
    public static void knockAway(LivingEntity target, Vec3 from, double strength) {
        Vec3 away = target.position().subtract(from);
        target.knockback(strength, away.x, away.z);
        target.hurtMarked = true;
    }

    public static void playSound(SkillContext ctx, Vec3 at, SoundEvent sound, float volume, float pitch) {
        ctx.level().playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /**
     * Shears soft scenery (leaves, grass, flowers) out of a box, the way Toriko's cutting
     * techniques clear a swathe through the jungle. No-op unless enabled in the config and the
     * caster may actually build here. Drops nothing — a skill cutting through a tree is not meant
     * to be a farming shortcut.
     */
    public static void cutVegetation(ServerPlayer caster, AABB region) {
        if (!Config.cutVegetation) {
            return;
        }
        Level level = caster.level();
        BlockPos min = BlockPos.containing(region.minX, region.minY, region.minZ);
        BlockPos max = BlockPos.containing(region.maxX, region.maxY, region.maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (!isCuttable(state)) {
                continue;
            }
            if (!level.mayInteract(caster, pos)) {
                continue;
            }
            level.destroyBlock(pos, false, caster);
        }
    }

    private static boolean isCuttable(BlockState state) {
        return state.is(BlockTags.LEAVES)
                || state.is(BlockTags.REPLACEABLE_BY_TREES)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS);
    }

    // ------------------------------------------------------------------- breaking terrain

    /**
     * The impulse a blow lands with: how hard it is driven in, spread over the face it lands on. Force here
     * is {@code speed × damage}, with the speed counted for {@link #SPEED_WEIGHT} of itself — a fast
     * technique hits harder, and a heavier one hits harder — so impulse is that over {@code area}. A fist, a
     * fork prong or the point of a drill concentrates all of it on next to nothing and bites deep; the wide
     * fan of a crescent spreads the same force thin and stops against the same wall. Compare it with the
     * block's own hardness via {@link #breakByImpulse}.
     *
     * @param blow how fast the technique is travelling, and which way
     * @param damage what it does to a body it hits
     * @param area the face it lands on — see {@link #frontalArea}
     */
    public static double impulse(Vec3 blow, double damage, double area) {
        return blow.length() * SPEED_WEIGHT * damage / Math.max(FIST_AREA, area);
    }

    /**
     * How much of {@code box} is turned toward a blow travelling along {@code direction} — the area the
     * force is actually spread over, being the hitbox projected onto the plane it lands on. A needle hitbox
     * driven point-first barely spreads it; the same hitbox caught broadside spreads it the whole way.
     */
    public static double frontalArea(AABB box, Vec3 direction) {
        double sx = box.getXsize();
        double sy = box.getYsize();
        double sz = box.getZsize();
        double length = direction.length();
        if (length < 1.0E-6) {
            // Not going anywhere: treat it as landing flat, on whichever face is the smallest.
            return Math.min(Math.min(sx * sy, sy * sz), sx * sz);
        }
        // Each axis of the hitbox spreads over the other two, in proportion to how square-on it is.
        return Math.abs(direction.x) / length * sy * sz
                + Math.abs(direction.y) / length * sx * sz
                + Math.abs(direction.z) / length * sx * sy;
    }

    /**
     * Breaks the single block a technique has run into, if the blow was driven hard enough for it — the
     * block a thrown knife, fork or crescent stops against (see {@link #impulse}).
     *
     * @param area the face the blow lands on; see {@link #frontalArea}
     * @return true if the block was destroyed and the technique should carry on through it
     */
    public static boolean breakThrough(ServerPlayer caster, Level level, BlockPos pos, Vec3 blow, double damage,
            double area) {
        return breakByImpulse(caster, level, pos, impulse(blow, damage, area));
    }

    /**
     * Breaks {@code pos} when the blow lands more impulse on it than the block's own hardness can take.
     * Every block hands over one hardness number, so this is a single rule for the whole world: dirt and
     * glass go down to almost any technique, stone needs a real one, iron ore more, and obsidian takes a
     * genuine monster. Hardness is what is left of the old per-level cap — it no longer cares how evolved
     * the caster is, only how hard they actually hit. Unbreakable blocks (bedrock, barriers — negative
     * hardness) never give at any impulse, and fluids report a hardness of their own that nothing reaches.
     * Never drops items — a technique punching through a wall isn't meant to be a mining shortcut.
     *
     * @return true if the block was destroyed
     */
    public static boolean breakByImpulse(ServerPlayer caster, Level level, BlockPos pos, double impulse) {
        if (!givesWay(caster, level, pos, impulse)) {
            return false;
        }
        level.destroyBlock(pos, false, caster);
        return true;
    }

    /**
     * Whether {@code pos} would give way to that much impulse — the same test {@link #breakByImpulse} makes,
     * for callers that need the answer <i>before</i> the block goes. The upheaval asks it while it is planning
     * a crater, so each block of it is either broken where it stands or only heaved and put back, decided the
     * moment the crater is laid out rather than while the wave is crossing it.
     *
     * <p>A null caster — a blow whose owner has since left — breaks nothing: who may build here is not
     * something a stray effect can answer.
     *
     * @return true if the block is there to be broken and the blow is worth it
     */
    public static boolean givesWay(ServerPlayer caster, Level level, BlockPos pos, double impulse) {
        if (!Config.cellPowerBreaksBlocks || caster == null || !level.mayInteract(caster, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, pos);
        return hardness >= 0.0F && impulse >= hardness * IMPULSE_PER_HARDNESS;
    }

    /**
     * Sweeps {@code region} block by block, applying {@link #breakByImpulse} to each — a corridor of
     * destruction the width of the technique's actual (possibly charge-scaled) size, rather than the
     * single-block-wide line a raw ray would carve. Used every tick by the thrown techniques so what
     * gets destroyed keeps pace with how big the effect actually looks.
     *
     * @param blow how fast the technique is travelling, and which way — the impact's speed and direction
     * @param damage what it does to a body it hits
     * @param area the face it lands on (see {@link #frontalArea}); the wider, the shallower it bites
     */
    public static void sweepBreak(ServerPlayer caster, Level level, AABB region, Vec3 blow, double damage,
            double area) {
        sweepBreak(caster, level, region, blow, damage, area, UpheavalEntity.Growth.DEFAULT);
    }

    /** As {@link #sweepBreak(ServerPlayer, Level, AABB, Vec3, double, double)}, with its own upheaval growth rates. */
    public static void sweepBreak(ServerPlayer caster, Level level, AABB region, Vec3 blow, double damage,
            double area, UpheavalEntity.Growth growth) {
        double impulse = impulse(blow, damage, area);
        BlockPos min = BlockPos.containing(region.minX, region.minY, region.minZ);
        BlockPos max = BlockPos.containing(region.maxX, region.maxY, region.maxZ);
        boolean broke = false;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            broke |= breakByImpulse(caster, level, pos, impulse);
        }
        if (broke && level instanceof ServerLevel server) {
            // A technique tears the ground up as well as through it: the same burst a blow leaves, scaled by
            // how much damage the technique carries and how fast it was thrown, and skipped when that is not
            // enough for one. Weighed the same way, so the crater it leaves is broken through under the very
            // impulse that just swept the corridor.
            UpheavalEntity.burst(server, caster, BlockPos.containing(region.getCenter()), blow, damage,
                    blow.length(), impulse, growth);
        }
    }
}
