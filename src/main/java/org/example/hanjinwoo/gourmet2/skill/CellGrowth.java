package org.example.hanjinwoo.gourmet2.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.jetbrains.annotations.Nullable;

/**
 * What evolving does to the body itself. Every Gourmet Cell level raises a couple of the caster's attributes
 * at random — one level a heartier chest, the next quicker feet, never the same twice — so a well-fed player
 * grows into something visibly unlike an ordinary one without ever being handed a menu to optimise.
 *
 * <p>Two rules keep that readable. One level always hands out the same <i>amount</i> of growth
 * ({@link #HEADROOM_PER_LEVEL}) no matter how it is split up, so a level is worth a level however the dice
 * fall; and what a share of it is worth in any one attribute is measured against that attribute's own scale
 * in {@link #BODY}. The parts that would be nonsense without a limit — speed, knockback resistance, armour —
 * <i>converge</i>: their share is taken out of the gap still left in them, so they come up on their scale and
 * never pass it, while the health and the strength of a body that keeps eating never stop. And as those parts
 * fill, the share they no longer take is handed to the two that can still use it, so a level is worth exactly
 * the same amount at level 300 as it was at level 3.
 *
 * <p>The draw is seeded off the player's id, so a body is not re-rolled by logging in: level 12 always gives
 * that player level 12's growth, whether it is the first time they reach it or the fiftieth time the number is
 * worked out. That is what lets the whole body be rebuilt from the level alone ({@link #apply}) without
 * anything extra written down to remember how it got there — and it means a player who was already deep into
 * their Gourmet Cells gets the body they had earned the first time they log in.
 *
 * <p>The totals themselves live on the attributes, as permanent modifiers — the same place the game keeps
 * every other body stat. Permanent, not transient, because transient modifiers are not written out at all, and
 * this body has to survive the save its session ends with.
 */
public final class CellGrowth {
    /** How much of the body's remaining headroom one level is worth, in total. */
    private static final double HEADROOM_PER_LEVEL = 0.10;
    /** At most this many attributes grow on any one level. */
    private static final int MAX_GROWTHS_PER_LEVEL = 2;
    /** Any attribute this close to its ceiling counts as full, so nothing divides by a sliver. */
    private static final double FULL = 1.0E-3;

    /**
     * The body a Gourmet Cell grows into. The scale is what a whole level's worth of growth is worth in that
     * attribute, and doubles as the value a converging part of the body stops at — 40 health is both a full
     * share and the most health growing will ever hand over. Health and strength do not converge at all: a
     * body that keeps eating keeps getting bigger and hits harder, for as long as there are levels to earn.
     * Everything else tops out well short of making the rest of the world irrelevant. A part that has reached
     * its scale drops out of the draw, and the rest divide that level's growth between them.
     */
    private static final List<Growth> BODY = List.of(
            new Growth(Attributes.MAX_HEALTH, Gourmet2.id("cell_health"),
                    AttributeModifier.Operation.ADD_VALUE, 40.0, false),
            new Growth(Attributes.ATTACK_DAMAGE, Gourmet2.id("cell_attack"),
                    AttributeModifier.Operation.ADD_VALUE, 15.0, false),
            new Growth(Attributes.ARMOR, Gourmet2.id("cell_armor"),
                    AttributeModifier.Operation.ADD_VALUE, 12.0, true),
            new Growth(Attributes.ARMOR_TOUGHNESS, Gourmet2.id("cell_toughness"),
                    AttributeModifier.Operation.ADD_VALUE, 8.0, true),
            new Growth(Attributes.MOVEMENT_SPEED, Gourmet2.id("cell_speed"),
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.35, true),
            new Growth(Attributes.KNOCKBACK_RESISTANCE, Gourmet2.id("cell_knockback"),
                    AttributeModifier.Operation.ADD_VALUE, 0.5, true));

    private CellGrowth() {}

    /**
     * One part of the body that growing can raise: which attribute, the id its modifier carries (so the total
     * can be found and replaced), how it stacks on the base value, what a level's worth of it is, and whether
     * it tops out there or keeps going.
     */
    private record Growth(Holder<Attribute> attribute, ResourceLocation id, AttributeModifier.Operation operation,
                          double scale, boolean converges) {}

    /**
     * Rebuilds the body {@code level} levels of Gourmet Cells grow into. Idempotent, so it is safe — and
     * right — to call whenever the level may have moved or the body may have been left behind: a level up, a
     * login, a respawn, a walk through a portal.
     */
    public static void apply(ServerPlayer player, int level) {
        double[] grown = bodyAt(player.getUUID(), level);
        for (int i = 0; i < BODY.size(); i++) {
            AttributeInstance instance = player.getAttribute(BODY.get(i).attribute());
            if (instance != null) {
                set(instance, BODY.get(i), grown[i]);
            }
        }
    }

