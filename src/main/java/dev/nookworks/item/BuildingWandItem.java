package dev.nookworks.item;

import dev.nookworks.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.jspecify.annotations.Nullable;

import dev.nookworks.compat.FeatureGate;
import dev.nookworks.feature.Permission;
import dev.nookworks.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
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
 * <p>
 * Two items are made of this class (the user, 2026-11-07). The Building Wand does only that, as it always did, with two
 * helpers: a sneaking click at the air takes its last use back, and an outline shows what a click would do. The
 * Reinforced Building Wand, made from it with velarium, also has modes, stepped by scrolling while sneaking
 * ({@link WandMode}): that one; replacing the surface clicked with the block in the other hand (an ordinary full block,
 * never over ores or blocks harder than deepslate's - {@link WandWork#replaceRefusal}); and a box between two clicked
 * corners, filled whole, its walls or its edges ({@link WandBox}). The modes were first tried on the plain wand and
 * found too strong there. The work is {@link WandWork}'s.
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

    /** Whether this wand has modes: the Reinforced Building Wand; the plain one only spreads. */
    private final boolean modes;

    public BuildingWandItem(Properties properties) {
        this(properties, false);
    }

    public BuildingWandItem(Properties properties, boolean modes) {
        super(properties);
        this.modes = modes;
    }

    /** Whether {@code stack} is a wand with modes (the Reinforced Building Wand). */
    public static boolean hasModes(ItemStack stack) {
        return stack.getItem() instanceof BuildingWandItem wand && wand.modes;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || !FeatureGate.wand()) {
            return InteractionResult.PASS;
        }
        ItemStack wand = context.getItemInHand();
        WandMode mode = mode(wand);
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockState clickedState = level.getBlockState(clicked);
        if (mode.box()) {
            if (level instanceof ServerLevel server) {
                corner(server, player, wand, mode, clicked, face, clickedState);
            }
            return InteractionResult.SUCCESS;
        }
        if (mode == WandMode.REPLACE) {
            if (level instanceof ServerLevel server) {
                // as for spreading below: each block asked about on its own, not all again at the end (r1 review)
                boolean capturing = server.captureBlockSnapshots;
                server.captureBlockSnapshots = false;
                try {
                    WandWork.replace(server, player, clicked, face, wand);
                } finally {
                    server.captureBlockSnapshots = capturing;
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (!canCopy(clickedState)) {
            return InteractionResult.PASS;
        }
        BlockState placing = toPlace(clickedState);
        List<BlockPos> targets = plan(level, clicked, face, clickedState);
        if (targets.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            // Each block is asked about on its own (WandWork.placeOne), with its materials taken only once it stands.
            // NeoForge would otherwise also record them all and ask about the lot at the end, and a refusal there puts
            // the blocks back without the materials that came out of other inventory slots.
            boolean capturing = server.captureBlockSnapshots;
            server.captureBlockSnapshots = false;
            try {
                WandWork.spread(server, player, clicked, face, placing, targets, wand);
            } finally {
                server.captureBlockSnapshots = capturing;
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * A box mode's click on a block: the first corner (the square in front of the face clicked, where a block put
     * against it would go), then the second, then - a box marked out - fills it. A first corner in another world, or
     * a box too big, starts over.
     */
    private static void corner(ServerLevel level, Player player, ItemStack wand, WandMode mode, BlockPos clicked,
            Direction face, BlockState clickedState) {
        BlockPos at = clicked.relative(face);
        WandBox box = wand.get(ModDataComponents.WAND_BOX.get());
        if (box != null && box.first().dimension() != level.dimension()) {
            box = null;
        }
        if (box == null) {
            BlockState template = fillBlock(player, canCopy(clickedState) ? toPlace(clickedState) : null);
            if (template == null) {
                WandWork.say(player, "need_block");
                return;
            }
            wand.set(ModDataComponents.WAND_BOX.get(), new WandBox(GlobalPos.of(level.dimension(), at), Optional.empty(), template));
            WandWork.say(player, "first_corner");
            mark(player, 1.0F);
            return;
        }
        if (box.second().isEmpty()) {
            BlockPos first = box.first().pos();
            if (!WandBox.fits(first, at)) {
                WandWork.say(player, "too_big", WandBox.MAX_SIDE, WandBox.MAX_VOLUME);
                return;
            }
            wand.set(ModDataComponents.WAND_BOX.get(), box.withSecond(at));
            int[] size = WandBox.size(first, at);
            WandWork.say(player, "second_corner", size[0], size[1], size[2], WandBox.cells(first, at, mode).size());
            mark(player, 1.15F);
            return;
        }
        fill(level, player, wand, mode, box);
    }

    /**
     * Fills a marked-out box with the block in the other hand, or the one its first corner was clicked on. The box is
     * looked at again here, not taken on the clicks' word: a wand's box can come from anywhere a component can (a
     * creative client's own item, a command), and one too big, or a block the wand cannot place, fills nothing (r22
     * CXC-005: a box of any size was listed whole on the server thread).
     */
    private static void fill(ServerLevel level, Player player, ItemStack wand, WandMode mode, WandBox box) {
        BlockState template = canCopy(box.template()) ? toPlace(box.template()) : null; // as a click makes it: one block an item
        BlockState block = fillBlock(player, template);
        wand.remove(ModDataComponents.WAND_BOX.get());
        if (block == null || box.second().isEmpty()) {
            return;
        }
        if (box.first().dimension() != level.dimension() || !WandBox.fits(box.first().pos(), box.second().get())) {
            WandWork.say(player, "too_big", WandBox.MAX_SIDE, WandBox.MAX_VOLUME);
            return;
        }
        WandWork.startBox(level, player, wand, box.first().pos(), box.second().get(), mode, block);
    }

    /** What a box is filled with: the block in the other hand, if the wand can place it; else {@code fallback}. */
    public static @Nullable BlockState fillBlock(Player player, @Nullable BlockState fallback) {
        ItemStack other = player.getOffhandItem();
        if (other.getItem() instanceof BlockItem item && canCopy(item.getBlock().defaultBlockState())) {
            return fallback != null && fallback.is(item.getBlock()) ? fallback : item.getBlock().defaultBlockState();
        }
        return fallback;
    }

    /**
     * A click at the air: sneaking, it lets go of a box being marked out, or else takes the last use back; standing, it
     * fills a box marked out.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack wand = player.getItemInHand(hand);
        if (!FeatureGate.wand()) {
            return InteractionResult.PASS;
        }
        WandBox box = wand.get(ModDataComponents.WAND_BOX.get());
        if (player.isSecondaryUseActive()) {
            if (level instanceof ServerLevel server) {
                if (box != null && hasModes(wand)) {
                    wand.remove(ModDataComponents.WAND_BOX.get());
                    WandWork.say(player, "cancelled");
                } else {
                    WandWork.undo(server, player, wand);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (box != null && box.second().isPresent() && mode(wand).box()) {
            if (level instanceof ServerLevel server && box.first().dimension() == level.dimension()) {
                fill(server, player, wand, mode(wand), box);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** What a click with {@code wand} does: its mode, if it has modes; a plain wand only spreads. */
    public static WandMode mode(ItemStack wand) {
        return hasModes(wand) ? wand.getOrDefault(ModDataComponents.WAND_MODE.get(), WandMode.SPREAD) : WandMode.SPREAD;
    }

    /** Steps the wand's mode (scrolled while sneaking), letting go of a box being marked out; a plain wand has none. */
    public static void stepMode(ServerPlayer player, ItemStack wand, int steps) {
        if (!hasModes(wand)) {
            return;
        }
        WandMode mode = mode(wand).step(steps);
        wand.set(ModDataComponents.WAND_MODE.get(), mode);
        wand.remove(ModDataComponents.WAND_BOX.get());
        player.sendOverlayMessage(Component.translatable("message.nookworks.wand.mode", mode.title()));
        mark(player, 1.3F);
    }

    /**
     * The wand's own tick, to the one holding it and to no one else: a corner marked or a mode chosen is theirs, not the
     * room's. (It was vanilla's menu click sent through the level - a stereo file, so every player within 16 blocks
     * heard a click with no place to it.)
     */
    private static void mark(Player player, float pitch) {
        if (player instanceof ServerPlayer server) {
            server.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.BUILDING_WAND_MARK.get()),
                    SoundSource.PLAYERS, server.getX(), server.getY(), server.getZ(), 0.5F, pitch, server.level().getRandom().nextLong()));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        if (!this.modes) {
            return;
        }
        tooltip.accept(Component.translatable("item.nookworks.building_wand.mode", mode(stack).title()).withStyle(ChatFormatting.GRAY));
    }

    /**
     * Places one block, asking the same questions a hand would: may this player build here, does the block
     * survive, and does anything else object. Returns false when the block was not placed, so the caller can
     * keep the materials.
     */
    static boolean place(ServerLevel level, Player player, BlockPos target, Direction face, BlockState placing,
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
        return oriented(clicked, clicked.getBlock().defaultBlockState());
    }

    /** {@code fresh} turned the way {@code from} is, as far as they share how they sit. */
    public static BlockState oriented(BlockState from, BlockState fresh) {
        for (Property<?> property : ORIENTATION) {
            fresh = copy(from, fresh, property);
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

    /** Where the wand takes its blocks from: the inventory, then the other hand (where the block to use is held). */
    static List<ItemStack> stacks(Player player) {
        List<ItemStack> stacks = new java.util.ArrayList<>(player.getInventory().getNonEquipmentItems());
        stacks.add(player.getOffhandItem());
        return stacks;
    }

    /** How many of these the player has. */
    static int count(Player player, BlockState placing) {
        ItemStack wanted = new ItemStack(placing.getBlock());
        return stacks(player).stream().filter(s -> !s.isEmpty() && ItemStack.isSameItem(s, wanted))
                .mapToInt(ItemStack::getCount).sum();
    }

    /** Takes {@code cost} matching blocks, starting with the one in {@code slot}. */
    static void take(Player player, BlockState placing, int slot, int cost) {
        var items = stacks(player);
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

    static int find(Player player, BlockState placing) {
        ItemStack wanted = new ItemStack(placing.getBlock());
        var items = stacks(player);
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty() && ItemStack.isSameItem(stack, wanted)) {
                return slot;
            }
        }
        return -1;
    }
}
