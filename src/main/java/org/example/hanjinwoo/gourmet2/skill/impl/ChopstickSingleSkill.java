package org.example.hanjinwoo.gourmet2.skill.impl;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.compat.CombatAnimations;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.entity.ChopsticksEntity;
import org.example.hanjinwoo.gourmet2.entity.ShockwaveRingEntity;
import org.example.hanjinwoo.gourmet2.entity.UpheavalEntity;
import org.example.hanjinwoo.gourmet2.skill.ActiveSkill;
import org.example.hanjinwoo.gourmet2.skill.CellEvolution;
import org.example.hanjinwoo.gourmet2.skill.ChopsticksState;
import org.example.hanjinwoo.gourmet2.skill.Hurt;
import org.example.hanjinwoo.gourmet2.skill.SkillBehavior;
import org.example.hanjinwoo.gourmet2.skill.SkillContext;
import org.example.hanjinwoo.gourmet2.skill.SkillType;
import org.example.hanjinwoo.gourmet2.skill.Targeting;

/**
 * Single Chopstick (Ichiryu) - two presses. The first conjures a giant single chopstick hanging over the caster's
 * head; the second drops it, straight down onto whatever spot the caster is looking at. Everything under it is
 * hurt and thrown up; the ground it lands on is broken and heaved (gently, see {@link UpheavalEntity.Growth#GENTLE}).
 *
 * <p>Runs on the shared power settings: the flying damage and size dials (size also sets how big the chopstick and
 * its blast are), the ranged-reach dial for how far away it can be dropped, and Cell level / Food Immersion / the
 * attack-damage dial through {@link SkillContext#damage}.
 */
public class ChopstickSingleSkill implements SkillBehavior {
    private static final double BASE_RANGE = 20.0;
    private static final double DROP_HEIGHT = 12.0;
    private static final double STICK_LENGTH = 12.0;
    /** How high over the caster's feet the hovering chopstick's tip hangs. */
    private static final double HOVER_TIP_HEIGHT = 4.0;
    private static final int HOVER_TICKS = 400;
    private static final int DESCEND_TICKS = 6;
    private static final int LINGER_TICKS = 8;
    private static final int COOLDOWN = 100;
    private static final double BLAST_RADIUS = 3.5;
    private static final float BASE_DAMAGE = 32.0F;
    private static final double BLOW_SPEED = 2.0;
    private static final double IMPACT_AREA = 0.6;
    private static final int MAX_EXTRA_COST = 12;

    @Override
    public int extraAppetiteCost(TorikoData data) {
        int level = data.cellLevel();
        float damageFraction = CellEvolution.powerFraction(
                data.flyingDamageSetting(), CellEvolution.DAMAGE_MULT_BASE, CellEvolution.damageMultCap(level));
        float sizeFraction = CellEvolution.powerFraction(
                data.flyingSizeSetting(), CellEvolution.SIZE_MULT_BASE, CellEvolution.sizeMultCap(level));
        return Math.round(MAX_EXTRA_COST * (damageFraction + sizeFraction) / 2.0F);
    }

    /** First press: the chopstick is conjured over the caster's head and waits for the second. */
    @Override
    public boolean activate(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ChopsticksState state = ctx.data().singleChopstick;
        state.reset();
        state.active = true;
        state.ticksLeft = HOVER_TICKS;
        float size = ctx.data().flyingSizeSetting();
        ChopsticksEntity stick = new ChopsticksEntity(ctx.level());
        stick.setSingle(true);
        stick.setScale((float) (STICK_LENGTH / 7.0) * size);
        hover(player, stick, size);
        ctx.level().addFreshEntity(stick);
        state.mainId = stick.getId();
        Hurt.playSound(ctx, player.position(), SoundEvents.ANVIL_PLACE, 0.8F, 0.6F);
        return true;
    }

    /** Keeps the hovering chopstick over the caster, and lets it go if it is never used. */
    public static void tickHover(ServerPlayer player, TorikoData data) {
        ChopsticksState state = data.singleChopstick;
        if (!state.active) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        Entity stick = level.getEntity(state.mainId);
        if (!player.isAlive() || --state.ticksLeft <= 0 || !(stick instanceof ChopsticksEntity sticks)) {
            if (stick != null) {
                stick.discard();
            }
            state.reset();
            return;
        }
        sticks.setScale((float) (STICK_LENGTH / 7.0) * data.flyingSizeSetting());
        hover(player, sticks, data.flyingSizeSetting());
    }

    /** Second press: the chopstick is put over the spot being looked at and driven down onto it. */
    public static void fire(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        TorikoData data = ctx.data();
        ChopsticksState state = data.singleChopstick;
        if (data.isBusy()) {
            return;
        }
        float size = data.flyingSizeSetting();
        double range = BASE_RANGE * data.rangeMultiplier();
        Vec3 point = aimPoint(ctx, range);

        ChopsticksEntity stick = ctx.level().getEntity(state.mainId) instanceof ChopsticksEntity existing
                ? existing : new ChopsticksEntity(ctx.level());
        if (stick.level().getEntity(stick.getId()) == null) {
            stick.setSingle(true);
            ctx.level().addFreshEntity(stick);
        }
        stick.setScale((float) (STICK_LENGTH / 7.0) * size);
        place(stick, point, DROP_HEIGHT, size, player.getYRot());

        ActiveSkill active = new ActiveSkill(SkillType.CHOPSTICK_SINGLE, DESCEND_TICKS + LINGER_TICKS, false,
                ctx.lookDirection(), player.position());
        active.targetId = stick.getId();
        active.point = point;
        data.setActive(active);
        state.reset();
        data.setCooldown(SkillType.CHOPSTICK_SINGLE, COOLDOWN);
        CombatAnimations.playSkill(player, "chopstick_single_slam");
    }

