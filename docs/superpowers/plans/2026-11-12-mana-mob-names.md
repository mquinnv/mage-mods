# Mana Mob Names in Jade and Subtitles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In ManaCube mana worlds, Jade and the subtitle overlay name custom-model mobs by their real name tag ("Mana Wolf") instead of the vanilla entity the model is built on.

**Architecture:** A once-a-second registry (`ModelMobRegistry`) groups nearby model parts into mobs, using the pure rules already in `tracker/local` (`ModelHitbox`, `NameResolver`, `StackName`). An optional Jade plugin and a `SubtitleOverlay` mixin look mobs up in it. Task 1 is a capture that gates the fixtures and the subtitle half.

**Tech Stack:** Java 25, Fabric (loom 1.17, Mojang mappings, MC 26.2), Jade 26.2.11 API (compileOnly), JUnit 5.

**Spec:** `docs/superpowers/specs/2026-11-12-mana-mob-names-design.md`

All paths below are relative to `src/client-mods/cubewheel/` unless they start with `docs/`. Package root: `src/main/java/net/mage/cubewheel/` (written `cw/` below); tests: `src/test/java/net/mage/cubewheel/` (written `cwt/`).

Build/test command (from `src/client-mods/cubewheel`): `./gradlew test` (JDK 25 is pinned in `gradle.properties`). Never copy a jar into a running Prism instance.

## Global Constraints

- Everything is a no-op outside a mana world (`WorldInfo.special == false`): the registry is empty and both hooks pass through.
- CubeWheel must load and run with Jade absent: Jade classes are only referenced from the `cw/compat/jade/` package, loaded via the `"jade"` entrypoint; Jade is `compileOnly`, never `include`d; add `"jade": "*"` to `suggests`.
- Names shown are always the bare `StackName.parse(...).name()`, never the raw tag line.
- Jade's own health line is hidden for registry mobs; health shown is parsed from the tag. No tag health → no health line.
- When unsure which mob a sound belongs to, leave the subtitle vanilla.
- No per-frame or per-sound entity scans: hooks do map lookups only. The scan runs every 20 client ticks over entities within 48 blocks of the player.
- Kill-tracker behaviour is unchanged: all existing tests stay green.
- New config: `manaNames.jade` and `manaNames.subtitles`, both default `true`; `DefaultConfig.CONFIG_VERSION` 17 → 18; `mod_version` `0.1.0-beta.103` → `0.1.0-beta.104`.

## Review Focus

