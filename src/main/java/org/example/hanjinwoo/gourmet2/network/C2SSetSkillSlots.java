package org.example.hanjinwoo.gourmet2.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.example.hanjinwoo.gourmet2.Gourmet2;

/** The skill selection screen saving which skill sits on each combat hotbar slot (-1 = empty). */
public record C2SSetSkillSlots(int[] slots) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<C2SSetSkillSlots> TYPE =
            new CustomPacketPayload.Type<>(Gourmet2.id("set_skill_slots"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SSetSkillSlots> CODEC =
            CustomPacketPayload.codec(C2SSetSkillSlots::write, C2SSetSkillSlots::new);

    private C2SSetSkillSlots(RegistryFriendlyByteBuf buf) {
        this(buf.readVarIntArray());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarIntArray(slots);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
