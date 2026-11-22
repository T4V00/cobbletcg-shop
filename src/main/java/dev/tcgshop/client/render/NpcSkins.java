package dev.tcgshop.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Pieles propias de los NPC, pintadas por código (64x64, formato de piel de jugador): nada de Steve/Alex.
 * Cada rol tiene su estilo (clientes con ropa de fan de Pokémon genérica, cajeros con delantal y gorra de la tienda,
 * guardias de uniforme, Mayorista con gabardina, coleccionistas con chaleco, revendedores con sudadera, mendigos con
 * ropa remendada y el mercader negro encapuchado) y el índice cambia colores, pelo, gorra y emblema.
 * Los emblemas son figuras genéricas (bola bicolor, rayo, llama, hoja, gota, estrella), sin arte oficial.
 */
public final class NpcSkins {
    private NpcSkins() {}

    private static final Map<String, Identifier> CACHE = new HashMap<>();

    public static boolean slim(int index) {
        return Math.floorMod(index, 3) == 1;
    }

    public static Identifier texture(String roleKey, int index) {
        String role = roleKey.substring(roleKey.lastIndexOf('.') + 1);
        String key = role + "_" + Math.floorMod(index, 64);
        Identifier id = CACHE.get(key);
        if (id != null) return id;
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, 64, 64, true);
        new Painter(img, role, Math.floorMod(index, 64)).paint();
        id = Identifier.of("tcgshop", "dynamic/npc_skin_" + key);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(img));
        CACHE.put(key, id);
        return id;
    }

    // ------------------------------------------------------------------ pintor

    private static final int[] SKIN_TONES = {0xFFF6D3B3, 0xFFEAC09A, 0xFFD9A47A, 0xFFC08A5E, 0xFF9C6A44, 0xFF7A4E2E, 0xFF5A3820, 0xFFF0C8A0};
    private static final int[] HAIR = {0xFF2A1C12, 0xFF4A2E1A, 0xFF7A4A22, 0xFFB07A3A, 0xFFE0C070, 0xFF1A1A1E, 0xFF8A2A1A, 0xFFD8D8D8,
            0xFF3A5FC8, 0xFFE070B0, 0xFF5AC07A, 0xFF8A4AD0};
    private static final int[] SHIRTS = {0xFFE33B3B, 0xFF3A6FD8, 0xFFF2C14E, 0xFF4CAF50, 0xFF8E44AD, 0xFFFF8A3D, 0xFF26A69A, 0xFF37474F,
            0xFFF5F5F5, 0xFFEC407A, 0xFF5C6BC0, 0xFF8D6E63};
    private static final int[] PANTS = {0xFF2B3A5A, 0xFF3A3A40, 0xFF4A3A2A, 0xFF1F2A3A, 0xFF5A5A62, 0xFF6B4A2A, 0xFF22301F};

    private static final class Painter {
        final NativeImage img;
        final String role;
        final Random r;
        final boolean slim;
        final int aw;
        final int skin, hair, shirt, shirtDark, pants, shoes;

        Painter(NativeImage img, String role, int index) {
            this.img = img;
            this.role = role;
            this.r = new Random(index * 7919L + role.hashCode());
            this.slim = slim(index);
            this.aw = slim ? 3 : 4;
            skin = SKIN_TONES[r.nextInt(SKIN_TONES.length)];
            hair = r.nextFloat() < 0.8f ? HAIR[r.nextInt(7)] : HAIR[r.nextInt(HAIR.length)];
            shirt = SHIRTS[r.nextInt(SHIRTS.length)];
            shirtDark = shade(shirt, -40);
            pants = PANTS[r.nextInt(PANTS.length)];
            shoes = r.nextBoolean() ? 0xFF1E1E22 : shade(SHIRTS[r.nextInt(SHIRTS.length)], -20);
        }

        // --- utilidades
        void px(int x, int y, int c) {
            if (x >= 0 && y >= 0 && x < 64 && y < 64) img.setColor(x, y, abgr(c));
        }

        void rect(int x, int y, int w, int h, int c) {
            for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) px(x + i, y + j, c);
        }

        /** Rellena la plantilla de un cubo (arriba, abajo, der, frente, izq, detrás) con un color con algo de textura. */
        void box(int u, int v, int w, int h, int d, int c) {
            rectN(u + d, v, w, d, c);
            rectN(u + d + w, v, w, d, shade(c, -12));
            rectN(u, v + d, d, h, shade(c, -8));
            rectN(u + d, v + d, w, h, c);
            rectN(u + d + w, v + d, d, h, shade(c, -8));
            rectN(u + d + w + d, v + d, w, h, shade(c, -14));
        }

        void rectN(int x, int y, int w, int h, int c) {
            for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) px(x + i, y + j, shade(c, r.nextInt(7) - 3));
        }

        /** Fila (desde arriba) de las 4 caras laterales de un cubo: der, frente, izq, detrás. */
        void ring(int u, int v, int w, int d, int row, int c) {
            int y = v + d + row;
            rect(u, y, d, 1, c);
            rect(u + d, y, w, 1, c);
            rect(u + d + w, y, d, 1, c);
            rect(u + d + w + d, y, w, 1, c);
        }

        void ringRows(int u, int v, int w, int d, int from, int to, int c) {
            for (int row = from; row <= to; row++) ring(u, v, w, d, row, c);
        }

        static int shade(int c, int amt) {
            int a = c >>> 24, rr = clamp(((c >> 16) & 0xFF) + amt), g = clamp(((c >> 8) & 0xFF) + amt), b = clamp((c & 0xFF) + amt);
            return (a << 24) | (rr << 16) | (g << 8) | b;
        }

        static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

        static int abgr(int argb) {
            int a = argb >>> 24, rr = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
            return (a << 24) | (b << 16) | (g << 8) | rr;
        }

        // --- piel completa
        void paint() {
            // todo transparente (las capas exteriores sólo donde haga falta)
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) img.setColor(x, y, 0);
            head();
            switch (role) {
                case "cashier" -> outfit(0xFFF5F5F5, true, PANTS[0]);
                case "guard" -> outfit(0xFF263248, false, 0xFF1E2636);
                case "wholesaler" -> outfit(shade(0xFF6B4A2A, r.nextInt(20)), false, 0xFF3A2A1E);
                case "beggar" -> outfit(shade(0xFF7A6A52, r.nextInt(16) - 8), r.nextBoolean(), 0xFF4A4038);
                case "black_market" -> outfit(0xFF1A1A22, false, 0xFF121218);
                case "furniture_seller" -> outfit(shade(0xFF8D6E63, r.nextInt(18)), r.nextBoolean(), 0xFF4E342E);
                default -> outfit(shirt, r.nextFloat() < 0.6f, pants);
            }
            emblem();
            overlays();
        }

        void head() {
            box(0, 0, 8, 8, 8, skin);
            // pelo: arriba, detrás y laterales altos
            boolean longHair = r.nextFloat() < 0.35f, bald = role.equals("guard") ? r.nextFloat() < 0.3f : r.nextFloat() < 0.06f;
            if (!bald) {
                rectN(8, 0, 8, 8, hair);
                ringRows(0, 0, 8, 8, 0, longHair ? 1 : 0, hair);
                rect(0, 8, 8, longHair ? 7 : 3, hair);      // lado derecho
                rect(16, 8, 8, longHair ? 7 : 3, hair);     // lado izquierdo
                rect(24, 8, 8, longHair ? 8 : 5, hair);     // detrás
                if (longHair) { rect(8, 9, 1, 5, hair); rect(15, 9, 1, 5, hair); }
            }
            // cara (frente: 8..15, 8..15)
            int eye = r.nextFloat() < 0.15f ? 0xFF3A6FD8 : r.nextFloat() < 0.2f ? 0xFF4CAF50 : 0xFF3A2414;
            px(9, 12, 0xFFFFFFFF); px(10, 12, eye);
            px(13, 12, eye); px(14, 12, 0xFFFFFFFF);
            px(9, 11, shade(hair, -10)); px(10, 11, shade(hair, -10)); px(13, 11, shade(hair, -10)); px(14, 11, shade(hair, -10));
            px(11, 13, shade(skin, -18)); px(12, 13, shade(skin, -18));
            int mouth = shade(skin, -45);
            px(11, 14, mouth); px(12, 14, mouth);
            if (role.equals("beggar") || (r.nextFloat() < 0.12f && !slim)) { // barba
                int beard = shade(hair, -5);
                rect(9, 14, 6, 2, beard);
                px(11, 14, mouth); px(12, 14, mouth);
            }
            if (role.equals("black_market")) { px(10, 12, 0xFFFF3030); px(13, 12, 0xFFFF3030); }
        }

        /** Camiseta/chaqueta, brazos y pantalón. shortSleeves = manga corta. */
        void outfit(int top, boolean shortSleeves, int bottom) {
            box(16, 16, 8, 12, 4, top);
            ring(16, 16, 8, 4, 0, shade(top, 18)); // cuello
            // brazos
            int[][] arms = {{40, 16}, {32, 48}};
            for (int[] a : arms) {
                box(a[0], a[1], aw, 12, 4, skin);
                ringRows(a[0], a[1], aw, 4, 0, shortSleeves ? 3 : 10, top);
                if (!shortSleeves) ring(a[0], a[1], aw, 4, 10, shade(top, -25)); // puño
                rect(a[0] + 4, a[1], aw, 4, top);
            }
            // piernas: pantalón y zapatos
            int[][] legs = {{0, 16}, {16, 48}};
            for (int[] l : legs) {
                box(l[0], l[1], 4, 12, 4, bottom);
                ringRows(l[0], l[1], 4, 4, 10, 11, shoes);
                rect(l[0] + 8, l[1], 4, 4, shoes); // suela
            }
            ring(16, 16, 8, 4, 11, shade(bottom, -15)); // cinturón
            ring(16, 16, 8, 4, 10, 0xFF2A2018);
            if (role.equals("beggar")) { // remiendos
                for (int k = 0; k < 4; k++) rect(20 + r.nextInt(6), 22 + r.nextInt(8), 2, 2, shade(SHIRTS[r.nextInt(SHIRTS.length)], -30));
                rect(4 + r.nextInt(2), 24 + r.nextInt(4), 2, 2, 0xFF6A5A3A);
            }
        }

        /** Emblema genérico en el pecho (frente del cuerpo: 20..27, 20..31). */
        void emblem() {
            if (role.equals("black_market") || role.equals("beggar")) return;
            int cx = 22, cy = 23, kind = role.equals("cashier") ? 0 : role.equals("guard") ? 6 : r.nextInt(7);
            int col = shade(shirt, 70) == shirt ? 0xFFFFFFFF : contrast(role.equals("cashier") ? 0xFFF5F5F5 : role.equals("guard") ? 0xFF263248 : shirt);
            switch (kind) {
                case 0 -> { // bola bicolor
                    rect(cx, cy, 4, 2, 0xFFE33B3B);
                    rect(cx, cy + 2, 4, 2, 0xFFFFFFFF);
                    rect(cx, cy + 2, 4, 1, 0xFF1A1A1A);
                    px(cx + 1, cy + 2, 0xFFFFFFFF); px(cx + 2, cy + 2, 0xFFFFFFFF);
                    px(cx, cy, 0); px(cx + 3, cy, 0); px(cx, cy + 3, 0); px(cx + 3, cy + 3, 0);
                    restoreCorners(cx, cy);
                }
                case 1 -> { px(cx + 2, cy, 0xFFFFD23F); px(cx + 1, cy + 1, 0xFFFFD23F); px(cx + 2, cy + 1, 0xFFFFD23F); px(cx + 1, cy + 2, 0xFFFFD23F); px(cx, cy + 3, 0xFFFFD23F); } // rayo
                case 2 -> { rect(cx + 1, cy + 1, 2, 3, 0xFFFF7A2A); px(cx + 1, cy, 0xFFFFC23A); px(cx + 2, cy + 2, 0xFFFFD23F); } // llama
                case 3 -> { px(cx + 2, cy, 0xFF6CC46F); rect(cx + 1, cy + 1, 2, 2, 0xFF4CAF50); px(cx, cy + 3, 0xFF2E7A33); } // hoja
                case 4 -> { px(cx + 1, cy, 0xFF8FD8FF); rect(cx + 1, cy + 1, 2, 2, 0xFF4FA3E8); rect(cx + 1, cy + 3, 2, 1, 0xFF2D64B8); } // gota
                case 5 -> { px(cx + 1, cy, col); px(cx + 2, cy, col); rect(cx, cy + 1, 4, 1, col); px(cx + 1, cy + 2, col); px(cx + 2, cy + 2, col); px(cx, cy + 3, col); px(cx + 3, cy + 3, col); } // estrella
                default -> { rect(cx, cy, 4, 3, 0xFFC9D2DE); rect(cx + 1, cy + 3, 2, 1, 0xFFC9D2DE); px(cx + 1, cy + 1, 0xFF3A6FD8); px(cx + 2, cy + 1, 0xFF3A6FD8); } // placa / escudo
            }
        }

        void restoreCorners(int cx, int cy) {
            int c = role.equals("cashier") ? 0xFFF5F5F5 : shirt;
            px(cx, cy, c); px(cx + 3, cy, c); px(cx, cy + 3, c); px(cx + 3, cy + 3, c);
        }

        static int contrast(int c) {
            int l = ((c >> 16) & 0xFF) * 3 + ((c >> 8) & 0xFF) * 6 + (c & 0xFF);
            return l > 1300 ? 0xFF1E1E28 : 0xFFFFFFFF;
        }

        /** Capas exteriores: gorra, capucha, delantal, chaqueta abierta, gabardina. */
        void overlays() {
            switch (role) {
                case "cashier" -> {
                    cap(0xFF2E9C5A, 0xFFFFFFFF);
                    // delantal verde de la tienda con bolsillo
                    rect(21, 37, 6, 10, 0xFF2E9C5A);
                    rect(22, 42, 4, 2, 0xFF43C463);
                    rect(20, 36, 1, 4, 0xFF2E9C5A); rect(27, 36, 1, 4, 0xFF2E9C5A);
                }
                case "guard" -> {
                    cap(0xFF1E2636, 0xFFFFD23F);
                    rect(16, 36, 24, 12, 0); // sin chaqueta
                    ringRows(16, 32, 8, 4, 0, 1, 0xFF1E2636);
                }
                case "black_market" -> {
                    // capucha: toda la cabeza menos la cara
                    int hood = 0xFF22222C;
                    rectN(40, 0, 8, 8, hood);
                    rectN(48, 0, 8, 8, shade(hood, -6));
                    rectN(32, 8, 8, 8, hood);
                    rectN(48, 8, 8, 8, hood);
                    rectN(56, 8, 8, 8, hood);
                    rect(40, 8, 8, 3, hood);
                    rect(40, 11, 1, 5, hood);
                    rect(47, 11, 1, 5, hood);
                    // gabardina
                    jacket(0xFF15151C, false);
                }
                case "wholesaler" -> {
                    if (r.nextBoolean()) cap(0xFF4A3A2A, 0xFFF2C14E);
                    jacket(0xFF5A4030, true);
                }
                case "trader" -> {
                    jacket(SHIRTS[r.nextInt(SHIRTS.length)], true); // chaleco de coleccionista
                    rect(40, 32, 16, 16, 0);
                    rect(48, 48, 16, 16, 0);
                    if (r.nextFloat() < 0.5f) cap(SHIRTS[r.nextInt(SHIRTS.length)], 0xFFFFFFFF);
                }
                case "scalper" -> {
                    // sudadera con capucha bajada y gorra hacia atrás
                    jacket(shade(0xFF3A3A44, r.nextInt(20)), false);
                    cap(0xFF1E1E22, 0xFFE33B3B);
                }
                case "beggar" -> {
                    if (r.nextBoolean()) { // gorro de lana
                        int b = shade(SHIRTS[r.nextInt(SHIRTS.length)], -30);
                        rectN(40, 0, 8, 8, b);
                        for (int x = 32; x < 64; x++) for (int y = 8; y < 11; y++) px(x, y, b);
                    }
                }
                default -> { // clientes: gorra o chaqueta a veces
                    float f = r.nextFloat();
                    if (f < 0.45f) cap(SHIRTS[r.nextInt(SHIRTS.length)], r.nextBoolean() ? 0xFFFFFFFF : 0xFFE33B3B);
                    else if (f < 0.55f) { // diadema / cinta
                        int b = SHIRTS[r.nextInt(SHIRTS.length)];
                        for (int x = 32; x < 64; x++) px(x, 9, b);
                    }
                    if (r.nextFloat() < 0.3f) jacket(SHIRTS[r.nextInt(SHIRTS.length)], true);
                }
            }
        }

        /** Gorra en la capa del sombrero con un logotipo de dos colores al frente. */
        void cap(int c, int logo) {
            rectN(40, 0, 8, 8, c);
            for (int y = 8; y < 11; y++) {
                rect(32, y, 8, 1, c);
                rect(40, y, 8, 1, y == 10 ? shade(c, -25) : c);
                rect(48, y, 8, 1, c);
                rect(56, y, 8, 1, c);
            }
            px(43, 8, logo); px(44, 8, logo); px(43, 9, logo == 0xFFFFFFFF ? 0xFFE33B3B : 0xFFFFFFFF); px(44, 9, logo == 0xFFFFFFFF ? 0xFFE33B3B : 0xFFFFFFFF);
        }

        /** Chaqueta en la capa exterior; open = abierta por delante (se ve la camiseta y el emblema). */
        void jacket(int c, boolean open) {
            // cuerpo (16,32): frente 20..27 x 36..47
            for (int y = 36; y < 48; y++) {
                for (int x = 16; x < 40; x++) px(x, y, shade(c, r.nextInt(5) - 2));
                if (open) for (int x = 22; x < 26; x++) px(x, y, 0);
            }
            for (int x = 20; x < 36; x++) for (int y = 32; y < 36; y++) px(x, y, c);
            // mangas
            for (int y = 36; y < 46; y++) for (int x = 40; x < 56; x++) px(x, y, shade(c, -6));
            for (int y = 52; y < 62; y++) for (int x = 48; x < 64; x++) px(x, y, shade(c, -6));
            for (int x = 44; x < 44 + aw * 2; x++) for (int y = 32; y < 36; y++) px(x, y, c);
            for (int x = 52; x < 52 + aw * 2; x++) for (int y = 48; y < 52; y++) px(x, y, c);
        }
    }
}
