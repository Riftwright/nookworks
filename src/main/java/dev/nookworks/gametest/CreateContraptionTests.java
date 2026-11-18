package dev.nookworks.gametest;

import static dev.nookworks.gametest.CreateTests.createBlock;
import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import dev.nookworks.Nookworks;
import dev.nookworks.fluid.CopperTankBlockEntity;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.oil.OilPressBlockEntity;
import dev.nookworks.port.test.GameTestHelper;
import dev.nookworks.port.transfer.FluidResource;
import dev.nookworks.port.transfer.ItemResource;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModOil;
import dev.nookworks.storage.StorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Our blocks on Create's contraptions: a Mechanical Piston carries each one a block along and sets it down again, its
 * contents counted before and after (nothing duplicated, nothing lost, no crash); the blocks that remember other places
 * are refused, as {@code c:relocation_not_supported} says. Registered only with Create loaded.
 */
final class CreateContraptionTests {
    private CreateContraptionTests() {}

    static final BlockPos FROM = new BlockPos(2, 1, 3);
    static final BlockPos TO = new BlockPos(3, 1, 3);

    static void register() {
        NookworksGameTests.test("create_piston_carries_a_storage_barrel_whole", 200, CreateContraptionTests::barrel);
        NookworksGameTests.test("create_piston_carries_a_crate_whole", 200, CreateContraptionTests::crate);
        NookworksGameTests.test("create_piston_carries_a_copper_tank_whole", 200, CreateContraptionTests::copperTank);
        NookworksGameTests.test("create_piston_carries_an_oil_press_whole", 200, CreateContraptionTests::oilPress);
        NookworksGameTests.test("create_piston_leaves_a_drive_wheel_where_it_is", 120, CreateContraptionTests::driveWheelStays);
        NookworksGameTests.test("create_contraptions_refuse_exactly_our_remembering_blocks", CreateContraptionTests::refusals);
    }

