package dev.nookworks.item;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.recycling.CorrugatedSheetBlock;
import dev.nookworks.block.PipeSides;
import dev.nookworks.fluid.FluidPipeBlockEntity;
import dev.nookworks.furniture.BenchBlock;
import dev.nookworks.furniture.DeskBlock;
import dev.nookworks.furniture.WallShelfBlock;
import dev.nookworks.feature.Permission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

/**
 * Wrench: right-click a block to turn it.
 * <p>
 * Only a block that faces some way turns: one with a facing, an axis, a sign's sixteen steps or a crafter's
 * orientation. A fence, a pane, a wall or redstone dust has none - vanilla's rotation would only swap its joins round,
 * which then no longer meet its neighbours - so it is left alone, as is anything in {@link #IGNORES} (dripstone, whose
 * point would turn to the wrong end; portal frames and portals; dice, which lie as their roll left them, whatever their
 * facing says). A game master's block (a command block) turns only for a player who may use such blocks.
 * <p>
 * It asks the block to rotate itself first, which is what vanilla uses for structure blocks and what mods
 * implement for their own machines, so anything that can be rotated at all responds. Only when that changes
 * nothing does it fall back to stepping the block's own facing property, which covers the few blocks that
 * never bothered to implement rotation. Sneaking turns the other way, step for step - but on a Corrugated Sheet it
 * moves the sheet between the face it is set on and the middle of its block, where a glass pane stands. A facing that can point up or
 * down (a log, a hopper, a piston) goes round every way: the sides clockwise, then down and up.
 * <p>
 * On vanilla's blocks and ours it works before the block's own right-click ({@code onItemUseFirst}), or a machine or a
 * chest would open its screen instead of turning; a block it cannot turn is used as it would be with anything else in
 * hand. Another mod's block is let have the click first - the Wrench is in {@code c:tools/wrench}, and a mod's machine
 * may take a wrench to dismantle or set itself up - and is turned only if it does nothing with it. Sneaking too (the
 * user, 2026-10-23), as with that mod's own wrench: its use with the Wrench in hand is asked first (a sneak-use that
 * picks a machine up, say), and if it takes none the block is turned back a step - its empty-hand use, which would
 * open its screen, is not asked, vanilla skipping it for a sneak-use too. The block is asked to turn with its place in
 * the world, for a mod whose block turns more than itself.
 */
public class WrenchItem extends Item {
    /** Blocks the Wrench leaves alone although they face some way. Packs may add their own. */
    public static final TagKey<Block> IGNORES = TagKey.create(
            Registries.BLOCK, Nookworks.id("wrench_ignores"));

    public WrenchItem(Properties properties) {
        super(properties);
    }

    /** The side a bench, desk or wall shelf at {@code pos} meets another of its kind nearer {@code hit}, or null. */
    private static @Nullable Direction furnitureSide(Level level, BlockPos pos, BlockState state, Vec3 hit) {
        if (state.getBlock() instanceof BenchBlock) {
            return BenchBlock.apartSide(level, pos, state, hit);
        }
        if (state.getBlock() instanceof DeskBlock) {
            return DeskBlock.apartSide(level, pos, state, hit);
        }
        return state.getBlock() instanceof WallShelfBlock ? WallShelfBlock.apartSide(level, pos, state, hit) : null;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        boolean backwards = player != null && player.isShiftKeyDown();

        // Pipes and cables: sneak-use switches the side you point at off, or back on - and a fluid pipe's side against a
        // tank or machine goes joined, pulling, off (FluidPipeBlockEntity.cycle).
        if (backwards && PipeSides.isPipe(state)) {
            if (player != null && (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()))) {
                return InteractionResult.FAIL;
            }
            if (level instanceof ServerLevel server) {
                Direction side = PipeSides.sideFromHit(pos, context.getClickLocation(), context.getClickedFace());
                BlockPos next = pos.relative(side);
                if (player != null && server.getBlockState(next).getBlock() == state.getBlock()
                        && !Permission.mayPlace(server, next, side, player)) {
                    return InteractionResult.FAIL; // the pipe it joins is switched with it: asked as the clicked one was, against it
                }
                FluidPipeBlockEntity.SideSetting now = level.getBlockEntity(pos) instanceof FluidPipeBlockEntity pipe
                        ? pipe.cycle(server, side)
                        : PipeSides.toggle(level, pos, state, side) ? FluidPipeBlockEntity.SideSetting.OFF : FluidPipeBlockEntity.SideSetting.JOINED;
                level.playSound(null, pos, now == FluidPipeBlockEntity.SideSetting.OFF ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.IRON_TRAPDOOR_OPEN,
                        SoundSource.BLOCKS, 0.6F, now == FluidPipeBlockEntity.SideSetting.PULLING ? 1.0F : 1.4F);
                if (player != null) {
                    player.sendOverlayMessage(Component.translatable(switch (now) {
                        case OFF -> "message.nookworks.wrench.side_off";
                        case PULLING -> "message.nookworks.wrench.side_pull";
                        case JOINED -> "message.nookworks.wrench.side_on";
                    }, Component.translatable("direction.nookworks." + side.getSerializedName())));
                    context.getItemInHand().hurtAndBreak(1, player, context.getHand());
                }
            }
            return InteractionResult.SUCCESS;
        }

