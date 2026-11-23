package dev.tcgshop.collection;

import com.mojang.serialization.Codec;
import dev.tcgshop.TcgShop;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
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
        // 1.0: print variants and graded cards, kept as full stacks (absent in older saves = none)
        if (nbt.contains("specials", NbtElement.COMPOUND_TYPE)) {
            NbtCompound sp = nbt.getCompound("specials");
            for (String key : sp.getKeys()) {
                BinderData d;
                try { d = r.binders.get(UUID.fromString(key)); } catch (IllegalArgumentException e) { d = null; }
                if (d == null) continue;
                NbtList list = sp.getList(key, NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < list.size(); i++) {
                    NbtCompound e = list.getCompound(i);
                    int n = e.getInt("n");
                    if (n <= 0 || !e.contains("item")) continue;
                    BinderData target = d;
                    ItemStack.fromNbt(lookup, e.get("item")).ifPresent(st -> target.addSpecial(st, n));
                }
            }
        }
        return r;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtElement el = CODEC.encodeStart(NbtOps.INSTANCE, binders).getOrThrow();
        nbt.put("binders", el);
        NbtCompound sp = new NbtCompound();
        for (Map.Entry<UUID, BinderData> b : binders.entrySet()) {
            if (b.getValue().specials.isEmpty()) continue;
            NbtList list = new NbtList();
            for (BinderData.Special s : b.getValue().specials) {
                if (s.count <= 0 || s.stack.isEmpty()) continue;
                try {
                    NbtCompound e = new NbtCompound();
                    e.put("item", s.stack.encode(lookup));
                    e.putInt("n", s.count);
                    list.add(e);
                } catch (Exception ex) {
                    TcgShop.LOGGER.error("Could not save a special binder card", ex);
                }
            }
            sp.put(b.getKey().toString(), list);
        }
        nbt.put("specials", sp);
        return nbt;
    }
}
