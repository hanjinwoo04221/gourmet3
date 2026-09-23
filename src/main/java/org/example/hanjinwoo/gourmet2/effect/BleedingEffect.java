package org.example.hanjinwoo.gourmet2.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.example.hanjinwoo.gourmet2.registry.ModDamageTypes;

/**
 * Bleeding, inflicted by the Fork techniques. Deals a small amount of unblockable damage on a
 * fixed cadence and cannot land the killing blow — it always leaves the victim on a sliver of
 * health, so it pressures rather than executes.
 */
public class BleedingEffect extends MobEffect {
    /** Ticks between bleed ticks at amplifier 0. Higher amplifiers bleed faster. */
    private static final int BASE_INTERVAL = 30;
    private static final float DAMAGE_PER_TICK = 1.5F;

    public BleedingEffect() {
        super(MobEffectCategory.HARMFUL, 0xB3131C);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int interval = Math.max(5, BASE_INTERVAL >> Math.min(3, amplifier));
        return duration % interval == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide()) {
            return true;
        }
        float damage = DAMAGE_PER_TICK * (amplifier + 1);
        if (entity.getHealth() - damage <= 1.0F) {
            return true;
        }
        entity.invulnerableTime = 0;
        entity.hurt(ModDamageTypes.source(entity.level(), ModDamageTypes.BLEEDING, null), damage);
        return true;
    }
}
