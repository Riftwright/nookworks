package dev.nookworks.storage;

import java.util.Set;

import com.mojang.serialization.MapCodec;

import dev.nookworks.Nookworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * {@code nookworks:storage_spills}: true unless the block being looted is a Storage Barrel that leaves whole. A barrel
 * too full to break by hand is never looted as an empty barrel: whatever takes it away, it leaves as one item holding
 * everything, made as it goes ({@link StorageBlockEntity#preRemoveSideEffects}), so its contents are never in a loot
 * drop - a drop worked out and thrown away, or worked out twice, can neither lose them nor copy them.
 */
public record StorageSpillsCondition() implements LootItemCondition {
    public static final MapCodec<StorageSpillsCondition> MAP_CODEC = MapCodec.unit(new StorageSpillsCondition());

    public static final DeferredRegister<MapCodec<? extends LootItemCondition>> CONDITIONS =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, Nookworks.MODID);

    static {
        CONDITIONS.register("storage_spills", () -> MAP_CODEC);
    }

    @Override
    public MapCodec<StorageSpillsCondition> codec() {
        return MAP_CODEC;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.BLOCK_ENTITY);
    }

    @Override
    public boolean test(LootContext context) {
        return !(context.getOptional(LootContextParams.BLOCK_ENTITY) instanceof StorageBlockEntity storage)
                || !storage.leavesWhole();
    }
}
