package org.example.hanjinwoo.gourmet2.skill.combat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.example.hanjinwoo.gourmet2.compat.CombatAnimations;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.fx.FxDispatch;
import org.example.hanjinwoo.gourmet2.fx.SkillFx;
import org.example.hanjinwoo.gourmet2.registry.ModAttachments;
import org.example.hanjinwoo.gourmet2.registry.ModDamageTypes;
import org.example.hanjinwoo.gourmet2.skill.Hurt;
import org.example.hanjinwoo.gourmet2.skill.SkillContext;
import org.example.hanjinwoo.gourmet2.skill.SkillEngine;
import org.example.hanjinwoo.gourmet2.skill.Targeting;

/**
 * Server side of combat mode: a style's attack groups, a dodge dash with brief invulnerability and a
 * directional guard (with a tight perfect-guard window).
 *
 * <p>An attack group is a bundle of moves of the same kind — hand strikes, kicks, spins. Each group has
 * its own key, and each keeps its own place in its own chain: pressing a group's key takes that group's
 * next move, and letting the chain window lapse (or pressing a different group) starts that group's
 * chain over. Timing and damage come from the style's data in {@link CombatStyles}.
 */
public final class CombatEngine {
    private static final double REACH = 3.8;
    /** Minimum dot product between look direction and the direction to a victim. */
    private static final double CONE = 0.4;

    /**
     * A press during a swing's recovery is remembered and fired as soon as the recovery ends, so mashing
     * never drops an attack. Longer recoveries (dodge) are excluded so a stale press cannot fire later.
     */
    private static final int BUFFER_WINDOW = 17;
    /** Ticks after a press during which the next press continues that group's chain. */
    private static final int COMBO_WINDOW = 22;

    /** Aerial-combo styles only (see {@link CombatStyle#aerialCombo()}). */
    private static final int LAUNCHER_COST = 6;
    private static final int LAUNCHER_LOCK = 24;
    private static final float LAUNCHER_DAMAGE = 6.0F;
    /** Almost straight up, with a sliver forward so it doesn't feel like it teleports them. */
    private static final Vec3 LAUNCH_DIRECTION = new Vec3(0.0, 1.0, 0.12);
    private static final double LAUNCH_STRENGTH = 1.7;
    private static final float SELF_HOP_STRENGTH = 0.35F;

    private static final int SPIKE_COST = 7;
    private static final int SPIKE_LOCK = 22;
    private static final float SPIKE_DAMAGE = 10.0F;
    private static final double SPIKE_STRENGTH = 1.9;
    /** How fast the caster themselves crashes down alongside the target. */
    private static final double SPIKE_SELF_FALL_SPEED = 1.4;
    /** Ticks of fall-damage immunity granted so an intentional dive dropping through the sky doesn't hurt the caster. */
    private static final int SPIKE_FALL_IMMUNITY_TICKS = 60;

    private static final int DODGE_COST = 5;
    private static final int DODGE_COOLDOWN = 30;
    private static final int DODGE_INVULNERABLE_TICKS = 10;
    private static final double DODGE_SPEED = 1.2;

    /** Damage kept while guarding a hit from the front. */
    private static final float GUARD_DAMAGE_FACTOR = 0.25F;
    private static final int PERFECT_GUARD_TICKS = 6;
    private static final double GUARD_CONE = 0.3;

    private CombatEngine() {}

    /** @param group the attack group an {@link CombatAction#ATTACK} came from; ignored by every other action */
    public static void handle(ServerPlayer player, int actionIndex, int group) {
        TorikoData data = ModAttachments.of(player);
        CombatAction action = CombatAction.byIndex(actionIndex);
        if (action == null) {
            return;
        }
        if (!data.isCombatMode()) {
            if (action == CombatAction.GUARD_END) {
                return;
            }
            // Only a client in combat mode sends these, so the toggle packet must have been lost or
            // reordered (it can be, right after joining a world): trust the input and switch on.
            SkillEngine.setCombatMode(player, true);
        }
        if (CombatAnimations.holdsWeapon(player)) {
            return;
        }
        SkillContext ctx = new SkillContext(player, (ServerLevel) player.level(), data);
        switch (action) {
            case ATTACK -> attack(ctx, group);
            case DODGE -> dodge(ctx);
            case AERIAL_ATTACK -> aerialAttack(ctx);
            case GUARD_START -> {
                data.setGuarding(true);
                CombatAnimations.play(player, "guard");
            }
            case GUARD_END -> {
                if (data.isGuarding()) {
                    CombatAnimations.play(player, "stop");
                }
                data.setGuarding(false);
            }
        }
    }

