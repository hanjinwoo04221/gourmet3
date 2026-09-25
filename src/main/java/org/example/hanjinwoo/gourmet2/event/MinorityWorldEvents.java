package org.example.hanjinwoo.gourmet2.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import org.example.hanjinwoo.gourmet2.entity.SkillProjectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.network.S2CMinorityZone;
import org.example.hanjinwoo.gourmet2.registry.ModAttachments;
import org.example.hanjinwoo.gourmet2.skill.MinorityWorld;
import org.example.hanjinwoo.gourmet2.skill.impl.MinorityWorldSkill;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** The server side of Minority World: the abilities that hook into damage, healing, knockback and player abilities. */
@EventBusSubscriber(modid = Gourmet2.MODID)
public final class MinorityWorldEvents {
    /** Set while an inversion is itself dealing or healing, so it is not inverted back. */
    private static boolean inverting;
    /** Players this zone code gave flight to, so it takes back only what it gave. */
    private static final Set<UUID> GRANTED_FLIGHT = new HashSet<>();

    private MinorityWorldEvents() {}

    private static int flagsFor(LivingEntity entity) {
        return MinorityWorld.SERVER_ZONES.isEmpty() ? 0 : MinorityWorld.flagsFor(MinorityWorld.SERVER_ZONES.values(), entity);
    }

    /** Damage becomes healing (the ground swallowing a creature is not a wall to choke on either). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        int flags = flagsFor(victim);
        if (flags == 0) {
            return;
        }
        if ((flags & MinorityWorld.SOLID_TO_LIQUID) != 0 && event.getSource().is(DamageTypes.IN_WALL)) {
            event.setCanceled(true);
            return;
        }
        if ((flags & MinorityWorld.HEAL) != 0 && !inverting
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            float amount = event.getAmount();
            event.setCanceled(true);
            inverting = true;
            try {
                victim.heal(amount);
            } finally {
                inverting = false;
            }
        }
    }

    /** Healing becomes damage. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHeal(LivingHealEvent event) {
        LivingEntity victim = event.getEntity();
        if (inverting || (flagsFor(victim) & MinorityWorld.HEAL) == 0) {
            return;
        }
        float amount = event.getAmount();
        event.setCanceled(true);
        inverting = true;
        try {
            float left = victim.getHealth() - amount;
            if (left <= 0.5F) {
                victim.invulnerableTime = 0;
                victim.hurt(victim.damageSources().magic(), Math.max(amount, 0.5F));
            } else {
                victim.setHealth(left);
            }
        } finally {
            inverting = false;
        }
    }

    /**
     * Attacks fly the other way: a projectile loosed by a creature in a zone with inverted attack direction turns
     * around as it is created. The mod's own techniques are turned around where they are aimed instead (see
     * {@link MinorityWorld#aim}), so they are left alone here.
     */
    @SubscribeEvent
    public static void onProjectileJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk() || MinorityWorld.SERVER_ZONES.isEmpty()
                || !(event.getEntity() instanceof Projectile projectile) || projectile instanceof SkillProjectile
                || !(projectile.getOwner() instanceof LivingEntity owner)) {
            return;
        }
        if ((flagsFor(owner) & MinorityWorld.ATTACK_DIRECTION) != 0) {
            projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-1.0));
        }
    }

    /**
     * Players in the zone: flight (given and taken back through the abilities), and, while solid ground is liquid,
     * no collision on the server either, so the server accepts a client that has sunk into the ground. The flag is
     * put back every tick because the player's own tick clears it.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int flags = flagsFor(player);
        boolean creativeLike = player.isCreative() || player.isSpectator();
        if ((flags & MinorityWorld.FLIGHT) != 0) {
            if (!player.getAbilities().mayfly && !creativeLike) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
                GRANTED_FLIGHT.add(player.getUUID());
            }
        } else if (GRANTED_FLIGHT.remove(player.getUUID()) && !creativeLike) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        if ((flags & MinorityWorld.SOLID_TO_LIQUID) != 0) {
            player.noPhysics = true;
            player.resetFallDistance();
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        for (MinorityWorld.Zone zone : MinorityWorld.SERVER_ZONES.values()) {
            PacketDistributor.sendToPlayer(player, S2CMinorityZone.started(zone));
        }
        int flags = ModAttachments.of(player).minorityFlags();
        if (!MinorityWorld.SERVER_ZONES.containsKey(player.getUUID())) {
            PacketDistributor.sendToPlayer(player, S2CMinorityZone.ended(player.getUUID(), flags));
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MinorityWorldSkill.deactivate(player, ModAttachments.of(player));
            GRANTED_FLIGHT.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MinorityWorld.SERVER_ZONES.clear();
        GRANTED_FLIGHT.clear();
    }
}
