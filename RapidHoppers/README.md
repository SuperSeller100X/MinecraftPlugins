# ⚡ RapidHoppers

**Hoppers that actually keep up.** RapidHoppers makes every item-moving block
and entity in Minecraft dramatically faster — hoppers, hopper minecarts, chest
minecarts, droppers and (optionally) dispensers — with a full configuration
file, an in-game GUI, admin commands, permissions, tab completion, sound
effects and a TPS-aware safety net so the speed never costs you your server.

- **Version:** 1.0.0
- **Target:** Minecraft **26.2** (released 16 Jun 2026) — **Paper / Purpur / Folia**
- **Java:** **25** (the runtime Minecraft 26.2 requires)
- **Build:** Maven (`mvn -B clean package`)
- **Author:** SuperSeller100X
- **Dependencies:** none — Paper API only, no external libraries

---

## ✨ What it does

Vanilla hoppers move **1 item every 8 ticks** (2.5 items per second). That is
the single biggest bottleneck in storage systems, item sorters, auto-farms and
shop backends.

RapidHoppers adds an **acceleration engine** that runs alongside vanilla logic
and performs *extra* transfers:

| Setting | Default | Vanilla | Effect |
| --- | --- | --- | --- |
| `engine.interval-ticks` | `2` | 8 | transfers happen 4× more often |
| `engine.items-per-transfer` | `8` | 1 | 8 items move per transfer |
| **Result** | **32× vanilla throughput** by default | 2.5 items/s | ~80 items/s |

Because vanilla behaviour is never replaced — only supplemented — comparators,
redstone item sorters, filtered hoppers, locked hoppers and every other
contraption keep working exactly as before. They just run much faster.

### Accelerated containers

| Container | Config key | Default | What is boosted |
| --- | --- | --- | --- |
| Hopper | `containers.hopper` | ✅ on | pulling from above, pushing into the faced container, picking up dropped items |
| Hopper minecart | `containers.hopper-minecart` | ✅ on | pulling from containers above, pushing into containers below |
| Chest / storage minecart | `containers.chest-minecart` | ✅ on | draining into the hopper it sits on |
| Dropper | `containers.dropper` | ✅ on | pushing into the container it faces |
| Dispenser | `containers.dispenser` | ⛔ off | same as dropper (off by default, since dispensers usually *shoot* items) |

### Performance & safety

- **TPS auto-throttle** — below `soft-tps` (default 18) the engine slows down;
  below `hard-tps` (default 14) it pauses entirely until the server recovers.
- **Per-chunk limit** — at most `max-containers-per-chunk` (default 96)
  accelerated containers per chunk, so a 4 000-hopper sorter can't melt a chunk.
- **Per-world limit** and a **global per-tick transfer budget**.
- **Player activity radius** — chunks with no player within 6 chunks are
  skipped entirely (configurable, `0` = process every loaded chunk).
- **Live statistics** — tracked containers, total transfers, transfers/second
  and current TPS via `/rh stats` or the GUI.

### Platform support

The plugin ships **one jar** that runs on Paper, Purpur, Spigot and Folia.
Scheduling is resolved at runtime through reflection:

- **Folia** → `GlobalRegionScheduler` for the engine loop, `RegionScheduler`
  for every block/entity touch, so all work happens on the owning region thread.
- **Paper / Purpur / Spigot** → the classic `BukkitScheduler`.

Everything is pure Java with no native code and no OS-specific paths, so the
same jar behaves identically on **Linux, Windows and macOS**.

---

## 📥 Installation

1. Run a **Minecraft 26.2** Paper, Purpur or Folia server on **Java 25**.
2. Drop `RapidHoppers-1.0.0.jar` into `plugins/`.
3. Start the server. `plugins/RapidHoppers/config.yml` and `messages.yml` are
   created automatically.
4. Tune it in-game with `/rha gui` or edit the files and run `/rha reload`.

---

## 🧭 Commands

Every command and every sub-command has a **short form**.

### `/rapidhoppers` — players

Aliases: **`/rhoppers`**, **`/rh`** · Permission: `rapidhoppers.use` (default: everyone)

