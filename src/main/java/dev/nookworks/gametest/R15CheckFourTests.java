package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;

import dev.nookworks.block.EnergyCableBlock;
import dev.nookworks.block.MotorBlock;
import dev.nookworks.block.ThinRopeBlock;
import dev.nookworks.drive.BeltKind;
import dev.nookworks.drive.Belts;
import dev.nookworks.drive.DriveWheelBlock;
import dev.nookworks.drive.DriveWheelBlockEntity;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.feature.Permission;
import dev.nookworks.fluid.CopperTankBlockEntity;
import dev.nookworks.fluid.FluidPipeBlockEntity;
import dev.nookworks.machine.CrusherBlockEntity;
import dev.nookworks.machine.GeneratorBlockEntity;
import dev.nookworks.machine.MachineIO;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.machine.MotorBlockEntity;
import dev.nookworks.oil.OilPressBlockEntity;
import dev.nookworks.registry.ModBarter;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import dev.nookworks.registry.ModRope;
import dev.nookworks.rope.HungBannerEntity;
import dev.nookworks.rope.HungItemEntity;
import dev.nookworks.rope.RopeKnotEntity;
import dev.nookworks.rope.RopeSpans;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * r15's fix check 4 (2026-10-13): the Motor's drive is asked across a claim's edge as pipes and cables are; a block
 * another mod's machine sets down (a fake player) is the player's whose id it carries when the server knows them, and
 * nobody's otherwise - the Laser, Planter and Barter Terminal too; Thin Rope, what hangs on it and drive belts copied by
 * a structure - moved, turned, mirrored - run between the copied posts and wheels, and saves from before still load;
 * power another mod puts into a cable is asked once for each owner, not once for each receiver; and a receiver that
 * tells of no room is asked before it is handed anything.
 */
final class R15CheckFourTests {
    private R15CheckFourTests() {}

    static void register() {
        test("motor_shaft_turns_no_strangers_machine_across_a_claim_edge", 100, R15CheckFourTests::motorShaft);
        test("motor_wheels_turn_no_strangers_machine_across_a_claim_edge", 100, R15CheckFourTests::motorWheels);
        test("a_known_players_machine_places_for_them", 100, R15CheckFourTests::knownPlacer);
        test("an_unknown_machines_placing_owns_nothing", R15CheckFourTests::unknownPlacer);
        test("structure_copy_mirrored_and_turned_keeps_its_lines", 100,
                helper -> copied(helper, Mirror.LEFT_RIGHT, Rotation.CLOCKWISE_90, new BlockPos(2, 5, 1)));
        test("structure_copy_turned_keeps_its_lines", 100,
                helper -> copied(helper, Mirror.NONE, Rotation.COUNTERCLOCKWISE_90, new BlockPos(2, 5, 6)));
        test("structure_copy_mirrored_front_to_back_keeps_its_lines", 100,
                helper -> copied(helper, Mirror.FRONT_BACK, Rotation.NONE, new BlockPos(6, 5, 2)));
        test("structure_copy_mirrored_and_turned_round_keeps_its_lines", 100,
                helper -> copied(helper, Mirror.LEFT_RIGHT, Rotation.CLOCKWISE_180, new BlockPos(6, 5, 2)));
        test("structure_copy_mirrored_front_to_back_and_turned_keeps_its_lines", 100,
                helper -> copied(helper, Mirror.FRONT_BACK, Rotation.COUNTERCLOCKWISE_90, new BlockPos(2, 5, 1)));
        test("lines_saved_before_they_kept_their_own_place_still_load", R15CheckFourTests::oldLines);
        test("cable_power_from_another_mod_is_asked_once_an_owner", R15CheckFourTests::cableAskings);
        test("a_receiver_telling_of_no_room_is_still_asked", R15CheckFourTests::noRoomAsked);
    }

    // ---- helpers ----

