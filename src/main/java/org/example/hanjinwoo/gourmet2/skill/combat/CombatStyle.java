package org.example.hanjinwoo.gourmet2.skill.combat;

import net.minecraft.network.chat.Component;
import org.example.hanjinwoo.gourmet2.Gourmet2;

import java.util.ArrayList;
import java.util.List;

/**
 * One selectable way of fighting in combat mode.
 *
 * @param id            stable id, saved with the player and used in the language keys
 * @param animationDir  folder under {@code animmodels/animations/} holding this style's clips; every
 *                      style needs dodge, guard, guard_release, idle_stance, walk_stance and
 *                      run_stance there, plus launcher and spike if {@link #aerialCombo()} is true,
 *                      and one clip per move of its {@link AttackGroup}s
 * @param groups        the style's attack bundles in key order: {@code groups.get(0)} is on the attack
 *                      button, the rest are on the style's extra attack keys in order
 * @param attackSpeed   multiplies how quickly swings recover; 1.0 is the baseline (Bare-Handed), higher
 *                      is faster
 * @param aerialCombo   whether holding jump while attacking triggers the launcher (grounded) / spike
 *                      (airborne) moves instead of the normal attack
 */
public record CombatStyle(String id, String animationDir, List<AttackGroup> groups,
                          float attackSpeed, boolean aerialCombo) {

    public Component displayName() {
        return Component.translatable("combat_style." + Gourmet2.MODID + "." + id);
    }

    public Component description() {
        return Component.translatable("combat_style." + Gourmet2.MODID + "." + id + ".desc");
    }

    /** Every clip the style's attack groups play, in the order the groups and their moves are listed. */
    public List<String> attackClips() {
        List<String> clips = new ArrayList<>();
        for (AttackGroup group : groups) {
            for (ComboMove move : group.moves()) {
                clips.add(move.clip());
            }
        }
        return clips;
    }
}
