package dev.nookworks.compat.create;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;

import dev.nookworks.machine.Readout;
import net.minecraft.network.chat.MutableComponent;

/**
 * A Display Link on a block of ours that tells how it is getting on - a machine's status, a tank's liquid, a pot's
 * dish (our {@link Readout}, what Jade shows) - sends those lines to a Display Board, a Nixie Tube or a sign, as Create's
 * own sources send theirs. Listed for every block of ours whose block entity has a readout (CreateCompat).
 */
public final class ReadoutDisplaySource extends DisplaySource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        if (!(context.getSourceBlockEntity() instanceof Readout readout)) {
            return EMPTY;
        }
        List<MutableComponent> lines = new ArrayList<>();
        readout.readout(line -> lines.add(line.copy()));
        return lines.isEmpty() ? EMPTY : lines;
    }

    @Override
    protected String getTranslationKey() {
        return "readout";
    }
}
