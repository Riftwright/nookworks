package dev.nookworks.port.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Stand-in for 26.3's {@code SavedDataType}: saved data named by an id, made new by a constructor and kept with a codec.
 * <p>
 * 26.3 asks the storage for it ({@code storage.computeIfAbsent(TYPE)}); 1.21.1's storage takes a name and a
 * {@code SavedData.Factory} instead, and a server has no storage of its own. So the mod calls
 * {@link #computeIfAbsent(MinecraftServer, SavedDataType)} / {@link #get(ServerLevel, SavedDataType)} here: the server's
 * data is the overworld's (the same {@code data} folder 26.3's server storage uses). The file keeps 26.3's name,
 * {@code data/<namespace>/<path>.dat}, and 26.3's contents ({@link SavedData}).
 */
public record SavedDataType<T extends SavedData>(ResourceLocation id, Supplier<T> constructor, Codec<T> codec) {
    /** The server's data (26.3's {@code server.getDataStorage()}), made new if there is none. */
    public static <T extends SavedData> T computeIfAbsent(MinecraftServer server, SavedDataType<T> type) {
        return computeIfAbsent(overworld(server, type), type);
    }

    private static ServerLevel overworld(MinecraftServer server, SavedDataType<?> type) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            // 26.3's server storage is there from the start; the overworld's comes with the levels
            throw new IllegalStateException(type + " asked for before the server's levels are made");
        }
        return overworld;
    }

    /** A level's data, made new if there is none. */
    public static <T extends SavedData> T computeIfAbsent(ServerLevel level, SavedDataType<T> type) {
        DimensionDataStorage storage = level.getDataStorage();
        type.folder(level);
        T data = storage.computeIfAbsent(type.factory(storage), type.fileName());
        data.bind(type);
        return data;
    }

    /** The server's data, or null if there is none yet. */
    public static <T extends SavedData> @Nullable T get(MinecraftServer server, SavedDataType<T> type) {
        return get(overworld(server, type), type);
    }

    /** A level's data, or null if there is none yet. */
    public static <T extends SavedData> @Nullable T get(ServerLevel level, SavedDataType<T> type) {
        DimensionDataStorage storage = level.getDataStorage();
        T data = storage.get(type.factory(storage), type.fileName());
        if (data != null) {
            data.bind(type);
        }
        return data;
    }

    /** 1.21.1's name for the file, within the data folder: {@code <namespace>/<path>}, as 26.3 lays it out. */
    String fileName() {
        return this.id.getNamespace() + "/" + this.id.getPath();
    }

    /** The namespace's folder must be there before 1.21.1 writes a file into it (it makes no folders). */
    private void folder(ServerLevel level) {
        Path root = level.getServer().getWorldPath(LevelResource.ROOT);
        Path folder = DimensionType.getStorageFolder(level.dimension(), root).resolve("data").resolve(this.id.getNamespace());
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            LogUtils.getLogger().error("Could not make the folder for {}", this.id, e);
        }
    }

    private Factory<T> factory(DimensionDataStorage storage) {
        // 1.21.1 hands the reader only the "data" compound; 26.3's codec may have written something else there (a list),
        // so the file is read again whole and its "data" decoded as it is
        return new Factory<>(this::create, (ignored, registries) -> this.read(storage, registries), null);
    }

    private T create() {
        T data = this.constructor.get();
        data.bind(this);
        return data;
    }

    private @Nullable T read(DimensionDataStorage storage, HolderLookup.Provider registries) {
        try {
            CompoundTag file = storage.readTagFromDisk(this.fileName(), null, SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            Tag data = file.get("data");
            if (data == null) {
                return null;
            }
            T read = this.codec.parse(registries.createSerializationContext(NbtOps.INSTANCE), data)
                    .resultOrPartial(error -> LogUtils.getLogger().error("Could not read {}: {}", this.id, error)).orElse(null);
            if (read != null) {
                read.bind(this);
            }
            return read;
        } catch (IOException e) {
            LogUtils.getLogger().error("Could not read {}", this.id, e);
            return null;
        }
    }

    /** The data as its codec writes it; what does not encode is logged, its partial result kept. */
    @Nullable Tag encode(SavedData data, HolderLookup.Provider registries) {
        @SuppressWarnings("unchecked")
        T typed = (T) data;
        return this.codec.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), typed)
                .resultOrPartial(error -> LogUtils.getLogger().error("Could not save {}: {}", this.id, error)).orElse(null);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SavedDataType<?> type && this.id.equals(type.id);
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public String toString() {
        return "SavedDataType[" + this.id + "]";
    }
}
