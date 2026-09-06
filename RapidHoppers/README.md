# ⚡ RapidHoppers

**Hoppers that actually keep up.** RapidHoppers makes hoppers and hopper
minecarts run their **vanilla transfer more often** — and changes nothing else.
Same one item per transfer, same source, same destination, same rules. Only the
clock is faster.

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

RapidHoppers shortens that 8-tick cooldown — and does nothing else:

| | Vanilla | RapidHoppers (default) |
| --- | --- | --- |
| Items per transfer | 1 | **1** (not configurable, on purpose) |
| Ticks between transfers | 8 | `engine.interval-ticks`, default **2** |
| Throughput | 2.5 items/s | **10 items/s** (4× vanilla) |
| Sources & destinations | above / faced block | **identical** |

### Speed is the only thing that changes

This is the whole design, and it is enforced in three places:

1. **One item per transfer.** There is no `items-per-transfer` setting. A
   hopper moving 8 or 64 items at once is not a fast hopper, it is a *different*
   hopper: comparators read wrong, item sorters overshoot their filter slot,
   and single-item redstone clocks break. RapidHoppers only ever moves one.

2. **A shared clock, so the plugin never stacks on top of vanilla.** Vanilla's
   own transfers are observed via `InventoryMoveItemEvent` and stamped into the
   same per-container cooldown the engine uses. The configured rate is a
   **ceiling**, not a bonus: at `interval-ticks: 2` a hopper moves 10 items per
   second total, not vanilla's 2.5 *plus* the plugin's. This is what removes
   the old burst-and-race behaviour.

3. **Only vanilla's own destinations.** A hopper pulls from the inventory
   directly above and pushes into the inventory it faces. The engine never
   invents a destination, never reaches sideways into a neighbouring container,
   and never inserts into a block vanilla would not insert into.

Because of this, comparators, redstone item sorters, filtered hoppers, locked
hoppers and every other contraption behave *exactly* as they do in vanilla.
They just run sooner.

### Accelerated containers

| Container | Config key | Default | What is accelerated |
| --- | --- | --- | --- |
| Hopper | `containers.hopper` | ✅ on | pulling one item from the inventory above, pushing one item into the faced inventory |
| Hopper minecart | `containers.hopper-minecart` | ✅ on | pulling one item from the inventory above |

**Not handled at all — and deliberately so:**

| Container | Why it is left to vanilla |
| --- | --- |
| Dropper / dispenser | They fire on a **redstone pulse**, not on a hopper clock. There is no transfer rate to speed up, so "accelerating" them means ejecting items nothing asked to be ejected. |
| Chest minecart | It has no transfer logic of its own; the hopper underneath drains it, and *that* hopper is already accelerated. Draining it separately just double-moves. |
| Hopper-minecart push-down | Vanilla hopper minecarts do not push into the block below. The hopper below pulls from them — again, already accelerated. |
| Furnaces, brewing stands, crafters, … | They have slot rules (fuel, result slots) that a generic insert would bypass. Untouched. |

Item-entity pickup is also not accelerated: vanilla hoppers already scan for
dropped items **every single tick**, so there is nothing to improve.

### Performance & safety

- **TPS auto-throttle** — below `soft-tps` (default 18) hoppers step back
  towards vanilla speed; below `hard-tps` (default 14) acceleration stops
  entirely and hoppers run at pure vanilla speed until the server recovers.
- **Per-chunk limit** — at most `max-containers-per-chunk` (default 96)
  accelerated containers per chunk, so a 4 000-hopper sorter can't melt a chunk.
- **Per-world limit** and a **global per-tick transfer budget**.
- **Player activity radius** — chunks with no player within 6 chunks are
  skipped entirely (configurable, `0` = process every loaded chunk).
- **Live statistics** — tracked containers, total transfers, transfers/second
  and current TPS via `/rh stats` or the GUI.
