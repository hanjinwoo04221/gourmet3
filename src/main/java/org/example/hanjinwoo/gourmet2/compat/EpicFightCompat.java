package org.example.hanjinwoo.gourmet2.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.example.hanjinwoo.gourmet2.client.ClientTorikoData;
import org.example.hanjinwoo.gourmet2.registry.ModAttachments;
import org.example.hanjinwoo.gourmet2.skill.combat.AttackGroup;
import org.example.hanjinwoo.gourmet2.skill.combat.CombatStyle;
import org.example.hanjinwoo.gourmet2.skill.combat.CombatStyles;
import org.example.hanjinwoo.gourmet2.skill.combat.ComboMove;
import yesman.epicfight.api.event.EpicFightEventHooks;
import yesman.epicfight.api.event.types.player.SkillCastEvent;
import yesman.epicfight.skill.SkillCategories;
import yesman.epicfight.skill.SkillCategory;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import net.neoforged.bus.api.IEventBus;
import org.example.hanjinwoo.gourmet2.Gourmet2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.property.AnimationProperty;
import yesman.epicfight.api.animation.types.ActionAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

/**
 * Epic Fight side of combat mode. Only ever touched through {@link CombatAnimations}, and only when
 * Epic Fight is installed, so this mod still loads without it.
 *
 * <p>The animation files live in {@code assets/gourmet2/animmodels/animations/combat/} and were
 * authored in Blender on the Epic Fight player rig (see {@code rig/combat_animations.blend}).
 */
public final class EpicFightCompat {
    private static final float BLEND_TIME = 0.05F;

    /** Clips every style has whatever its attack groups are: the dodge, the guard and its release. */
    private static final String[] ACTION_CLIPS = {"dodge", "guard", "guard_release"};
    private static final String[] STANCE_CLIPS = {"idle_stance", "walk_stance", "run_stance"};
    /** Extra clips only aerial-combo styles need; see {@link CombatStyle#aerialCombo()}. */
    private static final String[] AERIAL_CLIPS = {"launcher", "spike"};
    /** Clip length in ticks of the clips that are not attack-group moves, keyed by clip name. */
    private static final Map<String, Integer> FIXED_CLIP_TICKS =
            Map.of("dodge", 11, "guard", 18, "guard_release", 5, "launcher", 20, "spike", 16);
    /** How long a clip holds Epic Fight mode, keyed by "<animationDir>/<clip>". */
    private static final Map<String, Integer> CLIP_TICKS = new HashMap<>();

    /**
     * One-shot clips played from {@code animations/skill/}: the skills, plus the leap's wind-up and its
     * launch (those are not skills, but they use the same shared folder so every style gets them).
     * Value = clip length in frames at 24 fps.
     */
    private static final Map<String, Integer> SKILL_CLIP_FRAMES = Map.ofEntries(
            Map.entry("nail_punch_charge", 56), Map.entry("nail_punch", 17),
            Map.entry("nail_gun_charge", 56), Map.entry("nail_gun_hold", 12), Map.entry("nail_gun", 24),
            Map.entry("fork", 20), Map.entry("knife", 20), Map.entry("leg_knife", 20),
            Map.entry("leg_knife_charge", 56), Map.entry("flying_fork_charge", 56), Map.entry("flying_fork_shot", 10),
            Map.entry("flying_knife_charge", 56), Map.entry("flying_knife_shot", 12), Map.entry("intimidation", 34),
            Map.entry("demon_form", 46), Map.entry("ki_release", 40), Map.entry("food_immersion", 84),
            Map.entry("leap_charge", 24), Map.entry("leap_hold", 12),
            Map.entry("leap", 16), Map.entry("leap_up", 16),
            Map.entry("leap_fly", 12), Map.entry("leap_up_fly", 12));

    /** Accessors keyed by "<animationDir>/<clip>". */
    private static final Map<String, AnimationManager.AnimationAccessor<? extends StaticAnimation>> CLIPS = new HashMap<>();

