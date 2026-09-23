package org.example.hanjinwoo.gourmet2.skill.impl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.fx.FxDispatch;
import org.example.hanjinwoo.gourmet2.fx.SkillFx;
import org.example.hanjinwoo.gourmet2.registry.ModMobEffects;
import org.example.hanjinwoo.gourmet2.skill.Hurt;
import org.example.hanjinwoo.gourmet2.skill.SkillBehavior;
import org.example.hanjinwoo.gourmet2.skill.SkillContext;
import org.example.hanjinwoo.gourmet2.skill.Targeting;

import java.util.HashSet;
import java.util.Set;

/**
 * フォーク Fork — four stiffened fingers driven forward as a set of prongs. Four parallel stabs, so
 * a target squarely in front takes all four and bleeds badly, while something clipped by the edge
 * of the spread only catches one.
 */
public class ForkSkill implements SkillBehavior {
    private static final double RANGE = 5.0;
    private static final double PRONG_RADIUS = 0.35;
    /** Sideways offsets of the four prongs, in blocks, relative to the caster's right vector. */
    private static final double[] PRONG_OFFSETS = {-1.05, -0.35, 0.35, 1.05};

    private static final float DAMAGE_PER_PRONG = 6.0F;
    private static final int BLEED_DURATION = 140;

    @Override
    public boolean activate(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        Vec3 direction = ctx.lookDirection();
        Vec3 origin = ctx.eyePosition();
        // Right vector in the horizontal plane, so the prongs fan out sideways rather than tumbling.
        Vec3 right = new Vec3(-direction.z, 0.0, direction.x).normalize();
        if (right.lengthSqr() < 1.0E-6) {
            right = new Vec3(1.0, 0.0, 0.0);
        }

        FxDispatch.on(ctx.level(), SkillFx.FORK_CAST, player);
        Hurt.playSound(ctx, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.5F);

        Set<LivingEntity> bled = new HashSet<>();
        for (double offset : PRONG_OFFSETS) {
            Vec3 prongOrigin = origin.add(right.scale(offset));
            for (LivingEntity victim : Targeting.alongRay(player, prongOrigin, direction, RANGE, PRONG_RADIUS)) {
                if (!Hurt.cut(ctx, victim, DAMAGE_PER_PRONG)) {
                    continue;
                }
                FxDispatch.at(ctx.level(), SkillFx.FORK_IMPACT, victim.getBoundingBox().getCenter(), 0.7F, 0.0F, 0.0F);
                // One application of bleeding per victim, however many prongs connected.
                if (bled.add(victim)) {
                    victim.addEffect(new MobEffectInstance(
                            ModMobEffects.bleeding(), BLEED_DURATION, 1, false, true, true));
                    Hurt.knockAway(victim, player.position(), 0.3);
                }
            }
        }
        return true;
    }
}
