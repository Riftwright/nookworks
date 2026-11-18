package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.List;
import java.util.function.Supplier;

import dev.nookworks.experience.ExperienceTankBlockEntity;
import dev.nookworks.experience.Move;
import dev.nookworks.fluid.CopperTankBlockEntity;
import dev.nookworks.mercury.MercuryTankBlockEntity;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import dev.nookworks.port.test.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import dev.nookworks.port.transfer.FluidResource;

/**
 * Our liquids in a dispenser (1.5; the user, 2026-11-10: 「ディスペンサーに液体入りバケツ入れたら、レッドストーンで水みたいに
 * 出したり戻したりできたら楽しいな」, and 「ディスペンサーに空の瓶とか入れてタンクに向けて置いておく→RS信号入れたら、瓶詰に
 * してディスペンサーのインベントリに戻す」): each bucket of ours pours its pool in front and an empty bucket takes it back;
 * facing a Copper Tank, a cauldron, a Mercury Tank, buckets and bottles pour in and are filled, the containers kept in the
 * dispenser; vanilla's water bucket still pours water into the open.
 */
final class LiquidDispensingTests {
    private LiquidDispensingTests() {}

    static void register() {
        test("a_dispenser_pours_each_bucket_of_ours_and_an_empty_one_takes_it_back", LiquidDispensingTests::pools);
        test("a_dispenser_still_pours_and_takes_back_water_in_the_open", LiquidDispensingTests::vanillaWater);
        test("a_redstone_pulse_pours_liquid_experience_and_the_next_takes_it_back", 100, LiquidDispensingTests::pulse);
        test("a_dispenser_fills_buckets_and_bottles_at_a_tank_and_pours_them_in", LiquidDispensingTests::tank);
        test("a_dispenser_fills_and_empties_cauldrons_and_mercury_tanks", LiquidDispensingTests::cauldrons);
        test("a_dispenser_moves_no_liquid_across_a_claims_edge", LiquidDispensingTests::claimEdge);
        test("a_dispenser_moves_no_liquid_into_a_neighbours_claim", LiquidDispensingTests::neighbours);
    }

    /**
     * A dispenser keeps no owner, so a stranger's set outside a claim draws nothing out of a tank in it, pours nothing
     * into a cauldron there, and spills nothing over its edge; one inside the claim works as anywhere (r22 CXC-003,
     * CXP-002, CXM-002: it drew the claimed tank dry, as the tank's owner could break the open ground it stood on).
     */
    private static void claimEdge(GameTestHelper helper) {
        java.util.UUID owner = java.util.UUID.randomUUID();
        BlockPos outside = new BlockPos(1, 2, 1);
        BlockPos inside = new BlockPos(1, 2, 3);
        BlockPos cauldron = new BlockPos(1, 2, 5);
        BlockPos spill = new BlockPos(4, 2, 1);
        java.util.Set<BlockPos> claimed = new java.util.HashSet<>();
        for (BlockPos rel : new BlockPos[] {outside.east(), inside, inside.east(), cauldron.east(), spill.east()}) {
            claimed.add(helper.absolutePos(rel));
        }
        CopperTankBlockEntity[] tanks = new CopperTankBlockEntity[2];
        DispenserBlockEntity[] dispensers = new DispenserBlockEntity[2];
        int i = 0;
        for (BlockPos pos : new BlockPos[] {outside, inside}) {
            helper.setBlock(pos.east(), ModBlocks.COPPER_TANK.get());
            tanks[i] = helper.getBlockEntity(pos.east(), CopperTankBlockEntity.class);
            tanks[i].setOwner(owner);
            Move.insert(tanks[i].fluidHandler(), FluidResource.of(OilFluids.METHANOL.get()), 1_000);
            dispensers[i++] = dispenser(helper, pos, new ItemStack(Items.GLASS_BOTTLE));
        }
        helper.setBlock(cauldron.east(), Blocks.CAULDRON);
        var mercury = dispenser(helper, cauldron, new ItemStack(ModItems.MERCURY_BUCKET.get()));
        var experience = dispenser(helper, spill, new ItemStack(ModItems.EXPERIENCE_BUCKET.get()));
        // these dispensers were set down by nobody known (as before 1.5): asked about as nobody, the answers kept by
        // place - let go of before and after, or a later batch's test on these places would hear them
        dev.nookworks.feature.DrawRights.forget(helper.getLevel());
        try {
            BlockTests.underClaim(owner, claimed::contains, () -> {
                for (BlockPos pos : new BlockPos[] {outside, inside, cauldron, spill}) {
                    fire(helper, pos);
                }
            });
        } finally {
            dev.nookworks.feature.DrawRights.forget(helper.getLevel());
        }
        check(helper, tanks[0].amount() == 1_000 && count(dispensers[0], Items.GLASS_BOTTLE) == 1,
                "from outside the claim, nothing drawn from the tank in it: " + tanks[0].amount());
        check(helper, tanks[1].amount() == 750 && count(dispensers[1], dev.nookworks.registry.ModOil.METHANOL.get()) == 1,
                "from inside it, a bottle filled as anywhere: " + tanks[1].amount());
        check(helper, helper.getBlockState(cauldron.east()).is(Blocks.CAULDRON) && count(mercury, ModItems.MERCURY_BUCKET.get()) == 1,
                "no mercury poured into the claimed cauldron: " + helper.getBlockState(cauldron.east()));
        check(helper, helper.getBlockState(spill.east()).isAir() && count(experience, ModItems.EXPERIENCE_BUCKET.get()) == 1,
                "no liquid experience spilt over the claim's edge: " + helper.getBlockState(spill.east()));
        helper.succeed();
    }

