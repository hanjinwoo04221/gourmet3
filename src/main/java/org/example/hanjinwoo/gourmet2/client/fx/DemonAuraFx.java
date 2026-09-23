package org.example.hanjinwoo.gourmet2.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.client.demon.DemonRegistry;

import java.util.List;

/**
 * Client side of the sustained auras (Intimidation stage 1 and Ki Release): keeps the effect file
 * that matches the demon's cell colour playing on the caster for as long as the owning entity lives.
 */
public final class DemonAuraFx {
    /** How often the emitter is checked and restarted if the effect file has run out. */
    private static final int CHECK_INTERVAL = 5;
    private static final Vec3 TORSO = new Vec3(0.0, 1.0, 0.0);

    private DemonAuraFx() {}

    private static List<FxPart> parts(String kind, float scale) {
        var candidates = DemonRegistry.active().effectCandidates(kind);
        return List.of(FxPart.onBody(scale, TORSO, candidates.toArray(String[]::new)));
    }

    /** @param kind "intimidation" or "ki"; @param active false stops the effect instead */
    public static void tick(Entity source, Entity owner, String kind, boolean active, float scale, int tickCount) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || tickCount % CHECK_INTERVAL != 0) {
            return;
        }
        String key = kind + "_" + source.getId();
        if (active) {
            FxPlayer.keepAlive(level, parts(kind, scale), owner, 1.0F, key);
        } else {
            FxPlayer.stopSustained(parts(kind, scale), key);
        }
    }

    public static void stop(Entity source, String kind) {
        FxPlayer.stopSustained(parts(kind, 1.0F), kind + "_" + source.getId());
    }
}
