package org.example.hanjinwoo.gourmet2.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.example.hanjinwoo.gourmet2.Gourmet2;

/**
 * "Let go of the skill key." Only meaningful while a {@link org.example.hanjinwoo.gourmet2.skill.SkillType.InputMode#CHARGE}
 * skill is winding up; the server reads how long it was held from its own tick count, so this
 * carries no data either.
 */
public record C2SReleaseSkill() implements CustomPacketPayload {

    public static final C2SReleaseSkill INSTANCE = new C2SReleaseSkill();

    public static final CustomPacketPayload.Type<C2SReleaseSkill> TYPE =
            new CustomPacketPayload.Type<>(Gourmet2.id("release_skill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SReleaseSkill> CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
