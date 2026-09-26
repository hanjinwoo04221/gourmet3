package org.example.hanjinwoo.gourmet2.skill.impl;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.example.hanjinwoo.gourmet2.Config;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.registry.ModAttachments;
import org.example.hanjinwoo.gourmet2.skill.CaptureLevel;
import java.util.List;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.entity.AppetiteDemonEntity;
import org.example.hanjinwoo.gourmet2.registry.ModDamageTypes;
import org.example.hanjinwoo.gourmet2.skill.Hurt;
import org.example.hanjinwoo.gourmet2.skill.SkillBehavior;
import org.example.hanjinwoo.gourmet2.skill.SkillContext;
import org.example.hanjinwoo.gourmet2.skill.Targeting;

/**
 * 威圧・食欲の悪魔 Intimidation: Appetite Demon — the caster's hunger becomes visible and everything
 * nearby feels like prey. Press once to summon the demon and pressure everything in range; it keeps
 * looming and re-pressuring (draining Appetite the whole time, see {@code SkillEngine}) until either
 * the player presses the key again or Appetite runs dry.
 */
public class IntimidationSkill implements SkillBehavior {
    /** 1 = the demon looms overhead, 2 = the caster turns into the demon. */
    private final int stage;

    public IntimidationSkill(int stage) {
        this.stage = stage;
    }

    private static final double RADIUS = 14.0;
    private static final int DEBUFF_DURATION = 60;
    private static final float FEAR_DAMAGE = 3.0F;
    /** Anything this frail is rooted outright rather than merely slowed. */
    private static final float ROOT_HEALTH_THRESHOLD = 20.0F;
    private static final int ROOT_DURATION = 60;
    /** How far a frightened mob tries to bolt. */
    private static final double FLEE_DISTANCE = 12.0;
    private static final double FLEE_SPEED = 1.4;
    /** Weaker than the initial burst — this is the repeating aura pulse, not a one-off nuke. */
    private static final float PULSE_DAMAGE = 1.0F;

    /** Stage 1 buff, refreshed every pulse so it lapses shortly after the toggle ends. */
    private static final int BUFF_DURATION = 50;

    private static final ResourceLocation[] FORM_IDS = {
            Gourmet2.id("demon_form_health"), Gourmet2.id("demon_form_attack"), Gourmet2.id("demon_form_speed"),
            Gourmet2.id("demon_form_armor"), Gourmet2.id("demon_form_toughness"), Gourmet2.id("demon_form_knockback")};

    @Override
    public boolean activate(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        applyBody(ctx, stage);
        spawnDemon(ctx);
        Hurt.playSound(ctx, player.position(), SoundEvents.WARDEN_ROAR, 1.6F, 0.7F);
        Hurt.playSound(ctx, player.position(), SoundEvents.ENDER_DRAGON_GROWL, 0.9F, 0.6F);
        pulse(ctx, FEAR_DAMAGE * stage);
        return true;
    }

    @Override
    public void tickToggle(SkillContext ctx) {
        int current = ctx.data().intimidationStage();
        applyBody(ctx, current);
        pulse(ctx, PULSE_DAMAGE * current);
    }

