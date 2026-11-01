package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.lang.reflect.Method;
import java.util.UUID;

import com.mojang.logging.LogUtils;

import dev.nookworks.NookworksConfig;
import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.feature.Permission;
import dev.nookworks.fluid.CopperFittingBlock;
import dev.nookworks.fluid.CopperTankBlockEntity;
import dev.nookworks.fluid.LineContent;
import dev.nookworks.fluid.SightGlassBlockEntity;
import dev.nookworks.fluid.WindowFluid;
import dev.nookworks.machine.MachineIO;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.storage.StorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** Fixes of the r19 round (2026-11-01): nookTechnology's two finds for us (its NEEDS_FROM_NOOKWORKS.md, section 4). */
final class R19FixTests {
    private R19FixTests() {}

    static void register() {
        test("a_machine_closed_in_keeps_what_it_made", R19FixTests::closedInKeeps);
        test("a_barrel_too_full_to_break_is_still_its_owners_to_take_from", R19FixTests::tooFullBarrelIsNoClaim);
        test("a_copper_tank_tells_its_client_the_liquid_in_its_window", R19FixTests::windowTellsItsLiquid);
        test("a_sight_glass_tells_its_client_the_liquid_in_its_window", R19FixTests::glassTellsItsLiquid);
    }

    /**
     * Another mod's liquid in a Copper Tank (nookTechnology's coal tar) was drawn in the plain grey of "other", black
     * tar looking like an empty glass: the tank now tells its client which liquid it is, for the window to be tinted
     * its colour (WindowTint). Lava stands in for another mod's liquid here.
     */
    private static void windowTellsItsLiquid(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.COPPER_TANK.get());
        var tank = helper.getBlockEntity(pos, CopperTankBlockEntity.class);
        var registries = helper.getLevel().registryAccess();
        check(helper, !tank.getUpdateTag(registries).contains(WindowFluid.KEY) && tank.getUpdatePacket() != null,
                "empty, it tells of no liquid, and it is sent with its block's changes");
        tank.fluidHandler().set(0, FluidResource.of(Fluids.LAVA), CopperTankBlockEntity.CAPACITY);
        check(helper, helper.getBlockState(pos).getValue(LineContent.PROPERTY) == LineContent.OTHER, "the glass shows another liquid");
        CompoundTag tag = tank.getUpdateTag(registries);
        check(helper, tag.getStringOr(WindowFluid.KEY, "").equals("minecraft:lava"), "and tells its client which: " + tag);
        var client = new CopperTankBlockEntity(helper.absolutePos(pos), helper.getBlockState(pos));
        try (var reporter = new ProblemReporter.ScopedCollector(client.problemPath(), LogUtils.getLogger())) {
            client.handleUpdateTag(TagValueInput.create(reporter, registries, tag));
        }
        check(helper, client.windowFluid() == Fluids.LAVA && client.amount() == 0,
                "a client's tank, told, knows the liquid, and nothing else of it is touched");
        helper.succeed();
    }

    /**
     * The same for a Sight Glass, by what it shows. Our pipes carry only water, oil and biodiesel; another mod's liquid
     * reaches a glass by an add-on's pipes (nookTechnology lets its own in), so the glass is set to show lava here, as
     * its reading would. One other liquid after another, as high, leaves the block's state as it was: the client is told
     * all the same.
     */
    private static void glassTellsItsLiquid(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, ModBlocks.SIGHT_GLASS.get().defaultBlockState().setValue(CopperFittingBlock.AXIS, Direction.Axis.X));
        var glass = helper.getBlockEntity(pos, SightGlassBlockEntity.class);
        var registries = helper.getLevel().registryAccess();
        check(helper, !glass.getUpdateTag(registries).contains(WindowFluid.KEY) && glass.getUpdatePacket() != null,
                "empty, it tells of no liquid, and it is sent with its block's changes");
        Method show;
        try {
            show = SightGlassBlockEntity.class.getDeclaredMethod("show", int.class, Fluid.class);
            show.setAccessible(true);
            show.invoke(glass, 1_000, Fluids.LAVA);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        BlockState shown = helper.getBlockState(pos);
        check(helper, shown.getValue(LineContent.PROPERTY) == LineContent.OTHER, "the glass shows another liquid");
        CompoundTag tag = glass.getUpdateTag(registries);
        check(helper, tag.getStringOr(WindowFluid.KEY, "").equals("minecraft:lava"), "and tells its client which: " + tag);
        var client = new SightGlassBlockEntity(helper.absolutePos(pos), shown);
        try (var reporter = new ProblemReporter.ScopedCollector(client.problemPath(), LogUtils.getLogger())) {
            client.handleUpdateTag(TagValueInput.create(reporter, registries, tag));
        }
        check(helper, client.windowFluid() == Fluids.LAVA, "a client's glass, told, knows the liquid");
        try {
            show.invoke(glass, 1_000, ExperienceFluids.SOURCE.get());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        check(helper, helper.getBlockState(pos) == shown, "another liquid as high leaves the state as it was");
        check(helper, glass.getUpdateTag(registries).getStringOr(WindowFluid.KEY, "").equals("nookworks:liquid_experience"),
                "and the glass tells its client the new one: " + glass.getUpdateTag(registries));
        helper.succeed();
    }

    /**
     * A machine with no container under it drops what it makes below, else on top: with stone under it and over it
     * too, it set the item down inside the stone on top, which pushed it out wherever it could go. Now it drops it
     * beside itself, on the first side open; closed in on every side, it keeps it; with the top open, it drops it there.
     */
    private static void closedInKeeps(GameTestHelper helper) {
        BlockPos machine = new BlockPos(2, 2, 2);
        helper.setBlock(machine, Blocks.SMOOTH_STONE);
        helper.setBlock(machine.below(), Blocks.STONE);
        helper.setBlock(machine.above(), Blocks.STONE);
        BlockPos at = helper.absolutePos(machine);
        AABB around = new AABB(at).inflate(2);
        var level = helper.getLevel();
        ItemStack rest = MachineIO.deliverBelow(level, at, new ItemStack(Items.FLINT, 3));
        var dropped = level.getEntitiesOfClass(ItemEntity.class, around);
        check(helper, rest.isEmpty() && dropped.size() == 1 && level.getBlockState(dropped.get(0).blockPosition()).isAir()
                && dropped.get(0).blockPosition().getY() == at.getY(), "with a side open, it drops it beside itself, not in the stone: " + dropped);
        dropped.forEach(ItemEntity::discard);
        for (var side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            helper.setBlock(machine.relative(side), Blocks.STONE);
        }
        rest = MachineIO.deliverBelow(level, at, new ItemStack(Items.FLINT, 3));
        check(helper, rest.getCount() == 3 && level.getEntitiesOfClass(ItemEntity.class, around).isEmpty(),
                "closed in on every side, it keeps all of it: " + rest);
        helper.setBlock(machine.above(), Blocks.AIR);
        rest = MachineIO.deliverBelow(level, at, new ItemStack(Items.FLINT, 3));
        dropped = level.getEntitiesOfClass(ItemEntity.class, around);
        check(helper, rest.isEmpty() && dropped.size() == 1 && dropped.get(0).getY() > at.getY() + 1,
                "with the top open, it drops it on top: " + dropped);
        dropped.forEach(ItemEntity::discard);
        helper.succeed();
    }

    /**
     * A Storage Barrel too full to break refuses its break event, to a machine asking too; but a block of ours asking
     * whose it is, to take from it or put into it (DrawRights), is not refused for its size: only claims answer that.
     */
    private static void tooFullBarrelIsNoClaim(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModBlocks.IRON_STORAGE.get());
        StorageBlockEntity barrel = helper.getBlockEntity(pos, StorageBlockEntity.class);
        barrel.inventory().set(0, ItemResource.of(Items.STONE), 64);
        var limit = NookworksConfig.STORAGE_BREAK_LIMIT;
        int was = limit.getAsInt();
        limit.set(0);
        limit.clearCache();
        try {
            check(helper, barrel.tooFullToBreak(), "a stack in it is too many, with the limit at nought");
            var level = helper.getLevel();
            var actor = Permission.actor(level, UUID.fromString("00000000-0000-0000-0000-00000000a19f"));
            BlockPos at = helper.absolutePos(pos);
            check(helper, !Permission.mayBreak(level, at, actor), "no one may break it");
            check(helper, Permission.mayTake(level, at, actor), "but its size keeps nothing from taking from it");
            check(helper, !Permission.taking() && !Permission.asking(), "and the asking is over");
        } finally {
            limit.set(was);
            limit.clearCache();
        }
        helper.succeed();
    }
}
