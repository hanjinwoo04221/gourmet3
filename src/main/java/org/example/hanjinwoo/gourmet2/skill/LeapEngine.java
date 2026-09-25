package org.example.hanjinwoo.gourmet2.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.example.hanjinwoo.gourmet2.compat.CombatAnimations;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.entity.ShockwaveRingEntity;
import org.example.hanjinwoo.gourmet2.entity.UpheavalEntity;
import org.example.hanjinwoo.gourmet2.registry.ModAttachments;
import org.example.hanjinwoo.gourmet2.registry.ModDamageTypes;
import org.jetbrains.annotations.Nullable;

/**
 * The charged leap: hold the leap key while supported by a surface to wind up, let go to launch at
 * speed in the direction the player is looking.
 *
 * <p>Two things decide where it ends. The wind-up decides how far it *can* go — a tap barely hops, a
 * full charge reaches {@link #MAX_CHARGE_TICKS} ticks' worth — and the attributes decide what a full
 * charge is worth: attack damage ("힘") and movement speed ("속도") each add reach on top of the
 * baseline, up to {@link #DISTANCE_CAP}. If the crosshair is on an entity that is actually visible,
 * the leap homes at it instead and stops just short, even when the charge would have carried further.
 *
 * <p>The dash is server-driven and re-aimed every tick, so it lands on the mark rather than wherever
 * an impulse happened to end up. Reach is spent as speed as well as distance — a longer leap cruises
 * faster instead of taking proportionally longer — and nothing is capped: however far the attributes
 * say the leap reaches is how far it goes. Everything is transient state on {@link TorikoData};
 * nothing here persists.
 */
public final class LeapEngine {
    /** Ticks of holding the key that reach a full charge. Matches the length of the wind-up clip. */
    public static final int MAX_CHARGE_TICKS = 20;

    /**
     * The wind-up is a one-shot clip, so once it has played Epic Fight drops the player back into the idle
     * stance while the key is still down — which is what made a long charge unwind halfway. Past this many
     * charge ticks the frozen hold pose is played instead, re-played every {@link #HOLD_REPLAY_TICKS}
     * ticks. The hold clip is static, so re-playing it shows nothing, and the period is shorter than the
     * clip is long, so the animation is always restarted before it could end and drop back to the idle
     * stance. That also keeps Epic Fight mode alive for the whole wind-up, however long it is held.
     */
    private static final int HOLD_FROM_CHARGE_TICKS = 18;
    private static final int HOLD_REPLAY_TICKS = 6;

    /**
     * The launch clip has reached its flight pose by about this many ticks in, and its tail is the landing —
     * so a leap still flying from there on holds that pose instead, re-played every
     * {@link #FLY_HOLD_REPLAY_TICKS} ticks. Same reasoning as the wind-up hold, and the same static clips.
     */
    private static final int FLY_HOLD_FROM_TICKS = 8;
    private static final int FLY_HOLD_REPLAY_TICKS = 6;

