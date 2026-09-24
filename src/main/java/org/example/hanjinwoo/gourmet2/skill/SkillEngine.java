package org.example.hanjinwoo.gourmet2.skill;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.network.PacketDistributor;
import org.example.hanjinwoo.gourmet2.Config;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.fx.FxDispatch;
import org.example.hanjinwoo.gourmet2.fx.SkillFx;
import org.example.hanjinwoo.gourmet2.network.S2CSyncTorikoData;
import org.example.hanjinwoo.gourmet2.registry.ModAttachments;
import org.example.hanjinwoo.gourmet2.compat.CombatAnimations;
import org.example.hanjinwoo.gourmet2.skill.combat.CombatEngine;
import org.example.hanjinwoo.gourmet2.skill.impl.KiReleaseSkill;

/**
 * The server-side heart of the mod: validates skill activations, charges Appetite, runs cooldowns,
 * drives multi-tick skills and keeps the client HUD in sync.
 *
 * <p>Every entry point takes a {@link ServerPlayer} — nothing here ever runs on the client.
 */
public final class SkillEngine {
    /** How far a channelling player may drift before the channel breaks. */
    private static final double CHANNEL_BREAK_DISTANCE = 0.65;
    /** Cooldowns are shorter while the Gourmet Cells are awake. */
    private static final float AWAKENED_COOLDOWN_FACTOR = 0.7F;
    /** An interrupted skill only eats a fraction of its cooldown. */
    private static final float INTERRUPT_COOLDOWN_FACTOR = 0.25F;
    /** Ticks between re-triggering the awakened aura visual. */
    private static final int AURA_REFRESH_INTERVAL = 40;
    private static final int STARVE_PENALTY_INTERVAL = 40;
    private static final int STARVE_PENALTY_DURATION = 60;
    /** Idle sync cadence; changes are batched to at most this often. */
    private static final int SYNC_INTERVAL = 4;
    /**
     * Ticks between pressing an instant skill and it actually going off, so the effect lands on the
     * strike frame of its cast animation instead of before the animation has started.
     */
    private static final java.util.Map<SkillType, Integer> WINDUP_TICKS = java.util.Map.of(
            SkillType.NAIL_GUN, 7, SkillType.FORK, 6, SkillType.KNIFE, 7);


    private SkillEngine() {}

    // -------------------------------------------------------------- activation

    public static void select(ServerPlayer player, int index) {
        TorikoData data = ModAttachments.of(player);
        data.setSelected(index);
        sync(player, data);
    }

    /** Saves the skill selection screen's hotbar layout. Unknown skill ids become empty slots. */
    public static void setSkillSlots(ServerPlayer player, int[] slots) {
        TorikoData data = ModAttachments.of(player);
        for (int i = 0; i < TorikoData.SLOT_COUNT; i++) {
            data.setSkillSlot(i, i < slots.length ? slots[i] : -1);
        }
        sync(player, data);
    }

    public static void selectCombatStyle(ServerPlayer player, String id) {
        TorikoData data = ModAttachments.of(player);
        data.setCombatStyle(id);
        sync(player, data);
    }

    public static void setCombatMode(ServerPlayer player, boolean on) {
        TorikoData data = ModAttachments.of(player);
        data.setCombatMode(on);
        CombatAnimations.setCombatMode(player, on);
        sync(player, data);
    }

    /** Applies the settings GUI's requested power tuning, clamped server-side to the player's cap. */
    public static void updateSettings(ServerPlayer player, int nailCombo, int forkProjectiles,
                                      int knifeWaves, float flyingDamage, float flyingSize, int kiOutput,
                                      float attackDamage, float leapDistance) {
        TorikoData data = ModAttachments.of(player);
        data.setNailComboSetting(nailCombo);
        data.setForkProjectileSetting(forkProjectiles);
        data.setKnifeWaveSetting(knifeWaves);
        data.setFlyingDamageSetting(flyingDamage);
        data.setFlyingSizeSetting(flyingSize);
        data.setKiOutputSetting(kiOutput);
        data.setAttackDamageSetting(attackDamage);
        data.setLeapDistanceSetting(leapDistance);
        sync(player, data);
    }

