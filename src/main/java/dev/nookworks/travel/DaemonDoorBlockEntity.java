package dev.nookworks.travel;

import org.jspecify.annotations.Nullable;
import java.util.Optional;

import com.mojang.logging.LogUtils;

import dev.nookworks.registry.ModTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;

/**
 * A Dock-a-Daemon Door's lower half, as the client needs it to draw the world beyond: whether it is the home door (which
 * opens onto wherever the player looking at it chose) or a return door, and where a return door leads. Nothing of it
 * is saved: the server works it out from {@link DaemonDoors} each time it is sent.
 */
public class DaemonDoorBlockEntity extends BlockEntity {
    private boolean home;
    private Optional<ResourceKey<Level>> leadsTo = Optional.empty();
    /** On the client: whether it was last drawn open, and when it began to slide there (game time); for the slide. */
    public @Nullable Boolean shownOpen;
    public double slideStart;

    public DaemonDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModTravel.DAEMON_DOOR_ENTITY.get(), pos, state);
    }

    /** Whether this is the home door (on the client, as last sent). */
    public boolean isHome() {
        return this.home;
    }

    /** Where a return door leads: the home door's dimension. Empty for the home door, or with no home door. */
    public Optional<ResourceKey<Level>> leadsTo() {
        return this.leadsTo;
    }

    /**
     * Where this door leads for {@code player} (on the client, as last sent): the home door to the dimension they chose at
     * its panel - nowhere while they have it off - and a return door home. Never the dimension it stands in.
     */
    public Optional<ResourceKey<Level>> leadsFor(Player player) {
        Optional<ResourceKey<Level>> to = this.home ? TravelLog.of(player).destination() : this.leadsTo;
        Level level = this.level;
        return level == null ? Optional.empty() : to.filter(key -> !key.equals(level.dimension()));
    }

    /** The door gone: forgotten, and the doors still standing told if the home door went with it. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel server) {
            DaemonDoors doors = DaemonDoors.get(server.getServer());
            doors.forget(server.dimension(), pos);
            refreshAll(server.getServer());
        }
    }

    /**
     * Sends every loaded door to whoever sees it, as it is now: after a door is set down or taken away, which may make
     * another the home door, or leave the return doors with no home. There are a handful at most.
     */
    static void refreshAll(MinecraftServer server) {
        for (var entry : DaemonDoors.get(server).all().entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level != null && level.isLoaded(entry.getValue()) && level.getBlockEntity(entry.getValue()) instanceof DaemonDoorBlockEntity door) {
                level.sendBlockUpdated(entry.getValue(), door.getBlockState(), door.getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            if (this.level instanceof ServerLevel server) {
                DaemonDoors doors = DaemonDoors.get(server.getServer());
                this.home = doors.isHome(server.dimension(), this.worldPosition);
                GlobalPos homeDoor = doors.home();
                this.leadsTo = this.home || homeDoor == null ? Optional.empty() : Optional.of(homeDoor.dimension());
            }
            output.putBoolean("Home", this.home);
            output.storeNullable("LeadsTo", ResourceKey.codec(Registries.DIMENSION), this.leadsTo.orElse(null));
            return output.buildResult();
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.home = input.getBooleanOr("Home", false);
        this.leadsTo = input.read("LeadsTo", ResourceKey.codec(Registries.DIMENSION));
    }
}
