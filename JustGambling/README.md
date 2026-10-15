# JustGambling 🎰

**JustGambling** is a configurable, house-only casino plugin for Minecraft
26.2. Players spend in-game money on games resolved by the server; they never
wager against or transfer stakes to another player. It supports Paper, Purpur,
and Folia, uses Java 25, and is built with Maven.

> This release is intentionally money-only. Item gambling is planned for a
> later release and is not exposed by the current commands or GUI.

## Highlights

- 12 house games with no PvP or player escrow:
  Coin Flip, Dice, Roulette, Lucky Wheel, High/Low, Slots, Scratch Card,
  Mines, Crash, Jackpot, Lucky Number, and Double or Nothing.
- Four configurable risk tiers. The defaults make the trade-off explicit:
  higher chance pays less, while lower chance pays more.
- `/jg` casino GUI with game cards, risk selection, amount presets, chat input,
  anvil-style custom input, choice screens, a Mines board, animated wheel,
  roulette, slots, scratch, crash, jackpot and dice presentations, and
  paginated history.
- Every instant game resolves through a visible inventory animation before
  settlement. Clicks are locked while it runs; a per-player pending-wager lock
  and a short configurable cooldown prevent double-click and command spam.
- Vault-first economy integration for EssentialsX and any other Vault provider,
  direct native EssentialsX fallback, and an isolated built-in YAML currency
  when no external provider is available.
- Secure random outcomes by default, configurable house rules, optional
  jackpot contributions, finite-number/overflow checks, and atomic stake
  settlement.
- Configurable sound effects, MiniMessage messages and titles, stake/payout
  limits, cooldowns, enabled games, payout tables, storage, announcements,
  and fallback currency.
- Audit history and statistics for every resolved wager.
- Administrative game toggles, reload, diagnostics, limits/rules inspection,
  fallback balance editing, recovery refunds, history and statistics, account
  reset, and jackpot-pool controls.
- Folia-safe entity/global scheduling; no hard-coded localhost, OS-specific
  paths, or platform-specific file assumptions.

## Requirements and compatibility

| Component | Requirement |
|---|---|
| Minecraft | **26.2** |
| Server | **Paper, Purpur, or Folia** 26.2 builds |
| Java runtime | **Java 25** |
| Economy | Vault plus a provider such as EssentialsX, or the built-in fallback |
| Build | Maven |

The plugin is compiled against `io.papermc.paper:paper-api:26.2.build.119-stable`.
Purpur and Folia expose the Paper/Bukkit API used by this plugin. Vault and
Essentials are soft dependencies: the plugin loads without either one when
`economy.fallback.enabled` is true.

## Installation

1. Install Java 25 and run Paper, Purpur, or Folia 26.2.
2. For normal server money, install Vault and enable an economy provider such as
   EssentialsX Economy. The plugin also attempts EssentialsX's native API when
   Vault is not present.
3. Copy `JustGambling-1.0.0.jar` into the server's `plugins/` directory.
4. Start or restart the server. The plugin creates `config.yml`,
   `messages.yml`, and `data.yml` in `plugins/JustGambling/`.
5. Run `/jg` or `/gamble` to open the casino.

All runtime data is stored below the plugin data folder. The YAML writer uses a
temporary file and an atomic replacement when the operating system supports it,
so the same jar works on Linux, Windows, and macOS.

## Commands

The primary command is `/justgambling`; aliases are `/jg`, `/gamble`, and
`/casino`. Subcommands also have short forms so they remain convenient in chat.

| Command | Short form | Description |
|---|---|---|
| `/jg` | `/jg menu` or `/jg g` | Open the casino GUI. |
| `/jg play <game> <amount> [risk] [choice]` | `/jg p ...` | Play directly. Amounts support `100`, `1.5k`, `2m`, `1b`, `1q`, `all`. |
| `/jg games` | `/jg l` | List games and whether each is enabled. |
| `/jg balance` | `/jg b` | Show the active economy balance and provider. |
| `/jg history [page]` | `/jg h [page]` | Open paginated personal history. |
| `/jg stats [player]` | `/jg st [player]` | View statistics; another player requires permission. |
| `/jg cashout` | `/jg co` | Safely cash out an active Mines board. |
| `/jg help` | `/jg ?` | Show the command guide. |
| `/jg reload` | `/jg rl` | Reload configuration and messages. |

### Direct-play examples

```text
/jg p coinflip 100 safe
/jg p dice 1.5k risky
/jg p roulette 250 balanced red
/jg p roulette 250 green extreme
/jg p highlow 500 risky high
/jg p crash 100 2.50
/jg p lottery 50 7
/jg p mines 200 extreme
```

