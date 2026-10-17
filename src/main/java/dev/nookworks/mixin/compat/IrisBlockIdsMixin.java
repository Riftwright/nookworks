package dev.nookworks.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import dev.nookworks.client.ShaderAliases;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.level.block.state.BlockState;

/**
 * When Iris hands a shader pack's block table to the world renderer, our leaves, saplings and crops are added to it
 * under their vanilla counterparts' numbers ({@link ShaderAliases}), so a pack sways them in the wind as it sways
 * oak leaves and wheat.
 * <p>
 * Applied only when Iris is there: the config's plugin ({@link CompatMixins}) adds it then and only then, and it is
 * {@code @Pseudo} besides.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings", remap = false)
abstract class IrisBlockIdsMixin {
    @ModifyVariable(method = "setBlockStateIds(Lit/unimi/dsi/fastutil/objects/Object2IntMap;)V", at = @At("HEAD"), argsOnly = true)
    private Object2IntMap<BlockState> nookworks$aliasOurPlants(Object2IntMap<BlockState> ids) {
        return ids == null ? null : ShaderAliases.withAliases(ids);
    }
}
