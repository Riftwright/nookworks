package dev.nookworks.barter;

import dev.nookworks.Nookworks;
import dev.nookworks.mixin.AbstractPiglinAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Piglins kept by a Barter Terminal's room. While the room has the Nether's air in it, the terminal marks each piglin in
 * it with vanilla's own flag that it is not to turn into a zombified piglin (the one a command sets): it stops the
 * change on the server and the shaking on the client alike, and anything reading the flag reads it right. The mark
 * says which terminal set it, and whether the flag was set before.
 *
 * <p>Any piglin so marked is looked at again every second: once it is no longer in a kept room - carried off in a Mob
 * Bottle, its terminal broken, or the air run out - the flag goes back to what it was, and vanilla's fifteen seconds
 * start to run.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class PiglinShelter {
    private static final String TERMINAL = "nookworks:barter_terminal";
    private static final String WAS_IMMUNE = "nookworks:was_immune";
    /** The dimension of the terminal that keeps it. */
    private static final String DIMENSION = "nookworks:barter_dimension";
    /** Further than this from its terminal, a piglin is in no room of it, whether the terminal is loaded or not. */
    private static final double ROOM_REACH = 32.0;

    /** Keeps {@code piglin} for the terminal at {@code terminal}. */
    public static void keep(AbstractPiglin piglin, BlockPos terminal) {
        CompoundTag data = piglin.getPersistentData();
        if (!data.contains(TERMINAL)) {
            data.putBoolean(WAS_IMMUNE, ((AbstractPiglinAccessor) piglin).nookworks$isImmuneToZombification());
        }
        data.putLong(TERMINAL, terminal.asLong());
        data.putString(DIMENSION, piglin.level().dimension().identifier().toString());
        piglin.setImmuneToZombification(true);
        piglin.setTimeInOverworld(0);
    }

    /** Lets {@code piglin} go if it is the terminal at {@code terminal} that keeps it. */
    public static void letGo(AbstractPiglin piglin, BlockPos terminal) {
        if (piglin.getPersistentData().getLongOr(TERMINAL, Long.MIN_VALUE) == terminal.asLong()) {
            letGo(piglin);
        }
    }

    /** Lets {@code piglin} go: its flag as it was before a terminal kept it. */
    public static void letGo(AbstractPiglin piglin) {
        CompoundTag data = piglin.getPersistentData();
        if (data.contains(TERMINAL)) {
            piglin.setImmuneToZombification(data.getBooleanOr(WAS_IMMUNE, false));
            data.remove(TERMINAL);
            data.remove(WAS_IMMUNE);
            data.remove(DIMENSION);
        }
    }

    /**
     * A piglin saved to be carried off (in a Mob Bottle): saved as it was before it was kept, so let out anywhere it is
     * no one's - not immune to the change for good, nor spared despawning, because of a room far off.
     */
    public static void forgetIn(CompoundTag saved) {
        CompoundTag data = saved.getCompoundOrEmpty("NeoForgeData");
        if (!data.contains(TERMINAL)) {
            return;
        }
        saved.putBoolean("IsImmuneToZombification", data.getBooleanOr(WAS_IMMUNE, false));
        data.remove(TERMINAL);
        data.remove(WAS_IMMUNE);
        data.remove(DIMENSION);
        if (data.isEmpty()) {
            saved.remove("NeoForgeData");
        } else {
            saved.put("NeoForgeData", data);
        }
    }

    public static boolean kept(AbstractPiglin piglin) {
        return piglin.getPersistentData().contains(TERMINAL);
    }

    @SubscribeEvent
    static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractPiglin piglin) || piglin.level().isClientSide() || piglin.tickCount % 20 != 7) {
            return;
        }
        CompoundTag data = piglin.getPersistentData();
        if (!data.contains(TERMINAL)) {
            return;
        }
        BlockPos at = BlockPos.of(data.getLongOr(TERMINAL, 0L));
        boolean away = !data.getStringOr(DIMENSION, "").equals(piglin.level().dimension().identifier().toString())
                || !at.closerToCenterThan(piglin.position(), ROOM_REACH);
        if (away) {
            letGo(piglin); // taken off somewhere else - its terminal's room is not here, loaded or not
            return;
        }
        if (!piglin.level().isLoaded(at)) {
            return; // its terminal is not loaded to ask: kept as it was until it is
        }
        if (!(piglin.level().getBlockEntity(at) instanceof BarterTerminalBlockEntity terminal && terminal.keeps(piglin))) {
            letGo(piglin);
        }
    }

    /** A kept piglin does not despawn: it was brought there, in a bottle, from the Nether. */
    @SubscribeEvent
    static void onDespawn(MobDespawnEvent event) {
        if (event.getEntity() instanceof AbstractPiglin piglin && kept(piglin)) {
            event.setResult(MobDespawnEvent.Result.DENY);
        }
    }

    private PiglinShelter() {}
}
