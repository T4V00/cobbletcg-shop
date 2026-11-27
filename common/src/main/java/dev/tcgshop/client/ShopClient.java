package dev.tcgshop.client;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.ClientPlatform;
import dev.masterball.api.platform.event.ClientEvents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.client.render.ShopRenderers;
import dev.tcgshop.entity.TcgEntities;
import dev.tcgshop.entity.WholesalerEntity;
import dev.tcgshop.item.ManagerGogglesItem;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.ShopTier;
import dev.tcgshop.shop.ShopZones;
import dev.tcgshop.shop.TcgBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Shop client: synced zones, Manager Goggles (borders + panel), renderers. */
public final class ShopClient {
    public record ZoneInfo(UUID id, UUID owner, String ownerName, BlockPos core, int tier, long revenue, long balance, int sales,
                           boolean cashier, boolean guard, String cashierName, String guardName, long wages, int[] metrics,
                           java.util.Map<String, Integer> factors, List<ShopZones.Review> reviews, int regulars,
                           CompoundTag ext, int size, boolean closed, boolean dormant) {
        // 1.1: closed = the owner closed it (everyone sees it); dormant = core removed (only the owner receives those)
        public double avgStars() {
            if (reviews.isEmpty()) return 0;
            double s = 0;
            for (ShopZones.Review r : reviews) s += r.stars();
            return s / reviews.size();
        }

        public int metric(int i) { return i < metrics.length ? metrics[i] : 0; }
        public int reputation() { return metric(7); }

        public ShopTier tierInfo() { return ShopTier.byRank(tier); }

        /** 1.0: the zone's real side ("size" from the server; pre-1.0 shops keep their old, bigger side). */
        public int size() { return size > 0 ? size : tierInfo().size; }

        public AABB box() {
            return ShopZones.boxFor(core, size(), tierInfo().up);
        }
    }

    public static List<ZoneInfo> zones = new ArrayList<>();

    /** 1.1: one stat bar of the Manager Goggles panel. */
    private record Bar(Component label, float fill, String value, int color) {}

    /** Green when good, yellow in the middle, red when bad (good = 1). */
    private static int grade(float good) {
        return good >= 0.66f ? 0xFF6BE07B : good >= 0.33f ? 0xFFFFD34D : 0xFFFF6B6B;
    }

    /**
     * 1.1: the goggles' bars. Everyone sees the reputation; the owner also sees the theft risk (share of arriving customers
     * who are thieves: the lower the reputation, the higher), cleanliness (bad smell), decoration and staff.
     */
    private static List<Bar> bars(ZoneInfo z, boolean mine) {
        List<Bar> out = new ArrayList<>();
        int rep = Math.max(0, Math.min(100, z.reputation()));
        out.add(new Bar(Component.translatable("tcgshop.goggles.bar.reputation"), rep / 100f, rep + "/100", grade(rep / 100f)));
        if (!mine) return out;
        CompoundTag ex = z.ext();
        if (ex.contains("theft")) {
            float risk = ex.getInt("theft") / 1000f;
            float fill = Math.min(1f, risk / 0.25f); // a full bar = 1 thief in 4 customers
            out.add(new Bar(Component.translatable("tcgshop.goggles.bar.theft"), fill, String.format(java.util.Locale.ROOT, "%.1f%%", risk * 100), grade(1 - fill)));
        }
        // 1.1 cleanliness update: the server's score (smell + grime + droppings); older servers: only the smell
        int clean = ex.contains("clean") ? Math.max(0, Math.min(100, ex.getInt("clean"))) : Math.max(0, 100 - z.metric(12));
        out.add(new Bar(Component.translatable("tcgshop.goggles.bar.clean"), clean / 100f, clean + "%", grade(clean / 100f)));
        if (ex.contains("decor_pct")) { // 1.1: the decoration liking (DecorScore), hard to raise
            float dec = Math.max(0, Math.min(100, ex.getInt("decor_pct"))) / 100f;
            out.add(new Bar(Component.translatable("tcgshop.goggles.bar.decor"), dec, Math.round(dec * 100) + "%", grade(dec)));
        } else if (ex.contains("decor_f")) {
            float dec = (Math.max(-10, Math.min(15, ex.getInt("decor_f"))) + 10) / 25f;
            out.add(new Bar(Component.translatable("tcgshop.goggles.bar.decor"), dec, Math.round(dec * 100) + "%", grade(dec)));
        }
        int guards = Math.max(z.guard() ? 1 : 0, ex.getInt("guards")), gmax = ex.contains("guards_max") ? ex.getInt("guards_max") : 3;
        int staff = (z.cashier() ? 1 : 0) + (ex.getBoolean("cashier2") ? 1 : 0) + guards, smax = 2 + gmax;
        out.add(new Bar(Component.translatable("tcgshop.goggles.bar.staff"), staff / (float) smax, staff + "/" + smax, grade(staff / (float) smax)));
        return out;
    }
    public static long myCosts, myRevenue;

