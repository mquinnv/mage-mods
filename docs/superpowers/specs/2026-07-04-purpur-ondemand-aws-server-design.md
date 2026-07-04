# On-Demand Purpur 26.2 Server — Design

**Date:** 2026-07-04
**Status:** Draft for review
**Author:** Michael + Claude
**Target repo:** new repo `mage-server` (this doc moves there; see Repository)

## Summary

Stand up a **Minecraft 26.2 Purpur server** for family/friends that **only costs money while it is running**. Players connect with **vanilla clients** (no mods). A player is held on a "booting…" screen while the real server cold-starts (~90s), then **seamlessly transferred** onto it — no manual reconnect. The server **shuts itself down after 10 minutes with no players**.

The backend compute lives in **AWS** (`us-east-1`, EC2 Graviton on-demand, Terraform). A tiny always-on **"doorman"** runs on an **Oracle Cloud Always-Free** ARM VM and does the hold-and-handoff. DNS stays on the owner's **Cloudflare** (`mage.net`). Admin layer — **LuckPerms** + **EssentialsX** — is re-applied on the new platform.

This replaces a paid always-on managed host (Apex Hosting) that billed 24/7 for a few hours/day of play.

## Goals

- Pay only for compute actually used — target **~$4/month** at ~60 hrs/month of play; the doorman is **$0** (Oracle free tier).
- **Vanilla clients connect** — zero client-side install.
- **Seamless "just connect" UX**: hold the player during the ~90s cold boot, then transfer them onto the live server with no manual reconnect.
- **Wake only on a real login from a known player** — never on a status ping (cost safety).
- Automatic idle shutdown after 10 min empty.
- Persistent world + automatic backups; no data loss across sleep/wake or instance replacement.
- Low latency for US East Coast players (~5–20 ms) — after handoff, traffic is client↔AWS direct.
- Backend infra reproducible in Terraform.

## Non-Goals

- **Running the existing Create-based mage modpack.** Create/most content mods have no 26.2 build, and Purpur can't run Fabric/Forge mods anyway. The Fabric 1.20.1 tooling stays in `mage-mods`, untouched.
- Public/large server. Small trusted group; **whitelist on**.
- HA/multi-region. Single instance, single AZ.

## Platform: Purpur 26.2

A performance-tuned fork of Paper (Spigot/Bukkit lineage). Plugins run **server-side only → vanilla clients connect.** This is the hard requirement that rules out Fabric/NeoForge content modding, and it makes the Fabric-vs-NeoForge question moot (Purpur is neither).

Availability verified 2026-07-04 (Purpur API + Modrinth): Purpur has a `26.2` build; LuckPerms, EssentialsX, BlueMap, Spark all ship 26.2 Paper/Bukkit builds.

### Plugin stack (server-side only)

| Plugin | Purpose | Required |
|---|---|---|
| LuckPerms | Permissions / groups / prefixes | ✅ |
| EssentialsX | Homes, warps, tpa, kits, spawn, /msg — "essentials" | ✅ |
| EssentialsXChat | Chat formatting with LuckPerms prefixes | ✅ |
| Vault | Bridge EssentialsX ↔ LuckPerms | ✅ |
| BlueMap | Live web map | ✅ |
| Spark | Performance profiler | ✅ |
| CoreProtect | Block logging / grief rollback (insurance) | Default-yes |
| GriefPrevention | Land claims | Optional — off for now (family trust); one-line add later |

Exact build versions pinned during implementation.

## Architecture

```
                Cloudflare DNS (mage.net)
   mc.mage.net ─────► Oracle doorman (reserved IP, stable)
   aws.mage.net ────► EC2 backend (DDNS: updated on each boot)

  ┌────────┐  login   ┌──────────────────────┐  ec2:StartInstances  ┌───────────────┐
  │ vanilla│─────────►│ Doorman (Oracle A1,   │─────────────────────►│ EC2 t4g.medium│
  │ client │          │ Ashburn, always-free) │                      │ us-east-1     │
  └────────┘          │ • status ping → 😴    │                      │ Purpur 26.2   │
       │              │ • login (whitelisted) │   poll aws.mage.net   │ (itzg docker) │
       │              │   → limbo + keepalive │◄────status ping───────│ EBS world vol │
       │              │   → start EC2         │                      │ mc-backup→S3  │
       │              │   → Transfer packet   │                      └───────────────┘
       │              └──────────┬────────────┘                              │
       │   Transfer: "connect to aws.mage.net"                               │
       └────────────────────────────────────────────────────────────────────┘
                    direct client ↔ AWS after handoff (doorman steps out)
```

