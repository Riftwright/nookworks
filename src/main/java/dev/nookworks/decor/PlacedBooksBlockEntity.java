package dev.nookworks.decor;

import java.util.ArrayList;
import java.util.List;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;

import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The books lying on a block, bottom first, each kept as it was laid down (an enchanted book keeps its enchantments, a
 * written book its pages), and each as it fell: turned, and set off from the block's middle. The client is sent the
 * books and how they lie, to draw them.
 */
public class PlacedBooksBlockEntity extends BlockEntity {
    /** How far a book may lie off the middle, in pixels, either way. */
    public static final float REACH = 1.5F;
    /** How far a book laid down now lies off the middle at most, in pixels: its weight over the pile's foot. */
    public static final float SETTLE = 0.5F;
    private final NonNullList<ItemStack> books = NonNullList.withSize(PlacedBooksBlock.MAX, ItemStack.EMPTY);
    /** For each book: the way it lies (degrees about up, 0 its spine to the west), and how far off the middle (pixels). */
    private final float[] yaw = new float[PlacedBooksBlock.MAX];
    private final float[] dx = new float[PlacedBooksBlock.MAX];
    private final float[] dz = new float[PlacedBooksBlock.MAX];
    /** Standing books: how far the pile leans back against its wall, in degrees. */
    private float lean = 15.0F;

    public PlacedBooksBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLACED_BOOKS.get(), pos, state);
    }

    public ItemStack book(int place) {
        return this.books.get(place);
    }

    public float yaw(int place) {
        return this.yaw[place];
    }

    public float dx(int place) {
        return this.dx[place];
    }

    public float dz(int place) {
        return this.dz[place];
    }

    public float lean() {
        return this.lean;
    }

    public int count() {
        int n = 0;
        while (n < PlacedBooksBlock.MAX && !this.books.get(n).isEmpty()) {
            n++;
        }
        return n;
    }

    /**
     * Lays one on the pile as a player facing {@code facing} (degrees) lets it fall. Each book is turned its own way - up
     * to thirty-six degrees either way from the pile's, in steps of six, one in five across - but their middles stay over
     * one another, half a pixel off at most: a pile looks steady when its weight is straight over its foot, however its
     * books are turned (the user, with a photograph of a pile of old books: 角度はバラバラでも、中心というか、重心が
     * ブレてなければ安定して見える). The first sets the pile's way: the player's, its spine to their left, turned a little.
     * On half pixels and six-degree steps, the game's own grid. False when the pile is full.
     */
    public boolean add(ItemStack book, float facing, boolean standing, RandomSource random) {
        int n = this.count();
        if (n >= PlacedBooksBlock.MAX) {
            return false;
        }
        this.books.set(n, book.copyWithCount(1));
        if (standing) {
            // leant against the wall, cover out: the pile leans back twelve to eighteen degrees, each book turned a
            // few degrees from square and set a pixel or so along the wall, as books leant there by hand are
            if (n == 0) {
                this.lean = 12.0F + random.nextInt(3) * 3.0F;
            }
            this.yaw[n] = (random.nextInt(5) - 2) * 3.0F;
            this.dx[n] = (random.nextInt(5) - 2) * 0.5F;
            this.dz[n] = 0.0F;
            this.updated();
            return true;
        }
        // the first lies the player's way in the world, kept as the pile's own (a pile in a turned structure, empty)
        float pile = n == 0 ? PlacedBooksBlock.yawKept(this.getBlockState(), -facing + 180.0F + (random.nextInt(7) - 3) * 6.0F) : this.yaw[0];
        float turn = n == 0 ? 0.0F : (random.nextInt(13) - 6) * 6.0F + (random.nextInt(5) == 0 ? 90.0F : 0.0F);
        this.yaw[n] = Mth.wrapDegrees(pile + turn);
        this.dx[n] = (random.nextInt(3) - 1) * SETTLE;
        this.dz[n] = (random.nextInt(3) - 1) * SETTLE;
        this.updated();
        return true;
    }

    /** The top one, taken up; empty when there is none. */
    public ItemStack take() {
        int n = this.count();
        if (n == 0) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = this.books.get(n - 1);
        this.books.set(n - 1, ItemStack.EMPTY);
        this.updated();
        return taken;
    }

    private void updated() {
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null && !this.level.isClientSide()) {
            for (ItemStack book : this.books) {
                Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, book.copy());
            }
            this.books.clear(); // dropped, so gone from here
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.save(output, this.books);
    }

    /** The books (or what the client is shown of them) and how they lie. */
    private void save(ValueOutput output, NonNullList<ItemStack> books) {
        ContainerHelper.saveAllItems(output, books, true);
        List<Float> lie = new ArrayList<>();
        for (int i = 0; i < PlacedBooksBlock.MAX; i++) {
            lie.add(this.yaw[i]);
            lie.add(this.dx[i]);
            lie.add(this.dz[i]);
        }
        output.store("Lie", Codec.FLOAT.listOf(), lie);
        output.putFloat("Lean", this.lean);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.books.clear();
        ContainerHelper.loadAllItems(input, this.books);
        this.lean = Mth.clamp(input.getFloatOr("Lean", 15.0F), 0.0F, 30.0F);
        List<Float> lie = input.read("Lie", Codec.FLOAT.listOf()).orElse(List.of());
        for (int i = 0; i < PlacedBooksBlock.MAX; i++) {
            boolean known = lie.size() >= 3 * (i + 1);
            this.yaw[i] = known ? Mth.wrapDegrees(finite(lie.get(3 * i))) : 0.0F;
            this.dx[i] = known ? Mth.clamp(finite(lie.get(3 * i + 1)), -REACH, REACH) : 0.0F;
            this.dz[i] = known ? Mth.clamp(finite(lie.get(3 * i + 2)), -REACH, REACH) : 0.0F;
            if (this.books.get(i).getCount() > 1) {
                this.books.set(i, this.books.get(i).copyWithCount(1)); // one book to a place, whatever a command wrote
            }
        }
    }

    /** A number read from saved data, a garbled one (NaN, infinite - an NBT editor's) taken as none. */
    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * The client is sent how the books lie and what each is - the bare item, which is all its cover shows - not the
     * books themselves: a written book's pages are a book's worth of text, and a draft in a book and quill anyone's to
     * read near by (the r18 review; vanilla's lectern and shelf send nothing of their books).
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            this.save(output, shown(this.books));
            return output.buildResult();
        }
    }

    /** Each book as the client is shown it: its item alone. */
    static NonNullList<ItemStack> shown(NonNullList<ItemStack> books) {
        NonNullList<ItemStack> shown = NonNullList.withSize(books.size(), ItemStack.EMPTY);
        for (int i = 0; i < books.size(); i++) {
            if (!books.get(i).isEmpty()) {
                shown.set(i, new ItemStack(books.get(i).getItem()));
            }
        }
        return shown;
    }
}
