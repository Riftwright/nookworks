package dev.nookworks.spawner;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Who set a Spawner Link Sender down, and which setting-down this is. Pairing a receiver with it is its owner's alone
 * (and an operator's): a link could otherwise reach anyone's spawners from far off, with nothing to show for it. A
 * receiver remembers the stamp it was paired under, and a sender picked up and set down again has a new one, so the old
 * links reach nothing (the user, 2026-10-01).
 * <p>
 * A sender from before has no owner - the first to pair with it becomes it - and stamp 0, which the receivers paired
 * with it then carry too: they go on working until it is moved.
 */
public class SpawnerLinkSenderBlockEntity extends BlockEntity {
    private @Nullable UUID owner;
    private long stamp;

    public SpawnerLinkSenderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPAWNER_LINK_SENDER.get(), pos, state);
    }

    public @Nullable UUID owner() {
        return this.owner;
    }

    public long stamp() {
        return this.stamp;
    }

    /** Set down by {@code owner} (null: not by a player): its own owner, and a stamp no receiver carries yet. */
    void placed(@Nullable UUID owner, long stamp) {
        this.owner = owner;
        this.stamp = stamp;
        this.setChanged();
    }

    /** A sender with no owner yet goes to the first to pair with it. */
    void adopt(UUID owner) {
        if (this.owner == null) {
            this.owner = owner;
            this.setChanged();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("Owner", UUIDUtil.CODEC, this.owner);
        output.putLong("Stamp", this.stamp);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        this.stamp = input.getLongOr("Stamp", 0L);
    }
}
