package org.example.hanjinwoo.gourmet2.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.skill.SkillType;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The Lizardman's brain. It paths in like any melee mob (that part is {@link MeleeAttackGoal}'s) and then plays the
 * game the way a player at the keyboard would: it presses the same inputs, through {@link MobFighter}, and the player
 * systems do the rest. It chains its style's attack groups, throws a launcher when the target is on the ground and
 * follows it up with an air chase and a spike, winds up leaps at long range, and spends its skills on a schedule.
 * Nothing here knows how any of those work; that is the engines' business.
 */
class LizardmanFighterGoal extends MeleeAttackGoal {
    private static final double REACH = 3.8;
    private static final int SKILL_COOLDOWN = 50;
    private static final int LEAP_COOLDOWN = 90;
    private static final int AERIAL_COOLDOWN = 45;
    private static final int FOLLOW_UP_TICKS = 30;
    private static final int JUMP_COOLDOWN = 70;
    /** Ticks without progress toward a target that is still out of reach before it treats the way as blocked. */
    private static final int STUCK_TICKS = 6;
    private static final int HURDLE_COOLDOWN = 30;
    private static final int DETOUR_TICKS = 36;
    private static final double DETOUR_SPEED = 1.7;
    private static final int DASH_TICKS = 18;
    private static final int DASH_COOLDOWN = 140;

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
    private Vec3 lastPosition = Vec3.ZERO;
    private int stuck;
    private int hurdleCooldown;
    private int failedHurdles;
    private int detourTicks;
    private Vec3 detourPoint;
    private Vec3 anchor = Vec3.ZERO;
    private int anchorTicks;
    private int escapeStep;
    private int rushTicks;
    private int dashCooldown;
    private int flashCooldown;
    private Vec3 rushDirection = Vec3.ZERO;
    private final Set<Integer> rushHit = new HashSet<>();

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
        detourTicks = 0;
        stuck = 0;
        rushTicks = 0;
        anchorTicks = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        MobFighter fighter = lizardman.fighter();
        boolean free = target != null && target.isAlive() && fighter.hasTwin() && !stomping
                && !fighter.data().isBusy() && !fighter.data().isLeaping() && !fighter.data().isLeapCharging();
        if (free && avoidObstacles(target)) {
            mob.getLookControl().setLookAt(target, 60.0F, 60.0F);
            return;
        }
        super.tick();
        if (target == null || !target.isAlive()) {
            return;
        }
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
        // Sees a blow coming (the target is mid-swing, or has a skill running) and braces to shrug it off.
        if (distance <= 5.0 && blowComing(target) && mob.getRandom().nextInt(3) != 0 && fighter.resist()) {
            return;
        }
        if (!mob.onGround() && data.combatCooldown() == 0 && aerialCooldown == 0 && airContext(fighter, target, data)) {
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

        // Closing a gap in an instant: a flash step straight at the target.
        if (mob.onGround() && flashCooldown == 0 && distance >= 4.5 && distance <= 8.0
                && mob.getSensing().hasLineOfSight(target) && mob.getRandom().nextInt(16) == 0
                && fighter.flashStep(1.0F, 0.0F)) {
            flashCooldown = 60;
            return;
        }
        // Running at the target: the spinning dash (charges along the run, spinning through whatever is in the way).
        if (mob.isSprinting() && mob.onGround() && dashCooldown == 0 && distance >= 4.5 && distance <= 11.0
                && mob.getSensing().hasLineOfSight(target) && mob.getRandom().nextInt(9) == 0) {
            double dx = target.getX() - mob.getX();
            double dz = target.getZ() - mob.getZ();
            double flat = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
            startDashSpin(dx / flat, dz / flat);
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

    /**
     * Movement that overrides the ordinary chase. Returns true while it owns the body this tick:
     * the spinning dash, a detour around an obstacle, or breaking out of a pit or a covered hole.
     */
    private boolean avoidObstacles(LivingEntity target) {
        hurdleCooldown = Math.max(0, hurdleCooldown - 1);
        dashCooldown = Math.max(0, dashCooldown - 1);
        flashCooldown = Math.max(0, flashCooldown - 1);
        Vec3 position = mob.position();
        double movedSqr = position.subtract(lastPosition).horizontalDistanceSqr();
        lastPosition = position;
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        double fx = dx / Math.max(flat, 0.01);
        double fz = dz / Math.max(flat, 0.01);

        if (rushTicks > 0) {
            return rush();
        }

        // Hanging from a ceiling (it has swung up under one): crawl across it toward the target.
        boolean roofOver = org.example.hanjinwoo.gourmet2.skill.WallCling.ceilingAbove(mob);
        if (roofOver && target.getY() - mob.getY() <= 1.2 && flat > REACH + 0.5
                && (lizardman.isClimbing() || !mob.onGround() && mob.getDeltaMovement().y < 0.2)) {
            lizardman.wantCeiling();
            mob.getNavigation().stop();
            mob.setDeltaMovement(org.example.hanjinwoo.gourmet2.skill.WallCling.ceilingToward(mob.position(),
                    target.position()));
            mob.hasImpulse = true;
            return true;
        }

        // Wall climbing: a target above (or a hole to get out of) and a wall in the way means going up it, the way a
        // spider does, instead of jumping at it. Under a ceiling it stays with the punch-through in escape().
        boolean above = target.getY() - mob.getY() > 1.2;
        boolean boxedIn = anchorTicks >= 22 && (flat > REACH + 0.5 || above);
        if ((above || boxedIn) && !(boxedIn && LizardmanTerrain.ceilingAbove(lizardman, 2))
                && (lizardman.isClimbing() || LizardmanTerrain.wallHeight(lizardman, fx, fz) >= 1)) {
            lizardman.wantClimb();
            mob.getNavigation().stop();
            Vec3 wall = org.example.hanjinwoo.gourmet2.skill.WallCling.wallNormal(mob);
            if (wall != null) {
                // On the wall: cross it toward the target, up, down or sideways, the way the player can.
                mob.setDeltaMovement(org.example.hanjinwoo.gourmet2.skill.WallCling.velocityToward(wall,
                        mob.getBoundingBox().getCenter(), target.getBoundingBox().getCenter()));
                mob.hasImpulse = true;
            } else {
                mob.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.4);
            }
            return true;
        }

        // Trapped: no real progress for a while and the target is out of reach (or over the edge of the hole).
        if (position.distanceToSqr(anchor) > 0.8 * 0.8) {
            anchor = position;
            anchorTicks = 0;
            escapeStep = 0;
        } else {
            anchorTicks++;
        }
        boolean cannotHit = flat > REACH + 0.5 || target.getY() - mob.getY() > 2.2;
        if (anchorTicks >= 22 && cannotHit && mob.onGround()) {
            if (anchorTicks % 12 == 10) {
                escape(fx, fz);
            }
            return false;
        }

        if (detourTicks > 0) {
            detourTicks--;
            if (detourPoint == null) {
                return false;
            }
            if (!mob.onGround() && mob.getDeltaMovement().y > 0.0) {
                return true;
            }
            if (mob.position().distanceToSqr(detourPoint) < 1.5 * 1.5 || flat <= REACH) {
                detourTicks = 0;
                mob.getNavigation().stop();
                return false;
            }
            if (detourTicks % 4 == 0) {
                mob.getNavigation().moveTo(detourPoint.x, detourPoint.y, detourPoint.z, DETOUR_SPEED);
            }
            return true;
        }

        boolean chasing = flat > REACH + 0.6 && mob.onGround();
        boolean blocked = mob.horizontalCollision || movedSqr < 0.03 * 0.03 && !mob.getNavigation().isDone();
        stuck = chasing && blocked ? stuck + 1 : 0;
        if (chasing && movedSqr > 0.08 * 0.08) {
            failedHurdles = 0;
        }
        if (stuck < STUCK_TICKS) {
            return false;
        }
        stuck = 0;

        int wall = LizardmanTerrain.wallHeight(lizardman, fx, fz);
        if (wall >= 1 && wall <= 3 && failedHurdles < 2 && hurdleCooldown == 0
                && LizardmanTerrain.roofClear(lizardman, fx, fz, wall)) {
            hurdle(fx, fz, wall);
            failedHurdles++;
            return false;
        }
        int side = mob.getRandom().nextBoolean() ? 1 : -1;
        for (int i = 0; i < 2; i++, side = -side) {
            Vec3 point = new Vec3(mob.getX() - fz * side * 4.0 + fx * 2.5, mob.getY(),
                    mob.getZ() + fx * side * 4.0 + fz * 2.5);
            Path path = mob.getNavigation().createPath(point.x, point.y, point.z, 1);
            if (path != null && path.canReach()) {
                detourPoint = point;
                detourTicks = DETOUR_TICKS;
                mob.getNavigation().moveTo(path, DETOUR_SPEED);
                return true;
            }
        }
        if (wall >= 1 && hurdleCooldown == 0) {
            hurdle(fx, fz, Math.min(wall, 6));
        }
        return false;
    }

    /** One jump sized to clear a wall of this height, toward the target. */
    private void hurdle(double fx, double fz, int wall) {
        mob.setDeltaMovement(fx * 0.4, LizardmanTerrain.jumpVelocityFor(wall + 0.7), fz * 0.4);
        mob.hasImpulse = true;
        lizardman.playClip("jump");
        hurdleCooldown = HURDLE_COOLDOWN;
    }

    /**
     * Out of a pit or a covered hole. Ladder: a solid ceiling is punched open first; otherwise it jumps out with a
     * jump sized to the wall, and when that keeps failing (a deep pit) it digs steps into the wall and jumps
     * again, climbing the notches it made.
     */
    private void escape(double tx, double tz) {
        escapeStep++;
        double bx = tx;
        double bz = tz;
        int best = LizardmanTerrain.wallHeight(lizardman, tx, tz);
        double[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (double[] d : dirs) {
            int h = LizardmanTerrain.wallHeight(lizardman, d[0], d[1]);
            // Lower wall wins; the one facing the target gets a half-block head start.
            double bias = 0.5 * (d[0] * tx + d[1] * tz);
            if (h - bias < best - 0.5 * (bx * tx + bz * tz)) {
                best = h;
                bx = d[0];
                bz = d[1];
            }
        }
        mob.getNavigation().stop();
        if (LizardmanTerrain.ceilingAbove(lizardman, 4)) {
            mob.setDeltaMovement(mob.getDeltaMovement().x, 0.62, mob.getDeltaMovement().z);
            mob.hasImpulse = true;
            LizardmanTerrain.risePunch(lizardman, true);
            lizardman.playClip("rise_punch");
            return;
        }
        if (best <= 6 && escapeStep % 3 != 0) {
            mob.setDeltaMovement(bx * 0.38, LizardmanTerrain.jumpVelocityFor(best + 0.8), bz * 0.38);
            mob.hasImpulse = true;
            lizardman.playClip("jump");
            return;
        }
        LizardmanTerrain.dig(lizardman, bx, bz);
        lizardman.playClip("claw1");
        mob.setDeltaMovement(bx * 0.3, LizardmanTerrain.jumpVelocityFor(3.8), bz * 0.3);
        mob.hasImpulse = true;
    }

    /** The spinning dash: a burst of speed along the run, hitting everything the spin sweeps through. */
    private boolean rush() {
        rushTicks--;
        if (mob.horizontalCollision && rushTicks < DASH_TICKS - 4) {
            rushTicks = 0;
            return false;
        }
        double ramp = Math.min(1.0, (DASH_TICKS - rushTicks) / 4.0);
        double speed = 0.30 + 0.34 * ramp;
        mob.setDeltaMovement(rushDirection.x * speed, mob.getDeltaMovement().y, rushDirection.z * speed);
        mob.hasImpulse = true;
        if (rushTicks % 6 == 0) {
            rushHit.clear();
        }
        if (rushTicks < DASH_TICKS - 5) {
            for (LivingEntity victim : mob.level().getEntitiesOfClass(LivingEntity.class,
                    mob.getBoundingBox().inflate(1.7, 0.3, 1.7),
                    e -> e != mob && e.isAlive() && !(e instanceof LizardmanEntity) && !rushHit.contains(e.getId()))) {
                rushHit.add(victim.getId());
                if (mob.doHurtTarget(victim)) {
                    victim.push(rushDirection.x * 0.9, 0.35, rushDirection.z * 0.9);
                    victim.hurtMarked = true;
                }
            }
        }
        return true;
    }

    private void startDashSpin(double fx, double fz) {
        rushDirection = new Vec3(fx, 0.0, fz);
        rushTicks = DASH_TICKS;
        rushHit.clear();
        dashCooldown = DASH_COOLDOWN;
        mob.getNavigation().stop();
        lizardman.playClip("dash_spin_move");
    }

    /**
     * Attacks in the air follow the way the body is moving: on the way up with the target (or a ceiling) above, the
     * rising punch; on the way down with the target below, the one-legged stomp. Returns true if one was thrown.
     */
    private boolean airContext(MobFighter fighter, LivingEntity target, TorikoData data) {
        Vec3 motion = mob.getDeltaMovement();
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        double rise = target.getY() - mob.getY();
        if (motion.y > 0.08) {
            boolean overhead = rise > 0.5 && flat < 3.6 && rise < 5.0;
            if (overhead || LizardmanTerrain.ceilingAbove(lizardman, 3) && anchorTicks > 10) {
                LizardmanTerrain.risePunch(lizardman, anchorTicks > 10);
                fighter.aerial();
                aerialCooldown = AERIAL_COOLDOWN;
                return true;
            }
        } else if (motion.y < -0.05 && rise < 1.0 && flat < 3.4 && mob.distanceTo(target) < 4.8) {
            fighter.aerial();
            aerialCooldown = AERIAL_COOLDOWN;
            stomping = false;
            return true;
        }
        return false;
    }

    /** Whether the target looks to be in the middle of an attack aimed at this body. */
    private boolean blowComing(LivingEntity target) {
        if (target.swinging) {
            return true;
        }
        if (target instanceof net.minecraft.server.level.ServerPlayer player) {
            TorikoData theirs = org.example.hanjinwoo.gourmet2.registry.ModAttachments.of(player);
            return theirs.active() != null || theirs.isCharging();
        }
        return false;
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
