package org.example.hanjinwoo.gourmet2.skill.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.compat.CombatAnimations;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.entity.ShockwaveRingEntity;
import org.example.hanjinwoo.gourmet2.entity.UpheavalEntity;
import org.example.hanjinwoo.gourmet2.fx.FxDispatch;
import org.example.hanjinwoo.gourmet2.fx.SkillFx;
import org.example.hanjinwoo.gourmet2.skill.ActiveSkill;
import org.example.hanjinwoo.gourmet2.skill.CellEvolution;
import org.example.hanjinwoo.gourmet2.skill.Hurt;
import org.example.hanjinwoo.gourmet2.skill.SkillBehavior;
import org.example.hanjinwoo.gourmet2.skill.SkillContext;
import org.example.hanjinwoo.gourmet2.skill.SkillEngine;
import org.example.hanjinwoo.gourmet2.skill.SkillType;
import org.example.hanjinwoo.gourmet2.skill.Targeting;
import org.jetbrains.annotations.Nullable;

/**
 * 釘パンチ Nail Punch — Toriko's bread and butter, screwing a drilling fist straight through
 * armour. This is the charge-and-release merge of what used to be separate "Nail Punch" and
 * "13-Hit Nail Punch" skills: a quick tap throws one punch (the old Nail Punch), while holding the
 * key winds up a target-locked ramping combo (the old Thirteen-Hit Nail Punch), landing more hits
 * the longer it was held — up to the player's cell-evolution-capped ceiling
 * ({@link org.example.hanjinwoo.gourmet2.skill.CellEvolution#nailComboCap}).
 */
public class NailPunchSkill implements SkillBehavior {
    /** Ticks between hits once a combo is actually landing. */
    private static final int HIT_INTERVAL = 2;

    private static final double SINGLE_RANGE = 4.5;
    private static final float SINGLE_DAMAGE = 15.0F;
    private static final float SINGLE_PIERCE_FRACTION = 0.45F;
    private static final double SINGLE_KNOCKBACK = 0.95;
    private static final int SINGLE_COST = 8;

    private static final double LOCK_RANGE = 5.0;
    /** Each this much armour on the target costs the combo one hit. */
    private static final double ARMOR_PER_LOST_HIT = 4.0;
    private static final float COMBO_FIRST_HIT_DAMAGE = 3.0F;
    private static final float COMBO_DAMAGE_RAMP = 0.35F;
    private static final float COMBO_FINISHER_DAMAGE = 16.0F;
    private static final float COMBO_PIERCE_FRACTION = 0.5F;
    private static final double FINISHER_KNOCKBACK = 1.9;
    /** Appetite per hit in a combo; the finisher hit costs extra on top. */
    private static final int COST_PER_HIT = 4;
    private static final int FINISHER_COST = 10;

    /**
     * What a combo costs in breathing room and in mobility, as a share of the skill's own cooldown and as a
     * Slowness level, both reached only at a <i>full</i> combo — one that spends every hit the caster's own
     * ceiling allows. Committing to a long combo is a commitment, not a punishment: the ceiling is a dial the
     * player set, so a punch that uses all of it is doing what it was tuned to do.
     */
    private static final float MAX_COOLDOWN_SHARE = 1.0F;
    private static final int PLANT_SLOWNESS = 1;
    private static final int PLANT_SLOWNESS_RAMP = 2;

    /**
     * How many hits a hold of {@code chargeTicksElapsed} has earned. The wind-up runs at a rate set by the
     * combo ceiling ({@link CellEvolution#nailChargeRate}), so the same wait buys proportionally more hits the
     * longer a combo is dialled in, and a full combo always takes the same wait to build — holding out for a
     * 20-hit punch is not twenty times the wait of a single one, it is the same wait spent twenty times as fast.
     */
    private static int hitsForCharge(TorikoData data, int chargeTicksElapsed) {
        int ceiling = data.nailComboSetting();
        int earned = 1 + (int) (Math.max(0, chargeTicksElapsed) * CellEvolution.nailChargeRate(ceiling));
        return Mth.clamp(earned, 1, ceiling);
    }

    private static int costForHits(int hits) {
        return hits <= 1 ? SINGLE_COST : COST_PER_HIT * hits + FINISHER_COST;
    }

    /** Wind-up cue on key-press. The actual punch(es) happen in {@link #releaseCharge}. */
    @Override
    public boolean activate(SkillContext ctx) {
        FxDispatch.on(ctx.level(), SkillFx.NAIL_PUNCH_CAST, ctx.player());
        Hurt.playSound(ctx, ctx.player().position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 0.5F);
        return true;
    }

