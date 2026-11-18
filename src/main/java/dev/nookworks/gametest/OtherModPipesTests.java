package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.SorterBlock;
import dev.nookworks.block.entity.CrateBlockEntity;
import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.machine.CrusherBlockEntity;
import dev.nookworks.machine.ElectricFurnaceBlockEntity;
import dev.nookworks.machine.GeneratorBlockEntity;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.port.test.GameTestHelper;
import dev.nookworks.port.transfer.ItemResource;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.sorter.SorterBlockEntity;
import dev.nookworks.storage.StorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Other mods' item pipes, fluid pipes and power cables on our blocks, with the real mods (Mekanism, EnderIO, Pipez), each
 * move counted before and after: the 1.21.1 port's bridges between NeoForge 21.1's handlers and ours
 * (dev.nookworks.port.transfer) must make and lose nothing. Each mod's set-up is first proved between its own or
 * vanilla's blocks (the "control" tests), then a block of ours is put at one end. The pipes are set as their own wrench
 * or screen sets them (Foreign: by registry id and reflection, no compile-time link), and each test is registered only
 * when its mod is loaded.
 *
 * <p>The line: the source at x=1, three pieces of pipe at x=2..4 (the first drawing from its west face), the target at
 * x=5, all at y=1, z=3.
 */
final class OtherModPipesTests {
    private OtherModPipesTests() {}

    static final BlockPos SOURCE = new BlockPos(1, 1, 3);
    static final BlockPos TARGET = new BlockPos(5, 1, 3);
    static final List<BlockPos> LINE = List.of(new BlockPos(2, 1, 3), new BlockPos(3, 1, 3), new BlockPos(4, 1, 3));

    /** One mod's pipe of one kind: how a piece is set down, and how a piece is set to draw from a neighbour. */
    record Kind(String mod, String what, Placer place, Puller pull, Puller push) {}

    private static final Puller AS_IT_IS = (helper, pos, side) -> {};

    interface Placer {
        void place(GameTestHelper helper, BlockPos pos);
    }

    interface Puller {
        void pull(GameTestHelper helper, BlockPos pos, Direction side);
    }

    // ---- the mods' pipes ----

    /** Mekanism's transmitters: a side's connection set as its Configurator sets it (NORMAL, PUSH, PULL, NONE). */
    static Kind mekanism(String what, String block) {
        return new Kind("mek", what, (helper, pos) -> helper.setBlock(pos, Foreign.block("mekanism:" + block)),
                (helper, pos, side) -> mekanismSide(helper, pos, side, "PULL"), AS_IT_IS);
    }

    static void mekanismSide(GameTestHelper helper, BlockPos pos, Direction side, String type) {
        Object transmitter = Foreign.call(Foreign.be(helper, pos), "getTransmitter");
        Foreign.call(transmitter, "setConnectionTypeRaw", side, Foreign.constant("mekanism.common.lib.transmitter.ConnectionType", type));
        Foreign.call(transmitter, "onModeChange", side);
        Foreign.call(transmitter, "refreshConnections");
    }

    /** Pipez: a side set to extract as its wrench sets it. */
    static Kind pipez(String what, String block) {
        return new Kind("pipez", what, (helper, pos) -> helper.setBlock(pos, Foreign.block("pipez:" + block)),
                (helper, pos, side) -> {
                    // a Pipez pipe has its block entity only once it has something to keep: its wrench sets has_data first
                    var hasData = (net.minecraft.world.level.block.state.properties.BooleanProperty) Foreign.staticField("de.maxhenkel.pipez.blocks.PipeBlock", "HAS_DATA");
                    helper.getLevel().setBlock(helper.absolutePos(pos), helper.getBlockState(pos).setValue(hasData, true), 3);
                    Foreign.call(Foreign.be(helper, pos), "setExtracting", side, true);
                }, AS_IT_IS);
    }

    /** EnderIO: a conduit bundle with one conduit, a side's connection set to extract only, as its screen sets it. */
    static Kind enderio(String what, String conduit) {
        return new Kind("eio", what, (helper, pos) -> {
            helper.setBlock(pos, Foreign.block("enderio:conduit"));
            Object result = Foreign.call(Foreign.be(helper, pos), "addConduit", conduit(helper, conduit), null, Foreign.fakePlayer(helper));
            check(helper, !result.getClass().getSimpleName().equals("Blocked"), "the " + conduit + " conduit went in: " + result);
        }, (helper, pos, side) -> {
            BlockEntity bundle = Foreign.be(helper, pos);
            Object holder = conduit(helper, conduit);
            Object config = Foreign.call(bundle, "getConnectionConfig", holder, side);
            check(helper, config != null && !config.getClass().getSimpleName().contains("Disabled"),
                    "the " + conduit + " conduit has joined its west neighbour: " + config);
            Object extract = Foreign.call(Foreign.call(config, "withIsExtract", true), "withIsInsert", false);
            // a new connection's extraction is NEVER_ACTIVE until set (EnderIO's default config)
            extract = Foreign.call(extract, "withExtractRedstoneControl", Foreign.constant("com.enderio.enderio.api.io.RedstoneControl", "ALWAYS_ACTIVE"));
            Foreign.call(bundle, "setConnectionConfig", holder, side, extract);
        }, (helper, pos, side) -> {
            // and a new item or fluid connection does not insert until set to (its screen's Insert box)
            BlockEntity bundle = Foreign.be(helper, pos);
            Object holder = conduit(helper, conduit);
            Object config = Foreign.call(bundle, "getConnectionConfig", holder, side);
            check(helper, config != null && !config.getClass().getSimpleName().contains("Disabled"),
                    "the " + conduit + " conduit has joined the block it is to fill: " + config);
            Foreign.call(bundle, "setConnectionConfig", holder, side, Foreign.call(Foreign.call(config, "withIsInsert", true), "withIsExtract", false));
        });
    }

