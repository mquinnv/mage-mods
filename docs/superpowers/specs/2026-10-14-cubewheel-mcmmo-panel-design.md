# CubeWheel: "mcMMO panel with meters" — investigation and design proposal

Investigation date: 2026-10-14. Checkout: `/Users/michael/Projects/mage-mods/.claude/worktrees/arc-label-and-challenge-gate`
(mod at `src/client-mods/cubewheel`, version `0.1.0-beta.77`, MC 26.2 / Fabric). Nothing in the repository was modified.

Evidence comes from the 11 capture files in
`~/Library/Application Support/PrismLauncher/instances/MacOS Optimized/minecraft/config/cubewheel-captures/`
(2026-09-30 .. 2026-10-14; 26,974 `bossbars`, 28,769 `chat`, 17,456 `actionbar`, 9,480 `sidebar`, 1,987 `container` lines).
Scan scripts and raw outputs are beside this file (`scan_mcmmo.py`, `scan2.py`, `scan3.py`, `scan*-out.txt`).

## 0. Summary

ManaCube Survival exposes mcMMO to the client through four passive channels, all of which the mod already reads
for other features, so the panel needs no new hooks and no command the mod sends itself:

| Signal | Carries | Where it already enters the mod |
|---|---|---|
| Boss bar `MINING » ʟᴠʟ. 617 [65,682 / 155,750 xp]` | skill, level, XP into level, XP needed (while gaining XP, ~3 s after the last gain) | `CubeWheelClient.bossBars(mc)` (accessor mixins), polled per tick by `EventHud.tick` |
| Chat + action bar `✚ \| Mining is now level 245` | level-up (skill, new level) | `ClientReceiveMessageEvents.ALLOW_GAME` listeners; action-bar fan-out in `LocalSignals` |
| Sidebar line `➼ Skills: Lvl 3239` | power level (= sum of the 16 non-child skill levels, verified) | `SidebarWatcher.values().get("Skills")` |
| `/mcmmo` menu "Skills Panel \| Browsing..." | every skill's `Level: N/cap` incl. never-trained ones, caps, child-skill flag, per-skill item icons | `ContainerHook.Session.scan` -> `ItemView` list |

XP needed per level fits `1500 + 250 * level` on all 14,461 captured skill bars (zero mismatches), so a meter can
be drawn for any skill whose level and XP-into-level are known, and "XP to next" is computable from the level alone.

Not available anywhere in captures: per-gain XP amounts as text, `/mcstats` text output (never sent), the XP into
level of a skill that never gained XP while the game ran (the menu shows level only), and anything for Alchemy,
Archery, Crossbows, Maces, Repair, Smelting, Taming, Tridents beyond the one menu read.

Recommended shape: a new `net.mage.cubewheel.skills` package (pure parsers/model/store + one Minecraft adapter), a
"mcMMO" panel registered in `PanelsHud` like Cooldowns, persisted per account in `config/cubewheel-skills.json`,
an optional "Open mcMMO skills" keybind that sends `/mcmmo` on a press (same pattern as the Quick keys), and settings
in the existing "HUD panels" category.

## 1. Evidence

### 1.1 Boss bar: the mcMMO XP bar (primary signal)

Capture kind `bossbars`; shape `{"t":…,"kind":"bossbars","bars":[{"name":"<plain text>","progress":<float>}]}`; written
only when the list changes. First skill bar seen: `{"t":1790783173073,"kind":"bossbars","bars":[{"name":"MINING » ʟᴠʟ. 243 [27,596 / 62,250 xp]","progress":0.44330925}]}`.

Exact name format: `SKILL » ʟᴠʟ. <level> [<xp>, / <needed> xp]` where SKILL is upper-case ASCII, `ʟᴠʟ` is Unicode
small caps (U+029F U+1D20 U+029F), numbers carry thousands separators, and one skill omits the space after the dot:
`SWORDS » ʟᴠʟ.242 [16,101 / 62,000 xp]` (every SWORDS bar; the other skills have `ʟᴠʟ. 243`). `progress` is the
bar's server-set target percent (`LerpingBossEvent.targetPercent`); it equals `xp/needed` within 0.002 in all but 6
of ~14.5k samples (one-tick lag of the number vs the text). The text is authoritative.

Skills seen as bars (rows = captured changes; dates):

