package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.nookworks.feature.JoinOverrides;
import dev.nookworks.feature.WrenchRecovery;
import dev.nookworks.item.WrenchItem;
import dev.nookworks.item.WrenchMode;
import dev.nookworks.machine.DieselGeneratorBlockEntity;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import dev.nookworks.port.test.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;

/**
 * The Wrench's two modes (the user, 2026-11-07): a sneak-use at nothing switches Adjust and Recover; Recover takes a
 * machine in #nookworks:wrench_pickup into the inventory - its item with its liquid and power, its slots' contents, the
 * rest at the player's feet - and only where breaking it by hand would be allowed; Adjust's sneak-use on a fence, wall
 * or pane parts or joins the side aimed at (that block's only), a fence gate is raised or lowered, and the setting stays through neighbours changing,
 * goes when the block does, and follows a piston.
 */
final class WrenchModeTests {
    private WrenchModeTests() {}

    static void register() {
        test("wrench_switches_mode_sneaking_at_nothing", WrenchModeTests::switchMode);
        test("wrench_recovers_a_machine_with_its_items_liquid_and_power", WrenchModeTests::recover);
        test("wrench_recovers_in_creative_too", WrenchModeTests::creative);
        test("wrench_recover_leaves_blocks_not_in_its_tag", WrenchModeTests::notTagged);
        test("wrench_recover_keeps_out_of_a_claim_and_adventure_mode", WrenchModeTests::claimed);
        test("wrench_parts_fences_and_they_stay_parted", WrenchModeTests::fences);
        test("wrench_parts_walls_and_they_stay_parted", WrenchModeTests::walls);
        test("wrench_parts_panes_and_they_stay_parted", WrenchModeTests::panes);
        test("wrench_parting_is_forgotten_with_the_fence", WrenchModeTests::forgotten);
        test("wrench_parting_follows_a_piston", WrenchModeTests::piston);
        test("wrench_raises_and_lowers_fence_gates", WrenchModeTests::gates);
        test("wrench_in_the_other_hand_keeps_its_mode", WrenchModeTests::otherHand);
        test("asking_to_place_keeps_a_fences_sides_set_by_hand", WrenchModeTests::askingKeepsSides);
    }

    // ---- helpers ----

    /** A player in {@code mode}, flying clear above the test (one held up there in survival fell to the void). */
    private static ServerPlayer player(GameTestHelper helper, GameType mode, boolean sneaking) {
        ServerPlayer player = MockPlayers.inLevel(helper, mode);
        Vec3 aside = helper.absoluteVec(new Vec3(0.5, 8, 0.5));
        player.moveTo(aside.x, aside.y, aside.z);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.setShiftKeyDown(sneaking);
        return player;
    }

    private static ItemStack wrench(WrenchMode mode) {
        ItemStack wrench = new ItemStack(ModItems.WRENCH.get());
        WrenchItem.setMode(wrench, mode);
        return wrench;
    }

