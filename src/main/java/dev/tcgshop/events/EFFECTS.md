# Random events: the contract for effect modules

This is the contract between the events core (`dev.tcgshop.events`) and the modules that plug into it
(`dev.tcgshop.events.incident.IncidentModule` and `dev.tcgshop.events.actor.ActorModule`).

Modules **must not edit core or shared files**: `EventManager`, `ShopLife`, `ShopServer`, `CustomerEntity`,
`ShopZones` and `PhoneScreen`. Every hook a module needs is already wired. What a module does:

1. Register its effect type in `init()`.
2. Use the public API below.
3. Add its own files: classes in its package, data, lang via its own `tools/gen_lang_09_events_<part>.py`.

`init()` is called from `TcgShop.onInitialize` (after `EventManager.register()`, before datapacks load).
`IncidentModule.initClient()` is called from `TcgShopClient`.

---------------------------------------------------------------------------------------------------------

## 1. Event definitions (data)

Definitions live in `data/<ns>/tcgshop_events/<path>.json` and get the id `<ns>:<path>`.

- A datapack replaces an event by shipping a file with the same id.
- `{"enabled": false}` disables an event.
- An invalid def is dropped with a `[eventos]` warning line in the log. Invalid means:
  - an unknown effect `type`
  - a negative event with no fix
  - no effects at all
- Unknown keys are ignored, so `_comment` is fine.

```jsonc
{
  "category": "negative",          // positive | neutral | negative
  "tier": "common",                // common | uncommon | rare | extraordinary
  "template": "water",             // optional (defaults to the id); the same template never fires twice in a row
  "weight": 1.0,                   // optional; multiplied by config overrides, the history penalty and chains
  "duration": {"ticks": [6000, 12000]},   // or a number, or "day" (until the next in-game day); clamped to 200..24000
  "conditions": { ... },           // section 2
  "effects": [ {"type": "...", ...}, ... ],
  "variants": [                    // optional; one is picked at start (weighted, among those whose conditions pass)
    {"key": "pipe", "weight": 3},
    {"key": "squirtle", "weight": 1, "replace": false, "conditions": {"requires": ["cobblemon"]},
     "effects": [ {"type": "actor", ...} ]}          // added to the base effects (or replacing them when replace=true)
  ],
  "review": {"tag": "flood", "chance": 0.6, "mood": -2, "fixedChance": 0.25},   // section 6
  "notify": {"toast": true, "lines": "flood", "count": 6, "chance": 0.35},    // toast + lines spoken by customers walking in
  "chain":  {"next": "tcgshop:other", "withinDays": 3, "weightMult": 3}       // after this one, that one is likelier
}
```

**Lang keys** (Spanish and English). The prefix is `tcgshop.event.<path>` for namespace `tcgshop`, otherwise
`tcgshop.event.<ns>.<path>`. Slashes in the path become dots.

| Key | Content | When required |
|---|---|---|
| `.name` | Name | Always |
| `.desc` | Description shown on the phone | Always |
| `.start` | Short owner message when the event starts | Always |
| `.fix` | How to fix it, shown on the toast and the phone | Every def with a fix |

Customer lines are `tcgshop.evline.<lines>.<0..count-1>`.

---------------------------------------------------------------------------------------------------------

## 2. Conditions (all optional)

### Values you can set in `conditions`

| Field | Meaning | Example |
|---|---|---|
| `biomes` / `notBiomes` | Biome ids, or `#tags` | `["#minecraft:is_forest", "minecraft:plains"]` |
| `minTemp` / `maxTemp` | Biome temperature at the shop core | |
| `weather` | Any of `clear`, `rain`, `thunder`, `wet` (= rain or thunder). Thunder also matches `rain`. A biome with no precipitation counts as `clear`. | |
| `time` | Time-of-day window `[from, to]` in ticks 0..24000; may wrap around | |
| `rep` | Reputation range `[min, max]` | |
| `minTier` / `maxTier` | Shop tier key (`paper`, `wood`, `brick`, `stone`, `copper`, `iron`, `gold`, `lapis`, `diamond`, `amethyst`, `emerald`, `netherite`). An event's `minTier` defaults to `wood`, so the Paper stall gets no random events unless the event sets `"minTier": "paper"`; a variant's conditions add no tier floor. | `"wood"` .. `"netherite"` |
| `minDisplays`, `minSales`, `minRegulars`, `minStars`, `minDecor` | Minimum values | |
| `flags` / `notFlags` | Zone flags from the last recompute | `no_roof`, `no_walls`, `dark`, `bright`, `hostile`, `pets`, `pokemon`, `farm_animals`, `cozy`, `cheap`, `pricey`, `empty`, `infamy` |
| `requires` | Facilities the shop must have (next table) | |
| `nearbyPokemon` | `{"species": ["psyduck"], "radius": 24, "min": 1}` | |
| `exclusiveGroup` | Never two active events with the same group in one shop | |
| `cooldownDays` | Overrides the tier cooldown | |
| `maxPerWeek` | Per owner | |

