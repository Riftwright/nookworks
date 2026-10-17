package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.RopeTests.posts;

import java.util.List;
import java.util.function.Consumer;

import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import dev.nookworks.rope.HungItemEntity;
import dev.nookworks.rope.RopeKnotEntity;
import dev.nookworks.rope.RopeSpans;
import dev.nookworks.storage.StorageContents;
import dev.nookworks.storage.StorageItem;
import dev.nookworks.storage.StorageSpill;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * r7's fixes to the Storage Barrels and Thin Rope: a barrel item refused by a claim mod copies nothing, an adventurer's
 * arrow does not cut a knot, a hub's rope drops in whole stacks, and the entity_drops rule holds for ropes and what hangs.
 */
final class R7StorageRopeTests {
    private R7StorageRopeTests() {}

    static void register() {
        NookworksGameTests.test("a_barrel_item_refused_by_a_claim_copies_nothing", R7StorageRopeTests::refusedBarrelCopiesNothing);
        NookworksGameTests.test("a_rope_knot_is_not_cut_by_an_adventurers_arrow_or_snowball", R7StorageRopeTests::knotSparedAdventurersShots);
        NookworksGameTests.test("a_hub_knot_drops_its_rope_in_whole_stacks", R7StorageRopeTests::hubDropsWholeStacks);
        NookworksGameTests.test("rope_and_hung_things_drop_nothing_with_entity_drops_off", R7StorageRopeTests::noDropsWithEntityDropsOff);
    }

    /**
     * A claim mod refuses the placing after the barrel was set down (the clicked block outside the claim, the cell
     * inside): the barrel is taken back up and the item handed back as it was. Nothing comes out of the barrel taken
     * back - not a second whole barrel (one too full to break, as a Wither breaking it leaves), not a spill (one under
     * the limit, made by a command) - or every refusal made a copy of everything.
     */
    private static void refusedBarrelCopiesNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 stand = helper.absoluteVec(new Vec3(5.5, 1, 5.5));
        player.snapTo(stand.x, stand.y, stand.z);
        int[] refused = {0};
        Consumer<BlockEvent.EntityPlaceEvent> claim = event -> {
            if (event.getEntity() == player) {
                event.setCanceled(true);
                refused[0]++;
            }
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, claim);
        AfterTest.undo(helper, () -> NeoForge.EVENT_BUS.unregister(claim));

