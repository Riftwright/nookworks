package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import dev.nookworks.block.EnergyCableBlock;
import dev.nookworks.cooking.IronPotBlock;
import dev.nookworks.cooking.IronPotBlockEntity;
import dev.nookworks.cooking.PotRecipe;
import dev.nookworks.drive.BeltKind;
import dev.nookworks.drive.Belts;
import dev.nookworks.drive.DriveNetwork;
import dev.nookworks.drive.DriveWheelBlock;
import dev.nookworks.drive.DriveWheelBlockEntity;
import dev.nookworks.machine.CrusherBlockEntity;
import dev.nookworks.machine.GeneratorBlockEntity;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.machine.MachineIO;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.oil.OilPressBlockEntity;
import dev.nookworks.recycling.Compressing;
import dev.nookworks.recycling.CompressorBlockEntity;
import dev.nookworks.recycling.CorrugatedSheetBlock;
import dev.nookworks.recycling.RecyclerBlock;
import dev.nookworks.recycling.RecyclerBlockEntity;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import dev.nookworks.registry.ModRecycling;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * The deferred low items the user went through on 2026-10-22 (HANDOFF, "Open, low"), as they were fixed: a Recycler's
 * intake asked of claims as its body (1), receivers' handlers kept and found again when they change (16, 17), a Motor
 * counting only what it may turn (32), the Compressor's clients told what presses (9), the Iron Pot's word for a food it
 * does not take (14) and its recipe found again as its contents change (25), a belt keeping its named rope (26) and
 * giving way to a claim's owner (31), progress at 100 % at most and an idle Generator's empty slot (11, 24), a centred
 * flat sheet no way through (8).
 */
final class LowItemTests {
    private LowItemTests() {}

    static void register() {
        test("a_recycler_intake_is_asked_of_claims_as_its_body", LowItemTests::recyclerIntake);
        test("a_receiver_set_down_again_is_found_again", LowItemTests::receiverFoundAgain);
        test("a_motor_counts_only_the_machines_it_may_turn", LowItemTests::motorCounts);
        test("the_compressor_is_told_exactly_what_presses", LowItemTests::compressible);
        test("the_pot_refuses_a_food_it_does_not_take_with_a_word", LowItemTests::potRefusesFood);
        test("a_pots_recipe_is_found_again_as_its_contents_change", 100, LowItemTests::potFindsAgain);
        test("a_belt_gives_back_a_named_rope_as_it_was", LowItemTests::beltKeepsNamedRope);
        test("a_belt_gives_way_to_a_claims_owner_building_on_its_line", LowItemTests::beltGivesWay);
        test("a_belt_still_in_the_way_keeps_out_a_block_another_gave_way_to", LowItemTests::beltsCrossing);
        test("progress_reads_100_at_most_and_an_empty_slot_burns_nothing", LowItemTests::smallSums);
        test("a_centred_flat_sheet_is_no_way_through", LowItemTests::centredSheet);
    }

    /** A stand-in claim mod over the blocks {@code inside}: only {@code owner} may break them. Gone when the test ends. */
    private static void claim(GameTestHelper helper, Predicate<BlockPos> inside, UUID owner) {
        Consumer<BreakBlockEvent> stand = event -> {
            if (inside.test(event.getPos()) && (event.getPlayer() == null || !owner.equals(event.getPlayer().getUUID()))) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(BreakBlockEvent.class, stand);
        AfterTest.undo(helper, () -> NeoForge.EVENT_BUS.unregister(stand));
    }

    /** A line of Energy Cables along x from {@code start} ({@code count} of them), joined to what is beside them. */
    private static List<BlockPos> cables(ServerLevel level, BlockPos start, int count) {
        List<BlockPos> cables = new ArrayList<>();
        for (int x = 0; x < count; x++) {
            BlockPos at = start.offset(x, 0, 0);
            level.setBlockAndUpdate(at, ModBlocks.ENERGY_CABLE.get().defaultBlockState());
            cables.add(at);
        }
        return cables;
    }

    private static void join(ServerLevel level, List<BlockPos> at) {
        for (BlockPos pos : at) {
            level.setBlock(pos, Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos), Block.UPDATE_ALL);
        }
    }

    private static SimpleEnergyHandler source() {
        SimpleEnergyHandler source = new SimpleEnergyHandler(1_000_000, 1_000_000, 1_000_000);
        ReviewFourTests.insert(source, 1_000_000);
        return source;
    }

