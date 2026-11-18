package dev.nookworks.storage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Storage Chest contents as saved data and as an item component. Counts are kept apart from the item because the
 * vanilla item codec only allows counts up to 99, while a storage slot can hold up to a billion. Read in, contents
 * are held to what a barrel can be: no slot past the biggest barrel's, and no slot named twice (on disk the first is
 * kept; over the network it is refused).
 */
public record StorageContents(List<Entry> entries) {
    public static final StorageContents EMPTY = new StorageContents(List.of());

    /**
     * {@code item} is always saved with a count of 1; the real amount is {@code count}. {@code added} is when the slot
     * was last put into, in game ticks (0: not known), so a barrel upgraded or carried as an item still sorts by it.
     */
    public record Entry(int slot, ItemStack item, int count, long added) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, StorageTier.MOST_SLOTS - 1).fieldOf("slot").forGetter(Entry::slot),
                ItemStack.CODEC.fieldOf("item").forGetter(Entry::item),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("count").forGetter(Entry::count),
                Codec.LONG.optionalFieldOf("added", 0L).forGetter(Entry::added)
        ).apply(i, Entry::new));
        /** Over the network: the sample item in vanilla's own item codec, its data held to vanilla's limits. */
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::slot,
                ItemStack.STREAM_CODEC, Entry::item,
                ByteBufCodecs.VAR_INT, Entry::count,
                ByteBufCodecs.VAR_LONG, Entry::added,
                Entry::new);

        public Entry(int slot, ItemStack item, int count) {
            this(slot, item, count, 0L);
        }
    }

    /**
     * On disk. A slot named twice (data broken or edited by hand; the game never writes one) keeps its first entry: as an
     * error it lost the whole contents, read as none and saved so (r15).
     */
    public static final Codec<StorageContents> CODEC = Entry.CODEC.listOf(0, StorageTier.MOST_SLOTS)
            .xmap(entries -> new StorageContents(onceEach(entries)), StorageContents::entries);

    private static List<Entry> onceEach(List<Entry> entries) {
        Set<Integer> seen = new HashSet<>();
        List<Entry> kept = entries.stream().filter(e -> seen.add(e.slot())).toList();
        if (kept.size() < entries.size()) {
            dev.nookworks.Nookworks.LOGGER.warn("Storage contents named {} slot(s) twice; the first of each kept", entries.size() - kept.size());
        }
        return kept;
    }
    /**
     * Over the network, entry by entry - not as one block of NBT, read without a limit as it once was so that a whole
     * Velarium barrel of written books would fit: anyone can send an item to the server (the creative slot packet is
     * read before the server knows whether its sender is in creative), and a limit lifted lets one send a block of NBT
     * that has the server set aside all the memory it likes. Now each sample item is read as vanilla reads any item, and
     * no more entries than the biggest barrel has slots, each a slot in range, named once, holding at least one.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageContents> STREAM_CODEC =
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(StorageTier.MOST_SLOTS)).map(StorageContents::checked, StorageContents::entries);

    /** Contents read off the network, refused if no barrel could hold them so. */
    private static StorageContents checked(List<Entry> entries) {
        Set<Integer> slots = new HashSet<>();
        for (Entry entry : entries) {
            if (entry.slot() < 0 || entry.slot() >= StorageTier.MOST_SLOTS || entry.count() < 1 || !slots.add(entry.slot())) {
                throw new DecoderException("Storage contents no barrel could hold: " + entry.slot() + " x" + entry.count());
            }
        }
        return new StorageContents(List.copyOf(entries));
    }

    public long totalItems() {
        long total = 0;
        for (Entry e : this.entries) {
            total += e.count();
        }
        return total;
    }

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }
}
