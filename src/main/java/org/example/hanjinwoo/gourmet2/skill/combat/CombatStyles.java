package org.example.hanjinwoo.gourmet2.skill.combat;

import java.util.List;

/**
 * Every combat style. To add one: append it here, add its animation clips under its own
 * {@code animationDir}, and add its language entries — {@code combat_style.gourmet2.<id>} and
 * {@code .desc} for the style, plus {@code combat_group.gourmet2.<group>} for each of its
 * {@link AttackGroup}s. The selection screen, saving and Epic Fight registration all pick it up from
 * this list.
 *
 * <p>A style's moves are bundled into {@link AttackGroup}s rather than forming one long chain. Each
 * group has its own key in combat mode and its own place in its chain, so a player picks the *form*
 * of the attack with the key and the *length* of the chain by how many times that key is pressed in
 * a row. Group 0 is always the attack button, so the plain attack is unchanged.
 */
public final class CombatStyles {
    /** Attack keys a style may use (the attack button plus the extra attack keys); combat state is sized by it. */
    public static final int MAX_GROUPS = 3;

    /** Bare hands: one three-hit combo. */
    public static final CombatStyle FIST = new CombatStyle("fist", "combat", List.of(
            new AttackGroup("basic", List.of(
                    new ComboMove("basic1", 5.0F, 12),
                    new ComboMove("basic2", 5.0F, 12),
                    new ComboMove("basic3", 9.0F, 16)))),
            1.0F, false);

    /**
     * Fast, aerial, both fists and feet — a mobile striker built around juggling: launch, then spike.
     * basic2 lands two kicks and basic3 two punches; each splits its damage over both impacts.
     */
    public static final CombatStyle ACROBATIC = new CombatStyle("acrobatic", "combat_acrobatic", List.of(
            new AttackGroup("basic", List.of(
                    new ComboMove("basic1", 4.0F, 12),
                    // Recovery is the whole clip: the second kick lands 12 ticks in, so letting the swing
                    // be cancelled early would drop it.
                    new ComboMove("basic2", 4.0F, 25, 25, 12),
                    new ComboMove("basic3", 7.0F, 13, 14, 4)))),
            1.35F, true);

    /** The fastest style: light knife-hand chops with quick lunges, and quick air moves. */
    public static final CombatStyle SWIFT = new CombatStyle("swift", "combat_swift", List.of(
            new AttackGroup("basic", List.of(
                    new ComboMove("basic1", 3.0F, 14),
                    new ComboMove("basic2", 3.0F, 14),
                    new ComboMove("basic3", 8.0F, 17)))),
            1.6F, true);

    /**
     * The heaviest style, and the only one that fights out of a dance. Three forms, one key each:
     * kicks (ginga's crescent, the spinning armada, the queixada finisher), open-hand strikes, and
     * spins built around a full cartwheel. Slower than the rest, so each blow lands harder.
     */
    public static final CombatStyle CAPOEIRA = new CombatStyle("capoeira", "combat_capoeira", List.of(
            new AttackGroup("kick", List.of(
                    new ComboMove("kick1", 5.0F, 14),
                    new ComboMove("kick2", 6.0F, 14),
                    new ComboMove("kick3", 11.0F, 17))),
            new AttackGroup("hand", List.of(
                    new ComboMove("hand1", 3.0F, 13),
                    new ComboMove("hand2", 4.0F, 13),
                    new ComboMove("hand3", 7.0F, 17))),
            new AttackGroup("spin", List.of(
                    new ComboMove("spin1", 5.0F, 14),
                    new ComboMove("spin2", 6.0F, 15),
                    new ComboMove("spin3", 10.0F, 20)))),
            1.2F, true);

    public static final List<CombatStyle> ALL = List.of(FIST, ACROBATIC, SWIFT, CAPOEIRA);

    static {
        // Combat state keeps one chain per group, sized by MAX_GROUPS: a style with more groups would
        // silently lose its place in the extra ones, so that is a mistake worth failing on.
        for (CombatStyle style : ALL) {
            if (style.groups().size() > MAX_GROUPS) {
                throw new IllegalStateException("combat style '" + style.id() + "' has " + style.groups().size()
                        + " attack groups, but CombatStyles.MAX_GROUPS is " + MAX_GROUPS);
            }
        }
    }

    private CombatStyles() {}

    /** The style with this id, or the first one for an unknown / empty id. */
    public static CombatStyle get(String id) {
        for (CombatStyle style : ALL) {
            if (style.id().equals(id)) {
                return style;
            }
        }
        return ALL.get(0);
    }

    public static boolean exists(String id) {
        return ALL.stream().anyMatch(style -> style.id().equals(id));
    }
}
