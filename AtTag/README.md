# AtTag 🔔

**@-mentions for Minecraft 26.2 (Paper / Purpur / Folia)**

AtTag brings Discord-style mentions to your server chat:

- **`@playername`** — the mentioned player hears a ping sound, so they never
  miss a message that calls them out.
- **`@here`** — replaced with your exact block coordinates, e.g.
  `[100, 64, 100]`, so everyone knows where you are.
- **`@everyone` / `@all`** — every online player gets pinged.

No commands, no permissions, no config hassle — drop the jar in `plugins/`
and it just works.

---

## ✨ Features

| Mention | Effect |
|---|---|
| `@alex` | **Alex** hears the player ping sound. The message keeps `@alex`. |
| `@here` | Replaced with the sender's coordinates as `[x, y, z]` (e.g. `[100, 64, 100]`). No sound. |
| `@everyone` | Every online player except the sender hears the everyone ping sound. |
| `@all` | Alias of `@everyone`. |
| Multiple mentions | `@alex @bob hey guys` pings both Alex and Bob — one sound each. |

### In-game examples

```
Alex > @Bob hello, how are you?        → Bob hears a "pop" 🔊
Alex > everyone come to @here          → chat shows "everyone come to [100, 64, 100]"
Alex > @everyone raid at spawn!        → everyone except Alex hears a "pop" 🔊
Alex > @all @Bob @bob                  → Bob pings once (no double ping), everyone else once
```

### Mention rules (the fine print)

- **Case-insensitive**: `@bob`, `@Bob` and `@BOB` all ping Bob.
- **Word boundaries**: `@bob123` does **not** ping a player named `bob`, and
  `@here` inside `@heretic` does not trigger. Mentions only match as whole
  words (preceded/followed by non-name characters, like spaces or punctuation).
- **Offline or unknown names** are left in the chat untouched — nobody is pinged.
- **The sender never hears their own ping** — you already know what you typed.
- **No double pings**: if a player is explicitly mentioned *and* `@everyone` is
  used, they hear exactly one sound (the explicit mention sound).
- **Special tokens win**: `@here`, `@everyone` and `@all` always behave as
  special tokens, even if a player happens to have that name.
- **`@here` coordinates** are the sender's *block* coordinates
  (floor of the exact position), formatted as `[x, y, z]`.

---

## 🔧 Configuration

On first start AtTag creates `plugins/AtTag/config.yml`:

```yaml
# Sound played to a player when someone mentions them with @playername.
player-sound: ENTITY_EXPERIENCE_ORB_PICKUP

# Sound played to every player (except the sender) on @everyone/@all.
everyone-sound: ENTITY_EXPERIENCE_ORB_PICKUP

# Volume (0.0 - 2.0) of the ping sounds.
volume: 1.0

# Pitch (0.5 - 2.0) of the ping sounds. 1.0 = normal.
pitch: 1.0
```

Any Bukkit/Paper sound name works. Popular choices:

| Sound | Effect |
|---|---|
| `ENTITY_EXPERIENCE_ORB_PICKUP` | subtle "pop" *(default)* |
| `BLOCK_NOTE_BLOCK_PLING` | classic "ding" |
| `BLOCK_NOTE_BLOCK_BELL` | bell |
| `UI_BUTTON_CLICK` | click |
| `ENTITY_PLAYER_LEVELUP` | level-up jingle |

If a configured sound name is invalid, AtTag logs a warning and falls back to
the default. Changes take effect on the next server restart (or plugin reload).

---

## 🎮 Commands

**None.** AtTag is intentionally a zero-command plugin — mentions work purely
through chat.

## 🔑 Permissions

**None.** Every player can use every mention (`@player`, `@here`, `@everyone`,
`@all`) — by design.

---

## 📥 Installation

1. Download/copy `AtTag-1.0.0.jar` into your server's `plugins/` folder.
2. Restart the server (or use a plugin manager to load it).
3. Done — start mentioning people!

## ✅ Compatibility

- **Minecraft 26.2** (Java Edition)
- **Paper**, **Purpur** and **Folia** (region-scheduler aware — Folia-safe
  sound delivery via the entity scheduler, with automatic fallback to the
  Bukkit scheduler on other Paper-family servers)
- Server JVM: Java **25** (required by Minecraft / Paper 26.2)
- No external dependencies (no Vault, no PlaceholderAPI, nothing)

---

## 🛠️ Building from source

### Option A — Maven (canonical)

Requires network access to Maven Central and `repo.papermc.io`:

```bash
cd AtTag
mvn -B clean package
```

Output: `target/AtTag-1.0.0.jar`.

### Option B — `build.sh` (offline)

Compiles with the Eclipse Compiler for Java against the bundled compile-only
API stubs (no network needed), then runs the full test suite:

```bash
cd AtTag
./build.sh          # or: JAVA_BIN=/path/to/java ./build.sh
```

Requires a Java runtime (17+) and `python3`. Produces the same
`target/AtTag-1.0.0.jar` and runs:

- `EngineTest` — 17 unit checks of the mention engine (pure logic, no server)
- `MockFlowTest` — 20 end-to-end checks against an in-memory mock of the
  Bukkit API (real plugin boot, chat rewriting, sound delivery)

---

## 🗂️ Project structure

```
AtTag/
├── pom.xml                     # Maven build (paper-api 26.2.build.112-stable, Java 25)
├── build.sh                    # offline ECJ build + tests
├── pack.py                     # jar packager used by build.sh
├── libs/ecj.jar                # Eclipse compiler (offline builds)
├── stub-api/src/               # compile-only Bukkit API stubs (never shipped)
├── smoke/                      # offline tests (EngineTest, MockFlowTest)
└── src/main/
    ├── java/dev/superseller/attag/
    │   ├── AtTagPlugin.java        # plugin main class
    │   ├── config/AtTagConfig.java # config.yml loading
    │   ├── engine/PingEngine.java  # pure mention-parsing engine
    │   ├── listener/ChatListener.java # chat event handling + sound delivery
    │   ├── scheduler/PlatformScheduler.java # Folia-safe scheduling
    │   └── util/MiniYaml.java      # tiny YAML parser
    └── resources/
        ├── plugin.yml
        └── config.yml
```

---

## ❓ FAQ

**Can players turn off ping sounds for themselves?**
Not in v1.0.0 — AtTag is intentionally minimal. If you want per-player toggles,
a `/ping` command, cooldowns, or permissions, open an issue or PR.

**Does AtTag change who receives the chat message?**
No. Only the message *text* can change (the `@here` → `[x, y, z]` replacement).
Normal chat delivery is untouched.

**Why `[100, 64, 100]` and not `[100.5, 64.0, 100.5]`?**
Block coordinates are what players can actually navigate to — decimals would
just be noise.

**Does it work on Spigot?**
The API usage is standard Bukkit, but AtTag is built and tested against the
Paper API and is optimized for Paper/Purpur/Folia. It will very likely work on
Spigot too, but it's not the supported target.
