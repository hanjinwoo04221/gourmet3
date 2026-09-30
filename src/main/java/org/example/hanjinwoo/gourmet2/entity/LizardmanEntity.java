package org.example.hanjinwoo.gourmet2.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Lizardman (modelCollection/red_nitro2): a hostile test fighter that plays by the same rules as a player. It has
 * a Gourmet Cell level, and fights through a stand-in player (see {@link MobFighter}) so that its combat style, its
 * skills, its leap and chase, the terrain breaking and the damage and dial maths are the player's own systems rather
 * than copies of them. What is its own is the body (stats, GeckoLib model and animations) and the AI that decides which
 * inputs to press ({@link LizardmanFighterGoal}).
 */
public class LizardmanEntity extends Monster implements GeoEntity, ClipPlayer {
    private static final EntityDataAccessor<Integer> CELL_LEVEL =
            SynchedEntityData.defineId(LizardmanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CLIMBING =
            SynchedEntityData.defineId(LizardmanEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CEILING =
            SynchedEntityData.defineId(LizardmanEntity.class, EntityDataSerializers.BOOLEAN);
    private static final String KEY_CELL_LEVEL = "CellLevel";

    /** The spread a Gourmet Cell level is drawn from at spawn, and what each level adds to the base stats. */
    private static final int CELL_LEVEL_MIN = 10;
    private static final int CELL_LEVEL_MAX = 30;
    private static final double HEALTH_PER_LEVEL = 10.0;
    private static final double ATTACK_PER_LEVEL = 0.8;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.lizardman.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.lizardman.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.lizardman.run");
    private static final RawAnimation CLIMB = RawAnimation.begin().thenLoop("animation.lizardman.climb");
    private static final RawAnimation WALL_HOLD = RawAnimation.begin().thenLoop("animation.lizardman.wall_hold");
    private static final RawAnimation CEILING_HANG = RawAnimation.begin().thenLoop("animation.lizardman.ceiling_hang");
    private static final RawAnimation CEILING_MOVE = RawAnimation.begin().thenLoop("animation.lizardman.ceiling_move");

    /** Every one-shot move the attack controller can play, by the name the clip mapping below uses. */
    private static final String[] MOVES = {
            "claw_right", "claw_left", "bite", "tail_slam", "leap", "leap_charge", "launcher", "spike", "dodge", "guard",
            "skill_thrust", "skill_charge", "rise_right", "rise_left", "flurry_right", "flurry_left", "jump",
            "run_claw_right", "run_claw_left", "run_bite", "dash_spin", "rise_punch", "flash_step"};

    /** Beyond this distance from its target the Lizardman breaks into a run. */
    private static final double RUN_DISTANCE_SQR = 4.0 * 4.0;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final MobFighter fighter = new MobFighter(this);
    private boolean fighterReady;
    /** Ticks the brain still wants to go up the wall it is against (refreshed every tick it does). */
    private int climbWish;
    private int ceilingWish;

    public LizardmanEntity(EntityType<? extends LizardmanEntity> type, Level level) {
        super(type, level);
    }

    /** Stats: tougher and harder-hitting than a zombie, but slower to close in. */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 34.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.6)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.ARMOR_TOUGHNESS, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.1)
                .add(Attributes.FOLLOW_RANGE, 28.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new LizardmanFighterGoal(this, 1.5));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CELL_LEVEL, CELL_LEVEL_MIN);
        builder.define(CLIMBING, false);
        builder.define(CEILING, false);
    }

    /** The brain asks to climb the wall in front of it; it only does while it is actually pressed against one. */
    void wantClimb() {
        climbWish = 3;
    }

    /** As {@link #wantClimb}, but hanging from the ceiling overhead rather than a wall. */
    void wantCeiling() {
        climbWish = 3;
        ceilingWish = 3;
    }

    /** Hanging from a ceiling rather than a wall (only meaningful while {@link #isClimbing}). */
    public boolean isOnCeiling() {
        return entityData.get(CEILING);
    }

    public boolean isClimbing() {
        return entityData.get(CLIMBING);
    }

    /** This creature's own Gourmet Cell level, drawn once at spawn. */
    public int cellLevel() {
        return entityData.get(CELL_LEVEL);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        spawnGroupData = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        setCellLevel(CELL_LEVEL_MIN + random.nextInt(CELL_LEVEL_MAX - CELL_LEVEL_MIN + 1));
        return spawnGroupData;
    }

    private void setCellLevel(int level) {
        entityData.set(CELL_LEVEL, level);
        apply(getAttribute(Attributes.MAX_HEALTH), Gourmet2.id("lizardman_cell_health"), level * HEALTH_PER_LEVEL);
        apply(getAttribute(Attributes.ATTACK_DAMAGE), Gourmet2.id("lizardman_cell_attack"), level * ATTACK_PER_LEVEL);
        setHealth(getMaxHealth());
        // The stand-in reads the level again on the next tick.
        fighterReady = false;
    }

    private static void apply(@Nullable AttributeInstance instance, ResourceLocation id, double amount) {
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (amount > 0.0) {
            instance.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(KEY_CELL_LEVEL, cellLevel());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(KEY_CELL_LEVEL)) {
            setCellLevel(tag.getInt(KEY_CELL_LEVEL));
        }
    }

    // --------------------------------------------------------------------------- the player systems

    @Override
    public MobFighter fighter() {
        return fighter;
    }

    /** Runs the player systems for this body every tick, once it has moved. */
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        if (climbWish > 0) {
            climbWish--;
        }
        if (ceilingWish > 0) {
            ceilingWish--;
        }
        boolean roof = ceilingWish > 0 && org.example.hanjinwoo.gourmet2.skill.WallCling.ceilingAbove(this);
        boolean climbing = climbWish > 0
                && (roof || org.example.hanjinwoo.gourmet2.skill.WallCling.wallNormal(this) != null);
        if (climbing) {
            fallDistance = 0.0F;
        }
        if (roof != isOnCeiling()) {
            entityData.set(CEILING, roof);
        }
        if (climbing != isClimbing()) {
            entityData.set(CLIMBING, climbing);
        }
        if (!fighterReady) {
            fighter.setCellLevel(cellLevel());
            fighter.combatMode(true);
            fighter.style("lizardman");
            fighterReady = true;
        }
        fighter.tick();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            fighter.shutdown();
        }
        super.remove(reason);
    }

    /** Sometimes answers a hit the way a player would: with the dodge dash. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide() && fighterReady && source.getEntity() != null) {
            // Shrugs off the follow-up of a combo it is caught in, or slips away with the dodge dash.
            if (random.nextInt(3) == 0) {
                fighter.resist();
            } else if (random.nextInt(4) == 0) {
                // A flash step to either side, or back, away from whatever hit it.
                fighter.flashStep(random.nextBoolean() ? -0.6F : 0.0F, random.nextBoolean() ? 1.0F : -1.0F);
            } else if (random.nextInt(5) == 0) {
                fighter.dodge();
            }
        }
        return hurt;
    }

    /** Runs when the target is a few blocks off and it is on its feet: the vanilla sprint flag gives the speed boost. */
    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide()) {
            LivingEntity target = getTarget();
            boolean leaping = fighterReady && (fighter.data().isLeaping() || fighter.data().isLeapCharging());
            boolean chase = target != null && target.isAlive() && !leaping && onGround()
                    && distanceToSqr(target) > RUN_DISTANCE_SQR;
            if (chase != isSprinting()) {
                setSprinting(chase);
            }
        }
    }

    /**
     * The animation for a clip the player systems asked for. Combat-style clips are this style's own moves; the skill
     * and leap clips are shared by every player style, so each maps to the closest move this body has.
     */
    @Override
    public void playClip(String clip) {
        String move = switch (clip) {
            case "dash_spin_move" -> "dash_spin";
            case "flash_step" -> "flash_step";
            case "claw1" -> "claw_right";
            case "claw2" -> "claw_left";
            case "rise1" -> "rise_right";
            case "rise2" -> "rise_left";
            case "flurry1", "flurry3" -> "flurry_right";
            case "flurry2", "flurry4" -> "flurry_left";
            case "jump" -> "jump";
            case "bite" -> "bite";
            case "tail1", "tail2", "leg_knife" -> "tail_slam";
            case "launcher" -> "launcher";
            case "spike" -> "spike";
            case "dodge" -> "dodge";
            case "guard" -> "guard";
            case "knife" -> "claw_right";
            case "leap_charge" -> "leap_charge";
            case "leap", "leap_up" -> "leap";
            case "nail_punch", "fork", "nail_gun", "flying_fork_shot", "flying_knife_shot" -> "skill_thrust";
            case "nail_punch_charge", "nail_gun_charge", "leg_knife_charge", "flying_fork_charge",
                    "flying_knife_charge", "nail_gun_hold" -> "skill_charge";
            default -> null;
        };
        if (move != null) {
            move = inContext(move);
            if (move.equals("rise_punch") && !level().isClientSide()) {
                LizardmanTerrain.risePunch(this, false);
            }
            triggerAnim("attack", move);
        }
    }

    /**
     * The same attack looks different depending on what the body is doing when it is thrown: on the way up it is a
     * punch through the ceiling, on the way down a one-legged stomp, and at a run it is the run-matched version that
     * keeps the stride going (the tail sweep becomes the spinning dash).
     */
    private String inContext(String move) {
        boolean attack = switch (move) {
            case "claw_right", "claw_left", "rise_right", "rise_left", "flurry_right", "flurry_left", "bite",
                    "tail_slam", "launcher", "spike" -> true;
            default -> false;
        };
        if (!attack) {
            return move;
        }
        Vec3 motion = getDeltaMovement();
        if (!onGround()) {
            if (motion.y > 0.08) {
                return "rise_punch";
            }
            if (motion.y < -0.05) {
                return "spike";
            }
            return move;
        }
        if (motion.horizontalDistanceSqr() > 0.04) {
            return switch (move) {
                case "claw_right", "rise_right", "flurry_right" -> "run_claw_right";
                case "claw_left", "rise_left", "flurry_left" -> "run_claw_left";
                case "bite" -> "run_bite";
                case "tail_slam" -> "dash_spin";
                default -> move;
            };
        }
        return move;
    }

    // -------------------------------------------------------------------------------------- GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 4, state ->
                state.setAndContinue(isClimbing() ? hanging()
                : state.isMoving() ? (isSprinting() ? RUN : WALK) : IDLE)));
        AnimationController<LizardmanEntity> attack = new AnimationController<>(this, "attack", 0, state -> PlayState.STOP);
        for (String move : MOVES) {
            attack.triggerableAnim(move, RawAnimation.begin().thenPlay("animation.lizardman." + move));
        }
        controllers.add(attack);
    }

    /** The hold or the crawl, on the wall or from the ceiling, depending on whether the body is going anywhere. */
    private RawAnimation hanging() {
        boolean moving = distanceToSqr(xo, yo, zo) > 0.05 * 0.05;
        if (isOnCeiling()) {
            return moving ? CEILING_MOVE : CEILING_HANG;
        }
        return moving ? CLIMB : WALL_HOLD;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
