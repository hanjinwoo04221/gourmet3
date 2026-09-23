package org.example.hanjinwoo.gourmet2.client.fx;

/**
 * Every Effekseer effect path this mod can ask for, relative to
 * {@code assets/gourmet2/effeks/} and without the {@code .efkefc} extension.
 *
 * <p>{@link Custom} holds the purpose-built effects. They are <b>optional</b>: drop an exported
 * {@code .efkefc} at the matching path and {@link FxLibrary} will start using it automatically,
 * because every visual lists a custom id first and a bundled sample as the fallback. Nothing
 * breaks while they are absent — see {@code docs/EFFEKSEER_EXPORT.md} for which sample project to
 * export for each one.
 *
 * <p>{@link Sample} holds the CC-0 Effekseer samples that ship in the assets folder. Only the
 * {@code .efkefc} files are listed: the loader cannot read the {@code .efkproj} editor projects,
 * so those are not usable ids.
 */
public final class EffekIds {
    private EffekIds() {}

    /** Purpose-built effects. Optional — supply them by exporting from the Effekseer editor. */
    public static final class Custom {
        public static final String NAIL_PUNCH = "toriko/nail_punch";
        public static final String NAIL_IMPACT = "toriko/nail_impact";
        public static final String NAIL_GUN = "toriko/nail_gun";
        public static final String NAIL_FINISH = "toriko/nail_finish";
        public static final String THIRTEEN_LOCK = "toriko/thirteen_lock";
        public static final String FORK = "toriko/fork";
        public static final String FORK_IMPACT = "toriko/fork_impact";
        public static final String KNIFE_SLASH = "toriko/knife_slash";
        public static final String KNIFE_IMPACT = "toriko/knife_impact";
        public static final String LEG_KNIFE = "toriko/leg_knife";
        public static final String LEG_KNIFE_SLASH = "toriko/leg_knife_slash";
        public static final String FLYING_FORK_CAST = "toriko/flying_fork_cast";
        public static final String FLYING_FORK_TRAIL = "toriko/flying_fork_trail";
        public static final String FLYING_KNIFE_CAST = "toriko/flying_knife_cast";
        public static final String FLYING_KNIFE_TRAIL = "toriko/flying_knife_trail";
        public static final String INTIMIDATION = "toriko/intimidation";
        public static final String FOOD_IMMERSION = "toriko/food_immersion";
        public static final String FOOD_IMMERSION_BURST = "toriko/food_immersion_burst";
        public static final String AWAKENED_AURA = "toriko/awakened_aura";
        /** Impact of a combat-mode blow; see {@code FxLibrary} for the look it falls back to. */
        public static final String COMBAT_IMPACT = "toriko/combat_impact";

        private Custom() {}
    }

    /**
     * Shared "the caster's hand becomes this tool" shapes — one file per tool, reused by every
     * skill that swings that tool, instead of duplicating the same effect under every skill's own
     * slot. See {@link FxLibrary}: each cast slot tries its own dedicated id first, then the
     * matching {@link Shape}, then a bundled sample.
     */
    public static final class Shape {
        public static final String NAIL = "toriko/shapes/nail";
        public static final String FORK = "toriko/shapes/fork";
        public static final String KNIFE = "toriko/shapes/knife";

        private Shape() {}
    }

    /** The bundled CC-0 samples, used as fallbacks. These all exist out of the box. */
    public static final class Sample {
        public static final String LASER_01 = "00_Basic/Laser01";
        public static final String LASER_02 = "00_Basic/Laser02";
        public static final String LASER_03 = "00_Basic/Laser03";
        public static final String RIBBON_SWORD = "00_Basic/Simple_Ribbon_Sword";
        public static final String RIBBON_PARENT = "00_Basic/Simple_Ribbon_Parent";
        public static final String TRACK = "00_Basic/Simple_Track1";
        public static final String RING_1 = "00_Basic/Simple_Ring_Shape1";
        public static final String RING_2 = "00_Basic/Simple_Ring_Shape2";
        public static final String FIREWORKS = "00_Basic/Simple_Turbulence_Fireworks";
        public static final String TURBULENCE = "00_Basic/Simple_Turbulence_Particles";
        public static final String SPAWN_BURST = "00_Basic/Simple_SpawnMethod1";
        public static final String DISTORTION = "00_Basic/Simple_Distortion";

        public static final String AURA = "00_Version16/Aura01";
        public static final String BARRIER_1 = "00_Version16/Barrior01";
        public static final String BARRIER_2 = "00_Version16/Barrior02";
        public static final String BARRIER_3 = "00_Version16/Barrior03";
        public static final String TORNADO = "00_Version16/ForceFieldTornado";

        public static final String LIGHT = "02_Tktk03/Light";
        public static final String TOON_HIT = "02_Tktk03/ToonHit";

        /**
         * @deprecated Do not use as a fallback candidate. This sample's internal material
         *     references its texture via a relative {@code ../Texture/black.png} path, which
         *     Minecraft's resource pack loader unconditionally rejects ({@code Invalid segment
         *     '..' in path}). The file is present on disk but will never actually load — AAA
         *     Particles logs "Failed to load" for it and {@link FxPlayer#resolve} has no way to
         *     tell, so it silently swallows whatever visual was supposed to render. Kept only so
         *     the constant doesn't dangle for anyone poking around the sample pack directly.
         */
        @Deprecated
        public static final String HANMADO_HIT = "03_Hanmado01/Effect/hit_hanmado_0409";

        private Sample() {}
    }
}
