package dev.nookworks.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import dev.nookworks.spawner.ControlledSpawner;
import dev.nookworks.spawner.SpawnerHold;
import dev.nookworks.spawner.SpawnerSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Lets a Spawner Controller (see {@link ControlledSpawner}) override a vanilla spawner while it is powered:
 * on/off, a fixed interval, spawns per cycle, the nearby cap, ignoring light, and running without players.
 * Vanilla values are only replaced where they are read, so nothing changes once the control lapses.
 */
@Mixin(BaseSpawner.class)
abstract class BaseSpawnerMixin implements ControlledSpawner {
    @Shadow
    private int spawnDelay;
    @Shadow
    private @Nullable SpawnData nextSpawnData;
    @Shadow
    private double spin;
    @Shadow
    private double oSpin;

    @Unique
    private @Nullable SpawnerSettings nookworks$settings;
    @Unique
    private long nookworks$until;
    /** The controller holding it off, saved with the spawner: see {@link ControlledSpawner}. */
    @Unique
    private @Nullable GlobalPos nookworks$heldBy;
    /** On a client, whether it is held off: see {@link ControlledSpawner#nookworks$shownOff}. */
    @Unique
    private boolean nookworks$shownOff;

    @Override
    public boolean nookworks$holdOff(GlobalPos by) {
        boolean news = !by.equals(this.nookworks$heldBy);
        this.nookworks$heldBy = by;
        return news;
    }

    @Override
    public boolean nookworks$release() {
        boolean was = this.nookworks$heldBy != null;
        this.nookworks$heldBy = null;
        return was;
    }

    @Override
    public @Nullable GlobalPos nookworks$heldBy() {
        return this.nookworks$heldBy;
    }

    @Override
    public boolean nookworks$shownOff() {
        return this.nookworks$shownOff;
    }

    @Inject(method = "save", at = @At("TAIL"))
    private void nookworks$saveHold(ValueOutput output, CallbackInfo ci) {
        output.storeNullable("NookworksHeldOffBy", GlobalPos.CODEC, this.nookworks$heldBy);
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void nookworks$loadHold(@Nullable Level level, BlockPos pos, ValueInput input, CallbackInfo ci) {
        this.nookworks$heldBy = input.read("NookworksHeldOffBy", GlobalPos.CODEC).orElse(null);
        this.nookworks$shownOff = input.getBooleanOr("NookworksOff", false);
    }

    /**
     * Held off, it looks it: the mob in the cage stops turning and no flame or smoke rises, as a spawner with no player
     * near. Vanilla turns it and smokes on the client whatever the server does with it.
     */
    @Inject(method = "clientTick", at = @At("HEAD"), cancellable = true)
    private void nookworks$stillWhileOff(Level level, BlockPos pos, CallbackInfo ci) {
        if (this.nookworks$shownOff) {
            this.oSpin = this.spin;
            ci.cancel();
        }
    }

    @Override
    public void nookworks$control(SpawnerSettings settings, long untilGameTime) {
        this.nookworks$settings = settings;
        this.nookworks$until = untilGameTime;
    }

    @Override
    public @Nullable SpawnerSettings nookworks$activeControl(Level level) {
        if (this.nookworks$settings != null && level.getGameTime() > this.nookworks$until) {
            this.nookworks$settings = null;
        }
        return this.nookworks$settings;
    }

    @Override
    public @Nullable EntityType<?> nookworks$entityType() {
        return this.nextSpawnData == null ? null
                : this.nextSpawnData.getEntityToSpawn().getString("id")
                        .map(Identifier::tryParse)
                        .flatMap(BuiltInRegistries.ENTITY_TYPE::getOptional)
                        .orElse(null);
    }

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private void nookworks$controlledTick(ServerLevel level, BlockPos pos, CallbackInfo ci) {
        SpawnerSettings s = this.nookworks$activeControl(level);
        if (s == null) {
            if (this.nookworks$heldBy != null) {
                // the controller is out of reach: the spawner holds itself off, looking now and then whether it still should
                if (level.getGameTime() % SpawnerHold.LOOK_EVERY != 0 || SpawnerHold.stillHeld(level, pos, this.nookworks$heldBy)) {
                    ci.cancel();
                } else {
                    this.nookworks$heldBy = null;
                    SpawnerHold.released(level, pos);
                }
            }
            return;
        }
        if (!s.enabled()) {
            ci.cancel();
            return;
        }
        if (this.spawnDelay > s.interval()) {
            this.spawnDelay = s.interval(); // a shorter interval takes effect now, not after the old wait
        }
    }

    @WrapOperation(method = "isNearPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;hasNearbyAlivePlayer(DDDD)Z"))
    private boolean nookworks$playerOptional(Level level, double x, double y, double z, double range, Operation<Boolean> original) {
        SpawnerSettings s = level.isClientSide() ? null : this.nookworks$activeControl(level);
        return (s != null && !s.needsPlayer()) || original.call(level, x, y, z, range);
    }

