package dev.nookworks.spawner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.nookworks.feature.DrawRights;
import dev.nookworks.feature.Permission;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * Holds the paired sender position, which travels with the item (so breaking and re-placing keeps the pairing), and
 * the FE the Spawner Controller on top of it runs on: the cable goes into the receiver, a full block, and the
 * console sits on it.
 */
public class SpawnerLinkReceiverBlockEntity extends BlockEntity implements DrawRights.Owned {
    /**
     * Enough for the strongest settings on a sender between six spawners (19,200 FE a tick) for a second, with a pipe
     * to match: the settings' caps are high (SpawnerSettings), so the power has to be able to get in.
     */
    public static final int CAPACITY = 400_000;
    public static final int MAX_INSERT = 20_000;

    /** What it holds, raised with the setting for the power machines use (as what it pays out is). */
    public static int capacity() {
        return Math.max(CAPACITY, MachineTuning.energy(CAPACITY));
    }

    /**
     * How long the claims' answer about a spawner is trusted before it is asked again: five minutes, as a Barter
     * Terminal's. Asking posts a break event, which a logging mod writes down: not every period.
     */
    private static final int ASK_AGAIN = 6_000;

    /**
     * Who set it down; null for one set down before owners were kept. A stranger's receiver set beside a claimed
     * generator's cable takes no power from it.
     */
    private @Nullable UUID owner;
    /** What its owner may take from and give into across a claim's edge, as last asked. */
    private final DrawRights drawRights = new DrawRights();

    private @Nullable GlobalPos link;
    /** The stamp of the sender's setting-down it was paired with: moved since, the sender has another. */
    private long stamp;
    /** The claims' answer for each spawner the sender touches, as last asked. Not saved: asked again after a load. */
    private final Map<BlockPos, Asked> asked = new HashMap<>();

    private record Asked(@Nullable UUID owner, boolean allowed, long at) {}

    // raised with the setting for the power machines use, as what it pays out is
    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(capacity(),
            Math.max(MAX_INSERT, MachineTuning.energy(MAX_INSERT)), 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            MachineBlockEntity.savePower(SpawnerLinkReceiverBlockEntity.this);
        }
    };

    public SpawnerLinkReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPAWNER_LINK_RECEIVER.get(), pos, state);
    }

    public SimpleEnergyHandler energy() {
        return this.energy;
    }

    public @Nullable GlobalPos link() {
        return this.link;
    }

    public void setLink(@Nullable GlobalPos link, long stamp) {
        this.link = link;
        this.stamp = stamp;
        this.asked.clear();
        this.setChanged();
    }

    /**
     * Whether the paired sender has been set down again since (or taken away): the link reaches nothing then. Null when
     * that cannot be told from here - no link, or the sender in another dimension or not loaded.
     */
    public @Nullable Boolean senderMoved(ServerLevel level) {
        if (this.link == null || !this.link.dimension().equals(level.dimension()) || !level.isLoaded(this.link.pos())) {
            return null;
        }
        return !(level.getBlockEntity(this.link.pos()) instanceof SpawnerLinkSenderBlockEntity sender) || sender.stamp() != this.stamp;
    }

    /**
     * The spawners touching the paired sender, if it is in this dimension, loaded, still there and not moved since -
     * those its owner may steer ({@link #mayControl}).
     */
    public List<SpawnerBlockEntity> spawners(ServerLevel level) {
        List<SpawnerBlockEntity> found = new ArrayList<>();
        if (this.link == null || !this.link.dimension().equals(level.dimension())) {
            return found;
        }
        BlockPos sender = this.link.pos();
        if (!level.isLoaded(sender) || !level.getBlockState(sender).is(ModBlocks.SPAWNER_LINK_SENDER.get())
                || !Boolean.FALSE.equals(this.senderMoved(level))) {
            return found;
        }
        UUID owner = level.getBlockEntity(sender) instanceof SpawnerLinkSenderBlockEntity s ? s.owner() : null;
        for (Direction dir : Direction.values()) {
            BlockPos next = sender.relative(dir);
            if (level.isLoaded(next) && level.getBlockEntity(next) instanceof SpawnerBlockEntity spawner
                    && this.mayControl(level, next, owner)) {
                found.add(spawner);
            }
        }
        return found;
    }

    /**
     * Whether the sender's owner may steer the spawner at {@code pos}: asked of claim mods as breaking it would be, for
     * them (a sender from before owners, for an anonymous machine), as other machines ask. A sender set down on one side
     * of a claim's edge reached the spawner on the other - held someone else's dungeon off, or ran it at full tilt.
     * Whoever may take a spawner away may as well steer it; asked as placing, the spawner would be lifted out and set
     * back each time. Asked again now and then, and for another owner. A spawner refused is held off no longer: its
     * hold finds this controller reaching it no more ({@link SpawnerHold}).
     */
    private boolean mayControl(ServerLevel level, BlockPos pos, @Nullable UUID owner) {
        long now = level.getGameTime();
        Asked last = this.asked.get(pos);
        if (last == null || !Objects.equals(last.owner(), owner) || now < last.at() || now - last.at() >= ASK_AGAIN) {
            last = new Asked(owner, Permission.mayBreak(level, pos, Permission.actor(level, owner)), now);
            this.asked.put(pos.immutable(), last);
        }
        return last.allowed();
    }

    @Override
    public @Nullable UUID owner() {
        return this.owner;
    }

    @Override
    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        this.setChanged();
    }

    @Override
    public DrawRights drawRights() {
        return this.drawRights;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("Owner", UUIDUtil.CODEC, this.owner);
        output.storeNullable("Link", GlobalPos.CODEC, this.link);
        output.putLong("Stamp", this.stamp);
        this.energy.serialize(output.child("Energy"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        this.link = input.read("Link", GlobalPos.CODEC).orElse(null);
        this.stamp = input.getLongOr("Stamp", 0L);
        MachineBlockEntity.readEnergy(input, this.energy);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        this.link = components.get(ModDataComponents.SPAWNER_LINK.get());
        this.stamp = components.getOrDefault(ModDataComponents.SPAWNER_LINK_STAMP.get(), 0L);
        MachineBlockEntity.restoreEnergy(this.energy, components);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (this.link != null) {
            components.set(ModDataComponents.SPAWNER_LINK.get(), this.link);
            components.set(ModDataComponents.SPAWNER_LINK_STAMP.get(), this.stamp);
        }
        MachineBlockEntity.keepEnergy(this.energy, components);
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("Link");
        output.discard("Stamp");
    }
}
