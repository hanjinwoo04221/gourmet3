package org.example.hanjinwoo.gourmet2.client.fx;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.example.hanjinwoo.gourmet2.client.fx.EffekIds.Custom;
import org.example.hanjinwoo.gourmet2.client.fx.EffekIds.Sample;
import org.example.hanjinwoo.gourmet2.client.fx.EffekIds.Shape;
import org.example.hanjinwoo.gourmet2.fx.SkillFx;

import net.minecraft.world.phys.Vec3;

/**
 * The look of every skill: which Effekseer emitters make up each {@link SkillFx}, how big they
 * are and where they sit.
 *
 * <p>This is the file to edit when tuning visuals. Each part lists a purpose-built effect first
 * and a bundled CC-0 sample after it, so the mod looks complete out of the box and gets better as
 * hand-made effects are dropped in.
 */
public final class FxLibrary {
    /**
     * Roughly where the right fist sits: off to the side and slightly forward, at fist height.
     * Uses {@link FxPart#onBody} (yaw-only, not head pitch) so the effect doesn't swing wildly
     * overhead/underfoot just because the caster is looking up or down.
     */
    private static final Vec3 RIGHT_HAND = new Vec3(0.45, 1.1, 0.35);
    private static final Vec3 FEET = new Vec3(0.0, 0.05, 0.0);
    private static final Vec3 TORSO = new Vec3(0.0, 1.0, 0.0);

    private static final Map<SkillFx, List<FxPart>> PARTS = new EnumMap<>(SkillFx.class);

