# Subscriptions

A full subscription marketplace for **Minecraft 26.2** on **Paper**, **Purpur**, and **Folia**.

Everyone can create a plan. Everyone can subscribe. Billing uses **real-world time** (every 10 minutes, every hour, every day, …). Money is never taken unless the seller can actually deliver — out of stock means pause, skip, or auto-cancel, never a silent charge.

| | |
|---|---|
| Minecraft | **26.2** |
| Platforms | Paper · Purpur · Folia |
| Java | **25** |
| Build | Maven (`mvn -B clean package`) |
| Storage | SQLite (`plugins/Subscriptions/subscriptions.db`) |
| Economy | Vault **or** VaultUnlocked (EssentialsX, CMI, CoinsEngine, …) |
| Soft-depends | PlaceholderAPI, LuckPerms |

Drop `Subscriptions-1.0.0.jar` into `plugins/` and restart. Linux, Windows, and macOS are all supported — paths, SQLite, and line endings go through the JVM/`java.io.File` APIs only.

---

## What it does

Players and staff open `/sub` and get a GUI for the whole lifecycle:

1. **Create a plan** — name, description, price (`10k`, `2.5m`, `1b`, `1t`, …), real-time interval (`10m`, `1h30m`, `every 2 days`), signup fee, free trial cycles, category, max subscribers, max cycles.
2. **Attach rewards** (mix and match):
   - **Items** — kit delivered to the subscriber **inbox** every cycle
   - **Money** — seller pays the subscriber (allowance / rebate)
   - **Commands** — console or player, with `{player}` `{uuid}` `{plan}` `{price}` `{seller}`
   - **Permissions** — LuckPerms node for the interval
   - **Ranks / groups** — LuckPerms inheritance for the interval
   - **Messages** — chat line on each cycle
3. **Stock the plan** — deposit one complete kit per billing cycle. Admins can mark **infinite stock**.
4. **Publish** — the plan appears on the public marketplace.
5. **Subscribe** — first charge (price + signup, or just signup during a trial) is taken only if the kit can be delivered.
6. **Manage** — pause, resume, cancel, or change **your personal charge policy** on that subscription.
7. **Claim** — `/subinbox` opens the paginated inbox. Overflow never deletes items.

Server-owned / admin plans work the same way (`/subadmin give`).

---

## Anti-scam billing

A cycle is charged **if and only if**:

- the subscriber can pay the price (unless this cycle is a free trial), **and**
- the seller can fulfil every reward (kit in stock, seller can fund a money payout, LuckPerms is present for rank/permission rewards, inbox is not full).

Otherwise the **effective charge policy** decides. Nothing is withdrawn.

| Policy | Behaviour |
|---|---|
| `PAUSE` (default) | Freeze the subscription. Resume automatically when stock / money is back. |
| `SKIP` | Do not charge. Try again next interval. |
| `CANCEL` | Cancel the subscription and notify both sides. |

Each **plan** has a default policy. Each **subscriber** can override it for their own subscription (`/sub policy <id> pause|skip|cancel` or the GUI). That is personal — it does not change the seller’s default.

If a withdraw succeeds and delivery then fails, the subscriber is **refunded immediately** and the kit is put back.

---

## Commands

### Players — `/sub` (aliases: `/subscriptions`, `/subscription`, `/subs`)

| Command | Permission | What it does |
|---|---|---|
| `/sub` | `subscriptions.use` | Main GUI |
| `/sub browse [query]` | `subscriptions.browse` | Marketplace |
| `/sub create` | `subscriptions.create` | Plan wizard |
| `/sub info <plan>` | `subscriptions.use` | Plan details |
| `/sub subscribe <plan>` | `subscriptions.subscribe` | Subscribe |
| `/sub cancel <id>` | `subscriptions.cancel` | Cancel yours |
| `/sub pause <id>` / `/sub resume <id>` | `subscriptions.pause` | Pause / resume |
| `/sub policy <id> <pause\|skip\|cancel>` | `subscriptions.policy` | Personal policy |
| `/sub inbox` | `subscriptions.inbox` | Claim deliveries |
| `/sub mysubs` | `subscriptions.use` | Your subscriptions |
| `/sub myplans` | `subscriptions.use` | Plans you own |
| `/sub history` | `subscriptions.history` | Charge log |
| `/sub stock <plan>` | `subscriptions.plan.stock` | Deposit kits |
| `/sub help` | `subscriptions.use` | In-game help |

