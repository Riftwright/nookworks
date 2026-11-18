package dev.nookworks.port.render;

import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * 26.3 NeoForge's {@code RegisterFluidModelsEvent}: a liquid's pictures and tint, by its fluids. Posted on every mod's
 * bus by the port as 1.21.1's client extensions are registered ({@link PortClientEvents}); what is registered is read by
 * {@link FluidModel#registered} - the liquid's fluid type's extensions give it to the game.
 */
public final class RegisterFluidModelsEvent extends Event implements IModBusEvent {
    RegisterFluidModelsEvent() {}

    public void register(FluidModel.Unbaked model, Fluid... fluids) {
        for (Fluid fluid : fluids) {
            FluidModel.REGISTERED.put(fluid, model);
        }
    }
}
