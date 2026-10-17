package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksNetwork;
import dev.nookworks.NookworksStartupConfig;
import dev.nookworks.block.ElectricFurnaceBlock;
import dev.nookworks.block.PipeSides;
import dev.nookworks.block.entity.TrashCanBlockEntity;
import dev.nookworks.client.render.LabelIconRenderer;
import dev.nookworks.cooking.NukadokoBlockEntity;
import dev.nookworks.cooking.PicklingRecipe;
import dev.nookworks.cooking.TrayBlockEntity;
import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.experience.LiquidExperienceBlock;
import dev.nookworks.experience.Move;
import dev.nookworks.fluid.CopperTankBlockEntity;
import dev.nookworks.fluid.Tanks;
import dev.nookworks.item.MagnetItem;
import dev.nookworks.label.LabelItem;
import dev.nookworks.machine.CrusherBlockEntity;
import dev.nookworks.machine.ElectricFurnaceBlockEntity;
import dev.nookworks.machine.GeneratorBlockEntity;
import dev.nookworks.machine.LaserBlockEntity;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.machine.MachineIO;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.machine.PlanterBlockEntity;
import dev.nookworks.machine.Readout;
import dev.nookworks.mercury.MercuryFluids;
import dev.nookworks.mercury.RetortBlockEntity;
import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.mobcapture.SpawnEggBrewerBlockEntity;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
import dev.nookworks.sorter.SorterTags;
import dev.nookworks.spawner.ControlledSpawner;
import dev.nookworks.spawner.SpawnerControllerBlockEntity;
import dev.nookworks.spawner.SpawnerHold;
import dev.nookworks.spawner.SpawnerLinkReceiverBlockEntity;
import dev.nookworks.spawner.SpawnerLinkSenderBlockEntity;
import dev.nookworks.spawner.SpawnerSettings;
import dev.nookworks.storage.StorageBlockEntity;
import dev.nookworks.storage.StorageContents;
import dev.nookworks.storage.StorageMenu;
import dev.nookworks.storage.StorageTier;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** What the fourth round of reviews (October 1, 2026: eight reviewers over the whole mod) found, each held to the right behaviour. */
final class ReviewFourTests {
    static void register() {
        NookworksGameTests.test("energy_network_loads_no_chunk", ReviewFourTests::networkLoadsNoChunk);
        NookworksGameTests.test("energy_reaches_every_machine_in_turn", ReviewFourTests::everyMachineInTurn);
        NookworksGameTests.test("power_goes_in_worthwhile_shares", ReviewFourTests::powerGoesInWorthwhileShares);
        NookworksGameTests.test("energy_network_skips_generators", ReviewFourTests::skipsGenerators);
        NookworksGameTests.test("cable_shares_another_mods_power_evenly", ReviewFourTests::cableSharesEvenly);
        NookworksGameTests.test("cable_capability_follows_its_block", ReviewFourTests::cableCapabilityFollowsItsBlock);
        NookworksGameTests.test("electric_furnace_short_of_power_does_not_flicker", ReviewFourTests::furnaceDoesNotFlicker);
        NookworksGameTests.test("crafting_recipes_never_overlap", ReviewFourTests::recipesNeverOverlap);
        NookworksGameTests.test("barrel_contents_cross_the_network_held_to_limits", ReviewFourTests::barrelContentsCrossTheNetwork);
        NookworksGameTests.test("barrel_opens_for_a_spectator", ReviewFourTests::barrelOpensForASpectator);
        NookworksGameTests.test("fluids_falling_wash_away_none_of_ours", ReviewFourTests::fluidsFallingWashAwayNoneOfOurs);
        NookworksGameTests.test("bottled_villager_gives_up_its_workstation", ReviewFourTests::bottledVillagerGivesUpItsWorkstation);
        NookworksGameTests.test("mercury_bucket_offers_no_fluid_to_other_mods", ReviewFourTests::mercuryBucketOffersNoFluid);
        NookworksGameTests.test("retort_takes_hoppered_bottles_in_its_fill_box", 120, ReviewFourTests::retortTakesHopperedBottles);
        NookworksGameTests.test("machine_sending_up_fills_a_tank_on_top", ReviewFourTests::machineSendingUpFillsATankOnTop);
        NookworksGameTests.test("liquid_experience_credits_only_monsters", ReviewFourTests::liquidExperienceCreditsOnlyMonsters);
        NookworksGameTests.test("sneak_takes_with_a_shield_in_the_other_hand", ReviewFourTests::sneakTakesWithAShield);
        NookworksGameTests.test("nukadoko_emptied_reads_empty", ReviewFourTests::nukadokoEmptiedReadsEmpty);
        NookworksGameTests.test("label_on_a_door_comes_off_when_it_opens", ReviewFourTests::labelOnADoorComesOff);
        NookworksGameTests.test("barrel_slot_hands_out_one_copy_per_click", ReviewFourTests::barrelSlotHandsOutOneCopy);
        NookworksGameTests.test("glazed_eyeblossom_opens_and_closes", ReviewFourTests::glazedEyeblossomOpensAndCloses);
        NookworksGameTests.test("trash_can_controller_and_brewer_read_out", ReviewFourTests::moreThingsReadOut);
        NookworksGameTests.test("other_startup_settings_are_told", ReviewFourTests::otherStartupSettingsAreTold);
        NookworksGameTests.test("machines_work_by_the_tuning_settings", ReviewFourTests::machinesWorkByTheTuningSettings);
        NookworksGameTests.test("a_label_item_shows_what_it_prints", ReviewFourTests::aLabelItemShowsWhatItPrints);
        NookworksGameTests.test("powered_blocks_keep_their_power_in_their_item", ReviewFourTests::poweredBlocksKeepTheirPower);
        NookworksGameTests.test("liquid_keepers_drop_by_hand_and_stand_explosions", ReviewFourTests::liquidKeepersDropByHand);
        NookworksGameTests.test("filled_liquid_keepers_go_one_to_a_stack", ReviewFourTests::filledLiquidKeepersGoOneToAStack);
        NookworksGameTests.test("nukadoko_pickles_while_nobody_is_near", ReviewFourTests::nukadokoPicklesWhileAway);
        NookworksGameTests.test("hand_magnet_leaves_others_land_alone", ReviewFourTests::handMagnetLeavesOthersLand);
        NookworksGameTests.test("sorter_lists_no_vanilla_tag_of_one_thing", ReviewFourTests::sorterListsNoVanillaTagOfOne);
        NookworksGameTests.test("spawner_link_is_its_placers", ReviewFourTests::spawnerLinkIsItsPlacers);
        NookworksGameTests.test("a_switched_off_spawner_stays_off_out_of_reach", ReviewFourTests::switchedOffSpawnerStaysOff);
    }

