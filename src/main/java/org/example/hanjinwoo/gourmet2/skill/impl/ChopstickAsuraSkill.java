package org.example.hanjinwoo.gourmet2.skill.impl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.compat.CombatAnimations;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.entity.ThrownChopstickEntity;
import org.example.hanjinwoo.gourmet2.skill.CellEvolution;
import org.example.hanjinwoo.gourmet2.skill.Hurt;
import org.example.hanjinwoo.gourmet2.skill.SkillBehavior;
import org.example.hanjinwoo.gourmet2.skill.SkillContext;
import org.example.hanjinwoo.gourmet2.skill.SkillEngine;
import org.example.hanjinwoo.gourmet2.skill.SkillType;

/**
 * Asura Chopsticks (Ichiryu) - hold the key to spray big single chopsticks straight ahead, several at a time in a
 * tight fan, for as long as it stays held. Release to stop.
 *
 * <p>Runs on the shared power settings like the other thrown techniques: the flying damage and size dials, the
 * ranged-reach dial (how fast they are thrown), the fire-rate dial, and Cell level / Food Immersion / the
 * attack-damage dial through {@link SkillContext#damage}. The sticks are {@link ThrownChopstickEntity}, so what
 * they run into is broken and heaved up gently.
 */
public class ChopstickAsuraSkill implements SkillBehavior {
    private static final float VELOCITY = 1.5F;
    /**
     * Fastest launch speed, however far the reach setting and Cell level would push it. The sticks pick up speed as
     * they fly (x1.8 over a full flight), and past about 3.9 blocks per tick the game's own tracking cannot keep up
     * with them, so they would be seen to stutter.
     */
    private static final double MAX_LAUNCH_SPEED = 3.0;
    private static final float BASE_DAMAGE = 7.0F;
    /**
     * Half the size of the Single Chopstick (12 blocks long, drawn 2.2x as thick as a scale-1 pair): these are
     * scaled so their length is 6 blocks and their girth half the Single Chopstick's, both times the size setting.
     */
    private static final float SIZE_FACTOR = 10.0F / 1.8F;
    /**
     * Drawn as thick as the box the stick actually hits and breaks blocks with (see {@code ThrownChopstickRenderer}):
     * the hitbox is 0.3 blocks per unit of scale, the stick 0.04 per unit of thickness at its tip.
     */
    /** The hitbox (and so the blocks it breaks) is scaled by this: a little under the tip's drawn width. */
    private static final float HIT_FACTOR = 3.0F;
    private static final float THICKNESS = 4.0F;
    private static final int STICKS_PER_VOLLEY = 3;
    /** The sticks are centred on where they spawn, so they start this far ahead to keep their rear end off the caster. */
    private static final double MUZZLE_OFFSET = 3.5;
    private static final double SPREAD = 0.02;
    private static final double SIDE_OFFSET = 0.15;
    /** A slow, heavy spray: each volley is followed by a long pause, and the sticks grow the longer the key is held. */
    private static final int BASE_INTERVAL = 24;
    private static final int MIN_INTERVAL = 14;
    /** Ticks of holding it takes to reach full growth, and how many times its size a stick is then. */
    private static final int GROW_TICKS = 300;
    private static final float MAX_GROWTH = 2.0F;
    private static final int VOLLEY_COST = 5;
    private static final int MAX_EXTRA_PER_VOLLEY = 6;
    /** The spray clip is 24 frames; replayed just before it ends so the arm keeps working while the key is held. */
    private static final int ANIMATION_TICKS = 18;

    @Override
    public boolean activate(SkillContext ctx) {
        Hurt.playSound(ctx, ctx.player().position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 0.8F);
        return true;
    }

    private static int fireInterval(TorikoData data) {
        int steps = data.forkProjectileSetting() - CellEvolution.FORK_PROJECTILE_BASE;
        return Math.max(MIN_INTERVAL, BASE_INTERVAL - steps / 3);
    }

    private static int volleyCost(TorikoData data) {
        int level = data.cellLevel();
        float damageFraction = CellEvolution.powerFraction(
                data.flyingDamageSetting(), CellEvolution.DAMAGE_MULT_BASE, CellEvolution.damageMultCap(level));
        float sizeFraction = CellEvolution.powerFraction(
                data.flyingSizeSetting(), CellEvolution.SIZE_MULT_BASE, CellEvolution.sizeMultCap(level));
        return VOLLEY_COST + Math.round(MAX_EXTRA_PER_VOLLEY * (damageFraction + sizeFraction) / 2.0F);
    }

    @Override
    public void tickHeld(SkillContext ctx, int ticksHeld) {
        ServerPlayer player = ctx.player();
        TorikoData data = ctx.data();
        if (ticksHeld == 1 || ticksHeld % ANIMATION_TICKS == 0) {
            CombatAnimations.playSkill(player, "chopstick_asura");
        }
        if (ticksHeld % fireInterval(data) != 0) {
            return;
        }
        int cost = volleyCost(data);
        if (!data.canAfford(cost)) {
            return;
        }
        data.spend(cost);

        Vec3 look = ctx.lookDirection();
        Vec3 right = new Vec3(-look.z, 0.0, look.x);
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        Vec3 up = right.cross(look).normalize();
        var random = player.getRandom();
        float grow = 1.0F + (MAX_GROWTH - 1.0F) * Math.min(1.0F, ticksHeld / (float) GROW_TICKS);
        for (int i = 0; i < STICKS_PER_VOLLEY; i++) {
            Vec3 dir = look.add(right.scale((random.nextDouble() - 0.5) * 2.0 * SPREAD))
                    .add(up.scale((random.nextDouble() - 0.5) * 2.0 * SPREAD)).normalize();
            Vec3 from = ctx.eyePosition().add(look.scale(MUZZLE_OFFSET))
                    .add(right.scale((random.nextDouble() - 0.5) * 2.0 * SIDE_OFFSET))
                    .add(up.scale((random.nextDouble() - 0.5) * 2.0 * SIDE_OFFSET - 0.2));
            ThrownChopstickEntity stick = new ThrownChopstickEntity(ctx.level());
            stick.setOwner(player);
            stick.setDamage(ctx.damage(BASE_DAMAGE * data.flyingDamageSetting() * grow));
            stick.setSizeScale(data.flyingSizeSetting() * HIT_FACTOR * grow);
            stick.setVisualScale(data.flyingSizeSetting() * SIZE_FACTOR * grow);
            stick.setThickness(THICKNESS);
            stick.moveTo(from.x, from.y, from.z);
            stick.launch(dir.scale(Math.min(MAX_LAUNCH_SPEED, VELOCITY * data.rangeMultiplier())));
            ctx.level().addFreshEntity(stick);
        }
        Hurt.playSound(ctx, player.position(), SoundEvents.TRIDENT_THROW.value(), 0.6F, 1.7F);
    }

    @Override
    public void stopHeld(SkillContext ctx) {
        ctx.data().setCooldown(SkillType.CHOPSTICK_ASURA, SkillEngine.cooldownFor(SkillType.CHOPSTICK_ASURA, ctx.data()));
    }
}
