package org.example.hanjinwoo.gourmet2.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.entity.AppetiteDemonEntity;
import org.example.hanjinwoo.gourmet2.entity.FlyingForkEntity;
import org.example.hanjinwoo.gourmet2.entity.FlyingKnifeEntity;
import org.example.hanjinwoo.gourmet2.entity.KiAuraEntity;
import org.example.hanjinwoo.gourmet2.entity.LegKnifeSlashEntity;
import org.example.hanjinwoo.gourmet2.entity.NailShotEntity;
import org.example.hanjinwoo.gourmet2.entity.ShockwaveRingEntity;
import org.example.hanjinwoo.gourmet2.entity.UpheavalEntity;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTER =
            DeferredRegister.create(Registries.ENTITY_TYPE, Gourmet2.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<FlyingForkEntity>> FLYING_FORK =
            REGISTER.register("flying_fork", () -> EntityType.Builder
                    .<FlyingForkEntity>of(FlyingForkEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F)
                    .clientTrackingRange(6)
                    // Matches vanilla's own thrown projectiles (Arrow=20, Egg/Pearl/Fireball=10):
                    // client and server both simulate the same simple straight-line motion each
                    // tick, so re-syncing every single tick just fights that local simulation with
                    // laggy corrections - that's what read as side-to-side wobble in testing.
                    .updateInterval(10)
                    .noSummon()
                    .build("flying_fork"));

    /** One round of the Nail Gun: small, quick, and carrying the gun's own effect along with it. */
    public static final DeferredHolder<EntityType<?>, EntityType<NailShotEntity>> NAIL_SHOT =
            REGISTER.register("nail_shot", () -> EntityType.Builder
                    .<NailShotEntity>of(NailShotEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F)
                    .clientTrackingRange(6)
                    .updateInterval(5)
                    .noSummon()
                    .build("nail_shot"));

    public static final DeferredHolder<EntityType<?>, EntityType<FlyingKnifeEntity>> FLYING_KNIFE =
            REGISTER.register("flying_knife", () -> EntityType.Builder
                    .<FlyingKnifeEntity>of(FlyingKnifeEntity::new, MobCategory.MISC)
                    .sized(1.8F, 0.9F)
                    .clientTrackingRange(6)
                    .updateInterval(10)
                    .noSummon()
                    .build("flying_knife"));

    public static final DeferredHolder<EntityType<?>, EntityType<LegKnifeSlashEntity>> LEG_KNIFE_SLASH =
            REGISTER.register("leg_knife_slash", () -> EntityType.Builder
                    .<LegKnifeSlashEntity>of(LegKnifeSlashEntity::new, MobCategory.MISC)
                    .sized(1.6F, 2.0F)
                    .clientTrackingRange(6)
                    .updateInterval(10)
                    .noSummon()
                    .build("leg_knife_slash"));

    public static final DeferredHolder<EntityType<?>, EntityType<AppetiteDemonEntity>> APPETITE_DEMON =
            REGISTER.register("appetite_demon", () -> EntityType.Builder
                    .<AppetiteDemonEntity>of(AppetiteDemonEntity::new, MobCategory.MISC)
                    .sized(1.0F, 2.5F)
                    .clientTrackingRange(8)
                    .updateInterval(4)
                    .noSummon()
                    .fireImmune()
                    .build("appetite_demon"));

    public static final DeferredHolder<EntityType<?>, EntityType<KiAuraEntity>> KI_AURA =
            REGISTER.register("ki_aura", () -> EntityType.Builder
                    .<KiAuraEntity>of(KiAuraEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(8)
                    .updateInterval(4)
                    .noSummon()
                    .fireImmune()
                    .build("ki_aura"));

    public static final DeferredHolder<EntityType<?>, EntityType<ShockwaveRingEntity>> SHOCKWAVE_RING =
            REGISTER.register("shockwave_ring", () -> EntityType.Builder
                    .<ShockwaveRingEntity>of(ShockwaveRingEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.2F)
                    .clientTrackingRange(8)
                    .updateInterval(4)
                    .noSummon()
                    .fireImmune()
                    .build("shockwave_ring"));

    /** The invisible owner of a crater of block displays; it renders nothing itself. */
    public static final DeferredHolder<EntityType<?>, EntityType<UpheavalEntity>> UPHEAVAL =
            REGISTER.register("upheaval", () -> EntityType.Builder
                    .<UpheavalEntity>of(UpheavalEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(8)
                    .updateInterval(4)
                    .noSummon()
                    .fireImmune()
                    .build("upheaval"));

    public static final DeferredHolder<EntityType<?>, EntityType<org.example.hanjinwoo.gourmet2.entity.ChopsticksEntity>> CHOPSTICKS =
            REGISTER.register("chopsticks", () -> EntityType.Builder
                    .<org.example.hanjinwoo.gourmet2.entity.ChopsticksEntity>of(
                            org.example.hanjinwoo.gourmet2.entity.ChopsticksEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .noSummon()
                    .fireImmune()
                    .build("chopsticks"));

    public static final DeferredHolder<EntityType<?>, EntityType<org.example.hanjinwoo.gourmet2.entity.ThrownChopstickEntity>> THROWN_CHOPSTICK =
            REGISTER.register("thrown_chopstick", () -> EntityType.Builder
                    .<org.example.hanjinwoo.gourmet2.entity.ThrownChopstickEntity>of(
                            org.example.hanjinwoo.gourmet2.entity.ThrownChopstickEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F)
                    .clientTrackingRange(24)
                    .updateInterval(2)
                    .noSummon()
                    .build("thrown_chopstick"));

    public static final DeferredHolder<EntityType<?>, EntityType<org.example.hanjinwoo.gourmet2.entity.ChopstickGhostEntity>> CHOPSTICK_GHOST =
            REGISTER.register("chopstick_ghost", () -> EntityType.Builder
                    .<org.example.hanjinwoo.gourmet2.entity.ChopstickGhostEntity>of(
                            org.example.hanjinwoo.gourmet2.entity.ChopstickGhostEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .noSummon()
                    .fireImmune()
                    .build("chopstick_ghost"));

    public static final DeferredHolder<EntityType<?>, EntityType<org.example.hanjinwoo.gourmet2.entity.MinorityWorldEntity>> MINORITY_WORLD =
            REGISTER.register("minority_world", () -> EntityType.Builder
                    .<org.example.hanjinwoo.gourmet2.entity.MinorityWorldEntity>of(
                            org.example.hanjinwoo.gourmet2.entity.MinorityWorldEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(16)
                    .updateInterval(20)
                    .noSummon()
                    .fireImmune()
                    .build("minority_world"));

    /** The Lizardman (modelCollection/red_nitro1): a real, hostile, AI-driven mob rather than a purely visual one. */
    public static final DeferredHolder<EntityType<?>, EntityType<org.example.hanjinwoo.gourmet2.entity.LizardmanEntity>> LIZARDMAN =
            REGISTER.register("lizardman", () -> EntityType.Builder
                    .<org.example.hanjinwoo.gourmet2.entity.LizardmanEntity>of(
                            org.example.hanjinwoo.gourmet2.entity.LizardmanEntity::new, MobCategory.MONSTER)
                    .sized(0.85F, 1.95F)
                    .clientTrackingRange(10)
                    .build("lizardman"));

    private ModEntities() {}
}