    /**
     * A Spawner Link Sender pairs only with whoever set it down (the user, 2026-10-01): another player's receiver is
     * refused. Set down again, it has a new stamp, and a receiver paired before reaches nothing - and says why.
     */
    private static void spawnerLinkIsItsPlacers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos spawner = new BlockPos(2, 2, 2);
        helper.setBlock(spawner, Blocks.SPAWNER);
        BlockPos senderPos = spawner.above();
        BlockPos at = helper.absolutePos(senderPos);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        Player other = helper.makeMockPlayer(GameType.SURVIVAL);
        Runnable place = () -> {
            helper.setBlock(senderPos, ModBlocks.SPAWNER_LINK_SENDER.get());
            ModBlocks.SPAWNER_LINK_SENDER.get().setPlacedBy(level, at, level.getBlockState(at), owner, ItemStack.EMPTY);
        };
        place.run();
        BiFunction<Player, ItemStack, ItemStack> pair = (player, receiver) -> {
            player.setItemInHand(InteractionHand.MAIN_HAND, receiver);
            level.getBlockState(at).useItemOn(receiver, level, player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
            return player.getMainHandItem();
        };
        ItemStack theirs = pair.apply(other, new ItemStack(ModItems.SPAWNER_LINK_RECEIVER.get()));
        check(helper, !theirs.has(ModDataComponents.SPAWNER_LINK.get()), "another player's receiver is refused");
        ItemStack mine = pair.apply(owner, new ItemStack(ModItems.SPAWNER_LINK_RECEIVER.get()));
        var sender = helper.getBlockEntity(senderPos, SpawnerLinkSenderBlockEntity.class);
        check(helper, mine.has(ModDataComponents.SPAWNER_LINK.get()) && sender.stamp() != 0
                && Long.valueOf(sender.stamp()).equals(mine.get(ModDataComponents.SPAWNER_LINK_STAMP.get())),
                "the owner's is paired, under the sender's stamp");
        BlockPos receiverPos = new BlockPos(5, 2, 5);
        helper.setBlock(receiverPos, ModBlocks.SPAWNER_LINK_RECEIVER.get());
        var receiver = helper.getBlockEntity(receiverPos, SpawnerLinkReceiverBlockEntity.class);
        receiver.applyComponentsFromItemStack(mine);
        check(helper, receiver.spawners(level).size() == 1 && Boolean.FALSE.equals(receiver.senderMoved(level)), "it reaches the spawner");
        helper.setBlock(senderPos, Blocks.AIR);
        place.run();
        check(helper, receiver.spawners(level).isEmpty() && Boolean.TRUE.equals(receiver.senderMoved(level)),
                "set down again, the sender is out of the old link's reach, and the receiver can tell");
        helper.succeed();
    }

    /**
     * A Spawner Controller switched off holds its spawner off out of its reach too (the user, 2026-10-01): the spawner
     * keeps the hold, saved with it, and with the controller's own control lapsed - far off, not loaded - it spawns
     * nothing. Switched on, the controller lets it go; and the hold goes when no sender touches the spawner any more.
     */
    private static void switchedOffSpawnerStaysOff(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SpawnerControllerBlockEntity controller = StorageTests.buildControl(helper, SpawnerSettings.DEFAULT.withEnabled(false), 0);
        SpawnerBlockEntity spawner = helper.getBlockEntity(StorageTests.SPAWNER_POS, SpawnerBlockEntity.class);
        ControlledSpawner control = ControlledSpawner.of(spawner.getSpawner());
        controller.update(level);
        check(helper, control.nookworks$heldBy() != null && control.nookworks$heldBy().pos().equals(controller.getBlockPos()),
                "switched off, the spawner holds itself off for it");
        check(helper, spawner.saveWithoutMetadata(level.registryAccess()).contains("NookworksHeldOffBy"), "and the hold is saved with it");
        control.nookworks$control(SpawnerSettings.DEFAULT.withEnabled(false), level.getGameTime() - 1); // its control lapsed
        var player = MockPlayers.inLevel(helper);
        Vec3 near = Vec3.atBottomCenterOf(spawner.getBlockPos().east(2));
        player.snapTo(near.x, near.y, near.z);
        IntSupplier delay = () -> spawner.saveWithoutMetadata(level.registryAccess()).getShortOr("Delay", (short) -1);
        int before = delay.getAsInt();
        for (int i = 0; i < 5; i++) {
            spawner.getSpawner().serverTick(level, spawner.getBlockPos());
        }
        check(helper, delay.getAsInt() == before, "out of its controller's reach, it does not count down to a spawn: "
                + before + " -> " + delay.getAsInt());
        controller.setSettings(SpawnerSettings.DEFAULT);
        controller.update(level);
        check(helper, control.nookworks$heldBy() == null, "switched on, the controller lets go of it");
        controller.setSettings(SpawnerSettings.DEFAULT.withEnabled(false));
        controller.update(level);
        GlobalPos by = control.nookworks$heldBy();
        helper.setBlock(StorageTests.SPAWNER_POS.above(), Blocks.AIR); // the sender
        check(helper, by != null && !SpawnerHold.stillHeld(level, spawner.getBlockPos(), by), "with no sender touching it, the hold goes");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * A carrot buried in a Nukadoko pickles as time passes, the tub's chunk loaded or not: back after the pickling
     * time, one tick finds it done (the user, 2026-10-01: fermentation goes on while away, as dough rising does).
     */
    private static void nukadokoPicklesWhileAway(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModCooking.NUKADOKO.get());
        var tub = helper.getBlockEntity(pos, NukadokoBlockEntity.class);
        BlockPos at = helper.absolutePos(pos);
        check(helper, tub.bury(new ItemStack(Items.CARROT)), "a carrot goes in");
        NukadokoBlockEntity.serverTick(level, at, level.getBlockState(at), tub);
        check(helper, !tub.done(0), "one tick does not pickle it");
        int time = PicklingRecipe.of(new ItemStack(Items.CARROT)).time();
        tub.tickedAt(level.getGameTime() - time); // its chunk away for the whole pickling time
        NukadokoBlockEntity.serverTick(level, at, level.getBlockState(at), tub);
        check(helper, tub.done(0), "back after the pickling time, it is pickled: " + tub.items().get(0));
        helper.succeed();
    }

