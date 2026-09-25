package org.example.hanjinwoo.gourmet2.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.example.hanjinwoo.gourmet2.client.ClientPacketHandlers;
import org.example.hanjinwoo.gourmet2.skill.LeapEngine;
import org.example.hanjinwoo.gourmet2.skill.SkillEngine;
import org.example.hanjinwoo.gourmet2.skill.combat.CombatEngine;

/**
 * Payload registration. Both sides must declare every payload for the connection to negotiate, so
 * registration lives here rather than being split by dist; the client-bound handler bodies are
 * guarded so the client-only classes are never loaded on a dedicated server.
 */
public final class ModNetwork {
    /**
     * Bump this whenever a payload's wire format changes, or when the ordinals of
     * {@link org.example.hanjinwoo.gourmet2.skill.SkillType} / {@link org.example.hanjinwoo.gourmet2.fx.SkillFx}
     * are reordered.
     */
    public static final String VERSION = "13";

    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);

        registrar.playToServer(C2SUseSkill.TYPE, C2SUseSkill.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                SkillEngine.use(player);
            }
        });

        registrar.playToServer(C2SReleaseSkill.TYPE, C2SReleaseSkill.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                SkillEngine.release(player);
            }
        });

        registrar.playToServer(C2SSelectSkill.TYPE, C2SSelectSkill.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                SkillEngine.select(player, payload.index());
            }
        });

        registrar.playToServer(C2SSetSkillSlots.TYPE, C2SSetSkillSlots.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                SkillEngine.setSkillSlots(player, payload.slots());
            }
        });

        registrar.playToServer(C2SCombatMode.TYPE, C2SCombatMode.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                SkillEngine.setCombatMode(player, payload.on());
            }
        });

        registrar.playToServer(C2SSelectCombatStyle.TYPE, C2SSelectCombatStyle.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                SkillEngine.selectCombatStyle(player, payload.id());
            }
        });

        registrar.playToServer(C2SCombatAction.TYPE, C2SCombatAction.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                CombatEngine.handle(player, payload.action(), payload.group());
            }
        });

        registrar.playToServer(C2SLeap.TYPE, C2SLeap.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                if (payload.release()) {
                    LeapEngine.release(player);
                } else {
                    LeapEngine.charge(player);
                }
            }
        });

        registrar.playToServer(C2SUpdateSkillSettings.TYPE, C2SUpdateSkillSettings.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                SkillEngine.updateSettings(player, payload.nailComboSetting(), payload.forkProjectileSetting(),
                        payload.knifeWaveSetting(), payload.flyingDamageSetting(), payload.flyingSizeSetting(),
                        payload.kiOutputSetting(), payload.attackDamageSetting(), payload.leapDistanceSetting(),
                        payload.rangeSetting(), payload.nailGunShotSetting());
            }
        });

        registrar.playToClient(S2CSyncTorikoData.TYPE, S2CSyncTorikoData.CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientPacketHandlers.onSync(payload);
            }
        });

        registrar.playToClient(S2CSkillFx.TYPE, S2CSkillFx.CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientPacketHandlers.onSkillFx(payload);
            }
        });
    }
}