`/subinbox` (alias `/sinbox`) is a shortcut to the inbox.

### Staff — `/subadmin` (aliases: `/sadmin`, `/subscriptionsadmin`)

| Command | Permission | What it does |
|---|---|---|
| `/subadmin reload` | `subscriptions.admin.reload` | Reload `config.yml` + `messages.yml` |
| `/subadmin give <player> <plan>` | `subscriptions.admin.give` | Force-subscribe (bypasses listing / caps) |
| `/subadmin cancel <id>` | `subscriptions.admin.cancel` | Force-cancel any subscription |
| `/subadmin info <id>` | `subscriptions.admin.info` | Inspect a plan or subscription |
| `/subadmin stats` | `subscriptions.admin.stats` | Plans / live subs / inbox / economy |
| `/subadmin forcebill` | `subscriptions.admin.forcebill` | Run a billing pass now |
| `/subadmin webhook` | `subscriptions.admin.webhook` | Send a Discord test embed |
| `/subadmin inbox <player>` | `subscriptions.admin.view` | Peek at inbox size |

---

## Permissions

All default to `true` for players unless marked **op**.

| Node | Default | Description |
|---|---|---|
| `subscriptions.use` | true | Open `/sub` |
| `subscriptions.browse` | true | Marketplace |
| `subscriptions.subscribe` | true | Subscribe |
| `subscriptions.create` | true | Create plans |
| `subscriptions.inbox` | true | Inbox |
| `subscriptions.history` | true | Personal history |
| `subscriptions.cancel` | true | Cancel your sub |
| `subscriptions.pause` | true | Pause / resume |
| `subscriptions.policy` | true | Override charge policy |
| `subscriptions.plan.edit` | true | Edit your plans |
| `subscriptions.plan.stock` | true | Deposit kits |
| `subscriptions.plan.items` | true | Item rewards |
| `subscriptions.plan.money` | true | Money-payout rewards |
| `subscriptions.plan.command` | **op** | Command rewards |
| `subscriptions.plan.permission` | **op** | LuckPerms permission rewards |
| `subscriptions.plan.group` | **op** | LuckPerms rank rewards |
| `subscriptions.plan.infinite` | **op** | Infinite stock |
| `subscriptions.bypass.limit` | **op** | Ignore plan / sub caps |
| `subscriptions.bypass.price` | **op** | Ignore min/max price |
| `subscriptions.bypass.interval` | **op** | Ignore min/max interval |
| `subscriptions.admin` | **op** | All admin children |

Give trusted shopkeepers `subscriptions.plan.command` (and friends) if they should attach crate keys / ranks. Regular players can still sell item kits and recurring money.

---

## Money amounts

Anywhere a price is typed (`10k`, `2.5M`, `$1b`, `1t`, `1q`, `3 million`, `1,500`, `all`):

| Suffix | Multiplier |
|---|---|
| `k` / `thousand` | 1,000 |
| `m` / `mil` / `million` | 1,000,000 |
| `b` / `bil` / `billion` | 1,000,000,000 |
| `t` / `tril` / `trillion` | 1,000,000,000,000 |
| `q` / `quad` / `aa` | 1,000,000,000,000,000 |
| `qi` | 1,000,000,000,000,000,000 |

Economy is resolved in this order:

1. **Vault** (`net.milkbowl.vault.economy.Economy`) — works with almost every economy plugin.
2. **VaultUnlocked** (`net.milkbowl.vault2.economy.Economy`) via reflection.
3. Disabled — free plans, item / command / rank rewards still work.

---

## Intervals

Real-world time, not ticks. Examples: `10s`, `10m`, `1h`, `1h30m`, `1d`, `1w`, `1mo`, `every 2 days`, `20 minutes`. Defaults in `config.yml` clamp this to 60 seconds … 365 days (bypass with `subscriptions.bypass.interval`).