    /**
     * Two claims side by side: a dispenser set down by one neighbour in theirs, facing a cauldron in the other's, takes
     * nothing out and pours nothing in - a block of no owner, which the claim alone keeps (the re-check of r22: one of
     * nobody's was let through, both in claims) - while one facing a cauldron in their own claim works. A player's
     * placing records who set it down.
     */
    private static void neighbours(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.server.level.ServerPlayer placer = MockPlayers.inLevel(helper, net.minecraft.world.level.GameType.SURVIVAL);
        java.util.UUID neighbour = java.util.UUID.randomUUID();
        BlockPos theirs = new BlockPos(1, 2, 1);
        BlockPos own = new BlockPos(1, 2, 3);
        var draw = dispenser(helper, theirs, new ItemStack(Items.BUCKET));
        var pour = dispenser(helper, own, new ItemStack(ModItems.MERCURY_BUCKET.get()));
        for (BlockPos pos : new BlockPos[] {theirs, own}) {
            net.neoforged.neoforge.event.EventHooks.onBlockPlace(placer,
                    net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(), level, helper.absolutePos(pos)), Direction.UP);
        }
        check(helper, placer.getUUID().equals(draw.getData(dev.nookworks.feature.DrawRights.PLACED_BY)),
                "a player's placing records who set the dispenser down");
        helper.setBlock(theirs.east(), net.minecraft.world.level.block.Blocks.CAULDRON);
        helper.setBlock(theirs.east(), dev.nookworks.fluid.LiquidCauldrons.full(ModBlocks.MERCURY_CAULDRON.get()));
        helper.setBlock(own.east(), net.minecraft.world.level.block.Blocks.CAULDRON);
        java.util.Set<BlockPos> neighbours = java.util.Set.of(helper.absolutePos(theirs.east()));
        java.util.Set<BlockPos> placers = java.util.Set.of(helper.absolutePos(theirs), helper.absolutePos(own), helper.absolutePos(own.east()));
        BlockTests.underClaim(neighbour, neighbours::contains, () -> BlockTests.underClaim(placer.getUUID(), placers::contains, () -> {
            fire(helper, theirs);
            fire(helper, own);
        }));
        check(helper, helper.getBlockState(theirs.east()).is(ModBlocks.MERCURY_CAULDRON.get()) && count(draw, Items.BUCKET) == 1,
                "the neighbour's mercury left in their cauldron: " + helper.getBlockState(theirs.east()));
        check(helper, helper.getBlockState(own.east()).is(ModBlocks.MERCURY_CAULDRON.get()) && count(pour, Items.BUCKET) == 1,
                "and their own cauldron filled from their own dispenser: " + helper.getBlockState(own.east()));
        helper.succeed();
    }

    /** A dispenser at {@code pos} facing east, holding {@code stack}. */
    private static DispenserBlockEntity dispenser(GameTestHelper helper, BlockPos pos, ItemStack stack) {
        helper.setBlock(pos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        var dispenser = helper.getBlockEntity(pos, DispenserBlockEntity.class);
        dispenser.setItem(0, stack);
        return dispenser;
    }

    /** One shot, as a redstone pulse gives it: what is in its first filled slot, by its dispenser behaviour. */
    private static void fire(GameTestHelper helper, BlockPos pos) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(pos);
        var dispenser = helper.getBlockEntity(pos, DispenserBlockEntity.class);
        for (int slot = 0; slot < dispenser.getContainerSize(); slot++) {
            ItemStack stack = dispenser.getItem(slot);
            if (!stack.isEmpty()) {
                var behaviour = DispenserBlock.DISPENSER_REGISTRY.get(stack.getItem());
                if (behaviour == null) {
                    behaviour = new DefaultDispenseItemBehavior();
                }
                dispenser.setItem(slot, behaviour.dispense(new BlockSource(level, at, level.getBlockState(at), dispenser), stack));
                return;
            }
        }
    }

    private static int count(DispenserBlockEntity dispenser, Item item) {
        return dispenser.countItem(item);
    }

    private record Bucket(Supplier<? extends Item> bucket, Supplier<? extends Block> pool) {}

    /** Every bucket of ours pours a pool of its liquid in front, and the empty bucket left takes the pool back up. */
    private static void pools(GameTestHelper helper) {
        int i = 0;
        for (Bucket b : List.of(new Bucket(ModOil.VEGETABLE_OIL_BUCKET, ModOil.VEGETABLE_OIL), new Bucket(ModOil.BIODIESEL_BUCKET, ModOil.BIODIESEL_BLOCK),
                new Bucket(ModOil.METHANOL_BUCKET, ModOil.METHANOL_BLOCK), new Bucket(ModOil.WOOD_VINEGAR_BUCKET, ModOil.WOOD_VINEGAR_BLOCK),
                new Bucket(ModItems.EXPERIENCE_BUCKET, ModBlocks.LIQUID_EXPERIENCE), new Bucket(ModItems.MERCURY_BUCKET, ModBlocks.MERCURY))) {
            BlockPos pos = new BlockPos(i < 3 ? 0 : 3, 1, 1 + (i % 3) * 2); // the arena is 7 x 7
            i++;
            var dispenser = dispenser(helper, pos, new ItemStack(b.bucket().get()));
            fire(helper, pos);
            var front = helper.getBlockState(pos.east());
            check(helper, front.is(b.pool().get()) && front.getFluidState().isSource(), "a pool poured in front: " + front);
            check(helper, count(dispenser, Items.BUCKET) == 1 && count(dispenser, b.bucket().get()) == 0, "the bucket kept, empty");
            fire(helper, pos);
            check(helper, !helper.getBlockState(pos.east()).getFluidState().isSource() && count(dispenser, b.bucket().get()) == 1,
                    "and the empty bucket takes it back: " + helper.getBlockState(pos.east()));
        }
        helper.succeed();
    }

    /** Vanilla's water bucket, unchanged in the open: it pours water out, and the empty bucket takes it back. */
    private static void vanillaWater(GameTestHelper helper) {
        BlockPos water = new BlockPos(1, 1, 3);
        var vanilla = dispenser(helper, water, new ItemStack(Items.WATER_BUCKET));
        fire(helper, water);
        check(helper, helper.getBlockState(water.east()).is(Blocks.WATER) && count(vanilla, Items.BUCKET) == 1, "water pours out as ever");
        fire(helper, water);
        check(helper, count(vanilla, Items.WATER_BUCKET) == 1, "and is taken back as ever");
        helper.succeed();
    }

    /**
     * By a real redstone signal: a pulse pours the Liquid Experience Bucket out in front - a trap's floor - and the next
     * takes it back into the dispenser.
     */
    private static void pulse(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        BlockPos lever = pos.west();
        var dispenser = dispenser(helper, pos, new ItemStack(ModItems.EXPERIENCE_BUCKET.get()));
        helper.startSequence()
                .thenExecute(() -> helper.setBlock(lever, Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> check(helper, helper.getBlockState(pos.east()).is(ModBlocks.LIQUID_EXPERIENCE.get()),
                        "the pulse pours liquid experience out"))
                .thenExecute(() -> helper.setBlock(lever, Blocks.AIR))
                .thenIdle(4)
                .thenExecute(() -> helper.setBlock(lever, Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> check(helper, count(dispenser, ModItems.EXPERIENCE_BUCKET.get()) == 1
                        && !helper.getBlockState(pos.east()).getFluidState().isSource(), "the next takes it back"))
                .thenSucceed();
    }

    /**
     * Facing a Copper Tank of methanol: an empty bucket comes back a Methanol Bucket into the dispenser, and pours back in;
     * of two glass bottles one is filled and put back beside the other; a filled bottle pours in. A water bucket will not
     * go into a tank of methanol, nor lava into any: it stays, unthrown. Facing an Experience Tank, a glass bottle draws
     * liquid experience.
     */
    private static void tank(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos.east(), ModBlocks.COPPER_TANK.get());
        var tank = helper.getBlockEntity(pos.east(), CopperTankBlockEntity.class);
        Move.insert(tank.fluidHandler(), FluidResource.of(OilFluids.METHANOL.get()), 1_000);
        var dispenser = dispenser(helper, pos, new ItemStack(Items.BUCKET));
        fire(helper, pos);
        check(helper, tank.amount() == 0 && count(dispenser, ModOil.METHANOL_BUCKET.get()) == 1,
                "an empty bucket filled from the tank, back in the dispenser: " + tank.amount());
        fire(helper, pos);
        check(helper, tank.amount() == 1_000 && count(dispenser, Items.BUCKET) == 1, "and poured back in: " + tank.amount());
        dispenser.setItem(0, new ItemStack(Items.GLASS_BOTTLE, 2));
        fire(helper, pos);
        check(helper, tank.amount() == 750 && count(dispenser, ModOil.METHANOL.get()) == 1 && count(dispenser, Items.GLASS_BOTTLE) == 1,
                "a glass bottle filled and put back beside the other: " + tank.amount());
        dispenser.setItem(0, ItemStack.EMPTY);
        fire(helper, pos); // the filled bottle, in the slot it was put back into
        check(helper, tank.amount() == 1_000 && count(dispenser, Items.GLASS_BOTTLE) == 1, "a filled bottle pours in: " + tank.amount());
        dispenser.setItem(0, ItemStack.EMPTY);
        dispenser.setItem(1, ItemStack.EMPTY);
        dispenser.setItem(0, new ItemStack(Items.WATER_BUCKET));
        fire(helper, pos);
        check(helper, count(dispenser, Items.WATER_BUCKET) == 1 && tank.amount() == 1_000 && tank.resource().getFluid() == OilFluids.METHANOL.get(),
                "water will not go into a tank of methanol, and the bucket stays in the dispenser");
        dispenser.setItem(0, new ItemStack(Items.LAVA_BUCKET));
        fire(helper, pos);
        check(helper, count(dispenser, Items.LAVA_BUCKET) == 1, "nor lava, which no Copper Tank holds");

        BlockPos second = new BlockPos(1, 2, 4);
        helper.setBlock(second.east(), ModBlocks.EXPERIENCE_TANK.get());
        var experience = helper.getBlockEntity(second.east(), ExperienceTankBlockEntity.class);
        var held = helper.getLevel().getCapability(dev.nookworks.port.transfer.Capabilities.Fluid.BLOCK,
                helper.absolutePos(second.east()), Direction.WEST);
        check(helper, held != null && Move.insert(held, FluidResource.of(dev.nookworks.experience.ExperienceFluids.SOURCE.get()), 500) == 500,
                "an Experience Tank with half a bucket");
        var bottler = dispenser(helper, second, new ItemStack(Items.GLASS_BOTTLE));
        fire(helper, second);
        check(helper, count(bottler, ModItems.LIQUID_EXPERIENCE_BOTTLE.get()) == 1, "a glass bottle draws a Liquid Experience Bottle: "
                + bottler.getItem(0) + " (" + experience.getBlockPos() + ")");
        helper.succeed();
    }

    /**
     * Facing an empty cauldron: a Methanol Bucket fills it, an empty bucket takes it back; a water bucket fills it with
     * water, as by hand; the Mercury Bucket fills it with mercury and an empty bucket takes that back. Facing a Mercury
     * Tank, the Mercury Bucket pours in a bucket and an empty bucket draws it.
     */
    private static void cauldrons(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos.east(), Blocks.CAULDRON);
        var dispenser = dispenser(helper, pos, new ItemStack(ModOil.METHANOL_BUCKET.get()));
        fire(helper, pos);
        check(helper, helper.getBlockState(pos.east()).is(ModBlocks.METHANOL_CAULDRON.get())
                && helper.getBlockState(pos.east()).getValue(LayeredCauldronBlock.LEVEL) == 3, "a methanol cauldron: " + helper.getBlockState(pos.east()));
        fire(helper, pos);
        check(helper, helper.getBlockState(pos.east()).is(Blocks.CAULDRON) && count(dispenser, ModOil.METHANOL_BUCKET.get()) == 1,
                "taken back into its bucket");
        dispenser.setItem(0, new ItemStack(Items.WATER_BUCKET));
        fire(helper, pos);
        check(helper, helper.getBlockState(pos.east()).is(Blocks.WATER_CAULDRON), "a water bucket fills it with water: " + helper.getBlockState(pos.east()));
        fire(helper, pos);
        check(helper, helper.getBlockState(pos.east()).is(Blocks.CAULDRON) && count(dispenser, Items.WATER_BUCKET) == 1, "and takes it back");
        dispenser.setItem(0, new ItemStack(ModItems.MERCURY_BUCKET.get()));
        fire(helper, pos);
        check(helper, helper.getBlockState(pos.east()).is(ModBlocks.MERCURY_CAULDRON.get()), "a mercury cauldron: " + helper.getBlockState(pos.east()));
        fire(helper, pos);
        check(helper, helper.getBlockState(pos.east()).is(Blocks.CAULDRON) && count(dispenser, ModItems.MERCURY_BUCKET.get()) == 1,
                "taken back as a Mercury Bucket");

        BlockPos second = new BlockPos(1, 2, 4);
        helper.setBlock(second.east(), ModBlocks.MERCURY_TANK.get());
        var mercury = helper.getBlockEntity(second.east(), MercuryTankBlockEntity.class);
        var pourer = dispenser(helper, second, new ItemStack(ModItems.MERCURY_BUCKET.get()));
        fire(helper, second);
        check(helper, mercury.mercury() == 1_000 && count(pourer, Items.BUCKET) == 1, "into the Mercury Tank: " + mercury.mercury());
        fire(helper, second);
        check(helper, mercury.mercury() == 0 && count(pourer, ModItems.MERCURY_BUCKET.get()) == 1, "and drawn back out: " + mercury.mercury());
        helper.succeed();
    }
}
