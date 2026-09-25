package org.example.hanjinwoo.gourmet2.client;

import net.minecraft.util.Mth;
import org.example.hanjinwoo.gourmet2.network.S2CSyncTorikoData;
import org.example.hanjinwoo.gourmet2.skill.CellEvolution;
import org.example.hanjinwoo.gourmet2.skill.Charge;
import org.example.hanjinwoo.gourmet2.skill.combat.CombatStyles;
import org.example.hanjinwoo.gourmet2.skill.SkillType;
import org.jetbrains.annotations.Nullable;

/**
 * The client's mirror of its own player's Gourmet Cell state, kept purely so the HUD and settings
 * GUI have something to draw. Cooldowns and the awakening timer count themselves down locally
 * between syncs, which is why the server does not need to stream them every tick.
 */
public final class ClientTorikoData {
    private static final int[] COOLDOWNS = new int[SkillType.COUNT];

    private static int appetite;
    private static int maxAppetite = 100;
    private static int selected;
    private static int awakenedTicks;
    private static int activeSkill = -1;
    private static int activeElapsed;
    private static int activeTotal;
    private static long cellXp;
    private static int intimidationStage;
    private static int chargingSkill = -1;
    private static int chargeTicks;

    private static int nailComboSetting = CellEvolution.NAIL_COMBO_BASE;
    private static int forkProjectileSetting = CellEvolution.FORK_PROJECTILE_BASE;
    private static int knifeWaveSetting = CellEvolution.KNIFE_WAVE_BASE;
    private static float flyingDamageSetting = CellEvolution.DAMAGE_MULT_BASE;
    private static float flyingSizeSetting = CellEvolution.SIZE_MULT_BASE;
    private static boolean kiActive;
    private static final int[] SKILL_SLOTS = new int[9];
    private static boolean combatMode;
    private static String combatStyle = CombatStyles.FIST.id();
    /** Which combat hotbar slot is highlighted. Separate from the item hotbar, which stays put in combat mode. */
    private static int skillCursor;

    static {
        java.util.Arrays.fill(SKILL_SLOTS, -1);
    }
    private static int kiOutputSetting = CellEvolution.KI_OUTPUT_BASE;
    private static float attackDamageSetting = CellEvolution.ATTACK_DAMAGE_BASE;
    private static float leapDistanceSetting = CellEvolution.LEAP_DISTANCE_BASE;
    private static float rangeSetting = CellEvolution.RANGE_BASE;

    /** Ticks left on the "a skill was just selected" HUD expansion. */
    private static int selectionHighlight;

    private ClientTorikoData() {}

    public static void accept(S2CSyncTorikoData payload) {
        appetite = payload.appetite();
        maxAppetite = Math.max(1, payload.maxAppetite());
        if (selected != payload.selected()) {
            selectionHighlight = HudTiming.SELECTION_HIGHLIGHT_TICKS;
        }
        selected = Mth.clamp(payload.selected(), 0, SkillType.COUNT - 1);
        awakenedTicks = payload.awakenedTicks();
        activeSkill = payload.activeSkill();
        activeElapsed = payload.activeElapsed();
        activeTotal = payload.activeTotal();
        cellXp = payload.cellXp();
        intimidationStage = payload.intimidationStage();
        chargingSkill = payload.chargingSkill();
        chargeTicks = payload.chargeTicks();
        nailComboSetting = payload.nailComboSetting();
        forkProjectileSetting = payload.forkProjectileSetting();
        knifeWaveSetting = payload.knifeWaveSetting();
        flyingDamageSetting = payload.flyingDamageSetting();
        flyingSizeSetting = payload.flyingSizeSetting();
        kiActive = payload.kiActive();
        int[] slots = payload.skillSlots();
        for (int i = 0; i < SKILL_SLOTS.length; i++) {
            SKILL_SLOTS[i] = i < slots.length ? slots[i] : -1;
        }
        combatMode = payload.combatMode();
        combatStyle = payload.combatStyle();
        kiOutputSetting = payload.kiOutputSetting();
        attackDamageSetting = payload.attackDamageSetting();
        leapDistanceSetting = payload.leapDistanceSetting();
        rangeSetting = payload.rangeSetting();

        int[] incoming = payload.cooldowns();
        System.arraycopy(incoming, 0, COOLDOWNS, 0, Math.min(incoming.length, COOLDOWNS.length));
    }

    /** Local decay so the HUD stays smooth between server syncs. */
    public static void tick() {
        for (int i = 0; i < COOLDOWNS.length; i++) {
            if (COOLDOWNS[i] > 0) {
                COOLDOWNS[i]--;
            }
        }
        if (awakenedTicks > 0) {
            awakenedTicks--;
        }
        if (selectionHighlight > 0) {
            selectionHighlight--;
        }
        if (activeSkill >= 0 && activeElapsed < activeTotal) {
            activeElapsed++;
        }
        if (chargingSkill >= 0) {
            chargeTicks++;
        }
    }

    /** Optimistic local selection so pressing the cycle key feels instant. */
    public static int cycleSelectedLocally(int delta) {
        selected = Math.floorMod(selected + delta, SkillType.COUNT);
        selectionHighlight = HudTiming.SELECTION_HIGHLIGHT_TICKS;
        return selected;
    }

