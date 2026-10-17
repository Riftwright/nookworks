package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.List;
import java.util.function.Function;

import dev.nookworks.cooking.GrillBlock;
import dev.nookworks.cooking.GrillBlockEntity;
import dev.nookworks.cooking.HandMachineBlockEntity;
import dev.nookworks.cooking.IronPotBlock;
import dev.nookworks.cooking.IronPotBlockEntity;
import dev.nookworks.cooking.NukadokoBlockEntity;
import dev.nookworks.cooking.PicklingRecipe;
import dev.nookworks.cooking.PotRecipe;
import dev.nookworks.cooking.TrayBlockEntity;
import dev.nookworks.crop.CornBlock;
import dev.nookworks.oil.PressingRecipe;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** The bakery and the noodle bar, corn and onions, the rice bale, and what can be eaten raw. */
final class KitchenTests {
    static void register() {
        NookworksGameTests.test("hard_dishes_give_a_boost_when_eaten", KitchenTests::dishesGiveABoost);
        NookworksGameTests.test("bread_dough_rises_on_a_wooden_tray", KitchenTests::breadDoughRises);
        NookworksGameTests.test("bread_is_made_of_flour", KitchenTests::breadIsMadeOfFlour);
        NookworksGameTests.test("hand_quern_and_noodle_maker_turn_by_hand", KitchenTests::handMachines);
        NookworksGameTests.test("corn_grows_two_blocks_tall", KitchenTests::cornGrowsTall);
        NookworksGameTests.test("corn_is_planted_from_its_kernels", KitchenTests::cornFromKernels);
        NookworksGameTests.test("cream_stew_gives_the_milk_bucket_back", 900, KitchenTests::creamStew);
        NookworksGameTests.test("raw_kelp_sugar_and_mushrooms_can_be_eaten", KitchenTests::rawThingsEdible);
        NookworksGameTests.test("rice_bale_lies_on_its_side", KitchenTests::riceBale);
        NookworksGameTests.test("an_iron_pot_cooks_between_two_hoppers", 900, KitchenTests::potBetweenHoppers);
        NookworksGameTests.test("a_grill_cooks_between_two_hoppers", 900, KitchenTests::grillBetweenHoppers);
        NookworksGameTests.test("tray_and_nukadoko_give_hoppers_only_what_is_done", KitchenTests::trayAndNukadokoForHoppers);
    }

