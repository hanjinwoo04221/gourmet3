package org.example.hanjinwoo.gourmet2.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.network.S2CMinorityZone;
import org.example.hanjinwoo.gourmet2.skill.MinorityWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The client's view of Minority World: the zones it has been told about, its own saved settings, and what the zones do
 * to the local player's body and camera. The local player moves itself, so the ground/liquid rules for it run here
 * (the server runs them for everything else); damage, healing, knockback and flight are decided on the server.
 */
@EventBusSubscriber(modid = Gourmet2.MODID, value = Dist.CLIENT)
public final class ClientMinorityWorld {
    private static final Map<UUID, MinorityWorld.Zone> ZONES = new HashMap<>();
    private static int myFlags = MinorityWorld.DEFAULT;

    private static final double SINK_STEP = 0.1;
    private static final double RISE_STEP = 0.12;
    private static final double WALK_THROUGH = 0.15;

    private ClientMinorityWorld() {}

    public static int myFlags() {
        return myFlags;
    }

    public static void accept(S2CMinorityZone payload) {
        LocalPlayer me = Minecraft.getInstance().player;
        if (payload.active()) {
            ZONES.put(payload.caster(), payload.zone());
        } else {
            ZONES.remove(payload.caster());
        }
        if (me != null && me.getUUID().equals(payload.caster())) {
            myFlags = payload.flags();
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ZONES.clear();
        myFlags = MinorityWorld.DEFAULT;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || ZONES.isEmpty()) {
            return;
        }
        int flags = MinorityWorld.flagsFor(ZONES.values(), player);
        if ((flags & MinorityWorld.SOLID_TO_LIQUID) != 0) {
            sinkThroughSolids(player);
        }
        if ((flags & MinorityWorld.LIQUID_TO_SOLID) != 0) {
            MinorityWorld.standOnFluid(player);
        }
    }

    /**
     * Solid ground behaving like a liquid: what the player stands on, or presses against, gives way. They sink into
     * it, pass through it in any direction they walk, and rise on the jump key, the way they would in water.
     */
    private static void sinkThroughSolids(LocalPlayer player) {
        player.noPhysics = true;
        player.resetFallDistance();
        boolean inside = !player.level().noCollision(player, player.getBoundingBox().deflate(1.0E-3));
        if (!(player.onGround() || player.horizontalCollision || inside)) {
            return;
        }
        Input input = player.input;
        double dx = 0.0;
        double dz = 0.0;
        if (inside || player.horizontalCollision) {
            float yaw = player.getYRot() * Mth.DEG_TO_RAD;
            double forward = input.forwardImpulse;
            double left = input.leftImpulse;
            double x = -Mth.sin(yaw) * forward + Mth.cos(yaw) * left;
            double z = Mth.cos(yaw) * forward + Mth.sin(yaw) * left;
            double length = Math.sqrt(x * x + z * z);
            if (length > 1.0E-4) {
                double speed = player.isShiftKeyDown() ? WALK_THROUGH * 0.4 : WALK_THROUGH;
                dx = x / length * speed;
                dz = z / length * speed;
            }
        }
        double dy = input.jumping ? RISE_STEP : -SINK_STEP;
        double floor = player.level().getMinBuildHeight() + 2.0;
        if (player.getY() + dy < floor) {
            dy = 0.0;
        }
        player.setPos(player.getX() + dx, player.getY() + dy, player.getZ() + dz);
        player.setDeltaMovement(Vec3.ZERO);
        player.setOnGround(false);
    }

    /** Inverted sight: the camera is turned over. */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || ZONES.isEmpty()) {
            return;
        }
        if ((MinorityWorld.flagsFor(ZONES.values(), player) & MinorityWorld.VISION) != 0) {
            event.setRoll(event.getRoll() + 180.0F);
        }
    }
}
