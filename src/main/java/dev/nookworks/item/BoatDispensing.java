package dev.nookworks.item;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModEntities;
import dev.nookworks.registry.ModWoods;
import net.minecraft.core.dispenser.BoatDispenseItemBehavior;
import net.minecraft.world.level.block.DispenserBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * A dispenser sets a Dawnwood Boat or Chest Boat on the water in front of it, as it does vanilla's boats (vanilla names
 * its own one by one; ours were thrown out as items - the 2026-11-24 audit of vanilla's item blocks).
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class BoatDispensing {
    private BoatDispensing() {}

    @SubscribeEvent
    static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DispenserBlock.registerBehavior(ModWoods.DAWNWOOD_BOAT.get(), new BoatDispenseItemBehavior(ModEntities.DAWNWOOD_BOAT.get()));
            DispenserBlock.registerBehavior(ModWoods.DAWNWOOD_CHEST_BOAT.get(),
                    new BoatDispenseItemBehavior(ModEntities.DAWNWOOD_CHEST_BOAT.get()));
        });
    }
}
