package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.nookworks.carry.CarriedBlock;
import dev.nookworks.carry.CarryAware;
import dev.nookworks.carry.CarryEvent;
import dev.nookworks.carry.Carrying;
import dev.nookworks.cooking.IronPotBlockEntity;
import dev.nookworks.experience.ExperienceTankBlockEntity;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.mercury.MercuryFluids;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModWoods;
import dev.nookworks.wood.TurnedPlanks;
import dev.nookworks.registry.ModRope;
import dev.nookworks.rope.HungItemEntity;
import dev.nookworks.rope.RopeSpans;
import dev.nookworks.rope.TiedLoadEntity;
import dev.nookworks.rope.TiedLoads;
import dev.nookworks.storage.StorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Carrying blocks (dev.nookworks.carry) and tying them up with rope (dev.nookworks.rope.TiedLoads): taken and put back
 * whole, with nothing spilled, dropped or let out; what may not go, and why; claims; turning; the add-ons' events and
 * hooks; tying and untying by hand, shears and a dispenser; hoisting with the rope's own clicks; a load's save.
 */
final class CarryTests {
    private CarryTests() {}

    // a stand-in block entity of another mod's that wants a word in being carried (registered only in GameTest runs)
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("nookworks_test");
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "nookworks_test");
    static final DeferredBlock<AwareBlock> AWARE = BLOCKS.registerBlock("carry_aware", AwareBlock::new, p -> p);
    static final Supplier<BlockEntityType<Aware>> AWARE_ENTITY = BLOCK_ENTITIES.register("carry_aware",
            () -> new BlockEntityType<>(Aware::new, AWARE.get()));

    static void listen(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }

    static void register() {
        test("carry_takes_and_puts_back_a_chest_whole", CarryTests::chest);
        test("carry_takes_both_halves_of_a_double_chest", CarryTests::doubleChest);
        test("carry_takes_a_full_storage_barrel_with_no_spill", CarryTests::fullBarrel);
        test("carry_takes_a_machine_with_power_mercury_and_leftovers", CarryTests::machine);
        test("carry_takes_an_experience_tank_with_no_orbs", CarryTests::experienceTank);
        test("carry_takes_an_iron_pot_with_what_is_in_it", CarryTests::ironPot);
        test("carry_refuses_what_is_not_a_whole_block_and_says_why", CarryTests::refusals);
        test("carry_asks_claims_where_it_takes_and_where_it_puts", CarryTests::claims);
        test("carry_puts_back_turned", CarryTests::rotation);
        test("carry_put_into_a_blocked_cell_changes_nothing", CarryTests::atomic);
        test("carry_gather_adds_blocks_and_refuses", CarryTests::gather);
        test("carry_takes_a_set_whole_before_its_neighbours_hear", CarryTests::wholeSet);
        test("carry_relocation_tag_gives_way_to_carriable_never_to_not_carriable", CarryTests::relocationTag);
        test("carry_check_event_vetoes", CarryTests::veto);
        test("carry_aware_block_entities_are_told", CarryTests::aware);
        test("carry_every_block_entity_block_of_ours_is_decided", CarryTests::everyBlockDecided);
        test("tied_load_ties_and_unties_by_hand_and_shears", CarryTests::tieByHand);
        test("tied_load_is_untied_by_a_dispenser_with_shears", 100, CarryTests::dispenser);
        test("tied_load_is_hoisted_by_the_ropes_clicks", 200, CarryTests::hoist);
        test("tied_load_ties_and_hoists_a_double_slab", 100, CarryTests::doubleSlab);
        test("tied_load_pull_stops_at_a_block_over_its_other_half", CarryTests::pullBlocked);
        test("tied_load_cannot_be_opened_nor_emptied_by_a_hopper", 100, CarryTests::sealed);
        test("tied_load_is_saved_with_what_it_holds", CarryTests::saved);
        test("tied_load_killed_breaks_open_with_what_it_holds", CarryTests::killed);
        test("tied_load_killed_where_it_cannot_be_set_back_loses_and_makes_nothing", CarryTests::killedBlocked);
        test("tied_load_refused_by_the_world_leaves_the_block_where_it_was", CarryTests::spawnRefused);
        test("tied_load_is_stood_on_as_a_block", 100, CarryTests::stoodOn);
        test("tied_load_and_what_hangs_never_share_a_place", 100, CarryTests::hungApart);
    }

    // ---- the stand-in ----

    /** Counts of its hooks' calls, and the rotation it was last told. */
    static int before;
    static int after;
    static @Nullable Rotation turned;

    static final class AwareBlock extends HorizontalDirectionalBlock implements EntityBlock {
        AwareBlock(BlockBehaviour.Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new Aware(pos, state);
        }
    }

    /** Keeps a side of its own in its data, as a machine's side configuration. */
    static final class Aware extends BlockEntity implements CarryAware {
        Direction side = Direction.NORTH;

        Aware(BlockPos pos, BlockState state) {
            super(AWARE_ENTITY.get(), pos, state);
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            output.putString("side", this.side.getSerializedName());
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            Direction read = Direction.byName(input.getStringOr("side", "north"));
            this.side = read == null ? Direction.NORTH : read;
        }

        @Override
        public void beforeCarried(ServerLevel level) {
            before++;
        }

        @Override
        public void afterPut(ServerLevel level, Rotation rotation) {
            after++;
            turned = rotation;
            this.side = rotation.rotate(this.side);
            this.setChanged();
        }

        @Override
        public void summary(Consumer<Component> lines) {
            lines.accept(Component.literal("side " + this.side.getSerializedName()));
        }
    }

    // ---- helpers ----

    private static String key(@Nullable Component reason) {
        return reason != null && reason.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(reason);
    }

    private static CompoundTag saved(GameTestHelper helper, BlockPos rel) {
        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        return entity == null ? new CompoundTag() : entity.saveWithFullMetadata(helper.getLevel().registryAccess());
    }

    private static int dropped(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(3)).size();
    }

    private static CarriedBlock take(GameTestHelper helper, BlockPos rel) {
        Optional<CarriedBlock> taken = Carrying.take(helper.getLevel(), helper.absolutePos(rel), null);
        check(helper, taken.isPresent(), "taken: " + helper.getBlockState(rel) + " " + key(Carrying.check(helper.getLevel(), helper.absolutePos(rel), null).reason()));
        return taken.get();
    }

    /** Takes the block at {@code rel} and puts it back where it was: the same state, and its block entity saved the same. */
    private static CarriedBlock roundTrip(GameTestHelper helper, BlockPos rel, String what) {
        BlockState state = helper.getBlockState(rel);
        CompoundTag data = saved(helper, rel);
        int drops = dropped(helper);
        CarriedBlock carried = take(helper, rel);
        check(helper, helper.getBlockState(rel).isAir(), what + ": gone from the world once taken");
        check(helper, dropped(helper) == drops, what + ": nothing dropped or spilled at take");
        Carrying.Put put = Carrying.put(helper.getLevel(), helper.absolutePos(rel), Rotation.NONE, carried, null);
        check(helper, put.placed(), what + ": put back, " + put);
        check(helper, helper.getBlockState(rel).equals(state), what + ": the same state, " + helper.getBlockState(rel));
        CompoundTag back = saved(helper, rel);
        check(helper, back.equals(data), what + ": its block entity as it was\n before " + data + "\n after  " + back);
        return carried;
    }

    private static ServerPlayer player(GameTestHelper helper, List<Component> said) {
        ServerPlayer player = MockPlayers.hearing(helper, GameType.SURVIVAL, said);
        Vec3 aside = helper.absoluteVec(new Vec3(0.5, 8, 0.5));
        player.snapTo(aside.x, aside.y, aside.z);
        player.getAbilities().flying = true; // one falling to the void would hang the batch
        player.onUpdateAbilities();
        return player;
    }

    private static boolean saidKey(List<Component> said, String key) {
        return said.stream().anyMatch(c -> key.equals(key(c)));
    }

    private static void use(GameTestHelper helper, ServerPlayer player, BlockPos rel, Direction face) {
        BlockPos at = helper.absolutePos(rel);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5)), face, at, false));
    }

    private static @Nullable TiedLoadEntity loadAt(GameTestHelper helper, BlockPos rel) {
        return TiedLoads.at(helper.getLevel(), helper.absolutePos(rel));
    }

    private static ChestBlockEntity chestWith(GameTestHelper helper, BlockPos rel, BlockState state, int diamonds) {
        helper.setBlock(rel, state);
        ChestBlockEntity chest = helper.getBlockEntity(rel, ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.DIAMOND, diamonds));
        ItemStack named = new ItemStack(Items.IRON_SWORD);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Kept"));
        chest.setItem(13, named);
        return chest;
    }

    // ---- taking and putting ----

    private static void chest(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2);
        chestWith(helper, at, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST), 5);
        CarriedBlock carried = roundTrip(helper, at, "a chest");
        check(helper, carried.parts().size() == 1 && carried.bytes() < CarriedBlock.MAX_BYTES, "one part, and small");
        check(helper, helper.getBlockEntity(at, ChestBlockEntity.class).getItem(0).getCount() == 5, "its diamonds in it again");
        helper.succeed();
    }

    private static void doubleChest(GameTestHelper helper) {
        BlockPos left = new BlockPos(2, 1, 2);
        BlockState leftState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT);
        BlockPos right = left.relative(ChestBlock.getConnectedDirection(leftState));
        chestWith(helper, left, leftState, 3);
        chestWith(helper, right, leftState.setValue(ChestBlock.TYPE, ChestType.RIGHT), 7);
        CompoundTag leftData = saved(helper, left);
        CompoundTag rightData = saved(helper, right);
        CarriedBlock carried = take(helper, right);
        check(helper, carried.parts().size() == 2 && helper.getBlockState(left).isAir() && helper.getBlockState(right).isAir(),
                "both halves taken: " + carried.parts().size());
        check(helper, dropped(helper) == 0, "nothing spilled");
        check(helper, Carrying.put(helper.getLevel(), helper.absolutePos(right), Rotation.NONE, carried, null).placed(), "put back");
        check(helper, helper.getBlockState(left).getValue(ChestBlock.TYPE) == ChestType.LEFT
                && helper.getBlockState(right).getValue(ChestBlock.TYPE) == ChestType.RIGHT, "a double chest again");
        check(helper, saved(helper, left).equals(leftData) && saved(helper, right).equals(rightData), "each half with what it held");
        helper.succeed();
    }

    private static void fullBarrel(GameTestHelper helper) {
        StorageBlockEntity storage = StorageTests.placeStorage(helper, ModBlocks.DIAMOND_STORAGE.get());
        int each = (int) storage.inventory().getCapacityAsLong(0, ItemResource.of(Items.COBBLESTONE));
        for (int i = 0; i < storage.inventory().size(); i++) {
            storage.inventory().set(i, ItemResource.of(Items.COBBLESTONE), each);
        }
        check(helper, storage.tooFullToBreak(), "too full to break (the case a broken barrel would leave whole or spill)");
        roundTrip(helper, StorageTests.STORAGE_POS, "a full Storage Barrel");
        check(helper, StorageTests.storageTotal(helper.getBlockEntity(StorageTests.STORAGE_POS, StorageBlockEntity.class), Items.COBBLESTONE)
                == each * storage.inventory().size(), "every stone in it again");
        helper.succeed();
    }

    private static void machine(GameTestHelper helper) {
        BlockPos at = new BlockPos(3, 1, 3);
        helper.setBlock(at, ModBlocks.RETORT.get());
        MachineBlockEntity retort = helper.getBlockEntity(at, MachineBlockEntity.class);
        CompoundTag old = new CompoundTag();
        net.minecraft.nbt.ListTag leftovers = new net.minecraft.nbt.ListTag();
        CompoundTag diamond = new CompoundTag();
        diamond.putString("id", "minecraft:diamond");
        diamond.putInt("count", 3);
        leftovers.add(diamond);
        old.put("leftovers", leftovers);
        retort.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), old));
        ItemStack kept = new ItemStack(Items.STONE);
        kept.set(ModDataComponents.HELD_ENERGY.get(), 5_000);
        kept.set(ModDataComponents.HELD_FLUIDS.get(), List.of(SimpleFluidContent.copyOf(new FluidStack(MercuryFluids.SOURCE.get(), 500))));
        retort.applyComponentsFromItemStack(kept);
        check(helper, retort.hasLeftovers() && retort.energy().getAmountAsLong() > 0, "a Retort with power and leftovers");
        roundTrip(helper, at, "a Retort");
        MachineBlockEntity back = helper.getBlockEntity(at, MachineBlockEntity.class);
        check(helper, back.hasLeftovers() && back.energy().getAmountAsLong() == retort.energy().getAmountAsLong(),
                "its leftovers and power kept, not dropped");
        helper.succeed();
    }

    private static void experienceTank(GameTestHelper helper) {
        BlockPos at = new BlockPos(3, 1, 3);
        helper.setBlock(at, ModBlocks.EXPERIENCE_TANK.get());
        ExperienceTankBlockEntity tank = helper.getBlockEntity(at, ExperienceTankBlockEntity.class);
        tank.fill(1_500);
        int amount = tank.amount();
        check(helper, amount > 0, "a tank with experience in it");
        roundTrip(helper, at, "an Experience Tank");
        helper.runAfterDelay(2, () -> {
            check(helper, helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, helper.getBounds().inflate(3)).isEmpty(), "no orbs let out");
            check(helper, helper.getBlockEntity(at, ExperienceTankBlockEntity.class).amount() == amount, "all of it in the tank again");
            helper.succeed();
        });
    }

    private static void ironPot(GameTestHelper helper) {
        BlockPos at = new BlockPos(3, 1, 3);
        helper.setBlock(at, ModCooking.IRON_POT.get());
        IronPotBlockEntity pot = helper.getBlockEntity(at, IronPotBlockEntity.class);
        check(helper, pot.addIngredient(new ItemStack(Items.CARROT)) && pot.addIngredient(new ItemStack(Items.POTATO)), "two things in the pot");
        roundTrip(helper, at, "an Iron Pot");
        check(helper, helper.getBlockEntity(at, IronPotBlockEntity.class).count() == 2, "both in it again");
        helper.succeed();
    }

    private static void refusals(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        record Case(BlockPos at, String key) {}
        List<Case> cases = new ArrayList<>();
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.OAK_FENCE);
        cases.add(new Case(new BlockPos(1, 1, 1), "message.nookworks.carry.not_whole"));
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.OAK_STAIRS);
        cases.add(new Case(new BlockPos(2, 1, 1), "message.nookworks.carry.not_whole"));
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.OAK_SLAB);
        cases.add(new Case(new BlockPos(3, 1, 1), "message.nookworks.carry.not_whole"));
        helper.setBlock(new BlockPos(4, 1, 1), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(new BlockPos(4, 2, 1), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        cases.add(new Case(new BlockPos(4, 1, 1), "message.nookworks.carry.not_whole"));
        helper.setBlock(new BlockPos(5, 1, 1), Blocks.GLASS_PANE);
        cases.add(new Case(new BlockPos(5, 1, 1), "message.nookworks.carry.not_whole"));
        helper.setBlock(new BlockPos(1, 1, 3), Blocks.SPAWNER);
        cases.add(new Case(new BlockPos(1, 1, 3), "message.nookworks.carry.not_carriable"));
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.BEDROCK);
        cases.add(new Case(new BlockPos(2, 1, 3), "message.nookworks.carry.not_carriable"));
        // a chest too heavy as saved: 27 stacks each carrying 3 KB of data, 81 KB in all
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.CHEST);
        ChestBlockEntity heavy = helper.getBlockEntity(new BlockPos(3, 1, 3), ChestBlockEntity.class);
        for (int i = 0; i < heavy.getContainerSize(); i++) {
            CompoundTag big = new CompoundTag();
            big.putString("text", ("slot " + i + " ").repeat(400));
            ItemStack stack = new ItemStack(Items.PAPER);
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.of(big));
            heavy.setItem(i, stack);
        }
        cases.add(new Case(new BlockPos(3, 1, 3), "message.nookworks.carry.too_much"));
        for (Case c : cases) {
            BlockState was = helper.getBlockState(c.at());
            Carrying.Check check = Carrying.check(level, helper.absolutePos(c.at()), null);
            check(helper, !check.isOk() && c.key().equals(key(check.reason())), was + ": refused with " + c.key() + ", was " + key(check.reason()));
            check(helper, check.why() != null && check.why().key().equals(c.key()), was + ": and says which, " + check.why());
            check(helper, Carrying.take(level, helper.absolutePos(c.at()), null).isEmpty() && helper.getBlockState(c.at()).equals(was),
                    was + ": not taken, left as it was");
            check(helper, !Carrying.couldCarry(was) || c.key().equals("message.nookworks.carry.too_much"),
                    was + ": couldCarry agrees on shape and tags");
        }
        // every reason in Nookworks' words for any carrier; the five at taking in the rope's too
        for (Carrying.Why why : Carrying.Why.values()) {
            check(helper, Language.getInstance().has(why.key()), why + ": worded, " + why.key());
        }
        for (String id : List.of("not_whole", "not_carriable", "too_much", "too_many", "not_yours")) {
            check(helper, Language.getInstance().has("message.nookworks.tied_load." + id), id + ": worded for the rope");
        }
        // and said over the hotbar, in the rope's words ("can't be tied up"), to one who tries it with rope
        List<Component> said = new ArrayList<>();
        ServerPlayer player = player(helper, said);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROPE.get(), 4));
        use(helper, player, new BlockPos(1, 1, 1), Direction.UP);
        check(helper, saidKey(said, "message.nookworks.tied_load.not_whole") && player.getMainHandItem().getCount() == 4,
                "a fence: said why, no rope used: " + said);
        helper.succeed();
    }

    private static void claims(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(2, 1, 2);
        BlockPos at = helper.absolutePos(rel);
        chestWith(helper, rel, Blocks.CHEST.defaultBlockState(), 4);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        UUID owner = UUID.randomUUID();
        BlockTests.underClaim(owner, () -> {
            Carrying.Check check = Carrying.check(level, at, player);
            check(helper, !check.isOk() && "message.nookworks.carry.not_yours".equals(key(check.reason())), "in another's claim: not taken, " + key(check.reason()));
            check(helper, Carrying.take(level, at, player).isEmpty() && helper.getBlockState(rel).is(Blocks.CHEST), "the chest left there");
        });
        CarriedBlock carried = take(helper, rel);
        BlockTests.underClaim(owner, () -> {
            Carrying.Put put = Carrying.put(level, at, Rotation.NONE, carried, player);
            check(helper, put instanceof Carrying.Put.Refused refused && "message.nookworks.carry.not_here".equals(key(refused.reason()))
                    && put.why() == Carrying.Why.NOT_HERE, "in another's claim: not put down, " + put);
            check(helper, helper.getBlockState(rel).isAir() && dropped(helper) == 0, "the cell as it was");
        });
        check(helper, Carrying.put(level, at, Rotation.NONE, carried, player).placed(), "out of it, put down");
        helper.succeed();
    }

    private static void rotation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos furnace = new BlockPos(1, 1, 1);
        helper.setBlock(furnace, Blocks.FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        CarriedBlock carried = take(helper, furnace);
        check(helper, Carrying.put(level, helper.absolutePos(furnace), Rotation.CLOCKWISE_90, carried, null).placed()
                && helper.getBlockState(furnace).getValue(HorizontalDirectionalBlock.FACING) == Direction.EAST, "turned a quarter: faces east");

        BlockPos left = new BlockPos(2, 1, 3);
        BlockState leftState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT);
        chestWith(helper, left, leftState, 2);
        chestWith(helper, left.relative(ChestBlock.getConnectedDirection(leftState)), leftState.setValue(ChestBlock.TYPE, ChestType.RIGHT), 2);
        CarriedBlock pair = take(helper, left);
        BlockPos to = new BlockPos(4, 1, 2);
        check(helper, Carrying.put(level, helper.absolutePos(to), Rotation.CLOCKWISE_90, pair, null).placed(), "the pair put down turned");
        BlockState first = helper.getBlockState(to);
        BlockState second = helper.getBlockState(to.south());
        check(helper, first.is(Blocks.CHEST) && first.getValue(ChestBlock.FACING) == Direction.EAST && first.getValue(ChestBlock.TYPE) == ChestType.LEFT
                && second.is(Blocks.CHEST) && second.getValue(ChestBlock.TYPE) == ChestType.RIGHT
                && ChestBlock.getConnectedDirection(first) == Direction.SOUTH, "a double chest facing east, its halves side by side: " + first + " " + second);
        helper.succeed();
    }

    private static void atomic(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 1, 1);
        chestWith(helper, rel, Blocks.CHEST.defaultBlockState(), 6);
        CarriedBlock carried = take(helper, rel);
        BlockPos stone = new BlockPos(3, 1, 3);
        helper.setBlock(stone, Blocks.STONE);
        CarriedBlock copy = new CarriedBlock(carried.parts());
        check(helper, Carrying.put(level, helper.absolutePos(stone), Rotation.NONE, carried, null) == Carrying.Put.BLOCKED
                && Carrying.Put.BLOCKED.why() == Carrying.Why.BLOCKED && "message.nookworks.carry.blocked".equals(key(Carrying.Put.BLOCKED.reason())),
                "into stone: blocked");
        check(helper, helper.getBlockState(stone).is(Blocks.STONE) && carried.equals(copy), "the stone left, the value unchanged");
        // a pair whose second cell is taken: neither is set down
        BlockPos left = new BlockPos(1, 1, 4);
        BlockState leftState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT);
        chestWith(helper, left, leftState, 1);
        chestWith(helper, left.east(), leftState.setValue(ChestBlock.TYPE, ChestType.RIGHT), 1);
        CarriedBlock pair = take(helper, left);
        BlockPos at = new BlockPos(4, 1, 4);
        helper.setBlock(at.east(), Blocks.DIRT);
        check(helper, Carrying.put(level, helper.absolutePos(at), Rotation.NONE, pair, null) == Carrying.Put.BLOCKED
                && helper.getBlockState(at).isAir() && helper.getBlockState(at.east()).is(Blocks.DIRT), "half blocked: nothing set down");
        check(helper, !Carrying.canPut(level, helper.absolutePos(at), Rotation.NONE, pair, null)
                && Carrying.canPut(level, helper.absolutePos(left), Rotation.NONE, pair, null), "canPut says the same");
        helper.succeed();
    }

    private static void gather(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos b = helper.absolutePos(new BlockPos(4, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.DIAMOND_BLOCK);
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.GOLD_BLOCK);
        helper.setBlock(new BlockPos(4, 1, 1), Blocks.EMERALD_BLOCK);
        Consumer<CarryEvent.Gather> listener = event -> {
            if (event.getPos().equals(a)) {
                event.set().add(a.above()); // a two-high block of an add-on's
            } else if (event.getPos().equals(b)) {
                event.refuse(Component.literal("part of something bigger"));
            }
        };
        NeoForge.EVENT_BUS.addListener(CarryEvent.Gather.class, listener);
        AfterTest.undo(helper, () -> NeoForge.EVENT_BUS.unregister(listener));
        CarriedBlock two = take(helper, new BlockPos(1, 1, 1));
        check(helper, two.parts().size() == 2 && helper.getBlockState(new BlockPos(1, 2, 1)).isAir(), "the block over it went too");
        check(helper, Carrying.put(level, a, Rotation.NONE, two, null).placed() && helper.getBlockState(new BlockPos(1, 2, 1)).is(Blocks.GOLD_BLOCK),
                "and came back with it");
        Carrying.Check refused = Carrying.check(level, b, null);
        check(helper, !refused.isOk() && refused.reason() != null && refused.reason().getString().equals("part of something bigger"),
                "refused by the add-on, its reason given");
        helper.succeed();
    }

    /**
     * The set is removed whole before any neighbour hears (nookTechnology, 2026-11-10): taken a part at a time, the
     * other half of a two-high block heard its partner go and broke as a door's does, dropping a copy of what was being
     * carried (its Large Battery). Here a stone with a redstone torch on it, gathered as one: none dropped. A torch on the
     * stone's side, not in the set, still hears it go and drops, as from any block removed.
     */
    private static void wholeSet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos stone = new BlockPos(2, 1, 2);
        BlockPos at = helper.absolutePos(stone);
        helper.setBlock(stone, Blocks.STONE);
        helper.setBlock(stone.above(), Blocks.REDSTONE_TORCH.defaultBlockState().setValue(RedstoneTorchBlock.LIT, false));
        helper.setBlock(stone.east(), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST));
        Consumer<CarryEvent.Gather> listener = event -> {
            if (event.getPos().equals(at)) {
                event.set().add(at.above()); // a block that stands only on the other, as a two-high one's top half
            }
        };
        NeoForge.EVENT_BUS.addListener(CarryEvent.Gather.class, listener);
        AfterTest.undo(helper, () -> NeoForge.EVENT_BUS.unregister(listener));
        CarriedBlock carried = take(helper, stone);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(3));
        check(helper, carried.parts().size() == 2 && carried.parts().get(1).state().is(Blocks.REDSTONE_TORCH),
                "the torch on it carried with it: " + carried.parts());
        check(helper, drops.stream().noneMatch(e -> e.getItem().is(Items.REDSTONE_TORCH)), "and not dropped: " + drops);
        check(helper, drops.stream().anyMatch(e -> e.getItem().is(Items.TORCH)) && helper.getBlockState(stone.east()).isAir(),
                "the torch on its side heard it go: " + drops);
        check(helper, Carrying.put(level, at, Rotation.NONE, carried, null).placed() && helper.getBlockState(stone).is(Blocks.STONE)
                && helper.getBlockState(stone.above()).is(Blocks.REDSTONE_TORCH), "put back, the torch on it again");
        helper.succeed();
    }

    /**
     * {@code #c:relocation_not_supported} keeps other mods' movers off a block (Create's contraptions): it keeps a carry
     * off too, unless the block is in {@code #nookworks:carriable} - an add-on's block a carry moves whole though a
     * contraption would break it (nookTechnology's Large Battery, Chunk Loader and Mob Data Bank, 2026-11-10). Never one
     * in {@code #nookworks:not_carriable}. Ours in it are carried by nothing: the Item Warps, the Drive Wheel.
     */
    private static void relocationTag(GameTestHelper helper) {
        record Row(boolean relocation, boolean carriable, boolean notCarriable, boolean lets) {}
        for (Row row : List.of(new Row(false, false, false, true), new Row(true, false, false, false),
                new Row(true, true, false, true), new Row(false, true, true, false), new Row(true, true, true, false))) {
            Predicate<TagKey<Block>> in = tag -> tag.equals(Tags.Blocks.RELOCATION_NOT_SUPPORTED) ? row.relocation()
                    : tag.equals(Carrying.CARRIABLE) ? row.carriable() : tag.equals(Carrying.NOT_CARRIABLE) && row.notCarriable();
            check(helper, Carrying.tagsLet(in) == row.lets(), "tags " + row);
        }
        ServerLevel level = helper.getLevel();
        BlockPos warp = new BlockPos(2, 1, 2);
        helper.setBlock(warp, ModBlocks.ITEM_WARP_SENDER.get());
        BlockState state = helper.getBlockState(warp);
        check(helper, state.is(Tags.Blocks.RELOCATION_NOT_SUPPORTED) && !state.is(Carrying.CARRIABLE), "the Item Warp is Create's to leave");
        Carrying.Check check = Carrying.check(level, helper.absolutePos(warp), null);
        check(helper, check.why() == Carrying.Why.NOT_CARRIABLE && !Carrying.couldCarry(state), "and not carried: " + check);
        // a chest carries by being a chest (#c:chests), not by the carriable tag, so the tag lets no chest of another
        // mod's past the relocation tag
        check(helper, !Blocks.CHEST.defaultBlockState().is(Carrying.CARRIABLE) && Carrying.couldCarry(Blocks.CHEST.defaultBlockState()),
                "a chest carries though not tagged carriable");
        helper.succeed();
    }

    private static void veto(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.BARREL);
        Consumer<CarryEvent.Check> listener = event -> {
            if (event.set().contains(at)) {
                event.refuse(Component.literal("still working"));
            }
        };
        NeoForge.EVENT_BUS.addListener(CarryEvent.Check.class, listener);
        AfterTest.undo(helper, () -> NeoForge.EVENT_BUS.unregister(listener));
        Carrying.Check check = Carrying.check(level, at, null);
        check(helper, !check.isOk() && "still working".equals(check.reason().getString()), "vetoed, with the add-on's reason");
        check(helper, Carrying.take(level, at, null).isEmpty() && helper.getBlockState(new BlockPos(2, 1, 2)).is(Blocks.BARREL), "and left there");
        helper.succeed();
    }

    private static void aware(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(2, 1, 2);
        helper.setBlock(rel, AWARE.get().defaultBlockState());
        helper.getBlockEntity(rel, Aware.class).side = Direction.NORTH;
        int was = before;
        int put = after;
        CarriedBlock carried = take(helper, rel);
        check(helper, before == was + 1, "told before it is carried");
        List<Component> lines = new ArrayList<>();
        carried.summary(level.registryAccess(), lines::add);
        check(helper, lines.size() == 2 && lines.get(1).getString().equals("side north"), "its own summary line: " + lines);
        check(helper, Carrying.put(level, helper.absolutePos(rel), Rotation.CLOCKWISE_90, carried, null).placed(), "put down turned");
        check(helper, after == put + 1 && turned == Rotation.CLOCKWISE_90 && helper.getBlockEntity(rel, Aware.class).side == Direction.EAST,
                "told after, with the turn: its side turned with it");
        // a chest has no summary of its own: its stacks are counted
        chestWith(helper, new BlockPos(4, 1, 4), Blocks.CHEST.defaultBlockState(), 2);
        List<Component> chestLines = new ArrayList<>();
        take(helper, new BlockPos(4, 1, 4)).summary(level.registryAccess(), chestLines::add);
        check(helper, chestLines.size() == 2 && "tooltip.nookworks.carried.stacks".equals(key(chestLines.get(1))), "a chest: its stacks counted, " + chestLines);
        helper.succeed();
    }

    /**
     * Every block of ours with a block entity is either carried (a full block, or tagged carriable), kept off by a tag
     * (not carriable, or Create's relocation one), or left out on purpose: listed here, so a new one is decided, not
     * forgotten.
     */
    private static final Set<String> LEFT_OUT = Set.of(
            // lines and their fittings: pipes run on through the next block, a pad lies on the floor
            "copper_pipe", "experience_pipe", "valve", "sight_glass", "absorption_pad",
            // small things set on or against blocks: books, bottles, a plate, a tray, dice, a grill under a pot
            "book_row", "bookend", "placed_books", "placed_bottles", "plate", "wooden_tray", "dice", "grill",
            // signs and a shelf, as vanilla's; a redstone timer; a mirror; a counter a villager is bound to
            "dawnwood_sign", "dawnwood_wall_sign", "dawnwood_hanging_sign", "dawnwood_wall_hanging_sign", "dawnwood_shelf",
            "nested_timer", "standing_mirror", "trading_counter",
            // unbreakable, as everything unbreakable is refused
            "creative_generator");

    private static void everyBlockDecided(GameTestHelper helper) {
        Set<String> undecided = new TreeSet<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            var id = BuiltInRegistries.BLOCK.getKey(block);
            BlockState state = block.defaultBlockState();
            if (!id.getNamespace().equals("nookworks") || !state.hasBlockEntity() || LEFT_OUT.contains(id.getPath())) {
                continue;
            }
            if (!Carrying.couldCarry(state) && Carrying.tagsLet(state::is)) {
                undecided.add(id.getPath());
            }
        }
        check(helper, undecided.isEmpty(), "blocks of ours with a block entity neither carried nor left out on purpose: " + undecided);
        helper.succeed();
    }

    // ---- tying ----

    private static void tieByHand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Component> said = new ArrayList<>();
        ServerPlayer player = player(helper, said);
        BlockPos rel = new BlockPos(2, 1, 2);
        chestWith(helper, rel, Blocks.CHEST.defaultBlockState(), 9);
        CompoundTag data = saved(helper, rel);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROPE.get(), 3));
        use(helper, player, rel, Direction.UP);
        TiedLoadEntity load = loadAt(helper, rel);
        check(helper, load != null && helper.getBlockState(rel).isAir() && player.getMainHandItem().getCount() == 2,
                "sneak + rope: tied, a rope used: " + helper.getBlockState(rel) + " " + player.getMainHandItem());
        check(helper, load.getName().getContents() instanceof TranslatableContents name && name.getKey().equals("entity.nookworks.tied_load.of")
                && name.getArgs().length == 1 && name.getArgs()[0] instanceof Component block && block.equals(Blocks.CHEST.getName()),
                "named after its block: " + load.getName());
        // a plain click: it is tied, and says so
        player.setShiftKeyDown(false);
        ItemStack rope = player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        load.interact(player, InteractionHand.MAIN_HAND, load.position());
        check(helper, saidKey(said, "message.nookworks.tied_load.tied") && !load.isRemoved(), "a plain click: said it is tied");
        // sneak + an empty hand: untied, the rope given back
        player.setShiftKeyDown(true);
        load.interact(player, InteractionHand.MAIN_HAND, load.position());
        check(helper, load.isRemoved() && helper.getBlockState(rel).is(Blocks.CHEST) && saved(helper, rel).equals(data), "untied: the chest as it was");
        check(helper, player.getInventory().countItem(ModItems.ROPE.get()) == 1, "the rope given back");
        // shears: untied, the rope dropped, the shears worn
        player.setItemInHand(InteractionHand.MAIN_HAND, rope);
        use(helper, player, rel, Direction.NORTH);
        TiedLoadEntity again = loadAt(helper, rel);
        check(helper, again != null, "tied again from its side");
        ItemStack shears = new ItemStack(Items.SHEARS);
        player.setItemInHand(InteractionHand.MAIN_HAND, shears);
        player.setShiftKeyDown(false);
        int drops = dropped(helper);
        again.interact(player, InteractionHand.MAIN_HAND, again.position());
        check(helper, again.isRemoved() && helper.getBlockState(rel).is(Blocks.CHEST), "shears untie it");
        check(helper, dropped(helper) == drops + 1 && player.getMainHandItem().getDamageValue() == 1, "the rope dropped, the shears worn");
        // the underside of a block a rope can hang from: the rope is hung, as it always was
        BlockPos ceiling = new BlockPos(5, 4, 5);
        helper.setBlock(ceiling, Blocks.STONE);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROPE.get(), 2));
        use(helper, player, ceiling, Direction.DOWN);
        check(helper, helper.getBlockState(ceiling).is(Blocks.STONE) && helper.getBlockState(ceiling.below()).is(ModBlocks.ROPE.get()),
                "clicked from below: the rope hung under it, nothing tied");
        // in creative, no rope is used
        player.setGameMode(GameType.CREATIVE);
        BlockPos barrel = new BlockPos(4, 1, 1);
        helper.setBlock(barrel, Blocks.BARREL);
        use(helper, player, barrel, Direction.UP);
        check(helper, loadAt(helper, barrel) != null && player.getMainHandItem().getCount() == 1, "creative: tied, no rope used");
        helper.succeed();
    }

    private static void dispenser(GameTestHelper helper) {
        BlockPos rel = new BlockPos(3, 1, 3);
        chestWith(helper, rel, Blocks.CHEST.defaultBlockState(), 2);
        TiedLoadEntity load = TiedLoads.tie(helper.getLevel(), helper.absolutePos(rel), null);
        check(helper, load != null, "tied");
        BlockPos dispenser = rel.west();
        helper.setBlock(dispenser, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        helper.getBlockEntity(dispenser, DispenserBlockEntity.class).setItem(0, new ItemStack(Items.SHEARS));
        helper.setBlock(dispenser.north(), Blocks.REDSTONE_BLOCK);
        helper.succeedWhen(() -> {
            check(helper, load.isRemoved() && helper.getBlockState(rel).is(Blocks.CHEST), "untied by the dispenser");
            check(helper, helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2),
                    item -> item.getItem().is(ModItems.ROPE.get())).size() == 1, "its rope dropped");
            check(helper, helper.getBlockEntity(dispenser, DispenserBlockEntity.class).getItem(0).getDamageValue() == 1, "the shears worn");
        });
    }

    private static void hoist(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Component> said = new ArrayList<>();
        ServerPlayer player = player(helper, said);
        // a rope three lengths long from a stone overhead, a chest tied up right under its end, in the air
        helper.setBlock(new BlockPos(3, 6, 3), Blocks.STONE);
        for (int y = 5; y >= 3; y--) {
            helper.setBlock(new BlockPos(3, y, 3), ModBlocks.ROPE.get());
        }
        chestWith(helper, new BlockPos(3, 2, 3), Blocks.CHEST.defaultBlockState(), 4);
        TiedLoadEntity load = TiedLoads.tie(level, helper.absolutePos(new BlockPos(3, 2, 3)), null);
        check(helper, load != null && load.held(), "tied, held by the rope");
        int floor = helper.absolutePos(BlockPos.ZERO).getY();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // pulled up a length: it comes up with the rope
        use(helper, player, new BlockPos(3, 5, 3), Direction.NORTH);
        check(helper, !helper.getBlockState(new BlockPos(3, 3, 3)).is(ModBlocks.ROPE.get()) && load.getY() == floor + 3,
                "pulled up: the rope's end and the load a block higher, " + (load.getY() - floor));
        check(helper, player.getInventory().countItem(ModItems.ROPE.get()) == 1, "the length pulled up in hand");
        // let down: rope on the rope, the load goes down and the rope with it
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROPE.get(), 4));
        use(helper, player, new BlockPos(3, 5, 3), Direction.NORTH);
        check(helper, load.getY() == floor + 2 && helper.getBlockState(new BlockPos(3, 3, 3)).is(ModBlocks.ROPE.get()), "let down a block");
        use(helper, player, new BlockPos(3, 5, 3), Direction.NORTH);
        check(helper, load.getY() == floor + 1 && helper.getBlockState(new BlockPos(3, 2, 3)).is(ModBlocks.ROPE.get()), "and down to the ground");
        // on the ground: no further, said why
        int ropes = player.getMainHandItem().getCount();
        use(helper, player, new BlockPos(3, 5, 3), Direction.NORTH);
        check(helper, load.getY() == floor + 1 && player.getMainHandItem().getCount() == ropes && saidKey(said, "message.nookworks.rope.no_lower"),
                "on the ground: no lower, nothing used, said why");
        // up to the top: the last length is not pulled up from under it
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        for (int i = 0; i < 3; i++) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); // the length pulled up comes to hand
            use(helper, player, new BlockPos(3, 5, 3), Direction.NORTH);
        }
        check(helper, load.getY() == floor + 4 && helper.getBlockState(new BlockPos(3, 5, 3)).is(ModBlocks.ROPE.get()), "wound up to the last length");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        use(helper, player, new BlockPos(3, 5, 3), Direction.NORTH);
        check(helper, load.getY() == floor + 4 && helper.getBlockState(new BlockPos(3, 5, 3)).is(ModBlocks.ROPE.get())
                && saidKey(said, "message.nookworks.tied_load.at_top"), "the last length stays, said why");
        // it hangs there; the rope cut, it falls, and rests on the ground still tied
        helper.runAfterDelay(10, () -> {
            check(helper, load.getY() == floor + 4, "hangs where it was stopped");
            level.removeBlock(helper.absolutePos(new BlockPos(3, 5, 3)), false);
            helper.runAfterDelay(40, () -> {
                check(helper, !load.isRemoved() && load.getY() == floor + 1 && load.aligned(), "fell to the ground, still tied: " + (load.getY() - floor));
                helper.succeed();
            });
        });
    }

    /**
     * Two slabs set into one block make a whole block, and are tied and hoisted as one (the user, 2026-11-10:
     * 「ハーフブロック2個積んでフルブロックみたいになってるやつは、縛って持ち上げられるの？」): vanilla's, Dawnwood's, a turned one;
     * untied, still double. A single slab is not (refusals).
     */
    private static void doubleSlab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Component> said = new ArrayList<>();
        ServerPlayer player = player(helper, said);
        List<Block> slabs = List.of(Blocks.OAK_SLAB, Blocks.SMOOTH_STONE_SLAB, ModWoods.DAWNWOOD_SLAB.get(), TurnedPlanks.SLABS.get("spruce").get());
        for (int i = 0; i < slabs.size(); i++) {
            BlockPos rel = new BlockPos(1 + i, 1, 1);
            BlockState doubled = slabs.get(i).defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
            helper.setBlock(rel, doubled);
            check(helper, Carrying.check(level, helper.absolutePos(rel), null).isOk(), doubled + ": whole, so carried");
            player.setShiftKeyDown(true);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROPE.get(), 2));
            use(helper, player, rel, Direction.UP);
            TiedLoadEntity load = loadAt(helper, rel);
            check(helper, load != null && helper.getBlockState(rel).isAir() && player.getMainHandItem().getCount() == 1,
                    doubled + ": tied, a rope used " + said);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            load.interact(player, InteractionHand.MAIN_HAND, load.position());
            check(helper, load.isRemoved() && helper.getBlockState(rel).equals(doubled), doubled + ": untied, double still: " + helper.getBlockState(rel));
        }
        // hoisted: a rope from a stone overhead, the double slab tied under its end, pulled up a length
        helper.setBlock(new BlockPos(3, 6, 3), Blocks.STONE);
        for (int y = 5; y >= 3; y--) {
            helper.setBlock(new BlockPos(3, y, 3), ModBlocks.ROPE.get());
        }
        BlockState doubled = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
        helper.setBlock(new BlockPos(3, 2, 3), doubled);
        TiedLoadEntity load = TiedLoads.tie(level, helper.absolutePos(new BlockPos(3, 2, 3)), null);
        check(helper, load != null && load.held(), "tied under the rope, held by it");
        double y = load.getY();
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        use(helper, player, new BlockPos(3, 5, 3), Direction.NORTH);
        check(helper, load.getY() == y + 1, "pulled up a length with the rope: " + (load.getY() - y));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROPE.get(), 2));
        use(helper, player, new BlockPos(3, 5, 3), Direction.NORTH);
        check(helper, load.getY() == y, "and let down again");
        helper.succeed();
    }

    private static void pullBlocked(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Component> said = new ArrayList<>();
        ServerPlayer player = player(helper, said);
        helper.setBlock(new BlockPos(3, 6, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 5, 3), ModBlocks.ROPE.get());
        helper.setBlock(new BlockPos(3, 4, 3), ModBlocks.ROPE.get());
        BlockPos left = new BlockPos(3, 3, 3);
        BlockState leftState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH).setValue(ChestBlock.TYPE, ChestType.LEFT);
        BlockPos right = left.relative(ChestBlock.getConnectedDirection(leftState));
        helper.setBlock(left.below(), Blocks.STONE);
        helper.setBlock(right.below(), Blocks.STONE);
        chestWith(helper, left, leftState, 1);
        chestWith(helper, right, leftState.setValue(ChestBlock.TYPE, ChestType.RIGHT), 1);
        helper.setBlock(right.above(), Blocks.OAK_PLANKS);
        TiedLoadEntity load = TiedLoads.tie(level, helper.absolutePos(left), null);
        check(helper, load != null && load.cells().size() == 2, "the pair tied as one");
        double y = load.getY();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        use(helper, player, new BlockPos(3, 5, 3), Direction.NORTH);
        check(helper, load.getY() == y && helper.getBlockState(new BlockPos(3, 4, 3)).is(ModBlocks.ROPE.get())
                && saidKey(said, "message.nookworks.tied_load.no_room"), "a block over its other half: not pulled up, said why");
        // Thin Rope holds no load: let down onto one, it goes no lower
        helper.setBlock(new BlockPos(5, 6, 5), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 5, 5), ModBlocks.THIN_ROPE.get());
        helper.setBlock(new BlockPos(5, 3, 5), Blocks.BARREL);
        TiedLoadEntity barrel = TiedLoads.tie(level, helper.absolutePos(new BlockPos(5, 3, 5)), null);
        helper.setBlock(new BlockPos(5, 4, 5), ModBlocks.THIN_ROPE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.THIN_ROPE.get(), 2));
        said.clear();
        use(helper, player, new BlockPos(5, 5, 5), Direction.NORTH);
        check(helper, barrel != null && !barrel.held() && player.getMainHandItem().getCount() == 2
                && saidKey(said, "message.nookworks.rope.no_lower"), "thin rope: no lower through a load, said why");
        helper.succeed();
    }

    /**
     * A load and a thing hung on a rope are never in one place (2026-11-10: a bottle hung under a Thin Rope let down to a
     * load sat inside it, hidden): nothing is hung into a load, said why; a load falling onto a rope's bottle knocks it
     * down, as a hit does. (A load let down into what hangs was already refused: TiedLoads#canMove.)
     */
    private static void hungApart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Component> said = new ArrayList<>();
        ServerPlayer player = player(helper, said);
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.THIN_ROPE.get()); // its end over the barrel's cell
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.BARREL);
        TiedLoadEntity barrel = TiedLoads.tie(level, helper.absolutePos(new BlockPos(2, 1, 2)), null);
        check(helper, barrel != null, "tied under the cord");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE, 2));
        use(helper, player, new BlockPos(2, 2, 2), Direction.NORTH);
        check(helper, level.getEntitiesOfClass(HungItemEntity.class, helper.getBounds()).isEmpty()
                && player.getMainHandItem().getCount() == 2 && saidKey(said, "message.nookworks.rope.no_room"),
                "no bottle hung into the load under the cord, said why");

        // a bottle on a rope strung across, a load let fall from above it
        ServerPlayer tier = RopeTests.player(helper, 10);
        BlockPos[] posts = RopeTests.posts(helper, 5, 4);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        RopeSpans.tie(level, tier, a, tier.getMainHandItem());
        RopeSpans.tie(level, tier, b, tier.getMainHandItem());
        level.getServer().getPlayerList().remove(tier);
        HungItemEntity bottle = HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(Items.GLASS_BOTTLE));
        check(helper, bottle.misfit(level) == null, "room for the bottle on the rope");
        level.addFreshEntity(bottle);
        BlockPos above = new BlockPos(2, 6, 5);
        helper.setBlock(above, Blocks.CHEST);
        TiedLoadEntity chest = TiedLoads.tie(level, helper.absolutePos(above), null);
        check(helper, chest != null && !chest.held(), "a load held by nothing over the bottle");
        helper.succeedWhen(() -> {
            check(helper, bottle.isRemoved() && chest.aligned() && chest.getY() < bottle.getY(),
                    "the falling load knocked the bottle down and came to rest below it: " + chest.getY());
            check(helper, !level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2), i -> i.getItem().is(Items.GLASS_BOTTLE)).isEmpty(),
                    "the bottle dropped, as by a hit");
        });
    }

    private static void sealed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(2, 2, 2);
        helper.setBlock(rel.below(), Blocks.HOPPER);
        chestWith(helper, rel, Blocks.CHEST.defaultBlockState(), 5);
        TiedLoadEntity load = TiedLoads.tie(level, helper.absolutePos(rel), null);
        check(helper, load != null, "tied over a hopper");
        List<Component> said = new ArrayList<>();
        ServerPlayer player = player(helper, said);
        load.interact(player, InteractionHand.MAIN_HAND, load.position());
        check(helper, player.containerMenu == player.inventoryMenu && saidKey(said, "message.nookworks.tied_load.tied"), "not opened, said why");
        helper.runAfterDelay(40, () -> {
            HopperBlockEntity hopper = helper.getBlockEntity(rel.below(), HopperBlockEntity.class);
            check(helper, hopper.isEmpty(), "the hopper under it took nothing");
            check(helper, !load.isRemoved() && load.getY() == helper.absolutePos(rel).getY(), "it rests on the hopper, tied");
            helper.succeed();
        });
    }

    private static void stoodOn(GameTestHelper helper) {
        BlockPos rel = new BlockPos(3, 1, 3);
        helper.setBlock(rel, Blocks.BARREL);
        TiedLoadEntity load = TiedLoads.tie(helper.getLevel(), helper.absolutePos(rel), null);
        check(helper, load != null, "tied");
        var pig = helper.spawn(net.minecraft.world.entity.EntityTypes.PIG, new Vec3(3.5, 4, 3.5));
        double top = helper.absolutePos(rel).getY() + 1;
        helper.succeedWhen(() -> check(helper, pig.onGround() && Math.abs(pig.getY() - top) < 1.0E-3,
                "the pig fell onto the load and stands on it: " + (pig.getY() - top)));
    }

    private static void killed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(2, 1, 2);
        chestWith(helper, rel, Blocks.CHEST.defaultBlockState(), 5);
        TiedLoadEntity load = TiedLoads.tie(level, helper.absolutePos(rel), null);
        check(helper, load != null, "tied");
        load.kill(level);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2));
        int diamonds = drops.stream().filter(i -> i.getItem().is(Items.DIAMOND)).mapToInt(i -> i.getItem().getCount()).sum();
        check(helper, load.isRemoved() && helper.getBlockState(rel).isAir() && diamonds == 5
                && drops.stream().anyMatch(i -> i.getItem().is(Items.CHEST)) && drops.stream().anyMatch(i -> i.getItem().is(Items.IRON_SWORD)),
                "killed: broken open where it stood, the chest and all it held dropped: " + drops);
        helper.succeed();
    }

    /**
     * Killed with its cell taken (a block pushed in by a piston) it cannot be set back, and is broken open as it stands
     * - each block's own way: a Storage Barrel spills what it holds (it was lost), a Crate leaves as one item holding
     * its things (they doubled, the Crate's item and a spill besides), a chest spills (r22 CXC-001, CXM-001).
     */
    private static void killedBlocked(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StorageBlockEntity barrel = StorageTests.placeStorage(helper, ModBlocks.IRON_STORAGE.get());
        barrel.inventory().set(0, ItemResource.of(Items.DIAMOND), 5);
        BlockPos crate = new BlockPos(1, 1, 5);
        helper.setBlock(crate, ModBlocks.CRATE.get());
        helper.getBlockEntity(crate, dev.nookworks.block.entity.CrateBlockEntity.class).setItem(0, new ItemStack(Items.EMERALD, 5));
        BlockPos chest = new BlockPos(5, 1, 1);
        chestWith(helper, chest, Blocks.CHEST.defaultBlockState(), 5); // 5 diamonds and a sword
        for (BlockPos at : new BlockPos[] {StorageTests.STORAGE_POS, crate, chest}) {
            TiedLoadEntity load = TiedLoads.tie(level, helper.absolutePos(at), null);
            check(helper, load != null, "tied: " + at);
            helper.setBlock(at, Blocks.STONE); // pushed in: no room to set it back
            load.kill(level);
            check(helper, load.isRemoved(), "killed: " + at);
        }
        // a barrel's spill is spread over ticks when many fall at once (StorageSpill): counted once it is all out
        helper.succeedWhen(() -> countBrokenOpen(helper, level));
    }

    private static void countBrokenOpen(GameTestHelper helper, ServerLevel level) {
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2));
        int diamonds = 0;
        int emeralds = 0;
        int barrels = 0;
        int crates = 0;
        for (ItemEntity entity : drops) {
            ItemStack stack = entity.getItem();
            diamonds += stack.is(Items.DIAMOND) ? stack.getCount() : 0;
            emeralds += stack.is(Items.EMERALD) ? stack.getCount() : 0;
            barrels += stack.is(ModBlocks.IRON_STORAGE.get().asItem()) ? stack.getCount() : 0;
            crates += stack.is(ModBlocks.CRATE.get().asItem()) ? stack.getCount() : 0;
            var held = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
            if (held != null) {
                for (ItemStack inside : held.nonEmptyItemCopyStream().toList()) {
                    emeralds += inside.is(Items.EMERALD) ? inside.getCount() : 0;
                    diamonds += inside.is(Items.DIAMOND) ? inside.getCount() : 0;
                }
            }
            var stored = stack.get(ModDataComponents.STORAGE.get());
            if (stored != null) {
                for (var entry : stored.entries()) {
                    diamonds += entry.item().is(Items.DIAMOND) ? entry.count() : 0;
                }
            }
        }
        check(helper, diamonds == 10, "the barrel's five diamonds and the chest's five, no more nor fewer: " + diamonds + " in " + drops);
        check(helper, emeralds == 5, "the Crate's five emeralds once: " + emeralds + " in " + drops);
        check(helper, barrels == 1 && crates == 1 && drops.stream().anyMatch(i -> i.getItem().is(Items.CHEST))
                && drops.stream().anyMatch(i -> i.getItem().is(Items.IRON_SWORD)), "each block dropped once, the sword too: " + drops);
    }

    /**
     * An add-on that cancels a Tied Load's joining the world (r22 CXC-004): tying leaves the block where it was, with all
     * it held; a load set down by {@link TiedLoads#spawn} and refused so is broken open where it would have stood.
     */
    private static void spawnRefused(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(2, 1, 2);
        chestWith(helper, at, Blocks.CHEST.defaultBlockState(), 5);
        CompoundTag before = saved(helper, at);
        Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> refuse = event -> {
            if (event.getEntity() instanceof TiedLoadEntity) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent.class, refuse);
        try {
            check(helper, TiedLoads.tie(level, helper.absolutePos(at), null) == null, "the tying refused");
            check(helper, helper.getBlockState(at).is(Blocks.CHEST) && saved(helper, at).equals(before),
                    "the chest where it was, with all it held: " + helper.getBlockState(at));
            check(helper, level.getEntitiesOfClass(TiedLoadEntity.class, helper.getBounds().inflate(2)).isEmpty(), "and no load");
            CarriedBlock taken = Carrying.take(level, helper.absolutePos(at), null).orElseThrow();
            TiedLoadEntity load = TiedLoads.spawn(level, net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(at)), taken);
            check(helper, load.carried() == null, "a load the world refused holds nothing");
        } finally {
            NeoForge.EVENT_BUS.unregister(refuse);
        }
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2));
        int diamonds = drops.stream().filter(i -> i.getItem().is(Items.DIAMOND)).mapToInt(i -> i.getItem().getCount()).sum();
        check(helper, diamonds == 5 && drops.stream().anyMatch(i -> i.getItem().is(Items.CHEST)),
                "what it held broken open where it would have stood: " + drops);
        helper.succeed();
    }

    private static void saved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(2, 1, 2);
        chestWith(helper, rel, Blocks.CHEST.defaultBlockState(), 8);
        CompoundTag data = saved(helper, rel);
        TiedLoadEntity load = TiedLoads.tie(level, helper.absolutePos(rel), null);
        check(helper, load != null, "tied");
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        load.saveWithoutId(out);
        TiedLoadEntity copy = new TiedLoadEntity(ModRope.TIED_LOAD.get(), level);
        copy.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), out.buildResult()));
        check(helper, load.carried() != null && load.carried().equals(copy.carried()) && copy.position().equals(load.position()),
                "loaded, it holds the same where it was");
        check(helper, copy.look().equals(load.look()), "and looks the same");
        load.discard();
        level.addFreshEntity(copy);
        check(helper, TiedLoads.untie(copy, null).placed() && saved(helper, rel).equals(data), "untied: the chest as it was saved");
        helper.succeed();
    }
}
