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

Any key or mouse button can be used for the wheel.

## Using the wheel

- Hold the wheel key. Move the mouse towards a slice to highlight it.
- Release the key or left-click to activate: a command slice sends its command and closes; a ring
  slice opens that ring (the wheel stays open, even if you already released the key).
- Right-click or click the centre to go back one level (at the root, closes). Esc closes.
- Releasing in the centre dead zone closes without sending anything.
- Clicking any mouse button ends "hold" mode, so releasing the key afterwards does nothing.
- A ring with more entries than `listThreshold` (default 8) opens as a scrolling list instead: type
  to filter (label or command), Enter runs the first visible row, click a row to run it, Esc or
  right-click returns to the wheel.

### Default layout

Travel, Homes, Vaults, Shops, Warps, Isles & Bosses, Progress, Party. All of it is editable, see below.

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
| `enabled` | Master switch | `true` |
| `serverHosts` | Host suffixes the mod is active on | `["manacube.com", "manacube.net"]` |
| `vaultCount` | Number of `/pv` entries in a `vaults` node (your rank decides this), 0–54 | `3` |
| `listThreshold` | Rings with more entries open as a list, 3–16 | `8` |
| `tracker.nearThreshold` | Fraction from which HUD entries are highlighted yellow, 0–1 | `0.8` |
| `tracker.hudMaxLines` | Max HUD lines, 1–20 (a file written earlier keeps its own value) | `8` |
| `tracker.hudVisible` | HUD on/off (saved when you toggle it, see below) | `true` |
| `tracker.sources` | Source id -> regex matched against the menu title | see below |
| `tracker.refreshCommands` | Commands the "Refresh trackers" key sends, one at a time (max 8) | `["/pquests", "/prestige", "/jobs"]` |
| `tracker.sidebarLinks` | Sidebar key -> regex on tracked names that follow that live value | `{"Skills": "(?i)reach [\\d,]+ skill level"}` |
| `tracker.local.enabled` | Live `~` estimates between menu reads (see [Live estimates](#live-estimates-local-counting)) | `true` |
| `tracker.local.blocks` / `kills` / `fish` | Count own block breaks / kills / catches | `true` each |
| `tracker.local.worlds` | World names recognised in objectives ("Wolfhaven Resources") | the six Mana worlds |
| `tracker.local.specialWorlds` | Worlds that count as "special worlds (/worlds)" | the six Mana worlds |
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

A node must be exactly one kind. Nodes that are not are **silently ignored** (with their children):
a blank or missing label; both `command` and `children` on a non-dynamic node; neither; an unknown
`dynamic` source; a `dynamic` node that also has a `command`.

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

Please check the current server rules yourself; you are responsible for how you use any client mod.

## Server gate

The mod only acts when the connected server's host equals or is a subdomain of an entry in
`serverHosts` (case-insensitive, port ignored). Elsewhere, and in singleplayer, pressing the wheel
key shows the action-bar hint "CubeWheel is only active on ManaCube", no command is ever sent, the
HUD is not drawn, menus are not recorded and capture records nothing. The toggle-HUD key and the
tracker picker still work there, on locally stored data.

## Progress tracker

The tracker reads what you open. When you open a menu whose title matches a `tracker.sources` regex (defaults:
`/jobs`, `/pquests`, `/prestige`, `/challenges`), the mod reads item names and lore in the top
container (never your inventory) and records the entries that show progress. Consequences:

- **Data is only as fresh as the last time you opened that menu** (or pressed the refresh key, or
  a linked sidebar value changed, see below). The HUD shows an age suffix ("now", "5m", ...).
- **HUD** (top right, below potion icons): pinned entries first, then every other incomplete entry,
  closest to done first, at most `hudMaxLines` lines. Entries at or above `nearThreshold` are yellow,
  estimated ones keep their `~`. Complete entries only show when pinned (green). Hidden entries never
  show. F1 hides it.
- **Picker** (bind "Open tracker picker"): all known entries grouped by source, sorted by
  percentage. Left-click to pin/unpin, right-click to hide/unhide an entry on the HUD (hidden entries
  stay listed, dimmed and marked "(hidden)"). A button forgets unpinned entries not seen for 7 days.
- Stored in `config/cubewheel-tracker.json`.

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
tracker menus are hidden and closed; any other menu (a chest, the warps menu) stops the run and stays
visible and usable. Opening any other screen (chat, inventory, pause menu), pressing Esc, or pressing
use or attack while the run waits for a menu (you may be opening a chest or an NPC) stops the run. A
tracker menu that arrives late, after its 3 s wait, is still read and closed. A second press within
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
unless pinned. Entries from the main jobs menu (e.g. `GOLDEN CRATE`) are kept.

### Live estimates (local counting)

Between menu reads CubeWheel estimates progress from what it sees you do, so the HUD moves while you
play. It only watches; it never sends a command, opens a menu or clicks anything.

- An estimated entry is shown with a `~`: `Haven Harvester  ~6,437 / 10,000 (64%) · 5m`. The age is still
  that of the last real read. Percentage quests are shown in objective units (64% of 10,000 plus what
  was counted since).
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
| Mine / Break / Chop / Dig N Stone, Cobblestone, Logs, Ores, Resources | break a matching block yourself; "Resources"/"Blocks" = any block |
| Kill / Slay N Mobs / Monsters / Zombies / Mana Wolves | kill it: you were the last player to damage it within 5 s (arrows and tridents count) |
| Catch N Fish | reel in while the bobber is biting |
| ... "Wolfhaven Resources", "Sandara Monsters", "in <world>" | only while you are in that world |
| ... with "special worlds (/worlds)" in the lore | only in one of `tracker.local.specialWorlds` |

The world comes from the dimension name and the sidebar's `World:` line, if the server shows one. If
it cannot be told, world-scoped objectives are not counted (under-count, never over-count).

Not counted (the next read fixes the number): skill levels, "Complete N Jobs", boss kills and
participation, discovery, dungeons, party/island levels, hand-in jobs ("Harvest and hand in ..."),
specific fish ("Catch 5 Angelfish"), quests with several objectives, and blocks broken by area tools
(3x3 hoes, hammers) beyond the one you broke yourself. Blocks you placed yourself do not count when
broken again, and a break the server undoes (claims, protection) is taken back.

Party quests count the whole party's work; the estimate only counts yours, so it runs low until the
next read.

Progress recognised in lore: `N / M`, `N/M`, `N of M`, `k`/`m` suffixes (`1.5k / 2k`) and percentages.
Glued words and three-part dates are rejected. Known limitation: a two-part date such as `12/25`
can be mistaken for progress. Chat and action-bar progress is not parsed; menus that show no numbers
are ignored.

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
- `local`: a counted signal (`break`, `kill`, `fish`, or `reject` for a break the server undid) with
  the block/mob id and name, the world and the tracker entries it moved.
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
- The command that opens the prestige rank-objectives menu ("Rank [✪n]" items); see
  [Refresh key](#refresh-key).

## License

MIT, see `LICENSE`.
