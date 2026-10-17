package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.nookworks.NookworksConfig;
import dev.nookworks.NookworksNetwork;
import dev.nookworks.photo.CameraItem;
import dev.nookworks.photo.Photo;
import dev.nookworks.photo.PhotoPrints;
import dev.nookworks.photo.Photos;
import dev.nookworks.poster.PosterEntity;
import dev.nookworks.poster.PosterVariant;
import dev.nookworks.poster.Posters;
import dev.nookworks.poster.PrintingPressBlockEntity;
import dev.nookworks.poster.PrintingPressMenu;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The Camera (the user, 2026-10-11: take a photo, open the Printing Press with the camera in the inventory, print it as
 * a poster): the photos it keeps, who is sent one, the press printing one at a size, the print on a wall, and the
 * picture a print shows.
 */
final class CameraTests {
    private CameraTests() {}

    static void register() {
        test("camera_keeps_its_last_nine", CameraTests::keepsItsLastNine);
        test("camera_takes_nothing_it_should_not", CameraTests::takesNothingItShouldNot);
        test("photos_are_shown_to_whom_they_may_be", CameraTests::shownToWhomTheyMayBe);
        test("press_prints_a_photo_at_its_size", CameraTests::pressPrintsAPhoto);
        test("photo_print_hangs_and_comes_down_as_itself", CameraTests::printHangs);
        test("photo_print_is_the_middle_of_the_photo", CameraTests::printIsTheMiddle);
        test("photo_is_finished_for_showing", CameraTests::finishedForShowing);
        test("photo_asks_are_bounded", CameraTests::asksAreBounded);
        test("photo_file_trouble_is_said", CameraTests::fileTroubleIsSaid);
        test("press_label_text_is_its_own", CameraTests::labelTextIsItsOwn);
    }

