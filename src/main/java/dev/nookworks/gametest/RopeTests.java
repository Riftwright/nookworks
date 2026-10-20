package dev.nookworks.gametest;

import static dev.nookworks.gametest.BlockTests.underClaim;
import static dev.nookworks.gametest.NookworksGameTests.check;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import org.joml.Vector3f;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
import dev.nookworks.registry.ModRope;
import dev.nookworks.registry.ModWoods;
import dev.nookworks.rope.FilamentBulbBlock;
import dev.nookworks.rope.HookBlock;
import dev.nookworks.rope.HungBy;
import dev.nookworks.rope.HungItemEntity;
import dev.nookworks.rope.RopeHanging;
import dev.nookworks.rope.RopeKnotEntity;
import dev.nookworks.rope.RopeSpans;
import dev.nookworks.rope.SpanMaterial;
import dev.nookworks.wood.SupportBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.RodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Thin Rope strung between fence posts, things hung on it and from a rope's end, drying fish, the Filament Bulb, lanterns. */
final class RopeTests {
    static void register() {
        NookworksGameTests.test("thin_rope_strings_from_post_to_post", 200, RopeTests::strings);
        NookworksGameTests.test("a_cut_rope_takes_its_far_knot_with_it", RopeTests::cutTakesFarKnot);
        NookworksGameTests.test("a_rope_knot_is_not_cut_by_an_adventurer", RopeTests::knotSparedByAnAdventurer);
        NookworksGameTests.test("things_hang_on_a_rope_and_fish_dries", RopeTests::hangs);
        NookworksGameTests.test("filament_bulb_hangs_and_lights", RopeTests::bulb);
        NookworksGameTests.test("hung_things_are_tied_by_their_ends", RopeTests::tiedByTheirEnds);
        NookworksGameTests.test("rope_is_taken_from_the_hand_first", RopeTests::takenFromTheHandFirst);
        NookworksGameTests.test("hooks_take_rope_and_berries", RopeTests::hooks);
        NookworksGameTests.test("rope_is_knotted_round_a_hooks_upright", RopeTests::ropeIsKnottedRoundAHooksUpright);
        NookworksGameTests.test("thin_rope_ties_round_a_brackets_brace", 60, RopeTests::tiesRoundABrace);
        NookworksGameTests.test("rope_is_knotted_round_the_middle_of_the_brace", RopeTests::knottedRoundTheBrace);
        NookworksGameTests.test("thin_rope_ties_to_bars_and_rods_not_walls", RopeTests::tiesToBarsAndRods);
        NookworksGameTests.test("lightning_knocks_rope_loose_and_spares_it", 100, RopeTests::lightningKnocksRopeLoose);
        NookworksGameTests.test("glow_berries_light_where_they_hang", RopeTests::glowBerriesLight);
        NookworksGameTests.test("glow_berries_light_at_once_a_bulb_after_a_moment", RopeTests::glowBerriesLightAtOnce);
        NookworksGameTests.test("heads_and_the_dragon_egg_hang_solid", RopeTests::headsHangFacingTheHanger);
        NookworksGameTests.test("hung_things_hold_against_adventurers_mobs_and_pistons", RopeTests::holdAgainstAdventurersMobsAndPistons);
        NookworksGameTests.test("bulb_puts_out_only_its_own_light", 60, RopeTests::bulbPutsOutOnlyItsOwnLight);
        NookworksGameTests.test("hanging_on_a_rope_asks_the_land", RopeTests::hangingAsksTheLand);
        NookworksGameTests.test("hung_things_stay_up_for_an_adventurer", RopeTests::hungThingsStayUpForAnAdventurer);
        NookworksGameTests.test("hung_light_never_outlives_its_bulb", 80, RopeTests::hungLightNeverOutlivesItsBulb);
        NookworksGameTests.test("lanterns_hang_as_blocks_on_rope_and_chain", 80, RopeTests::lanternsHang);
        NookworksGameTests.test("a_hung_lantern_comes_down_with_its_rope", 120, RopeTests::lanternFallsWithItsRope);
        NookworksGameTests.test("a_lantern_hangs_from_a_thin_ropes_end", 60, RopeTests::lanternOnARopesEnd);
        NookworksGameTests.test("hung_lanterns_go_out_and_light_where_they_hang", 60, RopeTests::hungLanternsGoOutAndLight);
        NookworksGameTests.test("glow_berries_hang_as_the_berried_vine", 40, RopeTests::glowBerriesHangAsTheVine);
    }