    private ShopClient() {}

    public static void apply(CompoundTag root) {
        List<ZoneInfo> list = new ArrayList<>();
        ListTag l = root.getList("zones", 10);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            java.util.Map<String, Integer> f = new java.util.LinkedHashMap<>();
            CompoundTag fc = c.getCompound("factors");
            for (String k : fc.getAllKeys()) f.put(k, fc.getInt(k));
            List<ShopZones.Review> rv = new ArrayList<>();
            ListTag rl = c.getList("reviews", 10);
            for (int j = 0; j < rl.size(); j++) rv.add(ShopZones.Review.fromNbt(rl.getCompound(j)));
            list.add(new ZoneInfo(c.getUUID("id"), c.getUUID("owner"), c.getString("name"), BlockPos.of(c.getLong("core")), c.getInt("tier"),
                    c.getLong("revenue"), c.getLong("balance"), c.getInt("sales"), c.getBoolean("cashier"), c.getBoolean("guard"),
                    c.getString("cashier_name"), c.getString("guard_name"), c.getLong("wages"), c.getIntArray("metrics"), f, rv, c.getInt("regulars"),
                    c.getCompound("ext"), c.getInt("size"), c.getBoolean("closed"), c.getBoolean("dormant")));
        }
        zones = list;
        myCosts = root.getLong("myCosts");
        myRevenue = root.getLong("myRevenue");
    }

    public static ZoneInfo zoneAt(Vec3 p) {
        for (ZoneInfo z : zones) if (!z.dormant() && z.box().contains(p)) return z; // 1.1: a dormant shop has no area
        return null;
    }

    public static void init() {
        ShopZones.CLIENT_OWNS = (player, pos) -> { // 1.1: lets common code (the mop) mirror the server on the client
            ZoneInfo z = zoneAt(pos.getCenter());
            return z != null && z.owner().equals(player.getUUID());
        };
        ClientPlatform.INSTANCE.blockEntityRenderer(TcgBlocks.DISPLAY_BE, ShopRenderers.DisplayRenderer::new);
        ClientPlatform.INSTANCE.blockEntityRenderer(TcgBlocks.BOX_BE, ShopRenderers.BoxRenderer::new);
        ClientPlatform.INSTANCE.blockEntityRenderer(TcgBlocks.DECOR_BE, ShopRenderers.DecorRenderer::new);
        ClientPlatform.INSTANCE.entityRenderer(TcgEntities.CUSTOMER, dev.tcgshop.client.render.TcgNpcRenderer::new);
        ClientPlatform.INSTANCE.entityRenderer(TcgEntities.EMPLOYEE, dev.tcgshop.client.render.TcgNpcRenderer::new);
        ClientPlatform.INSTANCE.blockRenderType(RenderType.translucent(), TcgBlocks.DISPLAY_CASE, TcgBlocks.CARD_STAND, TcgBlocks.PEDESTAL,
                TcgBlocks.SHOWCASE, TcgBlocks.HOME_SHOWCASE, TcgBlocks.HOME_PEDESTAL);
        ClientPlatform.INSTANCE.blockRenderType(RenderType.cutout(), dev.tcgshop.shop.CleanBlocks.POOP, dev.tcgshop.shop.CleanBlocks.GRAFFITI, dev.tcgshop.shop.CleanBlocks.WALL_FRESHENER,
                dev.tcgshop.shop.CleanBlocks.WIDE_DOOR); // 1.1 cleanliness update
        for (net.minecraft.world.item.Item it : dev.tcgshop.shop.CleanBlocks.DECOR)
            if (it instanceof net.minecraft.world.item.BlockItem bi) ClientPlatform.INSTANCE.blockRenderType(RenderType.cutout(), bi.getBlock());
        ClientPlatform.INSTANCE.blockRenderType(RenderType.cutout(), TcgBlocks.PACK_SHELF, TcgBlocks.CASH_REGISTER, TcgBlocks.GONDOLA,
                TcgBlocks.BOX_PALLET, TcgBlocks.CARD_WALL, TcgBlocks.COUNTER_DISPLAY, TcgBlocks.CARD_LEDGE, TcgBlocks.BOX_SHELF,
                TcgBlocks.CARD_FRAME, TcgBlocks.HOME_FRAME, TcgBlocks.HOME_LEDGE, TcgBlocks.HOME_CARD_WALL, TcgBlocks.POSTER, TcgBlocks.PLAY_TABLE, TcgBlocks.THEFT_DETECTOR, TcgBlocks.ROULETTE);
        ClientPlatform.INSTANCE.itemRenderer(TcgBlocks.BOOSTER_BOX_ITEM, (stack, mode, m, vcp, light, overlay) ->
                ShopRenderers.drawBox(m, vcp, light, overlay, stack.getOrDefault(TcgBlocks.BOX_SET, "mew151"),
                        dev.tcgshop.shop.BoosterBox.W, dev.tcgshop.shop.BoosterBox.H, dev.tcgshop.shop.BoosterBox.D));
        ClientPlatform.INSTANCE.entityRenderer(TcgEntities.WHOLESALER, dev.tcgshop.client.render.TcgNpcRenderer::new);
        ClientPlatform.INSTANCE.entityRenderer(TcgEntities.TRADER, dev.tcgshop.client.render.TcgNpcRenderer::new);
        ClientPlatform.INSTANCE.entityRenderer(TcgEntities.BEGGAR, dev.tcgshop.client.render.TcgNpcRenderer::new);
        ClientPlatform.INSTANCE.entityRenderer(TcgEntities.BLACK_MARKET, dev.tcgshop.client.render.TcgNpcRenderer::new);
        ClientPlatform.INSTANCE.entityRenderer(TcgEntities.FURNITURE_SELLER, dev.tcgshop.client.render.TcgNpcRenderer::new);
        // Manager Goggles in the Accessories face slot (the head slot uses the item model's "head" transform)
        dev.tcgshop.client.render.GogglesFeatureRenderer.register();

        // shop borders with the Manager Goggles
        ClientEvents.WORLD_AFTER_TRANSLUCENT.register(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || ctx.consumers() == null || !ManagerGogglesItem.isWearing(mc.player)) return;
            PoseStack m = ctx.poseStack();
            Vec3 cam = ctx.camera().getPosition();
            VertexConsumer lines = ctx.consumers().getBuffer(RenderType.lines());
            for (ZoneInfo z : zones) {
                if (z.dormant() || z.core().distToCenterSqr(cam) > 200 * 200) continue;
                boolean mine = z.owner().equals(mc.player.getUUID());
                float r = mine ? 0.3f : 1f, g = mine ? 1f : 0.35f, b = mine ? 0.45f : 0.3f;
                if (z.closed()) { r = 0.95f; g = 0.65f; b = 0.2f; } // 1.1: closed shops show an amber border
                AABB box = z.box().move(-cam.x, -cam.y, -cam.z);
                LevelRenderer.renderLineBox(m, lines, box, r, g, b, 0.9f);
                // ring at the player's height so the border is easy to see
                double py = mc.player.getY() - cam.y;
                for (double dy : new double[]{0.05, 1.0, 2.0}) {
                    AABB ring = new AABB(box.minX, py + dy, box.minZ, box.maxX, py + dy + 0.01, box.maxZ);
                    LevelRenderer.renderLineBox(m, lines, ring, r, g, b, 0.6f);
                }
            }
        });

        // info panel with the Manager Goggles
        ClientEvents.HUD.register((GuiGraphics ctx, net.minecraft.client.DeltaTracker tick) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui || !ManagerGogglesItem.isWearing(mc.player)) return;
            ZoneInfo z = zoneAt(mc.player.position());
            int x = 6, y = 6, w = 180;
            List<Component> lines = new ArrayList<>();
            List<Bar> bars = new ArrayList<>(); // 1.1: shop stats as bars
            int accent;
            if (z == null) {
                lines.add(Component.translatable("tcgshop.goggles.outside").withStyle(ChatFormatting.GRAY));
                accent = 0xFF7A7A90;
            } else {
                boolean mine = z.owner().equals(mc.player.getUUID());
                accent = z.closed() ? 0xFFE0A030 : mine ? 0xFF43C463 : 0xFFE05050;
                lines.add(Component.translatable("tcgshop.goggles.shop_of", z.ownerName()).withStyle(ChatFormatting.BOLD));
                if (z.closed()) lines.add(Component.translatable("tcgshop.goggles.closed").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)); // 1.1
                lines.add(Component.translatable("tcgshop.goggles.tier", Component.translatable("tcgshop.shop.tier." + z.tierInfo().key),
                        z.size(), z.size()).withStyle(ChatFormatting.GRAY));
                if (mine) {
                    long profit = myRevenue - myCosts;
                    lines.add(Component.translatable("tcgshop.goggles.revenue", PriceService.format(myRevenue)).withStyle(ChatFormatting.GREEN));
                    lines.add(Component.translatable("tcgshop.goggles.costs", PriceService.format(myCosts)).withStyle(ChatFormatting.RED));
                    lines.add(Component.translatable(profit >= 0 ? "tcgshop.goggles.profit" : "tcgshop.goggles.loss", PriceService.format(Math.abs(profit)))
                            .withStyle(profit >= 0 ? ChatFormatting.GOLD : ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                    lines.add(Component.translatable("tcgshop.goggles.balance", PriceService.format(z.balance()), z.sales()).withStyle(ChatFormatting.GRAY));
                }
                bars.addAll(bars(z, mine));
            }
            int h = 8 + lines.size() * 11 + (bars.isEmpty() ? 0 : 4 + bars.size() * 12);
            Ui.roundGradient(ctx, x, y, w, h, 0xC0161925, 0xC00E1019);
            Ui.outline(ctx, x, y, w, h, accent);
            ctx.fill(x + 1, y + 1, x + 3, y + h - 1, accent);
            int ty = y + 5;
            for (Component t : lines) {
                ctx.drawString(mc.font, t, x + 7, ty, 0xFFFFFFFF);
                ty += 11;
            }
            if (!bars.isEmpty()) {
                ty += 2;
                ctx.fill(x + 7, ty, x + w - 6, ty + 1, 0x33FFFFFF);
                ty += 4;
                int labelW = 0;
                for (Bar b : bars) labelW = Math.max(labelW, mc.font.width(b.label()));
                int bx = x + 9 + labelW, bw = w - (bx - x) - 38;
                for (Bar b : bars) {
                    ctx.drawString(mc.font, b.label(), x + 7, ty, 0xFFC8CCDA, false);
                    Ui.progress(ctx, bx, ty + 2, bw, 5, Math.max(0, Math.min(1, b.fill())), b.color());
                    ctx.drawString(mc.font, b.value(), x + w - 6 - mc.font.width(b.value()), ty, b.color(), false);
                    ty += 12;
                }
            }
        });
    }
}
