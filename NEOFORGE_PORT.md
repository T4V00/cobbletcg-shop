# NeoForge 1.21.1 port plan

Goal: native NeoForge 1.21.1 builds of **MasterBall Traders API** and **MasterBall Traders Co.**, with the same
features as Fabric. Both loaders are built from one shared code base. The Fabric version keeps working throughout.

Out of scope (user decision, 2026-11-25): old worlds. The NeoForge build gets no ScalperTCG / pre-0.9
migration and no Fabric→NeoForge world conversion.

## Key decisions

| Topic | Decision | Why |
|---|---|---|
| Layout | MultiLoader-Template style: `common/` + `fabric/` + `neoforge/` in **each** project (API and tcgshop) | No Architectury runtime dependency. About 85–90% of the code is shared. |
| Mappings | Mojang official + Parchment `1.21.1:2024.11.17` on both loaders | NeoForge requires Mojang names. One set of names lets common code compile once. |
| Toolchain | Gradle 9.2 (unchanged). Loom stays 1.15-SNAPSHOT for Fabric. ModDevGradle 2.0.148 + NeoForm `1.21.1-20240808.144430` for common/neoforge. NeoForge 21.1.267 | Latest stable versions; no forced Loom bump. |
| API → tcgshop link | `includeBuild('../masterball-api')` in tcgshop's settings, so each loader compiles against the matching API module | Replaces the `files(...jar)` hack, which only works on Fabric. |
| Platform layer | Small in-house `Services` (Java ServiceLoader): `Platform` (paths, isModLoaded, dist), `Net` (payload register/send), `Reg` (registration returning `Supplier`s), `Hooks` (events the loader calls into) | Keeps loader code thin and readable; no third-party abstraction. |
| Registration | Every `Registry.register(...)` goes through `Reg`. Fabric registers immediately; NeoForge uses `DeferredRegister`. Item, block and entity constants become `Supplier`s (`.get()`) | NeoForge freezes registries, so a static `Registry.register` crashes. |
| Networking | The 57 payload records and their `StreamCodec`s live in common. Each loader only wires registration and sending | The payloads are plain vanilla types. |
| 12.8k card/pack item models | NeoForge: served by the API's existing virtual resource pack (with model listing added). Fabric: unchanged | No 12.8k JSON files and no 12.8k log warnings. |
| Card item renderer | Shared draw code. Fabric `BuiltinItemRenderer` / NeoForge `IClientItemExtensions` + BEWLR adapters | Neither loader has a 1:1 equivalent of the other. |
| Legacy migration | Stays on Fabric. Not ported to NeoForge (4 risky mixins + LegacyRescue removed there) | Old worlds are out of scope. |
| Recipe viewers | REI NeoForge plugin (`@REIPluginClient`). JEI/EMI already respect `c:hidden_from_recipe_viewers` | Same behavior as on Fabric. |
| GraalJS engine | Unchanged: bundled as resources, isolated classloader, moved to common | Loader-agnostic. Still needs one real NeoForge smoke test. |
| Optional mods | All reached through reflection or ids. Only `isModLoaded` changes. NeoForge builds exist for Cobblemon 1.8.4, CobbleDollars 2.0 Beta-6.1, Accessories, CobbleFurnies, AdvancementPlaques, REI. Pokeblocks has only an unofficial port | Nothing blocks the port. |
| Versions / files | tcgshop **1.1.0**, API **0.3.0** for both loaders. Jars `masterball-traders-{fabric,neoforge}-1.1.0.jar` and `masterball-traders-api-{fabric,neoforge}-0.3.0.jar` | One version number, two loader files. |

## Phases

Every phase ends at a checkpoint where the user tests before the next one starts.

Way of working for every phase (ultracode):
1. Implement per area.
2. Review each area.
3. Do a cross-area review.
4. Run selftests.

Nothing is committed or pushed unless the user asks.

### Phase 0: Preparation (short)
- Create a git repo for `masterball-api`, which has none today, so the large mechanical diffs can be reviewed and rolled back. It stays local; pushing it is up to the user.
- Work on a `neoforge-port` branch in tcgshop. `main` stays releasable.
- Make a zip backup of both project folders.

### Phase 1: Move to Mojang names (Fabric only, no behavior change)
- **API first, then tcgshop.** Run Loom `migrateMappings --mappings "net.minecraft:mappings:1.21.1"`. Then switch to `loom.layered { officialMojangMappings(); parchment(...) }`.
- Fix what the tool misses:
  - mixin target strings and descriptors (17 mixins);
  - yarn-named helpers;
  - compile errors across about 77k lines.
- Cobblemon / CobbleDollars reflection strings are not Minecraft names, so they stay as they are.
- **Checkpoint:** both Fabric jars build, `/tcgshop debug selftest` passes, and the user plays a session and finds no difference.

