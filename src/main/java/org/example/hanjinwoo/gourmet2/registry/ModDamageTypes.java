package org.example.hanjinwoo.gourmet2.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.jetbrains.annotations.Nullable;

/**
 * Damage types are datapack-driven in 1.21, so these are just the keys — the definitions live in
 * {@code data/gourmet2/damage_type/} and the tag memberships in {@code data/minecraft/tags/damage_type/}.
 */
public final class ModDamageTypes {
    /** Blunt drilling damage. Armour applies normally. */
    public static final ResourceKey<DamageType> NAIL = key("nail");
    /** The part of a nail punch that screws past armour entirely (in {@code bypasses_armor}). */
    public static final ResourceKey<DamageType> NAIL_PIERCE = key("nail_pierce");
    /** Clean cuts from Knife / Fork / Leg Knife. */
    public static final ResourceKey<DamageType> CUTTING = key("cutting");
    /** Damage from bleeding inflicted by Fork. */
    public static final ResourceKey<DamageType> BLEEDING = key("bleeding");
    /** Pure fear damage from the Appetite Demon; ignores armour and enchantments. */
    public static final ResourceKey<DamageType> INTIMIDATION = key("intimidation");

    private ModDamageTypes() {}

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Gourmet2.id(path));
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> type, @Nullable Entity causing) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type),
                causing,
                causing);
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> type, @Nullable Entity direct, @Nullable Entity causing) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type),
                direct,
                causing);
    }
}