### `requires` values

| Value | Means |
|---|---|
| `cashier` | A cashier is hired |
| `guard` / `no_guard` | A guard is hired / is not |
| `staff_slot` | At least one staff slot is free |
| `no_cashier` | No cashier is hired |
| `register` | The shop has a cash register |
| `door` | The shop has a door |
| `playtable` | The shop has a play table |
| `cobblemon` | The Cobblemon mod is loaded |
| `grading_order` | The owner has a pending CobbleGrade order |
| `clean` | `smell == 0` and no hostile mobs nearby |

### How `nearbyPokemon` is checked

The check is a pluggable predicate: `EventConditions.setNearbyPokemon(pred)`. **ActorModule registers it**, reading
species by reflection.

Without the module:
- an empty `species` list counts any `cobblemon:` entity near the zone (not counting tagged event actors);
- a non-empty `species` list makes the condition fail.

### Requirements added automatically by effects

An effect can declare extra `requires` through `EventEffect.implicitRequires()`. Actors should return
`Set.of("cobblemon")`, so a def with an actor never fires without Cobblemon. Use a variant without the actor as the
fallback.

### What the scheduler checks itself

The following are checked by the scheduler, not by the condition object:
- grace period for negatives
- minimum sales and displays for positives
- the world deck
- cooldowns
- history
- the same template twice in a row
- `maxPerWeek`
- `exclusiveGroup`

---------------------------------------------------------------------------------------------------------

## 3. Effect lifecycle

`EventEffect` objects hold **configuration**. One instance is created per effect entry in the def and is shared by
every running instance of that event. Keep per-instance state in `ctx.data()` (an `NbtCompound` saved with the zone),
or in the instance's world locks (section 5).

```java
public interface EventEffect {
    void start(EventContext ctx);                   // once, when the event starts (zone loaded, owner nearby)
    void tick(EventContext ctx);                    // every 20 ticks, ONLY while the zone is loaded
    void resolve(EventContext ctx, String how);     // when someone fixes it (any effect may call ctx.resolve(how))
    void end(EventContext ctx);                     // always, exactly once (see below). May run with the zone UNLOADED.
    void onRecompute(EventContext ctx);             // after each shop recompute (every ~60 s, zone loaded)
    void onSale(EventContext ctx, ItemStack item, long price, long market);   // each NPC sale in the shop
    void contribute(Mods.Builder b, EventInstance inst, long now);           // modifiers (cached; see invalidate)
    boolean providesFix();                          // true if this effect gives the player a way to fix the event
    boolean keepsAfterResolve();                    // true = the event keeps running after it is resolved
    Set<String> implicitRequires();
}
```

**When `end` runs:** the event was resolved, it expired, `/tcgshop event end` was used, the def was removed, or the
shop core was broken (`EventManager.onZoneRemoved`).

**What `end` must do:** remove every block and entity the effect created, and clear its actor registrations.
**Never move or destroy player items or blocks.**

**Registering a type:**

```java
EffectTypes.register("incident", (JsonObject json, String defId) -> new IncidentEffect(json, defId));
```

The factory throws `IllegalArgumentException` with a clear message when the JSON is invalid. That message reaches the
log and `/tcgshop event list`.

### `EventContext`

Fields:
- `world`, `zone`, `instance`, `def`
- `index`: this effect's position in `def.effectsFor(instance.variant)`
- `rng`: a `java.util.Random` seeded from (uid, index, call)

Methods:
- `data()`
- `now()`, `day()`
- `owner()`: may be null
- `resolve(how)`
- `invalidate()`: call after changing locks or data that affect `Mods`
- `markDirty()`
- `tellOwner(text, actionBar)`

### Timing rules

- The end time is an absolute `world.getTime()` value. It is **never extended** because the zone is unloaded:
  unloaded time still counts.
