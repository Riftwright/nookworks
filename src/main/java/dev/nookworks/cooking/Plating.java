package dev.nookworks.cooking;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.UseRemainder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * Dishes in bowls, and the ones that are served on a plate instead (the user, 2026-11-14: スープとかチャーハンを皿にのせる
 * と 皿の上に器を載せる という変なことしてるよね。既に器に入っているのは、皿に置かずに、直接ワールドに置ける方が自然 ...
 * チャーハンや野菜炒めは、皿に盛るのが普通だと思うから、作る過程で、直接皿を使うか、皿バージョンと器バージョンを両方用意
 * する).
 * <ul>
 * <li>{@link #BOWL_DISHES}, {@code #nookworks:bowl_dishes}: a dish in its own bowl. It is set straight down on the table
 * ({@link PlacedBowlsBlock}) and goes on no Plate; a Wooden Tray still takes it.</li>
 * <li>Data map {@code nookworks:plated} on items: what a dish in a bowl is, served on a plate ({@link PlatedDishItem}) -
 * fried rice, stir-fried vegetables. From the Frying Pan with a Plate in hand, or the bowl used on an empty Plate set
 * on the table, and the bowl is handed back. A soup has none: it stays in its bowl.</li>
 * <li>A Plate with its food on it is one thing to carry: the Plate's item with the food in its container (lifted from the
 * table by a sneaking empty hand, {@link PlateBlockEntity#lift}; {@link #onPlate}, {@link #setOnPlate}). A Wooden Tray
 * takes it as a lot, plate and all.</li>
 * <li>{@link #SERVED_ON_PLATES}, {@code #nookworks:served_on_plates}: food that is served on a plate and not laid on
 * the bare board of a tray - a cutlet, karaage, a steak (the user, 2026-11-14: とんかつや空揚げは、そもそも、そのまま
 * トレイに載せない気がする。お皿に盛りつけたとんかつを、皿ごとにトレイに載せるであれば分かる). It goes on a tray on a
 * plate.</li>
 * </ul>
 */
public record Plating(Holder<Item> plated) {
    public static final TagKey<Item> BOWL_DISHES = TagKey.create(Registries.ITEM, Nookworks.id("bowl_dishes"));
    public static final TagKey<Item> SERVED_ON_PLATES = TagKey.create(Registries.ITEM, Nookworks.id("served_on_plates"));
    public static final Codec<Plating> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("plated").forGetter(Plating::plated)
    ).apply(i, Plating::new));
    public static final DataMapType<Item, Plating> DATA_MAP =
            DataMapType.builder(Nookworks.id("plated"), Registries.ITEM, CODEC).synced(CODEC, false).build();

    /** One of {@code dish} as it is served on a plate, or empty for a dish that is not. */
    public static ItemStack of(ItemStack dish) {
        Plating plating = dish.isEmpty() ? null : dish.typeHolder().getData(DATA_MAP);
        return plating == null ? ItemStack.EMPTY : new ItemStack(plating.plated());
    }

    /** Whether {@code stack} is a dish in its own bowl. */
    public static boolean inBowl(ItemStack stack) {
        return stack.is(BOWL_DISHES);
    }

    /** Whether {@code stack} is a dish on its own plate. */
    public static boolean plated(ItemStack stack) {
        return stack.getItem() instanceof PlatedDishItem;
    }

    /** Whether {@code stack} is food that goes on a tray only on a plate. */
    public static boolean wantsPlate(ItemStack stack) {
        return stack.is(SERVED_ON_PLATES);
    }

    /** Whether {@code stack} is a Plate's item: empty, or with food on it. */
    public static boolean isPlate(ItemStack stack) {
        return stack.is(dev.nookworks.registry.ModKitchen.PLATE_ITEM.get());
    }

    /** What lies on the Plate item {@code plate} (a copy; all of it, one stack), or empty. */
    public static ItemStack onPlate(ItemStack plate) {
        ItemContainerContents contents = plate.get(DataComponents.CONTAINER);
        return contents == null ? ItemStack.EMPTY : contents.copyOne();
    }

    /** Lays {@code food} (all of it) on the Plate item {@code plate}, one to a stack while it holds any; empty takes it off. */
    public static void setOnPlate(ItemStack plate, ItemStack food) {
        if (food.isEmpty()) {
            plate.remove(DataComponents.CONTAINER);
            plate.remove(DataComponents.MAX_STACK_SIZE);
        } else {
            plate.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(java.util.List.of(food)));
            plate.set(DataComponents.MAX_STACK_SIZE, 1);
        }
    }

    /** Whether one more of {@code food} goes on the Plate item {@code plate}: it is empty, or holds the same with room. */
    public static boolean plateTakes(ItemStack plate, ItemStack food) {
        if (!PlateBlockEntity.fits(food) || plated(food)) {
            return false; // a dish on its own plate wants no second one
        }
        ItemStack on = onPlate(plate);
        return on.isEmpty() || ItemStack.isSameItemSameComponents(on, food) && on.getCount() < Serving.of(food).plate();
    }

    /** What one of {@code dish} leaves when it is eaten, or moved to a plate: its bowl; empty for a dish that leaves nothing. */
    public static ItemStack emptied(ItemStack dish) {
        UseRemainder left = dish.get(DataComponents.USE_REMAINDER);
        return left == null ? ItemStack.EMPTY : left.convertInto().create();
    }

    @EventBusSubscriber(modid = Nookworks.MODID)
    static final class Registration {
        @SubscribeEvent
        static void register(RegisterDataMapTypesEvent event) {
            event.register(DATA_MAP);
        }

        private Registration() {}
    }
}
