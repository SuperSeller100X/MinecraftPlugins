# Veltryx ⚡

**The all-in-one server core for Minecraft 26.3 (Paper / Purpur / Folia), Java 25.**

Veltryx powers your server with one plugin: a custom **economy**, a serious
**anti-cheat** (with anti-xray), a **custom ban system** (`/offend`, the
DonutSMP-style one-hit ban), the **`/sus` investigation GUI** and a full
**staff toolbox**. More modules (shop, and everything else) land in the same
plugin in the next versions — the economy API is already built for it.

- Version: **vx-v0.0.1-beta** · api-version `26.2` (compatibility floor — loads on Paper/Purpur 26.2 **and** 26.3; built against paper-api 26.3) · `folia-supported: true`
- Storage: **YAML** (default) or **SQLite** — one config switch
- No required dependencies. Vault bridge and PlaceholderAPI hooks are planned.

---

## ✨ Features

### 💰 Economy (`/eco`)

Exact cent-based balances (no floating point drift), persistent across
restarts and shared by every future Veltryx module through the public
`VeltryxEconomy` API.

- `/eco` · `/bal` · `/balance` · `/money` — check your balance
- `/eco pay <player> <amount>` — pay online players (`1.5k`, `2m`, `$1,000` all parse)
- `/eco top` — money leaderboard
- `/eco give|take|set|reset <player> <amount>` — admin management (offline-friendly)
- Starting balance for new players, configurable minimum transfer, saturated
  math (balances can never overflow or go negative)

### 🛛️ Shop & selling (`/shop`, `/sell`)

- **`/shop` (`/store`)** — a category GUI with **almost every obtainable item**
  (970+): blocks, woods, ores, tools, armor, food, redstone, mob drops and
  more. Elytra and operator-only items (command blocks, barriers, …) are not
  sold. Left-click buys 1, shift-click buys a stack; every item shows buy and
  sell price plus your balance.
- **`/sell`** — a GUI listing everything sellable in your inventory with its
  payout. Click an item to sell all of it, or sell everything with one button.
  Sell prices are always below shop buy prices (~65% for gathered resources,
  ~50% for crafted items).
- **Fair, exploit-proof prices.** You earn money by *doing something* — mining,
  farming, hunting — never by market games: buying materials, crafting and
  selling **always loses money** (verified against 170+ recipes in the smoke
  tests, including compression/decompression loops like ingot ↔ block). Prices
  live in `shop.yml`, are editable per server, and new items are added on
  updates while keeping your values.
- New players still start with **$100** (`economy.starting-balance`).

### 🛡️ Anti-cheat

Server-side detections that auto-escalate: **flag → alert staff → kick →
automatic ban**, all configurable per check. Anti-cheat respects **ping and
TPS** at every step — tolerances scale up on laggy connections, checks are
skipped entirely on terrible ping/low TPS, and kicks/bans only fire while the
connection and server are healthy. A join-grace period and continuous
violation-level decay keep false positives near zero.

| Group | Checks |
|---|---|
| Movement | Fly (hover, slow descent, bobbing, ascend), Speed (sprint-jump aware), Jesus (water-skating), Phase (noclip), Timer |
| Combat | AutoClicker (CPS + robot-consistency), Reach (hitbox-precise), MultiAura, Aim (hits while looking away) |
| Interact | FastPlace/FastBreak, FastUse, InteractReach |
| Mining | X-Ray statistics (hidden-ore scoring) + block obfuscation |

- **Server authority (movement):** the server has the last word — movement that
  is impossible for vanilla clients (`speed-hard-cap: 1.75` × the limit) is
  **clamped smoothly** (the player glides at the cap in their own direction —
  no rubber-band, no lag feel, no getting stuck), so speed hacks physically
  cannot outrun the server. Confirmed flight gets a gentle gravity-like pull
  down that looks like normal falling. Borderline speeds are tolerated and
  never clamped, and clamped movement never feeds any violation level.
- **Speed without false flags:** the limit covers the fastest legitimate
  vanilla movement — sprint-jump chains (~7.5–8 b/s, the way everyone moves),
  speed potions, ice, soul speed and slime launches all widen it automatically,
  and it grows with ping/lag. Mild excess is only counted after three
  consecutive samples; falls, elytra, jumping and lag corrections never flag.
- **Anti-xray (obfuscation):** **every** ore (configurable) is sent to clients
  as stone the moment the client receives the chunk — X-Ray texture packs and
  ESP overlays show plain stone across the whole view distance, with **no
  visible flash** (the fakes are queued right behind the chunk packet itself,
  so ore positions can never be cached). Ore becomes visible again only when a
  player is close enough to legitimately see it (`reveal-radius: 5`) or digs it
  out — once seen it is never re-hidden, so nothing flickers. Client-side only;
  combine with Paper's built-in anti-xray for belt and braces.
