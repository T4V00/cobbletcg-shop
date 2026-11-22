# MasterBall Traders Co. — add-on para CobbleTCG / ScalperTCG 1.6.0

Mod Fabric 1.21.1 que **depende** de `cobbletcg` (no lo modifica ni redistribuye sus
assets; su licencia es CC BY-NC 3.0). Se integra con CobbleDollars (opcional, por reflexión).

## Cómo se engancha a cobbletcg (descubierto inspeccionando el jar)

| Cosa | En cobbletcg | Uso en el add-on |
|---|---|---|
| Carta | ítem `cobbletcg:<set>/cards/<id>`; nombre en lang `"<CÓDIGO> <nº>  <nombre>"` | `CardInfo` |
| Rareza | tags `cobbletcg:allcommons`, `alldoublerares`, `allalternative`, `allilustrationrare`, `allspecialilustrationrare`, `allhyperrare` | `Rarity.of(stack)` |
| Textura | `cobbletcg:texturas/cards/<set>/<id>.png` (resource packs 1-8) | `CardInfo.texture()` |
| Sobre | `sealed_<set>` → (usar) → `riped2/3/4_<set>` (normal / semi-dios / dios) → (usar) → 5 cartas vía `PlayerInventory.offerOrDrop` | Mixins `ServerPlayerInteractionManagerMixin` + `PlayerInventoryMixin` |
| Dinero | CobbleDollars: `CobbleDollarsPlayer.cobbleDollars$getCobbleDollars()` (BigInteger) | `CobbleDollarsBridge` |

---

## Precios — decisión final (cerrado)

### Fuente de datos: tcgcsv.com (TCGplayer)
Evaluadas:

| Fuente | Veredicto |
|---|---|
| **tcgcsv.com** (espejo diario de la API de TCGplayer) | ✅ **Elegida.** Gratis, sin clave, actualización diaria. Cubre **los 41 sets** de cobbletcg (Base Set 1999 → Chaos Rising / Pitch Black 2026) **y productos sellados** (sobre, caja, ETB). |
| pokemontcg.io | ❌ Obsoleta: registro cerrado, las claves dejan de funcionar el 1-mar-2027 (sucesora de pago: Scrydex). No tiene los sets de 2026. |
| TCGdex | Respaldo posible (TCGplayer + Cardmarket, gratis). No hace falta hoy. |
| Scrydex / PokémonPriceTracker / JustTCG | De pago, o con límites. Innecesarias. |

**Resultado medido:** precio real para **7.700 de 7.756 cartas (99,3%)** y precios de sellados para 38 sets.
Ejemplos: Umbreon VMAX alt (EVS 215) 2.211 USD · Charizard (BS 4) 1.001 USD · mediana global 0,39 USD.

### Pipeline
1. `tools/fetch_prices.py <cobbletcg.jar>` (cuando haya una versión nueva de cobbletcg, o para refrescar precios):
   empareja por código de set + número + nombre, con respaldos (sólo nombre, sufijos IR/SIR, "Mega X"↔"M X", "X Radiant"↔"Radiant X", promos ETB/UPC en grupos de promos).
2. Escribe `data/tcgshop/tcgshop_prices/cards.json` dentro del mod (~340 KB):
   `cards_usd`, `sealed_usd` (pack/box/etb por set), `rarity_median_usd` (mediana real por set+rareza y global `*`).
3. **El mod nunca se conecta a internet**: el servidor funciona offline, sin claves, sin límites de peticiones y con precios iguales para todos.
   Cualquier datapack puede sobreescribir precios (`data/<ns>/tcgshop_prices/*.json`; los archivos se fusionan y el último gana).

### Cálculo en el juego
```
precioBaseUSD = real de la carta
             ?? mediana(set, rareza) × variación estable por carta (0,4x–2,5x)
             ?? mediana global de la rareza
precio₽       = precioBaseUSD × tendenciaMercado(día) × cobbleDollarsPerUsd
```
- **Mercado dinámico (`MarketModel`)**: ruido suave determinista = ola lenta por set (hype de colección, ~9 días)
  + ola rápida del set + oscilación propia de la carta (más fuerte cuanto más rara). Rango medido ×0,68–×1,52, media ×1,01,
  como máximo un 20% de cambio en un día. El cliente lo calcula igual que el servidor (misma semilla): sólo se envía el número del día.
  Así tenemos **historial sin guardar nada**, listo para las gráficas del teléfono.
