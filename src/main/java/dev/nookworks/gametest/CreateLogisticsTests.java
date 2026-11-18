package dev.nookworks.gametest;

import static dev.nookworks.gametest.CreateTests.createBlock;
import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.List;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;

import dev.nookworks.fluid.CopperTankBlockEntity;
import dev.nookworks.machine.CrusherBlockEntity;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.port.test.GameTestHelper;
import dev.nookworks.port.transfer.Capabilities;
import dev.nookworks.port.transfer.FluidResource;
import dev.nookworks.port.transfer.ItemResource;
import dev.nookworks.port.transfer.ResourceHandler;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import dev.nookworks.storage.StorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Create's logistics and fluids with our blocks, each move counted before and after (the port's bridges between
 * NeoForge 21.1's item and fluid handlers and our transfer API: nothing may be made or lost on the way). Registered only
 * with Create loaded.
 */
final class CreateLogisticsTests {
    private CreateLogisticsTests() {}

    static void register() {
        NookworksGameTests.test("create_logi_funnel_feeds_a_storage_barrel", 100, h -> funnelInto(h, ModBlocks.IRON_STORAGE.get(), Items.COBBLESTONE, 40));
        NookworksGameTests.test("create_logi_funnel_feeds_a_crate", 100, h -> funnelInto(h, ModBlocks.CRATE.get(), Items.COBBLESTONE, 40));
        NookworksGameTests.test("create_logi_funnel_feeds_a_crusher", 100, h -> funnelInto(h, ModBlocks.CRUSHER.get(), Items.COBBLESTONE, 40));
        NookworksGameTests.test("create_logi_funnel_feeds_an_electric_furnace", 100, h -> funnelInto(h, ModBlocks.ELECTRIC_FURNACE.get(), Items.RAW_IRON, 16));
        NookworksGameTests.test("create_logi_funnel_feeds_an_oil_press", 100, h -> funnelInto(h, ModOil.OIL_PRESS.get(), ModOil.SUNFLOWER_SEEDS.get(), 16));
        NookworksGameTests.test("create_logi_funnel_feeds_an_iron_pot", 100, h -> funnelInto(h, ModCooking.IRON_POT.get(), Items.POTATO, 1));
        NookworksGameTests.test("create_logi_funnel_takes_from_a_storage_barrel", 100, CreateLogisticsTests::funnelOutOfBarrel);
        NookworksGameTests.test("create_logi_funnel_takes_from_a_crate", 100, CreateLogisticsTests::funnelOutOfCrate);
        NookworksGameTests.test("create_logi_funnel_takes_only_what_a_crusher_made", 100, CreateLogisticsTests::funnelOutOfCrusher);
        NookworksGameTests.test("create_logi_brass_funnel_takes_its_count_from_a_big_slot", 100, CreateLogisticsTests::brassFunnelCount);
        NookworksGameTests.test("create_logi_chute_takes_a_crushers_output_into_a_barrel", 200, CreateLogisticsTests::chuteFromCrusher);
        NookworksGameTests.test("create_logi_sorter_sorts_into_an_item_vault", 100, CreateLogisticsTests::sorterIntoVault);
        NookworksGameTests.test("create_logi_threshold_switch_reads_a_storage_barrel", 100, CreateLogisticsTests::thresholdBarrel);
        NookworksGameTests.test("create_logi_threshold_switch_reads_a_copper_tank", 100, CreateLogisticsTests::thresholdTank);
        NookworksGameTests.test("create_logi_copper_tank_pours_into_a_create_tank", 200, CreateLogisticsTests::copperIntoCreateTank);
        NookworksGameTests.test("create_logi_pump_fills_a_copper_tank_from_a_create_tank", 200, h -> pump(h, true));
        NookworksGameTests.test("create_logi_pump_empties_a_copper_tank_into_a_create_tank", 200, h -> pump(h, false));
        NookworksGameTests.test("create_logi_item_drain_empties_our_buckets_not_mercury", 100, CreateLogisticsTests::drainBuckets);
        NookworksGameTests.test("create_logi_open_pipe_leaves_mercury_alone", 160, CreateLogisticsTests::mercuryPool);
        NookworksGameTests.test("create_logi_display_link_reads_our_tank", 60, CreateLogisticsTests::displayLink);
        NookworksGameTests.test("create_logi_copper_pipe_draws_a_create_tank", 200, CreateLogisticsTests::pipeDrawsCreateTank);
        NookworksGameTests.test("create_logi_copper_tank_pours_into_a_basin", 200, CreateLogisticsTests::copperIntoBasin);
        NookworksGameTests.test("create_logi_deployer_sets_down_our_crate", 200, h -> deployerPlaces(h, ModBlocks.CRATE.get()));
        NookworksGameTests.test("create_logi_deployer_sets_down_cobblestone", 200, h -> deployerPlaces(h, net.minecraft.world.level.block.Blocks.COBBLESTONE));
    }

