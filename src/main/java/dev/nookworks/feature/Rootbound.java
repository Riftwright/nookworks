package dev.nookworks.feature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import dev.nookworks.compat.FeatureGate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.TriState;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * Rootbound (植え戻し), an axe enchantment (data/nookworks/enchantment/rootbound.json; the user's spec of 2026-10-22,
 * DECISIONS "Rootbound", from a player's request): a tree that tree felling brings down from its bottom log with such an axe
 * is planted again where its trunk stood, the saplings taken from the felling player's inventory.
 * <p>
 * The kind is told by the tree's crown (its leaves, a fungus's wart blocks), through the block data map
 * {@link #REPLANTS} - with no entry, a {@code <wood>_leaves} gives its namespace's {@code <wood>_sapling} if there is
 * one. The trunk's foot says how many: one for a trunk a block wide; four in a 2x2 for one two wide; for one three wide
 * (the Nether's huge fungi grow so; another mod's trees may), what the crown's entry says - one in the middle, nine in
 * a 3x3, or none. Any other foot is a tree whose saplings would have to be laid out in a way we do not know: none.
 * Nothing is planted at all unless every place is ground a sapling takes, the log cut was the bottom one (a tree left
 * floating, or cut higher up, is not), the player carries enough, the whole tree came down, and claims let the player
 * set the saplings down. Planted a tick after the felling, once the tree is gone. The axe strips no logs
 * ({@link #onRightClickBlock}).
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class Rootbound {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT, Nookworks.id("rootbound"));

    /** For a trunk three wide: one sapling in the middle, nine in a square, or none. */
    public enum ThreeWide implements StringRepresentable {
        NONE("none"), MIDDLE("middle"), NINE("nine");

        private final String name;

        ThreeWide(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    /**
     * For a trunk whose foot spreads past where its saplings stood (stumps, roots or buttresses at ground level): from
     * one sapling, or from four in a 2x2 - their places found from the column rising highest from the foot.
     */
    public enum Spread implements StringRepresentable {
        NONE("none"), ONE("one"), FOUR("four");

        private final String name;

        Spread(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    /**
     * What a crown block (leaves, a wart block) is replanted with, and how its tree's trunk stands:
     * <ul>
     * <li>{@code three_wide}: a trunk three wide ({@link ThreeWide}).</li>
     * <li>{@code four_only}: the tree grows only from four saplings in a 2x2 (a dark oak), so a trunk read as one wide
     * gets none - one sapling of it would never grow.</li>
     * <li>{@code spread}: a foot spread past its saplings ({@link Spread}). {@code one}: the tree grows from one sapling
     * whatever its foot (Ecologics' coconut, an L of logs; Integrated Dynamics' menril, a plus) - one where the column
     * rising highest stands. {@code four}: grown from four in a 2x2, the foot four wide or more (Biomes O' Plenty's
     * big redwoods) - the four in a 2x2 with that column at its north-west, where vanilla grows a 2x2's tree from.</li>
     * <li>{@code outvotes}: wherever it shows on a tree, the tree is its kind, however many plainer leaves it carries
     * (Biomes O' Plenty's flowering oak, its crown mostly oak leaves).</li>
     * <li>{@code preferred}: saplings planted instead when the player carries enough of one, in order - another mod's
     * that the tree drops in place of {@code sapling} (Wilder Wild's red and yellow poplar saplings, from vanilla's
     * poplar leaves); ones not in the game are passed over.</li>
     * </ul>
     * The saplings are kept as ids and looked up when planting: NeoForge reads a data map file whole, so one entry
     * naming an item the game lacks (another mod's, renamed in a later version) would otherwise drop every entry.
     */
    public record Replant(Identifier sapling, ThreeWide threeWide, boolean fourOnly, Spread spread, boolean outvotes,
            List<Identifier> preferred) {
        public static final Codec<Replant> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("sapling").forGetter(Replant::sapling),
                StringRepresentable.fromEnum(ThreeWide::values).optionalFieldOf("three_wide", ThreeWide.NONE).forGetter(Replant::threeWide),
                Codec.BOOL.optionalFieldOf("four_only", false).forGetter(Replant::fourOnly),
                StringRepresentable.fromEnum(Spread::values).optionalFieldOf("spread", Spread.NONE).forGetter(Replant::spread),
                Codec.BOOL.optionalFieldOf("outvotes", false).forGetter(Replant::outvotes),
                Identifier.CODEC.listOf().optionalFieldOf("preferred", List.of()).forGetter(Replant::preferred))
                .apply(i, Replant::new));

        /** A plain kind: one sapling for a trunk one wide, four for a 2x2. */
        public Replant(Identifier sapling) {
            this(sapling, ThreeWide.NONE, false, Spread.NONE, false, List.of());
        }

        /** The sapling to plant {@code count} of: the first preferred one the player carries enough of, else the plain one. */
        public @Nullable BlockItem chosen(ServerPlayer player, int count) {
            List<Item> kinds = new ArrayList<>();
            for (Identifier id : this.preferred) {
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(kinds::add);
            }
            BuiltInRegistries.ITEM.getOptional(this.sapling).ifPresent(kinds::add);
            for (Item kind : kinds) {
                if (kind instanceof BlockItem item && (player.getAbilities().instabuild || player.getInventory().countItem(item) >= count)) {
                    return item;
                }
            }
            return null;
        }
    }

    /** Crown block -> what replants its tree (data/nookworks/data_maps/block/replants.json). */
    public static final DataMapType<Block, Replant> REPLANTS = DataMapType.builder(Nookworks.id("replants"), Registries.BLOCK, Replant.CODEC).build();

    /** A felling's saplings to set down once the tree is gone. */
    private record Plan(ServerLevel level, ServerPlayer player, Item sapling, BlockState planted, List<BlockPos> at, Set<BlockPos> tree,
            Block log, long due) {}

    /** Server thread only. */
    private static final List<Plan> PENDING = new ArrayList<>();

    private Rootbound() {}

    public static boolean has(ItemStack tool) {
        return tool.getEnchantments().keySet().stream().anyMatch(holder -> holder.is(KEY));
    }

    /**
     * Whether Rootbound is offered by the enchanting table and the trades: only while Nookworks' tree felling works
     * (switched on, and not standing down for another mod's), there being nothing else for it to do (the user,
     * 2026-10-22). Before the world's settings are read, as offered (nothing asks then).
     */
    public static boolean offered() {
        try {
            return FeatureGate.timber();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /** Whether every enchantment of ours is on offer now: Rootbound ({@link #offered}) and Earthmover ({@link Earthmover#offered}). */
    public static boolean allOffered() {
        return offered() && Earthmover.offered();
    }

    /** Whether {@code holder} may be offered now: Rootbound by {@link #offered}, Earthmover by its own; the rest always. */
    public static boolean offerable(Holder<Enchantment> holder) {
        return holder.is(KEY) ? offered() : !holder.is(Earthmover.KEY) || Earthmover.offered();
    }

    /**
     * Called by tree felling as it fells, before any log is broken (the crown is still there to tell the kind): the
     * saplings to plant once the tree, {@code logs} with {@code cut} among them, is down - or nothing.
     */
    static void felling(ServerLevel level, ServerPlayer player, ItemStack tool, BlockPos cut, List<BlockPos> logs) {
        if (!has(tool)) {
            return;
        }
        if (logs.size() >= NookworksConfig.TIMBER_MAX_BLOCKS.getAsInt()) {
            return; // felling stops at its limit: the rest of the tree would stay up, over the saplings
        }
        BlockState cutState = level.getBlockState(cut);
        if (level.getBlockState(cut.below()).is(BlockTags.LOGS)) {
            return; // cut higher up than its bottom log (felling takes only the logs from the cut up)
        }
        Set<BlockPos> tree = new HashSet<>(logs);
        tree.add(cut.immutable());
        Replant replant = kind(level, tree);
        if (replant == null) {
            return;
        }
        List<BlockPos> at = places(tree, cut, replant);
        BlockItem item = at.isEmpty() ? null : replant.chosen(player, at.size());
        if (item == null) {
            return;
        }
        BlockState planted = item.getBlock().defaultBlockState();
        for (BlockPos pos : at) {
            // the ground under each place takes the sapling: a tree left floating has none
            if (tree.contains(pos.below()) || !planted.canSurvive(level, pos) || !level.getFluidState(pos).isEmpty()) {
                return;
            }
        }
        PENDING.add(new Plan(level, player, item, planted, at, tree, cutState.getBlock(), level.getGameTime() + 1));
    }

    /** The replant the tree's crown votes for: each crown block touching a log one vote, an outvoting kind's over the rest. */
    private static @Nullable Replant kind(ServerLevel level, Set<BlockPos> tree) {
        Map<Block, Integer> votes = new HashMap<>();
        Set<BlockPos> counted = new HashSet<>();
        for (BlockPos log : tree) {
            for (BlockPos near : BlockPos.betweenClosed(log.offset(-1, -1, -1), log.offset(1, 1, 1))) {
                if (!tree.contains(near) && level.isLoaded(near) && counted.add(near.immutable())) {
                    BlockState state = level.getBlockState(near);
                    if (ChainBreaking.isNaturalCrown(state) && replantOf(state.getBlock()) != null) {
                        votes.merge(state.getBlock(), 1, Integer::sum);
                    }
                }
            }
        }
        return votes.entrySet().stream()
                .max(Comparator.comparing((Map.Entry<Block, Integer> entry) -> replantOf(entry.getKey()).outvotes())
                        .thenComparing(Map.Entry::getValue)
                        .thenComparing(entry -> BuiltInRegistries.BLOCK.getKey(entry.getKey()).toString(), Comparator.reverseOrder()))
                .map(entry -> replantOf(entry.getKey())).orElse(null);
    }

    /** What {@code crown} replants with: its {@link #REPLANTS} entry, else {@code <wood>_leaves}' {@code <wood>_sapling}. */
    public static @Nullable Replant replantOf(Block crown) {
        Replant entry = crown.builtInRegistryHolder().getData(REPLANTS);
        if (entry != null) {
            return entry;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(crown);
        if (!id.getPath().endsWith("_leaves")) {
            return null;
        }
        Identifier sapling = id.withPath(id.getPath().substring(0, id.getPath().length() - "_leaves".length()) + "_sapling");
        return BuiltInRegistries.ITEM.getOptional(sapling).filter(item -> item instanceof BlockItem)
                .map(item -> new Replant(sapling)).orElse(null);
    }

    /**
     * Where the saplings go, at the cut's height: the trunk's foot, read from the tree's two lowest layers - so a 2x2
     * with one bottom log broken by hand before still reads as a 2x2, and gets its four where they grew. A trunk that
     * leans or steps aside right above its foot (Biomes O' Plenty's palms) is read from its bottom layer alone.
     */
    public static List<BlockPos> places(Set<BlockPos> tree, BlockPos cut, Replant kind) {
        List<BlockPos> at = foot(tree, cut, kind, 2);
        return at.isEmpty() ? foot(tree, cut, kind, 1) : at;
    }

    /** The places for the foot the tree's {@code layers} lowest layers show, or none for a foot we do not know. */
    private static List<BlockPos> foot(Set<BlockPos> tree, BlockPos cut, Replant kind, int layers) {
        Set<Long> columns = new HashSet<>();
        int minX = cut.getX(), maxX = cut.getX(), minZ = cut.getZ(), maxZ = cut.getZ();
        for (BlockPos log : tree) {
            if (log.getY() >= cut.getY() && log.getY() < cut.getY() + layers) {
                columns.add(BlockPos.asLong(log.getX(), 0, log.getZ()));
                minX = Math.min(minX, log.getX());
                maxX = Math.max(maxX, log.getX());
                minZ = Math.min(minZ, log.getZ());
                maxZ = Math.max(maxZ, log.getZ());
            }
        }
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        // a spread foot's edge is uneven: it need not be square
        if (kind.spread() == Spread.ONE && columns.size() > 1) {
            BlockPos highest = highest(tree, columns);
            return highest == null ? List.of() : List.of(highest);
        }
        if (kind.spread() == Spread.FOUR && Math.max(width, depth) >= 4) {
            BlockPos highest = highest(tree, columns);
            return highest == null ? List.of() : List.of(highest, highest.east(), highest.south(), highest.east().south());
        }
        if (width != depth) {
            return List.of();
        }
        List<BlockPos> at = new ArrayList<>();
        if (width == 1 && kind.fourOnly()) {
            return List.of();
        }
        if (width == 1 || width == 2 && columns.size() == 4) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    at.add(new BlockPos(x, cut.getY(), z));
                }
            }
            return at;
        }
        if (width == 3) {
            int cx = minX + 1, cz = minZ + 1;
            boolean plus = columns.contains(BlockPos.asLong(cx, 0, cz)) && columns.contains(BlockPos.asLong(cx - 1, 0, cz))
                    && columns.contains(BlockPos.asLong(cx + 1, 0, cz)) && columns.contains(BlockPos.asLong(cx, 0, cz - 1))
                    && columns.contains(BlockPos.asLong(cx, 0, cz + 1));
            if (kind.threeWide() == ThreeWide.MIDDLE && plus) {
                return List.of(new BlockPos(cx, cut.getY(), cz)); // a huge fungus's stem, its corners left out or not
            }
            if (kind.threeWide() == ThreeWide.NINE && columns.size() == 9) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        at.add(new BlockPos(x, cut.getY(), z));
                    }
                }
                return at;
            }
        }
        return List.of(); // a foot we do not know how to plant
    }

    /**
     * The foot of the column among {@code columns} rising highest without a break from its lowest log - the trunk a
     * spread foot's stumps and buttresses stand round - or null if two rise as high (we could not tell which it is).
     */
    private static @Nullable BlockPos highest(Set<BlockPos> tree, Set<Long> columns) {
        BlockPos best = null;
        int bestRise = 0;
        boolean tie = false;
        for (long column : columns) {
            int x = BlockPos.getX(column), z = BlockPos.getZ(column);
            int lowest = Integer.MAX_VALUE;
            for (BlockPos log : tree) {
                if (log.getX() == x && log.getZ() == z) {
                    lowest = Math.min(lowest, log.getY());
                }
            }
            int rise = 0;
            while (tree.contains(new BlockPos(x, lowest + rise, z))) {
                rise++;
            }
            if (rise > bestRise) {
                best = new BlockPos(x, lowest, z);
                bestRise = rise;
                tie = false;
            } else if (rise == bestRise) {
                tie = true;
            }
        }
        return tie ? null : best;
    }

    /**
     * A Rootbound axe strips no logs (the user, 2026-10-22): holding use with bone meal in the other hand grows the
     * sapling just planted, and the moment it grows the axe would strip the new tree's bottom log - which felling then
     * takes alone, the tree left standing and nothing replanted. So a log right-clicked with it is left to the other
     * hand. Both sides (the client must not show a strip the server refuses); copper's scraping and waxing go on.
     */
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (has(event.getItemStack()) && event.getLevel().getBlockState(event.getPos()).is(BlockTags.LOGS)) {
            event.setUseItem(TriState.FALSE);
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        List<Plan> due = new ArrayList<>();
        for (Iterator<Plan> it = PENDING.iterator(); it.hasNext(); ) {
            Plan plan = it.next();
            if (plan.level().getGameTime() >= plan.due()) {
                it.remove();
                due.add(plan);
            }
        }
        due.forEach(Rootbound::plant);
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    /** The tree down, the places free and the ground still taking them, claims letting the player: every sapling, or none. */
    private static void plant(Plan plan) {
        ServerLevel level = plan.level();
        ServerPlayer player = plan.player();
        if (player.isRemoved() || player.level() != level) {
            return; // gone (left, died, through a portal) in the tick since
        }
        for (BlockPos log : plan.tree()) {
            if (level.isLoaded(log) && level.getBlockState(log).is(plan.log())) {
                return; // not all of it came down (the axe gave out halfway): a sapling under a standing trunk is no replanting
            }
        }
        for (BlockPos pos : plan.at()) {
            // a fluid run in since (canBeReplaced is true for water) would be wiped out, or wash the sapling off
            if (!level.isLoaded(pos) || !level.getBlockState(pos).canBeReplaced() || !level.getFluidState(pos).isEmpty()
                    || !plan.planted().canSurvive(level, pos) || !Permission.mayPlace(level, pos, Direction.UP, player, plan.planted())) {
                return;
            }
        }
        if (!player.getAbilities().instabuild) {
            if (player.getInventory().countItem(plan.sapling()) < plan.at().size()) {
                return;
            }
            take(player, plan.sapling(), plan.at().size());
        }
        for (BlockPos pos : plan.at()) {
            level.setBlockAndUpdate(pos, plan.planted());
        }
        SoundType sound = plan.planted().getSoundType(level, plan.at().getFirst(), player);
        BlockPos first = plan.at().getFirst();
        level.playSound(null, first, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
    }

    /** Takes {@code count} of {@code item} out of the player's inventory, the plain ones first. */
    private static void take(ServerPlayer player, Item item, int count) {
        var inventory = player.getInventory();
        for (int pass = 0; pass < 2 && count > 0; pass++) {
            for (int slot = 0; slot < inventory.getContainerSize() && count > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.is(item) && (pass == 1 || stack.getComponentsPatch().isEmpty())) {
                    int n = Math.min(count, stack.getCount());
                    stack.shrink(n);
                    count -= n;
                }
            }
        }
    }

    @EventBusSubscriber(modid = Nookworks.MODID)
    static final class Registration {
        @SubscribeEvent
        static void register(RegisterDataMapTypesEvent event) {
            event.register(REPLANTS);
        }

        private Registration() {}
    }
}
