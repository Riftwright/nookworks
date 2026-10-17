package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.List;

import dev.nookworks.feature.Fireproofing;
import dev.nookworks.feature.Lubrication;
import dev.nookworks.machine.MachineLayout;
import dev.nookworks.mobcapture.SpawnEggBrewerMenu;
import dev.nookworks.poster.PrintingPressMenu;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import dev.nookworks.sorter.SorterMenu;
import dev.nookworks.sorter.SorterTier;
import dev.nookworks.storage.StorageMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Right-clicks as the game makes them: through {@code ServerPlayerGameMode.useItemOn}, which asks the item first
 * ({@code onItemUseFirst}), then the block with the item, then the block as if the hand were empty, and only then the
 * item's own use. A test that calls a block's or an item's method straight skips that order - which is how a door
 * came to open instead of taking the oil, and a tank to swallow the pipe being set against it.
 */
final class ClickTests {
    static void register() {
        NookworksGameTests.test("a_wrench_turns_a_machine_instead_of_opening_it", ClickTests::aWrenchTurnsAMachine);
        NookworksGameTests.test("tools_work_on_doors_trapdoors_and_beds", ClickTests::toolsWorkOnDoorsAndBeds);
        NookworksGameTests.test("things_go_on_blocks_an_empty_hand_reads", ClickTests::thingsGoOnBlocksAnEmptyHandReads);
        NookworksGameTests.test("screens_fit_a_small_window", ClickTests::screensFitASmallWindow);
        NookworksGameTests.test("a_refused_click_sets_nothing_down", ClickTests::aRefusedClickSetsNothingDown);
    }

    /** A player in the level, with a connection to send it messages and screens, standing clear of the test. */
    private static ServerPlayer clicker(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        Vec3 aside = helper.absoluteVec(new Vec3(0.5, 8, 0.5));
        player.snapTo(aside.x, aside.y, aside.z);
        return player;
    }

    private static void done(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /** Right-clicks the middle of {@code face} of {@code pos} holding {@code stack}. */
    private static void click(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos pos, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos at = helper.absolutePos(pos);
        Vec3 point = Vec3.atCenterOf(at).relative(face, 0.5);
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND, new BlockHitResult(point, face, at, false));
    }

    /**
     * A click a block turns down is spent: a rope held to a rope that cannot reach lower (stone under its end) is not set
     * down against it all the same (r7 multiplayer test: the server went on from the block's FAIL to the held item, and
     * placed it - a warp receiver refused its pairing, then; receivers no longer pair).
     */
    private static void aRefusedClickSetsNothingDown(GameTestHelper helper) {
        BlockPos at = new BlockPos(3, 3, 3);
        helper.setBlock(at.above(), Blocks.STONE);
        helper.setBlock(at.above().east(), Blocks.STONE); // a rope could hang beside it, were it set down there
        helper.setBlock(at, ModBlocks.ROPE.get().defaultBlockState());
        helper.setBlock(at.below(), Blocks.STONE); // nowhere lower for the rope to go: the rope refuses
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 aside = helper.absoluteVec(new Vec3(0.5, 8, 0.5));
        player.snapTo(aside.x, aside.y, aside.z);
        ItemStack ropes = new ItemStack(ModBlocks.ROPE.get().asItem(), 2);
        click(helper, player, ropes, at, Direction.EAST);
        check(helper, helper.getBlockState(at.east()).isAir(), "nothing set down beside the refusing rope: " + helper.getBlockState(at.east()));
        check(helper, player.getMainHandItem().getCount() == 2, "the ropes stay in hand: " + player.getMainHandItem());
        done(helper, player);
    }

    /** The wrench turns a machine that has a screen, and the screen does not open. */
    private static void aWrenchTurnsAMachine(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.GENERATOR.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        ServerPlayer player = clicker(helper);
        click(helper, player, new ItemStack(ModItems.WRENCH.get()), pos, Direction.UP);
        check(helper, helper.getBlockState(pos).getValue(BlockStateProperties.HORIZONTAL_FACING) != Direction.NORTH,
                "the generator turned: it faces " + helper.getBlockState(pos).getValue(BlockStateProperties.HORIZONTAL_FACING));
        check(helper, player.containerMenu == player.inventoryMenu, "and its screen did not open");
        done(helper, player);
    }

