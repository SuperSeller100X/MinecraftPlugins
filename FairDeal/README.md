# 🛡️ FairDeal

> **Planned plugin documentation** — FairDeal is a secure, player-to-player
> item and money trading system for modern Minecraft servers. This document
> records the agreed product specification before implementation begins.

FairDeal is designed to make direct player trades transparent and resilient:
each player places an offer, locks it, and confirms it. An offer can never be
silently changed after the other player has reviewed it. Items and Vault-backed
money are exchanged only after both parties have locked and confirmed the same
final offer.

- **Status:** requirements and documentation phase
- **Initial version:** 1.0.0
- **Author:** SuperSeller100X
- **Target server:** Paper, Purpur, and Folia for Minecraft 26.2
- **Runtime:** Java 25
- **Build:** Maven
- **Storage:** bundled SQLite database plus YAML recovery files; no separate database service required
- **Economy:** optional Vault-compatible economy provider (for example,
  EssentialsX or CMI through Vault)

> “Folia” is the correct platform name; it is supported alongside Paper and
> Purpur.

---

## ✨ Core experience

1. A player runs **`/trade <player>`**.
2. The target receives a clickable chat request and can accept or deny it with
   chat controls or `/trade accept` and `/trade deny`.
3. On acceptance, a clean two-sided GUI opens. Each player has **27 item offer
   slots**, can add money, and sees the other participant’s offer.
4. Each player clicks **Lock Offer** after reviewing their own offer. A locked
   offer cannot be edited.
5. If either player unlocks, both sides return to the editable state and every
   prior lock/confirmation is reset. This prevents bait-and-switch changes.
6. Once both offers are locked, each player clicks **Confirm Trade**.
7. FairDeal validates the final state, transfers the item offers and money,
   writes a complete transaction log, and displays a success message.

No player can complete a trade unilaterally. A trade must be explicitly locked
and confirmed by **both** participants.

## 🧰 Requirements

| Component | Requirement |
|---|---|
| Minecraft server | Paper, Purpur, or Folia for Minecraft **26.2** |
| Java | **Java 25** |
| Build tooling | Maven |
| Item trades | No external dependency |
| Money trades | Vault plus any Vault-compatible economy provider |
| Placeholders | PlaceholderAPI (optional) |

The plugin will remain useful without an economy provider: item trading works
normally and money controls are hidden or reported as unavailable. Vault is an
integration bridge, not an economy by itself; a provider such as EssentialsX
Economy or CMI supplies the actual player balances.

## 🎮 Player commands

| Command | Description |
|---|---|
| `/trade <player>` | Send a player a trade request. |
| `/trade accept` | Accept the pending request and open the secure trade GUI. |
| `/trade deny` | Decline the pending request. |
| `/trade cancel` | Cancel the current request or active trade. All offered items return safely. |
| `/trade inbox` | Open the recovery inbox for items that could not be returned directly. |
| `/trade log` | Open the player’s paginated completed/cancelled trade history. |
| `/trade toggle` | Stop receiving new incoming trade requests. The player may still send requests and finish a current trade. |

A request expires after **30 seconds** by default. The only intended baseline
restrictions for opening a trade are impossible/conflicting cases: offline
targets, trading oneself, and players already in incompatible active trade
states. The server owner can enable further restrictions in configuration when
needed.

## 🔐 Safe-trade rules

### Offers and locks

- Each participant can offer up to **27 GUI slots** of items.
- Normal vanilla and custom items are permitted and should preserve their item
  data. A configurable blacklist lets server owners reject selected materials
  or items.
- The GUI clearly separates *your offer* from *their offer*.
- A player may lock only their own offer.
- Locking prevents changes to that offer.
- If either participant unlocks, both locks and confirmations reset.
- Any offer change therefore requires both players to review and confirm again.
- Either player can cancel before completion. Cancellation returns all offers
  to their original owners.

### Money offers

Players add a money offer using a sign-style text input. The amount parser is
friendly and supports:

| Input | Meaning |
|---|---:|
| `1500` | 1,500 |
| `1,500` | 1,500 |
| `1k` / `1K` | 1,000 |
| `1.5m` | 1,500,000 |
| `2b` | 2,000,000,000 |
| `3t` | 3,000,000,000,000 |
| `all` | The player’s available balance |

Invalid, non-positive, or unaffordable amounts are rejected with a clear
message. If a participant lacks enough money at final validation, the money
offer is cancelled rather than allowing an incomplete exchange. Cancelling a
trade always returns the offered items and refunds/resolves any money held for
the trade.

The precise implementation will use a reliable reserve/validation flow so
balances cannot be spent twice between offer entry and confirmation.

### Interruptions and recovery

FairDeal’s default recovery behavior is **return to inventory**. On denial,
expiry, manual cancellation, disconnect, or an interrupted trade, offered
items are returned to their original owner where possible. If a direct return
cannot be completed safely, the implementation will persist the recovery so it
can be claimed through `/trade inbox` instead of losing items.

Completed/cancelled transactions are recorded in the log. A durable recovery
path is required for server shutdown/restart edge cases.

## 🖥️ GUI design

FairDeal uses a clean, security-first inventory interface:

- two clearly labeled, side-by-side offer areas;
- 27 item slots for each player;
- item previews retaining their normal names, lore, enchantments, and custom
  data;
