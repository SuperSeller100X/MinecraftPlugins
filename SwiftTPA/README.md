# SwiftTPA ⚡

A fast, fully configurable **teleport-request suite** for Minecraft **26.2**
(Java 25) — running with a single jar on **Paper**, **Purpur** and **Folia**.

`/tpa` and `/tpahere` done right: clickable **[Accept] / [Deny]** chips in chat,
accept or deny by player name — or answer your **newest** request by leaving the
name out — a chest **GUI** with live player heads, teleport **warmups** that
cancel when you move or take damage, **cooldowns**, per-player **toggles** and
**block lists**, sound effects on every event, and a complete **admin toolbox**
with force teleports, request clearing and a spy feed. Every message, sound,
limit and button is configurable.

---

## Table of contents

- [Features](#features)
- [Compatibility](#compatibility)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Commands](#commands)
  - [Player commands](#player-commands)
  - [Main command](#main-command)
  - [Admin commands](#admin-commands)
- [Permissions](#permissions)
  - [Player permissions](#player-permissions)
  - [Bypass permissions](#bypass-permissions)
  - [Admin permissions](#admin-permissions)
- [The requests GUI](#the-requests-gui)
- [Sound effects](#sound-effects)
- [Configuration](#configuration)
  - [config.yml reference](#configyml-reference)
  - [messages.yml](#messagesyml)
- [Storage](#storage-yaml-or-sqlite)
- [How a request flows](#how-a-request-flows)
- [Folia notes](#folia-notes)
- [Building from source](#building-from-source)
- [Automated checks and CI](#automated-checks-and-ci)

---

## Features

| Area | What you get |
| --- | --- |
| Requests | `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/tpacancel` — every command with two short aliases |
| Answering | By name (`/tpaccept Steve`) or newest-first without a name (`/tpaccept`) |
| Chat UX | Incoming requests arrive with clickable **[Accept]** and **[Deny]** chips |
| GUI | Chest menu of pending requests as player heads (skins loaded async); left-click accept, right-click deny; toggle + cancel buttons |
| Warmup | Configurable countdown before the teleport (action bar + rising tick sound); cancels on movement, damage or logout |
| Cooldown | Per-player cooldown between sending requests, with bypass permission |
| Toggle & block | `/tpatoggle` to opt out; `/tpablock` / `/tpaunblock` to ignore specific players — both persisted |
| Limits | Request expiry, max pending requests per player, one-outgoing rule, cross-world toggle, disabled worlds |
| Sounds | 14 individually configurable events (vanilla sound keys, volume, pitch — or muted) |
| Admin tools | Reload, force teleports, clear requests, per-player/self stats, spy feed, platform info |
| Storage | YAML (one file per player) **or** SQLite (single db file) — switchable in config |
| Safe by design | Pure in-memory request queue that is fully unit-tested; Folia-safe scheduling everywhere |

## Compatibility

| Component | Requirement |
| --- | --- |
| Minecraft | **26.2** (calendar-versioned drop; follows 1.21.11) |
| Server | Paper 26.2, Purpur 26.2 or Folia 26.2 |
| Java (server & build) | **Java 25 or newer** (required by Minecraft 26.2 itself) |
| Build tool | Maven 3.9+ |
| Operating systems | Linux, Windows, macOS — the jar and both storage backends are fully portable; no native or path-specific code |
| Dependencies | None required, none bundled except the shaded `sqlite-jdbc` driver |

## Installation

1. Build the jar (see [Building from source](#building-from-source)) or grab the
   CI artifact: `SwiftTPA-1.0.0.jar`.
2. Drop it into your server's `plugins/` folder.
3. Start the server — SwiftTPA generates `config.yml` and `messages.yml` inside
   `plugins/SwiftTPA/`.
4. (Optional) tune `config.yml` and apply with `/stpaadmin reload`.

## Quick start

```text
Steve:   /tpa Alex
Alex:    (chat) Steve wants to teleport to you. [Accept] [Deny] (120s)
Alex:    /tpaccept            # newest first — or /tpaccept Steve
Steve:   "Teleporting in 3s — don't move!"
Steve:   teleports to Alex ✔
```

## Commands

### Player commands

| Command | Short forms | Description | Permission |
| --- | --- | --- | --- |
| `/tpa <player>` | `/tpreq`, `/tpr` | Ask to teleport **to** a player | `swifttpa.tpa` |
| `/tpahere <player>` | `/tphere`, `/tph` | Ask a player to teleport **to you** | `swifttpa.tpahere` |
| `/tpaccept [player]` | `/tpyes`, `/tacc` | Accept a request — newest first without a name | `swifttpa.accept` |
| `/tpdeny [player]` | `/tpno`, `/tdeny` | Deny a request — newest first without a name | `swifttpa.deny` |
| `/tpacancel` | `/tpcancel`, `/tpcl` | Cancel your outgoing request | `swifttpa.cancel` |
| `/tpatoggle` | `/tptoggle`, `/tptg` | Turn receiving requests on/off (persisted) | `swifttpa.toggle` |
| `/tpalist` | `/tplist`, `/tpls` | List pending requests with live countdowns | `swifttpa.list` |
| `/tpablock <player>` | `/tpblock`, `/tpbl` | Block a player's requests (persisted) | `swifttpa.block` |
| `/tpablock list` | — | Show your block list | `swifttpa.block` |
| `/tpaunblock <player>` | `/tpunblock`, `/tpub` | Unblock a player | `swifttpa.block` |

Every command is also reachable namespaced, e.g. `/swifttpa:tpa`, if another
plugin claims an alias. Tab completion is implemented for everything: online
players for `/tpa`, `/tpahere` and `/tpablock`; **your** pending request senders
for `/tpaccept` and `/tpdeny`; your block list for `/tpaunblock`.

### Main command

| Command | Short forms | Description | Permission |
| --- | --- | --- | --- |
| `/swifttpa help` | `/stpa help` (`h`, `?`) | Player help overview | everyone |
| `/swifttpa gui` | `/stpa gui` (`g`) | Open the requests GUI | `swifttpa.gui` |
| `/swifttpa info` | `/stpa info` (`i`, `stats`) | Your teleport statistics + plugin info | `swifttpa.stats` |
| `/swifttpa version` | `/stpa version` (`v`) | Version, platform and storage info | everyone |

### Admin commands

`/swifttpaadmin` — short forms `/stpaadmin` and `/tpaadmin`. Requires
`swifttpa.admin` (plus `swifttpa.admin.reload` for the reload).

| Sub-command | Alias | Description |
| --- | --- | --- |
| `/stpaadmin reload` | `rl` | Reload `config.yml` + `messages.yml`, apply live settings, migrate storage backend if `storage.type` changed |
| `/stpaadmin forcetp <player> <target>` | `ftp` | Instantly teleport `player` to `target` — no request, cooldown or warmup |
| `/stpaadmin forcetphere <player>` | `ftph` | Instantly teleport `player` to you |
| `/stpaadmin clear [player\|all]` | `c` | Cancel pending requests — everyone's or one player's |
| `/stpaadmin spy` | `s` | Toggle the live request-traffic feed (send/accept/deny/cancel/expire/teleport events) |
| `/stpaadmin stats [player]` | `st` | Show teleport statistics (yours or another player's) |
| `/stpaadmin info` | `i` | Plugin version, detected platform and storage backend |

`forcetp`, `clear`, `stats <player>` and `info` also work from the console.

## Permissions

### Player permissions

| Node | Default | Grants |
| --- | --- | --- |
| `swifttpa.tpa` | everyone | `/tpa` |
| `swifttpa.tpahere` | everyone | `/tpahere` |
| `swifttpa.accept` | everyone | `/tpaccept` |
| `swifttpa.deny` | everyone | `/tpdeny` |
| `swifttpa.cancel` | everyone | `/tpacancel` |
| `swifttpa.toggle` | everyone | `/tpatoggle` |
| `swifttpa.list` | everyone | `/tpalist` |
| `swifttpa.block` | everyone | `/tpablock`, `/tpaunblock` |
| `swifttpa.gui` | everyone | `/swifttpa gui` |
| `swifttpa.stats` | everyone | `/swifttpa info` |

### Bypass permissions

| Node | Default | Grants |
| --- | --- | --- |
| `swifttpa.bypass.cooldown` | op | Skip the request cooldown |
| `swifttpa.bypass.warmup` | op | Teleport instantly, no countdown |
| `swifttpa.bypass.toggle` | op | Request players who disabled requests |
| `swifttpa.bypass.blocked` | op | Request players who blocked you |
| `swifttpa.bypass.world` | op | Ignore cross-world and disabled-world rules |

### Admin permissions

| Node | Default | Grants |
| --- | --- | --- |
| `swifttpa.admin` | op | The whole `/stpaadmin` suite incl. spy |
| `swifttpa.admin.reload` | op | `/stpaadmin reload` |
| `swifttpa.*` | op | Everything above (wildcard with explicit children) |

## The requests GUI

Open it with `/stpa gui`. What you see:

- **Player heads** — one per pending request, newest first, with the sender's
  real skin (fetched asynchronously, never blocking the tick). Lore shows the
  request kind and the live expiry countdown.
  **Left-click** a head to accept, **right-click** to deny.
- **Toggle button** (lime/gray dye) — flips your personal
  receive-requests switch, exactly like `/tpatoggle`.
- **Cancel button** (barrier) — withdraws your own outgoing request, like
  `/tpacancel`.
- **Live updates** — the menu repaints itself whenever a request of yours is
  sent, answered, cancelled or expires, on every viewer's region thread.

## Sound effects

Every event has its own configurable sound (vanilla sound key, volume, pitch —
set the key to `""` to mute just that event, or flip `sounds.enabled` to mute
everything):

`request-sent` · `request-received` · `accepted` · `denied` · `cancelled` ·
`expired` · `teleported` · `warmup-tick` (pitch rises as the countdown runs) ·
`gui-open` · `gui-click` · `blocked` · `unblocked` · `toggle` · `error`

Keys are vanilla sound identifiers with or without the `minecraft:` namespace,
e.g. `entity.enderman.teleport`.

## Configuration

Everything below ships as documented defaults — see the heavily commented
`config.yml`.

### config.yml reference

| Path | Default | Meaning |
| --- | --- | --- |
| `storage.type` | `yaml` | `yaml` (one file per player in `playerdata/`) or `sqlite` (`swifttpa.db`) |
| `storage.autosave.enabled` | `true` | Periodic flush of cached data to disk |
| `storage.autosave.interval-seconds` | `300` | Autosave period, minimum 30 |
| `requests.expire-seconds` | `120` | Request lifetime; `0` = never expires |
| `requests.max-pending-per-target` | `5` | Queue cap per player; oldest is dropped with a notice |
| `requests.one-outgoing` | `true` | New request replaces the sender's old one (old target is notified) |
| `cooldowns.request-seconds` | `30` | Delay between sending requests; `0` = off |
| `warmup.seconds` | `3` | Teleport delay after accepting; `0` = instant |
| `warmup.cancel-on-move` | `true` | Moving to another block aborts the warmup |
| `warmup.cancel-on-damage` | `true` | Taking damage aborts the warmup |
| `warmup.action-bar` | `true` | Action-bar countdown during warmups |
| `teleport.allow-cross-world` | `true` | Requests across worlds |
| `teleport.disabled-worlds` | `[]` | World names that may not be teleported into |
| `features.spy` | `true` | Enables the `/stpaadmin spy` feed |
| `sounds.*` | see file | Master switch + 14 events with key/volume/pitch |
| `gui.title` | gradient title | MiniMessage inventory title |
| `gui.icons.*` | glass / barrier / dyes | Materials for filler, cancel and toggle buttons |

### messages.yml

Every user-facing string lives in `messages.yml` in
[MiniMessage](https://docs.advntr.dev/minimessage/format.html) format —
prefix, errors, request flow, warmup, lists, stats, admin output, both help
pages and all GUI names/lores. Placeholders use `{name}` (documented at the
top of the file), e.g. `{player}`, `{seconds}`, `{expires}`, `{sent}`. The
clickable chips are just a message too (`request-buttons`), so you can restyle
or translate the `[Accept] [Deny]` buttons themselves. Reload with
`/stpaadmin reload` — no restart needed.

## Storage: YAML or SQLite

| | YAML (default) | SQLite |
| --- | --- | --- |
| Files | `plugins/SwiftTPA/playerdata/<uuid>.yml` | `plugins/SwiftTPA/swifttpa.db` |
| Best for | small/medium servers, easy hand-editing | big networks, single-file backups |
| Writes | through to disk on every save + autosave | write-through under a lock (WAL mode) |

Persisted per player: request toggle state, block list (uuid + last known
name) and lifetime statistics (sent / accepted / denied / teleported). Data
loads asynchronously after join and saves on quit, on autosave and on
shutdown. Switching `storage.type` and reloading migrates the live cache to
the new backend.

## How a request flows

```text
/tpa Alex ──────────────► checks: self? target toggled off? blocked?
                          cooldown? cross-world? disabled world?
                              │
                              ▼
                    request queued (120s expiry)
                    Alex sees message + [Accept] [Deny]
                     │                    │
   /tpaccept or click ▼                    ▼ /tpdeny or click
        warmup 3s ◄──── accepts      both sides get a deny notice
     (action bar, ticks; moving or
      damage aborts it)                      │
              │                              ▼
              ▼                      nothing else happens
   teleportAsync on the anchor's
   region thread (Folia-safe)
              │
              ▼
   both sides get messages + sounds,
   statistics tick up, GUI repaints
```

## Folia notes

SwiftTPA is written for Folia's regionised multithreading from the ground up:

- All player interaction (messages, sounds, inventory, teleports) is dispatched
  to the player's **own region thread** via a reflection-based scheduler —
  one jar, no Folia classes needed at compile time.
- The teleport target's location is captured on **their** region thread, then
  the mover travels with Paper's async cross-region `teleportAsync`.
- Warmup countdowns run on per-entity timers; expiry sweeps and autosaves run
  on the global scheduler; all disk and database I/O is async.
- The shared request queue is a synchronized, Bukkit-free data structure
  (unit-tested without a server).
- `folia-supported: true` is set in `plugin.yml`.

## Building from source

Requirements: **JDK 25** (e.g. Temurin 25) and Maven 3.9+.

```bash
cd SwiftTPA
mvn -B clean package
# → SwiftTPA/target/SwiftTPA-1.0.0.jar
```

- `mvn -B clean verify` additionally runs the JUnit unit tests
  (`RequestStore`, `CooldownTracker`, `TimeParser`) and shades the
  `sqlite-jdbc` driver into the jar.
- The build uses `io.papermc.paper:paper-api:26.2.build.119-stable` with
  `provided` scope — the same coordinates the other plugins in this repository
  build against.

## Automated checks and CI

- `python3 SwiftTPA/tools/check_consistency.py` — offline checks (no JDK, no
  PyYAML needed): every command in `plugin.yml` is registered, every message
  key / config path / permission / sound event referenced from Java exists in
  the shipped resources, packages match folders and braces are balanced.
- The GitHub Actions workflow at `SwiftTPA/.github/workflows/build.yml` runs
  the consistency checks and `mvn -B -ntp clean verify` on Temurin 25, then
  uploads the built jar as an artifact (copy it to the repository root
  `.github/workflows/` to activate it — the same convention the other plugins
  in this repository use).
