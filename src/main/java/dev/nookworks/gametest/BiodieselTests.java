package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.List;

import dev.nookworks.block.entity.SifterBlockEntity;
import dev.nookworks.machine.MachineMenu;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.mercury.RetortBlockEntity;
import dev.nookworks.mercury.RetortBottles;
import dev.nookworks.mercury.StirringTankBlockEntity;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.oil.OilPressBlockEntity;
import dev.nookworks.oil.PressingRecipe;
import dev.nookworks.oil.SunflowerCropBlock;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The biodiesel line as the user had it built and run (2026-10-12): the Sealed Retort told where hoppered bottles go,
 * caustic soda serving four batches, and Sunflower Seeds - sifted, sown into a sunflower, shelled from one, and what
 * the Oil Press takes in place of the flower.
 */
final class BiodieselTests {
    static final String[] NEED_LIQUID_EXPERIENCE = {"retort_sends_hoppered_bottles_where_its_screen_says"};

    static void register() {
        test("retort_sends_hoppered_bottles_where_its_screen_says", 120, BiodieselTests::retortBottles);
        test("one_caustic_soda_serves_four_batches_of_biodiesel", 600, BiodieselTests::sodaServesFour);
        test("sunflower_seeds_sift_sow_shell_and_press", BiodieselTests::sunflowerSeeds);
        test("a_slower_job_costs_no_more_power", BiodieselTests::slowerJobCostsNoMore);
        test("a_worlds_old_settings_move_to_syncedconfig", BiodieselTests::worldSettingsMoveToSyncedConfig);
    }

