package dev.nookworks.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import dev.nookworks.Nookworks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Finds out which blocks an explosion really took.
 * <p>
 * {@code ExplosionEvent.Detonate} lists the blocks the blast reached, before anything happens to them; with
 * mob griefing off, or a block that shrugs the blast off, they are all still there afterwards. Marks on a square
 * (Fireproof Coating, placed logs) must only go with the block, so the listed squares are looked at again once the
 * tick is over, and only those whose block has changed are reported.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class ExplosionAftermath {
    private record Watch(ServerLevel level, BlockPos pos, BlockState before, BiConsumer<Level, BlockPos> ifGone) {}

    private static final List<Watch> WATCHING = new ArrayList<>();

    /** Calls {@code ifGone} for each of {@code positions} whose block is different at the end of this tick. */
    public static void whenGone(Level level, List<BlockPos> positions, BiConsumer<Level, BlockPos> ifGone) {
        if (level instanceof ServerLevel server) {
            for (BlockPos pos : positions) {
                WATCHING.add(new Watch(server, pos.immutable(), server.getBlockState(pos), ifGone));
            }
        }
    }

    @SubscribeEvent
    static void afterTick(ServerTickEvent.Post event) {
        if (WATCHING.isEmpty()) {
            return;
        }
        List<Watch> due = new ArrayList<>(WATCHING);
        WATCHING.clear();
        for (Watch watch : due) {
            // gone when the block is another, not when its state changed: a wind charge swings a door or gate open (r21)
            if (watch.level().isLoaded(watch.pos()) && !watch.level().getBlockState(watch.pos()).is(watch.before().getBlock())) {
                watch.ifGone().accept(watch.level(), watch.pos());
            }
        }
    }

    private ExplosionAftermath() {}
}
