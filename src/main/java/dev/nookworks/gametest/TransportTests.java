package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.List;

import dev.nookworks.block.LineWarpBlock;
import dev.nookworks.block.entity.ItemWarpSenderBlockEntity;
import dev.nookworks.block.entity.LineWarpSenderBlockEntity;
import dev.nookworks.block.entity.MagnetChestBlockEntity;
import dev.nookworks.compat.FeatureGate;
import dev.nookworks.feature.Elevators;
import dev.nookworks.item.MagnetItem;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Moving players and things: the Elevator, the Item Warps, the magnets. */
final class TransportTests {
    static void register() {
        test("elevator_jump_goes_up", TransportTests::elevatorJumpGoesUp);
        test("elevator_sneak_goes_down", TransportTests::elevatorSneakGoesDown);
        test("elevator_skips_blocked_floor", TransportTests::elevatorSkipsBlocked);
        test("elevator_alone_does_nothing", TransportTests::elevatorAlone);
        test("item_warp_dropped_items_reach_chest", TransportTests::warpIntoChest);
        test("item_warp_drops_below_receiver", TransportTests::warpDropsBelow);
        test("item_warp_accepts_hopper_input", TransportTests::warpFromHopper);
        test("item_warp_waits_without_receiver", TransportTests::warpWaits);
        test("magnet_pulls_items_but_not_fresh_drops", TransportTests::magnetPulls);
        test("magnet_pulls_experience_from_its_whole_reach", TransportTests::magnetPullsOrbs);
        test("magnet_leaves_to_a_nearer_magnet", TransportTests::magnetLeavesToANearerMagnet);
        test("conflicting_mods_stand_our_features_down", TransportTests::conflictingModsStandDown);
        test("line_warp_sends_along_its_facing", TransportTests::lineWarpSends);
        test("line_warp_ignores_other_lines", TransportTests::lineWarpIgnoresOffLine);
        test("magnet_chest_collects_nearby_drops", TransportTests::magnetChestCollects);
    }

    // ---- Elevator ----

