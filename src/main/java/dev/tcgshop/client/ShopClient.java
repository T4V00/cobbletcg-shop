package dev.tcgshop.client;

import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.client.render.ShopRenderers;
import dev.tcgshop.entity.TcgEntities;
import dev.tcgshop.entity.WholesalerEntity;
import dev.tcgshop.item.ManagerGogglesItem;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.ShopTier;
import dev.tcgshop.shop.ShopZones;
import dev.tcgshop.shop.TcgBlocks;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Shop client: synced zones, Manager Goggles (borders + panel), renderers. */
public final class ShopClient {
    public record ZoneInfo(UUID id, UUID owner, String ownerName, BlockPos core, int tier, long revenue, long balance, int sales,
                           boolean cashier, boolean guard, String cashierName, String guardName, long wages, int[] metrics,
                           java.util.Map<String, Integer> factors, List<ShopZones.Review> reviews, int regulars,
                           NbtCompound ext) {
        public double avgStars() {
            if (reviews.isEmpty()) return 0;
            double s = 0;
            for (ShopZones.Review r : reviews) s += r.stars();
            return s / reviews.size();
        }

        public int metric(int i) { return i < metrics.length ? metrics[i] : 0; }
        public int reputation() { return metric(7); }

        public ShopTier tierInfo() { return ShopTier.values()[Math.max(0, Math.min(ShopTier.values().length - 1, tier))]; }

        public Box box() {
            return ShopZones.boxFor(core, tierInfo());
        }
    }

    public static List<ZoneInfo> zones = new ArrayList<>();
    public static long myCosts, myRevenue;

    private ShopClient() {}

    public static void apply(NbtCompound root) {
        List<ZoneInfo> list = new ArrayList<>();
        NbtList l = root.getList("zones", 10);
        for (int i = 0; i < l.size(); i++) {
            NbtCompound c = l.getCompound(i);
            java.util.Map<String, Integer> f = new java.util.LinkedHashMap<>();
            NbtCompound fc = c.getCompound("factors");
            for (String k : fc.getKeys()) f.put(k, fc.getInt(k));
            List<ShopZones.Review> rv = new ArrayList<>();
            NbtList rl = c.getList("reviews", 10);
            for (int j = 0; j < rl.size(); j++) rv.add(ShopZones.Review.fromNbt(rl.getCompound(j)));
            list.add(new ZoneInfo(c.getUuid("id"), c.getUuid("owner"), c.getString("name"), BlockPos.fromLong(c.getLong("core")), c.getInt("tier"),
                    c.getLong("revenue"), c.getLong("balance"), c.getInt("sales"), c.getBoolean("cashier"), c.getBoolean("guard"),
                    c.getString("cashier_name"), c.getString("guard_name"), c.getLong("wages"), c.getIntArray("metrics"), f, rv, c.getInt("regulars"),
                    c.getCompound("ext")));
        }
        zones = list;
        myCosts = root.getLong("myCosts");
        myRevenue = root.getLong("myRevenue");
    }

    public static ZoneInfo zoneAt(Vec3d p) {
        for (ZoneInfo z : zones) if (z.box().contains(p)) return z;
        return null;
    }