- a visible money offer control and amount display;
- distinct editable, locked, waiting-for-other-player, confirmed, completed,
  and cancelled states;
- obvious **Lock**, **Unlock**, **Confirm**, and **Cancel** controls;
- explanatory lore so players understand why a confirmation reset;
- sound and title/message feedback configurable in language files.

The exact slot map, materials, names, lore, sounds, and colors will be
server-configurable.

## 🗄️ History, logs, and recovery inbox

### Player history

`/trade log` opens a paginated GUI containing the caller’s completed and
cancelled trades. An entry shows the partner, timestamp, result, item summary,
money amounts, and cancellation reason where applicable. Selecting an entry
opens full details.

### Staff audit history

Administrators can browse global player logs through paginated GUI tools,
searching a participant and reviewing the full record. Each complete audit
record is intended to include:

- both player UUIDs and last-known names;
- timestamps and duration;
- completion/cancellation status and reason;
- offered and received items with item data;
- money each side offered;
- world/server context where appropriate; and
- recovery actions or failures.

### Recovery inbox

`/trade inbox` is a durable mailbox for items that could not be returned to a
player’s inventory safely. It is separate from ordinary history and protects
players from item loss caused by full inventories or interrupted operations.

## 🛠️ Administration and permissions

FairDeal is intended for public use by default, while server staff receive
administrative tools.

### Planned staff controls

- reload configuration and messages;
- inspect and cancel active trades;
- view a player’s trade history;
- browse global logs in a paginated GUI;
- inspect recovery inboxes and assist with recovery;
- manage the trade blacklist; and
- view integration/storage diagnostics.

### Planned permissions

| Permission | Default | Purpose |
|---|---|---|
| `fairdeal.use` | everyone | Send, accept, and manage personal trades. |
| `fairdeal.inbox` | everyone | Open the personal recovery inbox. |
| `fairdeal.log` | everyone | Open personal trade history. |
| `fairdeal.toggle` | everyone | Toggle incoming trade requests. |
| `fairdeal.admin` | op | Full administrative access. |
| `fairdeal.admin.logs` | op | Browse other players’ and global trade logs. |
| `fairdeal.admin.active` | op | Inspect or cancel active trades. |
| `fairdeal.admin.recovery` | op | Assist with recovery inboxes. |
| `fairdeal.admin.reload` | op | Reload configuration and messages. |
| `fairdeal.bypass` | op | Bypass configured trade restrictions. |

Final granular permission nodes and default values will be documented with the
implementation.

## ⚙️ Configuration

Configuration will use editable YAML files and English messages with legacy
`&` color codes. The config will include at least:

- request timeout (default: 30 seconds);
- player request-toggle behavior;
- item blacklist;
- GUI titles, slots, visual materials, lore, and sounds;
- money parser/limits and currency display formatting;
- recovery and logging settings;
- optional restrictions (worlds, distance, game modes, combat hooks, etc.);
- configurable messages; and
- PlaceholderAPI text integration in configurable messages.

The core product goal is open, uncomplicated trading; restrictive controls are
there for server owners who need them, not imposed by default.

## 🔌 Integrations

| Integration | Planned behavior |
|---|---|
| Vault | Detect a compatible economy and securely process money offers. |
| EssentialsX / CMI | Supported through their Vault economy provider. |
| PlaceholderAPI | Optional placeholders and placeholder expansion in configurable text. |
| Paper / Purpur | Fully supported server platforms. |
| Folia | Supported with thread-safe, scheduler-aware implementation. |

### Planned PlaceholderAPI placeholders

| Placeholder | Meaning |
|---|---|
| `%fairdeal_active%` | Whether the player is in an active trade. |
| `%fairdeal_completed%` | Player’s completed trade count. |
| `%fairdeal_cancelled%` | Player’s cancelled trade count. |
| `%fairdeal_money_sent%` | Total money offered/sent through completed trades. |
| `%fairdeal_money_received%` | Total money received through completed trades. |
| `%fairdeal_inbox%` | Number of pending recovery-inbox entries. |

FairDeal also plans cancellable Bukkit events for request, acceptance, offer
change, lock, confirmation, completion, and cancellation, allowing other
plugins to apply their own server rules or react to trades.

## 🧱 Technical direction

FairDeal will be a standard Maven project targeting Java 25 and the relevant
Paper 26.2 API. It will include a Maven Wrapper, source and test structure,
plugin descriptor, default configuration/messages, documentation, and CI.

FairDeal uses a bundled SQLite database for transaction history and a YAML
recovery inbox. SQLite is packaged inside the plugin jar, so the server owner
does not install or administer a database service. This supersedes the early
YAML-only preference because the approved requirements prioritize durable
transaction history and recovery.

## ✅ Quality expectations

The project is planned to ship with:

- Maven build setup for Java 25;
- unit and integration-style tests for trade state, money parsing, locking,
  recovery, and persistence;
- manual test scenarios for Paper, Purpur, and Folia;
- detailed configuration, command, permission, and installation documentation;
- changelog/release notes; and
- CI build/test checks.

## 🚧 Next implementation decisions

The requirements captured so far provide the core contract. Before coding,
remaining implementation choices should be documented precisely, including
final GUI slot mapping, item blacklist matching rules, balance reservation
mechanics, crash recovery testing strategy, exact Maven/Paper API coordinates,
and staff command syntax.

