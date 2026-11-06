package dev.nookworks.feature;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.nookworks.Nookworks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;

/**
 * Other mods' recipes that our parts hold up: an add-on built on Nookworks (nookTechnology) puts our conditions -
 * {@code nookworks:feature} and {@code nookworks:available} - on its recipes, so a part switched off takes those with
 * it, and with the Motor or the Machine Chassis off most of nookTechnology goes. It is allowed, a pack's say, but said
 * (the user, 2026-11-03: 「Tech入れている環境で、機械の機能をオフにしようとしたら、Tech使えなくなるよ?的な警告」, as Nook of
 * the Dead's If is, {@code NookOfTheDeadWarning}): the parts list counts, by mod, the recipes a change stops before it
 * is saved ({@code PartsLossScreen}), and a world whose parts already stop some says so in the log as the server starts
 * and to an operator or the single-player world's owner as they join.
 * <p>
 * Read from the other mods' own jars (every {@code data/<ns>/recipe/} file naming one of our conditions), once, so it
 * knows them before any world is made. A condition list it cannot read plainly - ours under a {@code not} or an
 * {@code or} - is left out: better no warning than a wrong one.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class AddonImpact {
    /** A recipe of another mod held up by our conditions: the parts it names, and the items it needs to be had. */
    public record Gate(String mod, List<String> parts, List<Needs> needs) {}

    /** {@code nookworks:available}: the items, all of them or ({@code any}) one. */
    public record Needs(List<String> items, boolean any) {}

    private static @Nullable List<Gate> gates;

    private AddonImpact() {}

    /** Every other mod's recipe our conditions hold up, read once from the mods' jars. */
    public static synchronized List<Gate> gates() {
        if (gates == null) {
            gates = scan();
        }
        return gates;
    }

    private static List<Gate> scan() {
        List<Gate> found = new ArrayList<>();
        if (ModList.get() == null) {
            return found;
        }
        for (IModFileInfo file : ModList.get().getModFiles()) {
            List<String> ids = file.getMods().stream().map(IModInfo::getModId).toList();
            if (ids.isEmpty() || ids.contains(Nookworks.MODID) || ids.contains("minecraft") || ids.contains("neoforge")) {
                continue;
            }
            String mod = ids.getFirst();
            try {
                file.getFile().getContents().visitContent("data", (path, resource) -> {
                    if (!path.endsWith(".json") || !path.contains("/recipe/")) {
                        return;
                    }
                    try {
                        String text = new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        if (!text.contains("\"nookworks:")) {
                            return; // most of another mod's recipes: not parsed at all (r20)
                        }
                        Gate gate = gate(mod, JsonParser.parseString(text));
                        if (gate != null) {
                            found.add(gate);
                        }
                    } catch (IOException | RuntimeException e) {
                        // not a recipe we can read: nothing of ours to warn of
                    }
                });
            } catch (RuntimeException e) {
                Nookworks.LOGGER.debug("Could not read {}'s recipes for our parts' conditions", mod, e);
            }
        }
        return List.copyOf(found);
    }

    /** The gate one recipe file makes, or null when our conditions do not hold it up (or not plainly). */
    public static @Nullable Gate gate(String mod, JsonElement json) {
        if (!json.isJsonObject() || !json.getAsJsonObject().has("neoforge:conditions")) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        List<Needs> needs = new ArrayList<>();
        if (!collect(json.getAsJsonObject().getAsJsonArray("neoforge:conditions"), parts, needs)) {
            return null;
        }
        return parts.isEmpty() && needs.isEmpty() ? null : new Gate(mod, List.copyOf(parts), List.copyOf(needs));
    }

    /** Our conditions in a list that all must hold; false when one of ours sits where it does not simply have to hold. */
    private static boolean collect(JsonArray conditions, List<String> parts, List<Needs> needs) {
        for (JsonElement element : conditions) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject condition = element.getAsJsonObject();
            String type = condition.has("type") ? condition.get("type").getAsString() : "";
            switch (type) {
                case "nookworks:feature" -> parts.add(condition.get("feature").getAsString());
                case "nookworks:setting" -> {
                    // {"setting": "feature.X"} is part X's start-up switch: as a part, if it must be on
                    String setting = condition.get("setting").getAsString();
                    boolean is = !condition.has("is") || condition.get("is").getAsBoolean();
                    if (setting.startsWith("feature.")) {
                        if (!is) {
                            return false;
                        }
                        parts.add(setting.substring("feature.".length()));
                    }
                }
                case "nookworks:available" -> {
                    List<String> items = new ArrayList<>();
                    condition.getAsJsonArray("items").forEach(item -> items.add(item.getAsString()));
                    needs.add(new Needs(List.copyOf(items), condition.has("any") && condition.get("any").getAsBoolean()));
                }
                case "neoforge:and" -> {
                    if (!collect(condition.getAsJsonArray("values"), parts, needs)) {
                        return false;
                    }
                }
                case "neoforge:not", "neoforge:or" -> {
                    if (condition.toString().contains("\"nookworks:")) {
                        return false;
                    }
                }
                default -> { }
            }
        }
        return true;
    }

    /** Whether {@code gate} holds with the parts {@code on} has on and {@code lost} the items of ours nothing reaches. */
    public static boolean holds(Gate gate, Predicate<String> on, Set<String> lost) {
        Predicate<String> had = id -> Features.enabledByPart(id, on) && !lost.contains(id);
        return gate.parts().stream().allMatch(part -> Features.enabled(part, on))
                && gate.needs().stream().allMatch(n -> n.any() ? n.items().stream().anyMatch(had) : n.items().stream().allMatch(had));
    }

    /** By mod, how many of {@code gates} hold with {@code before}'s parts and not with {@code after}'s. */
    public static Map<String, Integer> newlyStopped(List<Gate> gates, Predicate<String> before, Predicate<String> after,
            Collection<String> kept) {
        Set<String> lostBefore = PartGraph.lost(before, kept);
        Set<String> lostAfter = PartGraph.lost(after, kept);
        Map<String, Integer> stopped = new TreeMap<>();
        for (Gate gate : gates) {
            if (holds(gate, before, lostBefore) && !holds(gate, after, lostAfter)) {
                stopped.merge(gate.mod(), 1, Integer::sum);
            }
        }
        return stopped;
    }

    /** By mod, how many of the installed mods' recipes the parts as they are now stop. */
    public static Map<String, Integer> stoppedNow() {
        Map<String, Integer> stopped = new TreeMap<>();
        for (Gate gate : gates()) {
            if (!holds(gate, Features::switchedOn, Features.lost())) {
                stopped.merge(gate.mod(), 1, Integer::sum);
            }
        }
        return stopped;
    }

    /** A mod's name as it gives it. */
    public static String name(String mod) {
        return ModList.get() == null ? mod : ModList.get().getModContainerById(mod)
                .map(container -> container.getModInfo().getDisplayName()).orElse(mod);
    }

    @SubscribeEvent
    static void onStarted(ServerStartedEvent event) {
        stoppedNow().forEach((mod, count) -> Nookworks.LOGGER.warn(
                "{} recipes of {} cannot be used: Nookworks parts they need are switched off.", count, name(mod)));
    }

    @SubscribeEvent
    static void onLogIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                        || player.level().getServer().isSingleplayerOwner(player.nameAndId()))) {
            return;
        }
        stoppedNow().forEach((mod, count) -> player.sendSystemMessage(
                Component.translatable("message.nookworks.addon_stopped", name(mod), count).withStyle(ChatFormatting.YELLOW)));
    }
}
