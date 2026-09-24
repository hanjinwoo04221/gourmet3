package org.example.hanjinwoo.gourmet2.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.example.hanjinwoo.gourmet2.Config;
import org.example.hanjinwoo.gourmet2.skill.ActiveSkill;
import org.example.hanjinwoo.gourmet2.skill.CellEvolution;
import org.example.hanjinwoo.gourmet2.skill.combat.CombatStyles;
import org.example.hanjinwoo.gourmet2.skill.SkillType;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Per-player Gourmet Cell state: Appetite, per-skill cooldowns, the selected skill, the currently
 * running multi-tick skill, cell evolution progress and every skill's player-tuned power setting.
 *
 * <p>Attached to players via {@code ModAttachments.TORIKO_DATA}. The authoritative copy lives on
 * the server; clients receive a trimmed mirror through {@code S2CSyncTorikoData} purely so the HUD
 * and settings GUI have something to draw.
 */
public class TorikoData implements INBTSerializable<CompoundTag> {
    private static final String KEY_APPETITE = "Appetite";
    private static final String KEY_COOLDOWNS = "Cooldowns";
    private static final String KEY_SELECTED = "Selected";
    private static final String KEY_AWAKENED = "Awakened";
    private static final String KEY_CELL_XP = "CellXp";
    private static final String KEY_NAIL_COMBO = "NailComboSetting";
    private static final String KEY_FORK_PROJECTILES = "ForkProjectileSetting";
    private static final String KEY_KNIFE_WAVES = "KnifeWaveSetting";
    private static final String KEY_FLYING_DAMAGE = "FlyingDamageSetting";
    private static final String KEY_FLYING_SIZE = "FlyingSizeSetting";
    private static final String KEY_KI_OUTPUT = "KiOutputSetting";
    private static final String KEY_ATTACK_DAMAGE = "AttackDamageSetting";
    private static final String KEY_LEAP_DISTANCE = "LeapDistanceSetting";
    private static final String KEY_SKILL_SLOTS = "SkillSlots";
    private static final String KEY_COMBAT_STYLE = "CombatStyle";
    public static final int SLOT_COUNT = 9;

    private final int[] cooldowns = new int[SkillType.COUNT];
    private int appetite = 100;
    private int selected = 0;
    private int awakenedTicks = 0;
    private long cellXp = 0;

    // ------------------------------------------------------- player-tuned power settings
    private int nailComboSetting = CellEvolution.NAIL_COMBO_BASE;
    private int forkProjectileSetting = CellEvolution.FORK_PROJECTILE_BASE;
    private int knifeWaveSetting = CellEvolution.KNIFE_WAVE_BASE;
    private float flyingDamageSetting = CellEvolution.DAMAGE_MULT_BASE;
    private float flyingSizeSetting = CellEvolution.SIZE_MULT_BASE;
    private int kiOutputSetting = CellEvolution.KI_OUTPUT_BASE;
    private float attackDamageSetting = CellEvolution.ATTACK_DAMAGE_BASE;
    private float leapDistanceSetting = CellEvolution.LEAP_DISTANCE_BASE;

    private String combatStyle = CombatStyles.FIST.id();

    /** Skill (by ordinal, -1 = empty) assigned to each combat-mode hotbar slot. */
    private final int[] skillSlots = new int[SLOT_COUNT];

    {
        Arrays.fill(skillSlots, -1);
    }

    /** Combat mode swaps the hotbar for the skill hotbar and turns clicks into combat actions. */
    private transient boolean combatMode;
    /** A press that arrived during a swing's recovery, replayed (as this group) as soon as it ends. */
    private transient boolean bufferedAttack;
    private transient int bufferedGroup;
    private transient int combatCooldown;
    /**
     * One combo chain per attack group: how many presses into it the player is, and how long the chain
     * stays open. The groups are independent, so switching attack keys switches form without losing
     * the place in either chain.
     */
    private final transient int[] groupStep = new int[CombatStyles.MAX_GROUPS];
    private final transient int[] groupTimer = new int[CombatStyles.MAX_GROUPS];
    /**
     * Leap movement: the key is held to wind up and released to launch, so the charge only exists while
     * the key is down. Never persisted.
     */
    private transient boolean leapCharging;
    private transient int leapChargeTicks;
    /**
     * Ticks of dash left, or {@link #NO_LEAP}. A dash counts down to 0, and 0 is the tick it settles on — so
     * "no leap" needs its own value rather than sharing 0 with "just made the last step".
     */
    private transient int leapTicks = NO_LEAP;
    /** No leap running. */
    private static final int NO_LEAP = -1;
    /**
     * Distance the current dash set out to cover. Air resistance bleeds the speed off as the leap flies, and
     * how far along it is decides how much has been bled, so the launch distance is kept for the profile.
     */
    private transient double leapTravel;
    /**
     * Ticks the dash has been flying. The dash needs a plain clock, and the tick budget is not one — that is
     * stretched whenever the leap is slowed down — so this counts separately for pacing the held flight pose.
     */
    private transient int leapElapsed;
    private transient Vec3 leapAim = Vec3.ZERO;
    /** The entity the dash is homing on, or -1 for "just fly where the crosshair pointed". */
    private transient int leapTargetId = -1;
    private transient int dodgeTicks;
    private transient int fallImmuneTicks;
    private transient boolean guarding;
    private transient int guardTicks;