- **Día de mercado** = tiempo total del mundo / `marketDayTicks` (24000 por defecto; `/time set` no lo altera).
- **Conversión**: `cobbleDollarsPerUsd = 250`, calibrado con la tienda de CobbleDollars (Poké Ball 400 ₽, HP Up 2.500 ₽):
  sobre moderno 1.250–2.500 ₽ · común ~70 ₽ · carta de 100 USD = 25.000 ₽ (élitra en el banco).
  ⚠️ cobbletcg vende el bundle (6 sobres) a 100.000 ₽ (`config/cobbletcg.properties`); para que sea coherente, ~7.500 ₽.
- Config (`config/tcgshop.json`): `cobbleDollarsPerUsd`, `currencySuffix`, `marketEnabled`, `marketVolatility`, `marketDayTicks`, `marketSeed`.
- API para el resto del mod: `PriceService.priceCd(stack)`, `priceCdOnDay(...)` (historial), `sealedCd(set, PACK|BOX|ETB)`, `hasRealPrice(id)`.

Más adelante se multiplica sobre esta base: **gradeo** (PSA 10 ≈ ×2–×10 según rareza), estado de la carta y eventos del mercado.

---

## Referencias de diseño

### TCG Card Shop Simulator → cómo lo llevamos a Minecraft
| Mecánica del juego | En el add-on |
|---|---|
| Comprar stock al por mayor en una app (cajas, sobres), entrega en cajas | App **Mercado** del teléfono → paquete de reparto en un bloque **Buzón de la tienda**. Precio mayorista = `sealedCd × 0,7`. |
| Estanterías con capacidad; reponer desde la caja | Bloque **Estantería de sobres** (muestra los sobres en 3D, capacidad limitada); **Vitrina** para cartas sueltas (render de la textura de la carta). |
| Fijar precios contra el precio de mercado (app TCG Price) | **Etiquetadora**: clic en la estantería o vitrina → GUI con el precio de mercado de referencia. |
| Probabilidad de compra según el margen: ≤ mercado ~90%, +10% ~75%, +30% ~45%, +40% ~15%, > +60% ≈ 0 | Misma curva para los **NPC clientes** (configurable). |
| Clientes con presupuesto, que recorren estanterías, hacen cola en caja; tipos (normal / adinerado ~5%) | Entidad **Cliente** (geckolib) con presupuesto en ₽, ruta entre estanterías, cola en el **Mostrador**, paga con CobbleDollars. |
| Clientes que te ofrecen cartas al 70–120% del mercado | Evento: el NPC ofrece una carta; aceptas o rechazas (la tienda compra cartas sueltas). |
| Mesas de juego (más interés de noche), eventos que suben una categoría | **Mesa de juego** de pago por uso (ingreso pasivo); eventos semanales que suben un set (encaja con la ola por set del mercado). |
| Abrir sobres a mano da mucha más XP que con máquina | XP de tienda por abrir a mano (la animación) > abridora automática. |
| Nivel de tienda que desbloquea productos, trabajadores, ampliaciones; costes diarios (electricidad, caja) | **Nivel de tienda** (XP por ventas): desbloquea sets en el mercado, más clientes, empleados. Gastos diarios opcionales. |
| Empleados (caja, reponer, ajustar precios) con salario diario | NPC **Empleado** contratable con tareas: caja / reponer / reetiquetar al precio de mercado. |
| Gradeo (+15–20% de valor medio) | **Servicio de gradeo** (enviar → esperar N días de mercado → carta en carcasa con nota 1–10 y multiplicador). |
| Bulto de comunes en el banco de trabajo | **Caja de bulto**: compactar comunes para vender por lotes. |
| Juego de cartas contra clientes, torneos | Fase PvP (futuro). |

