# PlayerHeads 🗿

Give server admins the **player head of any Minecraft account** — including
accounts that have never joined your server — with one command.

Built for Minecraft **26.2** (Paper / Purpur / Folia), Java **25**, Maven.

```
/ph Notch          → your own head? no — Notch's head, 1×
/ph Notch 32       → 32 heads with Notch's skin
/playerheads reload
```

## What it does

- `/playerheads <player> [amount]` gives **yourself** one or more player heads
  showing the named account's skin.
- Works for **any real Mojang account** — the skin is resolved asynchronously
  from the session servers (Paper's profile cache is used first), so the
  command never lags or blocks the server thread.
- **Admin-only by default** (permission `playerheads.use`, default: `op`).
- Fully **Folia-safe**: lookups run on the async scheduler, the item hand-out
  runs on the receiving player's region thread. Also runs unchanged on Paper
  and Purpur.
- Configurable default/maximum amount, inventory-full behaviour, name pattern
  and every message (MiniMessage format).
- Pure Paper-API plugin: no NMS, no native code, no external libraries —
  works identically on **Linux, Windows and macOS**.

## Commands

| Command | Short form | Description |
| --- | --- | --- |
| `/playerheads <player> [amount]` | `/ph <player> [amount]` | Give yourself heads with that player's skin |
| `/playerheads reload` | `/ph reload` | Reload `config.yml` and `messages.yml` |
| `/playerheads help` | `/ph help` | Show the command help |

Aliases for `/playerheads`: `/playerhead`, `/phead`, `/ph`.

Notes:

- `amount` is optional (defaults to `settings.default-amount`, see below) and
  must be between 1 and `settings.max-amount`
  (or up to 65 536 with `playerheads.bypass-max`).
- Heads stack to 64; larger amounts are automatically split into stacks, and
  surplus heads are dropped at your feet if your inventory is full
  (configurable).
- Tab completion suggests online players, `reload` and common amounts.
- If a player is actually named *reload* (case-insensitive), use
  `/playerheads reload 1` — the argument makes it unambiguous.

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `playerheads.use` | `op` | Use `/playerheads <player> [amount]` (the command is admin-only) |
| `playerheads.reload` | `op` | Use `/playerheads reload` |
| `playerheads.bypass-max` | `op` | Ignore `settings.max-amount` (hard cap 65 536) |
| `playerheads.*` | `op` | All of the above |

## Configuration

`plugins/PlayerHeads/config.yml`:

```yaml
settings:
  default-amount: 1      # amount when the argument is omitted
  max-amount: 64         # per-command upper bound (bypass: playerheads.bypass-max)
  drop-when-full: true   # false = surplus heads are not given when the inventory is full
  name-pattern: "^[A-Za-z0-9_]{1,16}$"   # vanilla account-name format
```

`plugins/PlayerHeads/messages.yml` — every message in
[MiniMessage](https://docs.advntr.dev/minimessage/format.html) format with a
configurable `prefix` and the placeholders
`{player}`, `{name}`, `{amount}`, `{max}`, `{input}`, `{count}`.

Both files are (re)created when missing and hot-reload with
`/playerheads reload`.

## How skin lookup works

1. The name is validated against `settings.name-pattern` (no request is made
   for obviously invalid names).
2. A `PlayerProfile` is created and completed **asynchronously** (Paper first
   checks its internal profile cache — online players and recently seen
   players resolve instantly without a web request, otherwise Mojang's
   session servers are queried).
3. If no account exists you get a friendly `player-not-found` message; the
   main/region threads are never blocked.
4. The finished heads are added to your inventory on your region thread
   (Folia-safe), with surplus dropped on the ground when full.

## Building

Requires **JDK 25** and **Maven**:

```bash
cd PlayerHeads
mvn -B clean package
# → target/PlayerHeads-1.0.0.jar
```

The GitHub Actions workflow `.github/workflows/playerheads.yml` builds the
plugin on every push/PR (Temurin 25 + Maven against
`io.papermc.paper:paper-api:26.2.build.115-stable`), runs the offline smoke
test **and** boots a real Paper 26.2 server with the plugin to verify it
enables and its commands respond. The built jar is attached as a workflow
artifact.

### Offline smoke test

```bash
javac -d smoke-classes \
  PlayerHeads/smoke/SmokeTest.java \
  PlayerHeads/src/main/java/dev/superseller/playerheads/util/*.java
java -cp smoke-classes dev.superseller.playerheads.smoke.SmokeTest
```

## Installation

1. Drop `PlayerHeads-1.0.0.jar` into your server's `plugins/` folder
   (Paper / Purpur / Folia 26.2, Java 25+).
2. Restart the server.
3. As an op: `/ph <player> [amount]`.

## FAQ

**Does it work on offline-mode servers?**
Yes. Heads for premium accounts resolve normally via the session servers.
Account names that only exist on your offline-mode server (not at Mojang)
cannot have a skin resolved and are reported as not found.

**Can regular players use it?**
Not by default — `playerheads.use` defaults to `op`. Grant it explicitly
(e.g. via a permissions plugin) if you want a donor/vote perk.

**Why does the first head for an unknown player take a moment?**
The skin is fetched from Mojang's session servers asynchronously so the
server never lags; the response usually arrives within a second.

**Does it persist anything?**
No. PlayerHeads stores only `config.yml` and `messages.yml`; there is no
database, cooldown, or player tracking.

## Project layout

```
PlayerHeads/
├── pom.xml                       Maven build (Java 25, paper-api 26.2)
├── src/main/resources/
│   ├── plugin.yml                command/permission registration (Folia flag)
│   ├── config.yml                default configuration
│   └── messages.yml              default messages (MiniMessage)
├── src/main/java/dev/superseller/playerheads/
│   ├── PlayerHeadsPlugin.java    plugin entry point
│   ├── command/                  command + tab completion, permission nodes
│   ├── config/                   config.yml / messages.yml loading
│   ├── head/                     async profile lookup + item hand-out
│   ├── scheduler/                Folia-safe task scheduling
│   └── util/                     pure helpers (name/amount parsing, placeholders)
└── smoke/SmokeTest.java          offline unit test for the pure logic
```