    /** A second hit scheduled a few ticks after a combo step whose animation lands two blows (e.g. Acrobatic basic2/basic3). */
    private transient int delayedHitTicks;
    private transient float delayedHitDamage;
    private transient boolean delayedHitKnockback;
    /** Which attack group the delayed hit belongs to, so its second blow is thrown with the same limb. */
    private transient int delayedHitGroup;

    /** Passive regeneration accumulator. Not worth persisting. */
    private transient int regenTimer = 0;
    /** The multi-tick skill currently executing, if any. Server-side only. */
    private transient @Nullable ActiveSkill active;
    /** The skill currently being charged (key held, not yet released), or null. Never persisted. */
    private transient @Nullable SkillType chargingSkill;
    private transient int chargeTicks;
    /** A skill whose cast animation is still winding up; it fires when the countdown ends. */
    private transient @Nullable SkillType pendingSkill;
    private transient int pendingTicks;
    /** Whether the Intimidation toggle is currently on. Resets to off on rejoin, never persisted. */
    private transient int intimidationStage;
    /** Entity id of the currently-toggled-on Intimidation's Appetite Demon, or -1. */
    private transient int intimidationDemonId = -1;
    /** Whether Ki Release is on, and the entity carrying its aura. Never persisted. */
    private transient boolean kiActive;
    private transient int kiAuraId = -1;
    /** Set whenever a field the client HUD cares about changes. */
    private transient boolean dirty = true;

    // ---------------------------------------------------------------- appetite

    public int appetite() {
        return appetite;
    }

    public int maxAppetite() {
        return Config.maxAppetite + CellEvolution.maxAppetiteBonus(cellLevel());
    }

    public void setAppetite(int value) {
        int clamped = Mth.clamp(value, 0, maxAppetite());
        if (clamped != appetite) {
            appetite = clamped;
            dirty = true;
        }
    }

    public void addAppetite(int amount) {
        setAppetite(appetite + amount);
    }

    public boolean canAfford(int cost) {
        return appetite >= cost;
    }

    /** Spends {@code cost} Appetite, returning false (and spending nothing) if it is unaffordable. */
    public boolean spend(int cost) {
        if (!canAfford(cost)) {
            return false;
        }
        setAppetite(appetite - cost);
        return true;
    }

    public boolean isStarving() {
        return appetite <= 0;
    }

    // --------------------------------------------------------------- cooldowns

    public int cooldown(SkillType skill) {
        return cooldowns[skill.ordinal()];
    }

    public void setCooldown(SkillType skill, int ticks) {
        int clamped = Math.max(0, ticks);
        if (cooldowns[skill.ordinal()] != clamped) {
            cooldowns[skill.ordinal()] = clamped;
            dirty = true;
        }
    }

    public boolean isReady(SkillType skill) {
        return cooldowns[skill.ordinal()] <= 0;
    }

    /**
     * Decrements every cooldown by one tick. Deliberately does <em>not</em> mark the data dirty:
     * the client mirror counts its own copies down, so a running cooldown costs no bandwidth.
     *
     * @return true if any cooldown was still running
     */
    public boolean tickCooldowns() {
        boolean changed = false;
        for (int i = 0; i < cooldowns.length; i++) {
            if (cooldowns[i] > 0) {
                cooldowns[i]--;
                changed = true;
            }
        }
        return changed;
    }

    public int[] cooldownsView() {
        return cooldowns;
    }

    public void copyCooldowns(int[] source) {
        System.arraycopy(source, 0, cooldowns, 0, Math.min(source.length, cooldowns.length));
    }

    // ---------------------------------------------------------------- selection

