package org.example.hanjinwoo.gourmet2.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.fx.SkillFx;

/**
 * "Play this visual." The client resolves {@link SkillFx} into concrete Effekseer effects, so one
 * packet can produce a composite of several emitters.
 *
 * @param fx       ordinal of the {@link SkillFx} to play
 * @param entityId entity to attach the effect to, or -1 to place it at {@code pos} in world space
 * @param pos      world position, only meaningful when {@code entityId} is -1
 * @param scale    extra scale multiplier on top of each part's own scale
 * @param yawDeg   Minecraft yaw the effect should face
 * @param pitchDeg Minecraft pitch the effect should face
 */
public record S2CSkillFx(int fx, int entityId, Vec3 pos, float scale, float yawDeg, float pitchDeg)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CSkillFx> TYPE =
            new CustomPacketPayload.Type<>(Gourmet2.id("skill_fx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSkillFx> CODEC =
            CustomPacketPayload.codec(S2CSkillFx::write, S2CSkillFx::new);

    private S2CSkillFx(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt(),
                buf.readVarInt() - 1,
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(fx);
        // Offset by one so the "no entity" sentinel (-1) still encodes as a VarInt cheaply.
        buf.writeVarInt(entityId + 1);
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
        buf.writeFloat(scale);
        buf.writeFloat(yawDeg);
        buf.writeFloat(pitchDeg);
    }

    public SkillFx effect() {
        return SkillFx.byIndex(fx);
    }

    public boolean isAttached() {
        return entityId >= 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
