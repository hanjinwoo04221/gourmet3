package org.example.hanjinwoo.gourmet2.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.compat.CombatAnimations;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The player's wall and ceiling cling. Press the cling key to switch it on, and again to let go. While it is on and the
 * body is against a wall it hangs there: forward climbs, back (or sneak) climbs down, left and right move across it,
 * jump kicks off it. With a ceiling overhead (and the player looking up, or no wall beside them) it hangs from the
 * ceiling instead: the movement keys crawl across it, and jump lets go and drops.
 *
 * <p>The client moves the body (a player moves itself and the server takes its positions, exactly as it does on a
 * ladder), so the movement runs only there ({@link #tick}). The server's part is to know how the player is hanging
 * and keep a hanging body from collecting fall damage for the time it was up there ({@link #serverTick}).
 */
public final class PlayerCling {
    public static final int OFF = 0;
    public static final int WALL = 1;
    public static final int CEILING = 2;

    /** Ticks after a wall jump before the wall can be clung to again, so the jump actually leaves it. */
    private static final int REGRAB_TICKS = 8;
    /** Looking up at least this far above level picks the ceiling over a wall beside the player. */
    private static final float CEILING_PITCH = -50.0F;

    private static final Map<UUID, Integer> SERVER_MODE = new HashMap<>();
    /** Client-only state of the local player. */
    private static int clientLockedUntil;
    private static float clientYaw;

    private PlayerCling() {}

    /** How a client says it is hanging. */
    public static void setMode(UUID player, int mode) {
        if (mode == OFF) {
            SERVER_MODE.remove(player);
        } else {
            SERVER_MODE.put(player, mode);
        }
    }

    public static void forget(UUID player) {
        SERVER_MODE.remove(player);
    }

    /** Whether the player is hanging on a wall or ceiling that is really there. */
    public static boolean isClinging(ServerPlayer player) {
        int mode = SERVER_MODE.getOrDefault(player.getUUID(), OFF);
        return mode == WALL && WallCling.wallNormal(player) != null || mode == CEILING && WallCling.ceilingAbove(player);
    }

    /** Client: let go now and do not grab again for a few ticks (a leap or a flash step is leaving the wall). */
    public static void clientLock(Player player, int ticks) {
        clientLockedUntil = player.tickCount + ticks;
    }

    private static final Map<UUID, String> LAST_CLIP = new HashMap<>();
    private static final Map<UUID, Integer> LAST_PLAYED = new HashMap<>();

    /**
     * Server, every tick: a player hanging on a wall or a ceiling does not build up a fall, and plays the hanging
     * clip for what they are doing (holding, or moving across it), re-played before it could run out.
     */
    public static void serverTick(ServerPlayer player) {
        UUID id = player.getUUID();
        int mode = SERVER_MODE.getOrDefault(id, OFF);
        if (!isClinging(player)) {
            if (LAST_CLIP.remove(id) != null) {
                LAST_PLAYED.remove(id);
                CombatAnimations.play(player, "stop");
            }
            return;
        }
        player.fallDistance = 0.0F;
        boolean moving = player.position().distanceToSqr(player.xo, player.yo, player.zo) > 0.04 * 0.04;
        String clip = (mode == WALL ? "cling_wall" : "cling_ceiling") + (moving ? "_move" : "");
        int period = moving ? 18 : 8;
        int now = player.tickCount;
        if (!clip.equals(LAST_CLIP.get(id)) || now - LAST_PLAYED.getOrDefault(id, 0) >= period) {
            if (CombatAnimations.hasSkillClip(clip)) {
                CombatAnimations.playSkill(player, clip);
            }
            LAST_CLIP.put(id, clip);
            LAST_PLAYED.put(id, now);
        }
    }

    /** The yaw the local player's body should be drawn facing, as of the last {@link #tick}. */
    public static float clientYaw() {
        return clientYaw;
    }

    /**
     * Client, before the local player moves: one tick of clinging.
     *
     * @return {@link #OFF}, {@link #WALL} or {@link #CEILING}
     */
    public static int tick(Player player, boolean on, float forward, float strafe, boolean jumping,
            boolean sneaking) {
        if (!on || player.isSpectator() || player.getAbilities().flying || player.isFallFlying()
                || player.isInWater() || player.isPassenger()) {
            return OFF;
        }
        int now = player.tickCount;
        if (now < clientLockedUntil) {
            return OFF;
        }
        Vec3 normal = WallCling.wallNormal(player);
        boolean roof = WallCling.ceilingAbove(player);
        boolean ceiling = roof && (normal == null || player.getXRot() < CEILING_PITCH);

        if (ceiling) {
            player.fallDistance = 0.0F;
            clientYaw = player.getYRot();
            if (jumping) {
                // Let go: drop, and do not catch the ceiling again straight away.
                player.setDeltaMovement(player.getDeltaMovement().x, -0.2, player.getDeltaMovement().z);
                clientLockedUntil = now + REGRAB_TICKS;
                return OFF;
            }
            player.setDeltaMovement(WallCling.ceilingVelocity(forward, strafe, player.getYRot()));
            return CEILING;
        }
        if (normal == null) {
            return OFF;
        }
        // Standing at the foot of a wall is not hanging on it: it takes a push upward to leave the ground.
        if (player.onGround() && forward <= 0.0F) {
            return OFF;
        }
        player.fallDistance = 0.0F;
        clientYaw = (float) (Mth.atan2(normal.x, -normal.z) * Mth.RAD_TO_DEG);
        if (jumping) {
            player.setDeltaMovement(normal.x * WallCling.JUMP_AWAY, WallCling.JUMP_UP, normal.z * WallCling.JUMP_AWAY);
            clientLockedUntil = now + REGRAB_TICKS;
            return OFF;
        }
        player.setDeltaMovement(WallCling.velocity(normal, sneaking ? -1.0 : forward, strafe));
        return WALL;
    }
}
