# 🗳️ ChunkVoter

Community-voted **chunk regeneration** for Minecraft servers. Any player can
start a vote on the chunk they are standing in; everyone votes **YES** or
**NO**; if the majority says *yes*, the chunk is completely regenerated from
the world seed.

- **Version:** 1.0.0
- **Target:** Minecraft **26.2** ("Chaos Cubed") — **Paper / Purpur / Folia**
- **Java:** **25** (required by Minecraft 26.2)
- **Author:** SuperSeller100X

---

## ✨ Features

- **`/chunkvoter`** starts a regeneration vote for the chunk you're standing in
- **Live action bar** over the hotbar shows the running tally
  (`✔ YES n  ✘ NO n  • 12s  • chunk world (x, z)`)
- **Clickable chat buttons** that type `/cv yes` / `/cv no` into your chat
  bar (ready to send) so you can review before voting; you can also just type
  the command directly
- **Simple majority wins** — more YES than NO regenerates the chunk; a tie or
  a NO majority leaves the chunk untouched
- **Optional WorldGuard / region integration:** inside a claimed region only
  the **owner** can start the vote and cast the deciding vote; **admins** and
  `chunkvoter.bypass` always bypass. Without WorldGuard anyone can vote.
- **Folia-safe**: region/entity scheduling via Paper's threaded schedulers
  (reflection), falling back to the Bukkit scheduler on non-Paper servers
- Per-player **cooldown**, per-world **max concurrent votes**, **auto-cancel
  on quit**, fully **translatable messages** (MiniMessage + `{placeholders}`)
- **Admin tools**: list, cancel, force-regenerate and reload

## 📦 Requirements

| Requirement | Version | Notes |
|-------------|---------|-------|
| Server      | Paper / Purpur / Folia **26.2** (or newer 26.x) | `folia-supported: true` |
| Java        | **25** | Required by Minecraft 26.2 |
| WorldGuard  | 7.0.x (MC 26.1 – 26.2) | **Optional** — enables owner control on claimed chunks |

> WorldGuard is a Bukkit-only plugin and is **not Folia-compatible**. On a
> Folia server WorldGuard simply won't load, so owner control disables itself
> gracefully and voting works as an *unclaimed* area (anyone can vote).

## 🚀 Installation

1. Drop `ChunkVoter-1.0.0.jar` into your `plugins/` folder.
2. (Optional) Install [WorldGuard] + [WorldEdit] for region ownership control.
3. Start the server — `plugins/ChunkVoter/config.yml` and `messages.yml` are
   generated automatically.
4. Edit the config, then run `/chunkvoteadmin reload` (no restart needed).

[WorldGuard]: https://enginehub.org/worldguard
[WorldEdit]: https://enginehub.org/worldedit

## 🎮 How it works

1. A player runs `/chunkvoter` (or `/cv`) in the chunk they want to change.
2. The plugin announces the vote and shows the live tally in the action bar.
   The announcement chat message contains clickable **[YES]** and **[NO]**
   buttons that pre-fill `/cv yes` / `/cv no` in your chat bar.
3. Players click the button (then press Enter) or type `/cv yes|no`.
   You can change your vote at any time until it closes.
4. When the timer runs out:
   - **YES > NO** → the chunk is regenerated from the world seed.
   - **otherwise** → the chunk is left unchanged.

### Region control (WorldGuard)

If the chunk's centre sits inside a WorldGuard region:

- Only a region **owner** can start the vote and cast the deciding vote.
- Players with `chunkvoter.bypass` or `chunkvoter.admin` always bypass.
- Everyone else gets a "region owner only" message.

If the chunk is **not** inside a region (or WorldGuard is absent), any player
may start a vote and any player in the world may vote.

## ⌨️ Commands

