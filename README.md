# MasterBall Traders Co.

**PTCG Shop Simulator & PvP**

A Pokémon TCG shop simulator for Cobblemon worlds (Fabric, Minecraft 1.21.1). The cards come from **MasterBall Traders API** (11,700+ physical cards from 68 sets; TCG Pocket cards are not included).
Run your own Pokémon TCG card shop, open packs with animations, trade with NPCs and players, grade cards, play the real TCG and climb a ranked league. All prices are in CobbleDollars.

> Started as an add-on for ScalperTCG (by SorEscopeta). Since 0.2.0 it no longer needs it: its saved cards are converted automatically. Thanks to SorEscopeta for the original mod.
>
> Heavily inspired by **TCG Card Shop Simulator** and **Pokémon TCG Pocket**.
>
> **AI disclosure:** this mod was made with the help of **Claude** (Anthropic). AI was heavily involved in the development process, including code, procedurally generated textures, texts and balancing. The project was directed, play-tested and curated by a human.


**Download:** [CurseForge](https://www.curseforge.com/minecraft/mc-mods/cobbletcg-shop) (beta, pending moderation at first) · **Issues and suggestions:** [GitHub Issues](https://github.com/T4V00/cobbletcg-shop/issues)

---

## Requirements

| | Dependency | Why |
|---|---|---|
| **Required** | MasterBall Traders API 0.1.0+ | Cards, packs, rarities, print variants and card images (downloaded automatically on first launch). |
| **Incompatible** | ScalperTCG | Replaced by the API. Remove it: its cards in your worlds are converted, and its placed albums are emptied into barrels. |
| **Required** | CobbleDollars | The whole economy (buying, selling, wages, rewards) uses CobbleDollars. |
| **Required** | Fabric API, Fabric Loader 0.18.4+, Java 21 | |
| Recommended | Cobblemon 1.7+ | Pokémon battles against thieves and Scalpers, the black-market dealer, Pokémon titles. |
| Optional | Pokeblocks | Pokédolls as decoration and stock. |

Install the mods on the server and on every client. The resource packs are only needed on the client.

## Installation

1. Create a Fabric 1.21.1 profile (Fabric Loader 0.18.4+).
2. Put Fabric API, MasterBall Traders API, CobbleDollars, MasterBall Traders Co. (and ideally Cobblemon) in `mods/`.
3. Start the game once: the card images download on the title screen (or point `imageFolder` in `config/masterball-api.json` to a folder of images).
4. Start the game. The config files are created the first time it runs (see below).

---

## Configuration

There are three files in `config/`. You can edit them with the game running and apply the changes with **`/tcgshop reload`** (needs operator / cheats). The command re-reads every file and sends the new values to everyone online.
If a file contains invalid JSON, it is **not applied and not overwritten**: `/tcgshop reload` tells you the error, and operators see a warning when they join.

Card, pack and box prices are real TCGplayer market prices in **USD**. The mod turns them into CobbleDollars with `cobbleDollarsPerUsd`.

### `config/tcgshop.json`: general settings

| Setting | Default | What it does |
|---|---|---|
| `cobbleDollarsPerUsd` | `1000` | Global price multiplier. In-game price = real USD price × this value. `500` halves every card/pack/box price, `2000` doubles them. |
| `currencySuffix` | `" ₽"` | Text shown after every amount of money. |
| `marketEnabled` | `true` | Dynamic market: prices rise and fall every market day. |
| `marketVolatility` | `1.0` | How much prices move: `0` = fixed, `1` = normal (about ±20-40%), `2` = crazy market. |
| `marketDayTicks` | `24000` | Length of a market day in ticks (24000 = one Minecraft day, 20 min). |
| `marketSeed` | random | Seed of the market trends. Changing it "restarts" the trends. Created automatically. |
| `announcePullMinTier` | `3` | Minimum rarity tier (0-3) that gets announced in chat when someone pulls it. `-1` = never. |
| `announceRadius` | `0` | Radius in blocks for pull announcements. `0` = whole server. |
| `emeraldPriceCd` | `300` | Legacy. Emeralds now use the vanilla price table (see below). |
| `starterPacks` | `true` | Give sealed packs to players the first time they join a world. |
| `starterPackCount` | `3` | How many starter packs. |
| `packOpeningAnimation` | `true` | *(client)* Animated pack opening screen. |
| `showPriceInTooltip` | `true` | *(client)* Show the market price in card tooltips. |
| `configVersion` | `2` | Internal, used to migrate old defaults. Don't change it. |

### `config/tcgshop-economy.json`: economy, NPCs and reputation

The file contains a `_help` section with short explanations. Missing values are filled in with defaults, and new items added by updates are appended automatically. Your own values are always kept.

**Fixed prices**

| Setting | Default | What it does |
|---|---|---|
| `itemPrices` | (list) | Fixed price of every furniture piece, tool, phone, Mystery Box and shop core (`tcgshop:shop_core_wood` … `shop_core_netherite`), by item id. |
| `defaultItemPrice` | `5000` | Price of any sellable item that has no fixed price. |
| `pokedollPrice` / `giganticPokedollPrice` | `40000` / `150000` | Market price of Pokeblocks Pokédolls. |
| `minPackPrice` / `minBoxPrice` / `minCardPrice` | `100` / `1000` / `10` | Minimum market price, whatever the real data says. |

**Buying and selling**

| Setting | Default | What it does |
|---|---|---|
| `wholesalerBuyFactor` | `0.6` | Packs, boxes and cards bought from the Wholesaler cost this fraction of market price. |
| `phoneBuyFactor` | `0.8` | Same for the phone's online market. |
| `phoneSellRate` | `0.5` | Quick sell (phone and Binder Market) pays this fraction of a card's value. |
| `wholesalerSellRate` | `0.4` | The Wholesaler pays this fraction of a card's value. |

**Staff and customers**

| Setting | Default | What it does |
|---|---|---|
| `cashierHire` / `cashierWage` | `30000` / `4000` | Hiring cost and daily wage of each cashier (you can have two). |
| `guardHire` / `guardWage` | `45000` / `6000` | Hiring cost and daily wage of the guard. |
| `customerDemand` | `1.0` | Multiplier for how many customers come to your shop (`2` = twice as many). |
| `customerWaitSeconds` | `60` | How long a customer who wants a deal waits (bar over their head) before leaving. |

**Merchants and haggling**

| Setting | Default | What it does |
|---|---|---|
| `rejectNegativeChancePolite` | `0.18` | Chance of a negative review when you politely turn down a deal (otherwise neutral). |
| `rejectNegativeChanceRude` | `0.75` | Same when you turn it down rudely. |
| `maxRejectPenaltiesPerDay` | `2` | Max negative reviews from rejections per shop per day. The rest are neutral. |
| `merchantPatience` | `1.0` | Patience multiplier of merchants when you haggle. |

**Duels**

| Setting | Default | What it does |
|---|---|---|
| `houseDuelRewards` | `[800, 1500, 3000, 6000]` | Reward for beating the house AI (easy, normal, hard, expert). |
| `houseDuelPenalties` | `[200, 400, 800, 1500]` | Fine for losing a ranked match against the house. |
| `pvpPenalty` | `600` | Fine for losing a ranked match against a player. |

**Grading**

| Setting | Default | What it does |
|---|---|---|
| `gradingBaseFees` | `[2500, 6000, 15000]` | Fixed cost per card (economy, standard, express). |
| `gradingValuePct` | `[0.03, 0.06, 0.10]` | Plus this fraction of the card's value. |

**Loot and locked sets**

| Setting | Default | What it does |
|---|---|---|
| `chestPackChance` | `0.01` | Chance that a structure chest contains a sealed pack. Applies after `/reload`. |
| `lootCoinChancePerPack` | `0.00714` (1/140) | Chance per opened pack to find a rare duel coin. |
| `lockTopPackSets` | `5` | The N sets with the most expensive sealed pack (e.g. Base Set) can't be obtained from starter kits, Mystery Boxes or NPCs. `0` = off. |
| `lockedPacksMinShopTier` | `"diamond"` | Shop tier needed to **buy** those locked sets on the phone and from the Wholesaler (`wood`, `stone`, `iron`, `gold`, `diamond`, `emerald`, `netherite`). |
| `lockedPackChestChance` | `0.00001` | Chance per chest of finding a pack from a locked set (0.001%). |

**Reputation**

| Setting | Default | What it does |
|---|---|---|
| `repWeightAesthetics` / `repWeightValue` / `repWeightSafety` | `0.35` / `0.40` / `0.25` | Weight of each score in the total reputation (normalized). |
| `infamyPerKill` | `12` | Reputation lost in all your shops when you kill a shop NPC (customers, Wholesaler, Collector, staff). Thieves and Scalpers don't count. |
| `infamyDecayPerDay` | `3` | How much of that reputation comes back each day. |

**Encounters**

| Setting | Default | What it does |
|---|---|---|
| `beggarChance` | `0.25` | Chance every half day that the card beggar shows up near a player (near their shop if they have one). |
| `blackMarketChance` | `0.02` | Chance every half day that the hooded black-market dealer shows up. Very rare on purpose. |
| `blackMarketLockedChance` | `0.12` | Chance that the dealer offers a pack from a locked set. |

**Notifications**

| Setting | Default | What it does |
|---|---|---|
| `announceAllAdvancements` | `true` | Announce every MasterBall Traders Co. advancement in chat (not only challenges). Also respects the `announceAdvancements` gamerule. |

### `config/tcgshop-vanilla-prices.json`: what the Wholesaler pays for vanilla items

A complete table of every vanilla item with the price the Wholesaler pays **per unit**, in CobbleDollars (decimals allowed). When you sell, the total is added up and rounded down.

- Reference values: basic materials (logs, saplings) ≈ `0.5`, iron and copper ≈ `1`, diamond `25`, nothing above `1000`.
- Easily renewable items pay almost nothing, and compact blocks are worth exactly their pieces, so crafting gives no profit.
- Damaged tools and armour pay less. Enchanted items pay 25% more.
- `0` means the Wholesaler won't buy that item (spawn eggs, spawners, creative-only blocks…).
- Edit any value and run `/tcgshop reload`. Items added by Minecraft updates are appended automatically, and your values are kept.

### Card, pack and box prices (datapack)

Real prices come from `data/tcgshop/tcgshop_prices/*.json` (in USD). A datapack can override any of them. Only include what you want to change, then run `/reload`:

```
saves/<world>/datapacks/my_prices/pack.mcmeta            {"pack": {"pack_format": 48, "description": "My prices"}}
saves/<world>/datapacks/my_prices/data/tcgshop/tcgshop_prices/cards.json
```
```json
{
  "cards_usd": { "masterball:aradiance/cards/asr001": 5.0 },
  "sealed_usd": { "aradiance": { "pack": 4.0, "box": 150.0, "etb": 60.0 } },
  "rarity_median_usd": { "*": { "common": 0.15 } }
}
```
- `cards_usd`: price of one specific card.
- `sealed_usd`: pack / box / ETB of a set. A missing box is 36 packs, and a missing ETB is 10 packs.
- `rarity_median_usd`: estimate for cards without their own price, by rarity (`"*"` = every set).

---

## Commands

| Command | Who | What it does |
|---|---|---|
| `/tcgshop price` | everyone | Market price (and 7-day trend) of the card in your hand. |
| `/tcgshop value` | everyone | Value of all the cards you carry. |
| `/tcgshop offer accept <id>` / `deny <id>` | everyone | Answer a card sale offer from another player (clickable in chat). |
| `/tcgtrade <player>` / `accept` / `deny` | everyone | Card-for-card trade with another player. |
| `/tcgduel <player>` / `accept` / `decline` | everyone | Challenge a player to a TCG match. |
| `/tcgshop reload` | operators | Reload all config files and sync them to players. |
| `/tcgshop debug selftest`, `econtest`, `phasebtest`, `ptcgtest` | operators | Built-in self-tests (results in the server log). |

Default keybind: **G** opens your General Binder.

---

## Building from source

```
./gradlew build              # personal build
./gradlew build              # needs ../masterball-api built first
```
The jar ends up in `build/libs/`. The build compiles against MasterBall Traders API (`../masterball-api/build/libs`, see `build.gradle`). CobbleDollars and Cobblemon are used through reflection only.

`tools/` contains the generators for language files, textures, price data (`fetch_prices.py`, public data from tcgcsv.com) and the TCG engine card map.

### Graded prices (PriceCharting)

Graded cards use real graded prices when a price file has a `graded_usd` table (grades 7, 8, 9, 9.5 and 10 per card id); otherwise a typical PriceCharting curve is used (7 → x1.15, 8 → x1.5, 9 → x2.4, 9.5 → x4, 10 → x9 over the ungraded price, with a minimum value per slab). PriceCharting does not allow automated downloads, so import your own export: `python tools/import_pricecharting.py --csv pokemon-cards.csv` (or `--token <your API token>`). It writes `data/tcgshop/tcgshop_prices/pricecharting.json`, which overrides the TCGplayer prices where it has data.

### League points vs. the AI

Fixed per level: win easy +4, normal +9, hard +16, champion +28; lose easy -16, normal -12, hard -10, champion -10. From Gold you can no longer play ranked on easy, from Platinum not on normal, and from Diamond only against the champion.

## Credits

- **ScalperTCG** by SorEscopeta: the original mod this started from; its card list (CC0) was used as a reference for card names.
- Pokémon TCG rules engine: **twinleaf.gg**, based on **ryuu-play** by keeshii (MIT, license included in `assets/tcgshop/ptcg/LICENSE-twinleaf.txt`). Runs on GraalJS (UPL).
- Market data: TCGplayer, via tcgcsv.com.
- Inspiration: TCG Card Shop Simulator, Pokémon TCG Pocket.

## Disclaimer

Fan-made project, not affiliated with Nintendo, The Pokémon Company, Game Freak, Creatures Inc., TCGplayer or the TCG Card Shop Simulator developers. Pokémon and related names are trademarks of their respective owners. No official art is included.
