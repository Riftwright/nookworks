package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import dev.nookworks.Nookworks;
import dev.nookworks.feature.AddonImpact;
import dev.nookworks.feature.Features;
import dev.nookworks.feature.PartGraph;
import dev.nookworks.feature.PartsChange;
import dev.nookworks.feature.WorldFeatures;
import dev.nookworks.mercury.MercuryRecipes;
import dev.nookworks.registry.ModConditions;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * Every part can be switched off, and what it leaves nothing to reach goes with it (the user, 2026-11-01:
 * 「「これをオフにしたら、これとコレが作れなくなるよ」って警告を出したうえで、オフにすること自体はできる」).
 */
final class PartGraphTests {
    private static final String CELL = "electrolytic_cell";
    /** What the Electrolytic Cell takes with it: caustic soda is made by it alone, and paper, soap and biodiesel of that. */
    private static final Set<String> WITH_THE_CELL = Set.of("nookworks:biodiesel_bottle", "nookworks:biodiesel_bucket",
            "nookworks:caustic_soda", "nookworks:fine_paper", "nookworks:label", "nookworks:poster", "nookworks:printing_press",
            "nookworks:soap", "nookworks:wood_pulp");

    private PartGraphTests() {}

    static void register() {
        test("the_part_table_answers_as_its_tool_does", PartGraphTests::answersAsTheTool);
        test("a_part_switched_off_takes_what_nothing_else_reaches", PartGraphTests::takesWhatNothingElseReaches);
        test("every_part_stands_in_one_group_of_the_list", PartGraphTests::everyPartInOneGroup);
        test("another_mods_recipes_held_up_by_our_parts_are_counted", PartGraphTests::addonRecipesCounted);
    }

    /**
     * The game's walk of the table (PartGraph) and tools/part_graph.py's give the same answer for each part switched off
     * alone, and nothing is lost with every part on; the table knows every item of ours, and nothing else.
     */
    private static void answersAsTheTool(GameTestHelper helper) {
        check(helper, PartGraph.lost(part -> true, List.of()).isEmpty(), "with every part on, nothing is lost");
        List<String> differ = new ArrayList<>();
        for (Map.Entry<String, List<String>> expected : PartGraph.EXPECTED.entrySet()) {
            String off = expected.getKey();
            Set<String> lost = PartGraph.lost(part -> !part.equals(off), List.of());
            if (!lost.equals(new TreeSet<>(expected.getValue()))) {
                differ.add(off + ": the game says " + lost + ", the tool " + expected.getValue());
            }
        }
        check(helper, differ.isEmpty(), "the game and the tool agree on what each part takes: " + differ);
        check(helper, PartGraph.EXPECTED.keySet().equals(Features.PARTS.keySet()), "the table has every part's answer");
        Set<String> ours = new HashSet<>();
        BuiltInRegistries.ITEM.keySet().stream().filter(id -> id.getNamespace().equals(Nookworks.MODID)).forEach(id -> ours.add(id.toString()));
        check(helper, ours.equals(new HashSet<>(PartGraph.ITEMS)), "the table knows every item of ours and no other: missing "
                + ours.stream().filter(id -> !PartGraph.ITEMS.contains(id)).toList() + ", no item "
                + PartGraph.ITEMS.stream().filter(id -> !ours.contains(id)).toList());
        helper.succeed();
    }

    /**
     * The Electrolytic Cell off (r19 CXM-003): caustic soda, made by it alone, goes, and the paper, posters, labels, soap
     * and biodiesel made with it; the Printing Press's part is left with nothing. Their items are not had, their recipes
     * not loaded, no machine makes them, and the parts list names them; an item a pack keeps brings them back. With it
     * on again, all of it is back.
     */
    private static void takesWhatNothingElseReaches(GameTestHelper helper) {
        WorldFeatures.Choice was = WorldFeatures.current();
        try {
            WorldFeatures.restore(new WorldFeatures.Choice(null, Map.of(CELL, false)));
            check(helper, !Features.enabled(ModItems.CAUSTIC_SODA.get()) && !Features.enabled(ModItems.PRINTING_PRESS.get())
                    && !Features.available("nookworks:fine_paper"), "caustic soda and what is made of it are not had");
            check(helper, Features.enabled(ModItems.BRASS_INGOT.get()) && Features.enabled(ModItems.SALT.get()),
                    "what does not stand on it stays");
            check(helper, Features.disabledItems().contains(ModItems.CAUSTIC_SODA.get()), "and JEI hides it");
            check(helper, !new ModConditions.Available(List.of("nookworks:fine_paper"), false).test(ICondition.IContext.EMPTY)
                    && new ModConditions.Available(List.of("nookworks:fine_paper", "nookworks:brass_ingot"), true).test(ICondition.IContext.EMPTY),
                    "the availability condition holds by every item named, or by any with \"any\"");
            check(helper, !conditionsHold("fine_paper"), "the Fine Paper recipe's own conditions do not hold");
            check(helper, MercuryRecipes.retorting(new ItemStack(ModItems.WOOD_CHIPS.get())) == null,
                    "a Sealed Retort makes no wood pulp, for which the soda is wanted");
            List<String> lost = PartsChange.newlyLost(part -> true, part -> !part.equals(CELL), List.of());
            check(helper, new HashSet<>(lost).equals(WITH_THE_CELL), "the parts list names what goes: " + lost);
            Set<String> lostNow = PartGraph.lost(part -> !part.equals(CELL), List.of());
            check(helper, PartsChange.taken("printing", part -> !part.equals(CELL), lostNow)
                    && !PartsChange.taken("kitchen", part -> !part.equals(CELL), lostNow), "the printing part is left with nothing, the kitchen is not");
            check(helper, PartGraph.lost(part -> !part.equals(CELL), List.of("nookworks:caustic_soda")).isEmpty(),
                    "a pack that keeps caustic soda keeps all that is made of it");
        } finally {
            WorldFeatures.restore(was);
        }
        check(helper, Features.enabled(ModItems.CAUSTIC_SODA.get()) && conditionsHold("fine_paper")
                && MercuryRecipes.retorting(new ItemStack(ModItems.WOOD_CHIPS.get())) != null, "with the cell on again, all of it is back");
        helper.succeed();
    }