    // ---- counting ----

    /** How many of {@code item} the block at {@code pos} holds, by our own handler (every slot, from no side). */
    static int held(GameTestHelper helper, BlockPos pos, Item item) {
        ResourceHandler<ItemResource> handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(pos), null);
        int total = 0;
        if (handler != null) {
            for (int i = 0; i < handler.size(); i++) {
                if (handler.getResource(i).is(item)) {
                    total += handler.getAmountAsInt(i);
                }
            }
        }
        return total;
    }

    /** How many of {@code item} lie loose in the arena. */
    static int loose(GameTestHelper helper, Item item) {
        AABB arena = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(0, 0, 0).expandTowards(8, 10, 8).inflate(1);
        return helper.getLevel().getEntities(EntityType.ITEM, arena, e -> e.getItem().is(item)).stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    static void drop(GameTestHelper helper, BlockPos at, ItemStack stack) {
        var pos = helper.absoluteVec(net.minecraft.world.phys.Vec3.atCenterOf(at));
        ItemEntity item = new ItemEntity(helper.getLevel(), pos.x, pos.y, pos.z, stack);
        item.setDeltaMovement(0, 0, 0);
        helper.getLevel().addFreshEntity(item);
    }

    static void funnel(GameTestHelper helper, BlockPos pos, Direction facing, boolean extracting, boolean brass) {
        BlockState state = createBlock(brass ? "brass_funnel" : "andesite_funnel").defaultBlockState()
                .setValue(com.simibubi.create.content.logistics.funnel.AbstractDirectionalFunnelBlock.FACING, facing)
                .setValue(FunnelBlock.EXTRACTING, extracting);
        helper.setBlock(pos, state);
    }

    // ---- items ----

    /**
     * An andesite funnel on top of the block, facing up, and {@code count} items dropped onto it: all go in, none is
     * left lying, none made.
     */
    private static void funnelInto(GameTestHelper helper, Block block, Item item, int count) {
        BlockPos target = new BlockPos(3, 1, 3);
        helper.setBlock(target, block);
        funnel(helper, target.above(), Direction.UP, false, false);
        helper.runAfterDelay(5, () -> drop(helper, target.above(), new ItemStack(item, count)));
        helper.succeedWhen(() -> {
            int in = held(helper, target, item);
            int left = loose(helper, item);
            check(helper, in == count && left == 0, "the funnel put " + in + " of " + count + " in, " + left + " left lying");
        });
    }

    /**
     * An extracting andesite funnel on a Storage Barrel's side (two slots of 100): one at a time, as Create's andesite
     * funnel takes, the next once the last is picked up; every one accounted for.
     */
    private static void funnelOutOfBarrel(GameTestHelper helper) {
        BlockPos target = new BlockPos(3, 1, 3);
        helper.setBlock(target, ModBlocks.IRON_STORAGE.get());
        StorageBlockEntity barrel = helper.getBlockEntity(target, StorageBlockEntity.class);
        barrel.inventory().set(0, ItemResource.of(Items.COBBLESTONE), 100);
        barrel.inventory().set(1, ItemResource.of(Items.COBBLESTONE), 100);
        funnel(helper, target.east(), Direction.EAST, true, false);
        helper.startSequence().thenWaitUntil(() -> {
            int in = held(helper, target, Items.COBBLESTONE);
            int out = loose(helper, Items.COBBLESTONE);
            check(helper, out == 1 && in == 199, "one out, 199 kept: " + out + " out, " + in + " kept");
        }).thenExecute(() -> helper.getLevel().getEntities(EntityType.ITEM, new AABB(helper.absolutePos(target)).inflate(3), e -> true)
                .forEach(net.minecraft.world.entity.Entity::discard)).thenWaitUntil(() -> {
            int in = held(helper, target, Items.COBBLESTONE);
            int out = loose(helper, Items.COBBLESTONE);
            check(helper, out == 1 && in == 198, "picked up, the next: " + out + " out, " + in + " kept");
        }).thenSucceed();
    }

    private static void funnelOutOfCrate(GameTestHelper helper) {
        BlockPos target = new BlockPos(3, 1, 3);
        helper.setBlock(target, ModBlocks.CRATE.get());
        var crate = helper.getBlockEntity(target, dev.nookworks.block.entity.CrateBlockEntity.class);
        crate.setItem(4, new ItemStack(Items.DIAMOND, 20));
        funnel(helper, target.east(), Direction.EAST, true, false);
        helper.succeedWhen(() -> check(helper, loose(helper, Items.DIAMOND) == 1 && held(helper, target, Items.DIAMOND) == 19,
                "one out of the crate, 19 kept: " + loose(helper, Items.DIAMOND) + ", " + held(helper, target, Items.DIAMOND)));
    }

    /** A Crusher holding cobblestone to crush and gravel it made: the funnel takes the gravel, never the cobblestone. */
    private static void funnelOutOfCrusher(GameTestHelper helper) {
        BlockPos target = new BlockPos(3, 1, 3);
        helper.setBlock(target, ModBlocks.CRUSHER.get());
        CrusherBlockEntity crusher = helper.getBlockEntity(target, CrusherBlockEntity.class);
        crusher.setItem(CrusherBlockEntity.INPUT, new ItemStack(Items.COBBLESTONE, 10));
        crusher.setItem(CrusherBlockEntity.OUTPUT, new ItemStack(Items.GRAVEL, 7));
        funnel(helper, target.east(), Direction.EAST, true, false);
        helper.startSequence().thenExecuteAfter(40, () -> {
            check(helper, loose(helper, Items.GRAVEL) == 7 && held(helper, target, Items.GRAVEL) == 0, "the gravel came out: " + loose(helper, Items.GRAVEL));
            check(helper, loose(helper, Items.COBBLESTONE) == 0 && held(helper, target, Items.COBBLESTONE) == 10,
                    "the cobblestone stays to be crushed: " + held(helper, target, Items.COBBLESTONE));
        }).thenSucceed();
    }

    /** A brass funnel set to 10 diamonds on a Netherite Storage Barrel's slot of 1000: exactly 10 come out. */
    private static void brassFunnelCount(GameTestHelper helper) {
        BlockPos target = new BlockPos(3, 1, 3);
        helper.setBlock(target, ModBlocks.NETHERITE_STORAGE.get());
        StorageBlockEntity barrel = helper.getBlockEntity(target, StorageBlockEntity.class);
        barrel.inventory().set(0, ItemResource.of(Items.DIAMOND), 1000);
        funnel(helper, target.east(), Direction.EAST, true, true);
        FilteringBehaviour filter = BlockEntityBehaviour.get(helper.getLevel(), helper.absolutePos(target.east()), FilteringBehaviour.TYPE);
        filter.setFilter(new ItemStack(Items.DIAMOND));
        filter.count = 10;
        filter.upTo = false;
        helper.succeedWhen(() -> {
            List<ItemEntity> out = helper.getLevel().getEntities(EntityType.ITEM, new AABB(helper.absolutePos(target)).inflate(3),
                    e -> e.getItem().is(Items.DIAMOND));
            check(helper, out.size() == 1 && out.get(0).getItem().getCount() == 10 && held(helper, target, Items.DIAMOND) == 990,
                    "ten out, 990 kept: " + out + ", " + held(helper, target, Items.DIAMOND));
        });
    }

    /** Crusher above a Chute above a Storage Barrel: the Crusher's gravel goes down into the barrel, its cobblestone stays. */
    private static void chuteFromCrusher(GameTestHelper helper) {
        BlockPos crusherPos = new BlockPos(3, 3, 3);
        BlockPos barrelPos = new BlockPos(3, 1, 3);
        helper.setBlock(barrelPos, ModBlocks.IRON_STORAGE.get());
        helper.setBlock(barrelPos.above(), createBlock("chute"));
        helper.setBlock(crusherPos, ModBlocks.CRUSHER.get());
        CrusherBlockEntity crusher = helper.getBlockEntity(crusherPos, CrusherBlockEntity.class);
        crusher.setItem(CrusherBlockEntity.INPUT, new ItemStack(Items.STONE, 10));
        crusher.setItem(CrusherBlockEntity.OUTPUT, new ItemStack(Items.GRAVEL, 20));
        helper.succeedWhen(() -> {
            check(helper, held(helper, barrelPos, Items.GRAVEL) == 20, "the gravel went down: " + held(helper, barrelPos, Items.GRAVEL));
            check(helper, held(helper, crusherPos, Items.STONE) == 10 && held(helper, barrelPos, Items.STONE) == 0, "the stone stayed");
        });
    }

    /** An Item Sorter with diamonds registered, an Item Vault on its registered side: the diamonds go into the vault. */
    private static void sorterIntoVault(GameTestHelper helper) {
        BlockPos sorterPos = new BlockPos(3, 1, 3);
        helper.setBlock(sorterPos, ModBlocks.ITEM_SORTER.get().defaultBlockState()
                .setValue(dev.nookworks.block.SorterBlock.FACING, Direction.NORTH).setValue(dev.nookworks.block.SorterBlock.SWAPPED, false));
        var sorter = helper.getBlockEntity(sorterPos, dev.nookworks.sorter.SorterBlockEntity.class);
        sorter.filter().setItem(0, new ItemStack(Items.DIAMOND));
        helper.setBlock(sorterPos.west(), createBlock("item_vault"));
        sorter.setItem(0, new ItemStack(Items.DIAMOND, 5));
        helper.succeedWhen(() -> {
            IItemHandler vault = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                    helper.absolutePos(sorterPos.west()), null);
            int in = 0;
            for (int i = 0; vault != null && i < vault.getSlots(); i++) {
                in += vault.getStackInSlot(i).is(Items.DIAMOND) ? vault.getStackInSlot(i).getCount() : 0;
            }
            check(helper, in == 5, "five diamonds in the vault: " + in);
        });
    }

    private static ThresholdSwitchBlockEntity thresholdSwitch(GameTestHelper helper, BlockPos pos, Direction toTarget) {
        helper.setBlock(pos, createBlock("stockpile_switch").defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, toTarget));
        return helper.getBlockEntity(pos, ThresholdSwitchBlockEntity.class);
    }

    /** A Threshold Switch on an Iron Storage Barrel (27 slots of 128 cobblestone) holding 200: it reads 200 of 3456. */
    private static void thresholdBarrel(GameTestHelper helper) {
        BlockPos target = new BlockPos(3, 1, 3);
        helper.setBlock(target, ModBlocks.IRON_STORAGE.get());
        StorageBlockEntity barrel = helper.getBlockEntity(target, StorageBlockEntity.class);
        barrel.inventory().set(0, ItemResource.of(Items.COBBLESTONE), 128);
        barrel.inventory().set(1, ItemResource.of(Items.COBBLESTONE), 72);
        ThresholdSwitchBlockEntity reader = thresholdSwitch(helper, target.east(), Direction.WEST);
        helper.succeedWhen(() -> {
            reader.updateCurrentLevel();
            check(helper, reader.getStockLevel() == 200 && reader.getMaxLevel() == 27 * 128,
                    "200 of " + 27 * 128 + ": " + reader.getStockLevel() + " of " + reader.getMaxLevel());
        });
    }

    /** A Threshold Switch on a Copper Tank holding 4000 mB of oil: it reads 4000 of 16000. */
    private static void thresholdTank(GameTestHelper helper) {
        BlockPos target = new BlockPos(3, 1, 3);
        helper.setBlock(target, ModBlocks.COPPER_TANK.get());
        CopperTankBlockEntity tank = helper.getBlockEntity(target, CopperTankBlockEntity.class);
        tank.fluidHandler().set(0, FluidResource.of(OilFluids.SOURCE.get()), 4_000);
        ThresholdSwitchBlockEntity reader = thresholdSwitch(helper, target.east(), Direction.WEST);
        helper.succeedWhen(() -> {
            reader.updateCurrentLevel();
            check(helper, reader.getStockLevel() == 4_000 && reader.getMaxLevel() == CopperTankBlockEntity.CAPACITY,
                    "4000 of 16000: " + reader.getStockLevel() + " of " + reader.getMaxLevel());
        });
    }

    // ---- fluids ----

    private static FluidTankBlockEntity createTank(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, createBlock("fluid_tank"));
        return helper.getBlockEntity(pos, FluidTankBlockEntity.class);
    }

    private static int amount(FluidTankBlockEntity tank, Fluid fluid) {
        FluidStack in = tank.getControllerBE().getTankInventory().getFluid();
        return in.getFluid().isSame(fluid) ? in.getAmount() : 0;
    }

    /** A Copper Tank of 4000 mB of oil over a Create Fluid Tank: it pours down into it, all of it, none made. */
    private static void copperIntoCreateTank(GameTestHelper helper) {
        FluidTankBlockEntity below = createTank(helper, new BlockPos(3, 1, 3));
        helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.COPPER_TANK.get());
        CopperTankBlockEntity above = helper.getBlockEntity(new BlockPos(3, 2, 3), CopperTankBlockEntity.class);
        above.fluidHandler().set(0, FluidResource.of(OilFluids.SOURCE.get()), 4_000);
        helper.succeedWhen(() -> {
            int down = amount(below, OilFluids.SOURCE.get());
            check(helper, down == 4_000 && above.amount() == 0, "all 4000 poured: " + down + " below, " + above.amount() + " above");
        });
    }

    /**
     * A Create Fluid Tank, a Mechanical Pump turned by a cogwheel, a Copper Tank: the pump moves the oil from one to the
     * other ({@code intoOurs}: from Create's tank into ours, else back), every mB accounted for.
     */
    private static void pump(GameTestHelper helper, boolean intoOurs) {
        BlockPos createPos = new BlockPos(1, 1, 3);
        BlockPos pumpPos = new BlockPos(2, 1, 3);
        BlockPos oursPos = new BlockPos(3, 1, 3);
        FluidTankBlockEntity create = createTank(helper, createPos);
        helper.setBlock(oursPos, ModBlocks.COPPER_TANK.get());
        CopperTankBlockEntity ours = helper.getBlockEntity(oursPos, CopperTankBlockEntity.class);
        helper.setBlock(pumpPos, createBlock("mechanical_pump").defaultBlockState()
                .setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, intoOurs ? Direction.EAST : Direction.WEST));
        helper.setBlock(pumpPos.above(), createBlock("cogwheel").defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
        helper.setBlock(pumpPos.above().west(), createBlock("creative_motor").defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.EAST));
        helper.getBlockEntity(pumpPos.above().west(), CreativeMotorBlockEntity.class).generatedSpeed.setValue(64);
        Fluid oil = OilFluids.SOURCE.get();
        helper.startSequence().thenExecuteAfter(5, () -> {
            if (intoOurs) {
                create.getControllerBE().getTankInventory().fill(new FluidStack(oil, 6_000), IFluidHandler.FluidAction.EXECUTE);
            } else {
                ours.fluidHandler().set(0, FluidResource.of(oil), 6_000);
            }
        }).thenWaitUntil(() -> {
            int a = amount(create, oil);
            int b = ours.resource().is(oil) ? ours.amount() : 0;
            check(helper, a + b <= 6_000, "never more than there was: " + a + " + " + b);
            check(helper, intoOurs ? b == 6_000 : a == 6_000, "all of it moved: Create's tank " + a + ", ours " + b);
        }).thenSucceed();
    }

    /**
     * Our buckets of oil and liquid experience into an Item Drain: each is emptied into its tank, an empty bucket left; a
     * bucket of mercury is not (mercury goes only through our own iron tanks and pipes).
     */
    private static void drainBuckets(GameTestHelper helper) {
        BlockPos oilPos = new BlockPos(1, 1, 3);
        BlockPos mercuryPos = new BlockPos(5, 1, 3);
        helper.setBlock(oilPos, createBlock("item_drain"));
        helper.setBlock(mercuryPos, createBlock("item_drain"));
        IItemHandler oilIn = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(oilPos), Direction.NORTH);
        IItemHandler mercuryIn = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(mercuryPos), Direction.NORTH);
        check(helper, oilIn != null && mercuryIn != null, "the drains take items from their sides");
        check(helper, oilIn.insertItem(0, new ItemStack(ModOil.VEGETABLE_OIL_BUCKET.get()), false).isEmpty(), "the drain takes a bucket of oil");
        ItemStack mercuryLeft = mercuryIn.insertItem(0, new ItemStack(ModItems.MERCURY_BUCKET.get()), false);
        helper.startSequence().thenExecuteAfter(60, () -> {
            IFluidHandler oilTank = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                    helper.absolutePos(oilPos), Direction.DOWN);
            FluidStack drained = oilTank.getFluidInTank(0);
            check(helper, drained.getFluid().isSame(OilFluids.SOURCE.get()) && drained.getAmount() == 1_000, "1000 mB of oil drained: " + drained.getAmount());
            IFluidHandler mercuryTank = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                    helper.absolutePos(mercuryPos), Direction.DOWN);
            check(helper, mercuryTank.getFluidInTank(0).isEmpty(), "no mercury drained: " + mercuryTank.getFluidInTank(0).getAmount()
                    + " (the bucket " + (mercuryLeft.isEmpty() ? "went in" : "was refused") + ")");
        }).thenSucceed();
    }

    /**
     * A pool of mercury at a Create pipe's open end, a Mechanical Pump drawing from it into a Create Fluid Tank: the
     * mercury stays where it is - only our own iron tanks and pipes move mercury.
     */
    private static void mercuryPool(GameTestHelper helper) {
        BlockPos poolPos = new BlockPos(1, 1, 3);
        BlockPos pipePos = new BlockPos(2, 1, 3);
        BlockPos pumpPos = new BlockPos(3, 1, 3);
        BlockPos tankPos = new BlockPos(4, 1, 3);
        helper.setBlock(poolPos, ModBlocks.MERCURY.get());
        helper.setBlock(pipePos, createBlock("fluid_pipe").defaultBlockState()
                .setValue(net.minecraft.world.level.block.PipeBlock.EAST, true).setValue(net.minecraft.world.level.block.PipeBlock.WEST, true));
        helper.setBlock(pumpPos, createBlock("mechanical_pump").defaultBlockState()
                .setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, Direction.EAST));
        FluidTankBlockEntity tank = createTank(helper, tankPos);
        helper.setBlock(pumpPos.above(), createBlock("cogwheel").defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
        helper.setBlock(pumpPos.above().west(), createBlock("creative_motor").defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.EAST));
        helper.getBlockEntity(pumpPos.above().west(), CreativeMotorBlockEntity.class).generatedSpeed.setValue(64);
        helper.startSequence().thenExecuteAfter(140, () -> {
            check(helper, helper.getBlockState(poolPos).is(ModBlocks.MERCURY.get()), "the mercury pool is still there");
            check(helper, tank.getControllerBE().getTankInventory().isEmpty(), "no mercury in Create's tank: "
                    + tank.getControllerBE().getTankInventory().getFluidAmount());
        }).thenSucceed();
    }

    /**
     * A Create Fluid Tank of 5000 mB of biodiesel, a Copper Pipe whose side against it pulls, a Copper Tank: the pipe draws
     * the Create tank into ours (the port's bridge from 21.1's fluid handlers into our transactions), every mB counted.
     */
    private static void pipeDrawsCreateTank(GameTestHelper helper) {
        BlockPos createPos = new BlockPos(1, 1, 3);
        BlockPos pipePos = new BlockPos(2, 1, 3);
        BlockPos oursPos = new BlockPos(3, 1, 3);
        FluidTankBlockEntity create = createTank(helper, createPos);
        helper.setBlock(pipePos, ModBlocks.COPPER_PIPE.get());
        helper.setBlock(oursPos, ModBlocks.COPPER_TANK.get());
        for (BlockPos pos : List.of(pipePos, oursPos)) {
            BlockPos at = helper.absolutePos(pos);
            helper.getLevel().setBlock(at, Block.updateFromNeighbourShapes(helper.getLevel().getBlockState(at), helper.getLevel(), at), Block.UPDATE_ALL);
        }
        CopperTankBlockEntity ours = helper.getBlockEntity(oursPos, CopperTankBlockEntity.class);
        Fluid biodiesel = OilFluids.BIODIESEL.get();
        helper.startSequence().thenExecuteAfter(5, () -> {
            create.getControllerBE().getTankInventory().fill(new FluidStack(biodiesel, 5_000), IFluidHandler.FluidAction.EXECUTE);
            var pipe = helper.getBlockEntity(pipePos, dev.nookworks.fluid.FluidPipeBlockEntity.class);
            check(helper, pipe.cycle(helper.getLevel(), Direction.WEST) == dev.nookworks.fluid.FluidPipeBlockEntity.SideSetting.PULLING,
                    "the side against Create's tank set to pull");
        }).thenWaitUntil(() -> {
            int a = amount(create, biodiesel);
            int inPipe = helper.getBlockEntity(pipePos, dev.nookworks.fluid.FluidPipeBlockEntity.class).stored();
            check(helper, a + inPipe + ours.amount() == 5_000, "nothing made or lost: " + a + " + " + inPipe + " + " + ours.amount());
            check(helper, ours.amount() == 5_000, "all of it drawn into ours: " + ours.amount());
        }).thenSucceed();
    }

    /** A Copper Tank of water over a Create Basin: it pours down into the basin's tanks, all a basin holds and no more. */
    private static void copperIntoBasin(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 1, 3), createBlock("basin"));
        helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.COPPER_TANK.get());
        CopperTankBlockEntity above = helper.getBlockEntity(new BlockPos(3, 2, 3), CopperTankBlockEntity.class);
        above.fluidHandler().set(0, FluidResource.of(Fluids.WATER), 4_000);
        helper.succeedWhen(() -> {
            IFluidHandler basin = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                    helper.absolutePos(new BlockPos(3, 1, 3)), Direction.UP);
            int inBasin = 0;
            int room = 0;
            for (int i = 0; i < basin.getTanks(); i++) {
                inBasin += basin.getFluidInTank(i).getAmount();
            }
            room = basin.getTankCapacity(0);
            check(helper, inBasin > 0 && inBasin + above.amount() == 4_000, "poured into the basin, nothing lost: " + inBasin + " + " + above.amount());
            check(helper, inBasin == Math.min(4_000, room), "as much as the basin takes: " + inBasin + " of " + room);
        });
    }

    /** A Deployer facing down, turned by a Creative Motor, holding a Crate (or a vanilla block, the control): it sets it down. */
    private static void deployerPlaces(GameTestHelper helper, Block block) {
        BlockPos deployer = new BlockPos(3, 3, 3); // a deployer works on the block two in front of it
        helper.setBlock(deployer, createBlock("deployer").defaultBlockState()
                .setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, Direction.DOWN)
                .setValue(com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true));
        helper.setBlock(deployer.west(), createBlock("creative_motor").defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.EAST));
        helper.getBlockEntity(deployer.west(), CreativeMotorBlockEntity.class).generatedSpeed.setValue(64);
        IItemHandler hand = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(deployer), Direction.UP);
        check(helper, hand != null && hand.insertItem(0, new ItemStack(block.asItem()), false).isEmpty(), "the deployer takes it");
        helper.succeedWhen(() -> check(helper, helper.getBlockState(deployer.below(2)).is(block),
                "set down two below the deployer: " + helper.getBlockState(deployer.below(2))));
    }

    /** A Display Link on a Copper Tank: our readout is one of its sources, and it sends the tank's line (its liquid and amount). */
    private static void displayLink(GameTestHelper helper) {
        BlockPos tankPos = new BlockPos(3, 1, 3);
        helper.setBlock(tankPos, ModBlocks.COPPER_TANK.get());
        helper.getBlockEntity(tankPos, CopperTankBlockEntity.class).fluidHandler().set(0, FluidResource.of(OilFluids.SOURCE.get()), 4_000);
        helper.setBlock(tankPos.above(), createBlock("display_link").defaultBlockState().setValue(BlockStateProperties_FACING(), Direction.UP));
        var link = helper.getBlockEntity(tankPos.above(), com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity.class);
        helper.runAfterDelay(2, () -> {
            var sources = com.simibubi.create.api.behaviour.display.DisplaySource.getAll(helper.getLevel(), helper.absolutePos(tankPos));
            check(helper, sources.contains(dev.nookworks.compat.create.CreateCompat.READOUT.get()), "our readout is a source on the tank: " + sources);
            var lines = dev.nookworks.compat.create.CreateCompat.READOUT.get()
                    .provideText(new com.simibubi.create.content.redstone.displayLink.DisplayLinkContext(helper.getLevel(), link), null);
            check(helper, lines.toString().contains("4") , "the tank's line: " + lines);
            helper.succeed();
        });
    }

    private static net.minecraft.world.level.block.state.properties.DirectionProperty BlockStateProperties_FACING() {
        return net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
    }

    static Fluid water() {
        return Fluids.WATER;
    }
}
