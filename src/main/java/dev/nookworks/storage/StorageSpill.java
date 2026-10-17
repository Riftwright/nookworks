package dev.nookworks.storage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Spills a broken Storage Barrel's contents without stalling the server.
 * <p>
 * A full Netherite barrel is tens of thousands of stacks. Spawning that many item entities in one tick freezes
 * the game, so a barrel spills at most {@link #PER_TICK} stacks at once and the rest follow, the same number
 * every tick, until it is empty. By hand, a barrel that would spill more than the configured limit cannot be
 * broken at all (StorageBreakGuard); this is what keeps the other ways a block can go - a mod's block breaker,
 * a command - from doing the same damage.
 * <p>
 * What is still waiting is saved with the level ({@link Pending}), so a server that stops or crashes part way
 * through carries on spilling when it comes back instead of losing the rest. A pile whose chunk is not loaded
 * waits for it.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class StorageSpill {
    /** Stacks a level spills in one tick, from every barrel together: ten broken at once still share 64. */
    public static final int PER_TICK = 64;
    private static final Map<ResourceKey<Level>, long[]> SPENT =
            new HashMap<>();

    private record Pile(BlockPos pos, ArrayDeque<ItemStack> stacks) {
        static final Codec<Pile> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Pile::pos),
                ItemStack.CODEC.listOf().fieldOf("stacks").forGetter(p -> List.copyOf(p.stacks()))
        ).apply(i, (pos, stacks) -> new Pile(pos, new ArrayDeque<>(stacks))));
    }

    /** The piles still coming out in one level. */
    public static final class Pending extends SavedData {
        public static final Codec<Pending> CODEC = Pile.CODEC.listOf().xmap(Pending::new, p -> p.piles);
        public static final SavedDataType<Pending> TYPE = new SavedDataType<>(Nookworks.id("storage_spill"), Pending::new, CODEC);

        private final List<Pile> piles;

        Pending() {
            this(List.of());
        }

        private Pending(List<Pile> piles) {
            this.piles = new ArrayList<>(piles);
        }

        /** Stacks still to come out. */
        public int stacks() {
            return this.piles.stream().mapToInt(p -> p.stacks().size()).sum();
        }
    }

    private static Pending pending(ServerLevel level, boolean create) {
        return create ? level.getDataStorage().computeIfAbsent(Pending.TYPE) : level.getDataStorage().get(Pending.TYPE);
    }

    /** Drops the stacks at {@code pos}: the first {@link #PER_TICK} now, any more over the following ticks. */
    public static void spill(ServerLevel level, BlockPos pos, List<ItemStack> stacks) {
        ArrayDeque<ItemStack> queue = new ArrayDeque<>(stacks);
        drop(level, pos, queue);
        if (!queue.isEmpty()) {
            Pending pending = pending(level, true);
            pending.piles.add(new Pile(pos.immutable(), queue));
            pending.setDirty();
        }
    }

    private static void drop(ServerLevel level, BlockPos pos, ArrayDeque<ItemStack> queue) {
        long[] spent = SPENT.computeIfAbsent(level.dimension(), k -> new long[] {-1, 0}); // {game time, stacks dropped}
        if (spent[0] != level.getGameTime()) {
            spent[0] = level.getGameTime();
            spent[1] = 0;
        }
        while (spent[1] < PER_TICK && !queue.isEmpty()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), queue.poll());
            spent[1]++;
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Pending pending = pending(level, false);
        if (pending == null || pending.piles.isEmpty()) {
            return;
        }
        pending.piles.removeIf(pile -> {
            if (!level.isLoaded(pile.pos())) {
                return false; // wait for the chunk rather than dropping items into one that is not there
            }
            drop(level, pile.pos(), pile.stacks());
            return pile.stacks().isEmpty();
        });
        pending.setDirty();
    }

    /** How many stacks are still waiting to come out at {@code pos}, for tests. */
    public static int pending(ServerLevel level, BlockPos pos) {
        Pending pending = pending(level, false);
        return pending == null ? 0 : pending.piles.stream().filter(p -> p.pos().equals(pos)).mapToInt(p -> p.stacks().size()).sum();
    }

    /** How many stacks are still waiting to come out in {@code level}, for tests. */
    public static int pending(ServerLevel level) {
        Pending pending = pending(level, false);
        return pending == null ? 0 : pending.piles.stream().mapToInt(p -> p.stacks().size()).sum();
    }

    private StorageSpill() {}
}
