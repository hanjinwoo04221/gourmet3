package org.example.hanjinwoo.gourmet2.skill;

import net.minecraft.network.chat.Component;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.jetbrains.annotations.Nullable;

/**
 * The actual mechanics of one technique. Implementations are stateless singletons — all mutable
 * state belongs on {@link ActiveSkill} or the player's {@link org.example.hanjinwoo.gourmet2.data.TorikoData}.
 */
public interface SkillBehavior {
    /**
     * Fires the skill. Called after cooldown/Appetite checks have passed but <em>before</em> the
     * cost is charged, so returning {@code false} cleanly aborts with no cost and no cooldown.
     *
     * @return {@code false} if the skill could not take effect (e.g. nothing in range).
     */
    boolean activate(SkillContext ctx);

    /**
     * Extra Appetite on top of {@link SkillType#appetiteCost()}, scaled by how far the player has
     * dialled up this skill's player-tuned power settings (see the settings GUI and
     * {@link org.example.hanjinwoo.gourmet2.skill.CellEvolution}). Zero for skills with no tunables.
     */
    default int extraAppetiteCost(TorikoData data) {
        return 0;
    }

    /**
     * Additional per-skill gate checked before anything is charged. Return a failure message to
     * reject the activation, or {@code null} to allow it.
     */
    default @Nullable Component checkUsable(SkillContext ctx) {
        return null;
    }

    /** Called once per tick while a multi-tick skill started by {@link #startActive} is running. */
    default void tick(SkillContext ctx, ActiveSkill active) {}

    /**
     * Called when a multi-tick skill ends, either because it ran out of ticks or because it was
     * interrupted (channel broken, player died, dimension change).
     */
    default void finish(SkillContext ctx, ActiveSkill active, boolean interrupted) {}

    /**
     * The multi-tick state this skill wants to run after a successful {@link #activate}, or
     * {@code null} for instant skills.
     */
    default @Nullable ActiveSkill startActive(SkillContext ctx) {
        return null;
    }

    /**
     * For {@link SkillType.InputMode#CHARGE} skills only: called once when the use-key is
     * released, with how many ticks it was held down. Unlike {@link #activate}, this method is
     * responsible for spending Appetite, setting the cooldown and starting any {@link ActiveSkill}
     * follow-through itself (via {@link SkillContext#data()}) — {@code SkillEngine} does not do any
     * of that centrally here, since exactly how much of each depends on how charged the release was.
     */
    default void releaseCharge(SkillContext ctx, int chargeTicksElapsed) {}

    /**
     * For {@link SkillType.InputMode#TOGGLE} skills only: called periodically (see
     * {@code SkillEngine}'s toggle drain interval) while the toggle is on, for a repeating
     * effect like Intimidation's aura pulse. {@link #activate} already covers the initial burst
     * on the press that turns it on.
     */
    default void tickToggle(SkillContext ctx) {}

    /**
     * For {@link SkillType.InputMode#TOGGLE} skills only: called once when the toggle turns off,
     * whether from the player pressing the key again or from Appetite running dry.
     */
    default void deactivateToggle(SkillContext ctx) {}

    /**
     * For {@link SkillType.InputMode#TOGGLE} skills only: called when pressing the key while the
     * toggle is already on escalates it to {@code stage} (2) instead of turning it off.
     */
    default void enterStage(SkillContext ctx, int stage) {}

    /**
     * For {@link SkillType.InputMode#AUTO} skills only: called once per tick while the use-key is
     * held down, with how many ticks it has been held so far (1 on the first tick). Unlike
     * {@link #releaseCharge}, this fires repeatedly — each call decides for itself, from the ticks
     * elapsed, whether this is the tick to fire another shot — and is responsible for spending its
     * own Appetite per shot, since {@code SkillEngine} does not charge anything centrally here.
     */
    default void tickHeld(SkillContext ctx, int ticksHeld) {}

    /**
     * For {@link SkillType.InputMode#AUTO} skills only: called once when the use-key is released.
     * Responsible for setting its own cooldown, since how long that should be does not depend on
     * anything {@code SkillEngine} tracks centrally for this input mode.
     */
    default void stopHeld(SkillContext ctx) {}
}
