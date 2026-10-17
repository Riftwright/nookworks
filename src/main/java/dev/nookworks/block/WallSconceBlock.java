package dev.nookworks.block;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.ItemAbility;

/** Wall-mounted variant of {@link SconceBlock}. Model: models/block/wall_sconce.json (drawn facing east). */
public class WallSconceBlock extends WallTorchBlock {
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.boxZ(6.0, 3.0, 13.0, 9.0, 16.0));
    /** Candle centre is 4.5px from the wall, i.e. 3.5px from the block centre towards the wall. */
    private static final double WALL_OFFSET = 3.5 / 16.0;
    private static final double FLAME_Y = 13.5 / 16.0;

    public WallSconceBlock(BlockBehaviour.Properties properties) {
        super(ParticleTypes.SMALL_FLAME, properties);
        this.registerDefaultState(this.defaultBlockState().setValue(SconceBlock.LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SconceBlock.LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(SconceBlock.LIT)) {
            SconceBlock.spawnFlame(level, flame(state, pos), random);
        }
    }

    @Override
    public @Nullable BlockState getToolModifiedState(BlockState state, UseOnContext context, ItemAbility ability, boolean simulate) {
        BlockState lit = SconceBlock.lightWith(state, ability);
        return lit != null ? lit : super.getToolModifiedState(state, context, ability, simulate);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        return SconceBlock.snuff(stack, hand, state, level, pos, player, flame(state, pos));
    }

    /**
     * A burning arrow lights it as it passes through: a sconce has nothing to hit (an arrow flies through it as through a
     * torch), so the hit below never comes from one.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects,
            boolean precise) {
        if (entity instanceof Projectile projectile) {
            SconceBlock.igniteByProjectile(level, state, pos, projectile);
        }
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        SconceBlock.igniteByProjectile(level, state, hit.getBlockPos(), projectile);
    }

    private static Vec3 flame(BlockState state, BlockPos pos) {
        Direction towardsWall = state.getValue(FACING).getOpposite();
        return new Vec3(
                pos.getX() + 0.5 + WALL_OFFSET * towardsWall.getStepX(),
                pos.getY() + FLAME_Y,
                pos.getZ() + 0.5 + WALL_OFFSET * towardsWall.getStepZ());
    }
}
