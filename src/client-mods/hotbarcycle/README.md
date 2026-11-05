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

Groups are config lines (`itemGroups` in `config/hotbarcycle.json`), matchers separated by `|`:
`@tool`, `@weapon` (the vanilla components), `name:<regex>` (display name), or an item id. Defaults:

    @tool | @weapon | minecraft:bow | minecraft:crossbow | minecraft:trident | minecraft:mace | minecraft:fishing_rod | name:(?i)^zombie hand$ | name:(?i)wizards? book | name:(?i)magical book
    name:(?i)^candy apple$ | name:(?i)^bottle of nectar$ | name:(?i)^champion potion$

Logic is in `group/ItemGroups.java` (pure, unit-tested) and `GroupCycle.java`. Build: `./gradlew build`.
