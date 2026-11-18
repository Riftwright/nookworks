package dev.nookworks.mixin.compat.create;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;

import dev.nookworks.compat.create.CreateGoggles;
import dev.nookworks.cooking.HandMachineBlockEntity;
import dev.nookworks.crop.RicePolisherBlockEntity;
import dev.nookworks.drive.DriveWheelBlockEntity;
import dev.nookworks.machine.MotorBlockEntity;
import dev.nookworks.oil.OilPressBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Our hand-worked machines, Drive Wheels and Motors answer Create's Engineer's Goggles, as Create's own kinetic blocks do: the
 * shaft turning them, how fast, the stress they put on it (CreateGoggles). Added only with Create installed.
 */
@Mixin(value = {OilPressBlockEntity.class, RicePolisherBlockEntity.class, HandMachineBlockEntity.class, DriveWheelBlockEntity.class,
        MotorBlockEntity.class}, remap = false)
abstract class MachineGogglesMixin implements IHaveGoggleInformation {
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return CreateGoggles.add((BlockEntity) (Object) this, tooltip);
    }
}
