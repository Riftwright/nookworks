package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.List;

import com.mojang.logging.LogUtils;

import dev.nookworks.block.Clicks;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModRope;
import dev.nookworks.rope.HungBannerEntity;
import dev.nookworks.rope.HungItemEntity;
import dev.nookworks.rope.RopeHanging;
import dev.nookworks.rope.RopeKnotEntity;
import dev.nookworks.rope.RopeSpans;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * r8: banners hung on Thin Rope (the user, 2026-10-06) - on a span by their bar, from a rope's end by a cord - kept as
 * they were, patterns and all; refused where there is no room for the cloth, too near a post or the next thing, or on a
 * steep rope; and down they come with the rope.
 */
final class R8RopeBannersTests {
    private R8RopeBannersTests() {}

    static void register() {
        NookworksGameTests.test("a_hung_banner_keeps_its_patterns_and_gives_them_back", R8RopeBannersTests::keepsItsPatterns);
        NookworksGameTests.test("a_banner_needs_room_below_and_along_the_rope", R8RopeBannersTests::needsRoom);
        NookworksGameTests.test("a_hung_banner_falls_when_its_rope_is_cut", 120, R8RopeBannersTests::fallsWithItsRope);
        NookworksGameTests.test("a_banner_hangs_from_a_rope_end_by_a_cord", 60, R8RopeBannersTests::fromARopeEnd);
    }

