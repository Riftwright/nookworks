package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;



import dev.nookworks.block.PlanterBlock;
import dev.nookworks.drive.BeltKind;
import dev.nookworks.drive.Belts;
import dev.nookworks.drive.DriveWheelBlock;
import dev.nookworks.drive.DriveWheelBlockEntity;
import dev.nookworks.experience.AbsorptionPadBlockEntity;
import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.experience.Move;
import dev.nookworks.feature.Permission;
import dev.nookworks.fluid.CopperFittingBlock;
import dev.nookworks.fluid.SightGlassBlockEntity;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.PlanterBlockEntity;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * r16's fix check (2026-10-14), its probes kept as tests: belts asked along their path, pipes and the Absorption Pad
 * saved without setChanged (the Sight Glass still telling its comparator), the Planter keeping only a claim's refusals,
 * forgetting them on a new owner, and its memory running out and staying small. A stand-in claim mod over a set of cells
 * (only the players allowed may place there); every place event our asking posts is counted where the probe looks.
 */
final class R16FixcheckTests {

    private R16FixcheckTests() {}

    static void register() {
        test("r16_belt_open_land_friend_stranger", R16FixcheckTests::beltOpenFriendStranger);
        test("r16_belt_through_water_lava_torch_crop_sign", R16FixcheckTests::beltThroughThings);
        test("r16_belt_tied_by_a_fake_player", R16FixcheckTests::beltFakePlayer);
        test("r16_belt_in_the_nether", 200, R16FixcheckTests::beltNether);
        test("r16_pad_drawn_from_is_saved", R16FixcheckTests::padSaved);
        test("r16_sight_glass_still_tells_its_comparator", 120, R16FixcheckTests::sightGlassComparator);
        test("r16_planter_remembers_only_claim_refusals", R16FixcheckTests::planterOnlyClaims);
        test("r16_planter_forgets_on_owner_change", R16FixcheckTests::planterOwnerChange);
        test("r16_planter_memory_expires_and_stays_small", R16FixcheckTests::planterExpiry);
    }

    // ---- helpers ----

    /** A stand-in claim over {@code inside}: only {@code allowed} may place there. Counts the placings asked there. */
    private static int[] claim(GameTestHelper helper, Predicate<BlockPos> inside, Set<UUID> allowed) {
        int[] asked = {0};
        Consumer<BlockEvent.EntityPlaceEvent> stand = event -> {
            if (inside.test(event.getPos())) {
                asked[0]++;
                if (event.getEntity() == null || !allowed.contains(event.getEntity().getUUID())) {
                    event.setCanceled(true);
                }
            }
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, stand);
        AfterTest.undo(helper, () -> NeoForge.EVENT_BUS.unregister(stand));
        return asked;
    }

    /** Counts the place events our asking posts in {@code level} inside {@code inside}, nothing refused. */
    private static int[] counter(GameTestHelper helper, Level level, Predicate<BlockPos> inside) {
        int[] asked = {0};
        Consumer<BlockEvent.EntityPlaceEvent> count = event -> {
            if (Permission.asking() && event.getLevel() == level && inside.test(event.getPos())) {
                asked[0]++;
            }
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, count);
        AfterTest.undo(helper, () -> NeoForge.EVENT_BUS.unregister(count));
        return asked;
    }

    private static Predicate<BlockPos> arena(GameTestHelper helper, int height) {
        BlockPos lo = helper.absolutePos(BlockPos.ZERO);
        return p -> p.getX() >= lo.getX() - 1 && p.getX() <= lo.getX() + 8 && p.getZ() >= lo.getZ() - 1 && p.getZ() <= lo.getZ() + 8
                && p.getY() >= lo.getY() - 1 && p.getY() <= lo.getY() + height;
    }

