package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.NookworksConfig;
import dev.nookworks.feature.Permission;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The r7 review's findings on the Building Wand, the Wrench, Soap, vein mining's last point, the sneaking empty hand,
 * and asking claim mods without freeing a villager's bed: each test failed on the code before its fix.
 */
final class R7WandItemsTests {
    private R7WandItemsTests() {}

    static void register() {
        NookworksGameTests.test("building_wand_leaves_powder_snow_buckets_alone", R7WandItemsTests::wandLeavesPowderSnowBuckets);
        NookworksGameTests.test("building_wand_sets_no_double_slab_in_water", R7WandItemsTests::wandSetsNoDoubleSlabInWater);
        NookworksGameTests.test("vein_mining_keeps_the_last_point_for_the_ore_clicked", R7WandItemsTests::veinKeepsTheLastPoint);
        NookworksGameTests.test("building_wand_places_against_the_row_it_builds_on", R7WandItemsTests::wandPlacesAgainstTheRow);
        NookworksGameTests.test("wrench_asks_for_the_joined_pipe_against_the_clicked_one", R7WandItemsTests::wrenchAsksAgainstTheClickedPipe);
        NookworksGameTests.test("sneaking_empty_hand_leaves_a_refused_use_refused", R7WandItemsTests::sneakLeavesARefusalStanding);
        NookworksGameTests.test("soap_on_a_bed_foot_keeps_the_villagers_claim_on_the_bed", R7WandItemsTests::soapKeepsTheBedClaimed);
        NookworksGameTests.test("asking_to_place_keeps_a_claimed_job_site_taken", R7WandItemsTests::askingKeepsAJobSiteTaken);
    }

    /** A player standing clear of the arena's blocks, holding {@code held}. */
    private static ServerPlayer holding(GameTestHelper helper, GameType mode, ItemStack held) {
        ServerPlayer player = MockPlayers.inLevel(helper, mode);
        player.snapTo(helper.absoluteVec(new Vec3(0.5, 8, 0.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        return player;
    }

    /** Uses the wand in hand on the top of {@code pos}. */
    private static void wandOnTop(GameTestHelper helper, Player player, BlockPos pos) {
        BlockPos clicked = helper.absolutePos(pos);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(clicked).add(0, 0.5, 0), Direction.UP, clicked, false)));
    }