| Skill | Rows | First | Last |
|---|---|---|---|
| Acrobatics | 392 | 09-30 `ACROBATICS » ʟᴠʟ. 126 [18,419 / 33,000 xp]` | 10-13 `… ʟᴠʟ. 249 [60,456 / 63,750 xp]` |
| Axes | 4,348 | 09-30 lvl 131 | 10-13 `AXES » ʟᴠʟ. 268 [11,461 / 68,500 xp]` |
| Excavation | 54 | 09-30 lvl 109 | 10-12 lvl 157 |
| Fishing | 643 | 09-30 lvl 62 | 10-12 lvl 160 |
| Herbalism | 998 | 09-30 lvl 125 | 10-11 lvl 193 |
| Mining | 3,931 | 09-30 lvl 243 | 10-14 `MINING » ʟᴠʟ. 617 [65,682 / 155,750 xp]` |
| Swords | 3,252 | 09-30 lvl 242 | 10-13 lvl 351 |
| Unarmed | 4 | 10-12 `UNARMED » ʟᴠʟ. 56 [12,035 / 15,500 xp]` | 10-13 lvl 56 |
| Woodcutting | 839 | 09-30 lvl 173 | 10-11 lvl 362 |
| Alchemy, Archery, Crossbows, Maces, Repair, Smelting, Taming, Tridents | 0 | never gained XP during a capture | |

Behaviour: a skill's bar appears on an XP gain and is gone about 3 s after the last gain (median visible span 3.0 s
for acrobatics/fishing/excavation; longer spans, p90 16–30 s, while mining/chopping continuously). Several skill bars
show at once (342 frames had >1 skill bar). So between gains the panel holds the *last seen* values; there is no
continuous feed.

XP curve: `needed == 1500 + 250 * level` for every captured bar of every skill (14,461 samples, 0 mismatches).
Observed for levels 56..617; assumed to hold beyond (uncertain outside that range).

Other bars that must not be mistaken for skills: `MANA GOLEM [SWORDS] 2⺛ - Damage Dealt: 12 for loot`, `GOLDEN
KNIGHT …`, `Anomaly: Mining Fatigue I - 0:42`, `Mana Pond (Spawn) 120/256`, `/p like sushi`. An anchored regex on
`» ʟᴠʟ … [ … / … xp]` excludes all of them.

Feeds: current level, XP into level, XP to next, recent gains (deltas between consecutive readings), rate.

### 1.2 Level-up message (chat and action bar)

Exact text: `§a✚ §7| §aMining is now level §a§l245`. Captured 225 times as `chat` lines (all `overlay` absent, i.e.
real chat messages; `json` is the plain legacy string, not a component tree) and 122 times as `actionbar` lines (the
same text is also set on the action bar). Skills seen: Mining (69), Fishing (34), Axes (33), Acrobatics (28),
Woodcutting (24), Herbalism (20), Swords (17). Sample: 2026-09-30 11:47:19 `… Mining is now level §a§l244`.

Each is followed within ~1 s by the sidebar `Skills: Lvl` value rising by exactly one (25/25 checked).

Related, same prefix style, at ability unlocks (2026-10-01):
`§amcMMO§8 ➡ §7You have the unlocked the §a§nRupture§7 ability!\n§8§l* §7Ability Rank: §e( §l2§e )` (also
Counter Attack, Swords Limit Break). Informational only.

Feeds: level (authoritative), a "levelled up" flash. XP into level resets to a small value at that moment but the
exact value only comes with the next bar.

### 1.3 Sidebar: power level

Line `➼ Skills: Lvl 3148§0§7§r` in the SURVIVAL sidebar; 8,809 captured sidebar snapshots carry it; 246 distinct
values from 1866 (2026-09-30 11:44) to 3239 (2026-10-14 18:41). On 2026-10-01 09:59 the value was 1988 and the
`/mcmmo` menu read at the same minute summed to 1988 over the 16 non-child skills (Smelting excluded, see 1.4), so
`Skills: Lvl` **is** the mcMMO power level. `SidebarParser.parse` already yields `"Skills" -> 1988.0`
(`SidebarWatcher.values()`), and `tracker.sidebarLinks` already consumes it for prestige objectives.

Feeds: power level, live; also a sanity check (sum of known levels must not exceed it).

### 1.4 `/mcmmo` menu: "Skills Panel | Browsing..."

Captured once: 2026-10-01 09:59:45, `container` line with `"title":"Skills Panel | Browsing..."`,
`"afterCommand":"/mcmmo"`, `"afterCommandMs":380`, 50 slots. (3 s earlier a `//mcmmo` typo produced vanilla's
"Unknown or incomplete command" line — the `/mcmmo` command itself worked.) The title is plain text, unlike most
ManaCube menus whose titles are custom-font glyphs.

17 skill items, name `SKILL ▪ [CATEGORY]`, lore (exact, Herbalism):

```
Herbalism skill can be leveled up by
harvesting plants, vegetables and herbs.

Level: 137/2500

Abilities:
  [✔] Hylian Luck - Chance of find rare items
  …
  [✘ LOCKED] Green Thumb - (Level 250)

㎋ Click to open Abilities Menu
```

