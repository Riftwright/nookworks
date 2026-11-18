package dev.nookworks.port.core;

import dev.nookworks.mixin.AbstractPiglinAccessor;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;

/**
 * 26.3's {@code AbstractPiglin.setTimeInOverworld(int)}: how long a piglin has been out of the Nether, past 300 ticks
 * of which it turns into a zombified piglin. 1.21.1 keeps the count in the protected field {@code timeInOverworld}
 * with no setter: set through the accessor mixin.
 */
public final class Piglins {
    private Piglins() {}

    public static void setTimeInOverworld(AbstractPiglin piglin, int ticks) {
        ((AbstractPiglinAccessor) piglin).nookworks$setTimeInOverworld(ticks);
    }
}
