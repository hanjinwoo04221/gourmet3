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

    /** Applies the config multiplier and the awakening bonus to a skill's base damage. */
    public float damage(float base) {
        float value = (float) (base * Config.damageMultiplier);
        if (data.isAwakened()) {
            value *= 1.0F + AWAKENED_DAMAGE_BONUS;
        }
        return value;
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
