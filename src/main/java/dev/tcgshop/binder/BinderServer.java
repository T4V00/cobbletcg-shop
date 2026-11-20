package dev.tcgshop.binder;

import dev.tcgshop.advancement.CardAdvancements;
import dev.tcgshop.card.CardInfo;
import dev.tcgshop.collection.BinderData;
import dev.tcgshop.collection.BinderRegistry;
import dev.tcgshop.collection.BinderTag;
import dev.tcgshop.collection.CardCollection;
import dev.tcgshop.collection.CollectionService;
import dev.tcgshop.collection.Titles;
import dev.tcgshop.item.TcgItems;
import dev.tcgshop.network.BinderActionPayload;
import dev.tcgshop.network.OpenBinderPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Servidor: abrir binders y aplicar las acciones que llegan desde sus pantallas. */
public final class BinderServer {
    /** Prefijo de los huecos de "carta deseada" en el Personal Binder (no consumen cartas). */
    public static final String WISH = "wish:";

    private BinderServer() {}

    public static void openGeneral(ServerPlayerEntity player, @Nullable ItemStack stack) {
        if (CollectionService.openGeneral(player, stack)) send(player, new OpenBinderPayload(0, 0, List.of()));
    }

    public static void openPersonal(ServerPlayerEntity player, Hand hand) {
        ItemStack st = player.getStackInHand(hand);
        if (!st.isOf(TcgItems.PERSONAL_BINDER)) return;
        BinderTag tag = st.get(TcgItems.BINDER_TAG);
        if (tag == null) {
            st.set(TcgItems.BINDER_TAG, new BinderTag(UUID.randomUUID(), player.getUuid(), player.getName().getString()));
            player.sendMessage(Text.translatable("tcgshop.binder.bound").formatted(Formatting.GREEN), true);
        } else if (!tag.owner().equals(player.getUuid())) {
            player.sendMessage(Text.translatable("tcgshop.binder.foreign", tag.ownerName()).formatted(Formatting.RED), true);
            return;
        }
        PersonalBinderData d = st.getOrDefault(TcgItems.PERSONAL_BINDER_DATA, PersonalBinderData.DEFAULT);
        List<String> ids = new ArrayList<>();
        for (String s : d.slots()) if (!s.isEmpty() && !s.startsWith(WISH)) ids.add(s);
        CardAdvancements.credit(player, CollectionService.playerData(player), null, ids);
        CardAdvancements.grant(player, "first_binder");
        CollectionService.sync(player); // el cajón muestra las cartas de tu Binder General
        List<String> unlocked = new ArrayList<>();
        for (BinderStyles.Style s : BinderStyles.ALL) {
            if (s.advancement() == null || CardAdvancements.isDone(player, s.advancement())) unlocked.add(s.kind() + "=" + s.id());
        }
        send(player, new OpenBinderPayload(1, hand.ordinal(), unlocked));
    }

    public static void handle(BinderActionPayload p, ServerPlayerEntity player) {
        switch (p.action()) {
            case BinderActionPayload.OPEN_GENERAL -> openGeneral(player, null);
            case BinderActionPayload.WITHDRAW -> CollectionService.withdraw(player, p.arg(), p.amount());
            case BinderActionPayload.DEPOSIT_ALL -> CollectionService.depositAll(player);
            case BinderActionPayload.MARK_SEEN -> CollectionService.markSeen(player, Arrays.asList(p.arg().split(",")));
            case BinderActionPayload.CLEAR_FRESH -> CollectionService.clearFresh(player, p.arg());
            case BinderActionPayload.WISH_TOGGLE -> CollectionService.toggleWish(player, p.arg());
            case BinderActionPayload.TITLE_SET -> {
                Titles.select(player, p.arg());
                CollectionService.sync(player);
            }
            case BinderActionPayload.PERSONAL_PLACE, BinderActionPayload.PERSONAL_REMOVE,
                 BinderActionPayload.PERSONAL_ADD_PAGE, BinderActionPayload.PERSONAL_STYLE -> personal(p, player);
            default -> {}
        }
    }

    /** Acciones del Personal Binder. Las cartas se mueven entre tu Binder General y el personal. */
    private static void personal(BinderActionPayload p, ServerPlayerEntity player) {
        Hand hand = player.getMainHandStack().isOf(TcgItems.PERSONAL_BINDER) ? Hand.MAIN_HAND
                : player.getOffHandStack().isOf(TcgItems.PERSONAL_BINDER) ? Hand.OFF_HAND : null;
        if (hand == null) return;
        ItemStack st = player.getStackInHand(hand);
        BinderTag tag = st.get(TcgItems.BINDER_TAG);
        if (tag == null || !tag.owner().equals(player.getUuid())) return;
        PersonalBinderData d = st.getOrDefault(TcgItems.PERSONAL_BINDER_DATA, PersonalBinderData.DEFAULT);
        BinderData general = CollectionService.ownedBinder(player);

        switch (p.action()) {
            case BinderActionPayload.PERSONAL_PLACE -> {
                int slot = p.amount();
                if (slot < 0 || slot >= d.slots().size()) return;
                String arg = p.arg();
                boolean wish = arg.startsWith(WISH);
                Identifier id = Identifier.tryParse(wish ? arg.substring(WISH.length()) : arg);
                if (id == null || !CardInfo.isCard(id)) return;
                if (!wish) {
                    if (general == null) {
                        player.sendMessage(Text.translatable("tcgshop.binder.need").formatted(Formatting.RED), true);
                        return;
                    }
                    if (general.remove(arg, 1) == 0) return; // no la tienes en el Binder General
                }
                String prev = d.slots().get(slot);
                if (!prev.isEmpty() && !prev.startsWith(WISH) && general != null) general.add(prev, 1);
                d = d.withSlot(slot, arg);
            }
            case BinderActionPayload.PERSONAL_REMOVE -> {
                int slot = p.amount();
                if (slot < 0 || slot >= d.slots().size() || d.slots().get(slot).isEmpty()) return;
                String prev = d.slots().get(slot);
                if (!prev.startsWith(WISH)) {
                    if (general == null) {
                        player.sendMessage(Text.translatable("tcgshop.binder.need").formatted(Formatting.RED), true);
                        return;
                    }
                    general.add(prev, 1);
                }
                d = d.withSlot(slot, "");
            }
            case BinderActionPayload.PERSONAL_ADD_PAGE -> {
                if (d.pages() >= PersonalBinderData.MAX_PAGES) return;
                d = d.withPages(d.pages() + 2);
            }
            case BinderActionPayload.PERSONAL_STYLE -> {
                String[] kv = p.arg().split("=", 2);
                if (kv.length != 2) return;
                BinderStyles.Style s = BinderStyles.find(kv[0], kv[1]);
                if (s == null) return;
                if (s.advancement() != null && !CardAdvancements.isDone(player, s.advancement())) return;
                d = d.withStyle(kv[0], kv[1]);
                CardAdvancements.grant(player, "customize_binder");
            }
            default -> { return; }
        }
        st.set(TcgItems.PERSONAL_BINDER_DATA, d);
        BinderRegistry.get(player.getServer()).markDirty();
        CollectionService.sync(player);
    }

    private static void send(ServerPlayerEntity player, net.minecraft.network.packet.CustomPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.getId())) ServerPlayNetworking.send(player, payload);
    }
}
