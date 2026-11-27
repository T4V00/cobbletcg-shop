package dev.tcgshop.client.options;

import dev.masterball.api.card.CardInfo;
import dev.masterball.api.client.art.CardArt;
import dev.masterball.api.client.images.ImageDownloader;
import dev.tcgshop.TcgShopConfig;
import dev.tcgshop.client.ClientSettings;
import dev.tcgshop.client.phone.PhoneWallpaper;
import dev.tcgshop.client.screen.Draw;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 1.0: MasterBall Traders Co. options (key O by default): the card art (original / official) with a live preview,
 * 1.0.1: the official art download (status, download / cancel / delete, via the API's ImageDownloader), and
 * the client settings that the phone's Settings app also edits (speech bubbles, patience wheel, pack animation, prices
 * in tooltips, wallpaper). Everything here is client-side and saved at once.
 */
public class ModOptionsScreen extends Screen {
    private static final ResourceLocation[] SAMPLES = {
            ResourceLocation.fromNamespaceAndPath(CardInfo.NS, "baseset/cards/bs004"), ResourceLocation.fromNamespaceAndPath(CardInfo.NS, "baseset/cards/bs088")};
    private static final int ROW = 24, COL_W = 150, GAP = 10, FULL_W = COL_W * 2 + GAP;

    @Nullable
    private final Screen parent;
    private int top, officialY, generalY, bottomY;
    private CardArt.Mode shownMode;
    @Nullable
    private Button artDownload, artCancel, artDelete;
    /** Download size the download button's tooltip shows (it changes when the manifest arrives). */
    private long tooltipSize = Long.MIN_VALUE;

    public ModOptionsScreen(@Nullable Screen parent) {
        super(Component.translatable("tcgshop.options.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ClientSettings cs = ClientSettings.INSTANCE;
        TcgShopConfig gc = TcgShopConfig.INSTANCE;
        int x0 = width / 2 - FULL_W / 2;
        top = Math.max(28, height / 2 - 148);
        int y = top;

        shownMode = CardArt.mode();
        addRenderableWidget(CycleButton.<CardArt.Mode>builder(m -> Component.translatable("tcgshop.options.art." + m.key))
                .withValues(CardArt.Mode.values())
                .withInitialValue(CardArt.mode())
                .withTooltip(m -> Tooltip.create(Component.translatable(m == CardArt.Mode.OFFICIAL
                        ? "tcgshop.options.art.official.tip_v2" : "tcgshop.options.art." + m.key + ".tip")))
                .create(x0, y, FULL_W, 20, Component.translatable("tcgshop.options.art"), (b, v) -> {
                    CardArt.setMode(v);
                    shownMode = v;
                }));
        y += ROW + 14; // room for the art hint line

        // official art download (MasterBall Traders API): header + status line + progress bar, then the buttons
        ImageDownloader.checkManifest();
        officialY = y;
        y += 34; // header, status line and the 10 px progress bar
        artDownload = addRenderableWidget(Button.builder(downloadLabel(), b -> {
                    ImageDownloader.requestDownload(); // the player clicked it: that is the consent
                    refreshArtButtons();
                })
                .bounds(x0, y, COL_W, 20).build()); // tooltip set in refreshArtButtons (it shows the size)
        artCancel = addRenderableWidget(Button.builder(Component.translatable("tcgshop.options.official.cancel"), b -> {
                    ImageDownloader.cancel();
                    refreshArtButtons();
                })
                .bounds(x0 + COL_W + GAP, y, COL_W, 20).build());
        artDelete = addRenderableWidget(Button.builder(Component.translatable("tcgshop.options.official.delete"), b -> confirmDelete())
                .tooltip(Tooltip.create(Component.translatable("tcgshop.options.official.delete.tip", ImageDownloader.FOLDER)))
                .bounds(x0 + COL_W + GAP, y, COL_W, 20).build());
        tooltipSize = Long.MIN_VALUE;
        refreshArtButtons();
        y += ROW;
        generalY = y;

        List<CycleButton<?>> general = new ArrayList<>();
        general.add(onOff("tcgshop.settings.bubbles", cs.bubbles, v -> cs.bubbles = v, null));
        general.add(choice("tcgshop.settings.size", ClientSettings.SIZES.length, cs.bubbleSize,
                i -> Component.translatable("tcgshop.settings.sz." + i), i -> cs.bubbleSize = i));
        general.add(choice("tcgshop.settings.text_color", ClientSettings.TEXT_COLORS.length, cs.bubbleTextColor,
                i -> Component.translatable("tcgshop.settings.tc." + i), i -> cs.bubbleTextColor = i));
        general.add(choice("tcgshop.settings.background", ClientSettings.BG_COLORS.length, cs.bubbleBackground,
                i -> Component.translatable("tcgshop.settings.bg." + i), i -> cs.bubbleBackground = i));
        general.add(onOff("tcgshop.settings.bars", cs.patienceBars, v -> cs.patienceBars = v, null));
        general.add(onOff("tcgshop.settings.pack_anim", gc.packOpeningAnimation, v -> {
            gc.packOpeningAnimation = v;
            TcgShopConfig.save();
        }, null));
        general.add(onOff("tcgshop.settings.tooltip_price", gc.showPriceInTooltip, v -> {
            gc.showPriceInTooltip = v;
            TcgShopConfig.save();
        }, null));
        general.add(CycleButton.<Integer>builder(PhoneWallpaper::name)
                .withValues(range(PhoneWallpaper.KEYS.length))
                .withInitialValue(Math.floorMod(PhoneWallpaper.INSTANCE.wallpaper, PhoneWallpaper.KEYS.length))
                .create(0, 0, COL_W, 20, Component.translatable("tcgshop.options.wallpaper"), (b, v) -> PhoneWallpaper.select(v)));

        y += 12; // "General" header (drawn at generalY)
        for (int i = 0; i < general.size(); i++) {
            CycleButton<?> w = general.get(i);
            w.setPosition(x0 + (i % 2) * (COL_W + GAP), y + (i / 2) * ROW);
            addRenderableWidget(w);
        }
        y += ((general.size() + 1) / 2) * ROW + 6;

        addRenderableWidget(Button.builder(Component.translatable("tcgshop.options.controls"),
                b -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options))).bounds(x0, y, COL_W, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x0 + COL_W + GAP, y, COL_W, 20).build());
        bottomY = y + 26;
    }

