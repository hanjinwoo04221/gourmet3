package org.example.hanjinwoo.gourmet2.skill.impl;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.compat.CombatAnimations;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.entity.ChopsticksEntity;
import org.example.hanjinwoo.gourmet2.skill.ChopsticksState;
import org.example.hanjinwoo.gourmet2.skill.Hurt;
import org.example.hanjinwoo.gourmet2.skill.SkillBehavior;
import org.example.hanjinwoo.gourmet2.skill.SkillContext;
import org.example.hanjinwoo.gourmet2.skill.SkillEngine;
import org.example.hanjinwoo.gourmet2.skill.SkillType;
import org.example.hanjinwoo.gourmet2.skill.Targeting;

import java.util.List;

/**
 * Chopsticks (Ichiryu) - summons a giant pair of chopsticks. While they hover, Up/Down pick which technique
 * they perform, and using the skill again performs it: Pick grips the creature being looked at, Transfer Pick
 * summons several pairs, grips everything around the caster and sets it down where they are looking.
 */
public class ChopsticksSkill implements SkillBehavior {
    public static final int MODE_PICK = 0;
    public static final int MODE_TRANSFER = 1;
    public static final int MODE_COUNT = 2;
    private static final int[] MODE_LEVELS = {10, 20};
    private static final int[] MODE_COSTS = {10, 25};
    private static final String[] MODE_IDS = {"pick", "transfer_pick"};

    private static final int HOVER_TICKS = 4000;
    private static final int COOLDOWN = 100;
    private static final double PICK_RANGE = 24.0;
    private static final double PICK_HOLD_DISTANCE = 4.0;
    private static final int PICK_HOLD_TICKS = 40;
    private static final double PICK_REACH_SPEED = 3.0;
    private static final int PICK_REACH_MAX_TICKS = 30;
    private static final double PICK_PULL_SPEED = 1.5;
    private static final double TRANSFER_RADIUS = 8.0;
    private static final int TRANSFER_MAX = 6;
    private static final double TRANSFER_RANGE = 24.0;
    private static final int TRANSFER_TICKS = 40;
    private static final double STICK_LENGTH = 7.0;
    private static final double HOVER_HEIGHT = 3.0;
    private static final double HOVER_FORWARD = 0.8;

    /** Length of the giant sticks: grows and shrinks with the flying-technique size setting. */
    private static double length(TorikoData data) {
        return STICK_LENGTH * data.flyingSizeSetting();
    }

    public static boolean modeUnlocked(int mode, int cellLevel) {
        return mode >= 0 && mode < MODE_COUNT && cellLevel >= MODE_LEVELS[mode];
    }

    public static Component modeName(int mode) {
        return Component.translatable("skill." + Gourmet2.MODID + ".chopsticks." + MODE_IDS[mode]);
    }

    @Override
    public boolean activate(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ChopsticksState state = ctx.data().chopsticks;
        state.reset();
        state.active = true;
        state.mode = MODE_PICK;
        state.ticksLeft = HOVER_TICKS;
        ChopsticksEntity sticks = new ChopsticksEntity(ctx.level());
        sticks.setScale(ctx.data().flyingSizeSetting());
        placeMain(player, sticks, length(ctx.data()));
        ctx.level().addFreshEntity(sticks);
        state.mainId = sticks.getId();
        Hurt.playSound(ctx, player.position(), SoundEvents.ANVIL_PLACE, 0.6F, 1.8F);
        announceMode(player, state.mode);
        return true;
    }

