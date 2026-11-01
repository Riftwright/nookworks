package dev.nookworks.experience;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.nookworks.feature.DrawRights;
import dev.nookworks.fluid.OneTank;
import dev.nookworks.machine.SightGlass;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import dev.nookworks.fluid.PipePorts;

/**
 * Experience Tank: 16 buckets of Liquid Experience, worth 800 XP points.
 * <p>
 * The tank is a plain NeoForge fluid handler, so any mod's pipes can fill or drain it, and it takes any fluid
 * in {@code #c:experience} rather than only ours. Its tap pours the contents out as experience orbs, which is
 * what makes a tank useful without a machine attached.
 */
public class ExperienceTankBlockEntity extends BlockEntity implements DrawRights.Owned {
    public static final int CAPACITY = 16_000;
    /** Points poured a tick with the tap open: a full tank runs dry in about eight seconds. */
    public static final int POUR_PER_TICK = 5;

    /**
     * Who set it down; null for one set down before owners were kept. A stranger's tank set beside a claimed line takes
     * nothing from it.
     */
    private @Nullable UUID owner;
    /** What its owner may take from and give into across a claim's edge, as last asked. */
    private final DrawRights drawRights = new DrawRights();

    private final FluidStacksResourceHandler fluid = new OneTank(CAPACITY) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.typeHolder().is(ExperienceFluids.EXPERIENCE);
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previous) {
            ExperienceTankBlockEntity.this.onFluidChanged();
        }
    };

    public ExperienceTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EXPERIENCE_TANK.get(), pos, state);
    }

    public ResourceHandler<FluidResource> fluidHandler() {
        return this.fluid;
    }

    private void onFluidChanged() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.updateNeighbourForOutputSignal(this.worldPosition, this.getBlockState().getBlock());
        }
        SightGlass.show(this, this.amount(), CAPACITY);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        SightGlass.showOnLoad(this, this::amount, CAPACITY);
        PipePorts.showOnLoad(this);
    }

    public FluidResource resource() {
        return this.fluid.getResource(0);
    }

    public int amount() {
        return this.fluid.getAmountAsInt(0);
    }

    public int space() {
        return CAPACITY - this.amount();
    }

    /** Fills with our own fluid; returns how much went in. */
    public int fill(int millibuckets) {
        return Move.insert(this.fluid, FluidResource.of(ExperienceFluids.SOURCE.get()), millibuckets);
    }

    /**
     * A bucket's worth in or out, all of it or none: the bucket in the player's hand is only swapped when the whole
     * bucket moved. (A tank holding another mod's experience fluid will not take ours on top of it.)
     */
    public boolean fillBucket() {
        return Move.insertExactly(this.fluid, FluidResource.of(ExperienceFluids.SOURCE.get()), ExperienceTankBlock.BUCKET);
    }

    public boolean drainBucket() {
        FluidResource held = this.resource();
        return !held.isEmpty() && Move.extractExactly(this.fluid, held, ExperienceTankBlock.BUCKET);
    }

    public int drain(int millibuckets) {
        FluidResource held = this.resource();
        return held.isEmpty() ? 0 : Move.extract(this.fluid, held, millibuckets);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ExperienceTankBlockEntity tank) {
        if (state.getValue(ExperienceTankBlock.OPEN) && level instanceof ServerLevel server) {
            tank.pour(server, pos, state.getValue(ExperienceTankBlock.FACING));
        }
    }

    /**
     * One tick of the open tap: a few points leave the tank as an orb dropping out of the spout. Returns the points
     * poured. Less than a whole point stays in the tank rather than being rounded away.
     */
    public int pour(ServerLevel level, BlockPos pos, Direction facing) {
        int points = Math.min(POUR_PER_TICK, ExperienceFluids.points(this.amount()));
        if (points <= 0) {
            return 0;
        }
        this.drain(ExperienceFluids.millibuckets(points));
        // The spout's mouth: low on the front face, a couple of pixels out from it.
        double out = 0.5 + 2.25 / 16;
        Vec3 mouth = Vec3.atCenterOf(pos).add(facing.getStepX() * out, -0.5 + 1.5 / 16, facing.getStepZ() * out);
        ExperienceOrb orb = new ExperienceOrb(level, mouth.x, mouth.y, mouth.z, points);
        RandomSource random = level.getRandom();
        orb.setDeltaMovement(facing.getStepX() * 0.06 + (random.nextDouble() - 0.5) * 0.02, -0.02,
                facing.getStepZ() * 0.06 + (random.nextDouble() - 0.5) * 0.02);
        level.addFreshEntity(orb);
        return points;
    }

    /** 0-15 for a comparator. */
    public int comparatorSignal() {
        return this.amount() == 0 ? 0 : 1 + this.amount() * 14 / CAPACITY;
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
        this.fluid.serialize(output.child("Fluid"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        input.child("Fluid").ifPresent(this.fluid::deserialize);
    }

    /** Broken, it pours what it held out as orbs. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel server) {
            ExperienceFluids.spillAsOrbs(server, pos, this.amount());
        }
    }
}
