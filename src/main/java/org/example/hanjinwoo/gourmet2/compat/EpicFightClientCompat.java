package org.example.hanjinwoo.gourmet2.compat;

import net.minecraft.client.Minecraft;
import org.example.hanjinwoo.gourmet2.client.ClientTorikoData;
import org.example.hanjinwoo.gourmet2.skill.combat.CombatStyle;
import org.example.hanjinwoo.gourmet2.skill.combat.CombatStyles;
import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.input.InputManager;
import yesman.epicfight.api.client.input.action.EpicFightInputAction;
import yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;

import java.util.HashMap;
import java.util.Map;

/** Client half of the combat stance: overrides the local player's living animations while combat mode is on. */
final class EpicFightClientCompat {
    private static final LivingMotion[] MOTIONS = {LivingMotions.IDLE, LivingMotions.WALK, LivingMotions.RUN};
    private static final Map<LivingMotion, AssetAccessor<? extends StaticAnimation>> ORIGINALS = new HashMap<>();
    private static boolean applied;

    private EpicFightClientCompat() {}

    private static void setIfDifferent(Animator animator, LivingMotion motion, AssetAccessor<? extends StaticAnimation> wanted) {
        if (animator.getLivingAnimation(motion, null) != wanted) {
            animator.addLivingAnimation(motion, wanted);
        }
    }

    /**
     * Epic Fight replaces the attack input while in its mode, so the vanilla attack key can read as
     * released for clicks at an entity or the air. Its own attack action still reports the real state.
     */
    static boolean attackActive() {
        return InputManager.isActionPhysicallyActive(EpicFightInputAction.ATTACK);
    }

    static void tick(boolean combatMode) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            applied = false;
            return;
        }
        LocalPlayerPatch patch = EpicFightCapabilities.getEntityPatch(player, LocalPlayerPatch.class);
        if (patch == null) {
            return;
        }
        Animator animator = patch.getAnimator();
        if (combatMode) {
            if (!applied) {
                ORIGINALS.clear();
                for (LivingMotion motion : MOTIONS) {
                    ORIGINALS.put(motion, animator.getLivingAnimation(motion, null));
                }
                applied = true;
            }
            // Only re-applied when Epic Fight has swapped them back (held item or mode change): setting
            // them every tick restarts the clip each time, which showed as legs shaking in place.
            CombatStyle style = CombatStyles.get(ClientTorikoData.combatStyle());
            setIfDifferent(animator, LivingMotions.IDLE, EpicFightCompat.stance(style, "idle_stance"));
            setIfDifferent(animator, LivingMotions.WALK, EpicFightCompat.stance(style, "walk_stance"));
            setIfDifferent(animator, LivingMotions.RUN, EpicFightCompat.stance(style, "run_stance"));
        } else if (applied) {
            applied = false;
            for (LivingMotion motion : MOTIONS) {
                AssetAccessor<? extends StaticAnimation> original = ORIGINALS.get(motion);
                if (original != null) {
                    animator.addLivingAnimation(motion, original);
                } else {
                    animator.getLivingAnimations().remove(motion);
                }
            }
        }
    }
}