    private EpicFightCompat() {}

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(EpicFightCompat::registerAnimations);
        // Epic Fight's own attack / dodge / guard / dash skills must not fire in combat mode, or its
        // motions (for example the Ctrl + click sprint attack) play over ours.
        EpicFightEventHooks.Player.CAST_SKILL.registerEvent(EpicFightCompat::blockOwnCombat);
    }

    /** True when the main hand holds something Epic Fight treats as a weapon (not bare hands or a plain item). */
    public static boolean holdsWeapon(Player player) {
        var stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return false;
        }
        var capability = EpicFightCapabilities.getItemStackCapability(stack);
        if (capability == null || capability.isEmpty()) {
            return false;
        }
        var category = capability.getWeaponCategory();
        return category != CapabilityItem.WeaponCategories.NOT_WEAPON && category != CapabilityItem.WeaponCategories.FIST;
    }

    private static void blockOwnCombat(SkillCastEvent event) {
        var skill = event.getSkillContainer().getSkill();
        if (skill == null) {
            return;
        }
        SkillCategory category = skill.getCategory();
        if (category != SkillCategories.BASIC_ATTACK && category != SkillCategories.DODGE
                && category != SkillCategories.GUARD && category != SkillCategories.MOVER) {
            return;
        }
        Player player = event.getPlayerPatch().getOriginal();
        boolean combatMode = player instanceof ServerPlayer server
                ? ModAttachments.of(server).isCombatMode()
                : player.level().isClientSide() && ClientTorikoData.isCombatMode();
        // A held weapon keeps Epic Fight's own attacks, dodge and guard.
        if (combatMode && !holdsWeapon(player)) {
            event.cancel();
        }
    }

    private static void registerAnimations(AnimationManager.AnimationRegistryEvent event) {
        event.newBuilder(Gourmet2.MODID, builder -> {
            for (String clip : SKILL_CLIP_FRAMES.keySet()) {
                String key = "skill/" + clip;
                CLIPS.put(key, builder.nextAccessor(key, EpicFightCompat::action));
            }
            for (CombatStyle style : CombatStyles.ALL) {
                // A style's attack clips are whatever its groups list, so adding a group needs no code here.
                for (AttackGroup group : style.groups()) {
                    for (ComboMove move : group.moves()) {
                        String key = style.animationDir() + "/" + move.clip();
                        if (!CLIPS.containsKey(key)) {
                            CLIP_TICKS.put(key, move.clipTicks());
                            CLIPS.put(key, builder.nextAccessor(key, EpicFightCompat::action));
                        }
                    }
                }
                for (String clip : ACTION_CLIPS) {
                    String key = style.animationDir() + "/" + clip;
                    CLIPS.put(key, builder.nextAccessor(key, EpicFightCompat::action));
                }
                if (style.aerialCombo()) {
                    for (String clip : AERIAL_CLIPS) {
                        String key = style.animationDir() + "/" + clip;
                        CLIPS.put(key, builder.nextAccessor(key, EpicFightCompat::action));
                    }
                }
                for (String clip : STANCE_CLIPS) {
                    // Looping clips that replace idle / walk / run while combat mode is on.
                    String key = style.animationDir() + "/" + clip;
                    float blend = clip.startsWith("run") ? 0.12F : 0.15F;
                    CLIPS.put(key, builder.nextAccessor(key, acc -> new StaticAnimation(blend, true, acc, Armatures.BIPED)));
                }
            }
        });
    }

    /**
     * Action animations are not replaced by Epic Fight's idle/walk/fall motions, which is what made a
     * plain animation stop after a moment whenever the lunge or dash moved the player. Root motion is
     * switched off because {@link org.example.hanjinwoo.gourmet2.skill.combat.CombatEngine} already moves
     * the player on the server.
     */
    private static ActionAnimation action(AnimationManager.AnimationAccessor<ActionAnimation> accessor) {
        return new ActionAnimation(BLEND_TIME, accessor, Armatures.BIPED)
                .addProperty(AnimationProperty.ActionAnimationProperty.STOP_MOVEMENT, true);
    }

    /**
     * Epic Fight only draws its animations on players that are in Epic Fight mode; otherwise the
     * vanilla model is shown and nothing appears to change. So the player is switched into that mode for
     * as long as a combat animation is running, and put back afterwards.
     */
    private static final Map<UUID, Long> MODE_UNTIL = new HashMap<>();
    private static final Set<UUID> WAS_VANILLA = new HashSet<>();
    private static final Map<UUID, Long> GUARD_STARTED = new HashMap<>();
    private static final int GUARD_CLIP_TICKS = 20;

    /**
     * Ticks to stay in Epic Fight mode after this clip starts, so the animation is not cut off. Every
     * attack-group move carries its own clip length (see {@link ComboMove#clipTicks()}); the clips all
     * styles share are the fixed handful below.
     *
     * @param key the clip's "<animationDir>/<clip>" registration key
     */
    private static int durationTicks(String key) {
        Integer ticks = CLIP_TICKS.get(key);
        if (ticks != null) {
            return ticks;
        }
        return FIXED_CLIP_TICKS.getOrDefault(key.substring(key.indexOf('/') + 1), 18);
    }

    /** @param name one of the style's attack clips, or dodge / guard / guard_release / "stop" to return to idle */
    public static void play(ServerPlayer player, String name) {
        ServerPlayerPatch patch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
        if (patch == null) {
            Gourmet2.LOGGER.warn("Epic Fight has no player patch for {}; combat animation '{}' skipped", player.getName().getString(), name);
            return;
        }
        long now = player.level().getGameTime();
        if (name.equals("stop")) {
            // Epic Fight's own idle would flash for a moment; a short clip that blends the guard pose
            // back into the combat stance ends straight in the living animation instead.
            play(player, "guard_release");
            return;
        }
        String styleDir = CombatStyles.get(ModAttachments.of(player).combatStyle()).animationDir();
        String key = styleDir + "/" + name;
        var animation = CLIPS.get(key);
        if (animation == null) {
            Gourmet2.LOGGER.warn("Combat animation '{}' is not registered", name);
            return;
        }
        if (patch.isVanillaMode() && WAS_VANILLA.add(player.getUUID())) {
            patch.toEpicFightMode(true);
        } else if (patch.isVanillaMode()) {
            patch.toEpicFightMode(true);
        }
        patch.playAnimationSynchronized(animation, 0.0F);
        MODE_UNTIL.put(player.getUUID(), now + durationTicks(key));
        if (name.equals("guard")) {
            GUARD_STARTED.put(player.getUUID(), now);
        } else {
            GUARD_STARTED.remove(player.getUUID());
        }
    }

    /** Whether a clip in {@code animations/skill/} was registered under that name. */
    public static boolean hasSkillClip(String clip) {
        return CLIPS.containsKey("skill/" + clip);
    }

    /** Plays the animation for a skill cast; the same for every combat style. */
    public static void playSkill(ServerPlayer player, String clip) {
        var animation = CLIPS.get("skill/" + clip);
        if (animation == null) {
            Gourmet2.LOGGER.warn("Skill animation '{}' is not registered", clip);
            return;
        }
        ServerPlayerPatch patch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
        if (patch == null) {
            return;
        }
        UUID id = player.getUUID();
        if (patch.isVanillaMode()) {
            WAS_VANILLA.add(id);
            patch.toEpicFightMode(true);
        }
        patch.playAnimationSynchronized(animation, 0.0F);
        // Frames to ticks (24 fps -> 20 tps), plus a little so the mode does not flip back mid-clip.
        MODE_UNTIL.put(id, player.level().getGameTime() + SKILL_CLIP_FRAMES.get(clip) * 20 / 24 + 4);
        GUARD_STARTED.remove(id);
    }

    /** A looping stance clip ("idle_stance", "walk_stance", "run_stance") of the given style. */
    static AnimationManager.AnimationAccessor<? extends StaticAnimation> stance(CombatStyle style, String clip) {
        return CLIPS.get(style.animationDir() + "/" + clip);
    }

    /** Combat mode keeps the player in Epic Fight mode for its whole duration so the stance clips show. */
    public static void setCombatMode(ServerPlayer player, boolean on) {
        ServerPlayerPatch patch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
        if (patch == null) {
            return;
        }
        UUID id = player.getUUID();
        if (on) {
            if (patch.isVanillaMode()) {
                WAS_VANILLA.add(id);
                patch.toEpicFightMode(true);
            }
        } else {
            MODE_UNTIL.remove(id);
            GUARD_STARTED.remove(id);
            if (WAS_VANILLA.remove(id) && patch.isEpicFightMode()) {
                patch.toVanillaMode(true);
            }
        }
    }

    /** Keeps Epic Fight mode on while the guard is held, and restores vanilla mode once animations finish. */
    public static void tick(ServerPlayer player, boolean guarding, boolean combatMode) {
        if (combatMode) {
            // Stays in Epic Fight mode until combat mode is switched off.
            if (guarding) {
                Long started = GUARD_STARTED.get(player.getUUID());
                long now = player.level().getGameTime();
                if (started != null && now - started >= GUARD_CLIP_TICKS) {
                    play(player, "guard");
                }
            }
            return;
        }
        UUID id = player.getUUID();
        Long until = MODE_UNTIL.get(id);
        if (until == null) {
            return;
        }
        long now = player.level().getGameTime();
        if (guarding) {
            MODE_UNTIL.put(id, now + 30);
            // The guard clip is one second long, so start it again each time it runs out.
            Long started = GUARD_STARTED.get(id);
            if (started != null && now - started >= GUARD_CLIP_TICKS) {
                play(player, "guard");
            }
            return;
        }
        if (now >= until) {
            MODE_UNTIL.remove(id);
            if (WAS_VANILLA.remove(id)) {
                ServerPlayerPatch patch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
                if (patch != null && patch.isEpicFightMode()) {
                    patch.toVanillaMode(true);
                }
            }
        }
    }
}
