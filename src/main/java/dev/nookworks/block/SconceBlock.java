package dev.nookworks.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/**
 * A candle in an iron dish. Standing variant; see {@link WallSconceBlock}.
 * Placed unlit like a vanilla candle: light it with flint and steel, a fire charge or a burning arrow,
 * snuff it out with an empty hand.
 */
public class SconceBlock extends TorchBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final int LIGHT_LEVEL = 13;
    private static final VoxelShape SHAPE = Block.column(8.0, 0.0, 10.0);
    /** Flame height in blocks; must match the wick in models/block/sconce.json. */
    private static final double FLAME_Y = 10.0 / 16.0;

    public SconceBlock(BlockBehaviour.Properties properties) {
        super(ParticleTypes.SMALL_FLAME, properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            spawnFlame(level, flame(pos), random);
        }
    }

    @Override
    public @Nullable BlockState getToolModifiedState(BlockState state, UseOnContext context, ItemAbility ability, boolean simulate) {
        BlockState lit = lightWith(state, ability);
        return lit != null ? lit : super.getToolModifiedState(state, context, ability, simulate);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        return snuff(stack, hand, state, level, pos, player, flame(pos));
    }

    /**
     * A burning arrow lights it as it passes through: a sconce has nothing to hit (an arrow flies through it as through a
     * torch), so the hit below never comes from one.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects,
            boolean precise) {
        if (entity instanceof Projectile projectile) {
            igniteByProjectile(level, state, pos, projectile);
        }
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        igniteByProjectile(level, state, hit.getBlockPos(), projectile);
    }

    private static Vec3 flame(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + FLAME_Y, pos.getZ() + 0.5);
    }

    // ---- Shared by the standing and wall variants ----

    /** Flint and steel / fire charge lighting (NeoForge FIRESTARTER_LIGHT). Null if not applicable. */
    static @Nullable BlockState lightWith(BlockState state, ItemAbility ability) {
        return ability == ItemAbilities.FIRESTARTER_LIGHT && !state.getValue(LIT) ? state.setValue(LIT, true) : null;
    }

    /**
     * An empty main hand on a lit sconce puts it out, as on a vanilla candle. The main hand: with a pickaxe or an ingot in
     * it the game tries the empty off hand next, which put the flame out for a click that meant nothing (r7 review).
     */
    static InteractionResult snuff(ItemStack stack, InteractionHand hand, BlockState state, Level level, BlockPos pos, Player player, Vec3 flame) {
        if (!stack.isEmpty() || hand != InteractionHand.MAIN_HAND || !player.getAbilities().mayBuild || !state.getValue(LIT)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL_IMMEDIATE);
        level.addParticle(ParticleTypes.SMOKE, flame.x, flame.y, flame.z, 0.0, 0.1, 0.0);
        level.playSound(null, pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        return InteractionResult.SUCCESS;
    }

    static void igniteByProjectile(Level level, BlockState state, BlockPos pos, Projectile projectile) {
        if (!level.isClientSide() && projectile.isOnFire() && !state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL_IMMEDIATE);
        }
    }

    static void spawnFlame(Level level, Vec3 flame, RandomSource random) {
        level.addParticle(ParticleTypes.SMALL_FLAME, flame.x, flame.y, flame.z, 0.0, 0.0, 0.0);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMOKE, flame.x, flame.y + 0.05, flame.z, 0.0, 0.0, 0.0);
        }
    }
}
