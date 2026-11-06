package dev.nookworks.gametest;

import static dev.nookworks.gametest.BlockTests.underClaim;
import static dev.nookworks.gametest.NookworksGameTests.check;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.UUID;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksStartupConfig;
import dev.nookworks.block.PlanterBlock;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.PlanterBlockEntity;
import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import dev.nookworks.compat.CompatHooks;
import dev.nookworks.compat.EnglishNames;
import dev.nookworks.cooking.PotRecipe;
import dev.nookworks.machine.Electrolysis;
import dev.nookworks.mercury.MercuryFluids;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
import dev.nookworks.registry.ModRope;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.material.Fluid;

/**
 * Nookworks among other mods: the common tags it shares with them, its hooks into theirs, and the claim mods it asks
 * before putting things on someone's land.
 */
final class CompatTests {
    static void register() {
        NookworksGameTests.test("compat_hooks_wait_for_their_mods", CompatTests::compatHooksWaitForTheirMods);
        NookworksGameTests.test("other_mods_salt_and_eggs_go_in", CompatTests::otherModsSaltAndEggsGoIn);
        NookworksGameTests.test("common_tags_name_our_things", CompatTests::commonTagsNameOurThings);
        NookworksGameTests.test("planter_asks_as_its_owner", CompatTests::planterAsksAsItsOwner);
        NookworksGameTests.test("mob_bottle_lets_claims_decide_first", CompatTests::mobBottleLetsClaimsDecideFirst);
        NookworksGameTests.test("english_names_read_as_the_game_reads_them", CompatTests::englishNamesReadAsTheGameReadsThem);
    }

