# CubeWheel Skyblock Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** CubeWheel works on ManaCube Skyblock as well as Survival: it knows which gamemode you are in, keeps separate trackers, wheel and item catalog for each, and reads Skyblock's own progress menus (tasks, prestige, island quests, challenges).

**Architecture:** A `Gamemode` (SURVIVAL, SKYBLOCK, OTHER) is read from the scoreboard sidebar title, the same way the existing Survival gate works. Everything that is per-gamemode (tracker store, wheel, refresh commands, SVA catalog, menu classification) is chosen through that one value. Survival behaviour, file names and config stay exactly as they are. Skyblock gets new files and new config fields next to them.

**Tech Stack:** Java 25, Fabric (Minecraft 26.2), JUnit 5, Gradle (`./gradlew`). Mod root: `src/client-mods/cubewheel` (all paths below are relative to it; `J = src/main/java/net/mage/cubewheel`, `T = src/test/java/net/mage/cubewheel`).

**Spec:** the "Design" section below. Michael approved the proposal in chat on 2026-11-06 ("just go ahead and do it all"). It was based on the ManaCube wiki (https://wiki.manacube.com/skyblock/*). No Skyblock capture exists yet.

## Design

What Michael asked for: "my quests/trackers/wheel items all need to change for skyblock". Skyblock's `/jobs` (which the wiki calls `/tasks`) is "a different menu with a bunch of completely different things in it."

- **Gamemode detection.** The sidebar title already says `SURVIVAL` (capture 2026-11-04: `"   SURVIVAL"`). Skyblock's is expected to contain `SKYBLOCK`. That is configurable (`tracker.skyblockSidebarPattern`, default `(?i)skyblock`). No sidebar, or any other title (the hub, Parkour), is OTHER.
- **Trackers, per gamemode.** Survival keeps `config/cubewheel-tracker.json`. Skyblock uses `config/cubewheel-tracker-skyblock.json`. The HUD, Jobs and Focus panels, the tracker screen and local counting all follow `CubeWheelClient.tracker()`, which returns the current gamemode's store. OTHER returns the Survival store, which is what happens today.
- **Skyblock menus to read** (wiki wording; to be tuned from a capture later):
  1. Tasks: `/jobs` (or `/tasks`). Repeatable tasks that earn task points. Source `tasks`.
  2. Prestige: `/prestige`. 15 prestiges of 10 ranks ("Rank 1 Requirements: Harvest 25,000 crops"). Source `prestige`.
  3. Island quests: `/is quests`. 10 tiers (Beginner, Intermediate, Experienced, Master, Grand Master, Insane, Epic, Prestigious, Legendary, Godly) of 5 quests, plus a weekly island quest. Source `isquests`.
  4. Challenges: `/challenges`. 10 endless categories: "Mob / Monster / Dungeon / Task / Mining / Resource / Crop / Fish / Mini Boss / Boss Challenge". Source `sbchallenges`.
  5. Any other menu with 2 or more `Progress:` items: source `menu` (existing generic rule).
- **Refresh key per gamemode:** Skyblock sends `/jobs`, `/prestige`, `/is quests`, `/challenges`.
- **Wheel per gamemode:** a separate `wheelSkyblock` list with Skyblock commands (see Task 5).
- **SVA catalog per gamemode:** `https://api.manacube.com/api/svas/skyblock` exists (HTTP 200, ~800 KB, checked 2026-11-06). Skyblock tooltips and the catalog screen use it.
- **Survival-only, unchanged (non-goals):** event schedule, /cow daily reward, boss slice, fishing log, mcMMO skills panel, boosters, command cooldowns, homes slice. Their watchers keep their `ServerGate.survival` gate, so Skyblock never feeds Survival's files.
- **Anti-goals:** a Skyblock menu must never write into Survival's tracker file, and existing Survival configs and tracker files must load unchanged. No migration and no renames.

## Global Constraints

- Survival file names stay exactly the same: `cubewheel.json`, `cubewheel-tracker.json`, `cubewheel-cache/svas-survival.json`.
- Every new pure class has no Minecraft/Fabric imports and is unit-tested. This follows the existing `SidebarGate` / `MenuClassifier` style.
- Code comments that cite a decision quote Michael with the date, in the codebase's existing style ("Michael 2026-11-06: ...").
- Mod version: `gradle.properties` `mod_version` goes from `0.1.0-beta.96` to `0.1.0-beta.97` (Task 7 only).
- Run tests from the mod root with `./gradlew test`. Single class: `./gradlew test --tests '<fqcn>'`.
- Commit after each task with `feat(cubewheel): ...` or `fix(cubewheel): ...`. Never use `--no-verify`.

## Review Focus

1. **Switching gamemode on one connection** (Survival → hub → Skyblock through the proxy, with no reconnect). `tracker()` must change store as soon as the sidebar title changes, and pending saves of the old store must still be written. Test in Task 2.
2. **Hub or lobby (OTHER).** No menu scanning, no refresh run, and no local counting into either store. Test in Task 1 (`ServerGate.tracks`) and Task 3.
3. **Skyblock filler items** (glass panes, back arrows, "Close", category heads without counters) must not become entries. Test in Task 3.
4. **Old config file without the new fields.** It loads, gets the defaults, and the Survival wheel is untouched. Test in Task 5 (`ConfigNormalizer`) and Task 4.
5. **The Skyblock SVA cache must not overwrite Survival's** (same directory, same index file). Test in Task 6.

---

### Task 1: Gamemode detection

**Files:**
- Create: `J/sidebar/Gamemode.java`, `T/sidebar/GamemodeTest.java`
- Modify: `J/ServerGate.java` (add `gamemode`, `tracks`; `survival` delegates), `J/config/CubeWheelConfig.java` (`Tracker.skyblockSidebarPattern`), `J/config/DefaultConfig.java` (`SKYBLOCK_SIDEBAR`), `J/config/ConfigNormalizer.java:32` (default when null), `J/settings/SettingsSpec.java:252` (a text entry "Skyblock sidebar pattern" beside the Survival one), `T/ServerGateTest.java`

**Interfaces:**
- Produces:
  - `enum Gamemode { SURVIVAL, SKYBLOCK, OTHER; public String id(); }`. `id()` is `"survival"`, `"skyblock"`, `"other"`.
  - `static Gamemode Gamemode.detect(String sidebarTitle, String survivalPattern, String skyblockPattern)` (pure; matches via `SidebarGate.matches`)
  - `static Gamemode ServerGate.gamemode(CubeWheelConfig cfg)`: OTHER unless `active(cfg)`
  - `static boolean ServerGate.tracks(CubeWheelConfig cfg)`: gamemode is SURVIVAL or SKYBLOCK
  - `ServerGate.survival(cfg)` becomes `gamemode(cfg) == SURVIVAL`, with the same results as before
  - `DefaultConfig.SKYBLOCK_SIDEBAR = "(?i)skyblock"`

- [ ] **Step 1: Write the failing tests** in `GamemodeTest`:
  - `detect("   SURVIVAL", "(?i)survival", "(?i)skyblock") == SURVIVAL`
  - `detect("§6§lSKYBLOCK", ...) == SKYBLOCK`
  - `detect(null, ...) == OTHER`, `detect("", ...) == OTHER`, `detect("PARKOUR", ...) == OTHER`
  - Survival is checked first: `detect("SURVIVAL SKYBLOCK", ...) == SURVIVAL`
  - A blank skyblock pattern means Skyblock is never detected: `detect("SKYBLOCK", "(?i)survival", "") == OTHER`. This differs from `SidebarGate`'s blank=always rule, on purpose, so an empty Skyblock pattern cannot claim every server.
  - A blank survival pattern keeps today's meaning (gate off): `detect("anything", "", "(?i)skyblock") == SURVIVAL`
- [ ] **Step 2: Run** `./gradlew test --tests 'net.mage.cubewheel.sidebar.GamemodeTest'`. Expected: FAIL (no class).
- [ ] **Step 3: Implement** `Gamemode`, the config field, normalizer default, settings entry and `ServerGate` methods. Update the `ServerGate.survival` javadoc.
- [ ] **Step 4: Run** `./gradlew test`. Expected: all pass, including the existing `ServerGateTest` and `SidebarGateTest`.
- [ ] **Step 5: Commit** `feat(cubewheel): tell Survival from Skyblock by the sidebar title`

### Task 2: One tracker store per gamemode

**Files:**
- Create: `J/tracker/TrackerStores.java`, `T/tracker/TrackerStoresTest.java`
- Modify: `J/CubeWheelClient.java:66,80,103` (hold `TrackerStores`; `tracker()` returns `stores.of(ServerGate.gamemode(cfg))`; `flushTrackerSaves` flushes every loaded store)

**Interfaces:**
- Consumes: `Gamemode` (Task 1)
- Produces:
  - `final class TrackerStores { TrackerStores(Path configDir); TrackerStore of(Gamemode mode); void forEachLoaded(Consumer<TrackerStore> f); }`
  - Files: SURVIVAL and OTHER → `configDir/cubewheel-tracker.json` (the same instance). SKYBLOCK → `configDir/cubewheel-tracker-skyblock.json`.
  - Stores are created and `load()`ed on first `of()`. The Survival store is loaded eagerly in `onInitializeClient`, as today.

- [ ] **Step 1: Write the failing tests** in `TrackerStoresTest` (JUnit `@TempDir`):
  - `of(SURVIVAL) == of(OTHER)` (same instance), and `of(SKYBLOCK) != of(SURVIVAL)`
  - An entry updated in `of(SKYBLOCK)` and saved is absent from a fresh `new TrackerStores(dir).of(SURVIVAL)`, and present in a fresh `of(SKYBLOCK)`. Use `TrackerStore`'s existing update/save methods; copy the calls from `TrackerStoreTest`.
  - `forEachLoaded` visits only the stores `of()` has returned
- [ ] **Step 2: Run** `./gradlew test --tests 'net.mage.cubewheel.tracker.TrackerStoresTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** `TrackerStores` and wire it into `CubeWheelClient`. Callers of `CubeWheelClient.tracker()` stay unchanged.
- [ ] **Step 4: Run** `./gradlew test`. Expected: all pass.
- [ ] **Step 5: Commit** `feat(cubewheel): Skyblock progress lives in its own tracker file`

### Task 3: Read Skyblock's menus

**Files:**
- Create: `J/tracker/SkyblockMenus.java`, `T/tracker/SkyblockMenusTest.java`
- Modify: `J/tracker/MenuClassifier.java` (add the `classify(Gamemode, ...)` overload; the current 3-argument method stays the Survival path), `J/tracker/ContainerHook.java:121-145`, `J/tracker/SourceTag.java`, `J/tracker/local/mc/LocalSignals.java` (its `survival` gates become `tracks`), `T/tracker/MenuClassifierTest.java`

**Interfaces:**
- Consumes: `Gamemode`, `ServerGate.tracks` (Task 1); `CubeWheelClient.tracker()` (Task 2)
- Produces:
  - `static Optional<String> MenuClassifier.classify(Gamemode mode, String title, List<ItemView> items, Map<String,String> titleSources)`. SURVIVAL → the existing logic. SKYBLOCK → `SkyblockMenus.classify(items)`, then the generic ≥2 `Progress:` rule. OTHER → empty.
  - `static Optional<String> SkyblockMenus.classify(List<ItemView> items)` returns one of `"tasks"`, `"prestige"`, `"isquests"`, `"sbchallenges"`.
  - `SourceTag.of`: `"tasks"` → the JOBS tag; `"isquests"` → new `("⌂", 0xFF55FF55)`; `"sbchallenges"` → the CHALLENGES tag.

Rules for `SkyblockMenus.classify` (first item that matches decides; names and lore stripped with `ContainerScanner.strip`):
- prestige: name matches `^Rank \d+\b`, `^Rank \[✪\d+]`, or `^Prestige \d+\b`
- sbchallenges: name matches `(?i)^(mob|monster|dungeon|task|mining|resource|crop|fish|mini ?boss|boss) challenge$`
- isquests: name matches `(?i)^(beginner|intermediate|experienced|master|grand master|insane|epic|prestigious|legendary|godly)( island)? quests?$`, or any lore line contains `island quest`
- tasks: a lore line contains `task` and the item has a counter that `ProgressExtractor.extract(lore)` reads

ContainerHook changes:
- Capture as before.
- Then return unless `ServerGate.tracks(cfg)`.
- The Survival-only watchers (`SkillsWatcher.onMenu`, `LiveWatcher.onMenu`, `FishLog`) run only when the mode is SURVIVAL.
- `MenuFacts` and classification use the current mode.

ContainerScanner: none of the Skyblock sources needs a `Progress:` line. Items without a counter are already skipped (`p.isEmpty()`).

- [ ] **Step 1: Write the failing tests** in `SkyblockMenusTest`. Use items built like those in `MenuClassifierTest`, with wiki wording; label them `// wiki wording, replace with capture`:
  - `Rank 1` with lore `Harvest 25,000 crops`, `Progress: 1,200/25,000` → `prestige`
  - `Mob Challenge` with lore `Level 1 - 3,500,000 Mobs`, `120,000 / 3,500,000` → `sbchallenges`
  - `Mini Boss Challenge` → `sbchallenges`
  - `Beginner Island Quests` → `isquests`; an item `Miner` with lore `Island Quest`, `Mine 300 cobblestone`, `120/300` → `isquests`
  - `Daily Task` with lore `Task: Kill 50 Zombies`, `12/50` → `tasks`
  - Only filler (`minecraft:gray_stained_glass_pane` named `""`, `minecraft:arrow` named `Back`, `minecraft:barrier` named `Close`) → empty
- [ ] **Step 2: Add to `MenuClassifierTest`:**
  - `classify(OTHER, ...)` is empty even for a Survival prestige menu
  - `classify(SURVIVAL, ...)` equals the old 3-argument result for an existing Survival fixture
  - `classify(SKYBLOCK, ...)` of two generic `Progress:` items → `menu`
- [ ] **Step 3: Run** `./gradlew test --tests 'net.mage.cubewheel.tracker.*'`. Expected: the new tests FAIL.
- [ ] **Step 4: Implement** `SkyblockMenus`, the classifier overload, the `SourceTag` entries, the ContainerHook gating and the LocalSignals gates.
- [ ] **Step 5: Add a `ContainerScannerTest` case:** the `isquests` `Miner` item scanned into a fresh store gives one entry at 120/300, and a glass pane next to it gives none.
- [ ] **Step 6: Run** `./gradlew test`. Expected: all pass.
- [ ] **Step 7: Commit** `feat(cubewheel): read Skyblock's tasks, prestige, island quests and challenges`

### Task 4: Refresh key per gamemode

**Files:**
- Modify: `J/config/CubeWheelConfig.java` (`Tracker.skyblockRefreshCommands`, `Tracker.refreshCommandsFor(Gamemode)`), `J/config/DefaultConfig.java` (`skyblockRefreshCommands()`), `J/config/ConfigNormalizer.java`, `J/tracker/RefreshController.java:98-136`, `J/settings/SettingsSpec.java` (a list entry beside the Survival refresh commands)
- Test: `T/config/` (normalizer test class that already covers `refreshCommands`), `T/tracker/RefreshPolicyTest.java` only if the policy signature changes

**Interfaces:**
- Consumes: `Gamemode`, `ServerGate.gamemode/tracks` (Task 1)
- Produces:
  - `List<String> Tracker.refreshCommandsFor(Gamemode m)`: SKYBLOCK → `skyblockRefreshCommands`; otherwise → `refreshCommands`
  - `DefaultConfig.skyblockRefreshCommands() = List.of("/jobs", "/prestige", "/is quests", "/challenges")`

- [ ] **Step 1: Write the failing tests:**
  - A config without `skyblockRefreshCommands` normalizes to the default list
  - `refreshCommandsFor(SKYBLOCK)` returns it; `refreshCommandsFor(SURVIVAL)` returns `refreshCommands` unchanged
- [ ] **Step 2: Run** them. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - RefreshController gates on `ServerGate.tracks` instead of `survival`, both at the start and on each step.
  - It stops if the mode changes mid-run.
  - It starts with `refreshCommandsFor(mode)`.
  - The "not in Survival" message becomes "not in Survival or Skyblock".
- [ ] **Step 4: Run** `./gradlew test`. Expected: all pass.
- [ ] **Step 5: Commit** `feat(cubewheel): the refresh key opens Skyblock's menus on Skyblock`

### Task 5: Skyblock wheel

**Files:**
- Modify: `J/config/CubeWheelConfig.java` (`List<WheelNode> wheelSkyblock`, `List<WheelNode> wheelFor(Gamemode)`, `void setWheelFor(Gamemode, List<WheelNode>)`), `J/config/DefaultConfig.java` (`skyblockWheel()`), `J/config/ConfigNormalizer.java:73` (normalize `wheelSkyblock` the same way), `J/CubeWheelClient.java:466`, `J/settings/SettingsScreens.java:250` and `J/wheel/edit/WheelEditorScreen.java` (edit and reset the current gamemode's wheel; OTHER edits Survival's)
- Test: `T/config/` (the `DefaultConfig` wheel test class), `T/wheel/`

**Interfaces:**
- Consumes: `Gamemode`, `ServerGate.gamemode` (Task 1)
- Produces: `wheelFor(SKYBLOCK)` → `wheelSkyblock`; otherwise → `wheel`

`DefaultConfig.skyblockWheel()` content. Build it with the same `WheelNode` helpers as `DefaultConfig.wheel()`. Keep the Settings leaf and the dynamic `vaults` node exactly as Survival's wheel has them.
- Island ring: `/is go`, `/is home`, `/is homes`, `/is vault`, `/is upgrades`, `/is quests`, `/is team`
- Worlds ring: `/worlds`, `/warp wolfhaven`, `/warp tangleroots`, `/warp sandara`, `/warp morend`, `/warp icehaven`
- Warps ring: `/spawn`, `/warp boss`, `/warp pond`, `/warp quests`, `/warp enchanter`, `/warp crates`
- Progress ring: `/jobs`, `/taskshop`, `/prestige`, `/challenges`, `/challengeshop`, `/collection`, `/lb`
- Storage ring: `/pv` (the `vaults` node), `/vault`, `/ec`
- Trade ring: `/ah`, `/shop`, `/cubitshop`, `/trade`

- [ ] **Step 1: Write the failing tests:**
  - `skyblockWheel()` contains every command above, and every leaf command starts with `/`
  - It contains no Survival-only command (`/pquests`, `/cow`, `/isles`, `/warp crops`)
  - An old config with no `wheelSkyblock` normalizes it to `skyblockWheel()` and leaves `wheel` equal to what it loaded
  - `wheelFor(OTHER) == wheel`
- [ ] **Step 2: Run** them. Expected: FAIL.
- [ ] **Step 3: Implement.** The wheel key opens `wheelFor(ServerGate.gamemode(cfg))`.
- [ ] **Step 4: Run** `./gradlew test`. Expected: all pass.
- [ ] **Step 5: Commit** `feat(cubewheel): a Skyblock wheel with island, worlds and progress commands`

### Task 6: SVA catalog per gamemode

**Files:**
- Modify: `J/sva/SvaApi.java` (mode-aware URLs), `J/sva/SvaCache.java` (file name from mode), `J/sva/mc/SvaClient.java:40-60` (one `SvaService` per tracked mode; `service()` returns the current mode's, and Survival's for OTHER), the `gateOpen` / `SvaClient:99` / `SvaCatalogScreen:375` `survival` checks become `tracks`
- Test: `T/sva/SvaApiTest.java`, new `T/sva/SvaCacheTest.java`

**Interfaces:**
- Consumes: `Gamemode` (Task 1)
- Produces:
  - `SvaApi.ROOT = "https://api.manacube.com/api/svas"`
  - `static String catalogUrl(Gamemode m)` → `ROOT + "/" + m.id()`
  - `static String ownedUrl(Gamemode m, String uuid)`
  - Keep `BASE`, `catalogUrl()` and `ownedUrl(uuid)` as the Survival versions.
  - `new SvaCache(Path dir, Gamemode m)`: SURVIVAL → `dir`, as today. SKYBLOCK → `dir.resolve("skyblock")`. The catalog file is `svas-<id>.json`. The index and owned files live in that same directory, so the two caches never share an index.
  - `SvaService` takes the mode at construction and uses the mode's URLs.

- [ ] **Step 1: Write the failing tests:**
  - `catalogUrl(SKYBLOCK).equals("https://api.manacube.com/api/svas/skyblock")`
  - `ownedUrl(SKYBLOCK, "7f0e9af04a314e69837714bfd7ed5a90")` ends with `/skyblock/7f0e9af0-4a31-4e69-8377-14bfd7ed5a90`
  - `SvaCacheTest`: write a catalog through a Skyblock cache and through a Survival cache on the same `@TempDir`. Each reads back its own body, and the Survival file is `dir/svas-survival.json`.
- [ ] **Step 2: Run** `./gradlew test --tests 'net.mage.cubewheel.sva.*'`. Expected: FAIL.
- [ ] **Step 3: Implement.** Skyblock's catalog loads from cache asynchronously, the same way Survival's does.
- [ ] **Step 4: Run** `./gradlew test`. Expected: all pass, including `SvaServiceTest`.
- [ ] **Step 5: Commit** `feat(cubewheel): SVA tooltips and catalog use Skyblock's list on Skyblock`

### Task 7: Docs, version, build

**Files:**
- Modify: `README.md`
  - "Server gate" (~291): gamemodes
  - "Progress tracker" (~305): per-mode files and the Skyblock sources
  - "SVA catalog" (~794): per-mode URL and cache
  - "Known unknowns" (~895): Skyblock menu wording comes from the wiki and is awaiting a capture
- Modify: `TESTING.md` (a Skyblock section: detection, the 4 menus, refresh key, wheel, SVA tooltip, and that Survival is unaffected), `src/main/resources/fabric.mod.json:6` (description "Unofficial client-side helper for ManaCube Survival and Skyblock"), `gradle.properties` (`mod_version=0.1.0-beta.97`)

- [ ] **Step 1: Make the edits.**
- [ ] **Step 2: Run** `./gradlew build`. Expected: BUILD SUCCESSFUL, and `build/libs/cubewheel-0.1.0-beta.97.jar` exists.
- [ ] **Step 3: Commit** `feat(cubewheel): Skyblock support (beta.97)`
