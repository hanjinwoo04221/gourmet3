package org.example.hanjinwoo.gourmet2.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.client.fx.FxPlayer;
import org.example.hanjinwoo.gourmet2.client.weapon.ClientWeaponFlashes;
import org.example.hanjinwoo.gourmet2.client.weapon.WeaponFlashRegistry;
import org.example.hanjinwoo.gourmet2.network.S2CSkillFx;
import org.example.hanjinwoo.gourmet2.network.S2CSyncTorikoData;

/**
 * Client-side payload handling. Only ever reached through the dist guard in
 * {@link org.example.hanjinwoo.gourmet2.network.ModNetwork}, so this class is never loaded on a
 * dedicated server.
 */
public final class ClientPacketHandlers {
    private ClientPacketHandlers() {}

    public static void onSync(S2CSyncTorikoData payload) {
        ClientTorikoData.accept(payload);
    }

    public static void onMinorityZone(org.example.hanjinwoo.gourmet2.network.S2CMinorityZone payload) {
        ClientMinorityWorld.accept(payload);
    }

    public static void onSkillFx(S2CSkillFx payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity target = payload.isAttached() ? level.getEntity(payload.entityId()) : null;
        // If the entity has not arrived on this client yet, fall back to the packet's position.
        Vec3 position = target != null ? target.position() : payload.pos();
        FxPlayer.play(level, payload.effect(), target, position,
                payload.scale(), payload.yawDeg(), payload.pitchDeg());

        // Cast moments double as triggers for the cutlery flash on the caster's limb.
        if (target != null) {
            WeaponFlashRegistry.Entry flash = WeaponFlashRegistry.lookup(payload.effect());
            if (flash != null) {
                ClientWeaponFlashes.trigger(target.getId(), flash.type(), flash.limb());
            }
        }
    }
}
