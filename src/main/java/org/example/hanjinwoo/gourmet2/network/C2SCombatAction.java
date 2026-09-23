package org.example.hanjinwoo.gourmet2.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.example.hanjinwoo.gourmet2.Gourmet2;

/**
 * A combat-mode input: the ordinal of {@code CombatAction}, plus which attack group an {@code ATTACK}
 * came from. Carries no aim or move index — the server reads the player's own look direction and knows
 * how far each group's chain has advanced.
 */
public record C2SCombatAction(int action, int group) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<C2SCombatAction> TYPE =
            new CustomPacketPayload.Type<>(Gourmet2.id("combat_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SCombatAction> CODEC =
            CustomPacketPayload.codec(C2SCombatAction::write, C2SCombatAction::new);

    /** Every input except an attack uses group 0. */
    public C2SCombatAction(int action) {
        this(action, 0);
    }

    private C2SCombatAction(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeVarInt(group);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