- An effect's `tick` simply does not run while the zone is unloaded.

---------------------------------------------------------------------------------------------------------

## 4. Public API (`EventManager`)

| Call | What it does |
|---|---|
| `resolve(long uid, String how)` | Marks the event fixed. All effects get `resolve`; the owner gets a toast; review records are marked resolved; world locks are cleared. If no effect `keepsAfterResolve()`, the event then ends. Returns false if the event is unknown or already resolved. |
| `isActive(long uid)` | Whether the event is running. **False for unknown or ended events and for deleted shops.** Use it for self-cleanup of orphan blocks and entities. |
| `isUnresolved(long uid)` | Running and not yet fixed. |
| `instance(long uid)` | The instance, or null. |
| `activeFor(Zone)` | The zone's running instances (read-only list). |
| `end(long uid, String outcome)` | Ends the event now. |
| `logIncident(world, zone, uid, tag, negative, mood, chance)` | Adds an extra review-memory record (section 6). |
| `resolveIncident(world, zone, uid, tag)` | Marks only that record fixed. |
| `invalidate(Zone)` | Rebuilds `Mods` on the next query. |
| `mods(Zone)` | Aggregated, clamped modifiers. `Mods.NONE` when there are no events. |
| `effectiveRep(Zone)` | Reputation plus the temporary event `repDelta`. |
| `grantMoney(world, zone, amount, tell)` | Gives money to the shop register, **capped per owner** (daily: min(abs, 10% of average daily revenue); weekly: 3x that). Returns the amount actually given. Every module reward money must go through this. |
| `addPerk(server, owner, uid, kind, factor, uses, until)` | Grants a pending perk: `wholesale_discount`, `phone_markup`, `grading_discount`, `hire_discount` or `tip`. It is removed when the event ends. |
| `state()` | The global `EventState` (actor registry below), or null when no server is running. |
| `EVENT_TAG` | `"tcgshop_event"`. |

### `EVENT_TAG`: what it means for actors

Every entity a module spawns **must** carry this command tag, plus `"tcgshop_ev_" + uid`. Tagged entities are left
out of the shop's Pokémon count in `ShopLife.scanEnvironment`, so herds do not tank safety and cute actors do not
farm the +3.

### Actor registry (`EventState`, persisted)

- `registerActor(entityUuid, uid)`: call it **before** `world.spawnEntity`, because `ServerEntityEvents.ENTITY_LOAD`
  fires on spawn.
- `unregisterActor(uuid)`
- `isLiveActor(uuid)`: use it in your `ENTITY_LOAD` purge.
- `actorEvent(uuid)` returns the uid, or -1.
- `actorsOf(uid)`
- `clearActorsOf(uid)`: the core also calls this when the event ends, as a safety net.
- `actorCount()`

Rules:
- **Never respawn a missing actor.** If its UUID is gone, end the event or skip the actor step.
- Look entities up by UUID every tick. Never cache `Entity` references.

### Interaction hooks that already exist

| Interaction | Handled by |
|---|---|
| Owner right-clicks a troublemaker visitor | `EventManager.onVisitorInteract` |
| Owner right-clicks a distracted cashier | `EventManager.onEmployeeInteract` |

Modules handle their own blocks and entities, through `Block.onUse` and Fabric `UseEntityCallback`, and **must check
`zone.owner`** (or op). `ShopProtection` does not cover `onUse`, entities or buckets.

---------------------------------------------------------------------------------------------------------

## 5. World locks (already wired into customers, doors, the register and lighting)

Call these on `ctx.instance`, then `ctx.invalidate()`. They are saved with the instance and disappear when it is
resolved or ends.

| Call | Effect | Wiring |
|---|---|---|
| `disableDisplay(BlockPos)` / `enableDisplay(BlockPos)` | The whole display is ignored by customers. | Filtered in `CustomerEntity.forDisplays` (`pickTarget`, trades) and re-checked in `decide`. |
| `disableSlot(BlockPos, int slot)` | One slot is unavailable, for example "product knocked to the floor". **The item stays in the display.** | Same as above. |
| `closeDoor(BlockPos lowerDoorHalf)` / `openDoor` | Customers do not spawn through that door. If every door is blocked, nobody spawns. A customer already heading for a blocked door waits outside in the queue. | `ShopLife.spawnAt` and `ShopLife.queueGate`. |
| `setRegisterDown(true)` | Only the **cashier** branch of `ShopServer.cashierOnDuty` fails. The owner standing at a register can still serve. | `ShopServer.cashierOnDuty`. |
| `setLightingOverride(level)` | The light customers see in the next recompute is min(real, level). | `ShopLife.recompute`. |

