package org.example.hanjinwoo.gourmet2.fx;

/**
 * Logical visual events. The server only ever names one of these; the client decides which
 * Effekseer effects actually make it up (see {@code client.fx.FxLibrary}), which keeps the
 * particle asset choice out of the gameplay code and lets the client fall back when an effect
 * file is missing.
 *
 * <p>The ordinal is the wire format — append new entries at the end.
 */
public enum SkillFx {
    NAIL_PUNCH_CAST,
    NAIL_PUNCH_IMPACT,
    NAIL_GUN_CAST,
    NAIL_GUN_IMPACT,
    THIRTEEN_LOCK,
    THIRTEEN_IMPACT,
    THIRTEEN_FINISH,
    FORK_CAST,
    FORK_IMPACT,
    KNIFE_CAST,
    KNIFE_IMPACT,
    LEG_KNIFE_CAST,
    FLYING_FORK_CAST,
    FLYING_FORK_TRAIL,
    FLYING_FORK_IMPACT,
    FLYING_KNIFE_CAST,
    FLYING_KNIFE_TRAIL,
    FLYING_KNIFE_IMPACT,
    INTIMIDATION_CAST,
    FOOD_IMMERSION_CHANNEL,
    FOOD_IMMERSION_BURST,
    AWAKENED_AURA,
    LEG_KNIFE_SLASH,
    /**
     * A blow landed in combat mode (any attack group, the launch and the spike). Kept separate from the
     * nail punch's impact so the bare-handed styles never borrow a skill's effect.
     */
    COMBAT_IMPACT,
    /**
     * A Nail Gun round in flight. Its own slot rather than the muzzle flash, because a round is thrown out
     * with pitch as well as yaw — see {@code FxLibrary} for why that needs a different anchor.
     */
    NAIL_SHOT_TRAIL;

    public static final SkillFx[] VALUES = values();

    public static SkillFx byIndex(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : NAIL_PUNCH_IMPACT;
    }
}
