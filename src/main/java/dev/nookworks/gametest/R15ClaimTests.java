package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.mojang.authlib.GameProfile;

import dev.nookworks.block.EnergyCableBlock;
import dev.nookworks.experience.ExperienceTankBlockEntity;
import dev.nookworks.experience.Move;
import dev.nookworks.experience.RepairBoxBlockEntity;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.feature.Permission;
import dev.nookworks.fluid.CopperTankBlockEntity;
import dev.nookworks.fluid.FluidPipeBlockEntity;
import dev.nookworks.fluid.WaterPumpBlockEntity;
import dev.nookworks.ifworld.IfChunkGenerator;
import dev.nookworks.ifworld.IfLevel;
import dev.nookworks.ifworld.IfWorld;
import dev.nookworks.machine.CrusherBlockEntity;
import dev.nookworks.machine.MachineIO;
import dev.nookworks.mercury.MercuryNetwork;
import dev.nookworks.mercury.MercuryTankBlockEntity;
import dev.nookworks.pearl.LodestoneNames;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.spawner.SpawnerLinkReceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * r15's fix check 3 (2026-10-13): what our blocks move across a claim's edge (the user's 「おすすめで」 on r15's S3) -
 * asked for the side that takes it in, as its owner, at any step, sideways, over or under; nothing given
 * into a stranger's block either; asking leaves a lodestone its name; a network reads its receivers through their loaded
 * chunks; a fake player's placing owns nothing; and the If's animals at a chunk's making are vanilla's.
 */
final class R15ClaimTests {
    private R15ClaimTests() {}

    static void register() {
        test("drawing_across_a_claim_edge_is_asked_as_the_owner", R15ClaimTests::drawRights);
        test("repair_box_takes_across_a_claim_edge_only_for_its_owner", 200, helper -> repairBox(helper, false));
        test("repair_box_takes_from_under_it_out_of_a_claim_only_for_its_owner", 200, helper -> repairBox(helper, true));
        test("asking_whose_a_block_is_leaves_a_lodestone_its_name", 100, R15ClaimTests::lodestoneKept);
        test("energy_push_holds_no_receiver_chunk_loaded", 400, R15ClaimTests::energyLetsChunksGo);
        test("pipe_hands_nothing_to_a_strangers_pipe_across_a_claim_edge", 200, R15ClaimTests::pipeTap);
        test("pipe_pours_into_a_claimed_cauldron_only_for_its_owner", 100, R15ClaimTests::pipeIntoCauldron);
        test("power_goes_across_a_claim_edge_only_where_it_may_be_taken", R15ClaimTests::power);
        test("mercury_goes_across_a_claim_edge_only_where_it_may_be_taken", R15ClaimTests::mercury);
        test("water_pump_pours_across_a_claim_edge_only_where_it_may_be_taken", 100, R15ClaimTests::waterPump);
        test("copper_tank_pours_through_a_claims_floor_only_for_its_owner", 100, R15ClaimTests::tankThroughFloor);
        test("placing_records_the_owner_but_not_a_fake_players", R15ClaimTests::placingOwner);
        test("if_animals_at_a_chunks_making_are_vanillas", R15ClaimTests::ifAnimals);
    }

    /**
     * A stand-in claim mod over the blocks {@code inside}: only {@code owner} may break them, and so take from them or
     * give into them. Taken away when the test ends, passed or failed; returned to take it away sooner.
     */
    private static Consumer<BreakBlockEvent> claim(GameTestHelper helper, Predicate<BlockPos> inside, UUID owner) {
        Consumer<BreakBlockEvent> stand = event -> {
            if (inside.test(event.getPos()) && (event.getPlayer() == null || !owner.equals(event.getPlayer().getUUID()))) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(BreakBlockEvent.class, stand);
        AfterTest.undo(helper, () -> NeoForge.EVENT_BUS.unregister(stand));
        return stand;
    }

    /** A block beside {@code by}, east or west, in the same chunk column: Flan's claims need not keep to chunks. */
    private static BlockPos besideInItsChunk(BlockPos by) {
        return (by.getX() & 15) == 15 ? by.west() : by.east();
    }

    /** Sets each block at {@code at} (a pipe, a cable) to the shape its neighbours give it: joined where they take part. */
    private static void join(ServerLevel level, BlockPos... at) {
        for (BlockPos pos : at) {
            level.setBlock(pos, Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos), Block.UPDATE_ALL);
        }
    }