    /**
     * The dishes it takes most to make give a few minutes of a boost when eaten (the user, 2026-10-10), as strong as
     * vanilla's: tonkatsu Haste, karaage Strength, fried fish Water Breathing, udon Speed, cream stew Regeneration for
     * half a minute. Eaten under a stronger one - a beacon's Haste II - the stronger stays: they do not add up.
     */
    private static void dishesGiveABoost(GameTestHelper helper) {
        record Boost(Item dish, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int ticks) {}
        List<Boost> boosts = List.of(new Boost(ModCooking.TONKATSU.get(), MobEffects.HASTE, 3600),
                new Boost(ModCooking.KARAAGE.get(), MobEffects.STRENGTH, 2400),
                new Boost(ModCooking.FRIED_FISH.get(), MobEffects.WATER_BREATHING, 3600),
                new Boost(ModKitchen.UDON.get(), MobEffects.SPEED, 3600),
                new Boost(ModKitchen.CREAM_STEW.get(), MobEffects.REGENERATION, 600));
        for (Boost boost : boosts) {
            var player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
            player.getFoodData().setFoodLevel(2);
            new ItemStack(boost.dish()).finishUsingItem(helper.getLevel(), player);
            var given = player.getEffect(boost.effect());
            NookworksGameTests.check(helper, given != null && given.getAmplifier() == 0 && given.getDuration() == boost.ticks(),
                    boost.dish() + " gives " + boost.effect().getRegisteredName() + " I for " + boost.ticks() + " ticks: " + given);
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        var player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.HASTE, 300, 1));
        player.getFoodData().setFoodLevel(2);
        new ItemStack(ModCooking.TONKATSU.get()).finishUsingItem(helper.getLevel(), player);
        var haste = player.getEffect(MobEffects.HASTE);
        NookworksGameTests.check(helper, haste != null && haste.getAmplifier() == 1, "a beacon's Haste II stays II: " + haste);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private static BlockHitResult hit(GameTestHelper helper, BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP, helper.absolutePos(pos), false);
    }

    /**
     * Dough is not set down on the bare table any more: it goes on a Wooden Tray, one ball to a place, and rises there in
     * three minutes; an empty hand takes it off as Risen Dough, one a click into the inventory, which bakes into bread. A bowl of rice and a cutlet go on
     * a tray too, a stone does not, and a full tray takes nothing more. Off its table the tray falls, and what was on it
     * with it.
     */
    private static void breadDoughRises(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        check(helper, !(ModKitchen.BREAD_DOUGH.get() instanceof BlockItem), "dough is not a block to set down");
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.OAK_PLANKS);
        helper.setBlock(pos, ModKitchen.WOODEN_TRAY.get());
        var tray = helper.getBlockEntity(pos, TrayBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModKitchen.BREAD_DOUGH.get(), 3));
        for (int i = 0; i < 3; i++) {
            BlockState state = helper.getBlockState(pos);
            check(helper, state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit(helper, pos))
                    .consumesAction(), "dough goes on the tray");
        }
        check(helper, player.getMainHandItem().isEmpty(), "all three balls are on it, one to a place");
        check(helper, tray.items().stream().filter(s -> s.is(ModKitchen.BREAD_DOUGH.get())).count() == 3, "three balls of dough: " + tray.items());
        tray.riseBy(level, level.getGameTime() + TrayBlockEntity.RISE_TICKS / 2);
        check(helper, tray.items().get(0).is(ModKitchen.BREAD_DOUGH.get()) && tray.rise(0, level.getGameTime() + 1_800, 0) > 0.4F,
                "half way, still rising and half swollen");
        tray.riseBy(level, level.getGameTime() + TrayBlockEntity.RISE_TICKS);
        check(helper, tray.items().stream().filter(s -> s.is(ModKitchen.RISEN_DOUGH.get())).count() == 3, "risen in three minutes: " + tray.items());

        ItemStack rice = new ItemStack(ModCooking.COOKED_RICE.get());
        check(helper, helper.getBlockState(pos).useItemOn(rice, level, player, InteractionHand.MAIN_HAND, hit(helper, pos))
                .consumesAction() && rice.isEmpty(), "a bowl of rice is served on it");
        ItemStack stone = new ItemStack(Items.STONE);
        check(helper, !helper.getBlockState(pos).useItemOn(stone, level, player, InteractionHand.MAIN_HAND, hit(helper, pos))
                .consumesAction() && stone.getCount() == 1, "a stone is not food: the click is the stone's");
        ItemStack katsu = new ItemStack(ModCooking.TONKATSU.get());
        helper.getBlockState(pos).useItemOn(katsu, level, player, InteractionHand.MAIN_HAND, hit(helper, pos));
        check(helper, katsu.getCount() == 1 && tray.isFull(), "a full tray takes no cutlet");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.getBlockState(pos).useWithoutItem(level, player, hit(helper, pos));
        check(helper, player.getInventory().countItem(ModCooking.COOKED_RICE.get()) == 1 && player.getMainHandItem().isEmpty(),
                "an empty hand takes the last thing off, into the inventory: the hand stays empty");
        // so the next click takes the next one, where with the dough in hand it put it back (the user, 2026-10-12)
        helper.getBlockState(pos).useWithoutItem(level, player, hit(helper, pos));
        check(helper, player.getInventory().countItem(ModKitchen.RISEN_DOUGH.get()) == 1 && player.getMainHandItem().isEmpty()
                && tray.items().stream().filter(s -> s.is(ModKitchen.RISEN_DOUGH.get())).count() == 2, "and the next click the next one");
        helper.getBlockState(pos).useWithoutItem(level, player, hit(helper, pos));
        check(helper, player.getInventory().countItem(ModKitchen.RISEN_DOUGH.get()) == 2, "into the same stack");
        player.setShiftKeyDown(true);
        helper.getBlockState(pos).useWithoutItem(level, player, hit(helper, pos));
        check(helper, tray.isEmpty() && player.getInventory().countItem(ModKitchen.RISEN_DOUGH.get()) == 3, "sneaking, everything");
        var baked = level.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(new ItemStack(ModKitchen.RISEN_DOUGH.get())), level);
        check(helper, baked.isPresent() && baked.get().value().assemble(new SingleRecipeInput(new ItemStack(ModKitchen.RISEN_DOUGH.get()))).is(Items.BREAD),
                "risen dough bakes into bread");

        tray.place(level, null, new ItemStack(ModKitchen.BREAD_DOUGH.get()));
        helper.setBlock(pos.below(), Blocks.AIR);
        check(helper, helper.getBlockState(pos).isAir(), "nothing under it: the tray goes");
        var dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(1.5));
        check(helper, dropped.stream().anyMatch(e -> e.getItem().is(ModKitchen.WOODEN_TRAY_ITEM.get()))
                && dropped.stream().anyMatch(e -> e.getItem().is(ModKitchen.BREAD_DOUGH.get())), "and drops, with the dough that was on it");
        helper.succeed();
    }

    /**
     * With the bakery on (the default), bread, cookies, cake and pumpkin pie are made of flour: vanilla's recipes from
     * wheat are not loaded, ours are; the dough recipes are there. The Iron Pot knows the new dishes.
     */
    private static void breadIsMadeOfFlour(GameTestHelper helper) {
        var recipes = helper.getLevel().getServer().getRecipeManager();
        Function<String, Boolean> has = id -> recipes.byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse(id))).isPresent();
        for (String gone : new String[] {"minecraft:bread", "minecraft:cookie", "minecraft:cake", "minecraft:pumpkin_pie"}) {
            check(helper, !has.apply(gone), gone + " from wheat is not loaded");
        }
        for (String there : new String[] {"nookworks:cookie_from_flour", "nookworks:cake_from_flour", "nookworks:pumpkin_pie_from_flour",
                "nookworks:bread_dough", "nookworks:udon_dough", "nookworks:pancake_batter", "nookworks:rice_bale"}) {
            check(helper, has.apply(there), there + " is loaded");
        }
        var pot = PotRecipe.all(helper.getLevel());
        for (var dish : new Item[] {ModKitchen.UDON.get(), ModKitchen.CREAM_STEW.get(), ModKitchen.CORN_POTAGE.get(),
                ModKitchen.DONUT.get()}) {
            check(helper, pot.stream().anyMatch(r -> r.value().result().item().value() == dish), "the pot cooks " + dish);
        }
        check(helper, new ItemStack(ModCooking.FLOUR.get()).is(TagKey.create(Registries.ITEM, Identifier.parse("c:flours/wheat"))),
                "our flour is c:flours/wheat, so other mods' flour is too");
        helper.succeed();
    }

    /**
     * The Hand Quern grinds a wheat to flour in three turns, into the chest under it; the Noodle Maker cuts a dough into
     * two noodles in four. With nothing in, the handle turns nothing.
     */
    private static void handMachines(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos quernPos = new BlockPos(1, 2, 1);
        helper.setBlock(quernPos.below(), Blocks.BARREL);
        helper.setBlock(quernPos, ModKitchen.HAND_QUERN.get());
        var quern = helper.getBlockEntity(quernPos, HandMachineBlockEntity.class);
        check(helper, !quern.wantsTurn(), "empty: no turn wanted");
        check(helper, quern.canPlaceItem(HandMachineBlockEntity.INPUT, new ItemStack(Items.WHEAT))
                && !quern.canPlaceItem(HandMachineBlockEntity.INPUT, new ItemStack(Items.COBBLESTONE)), "it takes wheat, not stone");
        quern.setItem(HandMachineBlockEntity.INPUT, new ItemStack(Items.WHEAT, 2));
        for (int i = 0; i < 3; i++) {
            quern.turn(level);
        }
        var barrel = (BarrelBlockEntity) level.getBlockEntity(helper.absolutePos(quernPos.below()));
        check(helper, barrel.countItem(ModCooking.FLOUR.get()) == 1 && quern.getItem(HandMachineBlockEntity.INPUT).getCount() == 1,
                "three turns: a wheat is flour, in the barrel below");

        BlockPos makerPos = new BlockPos(3, 2, 1);
        helper.setBlock(makerPos, ModKitchen.NOODLE_MAKER.get());
        var maker = helper.getBlockEntity(makerPos, HandMachineBlockEntity.class);
        maker.setItem(HandMachineBlockEntity.INPUT, new ItemStack(ModKitchen.UDON_DOUGH.get()));
        for (int i = 0; i < 4; i++) {
            maker.turn(level);
        }
        check(helper, maker.getItem(HandMachineBlockEntity.OUTPUT).is(ModKitchen.RAW_UDON.get())
                && maker.getItem(HandMachineBlockEntity.OUTPUT).getCount() == 2, "four turns: two raw udon, kept inside with nowhere to go");
        check(helper, maker.takeOut().getCount() == 2, "a sneaking hand takes them out");
        helper.succeed();
    }

    /**
     * Corn comes up one block tall and grows its second block from stage three, tasselled; ripe, it gives two to four
     * ears, and before that nothing - the kernel it grew from is spent. Its top half goes with its stalk. Onions ripen
     * as carrots do, and give at least one.
     */
    private static void cornGrowsTall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.FARMLAND);
        helper.setBlock(pos, ModKitchen.CORN_CROP.get());
        BlockPos at = helper.absolutePos(pos);
        check(helper, Block.getDrops(level.getBlockState(at), level, at, null).isEmpty(), "just come up, it gives nothing back");
        for (int age = 1; age <= CornBlock.MAX_AGE; age++) {
            check(helper, CornBlock.grow(level, at, level.getBlockState(at)), "it grows to stage " + age);
            if (age < CornBlock.MAX_AGE) {
                check(helper, Block.getDrops(level.getBlockState(at), level, at, null).isEmpty(), "unripe at stage " + age + ", nothing");
            }
            BlockState top = level.getBlockState(at.above());
            check(helper, top.is(ModKitchen.CORN_CROP.get()) == CornBlock.isDouble(age)
                    && (!CornBlock.isDouble(age) || top.getValue(CornBlock.HALF) == DoubleBlockHalf.UPPER), "stage " + age + ": top half " + top);
        }
        check(helper, !CornBlock.grow(level, at, level.getBlockState(at)), "and no further");
        var drops = Block.getDrops(level.getBlockState(at), level, at, null);
        int ears = drops.stream().filter(s -> s.is(ModKitchen.CORN.get())).mapToInt(ItemStack::getCount).sum();
        check(helper, ears >= 2 && ears <= 4, "ripe: two to four ears, " + ears);
        check(helper, Block.getDrops(level.getBlockState(at.above()), level, at.above(), null).isEmpty(),
                "and the top half drops nothing of its own: the plant is one plant");
        // Broken for real, by either half, the plant drops its ears once (it dropped them for each half, twice over).
        for (BlockPos broken : List.of(at, at.above())) {
            level.setBlock(at, ModKitchen.CORN_CROP.get().defaultBlockState(), 3);
            while (CornBlock.grow(level, at, level.getBlockState(at))) {
                // to ripe
            }
            AABB around = new AABB(at).inflate(2);
            level.getEntitiesOfClass(ItemEntity.class, around).forEach(Entity::discard);
            level.destroyBlock(broken, true);
            int dropped = level.getEntitiesOfClass(ItemEntity.class, around).stream()
                    .filter(e -> e.getItem().is(ModKitchen.CORN.get())).mapToInt(e -> e.getItem().getCount()).sum();
            check(helper, dropped >= 2 && dropped <= 4, "broken by its " + (broken.equals(at) ? "foot" : "top") + ": two to four ears, not " + dropped);
            check(helper, level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir(), "and both halves go");
        }
        // Tall but not yet ripe, broken for real: nothing at all, not even from the top half.
        level.setBlock(at, ModKitchen.CORN_CROP.get().defaultBlockState(), 3);
        for (int age = 1; age < CornBlock.MAX_AGE; age++) {
            CornBlock.grow(level, at, level.getBlockState(at));
        }
        AABB around = new AABB(at).inflate(2);
        level.getEntitiesOfClass(ItemEntity.class, around).forEach(Entity::discard);
        level.destroyBlock(at.above(), true);
        check(helper, level.getEntitiesOfClass(ItemEntity.class, around).isEmpty(), "unripe and broken: nothing dropped");
        check(helper, level.getBlockState(at).isAir(), "and the stalk went with its top");

        BlockPos onion = new BlockPos(4, 2, 2);
        helper.setBlock(onion.below(), Blocks.FARMLAND);
        helper.setBlock(onion, ModKitchen.ONION_CROP.get().getStateForAge(7));
        var onions = Block.getDrops(helper.getBlockState(onion), level, helper.absolutePos(onion), null);
        check(helper, onions.stream().filter(s -> s.is(ModKitchen.ONION.get())).mapToInt(ItemStack::getCount).sum() >= 1, "ripe onions: at least one");
        check(helper, new ItemStack(ModKitchen.ONION.get()).is(TagKey.create(Registries.ITEM, Identifier.parse("c:crops/onion"))),
                "c:crops/onion, so the recipes take other mods' onions");
        helper.succeed();
    }

    private static TagKey<Item> itemTag(String id) {
        return TagKey.create(Registries.ITEM, Identifier.parse(id));
    }

    /**
     * Corn is planted from its kernels, as wheat is from its seeds (the user, 2026-10-02: an ear is not planted as it
     * is). Right-clicked on farmland as the game does it, a kernel is sown and used up; an ear is not sown. An ear shells
     * into four kernels on a crafting table; picking the plant gives a kernel. Chickens and parrots peck the kernels,
     * pigs eat the ear; the Oil Press gets as much oil from a kernel as from a melon seed.
     */
    private static void cornFromKernels(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 aside = helper.absoluteVec(new Vec3(0.5, 8, 0.5));
        player.snapTo(aside.x, aside.y, aside.z);
        BlockPos kernelSpot = new BlockPos(1, 2, 1);
        BlockPos earSpot = new BlockPos(3, 2, 1);
        helper.setBlock(kernelSpot, Blocks.FARMLAND);
        helper.setBlock(earSpot, Blocks.FARMLAND);

        ItemStack kernels = new ItemStack(ModKitchen.CORN_KERNELS.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, kernels);
        player.gameMode.useItemOn(player, level, kernels, InteractionHand.MAIN_HAND, hit(helper, kernelSpot));
        BlockState sown = helper.getBlockState(kernelSpot.above());
        check(helper, sown.is(ModKitchen.CORN_CROP.get()) && sown.getValue(CornBlock.AGE) == 0
                && sown.getValue(CornBlock.HALF) == DoubleBlockHalf.LOWER, "a kernel is sown: " + sown);
        check(helper, kernels.getCount() == 1, "and used up, one of two: " + kernels);

        ItemStack ear = new ItemStack(ModKitchen.CORN.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, ear);
        player.gameMode.useItemOn(player, level, ear, InteractionHand.MAIN_HAND, hit(helper, earSpot));
        check(helper, helper.getBlockState(earSpot.above()).isAir(), "an ear is not sown: " + helper.getBlockState(earSpot.above()));
        check(helper, !(ModKitchen.CORN.get() instanceof BlockItem) && ear.has(DataComponents.FOOD), "an ear is food, not a block");
        level.getServer().getPlayerList().remove(player);

        BlockPos at = helper.absolutePos(kernelSpot.above());
        check(helper, sown.getCloneItemStack(level, at, false).is(ModKitchen.CORN_KERNELS.get()), "picking the plant gives a kernel");

        CraftingInput shelling = CraftingInput.of(1, 1, List.of(new ItemStack(ModKitchen.CORN.get())));
        ItemStack shelled = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, shelling, level)
                .map(recipe -> recipe.value().assemble(shelling)).orElse(ItemStack.EMPTY);
        check(helper, shelled.is(ModKitchen.CORN_KERNELS.get()) && shelled.getCount() == 4, "an ear shells into four kernels: " + shelled);

        ItemStack kernel = new ItemStack(ModKitchen.CORN_KERNELS.get());
        check(helper, kernel.is(ItemTags.CHICKEN_FOOD) && kernel.is(ItemTags.PARROT_FOOD) && !kernel.is(ItemTags.PIG_FOOD),
                "chickens and parrots peck kernels");
        check(helper, ear.is(ItemTags.PIG_FOOD) && !ear.is(ItemTags.CHICKEN_FOOD), "pigs eat the ear, chickens do not");
        check(helper, kernel.is(itemTag("c:seeds/corn")) && kernel.is(itemTag("c:seeds")) && ear.is(itemTag("c:crops/corn"))
                && !kernel.is(itemTag("c:crops/corn")), "kernels are c:seeds/corn, the ear c:crops/corn");
        var pressed = PressingRecipe.of(kernel);
        var melon = PressingRecipe.of(new ItemStack(Items.MELON_SEEDS));
        check(helper, pressed != null && melon != null && pressed.oil() == melon.oil(), "a kernel gives a melon seed's oil: " + pressed);
        helper.succeed();
    }

    /**
     * Cream stew: milk, flour, onion, potato, carrot and chicken in water cook to three bowls' worth, and the milk's
     * bucket is given back with the dish.
     */
    private static void creamStew(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos.below(), Blocks.MAGMA_BLOCK);
        helper.setBlock(pos, ModCooking.IRON_POT.get());
        var pot = helper.getBlockEntity(pos, IronPotBlockEntity.class);
        pot.addLiquid(PotRecipe.Liquid.WATER, 250);
        for (var item : new Item[] {Items.MILK_BUCKET, ModCooking.FLOUR.get(), ModKitchen.ONION.get(), Items.POTATO,
                Items.CARROT, Items.CHICKEN}) {
            check(helper, pot.addIngredient(new ItemStack(item)), item + " goes in");
        }
        helper.succeedWhen(() -> {
            check(helper, pot.dish().is(ModKitchen.CREAM_STEW.get()) && pot.dish().getCount() == 3, "three bowls of stew: " + pot.dish());
            check(helper, pot.freed().stream().anyMatch(s -> s.is(Items.BUCKET)), "and the milk's bucket back");
        });
    }

    /**
     * A kitchen to run itself (the user, 2026-10-01: there should always be a way to automate for whoever looks for
     * one). A hopper above an Iron Pot feeds it corn, an onion and milk, then the bowls once the potage waits; the pot
     * stands on a hopper over a magma block, heated through it, and that hopper takes out three bowls of potage and the
     * milk's bucket into a chest. Nothing else comes out early, and the pot is left empty.
     */
    private static void potBetweenHoppers(GameTestHelper helper) {
        BlockPos pot = new BlockPos(1, 3, 1);
        helper.setBlock(pot.below(2), Blocks.MAGMA_BLOCK);
        helper.setBlock(pot.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        helper.setBlock(pot.below().east(), Blocks.CHEST);
        helper.setBlock(pot, ModCooking.IRON_POT.get());
        helper.setBlock(pot.above(), Blocks.HOPPER);
        var potEntity = helper.getBlockEntity(pot, IronPotBlockEntity.class);
        potEntity.addLiquid(PotRecipe.Liquid.WATER, 250);
        check(helper, IronPotBlock.heated(helper.getLevel(), helper.absolutePos(pot)), "the heat comes up through the hopper");
        var feed = helper.getBlockEntity(pot.above(), HopperBlockEntity.class);
        feed.setItem(0, new ItemStack(ModKitchen.CORN.get(), 2));
        feed.setItem(1, new ItemStack(ModKitchen.ONION.get()));
        feed.setItem(2, new ItemStack(Items.MILK_BUCKET));
        feed.setItem(3, new ItemStack(Items.BOWL, 3));
        feed.setItem(4, new ItemStack(Items.STONE));
        var chest = helper.getBlockEntity(pot.below().east(), ChestBlockEntity.class);
        helper.succeedWhen(() -> {
            int potage = 0;
            int buckets = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack stack = chest.getItem(i);
                potage += stack.is(ModKitchen.CORN_POTAGE.get()) ? stack.getCount() : 0;
                buckets += stack.is(Items.BUCKET) ? stack.getCount() : 0;
                check(helper, stack.isEmpty() || stack.is(ModKitchen.CORN_POTAGE.get()) || stack.is(Items.BUCKET),
                        "only what is done comes out: " + stack);
            }
            check(helper, potage == 3 && buckets == 1, "three bowls of potage and the bucket, so far " + potage + " and " + buckets);
            check(helper, potEntity.dish().isEmpty() && potEntity.freed().isEmpty() && potEntity.count() == 0, "and the pot is empty");
            check(helper, feed.getItem(4).is(Items.STONE), "the stone stays in the hopper: no recipe of the pot takes it");
        });
    }

    /**
     * A Grill standing on a hopper over a lit campfire cooks, heated through the hopper: a hopper above lays two raw beef
     * on it, and the hopper underneath takes them off into a chest once they are steaks - not before.
     */
    private static void grillBetweenHoppers(GameTestHelper helper) {
        BlockPos grill = new BlockPos(1, 3, 1);
        helper.setBlock(grill.below(2), Blocks.CAMPFIRE);
        helper.setBlock(grill.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        helper.setBlock(grill.below().east(), Blocks.CHEST);
        helper.setBlock(grill, ModCooking.GRILL.get());
        helper.setBlock(grill.above(), Blocks.HOPPER);
        check(helper, GrillBlock.hot(helper.getLevel(), helper.absolutePos(grill)), "the heat comes up through the hopper");
        helper.getBlockEntity(grill.above(), HopperBlockEntity.class).setItem(0, new ItemStack(Items.BEEF, 2));
        var grillEntity = helper.getBlockEntity(grill, GrillBlockEntity.class);
        var chest = helper.getBlockEntity(grill.below().east(), ChestBlockEntity.class);
        helper.succeedWhen(() -> {
            for (int i = 0; i < chest.getContainerSize(); i++) {
                check(helper, chest.getItem(i).isEmpty() || chest.getItem(i).is(Items.COOKED_BEEF), "only steaks come off: " + chest.getItem(i));
            }
            check(helper, chest.countItem(Items.COOKED_BEEF) == 2, "two steaks, so far " + chest.countItem(Items.COOKED_BEEF));
            check(helper, grillEntity.items().stream().allMatch(ItemStack::isEmpty), "and the grill is bare");
        });
    }

    /**
     * Through the item capability a Wooden Tray takes what goes on a tray and gives back dough only once it has risen; a
     * Nukadoko takes what pickles and gives back pickles only; neither takes a stone; and a move a pipe tries and takes
     * back leaves both as they were.
     */
    private static void trayAndNukadokoForHoppers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos trayPos = new BlockPos(1, 2, 1);
        BlockPos tubPos = new BlockPos(3, 2, 1);
        helper.setBlock(trayPos.below(), Blocks.HOPPER);
        helper.setBlock(trayPos, ModKitchen.WOODEN_TRAY.get());
        check(helper, helper.getBlockState(trayPos).canSurvive(level, helper.absolutePos(trayPos)),
                "a tray stands on a hopper, which can empty it");
        helper.setBlock(tubPos, ModCooking.NUKADOKO.get());
        var tray = helper.getBlockEntity(trayPos, TrayBlockEntity.class);
        var tub = helper.getBlockEntity(tubPos, NukadokoBlockEntity.class);
        var trayItems = level.getCapability(Capabilities.Item.BLOCK, helper.absolutePos(trayPos), Direction.UP);
        var tubItems = level.getCapability(Capabilities.Item.BLOCK, helper.absolutePos(tubPos), Direction.UP);
        check(helper, trayItems != null && tubItems != null, "both offer their places to hoppers");
        try (Transaction transaction = Transaction.openRoot()) {
            check(helper, trayItems.insert(ItemResource.of(Items.STONE), 1, transaction) == 0
                    && tubItems.insert(ItemResource.of(Items.STONE), 1, transaction) == 0, "neither takes a stone");
            check(helper, trayItems.insert(ItemResource.of(ModKitchen.BREAD_DOUGH.get()), 1, transaction) == 1, "the tray takes dough");
            check(helper, tubItems.insert(ItemResource.of(Items.CARROT), 1, transaction) == 1, "the tub takes a carrot");
        } // not committed: a pipe that thought better of it
        check(helper, tray.isEmpty() && tub.items().stream().allMatch(ItemStack::isEmpty), "a move taken back leaves both empty");
        try (Transaction transaction = Transaction.openRoot()) {
            trayItems.insert(ItemResource.of(ModKitchen.BREAD_DOUGH.get()), 1, transaction);
            tubItems.insert(ItemResource.of(Items.CARROT), 1, transaction);
            transaction.commit();
        }
        try (Transaction transaction = Transaction.openRoot()) {
            check(helper, trayItems.extract(ItemResource.of(ModKitchen.BREAD_DOUGH.get()), 1, transaction) == 0, "rising dough stays on");
            check(helper, tubItems.extract(ItemResource.of(Items.CARROT), 1, transaction) == 0, "a carrot still pickling stays in");
        }
        tray.riseBy(level, level.getGameTime() + TrayBlockEntity.RISE_TICKS);
        int pickling = PicklingRecipe.of(new ItemStack(Items.CARROT)).time();
        BlockPos at = helper.absolutePos(tubPos);
        for (int i = 0; i < pickling; i++) {
            NukadokoBlockEntity.serverTick(level, at, level.getBlockState(at), tub);
        }
        Item pickle = tub.items().get(0).getItem();
        try (Transaction transaction = Transaction.openRoot()) {
            check(helper, trayItems.extract(ItemResource.of(ModKitchen.RISEN_DOUGH.get()), 1, transaction) == 1, "risen dough comes off");
            check(helper, pickle != Items.CARROT && tubItems.extract(ItemResource.of(pickle), 1, transaction) == 1, "a pickle comes out");
            transaction.commit();
        }
        check(helper, tray.isEmpty() && tub.items().stream().allMatch(ItemStack::isEmpty), "and both are empty");
        helper.succeed();
    }

    /**
     * Kelp, sugar cane, sugar, salt, short grass, mushrooms, honeycomb and cocoa beans can be eaten raw; the red
     * mushroom poisons; seeds and eggs cannot be eaten (they are planted and thrown with the same click).
     */
    private static void rawThingsEdible(GameTestHelper helper) {
        for (var item : new Item[] {Items.KELP, Items.SUGAR_CANE, Items.SUGAR, ModItems.SALT.get(), Items.SHORT_GRASS,
                Items.BROWN_MUSHROOM, Items.RED_MUSHROOM, Items.CRIMSON_FUNGUS, Items.HONEYCOMB, Items.COCOA_BEANS,
                ModKitchen.CORN.get(), ModKitchen.ONION.get()}) {
            ItemStack stack = new ItemStack(item);
            check(helper, stack.has(DataComponents.FOOD) && stack.has(DataComponents.CONSUMABLE), item + " can be eaten");
        }
        var red = new ItemStack(Items.RED_MUSHROOM).get(DataComponents.CONSUMABLE);
        check(helper, red.onConsumeEffects().stream().anyMatch(e -> e instanceof ApplyStatusEffectsConsumeEffect a
                && a.effects().stream().anyMatch(i -> i.getEffect() == MobEffects.POISON)), "the red mushroom poisons");
        for (var item : new Item[] {Items.WHEAT_SEEDS, Items.PUMPKIN_SEEDS, Items.EGG, ModCrops.BROWN_RICE.get(), ModKitchen.CORN_KERNELS.get(),
                ModCooking.FLOUR.get(), ModKitchen.BREAD_DOUGH.get()}) {
            check(helper, !new ItemStack(item).has(DataComponents.FOOD), item + " is not eaten raw");
        }
        helper.succeed();
    }

    /**
     * A rice bale set on the ground lies on its side along the way the player faces, set against a block along that
     * face; it is made of nine brown rice, as rice is stored in the husk, and gives them back; it burns, and breaks a fall.
     */
    private static void riceBale(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModKitchen.RICE_BALE.get());
        BlockState state = helper.getBlockState(pos);
        check(helper, state.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y, "it lies on its side");
        check(helper, state.getFlammability(helper.getLevel(), helper.absolutePos(pos), Direction.UP) > 0, "straw burns");
        var recipes = helper.getLevel().getServer().getRecipeManager();
        check(helper, recipes.byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse("nookworks:brown_rice_from_rice_bale"))).isPresent(),
                "and unpacks into nine brown rice");
        check(helper, recipes.byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse("nookworks:white_rice_from_rice_bale"))).isEmpty(),
                "not white rice: that is polished out of the brown");
        helper.succeed();
    }

    private KitchenTests() {}
}
