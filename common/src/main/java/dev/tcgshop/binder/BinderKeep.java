package dev.tcgshop.binder;

import dev.masterball.api.platform.event.PlayerEvents;
import dev.masterball.api.platform.event.ServerEvents;
import dev.tcgshop.TcgShop;
import dev.tcgshop.compat.AccessoriesCompat;
import dev.tcgshop.item.TcgItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 1.1: the mod's binders (General and Personal Binder) never drop on death, like keepInventory but only for them.
 * <p>
 * At the very start of a player's death (mixin ServerPlayerDeathMixin, HEAD of ServerPlayer.die, before vanilla drops,
 * Accessories slots and gravestone mods collect anything) every binder in the inventory, in the Accessories slots, on the
 * cursor and in the 2x2 crafting grid is taken out and kept in data/tcgshop_kept_binders.dat (so a server restart while
 * the player is on the death screen loses nothing), remembering where each one was. They are handed back to the same
 * place (Accessories slot or inventory slot, else anywhere in the inventory) on respawn (PlayerEvents.RESPAWN) or, if
 * that was missed, the next time the player joins alive.
 * <p>
 * The death can still be cancelled after our HEAD inject (NeoForge's cancellable LivingDeathEvent fires inside die, and
 * Fabric revive / totem mods cancel die from their own mixins). So every stash is re-checked at the end of the tick: if
 * that player entity is still the live one and alive, the death did not happen and the binders go straight back.
 * With the keepInventory game rule on nothing is done (vanilla already keeps them).
 */
public final class BinderKeep extends SavedData {
    private static final Factory<BinderKeep> TYPE = new Factory<>(BinderKeep::new, BinderKeep::load, null);

    /** A kept binder and where it was: inventory slot (-1 = none), Accessories slot name + index ("" = none). */
    public record Kept(ItemStack stack, int invSlot, String accSlot, int accIndex) {
        public static Kept loose(ItemStack st) { return new Kept(st, -1, "", -1); }
    }

    private final Map<UUID, List<Kept>> kept = new HashMap<>();
    /** Deaths of this tick, re-checked at its end (a cancelled death leaves the player alive). */
    private static final Map<UUID, ServerPlayer> CHECK = new HashMap<>();

    private BinderKeep() {}

