package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import dev.nookworks.block.PlanterBlock;
import dev.nookworks.machine.PlanterBlockEntity;
import dev.nookworks.machine.PlanterBlockEntity.Reach;
import dev.nookworks.registry.ModBlocks;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import dev.nookworks.port.test.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import dev.nookworks.port.storage.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import dev.nookworks.port.storage.TagValueInput;
import dev.nookworks.port.storage.ValueIO;
import dev.nookworks.port.storage.Nbt;

/**
 * An add-on widens a Planter's field (the user, 2026-11-14: 「tec環境では加速ギアを入れると植え付け範囲と速度が拡張される、
 * みたいな挙動を入れたいな」 - nookTechnology's speed gears): {@link PlanterBlockEntity#addReachProvider}, the planting and the
 * outline both going by it, synced to the client.
 */
final class PlanterReachTests {
    private PlanterReachTests() {}

    /** What the test's provider widens, by a Planter's place in the world (each test's own). */
    private static final Map<BlockPos, Reach> WIDE = new ConcurrentHashMap<>();

    static void register() {
        PlanterBlockEntity.addReachProvider(planter -> WIDE.get(planter.getBlockPos()));
        NookworksGameTests.test("an_add_on_widens_a_planters_field_and_it_plants_what_it_shows", 200, PlanterReachTests::reach);
    }

    private static Set<BlockPos> relative(GameTestHelper helper, Iterable<BlockPos> spots) {
        Set<BlockPos> out = new HashSet<>();
        for (BlockPos spot : spots) {
            out.add(helper.relativePos(spot));
        }
        return out;
    }

    /**
     * A Planter facing south with farmland all round its field. No provider's word for it: three wide and five deep. Its
     * provider says one more each side and one more forward: five wide and six deep, in the update tag the client is
     * sent (and a copy told that tag has it); thirty seeds go in exactly there and nowhere else. Too much is kept to
     * fifteen by fifteen. The word taken back, it is three by five again by the next second's asking.
     */
    private static void reach(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 0);
        helper.setBlock(pos, ModBlocks.PLANTER.get().defaultBlockState().setValue(PlanterBlock.FACING, Direction.SOUTH));
        for (int x = 0; x <= 6; x++) {
            for (int z = 1; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.FARMLAND);
            }
        }
        PlanterBlockEntity planter = helper.getBlockEntity(pos, PlanterBlockEntity.class);
        BlockPos abs = helper.absolutePos(pos);
        var registries = helper.getLevel().registryAccess();
        check(helper, planter.reach().equals(Reach.NONE) && planter.spots().size() == 15,
                "no word for it: the field it starts with: " + planter.reach() + ", " + planter.spots().size());

        WIDE.put(abs, new Reach(1, 1));
        planter.refreshReach();
        check(helper, planter.reach().equals(new Reach(1, 1)), "widened by its provider: " + planter.reach());
        Set<BlockPos> field = new HashSet<>();
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 6; z++) {
                field.add(new BlockPos(x, 1, z));
            }
        }
        Set<BlockPos> spots = relative(helper, planter.spots());
        check(helper, spots.equals(field), "five wide and six deep: " + spots);

        CompoundTag tag = planter.getUpdateTag(registries);
        check(helper, Nbt.getIntOr(tag, "reach_side", 0) == 1 && Nbt.getIntOr(tag, "reach_forward", 0) == 1 && tag.size() == 2,
                "the client is sent its reach and nothing else: " + tag);
        PlanterBlockEntity told = new PlanterBlockEntity(abs, helper.getBlockState(pos));
        ValueIO.handleUpdateTag(told, TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
        check(helper, told.reach().equals(new Reach(1, 1)) && told.spots().size() == 30,
                "a client's copy told it outlines the same field: " + told.reach());

        planter.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 40));
        planter.energy().set(PlanterBlockEntity.CAPACITY);
        for (int i = 0; i < 30; i++) {
            check(helper, planter.plantOne(helper.getLevel()), "plant #" + (i + 1));
        }
        check(helper, !planter.plantOne(helper.getLevel()) && planter.getItem(0).getCount() == 10, "thirty, and no thirty-first");
        for (int x = 0; x <= 6; x++) {
            for (int z = 1; z <= 6; z++) {
                BlockPos at = new BlockPos(x, 1, z);
                check(helper, helper.getBlockState(at).is(Blocks.WHEAT) == field.contains(at), "wheat on the widened field and nowhere else: " + at);
            }
        }

        WIDE.put(abs, new Reach(20, 30));
        planter.refreshReach();
        check(helper, planter.reach().equals(new Reach(PlanterBlockEntity.MAX_EXTRA_SIDE, PlanterBlockEntity.MAX_EXTRA_FORWARD))
                && planter.spots().size() == 15 * 15, "kept to fifteen by fifteen: " + planter.reach());

        WIDE.remove(abs);
        helper.runAfterDelay(25, () -> {
            check(helper, planter.reach().equals(Reach.NONE) && planter.spots().size() == 15,
                    "the word taken back, asked again within the second: " + planter.reach());
            check(helper, planter.getUpdateTag(registries).isEmpty(), "and the client told nothing widens it");
            helper.succeed();
        });
    }
}
