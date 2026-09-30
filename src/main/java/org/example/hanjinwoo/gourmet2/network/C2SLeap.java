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
public record C2SLeap(boolean release, float forward, float strafe) implements CustomPacketPayload {

    /** The press edge, with the movement keys held (forward, and strafe toward the left, both -1..1). */
    public static C2SLeap charge(float forward, float strafe) {
        return new C2SLeap(false, forward, strafe);
    }

    /** The release edge, with the movement keys held. A short tap becomes a flash step toward them. */
    public static C2SLeap release(float forward, float strafe) {
        return new C2SLeap(true, forward, strafe);
    }

    public static final CustomPacketPayload.Type<C2SLeap> TYPE =
            new CustomPacketPayload.Type<>(Gourmet2.id("leap"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SLeap> CODEC =
            CustomPacketPayload.codec(C2SLeap::write, C2SLeap::new);

    private C2SLeap(RegistryFriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readFloat(), buf.readFloat());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(release);
        buf.writeFloat(forward);
        buf.writeFloat(strafe);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