    public static BinderKeep get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE, "tcgshop_kept_binders");
    }

    public static void register() {
        PlayerEvents.RESPAWN.register((oldPlayer, newPlayer, alive) -> { if (!alive) restore(newPlayer); });
        PlayerEvents.JOIN.register(p -> p.server.execute(() -> { if (p.isAlive()) restore(p); }));
        ServerEvents.TICK_END.register(BinderKeep::checkCancelled);
    }

    /** End of tick: a player whose death was cancelled after we stashed the binders gets them back now. */
    private static void checkCancelled(MinecraftServer server) {
        if (CHECK.isEmpty()) return;
        List<ServerPlayer> list = new ArrayList<>(CHECK.values());
        CHECK.clear();
        for (ServerPlayer p : list) {
            try {
                if (stillAlive(server, p)) {
                    TcgShop.LOGGER.info("The death of {} was cancelled: binders handed back", p.getName().getString());
                    restore(p);
                }
            } catch (Exception e) {
                TcgShop.LOGGER.error("Could not hand back the binders of {}", p.getName().getString(), e);
            }
        }
    }

    /** Is that entity still the live, alive player (so its death was cancelled)? */
    static boolean stillAlive(MinecraftServer server, ServerPlayer p) {
        return p.isAlive() && !p.isRemoved() && server.getPlayerList().getPlayer(p.getUUID()) == p;
    }

    /** Is this one of the mod's binders? */
    public static boolean isBinder(ItemStack st) {
        return !st.isEmpty() && (st.is(TcgItems.GENERAL_BINDER) || st.is(TcgItems.PERSONAL_BINDER));
    }

    /** Takes every binder out of a container (inventory), leaving the slots empty. */
    public static List<ItemStack> extract(Container c) {
        List<ItemStack> out = new ArrayList<>();
        for (Kept k : extractSlots(c, false)) out.add(k.stack());
        return out;
    }

    /** Same, remembering the slot of each one when {@code remember}. */
    public static List<Kept> extractSlots(Container c, boolean remember) {
        List<Kept> out = new ArrayList<>();
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack st = c.getItem(i);
            if (!isBinder(st)) continue;
            out.add(new Kept(st.copy(), remember ? i : -1, "", -1));
            c.setItem(i, ItemStack.EMPTY);
        }
        return out;
    }

    /** Takes a binder held on a menu's cursor (null if none). */
    public static Kept extractCarried(AbstractContainerMenu menu) {
        if (menu == null || !isBinder(menu.getCarried())) return null;
        Kept k = Kept.loose(menu.getCarried().copy());
        menu.setCarried(ItemStack.EMPTY);
        return k;
    }

    /** Every binder a dying player carries that would drop: inventory, Accessories slots, cursor and crafting grid. */
    static List<Kept> collect(ServerPlayer p) {
        List<Kept> got = new ArrayList<>(extractSlots(p.getInventory(), true));
        for (Item it : new Item[]{TcgItems.GENERAL_BINDER, TcgItems.PERSONAL_BINDER})
            for (AccessoriesCompat.Taken t : AccessoriesCompat.take(p, it)) got.add(new Kept(t.stack(), -1, t.slot(), t.index()));
        Kept c = extractCarried(p.containerMenu);
        if (c != null) got.add(c);
        if (p.containerMenu != p.inventoryMenu && (c = extractCarried(p.inventoryMenu)) != null) got.add(c);
        got.addAll(extractSlots(p.inventoryMenu.getCraftSlots(), false));
        return got;
    }

    /** Called at the start of ServerPlayer.die (before any drop). */
    public static void onDeath(ServerPlayer p) {
        try {
            if (p.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) || p.isSpectator()) return;
            List<Kept> got = collect(p);
            if (got.isEmpty()) return;
            BinderKeep st = get(p.getServer());
            st.kept.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).addAll(got);
            st.setDirty();
            CHECK.put(p.getUUID(), p);
            TcgShop.LOGGER.info("Kept {} binder(s) of {} through death", got.size(), p.getName().getString());
        } catch (Exception e) {
            TcgShop.LOGGER.error("Could not keep the binders of {} on death", p.getName().getString(), e);
        }
    }

    /** Hands the kept binders back (respawn, join or a cancelled death), each to its old slot when it is free. */
    public static void restore(ServerPlayer p) {
        BinderKeep st = get(p.getServer());
        List<Kept> list = st.kept.remove(p.getUUID());
        if (list == null || list.isEmpty()) return;
        st.setDirty();
        putBack(p, p.getInventory(), list);
        p.displayClientMessage(Component.translatable("tcgshop.binder.kept", list.size()).withStyle(ChatFormatting.GOLD), false);
    }

    /**
     * Puts kept binders back: their Accessories slot, else their old inventory slot, else anywhere in the inventory
     * (dropped at the feet only if it is full). {@code entity} may be null (selftest: inventory slots only).
     */
    public static void putBack(ServerPlayer entity, Container inv, List<Kept> list) {
        List<ItemStack> rest = new ArrayList<>();
        for (Kept k : list) {
            ItemStack it = k.stack().copy();
            if (it.isEmpty()) continue;
            if (entity != null && AccessoriesCompat.putBack(entity, k.accSlot(), k.accIndex(), it)) continue;
            if (k.invSlot() >= 0 && k.invSlot() < inv.getContainerSize() && inv.getItem(k.invSlot()).isEmpty()) {
                inv.setItem(k.invSlot(), it);
                continue;
            }
            rest.add(it);
        }
        for (ItemStack it : rest) {
            if (inv instanceof Inventory pi) { pi.placeItemBackInInventory(it); continue; }
            for (int i = 0; i < inv.getContainerSize() && !it.isEmpty(); i++)
                if (inv.getItem(i).isEmpty()) { inv.setItem(i, it); it = ItemStack.EMPTY; }
        }
    }

    /** Binders waiting for that player (for the selftest). */
    public static int pending(MinecraftServer server, UUID id) {
        List<Kept> l = get(server).kept.get(id);
        return l == null ? 0 : l.size();
    }

    /** Selftest helper: keeps stacks for a (fake) player id. */
    public static void keepForTest(MinecraftServer server, UUID id, List<ItemStack> stacks) {
        List<Kept> l = get(server).kept.computeIfAbsent(id, k -> new ArrayList<>());
        for (ItemStack st : stacks) l.add(Kept.loose(st));
    }

    /** Selftest helper: forgets a (fake) player id. */
    public static List<ItemStack> takeForTest(MinecraftServer server, UUID id) {
        List<Kept> l = get(server).kept.remove(id);
        List<ItemStack> out = new ArrayList<>();
        if (l != null) for (Kept k : l) out.add(k.stack());
        return out;
    }

    /** Selftest helper: the NBT round trip keeps where each binder was. */
    public static boolean roundTripForTest(HolderLookup.Provider lookup) {
        BinderKeep a = new BinderKeep();
        UUID id = new UUID(7, 9);
        a.kept.put(id, new ArrayList<>(List.of(new Kept(new ItemStack(TcgItems.GENERAL_BINDER), 4, "", -1),
                new Kept(new ItemStack(TcgItems.PERSONAL_BINDER), -1, "hand", 1))));
        BinderKeep b = load(a.save(new CompoundTag(), lookup), lookup);
        List<Kept> l = b.kept.get(id);
        return l != null && l.size() == 2 && l.get(0).invSlot() == 4 && l.get(0).stack().is(TcgItems.GENERAL_BINDER)
                && "hand".equals(l.get(1).accSlot()) && l.get(1).accIndex() == 1 && l.get(1).stack().is(TcgItems.PERSONAL_BINDER);
    }

    private static BinderKeep load(CompoundTag nbt, HolderLookup.Provider lookup) {
        BinderKeep s = new BinderKeep();
        CompoundTag p = nbt.getCompound("players");
        for (String k : p.getAllKeys()) {
            try {
                List<Kept> l = new ArrayList<>();
                ListTag items = p.getList(k, Tag.TAG_COMPOUND);
                for (int i = 0; i < items.size(); i++) {
                    CompoundTag e = items.getCompound(i);
                    // first 1.1 builds saved the bare stack; now {item, inv, acc, acc_i}
                    ItemStack st = ItemStack.parseOptional(lookup, e.contains("item", Tag.TAG_COMPOUND) ? e.getCompound("item") : e);
                    if (st.isEmpty()) continue;
                    l.add(new Kept(st, e.contains("inv") ? e.getInt("inv") : -1, e.getString("acc"), e.contains("acc_i") ? e.getInt("acc_i") : -1));
                }
                if (!l.isEmpty()) s.kept.put(UUID.fromString(k), l);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookup) {
        CompoundTag p = new CompoundTag();
        kept.forEach((id, l) -> {
            ListTag items = new ListTag();
            for (Kept k : l) {
                if (k.stack().isEmpty()) continue;
                CompoundTag e = new CompoundTag();
                e.put("item", k.stack().save(lookup));
                e.putInt("inv", k.invSlot());
                if (!k.accSlot().isEmpty()) {
                    e.putString("acc", k.accSlot());
                    e.putInt("acc_i", k.accIndex());
                }
                items.add(e);
            }
            p.put(id.toString(), items);
        });
        nbt.put("players", p);
        return nbt;
    }
}
