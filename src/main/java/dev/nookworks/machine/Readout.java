package dev.nookworks.machine;

import java.util.function.Consumer;

import net.minecraft.network.chat.Component;

/**
 * A block entity that can say what it holds and how it is getting on, a line at a time - for a tooltip that shows it
 * without opening anything (Jade's, when Jade is installed). Worked out on the server, where the whole state is, and
 * sent as text. Machines with a screen ({@link MachineInfo}) are read from that instead and need not implement this.
 */
public interface Readout {
    void readout(Consumer<Component> lines);

    /**
     * What Jade shows under the block's name: the readout, but for what Jade reads itself through NeoForge's capabilities
     * - energy, a fluid tank - which a block whose readout gives amounts leaves out here (r15: the Water Pump's water and
     * power were shown twice, once in our words and once in Jade's bars).
     */
    default void jade(Consumer<Component> lines) {
        this.readout(lines);
    }
}
