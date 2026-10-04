# CubeWheel in-game settings: implementation plan

Status: draft for Michael's review (2026-10-03). Produced by a read-only planning agent; open questions at the end.
Base path: `src/client-mods/cubewheel`.

## 1. Approach

**YACL for the flat settings, hand-rolled vanilla `Screen`s for the wheel editor and the arrange-panels screen
(already shipped in beta.13), optional Mod Menu entrypoint.**

| Library | Latest for 26.2 (Fabric) | Notes |
|---|---|---|
| **YACL** | `3.9.7+26.2-fabric` (2026-09-20); 26.3/26.4 builds out | Very active; depends only on Fabric API; already in Michael's instance (via Zoomify). Maven `maven.isxander.dev/releases`, `dev.isxander:yet-another-config-lib`. |
| **Mod Menu** | `20.0.3` (2026-09-24) | Already in the pack. Maven `maven.terraformersmc.com/releases`, `com.terraformersmc:modmenu`. |
| Cloth Config | `26.2.155+fabric` | Older UI; no advantage here. |
| owo-lib | `0.13.1+26.2` | Whole UI framework; overkill. |

Why YACL: ~60 scalar/list settings across 10 groups. YACL gives toggles, sliders, enums, string lists, tooltips,
reset-to-default and search for free. Ship it jar-in-jar (`include`) so one `cubewheel.jar` works for Liz and Modrinth
users; list it as a dependency on Modrinth. Mod Menu stays optional (`modCompileOnly`, `suggests`).

## 2. Edits flow into ConfigStore (pure core)

Nearly every consumer reads `CubeWheelClient.config().current()` per frame/event, so swapping `current` is a live
apply with no restart.

- `ConfigStore.copyOf(cfg)`: deep copy (Gson round-trip). The screen edits a draft, never `current()`.
- Extract `normalize` and its helpers into a public `config/ConfigNormalizer`; `ConfigStore.load` keeps calling it.
- `ConfigStore.apply(draft)`: set `configVersion`, normalize, `current = draft`, save, `lastLoadOk = true`, return
  warnings, which are shown in chat as the reload key does. Migrations still run only on load of older files.
- Broken `cubewheel.json` (`lastLoadOk() == false`): the screen shows a red banner and Save replaces the file; the
  keybind toggles keep refusing.

## 3. Screen structure

1. **General**: `enabled`, `serverHosts`, `vaultCount`, `listThreshold`; buttons Edit wheel…, Reset wheel, Reload
   from file, Open config folder.
2. **HUD panels**: a group per panel (Status, Events, Jobs, Tracker, Boosters, Cooldowns, Charms) with its toggles;
   **Arrange panels…** button; exact positions in a collapsed group.
3. **Events**: `enabled`, `show`, `alertMinutes`, `timezone`, `bossMinutes`, `schedule` and `bossWarps` as text lines
   (`KOTH | every 2h from 00:30`, `(?i)boss arena -> /warp boss`).
4. **Tracker**: sources, refresh commands, sidebar links, survival sidebar pattern, local counting switches and worlds.
5. **Daily reward**: `enabled`, periods, menu title pattern.
6. **SVA**: `enabled`, `tooltip`.

**Wheel editor v1** (`wheel/edit/WheelEditorScreen`): scrollable tree; Add command, Add ring, Add to arc, Edit,
Delete, Move up/down/out, Reset, Save, Cancel. `NodeEditScreen` edits label, icon (live preview), command, "fan out as
arc". Dynamic nodes are editable/deletable but not created in v1; arcs hold plain commands only. A node normalize
would drop is reported before saving. Later: drag reorder, icon picker, live preview, import/export.

## 4. Opening it

- Mod Menu entrypoint (`settings/ModMenuEntry`).
- Keybind `key.cubewheel.settings` (unbound).
- Wheel entry: `cubewheel:` pseudo-commands are client actions resolved in `CommandSender.send`
  (`cubewheel:settings`, `cubewheel:wheel-editor`, `cubewheel:arrange`); default wheel gets More › Settings, and a
  config v8 migration appends it.
- `/cubewheel` client command (opens the screen one tick later, after chat closes).

## 5. Testing

Pure JUnit: `ConfigNormalizerTest`, `ConfigStoreTest` (copyOf, apply, broken file), `LinesTest` (line codecs),
`WheelEditsTest` (tree ops and refusals), `SettingsSpecTest` (every config field bound by exactly one setting, so a
new field fails the build until exposed). Manual checks go in `TESTING.md`.

## 6. Tasks (each shippable)

Phase 0, pure plumbing:
1. `ConfigNormalizer` extraction, `copyOf`, `apply` + tests.
2. `config/Lines.java` + `LinesTest`.
3. `cubewheel:` client actions in `CommandSender` + normalize exemption + test.

Phase 1, dependency and screens:
4. Gradle/`fabric.mod.json` wiring for YACL (JiJ) and Mod Menu (optional).
5. `settings/SettingsSpec` + `SettingsScreens` (General, Daily reward, SVA) + Mod Menu entry + keybind.
6. HUD panels category with **Arrange panels…** button.
7. Tracker category. 8. Events category.
9. `/cubewheel` command, More › Settings leaf, config v8 migration.

Phase 2, wheel editor:
10. Pure `WheelEdits` + `WheelNodeCheck` + tests.
11. `WheelEditorScreen` + `NodeEditScreen`.
12. Polish backlog as separate tasks.

Phase 3, release: README/TESTING sections, version bump, Modrinth listing.

## 7. Open questions for Michael

- YACL jar-in-jar (~1 MB, friendlier) or a hard dependency?
- May the settings screen overwrite a broken `cubewheel.json` (with a banner)?
- Keep numeric position fields next to the arrange screen, or hide them?
- Map/schedule editing as text lines for v1 OK?
- Wheel editor v1 scope (no creating dynamic nodes, no drag) OK?
