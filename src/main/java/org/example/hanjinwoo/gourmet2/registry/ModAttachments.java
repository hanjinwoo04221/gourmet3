package org.example.hanjinwoo.gourmet2.registry;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.example.hanjinwoo.gourmet2.Gourmet2;
import org.example.hanjinwoo.gourmet2.data.TorikoData;

import java.util.function.Supplier;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Gourmet2.MODID);

    /** Appetite, cooldowns and Gourmet Cell state. Survives death so skills aren't lost on respawn. */
    public static final Supplier<AttachmentType<TorikoData>> TORIKO_DATA = REGISTER.register(
            "toriko_data",
            () -> AttachmentType.serializable(TorikoData::new).copyOnDeath().build());

    private ModAttachments() {}

    public static TorikoData of(Player player) {
        return player.getData(TORIKO_DATA);
    }
}
