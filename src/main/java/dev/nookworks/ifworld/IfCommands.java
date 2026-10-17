package dev.nookworks.ifworld;

import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import dev.nookworks.Nookworks;
import dev.nookworks.travel.DaemonDestinations;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /nookworks if}, for operators: {@code info} (its number and where its mirrors stand), {@code home <players>}
 * (out of the If and home, for whoever is lost there), and {@code reset} (the If taken away at the next start, so its
 * mirror can be rolled again; whoever is in it now is sent home first).
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class IfCommands {
    private IfCommands() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Nookworks.MODID).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("if")
                        .then(Commands.literal("info").executes(c -> info(c.getSource())))
                        .then(Commands.literal("home").then(Commands.argument("players", EntityArgument.players())
                                .executes(c -> home(c.getSource(), EntityArgument.getPlayers(c, "players")))))
                        .then(Commands.literal("reset").executes(c -> reset(c.getSource())))));
    }

    private static int info(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        IfWorld world = IfWorld.get(server);
        if (world.resetPending()) {
            source.sendSuccess(() -> Component.translatable("commands.nookworks.if.reset_pending", world.number()), false);
        } else if (world.rolled()) {
            source.sendSuccess(() -> Component.translatable("commands.nookworks.if.number", world.number()), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.nookworks.if.not_rolled", world.faces().size(), IfWorld.THROWS), false);
        }
        for (GlobalPos mirror : new GlobalPos[] {world.homeMirror(), world.mirrorFor(IfWorld.KEY)}) {
            if (mirror != null) {
                BlockPos at = mirror.pos();
                source.sendSuccess(() -> Component.translatable("commands.nookworks.if.mirror", DaemonDestinations.name(mirror.dimension()),
                        at.getX(), at.getY(), at.getZ()), false);
            }
        }
        return world.faces().size();
    }

    private static int home(CommandSourceStack source, Collection<ServerPlayer> players) {
        int sent = 0;
        for (ServerPlayer player : players) {
            if (player.level().dimension().equals(IfWorld.KEY)) {
                IfTravel.sendHome(player);
                sent++;
            }
        }
        int count = sent;
        source.sendSuccess(() -> Component.translatable("commands.nookworks.if.home", count), true);
        return sent;
    }

    private static int reset(CommandSourceStack source) throws CommandSyntaxException {
        MinecraftServer server = source.getServer();
        IfWorld world = IfWorld.get(server);
        if (!world.rolled()) {
            source.sendFailure(Component.translatable("commands.nookworks.if.not_rolled", world.faces().size(), IfWorld.THROWS));
            return 0;
        }
        ServerLevel level = server.getLevel(IfWorld.KEY);
        List<ServerPlayer> inside = level == null ? List.of() : List.copyOf(level.players());
        inside.forEach(player -> {
            IfTravel.sendHome(player);
            player.sendSystemMessage(Component.translatable("message.nookworks.if.reset_sent_home")); // (r15: nothing was said)
        });
        world.requestReset();
        source.sendSuccess(() -> Component.translatable("commands.nookworks.if.reset", world.number(), inside.size()), true);
        return 1;
    }
}