### Pokémon TCG Pocket → la sensación al abrir y coleccionar
| Mecánica | En el add-on |
|---|---|
| Sobre de 5 cartas, deslizar para cortar, revelar una a una, la mejor al final | ✅ Hecho (Fase 1). |
| Raras con arte especial, efectos y suspense | ✅ Boca abajo con brillo de anticipación, volteo, rayos, partículas, temblor y holo. Pendiente: "arte inmersivo" (zoom/paralaje de la ilustración en 2 capas) para SIR/Hyper. |
| God pack (~0,05%, todas las cartas ≥1★) | cobbletcg ya tiene sobres semi-dios/dios → presentación especial dorada ✅. |
| Puntos de sobre canjeables por cartas concretas | **Puntos de sobre** por cada sobre abierto → canjear una carta concreta en la app (coste según rareza). |
| Wonder Pick (elegir 1 carta de un sobre que abrió otro jugador; barajado visual) | **Elección sorpresa**: tras un pull raro de otro jugador, una elección entre 5 cartas barajadas (con cooldown). Muy multijugador. |
| Intercambio (misma rareza, coste en polvo) | Intercambio entre jugadores con GUI de confirmación; más adelante, reglas tipo "misma rareza". |
| Binder y tablero para enseñar cartas a amigos | **Binder portátil** (Fase 4) + **Tablero de exhibición** colgable en la pared para presumir de las mejores cartas. |
| Fundas, tapetes, decoración | Cosméticos de tienda (fundas que cambian el reverso de la carta en la animación). |

---

## Fases

### Fase 1 — Apertura estilo TCG Pocket + precios ✅
- Pantalla de apertura (rasgar el sobre, mazo, volteo, holo, partículas, temblor), resumen final con arrastrar, ordenar y valor total.
- Sonidos `tcgshop:*` reemplazables. Anuncio de pulls raros en el chat.
- Precios reales + mercado dinámico (sección anterior). `/tcgshop price` (con tendencia de 7 días), `/tcgshop value`.

### Fase 1.5 — Binders, logros y Scalper ✅
- **Binder General** (`tcgshop:general_binder`): colección única por jugador (Fabric Data Attachment, persiste y sobrevive a la muerte).
  Clic derecho o tecla **G** (también equipado en Accessories: slots belt/charm). Si lo llevas, las cartas de los sobres van directas a él.
  Pestañas por pack y por rareza (global o dentro del pack), buscador, huecos de cartas que faltan, notificaciones ★
  (se quitan al pasar el cursor; doble clic en pestaña limpia todas), panel de detalle, sacar/guardar cartas.
- **Personal Binder** (`tcgshop:personal_binder`): páginas de 9 bolsillos sin límite fijo (hasta 200), arrastrar y soltar desde el
  Binder General, pasar página animado. Personalización desbloqueable por logros: 16 paletas, 12 patrones (tipos Pokémon),
  10 fondos procedurales inspirados en biomas de Cobblemon.
- **Álbumes de set de cobbletcg** con pantalla Panini (mismo ScreenHandler original): huecos numerados en orden oficial con nombre.
- **Logros**: 36 nuevos (`tcgshop:*`) + 14 logros de colección de cobbletcg que venían rotos, reparados (`tools/fix_cobbletcg_advancements.py`).
  Las cartas cuentan **al abrir cualquier binder** (se otorgan los criterios por carta de cobbletcg); no hay rastreador aparte.
- **Datos curiosos** estilo TCG Pocket en 50 hitos de cartas distintas (máx. 1 por apertura de binder).
- **Apertura en grupo**: 1/5/10/15/20 sobres por rasgada, un trazo corta varios; sobres con carta de alta rareza brillan y suenan distinto.
- **Scalper** (`tcgshop:scalper`): revendedor hostil. Ataca si te ve abriendo sobres; si no, ofrece un sobre a 2,5–4× el precio;
  si rechazas, ataca. Si te golpea, roba un montón de sobres/cartas del inventario y huye. Daño 7 (espada de diamante),
  armadura 20 / dureza 12 / empuje 0,4 (netherita completa) sin equipo visible. Aparece de noche (peso 3).

### Fase 2 — Mega actualización de tienda ✅
- **Binders únicos**: cada Binder General tiene id + dueño (componente `tcgshop:binder_tag`); contenido en `BinderRegistry`
  (data/tcgshop_binders.dat). Un segundo binder registrado empieza vacío; los ajenos no se abren. Personal Binder también se vincula.
- **Deseadas** (♥) en el Binder General, pestaña propia, y huecos de deseadas en el Personal Binder.
- **Títulos por Pokémon** (Fan 5 · Entusiasta 15 · Obsesionado 40 · Totalmente demente 100 cartas sacadas), visibles sobre el nombre;
  mapa carta→especie generado desde Cobblemon (`tools/gen_card_species.py`). 4 logros genéricos + 48 de 12 Pokémon destacados.
