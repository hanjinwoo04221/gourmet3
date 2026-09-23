package org.example.hanjinwoo.gourmet2.client.renderer;

import net.minecraft.util.Mth;

/** Shared easing for the short-lived visual entities. */
public final class VisualEasing {
    private VisualEasing() {}

    /** Fast at the start, settling towards the end — good for impacts expanding outwards. */
    public static float easeOut(float t) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - (1.0F - clamped) * (1.0F - clamped);
    }

    /**
     * Alpha envelope: fades in quickly, holds, then fades out.
     *
     * @param t       normalised age, 0..1
     * @param fadeIn  fraction of the lifetime spent fading in
     * @param fadeOut fraction of the lifetime spent fading out
     */
    public static float envelope(float t, float fadeIn, float fadeOut) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        if (fadeIn > 0.0F && clamped < fadeIn) {
            return clamped / fadeIn;
        }
        float outStart = 1.0F - fadeOut;
        if (fadeOut > 0.0F && clamped > outStart) {
            return Math.max(0.0F, (1.0F - clamped) / fadeOut);
        }
        return 1.0F;
    }
}
