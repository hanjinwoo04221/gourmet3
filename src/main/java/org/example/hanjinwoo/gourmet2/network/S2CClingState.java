package org.example.hanjinwoo.gourmet2.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.example.hanjinwoo.gourmet2.Gourmet2;

/** Another player's cling state (see {@link C2SCling}), so their model can be drawn on the wall or ceiling. */
public record S2CClingState(int entityId, int mode, float yaw) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CClingState> TYPE =
            new CustomPacketPayload.Type<>(Gourmet2.id("cling_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CClingState> CODEC =
            CustomPacketPayload.codec(S2CClingState::write, S2CClingState::new);

    private S2CClingState(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt(), buf.readFloat());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(mode);
        buf.writeFloat(yaw);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
