package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.nookworks.block.PipeSides;
import dev.nookworks.fluid.CopperPipeBlockEntity;
import dev.nookworks.fluid.OneTank;
import dev.nookworks.machine.CrusherBlockEntity;
import dev.nookworks.machine.MachineIO;
import dev.nookworks.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import dev.nookworks.port.test.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import dev.nookworks.port.transfer.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import dev.nookworks.port.transfer.EnergyHandler;
import dev.nookworks.port.transfer.FluidResource;
import dev.nookworks.port.transfer.Transaction;
import dev.nookworks.port.transfer.TransactionContext;

/**
 * Energy Cables beside another mod's cables (found by the 1.21.1 port with the real mods, 2026-11-17: Pipez, EnderIO,
 * Mekanism; the same code is here), and Copper Pipes beside another mod's tank. Another mod's block is stood in by a
 * jukebox - a vanilla block with a block entity - given an energy or a fluid handler at the places a test names,
 * through providers only the tests register.
 */
final class OtherModCableTests {
    private OtherModCableTests() {}

    /** Where a jukebox passes power straight on: what is put in at one face goes into the block beyond the other. */
    private static final Set<BlockPos> RELAYS = ConcurrentHashMap.newKeySet();
    /** Where a jukebox takes power and keeps count of it: a store that has its handler from the moment it is named. */
    private static final Map<BlockPos, int[]> STORES = new ConcurrentHashMap<>();
    /** Where a jukebox holds a liquid: a tank that has its handler from the moment it is named. */
    private static final Map<BlockPos, OneTank> TANKS = new ConcurrentHashMap<>();

    static void register() {
        test("other_mod_cable_bridges_two_cable_networks", OtherModCableTests::bridge);
        test("other_mod_cable_looping_back_is_not_fed_twice", OtherModCableTests::loop);
        test("other_mod_handler_put_up_later_is_joined", 60, OtherModCableTests::joinedLater);
        test("other_mod_tank_put_up_later_is_joined_by_the_pipe", 160, OtherModCableTests::pipeJoinedLater);
    }

    /** The stand-ins' handlers, on the mod bus (called from NookworksGameTests while game tests are on). */
    static void listen(IEventBus modEventBus) {
        modEventBus.addListener(RegisterCapabilitiesEvent.class, event -> event.registerBlock(Capabilities.Energy.BLOCK,
                (level, pos, state, be, side) -> {
                    if (side != null && RELAYS.contains(pos) && level instanceof ServerLevel server) {
                        return new Relay(server, pos.relative(side.getOpposite()), side);
                    }
                    int[] store = STORES.get(pos);
                    return store == null ? null : new Store(store);
                }, Blocks.JUKEBOX));
        modEventBus.addListener(RegisterCapabilitiesEvent.class, event -> event.registerBlock(Capabilities.Fluid.BLOCK,
                (level, pos, state, be, side) -> TANKS.get(pos), Blocks.JUKEBOX));
    }

    /** Passes on what it is given, into whatever stands at {@code beyond}, asked through its face this way. */
    private record Relay(ServerLevel level, BlockPos beyond, Direction from) implements EnergyHandler {
        private EnergyHandler far() {
            return this.level.getCapability(Capabilities.Energy.BLOCK, this.beyond, this.from);
        }

        @Override
        public long getAmountAsLong() {
            return 0;
        }

        @Override
        public long getCapacityAsLong() {
            return 1000; // room told whatever is beyond, as a cable of another mod tells of its own rate
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            EnergyHandler far = this.far();
            return far == null ? 0 : far.insert(amount, transaction);
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return 0;
        }
    }

    /** Takes what it is given and counts it. */
    private record Store(int[] held) implements EnergyHandler {
        @Override
        public long getAmountAsLong() {
            return this.held[0];
        }

        @Override
        public long getCapacityAsLong() {
            return 1_000_000;
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            this.held[0] += amount;
            return amount;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return 0;
        }
    }

