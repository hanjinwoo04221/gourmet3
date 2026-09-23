package org.example.hanjinwoo.gourmet2.skill.impl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.fx.FxDispatch;
import org.example.hanjinwoo.gourmet2.fx.SkillFx;
import org.example.hanjinwoo.gourmet2.skill.Hurt;
import org.example.hanjinwoo.gourmet2.skill.SkillBehavior;
import org.example.hanjinwoo.gourmet2.skill.SkillContext;
import org.example.hanjinwoo.gourmet2.skill.Targeting;

/**
 * ナイフ Knife — the hand held flat and swept through everything in front of the caster. Cuts
 * rather than bludgeons: it hits a wide arc, shears the undergrowth, and hits everything it
 * touches for full damage.
 */
public class KnifeSkill implements SkillBehavior {
    private static final double RANGE = 5.0;
    private static final double HALF_ANGLE = 55.0;
    private static final int MAX_TARGETS = 8;
    private static final float DAMAGE = 18.0F;

    @Override
    public boolean activate(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        Vec3 direction = ctx.lookDirection();

        FxDispatch.on(ctx.level(), SkillFx.KNIFE_CAST, player);
        Hurt.playSound(ctx, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.9F);

        for (LivingEntity victim : Targeting.inCone(player, direction, RANGE, HALF_ANGLE, MAX_TARGETS)) {
            if (!Hurt.cut(ctx, victim, DAMAGE)) {
                continue;
            }
            Hurt.knockAway(victim, player.position(), 0.45);
            FxDispatch.at(ctx.level(), SkillFx.KNIFE_IMPACT, victim.getBoundingBox().getCenter(),
                    0.9F, player.getYRot(), 0.0F);
        }

        // The sweep clears a swathe of leaves and brush, the way Toriko carves through a jungle.
        Hurt.cutVegetation(player, player.getBoundingBox()
                .inflate(1.0)
                .expandTowards(direction.scale(RANGE)));
        return true;
    }
}
