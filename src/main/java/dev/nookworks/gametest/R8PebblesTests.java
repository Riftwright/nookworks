package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.nookworks.block.PebblesBlock;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Pebbles (r8, the user's ask of 2026-10-06): one to four in a place as sea pickles, as many back as lay there, holding
 * the water they are set down in, made from cobblestone and made back into it, and drawn inside what a click finds.
 */
final class R8PebblesTests {
    private R8PebblesTests() {}

    static void register() {
        NookworksGameTests.test("pebbles_lie_one_to_four_in_a_place", R8PebblesTests::oneToFourInAPlace);
        NookworksGameTests.test("pebbles_give_back_as_many_as_lay_there", R8PebblesTests::asManyBackAsLayThere);
        NookworksGameTests.test("pebbles_keep_the_water_they_are_set_down_in", R8PebblesTests::keepTheirWater);
        NookworksGameTests.test("pebbles_are_cut_from_cobblestone_and_put_back", R8PebblesTests::cutFromCobblestone);
        NookworksGameTests.test("pebbles_are_drawn_inside_their_outline", R8PebblesTests::drawnInsideTheirOutline);
    }

    /** A survival player standing clear of the arena's blocks, holding {@code count} pebbles. */
    private static ServerPlayer holdingPebbles(GameTestHelper helper, int count) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new Vec3(0.5, 2, 0.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PEBBLES.get(), count));
        return player;
    }

    /** The pebbles in hand used on the top of {@code pos}, as a right-click on it. */
    private static void useOnTop(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        BlockPos at = helper.absolutePos(pos);
        helper.useBlock(pos, player, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
    }

    private static int pebblesAt(GameTestHelper helper, BlockPos pos) {
        BlockState state = helper.getBlockState(pos);
        return state.is(ModBlocks.PEBBLES.get()) ? state.getValue(PebblesBlock.PEBBLES) : 0;
    }

    /**
     * Set down on stone and used on again, they grow from one to four in the same place, a pebble from the hand each
     * time; a fifth goes nowhere - not into the four, nor on top of them - and stays in hand. Sneaking adds none, as
     * with sea pickles. They want a whole top face: not on a bottom slab.
     */
    private static void oneToFourInAPlace(GameTestHelper helper) {
        BlockPos ground = new BlockPos(2, 1, 2);
        BlockPos at = ground.above();
        helper.setBlock(ground, Blocks.STONE);
        ServerPlayer player = holdingPebbles(helper, 6);
        useOnTop(helper, player, ground);
        check(helper, pebblesAt(helper, at) == 1, "one set down on the stone: " + helper.getBlockState(at));
        for (int n = 2; n <= 4; n++) {
            useOnTop(helper, player, at);
            check(helper, pebblesAt(helper, at) == n, n + " in the one place: " + helper.getBlockState(at));
        }
        check(helper, player.getMainHandItem().getCount() == 2, "a pebble from the hand each time: " + player.getMainHandItem());
        useOnTop(helper, player, at);
        check(helper, pebblesAt(helper, at) == 4 && helper.getBlockState(at.above()).isAir(), "no fifth, in the four or on them");
        check(helper, player.getMainHandItem().getCount() == 2, "the fifth stays in hand");

        BlockPos second = new BlockPos(4, 1, 2);
        helper.setBlock(second, Blocks.STONE);
        useOnTop(helper, player, second);
        player.setShiftKeyDown(true);
        useOnTop(helper, player, second.above());
        player.setShiftKeyDown(false);
        check(helper, pebblesAt(helper, second.above()) == 1, "sneaking adds none: " + helper.getBlockState(second.above()));

        BlockPos slab = new BlockPos(2, 1, 4);
        helper.setBlock(slab, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        useOnTop(helper, player, slab);
        check(helper, pebblesAt(helper, slab.above()) == 0, "not on a bottom slab, whose top is not a whole face");
        helper.succeed();
    }

    /**
     * Broken, they give back as many as lay there, by hand or pickaxe alike, and at a touch. With their ground taken
     * away they come up the same, as many as there were. Nothing to bump into, and their stones are heard underfoot.
     */
    private static void asManyBackAsLayThere(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        BlockPos ground = new BlockPos(2, 1, 2);
        BlockPos at = ground.above();
        BlockPos abs = helper.absolutePos(at);
        helper.setBlock(ground, Blocks.STONE);
        for (int n = 1; n <= PebblesBlock.MAX; n++) {
            helper.setBlock(at, ModBlocks.PEBBLES.get().defaultBlockState().setValue(PebblesBlock.PEBBLES, n));
            BlockState state = level.getBlockState(abs);
            for (ItemStack tool : List.of(ItemStack.EMPTY, new ItemStack(Items.STONE_PICKAXE))) {
                int back = Block.getDrops(state, level, abs, null, player, tool).stream()
                        .filter(s -> s.is(ModItems.PEBBLES.get())).mapToInt(ItemStack::getCount).sum();
                check(helper, back == n, n + " lying there give " + back + " back with " + tool);
            }
        }
        BlockState four = ModBlocks.PEBBLES.get().defaultBlockState().setValue(PebblesBlock.PEBBLES, 4);
        check(helper, four.getDestroySpeed(level, abs) == 0 && !four.requiresCorrectToolForDrops(), "broken at a touch, by hand too");
        check(helper, four.is(BlockTags.MINEABLE_WITH_PICKAXE), "stone, so a pickaxe is its tool");
        check(helper, four.getCollisionShape(level, abs).isEmpty(), "nothing to bump into or step up onto");
        check(helper, four.is(BlockTags.COMBINATION_STEP_SOUND_BLOCKS), "heard over the ground under them");

        helper.setBlock(at, ModBlocks.PEBBLES.get().defaultBlockState().setValue(PebblesBlock.PEBBLES, 3));
        helper.setBlock(ground, Blocks.AIR);
        check(helper, helper.getBlockState(at).isAir(), "gone with their ground");
        int loose = helper.getEntities(EntityTypes.ITEM).stream().map(ItemEntity::getItem)
                .filter(s -> s.is(ModItems.PEBBLES.get())).mapToInt(ItemStack::getCount).sum();
        check(helper, loose == 3, "the three come up: " + loose);
        helper.succeed();
    }

    /**
     * Set down in water they hold it, and keep it as more are added; flowing water reaching dry ones runs up to them and
     * leaves them be, as it does sea pickles and candles.
     */
    private static void keepTheirWater(GameTestHelper helper) {
        // a pool of one: stone under it and round it
        BlockPos ground = new BlockPos(2, 1, 2);
        BlockPos pool = ground.above();
        helper.setBlock(ground, Blocks.STONE);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(pool.relative(side), Blocks.STONE);
        }
        helper.setBlock(pool, Blocks.WATER);
        ServerPlayer player = holdingPebbles(helper, 2);
        useOnTop(helper, player, ground);
        BlockState wet = helper.getBlockState(pool);
        check(helper, pebblesAt(helper, pool) == 1 && wet.getValue(PebblesBlock.WATERLOGGED), "set down in the water, they hold it: " + wet);
        useOnTop(helper, player, pool);
        wet = helper.getBlockState(pool);
        check(helper, pebblesAt(helper, pool) == 2 && wet.getValue(PebblesBlock.WATERLOGGED), "and keep it with two: " + wet);
        check(helper, helper.getLevel().getFluidState(helper.absolutePos(pool)).isSourceOfType(Fluids.WATER), "a source still");

        // a trough on the far side: a source at one end, dry pebbles at the other, the water free to run between
        for (int x = 1; x <= 3; x++) {
            helper.setBlock(new BlockPos(x, 1, 5), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 2, 4), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 2, 6), Blocks.STONE);
        }
        helper.setBlock(new BlockPos(0, 2, 5), Blocks.STONE);
        helper.setBlock(new BlockPos(4, 2, 5), Blocks.STONE);
        BlockPos dry = new BlockPos(3, 2, 5);
        helper.setBlock(dry, ModBlocks.PEBBLES.get().defaultBlockState().setValue(PebblesBlock.PEBBLES, 2));
        helper.setBlock(new BlockPos(1, 2, 5), Blocks.WATER);
        helper.runAfterDelay(30, () -> {
            check(helper, helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(2, 2, 5))).is(Fluids.FLOWING_WATER),
                    "the water ran up to them");
            check(helper, pebblesAt(helper, dry) == 2, "and left them lying: " + helper.getBlockState(dry));
            check(helper, !helper.getBlockState(dry).getValue(PebblesBlock.WATERLOGGED), "dry, as flowing water does not fill them");
            helper.succeed();
        });
    }

    /** One cobblestone makes four, crafted or on a stonecutter; four crafted together make the cobblestone again. */
    private static void cutFromCobblestone(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipes = level.getServer().getRecipeManager();
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);
        CraftingInput one = CraftingInput.of(1, 1, List.of(cobble));
        ItemStack crafted = recipes.getRecipeFor(RecipeType.CRAFTING, one, level).map(r -> r.value().assemble(one)).orElse(ItemStack.EMPTY);
        check(helper, crafted.is(ModItems.PEBBLES.get()) && crafted.getCount() == 4, "a cobblestone crafts into four: " + crafted);

        SingleRecipeInput cut = new SingleRecipeInput(cobble);
        boolean cutter = recipes.stonecutterRecipes().selectByInput(cobble).entries().stream()
                .flatMap(entry -> entry.recipe().recipe().stream())
                .map(holder -> holder.value().assemble(cut))
                .anyMatch(made -> made.is(ModItems.PEBBLES.get()) && made.getCount() == 4);
        check(helper, cutter, "the stonecutter cuts a cobblestone into four");

        ItemStack pebble = new ItemStack(ModItems.PEBBLES.get());
        CraftingInput four = CraftingInput.of(2, 2, List.of(pebble, pebble, pebble, pebble));
        ItemStack back = recipes.getRecipeFor(RecipeType.CRAFTING, four, level).map(r -> r.value().assemble(four)).orElse(ItemStack.EMPTY);
        check(helper, back.is(Items.COBBLESTONE) && back.getCount() == 1, "four make a cobblestone: " + back);
        helper.succeed();
    }

    /**
     * Every stone of each count is drawn inside the outline a click finds, however the block is turned: the models
     * (tools/textures/gen_pebbles.py) turn each stone by its own angle, the blockstate the whole by quarters, and the
     * outline is a square about the middle, so it is enough that each stone's turned foot lies in it unturned.
     */
    private static void drawnInsideTheirOutline(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
        for (int n = 1; n <= PebblesBlock.MAX; n++) {
            AABB outline = ModBlocks.PEBBLES.get().defaultBlockState().setValue(PebblesBlock.PEBBLES, n)
                    .getShape(level, abs).bounds();
            check(helper, Math.abs(outline.minX + outline.maxX - 1) < 1e-6 && Math.abs(outline.minZ + outline.maxZ - 1) < 1e-6
                    && Math.abs(outline.getXsize() - outline.getZsize()) < 1e-6, n + ": a square about the middle, " + outline);
            JsonObject model = JsonParser.parseString(MercuryTests.readAsset(helper, "/assets/nookworks/models/block/pebbles_" + n + ".json"))
                    .getAsJsonObject();
            JsonArray elements = model.getAsJsonArray("elements");
            Set<String> stones = new HashSet<>(); // each stone is turned about its own middle
            for (var e : elements) {
                stones.add(e.getAsJsonObject().getAsJsonObject("rotation").getAsJsonArray("origin").toString());
            }
            check(helper, stones.size() == n, n + " stones drawn: " + stones.size());
            for (var e : elements) {
                JsonObject element = e.getAsJsonObject();
                JsonArray from = element.getAsJsonArray("from");
                JsonArray to = element.getAsJsonArray("to");
                JsonObject rotation = element.getAsJsonObject("rotation");
                JsonArray origin = rotation.getAsJsonArray("origin");
                double angle = Math.toRadians(rotation.get("angle").getAsDouble());
                double ox = origin.get(0).getAsDouble();
                double oz = origin.get(2).getAsDouble();
                check(helper, to.get(1).getAsDouble() / 16 <= outline.maxY + 1e-6, n + ": a stone stands above the outline");
                for (double x : new double[] {from.get(0).getAsDouble(), to.get(0).getAsDouble()}) {
                    for (double z : new double[] {from.get(2).getAsDouble(), to.get(2).getAsDouble()}) {
                        // turned about y as the game turns an element
                        double tx = ox + (x - ox) * Math.cos(angle) + (z - oz) * Math.sin(angle);
                        double tz = oz - (x - ox) * Math.sin(angle) + (z - oz) * Math.cos(angle);
                        check(helper, tx / 16 >= outline.minX - 1e-6 && tx / 16 <= outline.maxX + 1e-6
                                && tz / 16 >= outline.minZ - 1e-6 && tz / 16 <= outline.maxZ + 1e-6,
                                n + ": a stone's corner at " + tx + ", " + tz + " lies outside " + outline);
                    }
                }
            }
        }
        helper.succeed();
    }
}
