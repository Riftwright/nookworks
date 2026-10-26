package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.nookworks.NookworksConfig;
import dev.nookworks.feature.Rootbound;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/**
 * Rootbound (植え戻し, 2026-10-22; DECISIONS "Rootbound"): a tree felled with it is planted again where its trunk stood,
 * from the player's saplings - one for a trunk a block wide, four for a 2x2 (one whose bottom log was broken by hand
 * before as well), a huge fungus's one in the middle; none on ground that takes no sapling, for a tree left floating, or
 * with too few saplings; offered only while tree felling works.
 */
final class RootboundTests {
    private RootboundTests() {}

    static void register() {
        test("rootbound_replants_a_felled_tree", 60, RootboundTests::oak);
        test("rootbound_plants_none_with_too_few_saplings", 60, RootboundTests::tooFew);
        test("rootbound_replants_a_2x2_tree_where_it_grew", 60, RootboundTests::darkOak);
        test("rootbound_plants_nothing_for_a_floating_tree", 60, RootboundTests::floating);
        test("rootbound_replants_a_fungus_on_nylium_only", 60, RootboundTests::fungus);
        test("rootbound_reads_a_trunks_foot", RootboundTests::footprints);
        test("rootbound_is_offered_only_while_tree_felling_works", RootboundTests::offered);
        test("rootbound_plants_a_preferred_sapling_when_carried", RootboundTests::preferred);
        test("rootbound_plants_none_for_a_tree_cut_above_its_bottom_log", 60, RootboundTests::cutHigh);
        test("rootbound_plants_none_when_felling_stops_at_its_limit", 60, RootboundTests::limit);
        test("rootbound_plants_none_into_water_run_in", 60, RootboundTests::water);
        test("rootbound_is_traded_only_while_tree_felling_works", RootboundTests::traded);
        test("rootbound_axe_strips_no_logs", RootboundTests::noStripping);
    }

