package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.item.BuildingWandItem;
import dev.nookworks.item.WandBox;
import dev.nookworks.item.WandMode;
import dev.nookworks.item.WandWork;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import dev.nookworks.port.test.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Building Wands (the user, 2026-11-07): either taking its last use back, and stopping short of breaking; the plain
 * wand only spreading, whatever it is told; the Reinforced Building Wand's modes - replacing a surface with the block in
 * the other hand, within its limits (a full block in, no ores, nothing harder than the deepslate stones), a box filled
 * whole, by its walls or by its edges, the box's limits - and its recipe. The arena's stone floor is y = 0.
 */
final class WandTests {
    private WandTests() {}

    static void register() {
        test("wand_takes_its_last_use_back", WandTests::takesBack);
        test("wand_takes_back_only_what_still_stands", WandTests::takesBackWhatStands);
        test("wand_replaces_a_surface_with_the_other_hand", WandTests::replaces);
        test("wand_fills_a_box_whole_by_walls_and_by_edges", 200, WandTests::boxes);
        test("wand_box_keeps_to_its_limits", WandTests::limits);
        test("wand_stops_short_of_breaking", WandTests::stopsShort);
        test("wand_takes_back_no_block_changed_since", WandTests::changedSince);
        test("wand_mends_only_the_wear_it_took", WandTests::mendsWhatItTook);
        test("wand_replace_digs_the_same_again", WandTests::digsTheSame);
        test("plain_wand_has_no_modes", WandTests::plainHasNoModes);
        test("reinforced_wand_steps_its_modes", WandTests::reinforcedSteps);
        test("reinforced_wand_takes_its_last_use_back", WandTests::reinforcedTakesBack);
        test("wand_replace_puts_in_only_full_blocks", WandTests::replaceFullBlocksOnly);
        test("wand_replace_leaves_ores_alone", WandTests::replaceLeavesOres);
        test("wand_replace_leaves_hard_blocks_alone", WandTests::replaceLeavesHardBlocks);
        test("reinforced_wand_is_made_from_the_wand_and_velarium", WandTests::recipe);
    }

