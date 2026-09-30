package org.example.hanjinwoo.gourmet2.client;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.network.C2SCling;
import org.example.hanjinwoo.gourmet2.skill.PlayerCling;

import java.util.HashMap;
import java.util.Map;

/**
 * The client's half of the wall and ceiling cling: the toggle key, the movement of the local player, and the pose
 * every hanging player is drawn in. On a wall the body is turned to face it; from a ceiling it is drawn upside down,
 * with its feet on the ceiling, hanging on by them.
 */
@EventBusSubscriber(modid = Gourmet2.MODID, value = Dist.CLIENT)
public final class ClientCling {
    /** Entity id to how that player is hanging and the yaw to draw them facing. */
    private static final Map<Integer, float[]> STATES = new HashMap<>();
    private static boolean on;
    private static int lastMode;
    private static int idleTicks;
    /** Ticks with the key on but nothing to hang from before it switches itself off. */
    private static final int GIVE_UP_TICKS = 40;

    private ClientCling() {}

    /** The leap key came up: whatever it does, it takes the body off the wall or ceiling. */
    public static void leaveSurface() {
        Minecraft minecraft = Minecraft.getInstance();
        if (lastMode != PlayerCling.OFF && minecraft.player != null) {
            on = false;
            PlayerCling.clientLock(minecraft.player, 12);
        }
    }

    /** From the server: how another player is hanging. */
    public static void set(int entityId, int mode, float yaw) {
        if (mode == PlayerCling.OFF) {
            STATES.remove(entityId);
        } else {
            STATES.put(entityId, new float[] {mode, yaw});
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || Minecraft.getInstance().player != player) {
            return;
        }
        boolean inGame = Minecraft.getInstance().screen == null;
        while (ModKeys.CLING.consumeClick()) {
            if (inGame) {
                on = !on;
                idleTicks = 0;
            }
        }
        int mode = PlayerCling.tick(player, on, player.input.forwardImpulse, player.input.leftImpulse,
                player.input.jumping, player.input.shiftKeyDown);
        // Nothing to hang from for a while: the toggle switches itself off rather than waiting to grab a wall later.
        if (on && mode == PlayerCling.OFF && ++idleTicks > GIVE_UP_TICKS) {
            on = false;
        } else if (mode != PlayerCling.OFF) {
            idleTicks = 0;
        }
        if (mode != lastMode) {
            lastMode = mode;
            PacketDistributor.sendToServer(new C2SCling(mode, PlayerCling.clientYaw()));
        }
        if (mode == PlayerCling.OFF) {
            STATES.remove(player.getId());
        } else {
            STATES.put(player.getId(), new float[] {mode, PlayerCling.clientYaw()});
        }
    }

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        float[] state = STATES.get(player.getId());
        if (state != null && (int) state[0] == PlayerCling.WALL) {
            player.yBodyRot = state[1];
            player.yBodyRotO = state[1];
        }
    }

    private static boolean epicFightPose() {
        return org.example.hanjinwoo.gourmet2.compat.CombatAnimations.hasSkillClip("cling_ceiling");
    }

    @SubscribeEvent
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        float[] state = STATES.get(player.getId());
        if (state == null) {
            return;
        }
        if ((int) state[0] == PlayerCling.CEILING && !epicFightPose()) {
            // Upside down about the line the player is facing along, feet on the ceiling.
            float yaw = player.yBodyRot;
            float height = player.getBbHeight();
            var pose = event.getPoseStack();
            pose.translate(0.0F, height / 2.0F, 0.0F);
            pose.mulPose(Axis.YP.rotationDegrees(-yaw));
            pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.translate(0.0F, -height / 2.0F, 0.0F);
        }
    }

}
