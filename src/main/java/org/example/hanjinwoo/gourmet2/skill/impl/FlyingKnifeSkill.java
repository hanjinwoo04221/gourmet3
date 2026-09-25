package org.example.hanjinwoo.gourmet2.skill.impl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.compat.CombatAnimations;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.entity.FlyingKnifeEntity;
import org.example.hanjinwoo.gourmet2.fx.FxDispatch;
import org.example.hanjinwoo.gourmet2.fx.SkillFx;
import org.example.hanjinwoo.gourmet2.skill.CellEvolution;
import org.example.hanjinwoo.gourmet2.skill.Hurt;
import org.example.hanjinwoo.gourmet2.skill.SkillBehavior;
import org.example.hanjinwoo.gourmet2.skill.SkillContext;
import org.example.hanjinwoo.gourmet2.skill.SkillEngine;
import org.example.hanjinwoo.gourmet2.skill.SkillType;

/**
 * フライングナイフ Flying Knife — a crescent cutting wave thrown at whatever the caster is looking at.
 * Hold the key to keep firing waves, one after another, for as long as it stays held; release to
 * stop. Each wave's damage and size come straight from the power settings GUI; the fire rate speeds
 * up as the player's Gourmet Cells evolve (see the same GUI). Stronger settings drain Appetite
 * faster, since every shot costs more the more the caster has dialled themselves up.
 */
public class FlyingKnifeSkill implements SkillBehavior {
    private static final float VELOCITY = 1.7F;
    private static final float BASE_DAMAGE = 20.0F;
    /** Ticks between shots at the base fire rate (wave count setting at its level-0 floor). */
    private static final int BASE_INTERVAL = 10;
    private static final int MIN_INTERVAL = 4;
    private static final int PER_SHOT_COST = 5;
    /** Extra Appetite a shot costs when damage and size are both dialled to their current cap. */
    private static final int MAX_EXTRA_PER_SHOT = 7;

    /** Wind-up cue on key-press; shots are fired from {@link #tickHeld} for as long as the key stays down. */
    @Override
    public boolean activate(SkillContext ctx) {
        Hurt.playSound(ctx, ctx.player().position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 0.6F);
        return true;
    }

    /** Faster the more the fire-rate setting is dialled up (see the power settings GUI). */
    private int fireInterval(TorikoData data) {
        int steps = data.knifeWaveSetting() - CellEvolution.KNIFE_WAVE_BASE;
        return Math.max(MIN_INTERVAL, BASE_INTERVAL - steps * 2);
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
            return;
        }
        fire(ctx, data);
        data.spend(cost);
    }

    private void fire(SkillContext ctx, TorikoData data) {
        ServerPlayer player = ctx.player();
        Vec3 direction = ctx.lookDirection();
        Vec3 spawnAt = ctx.eyePosition().add(direction.scale(0.8));
        float damage = BASE_DAMAGE * data.flyingDamageSetting();
        float size = data.flyingSizeSetting();

        CombatAnimations.playSkill(player, "flying_knife_shot");
        FxDispatch.on(ctx.level(), SkillFx.FLYING_KNIFE_CAST, player);
        Hurt.playSound(ctx, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.5F);

        FlyingKnifeEntity wave = new FlyingKnifeEntity(ctx.level());
        wave.setOwner(player);
        wave.setDamage(ctx.damage(damage));
        wave.setSizeScale(size);
        wave.moveTo(spawnAt.x, spawnAt.y, spawnAt.z, player.getYRot(), player.getXRot());
        // Thrown harder with every level evolved (and held back by the reach dial): it is the launch speed that
        // carries the throw further, since these waves gather pace as they fly and a longer flight would run away
        // with the distance rather than add to it.
        wave.setDeltaMovement(direction.scale(VELOCITY * data.rangeMultiplier()));
        ctx.level().addFreshEntity(wave);
        FxDispatch.on(ctx.level(), SkillFx.FLYING_KNIFE_TRAIL, wave, size);
    }

    @Override
    public void stopHeld(SkillContext ctx) {
        ctx.data().setCooldown(SkillType.FLYING_KNIFE, SkillEngine.cooldownFor(SkillType.FLYING_KNIFE, ctx.data()));
    }
}
