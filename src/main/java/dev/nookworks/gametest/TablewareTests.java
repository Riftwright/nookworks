package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import dev.nookworks.cooking.PlateBlockEntity;
import dev.nookworks.decor.TablewareBlock;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 1.3's tableware (the user, 2026-10-31): a Plate takes one dish and gives it back, and goes with the table under it;
 * a wine glass or mug clicked onto another joins it, up to four, and four drop when the block is broken; the dish
 * models the renderer draws are in the jar with their textures.
 */
final class TablewareTests {
    private TablewareTests() {}

    static void register() {
        test("a_plate_takes_one_dish_and_gives_it_back", TablewareTests::plateServes);
        test("tableware_stands_four_to_a_block_and_drops_them_all", TablewareTests::fourToABlock);
        test("the_dish_models_are_in_the_jar", TablewareTests::dishModels);
    }

    private static BlockHitResult hitTop(GameTestHelper helper, BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP, helper.absolutePos(pos), false);
    }

    private static void plateServes(GameTestHelper helper) {
        BlockPos table = new BlockPos(1, 1, 1);
        BlockPos at = table.above();
        helper.setBlock(table, Blocks.OAK_PLANKS);
        helper.setBlock(at, ModKitchen.PLATE.get());
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
        helper.getBlockState(at).useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hitTop(helper, at));
        PlateBlockEntity plate = (PlateBlockEntity) helper.getBlockEntity(at, PlateBlockEntity.class);
        check(helper, plate.isEmpty(), "what is not food does not go on a plate");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModCooking.TONKATSU.get(), 3));
        helper.getBlockState(at).useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hitTop(helper, at));
        check(helper, plate.food().is(ModCooking.TONKATSU.get()) && plate.food().getCount() == 1, "one tonkatsu is served: " + plate.food());
        check(helper, player.getMainHandItem().getCount() == 2, "and leaves the hand");
        helper.getBlockState(at).useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hitTop(helper, at));
        check(helper, player.getMainHandItem().getCount() == 2, "a plate takes one dish only");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.getBlockState(at).useWithoutItem(helper.getLevel(), player, hitTop(helper, at));
        check(helper, plate.isEmpty() && player.getInventory().countItem(ModCooking.TONKATSU.get()) == 1, "an empty hand takes it back");
        helper.setBlock(table, Blocks.AIR);
        helper.assertBlockNotPresent(ModKitchen.PLATE.get(), at);
        helper.succeed();
    }

    private static void fourToABlock(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.OAK_PLANKS);
        for (var block : new TablewareBlock[] {ModBlocks.WINE_GLASS.get(), ModBlocks.MUG.get()}) {
            helper.setBlock(at, block.defaultBlockState().setValue(TablewareBlock.COUNT, 4));
            var abs = helper.absolutePos(at);
            var drops = Block.getDrops(helper.getBlockState(at), helper.getLevel(), abs, null);
            check(helper, drops.size() == 1 && drops.getFirst().getCount() == 4 && drops.getFirst().is(block.asItem()),
                    block + ": four drop from four: " + drops);
            helper.setBlock(at, block.defaultBlockState());
            drops = Block.getDrops(helper.getBlockState(at), helper.getLevel(), abs, null);
            check(helper, drops.size() == 1 && drops.getFirst().getCount() == 1, block + ": one from one: " + drops);
        }
        check(helper, ModItems.WINE_GLASS.get() != ModItems.MUG.get(), "two kinds");
        helper.succeed();
    }

    private static void dishModels(GameTestHelper helper) {
        for (String dish : new String[] {"udon", "tonkatsu", "karaage", "donut"}) {
            String path = "/assets/nookworks/models/block/dish_" + dish + ".json";
            try (var in = TablewareTests.class.getResourceAsStream(path)) {
                check(helper, in != null, "the model " + path + " is in the jar");
                String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                for (var m : java.util.regex.Pattern.compile("nookworks:block/([a-z_]+)").matcher(json).results().toList()) {
                    String texture = "/assets/nookworks/textures/block/" + m.group(1) + ".png";
                    check(helper, TablewareTests.class.getResource(texture) != null, dish + ": its texture " + texture + " is there");
                }
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
        helper.succeed();
    }
}
