package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import dev.nookworks.decor.BookendBlock;
import dev.nookworks.decor.BookendBlockEntity;
import dev.nookworks.decor.PlacedBooksBlock;
import dev.nookworks.decor.PlacedBooksBlockEntity;
import dev.nookworks.recycling.Compressing;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.rope.HungItemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/** The r18 review's findings (Codex code and play, and the night's own look), each kept fixed. */
final class R18FixTests {
    private R18FixTests() {}

    static void register() {
        test("bookend_row_end_in_a_claim_is_asked_of_the_end", R18FixTests::rowEndInAClaim);
        test("adventure_player_changes_no_books_set_out", R18FixTests::adventureRow);
        test("compressor_takes_no_recipe_that_leaves_containers", R18FixTests::compressorRemainders);
        test("pile_of_books_turns_and_mirrors_with_a_structure", R18FixTests::pileTurns);
        test("shared_hung_light_shines_as_the_brightest", 120, R18FixTests::sharedLight);
        test("long_row_of_books_falls_whole", 900, R18FixTests::longRow);
        test("books_set_down_before_their_support_wait_for_it", 40, R18FixTests::supportLater);
        test("placed_books_send_clients_no_pages", R18FixTests::noPages);
        test("book_rows_and_leant_books_are_walls_to_mobs", R18FixTests::mobsWalls);
    }

