# Veltryx ⚡

**The all-in-one server core for Minecraft 26.3 (Paper / Purpur / Folia), Java 25.**

Veltryx powers your server with one plugin: a custom **economy**, a serious
**anti-cheat** (with anti-xray), a **custom ban system** (`/offend`, the
DonutSMP-style one-hit ban), the **`/sus` investigation GUI** and a full
**staff toolbox**, survival shop, player settings, homes, shard tools, and
custom spawners.

- Version: **vx-0.0.5-beta** · targets Paper API 26.3 and Java 25 ·
  `folia-supported: true`. The descriptor declares `api-version: 26.2`, but
  compatibility with older servers has not been verified; 26.3 items are
  filtered out when the server does not recognize them.
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

### 🛛️ Shop, selling & QuickShop (`/shop`, `/sell`, `/quickshop`)

- The 26.3 catalogue has **1,530 survival-obtainable item types** in 11
  categories. Elytra, the dragon egg, operator-only and other unobtainable
  items are excluded. Items unknown to the running server are filtered out.
- `/shop` shows the category menu; the compass searches names and material
  IDs. Choose an item to open its purchase screen. The `+1`, `+16`, and `+32`
  controls change the amount (right-click to subtract); the paper buys the
  selected amount and the chest buys 64. The yellow shulker opens SellGUI.
- `/sell` shows sellable items in your inventory. Click one to sell that item,
  or the nether star to sell all; sale payouts appear in chat and the action
  bar. Enchantments add up to 12% per level before the safety caps; damage
  reduces value. Metadata-dependent potions, arrows, books and maps are
  **buy-only**, because a flat sell price cannot value their variants safely.
- Potion types, tipped-arrow effects, and enchanted-book enchantments and
  levels are selected before purchase. Their per-choice prices are fixed in
  `ChoiceCatalog`, not controlled by the generic `shop.yml` base price.
  Written books are supplied with a title, author, and page. Ordinary filled
  maps cover the buyer's world. Named explorer maps look for the indicated
  structure and carry a persistent destination marker; if a matching structure
  cannot be found in the buyer's world, the purchase is refunded. Camp maps
  try the available camp variants; structure lookup may take time.
- Each player has **45 initially empty QuickShop slots**. Click a slot, type a
  private search in chat (`*` browses all, `cancel` exits), choose the item
  and any variant, set an amount from 1 to 64, then save. Left-click the saved
  slot to buy that amount, right-click to edit, or shift-right-click to clear.
  Saved slots use the **current** shop price, not a cached price. The old
  `quickshop.items` list is retained for upgrades but is no longer used.
- Prices are editable in `shop.yml` and existing values are preserved on
  update. At runtime, base sell prices are capped to half the cheapest cost
  computed from the configured buys and the bundled recipe/transformation
  model; enchantment bonuses are capped too. The model covers 1,900+ expanded
  recipes and selected conversions, maximum Fortune drops and container
  returns. **It does not cover every trade, farm, datapack, plugin or game
  mechanic**, so server owners should audit their economy and trading rules.
  Very cheap outputs may have an effective sell price of zero.

### ⚙️ Personal settings (`/settings`)

Each player can toggle natural mob spawning attributed to them, phantom
spawning for them, and persistent night vision. Settings are saved across
restarts. Turning off natural spawning removes that player's chunks from
Paper's natural-spawn selection; **other players, spawners, and other spawn
mechanisms can still produce mobs nearby**. Phantom spawning is also blocked
while natural mobs are disabled. Night vision avoids overwriting an existing
external effect and removes the effect when the plugin owns it.

### 📊 Stats display

The sidebar shows cash, shards, deaths, kills, and playtime without visible
ordering numbers. Money and shard changes schedule a refresh on the next
player tick; death/kill changes refresh affected displays. Playtime uses the
periodic `scoreboard.update-seconds` interval (default **1** on new installs).
Existing configured values are preserved on upgrade, so change an older
`20` to `1` if faster playtime updates are wanted. `scoreboard.nametag`
controls whether the stat nametags are also mirrored to viewers.

### 🏷️ Auction house & orders (`/ah`, `/order`, `/orders`)

- **`/ah`** — hold an item, run `/ah sell <price-per-item>` (up to 20 active
  listings, 48h) and anyone can buy it from the `/ah` GUI with one click.
  Money goes straight from buyer to seller. "My listings" shows expired ones —
  one click reclaims your items.
- **`/order <item> <amount> <price-per-item>`** — player buy orders with the
  full price **escrowed** up front; other players sell into them instantly
  from the `/orders` GUI (with the same action-bar payout). Cancel any time to
  get the unfilled remainder back; orders auto-close after 48h and refund
  whatever was not filled.

### 🏠 Homes & teleporting (`/home`, `/tpa`, …)

- **`/sethome <name>`, `/home [name]`, `/homes`** — the first 3 homes are
  free; every extra home costs $2,500 more than the previous one ($2.5k,
  $5.0k, $7.5k, …) up to a maximum of 45 homes. Clicking a home in `/homes`
  opens its manage screen: **teleport, rename, change icon, delete**. Renames
  are typed in chat (or `cancel`). **Icons can be any Minecraft block or
  item** — the icon picker pages through every item in the game.
