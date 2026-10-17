package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.label.LabelEntity;
import dev.nookworks.label.LabelItem;
import dev.nookworks.photo.Photo;
import dev.nookworks.photo.PhotoPrints;
import dev.nookworks.poster.PosterEntity;
import dev.nookworks.poster.Posters;
import dev.nookworks.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Labels and posters copied by a structure (a structure block, a datapack's or another mod's template) - moved, turned
 * and mirrored - stay stuck where the copy puts them: a label on the copied crate, on the copied face and at the copied
 * spot of it (one off the middle of a face is off it the mirrored way in a mirrored copy), a poster on the copied wall,
 * one an even number of blocks across or high too. A label copied so was set on the block in front of its face, stuck
 * to nothing, and such a poster the next block along. (Thin Rope, what hangs on it and drive belts copied so are tried
 * by {@code R15CheckFourTests}.)
 */
final class StructureCopyTests {
    private StructureCopyTests() {}

    /** The build: a crate with a label on its front and one on its side, two posters on two walls. */
    private static final Vec3i SIZE = new Vec3i(6, 4, 6);

    static void register() {
        test("structure_copy_keeps_labels_and_posters_on", 40, helper -> copied(helper, Mirror.NONE, Rotation.NONE));
        test("structure_copy_turned_keeps_labels_and_posters_on", 40, helper -> copied(helper, Mirror.NONE, Rotation.CLOCKWISE_90));
        test("structure_copy_mirrored_keeps_labels_and_posters_on", 40, helper -> copied(helper, Mirror.LEFT_RIGHT, Rotation.NONE));
        test("structure_copy_mirrored_and_turned_keeps_labels_and_posters_on", 40,
                helper -> copied(helper, Mirror.FRONT_BACK, Rotation.COUNTERCLOCKWISE_90));
        test("structure_copy_mirrored_and_turned_round_keeps_labels_and_posters_on", 40,
                helper -> copied(helper, Mirror.LEFT_RIGHT, Rotation.CLOCKWISE_180));
        test("labels_and_posters_as_a_client_makes_them_are_on_their_block", StructureCopyTests::asClientMakesThem);
    }

