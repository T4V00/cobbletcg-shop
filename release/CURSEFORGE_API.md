# MasterBall Traders API

### The Pokémon TCG card library behind **MasterBall Traders Co.**

> ✅ **Compatible with COBBLEVERSE 1.8.0** (Cobblemon 1.8.4, Fabric, Minecraft 1.21.1).

This is a **library mod**. On its own it only adds the card and pack items. Install it together with **[MasterBall Traders Co.](https://www.curseforge.com/minecraft/mc-mods/masterball-traders-co)**, the Pokémon TCG shop simulator that requires it.

---

## What it provides

- **11,767 cards and 272 sealed packs from 68 sets**, from Base Set (1999) to the 2026 sets. Only physically sold TCG products are included; there are no TCG Pocket cards.
- **Official rarities.** There are 42, from Common to Mega Hyper Rare. Each card also has its era's holo pattern and **print variants**: reverse holo, Poké Ball and Master Ball reverse, 1st Edition, Shadowless and more.
- **Realistic pack contents**: 5 cards drawn by official rarity family, with rare semi-god packs and **god packs that are always full art**.

## Card art

By default every card gets **original, generated art**: a pixel-art silhouette with a border colored by the card's type, keeping the real card name. No official card art is included in this mod.

Prefer your own images? Press **O** in game (MasterBall Traders Co. options) and switch to **Official art**. The API then reads card images from your own `PNGS POKEMOMN` folder in the game directory, or from the folder set as `imageFolder` in `config/masterball-api.json`. A card back image can go in `back of cards/card_back.png` inside that folder.

## Requirements

| | Mod |
|---|---|
| **Required** | Fabric API · Fabric Loader 0.16+ · Minecraft 1.21.1 · Java 21 |

Source code and issues: [GitHub](https://github.com/T4V00/cobbletcg-shop)

## AI disclosure

Made with the help of **Claude** (Anthropic). The project was directed, play-tested and curated by a human.

## Disclaimer

<sub>Fan-made project, not affiliated with or endorsed by Nintendo, The Pokémon Company, Game Freak, Creatures or TCGplayer. Pokémon names are trademarks of their owners. Market data from TCGplayer via tcgcsv.com.</sub>