    @ModifyExpressionValue(method = "serverTick", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/level/BaseSpawner;spawnCount:I"))
    private int nookworks$count(int original, @Local(argsOnly = true) ServerLevel level) {
        SpawnerSettings s = this.nookworks$activeControl(level);
        return s == null ? original : s.count();
    }

    @ModifyExpressionValue(method = "serverTick", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/level/BaseSpawner;maxNearbyEntities:I"))
    private int nookworks$maxNearby(int original, @Local(argsOnly = true) ServerLevel level) {
        SpawnerSettings s = this.nookworks$activeControl(level);
        return s == null ? original : s.maxNearby();
    }

    @ModifyExpressionValue(method = "delay", at = {
            @At(value = "FIELD", target = "Lnet/minecraft/world/level/BaseSpawner;minSpawnDelay:I"),
            @At(value = "FIELD", target = "Lnet/minecraft/world/level/BaseSpawner;maxSpawnDelay:I") })
    private int nookworks$interval(int original, @Local(argsOnly = true) Level level) {
        SpawnerSettings s = this.nookworks$activeControl(level);
        return s == null ? original : s.interval();
    }

    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/SpawnPlacements;checkSpawnRules(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)Z"))
    private boolean nookworks$anyLight(EntityType<?> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
            RandomSource random, Operation<Boolean> original, @Local(argsOnly = true) ServerLevel serverLevel) {
        SpawnerSettings s = this.nookworks$activeControl(serverLevel);
        if (s != null && s.anyLight()) {
            // vanilla's placement rule (the light, the floor) waived; peaceful still as vanilla has it, and other mods
            // still asked, as checkSpawnRules asks them: a mod that stops spawner spawns there is heard
            if (!type.isAllowedInPeaceful() && serverLevel.getDifficulty() == Difficulty.PEACEFUL) {
                return false;
            }
            return net.neoforged.neoforge.event.EventHooks.checkSpawnPlacements(type, level, reason, pos, random, true);
        }
        return original.call(type, level, reason, pos, random);
    }

    /**
     * The mob's own say over where it spawns, which NeoForge asks after the placement rules above. For an animal it
     * weighs the light as well - a pig will not stand on stone in the dark - so "any light" has to be heard here too, or
     * a controlled pig spawner on a stone floor quietly stops at nightfall. With any light, only room to stand counts -
     * of the mob's own say: other mods' say through NeoForge's position check is still asked.
     */
    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/event/EventHooks;checkSpawnPositionSpawner(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/level/SpawnData;Lnet/minecraft/world/level/BaseSpawner;)Z"))
    private boolean nookworks$anyLightMob(Mob mob, ServerLevelAccessor level, EntitySpawnReason reason,
            SpawnData data, BaseSpawner spawner, Operation<Boolean> original, @Local(argsOnly = true) ServerLevel serverLevel) {
        SpawnerSettings s = this.nookworks$activeControl(serverLevel);
        if (s != null && s.anyLight()) {
            var event = new net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck(mob, level, reason, spawner);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
            return switch (event.getResult()) {
                case DEFAULT -> mob.checkSpawnObstruction(level);
                case SUCCEED -> true;
                case FAIL -> false;
            };
        }
        return original.call(mob, level, reason, data, spawner);
    }
}
