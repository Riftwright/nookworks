package dev.nookworks.machine;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookworks.experience.Move;
import dev.nookworks.fluid.CopperTankBlock;
import dev.nookworks.mercury.MercuryBucketItem;
import dev.nookworks.mercury.MercuryHandler;
import dev.nookworks.mercury.MercuryMachineBlockEntity;
import dev.nookworks.mercury.MercuryStore;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModItems;
import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.fluid.OneTank;
import dev.nookworks.fluid.Tanks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.util.Prediction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

/**
 * A bucket or bottle held on the cursor and clicked on a machine screen's tank gauge (the user, 2026-10-26: 「液体メーター
 * みたいなところに水銀入りバケツをドラッグドロップしたら、水銀が入れられる（逆にからのバケツや瓶を…取り出せる）」): a full one
 * pours into that tank, an empty one draws out of it - any tank, the ones a pipe may not draw from too, but only what the
 * gauge holds goes in or out, and each tank still takes only what it takes. Bottles of water, oil and biodiesel go by
 * the Copper Tank's own bottleOf / filledBottle (nookTechnology hooks those for its own bottles); buckets and any other
 * container by its fluid capability; a bucket of mercury, which has none, into a mercury tank by itself. The Barter
 * Terminal's air is no liquid and takes nothing.
 * <p>
 * Public for other mods (nookTechnology, its own screens): {@link #pour(Player, AbstractContainerMenu, ResourceHandler, GuiTank.Kind)}
 * does it for any tank handler; it is listed in docs/compat.md.
 */
public final class GaugePouring {
    /** A bucket's and a bottle's worth. */
    public static final int BUCKET = 1000;
    public static final int BOTTLE = OilFluids.BOTTLE;

    private GaugePouring() {}

    /** Gauge {@code index} of {@code menu}'s machine clicked by {@code player} with what their cursor holds. True if anything moved. */
    public static boolean clicked(ServerPlayer player, MachineMenu menu, int index) {
        // a spectator touches nothing, as a slot click of theirs does not: one made so with the screen open too (r19)
        if (player.isSpectator() || !(menu.machine() instanceof BlockEntity machine) || !(machine instanceof MachineInfo info)
                || !menu.stillValid(player)) {
            return false;
        }
        List<GuiTank> tanks = info.guiTanks();
        if (index < 0 || index >= tanks.size()) {
            return false;
        }
        GuiTank.Kind kind = tanks.get(index).kind();
        boolean moved;
        if (kind == GuiTank.Kind.MERCURY) {
            moved = machine instanceof MercuryMachineBlockEntity mercury && pourMercury(player, menu, mercury);
        } else {
            ResourceHandler<FluidResource> tank = info.gaugeTank(index);
            if (tank == null && machine.getLevel() != null) {
                tank = machine.getLevel().getCapability(Capabilities.Fluid.BLOCK, machine.getBlockPos(), null);
            }
            moved = tank != null && pour(player, menu, tank, kind);
        }
        if (moved) {
            machine.setChanged();
            menu.broadcastChanges();
        }
        return moved;
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

    /** A bucket of mercury into {@code store}, or an empty bucket filled from it. */
    private static boolean pourMercury(Player player, AbstractContainerMenu menu, MercuryMachineBlockEntity machine) {
        MercuryStore store = machine.mercuryStore();
        ItemStack carried = menu.getCarried();
        if (carried.getItem() instanceof MercuryBucketItem) {
            // not into a machine that makes mercury (the Retort, the Distiller): its gauge only gives, as a click on it
            // turns the bucket down (the round-two fix re-check: the two said different things)
            if (machine.mercuryRole() == MercuryHandler.Role.PRODUCER || store.fill(BUCKET, true) < BUCKET) {
                return false;
            }
            store.fill(BUCKET, false);
            swapOne(player, menu, new ItemStack(Items.BUCKET));
            // mercury's own sounds, the lava bucket's, as at a Mercury Tank and in the world (r21 round three)
            player.level().playSound(null, player.blockPosition(), SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.PLAYERS, 1.0F, 1.2F);
            return true;
        }
        if (carried.is(Items.BUCKET)) {
            if (store.drain(BUCKET, true) < BUCKET) {
                return false;
            }
            store.drain(BUCKET, false);
            swapOne(player, menu, new ItemStack(ModItems.MERCURY_BUCKET.get()));
            player.level().playSound(null, player.blockPosition(), SoundEvents.BUCKET_FILL_LAVA, SoundSource.PLAYERS, 1.0F, 1.2F);
            return true;
        }
        return false;
    }

    /** Whether a gauge of {@code kind} holds {@code fluid}: what may go in by it, and come out. */
    public static boolean holds(GuiTank.Kind kind, Fluid fluid) {
        return switch (kind) {
            case WATER -> fluid.isSame(Fluids.WATER);
            case VEGETABLE_OIL -> fluid.isSame(OilFluids.SOURCE.get());
            case BIODIESEL -> fluid.isSame(OilFluids.BIODIESEL.get());
            case LIQUID_FUEL -> fluid.isSame(OilFluids.SOURCE.get()) || fluid.isSame(OilFluids.BIODIESEL.get());
            case EXPERIENCE -> fluid.is(ExperienceFluids.EXPERIENCE);
            case MERCURY, NETHER_AIR -> false;
        };
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
            player.drop(result, false, Prediction.PREDICTED);
        }
    }
}