- **`/tpa <player>`, `/tpahere <player>`** — teleport requests, accepted with
  **`/tp`** (expires after 60s). The 10s send cooldown is tracked **per
  player**: you can ask several players at once, but the same player only
  every 10 seconds (ask A, wait 5s, ask B → A is ready again in 5s, B in
  10s). `/tp <player>` / `/tphere <player>` are instant staff teleports.

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
  consecutive samples; isolated impossible samples (lag corrections) are
  ignored completely and never touch the player. Speed only reports **grounded**
  movement — anything in the air is judged and labeled as **Fly**, so hacks are
  always named correctly.
- **Fly catches everything, fast:** flight is flagged after ~0.8s of
  unexplained airtime (jump-boost and ping aware). Vertical movement is judged
  in three tiers — real falls drain the counter, while hovering, bobbing **and
  slow "no-fall" descents** accumulate, so fake-falling fly hacks can't hide.
  Honey wall-slides and every legitimate air state (jumping, elytra, vehicles,
  riptide, levitation, slow falling, water, ladders) never flag.
- **Jesus is surface-only:** only skating across the water surface counts —
  diving in, walking on the sea floor, swimming and depth-strider/dolphin
  movement never flag, and it takes several consecutive skimming samples.
- **Kicks escalate to bans:** a player kicked twice for the same check within
  24 hours is **banned instead** — permanent for hacking (fly, speed, jesus,
  …), 30 days for xray/esp. Kicking alone used to reset the violation levels,
  which is why hacks mostly just got kicked; now repeat offenders are banned.
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

### ✦ Shards (`/shard`, `/shardtools`, `/st`)

A second currency earned **by playing**: every online player earns **1 shard
per minute** of playtime (with a small gray chat line: *"you earned 1 shard
for playing the server"*). Shards buy the shard weapons and tools — there is
deliberately **no way to buy shards with money**; they are earned, paid
between players (`/shard pay`) or given by staff (`/shard give`).

| Item | Price | What it does | Lifetime |
|---|---|---|---|
| Shard Spear | 500 ✦ | Right-click to **lunge** forward — **90% less hunger drain while lunging** | **24 h real time** |
| Shard Trident | 500 ✦ | **Riptide without water** (3 second cooldown; vanilla riptide still works in water/rain) | **24 h real time** |
| Shard Axe | 500 ✦ | **Fells whole trees at once** (max 128-log chain), 3×3 on everything else — pick **Fortune III or Silk Touch** | **24 h real time** |
| Shard Pickaxe | 500 ✦ | **3×3 mining** with **amethyst-like particles on every block mined** — pick **Fortune III or Silk Touch** | **24 h real time** |
| Void Totem | 150 ✦ | **Rescues you from a lethal void hit** — consumed, teleports you to safety, full heal + Resistance & Slow Falling | permanent |

- The weapons/tools self-destruct **24 real-time hours after purchase** —
  exactly like the ShardTools plugin — and warn you before they go.
- `/shard toggle` turns the held tool's ability on or off (each tool
  remembers its own state).
- `/shard top` shows the richest shard holders; `/shard balance` yours.
- Area mining applies the tool's Fortune/Silk Touch to **every** broken
  block and drops vanilla-style ore XP for the extras (never with Silk
  Touch), skips containers, spawners and bedrock, and replants saplings for
  the axe. All settings (chain cap, plane size, leaves) live under `shards:`
  in `config.yml`.

### 🐖 Custom spawners (`/shard spawners`)

- Open the spawner shop from `/shard shop` or directly with `/shard spawners`
  (`veltryx.shards`). **Pig, sheep, cow, iron golem, skeleton, zombie and
  creeper** spawners cost **1,000 shards each**. They are permanent and do
  not inherit shard-tool expiry.
- Place one, then **right-click** it to open its storage. The lever switches
  between normal **Spawner** mode and **Inventory** mode. Use the arrows for
  **20 pages of 45 slots**; left-click a loot stack to collect it, or
  right-click to collect one. The mode and loot survive pickup and restart.
- Inventory mode converts each permitted spawn attempt into a roll of that
  mob's server loot table, using the spawned entity but **no player killer**.
  Player-kill-only loot, Looting, XP and separate equipment drops do not
  apply. If a complete roll does not fit, that attempt produces no mob or
  loot; it resumes once there is enough space. Spawning requires the chunk to
  be loaded and still follows normal nearby-player, space, nearby-entity and
  other non-light rules. The custom light rule permits all block and sky light
  levels in both modes.
- Mine a custom spawner with **Silk Touch** to put the same spawner directly
  into your inventory, retaining its mode and contents. A full player
  inventory or a non-Silk tool prevents the break. Explosions do not destroy
  these spawners. If placement cannot be completed after the block-place
  event, the original item is returned at that location as a non-expiring,
  owner-only drop.
- Veltryx's auction house and buy orders store only material names, so **all
  spawner materials are blocked there** to prevent loss of custom data. Trade
  directly using an inventory/container that preserves item metadata. Existing
  spawner buy orders can be cancelled for their unfilled refund.
