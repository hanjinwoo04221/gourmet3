package org.example.hanjinwoo.gourmet2;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import org.example.hanjinwoo.gourmet2.compat.CombatAnimations;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.example.hanjinwoo.gourmet2.network.ModNetwork;
import org.example.hanjinwoo.gourmet2.registry.ModAttachments;
import org.example.hanjinwoo.gourmet2.registry.ModEntities;
import org.example.hanjinwoo.gourmet2.registry.ModMobEffects;
import org.slf4j.Logger;

/**
 * Gourmet 2 - a Toriko skill-set mod.
 *
 * <p>Skills are executed on the logical server ({@code SkillEngine}) and their visuals are
 * played back on clients through AAA Particles / Effekseer ({@code client.fx}).
 */
@Mod(Gourmet2.MODID)
public class Gourmet2 {
    public static final String MODID = "gourmet2";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Gourmet2(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachments.REGISTER.register(modEventBus);
        ModEntities.REGISTER.register(modEventBus);
        ModMobEffects.REGISTER.register(modEventBus);

        modEventBus.addListener(ModNetwork::register);
        CombatAnimations.init(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
