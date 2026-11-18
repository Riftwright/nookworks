package dev.nookworks.item;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DispenserBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Oil Cake and Rice Bran act on crops as bone meal does (they are BoneMealItems), and a dispenser uses them as it uses
 * bone meal: on the crop or plant in front of it, one at a time, spitting nothing out. Vanilla registers its dispenser
 * behaviour for bone meal alone, so they were thrown out as plain items (r8). Every BoneMealItem of ours with no
 * behaviour of its own is given bone meal's - vanilla's own, so it does just what bone meal does there.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class FertilizerDispensing {
    @SubscribeEvent
    static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(FertilizerDispensing::register);
    }

    private static void register() {
        DispenseItemBehavior boneMeal = DispenserBlock.DISPENSER_REGISTRY.get(Items.BONE_MEAL);
        if (boneMeal == null) {
            return; // nothing to share: they are thrown out as anything else is
        }
        for (var entry : ModItems.ITEMS.getEntries()) {
            Item item = entry.get();
            if (item instanceof BoneMealItem && !DispenserBlock.DISPENSER_REGISTRY.containsKey(item)) {
                DispenserBlock.registerBehavior(item, boneMeal);
            }
        }
    }

    private FertilizerDispensing() {}
}
