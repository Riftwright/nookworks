package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.nookworks.block.EnergyCableBlock;
import dev.nookworks.drive.BeltKind;
import dev.nookworks.drive.Belts;
import dev.nookworks.drive.DriveWheelBlock;
import dev.nookworks.drive.DriveWheelBlockEntity;
import dev.nookworks.machine.DieselGeneratorBlockEntity;
import dev.nookworks.machine.GeneratorBlockEntity;
import dev.nookworks.mercury.StirringTankBlockEntity;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The r15 late audit's findings, N1-N5 (2026-10-14; AI_review/r15/n_triage.md): an Energy Cable beside other mods'
 * endless stores tells of all the room there is, not of less than none; a Stirring Tank keeps the caustic soda it took
 * in in its item; a belt whose far wheel was broken while its owner was away comes back even with a new wheel set down
 * in that place since; a Generator broken mid-burn loses one small gulp; and a block is refused in a belt's way by the
 * wheels loaded, not by a walk through every block entity near.
 */
final class R15LateAuditTests {
    private R15LateAuditTests() {}

    /**
     * Where a test stands another mod's endless store (as Mekanism's creative cube tells of Long.MAX_VALUE of room): a
     * block of reinforced deepslate there gives one, through a provider only the tests register. Nowhere else.
     */
    private static final Set<BlockPos> ENDLESS = ConcurrentHashMap.newKeySet();

    static void register() {
        test("cable_room_stops_at_the_most_a_long_holds", R15LateAuditTests::cableRoom);
        test("stirring_tank_keeps_the_soda_it_took_in_its_item", 260, R15LateAuditTests::sodaKept);
        test("a_belt_whose_far_wheel_was_replaced_while_away_comes_back", 60, R15LateAuditTests::farWheelReplaced);
        test("a_far_wheel_that_let_go_itself_is_not_paid_twice", 60, R15LateAuditTests::farWheelLetGo);
        test("a_generator_broken_mid_burn_loses_one_small_gulp", 40, R15LateAuditTests::generatorGulp);
        test("a_belts_way_is_kept_by_the_wheels_loaded", R15LateAuditTests::beltWayByLoadedWheels);
        test("a_belt_owned_by_a_far_end_away_is_left_on", R15LateAuditTests::takeOffWithTheFarEndAway);
        test("a_second_belt_waits_for_a_stale_one_to_go", 80, R15LateAuditTests::secondBeltWaits);
    }

    /** The test-only endless store, on the mod bus (called from NookworksGameTests while game tests are on). */
    static void listen(IEventBus modEventBus) {
        modEventBus.addListener(RegisterCapabilitiesEvent.class, event -> event.registerBlock(Capabilities.Energy.BLOCK,
                (level, pos, state, be, side) -> ENDLESS.contains(pos) ? Endless.INSTANCE : null, Blocks.REINFORCED_DEEPSLATE));
    }

    /** Takes all it is given and tells of all the room a long holds. */
    private static final class Endless implements EnergyHandler {
        static final Endless INSTANCE = new Endless();

        @Override
        public long getAmountAsLong() {
            return 0;
        }

        @Override
        public long getCapacityAsLong() {
            return Long.MAX_VALUE;
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            return amount;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return 0;
        }
    }

    /** A store of {@code capacity} holding {@code amount}, as it tells. */
    private record Told(long amount, long capacity) implements EnergyHandler {
        @Override
        public long getAmountAsLong() {
            return this.amount;
        }

        @Override
        public long getCapacityAsLong() {
            return this.capacity;
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return 0;
        }
    }

