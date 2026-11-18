package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;

import dev.nookworks.block.entity.CrateBlockEntity;
import dev.nookworks.crop.EndSeedPlant;
import dev.nookworks.decor.PlacedBottlesBlock;
import dev.nookworks.machine.LootSummaries;
import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.photo.Photo;
import dev.nookworks.photo.Photos;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import dev.nookworks.storage.StorageContents;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import dev.nookworks.port.test.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import dev.nookworks.port.storage.Nbt;

/**
 * The r15 review toward 1.0 (2026-10-13): what an older world's items become in our blocks, who may set bottles down and
 * put lanterns out, a sifting table with a roll of nothing, and a photo's number after a crash.
 */
final class R15Tests {
    private R15Tests() {}

    static void register() {
        test("spectator_sets_no_bottle_down", helper -> noBottle(helper, GameType.SPECTATOR));
        test("adventurer_sets_no_bottle_down", helper -> noBottle(helper, GameType.ADVENTURE));
        test("bottle_goes_down_only_where_its_setter_may_build", R15Tests::bottleUnderClaim);
        test("spectator_puts_no_lantern_out", R15Tests::lanternLeftLit);
        test("sifting_table_with_a_roll_of_nothing_goes_to_clients", R15Tests::rollOfNothing);
        test("photo_number_with_a_file_already_is_passed_over", R15Tests::photoNumberPassedOver);
        test("end_seed_draw_reaches_the_client", R15Tests::endSeedSynced);
        test("barrel_contents_with_a_slot_named_twice_keep_the_rest", R15Tests::slotNamedTwice);
        test("japanese_info_text_is_broken_into_lines_before_jei", R15Tests::cjkLines);
        test("if_warm_ocean_coral_grows", R15Tests::ifCoral);
    }

    /**
     * Japanese broken into lines for JEI's pages: every line fits, none starts with closing punctuation or a long-vowel
     * mark, a number keeps its unit, nothing is added or lost (r15: JEI hyphenated every line, read as "ー").
     */
    private static void cjkLines(GameTestHelper helper) {
        String text = "銅インゴット2個と亜鉛インゴット1個を加熱すると、真鍮インゴットができる（80 FE/t）。ビートルートは腹持ちがよい。250 mB以上の水。";
        java.util.function.ToIntFunction<String> measure = line -> line.codePoints().map(c -> c < 0x2000 ? 1 : 2).sum();
        check(helper, dev.nookworks.compat.jei.CjkLines.needed(text) && !dev.nookworks.compat.jei.CjkLines.needed("Brass Ingot"),
                "Japanese needs it, English does not");
        for (int width : new int[] {20, 27, 32}) { // JEI's page holds about sixteen ideographs (here two each)
            String wrapped = dev.nookworks.compat.jei.CjkLines.wrap(text, width, measure);
            String[] lines = wrapped.split("\n");
            check(helper, lines.length > 1, "broken into lines at " + width + ": " + wrapped);
            check(helper, String.join("", lines).equals(text), "nothing added or lost at " + width + ": " + wrapped);
            for (String line : lines) {
                check(helper, measure.applyAsInt(line) <= width, "\"" + line + "\" fits in " + width);
                check(helper, "、。）ーっ".indexOf(line.charAt(0)) < 0, "\"" + line + "\" starts with no closing mark");
                check(helper, !line.endsWith("（"), "\"" + line + "\" ends with no opening bracket");
            }
            check(helper, java.util.Arrays.stream(lines).anyMatch(l -> l.contains("250 mB")) && java.util.Arrays.stream(lines).anyMatch(l -> l.contains("80 FE/t"))
                    && java.util.Arrays.stream(lines).anyMatch(l -> l.contains("2個")) && java.util.Arrays.stream(lines).anyMatch(l -> l.contains("1個")),
                    "numbers keep their units and counters at " + width + ": " + wrapped);
        }
        check(helper, dev.nookworks.compat.jei.CjkLines.wrap("一。\n\n二", 10, measure).equals("一。\n\n二"), "paragraphs and blank lines kept");
        helper.succeed();
    }

