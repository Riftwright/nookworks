package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.List;

import dev.nookworks.decor.BookendBlock;
import dev.nookworks.decor.BookendBlockEntity;
import dev.nookworks.decor.BottleLook;
import dev.nookworks.decor.ChevalMirrorBlock;
import dev.nookworks.decor.StandingMirrorBlockEntity;
import dev.nookworks.decor.Lanterns;
import dev.nookworks.decor.MirrorSight;
import dev.nookworks.decor.PlacedBooksBlock;
import dev.nookworks.decor.PlacedBooksBlockEntity;
import dev.nookworks.decor.PlacedBottlesBlock;
import dev.nookworks.decor.PlacedBottlesBlockEntity;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import dev.nookworks.registry.ModTravel;
import dev.nookworks.registry.ModWoods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import dev.nookworks.port.test.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import dev.nookworks.port.storage.ValueIO;

/**
 * The decorations of 2026-10-12: lanterns put out and lit again, and small bottles set down on a block (the user:
 * "置けるタイプの瓶", "消えてるランタン"); books and bookends after; the Standing Mirror (2026-10-28, "普通の鏡").
 */
final class DecorTests {
    static void register() {
        test("lantern_goes_out_and_lights_again", DecorTests::lanternOutAndLit);
        test("bottles_set_down_and_taken_back", DecorTests::bottlesSetDown);
        test("books_laid_down_and_taken_back", DecorTests::booksLaidDown);
        test("books_leant_against_a_wall", DecorTests::booksLeantAgainstAWall);
        test("bookend_holds_books", DecorTests::bookendHoldsBooks);
        test("standing_mirror_stands_whole", DecorTests::standingMirrorStandsWhole);
        test("standing_mirror_recipe_without_dice", DecorTests::standingMirrorRecipe);
        test("standing_mirror_is_veiled_by_water_and_glass", DecorTests::standingMirrorVeiled);
    }