    /** Runs the player's selected skill, or tells them why it didn't happen. */
    public static void use(ServerPlayer player) {
        TorikoData data = ModAttachments.of(player);
        SkillType skill = data.selectedSkill();
        SkillContext ctx = new SkillContext(player, (ServerLevel) player.level(), data);

        if (skill == SkillType.KI_RELEASE && data.isKiActive()) {
            SkillRegistry.get(skill).deactivateToggle(ctx);
            data.setKiActive(false);
            data.setCooldown(skill, cooldownFor(skill, data));
            sync(player, data);
            return;
        }
        if (isIntimidation(skill) && data.isIntimidationActive()) {
            int target = toggleStage(skill);
            if (data.intimidationStage() == target) {
                // Pressing the skill that is already on turns it off.
                SkillRegistry.get(skill).deactivateToggle(ctx);
                data.setIntimidationStage(0);
                data.setCooldown(skill, cooldownFor(skill, data));
            } else {
                // The other stage is running: swap straight over to this one.
                if (!data.canAfford(skill.appetiteCost())) {
                    fail(player, Component.translatable("message." + Gourmet2.MODID + ".not_enough_appetite",
                            skill.appetiteCost()));
                    return;
                }
                data.spend(skill.appetiteCost());
                data.setIntimidationStage(target);
                SkillRegistry.get(skill).enterStage(ctx, target);
                CombatAnimations.playSkill(player, skill.id());
            }
            sync(player, data);
            return;
        }
        if (data.isBusy()) {
            fail(player, message("busy"));
            return;
        }
        if (!data.isReady(skill)) {
            float seconds = data.cooldown(skill) / 20.0F;
            fail(player, Component.translatable("message." + Gourmet2.MODID + ".on_cooldown",
                    String.format("%.1f", seconds)));
            return;
        }
        SkillBehavior behavior = SkillRegistry.get(skill);
        // Skills with player-tuned power settings (see the settings GUI) cost more Appetite the
        // closer those settings are dialled to their cell-evolution-capped ceiling.
        int cost = skill.appetiteCost() + behavior.extraAppetiteCost(data);
        if (!data.canAfford(cost)) {
            fail(player, Component.translatable("message." + Gourmet2.MODID + ".not_enough_appetite", cost));
            return;
        }
        if (Config.skillsNeedEmptyHand
                && skill != SkillType.FOOD_IMMERSION
                && !player.getMainHandItem().isEmpty()) {
            fail(player, message("need_empty_hand"));
            return;
        }

        Component rejection = behavior.checkUsable(ctx);
        if (rejection != null) {
            fail(player, rejection);
            return;
        }

        switch (skill.inputMode()) {
            case CHARGE, AUTO -> {
                // The wind-up cue only; releaseCharge()/stopHeld() spend Appetite and set the cooldown
                // once the key comes back up (CHARGE), or per shot while it's down (AUTO).
                if (!behavior.activate(ctx)) {
                    fail(player, message("skill_failed"));
                    return;
                }
                data.startCharging(skill);
                if (skill.inputMode() == SkillType.InputMode.CHARGE) {
                    CombatAnimations.playSkill(player, skill.id() + "_charge");
                }
                sync(player, data);
            }
            case TOGGLE -> {
                if (!behavior.activate(ctx)) {
                    fail(player, message("skill_failed"));
                    return;
                }
                data.spend(cost);
                CombatAnimations.playSkill(player, skill.id());
                if (skill == SkillType.KI_RELEASE) {
                    data.setKiActive(true);
                } else {
                    data.setIntimidationStage(toggleStage(skill));
                }
                sync(player, data);
            }
            default -> {
                Integer windup = WINDUP_TICKS.get(skill);
                if (windup != null) {
                    data.startPending(skill, windup);
                    CombatAnimations.playSkill(player, skill.id());
                    sync(player, data);
                    return;
                }
                // A behaviour that returns false costs nothing, so a genuinely impossible cast is free.
                if (!behavior.activate(ctx)) {
                    fail(player, message("skill_failed"));
                    return;
                }
                data.spend(cost);
                data.setCooldown(skill, cooldownFor(skill, data));
                data.setActive(behavior.startActive(ctx));
                CombatAnimations.playSkill(player, skill.id());
                // A visible swing also drives the Appetite Demon's arm while Intimidation is on.
                player.swing(InteractionHand.MAIN_HAND, true);
                sync(player, data);
            }
        }
    }

