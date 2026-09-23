package org.example.hanjinwoo.gourmet2.client.weapon;

import org.example.hanjinwoo.gourmet2.fx.SkillFx;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Which {@link SkillFx} "cast" moments should flash a cutlery shape over the caster's limb, and
 * which shape/limb to use. Purely a lookup table — {@code ClientPacketHandlers} consults it
 * whenever a skill fx packet arrives, so no extra network traffic is needed for this feature.
 */
public final class WeaponFlashRegistry {
    private static final Map<SkillFx, Entry> ENTRIES = new EnumMap<>(SkillFx.class);

    static {
        define(SkillFx.NAIL_PUNCH_CAST, WeaponFlashType.NAIL, WeaponFlashLimb.ARM);
        define(SkillFx.NAIL_GUN_CAST, WeaponFlashType.NAIL, WeaponFlashLimb.ARM);
        define(SkillFx.THIRTEEN_LOCK, WeaponFlashType.NAIL, WeaponFlashLimb.ARM);
        define(SkillFx.FORK_CAST, WeaponFlashType.FORK, WeaponFlashLimb.ARM);
        define(SkillFx.FLYING_FORK_CAST, WeaponFlashType.FORK, WeaponFlashLimb.ARM);
        define(SkillFx.KNIFE_CAST, WeaponFlashType.KNIFE, WeaponFlashLimb.ARM);
        define(SkillFx.LEG_KNIFE_CAST, WeaponFlashType.KNIFE, WeaponFlashLimb.LEG);
        define(SkillFx.FLYING_KNIFE_CAST, WeaponFlashType.KNIFE, WeaponFlashLimb.ARM);
    }

    private WeaponFlashRegistry() {}

    private static void define(SkillFx fx, WeaponFlashType type, WeaponFlashLimb limb) {
        ENTRIES.put(fx, new Entry(type, limb));
    }

    public static @Nullable Entry lookup(SkillFx fx) {
        return ENTRIES.get(fx);
    }

    public record Entry(WeaponFlashType type, WeaponFlashLimb limb) {}
}
