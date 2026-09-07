# XPBank 🏦✨

Store and withdraw **experience points** (total XP, **not** levels) in a personal
bank on Minecraft **26.2** (Paper / Purpur / Folia), built with **Java 25**.

Bank your XP before a risky dive so you don't lose it on death, hand your savings
to a friend, climb the leaderboard, and manage everything from chat commands or a
clean point-and-click GUI. Every message, sound, limit, GUI icon and the storage
backend is configurable.

- **Total XP, not levels.** Balances are stored as exact experience points, using
  the vanilla XP formulas — no rounding to whole levels, no lost points.
- **Runs everywhere.** One jar for Paper, Purpur and Folia. All XP changes are
  dispatched to the player's owning region thread, so it is Folia-safe. Works on
  Linux, Windows and macOS.
- **Pick your storage.** `yaml` (simple flat file) or `sqlite` (bundled driver,
  better for big servers) — switch in `config.yml` and reload.
- **Optional interest.** A savings-account style interest payout on banked XP,
  **off by default** and fully configurable (rate, interval, caps, offline pay).

---

## Requirements

| | |
|---|---|
| Minecraft | **26.2** (Chaos Cubed) |
| Server | Paper, Purpur, or Folia 26.2 |
| Java | **25** (required by 26.2) |
| Dependencies | None (SQLite driver is shaded into the jar) |

---

## Installation

1. Download `XPBank-1.0.0.jar` (build it with `mvn -B clean verify`, see below).
2. Drop it into your server's `plugins/` folder.
3. Start the server. `config.yml` and `messages.yml` are generated in
   `plugins/XPBank/`.
4. Edit them to taste and run `/xpbank reload`.

---

## Commands

Base command: **`/xpbank`** — aliases **`/xp`**, **`/bank`**, **`/xpb`**, **`/expbank`**.

Running `/xp` with no arguments opens the GUI (or shows your balance if you lack
`xpbank.gui`).

| Command | Short forms | Description | Permission |
|---|---|---|---|
| `/xp balance [player]` | `bal`, `b` | Show banked + on-hand XP | `xpbank.balance` (`.others` for other players) |
| `/xp deposit [amount]` | `dep`, `d`, `store`, `in` | Bank XP from your bar (default: all) | `xpbank.deposit` |
| `/xp withdraw [amount]` | `with`, `w`, `take`, `out` | Take XP out of the bank (default: all) | `xpbank.withdraw` |
| `/xp pay <player> <amount>` | `send`, `transfer`, `give`, `p` | Transfer banked XP to another player | `xpbank.pay` |
| `/xp top` | `leaderboard`, `lb` | Richest XP savers | `xpbank.top` |
| `/xp gui` | `menu`, `open`, `g` | Open the bank GUI | `xpbank.gui` |
| `/xp help` | `?`, `h` | Command help | — |
| `/xp reload` | `rl` | Reload config & messages | `xpbank.admin.reload` |

**Amounts** accept plain numbers (`100`), grouped numbers (`1,000`), suffixes
(`1k`, `2.5m`, `1b`, `1t`, `1q`), and the keywords **`all`** / **`max`** and **`half`**.

### Admin command

Base command: **`/xpbankadmin`** — aliases **`/xpba`**, **`/bankadmin`**, **`/xpadmin`**.
Requires `xpbank.admin`.

| Command | Short forms | Description |
|---|---|---|
| `/xpba info <player>` | `check` | View a player's banked XP + UUID |
| `/xpba set <player> <amount>` | | Set a player's balance |
| `/xpba add <player> <amount>` | `give` | Add XP to a player's balance |
| `/xpba take <player> <amount>` | `remove` | Remove XP from a player's balance |
| `/xpba reset <player>` | `clear` | Set a player's balance to 0 |
| `/xpba stats` | | Account count, total banked XP, storage backend |
| `/xpba interest` | | Pay one round of interest immediately (if enabled) |
| `/xpba reload` | `rl` | Reload config & messages |

Admin commands operate directly on storage, so they work for **offline** players too.

---

## Permissions

| Permission | Default | What it grants |
|---|---|---|
| `xpbank.use` | everyone | Base access to player commands |
| `xpbank.balance` | everyone | Check your own balance |
| `xpbank.balance.others` | op | Check other players' balances |
| `xpbank.deposit` | everyone | Deposit XP |
| `xpbank.withdraw` | everyone | Withdraw XP |
| `xpbank.pay` | everyone | Transfer banked XP |
| `xpbank.top` | everyone | View the leaderboard |
| `xpbank.gui` | everyone | Open the GUI |
| `xpbank.admin` | op | Use `/xpbankadmin` |
| `xpbank.admin.reload` | op | Reload the plugin |
| `xpbank.*` | op | All of the above |