    /** Releases a charging or held (AUTO) skill on key-up. No-op if nothing is active. */
    public static void release(ServerPlayer player) {
        TorikoData data = ModAttachments.of(player);
        SkillType skill = data.chargingSkill();
        if (skill == null) {
            return;
        }
        SkillContext ctx = new SkillContext(player, (ServerLevel) player.level(), data);
        int chargeTicks = data.chargeTicks();
        data.stopCharging();
        if (skill.inputMode() == SkillType.InputMode.AUTO) {
            SkillRegistry.get(skill).stopHeld(ctx);
        } else {
            SkillRegistry.get(skill).releaseCharge(ctx, chargeTicks);
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        sync(player, data);
    }

    /** Intimidation stage a toggle skill turns on: 1 = demon overhead, 2 = the caster transforms. */
    private static int toggleStage(SkillType skill) {
        return skill == SkillType.DEMON_FORM ? 2 : 1;
    }

    private static boolean isIntimidation(SkillType skill) {
        return skill == SkillType.INTIMIDATION || skill == SkillType.DEMON_FORM;
    }

    private static SkillType skillForStage(int stage) {
        return stage >= 2 ? SkillType.DEMON_FORM : SkillType.INTIMIDATION;
    }

    public static int cooldownFor(SkillType skill, TorikoData data) {
        int base = skill.cooldownTicks();
        return data.isAwakened() ? Math.round(base * AWAKENED_COOLDOWN_FACTOR) : base;
    }

    // ------------------------------------------------------------------- ticking

    /** Called once per tick for every server player. */
    public static void tick(ServerPlayer player) {
        TorikoData data = ModAttachments.of(player);

        data.tickCooldowns();
        tickAwakening(player, data);
        tickAppetite(player, data);
        tickActive(player, data);
        tickCharging(player, data);
        tickPending(player, data);
        tickIntimidation(player, data);
        tickKi(player, data);
        CombatEngine.tick(player, data);
        LeapEngine.tick(player, data);
        CombatAnimations.tick(player, data.isGuarding(), data.isCombatMode());

        // While a skill is running the HUD needs a live progress bar; otherwise batch the updates.
        if (data.isBusy() || data.isIntimidationActive() || data.isKiActive() || (data.isDirty() && player.tickCount % SYNC_INTERVAL == 0)) {
            sync(player, data);
        }
    }

    /** Fires a skill whose cast animation has reached its strike frame. */
    private static void tickPending(ServerPlayer player, TorikoData data) {
        SkillType skill = data.pendingSkill();
        if (skill == null || !data.tickPending()) {
            return;
        }
        data.clearPending();
        SkillBehavior behavior = SkillRegistry.get(skill);
        SkillContext ctx = new SkillContext(player, (ServerLevel) player.level(), data);
        int cost = skill.appetiteCost() + behavior.extraAppetiteCost(data);
        if (!data.canAfford(cost)) {
            fail(player, Component.translatable("message." + Gourmet2.MODID + ".not_enough_appetite", cost));
            return;
        }
        if (!behavior.activate(ctx)) {
            fail(player, message("skill_failed"));
            return;
        }
        data.spend(cost);
        data.setCooldown(skill, cooldownFor(skill, data));
        data.setActive(behavior.startActive(ctx));
        player.swing(InteractionHand.MAIN_HAND, true);
        sync(player, data);
    }

    private static void tickCharging(ServerPlayer player, TorikoData data) {
        if (!data.isCharging()) {
            return;
        }
        data.tickCharge();
        SkillType skill = data.chargingSkill();
        if (skill.inputMode() == SkillType.InputMode.AUTO) {
            SkillContext ctx = new SkillContext(player, (ServerLevel) player.level(), data);
            SkillRegistry.get(skill).tickHeld(ctx, data.chargeTicks());
        }
    }

    /** Ticks between Intimidation's aura re-pulse while its toggle is on. */
    private static final int INTIMIDATION_PULSE_INTERVAL = 20;

    private static void tickIntimidation(ServerPlayer player, TorikoData data) {
        if (!data.isIntimidationActive()) {
            return;
        }
        SkillType skill = skillForStage(data.intimidationStage());
        SkillContext ctx = new SkillContext(player, (ServerLevel) player.level(), data);
        SkillBehavior behavior = SkillRegistry.get(skill);

        if (player.tickCount % 20 == 0) {
            // The full transformation burns Appetite twice as fast as the looming demon.
            int drain = Config.intimidationDrainPerSecond * data.intimidationStage();
            if (drain > 0 && !data.spend(drain)) {
                // Ran out of fuel: the toggle switches itself off.
                behavior.deactivateToggle(ctx);
                data.setIntimidationStage(0);
                data.setCooldown(skill, cooldownFor(skill, data));
                return;
            }
        }
        if (player.tickCount % INTIMIDATION_PULSE_INTERVAL == 0) {
            behavior.tickToggle(ctx);
        }
    }

    private static void tickKi(ServerPlayer player, TorikoData data) {
        if (!data.isKiActive()) {
            return;
        }
        SkillContext ctx = new SkillContext(player, (ServerLevel) player.level(), data);
        SkillBehavior behavior = SkillRegistry.get(SkillType.KI_RELEASE);
        if (player.tickCount % 20 == 0) {
            int drain = KiReleaseSkill.drainPerSecond(data);
            if (drain > 0 && !data.spend(drain)) {
                behavior.deactivateToggle(ctx);
                data.setKiActive(false);
                data.setCooldown(SkillType.KI_RELEASE, cooldownFor(SkillType.KI_RELEASE, data));
                return;
            }
            behavior.tickToggle(ctx);
        }
    }

    private static void tickAwakening(ServerPlayer player, TorikoData data) {
        if (!data.isAwakened()) {
            return;
        }
        data.decayAwakened();
        if (data.awakenedTicks() % AURA_REFRESH_INTERVAL == 0) {
            FxDispatch.on((ServerLevel) player.level(), SkillFx.AWAKENED_AURA, player);
        }
        if (!data.isAwakened()) {
            // The buff just ran out: make sure the HUD stops showing it.
            data.markDirty();
        }
    }

    private static void tickAppetite(ServerPlayer player, TorikoData data) {
        if (!data.isBusy()) {
            int regen = data.tickRegen();
            if (regen > 0) {
                data.addAppetite(regen);
            }
        }
        if (Config.starvationPenalty && data.isStarving() && player.tickCount % STARVE_PENALTY_INTERVAL == 0) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.WEAKNESS, STARVE_PENALTY_DURATION, 1, false, false, true));
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, STARVE_PENALTY_DURATION, 0, false, false, true));
        }
    }

    private static void tickActive(ServerPlayer player, TorikoData data) {
        ActiveSkill active = data.active();
        if (active == null) {
            return;
        }
        SkillContext ctx = new SkillContext(player, (ServerLevel) player.level(), data);
        SkillBehavior behavior = SkillRegistry.get(active.type);

        if (shouldInterrupt(player, active)) {
            endActive(ctx, data, behavior, active, true);
            return;
        }

        behavior.tick(ctx, active);
        active.elapsed++;
        if (active.isFinished()) {
            endActive(ctx, data, behavior, active, false);
        }
    }

    private static boolean shouldInterrupt(ServerPlayer player, ActiveSkill active) {
        if (!player.isAlive() || player.isRemoved()) {
            return true;
        }
        if (!active.channelled) {
            return false;
        }
        return player.hurtTime > 0
                || !player.onGround()
                || player.position().distanceToSqr(active.anchor) > CHANNEL_BREAK_DISTANCE * CHANNEL_BREAK_DISTANCE;
    }

    private static void endActive(SkillContext ctx, TorikoData data, SkillBehavior behavior,
                                  ActiveSkill active, boolean interrupted) {
        behavior.finish(ctx, active, interrupted);
        data.setActive(null);
        if (interrupted) {
            data.setCooldown(active.type, Math.round(active.type.cooldownTicks() * INTERRUPT_COOLDOWN_FACTOR));
        }
        data.markDirty();
    }

    /**
     * Drops any running skill without letting it finish. Used when the player dies, changes
     * dimension or logs out, where {@link #tick} will never run again for that state.
     */
    public static void abortActive(ServerPlayer player) {
        TorikoData data = ModAttachments.of(player);
        SkillContext ctx = new SkillContext(player, (ServerLevel) player.level(), data);

        ActiveSkill active = data.active();
        if (active != null) {
            SkillRegistry.get(active.type).finish(ctx, active, true);
            data.setActive(null);
            data.markDirty();
        }
        if (data.isCharging()) {
            data.stopCharging();
        }
        LeapEngine.stop(data);
        data.clearPending();
        if (data.isKiActive()) {
            SkillRegistry.get(SkillType.KI_RELEASE).deactivateToggle(ctx);
            data.setKiActive(false);
        }
        if (data.isIntimidationActive()) {
            SkillRegistry.get(SkillType.INTIMIDATION).deactivateToggle(ctx);
            data.setIntimidationStage(0);
        }
    }

    // -------------------------------------------------------------------- utility

    /** Feeds the player's Appetite (and Gourmet Cell evolution) when they finish eating something. */
    public static void onFoodEaten(ServerPlayer player, int nutrition) {
        if (nutrition <= 0) {
            return;
        }
        TorikoData data = ModAttachments.of(player);
        data.addAppetite((int) Math.round(nutrition * Config.appetitePerNutrition));
        long xpGain = Math.round(nutrition * Config.cellXpPerNutrition);
        int levels = data.addCellXp(xpGain);
        if (levels > 0) {
            int level = data.cellLevel();
            player.displayClientMessage(Component.translatable(
                    "message." + Gourmet2.MODID + ".cell_level_up", level), true);
            // Evolving changes the body as well as the headroom: what it raised is worth saying, since the
            // player never chose it and would otherwise have to go and read their attribute screen to find it.
            Component grown = CellGrowth.grow(player, level - levels, level);
            if (grown != null) {
                player.displayClientMessage(grown, false);
            }
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        sync(player, data);
    }

    public static void sync(ServerPlayer player, TorikoData data) {
        PacketDistributor.sendToPlayer(player, S2CSyncTorikoData.of(player, data));
        data.clearDirty();
    }

    public static void sync(ServerPlayer player) {
        sync(player, ModAttachments.of(player));
    }

    private static Component message(String key) {
        return Component.translatable("message." + Gourmet2.MODID + "." + key);
    }

    private static void fail(ServerPlayer player, Component reason) {
        player.displayClientMessage(reason, true);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.4F, 1.2F);
    }
}