    /**
     * A span costs more rope than the stack in hand: the hand's two go first, the rest from the inventory, and the
     * stack in hand ends at nothing - not at less than nothing, as it did.
     */
    private static void takenFromTheHandFirst(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, 2);
        player.getInventory().setItem(9, new ItemStack(ModItems.THIN_ROPE.get(), 10));
        BlockPos[] posts = posts(helper, 1, 6);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        ItemStack hand = player.getMainHandItem();
        RopeSpans.tie(level, player, a, hand);
        check(helper, RopeSpans.tie(level, player, b, hand) == InteractionResult.SUCCESS, "strung, on the rope in hand and in the pack");
        check(helper, hand.getCount() == 0, "the hand's two spent, and no more: " + hand.getCount());
        check(helper, player.getInventory().countItem(ModItems.THIN_ROPE.get()) == 6, "four more from the pack, six left");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * A bulb hung where a map maker set a light block leaves it be when it goes; of two bulbs in one block, the one that
     * lit it hands its light to the other, which puts it out when it goes in turn.
     */
    private static void bulbPutsOutOnlyItsOwnLight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, 20);
        BlockPos[] posts = posts(helper, 2, 5);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, b, player.getMainHandItem());
        level.getServer().getPlayerList().remove(player);
        ItemStack bulb = new ItemStack(ModRope.FILAMENT_BULB_ITEM.get());
        HungItemEntity guest = HungItemEntity.onSpan(level, a, b, 0.3, bulb);
        BlockPos theirs = guest.blockPosition();
        level.setBlock(theirs, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3);
        level.addFreshEntity(guest);
        HungItemEntity first = HungItemEntity.onSpan(level, a, b, 0.7, bulb);
        HungItemEntity second = HungItemEntity.onSpan(level, a, b, 0.7, bulb);
        level.addFreshEntity(first);
        level.addFreshEntity(second);
        helper.runAfterDelay(25, () -> {
            check(helper, guest.litAt() == null, "a light already there is not the bulb's");
            guest.discard();
            check(helper, level.getBlockState(theirs).is(Blocks.LIGHT), "and it stays when the bulb goes");
            HungItemEntity lit = first.litAt() != null ? first : second;
            HungItemEntity other = lit == first ? second : first;
            BlockPos at = lit.litAt();
            check(helper, at != null && other.litAt() == null && level.getBlockState(at).is(ModRope.HUNG_LIGHT.get()), "one of the two lit their block");
            lit.discard();
            check(helper, level.getBlockState(at).is(ModRope.HUNG_LIGHT.get()) && at.equals(other.litAt()), "going, it leaves its light to the other");
            other.discard();
            check(helper, level.getBlockState(at).isAir(), "which puts it out when it goes");
            helper.succeed();
        });
    }

    /** Hanging a thing on a rope over someone else's claim is refused, as putting it down there would be; over yours it hangs. */
    private static void hangingAsksTheLand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, 20);
        BlockPos[] posts = posts(helper, 2, 4);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, b, player.getMainHandItem());
        Vec3 target = RopeSpans.at(a, b, 0.5);
        player.snapTo(target.x, target.y - 1.62, target.z - 2.0, 0.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
        BlockTests.underClaim(UUID.randomUUID(), () -> check(helper,
                RopeHanging.onSpan(level, player, player.getMainHandItem()) == InteractionResult.FAIL && player.getMainHandItem().getCount() == 1,
                "someone else's claim: refused, the bottle still in hand"));
        BlockTests.underClaim(player.getUUID(), () -> check(helper,
                RopeHanging.onSpan(level, player, player.getMainHandItem()) == InteractionResult.SUCCESS && player.getMainHandItem().isEmpty(),
                "the player's own: it hangs"));
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /** Taking a thing down is breaking it: a player in adventure mode, who cannot break things, cannot take it either. */
    private static void hungThingsStayUpForAnAdventurer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, 20);
        BlockPos[] posts = posts(helper, 2, 4);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, b, player.getMainHandItem());
        HungItemEntity fish = HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(Items.COD));
        level.addFreshEntity(fish);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setGameMode(GameType.ADVENTURE);
        check(helper, fish.interact(player, InteractionHand.MAIN_HAND, fish.position()) == InteractionResult.FAIL && !fish.isRemoved(),
                "an adventurer's hand leaves it hanging");
        player.setGameMode(GameType.SURVIVAL);
        check(helper, fish.interact(player, InteractionHand.MAIN_HAND, fish.position()) == InteractionResult.SUCCESS && fish.isRemoved()
                && player.getInventory().countItem(Items.COD) == 1, "a survivor's takes it down");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * A bottle is tied by its neck, a fish by its tail, wheat, corn and kelp by their stalks, a flower by its stem. On
     * an outline the cord ties a pixel in from that end, and the thing turns so that its middle hangs straight below.
     */
    private static void tiedByTheirEnds(GameTestHelper helper) {
        check(helper, HungBy.of(new ItemStack(Items.POTION)) == HungBy.TOP && HungBy.of(new ItemStack(ModKitchen.ONION.get())) == HungBy.TOP,
                "bottles and onions hang by the top");
        check(helper, HungBy.of(new ItemStack(Items.COD)) == HungBy.TAIL && HungBy.of(new ItemStack(Items.COOKED_SALMON)) == HungBy.TAIL
                && HungBy.of(new ItemStack(ModRope.DRIED_FISH.get())) == HungBy.TAIL, "fish hang by the tail");
        check(helper, HungBy.of(new ItemStack(Items.PUFFERFISH)) == HungBy.TOP, "a pufferfish, round and face on, hangs by the top");
        check(helper, HungBy.of(new ItemStack(Items.WHEAT)) == HungBy.STALKS && HungBy.of(new ItemStack(ModKitchen.CORN.get())) == HungBy.STALKS
                && HungBy.of(new ItemStack(Items.KELP)) == HungBy.STALKS, "wheat, corn and kelp hang by their stalks");
        check(helper, HungBy.of(new ItemStack(Items.POPPY)) == HungBy.STEM, "a flower hangs by its stem");

        // A bottle: a body 8 pixels across and a neck 3 across on top of it.
        List<Vector3f> bottle = outline(new float[][] {{-4, -7}, {4, -7}, {4, 1}, {1.5F, 1}, {1.5F, 6}, {-1.5F, 6}, {-1.5F, 1}, {-4, 1}});
        float[] neck = HungBy.TOP.tieOn(bottle);
        check(helper, Math.abs(neck[0]) < 0.01F && Math.abs(neck[1] - 5 / 16F) < 0.01F && Math.abs(neck[2]) < 0.05F,
                "a bottle is tied a pixel down its neck and hangs upright");
        hangsBelow(helper, bottle, neck, "the bottle");

        // A fish corner to corner, its tail at the picture's top right, which is the drawn frame's top left.
        List<Vector3f> fish = outline(new float[][] {{5, -7}, {7, -5}, {-6, 7}, {-7, 6}});
        float[] tail = HungBy.TAIL.tieOn(fish);
        check(helper, tail[0] < -4 / 16F && tail[1] > 4 / 16F && Math.abs(Math.abs(tail[2]) - Math.PI / 4) < 0.15,
                "a fish is tied at its tail and turns an eighth round to hang head down");
        hangsBelow(helper, fish, tail, "the fish");

        // A flower on its stem: tied at the stem's foot, it turns right over.
        List<Vector3f> flower = outline(new float[][] {{-0.5F, -8}, {0.5F, -8}, {0.5F, 0}, {3, 0}, {3, 5}, {-3, 5}, {-3, 0}, {-0.5F, 0}});
        float[] stem = HungBy.STEM.tieOn(flower);
        check(helper, stem[1] < -6 / 16F && Math.abs(Math.abs(stem[2]) - Math.PI) < 0.1, "a flower is tied at its stem's foot and hangs upside down");
        hangsBelow(helper, flower, stem, "the flower");

        float[] none = HungBy.TOP.tieOn(List.of());
        check(helper, none[0] == 0 && none[1] == 0.5F && none[2] == 0, "with no outline to go by it is tied at the top middle");
        helper.succeed();
    }

    /** An outline from points in pixels, a block across. */
    private static List<Vector3f> outline(float[][] pixels) {
        List<Vector3f> points = new ArrayList<>();
        for (float[] p : pixels) {
            points.add(new Vector3f(p[0] / 16, p[1] / 16, 0));
        }
        return points;
    }

    /** Turned as it is drawn, the middle of the outline is straight below the tie. */
    private static void hangsBelow(GameTestHelper helper, List<Vector3f> outline, float[] tie, String what) {
        float middleX = 0, middleY = 0;
        for (Vector3f v : outline) {
            middleX += v.x / outline.size();
            middleY += v.y / outline.size();
        }
        float dx = middleX - tie[0], dy = middleY - tie[1];
        float cos = Mth.cos(tie[2]), sin = Mth.sin(tie[2]);
        check(helper, Math.abs(dx * cos - dy * sin) < 0.01F && dx * sin + dy * cos < 0, what + "'s middle hangs straight below its tie");
    }

    static ServerPlayer player(GameTestHelper helper, int ropes) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(3.5, 1, 4.5)); // in the arena, by the posts, as a player tying them is
        player.snapTo(at.x, at.y, at.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.THIN_ROPE.get(), ropes));
        return player;
    }

    /**
     * A Hook goes on a wall or under a ceiling, and Thin Rope ties to it as to a fence post - clicked from any side, from
     * below too - in the hook's bend: a line runs from a ceiling hook to a wall hook, slack between. Sweet berries hang on
     * a hook; the sprig pricks a mob that moves through it, one climbing up past it too, and an empty hand takes it back.
     * Glow berries hang there as a lamp, and prick nothing. Broken, a hook drops what hung on it; its wall gone, it comes
     * away.
     */
    private static void hooks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, 16);
        var hook = ModRope.HOOK.get();
        BlockPos wall = new BlockPos(0, 2, 3);
        BlockPos onWall = new BlockPos(1, 2, 3);
        BlockPos ceiling = new BlockPos(6, 4, 3);
        BlockPos underCeiling = new BlockPos(6, 3, 3);
        helper.setBlock(wall, Blocks.STONE);
        helper.setBlock(ceiling, Blocks.STONE);
        var wallHook = hook.defaultBlockState().setValue(HookBlock.FACE, AttachFace.WALL)
                .setValue(HookBlock.FACING, Direction.EAST);
        var ceilingHook = hook.defaultBlockState().setValue(HookBlock.FACE, AttachFace.CEILING)
                .setValue(HookBlock.FACING, Direction.NORTH);
        check(helper, wallHook.canSurvive(level, helper.absolutePos(onWall)) && ceilingHook.canSurvive(level, helper.absolutePos(underCeiling)),
                "a hook holds on a wall and under a ceiling");
        check(helper, !wallHook.canSurvive(level, helper.absolutePos(onWall.above(3))), "and not on nothing");
        helper.setBlock(onWall, wallHook);
        helper.setBlock(underCeiling, ceilingHook);
        BlockPos a = helper.absolutePos(onWall);
        BlockPos b = helper.absolutePos(underCeiling);
        check(helper, RopeSpans.isPost(level.getBlockState(a)) && helper.getBlockState(onWall).getCollisionShape(level, a).isEmpty(),
                "rope ties to it, and there is nothing to walk into");

        ItemStack rope = player.getMainHandItem();
        var fromBelow = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(b), Direction.DOWN, b, false));
        check(helper, ModItems.THIN_ROPE.get().onItemUseFirst(rope, fromBelow) == InteractionResult.SUCCESS
                && RopeKnotEntity.heldBy(level, player) != null, "clicked from below, a ceiling hook is tied to, not hung from");
        check(helper, RopeSpans.tie(level, player, a, rope) == InteractionResult.SUCCESS, "and tied off on the wall hook");
        RopeKnotEntity first = RopeKnotEntity.at(level, b);
        RopeKnotEntity second = RopeKnotEntity.at(level, a);
        Vec3 bend = HookBlock.eye(level.getBlockState(a), a);
        check(helper, first != null && first.ties(a) && second != null && second.position().distanceTo(bend) < 1.0E-6,
                "strung, tied in the hook's bend: " + (second == null ? null : second.position()) + " / " + bend);
        check(helper, bend.x > a.getX() && bend.x < a.getX() + 0.5 && RopeSpans.tie(level, a).distanceTo(bend) < 1.0E-9,
                "a wall hook's bend stands out from its wall, a little: " + bend);
        Vec3 top = RopeSpans.tie(level, b);
        check(helper, RopeSpans.at(level, b, a, 0.5).y < (top.y + bend.y) / 2, "and the line hangs slack between them");

        ItemStack berries = new ItemStack(Items.SWEET_BERRIES, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, berries);
        BlockHitResult atHook = new BlockHitResult(Vec3.atCenterOf(b), Direction.DOWN, b, false);
        check(helper, level.getBlockState(b).useItemOn(berries, level, player, InteractionHand.MAIN_HAND, atHook).consumesAction()
                && level.getBlockState(b).getValue(HookBlock.SPRIG) == HookBlock.Sprig.SWEET_BERRIES && berries.getCount() == 1
                && level.getBlockState(b).getLightEmission() == 0, "sweet berries hang on it");
        var spider = helper.spawn(EntityTypes.SPIDER, underCeiling);
        spider.setNoAi(true);
        float health = spider.getHealth();
        spider.setOldPosAndRot(spider.position().subtract(0, 0.2, 0), spider.getYRot(), spider.getXRot()); // it moved up: climbing
        level.getBlockState(b).entityInside(level, b, spider, InsideBlockEffectApplier.NOOP, true);
        check(helper, spider.getHealth() < health, "a mob moving through the sprig is pricked, climbing too");
        spider.discard();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        level.getBlockState(b).useWithoutItem(level, player, atHook);
        check(helper, level.getBlockState(b).getValue(HookBlock.SPRIG) == HookBlock.Sprig.NONE, "an empty hand takes them back off");
        var drops = Block.getDrops(ceilingHook.setValue(HookBlock.SPRIG, HookBlock.Sprig.SWEET_BERRIES), level, b, null);
        check(helper, drops.stream().anyMatch(s -> s.is(ModRope.HOOK_ITEM.get())) && drops.stream().anyMatch(s -> s.is(Items.SWEET_BERRIES)),
                "broken with berries on it, it drops the hook and the berries");

        ItemStack glow = new ItemStack(Items.GLOW_BERRIES);
        player.setItemInHand(InteractionHand.MAIN_HAND, glow);
        check(helper, level.getBlockState(b).useItemOn(glow, level, player, InteractionHand.MAIN_HAND, atHook).consumesAction()
                && level.getBlockState(b).getValue(HookBlock.SPRIG) == HookBlock.Sprig.GLOW_BERRIES && glow.isEmpty()
                && level.getBlockState(b).getLightEmission() == HookBlock.GLOW_LIGHT, "glow berries hang on it, a lamp");
        var zombie = helper.spawn(EntityTypes.ZOMBIE, underCeiling);
        zombie.setNoAi(true);
        float whole = zombie.getHealth();
        zombie.setOldPosAndRot(zombie.position().subtract(0.3, 0, 0), zombie.getYRot(), zombie.getXRot());
        level.getBlockState(b).entityInside(level, b, zombie, InsideBlockEffectApplier.NOOP, true);
        check(helper, zombie.getHealth() == whole && level.getBlockState(b).getEntityInsideCollisionShape(level, b, zombie).isEmpty(),
                "which prick nothing");
        zombie.discard();
        drops = Block.getDrops(level.getBlockState(b), level, b, null);
        check(helper, drops.stream().anyMatch(s -> s.is(Items.GLOW_BERRIES)) && drops.stream().noneMatch(s -> s.is(Items.SWEET_BERRIES)),
                "and broken, it gives the glow berries back");

        helper.setBlock(wall, Blocks.AIR);
        check(helper, helper.getBlockState(onWall).isAir(), "its wall gone, the hook comes away");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Glow berries hung on a rope light the air where they hang, as a bulb does; with a bulb in the same block, the one
     * that lit it hands its light to the other at the other's brightness. A block set there puts it out, and once it is
     * taken away the light comes back; the last to go takes it away.
     */
    private static void glowBerriesLight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, 20);
        BlockPos[] posts = posts(helper, 2, 5);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, b, player.getMainHandItem());
        level.getServer().getPlayerList().remove(player);
        HungItemEntity berries = bearing(HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(Items.GLOW_BERRIES)));
        HungItemEntity bulb = HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(ModRope.FILAMENT_BULB_ITEM.get()));
        check(helper, berries.glow() == HungItemEntity.GLOW_BERRY_LIGHT && bulb.glow() == HungItemEntity.BULB_LIGHT
                && HungItemEntity.onSpan(level, a, b, 0.3, new ItemStack(Items.SWEET_BERRIES)).glow() == 0, "glow berries and a bulb light; sweet berries do not");
        level.addFreshEntity(berries);
        level.addFreshEntity(bulb);
        helper.runAfterDelay(25, () -> {
            HungItemEntity lit = berries.litAt() != null ? berries : bulb;
            HungItemEntity other = lit == berries ? bulb : berries;
            BlockPos at = lit.litAt();
            check(helper, at != null && other.litAt() == null && level.getBlockState(at).is(ModRope.HUNG_LIGHT.get())
                    && level.getBlockState(at).getValue(LightBlock.LEVEL) == lit.glow(),
                    "one of the two lit the air where they hang, as bright as it is");
            lit.discard();
            check(helper, at.equals(other.litAt()) && level.getBlockState(at).is(ModRope.HUNG_LIGHT.get())
                    && level.getBlockState(at).getValue(LightBlock.LEVEL) == other.glow(),
                    "going, it leaves the light to the other, at the other's brightness");
            level.setBlock(at, Blocks.STONE.defaultBlockState(), 3);
            helper.runAfterDelay(21, () -> {
                check(helper, other.litAt() == null && level.getBlockState(at).is(Blocks.STONE), "a block set in its light puts it out");
                level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
                helper.runAfterDelay(21, () -> {
                    check(helper, at.equals(other.litAt()) && level.getBlockState(at).is(ModRope.HUNG_LIGHT.get()), "taken away again, it lights once more");
                    other.discard();
                    check(helper, level.getBlockState(at).isAir(), "which takes its light away when it goes");
                    helper.succeed();
                });
            });
        });
    }

    /**
     * A mob's head hangs on a rope - by a player's click, as anything hangs - as solid as it stands on a block, half a
     * block across, its face turned to whoever hung it from either side, and the dragon egg the same way; each keeps its
     * room along the rope, and a sprig of berries is drawn bigger than a bottle (the user, 2026-10-11).
     */
    private static void headsHangFacingTheHanger(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, 20);
        BlockPos[] posts = posts(helper, 2, 5);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, b, player.getMainHandItem());
        level.getServer().getPlayerList().remove(player);
        ItemStack zombie = new ItemStack(Items.ZOMBIE_HEAD);
        check(helper, HungItemEntity.hangs(zombie) && HungItemEntity.hangs(new ItemStack(Items.PLAYER_HEAD))
                && HungItemEntity.hangs(new ItemStack(Items.DRAGON_EGG)) && !HungItemEntity.hangs(new ItemStack(Items.DRAGON_HEAD)),
                "mobs' heads and the dragon egg hang, the dragon's head (a block across) not");
        Vec3 point = RopeSpans.at(level, a, b, 0.5);
        for (int side : new int[] {-1, 1}) { // the rope runs east: hung from the north, then from the south
            Vec3 seen = point.add(0, 0, 3 * side);
            HungItemEntity head = HungItemEntity.onSpan(level, a, b, 0.5, zombie, seen);
            double yaw = Math.toRadians(head.yaw());
            double faces = (seen.x - point.x) * Math.sin(yaw) - (seen.z - point.z) * Math.cos(yaw);
            check(helper, faces > 0, "hung from the " + (side < 0 ? "north" : "south") + ", it faces that way");
            AABB box = head.getBoundingBox();
            check(helper, Math.abs(box.getXsize() - HungItemEntity.SOLID_SIZE) < 1e-6
                    && Math.abs(box.getYsize() - HungItemEntity.SOLID_SIZE) < 1e-6
                    && Math.abs(box.getZsize() - HungItemEntity.SOLID_SIZE) < 1e-6, "it takes up a head's cube under its cord");
        }
        check(helper, head(level, a, b).room() == HungItemEntity.SOLID_ROOM && HungItemEntity.room(zombie) > HungItemEntity.ROOM,
                "a head keeps more room along the rope than a bottle");
        ItemStack piglin = new ItemStack(Items.PIGLIN_HEAD);
        HungItemEntity piglinHead = HungItemEntity.onSpan(level, a, b, 0.5, piglin, point.add(0, 0, -3));
        check(helper, HungItemEntity.room(piglin) > HungItemEntity.room(zombie)
                && Math.abs(piglinHead.getBoundingBox().getXsize() - 2 * HungItemEntity.PIGLIN_HALF) < 1e-6,
                "a piglin's head reaches further along the rope, its ears out, and keeps more room");
        // its facing is saved: hung from the south, it loads facing south
        HungItemEntity south = HungItemEntity.onSpan(level, a, b, 0.5, zombie, point.add(0, 0, 3));
        net.minecraft.world.level.storage.TagValueOutput out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
        south.saveWithoutId(out);
        HungItemEntity loaded = new HungItemEntity(dev.nookworks.registry.ModRope.HUNG_ITEM.get(), level);
        loaded.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,
                level.registryAccess(), out.buildResult()));
        level.addFreshEntity(loaded); // its way round worked out again from the rope as it runs, at its first look round
        HungItemEntity egg = HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(Items.DRAGON_EGG), point.add(0, 0, -3));
        check(helper, egg.isSolid() && Math.abs(egg.getBoundingBox().getYsize() - HungItemEntity.SOLID_SIZE) < 1e-6,
                "the dragon egg hangs as a head does, half a block tall");
        HungItemEntity sprig = HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(Items.SWEET_BERRIES));
        HungItemEntity bottle = HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(Items.GLASS_BOTTLE));
        check(helper, sprig.getBoundingBox().getYsize() > bottle.getBoundingBox().getYsize() + 0.2,
                "a sprig of berries is drawn bigger than a bottle");
        helper.runAfterDelay(25, () -> {
            check(helper, !loaded.isRemoved() && Math.abs(Mth.wrapDegrees(loaded.yaw() - south.yaw())) < 1e-3,
                    "a head saved facing the far side still faces it once it has looked round again");
            loaded.discard();
            helper.succeed();
        });
    }

    private static HungItemEntity head(ServerLevel level, BlockPos a, BlockPos b) {
        return HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(Items.SKELETON_SKULL));
    }

    /**
     * Glow berries are alight the tick after they are hung; a Filament Bulb comes on a second later, as a filament
     * warms (the user, 2026-10-11: the berries' wait looked wrong, the bulb's suits it).
     */
    private static void glowBerriesLightAtOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, 20);
        BlockPos[] posts = posts(helper, 2, 5);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, b, player.getMainHandItem());
        level.getServer().getPlayerList().remove(player);
        HungItemEntity berries = bearing(HungItemEntity.onSpan(level, a, b, 0.3, new ItemStack(Items.GLOW_BERRIES)));
        HungItemEntity bulb = HungItemEntity.onSpan(level, a, b, 0.7, new ItemStack(ModRope.FILAMENT_BULB_ITEM.get()));
        check(helper, !berries.blockPosition().equals(bulb.blockPosition()), "the two hang in blocks of their own");
        level.addFreshEntity(berries);
        level.addFreshEntity(bulb);
        helper.runAfterDelay(3, () -> {
            check(helper, berries.litAt() != null && level.getBlockState(berries.litAt()).is(ModRope.HUNG_LIGHT.get()),
                    "the glow berries are alight at once");
            check(helper, bulb.litAt() == null, "the bulb not yet");
            helper.runAfterDelay(20, () -> {
                check(helper, bulb.litAt() != null && level.getBlockState(bulb.litAt()).is(ModRope.HUNG_LIGHT.get()),
                        "and comes on a second later");
                berries.discard();
                bulb.discard();
                helper.succeed();
            });
        });
    }

    /**
     * Something hung up is spared what spares an item frame: an adventurer's hit (as their empty hand is refused), and
     * a mob's doing with mob griefing off; moved by a piston, it falls rather than hanging on in the air.
     */
    private static void holdAgainstAdventurersMobsAndPistons(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, 20);
        BlockPos[] posts = posts(helper, 2, 5);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, b, player.getMainHandItem());
        HungItemEntity fish = HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(Items.COD));
        level.addFreshEntity(fish);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setGameMode(GameType.ADVENTURE);
        player.attack(fish);
        check(helper, !fish.isRemoved(), "an adventurer's hit leaves it hanging, as their empty hand does");
        player.setGameMode(GameType.SURVIVAL);

        var rules = level.getGameRules();
        var griefing = GameRules.MOB_GRIEFING;
        boolean was = rules.get(griefing);
        rules.set(griefing, false, level.getServer());
        var zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 5));
        try {
            fish.hurtServer(level, level.damageSources().mobAttack(zombie), 1.0F);
            check(helper, !fish.isRemoved(), "a mob's hit, with mob griefing off, leaves it hanging");
        } finally {
            rules.set(griefing, was, level.getServer());
            zombie.discard();
        }

        fish.move(MoverType.PISTON, new Vec3(0.5, 0, 0));
        check(helper, fish.isRemoved(), "moved by a piston, it falls");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * A hung bulb's light is our own light block (the user, 2026-10-01), never vanilla's that outlived it: one left
     * where nothing lit hangs goes of itself when it looks, and a vanilla light block set by a bulb before is swapped
     * for ours at the bulb's next look.
     */
    private static void hungLightNeverOutlivesItsBulb(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos orphan = helper.absolutePos(new BlockPos(1, 4, 1));
        level.setBlock(orphan, ModRope.HUNG_LIGHT.get().at(13), 3);
        check(helper, level.getBlockState(orphan).getLightEmission() == 13, "it gives the light it was set at");
        level.getBlockState(orphan).tick(level, orphan, level.getRandom());
        check(helper, level.getBlockState(orphan).isAir(), "with nothing lit hanging in it, it goes");

        ServerPlayer player = player(helper, 20);
        BlockPos[] posts = posts(helper, 3, 5);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, b, player.getMainHandItem());
        level.getServer().getPlayerList().remove(player);
        HungItemEntity bulb = HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(ModRope.FILAMENT_BULB_ITEM.get()));
        level.addFreshEntity(bulb);
        helper.runAfterDelay(25, () -> {
            BlockPos at = bulb.litAt();
            check(helper, at != null && level.getBlockState(at).is(ModRope.HUNG_LIGHT.get()), "a bulb lights its block with ours");
            level.getBlockState(at).tick(level, at, level.getRandom());
            check(helper, level.getBlockState(at).is(ModRope.HUNG_LIGHT.get()), "which stays while the bulb hangs there");
            level.setBlock(at, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, HungItemEntity.BULB_LIGHT), 3); // set before
            helper.runAfterDelay(21, () -> {
                check(helper, level.getBlockState(at).is(ModRope.HUNG_LIGHT.get()) && at.equals(bulb.litAt()),
                        "vanilla's light from before is swapped for ours");
                bulb.discard();
                check(helper, level.getBlockState(at).isAir(), "and it goes with the bulb");
                helper.succeed();
            });
        });
    }

    /**
     * A rope on a hook is knotted round the foot of its upright - the point on a wall, the shank under a ceiling - and the
     * knot lies on the iron across under it (the user, 2026-10-02: tied round the end of the shank, it looked threaded
     * through the hook). Against the boxes of the models as drawn, turned each way: the upright runs up through the
     * knot, the knot is sunk into the iron under it by a sliver and touches nothing else, and the cord, once out of the
     * knot, touches no iron along the first quarter block of a span - out from a wall or along it, back from a
     * ceiling hook's point or across it, level or going down to a lower post.
     */
    /**
     * A wall bracket's brace takes Thin Rope as a fence post does - from any side, from below too, but for its beam's
     * underside, which a rope still hangs from. A support standing as a post or lying as a beam takes none; a bracket
     * made something else with an axe lets its rope go, as a post broken does.
     */
    private static void tiesRoundABrace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, 16);
        var support = ModWoods.SUPPORTS.get("oak").get();
        BlockState bracket = support.defaultBlockState().setValue(SupportBlock.FACING, Direction.EAST)
                .setValue(SupportBlock.SHAPE, SupportBlock.Shape.BRACKET);
        check(helper, RopeSpans.isPost(bracket), "a bracket takes rope");
        check(helper, !RopeSpans.isPost(bracket.setValue(SupportBlock.SHAPE, SupportBlock.Shape.POST))
                && !RopeSpans.isPost(bracket.setValue(SupportBlock.SHAPE, SupportBlock.Shape.BEAM))
                && !RopeSpans.isPost(bracket.setValue(SupportBlock.SHAPE, SupportBlock.Shape.WALL_POST)), "a support's post, beam or wall post does not");
        BlockPos at = new BlockPos(1, 3, 2);
        BlockPos fenceAt = new BlockPos(6, 2, 2);
        helper.setBlock(at.west(), Blocks.STONE);
        helper.setBlock(at, bracket);
        helper.setBlock(fenceAt, Blocks.OAK_FENCE);
        BlockPos a = helper.absolutePos(at);
        BlockPos b = helper.absolutePos(fenceAt);
        ItemStack rope = player.getMainHandItem();

        var underBeam = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atLowerCornerOf(a).add(0.85, 11.0 / 16, 0.5), Direction.DOWN, a, false));
        check(helper, ModItems.THIN_ROPE.get().onItemUseFirst(rope, underBeam) == InteractionResult.PASS
                && RopeKnotEntity.heldBy(level, player) == null, "clicked under its beam, a rope hangs from it, as before");
        var underBrace = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atLowerCornerOf(a).add(0.5, 4.0 / 16, 0.5), Direction.DOWN, a, false));
        check(helper, ModItems.THIN_ROPE.get().onItemUseFirst(rope, underBrace) == InteractionResult.SUCCESS
                && RopeKnotEntity.heldBy(level, player) != null, "clicked from below on the brace, it is tied to");
        check(helper, RopeSpans.tie(level, player, b, rope) == InteractionResult.SUCCESS, "and tied off on a fence post");
        RopeKnotEntity knot = RopeKnotEntity.at(level, a);
        Vec3 brace = SupportBlock.brace(level.getBlockState(a), a);
        check(helper, knot != null && knot.ties(b) && knot.position().distanceTo(brace) < 1.0E-6
                && RopeSpans.tie(level, a).distanceTo(brace) < 1.0E-9,
                "strung from the brace's middle: " + (knot == null ? null : knot.position()));
        int spent = RopeSpans.ropesFor(a, b);
        check(helper, rope.getCount() == 16 - spent, "a Thin Rope a block, as between posts: " + rope.getCount());

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_AXE));
        level.getBlockState(a).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(a), Direction.UP, a, false));
        check(helper, level.getBlockState(a).getValue(SupportBlock.SHAPE) == SupportBlock.Shape.WALL_POST,
                "an axe takes its beam and brace off: a post against the wall");
        helper.runAfterDelay(25, () -> {
            RopeKnotEntity far = RopeKnotEntity.at(level, b);
            check(helper, knot.isRemoved() && (far == null || !far.ties(a)), "its brace gone, the rope comes away");
            int dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(a).inflate(8)).stream()
                    .filter(e -> e.getItem().is(ModItems.THIN_ROPE.get())).mapToInt(e -> e.getItem().getCount()).sum();
            check(helper, dropped == spent, "and drops: " + dropped);
            level.getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    /**
     * Where a rope is tied on a bracket is the middle of its brace as the model draws it, every way round: the point the
     * brace's timber is turned about, which is its middle (tools/gen_wood_data.py), and the knot drawn for its size.
     */
    private static void knottedRoundTheBrace(GameTestHelper helper) {
        JsonObject element = null;
        try (var in = RopeTests.class.getResourceAsStream("/assets/nookworks/models/block/oak_support_bracket.json")) {
            for (var e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonArray("elements")) {
                if (e.getAsJsonObject().has("rotation")) {
                    element = e.getAsJsonObject();
                }
            }
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException("cannot read the bracket's model", e);
        }
        check(helper, element != null, "the bracket has a brace, turned");
        var rotation = element.getAsJsonObject("rotation");
        var origin = rotation.getAsJsonArray("origin");
        var from = element.getAsJsonArray("from");
        var to = element.getAsJsonArray("to");
        check(helper, rotation.get("axis").getAsString().equals("x") && rotation.get("angle").getAsFloat() == SupportBlock.BRACE_ANGLE,
                "turned about x, as steep as the knot is drawn");
        check(helper, to.get(0).getAsDouble() - from.get(0).getAsDouble() == SupportBlock.BRACE_WIDTH
                && to.get(1).getAsDouble() - from.get(1).getAsDouble() == SupportBlock.BRACE_THICKNESS,
                "as wide and as thick as the knot is made for");
        for (int i = 0; i < 3; i++) {
            check(helper, (from.get(i).getAsDouble() + to.get(i).getAsDouble()) / 2 == origin.get(i).getAsDouble(),
                    "turned about its own middle, along " + "xyz".charAt(i));
        }
        var support = ModWoods.SUPPORTS.get("oak").get();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState state = support.defaultBlockState().setValue(SupportBlock.FACING, facing)
                    .setValue(SupportBlock.SHAPE, SupportBlock.Shape.BRACKET);
            Vec3 tie = SupportBlock.brace(state, BlockPos.ZERO).scale(16);
            // back to the model's own way round (it faces north), as the blockstate turns it
            double x = switch (facing) {
                case SOUTH -> 16 - tie.x;
                case EAST -> tie.z;
                case WEST -> 16 - tie.z;
                default -> tie.x;
            };
            double z = switch (facing) {
                case SOUTH -> 16 - tie.z;
                case EAST -> 16 - tie.x;
                case WEST -> tie.x;
                default -> tie.z;
            };
            check(helper, Math.abs(x - origin.get(0).getAsDouble()) < 1.0E-9 && Math.abs(tie.y - origin.get(1).getAsDouble()) < 1.0E-9
                    && Math.abs(z - origin.get(2).getAsDouble()) < 1.0E-9, facing + ": tied at the brace's middle, got " + tie);
        }
        helper.succeed();
    }

    /**
     * Bars and rods take Thin Rope too: iron and copper bars as a fence post does, round the middle bar (and their
     * underside still lets a rope down); a lightning rod just under its cap, an end rod standing up near its tip and one
     * out of a wall or down from a ceiling at its foot, along the way it points, from any side - a rod under a ceiling
     * from below, on its tip. A wall does not: too thick a post for a cord.
     */
    private static void tiesToBarsAndRods(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, 32);
        var every = new ArrayList<Block>(List.of(Blocks.IRON_BARS, Blocks.END_ROD));
        every.addAll(Blocks.COPPER_BARS.asList());
        every.addAll(Blocks.LIGHTNING_ROD.asList());
        for (Block block : every) {
            check(helper, RopeSpans.isPost(block.defaultBlockState()), block + " takes rope");
        }
        check(helper, !RopeSpans.isPost(Blocks.COBBLESTONE_WALL.defaultBlockState()), "a wall does not");
        check(helper, (RopeSpans.LIGHTNING_ROD_TIE + 0.75) <= 12 && RopeSpans.END_ROD_TIE + 0.75 <= 16
                && RopeSpans.END_ROD_FOOT_TIE - 0.75 >= 1,
                "a knot sits under a lightning rod's cap (from 12 pixels up), on an end rod short of its tip, or at its foot clear of its base");

        // a lightning rod on a post, pointing up; an end rod out of a wall, pointing west (towards the bars it is tied
        // to: pointing away from them, the rope would run back through its wall); one under a ceiling
        BlockPos rodAt = new BlockPos(1, 3, 2);
        helper.setBlock(rodAt.below(), Blocks.STONE);
        helper.setBlock(rodAt, Blocks.LIGHTNING_ROD.asList().getFirst().defaultBlockState().setValue(RodBlock.FACING, Direction.UP));
        BlockPos sideAt = new BlockPos(5, 3, 2);
        helper.setBlock(sideAt.east(), Blocks.STONE);
        helper.setBlock(sideAt, Blocks.END_ROD.defaultBlockState().setValue(RodBlock.FACING, Direction.WEST));
        BlockPos hangingAt = new BlockPos(6, 3, 5);
        helper.setBlock(hangingAt.above(), Blocks.STONE);
        helper.setBlock(hangingAt, Blocks.END_ROD.defaultBlockState().setValue(RodBlock.FACING, Direction.DOWN));
        BlockPos barsAt = new BlockPos(1, 2, 5);
        helper.setBlock(barsAt, Blocks.IRON_BARS);
        BlockPos rod = helper.absolutePos(rodAt);
        BlockPos side = helper.absolutePos(sideAt);
        BlockPos hanging = helper.absolutePos(hangingAt);
        BlockPos bars = helper.absolutePos(barsAt);
        double px = 1.0 / 16;
        check(helper, RopeSpans.tie(level, rod).distanceTo(new Vec3(rod.getX() + 0.5, rod.getY() + 10.5 * px, rod.getZ() + 0.5)) < 1.0E-9,
                "on a lightning rod standing up: under its cap, round the rod's middle");
        check(helper, RopeSpans.tie(level, side).distanceTo(new Vec3(side.getX() + 14 * px, side.getY() + 0.5, side.getZ() + 0.5)) < 1.0E-9,
                "on an end rod out of a wall: at its foot, against its base, not near its bare tip");
        check(helper, RopeSpans.tie(level, hanging).distanceTo(new Vec3(hanging.getX() + 0.5, hanging.getY() + 14 * px, hanging.getZ() + 0.5)) < 1.0E-9,
                "on one under a ceiling: at its foot, just under the ceiling");
        check(helper, RopeSpans.tie(level, bars).equals(RopeSpans.tie(bars)), "on bars: as on a fence post");

        ItemStack rope = player.getMainHandItem();
        var underBars = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atLowerCornerOf(bars).add(0.5, 0, 0.5), Direction.DOWN, bars, false));
        check(helper, ModItems.THIN_ROPE.get().onItemUseFirst(rope, underBars) == InteractionResult.PASS,
                "bars clicked from below let a rope down, as a fence post does");
        var onTip = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atLowerCornerOf(hanging).add(0.5, 0, 0.5), Direction.DOWN, hanging, false));
        check(helper, ModItems.THIN_ROPE.get().onItemUseFirst(rope, onTip) == InteractionResult.SUCCESS
                && RopeKnotEntity.heldBy(level, player) != null, "a rod under a ceiling, clicked from below, is tied to");
        check(helper, RopeSpans.tie(level, player, rod, rope) == InteractionResult.SUCCESS
                && RopeKnotEntity.at(level, hanging) != null && RopeKnotEntity.at(level, hanging).ties(rod),
                "and the rope tied off on the lightning rod");
        check(helper, RopeSpans.tie(level, player, side, rope) == InteractionResult.SUCCESS
                && RopeSpans.tie(level, player, bars, rope) == InteractionResult.SUCCESS
                && RopeKnotEntity.at(level, side).ties(bars), "and from the end rod on the wall to the bars");
        RopeKnotEntity knot = RopeKnotEntity.at(level, rod);
        check(helper, knot != null && knot.position().distanceTo(RopeSpans.tie(level, rod)) < 1.0E-6, "its knot round the rod");
        RopeKnotEntity underCeiling = RopeKnotEntity.at(level, hanging);
        check(helper, underCeiling.getLightProbePosition(1.0F).equals(underCeiling.position())
                && level.getBlockState(BlockPos.containing(underCeiling.getLightProbePosition(1.0F))).is(Blocks.END_ROD),
                "a knot just under a ceiling is lit where it is, in the rod's place, not in the ceiling");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Lightning on a lightning rod knocks the rope tied to it loose, and what hung by it: they fall, and the same bolt's
     * later flashes - which burn up an item lying there - spare them. A rope tied to a fence post beside the rod stays
     * tied, as vanilla's lead knots do.
     */
    private static void lightningKnocksRopeLoose(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, 16);
        BlockPos rodAt = new BlockPos(1, 3, 2);
        helper.setBlock(rodAt.below(), Blocks.STONE);
        helper.setBlock(rodAt, Blocks.LIGHTNING_ROD.asList().getFirst().defaultBlockState().setValue(RodBlock.FACING, Direction.UP));
        BlockPos fenceAt = new BlockPos(6, 2, 2); // inside the arena: its barrier wall would stand in the rope's way
        helper.setBlock(fenceAt, Blocks.OAK_FENCE);
        BlockPos rod = helper.absolutePos(rodAt);
        BlockPos fence = helper.absolutePos(fenceAt);
        BlockPos nearAt = new BlockPos(2, 2, 4);
        helper.setBlock(nearAt, Blocks.OAK_FENCE);
        BlockPos near = helper.absolutePos(nearAt);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.THIN_ROPE.get(), 32));
        ItemStack rope = player.getMainHandItem();
        check(helper, RopeSpans.tie(level, player, rod, rope) == InteractionResult.SUCCESS
                && RopeSpans.tie(level, player, fence, rope) == InteractionResult.SUCCESS, "strung from the rod to a fence post");
        check(helper, RopeSpans.tie(level, player, near, rope) == InteractionResult.SUCCESS
                && RopeSpans.tie(level, player, fence, rope) == InteractionResult.SUCCESS, "and from a post beside the rod");
        RopeKnotEntity beside = RopeKnotEntity.at(level, near);
        int spent = RopeSpans.ropesFor(rod, fence);
        HungItemEntity bottle = HungItemEntity.onSpan(level, rod, fence, 0.15, new ItemStack(Items.GLASS_BOTTLE));
        level.addFreshEntity(bottle);
        RopeKnotEntity knot = RopeKnotEntity.at(level, rod);
        level.getServer().getPlayerList().remove(player);
        helper.spawn(EntityTypes.LIGHTNING_BOLT, rodAt.above());
        helper.runAfterDelay(60, () -> {
            check(helper, knot.isRemoved(), "struck, the knot on the rod comes undone");
            check(helper, bottle.isRemoved(), "and the bottle hung by it is knocked down");
            check(helper, !beside.isRemoved() && beside.ties(fence), "the rope on the post beside the rod stays tied");
            AABB round = new AABB(rod).inflate(10);
            int ropes = level.getEntitiesOfClass(ItemEntity.class, round).stream()
                    .filter(e -> e.getItem().is(ModItems.THIN_ROPE.get())).mapToInt(e -> e.getItem().getCount()).sum();
            int bottles = level.getEntitiesOfClass(ItemEntity.class, round).stream()
                    .filter(e -> e.getItem().is(Items.GLASS_BOTTLE)).mapToInt(e -> e.getItem().getCount()).sum();
            check(helper, ropes == spent && bottles == 1, "both fell and lie there, not burnt up: " + ropes + " rope of " + spent + ", " + bottles + " bottle");
            helper.succeed();
        });
    }

    private static void ropeIsKnottedRoundAHooksUpright(GameTestHelper helper) {
        BlockPos at = BlockPos.ZERO; // a place only, to work the points out at: nothing is set down
        double px = 1.0 / 16;
        for (AttachFace face : new AttachFace[] {AttachFace.WALL, AttachFace.CEILING}) {
            VoxelShape iron = Shapes.empty();
            try (var in = RopeTests.class.getResourceAsStream("/assets/nookworks/models/block/hook_" + face.getSerializedName() + ".json")) {
                for (var element : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                        .getAsJsonArray("elements")) {
                    var from = element.getAsJsonObject().getAsJsonArray("from");
                    var to = element.getAsJsonObject().getAsJsonArray("to");
                    iron = Shapes.or(iron, Block.box(from.get(0).getAsDouble(), from.get(1).getAsDouble(), from.get(2).getAsDouble(),
                            to.get(0).getAsDouble(), to.get(1).getAsDouble(), to.get(2).getAsDouble()));
                }
            } catch (IOException | NullPointerException e) {
                throw new IllegalStateException("cannot read the hook's model", e);
            }
            var turned = Shapes.rotateHorizontal(iron);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockState state = ModRope.HOOK.get().defaultBlockState().setValue(HookBlock.FACE, face).setValue(HookBlock.FACING, facing);
                List<AABB> boxes = turned.get(facing).toAabbs();
                String where = face.getSerializedName() + " " + facing.getSerializedName();
                Vec3 eye = HookBlock.eye(state, at);
                AABB knot = new AABB(eye, eye).inflate(HookBlock.KNOT_HALF * px, HookBlock.KNOT_HEIGHT / 2 * px, HookBlock.KNOT_HALF * px);
                Predicate<Vec3> inIron = point -> boxes.stream().anyMatch(box -> box.contains(point));
                check(helper, inIron.test(eye) && inIron.test(new Vec3(eye.x, knot.maxY + 0.3 * px, eye.z)),
                        where + ": the upright runs up through the knot");
                // beside the upright, still under the knot: the shank running to the wall, or the bend running to the point
                Vec3 beside = eye.add((face == AttachFace.WALL ? facing.getOpposite() : facing).getUnitVec3().scale(0.75 * px));
                check(helper, inIron.test(new Vec3(beside.x, knot.minY + 0.05 * px, beside.z))
                        && !inIron.test(new Vec3(beside.x, knot.minY + 0.2 * px, beside.z)),
                        where + ": the knot lies on the iron under it, sunk in by a sliver");
                AABB raised = knot.setMinY(knot.minY + 0.15 * px);
                check(helper, boxes.stream().filter(raised::intersects)
                        .allMatch(box -> box.minX <= eye.x && eye.x <= box.maxX && box.minZ <= eye.z && eye.z <= box.maxZ),
                        where + ": and touches no iron but what it is wound round");
                Vec3 out = facing.getUnitVec3();
                Vec3 right = facing.getClockWise().getUnitVec3();
                Vec3 away = face == AttachFace.WALL ? out : out.reverse(); // from the wall; from a ceiling hook's point
                Vec3 down = new Vec3(0, -1.5, 0);
                List<Vec3> ends = List.of(eye.add(away.scale(6)), eye.add(away.scale(6)).add(down), eye.add(right.scale(6)),
                        eye.add(right.reverse().scale(6)));
                AABB clear = knot.inflate(0.45 * px);
                for (Vec3 end : ends) {
                    for (double t = 0; t <= 0.0451; t += 0.001) { // the first quarter block of the span
                        Vec3 point = RopeSpans.at(eye, end, t);
                        if (!clear.contains(point)) {
                            AABB piece = new AABB(point, point).inflate(0.45 * px);
                            check(helper, boxes.stream().noneMatch(piece::intersects),
                                    where + ": the cord to " + end.subtract(eye) + " touches no iron, at " + point);
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    /** Two fences {@code apart} blocks apart along x, at y 2 (the arena is 7 across). */
    /** Glow berries hung, given berries on the vine's top piece as bone meal would: lit where they hang. */
    static HungItemEntity bearing(HungItemEntity vine) {
        vine.setVineBerries(0, true);
        return vine;
    }

    static BlockPos[] posts(GameTestHelper helper, int z, int apart) {
        BlockPos a = new BlockPos(0, 2, z);
        BlockPos b = a.east(apart);
        helper.setBlock(a, Blocks.OAK_FENCE);
        helper.setBlock(b, Blocks.OAK_FENCE);
        return new BlockPos[] {a, b};
    }

    /** A player in adventure mode cannot cut a knot, as they cannot tie a rope or take down what hangs on one. */
    private static void knotSparedByAnAdventurer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, 10);
        BlockPos[] posts = posts(helper, 1, 6);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        ItemStack rope = player.getMainHandItem();
        RopeSpans.tie(level, player, a, rope);
        RopeSpans.tie(level, player, b, rope);
        RopeKnotEntity knot = RopeKnotEntity.at(level, a);
        check(helper, knot != null, "a knot on the first post");
        player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
        player.attack(knot);
        check(helper, !knot.isRemoved() && knot.ties(b), "an adventurer's hit leaves it");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * A knot cut off its post takes its ropes with it, and the knot they ended in goes too (at once since r9:
     * R9RopeTests; end knots are otherwise looked at only every few seconds, there being so many on a strung-up street).
     */
    private static void cutTakesFarKnot(GameTestHelper helper) {
        BlockPos[] posts = posts(helper, 1, 6);
        // once the knots round about have loaded: until then a knot cannot tell "no rope here" from "not loaded yet"
        R9RopeTests.onceLoaded(helper, helper.absolutePos(posts[0]), helper.absolutePos(posts[1]), () -> cutTakesFarKnotLoaded(helper, posts));
    }

    private static void cutTakesFarKnotLoaded(GameTestHelper helper, BlockPos[] posts) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, 10);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        ItemStack rope = player.getMainHandItem();
        RopeSpans.tie(level, player, a, rope);
        RopeSpans.tie(level, player, b, rope);
        RopeKnotEntity knot = RopeKnotEntity.at(level, a);
        check(helper, knot != null && knot.ties(b) && RopeKnotEntity.at(level, b) != null, "strung from the first post to the second");
        knot.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
        check(helper, knot.isRemoved(), "cut");
        helper.runAfterDelay(22, () -> {
            check(helper, RopeKnotEntity.at(level, b) == null, "the knot the rope ended in is gone too");
            level.getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    /**
     * Rope on one post, then on another: tied off there, slack between them, a Thin Rope a block. A post too far is
     * refused and nothing is spent. Take a post away and the span drops its rope.
     */
    private static void strings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, 10);
        BlockPos[] posts = posts(helper, 1, 6);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        ItemStack rope = player.getMainHandItem();
        check(helper, RopeSpans.tie(level, player, a, rope) == InteractionResult.SUCCESS, "tied on the first post");
        RopeKnotEntity knot = RopeKnotEntity.at(level, a);
        check(helper, knot != null && knot.holder() == player && knot.spans().isEmpty(), "the knot holds the player, nothing strung yet");
        check(helper, RopeSpans.tie(level, player, b, rope) == InteractionResult.SUCCESS, "tied off on the second");
        check(helper, knot.ties(b) && knot.holder() == null && RopeKnotEntity.at(level, b) != null, "strung from the first to the second");
        int spent = RopeSpans.ropesFor(a, b);
        check(helper, spent == 6 && rope.getCount() == 10 - spent, "a Thin Rope a block: 6 spent, " + rope.getCount() + " left");
        Vec3 ta = RopeSpans.tie(a);
        Vec3 tb = RopeSpans.tie(b);
        check(helper, RopeSpans.at(ta, tb, 0).distanceTo(ta) < 1.0E-9 && RopeSpans.at(ta, tb, 1).distanceTo(tb) < 1.0E-9, "it runs post to post");
        check(helper, RopeSpans.at(ta, tb, 0.5).y < ta.y - 0.3, "and hangs slack, lowest in the middle: " + RopeSpans.at(ta, tb, 0.5).y);
        check(helper, RopeSpans.tie(level, player, b, rope) == InteractionResult.SUCCESS
                && RopeSpans.tie(level, player, a, rope) == InteractionResult.FAIL && rope.getCount() == 4, "the same two posts are not tied twice");
        check(helper, RopeKnotEntity.heldBy(level, player) != null, "the rope is still in hand, for another post");
        RopeSpans.tie(level, player, b, rope); // clicking the post it came from lets it go
        check(helper, RopeKnotEntity.heldBy(level, player) == null, "let go");

        RopeSpans.tie(level, player, a, rope);
        BlockPos far = a.east((int) RopeSpans.MAX_LENGTH + 2);
        check(helper, RopeSpans.tie(level, player, far, rope) == InteractionResult.FAIL && rope.getCount() == 4,
                "a post too far is refused and nothing spent");
        RopeSpans.tie(level, player, a, rope); // clicking the post it came from lets it go
        check(helper, RopeKnotEntity.heldBy(level, player) == null, "let go");

        helper.setBlock(posts[1], Blocks.AIR);
        helper.runAfterDelay(45, () -> {
            check(helper, !knot.ties(b), "its far post gone, the span is gone");
            int dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(a).inflate(8)).stream()
                    .filter(e -> e.getItem().is(ModItems.THIN_ROPE.get())).mapToInt(e -> e.getItem().getCount()).sum();
            check(helper, dropped == spent, "and its rope dropped: " + dropped);
            level.getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    /**
     * On a span: a fish hangs below the rope where it goes, and in a day of sun dries into Dried Fish (not in the
     * shade). A bottle hangs from a Thin Rope's end; a bulb used on the rope goes on its end as a block. The look is
     * followed to a span. An empty hand takes a thing down.
     */
    private static void hangs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, 20);
        BlockPos[] posts = posts(helper, 2, 4);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, b, player.getMainHandItem());

        HungItemEntity fish = HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(Items.COD));
        level.addFreshEntity(fish);
        Vec3 point = RopeSpans.at(a, b, 0.5);
        check(helper, fish.hangPoint().distanceTo(point) < 1.0E-6 && fish.getBoundingBox().maxY < point.y, "it hangs below the rope");
        fish.dry(level, 20_000, false);
        check(helper, fish.item().is(Items.COD), "in the shade it does not dry");
        fish.dry(level, 12_000, true);
        check(helper, fish.item().is(ModRope.DRIED_FISH.get()), "a day of sun, and it is Dried Fish: " + fish.item());

        // looking at the rope a quarter of the way along, with a bottle
        Vec3 target = RopeSpans.at(a, b, 0.25);
        player.snapTo(target.x, target.y - 1.62, target.z - 2.0, 0.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
        RopeSpans.Pick pick = RopeSpans.pick(level, player);
        check(helper, pick != null && Math.abs(pick.t() - 0.25) < 0.1, "the look finds the rope: " + (pick == null ? "nothing" : pick.t()));
        check(helper, RopeHanging.onSpan(level, player, player.getMainHandItem()) == InteractionResult.SUCCESS
                && player.getMainHandItem().isEmpty(), "and the bottle goes on it there");

        // three quarters along, the rope runs just over a slab: nothing would hang there without sinking into it
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.STONE_SLAB);
        Vec3 low = RopeSpans.at(a, b, 0.75);
        player.snapTo(low.x, low.y - 1.62, low.z - 2.0, 0.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.POTION));
        check(helper, RopeHanging.onSpan(level, player, player.getMainHandItem()) == InteractionResult.FAIL
                && player.getMainHandItem().getCount() == 1, "over a slab there is no room, and the bottle stays in hand");

        // from the end of a rope let down from a block
        BlockPos top = new BlockPos(5, 6, 5);
        helper.setBlock(top.above(), Blocks.STONE);
        helper.setBlock(top, ModBlocks.THIN_ROPE.get());
        ItemStack bottle = new ItemStack(Items.HONEY_BOTTLE);
        InteractionResult hung = helper.getBlockState(top).useItemOn(bottle, level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(top)), Direction.NORTH, helper.absolutePos(top), false));
        check(helper, hung.consumesAction() && bottle.isEmpty() && !level.getEntitiesOfClass(HungItemEntity.class,
                new AABB(helper.absolutePos(top.below()))).isEmpty(), "a bottle hangs from the rope's end");
        ItemStack bulb = new ItemStack(ModRope.FILAMENT_BULB_ITEM.get());
        BlockPos other = new BlockPos(6, 6, 5);
        helper.setBlock(other.above(), Blocks.STONE);
        helper.setBlock(other, ModBlocks.THIN_ROPE.get());
        helper.getBlockState(other).useItemOn(bulb, level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(other)), Direction.NORTH, helper.absolutePos(other), false));
        check(helper, helper.getBlockState(other.below()).is(ModRope.FILAMENT_BULB.get()), "a bulb goes on the rope's end as a block");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        fish.interact(player, InteractionHand.MAIN_HAND, fish.position());
        check(helper, fish.isRemoved() && player.getInventory().countItem(ModRope.DRIED_FISH.get()) == 1, "an empty hand takes it down");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /** It hangs from a block's underside and lights; with nothing over it, it goes. On a span it lights the air. */
    private static void bulb(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(3, 3, 3);
        helper.setBlock(pos.above(), Blocks.STONE);
        helper.setBlock(pos, ModRope.FILAMENT_BULB.get());
        check(helper, helper.getBlockState(pos).getLightEmission() == FilamentBulbBlock.LIGHT_LEVEL, "it gives light");
        helper.setBlock(pos.above(), Blocks.AIR);
        check(helper, helper.getBlockState(pos).isAir(), "with nothing to hang from, it goes");
        check(helper, !ModRope.FILAMENT_BULB.get().defaultBlockState().canSurvive(level, helper.absolutePos(pos)), "and cannot be set there");

        ServerPlayer player = player(helper, 20);
        BlockPos[] posts = posts(helper, 6, 5);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, b, player.getMainHandItem());
        HungItemEntity lit = HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(ModRope.FILAMENT_BULB_ITEM.get()));
        level.addFreshEntity(lit);
        helper.runAfterDelay(25, () -> {
            BlockPos at = lit.blockPosition();
            check(helper, level.getBlockState(at).is(ModRope.HUNG_LIGHT.get()), "a bulb on a rope lights the air it hangs in");
            lit.discard();
            check(helper, level.getBlockState(at).isAir(), "and taken down, the light goes with it");
            level.getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    /**
     * Lanterns hang on a rope and on a chain as the lantern block itself, hanging (the user, 2026-10-16): every lantern
     * of vanilla's tag and every unlit one of ours; the click on the rope hangs one where the player looks. A lit one
     * lights the air at once, as bright as the block (a soul lantern 10), one put out not at all. It keeps a lantern's
     * room along the line, takes up the block's six pixels across and its height under the rope, and is saved and loaded
     * as it was, wax, weathering and all.
     */
    private static void lanternsHang(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, 20);
        BlockPos[] posts = posts(helper, 2, 6);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, b, player.getMainHandItem());
        ServerPlayer chainer = ChainTests.chainer(helper, 20);
        BlockPos[] chainPosts = posts(helper, 5, 6);
        BlockPos c = helper.absolutePos(chainPosts[0]);
        BlockPos d = helper.absolutePos(chainPosts[1]);
        RopeSpans.tie(level, chainer, c, chainer.getMainHandItem());
        RopeSpans.tie(level, chainer, d, chainer.getMainHandItem());
        check(helper, RopeKnotEntity.at(level, c).material(d) == SpanMaterial.CHAIN, "a chain strung too");
        level.getServer().getPlayerList().remove(chainer);

        List<ItemStack> lanterns = new ArrayList<>(List.of(new ItemStack(Items.LANTERN), new ItemStack(Items.SOUL_LANTERN),
                new ItemStack(ModItems.UNLIT_LANTERN.get()), new ItemStack(ModItems.UNLIT_SOUL_LANTERN.get())));
        for (String copper : List.of("copper_lantern", "exposed_copper_lantern", "weathered_copper_lantern", "oxidized_copper_lantern",
                "waxed_copper_lantern", "waxed_exposed_copper_lantern", "waxed_weathered_copper_lantern", "waxed_oxidized_copper_lantern")) {
            lanterns.add(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(copper))));
            lanterns.add(new ItemStack(ModItems.UNLIT_COPPER_LANTERNS.get(copper).get()));
        }
        check(helper, lanterns.size() == 20, "twenty lanterns");
        for (ItemStack lantern : lanterns) {
            BlockState hanging = HungItemEntity.lanternState(lantern);
            check(helper, HungItemEntity.hangs(lantern) && hanging != null && hanging.getValue(LanternBlock.HANGING)
                    && lantern.is(hanging.getBlock().asItem()), "it hangs, as its block hanging: " + lantern);
            HungItemEntity hung = HungItemEntity.onSpan(level, a, b, 0.5, lantern);
            check(helper, hung.isLantern() && !hung.isSolid() && hung.glow() == hanging.getLightEmission(), "and lights as its block: " + lantern);
        }
        check(helper, HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(Items.LANTERN)).glow() == 15
                && HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(Items.SOUL_LANTERN)).glow() == 10
                && HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(Items.COPPER_LANTERN.waxed().oxidized())).glow() == 15
                && HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(ModItems.UNLIT_LANTERN.get())).glow() == 0,
                "a lantern 15, a soul lantern 10, a copper one 15, one put out none");
        check(helper, !HungItemEntity.isLantern(new ItemStack(Items.SEA_LANTERN)) && !HungItemEntity.hangs(new ItemStack(Items.SEA_LANTERN))
                && !HungItemEntity.isLantern(new ItemStack(Items.GLASS_BOTTLE)), "a sea lantern is no lantern to hang, nor a bottle");

        // hung by a click on the rope where the player looks, as anything is
        Vec3 target = RopeSpans.at(a, b, 0.3);
        player.snapTo(target.x, target.y - 1.62, target.z - 2.0, 0.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LANTERN));
        check(helper, RopeHanging.onSpan(level, player, player.getMainHandItem()) == InteractionResult.SUCCESS
                && player.getMainHandItem().isEmpty(), "a lantern goes on the rope where the player looks");
        HungItemEntity lit = level.getEntitiesOfClass(HungItemEntity.class, new AABB(a).minmax(new AABB(b)).inflate(1),
                h -> h.item().is(Items.LANTERN)).stream().findFirst().orElse(null);
        check(helper, lit != null, "it hangs there");
        Vec3 point = lit.hangPoint();
        AABB box = lit.getBoundingBox();
        check(helper, Math.abs(box.getXsize() - 2 * HungItemEntity.LANTERN_HALF) < 1e-6 && Math.abs(box.getZsize() - 2 * HungItemEntity.LANTERN_HALF) < 1e-6
                && box.maxY < point.y && Math.abs(box.minY - (point.y - HungItemEntity.LANTERN_DROP)) < 1e-6,
                "it takes up the lantern's six pixels across and its height under the rope: " + box);
        // another lantern beside it keeps a lantern's room: a bottle's spacing is too close, a lantern's is not
        double t = lit.along();
        double length = RopeSpans.tie(level, a).distanceTo(RopeSpans.tie(level, b));
        double near = t + RopeHanging.SPACING / length;
        double far = t + 2.01 * HungItemEntity.LANTERN_ROOM / length;
        check(helper, HungItemEntity.room(new ItemStack(Items.SOUL_LANTERN)) == HungItemEntity.LANTERN_ROOM
                && RopeHanging.tooNear(level, a, b, near, RopeSpans.at(a, b, near), HungItemEntity.LANTERN_ROOM, false)
                && !RopeHanging.tooNear(level, a, b, far, RopeSpans.at(a, b, far), HungItemEntity.LANTERN_ROOM, false),
                "the next lantern keeps a lantern's room");

        HungItemEntity soul = HungItemEntity.onSpan(level, a, b, 0.8, new ItemStack(Items.SOUL_LANTERN));
        HungItemEntity out = HungItemEntity.onSpan(level, c, d, 0.3, new ItemStack(ModItems.UNLIT_LANTERN.get()));
        ItemStack waxed = new ItemStack(Items.COPPER_LANTERN.waxed().weathered());
        HungItemEntity copper = HungItemEntity.onSpan(level, c, d, 0.7, waxed);
        check(helper, soul.misfit(level) == null && out.misfit(level) == null && copper.misfit(level) == null, "room under rope and chain");
        level.addFreshEntity(soul);
        level.addFreshEntity(out);
        level.addFreshEntity(copper);

        // saved and loaded, it is the same lantern on the same chain
        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        copper.saveWithoutId(saved);
        HungItemEntity loaded = new HungItemEntity(ModRope.HUNG_ITEM.get(), level);
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved.buildResult()));
        check(helper, ItemStack.matches(loaded.item(), copper.item()) && loaded.isLantern() && c.equals(loaded.spanFrom())
                && d.equals(loaded.spanTo()) && Math.abs(loaded.along() - 0.7) < 1e-9, "saved and loaded, the same lantern where it was");
        level.getServer().getPlayerList().remove(player);
        helper.runAfterDelay(3, () -> {
            for (HungItemEntity hung : List.of(lit, soul, copper)) {
                check(helper, hung.litAt() != null && level.getBlockState(hung.litAt()).is(ModRope.HUNG_LIGHT.get())
                        && level.getBlockState(hung.litAt()).getValue(LightBlock.LEVEL) == hung.glow(),
                        "a lit lantern lights the air at once, as bright as its block: " + hung.item());
            }
            check(helper, out.litAt() == null && out.glow() == 0, "one put out gives no light");
            BlockPos at = lit.litAt();
            ServerPlayer taker = player(helper, 0);
            taker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            lit.interact(taker, InteractionHand.MAIN_HAND, lit.position());
            check(helper, lit.isRemoved() && taker.getInventory().countItem(Items.LANTERN) == 1 && level.getBlockState(at).isAir(),
                    "an empty hand takes it down, the same lantern, and its light goes");
            level.getServer().getPlayerList().remove(taker);
            List.of(soul, out, copper).forEach(HungItemEntity::discard);
            helper.succeed();
        });
    }

    /** A rope cut, what hangs on it falls: a lantern drops as the lantern it is (one put out, put out). */
    private static void lanternFallsWithItsRope(GameTestHelper helper) {
        BlockPos[] posts = posts(helper, 2, 5);
        R9RopeTests.onceLoaded(helper, helper.absolutePos(posts[0]), helper.absolutePos(posts[1]), () -> {
            ServerLevel level = helper.getLevel();
            ServerPlayer player = player(helper, 20);
            BlockPos a = helper.absolutePos(posts[0]);
            BlockPos b = helper.absolutePos(posts[1]);
            RopeSpans.tie(level, player, a, player.getMainHandItem());
            RopeSpans.tie(level, player, b, player.getMainHandItem());
            HungItemEntity lit = HungItemEntity.onSpan(level, a, b, 0.3, new ItemStack(Items.LANTERN));
            HungItemEntity out = HungItemEntity.onSpan(level, a, b, 0.7, new ItemStack(ModItems.UNLIT_SOUL_LANTERN.get()));
            level.addFreshEntity(lit);
            level.addFreshEntity(out);
            helper.runAfterDelay(3, () -> {
                BlockPos at = lit.litAt();
                check(helper, at != null, "the lantern lights where it hangs");
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.attack(RopeKnotEntity.at(level, a));
                level.getServer().getPlayerList().remove(player);
                helper.runAfterDelay(25, () -> {
                    check(helper, lit.isRemoved() && out.isRemoved(), "the rope cut, both come down");
                    List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(a).minmax(new AABB(b)).inflate(3));
                    check(helper, drops.stream().anyMatch(e -> e.getItem().is(Items.LANTERN))
                            && drops.stream().anyMatch(e -> e.getItem().is(ModItems.UNLIT_SOUL_LANTERN.get())),
                            "and drop as the lanterns they are");
                    check(helper, !level.getBlockState(at).is(ModRope.HUNG_LIGHT.get()), "the light goes with it");
                    helper.succeed();
                });
            });
        });
    }

    /**
     * Used on a Thin Rope let down from a block, a lantern hangs from its end as it hangs on a span - drawn as the block,
     * lit or put out as the one in hand, a lit one lighting the air at once; the rope taken away, it drops as it is.
     * (Vanilla's lantern block does not hang under Thin Rope, whose cord is no support.)
     */
    private static void lanternOnARopesEnd(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, 0);
        BlockPos top = new BlockPos(3, 6, 3);
        helper.setBlock(top.above(), Blocks.STONE);
        helper.setBlock(top, ModBlocks.THIN_ROPE.get());
        helper.setBlock(top.below(), ModBlocks.THIN_ROPE.get());
        ItemStack lantern = new ItemStack(Items.SOUL_LANTERN);
        InteractionResult hung = helper.getBlockState(top).useItemOn(lantern, level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(top)), Direction.NORTH, helper.absolutePos(top), false));
        List<HungItemEntity> soul = level.getEntitiesOfClass(HungItemEntity.class, new AABB(helper.absolutePos(top.below(2))),
                h -> h.item().is(Items.SOUL_LANTERN));
        check(helper, hung.consumesAction() && lantern.isEmpty() && soul.size() == 1 && soul.get(0).isLantern() && !soul.get(0).onSpan(),
                "used on the rope, it hangs from its end");
        BlockPos other = new BlockPos(5, 6, 3);
        helper.setBlock(other.above(), Blocks.STONE);
        helper.setBlock(other, ModBlocks.THIN_ROPE.get());
        Item unlitCopper = ModItems.UNLIT_COPPER_LANTERNS.get("waxed_exposed_copper_lantern").get();
        ItemStack unlit = new ItemStack(unlitCopper);
        helper.getBlockState(other).useItemOn(unlit, level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(other)), Direction.NORTH, helper.absolutePos(other), false));
        List<HungItemEntity> out = level.getEntitiesOfClass(HungItemEntity.class, new AABB(helper.absolutePos(other.below())),
                h -> h.item().is(unlitCopper));
        check(helper, unlit.isEmpty() && out.size() == 1 && out.get(0).glow() == 0, "one put out hangs put out");
        level.getServer().getPlayerList().remove(player);
        helper.runAfterDelay(3, () -> {
            check(helper, soul.get(0).litAt() != null && level.getBlockState(soul.get(0).litAt()).getValue(LightBlock.LEVEL) == 10,
                    "the soul lantern lights the air at once, as a soul lantern does");
            helper.setBlock(other, Blocks.AIR);
            helper.runAfterDelay(21, () -> {
                check(helper, out.get(0).isRemoved() && level.getEntitiesOfClass(ItemEntity.class,
                        new AABB(helper.absolutePos(other)).inflate(2), e -> e.getItem().is(unlitCopper)).size() == 1,
                        "the rope taken away, it drops as it is");
                soul.get(0).discard();
                helper.succeed();
            });
        });
    }

    /**
     * A hung lantern goes out and is lit where it hangs, as the block is (the user, 2026-10-16): sneak and use it with an
     * empty hand and a lit one becomes its unlit one, weathering and wax kept, its light gone; flint and steel (worn a
     * use) or a fire charge (spent, but not in creative) lights it again and its light is back. Not sneaking, the hand
     * takes it down as before; an adventurer can do neither.
     */
    private static void hungLanternsGoOutAndLight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, 20);
        BlockPos[] posts = posts(helper, 2, 6);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, b, player.getMainHandItem());
        HungItemEntity copper = HungItemEntity.onSpan(level, a, b, 0.3, new ItemStack(Items.COPPER_LANTERN.waxed().weathered()));
        HungItemEntity soul = HungItemEntity.onSpan(level, a, b, 0.7, new ItemStack(Items.SOUL_LANTERN));
        level.addFreshEntity(copper);
        level.addFreshEntity(soul);
        Item unlitCopper = ModItems.UNLIT_COPPER_LANTERNS.get("waxed_weathered_copper_lantern").get();
        helper.runAfterDelay(3, () -> {
            BlockPos at = copper.litAt();
            check(helper, at != null && level.getBlockState(at).getValue(LightBlock.LEVEL) == 15, "hung lit, it lights at 15");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setShiftKeyDown(true);
            check(helper, copper.interact(player, InteractionHand.MAIN_HAND, copper.position()) == InteractionResult.SUCCESS
                    && !copper.isRemoved() && copper.item().is(unlitCopper) && copper.glow() == 0 && copper.litAt() == null
                    && !level.getBlockState(at).is(ModRope.HUNG_LIGHT.get()),
                    "sneaking, an empty hand puts it out where it hangs, waxed and weathered still, and its light goes: " + copper.item());
            soul.interact(player, InteractionHand.MAIN_HAND, soul.position());
            check(helper, soul.item().is(ModItems.UNLIT_SOUL_LANTERN.get()) && soul.litAt() == null, "a soul lantern goes out into its own");
            check(helper, copper.interact(player, InteractionHand.MAIN_HAND, copper.position()) == InteractionResult.SUCCESS
                    && copper.isRemoved() && player.getInventory().countItem(unlitCopper) == 1,
                    "one put out has nothing to put out: the hand takes it down, put out");
            player.getInventory().clearContent();
            player.setShiftKeyDown(false);

            ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
            player.setItemInHand(InteractionHand.MAIN_HAND, flint);
            check(helper, soul.interact(player, InteractionHand.MAIN_HAND, soul.position()) == InteractionResult.SUCCESS
                    && soul.item().is(Items.SOUL_LANTERN) && soul.glow() == 10 && soul.litAt() != null
                    && level.getBlockState(soul.litAt()).getValue(LightBlock.LEVEL) == 10 && flint.getDamageValue() == 1,
                    "flint and steel lights it again where it hangs, at once, worn a use");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setShiftKeyDown(true);
            soul.interact(player, InteractionHand.MAIN_HAND, soul.position());
            player.setShiftKeyDown(false);
            ItemStack charges = new ItemStack(Items.FIRE_CHARGE, 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, charges);
            check(helper, soul.interact(player, InteractionHand.MAIN_HAND, soul.position()) == InteractionResult.SUCCESS
                    && soul.item().is(Items.SOUL_LANTERN) && charges.getCount() == 1, "a fire charge lights it, and is spent");
            check(helper, soul.interact(player, InteractionHand.MAIN_HAND, soul.position()) == InteractionResult.PASS
                    && charges.getCount() == 1, "a lit one is not lit again, nor the charge spent");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setShiftKeyDown(true);
            soul.interact(player, InteractionHand.MAIN_HAND, soul.position());
            player.setShiftKeyDown(false);
            player.setGameMode(GameType.CREATIVE);
            player.setItemInHand(InteractionHand.MAIN_HAND, charges);
            soul.interact(player, InteractionHand.MAIN_HAND, soul.position());
            check(helper, soul.item().is(Items.SOUL_LANTERN) && charges.getCount() == 1, "in creative the charge is not spent");

            player.setGameMode(GameType.ADVENTURE);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setShiftKeyDown(true);
            check(helper, soul.interact(player, InteractionHand.MAIN_HAND, soul.position()) == InteractionResult.FAIL
                    && soul.item().is(Items.SOUL_LANTERN), "an adventurer cannot put it out");
            level.getServer().getPlayerList().remove(player);
            soul.discard();
            helper.succeed();
        });
    }

    /**
     * Glow berries hang on a rope as vanilla's cave vine bearing them, a block tall (the user, 2026-10-16), lit as the
     * vine is (14, at once); the vine keeps its reach along the line, and an empty hand takes back the glow berries. From
     * a Thin Rope's end too. Sweet berries stay a sprig.
     */
    private static void glowBerriesHangAsTheVine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, 20);
        BlockPos[] posts = posts(helper, 2, 6);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, b, player.getMainHandItem());
        ItemStack glow = new ItemStack(Items.GLOW_BERRIES);
        BlockState vine = HungItemEntity.drawnBlock(glow);
        check(helper, vine != null && vine.is(Blocks.CAVE_VINES) && !vine.getValue(net.minecraft.world.level.block.CaveVines.BERRIES)
                && HungItemEntity.isVine(glow) && !HungItemEntity.SPRIGS.containsKey(Items.GLOW_BERRIES)
                && HungItemEntity.SPRIGS.containsKey(Items.SWEET_BERRIES) && HungItemEntity.drawnBlock(new ItemStack(Items.SWEET_BERRIES)) == null,
                "glow berries are drawn as the vine, as set under a ceiling, sweet berries still a sprig");
        check(helper, HungItemEntity.room(glow) == HungItemEntity.VINE_ROOM, "the vine keeps its reach along the rope");
        HungItemEntity berries = HungItemEntity.onSpan(level, a, b, 0.3, glow);
        Vec3 point = berries.hangPoint();
        AABB box = berries.getBoundingBox();
        check(helper, Math.abs(box.getXsize() - 2 * HungItemEntity.VINE_HALF) < 1e-6 && Math.abs(box.minY - (point.y - HungItemEntity.VINE_DROP)) < 1e-6
                && box.maxY < point.y, "it takes up the vine's block under the rope: " + box);
        check(helper, berries.glow() == 0 && !berries.vineBerries(0), "hung, the vine bears no berries yet, and gives no light");
        level.addFreshEntity(berries);
        double length = RopeSpans.tie(level, a).distanceTo(RopeSpans.tie(level, b));
        double near = 0.3 + 0.6 / length;
        double far = 0.3 + 2.01 * HungItemEntity.VINE_ROOM / length;
        check(helper, RopeHanging.tooNear(level, a, b, near, RopeSpans.at(a, b, near), HungItemEntity.VINE_ROOM, false)
                && !RopeHanging.tooNear(level, a, b, far, RopeSpans.at(a, b, far), HungItemEntity.VINE_ROOM, false),
                "the next vine keeps a vine's reach away");

        BlockPos top = new BlockPos(5, 6, 5);
        helper.setBlock(top.above(), Blocks.STONE);
        helper.setBlock(top, ModBlocks.THIN_ROPE.get());
        ItemStack more = new ItemStack(Items.GLOW_BERRIES);
        helper.getBlockState(top).useItemOn(more, level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(top)), Direction.NORTH, helper.absolutePos(top), false));
        List<HungItemEntity> end = level.getEntitiesOfClass(HungItemEntity.class, new AABB(helper.absolutePos(top.below())),
                h -> h.item().is(Items.GLOW_BERRIES));
        check(helper, more.isEmpty() && end.size() == 1 && !end.get(0).onSpan(), "glow berries hang from a rope's end as the vine too");
        helper.runAfterDelay(3, () -> {
            check(helper, berries.litAt() == null, "unlit while it bears none");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE_MEAL));
            check(helper, berries.interact(player, InteractionHand.MAIN_HAND, berries.position()) == InteractionResult.SUCCESS
                    && berries.vineBerries(0) && berries.litAt() != null
                    && level.getBlockState(berries.litAt()).getValue(LightBlock.LEVEL) == 14, "bone meal gives the top berries, lit at 14 at once");
            BlockPos at = berries.litAt();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            check(helper, berries.interact(player, InteractionHand.MAIN_HAND, berries.position()) == InteractionResult.SUCCESS
                    && !berries.isRemoved() && !berries.vineBerries(0) && berries.litAt() == null && level.getBlockState(at).isAir(),
                    "an empty hand picks the top's berries, and its light goes");
            berries.interact(player, InteractionHand.MAIN_HAND, berries.position());
            check(helper, berries.isRemoved() && player.getInventory().countItem(Items.GLOW_BERRIES) == 1,
                    "bare, an empty hand takes the vine down: the glow berries hung come back");
            end.get(0).discard();
            level.getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }
}