    private static RecyclerBlockEntity recycler(ServerLevel level, BlockPos body, UUID owner) {
        BlockState lower = ModRecycling.RECYCLER.get().defaultBlockState();
        level.setBlockAndUpdate(body, lower);
        level.setBlockAndUpdate(body.above(), lower.setValue(RecyclerBlock.HALF, DoubleBlockHalf.UPPER));
        RecyclerBlockEntity recycler = (RecyclerBlockEntity) level.getBlockEntity(body);
        recycler.setOwner(owner);
        return recycler;
    }

    /**
     * R4: a stranger's Recycler in open land whose intake alone touches a claimed cable gets none of the claim's power,
     * as its body touching it gets none; the claim's owner's, set the same way, is powered. (The intake has no block
     * entity: it was asked as nobody's, and let through.)
     */
    private static void recyclerIntake(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 2, 3));
        level.setBlockAndUpdate(origin, Blocks.STONE.defaultBlockState());
        UUID claimant = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        // each intake (y 2) beside a cable, its body (y 1) beside none
        RecyclerBlockEntity strangers = recycler(level, helper.absolutePos(new BlockPos(2, 1, 4)), stranger);
        RecyclerBlockEntity own = recycler(level, helper.absolutePos(new BlockPos(4, 1, 4)), claimant);
        List<BlockPos> cables = cables(level, origin.east(), 5);
        join(level, cables);
        check(helper, MachineIO.receivers(level, origin).size() == 2, "the cable reaches both intakes: " + MachineIO.receivers(level, origin));
        claim(helper, at -> at.equals(origin) || at.equals(cables.get(0)), claimant);
        int sent = MachineIO.pushEnergy(level, origin, source(), 2_000);
        check(helper, sent > 0 && own.energyStored() > 0, "the claim's owner's recycler is powered through its intake: " + sent);
        check(helper, strangers.energyStored() == 0, "a stranger's, its intake on the cable outside, gets nothing: " + strangers.energyStored());
        helper.succeed();
    }

    /**
     * A receiver's handler is kept as found (16, 17): a machine broken and another set down in its place, in the same tick,
     * is the one powered next - the old one's handler is not.
     */
    private static void receiverFoundAgain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 3));
        level.setBlockAndUpdate(origin, Blocks.STONE.defaultBlockState());
        BlockPos crusherAt = helper.absolutePos(new BlockPos(2, 2, 3));
        level.setBlockAndUpdate(crusherAt, ModBlocks.CRUSHER.get().defaultBlockState());
        List<BlockPos> cables = cables(level, origin.east(), 3);
        join(level, cables);
        SimpleEnergyHandler source = source();
        var first = (CrusherBlockEntity) level.getBlockEntity(crusherAt);
        check(helper, MachineIO.pushEnergy(level, origin, source, 500) > 0 && first.energyStored() > 0, "the first crusher is powered");
        level.setBlockAndUpdate(crusherAt, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(crusherAt, ModBlocks.CRUSHER.get().defaultBlockState());
        var second = (CrusherBlockEntity) level.getBlockEntity(crusherAt);
        check(helper, second != first && second.energyStored() == 0, "a new crusher stands there, empty");
        int sent = MachineIO.pushEnergy(level, origin, source, 500);
        check(helper, sent > 0 && second.energyStored() > 0, "the new one is powered, not the one gone: " + sent + ", " + second.energyStored());
        helper.succeed();
    }

    private static DriveWheelBlockEntity wheel(GameTestHelper helper, BlockPos rel, Direction facing) {
        helper.setBlock(rel, ModOil.DRIVE_WHEEL.get().defaultBlockState().setValue(DriveWheelBlock.FACING, facing));
        return helper.getBlockEntity(rel, DriveWheelBlockEntity.class);
    }

    /**
     * 32: a Motor's eight are counted only of what it may turn: eight strangers' presses beyond its wheels, which claims
     * keep it from, took up the eight and left the owner's ninth still.
     */
    private static void motorCounts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        List<DriveWheelBlockEntity> wheels = new ArrayList<>();
        OilPressBlockEntity own = null;
        for (int i = 0; i < 9; i++) {
            int x = i < 7 ? i : i - 7;
            int z = i < 7 ? 1 : 4;
            wheels.add(wheel(helper, new BlockPos(x, 2, z), Direction.SOUTH));
            helper.setBlock(new BlockPos(x, 2, z + 1), ModOil.OIL_PRESS.get());
            OilPressBlockEntity press = helper.getBlockEntity(new BlockPos(x, 2, z + 1), OilPressBlockEntity.class);
            press.setOwner(i == 8 ? owner : stranger);
            if (i == 8) {
                own = press;
            }
        }
        List<BlockEntity> any = DriveNetwork.machines(level, wheels);
        check(helper, any.size() == DriveNetwork.MOST_MACHINES && !any.contains(own), "counted as they come, the eight are strangers': " + any.size());
        List<BlockEntity> turnable = DriveNetwork.machines(level, wheels,
                machine -> machine instanceof MachineBlockEntity m && owner.equals(m.owner()));
        check(helper, turnable.equals(List.of(own)), "counted only as it may turn them, the owner's ninth is one: " + turnable);
        helper.succeed();
    }

    /**
     * 9: what the server says presses is what its Compressor presses - nuggets, ingots, scrap - and not what has no 9-to-1
     * recipe; a client told it puts in its slot only that, and a named stack not at all; not told, it guesses by tags.
     */
    private static void compressible(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Set<Item> presses = Compressing.compressible(level);
        for (Item item : List.of(Items.IRON_NUGGET, Items.IRON_INGOT, Items.REDSTONE, Items.WHEAT, ModRecycling.SCRAP.get())) {
            check(helper, presses.contains(item), item + " presses");
        }
        for (Item item : List.of(Items.STICK, Items.DIRT, Items.DIAMOND_SWORD)) {
            check(helper, !presses.contains(item), item + " does not");
        }
        for (Item item : presses) {
            check(helper, !CompressorBlockEntity.resultOf(level, new ItemStack(item)).isEmpty(), item + " is said to press, and does not");
        }
        ItemStack named = new ItemStack(Items.IRON_NUGGET);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Lucky"));
        Compressing.heard(presses);
        try {
            check(helper, Compressing.pressesHere(new ItemStack(Items.IRON_NUGGET)) && !Compressing.pressesHere(new ItemStack(Items.DIRT))
                    && !Compressing.pressesHere(named), "a client told goes by the server's word");
        } finally {
            Compressing.leftServer();
        }
        check(helper, Compressing.pressesHere(new ItemStack(Items.IRON_NUGGET)) && !Compressing.pressesHere(new ItemStack(Items.DIRT)),
                "not told, it guesses by tags");
        helper.succeed();
    }

    /** 14: a Boiled Egg used on a pot of oil is refused, kept in hand and not eaten, and the pot takes nothing. */
    private static void potRefusesFood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.getFoodData().setFoodLevel(6);
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModCooking.IRON_POT.get());
        IronPotBlockEntity pot = helper.getBlockEntity(pos, IronPotBlockEntity.class);
        check(helper, pot.addLiquid(PotRecipe.Liquid.OIL, 1_000), "oil goes in");
        ItemStack egg = new ItemStack(ModCooking.BOILED_EGG.get(), 2);
        check(helper, !PotRecipe.isIngredient(level, egg), "a boiled egg is no pot ingredient");
        player.setItemInHand(InteractionHand.MAIN_HAND, egg);
        BlockPos at = helper.absolutePos(pos);
        InteractionResult result = player.gameMode.useItemOn(player, level, egg, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.UP, 0.4), Direction.UP, at, false));
        check(helper, result.consumesAction(), "the click is the pot's, not passed on to eating: " + result);
        check(helper, player.getMainHandItem().getCount() == 2 && !player.isUsingItem() && pot.count() == 0,
                "the eggs stay in hand, uneaten, and the pot holds nothing");
        helper.succeed();
    }

    /**
     * 25: a heated pot's recipe is kept as found while nothing it rests on changes, and found again as soon as it does:
     * sugar alone in oil cooks nothing, and three eggs added make it tamagoyaki.
     */
    private static void potFindsAgain(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos.below(), Blocks.MAGMA_BLOCK);
        helper.setBlock(pos, ModCooking.IRON_POT.get());
        IronPotBlockEntity pot = helper.getBlockEntity(pos, IronPotBlockEntity.class);
        check(helper, pot.addLiquid(PotRecipe.Liquid.OIL, 1_000) && pot.addIngredient(new ItemStack(Items.SUGAR)), "oil and sugar go in");
        helper.runAfterDelay(5, () -> {
            check(helper, !helper.getBlockState(pos).getValue(IronPotBlock.COOKING), "sugar alone in oil cooks nothing");
            for (int i = 0; i < 3; i++) {
                check(helper, pot.addIngredient(new ItemStack(Items.EGG)), "an egg goes in");
            }
            helper.succeedWhen(() -> check(helper, helper.getBlockState(pos).getValue(IronPotBlock.COOKING),
                    "with three eggs, the recipe is found again and it cooks"));
        });
    }

    private static int ropeFor(BlockPos a, BlockPos b) {
        return BeltKind.itemsFor(Vec3.atCenterOf(a).distanceTo(Vec3.atCenterOf(b)));
    }

    /**
     * 26: a belt tied with Rope named at an anvil, and a plain one from the bag besides, gives back the named ones named
     * and the plain one plain; both are saved with it, and who tied it.
     */
    private static void beltKeepsNamedRope(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        DriveWheelBlockEntity a = wheel(helper, new BlockPos(1, 2, 1), Direction.EAST);
        DriveWheelBlockEntity b = wheel(helper, new BlockPos(1, 2, 5), Direction.EAST);
        int count = ropeFor(a.getBlockPos(), b.getBlockPos());
        ItemStack named = new ItemStack(ModItems.ROPE.get(), count - 1);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Tow line"));
        player.setItemInHand(InteractionHand.MAIN_HAND, named);
        player.getInventory().add(new ItemStack(ModItems.ROPE.get(), 5));
        Component refused = Belts.tie(level, a, b, BeltKind.ROPE, player, player.getMainHandItem());
        check(helper, refused == null, "the belt is tied: " + refused);
        DriveWheelBlockEntity.Belt belt = a.belts().getFirst();
        check(helper, player.getInventory().countItem(ModItems.ROPE.get()) == 4, "it took the named ones and one plain one");
        List<ItemStack> back = belt.givenBack();
        check(helper, back.size() == 2 && back.get(0).getCount() == count - 1 && "Tow line".equals(back.get(0).getHoverName().getString())
                && back.get(1).getCount() == 1 && !back.get(1).has(DataComponents.CUSTOM_NAME), "given back as it went in: " + back);
        var ops = RegistryOps.create(NbtOps.INSTANCE, level.registryAccess());
        DriveWheelBlockEntity.Belt read = DriveWheelBlockEntity.Belt.CODEC.parse(ops,
                DriveWheelBlockEntity.Belt.CODEC.encodeStart(ops, belt).getOrThrow()).getOrThrow();
        check(helper, player.getUUID().equals(read.tiedBy()) && read.madeOf().size() == 1
                && ItemStack.matches(read.madeOf().getFirst(), belt.madeOf().getFirst()), "saved and read back the same: " + read);
        helper.succeed();
    }

    /** A block set down at {@code pos} by {@code by}, as the game posts it once the block is there; the event, as answered. */
    private static BlockEvent.EntityPlaceEvent place(ServerLevel level, BlockPos pos, Entity by) {
        BlockSnapshot before = BlockSnapshot.create(level.dimension(), level, pos);
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        return NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(before, Blocks.AIR.defaultBlockState(), by));
    }

    /**
     * 31: a stranger's belt, tied in open land before a claim was made over its line, gives way to the claim's owner
     * building there - the block stays, and the belt snaps a tick later, its rope dropped - where in open land it still
     * keeps the block out.
     */
    private static void beltGivesWay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer stranger = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        ServerPlayer claimant = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        DriveWheelBlockEntity claimedA = wheel(helper, new BlockPos(1, 2, 1), Direction.EAST);
        DriveWheelBlockEntity claimedB = wheel(helper, new BlockPos(1, 2, 5), Direction.EAST);
        DriveWheelBlockEntity openA = wheel(helper, new BlockPos(5, 2, 1), Direction.EAST);
        DriveWheelBlockEntity openB = wheel(helper, new BlockPos(5, 2, 5), Direction.EAST);
        stranger.getInventory().add(new ItemStack(ModItems.ROPE.get(), 64));
        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROPE.get(), 16));
        check(helper, Belts.tie(level, claimedA, claimedB, BeltKind.ROPE, stranger, stranger.getMainHandItem()) == null
                && Belts.tie(level, openA, openB, BeltKind.ROPE, stranger, stranger.getMainHandItem()) == null, "the stranger ties both belts");
        BlockPos claimed = helper.absolutePos(new BlockPos(1, 2, 3));
        BlockPos open = helper.absolutePos(new BlockPos(5, 2, 3));
        claim(helper, at -> at.equals(claimed), claimant.getUUID()); // made since, over the first belt's line
        var inClaim = place(level, claimed, claimant);
        check(helper, !inClaim.isCanceled() && claimedA.belts().size() == 1, "the claim's owner builds on the line in their claim");
        var inOpen = place(level, open, claimant);
        check(helper, inOpen.isCanceled() && openA.belts().size() == 1, "in open land the belt still keeps the block out");
        level.setBlock(open, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); // as the game takes a refused block back up
        helper.runAfterDelay(3, () -> {
            check(helper, claimedA.belts().isEmpty() && claimedB.belts().isEmpty(), "the stranger's belt in the claim has snapped");
            List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(claimed).inflate(2),
                    item -> item.getItem().is(ModItems.ROPE.get()));
            check(helper, dropped.stream().mapToInt(item -> item.getItem().getCount()).sum() == ropeFor(claimedA.getBlockPos(), claimedB.getBlockPos()),
                    "its rope dropped, all of it");
            check(helper, openA.belts().size() == 1, "the one in open land stays");
            helper.succeed();
        });
    }

    /**
     * 31, the review's case: one block in two belts' way, a stranger's that has lapsed and the claim's owner's own, which
     * has not - the block is kept out, and neither belt snaps (the lapsed one was snapped before it was known whether
     * the block would stay).
     */
    private static void beltsCrossing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer stranger = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        ServerPlayer claimant = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        for (ServerPlayer player : List.of(stranger, claimant)) {
            player.getInventory().add(new ItemStack(ModItems.ROPE.get(), 64));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROPE.get(), 16));
        }
        // the stranger's along z through (1, 2, 3); the claim's owner's along x through the same block
        DriveWheelBlockEntity theirsA = wheel(helper, new BlockPos(1, 2, 1), Direction.EAST);
        DriveWheelBlockEntity theirsB = wheel(helper, new BlockPos(1, 2, 5), Direction.EAST);
        DriveWheelBlockEntity ownA = wheel(helper, new BlockPos(0, 2, 3), Direction.SOUTH);
        DriveWheelBlockEntity ownB = wheel(helper, new BlockPos(3, 2, 3), Direction.SOUTH);
        check(helper, Belts.tie(level, theirsA, theirsB, BeltKind.ROPE, stranger, stranger.getMainHandItem()) == null
                && Belts.tie(level, ownA, ownB, BeltKind.ROPE, claimant, claimant.getMainHandItem()) == null, "both belts are tied");
        BlockPos cell = helper.absolutePos(new BlockPos(1, 2, 3));
        claim(helper, at -> at.equals(cell), claimant.getUUID());
        var event = place(level, cell, claimant);
        check(helper, event.isCanceled(), "the owner's own belt keeps the block out");
        level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); // as the game takes a refused block back up
        helper.runAfterDelay(3, () -> {
            check(helper, theirsA.belts().size() == 1 && ownA.belts().size() == 1, "and neither belt has snapped");
            helper.succeed();
        });
    }

    /** 11, 24: a percentage of progress past its time reads 100; a Generator's empty slot burns nothing, coal its 1,600. */
    private static void smallSums(GameTestHelper helper) {
        check(helper, MachineTuning.percent(120, 100) == 100 && MachineTuning.percent(50, 100) == 50
                && MachineTuning.percent(-3, 100) == 0 && MachineTuning.percent(5, 0) == 0, "percent keeps to 0-100");
        helper.setBlock(new BlockPos(1, 1, 1), ModBlocks.GENERATOR.get());
        GeneratorBlockEntity generator = helper.getBlockEntity(new BlockPos(1, 1, 1), GeneratorBlockEntity.class);
        check(helper, generator.burnTime(helper.getLevel(), ItemStack.EMPTY) == 0
                && generator.burnTime(helper.getLevel(), new ItemStack(Items.COAL)) == 1_600, "nothing burns nothing; coal burns 1,600 ticks");
        helper.succeed();
    }

    /** 8: lying on a floor or under a ceiling a sheet is walked over; standing, or lying in the middle of its block, it is not. */
    private static void centredSheet(GameTestHelper helper) {
        BlockState lying = ModRecycling.CORRUGATED_SHEETS.get("corrugated_sheet").get().defaultBlockState().setValue(CorrugatedSheetBlock.FACING, Direction.UP);
        check(helper, lying.isPathfindable(PathComputationType.LAND), "a sheet lying on a floor is walked over");
        check(helper, !lying.setValue(CorrugatedSheetBlock.CENTERED, true).isPathfindable(PathComputationType.LAND),
                "lying in the middle of its block it is a slab's floor, no way through");
        check(helper, !lying.setValue(CorrugatedSheetBlock.FACING, Direction.NORTH).isPathfindable(PathComputationType.LAND),
                "standing it is a wall");
        helper.succeed();
    }
}
