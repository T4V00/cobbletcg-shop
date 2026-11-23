package dev.tcgshop.collection;

import com.mojang.serialization.Codec;
import dev.tcgshop.TcgShop;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** All General Binders in the world (id -> contents). Saved in the Overworld's data/tcgshop_binders.dat. */
public class BinderRegistry extends PersistentState {
    private static final Codec<Map<UUID, BinderData>> CODEC = Codec.unboundedMap(Uuids.STRING_CODEC, BinderData.CODEC);
    private static final Type<BinderRegistry> TYPE = new Type<>(BinderRegistry::new, BinderRegistry::fromNbt, null);

    private final Map<UUID, BinderData> binders = new HashMap<>();

    public static BinderRegistry get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "tcgshop_binders");
    }

    public BinderData get(UUID id) {
        return binders.get(id);
    }

    public BinderData create(UUID id, UUID owner, String ownerName) {
        BinderData d = new BinderData(owner, ownerName, Map.of(), Set.of());
        binders.put(id, d);
        markDirty();
        return d;
    }

    private static BinderRegistry fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        BinderRegistry r = new BinderRegistry();
        if (nbt.contains("binders")) {
            CODEC.parse(NbtOps.INSTANCE, nbt.get("binders"))
                    .resultOrPartial(e -> TcgShop.LOGGER.error("Corrupted binders: {}", e))
                    .ifPresent(r.binders::putAll);
        }
        return r;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtElement el = CODEC.encodeStart(NbtOps.INSTANCE, binders).getOrThrow();
        nbt.put("binders", el);
        return nbt;
    }
}
