package org.example.hanjinwoo.gourmet2.skill.combat;

import org.jetbrains.annotations.Nullable;

/**
 * Inputs available in combat mode.
 *
 * <p>{@link #ATTACK} carries the index of the {@link AttackGroup} whose key was pressed. Which move of
 * that group comes out is decided server side from how often the key has just been pressed, so every
 * group keeps its own chain and switching keys switches form without losing it.
 */
public enum CombatAction {
    /** One of the style's attack keys; the group index travels alongside it. */
    ATTACK,
    DODGE,
    GUARD_START,
    GUARD_END,
    /**
     * Jump+attack, on the styles that have aerial moves. Which of the two comes out — the launch or the
     * spike — is decided server side from where the player actually is, not guessed on the client: the
     * jump that goes with the input has already been applied by the time the client could look.
     */
    AERIAL_ATTACK;

    public static final CombatAction[] VALUES = values();

    public static @Nullable CombatAction byIndex(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : null;
    }
}
