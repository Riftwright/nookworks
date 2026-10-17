package dev.nookworks.gametest;

import org.jspecify.annotations.Nullable;
import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;
import static dev.nookworks.gametest.TransportTests.countIn;

import java.util.List;

import dev.nookworks.barter.BarterRoom;
import dev.nookworks.barter.BarterTerminalBlockEntity;
import dev.nookworks.barter.PiglinShelter;
import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.mixin.AbstractPiglinAccessor;
import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.registry.ModBarter;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModVentilation;
import dev.nookworks.ventilation.VentFanBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import java.util.function.Consumer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.minecraft.util.ProblemReporter;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * The Barter Terminal and its piglins' room (the user, 2026-10-03): how the room is found and measured, piglins let in
 * and taken out by bottle, kept from turning while the room has air, handed gold, and what they throw gathered.
 */
final class BarterTests {
    static void register() {
        test("barter_room_is_found_and_measured", BarterTests::roomIsFound);
        test("barter_room_faults_are_found_where_they_are", BarterTests::roomFaults);
        test("barter_terminal_lets_piglins_in_and_out_by_bottle", 300, BarterTests::piglinsInAndOut);
        test("barter_terminal_keeps_its_piglins_while_there_is_air", 600, BarterTests::piglinsKept);
        test("barter_terminal_hands_gold_and_gathers_what_comes_back", 400, BarterTests::barters);
        test("barter_terminal_takes_a_bottle_by_click", BarterTests::bottleByClick);
        test("barter_room_too_long_is_refused", BarterTests::roomTooLong);
        test("barter_terminal_piglins_do_not_despawn", 300, BarterTests::keptPiglinsStay);
        test("barter_terminal_makes_no_air_on_a_trickle_of_power", 460, BarterTests::tricklePowerMakesNoAir);
        test("barter_terminal_hands_no_gold_on_land_not_its_owners", 200, BarterTests::noGoldOnClaimedLand);
        test("barter_piglin_bottled_from_a_room_is_no_ones", BarterTests::bottledKeptPiglinIsNoOnes);
        test("barter_piglin_taken_off_far_from_its_room_is_let_go", 80, BarterTests::piglinFarFromItsRoomIsLetGo);
        test("barter_room_reloaded_is_looked_at_before_it_is_trusted", 120, BarterTests::reloadedRoomIsLookedAtFirst);
        test("barter_terminal_keeps_a_bottled_piglin_with_no_chest_below", 40, BarterTests::bottledPiglinWaits);
    }

    /** A floor longer than four blocks is past what a terminal takes: its size is the fault. */
    private static void roomTooLong(GameTestHelper helper) {
        BlockPos fan = room(helper, 1, 5, 2);
        helper.setBlock(fan.east(), ModBarter.BARTER_TERMINAL.get());
        var found = BarterRoom.find(helper.getLevel(), helper.absolutePos(fan.east()));
        check(helper, found.fault() == BarterRoom.Fault.SIZE, "a 1x5 floor is too long: " + found);
        helper.succeed();
    }

    /**
     * A piglin let in by bottle is not made persistent, and vanilla despawns a mob that is not once no player is within
     * 128 blocks: piglins in a barter room far from anyone were found gone. Kept by the room, it stays. (The other tests
     * spawn theirs persistent, which hid this.)
     */
    private static void keptPiglinsStay(GameTestHelper helper) {
        BlockPos fan = room(helper, 1, 1, 2);
        var terminal = terminal(helper, fan.east());
        terminal.setItem(BarterTerminalBlockEntity.BOTTLE, MobBottleItem.filled(EntityTypes.PIGLIN));
        ServerPlayer far = MockPlayers.inLevel(helper);
        Vec3 away = helper.absoluteVec(new Vec3(0.5, 2, 200.5));
        far.snapTo(away.x, away.y, away.z);
        AABB inside = new AABB(helper.absolutePos(new BlockPos(1, 2, 1))).expandTowards(0, 1, 0);
        helper.succeedWhen(() -> {
            var piglins = helper.getLevel().getEntitiesOfClass(Piglin.class, inside);
            check(helper, piglins.size() == 1, "the piglin is let in");
            Piglin piglin = piglins.get(0);
            check(helper, !piglin.isPersistenceRequired() && PiglinShelter.kept(piglin), "kept by the room, not made persistent");
            piglin.checkDespawn();
            check(helper, piglin.isAlive(), "and not despawned, the only player 200 blocks off");
        });
    }

