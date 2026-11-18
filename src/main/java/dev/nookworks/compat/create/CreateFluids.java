package dev.nookworks.compat.create;

import dev.nookworks.mercury.MercuryFluids;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** What Create's fluid blocks may take from the world: anything but mercury, which only our own iron tanks and pipes move. */
public final class CreateFluids {
    private CreateFluids() {}

    private static Object none;

    public static boolean keptOut(FluidState state) {
        return !state.isEmpty() && (state.getType().isSame(MercuryFluids.SOURCE.get()) || state.getType().isSame(MercuryFluids.FLOWING.get()));
    }

    /** The NONE of the enum Create's Hose Pulley answers in (package-private in Create), found by name once. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Object none(CallbackInfoReturnable<?> cir) {
        if (none == null) {
            try {
                Class<?> type = Class.forName("com.simibubi.create.content.fluids.transfer.FluidDrainingBehaviour$FluidBlockType");
                none = Enum.valueOf((Class) type, "NONE");
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        return none;
    }
}