Doors and register presence are cached per recompute in `zone.events.doors` and `zone.events.hasRegister`. **Never
scan blocks every 20 ticks:** cache floor candidates yourself at `start` or in `onRecompute`.

---------------------------------------------------------------------------------------------------------

## 6. Review memory (how review tags are attached)

### Records

When an event starts with a `review` block, the core adds an `IncidentRecord` to `zone.events.log` (kept for 2 days).
The record holds: uid, tag, start, resolvedAt, end, mood, chance, fixedChance, and `customersDuring`. The last one
counts customers who arrived while the event was still unfixed.

A module can override the tag of a whole instance by setting `instance.reviewTag` before the record is written (at
`start`). It can also add sub-incidents with `logIncident(...)`, for example one record per puddle kind.

### What happens when a customer leaves

When a customer leaves (`CustomerEntity.finishVisit`, before stars are computed), `EventManager.reviewFor` runs over
the visit window `[arrivedAt, now]`:

1. **Overlap.** The record was unfixed during part of the visit. With probability `chance` the review key becomes
   `ev_<tag>` and `mood` is added. The worst negative wins over any positive. Strong keys (`murder`, `hit`, `infamy`,
   `shop_full`) are never replaced.
2. **Mitigated.** The record was fixed before this customer arrived (within half a day) and at least one customer saw
   it unfixed. With probability `fixedChance` the key becomes `ev_<tag>_fixed`, with no mood penalty.
3. **Fixed before any customer arrived:** nothing happens.

### Lang

Reviews use `tcgshop.review.ev_<tag>` (short label) and `tcgshop.reviewtext.ev_<tag>.<stars 1..5>.<0..7>`.

The core already ships these tag families. Reuse them before inventing new ones:

| Group | Tags |
|---|---|
| Negative | `flood`, `fire`, `dark`, `blocked`, `mess`, `dirt`, `noise`, `theft`, `slow`, `rude`, `broken`, `hot`, `crowd`, `competition`, `rumor`, `weather`, `inspect_bad` |
| Positive | `pkmn_cute`, `pkmn_rare`, `fun_event`, `influencer`, `clean`, `decor`, `inspect_good`, `staff_good` |
| `_fixed` variants | `flood`, `fire`, `dark`, `blocked`, `mess`, `dirt`, `noise`, `theft`, `slow`, `rude`, `broken`, `hot`, `crowd`, `competition`, `rumor` |

A module that needs a **new** tag adds both kinds of key in its own lang script (40 lines per tag: 5 star levels x 8).

### Event visitors

An event visitor's own review comes from its `VisitorEffect`: `selfTag`, and the inspection grade for inspectors.

---------------------------------------------------------------------------------------------------------

## 7. Core effect types (JSON)

### `modifier`

Numbers are multiplied or added together across events, then **clamped globally** by `EventsConfig.clamps`.

```jsonc
{"type": "modifier", "while": "auto",   // auto (unresolved for negatives, always otherwise) | unresolved | always | resolved
 "spawnMult": 1.2, "maxCustAdd": 1, "moodStart": -1, "patienceMult": 0.8, "buyMult": 0.9,
 "perceivedMult": 1.1, "haggleMult": 1.05, "serviceMult": 1.4,
 "revenueMult": 1.1,                    // hard cap 1.15; the bonus goes through the per-owner money cap
 "repDelta": -4,                        // EFFECTIVE reputation only (spawning and decisions); shown as the "event" factor
 "lightDelta": -3, "lighting": 4, "decorDelta": -4,
 "typeW": {"whale": 2, "thief": 3, "seller": 1.5, "trader": 1.5, "haggler": 1, "picky": 1, "regular": 1.3},
 "buy": [{"filter": "packs", "mult": 1.2}]}   // filter: packs | cards | fair_price | newest_set | old_sets | set:<id>
```

`repDelta` never feeds `settleReputation` and never touches `premiumFor`.

### `visitor`

Special customers spawned through `ShopLife.forceSpawnVisitor`. They respect capacity and the outside queue, and the
spawn is retried every 10 s while the shop is full.

