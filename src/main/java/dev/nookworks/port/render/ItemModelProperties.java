package dev.nookworks.port.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;

import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * What 26.3 says in its item definitions ({@code assets/<ns>/items/*.json}) that 1.21.1 item models cannot: the
 * conditions models switch on, and the tints. In 1.21.1 a model switches on an item property (its {@code overrides}, a
 * {@code predicate} on a number) and an item is tinted by an {@code ItemColor} answering each tint index; this is the
 * table of both, so the converted {@code models/item} files can name them:
 * <ul>
 * <li>{@code nookworks:has_contents} - 1 for a Plate or a Wooden Tray carrying {@code minecraft:container} (26.3:
 * {@code has_component container});</li>
 * <li>{@code nookworks:filled} - 1 for a Mob Bottle carrying {@code nookworks:captured_entity};</li>
 * <li>{@code nookworks:recover} - 1 for a Wrench in its Recover mode (26.3: {@code select} on
 * {@code nookworks:wrench_mode});</li>
 * <li>each {@link ConditionalItemModelProperty} the mod registers, as 1 or 0, on the items {@link #CONDITIONS} lists
 * ({@code nookworks:still_hot}, {@code nookworks:campfire_unlit}, {@code nookworks:photo});</li>
 * <li>{@code minecraft:angle} on the Nook Dowsing Rod: a compass needle, as vanilla's compass reads it, pointing at the
 * rod's {@code minecraft:lodestone_tracker} target where it has one (what the rod found in the Nether or the End), else
 * at the world spawn in the Overworld; with neither it spins, as a compass does where it has nothing to point at.</li>
 * </ul>
 * Another mod's items join both tables through {@link #addCondition} and {@link #addTints} (an add-on built on this
 * port, whose 26.3 item definitions name conditions and tints as ours do).
 */
public final class ItemModelProperties {
    private ItemModelProperties() {}

    /** Each condition the mod registers, and the items whose models switch on it. */
    static final Map<ResourceLocation, List<ResourceLocation>> CONDITIONS = Map.of(
            PortIds.id("still_hot"), List.of(PortIds.id("red_hot_velarium_ingot")),
            PortIds.id("campfire_unlit"), List.of(ResourceLocation.withDefaultNamespace("campfire"),
                    ResourceLocation.withDefaultNamespace("soul_campfire")),
            PortIds.id("photo"), List.of(PortIds.id("poster")));

    /** The items other mods have added to conditions ({@link #addCondition}), by condition. */
    private static final Map<ResourceLocation, List<ResourceLocation>> ADDED_CONDITIONS = new ConcurrentHashMap<>();

    /**
     * One tint index's colour: a constant (ARGB; an RGB constant is made opaque, as 26.3's is), or a kind registered
     * with {@code RegisterColorHandlersEvent.ItemTintSources}, read from {@code arguments} as an item definition's
     * tint object is (its fields but {@code type}).
     */
    public record Tint(int constant, @Nullable ResourceLocation type, @Nullable JsonObject arguments) {
        /** 26.3's {@code minecraft:constant} tint. */
        public static Tint constant(int rgb) {
            return new Tint(rgb == -1 ? -1 : 0xFF000000 | rgb, null, null);
        }

        /** A registered kind with the given arguments. */
        public static Tint of(ResourceLocation type, JsonObject arguments) {
            return new Tint(0, type, arguments.deepCopy());
        }

        /** A registered kind with one argument. */
        public static Tint of(ResourceLocation type, String key, String value) {
            JsonObject arguments = new JsonObject();
            arguments.addProperty(key, value);
            return new Tint(0, type, arguments);
        }
    }

    /** The tints other mods have added ({@link #addTints}), by item. */
    private static final Map<ResourceLocation, List<Tint>> ADDED_TINTS = new ConcurrentHashMap<>();

    /**
     * Port only (26.3 reads this from the items' definitions): the items whose models switch on {@code condition}, a
     * {@link ConditionalItemModelProperty} registered under that id with {@code RegisterConditionalItemModelPropertyEvent}
     * by any mod. Each item gets the item property {@code condition}, 1 or 0, for its model's {@code overrides}. To be
     * called before client setup's queued work: from the mod's constructor, or its listener of that event.
     */
    public static void addCondition(ResourceLocation condition, ResourceLocation... items) {
        ADDED_CONDITIONS.merge(condition, List.of(items), (had, more) -> {
            List<ResourceLocation> all = new ArrayList<>(had);
            all.addAll(more);
            return List.copyOf(all);
        });
    }

    /**
     * Port only: {@code item}'s tints, one for each tint index from 0, as its 26.3 item definition lists them (an index
     * past the list is untinted). Over any the table already gives that item. To be called before the item colours are
     * registered: from the mod's constructor, or its listener of {@code RegisterColorHandlersEvent.ItemTintSources}.
     */
    public static void addTints(ResourceLocation item, List<Tint> tints) {
        ADDED_TINTS.put(item, List.copyOf(tints));
    }

    /** The items {@code condition} applies to: the table's and those added. */
    private static List<ResourceLocation> conditionItems(ResourceLocation condition) {
        List<ResourceLocation> added = ADDED_CONDITIONS.get(condition);
        List<ResourceLocation> own = CONDITIONS.getOrDefault(condition, List.of());
        if (added == null) {
            return own;
        }
        List<ResourceLocation> all = new ArrayList<>(own);
        all.addAll(added);
        return all;
    }

    /** Each tinted item, a tint for each index from 0, as its 26.3 definition lists them. */
    static final Map<ResourceLocation, List<Tint>> TINTS = Map.of(
            PortIds.id("mob_bottle"), List.of(Tint.of(PortIds.id("captured_mob"), "which", "shell"),
                    Tint.of(PortIds.id("captured_mob"), "which", "spots"), Tint.of(PortIds.id("captured_mob"), "which", "eyes"),
                    Tint.constant(-1)),
            PortIds.id("dawnwood_boat"), List.of(Tint.constant(12701130)),
            PortIds.id("dawnwood_chest_boat"), List.of(Tint.constant(12701130)));

    /** The item properties, registered once the items are (at client setup, on the main thread). */
    static void registerProperties() {
        has(PortIds.id("plate"), "has_contents", stack -> stack.has(DataComponents.CONTAINER));
        has(PortIds.id("wooden_tray"), "has_contents", stack -> stack.has(DataComponents.CONTAINER));
        has(PortIds.id("mob_bottle"), "filled", stack -> stack.has(PortIds.component("captured_entity")));
        has(PortIds.id("wrench"), "recover",
                stack -> "recover".equals(serialized(stack.get(PortIds.component("wrench_mode")))));
        RegisterConditionalItemModelPropertyEvent.TYPES.forEach((id, codec) -> {
            ConditionalItemModelProperty property = decode(codec, new JsonObject());
            if (property == null) {
                return;
            }
            for (ResourceLocation itemId : conditionItems(id)) {
                Item item = item(itemId);
                if (item != null) {
                    ItemProperties.register(item, id, (ClampedItemPropertyFunction) (stack, level, owner, seed) ->
                            property.get(stack, level, owner, seed, ItemDisplayContext.NONE) ? 1.0F : 0.0F);
                }
            }
        });
        Item rod = item(PortIds.id("nook_dowsing_rod"));
        if (rod != null) {
            ItemProperties.register(rod, ResourceLocation.withDefaultNamespace("angle"), new CompassItemPropertyFunction(ItemModelProperties::rodTarget));
        }
    }

    /** What the rod's needle points at: its found target, else the Overworld's spawn; null (spinning) for neither. */
    private static @Nullable GlobalPos rodTarget(ClientLevel level, ItemStack stack, net.minecraft.world.entity.Entity holder) {
        LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
        if (tracker != null) {
            return tracker.target().orElse(null);
        }
        return level.dimension() == Level.OVERWORLD ? GlobalPos.of(Level.OVERWORLD, level.getSharedSpawnPos()) : null;
    }

    /** A component's value as its codec names it (an enum's serialized name), as 26.3's {@code select} compares it. */
    private static @Nullable String serialized(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof net.minecraft.util.StringRepresentable named ? named.getSerializedName()
                : value.toString().toLowerCase(java.util.Locale.ROOT);
    }

    private static void has(ResourceLocation itemId, String property, Predicate<ItemStack> test) {
        Item item = item(itemId);
        if (item != null) {
            ItemProperties.register(item, PortIds.id(property), (ClampedItemPropertyFunction) (stack, level, owner, seed) ->
                    test.test(stack) ? 1.0F : 0.0F);
        }
    }

    /** The item colours, from {@link #TINTS} and the kinds registered with {@link RegisterColorHandlersEvent}. */
    static void registerTints(RegisterColorHandlersEvent.Item event) {
        Map<ResourceLocation, List<Tint>> all = new LinkedHashMap<>(TINTS);
        all.putAll(ADDED_TINTS);
        all.forEach((itemId, tints) -> {
            Item item = item(itemId);
            if (item == null) {
                return;
            }
            ItemTintSource[] sources = new ItemTintSource[tints.size()];
            int[] constants = new int[tints.size()];
            for (int i = 0; i < tints.size(); i++) {
                Tint tint = tints.get(i);
                constants[i] = tint.constant();
                if (tint.type() != null) {
                    MapCodec<? extends ItemTintSource> codec = dev.nookworks.port.render.RegisterColorHandlersEvent.ItemTintSources.TYPES.get(tint.type());
                    sources[i] = codec == null ? null : decode(codec, tint.arguments());
                }
            }
            ItemColor colour = (stack, index) -> {
                if (index < 0 || index >= sources.length) {
                    return -1;
                }
                return sources[index] != null ? sources[index].calculate(stack, net.minecraft.client.Minecraft.getInstance().level, null)
                        : constants[index];
            };
            event.register(colour, item);
        });
    }

    static <T> @Nullable T decode(MapCodec<? extends T> codec, @Nullable JsonObject arguments) {
        return codec.codec().parse(JsonOps.INSTANCE, arguments == null ? new JsonObject() : arguments)
                .resultOrPartial(error -> PortIds.LOGGER.warn("Could not read {}: {}", arguments, error))
                .map(found -> (T) found)
                .orElse(null);
    }

    static @Nullable Item item(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? null : item;
    }
}
