package dev.nookworks.block.entity;

import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;

import dev.nookworks.block.DiceBlock;
import dev.nookworks.block.DiceTumble;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A die's roll ({@link DiceTumble}): the seed it was rolled with, when, and the pose it was rolled from (and the way it
 * was thrown, from a dispenser) - all a client needs to work out the same roll and draw it, sent to it as the roll
 * starts. The roll itself is worked out once a side and kept. Once over, the roll's last pose is where the die lies -
 * off the middle of its block and turned as it came to rest - which it is drawn in, outlined and bumped into at, and
 * which its next roll starts from.
 */
public class DiceBlockEntity extends BlockEntity {
    private static final Codec<List<Double>> POSE_CODEC = Codec.DOUBLE.listOf();

    private long seed;
    private long start = -1;
    /** The pose the roll started from (as {@link DiceTumble.Result#at}), or null for the middle of the block. */
    private double @Nullable [] from;
    private @Nullable Direction thrown;
    private DiceTumble.@Nullable Result tumble;
    /** On the client: the last impact already heard, as a tick of the roll. */
    private double heard = -1;
    private double @Nullable [] shapePose;
    private @Nullable VoxelShape shape;

    public DiceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DICE.get(), pos, state);
    }

    /** A roll starting now, from {@code from}: the die as it lies ({@link #lying}). */
    public DiceTumble.Result start(long seed, long gameTime, double[] from, @Nullable Direction thrownTowards) {
        this.seed = seed;
        this.start = gameTime;
        this.from = from.clone();
        // as it is read back (loadAdditional), so the server and every client work out the same roll
        this.thrown = thrownTowards == null || thrownTowards.getAxis().isVertical() ? null : thrownTowards;
        this.tumble = null;
        this.heard = -1;
        this.setChanged();
        return this.tumble();
    }

    /**
     * Set down turned or mirrored by a structure: its last roll forgotten, so it lies in the middle of its block as its
     * turned state has it, rather than drawn and struck at the spot and pose it was saved with (r21, 2026-11-06).
     */
    @Override
    public void applyStructureRotation(Mirror mirror, Rotation rotation) {
        if (mirror != Mirror.NONE || rotation != Rotation.NONE) {
            this.start = -1;
            this.from = null;
            this.thrown = null;
            this.tumble = null;
            this.setChanged();
        }
    }

    /** The roll under way or last made, or null if none has been made. */
    public DiceTumble.@Nullable Result tumble() {
        if (this.start < 0) {
            return null;
        }
        if (this.tumble == null) {
            BlockState state = this.getBlockState();
            double[] origin = this.from != null ? this.from
                    : DiceTumble.centred(state.getValue(DiceBlock.VALUE), state.getValue(DiceBlock.FACING));
            this.tumble = DiceTumble.roll(this.seed, origin, this.thrown);
        }
        return this.tumble;
    }

    /**
     * Where and how the die lies, as {@link DiceTumble.Result#at}: where its last roll left it, or, while it rolls,
     * where it was tossed up from; a die never rolled (or one coming in from a dispenser) lies in the middle of its
     * block as its state has it.
     */
    public double[] lying(BlockState state) {
        DiceTumble.Result roll = this.tumble();
        if (roll != null && state.getValue(DiceBlock.ROLL) == 0) {
            return roll.rest();
        }
        if (roll != null && this.from != null && this.thrown == null) {
            return this.from.clone();
        }
        return DiceTumble.centred(state.getValue(DiceBlock.VALUE), state.getValue(DiceBlock.FACING));
    }

    /**
     * The die's shape where it lies: its square foot, turned as it is, made of a few strips across (shapes are boxes on
     * the block's axes), as tall as the die. Kept until the die moves.
     */
    public VoxelShape shape(BlockState state) {
        double[] pose = this.lying(state);
        if (this.shape == null || this.shapePose == null || !Arrays.equals(pose, this.shapePose)) {
            this.shapePose = pose;
            this.shape = footprint(pose);
        }
        return this.shape;
    }

    /** The die's foot in {@code pose}, lying on a face, cut into strips across x; a die not lying flat, its box. */
    public static VoxelShape footprint(double[] pose) {
        double[] box = DiceTumble.extent(pose);
        double top = clamp(box[4]);
        double[][] foot = DiceTumble.footCorners(pose);
        if (foot == null) {
            return Shapes.box(clamp(box[0]), clamp(box[1]), clamp(box[2]), clamp(box[3]), top, clamp(box[5]));
        }
        int strips = 4;
        VoxelShape out = Shapes.empty();
        for (int i = 0; i < strips; i++) {
            double x0 = box[0] + (box[3] - box[0]) * i / strips;
            double x1 = box[0] + (box[3] - box[0]) * (i + 1) / strips;
            double zMin = Double.MAX_VALUE;
            double zMax = -Double.MAX_VALUE;
            for (int a = 0; a < 4; a++) {
                double[] p = foot[a];
                double[] q = foot[(a + 1) % 4];
                if (p[0] >= x0 && p[0] <= x1) {
                    zMin = Math.min(zMin, p[1]);
                    zMax = Math.max(zMax, p[1]);
                }
                for (double x : new double[] {x0, x1}) {
                    if (p[0] != q[0] && (p[0] - x) * (q[0] - x) <= 0) {
                        double z = p[1] + (q[1] - p[1]) * (x - p[0]) / (q[0] - p[0]);
                        zMin = Math.min(zMin, z);
                        zMax = Math.max(zMax, z);
                    }
                }
            }
            if (zMax > zMin && x1 > x0) {
                out = Shapes.or(out, Shapes.box(clamp(x0), 0, clamp(zMin), clamp(x1), top, clamp(zMax)));
            }
        }
        return out.isEmpty() ? Shapes.box(0.25, 0, 0.25, 0.75, 0.5, 0.75) : out;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /** How far into its roll the die is, in ticks. */
    public double elapsed(float partialTick) {
        return this.level == null || this.start < 0 ? 0 : this.level.getGameTime() - this.start + partialTick;
    }

    /** Client side: the clack of each corner that strikes hard, as the drawn die strikes. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, DiceBlockEntity die) {
        DiceTumble.Result roll = die.tumble();
        if (roll == null || state.getValue(DiceBlock.ROLL) == 0) {
            return;
        }
        double now = die.elapsed(0);
        double[] impacts = roll.impacts();
        for (int i = 0; i + 1 < impacts.length; i += 2) {
            double at = impacts[i];
            if (at > die.heard && at <= now) {
                float volume = (float) Math.min(0.7, 0.15 + impacts[i + 1] * 0.12);
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, SoundEvents.BONE_BLOCK_HIT, SoundSource.BLOCKS,
                        volume, 1.4F + (float) ((i * 0.37) % 0.4), false);
                die.heard = at;
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.start >= 0) {
            output.putLong("Seed", this.seed);
            output.putLong("Start", this.start);
            if (this.from != null) {
                output.store("From", POSE_CODEC, Arrays.stream(this.from).boxed().toList());
            }
            if (this.thrown != null) {
                output.putString("Thrown", this.thrown.getSerializedName());
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        long seed = input.getLongOr("Seed", 0L);
        long start = input.getLongOr("Start", -1L);
        double[] from = input.read("From", POSE_CODEC).map(list -> list.stream().mapToDouble(Double::doubleValue).toArray())
                .filter(DiceBlockEntity::sane).orElse(null);
        if (from == null && input.getString("FromFacing").isPresent()) {
            // a roll saved before dice lay where they came to rest: rolled from the middle, as its state was then
            Direction facing = Direction.byName(input.getStringOr("FromFacing", "north"));
            from = DiceTumble.centred(Math.max(1, Math.min(6, input.getIntOr("FromValue", 1))),
                    facing == null || facing.getAxis().isVertical() ? Direction.NORTH : facing);
        }
        Direction thrown = input.getString("Thrown").map(Direction::byName).orElse(null);
        boolean changed = seed != this.seed || start != this.start || !Arrays.equals(from, this.from);
        this.seed = seed;
        this.start = start;
        this.from = from;
        this.thrown = thrown == null || thrown.getAxis().isVertical() ? null : thrown;
        if (changed) {
            this.tumble = null;
            this.heard = -1;
            this.shape = null;
        }
    }

    /** A pose read from a save or the network: seven numbers, in the block, its turn a unit quaternion. */
    private static boolean sane(double[] pose) {
        if (pose.length != 7) {
            return false;
        }
        for (double d : pose) {
            if (!Double.isFinite(d)) {
                return false;
            }
        }
        double len = pose[3] * pose[3] + pose[4] * pose[4] + pose[5] * pose[5] + pose[6] * pose[6];
        return pose[0] >= 0 && pose[0] <= 1 && pose[1] >= 0 && pose[1] <= 1 && pose[2] >= 0 && pose[2] <= 1 && Math.abs(len - 1) < 1e-6;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The client is sent the roll as it starts, to draw it. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            this.saveAdditional(output);
            return output.buildResult();
        }
    }
}
