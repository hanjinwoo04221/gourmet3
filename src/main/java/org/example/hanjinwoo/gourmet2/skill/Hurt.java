package org.example.hanjinwoo.gourmet2.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import org.example.hanjinwoo.gourmet2.Config;
import org.example.hanjinwoo.gourmet2.registry.ModDamageTypes;

/** Damage, knockback and scenery-cutting helpers shared by the skills. */
public final class Hurt {
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

    private static boolean isWeakBlock(BlockState state) {
        return isCuttable(state)
                || state.is(BlockTags.CROPS)
                || state.is(Tags.Blocks.GLASS_BLOCKS)
                || state.is(Tags.Blocks.GLASS_PANES)
                || state.is(Blocks.COBWEB);
    }

    /**
     * Sweeps {@code region} block by block, applying {@link #breakByPower} to each — a corridor of
     * destruction the width of the technique's actual (possibly charge-scaled) size, rather than the
     * single-block-wide line a raw ray would carve. Used every tick by the thrown techniques so what
     * gets destroyed keeps pace with how big the effect actually looks.
     */
    public static void sweepBreak(ServerPlayer caster, int cellLevel, Level level, AABB region) {
        BlockPos min = BlockPos.containing(region.minX, region.minY, region.minZ);
        BlockPos max = BlockPos.containing(region.maxX, region.maxY, region.maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            breakByPower(caster, cellLevel, level, pos);
        }
    }

    /**
     * Breaks {@code pos} for a thrown technique: weak blocks (foliage, glass, cobweb, crops) always
     * give way, and beyond that, anything whose hardness the caster's Gourmet Cell level can punch
     * through (see {@link CellEvolution#blockHardnessCap}) — a fresh caster only manages the weak
     * blocks, an evolved one can punch clean through stone, ores, even obsidian. Unbreakable blocks
     * (bedrock, barriers — negative hardness) are never affected, at any level. Never drops items —
     * a technique punching through a wall isn't meant to be a mining shortcut.
     *
     * @return true if the block was destroyed
     */
    public static boolean breakByPower(ServerPlayer caster, int cellLevel, Level level, BlockPos pos) {
        if (!level.mayInteract(caster, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (Config.cutVegetation && isWeakBlock(state)) {
            level.destroyBlock(pos, false, caster);
            return true;
        }
        if (!Config.cellPowerBreaksBlocks) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0F || state.isAir() || hardness > CellEvolution.blockHardnessCap(cellLevel)) {
            return false;
        }
        level.destroyBlock(pos, false, caster);
        return true;
    }
}
