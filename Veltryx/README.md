# Veltryx ⚡

**The all-in-one server core for Minecraft 26.3 (Paper / Purpur / Folia), Java 25.**

Veltryx powers your server with one plugin: a custom **economy**, a serious
**anti-cheat** (with anti-xray), a **custom ban system** (`/offend`, the
DonutSMP-style one-hit ban), the **`/sus` investigation GUI** and a full
**staff toolbox**, survival shop, player settings, homes, shard tools, and
custom spawners.

- Version: **vx-0.0.6-beta** · targets Paper API 26.3 and Java 25 ·
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
- `/eco give|take|set <player> <amount>` and `/eco reset <player>` — admin management
- Starting balance for new players and configurable minimum transfer.
  `/eco` amounts support case-insensitive `k/m/b/t/q/d/g/l`, where the
  multipliers are respectively **10³/10⁶/10⁹/10¹²/10¹⁵/10¹⁸/10²¹/10²⁴**.
  Balances and transfers use exact arbitrary-precision cents; untrusted amount
  inputs are limited to 160 characters and sub-cent results round half up.
  Auction listings and buy orders also use exact, uncapped total prices; shop
  catalogue prices retain their own bounds.

### 🛒 Shop, selling & QuickShop (`/shop`, `/sell`, `/quickshop`)

- The 26.3 catalogue has **1,530 survival-obtainable item types** in 11
  categories, with positive default buy and sell prices. Elytra, the dragon
  egg, operator-only and other unobtainable items are excluded; materials
  unknown to the running server are filtered out.
- `/shop` opens the category menu. The compass opens a private sign editor;
  `/shop search <query>` also works. Search accepts names or material IDs,
  words in any order, `*` for all items, category filters such as `cat:tools`,
  and base-price filters such as `buy<=50` and `sell>=1`. For example,
  `/shop search cat:tools sword diamond`. Variant prices can differ from the
  base prices used by the filters. Blank text or `cancel` closes the prompt.
- In categories and search results, **left-click to buy** or **right-click to
  sell**. Choose a quantity from 1 to 64, review the total, and click the
  paper to confirm. `+1`, `+16` and `+32` adjust it (right-click to subtract);
  the chest selects 64. Shop sales use matching **storage** items, not armor
  or offhand. The preview displays the exact payout and up to eight selected
  stack portions; changed items or prices invalidate the quote. Purchases also
  reject a stale displayed price. The yellow shulker opens the separate tray.
- `/sell` opens a **45-slot deposit tray**. Click a gray placeholder or
  shift-click an item from inventory to add it, then click the green pane to
  sell **only the deposited items**. An empty tray never scans your inventory.
  Unsellable items stay in the tray; closing it returns them to inventory
  (overflow drops are owner-locked and do not despawn). Payouts appear in chat
  and the action bar. Close the tray before a plugin reload or stop; its
  contents are not persisted through a crash.
- Potion, splash, lingering and tipped-arrow forms share an effect picker;
  choose ordinary, extended/redstone or upgraded/glowstone effects as
  applicable. It shows the server's effect strength and duration, including
  the normal lingering and arrow duration scales; splash duration is a direct
  hit maximum and decreases with distance. Enchanted books require an
  enchantment and level. Targeted `/shop` sales match the selected form and
  variant: potions with additional custom effects or altered duration scales,
  and books with multiple stored enchantments, are not selected by that
  picker. They can still be placed deliberately in `/sell`, valued from their
  base potion type or total enchantment levels.
- Sale valuation uses the same catalogue, including stored book enchantments:
  each enchantment level adds up to 12% of base value before safety caps, and
  lost durability reduces it. Written books are supplied with a title, author
  and page; ordinary filled maps cover the buyer's world. Named explorer maps
  locate and mark the indicated structure or refund the purchase if none is
  found. Structure lookup can take time.
- Each player has **45 initially empty QuickShop slots**. Click a slot, search
  (`*` browses all), select an item and any variant, choose an amount, then
  save. Left-click the saved slot to buy that amount, right-click to edit, or
  shift-right-click to clear. Saved slots use the **current** price, not a
  cached price; if it changes while the menu is open, the purchase is refused
  and the refreshed total must be reviewed. The old `quickshop.items` list is
  retained for upgrades but is no longer used.
