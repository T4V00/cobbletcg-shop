package dev.tcgshop.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Trash can state that does not fit in the block state: when it was last searched (world time, for the refill of
 * street cans) and the search in progress (not saved: a search interrupted by a restart simply never happened).
 */
public class TrashCanBlockEntity extends BlockEntity {
    /** World time (World.getTime) of the last completed search, -1 = never. */
    public long searchedAt = -1;
    /** Completed searches (statistics only). */
    public int searches;
    /** Player currently rummaging and the animation step (transient). */
    @Nullable UUID searcher;
    int step;

    public TrashCanBlockEntity(BlockPos pos, BlockState state) {
        super(WorldLoot.TRASH_CAN_BE, pos, state);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        nbt.putLong("SearchedAt", searchedAt);
        nbt.putInt("Searches", searches);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        searchedAt = nbt.contains("SearchedAt") ? nbt.getLong("SearchedAt") : -1;
        searches = nbt.getInt("Searches");
    }
}