| Command | Short | Permission | Description |
| --- | --- | --- | --- |
| `/rh info` | `/rh i` | `rapidhoppers.info` | engine state, interval, speed factor, stack size, world mode, version/platform |
| `/rh stats` | `/rh s` | `rapidhoppers.stats` | tracked containers, total transfers, transfers/second, TPS |
| `/rh gui` | `/rh g` | `rapidhoppers.gui` | open the control panel (read-only without `rapidhoppers.admin`) |
| `/rh help` | `/rh h` | `rapidhoppers.use` | command list |

Running `/rh` with no arguments shows the info page.

### `/rapidhoppersadmin` — administration

Aliases: **`/rhadmin`**, **`/rha`** · Permission: `rapidhoppers.admin` (default: op)

| Command | Short | Permission | Description |
| --- | --- | --- | --- |
| `/rha reload` | `/rha rl` | `rapidhoppers.admin.reload` | reload `config.yml` + `messages.yml` and restart the engine |
| `/rha toggle` | `/rha t` | `rapidhoppers.admin.toggle` | enable/disable the whole engine |
| `/rha toggle <type>` | `/rha t <type>` | `rapidhoppers.admin.toggle` | toggle one container family (`hopper`, `hopper-minecart`, `chest-minecart`, `dropper`, `dispenser`) |
| `/rha speed <1-8>` | `/rha sp <1-8>` | `rapidhoppers.admin.speed` | transfer interval in ticks (1 = fastest) |
| `/rha stack <1-64>` | `/rha st <1-64>` | `rapidhoppers.admin.stack` | items moved per transfer |
| `/rha world [world]` | `/rha w [world]` | `rapidhoppers.admin.world` | toggle a world in the world list (defaults to your current world) |
| `/rha throttle [on\|off]` | `/rha th` | `rapidhoppers.admin.throttle` | toggle the TPS auto-throttle |
| `/rha throttle soft <tps>` | `/rha th soft <tps>` | `rapidhoppers.admin.throttle` | set the soft (slow-down) threshold |
| `/rha throttle hard <tps>` | `/rha th hard <tps>` | `rapidhoppers.admin.throttle` | set the hard (pause) threshold |
| `/rha limit <n>` | `/rha l <n>` | `rapidhoppers.admin.limit` | max accelerated containers per chunk (`0` = unlimited) |
| `/rha debug` | `/rha d` | `rapidhoppers.admin.debug` | toggle verbose logging |
| `/rha info` | `/rha i` | `rapidhoppers.admin` | status page |
| `/rha stats` | `/rha s` | `rapidhoppers.admin` | live statistics |
| `/rha gui` | `/rha g` | `rapidhoppers.admin` | open the editable control panel |
| `/rha help` | `/rha h` | `rapidhoppers.admin` | admin command list |

Every change made with a command (or in the GUI) is **written straight back to
`config.yml`**, so it survives restarts.

### Tab completion

Both commands implement `TabCompleter`:

- sub-commands (long **and** short forms) at argument 1;
- container types for `toggle`, loaded world names for `world`,
  sensible value suggestions for `speed`, `stack`, `limit`, and
  `on/off/soft/hard` plus TPS values for `throttle`.
- The admin completer returns nothing at all for players without
  `rapidhoppers.admin`, so the command surface stays invisible to them.

---

## 🔐 Permissions

| Node | Default | Grants |
| --- | --- | --- |
| `rapidhoppers.use` | everyone | access to `/rapidhoppers` |
| `rapidhoppers.info` | everyone | `/rh info` |
| `rapidhoppers.stats` | everyone | `/rh stats` |
| `rapidhoppers.gui` | op | open the control panel (read-only unless admin) |
| `rapidhoppers.admin` | op | **all** admin commands + editable GUI (parent of everything below) |
| `rapidhoppers.admin.reload` | op | `/rha reload` |
| `rapidhoppers.admin.toggle` | op | `/rha toggle [type]` |
| `rapidhoppers.admin.speed` | op | `/rha speed` |
| `rapidhoppers.admin.stack` | op | `/rha stack` |
| `rapidhoppers.admin.world` | op | `/rha world` |
| `rapidhoppers.admin.throttle` | op | `/rha throttle` |
| `rapidhoppers.admin.limit` | op | `/rha limit` |
| `rapidhoppers.admin.debug` | op | `/rha debug` |

