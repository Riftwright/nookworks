package dev.nookworks.wood;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.mixin.FireBlockAccessor;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModWoods;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import dev.nookworks.port.core.Props;

/**
 * Turned planks (the user, 2026-11-10: 「木の板材をレンチで回転させることができたらいいな。…木目の横縞模様を縦縞模様に
 * 変える」, then 「レシピやJEIには出さず、壊した時にも普通の板材としてドロップして。つまり、ユーザーからは『ただ板材を回転させ
 * ているように見える』ようにして。階段はいらないけど、ハーフは同じ挙動で作って。」): for every wood's planks and wooden slab,
 * a block of the same with the boards turned a quarter - the planks' own texture, turned in the model by name
 * (tools/gen_wood_data.py), never copied. The Wrench turns a plank or slab into it and back, in place.
 * <p>
 * To a player they are the planks turned and nothing else: no item, so nothing in the creative tab, JEI or a recipe;
 * broken they drop the planks (or the slab, two from a double), picked they give them, and they are named as them. Made
 * from a full copy of the planks' properties - hardness, sound, map colour, burning in lava, the note block's bass -
 * and in the block tags that give planks and wooden slabs their behaviour (#minecraft:planks, #minecraft:wooden_slabs:
 * the axe, fire spread, suffocation and the rest follow from them), never in an item tag. Fire takes them as it takes
 * the planks (ModWoods' woods but the Nether's two, which do not burn).
 * <p>
 * The woods are {@link ModWoods#SUPPORT_WOODS}: every wood of 26.3 (bamboo's planks too, whose boards run up already
 * and turn to lie across; its mosaic is a weave, not boards, and is left as it is) and Dawnwood. Another mod's planks
 * are not covered.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class TurnedPlanks {
    public static final Map<String, DeferredBlock<TurnedPlanksBlock>> PLANKS;
    public static final Map<String, DeferredBlock<TurnedSlabBlock>> SLABS;

    static {
        Map<String, DeferredBlock<TurnedPlanksBlock>> planks = new LinkedHashMap<>();
        Map<String, DeferredBlock<TurnedSlabBlock>> slabs = new LinkedHashMap<>();
        for (String wood : ModWoods.SUPPORT_WOODS) {
            Supplier<Block> plainPlanks = plain(wood, "planks");
            Supplier<Block> plainSlab = plain(wood, "slab");
            // 1.21.1 builds a block's properties when it is listed, Dawnwood's planks not registered yet: the copy is
            // made when the block is (26.3's properties supplier ran then)
            planks.put(wood, ModBlocks.BLOCKS.register("turned_" + wood + "_planks",
                    () -> new TurnedPlanksBlock(plainPlanks, copyOf(plainPlanks.get()))));
            slabs.put(wood, ModBlocks.BLOCKS.register("turned_" + wood + "_slab",
                    () -> new TurnedSlabBlock(plainSlab, copyOf(plainSlab.get()))));
        }
        PLANKS = Collections.unmodifiableMap(planks);
        SLABS = Collections.unmodifiableMap(slabs);
    }

    private static @Nullable Map<Block, Block> pairs;

    /** {@code wood}'s planks or slab: vanilla's, or Dawnwood's. */
    private static Supplier<Block> plain(String wood, String kind) {
        if (wood.equals("dawnwood")) {
            return kind.equals("planks") ? ModWoods.DAWNWOOD_PLANKS::get : ModWoods.DAWNWOOD_SLAB::get;
        }
        ResourceLocation id = ResourceLocation.withDefaultNamespace(wood + "_" + kind);
        return () -> BuiltInRegistries.BLOCK.get(id);
    }

    /**
     * The plain block's properties, whole, but named as it is (its loot table stays our own, by our id). 1.21.1's
     * properties carry no name: the blocks take the plain one's in getDescriptionId.
     */
    private static BlockBehaviour.Properties copyOf(Block plain) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.ofFullCopy(plain);
        return p;
    }

    /** Every plain planks or slab and its turned one, both ways. */
    private static Map<Block, Block> pairs() {
        Map<Block, Block> map = pairs;
        if (map == null) {
            map = new HashMap<>();
            for (var block : PLANKS.values()) {
                map.put(block.get(), block.get().plain());
                map.put(block.get().plain(), block.get());
            }
            for (var block : SLABS.values()) {
                map.put(block.get(), block.get().plain());
                map.put(block.get().plain(), block.get());
            }
            pairs = map;
        }
        return map;
    }

    /**
     * What the Wrench turns {@code state} into: the turned planks or slab of plain ones, the plain of turned ones - a
     * slab's half and water kept - or null when it is neither.
     */
    public static @Nullable BlockState turn(BlockState state) {
        Block other = pairs().get(state.getBlock());
        return other == null ? null : other.withPropertiesOf(state);
    }

    /** Loads this class, so its blocks are registered with the rest. */
    public static void init() {}

    /** Fire takes them as it takes the planks and slabs: the Nether's woods do not burn. */
    @SubscribeEvent
    static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            FireBlockAccessor fire = (FireBlockAccessor) (FireBlock) Blocks.FIRE;
            for (String wood : ModWoods.SUPPORT_WOODS) {
                if (!wood.equals("crimson") && !wood.equals("warped")) {
                    fire.nookworks$setFlammable(PLANKS.get(wood).get(), 5, 20);
                    fire.nookworks$setFlammable(SLABS.get(wood).get(), 5, 20);
                }
            }
        });
    }

    private TurnedPlanks() {}
}
