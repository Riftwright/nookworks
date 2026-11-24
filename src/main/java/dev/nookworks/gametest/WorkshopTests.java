package dev.nookworks.gametest;

import static dev.nookworks.gametest.MiningTests.registerVillagePiece;
import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dev.nookworks.block.MotorBlock;
import dev.nookworks.block.StorageBlock;
import dev.nookworks.client.ShaderAliases;
import dev.nookworks.feature.VillageMap;
import dev.nookworks.cooking.GrillBlock;
import dev.nookworks.cooking.IronPotBlock;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
import dev.nookworks.registry.ModOil;
import dev.nookworks.registry.ModVentilation;
import dev.nookworks.registry.ModWoods;
import dev.nookworks.storage.StorageBlockEntity;
import dev.nookworks.storage.StorageContents;
import dev.nookworks.storage.StorageInventory;
import dev.nookworks.storage.StorageTier;
import dev.nookworks.ventilation.ChimneyBlock;
import dev.nookworks.ventilation.DuctBlock;
import dev.nookworks.ventilation.VentFanBlock;
import dev.nookworks.ventilation.Ventilation;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import dev.nookworks.block.PipeSides;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemUtil;

/** The storage upgrades, the wandering trader's odd offer of ours, the shader packs' swaying plants, and the smoke. */
final class WorkshopTests {
    static void register() {
        NookworksGameTests.test("storage_upgrades_turn_a_barrel_up_a_tier_where_it_stands", WorkshopTests::storageUpgrades);
        NookworksGameTests.test("the_wandering_trader_now_and_then_sells_one_of_ours", WorkshopTests::wanderingTrader);
        NookworksGameTests.test("the_wandering_trader_has_a_village_map_for_one_who_never_found_a_village", 400, WorkshopTests::villageMap);
        NookworksGameTests.test("shader_packs_sway_our_leaves_as_oak", WorkshopTests::shaderAliases);
        NookworksGameTests.test("smoke_goes_up_the_hood_and_out_of_the_chimney", WorkshopTests::smoke);
        NookworksGameTests.test("smoke_goes_up_through_a_flue_in_the_roof", WorkshopTests::smokeThroughFlue);
        NookworksGameTests.test("smoke_goes_in_only_at_a_mouth", WorkshopTests::smokeOnlyAtAMouth);
        NookworksGameTests.test("smoke_ducts_and_chimneys_parted_with_the_wrench", WorkshopTests::ductsPartedWithTheWrench);
        NookworksGameTests.test("motor_shows_a_socket_where_a_cable_joins_its_side", WorkshopTests::motorSockets);
        NookworksGameTests.test("motor_set_down_points_its_shaft_at_what_it_drives", WorkshopTests::motorShaftWay);
    }

    private static void use(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos at = helper.absolutePos(pos);
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
    }

