package org.example.hanjinwoo.gourmet2.skill;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.example.hanjinwoo.gourmet2.Gourmet2;

/**
 * 捕獲レベル — how dangerous something is, as a single whole number. It is scored from the entity's
 * attributes (health, attack, armour, toughness, speed): the base value plus this mod's own
 * modifiers, so Intimidation's Demon Form raises it. Potion effects and equipment are ignored, which
 * keeps Intimidation stage 1's potion buffs out of it. Players additionally gain from their Gourmet
 * Cell level. Pure math, shared by client (HUD) and server (Intimidation).
 */
public final class CaptureLevel {
    private static final double HEALTH_WEIGHT = 0.5;
    private static final double ATTACK_WEIGHT = 2.0;
    private static final double ARMOR_WEIGHT = 1.0;
    private static final double TOUGHNESS_WEIGHT = 1.0;
    private static final double SPEED_WEIGHT = 10.0;
    /** Each Gourmet Cell level counts for this much score, on top of the caster's attributes. */
    private static final double CELL_LEVEL_WEIGHT = 12.0;
    /** Attribute score per capture level. A zombie lands around 2, an iron golem around 10, a Warden in the 20s. */
    private static final double SCORE_PER_LEVEL = 8.0;

    private CaptureLevel() {}

    /** @param cellLevel Gourmet Cell level for players; pass 0 for anything else */
    public static int of(LivingEntity entity, int cellLevel) {
        double score = base(entity, Attributes.MAX_HEALTH) * HEALTH_WEIGHT
                + base(entity, Attributes.ATTACK_DAMAGE) * ATTACK_WEIGHT
                + base(entity, Attributes.ARMOR) * ARMOR_WEIGHT
                + base(entity, Attributes.ARMOR_TOUGHNESS) * TOUGHNESS_WEIGHT
                + base(entity, Attributes.MOVEMENT_SPEED) * SPEED_WEIGHT
                + cellLevel * CELL_LEVEL_WEIGHT;
        return Math.max(0, (int) Math.floor(score / SCORE_PER_LEVEL));
    }

    private static double base(LivingEntity entity, Holder<Attribute> attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return 0.0;
        }
        // Same stacking order as vanilla: flat adds, then additive % of base, then multiplicative %.
        double base = instance.getBaseValue();
        double flat = 0.0;
        double additive = 0.0;
        double multiplier = 1.0;
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (!modifier.id().getNamespace().equals(Gourmet2.MODID)) {
                continue;
            }
            switch (modifier.operation()) {
                case ADD_VALUE -> flat += modifier.amount();
                case ADD_MULTIPLIED_BASE -> additive += modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> multiplier *= 1.0 + modifier.amount();
            }
        }
        return (base + flat + base * additive) * multiplier;
    }
}