    private static DriveWheelBlockEntity wheel(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModOil.DRIVE_WHEEL.get().defaultBlockState().setValue(DriveWheelBlock.FACING, Direction.NORTH));
        return helper.getBlockEntity(pos, DriveWheelBlockEntity.class);
    }

    private static void use(GameTestHelper helper, Player player, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(helper.absoluteVec(Vec3.atCenterOf(pos)), Direction.UP, helper.absolutePos(pos), false);
        helper.getBlockState(pos).useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }

    private static boolean tied(GameTestHelper helper, DriveWheelBlockEntity a, BlockPos bPos) {
        return a.has(helper.absolutePos(bPos));
    }

    // ---- belts ----

    /**
     * Open land: a belt ties, asked of each cell once. A friend the claim trusts ties through it; a stranger does not,
     * and pays nothing.
     */
    private static void beltOpenFriendStranger(GameTestHelper helper) {
        Item rope = ModItems.ROPE.get();
        ServerPlayer owner = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        ServerPlayer friend = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        ServerPlayer stranger = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        for (ServerPlayer p : new ServerPlayer[] {owner, friend, stranger}) {
            p.snapTo(helper.absoluteVec(new Vec3(3.5, 2, 6.5)));
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(rope, 20));
        }
        int[] all = counter(helper, helper.getLevel(), arena(helper, 12));

        // open land, a diagonal belt: tied, each cell it runs through asked once
        DriveWheelBlockEntity a = wheel(helper, new BlockPos(1, 2, 1));
        wheel(helper, new BlockPos(5, 7, 1));
        use(helper, stranger, new BlockPos(1, 2, 1));
        use(helper, stranger, new BlockPos(5, 7, 1));
        check(helper, tied(helper, a, new BlockPos(5, 7, 1)), "open land: the diagonal belt is tied");
        int openAsks = all[0];
        check(helper, openAsks > 0 && openAsks < 40, "asked once a cell: " + openAsks);

        // a claim over the rows z=3 and z=5, trusting the owner and the friend
        int lo = helper.absolutePos(BlockPos.ZERO).getZ();
        int[] claimAsks = claim(helper, p -> arena(helper, 12).test(p) && (p.getZ() == lo + 3 || p.getZ() == lo + 5),
                Set.of(owner.getUUID(), friend.getUUID()));
        DriveWheelBlockEntity c = wheel(helper, new BlockPos(1, 2, 3));
        wheel(helper, new BlockPos(5, 2, 3));
        use(helper, friend, new BlockPos(1, 2, 3));
        use(helper, friend, new BlockPos(5, 2, 3));
        check(helper, tied(helper, c, new BlockPos(5, 2, 3)), "the friend the claim trusts ties through it");
        check(helper, friend.getInventory().countItem(rope) == 12, "paying eight: " + friend.getInventory().countItem(rope));

        DriveWheelBlockEntity e = wheel(helper, new BlockPos(1, 2, 5));
        DriveWheelBlockEntity f = wheel(helper, new BlockPos(5, 2, 5));
        int before = claimAsks[0];
        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(rope, 20));
        int had = stranger.getInventory().countItem(rope);
        use(helper, stranger, new BlockPos(1, 2, 5));
        use(helper, stranger, new BlockPos(5, 2, 5));
        check(helper, e.belts().isEmpty() && f.belts().isEmpty(), "the stranger's is not tied through the claim");
        check(helper, stranger.getInventory().countItem(rope) == had, "and nothing more taken: " + stranger.getInventory().countItem(rope));
        helper.succeed();
    }

    /**
     * A belt through water, lava, a torch, a crop and a sign: tied (none is in the way), and each cell is as it was
     * the same tick - fluid source, crop age, the sign's text.
     */
    private static void beltThroughThings(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new Vec3(3.5, 2, 6.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROPE.get(), 40));
        // z=1: water, lava, torch, wheat
        helper.setBlock(new BlockPos(3, 2, 1), Blocks.STONE); // (water and lava apart: side by side, the lava is obsidian at once)
        helper.setBlock(new BlockPos(5, 2, 1), Blocks.FARMLAND);
        helper.setBlock(new BlockPos(2, 3, 1), Blocks.WATER);
        helper.setBlock(new BlockPos(4, 3, 1), Blocks.LAVA);
        helper.setBlock(new BlockPos(3, 3, 1), Blocks.TORCH);
        helper.setBlock(new BlockPos(5, 3, 1), Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 5));
        // z=4: a sign with words on it
        helper.setBlock(new BlockPos(3, 2, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 3, 4), Blocks.OAK_SIGN);
        SignBlockEntity sign = helper.getBlockEntity(new BlockPos(3, 3, 4), SignBlockEntity.class);
        java.util.List<Component> lines = java.util.List.of(Component.literal("keep me"), Component.empty(), Component.empty(), Component.empty());
        sign.setText(new SignText(lines, lines, net.minecraft.world.item.DyeColor.BLACK, false), SignTextSlot.FRONT);
        Map<BlockPos, BlockState> was = new HashMap<>();
        for (BlockPos p : new BlockPos[] {new BlockPos(2, 3, 1), new BlockPos(3, 3, 1), new BlockPos(4, 3, 1), new BlockPos(5, 3, 1), new BlockPos(3, 3, 4)}) {
            was.put(p, helper.getBlockState(p));
        }
        DriveWheelBlockEntity a = wheel(helper, new BlockPos(1, 3, 1));
        wheel(helper, new BlockPos(6, 3, 1));
        DriveWheelBlockEntity c = wheel(helper, new BlockPos(1, 3, 4));
        wheel(helper, new BlockPos(6, 3, 4));
        Component why = Belts.tie(helper.getLevel(), a, helper.getBlockEntity(new BlockPos(6, 3, 1), DriveWheelBlockEntity.class),
                BeltKind.ROPE, player, player.getMainHandItem());
        BlockPos in = Belts.blocker(helper.getLevel(), helper.absolutePos(new BlockPos(1, 3, 1)), helper.absolutePos(new BlockPos(6, 3, 1)), Direction.Axis.Z);
        use(helper, player, new BlockPos(1, 3, 4));
        use(helper, player, new BlockPos(6, 3, 4));
        check(helper, tied(helper, a, new BlockPos(6, 3, 1)), "tied through water, lava, a torch and a crop");
        check(helper, tied(helper, c, new BlockPos(6, 3, 4)), "tied through a sign");
        was.forEach((p, s) -> check(helper, helper.getBlockState(p).equals(s), "left as it was at " + p + ": " + helper.getBlockState(p) + " was " + s));
        check(helper, helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(2, 3, 1))).isSource(), "the water a source still");
        check(helper, helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(4, 3, 1))).isSource(), "the lava a source still");
        SignBlockEntity after = helper.getBlockEntity(new BlockPos(3, 3, 4), SignBlockEntity.class);
        check(helper, after.getText(SignTextSlot.FRONT).getMessages(false).get(0).getString().equals("keep me"), "the sign keeps its words: "
                + after.getText(SignTextSlot.FRONT).getMessages(false).get(0).getString());
        helper.succeed();
    }

    /** A fake player (a Deployer's, a machine's) ties as its own id: let where that id may build, refused elsewhere. */
    private static void beltFakePlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        FakePlayer mine = Permission.actor(level, owner);
        FakePlayer theirs = Permission.actor(level, UUID.randomUUID());
        Item rope = ModItems.ROPE.get();
        try {
            int lo = helper.absolutePos(BlockPos.ZERO).getZ();
            claim(helper, p -> arena(helper, 12).test(p) && p.getZ() <= lo + 6, Set.of(owner));
            mine.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(rope, 20));
            theirs.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(rope, 20));
            DriveWheelBlockEntity a = wheel(helper, new BlockPos(1, 2, 1));
            wheel(helper, new BlockPos(5, 2, 1));
            use(helper, mine, new BlockPos(1, 2, 1));
            use(helper, mine, new BlockPos(5, 2, 1));
            check(helper, tied(helper, a, new BlockPos(5, 2, 1)), "the owner's fake player ties in the owner's claim");
            DriveWheelBlockEntity c = wheel(helper, new BlockPos(1, 2, 4));
            wheel(helper, new BlockPos(5, 2, 4));
            use(helper, theirs, new BlockPos(1, 2, 4));
            use(helper, theirs, new BlockPos(5, 2, 4));
            check(helper, c.belts().isEmpty(), "a stranger's fake player does not");
            check(helper, theirs.getInventory().countItem(rope) == 20, "and pays nothing");
        } finally {
            mine.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            theirs.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        helper.succeed();
    }

    /** A belt in the Nether is asked there, of the Nether's cells, and tied. */
    private static void beltNether(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        BlockPos base = new BlockPos(helper.absolutePos(BlockPos.ZERO).getX(), 100, helper.absolutePos(BlockPos.ZERO).getZ());
        nether.getChunk(base);
        nether.getChunk(base.east(8));
        for (int x = 0; x <= 6; x++) {
            for (int y = -1; y <= 2; y++) {
                nether.setBlock(base.offset(x, y, 0), y == -1 ? Blocks.NETHERRACK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            }
        }
        BlockState wheelState = ModOil.DRIVE_WHEEL.get().defaultBlockState().setValue(DriveWheelBlock.FACING, Direction.NORTH);
        nether.setBlock(base.east(1), wheelState, 3);
        nether.setBlock(base.east(5), wheelState, 3);
        DriveWheelBlockEntity a = (DriveWheelBlockEntity) nether.getBlockEntity(base.east(1));
        DriveWheelBlockEntity b = (DriveWheelBlockEntity) nether.getBlockEntity(base.east(5));
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROPE.get(), 20));
        int[] asked = counter(helper, nether, p -> Math.abs(p.getX() - base.getX()) < 10 && Math.abs(p.getZ() - base.getZ()) < 10);
        Component why = Belts.tie(nether, a, b, BeltKind.ROPE, player, player.getMainHandItem());
        boolean ok = why == null && a.has(base.east(5)) && asked[0] > 0;
        nether.setBlock(base.east(1), Blocks.AIR.defaultBlockState(), 3);
        nether.setBlock(base.east(5), Blocks.AIR.defaultBlockState(), 3);
        check(helper, ok, "tied in the Nether, asked of its cells: " + (why == null ? "tied" : why.getString()) + ", asked " + asked[0]);
        helper.succeed();
    }

    // ---- pipes and the pad ----

    /** Experience drawn out of an Absorption Pad (as a pipe draws) still marks its chunk to be saved. */
    private static void padSaved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.ABSORPTION_PAD.get());
        AbsorptionPadBlockEntity pad = helper.getBlockEntity(new BlockPos(3, 2, 3), AbsorptionPadBlockEntity.class);
        try {
            Method add = AbsorptionPadBlockEntity.class.getDeclaredMethod("add", int.class);
            add.setAccessible(true);
            add.invoke(pad, 1000);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        LevelChunk chunk = level.getChunkAt(pad.getBlockPos());
        chunk.tryMarkSaved();
        int drawn = Move.extract(pad.fluidHandler(), FluidResource.of(ExperienceFluids.SOURCE.get()), 100);
        check(helper, drawn == 100 && pad.stored() == 900, "100 drawn: " + drawn + ", left " + pad.stored());
        check(helper, chunk.isUnsaved(), "and the pad's chunk is to be saved");
        helper.succeed();
    }

    /** A comparator reading a Sight Glass rises when liquid stands in it and falls when it is flushed. */
    private static void sightGlassComparator(GameTestHelper helper) {
        BlockPos glassPos = new BlockPos(2, 2, 2);
        BlockPos comparatorPos = new BlockPos(2, 2, 3);
        helper.setBlock(glassPos.below(), Blocks.STONE);
        helper.setBlock(comparatorPos.below(), Blocks.STONE);
        helper.setBlock(glassPos, ModBlocks.SIGHT_GLASS.get().defaultBlockState().setValue(CopperFittingBlock.AXIS, Direction.Axis.X));
        helper.setBlock(comparatorPos, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.NORTH));
        SightGlassBlockEntity glass = helper.getBlockEntity(glassPos, SightGlassBlockEntity.class);
        ComparatorBlockEntity comparator = helper.getBlockEntity(comparatorPos, ComparatorBlockEntity.class);
        Move.insert(glass.handlerFor(null), FluidResource.of(Fluids.WATER), 1_000);
        helper.startSequence()
                .thenWaitUntil(() -> check(helper, comparator.getOutputSignal() > 0, "the comparator reads the full glass: " + comparator.getOutputSignal()))
                .thenExecute(() -> {
                    glass.flush(helper.getLevel());
                })
                .thenWaitUntil(() -> check(helper, comparator.getOutputSignal() == 0, "flushed, it reads nothing: " + comparator.getOutputSignal()))
                .thenSucceed();
    }

    // ---- the planter ----

    private static PlanterBlockEntity planter(GameTestHelper helper, UUID owner, int seeds) {
        BlockPos pos = new BlockPos(3, 2, 1);
        helper.setBlock(pos, ModBlocks.PLANTER.get().defaultBlockState().setValue(PlanterBlock.FACING, Direction.SOUTH));
        PlanterBlockEntity planter = helper.getBlockEntity(pos, PlanterBlockEntity.class);
        planter.energy().set((int) planter.energy().getCapacityAsLong());
        if (seeds > 0) {
            planter.setItem(0, new ItemStack(Items.WHEAT_SEEDS, seeds));
        }
        planter.setOwner(owner);
        return planter;
    }

    @SuppressWarnings("unchecked")
    private static Map<BlockPos, Long> refusals(PlanterBlockEntity planter) {
        try {
            Field field = PlanterBlockEntity.class.getDeclaredField("refusedUntil");
            field.setAccessible(true);
            return (Map<BlockPos, Long>) field.get(planter);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The planter's field, 3 wide and 5 deep south of (3, 2, 1): farmland under every spot. */
    private static void field(GameTestHelper helper) {
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.FARMLAND);
            }
        }
    }

    /**
     * Spots it cannot plant for other reasons - taken, no soil for the seed, no seeds left - are not remembered as
     * refused, and its status is not "protected".
     */
    private static void planterOnlyClaims(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE); // no soil for wheat
                helper.setBlock(new BlockPos(x, 2, z), z == 2 ? Blocks.COBBLESTONE : Blocks.AIR); // the first row taken
            }
        }
        helper.setBlock(new BlockPos(4, 1, 6), Blocks.FARMLAND); // one spot it can plant, last in its search
        PlanterBlockEntity planter = planter(helper, UUID.randomUUID(), 1);
        planter.work(level);
        check(helper, planter.guiStatus() == MachineStatus.RUNNING && helper.getBlockState(new BlockPos(4, 2, 6)).is(Blocks.WHEAT),
                "it plants the one spot it can: " + planter.guiStatus());
        check(helper, refusals(planter).isEmpty(), "nothing remembered as refused: " + refusals(planter));
        planter.work(level);
        check(helper, planter.guiStatus() != MachineStatus.PROTECTED && refusals(planter).isEmpty(),
                "no seeds: not protected, nothing remembered: " + planter.guiStatus() + " " + refusals(planter));
        planter.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 4));
        planter.work(level);
        check(helper, planter.guiStatus() == MachineStatus.NO_SPACE && refusals(planter).isEmpty(),
                "seeds and no place for them: no space, nothing remembered: " + planter.guiStatus() + " " + refusals(planter));
        helper.succeed();
    }

    /** Refused under A, the same planter given to B (whom the claim lets in) plants at once. */
    private static void planterOwnerChange(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        field(helper);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        int lo = helper.absolutePos(BlockPos.ZERO).getZ();
        int[] asked = claim(helper, p -> arena(helper, 4).test(p) && p.getZ() >= lo + 2, Set.of(b));
        PlanterBlockEntity planter = planter(helper, a, 4);
        planter.work(level);
        int first = asked[0];
        planter.work(level);
        planter.work(level);
        check(helper, planter.guiStatus() == MachineStatus.PROTECTED && first == 15 && asked[0] == 15,
                "under A: 15 spots asked once, then not again, protected: " + planter.guiStatus() + " asked " + first + "/" + asked[0]);
        check(helper, refusals(planter).size() == 15, "15 refusals kept: " + refusals(planter).size());
        planter.setOwner(b);
        planter.work(level);
        check(helper, planter.guiStatus() == MachineStatus.RUNNING && planter.getItem(0).getCount() == 3,
                "given to B, it plants at once: " + planter.guiStatus() + " seeds " + planter.getItem(0).getCount());
        helper.succeed();
    }

    /** The refusals run out and are asked again; the map never holds more than the field's 15 spots. */
    private static void planterExpiry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        field(helper);
        int lo = helper.absolutePos(BlockPos.ZERO).getZ();
        int[] asked = claim(helper, p -> arena(helper, 4).test(p) && p.getZ() >= lo + 2, Set.of());
        PlanterBlockEntity planter = planter(helper, UUID.randomUUID(), 4);
        planter.work(level);
        Map<BlockPos, Long> kept = refusals(planter);
        long now = level.getGameTime();
        long min = kept.values().stream().mapToLong(Long::longValue).min().orElse(0) - now;
        long max = kept.values().stream().mapToLong(Long::longValue).max().orElse(0) - now;
        check(helper, min >= 4800 && max <= 6000, "kept four to five minutes: " + min + ".." + max);
        for (int round = 0; round < 5; round++) {
            kept.replaceAll((k, v) -> now); // run out
            planter.work(level);
            check(helper, kept.size() <= 15, "no more than the field's spots: " + kept.size());
        }
        check(helper, asked[0] == 15 * 6, "each run out, each spot asked again once: " + asked[0]);
        helper.succeed();
    }

}