    public SkillType selectedSkill() {
        return SkillType.byIndex(selected);
    }

    public int selectedIndex() {
        return selected;
    }

    public void setSelected(int index) {
        int clamped = Mth.clamp(index, 0, SkillType.COUNT - 1);
        if (clamped != selected) {
            selected = clamped;
            dirty = true;
        }
    }

    public void cycleSelected(int delta) {
        setSelected(Math.floorMod(selected + delta, SkillType.COUNT));
    }

    // ---------------------------------------------------------- gourmet cells

    public int awakenedTicks() {
        return awakenedTicks;
    }

    public boolean isAwakened() {
        return awakenedTicks > 0;
    }

    public void setAwakenedTicks(int ticks) {
        int clamped = Math.max(0, ticks);
        if (clamped != awakenedTicks) {
            awakenedTicks = clamped;
            dirty = true;
        }
    }

    /**
     * Counts the awakening down by one tick without marking the data dirty — the client mirror
     * decays its own copy, so a 30 second buff costs one packet instead of six hundred.
     */
    public void decayAwakened() {
        if (awakenedTicks > 0) {
            awakenedTicks--;
        }
    }

    // ------------------------------------------------------------ cell evolution

    public long cellXp() {
        return cellXp;
    }

    public int cellLevel() {
        return CellEvolution.levelForXp(cellXp);
    }

    /**
     * Sets the Cell XP outright. The level is derived from it, so this is how the level is set as well — by
     * the food that earns it and by the cell-level command alike.
     */
    public void setCellXp(long value) {
        long clamped = Math.max(0, value);
        if (clamped != cellXp) {
            cellXp = clamped;
            dirty = true;
        }
    }

    /** @return how many levels this pushed the player up, 0 if none */
    public int addCellXp(long amount) {
        if (amount <= 0) {
            return 0;
        }
        int before = cellLevel();
        setCellXp(cellXp + amount);
        return cellLevel() - before;
    }

    // ------------------------------------------------------- tuned power settings

    public int nailComboSetting() {
        return nailComboSetting;
    }

    public void setNailComboSetting(int value) {
        int clamped = Mth.clamp(value, CellEvolution.NAIL_COMBO_BASE, CellEvolution.nailComboCap(cellLevel()));
        if (clamped != nailComboSetting) {
            nailComboSetting = clamped;
            dirty = true;
        }
    }

    public int forkProjectileSetting() {
        return forkProjectileSetting;
    }

    public void setForkProjectileSetting(int value) {
        int clamped = Mth.clamp(value, CellEvolution.FORK_PROJECTILE_BASE, CellEvolution.forkProjectileCap(cellLevel()));
        if (clamped != forkProjectileSetting) {
            forkProjectileSetting = clamped;
            dirty = true;
        }
    }

    public int knifeWaveSetting() {
        return knifeWaveSetting;
    }

    public void setKnifeWaveSetting(int value) {
        int clamped = Mth.clamp(value, CellEvolution.KNIFE_WAVE_BASE, CellEvolution.knifeWaveCap(cellLevel()));
        if (clamped != knifeWaveSetting) {
            knifeWaveSetting = clamped;
            dirty = true;
        }
    }

    public float flyingDamageSetting() {
        return flyingDamageSetting;
    }

    public void setFlyingDamageSetting(float value) {
        float clamped = Mth.clamp(value, CellEvolution.DAMAGE_MULT_BASE, CellEvolution.damageMultCap(cellLevel()));
        if (clamped != flyingDamageSetting) {
            flyingDamageSetting = clamped;
            dirty = true;
        }
    }

    public float flyingSizeSetting() {
        return flyingSizeSetting;
    }

    public void setFlyingSizeSetting(float value) {
        float clamped = Mth.clamp(value, CellEvolution.SIZE_MULT_BASE, CellEvolution.sizeMultCap(cellLevel()));
        if (clamped != flyingSizeSetting) {
            flyingSizeSetting = clamped;
            dirty = true;
        }
    }

    public int kiOutputSetting() {
        return kiOutputSetting;
    }

    public void setKiOutputSetting(int value) {
        int clamped = Mth.clamp(value, CellEvolution.KI_OUTPUT_BASE, CellEvolution.kiOutputCap(cellLevel()));
        if (clamped != kiOutputSetting) {
            kiOutputSetting = clamped;
            dirty = true;
        }
    }

    public float attackDamageSetting() {
        return attackDamageSetting;
    }