    static {
        // --------------------------------------------------- Combat mode blow (no skill)
        // Its own slot, so a bare-handed style never borrows the nail punch's impact effect. The ring
        // on top is what makes a plain swing read as a hit; drop an exported effect at
        // toriko/combat_impact.efkefc to replace the fallback. Other bundled samples worth trying in its
        // place: Sample.SPAWN_BURST, Sample.DISTORTION.
        define(SkillFx.COMBAT_IMPACT,
                FxPart.world(0.45F, Custom.COMBAT_IMPACT, Sample.TOON_HIT),
                FxPart.world(0.35F, Sample.RING_1));

        // -------------------------------------------------------------- Nail Punch
        // The impact is deliberately small: a hit effect fires at a target being punched, often every other
        // tick for a whole combo, and one sized like a single big blow blankets the body it lands on and
        // everything behind it. Sized to read as a hard hit on the spot instead — the finisher, which lands
        // once, is the only one of the three with any size to it.
        define(SkillFx.NAIL_PUNCH_CAST,
                FxPart.onBody(0.09F, RIGHT_HAND, Custom.NAIL_PUNCH, Shape.NAIL, Sample.LASER_01));
        define(SkillFx.NAIL_PUNCH_IMPACT,
                FxPart.world(0.20F, Custom.NAIL_IMPACT, Sample.TOON_HIT));

        // ---------------------------------------------------------------- Nail Gun
        define(SkillFx.NAIL_GUN_CAST,
                FxPart.onBody(0.09F, RIGHT_HAND, Custom.NAIL_GUN, Shape.NAIL, Sample.TURBULENCE));
        define(SkillFx.NAIL_GUN_IMPACT,
                FxPart.world(0.35F, Custom.NAIL_IMPACT, Sample.TOON_HIT));
        // The round in flight, riding the entity it belongs to. Anchored like the other flying techniques (see
        // FLYING_FORK_TRAIL) and not like the muzzle flash above: a body-anchored part only ever tracks yaw and
        // treats pitch as level, which made a round fired up or down fly out flat and read as going the wrong way.
        define(SkillFx.NAIL_SHOT_TRAIL,
                FxPart.onProjectile(0.35F, Vec3.ZERO, Custom.NAIL_GUN, Shape.NAIL, Sample.TRACK));

        // ------------------------------------------------- 13-hit Nail Punch combo
        // Both of these are impacts on a body, so both are kept small for the same reason as NAIL_PUNCH_IMPACT:
        // a combo's hits are two ticks apart and would otherwise stack a screenful of effect on one victim. The
        // ramp in the skill still scales each hit up as the combo builds (see NailPunchSkill), and the finisher
        // keeps its size because it is the one blow of the chain that lands alone.
        define(SkillFx.THIRTEEN_LOCK,
                FxPart.onBody(0.5F, RIGHT_HAND, Custom.THIRTEEN_LOCK, Shape.NAIL, Sample.BARRIER_3));
        define(SkillFx.THIRTEEN_IMPACT,
                FxPart.world(0.14F, Custom.NAIL_IMPACT, Sample.TOON_HIT));
        define(SkillFx.THIRTEEN_FINISH,
                FxPart.world(0.42F, Custom.NAIL_FINISH, Sample.TOON_HIT),
                FxPart.world(0.35F, Sample.RING_1));

        // -------------------------------------------------------------------- Fork
        define(SkillFx.FORK_CAST,
                FxPart.onBody(0.13F, RIGHT_HAND, Custom.FORK, Shape.FORK, Sample.LASER_02));
        define(SkillFx.FORK_IMPACT,
                FxPart.world(0.40F, Custom.FORK_IMPACT, Sample.TOON_HIT));

        // ------------------------------------------------------------------- Knife
        define(SkillFx.KNIFE_CAST,
                FxPart.onBody(0.33F, RIGHT_HAND, Custom.KNIFE_SLASH, Shape.KNIFE, Sample.RIBBON_SWORD));
        define(SkillFx.KNIFE_IMPACT,
                FxPart.world(0.50F, Custom.KNIFE_IMPACT, Sample.TOON_HIT));

        // --------------------------------------------------------------- Leg Knife
        // The kick itself only needs a flash at the foot; the crescent it throws is LEG_KNIFE_SLASH.
        define(SkillFx.LEG_KNIFE_CAST);
        // The crescent of cutting wind. Drop your own Effekseer file at toriko/leg_knife_slash.efkefc to replace it.
        define(SkillFx.LEG_KNIFE_SLASH,
                FxPart.onProjectile(0.4F, Vec3.ZERO, Custom.LEG_KNIFE_SLASH, Custom.FLYING_KNIFE_TRAIL, Shape.KNIFE, Sample.RIBBON_PARENT));

        // ------------------------------------------------------------ Flying Fork
        define(SkillFx.FLYING_FORK_CAST,
                FxPart.onBody(0.25F, RIGHT_HAND, Custom.FLYING_FORK_CAST, Shape.FORK, Sample.LASER_03));
        define(SkillFx.FLYING_FORK_TRAIL,
                FxPart.onProjectile(0.50F, Vec3.ZERO, Custom.FLYING_FORK_TRAIL, Shape.FORK, Sample.TRACK));
        define(SkillFx.FLYING_FORK_IMPACT,
                FxPart.world(0.35F, Custom.FORK_IMPACT, Sample.TOON_HIT));

        // ----------------------------------------------------------- Flying Knife
        define(SkillFx.FLYING_KNIFE_CAST,
                FxPart.onBody(0.45F, RIGHT_HAND, Custom.FLYING_KNIFE_CAST, Shape.KNIFE, Sample.RIBBON_SWORD));
        define(SkillFx.FLYING_KNIFE_TRAIL,
                FxPart.onProjectile(1.00F, Vec3.ZERO, Custom.FLYING_KNIFE_TRAIL, Shape.KNIFE, Sample.RIBBON_PARENT));
        define(SkillFx.FLYING_KNIFE_IMPACT,
                FxPart.world(0.60F, Custom.KNIFE_IMPACT, Sample.TOON_HIT));

        // ------------------------------------------------ Intimidation / the Demon
        // Kept alive by the Appetite Demon for as long as stage 1 lasts; no sample fallbacks on
        // purpose, so a missing file shows nothing rather than a stray white tornado.
        define(SkillFx.INTIMIDATION_CAST,
                FxPart.onBody(1.5F, TORSO, Custom.INTIMIDATION));

        // ---------------------------------------------------------- Food Immersion
        define(SkillFx.FOOD_IMMERSION_CHANNEL,
                FxPart.onBody(1.0F, FEET, Custom.FOOD_IMMERSION, Sample.LIGHT));
        define(SkillFx.FOOD_IMMERSION_BURST,
                FxPart.onBody(1.4F, TORSO, Custom.FOOD_IMMERSION_BURST, Sample.BARRIER_1),
                FxPart.onBody(0.8F, TORSO, Sample.FIREWORKS));
        define(SkillFx.AWAKENED_AURA,
                FxPart.onBody(1.1F, FEET, Custom.AWAKENED_AURA, Sample.AURA));

        for (SkillFx fx : SkillFx.VALUES) {
            if (!PARTS.containsKey(fx)) {
                throw new IllegalStateException("No visual defined for SkillFx." + fx);
            }
        }
    }

    private FxLibrary() {}

    public static List<FxPart> parts(SkillFx fx) {
        return PARTS.getOrDefault(fx, List.of());
    }

    private static void define(SkillFx fx, FxPart... parts) {
        PARTS.put(fx, List.of(parts));
    }
}
