# ⚔️ CombatTag

PvP **combat tagging** for Minecraft servers. Get hit by a player (or their arrow, trident,
splash potion…) and you are *in combat* for **10 seconds** by default — during which you
**cannot open shops, cannot teleport, and cannot repair with EasyMending**.

- **Version:** 1.0.0
- **Target:** Minecraft **26.2** — **Paper / Purpur / Folia**
- **Java:** **25** (required by Minecraft 26.2)
- **Author:** SuperSeller100X
- **Dependencies:** none (PlaceholderAPI is optional)

---

## ✨ Features

- **Combat tag on PvP damage** — melee, projectiles (arrow, trident, snowball, egg,
  crossbow), splash & lingering potions, and optionally a player's tamed pets
- **Separate projectile duration** so bow fights can be tuned independently
- **Blocked while tagged** (each toggleable):
  - 🛒 **Shops** — `EconomyShopGUI`, `ShopGUI+`, auction houses, ChestShop… detected both by
    **command** and by the **GUI itself** (inventory title *and* inventory-holder class), so
    a renamed shop menu is still blocked
  - 🌀 **Teleports** — `/home`, `/warp`, `/spawn`, `/tpa`, `/tpaccept`, `/back`, `/rtp`,
    `/hub`, islands/plots/towns, **TeleportSigns**, plus **every** `PlayerTeleportEvent`
    regardless of which plugin fired it, plus **ender pearls** and **chorus fruit**
  - 🔨 **EasyMending** — commands *and* the repair GUI *and* the sneak-right-click quick repair
  - 🚫 **Any command you like** — blacklist mode or strict whitelist mode
- **Combat logging punishment** — quitting while tagged kills the player, drops their
  inventory, broadcasts it, logs it and can run any console commands you configure
- **Boss bar + action bar countdown**, optional title, all fully translatable
- **GUI** — a player status screen (`/ct gui`) and an **admin panel** (`/cta gui`) with
  live stats, clear-all and reload buttons
- **Sounds** for tag start, tag end, blocked actions and GUI interactions
- **Admin commands** — tag, untag, clear, list, exempt, live duration changes, stats, reload
- **Short aliases for every command and sub-command**
- **PlaceholderAPI** support and a small **developer API**
- **Folia-safe**: global/region/entity schedulers via reflection, Bukkit fallback
- Pure in-memory state (a combat timer should not survive a restart) — no database, no
  files to corrupt; runs identically on **Linux, Windows and macOS**

---

## 📦 Installation

1. Run **Paper**, **Purpur** or **Folia** for Minecraft **26.2** on **Java 25**.
2. Drop `CombatTag-1.0.0.jar` into `plugins/`.
3. Start the server, then edit `plugins/CombatTag/config.yml` and `messages.yml`.
4. Apply changes with `/cta reload` — no restart needed.

### Building from source

```bash
cd CombatTag
mvn -B clean package     # or: mvn -B clean verify  (runs the unit tests)
```

The jar lands in `target/CombatTag-1.0.0.jar`. JDK **25** is required
(`maven.compiler.release = 25`). An offline sanity check that needs no JDK:

```bash
python3 tools/check_consistency.py
```

---

## 🎮 Commands

### Player — `/combattag` · aliases `/ct`, `/combat`, `/ctag`

| Command | Short | Description | Permission |
|---------|-------|-------------|------------|
| `/ct` | — | Your current combat status | `combattag.use` |
| `/ct status` | `/ct s` | Same as above | `combattag.use` |
| `/ct time` | `/ct t` | Remaining combat time | `combattag.use` |
| `/ct gui` | `/ct g` | Open the combat status GUI | `combattag.gui` |
| `/ct check <player>` | `/ct c <player>` | Is that player in combat? | `combattag.check` |
| `/ct info` | `/ct i` | What is blocked, and for how long | `combattag.use` |
| `/ct help` | `/ct h` | Command list | `combattag.use` |

### Admin — `/combattagadmin` · aliases `/cta`, `/ctadmin`, `/combatadmin`

| Command | Short | Description | Permission |
|---------|-------|-------------|------------|
| `/cta tag <player> [time]` | `/cta t` | Tag someone (`10`, `30s`, `2m`, `1h`) | `combattag.admin.tag` |
| `/cta untag <player>` | `/cta u` | Remove a tag | `combattag.admin.untag` |
| `/cta clear` | `/cta c` | Clear **all** tags | `combattag.admin.clear` |
| `/cta list` | `/cta l` | Everyone currently tagged | `combattag.admin.list` |
| `/cta exempt <player>` | `/cta e` | Toggle tagging exemption | `combattag.admin.exempt` |
| `/cta duration <time>` | `/cta d` | Change the tag duration live (saved to config) | `combattag.admin.duration` |
| `/cta bypass` | `/cta b` | Show the live state of every bypass | `combattag.admin.bypass` |
| `/cta bypass on\|off` | `/cta b off` | Master switch — `off` enforces **everything** for everyone | `combattag.admin.bypass` |
| `/cta bypass <key> [true\|false]` | `/cta b shop false` | Toggle one bypass (`tag`, `shop`, `teleport`, `easymending`, `command`, `combatlog`) | `combattag.admin.bypass` |
| `/cta stats` | `/cta st` | Tags, blocked actions, combat logs | `combattag.admin` |
| `/cta gui` | `/cta g` | Open the admin panel | `combattag.admin` |
| `/cta reload` | `/cta rl` | Reload `config.yml` + `messages.yml` | `combattag.admin.reload` |

