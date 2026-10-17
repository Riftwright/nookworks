package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.TransportTests.countIn;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.block.DieselGeneratorBlock;
import dev.nookworks.machine.CrusherBlockEntity;
import dev.nookworks.machine.DieselGeneratorBlockEntity;
import dev.nookworks.machine.GeneratorBlockEntity;
import dev.nookworks.machine.MachineIO;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Diesel Generator (r8, the user's ask of 2026-10-06): biodiesel alone, at 240 FE/t and 1,280 FE a mB - six
 * Generators, and twice a Generator's power from each mB; filled by pipe, by hand, or through its slot, the empties
 * coming back; its power sent along cables as a Generator's is.
 */
final class R8DieselGeneratorTests {
    private R8DieselGeneratorTests() {}

    static void register() {
        NookworksGameTests.test("diesel_generator_burns_biodiesel_at_240_fe_a_tick", 200, R8DieselGeneratorTests::burnsBiodiesel);
        NookworksGameTests.test("diesel_generator_refuses_oil_and_gives_the_empties_back", R8DieselGeneratorTests::refusesOil);
        NookworksGameTests.test("diesel_generator_pours_its_slot_and_puts_the_empties_aside", R8DieselGeneratorTests::pourItsSlot);
        NookworksGameTests.test("diesel_generator_powers_a_machine_over_cables", 200, R8DieselGeneratorTests::powersOverCables);
        NookworksGameTests.test("diesel_generator_is_made_with_an_empty_copper_tank", R8DieselGeneratorTests::madeWithAnEmptyTank);
    }

    /**
     * Fifteen mB (one gulp) burn for 80 ticks at 240 FE each - 19,200 FE, 1,280 a mB - lit while they burn, and then it
     * waits for fuel. Both figures follow the generatorOutput setting, as a Generator's do; at its default they are the
     * user's: six times a Generator's 40 FE/t, and twice the 640 FE a Generator gets from a mB of biodiesel.
     */
    private static void burnsBiodiesel(GameTestHelper helper) {
        check(helper, DieselGeneratorBlockEntity.FE_PER_TICK == 6 * GeneratorBlockEntity.FE_PER_TICK,
                "six Generators' worth a tick: " + DieselGeneratorBlockEntity.FE_PER_TICK);
        check(helper, DieselGeneratorBlockEntity.FE_PER_MB
                        == 2 * GeneratorBlockEntity.FE_PER_TICK * GeneratorBlockEntity.BIODIESEL_TICKS_PER_MB,
                "twice a Generator's power from a mB of biodiesel: " + DieselGeneratorBlockEntity.FE_PER_MB);
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.DIESEL_GENERATOR.get());
        DieselGeneratorBlockEntity gen = helper.getBlockEntity(pos, DieselGeneratorBlockEntity.class);
        int mb = DieselGeneratorBlockEntity.GULP;
        check(helper, gen.pour(mb), "15 mB pour into an empty tank");
        int rate = MachineTuning.generated(DieselGeneratorBlockEntity.FE_PER_TICK);
        int ticks = mb * DieselGeneratorBlockEntity.FE_PER_MB / DieselGeneratorBlockEntity.FE_PER_TICK;
        int[] last = {0};
        int[] ran = {0};
        List<Integer> odd = new ArrayList<>();
        boolean[] lit = {false};
        helper.onEachTick(() -> {
            int now = gen.energyStored();
            if (now != last[0]) {
                ran[0]++;
                if (now - last[0] != rate) {
                    odd.add(now - last[0]);
                }
            }
            last[0] = now;
            lit[0] |= helper.getBlockState(pos).getValue(DieselGeneratorBlock.LIT);
        });
        helper.succeedWhen(() -> {
            check(helper, gen.fuelStored() == 0, "the tank is burned dry: " + gen.fuelStored());
            check(helper, gen.energyStored() == ticks * rate, mb + " mB make " + ticks * rate + " FE, got " + gen.energyStored());
            check(helper, ran[0] == ticks, "over " + ticks + " ticks, ran " + ran[0]);
            check(helper, odd.isEmpty(), rate + " FE every tick it ran, not " + odd);
            check(helper, lit[0], "lit while it ran");
            check(helper, gen.guiStatus() == MachineStatus.NO_FUEL, "then it waits for fuel: " + gen.guiStatus());
        });
    }

    /**
     * Vegetable oil goes in by no way at all - a bucket or a bottle clicked on it is refused and kept, a pipe pushes
     * none, a hopper puts none in its slot (nor coal, nor lava) - while biodiesel clicked on it pours in, the empty
     * bucket or bottle coming back to the player. A full tank refuses a bucket, which is kept. A comparator reads the
     * tank.
     */
    private static void refusesOil(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.DIESEL_GENERATOR.get());
        helper.setBlock(pos.east(), Blocks.REDSTONE_BLOCK); // stopped, so the tank keeps just what was poured
        DieselGeneratorBlockEntity gen = helper.getBlockEntity(pos, DieselGeneratorBlockEntity.class);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 aside = helper.absoluteVec(new Vec3(0.5, 8, 0.5));
        player.snapTo(aside.x, aside.y, aside.z);

        click(helper, player, new ItemStack(ModOil.VEGETABLE_OIL_BUCKET.get()), pos);
        check(helper, player.getMainHandItem().is(ModOil.VEGETABLE_OIL_BUCKET.get()) && gen.fuelStored() == 0,
                "an oil bucket is refused and kept: " + player.getMainHandItem() + ", tank " + gen.fuelStored());
        check(helper, helper.getBlockState(pos.above()).isAir(), "and not poured out on top of it either");
        click(helper, player, new ItemStack(ModOil.VEGETABLE_OIL_BOTTLE.get()), pos);
        check(helper, player.getMainHandItem().is(ModOil.VEGETABLE_OIL_BOTTLE.get()) && gen.fuelStored() == 0,
                "an oil bottle is refused and kept: " + player.getMainHandItem());
        var pipeSide = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(pos), Direction.UP);
        check(helper, pipeSide != null, "a pipe finds its tank");
        try (Transaction tx = Transaction.openRoot()) {
            check(helper, pipeSide.insert(FluidResource.of(OilFluids.SOURCE.get()), 1_000, tx) == 0, "a pipe pushes no oil in");
            check(helper, pipeSide.insert(FluidResource.of(Fluids.WATER), 1_000, tx) == 0, "nor water");
        }
        for (ItemStack refused : List.of(new ItemStack(ModOil.VEGETABLE_OIL_BOTTLE.get()), new ItemStack(ModOil.VEGETABLE_OIL_BUCKET.get()),
                new ItemStack(Items.COAL), new ItemStack(Items.LAVA_BUCKET))) {
            check(helper, !gen.canPlaceItem(0, refused), "its slot takes no " + refused);
        }
        check(helper, gen.canPlaceItem(0, new ItemStack(ModOil.BIODIESEL_BOTTLE.get()))
                && gen.canPlaceItem(0, new ItemStack(ModOil.BIODIESEL_BUCKET.get())), "its slot takes biodiesel, bottled or by the bucket");

        click(helper, player, new ItemStack(ModOil.BIODIESEL_BUCKET.get()), pos);
        check(helper, gen.fuelStored() == 1_000 && player.getMainHandItem().is(Items.BUCKET),
                "a biodiesel bucket pours in and the empty comes back: tank " + gen.fuelStored() + ", hand " + player.getMainHandItem());
        click(helper, player, new ItemStack(ModOil.BIODIESEL_BOTTLE.get(), 2), pos);
        check(helper, gen.fuelStored() == 1_250 && player.getMainHandItem().is(ModOil.BIODIESEL_BOTTLE.get())
                        && player.getMainHandItem().getCount() == 1 && player.getInventory().countItem(Items.GLASS_BOTTLE) == 1,
                "a bottle pours in and its glass comes back: tank " + gen.fuelStored() + ", hand " + player.getMainHandItem());
        var abs = helper.absolutePos(pos);
        int signal = helper.getLevel().getBlockState(abs).getAnalogOutputSignal(helper.getLevel(), abs, Direction.WEST);
        check(helper, signal == 1 + 1_250 * 14 / DieselGeneratorBlockEntity.FUEL_TANK, "a comparator reads the tank: " + signal);

        check(helper, gen.pour(DieselGeneratorBlockEntity.FUEL_TANK - 1_250), "filled to the brim");
        click(helper, player, new ItemStack(ModOil.BIODIESEL_BUCKET.get()), pos);
        check(helper, player.getMainHandItem().is(ModOil.BIODIESEL_BUCKET.get()) && gen.fuelStored() == DieselGeneratorBlockEntity.FUEL_TANK,
                "a full tank refuses a bucket, which is kept: " + player.getMainHandItem());
        check(helper, helper.getBlockState(pos.above()).isAir(), "and not poured out on top of it");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Bottles put in its slot (as a hopper puts them) pour in one by one; the empties of a stack go into the chest under
     * it and the last stays in the slot, as a Generator's do. A bucket leaves its empty bucket in the slot, which a
     * hopper under it may draw out - and biodiesel may not be drawn out that way.
     */
    private static void pourItsSlot(GameTestHelper helper) {
        BlockPos bottles = new BlockPos(2, 2, 3);
        BlockPos bucket = new BlockPos(4, 2, 3);
        helper.setBlock(bottles.below(), Blocks.CHEST);
        helper.setBlock(bottles, ModBlocks.DIESEL_GENERATOR.get());
        helper.setBlock(bucket, ModBlocks.DIESEL_GENERATOR.get());
        helper.setBlock(bottles.north(), Blocks.REDSTONE_BLOCK); // stopped: pouring in goes on, burning does not
        helper.setBlock(bucket.north(), Blocks.REDSTONE_BLOCK);
        DieselGeneratorBlockEntity byBottle = helper.getBlockEntity(bottles, DieselGeneratorBlockEntity.class);
        DieselGeneratorBlockEntity byBucket = helper.getBlockEntity(bucket, DieselGeneratorBlockEntity.class);
        byBottle.setItem(0, new ItemStack(ModOil.BIODIESEL_BOTTLE.get(), 3));
        byBucket.setItem(0, new ItemStack(ModOil.BIODIESEL_BUCKET.get()));
        check(helper, !byBucket.canTakeItemThroughFace(0, new ItemStack(ModOil.BIODIESEL_BUCKET.get()), Direction.DOWN),
                "no biodiesel is drawn out from below");

        helper.succeedWhen(() -> {
            check(helper, byBottle.fuelStored() == 750, "three bottles poured in: " + byBottle.fuelStored());
            int aside = countIn(helper.getBlockEntity(bottles.below(), ChestBlockEntity.class), Items.GLASS_BOTTLE);
            check(helper, aside == 2 && byBottle.getItem(0).is(Items.GLASS_BOTTLE) && byBottle.getItem(0).getCount() == 1,
                    "two empties in the chest below, the last in the slot: " + aside + ", " + byBottle.getItem(0));
            check(helper, byBucket.fuelStored() == 1_000 && byBucket.getItem(0).is(Items.BUCKET),
                    "the bucket poured in, its empty left in the slot: " + byBucket.fuelStored() + ", " + byBucket.getItem(0));
            check(helper, byBucket.canTakeItemThroughFace(0, byBucket.getItem(0), Direction.DOWN), "which a hopper below may draw out");
        });
    }

    /**
     * Fuelled, it sends its power along Energy Cables to a Crusher, which crushes - the network found as a Generator's
     * is. It takes no power in: a Generator beside it does not count it among the machines it feeds.
     */
    private static void powersOverCables(GameTestHelper helper) {
        BlockPos diesel = new BlockPos(1, 2, 3);
        BlockPos crusher = new BlockPos(5, 2, 3);
        BlockPos generator = new BlockPos(1, 2, 4);
        helper.setBlock(diesel, ModBlocks.DIESEL_GENERATOR.get());
        for (int x = 2; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 2, 3), ModBlocks.ENERGY_CABLE.get());
        }
        helper.setBlock(crusher, ModBlocks.CRUSHER.get());
        helper.setBlock(crusher.below(), Blocks.CHEST);
        helper.setBlock(generator, ModBlocks.GENERATOR.get());
        var level = helper.getLevel();
        check(helper, MachineIO.receivers(level, helper.absolutePos(generator)).stream()
                .noneMatch(r -> r.pos().equals(helper.absolutePos(diesel))), "a Generator does not feed a Diesel Generator");
        DieselGeneratorBlockEntity gen = helper.getBlockEntity(diesel, DieselGeneratorBlockEntity.class);
        check(helper, gen.pour(250), "a bottle's worth in its tank");
        helper.getBlockEntity(crusher, CrusherBlockEntity.class).setItem(CrusherBlockEntity.INPUT, new ItemStack(Items.COBBLESTONE, 2));

        helper.succeedWhen(() -> {
            check(helper, MachineIO.receivers(level, helper.absolutePos(diesel)).stream()
                    .anyMatch(r -> r.pos().equals(helper.absolutePos(crusher))), "the cables lead it to the crusher");
            check(helper, countIn(helper.getBlockEntity(crusher.below(), ChestBlockEntity.class), Items.GRAVEL) >= 1,
                    "gravel arrives in the chest under the crusher");
        });
    }

    /**
     * Its recipe takes a Copper Tank only while the tank is empty: one holding liquid would lose it in the grid, the
     * way a filled tank never loses it anywhere else (a block that keeps a liquid keeps it, DECISIONS). The filled one
     * is a real tank's drop (r15: the recipe looked for the machines' component, which a tank never has, and took a
     * filled tank, water and all).
     */
    private static void madeWithAnEmptyTank(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos at = new BlockPos(1, 1, 1);
        helper.setBlock(at, ModBlocks.COPPER_TANK.get());
        var tankBlock = helper.getBlockEntity(at, dev.nookworks.fluid.CopperTankBlockEntity.class);
        tankBlock.fluidHandler().set(0, FluidResource.of(Fluids.WATER), 1_000);
        ItemStack filled = net.minecraft.world.level.block.Block.getDrops(helper.getBlockState(at), level, helper.absolutePos(at), tankBlock,
                null, new ItemStack(Items.IRON_PICKAXE)).stream().filter(s -> s.is(ModItems.COPPER_TANK.get())).findFirst().orElseThrow();
        check(helper, filled.getOrDefault(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.EMPTY).getAmount() == 1_000,
                "the tank's drop holds its water");
        for (ItemStack tank : List.of(new ItemStack(ModItems.COPPER_TANK.get()), filled)) {
            ItemStack iron = new ItemStack(Items.IRON_INGOT);
            ItemStack brass = new ItemStack(ModItems.BRASS_INGOT.get());
            var grid = CraftingInput.of(3, 3, List.of(iron, tank, iron, brass, new ItemStack(ModItems.GENERATOR.get()), brass,
                    iron, new ItemStack(Items.IRON_BLOCK), iron));
            ItemStack made = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level)
                    .map(recipe -> recipe.value().assemble(grid)).orElse(ItemStack.EMPTY);
            if (tank == filled) {
                check(helper, made.isEmpty(), "a Copper Tank holding water makes nothing: " + made);
            } else {
                check(helper, made.is(ModItems.DIESEL_GENERATOR.get()), "an empty Copper Tank, a Generator, brass and iron make one: " + made);
            }
        }
        helper.succeed();
    }

    /** Right-clicks the top of {@code pos} holding {@code stack}, as the game does: the item first, then the block. */
    private static void click(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos at = helper.absolutePos(pos);
        Vec3 point = Vec3.atCenterOf(at).relative(Direction.UP, 0.5);
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND, new BlockHitResult(point, Direction.UP, at, false));
    }
}
