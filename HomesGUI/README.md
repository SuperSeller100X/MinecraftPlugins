# HomesGUI 🏠

A personal homes plugin with a clickable inventory menu, direct teleport commands, configurable paid home slots, and staff management. Players begin with four available slots—**home 1, home 2, home 3, and home 4**—and can save a location to any unlocked slot.

Built for **Minecraft Java Edition 26.2** on **Paper, Purpur, and Folia**, using **Java 25** and **Maven**.

## Features

- `/home` opens a paginated GUI; `/home <slot>` teleports directly.
- Set, update, and delete homes from commands or the GUI.
- Homes 1–4 are free by default; players can buy more through a Vault economy provider.
- Configurable geometric pricing. Defaults: home 5 costs `$1,000`, home 6 costs `$1,250`, home 7 costs `$1,562.50`, and so on (each paid slot costs 1.25× the previous one).
- Admin commands to inspect players, grant or remove bonus slots, set limits, clear/reset data, delete locations, and teleport to a saved home.
- Permissions, short command aliases, and tab completion for public and admin commands.
- Configurable GUI title, rows, page size, icons, names, lore, control positions, messages, sounds, pricing, limits, and storage cadence.
- UUID-keyed YAML data using the requested `UUID -> homes -> slot -> world/x/y/z/yaw/pitch` shape.
- Folia-aware entity and asynchronous schedulers; teleports use `teleportAsync`.
- UTF-8 file handling and atomic replacement where supported for Linux, Windows, and macOS.

## Requirements and compatibility

| Requirement | Version |
|---|---|
| Minecraft | 26.2 |
| Server | Paper, Purpur, or Folia 26.2 |
| Java runtime and compiler release | 25 |
| Build tool | Maven 3.9+ |
| Economy | Optional Vault or VaultUnlocked service + a registered economy provider |

The plugin compiles against Paper API `26.2.build.132-stable`. Vault is an optional reflective integration; it is not bundled in the jar and is not needed for homes, commands, or the GUI to work.

## Build

From the repository root:

```bash
cd HomesGUI
mvn -B clean verify
```

The packaged plugin is:

```text
HomesGUI/target/HomesGUI-1.0.0.jar
```

`mvn clean verify` also runs the unit tests for price progression and location validation. A GitHub Actions workflow template is included at `HomesGUI/.github/workflows/build.yml`; copy it to the repository root `.github/workflows/` to activate it. Put the built jar in the server's `plugins/` directory and restart the server.

## Player commands

Every root command has a short alias, and every multi-word action also has a short subcommand form.

| Command | Short form | Description | Permission |
|---|---|---|---|
| `/home` | `/h` | Open the homes GUI | `homesgui.gui` |
| `/homes` | `/hs` | Open the homes GUI | `homesgui.gui` |
| `/home <slot>` | `/h <slot>` | Teleport to a saved home | `homesgui.use` |
| `/home gui` | `/h g` | Open the GUI | `homesgui.gui` |
| `/home set [slot]` | `/h s [slot]` | Save/update the current location; with no slot, use the first empty unlocked slot | `homesgui.set` |
| `/home delete <slot>` | `/h d <slot>` | Delete a saved home | `homesgui.delete` |
| `/home buy [amount]` | `/h b [amount]` | Buy one or more consecutive slots | `homesgui.buy` |
| `/home price [amount]` | `/h p [amount]` | Preview the cost of the next purchase(s) | `homesgui.price` |
| `/home info` | `/h i` | Show saved/available slots, next price, and economy provider | `homesgui.info` |
| `/home help` | `/h help` | Show player command help | None |
| `/sethome [slot]` | `/sh [slot]` | Save/update a home (defaults to the first empty unlocked slot) | `homesgui.set` |
| `/delhome <slot>` | `/dh <slot>` | Delete a saved home | `homesgui.delete` |
| `/buyhome [amount]` | `/bh [amount]` | Buy the next slot(s); amount defaults to 1 | `homesgui.buy` |

