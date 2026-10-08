# CLAUDE.md

Guidance for Claude Code (claude.ai/code) working in this repository.
`AGENTS.md` is a symlink to this file — edit this one.

## Project

`mage-mods` builds and publishes the Fabric **client modpack** ("Mage Realism")
for the private Minecraft server. `config/pack-info.json` is the source of truth
for pack name, version, and the targeted Minecraft/Fabric versions.

> **The Minecraft server lives in the separate `mage-server` repo, not here.**
> This repo still carries server-pack and deploy machinery (`build:server`,
> `config/mods-server.json`, `scripts/deploy-apex*`) left over from when it
> handled both. That machinery is **dead** — see Deployment. It should probably
> be deleted rather than maintained.

## Layout

- `config/*.json` — the declarative pack definition. `mods-client.json` /
  `mods-server.json` list mods by Modrinth `projectId` plus a pinned `fileId`;
  alongside them are `shader-packs.json`, `resource-packs.json`,
  `pack-info.json`, `upload-config.json` (Modrinth project ids), and
  `local-overrides.json` (paid/manual assets sourced from `~/Downloads`, which
  are not redistributable and are bundled only into local builds).
- `scripts/` — build, publish, and deploy tooling; plain Node/Bun scripts.
- `src/` — `client/`, `server/`, and `shared/config/` override trees baked into
  the packs, plus `client-mods/hammerharvest`, a small mod kept in-repo.
- `build/` — generated `.mrpack` and Prism zip output. Gitignored.

## Commands

Every script that talks to an external service is wrapped in `op run` in
`package.json`. Run them via the package scripts, not by invoking
`bun scripts/*.js` directly, or the API token will be missing.

```bash
bun run build            # build all packs into build/
bun run build:client     # client pack only
bun run build:server     # server pack only
bun run upload           # publish versions to Modrinth
bun run deploy           # DEAD - targets retired Apex Hosting. See Deployment.
bun run check-versions   # ask Modrinth for newer mod versions
bun run update-versions  # bump pinned fileIds in config/mods-*.json
bun run clean            # remove built .mrpack files
bun run install:liz      # push the newest CubeWheel jar to Liz's PC (see scripts/install-cubewheel-liz.sh)
```

## Deployment

**Nothing in this repo deploys the server. Do not run the `deploy` scripts.**

`scripts/deploy-apex.sh`, `deploy-apex.js` and `deploy-apex-smart.js` push over
plain FTP to Apex Hosting. **The server is no longer hosted on Apex** (confirmed
by Michael, 2026-08-19), and those scripts have not been touched since
2025-06-28. `ftp_deploy.txt` is a committed leftover of that dead path -- it is
generated and deleted by `deploy-apex.sh` at run time, so nothing reads the
committed copy.

The server now lives in the `mage-server` repo, with this topology (per that
repo's own README):

    vanilla client -> mc.mage.net -> Oracle A1 doorman (Velocity + PicoLimbo
    limbo, holds you at "booting...") -> aws.mage.net -> EC2 on-demand Purpur

DNS is Cloudflare on `mage.net`. Credentials there are narrowly scoped by
design. **Record nothing about that setup here** -- `mage-server` owns it.

Worth noting how this got stale: every one of those deploy scripts exists and
runs, and every path inside them resolves. What moved was the destination, which
nothing inside this repo can reveal. In-repo verification cannot catch it.

### CubeWheel on Liz's PC

`bun run install:liz` (`scripts/install-cubewheel-liz.sh`) is the standard
route: upload the jar to a private S3 bucket, presign it, and have SSM
(`AWS-RunPowerShellScript`) download, hash-check and swap it into her mods
folder. It refuses to touch anything while `javaw` is running. The old
base64-chunked SSM upload and the LAN `http.server` route are retired (the
auto-mode classifier blocks Claude from starting a local listener anyway).

## Conventions

- **Mods are pinned by `fileId`** (sometimes by filename too) deliberately —
  several pins exist to avoid broken betas. Read the commit that introduced a
  pin before loosening it.
- Prefer the Modrinth API for mod research; it returns dependency and version
  data directly. No confirmation is needed to call it.

## Credentials

There is **no `.env` file** in this repo. `MODRINTH_TOKEN` is injected at run
time from a 1Password Environment by the `op run` prefix in `package.json`;
`.env.example` documents the variable name only. Account and environment
details live in mem0 (`user_id=michael`, `app_id=mage-mods`).

_last verified: 2026-08-19_

## Known rough edges

- The `turbo:*` package scripts shell out to `turbo run …`, but there is no
  `turbo.json` in the repo. Treat them as vestigial; use the direct scripts.