    /**
     * A client makes a label or poster from the packet that brings it - its block's corner, before what it shows and
     * which way it faces come in the synced data after (as a summon at a block's place does too): it is on the block the
     * server's is on, and lies where the server's does. Set down as a structure's copy is, it went a block off - a label
     * a block before its crate, held to nothing in the air - seen so by a player coming back to a place whose chunks
     * stayed loaded, or joining a server later (r16 fix check 2, M1).
     */
    private static void asClientMakesThem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<HangingEntity> stuck = build(helper, helper.absolutePos(new BlockPos(0, 1, 0)));
        for (HangingEntity real : stuck) {
            ClientboundAddEntityPacket packet = new ClientboundAddEntityPacket(real, real.getDirection().get3DDataValue(), real.getPos());
            HangingEntity copy = (HangingEntity) real.getType().create(level, EntitySpawnReason.LOAD);
            copy.recreateFromPacket(packet);
            List<SynchedEntityData.DataValue<?>> values = real.getEntityData().getNonDefaultValues();
            if (values != null) {
                copy.getEntityData().assignValues(values);
            }
            String what = real.getType().toShortString() + " " + real.getDirection();
            check(helper, copy.getPos().equals(real.getPos()), what + " as a client makes it is on " + copy.getPos() + ", not on " + real.getPos());
            check(helper, copy.getBoundingBox().getCenter().distanceTo(real.getBoundingBox().getCenter()) < 1.0E-6,
                    what + " as a client makes it lies at " + copy.getBoundingBox() + ", not at " + real.getBoundingBox());
        }
        helper.succeed();
    }

    /** The build in the arena's lower part, from (0, 1, 0); what is stuck on it, labels first. */
    private static List<HangingEntity> build(GameTestHelper helper, BlockPos origin) {
        ServerLevel level = helper.getLevel();
        List<HangingEntity> stuck = new ArrayList<>();
        BlockPos crate = origin.offset(1, 0, 1);
        level.setBlockAndUpdate(crate, ModBlocks.CRATE.get().defaultBlockState());
        // off the middle of each face: a mirror moves them to its other side
        LabelEntity front = LabelItem.place(level, crate, Direction.NORTH,
                new Vec3(crate.getX() + 0.8, crate.getY() + 0.7, crate.getZ()), new ItemStack(Items.IRON_INGOT), false);
        LabelEntity side = LabelItem.place(level, crate, Direction.WEST,
                new Vec3(crate.getX(), crate.getY() + 0.3, crate.getZ() + 0.25), new ItemStack(Items.PAPER), false);
        check(helper, front != null && side != null, "the labels go on the crate");
        side.setText("AB");
        stuck.add(front);
        stuck.add(side);
        // a print two blocks by two on a wall to the south, a design one block square on a wall to the east
        for (BlockPos stone : BlockPos.betweenClosed(origin.offset(1, 0, 5), origin.offset(4, 3, 5))) {
            level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        }
        for (BlockPos stone : BlockPos.betweenClosed(origin.offset(5, 0, 1), origin.offset(5, 2, 3))) {
            level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        }
        PosterEntity print = new PosterEntity(level, origin.offset(3, 1, 4), Direction.NORTH, PhotoPrints.id(42, new Photo.Size(2, 2)));
        Identifier design = Posters.ids(level.registryAccess()).getFirst();
        PosterEntity small = new PosterEntity(level, origin.offset(4, 1, 2), Direction.WEST, design);
        check(helper, print.survives() && small.survives(), "the posters go on their walls");
        stuck.add(print);
        stuck.add(small);
        stuck.forEach(level::addFreshEntity);
        return stuck;
    }

    private static void copied(GameTestHelper helper, Mirror mirror, Rotation rotation) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
        List<HangingEntity> stuck = build(helper, origin);
        // the copy above, in the arena's upper part, wherever the turn and the mirror take it
        BlockPos a = StructureTemplate.transform(BlockPos.ZERO, mirror, rotation, BlockPos.ZERO);
        BlockPos b = StructureTemplate.transform(new BlockPos(SIZE.getX() - 1, 0, SIZE.getZ() - 1), mirror, rotation, BlockPos.ZERO);
        BlockPos at = helper.absolutePos(new BlockPos(1 - Math.min(a.getX(), b.getX()), 5, 1 - Math.min(a.getZ(), b.getZ())));
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, origin, SIZE, true, List.of());
        template.placeInWorld(level, at, at, new StructurePlaceSettings().setMirror(mirror).setRotation(rotation), level.getRandom(), Block.UPDATE_ALL);

        AABB copyBox = AABB.encapsulatingFullBlocks(at.offset(-SIZE.getX(), 0, -SIZE.getZ()), at.offset(SIZE.getX(), SIZE.getY(), SIZE.getZ()));
        List<HangingEntity> copies = level.getEntitiesOfClass(HangingEntity.class, copyBox, e -> !stuck.contains(e));
        check(helper, copies.size() == stuck.size(), "all " + stuck.size() + " are copied: " + copies);
        for (HangingEntity thing : stuck) {
            HangingEntity twin = copies.stream().filter(c -> same(thing, c)).findFirst().orElse(null);
            check(helper, twin != null, thing + " is copied");
            String what = thing.getType().toShortString() + (thing instanceof LabelEntity l ? " " + l.getDirection() : "");
            BlockPos onto = StructureTemplate.transform(thing.getPos().subtract(origin), mirror, rotation, BlockPos.ZERO).offset(at);
            // a label's block is the one it is stuck to; a poster's, for one an even number of blocks across, is either of
            // the two by its middle - which, depends on the way it faces, so a mirrored one's is the other
            check(helper, !(thing instanceof LabelEntity) || twin.getPos().equals(onto), what + " copied is on " + twin.getPos()
                    + ", not on the copied block " + onto);
            Direction facing = rotation.rotate(mirror.mirror(thing.getDirection()));
            check(helper, twin.getDirection() == facing, what + " copied faces " + twin.getDirection() + ", not " + facing);
            AABB box = thing.getBoundingBox().move(-origin.getX(), -origin.getY(), -origin.getZ());
            Vec3 c1 = StructureTemplate.transform(box.getMinPosition(), mirror, rotation, BlockPos.ZERO);
            Vec3 c2 = StructureTemplate.transform(box.getMaxPosition(), mirror, rotation, BlockPos.ZERO);
            AABB expected = new AABB(c1, c2).move(at.getX(), at.getY(), at.getZ());
            AABB found = twin.getBoundingBox();
            check(helper, Math.abs(found.minX - expected.minX) + Math.abs(found.minY - expected.minY) + Math.abs(found.minZ - expected.minZ)
                    + Math.abs(found.maxX - expected.maxX) + Math.abs(found.maxY - expected.maxY) + Math.abs(found.maxZ - expected.maxZ) < 1.0E-6,
                    what + " copied lies at " + found + ", not where the copy put it: " + expected);
            check(helper, twin.survives(), what + " copied stays: it has its block behind it");
            if (twin instanceof LabelEntity) {
                check(helper, level.getBlockState(twin.getPos()).is(ModBlocks.CRATE.get()), what + " copied is on the copied crate");
            }
        }
        helper.succeed();
    }

    /** Whether {@code copy} is a copy of {@code thing}: the same kind, showing the same. */
    private static boolean same(HangingEntity thing, HangingEntity copy) {
        if (thing instanceof LabelEntity label && copy instanceof LabelEntity other) {
            return label.text().equals(other.text()) && ItemStack.isSameItem(label.shows(), other.shows());
        }
        return thing instanceof PosterEntity poster && copy instanceof PosterEntity other
                && java.util.Objects.equals(poster.variantId(), other.variantId());
    }
}
