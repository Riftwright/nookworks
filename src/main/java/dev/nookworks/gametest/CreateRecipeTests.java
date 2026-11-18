package dev.nookworks.gametest;

import static dev.nookworks.gametest.CreateTests.createBlock;
import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import dev.nookworks.Nookworks;
import dev.nookworks.compat.create.CreateRecipes;
import dev.nookworks.cooking.HandWork;
import dev.nookworks.machine.CrushingRecipe;
import dev.nookworks.port.test.GameTestHelper;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The two mods' like machines doing each other's work (CreateRecipes): ours among Create's recipes, Create's in our
 * Crusher and Hand Quern, no machine with two recipes for one thing, no gear ground for its full count. Registered only
 * with Create loaded.
 */
final class CreateRecipeTests {
    private CreateRecipeTests() {}

    static void register() {
        NookworksGameTests.test("create_recipes_ours_are_among_creates", CreateRecipeTests::oursAmongCreates);
        NookworksGameTests.test("create_recipes_creates_are_in_our_machines", CreateRecipeTests::createsInOurs);
        NookworksGameTests.test("create_recipes_no_machine_has_two_for_one_thing", CreateRecipeTests::noTwo);
        NookworksGameTests.test("create_millstone_grinds_our_bread_into_breadcrumbs", 200, CreateRecipeTests::millstoneBread);
        NookworksGameTests.test("create_item_drain_empties_our_oil_bottle", 100, CreateRecipeTests::drainBottle);
        if (dev.nookworks.compat.farmersdelight.FarmersDelight.LOADED) {
            NookworksGameTests.test("create_fd_twins_have_one_millstone_recipe", CreateRecipeTests::twins);
        }
    }

    private static RecipeManager recipes(GameTestHelper helper) {
        return helper.getLevel().getServer().getRecipeManager();
    }

    private static ResourceLocation ours(String path) {
        return Nookworks.id(CreateRecipes.PREFIX + path);
    }

    private static void oursAmongCreates(GameTestHelper helper) {
        RecipeManager manager = recipes(helper);
        check(helper, has(manager, ours("milling/minecraft/bread"), AllRecipeTypes.MILLING.getType()), "our quern's bread to breadcrumbs, in the Millstone");
        check(helper, has(manager, ours("milling/nookworks/brown_rice"), AllRecipeTypes.MILLING.getType()), "our polisher's brown rice, in the Millstone");
        check(helper, has(manager, ours("compacting/nookworks/sunflower_seeds"), AllRecipeTypes.COMPACTING.getType()), "our press's seeds, compacted for oil");
        check(helper, has(manager, ours("pressing/nookworks/udon_dough"), AllRecipeTypes.PRESSING.getType()), "our Noodle Maker's dough, under the press");
        check(helper, has(manager, ours("crushing/nookworks/charred_lump"), AllRecipeTypes.CRUSHING.getType()), "our Crusher's charred lump, in Crushing Wheels");
        check(helper, has(manager, ours("filling/nookworks/vegetable_oil_bottle"), AllRecipeTypes.FILLING.getType())
                && has(manager, ours("emptying/nookworks/vegetable_oil_bottle"), AllRecipeTypes.EMPTYING.getType()), "a Spout fills, a Drain empties, our oil bottle");
        check(helper, has(manager, ours("filling/nookworks/liquid_experience_bottle"), AllRecipeTypes.FILLING.getType()), "and our experience bottle");
        check(helper, manager.byKey(ours("filling/minecraft/potion")).isEmpty(), "water's bottle stays Create's");
        check(helper, manager.byKey(ours("milling/minecraft/wheat")).isEmpty(), "wheat stays Create's in the Millstone (its own flour)");
        check(helper, manager.byKey(ours("crushing/minecraft/diamond_pickaxe")).isEmpty(), "no gear in Crushing Wheels for its full count");
        check(helper, manager.byKey(ours("milling/nookworks/white_rice")).isEmpty(), "white rice is not polished again");
        helper.succeed();
    }

    private static boolean has(RecipeManager manager, ResourceLocation id, RecipeType<?> type) {
        return manager.byKey(id).map(holder -> holder.value().getType() == type).orElse(false);
    }

