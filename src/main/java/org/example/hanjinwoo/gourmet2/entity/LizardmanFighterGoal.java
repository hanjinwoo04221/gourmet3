package org.example.hanjinwoo.gourmet2.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.skill.SkillType;

import java.util.Map;

/**
 * The Lizardman's brain. It paths in like any melee mob (that part is {@link MeleeAttackGoal}'s) and then plays the
 * game the way a player at the keyboard would: it presses the same inputs, through {@link MobFighter}, and the player
 * systems do the rest. It chains its style's attack groups, throws a launcher when the target is on the ground and
 * follows it up with an air chase and a spike, winds up leaps at long range, and spends its skills on a schedule.
 * Nothing here knows how any of those work; that is the engines' business.
 */
class LizardmanFighterGoal extends MeleeAttackGoal {
    private static final double REACH = 3.3;
    private static final int SKILL_COOLDOWN = 50;
    private static final int LEAP_COOLDOWN = 90;
    private static final int AERIAL_COOLDOWN = 45;
    private static final int FOLLOW_UP_TICKS = 30;
    private static final int JUMP_COOLDOWN = 70;

    /** The style attack groups, by index (see CombatStyles.LIZARDMAN). */
    private static final int RISE = 0;
    private static final int FLURRY = 1;
    private static final int TAIL = 2;

    /** A skill's usable range (min, max in blocks) and how many ticks the key is held, 0 for a plain press. */
    private record Loadout(double min, double max, int hold) {}

    private static final Map<SkillType, Loadout> SKILLS = Map.of(
            SkillType.NAIL_PUNCH, new Loadout(0.0, 4.5, 12),
            SkillType.FORK, new Loadout(1.5, 5.0, 0),
            SkillType.KNIFE, new Loadout(1.5, 4.5, 0),
            SkillType.NAIL_GUN, new Loadout(4.0, 14.0, 26),
            SkillType.LEG_KNIFE, new Loadout(1.5, 4.0, 10),
            SkillType.FLYING_FORK, new Loadout(5.0, 16.0, 18));

    private final LizardmanEntity lizardman;
    private int skillCooldown;
    private int leapCooldown;
    private int aerialCooldown;
    private int followUp;
    private int tapGap;
    private int heldSkill;
    private int leapHold;
    /** A chain being pressed out: which attack group, how many presses are left. */
    private int chainGroup;
    private int chainLeft;
    /** Ticks the body keeps driving in on the target while an attack plays out. */
    private int driveTicks;
    private int jumpCooldown;
    private boolean stomping;
    private int jumpAge;

    LizardmanFighterGoal(LizardmanEntity lizardman, double speedModifier) {
        super(lizardman, speedModifier, false);
        this.lizardman = lizardman;
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity target) {
        // Attacking is decided in think(), through the player systems.
    }

    @Override
    public void stop() {
        super.stop();
        MobFighter fighter = lizardman.fighter();
        if (fighter.hasTwin()) {
            if (heldSkill > 0) {
                fighter.releaseSkill();
            }
            if (leapHold > 0) {
                fighter.leapRelease();
            }
        }
        heldSkill = 0;
        leapHold = 0;
        followUp = 0;
        chainLeft = 0;
        driveTicks = 0;
        stomping = false;
    }

    @Override
    public void tick() {
        super.tick();
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        MobFighter fighter = lizardman.fighter();
        if (!fighter.hasTwin()) {
            return;
        }
        // Every move is made facing the target, head included.
        mob.getLookControl().setLookAt(target, 60.0F, 60.0F);
        fighter.aimAt(target.getBoundingBox().getCenter());
        think(fighter, target);
        drive(target);
    }