    /**
     * Auto, as it was: an empty retort's bottles to the fill box. Experience only: to the fill box though logs are in.
     * Wood Vinegar only: to the reagents, though no log is in yet. The button steps through them; the choice is saved.
     */
    private static void retortBottles(GameTestHelper helper) {
        BlockPos experience = new BlockPos(1, 1, 1);
        BlockPos vinegar = new BlockPos(4, 1, 1);
        for (BlockPos pos : List.of(experience, vinegar)) {
            helper.setBlock(pos, ModBlocks.RETORT.get());
            helper.setBlock(pos.above(), Blocks.HOPPER);
            helper.getBlockEntity(pos.above(), HopperBlockEntity.class).setItem(0, new ItemStack(Items.GLASS_BOTTLE, 4));
        }
        var toFill = helper.getBlockEntity(experience, RetortBlockEntity.class);
        var toLogs = helper.getBlockEntity(vinegar, RetortBlockEntity.class);
        check(helper, toFill.bottles() == RetortBottles.AUTO, "a retort starts as it always worked: " + toFill.bottles());

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var menu = new MachineMenu(1, player.getInventory(), toFill.layout(), toFill.slots(), MachineMenu.data(toFill), toFill);
        check(helper, menu.clickMenuButton(player, MachineMenu.BUTTON_RETORT_BOTTLES) && toFill.bottles() == RetortBottles.EXPERIENCE,
                "the screen's button steps to experience only: " + toFill.bottles());
        check(helper, menu.extra() == RetortBottles.EXPERIENCE.ordinal(), "and the screen is told so");
        toFill.setItem(0, new ItemStack(Items.OAK_LOG, 4)); // logs in, which in auto would take the bottles
        toLogs.setBottles(RetortBottles.VINEGAR);

        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        toLogs.saveCustomOnly(saved);
        check(helper, "VINEGAR".equals(saved.buildResult().getStringOr("Bottles", "")), "the choice is saved");
        // and read back by a retort made afresh, as after a restart (r14: only the writing was looked at)
        var reloaded = new RetortBlockEntity(helper.absolutePos(vinegar), helper.getBlockState(vinegar));
        reloaded.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), saved.buildResult()));
        check(helper, reloaded.bottles() == RetortBottles.VINEGAR, "and read back: " + reloaded.bottles());
        helper.runAfterDelay(60, () -> {
            check(helper, toFill.getItem(RetortBlockEntity.FILL).is(Items.GLASS_BOTTLE)
                    && toFill.getItem(RetortBlockEntity.FIRST_REAGENT).isEmpty()
                    && toFill.getItem(RetortBlockEntity.FIRST_REAGENT + 1).isEmpty(),
                    "experience only: into the fill box, the logs beside them left without: " + toFill.getItem(RetortBlockEntity.FILL));
            check(helper, toLogs.getItem(RetortBlockEntity.FILL).isEmpty()
                    && toLogs.getItem(RetortBlockEntity.FIRST_REAGENT).is(Items.GLASS_BOTTLE),
                    "wood vinegar only: to the reagents with no log in yet, never the fill box: "
                            + toLogs.getItem(RetortBlockEntity.FIRST_REAGENT) + ", " + toLogs.getItem(RetortBlockEntity.FILL));
            helper.succeed();
        });
    }

    /** Four batches - a bucket of oil, four methanol - from the one caustic soda; a fifth wants another. Saved between. */
    private static void sodaServesFour(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos.below(), Blocks.CHEST);
        helper.setBlock(pos, ModBlocks.STIRRING_TANK.get());
        var tank = helper.getBlockEntity(pos, StirringTankBlockEntity.class);
        tank.energy().set((int) tank.energy().getCapacityAsLong());
        tank.setItem(0, new ItemStack(ModOil.METHANOL.get(), 5));
        tank.setItem(StirringTankBlockEntity.CATALYST, new ItemStack(ModItems.CAUSTIC_SODA.get()));
        try (Transaction tx = Transaction.openRoot()) {
            int poured = tank.fluidHandler().insert(FluidResource.of(OilFluids.SOURCE.get()), 1_500, tx);
            tx.commit();
            check(helper, poured == 1_500, "the oil goes in: " + poured);
        }
        helper.runAfterDelay(StirringTankBlockEntity.TICKS + 10, () -> {
            check(helper, tank.getItem(StirringTankBlockEntity.CATALYST).isEmpty() && tank.catalystLeft() == 3,
                    "the first batch takes the soda in, with three batches left in it: " + tank.catalystLeft());
            var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
            tank.saveCustomOnly(saved);
            check(helper, saved.buildResult().getIntOr("CatalystLeft", -1) == 3, "and what is left of it is saved");
            var reloaded = new StirringTankBlockEntity(helper.absolutePos(pos), helper.getBlockState(pos));
            reloaded.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(ProblemReporter.DISCARDING,
                    helper.getLevel().registryAccess(), saved.buildResult()));
            check(helper, reloaded.catalystLeft() == 3, "and read back, as after a restart (r14): " + reloaded.catalystLeft());
        });
        helper.succeedWhen(() -> {
            check(helper, tank.biodieselStored() == StirringTankBlockEntity.BIODIESEL_TANK, "four batches: " + tank.biodieselStored());
            check(helper, tank.oilStored() == 500 && tank.getItem(0).getCount() == 1, "a bucket of oil and four methanol spent");
            check(helper, tank.catalystLeft() == 0, "the soda spent with them");
        });
    }

    private static void sunflowerSeeds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack seeds = new ItemStack(ModOil.SUNFLOWER_SEEDS.get());

        // sifted from soil, about as often as corn kernels
        int found = 0;
        for (int i = 0; i < 20_000; i++) {
            found += SifterBlockEntity.sift(level, helper.absolutePos(BlockPos.ZERO), new ItemStack(Items.DIRT)).stream()
                    .filter(stack -> stack.is(ModOil.SUNFLOWER_SEEDS.get())).mapToInt(ItemStack::getCount).sum();
        }
        check(helper, found >= 50 && found <= 140, "20,000 blocks of dirt should give about 90 sunflower seeds, gave " + found);

        // a sunflower crafted into yellow dye, as in vanilla, leaves its four seeds in the grid, the petals plucked
        CraftingInput grid = CraftingInput.of(1, 1, List.of(new ItemStack(Items.SUNFLOWER)));
        var dyeing = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level).orElse(null);
        check(helper, dyeing != null && dyeing.id().identifier().toString().equals("minecraft:yellow_dye_from_sunflower"),
                "a sunflower in the grid is still vanilla's yellow dye recipe: " + dyeing);
        ItemStack dye = dyeing.value().assemble(grid);
        check(helper, dye.is(Items.DYE.pick(net.minecraft.world.item.DyeColor.YELLOW)) && dye.getCount() == 2, "two yellow dye, as ever: " + dye);
        ItemStack left = dyeing.value().getRemainingItems(grid).getFirst();
        check(helper, left.is(ModOil.SUNFLOWER_SEEDS.get()) && left.getCount() == 4, "and its four seeds stay in the grid: " + left);

        // the press takes the seeds, not the flower: four seeds give about the oil a flower gave
        var pressed = PressingRecipe.of(seeds);
        check(helper, pressed != null && pressed.oil() * 4 >= 200 && pressed.oil() * 4 <= 250, "four seeds give a flower's oil: " + pressed);
        check(helper, PressingRecipe.of(new ItemStack(Items.SUNFLOWER)) == null, "the flower itself is not pressed");
        BlockPos press = new BlockPos(5, 2, 5);
        helper.setBlock(press, ModOil.OIL_PRESS.get());
        var pressEntity = helper.getBlockEntity(press, OilPressBlockEntity.class);
        check(helper, pressEntity.canPlaceItem(0, seeds) && !pressEntity.canPlaceItem(0, new ItemStack(Items.SUNFLOWER)),
                "a hopper loads the press with seeds, not sunflowers");

        // eaten and filed as seeds
        check(helper, seeds.is(ItemTags.CHICKEN_FOOD) && seeds.is(ItemTags.PARROT_FOOD), "chickens and parrots eat them");
        check(helper, seeds.is(itemTag("c:seeds/sunflower")) && seeds.is(itemTag("c:seeds")), "they are c:seeds/sunflower");
        check(helper, ModOil.SUNFLOWER_CROP.get().defaultBlockState().is(BlockTags.CROPS), "and the plant a crop");

        // sown in farmland, as a player sows it; bone meal brings it to a bud, and then into vanilla's sunflower
        BlockPos field = new BlockPos(1, 1, 1);
        BlockPos roofed = new BlockPos(3, 1, 1);
        for (BlockPos soil : List.of(field, roofed)) {
            helper.setBlock(soil, Blocks.FARMLAND);
        }
        helper.setBlock(roofed.above(2), Blocks.STONE); // no room for the head
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 aside = helper.absoluteVec(new Vec3(0.5, 8, 0.5));
        player.snapTo(aside.x, aside.y, aside.z);
        for (BlockPos soil : List.of(field, roofed)) {
            ItemStack hand = new ItemStack(ModOil.SUNFLOWER_SEEDS.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, hand);
            player.gameMode.useItemOn(player, level, hand, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(soil)), Direction.UP, helper.absolutePos(soil), false));
            check(helper, helper.getBlockState(soil.above()).is(ModOil.SUNFLOWER_CROP.get()) && hand.isEmpty(), "a seed is sown");
        }
        level.getServer().getPlayerList().remove(player);
        var crop = ModOil.SUNFLOWER_CROP.get();
        for (int stage = 1; stage < SunflowerCropBlock.BLOOM; stage++) {
            for (BlockPos soil : List.of(field, roofed)) {
                BlockState state = helper.getBlockState(soil.above());
                crop.performBonemeal(level, level.getRandom(), helper.absolutePos(soil.above()), state, BonemealSource.INTERACTION);
                check(helper, helper.getBlockState(soil.above()).getValue(SunflowerCropBlock.AGE) == stage, "a stage a bone meal: " + stage);
            }
        }
        BlockState bud = helper.getBlockState(roofed.above());
        check(helper, !crop.isValidBonemealTarget(level, helper.absolutePos(roofed.above()), bud, BonemealSource.INTERACTION),
                "a bud with no room above takes no bone meal");
        crop.performBonemeal(level, level.getRandom(), helper.absolutePos(field.above()), helper.getBlockState(field.above()), BonemealSource.INTERACTION);
        BlockState lower = helper.getBlockState(field.above());
        BlockState upper = helper.getBlockState(field.above(2));
        check(helper, lower.is(Blocks.SUNFLOWER) && lower.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER
                && upper.is(Blocks.SUNFLOWER) && upper.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER,
                "the bud opens into a whole sunflower: " + lower + " / " + upper);
        check(helper, bud.getCloneItemStack(level, helper.absolutePos(roofed.above()), false).is(ModOil.SUNFLOWER_SEEDS.get()),
                "picking the plant gives a seed");
        helper.succeed();
    }

    /**
     * The workTime setting makes a job longer or shorter, not dearer: a Distiller's 120 ticks at 60 FE/t cost about the
     * same at a half, twice and ten times the time (they cost twice, four and ten times as much); energyUse still counts.
     */
    private static void slowerJobCostsNoMore(GameTestHelper helper) {
        int job = 60 * 120;
        for (double time : new double[] {0.5, 2.0, 4.0, 10.0}) {
            int cost = MachineTuning.workEnergy(60, 1.0, time) * MachineTuning.scale(120, time);
            check(helper, Math.abs(cost - job) <= job / 50, "at " + time + " times the time a job costs " + cost + ", not about " + job);
        }
        check(helper, MachineTuning.workEnergy(60, 2.0, 2.0) * MachineTuning.scale(120, 2.0) == 2 * job,
                "twice the energy use still doubles it, however long");
        // paid tick by tick with the fractions carried, the job costs its due at any pair of settings, where rounding
        // each tick on its own made a Stone Maker's cobblestone cost five times as much at 0.1 and 10 (r14)
        for (double[] setting : new double[][] {{0.1, 10}, {1, 8}, {0.3, 7}, {2.5, 0.4}, {0.1, 1}, {10, 0.1}}) {
            for (int[] machine : new int[][] {{20, 20}, {10, 20}, {60, 120}, {15, 200}}) {
                double exact = MachineTuning.workEnergyExact(machine[0], setting[0], setting[1]);
                double owed = 0;
                int paid = 0;
                int ticks = MachineTuning.scale(machine[1], setting[1]);
                for (int t = 0; t < ticks; t++) {
                    int due = MachineTuning.due(owed, exact);
                    owed += exact - due;
                    paid += due;
                }
                double fair = machine[0] * setting[0] * ticks / setting[1];
                check(helper, Math.abs(paid - fair) < 1.0, machine[0] + " FE/t x " + machine[1] + " at energyUse " + setting[0]
                        + ", workTime " + setting[1] + " pays " + paid + ", not " + fair);
            }
        }
        helper.succeed();
    }

    /**
     * A world's own settings from before 26.3 (serverconfig/nookworks-server.toml) are copied to syncedconfig/, where
     * NeoForge 26.3 reads them; never over a copy already there, nor when there is none to move (r14). In a folder of its
     * own, not the test world's, whose settings are in use.
     */
    private static void worldSettingsMoveToSyncedConfig(GameTestHelper helper) {
        try {
            java.nio.file.Path world = java.nio.file.Files.createTempDirectory("nookworks-world");
            java.nio.file.Path old = world.resolve("serverconfig").resolve("nookworks-server.toml");
            java.nio.file.Path now = world.resolve("syncedconfig").resolve("nookworks-server.toml");
            check(helper, !dev.nookworks.WorldConfigMove.move(world) && !java.nio.file.Files.exists(now), "nothing to move, nothing made");
            java.nio.file.Files.createDirectories(old.getParent());
            java.nio.file.Files.writeString(old, "[machines]\nenergyUse = 2.0\n");
            check(helper, dev.nookworks.WorldConfigMove.move(world) && java.nio.file.Files.readString(now).contains("energyUse = 2.0"),
                    "the old file is copied across");
            check(helper, !java.nio.file.Files.exists(old) && java.nio.file.Files.exists(old.resolveSibling("nookworks-server.toml.moved-to-syncedconfig")),
                    "and set aside, so it is not copied again");
            java.nio.file.Files.writeString(old, "[machines]\nenergyUse = 3.0\n");
            check(helper, !dev.nookworks.WorldConfigMove.move(world) && java.nio.file.Files.readString(now).contains("energyUse = 2.0"),
                    "and never over a copy already there");
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        helper.succeed();
    }

    private static TagKey<Item> itemTag(String id) {
        return TagKey.create(Registries.ITEM, Identifier.parse(id));
    }

    private BiodieselTests() {}
}