- **X-Ray detection:** every broken ore is scored by how hidden it was
  (0–6 exposed faces). Players who keep pulling ores out of solid rock at an
  impossible ratio get flagged and escalated like any other check.
- **Creative and spectator players are never checked** — neither are players
  with `veltryx.anticheat.bypass`, nor legitimate flight/height (elytra, boats,
  horses, vehicles, riptide, levitation, slow falling, falling/jumping).
- **Every flag is stored as evidence (player, check, VL, detail, time) and shown
  in `/sus` and on ban records.**

### 🔨 `/offend` — the custom ban system

One command, permanent consequences:

```
/offend player1 xray/esp     →  30 day ban  (category: xray-esp)
/offend player1 hacking      →  permanent   (category: hacking)
/offend player1 flying       →  permanent   (alias → hacking)
/offend player1 xray tunneling from spawn   (free-text details, evidence appended)
/offend player1              →  opens the ban menu GUI (all reasons, categories
                                and durations — click a reason to ban)
```

There is also a general **`/ban`** command using the same custom ban system
(never Minecraft's vanilla ban list — it shadows vanilla `/ban`):

```
/ban player1 7d cheating at PvP     →  7 day ban
/ban player1 griefing               →  permanent ban
/ban player1 perm cheating          →  permanent ban
```

- Every ban shows the **custom ban screen**: reason, category, ban ID, expiry,
  staff member and appeal URL.
- The player's recent anti-cheat flags are attached to the ban automatically.
- `/bans [player]` — active bans or a player's ban history · `/unban <player|id>`
- Reasons, categories, durations and aliases are fully configurable in
  `config.yml` (defaults: `xray`/`esp` = 30d, `hacking`/`fly`/`speed`/… = perm).
- Bans live in Veltryx storage (not the vanilla ban list) and are enforced at
  login — expired bans drop off automatically.

### 🔍 `/sus` — the investigation GUI

A chest GUI listing every online player sorted by **suspicion score**
(violation levels + recent flags). Click a head to open the detail view:

- per-check violation levels and the last 8 stored flags (check, VL, detail, time)
- one-click staff actions: **Teleport**, **Freeze**, **Kick**,
  **Offend** (opens the ban menu with every configured reason, category
  and duration — click a reason to ban)

`/sus <player>` jumps straight into the detail view.

### 🧰 Admin toolbox

| Command | What it does |
|---|---|
| `/lookup <player>` (`/whois`) | Identity, playtime, joins, balance, ping, moderation record |
| `/freeze <player>` | Freeze in place — movement, interaction, damage and teleport locked |
| `/vanish` (`/v`) | Staff vanish (optional persistence across relogs) |
| `/staffchat <msg>` (`/sc`) | Private staff chat (+ `toggle` to opt out) |
| `/mute <player> [duration] [reason]` · `/unmute` | Chat mutes (default 1h) |
| `/warn <player> <reason>` · `/warns <player>` | Warnings with auto-escalation at the threshold (kick/mute/ban) |
| `/notes <player> add\|list\|clear` | Persistent staff notes |
| `/report <player> <reason>` · `/reports` | Player reports + staff review queue with resolve |
| `/broadcast <msg>` (`/bc`) | Formatted server announcements |
| `/clearchat` (`/cc`) | Clear chat for non-staff |
| `/slowchat [seconds\|off]` | Slow down chat (staff bypass) |
| `/ping [player]` · `/tps` | Diagnostics |
| `/veltryx reload\|version\|info` (`/vx`) | Plugin administration |

---

## 📜 Commands

| Command | Permission | Default |
|---|---|---|
| `/eco` (`/bal`, `/balance`, `/money`) | `veltryx.eco.balance` / `.pay` / `.top` / `.admin` | true / op |
| `/shop` (`/store`) [category] | `veltryx.shop` | true |
| `/sell` | `veltryx.sell` | true |
| `/offend` | `veltryx.offend` | op |
| `/ban` | `veltryx.bans.ban` | op |
| `/bans` · `/unban` | `veltryx.bans.manage` | op |
| `/sus` | `veltryx.sus` | op |
| `/lookup` | `veltryx.moderation.lookup` | op |
| `/freeze` | `veltryx.moderation.freeze` | op |
| `/vanish` | `veltryx.staff.vanish` | op |
| `/staffchat` | `veltryx.staff.chat` | op |
| `/mute` · `/unmute` | `veltryx.moderation.mute` | op |
| `/warn` · `/warns` | `veltryx.moderation.warn` | op |
| `/notes` | `veltryx.moderation.notes` | op |
| `/report` | `veltryx.report` | true |
| `/reports` | `veltryx.moderation.reports` | op |
| `/broadcast` | `veltryx.admin.broadcast` | op |
| `/clearchat` · `/slowchat` | `veltryx.admin.chat` | op |
| `/ping` (`/ping <player>` → `veltryx.ping.others`) | `veltryx.ping` | true |
| `/tps` | `veltryx.tps` | true |
| `/veltryx` (`/vx`) | `veltryx.admin.reload` (for reload) | — |

Umbrella nodes: `veltryx.*`, `veltryx.eco.*`, `veltryx.moderation.*`,
`veltryx.staff.*`, `veltryx.admin.*`. Anti-cheat: `veltryx.anticheat.alerts`
(receive flags, op) and `veltryx.anticheat.bypass` (never checked, op).
Every command has tab completion.

---

## ⚙️ Configuration

`config.yml` (reload with `/veltryx reload`) — the important knobs:

> **The config updates itself.** On every start and reload the plugin adds new
> default keys (and new message entries) to your existing `config.yml` and
> `messages.yml` — your own values and comments are always kept. After a plugin
> update you never have to delete the config; missing settings simply appear.

```yaml
storage:
  type: yaml              # or "sqlite"
economy:
  starting-balance: 100
anticheat:
  max-ping: 350           # skip checks above this ping
  min-tps: 18.0           # skip checks below this TPS
  punish-max-ping: 250    # kicks/bans need a healthy connection
  punish-min-tps: 19.0
  server-authority: true    # roll back impossible movement (speed/flight hacks)
  speed-hard-cap: 1.75      # limit x this = impossible -> rolled back
  checks:
    movement.fly:  { alert-vl: 6, kick-vl: 18, ban-vl: 40, ban-duration: 7d }
  xray:
    obfuscation: true     # hide buried ores from clients
punishments:
  offend-reasons:
    xray:    { duration: 30d,  category: xray-esp }
    hacking: { duration: perm, category: hacking }
moderation:
  warn-threshold: 3
  warn-action: kick       # kick | mute | ban
```

All messages are MiniMessage templates in `messages.yml` (placeholders use
`{name}` style and are always inserted unparsed — player input can never
inject formatting).

## 💾 Storage

- `yaml` — `economy.yml`, `profiles.yml`, `moderation.yml`, `flags.yml` in the
  plugin folder. Great for most servers.
- `sqlite` — one `veltryx.db` file (driver is shaded into the jar).
  Recommended for large servers and networks.

Both keep an in-memory cache as the source of truth; YAML autosaves on an
interval, SQLite writes through on every change.

## 🔌 Developer API

```java
VeltryxEconomy.get().ifPresent(eco -> {
    long cents = eco.getBalance(playerId);
    eco.withdraw(playerId, name, 1_500);            // $15.00
    String pretty = eco.format(1_500);              // "$15.00"
});
```

The upcoming shop module (next version) uses exactly this API.

---

## 🛠️ Building

Canonical build (needs network for the Paper API):

```bash
cd Veltryx
mvn -B clean verify        # JDK 25 required — output: target/Veltryx-vx-v0.0.1-beta.jar
```

Offline fallback (no Maven Central access): the bundled Eclipse compiler and
compile-only API stubs build the plugin and run the smoke tests:

```bash
cd Veltryx
JAVA_BIN=/path/to/java25 ./build.sh   # output: target/Veltryx-offline.jar
```

Offline consistency checks (message keys, permissions, command wiring):

```bash
python3 Veltryx/tools/check_consistency.py
```

A CI workflow that runs the consistency checks, the unit tests and
`mvn clean verify` on Temurin 25 is included at
`Veltryx/.github/workflows/build.yml` (copy it to the repo root
`.github/workflows/` to activate it).

## 🚑 Troubleshooting

### The plugin name shows red (in `/plugins` or the console)

A red name means the plugin did **not** enable. Check the console — the fix depends on what is printed:

| Console output | Cause | Fix |
|---|---|---|
| `Veltryx failed to enable during stage '...'` + stack | A bug during a specific startup stage | Send the whole error — the stage name pinpoints it |
| `Could not load 'plugins/Veltryx...jar' ... plugin.yml` | Not a real built jar was dropped in | Build with `mvn -B clean package` and use `target/Veltryx-vx-v0.0.1-beta.jar` (NOT `original-...jar`, not `target/classes`) |
| `UnsupportedClassVersionError` | Server runs an old Java | Minecraft 26.x needs **Java 25** — update the server's Java |
| `api-version` / "made for a newer version" | Server is older than the plugin expects | Veltryx declares `api-version: '26.2'`, so 26.2+ servers accept it — if you still see this, tell us the exact server version |
| `config.yml is missing from the plugin jar!` | Incomplete/repacked jar | Rebuild with Maven; don't unzip/rezip the jar |

After a successful enable the console shows `[Veltryx] Veltryx vx-v0.0.1-beta enabled on ...` and `plugins/Veltryx/` is created with `config.yml` and `messages.yml`.

## 🗺️ Roadmap (next versions)

- **Shop** — the full server shop, powered by the built-in economy API
- Vault / VaultUnlocked bridge so existing plugins can use the Veltryx economy
- PlaceholderAPI placeholders, Discord webhook alerts for anti-cheat flags
- Punishment GUIs, ban appeal workflow