The parser accepts risk in any optional position. For choice games, the valid
choices are:

- `roulette`: `red`, `black`, or `green`;
- `highlow`: `high` or `low`;
- `crash`: a target from `1.01x` through `1000x` (the GUI provides common
  targets such as `1.25`, `1.5`, `2`, `3`, `5`, and `10`);
- `lottery`: an integer from `1` to `10`.

If a choice is omitted for a direct command, the safe default is `red`, `high`,
`2.0x`, or `1` respectively. The GUI asks for a choice before starting a
wager. Mines is interactive: revealing a safe tile raises the cash-out
multiplier, hitting a mine loses the stake, and closing the board safely cashes
out the current amount rather than silently destroying it.

## Games and payout model

All games are house games. The server withdraws a stake, resolves one outcome,
and deposits the payout when appropriate. No other player is involved.

| Game | How it works |
|---|---|
| Coin Flip | A house flip with the selected risk chance and multiplier. |
| Dice | Rolls 1–100; the selected chance determines the winning threshold. |
| Roulette | Spins 0–36. Red/black use the configured colour payout; green is the rare payout. |
| Lucky Wheel | Rolls a weighted 1–100 wheel using the selected risk profile. |
| High/Low | Picks a high or low window; the risk profile controls the window size. |
| Slots | Generates three reel symbols and applies the selected risk payout on a win. |
| Scratch Card | Reveals an instant win or loss using the selected profile. |
| Mines | A 5×5 board with risk-specific mine counts; cash out after safe reveals. |
| Crash | Generates a crash point and wins if it reaches the chosen target multiplier. |
| Jackpot | Rare house hit; the persisted house pool can make the payout larger. |
| Lucky Number | Picks 1–10 and wins when it matches the selected number. |
| Double or Nothing | A high-risk profile with a minimum 2x win payout. |

Default risk tiers are:

| Tier | Chance | Payout |
|---|---:|---:|
| Safe | 60% | 1.45x |
| Balanced | 50% | 1.90x |
| Risky | 25% | 3.60x |
| Extreme | 10% | 8.50x |

These are starting values, not hard-coded rules. Edit `risk-tiers` and the
individual game sections in `config.yml`. The plugin always rejects NaN,
infinite, negative, and arithmetic-overflow values even if limits are disabled.

## Economy behavior

The economy order is:

1. a registered Vault `Economy` service (EssentialsX normally registers one);
2. EssentialsX's native economy API when the Essentials plugin is present but
   Vault is not;
3. JustGambling's isolated fallback currency when
   `economy.fallback.enabled: true`.

The fallback is **not** a Vault or EssentialsX balance. Other plugins cannot
spend it, `/pay` cannot see it, and changing it does not change a player's
wallet. This makes a no-dependency test server possible while keeping normal
server money compatible with shops and `/pay`.

If an external provider is hooked, wagers use that provider and fallback
balances remain untouched. If no provider exists, `/jg balance` identifies the
fallback currency and the admin can seed accounts with `/jga give`.

## Admin commands

The admin command is `/justgamblingadmin`, with `/jga` and `/jgadmin` aliases.
Each operation has a separate permission node.

| Command | Description |
|---|---|
| `/jga status` / `/jga st` / `/jga diag` | Show economy provider, scheduler detection, jackpot pool, and active Mines boards. |
| `/jga reload` / `/jga rl` | Reload all configuration and messages. |
| `/jga rules` / `/jga limits` | Show configured stake/payout limits, cooldown, and risk rules. |
| `/jga enable <game>` / `/jga on <game>` | Enable a game and persist the setting. |
| `/jga disable <game>` / `/jga off <game>` | Disable a game and persist the setting. |
| `/jga set <player> <amount>` | Set an isolated fallback balance. |
| `/jga give <player> <amount>` / `/jga add ...` | Give fallback currency. |
| `/jga take <player> <amount>` / `/jga remove ...` | Take fallback currency. |
| `/jga refund <player> <amount>` / `/jga recover ...` | Issue a recovery refund through the active economy. |
| `/jga history <player> [page]` / `/jga h ...` | Open another player's history GUI. |
| `/jga stats <player>` / `/jga stat ...` | Inspect another player's aggregate statistics. |
| `/jga reset <player>` / `/jga clear ...` | Reset fallback balance, statistics, and history. |
| `/jga pool info` / `/jga jp info` | Inspect the persisted house jackpot pool. |
| `/jga pool set <amount>` / `/jga pool add <amount>` | Set or add to the house pool. |

