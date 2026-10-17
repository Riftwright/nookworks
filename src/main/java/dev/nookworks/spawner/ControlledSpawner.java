package dev.nookworks.spawner;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;

/**
 * Added to every vanilla {@link BaseSpawner} by BaseSpawnerMixin. A Spawner Controller keeps calling
 * {@link #nookworks$control}; once it stops (unpowered, unlinked, broken) the settings lapse and the spawner is
 * vanilla again. The settings are not saved: the controller owns them.
 * <p>
 * Off is the one thing the spawner keeps itself ({@link #nookworks$holdOff}, saved with it): a controller switched off
 * holds its spawners off while it is out of reach too - far off, unloaded - or a farm stopped from the other side of
 * the world would start up again as soon as someone came by (the user, 2026-10-01). The hold is let go only when the
 * controller is found switched on, gone or linked elsewhere, or when no sender touches the spawner any more
 * ({@link SpawnerHold}).
 */
public interface ControlledSpawner {
    /** Applies {@code settings} until the given game time. */
    void nookworks$control(SpawnerSettings settings, long untilGameTime);

    /** The settings in force right now, or null for vanilla behaviour. */
    @Nullable SpawnerSettings nookworks$activeControl(Level level);

    /** Held off by the controller at {@code by} from now on. Returns whether that is news (to be saved). */
    boolean nookworks$holdOff(GlobalPos by);

    /** No longer held off. Returns whether it was. */
    boolean nookworks$release();

    /** The controller holding it off, or null. */
    @Nullable GlobalPos nookworks$heldBy();

    /**
     * On a client: whether the server says it is held off ({@code NookworksOff} in what it sends - which controller
     * is kept from the clients), so its mob stands still and it gives no flame.
     */
    boolean nookworks$shownOff();

    /** The mob this spawner makes next, or null for an empty spawner. */
    @Nullable EntityType<?> nookworks$entityType();

    static ControlledSpawner of(BaseSpawner spawner) {
        return (ControlledSpawner) spawner;
    }
}
