package org.example.hanjinwoo.gourmet2.client.fx;

import mod.chloeprime.aaaparticles.api.common.AAALevel;
import mod.chloeprime.aaaparticles.api.client.effekseer.ParticleEmitter;
import mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo;
import mod.chloeprime.aaaparticles.client.registry.EffectDefinition;
import mod.chloeprime.aaaparticles.client.registry.EffectRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.fx.SkillFx;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a {@link SkillFx} into live Effekseer emitters via AAA Particles.
 *
 * <p>Each part of a composite is resolved against the client's resource pack stack, so a missing
 * effect file degrades to the next candidate — or to nothing at all — instead of throwing. That is
 * what lets the gameplay code name visuals it might not have assets for yet.
 */
public final class FxPlayer {
    /** AAA Particles reads effects from {@code assets/<ns>/effeks/<path>.efkefc}. */
    private static final String EFFEK_DIRECTORY = "effeks/";
    private static final String[] EXTENSIONS = {".efkefc", ".efkpkg"};

    /** Resolution is a resource-pack lookup, so cache both hits and misses and drop it on reload. */
    private static final Map<String, ResourceLocation> RESOLVED = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();

    private FxPlayer() {}

    /** Called on resource reload; the next lookup re-checks the pack stack. */
    public static void clearCache() {
        RESOLVED.clear();
        MISSING.clear();
    }

    /**
     * Plays a composite visual.
     *
     * @param entity   the entity to attach entity-anchored parts to, or null
     * @param pos      world position for world-anchored parts
     * @param scale    extra multiplier on every part's own scale
     * @param yawDeg   Minecraft yaw for world-anchored parts
     * @param pitchDeg Minecraft pitch for world-anchored parts
     */
    public static void play(ClientLevel level, SkillFx fx, @Nullable Entity entity,
                            Vec3 pos, float scale, float yawDeg, float pitchDeg) {
        for (FxPart part : FxLibrary.parts(fx)) {
            ResourceLocation effect = resolve(part);
            if (effect == null) {
                continue;
            }
            ParticleEmitterInfo info = ParticleEmitterInfo.create(level, effect);
            info.scale(part.scale() * scale);
            anchor(info, part, entity, pos, yawDeg, pitchDeg);
            AAALevel.addParticle(level, info);
        }
    }

    /**
     * Keeps a visual running on {@code entity}: each part is a named emitter, and any that has
     * finished (or was never started) is started again. Call it periodically; it is a no-op while
     * the emitters are still alive, so a short effect file plays continuously until
     * {@link #stopSustained} is called.
     */
    public static void keepAlive(ClientLevel level, List<FxPart> parts, Entity entity, float scale, String key) {
        for (int i = 0; i < parts.size(); i++) {
            FxPart part = parts.get(i);
            ResourceLocation effect = resolve(part);
            if (effect == null) {
                continue;
            }
            ResourceLocation name = Gourmet2.id(key + "_" + i);
            if (isRunning(effect, name)) {
                continue;
            }
            ParticleEmitterInfo info = ParticleEmitterInfo.create(level, effect, name);
            info.scale(part.scale() * scale);
            anchor(info, part, entity, entity.position(), entity.getYRot(), entity.getXRot());
            AAALevel.addParticle(level, info);
        }
    }

    /** Ends a visual started by {@link #keepAlive}. */
    public static void stopSustained(List<FxPart> parts, String key) {
        for (int i = 0; i < parts.size(); i++) {
            ResourceLocation effect = resolve(parts.get(i));
            EffectDefinition definition = effect == null ? null : EffectRegistry.get(effect);
            if (definition != null) {
                definition.getNamedEmitter(ParticleEmitter.Type.WORLD, Gourmet2.id(key + "_" + i))
                        .ifPresent(ParticleEmitter::stop);
            }
        }
    }

    private static boolean isRunning(ResourceLocation effect, ResourceLocation name) {
        EffectDefinition definition = EffectRegistry.get(effect);
        return definition != null && definition.getNamedEmitter(ParticleEmitter.Type.WORLD, name)
                .map(ParticleEmitter::exists).orElse(false);
    }

    private static void anchor(ParticleEmitterInfo info, FxPart part, @Nullable Entity entity,
                               Vec3 pos, float yawDeg, float pitchDeg) {
        // Entity-anchored parts need an entity; if it is already gone, place them in the world.
        boolean attachable = entity != null && part.anchor() != FxPart.Anchor.WORLD;
        if (!attachable) {
            info.position(pos.add(part.offset()));
            setFullRotation(info, yawDeg, pitchDeg);
            return;
        }

        info.bindOnEntity(entity);
        if (part.anchor() == FxPart.Anchor.PROJECTILE) {
            // Position only: AAA Particles adds this offset straight to the entity's lerped
            // position every frame regardless of entitySpaceRelativePosition, so plain position()
            // is enough to follow the entity without also opting into its per-frame rotation
            // tracking (which is yaw-only - see ENTITY_BODY's doc - and would flatten out pitch).
            info.position(part.offset());
            setFullRotation(info, yawDeg, pitchDeg);
            return;
        }
        if (part.anchor() == FxPart.Anchor.ENTITY_HEAD) {
            info.useEntityHeadSpace();
        }
        // ENTITY_HEAD / ENTITY_BODY: re-orients every frame from the entity's own rotation, so the
        // offset needs to be in the entity's basis rather than the world's.
        info.entitySpaceRelativePosition(part.offset());
    }

    /**
     * AAA Particles orients head-attached emitters with {@code (pitchRad, -yawRad, 0)}; every
     * other rotation set in this class follows the same convention so an aimed effect points the
     * same way regardless of how it's anchored.
     */
    private static void setFullRotation(ParticleEmitterInfo info, float yawDeg, float pitchDeg) {
        info.rotation((float) Math.toRadians(pitchDeg), (float) Math.toRadians(-yawDeg), 0.0F);
    }

    /** First candidate whose effect file is actually present, or null if none are. */
    private static @Nullable ResourceLocation resolve(FxPart part) {
        for (String candidate : part.candidates()) {
            ResourceLocation cached = RESOLVED.get(candidate);
            if (cached != null) {
                return cached;
            }
            if (MISSING.contains(candidate)) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryBuild(Gourmet2.MODID, candidate);
            if (id != null && exists(candidate)) {
                // A file can be present yet fail to load in AAA Particles (bad path inside it, wrong
                // Effekseer version); then playing it does nothing, so fall through to the next candidate.
                if (EffectRegistry.get(id) == null) {
                    Gourmet2.LOGGER.warn("Effekseer effect '{}' exists but AAA Particles did not load it; trying the next candidate", candidate);
                } else {
                    Gourmet2.LOGGER.info("Effekseer effect '{}' will be used for {}", candidate, part.candidates().get(0));
                    RESOLVED.put(candidate, id);
                    return id;
                }
            }
            MISSING.add(candidate);
        }
        return null;
    }

    private static boolean exists(String candidate) {
        var resources = Minecraft.getInstance().getResourceManager();
        for (String extension : EXTENSIONS) {
            ResourceLocation file = ResourceLocation.tryBuild(
                    Gourmet2.MODID, EFFEK_DIRECTORY + candidate + extension);
            if (file != null && resources.getResource(file).isPresent()) {
                return true;
            }
        }
        return false;
    }
}
