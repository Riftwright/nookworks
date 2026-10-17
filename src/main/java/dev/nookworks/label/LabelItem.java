package dev.nookworks.label;

import org.jspecify.annotations.Nullable;

import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A printed label, carrying the picture of one item ({@link ModDataComponents#LABEL}) or a few letters
 * ({@link ModDataComponents#LABEL_TEXT}, see {@link LabelText}); the Printing Press makes them from a sample or from
 * what is typed. Pressed against the side of a block it sticks there as a {@link LabelEntity}, centred where it was
 * pressed and kept on the block's face, so on a chest it goes on the chest's front and not over its edge.
 */
public class LabelItem extends Item {
    public LabelItem(Properties properties) {
        super(properties);
    }

    /** A label showing {@code shows}'s item; a label as the sample prints what it shows, picture or letters. */
    public static ItemStack of(ItemStack shows) {
        if (shows.isEmpty()) {
            return ItemStack.EMPTY; // (an empty stack's item is air: a label of nothing, else)
        }
        String text = text(shows);
        if (shows.is(ModItems.LABEL.get()) && !text.isEmpty()) {
            return ofText(text);
        }
        Holder<Item> printed = shows.is(ModItems.LABEL.get()) ? shows.get(ModDataComponents.LABEL.get()) : shows.typeHolder();
        return printed == null ? ItemStack.EMPTY : of(printed);
    }

    /** The label the creative tab shows, and JEI with it: an iron ingot's. */
    public static ItemStack sample() {
        return of(Items.IRON_INGOT.builtInRegistryHolder());
    }

    public static ItemStack of(Holder<Item> shows) {
        ItemStack label = new ItemStack(ModItems.LABEL.get());
        label.set(ModDataComponents.LABEL.get(), shows);
        return label;
    }

    /** A letter label saying {@code text} (cleaned: see {@link LabelText#clean}); empty when nothing is left of it. */
    public static ItemStack ofText(String text) {
        String clean = LabelText.clean(text);
        if (clean.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack label = new ItemStack(ModItems.LABEL.get());
        label.set(ModDataComponents.LABEL_TEXT.get(), clean);
        return label;
    }

    /** What a letter label says, or "" for a picture label or a blank one. */
    public static String text(ItemStack label) {
        return label.getOrDefault(ModDataComponents.LABEL_TEXT.get(), "");
    }

    /** The item a label shows, or empty for one that shows nothing. */
    public static ItemStack shows(ItemStack label) {
        Holder<Item> shows = label.get(ModDataComponents.LABEL.get());
        return shows == null ? ItemStack.EMPTY : new ItemStack(shows);
    }

    @Override
    public Component getName(ItemStack stack) {
        String text = text(stack);
        if (!text.isEmpty()) {
            return Component.translatable("item.nookworks.label.text", text);
        }
        ItemStack shows = shows(stack);
        return shows.isEmpty() ? super.getName(stack) : Component.translatable("item.nookworks.label.of", shows.getHoverName());
    }

    /**
     * Stuck on before the block has its say: otherwise a chest or a barrel, the very things labels are for, would open
     * instead. Where one cannot go - on another label, say - the block is used as usual, so a labelled chest still
     * opens with labels in hand when the click lands on its label. Sneaking, it goes in the very middle of the face
     * (of a full block's face, or of a door's or a chest's outline), so a wall of barrels labels in neat rows.
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Direction face = context.getClickedFace();
        ItemStack shows = shows(stack);
        String text = text(stack);
        if (face.getAxis().isVertical() || shows.isEmpty() && text.isEmpty()) {
            return InteractionResult.PASS; // on the side of a block only, like a sign on a wall
        }
        Level level = context.getLevel();
        BlockPos onto = context.getClickedPos();
        Player player = context.getPlayer();
        if (player != null && !player.mayUseItemAt(onto, face, stack)) {
            return InteractionResult.PASS;
        }
        LabelEntity label = place(level, onto, face, context.getClickLocation(), shows, player != null && player.isShiftKeyDown());
        if (label == null) {
            return InteractionResult.PASS;
        }
        label.setText(text);
        if (level instanceof ServerLevel server) {
            label.playPlacementSound();
            server.addFreshEntity(label);
        }
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    /** As {@link #place(Level, BlockPos, Direction, Vec3, ItemStack, boolean)}, where it was pressed. */
    public static @Nullable LabelEntity place(Level level, BlockPos onto, Direction facing, Vec3 hit, ItemStack shows) {
        return place(level, onto, facing, hit, shows, false);
    }

    /**
     * Where a label set in the middle of a chest's front goes up and down, as pixels from the block's middle: under the
     * latch (which stands 7 to 11 px up the front), not across it.
     */
    private static final int UNDER_THE_LATCH = 7 - 8 - Math.round(LabelEntity.HEIGHT * 8);

    /**
     * A label for the face {@code facing} of the block at {@code onto}, centred at {@code hit} as near as it fits - or,
     * {@code middle}, in the middle of the block's outline on that face (on a chest's front, under its latch): on the
     * pixel grid, on the block's own surface, and within the outline. Null when it cannot go there - nothing to stick
     * to, or another label already in the way.
     */
    public static @Nullable LabelEntity place(Level level, BlockPos onto, Direction facing, Vec3 hit, ItemStack shows, boolean middle) {
        BlockState state = level.getBlockState(onto);
        VoxelShape shape = state.getShape(level, onto);
        if (shape.isEmpty()) {
            return null;
        }
        AABB bounds = shape.bounds(); // in the block's own square, 0 to 1
        Vec3 local = hit.subtract(Vec3.atCenterOf(onto));
        Direction across = LabelEntity.acrossOf(facing);
        int halfW = Math.round(LabelEntity.WIDTH * 8);
        int halfH = Math.round(LabelEntity.HEIGHT * 8);
        // Where the outline runs along the face, as pixels from the middle: across (the way the viewer's right runs) and up.
        double lo = across.getAxisDirection() == Direction.AxisDirection.POSITIVE ? bounds.min(across.getAxis()) - 0.5 : 0.5 - bounds.max(across.getAxis());
        double hi = across.getAxisDirection() == Direction.AxisDirection.POSITIVE ? bounds.max(across.getAxis()) - 0.5 : 0.5 - bounds.min(across.getAxis());
        double wantX = middle ? (lo + hi) * 8 : local.dot(across.getUnitVec3()) * 16;
        double wantY = !middle ? local.y * 16 : hasLatch(state, facing) ? UNDER_THE_LATCH : (bounds.minY + bounds.maxY - 1) * 8;
        int x = fit(wantX, lo * 16 + halfW, hi * 16 - halfW);
        int y = fit(wantY, (bounds.minY - 0.5) * 16 + halfH, (bounds.maxY - 0.5) * 16 - halfH);
        // How far back from the edge of the square the face that was pressed on stands.
        double out = local.dot(facing.getUnitVec3());
        int sunk = Mth.clamp((int) Math.round((0.5 - out) * 16), 0, 15);
        LabelEntity label = new LabelEntity(level, onto, facing, shows, x, y, sunk);
        return label.survives() ? label : null;
    }

    /** Whether {@code facing} is the front of a chest, with the latch in its middle. */
    private static boolean hasLatch(BlockState state, Direction facing) {
        return (state.getBlock() instanceof ChestBlock
                || state.getBlock() instanceof EnderChestBlock)
                && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing;
    }

    /** {@code at} rounded to a whole pixel and kept within {@code min}..{@code max}; centred when the room is too small. */
    private static int fit(double at, double min, double max) {
        int from = (int) Math.ceil(min - 1.0E-6);
        int to = (int) Math.floor(max + 1.0E-6);
        if (from > to) {
            return (int) Math.round((min + max) / 2);
        }
        return Mth.clamp((int) Math.round(at), from, to);
    }
}
