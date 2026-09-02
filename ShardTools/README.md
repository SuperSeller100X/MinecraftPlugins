# ShardTools ✦

**DonutSMP-style Shard economy for Minecraft 26.2** — a brand-new **Shards** currency,
self-destructing **Shard Tools** (3×3 pickaxe, tree-felling axe, 3×3 shovel), the **Shard Potion
of Haste**, enchanted **netherite armor & gear**, and a GUI **Shard Shop** — for
**Paper, Purpur and Folia**.

- **Minecraft:** 26.2 "Chaos Cubed" (`api-version: '26.2'`)
- **Servers:** Paper 26.2 · Purpur 26.2 · Folia 26.2 (`folia-supported: true`, regionized-scheduler aware)
- **Java:** 25 (OpenJDK 25 — required by Minecraft ≥ 26.1)
- **Build:** Maven, `release 25` · runs identically on **Linux, Windows and macOS**
- **No hard dependencies** — no Vault, no economy plugins, everything is self-contained

---

## What it does

### Shards — the new currency
- Every player on the server automatically earns **5 Shards every 5 minutes** (default,
  fully configurable — `/sta interval`, `/sta amount`, `/sta award off`).
- Balances persist in `plugins/ShardTools/shards.yml`, survive restarts, and are paid
  between players with `/st pay` — and can be **bought with in-game money**
  (`/st buy 100`) through any Vault economy such as EssentialsX, at a
  configurable rate (`money-shop:` in config.yml, `enabled: false` turns it
  off completely).
- Leaderboard with `/st top`.

### Shard Tools — like DonutSMP
| Item | Price | Enchants (pre-applied) | Effect | Lifetime |
|---|---|---|---|---|
| Shard Pickaxe (Fortune III) | 3,000 ✦ | Efficiency V, Unbreaking III, Mending, Fortune III | Mines a **3×3 plane** — same shared block list as axe & shovel | **24 h real time** |
| Shard Pickaxe (Silk Touch) | 3,000 ✦ | Efficiency V, Unbreaking III, Mending, Silk Touch | Mines a **3×3 plane** | **24 h real time** |
| Shard Axe (Fortune III) | 3,000 ✦ | Efficiency V, Unbreaking III, Mending, Fortune III | **Fells whole trees** (log = whole tree) + **3×3 on everything else** | **24 h real time** |
| Shard Axe (Silk Touch) | 3,000 ✦ | Efficiency V, Unbreaking III, Mending, Silk Touch | Fells whole trees + 3×3 on everything else | **24 h real time** |
| Shard Shovel (Fortune III) | 3,000 ✦ | Efficiency V, Unbreaking III, Mending, Fortune III | Digs a **3×3 plane** — same shared block list as pickaxe & axe | **24 h real time** |
| Shard Shovel (Silk Touch) | 3,000 ✦ | Efficiency V, Unbreaking III, Mending, Silk Touch | Digs a **3×3 plane** — same shared block list as pickaxe & axe | **24 h real time** |
| Shard Potion of Haste | 500 ✦ | — | **Haste II for 1 hour** when drunk (a portable beacon) | **24 h real time** |

Area breaking details (all configurable):
- Silk Touch / Fortune are applied to **every** broken block, not just the centre one.
- Vanilla-style **ore XP** is dropped for extra blocks (`behavior.ore-xp`).
- Containers, bedrock and a configurable protected-materials list are never hit.
- The 3×3 plane follows your **look direction** (mine a floor or a wall naturally).
- **Pickaxe, axe and shovel 3×3 the same blocks** — one shared `behavior.area-materials`
  list (plus every `*_ORE` and every log). The axe fells a whole tree when you break
  a log, and 3×3s like the others on any other block.
- Radius configurable (`behavior.area-radius`: 1 = 3×3, 2 = 5×5). Tree felling caps at
  `behavior.tree.max-blocks` (default 256) with same-log-type chaining **and breaks the
  tree's leaves too** (`behavior.tree.break-leaves`), exactly like DonutSMP's amethyst axe.
- **`/st toggle`** pauses/resumes the ability of the shard tool in hand (stored on the
  item itself, so each tool remembers its own state) — only works while holding a shard
  tool that actually has an ability.

