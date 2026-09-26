package org.example.hanjinwoo.gourmet2.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * The Lizardman's own fighting style: three moves in rotation rather than one repeated swing — a right claw, a left
 * claw, then a tail slam that knocks the target back and resets the rotation. Each move triggers its own GeckoLib
 * animation (see {@link LizardmanEntity#registerControllers}); the timing and the decision to attack at all are
 * still {@link MeleeAttackGoal}'s own, so the creature paths in and swings exactly like any other melee mob.
 */
class LizardmanCombatGoal extends MeleeAttackGoal {
    private final LizardmanEntity lizardman;
    private int nextMove;

    LizardmanCombatGoal(LizardmanEntity lizardman, double speedModifier, boolean followEvenIfNotSeen) {
        super(lizardman, speedModifier, followEvenIfNotSeen);
        this.lizardman = lizardman;
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity target) {
        if (!canPerformAttack(target)) {
            return;
        }
        resetAttackCooldown();
        lizardman.swing(net.minecraft.world.InteractionHand.MAIN_HAND);

        int move = nextMove;
        nextMove = (nextMove + 1) % 3;
        switch (move) {
            case 0 -> {
                lizardman.triggerAnim("attack", "claw_right");
                if (lizardman.doHurtTarget(target)) {
                    lizardman.onClawHit(target);
                }
            }
            case 1 -> {
                lizardman.triggerAnim("attack", "claw_left");
                if (lizardman.doHurtTarget(target)) {
                    lizardman.onClawHit(target);
                }
            }
            default -> {
                lizardman.triggerAnim("attack", "tail_slam");
                if (lizardman.doHurtTarget(target)) {
                    lizardman.onTailSlamHit(target);
                }
            }
        }
    }
}