    public void setAttackDamageSetting(float value) {
        float clamped = Mth.clamp(value, CellEvolution.ATTACK_DAMAGE_FLOOR, CellEvolution.ATTACK_DAMAGE_BASE);
        if (clamped != attackDamageSetting) {
            attackDamageSetting = clamped;
            dirty = true;
        }
    }

    public float leapDistanceSetting() {
        return leapDistanceSetting;
    }

    public void setLeapDistanceSetting(float value) {
        float clamped = Mth.clamp(value, CellEvolution.LEAP_DISTANCE_FLOOR, CellEvolution.LEAP_DISTANCE_BASE);
        if (clamped != leapDistanceSetting) {
            leapDistanceSetting = clamped;
            dirty = true;
        }
    }

    public boolean isKiActive() {
        return kiActive;
    }

    public void setKiActive(boolean active) {
        if (kiActive != active) {
            kiActive = active;
            dirty = true;
        }
    }

    public int kiAuraId() {
        return kiAuraId;
    }

    public void setKiAuraId(int id) {
        kiAuraId = id;
    }

    /**
     * Re-clamps every setting to the current cell level's caps — a safety net for old save data, and what the
     * cell-level command calls when the level comes down. Nothing stops a player dialling a setting up to the
     * cap of a level they no longer have, and a setting past its cap would still fire at full strength while
     * only being charged the capped Appetite cost for it. Raising the level needs nothing: everything is
     * already inside the wider caps.
     */
    public void clampSettingsToLevel() {
        setNailComboSetting(nailComboSetting);
        setForkProjectileSetting(forkProjectileSetting);
        setKnifeWaveSetting(knifeWaveSetting);
        setFlyingDamageSetting(flyingDamageSetting);
        setFlyingSizeSetting(flyingSizeSetting);
        setKiOutputSetting(kiOutputSetting);
        setAttackDamageSetting(attackDamageSetting);
        setLeapDistanceSetting(leapDistanceSetting);
    }

    // ------------------------------------------------------------ active skill

    public @Nullable ActiveSkill active() {
        return active;
    }

    public void setActive(@Nullable ActiveSkill active) {
        this.active = active;
    }

    public boolean isBusy() {
        return active != null || chargingSkill != null || pendingSkill != null;
    }

    public @Nullable SkillType pendingSkill() {
        return pendingSkill;
    }

    public void startPending(SkillType skill, int ticks) {
        pendingSkill = skill;
        pendingTicks = Math.max(1, ticks);
    }

    /** @return true on the tick the wind-up finishes */
    public boolean tickPending() {
        return pendingSkill != null && --pendingTicks <= 0;
    }

    public void clearPending() {
        pendingSkill = null;
        pendingTicks = 0;
    }

    // ----------------------------------------------------------------- charging

    public @Nullable SkillType chargingSkill() {
        return chargingSkill;
    }

    public boolean isCharging() {
        return chargingSkill != null;
    }

    public int chargeTicks() {
        return chargeTicks;
    }

    public void startCharging(SkillType skill) {
        chargingSkill = skill;
        chargeTicks = 0;
    }

    public void tickCharge() {
        chargeTicks++;
    }

    public void stopCharging() {
        chargingSkill = null;
        chargeTicks = 0;
    }

    // ------------------------------------------------------------- intimidation

    public boolean isIntimidationActive() {
        return intimidationStage > 0;
    }

    /** 0 = off, 1 = demon looming overhead, 2 = the caster has become the demon. */
    public int intimidationStage() {
        return intimidationStage;
    }

    public void setIntimidationStage(int stage) {
        int clamped = Mth.clamp(stage, 0, 2);
        if (intimidationStage != clamped) {
            intimidationStage = clamped;
            dirty = true;
        }
    }

    public int intimidationDemonId() {
        return intimidationDemonId;
    }

    public void setIntimidationDemonId(int id) {
        intimidationDemonId = id;
    }

    // ------------------------------------------------------------ skill hotbar / combat mode

    public int skillSlot(int index) {
        return index < 0 || index >= SLOT_COUNT ? -1 : skillSlots[index];
    }

    public int[] skillSlotsView() {
        return skillSlots;
    }

    public void setSkillSlot(int index, int skillOrdinal) {
        if (index < 0 || index >= SLOT_COUNT) {
            return;
        }
        int value = skillOrdinal < 0 || skillOrdinal >= SkillType.COUNT ? -1 : skillOrdinal;
        if (skillSlots[index] != value) {
            skillSlots[index] = value;
            dirty = true;
        }
    }

    public String combatStyle() {
        return combatStyle;
    }

