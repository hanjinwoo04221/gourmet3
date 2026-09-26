package org.example.hanjinwoo.gourmet2;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.item.CreativeModeTabs;
import org.example.hanjinwoo.gourmet2.compat.CombatAnimations;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.example.hanjinwoo.gourmet2.network.ModNetwork;
import org.example.hanjinwoo.gourmet2.registry.ModAttachments;
import org.example.hanjinwoo.gourmet2.registry.ModEntities;
import org.example.hanjinwoo.gourmet2.registry.ModEntityAttributes;
import org.example.hanjinwoo.gourmet2.registry.ModItems;
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
        ModItems.REGISTER.register(modEventBus);
        ModMobEffects.REGISTER.register(modEventBus);

        modEventBus.addListener(ModNetwork::register);
        modEventBus.addListener(ModEntityAttributes::register);
        modEventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
                event.accept(ModItems.LIZARDMAN_SPAWN_EGG.get());
            }
        });
        CombatAnimations.init(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