    /** Attacks are thrown while closing in, not from a standstill: keeps pushing the body toward the target. */
    private void drive(LivingEntity target) {
        if (driveTicks <= 0) {
            return;
        }
        driveTicks--;
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 1.4 || !mob.onGround()) {
            return;
        }
        Vec3 motion = mob.getDeltaMovement();
        double speed = Math.min(0.55, Math.sqrt(motion.x * motion.x + motion.z * motion.z) + 0.12);
        mob.setDeltaMovement(dx / flat * speed, motion.y, dz / flat * speed);
    }

    private void think(MobFighter fighter, LivingEntity target) {
        TorikoData data = fighter.data();
        skillCooldown = Math.max(0, skillCooldown - 1);
        leapCooldown = Math.max(0, leapCooldown - 1);
        aerialCooldown = Math.max(0, aerialCooldown - 1);
        tapGap = Math.max(0, tapGap - 1);
        jumpCooldown = Math.max(0, jumpCooldown - 1);
        double distance = mob.distanceTo(target);

        // A skill or a leap already being held comes up when its time is done.
        if (heldSkill > 0) {
            if (--heldSkill == 0) {
                fighter.releaseSkill();
            }
            return;
        }
        if (leapHold > 0) {
            mob.getNavigation().stop();
            if (--leapHold == 0) {
                fighter.leapRelease();
            }
            return;
        }
        if (data.isLeaping() || data.isBusy()) {
            return;
        }

        if (stomping) {
            stompFlight(fighter, target, data, distance);
            return;
        }

        // A chain in progress (rising claws, or the rake): each press goes in as soon as the last has recovered.
        if (chainLeft > 0) {
            if (distance > 5.5) {
                chainLeft = 0;
            } else if (data.combatCooldown() == 0) {
                fighter.attack(chainGroup);
                chainLeft--;
                driveTicks = chainGroup == FLURRY ? 8 : 6;
            }
            return;
        }

        // The follow-up to a launcher: double-tap the leap key at the airborne body, then spike it on the way down.
        if (followUp > 0) {
            followUp--;
            if (!target.onGround() && distance < 7.0) {
                if (mob.onGround() && tapGap == 0) {
                    fighter.leapCharge();
                    tapGap = 3;
                    return;
                }
                if (!mob.onGround() && mob.getDeltaMovement().y <= 0.0 && distance < 4.5 && aerialCooldown == 0) {
                    fighter.aerial();
                    aerialCooldown = AERIAL_COOLDOWN;
                    followUp = 0;
                    return;
                }
            }
        }

        boolean inReach = distance <= REACH && mob.getSensing().hasLineOfSight(target);
        if (inReach && data.combatCooldown() == 0) {
            if (!target.onGround() && !mob.onGround() && mob.getDeltaMovement().y <= 0.0 && aerialCooldown == 0) {
                fighter.aerial();
                aerialCooldown = AERIAL_COOLDOWN;
                return;
            }
            int roll = mob.getRandom().nextInt(12);
            if (roll < 5) {
                startChain(fighter, FLURRY, 4);
            } else if (roll < 8) {
                startChain(fighter, RISE, 2 + mob.getRandom().nextInt(2));
            } else if (roll == 8 && distance < 2.6) {
                fighter.attack(TAIL);
                driveTicks = 6;
            } else if (roll == 9 && target.onGround() && mob.onGround() && aerialCooldown == 0) {
                fighter.aerial();
                aerialCooldown = AERIAL_COOLDOWN;
                followUp = FOLLOW_UP_TICKS;
            } else if (roll >= 10 && jumpCooldown == 0 && startStomp(fighter, target, distance)) {
                return;
            } else {
                startChain(fighter, FLURRY, 4);
            }
            return;
        }

        // A little further out it either dashes in raking, or jumps to come down on the target from above.
        if (mob.onGround() && data.combatCooldown() == 0 && mob.getSensing().hasLineOfSight(target)) {
            if (distance > REACH && distance <= 4.8 && mob.getRandom().nextInt(10) == 0) {
                startChain(fighter, FLURRY, 4);
                return;
            }
            if (jumpCooldown == 0 && distance >= 3.0 && distance <= 10.0 && mob.getRandom().nextInt(14) == 0
                    && startStomp(fighter, target, distance)) {
                return;
            }
        }

        if (skillCooldown == 0 && trySkill(fighter, data, distance)) {
            skillCooldown = SKILL_COOLDOWN + mob.getRandom().nextInt(40);
            return;
        }

        if (leapCooldown == 0 && distance >= 7.0 && distance <= 18.0 && mob.onGround()
                && mob.getRandom().nextInt(8) == 0) {
            fighter.leapCharge();
            if (data.isLeapCharging()) {
                leapHold = Math.max(6, Math.min(20, (int) (distance * 1.1)));
                leapCooldown = LEAP_COOLDOWN;
            }
        }
    }

    private void startChain(MobFighter fighter, int group, int presses) {
        chainGroup = group;
        chainLeft = presses - 1;
        fighter.attack(group);
        driveTicks = group == FLURRY ? 8 : 6;
    }

    /**
     * The jump of the aerial stomp: the height follows the situation (further off or higher up is a bigger jump) and it
     * is aimed to land on the target. The stomp itself is the player aerial attack, pressed on the way down.
     */
    private boolean startStomp(MobFighter fighter, LivingEntity target, double distance) {
        if (!mob.onGround()) {
            return false;
        }
        double rise = Math.max(0.0, target.getY() - mob.getY());
        double up = Mth.clamp(0.5 + distance * 0.02 + rise * 0.1, 0.5, 0.9);
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        double flat = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        double out = Mth.clamp((flat - 1.2) / 8.4, 0.0, 0.9);
        mob.setDeltaMovement(dx / flat * out, up, dz / flat * out);
        mob.hasImpulse = true;
        mob.getNavigation().stop();
        lizardman.playClip("jump");
        stomping = true;
        jumpAge = 0;
        jumpCooldown = JUMP_COOLDOWN;
        return true;
    }

    /** In the air on a stomp: steer at the target, and bring the foot down once it is under the body and falling. */
    private void stompFlight(MobFighter fighter, LivingEntity target, TorikoData data, double distance) {
        jumpAge++;
        if ((jumpAge > 3 && mob.onGround()) || jumpAge > 60) {
            stomping = false;
            return;
        }
        Vec3 motion = mob.getDeltaMovement();
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat > 1.5) {
            mob.setDeltaMovement(motion.x + dx / flat * 0.025, motion.y, motion.z + dz / flat * 0.025);
        }
        if (motion.y <= 0.0 && distance <= 3.6 && data.combatCooldown() == 0 && aerialCooldown == 0) {
            fighter.aerial();
            aerialCooldown = AERIAL_COOLDOWN;
            stomping = false;
            driveTicks = 0;
        }
    }

    /** Picks a skill it has unlocked, can afford and that suits the range, and presses it. */
    private boolean trySkill(MobFighter fighter, TorikoData data, double distance) {
        SkillType[] order = SKILLS.keySet().toArray(new SkillType[0]);
        int start = mob.getRandom().nextInt(order.length);
        for (int i = 0; i < order.length; i++) {
            SkillType skill = order[(start + i) % order.length];
            Loadout loadout = SKILLS.get(skill);
            if (distance < loadout.min() || distance > loadout.max() || !skill.isUnlocked(data.cellLevel())
                    || !data.isReady(skill) || !data.canAfford(skill.appetiteCost())) {
                continue;
            }
            fighter.select(skill);
            fighter.useSkill();
            if (data.isCharging()) {
                heldSkill = Math.max(1, loadout.hold());
            }
            return true;
        }
        return false;
    }
}