    private static Component downloadLabel() {
        String key = ImageDownloader.updateAvailable() ? "tcgshop.options.official.update"
                : ImageDownloader.hasPartialDownload() ? "tcgshop.options.official.resume" : "tcgshop.options.official.download";
        return Component.translatable(key);
    }

    /** Shows Cancel while downloading and Delete otherwise; Download only when there is something to download. */
    private void refreshArtButtons() {
        if (artDownload == null) return;
        boolean running = ImageDownloader.isRunning();
        boolean deleting = ImageDownloader.state() == ImageDownloader.State.DELETING;
        artDownload.setMessage(downloadLabel());
        long size = ImageDownloader.availableSize();
        if (size != tooltipSize) { // the manifest arrived: show the real download size
            tooltipSize = size;
            artDownload.setTooltip(Tooltip.create(Component.translatable("tcgshop.options.official.download.tip",
                    ImageDownloader.sizeText(), ImageDownloader.FOLDER)));
        }
        artDownload.active = !running && !deleting
                && (!ImageDownloader.isInstalled() || ImageDownloader.updateAvailable() || ImageDownloader.hasPartialDownload());
        artCancel.visible = running;
        artDelete.visible = !running;
        artDelete.active = !deleting && ImageDownloader.hasDownloadedFiles();
    }

