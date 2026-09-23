package org.example.hanjinwoo.gourmet2.skill.impl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.compat.CombatAnimations;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.entity.FlyingForkEntity;
import org.example.hanjinwoo.gourmet2.fx.FxDispatch;
import org.example.hanjinwoo.gourmet2.fx.SkillFx;
import org.example.hanjinwoo.gourmet2.skill.CellEvolution;
import org.example.hanjinwoo.gourmet2.skill.Hurt;
import org.example.hanjinwoo.gourmet2.skill.SkillBehavior;
import org.example.hanjinwoo.gourmet2.skill.SkillContext;
import org.example.hanjinwoo.gourmet2.skill.SkillEngine;
import org.example.hanjinwoo.gourmet2.skill.SkillType;

/**
 * フライングフォーク Flying Fork — a vacuum prong thrown at whatever the caster is looking at. Hold the
 * key to keep firing prongs, one after another, for as long as it stays held; release to stop. Each
 * prong's damage and size come straight from the power settings GUI; the fire rate speeds up as the
 * player's Gourmet Cells evolve (see the same GUI). Stronger settings drain Appetite faster, since
 * every shot costs more the more the caster has dialled themselves up.
 */
public class FlyingForkSkill implements SkillBehavior {
    private static final float VELOCITY = 2.2F;
    private static final float BASE_DAMAGE = 7.0F;
    /** Ticks between shots at the base fire rate (fork count setting at its level-0 floor). */
    private static final int BASE_INTERVAL = 8;
    private static final int MIN_INTERVAL = 3;
    private static final int PER_SHOT_COST = 4;
    /** Extra Appetite a shot costs when damage and size are both dialled to their current cap. */
    private static final int MAX_EXTRA_PER_SHOT = 6;

    /** Wind-up cue on key-press; shots are fired from {@link #tickHeld} for as long as the key stays down. */
    @Override
    public boolean activate(SkillContext ctx) {
        Hurt.playSound(ctx, ctx.player().position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 0.7F);
        return true;
    }

    /** Faster the more the fire-rate setting is dialled up (see the power settings GUI). */
    private int fireInterval(TorikoData data) {
        int steps = data.forkProjectileSetting() - CellEvolution.FORK_PROJECTILE_BASE;
        return Math.max(MIN_INTERVAL, BASE_INTERVAL - steps);
    }

    private int shotCost(TorikoData data) {
        int level = data.cellLevel();
        float damageFraction = CellEvolution.powerFraction(
                data.flyingDamageSetting(), CellEvolution.DAMAGE_MULT_BASE, CellEvolution.damageMultCap(level));
        float sizeFraction = CellEvolution.powerFraction(
                data.flyingSizeSetting(), CellEvolution.SIZE_MULT_BASE, CellEvolution.sizeMultCap(level));
        float power = (damageFraction + sizeFraction) / 2.0F;
        return PER_SHOT_COST + Math.round(MAX_EXTRA_PER_SHOT * power);
    }

    @Override
    public void tickHeld(SkillContext ctx, int ticksHeld) {
        TorikoData data = ctx.data();
        if (ticksHeld % fireInterval(data) != 0) {
            return;
        }
        int cost = shotCost(data);
        if (!data.canAfford(cost)) {
            // Not enough left for another shot; the stream just goes quiet until the key is released
            // (or Appetite regenerates enough for the next scheduled shot).
            return;
        }
        fire(ctx, data);
        data.spend(cost);
    }

    private void fire(SkillContext ctx, TorikoData data) {
        ServerPlayer player = ctx.player();
        // Re-aimed on every shot, so a held trigger can be swept across a target.
        Vec3 direction = ctx.lookDirection();
        Vec3 spawnAt = ctx.eyePosition().add(direction.scale(0.6));
        float damage = BASE_DAMAGE * data.flyingDamageSetting();
        float size = data.flyingSizeSetting();

        CombatAnimations.playSkill(player, "flying_fork_shot");
        FxDispatch.on(ctx.level(), SkillFx.FLYING_FORK_CAST, player);
        Hurt.playSound(ctx, player.position(), SoundEvents.TRIDENT_THROW.value(), 0.8F, 1.4F);

        FlyingForkEntity prong = new FlyingForkEntity(ctx.level());
        prong.setOwner(player);
        prong.setDamage(ctx.damage(damage));
        prong.setSizeScale(size);
        prong.moveTo(spawnAt.x, spawnAt.y, spawnAt.z, player.getYRot(), player.getXRot());
        prong.setDeltaMovement(direction.scale(VELOCITY));
        ctx.level().addFreshEntity(prong);
        FxDispatch.on(ctx.level(), SkillFx.FLYING_FORK_TRAIL, prong, size);
    }

    @Override
    public void stopHeld(SkillContext ctx) {
        ctx.data().setCooldown(SkillType.FLYING_FORK, SkillEngine.cooldownFor(SkillType.FLYING_FORK, ctx.data()));
    }
}
