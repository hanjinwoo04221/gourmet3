package org.example.hanjinwoo.gourmet2.client.gui;

import org.example.hanjinwoo.gourmet2.skill.SkillTree;
import org.example.hanjinwoo.gourmet2.skill.SkillType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Where each skill sits on the skill tree screen. Every tree grows out of the one origin along its own spoke, one
 * skill hanging off the one before it, and where a skill leads to several the branches fan out to either side.
 * Positions are in layout units around the origin; the screen turns them into pixels.
 */
final class SkillTreeLayout {
    /** A skill on the canvas, and the skill it grows out of (null for the first skill of its tree). */
    record Node(SkillType skill, @Nullable SkillType parent, double x, double y) {}

    /** How far the first skill of a branch is from the origin, and how far each further step is. */
    private static final double FIRST_RADIUS = 78.0;
    private static final double STEP = 58.0;
    private static final double SPREAD = 54.0;

    private static final Map<SkillType, SkillType> PARENT = new EnumMap<>(SkillType.class);

    static {
        // Technique: the cutlery, from the fist outward.
        PARENT.put(SkillType.FORK, SkillType.NAIL_PUNCH);
        PARENT.put(SkillType.KNIFE, SkillType.FORK);
        PARENT.put(SkillType.LEG_KNIFE, SkillType.KNIFE);
        PARENT.put(SkillType.FLYING_KNIFE, SkillType.KNIFE);
        PARENT.put(SkillType.NAIL_GUN, SkillType.NAIL_PUNCH);
        PARENT.put(SkillType.FLYING_FORK, SkillType.NAIL_GUN);
        // Demon.
        PARENT.put(SkillType.DEMON_FORM, SkillType.INTIMIDATION);
        // Ichiryu: the two chopsticks, then the fist branch that forks into the flurry line and the single line.
        PARENT.put(SkillType.CHOPSTICK_STAB, SkillType.CHOPSTICKS);
        PARENT.put(SkillType.CHOPSTICK_FIST, SkillType.CHOPSTICKS);
        PARENT.put(SkillType.CHOPSTICK_FLURRY, SkillType.CHOPSTICK_FIST);
        PARENT.put(SkillType.CHOPSTICK_ASURA, SkillType.CHOPSTICK_FLURRY);
        PARENT.put(SkillType.CHOPSTICK_SINGLE, SkillType.CHOPSTICK_FIST);
        PARENT.put(SkillType.MINORITY_WORLD, SkillType.CHOPSTICK_SINGLE);
    }

    private SkillTreeLayout() {}

    static List<Node> build() {
        List<Node> nodes = new ArrayList<>();
        for (SkillTree tree : SkillTree.VALUES) {
            double angle = Math.toRadians(-90.0 + 360.0 * tree.ordinal() / SkillTree.VALUES.length);
            for (SkillType skill : tree.skills()) {
                if (PARENT.get(skill) == null) {
                    place(nodes, skill, null, angle, 0, 0.0);
                }
            }
        }
        return nodes;
    }

    private static void place(List<Node> nodes, SkillType skill, @Nullable SkillType parent, double angle,
                              int depth, double lateral) {
        double radius = FIRST_RADIUS + STEP * depth;
        double dx = Math.cos(angle);
        double dy = Math.sin(angle);
        nodes.add(new Node(skill, parent, dx * radius - dy * lateral, dy * radius + dx * lateral));

        List<SkillType> children = new ArrayList<>();
        for (Map.Entry<SkillType, SkillType> entry : PARENT.entrySet()) {
            if (entry.getValue() == skill) {
                children.add(entry.getKey());
            }
        }
        children.sort(Comparator.comparingInt(SkillType::unlockLevel));
        for (int i = 0; i < children.size(); i++) {
            double offset = (i - (children.size() - 1) / 2.0) * SPREAD;
            place(nodes, children.get(i), skill, angle, depth + 1, lateral + offset);
        }
    }

    /** The unit direction of a tree's spoke, for the branch label. */
    static double[] spoke(SkillTree tree) {
        double angle = Math.toRadians(-90.0 + 360.0 * tree.ordinal() / SkillTree.VALUES.length);
        return new double[] {Math.cos(angle), Math.sin(angle)};
    }
}
