# ConnectedTools — Professional Item-to-Block Redstone Connector

A professional-grade Minecraft plugin for **26.2** (Paper / Purpur / Folia) that connects items and tools to nearby blocks using **vanilla-style toggling** (levers, buttons, doors, observers, comparators, repeaters, redstone wire, trapdoors, fence gates). Includes full commands with **tab completers**, a management **GUI**, complete **permissions**, YAML persistence, and a public **API**.

## Features (100000x Better)

- **Vanilla redstone mechanism:** Toggles real block states instead of placing temporary redstone blocks.
  - Levers (`LEVER`): toggle powered state
  - Buttons (`*_BUTTON`): press for 2 ticks
  - Doors (`*_DOOR`): open/close
  - Observers (`OBSERVER`): trigger with temporary pulse
  - Comparators / Repeaters: toggle powered
  - Redstone wire (`REDSTONE_WIRE`): set to 15 then revert
  - Redstone torches (`REDSTONE_TORCH`, `REDSTONE_WALL_TORCH`): toggle lit
  - Trapdoors (`*_TRAPDOOR`): open/close
  - Fence gates (`*_FENCE_GATE`): open/close
- **Tab completers:** Every subcommand supports tab completion (`connect`, `disconnect`, `list`, `info`, `gui`, `reload`, plus aliases `c`, `d`, `l`, `i`).
- **Commands with aliases:** `/connectedtools` (`/ct`, `/conn`, `/conntools`).
- **Full permissions with children:** `connectedtools.connect`, `.disconnect`, `.list`, `.info`, `.gui`, `.reload`, `.all`.
- **Persistence:** Connections saved to `plugins/ConnectedTools/connections.yml`.
- **GUI:** Professional inventory-based management (`/ct gui`) with colors and sound feedback.
- **Public API:** `ConnectionAPI` for external plugins.
- **Cross-platform scheduling:** `PlatformScheduler` for Folia-safe scheduling.
- **Service architecture:** `ConnectionService`, `RedstoneService`, `PluginSettings`, `ConnectionStore`.
- **Utilities:** `Colors`, `Text`, `ItemBuilder`, `BlockUtil`.
- **Professional build:** Maven, Java 25, Paper 26.2 API, `messages.yml` for localization.

## Requirements

- **Server:** Paper 26.2, Purpur 26.2, or Folia 26.2
- **Java:** JDK 25 (verified via [mcreference.com](https://mcreference.com/compatibility/26.2) and Paper docs)
- **Build:** Maven

## Installation

```bash
mvn -B clean package
```
Place the JAR in `plugins/` and restart.

## Commands & Aliases

| Full | Short | Description | Permission |
|---|---|---|---|
| `/connectedtools` | `/ct`, `/conn`, `/conntools` | Main command (help) | — |
| `/ct connect` | `/ct c` | Enter binding mode | `connectedtools.connect` |
| `/ct disconnect` | `/ct d` | Unbind held item | `connectedtools.disconnect` |
| `/ct list` | `/ct l` | List connections | `connectedtools.list` |
| `/ct info` | `/ct i` | Show info | `connectedtools.info` |
| `/ct gui` | — | Open GUI | `connectedtools.gui` |
| `/ct reload` | — | Reload config | `connectedtools.reload` |

All subcommands have tab completers.

## Permissions

| Permission | Default |
|---|---|
| `connectedtools.connect` | `true` |
| `connectedtools.disconnect` | `true` |
| `connectedtools.list` | `true` |
| `connectedtools.info` | `true` |
| `connectedtools.gui` | `true` |
| `connectedtools.reload` | `op` |
| `connectedtools.all` | `false` (includes all above) |

## Mechanism (Plan Verified Before Build)

1. **Bind:** `/ct c` → hold item → left-click any block within 10 blocks.
2. **Emit:** Right-click with bound item → `RedstoneService.emitPulse()` toggles the target block's state directly using native `BlockData` APIs (levers powered/unpowered, buttons pressed/released, doors open/closed, etc.). No temporary redstone blocks placed.
3. **Persist:** `ConnectionStore` writes to YAML file.
4. **Manage:** `/ct gui` opens a professional inventory interface.
5. **API:** `ConnectionAPI.getConnections()`, `.addConnection()`, `.removeConnection()`.

## Architecture (Inspired by Subscriptions Plugin)

```
ConnectedTools/
├── src/main/java/dev/superseller/connectedtools/
│   ├── ConnectedToolsPlugin.java        (main, services, listeners)
│   ├── api/ConnectionAPI.java            (public API)
│   ├── command/ConnectedToolsCommand.java (TabCompleter + CommandExecutor)
│   ├── config/PluginSettings.java         (config + messages loader)
│   ├── gui/GuiManager.java                (professional menus)
│   ├── gui/MenuHolder.java                (inventory tracking)
│   ├── listener/PlayerInteractListener.java (binding + activation)
│   ├── listener/GUIListener.java           (inventory clicks)
│   ├── model/Connection.java               (connection data model)
│   ├── model/ConnectionStore.java         (YAML persistence)
│   ├── service/ConnectionService.java      (business logic)
│   ├── service/RedstoneService.java        (vanilla toggling)
│   ├── scheduler/PlatformScheduler.java    (Folia-safe scheduling)
│   └── util/
│       ├── Colors.java
│       ├── Text.java
│       ├── ItemBuilder.java
│       └── BlockUtil.java                  (canToggle, toggleBlock)
├── src/main/resources/
│   ├── plugin.yml
│   ├── config.yml
│   └── messages.yml
└── smoke/SmokeTest.java
```

## Build

```bash
mvn -B clean package
```

Output: `target/ConnectedTools-1.0.0.jar`

## Compatibility

- **Paper 26.2:** Primary target (`api-version: '26.2'`)
- **Purpur 26.2:** Fully supported (inherits Paper API)
- **Folia 26.2:** Supported (`folia-supported: true`, `PlatformScheduler`)
- **Operating systems:** Linux, Windows, macOS (pure Java / Bukkit API, no native code)
- **Java:** JDK 25 (verified for Minecraft 26.2)

## License

Same as the parent repository (`SuperSeller100X/MinecraftPlugins`).
