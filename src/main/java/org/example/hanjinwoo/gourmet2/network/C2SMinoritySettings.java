package org.example.hanjinwoo.gourmet2.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.example.hanjinwoo.gourmet2.Gourmet2;

/** The Minority World settings screen saving which abilities are on and who they reach (see {@code MinorityWorld}). */
public record C2SMinoritySettings(int flags) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<C2SMinoritySettings> TYPE =
            new CustomPacketPayload.Type<>(Gourmet2.id("minority_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SMinoritySettings> CODEC =
            CustomPacketPayload.codec(C2SMinoritySettings::write, C2SMinoritySettings::new);

    private C2SMinoritySettings(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(flags);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
