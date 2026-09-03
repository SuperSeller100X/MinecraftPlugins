# Minecraft Plugins

A collection of Minecraft plugins.

## SwiftTPA ⚡

A fast, fully configurable teleport-request suite for Minecraft **26.2** (Paper /
Purpur / Folia), Java 25. `/tpa` and `/tpahere` with clickable **[Accept] /
[Deny]** chat chips, answering by name or newest-first without one, a requests
GUI with live player heads, warmup countdowns that cancel on move/damage,
cooldowns, per-player toggles and persisted block lists, sounds on every event,
and a full admin toolbox.

- `/tpa (tpr)` · `/tpahere (tph)` · `/tpaccept (tacc) [player]` — no name accepts the latest · `/tpdeny (tdeny)` · `/tpacancel (tpcl)` · `/tpatoggle (tptg)` · `/tpalist (tpls)` · `/tpablock (tpbl)` · `/tpaunblock (tpub)`
- `/swifttpa (stpa)` — help / gui / info / version
- `/swifttpaadmin (stpaadmin)` — reload (rl) · forcetp (ftp) · forcetphere (ftph) · clear (c) · spy (s) · stats (st) · info (i)
- Permissions `swifttpa.*` incl. bypass nodes, full TabCompleter, YAML **or**
  SQLite storage, Folia-safe `teleportAsync`

See [SwiftTPA/README.md](SwiftTPA/README.md). Build with `mvn -B clean package` in
`SwiftTPA/` (JDK 25 required). A CI workflow that runs the consistency checks, the
unit tests and `mvn clean verify` on Temurin 25 is included at
`SwiftTPA/.github/workflows/build.yml` (copy it to the repo root `.github/workflows/`
to activate it).

## PlayerVault 🗄️

A personal, expandable vault for every player on Minecraft **26.2** (Paper / Purpur /
Folia). Everyone starts with **27 slots (3 rows)** and buys extra rows of 9 slots with
server money — **10k, 15k, 22.5k, 33.75k**, each row 1.5× the last. No row limit by
default; vaults that outgrow one chest page are paginated.

- `/pv` — open your vault · `/pv u` upgrade · `/pv p` price · `/pv i` info · `/pv s` sort · `/pv rl` reload
- `/pva o|r|ar|x|c|i|p|rl` — open, set rows, grant rows, reset, clear, inspect, price, reload (works offline)
- Vault / VaultUnlocked (any economy plugin), PlaceholderAPI, YAML **or** SQLite storage,
  permission bonus rows (`playervault.rows.<n>`), full permissions and tab completion

See [PlayerVault/README.md](PlayerVault/README.md). Build with `mvn -B clean package` in
`PlayerVault/` (JDK 25 required). A CI workflow that runs the consistency checks, the
unit tests and `mvn clean verify` on Temurin 25 is included at
`PlayerVault/.github/workflows/build.yml` (copy it to the repo root `.github/workflows/`
to activate it).

## TeleportSigns 🪧

Look at a written sign, bind a destination with `/ts [world] x y z [yaw] [pitch]`, and right-click to teleport. Minecraft **26.2** (Paper / Purpur / Folia), Java 25. Operators create signs; every player can use them. Folia-safe `teleportAsync`, safety checks, cooldown/warmup, optional Vault cost, tab completion, and short aliases (`/ts s`, `/ts r`, `/ts i`, `/ts l`, `/ts c`, `/ts rl`).

See [TeleportSigns/README.md](TeleportSigns/README.md). Build with `mvn -B clean package` in `TeleportSigns/` (JDK 25 required).

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

## XPBank 🏦✨

Store and withdraw **experience points** (total XP, **not** levels) in a personal bank on Minecraft **26.2** (Paper / Purpur / Folia), Java 25. Bank your XP before a risky dive, transfer savings to a friend, climb the leaderboard, and manage it all from commands or a GUI. Folia-safe XP handling, configurable **YAML or SQLite** storage, optional savings **interest** (off by default), sound effects, permissions and admin tools.

- `/xpbank` / `/xp` / `/bank` — deposit, withdraw, balance, pay, top, gui
- `/xp deposit (d)` · `/xp withdraw (w)` · `/xp balance (bal)` · `/xp pay (p)` · `/xp top (lb)` · `/xp gui (g)`
- `/xpbankadmin` / `/xpba` — set / add / take / reset / info / stats / interest / reload
- Optional bank-style **interest** on banked XP — configurable rate/interval, off by default
- Amounts accept `all`, `half` and `k/m/b/t` suffixes; full tab completion
- Permissions: `xpbank.*` (deposit/withdraw/pay/top/gui default-on, admin op)