    /**
     * The hand Magnet leaves alone what the player could not pick up by walking to it (the user, 2026-10-01): a claim
     * mod that keeps others' drops and experience on its land says no to the pickup, and the magnet asks that question,
     * which is answered without a word to the player. It never asks whether a block there may be broken: a claim mod
     * answers that by telling the player they cannot break blocks here, every few seconds (2026-10-02).
     */
    private static void handMagnetLeavesOthersLand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        Consumer<ItemEntityPickupEvent.Pre> items = event -> {
            if (!owner.getUUID().equals(event.getPlayer().getUUID())) {
                event.setCanPickup(TriState.FALSE);
            }
        };
        Consumer<PlayerXpEvent.PickupXp> orbs = event -> {
            if (!owner.getUUID().equals(event.getEntity().getUUID())) {
                event.setCanceled(true);
            }
        };
        int[] breaks = {0};
        Consumer<BreakBlockEvent> asked = event -> breaks[0]++;
        NeoForge.EVENT_BUS.addListener(ItemEntityPickupEvent.Pre.class, items);
        NeoForge.EVENT_BUS.addListener(PlayerXpEvent.PickupXp.class, orbs);
        NeoForge.EVENT_BUS.addListener(BreakBlockEvent.class, asked);
        try {
            for (Player player : List.of(stranger, owner)) {
                Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 2)));
                player.snapTo(stand.x, stand.y, stand.z);
                Vec3 drop = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 2)));
                ItemEntity item = new ItemEntity(level, drop.x, drop.y, drop.z, new ItemStack(Items.DIAMOND), 0, 0, 0);
                item.setNoPickUpDelay();
                level.addFreshEntity(item);
                Vec3 orbAt = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 4)));
                ExperienceOrb orb = new ExperienceOrb(level, orbAt.x, orbAt.y, orbAt.z, 7);
                orb.setDeltaMovement(Vec3.ZERO);
                level.addFreshEntity(orb);
                MagnetItem.pull(player, 8);
                boolean mine = player == owner;
                check(helper, (item.getDeltaMovement().x < -0.1) == mine,
                        (mine ? "what the player may pick up, it pulls" : "what is someone else's to pick up, it leaves") + ": " + item.getDeltaMovement());
                check(helper, (orb.getDeltaMovement().x < -0.1) == mine,
                        (mine ? "and experience too" : "experience too") + ": " + orb.getDeltaMovement());
                item.discard();
                orb.discard();
            }
            check(helper, breaks[0] == 0, "it never asks whether a block may be broken, asked " + breaks[0] + " times");
        } finally {
            NeoForge.EVENT_BUS.unregister(items);
            NeoForge.EVENT_BUS.unregister(orbs);
            NeoForge.EVENT_BUS.unregister(asked);
        }
        helper.succeed();
    }

    /**
     * An iron ingot's tags in the Precision Sorter's list: none of vanilla's tags of one thing (repairs_iron_armor and
     * the like, the game's bookkeeping), but the common c:ingots/iron, which other mods' iron joins in a pack.
     */
    private static void sorterListsNoVanillaTagOfOne(GameTestHelper helper) {
        var choices = SorterTags.choices(new ItemStack(Items.IRON_INGOT));
        var single = choices.stream().filter(tag -> tag.location().getNamespace().equals("minecraft") && SorterTags.size(tag) == 1).toList();
        check(helper, single.isEmpty(), "no vanilla tag of one thing: " + single);
        check(helper, choices.stream().anyMatch(tag -> tag.location().toString().equals("c:ingots/iron")), "the common tag stays: " + choices);
        helper.succeed();
    }

    /** The FE store of a powered block of ours, or null. */
    private static @Nullable SimpleEnergyHandler energyOf(@Nullable BlockEntity entity) {
        return entity instanceof MachineBlockEntity machine ? machine.energy()
                : entity instanceof LaserBlockEntity laser ? laser.energy()
                : entity instanceof PlanterBlockEntity planter ? planter.energy()
                : entity instanceof SpawnerLinkReceiverBlockEntity receiver ? receiver.energy() : null;
    }

    /** Every block of ours that keeps a liquid in its item: a tank, or a machine with a tank. */
    static final List<String> KEEPERS = List.of("copper_tank", "mercury_tank", "electrolytic_cell", "generator", "distiller",
            "retort", "stirring_tank", "oil_press");

    /**
     * A block that keeps a liquid in its item drops to a bare hand - its liquid with it - and no explosion breaks it, as
     * a Storage Barrel: what it holds is never lost to a missing pickaxe or a creeper (the user, 2026-10-01).
     */
    private static void liquidKeepersDropByHand(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos spot = new BlockPos(3, 2, 3);
        BlockPos at = helper.absolutePos(spot);
        var player = MockPlayers.inLevel(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setPermanentlyInvulnerable(true); // it stands by the blasts
        player.setGameMode(GameType.SURVIVAL);
        for (String name : KEEPERS) {
            Block block = BuiltInRegistries.BLOCK.getValue(Nookworks.id(name));
            helper.setBlock(spot, block);
            level.explode(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() - 0.5, 4.0F, Level.ExplosionInteraction.TNT);
            check(helper, level.getBlockState(at).is(block), name + " stands a creeper's blast");
            check(helper, player.hasCorrectToolForDrops(level.getBlockState(at)), name + " drops to a bare hand");
            player.gameMode.destroyBlock(at);
            check(helper, helper.getEntities(EntityTypes.ITEM, spot, 2.0).stream().anyMatch(item -> item.getItem().is(block.asItem())),
                    name + ": broken by hand, it drops");
            helper.getEntities(EntityTypes.ITEM).forEach(Entity::discard);
        }
        helper.succeed();
    }

    /** The blocks whose items keep a liquid, but for the Mercury Tank, whose item is one to a stack anyway. */
    static final List<String> LIQUID_ITEMS = List.of("copper_tank", "distiller", "electrolytic_cell", "generator", "oil_press",
            "retort", "stirring_tank");

    /**
     * An item that keeps a liquid goes one to a stack while it holds some, as a filled bucket does: 64 filled Copper
     * Tanks carried a thousand buckets in one slot (the user, 2026-10-01). Empty - or set down, emptied and broken
     * again - it stacks to 64.
     */
    private static void filledLiquidKeepersGoOneToAStack(GameTestHelper helper) {
        BlockPos spot = new BlockPos(2, 2, 2);
        helper.setBlock(spot, ModBlocks.COPPER_TANK.get());
        check(helper, dropOf(helper, spot).getMaxStackSize() == 64, "an empty tank stacks to 64");
        helper.getBlockEntity(spot, CopperTankBlockEntity.class).fluidHandler().set(0, FluidResource.of(Fluids.WATER), 4_000);
        ItemStack filled = dropOf(helper, spot);
        check(helper, filled.getMaxStackSize() == 1, "a filled tank is one to a stack, as a water bucket is");
        helper.setBlock(spot, Blocks.AIR);
        helper.setBlock(spot, ModBlocks.COPPER_TANK.get());
        var again = helper.getBlockEntity(spot, CopperTankBlockEntity.class);
        again.applyComponentsFromItemStack(filled);
        again.fluidHandler().set(0, FluidResource.EMPTY, 0);
        ItemStack emptied = dropOf(helper, spot);
        check(helper, emptied.getMaxStackSize() == 64 && !emptied.getComponentsPatch().isPatched(DataComponents.MAX_STACK_SIZE),
                "set down, emptied and broken, it stacks again: " + emptied.getComponentsPatch());

        helper.setBlock(spot, ModBlocks.GENERATOR.get());
        check(helper, dropOf(helper, spot).getMaxStackSize() == 64, "an empty generator stacks to 64");
        Move.insert(helper.getBlockEntity(spot, GeneratorBlockEntity.class).fluidHandler(), FluidResource.of(OilFluids.SOURCE.get()), 1_500);
        check(helper, dropOf(helper, spot).getMaxStackSize() == 1, "a generator holding oil is one to a stack");

        for (String name : LIQUID_ITEMS) { // the drop takes the limit from the block only where its loot table copies it
            try (var table = ReviewFourTests.class.getResourceAsStream("/data/nookworks/loot_table/blocks/" + name + ".json")) {
                check(helper, table != null && new String(table.readAllBytes(), StandardCharsets.UTF_8).contains("\"minecraft:max_stack_size\""),
                        name + "'s loot table copies the stack limit with the liquid");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        helper.succeed();
    }

    /** What the block at {@code spot} drops of itself to a pickaxe. */
    private static ItemStack dropOf(GameTestHelper helper, BlockPos spot) {
        BlockPos at = helper.absolutePos(spot);
        BlockState state = helper.getBlockState(spot);
        return Block.getDrops(state, helper.getLevel(), at, helper.getLevel().getBlockEntity(at), null, new ItemStack(Items.IRON_PICKAXE))
                .stream().filter(stack -> stack.is(state.getBlock().asItem())).findFirst().orElse(ItemStack.EMPTY);
    }

    /** Every block of ours that stores FE: each keeps it in its item when broken. */
    static final List<String> POWERED = List.of("repair_box", "water_pump", "crusher", "electric_furnace", "electrolytic_cell",
            "generator", "laser", "motor", "planter", "stone_maker", "distiller", "retort", "stirring_tank", "printing_press",
            "spawner_link_receiver");

    /**
     * Every powered block keeps the power it held in the item it leaves when broken, as its liquids are kept, and set
     * down again holds it as before (the user, 2026-10-01: the liquids were kept and the power lost).
     */
    private static void poweredBlocksKeepTheirPower(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos spot = new BlockPos(2, 2, 2);
        BlockPos at = helper.absolutePos(spot);
        for (String name : POWERED) {
            Block block = BuiltInRegistries.BLOCK.getValue(Nookworks.id(name));
            helper.setBlock(spot, block);
            BlockEntity entity = level.getBlockEntity(at);
            SimpleEnergyHandler energy = energyOf(entity);
            check(helper, energy != null, name + " stores power");
            int amount = (int) Math.min(1_234, energy.getCapacityAsLong());
            energy.set(amount);
            ItemStack item = Block.getDrops(level.getBlockState(at), level, at, entity).stream()
                    .filter(stack -> stack.is(block.asItem())).findFirst().orElse(ItemStack.EMPTY);
            check(helper, Integer.valueOf(amount).equals(item.get(ModDataComponents.HELD_ENERGY.get())),
                    name + ": its item keeps " + amount + " FE: " + item.getComponentsPatch());
            helper.setBlock(spot, Blocks.AIR);
            helper.setBlock(spot, block);
            BlockEntity again = level.getBlockEntity(at);
            again.applyComponentsFromItemStack(item);
            SimpleEnergyHandler restored = energyOf(again);
            check(helper, restored.getAmountAsLong() == amount, name + ": set down again it holds " + restored.getAmountAsLong());
            helper.setBlock(spot, Blocks.AIR);
        }
        helper.getEntities(EntityTypes.ITEM).forEach(Entity::discard);
        helper.succeed();
    }

    /**
     * A label's item shows what it prints wherever it is drawn: its item definition lays our special model over the
     * card, on a screen keeping the letters to whole pixels and anywhere else shrinking them to fit. The model is given
     * the picture or the letters - nothing for a blank label, and the same for two labels alike, so a screen draws each
     * kind once.
     */
    private static void aLabelItemShowsWhatItPrints(GameTestHelper helper) {
        var look = (Function<ItemStack, Object>) LabelIconRenderer::look;
        ItemStack iron = LabelItem.of(new ItemStack(Items.IRON_INGOT));
        check(helper, look.apply(new ItemStack(ModItems.LABEL.get())) == null, "a blank label prints nothing");
        check(helper, new LabelIconRenderer.Look(Items.IRON_INGOT, "").equals(look.apply(iron))
                && look.apply(iron).equals(look.apply(LabelItem.of(new ItemStack(Items.IRON_INGOT, 5)))),
                "an iron label prints iron, as every iron label does: " + look.apply(iron));
        check(helper, new LabelIconRenderer.Look(null, "Ores 42").equals(look.apply(LabelItem.ofText("Ores 42"))),
                "a letter label prints its letters: " + look.apply(LabelItem.ofText("Ores 42")));

        String path = "/assets/" + Nookworks.MODID + "/items/label.json";
        JsonObject definition;
        try (var in = Nookworks.class.getResourceAsStream(path)) {
            check(helper, in != null, "no " + path);
            definition = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
        var model = definition.getAsJsonObject("model");
        var layers = model.getAsJsonArray("models");
        check(helper, "minecraft:composite".equals(model.get("type").getAsString()) && layers.size() == 2
                && "nookworks:item/label".equals(layers.get(0).getAsJsonObject().get("model").getAsString()), "the card, and over it: " + model);
        var printed = layers.get(1).getAsJsonObject();
        var cases = printed.getAsJsonArray("cases");
        check(helper, "minecraft:display_context".equals(printed.get("property").getAsString()) && cases.size() == 1
                && "gui".equals(cases.get(0).getAsJsonObject().get("when").getAsString()), "chosen by where it is drawn: " + printed);
        BiConsumer<JsonObject, Boolean> special = (json, gui) -> {
            var inner = json.getAsJsonObject("model");
            var unbaked = LabelIconRenderer.Unbaked.MAP_CODEC.codec()
                    .parse(JsonOps.INSTANCE, inner).result();
            check(helper, "minecraft:special".equals(json.get("type").getAsString())
                    && "nookworks:item/label".equals(json.get("base").getAsString())
                    && LabelIconRenderer.TYPE.toString().equals(inner.get("type").getAsString())
                    && unbaked.isPresent() && unbaked.get().gui() == gui, (gui ? "on a screen" : "elsewhere") + ": " + json);
        };
        special.accept(cases.get(0).getAsJsonObject().getAsJsonObject("model"), true);
        special.accept(printed.getAsJsonObject("fallback"), false);
        helper.succeed();
    }

    /**
     * The machine settings scale every figure a machine works by - to the nearest whole, never to nothing - and at
     * their defaults leave them as made, which is what the machines then report.
     */
    private static void machinesWorkByTheTuningSettings(GameTestHelper helper) {
        check(helper, MachineTuning.scale(20, 2.0) == 40 && MachineTuning.scale(60, 0.5) == 30
                && MachineTuning.scale(10, 0.1) == 1 && MachineTuning.scale(3, 0.1) == 1,
                "scaled to the nearest whole, never below 1");
        check(helper, !MachineTuning.tuned(), "the test server plays with the defaults");
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModBlocks.CRUSHER.get());
        var crusher = helper.getBlockEntity(pos, CrusherBlockEntity.class);
        check(helper, crusher.guiEnergyRate() == CrusherBlockEntity.FE_PER_TICK
                && crusher.guiProgressMax() == CrusherBlockEntity.TICKS_PER_ITEM, "a crusher at the defaults works as made");
        BlockPos gen = new BlockPos(3, 2, 1);
        helper.setBlock(gen, ModBlocks.GENERATOR.get());
        check(helper, helper.getBlockEntity(gen, GeneratorBlockEntity.class).guiEnergyRate()
                == GeneratorBlockEntity.FE_PER_TICK, "and a generator makes as made");
        helper.succeed();
    }

    /**
     * A client whose start-up settings are not the server's is told which, when it joins: they make items and recipes
     * what they are, and neither side can change them while it runs. The same settings say nothing.
     */
    private static void otherStartupSettingsAreTold(GameTestHelper helper) {
        var mine = NookworksStartupConfig.values();
        check(helper, NookworksStartupConfig.mismatch(mine).isEmpty(), "the same settings: nothing to say");
        var other = new LinkedHashMap<>(mine);
        other.put("edible_raw_foods", !mine.get("edible_raw_foods"));
        var told = NookworksStartupConfig.mismatch(other);
        check(helper, told.isPresent() && told.get().getString().contains("Edible Raw Foods")
                && !told.get().getString().contains("Flour Bakery"), "only the setting that differs is named: " + told.map(c -> c.getString()));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        NookworksNetwork.StartupSettings.STREAM_CODEC.encode(buffer, new NookworksNetwork.StartupSettings(other));
        check(helper, NookworksNetwork.StartupSettings.STREAM_CODEC.decode(buffer).values().equals(other), "sent whole");
        helper.succeed();
    }

    /** The Trash Can, the Spawner Controller and the Spawn Egg Brewer say how they are without being opened (Jade's lines). */
    private static void moreThingsReadOut(GameTestHelper helper) {
        Function<BlockEntity, List<String>> keys = entity -> {
            List<String> found = new ArrayList<>();
            ((Readout) entity).readout(line -> found.add(
                    line.getContents() instanceof TranslatableContents t ? t.getKey() : line.getString()));
            return found;
        };
        BlockPos can = new BlockPos(1, 2, 1);
        helper.setBlock(can, ModBlocks.TRASH_CAN.get());
        List<String> canLines = keys.apply(helper.getBlockEntity(can, TrashCanBlockEntity.class));
        check(helper, canLines.equals(List.of("nookworks.trash_can.off")), "an unpowered can says it is off: " + canLines);

        BlockPos controller = new BlockPos(3, 2, 1);
        helper.setBlock(controller, ModBlocks.SPAWNER_CONTROLLER.get());
        List<String> controllerLines = keys.apply(helper.getBlockEntity(controller, SpawnerControllerBlockEntity.class));
        check(helper, controllerLines.equals(List.of("gui.nookworks.spawner_controller.status.none")), "no spawner linked: " + controllerLines);

        BlockPos brewer = new BlockPos(5, 2, 1);
        helper.setBlock(brewer, ModBlocks.SPAWN_EGG_BREWER.get());
        var brewing = helper.getBlockEntity(brewer, SpawnEggBrewerBlockEntity.class);
        check(helper, keys.apply(brewing).equals(List.of("gui.nookworks.spawn_egg_brewer.idle")), "an empty brewer asks for a bottle and an egg");
        brewing.setItem(SpawnEggBrewerBlockEntity.BOTTLE,
                MobBottleItem.filled(EntityTypes.PIG));
        brewing.setItem(SpawnEggBrewerBlockEntity.EGG, new ItemStack(Items.EGG));
        check(helper, keys.apply(brewing).equals(List.of("gui.nookworks.machine.status.no_fuel")), "with no fuel it says so: " + keys.apply(brewing));
        helper.succeed();
    }

    /** Inserts {@code amount} into {@code handler} and commits; what it took. */
    static int insert(EnergyHandler handler, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int taken = handler.insert(amount, tx);
            tx.commit();
            return taken;
        }
    }

    /**
     * A receiver kept in a network whose chunk has gone unloaded is not loaded again to be fed - nor held loaded by being
     * asked every tick, which is what reading it through the level does - and gets nothing.
     */
    private static void networkLoadsNoChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos far = new BlockPos(48_016, 64, -48_016);
        check(helper, !level.isLoaded(far), "the place is out of the loaded world to begin with");
        check(helper, MachineIO.handlerOf(level, new MachineIO.Receiver(far, List.of(Direction.NORTH))) == null
                && MachineIO.loadedChunk(level, far) == null, "a receiver there has no handler");
        check(helper, !level.isLoaded(far), "and asking did not load it");
        helper.succeed();
    }

    /**
     * With less to send than a share for every machine, the machines still all get their turn: the one sending starts
     * further along each tick. (It used to send to the first ones found every tick, and a long row of furnaces past the
     * fortieth never ran.)
     */
    private static void everyMachineInTurn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(0, 1, 3);
        helper.setBlock(origin, Blocks.STONE);
        List<BlockPos> crushers = new ArrayList<>();
        for (int x = 1; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 1, 3), ModBlocks.ENERGY_CABLE.get());
        }
        for (int x = 1; x <= 5; x++) {
            for (BlockPos at : List.of(new BlockPos(x, 2, 3), new BlockPos(x, 1, 2), new BlockPos(x, 1, 4))) {
                helper.setBlock(at, ModBlocks.CRUSHER.get());
                crushers.add(at);
            }
        }
        SimpleEnergyHandler source = new SimpleEnergyHandler(1_000_000, 1_000_000, 1_000_000);
        insert(source, 1_000_000);
        BlockPos from = helper.absolutePos(origin);
        check(helper, MachineIO.receivers(level, from).size() == crushers.size(), "the network reaches all " + crushers.size() + " crushers");
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (ticks[0]++ < crushers.size()) {
                MachineIO.pushEnergy(level, from, source, 10); // ten to send among fifteen: not a share for each
            }
        });
        helper.runAfterDelay(crushers.size() + 2, () -> {
            List<BlockPos> starved = crushers.stream().filter(at -> helper.getBlockEntity(at, CrusherBlockEntity.class).energyStored() == 0).toList();
            check(helper, starved.isEmpty(), "every crusher had its turn; none got: " + starved);
            helper.succeed();
        });
    }

    /**
     * A Generator's 200 FE among fifteen machines goes to four of them, 50 each, the turn moving on - not in crumbs to all
     * fifteen, a transaction each, every tick (r7 load measurement: 40 Generators on 40 furnaces, half the tick) - and
     * none to a machine that is full.
     */
    private static void powerGoesInWorthwhileShares(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(0, 1, 3);
        helper.setBlock(origin, Blocks.STONE);
        List<BlockPos> crushers = new ArrayList<>();
        for (int x = 1; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 1, 3), ModBlocks.ENERGY_CABLE.get());
        }
        for (int x = 1; x <= 5; x++) {
            for (BlockPos at : List.of(new BlockPos(x, 2, 3), new BlockPos(x, 1, 2), new BlockPos(x, 1, 4))) {
                helper.setBlock(at, ModBlocks.CRUSHER.get());
                crushers.add(at);
            }
        }
        SimpleEnergyHandler source = new SimpleEnergyHandler(1_000_000, 1_000_000, 1_000_000);
        insert(source, 1_000_000);
        int sent = MachineIO.pushEnergy(level, helper.absolutePos(origin), source, 200);
        List<Integer> got = crushers.stream().map(at -> helper.getBlockEntity(at, CrusherBlockEntity.class).energyStored()).filter(e -> e > 0).toList();
        check(helper, sent == 200 && got.equals(List.of(50, 50, 50, 50)), "200 sent as 50 to each of four: " + sent + ", " + got);
        helper.succeed();
    }

    /** A Generator takes no power in, so a network does not count it among what it feeds: another beside it is not asked every tick. */
    private static void skipsGenerators(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(1, 1, 3), ModBlocks.GENERATOR.get());
        helper.setBlock(new BlockPos(2, 1, 3), ModBlocks.ENERGY_CABLE.get());
        helper.setBlock(new BlockPos(3, 1, 3), ModBlocks.GENERATOR.get());
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.CRUSHER.get());
        helper.setBlock(new BlockPos(1, 1, 4), ModBlocks.GENERATOR.get()); // touching the first straight on
        var receivers = MachineIO.receivers(level, helper.absolutePos(new BlockPos(1, 1, 3)));
        check(helper, receivers.size() == 1 && receivers.get(0).pos().equals(helper.absolutePos(new BlockPos(2, 1, 2))),
                "the crusher alone, not the other generators: " + receivers);
        helper.succeed();
    }

    /**
     * Power another mod puts into a cable is shared among what the cable reaches as evenly as a Generator's is, as the
     * cable's page says. (It went to the first machine found until that was full, and the next got none.)
     */
    private static void cableSharesEvenly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cable = new BlockPos(3, 1, 3);
        helper.setBlock(cable, ModBlocks.ENERGY_CABLE.get());
        List<BlockPos> crushers = List.of(cable.north(), cable.east(), cable.south());
        crushers.forEach(at -> helper.setBlock(at, ModBlocks.CRUSHER.get()));
        EnergyHandler intoCable = level.getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(cable), Direction.WEST);
        check(helper, intoCable != null && insert(intoCable, 300) == 300, "the cable takes the 300 sent into it");
        List<Integer> shares = crushers.stream().map(at -> helper.getBlockEntity(at, CrusherBlockEntity.class).energyStored()).toList();
        check(helper, shares.equals(List.of(100, 100, 100)), "a hundred each: " + shares);
        helper.succeed();
    }

    /**
     * What another mod keeps of a cable's capability (a capability cache, as NeoForge has mods do) follows the cable: it
     * finds one set down at once, finds none on a side the Wrench turned off, and none once it is broken. A handler kept
     * from before takes nothing through a side turned off, or where the cable was.
     */
    private static void cableCapabilityFollowsItsBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cable = new BlockPos(3, 1, 3);
        BlockPos at = helper.absolutePos(cable);
        helper.setBlock(cable.east(), ModBlocks.CRUSHER.get());
        var cache = BlockCapabilityCache.create(Capabilities.Energy.BLOCK, level, at, Direction.WEST);
        check(helper, cache.getCapability() == null, "nothing there yet");
        helper.setBlock(cable, ModBlocks.ENERGY_CABLE.get());
        EnergyHandler first = cache.getCapability();
        check(helper, first != null, "a cable set down is seen at once by whoever was watching the place");
        PipeSides.toggle(level, at, level.getBlockState(at), Direction.WEST);
        check(helper, cache.getCapability() == null, "its side turned off: nothing there that way");
        check(helper, insert(first, 100) == 0, "and the handler kept from before takes nothing through the side turned off");
        PipeSides.toggle(level, at, level.getBlockState(at), Direction.WEST);
        EnergyHandler again = cache.getCapability();
        check(helper, again != null && insert(again, 100) == 100
                && helper.getBlockEntity(cable.east(), CrusherBlockEntity.class).energyStored() == 100, "on again, it carries power to the crusher");
        helper.setBlock(cable, Blocks.AIR);
        check(helper, cache.getCapability() == null && insert(again, 100) == 0
                && helper.getBlockEntity(cable.east(), CrusherBlockEntity.class).energyStored() == 100,
                "broken: nothing there, and nothing goes through the handler kept from before");
        helper.succeed();
    }

    /** An eyeblossom in a Glazed Flower Pot opens and closes with the time of day, as in a clay pot. (It stayed as planted.) */
    private static void glazedEyeblossomOpensAndCloses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(3, 1, 3);
        BlockPos at = helper.absolutePos(pos);
        boolean open = level.environmentAttributes().getValue(EnvironmentAttributes.EYEBLOSSOM_OPEN, at).toBoolean(false);
        var openPot = BuiltInRegistries.BLOCK.getValue(Nookworks.id("glazed_potted_open_eyeblossom"));
        var closedPot = BuiltInRegistries.BLOCK.getValue(Nookworks.id("glazed_potted_closed_eyeblossom"));
        helper.setBlock(pos, open ? closedPot : openPot); // the wrong way round for the hour
        level.getBlockState(at).randomTick(level, at, level.getRandom());
        check(helper, level.getBlockState(at).is(open ? openPot : closedPot), "it turns as the hour has it: " + level.getBlockState(at));
        helper.succeed();
    }

    /**
     * A barrel slot asked for its item twice, unchanged in between, hands out the same copy - so what a click does to the
     * copy it was given is what gets written back.
     */
    private static void barrelSlotHandsOutOneCopy(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.IRON_STORAGE.get());
        var barrel = helper.getBlockEntity(pos, StorageBlockEntity.class);
        barrel.inventory().set(0, ItemResource.of(new ItemStack(Items.COBBLESTONE)), 100);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var menu = barrel.createMenu(1, player.getInventory(), player);
        var slot = menu.slots.get(0);
        ItemStack first = slot.getItem();
        check(helper, slot.getItem() == first, "asked again, the same copy");
        first.shrink(30);
        slot.setChanged();
        check(helper, barrel.inventory().getAmountAsInt(0) == 70, "and what was done to it is written back: " + barrel.inventory().getAmountAsInt(0));
        helper.succeed();
    }

    /**
     * A label stuck on a closed door comes off when the door swings open, its face gone from behind the card - rather
     * than staying in the air where the door had been.
     */
    private static void labelOnADoorComesOff(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos door = new BlockPos(3, 1, 3);
        helper.setBlock(door, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH));
        helper.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        BlockPos at = helper.absolutePos(door);
        // a closed door facing north is a board on the south side of its square: stuck on its south face
        var shape = level.getBlockState(at).getCollisionShape(level, at).bounds();
        var hit = new Vec3(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + shape.maxZ);
        var label = LabelItem.place(level, at, Direction.SOUTH, hit, new ItemStack(Items.IRON_INGOT));
        check(helper, label != null, "a label goes on the closed door");
        level.addFreshEntity(label);
        level.setBlock(at, level.getBlockState(at).setValue(DoorBlock.OPEN, true), 3);
        helper.runAfterDelay(10, () -> {
            check(helper, label.isRemoved(), "opened, the door takes its face away from behind the label, and the label comes off");
            helper.succeed();
        });
    }

    /**
     * Sneaking with an empty hand takes everything off a Tray, a shield in the other hand or not. (Vanilla skips a
     * block's use for a sneaking player holding anything in either hand, and with a shield nothing came off.)
     */
    private static void sneakTakesWithAShield(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.OAK_PLANKS);
        helper.setBlock(pos, ModKitchen.WOODEN_TRAY.get());
        var tray = helper.getBlockEntity(pos, TrayBlockEntity.class);
        tray.place(level, null, new ItemStack(Items.BREAD));
        tray.place(level, null, new ItemStack(Items.BREAD));
        var player = MockPlayers.inLevel(helper);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        player.setShiftKeyDown(true);
        var hit = new BlockHitResult(helper.absoluteVec(Vec3.atCenterOf(pos)), Direction.UP,
                helper.absolutePos(pos), false);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        check(helper, tray.isEmpty() && player.getInventory().countItem(Items.BREAD) == 2, "both loaves come off into the hand that holds no shield");
        helper.succeed();
    }

    /**
     * A Nukadoko read back after its pickles were taken out reads empty: a client told so stops drawing them. (Reading
     * filled only the slots saved, and the taken-out pickles went on sitting in the bran.)
     */
    private static void nukadokoEmptiedReadsEmpty(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModCooking.NUKADOKO.get());
        var bed = helper.getBlockEntity(pos, NukadokoBlockEntity.class);
        var empty = bed.saveWithoutMetadata(level.registryAccess());
        bed.items().set(0, new ItemStack(Items.CARROT));
        bed.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,
                level.registryAccess(), empty));
        check(helper, bed.items().get(0).isEmpty(), "read back empty, it is empty: " + bed.items().get(0));
        helper.succeed();
    }

    /**
     * A monster that dies in Liquid Experience is the nearest player's kill, as a mob farm wants; a villager, a pet or
     * a named mob is nobody's - no bystander's doing, for the village to hold against them or a death message to name.
     */
    private static void liquidExperienceCreditsOnlyMonsters(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var player = MockPlayers.inLevel(helper);
        player.snapTo(helper.absoluteVec(new Vec3(3.5, 1, 3.5)));
        var zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
        var villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
        var named = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(4, 1, 2));
        named.setCustomName(Component.literal("Ton"));
        check(helper, LiquidExperienceBlock.source(level, zombie).getEntity() == player,
                "a zombie's death here is the nearest player's kill");
        check(helper, LiquidExperienceBlock.source(level, villager).getEntity() == null, "a villager's is nobody's");
        check(helper, LiquidExperienceBlock.source(level, named).getEntity() == null, "nor a named pig's");
        helper.succeed();
    }

    /**
     * Glass bottles a hopper sends down into an empty Sealed Retort go to its fill box, for the experience - not into a
     * reagent slot (bottles are what logs are charred with), where they started nothing and kept brass and pulp from
     * their second reagent.
     */
    private static void retortTakesHopperedBottles(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.RETORT.get());
        helper.setBlock(pos.above(), Blocks.HOPPER);
        var hopper = helper.getBlockEntity(pos.above(), HopperBlockEntity.class);
        hopper.setItem(0, new ItemStack(Items.GLASS_BOTTLE, 4));
        var retort = helper.getBlockEntity(pos, RetortBlockEntity.class);
        helper.runAfterDelay(60, () -> {
            check(helper, retort.getItem(1).isEmpty() && retort.getItem(2).isEmpty(), "nothing in the reagent slots: " + retort.getItem(1) + ", " + retort.getItem(2));
            check(helper, retort.getItem(RetortBlockEntity.FILL).is(Items.GLASS_BOTTLE), "the bottles are in the fill box");
            helper.succeed();
        });
    }

    /**
     * A Copper Tank stood on a machine that sends its fluid up - a Water Pump, a Stirring Tank - is filled by it, though
     * its bottom takes nothing in from a pipe below. (The tank stayed empty, and the machine stopped full.)
     */
    private static void machineSendingUpFillsATankOnTop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pump = new BlockPos(3, 1, 3);
        helper.setBlock(pump, ModBlocks.WATER_PUMP.get());
        helper.setBlock(pump.above(), ModBlocks.COPPER_TANK.get());
        var tank = helper.getBlockEntity(pump.above(), CopperTankBlockEntity.class);
        var bottom = tank.handlerFor(Direction.DOWN);
        var water = FluidResource.of(Fluids.WATER);
        try (Transaction tx = Transaction.openRoot()) {
            check(helper, bottom.insert(water, 1_000, tx) == 0, "a pipe below gets nothing in through the tank's bottom");
        }
        var up = Tanks.above(level, helper.absolutePos(pump));
        try (Transaction tx = Transaction.openRoot()) {
            check(helper, up != null && up.insert(water, 1_000, tx) == 1_000, "the pump under it fills it");
        }
        helper.succeed();
    }

    /**
     * A bucket of mercury has no fluid capability for another mod's tank to pour from, as the Mercury Tank and pipes
     * have none: mercury stays in our own iron. (NeoForge gave it one, as it gives every plain bucket.) Other buckets
     * of ours - Liquid Experience, oil - are public fluids, and keep theirs.
     */
    private static void mercuryBucketOffersNoFluid(GameTestHelper helper) {
        var fluid = Capabilities.Fluid.ITEM;
        check(helper, ItemAccess.forStack(new ItemStack(ModItems.MERCURY_BUCKET.get()))
                .getCapability(fluid) == null, "no fluid handler on a bucket of mercury");
        check(helper, ItemAccess.forStack(new ItemStack(ModItems.EXPERIENCE_BUCKET.get()))
                .getCapability(fluid) != null, "while Liquid Experience, a public fluid, still pours from its bucket");
        helper.succeed();
    }

    /**
     * A villager bottled gives up the workstation it held, as one that dies does, and is bottled without the memory of
     * it: another villager can take the lectern. (It stayed held for good, by a villager no longer in the world.)
     */
    private static void bottledVillagerGivesUpItsWorkstation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lectern = new BlockPos(1, 1, 1);
        helper.setBlock(lectern, Blocks.LECTERN);
        BlockPos at = helper.absolutePos(lectern);
        var pois = level.getPoiManager();
        var librarian = PoiTypes.LIBRARIAN;
        var villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(),
                VillagerProfession.LIBRARIAN));
        check(helper, pois.take(type -> type.is(librarian), (type, p) -> p.equals(at), at, 1).isPresent(), "the villager takes the lectern");
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE,
                GlobalPos.of(level.dimension(), at));
        IntSupplier free = () -> (int) pois.getCountInRange(type -> type.is(librarian), at, 0,
                PoiManager.Occupancy.HAS_SPACE);
        check(helper, free.getAsInt() == 0, "held: no room at the lectern");
        villager.setHealth(villager.getMaxHealth() / 2);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MOB_BOTTLE.get()));
        MobBottleItem.tryCapture(player, InteractionHand.MAIN_HAND, villager);
        ItemStack bottle = player.getMainHandItem();
        check(helper, MobBottleItem.captured(bottle) == EntityTypes.VILLAGER, "bottled");
        check(helper, free.getAsInt() == 1, "and the lectern is free again");
        var data = MobBottleItem.capturedData(bottle);
        check(helper, data != null && !data.toString().contains("job_site"), "and the villager in the bottle remembers no workstation");
        helper.succeed();
    }

    /**
     * Water or lava falling onto a pool of mercury lies on top of it, and water falling onto Liquid Experience or
     * vegetable oil leaves its source where it is. (Anything falling took the place of any of them, as it does water's:
     * a bucket of mercury gone under a waterfall, a mob farm's pool of experience under its own water stream.)
     */
    private static void fluidsFallingWashAwayNoneOfOurs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var pools = List.of(
                new BlockPos(1, 1, 1), new BlockPos(5, 1, 1), new BlockPos(1, 1, 5), new BlockPos(5, 1, 5));
        var fluids = List.of(MercuryFluids.SOURCE.get(), MercuryFluids.SOURCE.get(),
                ExperienceFluids.SOURCE.get(), OilFluids.SOURCE.get());
        var falling = List.of(Blocks.WATER, Blocks.LAVA, Blocks.WATER, Blocks.WATER);
        for (int i = 0; i < pools.size(); i++) {
            BlockPos pool = pools.get(i);
            for (int dy = 0; dy <= 3; dy++) {
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    helper.setBlock(pool.above(dy).relative(side), Blocks.STONE);
                }
            }
            helper.setBlock(pool, fluids.get(i).defaultFluidState().createLegacyBlock());
            helper.setBlock(pool.above(3), falling.get(i));
        }
        helper.runAfterDelay(60, () -> {
            for (int i = 0; i < pools.size(); i++) {
                var there = level.getFluidState(helper.absolutePos(pools.get(i)));
                check(helper, there.getType() == fluids.get(i) && there.isSource(),
                        falling.get(i).getName().getString() + " fell on " + fluids.get(i) + " and left " + there.getType());
            }
            helper.succeed();
        });
    }

    /**
     * A barrel item's contents cross the network entry by entry, each sample item read as vanilla reads any item: a
     * full Velarium barrel of named samples goes through whole, and contents no barrel could hold - more entries than
     * the biggest barrel has slots, a slot named twice, a slot out of range - are refused, not read.
     */
    private static void barrelContentsCrossTheNetwork(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        int most = StorageTier.MOST_SLOTS;
        List<StorageContents.Entry> entries = new ArrayList<>();
        for (int slot = 0; slot < most; slot++) {
            ItemStack sample = new ItemStack(Items.WRITTEN_BOOK);
            sample.set(DataComponents.CUSTOM_NAME, Component.literal("volume " + slot + " ".repeat(200)));
            entries.add(new StorageContents.Entry(slot, sample, 1_000_000));
        }
        var full = new StorageContents(entries);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        StorageContents.STREAM_CODEC.encode(buffer, full);
        var back = StorageContents.STREAM_CODEC.decode(buffer);
        check(helper, back.entries().size() == most && back.totalItems() == full.totalItems()
                && ItemStack.isSameItemSameComponents(back.entries().get(7).item(), entries.get(7).item()), "a full barrel of named books goes through whole");

        var twice = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        ByteBufCodecs.VAR_INT.encode(twice, 2);
        StorageContents.Entry.STREAM_CODEC.encode(twice, entries.get(0));
        StorageContents.Entry.STREAM_CODEC.encode(twice, entries.get(0));
        check(helper, refused(twice), "a slot named twice is refused");
        var outside = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        ByteBufCodecs.VAR_INT.encode(outside, 1);
        StorageContents.Entry.STREAM_CODEC.encode(outside, new StorageContents.Entry(most, entries.get(0).item(), 1));
        check(helper, refused(outside), "a slot no barrel has is refused");
        var tooMany = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        ByteBufCodecs.VAR_INT.encode(tooMany, most + 1);
        check(helper, refused(tooMany), "more entries than the biggest barrel has slots are refused before any is read");
        helper.succeed();
    }

    private static boolean refused(RegistryFriendlyByteBuf buffer) {
        try {
            StorageContents.STREAM_CODEC.decode(buffer);
            return false;
        } catch (DecoderException refused) {
            return true;
        }
    }

    /**
     * A spectator's click opens a barrel's screen straight from the barrel, past the block's own use, so the barrel
     * itself sends the client its tier: the client builds the screen from what it is sent. (It was sent nothing, and the
     * spectator lost the connection.)
     */
    private static void barrelOpensForASpectator(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.GOLD_STORAGE.get());
        var barrel = helper.getBlockEntity(pos, StorageBlockEntity.class);
        var player = helper.makeMockPlayer(GameType.SPECTATOR);
        var menu = barrel.createMenu(1, player.getInventory(), player);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        barrel.writeClientSideData(menu, buffer);
        var client = StorageMenu.client(1, player.getInventory(), buffer);
        check(helper, client.slots.size() == menu.slots.size(), "the client's screen is the gold barrel's: " + client.slots.size() + " / " + menu.slots.size());
        helper.succeed();
    }

    /**
     * Each of our crafting recipes, laid out with the first item each ingredient takes, makes only itself: no other
     * recipe - vanilla's or ours - matches the same grid. (The Golden Sorter Gear was laid out exactly as vanilla's
     * Clock: the grid made a clock by hand, with JEI and in a Crafter, and the gear only from the recipe book.)
     */
    private static void recipesNeverOverlap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var crafting = level.getServer().getRecipeManager().getRecipes().stream()
                .filter(holder -> holder.value() instanceof CraftingRecipe).toList();
        List<String> overlaps = new ArrayList<>();
        int checked = 0;
        for (var holder : crafting) {
            if (!holder.id().identifier().getNamespace().equals(Nookworks.MODID)) {
                continue;
            }
            CraftingInput grid = sampleGrid(holder.value());
            if (grid == null) {
                continue;
            }
            checked++;
            for (var other : crafting) {
                if (other != holder && ((CraftingRecipe) other.value()).matches(grid, level)) {
                    overlaps.add(holder.id().identifier() + " is also " + other.id().identifier());
                }
            }
        }
        check(helper, checked > 100, "too few recipes laid out to trust the check: " + checked);
        check(helper, overlaps.isEmpty(), "grids that make something else too: " + overlaps);
        helper.succeed();
    }

    /** A recipe's grid as a player would lay it out, with the first item each ingredient takes; null for a special recipe. */
    private static @Nullable CraftingInput sampleGrid(
            Recipe<?> recipe) {
        List<ItemStack> items = new ArrayList<>();
        if (recipe instanceof ShapedRecipe shaped) {
            for (var ingredient : shaped.getIngredients()) {
                items.add(ingredient.map(ReviewFourTests::firstOf).orElse(ItemStack.EMPTY));
            }
            return CraftingInput.of(shaped.getWidth(), shaped.getHeight(), items);
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            for (var ingredient : shapeless.placementInfo().ingredients()) {
                items.add(firstOf(ingredient));
            }
            while (items.size() < 9) {
                items.add(ItemStack.EMPTY);
            }
            return CraftingInput.of(3, 3, items);
        }
        return null;
    }

    private static ItemStack firstOf(Ingredient ingredient) {
        return ingredient.items().findFirst().map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    /**
     * An Electric Furnace given only enough power to smelt every other tick keeps its fire up rather than lighting and
     * going dark every tick, its light with it. (It flickered ten times a second on one Generator shared with a Crusher.)
     */
    private static void furnaceDoesNotFlicker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.ELECTRIC_FURNACE.get());
        ElectricFurnaceBlockEntity furnace = helper.getBlockEntity(pos, ElectricFurnaceBlockEntity.class);
        furnace.setItem(ElectricFurnaceBlockEntity.INPUT, new ItemStack(Items.RAW_IRON, 16));
        BlockPos at = helper.absolutePos(pos);
        int changes = 0;
        boolean was = false;
        for (int tick = 0; tick < 40; tick++) {
            if (tick % 2 == 0) {
                insert(furnace.energy(), ElectricFurnaceBlockEntity.FE_PER_TICK);
            }
            ElectricFurnaceBlockEntity.serverTick(level, at, level.getBlockState(at), furnace);
            boolean lit = level.getBlockState(at).getValue(ElectricFurnaceBlock.LIT);
            if (lit != was) {
                changes++;
                was = lit;
            }
        }
        check(helper, was && changes == 1, "lit once and kept lit, not " + changes + " changes in 40 ticks");
        helper.succeed();
    }
}
