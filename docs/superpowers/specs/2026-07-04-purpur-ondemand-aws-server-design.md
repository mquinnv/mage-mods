# On-Demand Purpur 26.2 Server on AWS — Design

**Date:** 2026-07-04
**Status:** Draft for review
**Author:** Michael + Claude

## Summary

Stand up a **Minecraft 26.2 Purpur server** for family/friends that **only costs money while it is running**. Players connect with **vanilla clients** (no mods, no launcher changes). The server **boots automatically when someone tries to connect** and **shuts itself down after 10 minutes with no players**. Infrastructure lives in the owner's AWS account (`us-east-1`), managed with Terraform. The server's admin layer — **LuckPerms** permissions and **EssentialsX** ("essentials") — is re-applied on the new platform.

This replaces the previous approach of a paid, always-on managed host (Apex Hosting), which cost money 24/7 for a server used a few hours a day.

## Goals

- Pay only for compute actually used (target: **~$4–5/month** all-in at ~60 hrs/month of play; see cost table).
- **Vanilla clients connect** — zero client-side install for kids/friends.
- Auto-wake on connect; auto-sleep when idle. No manual start step required.
- Persistent world with automatic backups; no data loss across sleep/wake or instance replacement.
- Re-apply the existing **LuckPerms group model** (Guest → Member → VIP → Mod → Admin) and restore **essentials** (homes/warps/tpa/kits/spawn).
- Low latency for US East Coast players (~5–20 ms).
- All infra reproducible in Terraform.

## Non-Goals

- **Running the existing Create-based mage modpack.** Create and most gameplay content mods have no 26.2 build; Purpur cannot run Fabric/Forge content mods at all. The Fabric 1.20.1 modpack tooling in this repo is untouched and can be revived separately if desired.
- Public/large-scale server. This is a small trusted-players server (whitelist on).
- Multi-region / high-availability. Single instance in one AZ is fine.

## Platform Decision: Purpur 26.2

**Why Purpur** (a performance-tuned fork of Paper, which forks Spigot/Bukkit):

- **Vanilla clients connect** — plugins run entirely server-side. This is the hard requirement that rules out Fabric/NeoForge content modding.
- Richest server-admin ecosystem: EssentialsX, LuckPerms, BlueMap, CoreProtect, WorldGuard, etc.
- **The whole admin stack is confirmed available on 26.2** (verified via Modrinth + provider APIs on 2026-07-04):
  - Purpur has a `26.2` build (Purpur API).
  - LuckPerms ships `paper`/`bukkit` builds for 26.2.
  - EssentialsX supports 26.2.
  - BlueMap and Spark ship Paper/Bukkit builds for 26.2.
- The Fabric-vs-NeoForge loader question is **moot** — Purpur is neither; it's the Bukkit plugin lineage.

**What we give up:** Fabric-only tools like Carpet. Purpur's own config (`purpur.yml`, `paper-world-defaults.yml`) covers most of the equivalent tuning. No mod content — datapacks only for custom content.

### Plugin stack (server-side only)

| Plugin | Purpose | Required |
|---|---|---|
| LuckPerms | Permissions / groups / prefixes | ✅ |
| EssentialsX | Homes, warps, tpa, kits, spawn, /msg — the "essentials" | ✅ |
| EssentialsXChat | Chat formatting with LuckPerms prefixes | ✅ |
| Vault | Bridge between EssentialsX and LuckPerms | ✅ |
| BlueMap | Live web map | ✅ |
| Spark | Performance profiler | ✅ |
| CoreProtect | Block logging / griefing rollback (insurance) | Recommended |
| GriefPrevention | Land claims | Optional (family may not need it) |

Exact plugin build versions are pinned during implementation (planning phase resolves each against 26.2).

## Hosting Architecture: EC2 Graviton On-Demand

**Compute:** a single **`t4g.medium`** instance (2 vCPU ARM, 4 GB RAM) in `us-east-1`. Paper/Purpur run natively on ARM/Java 21. 4 GB is ample for a family survival server (~3 GB JVM heap). On-demand billing means it only accrues cost while `running`; when stopped, only storage is billed. Resize path: bump to `t4g.large` (8 GB) or a `c7g` (higher clock) if TPS suffers — trivial on-demand change.

**Server runtime:** the **`itzg/minecraft-server`** Docker image with `TYPE=PURPUR`, `VERSION=26.2`. It downloads Purpur and the pinned plugins declaratively from env/config, exposes RCON (used by the watchdog), and pairs with **`itzg/mc-backup`** for scheduled + on-shutdown backups to S3.

**World persistence:** a dedicated **EBS gp3 volume** (start 20 GB) mounted for world + plugin data, detached from the instance's lifecycle so the world survives instance stop/start and replacement.

**Backups:** `itzg/mc-backup` sidecar → **S3 bucket** on an interval and on graceful shutdown. S3 lifecycle rule expires backups older than 14 days. Restores are a manual pull from S3.

### Auto-wake flow (boots on connect)

```
Player MC client resolves  mc.<domain>
        │
        ▼
Route 53 public hosted zone  ──(query logging)──►  CloudWatch Logs
        │                                                │
        │                                                ▼
        │                                          subscription filter
        │                                                │
        ▼                                                ▼
 (first connect fails —                          Lambda: ec2:StartInstances
  server still down)                                     │
                                                         ▼
                                          Instance boots (~1–2 min), Docker
                                          starts Purpur, boot script updates
                                          the Route 53 A record to the new
                                          public IP (dynamic IP — no paid
                                          Elastic IP needed)
        │
        ▼
 Player reconnects ~1–2 min later → in.
```

