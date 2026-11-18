package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.monster.piglin.AbstractPiglin;

/**
 * Lets a Barter Terminal read whether a piglin was already kept from turning into a zombified piglin before it kept it
 * ({@code dev.nookworks.barter.PiglinShelter}), so that letting it go puts the flag back as it was: vanilla can set the
 * flag and read it, but reads it only inside the class.
 */
@Mixin(AbstractPiglin.class)
public interface AbstractPiglinAccessor {
    @Invoker("isImmuneToZombification")
    boolean nookworks$isImmuneToZombification();

    /** 26.3's {@code setTimeInOverworld}: 1.21.1 has the field and no setter ({@code port/core/Piglins}). */
    @Accessor("timeInOverworld")
    void nookworks$setTimeInOverworld(int ticks);
}
