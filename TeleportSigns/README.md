# TeleportSigns

Bind a destination to a written sign and right-click it to teleport. Built for **Minecraft 26.2** on **Paper**, **Purpur**, and **Folia**, compiled with **Java 25** and **Maven**.

Write any text you like on a sign (standing, wall, or hanging). Look at that sign and run `/ts 100 64 -200`. Anyone who right-clicks the sign is teleported to those coordinates.

## Features

- **Look-at binding:** `/ts [world] <x> <y> <z> [yaw] [pitch]` attaches a destination to the sign you are facing (default 6 blocks).
- **Relative coords:** `~`, `~10`, `~-3.5` are resolved from your current position.
- **Cross-world warps:** optional world name, plus optional yaw/pitch so players face the right way.
- **Right-click teleport:** uses Paper/Folia `teleportAsync` (never the broken Folia `Entity#teleport`).
- **OP creates, everyone uses:** binding is `op` by default; using a sign is granted to all players.
- **Safety:** rejects void, lava, fire, and destinations with no solid floor (bypass permission available).
- **Cooldown & warmup:** configurable; warmup cancels if you move or take damage.
- **Optional cost:** Vault / VaultUnlocked when present; per-sign price via `/ts cost`.
- **List & info:** `/ts list` nearby signs, `/ts info` on the looked-at sign.
- **Persistence:** destination is stored on the sign (`PersistentDataContainer`) and indexed in `signs.yml`. Breaking the sign removes the warp.
- **Wax on bind:** the sign is waxed so a normal right-click teleports instead of opening the editor. Sneak-click still lets you dye / interact.
- **Tab completion** for every argument (subcommands, worlds, `~`, your coordinates, costs, radii).
- **Short aliases** for every command.
- **Cross-platform:** Linux, Windows, and macOS (pure Java, `File` APIs, UTF-8).

## Requirements

| Piece | Version |
|---|---|
| Server | Paper 26.2, Purpur 26.2, or Folia 26.2 |
| Java | **25** (required by Minecraft / Paper 26.2) |
| Build | Maven 3.9+ |
| Optional | Vault or VaultUnlocked (only if you set a cost) |

Verified against the Paper 26.2 developer docs (`paper-api` `26.2.build.115-stable`, `api-version: '26.2'`, Java toolchain 25) and Folia’s requirement to set `folia-supported: true` and use `teleportAsync`.

## Installation

1. Build or download `TeleportSigns-1.0.0.jar`.
2. Place it in the server `plugins/` folder.
3. Restart the server.
4. Edit `plugins/TeleportSigns/config.yml` and `messages.yml` if you want, then `/ts reload`.

## Commands

Root command: `/teleportsigns`  
Aliases: `/ts` `/tpsign` `/tpsigns` `/telesign`

| Full command | Short | Description | Permission |
|---|---|---|---|
| `/teleportsigns` | `/ts` | Show help | — |
| `/ts help` | `/ts ?` | Show help | — |
| `/ts [world] <x> <y> <z> [yaw] [pitch]` | same | Bind the looked-at sign | `teleportsigns.create` |
| `/ts set [world] <x> <y> <z> [yaw] [pitch]` | `/ts s` | Same as above | `teleportsigns.create` |
| `/ts here` | `/ts .` | Bind to your current location (incl. look) | `teleportsigns.create` |
| `/ts remove` | `/ts r` | Unbind the looked-at sign | `teleportsigns.remove` |
| `/ts unset` | `/ts u` | Alias of remove | `teleportsigns.remove` |
| `/ts info` | `/ts i` | Show destination / cost / creator | `teleportsigns.info` |
| `/ts list [radius]` | `/ts l` | List nearby teleport signs | `teleportsigns.list` |
| `/ts cost <amount>` | `/ts c` | Set a per-sign teleport price | `teleportsigns.cost` |
| `/ts reload` | `/ts rl` | Reload config, messages, and index | `teleportsigns.reload` |

Examples:

```
/ts 100 64 -200
/ts s world_nether 0 80 0 90 0
/ts ~ ~1 ~
/ts here
/ts i
/ts l 48
/ts c 25
/ts r
```

## Permissions

Default: **anyone can use** a teleport sign; **only operators can create / manage** them.

