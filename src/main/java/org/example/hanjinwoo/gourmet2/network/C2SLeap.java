package org.example.hanjinwoo.gourmet2.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.example.hanjinwoo.gourmet2.Gourmet2;

/**
 * The leap key's press and release. Only the edges travel — the wind-up itself is counted server side, so
 * a modified client cannot claim a longer charge than it held the key for. Like the skill key, no aim
 * data is carried: the server reads the player's own rotation.
 */
public record C2SLeap(boolean release) implements CustomPacketPayload {

    public static final C2SLeap CHARGE = new C2SLeap(false);
    public static final C2SLeap RELEASE = new C2SLeap(true);

    public static final CustomPacketPayload.Type<C2SLeap> TYPE =
            new CustomPacketPayload.Type<>(Gourmet2.id("leap"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SLeap> CODEC =
            CustomPacketPayload.codec(C2SLeap::write, C2SLeap::new);

    private C2SLeap(RegistryFriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(release);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
