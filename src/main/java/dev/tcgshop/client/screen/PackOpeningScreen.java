package dev.tcgshop.client.screen;

import dev.tcgshop.TcgSounds;
import dev.tcgshop.card.CardInfo;
import dev.tcgshop.card.Rarity;
import dev.tcgshop.network.GroupCutPayload;
import dev.tcgshop.network.GroupReadyPayload;
import dev.tcgshop.network.GroupRevealPayload;
import dev.tcgshop.network.OpenGroupPayload;
import dev.tcgshop.pricing.PriceService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static dev.tcgshop.client.screen.Draw.*;

/**
 * Sesión de apertura de sobres estilo TCG Pocket, por grupos (1, 5, 10, 15 o 20 sobres por rasgada).
 *
 * Por grupo:
 *  PREPARING  el servidor prepara los sobres del grupo (cobbletcg sortea normal / semi-dios / dios y su contenido).
 *  PACKS      los sobres aparecen en rejilla. Cada trazo que cruza un sobre de lado a lado lo corta:
 *             · por la franja sellada de arriba (sobre la línea punteada) => apertura limpia;
 *             · si el trazo baja hasta las cartas => esas cartas quedan dañadas.
 *             Los sobres con una carta de alta rareza brillan y suenan distinto al rasgarse.
 *  REVEAL     las cartas se revelan una a una, del primer sobre al último (la más rara de cada sobre al final).
 *  SUMMARY    resumen del grupo. Los sobres mal cortados: sus cartas se parten por donde pasó el corte y valen 0.
 * Al final: SESSION, resumen de toda la sesión.
 */
public class PackOpeningScreen extends Screen {
    private static final float TEAR_Y = 0.07f, CARD_TOP = 0.15f, CARD_BOTTOM = 0.97f, CARD_X0 = 0.06f, CARD_X1 = 0.94f;
    private static final int BINS = 20;
    private static final int[] TIER_COLORS = {0xFFFFFFFF, 0xFFFFFFFF, 0xFFC77DFF, 0xFFFFD34D};
    private static final double TEAR_TIME = 0.8;

    private enum Phase { PREPARING, PACKS, REVEAL, SUMMARY, SESSION }

    private final class PackState {
        final Identifier requested;
        Identifier ripped;
        int tier, best;
        boolean failed;
        final float[] stroke = new float[BINS];
        float[] packCut, cardCut;
        boolean cut, damaged;
        double cutTime = -1;
        final List<CardView> cards = new ArrayList<>();
        long value;
        ItemStack stack = ItemStack.EMPTY;
        float x, y, w, h; // geometría

        PackState(Identifier requested) {
            this.requested = requested;
            Arrays.fill(stroke, Float.NaN);
            Item item = CardInfo.item(requested);
            if (item != null) stack = new ItemStack(item);
            tier = Math.max(0, CardInfo.packTier(stack));
        }

        Identifier sealedTex() { return CardInfo.sealedTexture(ripped != null ? ripped : requested); }
    }

    private record SessionEntry(List<CardView> cards, boolean damaged, long value) {}

    private final List<List<Identifier>> groups = new ArrayList<>();
    private int gi;
    private final List<PackState> packs = new ArrayList<>();
    private final List<SessionEntry> session = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final Random rng = Random.create();

    private Phase phase = Phase.PREPARING;
    private double phaseStart, lastFrame, allCutTime = -1;
    private boolean revealReceived, toBinder;

    // trazo
    private boolean stroking;
    private double lastMx = Double.NaN, lastMy;
    private double strokeFailTime = -10;
    private double glovesHintTime = -10;

    // revelado
    private int revPack, revCard;
    private double dragX;
    private boolean draggingCard;
    private double pressX, pressY;
    private double dismissStart = -1;
    private int dismissDir = 1;

    // efectos
    private double shake, flash;
    private int flashColor = 0xFFFFFFFF;

    // resumen
    private double summaryScroll, destroyStart = -1, lastTearSound = -1;
    private int destroySounds;
    private CardView hoveredSummary;
    private ButtonWidget nextButton, skipButton;

    public PackOpeningScreen(List<Identifier> queue, int perTear) {
        super(Text.translatable("tcgshop.open.summary"));
        int g = Math.max(1, Math.min(PackOpenerLimits.MAX, perTear));
        for (int i = 0; i < queue.size(); i += g) groups.add(new ArrayList<>(queue.subList(i, Math.min(queue.size(), i + g))));
    }

    /** Límite compartido con el servidor. */
    private static final class PackOpenerLimits { static final int MAX = 100; }

    // ------------------------------------------------------------------ ciclo de vida