    /** How far ahead the crosshair can pick an entity to fly at. */
    private static final double TARGET_RANGE = 24.0;
    /**
     * How quickly the leap key has to come back down for the second press to read as a double tap rather than
     * as the start of a wind-up, and what that double tap buys (see {@link #chaseAirborne}). A press with no
     * airborne mark under the crosshair does nothing at all, so the only thing a stray double tap costs is the
     * little hop the first tap would have thrown anyway.
     */
    private static final int DOUBLE_TAP_TICKS = 8;
    /**
     * How long one moment of Slow Falling lasts for both halves of an air duel — the one handed over as the
     * chase arrives, and every one handed over after that as the two of them trade blows. Deliberately short:
     * the duel hangs for exactly as long as they keep at each other, and starts coming down the moment they stop.
     */
    private static final int SKY_HANG_TICKS = 15;
    /**
     * How long after a launch a double tap can still follow that body up by memory, without the caster having to
     * put the crosshair on it. Everything worth chasing has come back down well inside this.
     */
    private static final int LAUNCH_MEMORY_TICKS = 60;
    /** Blocks covered by a tap / by a full charge at baseline attributes. */
    private static final double MIN_DISTANCE = 2.0;
    private static final double MAX_DISTANCE = 9.0;
    /** Baseline player attributes; only what a player has above these lengthens the leap. */
    private static final double BASE_ATTACK_DAMAGE = 1.0;
    private static final double BASE_MOVEMENT_SPEED = 0.1;
    /**
     * Extra blocks of reach per point of attack damage ("힘") and per point of movement speed ("속도").
     * Movement speed attribute values are small — 0.1 is a bare player and a Speed II potion only lifts
     * that to about 0.14 — so its factor is far larger than the damage one to be worth anything at all.
     */
    private static final double BLOCKS_PER_DAMAGE = 0.9;
    private static final double BLOCKS_PER_SPEED = 120.0;
    /** Cruise speed of the dash in blocks per tick, before the reach is added on top of it. */
    private static final double CRUISE_SPEED = 1.15;
    /** Extra blocks per tick of cruise speed per block of reach: a longer leap also flies faster. */
    private static final double SPEED_PER_BLOCK = 0.07;
    /**
     * Air resistance: the fraction of the launch speed the flight bleeds off by the time it arrives. The dash
     * therefore flies on a decaying profile instead of at one flat speed, and its flight is planned on the
     * average of that profile, so the drag costs time rather than reach.
     */
    private static final double AIR_DRAG = 0.9;
    /** Gravity the leap is flown under, in blocks per tick per tick. Vanilla is 0.08. */
    private static final double GRAVITY = 0.2;
    /** How far outside the player's own bounding box a block still counts as something to stand on. */
    private static final double SURFACE_REACH = 0.08;
    /** How much of the dash's speed is kept when it settles, so a landing slides instead of stopping dead. */
    private static final double SETTLE_TAKE = 0.2;
    /** The slowest the dash is ever allowed to fly. It also bounds how far a breakthrough can stretch it. */
    private static final double MIN_DASH_SPEED = 0.4;
    /**
     * What the dash is worth against something in the way: per block per tick it is flying at, and per point
     * of attack damage ("힘"). A block is beaten when this beats its hardness, an entity when it beats that
     * entity's armour.
     */
    private static final double POWER_PER_SPEED = 0.5;
    private static final double POWER_PER_DAMAGE = 0.0;
    /**
     * What going through something costs in speed. The dash is slowed in proportion to how close the thing
     * it went through came to stopping it: a soft block barely costs anything, one at the very limit of the
     * power more than halves the speed.
     */
    private static final double BREAK_KEEP_LIGHT = 0.005;
    private static final double BREAK_KEEP_HEAVY = 0.001;
    /**
     * How far ahead a body is picked up as being hit. Deliberately short: a long leap is a long sweep, and
     * sweeping the whole of it would hit things on the far side of a wall.
     */
    private static final double IMPACT_REACH = 2.5;
    /** Damage and shove of a body hit, per block per tick of speed and per point of attack damage. */
    private static final float IMPACT_DAMAGE_PER_SPEED = 1.5F;
    private static final float IMPACT_DAMAGE_PER_DAMAGE = 0.5F;
    private static final double IMPACT_KNOCKBACK_PER_SPEED = 0.8;
    /** A shove is capped as well, so a very fast leap does not fling what it hits out of the world. */
    private static final double IMPACT_KNOCKBACK_CAP = 3.0;
    /** Close enough to the aim point to have arrived, and how far short of a target entity to stop. */
    private static final double ARRIVE_EPSILON = 0.75;
    private static final double ENTITY_STAND_OFF = 0.7;
    /**
     * Fall damage is ignored for at least this long after a leap, plus a tick per block of reach, since a
     * long leap can also be a long fall — a dive off a ceiling onto the floor should be free.
     */
    private static final int FALL_IMMUNITY_TICKS = 60;

    private LeapEngine() {}

