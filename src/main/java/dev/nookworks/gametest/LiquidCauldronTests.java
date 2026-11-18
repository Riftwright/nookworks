package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.List;
import java.util.function.Supplier;

import dev.nookworks.block.PipeSides;
import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.experience.ExperienceTankBlockEntity;
import dev.nookworks.experience.Move;
import dev.nookworks.fluid.CopperTankBlockEntity;
import dev.nookworks.fluid.FluidPipeBlockEntity;
import dev.nookworks.fluid.Tanks;
import dev.nookworks.mercury.DistillerBlockEntity;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import dev.nookworks.port.test.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import dev.nookworks.port.transfer.Capabilities;
import dev.nookworks.port.transfer.FluidResource;

/**
 * Cauldrons of methanol, wood vinegar, biodiesel, liquid experience and mercury (1.5; the user, 2026-11-10: 「メタノール
 * とか液体経験値とか、水銀とかも、大釜に入るようにできませんか？」), as the Vegetable Oil Cauldron: a bucket fills an
 * empty cauldron and an empty bucket takes a full one back, a comparator reads three; Copper Pipes fill and drain them a
 * bucket at a time - a line pulled from a tank too, and one from a machine - and Experience Pipes the liquid experience
 * one, but nothing the mercury one; standing in mercury poisons, in liquid experience burns.
 */
final class LiquidCauldronTests {
    private LiquidCauldronTests() {}

    static void register() {
        test("a_bucket_fills_each_liquid_cauldron_and_an_empty_one_takes_it_back", LiquidCauldronTests::buckets);
        test("pipes_fill_and_drain_liquid_cauldrons_but_not_the_mercury_one", LiquidCauldronTests::pipes);
        test("a_line_pulled_from_a_tank_or_run_from_a_distiller_fills_a_cauldron", 200, LiquidCauldronTests::lines);
        test("an_experience_pipe_line_fills_and_drains_a_liquid_experience_cauldron", 200, LiquidCauldronTests::experienceLine);
        test("a_body_in_a_mercury_cauldron_is_poisoned_and_in_liquid_experience_burns", 120, LiquidCauldronTests::touch);
    }

    private record Kind(Supplier<? extends Item> bucket, Supplier<? extends Block> cauldron) {}

    private static List<Kind> kinds() {
        return List.of(new Kind(ModOil.METHANOL_BUCKET, ModBlocks.METHANOL_CAULDRON), new Kind(ModOil.WOOD_VINEGAR_BUCKET, ModBlocks.WOOD_VINEGAR_CAULDRON),
                new Kind(ModOil.BIODIESEL_BUCKET, ModBlocks.BIODIESEL_CAULDRON), new Kind(ModItems.EXPERIENCE_BUCKET, ModBlocks.LIQUID_EXPERIENCE_CAULDRON),
                new Kind(ModItems.MERCURY_BUCKET, ModBlocks.MERCURY_CAULDRON));
    }