    @Override
    public void releaseCharge(SkillContext ctx, int chargeTicksElapsed) {
        TorikoData data = ctx.data();
        int hits = hitsForCharge(data, chargeTicksElapsed);
        int cost = costForHits(hits);
        // A charge that outgrew the wallet still fires - just at however many hits are affordable,
        // rather than the whole punch fizzling after being held and paid for with nothing to show.
        while (hits > 1 && !data.canAfford(cost)) {
            hits--;
            cost = costForHits(hits);
        }
        if (!data.canAfford(cost)) {
            return;
        }

        LivingEntity target = null;
        if (hits > 1) {
            target = Targeting.lockOn(ctx.player(), LOCK_RANGE);
            if (target == null) {
                // No target to combo into - fall back to the single punch so charging never whiffs
                // into literally nothing.
                hits = 1;
            } else {
                int planned = hits;
                // Armour soaks up hits: the more the target wears, the shorter the combo.
                hits = Math.max(1, hits - (int) (target.getArmorValue() / ARMOR_PER_LOST_HIT));
                if (hits < planned) {
                    ctx.player().displayClientMessage(Component.translatable(
                            "message." + Gourmet2.MODID + ".armor_reduced_combo", planned, hits), true);
                }
            }
            cost = costForHits(hits);
        }

        if (hits <= 1) {
            singlePunch(ctx);
        } else {
            startCombo(ctx, hits, target);
        }

        CombatAnimations.playSkill(ctx.player(), "nail_punch");
        data.spend(cost);
        data.setCooldown(SkillType.NAIL_PUNCH, cooldownFor(ctx, hits));
    }

    /**
     * How much of the caster's own combo ceiling this punch spent: 0 for a single blow, 1 for every hit they
     * had dialled in. This, rather than the raw hit count, is what the costs of committing are drawn from —
     * 20 hits is a real combo at a ceiling of 20 and almost nothing at a ceiling of 200, and what should be
     * paid for is how much of what they have they spent, not how big the number is.
     */
    private static float spentFraction(SkillContext ctx, int hits) {
        int ceiling = ctx.data().nailComboSetting();
        return ceiling <= 1 ? 0.0F : Mth.clamp((hits - 1) / (float) (ceiling - 1), 0.0F, 1.0F);
    }

    /**
     * The breather a punch earns: the skill's own cooldown, up to twice that for one that spent the whole
     * ceiling. Deliberately mild — a long combo no longer scales it up hit by hit, which at a high ceiling
     * used to put the skill out of action for ten seconds at a time.
     */
    private int cooldownFor(SkillContext ctx, int hits) {
        int base = SkillType.NAIL_PUNCH.cooldownTicks();
        return base + Math.round(base * MAX_COOLDOWN_SHARE * spentFraction(ctx, hits));
    }

    // --------------------------------------------------------------- single punch (no charge)

    private void singlePunch(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        Vec3 direction = ctx.lookDirection();

        LivingEntity target = Targeting.lockOn(player, SINGLE_RANGE);
        Vec3 impact = target != null
                ? target.getBoundingBox().getCenter()
                : Targeting.impactPoint(player, direction, SINGLE_RANGE);
        FxDispatch.at(ctx.level(), SkillFx.NAIL_PUNCH_IMPACT, impact, 1.0F, player.getYRot(), player.getXRot());

        if (target != null && Hurt.nail(ctx, target, SINGLE_DAMAGE, SINGLE_PIERCE_FRACTION)) {
            Vec3 knockDirection = direction.add(0.0, 0.22, 0.0);
            Hurt.launch(target, knockDirection, SINGLE_KNOCKBACK);
            hitTerrain(ctx, target, knockDirection, SINGLE_KNOCKBACK, SINGLE_DAMAGE);
            Hurt.playSound(ctx, impact, SoundEvents.ANVIL_LAND, 0.5F, 1.7F);
        }
    }

    /**
     * The terrain side of one landed hit: the ground the victim is on and whatever they are pressed into comes
     * apart, and the ground is thrown up (see {@link UpheavalEntity}). Called for <i>every</i> hit of a combo,
     * not only the first and the last, so a long combo keeps breaking up the patch the target is being driven
     * into. Note what gets through is the fist, not the body it is carrying: the blow is measured over a fist's
     * contact patch, so a nail punch bites far deeper than a crescent of the same damage, which spreads the same
     * force over its whole fanned face. The knockback's strength is the speed of the blow, and harder knockback
     * reaches further — which is also what decides what the blow is worth against the blocks it hits.
     */
    private void hitTerrain(SkillContext ctx, LivingEntity target, Vec3 knockDirection, double strength,
            float damage) {
        Vec3 dir = knockDirection.lengthSqr() > 1.0E-6 ? knockDirection.normalize() : ctx.lookDirection();
        // What the victim is up against, rather than only what is behind them: their own block, the ground they
        // are standing on and the wall they are pressed into are the blocks a blow actually lands on.
        AABB contact = target.getBoundingBox().expandTowards(dir.scale(strength * 2.0)).inflate(0.5);
        float blow = ctx.damage(damage);
        // The ground comes up where the victim is, whether or not anything gave way: a punch the hardness beats
        // is still a punch, and the burst is what makes one hit read as a hit. Bursts do not stack on each other
        // (see UpheavalEntity), so a fast combo churns the same patch instead of carpeting it.
        UpheavalEntity.burst((ServerLevel) ctx.level(), BlockPos.containing(contact.getCenter()), dir, blow, strength);
        Hurt.sweepBreak(ctx.player(), ctx.level(), contact, dir.scale(strength), blow, Hurt.FIST_AREA);
    }

