package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.List;

import dev.nookworks.registry.ModRope;
import dev.nookworks.rope.HungItemEntity;
import dev.nookworks.rope.RopeKnotEntity;
import dev.nookworks.rope.RopeSpans;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Glow berries hung on a rope are a cave vine as set under a ceiling, bearing nothing yet, and grow down as vanilla's
 * does (the user, 2026-10-16): a block at a time while
 * there is room and it is young, up to {@link HungItemEntity#VINE_MAX} blocks; shears stop it; bone meal gives a bare
 * piece berries and an empty hand picks them; each grown piece with berries lights its block; saved and loaded as it is.
 */
final class HungVineTests {
    private HungVineTests() {}

    static void register() {
        NookworksGameTests.test("a_hung_vine_grows_down_until_something_stops_it", 40, HungVineTests::grows);
        NookworksGameTests.test("shears_stop_a_hung_vine_for_good", 40, HungVineTests::shears);
        NookworksGameTests.test("a_hung_vines_berries_are_picked_and_bone_mealed", 40, HungVineTests::berries);
        NookworksGameTests.test("a_hung_vine_is_saved_lit_and_dropped_as_it_grew", 120, HungVineTests::savedAndDropped);
        NookworksGameTests.test("a_hung_vines_lights_are_found_across_a_section_edge", 40, HungVineTests::acrossSections);
        NookworksGameTests.test("a_hung_vine_keeps_its_length_when_its_rope_is_let_down", 100, HungVineTests::ropeLetDown);
    }

    /** Two fence posts six apart, high in the air, a rope strung between them; the posts. */
    private static BlockPos[] span(GameTestHelper helper, ServerPlayer player, int y, int z) {
        BlockPos a = new BlockPos(0, y, z);
        BlockPos b = a.east(6);
        helper.setBlock(a, Blocks.OAK_FENCE);
        helper.setBlock(b, Blocks.OAK_FENCE);
        BlockPos from = helper.absolutePos(a);
        BlockPos to = helper.absolutePos(b);
        RopeSpans.tie(helper.getLevel(), player, from, player.getMainHandItem());
        RopeSpans.tie(helper.getLevel(), player, to, player.getMainHandItem());
        return new BlockPos[] {from, to};
    }

    private static HungItemEntity vine(ServerLevel level, BlockPos[] span, double along) {
        HungItemEntity vine = HungItemEntity.onSpan(level, span[0], span[1], along, new ItemStack(Items.GLOW_BERRIES));
        vine.setVineAge(0);
        level.addFreshEntity(vine);
        return vine;
    }

    /**
     * A vine five blocks long whose top is in one 16-block section and whose lower pieces are in the one below: each lit
     * piece is still found where it lights (its light stays), and nothing can be hung into its pieces from below.
     */
    private static void acrossSections(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = RopeTests.player(helper, 40);
        // high over the arena (its barrier roof is in the way of a grown vine), at three heights a block apart: the vine
        // stands a little under its rope, and one of the three has a section's edge between its top and lower pieces
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        int high = (corner.getY() + 100) / 16 * 16;
        boolean crossed = false;
        java.util.List<BlockPos> placed = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            BlockPos a = new BlockPos(corner.getX(), high + 1 + i, corner.getZ() + 2 * i);
            BlockPos b = a.east(6);
            level.setBlockAndUpdate(a, Blocks.OAK_FENCE.defaultBlockState());
            level.setBlockAndUpdate(b, Blocks.OAK_FENCE.defaultBlockState());
            placed.add(a);
            placed.add(b);
            RopeSpans.tie(level, player, a, player.getMainHandItem());
            RopeSpans.tie(level, player, b, player.getMainHandItem());
            HungItemEntity vine = vine(level, new BlockPos[] {a, b}, 0.5);
            RandomSource random = RandomSource.create(5);
            while (vine.growVine(level, random)) {
                // to its full length
            }
            check(helper, vine.vineLength() == HungItemEntity.VINE_MAX, "it grew to its full length: " + vine.vineLength());
            for (int piece = 1; piece < vine.vineLength(); piece++) {
                vine.setVineBerries(piece, true);
            }
            int sectionTop = net.minecraft.core.SectionPos.blockToSectionCoord(vine.getY());
            for (int piece = 1; piece < vine.vineLength(); piece++) {
                BlockPos at = vine.vineLightAt(piece);
                crossed |= net.minecraft.core.SectionPos.blockToSectionCoord(at.getY() + 1) < sectionTop;
                check(helper, HungItemEntity.lightsAt(level, at), "piece " + piece + " at " + at + " is found lighting its block");
            }
        }
        check(helper, crossed, "one of the vines should cross a section's edge");
        // what was set up over the arena goes: its posts, and so the lines and the vines
        level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new AABB(placed.get(0)).inflate(12),
                e -> e instanceof HungItemEntity || e instanceof dev.nookworks.rope.RopeKnotEntity).forEach(e -> e.discard());
        placed.forEach(pos -> level.removeBlock(pos, false));
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /** A vine grown three blocks under a Thin Rope's end: the rope let down a block, the vine goes down with it whole. */
    private static void ropeLetDown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ropeEnd = helper.absolutePos(new BlockPos(3, 9, 3));
        level.setBlockAndUpdate(ropeEnd.above(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(ropeEnd, dev.nookworks.registry.ModBlocks.THIN_ROPE.get().defaultBlockState().setValue(dev.nookworks.block.ThinRopeBlock.ANCHORED, true));
        HungItemEntity vine = HungItemEntity.underRope(level, ropeEnd, new ItemStack(Items.GLOW_BERRIES), 0.0F);
        vine.setVineAge(0);
        level.addFreshEntity(vine);
        RandomSource random = RandomSource.create(9);
        vine.growVine(level, random);
        vine.growVine(level, random);
        check(helper, vine.vineLength() == 3, "it grew three blocks: " + vine.vineLength());
        level.setBlockAndUpdate(ropeEnd.below(), dev.nookworks.registry.ModBlocks.THIN_ROPE.get().defaultBlockState());
        helper.succeedWhen(() -> {
            check(helper, vine.isRemoved(), "the vine should move to the rope's new end");
            var moved = level.getEntitiesOfClass(HungItemEntity.class, new AABB(ropeEnd).expandTowards(0, -8, 0), e -> !e.isRemoved());
            check(helper, moved.size() == 1 && moved.get(0).vineLength() == 3, "whole, three blocks long: "
                    + moved.stream().map(HungItemEntity::vineLength).toList());
        });
    }

    /** Where to click piece {@code piece} of {@code vine}, as the client sends it: from the entity's place. */
    private static Vec3 at(HungItemEntity vine, int piece) {
        Vec3 light = Vec3.atCenterOf(vine.vineLightAt(piece));
        return new Vec3(0, light.y - vine.getY(), 0);
    }

    /**
     * Forced to grow, it goes down a block at a time - age one on each - to five blocks and no further; one grown to 25
     * grows no more; a block or another hung thing below stops it, its pieces never reaching into them. A vine starts a
     * block long.
     */
    private static void grows(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = RopeTests.player(helper, 40);
        RandomSource random = RandomSource.create(7);
        BlockPos[] high = span(helper, player, 9, 1);
        HungItemEntity vine = vine(level, high, 0.5);
        double top = vine.getBoundingBox().maxY;
        for (int i = 1; i < HungItemEntity.VINE_MAX; i++) {
            check(helper, vine.growVine(level, random) && vine.vineLength() == i + 1 && vine.vineAge() == i, "it grows a block: " + i);
        }
        check(helper, !vine.growVine(level, random) && vine.vineLength() == HungItemEntity.VINE_MAX, "and stops at five blocks");
        check(helper, HungItemEntity.onSpan(level, high[0], high[1], 0.65, new ItemStack(Items.GLOW_BERRIES)).vineLength() == 1,
                "a vine starts a block long");
        check(helper, Math.abs(vine.getBoundingBox().maxY - top) < 1e-6
                && vine.getBoundingBox().getYsize() > HungItemEntity.VINE_MAX - 1, "its box reaches down with it: " + vine.getBoundingBox());

        HungItemEntity old = vine(level, high, 0.2);
        old.setVineAge(GrowingPlantHeadBlock.MAX_AGE - 1);
        check(helper, old.growVine(level, random) && old.vineAge() == GrowingPlantHeadBlock.MAX_AGE && !old.growVine(level, random)
                && old.vineLength() == 2, "a vine grown to 25 grows no more");

        HungItemEntity blocked = vine(level, high, 0.8);
        BlockPos below = blocked.vineLightAt(3); // in the third piece's way, not the second's
        level.setBlock(below, Blocks.STONE.defaultBlockState(), 3);
        check(helper, blocked.growVine(level, random) && !blocked.growVine(level, random) && blocked.vineLength() == 2,
                "a block below stops it");

        // a second span three blocks under the first, a bottle on it under the vine
        BlockPos[] low = span(helper, player, 6, 1);
        HungItemEntity bottle = HungItemEntity.onSpan(level, low[0], low[1], 0.5, new ItemStack(Items.GLASS_BOTTLE));
        level.addFreshEntity(bottle);
        vine.discard();
        HungItemEntity above = vine(level, high, 0.5);
        while (above.growVine(level, random)) {
            // as far as it goes
        }
        check(helper, above.vineLength() > 1 && above.vineLength() < HungItemEntity.VINE_MAX
                && !above.getBoundingBox().intersects(bottle.getBoundingBox()), "another hung thing below stops it short of it: " + above.vineLength());
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /** Shears on the tip stop it for good (age 25), worn a use; on a vine already stopped they do nothing. */
    private static void shears(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = RopeTests.player(helper, 40);
        BlockPos[] span = span(helper, player, 9, 1);
        HungItemEntity vine = vine(level, span, 0.5);
        RandomSource random = RandomSource.create(3);
        vine.growVine(level, random);
        ItemStack shears = new ItemStack(Items.SHEARS);
        player.setItemInHand(InteractionHand.MAIN_HAND, shears);
        check(helper, vine.interact(player, InteractionHand.MAIN_HAND, at(vine, 0)) != InteractionResult.SUCCESS
                || vine.vineAge() < GrowingPlantHeadBlock.MAX_AGE, "shears above the tip do not cut it");
        check(helper, vine.interact(player, InteractionHand.MAIN_HAND, at(vine, 1)) == InteractionResult.SUCCESS
                && vine.vineAge() == GrowingPlantHeadBlock.MAX_AGE && shears.getDamageValue() == 1 && !vine.isRemoved(),
                "shears on the tip stop it, worn a use");
        check(helper, !vine.growVine(level, random) && vine.vineLength() == 2, "and it grows no more");
        check(helper, vine.interact(player, InteractionHand.MAIN_HAND, at(vine, 1)) == InteractionResult.PASS && shears.getDamageValue() == 1,
                "shears on one already stopped do nothing");
        player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
        HungItemEntity other = vine(level, span, 0.2);
        check(helper, other.interact(player, InteractionHand.MAIN_HAND, at(other, 0)) == InteractionResult.FAIL
                && other.vineAge() < GrowingPlantHeadBlock.MAX_AGE, "not by an adventurer");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * An empty hand on a grown piece with berries picks them, as from vanilla's vine - glow berries dropped there, its
     * light gone; bone meal gives a bare piece berries and its light, and is spent; on one with berries it does nothing.
     */
    private static void berries(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = RopeTests.player(helper, 40);
        BlockPos[] span = span(helper, player, 9, 1);
        HungItemEntity vine = vine(level, span, 0.5);
        RandomSource random = RandomSource.create(11);
        vine.growVine(level, random);
        vine.growVine(level, random);
        check(helper, vine.vineLength() == 3, "grown two blocks");
        ItemStack meal = new ItemStack(Items.BONE_MEAL, 4);
        for (int piece = 1; piece < 3; piece++) {
            if (!vine.vineBerries(piece)) {
                player.setItemInHand(InteractionHand.MAIN_HAND, meal);
                int before = meal.getCount();
                check(helper, vine.interact(player, InteractionHand.MAIN_HAND, at(vine, piece)) == InteractionResult.SUCCESS
                        && vine.vineBerries(piece) && meal.getCount() == before - 1, "bone meal gives a bare piece berries: " + piece);
            }
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, meal);
        int left = meal.getCount();
        check(helper, vine.interact(player, InteractionHand.MAIN_HAND, at(vine, 1)) == InteractionResult.PASS && meal.getCount() == left,
                "on a piece with berries bone meal does nothing");
        BlockPos lit = vine.vineLightAt(2);
        check(helper, level.getBlockState(lit).is(ModRope.HUNG_LIGHT.get()) && level.getBlockState(lit).getValue(LightBlock.LEVEL) == 14,
                "a grown piece with berries lights its block at 14");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(helper, vine.interact(player, InteractionHand.MAIN_HAND, at(vine, 2)) == InteractionResult.SUCCESS && !vine.vineBerries(2)
                && !vine.isRemoved() && vine.vineLength() == 3, "an empty hand picks a piece's berries, the vine left hanging");
        List<ItemEntity> picked = level.getEntitiesOfClass(ItemEntity.class, new AABB(lit).inflate(1), e -> e.getItem().is(Items.GLOW_BERRIES));
        check(helper, picked.size() == 1 && picked.get(0).getItem().getCount() == 1, "glow berries drop where they were picked");
        check(helper, !level.getBlockState(lit).is(ModRope.HUNG_LIGHT.get()) || lit.equals(vine.litAt()), "and its light goes");
        check(helper, vine.interact(player, InteractionHand.MAIN_HAND, at(vine, 2)) == InteractionResult.SUCCESS && vine.isRemoved()
                && player.getInventory().countItem(Items.GLOW_BERRIES) == 2,
                "a bare piece clicked takes the vine down: the berries hung, and the one piece still bearing");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Saved and loaded, a vine is as long as it was, its berries and age with it; unloaded and back, it takes up its
     * pieces' light again; its rope cut, it drops the berries it was hung with and one for each piece bearing.
     */
    private static void savedAndDropped(GameTestHelper helper) {
        BlockPos a = helper.absolutePos(new BlockPos(0, 9, 1));
        BlockPos b = helper.absolutePos(new BlockPos(6, 9, 1));
        R9RopeTests.onceLoaded(helper, a, b, () -> savedAndDroppedLoaded(helper));
    }

    private static void savedAndDroppedLoaded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = RopeTests.player(helper, 40);
        BlockPos[] span = span(helper, player, 9, 1);
        HungItemEntity vine = vine(level, span, 0.5);
        RandomSource random = RandomSource.create(5);
        vine.growVine(level, random);
        vine.growVine(level, random);
        ItemStack meal = new ItemStack(Items.BONE_MEAL, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, meal);
        for (int piece = 1; piece < 3; piece++) {
            if (!vine.vineBerries(piece)) {
                vine.interact(player, InteractionHand.MAIN_HAND, at(vine, piece));
            }
        }
        check(helper, vine.vineBerries(1) && vine.vineBerries(2), "both grown pieces bear berries");
        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        vine.saveWithoutId(saved);
        BlockPos lit = vine.vineLightAt(1);
        vine.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        check(helper, level.getBlockState(lit).is(ModRope.HUNG_LIGHT.get()), "unloaded, its light stays");
        HungItemEntity loaded = new HungItemEntity(ModRope.HUNG_ITEM.get(), level);
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved.buildResult()));
        check(helper, loaded.vineLength() == 3 && loaded.vineBerries(1) && loaded.vineBerries(2) && loaded.vineAge() == 2,
                "loaded as long as it was, its berries and age with it");
        net.minecraft.nbt.CompoundTag old = saved.buildResult().copy();
        old.remove("vine_length");
        old.remove("vine_berries");
        old.remove("vine_age");
        HungItemEntity before = new HungItemEntity(ModRope.HUNG_ITEM.get(), level);
        before.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), old));
        check(helper, before.vineLength() == 1 && before.vineBerries(0) && before.glow() == 14,
                "glow berries hung before vines grew keep their berries, and their light");
        level.addFreshEntity(loaded);
        loaded.lightVineNow(level);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        loaded.interact(player, InteractionHand.MAIN_HAND, at(loaded, 1));
        check(helper, !loaded.vineBerries(1) && (!level.getBlockState(lit).is(ModRope.HUNG_LIGHT.get()) || lit.equals(loaded.litAt())),
                "loaded, it took its pieces' light up again as its own: picked, the light goes");
        level.getEntitiesOfClass(ItemEntity.class, new AABB(span[0]).minmax(new AABB(span[1])).inflate(8)).forEach(ItemEntity::discard);
        player.attack(RopeKnotEntity.at(level, span[0]));
        level.getServer().getPlayerList().remove(player);
        helper.runAfterDelay(25, () -> {
            int dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(span[0]).minmax(new AABB(span[1])).inflate(8),
                    e -> e.getItem().is(Items.GLOW_BERRIES)).stream().mapToInt(e -> e.getItem().getCount()).sum();
            check(helper, loaded.isRemoved() && dropped == 2, "its rope cut, it drops the berries hung and the piece still bearing: " + dropped);
            check(helper, !level.getBlockState(loaded.vineLightAt(2)).is(ModRope.HUNG_LIGHT.get()), "and its light goes with it");
            helper.succeed();
        });
    }
}
