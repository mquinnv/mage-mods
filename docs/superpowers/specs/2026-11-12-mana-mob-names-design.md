# Mana mob names in Jade and subtitles: design

_2026-11-12. CubeWheel (`src/client-mods/cubewheel`)._

## Intent

ManaCube's mana-world mobs are custom models: an invisible vanilla mob, or a `minecraft:interaction` riding an
`area_effect_cloud`, plus `item_display` bones riding another cloud and a text-display name tag ("Mana Wolf").
The client only knows about the vanilla parts, so:

- **Jade** names the underlying vanilla mob or hitbox instead of the name on the tag.
- **Subtitles** say "Wolf growls" (or whatever the base mob is) instead of "Mana Wolf growls".

Michael wants both to use the real ManaCube mob name while in a mana world.

## Goals

- When Jade looks at any part of a model mob (hitbox, invisible base mob, bone), it shows the mob's bare resolved
  name ("Viper", "Mana Wolf", as `StackName.parse` returns it) and the mob's health taken from its name tag
  ("Viper 75⺛" → 75). Whatever health the hidden part reports is never shown. (Michael's call, 2026-11-12.)
- Subtitles caused by a model mob name that mob ("Mana Wolf growls").
- Both read one shared answer: which tracked model mob this entity, or this sound position, belongs to.

## Non-goals

- Changing anything outside mana worlds (`WorldInfo.special`). Outside them the registry is empty and both hooks
  are no-ops.
- Renaming non-model mobs, players or vanilla mobs that already have their own custom name. Jade already shows
  those.
- Server-side or resource-pack changes.
- Changing the kill tracker's behaviour. Refactoring shared code is fine, as long as kill counts stay the same.

## Anti-goals (a failure even if it works)

- Frame drops: no entity scans per frame or per sound beyond a bounded lookup in a map.
- Wrong names: renaming a subtitle to the wrong mob is worse than leaving it vanilla. When in doubt, leave it.
- Hard dependency on Jade: CubeWheel must load and work with Jade absent.
- Breaking `KillRuleTest`, `RemovalKillTest`, `SandaraKillTest` or the other existing tests.

## Approaches considered

- **A. Resolve on demand.** Each subtitle or Jade query runs `NearbyProbe`-style resolution. Least code, but Jade
  queries every frame, so it needs a memo anyway, and every subtitle repeats the search. Rejected.
- **B. Rolling registry of nearby model mobs (picked by Michael).** About once a second, while in a mana world, scan
  around the player and map each model mob's parts (hitbox, base mob, bone cloud, bones) to one record holding
  the resolved name, base type and position. Jade and subtitles look the name up in that map. Costs more code; a
  newly appeared mob can go up to one scan without a name.
- **C. Server-specific name table** (map each base type to a mana name per world). Rejected: several mana mobs
  share a base type, so it would guess wrong.

## Design

### 1. Capture shapes and sounds first (gates everything else)

The repo has no record of how the Mana Wolf's hitbox is built (an interaction, an invisible wolf, or something
else). Only excerpts of the Sandara viper's shape survive, in code comments. So the first task is an in-game capture
next to a Mana Wolf and a Sandara viper or rattlesnake. Use a temporary debug log or extend `CaptureLog`. Michael
plays; the builder supplies the logging build and reads the result. Record:

- **Shape:** every entity in the model: id, type, vehicle and passengers, invisible/mob flags, custom name,
  text-display text, position. Also what Jade's look actually hits (interaction, invisible mob, bone?).
- **Sounds:** sound event id, its subtitle key (from `SoundManager.getSoundEvent(id).getSubtitle()`), source
  category, position, and the nearest model parts.

Then:

- Build the registry's unit-test fixtures from the captured shapes.
- If the sounds are vanilla `entity.<type>.*` events from the base mob's position, build section 4 as designed.
- If they are custom (`minecraft:` ids that are not entity sounds, or a namespace from the server pack), or a mob
  has no invisible base mob whose type could match, stop the subtitle half and report back with the capture. The
  matching rule changes and needs Michael's input. Sections 2–3 go ahead regardless.

### 2. `ModelMobRegistry` (shared)

- Pure part (no Minecraft imports, unit-tested): given snapshots of entities (id, type id, vehicle id, passenger
  ids, position, invisible/mob/owned flags, custom name, text-display text), group them into model mobs and pick
  each mob's name, using the rules that already exist: `ModelHitbox.isModelHitbox`, the bones-cloud rule
  (`MIN_BONES`), and `NameResolver.pick` over tag candidates. Any rule that moves out of `NearbyProbe` into shared
  code must keep the kill-tracker tests green.
