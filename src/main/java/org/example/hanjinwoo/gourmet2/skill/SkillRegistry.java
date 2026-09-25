package org.example.hanjinwoo.gourmet2.skill;

import org.example.hanjinwoo.gourmet2.skill.impl.ChopstickAsuraSkill;
import org.example.hanjinwoo.gourmet2.skill.impl.ChopstickFistSkill;
import org.example.hanjinwoo.gourmet2.skill.impl.ChopstickSingleSkill;
import org.example.hanjinwoo.gourmet2.skill.impl.MinorityWorldSkill;
import org.example.hanjinwoo.gourmet2.skill.impl.ChopstickFlurrySkill;
import org.example.hanjinwoo.gourmet2.skill.impl.ChopstickStabSkill;
import org.example.hanjinwoo.gourmet2.skill.impl.ChopsticksSkill;
import org.example.hanjinwoo.gourmet2.skill.impl.FlyingForkSkill;
import org.example.hanjinwoo.gourmet2.skill.impl.FlyingKnifeSkill;
import org.example.hanjinwoo.gourmet2.skill.impl.FoodImmersionSkill;
import org.example.hanjinwoo.gourmet2.skill.impl.ForkSkill;
import org.example.hanjinwoo.gourmet2.skill.impl.IntimidationSkill;
import org.example.hanjinwoo.gourmet2.skill.impl.KiReleaseSkill;
import org.example.hanjinwoo.gourmet2.skill.impl.KnifeSkill;
import org.example.hanjinwoo.gourmet2.skill.impl.LegKnifeSkill;
import org.example.hanjinwoo.gourmet2.skill.impl.NailGunSkill;
import org.example.hanjinwoo.gourmet2.skill.impl.NailPunchSkill;

import java.util.EnumMap;
import java.util.Map;

/** Binds each {@link SkillType} to its mechanics. */
public final class SkillRegistry {
    private static final Map<SkillType, SkillBehavior> BEHAVIORS = new EnumMap<>(SkillType.class);

    static {
        BEHAVIORS.put(SkillType.NAIL_PUNCH, new NailPunchSkill());
        BEHAVIORS.put(SkillType.NAIL_GUN, new NailGunSkill());
        BEHAVIORS.put(SkillType.FORK, new ForkSkill());
        BEHAVIORS.put(SkillType.KNIFE, new KnifeSkill());
        BEHAVIORS.put(SkillType.LEG_KNIFE, new LegKnifeSkill());
        BEHAVIORS.put(SkillType.FLYING_FORK, new FlyingForkSkill());
        BEHAVIORS.put(SkillType.FLYING_KNIFE, new FlyingKnifeSkill());
        BEHAVIORS.put(SkillType.INTIMIDATION, new IntimidationSkill(1));
        BEHAVIORS.put(SkillType.DEMON_FORM, new IntimidationSkill(2));
        BEHAVIORS.put(SkillType.KI_RELEASE, new KiReleaseSkill());
        BEHAVIORS.put(SkillType.FOOD_IMMERSION, new FoodImmersionSkill());
        BEHAVIORS.put(SkillType.CHOPSTICKS, new ChopsticksSkill());
        BEHAVIORS.put(SkillType.CHOPSTICK_FIST, new ChopstickFistSkill());
        BEHAVIORS.put(SkillType.CHOPSTICK_STAB, new ChopstickStabSkill());
        BEHAVIORS.put(SkillType.CHOPSTICK_FLURRY, new ChopstickFlurrySkill());
        BEHAVIORS.put(SkillType.CHOPSTICK_SINGLE, new ChopstickSingleSkill());
        BEHAVIORS.put(SkillType.CHOPSTICK_ASURA, new ChopstickAsuraSkill());
        BEHAVIORS.put(SkillType.MINORITY_WORLD, new MinorityWorldSkill());

        // Fail at class-load rather than mid-fight if a new SkillType is added without mechanics.
        for (SkillType type : SkillType.VALUES) {
            if (!BEHAVIORS.containsKey(type)) {
                throw new IllegalStateException("No SkillBehavior registered for " + type);
            }
        }
    }

    private SkillRegistry() {}

    public static SkillBehavior get(SkillType type) {
        return BEHAVIORS.get(type);
    }
}