    /** A photo all of one colour, told apart by {@code shade}. */
    private static byte[] pixels(int shade) {
        byte[] pixels = new byte[Photo.BYTES];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (byte) shade;
        }
        return pixels;
    }

    private static ServerPlayer withCamera(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CAMERA.get()));
        return player;
    }

    /** Takes a photo, the camera's cooling down put aside (a test has no time to wait). */
    private static int shoot(ServerPlayer player, int shade) {
        player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(player.getMainHandItem()));
        return Photos.take(player, pixels(shade));
    }

    /**
     * The camera keeps the last nine: a tenth pushes the oldest out, which is gone with its file - unless it was printed,
     * which stays for the posters of it. A second photo straight after the first waits for the camera to cool down.
     */
    private static void keepsItsLastNine(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = withCamera(helper);
        int first = shoot(player, 1);
        check(helper, first > 0 && Photos.get(server, first) != null, "a photo taken: " + first);
        check(helper, Photos.take(player, pixels(2)) == 0, "none straight after: the camera cools down");
        int second = shoot(player, 2);
        Photos.printed(server, second);
        for (int i = 3; i <= Photos.KEPT; i++) {
            shoot(player, i);
        }
        List<Integer> kept = CameraItem.photos(player.getMainHandItem());
        check(helper, kept.size() == Photos.KEPT && kept.getFirst() == first, "nine kept, the first first: " + kept);
        int tenth = shoot(player, 10);
        kept = CameraItem.photos(player.getMainHandItem());
        check(helper, kept.size() == Photos.KEPT && !kept.contains(first) && kept.getLast() == tenth, "the tenth pushes the first out: " + kept);
        check(helper, Photos.get(server, first) == null, "and the first, never printed, is gone");
        shoot(player, 11);
        check(helper, !CameraItem.photos(player.getMainHandItem()).contains(second) && Photos.get(server, second) != null,
                "the second, printed, stays when the camera lets it go");
        Photo photo = Photos.get(server, tenth);
        check(helper, photo != null && photo.pixels()[0] == 10 && photo.author().equals(player.getName().getString())
                && photo.authorId().equals(player.getUUID()), "a photo keeps its pixels and who took it");
        helper.succeed();
    }

    /** Nothing without a camera in hand, nothing of the wrong size, and nothing with photos turned off. */
    private static void takesNothingItShouldNot(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        check(helper, Photos.take(player, pixels(1)) == 0, "no camera in hand, no photo");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.CAMERA.get()));
        check(helper, Photos.take(player, new byte[Photo.BYTES - 3]) == 0, "pixels of the wrong size, no photo");
        var allowed = NookworksConfig.PLAYER_PHOTOS;
        boolean was = allowed.getAsBoolean();
        try {
            allowed.set(false);
            allowed.clearCache();
            check(helper, Photos.take(player, pixels(1)) == 0, "photos off, no photo");
        } finally {
            allowed.set(was);
            allowed.clearCache();
        }
        check(helper, Photos.take(player, pixels(1)) > 0, "the camera in the off hand takes one");
        helper.succeed();
    }

    /**
     * A photo is sent to the one who took it, to anyone carrying a camera that holds it, and to anyone once it is printed
     * - not to anybody else by its number; and to no one with photos turned off. Taken out, it is no one's.
     */
    private static void shownToWhomTheyMayBe(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer author = withCamera(helper);
        ServerPlayer other = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        int photo = shoot(author, 5);
        check(helper, Photos.mayShow(author, photo) && !Photos.mayShow(other, photo), "the author sees it, someone else not");
        ItemStack copy = author.getMainHandItem().copy();
        other.getInventory().add(copy);
        check(helper, Photos.mayShow(other, photo), "carrying a camera that holds it, they do");
        other.getInventory().clearContent();
        Photos.printed(server, photo);
        check(helper, Photos.mayShow(other, photo), "printed, anyone does");
        var allowed = NookworksConfig.PLAYER_PHOTOS;
        boolean was = allowed.getAsBoolean();
        try {
            allowed.set(false);
            allowed.clearCache();
            check(helper, !Photos.mayShow(author, photo), "photos off, no one");
        } finally {
            allowed.set(was);
            allowed.clearCache();
        }
        check(helper, Photos.answer(author, photo).status() == NookworksNetwork.PhotoPixels.SHOWN
                && Photos.answer(other, photo).status() == NookworksNetwork.PhotoPixels.SHOWN, "told so, both are shown it");
        check(helper, Photos.remove(server, photo) == Photos.Removal.REMOVED && Photos.get(server, photo) == null && !Photos.mayShow(author, photo),
                "removed, it is gone for everyone");
        check(helper, Photos.answer(author, photo).status() == NookworksNetwork.PhotoPixels.GONE, "and told so: gone");
        check(helper, Photos.remove(server, photo) == Photos.Removal.NONE, "and cannot be removed twice");
        int hidden = shoot(author, 6);
        check(helper, Photos.answer(other, hidden).status() == NookworksNetwork.PhotoPixels.HIDDEN,
                "one not to be seen is told apart from one gone (the game asks again for it, review r12)");
        helper.succeed();
    }

    /**
     * Through the press's menu: a photo in the player's camera is chosen by its button, a size by its, and Print gives a
     * poster of that photo at that size, for a sheet and the four inks; the photo is kept as printed. A photo the player
     * does not carry cannot be chosen by its number.
     */
    private static void pressPrintsAPhoto(GameTestHelper helper) {
        var level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos pressPos = new BlockPos(1, 2, 1);
        helper.setBlock(pressPos, ModBlocks.PRINTING_PRESS.get());
        var press = helper.getBlockEntity(pressPos, PrintingPressBlockEntity.class);
        press.energy().set((int) press.energy().getCapacityAsLong());
        press.setItem(PrintingPressBlockEntity.PAPER, new ItemStack(ModItems.FINE_PAPER.get(), 2));
        for (int i = 0; i < PosterVariant.PROCESS_INKS.size(); i++) {
            press.setItem(PrintingPressBlockEntity.FIRST_INK + i, new ItemStack(PosterVariant.PROCESS_INKS.get(i), 2));
        }
        ServerPlayer player = withCamera(helper);
        int photo = shoot(player, 7);
        ServerPlayer stranger = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        var strangers = (PrintingPressMenu) press.createMenu(2, stranger.getInventory(), stranger);
        check(helper, !strangers.clickMenuButton(stranger, PrintingPressMenu.photoButton(photo)) && press.photo() == 0,
                "a photo not carried is not chosen by its number");
        var menu = (PrintingPressMenu) press.createMenu(1, player.getInventory(), player);
        check(helper, PrintingPressMenu.photos(player.getInventory()).equals(List.of(photo)), "the press offers the camera's photo");
        check(helper, menu.clickMenuButton(player, PrintingPressMenu.photoButton(photo)) && press.photo() == photo
                && PrintingPressBlockEntity.PHOTO.equals(press.selected()), "chosen by its button");
        Photo.Size wide = new Photo.Size(2, 1);
        check(helper, menu.clickMenuButton(player, PrintingPressMenu.sizeButton(wide)) && press.photoSize().equals(wide), "a size chosen");
        check(helper, menu.photo() == photo && menu.photoSize().equals(wide) && menu.printingPhoto(), "and the screen is told");
        check(helper, !strangers.clickMenuButton(stranger, PrintingPressMenu.BUTTON_PRINT) && !press.running(),
                "nor printed by one it is kept from, left chosen in the press: that would show it to all (review r12 fix check)");
        var allowed = NookworksConfig.PLAYER_PHOTOS;
        boolean was = allowed.getAsBoolean();
        try {
            allowed.set(false);
            allowed.clearCache();
            menu.clickMenuButton(player, PrintingPressMenu.BUTTON_PRINT);
            PrintingPressBlockEntity.serverTick(level, helper.absolutePos(pressPos), helper.getBlockState(pressPos), press);
            check(helper, !press.running() && press.getItem(PrintingPressBlockEntity.PAPER).getCount() == 2,
                    "with photos off, no blank sheet printed for paper and ink (review r12)");
        } finally {
            allowed.set(was);
            allowed.clearCache();
        }
        menu.clickMenuButton(player, PrintingPressMenu.BUTTON_PRINT);
        for (int i = 0; i < PrintingPressBlockEntity.TICKS + 2; i++) {
            PrintingPressBlockEntity.serverTick(level, helper.absolutePos(pressPos), helper.getBlockState(pressPos), press);
        }
        ItemStack out = press.getItem(PrintingPressBlockEntity.OUTPUT);
        check(helper, out.is(ModItems.POSTER.get()) && PhotoPrints.id(photo, wide).equals(Posters.idOf(out)), "a print of the photo: " + out);
        check(helper, press.getItem(PrintingPressBlockEntity.PAPER).getCount() == 1
                && press.getItem(PrintingPressBlockEntity.FIRST_INK).getCount() == 1, "for a sheet and the inks");
        Photo kept = Photos.get(server, photo);
        check(helper, kept != null && kept.printed(), "the photo is kept as printed");
        check(helper, strangers.clickMenuButton(stranger, PrintingPressMenu.BUTTON_PRINT) && press.running(),
                "and, printed, it is anyone's to print again");
        check(helper, strangers.clickMenuButton(stranger, PrintingPressMenu.BUTTON_PRINT) && !press.running(), "or to stop");
        check(helper, menu.clickMenuButton(player, PrintingPressMenu.photoButton(photo)) && press.selected() == null,
                "chosen again, it is let go of");
        helper.succeed();
    }

    /**
     * A game may ask only so much (review r12: a modified client asking for every number filled the server's memory, and
     * had each looked for on the disk): a number never given out is turned away and not remembered, the photos kept read
     * are few, and a player's game has {@link Photos#ASKS} asks in hand.
     */
    private static void asksAreBounded(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = withCamera(helper);
        int real = shoot(player, 3);
        int before = Photos.readCount(server);
        int next = Photos.nextNumber(server);
        for (int i = 0; i < 1000; i++) {
            check(helper, Photos.get(server, next + 1 + i * 7919) == null, "no photo by a number not given out");
            Photos.answer(player, -i - 1);
        }
        check(helper, Photos.readCount(server) == before, "and none of them kept: " + before + " -> " + Photos.readCount(server));
        check(helper, Photos.get(server, real) != null && Photos.readCount(server) <= Photos.CACHED, "the real one still there");
        int granted = 0;
        for (int i = 0; i < Photos.ASKS * 2; i++) {
            if (Photos.mayAsk(player)) {
                granted++;
            }
        }
        check(helper, granted == Photos.ASKS, "a game's asks in hand, and no more at once: " + granted);
        helper.succeed();
    }

    /**
     * A photo whose file cannot be written is not kept (the player is told), and one whose file cannot be deleted is not
     * said removed - it stays, rather than come back at the next start (review r12). A file in the way is stood in for by
     * a folder with something in it, which neither a save nor a delete can go through.
     */
    private static void fileTroubleIsSaid(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = withCamera(helper);
        int kept = shoot(player, 4);
        Path folder = server.getWorldPath(LevelResource.DATA).resolve("nookworks").resolve("photos");
        Path inTheWay = folder.resolve(kept + ".dat");
        Path blocked = folder.resolve(Photos.nextNumber(server) + ".dat");
        try {
            Files.delete(inTheWay);
            Files.createDirectories(inTheWay.resolve("held"));
            check(helper, Photos.remove(server, kept) == Photos.Removal.FAILED && Photos.get(server, kept) != null,
                    "a delete that fails says so, and the photo stays");
            Files.createDirectories(blocked.resolve("held"));
            List<Integer> before = CameraItem.photos(player.getMainHandItem());
            check(helper, shoot(player, 5) == 0 && CameraItem.photos(player.getMainHandItem()).equals(before),
                    "a photo whose file cannot be written is not kept, nor put in the camera");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            for (Path dir : List.of(inTheWay, blocked)) {
                try {
                    Files.deleteIfExists(dir.resolve("held"));
                    Files.deleteIfExists(dir);
                    Files.deleteIfExists(dir.resolveSibling(dir.getFileName() + ".partial"));
                } catch (IOException ignored) {
                    // left for the next run's world
                }
            }
        }
        helper.succeed();
    }

    /**
     * The letters a press's labels say are read from their own slots of its screen's data, not from the photo's after
     * them (review r12: a photo printed 2x1 put an A on the labels' preview, which the print then lacked).
     */
    private static void labelTextIsItsOwn(GameTestHelper helper) {
        BlockPos pressPos = new BlockPos(1, 2, 1);
        helper.setBlock(pressPos, ModBlocks.PRINTING_PRESS.get());
        var press = helper.getBlockEntity(pressPos, PrintingPressBlockEntity.class);
        ServerPlayer player = withCamera(helper);
        int photo = shoot(player, 8);
        var menu = (PrintingPressMenu) press.createMenu(1, player.getInventory(), player);
        menu.clickMenuButton(player, PrintingPressMenu.photoButton(photo));
        for (Photo.Size size : Photo.SIZES) {
            menu.clickMenuButton(player, PrintingPressMenu.sizeButton(size));
            check(helper, menu.labelText().isEmpty(), "no letters with the photo at " + size + ": '" + menu.labelText() + "'");
        }
        press.setLabelText("ABC");
        check(helper, menu.labelText().equals("ABC"), "the letters as typed, whatever the photo: '" + menu.labelText() + "'");
        helper.succeed();
    }

    /** A print goes on a wall at its size, is named a photo, and comes down as the same print. */
    private static void printHangs(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos wall = new BlockPos(2, 2, 2);
        for (BlockPos stone : List.of(wall.west(), wall, wall.east())) {
            helper.setBlock(stone, Blocks.STONE);
        }
        Identifier id = PhotoPrints.id(42, new Photo.Size(2, 1));
        var poster = new PosterEntity(level, helper.absolutePos(wall.north()), Direction.NORTH, id);
        check(helper, poster.survives(), "a 2x1 print hangs on two blocks of wall");
        var box = poster.getBoundingBox();
        check(helper, Math.abs(box.getXsize() - 2) < 1.0E-6 && Math.abs(box.getYsize() - 1) < 1.0E-6, "two blocks wide, one high: " + box);
        level.addFreshEntity(poster);
        ItemStack picked = poster.getPickResult();
        check(helper, id.equals(Posters.idOf(picked)), "it picks as itself: " + picked);
        check(helper, picked.getHoverName().getContents() instanceof TranslatableContents name && name.getKey().equals("item.nookworks.photo"),
                "named a photo: " + picked.getHoverName());
        poster.dropItem(level, null);
        helper.assertItemEntityPresent(ModItems.POSTER.get(), wall.north(), 2.0);
        helper.succeed();
    }

    /**
     * A print shows the middle of the photo cut to its shape, at 16 pixels a block: a square the middle three quarters
     * across, a wide one the middle two thirds down; and the ids name only real prints.
     */
    private static void printIsTheMiddle(GameTestHelper helper) {
        byte[] pixels = new byte[Photo.BYTES];
        for (int y = 0; y < Photo.HEIGHT; y++) {
            for (int x = 0; x < Photo.WIDTH; x++) {
                int at = (x + y * Photo.WIDTH) * 3;
                // a frame of red eight pixels wide round green
                boolean edge = x < 8 || x >= Photo.WIDTH - 8 || y < 8 || y >= Photo.HEIGHT - 8;
                pixels[at] = (byte) (edge ? 255 : 0);
                pixels[at + 1] = (byte) (edge ? 0 : 255);
            }
        }
        int[] square = Photo.print(pixels, 1, 1);
        check(helper, square.length == 16 * 16, "a 1x1 print is 16 pixels square");
        check(helper, square[0] == 0xFFFF0000 && square[8 + 8 * 16] == 0xFF00FF00 && square[15] == 0xFFFF0000,
                "the middle 48 across: the frame's sides at its sides, green in the middle");
        int[] wide = Photo.print(pixels, 2, 1);
        check(helper, wide.length == 32 * 16 && wide[0] == 0xFFFF0000 && wide[16] == 0xFF00FF00 && wide[16 + 15 * 32] == 0xFF00FF00,
                "a 2x1 print, the whole width, the middle 32 down: the frame's sides, and none of its top or bottom");
        int[] whole = Photo.print(pixels, 4, 3);
        check(helper, whole.length == Photo.WIDTH * Photo.HEIGHT && whole[8 + 8 * Photo.WIDTH] == 0xFF00FF00
                && whole[7 + 8 * Photo.WIDTH] == 0xFFFF0000, "4x3 is the photo pixel for pixel");
        Identifier id = PhotoPrints.id(12, new Photo.Size(3, 2));
        PhotoPrints.Print print = PhotoPrints.parse(id);
        check(helper, print != null && print.photo() == 12 && print.size().equals(new Photo.Size(3, 2)), "an id read back: " + id);
        for (String bad : List.of("photo/0/4x3", "photo/5/5x5", "photo/x/4x3", "photo/5", "safety_cross")) {
            check(helper, PhotoPrints.parse(Identifier.fromNamespaceAndPath("nookworks", bad)) == null, bad + " is no print");
        }
        check(helper, PhotoPrints.parse(Identifier.fromNamespaceAndPath("other", "photo/5/4x3")) == null, "nor another mod's");
        helper.succeed();
    }

    /**
     * The picture shown of a photo ({@link Photo#finish}; the user, 2026-10-14: 眠い…コントラストが低くてぼんやり): a
     * flat picture stays flat and grey stays grey; a dull one is stretched; a dark one stays dark and a snowfield bright;
     * a blue sky stays blue; an edge comes out crisper; and the photo itself is left as the camera read it.
     */
    private static void finishedForShowing(GameTestHelper helper) {
        byte[] grey = shaded((x, y, c) -> 100);
        byte[] greyShown = Photo.finish(grey);
        boolean flat = true;
        for (int i = 0; i < Photo.BYTES; i++) {
            flat &= greyShown[i] == greyShown[0];
        }
        check(helper, flat && Math.abs((greyShown[0] & 0xFF) - 100) <= 4, "a flat grey stays a flat grey: " + (greyShown[0] & 0xFF));
        check(helper, grey[0] == 100, "and the photo is left as it was");

        byte[] dull = Photo.finish(shaded((x, y, c) -> 90 + 60 * x / (Photo.WIDTH - 1)));
        check(helper, at(dull, 0, 10, 0) <= 80 && at(dull, Photo.WIDTH - 1, 10, 0) >= 180,
                "a dull 90..150 is stretched: " + at(dull, 0, 10, 0) + ".." + at(dull, Photo.WIDTH - 1, 10, 0));

        byte[] dark = Photo.finish(shaded((x, y, c) -> 40 * x / (Photo.WIDTH - 1)));
        check(helper, max(dark) <= 50 && Math.abs(mean(dark) - 19.5) <= 3, "a night stays dark: up to " + max(dark) + ", " + mean(dark) + " on average");

        byte[] snow = Photo.finish(shaded((x, y, c) -> 180 + 75 * x / (Photo.WIDTH - 1)));
        check(helper, min(snow) >= 170 && mean(snow) >= 210, "a snowfield stays bright: down to " + min(snow) + ", " + mean(snow) + " on average");

        int[] skyColour = {120, 160, 250};
        int[] grassColour = {40, 70, 30};
        byte[] view = Photo.finish(shaded((x, y, c) -> y < Photo.HEIGHT / 2 ? skyColour[c] : grassColour[c]));
        check(helper, at(view, 5, 2, 2) > at(view, 5, 2, 1) && at(view, 5, 2, 1) > at(view, 5, 2, 0), "the sky stays blue");
        check(helper, at(view, 5, 40, 1) > at(view, 5, 40, 0) && at(view, 5, 40, 1) > at(view, 5, 40, 2), "the grass stays green");

        byte[] edge = Photo.finish(shaded((x, y, c) -> x < Photo.WIDTH / 2 ? 100 : 150));
        check(helper, at(edge, Photo.WIDTH / 2 - 1, 10, 0) < at(edge, 16, 10, 0) && at(edge, Photo.WIDTH / 2, 10, 0) > at(edge, 48, 10, 0),
                "an edge is sharpened: darker just before it, lighter just after");
        helper.succeed();
    }

    private interface Shade {
        int at(int x, int y, int channel);
    }

    private static byte[] shaded(Shade shade) {
        byte[] pixels = new byte[Photo.BYTES];
        for (int y = 0; y < Photo.HEIGHT; y++) {
            for (int x = 0; x < Photo.WIDTH; x++) {
                for (int c = 0; c < 3; c++) {
                    pixels[(x + y * Photo.WIDTH) * 3 + c] = (byte) shade.at(x, y, c);
                }
            }
        }
        return pixels;
    }

    private static int at(byte[] pixels, int x, int y, int channel) {
        return pixels[(x + y * Photo.WIDTH) * 3 + channel] & 0xFF;
    }

    private static int max(byte[] pixels) {
        int max = 0;
        for (byte b : pixels) {
            max = Math.max(max, b & 0xFF);
        }
        return max;
    }

    private static int min(byte[] pixels) {
        int min = 255;
        for (byte b : pixels) {
            min = Math.min(min, b & 0xFF);
        }
        return min;
    }

    private static double mean(byte[] pixels) {
        double sum = 0;
        for (byte b : pixels) {
            sum += b & 0xFF;
        }
        return sum / pixels.length;
    }
}
