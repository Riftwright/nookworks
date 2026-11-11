package dev.nookworks.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The box the Reinforced Building Wand is marking out: its first corner (where in which world), its second once clicked, and the
 * block the first click was on - what it is filled with when the other hand holds no block. Kept on the wand, so the
 * client draws the box as it is marked out.
 */
public record WandBox(GlobalPos first, Optional<BlockPos> second, BlockState template) {
    /** The most a box may hold, and the longest it may be along any side. */
    public static final int MAX_VOLUME = 4096;
    public static final int MAX_SIDE = 32;

    public static final Codec<WandBox> CODEC = RecordCodecBuilder.create(i -> i.group(
            GlobalPos.CODEC.fieldOf("first").forGetter(WandBox::first),
            BlockPos.CODEC.optionalFieldOf("second").forGetter(WandBox::second),
            BlockState.CODEC.fieldOf("template").forGetter(WandBox::template)).apply(i, WandBox::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, WandBox> STREAM_CODEC = StreamCodec.composite(
            GlobalPos.STREAM_CODEC, WandBox::first,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), WandBox::second,
            ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY), WandBox::template,
            WandBox::new);

    public WandBox withSecond(BlockPos pos) {
        return new WandBox(this.first, Optional.of(pos), this.template);
    }

    /** The lengths of a box from {@code a} to {@code b}, both corners in it. */
    public static int[] size(BlockPos a, BlockPos b) {
        return new int[] {Math.abs(a.getX() - b.getX()) + 1, Math.abs(a.getY() - b.getY()) + 1, Math.abs(a.getZ() - b.getZ()) + 1};
    }

    /** Whether a box from {@code a} to {@code b} is within the limits. */
    public static boolean fits(BlockPos a, BlockPos b) {
        int[] s = size(a, b);
        return s[0] <= MAX_SIDE && s[1] <= MAX_SIDE && s[2] <= MAX_SIDE && s[0] * s[1] * s[2] <= MAX_VOLUME;
    }

    /**
     * The squares of a box from {@code a} to {@code b} that {@code mode} fills - all of them, those on its walls, or
     * those on its edges - from the bottom layer up, so sand and gravel settle on what is already there.
     */
    public static List<BlockPos> cells(BlockPos a, BlockPos b, WandMode mode) {
        BlockPos min = BlockPos.min(a, b);
        BlockPos max = BlockPos.max(a, b);
        List<BlockPos> cells = new ArrayList<>();
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    int faces = (x == min.getX() || x == max.getX() ? 1 : 0) + (y == min.getY() || y == max.getY() ? 1 : 0)
                            + (z == min.getZ() || z == max.getZ() ? 1 : 0);
                    boolean wanted = switch (mode) {
                        case HOLLOW -> faces >= 1;
                        case FRAME -> faces >= 2;
                        default -> true;
                    };
                    if (wanted) {
                        cells.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return cells;
    }
}
