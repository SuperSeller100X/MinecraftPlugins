# ChestLock 🔐

ChestLock is a passcode and physical-key container protection plugin for **Minecraft Java 26.2**, built against **Paper API 26.2** with **Java 25**. It runs on **Paper, Purpur, and Folia** and uses Minecraft's native dialog screens for every passcode and settings workflow—passcodes never need to appear in chat or command arguments.

## What it does

Players look at a supported container and run `/cl lock`. A guided GUI asks for:

1. a passcode and confirmation;
2. how long a successful unlock should last (5 seconds to 1 hour by default); and
3. whether an initial physical key should be issued.

A locked container cannot be opened or mined normally. The player runs `/cl unlock`, enters the code in a private GUI, and receives a time-limited session for **that lock only**. During the session they can open it normally or physically break it. The optional second-break confirmation prevents accidental destruction.

Anyone who knows the correct passcode can unlock and break the container during their session. Only the lock creator can change/remove the lock or issue/revoke keys. An administrator can explicitly enable bypass mode or recover a lock with force-remove.

### Supported containers

- normal chests;
- trapped chests;
- barrels; and
- every color of placed shulker box.

Both halves of a double chest carry the same lock. Targeting either half addresses the complete double chest. Adding another chest beside a locked single chest is blocked so its inventory cannot be merged into the lock. When an authorized player breaks a locked shulker box, its dropped item keeps its contents but not the live lock metadata; place it again and create a new lock if protection is still wanted.

### Physical keys

A key is a named, glowing item (a tripwire hook by default) bound by unguessable random identifiers to exactly one lock.

- It opens only its bound container while held in either hand.
- It never permits mining, changing, or removing the lock.
- It is intentionally transferable: whoever holds it can open the container.
- The owner can issue duplicates or revoke **all** old keys and receive a replacement.
- Keys cannot be consumed as ordinary crafting ingredients.
- A lost key is harmless after key revocation or lock removal.

## Protection behavior

By default ChestLock prevents:

- unauthorized right-click and inventory opening, including opens requested by another plugin;
- unauthorized block breaking;
- TNT, creeper, and other block-list explosions;
- piston movement;
- burning and entity block changes; and
- merging a newly placed chest with a locked chest.

Per the selected design, **hoppers and hopper minecarts remain usable**. Set `protection.block-hoppers: true` if the server owner wants a fully sealed container instead.

Protection is event based. Administrative world editors or other plugins that replace blocks without firing Bukkit/Paper events can bypass any protection plugin; restrict those tools to trusted staff.

## Player workflow

### Create a lock

1. Look at a supported container within the configured distance.
2. Run `/cl lock` or `/cl l`.
3. Fill in the native GUI and click **Create lock**.

Passcodes default to 4–64 characters. Leading/trailing whitespace and control characters are rejected. Symbols and Unicode are supported.

### Unlock and use

1. Look at the lock.
2. Run `/cl unlock` or `/cl u`.
3. Enter the passcode in the GUI.
4. Open or mine the container normally before the displayed timer ends.

An open inventory is closed when its timed session expires unless the player still holds a valid key or has administrative bypass enabled. Wrong attempts are rate-limited with escalating cooldowns.

### Configure personal defaults

Run `/cl settings` or `/cl s`. Each player controls:

- default unlock duration for newly created locks;
- whether new locks create a key by default;
- auto-open after a successful unlock;
- sounds;
- particles;
- action-bar messages;
- second-action break confirmation; and
- owner notifications for wrong passcode attempts.

The duration and key choice selected while creating a lock become that player's defaults for the next lock. The actual unlock duration is stored on each lock and does not change when personal defaults change later.

## Commands

All commands have both a long and short form. `/chestlock` itself is shortened to `/cl`.

| Long form | Short form | Description | Permission |
|---|---|---|---|
| `/chestlock` | `/cl` | Open the main ChestLock GUI | `chestlock.use` |
| `/cl lock` | `/cl l` | Lock the targeted container through a guided GUI | `chestlock.lock` |
| `/cl unlock` | `/cl u` | Enter a passcode and start a timed access session | `chestlock.unlock` |
| `/cl info` | `/cl i` | Show non-secret information about the targeted lock | `chestlock.info` |
| `/cl change` | `/cl c` | Owner: verify and change the passcode | `chestlock.manage` |
| `/cl remove` | `/cl r` | Owner: verify and permanently remove the lock | `chestlock.manage` |
| `/cl key` | `/cl k` | Owner: issue a key or revoke old keys | `chestlock.key` |
| `/cl settings` | `/cl s` | Open personal defaults and feedback settings | `chestlock.settings` |
| `/cl help` | `/cl h` | Open the in-game command guide | `chestlock.use` |
| `/cl bypass` | `/cl bp` | Toggle explicit administrative bypass for yourself | `chestlock.admin.bypass` |
| `/cl forceremove` | `/cl fr` | GUI-confirm removal of any targeted or damaged lock | `chestlock.admin.forceremove` |
| `/cl reload` | `/cl rl` | Reload `config.yml` and `messages.yml` | `chestlock.admin.reload` |

Passcodes are never accepted as command arguments, so normal command logging does not expose them.

## Permissions

| Permission | Default | Purpose |
|---|---:|---|
| `chestlock.use` | everyone | Use `/chestlock` and `/cl` |
| `chestlock.lock` | everyone | Create locks |
| `chestlock.unlock` | everyone | Attempt passcode unlocks |
| `chestlock.info` | everyone | Read safe lock metadata |
| `chestlock.manage` | everyone | Change/remove locks the player owns |
| `chestlock.key` | everyone | Issue/revoke keys for locks the player owns |
| `chestlock.key.use` | everyone | Open a matching lock with a held key |
| `chestlock.settings` | everyone | Change personal settings |
| `chestlock.admin.reload` | operators | Reload configuration |
| `chestlock.admin.bypass` | operators | Toggle explicit bypass mode |
| `chestlock.admin.forceremove` | operators | Remove any lock without its passcode |
| `chestlock.admin` | operators | Parent for all administrative permissions |
| `chestlock.*` | operators | Every ChestLock permission |

