# CubeWheel — client-side ManaCube Survival helper (design)

Date: 2026-09-30. Status: approved for autonomous build (Michael, 2026-09-30: "just go ahead and
start building things as much as you can towards completion").

## Intent

**What Michael asked for:** a client-side Fabric mod that makes ManaCube Survival easier to play:
shortcut/mouse activation of `/pv`, common shops (`/sell` GUI, `/kilton`, `/alchemist`,
`/enchanter`), a warp list including `/warp crops` and `/warp spawners`, a separate menu for
"special locations" (Wolfhaven, Tangleroots, Morend, boss arena, resource worlds…), a dynamic list of
his `/home`s, and — stretch — a tracker for jobs / prestige quests / party quests that surfaces the
ones near completion, with a GUI to choose what to track.

**Decisions made in brainstorming:**
- Gamemode: **Survival** only.
- Interaction: **radial wheel** (hold a key, flick, release), long lists fall back to a scrolling list.
- `/homes` on ManaCube Survival prints a **chat list** (Michael confirmed).
- `/pv` count is per-player (rank-gated: VIP 1 … ELITE 5; Michael has 3) → configurable.
- Audience: Michael + his wife now; **publish to Modrinth eventually**. So: no hardcoding that blocks
  publishing, gate to ManaCube servers, clearly unofficial branding. JSON config is fine for v1; an
  in-game settings screen belongs to the "prepare for Modrinth" phase.

**Research findings that shaped the design** (wiki.manacube.com, manacube.com/rules, checked
2026-09-30):
- `/warp crops`, `/warp spawners` are **player (party) warps**, not server warps — they belong in a
  user-editable "Favorite warps" ring, not hard defaults of the server layout.
- Mana Worlds (Isles): Wolfhaven, Tangleroots, Sandara, Icehaven, Morend, Burninglands, reached via
  `/isles` GUI; `/warp tangleroots` confirmed, others assumed `/warp <name>`. Boss arena: `/warp boss`.
  Resource worlds: `/rtp` (== `/wild`) GUI.
- Rules: *"Rebinding a command or message to a key isn't a macro as long as you manually press it.
  Automating presses is."* → **every command this mod sends is the direct result of a keypress/click.**
  No timers, no background polling. (One narrow exception, below: `/homes` is sent when the user
  opens the Homes ring — still a direct result of a user action, once, and rate-limited.)
- ManaCube proxy accepts 26.x clients; Survival needs ≥1.21.10. Building on the repo's 26.2 stack is fine.

**Success criteria:**
1. On ManaCube, holding the wheel key and flicking to "Vaults → 2" sends `/pv 2`; the same on any
   other server does nothing (gated).
2. Opening "Homes" shows Michael's real homes within ~1 s the first time and instantly afterwards,
   without the `/homes` reply appearing in chat.
3. Wheel contents are editable in `config/cubewheel.json` and reloaded by a keybind, no restart.
4. Tracker (v0): after opening `/jobs`, `/pquests`, `/prestige` or `/challenges` once, entries with a
   visible `N / M` or `%` progress are listed; the near-complete ones (≥ threshold) and pinned ones
   show on a HUD; a picker screen toggles pins.
5. A capture mode dumps raw chat components and container item names/lore to a file so parsers can
   be tuned against real data.

## Name & identity

- Mod id `cubewheel`, display name "CubeWheel (unofficial ManaCube helper)". Package `com.mage.cubewheel`.
- Lives at `src/client-mods/cubewheel/`, same build stack as `hammerharvest` (Fabric Loom 1.17, MC 26.2,
  Mojang mappings, Java 25 release, JDK 26 toolchain). Depends only on Fabric API.

## Architecture

Pure logic is kept in Minecraft-free classes so it can be unit-tested with JUnit; thin adapters touch
Minecraft.

```
com.mage.cubewheel
├── CubeWheelClient            entrypoint: keybinds, event registration, wiring
├── ServerGate                 is the current connection a ManaCube server? (host suffix match)
├── CommandSender              the only place that sends commands; gated; logs; strips leading '/'
├── config/
│   ├── WheelNode              record tree: leaf(label, icon, command) | ring(label, icon, children)
│   │                          | dynamic(label, icon, source: "homes"|"vaults")
│   ├── CubeWheelConfig        root: enabled, serverHosts, vaultCount, listThreshold, tracker{…}, wheel
│   ├── DefaultConfig          the shipped default layout (below)
│   └── ConfigStore            load/save/reload JSON via Gson; writes defaults when missing;
│                              on parse error keeps the previous config and reports in chat
├── wheel/
│   ├── RadialMath             pure: angle→slice index, dead-zone, slice geometry
│   ├── WheelResolver          pure: expands dynamic nodes (vaults 1..N, homes from cache)
│   ├── RadialScreen           Screen: draws ring, handles hover/click/release/back
│   └── ListScreen             Screen: scrolling list fallback for rings with > listThreshold entries
├── homes/
│   ├── HomesParser            pure: extract home names from a /homes reply (click events first,
│   │                          text fallback)
│   ├── HomesCache             per-server list + timestamp, persisted to config/cubewheel-homes.json;
│   │                          also updated from the player's own /sethome and /delhome
│   └── HomesFetcher           sends /homes (user-triggered, ≤ once / 30 s), captures & suppresses
│                              the matching reply within a 3 s window
├── tracker/
│   ├── ProgressExtractor      pure: finds "N / M", "N/M", "N of M", "NN%" in lore lines → Progress
│   ├── Trackable              record: id (source + name), source, name, current, max, fraction, seenAt
│   ├── TrackerStore           map of trackables + pins, persisted to config/cubewheel-tracker.json
│   ├── ContainerScanner       on container screen open/update: if the title matches a configured
│   │                          source pattern, read item names + lore → ProgressExtractor → store
│   ├── TrackerHud             HUD overlay: pinned + near-complete entries, with "seen Xm ago"
│   └── TrackerScreen          picker: all known trackables, click to pin/unpin, sorted by fraction
└── capture/
    └── CaptureLog             toggleable: appends chat components (JSON) and container
                               title/items/lore to config/cubewheel-captures/<date>.jsonl
```

### Wheel behaviour

- **Open:** press and hold the wheel key (default `G`; any key or mouse button — vanilla keybind
  screen supports mouse buttons). Opens `RadialScreen` for the root ring; the mouse is released.
- **Select:** the slice under the cursor direction (outside a small centre dead-zone) highlights.
- **Commit:** releasing the wheel key, or left-clicking, activates the highlighted slice:
  leaf → send its command and close; ring/dynamic → drill into that ring (screen stays open, even if
  the key was released). Releasing in the dead-zone closes without action.
- **Back:** right-click or clicking the centre goes up one level; Esc closes.
- **Too many entries:** if a ring has more than `listThreshold` (default 8) entries it opens in
  `ListScreen` instead (click to run, type-to-filter).
- **Icons:** each node may name an item id (`minecraft:ender_chest`) drawn in the slice; label under it.
- **Gating:** if `ServerGate` says no, pressing the key shows an action-bar hint
  ("CubeWheel is only active on ManaCube") instead of opening. `enabled=false` disables everything.

### Default layout (root ring, 8 slices)

| Slice | Contents |
|---|---|
| Travel | `/spawn`, `/rtp`, `/teleporter`, `/warp`, `/p home`, `/back` |
| Homes | dynamic: homes from cache (→ `/home <name>`) |
| Vaults | dynamic: `/pv 1` … `/pv vaultCount`, plus `/ec`, `/p vault` |
| Shops | `/sell`, `/kilton`, `/alchemist`, `/enchanter`, `/shop`, `/ah`, `/forge`, `/fish` |
| Warps (favorites) | `/warp crops`, `/warp spawners` (user-editable player warps) |
| Isles & Bosses | `/isles`, `/warp wolfhaven`, `/warp tangleroots`, `/warp morend`, `/warp sandara`, `/warp icehaven`, `/warp burninglands`, `/warp boss`, `/bosses` |
| Progress | `/jobs`, `/pquests`, `/prestige`, `/challenges`, `/cow` |
| Party | `/p`, `/p warps`, `/p claim`, `/p map` |

Isles ring has 9 entries → list fallback; acceptable. Uncertain warp names are fine: the server just
says "unknown warp" and the user edits JSON.

### Homes

- `HomesParser` order of precedence:
  1. Walk the chat `Component` tree; any `ClickEvent` of kind run/suggest command whose value is
     `/home <name>` (or `/homes <name>`) contributes `<name>`. Most robust to colour/format changes.
  2. Else plain text: take the text after the first `:` on a line that contains "home"
     (case-insensitive), split on `,` / whitespace-comma, trim, drop empties.
  3. Else: not a homes reply.
- `HomesFetcher`: when the Homes ring is opened and the cache is empty or older than 5 minutes, and no
  fetch in the last 30 s, send `/homes` and arm a 3 s capture window. The first incoming game message
  in the window that `HomesParser` accepts updates the cache and is **suppressed** (hidden from chat).
  Messages the user triggered themselves (typed `/homes`) are parsed passively and not suppressed.
  While fetching with an empty cache the ring shows a single "Loading…" slice; when the reply lands
  the open screen refreshes.
- Outgoing command hook: `/sethome X` adds X, `/delhome X` removes X (optimistic).
- Cache is keyed by server host so different servers/accounts don't mix.

### Tracker (v0, generic)

Because the exact lore format is unknown until captured, v0 is deliberately generic:
- Sources (config `tracker.sources`): name + regex on container title, defaults
  `jobs: "(?i)jobs"`, `pquests: "(?i)quest"`, `prestige: "(?i)prestige"`, `challenges: "(?i)challenge"`.
- For each item in a matching container: name = hover name stripped of formatting; progress = first
  match in lore of `([\d,.]+[kKmM]?)\s*(?:/|of)\s*([\d,.]+[kKmM]?)` or `(\d{1,3}(?:\.\d+)?)\s*%`.
  Items without progress are ignored. Items with current ≥ max are marked complete.
- `TrackerHud` (top-right, toggle keybind): pinned entries always, plus unpinned entries with
  fraction ≥ `tracker.nearThreshold` (default 0.8) and not complete; max 6 lines;
  `Name  1,234 / 1,500 (82%) · 12m`.
- `TrackerScreen` (keybind): list of all trackables grouped by source, sorted by fraction desc, click
  toggles pin, button to forget stale entries.
- Chat updates: v0 does not parse chat progress (format unknown); capture mode collects it for v1.
- **Never** opens menus or sends commands itself; data is only as fresh as the last time the user
  opened the menu — shown by the age suffix.

### Capture mode

Keybind toggles; action-bar confirms. While on, append JSONL lines:
`{"t":…,"kind":"chat","json":<component json>,"text":"…"}` and
`{"t":…,"kind":"container","title":"…","items":[{"slot":n,"id":"…","name":"…","lore":["…"]}]}`.
Purpose: collect `/homes`, `/jobs`, `/pquests`, `/prestige`, `/challenges` samples to tune parsers.

### Keybinds (category "CubeWheel")

Wheel `G` · Reload config `(unbound)` · Toggle tracker HUD `(unbound)` · Tracker picker `(unbound)` ·
Toggle capture `(unbound)`.

### Error handling

- Bad JSON: keep last good config, show red chat line with the Gson error location. Missing file:
  write defaults.
- Commands not recognised by the server: the server's own error is shown; nothing to handle.
- Homes fetch timeout: keep old cache, ring shows cached/empty with a "Refresh" entry.
- All event handlers catch and log exceptions so a parser bug never crashes the client.

## Testing

- JUnit 5 unit tests for all pure classes: `RadialMath`, `WheelResolver`, `ConfigStore`
  (round-trip, defaults, bad JSON), `HomesParser` (click-event and text variants), `HomesCache`,
  `ProgressExtractor` (many formats), `TrackerStore` (near-complete selection, pins).
- `./gradlew build` must pass (compiles against 26.2, runs tests).
- Manual in-game checklist written to `src/client-mods/cubewheel/TESTING.md` (can't be automated here).

## Out of scope (v1)

In-game settings screen, Mod Menu integration, rank auto-detection, chat-based progress parsing,
per-source tailored lore parsers (need captures first), Modrinth publishing, localisation.
