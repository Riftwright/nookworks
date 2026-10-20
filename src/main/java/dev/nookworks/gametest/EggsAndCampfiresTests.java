package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.List;

import dev.nookworks.NookworksConfig;
import dev.nookworks.cooking.IronPotBlockEntity;
import dev.nookworks.cooking.PotRecipe;
import dev.nookworks.registry.ModCooking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The egg dishes - a fried egg, boiled eggs and tamagoyaki (the user, 2026-10-16) - and campfires that are set down
 * unlit and lit with flint and steel (the user, the same day).
 */
final class EggsAndCampfiresTests {
    static void register() {
        NookworksGameTests.test("a_placed_campfire_is_unlit_until_lit", EggsAndCampfiresTests::placedCampfireIsUnlit);
        NookworksGameTests.test("an_egg_fries_in_a_furnace_smoker_or_campfire", EggsAndCampfiresTests::eggFries);
        NookworksGameTests.test("the_iron_pot_boils_eggs_and_rolls_tamagoyaki", 900, EggsAndCampfiresTests::potEggs);
        NookworksGameTests.test("egg_dishes_fill_as_the_table_says", EggsAndCampfiresTests::eggDishValues);
        NookworksGameTests.test("the_iron_pot_takes_no_other_bucket", EggsAndCampfiresTests::potRefusesOtherBuckets);
    }

