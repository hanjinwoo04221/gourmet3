package org.example.hanjinwoo.gourmet2;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Balance knobs for the Toriko skill set. Everything is expressed as a multiplier on top of the
 * per-skill defaults declared in {@link org.example.hanjinwoo.gourmet2.skill.SkillType}, so a
 * server owner can retune the whole mod without touching every skill.
 */
@EventBusSubscriber(modid = Gourmet2.MODID)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.DoubleValue DAMAGE_MULTIPLIER = BUILDER
            .comment("Global multiplier applied to all Toriko skill damage.")
            .defineInRange("balance.damageMultiplier", 1.0, 0.0, 64.0);

    private static final ModConfigSpec.DoubleValue COOLDOWN_MULTIPLIER = BUILDER
            .comment("Global multiplier applied to all skill cooldowns. 0.5 = twice as fast.")
            .defineInRange("balance.cooldownMultiplier", 1.0, 0.0, 16.0);

    private static final ModConfigSpec.DoubleValue APPETITE_COST_MULTIPLIER = BUILDER
            .comment("Global multiplier applied to the Appetite cost of every skill.")
            .defineInRange("balance.appetiteCostMultiplier", 1.0, 0.0, 16.0);

    private static final ModConfigSpec.IntValue MAX_APPETITE = BUILDER
            .comment("Maximum Appetite (the skill resource). Restored by eating food.")
            .defineInRange("balance.maxAppetite", 100, 10, 10000);

    private static final ModConfigSpec.IntValue APPETITE_REGEN_INTERVAL = BUILDER
            .comment("How many ticks between passive Appetite regeneration ticks.")
            .defineInRange("balance.appetiteRegenInterval", 20, 1, 1200);

    private static final ModConfigSpec.IntValue APPETITE_REGEN_AMOUNT = BUILDER
            .comment("How much Appetite is restored per regeneration tick.")
            .defineInRange("balance.appetiteRegenAmount", 1, 0, 1000);

    private static final ModConfigSpec.DoubleValue APPETITE_PER_NUTRITION = BUILDER
            .comment("Appetite restored per point of food nutrition when eating.")
            .defineInRange("balance.appetitePerNutrition", 4.0, 0.0, 200.0);

    private static final ModConfigSpec.DoubleValue CELL_XP_PER_NUTRITION = BUILDER
            .comment("Gourmet Cell evolution XP gained per point of food nutrition when eating.",
                    "See skill.CellEvolution for the level curve this feeds into.")
            .defineInRange("balance.cellXpPerNutrition", 3.0, 0.0, 200.0);

    private static final ModConfigSpec.IntValue INTIMIDATION_DRAIN_PER_SECOND = BUILDER
            .comment("Appetite drained per second while Intimidation's toggle is on.")
            .defineInRange("balance.intimidationDrainPerSecond", 4, 0, 1000);

    private static final ModConfigSpec.IntValue CAPTURE_SIMILARITY_WINDOW = BUILDER
            .comment("Intimidation only works on targets whose Capture Level is lower than the caster's by MORE than this.",
                    "Equal, similar or higher Capture Levels shrug it off.")
            .defineInRange("balance.captureSimilarityWindow", 2, 0, 1000);

    private static final ModConfigSpec.IntValue KI_DRAIN_PER_SECOND = BUILDER
            .comment("Appetite drained per second while Ki Release is on at the lowest output.",
                    "The output dial takes it up to ten times this at the player's own ceiling, whatever the ceiling is.")
            .defineInRange("balance.kiDrainPerSecond", 2, 0, 1000);

    private static final ModConfigSpec.BooleanValue STARVATION_PENALTY = BUILDER
            .comment("When Appetite hits zero, inflict Weakness/Slowness (Toriko's 'starving' state).")
            .define("balance.starvationPenalty", true);

    private static final ModConfigSpec.BooleanValue CUT_VEGETATION = BUILDER
            .comment("Let cutting skills (Knife / Flying Knife / Leg Knife) shear leaves and plants.")
            .define("world.cutVegetation", true);

    private static final ModConfigSpec.BooleanValue CELL_POWER_BREAKS_BLOCKS = BUILDER
            .comment("Let techniques break the terrain they hit, instead of only passing through foliage and glass.",
                    "What a technique can get through comes from how hard it actually hits: see skill.Hurt#impulse. Turning this off leaves the world untouched by blows.")
            .define("world.cellPowerBreaksBlocks", true);

    private static final ModConfigSpec.BooleanValue SKILLS_NEED_EMPTY_HAND = BUILDER
            .comment("Require an empty main hand for the bare-handed skills (everything but Food Immersion).")
            .define("gameplay.skillsNeedEmptyHand", false);

    private static final ModConfigSpec.BooleanValue AFFECT_PLAYERS = BUILDER
            .comment("Whether Toriko skills can damage other players.")
            .define("gameplay.affectPlayers", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static double damageMultiplier = 1.0;
    public static double cooldownMultiplier = 1.0;
    public static double appetiteCostMultiplier = 1.0;
    public static int maxAppetite = 100;
    public static int appetiteRegenInterval = 20;
    public static int appetiteRegenAmount = 1;
    public static double appetitePerNutrition = 4.0;
    public static double cellXpPerNutrition = 3.0;
    public static int intimidationDrainPerSecond = 4;
    public static int kiDrainPerSecond = 2;
    public static int captureSimilarityWindow = 2;
    public static boolean starvationPenalty = true;
    public static boolean cutVegetation = true;
    public static boolean cellPowerBreaksBlocks = true;
    public static boolean skillsNeedEmptyHand = false;
    public static boolean affectPlayers = true;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        damageMultiplier = DAMAGE_MULTIPLIER.get();
        cooldownMultiplier = COOLDOWN_MULTIPLIER.get();
        appetiteCostMultiplier = APPETITE_COST_MULTIPLIER.get();
        maxAppetite = MAX_APPETITE.get();
        appetiteRegenInterval = APPETITE_REGEN_INTERVAL.get();
        appetiteRegenAmount = APPETITE_REGEN_AMOUNT.get();
        appetitePerNutrition = APPETITE_PER_NUTRITION.get();
        cellXpPerNutrition = CELL_XP_PER_NUTRITION.get();
        intimidationDrainPerSecond = INTIMIDATION_DRAIN_PER_SECOND.get();
        kiDrainPerSecond = KI_DRAIN_PER_SECOND.get();
        captureSimilarityWindow = CAPTURE_SIMILARITY_WINDOW.get();
        starvationPenalty = STARVATION_PENALTY.get();
        cutVegetation = CUT_VEGETATION.get();
        cellPowerBreaksBlocks = CELL_POWER_BREAKS_BLOCKS.get();
        skillsNeedEmptyHand = SKILLS_NEED_EMPTY_HAND.get();
        affectPlayers = AFFECT_PLAYERS.get();
    }
}