    /** A stand-in claim mod over the blocks {@code inside}: only {@code owners} may break them. Gone when the test ends. */
    private static void claim(GameTestHelper helper, Set<BlockPos> inside, Set<UUID> owners) {
        Consumer<BreakBlockEvent> stand = event -> {
            if (inside.contains(event.getPos()) && (event.getPlayer() == null || !owners.contains(event.getPlayer().getUUID()))) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(BreakBlockEvent.class, stand);
        AfterTest.undo(helper, () -> NeoForge.EVENT_BUS.unregister(stand));
    }

    /** Counts our own askings (break events posted as a question) from now until the test ends, refused ones too. */
    private static AtomicInteger askings(GameTestHelper helper) {
        AtomicInteger asked = new AtomicInteger();
        Consumer<BreakBlockEvent> counter = event -> {
            if (Permission.asking()) {
                asked.incrementAndGet();
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, BreakBlockEvent.class, counter);
        AfterTest.undo(helper, () -> NeoForge.EVENT_BUS.unregister(counter));
        return asked;
    }

    private static MotorBlockEntity motor(GameTestHelper helper, BlockPos rel, Direction facing) {
        helper.setBlock(rel, ModOil.MOTOR.get().defaultBlockState().setValue(MotorBlock.FACING, facing));
        MotorBlockEntity motor = helper.getBlockEntity(rel, MotorBlockEntity.class);
        motor.energy().set((int) motor.energy().getCapacityAsLong());
        return motor;
    }

    private static DriveWheelBlockEntity wheel(GameTestHelper helper, BlockPos rel, Direction facing) {
        helper.setBlock(rel, ModOil.DRIVE_WHEEL.get().defaultBlockState().setValue(DriveWheelBlock.FACING, facing));
        return helper.getBlockEntity(rel, DriveWheelBlockEntity.class);
    }

    private static OilPressBlockEntity press(GameTestHelper helper, BlockPos rel, UUID owner) {
        helper.setBlock(rel, ModOil.OIL_PRESS.get());
        OilPressBlockEntity press = helper.getBlockEntity(rel, OilPressBlockEntity.class);
        press.setItem(OilPressBlockEntity.INPUT, new ItemStack(ModOil.SUNFLOWER_SEEDS.get(), 64));
        press.setOwner(owner);
        return press;
    }

    private static boolean worked(OilPressBlockEntity press) {
        return press.turns() > 0 || press.stored() > 0;
    }

    /** {@code id} made a player this server knows: one who has played here, in its user cache. */
    private static void known(ServerLevel level, UUID id, String name) {
        level.getServer().services().nameToIdCache().add(new NameAndId(id, name));
    }

    /** Sets {@code block} down on stone at {@code floor} as another mod's machine would: a fake player carrying {@code id}. */
    private static BlockPos placeAsMachine(ServerLevel level, BlockPos floor, Block block, UUID id) {
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        FakePlayer fake = FakePlayerFactory.get(level, new GameProfile(id, "[Test deployer]"));
        ItemStack stack = new ItemStack(block.asItem());
        fake.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(fake, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false)));
        fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return floor.above();
    }