    private static void createsInOurs(GameTestHelper helper) {
        Item crushed = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "crushed_raw_iron"));
        CrushingRecipe raw = CrushingRecipe.of(new ItemStack(Items.RAW_IRON));
        // with a mod's iron dust about (Mekanism, EnderIO), our own grind of raw iron into it comes first
        var dusts = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "dusts/iron"));
        boolean dust = BuiltInRegistries.ITEM.getTagOrEmpty(dusts).iterator().hasNext();
        check(helper, raw != null && (dust ? raw.output().is(dusts) : raw.output().is(crushed)),
                "the Crusher crushes raw iron as Crushing Wheels do, or into a mod's iron dust where there is one: " + (raw == null ? null : raw.output()));
        CrushingRecipe cobble = CrushingRecipe.of(new ItemStack(Items.COBBLESTONE));
        check(helper, cobble != null && cobble.output().is(Items.GRAVEL), "its own grinds stay its own");
        HandWork wheat = HandWork.of(HandWork.MILLING, new ItemStack(Items.WHEAT));
        check(helper, wheat != null && wheat.output().is(dev.nookworks.registry.ModCooking.FLOUR.get()), "the quern's wheat still gives our flour");
        HandWork flower = HandWork.of(HandWork.MILLING, new ItemStack(Items.DANDELION));
        check(helper, flower != null && flower.output().is(Items.YELLOW_DYE), "the quern mills a flower as the Millstone does: " + (flower == null ? null : flower.output()));
        check(helper, HandWork.of(HandWork.MILLING, new ItemStack(Items.COBBLESTONE)) == null, "stone stays the Crusher's work, out of the quern");
        check(helper, CrushingRecipe.of(new ItemStack(Items.DIAMOND_PICKAXE)) != null, "our gear grinding is ours, as before");
        // what Create only gives by chance is not made sure by our machines: it stays the wheels' and the Millstone's
        for (String stone : List.of("crimsite", "ochrum", "veridium", "asurine", "cut_crimsite")) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", stone));
            check(helper, item != Items.AIR, "Create has " + stone);
            CrushingRecipe chance = CrushingRecipe.of(new ItemStack(item));
            check(helper, chance == null, "the Crusher leaves " + stone + " (a chance only) to the wheels: " + (chance == null ? null : chance.output()));
        }
        HandWork grass = HandWork.of(HandWork.MILLING, new ItemStack(Items.SHORT_GRASS));
        check(helper, grass == null, "the quern leaves grass (seeds one time in four) to the Millstone: " + (grass == null ? null : grass.output()));
        check(helper, CrushingRecipe.of(new ItemStack(Items.SHORT_GRASS)) == null, "and so does the Crusher");
        helper.succeed();
    }

    /** In each of Create's machines our recipes went to, every thing has one recipe at most (our crossings never doubling one). */
    private static void noTwo(GameTestHelper helper) {
        RecipeManager manager = recipes(helper);
        TreeSet<String> twice = new TreeSet<>();
        for (RecipeType<?> type : java.util.List.of(AllRecipeTypes.MILLING.getType(), AllRecipeTypes.CRUSHING.getType(),
                AllRecipeTypes.PRESSING.getType())) {
            Map<Item, ResourceLocation> seen = new HashMap<>();
            for (RecipeHolder<?> holder : manager.getRecipes()) {
                if (holder.value().getType() != type || holder.value().getIngredients().size() != 1) {
                    continue;
                }
                for (ItemStack stack : holder.value().getIngredients().get(0).getItems()) {
                    ResourceLocation before = seen.put(stack.getItem(), holder.id());
                    if (before != null && (before.getNamespace().equals(Nookworks.MODID) || holder.id().getNamespace().equals(Nookworks.MODID))) {
                        twice.add(BuiltInRegistries.RECIPE_TYPE.getKey(type) + " " + stack.getItem() + ": " + before + " and " + holder.id());
                    }
                }
            }
        }
        // Crushing Wheels fall back on milling: none of ours in crushing for what milling has
        for (ResourceLocation id : CreateRecipes.crossed()) {
            if (id.getPath().startsWith(CreateRecipes.PREFIX + "crushing/")) {
                var holder = manager.byKey(id).orElseThrow();
                for (ItemStack stack : holder.value().getIngredients().get(0).getItems()) {
                    for (RecipeHolder<?> other : manager.getRecipes()) {
                        if (other.value().getType() == AllRecipeTypes.MILLING.getType() && other.value().getIngredients().size() == 1
                                && other.value().getIngredients().get(0).test(stack)) {
                            twice.add("crushing wheels " + stack.getItem() + ": " + id + " and milling " + other.id());
                        }
                    }
                }
            }
        }
        check(helper, twice.isEmpty(), "no thing with two recipes: " + twice);
        helper.succeed();
    }

    /**
     * With Farmer's Delight too: its Rice and our Brown Rice, twins, are polished by one Millstone recipe that takes either
     * (named after ours), not one each that the twin widening would make both take both.
     */
    private static void twins(GameTestHelper helper) {
        RecipeManager manager = recipes(helper);
        Item theirs = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("farmersdelight", "rice"));
        var one = manager.byKey(ours("milling/nookworks/brown_rice"));
        check(helper, one.isPresent() && one.get().value().getIngredients().size() == 1
                && one.get().value().getIngredients().get(0).test(new ItemStack(ModCrops.BROWN_RICE.get()))
                && one.get().value().getIngredients().get(0).test(new ItemStack(theirs)), "one Millstone recipe takes both rices");
        check(helper, manager.byKey(ours("milling/farmersdelight/rice")).isEmpty(), "and none of its own for its Rice");
        check(helper, !dev.nookworks.compat.farmersdelight.FdKitchen.twins().isEmpty(), "the twins were read before Create's crossing");
        helper.succeed();
    }

    /** A Millstone turned by a Creative Motor under it, a loaf of bread put in: breadcrumbs come out (our Hand Quern's work). */
    private static void millstoneBread(GameTestHelper helper) {
        BlockPos mill = new BlockPos(3, 2, 3);
        helper.setBlock(mill, createBlock("millstone"));
        helper.setBlock(mill.below(), createBlock("creative_motor").defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.UP));
        helper.getBlockEntity(mill.below(), CreativeMotorBlockEntity.class).generatedSpeed.setValue(64);
        IItemHandler in = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, helper.absolutePos(mill), Direction.NORTH);
        check(helper, in != null && in.insertItem(0, new ItemStack(Items.BREAD, 2), false).isEmpty(), "the Millstone takes bread");
        helper.succeedWhen(() -> {
            IItemHandler out = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, helper.absolutePos(mill), Direction.DOWN);
            int crumbs = 0;
            for (int i = 0; out != null && i < out.getSlots(); i++) {
                crumbs += out.getStackInSlot(i).is(dev.nookworks.registry.ModCooking.BREADCRUMBS.get()) ? out.getStackInSlot(i).getCount() : 0;
            }
            check(helper, crumbs == 4, "two loaves, four breadcrumbs: " + crumbs);
        });
    }

    /** An Item Drain given a Vegetable Oil Bottle: 250 mB of oil in its tank (our emptying recipe), a glass bottle left. */
    private static void drainBottle(GameTestHelper helper) {
        BlockPos drain = new BlockPos(3, 1, 3);
        helper.setBlock(drain, createBlock("item_drain"));
        IItemHandler in = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, helper.absolutePos(drain), Direction.NORTH);
        check(helper, in != null && in.insertItem(0, new ItemStack(dev.nookworks.registry.ModOil.VEGETABLE_OIL_BOTTLE.get()), false).isEmpty(),
                "the drain takes the bottle");
        helper.succeedWhen(() -> {
            var tank = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, helper.absolutePos(drain), Direction.DOWN);
            var held = tank.getFluidInTank(0);
            check(helper, held.getFluid().isSame(dev.nookworks.oil.OilFluids.SOURCE.get()) && held.getAmount() == 250, "250 mB of oil: " + held.getAmount());
        });
    }
}