    /** Up/Down while the chopsticks hover: steps to the next technique the caster has unlocked. */
    public static void cycleMode(ServerPlayer player, TorikoData data, int delta) {
        ChopsticksState state = data.chopsticks;
        if (!state.active || state.holding() || delta == 0) {
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

    /** Using the skill again while the chopsticks hover: performs the chosen technique. */
    public static void fire(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        TorikoData data = ctx.data();
        ChopsticksState state = data.chopsticks;
        if (state.holding()) {
            return;
        }
        int mode = state.mode;
        if (!modeUnlocked(mode, data.cellLevel())) {
            return;
        }
        if (!data.canAfford(MODE_COSTS[mode])) {
            SkillEngine.fail(player, Component.translatable("message." + Gourmet2.MODID + ".not_enough_appetite",
                    MODE_COSTS[mode]));
            return;
        }
        Vec3 look = ctx.lookDirection();
        List<LivingEntity> victims;
        Vec3 dest = null;
        if (mode == MODE_PICK) {
            LivingEntity target = Targeting.lockOn(player, PICK_RANGE);
            victims = target == null ? List.of() : List.of(target);
        } else {
            victims = Targeting.inSphere(player, player.position(), TRANSFER_RADIUS);
            if (victims.size() > TRANSFER_MAX) {
                victims = victims.subList(0, TRANSFER_MAX);
            }
            dest = Targeting.impactPoint(player, look, TRANSFER_RANGE);
        }
        if (victims.isEmpty()) {
            SkillEngine.fail(player, Component.translatable("message." + Gourmet2.MODID + ".chopsticks_no_target"));
            return;
        }

        data.spend(MODE_COSTS[mode]);
        double len = length(data);
        Entity main = ctx.level().getEntity(state.mainId);
        Vec3 mainTip = main == null ? ctx.eyePosition()
                : main.position().add(main.getLookAngle().scale(len / 2.0));
        if (main != null) {
            main.discard();
        }
        state.mainId = -1;
        state.active = false;
        state.holdMode = mode;
        state.holdTicks = 0;
        state.held.clear();
        for (int i = 0; i < victims.size(); i++) {
            LivingEntity victim = victims.get(i);
            ChopsticksEntity grabber = new ChopsticksEntity(ctx.level());
            grabber.setScale(data.flyingSizeSetting());
            Vec3 spot = null;
            if (dest != null) {
                double angle = i * (Math.PI * 2.0 / victims.size());
                double ring = victims.size() > 1 ? 1.6 : 0.0;
                spot = dest.add(Math.cos(angle) * ring, 0.3, Math.sin(angle) * ring);
            }
            // Pick sets out from where the hovering sticks were and travels to the creature; Transfer Pick
            // conjures its pairs directly over each one.
            boolean arrived = mode != MODE_PICK;
            grabber.setGripping(arrived);
            Vec3 center = victim.getBoundingBox().getCenter();
            if (arrived) {
                grabber.moveTo(center.x, center.y + len / 2.0 - 0.3, center.z, player.getYRot(), 90.0F);
            } else {
                aimTip(grabber, mainTip, center, len);
            }
            ctx.level().addFreshEntity(grabber);
            state.held.add(new ChopsticksState.Held(victim.getId(), grabber.getId(), victim.position(), spot,
                    mainTip, arrived));
        }
        data.setCooldown(SkillType.CHOPSTICKS, COOLDOWN);
        CombatAnimations.playSkill(player, mode == MODE_PICK ? "chopsticks_pick" : "chopsticks_transfer");
        Hurt.playSound(ctx, player.position(), SoundEvents.ANVIL_LAND, 0.7F, 1.6F);
    }

    /** Per-tick upkeep: keeps the hovering pair beside the caster, or carries whatever is being held. */
    public static void tick(ServerPlayer player, TorikoData data) {
        ChopsticksState state = data.chopsticks;
        if (!state.active && !state.holding()) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        if (!player.isAlive()) {
            release(level, state);
            return;
        }
        SkillContext ctx = new SkillContext(player, level, data);
        if (state.active) {
            Entity main = level.getEntity(state.mainId);
            if (--state.ticksLeft <= 0 || !(main instanceof ChopsticksEntity sticks)) {
                if (main != null) {
                    main.discard();
                }
                state.reset();
                return;
            }
            sticks.setScale(data.flyingSizeSetting());
            placeMain(player, sticks, length(data));
            return;
        }
        tickHold(ctx, state);
    }

    private static void tickHold(SkillContext ctx, ChopsticksState state) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        Vec3 look = player.getLookAngle();
        double len = length(ctx.data());

        boolean allArrived = true;
        boolean anyLeft = false;
        for (ChopsticksState.Held held : state.held) {
            if (!(level.getEntity(held.targetId) instanceof LivingEntity victim) || !victim.isAlive()) {
                continue;
            }
            anyLeft = true;
            if (!held.arrived) {
                allArrived = false;
                reach(level, held, victim, len);
                continue;
            }
            Vec3 at;
            if (held.dest == null) {
                // The creature is drawn in to a spot in front of the caster.
                Vec3 hold = Targeting.impactPoint(player, look, PICK_HOLD_DISTANCE).subtract(look.scale(0.6));
                Vec3 goal = hold.subtract(0.0, victim.getBbHeight() / 2.0, 0.0);
                Vec3 toGoal = goal.subtract(victim.position());
                at = toGoal.length() <= PICK_PULL_SPEED ? goal
                        : victim.position().add(toGoal.normalize().scale(PICK_PULL_SPEED));
            } else {
                double t = Math.min(1.0, (state.holdTicks + 1) / (double) TRANSFER_TICKS);
                at = held.start.lerp(held.dest, t).add(0.0, Math.sin(Math.PI * t) * 4.0, 0.0);
            }
            moveHeld(victim, at);
            if (level.getEntity(held.grabberId) instanceof ChopsticksEntity grabber) {
                Vec3 center = victim.getBoundingBox().getCenter();
                grabber.moveTo(center.x, center.y + len / 2.0 - 0.3, center.z, player.getYRot(), 90.0F);
            }
        }
        if (allArrived) {
            state.holdTicks++;
        }
        int total = state.holdMode == MODE_PICK ? PICK_HOLD_TICKS : TRANSFER_TICKS;
        if (!anyLeft || state.holdTicks >= total) {
            release(level, state);
        }
    }

    /** Sends the sticks' tip a few blocks toward the creature each tick; they grip it once they are on it. */
    private static void reach(ServerLevel level, ChopsticksState.Held held, LivingEntity victim, double len) {
        Vec3 center = victim.getBoundingBox().getCenter();
        Vec3 toTarget = center.subtract(held.tip);
        double distance = toTarget.length();
        held.reachTicks++;
        if (distance <= PICK_REACH_SPEED || held.reachTicks > PICK_REACH_MAX_TICKS) {
            held.tip = center;
            held.arrived = true;
            if (level.getEntity(held.grabberId) instanceof ChopsticksEntity grabber) {
                grabber.setGripping(true);
                grabber.moveTo(center.x, center.y + len / 2.0 - 0.3, center.z, grabber.getYRot(), 90.0F);
            }
            return;
        }
        held.tip = held.tip.add(toTarget.scale(PICK_REACH_SPEED / distance));
        if (level.getEntity(held.grabberId) instanceof ChopsticksEntity grabber) {
            aimTip(grabber, held.tip, center, len);
        }
    }

    /** Places the sticks so their tip is at {@code tip}, pointing toward {@code goal}. */
    private static void aimTip(ChopsticksEntity sticks, Vec3 tip, Vec3 goal, double len) {
        Vec3 dir = goal.subtract(tip);
        if (dir.lengthSqr() < 1.0E-6) {
            dir = new Vec3(0.0, -1.0, 0.0);
        }
        dir = dir.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) -Math.toDegrees(Math.asin(dir.y));
        Vec3 center = tip.subtract(dir.scale(len / 2.0));
        sticks.moveTo(center.x, center.y, center.z, yaw, pitch);
    }