Both commands have **full, permission-aware tab completion** (sub-commands, online player
names, duration suggestions).

---

## 🔐 Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `combattag.use` | everyone | Use `/ct` |
| `combattag.gui` | everyone | Open the status GUI |
| `combattag.check` | op | Check other players |
| `combattag.notify` | op | Receive "player X was blocked" notifications |
| `combattag.admin` | op | All admin commands (parent of the ones below) |
| `combattag.admin.tag` / `.untag` / `.clear` / `.list` / `.exempt` / `.duration` / `.bypass` / `.reload` | op | Individual admin actions |
| `combattag.*` | op | All player + admin permissions (**bypasses stay off**) |

### Bypass system — live, no reload needed

Bypasses are evaluated **per event, against the current configuration**. Switching a bypass
off makes the matching restriction **fully functional immediately** — no `/cta reload`, no
server restart, and no re-login for the affected players. The same is true in reverse.

```yaml
bypass:
  enabled: true        # master switch: false = nobody bypasses anything, ever
  permissions: true    # honour the combattag.bypass.* permissions at all?
  op-bypasses: false   # do operators bypass automatically? (off by default)
  allow:
    tag: true
    shop: true
    teleport: true     # also covers ender pearls and chorus fruit
    easymending: true
    command: true
    combatlog: true
```

Three layers, all checked live, all of which must pass for a bypass to apply:

1. `bypass.enabled` — the master switch. While it is `false`, **every** restriction is
   enforced for **everyone**: permissions, `combattag.bypass.*`, and OP status are all ignored.
2. `bypass.allow.<key>` — the individual switch for that restriction.
3. The player actually holding the permission (or being OP, if `op-bypasses` is on).

Toggle any of it at runtime with `/cta bypass …` or the **Bypass system** button in
`/cta gui`; changes apply to the very next event and are written back to `config.yml`.

### Bypass permissions — **off by default, even for operators**

| Permission | Effect |
|------------|--------|
| `combattag.bypass.tag` | Never get combat tagged at all |
| `combattag.bypass.shop` | Use shops while tagged |
| `combattag.bypass.teleport` | Teleport / pearl / chorus while tagged |
| `combattag.bypass.easymending` | Repair with EasyMending while tagged |
| `combattag.bypass.command` | Use blacklisted commands while tagged |
| `combattag.bypass.combatlog` | Quit while tagged without punishment |
| `combattag.bypass.*` | All of the above |

---

## ⚙️ Configuration (`config.yml`)

Everything is configurable. Highlights:

```yaml
combat:
  tag-seconds: 10                 # the default combat duration
  projectile-tag-seconds: 10      # separate duration for ranged hits
  tag-victim: true
  tag-attacker: true
  tag-on-projectile: true
  tag-on-splash-potion: true
  tag-on-pet-damage: false
  clear-tag-on-death: true
  refresh-on-hit: true            # false = the timer is only ever extended
  disabled-worlds: ["creative", "hub"]
```

### Restrictions

```yaml
restrictions:
  shops:
    enabled: true
    commands: ["shop", "sellall", "sellgui", "ah", ...]
    inventory-titles: ["shop", "market", "auction", ...]
    inventory-holder-classes: ["economyshopgui", "shopguiplus", ...]
  teleports:
    enabled: true
    commands: ["home", "warp", "tpa", "back", "rtp", "is", "plot", ...]
    allowed-causes: ["UNKNOWN", "DISMOUNT", "EXIT_BED"]
    block-ender-pearls: true
    block-chorus-fruit: true
  easymending:
    enabled: true
    commands: ["em", "mend", "easymending", "repair", ...]
    inventory-holder-classes: ["easymending"]
  commands:
    enabled: true
    whitelist-mode: false         # true = block everything except `whitelist`
    blacklist: ["enderchest", "pv", "kit", "fly", "god", "gamemode", ...]
    whitelist: ["combattag", "msg", "r", "helpop"]
```

Command entries accept plain labels (`home`), namespaced labels
(`essentials:home`), sub-command entries (`em hand`) and trailing wildcards (`sell*`).
Namespaces are stripped before matching, so `/essentials:home` cannot slip through.

