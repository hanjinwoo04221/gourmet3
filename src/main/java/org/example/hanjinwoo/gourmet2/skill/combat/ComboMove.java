package org.example.hanjinwoo.gourmet2.skill.combat;

/**
 * One blow of an {@link AttackGroup}: the animation clip it plays and what that blow does.
 *
 * <p>The group's last move is its finisher: it knocks the victim away.
 *
 * @param clip           animation clip under the style's animationDir (see {@link CombatStyle})
 * @param damage         damage of this blow, before cell level and the style's attack speed
 * @param clipTicks      the clip's length in ticks (24 fps clips at 20 tps), which is also how long the
 *                       player stays in Epic Fight mode so the animation is never cut off
 * @param recoveryTicks  ticks the next swing is locked out for, or 0 for the usual "as long as the clip
 *                       takes, divided by the style's attack speed"; moves that must play out in full
 *                       (a second impact landing late, say) set it explicitly
 * @param secondHitDelay ticks after the first blow that a second one lands, or 0 for a single blow
 */
public record ComboMove(String clip, float damage, int clipTicks, int recoveryTicks, int secondHitDelay) {

    public ComboMove(String clip, float damage, int clipTicks) {
        this(clip, damage, clipTicks, 0, 0);
    }

    /** Whether this blow's damage is split over a second impact landing {@link #secondHitDelay()} later. */
    public boolean twoHits() {
        return secondHitDelay > 0;
    }

    /** Ticks this move locks the next swing out for, with the style's speed applied when it is not set. */
    public int lockTicks(float attackSpeed) {
        return recoveryTicks > 0 ? recoveryTicks : Math.round(clipTicks / attackSpeed);
    }
}