    /** The leap key went down: plant and wind up, if there is a surface to push off from. */
    public static void charge(ServerPlayer player) {
        TorikoData data = ModAttachments.of(player);
        if (data.isLeapCharging()) {
            return;
        }
        // A press hot on the heels of the last one is not a wind-up: it is the air chase. Read before the
        // "already flying" guard below, because the little hop the first tap threw is exactly what it overtakes.
        int sinceTap = player.tickCount - data.leapTapTick();
        boolean doubleTap = sinceTap >= 0 && sinceTap <= DOUBLE_TAP_TICKS;
        data.setLeapTapTick(player.tickCount);
        if (doubleTap && chaseAirborne(player, data)) {
            return;
        }
        if (data.isLeaping() || !supported(player)) {
            return;
        }
        data.setLeapCharging(true);
        data.setGuarding(false);
        CombatAnimations.playSkill(player, "leap_charge");
    }

    /**
     * The double tap: go straight at whatever the crosshair is on, provided it is off the ground, at the full
     * reach and with no wind-up at all. Launched prey is the whole point — the move is for catching something
     * that has just been thrown into the air before it comes back down. The mark is remembered as the partner of
     * the duel that follows ({@link #exchanged}), and the two of them open it on one short moment of Slow Falling
     * rather than dropping past each other. It works whether or not the caster has a surface under them, since
     * the ground they pushed off was left behind on the first tap.
     *
     * @return whether the chase happened; false (and nothing spent) if nothing airborne was under the crosshair
     */
    private static boolean chaseAirborne(ServerPlayer player, TorikoData data) {
        LivingEntity mark = airborneAim(player);
        if (mark == null) {
            mark = airborneLaunch(player, data);
        }
        if (mark == null) {
            return false;
        }
        // The chase flies as far as a full charge would reach, held back by the same dial the charge is.
        if (!launch(player, data, maxDistance(player) * data.leapDistanceSetting(), mark, true)) {
            return false;
        }
        hang(player, mark);
        data.setSkyPartner(mark.getId());
        return true;
    }

    /**
     * A blow traded in the air between the caster and the body they chased up there, in either direction, or a
     * guard raised against one. Every exchange buys both of them {@link #SKY_HANG_TICKS} more of falling very
     * slowly, so the duel hangs up there for exactly as long as the two of them are actually at each other, and
     * starts coming down the moment they stop — which is what keeps it a fight in the air rather than a pair of
     * bodies parked in one.
     */
    public static void exchanged(ServerPlayer player, @Nullable Entity other) {
        if (other != null && other.getId() == ModAttachments.of(player).skyPartnerId()) {
            holdTheAir(player);
        }
    }

    /** The caster raised a guard mid-duel, which is an exchange of its own. */
    public static void guarded(ServerPlayer player) {
        holdTheAir(player);
    }

    /**
     * Hands both halves of an air duel another moment of Slow Falling, if there is a duel on: a partner still
     * alive, both of them still off the ground, and the caster in the air to begin with. Once either of them is
     * back on solid ground there is nothing left to hold up, and this quietly does nothing.
     */
    private static void holdTheAir(ServerPlayer player) {
        TorikoData data = ModAttachments.of(player);
        if (data.skyPartnerId() < 0 || !data.isSkyDuelAirborne() || player.onGround()
                || !(player.level().getEntity(data.skyPartnerId()) instanceof LivingEntity mark)
                || !mark.isAlive() || mark.onGround()) {
            return;
        }
        hang(player, mark);
    }

    /**
     * Closes an air duel the moment the caster is back on solid ground, however it ended. A duel that outlived the
     * chase that opened it would keep paying out: the partner would stay remembered for the rest of the session,
     * and then any later jump followed by an exchange of blows with that same body would start holding the two of
     * them up again, out of nothing. Called every tick, from {@code SkillEngine}.
     */
    public static void tickDuel(ServerPlayer player, TorikoData data) {
        if (data.skyPartnerId() < 0) {
            return;
        }
        if (player.onGround()) {
            if (data.isSkyDuelAirborne()) {
                data.endSkyDuel();
            }
            return;
        }
        data.setSkyDuelAirborne(true);
    }

