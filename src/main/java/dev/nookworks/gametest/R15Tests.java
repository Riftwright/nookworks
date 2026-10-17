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
import net.minecraft.gametest.framework.GameTestHelper;
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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The r15 review toward 1.0 (2026-10-13): what an older world's items become in our blocks, who may set bottles down and
 * put lanterns out, a sifting table with a roll of nothing, and a photo's number after a crash.
 */
final class R15Tests {
    private R15Tests() {}

    /** 26.2's data version, the version of the worlds a 26.3 game is given most. */
    private static final int OLD = 4903;

    static void register() {
        test("old_items_in_our_blocks_are_brought_up_to_date", R15Tests::oldItemsInOurBlocks);
        test("old_items_on_our_entities_and_players_are_brought_up_to_date", R15Tests::oldItemsOnEntitiesAndPlayers);
        test("old_vanilla_entities_beside_ours_are_brought_up_to_date", R15Tests::oldVanillaEntitiesBesideOurs);
        test("old_mob_in_a_bottle_is_brought_up_to_date", R15Tests::oldMobInABottle);
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
                .filter(h -> h.unwrapKey().map(k -> k.identifier().getPath().equals("warm_ocean_vegetation")).orElse(false))
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
        // and the reason: the code's own copy of it cannot be grown
        var codeBuilt = net.minecraft.data.registries.VanillaRegistries.createWorldLookup()
                .lookupOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE).getOrThrow(corals.unwrapKey().orElseThrow());
        boolean threw = false;
        for (int i = 0; i < 30 && !threw; i++) {
            for (int x = 0; x <= 8; x++) { // fresh sea for each try: the reef above grew over this one
                for (int z = 0; z <= 8; z++) {
                    for (int y = 2; y <= 7; y++) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                    }
                }
            }
            try {
                codeBuilt.value().feature().value().place(helper.getLevel(), generator, net.minecraft.util.RandomSource.create(i),
                        helper.absolutePos(new BlockPos(4, 2, 4)));
            } catch (UnsupportedOperationException e) {
                threw = true;
            }
        }
        check(helper, threw, "the code-built coral cannot read its tag (else this test shows nothing)");
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

    // --- an older world's items in our blocks (M2) ---

    private static boolean runs(GameTestHelper helper) {
        if (SharedConstants.getCurrentVersion().dataVersion().version() <= OLD) {
            helper.succeed(); // the game is 26.2 itself: nothing of 26.2's to bring up
            return false;
        }
        return true;
    }

    /** A decorated pot with a sherd on its back, as 26.3 writes one. */
    private static ItemStack pot() {
        ItemStack pot = new ItemStack(Items.DECORATED_POT);
        var sherd = Optional.of(new ItemStackTemplate(Items.ANGLER_POTTERY_SHERD.builtInRegistryHolder(), 1, DataComponentPatch.EMPTY));
        var brick = Optional.of(new ItemStackTemplate(Items.BRICK.builtInRegistryHolder(), 1, DataComponentPatch.EMPTY));
        pot.set(DataComponents.POT_DECORATIONS, new PotDecorations(sherd, brick, brick, brick));
        return pot;
    }

    /**
     * {@code tag} as 26.2 wrote it, in the two ways 26.3 reads differently: a pot's sides a list of ids, an ocean
     * explorer map by its old name. Changes it in place.
     */
    private static Tag aged(Tag tag) {
        if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                list.set(i, aged(list.get(i)));
            }
        } else if (tag instanceof CompoundTag compound) {
            if (compound.get("id") instanceof StringTag id && id.value().equals("minecraft:ocean_monument_map")) {
                compound.putString("id", "minecraft:ocean_explorer_map");
            }
            if (compound.get("minecraft:pot_decorations") instanceof CompoundTag sides) {
                ListTag ids = new ListTag();
                for (String side : new String[] {"back", "left", "right", "front"}) {
                    ids.add(StringTag.valueOf(sides.getCompoundOrEmpty(side).getStringOr("id", "minecraft:brick")));
                }
                compound.put("minecraft:pot_decorations", ids);
            }
            for (String key : List.copyOf(compound.keySet())) {
                compound.put(key, aged(compound.get(key)));
            }
        }
        return tag;
    }

    private static CompoundTag fixed(GameTestHelper helper, DataFixTypes type, CompoundTag old) {
        int now = SharedConstants.getCurrentVersion().dataVersion().version();
        return (CompoundTag) type.update(helper.getLevel().getServer().getFixerUpper(), new Dynamic<Tag>(NbtOps.INSTANCE, old), OLD, now).getValue();
    }

    /**
     * A 26.2 chunk opened by 26.3: a Crate holding an explorer map, a decorated pot and a Mob Bottle whose allay holds
     * another pot, and a chest holding a Storage Barrel item with a map and a pot in it. The game updates its own chest
     * and passes our Crate over, and the barrel's contents either way; ours are now updated as its are, nothing lost.
     */
    private static void oldItemsInOurBlocks(GameTestHelper helper) {
        if (!runs(helper)) {
            return;
        }
        ServerLevel level = helper.getLevel();
        var registries = level.registryAccess();
        ItemStack map = new ItemStack(Items.OCEAN_MONUMENT_MAP);
        var allay = helper.spawnWithNoFreeWill(EntityTypes.ALLAY, new BlockPos(5, 1, 5));
        allay.setItemSlot(EquipmentSlot.MAINHAND, pot());
        ItemStack bottle = MobBottleItem.filled(allay);
        allay.discard();

        BlockPos cratePos = new BlockPos(1, 1, 1);
        helper.setBlock(cratePos, ModBlocks.CRATE.get());
        CrateBlockEntity crate = helper.getBlockEntity(cratePos, CrateBlockEntity.class);
        crate.setItem(0, map.copy());
        crate.setItem(1, pot());
        crate.setItem(2, bottle.copy());

        BlockPos chestPos = new BlockPos(3, 1, 1);
        helper.setBlock(chestPos, Blocks.CHEST);
        ItemStack barrel = new ItemStack(ModItems.IRON_STORAGE.get());
        barrel.set(ModDataComponents.STORAGE.get(), new StorageContents(List.of(
                new StorageContents.Entry(0, map.copy(), 5), new StorageContents.Entry(1, pot(), 2))));
        helper.getBlockEntity(chestPos, ChestBlockEntity.class).setItem(0, barrel.copy());

        CompoundTag chunk = new CompoundTag();
        chunk.putInt("DataVersion", OLD);
        chunk.putInt("xPos", 0);
        chunk.putInt("zPos", 0);
        chunk.putInt("yPos", -4);
        chunk.putString("Status", "minecraft:full");
        chunk.put("sections", new ListTag());
        ListTag blockEntities = new ListTag();
        blockEntities.add(aged(helper.getBlockEntity(cratePos, BlockEntity.class).saveWithFullMetadata(registries)));
        blockEntities.add(aged(helper.getBlockEntity(chestPos, BlockEntity.class).saveWithFullMetadata(registries)));
        chunk.put("block_entities", blockEntities);
        check(helper, blockEntities.getCompoundOrEmpty(0).toString().contains("ocean_explorer_map"), "the crate is saved as 26.2 saved it");

        // the game's fixer alone, as before: the chest's barrel item is updated, our crate's items are not
        int now = SharedConstants.getCurrentVersion().dataVersion().version();
        CompoundTag alone = (CompoundTag) helper.getLevel().getServer().getFixerUpper()
                .update(References.CHUNK, new Dynamic<Tag>(NbtOps.INSTANCE, chunk.copy()), OLD, now).getValue();
        check(helper, alone.getListOrEmpty("block_entities").getCompoundOrEmpty(0).toString().contains("ocean_explorer_map"),
                "the game alone leaves the crate's map as 26.2 named it");

        ListTag after = fixed(helper, DataFixTypes.CHUNK, chunk).getListOrEmpty("block_entities");
        BlockState crateState = helper.getBlockState(cratePos);
        var reloaded = (CrateBlockEntity) BlockEntity.loadStatic(helper.absolutePos(cratePos), crateState, after.getCompoundOrEmpty(0), registries);
        check(helper, reloaded != null, "the crate reads back");
        check(helper, ItemStack.matches(reloaded.getItem(0), map), "the explorer map in the crate is an ocean monument map: " + reloaded.getItem(0));
        check(helper, ItemStack.matches(reloaded.getItem(1), pot()), "the pot in the crate keeps its sherd: " + reloaded.getItem(1).getComponentsPatch());
        check(helper, ItemStack.matches(reloaded.getItem(2), bottle), "the allay in the bottle keeps its pot: "
                + reloaded.getItem(2).get(ModDataComponents.CAPTURED_DATA.get()));

        var chest = (ChestBlockEntity) BlockEntity.loadStatic(helper.absolutePos(chestPos), helper.getBlockState(chestPos), after.getCompoundOrEmpty(1), registries);
        check(helper, chest != null && sameContents(chest.getItem(0), barrel), "the barrel item in the chest holds its map and pot: "
                + (chest == null ? null : chest.getItem(0).get(ModDataComponents.STORAGE.get())));
        helper.succeed();
    }

    /** Whether two barrel items hold the same: slot by slot, the same item (data and all) and count. */
    private static boolean sameContents(ItemStack a, ItemStack b) {
        if (!a.is(b.getItem())) {
            return false;
        }
        List<StorageContents.Entry> x = a.getOrDefault(ModDataComponents.STORAGE.get(), StorageContents.EMPTY).entries();
        List<StorageContents.Entry> y = b.getOrDefault(ModDataComponents.STORAGE.get(), StorageContents.EMPTY).entries();
        if (x.size() != y.size()) {
            return false;
        }
        for (int i = 0; i < x.size(); i++) {
            if (x.get(i).slot() != y.get(i).slot() || x.get(i).count() != y.get(i).count() || !ItemStack.matches(x.get(i).item(), y.get(i).item())) {
                return false;
            }
        }
        return true;
    }

    /**
     * An entity chunk's rope-hung map, and a player's barrel item, in 26.2's way: both read in 26.3. The player's own
     * items the game updates; the barrel's contents and our entity's item it does not.
     */
    private static void oldItemsOnEntitiesAndPlayers(GameTestHelper helper) {
        if (!runs(helper)) {
            return;
        }
        var registries = helper.getLevel().registryAccess();
        ItemStack map = new ItemStack(Items.OCEAN_MONUMENT_MAP);
        CompoundTag mapTag = (CompoundTag) ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), map).getOrThrow();

        CompoundTag hung = new CompoundTag();
        hung.putString("id", "nookworks:hung_item");
        hung.put("item", aged(mapTag.copy()));
        CompoundTag entities = new CompoundTag();
        entities.putInt("DataVersion", OLD);
        entities.putIntArray("Position", new int[] {0, 0});
        ListTag list = new ListTag();
        list.add(hung);
        entities.put("Entities", list);
        CompoundTag hungAfter = fixed(helper, DataFixTypes.ENTITY_CHUNK, entities).getListOrEmpty("Entities").getCompoundOrEmpty(0);
        check(helper, hungAfter.getCompoundOrEmpty("item").getStringOr("id", "").equals("minecraft:ocean_monument_map"),
                "the map hung on a rope is an ocean monument map: " + hungAfter);

        ItemStack barrel = new ItemStack(ModItems.IRON_STORAGE.get());
        barrel.set(ModDataComponents.STORAGE.get(), new StorageContents(List.of(new StorageContents.Entry(0, map.copy(), 3))));
        CompoundTag held = (CompoundTag) ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), barrel).getOrThrow();
        held.putByte("Slot", (byte) 0);
        CompoundTag player = new CompoundTag();
        player.putInt("DataVersion", OLD);
        ListTag inventory = new ListTag();
        inventory.add(aged(held));
        inventory.add(aged(mapTag.copy()));
        inventory.getCompoundOrEmpty(1).putByte("Slot", (byte) 1);
        player.put("Inventory", inventory);
        ListTag after = fixed(helper, DataFixTypes.PLAYER, player).getListOrEmpty("Inventory");
        check(helper, after.getCompoundOrEmpty(1).getStringOr("id", "").equals("minecraft:ocean_monument_map"), "the game updates the player's own map");
        CompoundTag barrelAfter = after.getCompoundOrEmpty(0).copy();
        barrelAfter.remove("Slot");
        ItemStack read = ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), barrelAfter).result().orElse(ItemStack.EMPTY);
        check(helper, sameContents(read, barrel), "the barrel in the player's hands still holds its map: " + barrelAfter);
        helper.succeed();
    }

    /**
     * An entity chunk of 26.2 holding an item frame (an explorer map in it) beside a rope-hung thing of ours and another
     * mod's entity: the frame is updated as it is alone, ours too, the other mod's left as it was, nothing lost (r15 fix
     * check: one entity of an unknown kind in the list and the game updated none of them).
     */
    private static void oldVanillaEntitiesBesideOurs(GameTestHelper helper) {
        if (!runs(helper)) {
            return;
        }
        var registries = helper.getLevel().registryAccess();
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        CompoundTag map = (CompoundTag) ItemStack.CODEC.encodeStart(ops, new ItemStack(Items.OCEAN_MONUMENT_MAP)).getOrThrow();
        CompoundTag frame = new CompoundTag();
        frame.putString("id", "minecraft:item_frame");
        frame.put("Item", aged(map.copy()));
        CompoundTag hung = new CompoundTag();
        hung.putString("id", "nookworks:hung_item");
        hung.put("item", aged(map.copy()));
        CompoundTag other = new CompoundTag();
        other.putString("id", "othermod:thing");
        other.putString("note", "left as it is");
        CompoundTag stand = new CompoundTag(); // a vanilla mount with one of ours riding it, and a vanilla rider after
        stand.putString("id", "minecraft:armor_stand");
        ListTag riders = new ListTag();
        CompoundTag seat = new CompoundTag();
        seat.putString("id", "nookworks:seat");
        riders.add(seat);
        CompoundTag riderFrame = frame.copy();
        riders.add(riderFrame);
        stand.put("Passengers", riders);
        CompoundTag chunk = new CompoundTag();
        chunk.putInt("DataVersion", OLD);
        chunk.putIntArray("Position", new int[] {0, 0});
        ListTag entities = new ListTag();
        entities.add(frame);
        entities.add(hung);
        entities.add(other);
        entities.add(stand);
        chunk.put("Entities", entities);
        ListTag after = fixed(helper, DataFixTypes.ENTITY_CHUNK, chunk).getListOrEmpty("Entities");
        check(helper, after.size() == 4, "every entity is still there: " + after.size());
        String monument = "minecraft:ocean_monument_map";
        boolean frameDone = false;
        boolean hungDone = false;
        boolean otherKept = false;
        boolean riderDone = false;
        for (Tag tag : after) {
            CompoundTag entity = (CompoundTag) tag;
            String id = entity.getStringOr("id", "");
            if (id.equals("minecraft:item_frame")) {
                frameDone = entity.getCompoundOrEmpty("Item").getStringOr("id", "").equals(monument);
            } else if (id.equals("nookworks:hung_item")) {
                hungDone = entity.getCompoundOrEmpty("item").getStringOr("id", "").equals(monument);
            } else if (id.equals("othermod:thing")) {
                otherKept = entity.getStringOr("note", "").equals("left as it is");
            } else if (id.equals("minecraft:armor_stand")) {
                riderDone = entity.getListOrEmpty("Passengers").stream().anyMatch(r -> r instanceof CompoundTag c
                        && c.getStringOr("id", "").equals("minecraft:item_frame") && c.getCompoundOrEmpty("Item").getStringOr("id", "").equals(monument));
            }
        }
        check(helper, frameDone, "the item frame's explorer map is an ocean monument map: " + after);
        check(helper, hungDone, "the rope-hung one too");
        check(helper, otherKept, "the other mod's entity is as it was");
        check(helper, riderDone, "a vanilla rider beside one of ours, on a vanilla mount, is updated too");
        helper.succeed();
    }

    /**
     * A Mob Bottle holding an enderman with a block, from 26.2: the block's state, written in 26.2's way, reads in 26.3
     * (r15 fix check: only the mob's items were updated, and the block was lost on letting it out).
     */
    private static void oldMobInABottle(GameTestHelper helper) {
        if (!runs(helper)) {
            return;
        }
        var registries = helper.getLevel().registryAccess();
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        var enderman = helper.spawnWithNoFreeWill(EntityTypes.ENDERMAN, new BlockPos(2, 1, 2));
        enderman.setCarriedBlock(Blocks.GRASS_BLOCK.defaultBlockState());
        ItemStack bottle = MobBottleItem.filled(enderman);
        enderman.discard();
        CompoundTag held = (CompoundTag) ItemStack.CODEC.encodeStart(ops, bottle).getOrThrow();
        // 26.2 wrote a block state as {Name, Properties}
        CompoundTag mob = held.getCompoundOrEmpty("components").getCompoundOrEmpty("nookworks:captured_data");
        check(helper, mob.get("carriedBlockState") != null, "the bottled enderman carries a block: " + mob);
        CompoundTag old = new CompoundTag();
        old.putString("Name", "minecraft:grass_block");
        CompoundTag properties = new CompoundTag();
        properties.putString("snowy", "false");
        old.put("Properties", properties);
        mob.put("carriedBlockState", old);
        held.putByte("Slot", (byte) 0);
        CompoundTag player = new CompoundTag();
        player.putInt("DataVersion", OLD);
        ListTag inventory = new ListTag();
        inventory.add(held);
        player.put("Inventory", inventory);
        CompoundTag after = fixed(helper, DataFixTypes.PLAYER, player).getListOrEmpty("Inventory").getCompoundOrEmpty(0);
        after.remove("Slot");
        Tag carried = after.getCompoundOrEmpty("components").getCompoundOrEmpty("nookworks:captured_data").get("carriedBlockState");
        var state = net.minecraft.world.level.block.state.BlockState.CODEC.parse(ops, carried).result();
        check(helper, state.isPresent() && state.get() == Blocks.GRASS_BLOCK.defaultBlockState(),
                "the bottled enderman's block reads as a grass block again: " + carried);
        helper.succeed();
    }

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
        player.snapTo(near.x, near.y, near.z);
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
