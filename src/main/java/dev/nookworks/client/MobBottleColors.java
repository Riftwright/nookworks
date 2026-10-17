package dev.nookworks.client;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.mobcapture.MobBottleItem;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.ARGB;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * The colours a filled Mob Bottle's contents take, so a shelf of them can be told apart: the two colours of the mob's
 * spawn egg, its shell and its spots ({@code assets/nookworks/mob_bottle_colors.json}, worked out from the eggs by
 * tools/textures/gen_mob_bottle_colors.py; a resource pack may give its own), or for a mob not in it - another
 * mod's - colours for its kind: a monster's red, a creature's green, the sea's blue. The eyes looking out are dark
 * in a pale mob and light in a dark one.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class MobBottleColors {
    private static final Identifier FILE = Nookworks.id("mob_bottle_colors.json");
    private static final int LIGHT_EYES = 0xFAF8D6;
    private static final int DARK_EYES = 0x28241F;
    private static Map<Identifier, int[]> table = Map.of();

    /** The parts of a filled bottle that take a colour of its mob's. */
    public enum Part implements StringRepresentable {
        SHELL("shell"),
        SPOTS("spots"),
        EYES("eyes");

        public static final Codec<Part> CODEC = StringRepresentable.fromEnum(Part::values);
        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    /** The shell's colour and the spots' for {@code type}, as 0xRRGGBB. */
    public static int[] of(EntityType<?> type) {
        int[] known = table.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        return known != null ? known : byKind(type.getCategory());
    }

    /** The colour of {@code part} in a bottle holding {@code type}, as 0xRRGGBB. */
    public static int colour(EntityType<?> type, Part part) {
        int[] egg = of(type);
        return switch (part) {
            case SHELL -> egg[0];
            case SPOTS -> egg[1];
            case EYES -> 0.2126 * ARGB.red(egg[0]) + 0.7152 * ARGB.green(egg[0]) + 0.0722 * ARGB.blue(egg[0]) > 150
                    ? DARK_EYES : LIGHT_EYES;
        };
    }

    static int[] byKind(MobCategory kind) {
        return switch (kind) {
            case MONSTER -> new int[] {0x8C2A30, 0x3A1418};
            case CREATURE -> new int[] {0x6CAA48, 0x2E6428};
            case AMBIENT -> new int[] {0x6E6054, 0x3A322C};
            case WATER_CREATURE, WATER_AMBIENT, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> new int[] {0x3C78C8, 0x1E4488};
            default -> new int[] {0xBEA064, 0x786040};
        };
    }

    /**
     * The tint of one part of a filled bottle in its item model ({@code "which": "shell"}, {@code "spots"} or
     * {@code "eyes"}). White on an empty bottle, which has nothing in it to colour.
     */
    public record Tint(Part which) implements ItemTintSource {
        public static final MapCodec<Tint> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Part.CODEC.fieldOf("which").forGetter(Tint::which)).apply(i, Tint::new));

        @Override
        public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
            EntityType<?> type = MobBottleItem.captured(stack);
            return type == null ? -1 : ARGB.opaque(colour(type, this.which));
        }

        @Override
        public MapCodec<Tint> type() {
            return CODEC;
        }
    }

    @SubscribeEvent
    static void registerTint(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(Nookworks.id("captured_mob"), Tint.CODEC);
    }

    @SubscribeEvent
    static void registerReload(AddClientReloadListenersEvent event) {
        event.addListener(Nookworks.id("mob_bottle_colors"), (ResourceManagerReloadListener) MobBottleColors::load);
    }

    private static void load(ResourceManager resources) {
        Map<Identifier, int[]> read = new HashMap<>();
        resources.getResource(FILE).ifPresent(resource -> {
            try (var reader = resource.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                for (var entry : json.entrySet()) {
                    var pair = entry.getValue().getAsJsonArray();
                    read.put(Identifier.parse(entry.getKey()), new int[] {colour(pair.get(0).getAsString()), colour(pair.get(1).getAsString())});
                }
            } catch (IOException | RuntimeException e) {
                Nookworks.LOGGER.warn("Could not read {}: {}", FILE, e.toString());
            }
        });
        table = Map.copyOf(read);
    }

    private static int colour(String hex) {
        return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16);
    }

    private MobBottleColors() {}
}