    private static void moveHeld(LivingEntity victim, Vec3 at) {
        victim.fallDistance = 0.0F;
        victim.setDeltaMovement(Vec3.ZERO);
        if (victim instanceof ServerPlayer sp) {
            sp.connection.teleport(at.x, at.y, at.z, sp.getYRot(), sp.getXRot());
        } else {
            victim.setPos(at.x, at.y, at.z);
        }
    }

    /** Frees everything held, cushioning the fall: the chopsticks themselves never hurt anyone. */
    private static void release(ServerLevel level, ChopsticksState state) {
        for (ChopsticksState.Held held : state.held) {
            Entity grabber = level.getEntity(held.grabberId);
            if (grabber != null) {
                grabber.discard();
            }
            if (level.getEntity(held.targetId) instanceof LivingEntity victim && victim.isAlive()) {
                victim.fallDistance = 0.0F;
                victim.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, false));
            }
        }
        Entity main = level.getEntity(state.mainId);
        if (main != null) {
            main.discard();
        }
        state.reset();
    }

    private static void placeMain(ServerPlayer player, ChopsticksEntity sticks, double len) {
        Vec3 look = player.getLookAngle();
        Vec3 at = player.getEyePosition().add(look.scale(HOVER_FORWARD)).add(0.0, HOVER_HEIGHT + len * 0.08, 0.0);
        sticks.moveTo(at.x, at.y, at.z, player.getYRot(), player.getXRot());
    }

    private static void announceMode(ServerPlayer player, int mode) {
        player.displayClientMessage(Component.translatable("message." + Gourmet2.MODID + ".chopsticks_mode",
                modeName(mode)), true);
    }
}