    /**
     * Stage 1 raises the body with short potion effects; stage 2 with real attribute modifiers, which
     * are stripped again whenever the stage changes or the toggle ends. Capture Level reads base
     * attribute values, so neither affects it.
     */
    private void applyBody(SkillContext ctx, int forStage) {
        ServerPlayer player = ctx.player();
        if (forStage == 1) {
            clearForm(player);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, BUFF_DURATION, 1, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, BUFF_DURATION, 1, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, BUFF_DURATION, 1, false, false, true));
        } else if (forStage == 2) {
            addForm(player, Attributes.MAX_HEALTH, 0, 20.0, AttributeModifier.Operation.ADD_VALUE);
            addForm(player, Attributes.ATTACK_DAMAGE, 1, 8.0, AttributeModifier.Operation.ADD_VALUE);
            addForm(player, Attributes.MOVEMENT_SPEED, 2, 0.35, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            addForm(player, Attributes.ARMOR, 3, 8.0, AttributeModifier.Operation.ADD_VALUE);
            addForm(player, Attributes.ARMOR_TOUGHNESS, 4, 4.0, AttributeModifier.Operation.ADD_VALUE);
            addForm(player, Attributes.KNOCKBACK_RESISTANCE, 5, 0.6, AttributeModifier.Operation.ADD_VALUE);
        }
    }

    private static void addForm(ServerPlayer player, Holder<Attribute> attribute, int index, double amount,
                                AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null && !instance.hasModifier(FORM_IDS[index])) {
            instance.addTransientModifier(new AttributeModifier(FORM_IDS[index], amount, operation));
        }
    }

    private static void clearForm(ServerPlayer player) {
        for (Holder<Attribute> attribute : List.of(Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE,
                Attributes.MOVEMENT_SPEED, Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS,
                Attributes.KNOCKBACK_RESISTANCE)) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                for (ResourceLocation id : FORM_IDS) {
                    instance.removeModifier(id);
                }
            }
        }
    }

    @Override
    public void enterStage(SkillContext ctx, int stage) {
        ServerPlayer player = ctx.player();
        if (ctx.level().getEntity(ctx.data().intimidationDemonId()) instanceof AppetiteDemonEntity demon) {
            demon.setStage(stage);
        }
        clearForm(player);
        applyBody(ctx, stage);
        Hurt.playSound(ctx, player.position(), SoundEvents.WARDEN_ROAR, 1.8F, 0.5F);
        pulse(ctx, FEAR_DAMAGE * stage);
    }

    @Override
    public void deactivateToggle(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        clearForm(player);
        Entity demon = ctx.level().getEntity(ctx.data().intimidationDemonId());
        if (demon != null) {
            demon.discard();
        }
        ctx.data().setIntimidationDemonId(-1);
        Hurt.playSound(ctx, player.position(), SoundEvents.WARDEN_ROAR, 0.6F, 1.5F);
    }

    private void pulse(SkillContext ctx, float damage) {
        ServerPlayer player = ctx.player();
        Vec3 center = player.position().add(0.0, 1.0, 0.0);
        int casterLevel = CaptureLevel.of(player, ctx.data().cellLevel());
        for (LivingEntity victim : Targeting.inSphere(player, center, RADIUS)) {
            // Only clearly weaker targets feel it; anything of a similar or higher level shrugs it off.
            int victimLevel = CaptureLevel.of(victim, victim instanceof ServerPlayer other
                    ? ModAttachments.of(other).cellLevel() : CaptureLevel.cellLevelOf(victim));
            if (casterLevel - victimLevel <= Config.captureSimilarityWindow) {
                continue;
            }
            Hurt.apply(ctx, victim, ModDamageTypes.INTIMIDATION, ctx.damage(damage));
            victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DEBUFF_DURATION, 1, false, true, true));
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DEBUFF_DURATION, 1, false, true, true));

            if (victim instanceof Mob mob) {
                terrify(mob, player);
            } else if (victim instanceof ServerPlayer other) {
                other.addEffect(new MobEffectInstance(MobEffects.CONFUSION, DEBUFF_DURATION, 0, false, true, true));
            }
        }
    }

    /** Drops the mob's aggression and sends it running. */
    private void terrify(Mob mob, ServerPlayer source) {
        mob.setTarget(null);
        mob.setLastHurtByMob(null);
        mob.getNavigation().stop();

        Vec3 away = mob.position().subtract(source.position());
        if (away.lengthSqr() < 1.0E-4) {
            away = source.getLookAngle().scale(-1.0);
        }
        Vec3 destination = mob.position().add(away.normalize().scale(FLEE_DISTANCE));
        mob.getNavigation().moveTo(destination.x, destination.y, destination.z, FLEE_SPEED);

        if (mob.getMaxHealth() <= ROOT_HEALTH_THRESHOLD) {
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ROOT_DURATION, 6, false, false, true));
        }
    }

    private void spawnDemon(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        // It positions and animates itself off its owner from here on.
        AppetiteDemonEntity demon = new AppetiteDemonEntity(ctx.level());
        demon.setOwner(player);
        demon.setStage(stage);
        ctx.level().addFreshEntity(demon);
        ctx.data().setIntimidationDemonId(demon.getId());
    }
}
