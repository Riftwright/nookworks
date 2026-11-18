package dev.nookworks.storage;

import java.util.Set;

import com.mojang.serialization.MapCodec;

import dev.nookworks.Nookworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * {@code nookworks:storage_spills}: true unless the block being looted is a Storage Barrel that leaves whole. A barrel
 * too full to break by hand is never looted as an empty barrel: whatever takes it away, it leaves as one item holding
 * everything, made as it goes ({@link StorageBlockEntity#preRemoveSideEffects}), so its contents are never in a loot
 * drop - a drop worked out and thrown away, or worked out twice, can neither lose them nor copy them.
 */
public record StorageSpillsCondition() implements LootItemCondition {
    public static final MapCodec<StorageSpillsCondition> MAP_CODEC = MapCodec.unit(new StorageSpillsCondition());

    // PORT: 1.21.1 registers a condition type holding the codec, where 26.3 registers the codec itself
    public static final DeferredRegister<LootItemConditionType> CONDITIONS =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, Nookworks.MODID);

    public static final DeferredHolder<LootItemConditionType, LootItemConditionType> TYPE =
            CONDITIONS.register("storage_spills", () -> new LootItemConditionType(MAP_CODEC));

    @Override
    public LootItemConditionType getType() {
        return TYPE.get();
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.BLOCK_ENTITY);
    }

    @Override
    public boolean test(LootContext context) {
        return !(context.getParamOrNull(LootContextParams.BLOCK_ENTITY) instanceof StorageBlockEntity storage)
                || !storage.leavesWhole();
    }
}