### Combat logging, display, sounds, GUI, integrations

```yaml
combat-logging:
  enabled: true
  kill-player: true
  drop-inventory: true
  broadcast: true
  log-to-console: true
  run-commands: []                # {player} and {opponent} placeholders

display:
  boss-bar: { enabled: true, color: RED, overlay: PROGRESS }
  action-bar: { enabled: true }
  title-on-tag: false
  update-interval-ticks: 10

sounds:
  enabled: true
  tag-start:      { sound: ENTITY_ENDER_DRAGON_GROWL, volume: 0.6, pitch: 1.4 }
  tag-end:        { sound: ENTITY_PLAYER_LEVELUP,     volume: 0.7, pitch: 1.6 }
  action-blocked: { sound: BLOCK_NOTE_BLOCK_BASS,     volume: 1.0, pitch: 0.6 }
  gui-open:       { sound: BLOCK_CHEST_OPEN,          volume: 0.7, pitch: 1.2 }
  gui-click:      { sound: UI_BUTTON_CLICK,           volume: 0.7, pitch: 1.0 }
  combat-log:     { sound: ENTITY_WITHER_SPAWN,       volume: 0.8, pitch: 1.0 }

gui: { enabled: true, rows: 3 }

integrations:
  placeholderapi: true
  statistics: true
  notify-admins-on-block: false
```

Sound names accept **both** the modern registry key (`block.anvil.use`) and the legacy
constant (`BLOCK_ANVIL_USE`); an unknown name falls back to the built-in default instead
of breaking the plugin.

---

## 💬 Messages (`messages.yml`)

Every string is translatable and supports **MiniMessage** (`<red>`, `<gradient:#a:#b>`)
as well as legacy `&c` / `§c` codes. Placeholders: `<seconds>`, `<time>`, `<opponent>`,
`<player>`, `<detail>`, `<count>`, `<active>`, `<tags>`, `<blocked>`, `<logs>`.

---

## 🧩 PlaceholderAPI

| Placeholder | Output |
|-------------|--------|
| `%combattag_tagged%` | `true` / `false` |
| `%combattag_status%` | `In Combat` / `Safe` |
| `%combattag_seconds%` | remaining whole seconds |
| `%combattag_time%` | pretty time, e.g. `9.4s` |
| `%combattag_active%` | number of players currently tagged |

---

## 🔌 Developer API

```java
CombatTagApi api = CombatTagPlugin.getInstance().getApi();

if (api.isInCombat(player)) {
    player.sendMessage("Not now — " + api.getRemainingSeconds(player.getUniqueId()) + "s left!");
}

api.tag(player, 15);            // tag for 15 seconds
api.untag(player.getUniqueId());
int fighting = api.getActiveCount();
```

Depend on CombatTag with `softdepend: [CombatTag]` in your own `plugin.yml`.

---

## 🤝 Supported / tested integrations

| Plugin | How it is handled |
|--------|-------------------|
| **EconomyShopGUI** (+ Premium) | commands `/shop`, `/sellall`, `/sellgui`, `/eshop`, `/editshop` **and** GUI holder detection |
| **ShopGUI+, ChestShop, auction houses** | command list + GUI title/holder detection |
| **EasyMending** (this repo) | `/em`, `/mend`, `/repair`, its GUI and its sneak-click quick repair |
| **EssentialsX / CMI** | `/home`, `/warp`, `/spawn`, `/tpa`, `/back`, `/rtp`, namespaced variants |
| **HuskHomes, AdvancedTeleport, BetterRTP** | command list + the global teleport listener |
| **TeleportSigns** (this repo) | `/ts` and the sign teleport (`PlayerTeleportEvent`) |
| **PlotSquared, Towny, SkyBlock plugins** | `/plot`, `/town`, `/is`, `/island` |
| **PlaceholderAPI** | optional expansion |

Because CombatTag also cancels the **generic** `PlayerTeleportEvent`, teleport plugins
that are not on the list are still blocked — the command list only exists to give a nicer
error message *before* the other plugin starts a warmup.

---

## 🖥️ Platform notes

- **Paper / Purpur**: everything runs on the main thread through the Bukkit scheduler.
- **Folia**: `folia-supported: true`; the countdown uses the global region scheduler and
  punishments are dispatched on the correct region thread.
- **OS**: no native code, no file locking, no path assumptions — identical behaviour on
  Linux, Windows and macOS.

---

## 🧪 Tests

`mvn -B clean verify` runs JUnit 5 tests covering command matching (namespaces,
wildcards, sub-commands), duration parsing/formatting and combat-tag maths.
`tools/check_consistency.py` additionally verifies that every message key, permission and
config path used in Java actually exists in the YAML resources.

---

## 📄 License

Part of the [SuperSeller100X/MinecraftPlugins](https://github.com/SuperSeller100X/MinecraftPlugins) collection.