See [XPBank/README.md](XPBank/README.md). Build with `mvn -B clean verify` in `XPBank/` (JDK 25 required).

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


## ConnectedTools 🔗

Connect items and tools to nearby blocks (redstone, storage, etc.) for Minecraft **26.2** (Paper / Purpur / Folia). Commands (`/ct connect` / `/ct c`), GUI (`/ct gui`), full permissions (`connectedtools.*`), and redstone pulse emission on right-click. Cross-platform (Linux / Windows / Mac) with Java 25 and Maven.

- `/ct connect (c)` — bind held item to a clicked block
- `/ct disconnect (d)` — unbind held item
- `/ct list (l)` — list connections
- `/ct info (i)` — show info
- `/ct gui` — manage via inventory
- `/ct reload` — reload config (op)
- Permissions: `connectedtools.connect`, `.disconnect`, `.list`, `.info`, `.gui`, `.reload`, `.all`

See [ConnectedTools/README.md](ConnectedTools/README.md). Build with `mvn -B clean package` in `ConnectedTools/` (JDK 25 required).

## RandomStructureChallenge ⚡

A random vanilla-structure challenge for Minecraft **26.2** (Paper / Purpur / Folia). `/challenge start` (or `/ch s`) then type an interval like `60`; every N seconds a real Minecraft structure is placed on **every online player** via `/place structure`. Custom hotbar countdown plus a BossBar titled `seconds left until next structure:` whose fill is the remaining percent.

- `/challenge start` / `/ch s` — start (chat interval or `/ch s 60`)
- `/challenge stop` / `/ch x` — stop
- `/challenge pause` / `/ch p` · `/challenge resume` / `/ch r`
- `/challenge status` / `/ch i` · `/challenge reload` / `/ch rl`
- Permissions: `randomstructurechallenge.*` (status default-on, the rest op)

See [RandomStructureChallenge/README.md](RandomStructureChallenge/README.md). Build with `mvn -B clean package` in `RandomStructureChallenge/` (JDK 25 required).

## JustGambling 🎰

A configurable, house-only casino for Minecraft **26.2** (Paper / Purpur / Folia) and Java 25. `/jg` opens the GUI; players can use money for coin flip, dice, roulette, wheel, high/low, slots, scratch cards, Mines, Crash, Jackpot, Lucky Number, and Double or Nothing. Higher-risk tiers trade lower chance for higher payouts. Vault/EssentialsX is preferred, with an isolated YAML fallback currency, configurable sounds, limits, permissions, audit history, admin tools, and Folia-safe scheduling.

See [JustGambling/README.md](JustGambling/README.md). Build with `mvn -B clean package` in `JustGambling/` (JDK 25 required).

## AtTag 🔔

Discord-style @-mentions for Minecraft **26.2** (Paper / Purpur / Folia):

- `@playername` — the mentioned player hears a ping sound
- `@here` — replaced with your coordinates in chat, e.g. `[100, 64, 100]`
- `@everyone` / `@all` — every online player gets pinged

Zero commands, zero permissions, configurable sounds. See
[AtTag/README.md](AtTag/README.md) for the full documentation. Build with
`mvn -B clean package` in `AtTag/` (or use the offline `AtTag/build.sh`).

## ShardTools ✦

DonutSMP-style **Shards** currency and **Shard Tools** for Minecraft **26.2** (Paper /
Purpur / Folia). Every player earns **5 Shards every 5 minutes** (configurable); the
Shard Shop GUI sells the **Shard Pickaxe / Axe / Shovel** (3×3 mining, whole-tree
felling, Silk Touch & Fortune III variants, Eff 5 / Unb 3 / Mending, 3,000 shards
each), the **Shard Potion of Haste** (24 h Haste II, 6,000) and DonutSMP's full
enchanted-netherite catalog, styled exactly like DonutSMP's **amethyst** items:
**purple star** currency icon, light-purple names, **amethyst chime + purple portal
particles** when mining/equipping, and a tree-felling axe that clears logs *and*
leaves. The haste potion grants **1 h of Haste II**; tools **self-destruct after
24 h of real time** — wall-clock based, so it keeps ticking while players are
offline and even while the server is stopped. Spawner/crate-key purchases are
supported via configurable command items.

- `/st shop (s)` · `/st balance (b)` · `/st pay (p)` · `/st top (t)` · `/st info (i)`
- Admin: `/st give (g)` · `/st setprice (sp)` · `/st shards (sh)` · `/st interval (iv)`
  · `/st amount (am)` · `/st award (aw)` · `/st reload (rl)`
- Permissions `shardtools.*`, full TabCompleter, everything configurable

