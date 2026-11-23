package dev.tcgshop.client.options;

import dev.masterball.api.card.CardInfo;
import dev.masterball.api.client.art.CardArt;
import dev.tcgshop.TcgShopConfig;
import dev.tcgshop.client.ClientSettings;
import dev.tcgshop.client.phone.PhoneWallpaper;
import dev.tcgshop.client.screen.Draw;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * 1.0: MasterBall Traders Co. options (key O by default): the card art (original / official) with a live preview, and
 * the client settings that the phone's Settings app also edits (speech bubbles, patience wheel, pack animation, prices
 * in tooltips, wallpaper). Everything here is client-side and saved at once.
 */
public class ModOptionsScreen extends Screen {
    private static final Identifier[] SAMPLES = {
            Identifier.of(CardInfo.NS, "baseset/cards/bs004"), Identifier.of(CardInfo.NS, "baseset/cards/bs088")};
    private static final int ROW = 24, COL_W = 150, GAP = 10, FULL_W = COL_W * 2 + GAP;

    @Nullable
    private final Screen parent;
    private int top;

    public ModOptionsScreen(@Nullable Screen parent) {
        super(Text.translatable("tcgshop.options.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ClientSettings cs = ClientSettings.INSTANCE;
        TcgShopConfig gc = TcgShopConfig.INSTANCE;
        int x0 = width / 2 - FULL_W / 2;
        top = Math.max(28, height / 2 - 118);
        int y = top;

        addDrawableChild(CyclingButtonWidget.<CardArt.Mode>builder(m -> Text.translatable("tcgshop.options.art." + m.key))
                .values(CardArt.Mode.values())
                .initially(CardArt.mode())
                .tooltip(m -> Tooltip.of(Text.translatable("tcgshop.options.art." + m.key + ".tip")))
                .build(x0, y, FULL_W, 20, Text.translatable("tcgshop.options.art"), (b, v) -> CardArt.setMode(v)));
        y += ROW + 14; // room for the art hint line

        List<CyclingButtonWidget<?>> general = new ArrayList<>();
        general.add(onOff("tcgshop.settings.bubbles", cs.bubbles, v -> cs.bubbles = v, null));
        general.add(choice("tcgshop.settings.size", ClientSettings.SIZES.length, cs.bubbleSize,
                i -> Text.translatable("tcgshop.settings.sz." + i), i -> cs.bubbleSize = i));
        general.add(choice("tcgshop.settings.text_color", ClientSettings.TEXT_COLORS.length, cs.bubbleTextColor,
                i -> Text.translatable("tcgshop.settings.tc." + i), i -> cs.bubbleTextColor = i));
        general.add(choice("tcgshop.settings.background", ClientSettings.BG_COLORS.length, cs.bubbleBackground,
                i -> Text.translatable("tcgshop.settings.bg." + i), i -> cs.bubbleBackground = i));
        general.add(onOff("tcgshop.settings.bars", cs.patienceBars, v -> cs.patienceBars = v, null));
        general.add(onOff("tcgshop.settings.pack_anim", gc.packOpeningAnimation, v -> {
            gc.packOpeningAnimation = v;
            TcgShopConfig.save();
        }, null));
        general.add(onOff("tcgshop.settings.tooltip_price", gc.showPriceInTooltip, v -> {
            gc.showPriceInTooltip = v;
            TcgShopConfig.save();
        }, null));
        general.add(CyclingButtonWidget.<Integer>builder(PhoneWallpaper::name)
                .values(range(PhoneWallpaper.KEYS.length))
                .initially(Math.floorMod(PhoneWallpaper.INSTANCE.wallpaper, PhoneWallpaper.KEYS.length))
                .build(0, 0, COL_W, 20, Text.translatable("tcgshop.options.wallpaper"), (b, v) -> PhoneWallpaper.select(v)));

        y += 12; // "General" header
        for (int i = 0; i < general.size(); i++) {
            CyclingButtonWidget<?> w = general.get(i);
            w.setPosition(x0 + (i % 2) * (COL_W + GAP), y + (i / 2) * ROW);
            addDrawableChild(w);
        }
        y += ((general.size() + 1) / 2) * ROW + 6;

        addDrawableChild(ButtonWidget.builder(Text.translatable("tcgshop.options.controls"),
                b -> client.setScreen(new KeybindsScreen(this, client.options))).dimensions(x0, y, COL_W, 20).build());
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close()).dimensions(x0 + COL_W + GAP, y, COL_W, 20).build());
    }

    private static List<Integer> range(int n) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(i);
        return out;
    }

    private CyclingButtonWidget<Boolean> onOff(String key, boolean value, java.util.function.Consumer<Boolean> set, @Nullable String tipKey) {
        CyclingButtonWidget.Builder<Boolean> b = CyclingButtonWidget.onOffBuilder(value);
        if (tipKey != null) b.tooltip(v -> Tooltip.of(Text.translatable(tipKey)));
        return b.build(0, 0, COL_W, 20, Text.translatable(key), (w, v) -> {
            set.accept(v);
            ClientSettings.save();
        });
    }

    private CyclingButtonWidget<Integer> choice(String key, int count, int value, IntFunction<Text> label, IntConsumer set) {
        return CyclingButtonWidget.<Integer>builder(label::apply)
                .values(range(count))
                .initially(Math.floorMod(value, count))
                .build(0, 0, COL_W, 20, Text.translatable(key), (w, v) -> {
                    set.accept(v);
                    ClientSettings.save();
                });
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int cx = width / 2, x0 = cx - FULL_W / 2;
        ctx.drawCenteredTextWithShadow(textRenderer, title, cx, top - 18, 0xFFFFD34D);
        Text hint = Text.translatable(CardArt.original() ? "tcgshop.options.art.hint.original" : "tcgshop.options.art.hint.official")
                .formatted(Formatting.GRAY);
        ctx.drawCenteredTextWithShadow(textRenderer, hint, cx, top + 24, 0xFFAAAAAA);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.options.general").formatted(Formatting.BOLD),
                x0, top + ROW + 14, 0xFFFFE680);
        ctx.drawCenteredTextWithShadow(textRenderer, ModOptions.howToOpen().copy().formatted(Formatting.DARK_GRAY),
                cx, Math.min(height - 12, top + 7 * ROW + 40), 0xFF777777);
        preview(ctx);
    }

    /** Sample card face on the left and card back on the right, in the current art mode (only if there is room). */
    private void preview(DrawContext ctx) {
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
                ctx.drawTextWrapped(textRenderer, Text.translatable("tcgshop.options.art.no_images").formatted(Formatting.RED),
                        Math.round(x), Math.round(cy + ch + 4), Math.max(60, Math.round(cw)), 0xFFFF7070);
        }
        var m = ctx.getMatrices();
        m.push();
        m.translate(width - side / 2f, cy + ch / 2f, 0);
        Draw.cardBack(ctx, cw, ch, 1f, 0, 0);
        m.pop();
    }

    /** A sample card that exists in this game (the samples, or any card of the first set). */
    private static ItemStack sample() {
        for (Identifier id : SAMPLES) {
            Item it = CardInfo.item(id);
            if (it != null) return new ItemStack(it);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void close() {
        ClientSettings.save();
        client.setScreen(parent);
    }
}