    /**
     * Whether a block may take from another: at any step, sideways (within one chunk column too, as Flan's claims need
     * not keep to chunks) or straight over or under (Flan's claims have floors), as its owner breaking the other would be asked, the answer
     * kept a while; a block with no owner (set down before owners were kept) as before, unasked. Between blocks of ours,
     * the side taking in is asked; into one that keeps no owner, the giver's; two of one owner's, not at all.
     */
    private static void drawRights(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos by = helper.absolutePos(new BlockPos(3, 1, 1));
        BlockPos beside = besideInItsChunk(by);
        BlockPos above = by.above(2);
        UUID claimant = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        var stand = claim(helper, at -> at.equals(beside) || at.equals(above), claimant);
        var rights = new DrawRights();
        check(helper, !rights.couldBreak(level, by, beside, stranger), "a stranger's block takes nothing a step sideways into a claim");
        check(helper, new DrawRights().couldBreak(level, by, beside, claimant), "the claim's owner's block does");
        check(helper, new DrawRights().couldBreak(level, by, beside, null), "a block with no owner, as before");
        check(helper, !rights.couldBreak(level, by, above, stranger), "nor straight over it: Flan's claims have a floor and a ceiling");

        // our blocks: a tank of the claim's owner inside it, a stranger's beside it outside
        level.setBlockAndUpdate(beside, ModBlocks.COPPER_TANK.get().defaultBlockState());
        level.setBlockAndUpdate(by, ModBlocks.COPPER_TANK.get().defaultBlockState());
        var inside = (CopperTankBlockEntity) level.getBlockEntity(beside);
        var outside = (CopperTankBlockEntity) level.getBlockEntity(by);
        inside.setOwner(claimant);
        outside.setOwner(stranger);
        check(helper, !DrawRights.mayMove(level, beside, inside, by, outside), "nothing goes out of the claim into a stranger's tank");
        check(helper, DrawRights.mayMove(level, by, outside, beside, inside), "into the claim, from open land, it may");
        level.setBlockAndUpdate(by, Blocks.CAULDRON.defaultBlockState());
        check(helper, DrawRights.mayMove(level, beside, inside, by, null), "into a block that keeps no owner in open land, as before");
        level.setBlockAndUpdate(by, ModBlocks.COPPER_TANK.get().defaultBlockState());
        var theirs = (CopperTankBlockEntity) level.getBlockEntity(by);
        theirs.setOwner(stranger);
        inside.setOwner(stranger); // (a stranger's block inside the claim: set down before it was claimed)
        check(helper, DrawRights.mayMove(level, beside, inside, by, theirs), "between two blocks of one owner, nothing is asked");
        level.setBlockAndUpdate(by, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(beside, Blocks.AIR.defaultBlockState());

        NeoForge.EVENT_BUS.unregister(stand);
        check(helper, new DrawRights().couldBreak(level, by, beside, stranger), "with no claim there, anyone's");
        check(helper, !rights.couldBreak(level, by, beside, stranger), "an answer is kept a while");
        helper.succeed();
    }

    /**
     * A Repair Box set down beside an experience tank in a claim, a step sideways (in one chunk column): owned by a
     * stranger it draws nothing; by the claim's owner, the experience comes (r15 S3: a box outside another's claim drew
     * the experience out of their tank; S4: a column was let through unasked, which Flan's claims do not keep to).
     */
    private static void repairBox(GameTestHelper helper, boolean over) {
        var level = helper.getLevel();
        // or straight over the tank: the top of a claim Flan made 3D, or the floor of one it made 2D from above (r15's
        // other-mods check: a box there drew all of a claimed tank)
        BlockPos boxAt = helper.absolutePos(new BlockPos(3, over ? 2 : 1, 3));
        BlockPos tankAt = over ? boxAt.below() : besideInItsChunk(boxAt);
        UUID claimant = UUID.randomUUID();
        level.setBlockAndUpdate(boxAt, ModBlocks.REPAIR_BOX.get().defaultBlockState());
        level.setBlockAndUpdate(tankAt, ModBlocks.EXPERIENCE_TANK.get().defaultBlockState());
        var repair = (RepairBoxBlockEntity) level.getBlockEntity(boxAt);
        var tank = (ExperienceTankBlockEntity) level.getBlockEntity(tankAt);
        tank.fill(1000);
        repair.setOwner(UUID.randomUUID()); // a stranger's
        claim(helper, at -> at.equals(tankAt), claimant);
        helper.runAfterDelay(40, () -> {
            int strangers = repair.experience();
            // a new box, its answers not kept: the claim's owner's
            level.setBlockAndUpdate(boxAt, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(boxAt, ModBlocks.REPAIR_BOX.get().defaultBlockState());
            var own = (RepairBoxBlockEntity) level.getBlockEntity(boxAt);
            own.setOwner(claimant);
            helper.runAfterDelay(40, () -> {
                check(helper, strangers == 0, "a stranger's box drew " + strangers + " out of the claimed tank");
                check(helper, own.experience() > 0, "the claim's owner's box draws from it");
                helper.succeed();
            });
        });
    }

    /**
     * A claimed tank on a claim's bottom layer pours into a tank under it only if that tank is its owner's: a stranger's
     * set under the floor (Flan's claims have one) took all of it (r15 fix check 8, S1).
     */
    private static void tankThroughFloor(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos upper = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos lower = upper.below();
        UUID claimant = UUID.randomUUID();
        level.setBlockAndUpdate(lower, ModBlocks.COPPER_TANK.get().defaultBlockState());
        level.setBlockAndUpdate(upper, ModBlocks.COPPER_TANK.get().defaultBlockState());
        var top = (CopperTankBlockEntity) level.getBlockEntity(upper);
        var under = (CopperTankBlockEntity) level.getBlockEntity(lower);
        top.setOwner(claimant);
        under.setOwner(UUID.randomUUID()); // a stranger's, under the claim's floor
        claim(helper, at -> at.equals(upper), claimant);
        try (Transaction tx = Transaction.openRoot()) {
            top.fluidHandler().insert(FluidResource.of(Fluids.WATER), 4000, tx);
            tx.commit();
        }
        helper.runAfterDelay(20, () -> {
            int strangers = under.amount();
            // the claim's owner's own tank under it, its answers not kept: the claim's owner's
            level.setBlockAndUpdate(lower, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(lower, ModBlocks.COPPER_TANK.get().defaultBlockState());
            ((CopperTankBlockEntity) level.getBlockEntity(lower)).setOwner(claimant);
            helper.runAfterDelay(20, () -> {
                check(helper, strangers == 0, "a stranger's tank under the floor took " + strangers + " mB");
                check(helper, ((CopperTankBlockEntity) level.getBlockEntity(lower)).amount() > 0, "the claim's owner's own tank under it fills");
                helper.succeed();
            });
        });
    }

    /**
     * Asking whose a block is posts a break event, and a lodestone's name was forgotten at it: a Repair Box beside a
     * named lodestone, across a chunk's edge, took its name away every five minutes (r15 fix check 3, S1). Neither the
     * asking itself nor a box beside it - which asks only where there is experience to take - touches the name now.
     */
    private static void lodestoneKept(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos stone = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlockAndUpdate(stone, Blocks.LODESTONE.defaultBlockState());
        LodestoneNames.ensure(level, stone);
        GlobalPos named = GlobalPos.of(level.dimension(), stone.immutable());
        AfterTest.undo(helper, () -> LodestoneNames.forget(level, stone));
        check(helper, LodestoneNames.of(named).isPresent(), "named to begin with");
        check(helper, Permission.mayBreak(level, stone, Permission.actor(level, UUID.randomUUID())), "nothing keeps it from being broken");
        check(helper, LodestoneNames.of(named).isPresent(), "only asked about, the stone keeps its name");
        level.setBlockAndUpdate(stone.west(), ModBlocks.REPAIR_BOX.get().defaultBlockState());
        ((RepairBoxBlockEntity) level.getBlockEntity(stone.west())).setOwner(UUID.randomUUID());
        level.setBlockAndUpdate(stone.east(), ModBlocks.REPAIR_BOX.get().defaultBlockState());
        ((RepairBoxBlockEntity) level.getBlockEntity(stone.east())).setOwner(UUID.randomUUID());
        helper.runAfterDelay(40, () -> {
            check(helper, LodestoneNames.of(named).isPresent(), "Repair Boxes beside it leave it its name");
            helper.succeed();
        });
    }

    /**
     * A generator's push reads its receivers through their loaded chunks: through the level, every one of them was read
     * every tick, which left a ticket holding its chunk loaded after everyone had gone (r15 fix check 3's M1, a return of
     * what r7 fixed). Four full machines along a cable through four chunks, the generator's chunk kept loaded: the far
     * ones' chunks go when no longer kept. (The ones next to it stay, as the loaded chunk's neighbours always do.)
     */
    private static void energyLetsChunksGo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int baseX = 1_216_000;
        int bz = -1_216_000;
        int y = 100;
        int chunks = 5; // C0 (kept forced) .. C4
        for (int k = 0; k < chunks; k++) {
            level.setChunkForced((baseX >> 4) + k, bz >> 4, true);
        }
        BlockPos origin = new BlockPos(baseX + 8, y, bz + 8);
        List<BlockPos> placed = new ArrayList<>();
        level.setBlock(origin, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        placed.add(origin);
        List<BlockPos> crushers = new ArrayList<>();
        for (int k = 1; k < chunks; k++) {
            BlockPos at = new BlockPos(baseX + 16 * k + 8, y + 1, bz + 8);
            level.setBlock(at, ModBlocks.CRUSHER.get().defaultBlockState(), Block.UPDATE_ALL);
            crushers.add(at);
        }
        placed.addAll(crushers);
        List<BlockPos> cables = new ArrayList<>();
        for (int x = baseX + 9; x <= baseX + 16 * (chunks - 1) + 8; x++) {
            BlockPos at = new BlockPos(x, y, bz + 8);
            level.setBlock(at, ModBlocks.ENERGY_CABLE.get().defaultBlockState(), Block.UPDATE_ALL);
            cables.add(at);
        }
        placed.addAll(cables);
        join(level, cables.toArray(BlockPos[]::new));
        for (BlockPos at : crushers) { // full: nothing is sent to them, so no insert marks them changed
            var handler = MachineIO.handlerOf(level, new MachineIO.Receiver(at, List.of(Direction.DOWN)));
            check(helper, handler != null, "a crusher at " + at + " takes power");
            for (int i = 0; i < 1000 && handler.getAmountAsLong() < handler.getCapacityAsLong(); i++) {
                ReviewFourTests.insert(handler, Integer.MAX_VALUE);
            }
        }
        check(helper, MachineIO.receivers(level, origin).size() == crushers.size(), "the network reaches the " + crushers.size() + " crushers");
        SimpleEnergyHandler source = new SimpleEnergyHandler(1_000_000, 1_000_000, 1_000_000);
        for (int k = 1; k < chunks; k++) {
            level.setChunkForced((baseX >> 4) + k, bz >> 4, false);
        }
        helper.onEachTick(() -> {
            ReviewFourTests.insert(source, 1_000_000);
            MachineIO.pushEnergy(level, origin, source, 1000);
        });
        helper.runAfterDelay(300, () -> {
            boolean c3 = level.getChunkSource().getChunkNow((baseX >> 4) + 3, bz >> 4) != null;
            boolean c4 = level.getChunkSource().getChunkNow((baseX >> 4) + 4, bz >> 4) != null;
            for (BlockPos at : placed) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            level.setChunkForced(baseX >> 4, bz >> 4, false);
            check(helper, !c3 && !c4, "the far receivers' chunks are let go (C3 loaded " + c3 + ", C4 " + c4 + ")");
            helper.succeed();
        });
    }

    /**
     * The claim's owner's pipe draws from their tank inside the claim; a stranger joins their own pipe and tank to it
     * from outside, a step sideways: the line hands them nothing (r15 fix check 3, S2: all 4,000 mB went). Set down by
     * the claim's owner, the same pipe and tank fill.
     */
    private static void pipeTap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ownTank = helper.absolutePos(new BlockPos(1, 1, 3));
        BlockPos ownPipe = ownTank.east();
        BlockPos theirPipe = ownPipe.east();
        BlockPos theirTank = theirPipe.east();
        UUID claimant = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        level.setBlockAndUpdate(ownTank, ModBlocks.COPPER_TANK.get().defaultBlockState());
        level.setBlockAndUpdate(ownPipe, ModBlocks.COPPER_PIPE.get().defaultBlockState());
        level.setBlockAndUpdate(theirPipe, ModBlocks.COPPER_PIPE.get().defaultBlockState());
        level.setBlockAndUpdate(theirTank, ModBlocks.COPPER_TANK.get().defaultBlockState());
        join(level, ownPipe, theirPipe);
        var from = (CopperTankBlockEntity) level.getBlockEntity(ownTank);
        var own = (FluidPipeBlockEntity) level.getBlockEntity(ownPipe);
        var theirs = (FluidPipeBlockEntity) level.getBlockEntity(theirPipe);
        var to = (CopperTankBlockEntity) level.getBlockEntity(theirTank);
        from.setOwner(claimant);
        own.setOwner(claimant);
        theirs.setOwner(stranger);
        to.setOwner(stranger);
        check(helper, own.cycle(level, Direction.WEST) == FluidPipeBlockEntity.SideSetting.PULLING, "the claim's owner's pipe pulls from their tank");
        from.fluidHandler().set(0, FluidResource.of(Fluids.WATER), 4_000);
        claim(helper, at -> at.equals(ownTank) || at.equals(ownPipe), claimant);
        helper.runAfterDelay(60, () -> {
            int taken = to.amount();
            check(helper, taken == 0, "a stranger's pipe joined across the claim's edge took " + taken + " mB");
            check(helper, from.amount() >= 3_000, "and the claimed tank keeps it: " + from.amount() + " mB");
            theirs.setOwner(claimant);
            to.setOwner(claimant);
            helper.runAfterDelay(40, () -> {
                check(helper, to.amount() > 0, "the claim's owner's own pipe and tank there fill");
                helper.succeed();
            });
        });
    }

    /**
     * A pipe pours into a vanilla cauldron, which keeps no owner of ours: asked as the pipe's owner whether they could
     * break it. Inside someone else's claim, a stranger's pipe pours nothing in; the claim's owner's does.
     */
    private static void pipeIntoCauldron(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pipeAt = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos cauldron = besideInItsChunk(pipeAt);
        UUID claimant = UUID.randomUUID();
        level.setBlockAndUpdate(cauldron, Blocks.CAULDRON.defaultBlockState());
        level.setBlockAndUpdate(pipeAt, ModBlocks.COPPER_PIPE.get().defaultBlockState());
        join(level, pipeAt);
        var pipe = (FluidPipeBlockEntity) level.getBlockEntity(pipeAt);
        pipe.setOwner(UUID.randomUUID());
        check(helper, Move.insert(pipe.handlerFor(null), FluidResource.of(Fluids.WATER), 1_000) == 1_000, "the pipe holds a bucket");
        claim(helper, at -> at.equals(cauldron), claimant);
        helper.runAfterDelay(20, () -> {
            check(helper, level.getBlockState(cauldron).is(Blocks.CAULDRON), "a stranger's pipe pours nothing into a claimed cauldron");
            pipe.setOwner(claimant);
            helper.runAfterDelay(20, () -> {
                check(helper, level.getBlockState(cauldron).is(Blocks.WATER_CAULDRON), "the claim's owner's pipe fills it");
                helper.succeed();
            });
        });
    }

    /**
     * Power from a generator (and from another mod, into a cable) inside a claim goes to the claim's owner's machine, and
     * none to a stranger's crusher or Spawner Link Receiver (not a machine, but it keeps its owner now) on the same cable
     * outside it.
     */
    private static void power(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 3));
        level.setBlockAndUpdate(origin, Blocks.STONE.defaultBlockState());
        List<BlockPos> cables = new ArrayList<>();
        for (int x = 1; x <= 5; x++) {
            BlockPos at = origin.offset(x, 0, 0);
            level.setBlockAndUpdate(at, ModBlocks.ENERGY_CABLE.get().defaultBlockState());
            cables.add(at);
        }
        BlockPos strangersAt = origin.offset(2, 1, 0);
        BlockPos receiverAt = origin.offset(3, 1, 0);
        BlockPos ownAt = origin.offset(5, 1, 0);
        level.setBlockAndUpdate(strangersAt, ModBlocks.CRUSHER.get().defaultBlockState());
        level.setBlockAndUpdate(receiverAt, ModBlocks.SPAWNER_LINK_RECEIVER.get().defaultBlockState());
        level.setBlockAndUpdate(ownAt, ModBlocks.CRUSHER.get().defaultBlockState());
        join(level, cables.toArray(BlockPos[]::new));
        UUID claimant = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        var strangers = (CrusherBlockEntity) level.getBlockEntity(strangersAt);
        var receiver = (SpawnerLinkReceiverBlockEntity) level.getBlockEntity(receiverAt);
        var own = (CrusherBlockEntity) level.getBlockEntity(ownAt);
        strangers.setOwner(stranger);
        receiver.setOwner(stranger);
        own.setOwner(claimant);
        check(helper, MachineIO.receivers(level, origin).size() == 3, "the cable reaches all three");
        claim(helper, at -> at.equals(origin) || at.equals(cables.get(0)), claimant);
        SimpleEnergyHandler source = new SimpleEnergyHandler(1_000_000, 1_000_000, 1_000_000);
        ReviewFourTests.insert(source, 1_000_000);
        int sent = MachineIO.pushEnergy(level, origin, source, 600);
        check(helper, sent > 0 && own.energyStored() > 0, "the claim's owner's crusher is powered: " + sent);
        check(helper, strangers.energyStored() == 0 && receiver.energy().getAmountAsLong() == 0,
                "a stranger's crusher and receiver on the cable outside get nothing: " + strangers.energyStored() + ", "
                        + receiver.energy().getAmountAsLong());
        int had = own.energyStored();
        var cable = EnergyCableBlock.networkHandler(level, cables.get(0), null);
        try (Transaction tx = Transaction.openRoot()) {
            cable.insert(600, tx);
            tx.commit();
        }
        check(helper, own.energyStored() > had, "another mod's power put into the claimed cable reaches the claim's owner's");
        check(helper, strangers.energyStored() == 0 && receiver.energy().getAmountAsLong() == 0, "and not the stranger's");
        helper.succeed();
    }

