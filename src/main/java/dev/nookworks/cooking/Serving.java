package dev.nookworks.cooking;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * How much of a food a Plate and a Wooden Tray hold, food by food (the user, 2026-11-14: お皿やトレイにおける数は、品目に
 * よって1つずつ見直して最適化して欲しいな。クッキーならお皿に数個置けてもいいと思う): data map {@code nookworks:serving} on
 * items, so a datapack sets another mod's food.
 * <ul>
 * <li>{@code plate} - how many of it one Plate holds (one kind to a plate); 1 when not said.</li>
 * <li>{@code room} - the room one lot of it takes on a tray: a {@code quarter} of the board (four lots to a tray, as
 * ever), a {@code half} (a long thing lies across it: a fish, a cutlet with its cabbage), or the {@code whole} tray
 * (a cake, a dish on its own plate). A quarter when not said.</li>
 * <li>{@code tray} - how many of it lie together in that room; 1 when not said.</li>
 * <li>{@code leaning} - on a Plate, several of it lean one on the next instead of lying side by side: cookies (the
 * user, 2026-11-14, with a photograph of three on a plate: クッキーはちょっと重なる感じにしたほうがおしゃれ). On a tray
 * they lie flat, as on a baking sheet.</li>
 * <li>{@code height} - how tall one of it stands on a Plate, in pixels, as it is drawn there (from its model, by
 * tools/gen_servings.py): what is on a plate is stood on and clicked as tall as it is. 0 when not said - a food drawn
 * as its item's picture lies flat.</li>
 * <li>{@code piled} - on a Plate, several of it are piled one on another, each that much taller: doughnuts in a
 * tower (the user, 2026-11-14, play-testing: 「ドーナッツタワーの当たり判定がなくて、埋まってしまう」).</li>
 * </ul>
 * Before this every food was one to a plate and four to a tray, whatever its size: four cutlets ran into one another.
 */
public record Serving(int plate, int tray, Room room, boolean leaning, float height, boolean piled) {
    /** The room one lot of a food takes on a tray, in quarters of its board. */
    public enum Room implements StringRepresentable {
        QUARTER("quarter", 1),
        HALF("half", 2),
        WHOLE("whole", 4);

        public static final Codec<Room> CODEC = StringRepresentable.fromEnum(Room::values);
        private final String name;
        private final int quarters;

        Room(String name, int quarters) {
            this.name = name;
            this.quarters = quarters;
        }

