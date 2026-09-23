package org.example.hanjinwoo.gourmet2.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.example.hanjinwoo.gourmet2.client.gui.CombatStyleScreen;
import org.example.hanjinwoo.gourmet2.client.gui.SkillSlotsScreen;
import org.example.hanjinwoo.gourmet2.compat.CombatAnimations;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.network.C2SCombatAction;
import org.example.hanjinwoo.gourmet2.network.C2SCombatMode;
import org.example.hanjinwoo.gourmet2.network.C2SLeap;
import org.example.hanjinwoo.gourmet2.skill.combat.CombatAction;
import org.example.hanjinwoo.gourmet2.skill.combat.CombatStyles;
import org.example.hanjinwoo.gourmet2.entity.AppetiteDemonEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.client.gui.SkillSettingsScreen;
import org.example.hanjinwoo.gourmet2.client.weapon.ClientWeaponFlashes;
import org.example.hanjinwoo.gourmet2.network.C2SReleaseSkill;
import org.example.hanjinwoo.gourmet2.network.C2SSelectSkill;
import org.example.hanjinwoo.gourmet2.network.C2SUseSkill;

/**
 * Client game-bus handling: ticks the HUD mirror and turns key presses into requests.
 *
 * <p>Skill selection is applied locally first and then confirmed by the server, so cycling feels
 * instant; activation is never predicted, because only the server knows whether it is allowed.
 */
