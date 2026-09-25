package org.example.hanjinwoo.gourmet2.skill.impl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.entity.ChopstickGhostEntity;
import org.example.hanjinwoo.gourmet2.entity.UpheavalEntity;
import org.example.hanjinwoo.gourmet2.skill.CellEvolution;
import org.example.hanjinwoo.gourmet2.skill.Hurt;
import org.example.hanjinwoo.gourmet2.skill.SkillBehavior;
import org.example.hanjinwoo.gourmet2.skill.SkillContext;
import org.example.hanjinwoo.gourmet2.skill.Targeting;

/**
 * Chopstick Stab (Ichiryu) - the caster sinks low and throws an overhand thrust; a string of chopstick
 * afterimages is left down the line of the blow and everything along it is hurt.
 *
 * <p>It runs on the shared power settings: the flying damage and size dials (size also widens the line and the
 * afterimages), the ranged-reach dial and Cell level for how far it carries, and Cell level / Food Immersion /
 * the attack-damage dial through {@link SkillContext#damage}. What stands in the way is broken and heaved up
 * by the same terrain rules as the other techniques.
 */
public class ChopstickStabSkill implements SkillBehavior {
    private static final double BASE_RANGE = 6.0;
    private static final float BASE_DAMAGE = 18.0F;
    private static final double LINE_RADIUS = 0.7;
    private static final double BLOW_SPEED = 2.0;
    /** The face the stab lands on: a chopstick point, as narrow as a fork's prong. */
    private static final double IMPACT_AREA = 0.25;
    private static final int MAX_EXTRA_COST = 8;

    @Override
    public int extraAppetiteCost(TorikoData data) {
        int level = data.cellLevel();
        float damageFraction = CellEvolution.powerFraction(
                data.flyingDamageSetting(), CellEvolution.DAMAGE_MULT_BASE, CellEvolution.damageMultCap(level));
        float sizeFraction = CellEvolution.powerFraction(
                data.flyingSizeSetting(), CellEvolution.SIZE_MULT_BASE, CellEvolution.sizeMultCap(level));
        return Math.round(MAX_EXTRA_COST * (damageFraction + sizeFraction) / 2.0F);
    }

    @Override
    public boolean activate(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        TorikoData data = ctx.data();
        Vec3 dir = ctx.lookDirection();
        Vec3 origin = ctx.eyePosition();
        float size = data.flyingSizeSetting();

        // Solid terrain ends the reach; the last afterimage sits on it so the blow breaks what it can.
        double range = BASE_RANGE * data.rangeMultiplier();
        double wall = Targeting.impactPoint(player, dir, range).subtract(origin).length();
        double reach = Math.min(range, wall + 0.6);

        float damage = BASE_DAMAGE * data.flyingDamageSetting();
        float blow = ctx.damage(damage);
        for (LivingEntity victim : Targeting.alongLine(player, origin, dir, reach, LINE_RADIUS * size)) {
            if (Hurt.cut(ctx, victim, damage)) {
                Hurt.knockAway(victim, player.position(), 0.5);
                Hurt.playSound(ctx, victim.position(), SoundEvents.TRIDENT_HIT, 0.8F, 1.7F);
            }
        }

        int ghosts = Math.max(3, (int) Math.ceil(reach / 1.3));
        for (int i = 0; i < ghosts; i++) {
            double d = ghosts == 1 ? reach : 0.6 + (reach - 0.6) * i / (ghosts - 1);
            Vec3 at = origin.add(dir.scale(d)).add(0.0, -0.15, 0.0);
            // Earlier images fade first, so the trail runs out behind the tip of the thrust.
            ChopstickGhostEntity.spawn(ctx.level(), at, dir, size, 6 + i * 2);
            AABB box = AABB.ofSize(at, 1.0 * size + 0.4, 1.0 * size + 0.4, 1.0 * size + 0.4);
            Hurt.sweepBreak(player, ctx.level(), box, dir.scale(BLOW_SPEED * data.rangeMultiplier()), blow,
                    IMPACT_AREA, UpheavalEntity.Growth.GENTLE);
        }
        Hurt.playSound(ctx, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.9F, 1.5F);
        Hurt.playSound(ctx, player.position(), SoundEvents.TRIDENT_THROW.value(), 0.7F, 1.8F);
        return true;
    }
}
