# MasterBall Traders Co. sin ScalperTCG

> **Hecho el 2026-11-22 (versión 0.2.0 + MasterBall Traders API 0.1.0).** Las cartas y sobres son ítems de la API
> (`masterball:*`), con imágenes descargables, contenido de sobres propio y conversión automática de los ids
> `cobbletcg:*` (registro de ítems, Binder, colección, favoritos, vitrinas, pedidos de Grado) y rescate de los álbumes
> colocados. ScalperTCG queda marcado como incompatible. Lo que sigue es el plan original, como referencia.

Objetivo: que MasterBall Traders Co. funcione solo, con sus propias cartas y sobres, usando las imágenes de los
resource packs externos (que instalará **MasterBall Traders API**) sin incluir ninguna imagen en el jar.

Inventario hecho el 2026-11-22 sobre ScalperTCG 1.6.0 (`cobbletcg`).

## Qué usamos hoy de ScalperTCG

| Pieza | Dónde | Dificultad |
|---|---|---|
| 8.084 ítems de carta `cobbletcg:<set>/cards/<prefijo><nnn>` (41 sets) | `card/CardInfo.java` (`NS = "cobbletcg"`), ~200 llamadas en 50 archivos | Media: casi todo pasa por `CardInfo` |
| 154 sobres: 41 `sealed_`, 41 `riped2_`, 35 `riped3_`, 37 `riped4_` | `CardInfo.packTier`, 15 archivos que buscan `"/sealed_"` en el registro | Media |
| Apertura de sobres: sus mixins de `Item.use` + 563 loot tables (sellado → normal/semi-dios/dios 96/3/1; 5 cartas por rareza) | `pack/PackOpener.java`, `pack/PackCapture.java`, `mixin/PlayerInventoryMixin.java` | **Alta** |
| Modelos y render de cartas (no hay JSON por carta: `card_template` + renderer propio) | `client/render/ShopRenderers.java` (`FRAME_CARD_H`, `frameCard`), `mixin/client/ItemRendererMixin.java` | **Alta** |
| Nombres de las cartas (13 idiomas, con fallos: `tforce`/`tforces`, 328 cartas sin nombre) | `stack.getName()` en todas partes | Media |
| Álbumes (bloques, `AlbumScreenHandler`, `PageChangePayload`) | `client/binder/PaniniAlbumScreen.java`, `mixin/client/HandledScreensMixin.java`, `mixin/ServerPlayerEntityMixin.java` (compilan contra sus clases) | Alta |
| Logros de colección de `cobbletcg` y 14 que corregimos | `advancement/CardAdvancements.java`, `data/cobbletcg/advancement/` | Baja |
| 56 logros propios con iconos `cobbletcg:` | `data/tcgshop/advancement/` (`tools/gen_advancements.py`) | Baja |
| Activación automática de los 8 resource packs | Su `MixinResourcePackManager` | Baja (la hará MasterBall Traders API) |
| Tags de rareza `all*` | Ya no: desde el 2026-11-22 la rareza sale de `tcgshop_rarity/cards.json` | Hecho |

Lo que **ya no depende** de ScalperTCG: rarezas, precios, Grado, motor TCG (twinleaf), especies, tienda y NPCs.
Todos los datos de cartas están en `data/tcgshop/*` con ids `cobbletcg:...`; `tcgshop_rarity` ya usa la clave
sin namespace (`<set>/<hoja>`).

## Fases

### 0. Datos propios (hecho el 2026-11-22)
- `data/tcgshop/tcgshop_cards/catalog.json` (`tools/gen_card_catalog.py`): **72 sets, 12.572 cartas** con nombre,
  número impreso, código de set y grupos de TCGplayer. Los nombres e ids salen de ScalperTCG 1.7.0 (CC0) usado sólo
  como referencia: el jar no está instalado y no se copia ningún asset.
- `tools/fetch_rarities.py` ya lee el catálogo, no el jar: rareza oficial, nombre oficial y precio por variante de
  los 72 sets (11.644 de 11.766 cartas del TCG físico emparejadas con TCGplayer). Los 4 sets de TCG Pocket
  (Genetic Apex, Mythical Island, Mega Rising, Crimson Blaze) no están en TCGplayer: rareza por los tags de ScalperTCG
  y precio por la mediana de su rareza.
