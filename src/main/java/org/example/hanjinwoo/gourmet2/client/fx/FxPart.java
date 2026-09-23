package org.example.hanjinwoo.gourmet2.client.fx;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * One Effekseer emitter inside a composite visual.
 *
 * @param candidates effect paths in priority order; the first one actually present is used, so a
 *                   hand-made effect can shadow the bundled sample fallback
 * @param scale      base scale for this emitter
 * @param offset     positional offset, interpreted according to {@code anchor}
 * @param anchor     what the emitter is positioned relative to
 */
public record FxPart(List<String> candidates, float scale, Vec3 offset, FxPart.Anchor anchor) {
    public enum Anchor {
        /** Fixed point in the world. Faces the yaw/pitch carried by the packet. */
        WORLD,
        /**
         * Follows the entity every frame, but only ever re-orients to its <em>body yaw</em> — pitch
         * is always treated as level. Right for anything attached to a walking/standing character,
         * whose body doesn't tilt up or down just because they look up or down.
         */
        ENTITY_BODY,
        /** Follows the entity's head, oriented to where it is looking. Offset is relative to the eyes. */
        ENTITY_HEAD,
        /**
         * Follows the entity's position every frame, but the orientation is captured once — both
         * yaw <em>and</em> pitch — at the moment the effect was triggered, and never re-read after
         * that. Right for something that flies in a straight line and doesn't rotate again once
         * launched (a thrown weapon), where {@link #ENTITY_BODY}'s yaw-only tracking would flatten
         * out any up/down angle the entity was actually launched at.
         */
        PROJECTILE
    }

    public static FxPart world(float scale, String... candidates) {
        return new FxPart(List.of(candidates), scale, Vec3.ZERO, Anchor.WORLD);
    }

    public static FxPart world(float scale, Vec3 offset, String... candidates) {
        return new FxPart(List.of(candidates), scale, offset, Anchor.WORLD);
    }

    public static FxPart onBody(float scale, Vec3 offset, String... candidates) {
        return new FxPart(List.of(candidates), scale, offset, Anchor.ENTITY_BODY);
    }

    public static FxPart onHead(float scale, Vec3 offset, String... candidates) {
        return new FxPart(List.of(candidates), scale, offset, Anchor.ENTITY_HEAD);
    }

    public static FxPart onProjectile(float scale, Vec3 offset, String... candidates) {
        return new FxPart(List.of(candidates), scale, offset, Anchor.PROJECTILE);
    }
}
