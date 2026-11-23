# MasterBall Traders Co. — add-on for CobbleTCG / ScalperTCG 1.6.0

Fabric 1.21.1 mod that **depends** on `cobbletcg` (it does not modify it or redistribute its
assets; its license is CC BY-NC 3.0). It integrates with CobbleDollars (optional, via reflection).

## How it hooks into cobbletcg (found by inspecting the jar)

| Thing | In cobbletcg | Use in the add-on |
|---|---|---|
| Card | item `cobbletcg:<set>/cards/<id>`; lang name `"<CÓDIGO> <nº>  <nombre>"` | `CardInfo` |
| Rarity | tags `cobbletcg:allcommons`, `alldoublerares`, `allalternative`, `allilustrationrare`, `allspecialilustrationrare`, `allhyperrare` | `Rarity.of(stack)` |
| Texture | `cobbletcg:texturas/cards/<set>/<id>.png` (resource packs 1-8) | `CardInfo.texture()` |
| Pack | `sealed_<set>` → (use) → `riped2/3/4_<set>` (normal / semi-god / god) → (use) → 5 cards via `PlayerInventory.offerOrDrop` | Mixins `ServerPlayerInteractionManagerMixin` + `PlayerInventoryMixin` |
| Money | CobbleDollars: `CobbleDollarsPlayer.cobbleDollars$getCobbleDollars()` (BigInteger) | `CobbleDollarsBridge` |

---

## Prices — final decision (closed)

### Data source: tcgcsv.com (TCGplayer)
Evaluated:

| Source | Verdict |
|---|---|
| **tcgcsv.com** (daily mirror of the TCGplayer API) | ✅ **Chosen.** Free, no key, updated daily. Covers **all 41 sets** of cobbletcg (Base Set 1999 → Chaos Rising / Pitch Black 2026) **and sealed products** (pack, box, ETB). |
| pokemontcg.io | ❌ Obsolete: registration closed, keys stop working on 1-Mar-2027 (paid successor: Scrydex). Does not have the 2026 sets. |
| TCGdex | Possible fallback (TCGplayer + Cardmarket, free). Not needed today. |
| Scrydex / PokémonPriceTracker / JustTCG | Paid, or rate-limited. Unnecessary. |

**Measured result:** real price for **7,700 of 7,756 cards (99.3%)** and sealed prices for 38 sets.
Examples: Umbreon VMAX alt (EVS 215) 2,211 USD · Charizard (BS 4) 1,001 USD · global median 0.39 USD.

### Pipeline
1. `tools/fetch_prices.py <cobbletcg.jar>` (when there is a new cobbletcg version, or to refresh prices):
   matches by set code + number + name, with fallbacks (name only, IR/SIR suffixes, "Mega X"↔"M X", "X Radiant"↔"Radiant X", ETB/UPC promos in promo groups).
2. Writes `data/tcgshop/tcgshop_prices/cards.json` inside the mod (~340 KB):
   `cards_usd`, `sealed_usd` (pack/box/etb per set), `rarity_median_usd` (real median per set+rarity and global `*`).
3. **The mod never connects to the internet**: the server works offline, with no keys, no request limits and the same prices for everyone.
   Any datapack can override prices (`data/<ns>/tcgshop_prices/*.json`; files are merged and the last one wins).

### In-game calculation
```
basePriceUSD = the card's real price
             ?? median(set, rarity) × stable per-card variation (0.4x–2.5x)
             ?? global median for the rarity
price₽        = basePriceUSD × marketTrend(day) × cobbleDollarsPerUsd
```
- **Dynamic market (`MarketModel`)**: smooth deterministic noise = slow wave per set (collection hype, ~9 days)
  + fast set wave + the card's own oscillation (stronger the rarer it is). Measured range ×0.68–×1.52, mean ×1.01,
  at most a 20% change in one day. The client computes it the same way as the server (same seed): only the day number is sent.
  This gives us **history without storing anything**, ready for the phone's charts.