`rapidhoppers.admin` is a parent node with children, so a single LuckPerms grant
(`lp group admin permission set rapidhoppers.admin true`) unlocks everything.

---

## 🖥️ The GUI

`/rh gui` or `/rha gui` opens a chest-style control panel (title, row count and
filler material configurable). Admins can change everything with a click;
players with only `rapidhoppers.gui` see the same panel read-only.

| Slot | Item | Left click | Right click |
| --- | --- | --- | --- |
| Engine | Hopper / Barrier | toggle the engine on/off | — |
| Transfer interval | Clock | faster (−1 tick) | slower (+1 tick) |
| Items per transfer | Chest | +1 (shift: +8) | −1 (shift: −8) |
| Container types | Minecart | cycle to the next type and toggle it | — |
| TPS auto-throttle | Redstone torch | toggle the throttle | — |
| Per-chunk limit | Iron bars | +8 | −8 |
| Worlds | Map | switch BLACKLIST ⇄ WHITELIST | — |
| Live statistics | Paper | refresh | — |
| Reload | Comparator | reload config & messages | — |
| Close | Red pane | close the panel | — |

The statistics tile refreshes automatically every `gui.refresh-ticks` ticks
(default 20 = once per second). Drag-and-drop and item theft are blocked.

---

## 🔊 Sound effects

Every sound is configurable (name, volume, pitch) under `sounds:` and can be
turned off globally with `sounds.enabled: false`. Unknown sound names are
ignored gracefully instead of throwing.

| Key | Default sound | Played when |
| --- | --- | --- |
| `gui-open` | `BLOCK_BARREL_OPEN` | the panel opens |
| `gui-click` | `UI_BUTTON_CLICK` | a panel button is clicked |
| `gui-close` | `BLOCK_BARREL_CLOSE` | the panel closes |
| `success` | `ENTITY_EXPERIENCE_ORB_PICKUP` | a command succeeded |
| `error` | `BLOCK_NOTE_BLOCK_BASS` | a command failed / permission denied |
| `toggle-on` | `BLOCK_LEVER_CLICK` (pitch 1.6) | something was enabled |
| `toggle-off` | `BLOCK_LEVER_CLICK` (pitch 0.8) | something was disabled |

---

## ⚙️ Configuration (`config.yml`)

```yaml
enabled: true

engine:
  interval-ticks: 2            # 1-8, vanilla is 8
  items-per-transfer: 8        # 1-64, vanilla is 1
  boost-vanilla-transfers: true
  scan-interval-ticks: 100
  player-activity-radius-chunks: 6   # 0 = every loaded chunk

containers:
  hopper:
    enabled: true
    pull-from-above: true
    push-to-facing: true
    pickup-items: true
  hopper-minecart:
    enabled: true
    pull-from-above: true
    push-to-container: true
  chest-minecart:
    enabled: true
  dropper:
    enabled: true
    interval-multiplier: 2
  dispenser:
    enabled: false
    interval-multiplier: 2

worlds:
  mode: BLACKLIST              # or WHITELIST
  list: []

performance:
  throttle:
    enabled: true
    soft-tps: 18.0             # slow down below this
    hard-tps: 14.0             # pause below this
    interval-multiplier: 3
    check-interval-seconds: 5
    log-state-changes: true
  max-containers-per-chunk: 96 # 0 = unlimited
  max-containers-per-world: 20000
  max-transfers-per-tick: 2000

sounds:
  enabled: true
  # gui-open / gui-click / gui-close / success / error / toggle-on / toggle-off
  # each with: sound, volume, pitch

gui:
  title: "<gradient:#38bdf8:#a855f7><bold>RapidHoppers</bold></gradient>"
  rows: 5                      # 3-6
  filler: GRAY_STAINED_GLASS_PANE
  refresh-ticks: 20

debug: false
```

All values are **clamped on load**, so a typo can never crash the server — an
out-of-range number is pulled back into the valid range instead.

### Messages (`messages.yml`)