- **Guantes de friki**: Espacio abre los sobres sólo con ellos (slot hand de Accessories o mano secundaria).
- **Avisos de logro con descripción** (sustituye el toast vanilla).
- **Scalper**: se para a admirar el botín (bocadillo 💭 dinero/corazón), se defiende si le pegas, sólo desaparece si te alejas >64 bloques o mueres.
- **Zonas de tienda** por niveles (Madera 16 → Netherita 100), protegidas en X/Y/Z (−16/+48), muebles sólo dentro de tu zona.
- **Muebles**: vitrina (4), expositor (1), estantería de sobres (6) con productos renderizados y precio; caja registradora (cobrar).
- **Cajas de sobres** 3D con el arte del set (36 sobres). **Mayorista misterioso** (aparece cada medio día con 35%): cajas al por mayor,
  bloques de tienda, muebles y herramientas. **Gafas de Gerente**: bordes de tiendas + panel de ingresos/gastos/beneficio.
  **Teléfono TCG**: Mercado online, Precios (gráfica 30 días), Colección, Mi tienda, Títulos, Logros.
- **PokéCoins fuera**: el ATM de cobbletcg queda desactivado; los sobres se compran con Pokédólares.

### Fase 3 — Tienda viva ✅
- **Clientes NPC** con nombre real, skin vanilla y título opcional (%): normales, exigentes, VIP (pagan de más), regateadores,
  vendedores (te ofrecen cartas: aceptar/rechazar) y ladrones. Deciden por precio vs. mercado y reputación. 467 frases (224 razones de venta).
- **Compra directa**: apuntar a un producto lo resalta y muestra nombre/precio; clic derecho cobra y el dinero va a la caja (también en tu tienda).
- **Cajero** (sin él sólo se venden cartas sueltas) y **guardia** (patrulla, detiene ladrones, recupera cartas), contratados desde el teléfono, con sueldo diario.
- **Reputación** 0–100 con desglose (precios, variedad, expositores, decoración, seguridad, atención, satisfacción, robos, boca a boca).
- **Muebles con modelo propio**: expositor, vitrina-mostrador, estantería de pared, góndola doble cara (pasillos), palé de cajas, pedestal giratorio,
  caja registradora animada, póster enmarcado, mesa de juego, detector antirrobo (alarma), Ruleta Poké (rojo/negro, sólo por teléfono), caja misteriosa.
- **Teléfono** rediseñado: Mercado con categorías y orden (todo lo del Mayorista + Ruleta), Precios con filtros de set/rareza y gráfica,
  Mi tienda (resumen, personal, seguridad, reputación), Títulos, Colección y Logros.
- **Intercambio de cartas** desde la rueda de interacción de Cobblemon (o `/tcgtrade <jugador>`): misma rareza, hasta Ilustración Rara,
  ambos confirman, cambio atómico revalidado en el servidor; se cancela al alejarse >12 bloques o desconectarse.
- 17 logros nuevos de tienda/intercambio (111 en total).

### Fase 4 — Binders y exhibición
Binder portátil con páginas animadas, 9 bolsillos, holo, filtros y % completado; bloque álbum de pie o en pared;
tablero de exhibición.

### Fase 3.5 — Tienda viva v2 ✅
- **Muebles grandes de varios bloques** (las cartas se ven del tamaño de un marco y se dibujan igual que en él, con la inclinación de cada mueble):
  expositor 2×2 (2 cartas), vitrina 3×1 (4), estantería 3×3 (12 sobres), góndola 3×2 doble cara (16), palé 2×2 (6 cajas),
  pedestal con urna 1×3 y **muro de cartas gradadas** 3×3 (12 slabs). Romper cualquier parte rompe el mueble entero.
- **Cajas de sobres de cartón** grandes con tapa, pliegues, canto ondulado, faja "36 SOBRES", plástico con brillo animado;
  al abrirlas (colocadas o desde la mano) se ve la animación: tapa, sobres dentro y sobres saliendo.
- **Clientes**: llegan andando desde fuera, cogen el producto, van a pagar a la caja y se van con él en la mano; dejan
  reseña (1-5★) que cuenta para la reputación; los contentos vuelven como **clientes habituales**. Los que quieren trato
  muestran un **«!»**; nuevo tipo **coleccionista** que propone trueques (su carta ⇄ una de tus expositores).