    private void confirmDelete() {
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) ImageDownloader.deleteInstalled();
            minecraft.setScreen(this);
        }, Component.translatable("tcgshop.options.official.delete.title"),
                Component.translatable("tcgshop.options.official.delete.body", ImageDownloader.FOLDER)));
    }

    @Override
    public void tick() {
        super.tick();
        // the download finished (official art on) or the art was deleted (original art): show the new mode
        if (CardArt.mode() != shownMode) {
            rebuildWidgets();
            return;
        }
        refreshArtButtons();
    }

    private static List<Integer> range(int n) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(i);
        return out;
    }

    private CycleButton<Boolean> onOff(String key, boolean value, java.util.function.Consumer<Boolean> set, @Nullable String tipKey) {
        CycleButton.Builder<Boolean> b = CycleButton.onOffBuilder(value);
        if (tipKey != null) b.withTooltip(v -> Tooltip.create(Component.translatable(tipKey)));
        return b.create(0, 0, COL_W, 20, Component.translatable(key), (w, v) -> {
            set.accept(v);
            ClientSettings.save();
        });
    }

    private CycleButton<Integer> choice(String key, int count, int value, IntFunction<Component> label, IntConsumer set) {
        return CycleButton.<Integer>builder(label::apply)
                .withValues(range(count))
                .withInitialValue(Math.floorMod(value, count))
                .create(0, 0, COL_W, 20, Component.translatable(key), (w, v) -> {
                    set.accept(v);
                    ClientSettings.save();
                });
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int cx = width / 2, x0 = cx - FULL_W / 2;
        ctx.drawCenteredString(font, title, cx, top - 18, 0xFFFFD34D);
        Component hint = Component.translatable(CardArt.original() ? "tcgshop.options.art.hint.original" : "tcgshop.options.art.hint.official")
                .withStyle(ChatFormatting.GRAY);
        ctx.drawCenteredString(font, hint, cx, top + 24, 0xFFAAAAAA);
        officialArt(ctx, x0);
        ctx.drawString(font, Component.translatable("tcgshop.options.general").withStyle(ChatFormatting.BOLD),
                x0, generalY, 0xFFFFE680);
        ctx.drawCenteredString(font, ModOptions.howToOpen().copy().withStyle(ChatFormatting.DARK_GRAY),
                cx, Math.min(height - 12, bottomY), 0xFF777777);
        preview(ctx);
    }

    /** "Official art" header, its status (not installed / downloading x% / installed vX / failed: reason) and progress. */
    private void officialArt(GuiGraphics ctx, int x0) {
        ctx.drawString(font, Component.translatable("tcgshop.options.official").withStyle(ChatFormatting.BOLD),
                x0, officialY, 0xFFFFE680);
        ImageDownloader.State s = ImageDownloader.state();
        int color = switch (s) {
            case FAILED, UNAVAILABLE -> 0xFFFF7070;
            case CHECKING, DOWNLOADING, EXTRACTING, DELETING -> 0xFFFFE070;
            default -> ImageDownloader.updateAvailable() ? 0xFFFFC060 : ImageDownloader.isInstalled() ? 0xFF80E080 : 0xFFAAAAAA;
        };
        List<FormattedCharSequence> status = font.split(ImageDownloader.status(), FULL_W);
        if (!status.isEmpty()) ctx.drawString(font, status.get(0), x0, officialY + 11, color);
        if (s == ImageDownloader.State.DOWNLOADING || s == ImageDownloader.State.EXTRACTING) {
            // shared framed bar with the percentage (status line already drawn above)
            dev.masterball.api.client.images.DownloadBar.draw(ctx, font, null, x0, officialY + 21, FULL_W);
        }
    }

    /** Sample card face on the left and card back on the right, in the current art mode (only if there is room). */
    private void preview(GuiGraphics ctx) {
        int side = (width - FULL_W) / 2;
        if (side < 70) return;
        float ch = Math.min(150, Math.min(height - 70, (side - 16) / Draw.CARD_RATIO));
        float cw = ch * Draw.CARD_RATIO;
        float cy = top + 10;
        ItemStack face = sample();
        if (!face.isEmpty()) {
            float x = side / 2f - cw / 2f;
            Draw.cardFace(ctx, CardArt.texture(CardInfo.id(face)), face, x, cy, cw, ch, 1f);
            if (!CardArt.original() && !Draw.textureExists(CardArt.texture(CardInfo.id(face))))
                ctx.drawWordWrap(font, Component.translatable("tcgshop.options.art.no_images").withStyle(ChatFormatting.RED),
                        Math.round(x), Math.round(cy + ch + 4), Math.max(60, Math.round(cw)), 0xFFFF7070);
        }
        var m = ctx.pose();
        m.pushPose();
        m.translate(width - side / 2f, cy + ch / 2f, 0);
        Draw.cardBack(ctx, cw, ch, 1f, 0, 0);
        m.popPose();
    }

    /** A sample card that exists in this game (the samples, or any card of the first set). */
    private static ItemStack sample() {
        for (ResourceLocation id : SAMPLES) {
            Item it = CardInfo.item(id);
            if (it != null) return new ItemStack(it);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void onClose() {
        ClientSettings.save();
        minecraft.setScreen(parent);
    }
}
