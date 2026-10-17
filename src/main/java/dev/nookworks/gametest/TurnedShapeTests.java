package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.List;

import dev.nookworks.cooking.HandMachineBlock;
import dev.nookworks.label.LabelEntity;
import dev.nookworks.label.LabelItem;
import dev.nookworks.registry.ModKitchen;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Hand Quern's, Noodle Maker's and Oil Press's outlines turn with their models: each is written facing north and the
 * blockstate turns the model a quarter clockwise (seen from above) for each step round to east, south and west. Their
 * outlines stayed facing north, so a quern facing east had its spout's outline to the north, and a label stuck on the
 * spout of one copied a quarter turned by a structure had nothing behind it and dropped (r16 fix check 2, N3).
 */
final class TurnedShapeTests {
    private TurnedShapeTests() {}

    static void register() {
        test("hand_machines_and_the_oil_press_outlines_turn_with_their_models", TurnedShapeTests::outlinesTurn);
        test("labels_on_a_querns_spout_and_frame_stay_on_its_turned_copies", 40, TurnedShapeTests::labelOnTurnedQuern);
    }

    private static void outlinesTurn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        List<Block> blocks = List.of(ModKitchen.HAND_QUERN.get(), ModKitchen.NOODLE_MAKER.get(), ModOil.OIL_PRESS.get());
        for (Block block : blocks) {
            BlockState north = block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
            level.setBlock(pos, north, Block.UPDATE_CLIENTS);
            VoxelShape written = level.getBlockState(pos).getShape(level, pos);
            check(helper, !written.isEmpty(), block + " has an outline");
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                level.setBlock(pos, north.setValue(BlockStateProperties.HORIZONTAL_FACING, facing), Block.UPDATE_CLIENTS);
                VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
                VoxelShape expected = turned(written, facing);
                check(helper, !Shapes.joinIsNotEmpty(shape, expected, BooleanOp.NOT_SAME),
                        block + " facing " + facing + ": its outline is " + shape.bounds() + ", not " + expected.bounds()
                                + " (its north one turned as the model is)");
                if (block == ModKitchen.HAND_QUERN.get()) {
                    // the spout, 1 to 3 px up in the middle of the face it faces, reaches the edge of the block there
                    Vec3 spout = new Vec3(0.5, 2 / 16.0, 0.5).add(facing.getUnitVec3().scale(0.5 - 0.5 / 16));
                    check(helper, shape.toAabbs().stream().anyMatch(box -> box.contains(spout)),
                            "a quern facing " + facing + " has its spout's outline on that side: " + shape.toAabbs());
                }
            }
        }
        helper.succeed();
    }

    /** {@code north} turned to face {@code facing}, a quarter clockwise seen from above for each step from north. */
    private static VoxelShape turned(VoxelShape north, Direction facing) {
        int quarters = facing.get2DDataValue() - Direction.NORTH.get2DDataValue();
        VoxelShape out = Shapes.empty();
        for (AABB box : north.toAabbs()) {
            AABB b = box;
            for (int i = 0; i < ((quarters % 4) + 4) % 4; i++) {
                // clockwise from above: north (-z) goes to east (+x); x' = 1 - z, z' = x
                b = new AABB(1 - b.maxZ, b.minY, b.minX, 1 - b.minZ, b.maxY, b.maxX);
            }
            out = Shapes.or(out, Shapes.create(b));
        }
        return out;
    }

    /**
     * Labels on the spout and on an end of the frame of a quern facing north, the build copied turned each way: the
     * copied quern faces east, south or west, and each label is on the same part of it there, with that part behind it.
     * (A copy a quarter turned kept the spout's label by chance - the north-facing outline's frame end lay just behind
     * it - but turned half round the spout's label, and a quarter the frame end's, had nothing there and dropped.)
     */
    private static void labelOnTurnedQuern(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos quern = origin.offset(1, 0, 1);
        level.setBlockAndUpdate(quern, ModKitchen.HAND_QUERN.get().defaultBlockState().setValue(HandMachineBlock.FACING, Direction.NORTH));
        LabelEntity spout = LabelItem.place(level, quern, Direction.NORTH,
                new Vec3(quern.getX() + 0.5, quern.getY() + 2 / 16.0, quern.getZ()), new ItemStack(Items.WHEAT), false);
        LabelEntity frame = LabelItem.place(level, quern, Direction.EAST,
                new Vec3(quern.getX() + 1, quern.getY() + 8 / 16.0, quern.getZ() + 0.5), new ItemStack(Items.BREAD), false);
        check(helper, spout != null && frame != null, "labels go on the quern's spout and the end of its frame");
        level.addFreshEntity(spout);
        level.addFreshEntity(frame);
        List<LabelEntity> stuck = List.of(spout, frame);
        Vec3i size = new Vec3i(3, 1, 3);
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, origin, size, true, List.of());
        int k = 0;
        for (Rotation rotation : List.of(Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90)) {
            k++;
            BlockPos cell = origin.offset(0, 2 * k, 0); // above it: the arena is 7 square
            BlockPos a = StructureTemplate.transform(BlockPos.ZERO, Mirror.NONE, rotation, BlockPos.ZERO);
            BlockPos b = StructureTemplate.transform(new BlockPos(size.getX() - 1, 0, size.getZ() - 1), Mirror.NONE, rotation, BlockPos.ZERO);
            BlockPos at = cell.offset(-Math.min(a.getX(), b.getX()), 0, -Math.min(a.getZ(), b.getZ()));
            template.placeInWorld(level, at, at, new StructurePlaceSettings().setRotation(rotation), level.getRandom(), Block.UPDATE_ALL);
            BlockPos copied = StructureTemplate.transform(quern.subtract(origin), Mirror.NONE, rotation, BlockPos.ZERO).offset(at);
            Direction facing = rotation.rotate(Direction.NORTH);
            check(helper, level.getBlockState(copied).is(ModKitchen.HAND_QUERN.get())
                    && level.getBlockState(copied).getValue(HandMachineBlock.FACING) == facing, "the quern is copied facing " + facing);
            List<LabelEntity> twins = level.getEntitiesOfClass(LabelEntity.class, new AABB(copied).inflate(1, 0, 1), e -> !stuck.contains(e));
            check(helper, twins.size() == 2, rotation + ": " + twins.size() + " labels copied with the quern, not 2");
            for (LabelEntity label : stuck) {
                String what = label == spout ? "spout" : "frame end";
                Direction side = rotation.rotate(label.getDirection());
                LabelEntity twin = twins.stream().filter(t -> t.getDirection() == side).findFirst().orElse(null);
                check(helper, twin != null && twin.getPos().equals(copied), rotation + ": the " + what + "'s label is copied onto the quern, facing " + side);
                check(helper, twin.survives(), rotation + ": the label copied onto the " + what + " of a quern facing " + facing
                        + " has nothing behind it and drops");
            }
        }
        helper.succeed();
    }
}
