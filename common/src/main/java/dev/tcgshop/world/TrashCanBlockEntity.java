package dev.tcgshop.world;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider lookup) {
        super.saveAdditional(nbt, lookup);
        nbt.putLong("SearchedAt", searchedAt);
        nbt.putInt("Searches", searches);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider lookup) {
        super.loadAdditional(nbt, lookup);
        searchedAt = nbt.contains("SearchedAt") ? nbt.getLong("SearchedAt") : -1;
        searches = nbt.getInt("Searches");
    }
}