    /**
     * A Planter plants as the player who set it down: in its owner's claim it plants, in someone else's it plants
     * nothing and its screen says the land is protected (it said "nowhere to plant"). A refusal is kept a while (r16
     * N3), so the land as its owner's is shown to a planter set down afresh.
     */
    private static void planterAsksAsItsOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(3, 2, 1);
        for (int x = 2; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 1, 2), Blocks.FARMLAND);
        }
        UUID owner = UUID.randomUUID();
        PlanterBlockEntity planter = planter(helper, pos, owner);
        BlockTests.underClaim(UUID.randomUUID(), () -> planter.work(level));
        check(helper, planter.guiStatus() == MachineStatus.PROTECTED && planter.getItem(0).getCount() == 4,
                "someone else's land: nothing planted, and it says why: " + planter.guiStatus());
        for (int x = 2; x <= 4; x++) {
            check(helper, helper.getBlockState(new BlockPos(x, 2, 2)).isAir(), "the row is bare");
        }
        helper.setBlock(pos, Blocks.AIR);
        PlanterBlockEntity again = planter(helper, pos, owner);
        BlockTests.underClaim(owner, () -> again.work(level));
        check(helper, again.guiStatus() == MachineStatus.RUNNING && again.getItem(0).getCount() == 3, "its owner's: it plants");
        helper.succeed();
    }

    /** A Planter facing south at {@code pos}, owned by {@code owner}: full of power, four wheat seeds in it. */
    private static PlanterBlockEntity planter(GameTestHelper helper, BlockPos pos, UUID owner) {
        helper.setBlock(pos, ModBlocks.PLANTER.get().defaultBlockState().setValue(PlanterBlock.FACING, Direction.SOUTH));
        PlanterBlockEntity planter = helper.getBlockEntity(pos, PlanterBlockEntity.class);
        planter.energy().set((int) planter.energy().getCapacityAsLong());
        planter.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 4));
        planter.setOwner(owner);
        return planter;
    }

    /**
     * The bottle hears the click on a mob last, so a claim mod keeping others' animals safe can say no before it catches
     * one, and an add-on's stronger bottle (at LOW) before ours (LOWEST since r21).
     */
    private static void mobBottleLetsClaimsDecideFirst(GameTestHelper helper) {
        try {
            var handler = MobBottleItem.class.getDeclaredMethod("onInteract", PlayerInteractEvent.EntityInteract.class);
            check(helper, handler.getAnnotation(SubscribeEvent.class).priority() == EventPriority.LOWEST, "it listens at the lowest priority");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
        check(helper, MobBottleItem.MAX_BYTES <= 64 * 1024, "and a bottled mob weighs 64 KB at most");
        helper.succeed();
    }

    private static TagKey<Item> item(String id) {
        return TagKey.create(Registries.ITEM, Identifier.parse(id));
    }

    private static TagKey<Fluid> fluid(String id) {
        return TagKey.create(Registries.FLUID, Identifier.parse(id));
    }

    /**
     * The hooks into Sodium and Iris are not listed in the compat config - listed, each start without those mods logged
     * that their classes were missing - but added by its plugin when their mods are there; and each name it adds one by
     * is a mixin of ours, so a renamed one is caught here rather than quietly lost.
     */
    private static void compatHooksWaitForTheirMods(GameTestHelper helper) {
        ClassLoader loader = CompatTests.class.getClassLoader();
        JsonObject config;
        try (InputStream in = loader.getResourceAsStream("nookworks.compat.mixins.json")) {
            check(helper, in != null, "the compat config is in the jar");
            config = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        check(helper, config.has("plugin") && "dev.nookworks.mixin.compat.CompatMixins".equals(config.get("plugin").getAsString()),
                "the plugin picks the hooks");
        for (String side : List.of("mixins", "client", "server")) {
            check(helper, !config.has(side) || config.getAsJsonArray(side).isEmpty(), "no hook listed outright under " + side);
        }
        for (String mixin : CompatHooks.TARGETS.keySet()) {
            check(helper, loader.getResource("dev/nookworks/mixin/compat/" + mixin + ".class") != null, mixin + " is a mixin of ours");
        }
        check(helper, CompatHooks.present(target -> false).isEmpty(), "no hooks without the mods");
        check(helper, CompatHooks.present(target -> target.startsWith("net.caffeinemc.")).equals(List.of("SodiumLevelSliceMixin")),
                "Sodium brings its own hook alone");
        check(helper, CompatHooks.present(target -> target.startsWith("net.irisshaders.")).equals(List.of("IrisBlockIdsMixin", "IrisHandLightMixin")),
                "Iris brings its two");
        helper.succeed();
    }

    /**
     * Salt and eggs are asked for by their common tags, so another mod's go in: a cake takes a blue egg, the tonkatsu
     * pot recipe a brown one, and the Electrolytic Cell takes salt by {@code c:dusts/salt}, which ours is in.
     */
    private static void otherModsSaltAndEggsGoIn(GameTestHelper helper) {
        var level = helper.getLevel();
        ItemStack milk = new ItemStack(Items.MILK_BUCKET);
        ItemStack sugar = new ItemStack(Items.SUGAR);
        ItemStack grain = NookworksStartupConfig.flourBakery() ? new ItemStack(ModCooking.FLOUR.get()) : new ItemStack(Items.WHEAT);
        CraftingInput cake = CraftingInput.of(3, 3, List.of(milk, milk, milk, sugar, new ItemStack(Items.BLUE_EGG), sugar, grain, grain, grain));
        check(helper, level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, cake, level)
                .map(recipe -> recipe.value().assemble(cake).is(Items.CAKE)).orElse(false), "a cake with a blue egg");
        CraftingInput coating = CraftingInput.of(2, 2, List.of(new ItemStack(Items.PORKCHOP), new ItemStack(ModCooking.FLOUR.get()),
                new ItemStack(Items.BROWN_EGG), new ItemStack(ModCooking.BREADCRUMBS.get())));
        check(helper, level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, coating, level)
                .map(recipe -> recipe.value().assemble(coating).is(ModCooking.BREADED_PORK.get())).orElse(false),
                "tonkatsu's coat with a brown egg");
        ItemStack salt = new ItemStack(ModItems.SALT.get());
        check(helper, salt.is(item("c:dusts/salt")) && salt.is(item("c:dusts")), "our salt is a salt dust");
        Electrolysis run = Electrolysis.of(salt);
        check(helper, run != null && run.result().value() == ModItems.CAUSTIC_SODA.get(), "the cell takes salt by its tag");
        helper.succeed();
    }

    /**
     * What JEI is given to search by, with the game in another language: each name worked out from the English texts
     * as the game works it out. The server reads English, so every item in the creative tabs - potions, tipped arrows,
     * goat horns, ours - comes out as the server names it; and an enchanted book's enchantment, with its level.
     */
    private static void englishNamesReadAsTheGameReadsThem(GameTestHelper helper) {
        var level = helper.getLevel();
        Map<String, String> english = new HashMap<>();
        for (Class<?> owner : List.of(Item.class, Nookworks.class)) { // each jar's own, asked of a class in it
            String path = "/assets/" + (owner == Item.class ? "minecraft" : Nookworks.MODID) + "/lang/en_us.json";
            try (InputStream in = owner.getResourceAsStream(path)) {
                check(helper, in != null, path + " is there");
                JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().entrySet()
                        .forEach(e -> english.put(e.getKey(), e.getValue().getAsString()));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
        List<String> wrong = new ArrayList<>();
        int read = 0;
        for (var tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            for (ItemStack stack : tab.getSearchTabDisplayItems()) {
                String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
                if (!namespace.equals("minecraft") && !namespace.equals(Nookworks.MODID)) {
                    continue;
                }
                read++;
                String ours = EnglishNames.of(stack.getHoverName(), english);
                if (!ours.equals(stack.getHoverName().getString()) && wrong.size() < 5) {
                    wrong.add(ours + " / " + stack.getHoverName().getString());
                }
            }
        }
        check(helper, read > 1000 && wrong.isEmpty(), read + " names read, wrong: " + wrong);
        var sharpness = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
        check(helper, EnglishNames.of(Enchantment.getFullname(sharpness, 3), english).equals("Sharpness III"), "an enchantment and its level");
        english.put("nookworks.test.two", "%2$s then %1$s, 100%%");
        check(helper, EnglishNames.of(Component.translatable("nookworks.test.two", "a", Component.translatable("item.minecraft.stick")), english)
                .equals("Stick then a, 100%"), "arguments in their places, themselves in English");
        check(helper, EnglishNames.of(Component.translatableWithFallback("nookworks.test.none", "Else"), english).equals("Else"), "a fallback");
        check(helper, EnglishNames.of(Component.literal("Odd ").append(Component.translatable("item.minecraft.stick")), english)
                .equals("Odd Stick"), "plain text and what follows it");
        helper.succeed();
    }

    /** Other mods find our dishes, liquids and wrench under the common tags. */
    private static void commonTagsNameOurThings(GameTestHelper helper) {
        record Tagged(Item item, String tag) {}
        for (Tagged tagged : List.of(
                new Tagged(ModCooking.KARAAGE.get(), "c:foods/cooked_meat"),
                new Tagged(ModCooking.KARAAGE.get(), "c:foods"),
                new Tagged(ModCooking.FRIED_FISH.get(), "c:foods/cooked_fish"),
                new Tagged(ModRope.DRIED_FISH.get(), "c:foods"),
                new Tagged(ModKitchen.UDON.get(), "c:foods/soup"),
                new Tagged(ModCooking.COOKED_RICE.get(), "c:foods"),
                new Tagged(ModItems.WRENCH.get(), "c:tools/wrench"),
                new Tagged(ModItems.WRENCH.get(), "c:tools"))) {
            check(helper, new ItemStack(tagged.item()).is(item(tagged.tag())), tagged.item() + " is in #" + tagged.tag());
        }
        check(helper, OilFluids.SOURCE.get().defaultFluidState().is(fluid("c:vegetable_oil"))
                && OilFluids.FLOWING.get().defaultFluidState().is(fluid("c:plantoil")), "vegetable oil, and Immersive Engineering's plant oil");
        check(helper, OilFluids.BIODIESEL.get().defaultFluidState().is(fluid("c:biodiesel")), "biodiesel");
        check(helper, MercuryFluids.SOURCE.get().defaultFluidState().is(fluid("c:mercury")), "mercury");
        helper.succeed();
    }

    private CompatTests() {}
}
