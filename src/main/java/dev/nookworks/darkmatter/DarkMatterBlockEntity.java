package dev.nookworks.darkmatter;

import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Keeps a Dark Matter block's disc in {@link DarkMatter} while it is loaded, on each side. The server looks over the
 * disc twice a second for anything giving light and sets DARKENING to match; each side darkens or not as DARKENING
 * says, so a lamp switched on in the disc lifts the dark and switched off lets it fall again.
 * <p>
 * The server's disc is whole at once. The client's spreads: set down, or freed of the light that beat it, the dark
 * rolls out from the block a ring every other tick, a ring of smoke along its edge, and draws back the same way. A
 * block that simply comes into view is dark already.
 */
public class DarkMatterBlockEntity extends BlockEntity {
    /** How often, in ticks, the server looks for lights in the disc. */
    private static final int LOOK_EVERY = 10;
    /** Ticks for the dark to spread one more ring, on the client. */
    private static final int SPREAD_EVERY = 2;
    /** The block event the server sends when the block is set down, so the client spreads it from nothing. */
    public static final int EVENT_PLACED = 1;
    private boolean darkening;
    /** Client: how far the dark has spread so far; -1 not at all. */
    private int reach = -1;

    public DarkMatterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DARK_MATTER.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && this.level.isClientSide()) {
            this.reach = this.getBlockState().getValue(DarkMatterBlock.DARKENING) ? DarkMatter.RADIUS : -1;
            DarkMatter.reach(this.level, this.worldPosition, this.reach);
        } else {
            this.follow(this.getBlockState());
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && (this.darkening || this.reach >= 0)) {
            this.darkening = false;
            this.reach = -1;
            DarkMatter.set(this.level, this.worldPosition, false);
        }
    }

    /** Set down just now: the client starts the dark from the block itself. */
    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == EVENT_PLACED) { // true on the server too, or the event is never sent
            if (this.level != null && this.level.isClientSide()) {
                this.reach = -1;
                DarkMatter.reach(this.level, this.worldPosition, -1);
            }
            return true;
        }
        return super.triggerEvent(id, param);
    }

    private void follow(BlockState state) {
        boolean darkening = state.getValue(DarkMatterBlock.DARKENING);
        if (this.level != null && darkening != this.darkening) {
            this.darkening = darkening;
            DarkMatter.set(this.level, this.worldPosition, darkening);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DarkMatterBlockEntity entity) {
        if (Math.floorMod(level.getGameTime() + pos.asLong(), LOOK_EVERY) == 0) {
            boolean darkening = !DarkMatter.lit(level, pos);
            if (darkening != state.getValue(DarkMatterBlock.DARKENING)) {
                state = state.setValue(DarkMatterBlock.DARKENING, darkening);
                level.setBlock(pos, state, Block.UPDATE_ALL);
            }
        }
        entity.follow(state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, DarkMatterBlockEntity entity) {
        int target = state.getValue(DarkMatterBlock.DARKENING) ? DarkMatter.RADIUS : -1;
        if (entity.reach == target || level.getGameTime() % SPREAD_EVERY != 0) {
            return;
        }
        boolean out = target > entity.reach;
        entity.reach += out ? 1 : -1;
        DarkMatter.reach(level, pos, entity.reach);
        edge(level, pos, out ? entity.reach : entity.reach + 1, out);
    }

    /** Smoke along the ring the dark has just reached (or left), low over the ground, drifting the way it goes. */
    private static void edge(Level level, BlockPos centre, int ring, boolean out) {
        if (ring < 0) {
            return;
        }
        var random = level.getRandom();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -ring; dx <= ring; dx++) {
            int rest = ring - Math.abs(dx);
            for (int dz : rest == 0 ? new int[] {0} : new int[] {-rest, rest}) {
                at.set(centre.getX() + dx, centre.getY(), centre.getZ() + dz);
                if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
                    continue;
                }
                double len = Math.max(1, Math.abs(dx) + Math.abs(dz));
                double push = (out ? 0.04 : -0.04) / len;
                level.addParticle(ParticleTypes.LARGE_SMOKE,
                        at.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6, at.getY() + 0.1 + random.nextDouble() * 0.3,
                        at.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, dx * push, 0.005, dz * push);
            }
        }
        if (ring == 0 && out) { // the start: a puff of violet from the block itself
            for (int i = 0; i < 12; i++) {
                level.addParticle(ParticleTypes.REVERSE_PORTAL, centre.getX() + 0.5, centre.getY() + 0.5,
                        centre.getZ() + 0.5, (random.nextDouble() - 0.5) * 0.6, random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.6);
            }
        }
    }
}