@EventBusSubscriber(modid = Gourmet2.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    /** Tracks USE_SKILL's held state across ticks for press/release edge detection. */
    private static boolean useSkillWasDown;

    /** Same, for the leap key: held winds the leap up, released launches it. */
    private static boolean leapWasDown;

    private static int combatSavedSlot;
    private static final boolean[] hotbarKeyWasDown = new boolean[9];
    private static boolean guardWasDown;
    /** Set by every attack click (even one shorter than a tick), consumed by the next tick. */
    private static boolean attackClicked;
    private static boolean attackDownPrev;
    private static final int COMBAT_MODE_RESEND_TICKS = 40;
    private static int combatModeResend;

    private ClientEvents() {}

    /** Attack / use clicks become combat actions instead of hitting or using the held item. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        var player = Minecraft.getInstance().player;
        if (ClientTorikoData.isCombatMode() && player != null && !CombatAnimations.holdsWeapon(player)
                && (event.isAttack() || event.isUseItem())) {
            if (event.isAttack()) {
                attackClicked = true;
            }
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** In combat mode the scroll wheel moves the skill cursor; the item hotbar does not turn. */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (ClientTorikoData.isCombatMode() && Minecraft.getInstance().screen == null) {
            moveSkillCursor(event.getScrollDeltaY() > 0 ? -1 : 1);
            event.setCanceled(true);
        }
    }

    private static void moveSkillCursor(int delta) {
        setSkillCursor(Math.floorMod(ClientTorikoData.skillCursor() + delta, TorikoData.SLOT_COUNT));
    }

    private static void setSkillCursor(int slot) {
        ClientTorikoData.setSkillCursor(slot);
        int skill = ClientTorikoData.skillSlot(slot);
        if (skill >= 0) {
            ClientTorikoData.setSelectedLocally(skill);
            PacketDistributor.sendToServer(new C2SSelectSkill(skill));
        }
    }

    private static void setCombatMode(Minecraft minecraft, boolean on) {
        ClientTorikoData.setCombatModeLocally(on);
        PacketDistributor.sendToServer(new C2SCombatMode(on));
        if (on) {
            combatSavedSlot = minecraft.player.getInventory().selected;
            setSkillCursor(ClientTorikoData.skillCursor());
        } else {
            if (guardWasDown) {
                PacketDistributor.sendToServer(new C2SCombatAction(CombatAction.GUARD_END.ordinal()));
            }
            attackClicked = false;
            guardWasDown = false;
        }
    }

    /**
     * Presses one of the current style's attack groups. The server walks down that group's chain, so
     * tapping the key repeatedly is what moves through the group's moves. Keys for groups the style
     * does not define do nothing.
     */
    private static void sendAttack(int group) {
        if (group >= CombatStyles.get(ClientTorikoData.combatStyle()).groups().size()) {
            return;
        }
        PacketDistributor.sendToServer(new C2SCombatAction(CombatAction.ATTACK.ordinal(), group));
    }

    private static void tickCombat(Minecraft minecraft, boolean inGame) {
        while (ModKeys.COMBAT_MODE.consumeClick()) {
            if (inGame) {
                setCombatMode(minecraft, !ClientTorikoData.isCombatMode());
            }
        }
        while (ModKeys.COMBAT_STYLE.consumeClick()) {
            if (inGame) {
                minecraft.setScreen(new CombatStyleScreen());
            }
        }
        while (ModKeys.SKILL_SLOTS.consumeClick()) {
            if (inGame) {
                minecraft.setScreen(new SkillSlotsScreen());
            }
        }
        if (!ClientTorikoData.isCombatMode()) {
            while (ModKeys.DODGE.consumeClick()) {
                // Nothing to do outside combat mode.
            }
            return;
        }

        // Re-assert the mode now and then: it is idempotent, and heals a toggle packet lost or
        // reordered while the world was still loading in.
        if (++combatModeResend >= COMBAT_MODE_RESEND_TICKS) {
            combatModeResend = 0;
            PacketDistributor.sendToServer(new C2SCombatMode(true));
        }

        // Number keys move the skill cursor. Vanilla still switches the item slot on the same press, so
        // it is put straight back: the held item never changes while combat mode is on.
        var keys = minecraft.options.keyHotbarSlots;
        for (int i = 0; i < keys.length && i < TorikoData.SLOT_COUNT; i++) {
            boolean down = inGame && keys[i].isDown();
            if (down && !hotbarKeyWasDown[i]) {
                setSkillCursor(i);
            }
            hotbarKeyWasDown[i] = down;
        }
        minecraft.player.getInventory().selected = combatSavedSlot;

        if (CombatAnimations.holdsWeapon(minecraft.player)) {
            // A held weapon plays by Epic Fight's own rules: send nothing and let it handle the input.
            attackClicked = false;
            if (guardWasDown) {
                PacketDistributor.sendToServer(new C2SCombatAction(CombatAction.GUARD_END.ordinal()));
                guardWasDown = false;
            }
            while (ModKeys.DODGE.consumeClick()) {
                // Epic Fight's own dodge key is used with a weapon.
            }
            return;
        }

        // The click event reports even a press-and-release inside one tick; Epic Fight intercepts attack
        // clicks aimed at an entity or the air before that event is sent, so the key state is watched too.
        boolean attackDown = inGame && (minecraft.options.keyAttack.isDown() || CombatAnimations.epicFightAttackHeld());
        boolean pressed = attackClicked || (attackDown && !attackDownPrev);
        attackDownPrev = attackDown;
        attackClicked = false;
        if (pressed && inGame) {
            // Aerial-combo styles only: holding jump while attacking launches (grounded) or spikes
            // (airborne) instead of pressing the attack button's group.
            boolean jumpHeld = minecraft.options.keyJump.isDown()
                    && CombatStyles.get(ClientTorikoData.combatStyle()).aerialCombo();
            if (jumpHeld) {
                // Which of the two aerial moves this becomes is the server's call: by the time this tick
                // runs the jump has already been applied locally, so the client cannot tell a launch from
                // a dive. Sending both at once, and never while a jump is mid-flight, is what made the
                // launcher unreachable and the spike fire against the ground.
                PacketDistributor.sendToServer(new C2SCombatAction(CombatAction.AERIAL_ATTACK.ordinal()));
            } else {
                sendAttack(0);
            }
        }

        // The style's extra attack keys: each walks down its own group's chain, so how many times a key
        // is tapped in a row decides which move of that form comes out.
        while (ModKeys.ATTACK_GROUP_2.consumeClick()) {
            if (inGame) {
                sendAttack(1);
            }
        }
        while (ModKeys.ATTACK_GROUP_3.consumeClick()) {
            if (inGame) {
                sendAttack(2);
            }
        }

        boolean guard = inGame && minecraft.options.keyUse.isDown();
        if (guard != guardWasDown) {
            CombatAction action = guard ? CombatAction.GUARD_START : CombatAction.GUARD_END;
            PacketDistributor.sendToServer(new C2SCombatAction(action.ordinal()));
        }
        guardWasDown = guard;

        while (ModKeys.DODGE.consumeClick()) {
            if (inGame) {
                PacketDistributor.sendToServer(new C2SCombatAction(CombatAction.DODGE.ordinal()));
            }
        }
    }

    /** While Intimidation stage 2 is on, the demon model stands in for the player's own. */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (AppetiteDemonEntity.isTransformed(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        ClientTorikoData.tick();
        ClientWeaponFlashes.tick();

        // Drain the click queues even while a screen is open, so presses don't fire on close.
        boolean inGame = minecraft.screen == null;

        tickCombat(minecraft, inGame);
        org.example.hanjinwoo.gourmet2.compat.CombatAnimations.clientTick(ClientTorikoData.isCombatMode() && !CombatAnimations.holdsWeapon(minecraft.player));

        int cycle = 0;
        while (ModKeys.NEXT_SKILL.consumeClick()) {
            cycle += inGame ? 1 : 0;
        }
        while (ModKeys.PREV_SKILL.consumeClick()) {
            cycle -= inGame ? 1 : 0;
        }
        if (cycle != 0) {
            int selected = ClientTorikoData.cycleSelectedLocally(cycle);
            PacketDistributor.sendToServer(new C2SSelectSkill(selected));
        }

        while (ModKeys.SKILL_SETTINGS.consumeClick()) {
            if (inGame) {
                minecraft.setScreen(new SkillSettingsScreen());
            }
        }

        // Charge skills need press *and* release edges, so this reads isDown() directly rather
        // than draining the click queue (consumeClick() only ever reports the press edge).
        boolean down = inGame && ModKeys.USE_SKILL.isDown();
        if (down && !useSkillWasDown) {
            PacketDistributor.sendToServer(C2SUseSkill.INSTANCE);
        } else if (!down && useSkillWasDown) {
            PacketDistributor.sendToServer(C2SReleaseSkill.INSTANCE);
        }
        useSkillWasDown = down;

        // The leap key has the same shape: held to wind up, released to launch. It is not a combat-mode
        // move, so it is read here rather than inside tickCombat.
        boolean leapDown = inGame && ModKeys.LEAP.isDown();
        if (leapDown && !leapWasDown) {
            PacketDistributor.sendToServer(C2SLeap.CHARGE);
        } else if (!leapDown && leapWasDown) {
            PacketDistributor.sendToServer(C2SLeap.RELEASE);
        }
        leapWasDown = leapDown;
    }
}
