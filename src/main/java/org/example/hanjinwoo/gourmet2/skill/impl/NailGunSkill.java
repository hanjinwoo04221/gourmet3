package org.example.hanjinwoo.gourmet2.skill.impl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.fx.FxDispatch;
import org.example.hanjinwoo.gourmet2.fx.SkillFx;
import org.example.hanjinwoo.gourmet2.skill.ActiveSkill;
import org.example.hanjinwoo.gourmet2.skill.Hurt;
import org.example.hanjinwoo.gourmet2.skill.SkillBehavior;
import org.example.hanjinwoo.gourmet2.skill.SkillContext;
import org.example.hanjinwoo.gourmet2.skill.SkillType;
import org.example.hanjinwoo.gourmet2.skill.Targeting;

import java.util.List;

/**
 * ネイルガン Nail Gun — nail punches thrown fast enough to be a machine gun. Sprays a forward cone
 * for a second and a bit, so it clears a crowd rather than deleting one target.
 *
 * <p>Unlike the single Nail Punch this keeps tracking: each volley re-reads where the player is
 * looking, which is what makes it feel like sweeping fire.
 */
public class NailGunSkill implements SkillBehavior {
    private static final int DURATION = 24;
    /** Ticks between volleys. */
    private static final int VOLLEY_INTERVAL = 2;
    private static final double RANGE = 9.0;
    private static final double HALF_ANGLE = 30.0;
    private static final int TARGETS_PER_VOLLEY = 2;
    private static final float DAMAGE_PER_HIT = 3.5F;
    private static final float PIERCE_FRACTION = 0.3F;

    @Override
    public boolean activate(SkillContext ctx) {
        FxDispatch.on(ctx.level(), SkillFx.NAIL_GUN_CAST, ctx.player());
        Hurt.playSound(ctx, ctx.player().position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 1.2F);
        return true;
    }

    @Override
    public ActiveSkill startActive(SkillContext ctx) {
        return new ActiveSkill(SkillType.NAIL_GUN, DURATION, false,
                ctx.lookDirection(), ctx.player().position());
    }

    @Override
    public void tick(SkillContext ctx, ActiveSkill active) {
        if (active.elapsed % VOLLEY_INTERVAL != 0) {
            return;
        }
        ServerPlayer player = ctx.player();
        Vec3 direction = ctx.lookDirection();

        List<LivingEntity> victims = Targeting.inCone(player, direction, RANGE, HALF_ANGLE, TARGETS_PER_VOLLEY);
        if (victims.isEmpty()) {
            // Still show the rounds landing on terrain so the burst reads as continuous fire.
            FxDispatch.at(ctx.level(), SkillFx.NAIL_GUN_IMPACT,
                    Targeting.impactPoint(player, direction, RANGE), 0.5F, player.getYRot(), player.getXRot());
        } else {
            for (LivingEntity victim : victims) {
                if (Hurt.nail(ctx, victim, DAMAGE_PER_HIT, PIERCE_FRACTION)) {
                    Hurt.knockAway(victim, player.position(), 0.18);
                    FxDispatch.at(ctx.level(), SkillFx.NAIL_GUN_IMPACT, victim.getBoundingBox().getCenter(), 0.6F, 0.0F, 0.0F);
                }
            }
        }
        Hurt.playSound(ctx, player.position(), SoundEvents.CROSSBOW_SHOOT, 0.35F, 1.9F);

        // Re-trigger the muzzle flash periodically; most Effekseer bursts are shorter than the barrage.
        if (active.elapsed % 8 == 0) {
            FxDispatch.on(ctx.level(), SkillFx.NAIL_GUN_CAST, player, 0.8F);
        }
    }
}