- There is no built-in owner lock on a placed spawner. Protect access with a
  claim/protection plugin that cancels `PlayerInteractEvent` as well as block
  placement/breaking. Inventory conversion respects earlier cancelled
  `SpawnerSpawnEvent`s, but no mob is actually added to the world in that
  mode: plugins listening **only** to the later `CreatureSpawnEvent` cannot
  block those loot rolls. Test protection integrations before enabling it.

### 🎲 Random teleport (`/rtp`)

- **`/rtp`** opens a world picker: **Overworld**, **Nether** or **End**
  (`/rtp overworld|nether|end` goes directly).
- The search radius is **10 million blocks** by default (the nether radius
  is divided by 8 to match its coordinate scale) — all configurable under
  `rtp:`.
- The plugin **searches for a safe block column**: never lava, magma,
  cactus, fire, campfires, sweet berries, powder snow, the void or the
  nether's bedrock roof — then counts down **3 seconds** (action bar) and
  teleports. The spot search runs asynchronously, so the server never
  stutters; `/rtp` has a 60s cooldown by default.

## 📜 Commands

| Command | Permission | Default |
|---|---|---|
| `/eco` (`/bal`, `/balance`, `/money`) | `veltryx.eco.balance` / `.pay` / `.top` / `.admin` | true / op |
| `/shop` (`/store`) [category] | `veltryx.shop` | true |
| `/sell` | `veltryx.sell` | true |
| `/shard` (`/shardtools`, `/st`) <balance\|pay\|top\|shop\|spawners\|toggle\|info> | `veltryx.shards` (give/set: `veltryx.shards.admin`) | true / op |
| `/quickshop` (`/qs`) | `veltryx.shop` | true |
| `/settings` | `veltryx.settings` | true |
| `/rtp` [overworld\|nether\|end] | `veltryx.rtp` | true |
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
> default keys to existing `config.yml`, `messages.yml`, and `shop.yml` while
> keeping existing values. The runtime message loader also falls back to the
> bundled templates for missing keys, without replacing custom translations.
> Invalid YAML is reported in the server log; fix it to restore custom values.

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
- `sqlite` — one `veltryx.db` file (driver is shaded into the Maven jar).
  Recommended for large servers and networks.
- `player-preferences.properties` and `quickshop-slots.properties` — persistent
  per-player settings and QuickShop slots, regardless of economy storage mode.
- `spawners/<uuid>.properties` — each custom spawner's type, location, mode
  and 900 slots; written through separately from YAML/SQLite. Back up this
  directory **together with world and player data**, preferably while the
  server is stopped. Many active inventory-mode spawners can increase disk
  I/O. Missing or corrupt records disable the affected spawner rather than
  recreate its loot; check the server log and restore the record.

The economy backends keep an in-memory cache as the source of truth; YAML
autosaves on an interval, SQLite writes through on every change.

## 🔌 Developer API

```java
VeltryxEconomy.get().ifPresent(eco -> {
    long cents = eco.getBalance(playerId);
    eco.withdraw(playerId, name, 1_500);            // $15.00
    String pretty = eco.format(1_500);              // "$15.00"
});
```

The built-in shop uses this economy service.

---

## 🛠️ Building

Canonical build (needs network for the Paper API):

```bash
cd Veltryx
mvn -B clean verify        # JDK 25 required — output: target/vx-0.0.5-beta.jar
```

Offline development fallback: the bundled Eclipse compiler and compile-only
API stubs build the plugin and run the smoke/regression tests. This is **not**
a real Paper compile or server test; its jar does not include the SQLite JDBC
driver. Use the Maven jar for deployment:

```bash
cd Veltryx
JAVA_BIN=/path/to/java25 ./build.sh   # output: target/vx-0.0.5-beta.jar
```

Offline consistency checks (message keys, permissions, command wiring):

```bash
cd Veltryx && python3 tools/check_consistency.py
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
| `Could not load 'plugins/Veltryx...jar' ... plugin.yml` | Not a real built jar was dropped in | Build with `mvn -B clean package` and use `target/vx-0.0.5-beta.jar` (NOT `original-...jar`, not `target/classes`) |
| `UnsupportedClassVersionError` | Server runs an old Java | Minecraft 26.x needs **Java 25** — update the server's Java |
| `api-version` / "made for a newer version" | Server is older than the plugin expects | Veltryx declares `api-version: '26.2'` and targets 26.3; send the exact server version and error |
| `config.yml is missing from the plugin jar!` | Incomplete/repacked jar | Rebuild with Maven; don't unzip/rezip the jar |

After a successful enable the console shows `[Veltryx] Veltryx vx-0.0.5-beta enabled on ...` and `plugins/Veltryx/` is created with `config.yml` and `messages.yml`.

## 🗺️ Roadmap (next versions)

- Vault / VaultUnlocked bridge so existing plugins can use the Veltryx economy
- PlaceholderAPI placeholders, Discord webhook alerts for anti-cheat flags
- Punishment GUIs, ban appeal workflow