        ItemStack whole = new ItemStack(ModBlocks.VELARIUM_STORAGE.get());
        whole.set(ModDataComponents.STORAGE.get(), new StorageContents(List.of(
                new StorageContents.Entry(0, new ItemStack(Items.COBBLESTONE), 1024 * 64),
                new StorageContents.Entry(1, new ItemStack(Items.DIAMOND), 5_000))));
        ItemStack small = new ItemStack(ModBlocks.IRON_STORAGE.get());
        small.set(ModDataComponents.STORAGE.get(), new StorageContents(List.of(
                new StorageContents.Entry(0, new ItemStack(Items.DIAMOND), 10))));
        String[] names = {"a whole barrel", "a barrel under the limit"};
        ItemStack[] items = {whole, small};
        for (int i = 0; i < items.length; i++) {
            BlockPos floor = new BlockPos(1 + i * 2, 0, 1);
            BlockPos cell = floor.above();
            BlockPos clicked = helper.absolutePos(floor);
            player.setItemInHand(InteractionHand.MAIN_HAND, items[i].copy());
            InteractionResult result = player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(clicked).add(0, 0.5, 0), Direction.UP, clicked, false)));
            check(helper, refused[0] == i + 1 && !result.consumesAction(), names[i] + ": set down, and refused by the claim, " + result);
            check(helper, helper.getBlockState(cell).isAir(), names[i] + ": the cell left empty");
            List<ItemEntity> out = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds());
            check(helper, out.isEmpty(), names[i] + ": nothing came out of the barrel taken back, " + out.size() + " items");
            check(helper, StorageSpill.pending(level, helper.absolutePos(cell)) == 0, names[i] + ": and nothing waits to spill");
            ItemStack hand = player.getMainHandItem();
            check(helper, hand.getCount() == 1 && ItemStack.isSameItemSameComponents(hand, items[i]) && StorageItem.holdsSomething(hand),
                    names[i] + ": the hand keeps the item as it was, contents and all: " + hand);
        }
        helper.succeed();
    }

    /**
     * An adventurer who may not hit a knot may not shoot or throw at it either: an arrow or a snowball of theirs leaves
     * it tied (cut, what hung on its spans fell for them to pick up). A survival player's arrow still cuts it.
     */
    private static void knotSparedAdventurersShots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = RopeTests.player(helper, 10);
        BlockPos[] posts = posts(helper, 1, 6);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, b, player.getMainHandItem());
        RopeKnotEntity knot = RopeKnotEntity.at(level, a);
        check(helper, knot != null && knot.ties(b), "strung from the first post to the second");

        player.setGameMode(GameType.ADVENTURE);
        Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
        Snowball snowball = new Snowball(level, player, new ItemStack(Items.SNOWBALL));
        knot.hurtOrSimulate(level.damageSources().arrow(arrow, player), 2.0F);
        knot.hurtOrSimulate(level.damageSources().thrown(snowball, player), 0.0F);
        check(helper, !knot.isRemoved() && knot.ties(b), "an adventurer's arrow and snowball leave the knot tied");
        check(helper, level.getEntitiesOfClass(ItemEntity.class, helper.getBounds()).isEmpty(), "and no rope dropped");

        player.setGameMode(GameType.SURVIVAL);
        knot.hurtOrSimulate(level.damageSources().arrow(arrow, player), 2.0F);
        check(helper, knot.isRemoved(), "a survival player's arrow still cuts it");
        helper.succeed();
    }

    /**
     * A pole with two dozen spokes, over a hundred Thin Rope, cut: the rope comes out in stacks of at most 64. In one
     * item of more than 99 it was not saved, and lost if its chunk was saved before anyone picked it up.
     */
    private static void hubDropsWholeStacks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = MockPlayers.inLevel(helper); // creative: tying takes no rope
        Vec3 stand = helper.absoluteVec(new Vec3(1.5, 1, 3.5));
        player.snapTo(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.THIN_ROPE.get()));
        BlockPos hub = helper.absolutePos(new BlockPos(3, 3, 3));
        helper.setBlock(new BlockPos(3, 3, 3), Blocks.OAK_FENCE);
        int ropes = 0;
        for (int corner = 0; corner < 4; corner++) {
            for (int y = 1; y <= 6; y++) {
                BlockPos rel = new BlockPos(corner % 2 * 6, y, corner / 2 * 6);
                helper.setBlock(rel, Blocks.OAK_FENCE);
                BlockPos post = helper.absolutePos(rel);
                RopeSpans.tie(level, player, hub, player.getMainHandItem());
                RopeSpans.tie(level, player, post, player.getMainHandItem());
                ropes += RopeSpans.ropesFor(hub, post);
            }
        }
        RopeKnotEntity knot = RopeKnotEntity.at(level, hub);
        check(helper, knot != null && knot.spans().size() == 24, "24 spokes from the pole: " + (knot == null ? 0 : knot.spans().size()));
        check(helper, ropes > 99, "more rope than one saved item holds: " + ropes);
        knot.hurtServer(level, level.damageSources().generic(), 1.0F);
        check(helper, knot.isRemoved(), "cut");
        List<ItemEntity> out = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds(), item -> item.getItem().is(ModItems.THIN_ROPE.get()));
        int max = new ItemStack(ModItems.THIN_ROPE.get()).getMaxStackSize();
        int total = 0;
        for (ItemEntity item : out) {
            check(helper, item.getItem().getCount() <= max, "no stack over " + max + ": " + item.getItem().getCount());
            total += item.getItem().getCount();
        }
        check(helper, total == ropes, "all the rope dropped, " + total + " of " + ropes);
        out.forEach(ItemEntity::discard);
        helper.succeed();
    }

    /**
     * With the entity_drops rule off, as an item frame's item and a broken lead give nothing, a knot cut gives no rope
     * and what hung on its span gives nothing as it falls: both are simply gone.
     */
    private static void noDropsWithEntityDropsOff(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = RopeTests.player(helper, 10);
        BlockPos[] posts = posts(helper, 1, 6);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, b, player.getMainHandItem());
        RopeKnotEntity knot = RopeKnotEntity.at(level, a);
        check(helper, knot != null && knot.ties(b), "strung from the first post to the second");
        HungItemEntity fish = HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(Items.COD));
        level.addFreshEntity(fish);

        var rules = level.getGameRules();
        boolean was = rules.get(GameRules.ENTITY_DROPS);
        rules.set(GameRules.ENTITY_DROPS, false, level.getServer()); // the whole server's: put back before this tick ends
        try {
            knot.hurtServer(level, level.damageSources().generic(), 1.0F);
            fish.move(MoverType.PISTON, new Vec3(0.5, 0, 0)); // falls at once, as it would at its next look with the rope gone
            check(helper, knot.isRemoved() && fish.isRemoved(), "the knot cut and the fish fallen");
            List<ItemEntity> out = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds());
            check(helper, out.isEmpty(), "and nothing dropped: " + out.stream().map(ItemEntity::getItem).toList());
        } finally {
            rules.set(GameRules.ENTITY_DROPS, was, level.getServer());
        }
        helper.succeed();
    }
}