    public static void tick(ServerPlayer player, TorikoData data) {
        if (data.combatCooldown() > 0) {
            data.setCombatCooldown(data.combatCooldown() - 1);
            if (data.combatCooldown() == 0 && data.bufferedAttack()) {
                data.setBufferedAttack(false);
                if (data.isCombatMode() && !data.isGuarding()) {
                    attack(new SkillContext(player, (ServerLevel) player.level(), data), data.bufferedGroup());
                }
            }
        }
        for (int group = 0; group < CombatStyles.MAX_GROUPS; group++) {
            if (data.groupTimer(group) > 0) {
                data.setGroupTimer(group, data.groupTimer(group) - 1);
                if (data.groupTimer(group) == 0) {
                    // The chain lapsed: the next press of this key starts it from the first move again.
                    data.setGroupStep(group, 0);
                }
            }
        }
        if (data.dodgeTicks() > 0) {
            data.setDodgeTicks(data.dodgeTicks() - 1);
        }
        if (data.delayedHitTicks() > 0) {
            data.setDelayedHitTicks(data.delayedHitTicks() - 1);
            if (data.delayedHitTicks() == 0) {
                deliverDelayedHit(new SkillContext(player, (ServerLevel) player.level(), data), data);
            }
        }
        if (data.fallImmuneTicks() > 0) {
            data.setFallImmuneTicks(data.fallImmuneTicks() - 1);
        }
        if (data.isGuarding()) {
            data.tickGuard();
        }
    }

    // ------------------------------------------------------------------- actions

    /**
     * One press of an attack key. Which move of the group comes out depends on how far that group's
     * chain has already advanced: a press inside the chain window takes the next move, otherwise the
     * first one, so tapping the key N times quickly plays moves 1..N and then wraps around.
     */
    private static void attack(SkillContext ctx, int groupIndex) {
        TorikoData data = ctx.data();
        CombatStyle style = CombatStyles.get(data.combatStyle());
        if (groupIndex < 0 || groupIndex >= style.groups().size()) {
            return;
        }
        if (data.combatCooldown() > 0) {
            data.setBufferedAttack(data.combatCooldown() <= BUFFER_WINDOW);
            data.setBufferedGroup(groupIndex);
            return;
        }
        if (data.isGuarding()) {
            // Attacking is a decision to stop guarding, not something to ignore.
            data.setGuarding(false);
        }
        ServerPlayer player = ctx.player();
        AttackGroup group = style.groups().get(groupIndex);
        int step = data.groupTimer(groupIndex) > 0 ? data.groupStep(groupIndex) : 0;
        ComboMove move = group.move(step);
        boolean finisher = group.isFinisher(step);
        // A move whose animation lands two blows splits its damage over both of them.
        float hitDamage = move.twoHits() ? move.damage() * 0.5F : move.damage();
        boolean immediateKnockback = finisher && !move.twoHits();

        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        lunge(player, finisher ? 0.5 : 0.3);
        Hurt.playSound(ctx, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.0F + step * 0.15F);

        for (LivingEntity victim : inFront(player)) {
            // Chained hits must land even inside the victim's post-hit invulnerability window.
            victim.invulnerableTime = 0;
            if (Hurt.apply(ctx, victim, ModDamageTypes.NAIL, ctx.damage(hitDamage))) {
                if (immediateKnockback) {
                    Hurt.knockAway(victim, player.position(), 0.9);
                }
                impact(ctx, victim, finisher ? 0.7F : 0.45F);
            }
        }
        CombatAnimations.play(player, move.clip());
        data.setGroupStep(groupIndex, (step + 1) % group.moves().size());
        data.setGroupTimer(groupIndex, Math.round(COMBO_WINDOW / style.attackSpeed()));
        data.setCombatCooldown(move.lockTicks(style.attackSpeed()));

        if (move.twoHits()) {
            data.setDelayedHitTicks(move.secondHitDelay());
            data.setDelayedHitDamage(hitDamage);
            // A chain's last move still knocks away on its second blow; the earlier moves stay in place.
            data.setDelayedHitKnockback(finisher);
        }
    }