    /**
     * An add-on's recipes carrying our conditions (as nookTechnology's do) are counted for the parts list by mod, as a
     * change stops them (the user, 2026-11-03: 「Tech使えなくなるよ?的な警告」): one needing the Motor stops with the
     * Motor off, one needing caustic soda with the Electrolytic Cell off (what nothing reaches counts), and one with ours
     * under a "not" is left out rather than guessed.
     */
    private static void addonRecipesCounted(GameTestHelper helper) {
        AddonImpact.Gate motor = AddonImpact.gate("tec", JsonParser.parseString(
                "{\"neoforge:conditions\":[{\"type\":\"nookworks:feature\",\"feature\":\"motor\"}],\"type\":\"minecraft:crafting_shaped\"}"));
        AddonImpact.Gate soda = AddonImpact.gate("tec", JsonParser.parseString(
                "{\"neoforge:conditions\":[{\"type\":\"neoforge:and\",\"values\":[{\"type\":\"nookworks:available\",\"items\":[\"nookworks:caustic_soda\"]}]}]}"));
        AddonImpact.Gate guessed = AddonImpact.gate("tec", JsonParser.parseString(
                "{\"neoforge:conditions\":[{\"type\":\"neoforge:not\",\"value\":{\"type\":\"nookworks:feature\",\"feature\":\"motor\"}}]}"));
        AddonImpact.Gate plain = AddonImpact.gate("tec", JsonParser.parseString("{\"type\":\"minecraft:crafting_shaped\"}"));
        check(helper, motor != null && motor.parts().equals(List.of("motor")) && soda != null && soda.needs().size() == 1,
                "our conditions are read, through an and");
        check(helper, guessed == null && plain == null, "a not over ours, or no condition of ours, makes no gate");
        List<AddonImpact.Gate> gates = List.of(motor, soda);
        check(helper, AddonImpact.newlyStopped(gates, part -> true, part -> !part.equals("motor"), List.of()).equals(Map.of("tec", 1)),
                "the Motor off stops the recipe that needs it");
        check(helper, AddonImpact.newlyStopped(gates, part -> true, part -> !part.equals(CELL), List.of()).equals(Map.of("tec", 1)),
                "the cell off stops the one needing what only it makes");
        check(helper, AddonImpact.newlyStopped(gates, part -> true, part -> !part.equals("kitchen"), List.of()).isEmpty(),
                "an unrelated part stops nothing");
        check(helper, AddonImpact.newlyStopped(gates, part -> !part.equals("motor"), part -> !part.equals("motor"), List.of()).isEmpty(),
                "what was stopped already is not counted again");
        helper.succeed();
    }

    /** Whether recipe {@code name}'s conditions, as its file has them, hold now. */
    private static boolean conditionsHold(String name) {
        String path = "/data/nookworks/recipe/" + name + ".json";
        try (var in = PartGraphTests.class.getResourceAsStream(path)) {
            var json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            var conditions = ICondition.LIST_CODEC.parse(JsonOps.INSTANCE, json.get("neoforge:conditions")).getOrThrow();
            return conditions.stream().allMatch(c -> c.test(ICondition.IContext.EMPTY));
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    /** The parts list's groups hold every part once, and only parts. */
    private static void everyPartInOneGroup(GameTestHelper helper) {
        List<String> grouped = new ArrayList<>();
        Features.GROUPS.values().forEach(grouped::addAll);
        check(helper, grouped.size() == new HashSet<>(grouped).size() && new HashSet<>(grouped).equals(Features.PARTS.keySet()),
                "every part in one group: " + grouped);
        helper.succeed();
    }
}
