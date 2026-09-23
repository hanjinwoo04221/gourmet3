package org.example.hanjinwoo.gourmet2.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.effect.BleedingEffect;

public final class ModMobEffects {
    public static final DeferredRegister<MobEffect> REGISTER =
            DeferredRegister.create(Registries.MOB_EFFECT, Gourmet2.MODID);

    public static final DeferredHolder<MobEffect, BleedingEffect> BLEEDING =
            REGISTER.register("bleeding", BleedingEffect::new);

    private ModMobEffects() {}

    public static Holder<MobEffect> bleeding() {
        return BLEEDING;
    }
}
