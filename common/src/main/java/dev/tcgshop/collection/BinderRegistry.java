package dev.tcgshop.collection;

import com.mojang.serialization.Codec;
import dev.tcgshop.TcgShop;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** All General Binders in the world (id -> contents). Saved in the Overworld's data/tcgshop_binders.dat. */
public class BinderRegistry extends SavedData {
    private static final Codec<Map<UUID, BinderData>> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, BinderData.CODEC);
    private static final Factory<BinderRegistry> TYPE = new Factory<>(BinderRegistry::new, BinderRegistry::fromNbt, null);

    private final Map<UUID, BinderData> binders = new HashMap<>();

    public static BinderRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE, "tcgshop_binders");
    }

    public BinderData get(UUID id) {
        return binders.get(id);
    }

    /** 1.1: every binder that player owns (the Mayor takes his card from the fullest one). Fullest first. */
    public java.util.List<BinderData> ownedBy(UUID owner) {
        java.util.List<BinderData> out = new java.util.ArrayList<>();
        for (BinderData d : binders.values()) if (d != null && owner.equals(d.owner)) out.add(d);
        out.sort((x, y) -> Long.compare(y.totalCards(), x.totalCards()));
        return out;
    }

    public BinderData create(UUID id, UUID owner, String ownerName) {
        BinderData d = new BinderData(owner, ownerName, Map.of(), Set.of());
        binders.put(id, d);
        setDirty();
        return d;
    }

    private static BinderRegistry fromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
        BinderRegistry r = new BinderRegistry();
        if (nbt.contains("binders")) {
            CODEC.parse(NbtOps.INSTANCE, nbt.get("binders"))
                    .resultOrPartial(e -> TcgShop.LOGGER.error("Corrupted binders: {}", e))
                    .ifPresent(r.binders::putAll);
        }
        // 1.0: print variants and graded cards, kept as full stacks (absent in older saves = none)
        if (nbt.contains("specials", Tag.TAG_COMPOUND)) {
            CompoundTag sp = nbt.getCompound("specials");
            for (String key : sp.getAllKeys()) {
                BinderData d;
                try { d = r.binders.get(UUID.fromString(key)); } catch (IllegalArgumentException e) { d = null; }
                if (d == null) continue;
                ListTag list = sp.getList(key, Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag e = list.getCompound(i);
                    int n = e.getInt("n");
                    if (n <= 0 || !e.contains("item")) continue;
                    BinderData target = d;
                    ItemStack.parse(lookup, e.get("item")).ifPresent(st -> target.addSpecial(st, n));
                }
            }
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookup) {
        Tag el = CODEC.encodeStart(NbtOps.INSTANCE, binders).getOrThrow();
        nbt.put("binders", el);
        CompoundTag sp = new CompoundTag();
        for (Map.Entry<UUID, BinderData> b : binders.entrySet()) {
            if (b.getValue().specials.isEmpty()) continue;
            ListTag list = new ListTag();
            for (BinderData.Special s : b.getValue().specials) {
                if (s.count <= 0 || s.stack.isEmpty()) continue;
                try {
                    CompoundTag e = new CompoundTag();
                    e.put("item", s.stack.save(lookup));
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
