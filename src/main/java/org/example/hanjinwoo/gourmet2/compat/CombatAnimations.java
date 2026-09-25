package org.example.hanjinwoo.gourmet2.compat;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

/** Plays combat-mode animations through Epic Fight when it is installed, and does nothing otherwise. */
public final class CombatAnimations {
    private static final String EPIC_FIGHT = "epicfight";

    private CombatAnimations() {}

    /** Whether the player holds an Epic Fight weapon, in which case that weapon's own animations and attacks are used. */
    public static boolean holdsWeapon(net.minecraft.world.entity.player.Player player) {
        return available() && EpicFightCompat.holdsWeapon(player);
    }

    public static boolean available() {
        return ModList.get() != null && ModList.get().isLoaded(EPIC_FIGHT);
    }

    /** Called once from the mod constructor. */
    public static void init(IEventBus modEventBus) {
        if (available()) {
            EpicFightCompat.init(modEventBus);
        }
    }

    public static void tick(ServerPlayer player, boolean guarding, boolean combatMode) {
        if (available()) {
            EpicFightCompat.tick(player, guarding, combatMode);
        }
    }

    public static void setCombatMode(ServerPlayer player, boolean on) {
        if (available()) {
            EpicFightCompat.setCombatMode(player, on);
        }
    }

    /** Client side: whether Epic Fight sees its attack input held (false when Epic Fight is absent). */
    public static boolean epicFightAttackHeld() {
        return available() && EpicFightClientCompat.attackActive();
    }

    /** Client side: swaps the local player's idle / walk / run clips for the combat stance while combat mode is on. */
    public static void clientTick(boolean combatMode) {
        if (available()) {
            EpicFightClientCompat.tick(combatMode);
        }
    }

    /** Plays a skill's cast animation (clip name in {@code animations/skill/}). */
    public static void playSkill(ServerPlayer player, String clip) {
        if (available()) {
            EpicFightCompat.playSkill(player, clip);
        }
    }

    /**
     * Whether a skill clip of that name was registered. Callers that would otherwise play a clip that
     * may not exist — a charged skill's held pose, which not every charged skill has — ask first, so a
     * missing one is skipped rather than logged.
     */
    public static boolean hasSkillClip(String clip) {
        return available() && EpicFightCompat.hasSkillClip(clip);
    }

    public static void play(ServerPlayer player, String name) {
        if (available()) {
            EpicFightCompat.play(player, name);
        }
    }
}
