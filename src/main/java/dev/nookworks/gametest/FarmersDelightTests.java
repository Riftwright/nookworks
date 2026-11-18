package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksStartupConfig;
import dev.nookworks.compat.farmersdelight.FarmersDelight;
import dev.nookworks.compat.farmersdelight.FdKitchen;
import dev.nookworks.cooking.ElectricStoveBlock;
import dev.nookworks.cooking.ElectricStoveBlockEntity;
import dev.nookworks.cooking.IronPotBlock;
import dev.nookworks.cooking.IronPotBlockEntity;
import dev.nookworks.cooking.Plating;
import dev.nookworks.cooking.PotRecipe;
import dev.nookworks.cooking.Serving;
import dev.nookworks.experience.Move;
import dev.nookworks.port.test.GameTestHelper;
import dev.nookworks.port.transfer.ItemResource;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.component.DataComponents;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * Farmer's Delight's kitchen and ours as one (compat/farmersdelight, the owner's order of 2026-11-15): heat both ways,
 * either pot making either mod's dishes, twins taken for each other, the owner's settled rules kept, its dishes laid on
 * our tables. Registered only when Farmer's Delight is installed (drop its jar into {@code run-gametest/mods/});
 * without it the suite is as it was. The data check that no two recipes fight in one place, its Cooking Pot and Cutting
 * Board among the places with it, runs in every run (RecipeCrossingTests).
 */
final class FarmersDelightTests {
    private FarmersDelightTests() {}

    static void register() {
        NookworksGameTests.test("fd_heat_goes_both_ways_by_tags", FarmersDelightTests::heatTags);
        NookworksGameTests.test("fd_our_pot_cooks_on_its_stove", 800, FarmersDelightTests::ourPotOnItsStove);
        NookworksGameTests.test("fd_its_pot_cooks_on_our_electric_stove", 800, FarmersDelightTests::itsPotOnOurStove);
        NookworksGameTests.test("fd_its_skillet_cooks_on_our_electric_stove", 800, FarmersDelightTests::skilletOnOurStove);
        NookworksGameTests.test("fd_our_dish_in_its_cooking_pot", 800, FarmersDelightTests::ourDishInItsPot);
        NookworksGameTests.test("fd_its_dish_in_our_iron_pot", 800, FarmersDelightTests::itsDishInOurPot);
        NookworksGameTests.test("fd_every_dish_crosses_to_the_other_pot", FarmersDelightTests::everyDishCrosses);
        NookworksGameTests.test("fd_twins_go_in_each_others_recipes", FarmersDelightTests::twins);
        NookworksGameTests.test("fd_an_egg_has_one_dish_at_a_furnace", FarmersDelightTests::oneEgg);
        NookworksGameTests.test("fd_the_bakery_holds_and_takes_its_milk", FarmersDelightTests::bakery);
        NookworksGameTests.test("fd_its_dishes_are_laid_on_our_tables", FarmersDelightTests::tables);
        NookworksGameTests.test("fd_its_knife_and_board_take_our_things", FarmersDelightTests::knife);
    }

    private static Block block(String path) {
        return BuiltInRegistries.BLOCK.get(FarmersDelight.id(path));
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.get(FarmersDelight.id(path));
    }

    /** Its Stove, lit, at {@code pos}. */
    private static void stove(GameTestHelper helper, BlockPos pos) {
        BlockState state = block("stove").defaultBlockState();
        helper.setBlock(pos, state.hasProperty(BlockStateProperties.LIT) ? state.setValue(BlockStateProperties.LIT, true) : state);
    }

    /** Its Cooking Pot's inventory (its public getInventory), as the pot's own hopper face would not take a test's say. */
    private static IItemHandlerModifiable inventory(GameTestHelper helper, BlockPos pos) {
        Object entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        try {
            return (IItemHandlerModifiable) entity.getClass().getMethod("getInventory").invoke(entity);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Farmer's Delight's Cooking Pot has no getInventory", e);
        }
    }

    /** The dish waiting in its Cooking Pot (its meal slot). */
    private static ItemStack meal(IItemHandler inventory) {
        return inventory.getStackInSlot(6);
    }

    /**
     * Both ways by tag: our Electric Stove, lit, is among its heat sources (and so are a lit furnace and a Generator, all
     * of #nookworks:heats_pots); its Stove heats our pot; a campfire still asks for our Grill.
     */
    private static void heatTags(GameTestHelper helper) {
        BlockState lit = ModCooking.ELECTRIC_STOVE.get().defaultBlockState().setValue(ElectricStoveBlock.LIT, true);
        check(helper, lit.is(FarmersDelight.HEAT_SOURCES), "our Electric Stove heats its pots");
        check(helper, block("stove").defaultBlockState().is(IronPotBlock.HEAT), "its Stove is in #nookworks:heats_pots");
        BlockPos pot = new BlockPos(1, 2, 1);
        helper.setBlock(pot.below(), Blocks.CAMPFIRE);
        helper.setBlock(pot, ModCooking.IRON_POT.get());
        check(helper, !IronPotBlock.heated(helper.getLevel(), helper.absolutePos(pot)), "a pot straight on a campfire is not heated: it wants the Grill");
        helper.setBlock(pot.below(), Blocks.LAVA_CAULDRON);
        check(helper, IronPotBlock.heated(helper.getLevel(), helper.absolutePos(pot)), "a lava cauldron, its heat source, heats our pot too");
        helper.succeed();
    }

    /** Our Iron Pot on its Stove boils rice. */
    private static void ourPotOnItsStove(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        stove(helper, pos.below());
        helper.setBlock(pos, ModCooking.IRON_POT.get());
        var pot = helper.getBlockEntity(pos, IronPotBlockEntity.class);
        pot.addLiquid(PotRecipe.Liquid.WATER, 250);
        check(helper, Move.insert(pot.itemHandler(), ItemResource.of(ModCrops.WHITE_RICE.get()), 1) == 1, "white rice goes in");
        check(helper, IronPotBlock.heated(helper.getLevel(), helper.absolutePos(pos)), "its Stove heats our pot");
        helper.succeedWhen(() -> check(helper, pot.dish().is(ModCooking.COOKED_RICE.get()), "the rice is boiled: " + pot.dish()));
    }

    /**
     * Its Cooking Pot on our Electric Stove: dark and costing nothing while the pot is empty; with two tomatoes in, the
     * ring lights, the pot cooks its Tomato Sauce, and the power is drawn.
     */
    private static void itsPotOnOurStove(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos.below(), ModCooking.ELECTRIC_STOVE.get());
        helper.setBlock(pos, block("cooking_pot"));
        var stove = helper.getBlockEntity(pos.below(), ElectricStoveBlockEntity.class);
        stove.energy().set(ElectricStoveBlockEntity.CAPACITY);
        IItemHandlerModifiable inventory = inventory(helper, pos);
        helper.runAfterDelay(20, () -> {
            check(helper, !helper.getBlockState(pos.below()).getValue(ElectricStoveBlock.LIT)
                    && stove.energy().getAmountAsLong() == ElectricStoveBlockEntity.CAPACITY, "an empty pot is not heated, nor paid for");
            inventory.setStackInSlot(0, new ItemStack(item("tomato")));
            inventory.setStackInSlot(1, new ItemStack(item("tomato")));
            boolean[] seenLit = {false};
            helper.succeedWhen(() -> {
                seenLit[0] |= helper.getBlockState(pos.below()).getValue(ElectricStoveBlock.LIT);
                check(helper, seenLit[0], "the ring lights under a pot with something to cook");
                check(helper, meal(inventory).is(item("tomato_sauce")), "its pot cooks over our stove: " + meal(inventory));
                check(helper, stove.energy().getAmountAsLong() < ElectricStoveBlockEntity.CAPACITY, "and the power is drawn");
            });
        });
    }

    /** Its Skillet on our Electric Stove: the ring lights while it holds a raw beef, and the beef is cooked. */
    private static void skilletOnOurStove(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos.below(), ModCooking.ELECTRIC_STOVE.get());
        helper.setBlock(pos, block("skillet"));
        var stove = helper.getBlockEntity(pos.below(), ElectricStoveBlockEntity.class);
        stove.energy().set(ElectricStoveBlockEntity.CAPACITY);
        IItemHandlerModifiable inventory = inventory(helper, pos);
        inventory.setStackInSlot(0, new ItemStack(Items.BEEF));
        boolean[] seenLit = {false};
        helper.succeedWhen(() -> {
            seenLit[0] |= helper.getBlockState(pos.below()).getValue(ElectricStoveBlock.LIT);
            check(helper, seenLit[0], "the ring lights under a skillet with something to cook");
            boolean out = !helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(pos)).inflate(3), e -> e.getItem().is(Items.COOKED_BEEF)).isEmpty();
            check(helper, inventory.getStackInSlot(0).is(Items.COOKED_BEEF) || out, "the beef is cooked: " + inventory.getStackInSlot(0));
        });
    }

    /** Our Udon in its Cooking Pot, on its Stove: the same things, two noodles and a dried kelp, make two bowls. */
    private static void ourDishInItsPot(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        stove(helper, pos.below());
        helper.setBlock(pos, block("cooking_pot"));
        IItemHandlerModifiable inventory = inventory(helper, pos);
        inventory.setStackInSlot(0, new ItemStack(ModKitchen.RAW_UDON.get()));
        inventory.setStackInSlot(1, new ItemStack(ModKitchen.RAW_UDON.get()));
        inventory.setStackInSlot(2, new ItemStack(Items.DRIED_KELP));
        helper.succeedWhen(() -> check(helper, meal(inventory).is(ModKitchen.UDON.get()) && meal(inventory).getCount() == 2,
                "our Udon from its pot: " + meal(inventory)));
    }

    /** Its Tomato Sauce in our Iron Pot's water, on its Stove; its Fried Rice is the Frying Pan's, in oil. */
    private static void itsDishInOurPot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RecipeHolder<PotRecipe> rice = PotRecipe.find(level, List.of(new ItemStack(item("rice")), new ItemStack(Items.EGG),
                new ItemStack(Items.CARROT), new ItemStack(item("onion"))), PotRecipe.Liquid.OIL, 250, true);
        check(helper, rice != null && rice.value().result().item().value() == item("fried_rice"),
                "its Fried Rice fries in our Frying Pan: " + (rice == null ? null : rice.id()));
        BlockPos pos = new BlockPos(1, 2, 1);
        stove(helper, pos.below());
        helper.setBlock(pos, ModCooking.IRON_POT.get());
        var pot = helper.getBlockEntity(pos, IronPotBlockEntity.class);
        pot.addLiquid(PotRecipe.Liquid.WATER, 250);
        check(helper, Move.insert(pot.itemHandler(), ItemResource.of(item("tomato")), 2) == 2, "two tomatoes go in");
        helper.succeedWhen(() -> check(helper, pot.dish().is(item("tomato_sauce")), "its Tomato Sauce from our pot: " + pot.dish()));
    }

    /**
     * Every dish our Iron Pot or Frying Pan makes is a Cooking Pot recipe too, and every Cooking Pot dish an Iron Pot or
     * Frying Pan recipe: what was left out is only what the crossing said why of.
     */
    private static void everyDishCrosses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var manager = level.getRecipeManager();
        RecipeType<?> cooking = FarmersDelight.recipeType(FarmersDelight.COOKING);
        List<String> missing = new ArrayList<>();
        String left = String.join("; ", FdKitchen.last().leftOut());
        for (RecipeHolder<PotRecipe> holder : manager.getAllRecipesFor(PotRecipe.TYPE.get())) {
            if (FdKitchen.made(holder.id())
                    || !new ItemStack(holder.value().result().item()).has(DataComponents.FOOD)) {
                continue;
            }
            ResourceLocation twin = Nookworks.id(FdKitchen.PREFIX + "cooking_pot/" + holder.id().getNamespace() + "/" + holder.id().getPath());
            if (manager.byKey(twin).isEmpty() && !left.contains(twin.toString())) {
                missing.add(holder.id() + " (no Cooking Pot recipe)");
            }
        }
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            if (holder.value().getType() != cooking || holder.id().getNamespace().equals(Nookworks.MODID)) {
                continue;
            }
            ResourceLocation twin = Nookworks.id(FdKitchen.PREFIX + "pot/" + holder.id().getNamespace() + "/" + holder.id().getPath());
            if (manager.byKey(twin).isEmpty() && !left.contains(holder.id().toString())) {
                missing.add(holder.id() + " (no Iron Pot recipe)");
            }
        }
        check(helper, missing.isEmpty(), "dishes not made in the other mod's pot, with no reason given: " + missing);
        check(helper, FdKitchen.last().intoOurPot().size() >= 25 && FdKitchen.last().intoTheirPot().size() >= 14,
                "most dishes cross: " + FdKitchen.last().intoOurPot().size() + " of its, " + FdKitchen.last().intoTheirPot().size()
                        + " of ours; left out: " + left);
        helper.succeed();
    }

    /** Our Bowl of Rice in its Cod Roll, its Cooked Rice in our onigiri and our Frying Pan's Fried Rice. */
    private static void twins(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack ours = new ItemStack(ModCooking.COOKED_RICE.get());
        ItemStack theirs = new ItemStack(item("cooked_rice"));
        var roll = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                CraftingInput.of(3, 1, List.of(new ItemStack(item("cod_slice")), new ItemStack(item("cod_slice")), ours)), level);
        check(helper, roll.isPresent() && roll.get().value().getResultItem(level.registryAccess()).is(item("cod_roll")),
                "its Cod Roll takes our Bowl of Rice");
        var onigiri = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 1,
                List.of(theirs, new ItemStack(ModItems.SALT.get()), new ItemStack(Items.DRIED_KELP))), level);
        check(helper, onigiri.isPresent() && onigiri.get().value().getResultItem(level.registryAccess()).is(ModCooking.SALT_ONIGIRI.get()),
                "our Salt Onigiri takes its Cooked Rice");
        RecipeHolder<PotRecipe> fried = PotRecipe.find(level, List.of(theirs, new ItemStack(Items.EGG)), PotRecipe.Liquid.OIL, 250, true);
        check(helper, fried != null && fried.value().result().item().value() == ModCooking.FRIED_RICE.get(),
                "our Frying Pan fries its Cooked Rice with an egg");
        check(helper, new ItemStack(item("onion")).is(TagKey.create(Registries.ITEM, ResourceLocation.parse("c:crops/onion")))
                && new ItemStack(ModKitchen.ONION.get()).is(TagKey.create(Registries.ITEM, ResourceLocation.parse("c:crops/onion"))),
                "both onions are c:crops/onion");
        helper.succeed();
    }

    /** An egg in a furnace or a smoker has one dish, its Fried Egg; ours comes from our Frying Pan. */
    private static void oneEgg(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (RecipeType<?> type : List.of(RecipeType.SMELTING, RecipeType.SMOKING, RecipeType.CAMPFIRE_COOKING)) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            List<RecipeHolder<?>> found = (List) level.getRecipeManager().getRecipesFor((RecipeType) type, new SingleRecipeInput(new ItemStack(Items.EGG)), level);
            check(helper, found.size() == 1, "an egg at " + BuiltInRegistries.RECIPE_TYPE.getKey(type) + " has one dish: "
                    + found.stream().map(RecipeHolder::id).toList());
            check(helper, found.get(0).value().getResultItem(level.registryAccess()).is(item("fried_egg")), "its Fried Egg");
        }
        RecipeHolder<PotRecipe> pan = PotRecipe.find(level, List.of(new ItemStack(Items.EGG)), PotRecipe.Liquid.OIL, 25, true);
        check(helper, pan != null && pan.value().result().item().value() == ModCooking.FRIED_EGG.get(), "our pan fries ours");
        helper.succeed();
    }

    /**
     * Its own cake of wheat and its dough baked into bread stay whatever our bakery settings are (the owner,
     * 2026-11-17: Nookworks never makes another mod less convenient); our flour cake takes its Milk Bottles beside it.
     */
    private static void bakery(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var manager = level.getRecipeManager();
        boolean bakery = NookworksStartupConfig.flourBakery();
        check(helper, manager.byKey(FarmersDelight.id("cake_from_milk_bottle")).isPresent(),
                "its cake of wheat is there, bakery or not (bakery " + bakery + ")");
        if (bakery) {
            ItemStack milk = new ItemStack(item("milk_bottle"));
            ItemStack flour = new ItemStack(ModCooking.FLOUR.get());
            var cake = manager.getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, List.of(milk, milk, milk,
                    new ItemStack(Items.SUGAR), new ItemStack(Items.EGG), new ItemStack(Items.SUGAR), flour, flour, flour)), level);
            check(helper, cake.isPresent() && cake.get().value().getResultItem(level.registryAccess()).is(Items.CAKE),
                    "our flour cake with its milk");
        }
        check(helper, manager.byKey(FarmersDelight.id("bread_from_smelting")).isPresent()
                && manager.byKey(FarmersDelight.id("bread_from_smoking")).isPresent(),
                "its dough bakes into bread whatever our bread setting is (wheat bread " + NookworksStartupConfig.vanillaBread() + ")");
        helper.succeed();
    }

    /**
     * Its dishes on our tables: every food of its that is eaten out of a bowl is a bowl dish of ours (set down as a bowl,
     * not on a plate), and nothing else of its is; its Fried Rice is served on a Plate as ours; its small things lie
     * several to a plate.
     */
    private static void tables(GameTestHelper helper) {
        TagKey<Item> bowls = TagKey.create(Registries.ITEM, Nookworks.id("bowl_dishes"));
        List<String> wrong = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(FarmersDelight.MODID)) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            boolean bowl = stack.has(DataComponents.FOOD) && stack.getCraftingRemainingItem().is(Items.BOWL);
            if (bowl != stack.is(bowls)) {
                wrong.add(BuiltInRegistries.ITEM.getKey(item) + (bowl ? " is eaten from a bowl" : " is no bowl dish"));
            }
        }
        check(helper, wrong.isEmpty(), "#nookworks:bowl_dishes and its bowls disagree: " + wrong);
        Plating plated = new ItemStack(item("fried_rice")).getItemHolder().getData(Plating.DATA_MAP);
        check(helper, plated != null && plated.plated().value() == ModKitchen.PLATED_FRIED_RICE.get(), "its Fried Rice is plated as ours");
        Serving dumplings = new ItemStack(item("dumplings")).getItemHolder().getData(Serving.DATA_MAP);
        check(helper, dumplings != null && dumplings.plate() == 3, "three dumplings to a plate");
        helper.succeed();
    }

    /** Its knife takes straw from our ripe rice, and its Cutting Board cuts our Udon Dough and Corn. */
    private static void knife(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockState ripe = ModCrops.RICE_PANICLES.get().defaultBlockState().setValue(BlockStateProperties.AGE_3, 3);
        List<ItemStack> drops = Block.getDrops(ripe, level, pos, null, null, new ItemStack(item("iron_knife")));
        check(helper, drops.stream().anyMatch(s -> s.is(item("straw"))), "a knife takes straw from ripe rice: " + drops);
        List<ItemStack> plain = Block.getDrops(ripe, level, pos, null, null, ItemStack.EMPTY);
        check(helper, plain.stream().noneMatch(s -> s.is(item("straw"))), "a hand takes none");
        var manager = level.getRecipeManager();
        check(helper, manager.byKey(Nookworks.id("farmersdelight/cutting_udon_dough")).isPresent()
                && manager.byKey(Nookworks.id("farmersdelight/cutting_corn")).isPresent(), "the Cutting Board's recipes for our things");
        helper.succeed();
    }

}