    public static void init() {
        BlockEntityRendererFactories.register(TcgBlocks.DISPLAY_BE, ShopRenderers.DisplayRenderer::new);
        BlockEntityRendererFactories.register(TcgBlocks.BOX_BE, ShopRenderers.BoxRenderer::new);
        BlockEntityRendererFactories.register(TcgBlocks.DECOR_BE, ShopRenderers.DecorRenderer::new);
        EntityRendererRegistry.register(TcgEntities.CUSTOMER, dev.tcgshop.client.render.TcgNpcRenderer::new);
        EntityRendererRegistry.register(TcgEntities.EMPLOYEE, dev.tcgshop.client.render.TcgNpcRenderer::new);
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getTranslucent(), TcgBlocks.DISPLAY_CASE, TcgBlocks.CARD_STAND, TcgBlocks.PEDESTAL,
                TcgBlocks.SHOWCASE, TcgBlocks.HOME_SHOWCASE, TcgBlocks.HOME_PEDESTAL);
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), TcgBlocks.PACK_SHELF, TcgBlocks.CASH_REGISTER, TcgBlocks.GONDOLA,
                TcgBlocks.BOX_PALLET, TcgBlocks.CARD_WALL, TcgBlocks.COUNTER_DISPLAY, TcgBlocks.CARD_LEDGE, TcgBlocks.BOX_SHELF,
                TcgBlocks.CARD_FRAME, TcgBlocks.HOME_FRAME, TcgBlocks.HOME_LEDGE, TcgBlocks.HOME_CARD_WALL, TcgBlocks.POSTER, TcgBlocks.PLAY_TABLE, TcgBlocks.THEFT_DETECTOR, TcgBlocks.ROULETTE);
        BuiltinItemRendererRegistry.INSTANCE.register(TcgBlocks.BOOSTER_BOX_ITEM, (stack, mode, m, vcp, light, overlay) ->
                ShopRenderers.drawBox(m, vcp, light, overlay, stack.getOrDefault(TcgBlocks.BOX_SET, "mew151"),
                        dev.tcgshop.shop.BoosterBox.W, dev.tcgshop.shop.BoosterBox.H, dev.tcgshop.shop.BoosterBox.D));
        EntityRendererRegistry.register(TcgEntities.WHOLESALER, dev.tcgshop.client.render.TcgNpcRenderer::new);
        EntityRendererRegistry.register(TcgEntities.TRADER, dev.tcgshop.client.render.TcgNpcRenderer::new);
        EntityRendererRegistry.register(TcgEntities.BEGGAR, dev.tcgshop.client.render.TcgNpcRenderer::new);
        EntityRendererRegistry.register(TcgEntities.BLACK_MARKET, dev.tcgshop.client.render.TcgNpcRenderer::new);
        EntityRendererRegistry.register(TcgEntities.FURNITURE_SELLER, dev.tcgshop.client.render.TcgNpcRenderer::new);
        // Manager Goggles in the Accessories face slot (the head slot uses the item model's "head" transform)
        dev.tcgshop.client.render.GogglesFeatureRenderer.register();

        // shop borders with the Manager Goggles
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || ctx.consumers() == null || !ManagerGogglesItem.isWearing(mc.player)) return;
            MatrixStack m = ctx.matrixStack();
            Vec3d cam = ctx.camera().getPos();
            VertexConsumer lines = ctx.consumers().getBuffer(RenderLayer.getLines());
            for (ZoneInfo z : zones) {
                if (z.core().getSquaredDistance(cam) > 200 * 200) continue;
                boolean mine = z.owner().equals(mc.player.getUuid());
                float r = mine ? 0.3f : 1f, g = mine ? 1f : 0.35f, b = mine ? 0.45f : 0.3f;
                Box box = z.box().offset(-cam.x, -cam.y, -cam.z);
                WorldRenderer.drawBox(m, lines, box, r, g, b, 0.9f);
                // ring at the player's height so the border is easy to see
                double py = mc.player.getY() - cam.y;
                for (double dy : new double[]{0.05, 1.0, 2.0}) {
                    Box ring = new Box(box.minX, py + dy, box.minZ, box.maxX, py + dy + 0.01, box.maxZ);
                    WorldRenderer.drawBox(m, lines, ring, r, g, b, 0.6f);
                }
            }
        });

        // info panel with the Manager Goggles
        HudRenderCallback.EVENT.register((DrawContext ctx, net.minecraft.client.render.RenderTickCounter tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden || !ManagerGogglesItem.isWearing(mc.player)) return;
            ZoneInfo z = zoneAt(mc.player.getPos());
            int x = 6, y = 6, w = 170;
            List<Text> lines = new ArrayList<>();
            int accent;
            if (z == null) {
                lines.add(Text.translatable("tcgshop.goggles.outside").formatted(Formatting.GRAY));
                accent = 0xFF7A7A90;
            } else {
                boolean mine = z.owner().equals(mc.player.getUuid());
                accent = mine ? 0xFF43C463 : 0xFFE05050;
                lines.add(Text.translatable("tcgshop.goggles.shop_of", z.ownerName()).formatted(Formatting.BOLD));
                lines.add(Text.translatable("tcgshop.goggles.tier", Text.translatable("tcgshop.shop.tier." + z.tierInfo().key),
                        z.tierInfo().size, z.tierInfo().size).formatted(Formatting.GRAY));
                if (mine) {
                    long profit = myRevenue - myCosts;
                    lines.add(Text.translatable("tcgshop.goggles.revenue", PriceService.format(myRevenue)).formatted(Formatting.GREEN));
                    lines.add(Text.translatable("tcgshop.goggles.costs", PriceService.format(myCosts)).formatted(Formatting.RED));
                    lines.add(Text.translatable(profit >= 0 ? "tcgshop.goggles.profit" : "tcgshop.goggles.loss", PriceService.format(Math.abs(profit)))
                            .formatted(profit >= 0 ? Formatting.GOLD : Formatting.DARK_RED, Formatting.BOLD));
                    lines.add(Text.translatable("tcgshop.goggles.balance", PriceService.format(z.balance()), z.sales()).formatted(Formatting.GRAY));
                }
            }
            int h = 8 + lines.size() * 11;
            Ui.roundGradient(ctx, x, y, w, h, 0xC0161925, 0xC00E1019);
            Ui.outline(ctx, x, y, w, h, accent);
            ctx.fill(x + 1, y + 1, x + 3, y + h - 1, accent);
            int ty = y + 5;
            for (Text t : lines) {
                ctx.drawTextWithShadow(mc.textRenderer, t, x + 7, ty, 0xFFFFFFFF);
                ty += 11;
            }
        });
    }
}
