# HotbarCycle
Minecraft Fabric mod for Stardew Valley-inspired hotbar cycling. Developed for 1.19.

# Features
Adds 2 keybinds:
- Cycle Hotbar (Default: H): shifts the rows of the inventory downwards, allowing quick access to every row of the inventory without ever opening it.
- Cycle Slot (Default: J): shifts a single column of the inventory downwards (whichever hotbar slot you have selected).

**Additionally, there is Hold-and-Scroll mode:**
Instead of clicking a keybind to cycle the hotbar/slot, hold the button and use your scroll-wheel to cycle!

There are also config options for reversing the cycle order, enabling sounds, and enabling individual rows/columns for cycling through mod menu.

# Download
**Curseforge**: https://www.curseforge.com/minecraft/mc-mods/hotbarcycle

**Modrinth**: https://modrinth.com/mod/hotbarcycle

# Video Showcase
[![HotbarCycle Showcase](https://img.youtube.com/vi/zJixvvbsNpM/mqdefault.jpg)](https://www.youtube.com/watch?v=zJixvvbsNpM)

## Mage fork (mage-mods)

Vendored from Estecka/mc-hotbarcycle, branch `26.1` (1.2.1, MIT). One change: **Cycle Slot by item group**
(on by default, Mod Menu › Hotbar Cycle). The Cycle Slot key then swaps the held item for the next item of the
same group from the three inventory rows (repeated presses walk the whole group); an item in no group is not
cycled, and the other hotbar slots never move. Pick-block, scroll and repeat-click cycling are upstream's.

Groups come from rules (`itemGroups` in `config/hotbarcycle.json`, or Mod Menu), checked in order; the first match
wins. A rule is `group: alt | alt | ...`; alternatives are separated by ` | ` (a bar with spaces, so a regex can use a
bare `|`), and an alternative is matchers joined by ` & ` (all must match). Rules with the same label form one group;
`none:` means never cycled. Matchers: `@tool`, `@weapon` (the vanilla components), `name:<regex>`, `lore:<regex>` (any
lore line), `id:<regex>`, or an item id.

The defaults (in `config/HotbarCycleConfig.java`) sort into melee, ranged, tools, utility and consumables, and were
tuned against ManaCube's whole custom-item catalog (the SVAS list CubeWheel caches, ~1,250 items), not just one
player's items. ManaCube builds weapons on tool items, so lore decides: staves and wands that shoot or deal damage are
ranged, axes are melee unless their lore names tool work (woodcutting, herbalism, vein mining, harvesting), hoes and
wands that teleport, glide, shield or pull are utility, and armor, shields, pendants, off-hand and one-time items are
never cycled. `ItemGroupsTest` pins real catalog items to their groups.

Logic is in `group/ItemGroups.java` (pure, unit-tested) and `GroupCycle.java`. Build: `./gradlew build`.
