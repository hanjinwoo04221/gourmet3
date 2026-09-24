package org.example.hanjinwoo.gourmet2.event;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.command.CellLevelCommand;
import org.example.hanjinwoo.gourmet2.registry.ModAttachments;
import org.example.hanjinwoo.gourmet2.skill.CellGrowth;
import org.example.hanjinwoo.gourmet2.skill.SkillEngine;
import org.example.hanjinwoo.gourmet2.skill.combat.CombatEngine;

/** Wires the skill engine into the server's player lifecycle. */
@EventBusSubscriber(modid = Gourmet2.MODID)
public final class CommonEvents {
    private CommonEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillEngine.tick(player);
        }
    }

    /** Eating is how Appetite is topped up quickly — the Gourmet Cells run on food. */
    @SubscribeEvent
    public static void onFinishUsingItem(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        FoodProperties food = event.getItem().get(DataComponents.FOOD);
        if (food != null) {
            SkillEngine.onFoodEaten(player, food.nutrition());
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        CellLevelCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CombatEngine.onIncomingDamage(player, event);
        }
    }

    /** A Spike (see the Acrobatic combat style) is an intentional dive, not an accidental fall. */
    @SubscribeEvent
    public static void onFall(net.neoforged.neoforge.event.entity.living.LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && org.example.hanjinwoo.gourmet2.registry.ModAttachments.of(player).fallImmuneTicks() > 0) {
            event.setCanceled(true);
        }
    }

    /**
     * A save that lands while a burst is out hides ground behind barriers with nothing to put it back, so
     * whatever is still written down when a level loads is restored here (see {@code HiddenBlocks}).
     */
    @SubscribeEvent
    public static void onLevelLoad(net.neoforged.neoforge.event.level.LevelEvent.Load event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            org.example.hanjinwoo.gourmet2.data.HiddenBlocks.of(level).restore(level);
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // The body is derived from the level, so it is rebuilt rather than stored: this is what hands a
            // player who evolved before the body grew at all the one they had already earned.
            CellGrowth.apply(player, ModAttachments.of(player).cellLevel());
            SkillEngine.sync(player);
        }
    }

    /**
     * A new body — a respawn, or the trip back through the End — is handed only the old one's base attribute
     * values, so the body the player grew into is rebuilt onto it (see {@code CellGrowth}, which derives the
     * whole body from the level and so has nothing to carry across by hand).
     */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CellGrowth.apply(player, ModAttachments.of(player).cellLevel());
            SkillEngine.sync(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillEngine.abortActive(player);
            CellGrowth.apply(player, ModAttachments.of(player).cellLevel());
            SkillEngine.sync(player);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillEngine.abortActive(player);
        }
    }
}
