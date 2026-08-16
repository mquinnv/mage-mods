# Ultra-Realism Client Pack (MC 26.2) — Design

**Date:** 2026-07-05
**Status:** Draft for review
**Repo:** `mage-mods` (the Fabric modpack builder), retargeted from 1.20.1 → 26.2

## Summary

Retarget the `mage-mods` client pack to **Minecraft 26.2** as a **private family "ultra-realism" client pack** that pairs with the on-demand Purpur 26.2 server (`play.mage.net`). It's **client-side only** — shaders, PBR textures, physics, LOD render distance, performance, and QoL — so it enhances how the vanilla-compatible server *looks and feels* without the server needing to know anything. Distributed as a **private `.mrpack`** (shared with family, imported into Prism/Modrinth launcher), **not** published publicly on Modrinth, so we can bundle the owner's paid Physics Mod Pro + Patrix.

## Goals

- One `.mrpack`, MC **26.2**, Fabric — installs the base performance/QoL layer automatically and offers the heavy realism layer as opt-in.
- **Ultra-realism:** shaders (labPBR) + PBR textures + Physics Mod Pro + Distant Horizons + realistic mob animations.
- Connects cleanly to the Purpur 26.2 server (client-side mods only — nothing that adds blocks/items the server lacks).
- Reuse the existing `mage-mods` builder (config-driven, `.mrpack` output), retargeted — don't rebuild it.

## Non-Goals

- **No content mods.** The old 1.20.1 pack's Create ecosystem is dropped: (1) not on 26.2, and (2) it would be broken against a Purpur/vanilla server (server has none of those blocks/items). This pack is cosmetic/QoL/perf only.
- **No public Modrinth listing.** Bundling paid mods (Physics Mod Pro, Patrix) means private distribution only.
- Not a server pack. The server is `mage-server` (separate repo).

## Distribution model

**Private `.mrpack`**, shared directly with family, imported into **Prism Launcher** (or the Modrinth launcher). The pack file references Modrinth mods by hash (auto-downloaded on install) AND bundles local override files (the paid jar + resource pack) directly inside the `.mrpack`. No public upload step. (License note: Physics Mod Pro / Patrix are per-user paid; this shares the owner's copies within the household — the owner's call, normal for family use.)

## Composition (Approach B — one pack, realism opt-in)

### Base — always installed (from Modrinth, by reference)
| Mod | Slug | 26.2 status |
|---|---|---|
| Fabric API | `fabric-api` | release |
| Sodium | `sodium` | **beta** (`mc26.2-0.9.1-beta.3`) |
| Iris (shader loader) | `iris` | release |
| EntityCulling | `entityculling` | release |
| Lithium | `lithium` | release |
| FerriteCore | `ferrite-core` | release |
| Xaero's Minimap | `xaeros-minimap` | release |
| Xaero's World Map | `xaeros-world-map` | release |

(**Indium is dropped** — modern Sodium implements the rendering API natively on 26.2, no Indium needed.)

### Realism — opt-in (heavy bundled files marked `optional` in the `.mrpack` so weak machines can untick at install; shader/PBR are enabled in-game)
| Item | Source | 26.2 status | Notes |
|---|---|---|---|
| **Shaders — ship all 3, pick in-game** | **Photon** (local `~/Downloads/Photon v1.3b.zip`) + **Complementary Reimagined** (Modrinth, release) + **Complementary Unbound** (Modrinth, release) | 26.2 (via Iris) | Photon = owner's original pick; Complementary Reimagined = vanilla-faithful realism; Unbound = stylized/customizable. All labPBR-capable; user selects one in Video Settings. |
| Distant Horizons (LOD render distance) | Modrinth `distanthorizons` | **beta** | Huge immersion win. |
| Fresh Animations (mob animations) | Modrinth `fresh-animations` | **beta** (resource pack) | Needs ETF + EMF. |
| Entity Texture Features (ETF) | Modrinth `entitytexturefeatures` | release | Supports Fresh Animations / emissive. |
| Entity Model Features (EMF) | Modrinth `entity-model-features` | release | Supports Fresh Animations models. |
| Continuity (connected textures) | Modrinth `continuity` | release | Fabric CTM for PBR packs. |
| **Physics Mod Pro** | **local** `~/Downloads/physics-mod-pro-v185b-fabric-mc-26.2.jar` | 26.2 fabric | Bundled override (paid; 139 MB). Ragdolls/debris, client-side, works in MP. |
| **Patrix 128× PBR** (ultra) | **local** `Patrix_26.2_128x_{basic,mobs,addon,items}.zip` | 26.2 | Bundled overrides (~735 MB, modular — enable basic→mobs→addon→items). The high-res PBR default. |
| **Patrix 64× PBR** (lite alt) | **local** `Patrix 26.2 64x basic.zip` + `Patrix_26.2_64x_addon.zip` | 26.2 | Lighter alternative for weaker GPUs (~238 MB). |
| Coherence X (alt pack) | **local** `Coherence X.zip` | — | Optional clean/high-detail alternative to Patrix (pending owner). |