| Permission | Default | Description |
|---|---|---|
| `teleportsigns.use` | `true` | Right-click a bound sign to teleport |
| `teleportsigns.create` | `op` | Bind a destination (`/ts`, `/ts set`, `/ts here`) |
| `teleportsigns.remove` | `op` | Unbind a sign |
| `teleportsigns.info` | `op` | View sign info |
| `teleportsigns.list` | `op` | List nearby signs |
| `teleportsigns.cost` | `op` | Set a per-sign price |
| `teleportsigns.reload` | `op` | Reload configuration |
| `teleportsigns.bypass.cooldown` | `op` | Skip cooldown |
| `teleportsigns.bypass.warmup` | `op` | Skip warmup |
| `teleportsigns.bypass.cost` | `op` | Skip economy charge |
| `teleportsigns.bypass.safety` | `op` | Allow unsafe destinations |
| `teleportsigns.admin` | `op` | All admin permissions above |
| `teleportsigns.*` | `op` | Everything, including `use` |

## Configuration

`plugins/TeleportSigns/config.yml`:

- `settings.max-look-distance` — how far `/ts` looks for a sign (default `6`)
- `settings.list-radius` / `list-max` — `/ts list` defaults
- `settings.wax-on-bind` — wax the sign so right-click does not open the editor
- `settings.sneak-to-edit` — sneaking skips teleport so you can dye the sign
- `settings.sound` / `particle` — feedback after a successful teleport
- `safety.*` — void / lava / fire / solid-floor checks
- `cooldown.seconds` — delay between teleports (`0` disables)
- `warmup.seconds` — stand-still delay (`0` = instant)
- `economy.enabled` — `auto` / `true` / `false`
- `economy.default-cost` — used for newly bound signs
- `economy.missing-policy` — `allow` (free if Vault is missing) or `deny`

All chat strings live in `messages.yml` (MiniMessage).

## How it works

1. A player writes text on a sign (any wood, wall or hanging).
2. An operator looks at the sign and runs `/ts <x> <y> <z>` (or `/ts here`).
3. The destination is written into the sign’s persistent data and indexed in `signs.yml`.
4. A player with `teleportsigns.use` right-clicks the sign.
5. Cooldown, optional warmup, optional cost, and the safety check run.
6. The player is moved with `teleportAsync` on the destination region (Folia-safe).

Destroyed signs (break, burn, explode) drop their stored warp automatically.

## Build

Requires **JDK 25** and Maven, with access to `https://repo.papermc.io/repository/maven-public/`.

```bash
cd TeleportSigns
mvn -B clean package
```

Output: `target/TeleportSigns-1.0.0.jar`

Offline compile (when Maven Central / PaperMC are unreachable) uses the bundled API stubs and the Eclipse compiler from `AtTag/libs/ecj.jar`. Requires a Java 25 runtime:

```bash
cd TeleportSigns
JAVA_BIN=/path/to/java ./build.sh
```

Offline logic check (no Paper dependency):

```bash
javac -d smoke-classes \
  smoke/SmokeTest.java \
  src/main/java/dev/superseller/teleportsigns/util/*.java \
  src/main/java/dev/superseller/teleportsigns/command/DestinationParser.java \
  src/main/java/dev/superseller/teleportsigns/model/*.java \
  src/main/java/dev/superseller/teleportsigns/service/SafetyChecker.java
java -cp smoke-classes dev.superseller.teleportsigns.smoke.SmokeTest
```

## Compatibility

| Platform | Status |
|---|---|
| Paper 26.2 | Supported (primary) |
| Purpur 26.2 | Supported (Paper API) |
| Folia 26.2 | Supported (`folia-supported: true`, region/entity schedulers, `teleportAsync`) |
| Linux / Windows / macOS | Supported |

## Project layout

```
TeleportSigns/
├── pom.xml
├── README.md
├── smoke/SmokeTest.java
├── .github/workflows/build.yml
└── src/main/
    ├── java/dev/superseller/teleportsigns/
    │   ├── TeleportSignsPlugin.java
    │   ├── command/          # /ts + TabCompleter + coordinate parser
    │   ├── config/           # config.yml + MiniMessage
    │   ├── economy/          # optional Vault / VaultUnlocked
    │   ├── listener/         # click + break/warmup
    │   ├── model/            # destination + PDC codec
    │   ├── scheduler/        # Folia-safe schedulers
    │   ├── service/          # teleport + safety
    │   ├── storage/          # PDC + signs.yml
    │   └── util/
    └── resources/
        ├── plugin.yml
        ├── config.yml
        └── messages.yml
```

## License

Same as the parent repository.