Every string the plugin sends is in `messages.yml` and uses
[MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatting with
`{placeholder}` substitution — fully translatable, including the GUI item names
and lore (lore lines are separated with `|`).

### Recommended presets

| Server type | interval | items | throttle | per-chunk limit |
| --- | --- | --- | --- | --- |
| Small survival / friends | `4` | `4` | on | `96` |
| Public survival (default) | `2` | `8` | on | `96` |
| Skyblock / heavy automation | `1` | `16` | on, soft 19 | `64` |
| Creative / build server | `1` | `64` | off | `0` |

---

## 🛠️ Building

The canonical build uses Maven and the real Paper API for Minecraft 26.2:

```bash
cd RapidHoppers
mvn -B clean package
# -> target/RapidHoppers-1.0.0.jar
```

Requirements: **JDK 25** and network access to `https://repo.papermc.io`
(dependency `io.papermc.paper:paper-api:26.2.build.115-stable`, scope
`provided` — nothing is shaded into the jar).

### Offline build & tests

For sandboxes and CI runners without access to the Paper repository, the
project ships compile-only API stubs plus the Eclipse batch compiler:

```bash
cd RapidHoppers
JAVA_BIN=/path/to/java25 ./build.sh
```

This regenerates `stub-api/src` (see `stub-api/generate.py`), compiles all
sources with `--release 25`, runs both smoke test suites and assembles
`target/RapidHoppers-offline.jar`. The stubs exist **only** for offline
compilation — they are never shipped or referenced by the Maven build.

### Tests

| Suite | Checks |
| --- | --- |
| `smoke/EngineTest.java` | transfer math, interval/throttle calculations, config clamping and world modes, inventory move semantics, statistics counters (50 assertions) |
| `smoke/CommandTest.java` | tab-completion filtering, message placeholders, and cross-file consistency between `plugin.yml`, `config.yml`, `messages.yml` and `pom.xml` (39 assertions) |

A GitHub Actions workflow is included at `.github/workflows/build.yml` — copy it
to the repository root `.github/workflows/` to activate it. It runs the offline
smoke tests and then `mvn clean package` on Temurin 25.

---

## 🧱 Project layout

```
RapidHoppers/
├── pom.xml                       # Maven build (Java 25, paper-api 26.2)
├── build.sh                      # offline build + smoke tests
├── libs/ecj.jar                  # Eclipse batch compiler (offline builds)
├── stub-api/generate.py          # emits compile-only API stubs
├── smoke/                        # server-free test suites
└── src/main/
    ├── java/dev/superseller/rapidhoppers/
    │   ├── RapidHoppersPlugin.java      # bootstrap, wiring, reload
    │   ├── command/                     # player + admin commands, tab completion, permissions
    │   ├── config/                      # Settings, ConfigService, Messages
    │   ├── engine/                      # HopperEngine, TransferMath, InventoryOps, ThrottleMonitor, Stats
    │   ├── gui/                         # ControlPanel, PanelHolder
    │   ├── listener/                    # TransferListener (boosts vanilla transfers)
    │   ├── scheduler/                   # PlatformScheduler (Folia/Bukkit bridge)
    │   └── util/                        # Sounds
    └── resources/                       # plugin.yml, config.yml, messages.yml
```

---

## ❓ FAQ

**Does this break item sorters?**
No. Vanilla hopper logic still runs; RapidHoppers only adds extra transfers on
top. Comparator readings, locked hoppers and filtered designs are unchanged.

**Will it lag my server?**
It is designed not to. The throttle pauses the engine before TPS collapses, the
per-chunk and per-tick budgets bound the worst case, and chunks far from any
player are skipped. If you still see impact, raise `interval-ticks`, lower
`items-per-transfer` or reduce `player-activity-radius-chunks`.

**Does it work on Folia?**
Yes — `folia-supported: true`, and all block/entity access is dispatched onto
the owning region thread through the region scheduler.

**Can I disable it in one world?**
Yes: `/rha world <name>` (BLACKLIST mode) or switch to WHITELIST and list only
the worlds that should be fast.

**Does it need Vault / PlaceholderAPI / a database?**
No. RapidHoppers has zero dependencies and stores nothing but its config.

---

## 📄 License & credits

Part of the [SuperSeller100X/MinecraftPlugins](https://github.com/SuperSeller100X/MinecraftPlugins)
collection. Built for Minecraft 26.2 on Paper, Purpur and Folia with Java 25.
