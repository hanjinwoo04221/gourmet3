package org.example.hanjinwoo.gourmet2.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.example.hanjinwoo.gourmet2.Gourmet2;

/** The client switching combat mode on or off. */
public record C2SCombatMode(boolean on) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<C2SCombatMode> TYPE =
            new CustomPacketPayload.Type<>(Gourmet2.id("combat_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SCombatMode> CODEC =
            CustomPacketPayload.codec(C2SCombatMode::write, C2SCombatMode::new);

    private C2SCombatMode(RegistryFriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(on);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
