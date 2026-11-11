package dev.nookworks.carry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * A block taken out of the world with its block entity, to be put back somewhere: what a Tied Load holds, and what an
 * add-on's cart, gondola or packed item holds (the API: docs/compat.md, "Carried blocks and tied loads"). Immutable.
 * <p>
 * One {@link Part} for most blocks, two for a double chest, as many as an add-on gathers ({@link CarryEvent.Gather});
 * the first part is the block that was clicked, the others' offsets are from it. Each part's data is its block entity's
 * {@code saveWithFullMetadata} (its components too), so the block comes back exactly as it was: contents, power,
 * liquids, progress. It is never ticked while carried.
 */
public record CarriedBlock(List<Part> parts) {
    /**
     * The most a carried block may weigh as saved: the Mob Bottle's cap, for the same reason - in an item it is sent
     * whole to every client that sees the item. A block whose data is larger is refused at take, with a reason.
     */
    public static final long MAX_BYTES = 64 * 1024;
    /** The most parts one value holds (what {@link Carrying} ever gathers, with room to spare). */
    public static final int MAX_PARTS = 64;

    /** One block of the set: where it stands from the first, its state, and its block entity's saved data (or none). */
    public record Part(BlockPos offset, BlockState state, @Nullable CompoundTag data) {
        public static final Codec<Part> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("offset").forGetter(Part::offset),
                BlockState.CODEC.fieldOf("state").forGetter(Part::state),
                CompoundTag.CODEC.optionalFieldOf("data").forGetter(part -> Optional.ofNullable(part.data())))
                .apply(instance, (offset, state, data) -> new Part(offset, state, data.orElse(null))));
        public static final StreamCodec<ByteBuf, Part> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Part::offset,
                ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY), Part::state,
                ByteBufCodecs.OPTIONAL_COMPOUND_TAG, part -> Optional.ofNullable(part.data()),
                (offset, state, data) -> new Part(offset, state, data.orElse(null)));

        public Part {
            offset = offset.immutable();
            data = data == null ? null : data.copy(); // kept as it was given: the caller's tag may change after
        }

        /** The data, a copy to change freely; null for a block with no block entity. */
        @Override
        public @Nullable CompoundTag data() {
            return this.data == null ? null : this.data.copy();
        }

        /** The data itself, not to be changed: for reading it without a copy. */
        @Nullable CompoundTag dataView() {
            return this.data;
        }
    }

    public static final Codec<CarriedBlock> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Part.CODEC.listOf(1, MAX_PARTS).fieldOf("parts").forGetter(CarriedBlock::parts))
            .apply(instance, CarriedBlock::new));
    public static final StreamCodec<ByteBuf, CarriedBlock> STREAM_CODEC =
            Part.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_PARTS)).map(CarriedBlock::new, CarriedBlock::parts);

    public CarriedBlock {
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("A carried block has at least one part");
        }
        parts = List.copyOf(parts);
    }

    /** The first part's state: the block that was clicked. */
    public BlockState state() {
        return this.parts.get(0).state();
    }

    /** The first part's block name (a double chest is a Chest). */
    public Component name() {
        return this.state().getBlock().getName();
    }

    /**
     * Turned by {@code rotation} about the first part: states and offsets. The data is not changed: a block entity
     * keeping a direction of its own is told at put-back ({@link CarryAware#afterPut}).
     */
    public CarriedBlock rotated(Rotation rotation) {
        if (rotation == Rotation.NONE) {
            return this;
        }
        List<Part> turned = new ArrayList<>(this.parts.size());
        for (Part part : this.parts) {
            turned.add(new Part(part.offset().rotate(rotation), part.state().rotate(rotation), part.dataView()));
        }
        return new CarriedBlock(turned);
    }

    /** Its size as saved, in bytes (NBT, as {@code CustomData.sizeInBytes} counts it). */
    public long bytes() {
        return CODEC.encodeStart(NbtOps.INSTANCE, this).result().map(tag -> (long) tag.sizeInBytes()).orElse(Long.MAX_VALUE);
    }

    /**
     * What a client needs to draw it: each part's state and its block entity's update tag (what vanilla sends a client
     * for drawing that block), never the full inventory. A block entity that cannot make one out of the world is drawn
     * by its state alone.
     */
    public CarriedLook look(HolderLookup.Provider registries) {
        List<CarriedLook.LookPart> looks = new ArrayList<>(this.parts.size());
        for (Part part : this.parts) {
            looks.add(new CarriedLook.LookPart(part.offset(), part.state(), updateTag(part, registries)));
        }
        return new CarriedLook(looks);
    }

    /** {@link #look(HolderLookup.Provider)} with the running server's registries (server side only). */
    public CarriedLook look() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException("CarriedBlock.look() needs a running server; pass the registries");
        }
        return this.look(server.registryAccess());
    }

    private static @Nullable CompoundTag updateTag(Part part, HolderLookup.Provider registries) {
        BlockEntity entity = part.dataView() == null ? null : blockEntity(part, registries);
        if (entity == null) {
            return null;
        }
        try {
            return entity.getUpdateTag(registries);
        } catch (RuntimeException e) { // one that reads its level for it: drawn by its state alone
            Nookworks.LOGGER.debug("No update tag for a carried {}: {}", part.state(), e.toString());
            return null;
        }
    }

    /** The part's block entity made out of the world from its data (no level), or null. */
    static @Nullable BlockEntity blockEntity(Part part, HolderLookup.Provider registries) {
        CompoundTag data = part.dataView();
        if (data == null) {
            return null;
        }
        try {
            return BlockEntity.loadStatic(BlockPos.ZERO, part.state(), data, registries);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Tooltip lines: the block's name, then how full it is - what its block entity's {@link CarryAware#summary} says,
     * else a count of the stacks in its saved items. {@code registries} may be null (the stack count alone is given).
     */
    public void summary(HolderLookup.@Nullable Provider registries, Consumer<Component> lines) {
        lines.accept(this.name());
        Part first = this.parts.get(0);
        if (first.dataView() == null) {
            return;
        }
        if (registries != null && blockEntity(first, registries) instanceof CarryAware aware) {
            aware.summary(lines);
            return;
        }
        int stacks = 0;
        for (Part part : this.parts) {
            if (part.dataView() != null) {
                stacks += part.dataView().getListOrEmpty("Items").size();
            }
        }
        if (stacks > 0) {
            lines.accept(Component.translatable("tooltip.nookworks.carried.stacks", stacks));
        }
    }

    /** {@link #summary(HolderLookup.Provider, Consumer)} with the running server's registries, if there is one here. */
    public void summary(Consumer<Component> lines) {
        var server = ServerLifecycleHooks.getCurrentServer();
        this.summary(server == null ? null : server.registryAccess(), lines);
    }
}