### Phase 2: Split into common + fabric (still Fabric only)
- Restructure both projects into `common/` and `fabric/`, and add the `Services` platform layer.
- Move all loader-free code to common: screens, renderers, game logic, the engine, payload records, config, assets and data.
- Move Fabric API calls behind `Reg` / `Net` / `Hooks` / `Platform`, and convert registry constants to `Supplier`s.
- Update the hard-coded `src/main/...` paths in the tools:
  - 67 tcgshop Python scripts;
  - the API tools;
  - `engine/build.js`, `meta_decks.js`, `promptstats.js`;
  - the `ptcgBench` path.
- Rewrite `installToInstance` for the new layout.
- **Checkpoint:** the Fabric build is functionally identical, selftests pass, and the user plays again. This build could ship as a normal Fabric update if wanted.

### Phase 3: API on NeoForge
Add `masterball-api/neoforge`:
- **Mod setup:** `neoforge.mods.toml`, `@Mod` entry points (common + `Dist.CLIENT`).
- **Registration:** `DeferredRegister` for about 12.8k items, the data component and 2 creative tabs.
- **Item models and rendering:** virtual pack model listing, BEWLR card renderer.
- **Client events:**
  - tooltip
  - ticks
  - title-screen prompt and download bar
  - HUD layer
  - reload listener
  - `AddPackFindersEvent` (replaces the pack-manager mixin)
- **Remaining mixins:** the TextureManager mixin. Also the tag mixin, unless a virtual data pack serves the tag instead.
- **REI:** the plugin.
- **Checkpoint:** a NeoForge dev client starts, cards and packs show in creative, render in hand, GUI and frames, and the art download works.

### Phase 4: tcgshop on NeoForge, server and shared side
Add `tcgshop/neoforge` with the loader glue:
- **Registration:** registries via `Reg`.
- **Networking:** 57 payloads through `RegisterPayloadHandlersEvent`.
- **Events:** about 40 server, player and lifecycle events moved to the NeoForge event bus.
- **Player data:** `AttachmentType` (copied on death).
- **Commands, worldgen and loot:**
  - commands;
  - biome modifier JSONs and structures;
  - loot through `LootTableLoadEvent`;
  - entity attributes event.
- **GraalJS engine:** smoke test (`/tcgshop debug ptcgtest`).
- **Checkpoint:** a NeoForge dedicated server in dev starts with Cobblemon NeoForge and Kotlin for Forge. The selftest, eventtest, econtest and ptcgtest commands pass.

### Phase 5: tcgshop on NeoForge, client side
- **Entry and renderers:** client entry point, block-entity and entity renderers, NPC layers.
- **Item renderers:** BEWLR for phone, tools and about 10 builtin items; extra models via `ModelEvent.RegisterAdditional`.
- **Input and HUD:** keybinds (Y), HUD layer, world-render stage (goggles boxes).
- **Text and tooltips:** tooltip event with explicit priority order after the API's; client reload listeners.
- **Mixins:** port the 9 client/common mixins to NeoForge-patched classes (ItemRenderer, PlayerRenderer, ToastComponent). Use `require=1` during the port so a broken mixin crashes instead of failing silently.
- **Checkpoint:** a NeoForge dev client reaches the full gameplay loop:
  - shop
  - NPCs
  - packs and animations
  - binder
  - phone apps
  - grading
  - duel / PTCG
  - casino
  - goggles

### Phase 6: Real testing
- The user creates a NeoForge 1.21.1 CurseForge instance with:
  - Cobblemon 1.8.4 NeoForge, Kotlin for Forge;
  - CobbleDollars Beta-6.1, Accessories + owo-lib;
  - optionally REI and CobbleFurnies.
- I install the builds there and the user plays: single player first, then a dedicated or LAN server with friends.
- Fix whatever shows up, then re-check Fabric for regressions.

### Phase 7: Release
- tcgshop 1.1.0 + API 0.3.0, Fabric and NeoForge files on the same CurseForge projects, tagged by loader. The API stays a Required relation.
- GitHub release with all 4 jars.
- CurseForge descriptions updated: NeoForge supported, NeoForge dependency list.

## Rough size
About 100–150 hours of work in total, across several sessions. Phases 1–2 are the largest mechanical steps, and the
user sees no change in game from them. Phases 3–5 are where NeoForge-specific code gets written.

## Risks to watch
- **Silent mixin failures:** mixins with `require=0` fail without errors. Temporarily set `require=1` and verify in game.
- **Dedicated server crashes:** NeoForge's dedicated server is strict about client classes. Every client reference must stay behind the `Dist.CLIENT` entry.
- **ItemRenderer patches:** NeoForge patches `ItemRenderer.render`, which affects the grading slab and badges. Verify them visually.
- **CobbleDollars on NeoForge:** only Beta-6.x exists. Check that the reflected `CobbleDollarsPlayer` class is still there.
- **Pokeblocks:** NeoForge has only an unofficial port. It is optional; its pokedoll features are skipped if the mod is missing.
