# Ultra-Realism 26.2 Client Pack — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retarget the `mage-mods` client-pack builder from MC 1.20.1 to 26.2 and produce a private "ultra-realism" `.mrpack` that bundles the owner's local paid assets (Physics Mod Pro, Patrix, Photon) alongside Modrinth-referenced base + realism mods.

**Architecture:** The builder (`scripts/build-packs.js`, Bun) reads `config/*.json`, resolves Modrinth versions, downloads jars to hash them, writes `modrinth.index.json`, copies an `overrides/` tree, and zips a `.mrpack`. We (1) make the target MC version config-driven, (2) replace the config lists with the 26.2 mod set, (3) add a "bundle local files into overrides/" capability, (4) build + validate. Verification is build-and-inspect (no unit-test harness exists — do not invent one).

**Tech Stack:** Bun, Node `fs`/`crypto`, axios, Modrinth v2 API, `.mrpack` (Modrinth pack) format, Prism Launcher (import target).

## Global Constraints

- **Target MC version: `26.2`, loader: Fabric.** No content mods, no Indium (modern Sodium needs none on 26.2).
- **Server address is `play.mage.net`** (the old builder hardcodes `minecraft.mage.net` — must change).
- **Private pack** — do NOT add a Modrinth upload step; build locally only.
- **Local assets to bundle as overrides** (exact paths):
  - `~/Downloads/physics-mod-pro-v185b-fabric-mc-26.2.jar` → `overrides/mods/`
  - `~/Downloads/Photon v1.3b.zip` → `overrides/shaderpacks/`
  - `~/Downloads/Patrix_26.2_128x_basic.zip`, `_mobs.zip`, `_addon.zip`, `_items.zip` → `overrides/resourcepacks/`
  - `~/Downloads/Patrix 26.2 64x basic.zip`, `Patrix_26.2_64x_addon.zip` → `overrides/resourcepacks/` (lite alt)
  - `~/Downloads/Coherence X.zip` → `overrides/resourcepacks/` (alt pack)
- **Base mods (Modrinth, auto-resolve to 26.2):** `fabric-api, sodium, iris, entityculling, lithium, ferrite-core, xaeros-minimap, xaeros-world-map`.
- **Realism mods (Modrinth):** `distanthorizons, fresh-animations, entitytexturefeatures, entity-model-features, continuity`.
- **Shaders (Modrinth):** `complementary-reimagined, complementary-unbound` (Photon is the local one above).
- Reality: `.mrpack` `optional` env applies only to Modrinth-referenced files, NOT to `overrides/` (those always extract). So bundled local packs are always included and enabled/selected **in-game** (resource packs + shaders are off until the user picks them; Physics Mod has an in-game toggle). This is expected — the pack is heavy (~1.1 GB) but realism is opt-in *in-game*, not at install.

---

### Task 1: Make the target MC version config-driven (retarget 1.20.1 → 26.2)

**Files:**
- Modify: `scripts/build-packs.js` (`getLatestModVersion` ~L149-177, `getLatestResourcePackVersion` ~L179-207)
- Modify: `config/pack-info.json`

**Interfaces:**
- Produces: version resolution that targets `packInfo.minecraft` instead of the hardcoded string.