- **Market day** = total world time / `marketDayTicks` (24000 by default; `/time set` does not affect it).
- **Conversion**: `cobbleDollarsPerUsd = 250`, calibrated against the CobbleDollars shop (Poké Ball 400 ₽, HP Up 2,500 ₽):
  modern pack 1,250–2,500 ₽ · common ~70 ₽ · 100 USD card = 25,000 ₽ (elytra at the bank).
  ⚠️ cobbletcg sells the bundle (6 packs) for 100,000 ₽ (`config/cobbletcg.properties`); to be consistent, ~7,500 ₽.
- Config (`config/tcgshop.json`): `cobbleDollarsPerUsd`, `currencySuffix`, `marketEnabled`, `marketVolatility`, `marketDayTicks`, `marketSeed`.
- API for the rest of the mod: `PriceService.priceCd(stack)`, `priceCdOnDay(...)` (history), `sealedCd(set, PACK|BOX|ETB)`, `hasRealPrice(id)`.

Later, multipliers are applied on top of this base: **grading** (PSA 10 ≈ ×2–×10 depending on rarity), card condition and market events.

---

## Design references

### TCG Card Shop Simulator → how we bring it to Minecraft
| Game mechanic | In the add-on |
|---|---|
| Buy wholesale stock in an app (boxes, packs), delivered in boxes | Phone **Market** app → delivery package in a **Shop Mailbox** block. Wholesale price = `sealedCd × 0,7`. |
| Shelves with capacity; restock from the box | **Pack Shelf** block (shows the packs in 3D, limited capacity); **Display Case** for single cards (renders the card texture). |
| Set prices against the market price (TCG Price app) | **Price Labeler**: click the shelf or display case → GUI with the reference market price. |
| Purchase probability by markup: ≤ market ~90%, +10% ~75%, +30% ~45%, +40% ~15%, > +60% ≈ 0 | Same curve for **customer NPCs** (configurable). |
| Customers with a budget who browse shelves and queue at the checkout; types (normal / wealthy ~5%) | **Customer** entity (geckolib) with a ₽ budget, route between shelves, queue at the **Counter**, pays with CobbleDollars. |
| Customers who offer you cards at 70–120% of market | Event: the NPC offers a card; you accept or decline (the shop buys single cards). |
| Play tables (more interest at night), events that boost a category | Pay-per-use **Play Table** (passive income); weekly events that boost a set (fits the market's per-set wave). |
| Opening packs by hand gives much more XP than with a machine | Shop XP for opening by hand (the animation) > automatic opener. |
| Shop level that unlocks products, workers, expansions; daily costs (electricity, checkout) | **Shop level** (XP from sales): unlocks sets in the market, more customers, employees. Optional daily expenses. |
| Employees (checkout, restocking, adjusting prices) with a daily salary | Hireable **Employee** NPC with tasks: checkout / restock / relabel at market price. |
| Grading (+15–20% average value) | **Grading service** (send → wait N market days → card in a slab with a 1–10 grade and multiplier). |
| Bulk of commons at the workbench | **Bulk box**: compact commons to sell in lots. |
| Card game against customers, tournaments | PvP phase (future). |

### Pokémon TCG Pocket → the feel of opening and collecting
| Mechanic | In the add-on |
|---|---|
| 5-card pack, swipe to cut, reveal one by one, best one last | ✅ Done (Phase 1). |
| Rares with special art, effects and suspense | ✅ Face down with an anticipation glow, flip, rays, particles, shake and holo. Pending: "immersive art" (zoom/parallax of the illustration in 2 layers) for SIR/Hyper. |
| God pack (~0.05%, all cards ≥1★) | cobbletcg already has semi-god/god packs → special golden presentation ✅. |
| Pack points redeemable for specific cards | **Pack points** for every pack opened → redeem a specific card in the app (cost by rarity). |
| Wonder Pick (choose 1 card from a pack another player opened; visual shuffle) | **Surprise pick**: after another player's rare pull, a pick among 5 shuffled cards (with cooldown). Very multiplayer. |
| Trading (same rarity, dust cost) | Player-to-player trading with a confirmation GUI; later, "same rarity" style rules. |
| Binder and board to show cards to friends | **Portable binder** (Phase 4) + wall-mounted **Showcase Board** to show off your best cards. |
| Sleeves, playmats, decoration | Shop cosmetics (sleeves that change the card back in the animation). |

---

## Phases

### Phase 1 — TCG Pocket-style opening + prices ✅
- Opening screen (tear the pack, deck, flip, holo, particles, shake), final summary with drag, sort and total value.
- Replaceable `tcgshop:*` sounds. Rare pull announcements in chat.
- Real prices + dynamic market (previous section). `/tcgshop price` (with 7-day trend), `/tcgshop value`.

### Phase 1.5 — Binders, achievements and Scalper ✅
- **General Binder** (`tcgshop:general_binder`): one collection per player (Fabric Data Attachment, persists and survives death).
  Right-click or the **G** key (also when equipped in Accessories: belt/charm slots). If you carry it, cards from packs go straight into it.
  Tabs by pack and by rarity (global or within the pack), search, gaps for missing cards, ★ notifications
  (cleared on hover; double-click on a tab clears them all), detail panel, take out/store cards.
- **Personal Binder** (`tcgshop:personal_binder`): 9-pocket pages with no fixed limit (up to 200), drag and drop from the
  General Binder, animated page turning. Customization unlocked by achievements: 16 palettes, 12 patterns (Pokémon types),
  10 procedural backgrounds inspired by Cobblemon biomes.
- **cobbletcg set albums** with a Panini screen (same original ScreenHandler): numbered slots in official order with names.
- **Achievements**: 36 new ones (`tcgshop:*`) + 14 cobbletcg collection achievements that were broken, now fixed (`tools/fix_cobbletcg_advancements.py`).
  Cards count **when opening any binder** (cobbletcg's per-card criteria are granted); there is no separate tracker.
- TCG Pocket-style **fun facts** at 50 distinct-card milestones (max. 1 per binder opening).
- **Group opening**: 1/5/10/15/20 packs per tear, one stroke cuts several; packs with a high-rarity card glow and sound different.
- **Scalper** (`tcgshop:scalper`): hostile reseller. Attacks if he sees you opening packs; otherwise offers a pack at 2.5–4× the price;
  if you decline, he attacks. If he hits you, he steals a pile of packs/cards from your inventory and flees. Damage 7 (diamond sword),
  armor 20 / toughness 12 / knockback 0.4 (full netherite) with no visible gear. Spawns at night (weight 3).

### Phase 2 — Mega shop update ✅
- **Unique binders**: each General Binder has an id + owner (component `tcgshop:binder_tag`); contents in `BinderRegistry`
  (data/tcgshop_binders.dat). A second registered binder starts empty; other people's binders don't open. The Personal Binder is bound too.
- **Wishlist** (♥) in the General Binder, its own tab, and wishlist slots in the Personal Binder.
- **Per-Pokémon titles** (Fan 5 · Entusiasta 15 · Obsesionado 40 · Totalmente demente 100 cards pulled), shown above the name;
  card→species map generated from Cobblemon (`tools/gen_card_species.py`). 4 generic achievements + 48 for 12 featured Pokémon.
- **Nerd gloves**: Space opens packs only with them (Accessories hand slot or offhand).
- **Achievement notices with description** (replaces the vanilla toast).
- **Scalper**: stops to admire the loot (💭 money/heart bubble), defends himself if you hit him, only despawns if you go >64 blocks away or die.
- Tiered **shop zones** (Wood 16 → Netherite 100), protected in X/Y/Z (−16/+48), furniture only inside your zone.
- **Furniture**: display case (4), display stand (1), pack shelf (6) with rendered products and price; cash register (charge).
- 3D **pack boxes** with the set art (36 packs). **Mysterious Wholesaler** (spawns every half day with 35%): wholesale boxes,
  shop blocks, furniture and tools. **Manager Goggles**: shop borders + income/expenses/profit panel.
  **TCG Phone**: online Market, Prices (30-day chart), Collection, My shop, Titles, Achievements.
- **PokéCoins out**: cobbletcg's ATM is disabled; packs are bought with Pokédollars.

### Phase 3 — Living shop ✅
- **Customer NPCs** with a real name, vanilla skin and optional title (%): normal, picky, VIP (overpay), hagglers,
  sellers (offer you cards: accept/decline) and thieves. They decide by price vs. market and reputation. 467 lines (224 sale reasons).
- **Direct purchase**: aiming at a product highlights it and shows name/price; right-click charges and the money goes to the register (also in your shop).
- **Cashier** (without one only single cards are sold) and **guard** (patrols, stops thieves, recovers cards), hired from the phone, with a daily wage.
- **Reputation** 0–100 with breakdown (prices, variety, displays, decoration, security, service, satisfaction, thefts, word of mouth).
- **Furniture with its own model**: display stand, display counter, wall shelf, double-sided gondola (aisles), box pallet, rotating pedestal,
  animated cash register, framed poster, play table, anti-theft detector (alarm), Poké Roulette (red/black, phone only), mystery box.
- Redesigned **phone**: Market with categories and sorting (everything from the Wholesaler + Roulette), Prices with set/rarity filters and chart,
  My shop (summary, staff, security, reputation), Titles, Collection and Achievements.
- **Card trading** from Cobblemon's interaction wheel (or `/tcgtrade <jugador>`): same rarity, up to Illustration Rare,
  both confirm, atomic swap revalidated on the server; cancelled when moving >12 blocks away or disconnecting.
- 17 new shop/trading achievements (111 in total).

### Phase 4 — Binders and showcase
Portable binder with animated pages, 9 pockets, holo, filters and % completed; standing or wall-mounted album block;
showcase board.

### Phase 3.5 — Living shop v2 ✅
- **Large multi-block furniture** (cards appear at item-frame size and are drawn just like in one, with each piece's tilt):
  2×2 display stand (2 cards), 3×1 display case (4), 3×3 shelf (12 packs), 3×2 double-sided gondola (16), 2×2 pallet (6 boxes),
  1×3 pedestal with glass case and a 3×3 **graded card wall** (12 slabs). Breaking any part breaks the whole piece.
- Large **cardboard pack boxes** with lid, folds, wavy edge, "36 SOBRES" band, plastic wrap with animated shine;
  when opened (placed or from the hand) the animation plays: lid, packs inside and packs coming out.
- **Customers**: walk in from outside, pick up the product, go pay at the register and leave holding it; they leave a
  review (1-5★) that counts toward reputation; happy ones return as **regular customers**. Those who want a deal
  show a **«!»**; new **collector** type who proposes swaps (his card ⇄ one from your displays).
- **Thieves**: with a guard they are always stopped; without a guard they flee like a Scalper (chase, hit or battle).
- **Scalpers**: fight each other and Minecraft monsters attack them. **Pokémon battles** (Cobblemon) against
  Scalpers and thieves: if you beat them, they go down.
- Redesigned **phone**: vertical home screen with widgets; apps rotate to landscape with a side rail; reviews tab.

### Phase 4 — Competitive and extras ✅
- **CobbleGrade**: 1.0-10 grading in tenths from the phone, timed orders, slab with grade on displays, value x0.4-x8.
- **Competitive TCG mode** at play tables: custom rules with values taken from the species (Cobblemon) and rarity;
  league matches between players (ELO, ranks Bronze→Master) or against the house (AI); TCG League app with deck editor.
- **NPC vs NPC Pokémon duels** (simulated with real Pokémon: Cobblemon doesn't set up battles without a player).
- **Bad smell** for customers and shop + **deodorant spray**. **Packs in chests** (1%). New displays and **home** displays.

### Phase 4.5 — Real Pokémon TCG ✅
- **twinleaf.gg** engine (MIT, based on keeshii's ryuu-play, MIT) compiled with esbuild (`engine/build.js` -> `assets/tcgshop/ptcg/engine.js`)
  and run with **GraalJS** inside the mod (jar-in-jar), on its own thread. Official rules and real effects: 60 cards, 6 prizes,
  evolution, typed Energy, special conditions, Supporters/Stadiums/Tools. 15,370 cards in the engine; 6,051 from cobbletcg matched
  (`tools/gen_ptcg_map.py`). The house uses the twinleaf bot. Engine texts from its i18n (`tools/gen_lang_ptcg.py`).
- Replaces the previous simplified mode (tables, ELO league and TCG League app with 60-card deck editor).

### Phase 4.6 — Economy v3, quick sell and Collector ✅
- **Rebalanced economy** so the shop turns a profit: furniture ~3-4x cheaper (shelf 16k, display case 18k, phone 25k),
  shop blocks 15k → 2M, cashier 30k + 4k/day, guard 45k + 6k/day. Wholesaler at 60% of market, phone Market at 80%.
  More customers (0.2 + rep/180 per attempt) and more willing to buy at market price.
- **Quick sell** in the phone (50% of value, with double-click confirmation) and to the **Wholesaler** (40%), who also buys
  **emeralds** for CobbleDollars (`emeraldPriceCd`, 300 by default) and recommends it when opening his stall.
- **Wandering Collector** (`tcgshop:trader`, passive, 40% every half day): 6 cards; trades them for yours worth ≥85% of their value
  and takes a fancy to one of yours, which he pays 115% for. 4 deals per visit.
- **Starter kit**: 5 random sealed packs the first time you join (`starterPacks`, `starterPackCount`).
- **Packs**: open 50 or 100 at once (wide grid, cutting sound without saturating).
- **Phone**: wallet in the status bar, price range filter and sorting in Prices, icons that fit on short screens.
- **Duel**: bots with a ~1.9 s pause between plays (4.5 s before the first); "VS" intro; **3D coin** (`Coin3D`, reeded edge,
  embossed faces, gold/silver/Master styles ready for collectible coins); tools peeking out behind the Pokémon with their name;
  messy discard pile and full view with you/opponent tabs; the hand tucks away when not in use and reveals the Bench.

### Phase 4.7 — Displays, phone v2 and duel cosmetics ✅
- **Placing products**: with a product in hand, free slots are marked with corners; the targeted one shows a translucent
  preview, "Colocar aquí" and the market price. When placed, the product drops with a bounce, spins and gives off sparks. Right-click with
  the same product on a slot = **restock** (adds to the pile). Multiple units appear stacked (packs in a row toward the back,
  cards fanned out, boxes one behind another), each product slightly rotated, and paper price tags with "×N".
- **Phone v2**: rounded metal case with a halo in the app's color, camera island, aurora background with stars, rounded
  icons with shine, glass widgets, top bar with shadow, rail with animated indicator, clipped screen corners.
- **Buttons and chips** (`Ui.button/chip/progress`, duel buttons): truly rounded corners, glass reflection, glow on mouse
  hover and they sink when pressed. Affects every screen in the mod.
- **Duel cosmetics** (`duel/Cosmetics`, stored in the League): **27 3D coins** (14 emblems: Poké Ball, star, lightning, flame,
  drop, leaf, moon, sun, crown, gem, snowflake, heart, spiral, shield; rainbow and sparkle effects) and **21 HD board backgrounds**
  generated in code (512 px seamless tiling, `DuelBackgrounds`). Unlocked by rank (best rank reached), points,
  wins, wins against the house, matches, streaks, packs opened, or as rare loot when opening packs (1/140 per pack).
  Phone → League → Coins / Backgrounds / Achievements to view and equip them. In the duel each player sees their own background and each flip uses the
  coin of whoever flips (the house uses one based on its level).

### Phase 4.8 — Configurable economy, traders and environment-based reputation ✅
- **Configuration**: `config/tcgshop-economy.json` (fixed prices, employees, factors, prizes, grading, demand, probabilities, set locking, customer waiting, rejections, reputation weights, bad reputation, achievement announcements) and `config/tcgshop-vanilla-prices.json` (full table of vanilla items). `/tcgshop reload` applies everything without restarting and sends it to clients. If `tcgshop.json` is broken, it is not applied and operators are warned.
- **Prices**: `tools/fetch_prices.py` fixed (old "[Unlimited Edition]" products, real sales only, Normal/Unlimited variant before reverse or 1st edition). Sealed with no data = pack × 36 (box) / × 10 (ETB).
- **Top 5 sets** (`lockTopPackSets`): can only be bought with a Diamond+ shop (`lockedPacksMinShopTier`); 0.001% per chest (`lockedPackChestChance`); never in the starter kit, Mystery Box or NPCs.
- **Wholesaler**: buys almost any vanilla item (wood ≈ 0.5, iron ≈ 1, max. 1,000; renewables almost nothing; blocks = 9 pieces). Emerald uses the table.
- **Traders**: 6 personalities, 120 motives (`tcgshop.motive.*`), haggling with counteroffer and arguments (market, elsewhere, no money, last offer, think it over), patience, neutral/negative review on rejection with a daily cap.
- **Customers**: 60 s wait with a bar; being left unattended penalizes. Comments about the surroundings and recommendations (recent 5★ bring more customers).
- **Reputation**: Aesthetics, Value for money and Security (0-100) + weighted total. Detects roof, walls, light, doors, monsters, animals, pets and Pokémon (only in the area with furniture).
- **Bad reputation**: killing shop NPCs lowers reputation (recovers each day) and customers who see it flee.
- **Achievements**: all of the mod's and cobbletcg's are announced in chat; with Advancement Plaques its notice is not replaced.

### Phase 4.9 — Phase B (part 1) ✅
- **Displays**: owner right-click on a card → Take / Edit (price, size 50-180% and X/Y/Z rotation, live preview on the furniture, Reset). Stored per slot (NBT "Look") and synced. Sneaking = take instantly.
- **Cashiers**: two checkout stations (one per cash register) + guard; cashiers only work by day (6:00-18:30), go home at night and come back at dawn; at night the owner charges for packs/boxes if standing next to a register. 3-card panel in the phone.
- **Card beggar**: hangs around shops; giving him coins or a card improves reputation ("charity"), sometimes he returns the favor; kicking him out worsens it.
- **Black marketeer**: extremely rare (`blackMarketChance`), private notice, offers a pack (sometimes from locked sets) for a Pokémon from your party; double confirmation; only delivers if Cobblemon confirms the Pokémon left; never leaves you without Pokémon.
- **Custom skins** for all NPCs (client/render/NpcSkins), by role, with no official art.

### Phase 4.10 — Phase B (part 2) ✅
- **Binder Market** (phone → Sell): quick sale of several cards at once from the General Binder (with "keep 1 of each" and confirmation), protected favorites, filters by expansion/rarity/value/quantity, sorting, total value, market vs. sale comparison, history (60 operations) and priced offers to another player (the card doesn't leave your Binder until the other accepts and pays; `/tcgshop offer accept|deny`).
- **Friends** (phone → Friends): requests, list with online/offline, stats (League, unique cards, packs, sales, revenue) and each friend's shops with coordinates (friends only). Data in `tcgshop_social`.
- **Display manager** (phone → Displays): all your displayed products with price and difference from market; -5%/+5%/market per product and in bulk (90/100/110/125%).
- **Premium display case**: rare cards (tier 2+) glow softly on displays.
- Pending: card-for-card trading directly from the Binder (today: take out to inventory and /tcgtrade).

### Next — Online ranked league (design pending)
Ranks Master Ball 1-10 → Champion 1-3 → top 10-2 "Arceus' Champion" → top 1 "God of Games"; 3D coins per rank/loot.
Real limits to solve before coding: P2P with no server at all still needs a meeting point (public DHT like
BitTorrent, or playing inside the same Minecraft server) and NAT traversal; a "global top 10" ranking without a server can only be
eventual (gossip between peers); and a rank stored on the PC can be obfuscated and signed, but not made impossible to edit
(what is verifiable is signing each result with both players' keys and having peers reject invalid histories).

### Phase 5 — Extras
Grading, employees, market events, bulk boxes, surprise pick, trading, cosmetics, immersive art.

### Future — PvP
Card battles between players (rules engine, decks, table, tournaments).

## Upcoming updates

- **Random events:** in development in 0.9 (data-driven engine, 10% daily chance per owner,
  incidents to fix, Cobblemon Pokémon, reviews that remember what happened).
- **Properties:** later you will be able to buy Minecraft buildings with money; the luxury statues
  from 0.9 will change and improve according to their tier.

### 1.0 — Release (planned 2026-11-23; not started; includes everything that was planned for 0.9.5)

**Decisions (made 2026-11-23)**
- The Paper stall costs **3,000**. It is the cheap starter, below the 5,000 starting money.
- **5 starter packs**, so the personal starter binder holds **25 cards**: 5 packs × 5 cards.
- **Card art.** Original (generated) art and our own card back are the default. Official art and the official back are read only from the player's image folder and never ship in the mod. The back goes in `<image folder>/back of cards/card_back.png`, and the API already serves it (since 2026-11-23).
- **Wandering Duelist.** You see the stakes and accept before the duel starts.
  - If you lose, he takes all the cash you carry, up to **50,000** (5× his 10,000 prize).
  - If you carry no cash, he takes one random inventory item.

**From 0.9.5 (merged)**
- Start with 5 packs and the personal binder already equipped in the accessory slot, with your name on it.
- 5,000 starting money, given only when you open your first pack.
- One-time unique collector NPC: after a while of play he offers 5,000 for a card he likes, and never returns.
- The wholesaler spawns more often while you have no shop yet.
- After 4 Minecraft days without a shop, a taunting NPC throws you a random card worth about 15,000, calls you a beggar and pushes you to open a shop.
- Better skins and many more of them, common names, and customers split by sex (female and male skins and names).
- Thief tiers scale with shop popularity. The top tiers can kill your guards or you and steal all your stock, but they are VERY rare. Guards matter more, and guard upgrades are added.

**Fixes first**
- Some cards don't go into the binder and stay in your hand.
- Some displays don't accept a card.
- The manager goggles render too high and behind the head.
- Phone notifications and popups let the text behind them show through, so they are unreadable. Fix with an opaque background and their own layer.
- God packs: every card in them is full art, with no exceptions.

**Prices and economy**
- Base phone: 5,000. Manager goggles: 2,000.
- Cash register: craftable, and sold for 2,000 in the phone store and by the merchant.
- The wholesaler buys:
  - emerald and gold: 300 each
  - diamond: 500
  - any Cobblemon Poké Ball: 200
- Every Mega Stone is worth 50,000 and sells to any trader.
- Buy x36 boxes of every available pack, limited by your phone tier.
- Tips: if a customer pays more than your asking price, the difference is a tip, and the dialogue says so.
- Charisma: a new level that makes haggling more effective.
- Collector: trade 2 or more of your cards for 1 of his.
- Mystery box rework, now for the player:
  - 99.99%: a card worth less than the box. 50% of these are graded, and even graded they are worth less than the box.
  - 0.01%: a decent grade-10 card from the newest packs.
- Very rare discount event: one store item drops 10, 30 or 50%.
  - The chance per item is higher for cheap items and almost zero for expensive ones.
  - The item shows a % tag in the store, and a notice arrives in the Messages app.

**Shops**
- At least 12 tiers, and the wood shop becomes 10×10. Proposed tiers, with the side length in blocks:

  | Tier | Size |
  |---|---|
  | Paper | 5 |
  | Wood | 10 |
  | Brick | 16 |
  | Stone | 24 |
  | Copper | 32 |
  | Iron | 40 |
  | Gold | 48 |
  | Lapis | 56 |
  | Diamond | 64 |
  | Amethyst | 72 |
  | Emerald | 80 |
  | Netherite | 100 |

- Paper tier, a lemonade-stand style stall:
  - capacity 1 and very few customers;
  - 1 small shelf;
  - crafted from 1 diamond plus wood (recipe to design).
- One shop per player until the third-to-last tier (Amethyst). Reaching it sends a phone notice that you may now own more.
- A richer shop menu on right-click. More completed missions raise your luck with deals and tips.
- About 100 shop missions in total.
- About 20 new, pricier RGB displays.
- TCG table: needs 2 CobbleFurnies chairs nearby. Then an NPC may challenge you, or two NPCs pretend to play a match and hold the table for a while.
- Statue tier 3 and up: NPCs sometimes drop a random pack near the statue (never the priciest ones). The chance grows with the tier, but it is almost never at gold.

**Phone**
- The TCG League unlocks at phone upgrade 2.
- Messages app: read/unread, delete and send.
- Wholesaler loans from tier 2, sent as a gold-bordered message:
  - at most 50,000;
  - tier 2 offers small loans, rarely;
  - you can't buy from the phone while a loan is active;
  - the phone remembers your loans, and your credit history depends on how many days you take to repay.
- Stats app: cash in the register, reputation, and what is going for and against you.
- A notice for a successful purchase, and one when you can't afford something.
- Customizable wallpaper.
- Locked/unlocked filter in the market.

**Binder**
- Multi-select, mainly to take several cards out at once.
- A price filter per pack.

**NPCs**
- Models with the second skin layer (clothes with depth) and more detail.
- 10 fixed unique customers:
  - rainbow names, a fixed skin and personality each, and unique dialogue;
  - some are nice and some are nasty; one is an excellent customer and one is a known thief who tries to rob you;
  - all 10 spawn rarely, and the excellent customer and the thief even more rarely.
- Steal 1 random card from a collector's own pool. He turns into a near mini-boss with speed, armor and a lot of health, and there are achievements for it. Outcomes:

  | Outcome | Effect |
  |---|---|
  | You escape without killing him | Huge reputation loss |
  | You kill him | 50% chance of a huge reputation loss |
  | He kills you | Reputation loss, and the stolen card vanishes |

**World**
- Detailed trash cans you can search with right-click: 10% chance of a pack, 30% chance of a card worth under 2,000.
- "Bankrupt Card Shop" structure:
  - an abandoned shop with cobwebs and a "Permanently Closed" sign;
  - rare, and more likely a few blocks away from villages;
  - may hold 1 cheap CobbleFurnies piece;
  - has a unique trash can with a 50% chance of one card worth 300 to 20,000.
- "Wandering Duelist" structure: a TCG table, 2 chairs and a seated NPC who challenges you on hard difficulty.
  - Win: you get 10,000.
  - Lose: he takes your cash (see Decisions) or, if you have none, a random item. Then he vanishes with his table and chairs, and you get a notice that the pro player left and took your item.

**Gambling**
- Many expensive machines with real-world odds:
  - slots, at about 92–96% RTP;
  - European roulette, with a 2.7% house edge;
  - blackjack;
  - scratch cards, and more.

**Art and options**
- A mod options screen with a bindable key.
- Original art is the default:
  - Pokémon cards: a pixel-art monster silhouette, with the border colour set by the card's type (fire, water, …);
  - Trainer and item cards: a different silhouette and a grey border and background;
  - card names stay the same;
  - our own card back.
- Official art (optional): read from the player's folder (PNGS POKEMOMN), including the official back.
- On first play, a notice says the art can be changed in the options.
- Proper 3D models for items: phone, gloves, broom, repair kit and the rest. The phone in hand shows the apps of its tier.

**Publishing**
- A new CurseForge description:
  - celebrate 1.0;
  - remove every mention of ScalperTCG;
  - ask for feedback and improvement ideas;
  - thank everyone for playing.