    /**
     * The If's warm ocean grows its coral: built in code, its features held tags that could not be read, and the coral
     * (a block drawn from #wall_corals) stopped the If's world being made (found in r15's tests).
     */
    private static void ifCoral(GameTestHelper helper) {
        if (IfTests.notOurs(helper)) {
            return;
        }
        var server = helper.getLevel().getServer();
        var world = dev.nookworks.ifworld.IfWorld.get(server);
        while (!world.rolled()) {
            world.addThrow(1 + helper.getLevel().getRandom().nextInt(6));
        }
        var ifLevel = dev.nookworks.ifworld.IfLevel.open(server);
        check(helper, ifLevel != null && ifLevel.getChunkSource().getGenerator() instanceof dev.nookworks.ifworld.IfChunkGenerator, "the If is ours");
        var generator = (dev.nookworks.ifworld.IfChunkGenerator) ifLevel.getChunkSource().getGenerator();
        var warm = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
                .getOrThrow(net.minecraft.world.level.biome.Biomes.WARM_OCEAN);
        var corals = generator.featuresOf(warm).stream()
                .filter(h -> h.unwrapKey().map(k -> k.location().getPath().equals("warm_ocean_vegetation")).orElse(false))
                .findFirst().orElseThrow(() -> new IllegalStateException("no warm ocean vegetation in the If's warm ocean"));
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.SAND);
                for (int y = 2; y <= 7; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                }
            }
        }
        for (int i = 0; i < 30; i++) { // a reef's worth of tries, every coral feature among them
            // the feature itself, under its placement (whose biome check is for a chunk being made)
            corals.value().feature().value().place(helper.getLevel(), generator, net.minecraft.util.RandomSource.create(i),
                    helper.absolutePos(new BlockPos(4, 2, 4)));
        }
        // PORT: 26.3's second half - the code's own copy of the coral, from VanillaRegistries, cannot be grown (its
        // features held tags that could not be read: the cause of the r15 fault) - is left out: 1.21.1's coral feature
        // reads the block tags from the global registry, so a code-built copy grows too and that check cannot hold here
        helper.succeed();
    }

    /** A barrel's saved contents naming a slot twice (broken data) keep the first of it and every other slot, not nothing. */
    private static void slotNamedTwice(GameTestHelper helper) {
        var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        ListTag entries = new ListTag();
        for (int[] e : new int[][] {{0, 5}, {0, 7}, {1, 9}}) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", e[0]);
            entry.put("item", ItemStack.CODEC.encodeStart(ops, new ItemStack(e[1] == 9 ? Items.FLINT : Items.DIRT)).getOrThrow());
            entry.putInt("count", e[1]);
            entries.add(entry);
        }
        StorageContents read = StorageContents.CODEC.parse(ops, entries).result().orElse(StorageContents.EMPTY);
        check(helper, read.entries().size() == 2 && read.entries().get(0).count() == 5 && read.entries().get(1).item().is(Items.FLINT),
                "the first of slot 0 and slot 1 kept: " + read.entries());
        helper.succeed();
    }

    // PORT: the four tests of an older world's items (M2: old_items_in_our_blocks_are_brought_up_to_date,
    // old_items_on_our_entities_and_players_are_brought_up_to_date, old_vanilla_entities_beside_ours_are_brought_up_to_date,
    // old_mob_in_a_bottle_is_brought_up_to_date) are left out: they open a 26.2 save in 26.3 (26.2's explorer map name,
    // its pot sides as a list) and skipped themselves on any game not newer than 26.2 - on 1.21.1 they could test nothing

    // --- bottles and lanterns: who may change them (S2) ---

    private static void use(GameTestHelper helper, ServerPlayer player, BlockPos pos, Direction face, ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos at = helper.absolutePos(pos);
        Vec3 hit = Vec3.atCenterOf(at).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        player.gameMode.useItemOn(player, helper.getLevel(), held, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, at, false));
    }

    private static ServerPlayer sneaking(GameTestHelper helper, GameType mode) {
        ServerPlayer player = MockPlayers.inLevel(helper, mode);
        Vec3 near = helper.absoluteVec(new Vec3(1.5, 2, 3.5));
        player.moveTo(near.x, near.y, near.z);
        player.setShiftKeyDown(true);
        return player;
    }

    /** A spectator, or a player in adventure mode, sneaking with honey on stone: no bottle goes down (a survivor's does). */
    private static void noBottle(GameTestHelper helper, GameType mode) {
        BlockPos table = new BlockPos(1, 1, 1);
        helper.setBlock(table, Blocks.STONE);
        ItemStack honey = new ItemStack(Items.HONEY_BOTTLE, 3);
        use(helper, sneaking(helper, mode), table, Direction.UP, honey);
        check(helper, !helper.getBlockState(table.above()).is(ModBlocks.PLACED_BOTTLES.get()) && honey.getCount() == 3,
                "a " + mode + " set no bottle down (honey left " + honey.getCount() + ")");
        BlockPos other = new BlockPos(3, 1, 1);
        helper.setBlock(other, Blocks.STONE);
        use(helper, sneaking(helper, GameType.SURVIVAL), other, Direction.UP, honey);
        check(helper, helper.getBlockState(other.above()).is(ModBlocks.PLACED_BOTTLES.get()) && honey.getCount() == 2,
                "a survivor sets one down");
        helper.succeed();
    }

    /** On land claimed by someone else, a bottle is not set down on top of the block clicked; on one's own it is. */
    private static void bottleUnderClaim(GameTestHelper helper) {
        BlockPos table = new BlockPos(1, 1, 1);
        helper.setBlock(table, Blocks.STONE);
        ServerPlayer player = sneaking(helper, GameType.SURVIVAL);
        ItemStack honey = new ItemStack(Items.HONEY_BOTTLE, 3);
        BlockTests.underClaim(UUID.randomUUID(), () -> PlacedBottlesBlock.setDown(helper.getLevel(), helper.absolutePos(table), Direction.UP, player, honey));
        check(helper, !helper.getBlockState(table.above()).is(ModBlocks.PLACED_BOTTLES.get()) && honey.getCount() == 3,
                "nothing set down on another's claim");
        BlockTests.underClaim(player.getUUID(), () -> PlacedBottlesBlock.setDown(helper.getLevel(), helper.absolutePos(table), Direction.UP, player, honey));
        check(helper, helper.getBlockState(table.above()).is(ModBlocks.PLACED_BOTTLES.get()) && honey.getCount() == 2,
                "set down on one's own");
        helper.succeed();
    }

    /** A spectator's sneaking empty hand leaves a lantern lit; a survivor's puts it out. */
    private static void lanternLeftLit(GameTestHelper helper) {
        BlockPos standing = new BlockPos(1, 2, 1);
        helper.setBlock(standing.below(), Blocks.STONE);
        helper.setBlock(standing, Blocks.LANTERN);
        use(helper, sneaking(helper, GameType.SPECTATOR), standing, Direction.SOUTH, ItemStack.EMPTY);
        check(helper, helper.getBlockState(standing).is(Blocks.LANTERN), "a spectator left it lit: " + helper.getBlockState(standing));
        use(helper, sneaking(helper, GameType.ADVENTURE), standing, Direction.SOUTH, ItemStack.EMPTY);
        check(helper, helper.getBlockState(standing).is(Blocks.LANTERN), "so did a player in adventure mode");
        use(helper, sneaking(helper, GameType.SURVIVAL), standing, Direction.SOUTH, ItemStack.EMPTY);
        check(helper, !helper.getBlockState(standing).is(Blocks.LANTERN), "a survivor put it out: " + helper.getBlockState(standing));
        helper.succeed();
    }

    // --- a sifting table that rolls nothing (S5) ---

    /** A datapack's table with a weighted roll of air: the air is a chance of nothing, not an item sent to clients. */
    private static void rollOfNothing(GameTestHelper helper) {
        var table = JsonParser.parseString("""
                {"pools": [{"rolls": 1, "entries": [
                  {"type": "minecraft:item", "name": "minecraft:air", "weight": 3},
                  {"type": "minecraft:item", "name": "minecraft:flint", "weight": 1}]}]}""").getAsJsonObject();
        List<LootSummaries.Drop> drops = LootSummaries.parse(table);
        check(helper, drops.size() == 1 && drops.getFirst().stack().is(Items.FLINT) && Math.abs(drops.getFirst().chance() - 0.25) < 1e-9,
                "only the flint, at a chance of one in four: " + drops);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            for (LootSummaries.Drop drop : drops) {
                LootSummaries.Drop.STREAM_CODEC.encode(buffer, drop); // an empty stack would throw here, and cut the player off
            }
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    // --- a photo's number (S6) ---

    /** A crash left the count behind the files: the next photo passes over a number whose file is there. */
    private static void photoNumberPassedOver(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Photo photo = new Photo(new byte[Photo.BYTES], "test", UUID.randomUUID(), 0, false);
        int next = Photos.nextNumber(server);
        var there = Photos.file(server, next);
        try {
            Files.createDirectories(there.getParent());
            Files.write(there, new byte[] {1});
            int id = Photos.add(server, photo);
            check(helper, id == next + 1, "photo " + next + "'s file is kept, the new one is " + (next + 1) + ", not " + id);
            check(helper, Files.size(there) == 1, "the file there is not written over");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } finally {
            try {
                Files.deleteIfExists(there);
            } catch (java.io.IOException ignored) {
                // a test world's
            }
        }
        helper.succeed();
    }

    // --- the End seed's draw on the client (S7) ---

    /** The End seed's data map goes to clients: bone meal on a bud is asked there too. */
    private static void endSeedSynced(GameTestHelper helper) {
        check(helper, EndSeedPlant.DATA_MAP.networkCodec() != null, "the End seed's draw is sent to clients");
        helper.succeed();
    }
}