    /**
     * N1: two of another mod's endless stores on one cable added up past Long.MAX_VALUE to below zero, and the cable
     * looked full to whatever asks before it sends. Held at the most there is; a store telling of more than it holds has
     * no room, and does not take from the others'.
     */
    private static void cableRoom(GameTestHelper helper) {
        check(helper, EnergyCableBlock.roomIn(List.of(Endless.INSTANCE, Endless.INSTANCE)) == Long.MAX_VALUE, "two endless stores: "
                + EnergyCableBlock.roomIn(List.of(Endless.INSTANCE, Endless.INSTANCE)));
        check(helper, EnergyCableBlock.roomIn(List.of(new Told(400, 1_000), Endless.INSTANCE, new Told(0, 500))) == Long.MAX_VALUE,
                "an endless store among others");
        check(helper, EnergyCableBlock.roomIn(List.of(new Told(400, 1_000), new Told(0, 500))) == 1_100, "two small ones add up");
        check(helper, EnergyCableBlock.roomIn(List.of(new Told(900, 100), new Told(-5, 50))) == 50,
                "one holding more than it holds has none, and one telling of less than nothing holds nothing");
        ServerLevel level = helper.getLevel();
        BlockPos cable = helper.absolutePos(new BlockPos(2, 2, 2));
        AfterTest.undo(helper, () -> ENDLESS.removeAll(Set.of(cable.west(), cable.east())));
        ENDLESS.add(cable.west());
        ENDLESS.add(cable.east());
        level.setBlockAndUpdate(cable, ModBlocks.ENERGY_CABLE.get().defaultBlockState());
        level.setBlockAndUpdate(cable.west(), Blocks.REINFORCED_DEEPSLATE.defaultBlockState()); // the cable joins each as it comes
        level.setBlockAndUpdate(cable.east(), Blocks.REINFORCED_DEEPSLATE.defaultBlockState());
        long room = EnergyCableBlock.networkHandler(level, cable).getCapacityAsLong();
        check(helper, room == Long.MAX_VALUE, "a cable between two endless stores tells of all the room there is: " + room);
        int taken;
        try (Transaction tx = Transaction.openRoot()) {
            taken = EnergyCableBlock.networkHandler(level, cable).insert(1_000, tx);
            tx.commit();
        }
        check(helper, taken == 1_000, "and power put in goes on to them: " + taken);
        helper.succeed();
    }

    /**
     * N2: the caustic soda a Stirring Tank has taken in serves four batches; broken after the first, its item keeps the
     * three left (with a line under its name), and set down again it stirs the next with no soda in its slot. One with
     * none taken in keeps nothing, and stacks with a new one.
     */
    private static void sodaKept(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos first = new BlockPos(1, 2, 1);
        BlockPos second = new BlockPos(5, 2, 5);
        helper.setBlock(first.below(), Blocks.CHEST);
        helper.setBlock(second.below(), Blocks.CHEST);
        helper.setBlock(first, ModBlocks.STIRRING_TANK.get());
        var tank = helper.getBlockEntity(first, StirringTankBlockEntity.class);
        tank.energy().set((int) tank.energy().getCapacityAsLong());
        tank.setItem(0, new ItemStack(ModOil.METHANOL.get(), 1));
        tank.setItem(StirringTankBlockEntity.CATALYST, new ItemStack(ModItems.CAUSTIC_SODA.get()));
        try (Transaction tx = Transaction.openRoot()) {
            tank.fluidHandler().insert(FluidResource.of(OilFluids.SOURCE.get()), 1_000, tx);
            tx.commit();
        }
        BlockPos fresh = new BlockPos(5, 2, 1);
        helper.setBlock(fresh, ModBlocks.STIRRING_TANK.get());
        BlockPos freshAt = helper.absolutePos(fresh);
        ItemStack plain = Block.getDrops(level.getBlockState(freshAt), level, freshAt, level.getBlockEntity(freshAt)).stream()
                .filter(s -> s.is(ModItems.STIRRING_TANK.get())).findFirst().orElse(ItemStack.EMPTY);
        check(helper, !plain.isEmpty() && plain.get(ModDataComponents.CATALYST_LEFT.get()) == null
                && ItemStack.isSameItemSameComponents(plain, new ItemStack(ModItems.STIRRING_TANK.get())),
                "a tank with no soda taken in keeps nothing: " + plain.getComponentsPatch());
        helper.setBlock(fresh, Blocks.AIR);
        int batch = StirringTankBlockEntity.TICKS + 10;
        helper.runAfterDelay(batch, () -> {
            check(helper, tank.catalystLeft() == 3 && tank.getItem(StirringTankBlockEntity.CATALYST).isEmpty(),
                    "the first batch took the soda in, three batches left in it: " + tank.catalystLeft());
            level.destroyBlock(helper.absolutePos(first), true);
            ItemStack item = level.getEntitiesOfClass(ItemEntity.class, helper.absoluteAABB(new AABB(0, 1, 0, 3, 4, 3))).stream()
                    .map(ItemEntity::getItem).filter(s -> s.is(ModItems.STIRRING_TANK.get())).findFirst().orElse(ItemStack.EMPTY);
            check(helper, Integer.valueOf(3).equals(item.get(ModDataComponents.CATALYST_LEFT.get())),
                    "broken, its item keeps the three batches: " + item.getComponentsPatch());
            helper.setBlock(second, ModBlocks.STIRRING_TANK.get());
            var again = helper.getBlockEntity(second, StirringTankBlockEntity.class);
            again.applyComponentsFromItemStack(item);
            check(helper, again.catalystLeft() == 3, "set down again it has them: " + again.catalystLeft());
            check(helper, again.oilStored() == 750 && again.biodieselStored() == 250, "with its oil and biodiesel: "
                    + again.oilStored() + ", " + again.biodieselStored());
            again.setItem(0, new ItemStack(ModOil.METHANOL.get(), 1));
            helper.runAfterDelay(batch, () -> {
                check(helper, again.biodieselStored() == 500 && again.catalystLeft() == 2,
                        "and stirs the next batch with no soda in its slot: " + again.biodieselStored() + " mB, " + again.catalystLeft() + " left");
                helper.succeed();
            });
        });
    }