    /**
     * Mercury between the claim's owner's tank inside the claim and a stranger's spliced onto the line outside: not pushed
     * out into it, nor pulled out by it; the claim's owner's own tank there takes it.
     */
    private static void mercury(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ownAt = helper.absolutePos(new BlockPos(1, 1, 3));
        BlockPos theirAt = ownAt.east(3);
        level.setBlockAndUpdate(ownAt, ModBlocks.MERCURY_TANK.get().defaultBlockState());
        level.setBlockAndUpdate(theirAt, ModBlocks.MERCURY_TANK.get().defaultBlockState());
        level.setBlockAndUpdate(ownAt.east(), ModBlocks.MERCURY_PIPE.get().defaultBlockState());
        level.setBlockAndUpdate(ownAt.east(2), ModBlocks.MERCURY_PIPE.get().defaultBlockState());
        join(level, ownAt.east(), ownAt.east(2));
        var own = (MercuryTankBlockEntity) level.getBlockEntity(ownAt);
        var theirs = (MercuryTankBlockEntity) level.getBlockEntity(theirAt);
        UUID claimant = UUID.randomUUID();
        own.setOwner(claimant);
        theirs.setOwner(UUID.randomUUID());
        own.fillMercury(1_000, false);
        check(helper, MercuryNetwork.handlers(level, ownAt).contains(theirs), "the two tanks are on one line");
        claim(helper, at -> at.equals(ownAt), claimant);
        int pushed = MercuryNetwork.push(level, ownAt, own, 500);
        int pulled = MercuryNetwork.pull(level, theirAt, theirs, 500);
        check(helper, pushed == 0 && pulled == 0 && theirs.mercury() == 0,
                "a stranger's tank across the claim's edge gets none: pushed " + pushed + ", pulled " + pulled);
        theirs.setOwner(claimant);
        check(helper, MercuryNetwork.pull(level, theirAt, theirs, 500) == 500 && MercuryNetwork.push(level, ownAt, own, 200) == 200,
                "the claim's owner's own tank there takes it");
        helper.succeed();
    }

