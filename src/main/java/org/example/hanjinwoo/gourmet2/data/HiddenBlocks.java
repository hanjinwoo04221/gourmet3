package org.example.hanjinwoo.gourmet2.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * The blocks this mod has taken out of the world to stand a display in for, so they can be put back.
 *
 * <p>A burst hides the ground it copies behind a barrier and puts it back when the piece goes. That works
 * while the level is running, but a save that lands in the couple of seconds in between leaves barriers in the
 * world with nothing left to undo them — so every block that is taken out is written down here, and anything
 * still on the list when a level loads is put back before anyone can walk into an invisible wall.
 */
public class HiddenBlocks extends SavedData {
    private static final String FILE_ID = "gourmet2_hidden_blocks";
    private static final String TAG_BLOCKS = "Blocks";
    private static final String TAG_STATE = "State";

    private final Map<BlockPos, BlockState> hidden = new HashMap<>();

    /** This level's list, read from disk the first time it is asked for. */
    public static HiddenBlocks of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(HiddenBlocks::new, HiddenBlocks::load), FILE_ID);
    }

    /** Writes down a block taken out of the world. */
    public void add(BlockPos pos, BlockState state) {
        hidden.put(pos.immutable(), state);
        setDirty();
    }

    /** Forgets a block that has been put back. */
    public void remove(BlockPos pos) {
        if (hidden.remove(pos) != null) {
            setDirty();
        }
    }

    /** Puts everything still on the list back, and empties it. */
    public void restore(ServerLevel level) {
        if (hidden.isEmpty()) {
            return;
        }
        for (Map.Entry<BlockPos, BlockState> entry : hidden.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
        hidden.clear();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, BlockState> entry : hidden.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putInt("x", entry.getKey().getX());
            one.putInt("y", entry.getKey().getY());
            one.putInt("z", entry.getKey().getZ());
            one.put(TAG_STATE, NbtUtils.writeBlockState(entry.getValue()));
            list.add(one);
        }
        tag.put(TAG_BLOCKS, list);
        return tag;
    }

    private static HiddenBlocks load(CompoundTag tag, HolderLookup.Provider registries) {
        HiddenBlocks data = new HiddenBlocks();
        ListTag list = tag.getList(TAG_BLOCKS, 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            data.hidden.put(new BlockPos(one.getInt("x"), one.getInt("y"), one.getInt("z")),
                    NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK),
                            one.getCompound(TAG_STATE)));
        }
        return data;
    }
}
