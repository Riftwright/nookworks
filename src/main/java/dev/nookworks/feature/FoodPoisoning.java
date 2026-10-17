package dev.nookworks.feature;

import java.util.Optional;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * Food poisoning from raw meat and fish, the way it really goes: nothing when you eat it, then a minute or two later
 * you are ill. How likely, and how badly, is the item's {@link #RISK} (a data map, {@code nookworks:food_poisoning},
 * so a pack can tune it or add another mod's raw food; by default any mod's raw meat by {@code c:foods/raw_meat} as
 * mild and raw fish by {@code c:foods/raw_fish}, then vanilla's meats each as below, and the more particular tags some
 * mods keep, {@code c:foods/raw_pork} and the like):
 * <ul>
 * <li>{@link Illness#SEVERE} - poultry always (Campylobacter, Salmonella), pork half the time: poison, nausea,
 * hunger;</li>
 * <li>{@link Illness#MILD} - beef, mutton and rabbit a quarter of the time: an upset stomach, hunger and a little
 * nausea;</li>
 * <li>{@link Illness#PARASITE} - raw fish one time in twenty (Anisakis): cramps - nausea, weakness, hunger.</li>
 * </ul>
 * The wait is kept on the player (saved, so logging out does not cure it) and counted down each tick. {@link #WARNING}
 * ticks before it strikes the stomach turns (a moment's hunger, a line saying what it was), so the player knows what
 * is coming and why; a bucket of milk drunk before then settles it. Only one thing brews at a time: eating something
 * worse makes it the worse one, the clock running on. Creative players are not troubled. Off with
 * {@code rawFoodSickness}; while it is on, raw food says what it risks in its tooltip.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class FoodPoisoning {
    /** Ticks from the meal to the first cramps: one to two minutes. */
    public static final int MIN_DELAY = 1_200;
    public static final int MAX_DELAY = 2_400;
    /** How long before the illness the first sign comes: twenty seconds. */
    public static final int WARNING = 400;

    /** What a raw food can do to you, mildest first. */
    public enum Illness implements StringRepresentable {
        MILD("mild"),
        PARASITE("parasite"),
        SEVERE("severe");

        public static final Codec<Illness> CODEC = StringRepresentable.fromEnum(Illness::values);
        private final String name;

        Illness(String name) {
            this.name = name;
        }

        /** What it does when it strikes. */
        void strike(Player player) {
            switch (this) {
                case SEVERE -> {
                    player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 12, 0));
                    player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 30, 0));
                    player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 30, 0));
                }
                case MILD -> {
                    player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 20, 0));
                    player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 8, 0));
                }
                case PARASITE -> {
                    player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 20, 0));
                    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 45, 0));
                    player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 20, 0));
                }
            }
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    /** How likely eating the item raw makes you ill ({@code chance}, 0 to 1), and with what. */
    public record Risk(float chance, Illness illness) {
        public static final Codec<Risk> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(Risk::chance),
                Illness.CODEC.fieldOf("illness").forGetter(Risk::illness)).apply(i, Risk::new));
    }

    public static final DataMapType<Item, Risk> RISK = DataMapType.builder(Nookworks.id("food_poisoning"), Registries.ITEM, Risk.CODEC)
            .synced(Risk.CODEC, false).build();

    /** Something brewing: the ticks until it strikes, what it will be, and what it was eaten as (for the warning). */
    public record Incubation(int ticks, Illness illness, Optional<Holder<Item>> cause) {
        public static final Incubation NONE = new Incubation(0, Illness.SEVERE, Optional.empty());
        // A save from before there was anything but chicken holds only the ticks: it was raw chicken, and severe.
        public static final MapCodec<Incubation> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("ticks").forGetter(Incubation::ticks),
                Illness.CODEC.optionalFieldOf("illness", Illness.SEVERE).forGetter(Incubation::illness),
                BuiltInRegistries.ITEM.holderByNameCodec().optionalFieldOf("cause").forGetter(Incubation::cause))
                .apply(i, Incubation::new));
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Nookworks.MODID);

    public static final Supplier<AttachmentType<Incubation>> INCUBATING = ATTACHMENT_TYPES.register("food_poisoning",
            () -> AttachmentType.builder(() -> Incubation.NONE).serialize(Incubation.CODEC, brewing -> brewing.ticks() > 0).build());

    /** Ticks until it strikes; 0 when nothing is brewing. */
    public static int incubating(Player player) {
        return player.getData(INCUBATING).ticks();
    }

    /** What is brewing, or null for nothing. */
    public static @Nullable Illness brewing(Player player) {
        Incubation now = player.getData(INCUBATING);
        return now.ticks() > 0 ? now.illness() : null;
    }

    /** What eating {@code food} raw risks, or null for nothing (cooked food, anything with no entry, or a chance of 0). */
    public static @Nullable Risk risk(ItemStack food) {
        Risk risk = food.isEmpty() ? null : food.typeHolder().getData(RISK);
        return risk == null || risk.chance() <= 0.0F ? null : risk;
    }

    /** What eating {@code food} does, by the eater's luck. */
    public static void ate(LivingEntity eater, ItemStack food) {
        ate(eater, food, eater.getRandom().nextFloat());
    }

    /**
     * What eating {@code food} does when the dice come up {@code roll} (0 to 1): below the food's chance, it starts the
     * clock, unless something as bad is already brewing (or the eater is in creative, where food does nothing to
     * anyone); something worse than what is brewing takes its place. Milk stops it.
     */
    public static void ate(LivingEntity eater, ItemStack food, float roll) {
        if (!(eater instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (food.is(Items.MILK_BUCKET) || food.is(Tags.Items.DRINKS_MILK)) {
            player.setData(INCUBATING, Incubation.NONE); // what milk does for the effects, it does for what has not struck yet
            return;
        }
        Risk risk = risk(food);
        if (!NookworksConfig.RAW_FOOD_SICKNESS.get() || risk == null || roll >= risk.chance() || player.isCreative() || player.isSpectator()) {
            return;
        }
        Incubation now = player.getData(INCUBATING);
        if (now.ticks() <= 0) {
            int delay = MIN_DELAY + player.getRandom().nextInt(MAX_DELAY - MIN_DELAY + 1);
            player.setData(INCUBATING, new Incubation(delay, risk.illness(), Optional.of(food.typeHolder())));
        } else if (risk.illness().compareTo(now.illness()) > 0) {
            player.setData(INCUBATING, new Incubation(now.ticks(), risk.illness(), Optional.of(food.typeHolder())));
        }
    }

    /**
     * One tick of the wait; at the end of it, the illness - unless the setting has been turned off since, or the player
     * has gone into creative or spectator, where food troubles no one: then it never comes.
     */
    public static void tick(Player player) {
        Incubation now = player.getData(INCUBATING);
        int left = now.ticks() - 1;
        if (now.ticks() <= 0) {
            return;
        }
        if (!NookworksConfig.RAW_FOOD_SICKNESS.get() || player.isCreative() || player.isSpectator()) {
            player.setData(INCUBATING, Incubation.NONE);
            return;
        }
        if (left > 0) {
            player.setData(INCUBATING, new Incubation(left, now.illness(), now.cause()));
            if (left == WARNING) {
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 4, 0));
                Component food = now.cause().map(item -> new ItemStack(item).getHoverName())
                        .orElse(Component.translatable("item.minecraft.chicken"));
                player.sendOverlayMessage(Component.translatable("message.nookworks.food_poisoning.warning." + now.illness().getSerializedName(), food)
                        .withStyle(ChatFormatting.GOLD));
            }
            return;
        }
        player.setData(INCUBATING, Incubation.NONE);
        now.illness().strike(player);
    }

    @SubscribeEvent
    static void onEat(LivingEntityUseItemEvent.Finish event) {
        ate(event.getEntity(), event.getItem());
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide()) {
            tick(event.getEntity());
        }
    }

    @EventBusSubscriber(modid = Nookworks.MODID)
    static final class Registration {
        @SubscribeEvent
        static void register(RegisterDataMapTypesEvent event) {
            event.register(RISK);
        }

        private Registration() {}
    }

    private FoodPoisoning() {}
}
