package org.example.hanjinwoo.gourmet2.skill;

import org.example.hanjinwoo.gourmet2.data.TorikoData;

/** Shared maths for charge-and-release skills (Leg Knife, Nail Gun, Flying Fork, Flying Knife). */
public final class Charge {
    /** Ticks of holding the key that reach a full charge. */
    public static final int MAX_TICKS = 40;

    private Charge() {}

    /** 0 for a tap, 1 for a full charge. */
    public static float fraction(int chargeTicks) {
        return Math.max(0.0F, Math.min(1.0F, chargeTicks / (float) MAX_TICKS));
    }

    /** Appetite for a cast at charge fraction {@code f}: up to double at a full charge. */
    public static int cost(int base, float f) {
        return Math.round(base * (1.0F + f));
    }

    /**
     * The highest charge fraction, no more than {@code wanted}, that the player can pay for, or -1 if
     * they cannot even afford an uncharged cast.
     */
    public static float affordable(TorikoData data, int base, float wanted) {
        float f = wanted;
        while (f > 0.0F && !data.canAfford(cost(base, f))) {
            f -= 0.05F;
        }
        f = Math.max(0.0F, f);
        return data.canAfford(cost(base, f)) ? f : -1.0F;
    }

    public static float lerp(float from, float to, float f) {
        return from + (to - from) * f;
    }
}
