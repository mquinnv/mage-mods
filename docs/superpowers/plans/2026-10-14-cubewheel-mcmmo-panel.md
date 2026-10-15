# CubeWheel mcMMO skills panel — implementation plan

Spec: `docs/superpowers/specs/2026-10-14-cubewheel-mcmmo-panel-design.md` (the investigation report; its section 1 is
the captured evidence, section 3 the design). Where this plan and the spec differ, the spec wins, except the version
number (beta.79, see Task 7) and the task split below.

Goal: a HUD panel "mcMMO" showing Michael's mcMMO skill levels and XP-to-next-level as meters, fed passively from
what the ManaCube server already sends (boss bars, chat, sidebar, the /mcmmo menu), with one keybind that opens
/mcmmo.

## Global Constraints

- Module: `src/client-mods/cubewheel/` (Fabric client mod, Java, package `net.mage.cubewheel`). Build and test with
  `./gradlew build` from that directory (JDK pinned in `gradle.properties`; ~1000 JUnit 5 tests; all must pass). Run
  the focused test class with `./gradlew test --tests '<fqcn>'` while iterating.
- **No automatic commands.** Every command the mod sends must be the direct result of one keypress or click
  (ManaCube rules). The panel is fed only from data the server already pushes or from menus the player opens. The
  only command this feature sends is `/mcmmo`, on one press of a new keybind, through `CommandSender.send`.
- Pure classes (no Minecraft/Fabric imports) carry JUnit tests built from the captured strings quoted in this plan.
  Minecraft adapters stay thin and untested, guarded like `cooldown/McmmoWatcher` (catch `RuntimeException |
  LinkageError`, log once, count failures, switch off after 10).
- New pure code lives in package `net.mage.cubewheel.skills` under `src/main/java/net/mage/cubewheel/skills/`,
  tests under `src/test/java/net/mage/cubewheel/skills/`.
- Follow the codebase's conventions: tabs, Javadoc that states the captured evidence and date it rests on, `record`s
  for value types, `Optional` returns from parsers, Gson for JSON, `io/BackgroundSaver` for coalesced atomic saves.
- Commit messages in the repo's style: `feat(cubewheel): <what the user sees> (beta.79)` for the release commit,
  `feat(cubewheel): <lower-case sentence>` for the rest. End every commit message with the two attribution lines:
  `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_01NeQssGiPDeivDxdcfyBLRw`.
- Never touch `gradle.properties` except in Task 7. Never run the `deploy` scripts or any install script.

### Captured facts every task may rely on

Boss bar names (plain text, `§` codes already stripped by the capture; the live `Component.getString()` may still
carry them — strip `§.` first):

```
MINING » ʟᴠʟ. 243 [27,596 / 62,250 xp]
MINING » ʟᴠʟ. 617 [65,682 / 155,750 xp]
SWORDS » ʟᴠʟ.242 [16,101 / 62,000 xp]        ← SWORDS omits the space after "ʟᴠʟ."
ACROBATICS » ʟᴠʟ. 126 [18,419 / 33,000 xp]
UNARMED » ʟᴠʟ. 56 [12,035 / 15,500 xp]
AXES » ʟᴠʟ. 268 [11,461 / 68,500 xp]
```

`ʟᴠʟ` is Unicode small caps U+029F U+1D20 U+029F. Numbers carry thousands separators. XP needed for a level is
`1500 + 250 * level` (14,461 captured bars, levels 56..617, zero mismatches). Bars that are NOT skill bars and must be
rejected: `MANA GOLEM [SWORDS] 2⺛ - Damage Dealt: 12 for loot`, `GOLDEN KNIGHT 3⺛ - Damage Dealt: 0 for loot`,
`Anomaly: Mining Fatigue I - 0:42`, `Mana Pond (Spawn) 120/256`, `/p like sushi`. A skill bar shows while XP is
gained and disappears ~3 s after the last gain; several can show at once.