`/home` with no arguments opens the menu. A numeric first argument teleports directly. Examples:

```text
/home
/home 2
/sethome
/sethome 3
/delhome 3
/buyhome 2
/home price 3
```

## GUI behavior

The menu shows all currently available home slots and keeps existing saved homes visible even if an administrator later lowers the global cap. If more than one page is needed, the bottom row provides previous/next navigation. Other default controls show slot information, buy one slot, and close the menu.

- **Left-click a saved home:** teleport.
- **Click an empty unlocked slot:** save your current location there.
- **Right-click a saved home:** open a confirmation dialog before updating it.
- **Shift-click a saved home:** open a confirmation dialog before deleting it.
- Item movement and dragging are cancelled while the menu is open; GUI items cannot be taken or moved.

The slot cards, lore, materials, menu title, row count, page size, filler, and control positions are configurable in `config.yml`. Actions still check the same permissions and configuration switches as commands.

## Default price ladder

Homes 1–4 are free and available immediately. Prices below are the cost of each **additional** slot. `multiplier: 1.25` means a true 25% compound increase, so the seventh home's default price is `$1,562.50` (not `$1,375`).

| Slot purchased | Home slot | Default price |
|---:|---:|---:|
| 1 | 5 | `$1,000.00` |
| 2 | 6 | `$1,250.00` |
| 3 | 7 | `$1,562.50` |
| 4 | 8 | `$1,953.13` |
| 5 | 9 | `$2,441.41` |

Change `homes.pricing.first-extra-slot`, `homes.pricing.multiplier`, and `homes.pricing.decimal-places` to tune the ladder. Bulk purchases charge the sum of consecutive prices in one economy withdrawal. If a provider is unavailable or a withdrawal fails, no home slots are granted.

## Admin commands

Root command `/homesadmin` has short alias `/ha`; each action also has a short form.

| Command | Short form | Description | Permission |
|---|---|---|---|
| `/ha reload` | `/ha rl` | Reload `config.yml`, `messages.yml`, and the economy hook | `homesgui.admin.reload` |
| `/ha info <player>` | `/ha i <player>` | Inspect saved, paid, bonus, and available slot counts | `homesgui.admin.info` |
| `/ha list <player>` | `/ha l <player>` | List a player's saved locations | `homesgui.admin.info` |
| `/ha grant <player> <amount>` | `/ha g <player> <amount>` | Grant persistent bonus slots | `homesgui.admin.slots` |
| `/ha setlimit <player> <total>` | `/ha sl <player> <total>` | Set persistent total capacity (free + paid + admin bonus) | `homesgui.admin.slots` |
| `/ha remove <player> <amount>` | `/ha rm <player> <amount>` | Remove admin-granted bonus slots without revoking paid slots | `homesgui.admin.slots` |
| `/ha delete <player> <slot>` | `/ha d <player> <slot>` | Delete one saved location | `homesgui.admin.data` |
| `/ha clear <player>` | `/ha c <player>` | Clear locations but keep unlocked slots | `homesgui.admin.data` |
| `/ha reset <player>` | `/ha x <player>` | Clear locations and reset purchased/admin bonus slots (no refund) | `homesgui.admin.data` |
| `/ha tp <player> <slot>` | `/ha t <player> <slot>` | Teleport yourself to another player's saved home | `homesgui.admin.teleport` |
| `/ha help` | `/ha h` | Show admin help | `homesgui.admin` |

Admin data commands accept an online player, a cached player name, or a UUID. To avoid blocking Folia/server threads on a profile lookup, an uncached offline name is not fetched from Mojang—use that player's UUID instead. The teleport command must be run by a player; data management can also be run from console.

## Permissions

