package org.example.hanjinwoo.gourmet2.client;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.client.demon.DemonRegistry;
import org.example.hanjinwoo.gourmet2.client.fx.FxPlayer;
import org.example.hanjinwoo.gourmet2.client.hud.SkillHudLayer;
import org.example.hanjinwoo.gourmet2.client.model.ModModelLayers;
import org.example.hanjinwoo.gourmet2.client.model.ShockwaveRingModel;
import org.example.hanjinwoo.gourmet2.client.model.WeaponFlashModel;
import org.example.hanjinwoo.gourmet2.client.renderer.AppetiteDemonRenderer;
import org.example.hanjinwoo.gourmet2.client.renderer.FlyingForkRenderer;
import org.example.hanjinwoo.gourmet2.client.renderer.FlyingKnifeRenderer;
import org.example.hanjinwoo.gourmet2.client.renderer.ShockwaveRingRenderer;
import org.example.hanjinwoo.gourmet2.client.renderer.WeaponFlashLayer;
import org.example.hanjinwoo.gourmet2.registry.ModEntities;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/** Client-side mod bus registration: key bindings, HUD, entity renderers and models. */
@EventBusSubscriber(modid = Gourmet2.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeys.USE_SKILL);
        event.register(ModKeys.NEXT_SKILL);
        event.register(ModKeys.PREV_SKILL);
        event.register(ModKeys.SKILL_SETTINGS);
        event.register(ModKeys.SKILL_SLOTS);
        event.register(ModKeys.COMBAT_MODE);
        event.register(ModKeys.COMBAT_STYLE);
        event.register(ModKeys.DODGE);
        event.register(ModKeys.ATTACK_GROUP_2);
        event.register(ModKeys.ATTACK_GROUP_3);
        event.register(ModKeys.LEAP);
        event.register(ModKeys.MINORITY_SETTINGS);
        event.register(ModKeys.CHOPSTICKS_PREV);
        event.register(ModKeys.CHOPSTICKS_NEXT);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Gourmet2.id("skill_hud"), new SkillHudLayer());
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ModModelLayers.SHOCKWAVE_RING, ShockwaveRingModel::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.WEAPON_FLASH, WeaponFlashModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FLYING_FORK.get(), FlyingForkRenderer::new);
        event.registerEntityRenderer(ModEntities.FLYING_KNIFE.get(), FlyingKnifeRenderer::new);
        event.registerEntityRenderer(ModEntities.NAIL_SHOT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.APPETITE_DEMON.get(), AppetiteDemonRenderer::new);
        event.registerEntityRenderer(ModEntities.LEG_KNIFE_SLASH.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.KI_AURA.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.SHOCKWAVE_RING.get(), ShockwaveRingRenderer::new);
        event.registerEntityRenderer(ModEntities.THROWN_CHOPSTICK.get(), org.example.hanjinwoo.gourmet2.client.renderer.ThrownChopstickRenderer::new);
        event.registerEntityRenderer(ModEntities.CHOPSTICK_GHOST.get(), org.example.hanjinwoo.gourmet2.client.renderer.ChopstickGhostRenderer::new);
        event.registerEntityRenderer(ModEntities.LIZARDMAN.get(), org.example.hanjinwoo.gourmet2.client.renderer.LizardmanRenderer::new);
        event.registerEntityRenderer(ModEntities.MINORITY_WORLD.get(), org.example.hanjinwoo.gourmet2.client.renderer.MinorityWorldRenderer::new);
        event.registerEntityRenderer(ModEntities.CHOPSTICKS.get(), org.example.hanjinwoo.gourmet2.client.renderer.ChopsticksRenderer::new);
        // A crater draws only its own block displays, so its owner has nothing to render.
        event.registerEntityRenderer(ModEntities.UPHEAVAL.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
    }

    /** Effect availability is cached per resource-pack stack, so invalidate it on reload. */
    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> FxPlayer.clearCache());
        event.registerReloadListener(new DemonRegistry());
    }

    /** Attaches the cutlery flash layer to both player skins (default and slim arms). */
    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new WeaponFlashLayer(renderer, event.getContext()));
            }
        }
    }
}
