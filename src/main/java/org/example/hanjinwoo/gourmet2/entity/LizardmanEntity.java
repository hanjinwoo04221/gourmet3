package org.example.hanjinwoo.gourmet2.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffectInstance;
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
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.registry.ModMobEffects;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Lizardman (modelCollection/red_nitro1): a hostile mob built around a claws-and-tail fighting style of its
 * own. It grows a Gourmet Cell level at spawn like the rest of this mod's ecosystem does for the player — a body
 * that is a little more (or less) dangerous than the last one, and worth more Capture Level for it (see
 * {@link org.example.hanjinwoo.gourmet2.skill.CaptureLevel}) — but it is otherwise a plain hostile creature: its
 * own stats, its own AI, and its own animated attacks, wired up through GeckoLib rather than through the player's
 * skill/combat-mode systems.
 */
public class LizardmanEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> CELL_LEVEL =
            SynchedEntityData.defineId(LizardmanEntity.class, EntityDataSerializers.INT);
    private static final String KEY_CELL_LEVEL = "CellLevel";

    /** How much of a spread the Gourmet Cell level draws from at spawn, and what each level is worth on top of the base stats. */
    private static final int CELL_LEVEL_MIN = 1;
    private static final int CELL_LEVEL_MAX = 6;
    private static final double HEALTH_PER_LEVEL = 4.0;
    private static final double ATTACK_PER_LEVEL = 0.6;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.lizardman.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.lizardman.walk");
    private static final RawAnimation CLAW_RIGHT = RawAnimation.begin().thenPlay("animation.lizardman.claw_right");
    private static final RawAnimation CLAW_LEFT = RawAnimation.begin().thenPlay("animation.lizardman.claw_left");
    private static final RawAnimation TAIL_SLAM = RawAnimation.begin().thenPlay("animation.lizardman.tail_slam");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public LizardmanEntity(EntityType<? extends LizardmanEntity> type, Level level) {
        super(type, level);
    }

    /** Stats: tougher and harder-hitting than a zombie, but slower to close in — the claws are what it counts on. */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 34.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.6)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.ARMOR_TOUGHNESS, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.1)
                .add(Attributes.FOLLOW_RANGE, 28.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new LizardmanCombatGoal(this, 1.15, false));
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
    }

    /** This creature's own Gourmet Cell level, drawn once at spawn. Never changes afterwards. */
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
        apply(getAttribute(Attributes.MAX_HEALTH), Gourmet2.id("lizardman_cell_health"),
                level * HEALTH_PER_LEVEL, AttributeModifier.Operation.ADD_VALUE);
        apply(getAttribute(Attributes.ATTACK_DAMAGE), Gourmet2.id("lizardman_cell_attack"),
                level * ATTACK_PER_LEVEL, AttributeModifier.Operation.ADD_VALUE);
        setHealth(getMaxHealth());
    }

    private static void apply(@Nullable AttributeInstance instance,
            net.minecraft.resources.ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (amount > 0.0) {
            instance.addPermanentModifier(new AttributeModifier(id, amount, operation));
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

    /** Claws bleed; the tail slam does not, but hits harder and shoves the target back. */
    void onClawHit(LivingEntity target) {
        target.addEffect(new MobEffectInstance(ModMobEffects.bleeding(), 100, 0, false, true, true));
    }

    void onTailSlamHit(LivingEntity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        target.knockback(0.9, -dx, -dz);
    }

    // -------------------------------------------------------------------- GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 4, state ->
                state.setAndContinue(state.isMoving() ? WALK : IDLE)));
        controllers.add(new AnimationController<>(this, "attack", 0, state -> PlayState.STOP)
                .triggerableAnim("claw_right", CLAW_RIGHT)
                .triggerableAnim("claw_left", CLAW_LEFT)
                .triggerableAnim("tail_slam", TAIL_SLAM)
                .receiveTriggeredAnimations());
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