    /** The second blow of a move whose animation lands two, delivered a few ticks after the first. */
    private static void deliverDelayedHit(SkillContext ctx, TorikoData data) {
        ServerPlayer player = ctx.player();
        for (LivingEntity victim : inFront(player)) {
            victim.invulnerableTime = 0;
            if (Hurt.apply(ctx, victim, ModDamageTypes.NAIL, ctx.damage(data.delayedHitDamage()))) {
                if (data.delayedHitKnockback()) {
                    Hurt.knockAway(victim, player.position(), 0.9);
                }
                impact(ctx, victim, data.delayedHitKnockback() ? 0.7F : 0.45F);
            }
        }
    }

    /**
     * Jump+attack on an aerial-combo style. Which move comes out follows where the player is: on the
     * ground, or still on the way up, it is the launcher; on the way down it is the spike.
     *
     * <p>This is deliberately not a client-side choice. The jump that goes with the input is applied
     * before the client's tick could look at it, so a client-side "am I on the ground" test turns almost
     * every launcher into a spike aimed at the ground the player is still standing on — which is what
     * made both moves look like they did nothing.
     */
    private static void aerialAttack(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        boolean goingUp = player.onGround() || player.getDeltaMovement().y > 0.0;
        if (goingUp) {
            launcher(ctx);
        } else {
            spike(ctx);
        }
    }

    /** Grounded jump+attack (aerial-combo styles only): pops everything in front into the air. */
    private static void launcher(SkillContext ctx) {
        TorikoData data = ctx.data();
        if (data.combatCooldown() > 0) {
            return;
        }
        if (!CombatStyles.get(data.combatStyle()).aerialCombo()) {
            attack(ctx, 0);
            return;
        }
        data.setGuarding(false);
        if (!data.spend(LAUNCHER_COST)) {
            attack(ctx, 0);
            return;
        }

        ServerPlayer player = ctx.player();
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        Hurt.playSound(ctx, player.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.9F);
        // The caster hops with the swing too, so it reads as one motion rather than the victim
        // floating up on its own.
        player.setDeltaMovement(player.getDeltaMovement().x, SELF_HOP_STRENGTH, player.getDeltaMovement().z);
        player.hurtMarked = true;

        for (LivingEntity victim : inFront(player)) {
            victim.invulnerableTime = 0;
            if (Hurt.apply(ctx, victim, ModDamageTypes.NAIL, ctx.damage(LAUNCHER_DAMAGE))) {
                Hurt.launch(victim, LAUNCH_DIRECTION, LAUNCH_STRENGTH);
                impact(ctx, victim, 0.8F);
            }
        }
        CombatAnimations.play(player, "launcher");
        data.resetGroupChains();
        data.setCombatCooldown(LAUNCHER_LOCK);
        SkillEngine.sync(player, data);
    }

    /** Airborne jump+attack (aerial-combo styles only): drives everything in front straight down. */
    private static void spike(SkillContext ctx) {
        TorikoData data = ctx.data();
        if (data.combatCooldown() > 0) {
            return;
        }
        if (!CombatStyles.get(data.combatStyle()).aerialCombo()) {
            attack(ctx, 0);
            return;
        }
        data.setGuarding(false);
        if (!data.spend(SPIKE_COST)) {
            attack(ctx, 0);
            return;
        }

        ServerPlayer player = ctx.player();
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        Hurt.playSound(ctx, player.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.5F);
        // The caster dives down with the strike, so a spiked victim and the caster crash down together.
        player.setDeltaMovement(player.getDeltaMovement().x, -SPIKE_SELF_FALL_SPEED, player.getDeltaMovement().z);
        player.hurtMarked = true;
        data.setFallImmuneTicks(SPIKE_FALL_IMMUNITY_TICKS);

        for (LivingEntity victim : inFront(player)) {
            victim.invulnerableTime = 0;
            if (Hurt.apply(ctx, victim, ModDamageTypes.NAIL_PIERCE, ctx.damage(SPIKE_DAMAGE))) {
                Hurt.launch(victim, new Vec3(0.0, -1.0, 0.0), SPIKE_STRENGTH);
                impact(ctx, victim, 1.0F);
            }
        }
        CombatAnimations.play(player, "spike");
        data.resetGroupChains();
        data.setCombatCooldown(SPIKE_LOCK);
        SkillEngine.sync(player, data);
    }

