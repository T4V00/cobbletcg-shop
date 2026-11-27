# MasterBall Traders Co. 1.1.0

**Requires MasterBall Traders API 0.3.0 or newer** (update both mods together). Minecraft 1.21.1, Java 21.

## New: NeoForge support

- MasterBall Traders Co. now runs natively on **NeoForge 1.21.1** (NeoForge 21.1+), with the same features as the Fabric version. There are two files from now on: one for Fabric, one for NeoForge. Install the one for your loader, together with the MasterBall Traders API file for the same loader.
- Both versions are built from one shared code base, so every future update lands on both loaders at the same time.
- On NeoForge, Cobblemon, CobbleDollars, Accessories and CobbleFurnies are supported through their NeoForge builds. Pokeblocks only has an unofficial NeoForge port; if it is missing, the Pokédoll features are simply skipped.
- Not supported: moving an existing Fabric world to NeoForge, and the old ScalperTCG / pre-0.9 conversion (that stays on Fabric). Start a new world on NeoForge.

## Your shop

- **Open / closed toggle.** Close your shop from the phone, the shop status window or by sneak-clicking the shop block. A closed shop gets no customers, events or reviews, and everything is paused until you open it again. The Manager Goggles show CLOSED.
- **Breaking the shop block keeps your progress.** The block you get back stays linked to your shop: place it again to restore the shop with its money, stats, staff, missions and reviews. You can also abandon a dormant shop from the phone.
- **Mid-game difficulty.** From the Stone tier, every higher tier makes customers a little less tolerant of high prices and hagglers a little tougher, and thieves show up more often and stronger. All of it is configurable (`tier*` in `tcgshop-economy.json`).
- **Collectors visit your shop.** While you are near your open shop, a Wandering Collector may walk in to trade.
- **Sell old shop blocks.** Shop blocks you no longer use (for example the one you get back after an upgrade) can be sold on the phone (quick sell) and to the Wholesaler for 50% of their price. Paper stalls (craftable) and blocks linked to a dormant shop are not bought.
- **Cleanliness matters much more** for sales, customer mood and reputation. Shops can get grimy, with droppings on the floor and graffiti on the walls: clean the floor with the mop, and the walls with the new **wall air freshener** or the deodorant spray (many uses, cheap).
- **3-block-wide storefront shutter** and **ten new decor objects** from the Furniture Seller.
- **Decoration liking:** a new bar in the Manager Goggles. Every decor item shows in its tooltip how much decoration it gives; raising the bar takes real effort and makes customers happier.
- **Luxury statues pay off:** customers buy more in a shop with a statue, and now and then one remarks that your shop is missing one.
- **Events happen more often:** 35% per day, up to 2 per day (was 10% and 1). Existing worlds are updated if they still had the old defaults.

## Staff, security and the register

- **Cashiers serve properly.** They now walk up to waiting customers and serve them instead of only waiting behind the counter.
- **Cashier upgrades** on the phone: Speed, Range and Accuracy, plus a hard-to-get top upgrade, **Handles hagglers**: the cashier closes haggling customers itself at a fair price.
- **Up to 3 guards** per shop (each one paid its own wage). Fixed: an upgraded guard stopped defending the shop.
- **Clearer Upgrades tab** on the phone: one card per staff member with its level, what the next level gives, the cost and one button.
- **Cash register:** withdraw 25 / 50 / 75 / 100% or a custom amount, with an "Are you sure?" confirmation, and **deposit** money into the register. Works on the register block and on the phone.
- **Tips are capped:** no customer ever tips more than $50,000 in total, after every multiplier.
- **Theft vs reputation:** the lower your reputation, the more likely a robbery. The Manager Goggles now show clear bars for reputation, theft risk, cleanliness, decoration and staff.

## Customers

- **Simpler haggling window:** fewer choices, big clear buttons, and one line explaining what each one does.
- **Don't get greedy:** if a customer's first offer is already good for you, pushing for more makes them leave. Instead, use the new charm options (**joke, compliment, story, small talk**). Their success depends on your Charisma; a success raises the tip (or lowers a seller's price). Limited per deal, each option once, so it can't be spammed.
- **Prices above market are much harder to sell.** How much markup customers accept now scales with your reputation.
- **200+ common Latin American first names and surnames** added to the NPC name pool.
- **1,000+ new dialogue lines** for every customer type and situation (English and Spanish).
- **Friendlier wall and roof detection:** walls can have some gaps (windows, holes) and still count.
- **No NPC spawns on shop roofs** any more.
- **Scalpers walk into shops** from the Stone tier and try to sell you overpriced packs. Say no and they leave grumbling.

## NPCs

- **The Mayor and taxes.** From the Iron tier, every 30 Minecraft days the Mayor comes to collect taxes, scaled to your shop and revenue. Pay and you are left alone until the next bill. Refuse and you face his competitive team in a real Cobblemon trainer battle: win and you pay nothing this time; lose, forfeit or walk away and he takes one random card from your binder.
- **Robbed collectors fight back:** more health and damage, they break and place blocks to reach you, follow you with an elytra if you fly, show up near you if you teleport, and have a boss bar. Existing configs with the old defaults are updated.
- **Collector trades by phone tier:** a better phone lets you give and take more cards per trade and make more trades per visit. Charisma talk options help when a trade isn't good for the collector, and the collector menu can filter your cards by price.
- **CREATive Engineer** (only when Create is installed): a wandering salesman with basic Create materials by the stack (about $3,000 each) and mid-level machines and tools ($50,000 and up).

## Phone, items and systems

- **Prices app** shows each card's Grade 10 value.
- **Ranking app:** climb past 100 shop owners up to **Don Cobbledome, owner of the Cobbledale Cobbledome** (100 trillion). Your capital counts your wallet, registers, shops, display stock and binder cards, minus what you owe.
- **$1,000,000 loan** from the Wholesaler once you own a Diamond shop.
- **Binder:** the 3 sets closest to completion and the set reward (1 Master Ball + $500,000) are always shown. Only cards you pulled or were given in game count, so a set traded between accounts pays once.
- **Binders open with "Missing" off,** so you see your own cards first.
- **Graded cards:** collect every finished order at once.
- **Shelf markup:** raise or lower every price of a fixture at once (+5% to +100%, -10%, -25%, or back to market).
- **Box-Opening Gloves** (sold only, very expensive): right-click a booster box to open all its packs at once, straight into your General Binder, with one announcement for the best pull.
- **Binders never drop on death:** General and Personal Binders stay with you and come back to the same slot.
- **Sound effects** for sales, the phone and other mod actions.
- **New gambling tables:** Lucky Seven dice table and Double or Nothing coin toss, with new 3D models.
- Call the **Furniture Seller** from the phone (Plus phone and up), once per day.

## Packs and grading

- **Much lower hit rates in packs.** Semi-god packs are now 1 in 125 and god packs about 1 in 667; big hits (Special Illustration, Hyper) are much rarer, and normal packs carry more plain and holo Rares. Pack and box prices did not change, so opening a whole booster box only rarely pays for itself.
- **Much lower high-grade odds** on cards from newer sets: a grade above 5.5 now comes up about 4% of the time (it was about 46%). Grade 10 stays about 1 in 1,000.

## Fixes and other changes

- Config defaults that changed in this version are migrated automatically in existing worlds (only values still at the old default move).
- Many smaller fixes found while porting to NeoForge.