    private static void join(ServerLevel level, BlockPos... at) {
        for (BlockPos pos : at) {
            level.setBlock(pos, Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos), Block.UPDATE_ALL);
        }
    }

    // ---- the Motor's drive (S1) ----

    /**
     * The claim's owner's Motor inside the claim, its shaft a step out of it: a stranger's Oil Press set against the
     * shaft outside is not turned, and the motor spends nothing on it (it was turned on the claim's owner's power); the
     * claim's owner's own press there, against another motor of theirs, is.
     */
    private static void motorShaft(GameTestHelper helper) {
        UUID claimant = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        MotorBlockEntity motor = motor(helper, new BlockPos(1, 2, 1), Direction.SOUTH);
        OilPressBlockEntity theirs = press(helper, new BlockPos(1, 2, 2), stranger);
        MotorBlockEntity ownMotor = motor(helper, new BlockPos(4, 2, 1), Direction.SOUTH);
        OilPressBlockEntity own = press(helper, new BlockPos(4, 2, 2), claimant);
        motor.setOwner(claimant);
        ownMotor.setOwner(claimant);
        claim(helper, Set.of(motor.getBlockPos(), ownMotor.getBlockPos()), Set.of(claimant));
        int had = motor.energyStored();
        helper.runAfterDelay(40, () -> {
            int spent = had - motor.energyStored();
            check(helper, !worked(theirs) && spent <= MachineTuning.energy(MotorBlockEntity.IDLE_FE_PER_TICK) * 40, "a stranger's press against the claimed motor's shaft is turned: turns "
                    + theirs.turns() + ", oil " + theirs.stored() + ", the claim's owner's FE spent " + spent);
            check(helper, worked(own), "the claim's owner's own press against their motor, a step out of the claim, is turned");
            helper.succeed();
        });
    }

    /**
     * The same through Drive Wheels set end to end on one axle - no belt to tie, so nothing in the claim is clicked: a
     * stranger's wheel against the claimed motor's wheel from outside, and their press beyond it, is not turned. The
     * claim's owner's own line of wheels out of the claim turns their press as before.
     */
    private static void motorWheels(GameTestHelper helper) {
        UUID claimant = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        MotorBlockEntity motor = motor(helper, new BlockPos(1, 2, 1), Direction.SOUTH);
        DriveWheelBlockEntity inside = wheel(helper, new BlockPos(1, 2, 2), Direction.SOUTH);
        wheel(helper, new BlockPos(1, 2, 3), Direction.SOUTH); // the stranger's, outside
        OilPressBlockEntity theirs = press(helper, new BlockPos(1, 2, 4), stranger);
        MotorBlockEntity ownMotor = motor(helper, new BlockPos(4, 2, 1), Direction.SOUTH);
        DriveWheelBlockEntity ownInside = wheel(helper, new BlockPos(4, 2, 2), Direction.SOUTH);
        wheel(helper, new BlockPos(4, 2, 3), Direction.SOUTH);
        OilPressBlockEntity own = press(helper, new BlockPos(4, 2, 4), claimant);
        motor.setOwner(claimant);
        ownMotor.setOwner(claimant);
        claim(helper, Set.of(motor.getBlockPos(), inside.getBlockPos(), ownMotor.getBlockPos(), ownInside.getBlockPos()), Set.of(claimant));
        int had = motor.energyStored();
        helper.runAfterDelay(40, () -> {
            int spent = had - motor.energyStored();
            check(helper, !worked(theirs) && spent <= MachineTuning.energy(MotorBlockEntity.IDLE_FE_PER_TICK) * 40, "a stranger's wheel and press joined to the claimed motor's wheel are turned: turns "
                    + theirs.turns() + ", oil " + theirs.stored() + ", the claim's owner's FE spent " + spent);
            check(helper, worked(own), "the claim's owner's own press beyond their wheels, out of the claim, is turned");
            helper.succeed();
        });
    }

    // ---- what another mod's machine sets down (S2, N1) ----

    /**
     * A Deployer (a fake player) carrying the id of a player the server knows sets blocks down as theirs: a stranger's,
     * a Copper Pipe beside a claimed tank from outside, draws nothing out of it (it drew all 4,000 mB, as nobody's); the
     * claim's owner's, a pipe against an unowned tank in their own claim, draws from it. A Laser, Planter and Barter
     * Terminal it sets down are its player's too.
     */
    private static void knownPlacer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID claimant = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        known(level, claimant, "claim-owner");
        known(level, stranger, "stranger");
        List<CopperTankBlockEntity> into = new ArrayList<>();
        List<FluidPipeBlockEntity> pipes = new ArrayList<>();
        List<BlockPos> claimed = new ArrayList<>();
        for (int row = 0; row < 2; row++) {
            UUID deployer = row == 0 ? stranger : claimant;
            BlockPos tankAt = helper.absolutePos(new BlockPos(1, 2, 1 + 3 * row));
            BlockPos pipeAt = tankAt.east();
            BlockPos intoAt = pipeAt.east();
            level.setBlockAndUpdate(tankAt, ModBlocks.COPPER_TANK.get().defaultBlockState());
            level.setBlockAndUpdate(intoAt, ModBlocks.COPPER_TANK.get().defaultBlockState());
            var from = (CopperTankBlockEntity) level.getBlockEntity(tankAt);
            var to = (CopperTankBlockEntity) level.getBlockEntity(intoAt);
            from.setOwner(row == 0 ? claimant : null); // the second row's tank keeps no owner: an old one, in the claim
            to.setOwner(deployer);
            placeAsMachine(level, pipeAt.below(), ModBlocks.COPPER_PIPE.get(), deployer);
            join(level, pipeAt);
            var pipe = (FluidPipeBlockEntity) level.getBlockEntity(pipeAt);
            check(helper, pipe != null && deployer.equals(pipe.owner()), "a pipe a known player's Deployer sets down is theirs: "
                    + (pipe == null ? null : pipe.owner()));
            check(helper, pipe.cycle(level, Direction.WEST) == FluidPipeBlockEntity.SideSetting.PULLING, "the pipe pulls from the tank");
            from.fluidHandler().set(0, FluidResource.of(Fluids.WATER), 4_000);
            into.add(to);
            pipes.add(pipe);
            claimed.add(tankAt);
        }
        int x = 0;
        for (Block block : List.of(ModBlocks.LASER.get(), ModBlocks.PLANTER.get(), ModBarter.BARTER_TERMINAL.get())) {
            BlockPos at = placeAsMachine(level, helper.absolutePos(new BlockPos(5, 1, 1 + 2 * x++)), block, claimant);
            check(helper, level.getBlockEntity(at) instanceof DrawRights.Owned owned && claimant.equals(owned.owner()),
                    block.getDescriptionId() + " a known player's Deployer sets down is theirs: " + level.getBlockEntity(at));
        }
        claim(helper, Set.copyOf(claimed), Set.of(claimant));
        helper.runAfterDelay(60, () -> {
            check(helper, into.get(0).amount() == 0, "a stranger's Deployer's pipe beside the claim drew " + into.get(0).amount()
                    + " mB out of the claimed tank");
            check(helper, into.get(1).amount() > 0, "the claim's owner's Deployer's pipe draws from a tank in their claim");
            helper.succeed();
        });
    }

    /**
     * A fake player carrying an id the server does not know (another mod's machine of its own) owns nothing it sets
     * down, as before: the Laser, Planter and Barter Terminal no more than a pipe (they kept the fake player's id, and a
     * Laser set down so in its owner's claim took no power across a step sideways).
     */
    private static void unknownPlacer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID machine = UUID.randomUUID();
        int x = 0;
        for (Block block : List.of(ModBlocks.COPPER_PIPE.get(), ModBlocks.LASER.get(), ModBlocks.PLANTER.get(), ModBarter.BARTER_TERMINAL.get())) {
            BlockPos at = placeAsMachine(level, helper.absolutePos(new BlockPos(1 + 2 * (x % 3), 1, 1 + 3 * (x / 3))), block, machine);
            x++;
            BlockEntity placed = level.getBlockEntity(at);
            check(helper, placed instanceof DrawRights.Owned owned && owned.owner() == null,
                    block.getDescriptionId() + " set down by an unknown machine keeps no owner: "
                            + (placed instanceof DrawRights.Owned owned ? owned.owner() : placed));
        }
        helper.succeed();
    }

    // ---- lines copied by a structure (S3) ----

    /** The template's corner, size and what it holds, in absolute places. */
    private record Lines(BlockPos origin, BlockPos a, BlockPos b, BlockPos ropeEnd, BlockPos w1, BlockPos w2) {}

    private static final Vec3i SIZE = new Vec3i(6, 4, 3);

    /**
     * Two fence posts and a Thin Rope rising from one to the other, a banner, glow berries (lit) and a skull hung on it; a
     * bottle under a Thin Rope let down from a ceiling; two Drive Wheels with a belt between them. All in the arena's
     * lower part, from (0, 1, 0), six by four by three.
     */
    private static Lines lines(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = RopeTests.player(helper, 32);
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos a = origin.offset(1, 2, 0);
        BlockPos b = origin.offset(4, 3, 2);
        level.setBlockAndUpdate(a, Blocks.OAK_FENCE.defaultBlockState());
        level.setBlockAndUpdate(b, Blocks.OAK_FENCE.defaultBlockState());
        ItemStack rope = player.getMainHandItem();
        RopeSpans.tie(level, player, a, rope);
        RopeSpans.tie(level, player, b, rope);
        Vec3 side = helper.absoluteVec(new Vec3(3.5, 2, 6));
        level.addFreshEntity(HungItemEntity.onSpan(level, a, b, 0.2, new ItemStack(Items.BANNER.red()), side));
        level.addFreshEntity(RopeTests.bearing(HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(Items.GLOW_BERRIES))));
        level.addFreshEntity(HungItemEntity.onSpan(level, a, b, 0.8, new ItemStack(Items.SKELETON_SKULL), side));
        BlockPos ropeEnd = origin.offset(3, 2, 0);
        level.setBlockAndUpdate(ropeEnd.above(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(ropeEnd, ModBlocks.THIN_ROPE.get().defaultBlockState().setValue(ThinRopeBlock.ANCHORED, true));
        level.addFreshEntity(HungItemEntity.underRope(level, ropeEnd, new ItemStack(Items.GLASS_BOTTLE), 30.0F));
        BlockPos w1 = origin.offset(0, 3, 1);
        BlockPos w2 = origin.offset(5, 3, 1);
        for (BlockPos w : List.of(w1, w2)) {
            level.setBlockAndUpdate(w, ModOil.DRIVE_WHEEL.get().defaultBlockState().setValue(DriveWheelBlock.FACING, Direction.SOUTH));
        }
        var why = Belts.tie(level, (DriveWheelBlockEntity) level.getBlockEntity(w1), (DriveWheelBlockEntity) level.getBlockEntity(w2),
                BeltKind.ROPE, null, new ItemStack(ModItems.ROPE.get()));
        check(helper, why == null, "the belt is tied: " + (why == null ? "" : why.getString()));
        RopeKnotEntity knot = RopeKnotEntity.at(level, a);
        check(helper, knot != null && knot.ties(b), "the rope is strung");
        return new Lines(origin, a, b, ropeEnd, w1, w2);
    }

    /** What hangs in {@code box}, by what it is. */
    private static HungItemEntity hung(ServerLevel level, AABB box, net.minecraft.world.item.Item item) {
        List<HungItemEntity> found = level.getEntitiesOfClass(HungItemEntity.class, box, h -> !h.isRemoved() && h.item().is(item));
        return found.size() == 1 ? found.get(0) : null;
    }

    /** {@code yaw} as it comes out of a structure mirrored by {@code mirror} and then turned by {@code rotation}. */
    private static float turned(float yaw, Mirror mirror, Rotation rotation) {
        float mirrored = switch (mirror) {
            case LEFT_RIGHT -> 180.0F - yaw;
            case FRONT_BACK -> -yaw;
            default -> yaw;
        };
        return mirrored + switch (rotation) {
            case CLOCKWISE_90 -> 90.0F;
            case CLOCKWISE_180 -> 180.0F;
            case COUNTERCLOCKWISE_90 -> 270.0F;
            default -> 0.0F;
        };
    }

    /**
     * The lines above copied by a structure (a structure block, a datapack's or another mod's template - the zombie
     * world's town): fillFromWorld with its entities, then placeInWorld at {@code at} (in the arena's upper part),
     * mirrored and turned. The copy's rope runs between the copied posts, not back to the first ones; what hangs on it
     * hangs from the copied span, the berries' light where the copy's is, the bottle under the copied rope's end, each
     * facing as the copy turned it; the copied wheels are belted to each other, turned with the build. Two seconds on,
     * nothing has dropped and all of it still holds - the first build too.
     */
    private static void copied(GameTestHelper helper, Mirror mirror, Rotation rotation, BlockPos atRel) {
        ServerLevel level = helper.getLevel();
        Lines lines = lines(helper);
        helper.runAfterDelay(3, () -> { // the berries light the tick after they are hung
            BlockPos origin = lines.origin();
            BlockPos at = helper.absolutePos(atRel);
            AABB first = AABB.encapsulatingFullBlocks(origin, origin.offset(SIZE).offset(-1, -1, -1));
            HungItemEntity berries = hung(level, first, Items.GLOW_BERRIES);
            HungItemEntity banner = hung(level, first, Items.BANNER.red());
            HungItemEntity skull = hung(level, first, Items.SKELETON_SKULL);
            HungItemEntity bottle = hung(level, first, Items.GLASS_BOTTLE);
            check(helper, berries != null && banner != null && skull != null && bottle != null, "all four hang");
            BlockPos lit = berries.litAt();
            check(helper, lit != null, "the berries are lit");
            StructureTemplate template = new StructureTemplate();
            template.fillFromWorld(level, origin, SIZE, true, List.of());
            StructurePlaceSettings settings = new StructurePlaceSettings().setMirror(mirror).setRotation(rotation);
            template.placeInWorld(level, at, at, settings, level.getRandom(), Block.UPDATE_ALL);
            java.util.function.UnaryOperator<BlockPos> copy = pos -> StructureTemplate.transform(pos.subtract(origin), mirror, rotation, BlockPos.ZERO).offset(at);
            AABB copyBox = AABB.encapsulatingFullBlocks(copy.apply(origin), copy.apply(origin.offset(SIZE).offset(-1, -1, -1)));

            check(helper, level.getBlockState(copy.apply(lines.a())).is(Blocks.OAK_FENCE), "the copy's posts are set down");
            RopeKnotEntity knot = RopeKnotEntity.at(level, copy.apply(lines.a()));
            check(helper, knot != null && knot.spans().equals(List.of(copy.apply(lines.b()))), "the copied knot's rope runs to "
                    + (knot == null ? null : knot.spans()) + ", not to the copied post " + copy.apply(lines.b()) + " (the first post is " + lines.b() + ")");
            check(helper, RopeKnotEntity.at(level, copy.apply(lines.b())) != null, "the copied rope's far end is knotted on its post");

            for (HungItemEntity thing : List.of(berries, banner, skull)) {
                HungItemEntity twin = hung(level, copyBox, thing.item().getItem());
                check(helper, twin != null && copy.apply(lines.a()).equals(twin.spanFrom()) && copy.apply(lines.b()).equals(twin.spanTo()),
                        thing.item() + " copied hangs on " + (twin == null ? null : twin.spanFrom() + " to " + twin.spanTo())
                                + ", not on the copied span " + copy.apply(lines.a()) + " to " + copy.apply(lines.b()));
                float yaw = turned(thing.yaw(), mirror, rotation);
                check(helper, Mth.degreesDifferenceAbs(twin.yaw(), yaw) < 0.01F, thing.item() + " copied faces " + twin.yaw()
                        + ", not " + yaw + " (it faced " + thing.yaw() + ")");
            }
            HungItemEntity berriesCopy = hung(level, copyBox, Items.GLOW_BERRIES);
            check(helper, copy.apply(lit).equals(berriesCopy.litAt()) && level.getBlockState(berriesCopy.litAt()).is(ModRope.HUNG_LIGHT.get()),
                    "the copied berries' light is the copy's: " + berriesCopy.litAt() + ", not " + copy.apply(lit));
            float slope = ((HungBannerEntity) banner).slope();
            float slopeCopy = ((HungBannerEntity) hung(level, copyBox, Items.BANNER.red())).slope();
            check(helper, Math.abs(slopeCopy - (mirror == Mirror.NONE ? slope : -slope)) < 1.0E-4F && Math.abs(slope) > 0.01F,
                    "the copied banner leans as the copied rope rises across it: " + slopeCopy + " (it leant " + slope + ")");
            HungItemEntity bottleCopy = hung(level, copyBox, Items.GLASS_BOTTLE);
            Vec3 under = Vec3.atBottomCenterOf(copy.apply(lines.ropeEnd()));
            check(helper, bottleCopy != null && bottleCopy.hangPoint().distanceTo(under) < 1.0E-6, "the copied bottle hangs under the copied rope's end "
                    + under + ", not at " + (bottleCopy == null ? null : bottleCopy.hangPoint()));
            check(helper, Mth.degreesDifferenceAbs(bottleCopy.yaw(), turned(bottle.yaw(), mirror, rotation)) < 0.01F,
                    "the copied bottle faces as the copy turned it: " + bottleCopy.yaw());

            var w1 = (DriveWheelBlockEntity) level.getBlockEntity(copy.apply(lines.w1()));
            var w2 = (DriveWheelBlockEntity) level.getBlockEntity(copy.apply(lines.w2()));
            check(helper, w1 != null && w2 != null && w1.has(copy.apply(lines.w2())) && w2.has(copy.apply(lines.w1()))
                    && w1.belts().size() == 1 && w2.belts().size() == 1, "the copied wheels are belted to each other: "
                    + (w1 == null ? null : w1.belts()) + " / " + (w2 == null ? null : w2.belts()));
            check(helper, level.getBlockState(copy.apply(lines.w1())).getValue(DriveWheelBlock.FACING) == rotation.rotate(mirror.mirror(Direction.SOUTH)),
                    "the copied wheel turned with the build");

            helper.runAfterDelay(45, () -> {
                List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2));
                check(helper, dropped.isEmpty(), "nothing drops: " + dropped.stream().map(ItemEntity::getItem).toList());
                RopeKnotEntity still = RopeKnotEntity.at(level, copy.apply(lines.a()));
                check(helper, still != null && still.ties(copy.apply(lines.b())), "the copied rope still holds");
                check(helper, RopeKnotEntity.at(level, lines.a()) != null && RopeKnotEntity.at(level, lines.a()).ties(lines.b()), "and the first");
                for (var item : List.of(Items.GLOW_BERRIES, Items.BANNER.red(), Items.SKELETON_SKULL, Items.GLASS_BOTTLE)) {
                    check(helper, hung(level, copyBox, item) != null && hung(level, first, item) != null, item + " still hangs in both");
                }
                check(helper, w1.has(copy.apply(lines.w2())) && w2.has(copy.apply(lines.w1())), "the copied belt still runs");
                var f1 = (DriveWheelBlockEntity) level.getBlockEntity(lines.w1());
                check(helper, f1.has(lines.w2()) && f1.belts().size() == 1, "and the first: " + f1.belts());
                helper.succeed();
            });
        });
    }

    /**
     * A knot, a thing hung on a rope and a Drive Wheel saved before they kept where their lines run from their own place
     * (only the places themselves) load as they were; saved now, they load the same again.
     */
    private static void oldLines(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Lines lines = lines(helper);
        helper.runAfterDelay(3, () -> {
            AABB first = AABB.encapsulatingFullBlocks(lines.origin(), lines.origin().offset(SIZE).offset(-1, -1, -1));
            RopeKnotEntity knot = RopeKnotEntity.at(level, lines.a());
            HungItemEntity berries = hung(level, first, Items.GLOW_BERRIES);
            HungItemEntity bottle = hung(level, first, Items.GLASS_BOTTLE);
            var wheel = (DriveWheelBlockEntity) level.getBlockEntity(lines.w1());
            for (boolean old : new boolean[] {false, true}) {
                String as = old ? "saved as before" : "saved now";
                RopeKnotEntity knotAgain = new RopeKnotEntity(ModRope.ROPE_KNOT.get(), level);
                knotAgain.load(input(level, saved(level, knot::saveWithoutId), old));
                check(helper, knotAgain.getPos().equals(lines.a()) && knotAgain.spans().equals(List.of(lines.b())),
                        "a knot " + as + " loads with its rope to " + knotAgain.spans());
                HungItemEntity berriesAgain = new HungItemEntity(ModRope.HUNG_ITEM.get(), level);
                berriesAgain.load(input(level, saved(level, berries::saveWithoutId), old));
                check(helper, lines.a().equals(berriesAgain.spanFrom()) && lines.b().equals(berriesAgain.spanTo())
                        && berries.litAt().equals(berriesAgain.litAt()), "berries " + as + " load on their span, their light kept: "
                        + berriesAgain.spanFrom() + " " + berriesAgain.spanTo() + " " + berriesAgain.litAt());
                HungItemEntity bottleAgain = new HungItemEntity(ModRope.HUNG_ITEM.get(), level);
                bottleAgain.load(input(level, saved(level, bottle::saveWithoutId), old));
                check(helper, bottleAgain.hangPoint().distanceTo(bottle.hangPoint()) < 1.0E-6, "a bottle " + as + " loads under its rope's end");
                var wheelAgain = new DriveWheelBlockEntity(wheel.getBlockPos(), wheel.getBlockState());
                wheelAgain.loadWithComponents(input(level, saved(level, wheel::saveWithoutMetadata), old));
                check(helper, wheelAgain.belts().equals(wheel.belts()) && wheelAgain.has(lines.w2()), "a wheel " + as + " loads with its belt: "
                        + wheelAgain.belts());
            }
            helper.succeed();
        });
    }

    private static CompoundTag saved(ServerLevel level, Consumer<TagValueOutput> save) {
        try (var reporter = new ProblemReporter.ScopedCollector(LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, level.registryAccess());
            save.accept(output);
            return output.buildResult();
        }
    }

    /** {@code tag} to load; {@code old}: as written before, with none of the places kept relative to the thing's own. */
    private static net.minecraft.world.level.storage.ValueInput input(ServerLevel level, CompoundTag tag, boolean old) {
        CompoundTag copy = tag.copy();
        if (old) {
            copy.keySet().removeIf(key -> key.toLowerCase(java.util.Locale.ROOT).contains("offset"));
        }
        return TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), copy);
    }

    // ---- askings (N2, N3) ----

    /**
     * Power another mod puts into a cable whose receivers are all one owner's, no claim anywhere: asked once at each
     * new place it comes in (as that owner breaking the cable there), not once for every receiver - each asking is a
     * break event every listener hears (a logging mod wrote "[Nookworks] broke a cable" for each); again at the same
     * place, not at all.
     */
    private static void cableAskings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        BlockPos start = helper.absolutePos(new BlockPos(0, 2, 2));
        List<BlockPos> cables = new ArrayList<>();
        for (int x = 0; x <= 6; x++) {
            BlockPos at = start.offset(x, 0, 0);
            level.setBlockAndUpdate(at, ModBlocks.ENERGY_CABLE.get().defaultBlockState());
            cables.add(at);
        }
        List<CrusherBlockEntity> crushers = new ArrayList<>();
        for (int x = 0; x <= 6; x++) {
            BlockPos at = start.offset(x, 0, 1);
            level.setBlockAndUpdate(at, ModBlocks.CRUSHER.get().defaultBlockState());
            CrusherBlockEntity crusher = (CrusherBlockEntity) level.getBlockEntity(at);
            crusher.setOwner(owner);
            crushers.add(crusher);
        }
        join(level, cables.toArray(BlockPos[]::new));
        AtomicInteger asked = askings(helper);
        List<Integer> perPlace = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int before = asked.get();
            try (Transaction tx = Transaction.openRoot()) {
                EnergyCableBlock.networkHandler(level, cables.get(i * 3), null).insert(700, tx);
                tx.commit();
            }
            perPlace.add(asked.get() - before);
        }
        int before = asked.get();
        try (Transaction tx = Transaction.openRoot()) {
            EnergyCableBlock.networkHandler(level, cables.get(0), null).insert(700, tx);
            tx.commit();
        }
        int again = asked.get() - before;
        check(helper, perPlace.equals(List.of(1, 1, 1)) && again == 0, "power put into a cable at three places, for "
                + crushers.size() + " receivers of one owner, asked " + perPlace + " (once a place); again at the first: " + again);
        check(helper, crushers.stream().allMatch(c -> c.energyStored() > 0), "and every receiver is powered");
        helper.succeed();
    }

    /**
     * A receiver whose handler tells of no room at all - another mod's cable that keeps nothing and passes power on, here
     * a lone Energy Cable with nothing beyond it - is asked like any other before power goes into it: across a claim's
     * edge from a stranger's generator, refused. It was taken for full and handed out unasked.
     */
    private static void noRoomAsked(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID claimant = UUID.randomUUID();
        BlockPos generatorAt = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos lone = generatorAt.east();
        level.setBlockAndUpdate(generatorAt, ModBlocks.GENERATOR.get().defaultBlockState());
        level.setBlockAndUpdate(lone, ModBlocks.ENERGY_CABLE.get().defaultBlockState());
        var generator = (GeneratorBlockEntity) level.getBlockEntity(generatorAt);
        generator.setOwner(UUID.randomUUID()); // a stranger's
        MachineIO.Receiver receiver = new MachineIO.Receiver(lone, List.of(Direction.WEST));
        EnergyHandler told = MachineIO.handlerOf(level, receiver);
        check(helper, told != null && told.getCapacityAsLong() == 0, "the lone cable tells of no room: "
                + (told == null ? null : told.getCapacityAsLong()));
        claim(helper, Set.of(lone), Set.of(claimant));
        AtomicInteger asked = askings(helper);
        EnergyHandler handed = MachineIO.handlerOf(level, receiver, generatorAt, generator);
        check(helper, handed == null && asked.get() == 1, "a receiver telling of no room in the claim is handed power from a stranger's "
                + "generator outside: " + handed + ", asked " + asked.get() + " time(s)");
        helper.succeed();
    }
}