    /** Ignores ids that are not a registered style. */
    public void setCombatStyle(String id) {
        if (CombatStyles.exists(id) && !combatStyle.equals(id)) {
            combatStyle = id;
            dirty = true;
        }
    }

    public boolean isCombatMode() {
        return combatMode;
    }

    public boolean bufferedAttack() { return bufferedAttack; }
    public void setBufferedAttack(boolean value) { bufferedAttack = value; }

    /** The attack group the buffered press came from. */
    public int bufferedGroup() { return bufferedGroup; }
    public void setBufferedGroup(int group) { bufferedGroup = Math.max(0, group); }

    public void setCombatMode(boolean on) {
        if (combatMode != on) {
            combatMode = on;
            dirty = true;
        }
        if (!on) {
            guarding = false;
            resetGroupChains();
        }
    }

    public int combatCooldown() { return combatCooldown; }
    public void setCombatCooldown(int ticks) { combatCooldown = Math.max(0, ticks); }
    public int groupStep(int group) { return inRange(group) ? groupStep[group] : 0; }
    public void setGroupStep(int group, int step) { if (inRange(group)) groupStep[group] = step; }
    public int groupTimer(int group) { return inRange(group) ? groupTimer[group] : 0; }
    public void setGroupTimer(int group, int ticks) { if (inRange(group)) groupTimer[group] = Math.max(0, ticks); }

    /** Closes every group's chain, so the next press starts from that group's first move again. */
    public void resetGroupChains() {
        Arrays.fill(groupStep, 0);
        Arrays.fill(groupTimer, 0);
    }

    private static boolean inRange(int group) {
        return group >= 0 && group < CombatStyles.MAX_GROUPS;
    }
    // ------------------------------------------------------------------- leap

    public boolean isLeapCharging() { return leapCharging; }

    public void setLeapCharging(boolean on) {
        leapCharging = on;
        if (on) {
            leapChargeTicks = 0;
        }
    }

    public int leapChargeTicks() { return leapChargeTicks; }

    /**
     * Counts the wind-up on. It is deliberately not capped: the leap clamps how much of it is worth
     * charge, and the engine uses the running count to pace the held pose so a long hold keeps its stance
     * instead of falling back to the idle one.
     */
    public void tickLeapCharge() { leapChargeTicks++; }

    public int leapTicks() { return leapTicks; }
    public void setLeapTicks(int ticks) { leapTicks = Math.max(0, ticks); }
    public Vec3 leapAim() { return leapAim; }
    public void setLeapAim(Vec3 aim) { leapAim = aim; }
    public int leapTargetId() { return leapTargetId; }
    public void setLeapTargetId(int id) { leapTargetId = id; }
    public double leapTravel() { return leapTravel; }
    public void setLeapTravel(double travel) { leapTravel = travel; }
    public int leapElapsed() { return leapElapsed; }
    public void tickLeapElapsed() { leapElapsed++; }
    public void resetLeapElapsed() { leapElapsed = 0; }
    /** True from the launch until the tick that settles it, which is the step after the last one. */
    public boolean isLeaping() { return leapTicks >= NO_LEAP + 1; }

    /** Ends both halves of the leap: the wind-up and the dash. */
    public void stopLeap() {
        leapCharging = false;
        leapChargeTicks = 0;
        leapTicks = NO_LEAP;
        leapTargetId = -1;
        leapTravel = 0.0;
        leapElapsed = 0;
    }

    public int dodgeTicks() { return dodgeTicks; }
    public void setDodgeTicks(int ticks) { dodgeTicks = Math.max(0, ticks); }
    public int fallImmuneTicks() { return fallImmuneTicks; }
    public void setFallImmuneTicks(int ticks) { fallImmuneTicks = Math.max(0, ticks); }
    public int delayedHitTicks() { return delayedHitTicks; }
    public void setDelayedHitTicks(int ticks) { delayedHitTicks = Math.max(0, ticks); }
    public float delayedHitDamage() { return delayedHitDamage; }
    public void setDelayedHitDamage(float damage) { delayedHitDamage = damage; }
    public boolean delayedHitKnockback() { return delayedHitKnockback; }
    public void setDelayedHitKnockback(boolean knockback) { delayedHitKnockback = knockback; }
    public int delayedHitGroup() { return delayedHitGroup; }
    public void setDelayedHitGroup(int group) { delayedHitGroup = group; }
    public boolean isGuarding() { return guarding; }
    public int guardTicks() { return guardTicks; }

