package org.example.hanjinwoo.gourmet2.skill.impl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.compat.CombatAnimations;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.entity.NailShotEntity;
import org.example.hanjinwoo.gourmet2.fx.FxDispatch;
import org.example.hanjinwoo.gourmet2.fx.SkillFx;
import org.example.hanjinwoo.gourmet2.skill.ActiveSkill;
import org.example.hanjinwoo.gourmet2.skill.Charge;
import org.example.hanjinwoo.gourmet2.skill.Hurt;
import org.example.hanjinwoo.gourmet2.skill.SkillBehavior;
import org.example.hanjinwoo.gourmet2.skill.SkillContext;
import org.example.hanjinwoo.gourmet2.skill.SkillEngine;
import org.example.hanjinwoo.gourmet2.skill.SkillType;

/**
 * ネイルガン Nail Gun — nails driven out on a line of vacuum, one after another. Hold the key to wind the barrage
 * up and let go to fire it: the longer the charge, the more rounds go out, and each one is a
 * {@link NailShotEntity} with the gun's own effect riding it rather than a hitbox swept in front of the caster.
 *
 * <p>Every round is aimed afresh as it leaves, so a barrage can be walked across a crowd; and because they are
 * real projectiles they carry past what the caster looked at, punch through leaves and glass, and reach further
 * with every level evolved (see {@code TorikoData#rangeMultiplier}).
 */
public class NailGunSkill implements SkillBehavior {
    /**
     * Ticks of charge per round of the magazine, whatever its size, so a full charge is the same wait for a small
     * magazine as for a large one — the same shape as the Nail Punch's combo charge. Public because the HUD's charge
     * bar measures itself against it (see {@code ClientTorikoData#chargeProgress}).
     */
    public static final int TICKS_PER_SHOT = 4;
    /** Ticks between rounds once the barrage is going. */
    private static final int SHOT_INTERVAL = 2;
    /** Launch speed of a round, and what one is worth in damage at the base setting. */
    private static final float VELOCITY = 3.0F;
    private static final float BASE_DAMAGE = 3.5F;
    /**
     * Appetite each round beyond the first costs, on top of the charge's share of the base cost. A bigger magazine
     * is a bigger barrage, and the mod charges for every setting that is dialled up.
     */
    private static final int COST_PER_EXTRA_SHOT = 2;

    /** Wind-up cue on key-press; the barrage itself fires from {@link #releaseCharge}. */
    @Override
    public boolean activate(SkillContext ctx) {
        Hurt.playSound(ctx, ctx.player().position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 0.9F);
        return true;
    }

    @Override
    public void releaseCharge(SkillContext ctx, int chargeTicksElapsed) {
        TorikoData data = ctx.data();
        int base = SkillType.NAIL_GUN.appetiteCost();
        // The charge the wallet can actually pay for, and so what the barrage is worth: a full wind-up
        // the player cannot afford fires as a smaller one they can.
        float power = Charge.affordable(data, base, chargeFraction(data, chargeTicksElapsed));
        if (power < 0.0F) {
            return;
        }
        int shots = shotsFor(data, power);
        int cost = costFor(base, power, shots);
        // A barrage the wallet cannot cover in full still goes out — just shorter, rather than not at all.
        while (shots > 1 && !data.canAfford(cost)) {
            shots--;
            cost = costFor(base, power, shots);
        }
        if (!data.canAfford(cost)) {
            return;
        }
        data.spend(cost);
        data.setCooldown(SkillType.NAIL_GUN, SkillEngine.cooldownFor(SkillType.NAIL_GUN, data));
        CombatAnimations.playSkill(ctx.player(), "nail_gun");
        FxDispatch.on(ctx.level(), SkillFx.NAIL_GUN_CAST, ctx.player());

        ActiveSkill active = new ActiveSkill(SkillType.NAIL_GUN, shots * SHOT_INTERVAL, false,
                ctx.lookDirection(), ctx.player().position());
        active.comboHits = shots;
        data.setActive(active);
    }

    /** What the barrage costs: the charge's share of the base, plus a little for every round past the first. */
    private static int costFor(int base, float power, int shots) {
        return Charge.cost(base, power) + Math.max(0, shots - 1) * COST_PER_EXTRA_SHOT;
    }

    /** Ticks a full charge takes: the size of the magazine the caster has dialled in, at a fixed rate. */
    private static int chargeTicks(TorikoData data) {
        return Math.max(1, data.nailGunShotSetting() * TICKS_PER_SHOT);
    }

    /** 0 for a tap, 1 for a full wind-up; how many rounds come out follows this. */
    private static float chargeFraction(TorikoData data, int chargeTicksElapsed) {
        return Math.min(1.0F, Math.max(0, chargeTicksElapsed) / (float) chargeTicks(data));
    }

    /** How many rounds a charge is worth: one for a tap, the whole magazine for a full wind-up. */
    private static int shotsFor(TorikoData data, float fraction) {
        int ceiling = Math.max(1, data.nailGunShotSetting());
        return Math.max(1, Math.min(ceiling, 1 + Math.round(fraction * (ceiling - 1))));
    }

    @Override
    public void tick(SkillContext ctx, ActiveSkill active) {
        if (active.elapsed % SHOT_INTERVAL != 0 || active.hits >= active.comboHits) {
            return;
        }
        active.hits++;
        fire(ctx);
    }

    private void fire(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        // Re-aimed on every round, so a held barrage can be walked across whatever the caster is looking at.
        Vec3 direction = ctx.lookDirection();
        Vec3 spawnAt = ctx.eyePosition().add(direction.scale(0.7));
        float size = ctx.data().flyingSizeSetting();

        NailShotEntity round = new NailShotEntity(ctx.level());
        round.setOwner(player);
        round.setDamage(ctx.damage(BASE_DAMAGE * ctx.data().flyingDamageSetting()));
        round.setSizeScale(size);
        round.moveTo(spawnAt.x, spawnAt.y, spawnAt.z, player.getYRot(), player.getXRot());
        round.setDeltaMovement(direction.scale(VELOCITY * ctx.data().rangeMultiplier()));
        ctx.level().addFreshEntity(round);
        // The gun's effect rides the round itself, anchored like the thrown techniques so it keeps the angle it was
        // fired at — the muzzle flash above is a body-anchored part and would flatten every round to level.
        FxDispatch.on(ctx.level(), SkillFx.NAIL_SHOT_TRAIL, round, size);
        Hurt.playSound(ctx, player.position(), SoundEvents.CROSSBOW_SHOOT, 0.35F, 1.9F);
    }
}