    /**
     * A Water Pump inside a claim pours into the tank a step sideways only where its owner may take it: a stranger's
     * tank set against it outside gets nothing, as a stranger's pipe would; set down by the claim's owner, it fills.
     */
    private static void waterPump(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pumpAt = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos tankAt = besideInItsChunk(pumpAt);
        level.setBlockAndUpdate(pumpAt, ModBlocks.WATER_PUMP.get().defaultBlockState());
        level.setBlockAndUpdate(tankAt, ModBlocks.COPPER_TANK.get().defaultBlockState());
        var pump = (WaterPumpBlockEntity) level.getBlockEntity(pumpAt);
        var tank = (CopperTankBlockEntity) level.getBlockEntity(tankAt);
        UUID claimant = UUID.randomUUID();
        pump.setOwner(claimant);
        tank.setOwner(UUID.randomUUID());
        ((FluidStacksResourceHandler) pump.fluidHandler()).set(0, FluidResource.of(Fluids.WATER), 4_000);
        claim(helper, at -> at.equals(pumpAt), claimant);
        helper.runAfterDelay(10, () -> {
            check(helper, tank.amount() == 0, "a stranger's tank against a claimed pump got " + tank.amount() + " mB");
            tank.setOwner(claimant);
            helper.runAfterDelay(10, () -> {
                check(helper, tank.amount() > 0, "the claim's owner's tank there fills");
                helper.succeed();
            });
        });
    }

