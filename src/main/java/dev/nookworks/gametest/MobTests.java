package dev.nookworks.gametest;

import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.util.RandomSource;
import net.minecraft.core.registries.Registries;
import dev.nookworks.item.WoolShearsItem;
import static dev.nookworks.gametest.MachineTests.placeByHand;
import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.mobcapture.SpawnEggBrewerBlockEntity;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/** The Mob Bottle and the Spawn Egg Brewer; the Wool Shears. */
final class MobTests {
    static void register() {
        test("mob_bottle_captures_weak_mob", MobTests::bottleCapturesWeakMob);
        test("mob_bottle_refuses_healthy_mob", MobTests::bottleRefusesHealthyMob);
        test("mob_bottle_releases_mob", MobTests::bottleReleasesMob);
        test("mob_bottle_leaves_pets_alone", MobTests::bottleLeavesPetsAlone);
        test("mob_bottle_drops_the_lead", MobTests::bottleDropsTheLead);
        test("spawn_egg_brewer_makes_egg", MobTests::brewerMakesSpawnEgg);
        test("spawn_egg_brewer_hopper_sides", MobTests::brewerHopperSides);
        test("spawn_egg_brewer_gives_back_what_the_mob_carried", MobTests::brewerGivesBackCarried);
        test("spawn_egg_brewer_keeps_its_powder_when_moved", MobTests::brewerKeepsPowder);
        test("wool_shears_shear_a_sheep_and_leave_its_lead", MobTests::woolShearsKeepTheLead);
        test("wool_shears_give_a_wool_more_now_and_then", MobTests::woolShearsBonus);
        test("wool_shears_work_in_a_dispenser", 40, MobTests::woolShearsDispensed);
        test("wool_shears_take_fortune", MobTests::woolShearsEnchantments);
    }

    // ---- Wool Shears ----

    /** White wool lying in the test's area, counted. */
    private static int woolLying(GameTestHelper helper) {
        return helper.getEntities(EntityTypes.ITEM).stream().filter(item -> item.getItem().is(Items.WOOL.white()))
                .mapToInt(item -> item.getItem().getCount()).sum();
    }

