package org.example.hanjinwoo.gourmet2.skill.impl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.compat.CombatAnimations;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.entity.LegKnifeSlashEntity;
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
 * レッグナイフ Leg Knife — a kick that throws one crescent of cutting wind. Hold the key to coil the
 * kick: the longer the charge, the bigger and harder-hitting the crescent.
 */
public class LegKnifeSkill implements SkillBehavior {
    private static final float VELOCITY = 2.0F;
    private static final float DAMAGE_MIN = 12.0F;
    private static final float DAMAGE_MAX = 34.0F;
    private static final float SCALE_MIN = 1.0F;
    private static final float SCALE_MAX = 2.6F;
    /** Ticks from the release to the kick's strike frame, where the crescent leaves the foot. */
    private static final int KICK_DELAY = 3;

    /** Wind-up cue on key-press; the kick itself happens in {@link #releaseCharge}. */
    @Override
    public boolean activate(SkillContext ctx) {
        Hurt.playSound(ctx, ctx.player().position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 0.5F);
        return true;
    }

    @Override
    public void releaseCharge(SkillContext ctx, int chargeTicksElapsed) {
        TorikoData data = ctx.data();
        int base = SkillType.LEG_KNIFE.appetiteCost();
        float power = Charge.affordable(data, base, Charge.fraction(chargeTicksElapsed));
        if (power < 0.0F) {
            return;
        }
        data.spend(Charge.cost(base, power));
        data.setCooldown(SkillType.LEG_KNIFE, SkillEngine.cooldownFor(SkillType.LEG_KNIFE, data));
        CombatAnimations.playSkill(ctx.player(), "leg_knife");

        ActiveSkill active = new ActiveSkill(SkillType.LEG_KNIFE, KICK_DELAY + 1, false,
                ctx.lookDirection(), ctx.player().position());
        active.power = power;
        data.setActive(active);
    }

    @Override
    public void tick(SkillContext ctx, ActiveSkill active) {
        if (active.elapsed != KICK_DELAY) {
            return;
        }
        ServerPlayer player = ctx.player();
        float power = active.power;
        float scale = Charge.lerp(SCALE_MIN, SCALE_MAX, power);
        Vec3 direction = ctx.lookDirection();
        // Leaves from about knee height, right in front of the kicking foot.
        Vec3 origin = player.position().add(0.0, 1.0, 0.0).add(direction.scale(0.3));

        FxDispatch.on(ctx.level(), SkillFx.LEG_KNIFE_CAST, player);
        Hurt.playSound(ctx, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.1F, 0.6F - 0.2F * power);

        LegKnifeSlashEntity slash = new LegKnifeSlashEntity(ctx.level());
        slash.setOwner(player);
        slash.setDamage(ctx.damage(Charge.lerp(DAMAGE_MIN, DAMAGE_MAX, power)));
        slash.setSizeScale(scale);
        slash.moveTo(origin.x, origin.y, origin.z, player.getYRot(), player.getXRot());
        slash.setDeltaMovement(direction.scale(VELOCITY));
        ctx.level().addFreshEntity(slash);
        FxDispatch.on(ctx.level(), SkillFx.LEG_KNIFE_SLASH, slash, scale);
    }
}