    // ---- drive belts ----

    private static DriveWheelBlockEntity wheel(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModOil.DRIVE_WHEEL.get().defaultBlockState().setValue(DriveWheelBlock.FACING, Direction.NORTH));
        return helper.getBlockEntity(pos, DriveWheelBlockEntity.class);
    }

    /** Two wheels four blocks apart, belted with eight rope; the one owning the belt first. */
    private static DriveWheelBlockEntity[] belted(GameTestHelper helper) {
        DriveWheelBlockEntity a = wheel(helper, new BlockPos(1, 2, 1));
        DriveWheelBlockEntity b = wheel(helper, new BlockPos(5, 2, 1));
        check(helper, Belts.tie(helper.getLevel(), a, b, BeltKind.ROPE, null, new ItemStack(ModItems.ROPE.get())) == null, "tied");
        check(helper, a.belts().getFirst().count() == 8, "with eight rope");
        return a.owns(b.getBlockPos()) ? new DriveWheelBlockEntity[] {a, b} : new DriveWheelBlockEntity[] {b, a};
    }

    private static int dropped(GameTestHelper helper, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.absoluteAABB(new AABB(0, 1, 0, 7, 5, 3)))
                .stream().filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
    }

    /**
     * N3: the wheel that does not own the belt broken while the owner is away gives nothing back (the owner does, when
     * next loaded); a new wheel set down in its place meanwhile was taken for the old one, which had let go of the belt
     * and given it back there - and the eight rope were lost. The owner now knows the wheel it was tied to.
     */
    private static void farWheelReplaced(GameTestHelper helper) {
        DriveWheelBlockEntity[] pair = belted(helper);
        DriveWheelBlockEntity owner = pair[0];
        BlockPos other = pair[1].getBlockPos();
        // broken while the owner was away: what it does on going with the owner there is not done
        helper.getLevel().setBlock(pair[1].getBlockPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        // where it stood, as the world has it (a test's relative places do not come back the same on 26.2)
        helper.getLevel().setBlock(other, ModOil.DRIVE_WHEEL.get().defaultBlockState().setValue(DriveWheelBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
        DriveWheelBlockEntity replaced = (DriveWheelBlockEntity) helper.getLevel().getBlockEntity(other);
        check(helper, replaced.wheelId() != pair[1].wheelId(), "a new wheel is another wheel");
        helper.succeedWhen(() -> {
            check(helper, owner.belts().isEmpty() && replaced.belts().isEmpty(), "the owner lets go of the belt");
            int rope = dropped(helper, ModItems.ROPE.get());
            check(helper, rope == 8, "and gives its eight rope back: " + rope);
        });
    }

    /**
     * Fix check 6, S1: a wheel whose belts are taken off while their far ends are away gives back a belt it owns, and
     * leaves on one the far end owns, for that end to settle when it loads: taken off and paid for here, the owner paid
     * for it again if it found this wheel broken (or another set down in its place) by then. The far ends are set a
     * hundred thousand blocks off, out of the loaded world.
     */
    private static void takeOffWithTheFarEndAway(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 2, 2);
        DriveWheelBlockEntity wheel = wheel(helper, pos);
        BlockPos east = wheel.getBlockPos().offset(100_000, 0, 0);
        BlockPos west = wheel.getBlockPos().offset(-100_000, 0, 0);
        check(helper, !level.isLoaded(east) && !level.isLoaded(west), "the far ends are away");
        BlockPos theirs = wheel.owns(east) ? west : east;
        BlockPos ours = theirs == east ? west : east;
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);

        beltTo(level, wheel, theirs);
        helper.useBlock(pos, player);
        check(helper, wheel.belts().size() == 1 && ropeHeld(player) == 0, "a belt the far end owns is left on, nothing given: "
                + wheel.belts().size() + " belt(s), " + ropeHeld(player) + " rope");

        beltTo(level, wheel, ours);
        helper.useBlock(pos, player);
        check(helper, wheel.belts().isEmpty() && ropeHeld(player) == 8, "one it owns comes off, its rope given back: "
                + wheel.belts().size() + " belt(s), " + ropeHeld(player) + " rope");
        helper.succeed();
    }

    /**
     * Fix check 6, NIT 3: a wheel still holding a stale belt to a place (its far wheel broken and set down again before it
     * looked) refuses a new one tied to it from there until it has let the stale one go - which it does by place, and
     * took the new one with it, its rope lost. Once let go, the new belt is tied and stays.
     */
    private static void secondBeltWaits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DriveWheelBlockEntity a = wheel(helper, new BlockPos(1, 2, 1));
        DriveWheelBlockEntity b = wheel(helper, new BlockPos(5, 2, 1));
        beltTo(level, a, b.getBlockPos()); // stale: b holds no belt back
        String why = refusal(Belts.tie(level, b, a, BeltKind.ROPE, null, new ItemStack(ModItems.ROPE.get(), 8)));
        check(helper, "block.nookworks.drive_wheel.already".equals(why), "a belt from the far wheel is refused while the stale one is held: " + why);
        helper.runAfterDelay(30, () -> { // a wheel looks over its belts once a second
            check(helper, a.belts().isEmpty(), "the stale belt let go of");
            String then = refusal(Belts.tie(level, b, a, BeltKind.ROPE, null, new ItemStack(ModItems.ROPE.get(), 8)));
            check(helper, then == null, "then it ties: " + then);
            helper.runAfterDelay(30, () -> {
                check(helper, a.belts().size() == 1 && b.belts().size() == 1, "one belt, held at both ends");
                helper.succeed();
            });
        });
    }

    /** The key of what {@link Belts#tie} answered, or null if it tied. */
    private static String refusal(Component answer) {
        return answer == null ? null : answer.getContents() instanceof TranslatableContents t ? t.getKey() : answer.getString();
    }

    /** {@code wheel} belted with eight rope to {@code far} alone, as if read so from a save. */
    private static void beltTo(ServerLevel level, DriveWheelBlockEntity wheel, BlockPos far) {
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        wheel.saveCustomOnly(out);
        CompoundTag tag = out.buildResult();
        tag.remove("BeltOffsets");
        tag.put("Belts", DriveWheelBlockEntity.Belt.CODEC.listOf().encodeStart(NbtOps.INSTANCE,
                List.of(new DriveWheelBlockEntity.Belt(far, BeltKind.ROPE, ModItems.ROPE.get(), 8, 0L))).getOrThrow());
        wheel.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
    }

    private static int ropeHeld(Player player) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.ROPE.get())) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /**
     * The other way round, the rule kept: the far wheel - the same one - let go of the belt itself (taken off while the
     * owner was away, its rope given back there); the owner, loaded again, lets go of it and gives nothing more.
     */
    private static void farWheelLetGo(GameTestHelper helper) {
        DriveWheelBlockEntity[] pair = belted(helper);
        DriveWheelBlockEntity owner = pair[0];
        DriveWheelBlockEntity far = pair[1];
        var level = helper.getLevel();
        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        far.saveCustomOnly(saved);
        CompoundTag tag = saved.buildResult();
        tag.remove("Belts");
        tag.remove("BeltOffsets");
        far.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        check(helper, far.belts().isEmpty() && owner.belts().size() == 1, "the far end has let go, the owner not yet");
        helper.succeedWhen(() -> {
            check(helper, owner.belts().isEmpty(), "the owner lets go of the belt");
            int rope = dropped(helper, ModItems.ROPE.get());
            check(helper, rope == 0, "and gives nothing: it was given back at the far end: " + rope);
        });
    }

    /**
     * N5: a block put down is looked at against the belts of the wheels loaded - not against every block entity within
     * a belt's length of it - and still refused in a belt's way. Wheels taken away (as with their area unloaded) stand
     * in no one's way; loaded again from their save, they do from their first tick.
     */
    private static void beltWayByLoadedWheels(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DriveWheelBlockEntity[] pair = belted(helper);
        BlockPos middle = helper.absolutePos(new BlockPos(3, 2, 1));
        BlockPos aside = helper.absolutePos(new BlockPos(3, 2, 4));
        var stone = Blocks.STONE.defaultBlockState();
        check(helper, Belts.inABeltsWay(level, middle, stone), "a stone on the belt's line is in its way");
        check(helper, !Belts.inABeltsWay(level, aside, stone), "one beside it is not");
        CompoundTag[] saves = new CompoundTag[2];
        BlockPos[] places = new BlockPos[2];
        for (int i = 0; i < 2; i++) {
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            pair[i].saveCustomOnly(out);
            saves[i] = out.buildResult();
            places[i] = pair[i].getBlockPos();
            level.setBlock(pair[i].getBlockPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        }
        check(helper, pair[0].isRemoved() && !Belts.inABeltsWay(level, middle, stone), "the wheels gone, nothing is in a belt's way");
        for (int i = 0; i < 2; i++) {
            // where they stood, as the world has it (a test's relative places do not come back the same on 26.2)
            level.setBlock(places[i], ModOil.DRIVE_WHEEL.get().defaultBlockState().setValue(DriveWheelBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
            ((DriveWheelBlockEntity) level.getBlockEntity(places[i])).loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saves[i]));
        }
        // as a chunk's block entities are told they are loaded: at the level's next block entity tick, which on 26.2 can
        // come a tick after the test's own
        helper.succeedWhen(() -> check(helper, Belts.inABeltsWay(level, middle, stone), "loaded again, the belt is in the way again"));
    }

    /**
     * N4: what a Generator is burning is not kept in its item (as a furnace's fire is not), so it takes its liquid fuel
     * a small gulp at a time - the Diesel Generator's 15 mB, not the tenth of a bucket it took before.
     */
    private static void generatorGulp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.GENERATOR.get());
        var generator = helper.getBlockEntity(pos, GeneratorBlockEntity.class);
        try (Transaction tx = Transaction.openRoot()) {
            generator.fluidHandler().insert(FluidResource.of(OilFluids.SOURCE.get()), 1_500, tx);
            tx.commit();
        }
        check(helper, GeneratorBlockEntity.GULP <= DieselGeneratorBlockEntity.GULP, "a gulp no bigger than the Diesel Generator's: "
                + GeneratorBlockEntity.GULP);
        helper.runAfterDelay(5, () -> {
            check(helper, generator.fuelStored() == 1_485 && generator.energyStored() > 0,
                    "burning, it has taken one gulp: " + generator.fuelStored() + " mB left");
            level.destroyBlock(helper.absolutePos(pos), true);
            ItemStack item = level.getEntitiesOfClass(ItemEntity.class, helper.absoluteAABB(new AABB(0, 1, 0, 5, 5, 5))).stream()
                    .map(ItemEntity::getItem).filter(s -> s.is(ModBlocks.GENERATOR.get().asItem())).findFirst().orElse(ItemStack.EMPTY);
            int kept = item.getOrDefault(ModDataComponents.HELD_FLUIDS.get(), List.<SimpleFluidContent>of()).stream()
                    .mapToInt(SimpleFluidContent::getAmount).sum();
            check(helper, kept == 1_485, "broken, its item keeps all but the 15 mB gulp: " + kept + " mB");
            helper.succeed();
        });
    }
}
