package dev.nookworks.drive;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.jspecify.annotations.Nullable;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.feature.StructureTurns;
import dev.nookworks.machine.DriveSource;
import dev.nookworks.machine.MotorBlockEntity;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A Drive Wheel's belts - up to two, to other wheels, each kept at both ends - and, on the client, how far round it is.
 * A belt is drawn by the wheel with the lower position of its two ({@link #owns}), and checked by it now and then: a
 * block set in its way since (a piston's, say) snaps it, and its rope or chain falls there. A wheel turned out of line
 * with the other (by the Wrench) lets its belts fall at once.
 * <p>
 * What a belt took is given back once, whichever way it comes off: by the end that lets go while the other is there to
 * be told; with the other end away (unloaded), by the end that owns the belt - now, if it is that one going, or when it
 * is next loaded and finds the other wheel gone. Gone is gone even where another wheel has been set down in its place
 * since: a belt knows the wheel it was tied to ({@link #wheelId}), not only where it stood.
 */
public class DriveWheelBlockEntity extends BlockEntity implements DriveSource {
    /** Belts on one wheel at most: one in, one out - a line of wheels, or two ways from the motor's. */
    public static final int MOST_BELTS = 2;
    /** How often a belt is looked along for something in its way, in ticks. */
    private static final int CHECK_EVERY = 20;
    /** Ticks after a motor last drove it that it stops going round. */
    private static final int COAST = 2;

    /**
     * A belt to the wheel at {@code other}, of {@code kind}, and what it was made with: {@code count} of {@code item}.
     * {@code farWheel} is that wheel's {@link #wheelId}, or 0 for a belt saved before belts knew it (any wheel there is
     * taken to be the one). {@code madeOf} is what of those carried more than the item (an anvil's name), as it was, the
     * rest being plain: it comes back as it went in (a belt kept item and count only, and a named Rope came back
     * unnamed). {@code tiedBy} is the player who tied it, or null for one tied before belts kept it, or by no one known:
     * whose leave a block set down on its line asks ({@link Belts#onPlace}).
     */
    public record Belt(BlockPos other, BeltKind kind, Item item, int count, long farWheel, List<ItemStack> madeOf,
            @Nullable UUID tiedBy) {
        public static final Codec<Belt> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("other").forGetter(Belt::other),
                StringRepresentable.fromEnum(BeltKind::values).fieldOf("kind").forGetter(Belt::kind),
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Belt::item),
                Codec.INT.fieldOf("count").forGetter(Belt::count),
                Codec.LONG.optionalFieldOf("far_wheel", 0L).forGetter(Belt::farWheel),
                // both saved only when there is something to say, so a belt of plain rope saves as before
                ItemStack.CODEC.listOf().optionalFieldOf("made_of", List.of()).forGetter(Belt::madeOf),
                UUIDUtil.CODEC.optionalFieldOf("tied_by").forGetter(belt -> Optional.ofNullable(belt.tiedBy())))
                .apply(i, (other, kind, item, count, farWheel, madeOf, tiedBy) -> new Belt(other, kind, item, count, farWheel,
                        madeOf, tiedBy.orElse(null))));

        public Belt {
            madeOf = List.copyOf(madeOf);
        }

        /** A belt of plain {@code item}, tied by no one known: as belts were saved before they kept either. */
        public Belt(BlockPos other, BeltKind kind, Item item, int count, long farWheel) {
            this(other, kind, item, count, farWheel, List.of(), null);
        }

        /** The same belt, its far wheel at {@code other}. */
        Belt to(BlockPos other) {
            return new Belt(other, this.kind, this.item, this.count, this.farWheel, this.madeOf, this.tiedBy);
        }

        /**
         * What it gives back, in stacks a hand can hold: what it was made of that carried more than the item, as it was,
         * and the rest of its count as plain {@code item}.
         */
        public List<ItemStack> givenBack() {
            List<ItemStack> back = new ArrayList<>();
            int plain = this.count;
            for (ItemStack made : this.madeOf) {
                int n = Math.min(made.getCount(), plain);
                if (n > 0) {
                    back.add(made.copyWithCount(n));
                    plain -= n;
                }
            }
            for (int left = plain; left > 0; ) {
                int n = Math.min(left, this.item.getDefaultMaxStackSize());
                back.add(new ItemStack(this.item, n));
                left -= n;
            }
            return back;
        }
    }

    /**
     * Which wheel this is, of all those that may stand at its place in turn (never 0): a belt's far end is the wheel it
     * was tied to only while this matches. A wheel broken while the owner of its belt was away gave nothing back, the
     * owner giving it back when next loaded; with a new wheel set down there meanwhile, the owner took that one for the
     * wheel it was tied to, let go of the belt and gave nothing either (r15 late audit, N3). Saved; copied with the
     * wheel by a structure, as its belts are.
     */
    private long wheelId = newWheelId();

    private final List<Belt> belts = new ArrayList<>();
    /** Turned to face another way since last tick: its belts are looked at now, not at the next check. */
    private boolean turned;
    private long lastDriven = -100;
    private float shaft;
    private float step;
    /** How far its belts have run, in blocks, a block round: what their texture is moved by (client only). */
    private float travel;
    /** Whether its belts were last read from their steps from this wheel, which a structure setting it down may turn. */
    private boolean stepsRead;

    public DriveWheelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRIVE_WHEEL.get(), pos, state);
    }

    public Direction.Axis axis() {
        return this.getBlockState().getValue(DriveWheelBlock.FACING).getAxis();
    }

    public List<Belt> belts() {
        return this.belts;
    }

    public boolean full() {
        return this.belts.size() >= MOST_BELTS;
    }

    public boolean has(BlockPos other) {
        return this.belts.stream().anyMatch(b -> b.other().equals(other));
    }

    public long wheelId() {
        return this.wheelId;
    }

    private static long newWheelId() {
        long id;
        do {
            id = ThreadLocalRandom.current().nextLong();
        } while (id == 0);
        return id;
    }

    /** Whether {@code far} is the wheel {@code belt} was tied to, not one set down in its place since. */
    private static boolean tiedTo(Belt belt, DriveWheelBlockEntity far) {
        return belt.farWheel() == 0 || belt.farWheel() == far.wheelId;
    }

    /** Along its axle, where the belt to {@code other} rides on it, in blocks: the middle, or to either side of it when
     * two belts share the wheel, so they do not run through each other. */
    public double lane(BlockPos other) {
        if (this.belts.size() < MOST_BELTS) {
            return 0;
        }
        return this.belts.get(0).other().equals(other) ? -LANE : LANE;
    }

    /** How far a belt rides off the middle of a wheel it shares with another, in blocks. */
    private static final double LANE = 0.9 / 16;

    /** Whether this end draws and checks the belt to {@code other}: the lower of the two. */
    public boolean owns(BlockPos other) {
        return this.worldPosition.asLong() < other.asLong();
    }

    void add(Belt belt) {
        this.belts.add(belt);
        Belts.loaded(this);
        this.changed();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        Belts.loaded(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        Belts.gone(this);
    }

    void remove(BlockPos other) {
        if (this.belts.removeIf(b -> b.other().equals(other))) {
            this.changed();
        }
    }

    private void changed() {
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** A motor drives it this tick: it goes round, belts and all. */
    public void driven(long gameTime) {
        this.lastDriven = gameTime;
        BlockState state = this.getBlockState();
        if (this.level != null && !state.getValue(DriveWheelBlock.TURNING)) {
            this.level.setBlock(this.worldPosition, state.setValue(DriveWheelBlock.TURNING, true), Block.UPDATE_CLIENTS);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DriveWheelBlockEntity wheel) {
        long now = level.getGameTime();
        if (state.getValue(DriveWheelBlock.TURNING) && now - wheel.lastDriven > COAST) {
            level.setBlock(pos, state.setValue(DriveWheelBlock.TURNING, false), Block.UPDATE_CLIENTS);
        }
        if ((wheel.turned || (now + (pos.asLong() & 0xFF)) % CHECK_EVERY == 0) && level instanceof ServerLevel server) {
            wheel.turned = false;
            wheel.check(server);
        }
    }

    @Override
    public void setBlockState(BlockState state) {
        if (state.getValue(DriveWheelBlock.FACING).getAxis() != this.getBlockState().getValue(DriveWheelBlock.FACING).getAxis()) {
            this.turned = true;
        }
        super.setBlockState(state);
    }

    /**
     * A belt whose far wheel has gone is dropped from this end; one with something in its way, or between wheels no
     * longer in line (either turned since), snaps.
     */
    private void check(ServerLevel level) {
        for (Belt belt : List.copyOf(this.belts)) {
            if (!level.isLoaded(belt.other())) {
                continue;
            }
            if (!(level.getBlockEntity(belt.other()) instanceof DriveWheelBlockEntity far) || !tiedTo(belt, far)) {
                // the far wheel was broken while this end was away (another set down in its place since or not): if this
                // end owns the belt, the far one did not give its rope back, so this one does
                if (this.owns(belt.other())) {
                    drop(level, Vec3.atCenterOf(this.worldPosition).lerp(Vec3.atCenterOf(belt.other()), 0.5), belt);
                }
                this.remove(belt.other());
                continue;
            }
            if (!far.has(this.worldPosition)) {
                this.remove(belt.other()); // let go of at the far end, its rope given back there
                continue;
            }
            Direction.Axis axis = this.axis();
            boolean askew = far.axis() != axis || belt.other().get(axis) != this.worldPosition.get(axis);
            if (askew || this.owns(belt.other()) && Belts.blocker(level, this.worldPosition, belt.other(), axis) != null) {
                this.snap(level, belt);
            }
        }
    }

    private static void drop(Level level, Vec3 at, Belt belt) {
        for (ItemStack back : belt.givenBack()) {
            Containers.dropItemStack(level, at.x, at.y, at.z, back);
        }
    }

    /**
     * Its belts looked along again next tick, not at the next check: a block set down on one's line where its tier may
     * no longer build, by someone who may, is let stand ({@link Belts#onPlace}), and snaps the belt if it stays.
     */
    void recheck() {
        this.turned = true;
    }

    /** Snaps {@code belt}, which this end owns, as something come into its way does: its rope or chain dropped at the middle, both ends let go. */
    private void snap(Level level, Belt belt) {
        Vec3 middle = Vec3.atCenterOf(this.worldPosition).lerp(Vec3.atCenterOf(belt.other()), 0.5);
        drop(level, middle, belt);
        level.playSound(null, middle.x, middle.y, middle.z, belt.kind().snapSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
        if (level.isLoaded(belt.other()) && level.getBlockEntity(belt.other()) instanceof DriveWheelBlockEntity far) {
            far.remove(this.worldPosition);
        }
        this.remove(belt.other());
    }

    /** Sneak-used with an empty hand: every belt off, its rope or chain into the player's hands, the far ends freed. */
    void takeOff(Player player) {
        for (Belt belt : List.copyOf(this.belts)) {
            if (this.level != null && !this.level.isLoaded(belt.other()) && !this.owns(belt.other())) {
                // left on while the end that owns it is away: that end settles it when it loads, by what it finds here.
                // Taken off and paid for here, the owner, finding this wheel broken (or another in its place) by then,
                // paid for it again (r15 fix check 6)
                continue;
            }
            // the far end loaded: given back by the same rule as breaking (a stale belt, already given back, is not)
            boolean owed = this.level == null || !this.level.isLoaded(belt.other()) || this.givesBack(this.level, belt);
            for (ItemStack back : owed ? belt.givenBack() : List.<ItemStack>of()) {
                if (!player.getInventory().add(back)) {
                    player.drop(back, false, Prediction.PREDICTED);
                }
            }
            if (this.level != null && this.level.isLoaded(belt.other())
                    && this.level.getBlockEntity(belt.other()) instanceof DriveWheelBlockEntity far) {
                far.remove(this.worldPosition);
            }
            this.remove(belt.other());
            if (this.level != null) {
                this.level.playSound(null, this.worldPosition, belt.kind().snapSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
    }

    /**
     * Whether this end, letting go of {@code belt} while the far end is loaded, gives back what it took: when the far
     * wheel still holds the belt (it is told, and gives nothing), or when the far wheel is gone and this end owns the
     * belt - the rule check() keeps. A far wheel without the belt gave it back already.
     */
    private boolean givesBack(Level level, Belt belt) {
        if (level.getBlockEntity(belt.other()) instanceof DriveWheelBlockEntity far && tiedTo(belt, far)) {
            return far.has(this.worldPosition);
        }
        return this.owns(belt.other());
    }

    /** Broken: its belts fall here, and the far wheels let go of them. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel server) {
            for (Belt belt : List.copyOf(this.belts)) {
                if (!server.isLoaded(belt.other())) {
                    if (this.owns(belt.other())) {
                        drop(server, Vec3.atCenterOf(pos), belt); // the far end, loaded again, lets go of it quietly
                    }
                    continue; // else the far end, owning it, gives it back when it is next loaded
                }
                if (this.givesBack(server, belt)) {
                    drop(server, Vec3.atCenterOf(pos), belt);
                }
                if (server.getBlockEntity(belt.other()) instanceof DriveWheelBlockEntity far) {
                    far.remove(pos);
                }
            }
            this.belts.clear();
        }
    }

    // ---- the look ----

    /** On the client: going round while a motor drives it, running up and down as the motor's shaft does. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, DriveWheelBlockEntity wheel) {
        float target = state.getValue(DriveWheelBlock.TURNING) ? MotorBlockEntity.DEGREES_PER_TICK : 0.0F;
        wheel.step += (target - wheel.step) * 0.25F;
        if (wheel.step < 0.05F && target == 0.0F) {
            wheel.step = 0.0F;
        }
        wheel.shaft = (wheel.shaft + wheel.step) % 360.0F;
        wheel.travel = (wheel.travel + (float) (Math.toRadians(wheel.step) * Belts.RADIUS)) % 1.0F;
    }

    /**
     * How far its belts have run, in blocks, now: a point on its rim's way round, kept a block round - as its texture
     * is - rather than worked out from the shaft's angle, which goes back to 0 each turn and would set the belt back.
     */
    public float beltTravel(float partialTick) {
        return this.travel - (float) (Math.toRadians(this.step) * Belts.RADIUS) * (1.0F - partialTick);
    }

    /** Whether it is going round (on the client), as a drive: not standing still. */
    @Override
    public boolean turning() {
        return this.step > 0.0F;
    }

    @Override
    public float shaftDegrees(float partialTick) {
        return this.shaft - this.step * (1.0F - partialTick);
    }

    @Override
    public Direction spinAxis() {
        return DriveSource.positive(this.axis());
    }

    /** Its belts, out to the far wheels, for culling. */
    public AABB renderBounds() {
        AABB box = new AABB(this.worldPosition);
        for (Belt belt : this.belts) {
            box = box.minmax(new AABB(belt.other()));
        }
        return box.inflate(0.5);
    }

    // ---- saving, and what the client is told ----

    /**
     * Its belts, and the far wheel of each again as a step from this one ("BeltOffsets", in the same order): the belts
     * as they are are what an older Nookworks reads; the steps are what a structure copying the wheel keeps right - a
     * template sets the copy where the copy goes and leaves every place in its data as it was, so a copied pair of wheels
     * was belted to the first pair (r15 fix check 4, S3).
     */
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("Belts", Belt.CODEC.listOf(), List.copyOf(this.belts));
        output.putLong("WheelId", this.wheelId);
        output.store("BeltOffsets", BlockPos.CODEC.listOf(), this.belts.stream().map(b -> b.other().subtract(this.worldPosition)).toList());
    }

    /** Each far wheel read from its step from this one where they were written, else as it was (an older save). */
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.belts.clear();
        input.read("Belts", Belt.CODEC.listOf()).ifPresent(this.belts::addAll);
        long id = input.getLongOr("WheelId", 0L);
        if (id != 0) {
            this.wheelId = id; // else one saved before wheels knew theirs keeps the one it was made with
        }
        List<BlockPos> steps = input.read("BeltOffsets", BlockPos.CODEC.listOf()).orElse(List.of());
        this.stepsRead = steps.size() == this.belts.size() && !steps.isEmpty();
        if (this.stepsRead) {
            for (int i = 0; i < steps.size(); i++) {
                this.belts.set(i, this.belts.get(i).to(this.worldPosition.offset(steps.get(i))));
            }
        }
    }

    /**
     * A structure setting it down turns and mirrors it (called on the world-making thread too, the wheel in no level yet:
     * nothing is read but its own belts): each far wheel turns with it about this one, as that wheel itself was turned.
     * Belts read as they were from an older save are left as they are, as there is no knowing where they were from.
     */
    @Override
    public void applyStructureRotation(Mirror mirror, Rotation rotation) {
        if (!this.stepsRead) {
            return;
        }
        for (int i = 0; i < this.belts.size(); i++) {
            Belt belt = this.belts.get(i);
            BlockPos step = StructureTurns.rotate(StructureTurns.mirror(belt.other().subtract(this.worldPosition), mirror), rotation);
            this.belts.set(i, belt.to(this.worldPosition.offset(step)));
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The client is sent its belts, to draw them. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            this.saveAdditional(output);
            return output.buildResult();
        }
    }
}
