package org.example.hanjinwoo.gourmet2.skill;

/**
 * The Gourmet Cell growth curve: eating raises {@code cellXp} (see {@code SkillEngine.onFoodEaten}),
 * which levels up {@code cellLevel}, which raises the ceiling on everything else — max Appetite,
 * and every skill's tunable power cap in {@link org.example.hanjinwoo.gourmet2.data.TorikoData}.
 *
 * <p>Pure math, no state — shared by client and server so the settings GUI can show the right caps
 * locally instead of waiting on a round trip. Level 0's caps all match this mod's original fixed
 * values, so a fresh player sees no change in power until they actually level up and choose to
 * spend the new headroom (see the {@code *_BASE} constants, which double as the default settings).
 */
public final class CellEvolution {
    public static final int MAX_LEVEL = 10;

    /** Default/starting value for every tunable setting - also level 0's cap. */
    public static final int NAIL_COMBO_BASE = nailComboCap(0);
    public static final int FORK_PROJECTILE_BASE = forkProjectileCap(0);
    public static final int KNIFE_WAVE_BASE = knifeWaveCap(0);
    public static final int KI_OUTPUT_BASE = kiOutputCap(0);
    public static final float DAMAGE_MULT_BASE = 1.0F;
    public static final float SIZE_MULT_BASE = 1.0F;

    /** Charge ticks needed per extra Nail Punch hit beyond the first. Shared by server and HUD. */
    public static final int NAIL_CHARGE_TICKS_PER_HIT = 8;

    private CellEvolution() {}

    /** XP needed to go from {@code level} to {@code level + 1}. Undefined past {@link #MAX_LEVEL}. */
    public static long xpForNextLevel(int level) {
        return 100L * (level + 1);
    }

    /** Cumulative XP (from level 0) needed to be exactly at {@code level}. */
    public static long xpForLevel(int level) {
        long total = 0;
        for (int i = 0; i < level; i++) {
            total += xpForNextLevel(i);
        }
        return total;
    }

    public static int levelForXp(long totalXp) {
        int level = 0;
        long remaining = totalXp;
        while (level < MAX_LEVEL && remaining >= xpForNextLevel(level)) {
            remaining -= xpForNextLevel(level);
            level++;
        }
        return level;
    }

    /** How far into the current level {@code totalXp} is, for a progress bar. */
    public static long xpIntoLevel(long totalXp, int level) {
        return Math.max(0, totalXp - xpForLevel(level));
    }

    // ---------------------------------------------------------------------- caps

    /** Bonus max Appetite on top of the config base, from eating enough to evolve. */
    public static int maxAppetiteBonus(int level) {
        return level * 20;
    }

    /** Highest nail-punch combo length a full charge can reach. */
    public static int nailComboCap(int level) {
        return 3 + level;
    }

    /** Highest number of Flying Fork prongs a single cast can launch. */
    public static int forkProjectileCap(int level) {
        return 4 + level / 2;
    }

    /** Highest number of Flying Knife waves a single cast can launch. */
    public static int knifeWaveCap(int level) {
        return 1 + level / 3;
    }

    /** Highest Ki Release output level: more output means a bigger aura, harder pressure and faster drain. */
    public static int kiOutputCap(int level) {
        return 1 + level;
    }

    /** Highest damage multiplier the flying techniques can be tuned to. */
    public static float damageMultCap(int level) {
        return 1.0F + level * 0.08F;
    }

    /** Highest size multiplier the flying techniques can be tuned to. */
    public static float sizeMultCap(int level) {
        return 1.0F + level * 0.06F;
    }

    /**
     * Highest block hardness a thrown technique can punch straight through instead of stopping
     * against it. 0 at level 0 — a fresh Gourmet Cell only manages the always-weak blocks (see
     * {@code Hurt.breakByPower}) — growing to 50 (obsidian) at max level. Unbreakable blocks
     * (bedrock, barriers: negative hardness) are never affected, at any level.
     */
    public static float blockHardnessCap(int level) {
        return level * 5.0F;
    }

    // ---------------------------------------------------------------- cost scaling

    /**
     * 0 at {@code base} (the cheapest setting), 1 at {@code cap} (the current ceiling) — how far a
     * player has dialled a tunable setting up. Used to scale Appetite cost: independent per-skill
     * caps, but pushing any of them higher costs more to actually fire, per the mod's design.
     */
    public static float powerFraction(double value, double base, double cap) {
        if (cap <= base) {
            return 0.0F;
        }
        double fraction = (value - base) / (cap - base);
        return (float) Math.max(0.0, Math.min(1.0, fraction));
    }
}
