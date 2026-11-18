package dev.nookworks.port.storage;

import java.io.File;
import java.io.IOException;

import org.jspecify.annotations.Nullable;

import com.mojang.logging.LogUtils;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.IOUtilities;

/**
 * Stand-in for 26.3's {@code SavedData}, which a {@link SavedDataType}'s codec saves (1.21.1's subclasses write a tag in
 * {@code save(CompoundTag, Provider)}). The file is written as 26.3 writes it: {@code {data: <the codec's encoding>,
 * DataVersion: n}} - also when the codec encodes to something other than a compound (a list), which 1.21.1's own
 * {@code SavedData.save(File, ...)} could not hold.
 * <p>
 * Its type is set by {@link SavedDataType#computeIfAbsent} / {@link SavedDataType#get} when it is made or read: only
 * data from there is ever saved by the game.
 */
public abstract class SavedData extends net.minecraft.world.level.saveddata.SavedData {
    private @Nullable SavedDataType<?> type;

    void bind(SavedDataType<?> type) {
        this.type = type;
    }

    /** The codec's encoding, when it is a compound (1.21.1 asks for one here; the file itself is written by {@link #save(File, HolderLookup.Provider)}). */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (this.encode(registries) instanceof CompoundTag encoded) {
            tag.merge(encoded);
        }
        return tag;
    }

    @Override
    public void save(File file, HolderLookup.Provider registries) {
        if (!this.isDirty()) {
            return;
        }
        Tag data = this.encode(registries);
        if (data == null) {
            this.setDirty(false);
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.put("data", data);
        NbtUtils.addCurrentDataVersion(tag);
        CompoundTag copied = tag.copy(); // written on the IO thread: what is saved is what there was now
        IOUtilities.withIOWorker(() -> {
            try {
                IOUtilities.writeNbtCompressed(copied, file.toPath());
            } catch (IOException e) {
                LogUtils.getLogger().error("Could not save data {}", this, e);
            }
        });
        this.setDirty(false);
    }

    private @Nullable Tag encode(HolderLookup.Provider registries) {
        if (this.type == null) {
            LogUtils.getLogger().error("Saved data {} was not made through its type, so it is not saved", this);
            return null;
        }
        return this.type.encode(this, registries);
    }
}
