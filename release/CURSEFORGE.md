# MasterBall Traders Co.

**PTCG Shop Simulator & PvP**

### Open your own Pokémon card shop in Minecraft. Rip packs, chase hits, price your stock, haggle with customers and become the best TCG store in the server.

**MasterBall Traders Co.** is a Pokémon TCG shop simulator for Cobblemon worlds. Its cards come from **MasterBall Traders API** (11,700+ physical cards from 68 sets). It turns that card collection into a full trading-card economy: real market prices, a living shop full of NPC customers, pack openings that feel like the real thing, binders, grading, a phone with apps, and real Pokémon TCG matches with a ranked league.

> ✅ **Compatible with COBBLEVERSE 1.8.0** (Cobblemon 1.8.4, Fabric, Minecraft 1.21.1). Just drop MasterBall Traders Co. and MasterBall Traders API into the modpack's `mods` folder.

---

## What's new in 0.9

- **Random shop events:** 78 data-driven events (positive, neutral and negative), only a 10% chance per day. They include incidents you clean up with a mop or repair kit, and Cobblemon Pokémon that wander in (scare them, feed them a berry or battle them). Nothing bad happens while you are offline.
- **Shop missions and capacity:** right-click your shop to see its stats and 12 missions. Customer flow depends on how many you complete. Each shop tier raises capacity, and when the shop is full, customers queue outside.
- **Phone upgrades:** 7 tiers, from Basic to MasterBall Edition. Each unlocks apps and merchant stock; the top tier adds a daily call to a collector or wholesaler.
- **Exclusive statues** from 1,000 to 1,000,000,000 CobbleDollars, and the **MasterBall Mansion Key** (1,000,000,000): right-click to build a 100x100 Poké Ball-themed estate. It is furnished with CobbleFurnies furniture and has resident Cobblemon that can't be caught.
- **Customers sit down:** browsers and some buyers rest on chairs, sofas and benches (CobbleFurnies, Cozyhome and more) before leaving, and stop to look at your furniture and plush.
- **PvP / TCG matches polished:**
  - smooth HP bars, animated damage numbers, K.O. and prize-card animations;
  - turn banners, energy-type colours and colour-coded logs;
  - a new end screen.
- **Binder:** click a card to see its art full-size.
- **Team Rocket Meowth** now talks in short speech bubbles and stays still, facing you.
- **TCG Pocket cards and packs removed:** only physical products remain.

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
| **Required** | MasterBall Traders API 0.1.0+ | Cards, packs, rarities and print variants (separate CurseForge project). |
| **Required** | [CobbleDollars](https://www.curseforge.com/minecraft/mc-mods/cobbledollars) | Everything is bought and sold in CobbleDollars. |
| **Required** | Fabric API | |
| **Required** | Fabric Loader 0.18.4+ · Minecraft 1.21.1 · Java 21 | |
| **Incompatible** | ScalperTCG | Replaced by the API. Remove it: its cards in existing worlds are converted automatically. |
| Recommended | [Cobblemon](https://www.curseforge.com/minecraft/mc-mods/cobblemon) | Pokémon battles, event Pokémon, mansion residents, the black-market dealer. |
| Recommended | ScalperTCG resource packs ("CobbleTCG ResourcePack 1, 2, 3...") | Card art. The API reads them from your `resourcepacks` folder automatically; you don't need to enable them. Without them, cards show a card back. |
| Optional | CobbleFurnies, Lucky's Cozyhome | Customers sit on their chairs and sofas; the mansion is furnished with CobbleFurnies. |
| Optional | Pokeblocks | Pokédolls as shop decoration and stock. |

---

## How to install

1. Install Minecraft **1.21.1** with **Fabric Loader** (0.18.4 or newer). The CurseForge app does this for you with a Fabric profile.
2. Add **Fabric API**, **MasterBall Traders API**, **CobbleDollars** and **MasterBall Traders Co.** to your `mods` folder. Cobblemon is strongly recommended.
3. For card art, put the ScalperTCG resource packs in your `resourcepacks` folder (they are read automatically).
4. Create or open a world. On your first join you get a few sealed packs to start your collection.
5. Buy a **TCG Phone** from the Mysterious Wholesaler, a travelling merchant who shows up near players every now and then. The phone is your hub for the market, your shop and everything else.

**On a server:** install the same mods on the server and on every client.

---

## Credits and thanks

- Started as an add-on for **[ScalperTCG](https://www.curseforge.com/minecraft/mc-mods/scalpertcg)** by SorEscopeta. Thanks for the original idea and mod.
- Heavily inspired by **TCG Card Shop Simulator** and **Pokémon TCG Pocket**.
- Pokémon TCG rules engine from **[twinleaf.gg](https://github.com/twinleafgg/twinleafgg)**, based on **[ryuu-play](https://github.com/keeshii/ryuu-play)** (MIT License, included in the mod).
- Market data from TCGplayer, via [tcgcsv.com](https://tcgcsv.com).
- Built for the Cobblemon community.

## AI disclosure

This mod was made with the help of **Claude** (Anthropic). AI was heavily involved in the development process, including code, procedurally generated textures, texts and balancing. The project was directed, play-tested and curated by a human.

**Bugs, suggestions or balance feedback?** Leave a comment or open an issue on the GitHub page. Server owners: every setting is documented there.

---

## Disclaimer

<sub>Fan-made project, not affiliated with or endorsed by Nintendo, The Pokémon Company, Game Freak, Creatures, TCGplayer or the TCG Card Shop Simulator developers. Pokémon names are trademarks of their owners. No official art is included in this mod. Prices are for in-game use only.</sub>