    /**
     * A trickle of power - 1 FE/t, a twentieth of what the air takes - makes no air at all: the second paid without a
     * break before any is made is paid for too, so a trickle saved up does not buy it. (It did: saved to 20 FE, it passed
     * the second on its savings and bought a second's air, over and over, and the room was kept on next to nothing.)
     */
    private static void tricklePowerMakesNoAir(GameTestHelper helper) {
        BlockPos fan = room(helper, 1, 1, 2);
        var terminal = terminal(helper, fan.east());
        terminal.energy().set(0);
        helper.onEachTick(() -> terminal.energy().set((int) Math.min(BarterTerminalBlockEntity.CAPACITY, terminal.energy().getAmountAsLong() + 1)));
        helper.runAfterDelay(440, () -> {
            check(helper, terminal.room().whole(), "the room is whole: " + terminal.room());
            check(helper, terminal.air() == 0, "and on 1 FE/t no air is made in it, got " + terminal.air());
            helper.succeed();
        });
    }

    /**
     * A room on land a claim keeps from the terminal's owner: nothing handed in or taken out - no gold handed to its
     * piglins (it was, though what they threw back was never gathered) - and the terminal says why.
     */
    private static void noGoldOnClaimedLand(GameTestHelper helper) {
        BlockPos fan = room(helper, 1, 1, 2);
        var terminal = terminal(helper, fan.east());
        terminal.setOwner(UUID.randomUUID());
        UUID claimant = UUID.randomUUID();
        AABB land = helper.getBounds();
        Consumer<BreakBlockEvent> breaking = event -> {
            if (land.contains(Vec3.atCenterOf(event.getPos())) && (event.getPlayer() == null || !claimant.equals(event.getPlayer().getUUID()))) {
                event.setCanceled(true);
            }
        };
        Consumer<BlockEvent.EntityPlaceEvent> placing = event -> {
            if (land.contains(Vec3.atCenterOf(event.getPos())) && (event.getEntity() == null || !claimant.equals(event.getEntity().getUUID()))) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(BreakBlockEvent.class, breaking);
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, placing);
        AfterTest.undo(helper, () -> {
            NeoForge.EVENT_BUS.unregister(breaking);
            NeoForge.EVENT_BUS.unregister(placing);
        });
        Piglin piglin = helper.spawn(EntityTypes.PIGLIN, new BlockPos(1, 2, 1));
        piglin.setBaby(false);
        piglin.setPersistenceRequired();
        terminal.setItem(BarterTerminalBlockEntity.GOLD, new ItemStack(Items.GOLD_INGOT, 2));
        helper.runAfterDelay(120, () -> {
            check(helper, terminal.getItem(BarterTerminalBlockEntity.GOLD).getCount() == 2 && piglin.getOffhandItem().isEmpty(),
                    "no gold handed over on land not the owner's");
            check(helper, terminal.guiStatus() == MachineStatus.ROOM_PROTECTED, "and it says so: " + terminal.guiStatus());
            helper.succeed();
        });
    }

