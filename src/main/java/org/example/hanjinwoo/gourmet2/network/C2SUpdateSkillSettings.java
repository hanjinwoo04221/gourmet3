package org.example.hanjinwoo.gourmet2.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.example.hanjinwoo.gourmet2.Gourmet2;

/**
 * The client asking to retune its player-tuned skill power settings (see the settings GUI). The
 * server re-clamps every value to the player's current Gourmet Cell level cap, so a modified
 * client cannot request more power than it has actually evolved.
 */
public record C2SUpdateSkillSettings(int nailComboSetting, int forkProjectileSetting, int knifeWaveSetting,
                                      float flyingDamageSetting, float flyingSizeSetting,
                                      int kiOutputSetting, float attackDamageSetting,
                                      float leapDistanceSetting, float rangeSetting)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<C2SUpdateSkillSettings> TYPE =
            new CustomPacketPayload.Type<>(Gourmet2.id("update_skill_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SUpdateSkillSettings> CODEC =
            CustomPacketPayload.codec(C2SUpdateSkillSettings::write, C2SUpdateSkillSettings::new);

    private C2SUpdateSkillSettings(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(nailComboSetting);
        buf.writeVarInt(forkProjectileSetting);
        buf.writeVarInt(knifeWaveSetting);
        buf.writeFloat(flyingDamageSetting);
        buf.writeFloat(flyingSizeSetting);
        buf.writeVarInt(kiOutputSetting);
        buf.writeFloat(attackDamageSetting);
        buf.writeFloat(leapDistanceSetting);
        buf.writeFloat(rangeSetting);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
