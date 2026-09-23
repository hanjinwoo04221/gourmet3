package org.example.hanjinwoo.gourmet2.skill;

import net.minecraft.world.phys.Vec3;

/**
 * Server-side state for a skill that plays out over several ticks — a barrage, a combo, or a
 * channel. Exactly one can run per player at a time; see {@link SkillEngine}.
 */
public class ActiveSkill {
    public final SkillType type;
    public final int totalTicks;
    /** True for skills the player must stand still to complete (Food Immersion). */
    public final boolean channelled;
    /** Aim direction captured at activation, so a barrage does not snap around with the mouse. */
    public final Vec3 aimDirection;
    /** Player position at activation, used to detect a broken channel. */
    public final Vec3 anchor;

    /** Ticks already elapsed. */
    public int elapsed;
    /** Entity id of a locked-on target, or -1. */
    public int targetId = -1;
    /** How many hits the skill has landed so far. Meaning is per-skill. */
    public int hits;
    /** Total hits a charged Nail Punch combo will land, or shots a charged flying skill will fire. */
    public int comboHits;
    /** Charge fraction (0..1) a charged skill was released at. */
    public float power;

    public ActiveSkill(SkillType type, int totalTicks, boolean channelled, Vec3 aimDirection, Vec3 anchor) {
        this.type = type;
        this.totalTicks = totalTicks;
        this.channelled = channelled;
        this.aimDirection = aimDirection;
        this.anchor = anchor;
    }

    public boolean isFinished() {
        return elapsed >= totalTicks;
    }

    /** 0.0 at activation, 1.0 on the final tick. */
    public float progress() {
        return totalTicks <= 0 ? 1.0F : Math.min(1.0F, (float) elapsed / totalTicks);
    }
}
