package dev.nookworks.compat.create;

import org.jspecify.annotations.Nullable;

import com.simibubi.create.AllShapes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.AbstractShaftBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Retired. The first two rounds of the 1.21.1 port's Create part swapped a plain {@code create:shaft} against a Motor
 * for this block ({@code nookworks:motor_shaft}); a Create player's belt, casing and cogwheel took it for no shaft at
 * all. Nothing makes one any more: a Motor drives Create's own block where it stands ({@link CreateEnds}). It stays
 * registered (with Create) only so that a world holding one loads, and any one found is Create's plain shaft again at
 * its first tick, axis and water kept ({@link MotorShaftBlockEntity}). The design: tools/port1211/design_create.md.
 */
public class MotorShaftBlock extends AbstractShaftBlock {
    public MotorShaftBlock(Properties properties) {
        super(properties);
    }

    private static @Nullable Block plain;

    /** Create's plain shaft, by id (Create's own block entries are Registrate's). */
    public static Block plainShaft() {
        Block block = plain;
        if (block == null) {
            block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "shaft"));
            plain = block;
        }
        return block;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.SIX_VOXEL_POLE.get(state.getValue(AXIS)); // a plain shaft's
    }

    @Override
    public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
        return CreateCompat.MOTOR_SHAFT_ENTITY.get();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(plainShaft());
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(plainShaft());
    }
}