    /**
     * A player's placing records who set it down - pipes, machines, tanks - and a fake player's (another mod's machine
     * placing) leaves no owner, as before owners were kept: a claim mod seldom counts it a member of its owner's claim.
     */
    private static void placingOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 near = helper.absoluteVec(new Vec3(3.5, 2, 0.5));
        player.snapTo(near.x, near.y, near.z);
        int x = 0;
        for (Block block : List.of(ModBlocks.COPPER_PIPE.get(), ModBlocks.CRUSHER.get(), ModBlocks.COPPER_TANK.get(), ModBlocks.MERCURY_TANK.get())) {
            BlockPos floor = helper.absolutePos(new BlockPos(x, 1, 2));
            level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
            ItemStack stack = new ItemStack(block.asItem());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
            BlockEntity placed = level.getBlockEntity(floor.above());
            check(helper, placed instanceof DrawRights.Owned owned && player.getUUID().equals(owned.owner()),
                    block.getDescriptionId() + " set down by a player is theirs: " + placed);
            x += 2;
        }
        var fake = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "[Test placer]"));
        BlockPos floor = helper.absolutePos(new BlockPos(1, 1, 5));
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        ItemStack stack = new ItemStack(ModBlocks.COPPER_PIPE.get().asItem());
        fake.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(fake, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false)));
        fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockEntity placed = level.getBlockEntity(floor.above());
        check(helper, placed instanceof FluidPipeBlockEntity, "the fake player set the pipe down: " + placed);
        check(helper, ((FluidPipeBlockEntity) placed).owner() == null, "a fake player's pipe has no owner: " + ((FluidPipeBlockEntity) placed).owner());
        helper.succeed();
    }

    /**
     * The animals a chunk of the If is made with are its biome's own in vanilla, not the server's: here the server's
     * biome is made to hold pandas only, nine times in ten (as a mod's biome modifier would add its own), and four new
     * chunks of it in the If are made - none draws a panda (r15 fix check 3, S5: vanilla's spawning at a chunk's making
     * read the server's biome). What is drawn is only noted, and stopped: nothing is let out into the If.
     */
    private static void ifAnimals(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        IfWorld world = IfWorld.get(server);
        while (!world.rolled()) {
            world.addThrow(1 + helper.getLevel().getRandom().nextInt(6));
        }
        ServerLevel ifLevel = IfLevel.open(server);
        check(helper, ifLevel != null && ifLevel.getChunkSource().getGenerator() instanceof IfChunkGenerator, "the If is ours");
        var generator = ifLevel.getChunkSource().getGenerator();
        var biomes = generator.getBiomeSource().createUncachedResolver(ifLevel.getChunkSource().randomState());
        var spawns = EnvironmentAttributes.NATURAL_MOB_SPAWNS;
        MobSpawnSettings dimension = ifLevel.dimensionType().attributes().applyModifier(spawns, spawns.defaultValue());
        // a land biome whose animals are not pandas, and four chunks of it far out, not made yet
        RandomSource random = RandomSource.create();
        Holder<Biome> chosen = null;
        List<ChunkPos> chunks = new ArrayList<>();
        int top = QuartPos.fromBlock(ifLevel.getMaxY());
        for (int attempt = 0; attempt < 8 && chunks.size() < 4; attempt++) {
            chosen = null;
            chunks.clear();
            int cx0 = random.nextIntBetweenInclusive(-60_000, 60_000);
            int cz0 = random.nextIntBetweenInclusive(-60_000, 60_000);
            for (int i = 0; i < 48 * 48 && chunks.size() < 4; i++) {
                int cx = cx0 + i % 48;
                int cz = cz0 + i / 48;
                Holder<Biome> biome = biomes.getNoiseBiome(QuartPos.fromSection(cx), top, QuartPos.fromSection(cz));
                if (chosen == null) {
                    var animals = biome.value().getAttributes().applyModifier(spawns, dimension).getMobsToSpawn(MobCategory.CREATURE);
                    if (!animals.isEmpty() && animals.unwrap().stream().noneMatch(w -> w.value().type() == EntityTypes.PANDA)) {
                        chosen = biome;
                    }
                }
                if (biome.equals(chosen)) {
                    chunks.add(new ChunkPos(cx, cz));
                }
            }
        }
        check(helper, chosen != null && chunks.size() == 4, "four chunks of one land biome found: " + chunks);
        Holder<Biome> swapped = chosen;
        ModifiableBiomeInfo info = swapped.value().modifiableBiomeInfo();
        Field modified;
        Object was;
        try {
            modified = ModifiableBiomeInfo.class.getDeclaredField("modifiedBiomeInfo");
            modified.setAccessible(true);
            was = modified.get(info);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        ModifiableBiomeInfo.BiomeInfo base = info.get();
        EnvironmentAttributeMap pandas = EnvironmentAttributeMap.builder().putAll(base.attributes())
                .set(spawns, new MobSpawnSettings.Builder().addSpawn(EntityTypes.PANDA, 1, 1, 2).build())
                .set(EnvironmentAttributes.CREATURE_WORLD_GEN_SPAWN_PROBABILITY, 0.9F)
                .build();
        Set<EntityType<?>> drawn = ConcurrentHashMap.newKeySet();
        Consumer<MobSpawnEvent.SpawnPlacementCheck> noted = event -> {
            if (event.getSpawnType() == EntitySpawnReason.CHUNK_GENERATION && event.getLevel().getLevel() == ifLevel) {
                drawn.add(event.getEntityType());
                event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
            }
        };
        GameRules rules = ifLevel.getGameRules();
        boolean spawning = rules.get(GameRules.SPAWN_MOBS);
        List<String> unmade = new ArrayList<>();
        boolean took;
        try {
            modified.set(info, new ModifiableBiomeInfo.BiomeInfo(base.climateSettings(), pandas, base.effects(), base.generationSettings()));
            NeoForge.EVENT_BUS.addListener(MobSpawnEvent.SpawnPlacementCheck.class, noted);
            rules.set(GameRules.SPAWN_MOBS, true, server); // (the test server's are off; nothing ticks while this runs)
            took = swapped.value().getAttributes().applyModifier(spawns, dimension).getMobsToSpawn(MobCategory.CREATURE)
                    .unwrap().stream().anyMatch(w -> w.value().type() == EntityTypes.PANDA);
            var source = ifLevel.getChunkSource();
            for (ChunkPos pos : chunks) {
                var future = source.getChunkFuture(pos.x(), pos.z(), ChunkStatus.FULL, true);
                long deadline = System.nanoTime() + 60_000_000_000L;
                while (!future.isDone() && System.nanoTime() < deadline) {
                    if (!source.pollTask()) {
                        LockSupport.parkNanos(1_000_000);
                    }
                }
                if (!future.isDone() || !future.join().isSuccess()) {
                    unmade.add(pos.toString());
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } finally {
            rules.set(GameRules.SPAWN_MOBS, spawning, server);
            NeoForge.EVENT_BUS.unregister(noted);
            try {
                modified.set(info, was);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        check(helper, took, "the server's biome held pandas for the while (else this test shows nothing)");
        check(helper, unmade.isEmpty(), "the chunks were made: not " + unmade);
        check(helper, !drawn.contains(EntityTypes.PANDA), "the If drew the server's pandas at a chunk's making: " + drawn);
        helper.succeed();
    }
}
