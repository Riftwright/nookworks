package dev.nookworks.machine;


import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

import dev.nookworks.experience.Move;
import dev.nookworks.fluid.Bottles;
import dev.nookworks.fluid.CopperTankBlock;
import dev.nookworks.mercury.MercuryBucketItem;
import dev.nookworks.mercury.MercuryStore;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModItems;
import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.fluid.OneTank;
import dev.nookworks.fluid.Tanks;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import dev.nookworks.port.transfer.Capabilities;
import dev.nookworks.port.transfer.ResourceHandler;
import dev.nookworks.port.transfer.ResourceHandlerUtil;
import dev.nookworks.port.transfer.ItemAccess;
import dev.nookworks.port.transfer.FluidResource;
import dev.nookworks.port.transfer.FluidUtil;
import dev.nookworks.port.transfer.ItemResource;
import dev.nookworks.port.transfer.ItemStacksResourceHandler;
import dev.nookworks.port.transfer.Transaction;
import dev.nookworks.port.transfer.TransactionContext;
/**
 * Buckets and bottles poured into a machine's tanks and filled from them by hand. Since 1.5 a machine screen's Liquid
 * tab does it (dev.nookworks.menu.tab.LiquidTab, the user's design of 2026-11-08: a slot for an empty container, one
 * for a filled one, each a second's work) through {@link #pourItem} and {@link #drawItem}; the click on a gauge with a
 * bucket on the cursor that 1.3 brought in (the user, 2026-10-26) is gone with it, the screen having one way to do it.
 * Any tank goes, the ones a pipe may not reach too (a machine's own {@link OneTank} worked by hand), but each takes only
 * what it takes. Bottles of water, oil, biodiesel, methanol, wood vinegar and liquid experience go by
 * dev.nookworks.fluid.Bottles (where an add-on registers its own), 250 mB; buckets and any other container by its fluid
 * capability; a bucket of mercury, which has none, by the machine's mercury store.
 * <p>
 * Public for other mods (nookTechnology, its own screens): {@link #pourItem}, {@link #drawItem}, {@link #pourMercury},
 * {@link #drawMercury}, and {@link #pour(Player, AbstractContainerMenu, ResourceHandler, GuiTank.Kind)} for what a
 * cursor holds; listed in docs/compat.md.
 */
public final class GaugePouring {
    /** A bucket's and a bottle's worth. */
    public static final int BUCKET = 1000;
    public static final int BOTTLE = OilFluids.BOTTLE;

    private GaugePouring() {}

    /**
     * The container {@code filled} becomes when its liquid is poured into {@code tank} - a glass bottle, an empty bucket
     * - or empty when it cannot be: not a filled container, a liquid {@code takes} turns down, or no room for all of it.
     * One container: the count of {@code filled} is not looked at. With {@code simulate} nothing changes.
     */
    public static ItemStack pourItem(ItemStack filled, ResourceHandler<FluidResource> tank, Predicate<Fluid> takes, boolean simulate) {
        return byHand(tank, simulate, (handler, transaction) -> pourInto(filled, handler, takes, transaction));
    }

    /**
     * The container {@code empty} (a glass bottle, an empty bucket) becomes when filled from {@code tank} with the first
     * liquid in it that {@code gives} allows and the container holds - or empty when it cannot be. With {@code simulate}
     * nothing changes.
     */
    public static ItemStack drawItem(ItemStack empty, ResourceHandler<FluidResource> tank, Predicate<Fluid> gives, boolean simulate) {
        return byHand(tank, simulate, (handler, transaction) -> drawFrom(empty, handler, gives, transaction));
    }

    /** An empty bucket, after a Mercury Bucket is poured into {@code store}; empty when it is not one or there is no room. */
    public static ItemStack pourMercury(ItemStack filled, MercuryStore store, boolean simulate) {
        if (!(filled.getItem() instanceof MercuryBucketItem) || store.fill(BUCKET, true) < BUCKET) {
            return ItemStack.EMPTY;
        }
        if (!simulate) {
            store.fill(BUCKET, false);
        }
        return new ItemStack(Items.BUCKET);
    }

    /** A Mercury Bucket, filled from {@code store} by an empty bucket; empty when it is not one or there is under a bucket. */
    public static ItemStack drawMercury(ItemStack empty, MercuryStore store, boolean simulate) {
        if (!empty.is(Items.BUCKET) || store.drain(BUCKET, true) < BUCKET) {
            return ItemStack.EMPTY;
        }
        if (!simulate) {
            store.drain(BUCKET, false);
        }
        return new ItemStack(ModItems.MERCURY_BUCKET.get());
    }

    private interface Work {
        ItemStack run(ResourceHandler<FluidResource> tank, TransactionContext transaction);
    }

