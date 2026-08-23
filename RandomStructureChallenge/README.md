# RandomStructureChallenge

A **random vanilla-structure challenge** for Minecraft **26.2** (Paper / Purpur / Folia).

Run `/challenge start`, type an interval like `60`, and every 60 seconds a **real Minecraft structure** is placed on **every online player** — the same way `/place structure` works. A custom timer sits **over the hotbar**, and a BossBar on top shows **seconds left until next structure** with a bar that is the remaining percent (a 100-second interval drops 1% every second).

Built with **Java 25** and **Maven**. Pure Java — identical on Linux, Windows and macOS.

```
/challenge start          → type 60 in chat
/ch s 1m                  → start immediately, drop every 60s
/ch p                     → pause
/ch r                     → resume
/ch x                     → stop
```

## What it does

1. An operator runs `/challenge start` (or `/ch s`).
2. They type the interval in chat (`60`, `90s`, `1m`, `1:30`, `2 minutes`) — or pass it as `/challenge start 60`.
3. A **BossBar** titled `seconds left until next structure: N` appears for everyone. The bar fill is the remaining percent.
4. A **gradient countdown** is drawn over the hotbar (action bar), with a `▰▱` meter that turns red in the last seconds.
5. When the timer hits zero, the plugin picks a **random registered structure** (villages, temples, shipwrecks, trial chambers, ancient cities, fortresses, end cities, datapack structures, …) and runs vanilla placement **on every online player**, centered on them.
6. A small **safe pocket** is carved / the player is shifted so they are not left suffocating inside the new build.
7. The timer resets and the next wave starts.

There is **one global challenge**. Structures always land on **every** currently online player (not only the starter). The challenge stays in memory until `/challenge stop` — it does not persist across restarts.

## Requirements

| | |
|---|---|
| Server | Paper 26.2, Purpur 26.2, or Folia 26.2 |
| Java | JDK **25** (required by Minecraft / Paper 26.2) |
| Build | Maven (`mvn -B clean package`) |
| OS | Linux, Windows, macOS (no native code) |

Vanilla `/place structure` must be available (it is, on an unmodified Paper/Purpur/Folia 26.2 server). The plugin dispatches it as console:

```
execute in <world> run place structure <id> <x> <y> <z>
```

`/place structure` can refuse a structure when the chunk/biome is invalid. The plugin then retries other random structures (default 8 attempts).

## Installation

1. Build or download `RandomStructureChallenge-1.0.0.jar`.
2. Drop it in the server `plugins/` folder.
3. Restart the server (Java 25).
4. As an operator: `/challenge start` → type `60`.

## Commands

Main command: `/challenge`  
Aliases: `/ch`, `/rsc`, `/rschallenge`, `/randomstructurechallenge`

| Command | Short | Description | Permission |
|---|---|---|---|
| `/challenge start` | `/ch s` | Start, then type the interval in chat | `randomstructurechallenge.start` |
| `/challenge start <interval>` | `/ch s 60` | Start immediately | `randomstructurechallenge.start` |
| `/challenge stop` | `/ch x` / `/ch end` | Stop the challenge | `randomstructurechallenge.stop` |
| `/challenge pause` | `/ch p` | Freeze the timer (BossBar stays) | `randomstructurechallenge.pause` |
| `/challenge resume` | `/ch r` | Unfreeze the timer | `randomstructurechallenge.resume` |
| `/challenge status` | `/ch i` / `/ch stat` / `/ch info` | State, starter, time left, last structure | `randomstructurechallenge.status` |
| `/challenge reload` | `/ch rl` | Reload `config.yml` and `messages.yml` | `randomstructurechallenge.reload` |
| `/challenge help` | `/ch h` | Show this help | everyone |

### Interval formats

`60` · `90s` · `1m` · `1m30s` · `1:30` · `1 minute` · `2 hours`

Bounds come from `settings.min-interval-seconds` / `max-interval-seconds` (default 5–3600).

While the plugin is waiting for chat input you can type `cancel` to abort. The prompt expires after `settings.input-timeout-seconds` (default 30).

Tab completion suggests subcommands (filtered by permission) and common intervals.

## Permissions

| Node | Default | Description |
|---|---|---|
| `randomstructurechallenge.start` | `op` | Start the challenge / enter an interval |
| `randomstructurechallenge.stop` | `op` | Stop the challenge |
| `randomstructurechallenge.pause` | `op` | Pause the timer |
| `randomstructurechallenge.resume` | `op` | Resume the timer |
| `randomstructurechallenge.status` | `true` | View `/challenge status` |
| `randomstructurechallenge.reload` | `op` | Reload configuration |
| `randomstructurechallenge.*` | `op` | All of the above |

