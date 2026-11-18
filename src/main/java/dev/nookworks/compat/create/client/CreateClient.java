package dev.nookworks.compat.create.client;

import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;

import dev.nookworks.compat.create.CreateCompat;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * The Create part on the client: Create's stress line on our hand-worked machines' items (they are kinetic appliances to
 * a Create player) and the Motor's (its capacity), the shaft a Motor turns drawn as Create's own, and the Ponder scenes. The machines' axles turn with the shaft against them through CrankRenderer,
 * at the angle Create draws that shaft at ({@link ShaftLook}).
 */
public final class CreateClient {
    private CreateClient() {}

    public static void register(IEventBus modEventBus, net.neoforged.fml.ModContainer modContainer) {
        modEventBus.addListener(CreateClient::setup);
        // where a Create shaft meets our machine or wheel: a flange coupling of ours, drawn inside our block (ShaftJoins)
        modEventBus.addListener(ShaftJoins::registerModels);
        // the shaft a Motor turns is drawn as Create draws its own shafts: its renderer, and its Flywheel visual
        modEventBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) ->
                event.registerBlockEntityRenderer(CreateCompat.MOTOR_SHAFT_ENTITY.get(),
                        com.simibubi.create.content.kinetics.base.ShaftRenderer::new));
        dev.nookworks.client.render.ForeignJoins.set(new ShaftJoins());
        // the recipes came (joining, after a /reload): Create's in our machines are looked up afresh
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RecipesUpdatedEvent event) -> dev.nookworks.compat.create.CreateRecipes.forget());
    }

    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (Block machine : CreateCompat.machineBlocks()) {
                TooltipModifier.REGISTRY.register(machine.asItem(), new KineticStats(machine));
            }
            // the Motor's item shows the stress capacity it gives a Create shaft, as Create's generators' items do
            Block motor = dev.nookworks.registry.ModOil.MOTOR.get();
            TooltipModifier.REGISTRY.register(motor.asItem(), new KineticStats(motor));
            dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer.builder(CreateCompat.MOTOR_SHAFT_ENTITY.get())
                    .factory(com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual::shaft)
                    .skipVanillaRender(be -> false).apply();
        });
        net.createmod.ponder.foundation.PonderIndex.addPlugin(new DrivePonder());
    }
}
