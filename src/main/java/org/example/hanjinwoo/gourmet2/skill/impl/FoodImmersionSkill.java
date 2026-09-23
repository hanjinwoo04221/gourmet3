package org.example.hanjinwoo.gourmet2.skill.impl;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.fx.FxDispatch;
import org.example.hanjinwoo.gourmet2.fx.SkillFx;
import org.example.hanjinwoo.gourmet2.skill.ActiveSkill;
import org.example.hanjinwoo.gourmet2.skill.Hurt;
import org.example.hanjinwoo.gourmet2.skill.SkillBehavior;
import org.example.hanjinwoo.gourmet2.skill.SkillContext;
import org.example.hanjinwoo.gourmet2.skill.SkillType;
import org.jetbrains.annotations.Nullable;

/**
 * 食没 Food Immersion — the caster stands still and sinks into their own hunger until the Gourmet
 * Cells wake up. Refills Appetite outright and empowers every other technique, but the three
 * second channel leaves them wide open, and taking a hit or moving breaks it.
 */
public class FoodImmersionSkill implements SkillBehavior {
    public static final int CHANNEL_TICKS = 60;
    /** How long the Gourmet Cells stay awake afterwards. */
    public static final int AWAKENED_TICKS = 600;

    @Override
    public @Nullable Component checkUsable(SkillContext ctx) {
        if (!ctx.player().onGround()) {
            return Component.translatable("message." + Gourmet2.MODID + ".must_be_grounded");
        }
        if (ctx.data().isAwakened()) {
            return Component.translatable("message." + Gourmet2.MODID + ".already_awakened");
        }
        return null;
    }

    @Override
    public boolean activate(SkillContext ctx) {
        FxDispatch.on(ctx.level(), SkillFx.FOOD_IMMERSION_CHANNEL, ctx.player());
        Hurt.playSound(ctx, ctx.player().position(), SoundEvents.BEACON_ACTIVATE, 0.8F, 0.8F);
        return true;
    }

    @Override
    public ActiveSkill startActive(SkillContext ctx) {
        return new ActiveSkill(SkillType.FOOD_IMMERSION, CHANNEL_TICKS, true,
                ctx.lookDirection(), ctx.player().position());
    }

    @Override
    public void tick(SkillContext ctx, ActiveSkill active) {
        // The channel visual is shorter than the channel, so re-trigger it as it plays out.
        if (active.elapsed > 0 && active.elapsed % 20 == 0) {
            FxDispatch.on(ctx.level(), SkillFx.FOOD_IMMERSION_CHANNEL, ctx.player(), 0.9F);
            Hurt.playSound(ctx, ctx.player().position(), SoundEvents.NOTE_BLOCK_BASS.value(), 0.5F, 0.6F);
        }
    }

    @Override
    public void finish(SkillContext ctx, ActiveSkill active, boolean interrupted) {
        ServerPlayer player = ctx.player();
        if (interrupted) {
            player.displayClientMessage(
                    Component.translatable("message." + Gourmet2.MODID + ".channel_broken"), true);
            Hurt.playSound(ctx, player.position(), SoundEvents.FIRE_EXTINGUISH, 0.6F, 1.2F);
            return;
        }

        ctx.data().setAppetite(ctx.data().maxAppetite());
        ctx.data().setAwakenedTicks(AWAKENED_TICKS);

        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, AWAKENED_TICKS, 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, AWAKENED_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0, false, true, true));

        FxDispatch.on(ctx.level(), SkillFx.FOOD_IMMERSION_BURST, player, 1.3F);
        FxDispatch.on(ctx.level(), SkillFx.AWAKENED_AURA, player);
        Hurt.playSound(ctx, player.position(), SoundEvents.TOTEM_USE, 1.0F, 0.8F);
        player.displayClientMessage(
                Component.translatable("message." + Gourmet2.MODID + ".awakened"), true);
    }
}
