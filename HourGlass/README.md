# HourGlass ⌛

Real playtime tracking for **Paper / Purpur / Folia 26.2**.

HourGlass counts how long each player really spends on your server — both the
**realtime** total (everything since their first join) and the **active** time
where the player was actually doing something, with idle time filtered out. The
number is worth keeping because it is measured from a monotonic clock and written
to plain YAML files, so it survives restarts, backups and moving the folder to
another machine.

Everything a player needs is one command: `/playtime` (`/pt`). Everything staff
need is one command: `/playtimeadmin` (`/pta`). No database, no shading, no
external services.

```
/pt                     your playtime, the way the config defines it
/pt total               every second since your first join
/pt active              the time you were doing something
/pt gui                 a clickable breakdown with your rank and milestones
/pta info Notch         what HourGlass has stored for somebody
```

---

## Contents

- [Install](#install)
- [What is counted, exactly](#what-is-counted-exactly)
- [Commands](#commands)
- [Permissions](#permissions)
- [Configuration](#configuration)
- [Duration syntax](#duration-syntax)
- [GUIs](#guis)
- [Milestones](#milestones)
- [Live display](#live-display)
- [Sounds](#sounds)
- [Placeholders (PlaceholderAPI)](#placeholders-placeholderapi)
- [Developer API](#developer-api)
- [Storage, export and purge](#storage-export-and-purge)
- [Folia, threads and clocks](#folia-threads-and-clocks)
- [Cross-platform behaviour](#cross-platform-behaviour)
- [Building and testing](#building-and-testing)
- [Troubleshooting](#troubleshooting)

---

## Install

1. Download or build `HourGlass-1.0.0.jar` (see [Building and testing](#building-and-testing)).
2. Drop it into `plugins/` on a **Paper, Purpur or Folia** server running
   **Minecraft 26.2** with **Java 25**.
3. Start the server. Four files appear in `plugins/HourGlass/`:
   `config.yml`, `messages.yml`, `gui.yml`, `players/` (one YAML file per player).
4. Optional: install **PlaceholderAPI** for `%playtime_*%` placeholders. HourGlass
   only registers its expansion when PlaceholderAPI is present — nothing else
   happens without it.
5. Adjust the files, then `/playtimeadmin reload` (aliases: `rl`, or
   `/playtime reload`).

Spigot is not supported: the plugin uses Paper APIs (Adventure text, registry
lookups, region scheduling). Purpur and Folia are Paper and work as-is.

---

## What is counted, exactly

Two counters are kept per player, both in whole seconds, both updated every
second by one shared timer (never one task per player):

| Counter | Meaning | Grows while |
| --- | --- | --- |
| **total** | realtime since first join | online, AFK, idle, frozen screens — everything |
| **active** | the time they were doing something | online *and* not idle (see below) |

`general.primary-metric` decides what `/playtime`, `/pt gui`, the leaderboard's
default sort, the live display and most placeholders show:

- `total` — the honest "this account has been here 240 hours" figure.
- `active` — the fair "this player actually played 180 of them" figure.

**Idle detection.** While `tracking.idle-detection: true`, a player who has not
moved, interacted, talked, swapped hands, changed slots, attacked or placed a
block for `tracking.idle-seconds` counts as idle and only adds to `total`. Any
of ~12 activity events resets the window, and the check is per player, so an
AFK fish farm does not inflate their ranking. Players with
`hourglass.bypass.idle` are never idle; players with `hourglass.bypass.untimed`
are not measured at all (useful for build teams and bots) — both are recorded
in the file so their history stays intact.

**Sessions.** Every login is a session. On logout the session is appended to the
player's history (`storage.history-size` entries are kept) with its own total and
active seconds, which is what `/playtime history` (`/pt h`) prints.

---

## Commands

Both commands are registered in `plugin.yml` with short aliases, both have a
full `TabCompleter` (sub-commands, then player names, then `on|off`, page
numbers …), and both print a translated usage line instead of a stack trace
when something is missing.

### `/playtime` — aliases `/pt`, `/ptime`, `/playtimes`, `/hg`, `/hourglass`

| Command | Aliases | Permission | What it does |
| --- | --- | --- | --- |
| `/playtime` | | `hourglass.use` | The primary figure, plus rank and session. |
| `/playtime total` | `/pt t` | `hourglass.total` | Realtime since first join. |
| `/playtime active` | `/pt a` | `hourglass.active` | Non-idle time. |
| `/playtime session` | `/pt s` | `hourglass.session` | This login: realtime and active. |
| `/playtime first [player]` | `/pt fi`, `/pt joined` | `hourglass.first-join` | First join + last seen dates. |
| `/playtime top [page\|self]` | `/pt lb`, `/pt lead`, `/pt leaderboard` | `hourglass.top` | The leaderboard, one page at a time; `self` jumps to your own row. |
| `/playtime rank [player]` | `/pt r` | `hourglass.rank` | Just the rank number. |
| `/playtime player <player>` | `/pt p`, `/pt o`, `/pt other` | `hourglass.see.others` | Another player's full summary — offline players included. |
| `/playtime history [player] [count]` | `/pt h` | `hourglass.history` | The last finished sessions. |
| `/playtime milestones [player]` | `/pt m` | `hourglass.milestones` | Progress against every configured milestone. |
| `/playtime gui [page]` | `/pt g`, `/pt menu` | `hourglass.gui` | Opens `stats`, `leaderboard` or `milestones` (see `gui.default-gui`). |
| `/playtime display <mode>` | `/pt d` | `hourglass.display` | `bossbar`, `actionbar`, `off` or `reset` for the live timer. |
| `/playtime info` | `/pt i`, `/pt about`, `/pt version` | — | Version, counters and the active settings. |
| `/playtime reload` | `/pt rl` | `hourglass.admin.reload` | Re-reads `config.yml`, `messages.yml`, `gui.yml`. |
| `/playtime help` | `/pt ?` | — | This list. |

Console may run `/playtime info`, `top`, `player`, `first`, `history`,
`milestones`, `reload` and `help`; anything that needs an inventory or a player
answers with `players-only` instead of an error. `commands.allow-console: false`
restricts the console to `/playtimeadmin reload`-style commands entirely.

### `/playtimeadmin` — aliases `/pta`, `/ptadmin`, `/hga`, `/hgadmin`, `/hourglassadmin`

| Command | Aliases | Permission | What it does |
| --- | --- | --- | --- |
| `/playtimeadmin info <player>` | `/pta i`, `/pta inspect` | `hourglass.admin.info` | Everything stored about one player: totals, rank, sessions, milestones, freeze and idle state. |
| `/playtimeadmin set <player> <duration>` | `/pta s` | `hourglass.admin.edit` | Overwrite the **total** (active is clamped below it). |
| `/playtimeadmin add <player> <duration>` | `/pta a`, `/pta give` | `hourglass.admin.edit` | Grant time — also fires any milestone crossed. |
| `/playtimeadmin remove <player> <duration>` | `/pta rm`, `/pta take` | `hourglass.admin.edit` | Take time back, never below zero. |
| `/playtimeadmin reset <player>` | `/pta rs`, `/pta clear` | `hourglass.admin.edit` | Wipe a record — first join is kept unless `admin.reset-keeps-first-join: false`. |
| `/playtimeadmin freeze <player> [on\|off\|toggle]` | `/pta f`, `/pta pause` | `hourglass.admin.freeze` | Stop or resume measuring somebody without deleting anything. |
| `/playtimeadmin list [page]` | `/pta l`, `/pta online` | `hourglass.admin.list` | Everyone online with total, active, session, idle and freeze state. |
| `/playtimeadmin top [refresh\|page]` | `/pta t`, `/pta leaderboard` | `hourglass.admin.leaderboard` | The cached leaderboard; `refresh` rebuilds it immediately. |
| `/playtimeadmin purge [days] [confirm]` | `/pta p` | `hourglass.admin.purge` | Delete records of long-inactive players. Without `confirm` it only reports how many would go (default `purge.default-days`). |
| `/playtimeadmin export` | `/pta e`, `/pta csv` | `hourglass.admin.export` | Write a CSV of every record into `plugins/HourGlass/exports/`. |
| `/playtimeadmin stats` | `/pta st`, `/pta status` | `hourglass.admin.stats` | Storage, timer, cache, milestone and sound internals. |
| `/playtimeadmin gui` | `/pta g`, `/pta menu` | `hourglass.admin.gui` | The paginated admin panel (players, freeze, ±time, reset, export, reload). |
| `/playtimeadmin reload` | `/pta rl` | `hourglass.admin.reload` | Re-read all three config files, rebuild the leaderboard, re-register placeholders. |
| `/playtimeadmin help` | `/pta ?` | — | The admin list. |

Tab completion is aware of permissions: a player only sees the sub-commands they
may actually run, and `commands.tab-limit` caps how many player names are sent.
With `commands.short-aliases: true` a single letter is enough for the obvious
ones (`/pt t`, `/pt a`, `/pt s`, …).

---

## Permissions

Every node is declared in `plugin.yml`, so `/permission` plugins, LuckPerms and
`defaults.yml` all see them with their descriptions. Grant `hourglass.*` for a
full install, or pick from the list.

| Node | Default | Grants |
| --- | --- | --- |
| `hourglass.use` | everyone | `/playtime` itself. |
| `hourglass.total` | everyone | `/pt total`. |
| `hourglass.active` | everyone | `/pt active`. |
| `hourglass.session` | everyone | `/pt session`. |
| `hourglass.first-join` | everyone | `/pt first`. |
| `hourglass.rank` | everyone | `/pt rank`. |
| `hourglass.top` | everyone | the leaderboard. |
| `hourglass.history` | everyone | `/pt history`. |
| `hourglass.milestones` | everyone | milestone progress. |
| `hourglass.gui` | everyone | the GUIs. |
| `hourglass.display` | everyone | the live bossbar / actionbar choice. |
| `hourglass.see.others` | everyone | look up other players (turn off for privacy). |
| `hourglass.notify.self` | everyone | the join summary. |
| `hourglass.notify.staff` | op | logout summaries for staff. |
| `hourglass.bypass.idle` | op | never counted as idle. |
| `hourglass.bypass.untimed` | op | not measured at all. |
| `hourglass.admin` | op | `/playtimeadmin` itself. |
| `hourglass.admin.info` | op | inspect a player. |
| `hourglass.admin.edit` | op | set / add / remove / reset. |
| `hourglass.admin.freeze` | op | freeze and resume. |
| `hourglass.admin.list` | op | the online list. |
| `hourglass.admin.leaderboard` | op | rebuild and browse the cache. |
| `hourglass.admin.purge` | op | delete inactive records. |
| `hourglass.admin.export` | op | write CSV exports. |
| `hourglass.admin.stats` | op | storage and timer statistics. |
| `hourglass.admin.gui` | op | the admin GUI. |
| `hourglass.admin.reload` | op | reload config files. |
| `hourglass.*` | op | everything above, declared as a parent with explicit children. |

---

## Configuration

Four files, all UTF-8, all reloadable with `/playtimeadmin reload`, none of them
containing a single hard-coded assumption in the plugin. Values are validated on
load: an illegal enum falls back to the documented default and is logged, a typo
in `gui.yml` never stops the server.

### `config.yml`

| Group | Options |
| --- | --- |
| `general` | `primary-metric` (`total` \| `active`), `time-zone`, `date-format`, `clock-24h`, `relative-times` |
| `storage` | `autosave-minutes`, `save-on-quit`, `flush-on-disable`, `history-size`, `max-players` |
| `tracking` | `idle-detection`, `idle-seconds`, `admin-edits-count-as-active`, `max-tick-gap-seconds`, `ignore-worlds` |
| `leaderboard` | `enabled`, `metric`, `per-page`, `refresh-seconds`, `include-offline`, `limit`, `self-highlight` |
| `display` | `mode` (`none` \| `bossbar` \| `actionbar`), `metric`, `update-seconds`, `bossbar.color`, `bossbar.overlay`, `bossbar.progress` |
| `join` | `summary-enabled`, `summary-delay-seconds`, `sound` |
| `quit` | `staff-broadcast` |
| `milestones` | `enabled`, `metric`, `check-seconds`, `broadcast`, `show-title`, `list` |
| `gui` | `fill`, `close-on-move`, `leaderboard-heads`, `allow-view-others`, `default-gui`, `progress-bar.filled` / `.empty` / `.length` |
| `export` | `directory`, `line-endings`, `add-bom`, `include-offline`, `sort` |
| `purge` | `default-days` |
| `admin` | `reset-keeps-first-join` |
| `placeholders` | `enabled`, `aliases` |
| `commands` | `allow-console`, `tab-limit`, `short-aliases` |
| `sounds` | `enabled`, `volume`, `pitch`, `events.<key>.sound` / `.volume` / `.pitch` / `.category` / `.enabled` |
| `format` | `style`, `max-units`, `include-seconds`, `separator`, `zero-text`, `units.<unit>`, `parse.default-unit` |

`format` is the reason the plugin reads well anywhere on the board:

```yaml
format:
  # units | words | digital | clock | compact | seconds
  style: units
  max-units: 3
  include-seconds: true
  separator: " "
  zero-text: "a brand new player"
  units:
    day: "d,day,days"     # short,singular,plural  (two or three parts)
  parse:
    default-unit: seconds # what a bare "45" means in admin commands
```

`general.time-zone` accepts `system`, `utc`, `gmt` or any IANA name
(`Europe/Berlin`), and `relative-times: true` turns "last seen" into
`3 hours ago` while keeping the absolute date in the GUI tooltip.

### `messages.yml`

Every string players and the console see, plus three switches:
`prefix`, `legacy-color-codes` (translate `&a`-style codes too) and
`escape-placeholder-values` (escape `<` inside player names, so a name cannot
inject MiniMessage formatting into someone else's chat). An empty value
disables that one message. Keys are grouped: `stats.*`, `leaderboard.*`,
`history.*`, `milestones.*`, `display.*`, `join.*`, `quit.*`, `gui.*`,
`admin.*`, `help.*` and the top-level error keys (`no-permission`,
`players-only`, `usage`, `invalid-duration`, `player-not-found`,
`unknown-subcommand`, `reloaded`, `reload-failed`, `internal-error`).

### `gui.yml`

One block per screen — `stats`, `leaderboard`, `milestones`, `admin`,
`admin-player` — each with `title`, `size`, `fill`, `filler-material`,
`filler-name`, `items.<key>` and (for the list screens) `list.start-slot`,
`list.cells`, `list.player-heads`, `list.item`. An item carries `slot`,
`material`, `player-head`, `name`, `lore`, `action` and, for the time buttons,
`seconds` / `minutes` / `hours` / `days` / `weeks`.

Actions: `none`, `close`, `back`, `open-stats`, `open-leaderboard`,
`open-milestones`, `open-admin`, `page-back`, `page-forward`, `page-first`,
`page-last`, `page-self`, `refresh`, `freeze-toggle`, `reset-time`, `add-time`,
`remove-time`, `view-player`, `toggle-display`, `copy-summary`, `export`,
`reload`. Every action is implemented — `tools/check_consistency.py` fails the
build if `gui.yml` or `GuiConfig` ever drift apart.

---

## Duration syntax

Used by `/pta set|add|remove`, by `milestones.list` and anywhere else a length
of time is configured:

```
45            45 seconds (or minutes with format.parse.default-unit)
90m  2h  3d  1w  1mo  2y         unit suffixes, combinable: 1d12h  2w3d
1:30          1 h 30 m           1:02:03  h:m:s      1:02:02:03  d:h:m:s
1 day, 5 hours                   words and "and" are accepted
```

Garbage is refused with `invalid-duration`; nothing is guessed.

---

## GUIs

- **stats** (27 slots): your head, the four figures, first join, rank, raw
  seconds, plus buttons for the leaderboard, milestones, the live display and a
  copyable summary line.
- **leaderboard** (54): 45 rows per page of real heads with their time, page
  navigation, `page-self` to jump to your own row, and `refresh`.
- **milestones** (27): one row per milestone with a progress bar built from
  `gui.progress-bar`, reached ones struck through.
- **admin** (54): everyone online sorted by playtime, click a head to open
  **admin-player** (36): freeze/resume, ±1 h / ±1 d / ±1 w, copy summary,
  reload-safe reset, export and reload in the bottom row.
- `reset-time` only fires on a **shift-click** and the GUI repeats the amount it
  removed, so nobody loses a year of playtime by accident.
- Clicks are cancelled everywhere, including drags and clicks outside the
  window, so no item can ever be taken out of a HourGlass GUI.
- `gui.close-on-move: true` closes the window as soon as the player walks off.

Slot collisions, unknown materials and unknown actions are logged once at load
and degraded instead of crashing.

---

## Milestones

```yaml
milestones:
  metric: total
  list:
    one-day:
      hours: 24
      message: milestones.reached      # any messages.yml key
      broadcast: true
      title: true
      sound: milestone                 # any sounds.events key
      commands:
        - "lp user %player% parent add veteran"
        - "give %player% diamond 1"
```

Each milestone is awarded **once per player** and remembered inside the player's
file, so a restart, a config edit or `/pta add` can never double-reward. Reaching
one fires `PlayerMilestoneReachEvent` first — other plugins may cancel it, which
suppresses the message, the sound, the title and the commands but keeps the award
itself. `%player%`, `%uuid%`, `%milestone%`, `%hours%` and `%seconds%` are
substituted in commands; commands always run from the console thread.

`/playtime milestones` and the milestone screen show progress towards every
threshold, including the ones still far away.

---

## Live display

`display.mode` sets the default for everyone, `/pt display` overrides it per
player (stored in the player's file, not in memory only):

- `bossbar` — a labelled bar whose progress is your playtime today, this week,
  or the run towards your next milestone (`display.bossbar.progress`), in any
  `BossBar.Color` and `BossBar.Overlay`.
- `actionbar` — a compact line every `display.update-seconds`.
- `off` — nothing at all.

The bar is created once per player, reused, and deleted on logout, quit and
reload; it is never left behind on disable. Refreshing is throttled per player,
so `update-seconds: 1` costs one string update, not a rebuild.

---

## Sounds

16 named events, each fully editable in `config.yml` (`sound`, `volume`,
`pitch`, `category`, `enabled`), all of them resolved through
`Registry.SOUNDS` so a datapack or modded sound id works too:
`gui-open`, `gui-close`, `gui-click`, `gui-back`, `gui-page`, `gui-error`,
`command-ok`, `command-error`, `admin-edit`, `admin-reset`, `admin-purge`,
`admin-export`, `milestone`, `join`, `display-on`, `display-off`.

`sounds.enabled: false` mutes everything at once; per-volume and per-pitch
multipliers sit next to it. A missing or misspelled sound id is logged once and
then stays silent — it never throws at the player.

---

## Placeholders (PlaceholderAPI)

Registered under every alias in `placeholders.aliases` — `playtime`,
`hourglass` and `hg` out of the box.

| Placeholder | Value |
| --- | --- |
| `%playtime_total%` / `..._formatted` | total, formatted with `format.*` |
| `%playtime_total_seconds%` `_plain` | raw seconds |
| `%playtime_total_minutes%` `_hours` `_days` | divided numbers for scores |
| `%playtime_active%` `..._formatted` `_seconds` `_plain` `_hours` | the active counter |
| `%playtime_session%` `..._formatted` `_seconds` | this login |
| `%playtime_session_active%` | active time this login |
| `%playtime_primary%` `..._formatted` `_seconds` | whichever metric `general.primary-metric` selects |
| `%playtime_first_join%` | date, honouring `general.time-zone` |
| `%playtime_last_seen%` | `online` or the last logout date |
| `%playtime_online%` `%playtime_idle%` `%playtime_frozen%` | `yes` / `no` |
| `%playtime_rank%` `%playtime_rank_plain%` | `#12` / `12` |
| `%playtime_sessions%` | finished sessions in the history |
| `%playtime_milestones_reached%` `_total` `_remaining` | milestone counters |
| `%playtime_milestones_next%` `_formatted` `_seconds` `_percent` | the next threshold |
| `%playtime_display%` | this player's live display mode |
| `%playtime_top_<n>_name%` | the name at leaderboard position *n* |
| `%playtime_top_<n>_time%` | the same row, formatted |
| `%playtime_top_<n>_seconds%` | the same row, raw |
| `%playtime_top_<n>_uuid%` | the same row, uuid |

Unknown params return `null` (PAPI keeps showing the raw placeholder to other
plugins rather than an empty string), and numeric params return `0` for players
HourGlass has never seen, so scoreboards never blink.

---

## Developer API

HourGlass exposes a small, dependency-free facade for other plugins. It is
installed while the plugin is enabled and removed on disable, so a soft-dependency
check is enough:

```java
if (HourGlassApi.isAvailable()) {
    HourGlassApi api = HourGlassApi.get();

    long total  = api.getTotalSeconds(playerId);    // realtime since first join
    long active = api.getActiveSeconds(playerId);   // idle time excluded
    long rank   = api.getRank(playerId);            // -1 when unranked
    boolean measuring = api.isTracking(playerId);   // is the player being counted?

    api.getFirstSeen(playerId);                     // Optional<Instant>
    api.getMilestonesReached(playerId);             // List<String> of milestone names

    api.addSeconds(playerId, 3_600L);                // stored, and fires milestones
    api.setTotalSeconds(playerId, 100L);             // admin-style overwrite (active clamps)
}
```

`getTotalSeconds`, `getActiveSeconds`, `getPrimarySeconds`, `getSessionSeconds`,
`getFormattedTotal`, `getFirstSeen`, `getLastSeen`, `isTracking`, `isFrozen`,
`getRank`, `getTopNames`, `getMilestonesReached`, `getNextMilestone`,
`getTrackedPlayers`, `isLoaded` and `getVersion` are read-only and safe to call
from any thread. `setFrozen`, `setTotalSeconds`, `addSeconds`, `reset` and `save`
write through the storage layer (they never touch a file on the calling thread)
and return `false` when the player is unknown or the plugin is shutting down.

Events: `PlayerMilestoneReachEvent` — `Cancellable`, with `getPlayer()` (may be
`null` for an offline award), `getPlayerId()`, `getMilestone()`, `getSeconds()`,
`isBroadcast()` / `setBroadcast(boolean)` and `getCommands()`. Cancelling keeps
the award recorded but suppresses the announcement, the sound, the title and the
commands. Listen with `@EventHandler(ignoreCancelled = false)` if you want to see
it regardless.

```java
@EventHandler
public void onMilestone(PlayerMilestoneReachEvent event) {
    getLogger().info(event.getPlayerId() + " reached " + event.getMilestone().name()
            + " after " + event.getSeconds() + " seconds");
}
```

---

## Storage, export and purge

Plain YAML, one file per player: `plugins/HourGlass/players/<uuid>.yml` (36
characters, no illegal ones, so the same folder works on ext4, APFS and NTFS).

```yaml
schema: 1
name: Notch
first-join: 1700000000000
last-seen: 1700000900000
total-seconds: 4200
active-seconds: 3600
frozen: false
display: inherit
milestones: [one-day, first-week]
history:
  - {start: 1700000000000, end: 1700000900000, total-seconds: 900, active-seconds: 800}
```

- Writes run on a single worker thread with one file per record, coalesced: a
  player who earns time every second produces one write, not 60. A failed save is
  re-queued, never dropped.
- `storage.autosave-minutes` flushes dirty records; `save-on-quit` writes on
  logout; `flush-on-disable` waits up to a bounded time so a `/stop` loses
  nothing. `storage.max-players` loads the most recent N records on startup and
  opens the rest lazily.
- **Backup**: copy `plugins/HourGlass/players/`. That is the whole database.
- `/playtimeadmin export` writes `plugins/HourGlass/exports/playtime-<date>-<timestamp>.csv`
  with the header

  ```
  uuid,name,total_seconds,total_readable,active_seconds,active_readable,
  first_join,last_seen,online,frozen,sessions,milestones
  ```

  both raw seconds *and* the formatted text, so the file can be sorted
  numerically in a spreadsheet and still read by a human. `export.sort`
  (`total` \| `active` \| `name` \| `last-seen`), `export.include-offline`,
  `export.line-endings` (`system` \| `lf` \| `crlf`) and `export.add-bom` (Excel
  wants the BOM for accented names) are all configurable, so the same export opens
  cleanly in Excel on Windows, Numbers on macOS and LibreOffice on Linux.
- `/playtimeadmin purge [days] [confirm]` previews first: without `confirm` it
  only counts the records older than `purge.default-days`. Deleting removes the
  file and forgets the cache, but never touches the player's own data.
- No SQLite, no MySQL, no shading, no async driver surprises.

---

## Folia, threads and clocks

- One 20-tick repeating task drives accumulation, leaderboard refresh, milestone
  checks, display updates and autosave; each of them self-throttles. There are no
  per-player timers to leak.
- `scheduler/PlatformScheduler` routes every player-bound action through
  `RegionScheduler` on Folia and the global scheduler elsewhere, so the same jar
  runs correctly on both. `folia-supported: true` is declared in `plugin.yml`.
- Elapsed time comes from `System.nanoTime()`, and each tick is clamped by
  `tracking.max-tick-gap-seconds`: a lag spike, a paused server or an NTP
  correction can create neither negative time nor a fabricated weekend.
- The join-time session start is anchored to wall-clock millis, so a restart
  mid-session does not silently truncate a login.
- `tracking.ignore-worlds` skips worlds where time should not be counted; the
  record keeps counting in others.

---

## Cross-platform behaviour

Identical on Linux, Windows and macOS, by construction rather than by luck:

- every path is `Path`-based, built from `getDataFolder().toPath().resolve(...)`
  with `Files.createDirectories(...)` — never a separator, never `new File(a + "/" + b)`;
- all resources and all player files are read and written as **UTF-8** through
  `YamlIO` (Bukkit's `YamlConfiguration#save(File)` uses the platform default
  charset, which is why it is never called here);
- files are written to a temp file and moved with `ATOMIC_MOVE` where the OS
  supports it, so a crash mid-save cannot leave a half-written record;
- dates use an explicit `ZoneId` and `Locale.US` pattern, so the same file
  renders the same string on a server in Hannover and one in Seattle;
- line endings in exports are chosen, not inherited;
- Java sources are ASCII-only: the hourglass glyph, the progress-bar blocks and
  the prefix arrow are written as `\u231B`, `\u2588`, `\u00BB` escapes so a wrong
  `-encoding` cannot change what players see. `tools/check_consistency.py` enforces it.

---

## Building and testing

```bash
cd HourGlass
mvn -B clean verify            # builds the jar and runs the tests
python3 tools/check_consistency.py   # resources vs. code, no JDK needed
```

- `pom.xml`: Java 25 (`maven.compiler.release`), `paper-api`
  `26.2.build.121-stable` **provided**, PlaceholderAPI `2.12.3` provided, no shade
  plugin at all. `plugin.yml`'s version is filtered from `${project.version}`.
- Tests cover the parts where an off-by-one would be visible to players:
  `TimeFormatTest` (every style, every parse form, clamping), `LeaderboardTest`
  (stable tie-breaking, paging, limits), `PlaytimeRecordTest` (active vs total,
  sessions, admin edits re-baselining a live session, history trimming),
  `YamlStorageTest` (round trip through real YAML, damaged files, negative
  timestamps), `DatesAndCsvTest` (zones, DST-free formatting, CSV quoting and line
  endings), `PluginDescriptorTest` (the shipped `plugin.yml` parses, both commands
  and every permission resolve) and `ResourceFilesTest` (gui.yml slots and
  actions, config.yml paths, sound events, `{placeholder}` hygiene).
- CI: `HourGlass/.github/workflows/build.yml` runs the consistency check, the unit
  tests and `mvn -B -ntp clean verify` on Temurin 25, matching the other plugins in
  this repository. GitHub only executes workflows found in `.github/workflows/` at
  the repository root, so `HourGlass/ci/root-workflow.yml` is shipped as a drop-in
  copy: `cp HourGlass/ci/root-workflow.yml .github/workflows/hourglass-build.yml`
  turns it on for every push and pull request that touches this folder.

---

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `/pt` says `a brand new player` for a veteran | `zero-text` is what an all-zero record looks like — check `general.primary-metric`; if the switch is `active`, the player's active time may be genuinely small. |
| Numbers differ from another plugin's playtime | Most "playtime" counters are session-based or use `playerStatistic(PLAY_ONE_TICK)`. HourGlass measures wall-clock elapsed time and excludes idle; that is the point. |
| Nobody is on the leaderboard | `leaderboard.enabled`, and the cache builds on a timer — `/playtimeadmin top refresh` forces it. |
| GUI looks empty or shifted | `gui.yml` slots or sizes; the load log names the offending line. |
| A message shows raw `{player}` | That placeholder is not filled by the caller — `python3 tools/check_consistency.py` reports these. |
| Time is not counting for one player | `hourglass.bypass.untimed`, `frozen: true` in their file, or they are idle (`/pta info <name>` says all three). |
| Export looks garbled in Excel | Set `export.line-endings: crlf` and keep `export.add-bom: true`. |
| Players see a bar they configured away | `/pt display reset` returns them to `display.mode`. |
| Console spam on startup about unknown sounds | A sound id in `config.yml` does not exist on this server version; fix the id or set that event's `enabled: false`. |

Data is never deleted by a config change: `storage.max-players` and
`purge.default-days` only limit what is loaded or explicitly purged, and the
`reset` and `purge` paths both say what they removed.