    private static Object conduit(GameTestHelper helper, String id) {
        var key = net.minecraft.resources.ResourceKey.<Object>createRegistryKey(net.minecraft.resources.ResourceLocation.parse("enderio:conduit"));
        var registry = helper.getLevel().registryAccess().registryOrThrow(key);
        return registry.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(key, net.minecraft.resources.ResourceLocation.parse(id)));
    }

    static List<Kind> itemPipes() {
        List<Kind> kinds = new ArrayList<>();
        if (Foreign.loaded("mekanism")) {
            kinds.add(mekanism("items", "ultimate_logistical_transporter"));
        }
        if (Foreign.loaded("pipez")) {
            kinds.add(pipez("items", "item_pipe"));
        }
        if (Foreign.loaded("enderio")) {
            kinds.add(enderio("items", "enderio:item"));
        }
        return kinds;
    }

    static List<Kind> fluidPipes() {
        List<Kind> kinds = new ArrayList<>();
        if (Foreign.loaded("mekanism")) {
            kinds.add(mekanism("fluids", "ultimate_mechanical_pipe"));
        }
        if (Foreign.loaded("pipez")) {
            kinds.add(pipez("fluids", "fluid_pipe"));
        }
        if (Foreign.loaded("enderio")) {
            kinds.add(enderio("fluids", "enderio:fluid"));
        }
        return kinds;
    }

    static List<Kind> cables() {
        List<Kind> kinds = new ArrayList<>();
        if (Foreign.loaded("mekanism")) {
            kinds.add(mekanism("energy", "ultimate_universal_cable"));
        }
        if (Foreign.loaded("pipez")) {
            kinds.add(pipez("energy", "energy_pipe"));
        }
        if (Foreign.loaded("enderio")) {
            kinds.add(enderio("energy", "enderio:energy"));
        }
        return kinds;
    }

    /** Lays the line between the two ends already set down, and sets its first piece to draw from the source. */
    static void lay(GameTestHelper helper, Kind kind) {
        lay(helper, kind, LINE, Direction.WEST, Direction.EAST);
    }

    static void lay(GameTestHelper helper, Kind kind, List<BlockPos> line, Direction pullFrom, @Nullable Direction pushTo) {
        for (BlockPos pos : line) {
            kind.place().place(helper, pos);
        }
        for (BlockPos pos : line) {
            Foreign.reshape(helper, pos);
        }
        kind.pull().pull(helper, line.get(0), pullFrom);
        if (pushTo != null) {
            kind.push().pull(helper, line.get(line.size() - 1), pushTo);
        }
    }

    // ---- registration ----

    static void register() {
        NookworksGameTests.test("transfer_double_chest_half_tells_the_other", 60, OtherModPipesTests::doubleChestComparator);
        NookworksGameTests.test("crusher_dust_choice_is_by_name", 20, OtherModPipesTests::dustChoice);
        for (Kind kind : itemPipes()) {
            items(kind);
        }
        for (Kind kind : fluidPipes()) {
            fluids(kind);
        }
        for (Kind kind : cables()) {
            energy(kind);
        }
    }

    /**
     * A double chest, a comparator against its left half; one of our handlers puts items in, which go into the right half
     * first (vanilla's order): the comparator is told, as a hopper's would be (both halves are marked changed).
     */
    private static void doubleChestComparator(GameTestHelper helper) {
        BlockPos left = new BlockPos(2, 1, 3);
        BlockPos right = left.east();
        var chest = Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.NORTH);
        helper.setBlock(left, chest.setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.LEFT));
        helper.setBlock(right, chest.setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.RIGHT));
        BlockPos comparator = left.west();
        helper.setBlock(comparator, Blocks.COMPARATOR.defaultBlockState().setValue(net.minecraft.world.level.block.ComparatorBlock.FACING, Direction.EAST));
        helper.startSequence().thenExecuteAfter(4, () -> {
            check(helper, signal(helper, comparator) == 0, "an empty chest gives no signal: " + signal(helper, comparator));
            var handler = helper.getLevel().getCapability(dev.nookworks.port.transfer.Capabilities.Item.BLOCK, helper.absolutePos(left), null);
            try (var tx = dev.nookworks.port.transfer.Transaction.openRoot()) {
                int moved = handler.insert(ItemResource.of(Items.COBBLESTONE), 640, tx);
                check(helper, moved == 640, "640 went in: " + moved);
                tx.commit();
            }
            check(helper, countIn(chest(helper, right), Items.COBBLESTONE) == 640 && countIn(chest(helper, left), Items.COBBLESTONE) == 0,
                    "into the right half first, as vanilla fills a double chest");
        }).thenExecuteAfter(4, () -> check(helper, signal(helper, comparator) > 0,
                "the comparator on the left half is told: " + signal(helper, comparator))).thenSucceed();
    }

    private static int signal(GameTestHelper helper, BlockPos comparator) {
        return helper.getBlockEntity(comparator, net.minecraft.world.level.block.entity.ComparatorBlockEntity.class).getOutputSignal();
    }

    /**
     * The Crusher's tag results: whichever mods fill a dust tag, the same item every time, chosen by name (vanilla's,
     * ours, then the first id), not by the order mods loaded in; and raw metal grinds to one dust, no more than smelting
     * gives (Mekanism's Enrichment Chamber gives four for three raw, EnderIO's SAG Mill 1.8 - ours is never better).
     */
    private static void dustChoice(GameTestHelper helper) {
        for (String metal : List.of("iron", "gold", "copper", "sulfur")) {
            var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "dusts/" + metal));
            List<String> ids = new ArrayList<>();
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagOrEmpty(tag)
                    .forEach(holder -> ids.add(holder.unwrapKey().orElseThrow().location().toString()));
            var recipe = new dev.nookworks.machine.CrushingRecipe(new dev.nookworks.machine.CrushingRecipe.Result.OfTag(tag), 1);
            var chosen = recipe.resolve().map(holder -> holder.unwrapKey().orElseThrow().location().toString()).orElse(null);
            String expected = ids.stream().min(java.util.Comparator.comparingInt((String id) -> id.startsWith("minecraft:") ? 0
                    : id.startsWith("nookworks:") ? 1 : 2).thenComparing(id -> id)).orElse(null);
            check(helper, java.util.Objects.equals(chosen, expected), "c:dusts/" + metal + " holds " + ids + ": the Crusher gives " + chosen
                    + ", expected " + expected);
        }
        var raw = dev.nookworks.machine.CrushingRecipe.of(new ItemStack(Items.RAW_IRON));
        if (raw != null && raw.output().is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "dusts/iron")))) {
            check(helper, raw.output().getCount() == 1, "raw iron grinds to one dust: " + raw.output());
        }
        helper.succeed();
    }

    private static void test(Kind kind, String what, int ticks, Consumer<GameTestHelper> body) {
        NookworksGameTests.test("xmod_" + kind.mod() + "_" + kind.what() + "_" + what, ticks, body);
    }

    // ---- items ----

    private static void items(Kind kind) {
        test(kind, "control_chest_to_chest", 400, h -> intoTarget(h, kind, Blocks.CHEST, Items.COBBLESTONE, 16, 0));
        test(kind, "into_a_crate", 400, h -> intoTarget(h, kind, ModBlocks.CRATE.get(), Items.COBBLESTONE, 16, 0));
        test(kind, "into_a_magnet_chest", 400, h -> intoTarget(h, kind, ModBlocks.MAGNET_CHEST.get(), Items.COBBLESTONE, 16, 0));
        for (var barrel : barrels()) {
            String name = barrel.getId().getPath();
            test(kind, "into_a_" + name, 400, h -> intoTarget(h, kind, barrel.get(), Items.COBBLESTONE, 16, 1000));
            test(kind, "out_of_a_" + name, 400, h -> outOfBarrel(h, kind, barrel.get()));
        }
        test(kind, "into_a_trash_can", 400, h -> intoTrash(h, kind));
        test(kind, "into_an_item_sorter", 400, h -> intoSorter(h, kind));
        test(kind, "into_a_crusher_only_what_it_crushes", 400, h -> intoMachine(h, kind, ModBlocks.CRUSHER.get(), Items.COBBLESTONE, Items.DIRT));
        test(kind, "into_an_electric_furnace_only_what_it_smelts", 400, h -> intoMachine(h, kind, ModBlocks.ELECTRIC_FURNACE.get(), Items.RAW_IRON, Items.DIRT));
        test(kind, "into_a_sifter_only_what_it_sifts", 400, h -> intoMachine(h, kind, ModBlocks.SIFTER.get(), Items.GRAVEL, Items.NETHER_STAR));
        test(kind, "into_a_retort_only_what_it_takes", 400, h -> intoMachine(h, kind, ModBlocks.RETORT.get(), Items.OAK_LOG, Items.DIAMOND));
        test(kind, "into_a_printing_press_only_what_it_takes", 400, h -> intoMachine(h, kind, ModBlocks.PRINTING_PRESS.get(), dev.nookworks.registry.ModItems.FINE_PAPER.get(), Items.DIRT));
        test(kind, "into_a_planter_only_what_it_plants", 400, h -> intoMachine(h, kind, ModBlocks.PLANTER.get(), Items.WHEAT_SEEDS, Items.COBBLESTONE));
        test(kind, "out_of_a_crate_keeping_components", 400, h -> outOfCrate(h, kind));
        test(kind, "out_of_a_crusher_only_what_it_made", 400, h -> outOfMachine(h, kind, ModBlocks.CRUSHER.get(),
                CrusherBlockEntity.INPUT, Items.COBBLESTONE, CrusherBlockEntity.OUTPUT, Items.GRAVEL));
        test(kind, "out_of_an_electric_furnace_only_what_it_made", 400, h -> outOfMachine(h, kind, ModBlocks.ELECTRIC_FURNACE.get(),
                ElectricFurnaceBlockEntity.INPUT, Items.RAW_IRON, ElectricFurnaceBlockEntity.OUTPUT, Items.IRON_INGOT));
        test(kind, "out_of_a_generator_never_its_fuel", 200, h -> outOfGenerator(h, kind));
        test(kind, "far_end_full_loses_nothing", 300, h -> farEndFull(h, kind));
        test(kind, "broken_midway_loses_nothing", 400, h -> brokenMidway(h, kind));
    }

    static List<net.neoforged.neoforge.registries.DeferredBlock<?>> barrels() {
        return List.of(ModBlocks.IRON_STORAGE, ModBlocks.GOLD_STORAGE, ModBlocks.DIAMOND_STORAGE, ModBlocks.NETHERITE_STORAGE,
                ModBlocks.VELARIUM_STORAGE);
    }

    private static Container chest(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, ChestBlockEntity.class);
    }

    static int countIn(Container container, Item item) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).is(item)) {
                total += container.getItem(i).getCount();
            }
        }
        return total;
    }

    /** How many of {@code item} the block holds: a vanilla chest by its slots, one of ours by our own handler. */
    static long held(GameTestHelper helper, BlockPos pos, Item item) {
        if (helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof ChestBlockEntity chest) {
            return countIn(chest, item);
        }
        return CreateLogisticsTests.held(helper, pos, item);
    }

    static int loose(GameTestHelper helper, Item item) {
        AABB arena = new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(8, 10, 8).inflate(2);
        return helper.getLevel().getEntities(EntityType.ITEM, arena, e -> e.getItem().is(item)).stream()
                .mapToInt(e -> e.getItem().getCount()).sum();
    }

    /**
     * A chest of {@code count} items drawn by the pipe into the target (a Storage Barrel already holding {@code already}
     * of it): every one arrives, none is left in the chest or lying about, none made.
     */
    private static void intoTarget(GameTestHelper helper, Kind kind, Block target, Item item, int count, int already) {
        helper.setBlock(SOURCE, Blocks.CHEST);
        helper.setBlock(TARGET, target);
        if (already > 0) {
            StorageBlockEntity barrel = helper.getBlockEntity(TARGET, StorageBlockEntity.class);
            barrel.inventory().set(0, ItemResource.of(item), Math.min(already, (int) barrel.inventory().getCapacityAsLong(0, ItemResource.of(item))));
        }
        long before = held(helper, TARGET, item);
        chest(helper, SOURCE).setItem(0, new ItemStack(item, count));
        lay(helper, kind);
        helper.succeedWhen(() -> {
            long in = held(helper, TARGET, item);
            int left = countIn(chest(helper, SOURCE), item);
            check(helper, in == before + count && left == 0 && loose(helper, item) == 0,
                    kind.mod() + ": " + (in - before) + " of " + count + " arrived (" + before + " there before, " + in + " now), "
                            + left + " left in the chest, " + loose(helper, item) + " lying");
        });
    }

    /** A Trash Can takes what a pipe brings it and keeps nothing; nothing comes out of it. */
    private static void intoTrash(GameTestHelper helper, Kind kind) {
        helper.setBlock(SOURCE, Blocks.CHEST);
        helper.setBlock(TARGET.below(), Blocks.REDSTONE_BLOCK); // a Trash Can takes things while it is powered
        helper.setBlock(TARGET, ModBlocks.TRASH_CAN.get());
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.ROTTEN_FLESH, 8));
        lay(helper, kind);
        helper.succeedWhen(() -> check(helper, countIn(chest(helper, SOURCE), Items.ROTTEN_FLESH) == 0 && loose(helper, Items.ROTTEN_FLESH) == 0,
                kind.mod() + ": the trash can takes all: " + countIn(chest(helper, SOURCE), Items.ROTTEN_FLESH) + " left"));
    }

    /**
     * A pipe coming down onto an Item Sorter (facing north, diamonds registered): what it brings is sorted, the diamonds
     * to its right (west), the rest to its left (east), every one accounted for.
     */
    private static void intoSorter(GameTestHelper helper, Kind kind) {
        BlockPos sorter = new BlockPos(3, 1, 3);
        helper.setBlock(sorter, ModBlocks.ITEM_SORTER.get().defaultBlockState().setValue(SorterBlock.FACING, Direction.NORTH));
        helper.getBlockEntity(sorter, SorterBlockEntity.class).filter().setItem(0, new ItemStack(Items.DIAMOND));
        helper.setBlock(sorter.west(), Blocks.CHEST);
        helper.setBlock(sorter.east(), Blocks.CHEST);
        BlockPos source = new BlockPos(3, 5, 3);
        helper.setBlock(source, Blocks.CHEST);
        chest(helper, source).setItem(0, new ItemStack(Items.DIAMOND, 4));
        chest(helper, source).setItem(1, new ItemStack(Items.DIRT, 4));
        lay(helper, kind, List.of(new BlockPos(3, 4, 3), new BlockPos(3, 3, 3), new BlockPos(3, 2, 3)), Direction.UP, Direction.DOWN);
        helper.succeedWhen(() -> {
            int diamonds = countIn(chest(helper, sorter.west()), Items.DIAMOND);
            int dirt = countIn(chest(helper, sorter.east()), Items.DIRT);
            check(helper, diamonds == 4 && dirt == 4, kind.mod() + ": the sorter sorts what the pipe brings: " + diamonds + " diamonds right, "
                    + dirt + " dirt left");
        });
    }

    /** A chest of what the machine takes and what it does not: the one goes in, all of it; the other stays in the chest. */
    private static void intoMachine(GameTestHelper helper, Kind kind, Block machine, Item takes, Item refuses) {
        helper.setBlock(SOURCE, Blocks.CHEST);
        helper.setBlock(TARGET, machine);
        chest(helper, SOURCE).setItem(0, new ItemStack(takes, 8));
        chest(helper, SOURCE).setItem(1, new ItemStack(refuses, 8));
        lay(helper, kind);
        // the Sifter starts on gravel as it comes, a few at a time from a slow pipe: there it is what is gone from the chest
        boolean eats = machine == ModBlocks.SIFTER.get();
        helper.startSequence().thenWaitUntil(() -> {
            long in = eats ? 8 - countIn(chest(helper, SOURCE), takes) : held(helper, TARGET, takes);
            check(helper, in == 8, kind.mod() + ": " + in + " of 8 " + takes + " went in; "
                    + Foreign.describe(helper, TARGET, Direction.WEST, new ItemStack(takes, 8)));
        }).thenExecuteAfter(60, () -> {
            // (the Sifter starts on gravel by itself, so what it took is not counted again here)
            check(helper, countIn(chest(helper, SOURCE), takes) == 0 && loose(helper, takes) == 0, "none left behind or lying");
            check(helper, countIn(chest(helper, SOURCE), refuses) == 8 && held(helper, TARGET, refuses) == 0 && loose(helper, refuses) == 0,
                    kind.mod() + ": the " + refuses + " stays in the chest: " + countIn(chest(helper, SOURCE), refuses));
        }).thenSucceed();
    }

    /** A Storage Barrel holding far more than a stack: what the pipe draws out plus what is left is what was in. */
    private static void outOfBarrel(GameTestHelper helper, Kind kind, Block block) {
        helper.setBlock(SOURCE, block);
        helper.setBlock(TARGET, Blocks.CHEST);
        StorageBlockEntity barrel = helper.getBlockEntity(SOURCE, StorageBlockEntity.class);
        int perSlot = (int) barrel.inventory().getCapacityAsLong(0, ItemResource.of(Items.COBBLESTONE));
        barrel.inventory().set(0, ItemResource.of(Items.COBBLESTONE), perSlot);
        barrel.inventory().set(1, ItemResource.of(Items.COBBLESTONE), perSlot / 2 + 7);
        long total = perSlot + perSlot / 2 + 7;
        lay(helper, kind);
        helper.startSequence().thenWaitUntil(() -> check(helper, countIn(chest(helper, TARGET), Items.COBBLESTONE) >= 16,
                kind.mod() + ": the pipe draws from the barrel: " + countIn(chest(helper, TARGET), Items.COBBLESTONE)))
                .thenExecuteAfter(20, () -> {
                    long left = held(helper, SOURCE, Items.COBBLESTONE);
                    int out = countIn(chest(helper, TARGET), Items.COBBLESTONE);
                    long moving = inTransit(helper, kind);
                    check(helper, left + out + moving + loose(helper, Items.COBBLESTONE) == total,
                            kind.mod() + ": " + total + " in the barrel at first, now " + left + " in it, " + out + " in the chest, "
                                    + moving + " on the way, " + loose(helper, Items.COBBLESTONE) + " lying");
                }).thenSucceed();
    }

    /**
     * Items riding in a pipe at this moment: Mekanism's transporters carry them along block by block (their transit
     * stacks), the other two move them in one go.
     */
    static long inTransit(GameTestHelper helper, Kind kind) {
        if (!kind.mod().equals("mek")) {
            return 0;
        }
        long total = 0;
        for (BlockPos pos : LINE) {
            BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
            if (be == null) {
                continue;
            }
            Object transmitter = Foreign.call(be, "getTransmitter");
            for (Object stack : (java.util.Collection<?>) Foreign.call(transmitter, "getTransit")) {
                try {
                    ItemStack item = (ItemStack) stack.getClass().getField("itemStack").get(stack);
                    total += item.getCount();
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("a Mekanism transit stack's item", e);
                }
            }
        }
        return total;
    }

    /** Twenty diamonds and a worn, named, enchanted sword in a Crate: all come out, the sword exactly as it was. */
    private static void outOfCrate(GameTestHelper helper, Kind kind) {
        helper.setBlock(SOURCE, ModBlocks.CRATE.get());
        helper.setBlock(TARGET, Blocks.CHEST);
        CrateBlockEntity crate = helper.getBlockEntity(SOURCE, CrateBlockEntity.class);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(77);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Old Faithful"));
        sword.enchant(helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 3);
        ItemStack copy = sword.copy();
        crate.setItem(2, new ItemStack(Items.DIAMOND, 20));
        crate.setItem(5, sword);
        lay(helper, kind);
        helper.succeedWhen(() -> {
            Container out = chest(helper, TARGET);
            check(helper, countIn(out, Items.DIAMOND) == 20 && held(helper, SOURCE, Items.DIAMOND) == 0,
                    kind.mod() + ": the diamonds came out: " + countIn(out, Items.DIAMOND));
            boolean same = false;
            for (int i = 0; i < out.getContainerSize(); i++) {
                same |= ItemStack.isSameItemSameComponents(out.getItem(i), copy);
            }
            check(helper, same, kind.mod() + ": the sword came out with its wear, name and enchantment");
        });
    }

    /** A machine with what it works on and what it made: the pipe takes only what it made. */
    private static void outOfMachine(GameTestHelper helper, Kind kind, Block block, int inputSlot, Item input, int outputSlot, Item output) {
        helper.setBlock(SOURCE, block);
        helper.setBlock(TARGET, Blocks.CHEST);
        Container machine = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(SOURCE));
        // walled in, so the machine cannot drop what it made out of itself (it does, where there is room: MachineIO.dropOut)
        for (BlockPos wall : List.of(SOURCE.above(), SOURCE.west(), SOURCE.north(), SOURCE.south())) {
            helper.setBlock(wall, Blocks.STONE);
        }
        machine.setItem(inputSlot, new ItemStack(input, 10));
        machine.setItem(outputSlot, new ItemStack(output, 7));
        lay(helper, kind);
        helper.startSequence().thenWaitUntil(() -> check(helper, countIn(chest(helper, TARGET), output) == 7,
                kind.mod() + ": what it made came out: " + countIn(chest(helper, TARGET), output) + " in the chest, " + machine.getItem(outputSlot)
                        + " in the machine, " + machine.getItem(inputSlot) + " in its input, " + inTransit(helper, kind) + " on the way, "
                        + loose(helper, output) + " lying")).thenExecuteAfter(40, () ->
                check(helper, countIn(chest(helper, TARGET), input) == 0 && machine.getItem(inputSlot).getCount() == 10,
                        kind.mod() + ": what it works on stays: " + machine.getItem(inputSlot))).thenSucceed();
    }

    /** A Generator's coal is its fuel: no pipe draws it out. */
    private static void outOfGenerator(GameTestHelper helper, Kind kind) {
        helper.setBlock(SOURCE, ModBlocks.GENERATOR.get());
        helper.setBlock(TARGET, Blocks.CHEST);
        GeneratorBlockEntity generator = helper.getBlockEntity(SOURCE, GeneratorBlockEntity.class);
        generator.setItem(0, new ItemStack(Items.COAL, 8));
        lay(helper, kind);
        helper.runAfterDelay(120, () -> {
            check(helper, countIn(chest(helper, TARGET), Items.COAL) == 0 && loose(helper, Items.COAL) == 0,
                    kind.mod() + ": the coal stays in the generator: " + countIn(chest(helper, TARGET), Items.COAL) + " drawn out");
            helper.succeed();
        });
    }

    /** The far end full: the barrel keeps every one, nothing lies about. */
    private static void farEndFull(GameTestHelper helper, Kind kind) {
        helper.setBlock(SOURCE, ModBlocks.IRON_STORAGE.get());
        helper.setBlock(TARGET, Blocks.CHEST);
        StorageBlockEntity barrel = helper.getBlockEntity(SOURCE, StorageBlockEntity.class);
        barrel.inventory().set(0, ItemResource.of(Items.COBBLESTONE), 100);
        Container full = chest(helper, TARGET);
        for (int i = 0; i < full.getContainerSize(); i++) {
            full.setItem(i, new ItemStack(Items.STONE, 64));
        }
        lay(helper, kind);
        helper.runAfterDelay(160, () -> {
            long left = held(helper, SOURCE, Items.COBBLESTONE);
            long moving = inTransit(helper, kind);
            check(helper, left + moving == 100 && loose(helper, Items.COBBLESTONE) == 0 && countIn(full, Items.STONE) == 27 * 64,
                    kind.mod() + ": with nowhere to go " + left + " stay in the barrel, " + moving + " on the way, "
                            + loose(helper, Items.COBBLESTONE) + " lying");
            helper.succeed();
        });
    }

    /** The pipe broken in the middle while it is drawing: every item is in the barrel, the chest, the pipe or on the ground. */
    private static void brokenMidway(GameTestHelper helper, Kind kind) {
        helper.setBlock(SOURCE, ModBlocks.NETHERITE_STORAGE.get());
        helper.setBlock(TARGET, Blocks.CHEST);
        StorageBlockEntity barrel = helper.getBlockEntity(SOURCE, StorageBlockEntity.class);
        barrel.inventory().set(0, ItemResource.of(Items.COBBLESTONE), 2000);
        lay(helper, kind);
        helper.startSequence().thenWaitUntil(() -> check(helper, countIn(chest(helper, TARGET), Items.COBBLESTONE) > 0, "drawing"))
                .thenExecute(() -> helper.getLevel().destroyBlock(helper.absolutePos(LINE.get(1)), true))
                .thenExecuteAfter(80, () -> {
                    long left = held(helper, SOURCE, Items.COBBLESTONE);
                    int out = countIn(chest(helper, TARGET), Items.COBBLESTONE);
                    long moving = inTransit(helper, kind);
                    int lying = loose(helper, Items.COBBLESTONE);
                    check(helper, left + out + moving + lying == 2000, kind.mod() + ": 2000 at first, now " + left + " in the barrel, "
                            + out + " in the chest, " + moving + " in the pipe, " + lying + " lying");
                }).thenSucceed();
    }

    // ---- fluids ----

    /** A tank of the other mod's own (or another tank mod's) that takes any fluid, for both ends of a control. */
    static @Nullable Block foreignTank() {
        if (Foreign.loaded("mekanism")) {
            return Foreign.block("mekanism:ultimate_fluid_tank");
        }
        if (Foreign.loaded("enderio")) {
            return Foreign.block("enderio:pressurized_fluid_tank");
        }
        return null;
    }

    private static void fluids(Kind kind) {
        if (!Foreign.loaded("mekanism") && !Foreign.loaded("enderio")) {
            return; // no tank of another mod's to prove the pipe with
        }
        test(kind, "control_tank_to_tank", 300, h -> fluidInto(h, kind, foreignTank(), Fluids.WATER, 4_000, true));
        test(kind, "into_a_copper_tank", 300, h -> fluidInto(h, kind, ModBlocks.COPPER_TANK.get(), Fluids.WATER, 4_000, true));
        test(kind, "out_of_a_copper_tank", 300, h -> fluidOutOf(h, kind, ModBlocks.COPPER_TANK.get(), Fluids.WATER, 3_000, true));
        test(kind, "experience_into_an_experience_tank", 300, h -> fluidInto(h, kind, ModBlocks.EXPERIENCE_TANK.get(), ExperienceFluids.SOURCE.get(), 2_000, true));
        test(kind, "water_refused_by_an_experience_tank", 200, h -> fluidInto(h, kind, ModBlocks.EXPERIENCE_TANK.get(), Fluids.WATER, 2_000, false));
        test(kind, "out_of_an_experience_tank", 300, h -> fluidOutOf(h, kind, ModBlocks.EXPERIENCE_TANK.get(), ExperienceFluids.SOURCE.get(), 2_000, true));
        test(kind, "lava_refused_by_a_copper_tank", 200, h -> fluidInto(h, kind, ModBlocks.COPPER_TANK.get(), Fluids.LAVA, 2_000, false));
        test(kind, "biodiesel_into_a_diesel_generator", 300, h -> fluidInto(h, kind, ModBlocks.DIESEL_GENERATOR.get(), OilFluids.BIODIESEL.get(), 2_000, true));
        test(kind, "water_refused_by_a_diesel_generator", 200, h -> fluidInto(h, kind, ModBlocks.DIESEL_GENERATOR.get(), Fluids.WATER, 2_000, false));
        test(kind, "wood_vinegar_into_a_distiller", 300, h -> fluidInto(h, kind, ModBlocks.DISTILLER.get(), OilFluids.WOOD_VINEGAR.get(), 2_000, true));
        test(kind, "water_refused_by_a_distiller", 200, h -> fluidInto(h, kind, ModBlocks.DISTILLER.get(), Fluids.WATER, 2_000, false));
        test(kind, "wood_vinegar_out_of_a_retort", 300, h -> fluidOutOfRetort(h, kind));
    }

    /** {@code amount} of {@code fluid} in a tank of theirs, the pipe drawing it into the target: all of it, or none if refused. */
    private static void fluidInto(GameTestHelper helper, Kind kind, Block target, Fluid fluid, int amount, boolean taken) {
        Block tank = foreignTank();
        helper.setBlock(SOURCE, tank);
        helper.setBlock(TARGET, target);
        int filled = Foreign.fill(helper, SOURCE, new FluidStack(fluid, amount));
        check(helper, filled == amount, "their tank took the " + amount + " mB to start with: " + filled);
        if (helper.getLevel().getBlockEntity(helper.absolutePos(TARGET)) instanceof dev.nookworks.machine.DieselGeneratorBlockEntity engine) {
            engine.energy().set((int) engine.energy().getCapacityAsLong()); // full, it burns none of what comes in
        }
        int before = Foreign.fluid(helper, TARGET, null);
        lay(helper, kind);
        if (taken) {
            helper.succeedWhen(() -> {
                int in = Foreign.fluid(helper, TARGET, null) - before;
                int left = Foreign.fluid(helper, SOURCE, null);
                check(helper, in == amount && left == 0, kind.mod() + ": " + in + " of " + amount + " mB arrived, " + left + " left");
            });
        } else {
            helper.runAfterDelay(120, () -> {
                int left = Foreign.fluid(helper, SOURCE, null);
                int in = Foreign.fluid(helper, TARGET, null) - before;
                check(helper, in == 0, kind.mod() + ": the " + fluid + " is refused: " + in + " mB went in");
                check(helper, left + pipeFluid(helper) == amount, kind.mod() + ": none lost: " + left + " left, " + pipeFluid(helper) + " in the pipe");
                helper.succeed();
            });
        }
    }

    /** What the pipe itself holds, read from each piece's 21.1 fluid handler (zero for pipes that show none). */
    static int pipeFluid(GameTestHelper helper) {
        int total = 0;
        for (BlockPos pos : LINE) {
            total = Math.max(total, Foreign.fluid(helper, pos, null));
        }
        return total;
    }

    private static void fluidOutOf(GameTestHelper helper, Kind kind, Block source, Fluid fluid, int amount, boolean given) {
        helper.setBlock(SOURCE, source);
        helper.setBlock(TARGET, foreignTank());
        int filled = Foreign.fill(helper, SOURCE, new FluidStack(fluid, amount));
        check(helper, filled == amount, "ours took the " + amount + " mB to start with: " + filled);
        lay(helper, kind);
        helper.succeedWhen(() -> {
            int out = Foreign.fluid(helper, TARGET, null);
            int left = Foreign.fluid(helper, SOURCE, null);
            check(helper, out == amount && left == 0, kind.mod() + ": " + out + " of " + amount + " mB drawn out, " + left + " left");
        });
    }

    /** A Retort's wood vinegar (drain only) drawn out by their pipe, to the mB. */
    private static void fluidOutOfRetort(GameTestHelper helper, Kind kind) {
        helper.setBlock(SOURCE, ModBlocks.RETORT.get());
        helper.setBlock(TARGET, foreignTank());
        var retort = helper.getBlockEntity(SOURCE, dev.nookworks.mercury.RetortBlockEntity.class);
        retort.liquidTank().set(0, dev.nookworks.port.transfer.FluidResource.of(OilFluids.WOOD_VINEGAR.get()), 1_500);
        lay(helper, kind);
        helper.succeedWhen(() -> {
            int out = Foreign.fluid(helper, TARGET, null);
            check(helper, out == 1_500 && retort.liquidTank().getAmountAsInt(0) == 0,
                    kind.mod() + ": " + out + " of 1500 mB of wood vinegar drawn out, " + retort.liquidTank().getAmountAsInt(0) + " left");
        });
    }

    // ---- energy ----

    private static void energy(Kind kind) {
        test(kind, "out_of_a_generator_into_a_crusher", 300, h -> generatorToCrusher(h, kind));
        test(kind, "creative_generator_fills_a_crusher", 300, h -> creativeToCrusher(h, kind));
    }

    /**
     * A Generator holding 8,000 FE and no fuel, their cable drawing it to a Crusher with room for 10,000: every FE that
     * left the Generator is in the Crusher or the cable, none made (Mekanism's cable counts in joules: 2.5 a FE).
     */
    private static void generatorToCrusher(GameTestHelper helper, Kind kind) {
        helper.setBlock(SOURCE, ModBlocks.GENERATOR.get());
        helper.setBlock(TARGET, ModBlocks.CRUSHER.get());
        GeneratorBlockEntity generator = helper.getBlockEntity(SOURCE, GeneratorBlockEntity.class);
        generator.energy().set(8_000);
        CrusherBlockEntity crusher = helper.getBlockEntity(TARGET, CrusherBlockEntity.class);
        lay(helper, kind);
        int[] last = {-1};
        int[] still = {0};
        helper.succeedWhen(() -> {
            int now = crusher.energyStored();
            still[0] = now == last[0] ? still[0] + 1 : 0;
            last[0] = now;
            int cable = cableEnergy(helper);
            int sum = generator.energyStored() + now + cable;
            check(helper, sum <= 8_000, kind.mod() + ": no FE is made: generator " + generator.energyStored() + " + crusher " + now
                    + " + cable " + cable + " = " + sum);
            check(helper, generator.energyStored() == 0 && still[0] >= 20 && now >= 8_000 - 8,
                    kind.mod() + ": the generator's 8000 FE reached the crusher: " + now + " (generator " + generator.energyStored()
                            + ", cable " + cable + ")");
        });
    }

    static int cableEnergy(GameTestHelper helper) {
        var storage = Foreign.energy(helper, LINE.get(1), null);
        return storage == null ? 0 : storage.getEnergyStored();
    }

    /** The Creative Generator pushing into their cable: a Crusher at the far end is filled. */
    private static void creativeToCrusher(GameTestHelper helper, Kind kind) {
        helper.setBlock(SOURCE, ModBlocks.CREATIVE_GENERATOR.get());
        helper.setBlock(TARGET, ModBlocks.CRUSHER.get());
        CrusherBlockEntity crusher = helper.getBlockEntity(TARGET, CrusherBlockEntity.class);
        lay(helper, kind);
        helper.succeedWhen(() -> check(helper, crusher.energyStored() == CrusherBlockEntity.CAPACITY,
                kind.mod() + ": the crusher is filled: " + crusher.energyStored()));
    }
}