| Permission | Default | Purpose |
|---|---|---|
| `homesgui.use` | Everyone | Teleport to your homes |
| `homesgui.gui` | Everyone | Open the GUI |
| `homesgui.set` | Everyone | Create/update homes |
| `homesgui.delete` | Everyone | Delete your homes |
| `homesgui.buy` | Everyone | Buy home slots |
| `homesgui.info` | Everyone | View your own home info |
| `homesgui.price` | Everyone | Preview purchase prices |
| `homesgui.slots.<n>` | Not granted by default | Grant `<n>` additional free slots; e.g. `homesgui.slots.3` adds three slots to the configured free/purchased/admin capacity |
| `homesgui.admin` | Operators | Parent permission for all admin actions |
| `homesgui.admin.reload` | Operators | Reload settings and messages |
| `homesgui.admin.info` | Operators | Inspect/list player homes |
| `homesgui.admin.slots` | Operators | Grant, remove, or set admin bonus slots |
| `homesgui.admin.data` | Operators | Delete, clear, or reset player home data |
| `homesgui.admin.teleport` | Operators | Teleport to a player's home |
| `homesgui.*` | Operators | All normal player and admin permissions |

Permission-based slots are added to the player's configured free, paid, and admin-granted slots. A global `homes.max-total` cap still applies.

## Configuration

`config.yml` is copied to `plugins/HomesGUI/config.yml` on first start. Most changes apply after `/ha reload`; home data is not reloaded or discarded.

### Home slots and pricing

| Setting | Default | Description |
|---|---:|---|
| `homes.free-slots` | `4` | Free slots available to every player (slots 1–4 by default) |
| `homes.max-total` | `-1` | Global total slot cap; `-1` means no configured cap (one-million safety ceiling remains) |
| `homes.max-bulk-purchase` | `25` | Maximum number of slots bought in one transaction |
| `homes.permission-slot-prefix` | `homesgui.slots.` | Prefix for dynamic free-slot permissions |
| `homes.allow-setting` | `true` | Enable/disable set and update actions |
| `homes.allow-deleting` | `true` | Enable/disable delete actions |
| `homes.allow-purchases` | `true` | Enable/disable purchases |
| `homes.pricing.first-extra-slot` | `1000.0` | Price of the first paid slot (home 5 with defaults) |
| `homes.pricing.multiplier` | `1.25` | Compound multiplier for each later paid slot |
| `homes.pricing.decimal-places` | `2` | Currency rounding precision, 0–6 |
| `homes.pricing.currency-symbol` | `$` | Fallback currency prefix when the provider has no formatter |
| `homes.economy.enabled` | `true` | Enable the optional economy integration |
| `homes.economy.free-if-unavailable` | `false` | If true, treat an unavailable economy as free purchases; false fails closed |

`homes.max-total` limits the total available slots, not just purchased slots. Existing saved homes are retained and remain visible/usable even if the cap is changed below their slot number; no home is silently deleted.

### Storage

| Setting | Default | Description |
|---|---:|---|
| `storage.autosave-seconds` | `300` | Async autosave interval; `0` disables periodic autosave |
| `storage.save-on-quit` | `true` | Schedule a save after a player disconnects |

Data is stored in `plugins/HomesGUI/homes.yml`, UTF-8 encoded, and replaced atomically where the filesystem supports atomic moves. A synchronous final flush runs during plugin shutdown.

### GUI and sounds

The GUI has 2–6 rows; its last row is reserved for controls. `homes-per-page` is clamped to the content rows. Configure `gui.title`, `gui.rows`, `gui.homes-per-page`, `gui.fill-empty`, and the `material`, `name`, and `lore` under `gui.filler`, `gui.home`, `gui.empty`, and each `gui.controls.*` icon. Control positions are offsets 0–8 within the bottom row (`previous-slot`, `info-slot`, `buy-slot`, `close-slot`, `next-slot`). Materials use Bukkit material names; invalid material names fall back safely.