- **Bounded memory** — the per-container cooldown map prunes entries that have
  not moved anything for a minute.

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
| `/rh info` | `/rh i` | `rapidhoppers.info` | engine state, interval, speed factor, items/second, world mode, version/platform |
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
| `/rha toggle <type>` | `/rha t <type>` | `rapidhoppers.admin.toggle` | toggle one container family (`hopper`, `hopper-minecart`) |
| `/rha speed <1-8>` | `/rha sp <1-8>` | `rapidhoppers.admin.speed` | ticks between transfers (1 = fastest, 8 = vanilla) |
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
  sensible value suggestions for `speed`, `limit`, and
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
| Hopper speed | Clock | faster (−1 tick) | slower (+1 tick) |
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
  interval-ticks: 2            # 1-8 ticks between transfers, vanilla is 8
                               # 8 = vanilla · 4 = 2x · 2 = 4x · 1 = 8x
                               # (there is no items-per-transfer: always 1)
  scan-interval-ticks: 100
  player-activity-radius-chunks: 6   # 0 = every loaded chunk

containers:
  hopper:
    enabled: true
    pull-from-above: true
    push-to-facing: true
  hopper-minecart:
    enabled: true
    pull-from-above: true

# Droppers, dispensers and chest minecarts are intentionally not handled -
# they do not run on a hopper clock, so there is no rate to accelerate.

worlds:
  mode: BLACKLIST              # or WHITELIST
  list: []

performance:
  throttle:
    enabled: true
    soft-tps: 18.0             # step back towards vanilla below this
    hard-tps: 14.0             # pure vanilla speed below this
    interval-multiplier: 3     # capped at vanilla's 8 ticks
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

| Server type | `interval-ticks` | Speed | throttle | per-chunk limit |
| --- | --- | --- | --- | --- |
| Vanilla parity (plugin idle) | `8` | 2.5 items/s | on | `96` |
| Small survival / friends | `4` | 5 items/s (2×) | on | `96` |
| Public survival (default) | `2` | 10 items/s (4×) | on | `96` |
| Skyblock / heavy automation | `1` | 20 items/s (8×) | on, soft 19 | `64` |
| Creative / build server | `1` | 20 items/s (8×) | off | `0` |

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
| `smoke/EngineTest.java` | transfer math, interval/throttle calculations, the per-container cooldown clock, config clamping and world modes, single-item move semantics, statistics counters (97 assertions) |
| `smoke/CommandTest.java` | tab-completion filtering, message placeholders, cross-file consistency between `plugin.yml`, `config.yml`, `messages.yml` and `pom.xml`, and a guard that no `items-per-transfer` setting can creep back in (40 assertions) |

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
    │   ├── engine/                      # HopperEngine, TransferClock, TransferMath, InventoryOps, ThrottleMonitor, Stats
    │   ├── gui/                         # ControlPanel, PanelHolder
    │   ├── listener/                    # TransferListener (observes vanilla transfers, never alters them)
    │   ├── scheduler/                   # PlatformScheduler (Folia/Bukkit bridge)
    │   └── util/                        # Sounds
    └── resources/                       # plugin.yml, config.yml, messages.yml
```

---

## ❓ FAQ

**Does this break item sorters?**
No. Every transfer is a single item to a vanilla destination, and the plugin
shares a cooldown with vanilla rather than adding transfers on top of it.
Comparator readings, locked hoppers and filtered designs are unchanged.

**Can I make hoppers move whole stacks at once?**
No, and that is intentional. Moving more than one item per transfer is what
breaks comparators and item sorters, and is what caused items to end up in
containers nobody meant to fill. Use a lower `interval-ticks` instead — at `1`
a hopper moves 20 items/second, one at a time.

**Will it lag my server?**
It is designed not to. The throttle returns hoppers to vanilla speed before TPS
collapses, the per-chunk and per-tick budgets bound the worst case, and chunks
far from any player are skipped. If you still see impact, raise
`interval-ticks` (towards 8 = vanilla) or reduce
`player-activity-radius-chunks`.

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