See [ShardTools/README.md](ShardTools/README.md) for the full documentation. Build with
`mvn -B clean package` in `ShardTools/` (JDK 25 required).
## EasyMending 🛠️

Repair tools, weapons, and armor that have Mending using your existing XP for Minecraft **26.2** (Paper / Purpur / Folia). Interactive drop-in anvil repair station GUI (`/em`), instant commands with 1-letter short aliases (`/em h`, `/em oh`, `/em a`, `/em hb`, `/em *`), partial repair fallback, customizable sound/particle effects, full permission tree, and admin management commands (`/ema`). Bypass is strictly off by default for all players and OPs. Cross-platform (Linux / Windows / Mac) with Java 25 and Maven.

- `/easymending` / `/em` — open the interactive repair station GUI (drop in any item to repair)
- `/em hand` (`/em h`) — repair main hand item
- `/em offhand` (`/em oh`) — repair offhand item
- `/em armor` (`/em a`) — repair equipped armor
- `/em hotbar` (`/em hb`) — repair hotbar items
- `/em all` (`/em *`) — repair entire inventory
- `/em info` (`/em i`) · `/em cost` (`/em c`) — inspect durability & costs
- `/emadmin` / `/ema` — reload, force-repair, inspect, live ratio adjustment, bypass, stats
- Permissions: `easymending.*` (use/gui/hand/armor default-on, bypass permissions strictly off by default)

See [EasyMending/README.md](EasyMending/README.md). Build with `mvn -B clean package` in `EasyMending/` (JDK 25 required).

## CombatTag ⚔️

PvP **combat tagging** for Minecraft **26.2** (Paper / Purpur / Folia), Java 25. Getting hit
by a player — or their arrow, trident or splash potion — puts you in combat for **10s**
(configurable). While tagged you **cannot open shops** (EconomyShopGUI & co., detected by
command *and* by the GUI itself), **cannot teleport** (`/home`, `/warp`, `/tpa`, `/back`,
`/rtp`, TeleportSigns, ender pearls, chorus fruit — plus every generic `PlayerTeleportEvent`),
and **cannot repair with EasyMending**. Combat logging kills the quitter, and any other
command can be blocked through a blacklist or strict whitelist.

- `/combattag` (`/ct`) — `status` (`s`) · `gui` (`g`) · `time` (`t`) · `check` (`c`) · `info` (`i`)
- `/combattagadmin` (`/cta`) — `tag` (`t`) · `untag` (`u`) · `clear` (`c`) · `list` (`l`) · `exempt` (`e`) · `duration` (`d`) · `stats` (`st`) · `gui` (`g`) · `reload` (`rl`)
- `/cta bypass` (`b`) — live bypass control: the master switch and each individual bypass
  (`tag`, `shop`, `teleport`, `easymending`, `command`, `combatlog`) can be toggled at
  runtime, and switching one **off enforces that restriction instantly for everyone** —
  no reload, no restart, no re-login
- Boss bar + action bar countdown, GUIs, sounds, full `combattag.*` permission tree with
  bypasses off by default, tab completion, PlaceholderAPI (`%combattag_status%`), developer
  API, Folia-safe scheduling, everything configurable and translatable

See [CombatTag/README.md](CombatTag/README.md). Build with `mvn -B clean verify` in
`CombatTag/` (JDK 25 required); a CI workflow is provided at `CombatTag/.github/workflows/build.yml` (copy it to the repo root `.github/workflows/` to activate it).
## RapidHoppers ⚡

Configurable **high-speed item transport** for Minecraft **26.2** (Paper / Purpur / Folia), Java 25. Hoppers, hopper minecarts, chest minecarts, droppers and (optionally) dispensers move items far faster than vanilla — **32× throughput by default** (every 2 ticks instead of 8, 8 items instead of 1) — without replacing vanilla logic, so comparators and item sorters keep working. A TPS auto-throttle slows or pauses the engine before the server suffers, plus per-chunk / per-world / per-tick budgets and a player-activity radius.

- `/rapidhoppers` · `/rhoppers` · `/rh` — `i` info, `s` stats, `g` gui, `h` help
- `/rapidhoppersadmin` · `/rhadmin` · `/rha` — `rl` reload, `t` toggle [type], `sp` speed, `st` stack, `w` world, `th` throttle, `l` limit, `d` debug, `i`, `s`, `g`
- Clickable GUI control panel with live stats, full `rapidhoppers.*` permission tree, TabCompleter, configurable sounds, MiniMessage messages — no dependencies, everything configurable, changes saved back to `config.yml`

See [RapidHoppers/README.md](RapidHoppers/README.md). Build with `mvn -B clean package` in `RapidHoppers/` (JDK 25 required); an offline stub build + smoke tests run via `./build.sh`.