| Slot | Item id | Name | Level/cap on 2026-10-01 |
|---|---|---|---|
| 0 | golden_hoe | HERBALISM ▪ [GATHERING] | 137/2500 |
| 1 | rabbit_foot | ACROBATICS ▪ [MISC] | 134/500 |
| 2 | brewing_stand | ALCHEMY ▪ [MISC] | 7/500 |
| 3 | anvil | REPAIR ▪ [MISC] | 7/500 |
| 4 | blast_furnace | SMELTING ▪ [MISC] | 150/3000 — lore adds "This Skill is a Child Skill. It does not count to your total skill level." |
| 5 | mace | MACES ▪ [COMBAT] | 40/1000 |
| 6 | crossbow | CROSSBOWS ▪ [COMBAT] | 17/1000 |
| 7 | trident | TRIDENT ▪ [COMBAT] (singular) | 26/1000 |
| 8 | netherite_sword | SWORDS ▪ [COMBAT] | 247/1000 |
| 9 | golden_pickaxe | MINING ▪ [GATHERING] | 293/2500 |
| 17 | netherite_axe | AXES ▪ [COMBAT] | 131/1000 |
| 18 | golden_axe | WOODCUTTING ▪ [GATHERING] | 196/2500 |
| 26 | bow | ARCHERY ▪ [COMBAT] | 44/1000 |
| 27 | golden_shovel | EXCAVATION ▪ [GATHERING] | 109/2500 |
| 35 | bone | TAMING ▪ [COMBAT] | 500/500 (capped) |
| 36 | fishing_rod | FISHING ▪ [GATHERING] | 75/1000 |
| 44 | netherite_axe | UNARMED ▪ [COMBAT] | 25/1000 |

Also: slot 31 book "General Information", slot 45 bookshelf "Guides & Settings" (`㎋ Click to Open`), slot 49 arrow
"Go Back" (lore `Keystone`). No Salvage item. No XP numbers anywhere in the menu, only levels. The server's main menu
(glyph title `⻔⻔⻔⻔⻔⻔⻔⻔㏰`) has a `SKILLS` book "Level up 15 skills to unlock unique abilities and bonuses" — what it
opens was not captured (probably this panel; uncertain). The "15" does not match the 16 counted skills.

Feeds: level and cap of every skill, including the eight never seen as bars; per-skill icon ids; child-skill flag.

### 1.5 Broadcasts naming the player