---

## PlaceholderAPI

Identifier: `subscriptions`.

| Placeholder | Result |
|---|---|
| `%subscriptions_active%` | Your living subscriptions |
| `%subscriptions_owned%` | Plans you own |
| `%subscriptions_inbox%` | Waiting inbox items |
| `%subscriptions_spent%` | Money you have paid (compact) |
| `%subscriptions_earned%` | Money you have received as a seller |
| `%subscriptions_next%` | Time until your next charge |
| `%subscriptions_plans%` | Total plans on the server |
| `%subscriptions_live%` | Living subscriptions on the server |
| `%subscriptions_plan_<id>_name%` | Plan name |
| `%subscriptions_plan_<id>_price%` | Plan price |
| `%subscriptions_plan_<id>_subs%` | Subscriber count |
| `%subscriptions_plan_<id>_stock%` | Remaining kits / `∞` |
| `%subscriptions_plan_<id>_status%` | `PUBLISHED` / `UNLISTED` / … |

---

## Discord, CSV, API

- Set `discord.webhook-url` to an incoming webhook. Charges, cancels, and out-of-stock events post as embeds. `/subadmin webhook` sends a test.
- Daily CSV files land in `plugins/Subscriptions/logs/charges-YYYY-MM-DD.csv` (retention configurable).
- Other plugins can softdepend on `Subscriptions` and call:

```java
SubscriptionsAPI api = SubscriptionsAPI.get();
api.plan("diamond-kit-ab12cd");
api.subscriptions(player.getUniqueId());
api.inboxSize(player.getUniqueId());
```

Cancellable events: `SubscriptionStartEvent`, `SubscriptionChargeEvent`.
Notify-only: `PlanCreateEvent`, `SubscriptionCancelEvent`.

---

## Folia

`plugin.yml` sets `folia-supported: true` and `api-version: '26.2'`.

- Billing, SQLite, Discord, and CSV run on the **global region scheduler**.
- GUIs, sounds, and player messages run on the **entity scheduler**.
- The legacy `BukkitScheduler` is only a fallback if region schedulers are missing.

Do not open inventories or touch world state from the billing tick — the code never does.

---

## Configuration

Generated on first start:

```
plugins/Subscriptions/
  config.yml
  messages.yml
  subscriptions.db      # SQLite, portable
  logs/charges-*.csv
```

Useful knobs in `config.yml`:

| Path | Default | Meaning |
|---|---|---|
| `limits.min-interval-seconds` | `60` | Shortest plan interval |
| `limits.max-plans-per-player` | `10` | Creation cap |
| `limits.max-active-subscriptions` | `20` | Per player |
| `economy.enabled` | `auto` | `auto` / `true` / `false` |
| `economy.tax-percent` | `0` | Taken from each payment before the seller is paid |
| `billing.tick-seconds` | `1` | How often due subscriptions are scanned |
| `items.blacklist` | `[]` | Bukkit material names banned as rewards |
| `discord.webhook-url` | `""` | Incoming webhook |
| `logs.csv` | `true` | Write daily CSV |

Every user-facing string lives in `messages.yml` (`&` and `&#RRGGBB` colours).

---

## Build

Requires **Java 25** and Maven. Targets Paper API `26.2.build.115-stable`.

```bash
cd Subscriptions
mvn -B clean test package
```

The shaded jar is `target/Subscriptions-1.0.0.jar` (sqlite-jdbc is bundled; Vault / PlaceholderAPI / LuckPerms are not).

CI: `.github/workflows/subscriptions.yml` runs the same command on Temurin 25.

### Tests

JUnit 5 covers money parsing, duration parsing, compact formatting, reward codecs, and the anti-scam billing decision table. They do not need a running server.

---

## Permissions recap for LuckPerms shopkeepers

A typical trusted merchant:

```
subscriptions.create
subscriptions.plan.items
subscriptions.plan.money
subscriptions.plan.stock
```

A rank vendor (staff):

```
subscriptions.plan.group
subscriptions.plan.permission
subscriptions.plan.command
subscriptions.plan.infinite
```
