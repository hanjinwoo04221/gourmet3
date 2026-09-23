package org.example.hanjinwoo.gourmet2.fx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.example.hanjinwoo.gourmet2.network.S2CSkillFx;

/**
 * Server-side entry point for playing a skill visual. Effects are broadcast to everyone close
 * enough to see them; clients that lack an effect file just draw less.
 */
public final class FxDispatch {
    /** Beyond this the Effekseer particles are too small to matter, so don't spend bandwidth. */
    private static final double VIEW_RADIUS = 72.0;

    private FxDispatch() {}

    /** Plays {@code fx} at a fixed point in the world. */
    public static void at(ServerLevel level, SkillFx fx, Vec3 pos) {
        at(level, fx, pos, 1.0F, 0.0F, 0.0F);
    }

    /** Plays {@code fx} at a fixed point, oriented along a Minecraft yaw/pitch. */
    public static void at(ServerLevel level, SkillFx fx, Vec3 pos, float scale, float yawDeg, float pitchDeg) {
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, VIEW_RADIUS,
                new S2CSkillFx(fx.ordinal(), -1, pos, scale, yawDeg, pitchDeg));
    }

    /** Plays {@code fx} attached to an entity, so it follows them for its whole lifetime. */
    public static void on(ServerLevel level, SkillFx fx, Entity entity) {
        on(level, fx, entity, 1.0F);
    }

    /** Plays {@code fx} attached to an entity, with an extra scale multiplier. */
    public static void on(ServerLevel level, SkillFx fx, Entity entity, float scale) {
        PacketDistributor.sendToPlayersNear(level, null, entity.getX(), entity.getY(), entity.getZ(), VIEW_RADIUS,
                new S2CSkillFx(fx.ordinal(), entity.getId(), entity.position(), scale,
                        entity.getYRot(), entity.getXRot()));
    }
}