- Ordinary prices are editable in `shop.yml`. Potion and tipped-arrow buy
  prices instead use modeled brewing ingredients (three bottles per brew and
  eight arrows per lingering potion); book buys use the enchantment and level
  tiers in `ChoiceCatalog`. Their generic `buy` entry enables the material but
  does not set each variant price. Potion/arrow sell prices use the modeled
  ingredients and are scaled down when their effective configured base sell
  is below the default. A zero sell disables trading except that the exact
  **old buy-only defaults** are migrated in memory to the new positive sells.
  An intentional zero indistinguishable from an old default will also migrate;
  change that old default buy value as well if you need to keep it disabled.
- Base sales and enchantment bonuses are capped against the cheapest modeled
  acquisition/recipe or blank-copy cost. The model covers 1,900+ expanded
  recipes and selected conversions, Fortune and container returns, but **not
  every trade, farm, datapack, plugin or game mechanic**. Server owners should
  audit their economy. Custom very low prices may reduce effective sells to
  zero. On-disk values remain unchanged by these runtime caps.

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
controls whether **other players' money only** is displayed below their names.
No stat suffix is added to names in the tab list, and a viewer's own score is
not added to their below-name board. Veltryx uses a separate scoreboard for
each viewer; test compatibility with other scoreboard/team plugins.

### 🏷️ Auction house & orders (`/ah`, `/order`, `/orders`)

- **`/ah`** opens the auction listing browser. Click **List the item in your
  hand**, type the **total price for the whole main-hand stack** in chat, or
  use **`/ah sell <total-price>`**. Only that held stack is removed; other
  inventory and offhand stacks are untouched. Enchantments, names, lore,
  damage and other item data are saved with the listing and delivered to the
  buyer or returned on reclaim. **`/ah mine`** shows your listings, including
  expired items to reclaim. Defaults are 20 active listings and 48 hours.
- **`/order`** (and `/orders`) opens the buy-order browser. Click **Create an
  order**, then the item icon to browse all item materials recognized by the
  server, or choose a material from your inventory. Set the quantity and click
  **Total price** to type the total in chat. Confirm to escrow that total.
  The direct syntax is **`/order <item> <amount> <total-price>`**; `/order mine`
  manages yours. Price prompts are cancelled by typing `cancel`, opening a new
  market menu, disconnecting or starting another Veltryx input prompt.
- Both commands interpret the entered price as the **whole listing/order**,
  not a per-item price. There is no fixed maximum monetary price; exact cents
  and `k/m/b/t/q/d/g/l` suffixes are supported (inputs have a 160-character
  safety limit). The minimum is $0.01. The order quantity limit remains 23,040.
  Partial fills divide the total in exact cents; the first remainder items get
  one extra cent, and the unfilled value is refunded on cancellation. Expired
  orders are closed and refunded lazily when an order browser is opened.
  For totals smaller than the item quantity in cents, later fills can pay $0;
  the order browser shows both normal and shift-click payouts before selling.
- Sellers can fill orders from their inventory. In your orders, **left-click
  to collect supplied items** and **right-click to cancel** and refund the
  unfilled remainder. Claims persist across logout, restart, cancellation and
  expiry; full inventories collect partially. Orders track **only a material**
  and accept **plain items only**: custom, enchanted, damaged or other
  metadata-bearing variants cannot fill them. This includes custom spawners
  and ordinary potions with effect data; use `/ah` for those items. Eligible
  plain stacks are used without consuming other variants.
- `/ah` can list an actual identity-bearing spawner because the held stack is
  serialized intact. Its contents remain in the separate spawner storage;
  back up that storage together with the market. Old material-only spawner
  auction records still fail closed and require administrator review rather
  than recreating an identity-less item.
- **Upgrade carefully:** existing auction records already store a total and
  remain material-only; existing per-item buy orders are migrated to the same
  original total and remaining escrow. New commands now interpret the typed
  price as a total, so update any player guidance or customized message
  templates that say “each”. **Do not downgrade** after creating new
  listings/orders: older versions cannot read their full prices or item data.
  Earlier beta versions also recorded fills without delivering purchased
  items. Historical fills have **unknown delivery/compensation status** and
  are not automatically made claimable; back up and reconcile them manually.
  Player inventories, economy and market storage are not one crash-atomic
  transaction. An abrupt crash or disk failure during a trade can require
  administrator review. Ordinary save/payment errors trigger best-effort
  rollback; a rollback failure is logged for administrator reconciliation.

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
| Shard Spear | 500 ✦ | Native spear with **Lunge III**; its Lunge exhaustion is reduced by 90% | **24 h real time** |
| Shard Trident | 500 ✦ | **Riptide III**, hold and release even when dry; 5-second dry-only cooldown (water/rain use stays vanilla) | **24 h real time** |
| Shard Axe | 500 ✦ | **Fells whole trees at once** (default 256-log cap), 3×3 on the allowed list — includes **Silk Touch** | **24 h real time** |
| Shard Pickaxe | 500 ✦ | **3×3 mining** with **amethyst-like particles on every block mined** — pick **Fortune III or Silk Touch** | **24 h real time** |
| Void Totem | 150 ✦ | **Rescues you from a lethal void hit** — consumed, teleports you to safety, full heal + Resistance & Slow Falling | permanent |