    /**
     * A click with them on a sheep the player leads shears it and leaves the lead on - through the game's own way in, the
     * click on the entity - where vanilla's shears cut the lead and leave the wool (1.21.6 on).
     */
    static void woolShearsKeepTheLead(GameTestHelper helper) {
        @SuppressWarnings("removal")
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Sheep sheep = helper.spawnWithNoFreeWill(EntityTypes.SHEEP, new BlockPos(3, 1, 3));
        sheep.setColor(DyeColor.WHITE);
        sheep.setLeashedTo(player, true);
        ItemStack shears = new ItemStack(ModItems.WOOL_SHEARS.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, shears);
        InteractionResult result = player.interactOn(sheep, InteractionHand.MAIN_HAND, sheep.position());
        check(helper, result.consumesAction(), "the click is taken: " + result);
        check(helper, sheep.isSheared(), "the sheep is shorn");
        check(helper, sheep.isLeashed() && sheep.getLeashHolder() == player, "and still on its lead");
        check(helper, shears.getDamageValue() == 1, "the shears wear one: " + shears.getDamageValue());
        int wool = woolLying(helper);
        check(helper, wool >= 1 && wool <= 4, "its wool lies there: " + wool);
        check(helper, helper.getEntities(EntityTypes.ITEM).stream().noneMatch(item -> item.getItem().is(Items.LEAD)), "no lead dropped");
        check(helper, !player.interactOn(sheep, InteractionHand.MAIN_HAND, sheep.position()).consumesAction() || !sheep.isLeashed() || sheep.isSheared(),
                "a shorn sheep is not shorn again");
        // vanilla's, for the record: the lead goes, the wool stays
        Sheep other = helper.spawnWithNoFreeWill(EntityTypes.SHEEP, new BlockPos(1, 1, 1));
        other.setLeashedTo(player, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
        player.interactOn(other, InteractionHand.MAIN_HAND, other.position());
        check(helper, !other.isLeashed() && !other.isSheared(), "vanilla's shears cut the lead first");
        helper.succeed();
    }

    /** A wool more one time in three, and Fortune a roll more a level: about a third of a wool without, one and a third at III. */
    static void woolShearsBonus(GameTestHelper helper) {
        RandomSource random = RandomSource.create(20261004L);
        int runs = 6000;
        double plain = 0;
        double fortune = 0;
        for (int i = 0; i < runs; i++) {
            plain += WoolShearsItem.bonus(random, 0);
            fortune += WoolShearsItem.bonus(random, 3);
        }
        plain /= runs;
        fortune /= runs;
        check(helper, Math.abs(plain - 1 / 3.0) < 0.03, "a third of a wool more without Fortune: " + plain);
        check(helper, Math.abs(fortune - 4 / 3.0) < 0.06, "about one and a third with Fortune III: " + fortune);
        helper.succeed();
    }

    /** A dispenser holding them, powered, shears the sheep in front of it - tied to a post - and leaves its lead on. */
    static void woolShearsDispensed(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 2);
        helper.setBlock(at.east(2), Blocks.OAK_FENCE);
        LeashFenceKnotEntity post = LeashFenceKnotEntity.getOrCreateKnot(helper.getLevel(), helper.absolutePos(at.east(2)));
        helper.setBlock(at, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        ((DispenserBlockEntity) helper.getBlockEntity(at, DispenserBlockEntity.class)).setItem(0, new ItemStack(ModItems.WOOL_SHEARS.get()));
        Sheep sheep = helper.spawnWithNoFreeWill(EntityTypes.SHEEP, at.east());
        sheep.setColor(DyeColor.WHITE);
        sheep.setLeashedTo(post, true);
        helper.setBlock(at.west(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(10, () -> {
            check(helper, sheep.isSheared(), "the sheep in front is shorn");
            check(helper, sheep.isLeashed(), "its lead left on");
            ItemStack inside = helper.getBlockEntity(at, DispenserBlockEntity.class).getItem(0);
            check(helper, inside.is(ModItems.WOOL_SHEARS.get()) && inside.getDamageValue() == 1, "the shears stay in it, worn one: " + inside);
            check(helper, woolLying(helper) >= 1, "its wool lies there");
            helper.succeed();
        });
    }

    /** Fortune goes on them (and Unbreaking and Mending, as on any tool that wears); Silk Touch does not, nor Fortune on vanilla's shears. */
    static void woolShearsEnchantments(GameTestHelper helper) {
        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ItemStack shears = new ItemStack(ModItems.WOOL_SHEARS.get());
        check(helper, shears.supportsEnchantment(enchantments.getOrThrow(Enchantments.FORTUNE)), "Fortune");
        check(helper, shears.isPrimaryItemFor(enchantments.getOrThrow(Enchantments.FORTUNE)), "Fortune at the enchanting table");
        check(helper, shears.supportsEnchantment(enchantments.getOrThrow(Enchantments.UNBREAKING)), "Unbreaking");
        check(helper, shears.supportsEnchantment(enchantments.getOrThrow(Enchantments.MENDING)), "Mending");
        check(helper, !shears.supportsEnchantment(enchantments.getOrThrow(Enchantments.SILK_TOUCH)), "not Silk Touch");
        check(helper, !new ItemStack(Items.SHEARS).supportsEnchantment(enchantments.getOrThrow(Enchantments.FORTUNE)), "vanilla's shears take no Fortune");
        helper.succeed();
    }

    // ---- Mob Bottle and Spawn Egg Brewer ----
    static Zombie weakZombie(GameTestHelper helper, float healthFraction) {
        var zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(3, 1, 3));
        zombie.setHealth(zombie.getMaxHealth() * healthFraction);
        return zombie;
    }

    static void bottleCapturesWeakMob(GameTestHelper helper) {
        var zombie = weakZombie(helper, 0.5F);
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MOB_BOTTLE.get(), 3));
        check(helper, MobBottleItem.tryCapture(player, InteractionHand.MAIN_HAND, zombie).consumesAction(), "half health: caught");
        check(helper, zombie.isRemoved(), "the zombie is gone");
        check(helper, player.getMainHandItem().getCount() == 2, "one empty bottle used");
        check(helper, player.getInventory().getNonEquipmentItems().stream().anyMatch(s -> MobBottleItem.captured(s) == EntityTypes.ZOMBIE),
                "a zombie bottle is in the inventory");
        helper.succeed();
    }

