package org.example.hanjinwoo.gourmet2.client.weapon;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.Iterator;
import java.util.Map;

/**
 * Tracks one active weapon flash per entity id on the client. Entries expire and remove themselves
 * as {@link #tick()} counts them down, so nothing needs to explicitly cancel a flash.
 */
public final class ClientWeaponFlashes {
    /** Total lifetime of a flash, in client ticks. */
    public static final int DURATION = 8;
    /** Ticks spent growing in before holding at full size. */
    public static final int GROW_TICKS = 2;
    /** Ticks spent shrinking away at the end. */
    public static final int SHRINK_TICKS = 3;

    private static final Int2ObjectMap<Flash> ACTIVE = new Int2ObjectOpenHashMap<>();

    private ClientWeaponFlashes() {}

    public static void trigger(int entityId, WeaponFlashType type, WeaponFlashLimb limb) {
        ACTIVE.put(entityId, new Flash(type, limb, DURATION));
    }

    /** Counts every active flash down by one tick, dropping the ones that just expired. */
    public static void tick() {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Int2ObjectMap.Entry<Flash>> it = ACTIVE.int2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            Flash flash = it.next().getValue();
            if (--flash.ticksLeft <= 0) {
                it.remove();
            }
        }
    }

    /**
     * The flash for {@code entityId}, or null if none is active. The returned object is live —
     * read it immediately, don't hold onto it across ticks.
     */
    public static Flash get(int entityId) {
        return ACTIVE.get(entityId);
    }

    /** Progress envelope for the flash's grow/hold/shrink animation, 0..1. */
    public static float scale(Flash flash) {
        int elapsed = DURATION - flash.ticksLeft;
        if (elapsed < GROW_TICKS) {
            return (elapsed + 1) / (float) (GROW_TICKS + 1);
        }
        if (flash.ticksLeft <= SHRINK_TICKS) {
            return flash.ticksLeft / (float) SHRINK_TICKS;
        }
        return 1.0F;
    }

    public static final class Flash {
        public final WeaponFlashType type;
        public final WeaponFlashLimb limb;
        int ticksLeft;

        private Flash(WeaponFlashType type, WeaponFlashLimb limb, int ticksLeft) {
            this.type = type;
            this.limb = limb;
            this.ticksLeft = ticksLeft;
        }
    }
}