    /** Pole, Mechanical Piston facing east, a Creative Motor on it; the block to move set at {@link #FROM} first. */
    private static void piston(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 3), createBlock("piston_extension_pole").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(1, 1, 3), createBlock("mechanical_piston").defaultBlockState()
                .setValue(DirectionalKineticBlock.FACING, Direction.EAST).setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true));
        helper.setBlock(new BlockPos(1, 2, 3), createBlock("creative_motor").defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.DOWN));
        helper.getBlockEntity(new BlockPos(1, 2, 3), CreativeMotorBlockEntity.class).generatedSpeed.setValue(64);
    }

    /** The piston pushes the block one along and sets it down; then {@code verify} looks at what arrived. */
    private static void moves(GameTestHelper helper, Block block, Consumer<GameTestHelper> verify) {
        piston(helper);
        helper.succeedWhen(() -> {
            check(helper, helper.getBlockState(TO).is(block), "the block arrived one along: " + helper.getBlockState(TO));
            check(helper, helper.getBlockState(new BlockPos(1, 1, 3)).getValue(MechanicalPistonBlock.STATE) == MechanicalPistonBlock.PistonState.EXTENDED,
                    "and was set down (the piston stands extended)");
            verify.accept(helper);
        });
    }

    private static void barrel(GameTestHelper helper) {
        helper.setBlock(FROM, ModBlocks.IRON_STORAGE.get());
        StorageBlockEntity barrel = helper.getBlockEntity(FROM, StorageBlockEntity.class);
        barrel.inventory().set(0, ItemResource.of(Items.COBBLESTONE), 128);
        barrel.inventory().set(5, ItemResource.of(Items.DIAMOND), 100);
        moves(helper, ModBlocks.IRON_STORAGE.get(), h -> {
            check(h, CreateLogisticsTests.held(h, TO, Items.COBBLESTONE) == 128 && CreateLogisticsTests.held(h, TO, Items.DIAMOND) == 100,
                    "its 128 cobblestone and 100 diamonds came with it: " + CreateLogisticsTests.held(h, TO, Items.COBBLESTONE) + ", "
                            + CreateLogisticsTests.held(h, TO, Items.DIAMOND));
            check(h, CreateLogisticsTests.loose(h, Items.COBBLESTONE) == 0 && CreateLogisticsTests.loose(h, Items.DIAMOND) == 0, "nothing spilt");
            check(h, CreateLogisticsTests.loose(h, ModBlocks.IRON_STORAGE.get().asItem()) == 0, "no barrel dropped");
        });
    }

    private static void crate(GameTestHelper helper) {
        helper.setBlock(FROM, ModBlocks.CRATE.get());
        helper.getBlockEntity(FROM, dev.nookworks.block.entity.CrateBlockEntity.class).setItem(2, new ItemStack(Items.EMERALD, 33));
        moves(helper, ModBlocks.CRATE.get(), h -> {
            check(h, CreateLogisticsTests.held(h, TO, Items.EMERALD) == 33, "its 33 emeralds came with it: " + CreateLogisticsTests.held(h, TO, Items.EMERALD));
            check(h, CreateLogisticsTests.loose(h, Items.EMERALD) == 0 && CreateLogisticsTests.loose(h, ModBlocks.CRATE.get().asItem()) == 0,
                    "nothing spilt or dropped");
        });
    }

    private static void copperTank(GameTestHelper helper) {
        helper.setBlock(FROM, ModBlocks.COPPER_TANK.get());
        helper.getBlockEntity(FROM, CopperTankBlockEntity.class).fluidHandler().set(0, FluidResource.of(OilFluids.SOURCE.get()), 7_000);
        moves(helper, ModBlocks.COPPER_TANK.get(), h -> {
            CopperTankBlockEntity tank = h.getBlockEntity(TO, CopperTankBlockEntity.class);
            check(h, tank.amount() == 7_000 && tank.resource().is(OilFluids.SOURCE.get()), "its 7000 mB of oil came with it: " + tank.amount());
        });
    }

    private static void oilPress(GameTestHelper helper) {
        helper.setBlock(FROM, ModOil.OIL_PRESS.get());
        OilPressBlockEntity press = helper.getBlockEntity(FROM, OilPressBlockEntity.class);
        press.setItem(OilPressBlockEntity.INPUT, new ItemStack(ModOil.SUNFLOWER_SEEDS.get(), 32));
        moves(helper, ModOil.OIL_PRESS.get(), h -> {
            OilPressBlockEntity moved = h.getBlockEntity(TO, OilPressBlockEntity.class);
            check(h, moved.getItem(OilPressBlockEntity.INPUT).getCount() == 32, "its 32 seeds came with it: " + moved.getItem(OilPressBlockEntity.INPUT));
            check(h, CreateLogisticsTests.loose(h, ModOil.SUNFLOWER_SEEDS.get()) == 0, "nothing spilt");
        });
    }

    /** A Drive Wheel (it remembers the wheels its belts run to) in front of the piston: the piston does not move it. */
    private static void driveWheelStays(GameTestHelper helper) {
        helper.setBlock(FROM, ModOil.DRIVE_WHEEL.get());
        piston(helper);
        helper.startSequence().thenExecuteAfter(80, () -> {
            check(helper, helper.getBlockState(FROM).is(ModOil.DRIVE_WHEEL.get()), "the wheel stays where it is");
            check(helper, !helper.getBlockState(TO).is(ModOil.DRIVE_WHEEL.get()), "and is not carried");
        }).thenSucceed();
    }

    /**
     * Every block of ours, set down on its own, asked as Create asks before carrying it: refused are exactly those in
     * c:relocation_not_supported (and the unbreakable ones and those no piston pushes, which Create refuses anyway).
     */
    private static void refusals(GameTestHelper helper) {
        Set<String> refused = new TreeSet<>();
        Set<String> wrong = new TreeSet<>();
        BlockPos at = new BlockPos(3, 2, 3);
        for (Block block : BuiltInRegistries.BLOCK) {
            var id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals(Nookworks.MODID)) {
                continue;
            }
            var state = block.defaultBlockState();
            helper.setBlock(at, state);
            boolean allowed = BlockMovementChecks.isMovementAllowed(helper.getBlockState(at), helper.getLevel(), helper.absolutePos(at));
            boolean tagged = state.is(net.neoforged.neoforge.common.Tags.Blocks.RELOCATION_NOT_SUPPORTED);
            boolean unbreakable = state.getDestroySpeed(helper.getLevel(), helper.absolutePos(at)) < 0;
            // and what no piston pushes (a two-block-high If Mirror, the Recycler): Create refuses those as vanilla does
            unbreakable |= state.getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.BLOCK;
            if (!allowed) {
                refused.add(id.getPath());
            }
            if (allowed == (tagged || unbreakable)) {
                wrong.add(id.getPath() + (allowed ? " moves though tagged" : " is refused untagged"));
            }
            helper.setBlock(at, net.minecraft.world.level.block.Blocks.AIR);
        }
        Nookworks.LOGGER.info("Create's contraptions refuse these blocks of ours: {}", refused);
        check(helper, wrong.isEmpty(), "refused exactly the tagged ones: " + wrong);
        check(helper, !(ModBlocks.CRATE.get() instanceof EntityBlock) || !refused.contains("crate"), "a Crate moves");
        helper.succeed();
    }
}
