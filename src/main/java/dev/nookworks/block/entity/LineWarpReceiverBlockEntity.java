package dev.nookworks.block.entity;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.nookworks.feature.DrawRights;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Which senders a Horizontal Item Warp Receiver takes from. On a server a line runs through other people's land, and
 * anyone could otherwise aim a sender at someone's receiver and fill their chest, or set a receiver of their own in
 * someone's line and take what it carries. So a receiver takes from the senders of whoever set it down
 * ({@link DrawRights#ownerOf}) - the user, 2026-10-14, in place of pairing each receiver with its sender, which was a
 * step every time ("あれ（ペアリング）いがいとめんどくさいよね").
 * <p>
 * Receivers from before keep working as they did. One paired while receivers paired (2026-10-02 to 10-14) keeps its
 * link - the sender it takes from, and which setting-down of that sender - and a receiver item still carrying a link
 * brings it along when set down; one of those set down unpaired took nothing then and takes nothing now, until set down
 * again. One from before pairing came in is <em>old</em>: no owner, no link, and it takes from an old sender in its line
 * and from any sender with an owner - nobody can make an old one by hand now, so it is no way in for a stranger, and an
 * old line whose sender alone is set down again goes on working. A receiver set down by a machine of no known player has
 * no owner either, and is an old one. One paired in the pairing days, or set down unpaired then, stays as it was: its
 * pairing promised that nothing else came in.
 */
public class LineWarpReceiverBlockEntity extends BlockEntity implements DrawRights.Owned {
    private @Nullable GlobalPos link;
    private long stamp;
    /** Set down while receivers paired, and so not an old one: without a link or an owner it takes nothing. */
    private boolean pairs;
    /** Who set it down; null for one from before owners were kept, or set down by no known player. */
    private @Nullable UUID owner;
    private @Nullable DrawRights drawRights;

    public LineWarpReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LINE_WARP_RECEIVER.get(), pos, state);
    }

    public @Nullable GlobalPos link() {
        return this.link;
    }

    /**
     * Whether this receiver takes what the sender at {@code sender}, of stamp {@code stamp} and owner {@code senderOwner},
     * sends: one paired with it, of the setting-down it was paired with; a sender of its own owner's; for an old receiver,
     * an old sender or any sender with an owner.
     */
    public boolean takesFrom(Level level, BlockPos sender, long stamp, @Nullable UUID senderOwner) {
        if (this.link != null && this.link.dimension().equals(level.dimension()) && this.link.pos().equals(sender) && this.stamp == stamp) {
            return true;
        }
        if (this.owner != null) {
            return this.owner.equals(senderOwner);
        }
        return this.link == null && !this.pairs && (stamp == 0 || senderOwner != null); // an old receiver
    }

    /** Whether it was set down while receivers paired (not an old one), so that with no link or owner it takes nothing. */
    public boolean pairs() {
        return this.pairs;
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

    /** Kept for {@link DrawRights.Owned}; a warp asks no claim, it only matches owners. */
    @Override
    public DrawRights drawRights() {
        if (this.drawRights == null) {
            this.drawRights = new DrawRights();
        }
        return this.drawRights;
    }

    /**
     * Whether the sender it is paired with has been set down again since, or taken away: the link reaches nothing then.
     * Null when that cannot be told from here - no link, or the sender in another dimension or not loaded.
     */
    public @Nullable Boolean senderMoved(Level level) {
        if (this.link == null || !this.link.dimension().equals(level.dimension()) || !level.isLoaded(this.link.pos())) {
            return null;
        }
        return !(level.getBlockEntity(this.link.pos()) instanceof LineWarpSenderBlockEntity sender) || sender.stamp() != this.stamp;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("Link", GlobalPos.CODEC, this.link);
        output.putLong("Stamp", this.stamp);
        output.putBoolean("Pairs", this.pairs);
        output.storeNullable("Owner", UUIDUtil.CODEC, this.owner);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.link = input.read("Link", GlobalPos.CODEC).orElse(null);
        this.stamp = input.getLongOr("Stamp", 0L);
        this.pairs = input.getBooleanOr("Pairs", false);
        this.owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        this.link = components.get(ModDataComponents.WARP_LINK.get());
        this.stamp = components.getOrDefault(ModDataComponents.WARP_LINK_STAMP.get(), 0L);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (this.link != null) {
            components.set(ModDataComponents.WARP_LINK.get(), this.link);
            components.set(ModDataComponents.WARP_LINK_STAMP.get(), this.stamp);
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("Link");
        output.discard("Stamp");
    }
}