    private static int put(ServerLevel level, BlockPos cable, Direction face, int amount) {
        EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, cable, face);
        if (handler == null) {
            return -1;
        }
        try (var tx = Transaction.openRoot()) {
            int accepted = handler.insert(amount, tx);
            tx.commit();
            return accepted;
        }
    }

    /**
     * Two networks of ours with another mod's pass-through cable between them: power put into the first comes out of
     * the second. One flag for every network refused the second while the first was sending, and nothing passed.
     */
    private static void bridge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos first = new BlockPos(1, 2, 2);
        BlockPos relay = first.east();
        BlockPos second = relay.east();
        BlockPos crusher = second.east();
        // a cable joins what is set down beside it: the cables first, then the machine and the other mod's cable
        helper.setBlock(second, ModBlocks.ENERGY_CABLE.get());
        helper.setBlock(first, ModBlocks.ENERGY_CABLE.get());
        helper.setBlock(crusher, ModBlocks.CRUSHER.get());
        RELAYS.add(helper.absolutePos(relay));
        helper.setBlock(relay, Blocks.JUKEBOX);
        BlockState firstState = helper.getBlockState(first);
        check(helper, PipeSides.joined(firstState, Direction.EAST) && PipeSides.joined(helper.getBlockState(second), Direction.WEST),
                "both cables join the other mod's cable between them");
        int accepted = put(level, helper.absolutePos(first), Direction.WEST, 120);
        int stored = helper.getBlockEntity(crusher, CrusherBlockEntity.class).energyStored();
        RELAYS.remove(helper.absolutePos(relay));
        check(helper, accepted == 120 && stored == 120, "120 FE put into the first network reaches the machine on the second, through "
                + "the other mod's cable: accepted " + accepted + ", stored " + stored);
        helper.succeed();
    }

    /**
     * Another mod's pass-through cable whose far end is the same network of ours: what the network sends into it is not
     * taken back in (it would go round for ever, or be counted twice); the machine on the network gets its power once.
     */
    private static void loop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // a row of three cables, the relay under the middle one's neighbour, its far end the row's last cable:
        //   cable a - cable b - cable c - crusher        relay stands south of a, and passes on into... a's own row
        BlockPos a = new BlockPos(1, 2, 2);
        BlockPos b = a.east();
        BlockPos crusher = b.east();
        BlockPos relay = a.south();        // asked from the north (cable a), it passes on to the south
        BlockPos back = relay.south();     // a cable of the same network, joined round by c and d
        BlockPos c = back.east();
        BlockPos d = c.north();            // d is south of b: joins b, closing the ring a-b-d-c-back
        for (BlockPos cable : new BlockPos[] {a, b, d, c, back}) {
            helper.setBlock(cable, ModBlocks.ENERGY_CABLE.get());
        }
        helper.setBlock(crusher, ModBlocks.CRUSHER.get());
        RELAYS.add(helper.absolutePos(relay));
        helper.setBlock(relay, Blocks.JUKEBOX);
        check(helper, PipeSides.joined(helper.getBlockState(a), Direction.SOUTH) && PipeSides.joined(helper.getBlockState(back), Direction.NORTH),
                "the ring's cables join the other mod's cable on both its ends");
        int accepted = put(level, helper.absolutePos(a), Direction.WEST, 90);
        int stored = helper.getBlockEntity(crusher, CrusherBlockEntity.class).energyStored();
        RELAYS.remove(helper.absolutePos(relay));
        check(helper, accepted == 90 && stored == 90, "90 FE put into a network with another mod's cable looping back into it "
                + "reaches its machine once: accepted " + accepted + ", stored " + stored);
        helper.succeed();
    }

    /**
     * A block of another mod's that puts its energy handler up later, with no block changed (a pipe set to extract by
     * its wrench, a conduit added to a bundle): the cable beside it joins it when its network is next walked, and power
     * goes in. It stayed unjoined for good. A block with no handler is left unjoined, however often the network is walked.
     */
    private static void joinedLater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cable = new BlockPos(2, 2, 2);
        BlockPos later = cable.east();
        BlockPos never = cable.west();
        helper.setBlock(later, Blocks.JUKEBOX);
        helper.setBlock(never, Blocks.JUKEBOX);
        helper.setBlock(cable, ModBlocks.ENERGY_CABLE.get());
        BlockPos cableAt = helper.absolutePos(cable);
        BlockPos laterAt = helper.absolutePos(later);
        check(helper, !PipeSides.joined(helper.getBlockState(cable), Direction.EAST), "a block with no energy handler is not joined");
        int[] held = new int[1];
        helper.runAfterDelay(5, () -> {
            MachineIO.networkChanged(level, cableAt);
            MachineIO.receivers(level, cableAt); // the network walked with nothing new beside it
        });
        helper.runAfterDelay(10, () -> {
            check(helper, !PipeSides.joined(helper.getBlockState(cable), Direction.EAST) && !PipeSides.joined(helper.getBlockState(cable), Direction.WEST),
                    "walked with no handler beside it, the cable joins nothing");
            STORES.put(laterAt, held);              // the other mod's block puts its handler up: no block changes
            level.invalidateCapabilities(laterAt);
            MachineIO.networkChanged(level, cableAt);
            MachineIO.receivers(level, cableAt);    // ...and the network is walked again, as it is when next used
        });
        helper.runAfterDelay(20, () -> {
            boolean joined = PipeSides.joined(helper.getBlockState(cable), Direction.EAST);
            boolean other = PipeSides.joined(helper.getBlockState(cable), Direction.WEST);
            int accepted = put(level, cableAt, Direction.UP, 70);
            STORES.remove(laterAt);
            check(helper, joined && !other && accepted == 70 && held[0] == 70, "a handler put up later is joined by the cable beside it and "
                    + "takes power: joined " + joined + ", the block with none joined " + other + ", accepted " + accepted + ", held " + held[0]);
            helper.succeed();
        });
    }

    /**
     * The same of a Copper Pipe: a block of another mod's that puts its fluid handler up later, with no block changed,
     * is joined by the pipe beside it within a couple of seconds, and the pipe's water goes into it. A pipe looked at
     * what stood beside it only when it was set down and when a neighbour's block changed. A block with no handler is
     * left unjoined.
     */
    private static void pipeJoinedLater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pipe = new BlockPos(2, 2, 2);
        BlockPos later = pipe.east();
        BlockPos never = pipe.west();
        helper.setBlock(later, Blocks.JUKEBOX);
        helper.setBlock(never, Blocks.JUKEBOX);
        helper.setBlock(pipe, ModBlocks.COPPER_PIPE.get());
        BlockPos laterAt = helper.absolutePos(later);
        OneTank tank = new OneTank(4000);
        helper.runAfterDelay(50, () -> {
            // looked at more than once by now, with nothing to join
            check(helper, !PipeSides.joined(helper.getBlockState(pipe), Direction.EAST) && !PipeSides.joined(helper.getBlockState(pipe), Direction.WEST),
                    "a pipe beside blocks with no fluid handler joins neither");
            TANKS.put(laterAt, tank);               // the other mod's block puts its handler up: no block changes
            level.invalidateCapabilities(laterAt);
            try (var tx = Transaction.openRoot()) {
                int in = helper.getBlockEntity(pipe, CopperPipeBlockEntity.class).handlerFor(null).insert(FluidResource.of(Fluids.WATER), 500, tx);
                tx.commit();
                check(helper, in == 500, "500 mB of water put into the pipe: " + in);
            }
        });
        helper.runAfterDelay(110, () -> {
            boolean joined = PipeSides.joined(helper.getBlockState(pipe), Direction.EAST);
            boolean other = PipeSides.joined(helper.getBlockState(pipe), Direction.WEST);
            int held = tank.getAmountAsInt(0);
            TANKS.remove(laterAt);
            check(helper, joined && !other && held == 500, "a tank put up later is joined by the pipe beside it and takes its water: joined "
                    + joined + ", the block with none joined " + other + ", held " + held);
            helper.succeed();
        });
    }
}
