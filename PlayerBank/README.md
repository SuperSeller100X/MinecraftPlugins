# PlayerBank

Isolated player bank for **Minecraft 26.2** (Paper / Purpur / Folia).

Wallet money (Essentials, CMI, or any **Vault** economy) stays in the normal economy. Bank money is stored **only** by this plugin. Other plugins — auctions, shops, `/pay`, orders — **cannot** spend the bank balance.

## Features

- **Deposit** wallet → bank (`/bank deposit`)
- **Withdraw** bank → wallet (`/bank withdraw`)
- **Vault** support for any economy plugin
- **Compound interest** (default **2.5%** every **10 real-time minutes**)
- Interest interval is real-world **hours / minutes / seconds** in `config.yml`
- Transaction **logs**
- **Refresh** reloads config and restarts the interest timer
- Everything is configurable (rates, limits, messages, storage)

## Requirements

- Java **25** (required by Minecraft / Paper 26.2)
- Paper (or Purpur / Folia) **26.2**
- [Vault](https://www.spigotmc.org/resources/vault.34315/) and an economy plugin (EssentialsX, CMI, etc.)

## Build (Maven)

```bash
cd PlayerBank
mvn -B clean package
```

The jar is `target/PlayerBank-1.0.0.jar`. Drop it in `plugins/` next to Vault and your economy plugin.

## Commands

| Command | Description |
| --- | --- |
| `/bank` | Show your **bank** and **wallet** balances |
| `/bank deposit <amount\|all>` | Move wallet money into the bank |
| `/bank withdraw <amount\|all>` | Move bank money back to the wallet |
| `/bank interest` | Look up rate, interval, and time until the next tick |
| `/bank logs [page]` | Your transaction history |
| `/bank logs <player> [page]` | Another player's logs (staff) |
| `/bank refresh` | Reload config and restart the interest scheduler |
| `/bank help` | Command list |
| `/bankadmin reload` | Reload configuration |
| `/bankadmin forceinterest` | Run an interest tick immediately |
| `/bankadmin set \| give \| take <player> <amount>` | Edit a bank balance |

Aliases: `/playerbank`, `/pb`, `/pba`.

## Permissions

| Permission | Default | Meaning |
| --- | --- | --- |
| `playerbank.use` | true | Use `/bank` |
| `playerbank.deposit` | true | Deposit |
| `playerbank.withdraw` | true | Withdraw |
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

## Isolation (important)

The bank account is **not** a Vault account.

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

YAML file `plugins/PlayerBank/data.yml` — UUID, last known name, bank balance, log ring buffer. Autosave is configurable.

## License

Same as the rest of this repository.