| Command | Description | Permission |
|---|---|---|
| `/chunkvoter` | Start a regen vote for the chunk you're in (`/cv`, `/chunkvote`) | `chunkvoter.start` |
| `/chunkvoter yes` / `/chunkvoter no` | Cast your vote (`/cv yes`, `/cv no`) | `chunkvoter.vote` |
| `/chunkvoter info` | Show the active vote in your chunk | `chunkvoter.use` |
| `/chunkvoteadmin reload` | Reload config + messages (`/cva`) | `chunkvoter.admin.reload` |
| `/chunkvoteadmin list` | List active votes | `chunkvoter.admin.list` |
| `/chunkvoteadmin cancel [x z\|-all]` | Cancel a vote (or `-all`) | `chunkvoter.admin.cancel` |
| `/chunkvoteadmin force [x z]` | Regenerate a chunk immediately, skipping the vote | `chunkvoter.admin.force` |
| `/chunkvoteadmin info` | Show config summary | `chunkvoter.admin.info` |

> **Coordinates:** players use `cancel [x z]` / `force [x z]` in their own
> world (or no coords = the chunk they stand in). Console must provide
> `world x z`. Coordinates are **chunk** coordinates, not block coordinates.

## 🔐 Permissions

| Permission | Default | Description |
|---|---|---|
| `chunkvoter.use` | everyone | Base permission (required for `/chunkvoter`) |
| `chunkvoter.start` | everyone | Start a regeneration vote |
| `chunkvoter.vote` | everyone | Cast a yes/no vote |
| `chunkvoter.bypass` | op | Bypass WorldGuard owner checks |
| `chunkvoter.admin` | op | Access `/chunkvoteadmin` |
| `chunkvoter.admin.reload` | op | Reload configuration |
| `chunkvoter.admin.list` | op | List active votes |
| `chunkvoter.admin.cancel` | op | Cancel votes |
| `chunkvoter.admin.force` | op | Force regeneration |
| `chunkvoter.admin.info` | op | Show config summary |

## ⚙️ Configuration (`config.yml`)

```yaml
vote:
  duration-seconds: 30          # how long a vote stays open
  cooldown-seconds: 120          # wait between votes by the same player
  max-active-per-world: 5       # concurrent votes per world
  announce: true                 # send the clickable YES/NO announcement
  auto-cancel-on-quit: true      # cancel votes when the starter leaves

worldguard:
  mode: auto                     # auto | true | false
  require-owner: true            # only region owners may decide claimed chunks
  admins-bypass: true            # chunkvoter.admin always bypasses

regenerate:
  require-chunk-load: true       # load the chunk before regenerating it
```

## 💬 Messages & placeholders

All player-facing text is in `messages.yml` using **MiniMessage** tags
(e.g. `<green>`, `<white>`) with `{placeholders}` such as `{world}`, `{x}`,
`{z}`, `{yes}`, `{no}`, `{time}`, `{initiator}`, `{choice}`. The action bar
template is `actionbar` and the clickable button labels are
`vote-button-yes` / `vote-button-no`.

## 🗄️ Storage

ChunkVoter keeps no persistent data — active votes live in memory and are
cleared on restart or disable. Only `config.yml` and `messages.yml` are
written to disk.

## 🔨 Building from source

```bash
cd ChunkVoter
mvn -B clean package
# -> target/ChunkVoter-1.0.0.jar
```

Requires **JDK 25** (Paper 26.2's `paper-api` is compiled for Java 25) and
Maven. WorldGuard is **optional** and accessed reflectively — it is never a
build or hard runtime dependency.

## 📝 Notes

- `World#regenerateChunk(int, int)` (Bukkit API) is used; it is deprecated
  but the supported way to reset a single chunk from the world seed. It may
  also alter blocks on the edge of adjacent chunks — this is expected
  behaviour of chunk regeneration. On Folia the chunk is touched on its
  owning region thread; if a platform does not implement it, the plugin
  reports *"Chunk regeneration is not supported on this server platform."*
- Voting only applies to the chunk you're standing in; you must be in the
  chunk to cast a vote.
- Chat input is used only for the optional click-to-type vote interaction —
  no other chat is captured.
