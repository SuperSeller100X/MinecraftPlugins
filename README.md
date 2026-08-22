# Minecraft Plugins

A collection of Minecraft plugins.

## ChunkVoter 🗳️

Community-voted chunk regeneration for Minecraft **26.2** (Paper / Purpur /
Folia). Run `/chunkvoter` in a chunk to open a YES/NO vote shown in the
action bar (over the hotbar) with clickable chat buttons; if a majority votes
**YES** the chunk is regenerated from the world seed. ChunkVoter includes
standalone **Anvil (`.mca`) chunk regeneration** (working without WorldEdit)
and also reflectively supports WorldEdit's `//regen` adapter when available.
Optional WorldGuard support lets only the **region owner** decide on claimed
chunks (admins bypass). No persistent storage, fully translatable, Folia-safe.

- `/chunkvoter` / `/cv` — start a vote, vote yes/no, or view the vote
- `/chunkvoteadmin` / `/cva` — reload, list, cancel, force, info
- Permissions: `chunkvoter.*` (use/start/vote default-on, admin/bypass op)

See the full documentation in [ChunkVoter/README.md](ChunkVoter/README.md).
Build with `mvn -B clean package` in `ChunkVoter/` (JDK 25 required).

## PlayerBank 🏦

Isolated bank accounts for Minecraft **26.2** (Paper). Deposit and withdraw via Vault, compound interest (default 2.5% every 10 real minutes). Bank money cannot be used by `/pay`, shops, or other plugins until withdrawn.

See [PlayerBank/README.md](PlayerBank/README.md). Build with `mvn -B clean package` in `PlayerBank/`.

## Gifty 🍩

A sweet gift & delivery system with a DonutSMP-style GUI for Minecraft
26.2 (Paper / Purpur / Folia).

- `/gift <player>` — compose a gift (items + money + message) in a GUI
- `/inbox` — claim your deliveries from a paginated inbox GUI
- Flat-file storage, Vault economy, PlaceholderAPI, full permissions

See [Gifty/README.md](Gifty/README.md) for the full documentation.
Build with `mvn -B clean package` in `Gifty/` (JDK 25 required).

## FairDeal 🛡️

A planned secure player-to-player item and money trading plugin for Minecraft
**26.2** Paper, Purpur, and Folia servers. Both players lock and confirm the
same final offer, with Vault-compatible economy support, recovery inboxes,
complete trade logs, and anti-bait-and-switch protection.

See [FairDeal/README.md](FairDeal/README.md) for the detailed requirements and
planned feature documentation.

## PlayerHeads 🗿

Give server admins the player head of any Minecraft account (skin fetched
asynchronously, works for accounts that never joined) for Minecraft **26.2**
(Paper / Purpur / Folia). Admin-only by default, no cooldown, Folia-safe.

- `/playerheads <player> [amount]` / `/ph <player> [amount]` — receive heads
- `/playerheads reload` / `/ph reload` — reload config & messages
- Permissions: `playerheads.use` / `.reload` / `.bypass-max` (all default op)

See the full documentation in [PlayerHeads/README.md](PlayerHeads/README.md).
Build with `mvn -B clean package` in `PlayerHeads/` (JDK 25 required). CI
builds the jar and smoke-tests it on a live Paper 26.2 server.


## AtTag 🔔

Discord-style @-mentions for Minecraft **26.2** (Paper / Purpur / Folia):

- `@playername` — the mentioned player hears a ping sound
- `@here` — replaced with your coordinates in chat, e.g. `[100, 64, 100]`
- `@everyone` / `@all` — every online player gets pinged

Zero commands, zero permissions, configurable sounds. See
[AtTag/README.md](AtTag/README.md) for the full documentation. Build with
`mvn -B clean package` in `AtTag/` (or use the offline `AtTag/build.sh`).
