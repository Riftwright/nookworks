package dev.nookworks.feature;

import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import dev.nookworks.port.storage.Nbt;

/**
 * A village map from the Wandering Trader, for a player who has never been to a village.
 * <p>
 * In vanilla a village map comes from a cartographer - who lives in a village - so the one player who needs it cannot
 * get it. The Wandering Trader comes to the player instead. When a player first opens a trader's stall and has not yet
 * set foot in a village (the hidden advancement {@link #VISITED_VILLAGE}, a location trigger on {@code #minecraft:village}),
 * one trader in two ({@link NookworksConfig#TRADER_VILLAGE_MAP_CHANCE}) adds the trade {@link #TRADE}: a map to the
 * nearest village, for a few emeralds and a compass. Each trader decides once, on the first click from a player who
 * has not been to a village: players who have been leave him undecided for one who has not.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class VillageMap {
    /**
     * The trade the stall gets: in 26.3 its price and uses are data, {@code data/nookworks/villager_trade/wandering_trader/village_map.json}.
     * PORT: 1.21.1 has no data-driven trades (no villager trade registry): the same trade is made in {@link #offer}, and
     * this is only its 26.3 id.
     */
    public static final ResourceLocation TRADE = Nookworks.id("wandering_trader/village_map");
    /** How far the map looks for a village, in chunks (the trade's {@code search_radius}). */
    private static final int TRADE_SEARCH_RADIUS = 50;
    /** Done once the player has stood in a village; it has no display, so it never shows. */
    public static final ResourceLocation VISITED_VILLAGE = Nookworks.id("hidden/visited_village");
    /** Set on a trader once he has decided, map or no map, so it is decided only once. */
    private static final String DECIDED = "nookworks:village_map_decided";
    /** Where the map goes among the offers: after his two buying trades and his two uncommon ones. */
    private static final int PLACE = 4;

    public static final DeferredRegister<LootItemFunctionType<?>> FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, Nookworks.MODID);

    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<Function>> FUNCTION_TYPE =
            FUNCTIONS.register("village_map", () -> new LootItemFunctionType<>(Function.MAP_CODEC));

    private VillageMap() {}

    @SubscribeEvent
    static void interact(PlayerInteractEvent.EntityInteract event) {
        // Before the trader's own click: his stall is made (if it is not yet) and the map put in it, then he opens it.
        if (event.getTarget() instanceof WanderingTrader trader && event.getEntity() instanceof ServerPlayer player
                && trader.isAlive() && !trader.isTrading() && !trader.isBaby()) {
            offerTo(trader, player, trader.getRandom().nextDouble());
        }
    }

    /**
     * Decides, the first time a trader is opened, whether he has a village map for {@code player}, and if so puts it
     * in his stall; {@code roll} is the trader's dice, 0 to 1. Returns the offer added, or null. Public for tests.
     */
    public static @Nullable MerchantOffer offerTo(WanderingTrader trader, ServerPlayer player, double roll) {
        if (Nbt.getBooleanOr(trader.getPersistentData(), DECIDED, false) || hasBeenInAVillage(player)) {
            return null;
        }
        trader.getPersistentData().putBoolean(DECIDED, true);
        if (roll >= NookworksConfig.TRADER_VILLAGE_MAP_CHANCE.get()) {
            return null;
        }
        MerchantOffer offer = offer(trader);
        if (offer != null) {
            MerchantOffers offers = trader.getOffers();
            offers.add(Math.min(PLACE, offers.size()), offer);
        }
        return offer;
    }

    /** Whether the player has ever stood in a village (since the mod has been there to see it). */
    public static boolean hasBeenInAVillage(ServerPlayer player) {
        AdvancementHolder visited = player.level().getServer().getAdvancements().get(VISITED_VILLAGE);
        return visited != null && player.getAdvancements().getOrStartProgress(visited).isDone();
    }

    /** The trade as the trader would make it where he stands: null if there is no village within reach of a map. */
    public static @Nullable MerchantOffer offer(WanderingTrader trader) {
        ServerLevel level = (ServerLevel) trader.level();
        // PORT: 26.3's trade from data (3 emeralds and a compass for an empty map turned by this class's function, kept
        // only if it became a filled map; one use, 1 xp); the same, written out
        ItemStack map = Function.villageMap(new ItemStack(Items.MAP), level, trader.position(), TRADE_SEARCH_RADIUS, (byte) 2);
        if (!map.is(Items.FILLED_MAP)) {
            return null; // no village within reach of a map
        }
        return new MerchantOffer(new ItemCost(Items.EMERALD, 3), Optional.of(new ItemCost(Items.COMPASS)), map, 1, 1, 0.0F);
    }

    /** The map's mark and name for the village it found: each vanilla kind its own house, any other the plains one. */
    public static Holder<MapDecorationType> decoration(Holder<Structure> village) {
        return village.unwrapKey().map(key -> switch (key.location().toString()) {
            case "minecraft:village_desert" -> MapDecorationTypes.DESERT_VILLAGE;
            case "minecraft:village_savanna" -> MapDecorationTypes.SAVANNA_VILLAGE;
            case "minecraft:village_snowy" -> MapDecorationTypes.SNOWY_VILLAGE;
            case "minecraft:village_taiga" -> MapDecorationTypes.TAIGA_VILLAGE;
            default -> MapDecorationTypes.PLAINS_VILLAGE;
        }).orElse(MapDecorationTypes.PLAINS_VILLAGE);
    }

    public static Component name(Holder<Structure> village) {
        return village.unwrapKey().map(key -> switch (key.location().toString()) {
            case "minecraft:village_plains", "minecraft:village_desert", "minecraft:village_savanna", "minecraft:village_snowy",
                    "minecraft:village_taiga" -> Component.translatable("filled_map." + key.location().getPath());
            default -> Component.translatable("filled_map.nookworks.village");
        }).orElse(Component.translatable("filled_map.nookworks.village"));
    }

    /**
     * {@code nookworks:village_map}: turns an empty map into a map of the nearest village of any kind
     * ({@code #minecraft:village}), marked with that kind's house and named for it - vanilla's explorer maps take one
     * kind of place and one mark. Anything but an empty map, or no village in reach, is left as it was.
     */
    public static final class Function extends LootItemConditionalFunction {
        public static final MapCodec<Function> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i)
                .and(i.group(
                        Codec.intRange(1, 200).optionalFieldOf("search_radius", 100).forGetter(f -> f.searchRadius),
                        Codec.BYTE.optionalFieldOf("zoom", (byte) 2).forGetter(f -> f.zoom)))
                .apply(i, Function::new));

        private final int searchRadius;
        private final byte zoom;

        private Function(java.util.List<LootItemCondition> condition, int searchRadius, byte zoom) {
            super(condition);
            this.searchRadius = searchRadius;
            this.zoom = zoom;
        }

        @Override
        public LootItemFunctionType<Function> getType() {
            return FUNCTION_TYPE.get();
        }

        @Override
        public Set<LootContextParam<?>> getReferencedContextParams() {
            return Set.of(LootContextParams.ORIGIN);
        }

        @Override
        protected ItemStack run(ItemStack stack, LootContext context) {
            return villageMap(stack, context.getLevel(), context.getParamOrNull(LootContextParams.ORIGIN), this.searchRadius, this.zoom);
        }

        /** The function's work, shared with the trade 1.21.1 makes in code ({@link VillageMap#offer}). */
        static ItemStack villageMap(ItemStack stack, ServerLevel level, @Nullable Vec3 origin, int searchRadius, byte zoom) {
            if (!stack.is(Items.MAP) || origin == null) {
                return stack;
            }
            Optional<HolderSet.Named<Structure>> villages = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(StructureTags.VILLAGE);
            if (villages.isEmpty()) {
                return stack;
            }
            Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                    .findNearestMapStructure(level, villages.get(), BlockPos.containing(origin), searchRadius, false);
            if (found == null) {
                return stack;
            }
            BlockPos at = found.getFirst();
            ItemStack map = MapItem.create(level, at.getX(), at.getZ(), zoom, true, true);
            MapItem.renderBiomePreviewMap(level, map);
            MapItemSavedData.addTargetDecoration(map, at, "+", decoration(found.getSecond()));
            map.set(DataComponents.ITEM_NAME, name(found.getSecond()));
            return map;
        }
    }
}
