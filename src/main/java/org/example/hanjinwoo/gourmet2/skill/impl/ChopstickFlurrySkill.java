package org.example.hanjinwoo.gourmet2.skill.impl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.compat.CombatAnimations;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.entity.ChopstickGhostEntity;
import org.example.hanjinwoo.gourmet2.entity.UpheavalEntity;
import org.example.hanjinwoo.gourmet2.skill.ActiveSkill;
import org.example.hanjinwoo.gourmet2.skill.CellEvolution;
import org.example.hanjinwoo.gourmet2.skill.Hurt;
import org.example.hanjinwoo.gourmet2.skill.SkillBehavior;
import org.example.hanjinwoo.gourmet2.skill.SkillContext;
import org.example.hanjinwoo.gourmet2.skill.SkillType;
import org.example.hanjinwoo.gourmet2.skill.Targeting;

/**
 * Chopstick Flurry (Ichiryu) - the caster bends the knees, steps in and jabs over and over, every jab leaving a
 * chopstick afterimage and hurting whatever is in front. Hold the key to charge: the longer the hold, the more
 * jabs (up to the combo ceiling in the power settings, the same dial as the Nail Punch combo).
 *
 * <p>Runs on the shared power settings: the flying damage and size dials, the ranged-reach dial, Cell level /
 * Food Immersion / the attack-damage dial through {@link SkillContext#damage}, and terrain-breaking on every
 * jab.
 */
public class ChopstickFlurrySkill implements SkillBehavior {
    private static final int HIT_INTERVAL = 2;
    private static final double BASE_REACH = 4.5;
    private static final float JAB_DAMAGE = 5.0F;
    private static final float FINISHER_DAMAGE = 12.0F;
    private static final double JAB_RADIUS = 0.9;
    private static final double JITTER = 0.45;
    private static final double BLOW_SPEED = 1.4;
    private static final double IMPACT_AREA = 0.25;
    private static final double STEP_SPEED = 0.55;
    private static final int BASE_COST = 6;
    private static final int COST_PER_JAB = 3;
    private static final int MAX_EXTRA_PER_JAB = 3;
    /** The flurry clip is 24 frames; replayed just before it ends so the arm keeps working for a long flurry. */
    private static final int ANIMATION_TICKS = 18;

    private static int hitsForCharge(TorikoData data, int chargeTicks) {
        int ceiling = data.nailComboSetting();
        int earned = 1 + (int) (Math.max(0, chargeTicks) * CellEvolution.nailChargeRate(ceiling));
        return Mth.clamp(earned, 1, ceiling);
    }

    private static int extraPerJab(TorikoData data) {
        int level = data.cellLevel();
        float damageFraction = CellEvolution.powerFraction(
                data.flyingDamageSetting(), CellEvolution.DAMAGE_MULT_BASE, CellEvolution.damageMultCap(level));
        float sizeFraction = CellEvolution.powerFraction(
                data.flyingSizeSetting(), CellEvolution.SIZE_MULT_BASE, CellEvolution.sizeMultCap(level));
        return Math.round(MAX_EXTRA_PER_JAB * (damageFraction + sizeFraction) / 2.0F);
    }

    private static int costFor(TorikoData data, int hits) {
        return BASE_COST + (COST_PER_JAB + extraPerJab(data)) * hits;
    }

    /** Wind-up cue on key-press; the jabs happen after {@link #releaseCharge}. */
    @Override
    public boolean activate(SkillContext ctx) {
        Hurt.playSound(ctx, ctx.player().position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 0.6F);
        return true;
    }

