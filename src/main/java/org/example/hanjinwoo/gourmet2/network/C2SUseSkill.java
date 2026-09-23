package org.example.hanjinwoo.gourmet2.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.example.hanjinwoo.gourmet2.Gourmet2;

/**
 * "Fire my selected skill." Deliberately carries no aim data — the server reads the player's own
 * rotation, so a modified client cannot aim at something it is not looking at.
 */
public record C2SUseSkill() implements CustomPacketPayload {

    public static final C2SUseSkill INSTANCE = new C2SUseSkill();

    public static final CustomPacketPayload.Type<C2SUseSkill> TYPE =
            new CustomPacketPayload.Type<>(Gourmet2.id("use_skill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SUseSkill> CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
