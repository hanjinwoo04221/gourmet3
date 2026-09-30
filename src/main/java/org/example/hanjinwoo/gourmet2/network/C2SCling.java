package org.example.hanjinwoo.gourmet2.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.example.hanjinwoo.gourmet2.Gourmet2;

/**
 * How the player is hanging now, sent when it changes: 0 not at all, 1 on a wall, 2 from a ceiling, and the yaw the
 * body should be drawn facing (the wall, for a wall). The server keeps it to spare a hanging body a fall, and tells
 * everyone tracking the player so their client can pose the model.
 */
public record C2SCling(int mode, float yaw) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<C2SCling> TYPE =
            new CustomPacketPayload.Type<>(Gourmet2.id("cling"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SCling> CODEC =
            CustomPacketPayload.codec(C2SCling::write, C2SCling::new);

    private C2SCling(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readFloat());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(mode);
        buf.writeFloat(yaw);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