- [ ] **Step 1: Retarget `pack-info.json`** to 26.2. Replace its contents:
```json
{
  "name": "Mage Realism",
  "version": "2.0.0",
  "minecraft": "26.2",
  "fabric": "0.17.2",
  "fabricApi": "0.154.0+26.2",
  "author": "michael",
  "description": {
    "client": "Private ultra-realism client pack for the Mage 26.2 server: shaders (Photon/Complementary), PBR (Patrix), Physics Mod Pro, Distant Horizons, and performance/QoL. Connects to play.mage.net.",
    "server": "unused"
  }
}
```
(If Fabric loader 0.17.2 is not current, the implementer resolves the latest 26.2-compatible Fabric loader from https://meta.fabricmc.net/v2/versions/loader and sets it — do not leave a stale value.)

- [ ] **Step 2: Make `getLatestModVersion` use the configured version.** Change L150 from the hardcoded `game_versions=["1.20.1"]` to read `packInfo.minecraft`:
```js
async function getLatestModVersion(projectId) {
  const gv = packInfo.minecraft;
  const url = `${MODRINTH_API}/project/${projectId}/version?game_versions=["${gv}"]&loaders=["fabric"]`;
```
Update the error string on the no-versions branch to `No compatible versions found for Minecraft ${gv} Fabric`.

- [ ] **Step 3: Same for `getLatestResourcePackVersion`** (L180): use `packInfo.minecraft` in the `game_versions` filter; update its error string.

- [ ] **Step 4: Verify resolution hits 26.2.** Run:
```bash
cd ~/Projects/mage-mods
bun -e 'const {execSync}=0;' 2>/dev/null; node -e "0" 2>/dev/null; \
MODRINTH_TOKEN=$(grep -i MODRINTH .env|cut -d= -f2) bun -e '
require("dotenv").config();
const axios=require("axios");
(async()=>{const gv="26.2";const r=await axios.get(`https://api.modrinth.com/v2/project/sodium/version?game_versions=["${gv}"]&loaders=["fabric"]`,{headers:{Authorization:`Bearer ${process.env.MODRINTH_TOKEN}`}});console.log("sodium 26.2 latest:",r.data[0].version_number,r.data[0].id);})();'
```
Expected: prints a `mc26.2-...` Sodium version + a fileId (proves the 26.2 filter works).

- [ ] **Step 5: Commit** `git add scripts/build-packs.js config/pack-info.json && git commit -m "builder: make target MC version config-driven (retarget to 26.2)"`

---

### Task 2: Replace config mod/shader/resource lists with the 26.2 set

**Files:**
- Modify: `config/mods-client.json`, `config/shader-packs.json`, `config/resource-packs.json`

**Interfaces:**
- Consumes: the config-driven resolver from Task 1 (blank `fileId` → auto-resolve to 26.2).
- Produces: the mod/shader lists the builder iterates. **Modrinth accepts a slug in place of a project id**, so `projectId` may be a slug; leave `fileId` empty to auto-resolve the latest 26.2 build.

- [ ] **Step 1: Write `config/mods-client.json`** — base + realism, all `side: client` except Fabric API (`both`). `optional: true` on the realism ones (used in Task 3's env mapping):
```json
{
  "mods": [
    { "name": "Fabric API", "projectId": "fabric-api", "fileId": "", "side": "both", "category": "core" },
    { "name": "Sodium", "projectId": "sodium", "fileId": "", "side": "client", "category": "performance" },
    { "name": "Iris Shaders", "projectId": "iris", "fileId": "", "side": "client", "category": "graphics" },
    { "name": "EntityCulling", "projectId": "entityculling", "fileId": "", "side": "client", "category": "performance" },
    { "name": "Lithium", "projectId": "lithium", "fileId": "", "side": "client", "category": "performance" },
    { "name": "FerriteCore", "projectId": "ferrite-core", "fileId": "", "side": "client", "category": "performance" },
    { "name": "Xaero's Minimap", "projectId": "xaeros-minimap", "fileId": "", "side": "client", "category": "map" },
    { "name": "Xaero's World Map", "projectId": "xaeros-world-map", "fileId": "", "side": "client", "category": "map" },
    { "name": "Distant Horizons", "projectId": "distanthorizons", "fileId": "", "side": "client", "category": "graphics", "optional": true },
    { "name": "Fresh Animations", "projectId": "fresh-animations", "fileId": "", "side": "client", "category": "graphics", "optional": true },
    { "name": "Entity Texture Features", "projectId": "entitytexturefeatures", "fileId": "", "side": "client", "category": "graphics", "optional": true },
    { "name": "Entity Model Features", "projectId": "entity-model-features", "fileId": "", "side": "client", "category": "graphics", "optional": true },
    { "name": "Continuity", "projectId": "continuity", "fileId": "", "side": "client", "category": "graphics", "optional": true }
  ]
}
```
Note: Fresh Animations is a resource pack on Modrinth (loader `minecraft`) — if `getLatestModVersion`'s `loaders=["fabric"]` filter returns empty for it, move it to `resource-packs.json` instead. The implementer verifies which resolves and places it accordingly.

- [ ] **Step 2: Write `config/shader-packs.json`** (Modrinth shaders; Photon comes from local overrides in Task 3):
```json
{
  "shaderPacks": [
    { "name": "Complementary Reimagined", "projectId": "complementary-reimagined", "fileId": "", "optional": true, "side": "client" },
    { "name": "Complementary Unbound", "projectId": "complementary-unbound", "fileId": "", "optional": true, "side": "client" }
  ]
}
```

- [ ] **Step 3: Write `config/resource-packs.json`** empty (Patrix/Coherence are local overrides): `{ "resourcePacks": [] }`

- [ ] **Step 4: Verify the lists resolve.** Run `bun run check-versions` (retargeted) — expect every listed mod/shader to report a found 26.2 version, no "No compatible versions" errors. If any fails, fix the slug or move it per Step 1's note.

- [ ] **Step 5: Commit** `git add config/mods-client.json config/shader-packs.json config/resource-packs.json && git commit -m "config: 26.2 base+realism mod/shader lists"`

---

### Task 3: Bundle local override files + fix server address + optional env

**Files:**
- Create: `config/local-overrides.json`
- Modify: `scripts/build-packs.js` (`createMrpack` ~L381-460; `buildModrinthIndex` env at ~L349-352)

**Interfaces:**
- Consumes: config from Tasks 1-2.
- Produces: an `overrides/` tree containing the local jars/zips; `optional` env for `optional:true` entries.

- [ ] **Step 1: Create `config/local-overrides.json`** mapping local files → override subfolders:
```json
{
  "overrides": [
    { "src": "~/Downloads/physics-mod-pro-v185b-fabric-mc-26.2.jar", "dest": "mods" },
    { "src": "~/Downloads/Photon v1.3b.zip", "dest": "shaderpacks" },
    { "src": "~/Downloads/Patrix_26.2_128x_basic.zip", "dest": "resourcepacks" },
    { "src": "~/Downloads/Patrix_26.2_128x_mobs.zip", "dest": "resourcepacks" },
    { "src": "~/Downloads/Patrix_26.2_128x_addon.zip", "dest": "resourcepacks" },
    { "src": "~/Downloads/Patrix_26.2_128x_items.zip", "dest": "resourcepacks" },
    { "src": "~/Downloads/Patrix 26.2 64x basic.zip", "dest": "resourcepacks" },
    { "src": "~/Downloads/Patrix_26.2_64x_addon.zip", "dest": "resourcepacks" },
    { "src": "~/Downloads/Coherence X.zip", "dest": "resourcepacks" }
  ]
}
```

- [ ] **Step 2: In `createMrpack`, after the existing overrides block (before zipping), copy the local overrides.** Add (expanding `~`):
```js
  // Bundle local override files (paid/private assets that can't come from Modrinth)
  const lo = 'config/local-overrides.json';
  if (packType === 'client' && fs.existsSync(lo)) {
    const { overrides = [] } = JSON.parse(fs.readFileSync(lo, 'utf8'));
    for (const o of overrides) {
      const src = o.src.replace(/^~/, require('os').homedir());
      if (!fs.existsSync(src)) { console.warn(`⚠️  local override missing: ${src}`); continue; }
      const destDir = path.join(buildDir, 'overrides', o.dest);
      fs.mkdirSync(destDir, { recursive: true });
      fs.copyFileSync(src, path.join(destDir, path.basename(src)));
      console.log(`  Bundled local override: ${path.basename(src)} -> overrides/${o.dest}/`);
    }
  }
```

- [ ] **Step 3: Fix the hardcoded server address** in `createMrpack`: replace every `minecraft.mage.net` with `play.mage.net` and "Minecraft Mage Server" → "Mage" in `SERVER_INFO.txt` and the `options.txt` `lastServer:` line. For the hand-built `servers.dat` NBT byte buffer (address length is hardcoded), **regenerate it for `play.mage.net`** (13 chars, length `0x0D`) — or, simpler and less error-prone, **drop the `servers.dat` write entirely** and rely on `SERVER_INFO.txt` + `lastServer` in `options.txt` (players add the server once). Prefer dropping it unless a correct NBT is trivially produced.

- [ ] **Step 4: Add `optional` env support** in `buildModrinthIndex` (L349-352) so `optional:true` mods map to `env.client: "optional"`:
```js
      env: {
        client: mod.optional ? 'optional' : (mod.side === 'client' || mod.side === 'both' ? 'required' : 'unsupported'),
        server: 'unsupported'
      },
```
(Realism mods become launcher-optional; base stays required. Local overrides are unaffected — always bundled, per Global Constraints.)

- [ ] **Step 5: Commit** `git add config/local-overrides.json scripts/build-packs.js && git commit -m "builder: bundle local override assets, play.mage.net address, optional env"`

---

### Task 4: Build the pack and validate the artifact

**Files:** none (build + inspect)

- [ ] **Step 1: Build.** `cd ~/Projects/mage-mods && bun run build:client`. Expect: base+realism mods resolve+download+hash, local overrides bundled, a `.mrpack` written under `build/`.

- [ ] **Step 2: Validate the manifest.** Unzip the `.mrpack` and inspect `modrinth.index.json`:
```bash
cd ~/Projects/mage-mods
f=$(ls build/*client*.mrpack | head -1); mkdir -p /tmp/mp && (cd /tmp/mp && rm -rf * && unzip -q "$OLDPWD/$f")
node -e '
const j=require("/tmp/mp/modrinth.index.json");
console.log("mc:", j.dependencies.minecraft, "loader:", j.dependencies["fabric-loader"]);
console.log("files:", j.files.length);
const opt=j.files.filter(f=>f.env.client==="optional").map(f=>f.path);
console.log("optional:", opt);
const missing=j.files.filter(f=>!f.downloads||!f.downloads.length).map(f=>f.path);
console.log("no-download (should be empty):", missing);
'
echo "--- bundled overrides ---"; find /tmp/mp/overrides -maxdepth 2 -type f | sed "s#/tmp/mp/##"
```
Expected: `mc: 26.2`; every file has a download URL (no-download list empty); realism mods show `optional`; `overrides/mods/physics-mod-pro-...jar`, `overrides/shaderpacks/Photon v1.3b.zip`, and the Patrix/Coherence zips under `overrides/resourcepacks/` are present.

- [ ] **Step 3: Sanity-check size.** `ls -lh build/*client*.mrpack` — expect ~1.1 GB (the bundled 128× Patrix + Physics Pro dominate). If it's a few MB, the overrides didn't bundle — fix Task 3.

- [ ] **Step 4: Commit** any doc note. `git add -A && git commit -m "build: produce 26.2 realism client .mrpack (validated manifest+overrides)" --allow-empty`

---

### Task 5: Owner acceptance in Prism (manual — needs a real client)

**Files:** none

- [ ] **Step 1:** Owner imports `build/*client*.mrpack` into Prism Launcher (already installed).
- [ ] **Step 2:** Launch → the instance downloads base mods; confirm the Physics Pro jar is in the instance `mods/`, and Patrix/Photon/Coherence in `resourcepacks/`/`shaderpacks/`.
- [ ] **Step 3:** Connect to `play.mage.net` — joins the Purpur server (client-side mods don't break it).
- [ ] **Step 4:** In Video Settings enable a shader (Photon / Complementary) and enable Patrix (128× or 64×) in Resource Packs → confirm PBR + shaders render; Physics Mod shows ragdolls/debris; Distant Horizons extends render distance.
- [ ] **Step 5:** Weak-machine check: with shaders off + Patrix off, the pack still runs and connects (base perf layer).

---

## Self-Review

**Spec coverage:** 26.2 retarget (T1) ✅ · client-side-only base+realism lists, content mods + Indium dropped (T2) ✅ · Photon local + Complementary ×2 (T2/T3) ✅ · Physics Pro + Patrix 128×/64× + Coherence bundled (T3) ✅ · private/no-upload (build:client only, no upload step) ✅ · Distant Horizons (T2) ✅ · one pack, realism opt-in (env optional for Modrinth realism; in-game for local packs — reconciled in Global Constraints) ✅ · Prism acceptance (T5) ✅ · play.mage.net address fix (T3) ✅.

**Placeholder scan:** No TBD/TODO. Fabric-loader version has a concrete value with a fallback instruction to resolve the current one (not a placeholder — an explicit resolve step). Fresh Animations placement has an explicit verify-and-move instruction.

**Consistency:** `packInfo.minecraft` is the single source of the target version (T1) and is consumed by both resolvers and `buildModrinthIndex.dependencies` (already reads `packInfo.minecraft`/`packInfo.fabric`). `optional` flag set in config (T2) and consumed by the env map (T3). Local-override `src` paths match the Global Constraints file list exactly.
