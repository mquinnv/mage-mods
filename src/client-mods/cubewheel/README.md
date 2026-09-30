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
| `tracker.nearThreshold` | Fraction at which unpinned entries appear on the HUD, 0–1 | `0.8` |
| `tracker.hudMaxLines` | Max HUD lines, 1–20 | `6` |
| `tracker.hudVisible` | HUD on/off (saved when you toggle it, see below) | `true` |
| `tracker.sources` | Source id -> regex matched against the menu title | see below |
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
- The tracker never opens, clicks or closes menus itself.

Please check the current server rules yourself; you are responsible for how you use any client mod.

## Server gate

The mod only acts when the connected server's host equals or is a subdomain of an entry in
`serverHosts` (case-insensitive, port ignored). Elsewhere, and in singleplayer, pressing the wheel
key shows the action-bar hint "CubeWheel is only active on ManaCube", no command is ever sent, the
HUD is not drawn, menus are not recorded and capture records nothing. The toggle-HUD key and the
tracker picker still work there, on locally stored data.

## Progress tracker

The tracker is passive. When you open a menu whose title matches a `tracker.sources` regex (defaults:
`/jobs`, `/pquests`, `/prestige`, `/challenges`), the mod reads item names and lore in the top
container (never your inventory) and records the entries that show progress. Consequences:

- **Data is only as fresh as the last time you opened that menu.** The HUD shows an age suffix
  ("now", "5m", ...).
- **HUD** (top right, below potion icons): pinned entries plus unpinned incomplete entries at or
  above `nearThreshold`, at most `hudMaxLines`. Complete entries are green. F1 hides it.
- **Picker** (bind "Open tracker picker"): all known entries grouped by source, sorted by
  percentage. Click to pin/unpin. A button forgets unpinned entries not seen for 7 days.
- Stored in `config/cubewheel-tracker.json`.

Progress recognised in lore: `N / M`, `N/M`, `N of M`, `k`/`m` suffixes (`1.5k / 2k`) and percentages.
Glued words and three-part dates are rejected. Known limitation: a two-part date such as `12/25`
can be mistaken for progress. Chat and action-bar progress is not parsed; menus that show no numbers
are ignored.

## Capture mode

Capture mode records raw samples so the parsers can be tuned to ManaCube's real formats.

1. Bind "Toggle capture mode" and press it: the action bar shows "Capture ON".
2. Run `/homes`, then open `/jobs`, `/pquests`, `/prestige` and `/challenges`; optionally play a
   little so action-bar and boss-bar text is seen.
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

Captures contain chat text, including other players' messages; review the file before sharing it.

## Known unknowns

- Isles warp names other than `tangleroots` are unverified guesses (`/warp <name>`); edit the JSON
  if the server says unknown warp.
- The `/homes` reply format (see above).
- Lore formats of the progress menus; extraction is generic until real captures exist.

## License

MIT, see `LICENSE`.