    private static void use(GameTestHelper helper, Player player, BlockPos pos, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.getBlockState(pos).useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP, helper.absolutePos(pos), false));
    }

    private static void buckets(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        int i = 0;
        for (Kind kind : kinds()) {
            BlockPos pos = new BlockPos(1 + (i % 3) * 2, 2, i < 3 ? 1 : 4); // the arena is 7 x 7
            i++;
            helper.setBlock(pos, Blocks.CAULDRON);
            use(helper, player, pos, new ItemStack(kind.bucket().get()));
            var state = helper.getBlockState(pos);
            check(helper, state.is(kind.cauldron().get()) && state.getValue(LayeredCauldronBlock.LEVEL) == 3,
                    "a bucket fills an empty cauldron: " + state);
            check(helper, player.getMainHandItem().is(Items.BUCKET), "the bucket left empty: " + player.getMainHandItem());
            check(helper, state.getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(pos)) == 3,
                    "a comparator reads three, as a full water cauldron");
            check(helper, state.is(net.minecraft.tags.BlockTags.CAULDRONS), "it is one of the cauldrons");
            use(helper, player, pos, new ItemStack(Items.BUCKET));
            check(helper, helper.getBlockState(pos).is(Blocks.CAULDRON) && player.getMainHandItem().is(kind.bucket().get()),
                    "an empty bucket takes it back: " + player.getMainHandItem());
            use(helper, player, pos, new ItemStack(kind.bucket().get()));
            use(helper, player, pos, new ItemStack(Items.GLASS_BOTTLE));
            check(helper, helper.getBlockState(pos).getValue(LayeredCauldronBlock.LEVEL) == 3,
                    "a glass bottle draws nothing (a level is a third of a bucket, a bottle a quarter)");
            check(helper, Block.getDrops(helper.getBlockState(pos), helper.getLevel(), helper.absolutePos(pos), null).stream()
                    .anyMatch(s -> s.is(Items.CAULDRON)), "broken, it drops a cauldron");
        }
        helper.succeed();
    }

    private static void pipes(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        record Liquid(Fluid fluid, Block cauldron) {}
        for (Liquid liquid : List.of(new Liquid(OilFluids.METHANOL.get(), ModBlocks.METHANOL_CAULDRON.get()),
                new Liquid(OilFluids.WOOD_VINEGAR.get(), ModBlocks.WOOD_VINEGAR_CAULDRON.get()),
                new Liquid(OilFluids.BIODIESEL.get(), ModBlocks.BIODIESEL_CAULDRON.get()),
                new Liquid(ExperienceFluids.SOURCE.get(), ModBlocks.LIQUID_EXPERIENCE_CAULDRON.get()))) {
            helper.setBlock(pos, Blocks.CAULDRON);
            var handler = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(pos), Direction.UP);
            check(helper, handler != null && Move.insert(handler, FluidResource.of(liquid.fluid()), 1_000) == 1_000
                    && helper.getBlockState(pos).is(liquid.cauldron()), "a pipe fills an empty cauldron: " + helper.getBlockState(pos));
            var full = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(pos), Direction.UP);
            check(helper, full != null && Move.extract(full, FluidResource.of(liquid.fluid()), 1_000) == 1_000
                    && helper.getBlockState(pos).is(Blocks.CAULDRON), "and draws it back out");
        }
        helper.setBlock(pos, ModBlocks.MERCURY_CAULDRON.get().defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        check(helper, helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(pos), Direction.UP) == null,
                "mercury goes through no fluid pipe: its cauldron has no fluid handler");
        helper.succeed();
    }

    /** A pipe set down as a player sets it, joined to what it may join around it. */
    private static void pipe(GameTestHelper helper, BlockPos at, Block block) {
        helper.setBlock(at, block.getStateForPlacement(new BlockPlaceContext(helper.getLevel(), null, InteractionHand.MAIN_HAND,
                new ItemStack(block.asItem()), new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(at)), Direction.UP,
                        helper.absolutePos(at), false))));
    }

    private static boolean full(GameTestHelper helper, BlockPos pos, Block cauldron) {
        var state = helper.getBlockState(pos);
        return state.is(cauldron) && state.getValue(LayeredCauldronBlock.LEVEL) == 3;
    }

    /**
     * A cauldron takes a bucket or nothing (NeoForge's cauldron handler), and the pipes asked it for a millibucket, so a
     * line pulled from a tank, or an empty one from a machine, never counted it as somewhere to go and never filled it
     * (nookTechnology, 2026-11-10). A Copper Tank of methanol, two Copper Pipes - the first pulling from the tank - and
     * a cauldron: it fills, and nothing is lost on the way. A Distiller holding a bucket of methanol fills a cauldron at
     * the end of an empty pipe on its tower's Condenser; a cauldron set against the Condenser itself gets none (pipes
     * only, since the play-test after 1.5).
     */
    private static void lines(GameTestHelper helper) {
        BlockPos tankAt = new BlockPos(1, 2, 1);
        BlockPos pulledInto = tankAt.east(3);
        helper.setBlock(tankAt, ModBlocks.COPPER_TANK.get());
        helper.setBlock(pulledInto, Blocks.CAULDRON);
        pipe(helper, tankAt.east(), ModBlocks.COPPER_PIPE.get());
        pipe(helper, tankAt.east(2), ModBlocks.COPPER_PIPE.get());
        var tank = helper.getBlockEntity(tankAt, CopperTankBlockEntity.class);
        tank.fluidHandler().set(0, FluidResource.of(OilFluids.METHANOL.get()), 3_000);
        var puller = helper.getBlockEntity(tankAt.east(), FluidPipeBlockEntity.class);
        var last = helper.getBlockEntity(tankAt.east(2), FluidPipeBlockEntity.class);
        check(helper, PipeSides.joined(helper.getBlockState(tankAt.east(2)), Direction.EAST), "the last pipe joins the cauldron");
        check(helper, puller.cycle(helper.getLevel(), Direction.WEST) == FluidPipeBlockEntity.SideSetting.PULLING,
                "the first pipe pulls from the tank");

        BlockPos piped = new BlockPos(1, 2, 4);
        tower(helper, piped);
        helper.setBlock(piped.above(3).east(2), Blocks.CAULDRON);
        pipe(helper, piped.above(3).east(), ModBlocks.COPPER_PIPE.get());
        BlockPos beside = new BlockPos(5, 2, 4);
        tower(helper, beside);
        helper.setBlock(beside.above(3).south(), Blocks.CAULDRON);
        var throughPipe = helper.getBlockEntity(piped, DistillerBlockEntity.class);
        var byItself = helper.getBlockEntity(beside, DistillerBlockEntity.class);
        Tanks.set(throughPipe.methanolTank(), OilFluids.METHANOL.get(), 1_000);
        Tanks.set(byItself.methanolTank(), OilFluids.METHANOL.get(), 1_000);

        helper.succeedWhen(() -> {
            check(helper, full(helper, pulledInto, ModBlocks.METHANOL_CAULDRON.get()),
                    "the pulled line fills the cauldron: " + helper.getBlockState(pulledInto));
            check(helper, tank.amount() + puller.stored() + last.stored() + 1_000 == 3_000,
                    "a bucket in the cauldron, the rest in the tank and the pipes: " + tank.amount() + " + " + puller.stored()
                            + " + " + last.stored());
            check(helper, full(helper, piped.above(3).east(2), ModBlocks.METHANOL_CAULDRON.get()) && throughPipe.methanolStored() == 0,
                    "the Distiller fills the cauldron at the end of the pipe on its Condenser: " + helper.getBlockState(piped.above(3).east(2)));
            check(helper, helper.getBlockState(beside.above(3).south()).is(Blocks.CAULDRON) && byItself.methanolStored() == 1_000,
                    "and none into one set against the Condenser: " + helper.getBlockState(beside.above(3).south()));
        });
    }

    private static void tower(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.DISTILLER.get());
        helper.setBlock(pos.above(1), ModBlocks.DISTILLATION_COLUMN.get());
        helper.setBlock(pos.above(2), ModBlocks.DISTILLATION_COLUMN.get());
        helper.setBlock(pos.above(3), ModBlocks.CONDENSER.get());
    }

    /**
     * The Experience Pipe holds a bucket since 1.5 (it held 400 mB, less than the bucket a cauldron takes at once, and
     * never filled one; the user, 2026-11-10: 「それで解決するなら、Bでいいよ」). An Experience Tank, two Experience Pipes - the
     * first pulling from the tank - and an empty cauldron: it fills with liquid experience, nothing lost on the way. A
     * full one, an Experience Pipe pulling from it and an Experience Tank: it is drained into the tank, a bucket whole.
     */
    private static void experienceLine(GameTestHelper helper) {
        BlockPos tankAt = new BlockPos(1, 2, 1);
        BlockPos cauldron = tankAt.east(3);
        helper.setBlock(tankAt, ModBlocks.EXPERIENCE_TANK.get());
        helper.setBlock(cauldron, Blocks.CAULDRON);
        pipe(helper, tankAt.east(), ModBlocks.EXPERIENCE_PIPE.get());
        pipe(helper, tankAt.east(2), ModBlocks.EXPERIENCE_PIPE.get());
        var tank = helper.getBlockEntity(tankAt, ExperienceTankBlockEntity.class);
        tank.fill(3_000);
        var puller = helper.getBlockEntity(tankAt.east(), FluidPipeBlockEntity.class);
        var last = helper.getBlockEntity(tankAt.east(2), FluidPipeBlockEntity.class);
        check(helper, PipeSides.joined(helper.getBlockState(tankAt.east(2)), Direction.EAST), "the last pipe joins the cauldron");
        check(helper, puller.cycle(helper.getLevel(), Direction.WEST) == FluidPipeBlockEntity.SideSetting.PULLING,
                "the first pipe pulls from the tank");

        BlockPos fullAt = new BlockPos(1, 2, 4);
        BlockPos intoAt = fullAt.east(2);
        helper.setBlock(fullAt, ModBlocks.LIQUID_EXPERIENCE_CAULDRON.get().defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        helper.setBlock(intoAt, ModBlocks.EXPERIENCE_TANK.get());
        pipe(helper, fullAt.east(), ModBlocks.EXPERIENCE_PIPE.get());
        var drain = helper.getBlockEntity(fullAt.east(), FluidPipeBlockEntity.class);
        var into = helper.getBlockEntity(intoAt, ExperienceTankBlockEntity.class);
        check(helper, PipeSides.joined(helper.getBlockState(fullAt.east()), Direction.WEST)
                && PipeSides.joined(helper.getBlockState(fullAt.east()), Direction.EAST), "the drain pipe joins the cauldron and the tank");
        check(helper, drain.cycle(helper.getLevel(), Direction.WEST) == FluidPipeBlockEntity.SideSetting.PULLING,
                "it pulls from the full cauldron");

        helper.succeedWhen(() -> {
            check(helper, full(helper, cauldron, ModBlocks.LIQUID_EXPERIENCE_CAULDRON.get()),
                    "the pulled line fills the cauldron with liquid experience: " + helper.getBlockState(cauldron));
            check(helper, tank.amount() + puller.stored() + last.stored() + 1_000 == 3_000,
                    "a bucket in the cauldron, the rest in the tank and the pipes: " + tank.amount() + " + " + puller.stored()
                            + " + " + last.stored());
            check(helper, helper.getBlockState(fullAt).is(Blocks.CAULDRON) && into.amount() == 1_000 && drain.stored() == 0,
                    "the full cauldron drained into the tank: " + helper.getBlockState(fullAt) + ", " + into.amount()
                            + " in the tank, " + drain.stored() + " in the pipe");
        });
    }

    private static void touch(GameTestHelper helper) {
        BlockPos mercury = new BlockPos(1, 2, 1);
        BlockPos experience = new BlockPos(4, 2, 1);
        helper.setBlock(mercury, ModBlocks.MERCURY_CAULDRON.get().defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        helper.setBlock(experience, ModBlocks.LIQUID_EXPERIENCE_CAULDRON.get().defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        var poisoned = helper.spawn(EntityType.PIG, new Vec3(1.5, 2.3, 1.5));
        var burnt = helper.spawn(EntityType.PIG, new Vec3(4.5, 2.3, 1.5));
        // held still: a pig left to wander hopped out of its cauldron before it was touched, failing one run in three
        // PORT: by taking their goals: 1.21.1 asks the blocks an entity is in only as it moves (26.3: every tick), and a
        // mob with no AI does not move at all, so a pig with setNoAi(true) was never touched by what it stood in
        poisoned.goalSelector.removeAllGoals(goal -> true);
        burnt.goalSelector.removeAllGoals(goal -> true);
        float health = burnt.getHealth();
        helper.succeedWhen(() -> {
            check(helper, poisoned.hasEffect(MobEffects.POISON) && poisoned.hasEffect(MobEffects.CONFUSION),
                    "mercury poisons the pig in it and makes it dizzy");
            check(helper, burnt.getHealth() < health || !burnt.isAlive(), "liquid experience burns the pig in it: " + burnt.getHealth());
        });
    }
}