    private static void use(GameTestHelper helper, ServerPlayer player, BlockPos abs, ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        player.gameMode.useItemOn(player, helper.getLevel(), held, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.SOUTH, abs, false));
    }

    /** A full Bookend facing south at {@code rel} (its row running east), floored three blocks on, and {@code more} books past it. */
    private static BlockPos row(GameTestHelper helper, BlockPos rel, int more) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(rel);
        for (int x = 0; x < 3; x++) {
            level.setBlockAndUpdate(at.east(x).below(), Blocks.STONE.defaultBlockState());
        }
        level.setBlockAndUpdate(at, ModBlocks.BOOKEND.get().defaultBlockState().setValue(BookendBlock.FACING, Direction.SOUTH)
                .setValue(BookendBlock.BOOKS, BookendBlock.MAX));
        BookendBlockEntity books = (BookendBlockEntity) level.getBlockEntity(at);
        for (int i = 0; i < BookendBlock.MAX; i++) {
            books.add(new ItemStack(Items.BOOK));
        }
        if (more > 0) {
            level.setBlockAndUpdate(at.east(), ModBlocks.BOOK_ROW.get().defaultBlockState().setValue(BookendBlock.FACING, Direction.SOUTH)
                    .setValue(BookendBlock.BOOKS, more));
            BookendBlockEntity rowBooks = (BookendBlockEntity) level.getBlockEntity(at.east());
            for (int i = 0; i < more; i++) {
                rowBooks.add(new ItemStack(Items.ENCHANTED_BOOK));
            }
        }
        return at;
    }

    /**
     * A row from open land into a claim: used at its bookend, outside, it gives nothing from the claimed end nor adds to
     * it - asked of the end, as breaking it is; with no claim there, it does both.
     */
    private static void rowEndInAClaim(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = row(helper, new BlockPos(1, 2, 1), 2);
        BlockPos end = at.east();
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.snapTo(Vec3.atCenterOf(at).add(0, 0, 2));
        UUID owner = UUID.randomUUID();
        Consumer<BreakBlockEvent> claim = event -> {
            if (event.getPos().equals(end) && (event.getPlayer() == null || !owner.equals(event.getPlayer().getUUID()))) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(BreakBlockEvent.class, claim);
        AfterTest.undo(helper, () -> NeoForge.EVENT_BUS.unregister(claim));
        BookendBlockEntity books = (BookendBlockEntity) level.getBlockEntity(end);
        use(helper, player, at, ItemStack.EMPTY);
        check(helper, books.count() == 2 && player.getInventory().countItem(Items.ENCHANTED_BOOK) == 0, "nothing taken from the claimed end");
        ItemStack book = new ItemStack(Items.BOOK);
        use(helper, player, at, book);
        check(helper, books.count() == 2 && book.getCount() == 1, "nor anything added to it");
        NeoForge.EVENT_BUS.unregister(claim);
        use(helper, player, at, ItemStack.EMPTY);
        check(helper, books.count() == 1 && player.getInventory().countItem(Items.ENCHANTED_BOOK) == 1, "with no claim, taken");
        use(helper, player, at, new ItemStack(Items.BOOK));
        check(helper, books.count() == 2, "and added to");
        helper.succeed();
    }

    /** In adventure mode, a book used on a full bookend puts down no new block of its row; in survival it does. */
    private static void adventureRow(GameTestHelper helper) {
        BlockPos at = row(helper, new BlockPos(1, 2, 1), 0);
        ServerPlayer adventurer = MockPlayers.inLevel(helper, GameType.ADVENTURE);
        adventurer.snapTo(Vec3.atCenterOf(at).add(0, 0, 2));
        ItemStack book = new ItemStack(Items.BOOK);
        use(helper, adventurer, at, book);
        check(helper, helper.getLevel().getBlockState(at.east()).isAir() && book.getCount() == 1, "no new block in adventure mode");
        // books set out are decoration in adventure mode: none taken from a bookend or a pile (the user, 2026-10-20)
        BookendBlockEntity held = (BookendBlockEntity) helper.getLevel().getBlockEntity(at);
        use(helper, adventurer, at, ItemStack.EMPTY);
        check(helper, held.count() == BookendBlock.MAX && adventurer.getInventory().countItem(Items.BOOK) == 0, "none taken from the bookend");
        BlockPos pileAt = helper.absolutePos(new BlockPos(1, 2, 5));
        helper.getLevel().setBlockAndUpdate(pileAt.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pileAt, ModBlocks.PLACED_BOOKS.get().defaultBlockState());
        PlacedBooksBlockEntity pile = (PlacedBooksBlockEntity) helper.getLevel().getBlockEntity(pileAt);
        pile.add(new ItemStack(Items.BOOK), 0.0F, false, helper.getLevel().getRandom());
        adventurer.snapTo(Vec3.atCenterOf(pileAt).add(0, 0, 2));
        use(helper, adventurer, pileAt, ItemStack.EMPTY);
        check(helper, pile.count() == 1 && adventurer.getInventory().countItem(Items.BOOK) == 0, "nor from a pile");
        ServerPlayer builder = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        builder.snapTo(Vec3.atCenterOf(at).add(0, 0, 2));
        use(helper, builder, at, new ItemStack(Items.BOOK));
        check(helper, helper.getLevel().getBlockState(at.east()).is(ModBlocks.BOOK_ROW.get()), "one in survival");
        helper.succeed();
    }

    /** Nine honey bottles to an emerald (a pack's recipe) leave nine bottles in the grid: not pressed. Nuggets to an ingot are. */
    private static void compressorRemainders(GameTestHelper helper) {
        CraftingRecipe bottles = shapeless(Items.HONEY_BOTTLE, Items.EMERALD);
        ItemStack honey = new ItemStack(Items.HONEY_BOTTLE);
        CraftingInput nineHoney = CraftingInput.of(3, 3, Collections.nCopies(9, honey));
        check(helper, Compressing.pressed(bottles, nineHoney, honey).isEmpty(), "a recipe leaving its bottles is not pressed");
        CraftingRecipe nuggets = shapeless(Items.IRON_NUGGET, Items.IRON_INGOT);
        ItemStack nugget = new ItemStack(Items.IRON_NUGGET);
        check(helper, Compressing.pressed(nuggets, CraftingInput.of(3, 3, Collections.nCopies(9, nugget)), nugget).is(Items.IRON_INGOT),
                "nuggets are");
        helper.succeed();
    }

    private static CraftingRecipe shapeless(net.minecraft.world.item.Item in, net.minecraft.world.item.Item out) {
        return new ShapelessRecipe(new Recipe.CommonInfo(false), new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
                new ItemStackTemplate(out), Collections.nCopies(9, Ingredient.of(in)));
    }

    /**
     * A pile copied in a structure turned a quarter clockwise lies turned with it - each book's turn a quarter on, its
     * place off the middle turned round; copied mirrored front to back, each lies as in a mirror. Laid in the world, as kept.
     */
    private static void pileTurns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(at.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(at, ModBlocks.PLACED_BOOKS.get().defaultBlockState());
        PlacedBooksBlockEntity pile = (PlacedBooksBlockEntity) level.getBlockEntity(at);
        for (int i = 0; i < 3 && (pile.count() == 0 || Math.abs(pile.dx(0)) + Math.abs(pile.dz(0)) == 0); i++) {
            level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState()); // a book off the middle, so its place shows a turn
            level.setBlockAndUpdate(at, ModBlocks.PLACED_BOOKS.get().defaultBlockState());
            pile = (PlacedBooksBlockEntity) level.getBlockEntity(at);
            pile.add(new ItemStack(Items.BOOK), 70.0F, false, level.getRandom());
        }
        float yaw = pile.yaw(0);
        float dx = pile.dx(0);
        float dz = pile.dz(0);
        float[] here = PlacedBooksBlock.lieInWorld(level.getBlockState(at), yaw, dx, dz);
        check(helper, near(here[0], net.minecraft.util.Mth.wrapDegrees(yaw)) && near(here[1], dx) && near(here[2], dz), "laid in the world, as kept");
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, at.below(), new Vec3i(1, 2, 1), false, List.of());

        BlockPos turnedAt = helper.absolutePos(new BlockPos(4, 1, 4));
        template.placeInWorld(level, turnedAt, turnedAt, new StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_90).setKnownShape(true),
                level.getRandom(), Block.UPDATE_ALL);
        BlockState turned = level.getBlockState(turnedAt.above());
        PlacedBooksBlockEntity copy = (PlacedBooksBlockEntity) level.getBlockEntity(turnedAt.above());
        float[] lie = PlacedBooksBlock.lieInWorld(turned, copy.yaw(0), copy.dx(0), copy.dz(0));
        // a quarter clockwise: north to east, east to south - a step east (+x) is one south (+z), a step north (-z) one east
        check(helper, near(lie[0], net.minecraft.util.Mth.wrapDegrees(yaw - 90)) && near(lie[1], -dz) && near(lie[2], dx),
                "turned a quarter: " + yaw + "/" + dx + "/" + dz + " -> " + lie[0] + "/" + lie[1] + "/" + lie[2]);

        BlockPos mirroredAt = helper.absolutePos(new BlockPos(7, 1, 1));
        template.placeInWorld(level, mirroredAt, mirroredAt, new StructurePlaceSettings().setMirror(Mirror.FRONT_BACK).setKnownShape(true),
                level.getRandom(), Block.UPDATE_ALL);
        BlockState mirrored = level.getBlockState(mirroredAt.above());
        PlacedBooksBlockEntity image = (PlacedBooksBlockEntity) level.getBlockEntity(mirroredAt.above());
        float[] seen = PlacedBooksBlock.lieInWorld(mirrored, image.yaw(0), image.dx(0), image.dz(0));
        check(helper, near(seen[0], net.minecraft.util.Mth.wrapDegrees(180 - yaw)) && near(seen[1], -dx) && near(seen[2], dz),
                "mirrored east to west (each book about, its spine on the mirrored side): " + yaw + "/" + dx + "/" + dz + " -> "
                        + seen[0] + "/" + seen[1] + "/" + seen[2]);
        // an empty pile come turned in a structure: its first book lies the player's way all the same
        BlockPos emptyAt = helper.absolutePos(new BlockPos(1, 2, 6));
        level.setBlockAndUpdate(emptyAt.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(emptyAt, ModBlocks.PLACED_BOOKS.get().defaultBlockState().setValue(PlacedBooksBlock.FACING, Direction.EAST)
                .setValue(PlacedBooksBlock.MIRRORED, true));
        PlacedBooksBlockEntity empty = (PlacedBooksBlockEntity) level.getBlockEntity(emptyAt);
        empty.add(new ItemStack(Items.BOOK), 0.0F, false, level.getRandom()); // a player looking south
        float first = PlacedBooksBlock.lieInWorld(level.getBlockState(emptyAt), empty.yaw(0), 0, 0)[0];
        check(helper, Math.abs(net.minecraft.util.Mth.wrapDegrees(first - 180.0F)) <= 18.0F, "the player's way: " + first);
        helper.succeed();
    }

    private static boolean near(float a, float b) {
        return Math.abs(net.minecraft.util.Mth.wrapDegrees(a - b)) < 0.01F || Math.abs(a - b) < 0.01F;
    }

    /**
     * A soul lantern (10) hung first and a lantern (15) beside it in the same block: the light there is 15, whichever came
     * first; the lantern gone, it is the soul lantern's 10 again.
     */
    private static void sharedLight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = RopeTests.player(helper, 20);
        BlockPos[] posts = RopeTests.posts(helper, 2, 4);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        dev.nookworks.rope.RopeSpans.tie(level, player, a, player.getMainHandItem());
        dev.nookworks.rope.RopeSpans.tie(level, player, b, player.getMainHandItem());
        HungItemEntity low = HungItemEntity.onSpan(level, a, b, 0.20, new ItemStack(Items.SOUL_LANTERN));
        HungItemEntity high = HungItemEntity.onSpan(level, a, b, 0.35, new ItemStack(Items.LANTERN));
        check(helper, low.blockPosition().equals(high.blockPosition()), "set up: one block for both");
        level.addFreshEntity(low);
        helper.runAfterDelay(3, () -> {
            level.addFreshEntity(high); // the lantern hung after the soul lantern's light is there
            helper.runAfterDelay(3, () -> {
                BlockPos lit = low.blockPosition();
                BlockState light = level.getBlockState(lit);
                check(helper, light.hasProperty(LightBlock.LEVEL) && light.getValue(LightBlock.LEVEL) == 15, "as bright as the lantern: " + light);
                high.remove(Entity.RemovalReason.KILLED);
                helper.runAfterDelay(30, () -> { // the soul lantern looks again within a second
                    BlockState after = level.getBlockState(lit);
                    check(helper, after.hasProperty(LightBlock.LEVEL) && after.getValue(LightBlock.LEVEL) == 10,
                            "the lantern gone, the soul lantern's: " + after);
                    helper.succeed();
                });
            });
        });
    }

    /** A row of 520 blocks past its bookend: the bookend broken, it comes down whole (512 on, it stood before). */
    private static void longRow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 60, 1)); // high over the other tests it runs east across
        int length = 520;
        // its blocks fall a tick apart: their chunks must tick, which far from a test they do only kept loaded
        for (int cx = at.getX() >> 4; cx <= (at.getX() + length) >> 4; cx++) {
            int chunkX = cx;
            if (level.setChunkForced(chunkX, at.getZ() >> 4, true)) {
                AfterTest.undo(helper, () -> level.setChunkForced(chunkX, at.getZ() >> 4, false));
            }
        }
        BlockState bookend = ModBlocks.BOOKEND.get().defaultBlockState().setValue(BookendBlock.FACING, Direction.SOUTH)
                .setValue(BookendBlock.BOOKS, 1);
        BlockState row = ModBlocks.BOOK_ROW.get().defaultBlockState().setValue(BookendBlock.FACING, Direction.SOUTH).setValue(BookendBlock.BOOKS, 1);
        for (int x = 0; x <= length; x++) {
            level.setBlock(at.east(x).below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.setBlock(at.east(x), x == 0 ? bookend : row, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            ((BookendBlockEntity) level.getBlockEntity(at.east(x))).add(new ItemStack(Items.BOOK));
        }
        AfterTest.undo(helper, () -> { // the line of stone and the books it dropped, out of the other tests' sky
            for (int x = 0; x <= length; x++) {
                level.setBlock(at.east(x), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(at.east(x).below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(at).expandTowards(length, 0, 0).inflate(2)).forEach(Entity::discard);
        });
        level.destroyBlock(at, true);
        helper.succeedWhen(() -> {
            int standing = 0;
            for (int x = 1; x <= length; x++) {
                standing += level.getBlockState(at.east(x)).is(ModBlocks.BOOK_ROW.get()) ? 1 : 0;
            }
            check(helper, standing == 0, "still standing: " + standing);
        });
    }

    /**
     * Set down as a structure is, chunk by chunk, with the shapes worked out at once: a row's block whose bookend is not
     * yet there, and books leant against a wall not yet there, stay - and the bookend and the wall come, they stand; one
     * whose bookend never comes falls.
     */
    private static void supportLater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bookendAt = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos rowAt = bookendAt.east();
        BlockPos lonely = helper.absolutePos(new BlockPos(1, 2, 4));
        BlockPos wall = helper.absolutePos(new BlockPos(5, 2, 1));
        BlockPos leant = wall.south();
        for (BlockPos floor : List.of(bookendAt, rowAt, lonely, lonely.west(), wall, leant)) {
            level.setBlockAndUpdate(floor.below(), Blocks.STONE.defaultBlockState());
        }
        BlockState row = ModBlocks.BOOK_ROW.get().defaultBlockState().setValue(BookendBlock.FACING, Direction.SOUTH).setValue(BookendBlock.BOOKS, 1);
        BlockState lean = ModBlocks.PLACED_BOOKS.get().defaultBlockState().setValue(PlacedBooksBlock.STANDING, true)
                .setValue(PlacedBooksBlock.FACING, Direction.SOUTH);
        for (BlockPos pos : List.of(rowAt, lonely, leant)) {
            level.setBlock(pos, pos.equals(leant) ? lean : row, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            BlockState shaped = Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos);
            check(helper, !shaped.isAir(), "kept as it is set down, its support not yet there: " + pos);
        }
        level.setBlock(bookendAt, ModBlocks.BOOKEND.get().defaultBlockState().setValue(BookendBlock.FACING, Direction.SOUTH),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        helper.runAfterDelay(5, () -> {
            check(helper, level.getBlockState(rowAt).is(ModBlocks.BOOK_ROW.get()), "the bookend come, the row stands");
            check(helper, level.getBlockState(leant).is(ModBlocks.PLACED_BOOKS.get()), "the wall come, the books stand");
            check(helper, level.getBlockState(lonely).isAir(), "with none to come, it falls");
            helper.succeed();
        });
    }

    /** A pile's and a bookend's client data name each book's item, not its pages or a book and quill's draft. */
    private static void noPages(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack written = new ItemStack(Items.WRITTEN_BOOK);
        written.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Diary"), "Someone", 0,
                List.of(Filterable.passThrough(Component.literal("secret page text"))), true));
        BlockPos pileAt = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(pileAt.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pileAt, ModBlocks.PLACED_BOOKS.get().defaultBlockState());
        PlacedBooksBlockEntity pile = (PlacedBooksBlockEntity) level.getBlockEntity(pileAt);
        pile.add(written, 0.0F, false, level.getRandom());
        String sent = pile.getUpdateTag(level.registryAccess()).toString();
        check(helper, sent.contains("written_book") && !sent.contains("secret page text") && !sent.contains("Diary"), "the pile's: " + sent);
        BlockPos at = row(helper, new BlockPos(4, 2, 1), 0);
        BookendBlockEntity books = (BookendBlockEntity) level.getBlockEntity(at);
        books.take();
        books.add(written);
        String sentRow = books.getUpdateTag(level.registryAccess()).toString();
        check(helper, sentRow.contains("written_book") && !sentRow.contains("secret page text"), "the bookend's: " + sentRow);
        check(helper, pile.book(0).has(DataComponents.WRITTEN_BOOK_CONTENT), "the server keeps the book whole");
        helper.succeed();
    }

    /** A Bookend, a Row of Books and books leant against a wall are walls to mobs; a lying pile is stepped over. */
    private static void mobsWalls(GameTestHelper helper) {
        BlockState bookend = ModBlocks.BOOKEND.get().defaultBlockState();
        BlockState row = ModBlocks.BOOK_ROW.get().defaultBlockState().setValue(BookendBlock.BOOKS, 3);
        BlockState lean = ModBlocks.PLACED_BOOKS.get().defaultBlockState().setValue(PlacedBooksBlock.STANDING, true);
        BlockState pile = ModBlocks.PLACED_BOOKS.get().defaultBlockState();
        check(helper, !bookend.isPathfindable(PathComputationType.LAND) && !row.isPathfindable(PathComputationType.LAND)
                && !lean.isPathfindable(PathComputationType.LAND), "the bookend, the row and leant books are walls");
        check(helper, pile.isPathfindable(PathComputationType.LAND), "a pile is walked over");
        helper.succeed();
    }
}
