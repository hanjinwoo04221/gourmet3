package org.example.hanjinwoo.gourmet2.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.lwjgl.glfw.GLFW;

/** Key bindings: one to fire the selected technique, two to cycle through them. */
public final class ModKeys {
    public static final String CATEGORY = "key.categories." + Gourmet2.MODID;

    public static final KeyMapping USE_SKILL = new KeyMapping(
            "key." + Gourmet2.MODID + ".use_skill",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY);

    public static final KeyMapping NEXT_SKILL = new KeyMapping(
            "key." + Gourmet2.MODID + ".next_skill",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY);

    public static final KeyMapping PREV_SKILL = new KeyMapping(
            "key." + Gourmet2.MODID + ".prev_skill",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            CATEGORY);

    public static final KeyMapping SKILL_SETTINGS = new KeyMapping(
            "key." + Gourmet2.MODID + ".skill_settings",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            CATEGORY);

    public static final KeyMapping SKILL_SLOTS = new KeyMapping(
            "key." + Gourmet2.MODID + ".skill_slots",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY);

    public static final KeyMapping COMBAT_MODE = new KeyMapping(
            "key." + Gourmet2.MODID + ".combat_mode",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            CATEGORY);

    public static final KeyMapping COMBAT_STYLE = new KeyMapping(
            "key." + Gourmet2.MODID + ".combat_style",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            CATEGORY);

    public static final KeyMapping DODGE = new KeyMapping(
            "key." + Gourmet2.MODID + ".dodge",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            CATEGORY);

    /**
     * The extra attack keys. Combat group 0 is the plain attack button; these press the style's second
     * and third attack groups, so a style that bundles its moves by form gets one key per form.
     */
    public static final KeyMapping ATTACK_GROUP_2 = new KeyMapping(
            "key." + Gourmet2.MODID + ".attack_group_2",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F,
            CATEGORY);

    public static final KeyMapping ATTACK_GROUP_3 = new KeyMapping(
            "key." + Gourmet2.MODID + ".attack_group_3",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_T,
            CATEGORY);

    /**
     * The charged leap: hold it while against a surface — floor, wall or ceiling — to wind up, let go to
     * launch in the direction being looked at. Rebindable like every other key here.
     */
    public static final KeyMapping LEAP = new KeyMapping(
            "key." + Gourmet2.MODID + ".leap",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY);

    /** Up/Down while Ichiryu's chopsticks hover: choose which technique they perform. */
    public static final KeyMapping CHOPSTICKS_PREV = new KeyMapping(
            "key." + Gourmet2.MODID + ".chopsticks_prev",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UP,
            CATEGORY);

    public static final KeyMapping CHOPSTICKS_NEXT = new KeyMapping(
            "key." + Gourmet2.MODID + ".chopsticks_next",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_DOWN,
            CATEGORY);

    /** Opens the Minority World settings screen. */
    public static final KeyMapping MINORITY_SETTINGS = new KeyMapping(
            "key." + Gourmet2.MODID + ".minority_settings",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY);

    private ModKeys() {}
}