        // Corrugated sheets: sneak-use moves one between the face it is set on and the middle of its block.
        if (backwards && state.getBlock() instanceof CorrugatedSheetBlock && !state.is(IGNORES)) {
            if (player != null && (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()))) {
                return InteractionResult.FAIL;
            }
            if (level instanceof ServerLevel) {
                BlockState moved = state.cycle(CorrugatedSheetBlock.CENTERED);
                level.setBlock(pos, moved, Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 1.2F);
                if (player != null) {
                    player.sendOverlayMessage(Component.translatable(moved.getValue(CorrugatedSheetBlock.CENTERED)
                            ? "message.nookworks.wrench.sheet_centered" : "message.nookworks.wrench.sheet_against"));
                    context.getItemInHand().hurtAndBreak(1, player, context.getHand());
                }
            }
            return InteractionResult.SUCCESS;
        }

        // Benches, desks and wall shelves: sneak-use on the end (a desk's edge) next to another keeps the two apart, or
        // joins them again (the user, 2026-10-23: 「つなげたくないときもある」); a lone one turns back as anything else.
        Direction apartSide = backwards ? furnitureSide(level, pos, state, context.getClickLocation()) : null;
        if (apartSide != null) {
            BlockPos next = pos.relative(apartSide);
            if (player != null && (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()))) {
                return InteractionResult.FAIL;
            }
            if (level instanceof ServerLevel server) {
                if (player != null && !Permission.mayPlace(server, next, apartSide, player)) {
                    return InteractionResult.FAIL; // the other one changes with it: asked as the clicked one was
                }
                boolean apart = state.getBlock() instanceof BenchBlock ? BenchBlock.toggleApart(level, pos, state, apartSide)
                        : state.getBlock() instanceof DeskBlock ? DeskBlock.toggleApart(level, pos, state, apartSide)
                        : WallShelfBlock.toggleApart(level, pos, state, apartSide);
                level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, apart ? 0.8F : 1.2F);
                if (player != null) {
                    player.sendOverlayMessage(Component.translatable(apart ? "message.nookworks.wrench.furniture_apart"
                            : "message.nookworks.wrench.furniture_joined"));
                    context.getItemInHand().hurtAndBreak(1, player, context.getHand());
                }
            }
            return InteractionResult.SUCCESS;
        }

        // A pipe used with the wrench (not sneaking) is flushed: what it holds is let out, so a line can change fluids.
        if (!backwards && level.getBlockEntity(pos) instanceof FluidPipeBlockEntity pipe) {
            if (pipe.stored() <= 0) {
                return InteractionResult.PASS;
            }
            if (player != null && (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()))) {
                return InteractionResult.FAIL;
            }
            if (level instanceof ServerLevel server) {
                var was = pipe.flush(server);
                if (player != null) {
                    player.sendOverlayMessage(Component.translatable("message.nookworks.wrench.flushed",
                            was.getHoverName(), was.getAmount()));
                }
            }
            return InteractionResult.SUCCESS;
        }

        if (!ownOrVanilla(state)) {
            if (backwards && player != null) {
                // sneaking, vanilla skips a block's own use and goes to the item: asked here first, as its own wrench would be
                InteractionResult own = state.useItemOn(stack, level, player, context.getHand(),
                        new BlockHitResult(context.getClickLocation(), context.getClickedFace(), pos, context.isInside()));
                if (own.consumesAction()) {
                    return own;
                }
                return turnAt(context, state, true);
            }
            return InteractionResult.PASS; // another mod's block: its own use first; turned in useOn if it lets the click by
        }
        return turnAt(context, state, backwards);
    }

    /** Another mod's block that let the click by: turned now. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        if (ownOrVanilla(state)) {
            return InteractionResult.PASS; // already had its turn, first
        }
        return turnAt(context, state, context.getPlayer() != null && context.getPlayer().isShiftKeyDown());
    }

    private static boolean ownOrVanilla(BlockState state) {
        String namespace = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace();
        return namespace.equals("minecraft") || namespace.equals(Nookworks.MODID);
    }

    private static InteractionResult turnAt(UseOnContext context, BlockState state, boolean backwards) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        BlockState turned = turn(level, pos, state, backwards);
        if (turned == state) {
            return InteractionResult.PASS;
        }
        if (!turned.canSurvive(level, pos)) {
            return InteractionResult.FAIL; // a torch, ladder or button turned away from its wall would hang in the air
        }
        // Fitted to its neighbours as it now lies, as if set down so: a stair's corner, a gate's dip between walls, a
        // fitting's two ends. Setting it updates only the neighbours, and one that does not change never tells it, so a
        // turned stair stayed a corner and a valve turned into a line stayed cut off from the tank beside it (r7 review).
        turned = Block.updateFromNeighbourShapes(turned, level, pos);
        if (!turned.is(state.getBlock())) {
            return InteractionResult.FAIL; // fitted so, it would be gone (as what cannot stand is): left as it was
        }
        // Turning a block is editing it, so ask the same questions placing one would: adventure mode, spawn
        // protection and claim mods all hang off these.
        if (player != null && (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()))) {
            return InteractionResult.FAIL;
        }
        // a command block, a jigsaw or a structure block is a game master's to turn, as to edit: vanilla lets nobody else
        if (state.getBlock() instanceof net.minecraft.world.level.block.GameMasterBlock && (player == null || !player.canUseGameMasterBlocks())) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            level.setBlockAndUpdate(pos, turned);
            // and whatever holds what it offers a side looks again: a tank kept pouring into a sight glass turned on its side
            level.invalidateCapabilities(pos);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6F, 1.6F);
            if (player != null) {
                context.getItemInHand().hurtAndBreak(1, player, context.getHand());
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** The turned state, or the same state when the block has nothing to turn. */
    public static BlockState turn(BlockState state, boolean backwards) {
        return turn(null, null, state, backwards);
    }

    /** The same, the block asked with its place in the world when there is one (NeoForge's level-aware rotate). */
    public static BlockState turn(@Nullable Level level, @Nullable BlockPos pos,
            BlockState state, boolean backwards) {
        if (!turnable(state)) {
            return state;
        }
        Rotation rotation = backwards ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90;
        Property<?> upright = upright(state);
        if (upright != null) {
            return round(level, pos, state, upright, rotation, backwards);
        }
        BlockState rotated = level != null && pos != null ? state.rotate(level, pos, rotation) : state.rotate(rotation);
        if (rotated != state) {
            return rotated;
        }
        // A block that never implemented rotation: step its facing by hand.
        for (Property<?> property : state.getProperties()) {
            if (property.getValueClass() == Direction.class
                    || property == BlockStateProperties.AXIS || property == BlockStateProperties.HORIZONTAL_AXIS) {
                return cycle(state, property, backwards);
            }
        }
        return state;
    }

    /**
     * Blocks that are one half of a pair turn only one half, and the pair comes apart: a door with its top
     * facing one way and its bottom another, a bed split in two, a double chest that stops being one, a piston
     * whose head is left behind. Those are left alone.
     */
    public static boolean turnable(BlockState state) {
        if (state.is(IGNORES) || !facesSomeWay(state)) {
            return false;
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || state.hasProperty(BlockStateProperties.BED_PART)
                || state.is(Blocks.PISTON_HEAD)
                || state.is(Blocks.MOVING_PISTON)) {
            return false;
        }
        if (state.hasProperty(BlockStateProperties.EXTENDED) && state.getValue(BlockStateProperties.EXTENDED)) {
            return false;
        }
        return !state.hasProperty(BlockStateProperties.CHEST_TYPE)
                || state.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.SINGLE;
    }

    /** Whether the block has a way it faces: a direction, an axis, a sign's rotation or a crafter's orientation. */
    public static boolean facesSomeWay(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property.getValueClass() == Direction.class || property == BlockStateProperties.AXIS
                    || property == BlockStateProperties.HORIZONTAL_AXIS || property == BlockStateProperties.ROTATION_16
                    || property == BlockStateProperties.ORIENTATION) {
                return true;
            }
        }
        return false;
    }

    /** The way round a facing that can point up or down: the sides as a clockwise quarter turn takes them, then down, up. */
    private static final List<Direction> ROUND = List.of(
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN, Direction.UP);
    /** And round an axis: x, then z (its quarter turn), then upright. */
    private static final List<Direction.Axis> AXIS_ROUND = List.of(Direction.Axis.X, Direction.Axis.Z, Direction.Axis.Y);

    /** The block's facing or axis when it can point up or down - a log, a hopper, a piston - else null. */
    private static @Nullable Property<?> upright(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property.getValueClass() == Direction.class && (property.getPossibleValues().contains(Direction.UP)
                    || property.getPossibleValues().contains(Direction.DOWN))
                    || property.getValueClass() == Direction.Axis.class && property.getPossibleValues().contains(Direction.Axis.Y)) {
                return property;
            }
        }
        return null;
    }

    /**
     * {@code state} a step on round every way {@code property} can point, or a step back when sneaking. A quarter turn
     * leaves up and down as they are and only goes round the sides, so a log once laid down never stood up again, nor
     * a hopper pointed down again (r7 review). A step round the sides is still the block's own turn, for a block that
     * turns more than this one property.
     */
    private static <T extends Comparable<T>> BlockState round(@Nullable Level level, @Nullable BlockPos pos, BlockState state,
            Property<T> property, Rotation rotation, boolean backwards) {
        List<T> round = new ArrayList<>();
        for (Object way : property.getValueClass() == Direction.class ? ROUND : AXIS_ROUND) {
            T value = property.getValueClass().cast(way);
            if (property.getPossibleValues().contains(value)) {
                round.add(value);
            }
        }
        T now = state.getValue(property);
        T next = round.get(Math.floorMod(round.indexOf(now) + (backwards ? -1 : 1), round.size()));
        if (sideways(now) && sideways(next)) {
            BlockState rotated = level != null && pos != null ? state.rotate(level, pos, rotation) : state.rotate(rotation);
            if (rotated.hasProperty(property) && rotated.getValue(property).equals(next)) {
                return rotated;
            }
        }
        return state.setValue(property, next);
    }

    private static boolean sideways(Object way) {
        return way instanceof Direction direction ? direction.getAxis().isHorizontal()
                : way instanceof Direction.Axis axis && axis.isHorizontal();
    }

    private static <T extends Comparable<T>> BlockState cycle(BlockState state, Property<T> property, boolean backwards) {
        var values = List.copyOf(property.getPossibleValues());
        int index = values.indexOf(state.getValue(property));
        int next = Math.floorMod(index + (backwards ? -1 : 1), values.size());
        return state.setValue(property, values.get(next));
    }

}
