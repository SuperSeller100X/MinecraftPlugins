# Minecraft Plugins

A collection of Minecraft plugins.

## ChestLock 🔐

Passcode and transferable-key protection for chests, trapped chests, barrels, and placed shulker boxes on Minecraft **26.2** (Paper / Purpur / Folia). Native GUI workflows, per-lock timed access, personal settings, secure salted hashes, failed-attempt cooldowns, owner/admin management, and Folia-safe scheduling.

See [ChestLock/README.md](ChestLock/README.md). Build with `mvn -B clean verify` in `ChestLock/` (JDK 25 required).

## Subscriptions 🔁

Recurring marketplace for Minecraft **26.2** (Paper / Purpur / Folia). Players and staff create plans for items, money, commands, permissions and ranks; billing is real-world time (`10m`, `1h`, `1d`) with `k/m/b/t` prices, a subscription inbox, and anti-scam policies (pause / skip / auto-cancel — never charge when stock is out). Vault / VaultUnlocked, PlaceholderAPI, LuckPerms, Discord webhooks, SQLite.

See [Subscriptions/README.md](Subscriptions/README.md). Build with `mvn -B clean package` in `Subscriptions/`.

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
Build with `mvn -B clean package` in `PlayerHeads/` (JDK 25 required). A CI
workflow that builds the jar and smoke-tests it on a live Paper 26.2 server is
included at `PlayerHeads/.github/workflows/build.yml` (copy it to the repo
root `.github/workflows/` to activate it).


## ConnectedTools 🔗 (Professional)

Connect items and tools to nearby blocks with **vanilla-style toggling** (levers, buttons, doors, observers, comparators, repeaters, redstone wire, gates, trapdoors) for Minecraft **26.2** (Paper / Purpur / Folia). Professional architecture: `PluginSettings`, `messages.yml`, `ConnectionAPI`, `ConnectionService`, `RedstoneService`, `GuiManager` with `MenuHolder`, `PlatformScheduler` for Folia, YAML persistence (`ConnectionStore`), and full **tab completers** on all commands.

- Commands (`/ct` / `/ct connect` / `/ct disconnect` / `/ct list` / `/ct info` / `/ct gui` / `/ct reload`) — all with tab completion and aliases (`c`, `d`, `l`, `i`).
- Redstone mechanism: toggles real `BlockData` instead of placing temporary redstone blocks.
- Permissions: `connectedtools.connect`, `.disconnect`, `.list`, `.info`, `.gui`, `.reload`, `.all` (with children).
- GUI: inventory-based connection management (`/ct gui`).
- Cross-platform (Linux / Windows / Mac), Java 25, Maven build with Paper 26.2 API.

See [ConnectedTools/README.md](ConnectedTools/README.md). Build with `mvn -B clean package` in `ConnectedTools/` (JDK 25 required).

## AtTag 🔔

Discord-style @-mentions for Minecraft **26.2** (Paper / Purpur / Folia):

- `@playername` — the mentioned player hears a ping sound
- `@here` — replaced with your coordinates in chat, e.g. `[100, 64, 100]`
- `@everyone` / `@all` — every online player gets pinged

Zero commands, zero permissions, configurable sounds. See
[AtTag/README.md](AtTag/README.md) for the full documentation. Build with
`mvn -B clean package` in `AtTag/` (or use the offline `AtTag/build.sh`).
