package org.example.hanjinwoo.gourmet2.skill;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.example.hanjinwoo.gourmet2.Gourmet2;

/** The branches the skills are grouped into. Each skill in a tree unlocks at its own Gourmet Cell level. */
public enum SkillTree {
    TECHNIQUE("technique", 0xFFD24B),
    KI("ki", 0xFFE066),
    FOOD_IMMERSION("food_immersion", 0x66FF9E),
    DEMON("demon", 0xB44CFF),
    ICHIRYU("ichiryu", 0xE8E8E8);

    public static final SkillTree[] VALUES = values();

    private final String id;
    private final int color;

    SkillTree(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public int color() {
        return color;
    }

    public MutableComponent displayName() {
        return Component.translatable("skill_tree." + Gourmet2.MODID + "." + id);
    }

    /** The tree's skills, ordered by the level they unlock at. */
    public java.util.List<SkillType> skills() {
        return java.util.Arrays.stream(SkillType.VALUES)
                .filter(skill -> skill.tree() == this)
                .sorted(java.util.Comparator.comparingInt(SkillType::unlockLevel))
                .toList();
    }
}
