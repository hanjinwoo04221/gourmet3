package org.example.hanjinwoo.gourmet2.skill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.Config;
import org.example.hanjinwoo.gourmet2.data.TorikoData;

/**
 * Everything a skill behaviour needs about the caster, bundled so behaviours stay one-liners at
 * their call sites.
 */
public record SkillContext(ServerPlayer player, ServerLevel level, TorikoData data) {
    /** Damage bonus while the Gourmet Cells are awakened (Food Immersion). */
    public static final float AWAKENED_DAMAGE_BONUS = 0.4F;

    /**
     * Applies the config multiplier, the awakening bonus, the Gourmet Cell level and the player's own damage
     * dial to a skill's base damage. Every technique a player has — and the blows of their bare hands as well
     * as the techniques proper — comes through here, so evolving is felt across the whole kit rather than in
     * one skill's numbers. The dial can only take away from that (see {@code CellEvolution#ATTACK_DAMAGE_FLOOR}):
     * a player who has outgrown what they wanted to hit for can hold back without giving up the level.
     */
    public float damage(float base) {
        float value = (float) (base * Config.damageMultiplier);
        if (data.isAwakened()) {
            value *= 1.0F + AWAKENED_DAMAGE_BONUS;
        }
        return value * (1.0F + CellEvolution.skillDamageBonus(data.cellLevel())) * data.attackDamageSetting();
    }

    public Vec3 eyePosition() {
        return player.getEyePosition();
    }

    public Vec3 lookDirection() {
        return player.getLookAngle();
    }

    /** A point just in front of the caster's fist, used as the origin of cast visuals. */
    public Vec3 fistPosition() {
        return eyePosition().add(lookDirection().scale(0.8));
    }
}