    /**
     * A piglin kept by a room, bottled by hand and let out elsewhere, is no one's: not immune to the change for good, nor
     * spared despawning, because of a room far off. (Its keeping went into the bottle with it.)
     */
    private static void bottledKeptPiglinIsNoOnes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Piglin piglin = helper.spawn(EntityTypes.PIGLIN, new BlockPos(3, 2, 3));
        piglin.setBaby(false);
        PiglinShelter.keep(piglin, helper.absolutePos(new BlockPos(0, 2, 0)));
        check(helper, PiglinShelter.kept(piglin) && ((AbstractPiglinAccessor) piglin).nookworks$isImmuneToZombification(), "kept by a room");
        ItemStack bottle = MobBottleItem.filled(piglin);
        piglin.discard();
        var out = MobBottleItem.letOut(level, bottle, helper.absolutePos(new BlockPos(3, 2, 3)));
        check(helper, out instanceof AbstractPiglin, "let out again: " + out);
        AbstractPiglin again = (AbstractPiglin) out;
        check(helper, !PiglinShelter.kept(again) && !((AbstractPiglinAccessor) again).nookworks$isImmuneToZombification(),
                "and no longer kept, nor immune");
        again.discard();
        helper.succeed();
    }

    /** A kept piglin found far from its terminal - carried off by other means - is let go, the terminal loaded or not. */
    private static void piglinFarFromItsRoomIsLetGo(GameTestHelper helper) {
        Piglin piglin = helper.spawn(EntityTypes.PIGLIN, new BlockPos(3, 2, 3));
        piglin.setBaby(false);
        piglin.setPersistenceRequired();
        PiglinShelter.keep(piglin, helper.absolutePos(new BlockPos(3, 2, 3)).offset(2_000, 0, 0)); // a terminal far off, unloaded
        helper.succeedWhen(() -> check(helper, !PiglinShelter.kept(piglin) && !((AbstractPiglinAccessor) piglin).nookworks$isImmuneToZombification(),
                "let go, far from its room"));
    }

    /**
     * A room saved is taken up again after a reload as not yet seen: its piglins kept from the first tick, but nothing let
     * in and no air made until the terminal has looked at it whole (it may have been broken open meanwhile).
     */
    private static void reloadedRoomIsLookedAtFirst(GameTestHelper helper) {
        BlockPos fan = room(helper, 1, 1, 2);
        var terminal = terminal(helper, fan.east());
        helper.runAfterDelay(40, () -> {
            check(helper, terminal.room().whole(), "the room found whole: " + terminal.room());
            try (var reporter = new ProblemReporter.ScopedCollector(terminal.problemPath(), LogUtils.getLogger())) {
                var output = TagValueOutput.createWithContext(reporter, helper.getLevel().registryAccess());
                terminal.saveWithoutMetadata(output);
                var copy = new BarterTerminalBlockEntity(terminal.getBlockPos(), terminal.getBlockState());
                copy.loadWithComponents(TagValueInput.create(reporter, helper.getLevel().registryAccess(), output.buildResult()));
                check(helper, copy.room().fault() == BarterRoom.Fault.UNSEEN && copy.room().inside() != null && !copy.room().whole(),
                        "reloaded, the room is known but not yet seen whole: " + copy.room());
            }
            helper.succeed();
        });
    }

    /**
     * A room of the Nether's ground: inside {@code w} x {@code d} from (1, 2, 1) up, {@code h} high; glass round it, its
     * floor crimson nylium. A Ventilation Fan in the east wall at the room's first row facing in; returns where it is.
     */
    static BlockPos room(GameTestHelper helper, int w, int d, int h) {
        for (int x = 0; x <= w + 1; x++) {
            for (int z = 0; z <= d + 1; z++) {
                for (int y = 1; y <= h + 2; y++) {
                    boolean inside = x >= 1 && x <= w && z >= 1 && z <= d && y >= 2 && y <= h + 1;
                    helper.setBlock(new BlockPos(x, y, z), inside ? Blocks.AIR : y == 1 ? Blocks.CRIMSON_NYLIUM : Blocks.GLASS);
                }
            }
        }
        BlockPos fan = new BlockPos(w + 1, 2, 1);
        helper.setBlock(fan, ModVentilation.VENT_FAN.get().defaultBlockState().setValue(VentFanBlock.FACING, Direction.WEST));
        return fan;
    }

    /** A powered Barter Terminal at {@code pos}, a chest under it. */
    static BarterTerminalBlockEntity terminal(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.CHEST);
        helper.setBlock(pos, ModBarter.BARTER_TERMINAL.get());
        var terminal = helper.getBlockEntity(pos, BarterTerminalBlockEntity.class);
        terminal.energy().set(BarterTerminalBlockEntity.CAPACITY);
        return terminal;
    }

    /**
     * A terminal against a fan's back finds the room behind the fan: 2x2, three high, 4 floor blocks, 12 piglins. Joined
     * by a run of Ducts it finds it the same; alone, it finds no fan.
     */
    private static void roomIsFound(GameTestHelper helper) {
        BlockPos fan = room(helper, 2, 2, 3);
        BlockPos at = fan.east();
        helper.setBlock(at, ModBarter.BARTER_TERMINAL.get());
        var found = BarterRoom.find(helper.getLevel(), helper.absolutePos(at));
        check(helper, found.whole() && found.floor() == 4 && found.capacity() == 12, "a 2x2 room, 12 piglins: " + found);
        check(helper, helper.absolutePos(fan).equals(found.fan()), "through the fan against its back");
        check(helper, found.inside().getYSpan() == 3, "three high");

        helper.setBlock(at, Blocks.AIR);
        BlockPos far = fan.east(3);
        helper.setBlock(far, ModBarter.BARTER_TERMINAL.get());
        for (BlockPos duct : List.of(fan.east(), fan.east(2))) { // joined up as a player's placing joins them
            helper.setBlock(duct, Block.updateFromNeighbourShapes(ModVentilation.DUCT.get().defaultBlockState(), helper.getLevel(),
                    helper.absolutePos(duct)));
        }
        found = BarterRoom.find(helper.getLevel(), helper.absolutePos(far));
        check(helper, found.whole() && found.floor() == 4, "along two Ducts: " + found);

        helper.setBlock(fan.east(), Blocks.AIR);
        found = BarterRoom.find(helper.getLevel(), helper.absolutePos(far));
        check(helper, found.fault() == BarterRoom.Fault.NO_FAN, "the ducts cut, no fan: " + found);
        check(helper, MachineLayout.BARTER_TERMINAL.slotCount() == BarterTerminalBlockEntity.FIRST_OUT + BarterTerminalBlockEntity.OUTPUTS,
                "its screen has a slot for each of its slots");
        helper.succeed();
    }

    /**
     * What is wrong with a room is found where it is: a gap in its wall, a block of its floor not of the Nether, a block
     * standing inside it, a ceiling too low. (A room too long: {@link #roomTooLong}.)
     */
    private static void roomFaults(GameTestHelper helper) {
        BlockPos fan = room(helper, 2, 2, 2);
        BlockPos at = fan.east();
        helper.setBlock(at, ModBarter.BARTER_TERMINAL.get());
        var level = helper.getLevel();
        BlockPos terminal = helper.absolutePos(at);

        BlockPos gap = new BlockPos(1, 3, 3);
        helper.setBlock(gap, Blocks.AIR);
        var found = BarterRoom.find(level, terminal);
        check(helper, found.fault() == BarterRoom.Fault.OPEN && helper.absolutePos(gap).equals(found.at()), "the gap, where it is: " + found);
        helper.setBlock(gap, Blocks.GLASS);

        BlockPos stone = new BlockPos(2, 1, 2);
        helper.setBlock(stone, Blocks.STONE);
        found = BarterRoom.find(level, terminal);
        check(helper, found.fault() == BarterRoom.Fault.FLOOR && helper.absolutePos(stone).equals(found.at()), "the stone in the floor: " + found);
        helper.setBlock(stone, Blocks.SOUL_SOIL);
        check(helper, BarterRoom.find(level, terminal).whole(), "soul soil is the Nether's ground");

        BlockPos block = new BlockPos(2, 3, 2);
        helper.setBlock(block, Blocks.OAK_PLANKS);
        found = BarterRoom.find(level, terminal);
        check(helper, found.fault() == BarterRoom.Fault.SIZE && helper.absolutePos(block).equals(found.at()), "a block inside: " + found);
        helper.setBlock(block, Blocks.AIR);

        helper.setBlock(new BlockPos(1, 3, 1), Blocks.GLASS);
        helper.setBlock(new BlockPos(1, 3, 2), Blocks.GLASS);
        helper.setBlock(new BlockPos(2, 3, 1), Blocks.GLASS);
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.GLASS);
        found = BarterRoom.find(level, terminal);
        check(helper, found.fault() == BarterRoom.Fault.SIZE, "one block high is too low: " + found);
        helper.succeed();
    }

    /**
     * A Mob Bottle with a piglin in it lets the piglin out into the room and comes back empty. A 1x1 room takes three,
     * and a fourth bottle waits - still waiting three of the terminal's looks on. An empty bottle takes one out, and the
     * piglin carried off in it comes out of it unkept, to turn in the overworld as any piglin.
     */
    private static void piglinsInAndOut(GameTestHelper helper) {
        BlockPos fan = room(helper, 1, 1, 2);
        var terminal = terminal(helper, fan.east());
        var chest = helper.getBlockEntity(fan.east().below(), ChestBlockEntity.class);
        AABB inside = new AABB(helper.absolutePos(new BlockPos(1, 2, 1))).expandTowards(0, 1, 0);
        check(helper, terminal.canPlaceItem(BarterTerminalBlockEntity.BOTTLE, MobBottleItem.filled(EntityTypes.PIGLIN))
                && terminal.canPlaceItem(BarterTerminalBlockEntity.BOTTLE, new ItemStack(ModItems.MOB_BOTTLE.get()))
                && !terminal.canPlaceItem(BarterTerminalBlockEntity.BOTTLE, MobBottleItem.filled(EntityTypes.COW))
                && !terminal.canPlaceItem(BarterTerminalBlockEntity.GOLD, new ItemStack(Items.IRON_INGOT))
                && terminal.canPlaceItem(BarterTerminalBlockEntity.GOLD, new ItemStack(Items.GOLD_INGOT)),
                "piglins' bottles, empty bottles and gold go in; a cow's bottle and iron do not");
        int[] put = {0};
        helper.onEachTick(() -> {
            if (terminal.getItem(BarterTerminalBlockEntity.BOTTLE).isEmpty() && put[0] < 4) {
                terminal.setItem(BarterTerminalBlockEntity.BOTTLE, MobBottleItem.filled(EntityTypes.PIGLIN));
                put[0]++;
            }
        });
        ServerLevel level = helper.getLevel();
        helper.startSequence().thenWaitUntil(() -> {
            int piglins = level.getEntitiesOfClass(Piglin.class, inside).size();
            check(helper, put[0] == 4 && piglins == 3, "three let in, the fourth waiting: " + piglins + " in, " + put[0] + " bottles");
            check(helper, countIn(chest, ModItems.MOB_BOTTLE.get()) == 3, "three empty bottles came back");
        }).thenExecuteAfter(30, () -> {
            // the terminal looks at its bottle every ten ticks: three looks on, the fourth is still refused
            check(helper, level.getEntitiesOfClass(Piglin.class, inside).size() == 3, "still three");
            check(helper, MobBottleItem.captured(terminal.getItem(BarterTerminalBlockEntity.BOTTLE)) == EntityTypes.PIGLIN,
                    "the fourth bottle is still full");
            check(helper, chest.getItem(0).getMaxStackSize() > 1, "and the empty ones stack again");
            terminal.setItem(BarterTerminalBlockEntity.BOTTLE, new ItemStack(ModItems.MOB_BOTTLE.get())); // an empty one instead
        }).thenWaitUntil(() -> {
            check(helper, level.getEntitiesOfClass(Piglin.class, inside).size() == 2, "an empty bottle takes one out");
            check(helper, terminal.getItem(BarterTerminalBlockEntity.BOTTLE).isEmpty(), "and is used");
        }).thenWaitUntil(() -> check(helper, bottled(chest) != null, "the piglin, bottled, comes into the chest")).thenExecute(() -> {
            var out = MobBottleItem.letOut(level, bottled(chest), helper.absolutePos(new BlockPos(5, 1, 5)));
            check(helper, out instanceof AbstractPiglin piglin && !PiglinShelter.kept(piglin)
                    && !((AbstractPiglinAccessor) piglin).nookworks$isImmuneToZombification(), "let out elsewhere, it is not kept: " + out);
            if (out != null) {
                out.discard();
            }
        }).thenSucceed();
    }

    /**
     * A piglin in a room with air is kept: vanilla's flag set, so it does not turn in the overworld past its fifteen
     * seconds. The terminal broken, the flag goes back at the piglin's next look. (Carried off in a bottle:
     * {@link #piglinsInAndOut}.)
     */
    private static void piglinsKept(GameTestHelper helper) {
        BlockPos fan = room(helper, 1, 1, 2);
        BlockPos at = fan.east();
        var terminal = terminal(helper, at);
        Piglin piglin = helper.spawn(EntityTypes.PIGLIN, new BlockPos(1, 2, 1));
        piglin.setBaby(false);
        piglin.setPersistenceRequired();
        helper.runAfterDelay(420, () -> {
            check(helper, piglin.isAlive() && ((AbstractPiglinAccessor) piglin).nookworks$isImmuneToZombification()
                    && PiglinShelter.kept(piglin), "kept past its fifteen seconds in the overworld");
            check(helper, terminal.air() == BarterTerminalBlockEntity.AIR_TICKS && terminal.guiStatus() == MachineStatus.IDLE,
                    "the room full of air, waiting for gold: " + terminal.air() + ", " + terminal.guiStatus());
            check(helper, helper.getBlockState(fan).getValue(VentFanBlock.DRIVEN) && helper.getBlockState(fan).getValue(VentFanBlock.ON),
                    "its fan turning");
            helper.setBlock(at, Blocks.AIR);
        });
        helper.runAfterDelay(470, () -> {
            check(helper, !((AbstractPiglinAccessor) piglin).nookworks$isImmuneToZombification() && !PiglinShelter.kept(piglin),
                    "the terminal gone, the piglin is let go");
            check(helper, !helper.getBlockState(fan).getValue(VentFanBlock.DRIVEN), "and the fan stops");
            helper.succeed();
        });
    }

    /**
     * Gold put in is handed to the piglin, which admires it as from a player's hand and throws something for it; that is
     * drawn in from the room's floor and into the chest under the terminal.
     */
    private static void barters(GameTestHelper helper) {
        BlockPos fan = room(helper, 1, 1, 2);
        var terminal = terminal(helper, fan.east());
        var chest = helper.getBlockEntity(fan.east().below(), ChestBlockEntity.class);
        Piglin piglin = helper.spawn(EntityTypes.PIGLIN, new BlockPos(1, 2, 1));
        piglin.setBaby(false);
        piglin.setPersistenceRequired();
        terminal.setItem(BarterTerminalBlockEntity.GOLD, new ItemStack(Items.GOLD_INGOT, 2));
        helper.succeedWhen(() -> {
            int got = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                got += chest.getItem(i).isEmpty() ? 0 : 1;
            }
            check(helper, terminal.getItem(BarterTerminalBlockEntity.GOLD).getCount() < 2, "gold was handed over");
            check(helper, got > 0, "what the piglin gave for it is in the chest");
            check(helper, helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(helper.absolutePos(new BlockPos(1, 2, 1))).expandTowards(0, 1, 0)).isEmpty(), "and none left lying in the room");
        });
    }

    /**
     * A piglin's bottle clicked on the terminal, as the game clicks, goes into its bottle slot. (A second, clicked while
     * that one waits, opens the screen instead - which a test's player cannot be sent.)
     */
    private static void bottleByClick(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, ModBarter.BARTER_TERMINAL.get());
        var terminal = helper.getBlockEntity(pos, BarterTerminalBlockEntity.class);
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 aside = helper.absoluteVec(new Vec3(0.5, 8, 0.5));
        player.snapTo(aside.x, aside.y, aside.z);
        BlockPos at = helper.absolutePos(pos);
        ItemStack bottle = MobBottleItem.filled(EntityTypes.PIGLIN);
        player.setItemInHand(InteractionHand.MAIN_HAND, bottle);
        player.gameMode.useItemOn(player, helper.getLevel(), bottle, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.UP, 0.5), Direction.UP, at, false));
        check(helper, MobBottleItem.captured(terminal.getItem(BarterTerminalBlockEntity.BOTTLE)) == EntityTypes.PIGLIN
                && player.getMainHandItem().isEmpty(), "the bottle went in");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * With no container under the terminal, a bottled piglin it took out waits in its output slot - dropped, it would be
     * gone with the item in five minutes - while what the piglins throw back is dropped out, as a machine's output is.
     */
    private static void bottledPiglinWaits(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 2, 2);
        helper.setBlock(at, ModBarter.BARTER_TERMINAL.get());
        var terminal = helper.getBlockEntity(at, BarterTerminalBlockEntity.class);
        terminal.setItem(2, MobBottleItem.filled(EntityTypes.PIGLIN));
        terminal.setItem(3, new ItemStack(Items.QUARTZ, 3));
        helper.runAfterDelay(10, () -> {
            check(helper, MobBottleItem.captured(terminal.getItem(2)) == EntityTypes.PIGLIN, "the bottled piglin waits in its slot");
            check(helper, terminal.getItem(3).isEmpty(), "the quartz was dropped out");
            var dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(helper.absolutePos(at)).inflate(3));
            check(helper, dropped.stream().noneMatch(item -> item.getItem().is(ModItems.MOB_BOTTLE.get())), "no bottle on the ground");
            helper.succeed();
        });
    }

    /** The bottle with a piglin in it among the chest's, if there is one. */
    private static @Nullable ItemStack bottled(ChestBlockEntity chest) {
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (MobBottleItem.captured(chest.getItem(i)) == EntityTypes.PIGLIN) {
                return chest.getItem(i);
            }
        }
        return null;
    }

    private BarterTests() {}
}
