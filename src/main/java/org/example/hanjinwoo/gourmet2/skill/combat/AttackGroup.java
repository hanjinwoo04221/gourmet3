package org.example.hanjinwoo.gourmet2.skill.combat;

import net.minecraft.network.chat.Component;
import org.example.hanjinwoo.gourmet2.Gourmet2;

import java.util.List;

/**
 * A bundle of moves that belong to the same kind of attack — hand strikes, kicks, spins. Every style
 * gives each of its groups its own key in combat mode (group 0 is the attack button), and how many
 * times in a row that key is pressed decides which move of the group comes out.
 *
 * <p>Groups are what keeps a style's moves from being one long chain: swapping between keys swaps
 * between forms, and each form keeps its own place in its own chain.
 *
 * @param id    stable id, used for the group's display name
 * @param moves the group's moves in order; the last one is the finisher
 */
public record AttackGroup(String id, List<ComboMove> moves) {

    public AttackGroup {
        if (moves.isEmpty()) {
            throw new IllegalArgumentException("attack group '" + id + "' has no moves");
        }
    }

    /** The move that {@code step} presses into, wrapping around at the end of the group. */
    public ComboMove move(int step) {
        return moves.get(Math.floorMod(step, moves.size()));
    }

    public boolean isFinisher(int step) {
        return Math.floorMod(step, moves.size()) == moves.size() - 1;
    }

    public Component displayName() {
        return Component.translatable("combat_group." + Gourmet2.MODID + "." + id);
    }
}
