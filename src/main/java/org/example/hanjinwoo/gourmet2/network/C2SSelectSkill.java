package org.example.hanjinwoo.gourmet2.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.example.hanjinwoo.gourmet2.Gourmet2;

/** The client asking to make {@code index} the active skill. The server clamps and confirms. */
public record C2SSelectSkill(int index) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<C2SSelectSkill> TYPE =
            new CustomPacketPayload.Type<>(Gourmet2.id("select_skill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SSelectSkill> CODEC =
            CustomPacketPayload.codec(C2SSelectSkill::write, C2SSelectSkill::new);

    private C2SSelectSkill(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(index);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
