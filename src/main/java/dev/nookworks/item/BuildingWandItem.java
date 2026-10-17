package dev.nookworks.item;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.nookworks.compat.FeatureGate;
import dev.nookworks.feature.Permission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Building Wand: right-click a block face and the same block spreads across the flat surface you clicked.
 * <p>
 * It only extends across the face that was clicked, and only over blocks identical to the one clicked, so it
 * fills a wall or a floor without wandering around a corner. Blocks come out of the player's inventory (a
 * creative player needs none), and it stops at {@link #MAX_BLOCKS} so one click stays something you can undo.
 */
public class BuildingWandItem extends Item {
    public static final int MAX_BLOCKS = 32;
    /** How many blocks the search may look at, and how far from the clicked block, whatever it finds. */
    public static final int MAX_VISITED = 256;
    public static final int MAX_REACH = 8;

    /**
     * The only state carried over from the block that was clicked: how it is set in the world.
     * <p>
     * Copying a whole {@link BlockState} would duplicate things that are half of something else or that mean
     * something: an extended piston head, the bottom of a door, a lit furnace, a waterlogged slab, a soil's goodness.
     * Starting from the block's default state and taking only how it sits keeps a log or a stair pointing the way
     * its neighbours do, and an upside-down stair or a top slab upside down, without carrying any of that across.
     * What the placed block holds comes from the item it is made of, as it would by hand ({@link #placing}).
     */
    private static final List<Property<?>> ORIENTATION = List.of(
            BlockStateProperties.AXIS, BlockStateProperties.HORIZONTAL_AXIS,
            BlockStateProperties.FACING, BlockStateProperties.HORIZONTAL_FACING,
            BlockStateProperties.ROTATION_16, BlockStateProperties.HALF, BlockStateProperties.SLAB_TYPE);

    public BuildingWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || !FeatureGate.wand()) {
            return InteractionResult.PASS;
        }
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockState clickedState = level.getBlockState(clicked);
        if (!canCopy(clickedState)) {
            return InteractionResult.PASS;
        }
        BlockState placing = toPlace(clickedState);
        List<BlockPos> targets = plan(level, clicked, face, clickedState);
        if (targets.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            int placed = 0;
            ItemStack wand = context.getItemInHand();
            // Each block is asked about on its own below, with its materials taken only once it stands. NeoForge
            // would otherwise also record them all and ask about the lot at the end, and a refusal there puts the
            // blocks back without the materials that came out of other inventory slots.
            boolean capturing = server.captureBlockSnapshots;
            server.captureBlockSnapshots = false;
            try {
            int cost = cost(placing);
            for (BlockPos target : targets) {
                int slot = find(player, placing);
                if (!player.hasInfiniteMaterials() && (slot < 0 || count(player, placing) < cost)) {
                    break;
                }
                // Made of that very item: a worn-out Nourished Soil is placed worn out, as it would be by hand.
                BlockState block = slot < 0 ? placing : placing(placing, player.getInventory().getNonEquipmentItems().get(slot));
                if (!place(server, player, target, face, block, wand)) {
                    continue; // refused by protection or the block itself; keep the materials
                }
                if (!player.hasInfiniteMaterials()) {
                    take(player, placing, slot, cost);
                }
                placed++;
            }
            } finally {
                server.captureBlockSnapshots = capturing;
            }
            if (placed > 0) {
                level.playSound(null, clicked, placing.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                wand.hurtAndBreak(placed, player, context.getHand());
            } else {
                level.playSound(null, clicked, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.6F, 1.0F);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Places one block, asking the same questions a hand would: may this player build here, does the block
     * survive, and does anything else object. Returns false when the block was not placed, so the caller can
     * keep the materials.
     */
    private static boolean place(ServerLevel level, Player player, BlockPos target, Direction face, BlockState placing,
            ItemStack wand) {
        if (!level.mayInteract(player, target) || !player.mayUseItemAt(target, face, wand)) {
            return false;
        }
        if (!placing.canSurvive(level, target)) {
            return false;
        }
        // No block inside a player or a mob, as a hand could not place one there either.
        if (!level.isUnobstructed(placing, target, CollisionContext.empty())) {
            return false;
        }
        // Asked first, with nothing set down yet: a claim mod that says no is heard before the block's neighbours are
        // told of it - an observer or a rail inside the claim was set off by a block put down and taken back. Placed
        // against the block in the row, as a hand clicking that face places it.
        if (!Permission.mayPlace(level, target, face, player)) {
            return false;
        }
        placing = asPlacedAt(level, target, placing);
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, target);
        if (!level.setBlock(target, placing, Block.UPDATE_ALL)) {
            return false;
        }
        if (EventHooks.onBlockPlace(player, snapshot, face)) {
            snapshot.restore();
            return false;
        }
        return true;
    }

    /**
     * {@code placing} as a hand would set it down at {@code target}: leaves kept (left at their default they decay where
     * they stand), in water if water is there, and joined to its neighbours - a fence, a pane, a wall.
     */
    private static BlockState asPlacedAt(ServerLevel level, BlockPos target, BlockState placing) {
        if (placing.hasProperty(BlockStateProperties.PERSISTENT)) {
            placing = placing.setValue(BlockStateProperties.PERSISTENT, true);
        }
        if (placing.hasProperty(BlockStateProperties.WATERLOGGED)) {
            // never a double slab, as by hand: one set down in water kept a source hidden in it
            boolean doubleSlab = placing.hasProperty(BlockStateProperties.SLAB_TYPE)
                    && placing.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE;
            placing = placing.setValue(BlockStateProperties.WATERLOGGED, !doubleSlab && level.getFluidState(target).is(Fluids.WATER));
        }
        return Block.updateFromNeighbourShapes(placing, level, target);
    }

    /** Blocks the wand will copy: no block entities, and nothing that is half of a taller thing. */
    public static boolean canCopy(BlockState state) {
        if (state.isAir() || state.hasBlockEntity() || state.getBlock().asItem() == Items.AIR) {
            return false;
        }
        // The block must be the one its item places. Several blocks share an item - the cauldrons all give a plain
        // cauldron - and copying a lava cauldron for the price of an empty one would make lava out of nothing.
        if (!(state.getBlock().asItem() instanceof BlockItem item) || item.getBlock() != state.getBlock()) {
            return false;
        }
        // A bucket of powder snow is a block item too, but placed by hand it gives the empty bucket back: the wand took
        // a whole bucket for each block.
        if (item instanceof DispensibleContainerItem) {
            return false;
        }
        return !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && !state.hasProperty(BlockStateProperties.BED_PART)
                && !state.hasProperty(BlockStateProperties.EXTENDED);
    }

    /** A fresh block of the same kind, turned the way the clicked one was. */
    public static BlockState toPlace(BlockState clicked) {
        BlockState fresh = clicked.getBlock().defaultBlockState();
        for (Property<?> property : ORIENTATION) {
            fresh = copy(clicked, fresh, property);
        }
        return fresh;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        return from.hasProperty(property) && to.hasProperty(property) ? to.setValue(property, from.getValue(property)) : to;
    }

    /**
     * Where the new blocks go: the free spaces in front of every block matching the one clicked, reached by
     * walking across the clicked face.
     * <p>
     * The walk is bounded on its own, not only by the blocks it finds: a stone cave wall with no free space in
     * front of it would otherwise have it crawl across the whole wall looking for 32. It looks at no more than
     * {@link #MAX_VISITED} blocks, none further than {@link #MAX_REACH} from the one clicked, and never into an
     * unloaded chunk.
     */
    public static List<BlockPos> plan(Level level, BlockPos clicked, Direction face, BlockState template) {
        List<BlockPos> targets = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> queue = new ArrayList<>();
        queue.add(clicked);
        seen.add(clicked);
        for (int i = 0; i < queue.size() && i < MAX_VISITED && targets.size() < MAX_BLOCKS; i++) {
            BlockPos current = queue.get(i);
            BlockPos front = current.relative(face);
            if (level.getBlockState(current) != template) {
                continue;
            }
            if (level.isLoaded(front) && level.getBlockState(front).canBeReplaced()) {
                targets.add(front);
            }
            for (Direction step : Direction.values()) {
                if (step.getAxis() == face.getAxis()) {
                    continue; // stay on the surface that was clicked
                }
                BlockPos next = current.relative(step);
                if (next.distChessboard(clicked) > MAX_REACH || !level.isLoaded(next)) {
                    continue;
                }
                if (seen.add(next) && level.getBlockState(next) == template) {
                    queue.add(next);
                }
            }
        }
        return targets;
    }

    /** How many items a block of {@code state} is made of: a double slab is two. */
    public static int cost(BlockState state) {
        return state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE)
                == SlabType.DOUBLE ? 2 : 1;
    }

    /** The block {@code placing} placed from {@code stack}: with whatever state the item carries (copy_state loot). */
    public static BlockState placing(BlockState placing, ItemStack stack) {
        var carried = stack.get(DataComponents.BLOCK_STATE);
        return carried == null ? placing : carried.apply(placing);
    }

    /** How many of these the player has. */
    private static int count(Player player, BlockState placing) {
        ItemStack wanted = new ItemStack(placing.getBlock());
        return player.getInventory().getNonEquipmentItems().stream().filter(s -> !s.isEmpty() && ItemStack.isSameItem(s, wanted))
                .mapToInt(ItemStack::getCount).sum();
    }

    /** Takes {@code cost} matching blocks, starting with the one in {@code slot}. */
    private static void take(Player player, BlockState placing, int slot, int cost) {
        var items = player.getInventory().getNonEquipmentItems();
        int left = cost - Math.min(cost, items.get(slot).getCount());
        items.get(slot).shrink(cost - left);
        while (left > 0) {
            int next = find(player, placing);
            if (next < 0) {
                return;
            }
            int take = Math.min(left, items.get(next).getCount());
            items.get(next).shrink(take);
            left -= take;
        }
    }

    private static int find(Player player, BlockState placing) {
        ItemStack wanted = new ItemStack(placing.getBlock());
        var items = player.getInventory().getNonEquipmentItems();
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty() && ItemStack.isSameItem(stack, wanted)) {
                return slot;
            }
        }
        return -1;
    }
}
