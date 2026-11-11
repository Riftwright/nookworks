package dev.nookworks.carry;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The client's copy of a {@link CarriedBlock}: what drawing it needs and nothing more - each part's state and its block
 * entity's update tag (what vanilla already sends a client to draw that block), never the full inventory. Any entity
 * can sync it in its {@code SynchedEntityData} with {@link #ENTITY_SERIALIZER}, and draw it with
 * {@code dev.nookworks.client.carry.CarriedBlockRendering}. Treat its tags as read-only.
 */
public record CarriedLook(List<LookPart> parts) {
    /** Nothing carried yet: an entity's default before its look comes in. */
    public static final CarriedLook EMPTY = new CarriedLook(List.of());

    /** One part: where it stands from the first, its state, and its block entity's update tag (or none). */
    public record LookPart(BlockPos offset, BlockState state, @Nullable CompoundTag updateTag) {
        public static final StreamCodec<ByteBuf, LookPart> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, LookPart::offset,
                ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY), LookPart::state,
                ByteBufCodecs.OPTIONAL_COMPOUND_TAG, part -> Optional.ofNullable(part.updateTag()),
                (offset, state, tag) -> new LookPart(offset, state, tag.orElse(null)));

        public LookPart {
            offset = offset.immutable();
        }
    }

    public static final StreamCodec<ByteBuf, CarriedLook> STREAM_CODEC =
            LookPart.STREAM_CODEC.apply(ByteBufCodecs.list(CarriedBlock.MAX_PARTS)).map(CarriedLook::new, CarriedLook::parts);

    /** For an entity's synced data: registered by Nookworks as {@code nookworks:carried_look}. */
    public static final EntityDataSerializer<CarriedLook> ENTITY_SERIALIZER = EntityDataSerializer.forValueType(STREAM_CODEC);

    public CarriedLook {
        parts = List.copyOf(parts);
    }

    public boolean isEmpty() {
        return this.parts.isEmpty();
    }

    /** The first part's state, or air when there is none. */
    public BlockState state() {
        return this.parts.isEmpty() ? Blocks.AIR.defaultBlockState() : this.parts.get(0).state();
    }
}
