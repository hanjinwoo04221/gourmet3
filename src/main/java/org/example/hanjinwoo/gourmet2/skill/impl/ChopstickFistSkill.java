package org.example.hanjinwoo.gourmet2.skill.impl;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.compat.CombatAnimations;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.entity.ThrownChopstickEntity;
import org.example.hanjinwoo.gourmet2.skill.CellEvolution;
import org.example.hanjinwoo.gourmet2.skill.ChopsticksState;
import org.example.hanjinwoo.gourmet2.skill.Hurt;
import org.example.hanjinwoo.gourmet2.skill.SkillBehavior;
import org.example.hanjinwoo.gourmet2.skill.SkillContext;
import org.example.hanjinwoo.gourmet2.skill.SkillEngine;
import org.example.hanjinwoo.gourmet2.skill.SkillType;

/**
 * Fist Chopstick (Ichiryu) - grips one chopstick in the fist. While it is held, Up/Down pick the technique and
 * using the skill again performs it: Chopstick Shot throws the held stick forward, Chopstick Frenzy hurls sticks
 * in every direction for as long as the key stays held.
 *
 * <p>Both techniques run on the same power settings as the other thrown techniques: the flying damage and size
 * dials, the ranged-reach dial, the attack-damage dial and Gourmet Cell level / Food Immersion (through
 * {@link SkillContext#damage}), and the fire-rate dial for the Frenzy. What they hit is thrown up by the
 * shared terrain-breaking (see {@link ThrownChopstickEntity}).
 */
public class ChopstickFistSkill implements SkillBehavior {
    public static final int MODE_SHOT = 0;
    public static final int MODE_FRENZY = 1;
    public static final int MODE_COUNT = 2;
    private static final int[] MODE_LEVELS = {20, 25};
    private static final String[] MODE_IDS = {"shot", "frenzy"};

    private static final int HOLD_TICKS = 400;
    private static final int COOLDOWN = 60;
    private static final float VELOCITY = 2.4F;
    /** What a stick is worth at the base damage setting, before the mod's own bonuses. */
    public static final float BASE_DAMAGE = 9.0F;
    private static final float FRENZY_DAMAGE_FACTOR = 0.45F;
    private static final int SHOT_COST = 8;
    private static final int FRENZY_COST_PER_VOLLEY = 3;
    private static final int MAX_EXTRA_PER_THROW = 6;
    private static final int FRENZY_BASE_INTERVAL = 5;
    private static final int FRENZY_MIN_INTERVAL = 2;
    private static final int FRENZY_STICKS_PER_VOLLEY = 2;
    /** The flail clip is 24 frames; replayed just before it ends so the arm never stops while the key is held. */
    private static final int FRENZY_ANIMATION_TICKS = 18;

    public static boolean modeUnlocked(int mode, int cellLevel) {
        return mode >= 0 && mode < MODE_COUNT && cellLevel >= MODE_LEVELS[mode];
    }

    public static Component modeName(int mode) {
        return Component.translatable("skill." + Gourmet2.MODID + ".chopstick_fist." + MODE_IDS[mode]);
    }

    @Override
    public boolean activate(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ChopsticksState state = ctx.data().fistChopstick;
        state.reset();
        state.active = true;
        state.mode = MODE_SHOT;
        state.ticksLeft = HOLD_TICKS;
        ThrownChopstickEntity stick = new ThrownChopstickEntity(ctx.level());
        stick.setOwner(player);
        stick.setHeld(true);
        placeHeld(player, stick);
        ctx.level().addFreshEntity(stick);
        state.mainId = stick.getId();
        Hurt.playSound(ctx, player.position(), SoundEvents.ANVIL_PLACE, 0.5F, 2.0F);
        announceMode(player, state.mode);
        return true;
    }

    /** Up/Down while the stick is held: steps to the next technique the caster has unlocked. */
    public static void cycleMode(ServerPlayer player, TorikoData data, int delta) {
        ChopsticksState state = data.fistChopstick;
        if (!state.active || state.barrage || delta == 0) {
            return;
        }
        int level = data.cellLevel();
        int step = delta < 0 ? -1 : 1;
        int mode = state.mode;
        for (int i = 0; i < MODE_COUNT; i++) {
            mode = Math.floorMod(mode + step, MODE_COUNT);
            if (modeUnlocked(mode, level)) {
                state.mode = mode;
                announceMode(player, mode);
                return;
            }
        }
    }

    /** Using the skill again: throws the held stick (Shot) or starts the Frenzy, which runs until the key comes up. */
    public static void fire(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        TorikoData data = ctx.data();
        ChopsticksState state = data.fistChopstick;
        if (state.barrage || !modeUnlocked(state.mode, data.cellLevel())) {
            return;
        }
        if (state.mode == MODE_SHOT) {
            int cost = SHOT_COST + extraCost(data);
            if (!data.spend(cost)) {
                SkillEngine.fail(player, Component.translatable(
                        "message." + Gourmet2.MODID + ".not_enough_appetite", cost));
                return;
            }
            Vec3 look = ctx.lookDirection();
            if (ctx.level().getEntity(state.mainId) instanceof ThrownChopstickEntity stick) {
                configure(ctx, stick, BASE_DAMAGE);
                stick.launch(look.scale(velocity(data)));
            }
            state.mainId = -1;
            CombatAnimations.playSkill(player, "chopstick_throw");
            Hurt.playSound(ctx, player.position(), SoundEvents.TRIDENT_THROW.value(), 0.9F, 1.6F);
            player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            finish(ctx);
            return;
        }
        if (!data.canAfford(FRENZY_COST_PER_VOLLEY)) {
            SkillEngine.fail(player, Component.translatable(
                    "message." + Gourmet2.MODID + ".not_enough_appetite", FRENZY_COST_PER_VOLLEY));
            return;
        }
        state.barrage = true;
        state.barrageTicks = 0;
        CombatAnimations.playSkill(player, "chopstick_frenzy");
    }

