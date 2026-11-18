package dev.nookworks.port.core;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.event.IModBusEvent;

/**
 * How the port posts the stand-ins for NeoForge 26.3's mod-bus events (port/render's {@code ModelEvent.RegisterStandalone},
 * the tint sources, the liquids' models, the special item renderers, the item conditions): to every mod's bus, as
 * NeoForge posts its own, so a mod built on this port (an add-on that names our stand-ins) gets them as ours does.
 *
 * <p>That is FML 4's {@code ModLoader.postEvent}: phase by phase (HIGHEST to LOWEST), and within a phase mod by mod in
 * load order - ours, which an add-on depends on, before the add-on at each priority. With a mod already failed to load
 * nothing is posted (FML logs "Cowardly refusing to send event"); a listener that throws is logged against its own mod
 * and ends the post there with a {@code ModLoadingException}, which FML reports as a loading error, as it does for any
 * mod-bus event. Our own listeners get each event as before, when the port posted it on our bus alone.
 */
public final class ModBusEvents {
    private ModBusEvents() {}

    public static <E extends Event & IModBusEvent> E post(E event) {
        ModLoader.postEvent(event);
        return event;
    }
}