    @Override
    protected void init() {
        if (lastFrame == 0) {
            lastFrame = now();
            startGroup();
        }
        skipButton = addDrawableChild(ButtonWidget.builder(Text.translatable("tcgshop.open.skip"), b -> skipReveal())
                .dimensions(width - 70, 8, 62, 18).build());
        nextButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> next())
                .dimensions(width / 2 - 80, height - 28, 160, 20).build());
        layoutPacks();
        updateButtons();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void updateButtons() {
        if (nextButton == null) return;
        nextButton.visible = phase == Phase.SUMMARY || phase == Phase.SESSION;
        skipButton.visible = phase == Phase.REVEAL;
        if (phase == Phase.SESSION) nextButton.setMessage(Text.translatable("tcgshop.open.done"));
        else {
            boolean last = gi >= groups.size() - 1;
            int remaining = 0;
            for (int i = gi + 1; i < groups.size(); i++) remaining += groups.get(i).size();
            nextButton.setMessage(!last ? Text.translatable("tcgshop.open.next_group", remaining)
                    : groups.size() > 1 || session.size() + packs.size() > 1 ? Text.translatable("tcgshop.open.session")
                    : Text.translatable("tcgshop.open.done"));
        }
    }

    private void setPhase(Phase p) {
        phase = p;
        phaseStart = now();
        updateButtons();
    }

    private void startGroup() {
        packs.clear();
        for (Identifier id : groups.get(gi)) packs.add(new PackState(id));
        revealReceived = false;
        allCutTime = -1;
        revPack = revCard = 0;
        dragX = 0;
        dismissStart = -1;
        destroyStart = -1;
        destroySounds = 0;
        summaryScroll = 0;
        layoutPacks();
        setPhase(Phase.PREPARING);
        ClientPlayNetworking.send(new OpenGroupPayload(groups.get(gi)));
    }

    public void onGroupReady(GroupReadyPayload p) {
        if (phase != Phase.PREPARING) return;
        for (int i = 0; i < packs.size(); i++) {
            PackState s = packs.get(i);
            if (i >= p.ripped().size() || p.best().get(i) < 0) {
                s.failed = true;
                s.cut = true;
                continue;
            }
            s.ripped = p.ripped().get(i);
            s.tier = MathHelper.clamp(p.tiers().get(i), 1, 3);
            s.best = p.best().get(i);
        }
        if (packs.stream().allMatch(s -> s.failed)) {
            if (client != null && client.player != null)
                client.player.sendMessage(Text.translatable("tcgshop.open.failed").formatted(Formatting.RED), true);
            next();
            return;
        }
        setPhase(Phase.PACKS);
        int maxTier = packs.stream().mapToInt(s -> s.tier).max().orElse(1);
        if (maxTier >= 2) play(TcgSounds.CARD_ANTICIPATE, maxTier == 3 ? 0.6f : 0.8f, 1f);
    }

    public void onGroupReveal(GroupRevealPayload p) {
        for (int i = 0; i < packs.size() && i < p.cards().size(); i++) {
            PackState s = packs.get(i);
            s.cards.clear();
            s.value = 0;
            List<ItemStack> cs = p.cards().get(i);
            for (int k = 0; k < cs.size(); k++) {
                long price = k < p.prices().get(i).size() ? p.prices().get(i).get(k) : 0;
                s.cards.add(new CardView(cs.get(k), price));
                s.value += price;
            }
            s.damaged = p.damaged().get(i);
        }
        toBinder = p.toBinder();
        revealReceived = true;
    }

    private void next() {
        if (phase == Phase.SESSION) {
            close();
            return;
        }
        for (PackState s : packs) if (!s.failed) session.add(new SessionEntry(new ArrayList<>(s.cards), s.damaged, s.damaged ? 0 : s.value));
        if (gi < groups.size() - 1) {
            gi++;
            startGroup();
        } else if (session.size() > 1) {
            setPhase(Phase.SESSION);
            play(TcgSounds.UI_COINS, 1f, 0.8f);
        } else {
            close();
        }
    }

    @Override
    public void close() {
        // cerrar a medias: el servidor entrega el grupo pendiente como limpio
        if (phase == Phase.PACKS || phase == Phase.PREPARING) ClientPlayNetworking.send(new GroupCutPayload(List.of()));
        super.close();
    }

    // ------------------------------------------------------------------ geometría

    private void layoutPacks() {
        int n = packs.size();
        if (n == 0) return;
        // hasta 20: filas de 5; grupos grandes (50/100): rejilla más ancha para que quepan todos y se corten fila a fila
        int cols = n <= 20 ? Math.min(n, 5) : Math.min(20, (int) Math.ceil(Math.sqrt(n * 2.4))), rows = (n + cols - 1) / cols;
        float ph;
        if (n == 1) ph = Math.min(height * 0.72f, 300f);
        else ph = Math.min((height - 90f) / rows - 12f, Math.min((width - 40f) / cols / PACK_RATIO - 10f, 210f));
        float pw = ph * PACK_RATIO, gap = n == 1 ? 0 : Math.max(8f, pw * 0.12f);
        float totalW = cols * pw + (cols - 1) * gap, totalH = rows * ph + (rows - 1) * (gap + 10);
        float x0 = (width - totalW) / 2f, y0 = (height - totalH) / 2f + 4;
        for (int i = 0; i < n; i++) {
            PackState s = packs.get(i);
            int r = i / cols, c = i % cols;
            int inRow = Math.min(cols, n - r * cols);
            float rowX0 = (width - (inRow * pw + (inRow - 1) * gap)) / 2f;
            s.x = (r == rows - 1 ? rowX0 : x0) + c * (pw + gap);
            s.y = y0 + r * (ph + gap + 10);
            s.w = pw;
            s.h = ph;
        }
    }

    private float cardH() { return Math.min(height * 0.62f, 250f); }

    // ------------------------------------------------------------------ render

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xE00B0B14, 0xF2040408);
        int c = 0xFF303050;
        CardView cur = currentReveal();
        if (phase == Phase.REVEAL && cur != null && cur.flipped()) c = cur.rarity.color;
        if (phase == Phase.SUMMARY && destroyStart >= 0) c = 0xFFB02020;
        ctx.fillGradient(0, height / 2, width, height, 0x00000000, withAlpha(c, 0.18f));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        double t = now();
        double dt = MathHelper.clamp(t - lastFrame, 0, 0.1);
        lastFrame = t;
        shake = Math.max(0, shake - dt * 2.2);
        flash = Math.max(0, flash - dt * 2.5);
        renderBackground(ctx, mouseX, mouseY, delta);

        MatrixStack m = ctx.getMatrices();
        m.push();
        if (shake > 0 && phase == Phase.REVEAL) {
            double a = shake * shake * 10;
            m.translate((rng.nextDouble() - 0.5) * a, (rng.nextDouble() - 0.5) * a, 0);
        }
        switch (phase) {
            case PREPARING -> renderPreparing(ctx, t);
            case PACKS -> renderPacks(ctx, t);
            case REVEAL -> renderReveal(ctx, t, mouseX);
            case SUMMARY -> renderSummary(ctx, t, mouseX, mouseY);
            case SESSION -> renderSession(ctx, t, mouseX, mouseY);
        }
        m.pop();
        updateAndDrawParticles(ctx, dt);
        if (flash > 0) ctx.fill(0, 0, width, height, withAlpha(flashColor, (float) (flash * 0.55)));

        int total = 0;
        for (List<Identifier> g : groups) total += g.size();
        if (total > 1 && phase != Phase.SESSION) {
            ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.open.queue", session.size() + 1, total), 8, 8, 0xAAFFFFFF);
        }
        for (var child : children()) if (child instanceof ButtonWidget b) b.render(ctx, mouseX, mouseY, delta);
    }

    // ---- PREPARING / PACKS ------------------------------------------------------------

    private void renderPreparing(DrawContext ctx, double t) {
        for (int i = 0; i < packs.size(); i++) {
            PackState s = packs.get(i);
            float bob = (float) Math.sin(t * 2 + i * 0.6) * 3;
            pack(ctx, s.sealedTex(), s.stack, s.x, s.y + bob, s.w, s.h, 0.8f);
        }
        String dots = ".".repeat(1 + (int) (t * 3) % 3);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("tcgshop.open.preparing").append(dots), width / 2, height - 22, 0xFFAAAAAA);
    }

    private void renderPacks(DrawContext ctx, double t) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3);
        boolean failing = t - strokeFailTime < 0.5;
        for (int i = 0; i < packs.size(); i++) {
            PackState s = packs.get(i);
            if (s.failed) continue;
            if (!s.cut) {
                int aura = TIER_COLORS[s.tier];
                if (s.tier >= 2) rays(ctx, s.x + s.w / 2, s.y + s.h / 2, s.h * 0.8f, t * 15 + i * 20, aura, 0.07f + pulse * 0.05f, 12);
                glow(ctx, s.x + s.w / 2, s.y + s.h / 2, s.w, s.h, aura, s.tier >= 2 ? 0.28f + 0.2f * pulse : 0.08f);
                pack(ctx, s.sealedTex(), s.stack, s.x, s.y, s.w, s.h, 1f);
                // línea guía
                int ly = Math.round(s.y + s.h * TEAR_Y);
                int off = (int) (t * 20) % 8;
                for (float x = s.x - 4 + off; x < s.x + s.w + 4; x += 8)
                    ctx.fill(Math.round(x), ly, Math.round(x) + 4, ly + 1, withAlpha(0xFFFFFFFF, 0.5f + 0.35f * pulse));
                // trazo
                float sa = failing ? (float) (1 - (t - strokeFailTime) / 0.5) : 1f;
                for (int b = 0; b < BINS; b++) {
                    if (Float.isNaN(s.stroke[b])) continue;
                    float x0 = s.x + s.w * b / BINS, x1 = s.x + s.w * (b + 1) / BINS, y = s.y + s.h * s.stroke[b];
                    int col = failing ? 0xFFFF5555 : s.stroke[b] > CARD_TOP ? 0xFFFF8A3D : 0xFFFFFFFF;
                    ctx.fill(Math.round(x0), Math.round(y) - 2, Math.round(x1), Math.round(y) + 2, withAlpha(col, 0.25f * sa));
                    ctx.fill(Math.round(x0), Math.round(y) - 1, Math.round(x1), Math.round(y) + 1, withAlpha(col, sa));
                }
                if (s.tier >= 2 && packs.size() == 1) {
                    scaledCentered(ctx, Text.translatable(s.tier == 3 ? "tcgshop.tier.god" : "tcgshop.tier.semigod"), s.x + s.w / 2, s.y - 22, 1.5f + 0.08f * pulse, aura);
                }
            } else {
                renderTornPack(ctx, s, t);
            }
        }
        if (t - glovesHintTime < 2.0) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("tcgshop.open.need_gloves"), width / 2, 24, 0xFFFFB347);
        }
        if (allCutTime < 0) {
            int alpha = (int) (150 + 100 * Math.sin(t * 4));
            Text hint = Text.translatable(packs.size() > 1 ? "tcgshop.open.tear_many" : "tcgshop.open.tear");
            ctx.drawCenteredTextWithShadow(textRenderer, hint, width / 2, height - 30, (alpha << 24) | 0xFFFFFF);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("tcgshop.open.tear_hint"), width / 2, height - 18, 0x99FFFFFF);
        } else if (t - allCutTime > TEAR_TIME + 0.2) {
            if (revealReceived) startReveal();
            else ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("tcgshop.open.preparing"), width / 2, height - 22, 0xFFAAAAAA);
        }
    }

    /** Sobre cortado: la tira de arriba sale volando, el resto baja; brillo extra si trae una carta de alta rareza. */
    private void renderTornPack(DrawContext ctx, PackState s, double t) {
        double p = MathHelper.clamp((t - s.cutTime) / TEAR_TIME, 0, 1);
        Identifier tex = s.sealedTex();
        if (s.best >= 2) {
            int col = s.best >= 3 ? 0xFFFFD34D : 0xFFC77DFF;
            float k = (float) (1 - p * 0.6);
            rays(ctx, s.x + s.w / 2, s.y + s.h / 2, s.h * 0.9f, t * 40, col, 0.16f * k, s.best >= 3 ? 16 : 10);
            glow(ctx, s.x + s.w / 2, s.y + s.h / 2, s.w, s.h, col, 0.6f * k);
            if (rng.nextFloat() < 0.3f) spawnSparkle(s.x + s.w / 2, s.y + s.h / 2, s.w, s.h, col);
        }
        float down = (float) easeInCubic(MathHelper.clamp((p - 0.35) / 0.65, 0, 1)) * height * 0.15f;
        float alphaDown = 1f - (float) MathHelper.clamp((p - 0.5) / 0.5, 0, 1) * 0.5f;
        split(ctx, s.x, s.y, s.w, s.h, s.packCut, 1, 0, down, () -> pack(ctx, tex, s.stack, s.x, s.y, s.w, s.h, alphaDown));
        tornEdge(ctx, s.x, s.y, s.w, s.h, s.packCut, 0, down, 0xFFE8E8E8, alphaDown, 7);
        float upY = (float) easeOutCubic(p) * -s.h * 0.7f, upX = (float) easeOutCubic(p) * s.w * 0.6f;
        float a = 1f - (float) MathHelper.clamp((p - 0.4) / 0.6, 0, 1);
        if (a > 0) {
            split(ctx, s.x, s.y, s.w, s.h, s.packCut, 0, upX, upY, () -> pack(ctx, tex, s.stack, s.x, s.y, s.w, s.h, a));
            tornEdge(ctx, s.x, s.y, s.w, s.h, s.packCut, upX, upY, 0xFFE8E8E8, a, 7);
        }
        if (s.damaged && p > 0.2) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("✂!"), Math.round(s.x + s.w / 2), Math.round(s.y + s.h / 2), 0xFFFF6B3D);
        }
    }

    private void addStrokePoint(double mx, double my) {
        if (!Double.isNaN(lastMx)) {
            double dist = Math.hypot(mx - lastMx, my - lastMy);
            int steps = Math.max(1, (int) Math.ceil(dist / 2));
            for (int i = 1; i <= steps; i++) recordPoint(lastMx + (mx - lastMx) * i / steps, lastMy + (my - lastMy) * i / steps);
        } else recordPoint(mx, my);
        lastMx = mx;
        lastMy = my;
    }

    private void recordPoint(double mx, double my) {
        for (PackState s : packs) {
            if (s.cut || s.failed) continue;
            double u = (mx - s.x) / s.w, v = (my - s.y) / s.h;
            if (u < 0 || u >= 1 || v < 0 || v > 1) continue;
            int bin = (int) (u * BINS);
            if (Float.isNaN(s.stroke[bin]) && rng.nextFloat() < 0.35f) {
                particles.add(new Particle((float) mx, (float) my, (rng.nextDouble() - 0.5) * 60, -30 - rng.nextDouble() * 50, 0.45,
                        v > CARD_TOP ? 0xFFFF8A3D : 0xFFFFFFFF, 1, true));
            }
            s.stroke[bin] = (float) v;
            int covered = 0;
            for (float f : s.stroke) if (!Float.isNaN(f)) covered++;
            if (covered >= BINS - 1) finishCut(s);
        }
    }

    private void cancelStroke() {
        boolean any = false;
        for (PackState s : packs) {
            if (s.cut) continue;
            for (float f : s.stroke) any |= !Float.isNaN(f);
        }
        if (any) strokeFailTime = now();
        stroking = false;
        lastMx = Double.NaN;
    }

    private void clearPartialStrokes() {
        for (PackState s : packs) if (!s.cut) Arrays.fill(s.stroke, Float.NaN);
    }

    private void finishCut(PackState s) {
        s.cut = true;
        s.cutTime = now();
        s.packCut = new float[BINS];
        for (int i = 0; i < BINS; i++) {
            if (!Float.isNaN(s.stroke[i])) { s.packCut[i] = s.stroke[i]; continue; }
            int a = i - 1, b = i + 1;
            while (a >= 0 && Float.isNaN(s.stroke[a])) a--;
            while (b < BINS && Float.isNaN(s.stroke[b])) b++;
            float va = a >= 0 ? s.stroke[a] : s.stroke[b], vb = b < BINS ? s.stroke[b] : s.stroke[a];
            s.packCut[i] = (a >= 0 && b < BINS) ? va + (vb - va) * (i - a) / (float) (b - a) : (a >= 0 ? va : vb);
        }
        float deepest = 0;
        for (float f : s.packCut) deepest = Math.max(deepest, f);
        s.damaged = deepest > CARD_TOP;
        s.cardCut = new float[16];
        for (int j = 0; j < s.cardCut.length; j++) {
            float u = CARD_X0 + (j + 0.5f) / s.cardCut.length * (CARD_X1 - CARD_X0);
            float v = s.packCut[MathHelper.clamp((int) (u * BINS), 0, BINS - 1)];
            s.cardCut[j] = MathHelper.clamp(Math.max((v - CARD_TOP) / (CARD_BOTTOM - CARD_TOP), 0.035f), 0f, 0.99f);
        }
        if (s.cutTime - lastTearSound > 0.045) { // muchos sobres en un trazo: sin saturar el audio
            lastTearSound = s.cutTime;
            play(TcgSounds.PACK_TEAR, s.damaged ? 0.7f : 0.9f + rng.nextFloat() * 0.15f, 0.9f);
        }
        // sobre con carta de alta rareza: sonido especial y ráfaga
        if (s.best >= 3) {
            play(TcgSounds.CARD_LEGENDARY, 1.2f, 0.8f);
            burstAt(s.x + s.w / 2, s.y + s.h * TEAR_Y, s.w, 0xFFFFD34D, 50);
        } else if (s.best >= 2) {
            play(TcgSounds.CARD_EPIC, 1.5f, 0.6f);
            burstAt(s.x + s.w / 2, s.y + s.h * TEAR_Y, s.w, 0xFFC77DFF, 30);
        } else {
            burstAt(s.x + s.w / 2, s.y + s.h * (s.damaged ? deepest : TEAR_Y), s.w, s.damaged ? 0xFFFF8A3D : 0xFFFFFFFF, 14);
        }
        if (packs.stream().allMatch(p -> p.cut)) {
            allCutTime = now();
            stroking = false;
            List<Boolean> dmg = new ArrayList<>();
            for (PackState p : packs) dmg.add(!p.failed && p.damaged);
            ClientPlayNetworking.send(new GroupCutPayload(dmg));
            play(TcgSounds.PACK_OPEN, 1f, 0.7f);
        }
    }

    // ---- REVEAL ---------------------------------------------------------------------

    private CardView currentReveal() {
        if (revPack >= packs.size()) return null;
        List<CardView> cs = packs.get(revPack).cards;
        return revCard < cs.size() ? cs.get(revCard) : null;
    }

    private void startReveal() {
        setPhase(Phase.REVEAL);
        revPack = 0;
        revCard = 0;
        skipEmptyPacks();
        prepareCurrentCard();
    }

    private void skipEmptyPacks() {
        while (revPack < packs.size() && (packs.get(revPack).failed || packs.get(revPack).cards.isEmpty())) {
            revPack++;
            revCard = 0;
        }
    }

    private void prepareCurrentCard() {
        CardView c = currentReveal();
        if (c == null) {
            goToSummary();
            return;
        }
        if (c.rarity.tier == 0) c.flipStart = now() - 1;
        else play(TcgSounds.CARD_ANTICIPATE, 0.7f + c.rarity.tier * 0.15f, 0.6f);
        play(TcgSounds.CARD_SLIDE, 1f, 0.7f);
    }

    private void advanceCard() {
        revCard++;
        if (revCard >= packs.get(revPack).cards.size()) {
            revPack++;
            revCard = 0;
            skipEmptyPacks();
        }
        prepareCurrentCard();
    }

    private void renderReveal(DrawContext ctx, double t, int mouseX) {
        CardView c = currentReveal();
        if (c == null) return;
        float ch = cardH(), cw = ch * CARD_RATIO;
        float cx = width / 2f, cy = height / 2f - 6;
        MatrixStack m = ctx.getMatrices();
        int remaining = packs.get(revPack).cards.size() - revCard - 1;
        for (int i = Math.min(3, remaining); i >= 1; i--) {
            m.push();
            m.translate(cx + i * 3, cy + i * 3, 0);
            cardBack(ctx, cw, ch, 1f, 0, t);
            m.pop();
        }
        float enter = (float) easeOutBack(MathHelper.clamp((t - c.enterTime(t)) / 0.35, 0, 1));
        float x = cx + (float) dragX, y = cy + (1 - enter) * 30, rot = (float) dragX * 0.05f, alpha = 1f;
        if (dismissStart >= 0) {
            double d = MathHelper.clamp((t - dismissStart) / 0.3, 0, 1);
            x += (float) (easeInCubic(d) * width * 0.8 * dismissDir);
            rot += (float) (d * 25 * dismissDir);
            alpha = 1f - (float) d;
            if (d >= 1) {
                dismissStart = -1;
                dragX = 0;
                advanceCard();
                return;
            }
        }
        double flipT = c.flipStart < 0 ? -1 : MathHelper.clamp((t - c.flipStart) / 0.45, 0, 1);
        boolean faceUp = flipT >= 0.5;
        if (faceUp && c.rarity.tier >= 2) rays(ctx, x, y, ch * 1.1f, t * 20, c.rarity.color, 0.12f * alpha, c.rarity.tier >= 3 ? 18 : 10);
        if (!faceUp && c.rarity.tier >= 1) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * (4 + c.rarity.tier * 2));
            glow(ctx, x, y, cw, ch, c.rarity.color, (0.25f + 0.35f * pulse) * c.rarity.tier / 2f);
            if (rng.nextFloat() < 0.15f * c.rarity.tier) spawnSparkle(x, y, cw, ch, c.rarity.color);
        } else if (faceUp && c.rarity.tier >= 1) glow(ctx, x, y, cw, ch, c.rarity.color, 0.35f * alpha);

        float sx = flipT < 0 ? 1f : (float) Math.abs(Math.cos(flipT * Math.PI));
        float pop = flipT >= 0.5 && flipT < 1 ? 1f + 0.08f * (float) Math.sin((flipT - 0.5) * 2 * Math.PI) : 1f;
        m.push();
        m.translate(x, y, 0);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rot));
        m.scale(sx * pop, pop, 1f);
        if (faceUp) drawFaceCentered(ctx, c, cw, ch, alpha);
        else cardBack(ctx, cw, ch, alpha, c.rarity.tier >= 1 ? c.rarity.color : 0, t);
        m.pop();
        if (faceUp && c.rarity.tier >= 1 && Math.abs(rot) < 0.5f && flipT >= 1 && alpha > 0.9f)
            holo(ctx, x - cw / 2, y - ch / 2, cw, ch, c.rarity.tier, c.rarity.color, t, mouseX);

        if (faceUp && dismissStart < 0) {
            int ty = (int) (y + ch / 2 + 10);
            ctx.drawCenteredTextWithShadow(textRenderer, c.stack.getName(), width / 2, ty, 0xFFFFFFFF);
            ctx.drawCenteredTextWithShadow(textRenderer, c.rarity.displayName(), width / 2, ty + 11, c.rarity.color);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(PriceService.format(c.price)), width / 2, ty + 22, 0xFFFFD34D);
        }
        String counter = packs.size() > 1 ? Text.translatable("tcgshop.open.reveal_pack", revPack + 1, packs.size()).getString() + "  ·  " : "";
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(counter + (revCard + 1) + " / " + packs.get(revPack).cards.size()), width / 2, 12, 0xAAFFFFFF);
        if (dismissStart < 0) {
            int a = (int) (120 + 80 * Math.sin(t * 4));
            ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("tcgshop.open.swipe"), width / 2, height - 18, (a << 24) | 0xFFFFFF);
        }
        if (faceUp && !c.celebrated) {
            c.celebrated = true;
            celebrate(c, x, y, cw, ch);
        }
    }

    private void drawFaceCentered(DrawContext ctx, CardView c, float w, float h, float alpha) {
        int x0 = Math.round(-w / 2), y0 = Math.round(-h / 2);
        ctx.fill(x0 + 3, y0 + 5, x0 + Math.round(w) + 3, y0 + Math.round(h) + 5, withAlpha(0xFF000000, 0.45f * alpha));
        cardFace(ctx, c.texture, c.stack, x0, y0, Math.round(w), Math.round(h), alpha);
        ctx.drawBorder(x0, y0, Math.round(w), Math.round(h), withAlpha(c.rarity.tier > 0 ? c.rarity.color : 0xFF202020, alpha));
    }

    private void celebrate(CardView c, float x, float y, float cw, float ch) {
        switch (c.rarity.tier) {
            case 0 -> play(TcgSounds.CARD_FLIP, 1.1f, 0.6f);
            case 1 -> { play(TcgSounds.CARD_RARE, 1.2f, 1f); burst(x, y, cw, ch, c.rarity.color, 25); }
            case 2 -> {
                play(TcgSounds.CARD_RARE, 0.9f, 1f);
                play(TcgSounds.CARD_EPIC, 1.4f, 0.6f);
                burst(x, y, cw, ch, c.rarity.color, 60);
                shake = 0.5; flash = 0.5; flashColor = c.rarity.color;
            }
            default -> {
                play(TcgSounds.CARD_LEGENDARY, 1f, 1f);
                play(TcgSounds.CARD_EPIC, 0.8f, 0.8f);
                burst(x, y, cw, ch, c.rarity.color, 120);
                burst(x, y, cw, ch, 0xFFFFFFFF, 40);
                shake = 1.0; flash = 1.0; flashColor = c.rarity.color;
            }
        }
    }

    private void advanceReveal(int dir) {
        CardView c = currentReveal();
        if (phase != Phase.REVEAL || c == null || dismissStart >= 0) return;
        double t = now();
        if (c.flipStart < 0) {
            c.flipStart = t;
            play(TcgSounds.CARD_FLIP, 1f, 0.8f);
        } else if (t - c.flipStart > 0.45) {
            dismissStart = t;
            dismissDir = dir == 0 ? -1 : dir;
            play(TcgSounds.CARD_SLIDE, 0.9f, 0.7f);
        }
    }

    private void skipReveal() {
        if (phase == Phase.REVEAL) goToSummary();
    }

    // ---- SUMMARY --------------------------------------------------------------------

    private void goToSummary() {
        for (PackState s : packs) for (CardView c : s.cards) { if (c.flipStart < 0) c.flipStart = now() - 1; c.celebrated = true; }
        dismissStart = -1;
        setPhase(Phase.SUMMARY);
        play(packs.stream().anyMatch(s -> s.damaged) ? TcgSounds.CARD_SLIDE : TcgSounds.UI_COINS, 1f, 0.6f);
    }

    private void renderSummary(DrawContext ctx, double t, int mouseX, int mouseY) {
        double since = t - phaseStart;
        long total = 0;
        boolean anyDamaged = false;
        for (PackState s : packs) {
            if (!s.damaged) total += s.value;
            anyDamaged |= s.damaged && !s.cards.isEmpty();
        }
        if (anyDamaged && destroyStart < 0 && since > 1.0) {
            destroyStart = t;
            flash = 0.5;
            flashColor = 0xFFB02020;
        }
        scaledCentered(ctx, Text.translatable(packs.size() > 1 ? "tcgshop.open.summary_group" : "tcgshop.open.summary", packs.size()), width / 2f, 14, 1.5f, 0xFFFFFFFF);
        scaledCentered(ctx, Text.translatable("tcgshop.open.total", PriceService.format(total)), width / 2f, 32, 1.2f, 0xFFFFD34D);
        if (toBinder) ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("tcgshop.open.to_binder"), width / 2, 46, 0xFF8BE08B);

        // filas: una por sobre
        List<PackState> shown = new ArrayList<>();
        for (PackState s : packs) if (!s.failed && !s.cards.isEmpty()) shown.add(s);
        int top = 58, bottom = height - 36;
        int maxCards = 5;
        for (PackState s : shown) maxCards = Math.max(maxCards, s.cards.size());
        float rowH = shown.size() == 1 ? Math.min(bottom - top - 10, height * 0.5f) : Math.min(78, Math.max(48, (bottom - top) / (float) Math.min(shown.size(), 4)));
        float cardHgt = rowH - 8, cardW = cardHgt * CARD_RATIO;
        if (cardW * maxCards + 60 > width - 20) { cardW = (width - 80f) / maxCards - 6; cardHgt = cardW / CARD_RATIO; }
        float content = shown.size() * rowH;
        summaryScroll = MathHelper.clamp(summaryScroll, 0, Math.max(0, content - (bottom - top)));
        hoveredSummary = null;
        ctx.enableScissor(0, top, width, bottom);
        for (int r = 0; r < shown.size(); r++) {
            PackState s = shown.get(r);
            float y = top + r * rowH - (float) summaryScroll;
            if (y + rowH < top || y > bottom) continue;
            float rowW = s.cards.size() * (cardW + 6) + (shown.size() > 1 ? 40 : 0);
            float x = (width - rowW) / 2f;
            if (shown.size() > 1) {
                float ph = cardHgt * 0.8f;
                pack(ctx, s.sealedTex(), s.stack, x, y + (rowH - ph) / 2, ph * PACK_RATIO, ph, 0.9f);
                String v = s.damaged ? PriceService.format(0) : PriceService.format(s.value);
                ctx.drawText(textRenderer, v, Math.round(x), Math.round(y + rowH - 9), s.damaged ? 0xFFFF5555 : 0xFFFFD34D, true);
                x += 40;
            }
            double appear = since - r * 0.08;
            for (int k = 0; k < s.cards.size(); k++) {
                CardView c = s.cards.get(k);
                float cx = x + k * (cardW + 6), cy = y + (rowH - cardHgt) / 2 + (float) Math.max(0, (0.3 - appear) * 60);
                if (s.damaged && destroyStart >= 0) {
                    drawDestroyed(ctx, c, s, k, r, cx, cy, cardW, cardHgt, t);
                    continue;
                }
                boolean hover = Ui.in(mouseX, mouseY, cx, cy, cardW, cardHgt) && mouseY >= top && mouseY <= bottom;
                ctx.fill(Math.round(cx) + 2, Math.round(cy) + 3, Math.round(cx + cardW) + 2, Math.round(cy + cardHgt) + 3, 0x66000000);
                cardFace(ctx, c.texture, c.stack, cx, cy, cardW, cardHgt, 1f);
                ctx.drawBorder(Math.round(cx), Math.round(cy), Math.round(cardW), Math.round(cardHgt), c.rarity.tier > 0 ? c.rarity.color : 0xFF202020);
                if (hover) {
                    hoveredSummary = c;
                    if (c.rarity.tier > 0) holo(ctx, cx, cy, cardW, cardHgt, c.rarity.tier, c.rarity.color, t, mouseX);
                }
            }
        }
        ctx.disableScissor();
        if (hoveredSummary != null) {
            ctx.drawTooltip(textRenderer, List.of(hoveredSummary.stack.getName(),
                    hoveredSummary.rarity.displayName().copy().withColor(hoveredSummary.rarity.color & 0xFFFFFF),
                    Text.literal(PriceService.format(hoveredSummary.price)).withColor(0xFFD34D)), mouseX, mouseY);
        }
        if (anyDamaged && destroyStart >= 0 && t - destroyStart > 1.0) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("tcgshop.open.destroyed_hint"), width / 2, bottom - 10, 0xFFFF9090);
        }
    }

    private void drawDestroyed(DrawContext ctx, CardView c, PackState s, int k, int row, float x0, float y0, float cw, float ch, double t) {
        double local = t - destroyStart - k * 0.12 - row * 0.2;
        if (local < 0) {
            cardFace(ctx, c.texture, c.stack, x0, y0, cw, ch, 1f);
            return;
        }
        int key = row * 10 + k;
        if (destroySounds <= key && local < 0.2) {
            destroySounds = key + 1;
            play(TcgSounds.PACK_TEAR, 0.6f + rng.nextFloat() * 0.3f, 0.8f);
            float cutY = y0 + ch * s.cardCut[s.cardCut.length / 2];
            for (int i = 0; i < 8; i++)
                particles.add(new Particle(x0 + rng.nextFloat() * cw, cutY, (rng.nextDouble() - 0.5) * 80, -20 - rng.nextDouble() * 50,
                        0.6, rng.nextBoolean() ? 0xFFEFE6C8 : 0xFFFFFFFF, 1, true));
        }
        float sep = (float) easeOutCubic(MathHelper.clamp(local / 0.6, 0, 1));
        float fall = (float) MathHelper.clamp((local - 0.6) / 1.2, 0, 1);
        float alpha = 1f - fall * 0.55f;
        float s1 = cw / 60f;
        float upDx = -sep * 8 * s1, upDy = -sep * 14 * s1 - fall * 4, loDx = sep * 8 * s1, loDy = sep * 18 * s1 + fall * 18;
        split(ctx, x0, y0, cw, ch, s.cardCut, 0, upDx, upDy, () -> cardFace(ctx, c.texture, c.stack, x0, y0, cw, ch, alpha));
        split(ctx, x0, y0, cw, ch, s.cardCut, 1, loDx, loDy, () -> cardFace(ctx, c.texture, c.stack, x0, y0, cw, ch, alpha));
        tornEdge(ctx, x0, y0, cw, ch, s.cardCut, upDx, upDy, 0xFFF5F0E0, alpha, key * 13L);
        tornEdge(ctx, x0, y0, cw, ch, s.cardCut, loDx, loDy, 0xFFF5F0E0, alpha, key * 13L + 5);
    }

    // ---- SESSION --------------------------------------------------------------------

    private void renderSession(DrawContext ctx, double t, int mouseX, int mouseY) {
        long total = 0;
        int ruined = 0, nCards = 0;
        List<CardView> all = new ArrayList<>();
        for (SessionEntry e : session) {
            total += e.value;
            if (e.damaged) ruined++;
            else all.addAll(e.cards);
            nCards += e.cards.size();
        }
        all.sort(Comparator.comparingLong((CardView c) -> c.price).reversed());
        scaledCentered(ctx, Text.translatable("tcgshop.session.title"), width / 2f, 16, 1.6f, 0xFFFFFFFF);
        scaledCentered(ctx, Text.translatable("tcgshop.open.total", PriceService.format(total)), width / 2f, 38, 1.3f, 0xFFFFD34D);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("tcgshop.session.stats", session.size(), nCards, ruined), width / 2, 56,
                ruined > 0 ? 0xFFFF9090 : 0xFFAAAAAA);
        int show = Math.min(8, all.size());
        if (show == 0) return;
        float cw = Math.min(height * 0.4f * CARD_RATIO, (width - 40f) / show - 8f), ch = cw / CARD_RATIO;
        float total0 = show * cw + (show - 1) * 8f, y = height * 0.52f;
        CardView hovered = null;
        float hx = 0;
        MatrixStack m = ctx.getMatrices();
        for (int i = 0; i < show; i++) {
            CardView c = all.get(i);
            float x = (width - total0) / 2f + i * (cw + 8) + cw / 2;
            float appear = (float) easeOutBack(MathHelper.clamp((t - phaseStart - i * 0.08) / 0.4, 0, 1));
            m.push();
            m.translate(x, y + (1 - appear) * 40, 0);
            drawFaceCentered(ctx, c, cw, ch, MathHelper.clamp(appear, 0, 1));
            m.pop();
            if (Math.abs(mouseX - x) < cw / 2 && Math.abs(mouseY - y) < ch / 2) { hovered = c; hx = x; }
        }
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("tcgshop.session.best"), width / 2, (int) (y - ch / 2 - 14), 0xFFCCCCCC);
        if (hovered != null) {
            if (hovered.rarity.tier >= 1) holo(ctx, hx - cw / 2, y - ch / 2, cw, ch, hovered.rarity.tier, hovered.rarity.color, t, mouseX);
            ctx.drawTooltip(textRenderer, List.of(hovered.stack.getName(), hovered.rarity.displayName().copy().withColor(hovered.rarity.color & 0xFFFFFF),
                    Text.literal(PriceService.format(hovered.price)).withColor(0xFFD34D)), mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------ efectos

    private void burst(float x, float y, float w, float h, int color, int n) {
        for (int i = 0; i < n; i++) {
            double ang = rng.nextDouble() * Math.PI * 2, sp = 80 + rng.nextDouble() * 260;
            particles.add(new Particle(x + (rng.nextFloat() - 0.5f) * w * 0.6f, y + (rng.nextFloat() - 0.5f) * h * 0.6f,
                    Math.cos(ang) * sp, Math.sin(ang) * sp - 60, 0.8 + rng.nextDouble() * 0.9, rng.nextBoolean() ? color : brighten(color), 1 + rng.nextInt(3), true));
        }
    }

    private void burstAt(float cx, float y, float w, int color, int n) {
        for (int i = 0; i < n; i++) {
            particles.add(new Particle(cx + (rng.nextFloat() - 0.5f) * w, y, (rng.nextDouble() - 0.5) * 160, -40 - rng.nextDouble() * 160,
                    0.6 + rng.nextDouble() * 0.6, rng.nextBoolean() ? color : 0xFFFFFFFF, 1 + rng.nextInt(2), true));
        }
    }

    private void spawnSparkle(float x, float y, float w, float h, int color) {
        particles.add(new Particle(x + (rng.nextFloat() - 0.5f) * w, y + (rng.nextFloat() - 0.5f) * h, (rng.nextDouble() - 0.5) * 20,
                -10 - rng.nextDouble() * 30, 0.6 + rng.nextDouble() * 0.6, color, 1 + rng.nextInt(2), false));
    }

    private void updateAndDrawParticles(DrawContext ctx, double dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.age += dt;
            if (p.age >= p.life) { particles.remove(i); continue; }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (p.gravity) p.vy += 300 * dt;
            p.vx *= 1 - dt * 1.5;
            float a = (float) (1 - p.age / p.life);
            int s = p.size, px = (int) p.x, py = (int) p.y;
            ctx.fill(px - s, py, px + s + 1, py + 1, withAlpha(p.color, a));
            ctx.fill(px, py - s, px + 1, py + s + 1, withAlpha(p.color, a));
            if (s > 1) ctx.fill(px - 1, py - 1, px + 2, py + 2, withAlpha(0xFFFFFFFF, a * 0.8f));
        }
    }

    // ------------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        pressX = mouseX;
        pressY = mouseY;
        if (phase == Phase.PACKS && allCutTime < 0) {
            clearPartialStrokes();
            strokeFailTime = -10;
            stroking = true;
            lastMx = Double.NaN;
            addStrokePoint(mouseX, mouseY);
        } else if (phase == Phase.REVEAL) draggingCard = true;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (phase == Phase.PACKS && stroking) addStrokePoint(mouseX, mouseY);
        else if (phase == Phase.REVEAL && draggingCard && dismissStart < 0) {
            CardView c = currentReveal();
            if (c != null && c.flipStart >= 0) dragX = mouseX - pressX;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        super.mouseReleased(mouseX, mouseY, button);
        if (phase == Phase.PACKS && stroking) cancelStroke();
        else if (phase == Phase.REVEAL && draggingCard) {
            draggingCard = false;
            float cw = cardH() * CARD_RATIO;
            boolean moved = Math.abs(mouseX - pressX) > 6 || Math.abs(mouseY - pressY) > 6;
            if (!moved) advanceReveal(0);
            else if (Math.abs(dragX) > cw * 0.35) advanceReveal(dragX > 0 ? 1 : -1);
            else dragX = 0;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (phase == Phase.SUMMARY) summaryScroll -= v * 30;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_RIGHT) {
            switch (phase) {
                case PACKS -> {
                    // Espacio = corte perfecto de todos los sobres, SOLO con los Guantes de friki puestos
                    if (keyCode == GLFW.GLFW_KEY_SPACE && client != null && client.player != null
                            && dev.tcgshop.item.NerdGlovesItem.isWearing(client.player)) {
                        for (PackState s : packs) if (!s.cut && !s.failed) { Arrays.fill(s.stroke, TEAR_Y); finishCut(s); }
                    } else if (keyCode == GLFW.GLFW_KEY_SPACE) {
                        glovesHintTime = now();
                    }
                }
                case REVEAL -> advanceReveal(-1);
                case SUMMARY, SESSION -> { if (keyCode != GLFW.GLFW_KEY_SPACE) next(); }
                default -> {}
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ util

    private void play(SoundEvent sound, float pitch, float volume) {
        if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(sound, pitch, volume));
    }

    private static double now() {
        return System.nanoTime() / 1.0e9;
    }

    private static final class CardView {
        final ItemStack stack;
        final Rarity rarity;
        final long price;
        final Identifier texture;
        double flipStart = -1;
        boolean celebrated;
        private double enter = -1;

        CardView(ItemStack stack, long price) {
            this.stack = stack;
            this.rarity = Rarity.of(stack);
            this.price = price;
            this.texture = CardInfo.texture(CardInfo.id(stack));
        }

        boolean flipped() { return flipStart >= 0 && now() - flipStart > 0.225; }

        double enterTime(double t) {
            if (enter < 0) enter = t;
            return enter;
        }
    }

    private static final class Particle {
        double x, y, vx, vy, age;
        final double life;
        final int color, size;
        final boolean gravity;

        Particle(float x, float y, double vx, double vy, double life, int color, int size, boolean gravity) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.life = life;
            this.color = color; this.size = size; this.gravity = gravity;
        }
    }

    /** Pequeño helper de hit-test (evita depender del paquete binder). */
    private static final class Ui {
        static boolean in(double mx, double my, float x, float y, float w, float h) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }
}
