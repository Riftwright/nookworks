package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.List;

import com.mojang.logging.LogUtils;

import dev.nookworks.photo.Photo;
import dev.nookworks.photo.PhotoPrints;
import dev.nookworks.poster.NookItemFrame;
import dev.nookworks.poster.PaintingFrames;
import dev.nookworks.poster.PictureFrames;
import dev.nookworks.poster.PosterEntity;
import dev.nookworks.registry.ModEntities;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import dev.nookworks.port.test.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import dev.nookworks.port.storage.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariants;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import dev.nookworks.port.storage.TagValueInput;
import dev.nookworks.port.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import dev.nookworks.port.core.EntityClicks;
import dev.nookworks.port.storage.ValueIO;

/**
 * Frames (the user, 2026-11-13: posters framed by vanilla's item frame, and item frames of dark oak and of brass that
 * frame a poster and hang as item frames too; then one frame for any picture, and vanilla's paintings framed too): a
 * poster or a painting takes one frame and gives it back, keeps it through a save, drops it when it comes down; the dark
 * oak and brass frames hang, hold an item and come down as themselves.
 */
final class FrameTests {
    private FrameTests() {}

    static void register() {
        test("poster_frame_takes_one_frame", FrameTests::takesOneFrame);
        test("poster_frame_comes_off_and_down", FrameTests::comesOffAndDown);
        test("poster_frame_is_kept_through_a_save", FrameTests::keptThroughASave);
        test("painting_frame_on_kept_off_and_down", FrameTests::paintingFramed);
        test("nook_item_frames_hang_and_hold", FrameTests::nookFramesHang);
    }

