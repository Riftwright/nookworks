package dev.nookworks.ifworld;

import java.util.function.Supplier;

import dev.nookworks.Nookworks;
import dev.nookworks.block.Clicks;
import dev.nookworks.travel.TravelLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.PortalShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The If keeps to its own ways in and out: its mirror, the Dock-a-Daemon Door and a Lode Pearl (let through since
 * 2026-10-10, the user's word). A nether portal lit there flickers out in a burst of static, an end portal frame will
 * not take an eye, and no other gate or mod crosses into or out of it - only an operator (by command) does. Moving within the If (a pearl, a command, another mod's teleport) is the
 * If's own business and passes.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class IfGates {
    /** How many of our own trips are under way (on the server thread): those cross. */
    private static int passes;
    /** In a player's persistent data: the If (its generation) they logged out in. */
    private static final String LEFT_IN = Nookworks.MODID + ":left_in_if";

    private IfGates() {}

    /** Runs {@code trip}, one of the mirror's or the door's, letting it cross into or out of the If. */
    public static <T> T passing(Supplier<T> trip) {
        passes++;
        try {
            return trip.get();
        } finally {
            passes--;
        }
    }

    @SubscribeEvent
    static void onTravel(EntityTravelToDimensionEvent event) {
        Entity entity = event.getEntity();
        if (event.getDimension().equals(entity.level().dimension())) {
            return; // NeoForge asks for a move within one dimension too (an ender pearl's): that crosses nothing
        }
        boolean out = entity.level().dimension().equals(IfWorld.KEY);
        if (passes > 0 || !out && !event.getDimension().equals(IfWorld.KEY)
                || entity instanceof ServerPlayer player && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return;
        }
        event.setCanceled(true);
        if (out && entity.level() instanceof ServerLevel level) {
            glitch(level, entity.blockPosition(), 1);
        }
        if (entity instanceof Player player) {
            player.sendOverlayMessage(Component.translatable("message.nookworks.if.sealed"));
        }
    }

    /**
     * A nether portal lit in the If flickers out. Vanilla opens portals only in the overworld and the Nether, and asks
     * nobody anywhere else, so it is the lighting that is caught: flint and steel or a fire charge used where the fire
     * would stand inside a whole frame.
     */
    @SubscribeEvent
    static void onLight(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        ItemStack stack = event.getItemStack();
        if (!level.dimension().equals(IfWorld.KEY) || event.getFace() == null || !stack.is(Items.FLINT_AND_STEEL) && !stack.is(Items.FIRE_CHARGE)) {
            return;
        }
        BlockPos fire = event.getPos().relative(event.getFace());
        if (PortalShape.findEmptyPortalShape(level, fire, Direction.Axis.X).isPresent()) {
            event.setCanceled(true);
            event.setCancellationResult(Clicks.REFUSED); // the click ends here: cancelled with PASS, the other hand's item was used next (r21)
            if (level instanceof ServerLevel server) {
                glitch(server, fire, 3);
            }
        }
    }

    /** Logged out in the If: which If it was, to know it again at the next login. */
    @SubscribeEvent
    static void onLogOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CompoundTag data = player.getPersistentData();
            if (player.level().dimension().equals(IfWorld.KEY)) {
                data.putInt(LEFT_IN, IfWorld.get(player.level().getServer()).generation());
            } else {
                data.remove(LEFT_IN);
            }
        }
    }

    /**
     * Back after the If they logged out in was reset: vanilla, finding no such dimension (or a new If by that name), would
     * put them at the same place - in rock, perhaps, or in the air. Sent to their respawn point instead (the world's
     * spawn, if that was in the If).
     */
    @SubscribeEvent
    static void onLogIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.getPersistentData().contains(LEFT_IN)) {
            return;
        }
        int generation = player.getPersistentData().getIntOr(LEFT_IN, -1);
        player.getPersistentData().remove(LEFT_IN);
        MinecraftServer server = player.level().getServer();
        if (player.level().dimension().equals(IfWorld.KEY) && generation == IfWorld.get(server).generation()) {
            return; // the If they left
        }
        server.schedule(new TickTask(server.getTickCount(), () -> {
            if (!player.isRemoved()) {
                IfTravel.toRespawn(player);
                player.sendSystemMessage(Component.translatable("message.nookworks.if.gone"));
                if (!player.level().dimension().equals(IfWorld.KEY)) {
                    // Set down in a new If by its name as they came back, they were counted as having been there, and
                    // their place in the old one kept as where they last were in it (where its first mirror or door went,
                    // in a cave perhaps): the new If is one they have not seen.
                    player.setData(TravelLog.TYPE, TravelLog.of(player).withoutIf());
                }
            }
        }));
    }

    /**
     * A respawn at a bed in the If while the If waits for its reset goes to the world's spawn instead: the mirror, the door
     * and the pearl all keep everyone out until the next start (r15: a bed let one in).
     */
    @SubscribeEvent
    static void onRespawnPosition(net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent event) {
        var transition = event.getTeleportTransition();
        ServerPlayer player = (ServerPlayer) event.getEntity();
        if (transition.newLevel().dimension().equals(IfWorld.KEY) && IfWorld.get(player.level().getServer()).resetPending()) {
            event.setTeleportTransition(net.minecraft.world.level.portal.TeleportTransition.createDefault(player,
                    net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
            player.sendSystemMessage(Component.translatable("message.nookworks.if.reset_pending"));
        }
    }

    /** An eye of ender at an end portal frame in the If: not taken. */
    @SubscribeEvent
    static void onEye(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.dimension().equals(IfWorld.KEY) && event.getItemStack().is(Items.ENDER_EYE)
                && level.getBlockState(event.getPos()).is(Blocks.END_PORTAL_FRAME)) {
            event.setCanceled(true);
            event.setCancellationResult(Clicks.REFUSED);
            if (level instanceof ServerLevel server) {
                glitch(server, event.getPos().above(), 1);
                event.getEntity().sendOverlayMessage(Component.translatable("message.nookworks.if.sealed"));
            }
        }
    }

    /** Static where a gate would open: grey and magenta specks scattering, and a dying hum. */
    private static void glitch(ServerLevel level, BlockPos pos, int size) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        for (int colour : new int[] {0xFFFFFF, 0x3A3A3A, 0xE040E0, 0x40E0E0}) {
            level.sendParticles(new DustParticleOptions(colour, 1.0F), x, y + size * 0.5, z, 10 * size, 0.4 * size, 0.8 * size, 0.4 * size, 0.0);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + size * 0.5, z, 12 * size, 0.3 * size, 0.6 * size, 0.3 * size, 0.05);
        level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 1.7F);
    }
}