    public void setGuarding(boolean on) {
        if (on && !guarding) {
            guardTicks = 0;
        }
        guarding = on;
    }

    public void tickGuard() {
        guardTicks++;
    }

    // ------------------------------------------------------------------- misc

    /** Ticks the passive regeneration timer, returning the amount of Appetite to grant this tick. */
    public int tickRegen() {
        int interval = Math.max(1, Config.appetiteRegenInterval);
        if (++regenTimer < interval) {
            return 0;
        }
        regenTimer = 0;
        return Config.appetiteRegenAmount;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public void clearDirty() {
        dirty = false;
    }

    // ------------------------------------------------------------ persistence

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_APPETITE, appetite);
        tag.putIntArray(KEY_COOLDOWNS, Arrays.copyOf(cooldowns, cooldowns.length));
        tag.putInt(KEY_SELECTED, selected);
        tag.putInt(KEY_AWAKENED, awakenedTicks);
        tag.putLong(KEY_CELL_XP, cellXp);
        tag.putInt(KEY_NAIL_COMBO, nailComboSetting);
        tag.putInt(KEY_FORK_PROJECTILES, forkProjectileSetting);
        tag.putInt(KEY_KNIFE_WAVES, knifeWaveSetting);
        tag.putFloat(KEY_FLYING_DAMAGE, flyingDamageSetting);
        tag.putFloat(KEY_FLYING_SIZE, flyingSizeSetting);
        tag.putInt(KEY_KI_OUTPUT, kiOutputSetting);
        tag.putFloat(KEY_ATTACK_DAMAGE, attackDamageSetting);
        tag.putFloat(KEY_LEAP_DISTANCE, leapDistanceSetting);
        tag.putIntArray(KEY_SKILL_SLOTS, Arrays.copyOf(skillSlots, SLOT_COUNT));
        tag.putString(KEY_COMBAT_STYLE, combatStyle);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        cellXp = Math.max(0, tag.getLong(KEY_CELL_XP));
        appetite = Mth.clamp(tag.getInt(KEY_APPETITE), 0, maxAppetite());
        Arrays.fill(cooldowns, 0);
        copyCooldowns(tag.getIntArray(KEY_COOLDOWNS));
        selected = Mth.clamp(tag.getInt(KEY_SELECTED), 0, SkillType.COUNT - 1);
        awakenedTicks = Math.max(0, tag.getInt(KEY_AWAKENED));

        nailComboSetting = tag.contains(KEY_NAIL_COMBO) ? tag.getInt(KEY_NAIL_COMBO) : CellEvolution.NAIL_COMBO_BASE;
        forkProjectileSetting = tag.contains(KEY_FORK_PROJECTILES) ? tag.getInt(KEY_FORK_PROJECTILES) : CellEvolution.FORK_PROJECTILE_BASE;
        knifeWaveSetting = tag.contains(KEY_KNIFE_WAVES) ? tag.getInt(KEY_KNIFE_WAVES) : CellEvolution.KNIFE_WAVE_BASE;
        flyingDamageSetting = tag.contains(KEY_FLYING_DAMAGE) ? tag.getFloat(KEY_FLYING_DAMAGE) : CellEvolution.DAMAGE_MULT_BASE;
        flyingSizeSetting = tag.contains(KEY_FLYING_SIZE) ? tag.getFloat(KEY_FLYING_SIZE) : CellEvolution.SIZE_MULT_BASE;
        kiOutputSetting = tag.contains(KEY_KI_OUTPUT) ? tag.getInt(KEY_KI_OUTPUT) : CellEvolution.KI_OUTPUT_BASE;
        attackDamageSetting = tag.contains(KEY_ATTACK_DAMAGE) ? tag.getFloat(KEY_ATTACK_DAMAGE) : CellEvolution.ATTACK_DAMAGE_BASE;
        leapDistanceSetting = tag.contains(KEY_LEAP_DISTANCE) ? tag.getFloat(KEY_LEAP_DISTANCE) : CellEvolution.LEAP_DISTANCE_BASE;
        Arrays.fill(skillSlots, -1);
        int[] savedSlots = tag.getIntArray(KEY_SKILL_SLOTS);
        for (int i = 0; i < Math.min(savedSlots.length, SLOT_COUNT); i++) {
            setSkillSlot(i, savedSlots[i]);
        }
        combatStyle = CombatStyles.FIST.id();
        setCombatStyle(tag.getString(KEY_COMBAT_STYLE));
        clampSettingsToLevel();

        dirty = true;
    }
}
