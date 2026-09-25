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
 *
 * <p>There is no ceiling on the level itself: the curve keeps going and every cap here keeps opening
 * up, so a player who keeps eating keeps evolving. The body is the exception — it converges instead
 * (see {@link CellGrowth}).
 */
public final class CellEvolution {
    /** Default/starting value for every tunable setting - also level 0's cap. */
    public static final int NAIL_COMBO_BASE = nailComboCap(0);
    public static final int FORK_PROJECTILE_BASE = forkProjectileCap(0);
    public static final int NAIL_GUN_SHOT_BASE = nailGunShotCap(0);
    public static final int KNIFE_WAVE_BASE = knifeWaveCap(0);
    public static final int KI_OUTPUT_BASE = kiOutputCap(0);
    public static final float DAMAGE_MULT_BASE = 1.0F;
    public static final float SIZE_MULT_BASE = 1.0F;
    public static final float ATTACK_DAMAGE_BASE = 1.0F;
    public static final float LEAP_DISTANCE_BASE = 1.0F;
    public static final float RANGE_BASE = 1.0F;

    /**
     * Lowest the reach of the ranged techniques can be dialled to: nothing at all. At zero the thrown techniques
     * stop where they are thrown — a prong, a crescent or a round leaves the hand with no speed behind it and
     * hangs in the air in front of the caster until it dissipates — so a technique can be taken out of a fight
     * without being taken off the hotbar.
     */
    public static final float RANGE_FLOOR = 0.0F;

    /**
     * How long a full Nail Punch charge takes, whatever combo the player has dialled in. The charge is spent
     * at a rate proportional to that combo as a result — a 30-hit punch is not ten times the wait of a 3-hit
     * one, it winds up ten times as fast — and either fills in these same ticks. Shared by server and HUD.
     */
    public static final int NAIL_CHARGE_TICKS = 16;

    private CellEvolution() {}

    /** Nail Punch hits beyond the first earned per tick of charge, for a combo ceiling of {@code ceiling}. */
    public static float nailChargeRate(int ceiling) {
        return Math.max(0, ceiling - 1) / (float) NAIL_CHARGE_TICKS;
    }

    /**
     * What a Gourmet Cell level is worth in skill damage: five percent each, so the numbers climb
     * steadily as the cells evolve instead of jumping. Uncapped, like the level itself — every level makes
     * every technique a little meaner, for as long as the eating continues. A player who finds that too much
     * can wind it back down with {@link #ATTACK_DAMAGE_FLOOR}, without losing the level that earned it.
     */
    public static float skillDamageBonus(int level) {
        return level * 0.05F;
    }

    /** XP needed to go from {@code level} to {@code level + 1}. Every level costs a little more than the last. */
    public static long xpForNextLevel(int level) {
        return 100L * (level + 1);
    }

    /**
     * Cumulative XP (from level 0) needed to be exactly at {@code level}. Summed in closed form, since with no
     * ceiling on the level this is asked for every single time the level is read.
     */
    public static long xpForLevel(int level) {
        return 50L * level * (level + 1);
    }

    /**
     * The level {@code totalXp} has earned. Uncapped: however much a player eats, there is always another
     * level above the one they are on.
     */
    public static int levelForXp(long totalXp) {
        if (totalXp <= 0) {
            return 0;
        }
        // Inverse of xpForLevel — the largest L with 50 * L * (L + 1) <= totalXp — then settled exactly on
        // both sides, since a square root can land either way on the whole level it is meant to give.
        int level = Math.max(0, (int) Math.floor((Math.sqrt(2500.0 + 200.0 * totalXp) - 50.0) / 100.0));
        while (level > 0 && xpForLevel(level) > totalXp) {
            level--;
        }
        while (xpForLevel(level + 1) <= totalXp) {
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

    /** Highest number of Nail Gun rounds a full charge can put out. */
    public static int nailGunShotCap(int level) {
        return 4 + level;
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
     * How far down each setting may be wound. Every dial is a throttle as well as a boost: it starts at its
     * {@code *_BASE} default, opens up with the Cell level, and can be held back <i>below</i> that default — so a
     * player whose techniques have outgrown what they wanted to fight with can rein them in without giving up the
     * level that earned them. A count bottoms out at the fewest that still does what the technique is for (one),
     * the multipliers that shape the flying techniques at a quarter of the default they start from, and the three
     * that throttle the growth itself — attack damage, leap reach and the reach of the ranged techniques — all
     * the way at nothing ({@link #ATTACK_DAMAGE_FLOOR}).
     *
     * <p>Winding back is free: Appetite is charged by how far <i>up</i> its cap a dial has been pushed (see
     * {@link #powerFraction}), so anything at or below its default fires at the ordinary price. A dial's floor is
     * also what keeps it live on a fresh character: at level 0 the cap is the default, and without a floor below
     * it there would be nowhere for the slider to go at all.
     */
    public static final int NAIL_COMBO_FLOOR = 1;
    public static final int FORK_PROJECTILE_FLOOR = 1;
    public static final int NAIL_GUN_SHOT_FLOOR = 1;
    public static final int KNIFE_WAVE_FLOOR = 1;
    /**
     * Ki Release may be held at nothing at all: the aura still plays and its pressure still pushes, but it hurts
     * nobody and burns Appetite at the slowest rate (see {@code KiReleaseSkill}). The other counts stop at one
     * instead — a technique that does nothing at all is not a setting.
     */
    public static final int KI_OUTPUT_FLOOR = 0;
    public static final float DAMAGE_MULT_FLOOR = 0.25F;
    public static final float SIZE_MULT_FLOOR = 0.25F;

    /**
     * How far down the caster can dial their own attack damage, the reach of their leap, and the reach of their
     * ranged techniques ({@link #RANGE_FLOOR}): nothing at all. Those three dials are <i>throttles</i>, not boosts
     * — 100% is whatever the caster's own progress already earns them, their Cell level and their growing body
     * for one, their strength and speed for the other — and the only thing the dials do is hold them back from
     * it. That is why they are the ones let all the way to the bottom: a player whose hits have outgrown what
     * they wanted to fight with can wind a blow down to nothing without giving up the growth that got them there.
     *
     * <p>Zero is a setting like any other, not a broken one. Damage that comes to nothing does nothing — every
     * technique's damage is applied through {@code Hurt}, which drops anything that is not above zero — and a
     * charged leap with no reach behind it has nothing to fly, so the wind-up simply does not spend itself on a
     * launch (see {@code LeapEngine}). What is left of a zeroed dial is the technique's own shape: the knockback
     * of a blow, the aura and pressure of Ki, and the little hop a leap key tap always throws.
     */
    public static final float ATTACK_DAMAGE_FLOOR = 0.0F;
    public static final float LEAP_DISTANCE_FLOOR = 0.0F;

    /**
     * How far the ranged techniques reach at this Cell level, as a multiplier on what they always reached. The
     * flying techniques are thrown further and fly for longer, and the spray of a Nail Gun covers more ground.
     * Like the other two dials over it this is a ceiling, not a floor: the level gives the reach and the player's
     * own dial can only hold it back ({@link #RANGE_FLOOR}).
     */
    public static float rangeLevelBonus(int level) {
        return 1.0F + level * 0.05F;
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