### Enchanted vanilla gear — no gimmicks
Shop items **without an ability** (`behavior: NONE` — the netherite armor, sword, hoe,
mace, bow, crossbow, …) are handed out as **plain vanilla items with only their
enchantments applied**: no custom name, no color, no rarity change, no lore, no plugin
data. The `name`/`lore` in config.yml only style their **shop icon**. All shop icons
render **non-italic** (Minecraft's default italics for custom names are disabled).

### Buy extra time in the shop — stackable
Clicking an **expiring shard item** or the **haste potion** in the shop opens an
**extra-time dialog** (available to everyone) instead of buying instantly:

- **Shard tools** (and any other expiring item): each step adds **+1 hour of real-time
  lifetime** on top before the self-destruct, for **+100 ✦** per step (defaults).
- **Haste potion**: each step makes the **effect last +30 minutes longer** when drunk,
  for **+50 ✦** per step (defaults) — the item's 24 h shelf life stays unchanged.
- Steps **stack**: +6 hours of haste = 12 × 30 min = **+600 ✦** on top of the base price.
  Shift-click the +/- buttons to move in jumps of 5 steps.
- Step size, step price and the per-purchase cap are configurable separately for tools
  and the potion under `shop.time-extension:` (or turn the whole feature off with
  `enabled: false`).

The dialog shows the resulting lifetime/effect duration and the total price before you
confirm; the purchased item's lore and `/st info` reflect the extended time.

### DonutSMP amethyst sounds & particles — purple star included
The real DonutSMP shard-tool sounds, all under `effects:` in config.yml:

- **Mining** — **ONE amethyst block step** sound (`block.amethyst_block.step`)
  per **use** of the tool — a 3×3 break plays it once, not nine times — plus a
  subtle **purple portal particle** at each broken block. The list is a random
  pick per use; add e.g. `block.amethyst_cluster.step` (crunchier) for variety.
- **On equip** — the **amethyst resonate** sound (`block.amethyst_block.resonate`)
  plus a purple particle ring: triggers when you pull a shard tool **into your
  hand** (hotbar switch), put shard **armor** on, or drink a shard potion.
- **On purchase** — resonate + purple ring.
- The currency icon is a **purple star** (`currency.symbol:
  "<light_purple>✦</light_purple>"`); shard tool names render in light purple
  (`&d` on DonutSMP); the shop shows your balance on an amethyst shard icon.
- Every sound entry supports its own volume/pitch: `"sound-id volume pitch"`.

## Protection
Shard items cannot be renamed or merged in **anvils** or disenchanted in **grindstones**
(both toggleable), so the tag and lifetime can't be wiped.

---

## Commands

Player command `/shardtools` with aliases **`/shard`** and **`/st`**; admin command
`/shardtoolsadmin` with aliases **`/sta`**, **`/stadmin`** and **`/shardadmin`** — every
subcommand has a short form. Console works for all admin commands.

### Player commands — `/st`

| Command | Short | Description | Permission |
|---|---|---|---|
| `/st help` | `h`, `?` | Command overview | — |
| `/st shop` | `s` | Open the shard shop GUI | `shardtools.shop` |
| `/st toggle` | `tg`, `ability`, `ab` | Toggle the **ability of the shard tool in hand** (3×3 mining / tree felling) on or off — only works while holding a shard tool that has an ability | `shardtools.toggle` |
| `/st balance [player]` | `bal`, `b` | Check your (or another player's) balance | `shardtools.balance` / `.others` |
| `/st pay <player> <amount>` | `p` | Send shards (supports `2.5k`, `1m`, …) | `shardtools.pay` |
| `/st buy <shards>` | — | **Buy shards with in-game money** (Vault economy, e.g. EssentialsX) | `shardtools.buy` |
| `/st top [n]` | `t` | Shard leaderboard (default top 10, max 25) | `shardtools.top` |
| `/st info` | `i` | Inspect the held shard item (remaining lifetime) | `shardtools.info` |

### Admin commands — `/sta`

| Command | Short | Description | Permission |
|---|---|---|---|
| `/sta help` | `h`, `?` | Admin command overview | — |
| `/sta give <player> <item> [amount]` | `g` | Give any shop item | `shardtools.give` |
| `/sta items` | `list`, `l` | List all item ids + prices | `shardtools.items` |
| `/sta add <id> <material> <price> [lifetimeHours] [behavior]` | `a`, `create` | **Add a new shop item** to config.yml and the live shop | `shardtools.manage` |
| `/sta remove <id>` | `rm`, `delete`, `del` | **Remove a shop item** from config.yml and the live shop | `shardtools.manage` |
| `/sta edit <id> <property> <value>` | `e` | **Edit a shop item**: `name`, `material`, `price`, `lifetime` (hours), `behavior`, `enchants` (comma list like `efficiency:5,mending:1`, or `none`) or `lore` (MiniMessage lines separated by `\|`, or `none`) | `shardtools.manage` |
| `/sta setprice <item> <price>` | `price`, `sp` | Change a price at runtime | `shardtools.setprice` |
| `/sta shards <player> <give\|take\|set> <amount>` | `sh`, `eco` | Edit balances | `shardtools.economy` |
| `/sta interval <minutes>` | `iv` | Set the income interval | `shardtools.settings` |
| `/sta amount <shards>` | `am` | Set the income amount | `shardtools.settings` |
| `/sta award <on\|off>` | `aw` | Toggle automatic income | `shardtools.settings` |
| `/sta reload` | `rl` | Reload config, messages & shop | `shardtools.reload` |

Tab completion covers every subcommand, player names, item ids, edit properties,
behaviors, and sensible amounts. Runtime changes (`/sta setprice`, `/sta interval`,
`/sta amount`, `/sta award`) persist in `plugins/ShardTools/runtime.yml` and survive
restarts *and* `/sta reload`; `/sta add`, `/sta remove` and `/sta edit` write straight
into `config.yml`.

## Permissions

| Node | Default | Description |
|---|---|---|
| `shardtools.use` | everyone | Use shard tool powers (3×3, tree fell, haste potion) |
| `shardtools.toggle` | everyone | `/st toggle` — pause/resume the held shard tool's ability |
| `shardtools.shop` | everyone | Open/buy from the shard shop |
| `shardtools.balance` | everyone | Check own balance |
| `shardtools.balance.others` | op | Check other players' balances |
| `shardtools.pay` | everyone | Send shards |
| `shardtools.buy` | everyone | Buy shards with in-game money (needs Vault + economy on the server) |
| `shardtools.top` | everyone | Leaderboard |
| `shardtools.info` | everyone | Inspect held shard item |
| `shardtools.give` | op | `/sta give` |
| `shardtools.items` | op | `/sta items` |
| `shardtools.economy` | op | `/sta shards` |
| `shardtools.setprice` | op | `/sta setprice` |
| `shardtools.manage` | op | `/sta add`, `/sta remove`, `/sta edit` |
| `shardtools.settings` | op | `/sta interval`, `/sta amount`, `/sta award` |
| `shardtools.reload` | op | `/sta reload` |
| `shardtools.admin` | op | All admin nodes combined |
| `shardtools.*` | op | Everything |

## Configuration

Everything is configurable — see `src/main/resources/config.yml`. Highlights:

```yaml
currency.symbol: "✦"          # shown everywhere
award: {enabled: true, interval-minutes: 5, amount: 5, announce: true}
items:                         # full catalog: material, name, price,
                               # lifetime-hours, behavior, enchants, lore
behavior:
  require-sneak: false         # area breaking always on (DonutSMP style)
  area-radius: 1               # 1 = 3x3, 2 = 5x5
  protect-containers: true
  protected-materials: [BEDROCK, ...]
  area-materials: [STONE, DIRT, SAND, ...]   # shared 3x3 list for all tools
  tree: {max-blocks: 256, same-material-only: true, replant: false}
expiry:
  sweep-seconds: 30            # removal + lore countdown refresh cadence
  warn-minutes: [60, 10, 1]
haste-potion: {duration-hours: 1, amplifier: 1}   # 1h effect; item destructs after 24h
shop: {confirm: false, rows: 6}
shop.time-extension:           # buy extra time in the shop (stackable)
  enabled: true
  tool:   {step-minutes: 60, step-price: 100, max-steps: 24}  # +1h / 100 ✦
  potion: {step-minutes: 30, step-price: 50,  max-steps: 12}  # +30min effect / 50 ✦
money-shop:                  # /st buy - shards for in-game money via Vault
  enabled: true              # false = feature completely off
  cost-per-shard: 10.0       # /st buy 100 costs 1,000 money
  min-purchase: 1
  max-purchase: 100000
effects:                      # DonutSMP amethyst sounds
  mine-sound: {enabled: true, sounds: ["block.amethyst_block.step 1.0 1.0"]}   # ONCE per use
  mine-particles: {enabled: true, id: PORTAL, count: 1}   # subtle
  equip-sound: {enabled: true, sound: "block.amethyst_block.resonate 1.0 1.0"}
  equip-particles: {enabled: true, id: PORTAL, count: 10}
```

All messages live in `messages.yml` (MiniMessage format) with `%placeholders%`.
Missing message keys are **auto-merged** from the packaged defaults on load, so
an outdated messages.yml can never display raw keys. Shard balances are stored
in `shards.yml`, runtime overrides in `runtime.yml`.

## Folia, Purpur & cross-platform notes

- **Folia:** `folia-supported: true`; all scheduling goes through a
  `PlatformScheduler` that uses Folia's regionized **global/async/entity**
  schedulers (via reflection) and falls back to the Bukkit scheduler on
  Paper/Purpur. Inventory work always runs on the owning entity's region thread.
- **Purpur:** Purpur 26.2 is Paper-compatible — the plugin runs unchanged.
- **OS:** pure Java + `java.nio`/`java.io` file handling with explicit UTF-8 — no
  native code, no OS-specific paths. Works on Linux, Windows and macOS (x64 & ARM64)
  wherever a Java 25 VM runs.

## Building

### Canonical build — Maven + JDK 25

```bash
cd ShardTools
mvn -B clean package        # requires JDK 25 (Temurin 25 recommended)
# → target/ShardTools-1.0.0.jar
```

`pom.xml` compiles with `release 25` against `paper-api 26.2.build.115-stable`
(https://repo.papermc.io). The CI workflow in `.github/workflows/build.yml` runs this
Maven build on Temurin 25 and additionally **boots a real Paper 26.2 server** with the
plugin and exercises `st help`, `sta interval`, `sta setprice`, `sta add`, `sta rl`, … (copy it to the
repo root `.github/workflows/` to activate it).

### Offline build (no Maven Central access)

The full source can be compiled without Maven Central: generate the compile-only API
stubs with `python3 stub-api/generate.py`, compile with the Eclipse compiler (ECJ)
against them on any Java 25 runtime (e.g. `pip install jdk4py`), run the 59
pure-logic smoke tests in `smoke/SmokeTest.java`, and zip the classes plus
`src/main/resources` into a jar (stubs must **never** be packaged). The CI workflow's
smoke step shows the exact `javac` invocation. Note: the offline ECJ used for
verification is 3.25, so the source is kept Java-15-syntax compatible; the canonical
Maven build compiles the same sources with `release 25`.

## Installation

1. Drop `ShardTools-1.0.0.jar` into your server's `plugins/` folder
   (Paper / Purpur / Folia 26.2, Java 25).
2. Start the server — `config.yml` and `messages.yml` are generated in
   `plugins/ShardTools/`.
3. Optional: tweak prices, income interval/amount, lifetimes and messages, then `/sta rl`.

## Item ids (for `/sta give`, `/sta setprice`, `/sta edit`)

`shard_pickaxe_fortune`, `shard_pickaxe_silk`, `shard_axe_fortune`, `shard_axe_silk`,
`shard_shovel_fortune`, `shard_shovel_silk`, `haste_potion`, `netherite_pickaxe_fortune`,
`netherite_pickaxe_silk`, `netherite_shovel`, `netherite_axe`, `netherite_hoe`,
`netherite_helmet`, `netherite_chestplate`, `netherite_leggings`, `netherite_boots`,
`netherite_sword`, `mace`, `crossbow`, `bow` — or run `/sta items` — or create your own with `/sta add`.

## Version verification (2026-08-24)

- Minecraft **26.2 "Chaos Cubed"** — current stable (released 2026-06-16); year-based
  versioning since 2026; **Java 25 required** for ≥ 26.1.
- **Paper 26.2** stable builds via fill.papermc.io; `paper-api 26.2.build.115-stable`
  (latest: 116) verified on repo.papermc.io.
- **Purpur 26.2** builds available since June 2026.
- **Folia 26.2** first builds published late July 2026 (ver/26.2.x branch).
- DonutSMP shard prices per donutsmp.wiki / donut.today (June–July 2026).
- "Amethyst Items" = DonutSMP's shard tools (the Shard Pickaxe was formerly the
  Amethyst Pickaxe; DonutSMP drill sounds + purple portal particles; shard axe
  fells logs *and* leaves) — per donutsmp.wiki / dsmp.fandom.com.
