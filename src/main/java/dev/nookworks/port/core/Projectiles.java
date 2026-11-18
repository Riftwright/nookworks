package dev.nookworks.port.core;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.projectile.Projectile;

/**
 * 26.3's protected {@code Projectile.owner} (an {@code EntityReference}, set whether or not the owner can be found just
 * now), which a projectile reads as "it was thrown by someone": {@code this.owner != null}. 1.21.1 keeps the owner's
 * id in the private field {@code ownerUUID}; the game runs with Mojang's names on NeoForge, so it is reached by that
 * name, as {@link Piglins} does.
 */
public final class Projectiles {
    private static final VarHandle OWNER_UUID;

    static {
        try {
            OWNER_UUID = MethodHandles.privateLookupIn(Projectile.class, MethodHandles.lookup())
                    .findVarHandle(Projectile.class, "ownerUUID", UUID.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Projectiles() {}

    /** The id of whoever threw or shot {@code projectile}, found in the world or not; null for nobody. */
    public static @Nullable UUID ownerId(Projectile projectile) {
        return (UUID) OWNER_UUID.get(projectile);
    }
}