Balance-edit commands always edit the built-in fallback account, not a Vault
wallet. They are useful when testing without an economy provider.

## Permissions

| Permission | Default | Purpose |
|---|---|---|
| `justgambling.use` | everyone | Open the GUI. |
| `justgambling.games` | everyone | List games. |
| `justgambling.balance` | everyone | View the active balance. |
| `justgambling.play` | everyone | Base permission for wagers. |
| `justgambling.play.<game>` | everyone | Per-game permission, for example `justgambling.play.mines`. |
| `justgambling.history` | everyone | View personal history. |
| `justgambling.stats` | everyone | View personal statistics. |
| `justgambling.stats.others` | op | View another player's statistics. |
| `justgambling.reload` | op | Use `/jg reload`. |
| `justgambling.admin` | op | Parent for all admin permissions. |
| `justgambling.admin.reload` | op | Admin reload. |
| `justgambling.admin.diagnostics` | op | View admin status and diagnostics. |
| `justgambling.admin.rules` | op | View limits and payout rules. |
| `justgambling.admin.games` | op | Enable/disable games. |
| `justgambling.admin.balance` | op | Edit fallback balances. |
| `justgambling.admin.refund` | op | Issue recovery refunds. |
| `justgambling.admin.history` | op | Open another player's history. |
| `justgambling.admin.stats` | op | Inspect another player's statistics. |
| `justgambling.admin.reset` | op | Reset an account. |
| `justgambling.admin.pool` | op | Manage the jackpot pool. |
| `justgambling.*` | op | All JustGambling permissions. |

The short command aliases do not bypass permissions.

## Configuration

`plugins/JustGambling/config.yml` controls the economy fallback, decimal
precision, unlimited or capped stakes, cooldowns, four risk tiers, every game,
Mines layout, Roulette payouts, Jackpot pool, Crash edge, GUI titles and amount
presets, sounds, announcements, and YAML storage. Important defaults:

```yaml
limits:
  minimum-stake: 0.01
  maximum-stake: 0       # 0 = unlimited
  maximum-payout: 0      # 0 = unlimited
  cooldown-seconds: 2.0  # blocks rapid-fire wagers by default

animations:
  enabled: true
  step-ticks: 2
  frames: 18
  result-pause-ticks: 8

economy:
  fallback:
    enabled: true
    currency-name: chips
    starting-balance: 0.0

games:
  jackpot:
    seed: 1000.0
    chance: 0.02
    minimum-multiplier: 25.0
    loss-contribution-percent: 5.0
```

`messages.yml` is MiniMessage-based. It contains the prefix, all command and
outcome messages, help lines, Mines feedback, and big-win announcements.
Invalid sound names safely fall back to their documented defaults; `none` or
`off` disables a sound. Reload with `/jg rl` or `/jga rl`.

## Persistence, safety, and fairness

- `data.yml` stores fallback balances, the house jackpot pool, per-player
  totals, and a bounded transaction history. It is portable and does not
  require a database server.
- Wager validation occurs before withdrawal. A failed payout operation attempts
  to refund the original stake; the plugin logs the incident for staff.
- Mines stakes are refunded/cashed out when a player closes the GUI, disconnects,
  or the plugin shuts down. No active board is intentionally abandoned.
- Outcomes use `SecureRandom` by default. Set `games.use-secure-random: false`
  only for a deliberate non-secure test setup.
- Results record game, stake, payout, win/loss, timestamp, and a human-readable
  result detail. This is an audit trail, not a cryptographic provably-fair
  commitment protocol.
- The plugin handles only in-game server economy values. It does not process
  real-world money or payments.

## Folia implementation notes

All player-facing work is done on the player's entity scheduler when a callback
originates from asynchronous chat. Global broadcasts and the economy hook retry
use the global region scheduler when available. The plugin does not assume that
all players share one main thread and declares `folia-supported: true` in
`plugin.yml`.

## Building and testing

From the repository root:

```bash
cd JustGambling
mvn -B clean test
mvn -B clean package
```

The resulting jar is `target/JustGambling-1.0.0.jar`. The build uses the Paper
26.2 API as `provided`, VaultAPI as `provided`, Java release 25, and JUnit for
pure parser tests. Never copy Paper or Vault into the plugin jar; the server
provides them.

## Verified upstream references

- [Paper 26.2 release notes](https://papermc.io/news/26-2/)
- [Paper 26.2 downloads](https://papermc.io/downloads/paper)
- [Paper developer project setup](https://docs.papermc.io/paper/dev/project-setup/)
- [Purpur 26.2 API](https://purpurmc.org/javadoc/)