    /** A red banner with a white top stripe and a black creeper on it, from the loom. */
    private static ItemStack patterned(GameTestHelper helper) {
        var patterns = helper.getLevel().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
        ItemStack banner = new ItemStack(Items.BANNER.red());
        banner.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder()
                .add(patterns.getOrThrow(BannerPatterns.STRIPE_TOP), DyeColor.WHITE)
                .add(patterns.getOrThrow(BannerPatterns.CREEPER), DyeColor.BLACK).build());
        return banner;
    }

    /** Two fences {@code apart} blocks apart along x at height {@code y}, row {@code z}, strung by {@code player}. */
    private static BlockPos[] strung(GameTestHelper helper, ServerPlayer player, int y, int z, int apart) {
        return strung(helper, player, new BlockPos(0, y, z), new BlockPos(apart, y, z));
    }

    /** Fences at {@code a} and {@code b}, strung by {@code player} with rope put in their hand. */
    private static BlockPos[] strung(GameTestHelper helper, ServerPlayer player, BlockPos a, BlockPos b) {
        helper.setBlock(a, Blocks.OAK_FENCE);
        helper.setBlock(b, Blocks.OAK_FENCE);
        BlockPos from = helper.absolutePos(a);
        BlockPos to = helper.absolutePos(b);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.THIN_ROPE.get(), 64));
        RopeSpans.tie(helper.getLevel(), player, from, player.getMainHandItem());
        RopeSpans.tie(helper.getLevel(), player, to, player.getMainHandItem());
        check(helper, RopeKnotEntity.at(helper.getLevel(), from) != null && RopeKnotEntity.at(helper.getLevel(), from).ties(to),
                "strung from " + a + " to " + b);
        return new BlockPos[] {from, to};
    }

    /** Whether two boxes are the same, to a millionth. */
    private static boolean same(AABB a, AABB b) {
        return Math.abs(a.minX - b.minX) < 1.0E-6 && Math.abs(a.minY - b.minY) < 1.0E-6 && Math.abs(a.minZ - b.minZ) < 1.0E-6
                && Math.abs(a.maxX - b.maxX) < 1.0E-6 && Math.abs(a.maxY - b.maxY) < 1.0E-6 && Math.abs(a.maxZ - b.maxZ) < 1.0E-6;
    }

    /** The way a banner's face looks out, by its yaw: as a wall banner facing south at 0. */
    private static Vec3 face(HungItemEntity banner) {
        double yaw = Math.toRadians(banner.yaw());
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    /**
     * Used on a rope strung between fences by a player looking at it, a patterned banner hangs there: one of its own
     * kind, the same banner, its bar just under the rope, its box thin across the rope, its face to the player. Saved
     * and loaded it is the same. An empty hand takes it down as it was; hung again, a hit knocks it off and it drops as
     * it was.
     */
    private static void keepsItsPatterns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = RopeTests.player(helper, 0);
        BlockPos[] posts = strung(helper, player, 5, 4, 5);
        ItemStack patterned = patterned(helper);
        Vec3 target = RopeSpans.at(level, posts[0], posts[1], 0.5);
        player.snapTo(target.x, target.y - 1.62, target.z - 2.0, 0.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, patterned.copy());
        check(helper, RopeHanging.onSpan(level, player, player.getMainHandItem()) == InteractionResult.SUCCESS
                && player.getMainHandItem().isEmpty(), "the banner goes on the rope where the player looks");
        List<HungBannerEntity> hung = level.getEntitiesOfClass(HungBannerEntity.class, helper.getBounds());
        check(helper, hung.size() == 1 && level.getEntitiesOfClass(HungItemEntity.class, helper.getBounds()).size() == 1,
                "it hangs as a banner, and as nothing else: " + hung);
        HungBannerEntity banner = hung.getFirst();
        check(helper, ItemStack.isSameItemSameComponents(banner.item(), patterned), "the same banner, its patterns kept: " + banner.item());
        AABB box = banner.getBoundingBox();
        check(helper, box.maxY < target.y && box.maxY > target.y - 0.1, "its bar just under the rope: " + box.maxY + ", the rope at " + target.y);
        check(helper, box.maxY - box.minY > 1.6 && box.maxX - box.minX > 0.8 && box.maxZ - box.minZ < 0.4,
                "a banner's size, thin across the rope: " + box);
        check(helper, Math.abs(Mth.wrapDegrees(banner.yaw())) < 0.01 || Math.abs(Mth.wrapDegrees(banner.yaw() - 180)) < 0.01,
                "side on to the rope, which runs along x: " + banner.yaw());
        check(helper, face(banner).dot(player.position().subtract(banner.position())) > 0, "its face to whoever hung it");
        check(helper, Math.abs(banner.slope()) < 0.05, "level at the rope's lowest: " + banner.slope());

        try (var reporter = new ProblemReporter.ScopedCollector(banner.problemPath(), LogUtils.getLogger())) {
            var output = TagValueOutput.createWithContext(reporter, level.registryAccess());
            banner.saveWithoutId(output);
            HungBannerEntity copy = new HungBannerEntity(ModRope.HUNG_BANNER.get(), level);
            copy.load(TagValueInput.create(reporter, level.registryAccess(), output.buildResult()));
            check(helper, ItemStack.isSameItemSameComponents(copy.item(), patterned) && copy.yaw() == banner.yaw()
                    && copy.slope() == banner.slope() && posts[0].equals(copy.spanFrom()) && posts[1].equals(copy.spanTo())
                    && copy.along() == banner.along() && same(copy.getBoundingBox(), box),
                    "saved and loaded, it is the same banner hung the same way: " + copy.getBoundingBox() + " for " + box);
        }

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(helper, banner.interact(player, InteractionHand.MAIN_HAND, banner.position()) == InteractionResult.SUCCESS && banner.isRemoved(),
                "an empty hand takes it down");
        check(helper, player.getInventory().findSlotMatchingItem(patterned) >= 0, "and has the banner back as it was");

        HungItemEntity again = HungItemEntity.onSpan(level, posts[0], posts[1], 0.5, patterned, player.position());
        level.addFreshEntity(again);
        check(helper, again instanceof HungBannerEntity, "hung again by the factory, a banner again");
        again.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
        List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds());
        check(helper, again.isRemoved() && dropped.size() == 1 && ItemStack.isSameItemSameComponents(dropped.getFirst().getItem(), patterned),
                "a hit knocks it off, and it drops as it was: " + dropped);
        helper.succeed();
    }

    /**
     * A banner needs its cloth's height clear under the rope: over a low rope, where a bottle hangs, it is refused and
     * stays in hand. Along a rope it keeps its width: from a post, from another banner, from a bottle, counted across the
     * ground. A steep rope takes none.
     */
    private static void needsRoom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = RopeTests.player(helper, 0);

        // a rope two blocks up: the cloth would reach into the floor
        BlockPos[] low = strung(helper, player, 2, 2, 4);
        Vec3 target = RopeSpans.at(level, low[0], low[1], 0.5);
        player.snapTo(target.x, target.y - 1.62, target.z - 2.0, 0.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, patterned(helper));
        check(helper, RopeHanging.onSpan(level, player, player.getMainHandItem()) == InteractionResult.FAIL
                && player.getMainHandItem().getCount() == 1, "over a low rope a banner is refused, and stays in hand");
        check(helper, HungItemEntity.onSpan(level, low[0], low[1], 0.5, patterned(helper)).misfit(level) != null,
                "no room under it there");
        check(helper, level.getEntitiesOfClass(HungBannerEntity.class, helper.getBounds()).isEmpty(), "nothing hangs");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
        check(helper, RopeHanging.onSpan(level, player, player.getMainHandItem()) == InteractionResult.SUCCESS,
                "a bottle hangs there all the same");

        // along a high rope, six blocks
        BlockPos[] high = strung(helper, player, 5, 4, 6);
        Vec3 a = RopeSpans.tie(level, high[0]);
        Vec3 b = RopeSpans.tie(level, high[1]);
        double run = Math.hypot(b.x - a.x, b.z - a.z);
        check(helper, RopeHanging.tooNear(level, high[0], high[1], 0.6 / run, RopeSpans.at(a, b, 0.6 / run), HungBannerEntity.ROOM, true)
                && !RopeHanging.tooNear(level, high[0], high[1], 0.7 / run, RopeSpans.at(a, b, 0.7 / run), HungBannerEntity.ROOM, true),
                "a banner keeps 0.65 from a post");
        HungItemEntity first = HungItemEntity.onSpan(level, high[0], high[1], 0.5, patterned(helper));
        check(helper, first.misfit(level) == null, "a high rope has room under it");
        level.addFreshEntity(first);
        double near = 0.5 + 0.85 / run;
        double clear = 0.5 + 0.95 / run;
        check(helper, RopeHanging.tooNear(level, high[0], high[1], near, RopeSpans.at(a, b, near), HungBannerEntity.ROOM, true)
                && !RopeHanging.tooNear(level, high[0], high[1], clear, RopeSpans.at(a, b, clear), HungBannerEntity.ROOM, true),
                "two banners keep their width apart, a little more");
        double bottleNear = 0.5 - 0.6 / run;
        double bottleClear = 0.5 - 0.7 / run;
        check(helper, RopeHanging.tooNear(level, high[0], high[1], bottleNear, RopeSpans.at(a, b, bottleNear), HungItemEntity.ROOM, false)
                && !RopeHanging.tooNear(level, high[0], high[1], bottleClear, RopeSpans.at(a, b, bottleClear), HungItemEntity.ROOM, false),
                "a bottle keeps half a banner and its own room from one");
        HungItemEntity second = HungItemEntity.onSpan(level, high[0], high[1], clear, patterned(helper));
        level.addFreshEntity(second);
        check(helper, !second.getBoundingBox().intersects(first.getBoundingBox()), "side by side, the two do not touch");

        // a rope from a fence on the floor up to one seven blocks higher, two along: far too steep
        BlockPos[] steepPosts = strung(helper, player, new BlockPos(1, 1, 6), new BlockPos(3, 8, 6));
        BlockPos foot = steepPosts[0];
        BlockPos top = steepPosts[1];
        HungItemEntity steep = HungItemEntity.onSpan(level, foot, top, 0.5, patterned(helper));
        check(helper, "message.nookworks.rope.too_steep".equals(steep.misfit(level)), "a steep rope takes no banner: " + steep.misfit(level));
        check(helper, HungItemEntity.onSpan(level, foot, top, 0.5, new ItemStack(Items.GLASS_BOTTLE)).misfit(level) == null,
                "a bottle hangs on it");
        helper.succeed();
    }

    /**
     * A banner hangs on a rope until the rope goes: the knot cut, it falls and drops as it was; on another span, the far
     * post broken, the same.
     */
    private static void fallsWithItsRope(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = RopeTests.player(helper, 0);
        BlockPos[] cut = strung(helper, player, 5, 1, 5);
        BlockPos[] broken = strung(helper, player, 5, 5, 5);
        ItemStack patterned = patterned(helper);
        HungItemEntity one = HungItemEntity.onSpan(level, cut[0], cut[1], 0.5, patterned);
        HungItemEntity two = HungItemEntity.onSpan(level, broken[0], broken[1], 0.5, patterned);
        level.addFreshEntity(one);
        level.addFreshEntity(two);
        RopeKnotEntity knot = RopeKnotEntity.at(level, cut[0]);
        knot.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
        check(helper, knot.isRemoved(), "the knot is cut");
        helper.setBlock(new BlockPos(5, 5, 5), Blocks.AIR); // the far post of the other span
        helper.runAfterDelay(70, () -> {
            check(helper, one.isRemoved() && two.isRemoved(), "both banners came down");
            List<ItemEntity> banners = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds(),
                    item -> item.getItem().is(Items.BANNER.red()));
            check(helper, banners.size() == 2 && banners.stream().allMatch(e -> ItemStack.isSameItemSameComponents(e.getItem(), patterned)),
                    "and dropped as they were: " + banners);
            helper.succeed();
        });
    }

    /**
     * Used on a Thin Rope let down from a ceiling, a banner hangs from its end by a cord, a little below it, facing the
     * player as a standing banner set down by them would. Let down further, it goes down with the rope's end. Over a
     * floor too near for its cloth it is refused, where a bottle would hang.
     */
    private static void fromARopeEnd(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = RopeTests.player(helper, 20);
        BlockPos rope = new BlockPos(3, 8, 3);
        helper.setBlock(rope.above(), Blocks.STONE);
        helper.setBlock(rope, ModBlocks.THIN_ROPE.get());
        Vec3 stand = helper.absoluteVec(new Vec3(3.5, 6, 1.2));
        player.snapTo(stand.x, stand.y, stand.z, 10.0F, 0.0F); // looking about south, a little round
        ItemStack patterned = patterned(helper);
        ItemStack hand = patterned.copy();
        BlockPos at = helper.absolutePos(rope);
        InteractionResult result = helper.getBlockState(rope).useItemOn(hand, level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.NORTH, at, false));
        List<HungBannerEntity> hung = level.getEntitiesOfClass(HungBannerEntity.class, helper.getBounds());
        check(helper, result.consumesAction() && hand.isEmpty() && hung.size() == 1, "a banner hangs from the rope's end: " + result);
        HungBannerEntity banner = hung.getFirst();
        check(helper, ItemStack.isSameItemSameComponents(banner.item(), patterned), "the same banner");
        check(helper, Math.abs(banner.getBoundingBox().maxY - (at.getY() - HungBannerEntity.BRIDLE)) < 1.0E-6
                && Math.abs(banner.getX() - (at.getX() + 0.5)) < 1.0E-6 && Math.abs(banner.getZ() - (at.getZ() + 0.5)) < 1.0E-6,
                "a cord's height under the rope's end: " + banner.getBoundingBox());
        check(helper, face(banner).dot(player.position().subtract(banner.position())) > 0
                && Math.abs(Mth.wrapDegrees(banner.yaw() - 180)) < 0.01F, "facing the player, turned square as a standing banner: " + banner.yaw());

        helper.setBlock(rope.below(), ModBlocks.THIN_ROPE.get()); // let down a block further
        helper.runAfterDelay(30, () -> {
            List<HungBannerEntity> moved = level.getEntitiesOfClass(HungBannerEntity.class, helper.getBounds());
            check(helper, banner.isRemoved() && moved.size() == 1
                    && Math.abs(moved.getFirst().getBoundingBox().maxY - (at.getY() - 1 - HungBannerEntity.BRIDLE)) < 1.0E-6
                    && ItemStack.isSameItemSameComponents(moved.getFirst().item(), patterned) && moved.getFirst().yaw() == banner.yaw(),
                    "let down further, it went down with the rope's end, as it was: " + moved);

            // a rope's end two blocks over the floor: room for a bottle, not for a banner's cloth
            BlockPos short_ = new BlockPos(5, 2, 1);
            helper.setBlock(short_.above(), Blocks.STONE);
            helper.setBlock(short_, ModBlocks.THIN_ROPE.get());
            BlockPos end = helper.absolutePos(short_);
            ItemStack another = patterned.copy();
            InteractionResult refused = helper.getBlockState(short_).useItemOn(another, level, player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(end), Direction.NORTH, end, false));
            check(helper, refused == Clicks.REFUSED && another.getCount() == 1
                    && level.getEntitiesOfClass(HungBannerEntity.class, new AABB(end).inflate(1)).isEmpty(),
                    "too near the floor, the banner is refused and stays in hand: " + refused);
            ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
            check(helper, helper.getBlockState(short_).useItemOn(bottle, level, player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(end), Direction.NORTH, end, false)).consumesAction() && bottle.isEmpty(),
                    "a bottle hangs there");
            helper.succeed();
        });
    }
}
