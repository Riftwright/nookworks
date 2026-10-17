package dev.nookworks.gametest;

import static dev.nookworks.gametest.MiningTests.registerVillagePiece;
import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dev.nookworks.block.StorageBlock;
import dev.nookworks.client.ShaderAliases;
import dev.nookworks.feature.VillageMap;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
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
import net.minecraft.world.level.block.Blocks;
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

    private WorkshopTests() {}
}
