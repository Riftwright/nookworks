package dev.nookworks.registry;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksStartupConfig;
import dev.nookworks.feature.Features;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Conditions for our data files. {@code {"type": "nookworks:setting", "setting": "flour_bakery", "is": true}} holds
 * while that startup setting is as given ({@link NookworksStartupConfig#byName}): the bakery's recipes load with it on,
 * vanilla's own bread, cookie, cake and pie with it off.
 */
public final class ModConditions {
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, Nookworks.MODID);

    public record Setting(String setting, boolean is) implements ICondition {
        public static final MapCodec<Setting> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("setting").forGetter(Setting::setting),
                Codec.BOOL.optionalFieldOf("is", true).forGetter(Setting::is)).apply(i, Setting::new));

        @Override
        public boolean test(IContext context) {
            if (this.setting.equals("flour_bakery")) {
                return NookworksStartupConfig.flourBakery() == this.is; // the bakery goes with the kitchen
            }
            var value = NookworksStartupConfig.byName(this.setting);
            if (value == null) {
                Nookworks.LOGGER.warn("A nookworks:setting condition names no setting: {}", this.setting);
                return false;
            }
            if (this.setting.startsWith("feature.")) { // a part: as the world has it, under its start-up switch
                return Features.enabled(this.setting.substring("feature.".length())) == this.is;
            }
            return value.getAsBoolean() == this.is;
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return CODEC;
        }
    }

    /**
     * {@code {"type": "nookworks:feature", "feature": "kitchen"}} holds while that part of the mod is on
     * ({@link dev.nookworks.feature.Features}), for a data pack's own files (ours carry {@link Available} since 1.3, which
     * also knows what nothing reaches any more). In the world whose data packs are loading: its own choice
     * ({@link dev.nookworks.feature.WorldFeatures}) is in force by then.
     */
    public record Feature(String feature) implements ICondition {
        public static final MapCodec<Feature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("feature").forGetter(Feature::feature)).apply(i, Feature::new));

        @Override
        public boolean test(IContext context) {
            if (NookworksStartupConfig.feature(this.feature) == null) {
                Nookworks.LOGGER.warn("A nookworks:feature condition names no part of the mod: {}", this.feature);
                return true;
            }
            return Features.enabled(this.feature);
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return CODEC;
        }
    }

    /**
     * {@code {"type": "nookworks:available", "items": ["nookworks:soap", "nookworks:caustic_soda"]}} holds while every item
     * named can be had (with {@code "any": true}, one of them): of no part switched off, and still reached by something
     * ({@link Features#available}). tools/gen_feature_conditions.py puts one on each recipe of ours whose result can go
     * (the result, and the items of ours it names), and on its recipe-book unlock; tools/gen_advancements.py puts one on
     * each advancement (any of what it asks for, with its parents'). In the world whose data packs are loading.
     */
    public record Available(List<String> items, boolean any) implements ICondition {
        public static final MapCodec<Available> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.listOf().fieldOf("items").forGetter(Available::items),
                Codec.BOOL.optionalFieldOf("any", false).forGetter(Available::any)).apply(i, Available::new));

        @Override
        public boolean test(IContext context) {
            return this.any ? this.items.stream().anyMatch(Features::available) : this.items.stream().allMatch(Features::available);
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return CODEC;
        }
    }

    static {
        CONDITIONS.register("setting", () -> Setting.CODEC);
        CONDITIONS.register("feature", () -> Feature.CODEC);
        CONDITIONS.register("available", () -> Available.CODEC);
    }

    private ModConditions() {}
}
