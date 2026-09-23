package org.example.hanjinwoo.gourmet2.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.example.hanjinwoo.gourmet2.compat.CombatAnimations;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.entity.ShockwaveRingEntity;
import org.example.hanjinwoo.gourmet2.registry.ModAttachments;
import org.example.hanjinwoo.gourmet2.registry.ModDamageTypes;

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

    /**
     * "힘" past {@link #GROUND_LIFT_MIN_DAMAGE} tears the launch point up: the surface around it is thrown
     * upward and falls back into place, and both the area and the height grow with how far past the
     * threshold the player's attack damage is. Nothing is destroyed — the blocks land back on the column
     * they came from.
     */
    private static final double GROUND_LIFT_MIN_DAMAGE = 6.0;
    private static final double GROUND_LIFT_BASE_RADIUS = 1.6;
    private static final double GROUND_LIFT_RADIUS_PER_DAMAGE = 0.22;
    private static final double GROUND_LIFT_MAX_RADIUS = 5.0;
    private static final double GROUND_LIFT_BASE_RISE = 1.2;
    private static final double GROUND_LIFT_RISE_PER_DAMAGE = 0.28;
    private static final double GROUND_LIFT_MAX_RISE = 4.5;
    /** Hard cap on the blocks one launch throws, so no amount of strength can stall the server. */
    private static final int GROUND_LIFT_MAX_BLOCKS = 24;

    /** How far ahead the crosshair can pick an entity to fly at. */
    private static final double TARGET_RANGE = 24.0;
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
        if (data.isLeapCharging() || data.isLeaping() || !supported(player)) {
            return;
        }
        data.setLeapCharging(true);
        data.setGuarding(false);
        CombatAnimations.playSkill(player, "leap_charge");
    }

    /** The leap key came up: launch, however far the wind-up earned. */
    public static void release(ServerPlayer player) {
        TorikoData data = ModAttachments.of(player);
        if (!data.isLeapCharging()) {
            return;
        }
        int chargeTicks = data.leapChargeTicks();
        data.setLeapCharging(false);

        float fraction = Math.min(1.0F, chargeTicks / (float) MAX_CHARGE_TICKS);
        double reach = Charge.lerp((float) MIN_DISTANCE, (float) maxDistance(player), fraction);

        Entity target = aimedEntity(player);
        Vec3 aim = target == null
                ? player.getEyePosition().add(player.getLookAngle().scale(reach))
                : stopPoint(player, target);
        Vec3 toAim = aim.subtract(player.position());
        double travel = Math.min(reach, toAim.length());
        if (travel < 0.5) {
            return;
        }

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
        liftGround(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.9F, 1.4F);
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
                data.setLeapTargetId(-1);
            }
        }
        Vec3 toAim = aim.subtract(player.position());
        double remaining = toAim.length();
        int ticksLeft = data.leapTicks();
        if (ticksLeft <= 0 || remaining <= ARRIVE_EPSILON) {
            // Done: either the last step landed on the mark, or something got in the way and the ticks ran
            // out. This is the tick after that step, so taking the speed away now does not undo it; a little
            // of it is kept so the landing still slides.
            data.stopLeap();
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x * SETTLE_TAKE, motion.y, motion.z * SETTLE_TAKE);
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
        Vec3 motion = flatDirection.scale(Math.min(flat, speed)).add(0.0, rise, 0.0);

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
     * Tears the launch point up when the player is strong enough to do it. The top block of each column
     * around them is thrown into the air and falls back onto the column it came from, so the ground reads
     * as being lifted without anything actually being destroyed. Both the area and the height grow with how
     * far past {@link #GROUND_LIFT_MIN_DAMAGE} the player's attack damage is, and the number of blocks is
     * capped so no amount of strength can stall the server.
     */
    private static void liftGround(ServerPlayer player) {
        double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (damage < GROUND_LIFT_MIN_DAMAGE) {
            return;
        }
        double over = damage - GROUND_LIFT_MIN_DAMAGE;
        double radius = Math.min(GROUND_LIFT_MAX_RADIUS,
                GROUND_LIFT_BASE_RADIUS + over * GROUND_LIFT_RADIUS_PER_DAMAGE);
        double rise = Math.min(GROUND_LIFT_MAX_RISE,
                GROUND_LIFT_BASE_RISE + over * GROUND_LIFT_RISE_PER_DAMAGE);
        // A column every couple of blocks: a sparse grid of ground jumping reads better than a solid slab
        // and keeps the entity count down, and it is the same picture whatever the strength.
        int stride = Math.max(1, Mth.ceil(radius / 2.0));
        int reach = Mth.ceil(radius);
        int centerX = Mth.floor(player.getX());
        int centerZ = Mth.floor(player.getZ());
        ServerLevel level = (ServerLevel) player.level();
        // A falling block is pulled down 0.04 a tick, so this is the speed that carries it `rise` blocks up.
        float upward = (float) Math.sqrt(2.0 * 0.04 * rise);

        int thrown = 0;
        for (int dx = -reach; dx <= reach && thrown < GROUND_LIFT_MAX_BLOCKS; dx += stride) {
            for (int dz = -reach; dz <= reach && thrown < GROUND_LIFT_MAX_BLOCKS; dz += stride) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                int x = centerX + dx;
                int z = centerZ + dz;
                BlockPos surface = new BlockPos(x,
                        level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
                BlockState state = level.getBlockState(surface);
                if (state.isAir() || !state.getFluidState().isEmpty() || state.is(Blocks.BEDROCK)) {
                    continue;
                }
                FallingBlockEntity block = FallingBlockEntity.fall(level, surface, state);
                block.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.12, upward,
                        (level.random.nextDouble() - 0.5) * 0.12);
                block.hurtMarked = true;
                thrown++;
            }
        }
        if (thrown == 0) {
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
     */
    private static Impact plough(ServerPlayer player, TorikoData data, Vec3 motion, double speed, double power) {
        AABB swept = player.getBoundingBox()
                .expandTowards(motion.scale(Math.min(IMPACT_REACH, speed)))
                .inflate(0.3);
        SkillContext ctx = null;
        double drag = 0.0;
        boolean stopped = false;
        for (LivingEntity victim : player.level().getEntitiesOfClass(LivingEntity.class, swept,
                candidate -> candidate != player && candidate.isPickable())) {
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

    /** Blocks a full charge reaches with this player's attributes. */
    private static double maxDistance(ServerPlayer player) {
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
