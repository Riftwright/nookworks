package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.List;

import dev.nookworks.mercury.MercuryRecipes;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.quench.Quenching;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Tempering Velarium (the user, 2026-10-12): heated red-hot, too hot to hold, quenched in a cauldron of oil by hopper or
 * dispenser within half a minute; the gear made of the tempered ingot on Reinforced Rods; Velarium two and a half times
 * as common as it was.
 */
final class QuenchingTests {
    static void register() {
        test("velarium_heats_red_hot_one_at_a_time", QuenchingTests::heatsRedHot);
        test("a_red_hot_ingot_cannot_be_held_and_cools", QuenchingTests::cannotBeHeld);
        test("a_red_hot_ingot_in_hand_earns_too_hot", QuenchingTests::tooHot);
        test("oil_cauldron_quenches_what_a_hopper_feeds_it", 60, QuenchingTests::quenchesFromAHopper);
        test("a_dispenser_dips_a_red_hot_ingot", 40, QuenchingTests::dispenserDips);
        test("velarium_gear_takes_tempered_ingots_and_reinforced_rods", QuenchingTests::gearTakesTempered);
        test("a_cached_bath_sees_the_cauldron_filled", QuenchingTests::cachedBathSeesItFilled);
        test("a_cached_bath_spends_the_oil_there_is", QuenchingTests::cachedBathSpendsTheOilThereIs);
        test("the_electric_furnace_heats_an_ingot_that_cools_in_it", 300, QuenchingTests::electricFurnaceWaitsForItToCool);
    }

    private static ItemStack redHot(ServerLevel level, long age) {
        return Quenching.stamp(new ItemStack(ModItems.RED_HOT_VELARIUM_INGOT.get()), level.getGameTime() - age);
    }