Starting a challenge can completely reshape the terrain around every player. Keep `start` / `stop` on operators unless you trust the whole server.

## Timer

**BossBar (top of the screen)**  
Title: `seconds left until next structure: 47`  
Fill: remaining ÷ interval (so a 100-second interval loses 1% every second).  
Color shifts purple → yellow → red as time runs out. Paused challenges stay yellow and prefix the title with `PAUSED —`.

**Action bar (over the hotbar)**  
A gradient `mm:ss` clock, a `▰▱` meter and the remaining percent. The last few seconds (default 5) switch to a red warning style and tick a note-block sound.

**On spawn**  
Title `STRUCTURE DROP`, subtitle with the structure name, a wither-spawn sound, and a chat broadcast.

## Configuration

Created on first start at `plugins/RandomStructureChallenge/config.yml`:

```yaml
settings:
  min-interval-seconds: 5
  max-interval-seconds: 3600
  input-timeout-seconds: 30
  same-structure-for-all: true   # one structure per wave, every player
  include-spectators: true
  load-chunk-radius: 4
  placement-retries: 8
  safe-pocket: true
  worlds: []                     # empty = all worlds
  structure-whitelist: []        # empty = every registered structure
  structure-blacklist: []        # e.g. [minecraft:ancient_city]
```

`messages.yml` is MiniMessage. Every string — prefix, BossBar title, action-bar templates, help — is editable and hot-reloads with `/challenge reload`.

## How structures are chosen

On each wave the plugin reads `RegistryKey.STRUCTURE` (Paper 26.2) so **any** structure the server knows about is eligible — vanilla plus datapacks. If the registry is unavailable it falls back to the built-in 26.2 list (villages, temples, shipwrecks, trial chambers, ancient cities, nether fortresses, end cities, ruined portals, …).

Placement is vanilla `/place structure`. That includes jigsaw expansion (full villages, trail ruins, trial chambers) and the structure’s entities / loot, exactly like running the command yourself.

After placement, `safe-pocket` looks for air with a solid floor or carves a 3×3×3 glass-floored pocket so the host player is not buried.

## Folia

`plugin.yml` sets `folia-supported: true`.

- The 1-second countdown runs on the **global region** scheduler.
- Action-bar / BossBar / sounds / titles run on each player’s **entity** scheduler.
- `/place structure` is dispatched on the player’s **entity / region** thread.

Large structures can span several Folia regions. That is a vanilla-command limitation, not extra plugin world edits.

## Building

Requires **JDK 25** and **Maven**, with access to [repo.papermc.io](https://repo.papermc.io/repository/maven-public/):

```bash
cd RandomStructureChallenge
mvn -B clean package
# → target/RandomStructureChallenge-1.0.0.jar
```

The jar is compiled against `io.papermc.paper:paper-api:26.2.build.115-stable` (`provided` — not shaded).

### Offline smoke test

The interval parser, percent bar and placeholders have no Bukkit dependency:

```bash
javac -d smoke-classes \
  RandomStructureChallenge/smoke/SmokeTest.java \
  RandomStructureChallenge/src/main/java/dev/superseller/randomstructurechallenge/util/*.java
java -cp smoke-classes dev.superseller.randomstructurechallenge.smoke.SmokeTest
```

## Project layout

```
RandomStructureChallenge/
├── pom.xml
├── README.md
├── smoke/SmokeTest.java
└── src/main/
    ├── java/dev/superseller/randomstructurechallenge/
    │   ├── RandomStructureChallengePlugin.java
    │   ├── command/          /challenge + TabCompleter + permissions
    │   ├── challenge/        session, catalog, /place structure, safe pocket, timer
    │   ├── config/           config.yml + MiniMessage messages
    │   ├── listener/         chat interval input, join/quit
    │   ├── scheduler/        Folia-safe Paper/Bukkit scheduler
    │   └── util/             duration parser, percent bar, placeholders
    └── resources/
        ├── plugin.yml
        ├── config.yml
        └── messages.yml
```

## FAQ

**Does this generate a full village or just one house?**  
Full configured structures, via `/place structure`. Jigsaw structures (villages, trail ruins, trial chambers, mansions, …) expand like they would if you typed the command.

**Can it wreck the spawn?**  
Yes. That is the challenge. Restrict `randomstructurechallenge.start` and optionally set `settings.worlds` / `structure-blacklist`.

**What if `/place structure` fails because of biome?**  
The plugin retries other random structures up to `placement-retries` times.

**Does it save anything?**  
Only `config.yml` and `messages.yml`. The running challenge is RAM-only.

**Spigot?**  
Not supported. The plugin uses the Paper 26.2 API, Adventure MiniMessage, and Folia schedulers.

## License

Same as the parent repository.
