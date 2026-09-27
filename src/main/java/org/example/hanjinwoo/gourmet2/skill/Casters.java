package org.example.hanjinwoo.gourmet2.skill;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.example.hanjinwoo.gourmet2.entity.ClipPlayer;
import org.jetbrains.annotations.Nullable;

/** Who is actually casting: a player is themself, and a mob that fights through the player systems is its stand-in. */
public final class Casters {
    private Casters() {}

    /** Whether {@code other} is the mob behind a stand-in caster, or one of its own kind: never a victim. */
    public static boolean isOwnKind(Entity caster, Entity other) {
        return caster instanceof org.example.hanjinwoo.gourmet2.entity.MobDouble standIn
                && (other == standIn.owner() || other.getType() == standIn.owner().getType());
    }

    public static @Nullable Player playerOf(@Nullable Entity owner) {
        if (owner instanceof Player player) {
            return player;
        }
        if (owner instanceof ClipPlayer fighting && !owner.level().isClientSide()) {
            return fighting.fighter().twin();
        }
        return null;
    }
}
