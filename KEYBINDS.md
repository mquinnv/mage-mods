# Minecraft Mage — Keybind Cheatsheet

Curated, conflict-free keybind layout for the modpack (MC 1.20.1 / Fabric).
Applies to both the standard and BTE client instances.

> **Design rules:** vanilla movement untouched · `M` stays the World Map ·
> Litematica rooted on `C` · no two in-world actions share a key.

## 👁️ In-game overlays — CustomHud

No more alt-tabbing. **CustomHud** renders three on-screen HUD "profiles" you flip
between with a keypress. They start hidden; the swap keys show a profile (and un-hide),
`` ` `` toggles the current one off again.

| Key | Overlay |
|-----|---------|
| `` ` `` (backtick) | Show / hide current overlay (`key.custom_hud.enable`) |
| `F6` | **Profile 1** — this keybind cheatsheet |
| `F7` | **Profile 2** — slim info (coords · facing · biome · time · FPS) |
| `F8` | **Profile 3** — debug panel (position/world + system/target) |

Bindings are pre-set in the bundled `options.txt`; to change them: **Options → Controls
→ Key Binds**, search **`custom hud`**.

> Overlay text lives in `config/custom-hud/profile1.txt` (+ `profile2`/`profile3`) — edit
> to taste with Minecraft `&`-color codes and `==Section:TopLeft/TopCenter/TopRight==`.
> **Gotcha:** keep any `==backgroundColor/scale/textShadow==` flags on the very first
> lines with no blank line before the first `==Section==`, or CustomHud rejects them.
> Full syntax: <https://customhud.dev/#docs>.

## 🗺️ Maps — Xaero's

| Key | Action |
|-----|--------|
| `M` | Open World Map |
| `B` | Place waypoint |
| `U` | Waypoints list |
| `Y` | Minimap settings |
| `]` | World Map settings |
| `Numpad +` | Instant waypoint |
| `Right Shift` | Quick-confirm (map dialogs) |
| `Z` | _unbound_ — "enlarge map" freed for Tweakeroo chords |

## 🏗️ Litematica — `C`-rooted

You only really need `C` (the main menu); the `C,x` chords are shortcuts.

| Key | Action |
|-----|--------|
| `C` | **Main menu** |
| `C, L` | Material list |
| `C, P` | Schematic placements |
| `C, S` | Selection manager |
| `C, V` | Schematic verifier |
| `C, C` | Settings |
| `C, A` | Add selection box |
| `C, R` | Toggle all rendering |
| `C, G` | Toggle schematic rendering |
| `C, T` | Toggle tool |
| `C, K` | Printer: print (standard pack only) |
| `I` | Render info overlay |
| `Page Up` / `Page Down` | Layer up / down |
| `Numpad −` | Placement settings |
| `Numpad *` | Area settings |

## 🔧 Other building — Tweakeroo / MiniHUD

| Key | Action |
|-----|--------|
| `X, C` | Tweakeroo config |
| `Z, 1`–`Z, 6` | Placement modes (plane / face / line / column / diagonal / layer) |
| `H` | MiniHUD toggle |
| `H, C` | MiniHUD config |

## 🎒 Inventory

| Key | Action |
|-----|--------|
| `R` | AutoSwitch on/off |
| `G` _(recommended)_ | IPN **sort** — set via **Mod Menu → Inventory Profiles Next → Hotkeys** |

> IPN stores its keybinds in its own config, not `options.txt`, so set its hotkeys in the IPN GUI.

## 🌈 Display

| Key | Action |
|-----|--------|
| `O` | Iris shader selection (in world) |
| `K` | Toggle shaders on/off |
| `J` | Jade overlay toggle |
| `Numpad 0` | Jade config |
| `Numpad 2`–`5` | _unbound_ — Jade recipes / uses / narrate / liquid |

## 📖 JEI

Only fire while hovering an item in a GUI, so they don't conflict with the in-world keys above.

| Key | Action |
|-----|--------|
| `R` | Show recipe |
| `U` | Show uses |
| `A` | Bookmark |
| `F` | Focus search |
| `O` | Hide / show JEI overlay |

## 🧩 Misc

| Key | Action |
|-----|--------|
| `\` | Mod Menu |
| `V` | Supplementaries quiver |
| `Alt` (hold) | Create tool menu |
| `W` | Create ponder (in Create UI) |

## ⌨️ Vanilla essentials

`WASD` move · `Space` jump · `Shift` sneak · `Ctrl` sprint · `E` inventory ·
`Q` drop · `F` swap offhand · `T` chat · `/` command · `Tab` player list ·
`1`–`9` hotbar · `F3` debug · `F5` perspective · mouse `L`/`R` attack/use · mouse-middle pick block.
