package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;

import dev.nookworks.feature.SaveFixes;
import net.minecraft.util.datafix.DataFixTypes;

/**
 * Whatever the game's data fixer brings up to date - a chunk, a player, a structure - has our items brought up to date
 * too, which the fixer does not reach, and the entities it cannot read set aside round it so that the rest are still
 * read ({@link SaveFixes}). Every update of saved data passes through here.
 */
@Mixin(DataFixTypes.class)
abstract class DataFixTypesMixin {
    // required, as few hooks are (HookCheck): a world's data goes through it, and it is never left out
    @WrapMethod(method = "update(Lcom/mojang/datafixers/DataFixer;Lcom/mojang/serialization/Dynamic;II)Lcom/mojang/serialization/Dynamic;", require = 1)
    private <T> Dynamic<T> nookworks$updateOurItems(DataFixer fixer, Dynamic<T> input, int fromVersion, int toVersion,
            Operation<Dynamic<T>> original) {
        return SaveFixes.update((DataFixTypes) (Object) this, fixer, input, fromVersion, toVersion,
                data -> original.call(fixer, data, fromVersion, toVersion));
    }
}