    /** The oil can oils a door, the coating coats a fence gate, soap washes a bed - none of them opened or slept in. */
    private static void toolsWorkOnDoorsAndBeds(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerPlayer player = clicker(helper);

        BlockPos door = new BlockPos(1, 2, 1);
        helper.setBlock(door.below(), Blocks.STONE);
        helper.setBlock(door, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        click(helper, player, new ItemStack(ModOil.OIL_CAN.get()), door, Direction.NORTH);
        check(helper, Lubrication.isLubricated(level, helper.absolutePos(door)), "the door took the oil");
        check(helper, !helper.getBlockState(door).getValue(DoorBlock.OPEN), "and did not open instead");

        BlockPos gate = new BlockPos(3, 2, 1); // a wooden trapdoor or door does not burn in vanilla; a fence gate does
        helper.setBlock(gate, Blocks.OAK_FENCE_GATE);
        click(helper, player, new ItemStack(ModItems.FIREPROOF_COATING.get()), gate, Direction.NORTH);
        check(helper, Fireproofing.isCoated(level, helper.absolutePos(gate)), "the fence gate took the coating");
        check(helper, !helper.getBlockState(gate).getValue(FenceGateBlock.OPEN), "and did not open instead");

        BlockPos foot = new BlockPos(5, 2, 1);
        helper.setBlock(foot, Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
        helper.setBlock(foot.south(), Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
        click(helper, player, new ItemStack(ModOil.SOAP.get()), foot, Direction.UP);
        check(helper, helper.getBlockState(foot).is(Blocks.BED.pick(DyeColor.WHITE)) && helper.getBlockState(foot.south()).is(Blocks.BED.pick(DyeColor.WHITE)),
                "soap washed the bed white, both halves: " + helper.getBlockState(foot) + ", " + helper.getBlockState(foot.south()));
        check(helper, !player.isSleeping(), "rather than going to bed");
        done(helper, player);
    }

    /**
     * Blocks whose empty-hand click only reads them out or takes something out leave any other click to the item: a
     * lantern goes on a grill standing as a table, and a pipe on an experience tank (whose tap stays shut), a copper
     * tank, a water pump and a mercury tank.
     */
    private static void thingsGoOnBlocksAnEmptyHandReads(GameTestHelper helper) {
        ServerPlayer player = clicker(helper);
        BlockPos grill = new BlockPos(1, 2, 1);
        helper.setBlock(grill, ModCooking.GRILL.get());
        click(helper, player, new ItemStack(Blocks.LANTERN), grill, Direction.UP);
        check(helper, helper.getBlockState(grill.above()).is(Blocks.LANTERN), "a lantern stands on the grill: " + helper.getBlockState(grill.above()));

        BlockPos xp = new BlockPos(3, 2, 1);
        helper.setBlock(xp, ModBlocks.EXPERIENCE_TANK.get());
        click(helper, player, new ItemStack(ModBlocks.EXPERIENCE_PIPE.get()), xp, Direction.UP);
        check(helper, helper.getBlockState(xp.above()).is(ModBlocks.EXPERIENCE_PIPE.get()), "a pipe goes on the experience tank");
        check(helper, !helper.getBlockState(xp).getValue(BlockStateProperties.OPEN), "and its tap stays shut");
        click(helper, player, ItemStack.EMPTY, xp, Direction.NORTH);
        check(helper, helper.getBlockState(xp).getValue(BlockStateProperties.OPEN), "an empty hand still turns the tap");

        for (var tank : List.of(new BlockPos(5, 2, 1), new BlockPos(1, 2, 4), new BlockPos(3, 2, 4))) {
            var block = tank.getX() == 5 ? ModBlocks.COPPER_TANK.get() : tank.getX() == 1 ? ModBlocks.WATER_PUMP.get() : ModBlocks.MERCURY_TANK.get();
            var pipe = block == ModBlocks.MERCURY_TANK.get() ? ModBlocks.MERCURY_PIPE.get() : ModBlocks.COPPER_PIPE.get();
            helper.setBlock(tank, block);
            click(helper, player, new ItemStack(pipe), tank, Direction.UP);
            check(helper, helper.getBlockState(tank.above()).is(pipe), "a pipe goes on the " + block.getName().getString());
        }
        done(helper, player);
    }

    /**
     * Every screen fits the 240 px a small window gives the GUI (854x480 at scale 2, 1280x720 at scale 3): taller, its
     * title goes off the top of the window. The biggest is the Storage Barrel's, six rows and the inventory.
     */
    private static void screensFitASmallWindow(GameTestHelper helper) {
        int small = 240;
        check(helper, PrintingPressMenu.INVENTORY_Y + 82 <= small, "the Printing Press is "
                + (PrintingPressMenu.INVENTORY_Y + 82) + " px tall");
        check(helper, MachineLayout.HEIGHT <= small, "a machine screen is " + MachineLayout.HEIGHT);
        int storage = StorageMenu.GRID_Y + StorageMenu.MAX_VISIBLE_ROWS * 18 + 26 + 84;
        check(helper, storage <= small, "a Storage Barrel's screen is " + storage);
        int sorter = SorterMenu.playerY(SorterTier.PRECISION) + 82;
        check(helper, sorter <= small, "the Precision Sorter's is " + sorter);
        check(helper, SpawnEggBrewerMenu.INVENTORY_Y + 82 <= small, "the Spawn Egg Brewer's is too tall");
        helper.succeed();
    }

    private ClickTests() {}
}