    // ------------------------------------------------------------------------- combo (charged)

    /**
     * The first hit locks the target in; the remaining hits then keep landing on it wherever it goes,
     * however far the earlier ones knock it.
     */
    private void startCombo(SkillContext ctx, int hits, LivingEntity target) {
        ServerPlayer player = ctx.player();

        FxDispatch.on(ctx.level(), SkillFx.THIRTEEN_LOCK, player);
        FxDispatch.on(ctx.level(), SkillFx.THIRTEEN_LOCK, target, 0.8F);
        Hurt.playSound(ctx, player.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.6F);

        int duration = hits * HIT_INTERVAL;
        // Committing to the combo plants the caster in place for as long as it takes to land, and the further
        // into their ceiling they went the deeper the plant — but a full combo is a plant, not a burial, so
        // even the worst of it is a Slowness a player can still walk (and fight) through.
        int plant = PLANT_SLOWNESS + Math.round(PLANT_SLOWNESS_RAMP * spentFraction(ctx, hits));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration + 2, plant, false, false, false));

        ActiveSkill active = new ActiveSkill(SkillType.NAIL_PUNCH, duration, false, ctx.lookDirection(), player.position());
        active.targetId = target.getId();
        active.comboHits = hits;
        ctx.data().setActive(active);
    }

    @Override
    public void tick(SkillContext ctx, ActiveSkill active) {
        if (active.comboHits <= 1 || active.elapsed % HIT_INTERVAL != 0) {
            return;
        }
        LivingEntity target = resolveTarget(ctx, active);
        if (target == null) {
            // The victim died or left the world; stop early rather than punching empty air.
            active.elapsed = active.totalTicks;
            return;
        }

        int hitIndex = active.hits;
        boolean finisher = hitIndex >= active.comboHits - 1;
        Vec3 center = target.getBoundingBox().getCenter();

        if (finisher) {
            if (Hurt.nail(ctx, target, COMBO_FINISHER_DAMAGE, COMBO_PIERCE_FRACTION)) {
                Vec3 knockDirection = ctx.lookDirection().add(0.0, 0.55, 0.0);
                Hurt.launch(target, knockDirection, FINISHER_KNOCKBACK);
                hitTerrain(ctx, target, knockDirection, FINISHER_KNOCKBACK, COMBO_FINISHER_DAMAGE);
            }
            FxDispatch.at(ctx.level(), SkillFx.THIRTEEN_FINISH, center, 1.4F, ctx.player().getYRot(), 0.0F);
            spawnRing(ctx, target);
            Hurt.playSound(ctx, center, SoundEvents.GENERIC_EXPLODE.value(), 0.9F, 1.4F);
        } else {
            float damage = COMBO_FIRST_HIT_DAMAGE + COMBO_DAMAGE_RAMP * hitIndex;
            if (Hurt.nail(ctx, target, damage, COMBO_PIERCE_FRACTION)) {
                // Every hit of the combo lands on the ground as well as on the victim, so a long combo keeps
                // breaking up what the target is being driven into instead of marking it once and stopping.
                // The fist is the same fist each time, so the blow's strength is the single punch's; what ramps
                // is the damage behind it, and the terrain it can get through ramps with it.
                hitTerrain(ctx, target, ctx.lookDirection(), SINGLE_KNOCKBACK, damage);
            }
            // Scale the impact with the ramp so the combo visibly builds.
            float scale = 0.45F + 0.05F * hitIndex;
            FxDispatch.at(ctx.level(), SkillFx.THIRTEEN_IMPACT, center, scale, 0.0F, 0.0F);
            Hurt.playSound(ctx, center, SoundEvents.PLAYER_ATTACK_CRIT, 0.5F, 1.2F + 0.05F * hitIndex);
        }
        active.hits++;
    }

    private @Nullable LivingEntity resolveTarget(SkillContext ctx, ActiveSkill active) {
        if (active.targetId < 0) {
            return null;
        }
        Entity entity = ctx.level().getEntity(active.targetId);
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            return null;
        }
        return living;
    }

    private void spawnRing(SkillContext ctx, LivingEntity target) {
        ShockwaveRingEntity ring = new ShockwaveRingEntity(ctx.level());
        ring.moveTo(target.getX(), target.getY() + 0.1, target.getZ(), ctx.player().getYRot(), 0.0F);
        ctx.level().addFreshEntity(ring);
    }
}