- The price cap defaults to `EventsConfig.visitorPriceCap` (1.15x market).
- **They are announced only after the visit.**
- They vanish when the world reloads and count as done. They are never respawned.

```jsonc
{"type": "visitor", "customer": "WHALE",        // REGULAR | PICKY | WHALE | HAGGLER | SELLER | TRADER (never THIEF)
 "count": [1, 2], "delay": [100, 600], "gap": [40, 160], "browses": [4, 6], "purchases": [2, 3],
 "moodStart": 1, "patienceMult": 1, "buyMult": 1.3, "priceCap": 1.15, "noBuy": false,
 "filter": "most_stocked_set",                  // most_stocked_set | old_sets | newest_set | packs | cards
 "regular": false,                              // true = one of the shop's regulars comes back
 "title": "tcgshop.role.ev_inspector",          // role line shown above the head
 "lines": "inspector", "linesCount": 6,         // tcgshop.evline.<lines>.<n>, said on entering
 "selfTag": "influencer",                       // the visitor's own review key becomes ev_<selfTag>
 "troublemaker": false,                         // fix: the owner right-clicks it, or a guard calms it after ~30 s inside
 "inspector": false,                            // never buys; scores the shop 0-7: smell, light, hostiles, roof/walls, stock, no other unfixed negative, stars
 "fairSeller": false,                           // SELLER asks and accepts at least the market price
 "resolveOnVisit": false, "minStars": 3,        // fix: serve it well
 "outcome": {"grade": "stars", "goodAt": 4, "badAt": 2,       // or "inspection" (score 0..7)
             "good": {"rep": 4, "spawn": 1.3, "charity": 3, "ticks": 24000, "money": 0},
             "bad":  {"rep": -4, "charity": -3, "ticks": 12000}}}
```

### `supplier`

Lightweight; there is no new delivery system.

```jsonc
{"type": "supplier", "perk": "wholesale_discount", "factor": 0.85, "uses": 1, // or phone_markup (>1), grading_discount, hire_discount
 "spawn": "wholesaler",                                                     // or black_market; appears next to the owner
 "speedup": 0.5}                                                            // speeds up ONE pending CobbleGrade order; implies requires grading_order
```

### `reward`

Rewards are small and capped per owner.

- **Item loot may only use `tcgshop:events/*` tables.** Cards, packs, boxes and tcgshop items are filtered out even if
  someone edits a table.
- Tables shipped by the core: `tcgshop:events/snacks`, `tcgshop:events/decor`, `tcgshop:events/berries`. The berries
  table holds Cobblemon items; without Cobblemon it simply gives nothing.

```jsonc
{"type": "reward", "when": "start",            // start | resolve | end
 "money": [1000, 2500], "scale": "regulars", "perUnit": 300,   // scale: regulars | decor | displays (perUnit x count, max = money[1])
 "charity": 2, "smellReset": false, "loot": "tcgshop:events/snacks", "rolls": 1,
 "tip": 0.1,                                   // perk: +10% on the next NPC sale, capped
 "tempRep": 3, "tempRepScale": "displays", "tempRepMax": 5,     // temporary effective reputation until the event ends
 "minDecor": 5,                                // nothing below this decor score
 "rank": "rep", "rankMinRep": 60}              // only if this owner's shop has the best reputation (single player: rep >= rankMinRep)
```

### `staff`

```jsonc
{"type": "staff", "serviceMult": 1.5, "distracted": true, "lines": "distracted", "count": 6}
```

- `distracted`: fixed when the owner right-clicks the cashier.
- Implies `requires: cashier`.

### `notify`

```jsonc
{"type": "notify", "message": "lang.key",      // an extra chat line to the owner at start
 "particles": "happy",                         // happy | note | heart | smoke | angry | glow | crit | cloud, shown around the shop
 "whileUnresolved": true}
```

### `metric`

A measurable goal. Fixes the event when reached.

```jsonc
{"type": "metric", "metric": "price_ok", "count": 3}
```

| `metric` | Goal | How it counts |
|---|---|---|
| `price_ok` | Price factor >= 0 | Consecutive recomputes |
| `smell_clean` | Smell <= 5 | Consecutive recomputes |
| `lit` | Not dark | Consecutive recomputes |
| `no_hostiles` | No hostile mobs | Consecutive recomputes |
| `stocked` | Not empty | Consecutive recomputes |
| `guarded` | A guard, or 2 detectors | Consecutive recomputes |
| `fair_sales` | NPC sales at market price or below | Summed |

