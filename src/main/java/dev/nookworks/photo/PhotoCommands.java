package dev.nookworks.photo;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import dev.nookworks.Nookworks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /nookworks photo}, for operators: {@code info <number>} (who took it, which day, whether it was printed) and
 * {@code remove <number>} (taken out of the world: every poster of it shows blank paper from then on). A photo's number
 * is in its poster's tooltip with advanced tooltips on (F3+H).
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class PhotoCommands {
    private PhotoCommands() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Nookworks.MODID).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("photo")
                        .then(Commands.literal("info").then(Commands.argument("number", IntegerArgumentType.integer(1))
                                .executes(c -> info(c.getSource(), IntegerArgumentType.getInteger(c, "number")))))
                        .then(Commands.literal("remove").then(Commands.argument("number", IntegerArgumentType.integer(1))
                                .executes(c -> remove(c.getSource(), IntegerArgumentType.getInteger(c, "number")))))));
    }

    private static int info(CommandSourceStack source, int number) {
        Photo photo = Photos.get(source.getServer(), number);
        if (photo == null) {
            source.sendFailure(Component.translatable("commands.nookworks.photo.none", number));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(photo.printed() ? "commands.nookworks.photo.info.printed" : "commands.nookworks.photo.info",
                number, photo.author(), photo.day()), false);
        return 1;
    }

    private static int remove(CommandSourceStack source, int number) {
        switch (Photos.remove(source.getServer(), number)) {
            case NONE -> {
                source.sendFailure(Component.translatable("commands.nookworks.photo.none", number));
                return 0;
            }
            case FAILED -> {
                source.sendFailure(Component.translatable("commands.nookworks.photo.remove_failed", number));
                return 0;
            }
            default -> {
                source.sendSuccess(() -> Component.translatable("commands.nookworks.photo.removed", number), true);
                return 1;
            }
        }
    }
}