    /**
     * Brings the body up to {@code toLevel} and works out what the step up from {@code fromLevel} added, for
     * telling the player — they never chose any of this, so the game should say what it gave them.
     *
     * @return one line naming what was raised, or null if that step raised nothing
     */
    public static @Nullable Component grow(ServerPlayer player, int fromLevel, int toLevel) {
        double[] before = bodyAt(player.getUUID(), fromLevel);
        double[] after = bodyAt(player.getUUID(), toLevel);
        apply(player, toLevel);
        double[] gained = new double[BODY.size()];
        boolean any = false;
        for (int i = 0; i < BODY.size(); i++) {
            gained[i] = after[i] - before[i];
            any |= gained[i] > 0.0;
        }
        return any ? describe(gained) : null;
    }

    /** The body {@code level} levels of Gourmet Cells grow into, replaying the same draw every time. */
    private static double[] bodyAt(UUID player, int level) {
        double[] value = new double[BODY.size()];
        RandomSource random = RandomSource.create(player.getMostSignificantBits() ^ player.getLeastSignificantBits());
        for (int i = 0; i < level; i++) {
            growOnce(value, random);
        }
        return value;
    }

    /** One level of growth, drawn at random onto {@code value}. */
    private static boolean growOnce(double[] value, RandomSource random) {
        // What is left to grow into, per attribute: the share of its ceiling it has not reached yet. A part
        // already at its ceiling is not in the draw at all, so every level is spent on something that can use it.
        List<Integer> roomy = new ArrayList<>();
        double[] room = new double[BODY.size()];
        for (int i = 0; i < BODY.size(); i++) {
            Growth growth = BODY.get(i);
            // A part that converges is only as open as the gap still left in it; one that does not is always
            // wide open, which is what lets it take up the share the others have stopped using.
            double open = growth.converges() ? (growth.scale() - value[i]) / growth.scale() : 1.0;
            if (open > FULL) {
                room[i] = open;
                roomy.add(i);
            }
        }
        if (roomy.isEmpty()) {
            return false;
        }

        // Which parts grow this level, and how much of it each takes, are both random...
        int picks = Math.min(roomy.size(), 1 + random.nextInt(MAX_GROWTHS_PER_LEVEL));
        List<Integer> chosen = new ArrayList<>();
        while (chosen.size() < picks) {
            int index = roomy.get(random.nextInt(roomy.size()));
            if (!chosen.contains(index)) {
                chosen.add(index);
            }
        }
        double[] weight = new double[picks];
        double weightTotal = 0.0;
        for (int i = 0; i < picks; i++) {
            weight[i] = 0.25 + random.nextDouble();
            weightTotal += weight[i];
        }
        // ...but the total handed out is not. Each part's slice is weighed against how much room the drawn
        // parts have to take it, so a level's growth is always the same size and a part that is nearly full
        // simply gets less of it — which is what keeps the whole body converging instead of running away.
        double spread = 0.0;
        for (int i = 0; i < picks; i++) {
            spread += weight[i] / weightTotal * room[chosen.get(i)];
        }
        if (spread <= 0.0) {
            return false;
        }

        boolean any = false;
        for (int i = 0; i < picks; i++) {
            int index = chosen.get(i);
            Growth growth = BODY.get(index);
            double share = HEADROOM_PER_LEVEL * (weight[i] / weightTotal) * room[index] / spread;
            double raised = value[index] + share * growth.scale();
            if (growth.converges()) {
                raised = Math.min(growth.scale(), raised);
            }
            any |= raised > value[index];
            value[index] = raised;
        }
        return any;
    }

    private static void set(AttributeInstance instance, Growth growth, double value) {
        instance.removeModifier(growth.id());
        instance.addPermanentModifier(new AttributeModifier(growth.id(), value, growth.operation()));
    }

    /** Names what grew, in the game's own words for those attributes. */
    private static Component describe(double[] gained) {
        MutableComponent line = Component.empty();
        boolean first = true;
        for (int i = 0; i < BODY.size(); i++) {
            if (gained[i] <= 0.0) {
                continue;
            }
            double amount = gained[i];
            String shown = BODY.get(i).operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                    ? String.format(Locale.ROOT, "%+.1f%%", amount * 100.0)
                    : String.format(Locale.ROOT, "%+.1f", amount);
            line.append(first ? Component.empty() : Component.literal(", "))
                    .append(Component.translatable(BODY.get(i).attribute().value().getDescriptionId()))
                    .append(Component.literal(" " + shown));
            first = false;
        }
        return Component.translatable("message." + Gourmet2.MODID + ".cell_growth", line);
    }
}
