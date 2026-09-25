package org.example.hanjinwoo.gourmet2.skill.impl;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.compat.CombatAnimations;
import org.example.hanjinwoo.gourmet2.data.TorikoData;
import org.example.hanjinwoo.gourmet2.entity.MinorityWorldEntity;
import org.example.hanjinwoo.gourmet2.network.S2CMinorityZone;
import org.example.hanjinwoo.gourmet2.skill.ChopsticksState;
import org.example.hanjinwoo.gourmet2.skill.Hurt;
import org.example.hanjinwoo.gourmet2.skill.MinorityWorld;
import org.example.hanjinwoo.gourmet2.skill.SkillBehavior;
import org.example.hanjinwoo.gourmet2.skill.SkillContext;
import org.example.hanjinwoo.gourmet2.skill.SkillEngine;
import org.example.hanjinwoo.gourmet2.skill.SkillType;

import java.util.Iterator;

/**
 * Minority World (Ichiryu) - raises a sphere around the spot the caster is standing on. Inside it the caster's
 * settings screen decides what happens to whoever is there (see {@link MinorityWorld}): inverted sight, inverted
 * damage and healing, inverted attack knockback, flight, and solid ground and liquid trading their natures. The
 * caster also chooses whether it reaches themselves, everybody else, or both.
 *
 * <p>Pressing the skill raises the zone and pressing it again lowers it; it burns Appetite every second for as long
 * as it stands, a little more for every ability switched on. This class owns the zone's life and the effects that
 * are applied to creatures from the server; the effects on damage, healing, knockback and the flight of players are
 * in {@code MinorityWorldEvents}, and what a player's own camera and body do is client side.
 */
public class MinorityWorldSkill implements SkillBehavior {
    private static final double BASE_RADIUS = 12.0;
    private static final int DRAIN_PERIOD = 20;
    private static final int BASE_DRAIN = 3;
    private static final int DRAIN_PER_EFFECT = 1;
    private static final int COOLDOWN = 40;
    private static final double SINK_SPEED = 0.05;

    @Override
    public boolean activate(SkillContext ctx) {
        return true;
    }

    /** The skill key: raises the zone, or lowers it if it is already up. */
    public static void press(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        TorikoData data = ctx.data();
        ChopsticksState state = data.minorityWorld;
        if (state.active) {
            deactivate(player, data);
            data.setCooldown(SkillType.MINORITY_WORLD, COOLDOWN);
            return;
        }
        if (!data.isReady(SkillType.MINORITY_WORLD)) {
            SkillEngine.fail(player, Component.translatable("message." + Gourmet2.MODID + ".on_cooldown",
                    String.format("%.1f", data.cooldown(SkillType.MINORITY_WORLD) / 20.0F)));
            return;
        }
        int cost = SkillType.MINORITY_WORLD.appetiteCost();
        if (!data.spend(cost)) {
            SkillEngine.fail(player, Component.translatable("message." + Gourmet2.MODID + ".not_enough_appetite", cost));
            return;
        }

        double radius = BASE_RADIUS * data.flyingSizeSetting();
        Vec3 center = player.position();
        MinorityWorldEntity shell = new MinorityWorldEntity(ctx.level());
        shell.setRadius((float) radius);
        shell.moveTo(center.x, center.y, center.z);
        ctx.level().addFreshEntity(shell);

        state.reset();
        state.active = true;
        state.mainId = shell.getId();
        state.ticksLeft = DRAIN_PERIOD;
        MinorityWorld.Zone zone = new MinorityWorld.Zone(player.getUUID(), ctx.level().dimension().location(),
                center, radius, data.minorityFlags());
        MinorityWorld.SERVER_ZONES.put(player.getUUID(), zone);
        PacketDistributor.sendToAllPlayers(S2CMinorityZone.started(zone));

        CombatAnimations.playSkill(player, "ki_release");
        Hurt.playSound(ctx, center, SoundEvents.BEACON_ACTIVATE, 1.0F, 0.6F);
        player.displayClientMessage(Component.translatable("message." + Gourmet2.MODID + ".minority_world_up"), true);
    }

    /** Lowers the zone, and gives back anything it was holding on creatures. */
    public static void deactivate(ServerPlayer player, TorikoData data) {
        ChopsticksState state = data.minorityWorld;
        if (!state.active) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        Entity shell = level.getEntity(state.mainId);
        if (shell != null) {
            shell.discard();
        }
        for (Iterator<Integer> it = state.affected.iterator(); it.hasNext(); ) {
            if (level.getEntity(it.next()) instanceof LivingEntity living && !(living instanceof Player)) {
                living.noPhysics = false;
            }
            it.remove();
        }
        MinorityWorld.SERVER_ZONES.remove(player.getUUID());
        PacketDistributor.sendToAllPlayers(S2CMinorityZone.ended(player.getUUID(), data.minorityFlags()));
        state.reset();
    }

    /** Saves the settings screen's choice and applies it to the zone if it is up. */
    public static void updateFlags(ServerPlayer player, int flags) {
        TorikoData data = org.example.hanjinwoo.gourmet2.registry.ModAttachments.of(player);
        data.setMinorityFlags(flags);
        MinorityWorld.Zone zone = MinorityWorld.SERVER_ZONES.get(player.getUUID());
        if (zone != null) {
            MinorityWorld.Zone updated = zone.withFlags(data.minorityFlags());
            MinorityWorld.SERVER_ZONES.put(player.getUUID(), updated);
            PacketDistributor.sendToAllPlayers(S2CMinorityZone.started(updated));
        } else {
            PacketDistributor.sendToPlayer(player, S2CMinorityZone.ended(player.getUUID(), data.minorityFlags()));
        }
    }

    /** Per-tick upkeep: Appetite drain, and what the zone does to creatures (players are handled in the events). */
    public static void tick(ServerPlayer player, TorikoData data) {
        ChopsticksState state = data.minorityWorld;
        if (!state.active) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        MinorityWorld.Zone zone = MinorityWorld.SERVER_ZONES.get(player.getUUID());
        if (zone == null || !player.isAlive() || !zone.dimension().equals(level.dimension().location())
                || !(level.getEntity(state.mainId) instanceof MinorityWorldEntity)) {
            deactivate(player, data);
            return;
        }
        if (--state.ticksLeft <= 0) {
            state.ticksLeft = DRAIN_PERIOD;
            int cost = BASE_DRAIN + DRAIN_PER_EFFECT * Integer.bitCount(zone.flags() & MinorityWorld.EFFECTS);
            if (!data.spend(cost)) {
                deactivate(player, data);
                data.setCooldown(SkillType.MINORITY_WORLD, COOLDOWN);
                return;
            }
        }

        AABB box = new AABB(zone.center(), zone.center()).inflate(zone.radius());
        var zones = MinorityWorld.SERVER_ZONES.values();
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box, e -> !(e instanceof Player))) {
            int flags = MinorityWorld.flagsFor(zones, living);
            if ((flags & MinorityWorld.SOLID_TO_LIQUID) != 0) {
                living.noPhysics = true;
                state.affected.add(living.getId());
                Vec3 motion = living.getDeltaMovement();
                boolean aboveFloor = living.getY() > level.getMinBuildHeight() + 2;
                living.setDeltaMovement(motion.x, aboveFloor ? -SINK_SPEED : 0.0, motion.z);
                living.resetFallDistance();
            } else if (state.affected.remove(living.getId())) {
                living.noPhysics = false;
            }
            if ((flags & MinorityWorld.LIQUID_TO_SOLID) != 0) {
                MinorityWorld.standOnFluid(living);
            }
        }
    }
}
