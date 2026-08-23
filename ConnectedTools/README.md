# ConnectedTools — Item-to-Block Redstone Connector

A professional Minecraft plugin for **26.2** (Paper / Purpur / Folia) that connects items and tools to nearby blocks. Once bound, using the item emits a temporary redstone pulse at the connected block. Includes commands, a management GUI, full permissions, and cross-platform support.

## Features

- **Command + GUI binding:** `/ct connect` (or `/ct c`) puts you in binding mode; left-click any block within 10 blocks to bind the item in your hand.
- **Redstone pulse:** Right-click with a bound item to emit a 2-tick redstone pulse at the bound location (works with redstone dust, comparators, repeaters, observers, redstone blocks, and any other block via temporary adjacent pulse).
- **Disconnect:** `/ct disconnect` (or `/ct d`) unbinds the held item.
- **List & Info:** `/ct list` (`/ct l`) shows all connections; `/ct info` (`/ct i`) shows details for the held item.
- **GUI Management:** `/ct gui` opens an inventory-based interface to view and disconnect connections by clicking.
- **Permissions:** Per-action permissions (`connect`, `disconnect`, `list`, `info`, `gui`, `reload`) plus `connectedtools.all` for full access.
- **Cross-platform:** Works on Linux, Windows, and macOS (pure Java / Bukkit API).

## Requirements

- **Server:** Paper 26.2, Purpur 26.2, or Folia 26.2
- **Java:** JDK 25 (required by Minecraft 26.2)
- **Build tool:** Maven

## Installation

1. Download or build the plugin JAR (`mvn -B clean package` in this folder).
2. Place the JAR in your server's `plugins/` directory.
3. Restart or reload the server.
4. Configure `plugins/ConnectedTools/config.yml` if needed.

## Commands

| Full Command | Short Alias | Description | Default Permission |
|---|---|---|---|
| `/connectedtools` | `/ct` | Main command (shows help) | — |
| `/ct connect` | `/ct c` | Enter binding mode for held item | `connectedtools.connect` |
| `/ct disconnect` | `/ct d` | Unbind held item | `connectedtools.disconnect` |
| `/ct list` | `/ct l` | List all your connections | `connectedtools.list` |
| `/ct info` | `/ct i` | Show info for held item | `connectedtools.info` |
| `/ct gui` | — | Open connection management GUI | `connectedtools.gui` |
| `/ct reload` | — | Reload plugin config (admin) | `connectedtools.reload` |

Aliases: `/conn` and `/conntools` also work.

## Permissions

| Permission | Description | Default |
|---|---|---|
| `connectedtools.connect` | Connect items to blocks | `true` |
| `connectedtools.disconnect` | Disconnect items | `true` |
| `connectedtools.list` | List connections | `true` |
| `connectedtools.info` | View connection info | `true` |
| `connectedtools.gui` | Open management GUI | `true` |
| `connectedtools.reload` | Reload config | `op` |
| `connectedtools.all` | Full access to all features | `false` |

## How It Works (Plan)

1. **Bind:** Player holds an item, runs `/ct connect`, then left-clicks a block within 10 blocks. The plugin records the block's world, coordinates, and type, keyed to the item.
2. **Emit:** When the player right-clicks with the bound item, the plugin finds the recorded block. If the block is a redstone component (dust, comparator, repeater, observer, torch, block), the plugin temporarily modifies its powered state or places a temporary redstone block nearby for 2 ticks, creating a redstone pulse without destroying any blocks.
3. **Manage:** The `/ct gui` opens a paginated inventory showing all bound items with their target block info; clicking an item disconnects it.

## Plugin Folder Structure

```
ConnectedTools/
├── pom.xml              # Maven build (Java 25, Paper 26.2)
├── src/
│   ├── main/
│   │   ├── java/dev/superseller/connectedtools/
│   │   │   ├── ConnectedToolsPlugin.java
│   │   │   ├── PluginConfig.java
│   │   │   ├── command/
│   │   │   │   └── ConnectedToolsCommand.java
│   │   │   ├── gui/
│   │   │   │   ├── ConnectionGUI.java
│   │   │   │   └── ...
│   │   │   ├── listener/
│   │   │   │   ├── PlayerInteractListener.java
│   │   │   │   └── GUIListener.java
│   │   │   ├── model/
│   │   │   │   ├── Connection.java
│   │   │   │   └── ConnectionStore.java
│   │   │   └── util/
│   │   └── resources/
│   │       ├── plugin.yml
│   │       └── config.yml
│   └── test/ ...
└── README.md (this file)
```

## Build

```bash
mvn -B clean package
```

The JAR is produced in `target/ConnectedTools-1.0.0.jar`.

## Compatibility

- **Paper:** Fully supported (primary target)
- **Purpur:** Supported (uses Paper API; Purpur inherits it)
- **Folia:** Supported (`folia-supported: true` in `plugin.yml`)
- **Operating systems:** Linux, Windows, macOS (Java cross-platform)

## License

Same as the parent repository. See root `README.md` for details.