    /**
     * A lava, mercury or fish bucket used on an Iron Pot stays in the hand and nothing is poured out over the pot (lava on
     * a pot over a fire burned the kitchen down: the kitchen play-test, 2026-10-19).
     */
    private static void potRefusesOtherBuckets(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        BlockPos pot = new BlockPos(2, 1, 2);
        helper.setBlock(pot, dev.nookworks.registry.ModCooking.IRON_POT.get());
        // standing by it, looking at its top, as a player who clicks it does: the bucket's own use aims the same way
        Vec3 stand = helper.absoluteVec(new Vec3(2.5, 1, 0.5));
        player.snapTo(stand.x, stand.y, stand.z);
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(pot)).add(0, -0.2, 0));
        for (var bucket : java.util.List.of(Items.LAVA_BUCKET, dev.nookworks.registry.ModItems.MERCURY_BUCKET.get(), Items.COD_BUCKET)) {
            ItemStack held = new ItemStack(bucket);
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            BlockPos at = helper.absolutePos(pot);
            var onBlock = player.gameMode.useItemOn(player, helper.getLevel(), held, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.UP, 0.4), Direction.UP, at, false));
            var onItem = onBlock.consumesAction() ? null
                    // passed on: the game then uses the item itself (a second packet), and a bucket pours where it is aimed
                    : player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
            boolean poured = BlockPos.betweenClosedStream(helper.absolutePos(pot).offset(-1, -1, -1), helper.absolutePos(pot).offset(1, 1, 1))
                    .anyMatch(p -> !helper.getLevel().getFluidState(p).isEmpty());
            check(helper, !poured, bucket + ": nothing poured out by the pot (the item's own use: " + onItem + ")");
            check(helper, player.getMainHandItem().is(bucket), bucket + " stays in the hand");
            check(helper, helper.getBlockState(pot).is(dev.nookworks.registry.ModCooking.IRON_POT.get()), "the pot stays");
        }
        helper.succeed();
    }

    /** Right-clicks the top of {@code pos} holding {@code stack}, as the game makes a click (the item, then the block). */
    private static void clickTop(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos at = helper.absolutePos(pos);
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.UP, 0.5), Direction.UP, at, false));
    }

    /**
     * A campfire or soul campfire a player sets down is unlit, and flint and steel (or a fire charge) lights it; one set by anything else
     * (a structure, a command) is as it was given. With {@code campfiresPlacedUnlit} off a placed one burns at once,
     * as in vanilla; a campfire item that names its lit state keeps it. The setting is flipped and put back within this
     * one tick, so no other test sees it changed.
     */
    private static void placedCampfireIsUnlit(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 aside = helper.absoluteVec(new Vec3(0.5, 6, 0.5));
        player.snapTo(aside.x, aside.y, aside.z);
        boolean before = NookworksConfig.CAMPFIRES_PLACED_UNLIT.get();
        try {
            NookworksConfig.CAMPFIRES_PLACED_UNLIT.set(true);
            for (int i = 0; i < 2; i++) {
                Block campfire = i == 0 ? Blocks.CAMPFIRE : Blocks.SOUL_CAMPFIRE;
                BlockPos ground = new BlockPos(1 + 2 * i, 1, 1);
                helper.setBlock(ground, Blocks.STONE);
                clickTop(helper, player, new ItemStack(campfire), ground);
                var placed = helper.getBlockState(ground.above());
                check(helper, placed.is(campfire) && !placed.getValue(CampfireBlock.LIT), campfire + " is set down unlit: " + placed);
                check(helper, player.getMainHandItem().isEmpty(), "and the item is spent, as a placed block's is");
                // (lighting it is reported as a block placed as well, over the campfire: it must not put it out again)
                ItemStack lighter = new ItemStack(i == 0 ? Items.FLINT_AND_STEEL : Items.FIRE_CHARGE);
                player.setItemInHand(InteractionHand.MAIN_HAND, lighter);
                BlockPos at = helper.absolutePos(ground.above());
                player.gameMode.useItemOn(player, helper.getLevel(), lighter, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
                check(helper, helper.getBlockState(ground.above()).getValue(CampfireBlock.LIT), lighter.getItem() + " lights it");
            }
            // the state a player's own game predicts as it places one is unlit already: put out only after, it showed
            // burning for a few frames (the user, 2026-10-20)
            BlockPos at = helper.absolutePos(new BlockPos(1, 1, 3));
            helper.setBlock(new BlockPos(1, 1, 3), Blocks.STONE);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.CAMPFIRE));
            var predicted = Blocks.CAMPFIRE.getStateForPlacement(new net.minecraft.world.item.context.BlockPlaceContext(player,
                    InteractionHand.MAIN_HAND, player.getMainHandItem(), new BlockHitResult(Vec3.atCenterOf(at).add(0, 0.5, 0), Direction.UP, at, false)));
            check(helper, predicted != null && !predicted.getValue(CampfireBlock.LIT), "the placing state itself unlit: " + predicted);
            BlockPos given = new BlockPos(5, 2, 1);
            // by a real /setblock, as a player with commands would (helper.setBlock fires no placing event: it proved nothing)
            BlockPos abs = helper.absolutePos(given);
            helper.getLevel().getServer().getCommands().performPrefixedCommand(
                    helper.getLevel().getServer().createCommandSourceStack().withSuppressedOutput().withLevel(helper.getLevel()),
                    "setblock " + abs.getX() + " " + abs.getY() + " " + abs.getZ() + " minecraft:campfire");
            check(helper, helper.getBlockState(given).is(Blocks.CAMPFIRE) && helper.getBlockState(given).getValue(CampfireBlock.LIT),
                    "one set by a command stays lit: " + helper.getBlockState(given));

            BlockPos copied = new BlockPos(1, 1, 3);
            helper.setBlock(copied, Blocks.STONE);
            ItemStack named = new ItemStack(Items.CAMPFIRE);
            named.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(CampfireBlock.LIT, true));
            clickTop(helper, player, named, copied);
            check(helper, helper.getBlockState(copied.above()).getValue(CampfireBlock.LIT), "a campfire item that says it is lit keeps it");

            NookworksConfig.CAMPFIRES_PLACED_UNLIT.set(false);
            BlockPos vanilla = new BlockPos(3, 1, 3);
            helper.setBlock(vanilla, Blocks.STONE);
            clickTop(helper, player, new ItemStack(Items.CAMPFIRE), vanilla);
            var lit = helper.getBlockState(vanilla.above());
            check(helper, lit.is(Blocks.CAMPFIRE) && lit.getValue(CampfireBlock.LIT), "with the setting off it burns at once: " + lit);
        } finally {
            NookworksConfig.CAMPFIRES_PLACED_UNLIT.set(before);
        }
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Any egg of #c:eggs (vanilla's three, and another mod's) fries in a furnace, a smoker and on a campfire - and so on
     * a Grill, which cooks what a campfire does. Eggs are still thrown, not eaten: a raw one has no food on it.
     */
    private static void eggFries(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (Item egg : List.of(Items.EGG, Items.BLUE_EGG, Items.BROWN_EGG)) {
            var input = new SingleRecipeInput(new ItemStack(egg));
            for (RecipeType<?> type : List.of(RecipeType.SMELTING, RecipeType.SMOKING, RecipeType.CAMPFIRE_COOKING)) {
                @SuppressWarnings("unchecked")
                var found = level.recipeAccess().getRecipeFor((RecipeType<net.minecraft.world.item.crafting.AbstractCookingRecipe>) type, input, level);
                check(helper, found.isPresent() && found.get().value().assemble(input).is(ModCooking.FRIED_EGG.get()),
                        egg + " fries in a " + type + ": " + found.map(r -> r.id().identifier()).orElse(null));
            }
        }
        check(helper, !new ItemStack(Items.EGG).has(DataComponents.FOOD), "a raw egg is not food");
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 2, 2.5));
        player.snapTo(at.x, at.y, at.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EGG, 2));
        player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND);
        check(helper, player.getMainHandItem().getCount() == 1
                && !level.getEntitiesOfClass(ThrownEgg.class, new AABB(at, at).inflate(4)).isEmpty(), "and an egg is still thrown");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * In the Iron Pot's water every egg in it boils at once, 50 mB an egg; three eggs and sugar in a little oil roll into
     * three slices of tamagoyaki (a blue egg as well as any). Eggs alone in oil make nothing: an egg is fried on a fire.
     */
    private static void potEggs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var oil = PotRecipe.Liquid.OIL;
        var rolled = PotRecipe.find(level, List.of(new ItemStack(Items.EGG, 2), new ItemStack(Items.BLUE_EGG),
                new ItemStack(Items.SUGAR)), oil, 1_000);
        check(helper, rolled != null && rolled.value().result().stack(1).is(ModCooking.TAMAGOYAKI.get())
                && rolled.value().result().count() == 3 && rolled.value().amount() == 25, "three eggs and sugar in oil: three tamagoyaki");
        check(helper, rolled.value().batches(List.of(new ItemStack(Items.EGG, 6), new ItemStack(Items.SUGAR, 2)), oil, 1_000) == 2,
                "six eggs and two sugar, two rolls at once");
        check(helper, PotRecipe.find(level, List.of(new ItemStack(Items.EGG, 2), new ItemStack(Items.SUGAR)), oil, 1_000) == null,
                "two eggs are not enough for a roll");
        check(helper, PotRecipe.find(level, List.of(new ItemStack(Items.EGG, 3)), oil, 1_000) == null, "eggs alone in oil make nothing");
        check(helper, PotRecipe.find(level, List.of(new ItemStack(Items.EGG, 3), new ItemStack(Items.SUGAR)), PotRecipe.Liquid.WATER, 1_000) == null,
                "sugar is not boiled with the eggs");

        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos.below(), Blocks.MAGMA_BLOCK);
        helper.setBlock(pos, ModCooking.IRON_POT.get());
        var pot = helper.getBlockEntity(pos, IronPotBlockEntity.class);
        check(helper, pot.addLiquid(PotRecipe.Liquid.WATER, 250), "a bottle of water goes in");
        for (int i = 0; i < 3; i++) {
            check(helper, pot.addIngredient(new ItemStack(i == 2 ? Items.BROWN_EGG : Items.EGG)), "an egg goes in");
        }
        helper.succeedWhen(() -> {
            check(helper, pot.dish().is(ModCooking.BOILED_EGG.get()) && pot.dish().getCount() == 3,
                    "three eggs boil into three boiled eggs: " + pot.dish());
            check(helper, pot.amount() == 100 && pot.count() == 0 && pot.container() == null,
                    "50 mB of water each, no bowl: " + pot.amount() + " mB left");
        });
    }

    /** Fried egg 4 (8.8 in all), boiled egg 5 (11.0, as bread), tamagoyaki 6 (15.6): under the pot's dishes, 7 to 9. */
    private static void eggDishValues(GameTestHelper helper) {
        record Value(Item item, int nutrition, float saturation) {}
        for (Value v : List.of(new Value(ModCooking.FRIED_EGG.get(), 4, 4.8F), new Value(ModCooking.BOILED_EGG.get(), 5, 6.0F),
                new Value(ModCooking.TAMAGOYAKI.get(), 6, 9.6F))) {
            FoodProperties food = new ItemStack(v.item()).get(DataComponents.FOOD);
            check(helper, food != null && food.nutrition() == v.nutrition() && Math.abs(food.saturation() - v.saturation()) < 0.01F,
                    v.item() + " fills " + v.nutrition() + " / " + v.saturation() + ": " + food);
            check(helper, new ItemStack(v.item()).getMaxStackSize() == 64, "and stacks as food does");
        }
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.getFoodData().setFoodLevel(4);
        ItemStack eaten = new ItemStack(ModCooking.TAMAGOYAKI.get());
        eaten.finishUsingItem(helper.getLevel(), player);
        check(helper, player.getFoodData().getFoodLevel() == 10 && eaten.isEmpty(), "a slice eaten fills six: " + player.getFoodData().getFoodLevel());
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }
}