- Minecraft adapter: refreshes once a second (every 20 client ticks), only when `WorldInfo.special`, over entities
  within about 48 blocks of the player. Clears on world change, disconnect, and when leaving a mana world.
- Lookups: `byEntity(int id) -> Optional<ModelMob>` (any part's id maps to the mob), and
  `nearSound(String baseTypeId, Vec3 pos) -> Optional<ModelMob>`: the closest mob whose base type matches,
  within 2 blocks.
- `ModelMob` is a record: bare name, tag health (nullable; parsed from the tag text, refreshed every scan), name-tag
  entity id, base type id (the invisible mob's type; for an interaction hitbox, the invisible mob nearby if any),
  hitbox entity id, a representative position, and part ids.
- Names are always the bare name from `StackName.parse`, never the raw tag line, so subtitles don't change text as
  the mob takes damage.

### 3. Jade plugin (optional dependency)

- `compileOnly` against the Jade API for 26.2 (Jade 26.2.11 is what the Realism instance runs; get it from the
  Modrinth maven or whichever repo publishes the API). Never bundled.
- `fabric.mod.json`: a `"jade"` entrypoint pointing to a plugin class in its own package. Nothing else references
  Jade classes, so CubeWheel loads without Jade. Add `jade` to `suggests`.
- `registerClient`:
  - a ray-trace callback: if the hit entity is any registry part (hitbox, invisible base mob, bone), make the
    tooltip about the mob as a whole: one stable target per mob (the hitbox), whatever part the look lands on;
  - a name override for entities the registry knows, replacing the object name line with the bare name;
  - health: hide Jade's own health line for registry mobs and show the tag health instead, read live from the tag
    entity rather than the once-a-second snapshot when that's cheap. No tag health → no health line.
- A config toggle `jade.manaNames` (default on).

### 4. Subtitles

- Mixin on `SubtitleOverlay.onPlaySound(SoundInstance, WeighedSoundEvents, float)` (or whatever the 26.2 signature
  is) that changes the subtitle component before the overlay stores it.
- Only when in a mana world and the subtitle key is `subtitles.entity.<type>.*`: look up
  `nearSound("minecraft:<type>", soundPos)`. On a match, take the translated subtitle text and replace the vanilla
  entity's display name (`EntityType.getDescription()`) with the mob name. If the vanilla name isn't in the
  translated text, show "<Mob name>: <subtitle>". On no match, leave it alone.
- The overlay merges repeats by component equality, so renamed subtitles must be built the same way every time
  (a literal text) so repeats still stack.
- A config toggle `subtitles.manaNames` (default on).

### 5. Docs and versioning

- README bullets for both features; TESTING.md manual steps (look at a Mana Wolf and a Sandara viper with Jade:
  bare name and the tag's health; turn on subtitles near a Mana Wolf).
- Bump the beta number and config version per the existing convention. The new toggles go through
  `ConfigNormalizer`/`WheelUpgrade` as other added settings have.

## Left to the builder

- Exact Jade API classes and method names (e.g. how the name line is replaced) for 26.2.11. Use its docs and
  sources.
- How to snapshot entities cheaply, and whether `NearbyProbe` is refactored to share the grouping code or the
  registry just calls the same pure helpers.
- Scan radius and interval within the bounds above.

## Testing

- Unit tests for the pure registry grouping, naming and tag-health parsing, with fixtures from the section 1
  capture (plus the Sandara excerpts already in `SandaraKillTest`).
- Unit tests for the subtitle rewrite as a pure function: (translated text, vanilla name, mob name) → text.
- Manual in-game check on ManaCube via TESTING.md; Michael runs it. Never hot-swap the jar while the game is
  running.

## Amendment (2026-11-12, after the capture)

The capture (`2026-11-12-mana-mob-capture-findings.md`) showed that mana mobs have no invisible base mob, and that
their sounds are borrowed vanilla sounds (Mana Wolf = polar bear, Viper = silverfish) coming from the interaction
hitbox. Michael approved this subtitle rule: match a `subtitles.entity.*` sound to the mob whose hitbox it comes
from (within 1.0 block horizontally, −0.5 to +1.5 vertically, closest wins, ties stay vanilla). Each mob name
learns its "voice" from its first sound, which stays vanilla, and afterwards only that voice is renamed. The
vanilla name replaced in the subtitle is the sound's own entity type ("Polar Bear hurts" → "Mana Wolf hurts").
The per-player recycled hitbox slime is a part of whichever mob it sits on, for Jade, but never a base type.