    /** A connected mock player (elevators teleport through the network connection) standing on {@code block}. */
    @SuppressWarnings("removal")
    static ServerPlayer playerOn(GameTestHelper helper, BlockPos block) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 feet = helper.absoluteVec(new Vec3(block.getX() + 0.3, block.getY() + 1.0, block.getZ() + 0.7));
        player.connection.teleport(feet.x, feet.y, feet.z, 45.0F, 10.0F);
        return player;
    }

    static void checkStandingOn(GameTestHelper helper, ServerPlayer player, BlockPos block) {
        Vec3 expected = helper.absoluteVec(new Vec3(block.getX() + 0.3, block.getY() + 1.0, block.getZ() + 0.7));
        check(helper, player.position().distanceTo(expected) < 1.0E-4,
                "player should stand on " + block + " at " + expected + ", is at " + player.position());
    }

    static void elevatorJumpGoesUp(GameTestHelper helper) {
        BlockPos lower = new BlockPos(3, 1, 3);
        BlockPos upper = new BlockPos(3, 4, 3);
        helper.setBlock(lower, ModBlocks.ELEVATOR.get());
        helper.setBlock(upper, ModBlocks.ELEVATOR.get());
        ServerPlayer player = playerOn(helper, lower);

        // The real path: a jump is queued and performed at the end of the player tick.
        player.jumpFromGround();
        // By the end of the tick a real player has already risen off the block (seen in-game: +0.84).
        player.connection.teleport(player.getX(), player.getY() + 0.84, player.getZ(), 45.0F, 10.0F);
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));

        checkStandingOn(helper, player, upper);
        check(helper, player.getDeltaMovement().y <= 0.0, "jump momentum must be cancelled on arrival");
        check(helper, player.getYRot() == 45.0F, "view direction must be kept");
        helper.succeed();
    }

    static void elevatorSneakGoesDown(GameTestHelper helper) {
        BlockPos lower = new BlockPos(3, 1, 3);
        BlockPos upper = new BlockPos(3, 4, 3);
        helper.setBlock(lower, ModBlocks.ELEVATOR.get());
        helper.setBlock(upper, ModBlocks.ELEVATOR.get());
        ServerPlayer player = playerOn(helper, upper);

        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player)); // not sneaking yet
        player.setShiftKeyDown(true);
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player)); // sneak pressed: go down
        checkStandingOn(helper, player, lower);
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player)); // still holding: no further move
        checkStandingOn(helper, player, lower);
        helper.succeed();
    }

    static void elevatorSkipsBlocked(GameTestHelper helper) {
        BlockPos lower = new BlockPos(3, 1, 3);
        BlockPos blocked = new BlockPos(3, 3, 3);
        BlockPos top = new BlockPos(3, 5, 3);
        helper.setBlock(lower, ModBlocks.ELEVATOR.get());
        helper.setBlock(blocked, ModBlocks.ELEVATOR.get());
        helper.setBlock(blocked.above(), Blocks.STONE); // no headroom on the middle one
        helper.setBlock(top, ModBlocks.ELEVATOR.get());
        ServerPlayer player = playerOn(helper, lower);

        check(helper, Elevators.tryMove(player, true), "should find the top elevator");
        checkStandingOn(helper, player, top);
        helper.succeed();
    }

    static void elevatorAlone(GameTestHelper helper) {
        BlockPos only = new BlockPos(3, 1, 3);
        helper.setBlock(only, ModBlocks.ELEVATOR.get());
        ServerPlayer player = playerOn(helper, only);

        check(helper, !Elevators.tryMove(player, true), "no elevator above: must not move");
        check(helper, !Elevators.tryMove(player, false), "no elevator below: must not move");
        checkStandingOn(helper, player, only);
        helper.succeed();
    }

    // ---- Item warp ----

    static void dropOnto(GameTestHelper helper, BlockPos block, ItemStack stack) {
        Vec3 at = helper.absoluteVec(new Vec3(block.getX() + 0.5, block.getY() + 1.1, block.getZ() + 0.5));
        helper.getLevel().addFreshEntity(new ItemEntity(helper.getLevel(), at.x, at.y, at.z, stack, 0.0, 0.0, 0.0));
    }

    static int countIn(Container container, Item item) {
        int n = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            n += container.getItem(i).is(item) ? container.getItem(i).getCount() : 0;
        }
        return n;
    }

    static void warpIntoChest(GameTestHelper helper) {
        BlockPos chest = new BlockPos(3, 1, 3);
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(chest.above(), ModBlocks.ITEM_WARP_RECEIVER.get());
        BlockPos sender = new BlockPos(3, 7, 3);
        helper.setBlock(sender, ModBlocks.ITEM_WARP_SENDER.get());
        dropOnto(helper, sender, new ItemStack(Items.DIAMOND, 5));

        helper.succeedWhen(() -> {
            Container box = helper.getBlockEntity(chest, ChestBlockEntity.class);
            check(helper, countIn(box, Items.DIAMOND) == 5, "chest should receive 5 diamonds, has " + countIn(box, Items.DIAMOND));
        });
    }

    static void warpDropsBelow(GameTestHelper helper) {
        BlockPos receiver = new BlockPos(3, 4, 3);
        helper.setBlock(receiver, ModBlocks.ITEM_WARP_RECEIVER.get());
        BlockPos sender = new BlockPos(3, 8, 3);
        helper.setBlock(sender, ModBlocks.ITEM_WARP_SENDER.get());
        dropOnto(helper, sender, new ItemStack(Items.BONE, 3));

        helper.succeedWhen(() -> {
            List<ItemEntity> bones = helper.getLevel().getEntities(EntityTypes.ITEM,
                    new AABB(helper.absolutePos(receiver.below())).inflate(0.5, 1.5, 0.5).move(0, -1, 0),
                    e -> e.getItem().is(Items.BONE));
            check(helper, bones.stream().mapToInt(e -> e.getItem().getCount()).sum() == 3, "3 bones should fall out below the receiver");
        });
    }

    static void warpFromHopper(GameTestHelper helper) {
        BlockPos chest = new BlockPos(3, 1, 3);
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(chest.above(), ModBlocks.ITEM_WARP_RECEIVER.get());
        BlockPos sender = new BlockPos(3, 6, 3);
        helper.setBlock(sender, ModBlocks.ITEM_WARP_SENDER.get());
        BlockPos hopper = sender.above();
        helper.setBlock(hopper, Blocks.HOPPER); // faces down into the sender by default
        helper.getBlockEntity(hopper, HopperBlockEntity.class)
                .setItem(0, new ItemStack(Items.IRON_INGOT, 3));

        helper.succeedWhen(() -> {
            Container box = helper.getBlockEntity(chest, ChestBlockEntity.class);
            check(helper, countIn(box, Items.IRON_INGOT) == 3, "chest should receive 3 iron from the hopper, has " + countIn(box, Items.IRON_INGOT));
        });
    }

    static void warpWaits(GameTestHelper helper) {
        BlockPos sender = new BlockPos(3, 2, 3);
        helper.setBlock(sender, ModBlocks.ITEM_WARP_SENDER.get());
        dropOnto(helper, sender, new ItemStack(Items.EMERALD, 2));

        helper.succeedWhen(() -> {
            ItemStack held = helper.getBlockEntity(sender, ItemWarpSenderBlockEntity.class).getItem(0);
            check(helper, held.is(Items.EMERALD) && held.getCount() == 2, "sender should hold the emeralds while no receiver exists");
            helper.assertItemEntityNotPresent(Items.EMERALD, sender.above(), 2.0);
        });
    }

    // ---- Magnet ----
    /**
     * Experience comes to a magnet from as far as items do - an Advanced Magnet's 16 blocks, past the 8 vanilla's orbs
     * come from by themselves (the user asked for it, 2026-10-05; it was so already, and is held so here).
     */
    static void magnetPullsOrbs(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.snapTo(helper.absoluteVec(new Vec3(0.5, 1, 0.5)));
        ItemStack magnet = new ItemStack(ModItems.ADVANCED_MAGNET.get());
        magnet.set(ModDataComponents.MAGNET_ACTIVE.get(), true);
        player.getInventory().setItem(9, magnet);
        // across the arena from corner to corner, high up: 11.7 blocks off, past vanilla's 8, in the player's sight
        Vec3 far = helper.absoluteVec(new Vec3(6.5, 9, 6.5));
        var orb = new net.minecraft.world.entity.ExperienceOrb(helper.getLevel(), far.x, far.y, far.z, 7);
        orb.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(orb);
        check(helper, MagnetItem.activeRange(player) == 16, "an Advanced Magnet on reaches 16 blocks");
        MagnetItem.pull(player, MagnetItem.activeRange(player));
        Vec3 toPlayer = player.position().subtract(orb.position()).normalize();
        check(helper, orb.getDeltaMovement().dot(toPlayer) > 0.3,
                "an orb " + String.format("%.1f", orb.distanceTo(player)) + " blocks off is pulled to the player: " + orb.getDeltaMovement());
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /** With two magnets in reach, a thing goes to the nearer player, not to whoever's magnet acts last (r7 multiplayer). */
    static void magnetLeavesToANearerMagnet(GameTestHelper helper) {
        ServerPlayer near = MockPlayers.inLevel(helper);
        ServerPlayer far = MockPlayers.inLevel(helper);
        near.snapTo(helper.absoluteVec(new Vec3(1.5, 1, 3.5)));
        far.snapTo(helper.absoluteVec(new Vec3(5.5, 1, 3.5)));
        for (ServerPlayer player : List.of(near, far)) {
            ItemStack magnet = new ItemStack(ModItems.MAGNET.get());
            magnet.set(ModDataComponents.MAGNET_ACTIVE.get(), true);
            player.getInventory().setItem(9, magnet);
        }
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.2, 3.5));
        ItemEntity emerald = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.EMERALD));
        emerald.setNoPickUpDelay();
        emerald.setNoGravity(true);
        emerald.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(emerald);
        MagnetItem.pull(far, 8);
        check(helper, emerald.getDeltaMovement().lengthSqr() < 1.0E-6, "the far magnet leaves it: " + emerald.getDeltaMovement());
        MagnetItem.pull(near, 8);
        check(helper, emerald.getDeltaMovement().x < 0, "the near one takes it: " + emerald.getDeltaMovement());
        // a nearer magnet that cannot see it - a wall between - leaves it to the far one (r7 review: nobody pulled it)
        emerald.setDeltaMovement(Vec3.ZERO);
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(new BlockPos(2, y, 2), net.minecraft.world.level.block.Blocks.STONE);
            helper.setBlock(new BlockPos(2, y, 3), net.minecraft.world.level.block.Blocks.STONE);
            helper.setBlock(new BlockPos(2, y, 4), net.minecraft.world.level.block.Blocks.STONE);
        }
        near.snapTo(helper.absoluteVec(new Vec3(1.5, 1, 3.5)));
        Vec3 behind = helper.absoluteVec(new Vec3(3.4, 1.2, 3.5));
        emerald.snapTo(behind.x, behind.y, behind.z);
        check(helper, near.distanceToSqr(emerald) < far.distanceToSqr(emerald), "the walled-off magnet is the nearer");
        MagnetItem.pull(far, 8);
        check(helper, emerald.getDeltaMovement().x > 0, "the far one takes what the near one cannot see: " + emerald.getDeltaMovement());
        emerald.discard();
        helper.getLevel().getServer().getPlayerList().remove(near);
        helper.getLevel().getServer().getPlayerList().remove(far);
        helper.succeed();
    }

    static void magnetPulls(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.snapTo(helper.absoluteVec(new Vec3(1.5, 1, 1.5)));
        ItemStack magnet = new ItemStack(ModItems.MAGNET.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, magnet);
        check(helper, MagnetItem.activeRange(player) == 0, "a new magnet is off");
        magnet.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        check(helper, MagnetItem.activeRange(player) == 8, "right-click switches it on (range 8)");

        ItemEntity loose = new ItemEntity(helper.getLevel(), player.getX() + 4, player.getY(), player.getZ() + 4, new ItemStack(Items.APPLE), 0, 0, 0);
        ItemEntity fresh = new ItemEntity(helper.getLevel(), player.getX() + 4, player.getY(), player.getZ() - 1, new ItemStack(Items.BREAD), 0, 0, 0);
        fresh.setDefaultPickUpDelay();
        ItemEntity far = new ItemEntity(helper.getLevel(), player.getX() + 20, player.getY(), player.getZ(), new ItemStack(Items.STICK), 0, 0, 0);
        helper.getLevel().addFreshEntity(loose);
        helper.getLevel().addFreshEntity(fresh);
        helper.getLevel().addFreshEntity(far);
        MagnetItem.pull(player, 8);
        Vec3 toPlayer = player.position().subtract(loose.position());
        check(helper, loose.getDeltaMovement().dot(toPlayer) > 0, "a loose item is pulled towards the player");
        check(helper, fresh.getDeltaMovement().horizontalDistanceSqr() < 1.0E-6, "an item still in its pickup delay is left alone");
        check(helper, far.getDeltaMovement().horizontalDistanceSqr() < 1.0E-6, "items out of range are left alone");
        helper.succeed();
    }

    /** Overlapping QoL stands down when the conflicting mod is loaded, unless the pack turns that off. */
    static void conflictingModsStandDown(GameTestHelper helper) {
        check(helper, FeatureGate.allowed(true, true, List.of()), "no conflicts: keep on");
        check(helper, !FeatureGate.allowed(true, true, List.of("minecraft")),
                "minecraft is always loaded, so a list that names it stands us down");
        check(helper, FeatureGate.allowed(true, false, List.of("minecraft")),
                "standDownIfConflicting false keeps the feature");
        check(helper, !FeatureGate.allowed(false, true, List.of()), "the master switch still wins");
        check(helper, !FeatureGate.anyLoaded(List.of("not_a_real_mod_id")), "unknown ids are ignored");
        check(helper, "minecraft".equals(FeatureGate.firstLoaded(List.of("not_a_real_mod_id", " ", "minecraft"))),
                "the log names the mod a feature stood down for");
        check(helper, FeatureGate.firstLoaded(List.of("not_a_real_mod_id")) == null, "and none when none is loaded");
        check(helper, FeatureGate.timber(), "this run has no tree-felling mods");
        helper.succeed();
    }

    // ---- Horizontal item warp ----
    static void placeLineWarp(GameTestHelper helper, Block block, BlockPos pos, Direction facing) {
        helper.setBlock(pos, block.defaultBlockState().setValue(LineWarpBlock.FACING, facing));
    }

    static void lineWarpSends(GameTestHelper helper) {
        placeLineWarp(helper, ModBlocks.LINE_WARP_SENDER.get(), new BlockPos(0, 1, 3), Direction.EAST);
        placeLineWarp(helper, ModBlocks.LINE_WARP_RECEIVER.get(), new BlockPos(5, 1, 3), Direction.EAST);
        helper.setBlock(new BlockPos(6, 1, 3), Blocks.CHEST);
        helper.getBlockEntity(new BlockPos(0, 1, 3), LineWarpSenderBlockEntity.class)
                .setItem(0, new ItemStack(Items.IRON_INGOT, 5));
        helper.succeedWhen(() -> {
            Container chest = helper.getBlockEntity(new BlockPos(6, 1, 3), ChestBlockEntity.class);
            check(helper, countIn(chest, Items.IRON_INGOT) == 5, "the chest in front of the receiver gets all 5");
        });
    }

    static void lineWarpIgnoresOffLine(GameTestHelper helper) {
        placeLineWarp(helper, ModBlocks.LINE_WARP_SENDER.get(), new BlockPos(0, 1, 3), Direction.EAST);
        placeLineWarp(helper, ModBlocks.LINE_WARP_RECEIVER.get(), new BlockPos(5, 2, 3), Direction.EAST); // one block higher
        placeLineWarp(helper, ModBlocks.LINE_WARP_RECEIVER.get(), new BlockPos(0, 1, 6), Direction.SOUTH); // to the side
        var sender = helper.getBlockEntity(new BlockPos(0, 1, 3), LineWarpSenderBlockEntity.class);
        sender.setItem(0, new ItemStack(Items.IRON_INGOT, 5));
        helper.runAfterDelay(20, () -> {
            check(helper, sender.getItem(0).getCount() == 5, "no receiver on the line: the items wait");
            helper.succeed();
        });
    }

    // ---- Magnet Chest ----
    static void magnetChestCollects(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.MAGNET_CHEST.get());
        var chest = helper.getBlockEntity(pos, MagnetChestBlockEntity.class);
        Vec3 near = helper.absoluteVec(new Vec3(0.5, 1.0, 0.5));
        ItemEntity drop = new ItemEntity(helper.getLevel(), near.x, near.y, near.z, new ItemStack(Items.BONE, 7), 0, 0, 0);
        drop.setNoPickUpDelay(); // a drop whose pickup delay has run out
        ItemEntity blocked = new ItemEntity(helper.getLevel(), near.x, near.y, near.z + 1, new ItemStack(Items.ARROW, 3), 0, 0, 0);
        blocked.getPersistentData().putBoolean("PreventRemoteMovement", true);
        helper.getLevel().addFreshEntity(drop);
        helper.getLevel().addFreshEntity(blocked);
        chest.collect(helper.getLevel());
        check(helper, countIn(chest, Items.BONE) == 7, "the 7 bones are pulled in");
        // A drop still inside its pickup delay is left alone, so mining beside the chest does not
        // feed it your own drops before you can reach them.
        ItemEntity fresh = new ItemEntity(helper.getLevel(), near.x, near.y, near.z, new ItemStack(Items.FLINT, 2), 0, 0, 0);
        fresh.setDefaultPickUpDelay();
        helper.getLevel().addFreshEntity(fresh);
        chest.collect(helper.getLevel());
        check(helper, countIn(chest, Items.FLINT) == 0 && fresh.isAlive(), "a just-dropped item is left for the player");
        check(helper, drop.isRemoved(), "the item entity is gone");
        check(helper, countIn(chest, Items.ARROW) == 0 && !blocked.isRemoved(), "opted-out items stay on the ground");
        helper.succeed();
    }

    private TransportTests() {}
}