1. **Two mana mobs of the same base type side by side**: a sound between them must go to the closer one only if it is within 2 blocks. If both are equally close (within 0.25 blocks of each other's distance), leave it vanilla. Test in Task 5 (`ambiguousSoundStaysVanilla`).
2. **A mob whose tag has not loaded yet** (no text yet, or a blank text display): the mob is not registered, so Jade and subtitles stay vanilla rather than showing "" or a multiplier like "2.5x". Test in Task 2 (`blankOrMultiplierTagIsNotAName`).
3. **A tag with a stack count** ("3x Tiger"): the name is "Tiger", and health parsing doesn't read the count as health. Test in Task 2 (`stackCountIsNotHealth`).
4. **Leaving a mana world mid-session** (warp to spawn): the registry clears on the first tick that `special` is false, so no stale names. Test in Task 3 (`clearsWhenLeavingManaWorld`, against the pure `RegistryState`).
5. **A plain vanilla mob near a model** (a real wolf standing next to a Mana Wolf): it is not a part of the model unless the grouping rules say so, so its subtitles and Jade name stay vanilla. Test in Task 2 (`visibleVanillaMobNearbyIsNotAPart`).

---

### Task 1: Capture model shapes and sounds (gate)

The output of this task is a capture file and a short findings note. Michael plays; the implementer builds the logging and reads the result.

**Files:**
- Modify: `cw/capture/CaptureLog.java`: add `modelShape(...)` and `sound(...)` line writers.
- Create: `cw/tracker/local/mc/ModelCaptureProbe.java`: in-game adapter.
- Create: `cw/mixin/SoundCaptureMixin.java`, registered in `src/main/resources/cubewheel.mixins.json`.
- Test: `cwt/capture/CaptureLogModelTest.java`
- Create: `docs/superpowers/specs/2026-11-12-mana-mob-capture-findings.md` (repo root `docs/`)

**Interfaces:**
- Produces: `record CaptureLog.Part(int entity, String type, Integer vehicle, List<Integer> passengers, boolean invisible, boolean mob, String customName, String text, double x, double y, double z)`.
- Produces: `void CaptureLog.modelShape(int hitEntity, String hitType, List<Part> parts, long timeMs)`, which writes `{"kind":"model_shape",...}`.
- Produces: `void CaptureLog.sound(String soundId, String subtitleKey, String source, double x, double y, double z, List<Nearby> near, long timeMs)`, which writes `{"kind":"sound",...}`.
- Task 2 consumes the captured JSON as test fixtures.

- [ ] **Step 1: Write failing tests** `modelShapeLineHasEveryPart` and `soundLineHasKeyAndPosition` in `CaptureLogModelTest`. Use the no-executor `CaptureLog` against a temp dir, then parse the written line and assert `kind`, the part count, one part's `vehicle`/`passengers`/`text`, and the sound's `subtitleKey` and coordinates.
- [ ] **Step 2: Run** `./gradlew test --tests '*CaptureLogModelTest'`. Expected: FAIL (methods missing).
- [ ] **Step 3: Implement** both writers, following the existing line writers' style.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Implement the adapter and mixin.** Both run only while capture mode is on and `WorldInfo.special`.
  - `ModelCaptureProbe`: on each client tick where the crosshair entity changes to a non-player entity, log every entity within 4 blocks of it as `Part`s, plus the hit entity's id and type. That entity is what Jade would hit.
  - `SoundCaptureMixin`: inject at HEAD of `SoundManager.play(SoundInstance)` (check the 26.2 name). Log the id, the subtitle key from `WeighedSoundEvents.getSubtitle()` (null if none), the source, the position, and `Nearby` entities within 3 blocks of the sound.
- [ ] **Step 6: Build** with `./gradlew build` (expected: BUILD SUCCESSFUL). Hand Michael the jar path and these instructions: close the game; install the jar (for his own instance, copy it into Realism's mods); turn on capture mode; in a mana world, look at and stand next to a Mana Wolf for about 30 s, then do the same with a Sandara viper or rattlesnake; turn capture off. Wait for Michael.
- [ ] **Step 7: Read the capture** (the day file under the capture dir). Write the findings note: for each mob, which entity a look hits; the hitbox and base mob types; how bones, clouds and tag relate; the tag text format; and whether its sounds are vanilla `subtitles.entity.<type>.*` keys from the base mob's position. Copy two trimmed `model_shape` lines per mob into `src/test/resources/manamobs/<mob>.json` for Task 2.
- [ ] **Step 8: Gate.** Check two things: the sounds are vanilla entity subtitles, and each mob has an invisible base mob whose type matches them. If either fails, mark Task 5 BLOCKED in the findings note and report back to the controller (who asks Michael). Tasks 2–4 and 6 continue either way.
- [ ] **Step 9: Commit** with `git commit -m "feat(cubewheel): capture mana-mob shapes and sounds"`.

### Task 2: Pure model-mob grouping, naming and tag health

**Files:**
- Create: `cw/tracker/local/ModelMobs.java` (pure; no Minecraft imports)
- Create: `cw/tracker/local/TagHealth.java` (pure)
- Test: `cwt/tracker/local/ModelMobsTest.java`, `cwt/tracker/local/TagHealthTest.java`
- Fixtures: `src/test/resources/manamobs/*.json` from Task 1

**Interfaces:**
- Produces: `record ModelMobs.Snap(int id, String type, Integer vehicle, List<Integer> passengers, boolean invisible, boolean mob, boolean owned, String customName, String text, double x, double y, double z)`. It has the same fields as `CaptureLog.Part` plus `owned`. Add a `static Snap from(CaptureLog.Part p)` so tests load fixtures directly.
- Produces: `record ModelMob(String name, Integer tagEntity, String baseType, int hitbox, double x, double y, double z, Set<Integer> parts)`, a top-level class in `cw/tracker/local/ModelMob.java`. `baseType` is nullable. `hitbox` is the stable Jade target: the interaction if there is one, else the invisible mob.
- Produces: `static List<ModelMob> ModelMobs.group(List<Snap> snaps)`.
- Produces: `static OptionalDouble TagHealth.parse(String rawTagLine)`.

Grouping rules, which the tests pin:
- A mob is anchored on each snap that `ModelHitbox.isModelHitbox` accepts. To do that, build `ModelHitbox.Facts` from the snap: the vehicle type comes from the snap map, and `modelPartsNear` means an `item_display` riding an `area_effect_cloud` within `NameResolver.RANGE`.
- Anchors that share a vehicle chain, or that sit within `NameResolver.RANGE` of the same bones cloud (a cloud with ≥ 2 `item_display` passengers), are one mob. This covers Sandara's sibling hitboxes.
- Parts: the anchors, their vehicles, the nearest bones cloud and its passengers, and the chosen tag.
- Name: build `NameResolver.Candidate`s the way `NearbyProbe.around` does, then use `NameResolver.pick`, then take `StackName.parse(firstLine).name()`. A mob with no name is dropped.
- `baseType`: the type of an invisible `mob` part (not an interaction). If there are several, use the most common type; ties go to the lowest id.

- [ ] **Step 1: Write failing tests** in `ModelMobsTest`:
  - `manaWolfFromCaptureIsOneMob`: the fixture gives exactly 1 mob, named `"Mana Wolf"`; `parts` contains every bone id; `hitbox` is the id the findings say Jade hits.
  - `sandaraViperSiblingHitboxesAreOneMob`: 1 mob named `"Viper"`, with `baseType` set to the findings' type.
  - `blankOrMultiplierTagIsNotAName`: when the only nearby text is `""` or `"2.5x"`, the result is empty.
  - `visibleVanillaMobNearbyIsNotAPart`: add a visible, non-invisible `minecraft:wolf` snap 1 block away to the wolf fixture; its id is in no mob's `parts`.
  - `twoModelsTwoMobs`: two fixture copies, ids offset and moved 10 blocks apart, give 2 mobs.
- [ ] **Step 2: Write failing tests** in `TagHealthTest`:
  - `"Viper 75⺛"` → 75.
  - `"Mana Wolf ❤ 20"` → 20.
  - `"Mana Wolf [12.5/40]"` → 12.5.
  - `"Mana Wolf"` → empty.
  - `stackCountIsNotHealth`: `"3x Tiger"` → empty and `"Tiger (x3)"` → empty.
  - The real tag line from the findings → the expected value.
- [ ] **Step 3: Run** `./gradlew test --tests '*ModelMobsTest' --tests '*TagHealthTest'`. Expected: FAIL.
- [ ] **Step 4: Implement** `TagHealth.parse`. Match the same trailing-health forms as `StackName.HEALTH`. Lift that pattern to package-private in `StackName` and add capture groups rather than duplicating it. Strip the stack count first.
- [ ] **Step 5: Implement** `ModelMobs.group` per the rules above. Where it reuses `NearbyProbe` logic (bones count, `MIN_BONES`), move that logic into `ModelMobs` and have `NearbyProbe` call it.
- [ ] **Step 6: Run** `./gradlew test`. Expected: every test passes, including all existing kill-tracker tests.
- [ ] **Step 7: Commit** with `git commit -m "feat(cubewheel): group mana-model parts into named mobs"`.

### Task 3: Registry, lifecycle and config

**Files:**
- Create: `cw/tracker/local/RegistryState.java` (pure state holder)
- Create: `cw/manamobs/ModelMobRegistry.java` (Minecraft adapter; static facade)
- Modify: `cw/config/CubeWheelConfig.java`: add `public ManaNames manaNames = new ManaNames();` with `public boolean jade = true; public boolean subtitles = true;`
- Modify: `cw/config/DefaultConfig.java` (`CONFIG_VERSION = 18`) and the config upgrade path (`ConfigNormalizer` / `WheelUpgrade`, as previous added sections did); `cw/settings/SettingsSpec.java` and `SettingsScreens.java`: two toggles under a "Mana mob names" group.
- Modify: `cw/CubeWheelClient.java`: register the tick and the disconnect/world-change clear.
- Test: `cwt/tracker/local/RegistryStateTest.java`; extend the existing config-normalizer test for v17 → v18.

**Interfaces:**
- Consumes: `ModelMobs.group`, `ModelMob`, `TagHealth.parse` (Task 2).
- Produces: `RegistryState`:
  - `void replace(List<ModelMob> mobs)`
  - `void clear()`
  - `Optional<ModelMob> byEntity(int id)`
  - `Optional<ModelMob> nearSound(String baseType, double x, double y, double z)`: the closest mob with that `baseType` within 2.0 blocks of its position. Empty if the second-closest is within 0.25 blocks of the same distance.
- Produces: `ModelMobRegistry`:
  - `static Optional<ModelMob> byEntity(int id)`
  - `static Optional<ModelMob> nearSound(String baseType, Vec3 pos)`
  - `static OptionalDouble liveHealth(ModelMob m)`: reads the tag entity's current text through the same text path `NearbyProbe.text` uses (make that helper package-shared), then `TagHealth.parse`. It falls back to empty.
  - `static boolean active()`: true when in a mana world and the registry is non-empty.

- [ ] **Step 1: Write failing tests** in `RegistryStateTest`:
  - `byEntityFindsMobFromAnyPart`
  - `nearSoundPicksClosestWithinTwoBlocks`
  - `nearSoundIgnoresOtherBaseType`
  - `nearSoundBeyondTwoBlocksIsEmpty`
  - `ambiguousSoundStaysVanilla`: 2 mobs at 1.0 and 1.1 blocks → empty
  - `clearsWhenLeavingManaWorld`: after `clear()`, every lookup is empty
- [ ] **Step 2: Add a failing config test.** A v17 config upgrades to v18 with both `manaNames` flags `true`.
- [ ] **Step 3: Run** `./gradlew test`. Expected: the new tests FAIL.
- [ ] **Step 4: Implement** `RegistryState`, the config fields, the upgrade, and the settings toggles.
- [ ] **Step 5: Implement** `ModelMobRegistry.tick`. Every 20 ticks: if `WorldProbe` says special and either toggle is on, snapshot entities within 48 blocks of the player (`level.getEntities(player, box.inflate(48), e -> !(e instanceof Player))`) into `Snap`s, then call `state.replace(ModelMobs.group(snaps))`. Otherwise call `state.clear()`. Also clear on disconnect and level change.
- [ ] **Step 6: Run** `./gradlew test build`. Expected: all tests pass and the build succeeds.
- [ ] **Step 7: Commit** with `git commit -m "feat(cubewheel): rolling registry of nearby mana mobs"`.

### Task 4: Jade plugin

**Files:**
- Modify: `build.gradle`: add the repo that hosts the Jade 26.2.11 API (try `maven { url = "https://api.modrinth.com/maven" }` with `compileOnly "maven.modrinth:jade:<version id for 26.2.11 Fabric>"`). Pin the exact version id with a comment.
- Modify: `src/main/resources/fabric.mod.json`: `"jade": ["net.mage.cubewheel.compat.jade.CubeWheelJadePlugin"]` entrypoint; `"jade": "*"` in `suggests`.
- Create: `cw/compat/jade/CubeWheelJadePlugin.java`, `cw/compat/jade/ManaMobNameProvider.java`
- Test: `cwt/compat/jade/JadeIsolationTest.java`

**Interfaces:**
- Consumes: `ModelMobRegistry.byEntity`, `ModelMobRegistry.liveHealth`, and `config.manaNames.jade` (Task 3).

- [ ] **Step 1: Write failing test** `noJadeReferencesOutsideCompat`. Walk `src/main/java`; any file outside `compat/jade/` that contains `snownee.jade` fails the test. This pins the optional-dependency constraint.
- [ ] **Step 2: Run** `./gradlew test --tests '*JadeIsolationTest'`. Expected: PASS now (no references yet). Keep the test as a guard.
- [ ] **Step 3: Implement** `CubeWheelJadePlugin.registerClient`. Look up the exact names in the Jade 26.2.11 API sources; the shape is:
  - `addRayTraceCallback`: when the accessor is an `EntityAccessor` whose entity id is in the registry and the toggle is on, return `registration.entityAccessor().from(accessor).entity(<entity with id mob.hitbox()>).build()`. If that entity isn't loaded, return the accessor unchanged.
  - Register `ManaMobNameProvider` as an entity component for `Entity.class`. It replaces the object name with `mob.name()` (Jade's core object-name id), removes Jade's health line, and, when `liveHealth` is present, adds a health line formatted like Jade's own health (one decimal at most, e.g. `"75"`, `"12.5"`).
- [ ] **Step 4: Run** `./gradlew test build`. Expected: all tests pass and the build succeeds. Then check the jar with `unzip -l build/libs/*.jar | grep -i jade`: no Jade classes are bundled.
- [ ] **Step 5: Commit** with `git commit -m "feat(cubewheel): Jade names mana mobs by their tag"`.

### Task 5: Subtitles (skip if Task 1 marked it BLOCKED)

**Files:**
- Create: `cw/manamobs/SubtitleNames.java` (pure)
- Create: `cw/mixin/SubtitleOverlayMixin.java`, registered in `cubewheel.mixins.json`
- Test: `cwt/manamobs/SubtitleNamesTest.java`

**Interfaces:**
- Consumes: `ModelMobRegistry.nearSound` and `config.manaNames.subtitles`.
- Produces: `static String SubtitleNames.rename(String translated, String vanillaName, String mobName)`.
- Produces: `static Optional<String> SubtitleNames.entityType(String subtitleKey)`: `"subtitles.entity.wolf.growl"` → `"minecraft:wolf"`; any key not under `subtitles.entity.` → empty.

- [ ] **Step 1: Write failing tests** in `SubtitleNamesTest`:
  - `rename("Wolf growls","Wolf","Mana Wolf")` → `"Mana Wolf growls"`.
  - `rename("Slime squishes","Slime","Viper")` → `"Viper squishes"`.
  - `rename("Footsteps","Wolf","Mana Wolf")` → `"Mana Wolf: Footsteps"`.
  - Replacing is whole-word and first-only: `rename("Wolfish wolf","Wolf","Mana Wolf")` → `"Mana Wolf: Wolfish wolf"`.
  - `entityType("subtitles.entity.wolf.growl")` → `minecraft:wolf`.
  - `entityType("subtitles.block.chest.open")` → empty.
  - Underscored types: `entityType("subtitles.entity.cave_spider.hurt")` → `minecraft:cave_spider`.
- [ ] **Step 2: Run** `./gradlew test --tests '*SubtitleNamesTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** both functions.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Implement the mixin** on `SubtitleOverlay.onPlaySound(SoundInstance, WeighedSoundEvents, float)` (check the 26.2 signature).
  - Use `@ModifyVariable`/`@Redirect` on the subtitle component it reads from `WeighedSoundEvents.getSubtitle()`.
  - Act only when the toggle is on, `ModelMobRegistry.active()`, the component is translatable, and `entityType(key)` is present.
  - Then call `nearSound(type, instance position)`. On a match, replace the component with `Component.literal(rename(component.getString(), EntityType.byString(type).get().getDescription().getString(), mob.name()))`.
  - Otherwise leave the component unchanged.
- [ ] **Step 6: Run** `./gradlew test build`. Expected: all tests pass and the build succeeds.
- [ ] **Step 7: Commit** with `git commit -m "feat(cubewheel): subtitles name mana mobs"`.

### Task 6: Docs, version, manual check

**Files:**
- Modify: `README.md`: bullets for Jade and subtitle names, the two toggles, and "mana worlds only".
- Modify: `TESTING.md`: manual steps. With Jade, look at a Mana Wolf and at a Sandara viper: the name is bare, health matches the tag, and the result is the same whether the look lands on the hitbox or the body. With subtitles on, stand near a Mana Wolf: "Mana Wolf …", and repeats stack. Next to a real wolf, the subtitles stay "Wolf …". Out of a mana world everything is vanilla. With Jade removed, the game starts and CubeWheel works.
- Modify: `gradle.properties`: `mod_version=0.1.0-beta.104`.

- [ ] **Step 1: Edit** the three files.
- [ ] **Step 2: Run** `./gradlew clean test build`. Expected: all tests pass and the build succeeds.
- [ ] **Step 3: Commit** with `git commit -m "docs(cubewheel): mana mob names (beta.104, config v18)"`.
- [ ] **Step 4: Hand off for the manual check.** Give the controller the jar path and the TESTING.md steps. Michael runs them with the game closed for the install. Don't install anything on Liz's PCs as part of this plan.
