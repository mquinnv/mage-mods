# CubeWheel (unofficial ManaCube helper)

A client-side Fabric mod that puts the commands you use most on ManaCube Survival behind a radial
wheel: hold a key, flick towards a slice, release. It also keeps a list of your `/home`s and can
show job / quest progress on a small HUD.

**Unofficial.** CubeWheel is not made by, affiliated with or endorsed by ManaCube.

## Requirements and install

- Minecraft **26.2** with Fabric Loader 0.19.3 or newer.
- Java **25** or newer at runtime (the launcher's bundled Java for 26.2 is fine).
- [Fabric API](https://modrinth.com/mod/fabric-api) (the only dependency).
- Drop `cubewheel-0.1.0.jar` and the Fabric API jar into your `mods` folder.

The mod is client-only. On servers whose address does not end in `manacube.com` or `manacube.net`
the wheel, commands, menu scans and capture do nothing; only the tracker HUD toggle key and the
tracker picker still work, on data recorded earlier (see [Server gate](#server-gate)).

## Building

Building needs JDK 25 or newer (Minecraft 26.2 class files are version 69). `gradle.properties`
points `org.gradle.java.home` at Homebrew's `openjdk`; change it if your JDK lives elsewhere.

```
./gradlew build      # compiles, runs the unit tests, writes build/libs/cubewheel-0.1.0.jar
./gradlew runClient  # dev client, for the manual checks in TESTING.md
```

## Keybinds

Options > Controls > Key Binds > **CubeWheel**.

| Action | Default |
|---|---|
| Open command wheel (hold) | `G` |
| Reload CubeWheel config | unbound |
| Toggle tracker HUD | unbound |
| Open tracker picker | unbound |
| Toggle capture mode | unbound |
| Refresh trackers | unbound |
| Toggle event HUD | unbound |
| Open SVA catalog | unbound |
| Toggle jobs panel | unbound |

Any key or mouse button can be used for the wheel. Each press does its action once: holding a key
(so the system repeats it) does not toggle capture or the HUD back and forth.

## Using the wheel

- Hold the wheel key. Move the mouse towards a slice to highlight it.
- Release the key or left-click to activate: a command slice sends its command and closes; a ring
  slice opens that ring (the wheel stays open, even if you already released the key).
- Right-click or click the centre to go back one level (at the root, closes). Esc closes.
- Releasing in the centre dead zone closes without sending anything.
- Clicking any mouse button ends "hold" mode, so releasing the key afterwards does nothing.
- A ring with more entries than `listThreshold` (default 8) opens as a scrolling list instead: type
  to filter (label or command), Enter runs the first visible row, click a row to run it, Esc or
  right-click returns to the wheel. If the wheel key is still held when the list opens, it is ignored
  there until you let go (it never types into the filter, and releasing it does nothing).

### Default layout

Top level, in fan order (slice 0 at the top, then clockwise), each with the entries that fan out in its arc
while it is hovered: Sushi `/warp crops` (Spawners `/warp spawners`), Homes (live, the cached homes), Teleporter
`/teleporter` (Back), Jobs `/jobs`, Shop `/shop` (Kilton, Auction house), Sell `/sell` (hand, all), Vaults (live:
PV 1..N, Party vault, All vaults), Heal `/heal` (Fly `/fly`, shown as `Fly: on`/`off`; the heal cooldown shows in
the Cooldowns panel, see [Command cooldowns](#command-cooldowns)), Isles `/isles` (the six Mana worlds), Party
quests `/pquests` (Prestige, Challenges), Daily reward `/cow` (Crates; live badge, see
[Daily reward badge](#daily-reward-badge)), Boss event (live, see [Boss event slice](#boss-event-slice)), TPA
(live, see [Live slices](#live-slices)) and More. More holds Shops (Alchemist, Enchanter, Shop, Auction house,
Forge, Fish shop), Warps (Server warps: Pond, Crates, Enchanter, Kilton, Leaderboard, PvP, 1v1; Bosses: Boss
arena, `/bosses`), Party (menu, home, warps, claim, map, vault), Ender chest and Settings.
Every ring below the top level has at most 8 entries, so none of them turns into a list. All of it is
editable, see below.

**Upgrading from an older version** (`configVersion` below 3): on the first load your `wheel` is replaced
with this layout once. Any command entry you had added yourself (a command the new layout does not have
anywhere) is kept under **More › Custom** (split into "Custom 1", "Custom 2", ... beyond 8); rings you made
are flattened to their entries. The log, and the reload key's chat lines, list what was moved. Everything
outside `wheel` is kept as it was.

### Live slices

Some slices change with the game: Fly shows `Fly: on` (green) / `Fly: off` (red) from whether the server
lets you fly; Daily reward and Boss event are below. Each is a small provider (`wheel/SliceViews`) that
may change a slice's label, colour and command; the wheel asks them every frame.

### Daily reward badge

The Daily reward (`/cow`) slice reads **`Daily reward: ready`** (green) when any reward tier (daily, weekly,
monthly) is available, or unknown, and otherwise **`Daily: 13h`**: the time until the soonest tier. Clicking
it always just sends `/cow`.

- Chat: ManaCube broadcasts `[/CASHCOW] <player> claimed daily|weekly|monthly <key>`. When `<player>` is you
  (your session username, any case), the claim time of that tier is saved per account in
  `config/cubewheel-cow.json`. `claimed their <key>` broadcasts (e.g. a Promo Key) name no tier: they are
  recorded but do not change the badge.
- A tier is then expected back `dailyReward.dailyHours` (24) / `weeklyDays` (7) / `monthlyDays` (30) after
  the claim. **ManaCube's real reset rule is unknown** (rolling 24 h, or a fixed daily reset); these are
  guesses and configurable.
- The /cow menu: when a menu opens within 10 s of sending `/cow` (or its title matches
  `dailyReward.menuTitlePattern`), each item naming a tier is read for `Available in 13h 2m`, `Claim in …`,
  `Claimed! Come back in …`, `Cooldown: …` (d/h/m/s) or `Click to claim`. That exact time replaces the
  estimate until your next claim. The menu wording is unconfirmed: capture the /cow menu (capture mode) so
  the phrases can be checked.
- Read only in ManaCube Survival; nothing is sent or clicked.

### Boss event slice

When chat announces a boss (`A BOSS SPAWNED` / `A MINI BOSS SPAWNED`, then `Boss <name>` and
`Location: <place>`), the Boss event slice shows **`<Boss> · 2m`** (gold, minutes since the announcement)
for `events.bossMinutes` (15) minutes. Clicking it sends the warp for its location from
`events.bossWarps` (first regex found in the location wins; one command per click, never automatically):

| Location regex | Command |
|---|---|
| `(?i)boss arena` | `/warp boss` |
| `(?i)wolfhaven` | `/warp wolfhaven` |
| `(?i)tangleroot` | `/warp tangleroots` |
| `(?i)sandara` | `/warp sandara` |
| `(?i)icehaven` | `/warp icehaven` |
| `(?i)morend` | `/warp morend` |
| `(?i)burning ?lands` | `/warp burninglands` |

A location with no match shows `<Boss> · 2m (no warp)` and does nothing. With no recent spawn the slice
reads `No boss event` (grey) and does nothing. Only the latest spawn is kept, in memory. Chat is read only
in ManaCube Survival.

### Homes

The Homes slice is filled from `/homes`:

- When the cached list is older than 5 minutes (or there is none yet), opening the ring sends one
  `/homes`, at most once per 30 seconds. The reply is parsed and hidden from chat, then the ring
  fills in.
- "Loading…" appears only when the cached list is empty. A stale but non-empty list shows the old
  homes straight away while `/homes` refreshes them in the background.
- If nothing parseable arrives within about 3 seconds, "Loading…" becomes "↻ Refresh". Clicking it
  re-sends `/homes` (at most once per 30 seconds) and refreshes the ring in place.
- A reply that says you have no homes ("Homes: none") is understood: the ring shows only Refresh
  plus any extras you configured.
- `/homes` is sent **only** when you open the Homes ring or click Refresh. Going back, the
  "Loading…" timeout, and any chat message that looks like a homes list never send it.
- `/homes` you type yourself is shown normally and still updates the cache.
- `/sethome X` and `/delhome X` update the list immediately.
- The list is cached per server in `config/cubewheel-homes.json`.

The parser only trusts a reply line that starts with a `Homes:`, `Your homes (N):` or `Home:` header,
or a message with at least two `/home <name>` click events. Only `/home <name>` clicks count as
homes; pagination clicks such as `/homes 2` are ignored (only the first page is read). **If ManaCube's format differs, homes
will not load until the parser is tuned**: use capture mode (below) and send the file.

## Configuration

`config/cubewheel.json` is written with defaults on first run. Edit it, then press the "Reload
CubeWheel config" key. A green chat line confirms; on a JSON error a red line shows the location and
the previous config keeps working. Numbers outside their range are clamped.

| Key | Meaning | Default |
|---|---|---|
| `configVersion` | File format version, managed by the mod. Loading an older file applies one-time upgrades and writes the file back (3: new wheel layout, see [Default layout](#default-layout); 4: the new Jobs panel takes the offset of your lowest top-left panel, see [Jobs panel](#jobs-panel); 5: the tracker becomes a panel with the Jobs panel's corner and offset, see [Tracker panel](#tracker-panel)) | `5` |
| `enabled` | Master switch | `true` |
| `serverHosts` | Host suffixes the mod is active on | `["manacube.com", "manacube.net"]` |
| `vaultCount` | Number of `/pv` entries in a `vaults` node (your rank decides this), 0–54 | `3` |
| `listThreshold` | Rings with more entries open as a list, 3–16 | `8` |
| `tracker.nearThreshold` | Fraction from which HUD entries are highlighted yellow, 0–1 | `0.8` |
| `tracker.hudMaxLines` | Max HUD lines, 1–20 (an older file with the old default 6 or 8 is upgraded to 10 once, see `configVersion`) | `10` |
| `tracker.hudVisible` | Tracker panel on/off (saved when you toggle it, see below) | `true` |
| `tracker.position` | Tracker panel corner and offset; in the Jobs panel's corner it stacks under it (see [Tracker panel](#tracker-panel)) | `{"corner": "top_left", "x": 4, "y": 4}` |
| `tracker.sources` | Source id -> regex matched against the menu title | see below |
| `tracker.refreshCommands` | Commands the "Refresh trackers" key sends, one at a time (max 8) | `["/pquests", "/prestige", "/jobs"]` |
| `tracker.survivalSidebarPattern` | Regex on the sidebar title; menu reading, refresh runs and live estimates only run while it matches (see [Server gate](#server-gate)). `""` switches the check off | `"(?i)survival"` |
| `tracker.worldFilter` | HUD order for the world you are in: `"sort"` lists entries naming the current world first (after pinned) and other worlds' last, `"hide"` drops other worlds' unpinned entries, `"off"` (see [World filter](#world-filter)) | `"sort"` |
| `tracker.sidebarLinks` | Sidebar key -> regex on tracked names that follow that live value | `{"Skills": "(?i)reach [\\d,]+ skill level"}` |
| `tracker.local.enabled` | Live `~` estimates between menu reads (see [Live estimates](#live-estimates-local-counting)) | `true` |
| `tracker.local.blocks` / `kills` / `fish` / `shear` | Count own block breaks / kills / catches / shears | `true` each |
| `tracker.local.areaBreaks` | Also count blocks the server breaks for you right after your own break (Tree Feller, harvester/hammer tools); needs `blocks` | `true` |
| `tracker.local.worlds` | World names recognised in objectives ("Wolfhaven Resources") | the six Mana worlds |
| `tracker.jobsPanel.enabled` | Left-hand Jobs panel on/off (saved by the "Toggle jobs panel" key); while on, job entries leave the tracker HUD (see [Jobs panel](#jobs-panel)) | `true` |
| `tracker.jobsPanel.position` | Panel corner and offset | `{"corner": "top_left", "x": 4, "y": 4}` |
| `tracker.local.specialWorlds` | Worlds that count as "special worlds (/worlds)" | the six Mana worlds |
| `toast.enabled` | Popup under the crosshair when a live counter goes up (see [Progress popup](#progress-popup)) | `true` |
| `toast.seconds` | How long the popup stays after the last increment before it fades, 0.5–5 | `1.5` |
| `events.enabled` | Event panel and alerts (see [Event timer](#event-timer)) | `true` |
| `events.hudVisible` | Event panel on/off (saved by the "Toggle event HUD" key) | `true` |
| `events.show` | Upcoming events listed, 1–10 | `3` |
| `events.alertMinutes` | Chat alert this many minutes before a start, 0–60 (0 = off) | `5` |
| `events.timezone` | Time zone of the schedule's times (entries may set their own `timezone`) | `"America/New_York"` |
| `events.schedule` | List of `{"name", "when"}` (optional `timezone`, `enabled`, `pinned`) | from ManaCube's wiki, see below |
| `events.schedule[].pinned` | Always list this event's next start, after the `events.show` soonest if it is not among them (see [Event timer](#event-timer)) | `false` (`true` for Mana Pond) |
| `events.position` | Panel corner and offset | `{"corner": "top_left", "x": 4, "y": 4}` |
| `events.bossWarps` | Boss location regex -> command for the Boss event slice; invalid entries are dropped with a warning | see [Boss event slice](#boss-event-slice) |
| `events.bossMinutes` | How long the Boss event slice shows a spawn, 1–180 | `15` |
| `dailyReward.enabled` | Live badge on the `/cow` slice (see [Daily reward badge](#daily-reward-badge)) | `true` |
| `dailyReward.dailyHours` / `weeklyDays` / `monthlyDays` | Assumed time from a claim until that tier is back (1–168 h / 1–60 d / 1–60 d) | `24` / `7` / `30` |
| `dailyReward.menuTitlePattern` | Regex on menu titles read as the /cow menu (menus opened within 10 s of `/cow` always are) | `"(?i)cash ?cow\|daily reward"` |
| `boosters.enabled` | Booster countdowns from chat (see [Boosters](#boosters)) | `true` |
| `boosters.position` | Panel corner (`top_left`, `top_right`, `bottom_left`, `bottom_right`) and `x`/`y` offset in GUI pixels | `{"corner": "top_left", "x": 4, "y": 4}` |
| `cooldowns.enabled` | Item ability countdowns (see [Item cooldowns](#item-cooldowns)) | `true` |
| `cooldowns.showUses` | Also show the held item's `Uses: N` | `true` |
| `cooldowns.mcmmo` | mcMMO ability countdowns (see [mcMMO ability cooldowns](#mcmmo-ability-cooldowns)); needs `cooldowns.enabled` | `true` |
| `cooldowns.commands` | Server command -> placeholder cooldown length (`"5m"`, `"2m 30s"`), counted down after the server confirms the command and corrected from its refusals (see [Command cooldowns](#command-cooldowns)); an entry without a command or a parseable length is dropped with a warning; needs `cooldowns.enabled` | `{"/heal": "5m"}` |
| `cooldowns.position` | Panel corner and offset | `{"corner": "top_left", "x": 4, "y": 4}` |
| `wheel` | The root ring: a list of nodes | see `DefaultConfig.java` |

`tracker.sources` defaults: `jobs` = `(?i)jobs`, `pquests` = `(?i)quest`, `prestige` = `(?i)prestige`,
`challenges` = `(?i)challenge`. Icons are item ids such as `minecraft:ender_chest`.

The toggle-HUD key saves `tracker.hudVisible` by rewriting `cubewheel.json` from the loaded config
(so ignored nodes, see below, are dropped from the file and clamped numbers are written back). If
the last load or reload failed (for example a JSON typo), it does **not** save: the HUD is toggled
for this session only and the action bar says "CubeWheel: HUD toggled for this session (config has
errors, not saved)", so your file is never overwritten.

### Node kinds

A **leaf** sends a command (with or without the leading slash):

```json
{ "label": "Spawn", "icon": "minecraft:red_bed", "command": "/spawn" }
```

A **ring** holds children (rings can nest):

```json
{
  "label": "Warps", "icon": "minecraft:oak_sign",
  "children": [
    { "label": "Crops", "icon": "minecraft:wheat", "command": "/warp crops" }
  ]
}
```

A **dynamic** node fills itself from a source (`homes` or `vaults`); `children` are extra fixed
entries appended after the generated ones:

```json
{
  "label": "Vaults", "icon": "minecraft:ender_chest", "dynamic": "vaults",
  "children": [
    { "label": "Ender chest", "icon": "minecraft:ender_chest", "command": "/ec" }
  ]
}
```

`vaults` generates `/pv 1` .. `/pv <vaultCount>`; `homes` generates `/home <name>` per cached home.

A **live slice** is one entry whose label and command come from the game; the only source is `boss`
(see [Boss event slice](#boss-event-slice)). It has no `command` and no `children`:

```json
{ "label": "Boss event", "icon": "minecraft:wither_skeleton_skull", "dynamic": "boss" }
```

A node must be exactly one kind. Nodes that are not are **silently ignored** (with their children):
a blank or missing label; both `command` and `children` on a non-dynamic node; neither; an unknown
`dynamic` source; a `dynamic` node that also has a `command`. A live slice's `children` are dropped.

## Rules note

ManaCube's rules say: *"Rebinding a command or message to a key isn't a macro as long as you
manually press it. Automating presses is."* CubeWheel is built around that:

- Every command is the direct result of a keypress or click of yours.
- No timers, no background polling, no automatic re-sends.
- The one narrow case is `/homes`, sent only when you open the Homes ring (and the cached list is
  older than 5 minutes) or click Refresh, which is still a direct result of your action, at most
  once per 30 seconds. Nothing else ever sends it: not going back, not a timeout, not an incoming
  chat message.
- The tracker never clicks anything in a menu. The only time it opens or closes menus is the
  "Refresh trackers" key (or the picker's Refresh button): one press sends the configured commands one
  after another, waits for each menu, reads it and closes it the way Esc does, at most once per
  60 seconds. Nothing starts a refresh except that press; there is no timer.
- Live estimates only observe what already happens on your screen (your own breaks, damage and
  death updates the server sends anyway, your own reel-ins). They send nothing and never touch a menu.

- The SVA catalog and tooltip read ManaCube's public web API (`api.manacube.com`) and, for "Compare
  with player", Mojang's name look-up: nothing goes to the game server. The one command is the
  catalog's left-click, a single `/ah search <name>` per click.

Please check the current server rules yourself; you are responsible for how you use any client mod.

## Server gate

The mod only acts when the connected server's host equals or is a subdomain of an entry in
`serverHosts` (case-insensitive, port ignored). Elsewhere, and in singleplayer, pressing the wheel
key shows the action-bar hint "CubeWheel is only active on ManaCube", no command is ever sent, the
HUD is not drawn, menus are not recorded and capture records nothing. The toggle-HUD key and the
tracker picker still work there, on locally stored data.

ManaCube runs SkyBlock, Parkour, the hub and more on the same address, and their menus and blocks must
not feed Survival's trackers. So menu reading, refresh runs and live estimates also need the sidebar
title to match `tracker.survivalSidebarPattern` (default `(?i)survival`: Survival's sidebar is titled
"SURVIVAL"). No sidebar means inactive. The wheel, capture and sidebar-linked values only need the
host gate. Refreshing elsewhere says "Tracker refresh only works in ManaCube Survival".

## Progress tracker

The tracker reads what you open. When you open a menu whose title matches a `tracker.sources` regex (defaults:
`/jobs`, `/pquests`, `/prestige`, `/challenges`), the mod reads item names and lore in the top
container (never your inventory) and records the entries that show progress; in a quest menu only items
with a `Progress:` line (or marked completed) are recorded, so a stray percentage in some other item's lore
is not tracked. Consequences:

- **Data is only as fresh as the last time you opened that menu** (or pressed the refresh key, or
  a linked sidebar value changed, see below). The HUD shows an age suffix ("now", "5m", ...).
- **HUD** (the [Tracker panel](#tracker-panel), top left under the Jobs panel): pinned entries first, then every other incomplete entry,
  closest to done first, at most `hudMaxLines` lines. Entries at or above `nearThreshold` are yellow,
  estimated ones keep their `~`. Complete entries only show when pinned (green). Hidden entries never
  show. F1 hides it. In a world, entries for that world move up (see below). While the
  [Jobs panel](#jobs-panel) is on, job entries (pinned or not) are shown there instead.
- **Percentage quests** whose objective has one known total are shown in objective units, on the HUD
  and in the picker: `Haven Harvester  6,700 / 10,000 (67%)` for "Progress: 67%" of "Harvest or Mine
  10,000 Wolfhaven Resources". Quests with several objectives keep `67 / 100 (67%)`.
- **Picker** (bind "Open tracker picker"): all known entries grouped by source, sorted by
  percentage. Left-click to pin/unpin, right-click to hide/unhide an entry on the HUD (hidden entries
  stay listed, dimmed and marked "(hidden)"). A button forgets unpinned entries not seen for 7 days.
- Stored in `config/cubewheel-tracker.json`.

### World filter

Many entries can only be worked on in one world: "Slay 16/64 Tigers in Tangleroots", "Harvest or Mine
10,000 Wolfhaven Resources", "Slay 2,500 Tangleroot Monsters", or a prestige objective that must be
done in the special worlds. An entry is tied to a world when its name or objective names one of
`tracker.local.worlds` (singular or plural, with or without spaces: "Tangleroot" = "Tangleroots",
"Burning Lands" = "burninglands"), or to the special worlds when its lore says so. The current world
comes from the dimension name (and a sidebar `World:` line, if any), as for live estimates.

With `tracker.worldFilter` = `"sort"` (default), unpinned entries for the current world come first,
entries tied to no world next, and entries for other worlds last; each group closest to done first.
`"hide"` also drops unpinned entries for other worlds from the HUD. Pinned entries always show first,
in their usual order, and the picker is unchanged. If the world cannot be told (or with `"off"`) the
HUD keeps its usual order.

### Live sidebar values

ManaCube's sidebar shows lines such as `Money: $2.89M`, `Souls: 18,975`, `Skills: Lvl 1851`. CubeWheel
reads the sidebar exactly as it is drawn (twice a second, only acting when it changed) and turns every
`Key: value` line into a number (`$`, `,`, `Lvl` and `k`/`M`/`B` suffixes understood; icon glyphs
ignored).

`tracker.sidebarLinks` connects a sidebar key to tracked entries: whenever that value changes, every
incomplete entry whose name matches the regex gets it as its current value (it is never lowered) and
counts as seen "now". The default links `Skills` to entries containing "Reach N Skill Level", so the
prestige objective "Rank [✪4] · Reach 2,500 Skill Level" moves live with your skill level once you have
opened the prestige rank menu once (a config still holding the older, broader `(?i)skill level` is
upgraded when loaded). Add more links (e.g. `"Mana": "(?i)mana"`) as needed; `{}` turns linking off.
Linked changes are saved at most every 10 seconds, and on disconnect/quit. A clock such as
`Time: 12:30` is not read as a number.

### Refresh key

Bind "Refresh trackers" (or click **Refresh** in the picker). One press = one run:

1. Sends the first `tracker.refreshCommands` entry.
2. Waits up to 3 s for the server's menu; once its items are shown it is read (as if you had opened it)
   and closed with the normal close packet. A menu that does not open or stays empty is skipped.
3. Only then sends the next command, and so on.
4. Chat shows a grey `[CubeWheel] Refreshed N trackers` (N = the entries its menus showed, whether
   they changed or were just confirmed). From the picker's Refresh button, the result (or why it was
   skipped) is also shown in the picker for a few seconds.

While a run is active the menus it opens are not drawn and ignore clicks and keys (except Esc), so the
screen does not flash and nothing can be clicked by accident. Only menus CubeWheel recognises as
tracker menus are hidden. The menu that opens right after one of the run's commands is that command's
answer: it is waited for while it fills in, and closed after 3 s (counted as "did not load") if it is
never recognised. Any other menu (a chest, the warps menu) stops the run and stays visible and usable.
Opening any other screen (chat, inventory, pause menu), pressing Esc, or pressing use or attack while
the run waits for a menu (you may be opening a chest or an NPC) stops the run; the picker a run was
started from does not. A tracker menu that arrives late, after its 3 s wait, is still read and closed. A second press within
60 s of the last start is refused: `[CubeWheel] refresh skipped: wait Ns`.

**Which commands?** `/prestige` opens the list of prestige levels ("Prestige N - Not Completed"); the
rank objectives with the real progress ("Rank [✪n]" items) are in a menu reached by clicking, whose
command is not known yet. CubeWheel never clicks, so it cannot reach that menu. Use capture mode: each
captured menu records the command sent just before it opened (`afterCommand`). Once the command that
opens the rank-objectives menu is known, put it in `tracker.refreshCommands` (and reload the config).

### Job listings

`/jobs` -> an industry shows three listings ("Beginner/Experienced/Heavy Objective"). Each is tracked
as `<Industry> <Tier> · <objective>`, e.g. `Farming Heavy · Harvest Cherry Logs  3,127 / 4,773`: the
progress is the objective's own counter, not the hand-in line. When an industry's listings page is
read again, that industry's listings that are no longer offered (rerolled or completed) are forgotten,
unless pinned. Entries from the main jobs menu (e.g. `GOLDEN CRATE`) are kept. Listings get live
estimates like other objectives: breaking cherry logs advances `Farming Heavy · Harvest Cherry Logs`
(the objective "Harvest 3,127/4,773 Cherry Logs" is read as "Harvest 4,773 Cherry Logs"). Catches of
non-fish such as "Catch 0/61 Tangleroots Fireflies" count like custom-model kills (see below).

### Live estimates (local counting)

Between menu reads CubeWheel estimates progress from what it sees you do, so the HUD moves while you
play. It only watches; it never sends a command, opens a menu or clicks anything. It is active only in
ManaCube Survival (see [Server gate](#server-gate)) and never counts what you do in creative or
spectator mode.

- An estimated entry is shown with a `~`: `Haven Harvester  ~6,437 / 10,000 (64%) · 5m`. The age is still
  that of the last real read. Percentage quests are shown in objective units (64% of 10,000 plus what
  was counted since), estimated or not.
- An estimate never turns an entry green. When it reaches the target it shows `✓?` in yellow until a
  menu read confirms it.
- Every real reading replaces the estimate ("snaps back"): opening the menu, a refresh run, or a
  linked sidebar value. The picker shows `+N~` (units counted since the last read); hover the row for
  when that was and how the previous estimate compared ("Last check: counted 212, actual 220").
- Estimates are saved with the tracker (at most every 30 s, and on disconnect/quit), so they survive a
  restart until the next read.
- `tracker.local.enabled: false` stops counting and hides stored estimates (they are not deleted).

What is counted, per objective text read from the menu (only objectives with a single line):

| Objective | Counted when you... |
|---|---|
| Harvest N Crops / Harvest N Wheat (carrots, potatoes, ...) | break a **fully grown** crop (melons and pumpkins count; stems do not) |
| Mine / Break / Chop / Dig N Stone, Cobblestone, Logs, Ores, Resources | break a matching block yourself; "Resources"/"Blocks" = any block except instant-break plants (grass, flowers, ferns); ripe crops still count |
| Harvest N Sweet Berries / Cocoa Beans | pick a ripe sweet berry bush / cocoa pod |
| Kill / Slay N Mobs / Monsters / Zombies / Mana Wolves, "Slay 16/64 Tigers in Tangleroots" | kill it: you were the last player to damage it within 5 s (arrows and tridents count). Names match the mob type or its name tag, ignoring stack counts, health (`Dart Frog 20⺛`, `❤ 20`), levels and small caps. A **stacked** mob you hit whose count drops (`5x Tiger` -> `4x Tiger`) counts the drop (only when both names show a count; at most 2 per change unless you hit it again). A **custom-model mob** (Tangleroot tigers: an unnamed hitbox such as a `Slime` that vanishes without dying) counts when the server removes it within 1.5 s (30 ticks) of your hit and within 16 blocks, once; it is named by its own name tag, a name tag riding it or within 3 blocks, or else by a loot action bar seen from your first hit on it (at most 3 s before the removal) until 1.5 s after it (`+2  Tiger Hide` counts for the one kill objective whose target is a whole word of the item name, singular or plural, here "Tigers"; `+N Mana` is ignored) |
| Catch N Fireflies (any non-fish), "Catch 0/61 Tangleroots Fireflies" | like a custom-model kill: the entity (a firefly is a `minecraft:interaction` hitbox) is hit **or used on** (Firefly Bottle) and then removed; named by the loot line (`+1  Sad Firefly` -> "Fireflies"), or, with a Firefly Bottle in hand and no loot line, by "Firefly" when exactly one such objective fits this world. Counted under `tracker.local.kills` |
| Catch N Fish (prestige "Rank [✪8] · Catch 1,000 Fish", party quest "Fisherman") | catch any fish: ManaCube's chat line `You caught a 52.2cm Common Flounder` (its custom fishing never makes the vanilla bobber bite). A vanilla reel-in on a biting bobber also counts; a reel-in and a chat line within 2 s are one catch (the chat line wins) |
| Catch N &lt;species&gt; (while fishing), job listing "Catch 0/9  YellowSeaShroom while fishing" | catch that species: the chat line's last word(s), compared without case, spaces or plural ("YellowSeaShroom" = "Yellow Sea Shrooms"); the rarity word (Common/Uncommon/Rare/...) is ignored. Item icon glyphs in the listing are dropped. "while fishing" makes any "Catch" a fishing objective ("Catch 5 Crabs while fishing") |
| Shear N Sheep (any shearable mob by name: "Shear 20 Mooshrooms"), job listing "Shear 10/84 Sheep" | use shears (vanilla shears, or any item whose name or lore says "Shears") on a grown, unsheared sheep **and** see the server sync it as sheared within 2 s (40 ticks); nothing counts at the click. With an area shears tool, other sheep within 5 blocks that were unsheared at the click and turn sheared within 1 s (20 ticks) count too. Sheep sheared by anyone else never count (only sheep in the snapshot taken at your click). Counted under `tracker.local.shear` |
| ... "Wolfhaven Resources", "Sandara Monsters", "in <world>" | only while you are in that world (names match with or without spaces/underscores: "Burning Lands" = `burning_lands` = `burninglands`) |
| Job listings: "Harvest 3,127/4,773 Cherry Logs" | as "Harvest 4,773 Cherry Logs" |
| ... with "special worlds (/worlds)" in the lore | only in one of `tracker.local.specialWorlds` |

Prestige ranks read before objectives were stored (kept fresh since only by the sidebar) take their
objective from their name ("Rank [✪8] · Catch 1,000 Fish") until /prestige is opened again; that
fallback does not know a rank's "special worlds" note, which the next read restores.

The world comes from the dimension name and the sidebar's `World:` line, if the server shows one. If
it cannot be told, world-scoped objectives are not counted.

Not counted (the next read fixes the number): skill levels, "Complete N Jobs", boss kills and
participation, discovery, dungeons, party/island levels, hand-in jobs ("Harvest and hand in ..."), and quests with several objectives. Blocks you placed yourself do
not count when broken again, and a break the server undoes (claims, protection) is taken back.

**Area breaks** (`tracker.local.areaBreaks`). Blocks the server breaks for you (mcMMO Tree Feller,
ManaCube harvester hoes and hammers) never show up as your own break. Instead, breaking or starting to
break a block, or a mcMMO `TREE FELLER` / `SUPER BREAKER` / `GIGA DRILL BREAKER` / `GREEN TERRA ACTIVATED`
action bar, opens a short window: for 1 s (20 ticks, extended by further breaks of the same block), a
block within 4 blocks (a 9x9x9 cube) that the server turns into air counts as broken by you, as the block
it was before (ripe crops only, as above). At most 64 per window. After `TREE FELLER ACTIVATED` (until
it wears off, at most 30 s), breaking a log opens a Tree Feller window instead: 2 s (40 ticks), **logs
only**, 8 blocks sideways, 2 down and 32 up, at most 256. Never counted: a block turning into another
block (a replanted crop, stone to cobblestone), water or lava changes, a block turned into water, a block
you placed, the same position twice, and plants that pop off because the block under or over them was
just broken (the rest of a sugar cane or cactus column, a flower on a broken dirt block).

Party quests count the whole party's work; the estimate only counts yours, so it runs low until the
next read.

**Estimates are approximate and can be off in both directions.** Every real read corrects them. Known
causes:

- *Under-count:* attribution gaps (a mob that dies to fire, fall damage or a pet more than 5 s after
  your last hit; a kill whose damage packet the client never saw); a stacker whose name tag shows no count, or a whole stack dying at
  once (counts one); party members' work; a world
  that cannot be told for world-scoped objectives; custom-model mobs killed by an area hit (a katana's
  sweep) that sent no damage packet naming you; a custom-model kill with no name tag whose loot line is
  missing, late (over 1.5 s after the removal or over 3 s before it), shared by several kills (a line
  seen before a removal names one removal only), or names two objectives ("unattributed", capture
  `method d`); a stacked mob's last kill if it is removed rather than dying (its id is already settled).
- *Over-count:* a hit custom-model mob the server removes within 1.5 s for another reason (despawn,
  model reload, plugin cleanup) counts as a kill; a loot item that happens to contain an objective's
  target as a word (`Tiger Lily` for "Tigers") names the wrong mob, and a loot line from an earlier
  kill within 3 s (but after your first hit) can name a later removal; a name tag of a different mob
  within 3 blocks; right-clicking a non-living entity (interaction, display) that the server then
  removes within 1.5 s; a Firefly Bottle used on something that is removed without being caught.
- *Shearing:* under-count when the sheared flag syncs late (over 2 s; area sheep over 1 s), when an
  area tool reaches farther than 5 blocks or more than 32 sheep, when a custom shears item neither is
  vanilla shears nor says "Shears" in its name or lore, or for mobs that are replaced rather than
  flagged when sheared (a mooshroom turns into a cow: it is removed, so it never counts); over-count
  when another player shears a sheep in your snapshot within that window (right after your click), or
  when the server shears it but does not count it for the job (plugin rules).
- *Area breaks:* over-count when another player (or a plugin, piston, explosion, sapling growth, a
  farm) turns blocks into air within 4 blocks of a block you just broke or hit, within 1 s (Tree Feller:
  logs within the column box for 2 s), up to 64 (256) per window; when a job counts a harvester's replant
  differently; for a Tree Feller window after the ability silently ended (no "worn off" line seen: up to
  30 s). Under-count when an area tool reaches farther than 4 blocks, breaks blocks more than 1 s after
  your break (slow, staged tools), replants a crop instead of breaking it (block to block, ignored), breaks
  into water (waterlogged blocks), exceeds 64 blocks per window, or fells a tree wider than 8 blocks or
  taller than 32; when a job counts plants that popped off (sugar cane, cactus columns); when the
  mcMMO activation line is not shown in the action bar (then Tree Feller windows only reach 4 blocks);
  if the `ClientLevel` mixin does not apply (nothing is counted from area tools; logged once).
- *Over-count (other):* hits and breaks the server ignores for the objective (plugin rules we cannot see, e.g.
  spawner mobs, custom drops, anti-farm limits, a player-placed block the client did not see you
  place); a break the server undoes more than 5 s later; before this version, "Resources"/"Blocks"
  also counted grass and flowers; any block counts for "Resources" even where ManaCube may only count
  certain blocks.

Progress recognised in lore: `N / M`, `N/M`, `N of M`, `k`/`m` suffixes (`1.5k / 2k`) and percentages.
Glued words and three-part dates are rejected. Known limitation: a two-part date such as `12/25`
can be mistaken for progress. Chat and action-bar progress is not parsed; menus that show no numbers
are ignored.

## HUD panels

CubeWheel draws small panels: events, boosters, item cooldowns, jobs and the tracker, stacked in that order. Each has a
`position` in the config: a corner (`top_left`, `top_right`, `bottom_left`, `bottom_right`) and an `x`/`y`
offset from it in GUI pixels. Panels in the same corner stack instead of overlapping (in a top corner, the
first one is `y` pixels from the top and the next one goes below it); a panel in the top-right corner goes
below the potion icons. All default to the top left, which vanilla leaves empty. F1 hides
them. They are shown only in ManaCube Survival (host and sidebar gate, see [Server gate](#server-gate)).
All of them are passive: they read chat, the clock and your own clicks, and never send or click anything.

### Jobs panel

A **Jobs** panel (top left, below the other panels) lists every tracked job listing, never capped by
`hudMaxLines`, grouped by industry (Farming, Hunting, Fishing, Mining, Woodcutting, Excavation, Brewing,
Enchanting; others after, alphabetically) and within one by tier (Beginner, Experienced, Heavy):

```
Jobs · Golden Crate 4/5
⚒ Farming
  Beginner · Harvest Wheat  64 / 64 (100%) · 12m · hand in
  Heavy · Harvest Cherry Logs  ~3,240 / 4,773 (68%) · 12m
⚒ Hunting
  Beginner · Slay Tigers in Tangleroots  16 / 64 (25%) · 3m
```

- The `GOLDEN CRATE` entry from the main jobs menu is shown in the title.
- A listing read complete stays, green, with `· hand in` (it still has to be claimed); it leaves once the
  industry's page no longer offers it.
- Live estimates keep their `~` and `✓?` (yellow) as on the tracker HUD.
- With `tracker.worldFilter` on (`sort`/`hide`) and the world known, listings naming the current world are
  white, those naming no world light grey and those naming another world dark grey (`hide` does not drop
  them here).
- Every listing across all industries is shown, whatever you hold. In a mana world, listings naming
  another world drop out, as do listings for vanilla things that world does not have (bare
  Mine/Harvest/Slay/Catch jobs naming a specific ore, crop, log, mob or fish; finished ones stay: they
  still have to be handed in) and an industry left with nothing drops with them; leaving the world
  brings everything back.
- Entries hidden in the picker (right-click) stay hidden here too.
- While the panel is on, the Tracker panel shows no job entries, pinned or not; turn it off (`tracker.jobsPanel.enabled`
  or the "Toggle jobs panel" key) to get them back there.
- Loading a config older than version 4 gives the panel the offset of your lowest top-left panel, so with
  panels moved down (say `"y": 80`) it stacks below them instead of above.

### Tracker panel

The tracker (formerly a separate top-right overlay) is a **Tracker** panel registered after the Jobs panel, so in
the same corner (`tracker.position`, default top left) it stacks right under it, at the same width:

```
Tracker
— Pinned
⚑ Jungle Pursuit            0/15k
— Anywhere
✦ ✪4 Skill Level        1.9k/2.5k
⚑ King of the Jungle        5/100
 ↳ 0% Jungle Zombies
 ↳ 10% Golden Knights
```

- The left column is the source's marker: ⚒ jobs, ✦ prestige, ⚑ party quests, ★ challenges, • other.
- Short titles: a prestige rank "Rank [✪4] · Reach 2,500 Skill Level" reads "✪4 Skill Level" (verb and amount
  dropped, the count shows them); a party quest keeps its name ("Jungle Pursuit"); a job listing (with the Jobs
  panel off) shows its target ("Cherry Logs"). Titles are cut at 18 characters with "…".
- Counts as on the Jobs panel: "1.9k/2.5k", `~` for live estimates, `✓?` at an estimated target, `✓` read
  complete, "·3h" only for reads at least an hour old.
- A quest with several objectives gets one grey "↳ 10% Golden Knights" row per objective (they do not count
  against `hudMaxLines`).
- Grey "— Pinned", "— This world", "— Anywhere", "— Other worlds" headings appear only when more than one group
  is shown; entries in "Other worlds" are dark grey.
- Green: read complete; yellow: at or above `nearThreshold` or `✓?`; white otherwise.
- `tracker.hudVisible` and the "Toggle tracker HUD" key show or hide it; `hudMaxLines`, `worldFilter`, pins and
  hidden entries work as before. While the Jobs panel is on, job entries are left out.
- Loading a config older than version 5 gives the panel the Jobs panel's corner and offset.

### Progress popup

When [local counting](#live-estimates-local-counting) adds progress to a tracked entry (blocks, kills, fish, shears,
milk, a quest completed in chat), a short line appears just below the crosshair and fades out:

```
+1 Mana Wolves  ~10/74
```

- Named and counted as on the Jobs and Tracker panels (`~` for a live estimate); an objective of a multi-objective
  quest shows its "↳" row's name and count ("+1 Golden Knights  ~6/10").
- Further increments to the same entry while it is shown add up in it ("+7 Acacia Logs  ~493/630") and restart its
  time; an increment to another entry replaces it. A count taken back (a rejected break, a chat catch replacing
  the bobber's) is taken off it too.
- Cyan; green with a `✓` ("✓ Mana Wolves  ~74/74") when the increment reaches the target.
- Only local counts show it: menu reads and sidebar values never do. Hidden while a screen is open.
- It also shows one-off notices, each once when the state changes (not on joining a world):
  - "Inventory full" (red) when the last of the 36 main inventory slots fills; again after one frees up.
  - "Phoenix set bonus lost (3/4)" (yellow) when the worn set's bonus stops being met, "Phoenix set bonus active"
    (green) when it is met again. The pieces a bonus needs come from its lore: "(Requires 4/4 pieces)" gives its
    count; "(Full Set Required)" or a "FULL SET EFFECTS" heading without a count means all 4. These sets also get
    the Status panel's "⚠ no set bonus" warning, and on its set bonus line effects that only apply "in Resource
    World" (Warden's "Speed IV in Resource World") are dimmed outside it, and each effect is shortened so the
    line fits: phrases first ("Take -10% less Damage" reads "-10% Dmg", "Invisible to Monsters" reads
    "Invisible"), then single words ("Strength II" reads "Str II"; Absorb, Res, HP Boost, Fatigue, Stun Immune).
    A heading that names its pieces ("(Helmet + Boots)") gets neither.
  - "PV 2: 3 slots left" (yellow) when a vault drops to 3 free slots or fewer, "PV 2 full" (red) when it fills; each
    again after it has been back above. Vault fill is read when a vault is opened, so these show as it closes.
  - Changes made in a menu (armor swapped, a vault filled) show when it closes.
- `toast.enabled` and `toast.seconds` (default 1.5 s, then a 0.3 s fade), in the settings screen's HUD panels tab
  under "Progress popup".

### Event timer

An **Events** panel lists the next `events.show` (3) Survival events with a countdown, soonest first:
`Golden Knight · 14:02` (yellow within 5 minutes). Each event appears once, with its next start. A schedule
entry with `"pinned": true` (the Mana Pond by default) is always listed: among the soonest if it is one of them,
else after them. While the Mana Pond runs its boss bar ("Mana Pond (Spawn) 120/256") replaces the countdown with
a green `Mana Pond · NOW 120/256`; when the bar goes the countdown to its next start returns. When a start is
`events.alertMinutes` (5) minutes away, a gold chat line (only you see it) says `[CubeWheel] KOTH starts in 5
min (12:30)` in your computer's local time; once per start, also if you join inside the window. Bind "Toggle
event HUD" to hide or show the panel (saved like the tracker HUD toggle; alerts do not depend on it).

The default schedule is taken from ManaCube's wiki page *Survival > Survival Events* (times in "EST", which
ManaCube uses as New York wall-clock time: on 2026-09-30, during daylight saving, KOTH began at 12:30 EDT and
LPS was announced for 13:00 EDT):

| Event | `when` |
|---|---|
| LPS | `at 08:00, 13:00, 17:00` |
| KOTH | `at 00:30, 02:30, 04:30, 06:30, 10:30, 12:30, 14:30, 16:30, 18:30, 22:30` |
| Boss | `at 01:30, 03:30, 05:30, 07:30, 09:30, 13:30, 15:30, 17:30, 19:30, 21:30` |
| Golden Knight | `every 3h from 00:15` |
| Cursed Witch | `every 3h from 01:15` |
| Desert Golem | `every 3h from 02:15` |
| Mana Pond | `at 03:00, 06:00, 10:00, 15:00, 18:00, 22:00` (pinned; from the sign at the pond, 2026-10-07) |

Only what the page states is included. It says KOTH "runs every two hours" but lists no 8:30, and the boss list
has no 11:30; the listed times are used. The Mana Pond's times are not on the page (they come from the sign at
the pond; it runs about 5m20s). Morender Dragon and Shadow Sorcerer have no times (they are triggered by player
progress). **In-game `/events` is authoritative**: if it disagrees, edit `events.schedule` and reload. `when` is
either `at HH:MM, HH:MM, ...` (24-hour, or `8:00AM`/`1:00 PM`) or `every Nh from HH:MM` / `every Nh at :MM`
(from that time until midnight, every day). Example:

```json
"events": {
  "schedule": [
    { "name": "KOTH", "when": "every 2h from 00:30" },
    { "name": "My thing", "when": "at 20:00", "timezone": "Europe/London", "enabled": true },
    { "name": "Mana Pond", "when": "at 03:00, 06:00, 10:00, 15:00, 18:00, 22:00", "enabled": true, "pinned": true }
  ]
}
```

An entry with a bad `when` or time zone is ignored; the reload key shows why in a yellow chat line.
`"schedule": []` shows nothing; deleting the key restores the defaults. `events.enabled: false` turns the panel and
alerts off.

### Boosters

When chat says you received or extended a booster, a **Boosters** panel counts it down:
`2x Sell · 12:34` (yellow in the last minute), soonest-ending first. Recognised messages:

- `You have received a 2x Sell Boost for 30m`
- `Your 2x Sell Boost has been extended from 5m 10s to 35m 10s` (the new remaining time is used)
- `Your 2x Sell Boost has expired` / `has ended` (removes it)

Case, colours, "Booster", "a"/"an" and `1h 30m` / `30 minutes` style times are tolerated. The message must
start with the phrase, so player chat ("[Rank] Name: You have received ...", `[SHOUT]` lines) never counts.
Types seen so far: Sell, Mob Head, mcMMO, XP; any other word works too. Receiving a booster you already have
never shortens it. End times are stored as clock times in `config/cubewheel-boosters.json`, so the countdown
survives a restart. It assumes a booster keeps running while you are offline (unverified). `boosters.enabled: false`
turns parsing and the panel off.

**The wording is unverified on Survival** (it comes from another mod's source). If a booster does not show
up, turn capture mode on, activate one, and send the `"kind":"chat"` line.

### Item cooldowns

Custom items whose lore lists a cooldown get a countdown when you use them: a **Cooldowns** panel shows
`Samurai Katana · 4.5s` (yellow in the last 3 s), soonest first. The lore is read like ManaCube writes it:

```
ITEM EFFECTS: (Right-Click)        <- the section says how the ability is triggered
➟ Duplicate a Monster
Cooldown: 60s                      <- 60s, 0.5s, 4.5m, 1m 30s, 10 seconds; "None" = no cooldown
```

| Section heading | Starts when you... |
|---|---|
| `(Right-Click)`, `(Right Click)`, plain `Right-Click:` | right-click with the item (air, block or entity) |
| `(Shift + Right Click)`, `(Sneak + Right-Click)`, `(Right-Click + Sneak)` | right-click while sneaking (a plain right-click ability of the same item is used when not sneaking) |
| `(Left-Click)`, `(Attack)`, `(On Attack)`, `(Shift + Left Click)` | press attack with the item in your main hand (hit or swing) |
| `(Consume)`, `(When Consumed)`, `(When Eaten)` | finish eating or drinking it (letting go early does not count) |
| `(Sneak)` | start sneaking while holding it |
| a `Cooldown:` line before any heading | right-click |

Passive sections (`While Worn`, `When Held`, `When Attacked`, `Block Attack`, `Take Damage`, `Shoot`, kills, ...)
start nothing: CubeWheel cannot see when they trigger. Using an item again while its countdown runs does not
restart it (the server ignores that use too). An item with several abilities shows the section:
`Iridium Axe (Shift + Right Click) · 7.0s`. Countdowns are keyed by the item's name and are cleared when you
leave the server. With `cooldowns.showUses`, the item in your main hand adds a grey `Uses: 1,234` line when its
lore has `Uses: N`, `Uses Left: N` or `Remaining Uses: N`.

This only watches your own clicks: every click still goes to the game unchanged, nothing is sent or cancelled.
**Unverified:** the countdown starts on your click, not on the server's confirmation, so a use the server
rejects (not enough souls, wrong world, a cooldown the HUD did not know about) still starts it; rank perks that
shorten cooldowns are not known; and the heading words come from a list of ManaCube item lore, not from
captures of your own items.

### mcMMO ability cooldowns

The same **Cooldowns** panel counts down mcMMO super abilities (Super Breaker, Giga Drill Breaker, Tree Feller,
Serrated Strikes, Skull Splitter, Berserk, Green Terra, Blast Mining): `Super Breaker · 3:12` (yellow in the
last 10 s), then a green `Super Breaker · ready` for 5 s once it is refreshed. It reads the messages ManaCube
sends (action bar and chat; player chat is ignored):

| Message | Effect |
|---|---|
| `●● SUPER BREAKER ACTIVATED ●●` | starts the countdown with the learned cooldown (default 240 s, Blast Mining 60 s) |
| `MINING » Your Super Breaker ability is refreshed!` | clears it, shows "ready", and learns the real cooldown (refresh time minus activation time, whole seconds, kept only between 10 s and 1 h) |
| `mcMMO ➡ You are too tired to use that ability again. (12s)` | sets the remaining time of the ability of the tool you last readied |
| `MINING » You ready your pickaxe.` | remembers the tool: pickaxe = Super Breaker, shovel = Giga Drill Breaker, axe = Tree Feller (Skull Splitter if that was your last activation), hoe = Green Terra, sword = Serrated Strikes, fists = Berserk |

With no tool readied yet, "too tired" goes to the most recent activation. Learned cooldowns are stored in
`config/cubewheel-mcmmo.json` (`{"Super Breaker": 180}`); running countdowns are kept in memory only (they
survive a reconnect, not a game restart). Purely passive: nothing is sent or hidden.
`cooldowns.mcmmo: false` turns it off.

### Command cooldowns

The same **Cooldowns** panel counts down server commands that have a cooldown, `/heal` by default: `/ Heal · 4:30`
with a golden apple (yellow in the last 10 s), in the row style of item abilities. It only knows a command was
sent (typed, or from the wheel) and what the server answers within 3 s:

| Reply within 3 s | Effect |
|---|---|
| `You have been healed.` (the only line captured so far) | starts the countdown with the known length: the learned one, else the placeholder from `cooldowns.commands` (`5m` for `/heal`, a guess). Pressing again while it runs and being healed again means the real cooldown is shorter: the countdown restarts and the shorter length is learned |
| a refusal: a server line naming the command word and a duration, such as `… wait 2m 30s … /heal …` | sets the remaining time; if a success was seen before, learns length = time since that success + remaining |
| nothing | nothing starts (the command may have failed for another reason) |

**The refusal wording is unverified**: no `/heal` refusal has been captured yet, so the matcher is generic (the
command word plus any `2m 30s` / `45 seconds` parts, player chat excluded). If `/heal` on cooldown shows nothing, turn
capture mode on, press it again and send the `"kind":"chat"` line. Learned lengths are stored as whole seconds per
command in `config/cubewheel-cooldowns.json` (`{"/heal": 450}`), separate from the mcMMO file; running countdowns are
kept in memory only. Purely passive: nothing is sent or hidden. Other commands can be added to `cooldowns.commands`
(paper icon, label from the command word), but without a known success line only a refusal can start them.

## SVA catalog

Press **Open SVA catalog** (unbound by default) for a grid of every Survival SVA (season vault
item) from ManaCube's public API: the item's own model when ManaCube's resource pack is loaded,
otherwise its vanilla item.

- **Search** matches the name and the lore (every word must occur: `souls monsters`).
- **All / Only owned / Not owned** filters by what you own; **Sort** cycles A–Z, Rarest, Most common
  (by circulation). Your own SVAs have a green border.
- **Compare with player…**: type a name and press Enter or *Compare*. Borders then show
  green = only you, pink = only them, aqua = both; *×* stops comparing.
- Hover: name and lore as in game, `Circulation: N`, whether you (and the compared player) own it.
- **Left-click** closes the screen and runs `/ah search <name>` (decorative symbols dropped).
- **↻** re-fetches your owned SVAs now (at most every 15 s). Scroll with the wheel or PgUp/PgDn.

The status line under the search fields says how old the data is, or "Offline: only fetched while on
ManaCube", "Too many requests…" or "API error: HTTP 503". The last good data stays usable offline.

### SVA tooltip line

Any item whose name (colour codes ignored, case-insensitive) is an SVA's gets a line
`✦ SVA · Circulation: 107` in its tooltip, plus `· owned` (`· owned ×2`) when you own it. When several
SVAs share a name (e.g. `DEEP OCEAN SCYTHE` and its enhanced version) the item type, item model and
lore pick the right one; if that still leaves several, each circulation is listed (`27 / 342`). Only
on a gated (ManaCube) server, and only once the catalog has been downloaded.

### Data and limits

| Request | When |
|---|---|
| `GET https://api.manacube.com/api/svas/survival` (catalog, ~700 KB) | on joining ManaCube or opening the screen, if the cached copy is missing or older than 6 hours |
| `GET https://api.manacube.com/api/svas/survival/<your uuid>` | the same, if older than 10 minutes; or ↻ |
| `GET https://api.mojang.com/users/profiles/minecraft/<name>` then that player's owned SVAs | Compare (cached 10 minutes per name) |

Every request is asynchronous (10 s timeout, `User-Agent: CubeWheel/<version>`), all of them share a
budget of 20 per minute, a failed automatic fetch is retried after a minute at the earliest, and
nothing is fetched unless the server gate is open. Responses are cached in
`config/cubewheel-cache/` (`svas-survival.json`, `owned-<uuid>.json`, `index.json`); delete the folder
to force a fresh download.

```json
"svas": { "enabled": true, "tooltip": true }
```

`enabled: false` switches the whole feature off (no requests, the key does nothing);
`tooltip: false` only drops the tooltip line.

## Capture mode

Capture mode records raw samples so the parsers can be tuned to ManaCube's real formats.

1. Bind "Toggle capture mode" and press it: the action bar shows "Capture ON".
2. Run `/homes`, then open `/jobs`, `/pquests`, `/prestige` and `/challenges`; optionally play a
   little so action-bar and boss-bar text is seen. To find the prestige rank-objectives command, try
   candidate commands (e.g. `/ranks`, `/rankup`, `/prestige ranks`) and note which one's container line
   has the "Rank [✪n]" items.
3. Press the key again ("Capture OFF"). It is also off after every restart.

If a Minecraft update breaks the (optional) HUD accessors, the game still starts; action-bar and
boss-bar capture is then switched off for the session with one warning in the log, and chat and
menu capture keep working.
4. Send `config/cubewheel-captures/<yyyy-MM-dd>.jsonl` (UTC date).

Each line is JSON with a kind:

- `chat`: a chat/game message (JSON and plain text); `"overlay": true` for action-bar game messages.
- `container`: menu title, and per slot the item id, name and lore.
- `actionbar`: action-bar text, when it changes.
- `bossbars`: boss bar names and progress, when they change.
- `sidebar`: the scoreboard sidebar title and lines as drawn, when they change.
- `world`: the world local counting resolved (dimension, sidebar lines, tokens, special), when it changes.
- `local`: a counted signal (`break`, `kill`, `fish`, `shear`, `area` for a block the server broke in an
  area window, or `reject` for a break the server undid) with
  the block/mob id and name, the world and the tracker entries it moved. A `fish` line from the chat has
the species as `name` and `detail` `chat; rarity=Common size=52.2cm` (plus `; replaces the bobber
reel-in` when it took a reel-in's count back); a bobber reel-in has `detail` `bobber reel-in`. Kill diagnostics also land here,
  with a `detail`: `attack` (each own hit: type id, raw name, custom name or not, passengers), `stack`
  (a hit mob's name changed, `old -> new (killed n, local hit)`), `death` (a death not credited to you:
  `other_player`, `expired`, or `no_hit` within 32 blocks), `removed` (a mob you hit vanished
  without a death event and did not count, with the reason; `pending: waiting for loot line` when it
  is a repeat removal of a kill still waiting to be named) and `nearby` (once per mob you hit: the
  entities within 4 blocks with type, custom name, text-display text, vehicle and passengers). A kill
  counted from a removal has `detail` `removal, method a|b|c|d (...)`: a = the mob's own name, b = a
  name tag riding it or near it, c = the loot line, d = unattributed (not counted). Loot lines are read
  from the action-bar packet, from Hud.setOverlayMessage and from a per-tick poll of the Hud's action-bar
  text (whichever works; each message once); the game log names the first source that saw one:
  `[cubewheel] loot lines: <source>`. Area breaks write `area` lines: one per counted block id per 2 s
  (`detail` `crop=… mature=…`, `tree feller; …` in a Tree Feller window), one per mcMMO activation
  (`ability activated; window at Pos[…]`), and a summary at most every 5 s while windows saw changes:
  `triggers break=… attack=… ability=…; counted <id>=n …; no rule <id>=n; skipped out_of_range=n
  duplicate=n not_break=n not_log=n cascade=n placed=n cap=n`. A job that rises while `counted` stays
  empty means the server's changes did not reach the window (too far, too late, or not to air).
- `estimate`: an estimate replaced by a real read: `counted` (local) vs `actual` (from the menu).

Container lines also carry `afterCommand` (the last command you or CubeWheel sent before the menu was
read) and `afterCommandMs` (how long before), so it is clear which command opens which menu. Open a
menu by clicking inside another one and `afterCommand` still names the command that opened the first
menu; the order of lines shows the rest.

Captures contain chat text, including other players' messages; review the file before sharing it.

## Known unknowns

- Isles warp names other than `tangleroots` are unverified guesses (`/warp <name>`); edit the JSON
  if the server says unknown warp.
- The `/homes` reply format (see above).
- Lore formats of the progress menus; extraction is generic until real captures exist.
- Local counting: whether ManaCube's world dimensions are named after the world, whether its Survival
  sidebar has a `World:` line, and how "Resources" is counted; `local`/`world`/`estimate` capture lines
  answer these.
- Whether the Survival sidebar title is plain text "SURVIVAL" (it may be drawn with a custom font);
  `sidebar` capture lines record the title. If tracking stops in Survival, set
  `tracker.survivalSidebarPattern` to match the captured title, or to `""`.
- Whether the wiki's event times are current (see [Event timer](#event-timer)); compare with `/events`.
- The exact booster chat wording on Survival (see [Boosters](#boosters)).
- `/heal`'s cooldown length and its refusal wording (see [Command cooldowns](#command-cooldowns)).
- The /cow menu's wording and ManaCube's reward reset rule (see [Daily reward badge](#daily-reward-badge)).
- Boss locations beyond Wolfhaven Mines, Morend, Boss Arena and Sandara Canyon (the warp regexes are
  guesses from the world names).
- The command that opens the prestige rank-objectives menu ("Rank [✪n]" items); see
  [Refresh key](#refresh-key).

## License

MIT, see `LICENSE`.
