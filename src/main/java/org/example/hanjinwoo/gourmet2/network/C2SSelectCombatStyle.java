package org.example.hanjinwoo.gourmet2.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.example.hanjinwoo.gourmet2.Gourmet2;

/** The client choosing a combat style by id; the server ignores ids it does not know. */
public record C2SSelectCombatStyle(String id) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<C2SSelectCombatStyle> TYPE =
            new CustomPacketPayload.Type<>(Gourmet2.id("select_combat_style"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SSelectCombatStyle> CODEC =
            CustomPacketPayload.codec(C2SSelectCombatStyle::write, C2SSelectCombatStyle::new);

    private C2SSelectCombatStyle(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf(64));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(id, 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