    @Override
    public void releaseCharge(SkillContext ctx, int chargeTicksElapsed) {
        ServerPlayer player = ctx.player();
        TorikoData data = ctx.data();
        int hits = hitsForCharge(data, chargeTicksElapsed);
        int cost = costFor(data, hits);
        // A charge that outgrew the wallet still goes off, just with as many jabs as can be paid for.
        while (hits > 1 && !data.canAfford(cost)) {
            hits--;
            cost = costFor(data, hits);
        }
        if (!data.canAfford(cost)) {
            return;
        }
        data.spend(cost);

        // Knees bent, the front foot stepped in: the lunge carries the caster forward.
        Vec3 look = ctx.lookDirection();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() > 1.0E-6) {
            player.setDeltaMovement(player.getDeltaMovement().add(flat.normalize().scale(STEP_SPEED)));
            player.hurtMarked = true;
        }
        int duration = hits * HIT_INTERVAL;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration + 2, 1, false, false, false));

        ActiveSkill active = new ActiveSkill(SkillType.CHOPSTICK_FLURRY, duration, false, look, player.position());
        active.comboHits = hits;
        data.setActive(active);
        data.setCooldown(SkillType.CHOPSTICK_FLURRY, SkillType.CHOPSTICK_FLURRY.cooldownTicks()
                + Math.round(SkillType.CHOPSTICK_FLURRY.cooldownTicks() * jabFraction(data, hits)));
        CombatAnimations.playSkill(player, "chopstick_flurry");
    }

    /**
     * Stops a flurry that is under way: the jabs not yet thrown are refunded, the slowdown is lifted and the
     * breather is cut to the skill's ordinary cooldown, so a flurry called off early is not paid for in full.
     */
    public static void cancel(SkillContext ctx, ActiveSkill active) {
        TorikoData data = ctx.data();
        int remaining = Math.max(0, active.comboHits - active.hits);
        data.addAppetite((COST_PER_JAB + extraPerJab(data)) * remaining);
        ctx.player().removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        data.setActive(null);
        data.setCooldown(SkillType.CHOPSTICK_FLURRY, SkillType.CHOPSTICK_FLURRY.cooldownTicks());
        data.markDirty();
    }

    /** How much of the caster's own jab ceiling this flurry spent, 0..1: what the longer breather is drawn from. */
    private static float jabFraction(TorikoData data, int hits) {
        int ceiling = data.nailComboSetting();
        return ceiling <= 1 ? 0.0F : Mth.clamp((hits - 1) / (float) (ceiling - 1), 0.0F, 1.0F);
    }

    @Override
    public void tick(SkillContext ctx, ActiveSkill active) {
        if (active.elapsed % HIT_INTERVAL != 0) {
            return;
        }
        if (active.elapsed > 0 && active.elapsed % ANIMATION_TICKS == 0) {
            CombatAnimations.playSkill(ctx.player(), "chopstick_flurry");
        }
        jab(ctx, active.hits, active.hits >= active.comboHits - 1 && active.comboHits > 1);
        active.hits++;
    }

    private void jab(SkillContext ctx, int index, boolean finisher) {
        ServerPlayer player = ctx.player();
        TorikoData data = ctx.data();
        var random = player.getRandom();
        Vec3 dir = ctx.lookDirection();
        Vec3 right = new Vec3(-dir.z, 0.0, dir.x);
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        float size = data.flyingSizeSetting();
        Vec3 jitter = right.scale((random.nextDouble() - 0.5) * 2.0 * JITTER * size)
                .add(0.0, (random.nextDouble() - 0.5) * 2.0 * JITTER * size, 0.0);
        Vec3 origin = ctx.eyePosition().add(jitter);

        double range = BASE_REACH * data.rangeMultiplier();
        double wall = Targeting.impactPoint(player, dir, range).subtract(ctx.eyePosition()).length();
        double reach = Math.min(range, wall + 0.5);

        float damage = (finisher ? FINISHER_DAMAGE : JAB_DAMAGE) * data.flyingDamageSetting();
        for (LivingEntity victim : Targeting.alongLine(player, origin, dir, reach, JAB_RADIUS * size)) {
            if (Hurt.cut(ctx, victim, damage)) {
                if (finisher) {
                    Hurt.launch(victim, dir.add(0.0, 0.35, 0.0), 1.1);
                    ctx.data().rememberLaunched(victim.getId(), player.tickCount);
                } else {
                    Hurt.knockAway(victim, player.position(), 0.15);
                }
            }
        }

        Vec3 tip = origin.add(dir.scale(reach * 0.75));
        ChopstickGhostEntity.spawn(ctx.level(), tip, dir, size, finisher ? 10 : 6);
        AABB box = AABB.ofSize(tip, size + 0.6, size + 0.6, size + 0.6);
        Hurt.sweepBreak(player, ctx.level(), box, dir.scale(BLOW_SPEED * data.rangeMultiplier()),
                ctx.damage(damage), IMPACT_AREA, UpheavalEntity.Growth.GENTLE);
        Hurt.playSound(ctx, tip, finisher ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_CRIT,
                0.6F, 1.3F + 0.03F * index);
    }
}
