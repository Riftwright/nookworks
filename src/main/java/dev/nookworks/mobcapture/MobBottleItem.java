package dev.nookworks.mobcapture;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.nookworks.Nookworks;
import dev.nookworks.barter.PiglinShelter;
import dev.nookworks.feature.Permission;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Mob Bottle. Empty: right-click a mob that is down to half health or less to capture it (bosses, players and
 * mobs without a spawn egg cannot be caught). Filled ({@code nookworks:captured_entity}, and the whole saved mob in
 * {@code nookworks:captured_data}): right-click a block to let the same mob out again - its name, trades, age, what it
 * carries and who it belongs to all come with it - or brew it into a spawn egg in the Spawn Egg Brewer.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public class MobBottleItem extends Item {
    public MobBottleItem(Properties properties) {
        super(properties);
    }

    public static @Nullable EntityType<?> captured(ItemStack stack) {
        return stack.get(ModDataComponents.CAPTURED_ENTITY.get());
    }

    public static ItemStack filled(EntityType<?> type) {
        ItemStack stack = new ItemStack(ModItems.MOB_BOTTLE.get());
        stack.set(ModDataComponents.CAPTURED_ENTITY.get(), type);
        stack.set(DataComponents.MAX_STACK_SIZE, 1); // one mob per bottle; releasing must not empty a whole stack
        return stack;
    }

    /** A bottle holding {@code mob} itself, saved the way the game saves it to disk. */
    public static ItemStack filled(Entity mob) {
        ItemStack stack = filled(mob.getType());
        stack.set(ModDataComponents.CAPTURED_DATA.get(), CustomData.of(save(mob)));
        return stack;
    }

    /** LivingEntity#dropEquipment: what a mob carries that falls when it dies, a donkey's chest and load, an allay's item. */
    private static final java.lang.reflect.Method DROP_EQUIPMENT;

    static {
        try {
            DROP_EQUIPMENT = LivingEntity.class.getDeclaredMethod("dropEquipment", ServerLevel.class);
            DROP_EQUIPMENT.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * What the mob in {@code bottle} carried that would have fallen had it died, set down at {@code at}: gear it picked
     * up or was given, a saddle, a horse's armour, a donkey's chest and its load, an allay's item. The Spawn Egg Brewer
     * keeps only the mob's kind, and threw these away with it (the dupe and loss audit, 2026-10-19). Not its loot, and
     * not the gear it was born with, which a death gives only now and then: nothing comes out that a death would not.
     */
    public static void spillCarried(ServerLevel level, ItemStack bottle, Vec3 at) {
        EntityType<?> type = captured(bottle);
        CompoundTag data = capturedData(bottle);
        if (type == null || data == null || type.onlyOpCanSetNbt()) {
            return;
        }
        Entity entity;
        try (var reporter = new ProblemReporter.ScopedCollector(() -> "mob_bottle", LOGGER)) {
            entity = EntityType.create(type, TagValueInput.create(reporter, level.registryAccess(), data),
                    level, EntitySpawnReason.BUCKET).orElse(null);
        }
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        living.snapTo(at.x, at.y, at.z, 0.0F, 0.0F); // never added to the level: only where its things fall
        if (living instanceof Mob mob) {
            mob.dropPreservedEquipment(level);
        }
        if (living instanceof net.minecraft.world.entity.monster.piglin.Piglin piglin) {
            // a piglin lets its pocket fall in its death loot, not with its equipment: the gold it was bartering with
            piglin.getInventory().removeAllItems().forEach(stack -> piglin.spawnAtLocation(level, stack));
        }
        if (living instanceof Enderman enderman && enderman.getCarriedBlock() != null) {
            // the block an enderman took, as its death lets it fall (r21: brewed into an egg, it was gone)
            Item block = enderman.getCarriedBlock().getBlock().asItem();
            if (block != Items.AIR) {
                enderman.spawnAtLocation(level, new ItemStack(block));
            }
            enderman.setCarriedBlock(null);
        }
        try {
            DROP_EQUIPMENT.invoke(living, level);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Could not give back what a bottled {} carried", type, e);
        }
    }

    /** The saved mob in a filled bottle, or null when it holds only a kind (an old bottle, one shown in JEI). */
    public static @Nullable CompoundTag capturedData(ItemStack stack) {
        var data = stack.get(ModDataComponents.CAPTURED_DATA.get());
        return data == null ? null : data.copyTag();
    }

    private static CompoundTag save(Entity mob) {
        try (var reporter = new ProblemReporter.ScopedCollector(mob.problemPath(), LOGGER)) {
            var output = TagValueOutput.createWithContext(reporter, mob.registryAccess());
            mob.saveWithoutId(output);
            CompoundTag tag = output.buildResult();
            // Where it stood and how it was moving mean nothing once it is let out somewhere else; its lead stays behind,
            // and so do its riders, which the game saves with the mob it sits on - kept, one let out of the bottle
            // would be a second of each rider left standing in the world.
            // (and a bed slept in: the sleeper is woken on the way in, but nothing of the bed is kept)
            for (String key : new String[] {"Pos", "Motion", "fall_distance", "OnGround", "PortalCooldown", "leash", "Passengers",
                    "sleeping_pos"}) {
                tag.remove(key);
            }
            if (mob instanceof AbstractPiglin) {
                PiglinShelter.forgetIn(tag); // a Barter Terminal's keeping stays in its room
            }
            return tag;
        }
    }

    /** The spawn egg for a mob, if the game has one. */
    public static Optional<Holder<Item>> spawnEgg(EntityType<?> type) {
        return SpawnEggItem.byId(type);
    }

    /**
     * Runs before the mob's own right-click action, so villagers and the like can be caught too; last among the
     * handlers of the click, so a claim mod keeping others' animals safe has its say before the bottle does, and an
     * add-on's stronger bottle (asked to hear it at LOW) before ours (r21: LOW shared the band with it).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        if (stack.is(ModItems.MOB_BOTTLE.get()) && captured(stack) == null && event.getTarget() instanceof Mob) {
            event.setCancellationResult(tryCapture(event.getEntity(), event.getHand(), event.getTarget()));
            event.setCanceled(true);
        }
    }

    /** Bosses in all but name (elder guardians, wardens) as well as the tagged ones. Datapacks may add more. */
    public static final TagKey<EntityType<?>> NOT_BOTTLEABLE =
            TagKey.create(Registries.ENTITY_TYPE, Nookworks.id("not_bottleable"));

    public enum Refusal {
        NONE, NOT_CAPTURABLE, TOO_HEALTHY, OWNED, TOO_LADEN
    }

    /**
     * The most a saved mob may weigh. A mob is a few kilobytes, a mule with a full pack of ordinary things a few more;
     * one packed with filled shulker boxes would make an item too large to send, and is left out of the bottle. Each
     * bottle is sent whole to its holder and whoever sees it held, so a hotbar of them is kept to a few megabytes at
     * most.
     */
    public static final long MAX_BYTES = 64 * 1024;

    /** Whether {@code player} may bottle {@code target}. */
    public static Refusal check(Entity target, Player player) {
        Refusal refusal = check(target);
        if (refusal == Refusal.OWNED && target instanceof OwnableEntity pet
                && pet.getOwnerReference() != null && pet.getOwnerReference().getUUID().equals(player.getUUID())) {
            refusal = target instanceof Mob mob && mob.getHealth() > mob.getMaxHealth() / 2.0F ? Refusal.TOO_HEALTHY : Refusal.NONE;
        }
        return refusal;
    }

    /** Whether a mob of this kind can go in a bottle at all: not a boss, not refused by tag, and with a spawn egg. */
    public static boolean bottleable(EntityType<?> type) {
        var holder = type.builtInRegistryHolder();
        return !holder.is(Tags.EntityTypes.BOSSES) && !holder.is(NOT_BOTTLEABLE) && !holder.is(Tags.EntityTypes.CAPTURING_NOT_SUPPORTED)
                && spawnEgg(type).isPresent();
    }

    /**
     * Whether {@code player} may bottle {@code target}, at any health if {@code anyHealth} (an add-on's stronger bottle,
     * nookTechnology's Reinforced Mob Bottle): every other refusal stands.
     */
    public static Refusal check(Entity target, Player player, boolean anyHealth) {
        Refusal refusal = check(target, player);
        return anyHealth && refusal == Refusal.TOO_HEALTHY ? Refusal.NONE : refusal;
    }

    public static Refusal check(Entity target) {
        if (!(target instanceof Mob mob) || !mob.isAlive() || !bottleable(target.getType())) {
            return Refusal.NOT_CAPTURABLE;
        }
        // A pet goes in the bottle only by its owner's hand (check(Entity, Player)): anyone else could walk off with it.
        // One owned by a mob - an evoker's vex - is no pet: it does not go in at all.
        if (mob instanceof Vex) {
            return Refusal.NOT_CAPTURABLE; // an evoker's, whether or not the evoker is about any more
        }
        if (mob instanceof OwnableEntity pet && pet.getOwnerReference() != null) {
            return pet.getOwner() instanceof Mob ? Refusal.NOT_CAPTURABLE : Refusal.OWNED;
        }
        return mob.getHealth() <= mob.getMaxHealth() / 2.0F ? Refusal.NONE : Refusal.TOO_HEALTHY;
    }

    /** Add-ons' word on which bottles take a mob at any health ({@link #anyHealthWhen}). */
    private static final List<Predicate<ItemStack>> ANY_HEALTH = new CopyOnWriteArrayList<>();

    /**
     * For add-ons: bottles {@code test} accepts take a mob at any health wherever a bottle catches by our rules - a
     * click on a mob, the Trading Counter reaching over to its villager (r21, 2026-11-06: nookTechnology's Reinforced Mob
     * Bottle took a villager by a click on it, but at the counter only at half health).
     */
    public static void anyHealthWhen(Predicate<ItemStack> test) {
        ANY_HEALTH.add(test);
    }

    /** Whether an add-on says {@code bottle} takes a mob at any health. */
    public static boolean takesAnyHealth(ItemStack bottle) {
        for (Predicate<ItemStack> test : ANY_HEALTH) {
            if (test.test(bottle)) {
                return true;
            }
        }
        return false;
    }

    /** Bottles {@code target} by the bottle's rules, at any health when an add-on says so of the bottle. */
    public static InteractionResult tryCapture(Player player, InteractionHand hand, Entity target) {
        return tryCapture(player, hand, target, takesAnyHealth(player.getItemInHand(hand)));
    }

    /**
     * Bottles {@code target} with the empty bottle in {@code player}'s {@code hand}, as a click does; {@code anyHealth}
     * takes it however healthy it is (for add-ons, whose own handler runs before ours). The filled bottle keeps the
     * empty one's components (an add-on's mark, a name), the mob added to them.
     */
    public static InteractionResult tryCapture(Player player, InteractionHand hand, Entity target, boolean anyHealth) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        Refusal refusal = check(target, player, anyHealth);
        ItemStack filled = null;
        if (refusal == Refusal.NONE) {
            // Weighed before anything is done to it (the save leaves out riders, mount and lead): a mob refused as too
            // laden keeps its rider, its seat and its lead.
            filled = filled(target);
            if (capturedData(filled).sizeInBytes() > MAX_BYTES) {
                refusal = Refusal.TOO_LADEN;
            } else {
                // Riders and mounts stay behind; a lead falls to the ground rather than tying the bottle to a fence.
                target.ejectPassengers();
                target.stopRiding();
                if (target instanceof Leashable leashed && leashed.isLeashed()) {
                    leashed.dropLeash();
                }
                if (target instanceof LivingEntity sleeper && sleeper.isSleeping()) {
                    sleeper.stopSleeping(); // its bed comes free, as it does when one dies in it
                    filled = filled(target);
                }
                if (target instanceof AbstractVillager trader && trader.isTrading()) {
                    // A player's trade screen lowers its prices by their discount (a cured villager's, the Hero of the
                    // Village's) while it is open: the trade is ended first, or the bottle kept that discount for whoever
                    // let the villager out - on top of their own.
                    trader.setTradingPlayer(null);
                    // 26.3 takes the discount off when the next trade begins, no longer when one ends: off here, as before
                    trader.getOffers().forEach(MerchantOffer::resetSpecialPriceDiff);
                    filled = filled(target);
                }
                if (target instanceof Villager villager) {
                    leaveItsPlaces(villager);
                    filled = filled(target); // bottled without the places it gave up
                }
            }
        }
        if (refusal != Refusal.NONE) {
            player.sendOverlayMessage(Component.translatable(switch (refusal) {
                case TOO_HEALTHY -> "message.nookworks.mob_bottle.too_healthy";
                case OWNED -> "message.nookworks.mob_bottle.owned";
                case TOO_LADEN -> "message.nookworks.mob_bottle.too_laden";
                default -> "message.nookworks.mob_bottle.not_capturable";
            }).withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.POOF, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, target.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0F, 0.8F);
        if (player instanceof ServerPlayer server) {
            // the advancement for bottling a mob is the mod's to award: a bottle clicked on a boat or a frame is no catch
            AdvancementHolder bottled = server.level().getServer().getAdvancements().get(Nookworks.id("mob_bottle"));
            if (bottled != null) { // not loaded with the Mob Bottle switched off
                server.getAdvancements().award(bottled, "bottled");
            }
        }
        target.discard(); // gone from the world, whole in the bottle: its gear and pack went in with it
        ItemStack held = player.getItemInHand(hand);
        ItemStack kept = held.copyWithCount(1);
        kept.applyComponents(filled.getComponentsPatch());
        filled = kept;
        if (!player.hasInfiniteMaterials()) {
            held.shrink(1);
        }
        if (held.isEmpty()) {
            player.setItemInHand(hand, filled);
        } else if (!player.getInventory().add(filled)) {
            player.drop(filled, false, Prediction.PREDICTED);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * A villager going into a bottle gives up its bed, its workstation and its meeting bell, as one that dies does -
     * or they stay held for good by a villager no longer in the world: a breeder's beds never come free, a trading
     * hall's lectern is never taken again. It forgets them too, and looks for new ones where it is let out; one that
     * has never traded may then take up another trade, as a villager carried far from its workstation does.
     */
    private static void leaveItsPlaces(Villager villager) {
        for (var memory : Villager.POI_MEMORIES.keySet()) {
            villager.releasePoi(memory);
            villager.getBrain().eraseMemory(memory);
        }
    }

    /** A filled bottle lets its mob out onto the clicked face. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        EntityType<?> type = captured(stack);
        if (type == null) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        CompoundTag data = capturedData(stack);
        // A creative player can hand the server any item, components and all: a bottle made up rather than filled could
        // hold a command block minecart or a boss. What could not go in does not come out, and data only an operator
        // may set (as on a spawn egg) comes out only for an operator.
        Player user = context.getPlayer();
        if (!bottleable(type) || data != null && data.sizeInBytes() > MAX_BYTES) {
            if (user != null) {
                user.sendOverlayMessage(Component.translatable("message.nookworks.mob_bottle.not_capturable").withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        if (data != null && type.onlyOpCanSetNbt() && (user == null || !user.canUseGameMasterBlocks())) {
            data = null;
        }
        if (vanishesHere(level, type)) {
            // on Peaceful a monster is gone the tick it is out, and with it all it carried: it stays in the bottle
            if (user != null) {
                user.sendOverlayMessage(Component.translatable("message.nookworks.mob_bottle.peaceful").withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        // Let out onto the block beside the one clicked: that place is asked, as putting a block there would be - from
        // outside a claim, a face of its edge would otherwise let a hostile mob out inside it.
        if (user != null && !Permission.mayPlace(level, pos, context.getClickedFace(), user)) {
            return InteractionResult.FAIL;
        }
        Entity released = data == null ? type.spawn(level, null, context.getPlayer(), pos, EntitySpawnReason.BUCKET, true, false)
                : release(level, type, data, Vec3.atBottomCenterOf(pos), context.getPlayer(),
                        context.getPlayer() != null && context.getPlayer().hasInfiniteMaterials());
        if (released == null) {
            return InteractionResult.FAIL;
        }
        level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1.0F, 0.8F);
        Player player = context.getPlayer();
        if (player == null || !player.hasInfiniteMaterials()) {
            stack.remove(ModDataComponents.CAPTURED_ENTITY.get());
            stack.remove(ModDataComponents.CAPTURED_DATA.get());
            stack.set(DataComponents.MAX_STACK_SIZE, stack.getPrototype().getOrDefault(DataComponents.MAX_STACK_SIZE, 1));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Lets the mob in {@code bottle} out standing on {@code pos}, as a click does but with nobody clicking: a Barter
     * Terminal letting a piglin into its room. The same rules as a click - what could not have gone in does not come
     * out, and data only an operator may set does not - and the bottle is left as it is. Null if nothing came out.
     */
    public static @Nullable Entity letOut(ServerLevel level, ItemStack bottle, BlockPos pos) {
        return letOut(level, bottle, pos, false);
    }

    /**
     * As {@link #letOut(ServerLevel, ItemStack, BlockPos)}; {@code again}: the bottle will not be used up (a creative
     * player's, clicked on a Trading Counter), so its mob comes out as a new one each time, as from a click.
     */
    public static @Nullable Entity letOut(ServerLevel level, ItemStack bottle, BlockPos pos, boolean again) {
        return letOut(level, bottle, Vec3.atBottomCenterOf(pos), again);
    }

    /**
     * As {@link #letOut(ServerLevel, ItemStack, BlockPos, boolean)}, standing at {@code at} itself: on whatever floor is
     * in the block - a Trading Counter's villager on a slab or a carpet, not stood inside it.
     */
    public static @Nullable Entity letOut(ServerLevel level, ItemStack bottle, Vec3 at, boolean again) {
        EntityType<?> type = captured(bottle);
        CompoundTag data = capturedData(bottle);
        if (type == null || !bottleable(type) || data != null && data.sizeInBytes() > MAX_BYTES || vanishesHere(level, type)) {
            return null;
        }
        if (data != null && type.onlyOpCanSetNbt()) {
            data = null;
        }
        return data == null ? type.spawn(level, null, null, BlockPos.containing(at), EntitySpawnReason.BUCKET, true, false)
                : release(level, type, data, at, null, again);
    }

    /** Whether a {@code type} let out here would be gone at once: a monster, on Peaceful. */
    private static boolean vanishesHere(ServerLevel level, EntityType<?> type) {
        return level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL && !type.isAllowedInPeaceful();
    }

    /** One of {@code bottle} with its mob let out: an empty bottle, which stacks again. */
    public static ItemStack emptied(ItemStack bottle) {
        ItemStack empty = bottle.copyWithCount(1);
        empty.remove(ModDataComponents.CAPTURED_ENTITY.get());
        empty.remove(ModDataComponents.CAPTURED_DATA.get());
        empty.set(DataComponents.MAX_STACK_SIZE, empty.getPrototype().getOrDefault(DataComponents.MAX_STACK_SIZE, 1));
        return empty;
    }

    /**
     * The saved mob, let out standing at {@code at} and facing whoever let it out. {@code again}: the bottle is not used
     * up (in creative), so the same mob may come out more than once - each time as a new one.
     */
    private static @Nullable Entity release(ServerLevel level, EntityType<?> type, CompoundTag data, Vec3 at,
            @Nullable Player player, boolean again) {
        Entity mob;
        try (var reporter = new ProblemReporter.ScopedCollector(() -> "mob_bottle", LOGGER)) {
            mob = EntityType.create(type, TagValueInput.create(reporter, level.registryAccess(), data),
                    level, EntitySpawnReason.BUCKET).orElse(null);
        }
        if (mob == null) {
            return null;
        }
        float yaw = player == null ? mob.getYRot() : Mth.wrapDegrees(player.getYRot() + 180.0F);
        mob.snapTo(at.x, at.y, at.z, yaw, 0.0F);
        mob.setDeltaMovement(Vec3.ZERO);
        if (mob instanceof LivingEntity living) {
            living.setYHeadRot(yaw);
            living.setYBodyRot(yaw);
        }
        // The same mob keeps its id, unless something with that id is about already anywhere - in another dimension, a
        // player online (a creative player's bottle is not used up, so its mob can come out twice, here and in the
        // Nether): the world will not hold two entities with one id, and would drop one of them later.
        // (a check of the loaded world only: from a creative bottle it is always a new id, the first copy perhaps unloaded)
        if (again || inUse(level.getServer(), mob.getUUID())) {
            mob.setUUID(UUID.randomUUID());
        }
        return level.addFreshEntity(mob) ? mob : null;
    }

    private static boolean inUse(MinecraftServer server, UUID id) {
        if (server.getPlayerList().getPlayer(id) != null) {
            return true;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Component getName(ItemStack stack) {
        EntityType<?> type = captured(stack);
        return type == null ? super.getName(stack) : Component.translatable("item.nookworks.mob_bottle.filled", type.getDescription());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
            TooltipFlag flag) {
        // an add-on's stronger bottle says it takes a mob at any health (r21: it said both, half health and any)
        builder.accept(Component.translatable(captured(stack) != null ? "tooltip.nookworks.mob_bottle.filled"
                : takesAnyHealth(stack) ? "tooltip.nookworks.mob_bottle.empty.any_health" : "tooltip.nookworks.mob_bottle.empty")
                .withStyle(ChatFormatting.GRAY));
        CompoundTag data = capturedData(stack);
        if (data == null) {
            return;
        }
        // Who is in it: the name it was given, how it is, and for a villager what it does.
        var registries = context.registries();
        if (registries != null && data.contains("CustomName")) {
            ComponentSerialization.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),
                    data.get("CustomName")).result().ifPresent(name -> builder.accept(Component.translatable("nookworks.quoted", name)
                            .withStyle(ChatFormatting.WHITE)));
        }
        data.getCompound("VillagerData").ifPresent(villager -> {
            String profession = villager.getStringOr("profession", "");
            int level = villager.getIntOr("level", 1);
            if (!profession.isEmpty() && !profession.endsWith(":none")) {
                Identifier id = Identifier.tryParse(profession);
                if (id != null) {
                    builder.accept(Component.translatable("tooltip.nookworks.mob_bottle.villager",
                            Component.translatable("entity." + id.getNamespace() + ".villager." + id.getPath()),
                            Component.translatable("merchant.level." + level)).withStyle(ChatFormatting.GRAY));
                }
            }
        });
        if (data.getIntOr("Age", 0) < 0) {
            builder.accept(Component.translatable("tooltip.nookworks.mob_bottle.baby").withStyle(ChatFormatting.GRAY));
        }
        data.getFloat("Health").ifPresent(health -> builder.accept(Component.translatable("tooltip.nookworks.mob_bottle.health",
                String.format(Locale.ROOT, "%.1f", health)).withStyle(ChatFormatting.GRAY)));
    }

    private static final Logger LOGGER = LogUtils.getLogger();
}