    private static void use(GameTestHelper helper, ServerPlayer player, BlockPos pos, Direction face, ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos at = helper.absolutePos(pos);
        Vec3 hit = Vec3.atCenterOf(at).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        player.gameMode.useItemOn(player, helper.getLevel(), held, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, at, false));
    }

    /**
     * A sneaking empty hand puts a lantern out - hanging, it stays hanging - and the unlit one gives no light; flint and
     * steel lights it again (a use of its durability), a fire charge too (spent). Without sneaking nothing happens, and
     * an unlit lantern broken gives itself.
     */
    private static void lanternOutAndLit(GameTestHelper helper) {
        BlockPos ceiling = new BlockPos(1, 4, 1);
        BlockPos hanging = ceiling.below();
        BlockPos floor = new BlockPos(3, 1, 1);
        BlockPos standing = floor.above();
        helper.setBlock(ceiling, Blocks.STONE);
        helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(hanging, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        helper.setBlock(standing, Blocks.SOUL_LANTERN.defaultBlockState());
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 near = helper.absoluteVec(new Vec3(2.5, 2, 3.5));
        player.moveTo(near.x, near.y, near.z);

        use(helper, player, hanging, Direction.NORTH, ItemStack.EMPTY);
        check(helper, helper.getBlockState(hanging).is(Blocks.LANTERN), "without sneaking it stays lit");
        player.setShiftKeyDown(true);
        use(helper, player, hanging, Direction.NORTH, ItemStack.EMPTY);
        use(helper, player, standing, Direction.NORTH, ItemStack.EMPTY);
        BlockState out = helper.getBlockState(hanging);
        check(helper, out.is(ModBlocks.UNLIT_LANTERN.get()) && out.getValue(LanternBlock.HANGING), "it goes out, still hanging: " + out);
        check(helper, helper.getBlockState(standing).is(ModBlocks.UNLIT_SOUL_LANTERN.get())
                && !helper.getBlockState(standing).getValue(LanternBlock.HANGING), "the soul lantern too, standing");
        check(helper, out.getLightEmission() == 0 && helper.getBlockState(standing).getLightEmission() == 0, "no light");
        // the world's light is worked out after the tick, and a lantern in a test beside this one may light the spot a
        // little: a few ticks on, it no longer has a lantern's own full light (r15 fix check: seen still lit at times)
        helper.runAfterDelay(5, () -> {
            int light = helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(hanging));
            check(helper, light < Blocks.LANTERN.defaultBlockState().getLightEmission(), "the light is gone from the world too: " + light);

            player.setShiftKeyDown(false);
            ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
            use(helper, player, hanging, Direction.NORTH, flint);
            check(helper, helper.getBlockState(hanging).is(Blocks.LANTERN) && helper.getBlockState(hanging).getValue(LanternBlock.HANGING),
                    "flint and steel lights it again, hanging");
            check(helper, flint.getDamageValue() == 1, "a use of the flint and steel: " + flint.getDamageValue());
            ItemStack charges = new ItemStack(Items.FIRE_CHARGE, 2);
            use(helper, player, standing, Direction.NORTH, charges);
            check(helper, helper.getBlockState(standing).is(Blocks.SOUL_LANTERN) && charges.getCount() == 1, "a fire charge lights it, spent");

            BlockState unlit = ModBlocks.UNLIT_LANTERN.get().defaultBlockState();
            check(helper, Block.getDrops(unlit, helper.getLevel(), helper.absolutePos(standing), null).stream()
                    .anyMatch(s -> s.is(ModItems.UNLIT_LANTERN.get())), "broken, it gives itself");
            helper.succeed();
        });
    }

    /**
     * Sneaking, books are laid on a block's top, three in a pile, each keeping itself (an enchanted book its enchantment,
     * a written book its title), the cover shown by kind; a fourth is not taken, nor anything not a book. An empty hand
     * takes the top one back; broken, the pile drops what is left; its support gone, it goes.
     */
    private static void booksLaidDown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos desk = new BlockPos(1, 1, 1);
        BlockPos at = desk.above();
        helper.setBlock(desk, Blocks.STONE);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 near = helper.absoluteVec(new Vec3(1.5, 2, 3.5));
        player.moveTo(near.x, near.y, near.z);
        var block = ModBlocks.PLACED_BOOKS.get();

        use(helper, player, desk, Direction.UP, new ItemStack(Items.BOOK));
        check(helper, helper.getBlockState(at).isAir(), "not without sneaking");
        player.setShiftKeyDown(true);
        ItemStack books = new ItemStack(Items.BOOK, 3);
        use(helper, player, desk, Direction.UP, books);
        check(helper, helper.getBlockState(at).is(block) && books.getCount() == 2, "laid on top");

        ItemStack enchanted = new ItemStack(Items.ENCHANTED_BOOK);
        var sharpness = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS);
        var stored = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        stored.set(sharpness, 3);
        enchanted.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        ItemStack written = new ItemStack(Items.WRITTEN_BOOK);
        written.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Field notes"));
        use(helper, player, at, Direction.NORTH, enchanted);
        use(helper, player, desk, Direction.UP, written);
        BlockState four = helper.getBlockState(at);
        check(helper, four.getValue(PlacedBooksBlock.BOOKS) == PlacedBooksBlock.MAX && enchanted.isEmpty() && written.isEmpty(),
                "three in a pile: " + four);
        var pile = (PlacedBooksBlockEntity) level.getBlockEntity(helper.absolutePos(at));
        check(helper, PlacedBooksBlock.Kind.of(pile.book(0)) == PlacedBooksBlock.Kind.PLAIN
                && PlacedBooksBlock.Kind.of(pile.book(1)) == PlacedBooksBlock.Kind.ENCHANTED
                && PlacedBooksBlock.Kind.of(pile.book(2)) == PlacedBooksBlock.Kind.WRITTEN
                && PlacedBooksBlock.Kind.of(new ItemStack(Items.WRITABLE_BOOK)) == PlacedBooksBlock.Kind.WRITABLE, "each cover shows what it is");
        for (int i = 0; i < PlacedBooksBlock.MAX; i++) {
            check(helper, Math.abs(pile.dx(i)) <= PlacedBooksBlockEntity.REACH && Math.abs(pile.dz(i)) <= PlacedBooksBlockEntity.REACH
                    && pile.dx(i) * 2 == Math.round(pile.dx(i) * 2), "each lies within reach of the middle, on half pixels: " + pile.dx(i));
        }
        use(helper, player, desk, Direction.UP, books);
        check(helper, books.getCount() == 2, "no fourth");
        check(helper, PlacedBooksBlock.placeFor(level, helper.absolutePos(desk), Direction.UP, new ItemStack(Items.PAPER)) == null,
                "only books");

        check(helper, pile.book(1).get(DataComponents.STORED_ENCHANTMENTS).getLevel(sharpness) == 3, "the enchanted book keeps its enchantment");
        check(helper, pile.book(2).getHoverName().getString().equals("Field notes"), "the written one its name");
        check(helper, four.getBlock().getCloneItemStack(level, helper.absolutePos(at), four).is(Items.WRITTEN_BOOK), "the pick key gives the top one");

        player.setShiftKeyDown(false);
        player.getInventory().clearContent();
        use(helper, player, at, Direction.NORTH, ItemStack.EMPTY);
        check(helper, player.getInventory().countItem(Items.WRITTEN_BOOK) == 1
                && helper.getBlockState(at).getValue(PlacedBooksBlock.BOOKS) == 2, "an empty hand takes the top one back, as it was");
        // saved and loaded, each lies as it did
        var saved = pile.saveWithoutMetadata(level.registryAccess());
        BlockPos other = new BlockPos(4, 1, 4);
        helper.setBlock(other.below(), Blocks.STONE);
        helper.setBlock(other, ModBlocks.PLACED_BOOKS.get());
        var copy = (PlacedBooksBlockEntity) level.getBlockEntity(helper.absolutePos(other));
        ValueIO.loadWithComponents(copy, dev.nookworks.port.storage.TagValueInput.create(dev.nookworks.port.storage.ProblemReporter.DISCARDING,
                level.registryAccess(), saved));
        check(helper, copy.count() == 2 && copy.yaw(1) == pile.yaw(1) && copy.dz(1) == pile.dz(1), "saved, they lie as they did");
        helper.setBlock(other, Blocks.AIR);
        helper.getEntities(EntityType.ITEM, other, 2).forEach(e -> e.discard());

        level.destroyBlock(helper.absolutePos(at), true);
        List<ItemEntity> dropped = helper.getEntities(EntityType.ITEM, at, 2);
        check(helper, dropped.size() == 2 && dropped.stream().anyMatch(e -> e.getItem().is(Items.ENCHANTED_BOOK)
                && e.getItem().get(DataComponents.STORED_ENCHANTMENTS).getLevel(sharpness) == 3),
                "broken, the two drop as they were, nothing more: " + dropped.stream().map(ItemEntity::getItem).toList());
        dropped.forEach(e -> e.discard());

        player.setShiftKeyDown(true);
        use(helper, player, desk, Direction.UP, new ItemStack(Items.BOOK));
        helper.setBlock(desk, Blocks.AIR);
        helper.runAfterDelay(2, () -> { // it falls a tick later
            check(helper, helper.getBlockState(at).isAir(), "its support gone, it goes");
            check(helper, helper.getEntities(EntityType.ITEM, at, 2).stream().anyMatch(e -> e.getItem().is(Items.BOOK)), "and its book drops");
            helper.succeed();
        });
    }

    /**
     * A Bookend set down on a block's top faces the player, its upright on the half of the block clicked (left, as they
     * see it); books used on it stand in it, sneaking or not (a written book is stood, not opened), nothing but books,
     * five to the block, then on in a Row of Books in the block past it while there is floor; a book used anywhere on
     * the row goes to its end, and an empty hand takes the end's last one back as it was (a row's block left empty
     * goes). The bookend gone, the row past it falls; its floor gone, it drops itself and its books.
     */
    private static void bookendHoldsBooks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = new BlockPos(1, 1, 1);
        BlockPos at = floor.above();
        BlockPos next = at.east(); // the row runs east: spines south, the upright to the west
        BlockPos floor2 = new BlockPos(1, 1, 3);
        helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(next.below(), Blocks.STONE);
        helper.setBlock(floor2, Blocks.STONE);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 near = helper.absoluteVec(new Vec3(2.5, 2, 5.5));
        player.moveTo(near.x, near.y, near.z, 180.0F, 30.0F); // looking north: west is their left
        var bookend = ModBlocks.BOOKEND.get();
        var row = ModBlocks.BOOK_ROW.get();

        placeAt(helper, player, floor, -0.3, new ItemStack(ModItems.BOOKEND.get()));
        BlockState state = helper.getBlockState(at);
        check(helper, state.is(bookend) && state.getValue(BookendBlock.FACING) == Direction.SOUTH
                && state.getValue(BookendBlock.HINGE) == DoorHingeSide.LEFT, "facing the player, upright on the left half clicked: " + state);
        placeAt(helper, player, floor2, 0.3, new ItemStack(ModItems.BOOKEND.get()));
        check(helper, helper.getBlockState(floor2.above()).getValue(BookendBlock.HINGE) == DoorHingeSide.RIGHT, "or the right");

        ItemStack written = new ItemStack(Items.WRITTEN_BOOK);
        use(helper, player, at, Direction.SOUTH, written);
        check(helper, written.isEmpty(), "a written book stood in it, not opened");
        use(helper, player, at, Direction.SOUTH, new ItemStack(Items.PAPER));
        var books = (BookendBlockEntity) level.getBlockEntity(helper.absolutePos(at));
        check(helper, books.count() == 1, "nothing but books");
        ItemStack plain = new ItemStack(Items.BOOK, 2);
        use(helper, player, at, Direction.UP, plain);
        use(helper, player, at, Direction.WEST, plain);
        player.setShiftKeyDown(true);
        ItemStack enchanted = new ItemStack(Items.ENCHANTED_BOOK);
        enchanted.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Field notes"));
        use(helper, player, at, Direction.SOUTH, enchanted);
        ItemStack more = new ItemStack(Items.WRITABLE_BOOK, 9);
        use(helper, player, at, Direction.SOUTH, more);
        check(helper, books.count() == BookendBlock.MAX && helper.getBlockState(at).getValue(BookendBlock.BOOKS) == 5
                && helper.getBlockState(next).isAir(), "sneaking too, five to the block: " + books.count());
        check(helper, PlacedBooksBlock.Kind.of(books.book(0)) == PlacedBooksBlock.Kind.WRITTEN
                && "Field notes".equals(books.book(3).getHoverName().getString()), "each as it was, in order");

        use(helper, player, at, Direction.SOUTH, more);
        check(helper, helper.getBlockState(next).is(row) && helper.getBlockState(next).getValue(BookendBlock.FACING) == Direction.SOUTH,
                "the sixth stands in the block past it, a row going on: " + helper.getBlockState(next));
        player.setShiftKeyDown(false);
        for (int i = 0; i < 5; i++) {
            use(helper, player, i % 2 == 0 ? at : next, Direction.SOUTH, more);
        }
        var rowBooks = (BookendBlockEntity) level.getBlockEntity(helper.absolutePos(next));
        check(helper, rowBooks.count() == BookendBlock.MAX && more.getCount() == 3 && helper.getBlockState(next.east()).isAir(),
                "from either block to the row's end, five to it, and not past the floor: " + rowBooks.count() + ", " + more.getCount());

        use(helper, player, at, Direction.SOUTH, ItemStack.EMPTY);
        check(helper, rowBooks.count() == 4 && books.count() == 5 && player.getInventory().contains(s -> s.is(Items.WRITABLE_BOOK)),
                "an empty hand takes the row's last one back");
        for (int i = 0; i < 4; i++) {
            use(helper, player, next, Direction.SOUTH, ItemStack.EMPTY);
        }
        check(helper, helper.getBlockState(next).isAir() && books.count() == 5, "a row's block left empty goes");
        use(helper, player, at, Direction.SOUTH, ItemStack.EMPTY);
        use(helper, player, at, Direction.SOUTH, ItemStack.EMPTY);
        check(helper, player.getInventory().contains(s -> "Field notes".equals(s.getHoverName().getString())), "as it was");

        for (int i = 0; i < 4; i++) {
            use(helper, player, at, Direction.SOUTH, more.isEmpty() ? new ItemStack(Items.BOOK) : more);
        }
        check(helper, helper.getBlockState(next).is(row), "a row again");
        helper.setBlock(floor, Blocks.AIR);
        helper.runAfterDelay(4, () -> { // a tick for the bookend, another for the row after it
            check(helper, helper.getBlockState(at).isAir() && helper.getBlockState(next).isAir(), "its floor gone, it goes, and the row past it");
            List<ItemEntity> dropped = helper.getEntities(EntityType.ITEM, at, 3);
            check(helper, dropped.stream().filter(e -> e.getItem().is(ModItems.BOOKEND.get())).count() == 1
                    && dropped.stream().filter(e -> e.getItem().is(PlacedBooksBlock.PLACEABLE)).mapToInt(e -> e.getItem().getCount()).sum() == 7,
                    "and drops itself and its books, the row's too: " + dropped.stream().map(e -> e.getItem().toString()).toList());
            helper.succeed();
        });
    }

    /** Uses {@code held} on {@code pos}'s top, {@code across} blocks east of its middle. */
    private static void placeAt(GameTestHelper helper, ServerPlayer player, BlockPos pos, double across, ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos abs = helper.absolutePos(pos);
        Vec3 hit = Vec3.atCenterOf(abs).add(across, 0.5, 0.0);
        player.gameMode.useItemOn(player, helper.getLevel(), held, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, abs, false));
    }

    /**
     * Sneaking, a book used on a wall's side stands in front of it, leant against it, facing out; three at most, a book
     * kept as it was; the wall taken away, they fall. Not where there is no floor.
     */
    private static void booksLeantAgainstAWall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos wall = new BlockPos(2, 2, 1);
        BlockPos at = wall.south();
        helper.setBlock(wall.below(), Blocks.STONE);
        helper.setBlock(at.below(), Blocks.STONE);
        helper.setBlock(wall, Blocks.STONE);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 near = helper.absoluteVec(new Vec3(2.5, 2, 4.5));
        player.moveTo(near.x, near.y, near.z);
        player.setShiftKeyDown(true);
        ItemStack books = new ItemStack(Items.BOOK, 4);
        use(helper, player, wall, Direction.SOUTH, books);
        BlockState stand = helper.getBlockState(at);
        check(helper, stand.is(ModBlocks.PLACED_BOOKS.get()) && stand.getValue(PlacedBooksBlock.STANDING)
                && stand.getValue(PlacedBooksBlock.FACING) == Direction.SOUTH && books.getCount() == 3, "leant against the wall, facing out: " + stand);
        use(helper, player, at, Direction.SOUTH, books);
        use(helper, player, wall, Direction.SOUTH, books);
        use(helper, player, at, Direction.UP, books);
        check(helper, helper.getBlockState(at).getValue(PlacedBooksBlock.BOOKS) == PlacedBooksBlock.MAX && books.getCount() == 1,
                "three at most, on the books or the wall: " + helper.getBlockState(at));
        var pile = (PlacedBooksBlockEntity) level.getBlockEntity(helper.absolutePos(at));
        check(helper, pile.lean() >= 12 && pile.lean() <= 18, "leant back twelve to eighteen degrees: " + pile.lean());
        BlockPos air = new BlockPos(5, 2, 1);
        helper.setBlock(air, Blocks.STONE);
        check(helper, PlacedBooksBlock.placeFor(level, helper.absolutePos(air), Direction.SOUTH, new ItemStack(Items.BOOK)) == null,
                "not where there is no floor");
        helper.setBlock(wall, Blocks.AIR);
        helper.runAfterDelay(2, () -> { // a tick later
            check(helper, helper.getBlockState(at).isAir(), "the wall gone, they fall");
            check(helper, helper.getEntities(EntityType.ITEM, at, 2).stream().mapToInt(e -> e.getItem().getCount()).sum() == 3,
                    "and the three drop");
            helper.succeed();
        });
    }

    /**
     * Sneaking, a bottle goes down on top of a block, and more beside it up to four, each as it was (a potion keeps its
     * potion) and coloured by what is in it; not without sneaking, nor on a block's side, nor into water (so a glass
     * bottle aimed at a river bed still fills). An empty hand takes back the last one set down, the pick key gives it,
     * and broken - its support gone, or washed off by water - they all drop.
     */
    private static void bottlesSetDown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos table = new BlockPos(1, 1, 1);
        BlockPos at = table.above();
        helper.setBlock(table, Blocks.STONE);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 near = helper.absoluteVec(new Vec3(1.5, 2, 3.5));
        player.moveTo(near.x, near.y, near.z);
        PlacedBottlesBlock block = ModBlocks.PLACED_BOTTLES.get();

        ItemStack honey = new ItemStack(Items.HONEY_BOTTLE, 3);
        use(helper, player, table, Direction.UP, honey);
        check(helper, helper.getBlockState(at).isAir() && honey.getCount() == 3, "not without sneaking");
        player.setShiftKeyDown(true);
        use(helper, player, table, Direction.NORTH, honey);
        check(helper, helper.getBlockState(at).isAir() && honey.getCount() == 3, "not on a block's side");
        use(helper, player, table, Direction.UP, honey);
        check(helper, helper.getBlockState(at).is(block) && honey.getCount() == 2, "set down on top");

        ItemStack healing = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
        use(helper, player, at, Direction.NORTH, healing);
        use(helper, player, table, Direction.UP, new ItemStack(Items.GLASS_BOTTLE));
        use(helper, player, table, Direction.UP, new ItemStack(ModOil.VEGETABLE_OIL_BOTTLE.get()));
        BlockState four = helper.getBlockState(at);
        check(helper, healing.isEmpty() && four.getValue(PlacedBottlesBlock.BOTTLES) == 4, "four beside it, on the bottles or the block under them");
        check(helper, four.getValue(PlacedBottlesBlock.FILLED.get(0)) && four.getValue(PlacedBottlesBlock.FILLED.get(1))
                && !four.getValue(PlacedBottlesBlock.FILLED.get(2)) && four.getValue(PlacedBottlesBlock.FILLED.get(3)), "the empty one shows empty: " + four);
        use(helper, player, table, Direction.UP, honey);
        check(helper, honey.getCount() == 2, "no fifth");

        var bottles = (PlacedBottlesBlockEntity) level.getBlockEntity(helper.absolutePos(at));
        check(helper, bottles.bottle(1).is(Items.POTION) && bottles.bottle(1).get(DataComponents.POTION_CONTENTS).is(Potions.HEALING),
                "a potion stays that potion");
        check(helper, BottleLook.liquid(bottles.bottle(0)) == 0xFFF0A42C, "honey's colour: " + Integer.toHexString(BottleLook.liquid(bottles.bottle(0))));
        check(helper, BottleLook.liquid(bottles.bottle(1)) == (PotionContents.createItemStack(Items.POTION, Potions.HEALING)
                .get(DataComponents.POTION_CONTENTS).getColor() | 0xFF000000), "a potion its own colour");
        check(helper, BottleLook.liquid(bottles.bottle(2)) == -1, "a glass bottle is empty");
        check(helper, BottleLook.of(new ItemStack(Items.MILK_BUCKET)) == null && BottleLook.of(new ItemStack(Items.WATER_BUCKET)) == null,
                "only bottles");
        check(helper, four.getBlock().getCloneItemStack(level, helper.absolutePos(at), four).is(ModOil.VEGETABLE_OIL_BOTTLE.get()), "the pick key gives the last one");

        player.setShiftKeyDown(false);
        player.getInventory().clearContent();
        use(helper, player, at, Direction.NORTH, ItemStack.EMPTY);
        check(helper, player.getInventory().countItem(ModOil.VEGETABLE_OIL_BOTTLE.get()) == 1
                && helper.getBlockState(at).getValue(PlacedBottlesBlock.BOTTLES) == 3, "an empty hand takes the last one back");
        check(helper, player.getMainHandItem().isEmpty(), "into the inventory, the hand left empty");

        // water: a glass bottle aimed at the bed under it is left to fill
        BlockPos bed = new BlockPos(4, 1, 1);
        helper.setBlock(bed, Blocks.STONE);
        helper.setBlock(bed.above(), Blocks.WATER);
        check(helper, PlacedBottlesBlock.placeFor(level, helper.absolutePos(bed), Direction.UP, new ItemStack(Items.GLASS_BOTTLE)) == null,
                "not into water");

        level.destroyBlock(helper.absolutePos(at), true);
        List<ItemEntity> dropped = helper.getEntities(EntityType.ITEM, at, 2);
        check(helper, dropped.stream().filter(e -> e.getItem().is(Items.HONEY_BOTTLE)).count() == 1
                && dropped.stream().anyMatch(e -> e.getItem().is(Items.POTION)) && dropped.stream().anyMatch(e -> e.getItem().is(Items.GLASS_BOTTLE))
                && dropped.size() == 3, "broken, the three drop, nothing more: " + dropped.stream().map(ItemEntity::getItem).toList());
        dropped.forEach(e -> e.discard());

        player.setShiftKeyDown(true);
        use(helper, player, table, Direction.UP, new ItemStack(Items.GLASS_BOTTLE));
        helper.setBlock(table, Blocks.AIR);
        check(helper, helper.getBlockState(at).isAir(), "its support gone, it goes");
        check(helper, helper.getEntities(EntityType.ITEM, at, 2).stream().anyMatch(e -> e.getItem().is(Items.GLASS_BOTTLE)), "and its bottle drops");

        // water poured on them (from beside, it runs for the nearest drop, which in the arena is not here)
        BlockPos shore = new BlockPos(1, 1, 4);
        helper.setBlock(shore, Blocks.STONE);
        use(helper, player, shore, Direction.UP, new ItemStack(Items.HONEY_BOTTLE));
        check(helper, helper.getBlockState(shore.above()).is(block), "set down by the shore");
        helper.setBlock(shore.above(2), Blocks.WATER);
        helper.succeedWhen(() -> {
            check(helper, helper.getBlockState(shore.above()).is(Blocks.WATER), "water washes it off");
            check(helper, helper.getEntities(EntityType.ITEM, shore.above(), 4).stream().anyMatch(e -> e.getItem().is(Items.HONEY_BOTTLE)),
                    "and the bottle drops");
        });
    }

    private DecorTests() {}

    /**
     * A Standing Mirror set down stands two blocks high, its glass to whoever set it down; an empty hand on the glass
     * takes nobody anywhere; broken from its upper half in survival, both halves go and one mirror drops.
     */
    private static void standingMirrorStandsWhole(GameTestHelper helper) {
        BlockPos floor = new BlockPos(2, 1, 2);
        BlockPos at = floor.above();
        helper.setBlock(floor, Blocks.STONE);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 standing = helper.absoluteVec(new Vec3(2.5, 2, 5.5));
        player.moveTo(standing.x, standing.y, standing.z, 180.0F, 30.0F); // south of it, looking north
        use(helper, player, floor, Direction.UP, new ItemStack(ModItems.STANDING_MIRROR.get()));
        BlockState lower = helper.getBlockState(at);
        BlockState upper = helper.getBlockState(at.above());
        check(helper, lower.is(ModBlocks.STANDING_MIRROR.get()) && lower.getValue(ChevalMirrorBlock.HALF) == DoubleBlockHalf.LOWER,
                "the lower half stands on the floor: " + lower);
        check(helper, upper.is(ModBlocks.STANDING_MIRROR.get()) && upper.getValue(ChevalMirrorBlock.HALF) == DoubleBlockHalf.UPPER,
                "the upper half above it: " + upper);
        check(helper, lower.getValue(ChevalMirrorBlock.FACING) == Direction.SOUTH, "its glass faces whoever set it down: " + lower);
        check(helper, helper.getLevel().getBlockEntity(helper.absolutePos(at)) instanceof StandingMirrorBlockEntity,
                "the lower half has the block entity the glass is drawn from");
        Vec3 before = player.position();
        use(helper, player, at.above(), Direction.SOUTH, ItemStack.EMPTY);
        check(helper, player.level() == helper.getLevel() && player.position().equals(before), "an empty hand on the glass goes nowhere");
        player.gameMode.destroyBlock(helper.absolutePos(at.above()));
        helper.succeedWhen(() -> {
            check(helper, helper.getBlockState(at).isAir() && helper.getBlockState(at.above()).isAir(), "both halves go");
            int dropped = helper.getEntities(EntityType.ITEM).stream().filter(item -> item.getItem().is(ModItems.STANDING_MIRROR.get()))
                    .mapToInt(item -> item.getItem().getCount()).sum();
            check(helper, dropped == 1, "one mirror drops: " + dropped);
        });
    }

    /**
     * What the client asks before it draws a mirror's glass under a shader pack: water or glass between the eye and the
     * glass veils it (the glass could not be painted behind them, and the board the pack's light is measured on showed
     * through - the user's mirror seen from under water, 2026-11-12); a wall of stone does not (the depth hides the
     * glass there), nor glass that stands aside, nor nothing at all.
     */
    private static void standingMirrorVeiled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(2, 2, 1);
        helper.setBlock(at.below(), Blocks.STONE);
        BlockPos lower = helper.absolutePos(at);
        BlockPos ahead = helper.absolutePos(at.south());
        Direction facing = Direction.getNearest(ahead.getX() - lower.getX(), 0, ahead.getZ() - lower.getZ());
        BlockState mirror = ModBlocks.STANDING_MIRROR.get().defaultBlockState().setValue(ChevalMirrorBlock.FACING, facing);
        level.setBlock(lower, mirror, Block.UPDATE_CLIENTS);
        level.setBlock(lower.above(), mirror.setValue(ChevalMirrorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
        Vec3 out = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 eye = MirrorSight.glassCentre(lower, facing).add(out.scale(3.4)).add(0, 0.3, 0);
        java.util.function.Predicate<BlockState> glassy = state -> state.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS)
                || state.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_PANES);
        BlockPos between = lower.relative(facing, 2);
        BlockPos aside = between.relative(facing.getClockWise(), 2);
        check(helper, !MirrorSight.veiled(level, lower, facing, eye, glassy), "nothing before the glass: it is drawn");
        record Case(Block block, BlockPos where, boolean veils, String what) {}
        for (Case c : List.of(
                new Case(Blocks.STONE, between, false, "a wall of stone before it is the depth's to hide"),
                new Case(Blocks.GLASS, between, true, "a window before it veils it"),
                new Case(Blocks.GLASS_PANE, between, true, "a pane before it veils it"),
                new Case(Blocks.WATER, between, true, "water before it veils it"),
                new Case(Blocks.GLASS, aside, false, "glass that stands aside does not"),
                new Case(Blocks.WATER, BlockPos.containing(eye), true, "an eye in water sees it veiled"))) {
            level.setBlock(c.where(), c.block().defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(c.where().above(), c.block().defaultBlockState(), Block.UPDATE_CLIENTS);
            boolean veiled = MirrorSight.veiled(level, lower, facing, eye, glassy);
            level.setBlock(c.where().above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(c.where(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            check(helper, veiled == c.veils(), c.what() + ": " + veiled);
        }
        helper.succeed();
    }

    /** The If Mirror's recipe with no Die: Dawnwood planks round a glass pane, a Mercury Bucket below. With a Die, the If Mirror. */
    private static void standingMirrorRecipe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack planks = new ItemStack(ModWoods.DAWNWOOD_PLANKS.get());
        ItemStack pane = new ItemStack(Items.GLASS_PANE);
        ItemStack mercury = new ItemStack(ModItems.MERCURY_BUCKET.get());
        for (ItemStack top : List.of(ItemStack.EMPTY, new ItemStack(ModItems.DICE.get()))) {
            CraftingInput grid = CraftingInput.of(3, 3, List.of(planks, top, planks, planks, pane, planks, planks, mercury, planks));
            ItemStack made = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level)
                    .map(holder -> holder.value().assemble(grid, helper.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
            var wanted = top.isEmpty() ? ModItems.STANDING_MIRROR.get() : ModTravel.IF_MIRROR_ITEM.get();
            check(helper, made.is(wanted), (top.isEmpty() ? "no Die" : "a Die") + " in the top's middle makes " + wanted + ": " + made);
        }
        helper.succeed();
    }
}
