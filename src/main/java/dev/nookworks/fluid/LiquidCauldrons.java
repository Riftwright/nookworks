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
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
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
    // PORT: 26.3's dispatchers, registered under these ids by RegisterCauldronInteractionEvent.Dispatcher; 1.21.1's
    // interaction maps register themselves (CauldronInteraction.INTERACTIONS) as they are made
    public static final CauldronInteraction.InteractionMap METHANOL = CauldronInteraction.newInteractionMap(Nookworks.id("methanol").toString());
    public static final CauldronInteraction.InteractionMap WOOD_VINEGAR = CauldronInteraction.newInteractionMap(Nookworks.id("wood_vinegar").toString());
    public static final CauldronInteraction.InteractionMap BIODIESEL = CauldronInteraction.newInteractionMap(Nookworks.id("biodiesel").toString());
    public static final CauldronInteraction.InteractionMap EXPERIENCE = CauldronInteraction.newInteractionMap(Nookworks.id("liquid_experience").toString());
    public static final CauldronInteraction.InteractionMap MERCURY = CauldronInteraction.newInteractionMap(Nookworks.id("mercury").toString());

    /** One cauldron: its interactions, its block, its bucket, its liquid (none for mercury: no fluid content), its sounds. */
    private record Kind(String name, CauldronInteraction.InteractionMap interactions, Supplier<? extends Block> block,
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

    // PORT: 26.3 fills the dispatchers at RegisterCauldronInteractionEvent.Interaction; 1.21.1 has no such event, so
    // they are filled once the registries stand, on the main thread. "To all" is every interaction map there is then
    // (vanilla's, ours, other mods'), as 26.3's registerToAll is every dispatcher registered.
    @SubscribeEvent
    static void interactions(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (Kind kind : kinds()) {
                CauldronInteraction emptyBucket = (state, level, pos, player, hand, held) -> CauldronInteraction.emptyBucket(
                        level, pos, player, hand, held, full(kind.block().get()), kind.empty());
                for (CauldronInteraction.InteractionMap each : CauldronInteraction.INTERACTIONS.values()) {
                    each.map().put(kind.bucket().get(), emptyBucket);
                }
                CauldronInteraction.addDefaultInteractions(kind.interactions().map());
                kind.interactions().map().put(Items.BUCKET, (state, level, pos, player, hand, held) -> CauldronInteraction.fillBucket(state, level,
                        pos, player, hand, held, new ItemStack(kind.bucket().get()), s -> s.getValue(LayeredCauldronBlock.LEVEL) == 3,
                        kind.fill()));
            }
        });
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
