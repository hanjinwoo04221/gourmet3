package org.example.hanjinwoo.gourmet2.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.example.hanjinwoo.gourmet2.Gourmet2;

public final class ModItems {
    public static final DeferredRegister<Item> REGISTER = DeferredRegister.create(Registries.ITEM, Gourmet2.MODID);

    /** Dull red scales, bright orange belly - matches modelCollection/red_nitro1's palette. */
    public static final DeferredHolder<Item, DeferredSpawnEggItem> LIZARDMAN_SPAWN_EGG = REGISTER.register(
            "lizardman_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.LIZARDMAN, 0xB33A2E, 0xF2A93B, new Item.Properties()));

    private ModItems() {}
}
