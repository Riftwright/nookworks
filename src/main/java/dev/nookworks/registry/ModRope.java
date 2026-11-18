package dev.nookworks.registry;

import java.util.List;
import java.util.function.Supplier;

import dev.nookworks.Nookworks;
import dev.nookworks.carry.CarriedLook;
import dev.nookworks.rope.FilamentBulbBlock;
import dev.nookworks.rope.HookBlock;
import dev.nookworks.rope.HungBannerEntity;
import dev.nookworks.rope.HungLightBlock;
import dev.nookworks.rope.HungItemEntity;
import dev.nookworks.rope.RopeKnotEntity;
import dev.nookworks.rope.ThinChainItem;
import dev.nookworks.rope.TiedLoadEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import dev.nookworks.port.core.Props;

/**
 * What Thin Rope does beyond hanging a planter: ropes strung from post to post - fence posts, or Hooks on a wall or
 * ceiling - things hung on them or from a rope's end - bottles, fish and kelp to dry, banners, a Filament Bulb - and
 * the Dried Fish that comes of it; and Thin Chain, strung as the rope is.
 */
public final class ModRope {
    public static final DeferredRegister<EntityDataSerializer<?>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, Nookworks.MODID);
    /** A knot's spans, sent to clients to draw. */
    public static final EntityDataSerializer<List<BlockPos>> POSITIONS =
            EntityDataSerializer.forValueType(BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()));

    static {
        SERIALIZERS.register("positions", () -> POSITIONS);
        // a carried block's look, for any mod's entity to sync (dev.nookworks.carry; a Tied Load's)
        SERIALIZERS.register("carried_look", () -> CarriedLook.ENTITY_SERIALIZER);
    }

    public static final Supplier<EntityType<RopeKnotEntity>> ROPE_KNOT = ModEntities.ENTITY_TYPES.register("rope_knot",
            () -> EntityType.Builder.<RopeKnotEntity>of(RopeKnotEntity::new, MobCategory.MISC)
                    .sized(0.375F, 0.375F).clientTrackingRange(10).updateInterval(Integer.MAX_VALUE)
                    .build(Nookworks.id("rope_knot").toString()));
    public static final Supplier<EntityType<HungItemEntity>> HUNG_ITEM = ModEntities.ENTITY_TYPES.register("hung_item",
            () -> EntityType.Builder.<HungItemEntity>of(HungItemEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.4F).clientTrackingRange(10).updateInterval(Integer.MAX_VALUE)
                    .build(Nookworks.id("hung_item").toString()));
    /** A banner hung on a rope, its bar under it: big enough for a box of its own (turned and leaning as it hangs). */
    public static final Supplier<EntityType<HungBannerEntity>> HUNG_BANNER = ModEntities.ENTITY_TYPES.register("hung_banner",
            () -> EntityType.Builder.<HungBannerEntity>of(HungBannerEntity::new, MobCategory.MISC)
                    .sized((float) HungBannerEntity.WIDTH, (float) HungBannerEntity.HEIGHT).clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .build(Nookworks.id("hung_banner").toString()));

    /**
     * A block tied up with rope, standing where it was (TiedLoadEntity): a block's size, tracked as far as a block is
     * seen, sent each tick it moves (hoisted or falling), as nothing else moves it.
     */
    public static final Supplier<EntityType<TiedLoadEntity>> TIED_LOAD = ModEntities.ENTITY_TYPES.register("tied_load",
            () -> EntityType.Builder.<TiedLoadEntity>of(TiedLoadEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).fireImmune().clientTrackingRange(10).updateInterval(1)
                    .build(Nookworks.id("tied_load").toString()));

    public static final DeferredBlock<FilamentBulbBlock> FILAMENT_BULB = ModBlocks.BLOCKS.registerBlock("filament_bulb", FilamentBulbBlock::new,
            Props.block(p -> p.mapColor(MapColor.NONE).strength(0.3F).sound(SoundType.GLASS).noOcclusion().pushReaction(PushReaction.DESTROY)
                    .lightLevel(FilamentBulbBlock::light)));
    public static final DeferredItem<BlockItem> FILAMENT_BULB_ITEM = ModItems.ITEMS.registerSimpleBlockItem(FILAMENT_BULB);

    /** The unseen light a bulb or glow berries give where they hang on a rope ({@link HungLightBlock}). No item. */
    public static final DeferredBlock<HungLightBlock> HUNG_LIGHT = ModBlocks.BLOCKS.registerBlock("hung_light", HungLightBlock::new,
            Props.block(p -> p.replaceable().noCollission().noLootTable().noOcclusion().strength(-1.0F, 3600000.8F)
                    .pushReaction(PushReaction.DESTROY).lightLevel(state -> state.getValue(HungLightBlock.LEVEL))));

    /**
     * A small iron hook for a wall or a ceiling: Thin Rope ties to it, berries hang on it - glow berries a lamp. Nothing
     * to walk into.
     */
    public static final DeferredBlock<HookBlock> HOOK = ModBlocks.BLOCKS.registerBlock("hook", HookBlock::new,
            Props.block(p -> p.mapColor(MapColor.NONE).strength(0.5F).sound(SoundType.CHAIN).noCollission().noOcclusion()
                    .lightLevel(HookBlock::light).pushReaction(PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> HOOK_ITEM = ModItems.ITEMS.registerSimpleBlockItem(HOOK);

    /**
     * Thin Chain: strung from post to post as Thin Rope is, the same sag and the same things hung on it, in iron links -
     * and it rattles and sways a little when something goes through it ({@link dev.nookworks.rope.ChainSway}).
     */
    public static final DeferredItem<ThinChainItem> THIN_CHAIN = ModItems.ITEMS.registerItem("thin_chain", ThinChainItem::new);
    /**
     * A garden wind chime that chimes in the wind (HungItemEntity#windChime): a block hung under a block as a lantern is,
     * and hung on a Thin Rope as a lantern is too, drawn as its block (it was an item, its picture on a string: the user,
     * 2026-11-12, 「ちゃんとブロックとして作ろう」). The id is the item's as it was, so chimes kept and hung carry over.
     */
    public static final DeferredBlock<dev.nookworks.block.WindChimeBlock> WIND_CHIME_BLOCK = ModBlocks.BLOCKS.registerBlock("wind_chime",
            dev.nookworks.block.WindChimeBlock::new, Props.block(p -> p.mapColor(MapColor.WOOD).strength(0.3F).sound(SoundType.BAMBOO_WOOD)
                    .noCollission().noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> WIND_CHIME = ModItems.ITEMS.registerSimpleBlockItem(WIND_CHIME_BLOCK);

    /**
     * Fish opened out and dried in the sun (干物): a day of sun makes it keep, and a little better eating than a fish
     * cooked in a furnace - and no parasite lives through it.
     */
    public static final DeferredItem<Item> DRIED_FISH = ModItems.ITEMS.registerSimpleItem("dried_fish",
            Props.item(p -> p.food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.7F).build())));

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }

    /** Loads this class, so its blocks, items and entities are registered with the rest. */
    public static void init() {
        Nookworks.LOGGER.debug("rope: {}", FILAMENT_BULB.getId());
    }

    private ModRope() {}
}