The first connection attempt intentionally "fails" but its DNS lookup is what triggers the boot — standard on-demand UX. We use a **dynamic public IP + A-record update on boot** rather than an Elastic IP, because an idle Elastic IP now costs ~$3.60/mo and would erase the cost advantage; a Route 53 hosted zone is only ~$0.50/mo.

### Auto-sleep flow (idle shutdown)

An on-box **watchdog** (systemd timer + script, or the itzg watchdog) polls player count via RCON. After **10 minutes with zero players**, it triggers a graceful stop: flush world → run final backup to S3 → `docker stop` → the instance calls `ec2:StopInstances` on itself (via instance role) or `shutdown` with a stop-on-shutdown behavior.

### Networking / security

- Security group: inbound `25565/tcp` (Minecraft) from `0.0.0.0/0`; `8100/tcp` (BlueMap) optionally restricted; SSH `22` restricted to owner IP / SSM only.
- Prefer **SSM Session Manager** over open SSH.
- Whitelist **on** — trusted players only.
- DDoS: AWS Shield Standard (automatic). Acceptable for a small private server.

### Cost estimate (~60 hrs/month)

| Item | Monthly |
|---|---|
| `t4g.medium` on-demand (~$0.0336/hr × 60) | ~$2.00 |
| EBS gp3 20 GB (~$0.08/GB, billed always) | ~$1.60 |
| Route 53 hosted zone | ~$0.50 |
| S3 backups (few GB) + Lambda + CloudWatch | ~$0.20 |
| Domain (if newly registered, amortized) | ~$1.00 |
| **Total** | **~$4–5/month** |

Scales roughly linearly with hours played. Compare: always-on managed 4 GB host ≈ $10–15/mo flat; Mineando (EU-only, ruled out on latency) ≈ $5/mo.

## Permissions: re-mapping the existing LuckPerms model

The repo already defines a 5-group model (`LUCKPERMS_GROUPS_GUIDE.md`): **Guest → Member → VIP → Moderator → Admin**, with VIP tuned for kids (creative freedom, no server control). That **group structure carries over unchanged**.

What changes: the **permission nodes**. The existing setup scripts target Fabric/Essential Commands + vanilla command nodes. On Purpur/EssentialsX the nodes are different (`essentials.*`, `bukkit.command.*`, Purpur/Paper nodes, plugin-specific nodes). Implementation re-maps each group's grants to the Bukkit/EssentialsX node namespace and produces a new `lp` setup script (applied via RCON or console).

## Repository Impact

This repo is currently a **Fabric modpack builder** (client + server packs, Modrinth uploads). The on-demand Purpur server is a different artifact. Proposed layout (in this repo):

```
infra/                 # Terraform: EC2, EBS, S3, Route53, Lambda, IAM, SG
server-purpur/
  compose.yml          # itzg/minecraft-server + mc-backup
  plugins.txt          # pinned plugin versions/sources
  config/              # server.properties, purpur.yml, EssentialsX, BlueMap
  luckperms/           # group setup script (Bukkit node mapping)
docs/superpowers/specs/ # this design + future specs
```

The existing Fabric modpack tooling (`scripts/`, `config/mods-*.json`, `build/`) stays as-is and orthogonal — still usable if the 1.20.1 Create pack is ever revived.

## Testing / Verification

- **Terraform:** `plan` is zero-drift after `apply`; destroy/re-apply reproduces the stack.
- **Wake:** from a cold (stopped) instance, pointing a client at `mc.<domain>` triggers boot within ~2 min and the client connects on retry.
- **Sleep:** with no players for 10 min, instance transitions to `stopped`; a final backup lands in S3.
- **Persistence:** build something, force sleep/wake, confirm the world and builds survive; confirm survival across an instance stop/start.
- **Permissions:** a test account in each group has exactly the intended abilities (VIP can `/tp` + creative, cannot `/stop`; Guest cannot `/give`; etc.).
- **Essentials:** `/sethome`, `/home`, `/warp`, `/tpa`, `/spawn`, kits function; chat shows LuckPerms prefixes.
- **Backup restore:** a backup from S3 restores into a fresh volume and boots.

## Open Decisions (resolve during spec review)

1. **Domain name** — the clean auto-wake UX needs a Route 53 public hosted zone, i.e. a domain (e.g. `mc.example.com`). Do you already own a domain to use, or should we register one (~$12/yr)? *(Blocking for the wake mechanism.)*
2. **Repo layout** — add `infra/` + `server-purpur/` to this repo (recommended), or spin up a new repo?
3. **Optional plugins** — include CoreProtect (rollback insurance) and/or GriefPrevention (land claims), or keep the stack minimal?
4. **Wake trigger** — DNS-query auto-wake as designed (primary), plus an optional Discord `/start` command or web button as a backup trigger? Or DNS-only?

## Workstreams (for the implementation plan)

1. **Infra (Terraform):** VPC/subnet/SG, EC2 `t4g.medium`, EBS world volume, S3 backups bucket + lifecycle, Route 53 zone + record, wake Lambda + CloudWatch subscription, IAM roles, SSM.
2. **Server config:** `itzg/minecraft-server` compose for Purpur 26.2, pinned plugin manifest, server/plugin config, watchdog + backup wiring.
3. **Permissions:** re-map LuckPerms groups to Bukkit/EssentialsX nodes; setup script; verify per-group.
```
