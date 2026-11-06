package dev.nookworks.gametest;

import static dev.nookworks.gametest.MachineTests.placeByHand;
import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.block.SconceBlock;
import dev.nookworks.block.entity.CrateBlockEntity;
import dev.nookworks.NookworksConfig;
import dev.nookworks.feature.ChainBreaking;
import dev.nookworks.feature.Earthmover;
import dev.nookworks.feature.Fireproofing;
import dev.nookworks.feature.PlacedLogs;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/** Tree felling, vein mining and the hammers' 3x3. */
final class MiningTests {
    static void register() {
        test("timber_fells_tree", MiningTests::timberFellsTree);
        test("timber_ignores_log_cabin", MiningTests::timberIgnoresLogCabin);
        test("timber_leaves_decay_fast_but_spare_other_trees", MiningTests::timberLeavesDecayFast);
        test("timber_with_silk_touch_drops_the_leaves", MiningTests::timberSilkTouchLeaves);
        test("timber_leaves_can_go_at_once", MiningTests::timberLeavesAtOnce);
        test("silk_touch_leaves_stop_before_the_axe_breaks", MiningTests::silkLeavesSpareAWornAxe);
        test("a_silk_touch_axe_switches_its_leaf_gathering", MiningTests::leafGatheringSwitches);
        test("a_silk_touch_axe_with_leaf_gathering_off_leaves_the_leaves", MiningTests::leafGatheringOff);
        test("timber_sneak_breaks_one", MiningTests::timberSneakBreaksOne);
        test("timber_spares_a_cabin_against_the_tree", MiningTests::timberSparesCabinAgainstTree);
        test("timber_spares_a_village_house", MiningTests::timberSparesVillageHouse);
        test("timber_fells_a_tree_by_the_road", MiningTests::timberFellsATreeByTheRoad);
        test("placed_logs_are_remembered", MiningTests::placedLogsAreRemembered);
        test("marks_follow_a_piston", MiningTests::marksFollowAPiston);
        test("a_new_block_is_not_coated", MiningTests::newBlockIsNotCoated);
        test("vein_mines_connected_ore", MiningTests::veinMinesConnectedOre);
        test("vein_runs_on_into_deepslate", MiningTests::veinRunsOnIntoDeepslate);
        test("vein_needs_proper_pickaxe", MiningTests::veinNeedsProperPickaxe);
        test("vein_stops_before_tool_breaks", MiningTests::veinStopsBeforeToolBreaks);
        test("vein_ignores_another_mods_area_breaks", MiningTests::veinIgnoresAreaBreaks);
        test("crate_placed_in_survival_is_consumed", MiningTests::cratePlacedIsConsumed);
        test("sconce_lit_by_flint_and_snuffed_by_hand", MiningTests::sconceLightAndSnuff);
        test("hammer_mines_3x3_facing_player", MiningTests::hammerMines3x3);
        test("hammer_sneak_mines_one", MiningTests::hammerSneakMinesOne);
        test("hammer_spares_unsuitable_blocks", MiningTests::hammerSparesUnsuitable);
        test("hammer_mines_floor_when_looking_down", MiningTests::hammerMinesFloor);
        test("hammer_on_dirt_does_not_sweep_stone", MiningTests::hammerOnDirtNoSweep);
        test("earthmover_sweeps_dirt_and_wood", MiningTests::earthmoverSweeps);
        test("earthmover_digs_dirt_fast", MiningTests::earthmoverDigsFast);
        test("earthmover_comes_from_the_table_and_trades", MiningTests::earthmoverFromTableAndTrades);
    }

