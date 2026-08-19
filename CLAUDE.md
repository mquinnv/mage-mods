# CLAUDE.md

Guidance for Claude Code (claude.ai/code) working in this repository.
`AGENTS.md` is a symlink to this file — edit this one.

## Project

`mage-mods` builds and publishes Fabric **modpacks** for the private Minecraft
server `play.mage.net`: a client pack ("Mage Realism") and a matching server
pack. `config/pack-info.json` is the source of truth for pack name, version,
and the targeted Minecraft/Fabric versions.

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

Every script that talks to Modrinth or Apex Hosting is wrapped in `op run` in
`package.json`. Run them via the package scripts, not by invoking
`bun scripts/*.js` directly, or the API token will be missing.

```bash
bun run build            # build all packs into build/
bun run build:client     # client pack only
bun run build:server     # server pack only
bun run upload           # publish versions to Modrinth
bun run deploy           # deploy the server pack to Apex Hosting
bun run check-versions   # ask Modrinth for newer mod versions
bun run update-versions  # bump pinned fileIds in config/mods-*.json
bun run clean            # remove built .mrpack files
```

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