    /** Furnace and blast furnace both heat an ingot, stamped with the time it came out; it does not stack. */
    private static void heatsRedHot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SingleRecipeInput ingot = new SingleRecipeInput(new ItemStack(ModItems.VELARIUM_INGOT.get()));
        for (var type : List.of(RecipeType.SMELTING, RecipeType.BLASTING)) {
            ItemStack out = level.recipeAccess().getRecipeFor(type, ingot, level).map(r -> r.value().assemble(ingot)).orElse(ItemStack.EMPTY);
            Long at = out.get(ModDataComponents.HEATED_AT.get());
            check(helper, out.is(ModItems.RED_HOT_VELARIUM_INGOT.get()) && at != null && Math.abs(at - level.getGameTime()) <= 1,
                    type + " heats it red-hot, stamped now: " + out + " at " + at);
            check(helper, Quenching.isHot(out, level.getGameTime()), "and it is hot");
        }
        check(helper, new ItemStack(ModItems.RED_HOT_VELARIUM_INGOT.get()).getMaxStackSize() == 1, "one at a time: it does not stack");
        // one that cooled where nothing looked at it (a chest, a hopper) heats again; one still hot does not (r14)
        for (var type : List.of(RecipeType.SMELTING, RecipeType.BLASTING)) {
            SingleRecipeInput cooled = new SingleRecipeInput(redHot(level, Quenching.HOT_TICKS + 1));
            ItemStack again = level.recipeAccess().getRecipeFor(type, cooled, level).map(r -> r.value().assemble(cooled)).orElse(ItemStack.EMPTY);
            check(helper, again.is(ModItems.RED_HOT_VELARIUM_INGOT.get()) && Quenching.isHot(again, level.getGameTime()),
                    type + " heats a cooled one again: " + again);
            check(helper, level.recipeAccess().getRecipeFor(type, new SingleRecipeInput(redHot(level, 0)), level).isEmpty(),
                    type + " does not take one still hot");
        }
        var chance = MercuryRecipes.distillation(new ItemStack(ModItems.TRACE_AMALGAM.get())).velariumChance();
        var metal = MercuryRecipes.distillation(new ItemStack(ModItems.GOLD_AMALGAM.get())).velariumChance();
        check(helper, Math.abs(chance - 0.002F) < 1e-6 && Math.abs(metal - 0.001F) < 1e-6,
                "Velarium two and a half times as often: rock's amalgam " + chance + ", a metal's " + metal);
        helper.succeed();
    }

    /**
     * Burned by a red-hot ingot, a survival player has "Too Hot to Handle" (a viewer's idea, 2026-10-23), an advancement
     * hidden in the tab until had.
     */
    private static void tooHot(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var advancement = server.getAdvancements().get(dev.nookworks.Nookworks.id("too_hot"));
        check(helper, advancement != null && advancement.value().display().map(d -> d.hidden()).orElse(false), "a hidden advancement");
        net.minecraft.server.level.ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        var aside = helper.absoluteVec(new Vec3(0.5, 8, 0.5));
        player.snapTo(aside.x, aside.y, aside.z);
        player.getAbilities().mayfly = true; // held up clear of the test: one falling to the void hangs the batch
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.connection.markClientLoaded(); // a player just joined cannot be hurt until its client says it has loaded
        check(helper, !player.getAdvancements().getOrStartProgress(advancement).isDone(), "not had before");
        player.getInventory().add(redHot(helper.getLevel(), 0));
        player.getInventory().tick();
        check(helper, player.getAdvancements().getOrStartProgress(advancement).isDone(), "had once the ingot burns");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * In a survival player's inventory it burns them and is dropped; on the ground it is not picked up while hot; past
     * half a minute it cools into a plain ingot, which is.
     */
    private static void cannotBeHeld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new Vec3(2.5, 2, 2.5)));
        float health = player.getHealth();
        player.getInventory().add(redHot(level, 0));
        player.getInventory().tick();
        check(helper, player.getInventory().countItem(ModItems.RED_HOT_VELARIUM_INGOT.get()) == 0, "it does not stay in the hand");
        check(helper, player.getHealth() < health, "it burns: " + player.getHealth() + " of " + health);
        var burn = player.getLastDamageSource();
        check(helper, burn != null && burn.is(Quenching.BURN) && burn.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                && burn.getLocalizedDeathMessage(player).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().equals("death.attack.nookworks.red_hot_ingot"), "its own burn, and its own death message: " + burn);
        var dropped = level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(3),
                e -> e.getItem().is(ModItems.RED_HOT_VELARIUM_INGOT.get()));
        check(helper, dropped.size() == 1, "it is dropped: " + dropped.size());
        ItemEntity hot = dropped.getFirst();
        hot.setNoPickUpDelay();
        hot.playerTouch(player);
        check(helper, hot.isAlive() && player.getInventory().countItem(ModItems.RED_HOT_VELARIUM_INGOT.get()) == 0,
                "and not picked up again while it glows");

        hot.setItem(redHot(level, Quenching.HOT_TICKS + 1));
        hot.tick();
        check(helper, hot.getItem().is(ModItems.VELARIUM_INGOT.get()), "past half a minute it has cooled to a plain ingot: " + hot.getItem());
        hot.setNoPickUpDelay();
        hot.playerTouch(player);
        check(helper, player.getInventory().countItem(ModItems.VELARIUM_INGOT.get()) == 1, "which is picked up");

        // one made by a command has no time on it: it starts cooling as it lies on the ground
        hot.setItem(new ItemStack(ModItems.RED_HOT_VELARIUM_INGOT.get()));
        hot.tick();
        Long at = hot.getItem().get(ModDataComponents.HEATED_AT.get());
        check(helper, at != null && Math.abs(at - level.getGameTime()) <= 1, "an unstamped one is stamped on the ground: " + at);
        helper.succeed();
    }

    /**
     * A bucket of oil fills a cauldron (three levels); a hopper feeds it a red-hot ingot, which comes out tempered into
     * the chest below and spends a level; a cooled one comes out a plain ingot and spends none; the last level spent, it
     * is an empty cauldron. A simulated insert changes nothing; a pipe fills an empty cauldron with oil too.
     */
    private static void quenchesFromAHopper(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cauldron = new BlockPos(1, 2, 1);
        helper.setBlock(cauldron.below(), Blocks.CHEST);
        helper.setBlock(cauldron, Blocks.CAULDRON);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bucket = new ItemStack(ModOil.VEGETABLE_OIL_BUCKET.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        helper.getBlockState(cauldron).useItemOn(bucket, level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(cauldron)), Direction.UP, helper.absolutePos(cauldron), false));
        check(helper, helper.getBlockState(cauldron).is(ModBlocks.VEGETABLE_OIL_CAULDRON.get())
                && helper.getBlockState(cauldron).getValue(LayeredCauldronBlock.LEVEL) == 3
                && player.getMainHandItem().is(Items.BUCKET), "a bucket of oil fills the cauldron: " + helper.getBlockState(cauldron));

        var bath = Quenching.handler(level, helper.absolutePos(cauldron));
        try (Transaction tx = Transaction.openRoot()) {
            check(helper, bath.insert(ItemResource.of(redHot(level, 0)), 1, tx) == 1, "it would take a red-hot ingot");
        } // not committed
        check(helper, helper.getBlockState(cauldron).getValue(LayeredCauldronBlock.LEVEL) == 3
                && helper.getBlockEntity(cauldron.below(), ChestBlockEntity.class).isEmpty(), "a simulated insert changes nothing");
        check(helper, !bath.isValid(0, ItemResource.of(new ItemStack(ModItems.VELARIUM_INGOT.get()))), "a cold ingot is not taken");

        helper.setBlock(cauldron.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        var hopper = helper.getBlockEntity(cauldron.above(), HopperBlockEntity.class);
        hopper.setItem(0, redHot(level, 0));
        helper.runAfterDelay(12, () -> {
            var chest = helper.getBlockEntity(cauldron.below(), ChestBlockEntity.class);
            check(helper, chest.countItem(ModItems.TEMPERED_VELARIUM_INGOT.get()) == 1, "it comes out tempered, into the chest below");
            check(helper, helper.getBlockState(cauldron).getValue(LayeredCauldronBlock.LEVEL) == 2, "a level of oil spent");
            hopper.setItem(0, redHot(level, Quenching.HOT_TICKS + 5));
            helper.runAfterDelay(12, () -> {
                check(helper, chest.countItem(ModItems.VELARIUM_INGOT.get()) == 1
                        && helper.getBlockState(cauldron).getValue(LayeredCauldronBlock.LEVEL) == 2,
                        "a cooled one comes out a plain ingot, and spends no oil");
                helper.setBlock(cauldron, ModBlocks.VEGETABLE_OIL_CAULDRON.get().defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 1));
                hopper.setItem(0, redHot(level, 0));
                helper.runAfterDelay(12, () -> {
                    check(helper, helper.getBlockState(cauldron).is(Blocks.CAULDRON) && chest.countItem(ModItems.TEMPERED_VELARIUM_INGOT.get()) == 2,
                            "the last level spent, an empty cauldron: " + helper.getBlockState(cauldron));
                    var pipe = level.getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(cauldron), Direction.UP);
                    try (Transaction tx = Transaction.openRoot()) {
                        check(helper, pipe != null && pipe.insert(FluidResource.of(OilFluids.SOURCE.get()), 1000, tx) == 1000, "a pipe pours oil in");
                        tx.commit();
                    }
                    check(helper, helper.getBlockState(cauldron).is(ModBlocks.VEGETABLE_OIL_CAULDRON.get()), "and it is a cauldron of oil");
                    helper.succeed();
                });
            });
        });
    }

    /** A dispenser facing a cauldron of oil with nothing under it dips the ingot; it comes out tempered onto the rim. */
    private static void dispenserDips(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cauldron = new BlockPos(3, 2, 3);
        BlockPos dispenser = cauldron.west();
        helper.setBlock(cauldron, ModBlocks.VEGETABLE_OIL_CAULDRON.get().defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        helper.setBlock(dispenser, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        helper.getBlockEntity(dispenser, DispenserBlockEntity.class).setItem(0, redHot(level, 0));
        helper.setBlock(dispenser.west(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(10, () -> {
            check(helper, helper.getBlockEntity(dispenser, DispenserBlockEntity.class).isEmpty(), "the dispenser let it go");
            check(helper, helper.getBlockState(cauldron).getValue(LayeredCauldronBlock.LEVEL) == 2, "into the oil");
            var out = level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(cauldron)).inflate(1.5),
                    e -> e.getItem().is(ModItems.TEMPERED_VELARIUM_INGOT.get()));
            check(helper, out.size() == 1, "tempered, on the rim: " + out.size());
            helper.succeed();
        });
    }

    /**
     * Velarium tools are made of tempered ingots on Reinforced Rods (a breeze rod in two brass ingots) and mended with
     * tempered ingots; plain ingots and sticks make none. The block still takes plain ingots.
     */
    private static void gearTakesTempered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack t = new ItemStack(ModItems.TEMPERED_VELARIUM_INGOT.get());
        ItemStack v = new ItemStack(ModItems.VELARIUM_INGOT.get());
        ItemStack rod = new ItemStack(ModItems.REINFORCED_ROD.get());
        ItemStack none = ItemStack.EMPTY;
        CraftingInput tempered = CraftingInput.of(3, 3, List.of(t, t, t, none, rod, none, none, rod, none));
        CraftingInput plain = CraftingInput.of(3, 3, List.of(v, v, v, none, new ItemStack(Items.STICK), none, none, new ItemStack(Items.STICK), none));
        var recipes = level.getServer().getRecipeManager();
        check(helper, recipes.getRecipeFor(RecipeType.CRAFTING, tempered, level).map(r -> r.value().assemble(tempered))
                .filter(s -> s.is(ModItems.VELARIUM_PICKAXE.get())).isPresent(), "tempered ingots on reinforced rods make a pickaxe");
        check(helper, recipes.getRecipeFor(RecipeType.CRAFTING, plain, level).map(r -> r.value().assemble(plain))
                .filter(s -> s.is(ModItems.VELARIUM_PICKAXE.get())).isEmpty(), "plain ingots on sticks do not");
        CraftingInput rodGrid = CraftingInput.of(2, 2, List.of(new ItemStack(Items.BREEZE_ROD), new ItemStack(ModItems.BRASS_INGOT.get()),
                new ItemStack(ModItems.BRASS_INGOT.get()), none));
        check(helper, recipes.getRecipeFor(RecipeType.CRAFTING, rodGrid, level).map(r -> r.value().assemble(rodGrid))
                .filter(s -> s.is(ModItems.REINFORCED_ROD.get())).isPresent(), "a breeze rod in two brass ingots is a reinforced rod");
        var repair = new ItemStack(ModItems.VELARIUM_PICKAXE.get()).get(DataComponents.REPAIRABLE);
        var armour = new ItemStack(ModItems.VELARIUM_CHESTPLATE.get()).get(DataComponents.REPAIRABLE);
        check(helper, repair != null && repair.isValidRepairItem(t) && !repair.isValidRepairItem(v)
                && armour != null && armour.isValidRepairItem(t) && !armour.isValidRepairItem(v), "mended with tempered ingots, not plain");
        CraftingInput block = CraftingInput.of(3, 3, List.of(v, v, v, v, v, v, v, v, v));
        check(helper, recipes.getRecipeFor(RecipeType.CRAFTING, block, level).map(r -> r.value().assemble(block))
                .filter(s -> s.is(ModItems.VELARIUM_BLOCK.get())).isPresent(), "the block is still of plain ingots");
        helper.succeed();
    }

    /** A capability cache another mod holds on an empty cauldron learns it is a bath once it is filled (r14, R14-CXC-002). */
    private static void cachedBathSeesItFilled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
        var cache = BlockCapabilityCache.create(Capabilities.Item.BLOCK, level, pos, Direction.UP);
        check(helper, cache.getCapability() == null, "an empty cauldron takes no items");
        level.setBlockAndUpdate(pos, ModBlocks.VEGETABLE_OIL_CAULDRON.get().defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        check(helper, cache.getCapability() != null, "filled with oil, the cached connection finds the bath");
        level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
        check(helper, cache.getCapability() == null, "and emptied, loses it again");
        helper.succeed();
    }

    /**
     * A handler cached while others quench, fill and drain spends what is in the cauldron then, not what was there when
     * it was first asked: two quenches elsewhere, then the cached one's - three levels, an empty cauldron (it put two back).
     */
    private static void cachedBathSpendsTheOilThereIs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlockAndUpdate(pos, ModBlocks.VEGETABLE_OIL_CAULDRON.get().defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        var cache = BlockCapabilityCache.create(Capabilities.Item.BLOCK, level, pos, Direction.UP);
        check(helper, cache.getCapability() != null, "a bath takes items");
        Quenching.dip(level, pos, redHot(level, 0));
        Quenching.dip(level, pos, redHot(level, 0));
        check(helper, level.getBlockState(pos).getValue(LayeredCauldronBlock.LEVEL) == 1, "one level left");
        try (Transaction tx = Transaction.openRoot()) {
            check(helper, cache.getCapability().insert(ItemResource.of(redHot(level, 0)), 1, tx) == 1, "the cached one takes the third");
            tx.commit();
        }
        check(helper, level.getBlockState(pos).is(Blocks.CAULDRON), "three quenches spend three levels: " + level.getBlockState(pos));
        try (Transaction tx = Transaction.openRoot()) {
            check(helper, cache.getCapability() == null || cache.getCapability().insert(ItemResource.of(redHot(level, 0)), 1, tx) == 0,
                    "and with no oil it takes nothing more");
        }
        // two faces in one transaction (a pipe network's) share the cauldron's one bath: the last level is spent once
        level.setBlockAndUpdate(pos, ModBlocks.VEGETABLE_OIL_CAULDRON.get().defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 1));
        try (Transaction tx = Transaction.openRoot()) {
            var up = level.getCapability(Capabilities.Item.BLOCK, pos, Direction.UP);
            var side = level.getCapability(Capabilities.Item.BLOCK, pos, Direction.NORTH);
            check(helper, up.insert(ItemResource.of(redHot(level, 0)), 1, tx) == 1
                    && side.insert(ItemResource.of(redHot(level, 0)), 1, tx) == 0, "one level, one quench, from whichever face");
        }
        helper.succeed();
    }

    /**
     * An Electric Furnace given an ingot still hot waits, and heats it once it has cooled: it does not keep the "no
     * recipe" it found for the hot one (r14 re-check: it kept it for as long as the stack stayed).
     */
    private static void electricFurnaceWaitsForItToCool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.CHEST); // what it smelts goes out below it
        helper.setBlock(pos, ModBlocks.ELECTRIC_FURNACE.get());
        var furnace = helper.getBlockEntity(pos, dev.nookworks.machine.ElectricFurnaceBlockEntity.class);
        furnace.energy().set((int) furnace.energy().getCapacityAsLong());
        furnace.setItem(dev.nookworks.machine.ElectricFurnaceBlockEntity.INPUT, redHot(level, Quenching.HOT_TICKS - 20));
        helper.succeedWhen(() -> {
            ItemStack out = helper.getBlockEntity(pos.below(), ChestBlockEntity.class).getItem(0);
            check(helper, out.is(ModItems.RED_HOT_VELARIUM_INGOT.get()) && Quenching.isHot(out, level.getGameTime()),
                    "it heats the cooled ingot again: " + out);
        });
    }

    private QuenchingTests() {}
}
