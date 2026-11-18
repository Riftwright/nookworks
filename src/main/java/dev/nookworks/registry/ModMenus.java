package dev.nookworks.registry;

import dev.nookworks.trading.TradingCounterMenu;
import java.util.function.Supplier;

import dev.nookworks.Nookworks;
import dev.nookworks.block.entity.CrateBlockEntity;
import dev.nookworks.experience.RepairBoxMenu;
import dev.nookworks.machine.MachineMenu;
import dev.nookworks.menu.CrateMenu;
import dev.nookworks.menu.NestedTimerMenu;
import dev.nookworks.mobcapture.SpawnEggBrewerMenu;
import dev.nookworks.poster.PrintingPressMenu;
import dev.nookworks.sorter.SorterMenu;
import dev.nookworks.spawner.SpawnerControllerMenu;
import dev.nookworks.storage.StorageMenu;
import dev.nookworks.travel.DaemonPanelMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Nookworks.MODID);

    public static final Supplier<MenuType<SorterMenu>> SORTER = MENUS.register("sorter",
            () -> IMenuTypeExtension.create((id, inventory, data) -> SorterMenu.client(id, inventory, data)));

    public static final Supplier<MenuType<StorageMenu>> STORAGE = MENUS.register("storage",
            () -> IMenuTypeExtension.create(StorageMenu::client));

    public static final Supplier<MenuType<SpawnerControllerMenu>> SPAWNER_CONTROLLER = MENUS.register("spawner_controller",
            () -> IMenuTypeExtension.create((id, inventory, data) -> new SpawnerControllerMenu(id, inventory)));

    public static final Supplier<MenuType<SpawnEggBrewerMenu>> SPAWN_EGG_BREWER = MENUS.register("spawn_egg_brewer",
            () -> IMenuTypeExtension.create((id, inventory, data) -> new SpawnEggBrewerMenu(id, inventory)));

    public static final Supplier<MenuType<PrintingPressMenu>> PRINTING_PRESS = MENUS.register("printing_press",
            () -> IMenuTypeExtension.create((id, inventory, data) -> new PrintingPressMenu(id, inventory)));

    public static final Supplier<MenuType<NestedTimerMenu>> NESTED_TIMER = MENUS.register("nested_timer",
            () -> IMenuTypeExtension.create((id, inventory, data) -> new NestedTimerMenu(id)));

    public static final Supplier<MenuType<RepairBoxMenu>> REPAIR_BOX = MENUS.register("repair_box",
            () -> IMenuTypeExtension.create(RepairBoxMenu::client));

    public static final Supplier<MenuType<CrateMenu>> CRATE = MENUS.register("crate",
            () -> IMenuTypeExtension.create((id, inventory, data) -> new CrateMenu(id, inventory,
                    new SimpleContainer(CrateBlockEntity.SIZE))));

    /** Every FE machine's screen; which one is sent as its {@link dev.nookworks.machine.MachineLayout}. */
    public static final Supplier<MenuType<MachineMenu>> MACHINE = MENUS.register("machine",
            () -> IMenuTypeExtension.create(MachineMenu::client));

    public static final Supplier<MenuType<DaemonPanelMenu>> DESTINATION_PANEL = MENUS.register("destination_panel",
            () -> IMenuTypeExtension.create(DaemonPanelMenu::client));

    public static final Supplier<MenuType<TradingCounterMenu>> TRADING_COUNTER = MENUS.register("trading_counter",
            () -> IMenuTypeExtension.create((id, inventory, data) -> new TradingCounterMenu(id, inventory)));

    private ModMenus() {}
}