`sounds.enabled`, `sounds.volume`, and `sounds.pitch` apply to the configured sound names for `gui-open`, `gui-click`, `teleport`, `set`, `delete`, `purchase`, and `failure`. Set an event to `none` to disable just that sound. Invalid sound names are logged and skipped.

### Messages

`messages.yml` contains all user-facing command, purchase, GUI, and admin messages. It supports Bukkit-style legacy colour codes such as `&6`, `&c`, `&l`, and `&r`. Common placeholders include `%slot%`, `%world%`, `%x%`, `%y%`, `%z%`, `%count%`, `%price%`, `%available%`, and `%player%`.

## Economy support

HomesGUI looks for a registered Vault economy service first, then VaultUnlocked. Neither API is bundled into the jar. Install Vault (or VaultUnlocked) and an economy plugin that registers a provider to enable paid purchases. The provider's own currency formatter is used when available; otherwise the configured currency symbol and decimal places are used.

The integration is optional so the plugin and home GUI remain usable if the provider is absent. By default, buying fails closed when no provider exists. On Folia, Vault or a chosen economy plugin may not be marked Folia-compatible and therefore may not load; in that case homes still work, but purchases stay unavailable until a compatible provider is registered (unless `free-if-unavailable` is deliberately enabled).

## Data format and importing existing homes

The player data file follows the supplied nested shape. Each UUID is a root key, home slots are numeric string keys, and coordinates are stored as world, x, y, z, yaw, and pitch:

```yaml
<player-uuid>:
  homes:
    '1':
      world: world
      x: 101.5
      y: 64.0
      z: -24.25
      yaw: 90.0
      pitch: 0.0
```

HomesGUI does **not** ship the example UUIDs or coordinates. To import a compatible file, stop the server, back up the current `plugins/HomesGUI/homes.yml`, copy the existing UUID/home mappings into that file, then start the server. If no slot-count metadata exists, the plugin infers enough purchased slots to keep saved slots above `homes.free-slots` accessible. On subsequent saves it adds `purchased-homes` and `bonus-homes` beside each player's `homes` section; these counters ensure deleting a location does not revoke its unlocked slot.

Example after a player buys and an administrator grants slots:

```yaml
<player-uuid>:
  homes:
    '1':
      world: world
      x: 0.5
      y: 64.0
      z: 0.5
      yaw: 0.0
      pitch: 0.0
  purchased-homes: 1
  bonus-homes: 2
```

## Folia and threading

Folia has independent region threads rather than one main server thread. HomesGUI uses the entity scheduler for player-facing callbacks, the async scheduler for file work, thread-safe per-player home state, and `Player.teleportAsync` for cross-world/chunk-loading teleports. It declares `folia-supported: true` and avoids synchronous entity teleports and Bukkit's legacy single-thread scheduler. The same code path is used on Paper and Purpur.

## Cross-platform notes

HomesGUI uses Java NIO paths instead of OS-specific path separators, explicit UTF-8 reads/writes, and an atomic-move fallback for filesystems that do not support atomic replacement. It contains no NMS code or native libraries. The plugin jar therefore has no Linux-, Windows-, or macOS-specific binary component.

## Troubleshooting

- **`/home` is handled by another plugin:** another homes plugin may own the same command. Remove/disable the conflicting command or invoke the plugin-qualified command shown by Bukkit's fallback (`/homesgui:home`).
- **Purchases report no economy:** confirm Vault or VaultUnlocked loaded, an economy provider is registered, and `homes.economy.enabled` is true. On Folia, verify the economy plugin is itself compatible with Folia.
- **A home cannot be reached because its world is missing:** load/restore that world or update the stored `world` value to its exact server world name.
- **The plugin refuses to load:** use a Java 25 runtime and a Paper/Purpur/Folia 26.2 server. This build intentionally targets 26.2, not earlier Minecraft versions.
- **A YAML edit did not take effect:** use a valid backup of the UUID-based structure, keep slot keys numeric, and restart after importing the data file.