    /** One moment of Slow Falling for both of them, so neither drops out of the fight. */
    private static void hang(ServerPlayer player, LivingEntity mark) {
        mark.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SKY_HANG_TICKS, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SKY_HANG_TICKS, 0, false, false, true));
    }

    /** Whatever airborne body is under the crosshair, or null. */
    private static @Nullable LivingEntity airborneAim(ServerPlayer player) {
        Entity aimed = aimedEntity(player);
        return aimed instanceof LivingEntity mark && !mark.onGround() ? mark : null;
    }

    /**
     * The body this player most recently threw into the air, if it is still up there and still within reach. A
     * launched body is somewhere overhead, which is the worst place to ask anyone to aim, so for a moment after
     * the launch the chase does not ask: it simply follows the last thing that was thrown.
     */
    private static @Nullable LivingEntity airborneLaunch(ServerPlayer player, TorikoData data) {
        int age = player.tickCount - data.launchedTick();
        if (data.launchedTargetId() < 0 || age < 0 || age > LAUNCH_MEMORY_TICKS) {
            return null;
        }
        // Deliberately no range test: whether the launch threw them six blocks up or twenty, the chase flies
        // as far towards them as the leap reaches and hangs there, and the falling body comes down to meet it.
        return player.level().getEntity(data.launchedTargetId()) instanceof LivingEntity mark
                && mark.isAlive() && !mark.onGround() ? mark : null;
    }

    /** The leap key came up: launch, however far the wind-up earned. */
    public static void release(ServerPlayer player) {
        TorikoData data = ModAttachments.of(player);
        if (!data.isLeapCharging()) {
            return;
        }
        int chargeTicks = data.leapChargeTicks();
        float fraction = Math.min(1.0F, chargeTicks / (float) MAX_CHARGE_TICKS);
        // The dial is applied here, over the whole of what the attributes earn, so that maxDistance() stays the
        // plain distance the power settings screen can show.
        double reach = Charge.lerp((float) MIN_DISTANCE,
                (float) (maxDistance(player) * data.leapDistanceSetting()), fraction);
        launch(player, data, reach, aimedEntity(player), false);
    }

    /**
     * Flies the dash over {@code reach}, aimed at {@code target} if the crosshair picked one up and at a point
     * that far ahead of the caster's eyes otherwise. Whatever the leap key was doing stops here.
     *
     * @param chase whether this is an air chase, which flies straight at the mark instead of solving an arc
     * @return whether a dash actually started — there is nothing to fly if the aim is underfoot
     */
    private static boolean launch(ServerPlayer player, TorikoData data, double reach, @Nullable Entity target,
            boolean chase) {
        Vec3 aim = target == null
                ? player.getEyePosition().add(player.getLookAngle().scale(reach))
                : stopPoint(player, target);
        Vec3 toAim = aim.subtract(player.position());
        double travel = Math.min(reach, toAim.length());
        if (travel < 0.5) {
            return false;
        }

        data.setLeapCharging(false);
        data.setLeapChasing(chase);
        data.setLeapAim(aim);
        data.setLeapTargetId(target == null ? -1 : target.getId());
        data.setLeapTravel(travel);
        data.resetLeapElapsed();
        // Planned on the average speed of the drag profile, so air resistance costs time, not reach.
        data.setLeapTicks(Math.max(2, (int) Math.ceil(plannedTicks(travel))));
        data.setFallImmuneTicks(FALL_IMMUNITY_TICKS + (int) travel);
        data.setGuarding(false);
        // Each flight clip is laid out along the direction of travel, so a climb needs the upright one —
        // the flat pose would have the body lying sideways in the air on the way up. Anything steeper than
        // about 35 degrees above level counts as a climb.
        double flatTravel = Math.sqrt(toAim.x * toAim.x + toAim.z * toAim.z);
        CombatAnimations.playSkill(player, toAim.y > flatTravel * 0.7 ? "leap_up" : "leap");
        if (supported(player)) {
            // Only a leap with something under it tears that something up: a chase started in midair has no
            // ground to push off, and the burst would be thrown into empty air where nothing could be seen of it.
            liftGround(player, data);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.9F, 1.4F);
        return true;
    }

    /** Winds the charge up while the key is held, then flies the dash a tick at a time. */
    public static void tick(ServerPlayer player, TorikoData data) {
        if (data.isLeapCharging()) {
            if (!supported(player)) {
                // The surface is gone, or they stepped off it: the wind-up needs something to push from.
                data.stopLeap();
                return;
            }
            data.tickLeapCharge();
            int held = data.leapChargeTicks() - HOLD_FROM_CHARGE_TICKS;
            if (held >= 0 && held % HOLD_REPLAY_TICKS == 0) {
                // The wind-up has eased into the coil: hold it from here on. Re-played before the previous
                // one could finish, so the stance never lapses and Epic Fight mode never drops either.
                CombatAnimations.playSkill(player, "leap_hold");
            }
            // Planted for the wind-up: the horizontal motion of whatever they were doing is dropped, but
            // falling still settles them onto the floor.
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(0.0, motion.y, 0.0);
            player.hurtMarked = true;
            return;
        }
        if (!data.isLeaping()) {
            return;
        }
        Vec3 aim = data.leapAim();
        int targetId = data.leapTargetId();
        if (targetId >= 0) {
            Entity target = player.level().getEntity(targetId);
            if (target != null && target.isAlive()) {
                aim = stopPoint(player, target);
                data.setLeapAim(aim);
            } else {
                // The mark left the world: fly out the rest of the dash straight, with nothing left to chase.
                data.setLeapTargetId(-1);
                data.setLeapChasing(false);
            }
        }
        Vec3 toAim = aim.subtract(player.position());
        double remaining = toAim.length();
        int ticksLeft = data.leapTicks();
        if (ticksLeft <= 0 || remaining <= ARRIVE_EPSILON) {
            // Done: either the last step landed on the mark, or something got in the way and the ticks ran
            // out. This is the tick after that step, so taking the speed away now does not undo it; a little
            // of it is kept so the landing still slides.
            Entity mark = data.isLeapChasing() ? player.level().getEntity(data.leapTargetId()) : null;
            data.stopLeap();
            Vec3 motion = player.getDeltaMovement();
            Vec3 settled;
            if (mark instanceof LivingEntity living && !living.onGround()) {
                // Arriving at a body in the air: carry on exactly as it is moving, so the two of them fall side
                // by side rather than the caster sailing on past it on whatever climb the approach left them
                // with, or drifting away from it on the speed they arrived at. Both are under Slow Falling from
                // here, so from this tick on they descend together.
                settled = living.getDeltaMovement();
            } else {
                // Onto a point in space: keep the slide and the fall, which is what carries the leap onto the
                // ledge it was aimed at.
                settled = new Vec3(motion.x * SETTLE_TAKE, motion.y, motion.z * SETTLE_TAKE);
            }
            player.setDeltaMovement(settled);
            player.hurtMarked = true;
            return;
        }
        // Air resistance: the flight bleeds speed as it goes rather than holding one flat speed. The step
        // never drops below what is still needed to make the mark, which is what keeps an aimed leap landing
        // where it was aimed; the budget was planned on the average of the profile.
        double planned = data.leapTravel();
        double flown = planned <= 0.0 ? 0.0 : Mth.clamp(1.0 - remaining / planned, 0.0, 1.0);
        double speed = Math.max(remaining / Math.max(1, ticksLeft),
                dashSpeed(planned) * (1.0 - AIR_DRAG * flown));
        // Gravity: the vertical is solved for an arc that passes through the mark over the time the leap was
        // planned for, scaled by how much of it is left. Deliberately not the tick budget — that one is
        // stretched by slow-downs, and hanging the arc off it let a leap that kept slowing down keep adding
        // lift to itself until it flew away upward.
        double arcTicks = Math.max(1.0, plannedTicks(planned)
                * (planned <= 0.0 ? 1.0 : Mth.clamp(remaining / planned, 0.0, 1.0)));
        double rise = (toAim.y + 0.5 * GRAVITY * arcTicks * arcTicks) / arcTicks;
        double flat = Math.sqrt(toAim.x * toAim.x + toAim.z * toAim.z);
        Vec3 flatDirection = flat < 1.0E-4 ? Vec3.ZERO : new Vec3(toAim.x, 0.0, toAim.z).scale(1.0 / flat);
        Vec3 motion;
        if (data.isLeapChasing()) {
            // A chase is an interception, not a crossing: straight at the mark, whose aim is being moved under
            // them every tick. The arc solve below is for a fixed point a fixed distance away, and against a
            // mark that is falling it answers by lifting the caster up and over them — which is how a chase used
            // to end with the caster flying above the body they were trying to reach.
            motion = toAim.scale(speed / remaining);
        } else {
            motion = flatDirection.scale(Math.min(flat, speed)).add(0.0, rise, 0.0);
        }

        // A block in the way stops the leap outright — nothing is torn out of the way. Only bodies can be
        // flown through, and only by a dash that has the power for them.
        double power = leapPower(player, speed);
        Impact bodies = plough(player, data, motion, speed, power);
        if (bodies.stopped() || blocked(player, motion)) {
            endAgainst(player, data, motion);
            return;
        }
        double drag = bodies.drag();
        int nextTicks = ticksLeft - 1;
        if (drag > 0.0) {
            // Slowed by the breakthrough: what is left of the leap is spread over more ticks. The stretch is
            // bounded by the slowest speed the dash may fly at, so grinding through a wall cannot make the
            // leap stretch its own flight without end.
            double keep = BREAK_KEEP_LIGHT - (BREAK_KEEP_LIGHT - BREAK_KEEP_HEAVY) * Math.min(1.0, drag);
            nextTicks = Math.max(1, (int) Math.ceil(ticksLeft / keep));
            nextTicks = Math.min(nextTicks, Math.max(1, (int) Math.ceil(remaining / MIN_DASH_SPEED)));
        }
        player.setDeltaMovement(motion);
        // The dash carries the player itself, so the fall it would otherwise count is not a fall.
        player.fallDistance = 0.0F;
        player.hurtMarked = true;
        // A leap still flying when its launch clip runs out takes the held flight pose instead: that clip is
        // short and its tail is the landing, which is what used to put the player back on their feet mid-air.
        data.tickLeapElapsed();
        if (data.leapElapsed() >= FLY_HOLD_FROM_TICKS
                && (data.leapElapsed() - FLY_HOLD_FROM_TICKS) % FLY_HOLD_REPLAY_TICKS == 0) {
            CombatAnimations.playSkill(player, toAim.y > flat * 0.7 ? "leap_up_fly" : "leap_fly");
        }
        data.setLeapTicks(nextTicks);
    }

    /** Clears the leap when something else takes over, whatever half of it was running. */
    public static void stop(TorikoData data) {
        data.stopLeap();
    }

    /**
     * Tears the launch point up when the player is strong enough to do it: the same burst a blow leaves on
     * terrain, centred on the block the leap pushed off from, so both read the same way. UpheavalEntity.burst
     * decides how far the player's strength and their launch speed carry it, whether it happens at all, and —
     * under the same impulse rule as any other blow — which of the blocks it tears up are broken rather than only
     * heaved. The blow is weighed on the speed the dash leaves with, so a long leap tears up more ground than a
     * short hop and a strong pair of legs breaks what the ground is made of.
     */
    private static void liftGround(ServerPlayer player, TorikoData data) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos under = player.blockPosition().below();
        // Straight down, whatever way the caster was looking: what a leap tears up is the ground under the feet it
        // pushed off from, and the direction a burst is given is the line the blow went into the terrain along — it
        // is what decides whether the crater lies flat on the floor or stands on end on a wall.
        Vec3 down = new Vec3(0.0, -1.0, 0.0);
        double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        // What the ground is asked to take is the push-off: the speed the dash leaves with, which is the whole of
        // this movement over the ticks it takes to fly it. Reading the player's own motion here instead left a leap
        // started from a standstill with nothing behind it — the ground came up and nothing under it gave way.
        double speed = data.leapAim().subtract(player.position()).length() / Math.max(1, data.leapTicks());
        if (!UpheavalEntity.burst(level, player, under, down, damage, speed,
                Hurt.impulse(down.scale(speed), damage, Hurt.FIST_AREA))) {
            return;
        }
        ShockwaveRingEntity ring = new ShockwaveRingEntity(level);
        ring.moveTo(player.getX(), player.getY() + 0.1, player.getZ(), player.getYRot(), 0.0F);
        level.addFreshEntity(ring);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 0.6F);
    }

    /** What one tick of flying into things added up to: how much it braked the dash, and whether it ended it. */
    private record Impact(double drag, boolean stopped) {
        private static final Impact NONE = new Impact(0.0, false);
        private static final Impact STOP = new Impact(0.0, true);
    }

    /** Power behind the dash: how fast it is going, plus what the player's strength adds to it. */
    private static double leapPower(ServerPlayer player, double speed) {
        return speed * POWER_PER_SPEED
                + player.getAttributeValue(Attributes.ATTACK_DAMAGE) * POWER_PER_DAMAGE;
    }

    /**
     * Whether the dash is about to fly into a block. Any block with a collision shape in the swept box counts,
     * except the ones the player is already standing in or on — the floor a leap pushes off from is not
     * something it runs into.
     */
    private static boolean blocked(ServerPlayer player, Vec3 motion) {
        ServerLevel level = (ServerLevel) player.level();
        AABB swept = player.getBoundingBox().expandTowards(motion);
        AABB standing = player.getBoundingBox();
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(swept.minX), Mth.floor(swept.minY), Mth.floor(swept.minZ),
                Mth.floor(swept.maxX), Mth.floor(swept.maxY), Mth.floor(swept.maxZ))) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty() || standing.intersects(shape.bounds().move(pos))) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * Everything the dash runs through this tick. Anything living in the way takes a hit — damage scaled by
     * the speed it was hit at, and a shove along the dash — and one tough enough to be worth more than the
     * dash's power stops the leap on the spot.
     *
     * <p>What counts as tough is the entity's armour: armour points, armour toughness, and a tenth of its
     * maximum health, so a bare mob is flown straight through and a plated one is not.
     *
     * <p>An air chase's mark is the one body a dash is not allowed to run into (see {@link #chaseAirborne}).
     */
    private static Impact plough(ServerPlayer player, TorikoData data, Vec3 motion, double speed, double power) {
        AABB swept = player.getBoundingBox()
                .expandTowards(motion.scale(Math.min(IMPACT_REACH, speed)))
                .inflate(0.3);
        SkillContext ctx = null;
        double drag = 0.0;
        boolean stopped = false;
        // A chase is a pursuit, not a charge: the body it is chasing is passed through untouched, or the dash
        // would knock away the very thing it flew up to fall alongside — and a tough one would stop it dead.
        int markId = data.isLeapChasing() ? data.leapTargetId() : -1;
        for (LivingEntity victim : player.level().getEntitiesOfClass(LivingEntity.class, swept,
                candidate -> candidate != player && candidate.getId() != markId && candidate.isPickable())) {
            if (!victim.isAlive() || victim.invulnerableTime > 0) {
                // Already hit on this pass; its own invulnerability keeps one leap to one hit each.
                continue;
            }
            double resistance = victim.getAttributeValue(Attributes.ARMOR)
                    + victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS)
                    + victim.getAttributeValue(Attributes.MAX_HEALTH) * 0.1;
            if (power < resistance) {
                stopped = true;
                continue;
            }
            if (ctx == null) {
                ctx = new SkillContext(player, (ServerLevel) player.level(), data);
            }
            float damage = (float) (speed * IMPACT_DAMAGE_PER_SPEED
                    + player.getAttributeValue(Attributes.ATTACK_DAMAGE) * IMPACT_DAMAGE_PER_DAMAGE);
            if (Hurt.apply(ctx, victim, ModDamageTypes.NAIL, ctx.damage(damage))) {
                Hurt.launch(victim, motion, Math.min(IMPACT_KNOCKBACK_CAP, IMPACT_KNOCKBACK_PER_SPEED * speed + 0.3));
                Hurt.playSound(ctx, player.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.9F);
            }
            drag = Math.max(drag, resistance / power);
        }
        if (stopped) {
            return Impact.STOP;
        }
        return drag > 0.0 ? new Impact(drag, false) : Impact.NONE;
    }

    /**
     * Ends the leap against something it could not beat. The step is still taken — vanilla collision decides
     * where the player really comes to rest — and the dash is over from here.
     */
    private static void endAgainst(ServerPlayer player, TorikoData data, Vec3 motion) {
        player.setDeltaMovement(motion);
        player.fallDistance = 0.0F;
        player.hurtMarked = true;
        data.stopLeap();
    }

    /**
     * Whether the player is against a surface to push off: the floor, a wall, or a ceiling all count, as
     * does holding on to a ladder or vine. The test is just whether a block's collision shape touches the
     * player, so clinging to a wall or hanging under an overhang winds the leap up like standing does.
     */
    private static boolean supported(ServerPlayer player) {
        if (player.onGround() || player.onClimbable()) {
            return true;
        }
        // Block collisions only count shapes that overlap, and standing on a floor already has the box
        // resting exactly on it, so the box is grown a little to catch a surface being touched.
        AABB reach = player.getBoundingBox().inflate(SURFACE_REACH);
        return player.level().getBlockCollisions(player, reach).iterator().hasNext();
    }

    /**
     * Cruise speed for a leap of this reach, in blocks per tick. A leap that reaches further also flies
     * faster, so extra reach is felt as speed rather than as a longer ride.
     */
    private static double dashSpeed(double travel) {
        return CRUISE_SPEED + travel * SPEED_PER_BLOCK;
    }

    /** Ticks the dash is planned to take over this distance: its drag-slowed speed, times the distance. */
    private static double plannedTicks(double travel) {
        return travel / dashSpeed(travel) / (1.0 - AIR_DRAG / 2.0);
    }

    /**
     * Blocks a full charge reaches: what this player's strength and speed earn them, held back by however far
     * they have wound the leap dial down in the power settings GUI — which reaches 100%, the whole of what
     * those attributes earn, and no further. Only the far end moves: the tap of a jump stays at
     * {@link #MIN_DISTANCE} whatever the dial says.
     *
     * <p>Without the dial, so this is a plain distance in blocks and the power settings screen can show its leap
     * dial in those terms.
     */
    public static double maxDistance(ServerPlayer player) {
        double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return MAX_DISTANCE
                + Math.max(0.0, damage - BASE_ATTACK_DAMAGE) * BLOCKS_PER_DAMAGE
                + Math.max(0.0, speed - BASE_MOVEMENT_SPEED) * BLOCKS_PER_SPEED;
    }

    /**
     * The entity under the crosshair, or null. Only entities in plain sight count: one behind terrain is
     * passed over, so a leap at something through a wall just carries the player at the wall instead.
     */
    private static Entity aimedEntity(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(TARGET_RANGE));
        AABB sweep = player.getBoundingBox().expandTowards(look.scale(TARGET_RANGE)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, sweep,
                candidate -> candidate != player && candidate.isPickable() && !candidate.isSpectator(),
                TARGET_RANGE * TARGET_RANGE);
        if (hit == null) {
            return null;
        }
        BlockHitResult block = player.level().clip(new ClipContext(eye, hit.getLocation(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        boolean blocked = block.getType() != HitResult.Type.MISS
                && block.getLocation().distanceToSqr(eye) < hit.getLocation().distanceToSqr(eye);
        return blocked ? null : hit.getEntity();
    }

    /** Where a leap aimed at this entity ends: level with its middle, just short of its hitbox. */
    private static Vec3 stopPoint(ServerPlayer player, Entity target) {
        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 flat = new Vec3(center.x - player.getX(), 0.0, center.z - player.getZ());
        if (flat.lengthSqr() < 1.0E-4) {
            return center;
        }
        Vec3 back = flat.normalize().scale(target.getBbWidth() * 0.5 + ENTITY_STAND_OFF);
        return new Vec3(center.x - back.x, center.y, center.z - back.z);
    }
}
