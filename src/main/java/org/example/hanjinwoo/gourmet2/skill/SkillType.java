package org.example.hanjinwoo.gourmet2.skill;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import org.example.hanjinwoo.gourmet2.Config;
import org.example.hanjinwoo.gourmet2.Gourmet2;

/**
 * Metadata for every Toriko technique. Behaviour lives in {@link SkillRegistry}, keyed by this
 * enum, so that adding a skill is "add a constant + add a behaviour".
 *
 * <p>The ordinal is the wire format for skill selection, so <b>do not reorder</b> these constants
 * without bumping the network version in {@code ModNetwork}.
 */
public enum SkillType {
    /**
     * 釘パンチ — a drilling punch that screws straight through armour. Hold the skill key to wind
     * up a combo; the longer the charge, the more hits land (up to the player's cell-level-capped
     * ceiling, see {@link CellEvolution#nailComboCap}), releasing on key-up.
     */
    NAIL_PUNCH("nail_punch", 8, 40, 0, 0xFFD24B, InputMode.CHARGE),
    /**
     * ネイルガン — a machine-gun barrage of nails. Charged rather than instant: the wind-up is how long a
     * burst the caster wants, and it fires that many rounds, each one a projectile of its own.
     */
    NAIL_GUN("nail_gun", 26, 70, 0, 0xFFA630, InputMode.CHARGE),
    /** フォーク — four parallel piercing stabs that make the target bleed. */
    FORK("fork", 12, 30, 0, 0x7FE3C8, InputMode.INSTANT),
    /** ナイフ — a clean horizontal sweep that cuts rather than bludgeons. */
    KNIFE("knife", 15, 36, 0, 0xC8F2FF, InputMode.INSTANT),
    /** レッグナイフ — a spinning kick that cuts everything in a ring and launches it. */
    LEG_KNIFE("leg_knife", 18, 44, 0, 0x8AD7FF, InputMode.CHARGE),
    /** フライングフォーク — piercing vacuum spears fired one after another for as long as the key is held. */
    FLYING_FORK("flying_fork", 20, 50, 0, 0x6EC6FF, InputMode.AUTO),
    /** フライングナイフ — crescent cutting waves fired one after another for as long as the key is held. */
    FLYING_KNIFE("flying_knife", 22, 56, 0, 0xB0E0FF, InputMode.AUTO),
    /**
     * 威圧・食欲の悪魔 — an intimidation aura that projects the Appetite Demon. Press once to turn
     * on: it keeps draining Appetite and re-pressuring everything nearby until either you press it
     * again or Appetite runs out.
     */
    INTIMIDATION("intimidation", 20, 60, 0, 0xB44CFF, InputMode.TOGGLE),
    /** 威圧・食欲の悪魔 (変身) — the same intimidation, but the caster's own body becomes the demon. */
    DEMON_FORM("demon_form", 30, 60, 0, 0x8A2BE2, InputMode.TOGGLE),
    /** 気の放出 — toggle: radiate Gourmet Cell ki around you; strength set in the power settings. */
    KI_RELEASE("ki_release", 6, 40, 0, 0xFFE066, InputMode.TOGGLE),
    /** 食没 — meditate to awaken the Gourmet Cells: refills Appetite and empowers every skill. */
    FOOD_IMMERSION("food_immersion", 0, 600, 60, 0x66FF9E, InputMode.INSTANT);

    /** How the use-key controls this skill. See {@code SkillEngine}/{@code ClientEvents}. */
    public enum InputMode {
        /** Fires immediately on press. */
        INSTANT,
        /** Press to start winding up, release to fire; power scales with how long it was held. */
        CHARGE,
        /** Press to turn on (draining Appetite over time), press again to turn off. */
        TOGGLE,
        /** Fires repeatedly for as long as the key is held, stopping the instant it is released. */
        AUTO
    }

    public static final SkillType[] VALUES = values();
    public static final int COUNT = VALUES.length;

    private final String id;
    private final int baseAppetiteCost;
    private final int baseCooldownTicks;
    private final int castTicks;
    private final int color;
    private final InputMode inputMode;

    SkillType(String id, int baseAppetiteCost, int baseCooldownTicks, int castTicks, int color, InputMode inputMode) {
        this.id = id;
        this.baseAppetiteCost = baseAppetiteCost;
        this.baseCooldownTicks = baseCooldownTicks;
        this.castTicks = castTicks;
        this.color = color;
        this.inputMode = inputMode;
    }

    public static SkillType byIndex(int index) {
        return VALUES[Mth.clamp(index, 0, COUNT - 1)];
    }

    public String id() {
        return id;
    }

    /** Ticks the player must keep channelling before the skill fires. 0 = instant. */
    public int castTicks() {
        return castTicks;
    }

    /** ARGB-less RGB tint used by the HUD. */
    public int color() {
        return color;
    }

    public InputMode inputMode() {
        return inputMode;
    }

    public int appetiteCost() {
        return Math.max(0, (int) Math.round(baseAppetiteCost * Config.appetiteCostMultiplier));
    }

    /** Cooldown after config scaling. Gourmet Cell awakening shortens this further, see {@link SkillEngine}. */
    public int cooldownTicks() {
        return Math.max(0, (int) Math.round(baseCooldownTicks * Config.cooldownMultiplier));
    }

    public String translationKey() {
        return "skill." + Gourmet2.MODID + "." + id;
    }

    public String descriptionKey() {
        return "skill." + Gourmet2.MODID + "." + id + ".desc";
    }

    public MutableComponent displayName() {
        return Component.translatable(translationKey());
    }
}
