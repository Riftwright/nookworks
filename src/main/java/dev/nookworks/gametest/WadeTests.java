package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.function.Supplier;

import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Bodies move through our fluids (WadeFluidType): NeoForge 26.3 left anyone in a modded fluid frozen, not even falling,
 * unless the fluid moved them itself (the user, 2026-11-04: 「植物油踏むと、全く動けなくなる」).
 */
final class WadeTests {
    private WadeTests() {}

    static void register() {
        test("a_body_sinks_and_moves_in_vegetable_oil", 60, helper -> wades(helper, ModOil.VEGETABLE_OIL::get));
        test("a_body_sinks_and_moves_in_biodiesel", 60, helper -> wades(helper, ModOil.BIODIESEL_BLOCK::get));
        test("a_body_sinks_and_moves_in_liquid_experience", 60, helper -> wades(helper, ModBlocks.LIQUID_EXPERIENCE::get));
        test("a_body_sinks_and_moves_in_mercury", 60, helper -> wades(helper, ModBlocks.MERCURY::get));
    }

    /** A pool three deep in a glass box; an armour stand let go near its top sinks, and pushed sideways it goes. */
    private static void wades(GameTestHelper helper, Supplier<? extends Block> fluid) {
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                for (int y = 1; y <= 4; y++) {
                    boolean wall = x == 0 || x == 4 || z == 0 || z == 4;
                    helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.GLASS.defaultBlockState()
                            : y <= 3 ? fluid.get().defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, new Vec3(1.5, 3.3, 2.5));
        stand.setPermanentlyInvulnerable(true); // mercury poisons and hurts what is in it
        double startY = stand.getY();
        helper.runAfterDelay(25, () -> {
            double sunk = startY - stand.getY();
            check(helper, sunk > 0.5, "an armour stand sinks in it (went down " + sunk + ")");
            double startX = stand.getX();
            stand.setDeltaMovement(0.3, 0.0, 0.0);
            helper.runAfterDelay(5, () -> {
                double moved = stand.getX() - startX;
                check(helper, moved > 0.2, "pushed sideways it moves (went " + moved + ")");
                helper.succeed();
            });
        });
    }
}