- **Ladrones**: con guardia siempre los detienen; sin guardia huyen como un Scalper (persigue, pega o combate).
- **Scalpers**: se pelean entre ellos y los monstruos de Minecraft les atacan. **Combates Pokémon** (Cobblemon) contra
  Scalpers y ladrones: si les ganas, caen.
- **Teléfono** rediseñado: inicio vertical con widgets; las apps giran a horizontal con carril lateral; pestaña de reseñas.

### Fase 4 — Competitivo y extras ✅
- **CobbleGrade**: gradeo 1.0-10 en décimas desde el teléfono, pedidos con tiempo, funda con nota en expositores, valor x0,4-x8.
- **Modo competitivo TCG** en las mesas de juego: reglas propias con valores sacados de la especie (Cobblemon) y la rareza;
  partidas de liga entre jugadores (ELO, rangos Bronce→Maestro) o contra la casa (IA); app Liga TCG con editor de mazos.
- **Duelos Pokémon entre NPC** (simulados con Pokémon reales: Cobblemon no monta combates sin jugador).
- **Mal olor** de clientes y tienda + **spray desodorante**. **Sobres en cofres** (1%). Expositores nuevos y **de casa**.

### Fase 4.5 — Pokémon TCG real ✅
- Motor de **twinleaf.gg** (MIT, basado en ryuu-play de keeshii, MIT) compilado con esbuild (`engine/build.js` -> `assets/tcgshop/ptcg/engine.js`)
  y ejecutado con **GraalJS** dentro del mod (jar-in-jar), en su propio hilo. Reglas oficiales y efectos reales: 60 cartas, 6 premios,
  evolución, Energía por tipo, condiciones especiales, Partidarios/Estadios/Herramientas. 15.370 cartas en el motor; 6.051 de cobbletcg emparejadas
  (`tools/gen_ptcg_map.py`). La casa usa el bot de twinleaf. Textos del motor desde su i18n (`tools/gen_lang_ptcg.py`).
- Sustituye al modo simplificado anterior (mesas, liga ELO y app Liga TCG con editor de mazos de 60).

### Fase 4.6 — Economía v3, venta rápida y Coleccionista ✅
- **Economía rebalanceada** para que la tienda dé beneficio: muebles ~3-4x más baratos (estantería 16k, vitrina 18k, teléfono 25k),
  bloques de tienda 15k → 2M, cajero 30k + 4k/día, guardia 45k + 6k/día. Mayorista al 60% del mercado, Mercado del teléfono al 80%.
  Más clientes (0,2 + rep/180 por intento) y más dispuestos a comprar a precio de mercado.
- **Venta rápida** en el teléfono (50% del valor, con doble clic de confirmación) y al **Mayorista** (40%), que además compra
  **esmeraldas** por CobbleDollars (`emeraldPriceCd`, 300 por defecto) y lo recomienda al abrir su puesto.
- **Coleccionista ambulante** (`tcgshop:trader`, pacífico, 40% cada medio día): 6 cartas; las cambia por tuyas de ≥85% de su valor
  y se encapricha de una tuya que paga al 115%. 4 tratos por visita.
- **Kit de inicio**: 5 sobres sellados al azar la primera vez que entras (`starterPacks`, `starterPackCount`).
- **Sobres**: abrir 50 o 100 a la vez (rejilla ancha, sonido de corte sin saturar).
- **Teléfono**: monedero en la barra de estado, filtro por rango de precio y orden en Precios, iconos que caben en pantallas bajas.
- **Duelo**: bots con pausa de ~1,9 s entre jugadas (4,5 s antes de la primera); presentación "VS"; **moneda 3D** (`Coin3D`, canto estriado,
  caras con relieve, estilos oro/plata/Master listos para monedas coleccionables); herramientas que asoman tras el Pokémon con su nombre;
  pila de descartes desordenada y vista completa con pestañas tú/rival; la mano se recoge cuando no la usas y deja ver la Banca.

### Fase 4.7 — Expositores, teléfono v2 y cosméticos de duelo ✅
- **Colocar productos**: con un producto en la mano, los huecos libres se marcan con esquinas; el apuntado muestra una vista previa
  translúcida, "Colocar aquí" y el precio de mercado. Al colocar, el producto cae con rebote, gira y suelta chispas. Clic derecho con
  el mismo producto sobre una casilla = **reponer** (se suma al montón). Varias unidades se ven apiladas (sobres en fila hacia el fondo,
  cartas en abanico, cajas una detrás de otra), cada producto un pelín girado, y etiquetas de precio de papel con "×N".
