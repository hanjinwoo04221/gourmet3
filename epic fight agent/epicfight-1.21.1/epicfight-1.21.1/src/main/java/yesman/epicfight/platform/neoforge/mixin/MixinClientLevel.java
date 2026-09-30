package yesman.epicfight.platform.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yesman.epicfight.client.world.util.FakeLevel;

@Mixin(value = ClientLevel.class)
public abstract class MixinClientLevel {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"))
    private Event epicfight$init(IEventBus instance, Event e, Operation<ClientLevel> operation) {
        if (((ClientLevel)(Object)this) instanceof FakeLevel) {
            // Prevents crashes that can occur when joining the world with certain mods.
            // See: https://github.com/Epic-Fight/epicfight/issues/2164
            return null;
        }

        return instance.post(e);
    }
}
