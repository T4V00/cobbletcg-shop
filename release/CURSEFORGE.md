# CobbleTCG Shop

### Open your own Pokémon card shop in Minecraft. Rip packs, chase hits, price your stock, haggle with customers and become the best TCG store in the server.

**CobbleTCG Shop** is a shop-simulator add-on for **[ScalperTCG](https://www.curseforge.com/minecraft/mc-mods/scalpertcg)** (formerly CobbleTCG). It turns ScalperTCG's card collection into a full trading-card economy: real market prices, a living shop full of NPC customers, pack openings that feel like the real thing, binders, grading, a phone with apps, and real Pokémon TCG matches with a ranked league.

---

## Main features

**Your own card shop**
- Claim a shop plot and upgrade it from Wood to Netherite.
- Furnish it with multi-block displays, shelves, gondolas, booster-box pallets, pedestals and graded-card walls.
- Hire cashiers (day shift) and a guard (works day and night).
- Set your prices, restock and watch customers walk in.

**Customers with personality**
- Regulars, picky buyers, rich collectors and hagglers browse and buy, then leave a review.
- Sellers bring you cards and packs to buy, and you can haggle with them. They have their own personality, patience and over 100 different reasons for selling.
- Thieves try to run off with your stock.

**Pack opening, done right**
- Rip packs card by card with flips, holo shine, particles and god packs.
- Open 1 to 100 packs at once.
- Every pull shows its market value.

**Real market prices**
- Card, pack and box prices come from real TCGplayer market data and move with a dynamic daily market.
- The most valuable vintage sets are locked behind high-tier shops.

**The TCG Phone**
- An online market with real price charts, plus quick sell.
- Manage your shop: staff, reputation and reviews.
- A display manager to reprice everything in your shop.
- Grading, the league, friends and their shops.

**Binders and collecting**
- A General Binder that stores your whole collection.
- Customizable Personal Binders and album pages.
- A Binder Market to sell straight from your binder, protect your favourites, and send sale offers to other players.

**CobbleGrade**
- Send cards for grading (1.0 to 10).
- Display the slabs in your shop.

**Real Pokémon TCG matches**
- Play with the official rules (60-card decks, prizes, evolutions, trainers) at play tables.
- Play against the house AI or other players.
- Climb a ranked league and unlock 27 collectible 3D coins and 21 HD board backgrounds.

**A world full of encounters**
- The Mysterious Wholesaler, the Wandering Collector and Scalpers who try to rob you.
- A card beggar, and a very rare hooded black-market dealer who swaps sealed packs for Pokémon.

**Reputation that reacts to your shop**
- Customers rate your shop's aesthetics, value and safety.
- They notice roofs, walls, lighting, monsters outside, animals and Pokémon, and they talk about it.

**Fully configurable economy**
- Prices, wages, rewards, chances and much more live in config files.
- Server owners can change them in-game with `/tcgshop reload`.

---

## Requirements (read this)

| | Mod | Notes |
|---|---|---|
| **Required** | [ScalperTCG](https://www.curseforge.com/minecraft/mc-mods/scalpertcg) (1.6.0+) | The base mod. This add-on does not work without it. |
| **Required** | ScalperTCG resource packs (listed as "cobbletcg resourcepack 1, 2, 3...") | Published by the ScalperTCG author on CurseForge. Without them, the cards have no art. |
| **Required** | [CobbleDollars](https://www.curseforge.com/minecraft/mc-mods/cobbledollars) | Everything is bought and sold in CobbleDollars. Without it, the economy can't pay or charge you. |
| **Required** | Fabric API | |
| **Required** | Fabric Loader 0.18.4+ · Minecraft 1.21.1 · Java 21 | |
| Recommended | [Cobblemon](https://www.curseforge.com/minecraft/mc-mods/cobblemon) | Pokémon battles against thieves and Scalpers, the black-market dealer, Pokémon titles. |
| Optional | Pokeblocks | Pokédolls as shop decoration and stock. |

**Don't forget the resource packs.** ScalperTCG's card art comes in separate resource packs. Download all of them from the ScalperTCG author's page and enable them in *Options > Resource Packs*, or your cards will show up blank.

---

## How to install

1. Install Minecraft **1.21.1** with **Fabric Loader** (0.18.4 or newer). The CurseForge app does this for you with a Fabric profile.
2. Add **Fabric API**, **ScalperTCG**, **CobbleDollars** and **CobbleTCG Shop** to your `mods` folder. Cobblemon is strongly recommended.
3. Download every **ScalperTCG resource pack** and put them in your `resourcepacks` folder.
4. Launch the game, go to *Options > Resource Packs* and enable all the ScalperTCG packs.
5. Create or open a world. On your first join you get a few sealed packs to start your collection.
6. Buy a **TCG Phone** from the Mysterious Wholesaler, a travelling merchant who shows up near players every now and then. The phone is your hub for the market, your shop and everything else.

**On a server:** install the same mods on the server and on every client. The resource packs are client-side only.

---

## Credits and thanks

- **This add-on only exists thanks to [ScalperTCG](https://www.curseforge.com/minecraft/mc-mods/scalpertcg) and its author, SorEscopeta.** All the cards, card art, packs and collection systems are theirs. Please support the original mod.
- Heavily inspired by **TCG Card Shop Simulator** and **Pokémon TCG Pocket**.
- Pokémon TCG rules engine from **[twinleaf.gg](https://github.com/twinleafgg/twinleafgg)**, based on **[ryuu-play](https://github.com/keeshii/ryuu-play)** (MIT License, included in the mod).
- Market data from TCGplayer, via [tcgcsv.com](https://tcgcsv.com).
- Built for the Cobblemon community.

## AI disclosure

This mod was made with the help of **Claude** (Anthropic). AI was heavily involved in the development process, including code, procedurally generated textures, texts and balancing. The project was directed, play-tested and curated by a human.

## Disclaimer

This is a fan-made project and is not affiliated with, endorsed by or connected to Nintendo, The Pokémon Company, Game Freak, Creatures Inc., TCGplayer or the developers of TCG Card Shop Simulator. Pokémon and all related names are trademarks of their respective owners. No official art is included in this mod.

---

**Bugs, suggestions or balance feedback?** Leave a comment or open an issue on the GitHub page. Server owners: every setting is documented there.
