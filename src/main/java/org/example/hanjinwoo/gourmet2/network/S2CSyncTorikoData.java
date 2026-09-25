package org.example.hanjinwoo.gourmet2.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.skill.LeapEngine;
import org.example.hanjinwoo.gourmet2.skill.SkillContext;
import org.example.hanjinwoo.gourmet2.skill.combat.CombatEngine;
import org.example.hanjinwoo.gourmet2.skill.impl.FlyingForkSkill;

import java.util.Arrays;

/** Mirrors the HUD/settings-GUI-relevant slice of {@link TorikoData} down to its owning client. */
public record S2CSyncTorikoData(int appetite, int maxAppetite, int selected, int awakenedTicks,
                                int activeSkill, int activeElapsed, int activeTotal, int[] cooldowns,
                                long cellXp, int intimidationStage, int chargingSkill, int chargeTicks,
                                int nailComboSetting, int forkProjectileSetting, int knifeWaveSetting,
                                float flyingDamageSetting, float flyingSizeSetting,
                                boolean kiActive, int kiOutputSetting,
                                float attackDamageSetting, float leapDistanceSetting, float rangeSetting,
                                float attackDialBase, float flyingDamageDialBase, float leapDialBase,
                                float rangeDialBase,
                                int[] skillSlots, boolean combatMode, String combatStyle, int nailGunShotSetting)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CSyncTorikoData> TYPE =
            new CustomPacketPayload.Type<>(Gourmet2.id("sync_toriko_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSyncTorikoData> CODEC =
            CustomPacketPayload.codec(S2CSyncTorikoData::write, S2CSyncTorikoData::new);

    public static S2CSyncTorikoData of(ServerPlayer player, TorikoData data) {
        var active = data.active();
        return new S2CSyncTorikoData(
                data.appetite(),
                data.maxAppetite(),
                data.selectedIndex(),
                data.awakenedTicks(),
                active == null ? -1 : active.type.ordinal(),
                active == null ? 0 : active.elapsed,
                active == null ? 0 : active.totalTicks,
                Arrays.copyOf(data.cooldownsView(), data.cooldownsView().length),
                data.cellXp(),
                data.intimidationStage(),
                data.chargingSkill() == null ? -1 : data.chargingSkill().ordinal(),
                data.chargeTicks(),
                data.nailComboSetting(),
                data.forkProjectileSetting(),
                data.knifeWaveSetting(),
                data.flyingDamageSetting(),
                data.flyingSizeSetting(),
                data.isKiActive(),
                data.kiOutputSetting(),
                data.attackDamageSetting(),
                data.leapDistanceSetting(),
                data.rangeSetting(),
                // What the ratio dials are worth in their own units — damage, and blocks — worked out here with
                // the very formulas the skills themselves use, so the settings screen can show real numbers and
                // no formula has to exist twice.
                // Both damage dials are shown against a blow with the attack dial wide open: the attack dial's own
                // number is a bare-handed blow, and the flying dial's is a prong. Neither is scaled by anybody's
                // current setting, so the ceiling the screen prints stays the ceiling — a player who has wound
                // their damage all the way down still reads what it is they are holding back from.
                CombatEngine.bareHandedBlow(player, data),
                new SkillContext(player, (ServerLevel) player.level(), data)
                        .damageAtFullDial(FlyingForkSkill.BASE_DAMAGE),
                (float) LeapEngine.maxDistance(player),
                (float) FlyingForkSkill.reach(data.cellLevel()),
                Arrays.copyOf(data.skillSlotsView(), data.skillSlotsView().length),
                data.isCombatMode(),
                data.combatStyle(),
                data.nailGunShotSetting());
    }

    private S2CSyncTorikoData(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt() - 1,
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarIntArray(),
                buf.readVarLong(),
                buf.readVarInt(),
                buf.readVarInt() - 1,
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readVarIntArray(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(appetite);
        buf.writeVarInt(maxAppetite);
        buf.writeVarInt(selected);
        buf.writeVarInt(awakenedTicks);
        buf.writeVarInt(activeSkill + 1);
        buf.writeVarInt(activeElapsed);
        buf.writeVarInt(activeTotal);
        buf.writeVarIntArray(cooldowns);
        buf.writeVarLong(cellXp);
        buf.writeVarInt(intimidationStage);
        buf.writeVarInt(chargingSkill + 1);
        buf.writeVarInt(chargeTicks);
        buf.writeVarInt(nailComboSetting);
        buf.writeVarInt(forkProjectileSetting);
        buf.writeVarInt(knifeWaveSetting);
        buf.writeFloat(flyingDamageSetting);
        buf.writeFloat(flyingSizeSetting);
        buf.writeBoolean(kiActive);
        buf.writeVarInt(kiOutputSetting);
        buf.writeFloat(attackDamageSetting);
        buf.writeFloat(leapDistanceSetting);
        buf.writeFloat(rangeSetting);
        buf.writeFloat(attackDialBase);
        buf.writeFloat(flyingDamageDialBase);
        buf.writeFloat(leapDialBase);
        buf.writeFloat(rangeDialBase);
        buf.writeVarIntArray(skillSlots);
        buf.writeBoolean(combatMode);
        buf.writeUtf(combatStyle);
        buf.writeVarInt(nailGunShotSetting);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
