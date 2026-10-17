package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.PlanterBlock;
import dev.nookworks.drive.BeltKind;
import dev.nookworks.drive.Belts;
import dev.nookworks.drive.DriveWheelBlock;
import dev.nookworks.drive.DriveWheelBlockEntity;
import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.experience.ExperiencePipeBlockEntity;
import dev.nookworks.experience.Move;
import dev.nookworks.fluid.CopperPipeBlockEntity;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.PlanterBlockEntity;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * r16's code review (2026-10-14): a drive belt is tied only where the player tying it may build all along it (S1); a started belt
 * whose first wheel is away does not load it (N1); a pipe's fluid is saved without the comparator updates (S3); a
 * Planter refused by a claim does not ask again every beat (N3).
 */
final class R16FixTests {
    private R16FixTests() {}

    static void register() {
        test("a_belt_is_not_tied_through_land_the_player_may_not_build_in", R16FixTests::beltAcrossAClaim);
        test("a_belt_started_on_a_wheel_now_away_is_forgotten_not_loaded", R16FixTests::startedWheelAway);
        test("a_pipes_fluid_is_still_saved", R16FixTests::pipeSaved);
        test("a_planter_refused_by_a_claim_does_not_ask_again_each_beat", R16FixTests::planterBacksOff);
    }

    /**
     * A stand-in claim mod over the blocks {@code inside} (absolute): only {@code owner} may put a block down there. It
     * counts every placing asked of it there, let or not. Taken away when the test ends.
     */
    private static int[] claim(GameTestHelper helper, Set<BlockPos> inside, UUID owner) {
        int[] asked = {0};
        Consumer<BlockEvent.EntityPlaceEvent> stand = event -> {
            if (inside.contains(event.getPos())) {
                asked[0]++;
                if (event.getEntity() == null || !owner.equals(event.getEntity().getUUID())) {
                    event.setCanceled(true);
                }
            }
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, stand);
        AfterTest.undo(helper, () -> NeoForge.EVENT_BUS.unregister(stand));
        return asked;
    }

