package org.example.hanjinwoo.gourmet2.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.example.hanjinwoo.gourmet2.Gourmet2;

/** Up/Down while Chopsticks hover: {@code delta} is the sign of the step through the techniques. */
public record C2SChopstickMode(int delta) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<C2SChopstickMode> TYPE =
            new CustomPacketPayload.Type<>(Gourmet2.id("chopstick_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SChopstickMode> CODEC =
            CustomPacketPayload.codec(C2SChopstickMode::write, C2SChopstickMode::new);

    private C2SChopstickMode(RegistryFriendlyByteBuf buf) {
        this(buf.readInt());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(delta);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