    public static int appetite() {
        return appetite;
    }

    public static int maxAppetite() {
        return maxAppetite;
    }

    public static float appetiteFraction() {
        return Mth.clamp((float) appetite / maxAppetite, 0.0F, 1.0F);
    }

    public static SkillType selectedSkill() {
        return SkillType.byIndex(selected);
    }

    public static int selectedIndex() {
        return selected;
    }

    public static int cooldown(SkillType skill) {
        return COOLDOWNS[skill.ordinal()];
    }

    public static boolean isReady(SkillType skill) {
        return COOLDOWNS[skill.ordinal()] <= 0 && appetite >= skill.appetiteCost();
    }

    public static boolean isAwakened() {
        return awakenedTicks > 0;
    }

    public static int awakenedTicks() {
        return awakenedTicks;
    }

    public static boolean isSelectionHighlighted() {
        return selectionHighlight > 0;
    }

    /** The skill currently executing, or null if none. */
    public static @Nullable SkillType activeSkill() {
        return activeSkill < 0 ? null : SkillType.byIndex(activeSkill);
    }

    /** 0..1 progress of the running skill, or 0 if none. */
    public static float activeProgress() {
        return activeTotal <= 0 ? 0.0F : Mth.clamp((float) activeElapsed / activeTotal, 0.0F, 1.0F);
    }

    // ------------------------------------------------------------ cell evolution

    public static long cellXp() {
        return cellXp;
    }

    public static int cellLevel() {
        return CellEvolution.levelForXp(cellXp);
    }

    public static float cellLevelProgress() {
        int level = cellLevel();
        long into = CellEvolution.xpIntoLevel(cellXp, level);
        long need = CellEvolution.xpForNextLevel(level);
        return need <= 0 ? 0.0F : Mth.clamp((float) into / need, 0.0F, 1.0F);
    }

    // -------------------------------------------------------------- intimidation

    public static boolean isIntimidationActive() {
        return intimidationStage > 0;
    }

    /** 0 = off, 1 = demon looming overhead, 2 = the caster has become the demon. */
    public static int intimidationStage() {
        return intimidationStage;
    }

    // ------------------------------------------------------------------ charging

    public static @Nullable SkillType chargingSkill() {
        return chargingSkill < 0 ? null : SkillType.byIndex(chargingSkill);
    }

    public static boolean isCharging() {
        return chargingSkill >= 0;
    }

    public static int chargeTicks() {
        return chargeTicks;
    }

    /** 0..1 progress toward the player's current Nail Punch combo ceiling, for the HUD bar. */
    public static float chargeProgress() {
        // A Nail Punch charge is spent at a rate set by the combo dialled in, so however long that combo is it
        // fills the bar in the same ticks; the rest charge to Charge.MAX_TICKS whatever their settings.
        int maxTicks = chargingSkill == SkillType.NAIL_PUNCH.ordinal()
                ? CellEvolution.NAIL_CHARGE_TICKS
                : Charge.MAX_TICKS;
        return Mth.clamp((float) chargeTicks / maxTicks, 0.0F, 1.0F);
    }

    // ------------------------------------------------------- tuned power settings

    public static int nailComboSetting() {
        return nailComboSetting;
    }

    public static int forkProjectileSetting() {
        return forkProjectileSetting;
    }

    public static int knifeWaveSetting() {
        return knifeWaveSetting;
    }

    public static float flyingDamageSetting() {
        return flyingDamageSetting;
    }

    /** Skill ordinal on a combat hotbar slot, or -1 if empty. */
    public static int skillSlot(int index) {
        return index < 0 || index >= SKILL_SLOTS.length ? -1 : SKILL_SLOTS[index];
    }

    public static boolean isCombatMode() {
        return combatMode;
    }

    /** Optimistic toggle so the hotbar swaps the instant the key is pressed. */
    public static String combatStyle() {
        return combatStyle;
    }

    /** Optimistic local choice so the selection screen reacts instantly. */
    public static void setCombatStyleLocally(String id) {
        combatStyle = id;
    }

    public static int skillCursor() {
        return skillCursor;
    }

    public static void setSkillCursor(int slot) {
        skillCursor = Mth.clamp(slot, 0, SKILL_SLOTS.length - 1);
    }

    public static void setCombatModeLocally(boolean on) {
        combatMode = on;
    }

    public static void setSelectedLocally(int index) {
        selected = Mth.clamp(index, 0, SkillType.COUNT - 1);
        selectionHighlight = HudTiming.SELECTION_HIGHLIGHT_TICKS;
    }

    public static boolean isKiActive() {
        return kiActive;
    }

    public static int kiOutputSetting() {
        return kiOutputSetting;
    }

    public static float attackDamageSetting() {
        return attackDamageSetting;
    }

    public static float leapDistanceSetting() {
        return leapDistanceSetting;
    }

    public static float rangeSetting() {
        return rangeSetting;
    }

    public static float flyingSizeSetting() {
        return flyingSizeSetting;
    }

    /** HUD timing constants, kept next to the state they drive. */
    public static final class HudTiming {
        public static final int SELECTION_HIGHLIGHT_TICKS = 60;

        private HudTiming() {}
    }
}