    private static DriveWheelBlockEntity wheel(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModOil.DRIVE_WHEEL.get().defaultBlockState().setValue(DriveWheelBlock.FACING, Direction.NORTH));
        return helper.getBlockEntity(pos, DriveWheelBlockEntity.class);
    }

    private static BlockHitResult hit(GameTestHelper helper, BlockPos pos) {
        return new BlockHitResult(helper.absoluteVec(Vec3.atCenterOf(pos)), Direction.UP, helper.absolutePos(pos), false);
    }

    /** {@code player} uses what is in their hand on the wheel at {@code pos}, as a right click does. */
    private static void use(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        helper.getBlockState(pos).useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(helper, pos));
    }

    private static boolean refused(@Nullable Component why, String key) {
        return why != null && why.getContents() instanceof TranslatableContents t && t.getKey().equals("block.nookworks.drive_wheel." + key);
    }

    /**
     * A stranger's belt between two wheels outside a claim, run through a block of it, is not tied - nothing taken,
     * and the claimed block left as it was - for once tied, the claim's owner could set nothing down on its line (r16
     * S1). The claim's owner ties the same belt. Beside the claim, off its line, the stranger's belt is tied as before.
     */
    private static void beltAcrossAClaim(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item rope = ModItems.ROPE.get();
        DriveWheelBlockEntity a = wheel(helper, new BlockPos(1, 2, 1));
        DriveWheelBlockEntity b = wheel(helper, new BlockPos(5, 2, 1));
        BlockPos claimed = new BlockPos(3, 2, 1); // on the belt's line, between the wheels
        helper.setBlock(claimed.below(), Blocks.STONE);
        ServerPlayer owner = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        ServerPlayer stranger = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        for (ServerPlayer player : new ServerPlayer[] {owner, stranger}) {
            player.snapTo(helper.absoluteVec(new Vec3(3.5, 2, 2.5)));
        }
        int[] asked = claim(helper, Set.of(helper.absolutePos(claimed)), owner.getUUID());

        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(rope, 10));
        use(helper, stranger, new BlockPos(1, 2, 1));
        use(helper, stranger, new BlockPos(5, 2, 1));
        check(helper, asked[0] > 0, "the claim was asked about the block the belt runs through");
        check(helper, a.belts().isEmpty() && b.belts().isEmpty(), "a stranger's belt through the claim is not tied");
        check(helper, stranger.getInventory().countItem(rope) == 10, "and no rope taken: " + stranger.getInventory().countItem(rope));
        check(helper, helper.getBlockState(claimed).isAir(), "the claimed block is left as it was: " + helper.getBlockState(claimed));
        Component why = Belts.tie(level, a, b, BeltKind.ROPE, stranger, stranger.getMainHandItem());
        check(helper, refused(why, "not_here"), "it says it cannot be tied here: " + (why == null ? "tied" : why.getString()));

        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(rope, 10));
        use(helper, owner, new BlockPos(1, 2, 1));
        use(helper, owner, new BlockPos(5, 2, 1));
        check(helper, a.has(helper.absolutePos(new BlockPos(5, 2, 1))) && b.has(helper.absolutePos(new BlockPos(1, 2, 1))),
                "the claim's owner ties it");
        check(helper, owner.getInventory().countItem(rope) == 2, "paying eight: " + owner.getInventory().countItem(rope));

        DriveWheelBlockEntity c = wheel(helper, new BlockPos(1, 2, 4));
        DriveWheelBlockEntity d = wheel(helper, new BlockPos(5, 2, 4));
        int before = asked[0];
        use(helper, stranger, new BlockPos(1, 2, 4));
        use(helper, stranger, new BlockPos(5, 2, 4));
        check(helper, c.has(helper.absolutePos(new BlockPos(5, 2, 4))) && d.belts().size() == 1, "beside the claim, off its line, the stranger ties one");
        check(helper, stranger.getInventory().countItem(rope) == 2, "paying eight: " + stranger.getInventory().countItem(rope));
        check(helper, asked[0] == before, "and the claim is not asked about it");
        helper.succeed();
    }

    /**
     * A belt started on a wheel whose chunk has since unloaded, the player using another wheel far off: the started
     * belt is forgotten and this wheel starts one, and the first wheel's chunk is not loaded from disk for it (r16 N1).
     */
    private static void startedWheelAway(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DriveWheelBlockEntity a = wheel(helper, new BlockPos(1, 2, 1));
        DriveWheelBlockEntity b = wheel(helper, new BlockPos(5, 2, 1));
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new Vec3(3.5, 2, 2.5)));
        Item rope = ModItems.ROPE.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(rope, 10));
        BlockPos away = helper.absolutePos(new BlockPos(1, 2, 1)).offset(20_000, 0, 20_000);
        ChunkPos chunk = ChunkPos.containing(away);
        check(helper, !level.getChunkSource().hasChunk(chunk.x(), chunk.z()), "the far wheel's chunk is not loaded to begin with");
        started(player, level.dimension(), away, level.getGameTime());

        use(helper, player, new BlockPos(5, 2, 1));
        check(helper, !level.getChunkSource().hasChunk(chunk.x(), chunk.z()), "the far wheel's chunk was not loaded for the click");
        check(helper, b.belts().isEmpty() && player.getInventory().countItem(rope) == 10, "nothing tied, nothing taken");
        use(helper, player, new BlockPos(1, 2, 1));
        check(helper, a.has(helper.absolutePos(new BlockPos(5, 2, 1))) && b.has(helper.absolutePos(new BlockPos(1, 2, 1))),
                "the wheel used started a belt, and the next one ties it off");
        helper.succeed();
    }

    /** Sets a belt started by {@code player} at {@code pos}, as a click there would have (Belts keeps it to itself). */
    private static void started(Player player, ResourceKey<?> dimension, BlockPos pos, long tick) {
        try {
            Field field = Belts.class.getDeclaredField("STARTED");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Player, Object> started = (Map<Player, Object>) field.get(null);
            Class<?> record = Class.forName(Belts.class.getName() + "$Started");
            Constructor<?> make = record.getDeclaredConstructor(ResourceKey.class, BlockPos.class, long.class);
            make.setAccessible(true);
            started.put(player, make.newInstance(dimension, pos, tick));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Belts.STARTED is not as this test knows it", e);
        }
    }

    /**
     * Fluid going into a pipe - copper or experience - still marks its chunk to be saved, now that it no longer tells
     * its neighbours as a comparator's block does (r16 S3).
     */
    private static void pipeSaved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.COPPER_PIPE.get());
        helper.setBlock(new BlockPos(4, 2, 1), ModBlocks.EXPERIENCE_PIPE.get());
        CopperPipeBlockEntity copper = helper.getBlockEntity(new BlockPos(1, 2, 1), CopperPipeBlockEntity.class);
        ExperiencePipeBlockEntity experience = helper.getBlockEntity(new BlockPos(4, 2, 1), ExperiencePipeBlockEntity.class);

        LevelChunk chunk = level.getChunkAt(copper.getBlockPos());
        chunk.tryMarkSaved();
        int water = Move.insert(copper.handlerFor(null), FluidResource.of(Fluids.WATER), 100);
        check(helper, water == 100, "the copper pipe takes water: " + water);
        check(helper, chunk.isUnsaved(), "and its chunk is to be saved");

        chunk = level.getChunkAt(experience.getBlockPos());
        chunk.tryMarkSaved();
        int xp = Move.insert(experience.handlerFor(null), FluidResource.of(ExperienceFluids.SOURCE.get()), 100);
        check(helper, xp == 100, "the experience pipe takes experience: " + xp);
        check(helper, chunk.isUnsaved(), "and its chunk is to be saved");
        helper.succeed();
    }

    /**
     * A Planter facing a field whose first row is someone else's claim: the claim is asked about each spot there once,
     * and those refusals are kept - the next beats ask only of the spot they plant, in the open row behind - while it
     * goes on planting that row as before; with nothing left it may plant, it says the land is protected (r16 N3: it
     * asked of every refused spot every ten ticks, each asking a block set in the chunk and a place event).
     */
    private static void planterBacksOff(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(3, 2, 1);
        helper.setBlock(pos, ModBlocks.PLANTER.get().defaultBlockState().setValue(PlanterBlock.FACING, Direction.SOUTH));
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.FARMLAND);
            }
        }
        PlanterBlockEntity planter = helper.getBlockEntity(pos, PlanterBlockEntity.class);
        planter.energy().set((int) planter.energy().getCapacityAsLong());
        planter.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 10));
        UUID owner = UUID.randomUUID();
        planter.setOwner(owner);
        int[] asked = {0};
        Consumer<BlockEvent.EntityPlaceEvent> counting = event -> {
            if (event.getEntity() instanceof FakePlayer fake && owner.equals(fake.getUUID())) {
                asked[0]++;
            }
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, counting);
        AfterTest.undo(helper, () -> NeoForge.EVENT_BUS.unregister(counting));
        claim(helper, Set.of(helper.absolutePos(new BlockPos(2, 2, 2)), helper.absolutePos(new BlockPos(3, 2, 2)),
                helper.absolutePos(new BlockPos(4, 2, 2))), UUID.randomUUID());

        planter.work(level);
        check(helper, planter.guiStatus() == MachineStatus.RUNNING && asked[0] == 4,
                "first beat: the three claimed spots refused, the first open one asked and planted: " + planter.guiStatus() + ", asked " + asked[0]);
        planter.work(level);
        planter.work(level);
        check(helper, asked[0] == 6, "the next beats ask only of the spot each plants: asked " + asked[0]);
        for (int x = 2; x <= 4; x++) {
            check(helper, helper.getBlockState(new BlockPos(x, 2, 2)).isAir(), "the claimed row stays bare");
            check(helper, helper.getBlockState(new BlockPos(x, 2, 3)).is(Blocks.WHEAT), "the open row is planted, as before");
        }
        planter.work(level);
        check(helper, planter.guiStatus() == MachineStatus.PROTECTED && asked[0] == 6 && planter.getItem(0).getCount() == 7,
                "with only the claim's spots left, it asks nothing and says the land is protected: " + planter.guiStatus() + ", asked " + asked[0]);
        helper.succeed();
    }
}
