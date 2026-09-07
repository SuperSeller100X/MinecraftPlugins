# PlayerBank

Isolated player bank for **Minecraft 26.2** (Paper / Purpur / Folia).

Wallet money stays in the normal economy: **EssentialsX** (directly, or through **Vault**) or any other Vault provider (CMI, etc.). Bank money is stored **only** by this plugin. Other plugins — auctions, shops, `/pay`, orders — **cannot** spend the bank balance.

## Features

- **GUI** — `/bank gui` opens a full bank menu, configurable between the
  **classic chest inventory** and the **new native Minecraft menu screens**
  (Paper dialogs), with one-click quick amounts, custom amount entry,
  interest details, paginated logs, and a per-player style choice
- **Deposit** wallet → bank (`/bank deposit`)
- **Withdraw** bank → wallet (`/bank withdraw`)
- **EssentialsX** wallet support (with or without Vault)
- **Vault** support for any other economy plugin
- **Compound interest** (default **2.5%** every **10 real-time minutes**)
- Interest interval is real-world **hours / minutes / seconds** in `config.yml`
- Transaction **logs** (chat and both GUIs)
- **Refresh** reloads config and restarts the interest timer
- Everything is configurable (rates, limits, GUI, messages, storage)

## Requirements

- Java **25** (required by Minecraft / Paper 26.2)
- Paper (or Purpur / Folia) **26.2**
- An economy plugin: **[EssentialsX](https://essentialsx.net/)** (`economy.enabled: true` in `plugins/Essentials/config.yml`) and/or **[Vault](https://www.spigotmc.org/resources/vault.34315/)** plus a Vault provider (EssentialsX, CMI, …)

## Build (Maven)

```bash
cd PlayerBank
mvn -B clean package
```

The jar is `target/PlayerBank-1.0.0.jar`. Drop it in `plugins/` next to EssentialsX and/or Vault.

### Offline checks

`./build.sh` runs the resource consistency checks (message keys, config
paths, permissions, imports — see `tools/check_consistency.py`) and the
pure-logic smoke tests (amount parser, chest layout) on any JVM, no network
needed. CI runs the same script plus the real `mvn clean verify` on Temurin
25 (`.github/workflows/build.yml`).

## Commands

| Command | Description |
| --- | --- |
| `/bank` | Show your **bank** and **wallet** balances |
| `/bank gui [chest\|dialog]` | Open the bank GUI — with an argument, set your preferred menu style |
| `/bank deposit <amount>` | Move wallet money into the bank |
| `/bank withdraw <amount>` | Move bank money back to the wallet |
| `/bank interest` | Look up rate, interval, and time until the next tick |
| `/bank logs [page]` | Your transaction history |
| `/bank logs <player> [page]` | Another player's logs (staff) |
| `/bank refresh` | Reload config and restart the interest scheduler |
| `/bank help` | Command list |
| `/bankadmin reload` | Reload configuration |
| `/bankadmin forceinterest` | Run an interest tick immediately |
| `/bankadmin set \| give \| take <player> <amount>` | Edit a bank balance |

Amounts (command and GUI) accept plain numbers with grouping commas
(`1,000`), multiplier suffixes (`1k`, `2.5m`, `10b`, `1t`), `half`, `all` /
`max`, and percentages (`25%`). `50%` is the same as `half`.

Aliases: `/playerbank`, `/pb`, `/pba`.

## Permissions

| Permission | Default | Meaning |
| --- | --- | --- |
| `playerbank.use` | true | Use `/bank` |
| `playerbank.deposit` | true | Deposit |
| `playerbank.withdraw` | true | Withdraw |
| `playerbank.gui` | true | Open the GUI and pick a menu style |
| `playerbank.interest` | true | Look up interest |
| `playerbank.logs` | true | Own logs |
| `playerbank.balance` | true | Own balance |
| `playerbank.logs.others` | op | Other players' logs |
| `playerbank.balance.others` | op | Other players' bank |
| `playerbank.refresh` | op | `/bank refresh` |
| `playerbank.admin` | op | All admin tools |
| `playerbank.admin.reload` | op | Reload |
| `playerbank.admin.set` / `.give` / `.take` | op | Mutate balances |
| `playerbank.admin.forceinterest` | op | Force interest |

## The GUI

`/bank gui` (alias `/bank menu`) opens the bank GUI. Two backends ship in the
jar and **which one is used is configurable**:

- **`DIALOG`** — the new native Minecraft **menu screens** (Paper's dialog
  API): a main menu with balances, one-tap deposit/withdraw buttons, custom
  amount screens with a text field, an interest notice, and a paginated log
  viewer. No chest, no inventory — pure client-side screens.
- **`CHEST`** — the classic **chest inventory** menu: balance info item,
  deposit row, withdraw row, interest, logs, style switch, close — with a
  paginated in-inventory log browser and an interest detail view.
- **`AUTO`** (default) — menu screens when the server supports them, chest
  inventory otherwise.

```yaml
gui:
  type: AUTO            # AUTO | DIALOG | CHEST
  player-choice: true   # players may pick their own style (/bank gui chest|dialog)
  quick-amounts: ["100", "1000", "half", "all"]   # one-click buttons, max 7
  chest:
    rows: 4             # 4..6
    title: "<dark_gray>Bank"
    filler-material: GRAY_STAINED_GLASS_PANE
    icons: { ... }      # every button material
  sounds:
    enabled: true
    # key form (ui.button.click) or legacy enum form (UI_BUTTON_CLICK)
    open: BLOCK_ENDER_CHEST_OPEN
    click: UI_BUTTON_CLICK
    deposit: ENTITY_EXPERIENCE_ORB_PICKUP
    withdraw: ENTITY_PLAYER_LEVELUP
    error: ENTITY_VILLAGER_NO
```

With `player-choice: true` every player can switch styles with
`/bank gui chest` / `/bank gui dialog` or the toggle button inside either
menu; the choice persists in `data.yml`. Set `player-choice: false` to pin
`gui.type` for everyone. Both backends share the same quick-amount buttons,
limits, sounds, and transaction pipeline — the GUI can never bypass a limit
the command enforces.

## Isolation (important)

The bank account is **not** a Vault / EssentialsX bank account.

- Players **cannot** `/pay` from the bank.
- Shops, auctions, jobs, claims, and other plugins only see the **wallet**.
- Interest is applied **only** to the bank balance.
- To spend bank money in the rest of the server, the player must **withdraw** first.

## Configuration

`plugins/PlayerBank/config.yml`:

```yaml
bank:
  interest:
    enabled: true
    rate-percent: 2.5          # compound % per interval
    interval:
      hours: 0
      minutes: 10              # real time
      seconds: 0
    min-balance: 0.01
    max-balance: 0             # 0 = unlimited
    decimal-places: 2
    notify-players: true
  min-transaction: 0.01
  max-deposit: 0
  max-withdraw: 0
logs:
  max-entries: 100
  page-size: 8
gui:
  type: AUTO                   # AUTO | DIALOG | CHEST
  player-choice: true
  quick-amounts: ["100", "1000", "half", "all"]
  chest:
    rows: 4
    title: "<dark_gray>Bank"
    filler-material: GRAY_STAINED_GLASS_PANE
  sounds:
    enabled: true
storage:
  file: data.yml
  autosave-seconds: 60
```

All chat strings are in `messages.yml` (MiniMessage).

## How interest works

Each interval, every account with at least `min-balance` receives:

`payout = round(bankBalance * ratePercent / 100)`

That payout is added to the **bank**, then logged as `INTEREST`. The timer uses real wall-clock hours/minutes/seconds, not Minecraft day cycles.

`/bank refresh` reloads YAML and reschedules that timer. `/bankadmin forceinterest` applies one tick immediately.

## Storage

YAML file `plugins/PlayerBank/data.yml` — UUID, last known name, bank balance, log ring buffer, and (when players pick one) their preferred GUI style. Autosave is configurable.

## License

Same as the rest of this repository.
