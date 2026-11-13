# Mana mob capture findings (Task 1b)

_2026-11-12. Capture file `config/cubewheel-captures/2026-11-13.jsonl` in the Realism ("MacOS Optimized")
instance (the date is UTC, so the file is dated the 13th), recorded by the Task 1a build. Michael looked at,
stood near, attacked and killed one **Mana Wolf** in Wolfhaven, then one **Viper** in Sandara._

Contents: 53 `model_shape` lines (31 Mana Wolf, 22 Viper), 229 `sound` lines, plus 62 chat, 96 bossbar, 20
local, 7 sidebar, 12 actionbar and 2 world lines. Both worlds were `special: true`. There is one Mana Wolf and two
Vipers in the capture (a second Viper, interaction #143291143, was ~7 blocks away and was never looked at).
The capture has no rattlesnake.

## The headline

1. **Neither mob has an invisible base mob.** Both models have the same shape: a `minecraft:interaction` hitbox
   riding a cloud, a bones cloud carrying `item_display` bones, and a `text_display` tag on a third cloud. All
   three clouds sit at the same x/z. No invisible wolf, polar bear or silverfish exists anywhere near either model.
2. **The only invisible Mob near either model is one recycled slime that does not belong to any model.** Slime
   #82124 rides its own cloud #82121. It had the same id in Wolfhaven and Sandara, so it belongs to the
   player's session, not to a mob (the recycled hitbox `RecycledHitboxTest` describes). The server keeps it 0.3 to
   1.9 blocks from whichever model the player is fighting, roughly between the player and the model. Looks hit
   it often: 13 of 31 Mana Wolf lines and 8 of 22 Viper lines.
3. **Sounds are vanilla entity sounds of a type that does not appear in the model.** The Mana Wolf plays
   `entity.polar_bear.*` and the Viper plays `entity.silverfish.*`. Each sound is played at the interaction
   hitbox's position.

So, per the gate's own test, **Task 5 is BLOCKED** (see below). A position-based rule would work.

## Shape, per mob

Ids are from the capture. Offsets are relative to the interaction's position. In all 53 lines, the interaction,
its cloud, the bones cloud, the tag cloud and the tag have **exactly** the same x and z (max offset 0.0).

### Mana Wolf (Wolfhaven)

| Part | Id | Type | Vehicle | Passengers | dy | Notes |
|---|---|---|---|---|---|---|
| Hitbox | 143133269 | `minecraft:interaction` | 143133268 | none | 0 | `invisible:false`, `mob:false` |
| Hitbox cloud | 143133268 | `minecraft:area_effect_cloud` | none | 143133269, 143133270 | −0.5 | |
| Extra display | 143133270 | `minecraft:item_display` | 143133268 | none | 0 | the only `invisible:false` display |
| Bones cloud | 143133233 | `minecraft:area_effect_cloud` | none | 32 `item_display` (143134237–143134268) | +0.1875 | |
| Bones | 143134237… | `minecraft:item_display` | 143133233 | none | +0.6875 | 32 of them |
| Tag cloud | 143134269 | `minecraft:area_effect_cloud` | none | 143134270 | +1.0 | |
| Tag | 143134270 | `minecraft:text_display` | 143134269 | none | +1.5 | text `Mana Wolf 60⺛` |
| Recycled slime | 82124 | `minecraft:slime` | 82121 | none | varies | `invisible:true`, `mob:true`; **not part of the model** |
| Slime cloud | 82121 | `minecraft:area_effect_cloud` | none | 82124 | | |

- **What a look hits:** the interaction #143133269 (18 lines) or the recycled slime #82124 (13 lines). The look
  never hit a bone, a cloud or the tag.
- **Invisible base mob:** none. 0 invisible mobs belong to the model. The only invisible Mob in range is the
  slime, type `minecraft:slime`.
- **Count:** 1 interaction, 4 clouds (3 model + the slime's), 33 `item_display` (32 bones + 1 on the hitbox cloud),
  1 `text_display`, plus the slime. 40 entities in total, 41 while a "1.3x" armor stand is there.

### Viper (Sandara)

| Part | Id | Type | Vehicle | Passengers | dy | Notes |
|---|---|---|---|---|---|---|
| Hitbox | 143289204 | `minecraft:interaction` | 143289203 | none | 0 | |
| Hitbox cloud | 143289203 | `minecraft:area_effect_cloud` | none | 143289204, 143289205 | −0.5 | |
| Extra display | 143289205 | `minecraft:item_display` | 143289203 | none | 0 | `invisible:false` |
| Bones cloud | 143289185 | `minecraft:area_effect_cloud` | none | 15 `item_display` (143293495–143293509) | +0.5 | |
| Tag cloud | 143293510 | `minecraft:area_effect_cloud` | none | 143293511 | +1.6 | |
| Tag | 143293511 | `minecraft:text_display` | 143293510 | none | +2.1 | text `Viper 75⺛` |
| Recycled slime | 82124 / cloud 82121 | as above | | | | same id as in Wolfhaven |

- **What a look hits:** the interaction #143289204 (14 lines) or the slime #82124 (8 lines).
- **Invisible base mob:** none, the same as the Mana Wolf.
- This matches the Sandara shape in `SandaraKillTest`/`ModelHitbox`'s comment from 2026-10-01 ("an interaction
  riding its own cloud plus invisible slimes"). The "invisible slimes" there were most likely this same recycled
  slime, not a part of the viper.

### Tag text format

The tag's text is verbatim `<Name> <health>⺛` (`⺛` is U+2E9B, bytes `e2 ba 9b`), with one space and no
formatting codes. It is one line, and the health is an integer in every sample. Values seen:

- `Mana Wolf 60⺛` (full), `Mana Wolf 30⺛`, `27⺛`, `25⺛`, `22⺛`, `20⺛`.
- `Viper 75⺛` (full), `Viper 30⺛`.

`StackName.HEALTH` already strips this (the `\s\d[\d,]*…[^\p{L}\p{N}\s\[\]()]{1,3}` branch). `TagHealth.parse`
should read `60` and `75` from these lines.

### Before and after "activation"

- **Shape:** the shape stays the same. Before the first hit, the model sits still at its spawn position (the Mana
  Wolf exactly at block centre −420.5, 101.0, −186.5; the Viper at −233.16, 56.0, 8.84). From the first hit on it
  moves. The parts, ids, vehicle links and tag format do not change.
- **Tag:** only the health number changes, and only after hits.
- **Multiplier hologram:** after hits, an extra `minecraft:armor_stand` with `customName` `1.3x` (a new id each
  time: 143221667, 143229705, 143236248, 143261940 for the wolf; 143447164 for the viper) floats 1–2.5 blocks away.
  It is `invisible:true`, `mob:false` and rides nothing. `NameResolver.isMultiplier` already rejects it.
- **Recycled slime:** it appears near the model before the first hit too (the first look at each mob already shows
  it). It also leaves range at times while the mob is alive. Its presence does not mark activation.
- **Sounds:** the Mana Wolf made no sound in the ~15 s before the first hit. The Viper played
  `silverfish.ambient` before it was hit (and so did the second Viper that was never touched).

## Sounds

In all 229 sound lines, every sound with an id under `minecraft:entity.` (other than the player's own
`entity.player.*` and `entity.experience_orb.pickup`, and one `entity.camel.ambient` from a real camel) came from
a model mob:

| Mob | Sound id | Subtitle key | Source | Count | Where |
|---|---|---|---|---|---|
| Mana Wolf | `minecraft:entity.polar_bear.hurt` | `subtitles.entity.polar_bear.hurt` | `master` | 6 | at the interaction's x/z and y (0.19–0.21 from the bones cloud, which sits 0.1875 above the interaction) |
| Mana Wolf | `minecraft:entity.polar_bear.death` | `subtitles.entity.polar_bear.death` | `master` | 1 | same x/z, **1.0 above** the interaction |
| Viper | `minecraft:entity.silverfish.ambient` | `subtitles.entity.silverfish.ambient` | `hostile` | 4 | 0.09–0.14 from an interaction (2 from #143289204, 2 from the other Viper #143291143) |
| Viper | `minecraft:entity.silverfish.hurt` | `subtitles.entity.silverfish.hurt` | `hostile` | 1 | 0.09 from the interaction |
| Viper | `minecraft:entity.silverfish.death` | `subtitles.entity.silverfish.death` | `hostile` | 1 | 0.31 from the interaction |
| Viper | `minecraft:entity.silverfish.death` | `subtitles.entity.silverfish.death` | `master` | 1 | same x/z, 1.0 above (1.05 from the interaction) |

- Sound positions are quantised to 1/8 block, as sound packets are, so ~0.1 of error is normal.
- There is no `slime` sound at all, so the recycled slime is silent. No sound came from a bone or the tag position.
- The Mana Wolf's sounds are all `master`. The Viper's are `hostile`, except a duplicate `master` death sound 1
  block higher. Source is therefore not a reliable filter.
- The player's own sounds (`entity.player.hurt`, `attack.*`) come from the player's position. During melee that
  is 0.6–2.4 blocks from the model, which is close enough to be confused with the mob. A position-only rule
  **must** skip `subtitles.entity.player.*`.

Verbatim examples (nearby lists trimmed to the model-level entities, bones left out):

```json
{"t":1794531600905,"kind":"sound","id":"minecraft:entity.polar_bear.hurt","subtitleKey":"subtitles.entity.polar_bear.hurt","source":"master","x":-420.5,"y":101.0,"z":-186.5,"nearby":[{"entity":143133233,"type":"minecraft:area_effect_cloud","passengers":["…32 bones"],"distance":0.19}, "…"]}
{"t":1794531658548,"kind":"sound","id":"minecraft:entity.silverfish.hurt","subtitleKey":"subtitles.entity.silverfish.hurt","source":"hostile","x":-233.125,"y":56.0,"z":8.75,"nearby":[{"entity":143289204,"type":"minecraft:interaction","vehicle":143289203,"distance":0.09},{"entity":143293511,"type":"minecraft:text_display","text":"Viper 75⺛","vehicle":143293510,"distance":2.1},{"entity":82124,"type":"minecraft:slime","vehicle":82121,"distance":0.77}, "…"]}
{"t":1794531669306,"kind":"sound","id":"minecraft:entity.silverfish.death","subtitleKey":"subtitles.entity.silverfish.death","source":"master","x":-234.5,"y":57.0,"z":22.25,"nearby":[{"entity":143289204,"type":"minecraft:interaction","vehicle":143289203,"distance":1.05},{"entity":82124,"type":"minecraft:slime","vehicle":82121,"distance":0.57}, "…"]}
```

Note that the capture's `nearby` list stops at 24 entities. For the Mana Wolf the 32 bones fill it, so the
interaction itself is missing from those lines. Its position follows from the bones cloud (same x/z, 0.1875
higher).

## Task 5 status: BLOCKED

Of the gate's two checks:

1. **The sounds are vanilla entity subtitles: yes.** `subtitles.entity.polar_bear.*` and
   `subtitles.entity.silverfish.*` exist in vanilla.
2. **Each mob has an invisible base mob whose type matches them: no, for both mobs.** Neither model has any
   invisible base mob. The only invisible Mob near them is the per-player recycled slime (`minecraft:slime`). It
   matches neither `polar_bear` nor `silverfish`, plays no sounds, and moves between mobs. Under the planned rule,
   `nearSound("minecraft:polar_bear", pos)` can never match, and a slime-derived `baseType` would be wrong for
   both mobs.

The rule this capture does support, for Michael to approve:

- **Match by position to the hitbox, ignoring the sound's type.** The sound's subtitle key starts with
  `subtitles.entity.`, its type is not `player` (and not `experience_orb`, `item` or another non-mob), and its x/z
  is within **0.5 blocks horizontally** and **−0.5…+1.5 vertically** of a registered model mob's interaction
  hitbox. All 14 model sounds observed fall at ≤ 0.31 horizontally, with dy 0 or +1.0. Under this rule, the
  closest mob wins. If the second closest is within 0.25 of the same distance, the subtitle stays vanilla (the
  existing ambiguity rule). `nearSound` then takes no base type, or a mob's learned "voice" type.
- **Optional tightening ("learned voice"):** the registry records the first entity-sound type heard at a mob's
  hitbox (`polar_bear` for Mana Wolf, `silverfish` for Viper) and, once known, matches only that type. That stops
  a real vanilla mob standing inside a model from being renamed. It costs a little state per mob name and leaves
  the first sound vanilla.
- **Do not filter on source:** the model's sounds arrive both as `master` and as `hostile`.
- The rename step still works as designed. Its vanilla name is the sound's own type (`Polar Bear hurts` →
  `Mana Wolf hurts`, `Silverfish hisses` → `Viper hisses`), taken from `EntityType.byString(<type from key>)`, not
  from the model's `baseType`.

Tasks 2–4 and 6 do not depend on this.

## Implications for Task 2

The plan's grouping rules assumed invisible base mobs and "sibling hitboxes". The capture shows neither. Concrete
changes:

1. **Do not anchor a mob on the recycled slime, and never take `baseType` from it.**
   `ModelHitbox.isModelHitbox` accepts slime #82124 (invisible, unowned Mob, model parts within 3 blocks). Under
   the plan's rules it becomes an anchor, joins whichever model's bones cloud is within 3 blocks, and sets
   `baseType = minecraft:slime` for both mobs. Anchor only on interactions riding a cloud. Attach an invisible
   unowned Mob that rides a cloud carrying nothing else to the **nearest** model (interaction within
   `NameResolver.RANGE`; leave it out when two are within 0.25 of each other). Then `byEntity(82124)` resolves to
   that mob for Jade, because a look hits it 21 of 53 times. It does not create a mob of its own, and it is not a
   base type. Keep the kill tracker's `ModelHitbox` unchanged; this is a registry-only distinction.
2. **`baseType` is null for both captured mobs.** The fixture tests should assert `baseType == null`, not a
   type, for `mana_wolf.json` and `viper.json`. In `sandaraViperSiblingHitboxesAreOneMob`, the "sibling" is the
   recycled slime, so the test becomes "the slime attaches to the Viper mob and is in its `parts`, but is not its
   `baseType`".
3. **Group by co-located clouds, not just by distance.** The interaction's cloud, the bones cloud and the tag's
   cloud share **exactly** the same x/z in all 53 lines (and move together). They are not linked by riding. The
   tag is not a passenger of the hitbox or its cloud, so `NameResolver` sees it as `TAG`, 1.5 blocks (wolf) or 2.1
   blocks (viper) above the interaction. Recommended: a model = the clouds whose x/z equal the interaction's
   (|dx|,|dz| < 0.01). The bones cloud is the one with ≥ `MIN_BONES` `item_display` passengers. The tag is the
   `text_display` riding a co-located cloud. Fall back to `NameResolver.pick` (nearest within 3) only when no
   co-located tag exists. That keeps two Vipers 2–3 blocks apart from swapping names.
4. **`hitbox` = the interaction** (#143133269 wolf, #143289204 viper). The fixture's first line per mob hits the
   interaction and the second hits the slime. Both must resolve to the same `ModelMob` with that `hitbox`.
5. **Parts to include:** the interaction, its cloud and the extra `item_display` on it, the bones cloud and all its
   bones, the tag cloud and the tag. The multiplier armor stand (`1.3x`, unattached) is **not** a part. The
   recycled slime and its cloud are included per point 1.
6. **Wolf bones = 32, Viper bones = 15.** The hitbox cloud carries exactly 1 `item_display`, so `MIN_BONES = 2`
   still tells the two clouds apart.

## Fixtures

`src/client-mods/cubewheel/src/test/resources/manamobs/`. Each file is a **JSON array** of two `model_shape`
objects, copied verbatim from the capture: `{"t", "kind", "hit", "hitType", "parts": [...]}`. Each part uses the
capture's keys: `entity`, `type`, `vehicle` (absent when null), `passengers`, `invisible`, `mob`, `customName` and
`text` (each absent when null), `x`, `y`, `z`. The loader must map `entity`→`id` and treat missing keys as null.
`owned` is not captured; use `false`.

| File | Line 1 | Line 2 |
|---|---|---|
| `mana_wolf.json` | t 1794531585594: before the first hit, look hits the interaction #143133269, slime in range, tag `Mana Wolf 60⺛`, 40 parts | t 1794531603388: after hits, look hits the slime #82124, tag `Mana Wolf 27⺛`, a `1.3x` armor stand #143229705, 41 parts |
| `viper.json` | t 1794531645141: before the first hit, look hits the interaction #143289204, slime in range, tag `Viper 75⺛`, 23 parts | t 1794531660192: after hits, look hits the slime #82124, tag `Viper 30⺛`, 23 parts |

**Trimmed:** only one entity per line was removed, an invisible `minecraft:armor_stand` that rides the player
(#143123269 in Wolfhaven, #143285151 in Sandara; its vehicle #143067859 is the player, whom capture leaves out).
It is a player cosmetic that follows the player everywhere. Every model part, the recycled slime and its cloud, and
the multiplier armor stand are kept.

## Side observation (not this feature): a Mana Wolf double count

The kill tracker counted the one Mana Wolf **twice** toward "Slay Monsters in Wolfhaven". The first count was at
t 1794531605505, "removal, method b … local hit", named `Mana Wolf 60⺛`. It came when the recycled slime #82124
left range or was removed while the wolf was still alive (its tag read 25⺛ 40 ms later). The second count was at
t 1794531613759, the real death, confirmed by the loot `+3 Mana | +2 Mana Wolf Pelt`. The first count most
likely happened because the slime was not yet known as "recycled" in that session. Its first removal's detail
lacks the "recycled hitbox" marker that the second one has, so it took the path that needs no loot line. This belongs with `RecycledHitboxTest` / `MorendDoubleCountTest`, not with this plan.