    /**
     * Earthmover is had from the enchanting table and from a librarian - and not from a structure's chests, a catch or
     * any other random loot: nothing of ours goes into a structure's chests, enchantments included (the user,
     * 2026-10-01).
     */
    static void earthmoverFromTableAndTrades(GameTestHelper helper) {
        var earthmover = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Earthmover.KEY);
        check(helper, earthmover.is(EnchantmentTags.IN_ENCHANTING_TABLE) && earthmover.is(EnchantmentTags.TRADEABLE),
                "the table and a librarian offer it");
        check(helper, !earthmover.is(EnchantmentTags.ON_RANDOM_LOOT), "no chest or catch holds it");
        helper.succeed();
    }

    // ---- Timber / vein mining ----

    /** Trunk (3,1..4,3), a diagonal branch at (4,5,4) and a crown of leaves; returns every log position. */
    static List<BlockPos> buildTree(GameTestHelper helper, boolean playerPlacedLeaves) {
        List<BlockPos> logs = new ArrayList<>();
        for (int y = 1; y <= 4; y++) {
            logs.add(new BlockPos(3, y, 3));
        }
        logs.add(new BlockPos(4, 5, 4));
        logs.forEach(p -> helper.setBlock(p, Blocks.OAK_LOG));
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, playerPlacedLeaves);
        helper.setBlock(new BlockPos(3, 5, 3), leaves);
        helper.setBlock(new BlockPos(2, 5, 3), leaves);
        return logs;
    }

    /** Tree A (trunk at x=1,z=1) and tree B (trunk at x=5,z=5); their leaf canopies touch in the middle. */
    static void timberLeavesDecayFast(GameTestHelper helper) {
        // Placed as a grown tree's leaves are, near a log: the default state is distance 7, "already decaying", and
        // vanilla's random ticks would now and then take one of tree B's leaves before its distance was worked out.
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false)
                .setValue(LeavesBlock.DISTANCE, 1);
        for (int y = 1; y <= 4; y++) {
            helper.setBlock(new BlockPos(1, y, 1), Blocks.OAK_LOG);
            helper.setBlock(new BlockPos(5, y, 5), Blocks.OAK_LOG);
        }
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(0, 5, 0), new BlockPos(6, 5, 6))) {
            helper.setBlock(p.immutable(), leaves);
        }
        helper.setBlock(new BlockPos(5, 5, 5), Blocks.OAK_LOG); // tree B's top log keeps its canopy alive
        ServerPlayer player = playerWith(helper, new ItemStack(Items.DIAMOND_AXE));
        player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(1, 1, 1)));
        helper.succeedWhen(() -> {
            check(helper, helper.getBlockState(new BlockPos(0, 5, 0)).isAir(), "tree A's far leaves decay quickly");
            check(helper, helper.getBlockState(new BlockPos(1, 5, 1)).isAir(), "leaves above the felled trunk decay");
            check(helper, helper.getBlockState(new BlockPos(5, 5, 4)).is(Blocks.OAK_LEAVES), "tree B keeps its leaves");
            check(helper, helper.getBlockState(new BlockPos(3, 5, 3)).is(Blocks.OAK_LEAVES), "leaves within reach of tree B stay");
        });
    }

    /** One oak, its trunk at (1,1..4,1) and a crown of 5x5x2 leaves above (50, more than a tick's share). */
    private static void smallOak(GameTestHelper helper) {
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false)
                .setValue(LeavesBlock.DISTANCE, 1);
        for (int y = 1; y <= 4; y++) {
            helper.setBlock(new BlockPos(3, y, 3), Blocks.OAK_LOG);
        }
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(1, 5, 1), new BlockPos(5, 6, 5))) {
            helper.setBlock(p.immutable(), leaves);
        }
    }

    /**
     * Felled by an axe with Silk Touch, the tree's leaves drop as leaves - as that axe would break them - and no
     * saplings (a CurseForge comment, 2026-10-26: shearing leaves is a bother).
     */
    static void timberSilkTouchLeaves(GameTestHelper helper) {
        smallOak(helper);
        ItemStack axe = new ItemStack(Items.DIAMOND_AXE);
        axe.enchant(helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
        ServerPlayer player = playerWith(helper, axe);
        player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(3, 1, 3)));
        helper.succeedWhen(() -> {
            check(helper, helper.getBlockState(new BlockPos(1, 5, 1)).isAir() && helper.getBlockState(new BlockPos(5, 6, 5)).isAir(),
                    "the leaves have gone");
            var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(9));
            int leafItems = drops.stream().filter(e -> e.getItem().is(Items.OAK_LEAVES)).mapToInt(e -> e.getItem().getCount()).sum();
            boolean saplings = drops.stream().anyMatch(e -> e.getItem().is(Items.OAK_SAPLING));
            // (the mock player at the corner may pick a few up: what matters is leaves, not saplings)
            check(helper, leafItems >= 40 && !saplings, "the leaves dropped as leaves, no saplings: " + leafItems + ", " + saplings);
            // four logs a point each, as by hand; the fifty leaves a point for the first and each fourth after: 13
            check(helper, axe.getDamageValue() == 4 + 13, "the axe worn a point a log and one for every four leaves: " + axe.getDamageValue());
        });
    }

    /**
     * A Silk Touch axe on its last points (protectTools on, the default) fells the tree, takes a leaf or so as leaves and
     * then leaves the rest to decay as usual, rather than breaking on them.
     */
    static void silkLeavesSpareAWornAxe(GameTestHelper helper) {
        smallOak(helper);
        ItemStack axe = new ItemStack(Items.DIAMOND_AXE);
        axe.enchant(helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
        axe.setDamageValue(axe.getMaxDamage() - 6); // the four logs leave it two
        ServerPlayer player = playerWith(helper, axe);
        player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(3, 1, 3)));
        helper.succeedWhen(() -> {
            check(helper, helper.getBlockState(new BlockPos(1, 5, 1)).isAir() && helper.getBlockState(new BlockPos(5, 6, 5)).isAir(),
                    "the leaves have gone");
            var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(9));
            int leafItems = drops.stream().filter(e -> e.getItem().is(Items.OAK_LEAVES)).mapToInt(e -> e.getItem().getCount()).sum();
            check(helper, player.getMainHandItem() == axe && axe.getMaxDamage() - axe.getDamageValue() == 1,
                    "the axe kept, on its last point: " + (axe.getMaxDamage() - axe.getDamageValue()));
            check(helper, leafItems <= SILK_POINT_LEAVES, "only the leaves its last point paid for as leaves: " + leafItems);
        });
    }

    static ItemStack silkAxe(GameTestHelper helper) {
        ItemStack axe = new ItemStack(Items.DIAMOND_AXE);
        axe.enchant(helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
        return axe;
    }

    /**
     * Sneak-use in the air switches a Silk Touch axe's leaf gathering off and on again; not without sneaking, not on a
     * plain axe, and not when the click lands on a block (the other hand may be placing one).
     */
    static void leafGatheringSwitches(GameTestHelper helper) {
        ItemStack axe = silkAxe(helper);
        ServerPlayer player = MockPlayers.inLevel(helper); // with a connection: the switch says so on the action bar
        player.setItemInHand(InteractionHand.MAIN_HAND, axe);
        player.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1, 1.5)), 0.0F, -90.0F); // looking at the sky
        player.gameMode.useItem(player, helper.getLevel(), axe, InteractionHand.MAIN_HAND);
        check(helper, dev.nookworks.feature.LeafGathering.gathers(axe), "not sneaking, nothing switched");
        player.setShiftKeyDown(true);
        player.gameMode.useItem(player, helper.getLevel(), axe, InteractionHand.MAIN_HAND);
        check(helper, !dev.nookworks.feature.LeafGathering.gathers(axe), "sneak-used in the air, it is switched off");
        player.gameMode.useItem(player, helper.getLevel(), axe, InteractionHand.MAIN_HAND);
        check(helper, !dev.nookworks.feature.LeafGathering.gathers(axe), "the button held (a use every 4 ticks) does not flip it back");
        player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(axe));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        player.gameMode.useItem(player, helper.getLevel(), axe, InteractionHand.MAIN_HAND);
        check(helper, !dev.nookworks.feature.LeafGathering.gathers(axe), "a shield in the other hand is raised instead");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.gameMode.useItem(player, helper.getLevel(), axe, InteractionHand.MAIN_HAND);
        check(helper, dev.nookworks.feature.LeafGathering.gathers(axe)
                && !axe.has(dev.nookworks.registry.ModDataComponents.GATHERS_LEAVES.get()), "and on again, as it came");
        ItemStack plain = new ItemStack(Items.DIAMOND_AXE);
        player.setItemInHand(InteractionHand.MAIN_HAND, plain);
        player.gameMode.useItem(player, helper.getLevel(), plain, InteractionHand.MAIN_HAND);
        check(helper, !plain.has(dev.nookworks.registry.ModDataComponents.GATHERS_LEAVES.get()), "an axe without Silk Touch has no switch");
        player.setItemInHand(InteractionHand.MAIN_HAND, axe);
        player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(axe));
        helper.setBlock(new BlockPos(1, 3, 1), Blocks.STONE); // a block over its head, in reach
        player.gameMode.useItem(player, helper.getLevel(), axe, InteractionHand.MAIN_HAND);
        check(helper, dev.nookworks.feature.LeafGathering.gathers(axe), "used at a block, nothing switched");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /** Its leaf gathering off, a Silk Touch axe's felled tree decays as any other's: saplings and all, the axe worn only for the logs. */
    static void leafGatheringOff(GameTestHelper helper) {
        smallOak(helper);
        ItemStack axe = silkAxe(helper);
        axe.set(dev.nookworks.registry.ModDataComponents.GATHERS_LEAVES.get(), false);
        ServerPlayer player = playerWith(helper, axe);
        player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(3, 1, 3)));
        helper.succeedWhen(() -> {
            check(helper, helper.getBlockState(new BlockPos(1, 5, 1)).isAir() && helper.getBlockState(new BlockPos(5, 6, 5)).isAir(),
                    "the leaves have gone");
            var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(9));
            check(helper, drops.stream().noneMatch(e -> e.getItem().is(Items.OAK_LEAVES)), "no leaves dropped as leaves");
            check(helper, axe.getDamageValue() == 4, "the axe worn for the four logs only: " + axe.getDamageValue());
        });
    }

    /** The leaves a point of a Silk Touch axe's durability takes as leaves (FastLeafDecay). */
    private static final int SILK_POINT_LEAVES = 4;

    /** With instantLeafDecay on, the crown goes the tick after the tree falls, all of it, not a dozen a tick. */
    static void timberLeavesAtOnce(GameTestHelper helper) {
        smallOak(helper);
        boolean was = NookworksConfig.INSTANT_LEAF_DECAY.get();
        NookworksConfig.INSTANT_LEAF_DECAY.set(true);
        ServerPlayer player = playerWith(helper, new ItemStack(Items.DIAMOND_AXE));
        player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(3, 1, 3)));
        NookworksConfig.INSTANT_LEAF_DECAY.set(was);
        helper.runAfterDelay(3, () -> {
            for (BlockPos p : BlockPos.betweenClosed(new BlockPos(1, 5, 1), new BlockPos(5, 6, 5))) {
                check(helper, helper.getBlockState(p).isAir(), "every leaf gone at once: one left at " + p);
            }
            helper.succeed();
        });
    }

    static ServerPlayer playerWith(GameTestHelper helper, ItemStack tool) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        return player;
    }

    static void timberFellsTree(GameTestHelper helper) {
        List<BlockPos> logs = buildTree(helper, false);
        ServerPlayer player = playerWith(helper, new ItemStack(Items.DIAMOND_AXE));

        player.gameMode.destroyBlock(helper.absolutePos(logs.get(0)));

        logs.forEach(p -> helper.assertBlockNotPresent(Blocks.OAK_LOG, p));
        helper.assertBlockPresent(Blocks.OAK_LEAVES, new BlockPos(2, 5, 3));
        int damage = player.getMainHandItem().getDamageValue();
        check(helper, damage == logs.size(), "axe should lose 1 durability per log (" + logs.size() + "), lost " + damage);
        helper.succeed();
    }

    static void timberIgnoresLogCabin(GameTestHelper helper) {
        List<BlockPos> logs = buildTree(helper, true);
        ServerPlayer player = playerWith(helper, new ItemStack(Items.DIAMOND_AXE));

        player.gameMode.destroyBlock(helper.absolutePos(logs.get(0)));

        helper.assertBlockNotPresent(Blocks.OAK_LOG, logs.get(0));
        helper.assertBlockPresent(Blocks.OAK_LOG, logs.get(1));
        helper.succeed();
    }

    /** A real tree, and a wall of placed logs of the same wood touching its trunk and its crown. */
    static void timberSparesCabinAgainstTree(GameTestHelper helper) {
        List<BlockPos> logs = buildTree(helper, false);
        List<BlockPos> wall = new ArrayList<>();
        for (int y = 1; y <= 5; y++) {
            wall.add(new BlockPos(2, y, 3));
            wall.add(new BlockPos(1, y, 3));
        }
        wall.forEach(p -> {
            helper.setBlock(p, Blocks.OAK_LOG);
            PlacedLogs.mark(helper.getLevel(), helper.absolutePos(p), true);
        });
        helper.setBlock(new BlockPos(2, 5, 3), Blocks.OAK_LOG); // the wall runs right up into the leaves
        ServerPlayer player = playerWith(helper, new ItemStack(Items.DIAMOND_AXE));

        player.gameMode.destroyBlock(helper.absolutePos(logs.get(0)));

        logs.forEach(p -> helper.assertBlockNotPresent(Blocks.OAK_LOG, p));
        wall.forEach(p -> helper.assertBlockPresent(Blocks.OAK_LOG, p));

        // Cutting the wall itself is just cutting one log.
        player.gameMode.destroyBlock(helper.absolutePos(wall.get(0)));
        helper.assertBlockNotPresent(Blocks.OAK_LOG, wall.get(0));
        helper.assertBlockPresent(Blocks.OAK_LOG, wall.get(2));
        helper.succeed();
    }

    /**
     * The same tree against a wall of logs that nobody placed, because the world generated them as part of a
     * village. The wall is registered as a village piece the way world generation does it: the start in its own
     * chunk, a reference to it from every chunk the piece covers.
     */
    static void timberSparesVillageHouse(GameTestHelper helper) {
        List<BlockPos> logs = buildTree(helper, false);
        List<BlockPos> wall = new ArrayList<>();
        for (int y = 1; y <= 5; y++) {
            wall.add(new BlockPos(2, y, 3));
            wall.add(new BlockPos(1, y, 3));
        }
        wall.forEach(p -> helper.setBlock(p, Blocks.OAK_LOG));
        ServerLevel level = helper.getLevel();
        check(helper, !PlacedLogs.isBuilt(level, helper.absolutePos(wall.get(0))),
                "before the village exists the wall is just logs");

        var box = BoundingBox.fromCorners(
                helper.absolutePos(new BlockPos(1, 1, 3)), helper.absolutePos(new BlockPos(2, 5, 3)));
        var piece = new StructurePiece(
                StructurePieceType.JIGSAW, 0, box) {
            @Override
            protected void addAdditionalSaveData(
                    StructurePieceSerializationContext context,
                    CompoundTag tag) {
            }

            @Override
            public void postProcess(WorldGenLevel worldGen,
                    StructureManager structures, ChunkGenerator generator,
                    RandomSource random, BoundingBox chunkBox,
                    ChunkPos chunkPos, BlockPos referencePos) {
            }
        };
        Runnable demolish = registerVillagePiece(helper, piece);
        try {
            check(helper, PlacedLogs.isBuilt(level, helper.absolutePos(wall.get(0))),
                    "a log inside a village piece counts as built");
            check(helper, !PlacedLogs.isBuilt(level, helper.absolutePos(logs.get(0))),
                    "the tree next to it does not");
            ServerPlayer player = playerWith(helper, new ItemStack(Items.DIAMOND_AXE));
            player.gameMode.destroyBlock(helper.absolutePos(logs.get(0)));
            logs.forEach(p -> helper.assertBlockNotPresent(Blocks.OAK_LOG, p));
            wall.forEach(p -> helper.assertBlockPresent(Blocks.OAK_LOG, p));
        } finally {
            demolish.run();
        }
        helper.succeed();
    }

    /**
     * Registers {@code piece} as part of a taiga village, the way world generation records one: the start in the
     * chunk it begins in, a reference to it from every chunk the piece covers. Returns what takes it away again,
     * so no pretend village is left behind in the test world.
     */
    static Runnable registerVillagePiece(GameTestHelper helper, StructurePiece piece) {
        ServerLevel level = helper.getLevel();
        var box = piece.getBoundingBox();
        var village = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValueOrThrow(BuiltinStructures.VILLAGE_TAIGA);
        var startChunk = new ChunkPos(box.minX() >> 4, box.minZ() >> 4);
        var start = new StructureStart(village, startChunk, 0,
                new PiecesContainer(List.of(piece)));
        var structures = level.structureManager();
        structures.setStartForStructure(village, start, level.getChunk(startChunk.x(), startChunk.z()));
        for (int cx = startChunk.x(); cx <= (box.maxX() >> 4); cx++) {
            for (int cz = startChunk.z(); cz <= (box.maxZ() >> 4); cz++) {
                structures.addReferenceForStructure(village, startChunk.pack(), level.getChunk(cx, cz));
            }
        }
        return () -> structures.setStartForStructure(village,
                StructureStart.INVALID_START, level.getChunk(startChunk.x(), startChunk.z()));
    }

    /**
     * A village street is a piece that follows the ground, and its box takes in the grass round the path. A tree
     * standing in it is still a tree.
     */
    static void timberFellsATreeByTheRoad(GameTestHelper helper) {
        List<BlockPos> logs = buildTree(helper, false);
        BlockPos lo = helper.absolutePos(new BlockPos(1, 0, 1));
        BlockPos hi = helper.absolutePos(new BlockPos(5, 1, 5)); // two deep, like every street template
        var street = new PoolElementStructurePiece(
                helper.getLevel().getStructureTemplateManager(),
                StructurePoolElement.single("minecraft:village/taiga/streets/turn_01")
                        .apply(StructureTemplatePool.Projection.TERRAIN_MATCHING),
                lo, 0, Rotation.NONE,
                BoundingBox.fromCorners(lo, hi),
                LiquidSettings.IGNORE_WATERLOGGING);
        Runnable demolish = registerVillagePiece(helper, street);
        try {
            check(helper, !PlacedLogs.isBuilt(helper.getLevel(), helper.absolutePos(logs.get(0))),
                    "the trunk's foot is in the street's box, but a street is not a building");
            ServerPlayer player = playerWith(helper, new ItemStack(Items.DIAMOND_AXE));
            player.gameMode.destroyBlock(helper.absolutePos(logs.get(0)));
            logs.forEach(p -> helper.assertBlockNotPresent(Blocks.OAK_LOG, p));
        } finally {
            demolish.run();
        }
        helper.succeed();
    }

    static void placedLogsAreRemembered(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        placeByHand(helper, new ItemStack(Items.OAK_LOG), pos);
        helper.assertBlockPresent(Blocks.OAK_LOG, pos);
        check(helper, PlacedLogs.isPlaced(helper.getLevel(), helper.absolutePos(pos)), "a log put down by hand is remembered");
        ServerPlayer player = playerWith(helper, new ItemStack(Items.DIAMOND_AXE));
        player.gameMode.destroyBlock(helper.absolutePos(pos));
        check(helper, !PlacedLogs.isPlaced(helper.getLevel(), helper.absolutePos(pos)), "and forgotten once it is broken");
        placeByHand(helper, new ItemStack(Items.STONE), pos);
        check(helper, !PlacedLogs.isPlaced(helper.getLevel(), helper.absolutePos(pos)), "stone is not a log");
        helper.succeed();
    }

    /** A piston pushes a coated plank and a placed log one block along; both marks go with them. */
    static void marksFollowAPiston(GameTestHelper helper) {
        BlockPos piston = new BlockPos(1, 1, 3);
        BlockPos plank = new BlockPos(2, 1, 3);
        BlockPos log = new BlockPos(3, 1, 3);
        helper.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        helper.setBlock(plank, Blocks.OAK_PLANKS);
        helper.setBlock(log, Blocks.OAK_LOG);
        var level = helper.getLevel();
        Fireproofing.coat(level, helper.absolutePos(plank));
        PlacedLogs.mark(level, helper.absolutePos(log), true);
        helper.setBlock(piston.below(), Blocks.REDSTONE_BLOCK);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.OAK_PLANKS, plank.east());
            helper.assertBlockPresent(Blocks.OAK_LOG, log.east());
            check(helper, Fireproofing.isCoated(level, helper.absolutePos(plank.east())), "the coating moved with the plank");
            check(helper, !Fireproofing.isCoated(level, helper.absolutePos(plank)), "and did not stay behind");
            check(helper, PlacedLogs.isPlaced(level, helper.absolutePos(log.east())), "the log is still known as placed");
            check(helper, !PlacedLogs.isPlaced(level, helper.absolutePos(log)), "its old square is clear");
        });
    }

    static void newBlockIsNotCoated(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, Blocks.OAK_PLANKS);
        Fireproofing.coat(helper.getLevel(), helper.absolutePos(pos));
        helper.setBlock(pos, Blocks.AIR); // gone without anyone breaking it, the way a machine takes a block
        placeByHand(helper, new ItemStack(Items.OAK_PLANKS), pos);
        helper.assertBlockPresent(Blocks.OAK_PLANKS, pos);
        check(helper, !Fireproofing.isCoated(helper.getLevel(), helper.absolutePos(pos)), "a plank put down on the square is not coated");
        helper.succeed();
    }

    static void timberSneakBreaksOne(GameTestHelper helper) {
        List<BlockPos> logs = buildTree(helper, false);
        ServerPlayer player = playerWith(helper, new ItemStack(Items.DIAMOND_AXE));
        player.setShiftKeyDown(true);

        player.gameMode.destroyBlock(helper.absolutePos(logs.get(0)));

        helper.assertBlockNotPresent(Blocks.OAK_LOG, logs.get(0));
        helper.assertBlockPresent(Blocks.OAK_LOG, logs.get(1));
        helper.succeed();
    }

    static void veinMinesConnectedOre(GameTestHelper helper) {
        BlockPos origin = new BlockPos(2, 1, 2);
        List<BlockPos> vein = List.of(origin, new BlockPos(3, 1, 2), new BlockPos(4, 2, 3));
        vein.forEach(p -> helper.setBlock(p, Blocks.IRON_ORE));
        BlockPos stone = new BlockPos(2, 1, 3);
        helper.setBlock(stone, Blocks.STONE);
        ServerPlayer player = playerWith(helper, new ItemStack(Items.DIAMOND_PICKAXE));

        player.gameMode.destroyBlock(helper.absolutePos(origin));

        vein.forEach(p -> helper.assertBlockNotPresent(Blocks.IRON_ORE, p));
        helper.assertBlockPresent(Blocks.STONE, stone);
        helper.succeedWhen(() -> helper.assertItemEntityCountIs(Items.RAW_IRON, new BlockPos(3, 1, 2), 3.0, 3));
    }

    /** Where stone gives way to deepslate, the vein goes on: iron ore and deepslate iron ore are one (c:ores/iron); coal is not. */
    static void veinRunsOnIntoDeepslate(GameTestHelper helper) {
        BlockPos origin = new BlockPos(2, 1, 2);
        helper.setBlock(origin, Blocks.IRON_ORE);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.DEEPSLATE_IRON_ORE);
        helper.setBlock(new BlockPos(4, 1, 2), Blocks.IRON_ORE);
        BlockPos coal = new BlockPos(2, 1, 3);
        helper.setBlock(coal, Blocks.DEEPSLATE_COAL_ORE);
        ServerPlayer player = playerWith(helper, new ItemStack(Items.DIAMOND_PICKAXE));
        player.gameMode.destroyBlock(helper.absolutePos(origin));
        helper.assertBlockNotPresent(Blocks.DEEPSLATE_IRON_ORE, new BlockPos(3, 1, 2));
        helper.assertBlockNotPresent(Blocks.IRON_ORE, new BlockPos(4, 1, 2));
        helper.assertBlockPresent(Blocks.DEEPSLATE_COAL_ORE, coal);
        helper.succeed();
    }

    static void veinNeedsProperPickaxe(GameTestHelper helper) {
        BlockPos origin = new BlockPos(2, 1, 2);
        helper.setBlock(origin, Blocks.IRON_ORE);
        helper.setBlock(origin.east(), Blocks.IRON_ORE);
        ServerPlayer player = playerWith(helper, new ItemStack(Items.WOODEN_PICKAXE));

        player.gameMode.destroyBlock(helper.absolutePos(origin));

        helper.assertBlockPresent(Blocks.IRON_ORE, origin.east());
        helper.succeed();
    }

    static void veinStopsBeforeToolBreaks(GameTestHelper helper) {
        for (int x = 1; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 1, 3), Blocks.COAL_ORE);
        }
        ItemStack pickaxe = new ItemStack(Items.STONE_PICKAXE);
        pickaxe.setDamageValue(pickaxe.getMaxDamage() - 3);
        ServerPlayer player = playerWith(helper, pickaxe);

        player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(1, 1, 3)));

        ItemStack held = player.getMainHandItem();
        check(helper, held.is(Items.STONE_PICKAXE), "pickaxe must survive");
        check(helper, held.getMaxDamage() - held.getDamageValue() == 1, "pickaxe should end with 1 durability left");
        helper.assertBlockNotPresent(Blocks.COAL_ORE, new BlockPos(2, 1, 3));
        helper.assertBlockPresent(Blocks.COAL_ORE, new BlockPos(3, 1, 3));
        helper.succeed();
    }

    /**
     * GitHub issue #1: Just Hammers posts a break event for each block of its 5x5 while the block clicked is broken;
     * vein mining or felling from those went far outside the area. Only the player's own break starts one.
     */
    static void veinIgnoresAreaBreaks(GameTestHelper helper) {
        BlockPos stone = new BlockPos(1, 1, 1);
        BlockPos ore = new BlockPos(3, 1, 3);
        BlockPos rest = new BlockPos(4, 1, 3);
        helper.setBlock(stone, Blocks.STONE);
        helper.setBlock(ore, Blocks.IRON_ORE);
        helper.setBlock(rest, Blocks.IRON_ORE);
        BlockPos log = new BlockPos(1, 1, 4);
        helper.setBlock(log, Blocks.OAK_LOG);
        helper.setBlock(log.above(), Blocks.OAK_LOG);
        helper.setBlock(log.above(2), Blocks.OAK_LEAVES);
        ServerPlayer player = playerWith(helper, new ItemStack(Items.DIAMOND_PICKAXE));
        ServerLevel level = helper.getLevel();
        BlockPos oreAt = helper.absolutePos(ore);

        // asked while another block is being broken, as Just Hammers does, and asked outside any break
        ChainBreaking.destroying(player, helper.absolutePos(stone), () ->
                !NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, oreAt, level.getBlockState(oreAt), player)).isCanceled());
        NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, oreAt, level.getBlockState(oreAt), player));
        helper.assertBlockPresent(Blocks.IRON_ORE, rest);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_AXE));
        BlockPos logAt = helper.absolutePos(log);
        NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, logAt, level.getBlockState(logAt), player));
        helper.assertBlockPresent(Blocks.OAK_LOG, log.above());

        // the player's own break still mines the vein
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        player.gameMode.destroyBlock(oreAt);
        helper.assertBlockNotPresent(Blocks.IRON_ORE, rest);
        helper.succeed();
    }

    /** Guards against the "filled crate duplicates when placed" report: in survival the item must be used up. */
    static void cratePlacedIsConsumed(GameTestHelper helper) {
        ItemStack filled = new ItemStack(ModItems.CRATE.get());
        filled.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 7))));
        Player survival = helper.makeMockPlayer(GameType.SURVIVAL);
        survival.setItemInHand(InteractionHand.MAIN_HAND, filled);

        BlockPos floor = helper.absolutePos(new BlockPos(3, 0, 3));
        survival.getMainHandItem().useOn(new UseOnContext(survival, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));

        BlockPos placed = new BlockPos(3, 1, 3);
        helper.assertBlockPresent(ModBlocks.CRATE.get(), placed);
        check(helper, survival.getMainHandItem().isEmpty(), "survival player must lose the placed crate");
        ItemStack inside = helper.getBlockEntity(placed, CrateBlockEntity.class).getItem(0);
        check(helper, inside.is(Items.DIAMOND) && inside.getCount() == 7, "placed crate must restore its contents");
        helper.succeed();
    }

    static void sconceLightAndSnuff(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.SCONCE.get());
        check(helper, !helper.getBlockState(pos).getValue(SconceBlock.LIT), "sconce must be placed unlit");
        check(helper, helper.getBlockState(pos).getLightEmission() == 0, "unlit sconce must give no light");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
        helper.useBlock(pos, player);
        check(helper, helper.getBlockState(pos).getValue(SconceBlock.LIT), "flint and steel should light the sconce");
        check(helper, helper.getBlockState(pos).getLightEmission() == SconceBlock.LIGHT_LEVEL, "lit sconce should give light");
        helper.assertBlockNotPresent(Blocks.FIRE, pos.above());

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.useBlock(pos, player);
        check(helper, !helper.getBlockState(pos).getValue(SconceBlock.LIT), "empty hand should snuff the sconce");
        helper.succeed();
    }

    // ---- Hammer ----

    /** Stone wall in the x=4 plane (y 1..3, z 2..4) with the centre at (4,2,3). */
    static final BlockPos WALL_CENTRE = new BlockPos(4, 2, 3);

    static List<BlockPos> buildWall(GameTestHelper helper) {
        List<BlockPos> wall = new ArrayList<>();
        for (int y = 1; y <= 3; y++) {
            for (int z = 2; z <= 4; z++) {
                BlockPos p = new BlockPos(4, y, z);
                helper.setBlock(p, Blocks.STONE);
                wall.add(p);
            }
        }
        helper.setBlock(WALL_CENTRE.east(), Blocks.STONE); // behind the wall: must survive
        return wall;
    }

    /** A survival player standing west of the wall, looking straight at its centre. */
    static ServerPlayer playerFacingWall(GameTestHelper helper, ItemStack tool) {
        ServerPlayer player = playerWith(helper, tool);
        Vec3 feet = helper.absoluteVec(new Vec3(1.5, WALL_CENTRE.getY() + 0.5 - player.getEyeHeight(), 3.5));
        player.snapTo(feet.x, feet.y, feet.z, -90.0F, 0.0F); // yaw -90 = looking towards +X
        player.setYHeadRot(-90.0F); // the view ray follows the head, which real clients keep in sync
        return player;
    }

    static void hammerMines3x3(GameTestHelper helper) {
        List<BlockPos> wall = buildWall(helper);
        BlockPos obsidianCorner = new BlockPos(4, 3, 4);
        helper.setBlock(obsidianCorner, Blocks.OBSIDIAN);
        ServerPlayer player = playerFacingWall(helper, new ItemStack(ModItems.DIAMOND_HAMMER.get()));

        player.gameMode.destroyBlock(helper.absolutePos(WALL_CENTRE));

        for (BlockPos p : wall) {
            if (!p.equals(obsidianCorner)) {
                helper.assertBlockNotPresent(Blocks.STONE, p);
            }
        }
        helper.assertBlockPresent(Blocks.OBSIDIAN, obsidianCorner);
        helper.assertBlockPresent(Blocks.STONE, WALL_CENTRE.east());
        int damage = player.getMainHandItem().getDamageValue();
        check(helper, damage == 8, "8 blocks mined should cost 8 durability, cost " + damage);
        helper.succeed();
    }

    static void hammerSneakMinesOne(GameTestHelper helper) {
        buildWall(helper);
        ServerPlayer player = playerFacingWall(helper, new ItemStack(ModItems.IRON_HAMMER.get()));
        player.setShiftKeyDown(true);

        player.gameMode.destroyBlock(helper.absolutePos(WALL_CENTRE));

        helper.assertBlockNotPresent(Blocks.STONE, WALL_CENTRE);
        helper.assertBlockPresent(Blocks.STONE, WALL_CENTRE.above());
        helper.succeed();
    }

    static void hammerSparesUnsuitable(GameTestHelper helper) {
        buildWall(helper);
        BlockPos furnace = new BlockPos(4, 1, 2);
        BlockPos dirt = new BlockPos(4, 1, 4);
        BlockPos diamondOre = new BlockPos(4, 3, 2);
        BlockPos ironOre = new BlockPos(4, 3, 4);
        helper.setBlock(furnace, Blocks.FURNACE);
        helper.setBlock(dirt, Blocks.DIRT);
        helper.setBlock(diamondOre, Blocks.DIAMOND_ORE);
        helper.setBlock(ironOre, Blocks.IRON_ORE);
        helper.setBlock(ironOre.east(), Blocks.IRON_ORE); // same vein, outside the 3x3: no vein mining from hammers
        BlockPos budding = new BlockPos(4, 2, 4); // a geode's: broken, it is gone for good and drops nothing
        helper.setBlock(budding, Blocks.BUDDING_AMETHYST);
        ServerPlayer player = playerFacingWall(helper, new ItemStack(ModItems.STONE_HAMMER.get()));

        player.gameMode.destroyBlock(helper.absolutePos(WALL_CENTRE));

        helper.assertBlockPresent(Blocks.BUDDING_AMETHYST, budding);
        helper.assertBlockPresent(Blocks.FURNACE, furnace);
        helper.assertBlockPresent(Blocks.DIRT, dirt);
        helper.assertBlockPresent(Blocks.DIAMOND_ORE, diamondOre);
        helper.assertBlockNotPresent(Blocks.IRON_ORE, ironOre);
        helper.assertBlockPresent(Blocks.IRON_ORE, ironOre.east());
        helper.assertBlockNotPresent(Blocks.STONE, WALL_CENTRE.above());
        helper.succeed();
    }

    static void hammerMinesFloor(GameTestHelper helper) {
        List<BlockPos> floor = new ArrayList<>();
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                BlockPos p = new BlockPos(x, 1, z);
                helper.setBlock(p, Blocks.STONE);
                floor.add(p);
            }
        }
        ServerPlayer player = playerWith(helper, new ItemStack(ModItems.IRON_HAMMER.get()));
        Vec3 feet = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
        player.snapTo(feet.x, feet.y, feet.z, 0.0F, 90.0F); // looking straight down

        player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(3, 1, 3)));

        floor.forEach(p -> helper.assertBlockNotPresent(Blocks.STONE, p));
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 0, 3)); // arena floor below stays
        helper.succeed();
    }

    static ItemStack earthmoverHammer(GameTestHelper helper) {
        ItemStack hammer = new ItemStack(ModItems.IRON_HAMMER.get());
        hammer.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Earthmover.KEY), 1);
        return hammer;
    }

    /** Stone centre with dirt, gravel, a log and planks around it. */
    static List<BlockPos> buildMixedWall(GameTestHelper helper) {
        List<BlockPos> wall = buildWall(helper);
        helper.setBlock(new BlockPos(4, 1, 2), Blocks.DIRT);
        helper.setBlock(new BlockPos(4, 1, 3), Blocks.GRAVEL);
        helper.setBlock(new BlockPos(4, 3, 2), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(4, 3, 4), Blocks.OAK_PLANKS);
        return wall;
    }

    static void hammerOnDirtNoSweep(GameTestHelper helper) {
        buildWall(helper);
        helper.setBlock(WALL_CENTRE, Blocks.DIRT);
        ServerPlayer player = playerFacingWall(helper, new ItemStack(ModItems.IRON_HAMMER.get()));

        player.gameMode.destroyBlock(helper.absolutePos(WALL_CENTRE));

        helper.assertBlockNotPresent(Blocks.DIRT, WALL_CENTRE);
        helper.assertBlockPresent(Blocks.STONE, WALL_CENTRE.above());
        helper.succeed();
    }

    static void earthmoverSweeps(GameTestHelper helper) {
        List<BlockPos> wall = buildMixedWall(helper);
        ServerPlayer player = playerFacingWall(helper, earthmoverHammer(helper));

        player.gameMode.destroyBlock(helper.absolutePos(WALL_CENTRE));

        for (BlockPos p : wall) {
            check(helper, helper.getBlockState(p).isAir(), "Earthmover should clear " + p + ", found " + helper.getBlockState(p));
        }
        helper.assertBlockPresent(Blocks.STONE, WALL_CENTRE.east());
        helper.succeed();
    }

    static void earthmoverDigsFast(GameTestHelper helper) {
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        Player plain = helper.makeMockPlayer(GameType.SURVIVAL);
        plain.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.IRON_HAMMER.get()));
        Player enchanted = helper.makeMockPlayer(GameType.SURVIVAL);
        enchanted.setItemInHand(InteractionHand.MAIN_HAND, earthmoverHammer(helper));

        float slow = plain.getDestroySpeed(dirt);
        float fast = enchanted.getDestroySpeed(dirt);
        float stone = enchanted.getDestroySpeed(Blocks.STONE.defaultBlockState());
        check(helper, fast > slow * 2, "Earthmover should dig dirt much faster: " + slow + " -> " + fast);
        check(helper, Math.abs(fast - stone) < 0.01F, "dirt should dig at the hammer's stone speed " + stone + ", got " + fast);
        helper.succeed();
    }

    private MiningTests() {}
}
