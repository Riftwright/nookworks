package dev.nookworks.compat;

import dev.nookworks.Nookworks;
import dev.nookworks.feature.Permission;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * TreeChop chops a log on every break event a player of any kind posts, and cancels the event as it does. Our
 * {@link Permission} posts break events as questions - may this machine, this rope, this load take the block? - with
 * nothing broken: beside TreeChop each such question about a log chopped it for real and came back "no" (the Laser,
 * asking for each log of a tree it was felling, chopped the trunk a notch a log and felled nothing; found with the real
 * mod, 2026-11-17). While one of our questions is being asked, TreeChop's own start-of-chop event, which its API lets a
 * mod cancel, is cancelled: the question is answered by the claims as before, and TreeChop does nothing to a block nobody
 * is breaking. A player's own break, and anything else TreeChop does, is left as TreeChop has it.
 * <p>
 * No compile-time tie to TreeChop: its event class is looked up by name, only when it is installed.
 */
@EventBusSubscriber(modid = Nookworks.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class TreeChopQuestions {
    /** TreeChop's cancellable event, posted on NeoForge's bus before it chops (api 0.19 for 1.21.1). */
    static final String START_CHOP = "ht.treechop.api.ChopEvent$StartChopEvent";

    private TreeChopQuestions() {}

    @SubscribeEvent
    static void onSetup(FMLCommonSetupEvent event) {
        if (!ModList.get().isLoaded("treechop")) {
            return;
        }
        Class<? extends Event> type = startChop();
        if (type == null) {
            Nookworks.LOGGER.warn("TreeChop is installed but has no {}: our claim questions about logs may chop them", START_CHOP);
            return;
        }
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, type, TreeChopQuestions::onStartChop);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Event> startChop() {
        try {
            Class<?> found = Class.forName(START_CHOP);
            return Event.class.isAssignableFrom(found) && ICancellableEvent.class.isAssignableFrom(found)
                    ? (Class<? extends Event>) found : null;
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static void onStartChop(Event event) {
        if (Permission.asking() && event instanceof ICancellableEvent cancellable) {
            cancellable.setCanceled(true);
        }
    }
}