    /**
     * An Iron Storage Upgrade used on a full wooden barrel makes it an Iron Storage Barrel where it stands, facing as it
     * did, everything in the same slots; a Golden one takes that to gold. One for another tier does nothing and is kept.
     * The kits' recipes cost what the tier recipes add, so neither way is cheaper.
     */
    private static void storageUpgrades(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.EAST));
        var barrel = (BarrelBlockEntity) helper.getBlockEntity(pos, BarrelBlockEntity.class);
        barrel.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        barrel.setItem(5, new ItemStack(Items.DIAMOND, 3));
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        ItemStack iron = new ItemStack(ModItems.IRON_STORAGE_UPGRADE.get());
        use(helper, player, iron, pos);
        var state = helper.getBlockState(pos);
        check(helper, state.is(ModBlocks.IRON_STORAGE.get()) && state.getValue(StorageBlock.FACING) == Direction.EAST,
                "the wooden barrel is an Iron Storage Barrel, facing as it did: " + state);
        var storage = helper.getBlockEntity(pos, StorageBlockEntity.class);
        check(helper, storage.inventory().getAmountAsInt(0) == 64 && storage.inventory().getResource(0).is(Items.COBBLESTONE)
                && storage.inventory().getAmountAsInt(5) == 3 && storage.inventory().getResource(5).is(Items.DIAMOND),
                "everything in it, in the same slots");
        check(helper, iron.isEmpty(), "the upgrade is used up");
        ItemStack wrong = new ItemStack(ModItems.VELARIUM_STORAGE_UPGRADE.get());
        use(helper, player, wrong, pos);
        check(helper, helper.getBlockState(pos).is(ModBlocks.IRON_STORAGE.get()) && wrong.getCount() == 1, "another tier's upgrade does nothing");
        ItemUtil.insertItemReturnRemaining(storage.inventory(), new ItemStack(Items.COBBLESTONE), false, null);
        long put = storage.inventory().lastAdded(0);
        check(helper, put > 0 && storage.inventory().getAmountAsInt(0) == 65, "cobblestone put in is timed: " + put);
        ItemStack gold = new ItemStack(ModItems.GOLD_STORAGE_UPGRADE.get());
        use(helper, player, gold, pos);
        var golden = helper.getBlockEntity(pos, StorageBlockEntity.class).inventory();
        check(helper, helper.getBlockState(pos).is(ModBlocks.GOLD_STORAGE.get()) && golden.getAmountAsInt(0) == 65, "then gold, still full");
        // "last put in first" still knows the cobblestone went in last, and so it does after the barrel is carried as an item
        check(helper, golden.lastAdded(0) == put, "the cobblestone keeps its time through the upgrade: " + golden.lastAdded(0));
        var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var saved = StorageContents.CODEC.encodeStart(ops, golden.toContents()).getOrThrow();
        var carried = new StorageInventory(StorageTier.GOLD, () -> {}, () -> 0L);
        carried.load(StorageContents.CODEC.parse(ops, saved).getOrThrow());
        check(helper, carried.lastAdded(0) == put && carried.getAmountAsInt(0) == 65, "and as an item's contents");
        var recipes = helper.getLevel().getServer().getRecipeManager();
        for (String id : new String[] {"iron_storage_upgrade", "gold_storage_upgrade", "diamond_storage_upgrade", "netherite_storage_upgrade",
                "velarium_storage_upgrade", "gold_storage_barrel_from_upgrade"}) {
            check(helper, recipes.byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse("nookworks:" + id))).isPresent(), id + " is loaded");
        }
        helper.succeed();
    }

    /**
     * The trade of ours is one entry among the wandering trader's uncommon ones - so at most one of his offers is ever
     * ours - and it is one of a few of our things, each at its own price.
     */
    private static void wanderingTrader(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var trades = level.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE);
        var ours = trades.get(ResourceKey.create(Registries.VILLAGER_TRADE, Identifier.parse("nookworks:wandering_trader/nookworks_goods")));
        check(helper, ours.isPresent(), "the trade loads");
        TagKey<VillagerTrade> uncommon = TagKey.create(Registries.VILLAGER_TRADE,
                Identifier.withDefaultNamespace("wandering_trader/uncommon"));
        check(helper, ours.get().is(uncommon), "among the trader's uncommon offers");
        long inCommon = trades.getOrThrow(TagKey.create(Registries.VILLAGER_TRADE, Identifier.withDefaultNamespace("wandering_trader/common")))
                .stream().filter(h -> h.unwrapKey().map(k -> k.identifier().getNamespace().equals("nookworks")).orElse(false)).count();
        check(helper, inCommon == 0, "and nowhere else, so there is only ever the one");
        var trader = new WanderingTrader(
                EntityTypes.WANDERING_TRADER, level);
        trader.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
        var context = new LootContext.Builder(
                new LootParams.Builder(level)
                        .withParameter(LootContextParams.ORIGIN, trader.position())
                        .withParameter(LootContextParams.THIS_ENTITY, trader)
                        .withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED,
                                Unit.INSTANCE)
                        .create(LootContextParamSets.VILLAGER_TRADE))
                .create(Optional.empty());
        Set<Item> seen = new HashSet<>();
        for (int i = 0; i < 80; i++) {
            var offer = ours.get().value().getOffer(context);
            check(helper, offer != null, "an offer");
            ItemStack result = offer.getResult();
            check(helper, result.typeHolder().unwrapKey().map(k -> k.identifier().getNamespace().equals("nookworks")).orElse(false),
                    "one of ours: " + result);
            check(helper, offer.getBaseCostA().is(Items.EMERALD) && offer.getBaseCostA().getCount() >= 2, "for emeralds: " + offer.getBaseCostA());
            seen.add(result.getItem());
        }
        check(helper, seen.size() >= 4, "not always the same thing: " + seen);
        helper.succeed();
    }

    /**
     * A player who has never been in a village: one trader in two has a map to the nearest one, for three emeralds and
     * a compass, decided once per trader; a player who has been in a village gets none. The map's house and name are
     * the village's kind. The test world makes no structures (its world options say so), so no village is ever in reach:
     * the map is never made, and the trade is dropped as in a world with no village near. That is checked as such, so
     * the day the test world has one the test says so instead of passing over it; the marks and names of each kind are
     * checked without one, below.
     */
    private static void villageMap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var visited = level.getServer().getAdvancements().get(VillageMap.VISITED_VILLAGE);
        check(helper, visited != null && visited.value().display().isEmpty(), "the hidden advancement that notes a village is there, unseen");
        check(helper, level.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE).get(VillageMap.TRADE).isPresent(),
                "the trade loads");

        ServerPlayer newcomer = MockPlayers.inLevel(helper);
        check(helper, !VillageMap.hasBeenInAVillage(newcomer), "a new player has not been in a village");
        var trader = helper.spawnWithNoFreeWill(EntityTypes.WANDERING_TRADER, new BlockPos(1, 2, 1));
        var unlucky = helper.spawnWithNoFreeWill(EntityTypes.WANDERING_TRADER, new BlockPos(3, 2, 1));
        check(helper, VillageMap.offerTo(unlucky, newcomer, 0.99) == null, "the other trader in two has none");
        check(helper, VillageMap.offerTo(unlucky, newcomer, 0.0) == null, "and he does not think again");

        var offer = VillageMap.offerTo(trader, newcomer, 0.0);
        check(helper, offer == null, "no village in the test world, so no map to sell: " + offer);
        check(helper, VillageMap.offerTo(trader, newcomer, 0.0) == null, "only once per trader");

        ServerPlayer traveller = MockPlayers.inLevel(helper);
        traveller.getAdvancements().award(visited, "in_village");
        check(helper, VillageMap.hasBeenInAVillage(traveller), "been in a village");
        var third = helper.spawnWithNoFreeWill(EntityTypes.WANDERING_TRADER, new BlockPos(5, 2, 1));
        check(helper, VillageMap.offerTo(third, traveller, 0.0) == null, "who has been in a village is sold no map");

        // Standing in a village is noted: a pretend village piece round the arena, and the check made each second.
        ServerPlayer walker = MockPlayers.inLevel(helper);
        var box = BoundingBox.fromCorners(
                helper.absolutePos(new BlockPos(1, 0, 1)), helper.absolutePos(new BlockPos(5, 4, 5)));
        var piece = new StructurePiece(
                StructurePieceType.JIGSAW, 0, box) {
            @Override
            protected void addAdditionalSaveData(StructurePieceSerializationContext context,
                    CompoundTag tag) {
            }

            @Override
            public void postProcess(WorldGenLevel worldGen, StructureManager manager,
                    ChunkGenerator generator, RandomSource random,
                    BoundingBox chunkBox, ChunkPos chunkPos, BlockPos referencePos) {
            }
        };
        Runnable demolish = MiningTests.registerVillagePiece(helper, piece);
        try {
            check(helper, !VillageMap.hasBeenInAVillage(walker), "not in a village yet");
            Vec3 inside = helper.absoluteVec(new Vec3(3.5, 1, 3.5));
            walker.snapTo(inside.x, inside.y, inside.z);
            CriteriaTriggers.LOCATION.trigger(walker);
            check(helper, VillageMap.hasBeenInAVillage(walker), "standing in a village is noted");
        } finally {
            demolish.run();
        }
        level.getServer().getPlayerList().remove(walker);

        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var desert = structures.getOrThrow(ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace("village_desert")));
        check(helper, VillageMap.decoration(desert).is(MapDecorationTypes.DESERT_VILLAGE.unwrapKey().orElseThrow()),
                "a desert village gets the desert house");
        check(helper, VillageMap.name(desert).getContents() instanceof TranslatableContents t
                && t.getKey().equals("filled_map.village_desert"), "and vanilla's name for it");
        level.getServer().getPlayerList().remove(newcomer);
        level.getServer().getPlayerList().remove(traveller);
        helper.succeed();
    }

    /** Given a pack's table with oak leaves in it, Dawnwood's leaves take the oak's numbers, state for state. */
    private static void shaderAliases(GameTestHelper helper) {
        var ids = new Object2IntOpenHashMap<BlockState>();
        for (var state : Blocks.OAK_LEAVES.getStateDefinition().getPossibleStates()) {
            ids.put(state, state.getValue(LeavesBlock.WATERLOGGED) ? 10011 : 10009);
        }
        ids.put(Blocks.WHEAT.defaultBlockState(), 10020);
        var out = ShaderAliases.withAliases(ids);
        var leaves = ModWoods.DAWNWOOD_LEAVES.get().defaultBlockState();
        check(helper, out.getInt(leaves.setValue(LeavesBlock.WATERLOGGED, false)) == 10009
                && out.getInt(leaves.setValue(LeavesBlock.WATERLOGGED, true)) == 10011,
                "our leaves sway as oak leaves, dry or waterlogged");
        check(helper, out.getInt(ModKitchen.ONION_CROP.get().defaultBlockState()) == out.defaultReturnValue(),
                "a plant whose counterpart the pack does not name is left alone");
        check(helper, out.getInt(ModCrops.RICE_CROP.get().defaultBlockState()) == 10020, "rice as wheat");
        helper.succeed();
    }

    /**
     * Smoke off a pot rises into the Range Hood over it and comes out of the top of the Chimney the hood's Duct leads
     * to. A running Ventilation Fan draws the smoke in front of it and blows it out behind; switched off, it does not.
     */
    private static void smoke(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pot = new BlockPos(2, 2, 2);
        helper.setBlock(pot, ModCooking.IRON_POT.get());
        helper.setBlock(pot.above(2), ModVentilation.RANGE_HOOD.get());
        helper.setBlock(pot.above(3), ModVentilation.DUCT.get());
        helper.setBlock(pot.above(3).east(), ModVentilation.DUCT.get());
        helper.setBlock(pot.above(3).east(2), ModVentilation.CHIMNEY.get());
        helper.setBlock(pot.above(4).east(2), ModVentilation.CHIMNEY.get());
        for (BlockPos p : new BlockPos[] {pot.above(3), pot.above(3).east(), pot.above(3).east(2), pot.above(4).east(2)}) {
            // set down as a player sets them, joined up with what is round them
            BlockPos at = helper.absolutePos(p);
            level.setBlock(at, Block.updateFromNeighbourShapes(level.getBlockState(at), level, at), 3);
        }
        var duct = helper.getBlockState(pot.above(3));
        check(helper, DuctBlock.connections(duct).containsAll(List.of(Direction.DOWN, Direction.EAST)), "the duct joins the hood and the next: "
                + DuctBlock.connections(duct));
        check(helper, !helper.getBlockState(pot.above(3).east(2)).getValue(ChimneyBlock.TOP)
                && helper.getBlockState(pot.above(4).east(2)).getValue(ChimneyBlock.TOP), "the chimney's top has the pot");
        var intake = Ventilation.intakeFor(level, helper.absolutePos(pot));
        check(helper, intake != null && intake.pos().equals(helper.absolutePos(pot.above(2))), "the hood draws the pot's smoke");
        var outlets = Ventilation.outlets(level, intake);
        BlockPos top = helper.absolutePos(pot.above(4).east(2));
        check(helper, outlets.size() == 1 && outlets.get(0).distanceTo(new Vec3(top.getX() + 0.5, top.getY() + 1.05, top.getZ() + 0.5)) < 0.01,
                "and it comes out of the chimney: " + outlets);

        BlockPos grill = new BlockPos(5, 2, 1);
        helper.setBlock(grill, ModCooking.GRILL.get());
        BlockPos fan = new BlockPos(5, 2, 4);
        helper.setBlock(fan, ModVentilation.VENT_FAN.get().defaultBlockState().setValue(VentFanBlock.FACING, Direction.NORTH));
        helper.runAfterDelay(2, () -> { // a block entity set down takes its place in the level at the next tick
            check(helper, Ventilation.intakeFor(level, helper.absolutePos(grill)) == null, "a fan switched off draws nothing");
            helper.setBlock(fan, helper.getBlockState(fan).setValue(VentFanBlock.ON, true).setValue(VentFanBlock.SWITCHED, true));
            var drawn = Ventilation.intakeFor(level, helper.absolutePos(grill));
            check(helper, drawn != null && drawn.pos().equals(helper.absolutePos(fan)), "a running fan draws the grill's smoke in front of it");
            check(helper, Ventilation.outlets(level, drawn).size() == 1, "and blows it out behind: " + Ventilation.outlets(level, drawn)
                    + " at " + helper.getBlockState(fan.south()));
            helper.succeed();
        });
    }

    /**
     * A Motor set down turns its shaft to what it drives beside it (the user, 2026-11-23: 「毎回レンチで向きを変える必要が
     * あって」), whichever block was clicked; never into a cable, nor into a solid face while a way is open; with nothing
     * to drive, away from the player, not down into the floor clicked. One set down as before - against a machine it
     * drives - is set as before.
     */
    private static void motorShaftWay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(3, 2, 3);
        BlockPos pos = helper.absolutePos(at);
        helper.setBlock(at.below(), Blocks.STONE);
        check(helper, MotorBlock.shaftFor(level, pos, Direction.DOWN, Direction.SOUTH) == Direction.SOUTH,
                "nothing to drive: set on the floor, the shaft points away from the player, not into the floor");
        helper.setBlock(at.east(), ModOil.OIL_PRESS.get());
        check(helper, MotorBlock.shaftFor(level, pos, Direction.DOWN, Direction.SOUTH) == Direction.EAST,
                "an Oil Press beside it: the shaft turns to it, whatever was clicked");
        check(helper, MotorBlock.shaftFor(level, pos, Direction.EAST, Direction.EAST) == Direction.EAST, "and against it, as before");
        helper.setBlock(at.east(), Blocks.AIR);
        helper.setBlock(at.west(), ModBlocks.ENERGY_CABLE.get());
        check(helper, MotorBlock.shaftFor(level, pos, Direction.WEST, Direction.WEST) != Direction.WEST, "never into the cable it was set against");
        helper.setBlock(at.north(), Blocks.STONE);
        Direction away = MotorBlock.shaftFor(level, pos, Direction.NORTH, Direction.NORTH);
        check(helper, away != Direction.NORTH && away != Direction.WEST && away.getAxis().isHorizontal(),
                "set against a wall, the shaft goes another way, sideways: " + away);
        helper.succeed();
    }

    /**
     * A Motor's body is set back on the sides round its shaft, and a cable's arm ends at the block's edge (the user,
     * 2026-11-23: 「モーターのケーブルの間に隙間ができるのが気になる」): it shows a socket across the gap on a side a cable is
     * joined to - not on the shaft's line, where its body is not set back - and none once the cable is gone. Turned
     * with the Wrench, the socket goes with the side.
     */
    private static void motorSockets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos motor = new BlockPos(3, 2, 3);
        helper.setBlock(motor, ModOil.MOTOR.get().defaultBlockState().setValue(MotorBlock.FACING, Direction.NORTH));
        helper.setBlock(motor.east(), ModBlocks.ENERGY_CABLE.get());
        helper.setBlock(motor.south(), ModBlocks.ENERGY_CABLE.get());
        for (BlockPos p : new BlockPos[] {motor.east(), motor.south(), motor}) {
            BlockPos at = helper.absolutePos(p);
            level.setBlock(at, Block.updateFromNeighbourShapes(level.getBlockState(at), level, at), 3);
        }
        BlockState state = helper.getBlockState(motor);
        check(helper, state.getValue(MotorBlock.SOCKETS.get(Direction.EAST)), "a socket where the cable joins the motor's side: " + state);
        check(helper, !state.getValue(MotorBlock.SOCKETS.get(Direction.SOUTH)), "none on its back, flush with the cable: " + state);
        check(helper, !state.getValue(MotorBlock.SOCKETS.get(Direction.WEST)), "none where there is no cable");
        helper.setBlock(motor.east(), Blocks.AIR);
        check(helper, !helper.getBlockState(motor).getValue(MotorBlock.SOCKETS.get(Direction.EAST)), "and none once the cable is gone");
        helper.setBlock(motor.east(), ModBlocks.ENERGY_CABLE.get());
        BlockPos east = helper.absolutePos(motor.east());
        level.setBlock(east, Block.updateFromNeighbourShapes(level.getBlockState(east), level, east), 3);
        helper.setBlock(motor, helper.getBlockState(motor).setValue(MotorBlock.FACING, Direction.EAST)); // turned: the cable on its shaft's line
        check(helper, !helper.getBlockState(motor).getValue(MotorBlock.SOCKETS.get(Direction.EAST)), "turned to face the cable, it shows none there");
        helper.succeed();
    }

    /**
     * A duct joins whatever is beside it that it can (the user, 2026-11-23: 「隣にあると、どこでも繋がっちまう」): a
     * sneak-use of the Wrench on its arm parts it from the chimney there - the chimney's collar goes too, and the hood's
     * smoke comes out of the duct's open end instead - and on the core's face it joins it back. On the chimney's side it
     * parts them the same. A duct parted from the hood under it takes none of its smoke.
     */
    private static void ductsPartedWithTheWrench(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos hood = new BlockPos(2, 1, 2);
        BlockPos duct = new BlockPos(2, 2, 2);
        BlockPos chimney = new BlockPos(3, 2, 2);
        helper.setBlock(hood, ModVentilation.RANGE_HOOD.get());
        helper.setBlock(duct, ModVentilation.DUCT.get());
        helper.setBlock(chimney, ModVentilation.CHIMNEY.get());
        helper.setBlock(chimney.above(), ModVentilation.CHIMNEY.get());
        for (BlockPos p : new BlockPos[] {duct, chimney, chimney.above()}) {
            BlockPos at = helper.absolutePos(p);
            level.setBlock(at, Block.updateFromNeighbourShapes(level.getBlockState(at), level, at), 3);
        }
        BlockPos d = helper.absolutePos(duct);
        Ventilation.Intake intake = intakeAt(helper, hood);
        BlockPos top = helper.absolutePos(chimney.above());
        Vec3 pot = new Vec3(top.getX() + 0.5, top.getY() + 1.05, top.getZ() + 0.5);
        check(helper, DuctBlock.connections(level.getBlockState(d)).containsAll(List.of(Direction.DOWN, Direction.EAST))
                && helper.getBlockState(chimney).getValue(ChimneyBlock.SIDES.get(Direction.WEST)), "the duct joins the hood and the chimney");
        check(helper, Ventilation.findOutlets(level, intake).equals(List.of(pot)), "and the smoke goes out of the pot");

        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        ItemStack wrench = new ItemStack(ModItems.WRENCH.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        // on the duct's arm towards the chimney
        player.gameMode.useItemOn(player, level, wrench, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(d.getX() + 0.85, d.getY() + 11 / 16.0, d.getZ() + 0.5), Direction.UP, d, false));
        check(helper, !DuctBlock.connections(level.getBlockState(d)).contains(Direction.EAST)
                && !helper.getBlockState(chimney).getValue(ChimneyBlock.SIDES.get(Direction.WEST)), "the wrench parts the duct from the chimney");
        var parted = Ventilation.findOutlets(level, intake);
        check(helper, !parted.contains(pot), "the chimney no longer takes the hood's smoke: " + parted);
        helper.setBlock(new BlockPos(1, 2, 2), ModVentilation.DUCT.get()); // a duct set down after, on the other side: joins as ever
        // on the core's face where the arm was: joined back
        player.gameMode.useItemOn(player, level, wrench, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(d.getX() + 11 / 16.0, d.getY() + 0.5, d.getZ() + 0.5), Direction.EAST, d, false));
        check(helper, DuctBlock.connections(level.getBlockState(d)).contains(Direction.EAST)
                && helper.getBlockState(chimney).getValue(ChimneyBlock.SIDES.get(Direction.WEST)), "and joins them back");
        // on the chimney's side facing the duct
        BlockPos c = helper.absolutePos(chimney);
        player.gameMode.useItemOn(player, level, wrench, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(c.getX() + 3 / 16.0, c.getY() + 0.5, c.getZ() + 0.5), Direction.WEST, c, false));
        check(helper, !DuctBlock.connections(level.getBlockState(d)).contains(Direction.EAST)
                && !helper.getBlockState(chimney).getValue(ChimneyBlock.SIDES.get(Direction.WEST)), "parted from the chimney's side as well");
        DuctBlock.toggleSide(level, d, level.getBlockState(d), Direction.DOWN);
        check(helper, !DuctBlock.connections(level.getBlockState(d)).contains(Direction.DOWN) && Ventilation.findOutlets(level, intake).isEmpty(),
                "a duct parted from the hood takes none of it: " + Ventilation.findOutlets(level, intake));
        // a parted chimney turned in a structure keeps its parted side towards the duct (r: 1.8 review)
        BlockState partedChimney = level.getBlockState(c);
        BlockState turned = partedChimney.rotate(Rotation.CLOCKWISE_90);
        check(helper, partedChimney.getValue(PipeSides.OFF.get(Direction.WEST)) && turned.getValue(PipeSides.OFF.get(Direction.NORTH))
                && !turned.getValue(PipeSides.OFF.get(Direction.WEST)), "a chimney's parted side turns with it: " + turned);
        check(helper, partedChimney.mirror(Mirror.FRONT_BACK).getValue(PipeSides.OFF.get(Direction.EAST)), "and is mirrored with it");
        // a Flue beside a duct is never parted by the Wrench: nothing of it is changed
        BlockPos flue = helper.absolutePos(new BlockPos(2, 2, 3));
        level.setBlock(flue, ModVentilation.BRICK_FLUE.get().defaultBlockState(), 3);
        DuctBlock.toggleSide(level, d, level.getBlockState(d), Direction.SOUTH);
        check(helper, !level.getBlockState(flue).getValue(PipeSides.OFF.get(Direction.NORTH)), "a flue beside a duct is not parted: " + level.getBlockState(flue));
        helper.succeed();
    }

    /**
     * Smoke goes in only where there is a mouth (the user, 2026-11-22). A Flue's open bottom takes an open fire's smoke
     * up to three blocks below with nothing between - a campfire, netherrack alight: a fireplace - and sends it up the
     * chimney on it; not a furnace's, which has no hole on top, nor through a block between. A chimney stood straight on
     * a campfire takes its smoke too (the user, 2026-11-23), not one stood on a furnace or a block above the fire. A hood
     * takes a lit smoker's smoke off the stove under it, and none when it is out; a running fan takes a campfire's in
     * front of it. A flue's shape is as it looks: open down the middle, nothing for a torch on its top. A grill astride a
     * lit campfire, or a pot on that grill, smokes with the fire under it, empty or not (the user, 2026-11-23: 「焼き網つか
     * うと煙出なくなる」); over a campfire gone out, nothing.
     */
    private static void smokeOnlyAtAMouth(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var lit = Blocks.CAMPFIRE.defaultBlockState();
        // a fireplace: a campfire, a gap, a flue and a chimney on it
        helper.setBlock(new BlockPos(0, 1, 0), lit);
        helper.setBlock(new BlockPos(0, 3, 0), ModVentilation.BRICK_FLUE.get());
        helper.setBlock(new BlockPos(0, 4, 0), ModVentilation.CHIMNEY.get());
        // netherrack set alight under a flue
        helper.setBlock(new BlockPos(2, 1, 0), Blocks.NETHERRACK);
        helper.setBlock(new BlockPos(2, 2, 0), Blocks.FIRE);
        helper.setBlock(new BlockPos(2, 4, 0), ModVentilation.STONE_BRICK_FLUE.get());
        // a block between the campfire and the flue
        helper.setBlock(new BlockPos(4, 1, 0), lit);
        helper.setBlock(new BlockPos(4, 2, 0), Blocks.STONE);
        helper.setBlock(new BlockPos(4, 3, 0), ModVentilation.BRICK_FLUE.get());
        // a lit furnace under a flue
        helper.setBlock(new BlockPos(6, 1, 0), Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
        helper.setBlock(new BlockPos(6, 2, 0), ModVentilation.BRICK_FLUE.get());
        // a chimney stood straight on a campfire
        helper.setBlock(new BlockPos(0, 1, 3), lit);
        helper.setBlock(new BlockPos(0, 2, 3), ModVentilation.CHIMNEY.get());
        helper.setBlock(new BlockPos(0, 3, 3), ModVentilation.CHIMNEY.get());
        // a lit smoker, a hood over it, a duct and a chimney
        BlockPos smoker = new BlockPos(3, 1, 3);
        helper.setBlock(smoker, Blocks.SMOKER.defaultBlockState().setValue(BlockStateProperties.LIT, true));
        helper.setBlock(new BlockPos(3, 3, 3), ModVentilation.RANGE_HOOD.get());
        helper.setBlock(new BlockPos(3, 4, 3), ModVentilation.DUCT.get());
        helper.setBlock(new BlockPos(3, 5, 3), ModVentilation.CHIMNEY.get());
        BlockPos duct = helper.absolutePos(new BlockPos(3, 4, 3));
        level.setBlock(duct, Block.updateFromNeighbourShapes(level.getBlockState(duct), level, duct), 3);

        Ventilation.Intake fireplace = intakeAt(helper, new BlockPos(0, 3, 0));
        check(helper, fireplace != null && Ventilation.drawing(level, fireplace).equals(List.of(helper.absolutePos(new BlockPos(0, 1, 0)))),
                "the flue's open bottom takes the campfire's smoke two blocks under it");
        BlockPos top = helper.absolutePos(new BlockPos(0, 4, 0));
        var out = Ventilation.outlets(level, fireplace);
        check(helper, out.size() == 1 && out.get(0).distanceTo(new Vec3(top.getX() + 0.5, top.getY() + 1.05, top.getZ() + 0.5)) < 0.01,
                "and it comes out of the chimney's pot: " + out);
        check(helper, Ventilation.chimneyOutlet(level, out.get(0)), "as a chimney's column of smoke");
        Ventilation.Intake netherrack = intakeAt(helper, new BlockPos(2, 4, 0));
        check(helper, netherrack != null && Ventilation.drawing(level, netherrack).size() == 1, "netherrack set alight makes a fireplace too");
        check(helper, Ventilation.drawing(level, intakeAt(helper, new BlockPos(4, 3, 0))).isEmpty(), "but not through a block between");
        check(helper, Ventilation.drawing(level, intakeAt(helper, new BlockPos(6, 2, 0))).isEmpty(),
                "a flue over a lit furnace takes nothing: a furnace has no hole on top");
        Ventilation.Intake onFire = intakeAt(helper, new BlockPos(0, 2, 3));
        check(helper, onFire != null && Ventilation.drawing(level, onFire).equals(List.of(helper.absolutePos(new BlockPos(0, 1, 3)))),
                "a chimney stood straight on a campfire takes its smoke");
        BlockPos stackTop = helper.absolutePos(new BlockPos(0, 3, 3));
        var up = Ventilation.outlets(level, onFire);
        check(helper, up.size() == 1 && up.get(0).distanceTo(new Vec3(stackTop.getX() + 0.5, stackTop.getY() + 1.05, stackTop.getZ() + 0.5)) < 0.01,
                "and lets it out of its pot: " + up);
        check(helper, intakeAt(helper, new BlockPos(0, 3, 3)) == null, "the chimney's upper block is no mouth of its own");
        helper.setBlock(new BlockPos(0, 1, 6), Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
        helper.setBlock(new BlockPos(0, 2, 6), ModVentilation.CHIMNEY.get());
        check(helper, intakeAt(helper, new BlockPos(0, 2, 6)) == null, "a chimney stood on a lit furnace takes nothing");
        helper.setBlock(new BlockPos(2, 1, 6), lit);
        helper.setBlock(new BlockPos(2, 3, 6), ModVentilation.CHIMNEY.get());
        check(helper, intakeAt(helper, new BlockPos(2, 3, 6)) == null, "nor a chimney a block above a campfire: only a flue reaches down");
        BlockPos flue = helper.absolutePos(new BlockPos(0, 3, 0));
        BlockState flueState = level.getBlockState(flue);
        check(helper, flueState.getCollisionShape(level, flue).toAabbs().stream().noneMatch(box -> box.contains(0.5, 0.5, 0.5)),
                "a flue is open down its middle");
        check(helper, !flueState.getShape(level, flue).toAabbs().stream().anyMatch(box -> box.contains(0.5, 0.5, 0.5)), "and its outline shows it");
        check(helper, !Blocks.TORCH.defaultBlockState().canSurvive(level, helper.absolutePos(new BlockPos(2, 5, 0))),
                "a torch finds nothing to stand on at a flue's top");
        helper.setBlock(new BlockPos(6, 1, 5), Blocks.STONE);
        check(helper, Blocks.TORCH.defaultBlockState().canSurvive(level, helper.absolutePos(new BlockPos(6, 2, 5))),
                "as it does on stone");
        helper.setBlock(new BlockPos(1, 1, 5), lit);
        helper.setBlock(new BlockPos(1, 2, 5), ModCooking.GRILL.get().defaultBlockState().setValue(GrillBlock.OVER_FIRE, true));
        helper.setBlock(new BlockPos(1, 4, 5), ModVentilation.RANGE_HOOD.get());
        Ventilation.Intake overGrill = intakeAt(helper, new BlockPos(1, 4, 5));
        check(helper, overGrill != null && Ventilation.drawing(level, overGrill).equals(List.of(helper.absolutePos(new BlockPos(1, 2, 5)))),
                "a hood over an empty grill on a lit campfire takes the fire's smoke");
        helper.setBlock(new BlockPos(3, 1, 5), lit);
        helper.setBlock(new BlockPos(3, 2, 5), ModCooking.IRON_POT.get().defaultBlockState().setValue(IronPotBlock.ON_GRILL, true));
        helper.setBlock(new BlockPos(3, 3, 5), ModVentilation.BRICK_FLUE.get());
        check(helper, Ventilation.drawing(level, intakeAt(helper, new BlockPos(3, 3, 5))).size() == 1,
                "a flue straight over a pot on a grill on a campfire takes its smoke");
        helper.setBlock(new BlockPos(4, 1, 5), lit.setValue(CampfireBlock.LIT, false));
        helper.setBlock(new BlockPos(4, 2, 5), ModCooking.GRILL.get().defaultBlockState().setValue(GrillBlock.OVER_FIRE, true));
        helper.setBlock(new BlockPos(4, 3, 5), ModVentilation.BRICK_FLUE.get());
        check(helper, Ventilation.drawing(level, intakeAt(helper, new BlockPos(4, 3, 5))).isEmpty(), "but none when the campfire under the grill is out");
        Ventilation.Intake hood = intakeAt(helper, new BlockPos(3, 3, 3));
        check(helper, hood != null && Ventilation.drawing(level, hood).equals(List.of(helper.absolutePos(smoker))), "the hood takes the lit smoker's smoke");
        BlockPos pot = helper.absolutePos(new BlockPos(3, 5, 3));
        var kitchen = Ventilation.outlets(level, hood);
        check(helper, kitchen.size() == 1 && kitchen.get(0).distanceTo(new Vec3(pot.getX() + 0.5, pot.getY() + 1.05, pot.getZ() + 0.5)) < 0.01,
                "up the duct and out of the chimney: " + kitchen);
        helper.setBlock(smoker, Blocks.SMOKER.defaultBlockState());
        check(helper, Ventilation.drawing(level, hood).isEmpty(), "and nothing once the smoker is out");
        // a running fan with a campfire in front of it (two blocks off), and one behind it
        BlockPos fan = new BlockPos(5, 2, 6);
        helper.setBlock(fan, ModVentilation.VENT_FAN.get().defaultBlockState().setValue(VentFanBlock.FACING, Direction.NORTH)
                .setValue(VentFanBlock.ON, true).setValue(VentFanBlock.SWITCHED, true));
        helper.setBlock(new BlockPos(5, 2, 4), lit);
        helper.setBlock(new BlockPos(5, 1, 4), Blocks.STONE);
        Ventilation.Intake blowing = intakeAt(helper, fan);
        check(helper, blowing != null && Ventilation.drawing(level, blowing).contains(helper.absolutePos(new BlockPos(5, 2, 4))),
                "a running fan takes the campfire's smoke in front of it");
        helper.setBlock(fan, helper.getBlockState(fan).setValue(VentFanBlock.ON, false).setValue(VentFanBlock.SWITCHED, false));
        check(helper, intakeAt(helper, fan) == null, "and switched off, it is no mouth");
        helper.succeed();
    }

    private static Ventilation.Intake intakeAt(GameTestHelper helper, BlockPos pos) {
        BlockPos at = helper.absolutePos(pos);
        return Ventilation.intakeAt(helper.getLevel(), at, helper.getLevel().getBlockState(at));
    }

    /**
     * The Flue Brick (the user, 2026-10-31) is part of the chimney: a hood's smoke goes up a flue straight over it and on
     * up a stone brick chimney stacked on that, out of its top; a duct run up into a flue from below goes the same way;
     * and a flue with nothing on it lets the smoke out of its own top. A chimney under a flue has no pot. A flue is
     * closed on its sides (the user, 2026-11-01): a duct beside it does not join it.
     */
    private static void smokeThroughFlue(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pot = new BlockPos(2, 2, 2);
        helper.setBlock(pot, ModCooking.IRON_POT.get());
        helper.setBlock(pot.above(2), ModVentilation.RANGE_HOOD.get());
        helper.setBlock(pot.above(3), ModVentilation.BRICK_FLUE.get());
        helper.setBlock(pot.above(4), ModVentilation.STONE_BRICK_CHIMNEY.get());
        helper.setBlock(pot.above(5), ModVentilation.STONE_BRICK_CHIMNEY.get());
        BlockPos other = new BlockPos(6, 2, 2);
        helper.setBlock(other, ModCooking.IRON_POT.get());
        helper.setBlock(other.above(2), ModVentilation.RANGE_HOOD.get());
        helper.setBlock(other.above(3), ModVentilation.DUCT.get());
        helper.setBlock(other.above(4), ModVentilation.COBBLESTONE_FLUE.get());
        for (BlockPos p : new BlockPos[] {pot.above(3), pot.above(4), pot.above(5), other.above(3), other.above(4)}) {
            BlockPos at = helper.absolutePos(p);
            level.setBlock(at, Block.updateFromNeighbourShapes(level.getBlockState(at), level, at), 3);
        }
        check(helper, !helper.getBlockState(pot.above(4)).getValue(ChimneyBlock.TOP) && helper.getBlockState(pot.above(5)).getValue(ChimneyBlock.TOP),
                "the chimney on the flue has its pot at the top");
        var outlets = Ventilation.outlets(level, Ventilation.intakeFor(level, helper.absolutePos(pot)));
        BlockPos top = helper.absolutePos(pot.above(5));
        check(helper, outlets.size() == 1 && outlets.get(0).distanceTo(new Vec3(top.getX() + 0.5, top.getY() + 1.05, top.getZ() + 0.5)) < 0.01,
                "the hood's smoke goes up the flue and out of the chimney: " + outlets);
        check(helper, DuctBlock.connections(helper.getBlockState(other.above(3))).containsAll(List.of(Direction.DOWN, Direction.UP)),
                "a duct joins the flue over it");
        var through = Ventilation.outlets(level, Ventilation.intakeFor(level, helper.absolutePos(other)));
        BlockPos flueTop = helper.absolutePos(other.above(4));
        check(helper, through.size() == 1 && through.get(0).distanceTo(new Vec3(flueTop.getX() + 0.5, flueTop.getY() + 1.05, flueTop.getZ() + 0.5)) < 0.01,
                "up a duct into a flue with nothing on it, the smoke comes out of the flue's top: " + through);
        BlockPos beside = other.above(4).east();
        helper.setBlock(beside, ModVentilation.DUCT.get());
        BlockPos at = helper.absolutePos(beside);
        level.setBlock(at, Block.updateFromNeighbourShapes(level.getBlockState(at), level, at), 3);
        check(helper, !DuctBlock.connections(helper.getBlockState(beside)).contains(Direction.WEST)
                && !DuctBlock.joins(helper.getBlockState(other.above(4)), Direction.WEST), "a duct beside a flue does not join its side");
        BlockPos flue = helper.absolutePos(other.above(4));
        var sideways = new Ventilation.Intake(flue.west(), Vec3.atCenterOf(flue.west()), flue, Direction.EAST);
        check(helper, Ventilation.outlets(level, sideways).isEmpty(), "nor does a fan blowing into its side send smoke up it");
        helper.succeed();
    }

    private WorkshopTests() {}
}