### Wake + handoff flow

1. Player hits Connect on `mc.mage.net` → reaches the **doorman**.
2. **Status ping** (server-list) → doorman replies with a "😴 Sleeping — join to wake" MOTD. **No boot.**
3. **Login attempt** → doorman checks the username against the **whitelist**. Unknown → reject. Known →
4. Doorman drops the player into a **limbo** state (void world, "🔮 Booting…" message/bossbar) and sends periodic **KeepAlive** packets (defeats the client's ~30s timeout, holds for 2+ min).
5. Doorman calls **`ec2:StartInstances`** (scoped IAM key).
6. EC2 boots → boot script updates **`aws.mage.net` → new public IP** via Cloudflare API (DDNS, no paid Elastic IP) → `itzg/minecraft-server` starts Purpur.
7. Doorman polls `aws.mage.net` with MC status pings until Purpur reports online.
8. Doorman sends the **Transfer packet** → client opens a **fresh direct connection to `aws.mage.net`** → lands in the real world. **Doorman is now out of the path.**
9. All game traffic is **client ↔ AWS direct** (lowest latency; doorman's location/bandwidth irrelevant post-handoff).

### Idle shutdown

On-box **watchdog** polls player count via RCON. After **10 min with zero players**: flush world → final backup to S3 → graceful `docker stop` → instance self-stops (`ec2:StopInstances` via instance role). **Manual start (indirect), automatic stop.**

### Doorman implementation — SPIKE (highest-risk item)

The doorman must: spoof status when asleep, whitelist-gate login, hold via limbo+keepalive, call AWS, health-check the backend, then issue a **Transfer packet**. The Transfer packet is post-1.20.5 and newish. Candidate implementations, to be validated first in the plan:

- **Gate** (Go, lightweight) — check current Transfer support.
- **Velocity + LimboAPI** + a small orchestration plugin — Velocity supports transfers; heavier (Java).
- **Custom minimal proxy** — most control, most work.

The plan's **task 1 is a spike**: confirm the Transfer packet works against a vanilla 26.2 client and pick the implementation. **Graceful fallback if Transfer misbehaves:** doorman kicks with "✅ Ready — reconnect to `aws.mage.net`" (degrades to a manual reconnect; never broken).

### Backend runtime

- **EC2 `t4g.medium`** (2 vCPU ARM, 4 GB) in `us-east-1`, on-demand. Resize to `t4g.large`/`c7g` if TPS suffers.
- **`itzg/minecraft-server`** Docker (`TYPE=PURPUR`, `VERSION=26.2`) — declarative Purpur + plugin download, RCON for the watchdog.
- **World** on a dedicated **EBS gp3** volume (20 GB), decoupled from instance lifecycle.
- **Backups** via **`itzg/mc-backup`** → **S3** on interval + on shutdown; lifecycle expires backups > 14 days.

### DNS (Cloudflare)

- `mc.mage.net` → doorman's **Oracle reserved public IP** (stable, free while attached) — the address the family uses.
- `aws.mage.net` → EC2 public IP, **DDNS-updated on each boot** via a scoped Cloudflare API token. Doubles as the direct/fallback connect address.
- **Cleanup:** delete the stale apex `mage.net` A (`216.27.81.8`) and old `mc.mage.net` A (`104.131.92.54`) — both dangling at IPs no longer owned (mild takeover risk).

### Discord `/start` — fallback trigger

Keep a dead-simple **Discord `/start`** command (bot → `ec2:StartInstances`) as a resilient backup for when the doorman/Oracle is unavailable. Players then connect directly to `aws.mage.net`. Near-zero cost; complements the doorman.

### Security / networking

- EC2 SG: inbound `25565/tcp` (from doorman + `aws.mage.net` clients), `8100/tcp` BlueMap (optionally restricted); SSH via **SSM Session Manager**, not open `22`.
- Oracle SG: inbound `25565/tcp`.
- Scoped IAM: doorman key limited to `ec2:StartInstances`/`DescribeInstances` on the one instance; EC2 instance role limited to `ec2:StopInstances` (self) + S3 backup bucket.
- Cloudflare API token scoped to edit only the `aws.mage.net` record.
- **Whitelist on**; wake gated on whitelisted login (cost safety).
- DDoS: AWS Shield Standard + Cloudflare in front of DNS. Acceptable for a small private server.

### Cost (~60 hrs/month)

| Item | Monthly |
|---|---|
| EC2 `t4g.medium` on-demand (~$0.0336/hr × 60) | ~$2.00 |
| EBS gp3 20 GB | ~$1.60 |
| S3 backups + AWS egress + API calls | ~$0.30 |
| Oracle doorman (Always-Free A1) | **$0.00** |
| Domain `mage.net` (owned) / Cloudflare / Discord | $0.00 |
| **Total** | **~$4/month** |

Oracle caveat: Always-Free A1 capacity can be hard to grab at signup and Oracle may reclaim *genuinely idle* instances — treat as best-effort, not an SLA. The Discord `/start` fallback covers doorman outages.

## Permissions: re-map the existing LuckPerms model

The 5-group model from `mage-mods` (`LUCKPERMS_GROUPS_GUIDE.md`): **Guest → Member → VIP → Moderator → Admin**, VIP tuned for kids. **Group structure carries over unchanged.** The **permission nodes** get re-mapped from Fabric/Essential Commands to the **Bukkit/EssentialsX** namespace (`essentials.*`, `bukkit.command.*`, plugin nodes), producing a new `lp` setup script applied via RCON/console.

## Repository

New repo **`mage-server`** (sibling to `mage-mods`). Zero code overlap with the Fabric modpack builder — different MC generation, platform, and stack (Terraform/OCI/Docker/proxy vs Bun/TS/Modrinth). Proposed layout:

```
infra/
  aws/                 # Terraform: EC2, EBS, S3, IAM, SG, SSM
  oracle/              # Terraform (OCI provider) or documented manual: A1 VM, reserved IP, SG
doorman/               # the hold-and-transfer proxy (impl chosen in spike)
discord-start/         # /start bot (fallback trigger)
server/
  compose.yml          # itzg/minecraft-server + mc-backup
  plugins.txt          # pinned plugin versions/sources
  config/              # server.properties, purpur.yml, EssentialsX, BlueMap
  luckperms/           # group setup script (Bukkit node mapping, ported from mage-mods)
docs/specs/            # this design + future specs
```

This design doc moves into `mage-server/docs/specs/` as plan task 1.

## Testing / Verification

- **Doorman spike:** a vanilla 26.2 client, connecting to a sleeping backend, is held on "booting…", then transferred onto the live server with no manual reconnect. Fallback kick path also verified.
- **Cost-safety:** raw status pings / unknown usernames do **not** start the EC2 instance; only a whitelisted login does.
- **Wake→handoff timing:** cold connect → in-world in ≤ ~2.5 min.
- **Idle stop:** empty 10 min → instance `stopped`; final backup in S3.
- **Persistence:** build → force sleep/wake → survives; survives instance stop/start.
- **DDNS:** `aws.mage.net` reflects the EC2's new IP within seconds of boot.
- **Permissions:** each group has exactly its intended abilities (VIP: `/tp`+creative, not `/stop`; Guest: no `/give`).
- **Essentials:** `/sethome`,`/home`,`/warp`,`/tpa`,`/spawn`,kits work; chat shows LuckPerms prefixes.
- **Terraform:** `plan` zero-drift after `apply`; destroy/re-apply reproduces the AWS stack.
- **Backup restore:** an S3 backup restores into a fresh volume and boots.

## Workstreams (for the implementation plan)

1. **Doorman spike** (de-risk first): validate Transfer-packet handoff on 26.2; pick Gate / Velocity+LimboAPI / custom.
2. **AWS backend (Terraform):** EC2 `t4g.medium`, EBS world vol, S3 + lifecycle, IAM, SG, SSM, boot script (DDNS + Purpur), idle watchdog.
3. **Server config:** itzg compose for Purpur 26.2, pinned plugins, config, backup wiring.
4. **Doorman deploy:** Oracle A1 (Ashburn) + reserved IP, doorman with whitelist-gated wake + limbo + transfer.
5. **DNS + Discord:** Cloudflare records (`mc`/`aws`), stale-record cleanup, `/start` fallback bot.
6. **Permissions:** port LuckPerms groups to Bukkit/EssentialsX nodes; verify per group.

## Open Decisions (resolved unless noted)

- Domain: **`mc.mage.net`** (owned, Cloudflare). ✅
- Repo: **new `mage-server`**. ✅
- Wake: **Oracle doorman + Transfer handoff**, Discord `/start` fallback. ✅
- Plugins: core stack **+ CoreProtect**; GriefPrevention deferred. ✅ (say the word to include it)
- Whitelist: **on**. ✅
- Doorman implementation: **TBD in the spike** — the one genuinely open technical choice.