Bypass is never silently inferred from operator status. Staff must run `/cl bp`, and bypass is disabled on logout.

## Installation

Requirements:

- Minecraft Java Edition server **26.2**;
- Paper, Purpur, or Folia 26.2;
- **Java 25** or newer at runtime; and
- no required plugin dependencies.

Steps:

1. Build or download `ChestLock-1.0.0.jar`.
2. Stop the server.
3. Copy the jar into the server's `plugins` directory.
4. Start the server.
5. Review `plugins/ChestLock/config.yml` and grant permissions through the server's permission manager.

The same jar is used on Linux, Windows, and macOS. All paths use Java's platform-independent file APIs and there are no native libraries.

## Build from source

```bash
cd ChestLock
mvn -B clean verify
```

The compiled plugin is written to:

```text
ChestLock/target/ChestLock-1.0.0.jar
```

The Maven build pins `io.papermc.paper:paper-api:26.2.build.115-stable` with `provided` scope and compiles with `--release 25`. Unit tests cover hashing, passcode policy, cooldown escalation, and duration formatting. A Java 25 build workflow is included at `ChestLock/.github/workflows/build.yml`; copy it to the repository root `.github/workflows/` to activate it on GitHub.

## Configuration

The generated `config.yml` is fully commented. Important settings:

| Path | Default | Meaning |
|---|---:|---|
| `target-distance` | `6` | Maximum command targeting distance |
| `containers.*` | `true` | Enable lock creation for each supported family |
| `passcodes.min-length` | `4` | Minimum passcode length |
| `passcodes.max-length` | `64` | Maximum passcode length |
| `passcodes.iterations` | `210000` | PBKDF2 work factor for newly created/changed passcodes |
| `passcodes.attempts-before-cooldown` | `3` | Failed attempts before cooldown |
| `passcodes.base-cooldown-seconds` | `5` | First cooldown |
| `passcodes.max-cooldown-seconds` | `300` | Cooldown ceiling |
| `unlock-duration.min-seconds` | `5` | Smallest lock duration selectable in the GUI |
| `unlock-duration.max-seconds` | `3600` | Largest selectable lock duration |
| `unlock-duration.default-seconds` | `30` | New player's initial default |
| `protection.explosions` | `true` | Remove locked blocks from explosion block lists |
| `protection.pistons` | `true` | Stop piston movement of locks |
| `protection.fire` | `true` | Stop locked blocks burning |
| `protection.entity-block-changes` | `true` | Stop entity block transformations |
| `protection.block-hoppers` | `false` | When true, stop automation into/out of locks |
| `keys.material` | `TRIPWIRE_HOOK` | Material used for generated keys |
| `security.clear-sessions-on-quit` | `true` | Remove timed sessions when a player leaves |
| `security.dialog-timeout-seconds` | `120` | Maximum age of an unfinished workflow |
| `logging.audit-actions` | `true` | Log lock lifecycle and failed attempts without passcodes |

`messages.yml` uses MiniMessage syntax and can be translated. `/cl reload` applies both files. Existing locks retain the hash iteration count and unlock duration with which they were created.

## Security and data storage

- Passcodes are hashed asynchronously with **PBKDF2-HMAC-SHA256**, a random 128-bit salt, a 256-bit result, and a configurable work factor.
- Plaintext passcodes are not written to configuration, logs, keys, or commands.
- Hash comparisons use constant-time `MessageDigest.isEqual`.
- Failed attempts are isolated by player and lock and receive bounded exponential cooldowns.
- Dialog submissions are single-player, time-limited workflows tied to the originally targeted lock.
- A lock is revalidated after asynchronous hashing, preventing stale-dialog races after passcode changes.
- Lock metadata is held in each container's Paper/Bukkit `PersistentDataContainer`; no external database is required.
- Copied lock metadata is removed from dropped and newly placed block-state items, preventing creative or shulker-item cloning from duplicating a lock UUID.
- Personal settings are atomically written to `plugins/ChestLock/players.yml`.
- Logs include actor, short lock ID, and location, but never a passcode.

Back up world data and `plugins/ChestLock/players.yml` with the rest of the server. Removing the plugin makes ChestLock metadata inactive; reinstalling it restores protection while the original block entities still exist.

## Folia compatibility

`plugin.yml` declares `folia-supported: true`, but ChestLock does not rely on that flag alone:

- block state and PDC operations run on the target location's region scheduler;
- player inventory, sound, particle, and dialog operations run on the player's entity scheduler;
- PBKDF2 and file writes run on Paper's async scheduler; and
- cross-thread transient state uses concurrent collections and immutable records.

No Bukkit main-thread scheduler assumptions are used.

## Platform notes

- **Paper 26.2:** primary API and test target.
- **Purpur 26.2:** supported because Purpur includes the Paper API.
- **Folia 26.2:** explicitly region-safe as described above.
- **Spigot/CraftBukkit:** not supported; ChestLock uses Paper's native dialog and scheduler APIs.
- **Older Minecraft versions:** not supported because `api-version: '26.2'` is intentional.

## License / support

This plugin is part of the [SuperSeller100X/MinecraftPlugins](https://github.com/SuperSeller100X/MinecraftPlugins) repository. Report reproducible issues with the server implementation/build, Java version, ChestLock configuration, and relevant log excerpt. Never include a real passcode in a bug report.