- **Teléfono v2**: carcasa metálica redondeada con halo del color de la app, isla de cámara, fondo aurora con estrellas, iconos
  redondeados con brillo, widgets de cristal, barra superior con sombra, carril con indicador animado, esquinas de pantalla recortadas.
- **Botones y chips** (`Ui.button/chip/progress`, botones del duelo): esquinas redondeadas reales, reflejo de cristal, halo al pasar
  el ratón y se hunden al pulsar. Afecta a todas las pantallas del mod.
- **Cosméticos de duelo** (`duel/Cosmetics`, guardados en la Liga): **27 monedas 3D** (14 emblemas: Poké Ball, estrella, rayo, llama,
  gota, hoja, luna, sol, corona, gema, copo, corazón, espiral, escudo; efectos arcoíris y destellos) y **21 fondos de tablero HD**
  generados por código (512 px en mosaico continuo, `DuelBackgrounds`). Se desbloquean por rango (mejor rango alcanzado), puntos,
  victorias, victorias a la casa, partidas, rachas, sobres abiertos, o como botín raro al abrir sobres (1/140 por sobre).
  Teléfono → Liga → Monedas / Fondos / Logros para verlos y equiparlos. En el duelo cada uno ve su fondo y cada lanzamiento usa la
  moneda de quien lanza (la casa usa una según su nivel).

### Fase 4.8 — Economía configurable, comerciantes y reputación por entorno ✅
- **Configuración**: `config/tcgshop-economy.json` (precios fijos, empleados, factores, premios, gradeo, demanda, probabilidades, bloqueo de sets, espera de clientes, rechazos, pesos de reputación, mala fama, anuncios de logros) y `config/tcgshop-vanilla-prices.json` (tabla completa de objetos vanilla). `/tcgshop reload` aplica todo sin reiniciar y lo envía a los clientes. Si `tcgshop.json` está roto, no se aplica y se avisa a los operadores.
- **Precios**: `tools/fetch_prices.py` corregido (productos antiguos "[Unlimited Edition]", sólo ventas reales, variante Normal/Unlimited antes que reverse o 1ª edición). Sellado sin dato = sobre × 36 (caja) / × 10 (ETB).
- **Sets top 5** (`lockTopPackSets`): sólo se compran con tienda Diamante+ (`lockedPacksMinShopTier`); 0,001% por cofre (`lockedPackChestChance`); nunca en kit de inicio, Mystery Box ni NPC.
- **Mayorista**: compra casi cualquier objeto vanilla (madera ≈ 0,5, hierro ≈ 1, máx. 1.000; renovables casi nada; bloques = 9 piezas). La esmeralda usa la tabla.
- **Comerciantes**: 6 personalidades, 120 motivos (`tcgshop.motive.*`), regateo con contraoferta y argumentos (mercado, otro sitio, sin dinero, última oferta, pensarlo), paciencia, reseña neutral/negativa al rechazar con tope diario.
- **Clientes**: 60 s de espera con barra; sin atender penaliza. Comentarios del entorno y recomendaciones (5★ recientes traen más clientes).
- **Reputación**: Estética, Calidad/precio y Seguridad (0-100) + total ponderada. Detecta techo, paredes, luz, puertas, monstruos, animales, mascotas y Pokémon (sólo en la zona con muebles).
- **Mala fama**: matar NPC de la tienda resta reputación (se recupera cada día) y los clientes que lo ven huyen.
- **Logros**: todos los del mod y de cobbletcg se anuncian en el chat; con Advancement Plaques no se reemplaza su aviso.

### Fase 4.9 — Fase B (parte 1) ✅
- **Expositores**: clic derecho del dueño sobre una carta → Tomar / Editar (precio, tamaño 50-180% y giro X/Y/Z, vista previa en vivo sobre el mueble, Restaurar). Se guarda por casilla (NBT "Look") y se sincroniza. Agachado = tomar al momento.
- **Cajeros**: dos puestos de caja (uno por caja registradora) + guardia; los cajeros sólo trabajan de día (6:00-18:30), se van a casa de noche y vuelven al amanecer; de noche el dueño cobra sobres/cajas si está junto a una caja. Panel de 3 tarjetas en el teléfono.
- **Mendigo de cartas**: ronda las tiendas; darle monedas o una carta mejora la fama ("charity"), a veces devuelve el favor; echarle la empeora.
- **Mercader negro**: rarísimo (`blackMarketChance`), aviso privado, ofrece un sobre (a veces de sets bloqueados) por un Pokémon del equipo; doble confirmación; sólo entrega si Cobblemon confirma que salió el Pokémon; nunca deja sin Pokémon.
- **Skins propias** para todos los NPC (client/render/NpcSkins), por rol, sin arte oficial.

