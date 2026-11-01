package dev.nookworks.mercury;

import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.nookworks.feature.DrawRights;
import dev.nookworks.machine.Readout;
import dev.nookworks.machine.SightGlass;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import dev.nookworks.fluid.PipePorts;

/** Mercury Tank: 16 buckets of mercury in an iron flask. Keeps its contents when broken. */
public class MercuryTankBlockEntity extends BlockEntity implements MercuryHandler, Readout, DrawRights.Owned {
    @Override
    public void readout(Consumer<Component> lines) {
        lines.accept(Component.translatable("nookworks.mercury.amount", this.mercury(), this.mercuryCapacity()));
    }

    public static final int CAPACITY = 16_000;

    /**
     * Who set it down; null for one set down before owners were kept. A stranger's tank spliced into a claimed line
     * takes no mercury from it.
     */
    private @Nullable UUID owner;
    /** What its owner may take from and give into across a claim's edge, as last asked. */
    private final DrawRights drawRights = new DrawRights();

    private final MercuryStore store = new MercuryStore(CAPACITY, this::onStoreChanged);

    public MercuryTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MERCURY_TANK.get(), pos, state);
    }

    private void onStoreChanged() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.updateNeighbourForOutputSignal(this.worldPosition, this.getBlockState().getBlock());
        }
        SightGlass.show(this, this.store.amount(), CAPACITY);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        SightGlass.showOnLoad(this, this.store::amount, CAPACITY);
        PipePorts.showOnLoad(this);
    }

    public MercuryStore store() {
        return this.store;
    }

    @Override
    public Role mercuryRole() {
        return Role.STORAGE;
    }

    @Override
    public int mercury() {
        return this.store.amount();
    }

    @Override
    public int mercuryCapacity() {
        return CAPACITY;
    }

    @Override
    public int fillMercury(int amount, boolean simulate) {
        return this.store.fill(amount, simulate);
    }

    @Override
    public int drainMercury(int amount, boolean simulate) {
        return this.store.drain(amount, simulate);
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
        this.store.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        this.store.load(input);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        this.store.set(components.getOrDefault(ModDataComponents.MERCURY.get(), 0));
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (this.store.amount() > 0) {
            components.set(ModDataComponents.MERCURY.get(), this.store.amount());
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("Mercury");
    }
}