- Paper does not attribute its exhaustion event to a specific enchantment or
  hand. The Lunge reduction correlates a matching recent event while the shard
  spear is in the main hand; other plugins or custom enchantments can interfere.
- Hold the Shard Trident to charge and release to launch, even when dry. A
  dry release needs at least 10 server ticks of charge; only a successful dry
  launch starts the 5-second cooldown. Water/rain releases remain vanilla and
  do not use or reset that cooldown. The dry cooldown survives reconnects.
- Dry charging uses Paper's active-item synchronization to emulate vanilla
  charge/release. Paper does not guarantee player-client behavior for this API,
  and it has **not been tested with a real 26.3 client/server**. An unmodified
  client can predict an offhand action or animation while the server is
  starting a dry main-hand charge; the server suppresses the duplicate action.
  Block-use handling is conservative around interactable blocks; the grounded
  lift is a small velocity approximation of vanilla's collision-aware movement.
  Test it with your clients and protection plugins before production.
- A void rescue returns you to the last remembered grounded location in the
  current world, or that world's spawn if none is known; ensure the spawn is
  safe. A failed teleport refunds the totem in memory (not across a crash).
- The Void Totem uses `shardtools:void_totem`. Host the updated
  `ShardTools/resourcepack.zip`, configure the URL and SHA-1 in
  `server.properties`, and have players accept the pack to see its model.
- The weapons/tools self-destruct **24 real-time hours after purchase** —
  exactly like the ShardTools plugin — and warn you before they go.
- `/shard toggle` turns the held tool's ability on or off (each tool
  remembers its own state).
- `/shard top` shows the richest shard holders; `/shard balance` yours.
- Area mining applies the tool's Fortune/Silk Touch to **every** broken
  block on the ShardTools shared allow-list and drops ore XP for the extras
  (never with Silk Touch), skips containers, spawners and bedrock, and
  supports optional sapling replanting for the axe. Existing `shards:`
  settings remain unchanged on upgrade; new configs default to 256 logs and
  no replanting, like ShardTools.

### 🐖 Custom spawners (`/shard spawners`)

- Open the spawner shop from `/shard shop` or directly with `/shard spawners`
  (`veltryx.shards`). **Pig, sheep, cow, iron golem, skeleton, zombie and
  creeper** spawners cost **1,000 shards each**. They are permanent and do
  not inherit shard-tool expiry.
- Place one, then right-click it with another **Veltryx spawner of the same
  mob type** to combine them, up to **1,024**. A different type or a stack
  that would exceed the cap is rejected without consuming the item. A picked-up
  stack can be combined as a whole; stacking does not split it. Right-click
  with an empty hand to open its storage and switch the lever between normal
  **Spawner** and **Inventory** modes. Each component retains its own **20
  pages of 45 slots** (a stack of two has 40 pages); previously saved loot,
  stack membership and the root's mode survive pickup and restart. Use arrows
  to navigate, left-click a loot stack to collect it, or right-click for one.
- In Spawner mode, native spawn attempts and the nearby-mob limit scale with
  stack size (up to 4,096 attempts per normal cycle at 1,024). Large stacks
  can cause **severe server lag**. Inventory mode keeps the normal number of
  attempts and makes one independent loot-table roll per spawner in the stack,
  using the spawned entity but **no player killer**. Player-kill-only loot,
  Looting, XP and separate equipment drops do not apply. If an entire attempt's
  batch cannot fit, it produces no mob or loot; it resumes when space is free.
  On an I/O error during a batch spanning multiple component files, earlier
  components may already contain part of the batch; it is not retried. Monitor
  disk space and errors, and use consistent backups for recovery.
  Spawning still requires a loaded chunk and obeys nearby-player, space,
  nearby-entity and other non-light rules; it is not a guaranteed output rate.
  The custom light rule permits all block and sky light levels in both modes.
- Mine a custom spawner with **Silk Touch** to put the **whole stack** as one
  identity-bearing item directly into your inventory, retaining its mode and
  every component's contents. A full player
  inventory or a non-Silk tool prevents the break. Explosions do not destroy
  these spawners. If placement cannot be completed after the block-place
  event, the original item is returned at that location as a non-expiring,
  owner-only drop.
