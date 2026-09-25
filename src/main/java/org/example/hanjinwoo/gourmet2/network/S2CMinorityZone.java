package org.example.hanjinwoo.gourmet2.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.skill.MinorityWorld;

import java.util.UUID;

/**
 * A Minority World zone starting, changing or ending, sent to everyone (the zone reaches other players' movement
 * and camera, so every client needs to know where it is). Also how a caster learns their own saved settings.
 */
public record S2CMinorityZone(UUID caster, boolean active, ResourceLocation dimension,
                              double x, double y, double z, double radius, int flags)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CMinorityZone> TYPE =
            new CustomPacketPayload.Type<>(Gourmet2.id("minority_zone"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CMinorityZone> CODEC =
            CustomPacketPayload.codec(S2CMinorityZone::write, S2CMinorityZone::new);

    public static S2CMinorityZone started(MinorityWorld.Zone zone) {
        return new S2CMinorityZone(zone.caster(), true, zone.dimension(),
                zone.center().x, zone.center().y, zone.center().z, zone.radius(), zone.flags());
    }

    /** The zone is gone (or was never up); {@code flags} are still the caster's saved settings. */
    public static S2CMinorityZone ended(UUID caster, int flags) {
        return new S2CMinorityZone(caster, false, ResourceLocation.withDefaultNamespace("overworld"),
                0.0, 0.0, 0.0, 0.0, flags);
    }

    public MinorityWorld.Zone zone() {
        return new MinorityWorld.Zone(caster, dimension, new Vec3(x, y, z), radius, flags);
    }

    private S2CMinorityZone(RegistryFriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readBoolean(), buf.readResourceLocation(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(caster);
        buf.writeBoolean(active);
        buf.writeResourceLocation(dimension);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeDouble(radius);
        buf.writeVarInt(flags);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
