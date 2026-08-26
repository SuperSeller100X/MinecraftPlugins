# PlayerVault 🗄️

Personal, expandable storage for every player on your server — an ender chest you can
actually grow.

Every player gets their own vault that starts at **27 slots (3 rows)**. Rows are bought
in blocks of **9 slots** with server money: the first row costs **10,000**, the next
**15,000**, then **22,500**, and so on — each row costs 1.5× the previous one. There is
**no row limit by default**, and vaults that outgrow a single chest page are split
across as many pages as needed.

Built for **Minecraft 26.2** on **Paper**, **Purpur** and **Folia**, compiled with
**Java 25** and **Maven**.

---

## Contents

- [Features](#features)
- [Requirements](#requirements)
- [Building](#building)
- [Installation](#installation)
- [Commands](#commands)
- [Permissions](#permissions)
- [The price ladder](#the-price-ladder)
- [The vault GUI](#the-vault-gui)
- [Configuration](#configuration)
- [Messages](#messages)
- [Storage backends](#storage-backends)
- [Economy support](#economy-support)
- [PlaceholderAPI](#placeholderapi)
- [Folia and threading](#folia-and-threading)
- [Cross-platform notes](#cross-platform-notes)
- [Developer notes](#developer-notes)
- [Troubleshooting](#troubleshooting)

---

## Features

- **A vault for everyone.** No setup, no claiming — `/pv` just works.
- **Expandable in rows.** Buy 9 slots at a time with a geometric price ladder.
- **Unlimited size, multiple pages.** Configurable rows per page (default 5), with
  previous/next navigation. Only rows you own are ever rendered, so it is impossible
  to place an item into space you have not paid for.
- **Works with any economy plugin.** Vault and VaultUnlocked are reached reflectively,
  so EssentialsX, CMI, Xconomy, Gringotts and friends all work with no extra config.
  With no economy installed, upgrades are free instead of broken.
- **Full utility bar.** Upgrade, sort, deposit everything, withdraw everything, info
  and page navigation, all as configurable GUI buttons.
- **Two storage backends.** Human-readable YAML per player, or a single SQLite file.
  Switchable at runtime with `/pv reload` — cached vaults are flushed first, so
  nothing is lost.
- **Admin tools.** Open, resize, grant, reset, inspect and clear any player's vault,
  online or offline. Shrinking returns the evicted items to the player instead of
  deleting them, with optional refunds.
- **Permission-based bonus rows.** Give a rank free rows with
  `playervault.rows.<n>`; losing the rank never deletes stored items.
- **PlaceholderAPI** placeholders for menus, scoreboards and tab lists.
- **Everything is configurable** — prices, sizes, storage, sounds, button materials and
  slots, titles, and every message.
- **Folia-safe.** All scheduling goes through region, entity and async schedulers.
- **Safe on every OS.** Linux, Windows and macOS: explicit UTF-8 I/O, atomic file
  writes, and a bundled SQLite driver with natives for all three.

## Requirements

| | |
|---|---|
| Minecraft | **26.2** |
| Server | Paper, Purpur or Folia (26.2 builds) |
| Java | **25** or newer |
| Build | Maven 3.9+ |
| Optional | Vault or VaultUnlocked + any economy plugin, PlaceholderAPI |

> Paper 26.2 ships Adventure 5 and removed previously deprecated API, so this plugin is
> compiled against `paper-api` 26.2 directly and will not work on 1.21.x servers.

## Building

```bash
cd PlayerVault
mvn -B clean package
```

The finished plugin lands in `PlayerVault/target/PlayerVault-1.0.0.jar`.

`mvn clean verify` additionally runs the unit tests for the pricing ladder, the page
layout maths and the money parser.

The bundled SQLite driver is shaded into the jar **without** package relocation on
purpose: `org.xerial:sqlite-jdbc` locates its native libraries by a package-relative
resource path, and relocating the package would break native loading on Linux, Windows
and macOS alike.

## Installation

1. Drop `PlayerVault-1.0.0.jar` into `plugins/`.
2. Start the server. `config.yml` and `messages.yml` are created automatically.
3. Adjust `config.yml`, then `/pv reload`.

No dependency needs to be installed for the plugin to run. Vault/VaultUnlocked and
PlaceholderAPI are picked up automatically when present.

## Commands

Every command has a short alias, and `/playervault` is also available as `/pv`,
`/pvault` and `/myvault`.

### `/playervault` — aliases `/pv`, `/pvault`, `/myvault`

| Command | Short | Description | Permission |
|---|---|---|---|
| `/pv` | — | Open your vault on the last page you used | `playervault.use` |
| `/pv <page>` | — | Open your vault on a specific page | `playervault.use` |
| `/pv upgrade [rows]` | `/pv u` | Buy rows (default 1) | `playervault.upgrade` |
| `/pv upgrade <rows> yes` | `/pv u 5 y` | Confirm a bulk purchase | `playervault.upgrade` |
| `/pv price [rows]` | `/pv p` | Preview what the next rows cost | `playervault.price` |
| `/pv info` | `/pv i` | Rows, slots, usage, next price, money spent | `playervault.info` |
| `/pv sort` | `/pv s` | Compact and stack the vault contents | `playervault.sort` |
| `/pv reload` | `/pv rl` | Reload `config.yml` and `messages.yml` | `playervault.reload` |
| `/pv help` | `/pv h` | Command overview | — |

Bulk purchases of `upgrade.confirm-threshold` rows or more (default 5) ask for a
confirmation, which stays valid for 30 seconds.

### `/playervaultadmin` — aliases `/pva`, `/pvadmin`

| Command | Short | Description | Permission |
|---|---|---|---|
| `/pva open <player>` | `/pva o` | Open somebody else's vault (read/write) | `playervault.admin.open` |
| `/pva rows <player> <rows>` | `/pva r` | Set an exact row count | `playervault.admin.rows` |
| `/pva addrows <player> [rows]` | `/pva ar` | Grant rows for free (negative removes) | `playervault.admin.rows` |
| `/pva reset <player>` | `/pva x` | Back to the configured starting size | `playervault.admin.reset` |
| `/pva clear <player>` | `/pva c` | Empty the vault, keep its size | `playervault.admin.clear` |
| `/pva info <player>` | `/pva i` | Inspect somebody's vault | `playervault.admin.info` |
| `/pva price [rows]` | `/pva p` | Show the ladder from the first purchase | `playervault.admin` |
| `/pva reload` | `/pva rl` | Reload configuration | `playervault.reload` |
| `/pva help` | `/pva h` | Admin command overview | — |

Admin row changes work on **offline** players too. Names are resolved against online
players first and then against the server's cached profile lookup, so the command never
blocks on Mojang's session service.

When a vault is shrunk while its owner is online, their open GUI is closed and any items
that no longer fit are **dropped at their feet** rather than deleted.

> Admin views hide the upgrade, deposit, withdraw and sort buttons, because those act
> on the person clicking. If two people edit the same vault at the same time, the last
> one to close their window wins.

## Permissions

| Permission | Default | Description |
|---|---|---|
| `playervault.use` | everyone | Open your own vault |
| `playervault.upgrade` | everyone | Buy rows |
| `playervault.price` | everyone | Preview prices |
| `playervault.info` | everyone | View your vault statistics |
| `playervault.sort` | everyone | Compact your vault |
| `playervault.reload` | op | Reload configuration and messages |
| `playervault.bypass.cost` | op | Upgrade without paying |
| `playervault.bypass.limit` | op | Ignore the `max-rows` limit |
| `playervault.admin.open` | op | Open another player's vault |
| `playervault.admin.rows` | op | Set or grant rows |
| `playervault.admin.reset` | op | Reset a vault to the starting size |
| `playervault.admin.clear` | op | Empty a vault |
| `playervault.admin.info` | op | Inspect a vault |
| `playervault.admin` | op | All administrative permissions |
| `playervault.*` | op | Everything |

### Bonus rows

`playervault.rows.<n>` grants free starting rows. The number is read straight from the
node, for example:

```yaml
# LuckPerms: give the VIP rank 5 rows (45 slots)
lp group vip permission set playervault.rows.5
```

How it combines with the configured size is controlled by `vault.bonus-rows`:

| Mode | Effect |
|---|---|
| `HIGHEST` (default) | vault size = max(current size, the permission value) |
| `ADD` | vault size = max(current size, starting-rows + the permission value) |
| `OFF` | ignore row permissions entirely |

Bonus rows only ever **grow** a vault. Removing the permission does not shrink it and
never deletes items — an admin has to do that explicitly.

Bonus rows are also excluded from the price ladder: the ladder is indexed by rows that
were actually **paid for**, so a VIP still pays exactly 10k for their first purchased
row.

## The price ladder

With the shipped defaults (`base-price: 10k`, `multiplier: 1.5`, `round-decimals: 2`):

| Row bought | Slots after | Price of that row | Total spent |
|---|---|---|---|
| 1 | 4 rows / 36 slots | 10,000.00 | 10,000.00 |
| 2 | 5 rows / 45 slots | 15,000.00 | 25,000.00 |
| 3 | 6 rows / 54 slots | 22,500.00 | 47,500.00 |
| 4 | 7 rows / 63 slots | 33,750.00 | 81,250.00 |
| 5 | 8 rows / 72 slots | 50,625.00 | 131,875.00 |
| 6 | 9 rows / 81 slots | 75,937.50 | 207,812.50 |
| 7 | 10 rows / 90 slots | 113,906.25 | 321,718.75 |
| 8 | 11 rows / 99 slots | 170,859.38 | 492,578.13 |
| 9 | 12 rows / 108 slots | 256,289.06 | 748,867.19 |
| 10 | 13 rows / 117 slots | 384,433.59 | 1,133,300.78 |

`multiplier: 1.0` gives a flat price, and `base-price: 0` makes every row free.

## The vault GUI

```
┌─────────────────────────────────────┐
│  storage rows you own (up to 5)     │   ← normal chest behaviour
│                                     │
├─────────────────────────────────────┤
│ ◀  sort  ⇩  info  page  ⇧  ░  ★    │   ← utility bar
└─────────────────────────────────────┘
```

| Button | Default material | Slot | Action |
|---|---|---|---|
| Previous page | `ARROW` | 0 | Turn to the previous page |
| Sort | `STRUCTURE_VOID` | 1 | Compact and stack the contents |
| Deposit everything | `HOPPER` | 2 | Move your whole inventory into the vault |
| Info | `BOOK` | 3 | Print vault statistics to chat |
| Page indicator | `PAPER` | 4 | Shows the current page (not clickable) |
| Withdraw everything | `DROPPER` | 5 | Move the vault into your inventory |
| Filler | `GRAY_STAINED_GLASS_PANE` | 6 | Decoration |
| Upgrade | `NETHER_STAR` | 7 | Buy one row, showing price and balance |
| Next page | `ARROW` | 8 | Turn to the next page |

Materials **and** slot positions are configurable under `gui.buttons`.

Safety rules enforced by the click handler:

- Only rows you own are rendered, so items can never land in space you have not bought.
- Every utility-bar click is cancelled — buttons cannot be taken, moved or dragged.
- Double-click "collect to cursor" is cancelled while a vault is open. Vanilla scans
  the whole top inventory, which would otherwise let a player pull a GUI button into
  their inventory.

## Configuration

`config.yml` is created on first start. Every option:

### `storage`

| Key | Default | Description |
|---|---|---|
| `type` | `YAML` | `YAML` or `SQLITE` |
| `auto-save-seconds` | `300` | Periodic background save; `0` disables it |
| `save-on-quit` | `true` | Write a vault when the player disconnects |
| `sqlite.journal-mode` | `WAL` | Any SQLite journal mode; validated against an allow-list |
| `sqlite.busy-timeout-ms` | `5000` | How long SQLite waits on a locked database |

### `vault`

| Key | Default | Description |
|---|---|---|
| `starting-rows` | `3` | Rows every player starts with (3 = 27 slots) |
| `max-rows` | `-1` | Hard ceiling on purchased rows; `-1` = unlimited |
| `bonus-rows` | `HIGHEST` | `HIGHEST`, `ADD` or `OFF` — see [Bonus rows](#bonus-rows) |

### `upgrade`

| Key | Default | Description |
|---|---|---|
| `base-price` | `10k` | Price of the first purchased row |
| `multiplier` | `1.5` | Growth factor per row |
| `round-decimals` | `2` | Decimals prices are rounded to |
| `max-rows-per-purchase` | `25` | Cap for a single `/pv upgrade <n>` |
| `confirm-threshold` | `5` | Ask for confirmation from this many rows; `0` disables |

Money values accept the suffixes `k`, `m`, `b` and `t`, so `10k` means `10000`.

### `economy`

| Key | Default | Description |
|---|---|---|
| `enabled` | `true` | `false` makes all upgrades free |
| `refund-percent` | `0` | Share of the price refunded when an admin removes rows |
| `log-transactions` | `true` | Log purchases and refunds to the console |

### `gui`

| Key | Default | Description |
|---|---|---|
| `title` | see file | MiniMessage title; `%page%` and `%pages%` are replaced |
| `page-rows` | `5` | Storage rows per page (1–5) |
| `remember-page` | `true` | Reopen on the page the player last used |
| `sounds` | `true` | Play a click sound on buttons |
| `sound` | `ui.button.click` | Namespaced sound id used for clicks |
| `buttons.<name>.material` | see file | Any item material |
| `buttons.<name>.slot` | see file | Utility-bar slot, 0–8 |

### `placeholders`

| Key | Default | Description |
|---|---|---|
| `enabled` | `true` | Register `%playervault_*%` when PlaceholderAPI is present |

### `debug`

`false` by default. Logs each periodic auto-save.

## Messages

All text lives in `messages.yml` and uses
[MiniMessage](https://docs.advntr.dev/minimessage/format.html). Legacy `&` colour
codes are converted automatically, including `&#rrggbb` hex colours, so you can paste
older formats straight in. An unknown `<tag>` is left as literal text rather than
breaking the message.

`prefix` is prepended to command feedback; `info.*`, `price.*` and `help.*` blocks are
sent without it.

Available placeholders: `%player%`, `%count%`, `%rows%`, `%slots%`, `%used%`, `%free%`,
`%pages%`, `%page%`, `%price%`, `%total%`, `%balance%`, `%spent%`, `%max%`, `%amount%`,
`%provider%`, `%arg%`, `%from%`, `%to%`, `%row%`.

## Storage backends

### YAML (default)

```
plugins/PlayerVault/vaults/<uuid>.yml
```

```yaml
owner: 069a79f4-44e9-4726-a5be-fca90e38aaf5
name: Notch
rows: 5
purchased: 2
spent: 25000.0
updated: 1798000000000
items:
  - <base64 chunk>
  - <base64 chunk>
```

Human readable and hand-editable. Writes are atomic — the document goes to a `.tmp`
sibling and is then moved into place, so a crash mid-write can never truncate a vault.
The item blob is split into fixed-size chunks so SnakeYAML's line folding cannot alter
it.

### SQLite

```
plugins/PlayerVault/vaults.db
```

One table, one row per player:

```sql
CREATE TABLE player_vaults (
    uuid      TEXT PRIMARY KEY NOT NULL,
    name      TEXT NOT NULL DEFAULT '',
    rows      INTEGER NOT NULL DEFAULT 1,
    purchased INTEGER NOT NULL DEFAULT 0,
    spent     REAL NOT NULL DEFAULT 0,
    updated   INTEGER NOT NULL DEFAULT 0,
    items     TEXT NOT NULL DEFAULT ''
);
```

Better for large servers. The driver is bundled in the jar, so this works on servers
with no internet access. Adding columns in a future release is handled by an automatic
migration — you never have to delete the database.

Switching backends with `/pv reload` flushes the cache to the old backend first and
keeps it in memory, so nothing is lost either way. If SQLite cannot be opened, the
plugin logs the reason and falls back to YAML instead of failing to start.

## Economy support

Vault and VaultUnlocked are looked up through the Bukkit service manager **by
reflection**, so the plugin compiles and runs with neither installed:

1. `net.milkbowl.vault.economy.Economy` (Vault)
2. `net.milkbowl.vault2.economy.Economy` (VaultUnlocked)

Anything that registers with Vault works — EssentialsX, CMI, Xconomy, Gringotts,
iConomy, Treasury bridges and more. Both `EconomyResponse` and plain-boolean providers
are understood, and money is formatted by the provider when it can.

With no provider registered, upgrades report `upgrade.no-economy` instead of failing
silently, and `economy.enabled: false` makes upgrades free for everyone.

## PlaceholderAPI

Registered automatically when PlaceholderAPI is installed. Identifier: `playervault`.

| Placeholder | Value |
|---|---|
| `%playervault_rows%` | Rows owned |
| `%playervault_slots%` | Total slots |
| `%playervault_used%` | Slots in use |
| `%playervault_free%` | Empty slots |
| `%playervault_pages%` | GUI pages |
| `%playervault_next_price%` | Price of the next row |
| `%playervault_spent%` | Money spent so far |
| `%playervault_purchased%` | Rows that were paid for |
| `%playervault_percent%` | Vault fullness in percent |
| `%playervault_max_rows%` | Configured limit, `-1` when unlimited |
| `%playervault_provider%` | Hooked economy provider |

## Folia and threading

`folia-supported: true` is set in `plugin.yml`, and every task goes through
`PlatformScheduler`:

| Work | Scheduler |
|---|---|
| Vault GUI, inventory edits, player feedback | the player's **entity** scheduler |
| Auto-save timer, cache eviction | the **global region** scheduler |
| File and database reads/writes | the **async** scheduler |

Folia is detected by probing for `io.papermc.paper.threadedregions.RegionizedServer`,
as recommended by the Paper documentation. On Paper and Purpur the same region APIs
run on the main thread, so one code path serves all three.

The vault cache is a `ConcurrentHashMap` and each `VaultData` guards its own fields, so
a region thread editing a vault and an async task saving it cannot corrupt each other.
Vault contents are cloned on every read and write between the cache and an inventory.

## Cross-platform notes

Linux, Windows and macOS are all first-class:

- Every file read and write goes through `java.nio` with an explicit **UTF-8** charset,
  so a vault file written on Windows reads identically on Linux.
- Paths are built with `java.nio.file.Path`, never string concatenation. The SQLite URL
  normalises separators, which SQLite accepts on Windows.
- File writes are atomic, with a graceful fallback where a filesystem refuses
  `ATOMIC_MOVE` (some network shares and macOS volumes).
- The bundled `sqlite-jdbc` ships native libraries for all three operating systems and
  selects the right one at runtime.
- No shell commands, no absolute paths, no platform-specific line endings.

## Developer notes

```
PlayerVault/
├── pom.xml                     Java 25, paper-api 26.2, shaded sqlite-jdbc
├── tools/check_consistency.py  offline checks (see below)
└── src/
    ├── main/java/dev/superseller/playervault/
    │   ├── PlayerVaultPlugin.java      wiring, reload, storage selection
    │   ├── command/                    VaultCommand, VaultAdminCommand, VaultTabCompleter
    │   ├── config/                     Settings (immutable snapshot), Messages
    │   ├── economy/                    EconomyHook (reflective Vault bridge)
    │   ├── gui/                        GuiLayout, VaultGui, VaultHolder, GuiListener
    │   ├── integration/                VaultPlaceholders (PlaceholderAPI)
    │   ├── listener/                   ConnectionListener (join/quit)
    │   ├── model/                      VaultData
    │   ├── pricing/                    PriceCalculator
    │   ├── scheduler/                  PlatformScheduler (Folia-safe)
    │   ├── service/                    VaultService (cache + rules)
    │   ├── storage/                    VaultStore, YamlVaultStore, SqliteVaultStore, ItemCodec
    │   └── util/                       Numbers, Texts
    ├── main/resources/                 plugin.yml, config.yml, messages.yml
    └── test/java/                      PriceCalculatorTest, GuiLayoutTest, NumbersTest
```

`PriceCalculator`, `GuiLayout` and `Numbers` are deliberately free of Bukkit types so
the price ladder, the page geometry and the money parser are covered by plain JUnit
tests that need no server.

Items are serialised with Paper's NBT API (`ItemStack.serializeItemsAsBytes` /
`deserializeItemsFromBytes`) rather than Bukkit's map format, so stored vaults pass
through the vanilla data converter on a Minecraft upgrade instead of being
re-interpreted.

### Offline consistency checks

```bash
python3 PlayerVault/tools/check_consistency.py
```

This needs only Python and PyYAML, and verifies the things a compiler cannot see: that
every message key, config path and permission referenced from Java exists in the
shipped resource files, that `plugin.yml` declares `api-version: '26.2'` and
`folia-supported: true`, that the declared main class exists, that permission children
resolve, and that no `${...}` in a resource would be eaten by Maven filtering. It runs
as a CI step before the Maven build.

### CI workflow

`PlayerVault/.github/workflows/build.yml` builds the plugin on Temurin **25** with
`mvn -B -ntp clean verify`, runs the consistency checks first, and uploads
`PlayerVault-1.0.0.jar` as an artifact.

GitHub only executes workflows that live in the repository's **root** `.github/workflows/`
directory, so to activate it copy the file there:

```bash
mkdir -p .github/workflows
cp PlayerVault/.github/workflows/build.yml .github/workflows/playervault.yml
```

This matches how the other plugins in this repository ship their workflows.

## Troubleshooting

**`No Vault / VaultUnlocked provider found`**
Install Vault or VaultUnlocked plus an economy plugin, then `/pv reload`. Until then
upgrades report that no economy is available rather than failing quietly.

**`SQLite storage is unavailable, falling back to YAML`**
The bundled driver could not open `vaults.db`. Check file permissions on
`plugins/PlayerVault/`. The plugin keeps running on YAML.

**Upgrades are free**
Either `economy.enabled` is `false`, or the player has `playervault.bypass.cost`.

**A player's vault is smaller than their rank**
`vault.bonus-rows` may be `OFF`. Bonus rows only grow a vault, so after fixing the
setting the player has to rejoin (or run `/pv`) for the permission to be re-read.

**Vault looks wrong after a Minecraft update**
Item data is migrated by the vanilla converter on load. Anything that cannot be
migrated is skipped and logged rather than corrupting the rest of the vault.