    private static Holder<Enchantment> rootbound(GameTestHelper helper) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Rootbound.KEY);
    }

    private static ServerPlayer feller(GameTestHelper helper, Item sapling, int saplings) {
        ItemStack axe = new ItemStack(Items.DIAMOND_AXE);
        axe.enchant(rootbound(helper), 1);
        ServerPlayer player = MiningTests.playerWith(helper, axe);
        if (saplings > 0) {
            player.getInventory().add(new ItemStack(sapling, saplings));
        }
        return player;
    }

    /** Logs of {@code log} in each of {@code columns} from y {@code from} to 5, a crown of {@code crown} round its top. */
    private static void tree(GameTestHelper helper, List<BlockPos> columns, int from, Block log, BlockState crown) {
        for (BlockPos column : columns) {
            for (int y = from; y <= 5; y++) {
                helper.setBlock(column.atY(y), log);
            }
        }
        for (BlockPos column : columns) {
            for (BlockPos near : BlockPos.betweenClosed(column.offset(-1, 6, -1), column.offset(1, 6, 1))) {
                helper.setBlock(near.immutable(), crown);
            }
        }
    }

    private static BlockState leaves(Block block) {
        return block.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, 1);
    }

    private static void oak(GameTestHelper helper) {
        BlockPos column = new BlockPos(3, 0, 3);
        helper.setBlock(column.atY(1), Blocks.DIRT);
        tree(helper, List.of(column), 2, Blocks.OAK_LOG, leaves(Blocks.OAK_LEAVES));
        ServerPlayer player = feller(helper, Items.OAK_SAPLING, 3);
        player.gameMode.destroyBlock(helper.absolutePos(column.atY(2)));
        helper.runAfterDelay(3, () -> {
            check(helper, helper.getBlockState(column.atY(3)).isAir(), "the tree came down");
            check(helper, helper.getBlockState(column.atY(2)).is(Blocks.OAK_SAPLING), "an oak sapling stands where it grew: "
                    + helper.getBlockState(column.atY(2)));
            check(helper, player.getInventory().countItem(Items.OAK_SAPLING) == 2, "one sapling taken from the three");
            helper.succeed();
        });
    }

    /** An oak, a dirt floor and four logs up from y 2, three saplings at hand: its column and the feller. */
    private static ServerPlayer oakTree(GameTestHelper helper, BlockPos column) {
        helper.setBlock(column.atY(1), Blocks.DIRT);
        tree(helper, List.of(column), 2, Blocks.OAK_LOG, leaves(Blocks.OAK_LEAVES));
        return feller(helper, Items.OAK_SAPLING, 3);
    }

    /** Cut at its second log, the bottom one left standing: nothing planted, nothing taken. */
    private static void cutHigh(GameTestHelper helper) {
        BlockPos column = new BlockPos(3, 0, 3);
        ServerPlayer player = oakTree(helper, column);
        player.gameMode.destroyBlock(helper.absolutePos(column.atY(3)));
        helper.runAfterDelay(3, () -> {
            check(helper, helper.getBlockState(column.atY(4)).isAir(), "the tree above the cut came down");
            check(helper, helper.getBlockState(column.atY(2)).is(Blocks.OAK_LOG), "its bottom log stands");
            check(helper, helper.getBlockState(column.atY(3)).isAir(), "no sapling where it was cut");
            check(helper, player.getInventory().countItem(Items.OAK_SAPLING) == 3, "and none taken");
            helper.succeed();
        });
    }

    /** Felling held to two extra logs leaves the oak's top standing: no sapling under it. */
    private static void limit(GameTestHelper helper) {
        BlockPos column = new BlockPos(3, 0, 3);
        ServerPlayer player = oakTree(helper, column);
        int was = NookworksConfig.TIMBER_MAX_BLOCKS.getAsInt();
        try {
            NookworksConfig.TIMBER_MAX_BLOCKS.set(2); // flipped and put back within this one call
            player.gameMode.destroyBlock(helper.absolutePos(column.atY(2)));
        } finally {
            NookworksConfig.TIMBER_MAX_BLOCKS.set(was);
        }
        helper.runAfterDelay(3, () -> {
            check(helper, helper.getBlockState(column.atY(5)).is(Blocks.OAK_LOG), "the top log is left standing");
            check(helper, helper.getBlockState(column.atY(2)).isAir(), "no sapling under it");
            check(helper, player.getInventory().countItem(Items.OAK_SAPLING) == 3, "and none taken");
            helper.succeed();
        });
    }

    /** Water poured where the trunk stood, in the tick before the planting: it is kept, and no sapling is taken. */
    private static void water(GameTestHelper helper) {
        BlockPos column = new BlockPos(3, 0, 3);
        ServerPlayer player = oakTree(helper, column);
        player.gameMode.destroyBlock(helper.absolutePos(column.atY(2)));
        helper.setBlock(column.atY(2), Blocks.WATER);
        helper.runAfterDelay(3, () -> {
            check(helper, helper.getBlockState(column.atY(2)).is(Blocks.WATER), "the water is still there");
            check(helper, player.getInventory().countItem(Items.OAK_SAPLING) == 3, "and no sapling was taken");
            helper.succeed();
        });
    }

    /** Right-clicks the top of {@code pos} with {@code stack} in the main hand: what the click came to. */
    private static InteractionResult click(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos at = helper.absolutePos(pos);
        return player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.UP, 0.5), Direction.UP, at, false));
    }

    /**
     * A Rootbound axe leaves a log's bark on, passing the click on to the other hand (bone meal held there grows the
     * sapling just planted, and must not strip the tree that grows); a plain axe strips it, and the Rootbound one still
     * scrapes copper.
     */
    private static void noStripping(GameTestHelper helper) {
        BlockPos log = new BlockPos(2, 1, 2);
        BlockPos plain = new BlockPos(4, 1, 2);
        BlockPos copper = new BlockPos(2, 1, 4);
        helper.setBlock(log, Blocks.OAK_LOG);
        helper.setBlock(plain, Blocks.OAK_LOG);
        helper.setBlock(copper, Blocks.COPPER_BLOCK.weathering().exposed());
        ServerPlayer player = feller(helper, Items.OAK_SAPLING, 0);
        ItemStack rootboundAxe = player.getMainHandItem();
        InteractionResult onLog = click(helper, player, rootboundAxe, log);
        check(helper, helper.getBlockState(log).is(Blocks.OAK_LOG), "the Rootbound axe leaves the log's bark on");
        check(helper, !onLog.consumesAction(), "and passes the click on to the other hand: " + onLog);
        click(helper, player, new ItemStack(Items.DIAMOND_AXE), plain);
        check(helper, helper.getBlockState(plain).is(Blocks.STRIPPED_OAK_LOG), "a plain axe strips it");
        click(helper, player, rootboundAxe, copper);
        check(helper, helper.getBlockState(copper).is(Blocks.COPPER_BLOCK.weathering().unaffected()), "the Rootbound axe still scrapes copper");
        helper.succeed();
    }

    private static void tooFew(GameTestHelper helper) {
        List<BlockPos> columns = List.of(new BlockPos(2, 0, 2), new BlockPos(3, 0, 2), new BlockPos(2, 0, 3), new BlockPos(3, 0, 3));
        columns.forEach(c -> helper.setBlock(c.atY(1), Blocks.DIRT));
        tree(helper, columns, 2, Blocks.DARK_OAK_LOG, leaves(Blocks.DARK_OAK_LEAVES));
        ServerPlayer player = feller(helper, Items.DARK_OAK_SAPLING, 3);
        player.gameMode.destroyBlock(helper.absolutePos(columns.getFirst().atY(2)));
        helper.runAfterDelay(3, () -> {
            check(helper, columns.stream().allMatch(c -> helper.getBlockState(c.atY(2)).isAir()), "a 2x2 with three saplings at hand gets none");
            check(helper, player.getInventory().countItem(Items.DARK_OAK_SAPLING) == 3, "and keeps its three");
            helper.succeed();
        });
    }

    /** A 2x2 dark oak, one of its bottom logs broken by hand before; felled from another bottom log: four saplings. */
    private static void darkOak(GameTestHelper helper) {
        List<BlockPos> columns = List.of(new BlockPos(2, 0, 2), new BlockPos(3, 0, 2), new BlockPos(2, 0, 3), new BlockPos(3, 0, 3));
        columns.forEach(c -> helper.setBlock(c.atY(1), Blocks.GRASS_BLOCK));
        tree(helper, columns, 2, Blocks.DARK_OAK_LOG, leaves(Blocks.DARK_OAK_LEAVES));
        helper.setBlock(columns.get(3).atY(2), Blocks.AIR); // broken by hand before
        ServerPlayer player = feller(helper, Items.DARK_OAK_SAPLING, 5);
        player.gameMode.destroyBlock(helper.absolutePos(columns.getFirst().atY(2)));
        helper.runAfterDelay(3, () -> {
            check(helper, columns.stream().allMatch(c -> helper.getBlockState(c.atY(2)).is(Blocks.DARK_OAK_SAPLING)),
                    "four dark oak saplings stand where the four grew");
            check(helper, player.getInventory().countItem(Items.DARK_OAK_SAPLING) == 1, "four taken from the five");
            helper.succeed();
        });
    }

    /** All four bottom logs of a 2x2 broken, the rest hanging in the air: felled from the lowest left, nothing planted. */
    private static void floating(GameTestHelper helper) {
        List<BlockPos> columns = List.of(new BlockPos(2, 0, 2), new BlockPos(3, 0, 2), new BlockPos(2, 0, 3), new BlockPos(3, 0, 3));
        columns.forEach(c -> helper.setBlock(c.atY(1), Blocks.DIRT));
        tree(helper, columns, 3, Blocks.SPRUCE_LOG, leaves(Blocks.SPRUCE_LEAVES));
        ServerPlayer player = feller(helper, Items.SPRUCE_SAPLING, 8);
        player.gameMode.destroyBlock(helper.absolutePos(columns.getFirst().atY(3)));
        helper.runAfterDelay(3, () -> {
            check(helper, columns.stream().allMatch(c -> helper.getBlockState(c.atY(3)).isAir() && helper.getBlockState(c.atY(2)).isAir()),
                    "a floating tree gets no saplings");
            check(helper, player.getInventory().countItem(Items.SPRUCE_SAPLING) == 8, "and none are taken");
            helper.succeed();
        });
    }

    /** A crimson stem on crimson nylium with a crown of wart blocks gets a crimson fungus; one on netherrack, none. */
    private static void fungus(GameTestHelper helper) {
        BlockPos nylium = new BlockPos(2, 0, 2);
        BlockPos netherrack = new BlockPos(5, 0, 5);
        helper.setBlock(nylium.atY(1), Blocks.CRIMSON_NYLIUM);
        helper.setBlock(netherrack.atY(1), Blocks.NETHERRACK);
        tree(helper, List.of(nylium), 2, Blocks.CRIMSON_STEM, Blocks.NETHER_WART_BLOCK.defaultBlockState());
        tree(helper, List.of(netherrack), 2, Blocks.CRIMSON_STEM, Blocks.NETHER_WART_BLOCK.defaultBlockState());
        ServerPlayer player = feller(helper, Items.CRIMSON_FUNGUS, 4);
        player.gameMode.destroyBlock(helper.absolutePos(nylium.atY(2)));
        player.gameMode.destroyBlock(helper.absolutePos(netherrack.atY(2)));
        helper.runAfterDelay(3, () -> {
            check(helper, helper.getBlockState(nylium.atY(2)).is(Blocks.CRIMSON_FUNGUS), "a crimson fungus on the nylium");
            check(helper, helper.getBlockState(netherrack.atY(2)).isAir(), "none where the nylium had turned to netherrack");
            check(helper, player.getInventory().countItem(Items.CRIMSON_FUNGUS) == 3, "one fungus taken");
            helper.succeed();
        });
    }

    private static final Rootbound.Replant PLAIN = new Rootbound.Replant(Identifier.withDefaultNamespace("oak_sapling"));

    private static Rootbound.Replant kind(Rootbound.ThreeWide threeWide, boolean fourOnly, Rootbound.Spread spread) {
        return new Rootbound.Replant(Identifier.withDefaultNamespace("oak_sapling"), threeWide, fourOnly, spread, false, List.of());
    }

    private static Set<BlockPos> logs(int... xz) {
        Set<BlockPos> set = new HashSet<>();
        for (int i = 0; i < xz.length; i += 2) {
            set.add(new BlockPos(xz[i], 0, xz[i + 1]));
            set.add(new BlockPos(xz[i], 1, xz[i + 1]));
        }
        return set;
    }

    private static void footprints(GameTestHelper helper) {
        BlockPos cut = BlockPos.ZERO;
        check(helper, Rootbound.places(logs(0, 0), cut, PLAIN).equals(List.of(cut)), "a trunk one wide: one");
        check(helper, Rootbound.places(logs(0, 0, 1, 0, 0, 1, 1, 1), cut, PLAIN).size() == 4, "a 2x2: four");
        check(helper, Rootbound.places(logs(0, 0, 1, 0, 0, 1), cut, PLAIN).isEmpty(), "three of a 2x2: none");
        Set<BlockPos> leaning = Set.of(cut, new BlockPos(1, 1, 0), new BlockPos(1, 2, 0));
        check(helper, Rootbound.places(leaning, cut, PLAIN).equals(List.of(cut)),
                "a trunk leaning aside right above its foot (a palm): one at the foot");
        Set<BlockPos> plus = logs(1, 1, 0, 1, 2, 1, 1, 0, 1, 2);
        check(helper, Rootbound.places(plus, new BlockPos(1, 0, 0), kind(Rootbound.ThreeWide.MIDDLE, false, Rootbound.Spread.NONE)).equals(List.of(new BlockPos(1, 0, 1))),
                "a stem three wide, its corners left out: one in the middle");
        check(helper, Rootbound.places(plus, new BlockPos(1, 0, 0), PLAIN).isEmpty(), "unless its kind says none");
        check(helper, Rootbound.places(logs(0, 0, 1, 0, 2, 0), cut, kind(Rootbound.ThreeWide.MIDDLE, false, Rootbound.Spread.NONE)).isEmpty(), "a foot three in a row: none");
        Set<BlockPos> nine = logs(0, 0, 1, 0, 2, 0, 0, 1, 1, 1, 2, 1, 0, 2, 1, 2, 2, 2);
        check(helper, Rootbound.places(nine, cut, kind(Rootbound.ThreeWide.NINE, false, Rootbound.Spread.NONE)).size() == 9, "a 3x3 whose kind says nine: nine");
        check(helper, Rootbound.places(logs(0, 0), cut, kind(Rootbound.ThreeWide.NONE, true, Rootbound.Spread.NONE)).isEmpty(),
                "one wide of a kind that grows only from four: none");
        check(helper, Rootbound.places(logs(0, 0, 1, 0, 0, 1, 1, 1), cut, kind(Rootbound.ThreeWide.NONE, true, Rootbound.Spread.NONE)).size() == 4,
                "its 2x2: four");
        // a big redwood's buttressed foot, five wide: the middle column (2, 2) rises highest
        Set<BlockPos> buttress = new HashSet<>();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                int far = Math.abs(x - 2) + Math.abs(z - 2);
                for (int y = 0; far <= 3 && y < (far == 0 ? 20 : 4 - far); y++) {
                    buttress.add(new BlockPos(x, y, z));
                }
            }
        }
        buttress.add(new BlockPos(4, 19, 2)); // a branch up high, not joined to the foot
        check(helper, Rootbound.places(buttress, new BlockPos(2, 0, 0), kind(Rootbound.ThreeWide.NONE, false, Rootbound.Spread.FOUR))
                .equals(List.of(new BlockPos(2, 0, 2), new BlockPos(3, 0, 2), new BlockPos(2, 0, 3), new BlockPos(3, 0, 3))),
                "a foot spread from four: four in a 2x2, the highest column at its north-west");
        check(helper, Rootbound.places(buttress, new BlockPos(2, 0, 0), PLAIN).isEmpty(), "unless its kind spreads so");
        // a coconut palm's foot: an L of three logs, the trunk rising from one of them
        Set<BlockPos> ell = Set.of(cut, new BlockPos(1, 0, 0), new BlockPos(0, 0, 1), new BlockPos(0, 1, 0), new BlockPos(0, 2, 0),
                new BlockPos(1, 3, 0), new BlockPos(1, 4, 0));
        check(helper, Rootbound.places(ell, new BlockPos(1, 0, 0), kind(Rootbound.ThreeWide.NONE, false, Rootbound.Spread.ONE))
                .equals(List.of(cut)), "a foot spread from one sapling: one where the trunk rises");
        check(helper, Rootbound.places(ell, new BlockPos(1, 0, 0), PLAIN).isEmpty(), "unless its kind spreads so");
        check(helper, Rootbound.replantOf(Blocks.MANGROVE_LEAVES) == null, "a mangrove is not replanted");
        check(helper, Stream.of(Blocks.RED_POPLAR_LEAVES, Blocks.ORANGE_POPLAR_LEAVES, Blocks.YELLOW_POPLAR_LEAVES)
                .allMatch(leaves -> Rootbound.replantOf(leaves).sapling().equals(Identifier.withDefaultNamespace("poplar_sapling"))),
                "a poplar's leaves of every colour give the poplar sapling");
        check(helper, Rootbound.replantOf(Blocks.DARK_OAK_LEAVES).fourOnly(),
                "the data map loads, other mods' entries left out (a dark oak grows only from four)");
        check(helper, Rootbound.replantOf(Blocks.FLOWERING_AZALEA_LEAVES).sapling().equals(Identifier.withDefaultNamespace("flowering_azalea")),
                "flowering azalea leaves give a flowering azalea");
        helper.succeed();
    }

    /**
     * A kind whose tree drops another mod's sapling in place of its own (Wilder Wild's poplars): that one is planted
     * when the player carries enough, one not in the game passed over, else the plain sapling - or none without either.
     */
    private static void preferred(GameTestHelper helper) {
        Rootbound.Replant kind = new Rootbound.Replant(Identifier.withDefaultNamespace("oak_sapling"), Rootbound.ThreeWide.NONE, false,
                Rootbound.Spread.NONE, false, List.of(Identifier.fromNamespaceAndPath("nookworks", "no_such_sapling"),
                        Identifier.withDefaultNamespace("birch_sapling")));
        ServerPlayer player = feller(helper, Items.BIRCH_SAPLING, 1);
        check(helper, kind.chosen(player, 1) == Items.BIRCH_SAPLING, "the preferred sapling, carried, is the one planted");
        check(helper, kind.chosen(player, 4) == null, "too few of it and none of the plain one: nothing");
        player.getInventory().add(new ItemStack(Items.OAK_SAPLING, 4));
        check(helper, kind.chosen(player, 4) == Items.OAK_SAPLING, "too few of it: the plain sapling");
        helper.succeed();
    }

    /**
     * A librarian's book (enchant_randomly) offers Rootbound only while felling works: drawn from Rootbound alone, the
     * book is enchanted with felling on and left plain with it off (flipped and put back in one tick).
     */
    private static void traded(GameTestHelper helper) {
        Holder<Enchantment> rootbound = rootbound(helper);
        LootItemFunction book = EnchantRandomlyFunction.randomEnchantment().withEnchantment(rootbound).build();
        LootContext context = new LootContext.Builder(new LootParams.Builder(helper.getLevel()).create(LootContextParamSets.EMPTY))
                .create(Optional.empty());
        boolean was = NookworksConfig.TIMBER_ENABLED.get();
        ItemStack on;
        ItemStack off;
        try {
            NookworksConfig.TIMBER_ENABLED.set(true);
            on = book.apply(new ItemStack(Items.BOOK), context);
            NookworksConfig.TIMBER_ENABLED.set(false);
            off = book.apply(new ItemStack(Items.BOOK), context);
        } finally {
            NookworksConfig.TIMBER_ENABLED.set(was);
        }
        check(helper, on.is(Items.ENCHANTED_BOOK) && EnchantmentHelper.getEnchantmentsForCrafting(on).getLevel(rootbound) == 1,
                "with felling on, the book gets Rootbound: " + on);
        check(helper, off.is(Items.BOOK), "with felling off, it is left a plain book: " + off);
        helper.succeed();
    }

    /** With tree felling switched off, the table offers no Rootbound; on, it does (flipped and put back in one tick). */
    private static void offered(GameTestHelper helper) {
        Holder<Enchantment> rootbound = rootbound(helper);
        ItemStack axe = new ItemStack(Items.DIAMOND_AXE);
        boolean was = NookworksConfig.TIMBER_ENABLED.get();
        Set<Holder<Enchantment>> on;
        Set<Holder<Enchantment>> off;
        try {
            NookworksConfig.TIMBER_ENABLED.set(true);
            on = EnchantmentHelper.getAvailableEnchantmentResults(30, axe, Stream.of(rootbound)).stream()
                    .map(e -> e.enchantment()).collect(Collectors.toSet());
            NookworksConfig.TIMBER_ENABLED.set(false);
            off = EnchantmentHelper.getAvailableEnchantmentResults(30, axe, Stream.of(rootbound)).stream()
                    .map(e -> e.enchantment()).collect(Collectors.toSet());
        } finally {
            NookworksConfig.TIMBER_ENABLED.set(was);
        }
        check(helper, on.contains(rootbound), "with felling on, an axe can get Rootbound at the table");
        check(helper, off.isEmpty(), "with felling off, it is not offered");
        helper.succeed();
    }
}
