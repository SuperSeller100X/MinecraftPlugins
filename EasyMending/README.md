# EasyMending 🛠️

**EasyMending** is a high-performance, configurable Minecraft plugin for **Minecraft 26.2 (Paper / Purpur / Folia)** built with **Java 25** and **Maven**. It allows players to repair damaged tools, weapons, and armor that have the **Mending** enchantment directly using their existing experience points (XP).

Instead of waiting at XP grinders holding damaged gear in your offhand, EasyMending provides immediate, seamless durability restoration through fast chat commands, short aliases, or a sleek interactive chest GUI.

---

## ✨ Features

- ⚡ **Instant Mending with XP**: Restores durability using player experience at the vanilla rate (1 raw XP point restores 2 durability points).
- 🛡️ **Flexible Repair Scopes**:
  - **Hand**: Repair the held tool or weapon.
  - **Offhand**: Repair the offhand shield, totem, or item.
  - **Armor**: Repair all equipped armor pieces in a single action.
  - **Hotbar**: Repair all damaged items across the player's hotbar.
  - **All**: Repair all eligible damaged items in the entire inventory.
- 📦 **Interactive Chest GUI (`/em gui`)**:
  - Real-time durability inspection and XP cost previews.
  - One-click buttons to repair hand, offhand, armor, or all items.
  - Player XP profile overview displaying current level, total XP, and missing durability.
  - Instant live refresh without having to re-open the menu.
- 🧩 **Configurable Mending Enforcement & Fallback**:
  - By default, strictly requires the Mending enchantment on tools.
  - Configurable option to allow non-mending repairs with an optional XP penalty multiplier (e.g. 1.5x).
  - Admin bypass permission (`easymending.bypass.mending`) for staff or VIPs.
- 💡 **Intelligent Partial Repair Support**:
  - If a player lacks sufficient XP for a 100% full repair, the plugin can spend all available XP to repair as much durability as possible.
  - Can be toggled on/off in `config.yml`.
- 🌐 **True Folia & Threading Safety**:
  - Folia-safe entity and region scheduling via `PlatformScheduler`. All inventory and XP modifications execute on the appropriate player entity thread.
- 💻 **Cross-Platform**:
  - Guaranteed compatibility across Linux, Windows, and macOS with UTF-8 character encoding and portable path management.
- 🔊 **Rich Sound & Particle Effects**:
  - Customizable sound effects for repair success, all-item repair, insufficient XP, no-damage, and GUI navigation.
  - Happy villager / enchantment table particle bursts on successful repairs.
- ⌨️ **Short Aliases & Context-Sensitive Tab Completion**:
  - Every command and sub-command has a convenient 1-2 letter short alias (e.g., `/em h`, `/em a`, `/em *`, `/ema rl`).
  - Intelligent tab completion with permission filtering.
- 👑 **Comprehensive Admin Tools**:
  - Inspect any online player's inventory, damaged gear, and total XP.
  - Force-repair another player's items (with optional `--free` / `-f` bypass flag).
  - Live adjustment of durability-to-XP ratios (`/ema setratio <value>`).
  - Toggle temporary free repair bypass for testing or rewards (`/ema bypass <player>`).
  - Global statistics tracking total repairs and XP consumed.

---

## 📋 Commands & Aliases

Primary command: `/easymending` (Aliases: `/em`, `/mend`, `/easymend`, `/mending`)  
Admin command: `/easymendingadmin` (Aliases: `/emadmin`, `/ema`, `/mendadmin`)

### Player Commands

| Command | Short Alias | Description | Permission |
|---|---|---|---|
| `/easymending` | `/em` | Opens the interactive repair GUI | `easymending.gui` |
| `/easymending gui` | `/em g` / `/em menu` | Opens the interactive repair GUI | `easymending.gui` |
| `/easymending hand` | `/em h` / `/em main` | Repairs the item in your main hand | `easymending.hand` |
| `/easymending offhand` | `/em oh` / `/em off` | Repairs the item in your offhand | `easymending.offhand` |
| `/easymending armor` | `/em a` | Repairs all equipped armor pieces | `easymending.armor` |
| `/easymending hotbar` | `/em hb` / `/em hot` | Repairs all damaged items in hotbar | `easymending.hotbar` |
| `/easymending all` | `/em *` / `/em all` / `/em inv` | Repairs all damaged items in inventory | `easymending.all` |
| `/easymending info` | `/em i` | Shows durability status and XP cost of held item | `easymending.info` |
| `/easymending cost` | `/em c` | Shows total XP needed to repair all items | `easymending.cost` |
| `/easymending help` | `/em ?` / `/em hlp` | Displays the help menu | `easymending.use` |

### Administrator Commands

| Command | Short Alias | Description | Permission |
|---|---|---|---|
| `/emadmin reload` | `/ema rl` | Reloads `config.yml` and `messages.yml` | `easymending.admin.reload` |
| `/emadmin repair <player> [scope] [-f]` | `/ema r <player> [scope] [--free]` | Force-repairs target player's equipment | `easymending.admin.repair` |
| `/emadmin inspect <player>` | `/ema i <player>` | Inspects player's mending items & XP | `easymending.admin.inspect` |
| `/emadmin setratio <value>` | `/ema sr <value>` | Changes durability restored per XP point | `easymending.admin.setratio` |
| `/emadmin bypass <player>` | `/ema bp <player>` | Toggles free repair bypass for a player | `easymending.admin.bypass` |
| `/emadmin stats` | `/ema s` | Displays global repair metrics | `easymending.admin.stats` |
| `/emadmin help` | `/ema ?` | Displays the admin command help menu | `easymending.admin` |