Level-up line (chat, also set on the action bar): `§a✚ §7| §aMining is now level §a§l245`. Ability unlock lines to
ignore: `§amcMMO§8 ➡ §7You have the unlocked the §a§nRupture§7 ability!`. Broadcasts naming the player:
`Qualan has reached a total Power Level of 1,900`, `Qualan has reached level 200 in WOODCUTTING`,
`*Buttle has reached level 1,000 in Mining` (others' lines look the same with another name).

Sidebar line `➼ Skills: Lvl 3148` is the mcMMO power level (sum of the 16 non-child skill levels; Smelting excluded).
`SidebarWatcher.values()` already yields it under the key `"Skills"`.

The `/mcmmo` menu, title `Skills Panel | Browsing...`, 50 slots, 17 skill items named `SKILL ▪ [CATEGORY]` with a
lore line `Level: 137/2500` (captured 2026-10-01):

| Slot | Item id | Name | Level/cap |
|---|---|---|---|
| 0 | minecraft:golden_hoe | HERBALISM ▪ [GATHERING] | 137/2500 |
| 1 | minecraft:rabbit_foot | ACROBATICS ▪ [MISC] | 134/500 |
| 2 | minecraft:brewing_stand | ALCHEMY ▪ [MISC] | 7/500 |
| 3 | minecraft:anvil | REPAIR ▪ [MISC] | 7/500 |
| 4 | minecraft:blast_furnace | SMELTING ▪ [MISC] | 150/3000 (lore also: "This Skill is a Child Skill. It does not count to your total skill level.") |
| 5 | minecraft:mace | MACES ▪ [COMBAT] | 40/1000 |
| 6 | minecraft:crossbow | CROSSBOWS ▪ [COMBAT] | 17/1000 |
| 7 | minecraft:trident | TRIDENT ▪ [COMBAT] | 26/1000 |
| 8 | minecraft:netherite_sword | SWORDS ▪ [COMBAT] | 247/1000 |
| 9 | minecraft:golden_pickaxe | MINING ▪ [GATHERING] | 293/2500 |
| 17 | minecraft:netherite_axe | AXES ▪ [COMBAT] | 131/1000 |
| 18 | minecraft:golden_axe | WOODCUTTING ▪ [GATHERING] | 196/2500 |
| 26 | minecraft:bow | ARCHERY ▪ [COMBAT] | 44/1000 |
| 27 | minecraft:golden_shovel | EXCAVATION ▪ [GATHERING] | 109/2500 |
| 35 | minecraft:bone | TAMING ▪ [COMBAT] | 500/500 |
| 36 | minecraft:fishing_rod | FISHING ▪ [GATHERING] | 75/1000 |
| 44 | minecraft:netherite_axe | UNARMED ▪ [COMBAT] | 25/1000 |

Other items in that menu: slot 31 `minecraft:book` "General Information", slot 45 `minecraft:bookshelf` "Guides &
Settings", slot 49 `minecraft:arrow` "Go Back". Example lore of a skill item (Herbalism):

```
Herbalism skill can be leveled up by
harvesting plants, vegetables and herbs.

Level: 137/2500

Abilities:
  [✔] Hylian Luck - Chance of find rare items
  [✘ LOCKED] Green Thumb - (Level 250)

㎋ Click to open Abilities Menu
```

Only levels are in the menu, never XP. The eight skills never seen as bars (Alchemy, Archery, Crossbows, Maces,
Repair, Smelting, Taming, Trident) are known only from this menu; their bar names are unverified (assume the menu
name, upper-case; `TRIDENT` singular).

### Existing code to plug into (paths under `src/client-mods/cubewheel/src/main/java/net/mage/cubewheel/`)

- `hud/Panel.java`: `record Panel(title, List<Line> lines, corner, x, y, Grid grid)`; `Line(tag, tagColor, text, color,
  right, rightColor)` with `.withProgress(f)` (row-as-bar), `.withAccent(argb)`, `.withIcon(ItemStack)`, `.withLoose()`;
  `Panel.meterColor(f)`; colour constants `WHITE, YELLOW, GRAY, GREEN, CYAN`. `hud/PanelsHud.java`: `add(Source,
  Function<CubeWheelConfig, Position>, Supplier<Position>)`, `perTick(source, position)`, `MAX_CONTENT_W = 170`.
  Row-as-bar example: `tracker/JobsPanel.java` lines 64–82 (icon, right-aligned count, accent via
  `JobsPanel.accent(Activity)`). `wheel/Icons.stack(String itemId)` makes an `ItemStack` from an id.
  `hud/KeyHint.titled(title, verb, KeyMapping)` adds a key hint to a title.
- `CubeWheelClient.onInitializeClient()` registers panels in order (Status, Events, Quests, Jobs, Focus, Boosters,
  Charms, Cooldowns) then `PanelsHud.register()` (line ~152); `ClientReceiveMessageEvents.ALLOW_GAME` listeners
  (`McmmoWatcher.onGameMessage` is the model; return true always, skip overlay); disconnect and `CLIENT_STOPPING`
  blocks call `sidebar.flush()` and `capture.flush()` (lines ~181–190) — add the new store's flush beside them.
  `CubeWheelClient.bossBars(mc)` returns `List<CaptureLog.BossBar(name, progress)>` or null.
  `CubeWheelClient.onEndTick` handles keybinds with `pressed(KeyMapping)`; Quick keys there send one command per press
  via `CommandSender.send(cmd)` — copy that pattern for `/mcmmo`.
- `tracker/local/mc/LocalSignals.onOverlayMessage` and `onActionBarPacket` each call
  `McmmoWatcher.onActionBarEvent(ActionBarFeed.Source, Component)`; add a `SkillsWatcher.onActionBarEvent` call beside
  each.
- `tracker/ContainerHook.java` line ~122: `LiveWatcher.onMenu(title, items, now)`; add `SkillsWatcher.onMenu(title,
  items, now)` beside it. Items are `ContainerScanner.ItemView(slot, id, name, lore)`.
- `sidebar/SidebarWatcher.values()` (map of sidebar numbers; key `"Skills"`), reachable via `CubeWheelClient.sidebar()`.
- Config: `config/CubeWheelConfig.java` (nested static classes per feature with `enabled` and `Position position`),
  `config/DefaultConfig.java` (`xxxPosition()` suppliers; `Position` has `corner`, `x`, `y`),
  `config/ConfigNormalizer.normalize` (fills null sections, `normalizePosition(p, default)`),
  `settings/SettingsSpec.java` (groups in category `HUD_PANELS`; helpers `toggle/intRange/choice`; `positions(d)`
  with `position(out, d, "<path>", "<Panel name>", c -> c.x.position)`), tests `settings/SettingsSpecTest.java`
  (asserts the group order list at line ~55, ids per group, "Exact positions" ids, `assertEquals(9, choices.size())`
  at line ~88) and `config/ConfigStoreTest.java`.
- Keybinds: `Keybinds.java` (static `KeyMapping` fields), `src/main/resources/assets/cubewheel/lang/en_us.json`.
- Persistence precedents: `io/BackgroundSaver` (`saveInBackground`, atomic), per-account JSON keyed by lower-case
  username as in `live/LiveWatcher.player()` (`cubewheel-vaults.json`, `cubewheel-cow.json`); save throttle precedent
  `sidebar/SidebarLinker.SaveThrottle` (10 s).
- Gating: `ServerGate.survival(cfg)`.

---

### Task 1: Skill enum, XP curve and boss-bar parser

Files: new `skills/Skill.java`, `skills/XpCurve.java`, `skills/SkillBarParser.java`; tests `skills/SkillTest.java`,
`skills/XpCurveTest.java`, `skills/SkillBarParserTest.java`. TDD: write each test first and show it failing.

- `enum Skill`: the 17 menu skills. Fields: `label` (title case: "Mining", "Woodcutting", "Trident"), `menuName`
  (upper-case as in the menu: "MINING", "TRIDENT"), `category` (`enum Category { GATHERING, COMBAT, MISC }`),
  `defaultCap` (from the table: 2500 for Herbalism/Mining/Woodcutting/Excavation; 1000 for Fishing, Swords, Axes,
  Archery, Unarmed, Taming is 500, Maces/Crossbows/Trident 1000; 500 for Acrobatics/Alchemy/Repair; 3000 Smelting),
  `child` (true only for SMELTING), `iconId` (the table's item id; Unarmed uses `minecraft:netherite_axe` like the
  menu). `static Optional<Skill> byName(String)`: case-insensitive, trims, strips `§.`, accepts the label, the menu
  name, and plurals/singulars `TRIDENTS`/`TRIDENT`, `MACE`/`MACES`, `CROSSBOW`/`CROSSBOWS`, `SWORD`/`SWORDS`,
  `AXE`/`AXES`; empty for anything else ("MANA GOLEM", "", null).
- `final class XpCurve`: `static long needed(int level)` = `1500 + 250L * level`. Test it against the captured bars:
  level 243 → 62,250; 617 → 155,750; 242 → 62,000; 126 → 33,000; 56 → 15,500; 268 → 68,500.
- `final class SkillBarParser`: `record Reading(Skill skill, int level, long xp, long needed)`;
  `static Optional<Reading> parse(String bossBarName)`. Strip `§.`, fold the small caps `ʟᴠʟ` to `lvl`, then match
  (case-insensitive, anchored) `^([A-Za-z ]+?)\s*»\s*lvl\.?\s*([\d,]+)\s*\[\s*([\d,]+)\s*/\s*([\d,]+)\s*xp\s*\]$`;
  the skill via `Skill.byName`. Tests: every captured name above parses to the right values (including the
  no-space `SWORDS » ʟᴠʟ.242` form and a `§e`-coloured copy); each non-skill bar listed above returns empty; an
  unknown skill word returns empty; `null` returns empty.

Commit: `feat(cubewheel): mcMMO skill names, XP curve and skill-bar parser`.

### Task 2: Chat and menu parsers

Files: new `skills/SkillTextParser.java`, `skills/SkillsMenu.java`; tests `skills/SkillTextParserTest.java`,
`skills/SkillsMenuTest.java`. TDD.

- `SkillTextParser`: `sealed interface Message permits LevelUp, PowerLevel, SkillMilestone`; records
  `LevelUp(Skill skill, int level)`, `PowerLevel(long power)`, `SkillMilestone(Skill skill, int level)`.
  `static Optional<Message> parse(String text, String ownName)`: strip `§.`; `LevelUp` from
  `^\W*\|?\s*([A-Za-z]+) is now level ([\d,]+)\s*$` after trimming leading `✚ |` decoration (the captured line is
  `§a✚ §7| §aMining is now level §a§l245` → LevelUp(MINING, 245)); `PowerLevel` from
  `^<ownName> has reached a total Power Level of ([\d,]+)$`; `SkillMilestone` from
  `^<ownName> has reached level ([\d,]+) in ([A-Za-z]+)$` (skill word in any case). Other players' broadcasts
  (`*Buttle has reached level 1,000 in Mining` with ownName "Qualan") return empty; the ability-unlock line returns
  empty; player chat such as `Qualan: Mining is now level 5` returns empty (a `:` before the phrase). `ownName` null
  disables the two broadcast forms only.
- `SkillsMenu`: `record Entry(Skill skill, int level, int cap, boolean child)`; a tiny input record
  `record Item(String name, List<String> lore)` so the class stays free of Minecraft and tracker types;
  `static List<Entry> parse(List<Item> items)`: an item counts when its name (codes stripped) matches
  `^(.+?)\s*▪\s*\[(GATHERING|COMBAT|MISC)\]$` and `Skill.byName` knows the first group, and a lore line matches
  `^Level:\s*([\d,]+)\s*/\s*([\d,]+)$`; `child` when any lore line contains "Child Skill". `static boolean
  isSkillsMenu(List<Item> items)`: two or more such entries (do not rely on the title). Tests: the 17 captured items
  (names and a `Level: x/y` lore line each, Smelting with the child line) parse to 17 entries with the table's
  values; the book/bookshelf/arrow items are ignored; an empty list is not a skills menu; a one-entry list is not.

Commit: `feat(cubewheel): mcMMO level-up and /mcmmo menu parsers`.

### Task 3: Skill store

Files: new `skills/SkillStore.java`; test `skills/SkillStoreTest.java` (use `@TempDir`). TDD. Pure: no Minecraft
imports; Gson is fine (already a dependency).

- `record State(int level, long xp, long needed, long xpAt, long levelAt, int cap)`: `xp < 0` means unknown
  (menu-only); `xpAt` 0 when never read from a bar.
- `final class Account { long power; long powerAt; Map<Skill, State> skills; long menuAt; }` (or a record with
  copy methods — implementer's choice, but the JSON shape below is fixed).
- `SkillStore(Path file)`: `Account account(String name)` (lower-cases the key, creates on demand); mutators return
  what changed:
  - `long bar(String account, SkillBarParser.Reading r, long now)`: sets level/xp/needed, stamps `xpAt = now` and
    `levelAt = now` when the level rose; returns the XP gained since the previous reading of that skill: `r.xp -
    prev.xp` on the same level, `r.xp + (prev.needed - prev.xp)` across one level roll-over, 0 when there was no
    previous reading or the previous xp was unknown. Never returns a negative number (a lower reading means a reset:
    return 0).
  - `void levelUp(String account, Skill s, int level, long now)`: sets level and `levelAt`; marks xp unknown (-1) and
    `needed = XpCurve.needed(level)`; never lowers a level.
  - `void menu(String account, List<SkillsMenu.Entry> entries, long now)`: sets `cap` (and `child` is the enum's);
    sets the level only when the store has none or a lower one; sets `menuAt = now`.
  - `void power(String account, long power, long now)`.
  - `Map<Skill, Long> recentGains(String account, long now, long windowMs)`: implementer keeps a bounded per-skill
    list of `(at, gain)` from `bar()`; returns the summed gain per skill within the window (skills with none
    omitted).
  - `void load()` (missing or unreadable file → empty store, logged by the caller), `void save()` writes atomically
    (temp file + move) — the adapter decides when; `boolean dirty()` / `void clearDirty()`.
- JSON shape, keyed by lower-case account name, skills keyed by lower-case enum name:

```json
{ "qualan": { "power": 3239, "powerAt": 1791938500000, "menuAt": 1790863185204,
  "skills": { "mining":  { "level": 617, "xp": 65682, "needed": 155750, "xpAt": 1791938466000, "levelAt": 1791930000000, "cap": 2500 },
              "alchemy": { "level": 7, "xp": -1, "needed": 3250, "xpAt": 0, "levelAt": 1790863185204, "cap": 500 } } } }
```

Tests: bar deltas on the same level and across a roll-over (e.g. 61,000/62,250 at 243 then 1,200/62,500 at 244 →
gain 2,450); first reading gains 0; level-up marks xp unknown and uses the curve; menu never lowers a level a bar
reported, but fills a missing one and always sets the cap; account keys are case-insensitive ("Qualan" and "qualan"
are one); round trip through a temp file reproduces every field; `recentGains` honours the window; a corrupt file
loads as empty.

Commit: `feat(cubewheel): per-account mcMMO skill store`.

### Task 4: Panel model

Files: new `skills/SkillsPanelModel.java`; test `skills/SkillsPanelModelTest.java`. TDD. Pure: produces plain rows
the adapter turns into `Panel.Line`s; no `hud` imports.

- `record Options(Mode mode, long recentMs, int max, boolean showChild)`; `enum Mode { RECENT, ALL }`.
- `record Row(Skill skill, String label, String right, double fraction, Tone tone, boolean accent, boolean capped,
  boolean unknown)`; `enum Tone { FRESH, STALE }`. `label` = `"<Skill label> <level>"` ("Mining 617"); `right` =
  `"65,682 / 155,750"` with thousands separators when xp is known, `"MAX"` when `level >= cap`, `"?"` when xp is
  unknown; `fraction` = xp/needed clamped to 0..1, 1.0 when capped, 0 when unknown; `tone` FRESH when `xpAt` is
  within `recentMs` of now, else STALE; `accent` true when the skill gained XP within the last 2 minutes
  (`recentGains(now, 120_000)` non-empty for it).
- `record Header(long power, String powerText)` with thousands separators; `record View(Header header, List<Row>
  rows)`.
- `static View build(SkillStore.Account account, Map<Skill, Long> recentGains, Options o, long now)`:
  - RECENT: skills whose `xpAt` is within `recentMs`, newest `xpAt` first, at most `max` rows; may be empty (the
    adapter hides the panel then).
  - ALL: every skill in the account, sorted by level descending then label; the child skill (Smelting) omitted
    unless `showChild`; never-seen skills (no state) are omitted — only what the store knows.
- Tests: RECENT ordering and cap at `max`; RECENT empty when nothing is recent; ALL ordering and child omission;
  MAX for Taming 500/500; `?` for a menu-only skill; FRESH/STALE by `xpAt`; accent from recent gains; number
  formatting with separators; fraction clamping.

Commit: `feat(cubewheel): mcMMO panel model`.

### Task 5: Config, defaults, normaliser and settings

Files: `config/CubeWheelConfig.java`, `config/DefaultConfig.java`, `config/ConfigNormalizer.java`,
`settings/SettingsSpec.java`; tests `config/ConfigStoreTest.java` (extend), `settings/SettingsSpecTest.java` (update
the exact lists).

- `CubeWheelConfig.Skills { public boolean enabled = true; public String show = "recent"; public int recentMinutes =
  10; public int max = 6; public boolean showChild = false; public Position position = DefaultConfig.skillsPosition(); }`
  and a field `public Skills skills = new Skills();` placed after `cooldowns`/`focus` as the file orders its sections.
- `DefaultConfig.skillsPosition()` → `top_left 4,4` like the other panels (NOT bottom_right).
- `ConfigNormalizer`: fill a null `skills`, clamp `recentMinutes` to 1..120 and `max` to 1..17, coerce `show` to
  `recent` unless it is exactly `all`, `normalizePosition(skills.position, DefaultConfig.skillsPosition())`.
- `SettingsSpec`: group "mcMMO" placed after "Focus" and before "Progress popup" in `HUD_PANELS`: toggle
  `skills.enabled` ("mcMMO panel"), choice `skills.show` with options `recent`/`all` (labels "Recently trained" /
  "All known skills"), intRange `skills.recentMinutes` 1..120 ("Recent window (minutes)"), intRange `skills.max` 1..17
  ("Rows in recent mode"), toggle `skills.showChild` ("Show Smelting (child skill)"). `positions()` gains
  `position(out, d, "skills", "mcMMO", c -> c.skills.position)` in the same relative place (after Focus).
- Update `SettingsSpecTest`: the group-order list gains "mcMMO" after "Focus"; add the group's ids; the "Exact
  positions" ids gain the skills one; the choice count assertion becomes 11 (worldFilter + nine corners + show).
  Check the actual count by reading the test's derivation rather than trusting this number blindly, and say what you
  found in the report. Extend `ConfigStoreTest` for defaults, clamping and the null-section fill.
- Do not bump `CONFIG_VERSION` (a new section with defaults needs no migration).

Commit: `feat(cubewheel): mcMMO panel config and settings`.

### Task 6: Adapters, wiring and keybind

Files: new `skills/SkillsWatcher.java`, `skills/SkillsPanel.java`; edit `CubeWheelClient.java`,
`tracker/local/mc/LocalSignals.java`, `tracker/ContainerHook.java`, `Keybinds.java`,
`src/main/resources/assets/cubewheel/lang/en_us.json`. No new tests (Minecraft adapters); the full suite must still
pass and `./gradlew build` must produce the jar.

- `SkillsWatcher` (static, like `McmmoWatcher`): `init(Path configDir)` loads a `SkillStore` at
  `configDir/cubewheel-skills.json`; `boolean onGameMessage(Component, boolean overlay)` (ALLOW_GAME listener: skip
  overlay, feed `SkillTextParser` with the own name from `Minecraft.getInstance().getUser().getName()`, apply
  `levelUp`/`power`; always return true); `onActionBarEvent(ActionBarFeed.Source, Component)` (same parser; a
  duplicate level-up is harmless because `levelUp` is idempotent); `tick(Minecraft mc)` on `END_CLIENT_TICK`: when
  `enabled && ServerGate.survival(cfg)`, read `CubeWheelClient.bossBars(mc)` (null → skip), `SkillBarParser.parse`
  each name, `store.bar(...)`; read the sidebar `"Skills"` value and `store.power(...)` when it changed; save at
  most every 10 s while dirty via `BackgroundSaver`; `onMenu(String title, List<ContainerScanner.ItemView> items,
  long now)` converts to `SkillsMenu.Item`s and applies `store.menu` when `isSkillsMenu`; `flush()` saves if dirty.
  Account name = lower-case own username. Guard every hook as `McmmoWatcher` does (catch `RuntimeException |
  LinkageError`, log once, `MAX_FAILURES = 10`).
- `SkillsPanel.panel(long now) -> Optional<Panel>`: empty when disabled, off Survival, or the model's row list is
  empty. Title via `KeyHint.titled("mcMMO · <power>", "/mcmmo", Keybinds.mcmmoSkills)` when a power level is known,
  else `"mcMMO"`. Each `Row` → `Panel.Line` row-as-bar: icon `Icons.stack(skill.iconId())`, text `row.label`, right
  `row.right`, `.withProgress(row.fraction)`, colour `Panel.WHITE` for FRESH and `Panel.GRAY` for STALE or unknown,
  `.withAccent(JobsPanel.accent(Activity.ACTIVE))` when `row.accent`; a capped row's right text is `Panel.GREEN`.
  Register in `CubeWheelClient.onInitializeClient()` after Cooldowns: `PanelsHud.add(PanelsHud.perTick(SkillsPanel::panel,
  c -> c.skills.position), c -> c.skills.position, DefaultConfig::skillsPosition)`.
- Wiring: `SkillsWatcher.init(...)` beside the other inits; `ClientReceiveMessageEvents.ALLOW_GAME.register(SkillsWatcher::onGameMessage)`;
  `SkillsWatcher.tick(mc)` from the existing `END_CLIENT_TICK` handler; `SkillsWatcher.flush()` in both flush blocks
  (disconnect and `CLIENT_STOPPING`); in `LocalSignals.onOverlayMessage` and `onActionBarPacket` one
  `SkillsWatcher.onActionBarEvent(source, message)` call beside each `McmmoWatcher.onActionBarEvent`; in
  `ContainerHook` one `SkillsWatcher.onMenu(title, items, now)` beside `LiveWatcher.onMenu`.
- Keybind: `Keybinds.mcmmoSkills` registered like the others, translation key `key.cubewheel.mcmmo_skills`, en_us
  text "Open mcMMO skills (/mcmmo)", unbound by default (`GLFW.GLFW_KEY_UNKNOWN`, as the other unbound keys are
  declared). In `CubeWheelClient.onEndTick`, exactly as the Quick keys: `if (pressed(Keybinds.mcmmoSkills) && mc.screen
  == null) CommandSender.send("/mcmmo");` — one press, one command, nothing else sends.

Commit: `feat(cubewheel): mcMMO skills panel with XP meters, fed from boss bars, level-ups and /mcmmo`.

### Task 7: Docs and release

Files: `src/client-mods/cubewheel/README.md`, `src/client-mods/cubewheel/TESTING.md` (if present; otherwise skip and
say so), `src/client-mods/cubewheel/gradle.properties`.

- README: a "### mcMMO skills panel" section under the HUD panels: what it shows (level, xp / needed meter per
  skill, power level in the title), how it is fed (the server's own XP boss bars, level-up messages, the sidebar
  power level, and the /mcmmo menu when you open it), the two modes (recently trained vs all known), that skills
  never trained since install show only a level from /mcmmo with "?", the keybind (unbound by default), and a
  "Known unknowns" list: bar names of the eight untrained skills, behaviour of capped skills, XP numbers after a
  level-up until the next bar. Add the keybind to the keybind table.
- TESTING.md checklist: bar appears while mining and the row updates; a level-up flips the level and shows `?`
  until the next bar; opening /mcmmo fills the levels of untrained skills; rows grey out after the recent window;
  the panel hides in recent mode when idle; arrange-screen drag and snap work for it.
- `gradle.properties`: `mod_version` → `0.1.0-beta.79`. Run `./gradlew build` once more and confirm the jar
  `build/libs/cubewheel-0.1.0-beta.79.jar` exists.

Commit: `feat(cubewheel): mcMMO skills panel (beta.79)`.