- Identity-bearing spawner items can be listed from the hand in `/ah`, which
  retains their metadata. Their loot remains in `spawners/`, so back it up
  together with market and player data. Material-only buy orders accept only
  plain spawners and never consume custom identity-bearing ones. Old
  material-only spawner auction records require administrator review.
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
- `spawners/<uuid>.properties` — each purchased identity's type, parent link,
  location, mode and 900 loot slots; written through separately from YAML/SQLite.
  Back up the **entire directory together with world and player data**,
  preferably while the server is stopped. An unreadable record or invalid
  parent graph disables custom spawners rather than inventing missing data;
  inspect the log and restore the complete backup. Large stacks can increase
  disk I/O and CPU use.

Economy account updates write through: YAML uses an atomic file replacement
where supported; SQLite uses transactions. Existing long-cent YAML/SQLite
balances are migrated automatically to exact decimal strings. **Back up data
before upgrading and do not downgrade after using the new market format or
creating balances beyond the old 64-bit limit**: old versions cannot read full
market prices/item payloads or exact SQLite balances. Other YAML subsystems also
autosave.

## 🔌 Developer API

```java
VeltryxEconomy.get().ifPresent(eco -> {
    java.math.BigInteger cents = eco.getBalanceExact(playerId);
    eco.withdrawExact(playerId, name, java.math.BigInteger.valueOf(1_500)); // $15.00
    String pretty = eco.formatExact(cents);
});
```

The built-in shop uses this economy service. Legacy `long` balance reads and
return values clamp at `Long.MAX_VALUE`; use the `*Exact` methods for the full
balance. Legacy long deposits/withdrawals still operate on the exact balance,
while an explicit legacy `setBalance` replaces it with the supplied value.

---

## 🛠️ Building

Canonical build (needs network for the Paper API):

```bash
cd Veltryx
mvn -B clean verify        # JDK 25 required — output: target/vx-0.0.6-beta.jar
```

Offline development fallback: the bundled Eclipse compiler and compile-only
API stubs build the plugin and run the smoke/regression tests. This is **not**
a real Paper compile or server test; its jar does not include the SQLite JDBC
driver. Use the Maven jar for deployment:

```bash
cd Veltryx
JAVA_BIN=/path/to/java25 ./build.sh   # output: target/vx-0.0.6-beta.jar
```

To run the additional real SQLite integration tests during the offline build,
set `SQLITE_JDBC_JAR` to a local xerial SQLite JDBC jar (and include any runtime
dependencies it needs). This still does not run a Paper server:

```bash
JAVA_BIN=/path/to/java25 SQLITE_JDBC_JAR=/absolute/path/sqlite-jdbc.jar ./build.sh
```

Offline consistency checks (message keys, permissions, command wiring):

```bash
cd Veltryx && python3 tools/check_consistency.py
```

The CI template at `Veltryx/.github/workflows/build.yml` runs consistency,
offline regressions, SQLite integration and `mvn clean verify` on Temurin 25.
Copy it to the repo root `.github/workflows/` to activate it; updating active
workflows requires GitHub workflow permission.

## 🚑 Troubleshooting

### The plugin name shows red (in `/plugins` or the console)

A red name means the plugin did **not** enable. Check the console — the fix depends on what is printed:

| Console output | Cause | Fix |
|---|---|---|
| `Veltryx failed to enable during stage '...'` + stack | A bug during a specific startup stage | Send the whole error — the stage name pinpoints it |
| `Could not load 'plugins/Veltryx...jar' ... plugin.yml` | Not a real built jar was dropped in | Build with `mvn -B clean package` and use `target/vx-0.0.6-beta.jar` (NOT `original-...jar`, not `target/classes`) |
| `UnsupportedClassVersionError` | Server runs an old Java | Minecraft 26.x needs **Java 25** — update the server's Java |
| `api-version` / "made for a newer version" | Server is older than the plugin expects | Veltryx declares `api-version: '26.2'` and targets 26.3; send the exact server version and error |
| `config.yml is missing from the plugin jar!` | Incomplete/repacked jar | Rebuild with Maven; don't unzip/rezip the jar |

After a successful enable the console shows `[Veltryx] Veltryx vx-0.0.6-beta enabled on ...` and `plugins/Veltryx/` is created with `config.yml` and `messages.yml`.

## 🗺️ Roadmap (next versions)

- Vault / VaultUnlocked bridge so existing plugins can use the Veltryx economy
- PlaceholderAPI placeholders, Discord webhook alerts for anti-cheat flags
- Punishment GUIs, ban appeal workflow