    static void bottleRefusesHealthyMob(GameTestHelper helper) {
        var zombie = weakZombie(helper, 0.75F);
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MOB_BOTTLE.get()));
        MobBottleItem.tryCapture(player, InteractionHand.MAIN_HAND, zombie);
        check(helper, !zombie.isRemoved(), "a healthy zombie stays out");
        check(helper, MobBottleItem.check(player) == MobBottleItem.Refusal.NOT_CAPTURABLE, "players can never be caught");
        check(helper, MobBottleItem.captured(player.getMainHandItem()) == null, "the bottle stays empty");
        helper.succeed();
    }

    static void bottleLeavesPetsAlone(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        var wolf = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new BlockPos(3, 1, 3));
        wolf.tame(player);
        wolf.setHealth(1.0F);
        check(helper, MobBottleItem.check(wolf) == MobBottleItem.Refusal.OWNED, "a tamed wolf is refused");
        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MOB_BOTTLE.get()));
        MobBottleItem.tryCapture(stranger, InteractionHand.MAIN_HAND, wolf);
        check(helper, !wolf.isRemoved(), "by anyone but its owner, and stays where it is");
        var stray = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new BlockPos(1, 1, 1));
        stray.setHealth(1.0F);
        check(helper, MobBottleItem.check(stray) == MobBottleItem.Refusal.NONE, "a wild wolf can still be caught");
        helper.succeed();
    }

    static void bottleDropsTheLead(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        var pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(3, 1, 3));
        pig.setHealth(1.0F);
        pig.setLeashedTo(player, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MOB_BOTTLE.get()));
        check(helper, MobBottleItem.tryCapture(player, InteractionHand.MAIN_HAND, pig).consumesAction(), "the pig is caught");
        helper.succeedWhen(() -> helper.assertItemEntityPresent(Items.LEAD, new BlockPos(3, 1, 3), 3.0));
    }

    static void bottleReleasesMob(GameTestHelper helper) {
        ItemStack bottle = MobBottleItem.filled(EntityTypes.PIG);
        check(helper, bottle.getMaxStackSize() == 1, "filled bottles do not stack");
        placeByHand(helper, bottle, new BlockPos(3, 1, 3));
        check(helper, MobBottleItem.captured(bottle) == null, "the bottle is empty after release");
        check(helper, bottle.getMaxStackSize() == 16, "and stacks like an empty bottle again");
        check(helper, pigsNear(helper, new BlockPos(3, 1, 3)) == 1, "one pig was released");
        helper.succeed();
    }

    static int pigsNear(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntities(EntityTypes.PIG, new AABB(helper.absolutePos(pos)).inflate(3.0), e -> true).size();
    }

    static void brewerMakesSpawnEgg(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.SPAWN_EGG_BREWER.get());
        SpawnEggBrewerBlockEntity brewer = helper.getBlockEntity(pos, SpawnEggBrewerBlockEntity.class);
        ItemStack bottle = MobBottleItem.filled(EntityTypes.ZOMBIE);
        bottle.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Strong"));
        brewer.setItem(SpawnEggBrewerBlockEntity.BOTTLE, bottle);
        brewer.setItem(SpawnEggBrewerBlockEntity.EGG, new ItemStack(Items.EGG, 2));
        brewer.setItem(SpawnEggBrewerBlockEntity.FUEL, new ItemStack(Items.BLAZE_POWDER));
        for (int i = 0; i < SpawnEggBrewerBlockEntity.BREW_TIME; i++) {
            SpawnEggBrewerBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), brewer);
        }
        check(helper, brewer.getItem(SpawnEggBrewerBlockEntity.OUTPUT).is(Items.ZOMBIE_SPAWN_EGG), "a zombie spawn egg is brewed");
        ItemStack left = brewer.getItem(SpawnEggBrewerBlockEntity.BOTTLE);
        check(helper, left.is(ModItems.MOB_BOTTLE.get()) && MobBottleItem.captured(left) == null && left.getCount() == 1,
                "the bottle comes back empty");
        check(helper, "Strong".equals(left.getHoverName().getString()) && left.getMaxStackSize() == 16,
                "keeping its own components (an add-on's stronger bottle stays one), and stacking again");
        check(helper, brewer.getItem(SpawnEggBrewerBlockEntity.EGG).getCount() == 1, "one egg is used");
        check(helper, brewer.getItem(SpawnEggBrewerBlockEntity.FUEL).isEmpty()
                && brewer.fuel() == SpawnEggBrewerBlockEntity.BREWS_PER_FUEL - 1, "one blaze powder gives several brews");
        for (int i = 0; i < 20; i++) {
            SpawnEggBrewerBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), brewer);
        }
        check(helper, brewer.progress() == 0, "an empty bottle brews nothing");
        helper.succeed();
    }

    /**
     * A bottled mob's own things fall on the brewer as the egg is made, as they would had it died: a zombie's picked-up
     * helmet, an allay's item. The gear it was born with (a death's small chance) and its loot do not.
     */
    static void brewerGivesBackCarried(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.SPAWN_EGG_BREWER.get());
        SpawnEggBrewerBlockEntity brewer = helper.getBlockEntity(pos, SpawnEggBrewerBlockEntity.class);
        var zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
        zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        zombie.setGuaranteedDrop(net.minecraft.world.entity.EquipmentSlot.HEAD);
        zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        zombie.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);
        var allay = helper.spawn(EntityTypes.ALLAY, new BlockPos(5, 2, 1));
        allay.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.EMERALD, 3));
        var piglin = helper.spawn(EntityTypes.PIGLIN, new BlockPos(1, 2, 5));
        piglin.getInventory().addItem(new ItemStack(Items.GOLD_INGOT, 2));
        java.util.List<ItemStack> bottles = java.util.List.of(MobBottleItem.filled(zombie), MobBottleItem.filled(allay),
                MobBottleItem.filled(piglin));
        zombie.discard();
        allay.discard();
        piglin.discard();
        brewer.setItem(SpawnEggBrewerBlockEntity.EGG, new ItemStack(Items.EGG, 3));
        brewer.setItem(SpawnEggBrewerBlockEntity.FUEL, new ItemStack(Items.BLAZE_POWDER));
        for (ItemStack bottle : bottles) {
            brewer.setItem(SpawnEggBrewerBlockEntity.BOTTLE, bottle);
            brewer.setItem(SpawnEggBrewerBlockEntity.OUTPUT, ItemStack.EMPTY);
            for (int i = 0; i < SpawnEggBrewerBlockEntity.BREW_TIME; i++) {
                SpawnEggBrewerBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), brewer);
            }
        }
        var dropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(2.0), e -> true).stream().map(e -> e.getItem()).toList();
        check(helper, dropped.stream().anyMatch(s -> s.is(Items.DIAMOND_HELMET)), "the zombie's picked-up helmet falls: " + dropped);
        check(helper, dropped.stream().anyMatch(s -> s.is(Items.EMERALD) && s.getCount() == 3), "the allay's emeralds fall: " + dropped);
        check(helper, dropped.stream().anyMatch(s -> s.is(Items.GOLD_INGOT) && s.getCount() == 2), "the piglin's pocketed gold falls: " + dropped);
        check(helper, dropped.stream().noneMatch(s -> s.is(Items.IRON_SWORD) || s.is(Items.ROTTEN_FLESH)),
                "not the gear it was born with, nor its loot: " + dropped);
        helper.succeed();
    }

    /** Broken after a brew, a brewer keeps the blaze powder it took in, in its item, and has it again when set down. */
    static void brewerKeepsPowder(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.SPAWN_EGG_BREWER.get());
        SpawnEggBrewerBlockEntity brewer = helper.getBlockEntity(pos, SpawnEggBrewerBlockEntity.class);
        brewer.setItem(SpawnEggBrewerBlockEntity.BOTTLE, MobBottleItem.filled(EntityTypes.PIG));
        brewer.setItem(SpawnEggBrewerBlockEntity.EGG, new ItemStack(Items.EGG));
        brewer.setItem(SpawnEggBrewerBlockEntity.FUEL, new ItemStack(Items.BLAZE_POWDER));
        for (int i = 0; i < SpawnEggBrewerBlockEntity.BREW_TIME; i++) {
            SpawnEggBrewerBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), brewer);
        }
        int left = SpawnEggBrewerBlockEntity.BREWS_PER_FUEL - 1;
        check(helper, brewer.fuel() == left, "one brew used of the powder's " + SpawnEggBrewerBlockEntity.BREWS_PER_FUEL);
        var drops = net.minecraft.world.level.block.Block.getDrops(helper.getBlockState(pos), helper.getLevel(),
                helper.absolutePos(pos), brewer);
        check(helper, drops.size() == 1 && Integer.valueOf(left).equals(drops.get(0).get(dev.nookworks.registry.ModDataComponents.BREWS_LEFT.get())),
                "its item keeps the brews left: " + drops);
        BlockPos other = new BlockPos(5, 1, 3);
        helper.setBlock(other, ModBlocks.SPAWN_EGG_BREWER.get());
        SpawnEggBrewerBlockEntity again = helper.getBlockEntity(other, SpawnEggBrewerBlockEntity.class);
        again.applyComponentsFromItemStack(drops.get(0));
        check(helper, again.fuel() == left, "set down again, it has them: " + again.fuel());
        helper.succeed();
    }

    static void brewerHopperSides(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.SPAWN_EGG_BREWER.get());
        SpawnEggBrewerBlockEntity brewer = helper.getBlockEntity(pos, SpawnEggBrewerBlockEntity.class);
        check(helper, brewer.canPlaceItemThroughFace(SpawnEggBrewerBlockEntity.BOTTLE, MobBottleItem.filled(EntityTypes.PIG), Direction.UP), "filled bottles in");
        check(helper, !brewer.canPlaceItem(SpawnEggBrewerBlockEntity.BOTTLE, new ItemStack(ModItems.MOB_BOTTLE.get())), "empty bottles are refused");
        check(helper, !brewer.canPlaceItem(SpawnEggBrewerBlockEntity.EGG, new ItemStack(Items.WHEAT_SEEDS)), "only eggs in the egg slot");
        check(helper, brewer.canTakeItemThroughFace(SpawnEggBrewerBlockEntity.OUTPUT, new ItemStack(Items.PIG_SPAWN_EGG), Direction.DOWN)
                && !brewer.canTakeItemThroughFace(SpawnEggBrewerBlockEntity.EGG, new ItemStack(Items.EGG), Direction.DOWN), "eggs stay in");
        check(helper, brewer.canTakeItemThroughFace(SpawnEggBrewerBlockEntity.BOTTLE, new ItemStack(ModItems.MOB_BOTTLE.get()), Direction.DOWN)
                && !brewer.canTakeItemThroughFace(SpawnEggBrewerBlockEntity.BOTTLE, MobBottleItem.filled(EntityTypes.PIG), Direction.DOWN),
                "an emptied bottle comes out below, a full one does not");
        helper.succeed();
    }

    private MobTests() {}
}