- Sets nuevos respecto a 1.6.0 (31): Jungle, Fossil, Base Set 2, Gym Heroes, Neo Genesis, Neo Discovery, Expedition,
  Aquapolis, Skyridge, EX Ruby & Sapphire, EX Sandstorm, Diamond & Pearl, Mysterious Treasures, Platinum, Rising Rivals,
  HeartGold & SoulSilver, Unleashed, Black & White, Emerging Powers, XY, Flashfire, Sun & Moon, Guardians Rising,
  Sword & Shield, Rebel Clash, Delta Reign (ME06), 30th Celebration y los 4 de Pocket. Todos tienen imágenes en los
  packs 1-8 salvo **30th Celebration** (243 cartas sin imagen todavía).
- Cuidado: los nombres de ScalperTCG no son fiables en todos los sets (Perfect Order 89+ corridos también en 1.7.0,
  Roaring Skies con ids desplazados). Usar `tcg_name` de `tools/rarity_raw.json` y, si hay duda, mirar el PNG.

### 1. Registro propio de cartas y sobres
- Registrar a partir de `catalog.json` (nombre oficial de TCGplayer cuando el del catálogo no cuadre).
- Registrar `tcgshop:<set>/cards/<hoja>` y `tcgshop:<set>/sealed_<set>` / `riped2-4_`. Mismo formato de ruta, así
  `CardInfo` sólo cambia `NS`.
- Nombres traducibles con claves propias (`item.tcgshop.<set>.cards.<hoja>`) generadas por script.
- Las texturas siguen en `cobbletcg:texturas/cards/<set>/<hoja>.png` (los packs no cambian).

### 2. Modelos y render propios
- `ModelLoadingPlugin` que resuelve cualquier `/cards/` a nuestro `card_template` (modelo nuestro, no copiado) y
  un `BuiltinItemRenderer` que dibuja la textura del pack, con carta por defecto si falta.
- Ajustar `ShopRenderers.FRAME_CARD_H`/`frameCard` e `ItemRendererMixin` a la geometría nueva.

### 3. Apertura de sobres propia
- Contenido de cada sobre generado desde nuestros datos (rareza oficial + estructura real del sobre de cada era),
  no copiando sus loot tables. `PackOpener` deja de necesitar `PackCapture` y el mixin de `offerOrDrop`.
- Mantener normal/semi-dios/dios (96/3/1) y añadir las variantes de impresión (ya hechas en `PrintVariant`).

### 4. Migración de mundos
- Datos propios con ids de texto (Binder, colección, Binder personal, favoritos): reescribir `cobbletcg:` →
  `tcgshop:` al cargar.
- `ItemStack` guardados (vitrinas, pedidos de Grado, inventarios, cofres): mixin que remapea ids `cobbletcg:*`
  desconocidos a `tcgshop:*` al decodificar.
- Álbumes de ScalperTCG con cartas dentro: convertir su contenido a nuestro Binder la primera vez que se abran o
  avisar antes de quitar ScalperTCG.

### 5. Álbumes, logros y limpieza
- Sustituir el álbum Panini por uno propio (ya tenemos Binder General y Personal).
- Iconos de logros a `tcgshop:`; quitar `data/cobbletcg/*`, `fix_rarity_tags.py`, `fix_cobbletcg_advancements.py`.
- `fabric.mod.json`: quitar `depends.cobbletcg` y declarar `breaks`/conversión si ambos están instalados.
- Repuntar `tools/*.py` a nuestra lista de cartas en vez del jar de ScalperTCG.

### 6. Extra
- Los packs 7 y 8 traen 30 sets que ScalperTCG 1.6.0 no registra (Jungle, Fossil, Neo, EX, DP, HGSS, BW, XY…,
  4.373 imágenes). Con registro propio podemos añadirlos generando sus datos desde TCGplayer.

## MasterBall Traders API (instalador de resource packs)
- Descarga los packs desde tu servidor la primera vez, comprueba su hash SHA-256 y los deja en `resourcepacks/`.
- Los activa automáticamente (lo que hoy hace ScalperTCG con su mixin).
- Muestra progreso en el menú principal, permite reanudar descargas y no bloquea el juego si no hay conexión.
- Ojo: las imágenes de las cartas son propiedad de The Pokémon Company / Nintendo / Creatures / Game Freak y de sus
  ilustradores. Alojarlas tú es donde está el riesgo legal (DMCA): conviene que la API sólo descargue y que el
  alojamiento y la responsabilidad estén claros.