    private static int count(Player player, Item item) {
        return player.getInventory().getNonEquipmentItems().stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    /**
     * A bucket of powder snow is its block's item, but placing one by hand gives the empty bucket back. The wand took a
     * whole bucket for each block it set down, and gave nothing back: it leaves powder snow alone.
     */
    private static void wandLeavesPowderSnowBuckets(GameTestHelper helper) {
        for (int x = 1; x <= 3; x++) {
            helper.setBlock(new BlockPos(x, 1, 2), Blocks.POWDER_SNOW);
        }
        ServerPlayer player = holding(helper, GameType.SURVIVAL, new ItemStack(ModItems.BUILDING_WAND.get()));
        for (int slot = 5; slot <= 7; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.POWDER_SNOW_BUCKET));
        }
        wandOnTop(helper, player, new BlockPos(1, 1, 2));
        int full = count(player, Items.POWDER_SNOW_BUCKET);
        int empty = count(player, Items.BUCKET);
        check(helper, full + empty == 3, "no bucket lost: " + full + " of powder snow and " + empty + " empty left of 3");
        helper.succeed();
    }

    /** A double slab never holds water, as by hand: built in water by the wand, it kept a source hidden in it. */
    private static void wandSetsNoDoubleSlabInWater(GameTestHelper helper) {
        BlockState slab = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
        for (int x = 1; x <= 3; x++) {
            helper.setBlock(new BlockPos(x, 1, 2), slab);
            helper.setBlock(new BlockPos(x, 2, 2), Blocks.WATER);
        }
        ServerPlayer player = holding(helper, GameType.CREATIVE, new ItemStack(ModItems.BUILDING_WAND.get()));
        wandOnTop(helper, player, new BlockPos(1, 1, 2));
        for (int x = 1; x <= 3; x++) {
            BlockState built = helper.getBlockState(new BlockPos(x, 2, 2));
            check(helper, built.is(Blocks.STONE_SLAB) && built.getValue(SlabBlock.TYPE) == SlabType.DOUBLE,
                    "a double slab built in the water at x " + x + ": " + built);
            check(helper, !built.getValue(BlockStateProperties.WATERLOGGED), "with no water kept in it at x " + x);
        }
        helper.succeed();
    }

    /**
     * With protectTools off, a pickaxe broken on an extra ore left the ore clicked to an empty hand, and it gave nothing.
     * The last point is the clicked ore's: it drops its coal, the pickaxe breaks on it as by hand, and the vein stops short.
     */
    private static void veinKeepsTheLastPoint(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 3);
        BlockPos extra = new BlockPos(2, 1, 3);
        helper.setBlock(origin, Blocks.COAL_ORE);
        helper.setBlock(extra, Blocks.COAL_ORE);
        ItemStack pickaxe = new ItemStack(Items.STONE_PICKAXE);
        pickaxe.setDamageValue(pickaxe.getMaxDamage() - 1);
        ServerPlayer player = MiningTests.playerWith(helper, pickaxe);
        // Put back before anything else runs: nothing is written to disk, and no other test sees it.
        var protect = NookworksConfig.PROTECT_TOOLS;
        boolean was = protect.getAsBoolean();
        try {
            protect.set(false);
            protect.clearCache();
            player.gameMode.destroyBlock(helper.absolutePos(origin));
        } finally {
            protect.set(was);
            protect.clearCache();
        }
        helper.assertBlockNotPresent(Blocks.COAL_ORE, origin);
        helper.assertItemEntityPresent(Items.COAL, origin, 0.1); // its own drop, in its own cell
        helper.assertBlockPresent(Blocks.COAL_ORE, extra);
        check(helper, player.getMainHandItem().isEmpty(), "the pickaxe broke on the ore clicked");
        helper.succeed();
    }

    // ---- What the place event says a block is placed against ----

    private static volatile Player placer;
    private static final List<BlockState> placedAgainst = new ArrayList<>();
    private static boolean listening;

    /** From now until {@link #stopHearing()}, what each place event of {@code player}'s says it is placed against. */
    private static void hear(Player player) {
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (BlockEvent.EntityPlaceEvent event) -> {
                if (placer != null && event.getEntity() == placer) {
                    placedAgainst.add(event.getPlacedAgainst());
                }
            });
        }
        placedAgainst.clear();
        placer = player;
    }

    private static List<BlockState> stopHearing() {
        placer = null;
        List<BlockState> heard = List.copyOf(placedAgainst);
        placedAgainst.clear();
        return heard;
    }

    /** Each block of the wand's row is placed against the block in the row it stands on, as a hand would place it. */
    private static void wandPlacesAgainstTheRow(GameTestHelper helper) {
        for (int x = 1; x <= 3; x++) {
            helper.setBlock(new BlockPos(x, 1, 2), Blocks.SMOOTH_STONE);
        }
        ServerPlayer player = holding(helper, GameType.CREATIVE, new ItemStack(ModItems.BUILDING_WAND.get()));
        List<BlockState> heard;
        hear(player);
        try {
            wandOnTop(helper, player, new BlockPos(1, 1, 2));
        } finally {
            heard = stopHearing();
        }
        check(helper, helper.getBlockState(new BlockPos(3, 2, 2)).is(Blocks.SMOOTH_STONE), "the wand built the row");
        check(helper, !heard.isEmpty() && heard.stream().allMatch(s -> s.is(Blocks.SMOOTH_STONE)),
                "each placed against the smooth stone below it: " + heard);
        helper.succeed();
    }

    /** The pipe switched with the one clicked is asked about as placed against the clicked one, not the cell beyond it. */
    private static void wrenchAsksAgainstTheClickedPipe(GameTestHelper helper) {
        BlockPos first = new BlockPos(2, 2, 2);
        BlockPos second = new BlockPos(3, 2, 2);
        helper.setBlock(first, ModBlocks.ENERGY_CABLE.get());
        helper.setBlock(second, ModBlocks.ENERGY_CABLE.get());
        ItemStack wrench = new ItemStack(ModItems.WRENCH.get());
        ServerPlayer player = holding(helper, GameType.SURVIVAL, wrench);
        player.setShiftKeyDown(true);
        Vec3 onArm = helper.absoluteVec(Vec3.atCenterOf(first).add(0.4, 0.2, 0)); // the first cable's east arm
        List<BlockState> heard;
        hear(player);
        try {
            wrench.onItemUseFirst(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(onArm, Direction.UP, helper.absolutePos(first), false)));
        } finally {
            heard = stopHearing();
        }
        check(helper, heard.size() == 1 && heard.getFirst().is(ModBlocks.ENERGY_CABLE.get()),
                "the second cable asked about as placed against the first: " + heard);
        helper.succeed();
    }

    // ---- The sneaking empty hand ----

    private static volatile Player refused;
    private static boolean refusing;

    /**
     * A claim mod may refuse a block's use by setting it to FALSE rather than cancelling the click. The sneaking empty
     * hand that gives the kitchen's things back set it to TRUE again, and a visitor took the food out of the pot.
     */
    private static void sneakLeavesARefusalStanding(GameTestHelper helper) {
        BlockPos pot = new BlockPos(2, 1, 2);
        helper.setBlock(pot, ModCooking.IRON_POT.get());
        ServerPlayer player = holding(helper, GameType.SURVIVAL, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        BlockPos at = helper.absolutePos(pot);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at).add(0, 0.5, 0), Direction.UP, at, false);
        check(helper, CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, at, hit).getUseBlock().isTrue(),
                "a sneaking empty hand is let use the pot");
        if (!refusing) {
            refusing = true;
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, (PlayerInteractEvent.RightClickBlock event) -> {
                if (refused != null && event.getEntity() == refused) {
                    event.setUseBlock(TriState.FALSE);
                }
            });
        }
        TriState use;
        refused = player;
        try {
            use = CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, at, hit).getUseBlock();
        } finally {
            refused = null;
        }
        check(helper, use.isFalse(), "a claim mod's no to the pot's use stands: " + use);
        helper.succeed();
    }

    // ---- Asking claim mods, and villagers' beds and job sites ----

    /** Whether the point of interest of {@code type} at {@code pos} has room for another villager. */
    private static boolean free(ServerLevel level, BlockPos pos, ResourceKey<PoiType> type) {
        return level.getPoiManager().getInRange(t -> t.is(type), pos, 1, PoiManager.Occupancy.HAS_SPACE)
                .anyMatch(record -> record.getPos().equals(pos));
    }

    /** A villager's claim on {@code pos}, as one takes its bed or job site. */
    private static boolean claim(ServerLevel level, BlockPos pos, ResourceKey<PoiType> type) {
        return level.getPoiManager().take(t -> t.is(type), (t, at) -> at.equals(pos), pos, 1).isPresent();
    }

    /**
     * Soap clicked on a bed's foot asks claim mods about the head. The asking took the head away and put it back, and
     * the bed came back free: a second villager, or a newborn, could claim a bed a villager slept in.
     */
    private static void soapKeepsTheBedClaimed(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos foot = new BlockPos(2, 1, 1);
        BlockPos head = foot.south();
        BlockState red = Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
        helper.setBlock(foot, red.setValue(BedBlock.PART, BedPart.FOOT));
        helper.setBlock(head, red.setValue(BedBlock.PART, BedPart.HEAD));
        BlockPos home = helper.absolutePos(head);
        check(helper, claim(level, home, PoiTypes.HOME), "a villager claims the bed");
        ItemStack soap = new ItemStack(ModOil.SOAP.get());
        ServerPlayer player = holding(helper, GameType.SURVIVAL, soap);
        BlockPos at = helper.absolutePos(foot);
        soap.onItemUseFirst(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at).add(0, 0.06, 0), Direction.UP, at, false)));
        check(helper, helper.getBlockState(head).is(Blocks.BED.pick(DyeColor.WHITE)), "the bed washed white from its foot: "
                + helper.getBlockState(head));
        stillTaken(helper, home, PoiTypes.HOME, "and still the villager's: no other may claim it");
    }

    /** Checked a tick on, when anything the level put off for its points of interest has run. */
    private static void stillTaken(GameTestHelper helper, BlockPos pos, ResourceKey<PoiType> type, String message) {
        helper.runAfterDelay(1, () -> {
            check(helper, !free(helper.getLevel(), pos, type), message);
            helper.succeed();
        });
    }

    /**
     * A Trading Counter asks about the cell its villager stands in, now and then; a composter there is a farmer's job
     * site. Asked about, it came back free, and another villager could take the farmer's job site.
     */
    private static void askingKeepsAJobSiteTaken(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos composter = new BlockPos(2, 1, 2);
        helper.setBlock(composter, Blocks.COMPOSTER);
        BlockPos at = helper.absolutePos(composter);
        check(helper, claim(level, at, PoiTypes.FARMER), "a farmer takes the composter");
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        check(helper, Permission.mayPlace(level, at, Direction.UP, player), "nothing refuses here");
        check(helper, helper.getBlockState(composter).is(Blocks.COMPOSTER), "the composter is put back");
        stillTaken(helper, at, PoiTypes.FARMER, "and still the farmer's: no other villager may take it");
    }
}