    /**
     * {@code work} in a transaction, committed unless {@code simulate}; a machine's own tank by hand, past the rules that
     * keep pipes one way (a water tank no pipe may draw from), on a copy whose contents go back into the tank.
     */
    private static ItemStack byHand(ResourceHandler<FluidResource> tank, boolean simulate, Work work) {
        OneTank one = tank instanceof OneTank own ? own : null;
        ResourceHandler<FluidResource> handler = one == null ? tank : Tanks.byHand(one);
        try (Transaction transaction = Transaction.openRoot()) {
            ItemStack out = work.run(handler, transaction);
            if (out.isEmpty() || simulate) {
                return out;
            }
            transaction.commit();
            if (one != null) {
                OneTank copy = (OneTank) handler;
                Tanks.set(one, copy.getResource(0).getFluid(), copy.getAmountAsInt(0));
            }
            return out;
        }
    }

    private static ItemStack pourInto(ItemStack filled, ResourceHandler<FluidResource> tank, Predicate<Fluid> takes,
            TransactionContext transaction) {
        if (filled.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Fluid bottled = Bottles.fluidOf(filled);
        if (bottled != null) {
            return takes.test(bottled) && tank.insert(FluidResource.of(bottled), BOTTLE, transaction) == BOTTLE
                    ? new ItemStack(Items.GLASS_BOTTLE) : ItemStack.EMPTY;
        }
        ItemStacksResourceHandler box = box(filled);
        var held = ItemAccess.forHandlerIndexStrict(box, 0).getCapability(Capabilities.Fluid.ITEM);
        if (held == null) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < held.size(); i++) {
            FluidResource resource = held.getResource(i);
            int amount = held.getAmountAsInt(i);
            if (resource.isEmpty() || amount <= 0 || !takes.test(resource.getFluid())) {
                continue;
            }
            try (Transaction inner = Transaction.open(transaction)) {
                int out = held.extract(resource, amount, inner);
                if (out <= 0 || tank.insert(resource, out, inner) != out) {
                    continue;
                }
                inner.commit();
            }
            return contents(box);
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack drawFrom(ItemStack empty, ResourceHandler<FluidResource> tank, Predicate<Fluid> gives,
            TransactionContext transaction) {
        if (empty.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (empty.is(Items.GLASS_BOTTLE)) {
            for (int i = 0; i < tank.size(); i++) {
                FluidResource resource = tank.getResource(i);
                if (resource.isEmpty() || tank.getAmountAsInt(i) < BOTTLE || !gives.test(resource.getFluid())) {
                    continue;
                }
                ItemStack bottle = Bottles.filled(resource.getFluid());
                if (!bottle.isEmpty() && tank.extract(resource, BOTTLE, transaction) == BOTTLE) {
                    return bottle;
                }
            }
            return ItemStack.EMPTY;
        }
        ItemStacksResourceHandler box = box(empty);
        var held = ItemAccess.forHandlerIndexStrict(box, 0).getCapability(Capabilities.Fluid.ITEM);
        if (held == null) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < tank.size(); i++) {
            FluidResource resource = tank.getResource(i);
            int amount = tank.getAmountAsInt(i);
            if (resource.isEmpty() || amount <= 0 || !gives.test(resource.getFluid())) {
                continue;
            }
            int wanted;
            try (Transaction probe = Transaction.open(transaction)) {
                wanted = held.insert(resource, amount, probe); // what the container would take of it; rolled back
            }
            if (wanted <= 0) {
                continue;
            }
            try (Transaction inner = Transaction.open(transaction)) {
                if (tank.extract(resource, wanted, inner) != wanted || held.insert(resource, wanted, inner) != wanted) {
                    continue;
                }
                inner.commit();
            }
            return contents(box);
        }
        return ItemStack.EMPTY;
    }

    /** One of {@code stack} in a handler of its own, for its fluid capability to swap it for what it becomes. */
    private static ItemStacksResourceHandler box(ItemStack stack) {
        ItemStacksResourceHandler box = new ItemStacksResourceHandler(1);
        box.set(0, ItemResource.of(stack), 1);
        return box;
    }

    private static ItemStack contents(ItemStacksResourceHandler box) {
        return box.getResource(0).toStack(box.getAmountAsInt(0));
    }

    /**
     * What {@code player}'s cursor holds, poured into {@code tank} if it is full of what a gauge of {@code kind} holds, or
     * filled from it if it is empty; one container of a stack at a time, the rest kept on the cursor. True if anything moved.
     */
    public static boolean pour(Player player, AbstractContainerMenu menu, ResourceHandler<FluidResource> tank, GuiTank.Kind kind) {
        if (player.isSpectator()) {
            return false;
        }
        if (!(tank instanceof OneTank one)) {
            return pourInto(player, menu, tank, kind);
        }
        // a machine's own tank: by hand past the rules that keep pipes one way (a water tank no pipe may draw from),
        // on a copy whose contents go back into the tank if anything moved
        OneTank copy = Tanks.byHand(one);
        if (!pourInto(player, menu, copy, kind)) {
            return false;
        }
        Tanks.set(one, copy.getResource(0).getFluid(), copy.getAmountAsInt(0));
        return true;
    }

    private static boolean pourInto(Player player, AbstractContainerMenu menu, ResourceHandler<FluidResource> tank, GuiTank.Kind kind) {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty() || kind == GuiTank.Kind.NETHER_AIR) {
            return false;
        }
        Fluid bottled = CopperTankBlock.bottleOf(carried);
        if (bottled != null) {
            if (!holds(kind, bottled) || !Move.insertExactly(tank, FluidResource.of(bottled), BOTTLE)) {
                return false;
            }
            swapOne(player, menu, new ItemStack(Items.GLASS_BOTTLE));
            player.level().playSound(null, player.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.8F, 1.0F);
            return true;
        }
        if (carried.is(Items.GLASS_BOTTLE)) {
            Fluid fluid = drawn(kind, tank);
            ItemStack filled = fluid == null ? ItemStack.EMPTY : CopperTankBlock.filledBottle(fluid);
            if (filled.isEmpty() || !Move.extractExactly(tank, FluidResource.of(fluid), BOTTLE)) {
                return false;
            }
            swapOne(player, menu, filled);
            player.level().playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.8F, 1.0F);
            return true;
        }
        // in creative the bucket stays, as a right-click with it keeps it; what it would become goes into the inventory
        ItemAccess access = player.hasInfiniteMaterials() ? ItemAccess.forInfiniteMaterials(player, carried)
                : ItemAccess.forPlayerCursor(player, menu);
        var held = access.oneByOne().getCapability(Capabilities.Fluid.ITEM);
        if (held == null) {
            return false;
        }
        var moved = ResourceHandlerUtil.moveFirst(held, tank, resource -> holds(kind, resource.getFluid()), Integer.MAX_VALUE, null);
        boolean pickup = false;
        if (moved == null) {
            moved = ResourceHandlerUtil.moveFirst(tank, held, resource -> holds(kind, resource.getFluid()), Integer.MAX_VALUE, null);
            pickup = true;
        }
        if (moved == null) {
            return false;
        }
        FluidUtil.triggerSoundAndGameEvent(moved.resource(), player.level(), player.position(), player, pickup);
        return true;
    }