`Qualan has reached a total Power Level of 1,900` / `… of 2,000` (own; others' lines are frequent: every 100) and
`Qualan has reached level 200 in WOODCUTTING` (own, once; skill upper-case here, title case in some others'
lines: `*Buttle has reached level 1,000 in Mining`). Optional confirmation signal only; the own username is
`Minecraft.getInstance().getUser().getName()` (used by `LiveWatcher.player()`).

### 1.6 mcMMO XP boosters (context, not needed for the meters)

Item lore: `➟ +15% Fishing McMMO XP` (Relic Rod), `➟ +10% Fishing MCMMO XP` (Wavecaster), set bonus `➟ +20% MCMMO
Boost` (Dragonscale), `➟ Bonus Herbalism McMMO XP` / `Multiplier: +15%` (Spooky Splitter). Consumable "McMMO Booster"
item: `You picked up 1  McMMO Booster` (chat), `+1  McMMO Booster` (action bar loot), crate wins `20 min McMMO
XP Booster`. No "booster activated / running" message was captured, so `BoosterParser`'s formats stay unverified for
mcMMO boosters.

### 1.7 Not evidenced (plainly)

- `/mcstats`, `/mcrank`, `/mctop`, `/skills` were never sent; no mcMMO text stats output exists in captures.
- No per-gain XP text ("+12 XP"); gains are only visible as bar deltas.
- XP into level for Alchemy, Archery, Crossbows, Maces, Repair, Smelting, Taming, Tridents (levels known once from
  the menu; Taming is capped so may never show a bar).
- The exact bar names for those eight skills (e.g. `TRIDENT` vs `TRIDENTS`, `CROSSBOWS`); the menu says `TRIDENT`.
- The "Abilities Menu" and "Guides & Settings" sub-menus; whether anything there shows XP.
- Whether `/mcmmo` always opens the panel (one success, one `//mcmmo` typo).
- Any own-account mcMMO booster activation message.

## 2. How the existing code works (what the new panel plugs into)

Paths are under `src/client-mods/cubewheel/src/main/java/net/mage/cubewheel/`.

### 2.1 Panel registration and stacking
- `hud/PanelsHud.java`: one `HudElement` drawing every panel. `PanelsHud.add(Source s, Function<CubeWheelConfig,
  Position> position, Supplier<Position> def)`; a `Source` is `Function<Long, Optional<Panel>>` asked every frame
  (must be cheap; empty = hidden). `PanelsHud.perTick(source, position)` caches the built panel per client tick.
  Panels sharing a corner stack in **registration order** and share the widest width.
- Registration lives in `CubeWheelClient.onInitializeClient()` (Status, Events, Quests, Jobs, Focus, Boosters, Charms,
  Cooldowns, then `PanelsHud.register()`). Comment there: panels that come and go go last.
- `hud/HudLayout.java`: corners `TOP_LEFT/TOP_RIGHT/BOTTOM_LEFT/BOTTOM_RIGHT/BOTTOM_CENTER/CUSTOM`, `GAP = 3`,
  `reserve()` keeps the top right clear of effect icons (`TrackerHud.lastBottom()`).
- `hud/ArrangeScreen.java` + `hud/Snap.java`: dragging a panel saves `corner = "custom"` with exact x/y, or snaps it
  back into a corner's stack within 12 px. Panels are identified by `PanelsHud.Placed(source, title, box)`.

### 2.2 Panel model and meters
- `hud/Panel.java`: `record Panel(title, List<Line> lines, corner, x, y, Grid grid)`; title drawn gold.
  `Line(tag, tagColor, text, color, right, rightColor)` plus builders: `.withProgress(f)` (the whole row becomes a
  bar: `METER_TRACK` behind, fill in `Panel.meterColor(f)` — steady teal, green at 1.0), `.withTall()` (double-height
  boxed bar with bright fill edge, text centred — the Focus panel), `.withGauge(f)` (thin capacity bar under the row,
  `gaugeColor` green/yellow/red), `.withAccent(argb)` (left-edge bar; Jobs uses cyan for recent activity),
  `.withIcon(ItemStack)` (8 px item), `.withLoose()`, `Line.split(left, right, color)`, `Line.pieces(...)`.
  `PanelsHud.MAX_CONTENT_W = 170`. Colours: `WHITE, YELLOW, GRAY, GREEN, CYAN`.
- Examples: `tracker/JobsPanel.java` lines 64–82 (row-as-bar with icon, right-aligned count, accent),
  `tracker/FocusPanel.java` `panel()` (tall bar `count · percent%`, dim loose detail lines), `charms/CharmsPanel.java`
  (gauge). `wheel/Icons.stack(String itemId)` makes icon stacks from ids like `minecraft:golden_pickaxe`.
- `hud/KeyHint.titled("Status", "move", Keybinds.arrange)` puts a key hint into a title.

### 2.3 Config, defaults, normalisation, settings
- `config/CubeWheelConfig.java`: one static nested class per feature (`Cooldowns`, `Focus`, `Charms`, `Status` …)
  with `enabled` and `Position position` (`corner`, `x`, `y`). `config/DefaultConfig.java` has `xxxPosition()`
  suppliers (all top-left 4,4 except Charms bottom_center 0,62) and `CONFIG_VERSION = 12` with migrations in
  `ConfigStore.migrate` (a bump is only needed if existing files must change, e.g. a new wheel leaf).
- `config/ConfigNormalizer.normalize` fills a missing section (`if (c.focus == null) …`) and
  `normalizePosition(p, default)`; new sections must be added there or old files load with nulls.
- `settings/SettingsSpec.java`: pure data for the YACL screen; category `HUD_PANELS` has one `Group` per panel
  (toggle/intRange/choice/text helpers) and a collapsed "Exact positions" group built by `positions(d)` with one
  `position(out, d, "<path>", "<Panel name>", c -> c.x.position)` per panel. `settings/SettingsScreens.java` renders
  it generically. `src/test/.../settings/SettingsSpecTest.java` asserts the exact group order, ids per group,
  "Exact positions" ids, and `choices.size() == 9` — all must be updated with a new panel.
- Keybind toggles for panels (`handleJobsPanelKey`) flip a config boolean and `saveToggle` (skipped when
  `config.lastLoadOk()` is false). Keybinds: `Keybinds.java` + `assets/cubewheel/lang/en_us.json`.

### 2.4 Gating and the no-auto-command rule
- `ServerGate.active(cfg)` (host match) and `ServerGate.survival(cfg)` (sidebar title matches
  `tracker.survivalSidebarPattern`); ManaCube-specific panels check `survival`.
- `CommandSender.send(cmd)` is the only sending path and refuses off-gate. All sends are one physical press/click
  (`pressed(KeyMapping)` with `PressGate` drains repeats): Quick keys in `onEndTick` are the precedent for
  "a key that sends one configured command". No timers send anything.

### 2.5 Data feeds
- Chat: `ClientReceiveMessageEvents.ALLOW_GAME.register(X::onGameMessage)` in `CubeWheelClient`; listeners return
  `true` always; overlay (action-bar) messages are skipped there (`McmmoWatcher.onGameMessage`).
- Action bar: `tracker/local/mc/LocalSignals.onOverlayMessage` (Hud mixin) and `onActionBarPacket` (packet mixin)
  explicitly call `McmmoWatcher.onActionBarEvent(source, message)`; plus a per-tick poll of
  `HudAccessor.cubewheel$getOverlayMessage()` deduped by `tracker/local/ActionBarFeed`. A new watcher is wired by
  adding one call beside the `McmmoWatcher` ones (or by having `McmmoWatcher` forward).
- Boss bars: `CubeWheelClient.bossBars(mc)` returns `List<CaptureLog.BossBar(name, progress)>` via
  `mixin/BossHealthOverlayAccessor` + `LerpingBossEventAccessor` (optional mixins; returns null once they fail).
  `events/EventHud.tick` calls it every tick and `events/LiveEvents.running` regex-matches names — the model for a
  skill-bar reader.
- Sidebar: `sidebar/SidebarWatcher.tick` every 10 ticks, `values()` map, `SidebarLinker` pushes "Skills" into
  prestige objectives.
- Menus: `tracker/ContainerHook` scans server menus at ticks 5 and 20 and on close, builds
  `ContainerScanner.ItemView(slot, id, name, lore)` and calls, in order: capture, `LiveWatcher.onMenu` (/cow, /pv),
  `FishLog.scan`, `MenuFacts`, `MenuClassifier`/`ContainerScanner`. A skills-menu reader is one more call there.
  `MenuClassifier` must not claim the Skills Panel: its items have no "Progress:" lines and no quest/job words, so it
  falls through today (verified by reading the classifier rules; not run).
- Learning/persistence precedents: `cooldown/McmmoCooldowns` learns ability cooldowns (activation→refresh) and
  saves a `label -> seconds` map to `config/cubewheel-mcmmo.json` (tmp file + atomic move). `tracker/FishLog`
  turns the /fish menu into a static table saved via `io/BackgroundSaver` (`saveInBackground` coalesces, atomic).
  Per-account stores key by lower-case username: `cubewheel-vaults.json` `{"qualan": 4}`, `cubewheel-cow.json`
  `{"qualan": {…}}` (`LiveWatcher.player()`). `cubewheel-tracker.json` is a single store, not per host.
  Save throttling precedent: `SidebarLinker.SaveThrottle` (10 s) with `flush()` on disconnect/stop in
  `CubeWheelClient`.
- Tests: pure classes (no Minecraft imports) carry JUnit 5 tests built from captured strings
  (`McmmoParserTest`, `BossTest`, `SidebarParserTest`); adapters stay thin and untested.

## 3. Proposed design

### 3.1 Package and classes (`net.mage.cubewheel.skills`)

Pure (unit-tested, no Minecraft imports):
- `Skill` enum: the 17 menu skills with `label` ("Mining"), `menuName` ("MINING"), `category` (GATHERING/COMBAT/MISC),
  `defaultCap` (from the 2026-10-01 menu: 2500/1000/500/3000), `child` (Smelting), `iconId` (the menu's item id:
  `minecraft:golden_pickaxe` …; Unarmed shares `netherite_axe` in the menu — consider `minecraft:air`/a fist stand-in,
  uncertain), `byName(String)` tolerant of case, `TRIDENT`/`TRIDENTS`, `MACE`/`MACES`.
- `XpCurve.needed(level) = 1500 + 250L * level` (documented as observed on 14,461 bars, levels 56–617).
- `SkillBarParser.parse(String name) -> Optional<Reading(skill, level, xp, needed)>`: strip `§.`, map small caps
  `ʟᴠʟ` to `lvl` (or match `[lʟ][vᴠ][lʟ]`), regex
  `^([A-Za-z ]+?)\s*»\s*lvl\.?\s*([\d,]+)\s*\[\s*([\d,]+)\s*/\s*([\d,]+)\s*xp\s*\]$` (case-insensitive). Rejects
  `MANA GOLEM [SWORDS] …`, `Anomaly: …`.
- `SkillTextParser.parse(String chat, String ownName) -> Optional<Message>`: `LevelUp(skill, level)` from
  `^\W*\|?\s*([A-Za-z]+) is now level ([\d,]+)$` after stripping codes; optional `Milestone(skill, level)` /
  `PowerLevel(n)` from own-name broadcasts; player chat excluded via `BoosterParser.isPlayerChat` (shared helper).
- `SkillsMenu.parse(List<ItemView>) -> List<MenuEntry(skill, level, cap, child)>`: item name
  `^(.+?)\s*▪\s*\[(GATHERING|COMBAT|MISC)\]$`, lore `^Level:\s*([\d,]+)\s*/\s*([\d,]+)$`, child flag from "Child
  Skill". Recognise the menu by two or more such items (not by title; title is plain today but may change).
- `SkillStore` (per account, persisted): `Map<String account, Account{ long power, powerAt; Map<Skill, State> }>`,
  `State(int level, long xp, long needed, long xpAt, long levelAt, int cap, Source source)` where `xp < 0` means
  "unknown" (menu-only). Methods: `bar(account, Reading, now)` (returns the XP gained since the previous reading,
  handling a level roll-over as `xp + (prevNeeded - prevXp)`), `levelUp(account, skill, level, now)` (sets level, xp
  unknown unless a bar follows, `needed = XpCurve`), `menu(account, entries, now)` (sets level/cap; never lowers a
  level a bar reported later), `power(account, n, now)`, `recentGains(account, windowMs)`, `load()/save()` (Gson,
  `BackgroundSaver`), `dirty` flag for a throttle. Pure.
- `SkillsPanelModel.build(Account, Options, now) -> List<Row>`: ordering, which skills, labels, fractions, colours
  (see 3.3). Pure, fully tested.

Minecraft adapters:
- `SkillsWatcher`: `init(configDir)` (load store), `onGameMessage` (ALLOW_GAME, skips overlay), `onActionBarEvent`
  (called from `LocalSignals` beside `McmmoWatcher`, own `ActionBarFeed` — only the level-up line matters, so
  duplicates are harmless; chat alone would suffice, the action-bar path is belt and braces), `tick(mc)` on
  `END_CLIENT_TICK`: read `CubeWheelClient.bossBars(mc)` (null-safe), feed `SkillBarParser` for each bar, read
  `CubeWheelClient.sidebar().values().get("Skills")` for power; `onMenu(title, items, now)` from
  `ContainerHook.Session.scan`; save throttle (10 s, as `SidebarLinker.SaveThrottle`) and `flush()` on
  disconnect/stop. Guarded like `McmmoWatcher` (`MAX_FAILURES`, switch off for the session). Active only while
  `skills.enabled && ServerGate.survival(cfg)`.
- `SkillsPanel.panel(long now) -> Optional<Panel>`: wraps `SkillsPanelModel` rows into `Panel.Line`s, registered
  with `PanelsHud.perTick`.

### 3.2 Data flow and precedence
1. Boss bar reading: authoritative for level, xp, needed; stamps `xpAt`. Deltas feed `recentGains`.
2. Level-up message: authoritative for level; marks xp unknown (shown as 0 of `XpCurve.needed(level)` with a "?"
   until a bar confirms; in practice the next bar follows within seconds while the activity continues).
3. Menu: authoritative for cap and child flag; sets level when the store has none or an older one; never lowers.
4. Sidebar: power level, live; the panel shows it in the header. If the sum of known levels exceeds it, log once
   (data integrity, not shown).
5. Capped skill (`level >= cap`, e.g. Taming 500/500): full green bar labelled `MAX`; bars for it are not expected
   (uncertain).

### 3.3 Panel layout

Title: `mcMMO · 3,239` (power level), with `KeyHint.titled` adding `· /mcmmo [K]` when the new key is bound.

One row per shown skill, Jobs-style row-as-bar:

```
mcMMO · 3,239 · /mcmmo [K]
⛏ Mining 617            65,682 / 155,750    ← icon, "Skill level", right part, bar = xp/needed (teal)
⚔ Swords 351            30,630 / 89,250
🪓 Axes 268                 11,461 / 68,500
🐾 Taming 500                      MAX       ← green full bar
⚗ Alchemy 7                         ?       ← level from menu only: empty track, grey text
  levels from /mcmmo 13 d ago              ← dim loose line, only when the newest menu read is old (optional)
```

- Icons: the menu's item ids via `Icons.stack`, drawn at 8 px like Cooldowns rows.
- Right part: `xp / needed` when known (compact `65.7k / 155.8k` if it exceeds `MAX_CONTENT_W`, decided in the model
  with a `Fit`-style width budget — uncertain, tune in game); `MAX` when capped; `?` when unknown.
- Accent: cyan left bar (`JobsPanel.accent(Activity.ACTIVE)`) for a skill that gained XP in the last 2 min, dim for
  10 min, mirroring Jobs.
- Recent-gain flash: for 3 s after a gain the right part reads `+312` in cyan instead of the totals (optional,
  cheap because `perTick` rebuilds each tick).
- Stale colouring: text white while `xpAt` is within `skills.recentMinutes`, grey after; a skill never seen as a bar
  is grey with `?`.
- Which skills (config `skills.show`):
  - `recent` (default): skills with a bar reading within `skills.recentMinutes` (default 10), most recent first,
    at most `skills.max` (default 6). Empty list hides the panel, like Cooldowns/Boosters, so it is a "what am I
    training now" meter strip.
  - `all`: every non-child skill the store knows, sorted by level descending (child Smelting omitted unless
    `skills.showChild`), never-seen ones grey with `?`; the panel is always on in Survival.
  - `pinned`: the skills listed in `skills.pinned` (TextList), in that order, plus recent ones — uncertain whether
    wanted; cheap to add later, so not in the first cut.
- Pace/ETA (optional, second cut): xp/min over the last 5 min of gains → `~18 min` to next level, as the Focus panel's
  pace line; `tracker/Pace.java` exists and may be reusable.

### 3.4 Persistence
`config/cubewheel-skills.json`, keyed by lower-case username (as `cubewheel-vaults.json` / `cubewheel-cow.json`):

```json
{ "qualan": { "power": 3239, "powerAt": 1791938500000,
  "skills": { "mining": { "level": 617, "xp": 65682, "needed": 155750, "xpAt": 1791938466000,
                          "levelAt": 1791930000000, "cap": 2500 },
              "alchemy": { "level": 7, "xp": -1, "needed": 3250, "xpAt": 0, "levelAt": 1790863185204, "cap": 500 } } } }
```
Written through `BackgroundSaver` (coalesced, atomic), at most every 10 s while dirty, and flushed from the existing
disconnect/`CLIENT_STOPPING` blocks in `CubeWheelClient`. Loads at init; survives restarts, so `all` mode shows
yesterday's levels immediately (grey until confirmed).

### 3.5 Config and settings
- `CubeWheelConfig.Skills { boolean enabled = true; String show = "recent"; int recentMinutes = 10; int max = 6;
  boolean showChild = false; Position position = DefaultConfig.skillsPosition(); }`, field `public Skills skills`.
- `DefaultConfig.skillsPosition()` → `top_left 4,4` (stacks under the others; the user can drag it).
  Alternative: `bottom_right` is where Michael's minimap sits (per `charmsPosition` comment) — avoid.
- `ConfigNormalizer`: fill null section, clamp `recentMinutes` 1..120, `max` 1..17, `show` to a known value,
  `normalizePosition`.
- `SettingsSpec`: group "mcMMO" after "Focus" (before "Progress popup"): toggle `skills.enabled`, choice
  `skills.show` (recent/all), intRange `skills.recentMinutes`, intRange `skills.max`, toggle `skills.showChild`;
  `positions()` gains `position(out, d, "skills", "mcMMO", c -> c.skills.position)`.
- No `CONFIG_VERSION` bump needed (new section with defaults). Bump to 13 only if the optional wheel leaf is added.

### 3.6 Keybind (allowed: one press → one command)
`Keybinds.mcmmoSkills` = `key.cubewheel.mcmmo_skills` ("Open mcMMO skills (/mcmmo)"), unbound by default. Handler in
`CubeWheelClient.onEndTick`: `if (pressed(Keybinds.mcmmoSkills) && mc.gui.screen() == null) CommandSender.send("/mcmmo");`
— identical to the Quick-key path; `CommandSender` refuses off-gate. The panel title shows the hint. Opening the menu
refreshes every skill's level and cap through `onMenu`. Optional (needs migration v13): a `Skills` leaf
(`minecraft:book`, `/mcmmo`) under More, or in the "Party quests" arc.

### 3.7 Registration order and gating
`PanelsHud.add(PanelsHud.perTick(SkillsPanel::panel, c -> c.skills.position), c -> c.skills.position,
DefaultConfig::skillsPosition)` after Cooldowns (it comes and goes in `recent` mode). Panel and watcher require
`ServerGate.survival(cfg)`; off Survival the store is untouched.

### 3.8 Failure handling
Each hook in `SkillsWatcher` catches `RuntimeException | LinkageError`, logs once, counts failures, switches off at
10 (the `McmmoWatcher` pattern). `bossBars()` returning null (accessor mixins unavailable) simply disables the bar
feed; level-ups and the menu still work.

## 4. Open questions that need an in-game capture

1. **Open `/mcmmo` once with capture on** (confirms the Skills Panel title/items are stable since 2026-10-01 and
   that `/mcmmo` opens it directly). Then click one skill ("Abilities Menu") and "Guides & Settings": does any
   sub-menu show XP (`xp / needed`) or power level? If so the panel could fill never-trained skills' meters.
2. Click the main menu's `SKILLS` book: same panel or a different one?
3. Gain a little XP in each unseen skill (shoot a bow, brew, repair at an iron block, throw a trident, hit with a
   mace/crossbow) to capture their exact bar names (`TRIDENT`/`TRIDENTS`, `CROSSBOWS`, `MACES`, `ARCHERY`,
   `ALCHEMY`, `REPAIR`) and confirm the XP curve there.
4. A capped skill (Taming 500/500): does a bar appear at all when a wolf kills something? What does it read?
5. Drink a McMMO Booster with capture on: the activation/expiry chat lines (for `BoosterParser`) and whether the
   bar's numbers reflect the boost.
6. One level-up with capture on while standing still afterwards: how soon the first post-level bar arrives (decides
   how long the "?" state lasts).
7. Does the sidebar `Skills: Lvl` exclude Smelting on every account (one data point: 1988 == 1988)?
8. Liz's account: run the same `/mcmmo` once there so the per-account store and any menu differences are seen.

## 5. Implementation plan (ordered, subagent-sized tasks)

All paths relative to `src/client-mods/cubewheel/`. Build/tests: `./gradlew build` (JDK 25, see `gradle.properties`).
Each pure task is TDD-able from the captured strings quoted in section 1.

1. **Skill enum and XP curve** — new `src/main/java/net/mage/cubewheel/skills/Skill.java`, `XpCurve.java`;
   tests `src/test/java/net/mage/cubewheel/skills/SkillTest.java` (byName variants, caps, child flag, icon ids),
   `XpCurveTest.java` (1500+250·level against the quoted bars).
2. **Boss-bar parser** — `skills/SkillBarParser.java` + `SkillBarParserTest.java` (small caps, `ʟᴠʟ.242` no-space
   form, `§` codes, rejects Mana Golem / Anomaly / Mana Pond bars).
3. **Chat/action-bar parser** — `skills/SkillTextParser.java` + test (level-up line with codes, own milestone and
   power-level broadcasts by name, player chat rejected, ability-unlock line ignored).
4. **Menu parser** — `skills/SkillsMenu.java` + test built from the 2026-10-01 item names/lore (17 entries, Smelting
   child, caps).
5. **Store** — `skills/SkillStore.java` + `SkillStoreTest.java` (bar deltas incl. roll-over, level-up sets unknown xp,
   menu never lowers, per-account keys, JSON round trip via a temp file, staleness).
6. **Panel model** — `skills/SkillsPanelModel.java` + test (recent vs all, ordering, MAX, `?`, stale colours,
   gain flash, max rows, child omitted).
7. **Config** — `config/CubeWheelConfig.java` (`Skills`), `config/DefaultConfig.java` (`skillsPosition`),
   `config/ConfigNormalizer.java`; extend `src/test/.../config/ConfigStoreTest.java` (defaults, clamping, position).
8. **Adapters and wiring** — `skills/SkillsWatcher.java`, `skills/SkillsPanel.java`; edits: `CubeWheelClient.java`
   (init, ALLOW_GAME listener, END_CLIENT_TICK via watcher, `PanelsHud.add`, flush on disconnect/stop),
   `tracker/local/mc/LocalSignals.java` (two `SkillsWatcher.onActionBarEvent` calls beside `McmmoWatcher`'s),
   `tracker/ContainerHook.java` (`SkillsWatcher.onMenu` beside `LiveWatcher.onMenu`).
9. **Keybind** — `Keybinds.java`, `assets/cubewheel/lang/en_us.json`, handler in `CubeWheelClient.onEndTick`,
   title hint in `SkillsPanel`.
10. **Settings** — `settings/SettingsSpec.java` (group + position); update `settings/SettingsSpecTest.java`
    (group order list, ids, "Exact positions" ids, choice count 9 → 11).
11. **Docs and release** — `README.md` (new "### mcMMO skills panel" under HUD panels; keybind table; Known unknowns
    from section 4), `TESTING.md` checklist (bar appears while mining, level-up, `/mcmmo` refresh, stale greying,
    arrange/snap), `gradle.properties` `mod_version` → `0.1.0-beta.78`, commit message in the repo's style
    (`feat(cubewheel): … (beta.78)`).
12. **Optional** — wheel leaf `Skills → /mcmmo` with `CONFIG_VERSION` 13 migration in `ConfigStore.migrate` (+ test);
    pace/ETA line reusing `tracker/Pace.java`.

Rule check: every command the design sends is a direct keypress (`/mcmmo` on the new key or a wheel click); the
boss-bar, chat, sidebar and menu reads are passive and already happen for other panels.