    /** Key came up: the Frenzy stops and the technique ends. */
    public static void stopFrenzy(ServerPlayer player, TorikoData data) {
        ChopsticksState state = data.fistChopstick;
        if (!state.barrage) {
            return;
        }
        finish(new SkillContext(player, (ServerLevel) player.level(), data));
    }

    public static void tick(ServerPlayer player, TorikoData data) {
        ChopsticksState state = data.fistChopstick;
        if (!state.active) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        SkillContext ctx = new SkillContext(player, level, data);
        if (!player.isAlive() || (!state.barrage && --state.ticksLeft <= 0)) {
            finish(ctx);
            return;
        }
        if (level.getEntity(state.mainId) instanceof ThrownChopstickEntity stick && stick.isHeld()) {
            placeHeld(player, stick);
        }
        if (!state.barrage) {
            return;
        }
        state.barrageTicks++;
        if (state.barrageTicks % FRENZY_ANIMATION_TICKS == 0) {
            CombatAnimations.playSkill(player, "chopstick_frenzy");
        }
        if (state.barrageTicks % frenzyInterval(data) != 0) {
            return;
        }
        int cost = FRENZY_COST_PER_VOLLEY + extraCost(data);
        if (!data.canAfford(cost)) {
            // Out of Appetite: the Frenzy ends, as a held technique would.
            finish(ctx);
            return;
        }
        data.spend(cost);
        volley(ctx);
    }

    private static void volley(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        var random = player.getRandom();
        Vec3 hand = handPosition(player);
        for (int i = 0; i < FRENZY_STICKS_PER_VOLLEY; i++) {
            Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
            if (dir.lengthSqr() < 1.0E-6) {
                dir = ctx.lookDirection();
            }
            dir = dir.normalize();
            ThrownChopstickEntity stick = new ThrownChopstickEntity(ctx.level());
            stick.setOwner(player);
            configure(ctx, stick, BASE_DAMAGE * FRENZY_DAMAGE_FACTOR);
            Vec3 from = hand.add(dir.scale(0.6));
            stick.moveTo(from.x, from.y, from.z);
            stick.launch(dir.scale(velocity(ctx.data())));
            ctx.level().addFreshEntity(stick);
        }
        Hurt.playSound(ctx, player.position(), SoundEvents.TRIDENT_THROW.value(), 0.6F, 1.9F);
    }

    /** Applies the power settings to one stick: damage (cell level, awakening and dials) and size. */
    private static void configure(SkillContext ctx, ThrownChopstickEntity stick, float baseDamage) {
        TorikoData data = ctx.data();
        stick.setOwner(ctx.player());
        stick.setDamage(ctx.damage(baseDamage * data.flyingDamageSetting()));
        stick.setSizeScale(data.flyingSizeSetting());
    }

    /** Launch speed: what carries a stick further, from the Cell level and the ranged-reach dial. */
    private static double velocity(TorikoData data) {
        return VELOCITY * data.rangeMultiplier();
    }

    /** Ticks between Frenzy volleys, shorter the further the fire-rate dial is turned up. */
    private static int frenzyInterval(TorikoData data) {
        int steps = data.forkProjectileSetting() - CellEvolution.FORK_PROJECTILE_BASE;
        return Math.max(FRENZY_MIN_INTERVAL, FRENZY_BASE_INTERVAL - steps);
    }

    /** More Appetite the further the damage and size dials are pushed up their current caps. */
    private static int extraCost(TorikoData data) {
        int level = data.cellLevel();
        float damageFraction = CellEvolution.powerFraction(
                data.flyingDamageSetting(), CellEvolution.DAMAGE_MULT_BASE, CellEvolution.damageMultCap(level));
        float sizeFraction = CellEvolution.powerFraction(
                data.flyingSizeSetting(), CellEvolution.SIZE_MULT_BASE, CellEvolution.sizeMultCap(level));
        return Math.round(MAX_EXTRA_PER_THROW * (damageFraction + sizeFraction) / 2.0F);
    }

    private static void finish(SkillContext ctx) {
        ChopsticksState state = ctx.data().fistChopstick;
        Entity held = ctx.level().getEntity(state.mainId);
        if (held instanceof ThrownChopstickEntity stick && stick.isHeld()) {
            stick.discard();
        }
        state.reset();
        ctx.data().setCooldown(SkillType.CHOPSTICK_FIST, COOLDOWN);
    }

    private static Vec3 handPosition(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0.0, look.x);
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        return player.getEyePosition().add(look.scale(0.6)).add(right.scale(0.35)).add(0.0, -0.35, 0.0);
    }

    private static void placeHeld(ServerPlayer player, ThrownChopstickEntity stick) {
        Vec3 at = handPosition(player).add(player.getLookAngle().scale(0.6));
        stick.holdAt(at, player.getYRot(), Mth.clamp(player.getXRot(), -90.0F, 90.0F));
    }

    private static void announceMode(ServerPlayer player, int mode) {
        player.displayClientMessage(Component.translatable("message." + Gourmet2.MODID + ".chopstick_fist_mode",
                modeName(mode)), true);
    }
}