    /** Whether a gauge of {@code kind} holds {@code fluid}: what may go in by it, and come out. */
    public static boolean holds(GuiTank.Kind kind, Fluid fluid) {
        return switch (kind) {
            case WATER -> fluid.isSame(Fluids.WATER);
            case VEGETABLE_OIL -> fluid.isSame(OilFluids.SOURCE.get());
            case BIODIESEL -> fluid.isSame(OilFluids.BIODIESEL.get());
            case METHANOL -> fluid.isSame(OilFluids.METHANOL.get());
            case WOOD_VINEGAR -> fluid.isSame(OilFluids.WOOD_VINEGAR.get());
            case LIQUID_FUEL -> fluid.isSame(OilFluids.SOURCE.get()) || fluid.isSame(OilFluids.BIODIESEL.get());
            case EXPERIENCE -> fluid.is(ExperienceFluids.EXPERIENCE);
            case MERCURY, NETHER_AIR -> false;
        };
    }

    /**
     * The gauge for {@code fluid}: the kind that holds just it - water, oil, biodiesel, methanol, wood vinegar, liquid
     * experience - or {@code otherwise} for none (nothing yet, or a liquid of no gauge's). For a tank that holds what a
     * recipe makes, which a data pack may change: a Retort giving off methanol showed wood vinegar and gave none to a
     * bottle (r22 CXC-006, CXP-004).
     */
    public static GuiTank.Kind kindOf(Fluid fluid, GuiTank.Kind otherwise) {
        for (GuiTank.Kind kind : GuiTank.Kind.values()) {
            if (kind != GuiTank.Kind.LIQUID_FUEL && holds(kind, fluid)) {
                return kind;
            }
        }
        return otherwise;
    }

    /** What a bottle would draw out of {@code tank} by a gauge of {@code kind}: the first fluid in it the gauge holds. */
    private static @Nullable Fluid drawn(GuiTank.Kind kind, ResourceHandler<FluidResource> tank) {
        for (int i = 0; i < tank.size(); i++) {
            Fluid fluid = tank.getResource(i).getFluid();
            if (tank.getAmountAsLong(i) > 0 && holds(kind, fluid)) {
                return fluid;
            }
        }
        return null;
    }

    /** One of the cursor's stack becomes {@code result}: on the cursor if it was the last, else into the inventory (or dropped). */
    private static void swapOne(Player player, AbstractContainerMenu menu, ItemStack result) {
        ItemStack carried = menu.getCarried();
        if (player.hasInfiniteMaterials()) {
            if (!player.getInventory().contains(result)) {
                player.getInventory().add(result);
            }
            return;
        }
        carried.shrink(1);
        if (carried.isEmpty()) {
            menu.setCarried(result);
        } else if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }
    }
}