---

## GUI

`/xp gui` (or just `/xp`) opens a compact menu:

- **Center** — an XP bottle showing your banked and on-hand totals.
- **Left** — green buttons to deposit **10**, **100**, or **ALL**.
- **Right** — red buttons to withdraw **10**, **100**, or **ALL**.

The menu is display-only (items can't be taken), repaints after every action, and
plays your configured click/deposit/withdraw sounds. It's fully Folia-safe because
it never relies on chat input.

---

## Interest (optional, off by default)

Like a real bank, XPBank can pay **interest** on banked XP. It is **disabled by
default** — enable it under `interest:` in `config.yml`.

When enabled, every interval each account that meets the minimum balance earns
`rate-percent` of its banked XP, added straight back to the balance (so it
**compounds**). You control everything:

- `rate-percent` — how much is paid each interval (e.g. `1.0` = 1%).
- `interval` — `hours` + `minutes` + `seconds` between payouts.
- `min-balance` — accounts below this earn nothing.
- `max-payout` — cap on XP paid per account per interval (`0` = no cap).
- `pay-offline-players` — pay accounts whose owner is offline.
- `notify-players` — message + sound online players when they earn interest.

Interest only ever touches **stored balances**, never a player's live XP bar, so
it is Folia-safe and works for offline players. Admins can trigger a payout on
demand with `/xpba interest`. Changing any interest setting and running
`/xpbank reload` restarts the payout timer.

---

## Configuration

`config.yml` (excerpt — every option is documented in the file):

```yaml
storage:
  type: yaml          # "yaml" or "sqlite"
  autosave:
    enabled: true
    interval-seconds: 300

features:
  transfers: true     # /xp pay
  leaderboard: true   # /xp top
  leaderboard-size: 10

limits:
  min-deposit: 1
  min-withdraw: 1
  min-transfer: 1

interest:                 # savings-account interest on banked XP (OFF by default)
  enabled: false
  rate-percent: 1.0       # % of the balance paid each interval
  interval:
    hours: 1
    minutes: 0
    seconds: 0
  min-balance: 1          # accounts below this earn nothing
  max-payout: 0           # cap per account per interval (0 = no cap)
  pay-offline-players: true
  notify-players: true

sounds:
  enabled: true
  deposit: ENTITY_EXPERIENCE_ORB_PICKUP
  withdraw: ENTITY_PLAYER_LEVELUP
  transfer: ENTITY_ARROW_HIT_PLAYER
  error: ENTITY_VILLAGER_NO
  gui-open: BLOCK_CHEST_OPEN
  gui-click: UI_BUTTON_CLICK
  interest: ENTITY_EXPERIENCE_ORB_PICKUP

gui:
  title: "<dark_aqua><bold>XP Bank</bold></dark_aqua>"
  rows: 3
  icons:
    balance: EXPERIENCE_BOTTLE
    deposit: LIME_DYE
    withdraw: RED_DYE
    filler: GRAY_STAINED_GLASS_PANE
```

All player-facing text lives in `messages.yml` and uses
[MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatting with
`{placeholder}` substitution.

---

## Building

Requires **JDK 25**. From the `XPBank/` folder:

```bash
mvn -B clean verify
```

The shaded plugin jar is written to `target/XPBank-1.0.0.jar`. `verify` also runs
the JUnit tests that validate the XP formulas and amount parser.

The dependency is Paper's official API
(`io.papermc.paper:paper-api:26.2.build.115-stable`) from `repo.papermc.io`, plus
`org.xerial:sqlite-jdbc` (shaded in).

A GitHub Actions workflow that builds and tests the jar with Temurin **JDK 25**
is provided at `XPBank/.github/workflows/build.yml`. GitHub only runs workflows
that live in the repository-root `.github/workflows/` folder, so copy it there to
activate CI:

```bash
mkdir -p .github/workflows
cp XPBank/.github/workflows/build.yml .github/workflows/xpbank.yml
```

---

## How XP is measured

Bukkit's `Player#getTotalExperience()` is unreliable (it drifts out of sync with
picked-up orbs and level changes), so XPBank computes a player's exact point total
from their level and XP-bar progress using the vanilla formulas, and applies
changes by clearing and re-granting XP. This guarantees deposits and withdrawals
are point-accurate on every supported platform.

---

## License & credits

Part of the [MinecraftPlugins](https://github.com/SuperSeller100X/MinecraftPlugins)
collection by SuperSeller100X.
