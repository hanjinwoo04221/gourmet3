package yesman.epicfight.world.capabilities.item.custom;

import net.minecraft.nbt.Tag;

import java.util.Optional;
import java.util.function.Function;

public record CustomData<T>(T defaultValue, Optional<Function<Tag, T>> deserializer) {
    public T get() {
        return defaultValue;
    }

    /**
     * Used to create a new custom data instance without a deserializer. Use this only for Moveset Data or if you know that only code would alter this.
     * @param defaultValue The default value of the custom data.
     * @return A new custom data instance.
     * @param <T> Any type.
     */
    public static <T> CustomData<T> of(T defaultValue) {
        return new CustomData<>(defaultValue, Optional.empty());
    }

    /**
     * Creates a new custom data instance with a deserializer. Use this for weapon data where datapacks can alter the data.
     * @param defaultValue The default value of the custom data.
     * @param deserializer The deserializer function.
     * @return A new custom data instance.
     * @param <T> Any type.
     */
    public static <T> CustomData<T> createDeserializable(T defaultValue, Function<Tag, T> deserializer)
    {
        return new CustomData<>(defaultValue, Optional.of(deserializer));
    }
}
