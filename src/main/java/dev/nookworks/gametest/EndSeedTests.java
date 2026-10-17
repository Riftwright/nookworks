package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.nookworks.crop.BloomingCropBlock;
import dev.nookworks.crop.CornBlock;
import dev.nookworks.crop.EndSeedPlant;
import dev.nookworks.machine.LootSummaries;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The End Seed (the user, 2026-10-12): sifted from End Gravel alone, one in five hundred; sown in farmland, a
 * sprout that opens into a plant drawn by lot from the nookworks:end_seed data map.
 */
final class EndSeedTests {
    static void register() {
        test("end_seed_sifts_from_end_gravel_alone", EndSeedTests::siftedFromEndGravel);
        test("end_seed_draws_by_weight", EndSeedTests::drawsByWeight);
        test("end_sprout_opens_into_what_it_draws", EndSeedTests::opensIntoWhatItDraws);
        test("end_sprout_outline_is_as_tall_as_drawn", EndSeedTests::outlineAsDrawn);
    }

    /**
     * The sprout is drawn small (the user, 2026-10-14), so its outline is not the sunflower crop's: at each stage it is
     * as tall as the stage's texture is drawn, so the box shown on it neither clips the leaves nor stands empty above.
     */
    private static void outlineAsDrawn(GameTestHelper helper) {
        var sprout = ModCrops.END_SPROUT.get();
        for (int age = 0; age < BloomingCropBlock.BLOOM; age++) {
            String path = "/assets/nookworks/textures/block/end_sprout_stage" + age + ".png";
            int drawn = 0;
            try (var in = EndSeedTests.class.getResourceAsStream(path)) {
                check(helper, in != null, "missing " + path);
                var image = javax.imageio.ImageIO.read(in);
                for (int y = 0; y < image.getHeight() && drawn == 0; y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        if ((image.getRGB(x, y) >>> 24) != 0) {
                            drawn = image.getHeight() - y;
                            break;
                        }
                    }
                }
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
            BlockState state = sprout.getStateForAge(age);
            double top = state.getShape(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)).max(Direction.Axis.Y) * 16;
            check(helper, Math.abs(top - drawn) < 1e-6, "stage " + age + ": outline " + top + " px, drawn " + drawn + " px");
        }
        helper.succeed();
    }

    private static void siftedFromEndGravel(GameTestHelper helper) {
        var item = ModCrops.END_SEED.get();
        double fromEnd = LootSummaries.sifted(BuiltInRegistries.ITEM.getKey(ModItems.END_GRAVEL.get()), false).stream()
                .filter(d -> d.stack().is(item)).mapToDouble(LootSummaries.Drop::chance).sum();
        check(helper, Math.abs(fromEnd - 0.002) < 1e-9, "one End Gravel in five hundred: " + fromEnd);
        for (var ground : List.of(Items.DIRT, Items.GRAVEL, Items.SAND, Items.SOUL_SAND, Items.COARSE_DIRT)) {
            for (boolean fine : new boolean[] {false, true}) {
                check(helper, LootSummaries.sifted(BuiltInRegistries.ITEM.getKey(ground), fine).stream().noneMatch(d -> d.stack().is(item)),
                        "nothing else sifts it: " + ground);
            }
        }
        helper.succeed();
    }

    /**
     * Every plant in the draw is one a field can grow; flowers and ferns most often, the wither rose least, the
     * sniffer's torchflower and the crafted golden dandelion never. Drawn many times, the shares follow the weights.
     */
    private static void drawsByWeight(GameTestHelper helper) {
        var map = BuiltInRegistries.BLOCK.getDataMap(EndSeedPlant.DATA_MAP);
        Map<Block, Integer> weights = new HashMap<>();
        map.forEach((key, plant) -> {
            Block block = BuiltInRegistries.BLOCK.getValue(key);
            check(helper, block != null && plant.state(block) != null, "every plant of the draw resolves: " + key + " " + plant);
            weights.put(block, plant.weight());
        });
        for (Block there : List.of(Blocks.POPPY, Blocks.ALLIUM, Blocks.FERN, Blocks.LILAC, Blocks.PITCHER_PLANT, Blocks.BAMBOO,
                Blocks.SWEET_BERRY_BUSH, ModKitchen.CORN_CROP.get(), Blocks.WITHER_ROSE)) {
            check(helper, weights.containsKey(there), "the draw has " + there);
        }
        for (Block never : List.of(Blocks.TORCHFLOWER, Blocks.GOLDEN_DANDELION, Blocks.SPORE_BLOSSOM, Blocks.CACTUS_FLOWER,
                ModCrops.RICE_CROP.get(), ModCrops.END_SPROUT.get())) {
            check(helper, !weights.containsKey(never), "the draw has no " + never);
        }
        int rose = weights.get(Blocks.WITHER_ROSE);
        check(helper, weights.values().stream().allMatch(w -> w >= rose) && weights.get(Blocks.POPPY) > weights.get(Blocks.LILAC),
                "small flowers come most often, the wither rose least: " + weights);

        BlockPos open = helper.absolutePos(new BlockPos(3, 2, 3));
        var pool = EndSeedPlant.fitting(helper.getLevel(), open);
        Map<Block, Integer> drawn = new HashMap<>();
        for (int i = 0; i < 20_000; i++) {
            drawn.merge(EndSeedPlant.draw(pool, helper.getLevel().getRandom()).state().getBlock(), 1, Integer::sum);
        }
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        double poppy = drawn.getOrDefault(Blocks.POPPY, 0) / 20_000.0;
        double roses = drawn.getOrDefault(Blocks.WITHER_ROSE, 0) / 20_000.0;
        check(helper, drawn.size() == weights.size(), "all of them come up: " + drawn.size() + " of " + weights.size());
        check(helper, Math.abs(poppy - 10.0 / total) < 0.01 && roses < 3.0 / total, "at their weights: poppy " + poppy + ", wither rose " + roses);
        helper.succeed();
    }

    /**
     * Sown as a player sows it and grown with bone meal: a bud, and then a plant of the draw in its place - one plant,
     * never another seed. A flower stays on the farmland, bamboo gets dirt under it, ripe corn and a lilac both halves;
     * under a roof only a short plant is drawn; a plant that stands on neither leaves the bud and the farmland as they were.
     */
    private static void opensIntoWhatItDraws(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var sprout = ModCrops.END_SPROUT.get();
        check(helper, sprout.defaultBlockState().is(BlockTags.CROPS) && sprout.defaultBlockState().is(BlockTags.MAINTAINS_FARMLAND),
                "a crop, that keeps its farmland");

        BlockPos field = new BlockPos(1, 1, 1);
        BlockPos roofed = new BlockPos(3, 1, 1);
        for (BlockPos soil : List.of(field, roofed)) {
            helper.setBlock(soil, Blocks.FARMLAND);
        }
        helper.setBlock(roofed.above(2), Blocks.STONE);
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 aside = helper.absoluteVec(new Vec3(0.5, 8, 0.5));
        player.snapTo(aside.x, aside.y, aside.z);
        for (BlockPos soil : List.of(field, roofed)) {
            ItemStack hand = new ItemStack(ModCrops.END_SEED.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, hand);
            player.gameMode.useItemOn(player, level, hand, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(soil)), Direction.UP, helper.absolutePos(soil), false));
            check(helper, helper.getBlockState(soil.above()).is(sprout) && hand.isEmpty(), "a seed is sown");
        }
        level.getServer().getPlayerList().remove(player);
        for (int stage = 1; stage < BloomingCropBlock.BLOOM; stage++) {
            for (BlockPos soil : List.of(field, roofed)) {
                sprout.performBonemeal(level, level.getRandom(), helper.absolutePos(soil.above()), helper.getBlockState(soil.above()), BonemealSource.INTERACTION);
                check(helper, helper.getBlockState(soil.above()).getValue(BloomingCropBlock.AGE) == stage, "a stage a bone meal: " + stage);
            }
        }
        BlockState bud = helper.getBlockState(roofed.above());
        check(helper, bud.getCloneItemStack(level, helper.absolutePos(roofed.above()), false).is(ModCrops.END_SEED.get()),
                "picking the bud gives a seed");
        check(helper, Block.getDrops(bud, level, helper.absolutePos(roofed.above()), null).stream()
                .allMatch(s -> s.is(ModCrops.END_SEED.get()) && s.getCount() == 1), "broken, it gives its seed back, one");
        check(helper, sprout.isValidBonemealTarget(level, helper.absolutePos(roofed.above()), bud, BonemealSource.INTERACTION), "under a roof it can still open, short");
        var drawn = BuiltInRegistries.BLOCK.getDataMap(EndSeedPlant.DATA_MAP).keySet();
        for (BlockPos soil : List.of(field, roofed)) {
            sprout.performBonemeal(level, level.getRandom(), helper.absolutePos(soil.above()), helper.getBlockState(soil.above()), BonemealSource.INTERACTION);
            BlockState plant = helper.getBlockState(soil.above());
            check(helper, drawn.contains(plant.getBlock().builtInRegistryHolder().key()), "it opens into a plant of the draw: " + plant);
        }
        check(helper, !EndSeedPlant.isTall(helper.getBlockState(roofed.above())), "under the roof, a short one");

        // the plants one by one, each in a bud of its own
        List<BlockPos> beds = new ArrayList<>();
        for (int x = 0; x < 6; x++) {
            BlockPos soil = new BlockPos(x, 1, 4);
            helper.setBlock(soil, Blocks.FARMLAND);
            helper.setBlock(soil.above(), sprout.getStateForAge(BloomingCropBlock.BLOOM - 1));
            beds.add(soil);
        }
        check(helper, EndSeedPlant.open(level, helper.absolutePos(beds.get(0).above()), Blocks.POPPY.defaultBlockState())
                && helper.getBlockState(beds.get(0).above()).is(Blocks.POPPY) && helper.getBlockState(beds.get(0)).is(Blocks.FARMLAND),
                "a poppy stands in the farmland");
        check(helper, EndSeedPlant.open(level, helper.absolutePos(beds.get(1).above()), Blocks.BAMBOO.defaultBlockState())
                && helper.getBlockState(beds.get(1).above()).is(Blocks.BAMBOO) && helper.getBlockState(beds.get(1)).is(Blocks.DIRT),
                "bamboo gets dirt under it");
        BlockState corn = ModKitchen.CORN_CROP.get().defaultBlockState().setValue(CornBlock.AGE, CornBlock.MAX_AGE);
        check(helper, EndSeedPlant.open(level, helper.absolutePos(beds.get(2).above()), corn)
                && helper.getBlockState(beds.get(2).above()).is(ModKitchen.CORN_CROP.get())
                && helper.getBlockState(beds.get(2).above(2)).getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER
                && helper.getBlockState(beds.get(2)).is(Blocks.FARMLAND), "ripe corn, both halves, in its farmland");
        check(helper, EndSeedPlant.open(level, helper.absolutePos(beds.get(3).above()), Blocks.LILAC.defaultBlockState())
                && helper.getBlockState(beds.get(3).above(2)).is(Blocks.LILAC), "a lilac, both halves");
        BlockState berries = Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(BlockStateProperties.AGE_3, 2);
        check(helper, EndSeedPlant.open(level, helper.absolutePos(beds.get(4).above()), berries)
                && helper.getBlockState(beds.get(4).above()).equals(berries), "a sweet berry bush, bearing");
        check(helper, !EndSeedPlant.open(level, helper.absolutePos(beds.get(5).above()), Blocks.NETHER_WART.defaultBlockState())
                && helper.getBlockState(beds.get(5).above()).is(sprout) && helper.getBlockState(beds.get(5)).is(Blocks.FARMLAND),
                "nether wart stands on neither: the bud and its farmland stay");
        helper.succeed();
    }

    private EndSeedTests() {}
}
