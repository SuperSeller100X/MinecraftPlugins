# 🍩 Gifty

A sweet **gift & delivery system** for Minecraft servers, with a clean
DonutSMP-style GUI. Players compose gifts in a GUI, send them to a player's
personal **inbox**, and claim their items, money and messages whenever they
like — even across restarts.

- **Version:** 1.0.0
- **Target:** Minecraft **26.2** ("Chaos Cubed") — Paper / Purpur / Folia
- **Author:** SuperSeller100X

---

## ✨ Features

- **`/gift <player>`** opens a compose GUI: place items (1–54 slots,
  configurable), optionally add **money** and a **written message**
- **`/inbox [page]`** opens the delivery inbox: paginated (45 per page),
  click to claim — **Left-click** items, **Right-click** money,
  **Shift-click** everything, or use the *Claim all money* button
- **DonutSMP-style flow**: compose → ESC → confirm screen → send.
  Items are only taken when the gift is actually sent
- **Items + money + messages** all supported (Vault economy, auto-detected)
- **Notifications**: chat message + sound on arrival, reminder on join
- **Safe by design**: ESC cancels and returns items, quitting mid-gift
  returns items on next join, wrong items can't end up in the wrong inbox
- **Send cooldown** (default 60s), **inbox capacity** (default 108),
  optional **blacklist** of un-giftable materials
- **Flat-file storage** — one small YAML file per inbox, no database setup
- **PlaceholderAPI** placeholders for scoreboards/tab lists
- **Admin tools**: `/giftadmin reload|view|clear|expire|info`
- Every message is translatable/colorable in `messages.yml`
  (`&` codes and `&#RRGGBB` hex)

## 📦 Requirements

| Requirement | Version |
|-------------|---------|
| Server      | Paper / Purpur / Folia **26.2** (or newer 26.x) |
| Java        | **25** (required by Minecraft 26.2) |
| Economy     | any Vault economy (EssentialsX, CMI, ...) — optional |

`folia-supported: true` is set in `plugin.yml`; the plugin uses Paper's
region schedulers on Folia and the Bukkit scheduler elsewhere, so it runs
safely on all three platforms.

## 🚀 Installation

1. Drop `Gifty-1.0.0.jar` into your `plugins/` folder
2. (Optional) install [Vault] and an economy plugin for money gifts, and
   [PlaceholderAPI] for placeholders
3. Start the server — `plugins/Gifty/config.yml` and `messages.yml` are
   generated automatically
4. Edit the config, then run `/giftadmin reload` (no restart needed)

[Vault]: https://www.spigotmc.org/resources/vault.34315/
[PlaceholderAPI]: https://www.spigotmc.org/resources/placeholderapi.6245/

## 🎁 How it works (player guide)

**Sending a gift**
1. `/gift Steve` — a GUI opens
2. Put your items into the gift slot(s), then press **ESC**
3. The confirm screen shows what's included. Click **Money** or
   **Message** to add extras (type them in chat), then click
   **Send Gift**. Money amounts accept short forms: `1.5k`, `2m`,
   `1b`, `3t`, `1q` (thousand, million, billion, trillion, quadrillion).
4. Steve gets notified and can claim the gift in `/inbox`

**Receiving**
1. You get a chat message + sound when a gift arrives
2. `/inbox` — click a gift to see who sent it, what's inside and the
   message
3. **Left-click** = claim items · **Right-click** = claim money ·
   **Shift-click** = claim everything. Items that don't fit are dropped
   at your feet

## ⌨️ Commands

| Command | Description | Permission |
|---|---|---|
| `/gift <player>` | Open the compose GUI (aliases: `gifts`) | `gifty.send` |
| `/inbox [page]` | Open your inbox (aliases: `deliveries`, `mailbox`) | `gifty.inbox` |
| `/giftadmin reload` | Reload config + messages, re-detect economy | `gifty.admin.reload` |
| `/giftadmin view <player> [page]` | View someone's inbox (read-only GUI) | `gifty.admin.view` |
| `/giftadmin clear <player>` | Delete all pending gifts of a player | `gifty.admin.clear` |
| `/giftadmin expire <player>` | Expire & refund all pending gifts to their senders | `gifty.admin.expire` |
| `/giftadmin info` | Show config summary | `gifty.admin` |

## 🔐 Permissions

| Permission | Default | Description |
|---|---|---|
| `gifty.use` | everyone | Base permission |
| `gifty.send` | everyone | Send gifts |
| `gifty.inbox` | everyone | Open the inbox |
| `gifty.message` | everyone | Add a written message to gifts |
| `gifty.money` | everyone | Include money in gifts |
| `gifty.cooldown.bypass` | op | Ignore the send cooldown |
| `gifty.full.bypass` | op | Send to full inboxes |
| `gifty.admin` | op | Access `/giftadmin` (+ its sub-permissions) |

## ⚙️ Configuration (`config.yml`)

```yaml
general:
  send-slots: 1                # gift slots in the compose GUI (1-54)
  blacklisted-materials: []    # materials that can never be gifted
  cooldown-seconds: 60         # wait between sends (0 = off)
  max-pending-per-player: 108  # inbox capacity
  inbox-pages: 2               # pages in the inbox GUI (45 per page)
  expire-hours: 0              # 0 = gifts never expire; >0 = auto-refund
  notify-on-send: true         # chat notification on arrival
  play-sounds: true            # GUI sounds
  max-message-length: 100      # gift message cap
  save-interval-seconds: 300   # how often data is flushed to disk

economy:
  enabled: auto                # auto | true | false
```

When `expire-hours` is set, unclaimed gifts are automatically refunded
(items + money) to their senders through their own inbox.

## 💬 Messages & placeholders

All player-facing strings live in `messages.yml` with `{placeholders}`
(e.g. `{player}`, `{money}`, `{time}`) and support `&` and `&#RRGGBB`
colors. `\n` in a quoted string creates a new lore line.

**PlaceholderAPI** (optional):

| Placeholder | Meaning |
|---|---|
| `%gifty_pending%` | Pending deliveries in your inbox |
| `%gifty_total_sent%` | Gifts you've sent (all time) |
| `%gifty_total_received%` | Gifts you've received (all time) |
| `%gifty_cooldown_left%` | Seconds until you can send again |

## 🗄️ Storage

Everything is stored as human-readable YAML-subset files under
`plugins/Gifty/data/`:

- `inboxes/<uuid>.yml` — one file per player's inbox
- `meta.yml` — id sequence, counters and queued item returns

Items are serialized version-safely (material, amount, display name, lore,
item flags, enchantments, custom model data), so inboxes survive restarts
and Minecraft updates.

## 🔨 Building from source

```bash
cd Gifty
mvn -B clean package
# -> target/Gifty-1.0.0.jar
```

Requires **JDK 25** (Paper 26.2's `paper-api` is compiled for Java 25) and Maven.
Vault and PlaceholderAPI are `provided` dependencies (softdepends at runtime).

An older offline stub/`build.sh` layout may still exist under `stub-api/` and
`libs/` for reference, but Maven is the supported build.
## 📝 Notes

- Only chat input prompts temporarily capture chat; everything else
  (EssentialsX etc.) keeps working normally.
- On Folia, all player/world-touching work runs on the player's region
  thread; the global timer only touches data and files.
- Money gifts require a Vault economy; without one, the money feature
  disables itself gracefully (`/giftadmin info` shows the provider).
