package org.example.hanjinwoo.gourmet2.skill;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Runtime state of a player's Chopsticks technique. Server-side and never persisted. */
public final class ChopsticksState {
    /** One creature held in a pair of chopsticks. {@code dest} is where a Transfer Pick sets it down. */
    public static final class Held {
        public final int targetId;
        public final int grabberId;
        public final Vec3 start;
        public final @Nullable Vec3 dest;
        /** Where the sticks' tip is while they are still travelling out to the creature (Pick only). */
        public Vec3 tip;
        /** Whether the sticks have reached and gripped the creature. */
        public boolean arrived;
        public int reachTicks;

        public Held(int targetId, int grabberId, Vec3 start, @Nullable Vec3 dest, Vec3 tip, boolean arrived) {
            this.targetId = targetId;
            this.grabberId = grabberId;
            this.start = start;
            this.dest = dest;
            this.tip = tip;
            this.arrived = arrived;
        }
    }

    public boolean active;
    public int mode;
    public int mainId = -1;
    public int ticksLeft;
    public int holdMode = -1;
    public int holdTicks;
    /** Fist Chopstick's Frenzy: sticks keep flying while this is set (the use key is held). */
    public boolean barrage;
    public int barrageTicks;
    public final List<Held> held = new ArrayList<>();
    /** Entity ids of creatures a Minority World zone is holding without collision, so they can be freed. */
    public final java.util.Set<Integer> affected = new java.util.HashSet<>();

    public boolean holding() {
        return holdMode >= 0;
    }

    public void reset() {
        active = false;
        mainId = -1;
        ticksLeft = 0;
        holdMode = -1;
        holdTicks = 0;
        barrage = false;
        barrageTicks = 0;
        held.clear();
        affected.clear();
    }
}