        public int quarters() {
            return this.quarters;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    /** Any food not in the map: one to a plate, one to a quarter of a tray. */
    public static final Serving ONE = new Serving(1, 1, Room.QUARTER, false, 0, false);
    public static final Codec<Serving> CODEC = RecordCodecBuilder.create(i -> i.group(
            ExtraCodecs.intRange(1, 8).optionalFieldOf("plate", 1).forGetter(Serving::plate),
            ExtraCodecs.intRange(1, 8).optionalFieldOf("tray", 1).forGetter(Serving::tray),
            Room.CODEC.optionalFieldOf("room", Room.QUARTER).forGetter(Serving::room),
            Codec.BOOL.optionalFieldOf("leaning", false).forGetter(Serving::leaning),
            Codec.floatRange(0, 16).optionalFieldOf("height", 0F).forGetter(Serving::height),
            Codec.BOOL.optionalFieldOf("piled", false).forGetter(Serving::piled)
    ).apply(i, Serving::new));

    /** How tall {@code count} of this stand on a Plate, in pixels above its top: piled, each adds its height. */
    public float heightOnPlate(int count) {
        return this.piled ? this.height * Math.max(1, count) : this.height;
    }
    public static final DataMapType<Item, Serving> DATA_MAP =
            DataMapType.builder(Nookworks.id("serving"), Registries.ITEM, CODEC).synced(CODEC, false).build();

    public static Serving of(ItemStack stack) {
        Serving serving = stack.isEmpty() ? null : stack.getItemHolder().getData(DATA_MAP);
        return serving == null ? ONE : serving;
    }

    /** A tray's rows, from its back to its front, and the board's middle between them. */
    public static final float BACK = 5, FRONT = 11, MIDDLE = 8;
    /** Where the two things of a row of quarters lie, across the board. */
    public static final float LEFT = 5, RIGHT = 11;
    /** As a tray was laid before its things had rooms: by how many there are (kept for what an old tray holds). */
    private static final float[][][] BY_COUNT = {
            {{8, 8}},
            {{5, 8}, {11, 8}},
            {{5, 5.5F}, {11, 5.5F}, {8, 11}},
            {{5, 5}, {11, 5}, {5, 11}, {11, 11}},
    };

    /**
     * Whether a tray's lots, slot by slot (empty stacks between them or not), find room on its board: a whole-tray thing
     * alone, otherwise two rows, each holding a half or up to two quarters.
     */
    public static boolean packs(List<ItemStack> slots) {
        return rows(slots) != null;
    }

    /** The row of each slot's lot (0 or 1; -1 for an empty slot, 2 for the whole board), or null when they do not pack. */
    private static int @Nullable [] rows(List<ItemStack> slots) {
        int[] row = new int[slots.size()];
        int[] used = new int[2];
        int lots = (int) slots.stream().filter(stack -> !stack.isEmpty()).count();
        // what stands on the board itself first, then the plates: so one plate among bowls gets a row to itself
        // (the main dish, set behind the rice and the soup), whichever went on first
        for (int pass = 0; pass < 2; pass++) {
            for (int slot = 0; slot < slots.size(); slot++) {
                ItemStack stack = slots.get(slot);
                if (stack.isEmpty()) {
                    row[slot] = -1;
                    continue;
                }
                if (onPlate(stack) != (pass == 1)) {
                    continue;
                }
                int quarters = of(stack).room().quarters();
                if (quarters == 4) {
                    if (lots != 1) {
                        return null;
                    }
                    row[slot] = 2;
                    continue;
                }
                int into = used[0] + quarters <= 2 ? 0 : used[1] + quarters <= 2 ? 1 : -1;
                if (into < 0) {
                    return null;
                }
                used[into] += quarters;
                row[slot] = into;
            }
        }
        return row;
    }

    /** Whether {@code stack} is a lot that stands on a plate: a Plate with or without food, or a dish on its own plate. */
    private static boolean onPlate(ItemStack stack) {
        return Plating.isPlate(stack) || Plating.plated(stack);
    }

    /**
     * Where each slot's lot lies on a tray whose front faces south: {x, z} in pixels, null for an empty slot. One lot
     * alone lies in the middle; lots that fill one row lie along the middle; otherwise in two rows, a long thing or a
     * plate behind a row of two (the cutlet on its plate behind, the rice and the soup before it). A tray laid before its
     * things had rooms, holding more than packs, is laid as it was: by how many there are.
     */
    public static float @Nullable [][] trayPlaces(List<ItemStack> slots) {
        float[][] at = new float[slots.size()][];
        int lots = (int) slots.stream().filter(stack -> !stack.isEmpty()).count();
        if (lots == 0) {
            return at;
        }
        int[] row = rows(slots);
        if (row == null || lots == 1) {
            float[][] places = BY_COUNT[Math.min(lots, BY_COUNT.length) - 1];
            int n = 0;
            for (int slot = 0; slot < slots.size(); slot++) {
                if (!slots.get(slot).isEmpty()) {
                    at[slot] = places[Math.min(n++, places.length - 1)];
                }
            }
            return at;
        }
        int[] inRow = new int[2];
        int[] behind = new int[2]; // how much a row wants to be the back one: a long thing in it, then its plates
        for (int slot = 0; slot < slots.size(); slot++) {
            if (row[slot] == 0 || row[slot] == 1) {
                inRow[row[slot]]++;
                behind[row[slot]] += (of(slots.get(slot)).room() == Room.HALF ? 4 : 0) + (onPlate(slots.get(slot)) ? 1 : 0);
            }
        }
        boolean oneRow = inRow[1] == 0;
        // a long thing lies behind a row of two, and plates behind bowls, whichever went on first
        boolean swap = !oneRow && behind[1] > behind[0];
        int[] seen = new int[2];
        for (int slot = 0; slot < slots.size(); slot++) {
            int r = row[slot];
            if (r < 0) {
                continue;
            }
            float z = oneRow ? MIDDLE : (r == 0) != swap ? BACK : FRONT;
            float x = inRow[r] == 1 ? MIDDLE : seen[r]++ == 0 ? LEFT : RIGHT;
            at[slot] = new float[] {x, z};
        }
        return at;
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