    @Override
    public void tick(SkillContext ctx, ActiveSkill active) {
        if (!(ctx.level().getEntity(active.targetId) instanceof ChopsticksEntity stick)) {
            return;
        }
        float size = ctx.data().flyingSizeSetting();
        if (active.elapsed < DESCEND_TICKS) {
            double t = (active.elapsed + 1) / (double) DESCEND_TICKS;
            place(stick, active.point, DROP_HEIGHT * (1.0 - t * t), size, ctx.player().getYRot());
            if (active.elapsed == DESCEND_TICKS - 1) {
                impact(ctx, active.point, size);
            }
        }
    }

    @Override
    public void finish(SkillContext ctx, ActiveSkill active, boolean interrupted) {
        Entity stick = ctx.level().getEntity(active.targetId);
        if (stick != null) {
            stick.discard();
        }
    }

    private static void hover(ServerPlayer player, ChopsticksEntity stick, float size) {
        double length = STICK_LENGTH * size;
        stick.moveTo(player.getX(), player.getY() + HOVER_TIP_HEIGHT + length / 2.0, player.getZ(),
                player.getYRot(), 90.0F);
    }

    /** Puts the stick straight up and down with its tip {@code above} blocks over the ground point. */
    private static void place(ChopsticksEntity stick, Vec3 ground, double above, float size, float yaw) {
        double length = STICK_LENGTH * size;
        stick.moveTo(ground.x, ground.y + above + length / 2.0, ground.z, yaw, 90.0F);
    }

    private static void impact(SkillContext ctx, Vec3 point, float size) {
        ServerPlayer player = ctx.player();
        TorikoData data = ctx.data();
        double radius = BLAST_RADIUS * size;
        float damage = BASE_DAMAGE * data.flyingDamageSetting();

        AABB area = new AABB(point, point).inflate(radius, 2.0, radius);
        for (Entity entity : ctx.level().getEntities(player, area, e -> Targeting.canTarget(player, e))) {
            if (entity.position().distanceToSqr(point.x, entity.getY(), point.z) > radius * radius
                    || !(entity instanceof LivingEntity victim)) {
                continue;
            }
            if (Hurt.cut(ctx, victim, damage)) {
                Hurt.launch(victim, new Vec3(0.0, 1.0, 0.0), 0.8);
                Hurt.knockAway(victim, point, 0.5);
                data.rememberLaunched(victim.getId(), player.tickCount);
            }
        }

        ShockwaveRingEntity ring = new ShockwaveRingEntity(ctx.level());
        ring.moveTo(point.x, point.y + 0.1, point.z, player.getYRot(), 0.0F);
        ctx.level().addFreshEntity(ring);
        Hurt.playSound(ctx, point, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.9F);
        Hurt.playSound(ctx, point, SoundEvents.ANVIL_LAND, 1.0F, 0.6F);

        AABB ground = AABB.ofSize(point.add(0.0, 0.5, 0.0), 2.0 * size + 1.0, 3.0, 2.0 * size + 1.0);
        Hurt.sweepBreak(player, ctx.level(), ground, new Vec3(0.0, -BLOW_SPEED * data.rangeMultiplier(), 0.0),
                ctx.damage(damage), IMPACT_AREA * size, UpheavalEntity.Growth.GENTLE);
    }

    /**
     * Exactly where the crosshair is: the first creature the line of sight runs into (nearest along it, and in front
     * of any wall), otherwise the spot on the terrain it lands on. Unlike a lock-on cone this never drifts to
     * something merely near the line of sight.
     */
    private static Vec3 aimPoint(SkillContext ctx, double range) {
        ServerPlayer player = ctx.player();
        Vec3 look = ctx.lookDirection();
        Vec3 eye = ctx.eyePosition();
        Vec3 hit = Targeting.impactPoint(player, look, range);
        double wall = hit.subtract(eye).length();
        for (LivingEntity victim : Targeting.alongRay(player, eye, look, range, 0.3)) {
            if (victim.getBoundingBox().getCenter().subtract(eye).length() <= wall + 0.5) {
                return victim.position();
            }
        }
        // Ran out of range in open air: come down on whatever floor is under the end of the line.
        return wall >= range - 0.01 ? groundBelow(ctx, hit) : hit;
    }

    /** The floor under {@code from}, so a chopstick aimed at open air still comes down on something. */
    private static Vec3 groundBelow(SkillContext ctx, Vec3 from) {
        Vec3 top = from.add(0.0, 1.0, 0.0);
        var hit = ctx.level().clip(new ClipContext(top, top.add(0.0, -16.0, 0.0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx.player()));
        return hit.getType() == HitResult.Type.MISS ? from : hit.getLocation();
    }
}