    /** A survival player holding the plain Building Wand. */
    private static ServerPlayer player(GameTestHelper helper, List<Component> said) {
        ServerPlayer player = MockPlayers.hearing(helper, GameType.SURVIVAL, said);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BUILDING_WAND.get()));
        return player;
    }

    /** A survival player holding the Reinforced Building Wand, the one with modes. */
    private static ServerPlayer reinforced(GameTestHelper helper, List<Component> said) {
        ServerPlayer player = MockPlayers.hearing(helper, GameType.SURVIVAL, said);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.REINFORCED_BUILDING_WAND.get()));
        return player;
    }

    private static boolean saidLast(List<Component> said, String key) {
        return !said.isEmpty() && said.getLast().getString().equals(Component.translatable("message.nookworks.wand." + key).getString());
    }

    private static void click(GameTestHelper helper, ServerPlayer player, BlockPos pos, Direction face) {
        BlockPos at = helper.absolutePos(pos);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), face, at, false)));
    }

    private static void air(ServerPlayer player, boolean sneaking) {
        player.setShiftKeyDown(sneaking);
        player.getMainHandItem().getItem().use(player.level(), player, InteractionHand.MAIN_HAND);
        player.setShiftKeyDown(false);
    }

    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        int n = player.getInventory().items.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
        return n + (player.getOffhandItem().is(item) ? player.getOffhandItem().getCount() : 0);
    }

    /** A 3x3 floor of planks on the stone, at y = 1. */
    private static void floor(GameTestHelper helper) {
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.OAK_PLANKS);
            }
        }
    }

    /** Spread over a floor and taken back: the floor as it was, the planks and the wear given back. */
    private static void takesBack(GameTestHelper helper) {
        List<Component> said = new ArrayList<>();
        ServerPlayer player = player(helper, said);
        floor(helper);
        player.getInventory().setItem(1, new ItemStack(Items.OAK_PLANKS, 20));
        click(helper, player, new BlockPos(3, 1, 3), Direction.UP);
        check(helper, helper.getBlockState(new BlockPos(2, 2, 2)).is(Blocks.OAK_PLANKS) && count(player, Items.OAK_PLANKS) == 11,
                "nine planks spread: " + count(player, Items.OAK_PLANKS) + " left");
        check(helper, player.getMainHandItem().getDamageValue() == 9, "the wand wore nine");
        air(player, true);
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                check(helper, helper.getBlockState(new BlockPos(x, 2, z)).isAir(), "taken back at " + x + ", " + z);
            }
        }
        check(helper, count(player, Items.OAK_PLANKS) == 20, "the planks back: " + count(player, Items.OAK_PLANKS));
        check(helper, player.getMainHandItem().getDamageValue() == 0, "the wand mended");
        air(player, true);
        check(helper, said.getLast().getString().equals(Component.translatable("message.nookworks.wand.nothing_to_undo").getString()),
                "only the last use is taken back");
        helper.succeed();
    }

    /** A square changed since is left as it is, and nothing is handed back for it. */
    private static void takesBackWhatStands(GameTestHelper helper) {
        ServerPlayer player = player(helper, new ArrayList<>());
        floor(helper);
        player.getInventory().setItem(1, new ItemStack(Items.OAK_PLANKS, 9));
        click(helper, player, new BlockPos(3, 1, 3), Direction.UP);
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.DIRT);
        air(player, true);
        check(helper, helper.getBlockState(new BlockPos(3, 2, 3)).is(Blocks.DIRT), "the dirt put there since stays");
        check(helper, count(player, Items.OAK_PLANKS) == 8, "eight planks back, not nine: " + count(player, Items.OAK_PLANKS));
        helper.succeed();
    }

    /**
     * Replace: a stone floor turned to planks from the other hand, the stone coming back as the player's pickaxe digs it
     * (cobblestone); with no pickaxe nothing is touched; taken back, the stone stands again and the cobblestone goes.
     */
    private static void replaces(GameTestHelper helper) {
        List<Component> said = new ArrayList<>();
        ServerPlayer player = reinforced(helper, said);
        player.getMainHandItem().set(ModDataComponents.WAND_MODE.get(), WandMode.REPLACE);
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.OAK_PLANKS, 16));
        click(helper, player, new BlockPos(3, 1, 3), Direction.UP);
        check(helper, helper.getBlockState(new BlockPos(3, 1, 3)).is(Blocks.STONE), "no pickaxe, no change");
        check(helper, said.getLast().getString().equals(Component.translatable("message.nookworks.wand.no_tool").getString()),
                "and it says why: " + said.getLast().getString());
        player.getInventory().setItem(5, new ItemStack(Items.IRON_PICKAXE));
        click(helper, player, new BlockPos(3, 1, 3), Direction.UP);
        check(helper, helper.getBlockState(new BlockPos(2, 1, 2)).is(Blocks.OAK_PLANKS)
                && helper.getBlockState(new BlockPos(4, 1, 4)).is(Blocks.OAK_PLANKS), "the floor is planks: " + said);
        check(helper, helper.getBlockState(new BlockPos(2, 0, 2)).is(Blocks.STONE), "the arena's floor below untouched");
        check(helper, count(player, Items.COBBLESTONE) == 9 && count(player, Items.OAK_PLANKS) == 7,
                "nine cobblestone for nine planks: " + count(player, Items.COBBLESTONE) + ", " + count(player, Items.OAK_PLANKS));
        air(player, true);
        check(helper, helper.getBlockState(new BlockPos(3, 1, 3)).is(Blocks.STONE), "taken back, stone again");
        check(helper, count(player, Items.COBBLESTONE) == 0 && count(player, Items.OAK_PLANKS) == 16, "and the cobblestone gone, the planks back");
        helper.succeed();
    }

    /** Three boxes of 3x3x3 in the air, whole (27), walls (26) and edges (20), a share each tick. */
    private static void boxes(GameTestHelper helper) {
        ServerPlayer player = reinforced(helper, new ArrayList<>());
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GLASS, 64));
        player.getInventory().setItem(1, new ItemStack(Items.GLASS, 64));
        int[][] at = {{0, 0}, {4, 0}, {0, 4}};
        WandMode[] modes = {WandMode.FILL, WandMode.HOLLOW, WandMode.FRAME};
        int[] wanted = {27, 26, 20};
        ItemStack wand = player.getMainHandItem();
        // one box at a time: a new use stops the one before
        List<Runnable> steps = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int x = at[i][0];
            int z = at[i][1];
            WandMode mode = modes[i];
            steps.add(() -> {
                wand.set(ModDataComponents.WAND_MODE.get(), mode);
                click(helper, player, new BlockPos(x, 3, z), Direction.UP); // first corner (x, 4, z)
                click(helper, player, new BlockPos(x + 2, 5, z + 2), Direction.UP); // second (x + 2, 6, z + 2)
                check(helper, wand.get(ModDataComponents.WAND_BOX.get()) != null, "the box is marked out");
                air(player, false); // fills it
                check(helper, wand.get(ModDataComponents.WAND_BOX.get()) == null, "and let go once begun");
            });
        }
        steps.getFirst().run();
        int[] step = {0};
        helper.succeedWhen(() -> {
            int i = step[0];
            int n = 0;
            for (int x = at[i][0]; x <= at[i][0] + 2; x++) {
                for (int y = 4; y <= 6; y++) {
                    for (int z = at[i][1]; z <= at[i][1] + 2; z++) {
                        n += helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.GLASS) ? 1 : 0;
                    }
                }
            }
            if (n != wanted[i]) {
                throw helper.assertionException(Component.literal(modes[i] + ": " + n + " of " + wanted[i]));
            }
            if (i < 2) {
                step[0]++;
                steps.get(step[0]).run();
                throw helper.assertionException(Component.literal("next box"));
            }
            check(helper, helper.getBlockState(new BlockPos(1, 5, 1)).is(Blocks.GLASS), "the whole box is glass through");
            check(helper, helper.getBlockState(new BlockPos(5, 5, 1)).isAir(), "the walls' box is hollow");
            check(helper, helper.getBlockState(new BlockPos(1, 4, 5)).isAir() && helper.getBlockState(new BlockPos(0, 4, 4)).is(Blocks.GLASS),
                    "the edges' box has no faces, only edges");
        });
    }

    /** A box over 32 a side, or over 4,096 blocks, is turned down at its second corner. */
    private static void limits(GameTestHelper helper) {
        check(helper, WandBox.fits(BlockPos.ZERO, new BlockPos(31, 3, 31)), "32 x 4 x 32 fits");
        check(helper, !WandBox.fits(BlockPos.ZERO, new BlockPos(32, 0, 0)), "33 long does not");
        check(helper, !WandBox.fits(BlockPos.ZERO, new BlockPos(31, 4, 31)), "32 x 5 x 32 (5,120) does not");
        List<Component> said = new ArrayList<>();
        ServerPlayer player = reinforced(helper, said);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GLASS, 64));
        ItemStack wand = player.getMainHandItem();
        wand.set(ModDataComponents.WAND_MODE.get(), WandMode.FILL);
        click(helper, player, new BlockPos(0, 0, 0), Direction.UP);
        click(helper, player, new BlockPos(40, 0, 0), Direction.UP);
        check(helper, wand.get(ModDataComponents.WAND_BOX.get()).second().isEmpty(), "the far corner turned down");
        check(helper, said.getLast().getString().startsWith(Component.translatable("message.nookworks.wand.too_big", 32, 4096).getString()),
                "and it says so: " + said.getLast().getString());
        air(player, true);
        check(helper, wand.get(ModDataComponents.WAND_BOX.get()) == null, "a sneaking click at the air lets the box go");
        // a box handed over whole in the wand's data (a creative client's item, a command), not marked out by clicks:
        // looked at again before it is filled (r22 CXC-005: one of any size was listed whole on the server thread)
        BlockPos a = helper.absolutePos(new BlockPos(0, 1, 0));
        var dimension = helper.getLevel().dimension();
        wand.set(ModDataComponents.WAND_BOX.get(), new WandBox(net.minecraft.core.GlobalPos.of(dimension, a),
                java.util.Optional.of(a.east(32)), Blocks.STONE.defaultBlockState()));
        air(player, false);
        check(helper, wand.get(ModDataComponents.WAND_BOX.get()) == null && !WandWork.busy(player)
                && helper.getBlockState(new BlockPos(0, 1, 0)).isAir(), "a box 33 long handed over fills nothing");
        check(helper, said.getLast().getString().startsWith(Component.translatable("message.nookworks.wand.too_big", 32, 4096).getString()),
                "and it says it is too big: " + said.getLast().getString());
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        wand.set(ModDataComponents.WAND_BOX.get(), new WandBox(net.minecraft.core.GlobalPos.of(dimension, a),
                java.util.Optional.of(a.east(2)), Blocks.CHEST.defaultBlockState()));
        air(player, false);
        check(helper, !WandWork.busy(player) && helper.getBlockState(new BlockPos(1, 1, 0)).isAir(),
                "nor one of a block the wand cannot place, handed over as the box's block");
        helper.succeed();
    }

    /**
     * A slab the wand put down and a player then made double is not the wand's any more: taking back leaves it, and gives
     * nothing for it (r1 review: a bitten cake came back whole, a slab broken and replaced gave a slab).
     */
    private static void changedSince(GameTestHelper helper) {
        ServerPlayer player = player(helper, new ArrayList<>());
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.OAK_SLAB); // a floor of bottom slabs
            }
        }
        player.getInventory().setItem(1, new ItemStack(Items.OAK_SLAB, 20));
        click(helper, player, new BlockPos(3, 1, 3), Direction.UP);
        int placed = 20 - count(player, Items.OAK_SLAB);
        check(helper, placed == 9, "nine slabs on the slabs: " + placed);
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.OAK_SLAB.defaultBlockState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE,
                net.minecraft.world.level.block.state.properties.SlabType.DOUBLE));
        air(player, true);
        check(helper, helper.getBlockState(new BlockPos(2, 2, 2)).is(Blocks.OAK_SLAB), "the slab made double since stays");
        check(helper, helper.getBlockState(new BlockPos(4, 2, 4)).isAir(), "the others are taken back");
        check(helper, count(player, Items.OAK_SLAB) == 19, "eight slabs back, not nine: " + count(player, Items.OAK_SLAB));
        helper.succeed();
    }

    /** With Unbreaking the wand wears less than a point a block; taking back mends what it wore, no more. */
    private static void mendsWhatItTook(GameTestHelper helper) {
        ServerPlayer player = player(helper, new ArrayList<>());
        ItemStack wand = player.getMainHandItem();
        wand.enchant(helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 3);
        wand.setDamageValue(100);
        floor(helper);
        player.getInventory().setItem(1, new ItemStack(Items.OAK_PLANKS, 20));
        click(helper, player, new BlockPos(3, 1, 3), Direction.UP);
        check(helper, wand.getDamageValue() >= 100, "worn some, or none");
        air(player, true);
        check(helper, wand.getDamageValue() == 100, "mended back to where it was, no further: " + wand.getDamageValue());
        helper.succeed();
    }

    /** Leaves replaced, taken back and replaced again give the same: what comes out is not rolled again. */
    private static void digsTheSame(GameTestHelper helper) {
        ServerPlayer player = reinforced(helper, new ArrayList<>());
        player.getMainHandItem().set(ModDataComponents.WAND_MODE.get(), WandMode.REPLACE);
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.OAK_LEAVES.defaultBlockState().setValue(
                        net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
            }
        }
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.OAK_PLANKS, 64));
        List<String> rolls = new ArrayList<>();
        for (int round = 0; round < 2; round++) {
            click(helper, player, new BlockPos(3, 1, 3), Direction.UP);
            check(helper, helper.getBlockState(new BlockPos(3, 1, 3)).is(Blocks.OAK_PLANKS), "replaced, round " + round);
            rolls.add(count(player, Items.OAK_SAPLING) + " saplings, " + count(player, Items.STICK) + " sticks, "
                    + count(player, Items.APPLE) + " apples");
            air(player, true);
            check(helper, helper.getBlockState(new BlockPos(3, 1, 3)).is(Blocks.OAK_LEAVES), "taken back, round " + round);
        }
        check(helper, rolls.get(0).equals(rolls.get(1)), "the same both times: " + rolls);
        helper.succeed();
    }

    /** With five points of wear left the wand puts down four blocks and stops, whole. */
    private static void stopsShort(GameTestHelper helper) {
        List<Component> said = new ArrayList<>();
        ServerPlayer player = player(helper, said);
        ItemStack wand = player.getMainHandItem();
        wand.setDamageValue(wand.getMaxDamage() - 5);
        floor(helper);
        player.getInventory().setItem(1, new ItemStack(Items.OAK_PLANKS, 20));
        click(helper, player, new BlockPos(3, 1, 3), Direction.UP);
        check(helper, count(player, Items.OAK_PLANKS) == 16, "four planks put down: " + count(player, Items.OAK_PLANKS));
        check(helper, !wand.isEmpty() && wand.getDamageValue() == wand.getMaxDamage() - 1, "the wand whole, one point left");
        check(helper, said.getLast().getString().equals(Component.translatable("message.nookworks.wand.worn", 4).getString()),
                "and it says why: " + said.getLast().getString());
        helper.succeed();
    }

    /**
     * The plain wand only spreads (the user, 2026-11-07): a scroll does not step it, a mode written on it (as a wand of
     * the trial carries) is not heeded - with stone in the other hand a click on a planks floor spreads planks, replacing
     * nothing - and no box corner is marked; its use is taken back as before.
     */
    private static void plainHasNoModes(GameTestHelper helper) {
        List<Component> said = new ArrayList<>();
        ServerPlayer player = player(helper, said);
        ItemStack wand = player.getMainHandItem();
        check(helper, !BuildingWandItem.hasModes(wand), "the plain wand has no modes");
        BuildingWandItem.stepMode(player, wand, 1);
        check(helper, wand.get(ModDataComponents.WAND_MODE.get()) == null && said.isEmpty(), "a scroll steps nothing");
        wand.set(ModDataComponents.WAND_MODE.get(), WandMode.REPLACE);
        check(helper, BuildingWandItem.mode(wand) == WandMode.SPREAD, "a mode written on it is not heeded");
        floor(helper);
        player.getInventory().setItem(5, new ItemStack(Items.IRON_PICKAXE));
        player.getInventory().setItem(1, new ItemStack(Items.OAK_PLANKS, 20));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE, 16));
        click(helper, player, new BlockPos(3, 1, 3), Direction.UP);
        check(helper, helper.getBlockState(new BlockPos(3, 1, 3)).is(Blocks.OAK_PLANKS)
                && helper.getBlockState(new BlockPos(3, 2, 3)).is(Blocks.OAK_PLANKS), "it spread, it did not replace");
        check(helper, count(player, Items.STONE) == 16 && count(player, Items.COBBLESTONE) == 0, "no stone used, nothing dug");
        air(player, true);
        check(helper, helper.getBlockState(new BlockPos(3, 2, 3)).isAir() && count(player, Items.OAK_PLANKS) == 20, "and taken back");
        wand.set(ModDataComponents.WAND_MODE.get(), WandMode.FILL);
        click(helper, player, new BlockPos(3, 1, 3), Direction.UP);
        check(helper, wand.get(ModDataComponents.WAND_BOX.get()) == null, "a box mode written on it marks no corner");
        helper.succeed();
    }

    /** The reinforced wand steps round its five modes, scrolled either way, naming each, and a step lets a box go. */
    private static void reinforcedSteps(GameTestHelper helper) {
        List<Component> said = new ArrayList<>();
        ServerPlayer player = reinforced(helper, said);
        ItemStack wand = player.getMainHandItem();
        check(helper, BuildingWandItem.hasModes(wand) && BuildingWandItem.mode(wand) == WandMode.SPREAD, "it starts at Spread");
        BuildingWandItem.stepMode(player, wand, 1);
        check(helper, BuildingWandItem.mode(wand) == WandMode.REPLACE, "one on is Replace");
        check(helper, said.getLast().getString().equals(Component.translatable("message.nookworks.wand.mode",
                WandMode.REPLACE.title()).getString()), "and the action bar names it");
        BuildingWandItem.stepMode(player, wand, -1);
        BuildingWandItem.stepMode(player, wand, -1);
        check(helper, BuildingWandItem.mode(wand) == WandMode.FRAME, "back round to the last");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GLASS, 8));
        click(helper, player, new BlockPos(1, 0, 1), Direction.UP);
        check(helper, wand.get(ModDataComponents.WAND_BOX.get()) != null, "a corner marked");
        BuildingWandItem.stepMode(player, wand, 1);
        check(helper, wand.get(ModDataComponents.WAND_BOX.get()) == null, "a step lets the box go");
        helper.succeed();
    }

    /** The reinforced wand takes back as the plain one does: its spread, here, with the wear. */
    private static void reinforcedTakesBack(GameTestHelper helper) {
        ServerPlayer player = reinforced(helper, new ArrayList<>());
        floor(helper);
        player.getInventory().setItem(1, new ItemStack(Items.OAK_PLANKS, 20));
        click(helper, player, new BlockPos(3, 1, 3), Direction.UP);
        check(helper, count(player, Items.OAK_PLANKS) == 11 && player.getMainHandItem().getDamageValue() == 9, "nine spread");
        air(player, true);
        check(helper, helper.getBlockState(new BlockPos(3, 2, 3)).isAir() && count(player, Items.OAK_PLANKS) == 20
                && player.getMainHandItem().getDamageValue() == 0, "and taken back, mended");
        helper.succeed();
    }

    /** A 3x3 floor of {@code block} at y = 1, and a player with a diamond pickaxe and the reinforced wand in Replace. */
    private static ServerPlayer replacing(GameTestHelper helper, List<Component> said, net.minecraft.world.level.block.Block block) {
        ServerPlayer player = reinforced(helper, said);
        player.getMainHandItem().set(ModDataComponents.WAND_MODE.get(), WandMode.REPLACE);
        player.getInventory().setItem(5, new ItemStack(Items.DIAMOND_PICKAXE));
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), block);
            }
        }
        return player;
    }

    /**
     * Only an ordinary full block goes in (the user, 2026-11-07: replacing with torches would tunnel through rock): a
     * torch, a carpet, a slab, a fence, a pane are turned down and it says why, the floor left as it was, the outline
     * told the same; planks go in.
     */
    private static void replaceFullBlocksOnly(GameTestHelper helper) {
        List<Component> said = new ArrayList<>();
        ServerPlayer player = replacing(helper, said, Blocks.STONE);
        BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3));
        for (var item : List.of(Items.TORCH, Items.MOSS_CARPET, Items.OAK_SLAB, Items.OAK_FENCE, Items.GLASS_PANE)) {
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(item, 16));
            click(helper, player, new BlockPos(3, 1, 3), Direction.UP);
            check(helper, helper.getBlockState(new BlockPos(3, 1, 3)).is(Blocks.STONE) && count(player, item) == 16,
                    item + " is not put in");
            check(helper, saidLast(said, "not_full_block"), "and it says why: " + said);
            check(helper, "not_full_block".equals(WandWork.replaceRefusal(helper.getLevel(), player, at, Blocks.STONE.defaultBlockState())),
                    "the outline is told the same");
        }
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.OAK_PLANKS, 16));
        check(helper, WandWork.replaceRefusal(helper.getLevel(), player, at, Blocks.STONE.defaultBlockState()) == null, "planks will do");
        click(helper, player, new BlockPos(3, 1, 3), Direction.UP);
        check(helper, helper.getBlockState(new BlockPos(3, 1, 3)).is(Blocks.OAK_PLANKS), "and go in");
        helper.succeed();
    }

    /** An ore surface is not replaced, however good the pickaxe: the wand is no free fast miner. */
    private static void replaceLeavesOres(GameTestHelper helper) {
        List<Component> said = new ArrayList<>();
        for (var ore : List.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.NETHER_QUARTZ_ORE, Blocks.ANCIENT_DEBRIS)) {
            ServerPlayer player = replacing(helper, said, ore);
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE, 16));
            click(helper, player, new BlockPos(3, 1, 3), Direction.UP);
            check(helper, helper.getBlockState(new BlockPos(3, 1, 3)).is(ore) && count(player, Items.STONE) == 16, ore + " left alone");
            check(helper, saidLast(said, "ore"), ore + ", and it says why: " + said);
        }
        helper.succeed();
    }

    /**
     * Nothing harder than the deepslate stones is replaced: obsidian, crying obsidian and a block of iron are left alone
     * and it says why; deepslate (3.0) and deepslate bricks (3.5) are replaced.
     */
    private static void replaceLeavesHardBlocks(GameTestHelper helper) {
        List<Component> said = new ArrayList<>();
        for (var hard : List.of(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.IRON_BLOCK)) {
            ServerPlayer player = replacing(helper, said, hard);
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE, 16));
            click(helper, player, new BlockPos(3, 1, 3), Direction.UP);
            check(helper, helper.getBlockState(new BlockPos(3, 1, 3)).is(hard) && count(player, Items.STONE) == 16, hard + " left alone");
            check(helper, saidLast(said, "too_hard"), hard + ", and it says why: " + said);
        }
        for (var stone : List.of(Blocks.DEEPSLATE, Blocks.DEEPSLATE_BRICKS)) {
            ServerPlayer player = replacing(helper, said, stone);
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE, 16));
            click(helper, player, new BlockPos(3, 1, 3), Direction.UP);
            check(helper, helper.getBlockState(new BlockPos(3, 1, 3)).is(Blocks.STONE), stone + " is replaced: " + said);
        }
        helper.succeed();
    }

    /**
     * A Building Wand and two velarium ingots make the Reinforced Building Wand, keeping the wand's wear and
     * enchantments; one ingot does not.
     */
    private static void recipe(GameTestHelper helper) {
        ItemStack wand = new ItemStack(ModItems.BUILDING_WAND.get());
        wand.setDamageValue(100);
        wand.enchant(helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 2);
        ItemStack ingot = new ItemStack(ModItems.VELARIUM_INGOT.get());
        ItemStack none = ItemStack.EMPTY;
        ItemStack made = MercuryTests.craft(helper, List.of(none, ingot, none, ingot, wand, none, none, none, none));
        check(helper, made.is(ModItems.REINFORCED_BUILDING_WAND.get()) && made.getCount() == 1, "the wand and two ingots: " + made);
        check(helper, made.getDamageValue() == 100 && made.getMaxDamage() == 8192, "the wear kept, the new wand's durability");
        check(helper, made.isEnchanted(), "the enchantment kept");
        check(helper, MercuryTests.craft(helper, List.of(none, ingot, none, none, wand, none, none, none, none)).isEmpty(),
                "one ingot is not enough");
        check(helper, helper.getLevel().getServer().getRecipeManager().byKey(dev.nookworks.Nookworks.id("reinforced_building_wand")).isPresent(),
                "the recipe is there by its name");
        helper.succeed();
    }
}