**Size note:** with 128× Patrix + Physics Pro, the full pack is **~1 GB+**. The heavy bundled files ship as `optional` so weaker machines can untick the 128× set (falling back to 64× or vanilla res) at install.

Ambient mods (Effective / Falling Leaves / Particle Rain) have no 26.2 build yet — omitted for now.

## Builder changes (`mage-mods`)

The builder is config-driven (`config/mods-client.json`, `shader-packs.json`, `resource-packs.json`, `pack-info.json`) → `.mrpack` via `scripts/build-packs.js`. Required changes:

1. **Retarget `pack-info.json`** → `minecraft: 26.2`, matching Fabric loader + Fabric API for 26.2; bump pack version; new realism-pack name/description.
2. **New 26.2 client mod list** — replace the 1.20.1 `mods-client.json` with the base + realism mods above, resolved to their **26.2 Modrinth version/hash** (the existing `check-versions`/`update-versions` tooling resolves these). Drop all content mods and Indium.
3. **Bundle local override files** — the builder must include arbitrary local files as `.mrpack` overrides: the Physics Mod Pro jar → `overrides/mods/`, Patrix zip → `overrides/resourcepacks/`. This is the one genuinely new builder capability (today it only references Modrinth). Add an `overrides` config section pointing at local file paths.
4. **Mark heavy realism as `optional`** in the `.mrpack` per-file `env` (client: optional) so the launcher offers a toggle for the big bundled files (Physics Pro, Patrix) and Distant Horizons.
5. **Ship shader + resource-pack selection** so first launch is realism-ready: include the Complementary Reimagined shaderpack (Modrinth) + Patrix in the pack; leave them **installed-but-user-enabled** (shaders + resource pack are enabled in-game, not auto — standard). Optionally pre-write `options.txt`/shader config to pre-select them.
6. **Private build** — produce the `.mrpack` locally; skip the Modrinth upload step (`upload:*`) for this pack.

## Testing / acceptance

- `bun run build:client` (retargeted) produces a `.mrpack` with base mods referenced + the two local files bundled as overrides; validate the manifest (`modrinth.index.json`) hashes/paths.
- **Import into Prism Launcher** → instance installs, base mods download, Physics Pro jar + Patrix present.
- Launch → connects to `play.mage.net` (client-side mods don't break the vanilla-compatible server).
- Enable Complementary Reimagined + Patrix → PBR/labPBR renders (normal/specular). Physics Mod shows ragdolls/debris. Distant Horizons extends render distance. Fresh Animations animates mobs.
- Weak-machine path: at install, unticking the optional heavy files yields a lean perf pack that still connects.

## Open decisions (resolve at review)

- **Patrix + Physics as `optional` toggles** (recommended, lets weak machines skip ~213 MB) vs always-included. Default: optional.
- **Pre-enable shaders/PBR** via a shipped `options.txt`/config (nice first-run) vs leave off by default (safer for low-end first boot). Default: ship them installed but **off**, with a one-line "turn these on" note — avoids a first-launch GPU stall on weak machines.
- Photon: add as an alternative shader when its 26.2 build lands (tracked, not blocking).
