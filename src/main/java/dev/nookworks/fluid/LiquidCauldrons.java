package dev.nookworks.fluid;

import java.util.List;
import java.util.function.Supplier;

import dev.nookworks.Nookworks;
import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.cauldron.CauldronInteractions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCauldronInteractionEvent;
import net.neoforged.neoforge.fluids.RegisterCauldronFluidContentEvent;

/**
 * What fills and empties the cauldrons of our liquids ({@link LiquidCauldronBlock}), as the Vegetable Oil Cauldron's
 * (dev.nookworks.quench.Quenching) does its own: the liquid's bucket fills any cauldron, as a water or lava bucket does;
 * an empty bucket takes a full one back; a water or lava bucket replaces what is in it, as vanilla's do each other's.
 * Bottles do not: a cauldron's level is a third of a bucket and a bottle a quarter, so three bottles in and a bucket out
 * would make a quarter of a bucket out of nothing - liquid experience above all.
 * <p>
 * Pipes, tanks and a dispenser's buckets fill and drain them as a water cauldron, a bucket at a time (NeoForge's
 * cauldron fluid content) - all but the mercury cauldron: mercury goes through no fluid pipe, so it is by the Mercury
 * Bucket alone, by hand or by dispenser ({@link LiquidDispensing}).
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class LiquidCauldrons {
    public static final CauldronInteraction.Dispatcher METHANOL = new CauldronInteraction.Dispatcher();
    public static final CauldronInteraction.Dispatcher WOOD_VINEGAR = new CauldronInteraction.Dispatcher();
    public static final CauldronInteraction.Dispatcher BIODIESEL = new CauldronInteraction.Dispatcher();
    public static final CauldronInteraction.Dispatcher EXPERIENCE = new CauldronInteraction.Dispatcher();
    public static final CauldronInteraction.Dispatcher MERCURY = new CauldronInteraction.Dispatcher();

    /** One cauldron: its interactions, its block, its bucket, its liquid (none for mercury: no fluid content), its sounds. */
    private record Kind(String name, CauldronInteraction.Dispatcher interactions, Supplier<? extends Block> block,
            Supplier<? extends Item> bucket, Supplier<? extends Fluid> fluid, SoundEvent fill, SoundEvent empty) {}

    private static List<Kind> kinds() {
        return List.of(
                new Kind("methanol", METHANOL, ModBlocks.METHANOL_CAULDRON, ModOil.METHANOL_BUCKET, OilFluids.METHANOL,
                        SoundEvents.BUCKET_FILL, SoundEvents.BUCKET_EMPTY),
                new Kind("wood_vinegar", WOOD_VINEGAR, ModBlocks.WOOD_VINEGAR_CAULDRON, ModOil.WOOD_VINEGAR_BUCKET, OilFluids.WOOD_VINEGAR,
                        SoundEvents.BUCKET_FILL, SoundEvents.BUCKET_EMPTY),
                new Kind("biodiesel", BIODIESEL, ModBlocks.BIODIESEL_CAULDRON, ModOil.BIODIESEL_BUCKET, OilFluids.BIODIESEL,
                        SoundEvents.BUCKET_FILL, SoundEvents.BUCKET_EMPTY),
                new Kind("liquid_experience", EXPERIENCE, ModBlocks.LIQUID_EXPERIENCE_CAULDRON, ModItems.EXPERIENCE_BUCKET,
                        ExperienceFluids.SOURCE, SoundEvents.BUCKET_FILL, SoundEvents.BUCKET_EMPTY),
                // mercury's own sounds, the lava bucket's, as at a Mercury Tank and in the world
                new Kind("mercury", MERCURY, ModBlocks.MERCURY_CAULDRON, ModItems.MERCURY_BUCKET, null,
                        SoundEvents.BUCKET_FILL_LAVA, SoundEvents.BUCKET_EMPTY_LAVA));
    }

    private LiquidCauldrons() {}

    @SubscribeEvent
    static void dispatchers(RegisterCauldronInteractionEvent.Dispatcher event) {
        for (Kind kind : kinds()) {
            event.register(Nookworks.id(kind.name()), kind.interactions());
        }
    }

    @SubscribeEvent
    static void interactions(RegisterCauldronInteractionEvent.Interaction event) {
        for (Kind kind : kinds()) {
            event.registerToAll(kind.bucket().get(), (state, level, pos, player, hand, held) -> CauldronInteractions.emptyBucket(
                    level, pos, player, hand, held, full(kind.block().get()), kind.empty()));
            CauldronInteractions.addDefaultInteractions(kind.interactions());
            kind.interactions().put(Items.BUCKET, (state, level, pos, player, hand, held) -> CauldronInteractions.fillBucket(state, level,
                    pos, player, hand, held, new ItemStack(kind.bucket().get()), s -> s.getValue(LayeredCauldronBlock.LEVEL) == 3,
                    kind.fill()));
        }
    }

    /** Pipes and tanks fill and drain them as a cauldron of water: a bucket's worth, three levels. Not mercury's. */
    @SubscribeEvent
    static void fluid(RegisterCauldronFluidContentEvent event) {
        for (Kind kind : kinds()) {
            if (kind.fluid() != null) {
                event.register(kind.block().get(), kind.fluid().get(), 1000, LayeredCauldronBlock.LEVEL);
            }
        }
    }

    /** A full cauldron of {@code block}. */
    public static net.minecraft.world.level.block.state.BlockState full(Block block) {
        return block.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3);
    }
}