    /** {@code stack} used by {@code player} at {@code point} (relative to the test) on {@code face} of {@code block}. */
    private static void use(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos block, Vec3 point, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO)).add(point);
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, helper.absolutePos(block), false));
    }

    /** A click on the top of {@code block}'s middle. */
    private static void useTop(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos block) {
        use(helper, player, stack, block, Vec3.atCenterOf(block).add(0, 0.5, 0), Direction.UP);
    }

    /** Sets {@code state} at {@code pos} as placing it would shape it among its neighbours. */
    private static void place(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.setBlock(pos, Block.updateFromNeighbourShapes(state, helper.getLevel(), helper.absolutePos(pos)));
    }

    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack stack : stacks(player)) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** Every slot of the player's inventory, as 26.3's Inventory iterates (1.21.1's is no Iterable). */
    private static List<ItemStack> stacks(ServerPlayer player) {
        var inventory = player.getInventory();
        return java.util.stream.IntStream.range(0, inventory.getContainerSize()).mapToObj(inventory::getItem).toList();
    }

    private static @Nullable ItemStack find(ServerPlayer player, net.minecraft.world.item.Item item) {
        for (ItemStack stack : stacks(player)) {
            if (stack.is(item)) {
                return stack;
            }
        }
        return null;
    }

    private static boolean joined(GameTestHelper helper, BlockPos pos, Direction side) {
        return JoinOverrides.joined(helper.getBlockState(pos), side);
    }

    // ---- the modes ----

    /** Sneak-used with no block in reach it switches, and back; sneak-used at a block it does nothing with, not. */
    private static void switchMode(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.SURVIVAL, true);
        ItemStack wrench = wrench(WrenchMode.ADJUST);
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        // nothing in reach: the arena is roofed with barriers, so the reach is made short rather than looking up
        var reach = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE);
        double was = reach.getBaseValue();
        reach.setBaseValue(0.5);
        Vec3 middle = helper.absoluteVec(new Vec3(2.5, 2, 2.5)); // in the open, clear of the roof the player is kept under
        player.moveTo(middle.x, middle.y, middle.z);
        player.setXRot(-90.0F);
        player.gameMode.useItem(player, helper.getLevel(), wrench, InteractionHand.MAIN_HAND);
        check(helper, WrenchItem.mode(player.getMainHandItem()) == WrenchMode.RECOVER, "sneak-used at nothing, Adjust becomes Recover");
        check(helper, player.getMainHandItem().has(ModDataComponents.WRENCH_MODE.get()), "kept on the item");
        player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
        check(helper, WrenchItem.mode(player.getMainHandItem()) == WrenchMode.ADJUST
                && !player.getMainHandItem().has(ModDataComponents.WRENCH_MODE.get()), "and back to Adjust, as a new Wrench is");
        player.setShiftKeyDown(false);
        player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
        check(helper, WrenchItem.mode(player.getMainHandItem()) == WrenchMode.ADJUST, "not sneaking, it does not switch");
        // a block in reach under the player: the sneak-use is the block's, not a switch
        reach.setBaseValue(was);
        helper.getLevel().setBlockAndUpdate(player.blockPosition().below(), Blocks.STONE.defaultBlockState());
        player.setShiftKeyDown(true);
        player.setXRot(90.0F);
        player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
        check(helper, WrenchItem.mode(player.getMainHandItem()) == WrenchMode.ADJUST, "aimed at a block, it does not switch");
        helper.succeed();
    }

    /**
     * A Diesel Generator holding biodiesel, power and a stack in its slot, taken up in survival by a player whose
     * inventory has one slot free: the generator goes in with its fuel and power, the stack lands at the player's feet.
     */
    private static void recover(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at, ModBlocks.DIESEL_GENERATOR.get().defaultBlockState());
        DieselGeneratorBlockEntity machine = (DieselGeneratorBlockEntity) helper.getBlockEntity(at, DieselGeneratorBlockEntity.class);
        check(helper, machine.pour(2000), "biodiesel poured in");
        machine.energy().set(4000);
        machine.setItem(0, new ItemStack(Items.BUCKET, 3));
        ServerPlayer player = player(helper, GameType.SURVIVAL, false);
        var inventory = player.getInventory();
        for (int slot = 1; slot < 35; slot++) {
            inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64)); // slot 0 the wrench's, 35 the one free
        }
        ItemStack wrench = wrench(WrenchMode.RECOVER);
        player.getInventory().selected = 0;
        useTop(helper, player, wrench, at);
        helper.assertBlockNotPresent(ModBlocks.DIESEL_GENERATOR.get(), at);
        check(helper, player.getMainHandItem().getDamageValue() == 1, "the wrench takes one durability: " + player.getMainHandItem().getDamageValue());
        List<ItemEntity> around = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(2.0));
        ItemStack carried = find(player, ModItems.DIESEL_GENERATOR.get());
        check(helper, carried != null && inventory.getItem(35) == carried, "the generator went into the free slot");
        var fluids = carried.get(ModDataComponents.HELD_FLUIDS.get());
        check(helper, fluids != null && fluids.size() == 1 && fluids.get(0).getAmount() == 2000, "with its biodiesel: " + fluids);
        check(helper, Integer.valueOf(4000).equals(carried.get(ModDataComponents.HELD_ENERGY.get())), "and its power: "
                + carried.get(ModDataComponents.HELD_ENERGY.get()));
        check(helper, around.stream().anyMatch(e -> e.getItem().is(Items.BUCKET) && e.getItem().getCount() == 3),
                "the buckets in its slot, with no room left, lie at the player's feet");
        check(helper, helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(at)).inflate(2.0)).isEmpty(),
                "nothing dropped where the machine stood");
        // and with room: a second one, its contents straight into the inventory
        for (int slot = 20; slot < 36; slot++) {
            inventory.setItem(slot, ItemStack.EMPTY);
        }
        around.forEach(e -> e.discard());
        helper.setBlock(at, ModBlocks.DIESEL_GENERATOR.get().defaultBlockState());
        ((DieselGeneratorBlockEntity) helper.getBlockEntity(at, DieselGeneratorBlockEntity.class)).setItem(0, new ItemStack(Items.BUCKET, 5));
        int generators = count(player, ModItems.DIESEL_GENERATOR.get());
        useTop(helper, player, player.getMainHandItem(), at);
        check(helper, count(player, ModItems.DIESEL_GENERATOR.get()) == generators + 1 && count(player, Items.BUCKET) == 5,
                "with room, the machine and what it held go into the inventory");
        helper.succeed();
    }

    /** In creative, the block's item is handed over all the same (breaking it would drop nothing). */
    private static void creative(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at, ModBlocks.COPPER_TANK.get().defaultBlockState());
        ServerPlayer player = player(helper, GameType.CREATIVE, true); // sneaking too: the same in Recover mode
        useTop(helper, player, wrench(WrenchMode.RECOVER), at);
        helper.assertBlockNotPresent(ModBlocks.COPPER_TANK.get(), at);
        check(helper, count(player, ModItems.COPPER_TANK.get()) == 1, "a creative player gets the tank");
        helper.succeed();
    }

    /** A Crate (carrying is its own job), a Storage Barrel and stone are no machines: left where they are. */
    private static void notTagged(GameTestHelper helper) {
        // sneaking (the same in Recover mode), so that a block the Wrench leaves to its own use opens no screen: a mock
        // player's connection cannot be sent one
        ServerPlayer player = player(helper, GameType.SURVIVAL, true);
        List<Block> blocks = List.of(ModBlocks.CRATE.get(), ModBlocks.IRON_STORAGE.get(), Blocks.STONE);
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos at = new BlockPos(1 + i, 1, 2);
            helper.setBlock(at, blocks.get(i));
            check(helper, !helper.getBlockState(at).is(WrenchRecovery.PICKUP), "not in the tag: " + blocks.get(i));
            useTop(helper, player, wrench(WrenchMode.RECOVER), at);
            helper.assertBlockPresent(blocks.get(i), at);
            check(helper, count(player, blocks.get(i).asItem()) == 0, "and nothing handed over: " + blocks.get(i));
        }
        helper.succeed();
    }

    /**
     * A stand-in claim mod over one cell refuses the break event there to all but its owner: a Sifter inside it stays,
     * one beside it (outside) comes up, so the claim really was what refused. An adventure player takes none.
     */
    private static void claimed(GameTestHelper helper) {
        BlockPos inside = new BlockPos(1, 1, 2);
        BlockPos outside = new BlockPos(3, 1, 2);
        helper.setBlock(inside, ModBlocks.SIFTER.get());
        helper.setBlock(outside, ModBlocks.SIFTER.get());
        BlockPos claimed = helper.absolutePos(inside);
        UUID owner = UUID.randomUUID();
        List<BlockPos> asked = new ArrayList<>();
        Consumer<BreakEvent> claim = event -> {
            asked.add(event.getPos().immutable());
            if (event.getPos().equals(claimed) && (event.getPlayer() == null || !owner.equals(event.getPlayer().getUUID()))) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(BreakEvent.class, claim);
        AfterTest.undo(helper, () -> NeoForge.EVENT_BUS.unregister(claim));
        ServerPlayer player = player(helper, GameType.SURVIVAL, false);
        useTop(helper, player, wrench(WrenchMode.RECOVER), inside);
        check(helper, asked.contains(claimed), "the claim was asked about the cell it holds");
        helper.assertBlockPresent(ModBlocks.SIFTER.get(), inside);
        check(helper, count(player, ModItems.SIFTER.get()) == 0, "refused by the claim, nothing taken");
        useTop(helper, player, player.getMainHandItem(), outside);
        helper.assertBlockNotPresent(ModBlocks.SIFTER.get(), outside);
        check(helper, count(player, ModItems.SIFTER.get()) == 1, "outside the claim, taken");
        NeoForge.EVENT_BUS.unregister(claim);
        ServerPlayer adventurer = player(helper, GameType.ADVENTURE, true); // sneaking: no screen for the mock player
        BlockPos other = new BlockPos(2, 1, 3);
        helper.setBlock(other, ModBlocks.SIFTER.get());
        useTop(helper, adventurer, wrench(WrenchMode.RECOVER), other);
        helper.assertBlockPresent(ModBlocks.SIFTER.get(), other);
        check(helper, WrenchRecovery.recover(helper.getLevel(), helper.absolutePos(other), adventurer, InteractionHand.MAIN_HAND)
                == net.minecraft.world.InteractionResult.FAIL, "asked straight, it refuses in adventure mode");
        helper.assertBlockPresent(ModBlocks.SIFTER.get(), other);
        check(helper, count(adventurer, ModItems.SIFTER.get()) == 0, "in adventure mode, nothing taken");
        helper.succeed();
    }

    // ---- fences, walls, panes ----

    /** The click that parts or joins {@code a}'s east side: on the top of its east arm. */
    private static void eastArm(GameTestHelper helper, ServerPlayer player, ItemStack wrench, BlockPos a) {
        use(helper, player, wrench, a, new Vec3(a.getX() + 0.88, a.getY() + 0.9, a.getZ() + 0.5), Direction.UP);
    }

    /**
     * Two oak fences side by side, the west one's east arm parted by the Wrench: that side only - the east one keeps
     * reaching to it (the user, 2026-11-07: a wall and a fence side by side must be set each on its own). A stone set
     * beside the east one and taken away again changes neither; the same click joins it again, nothing kept. The east
     * one parted on its own side then leaves the west one joined.
     */
    private static void fences(GameTestHelper helper) {
        sideKept(helper, Blocks.OAK_FENCE.defaultBlockState(), "fences");
    }

    private static void panes(GameTestHelper helper) {
        sideKept(helper, Blocks.GLASS_PANE.defaultBlockState(), "panes");
    }

    private static void sideKept(GameTestHelper helper, BlockState kind, String what) {
        BlockPos a = new BlockPos(1, 1, 2);
        BlockPos b = new BlockPos(2, 1, 2);
        place(helper, a, kind);
        place(helper, b, kind);
        check(helper, joined(helper, a, Direction.EAST) && joined(helper, b, Direction.WEST), what + " side by side join");
        ServerPlayer player = player(helper, GameType.SURVIVAL, true);
        ItemStack wrench = wrench(WrenchMode.ADJUST);
        eastArm(helper, player, wrench, a);
        check(helper, !joined(helper, a, Direction.EAST), "the Wrench parts the " + what + "' side clicked");
        check(helper, joined(helper, b, Direction.WEST), "and the one beside it keeps its own");
        check(helper, player.getMainHandItem().getDamageValue() == 1, "for one durability");
        helper.setBlock(b.north(), Blocks.STONE);
        check(helper, joined(helper, b, Direction.NORTH), "the east one joins the stone set beside it");
        check(helper, !joined(helper, a, Direction.EAST) && joined(helper, b, Direction.WEST), "and nothing else changes");
        helper.setBlock(b.north(), Blocks.AIR);
        check(helper, !joined(helper, a, Direction.EAST) && joined(helper, b, Direction.WEST), "the stone gone again, the same");
        eastArm(helper, player, player.getMainHandItem(), a);
        check(helper, joined(helper, a, Direction.EAST), "the same click joins it again");
        check(helper, JoinOverrides.forced(helper.getLevel(), helper.absolutePos(a), Direction.EAST) == null,
                "joined as vanilla has it, nothing is kept");
        use(helper, player, player.getMainHandItem(), b, new Vec3(b.getX() + 0.12, b.getY() + 0.9, b.getZ() + 0.5), Direction.UP);
        check(helper, !joined(helper, b, Direction.WEST) && joined(helper, a, Direction.EAST), "the east one parted on its own side");
        helper.succeed();
    }

    /**
     * Three cobblestone walls in a row, the middle one's west side parted: that side none, the west wall still reaching
     * to it, and the middle one, now an end, raises its post. A block set above it makes its other side tall but leaves
     * the parted one apart; joined again under that block, the side is tall, as vanilla would make it.
     */
    private static void walls(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 2);
        BlockPos b = new BlockPos(2, 1, 2);
        BlockPos c = new BlockPos(3, 1, 2);
        BlockState wall = Blocks.COBBLESTONE_WALL.defaultBlockState();
        place(helper, a, wall);
        place(helper, b, wall);
        place(helper, c, wall);
        check(helper, !helper.getBlockState(b).getValue(WallBlock.UP), "a straight wall has no post in the middle");
        ServerPlayer player = player(helper, GameType.SURVIVAL, true);
        ItemStack wrench = wrench(WrenchMode.ADJUST);
        use(helper, player, wrench, b, new Vec3(b.getX() + 0.12, b.getY() + 0.9, b.getZ() + 0.5), Direction.UP);
        check(helper, helper.getBlockState(b).getValue(WallBlock.WEST_WALL) == WallSide.NONE, "parted, the side is none");
        check(helper, helper.getBlockState(a).getValue(WallBlock.EAST_WALL) == WallSide.LOW, "the west wall keeps its own side");
        check(helper, helper.getBlockState(b).getValue(WallBlock.UP), "the middle one, now an end, raises its post");
        helper.setBlock(b.above(), Blocks.STONE);
        check(helper, helper.getBlockState(b).getValue(WallBlock.EAST_WALL) == WallSide.TALL, "a block above makes its joined side tall");
        check(helper, helper.getBlockState(b).getValue(WallBlock.WEST_WALL) == WallSide.NONE, "but the parted side stays apart");
        use(helper, player, player.getMainHandItem(), b, new Vec3(b.getX() + 0.12, b.getY() + 0.9, b.getZ() + 0.5), Direction.NORTH);
        check(helper, helper.getBlockState(b).getValue(WallBlock.WEST_WALL) == WallSide.TALL
                && helper.getBlockState(a).getValue(WallBlock.EAST_WALL) == WallSide.LOW, "joined again: tall under the block, low where nothing is above");
        helper.setBlock(b.above(), Blocks.AIR);
        check(helper, helper.getBlockState(b).getValue(WallBlock.WEST_WALL) == WallSide.LOW, "the block gone, low again");
        helper.succeed();
    }

    /**
     * A fence gate between two walls sits lowered (vanilla's in_wall); the Wrench raises it to a fence's height, and it
     * stays so when a wall beside it goes and comes back; lowered again, nothing is kept. Between fences it sits high,
     * and the Wrench lowers it, kept likewise.
     */
    private static void gates(GameTestHelper helper) {
        BlockPos gate = new BlockPos(2, 1, 2);
        BlockState wall = Blocks.COBBLESTONE_WALL.defaultBlockState();
        place(helper, gate.east(), wall);
        place(helper, gate.west(), wall);
        place(helper, gate, Blocks.OAK_FENCE_GATE.defaultBlockState());
        check(helper, helper.getBlockState(gate).getValue(FenceGateBlock.IN_WALL), "between walls it sits lowered");
        ServerPlayer player = player(helper, GameType.SURVIVAL, true);
        Vec3 top = new Vec3(gate.getX() + 0.5, gate.getY() + 0.9, gate.getZ() + 0.5);
        use(helper, player, wrench(WrenchMode.ADJUST), gate, top, Direction.UP);
        check(helper, !helper.getBlockState(gate).getValue(FenceGateBlock.IN_WALL), "the Wrench raises it");
        helper.setBlock(gate.east(), Blocks.AIR);
        place(helper, gate.east(), wall);
        check(helper, !helper.getBlockState(gate).getValue(FenceGateBlock.IN_WALL), "and it stays raised as the walls change");
        use(helper, player, player.getMainHandItem(), gate, top, Direction.UP);
        check(helper, helper.getBlockState(gate).getValue(FenceGateBlock.IN_WALL), "lowered again");
        helper.setBlock(gate.east(), Blocks.AIR);
        helper.setBlock(gate.west(), Blocks.AIR);
        check(helper, helper.getBlockState(gate).getValue(FenceGateBlock.IN_WALL), "set by hand, it stays lowered with the walls gone");
        BlockPos other = new BlockPos(2, 1, 4);
        place(helper, other.east(), Blocks.OAK_FENCE.defaultBlockState());
        place(helper, other.west(), Blocks.OAK_FENCE.defaultBlockState());
        place(helper, other, Blocks.OAK_FENCE_GATE.defaultBlockState());
        check(helper, !helper.getBlockState(other).getValue(FenceGateBlock.IN_WALL), "between fences it sits high");
        use(helper, player, player.getMainHandItem(), other, new Vec3(other.getX() + 0.5, other.getY() + 0.9, other.getZ() + 0.5), Direction.UP);
        helper.setBlock(other.west(), Blocks.AIR);
        check(helper, helper.getBlockState(other).getValue(FenceGateBlock.IN_WALL), "lowered by the Wrench, and kept as a fence goes");
        helper.succeed();
    }

    /** A sneaking click at the air with something else in hand leaves a Wrench in the other hand as it was. */
    private static void otherHand(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.SURVIVAL, true);
        ItemStack wrench = wrench(WrenchMode.ADJUST);
        player.setItemInHand(InteractionHand.OFF_HAND, wrench);
        player.setXRot(-90); // at the sky
        wrench.getItem().use(helper.getLevel(), player, InteractionHand.OFF_HAND);
        check(helper, WrenchItem.mode(wrench) == WrenchMode.ADJUST, "still Adjust");
        helper.succeed();
    }

    /** Parted, then the east fence broken: the west one forgets its side; a fence set there again joins it. */
    private static void forgotten(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 2);
        BlockPos b = new BlockPos(2, 1, 2);
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        place(helper, a, fence);
        place(helper, b, fence);
        ServerPlayer player = player(helper, GameType.SURVIVAL, true);
        eastArm(helper, player, wrench(WrenchMode.ADJUST), a);
        check(helper, JoinOverrides.forced(helper.getLevel(), helper.absolutePos(a), Direction.EAST) == Boolean.FALSE, "kept apart");
        ServerPlayer breaker = player(helper, GameType.SURVIVAL, false);
        breaker.gameMode.destroyBlock(helper.absolutePos(b));
        helper.assertBlockNotPresent(Blocks.OAK_FENCE, b);
        check(helper, JoinOverrides.forced(helper.getLevel(), helper.absolutePos(a), Direction.EAST) == null,
                "the one beside it gone, the parting is forgotten");
        place(helper, b, fence);
        check(helper, joined(helper, a, Direction.EAST) && joined(helper, b, Direction.WEST), "a fence set there again joins");
        // and the clicked one gone without a break: its square keeps nothing
        eastArm(helper, player, player.getMainHandItem(), a);
        helper.setBlock(a, Blocks.AIR);
        check(helper, JoinOverrides.bits(helper.getLevel(), helper.absolutePos(a)) == 0, "gone without a break, the same");
        place(helper, a, fence);
        check(helper, joined(helper, a, Direction.EAST) && joined(helper, b, Direction.WEST), "and a new one joins");
        helper.succeed();
    }

    /** Two parted fences pushed one block east by a piston land parted, and their old squares keep nothing. */
    private static void piston(GameTestHelper helper) {
        BlockPos piston = new BlockPos(0, 1, 3);
        BlockPos a = new BlockPos(1, 1, 3);
        BlockPos b = new BlockPos(2, 1, 3);
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        helper.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        place(helper, a, fence);
        place(helper, b, fence);
        ServerPlayer player = player(helper, GameType.SURVIVAL, true);
        eastArm(helper, player, wrench(WrenchMode.ADJUST), a);
        check(helper, !joined(helper, a, Direction.EAST), "parted before the push");
        helper.setBlock(piston.below(), Blocks.REDSTONE_BLOCK);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.OAK_FENCE, b.east());
            helper.assertBlockPresent(Blocks.OAK_FENCE, b);
            check(helper, !joined(helper, b, Direction.EAST), "pushed, the parted side is still apart");
            check(helper, JoinOverrides.forced(helper.getLevel(), helper.absolutePos(b), Direction.EAST) == Boolean.FALSE,
                    "the parting went with it");
            check(helper, JoinOverrides.bits(helper.getLevel(), helper.absolutePos(a)) == 0, "the square left behind keeps nothing");
        });
    }

    /**
     * Our asking whether something may be placed (a rope's end, a laser's crop) sets the cell aside and back, and posts
     * a place event: that is no block set down, and a fence's sides set by hand there stay set (r22 CXC-007: they were
     * forgotten), whether the claim there lets it or not.
     */
    private static void askingKeepsSides(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, Blocks.OAK_FENCE);
        BlockPos at = helper.absolutePos(pos);
        JoinOverrides.toggle(helper.getLevel(), at, helper.getBlockState(pos), Direction.EAST);
        int before = JoinOverrides.bits(helper.getLevel(), at);
        check(helper, before != 0, "its east side set by hand");
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        dev.nookworks.feature.Permission.mayPlace(helper.getLevel(), at, Direction.UP, player);
        check(helper, JoinOverrides.bits(helper.getLevel(), at) == before,
                "still so after the asking: " + JoinOverrides.bits(helper.getLevel(), at) + ", was " + before);
        UUID owner = UUID.randomUUID();
        BlockTests.underClaim(owner, () -> dev.nookworks.feature.Permission.mayPlace(helper.getLevel(), at, Direction.UP, player));
        check(helper, JoinOverrides.bits(helper.getLevel(), at) == before, "and after an asking a claim says no to");
        helper.succeed();
    }
}