### Fase 4.10 — Fase B (parte 2) ✅
- **Mercado del Binder** (teléfono → Vender): venta rápida de varias cartas a la vez desde el Binder General (con "conservar 1 de cada" y confirmación), favoritas protegidas, filtros por expansión/rareza/valor/cantidad, orden, valor total, comparación mercado vs venta, historial (60 operaciones) y ofertas a otro jugador con precio (la carta no sale de tu Binder hasta que el otro acepta y paga; `/tcgshop offer accept|deny`).
- **Amigos** (teléfono → Amigos): solicitudes, lista con conectado/desconectado, estadísticas (Liga, cartas únicas, sobres, ventas, facturado) y tiendas de cada amigo con coordenadas (sólo amigos). Datos en `tcgshop_social`.
- **Gestor de expositores** (teléfono → Expositores): todos tus productos expuestos con precio y diferencia con el mercado; -5%/+5%/mercado por producto y en bloque (90/100/110/125%).
- **Vitrina premium**: las cartas raras (tier 2+) brillan suavemente en los expositores.
- Pendiente: intercambio carta-por-carta directamente desde el Binder (hoy: sacar al inventario y /tcgtrade).

### Próximo — Liga online clasificatoria (diseño pendiente)
Rangos Master Ball 1-10 → Champion 1-3 → top 10-2 "Arceus' Champion" → top 1 "God of Games"; monedas 3D por rango/botín.
Límites reales a resolver antes de programar: P2P sin ningún servidor necesita igualmente un punto de encuentro (DHT pública tipo
BitTorrent, o jugar dentro del mismo servidor de Minecraft) y atravesar NAT; un ranking "top 10 global" sin servidor solo puede ser
eventual (gossip entre pares); y un rango guardado en el PC se puede ofuscar y firmar, pero no hacer imposible de editar
(lo verificable es firmar cada resultado con las claves de ambos jugadores y que los pares rechacen historiales inválidos).

### Fase 5 — Extras
Gradeo, empleados, eventos de mercado, cajas de bulto, elección sorpresa, intercambio, cosméticos, arte inmersivo.

### Futuro — PvP
Batallas de cartas entre jugadores (motor de reglas, mazos, mesa, torneos).

## Próximas actualizaciones

- **Eventos aleatorios:** en desarrollo en la 0.9 (motor por datos, 10 % de probabilidad diaria por dueño,
  incidentes que se arreglan, Pokémon de Cobblemon, reseñas que recuerdan lo que pasó).
- **Propiedades:** más adelante se podrán comprar construcciones de Minecraft con dinero; las estatuas de lujo
  de la 0.9 cambiarán y mejorarán según su tier.

### 0.9.5 (después de corregir la 0.9; luego se publica en GitHub y CurseForge)
- Empiezas con 5 sobres y un Binder General ya equipado en el slot de accesorios, a tu nombre.
- Empiezas con 5.000 de dinero, pero solo se entregan al abrir tu primer sobre.
- Evento único por jugador: tras jugar un rato aparece un NPC coleccionista único (no vuelve) que ofrece 5.000
  por una carta de tu colección que le gusta.
- El mayorista tiene más probabilidad de aparecer si aún no tienes tienda.
- A los 4 días de Minecraft sin tienda aparece un NPC que te tira una carta al azar de unos 15.000, te llama
  mendigo y suelta frases para fastidiarte y animarte a abrir una tienda.
- Skins mejores y muchas más, nombres comunes, y clientes separados por sexo (skins y nombres femeninos y
  masculinos).
- Tiers de ladrones según la popularidad de la tienda; los más altos pueden matar a tus guardias o a ti y robar
  todo el stock (MUY infrecuentes). Contratar y mejorar guardias se vuelve importante (añadir mejoras de guardia).