---

## 🔑 Permissions

| Permission | Default | Description |
|---|---|---|
| `easymending.use` | `true` | Access to basic EasyMending commands and GUI |
| `easymending.gui` | `true` | Open the interactive repair GUI |
| `easymending.hand` | `true` | Repair the main hand item with `/em hand` |
| `easymending.offhand` | `true` | Repair the offhand item with `/em offhand` |
| `easymending.armor` | `true` | Repair equipped armor pieces with `/em armor` |
| `easymending.hotbar` | `true` | Repair hotbar items with `/em hotbar` |
| `easymending.all` | `true` | Repair all inventory items with `/em all` |
| `easymending.info` | `true` | View durability and XP cost with `/em info` |
| `easymending.cost` | `true` | View total inventory repair cost with `/em cost` |
| `easymending.admin` | `op` | Full access to all administrator commands |
| `easymending.admin.reload` | `op` | Permission to reload configurations |
| `easymending.admin.repair` | `op` | Permission to force repair player items |
| `easymending.admin.inspect` | `op` | Permission to inspect other players |
| `easymending.admin.setratio` | `op` | Permission to dynamically modify ratio |
| `easymending.admin.bypass` | `op` | Permission to toggle bypass for players |
| `easymending.admin.stats` | `op` | Permission to view global statistics |
| `easymending.bypass.cost` | `false` | Repair items for free without deducting XP |
| `easymending.bypass.mending`| `false` | Repair items that do not have Mending |
| `easymending.bypass.cooldown`| `false` | Bypass command cooldowns |
| `easymending.*` | `op` | Wildcard grant for all EasyMending permissions |

---

## ⚙️ Configuration Reference (`config.yml`)

```yaml
# ==============================================================================
# EasyMending Configuration
# Target: Minecraft 26.2 (Paper, Purpur, Folia) | Java 25
# ==============================================================================

repair:
  # Durability restored per 1 point of experience.
  # Vanilla Mending ratio is 2.0 (1 XP restores 2 durability).
  durability-per-xp: 2.0

  # If true, only items with the Mending enchantment can be repaired.
  require-mending: true

  # Multiplier applied to XP cost for items without Mending (if allowed or bypassed).
  non-mending-cost-multiplier: 1.5

  # If true, spend all available XP to repair as much durability as possible
  # when the player cannot afford a full 100% repair.
  allow-partial-repair: true

  # Minimum XP cost for any repair transaction.
  min-xp-per-repair: 1

  # Cooldown in seconds between repair commands (0 to disable).
  cooldown-seconds: 0

  # Whether sneak-right clicking with a damaged tool triggers quick repair.
  sneak-click-to-repair: false

sounds:
  enabled: true
  repair-success:
    sound: "BLOCK_ANVIL_USE"
    volume: 1.0
    pitch: 1.2
  repair-all:
    sound: "UI_TOAST_CHALLENGE_COMPLETE"
    volume: 0.8
    pitch: 1.0
  no-damage:
    sound: "BLOCK_NOTE_BLOCK_DIDGERIDOO"
    volume: 0.8
    pitch: 0.8
  no-mending:
    sound: "ENTITY_VILLAGER_NO"
    volume: 0.9
    pitch: 0.9
  insufficient-xp:
    sound: "BLOCK_NOTE_BLOCK_BASS"
    volume: 1.0
    pitch: 0.6
  gui-click:
    sound: "UI_BUTTON_CLICK"
    volume: 0.7
    pitch: 1.2
  gui-open:
    sound: "BLOCK_CHEST_OPEN"
    volume: 0.7
    pitch: 1.0

particles:
  enabled: true
  type: "HAPPY_VILLAGER"
  count: 15

gui:
  title: "<gradient:#4facfe:#00f2fe><b>EasyMending</b></gradient> <dark_gray>»</dark_gray> <gray>Repair Menu</gray>"
  fill-empty-slots: true
  fill-material: "GRAY_STAINED_GLASS_PANE"
  border-material: "CYAN_STAINED_GLASS_PANE"
```

---

## 🧮 Experience Math & Precision

EasyMending implements the exact quadratic formula used by vanilla Minecraft to compute cumulative experience from levels and vice versa:

$$\text{Points to Level } L \rightarrow L+1 = \begin{cases} 2L + 7 & 0 \le L \le 15 \\ 5L - 38 & 16 \le L \le 30 \\ 9L - 158 & L \ge 31 \end{cases}$$

$$\text{Total Points at Level } L = \begin{cases} L^2 + 6L & 0 \le L \le 16 \\ 2.5L^2 - 40.5L + 360 & 17 \le L \le 31 \\ 4.5L^2 - 162.5L + 2220 & L \ge 32 \end{cases}$$

This guarantees 100% mathematical precision without round-off discrepancies or experience bar desynchronization.

---

## 🔨 Building

### Requirements
- **JDK 25**
- **Maven 3.9+**

### Compile & Package
```bash
cd EasyMending
mvn clean package
```

The compiled plugin jar will be generated in `EasyMending/target/EasyMending-1.0.0.jar`.

### Offline Smoke Tests
Run the standalone offline smoke test suite without a running server:
```bash
javac -d /tmp/classes EasyMending/src/main/java/dev/superseller/easymending/model/*.java \
      EasyMending/src/main/java/dev/superseller/easymending/util/ExperienceCalculator.java \
      EasyMending/smoke/SmokeTest.java
java -cp /tmp/classes dev.superseller.easymending.smoke.SmokeTest
```