    /** A 3x2 print on a wall of stone, three blocks wide and two high. */
    private static PosterEntity poster(GameTestHelper helper) {
        BlockPos base = new BlockPos(1, 1, 2);
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 2; y++) {
                helper.setBlock(base.offset(x, y, 0), Blocks.STONE);
            }
        }
        var poster = new PosterEntity(helper.getLevel(), helper.absolutePos(base.offset(1, 0, -1)), Direction.NORTH,
                PhotoPrints.id(42, new Photo.Size(3, 2)));
        check(helper, poster.survives(), "the 3x2 print hangs on the wall: " + poster.getBoundingBox());
        helper.getLevel().addFreshEntity(poster);
        return poster;
    }

    private static InteractionResult click(PosterEntity poster, ServerPlayer player) {
        return poster.interactAt(player, poster.position(), InteractionHand.MAIN_HAND);
    }

    /** One frame frames a poster three blocks wide, whatever its size; its look vanilla's birch, and each frame's look. */
    private static void takesOneFrame(GameTestHelper helper) {
        PosterEntity poster = poster(helper);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ITEM_FRAME, 2));
        check(helper, click(poster, player).consumesAction(), "framed");
        check(helper, poster.frame().is(Items.ITEM_FRAME) && poster.frame().getCount() == 1, "one frame on it: " + poster.frame());
        check(helper, player.getMainHandItem().getCount() == 1, "one left in the hand: " + player.getMainHandItem());
        check(helper, PictureFrames.look(poster.frame()).rim().getPath().equals("birch_planks") && !PictureFrames.look(poster.frame()).glow(),
                "vanilla's frame is its birch rim, not glowing");
        check(helper, PictureFrames.look(new ItemStack(Items.GLOW_ITEM_FRAME)).glow(), "a glowing frame's picture glows");
        var brass = PictureFrames.look(new ItemStack(ModItems.BRASS_ITEM_FRAME.get()));
        check(helper, brass.rim().getPath().equals("brass_moulding") && brass.moulded() && !brass.glow(),
                "the brass frame's rim is the brass moulding: " + brass);
        var darkGlow = PictureFrames.look(new ItemStack(ModItems.DARK_OAK_GLOW_ITEM_FRAME.get()));
        check(helper, darkGlow.rim().getPath().equals("dark_oak_planks") && darkGlow.glow() && !darkGlow.moulded(),
                "the glowing dark oak frame: a plain dark oak rim, the picture glowing: " + darkGlow);
        var brassGlow = PictureFrames.look(new ItemStack(ModItems.BRASS_GLOW_ITEM_FRAME.get()));
        check(helper, brassGlow.moulded() && brassGlow.glow(), "the glowing brass frame: the moulding, the picture glowing: " + brassGlow);
        check(helper, !PictureFrames.look(new ItemStack(ModItems.DARK_OAK_ITEM_FRAME.get())).moulded(), "dark oak's rim is plain");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DARK_OAK_ITEM_FRAME.get(), 3));
        click(poster, player);
        check(helper, poster.frame().is(Items.ITEM_FRAME) && player.getMainHandItem().getCount() == 3,
                "a framed poster takes no second frame");
        helper.succeed();
    }

    /** Sneaking with an empty hand takes the frame off, back to the hand; a framed poster broken drops poster and frame. */
    private static void comesOffAndDown(GameTestHelper helper) {
        PosterEntity poster = poster(helper);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DARK_OAK_ITEM_FRAME.get()));
        click(poster, player);
        check(helper, poster.frame().is(ModItems.DARK_OAK_ITEM_FRAME.get()), "framed in dark oak");
        check(helper, player.getMainHandItem().isEmpty(), "the one frame used");
        click(poster, player);
        check(helper, !poster.frame().isEmpty(), "an empty hand standing up takes nothing off");
        player.setShiftKeyDown(true);
        click(poster, player);
        check(helper, poster.frame().isEmpty(), "sneaking, the frame comes off");
        check(helper, player.getInventory().countItem(ModItems.DARK_OAK_ITEM_FRAME.get()) == 1, "back to the player");
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BRASS_ITEM_FRAME.get()));
        click(poster, player);
        poster.dropItem(null);
        List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(poster.blockPosition()).inflate(3));
        check(helper, dropped.stream().anyMatch(item -> item.getItem().is(ModItems.POSTER.get())), "the poster drops: " + dropped);
        check(helper, dropped.stream().anyMatch(item -> item.getItem().is(ModItems.BRASS_ITEM_FRAME.get()) && item.getItem().getCount() == 1),
                "and its brass frame: " + dropped);
        helper.succeed();
    }

    /**
     * A vanilla painting, clicked as a player clicks it (Player#interactOn, the hook PaintingFrames listens on): framed by
     * one frame, no second; kept through a save, a painting with none saving no frame; taken off by sneaking with an
     * empty hand; dropped beside the painting when it comes down.
     */
    private static void paintingFramed(GameTestHelper helper) {
        BlockPos wall = new BlockPos(2, 2, 3);
        helper.setBlock(wall, Blocks.STONE);
        var level = helper.getLevel();
        var kebab = level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT).getOrThrow(PaintingVariants.KEBAB);
        var painting = new Painting(level, helper.absolutePos(wall.north()), Direction.NORTH, kebab);
        check(helper, painting.survives(), "the painting hangs on the wall");
        level.addFreshEntity(painting);
        ServerPlayer visitor = MockPlayers.inLevel(helper, GameType.ADVENTURE);
        visitor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ITEM_FRAME));
        EntityClicks.interactOn(visitor, painting, InteractionHand.MAIN_HAND, Vec3.ZERO);
        check(helper, PaintingFrames.frame(painting).isEmpty() && visitor.getMainHandItem().getCount() == 1,
                "a player in adventure mode frames nothing");
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLOW_ITEM_FRAME, 2));
        check(helper, EntityClicks.interactOn(player, painting, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction(), "the click frames it");
        check(helper, PaintingFrames.frame(painting).is(Items.GLOW_ITEM_FRAME) && PaintingFrames.frame(painting).getCount() == 1,
                "one glowing frame on it: " + PaintingFrames.frame(painting));
        check(helper, player.getMainHandItem().getCount() == 1, "one left in the hand");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BRASS_ITEM_FRAME.get()));
        EntityClicks.interactOn(player, painting, InteractionHand.MAIN_HAND, Vec3.ZERO);
        check(helper, PaintingFrames.frame(painting).is(Items.GLOW_ITEM_FRAME) && player.getMainHandItem().getCount() == 1,
                "a framed painting takes no second frame");
        try (var reporter = new ProblemReporter.ScopedCollector(ProblemReporter.pathOf(painting), LogUtils.getLogger())) {
            var output = TagValueOutput.createWithContext(reporter, level.registryAccess());
            ValueIO.saveWithoutId(painting, output);
            var copy = new Painting(EntityType.PAINTING, level);
            ValueIO.load(copy, TagValueInput.create(reporter, level.registryAccess(), output.buildResult()));
            check(helper, PaintingFrames.frame(copy).is(Items.GLOW_ITEM_FRAME), "kept through a save: " + PaintingFrames.frame(copy));
            var bare = TagValueOutput.createWithContext(reporter, level.registryAccess());
            ValueIO.saveWithoutId(new Painting(level, helper.absolutePos(wall.north()), Direction.NORTH, kebab), bare);
            check(helper, !bare.buildResult().toString().contains("painting_frame"), "a painting with no frame saves none: " + bare.buildResult());
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        check(helper, EntityClicks.interactOn(player, painting, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction(), "sneaking, an empty hand");
        check(helper, PaintingFrames.frame(painting).isEmpty(), "the frame comes off");
        check(helper, player.getInventory().countItem(Items.GLOW_ITEM_FRAME) == 1, "back to the player");
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BRASS_ITEM_FRAME.get()));
        EntityClicks.interactOn(player, painting, InteractionHand.MAIN_HAND, Vec3.ZERO);
        painting.dropItem(null);
        List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(painting.blockPosition()).inflate(3));
        check(helper, dropped.stream().anyMatch(item -> item.getItem().is(Items.PAINTING)), "the painting drops: " + dropped);
        check(helper, dropped.stream().anyMatch(item -> item.getItem().is(ModItems.BRASS_ITEM_FRAME.get())), "and its brass frame: " + dropped);
        check(helper, PaintingFrames.frame(painting).isEmpty(), "and has none left to drop twice");
        helper.succeed();
    }

    /** Saved and loaded, a poster keeps its frame. */
    private static void keptThroughASave(GameTestHelper helper) {
        PosterEntity poster = poster(helper);
        poster.setFrame(new ItemStack(Items.GLOW_ITEM_FRAME));
        try (var reporter = new ProblemReporter.ScopedCollector(ProblemReporter.pathOf(poster), LogUtils.getLogger())) {
            var output = TagValueOutput.createWithContext(reporter, helper.getLevel().registryAccess());
            ValueIO.saveWithoutId(poster, output);
            var copy = new PosterEntity(ModEntities.POSTER.get(), helper.getLevel());
            ValueIO.load(copy, TagValueInput.create(reporter, helper.getLevel().registryAccess(), output.buildResult()));
            check(helper, copy.frame().is(Items.GLOW_ITEM_FRAME), "kept: " + copy.frame());
            var bare = TagValueOutput.createWithContext(reporter, helper.getLevel().registryAccess());
            ValueIO.saveWithoutId(new PosterEntity(helper.getLevel(), poster.getPos(), Direction.NORTH, PhotoPrints.id(42, new Photo.Size(1, 1))), bare);
            check(helper, !bare.buildResult().contains("frame"), "an unframed poster saves no frame key");
        }
        helper.succeed();
    }

    /**
     * The dark oak and brass frames and their glowing forms hang on a wall as item frames, each its own entity type, hold
     * an item, and come down as themselves; a glowing one sounds as vanilla's glowing frame.
     */
    private static void nookFramesHang(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        var items = List.of(ModItems.DARK_OAK_ITEM_FRAME.get(), ModItems.DARK_OAK_GLOW_ITEM_FRAME.get(),
                ModItems.BRASS_ITEM_FRAME.get(), ModItems.BRASS_GLOW_ITEM_FRAME.get());
        var types = List.of(ModEntities.DARK_OAK_ITEM_FRAME.get(), ModEntities.DARK_OAK_GLOW_ITEM_FRAME.get(),
                ModEntities.BRASS_ITEM_FRAME.get(), ModEntities.BRASS_GLOW_ITEM_FRAME.get());
        var walls = List.of(new BlockPos(1, 2, 2), new BlockPos(3, 2, 2), new BlockPos(5, 2, 2), new BlockPos(3, 5, 2));
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            BlockPos on = walls.get(i);
            helper.setBlock(on, Blocks.STONE);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
            BlockPos at = helper.absolutePos(on);
            var hit = new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.NORTH, 0.5), Direction.NORTH, at, false);
            InteractionResult placed = item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            check(helper, placed.consumesAction() && player.getMainHandItem().isEmpty(), item + " hangs: " + placed);
            List<NookItemFrame> frames = helper.getLevel().getEntitiesOfClass(NookItemFrame.class, new AABB(at.north()));
            check(helper, frames.size() == 1, "one frame on the wall: " + frames);
            NookItemFrame frame = frames.get(0);
            check(helper, frame.getType() == types.get(i), item + ": its own entity type, " + frame.getType());
            check(helper, frame.brass() == (i >= 2) && frame.glow() == (i % 2 == 1), item + ": brass " + frame.brass() + ", glow " + frame.glow());
            check(helper, (frame.getPlaceSound() == SoundEvents.GLOW_ITEM_FRAME_PLACE) == frame.glow(), item + ": its sound");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
            dev.nookworks.port.core.EntityClicks.interact(frame, player, InteractionHand.MAIN_HAND, frame.position());
            check(helper, frame.getItem().is(Items.DIAMOND), "it holds a diamond: " + frame.getItem());
            frame.dropItem(player);
            List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at.north()).inflate(2));
            check(helper, dropped.stream().anyMatch(drop -> drop.getItem().is(item)), "it comes down as itself: " + dropped);
            check(helper, dropped.stream().anyMatch(drop -> drop.getItem().is(Items.DIAMOND)), "with the diamond: " + dropped);
            dropped.forEach(drop -> drop.discard());
            frame.discard();
        }
        helper.succeed();
    }
}