    private static void dodge(SkillContext ctx) {
        TorikoData data = ctx.data();
        if (data.combatCooldown() > 0 || !data.spend(DODGE_COST)) {
            return;
        }
        ServerPlayer player = ctx.player();
        // Dash the way the player is already moving; standing still means a backstep.
        Vec3 motion = player.getDeltaMovement().multiply(1.0, 0.0, 1.0);
        Vec3 direction = motion.lengthSqr() > 0.0025
                ? motion.normalize()
                : player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(-1.0);
        Vec3 dash = direction.scale(DODGE_SPEED);
        player.setDeltaMovement(dash.x, Math.max(player.getDeltaMovement().y, 0.1), dash.z);
        player.hurtMarked = true;

        CombatAnimations.play(player, "dodge");
        data.setDodgeTicks(DODGE_INVULNERABLE_TICKS);
        data.setCombatCooldown(DODGE_COOLDOWN);
        data.setGuarding(false);
        Hurt.playSound(ctx, player.position(), SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.8F, 1.6F);
        SkillEngine.sync(player, data);
    }

    // ------------------------------------------------------------ incoming damage

    /** Dodging ignores hits; guarding softens hits from the front, and a well-timed guard negates them. */
    public static void onIncomingDamage(ServerPlayer player, LivingIncomingDamageEvent event) {
        TorikoData data = ModAttachments.of(player);
        if (data.dodgeTicks() > 0) {
            event.setCanceled(true);
            return;
        }
        if (!data.isGuarding() || !data.isCombatMode()) {
            return;
        }
        Vec3 source = event.getSource().getSourcePosition();
        if (source == null) {
            return;
        }
        Vec3 toSource = source.subtract(player.getEyePosition());
        if (toSource.lengthSqr() < 1.0E-6 || player.getLookAngle().dot(toSource.normalize()) < GUARD_CONE) {
            return;
        }
        SkillContext ctx = new SkillContext(player, (ServerLevel) player.level(), data);
        if (data.guardTicks() <= PERFECT_GUARD_TICKS) {
            event.setCanceled(true);
            if (event.getSource().getEntity() instanceof LivingEntity attacker) {
                Hurt.knockAway(attacker, player.position(), 1.0);
            }
            Hurt.playSound(ctx, player.position(), SoundEvents.ANVIL_LAND, 0.6F, 1.8F);
        } else {
            event.setAmount(event.getAmount() * GUARD_DAMAGE_FACTOR);
            Hurt.playSound(ctx, player.position(), SoundEvents.SHIELD_BLOCK, 0.9F, 1.0F);
        }
    }

    // -------------------------------------------------------------------- helpers

    private static java.util.List<LivingEntity> inFront(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        return Targeting.inSphere(player, eye.add(look.scale(REACH * 0.5)), REACH).stream()
                .filter(victim -> {
                    Vec3 to = victim.getBoundingBox().getCenter().subtract(eye);
                    return to.lengthSqr() > 1.0E-6 && look.dot(to.normalize()) > CONE
                            && to.length() <= REACH + victim.getBbWidth();
                })
                .toList();
    }

    private static void lunge(ServerPlayer player, double strength) {
        Vec3 flat = player.getLookAngle().multiply(1.0, 0.0, 1.0);
        if (flat.lengthSqr() < 1.0E-6) {
            return;
        }
        Vec3 push = flat.normalize().scale(strength);
        player.setDeltaMovement(player.getDeltaMovement().add(push.x, 0.0, push.z));
        player.hurtMarked = true;
    }

    private static void impact(SkillContext ctx, LivingEntity victim, float scale) {
        FxDispatch.at(ctx.level(), SkillFx.COMBAT_IMPACT, victim.getBoundingBox().getCenter(),
                scale, ctx.player().getYRot(), 0.0F);
    }
}