---------------------------------------------------------------------------------------------------------

## 8. Module effect types (filled in by the module agents)

### `incident` (IncidentModule)

```jsonc
{"type": "incident",
 "kind": "puddle",          // puddle | debris | dirt | droppings | scorch | sparks | mess | package
 "count": [3, 6],
 "near": "floor",           // door | display | register | floor | outside_door (still INSIDE zone.box())
 "fix": {"items": "#tcgshop:fix/puddle", "uses": 1, "hits": 1},   // owner-only; never a vanilla bucket or flint and steel
 "disable": "display",      // optional: lock the display/slot next to it while it exists (scorch/sparks)
 "registerDown": false,     // optional: sparks next to the register
 "closeDoor": false,        // optional: debris blocks the door
 "lighting": -1,            // optional: power failure
 "resolveWhen": "all"}      // all | any: the event resolves when all (or any) incidents are fixed
```

**Incident block rules:**
- It is placed only into air, above a solid full face, inside the zone.
- It is never placed next to redstone or fluids, in doors, or on block entities.
- `NOTIFY_LISTENERS | FORCE_STATE`; no drops; `PistonBehavior.BLOCK`; not waterloggable.
- It removes itself when `!EventManager.isActive(uid)`.
- Every placed block is also cleaned up in the effect's `end`.

### `actor` (ActorModule)

```jsonc
{"type": "actor",
 "species": "squirtle", "level": [5, 15],          // capped at the owner's party average
 "count": 1,                                       // caps: 3 per zone, 12 global
 "behaviour": "wander",                            // stay | wander | goto_door | goto_display | goto_register | goto_light | outside
 "resolve": ["shoo", "feed", "battle", "expire"], // shoo: empty hand xN; feed: cobblemon:*_berry; battle: owner only, level-capped
 "shooHits": 3,
 "closeDoor": false, "disable": "display", "registerDown": false, "lighting": -1,   // optional world locks while present
 "reward": {"onFeed": {"charity": 1, "loot": "tcgshop:events/berries"}}}            // positive actors only, capped
```

**Actor rules:**
- **Always uncatchable**: the ball hit is cancelled.
- `setInvulnerable(true)`.
- `UNBATTLEABLE` unless the resolution is the owner's battle.
- Tagged with `EVENT_TAG`, plus `"tcgshop_ev_" + uid`.
- Registered in `EventState` before `spawnEntity`.
- `setPersistent()` plus a `POKEMON_ENTITY_SAVE_TO_WORLD` cancel, so actors are never written to disk.
- Purged on `ENTITY_LOAD` when not live.
- Never respawned.
- Removed in `end`.
- `implicitRequires()` returns `Set.of("cobblemon")`.

---------------------------------------------------------------------------------------------------------

## 9. Frequency (for reference; everything is in `config/tcgshop-events.json`)

### The daily roll

- Each owner gets one seeded roll per in-game day: `Random(daySeed(worldSeed, owner, day))`, with `dailyChance` 0.10.
- If it hits, the start moment is a random tick of the day shift (`shiftStart..shiftEnd`). The event fires once that
  moment has passed, the owner is online, and the owner is within `ownerRange` (96) of one of their shops: the one
  they are in, or the nearest.
- If none of that happens by the end of the day, the event is dropped.
- At most `maxPerOwnerPerDay` (1) per owner across all shops.
- Nothing fires on day 0 of a new world.

### Picking the event

| Step | Weights / rules |
|---|---|
| Category | positive 40 / neutral 30 / negative 30 |
| Tier | common 65 / uncommon 25 / rare 8 / extraordinary 2 |
| Cooldown by tier | 5 / 10 / 20 / 40 days |
| History | last 10 events weigh x0.25 |
| Template | never the same template twice in a row |
| Grace | no negatives until the shop has 20 sales and is 3 days old |
| Positives | need 5 sales and 2 displays |
| World deck | 100 / 85 / 65 / 40 % present by tier (seeded once per world; `/tcgshop event deck reroll`) |

### Commands

`/tcgshop event list | start <id> [variant] | end <uid> | log | deck [reroll] | roll`

- `start` ignores frequency, conditions and the deck, so it works even with frequency 0. It prints why the event
  would not have been eligible.
- `/tcgshop debug eventtest` runs the self-test.
