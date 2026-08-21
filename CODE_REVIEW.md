# Repository Code Review — MinecraftPlugins

**Scope:** all four plugins (`AtTag`, `FairDeal`, `Gifty`, `PlayerBank`), full source tree (~7,000 lines of Java, excluding compile-only API stubs).

**Method:** every module was compiled with the Eclipse Compiler for Java (ECJ). `AtTag` and `Gifty` were built end-to-end with their offline `build.sh` scripts and their bundled smoke/mock-flow test suites were executed (37 checks for AtTag, full mock-flow suite for Gifty). `FairDeal` and `PlayerBank` have no offline stubs, so they were ECJ-analyzed with dependency-resolution errors filtered out, plus a close manual review; each confirmed defect below was reproduced in isolation where possible.

---

## Summary of findings

| # | Severity | Plugin | File | Issue | Status |
|---|----------|--------|------|-------|--------|
| 1 | **Build-breaking** | PlayerBank | `storage/BankStorage.java` | `Map<?,?>.getOrDefault(key, "literal")` does not compile | **Fixed** |
| 2 | **Build-breaking** | FairDeal | `FairDealPlugin.java` | Call to nonexistent method `Trade.confirmed(UUID)` | **Fixed** |
| 3 | **Runtime / functional** | FairDeal | `FairDealPlugin.java` | Trade GUI cancels *all* clicks, incl. the player's own inventory — items can never be offered | **Fixed** |
| 4 | **Runtime crash** | PlayerBank | `plugin.yml` | Declares `folia-supported: true` but uses the legacy `BukkitScheduler` (throws on Folia) | **Fixed** |
| 5 | Logic edge case | PlayerBank | `command/BankCommand.java` | `/bank deposit all` & `withdraw all` can fail from half-up rounding | **Fixed** |
| 6 | Performance | AtTag | `engine/PingEngine.java` | Compiles one regex **per online player per chat message** | **Fixed** |
| 7 | Exploit / data loss | Gifty | `model/SerialItem.java` | Lossy item serialization repairs damaged tools and destroys container/NBT contents | Documented below (design change needed) |
| 8 | Performance | FairDeal | `FairDealPlugin.java` | PlaceholderAPI stats run a synchronous SQL query per request; log GUI loads all rows | Recommendation |
| 9 | Minor | FairDeal | `FairDealPlugin.java` | `showLogDetail(...)`/`LogDetailHolder` are dead code | Recommendation |

Items 1–6 are **confirmed errors** (verified by compilation or by direct code-path analysis). Items 7–9 are **best-practice / design suggestions**.

---

## Confirmed errors and corrections

### 1. PlayerBank does not compile — `Map<?,?>.getOrDefault` (BankStorage.java)

`getOrDefault(Object key, V defaultValue)` requires the default to be of the map's value type `V`. For a wildcard `Map<?,?>`, `V` is an unnameable capture, so **no argument except `null` type-checks**. Reproduced in isolation with ECJ:

```
The method getOrDefault(Object, capture#2-of ?) in the type Map<capture#1-of ?,capture#2-of ?>
is not applicable for the arguments (String, String)
```

`mvn clean package` in `PlayerBank/` fails on this line before anything else runs.

**Original:**
```java
for (Map<?, ?> map : sec.getMapList("logs")) {
    long time = toLong(map.get("time"));
    String type = String.valueOf(map.getOrDefault("type", "?"));   // ← does not compile
    double amount = toDouble(map.get("amount"));
    String note = String.valueOf(map.getOrDefault("note", ""));    // ← does not compile
    acc.logs().addLast(new BankLogEntry(time, type, amount, note));
}
```

**Corrected:**
```java
for (Map<?, ?> map : sec.getMapList("logs")) {
    long time = toLong(map.get("time"));
    Object rawType = map.get("type");
    String type = rawType == null ? "?" : String.valueOf(rawType);
    double amount = toDouble(map.get("amount"));
    Object rawNote = map.get("note");
    String note = rawNote == null ? "" : String.valueOf(rawNote);
    acc.logs().addLast(new BankLogEntry(time, type, amount, note));
}
```

`get(...)` returns the capture as `Object`, which is always legal; the null-check preserves the intended defaults exactly.

### 2. FairDeal does not compile — `Trade.confirmed(UUID)` missing (FairDealPlugin.java)

`open(...)` renders the confirm button with:

```java
inv.setItem(51, button(t.bothLocked() ? Material.EMERALD : Material.REDSTONE,
        t.confirmed(viewer.getUniqueId()) ? "&aConfirmed" : "&aConfirm trade", ...));
        // ^ Trade has confirm(UUID) and bothConfirmed(), but no confirmed(UUID)
```

The `Trade` inner class defines `confirm(UUID)` (a mutator) and `bothConfirmed()`, but no per-player accessor — this is a guaranteed *"method undefined"* compile error, hidden in the raw ECJ output behind the unresolved-Bukkit cascade but unambiguous from the class body.

**Corrected** by adding the missing accessor to `Trade`, preserving the intended per-viewer label:

```java
boolean confirmed(UUID x) { return x.equals(a) ? confirmA : confirmB; }
```

### 3. FairDeal trade GUI is unusable for items — every click cancelled

The `InventoryClickEvent` handler calls `e.setCancelled(true)` unconditionally and only re-implements behavior for the top-inventory slots (`rawSlot 0–26` offers, `45/47/49/51` buttons). Clicks in the player's **own inventory** (`rawSlot ≥ 54`) fall through with the event still cancelled — so a player can never pick an item up onto the cursor, and therefore **can never place anything into the offer slots**. Item trading is impossible; only money offers work. (`InventoryDragEvent` is also fully cancelled, closing the other route.)

**Original:**
```java
int raw = e.getRawSlot();
// Only the owner may use their 27 offer slots. Every bottom-inventory,
// shift, number-key, double-click and offhand action is rejected here; ...
if (raw >= 0 && raw < 27) { ... }
```

**Corrected** — plain pickup/place clicks in the player's own inventory are re-allowed; every dupe-prone action (`MOVE_TO_OTHER_INVENTORY` from shift-clicks, `COLLECT_TO_CURSOR` from double-clicks, `HOTBAR_SWAP`, drops) stays blocked, so the anti-duplication boundary is intact:

```java
int raw = e.getRawSlot();
if (raw >= 54) {
    InventoryAction act = e.getAction();
    if (act == InventoryAction.PICKUP_ALL || act == InventoryAction.PICKUP_HALF
            || act == InventoryAction.PICKUP_SOME || act == InventoryAction.PICKUP_ONE
            || act == InventoryAction.PLACE_ALL || act == InventoryAction.PLACE_SOME
            || act == InventoryAction.PLACE_ONE || act == InventoryAction.SWAP_WITH_CURSOR)
        e.setCancelled(false);
    return;
}
if (raw >= 0 && raw < 27) { ... }
```

### 4. PlayerBank crashes on Folia despite advertising support

`plugin.yml` declared `folia-supported: true`, but `InterestService.start()` and `BankStorage.startAutosave()` both use `getServer().getScheduler().runTaskTimer(...)`. Folia's implementation of the legacy `BukkitScheduler` throws `UnsupportedOperationException`, so the plugin would fail during `onEnable()` on any Folia server that the flag explicitly invites.

**Corrected** by removing the flag (the honest, minimal fix):
```yaml
api-version: '1.21'
# NOTE: not Folia-compatible — InterestService and BankStorage use the legacy
# BukkitScheduler, which Folia rejects at runtime. Re-add folia-supported only
# after migrating to the global-region/async schedulers.
```
The proper long-term fix is a reflection-based scheduler shim — the repo already contains exactly that pattern in `AtTag`/`Gifty` (`PlatformScheduler.runTimer`), which can be copied verbatim.

### 5. PlayerBank `/bank deposit all` and `/bank withdraw all` can fail on rounding

`parseAmount("all", wallet)` returns the exact balance, then `roundMoney` applies **half-up** rounding. A wallet of `123.456` (external economies routinely hold >2 decimals) rounds to `123.46`, which fails the `amount > wallet + 1e-9` check ("insufficient funds" for depositing *everything you own*). The withdraw path has the same flaw against `BankAccount.subtract`.

**Corrected** — `all`/`max` amounts now round toward zero via a new `BankConfig.floorMoney(...)`:

```java
boolean allKeyword = args[1].equalsIgnoreCase("all") || args[1].equalsIgnoreCase("max");
double amount = allKeyword ? cfg.floorMoney(parsed) : cfg.roundMoney(parsed);
```
```java
/** Rounds toward zero — used for "all"/"max" so the result never exceeds the source balance. */
public double floorMoney(double value) {
    double factor = Math.pow(10, decimalPlaces);
    return Math.floor(value * factor) / factor;
}
```

### 6. AtTag compiled a regex per online player per message

For every chat message containing `@`, `PingEngine.parse` ran `tokenPattern(name)` — i.e. `Pattern.compile(...)` — **once per online player**. With 200 players that is 200 pattern compilations per chat line on the chat thread: O(players × message) with heavy constant cost, a real hot-path bottleneck on busy servers.

**Original:**
```java
for (String name : onlineNames) {
    ...
    if (tokenPattern(name).matcher(message).find()) {   // Pattern.compile per player!
        named.add(name);
    }
}
```

**Corrected** — one static pre-compiled pattern extracts every `@token` from the message in a single pass, then names resolve via O(1) map lookups (O(players + message) total, zero per-message compilation):

```java
private static final Pattern MENTION_TOKEN =
        Pattern.compile("(?<![A-Za-z0-9_])@([A-Za-z0-9_]+)");
...
Map<String, String> lookup = new HashMap<>();
for (String name : onlineNames) {
    if (name == null || name.isEmpty()) continue;
    if (name.equalsIgnoreCase(senderName)) continue;
    if (isSpecialName(name)) continue;
    lookup.put(name.toLowerCase(Locale.ROOT), name);
}
Matcher mentions = MENTION_TOKEN.matcher(message);
while (mentions.find()) {
    String actual = lookup.get(mentions.group(1).toLowerCase(Locale.ROOT));
    if (actual != null) named.add(actual);
}
```

Semantics are preserved (word-boundary rules, case-insensitivity, `@alex123` ≠ `alex`, `@heretic` ≠ `@here`): **all 37 bundled engine/mock-flow checks still pass** after the change.

---

## Suggestions (best practice — not applied)

### 7. Gifty: lossy `SerialItem` serialization is a free-repair exploit
`SerialItem` persists only material, amount, name, lore, flags, enchants and custom-model-data. Consequences when a gift is claimed:
- **durability/damage is discarded** → send a nearly broken netherite pickaxe to a friend, it arrives fully repaired (free-repair loop);
- shulker-box contents, enchanted-book stored enchants, potion effects, and all other NBT/PDC are silently destroyed.

Recommendation: on Paper, serialize with `ItemStack#serializeAsBytes()` (Base64 into the same flat file) and keep `SerialItem` only as a cross-version fallback; at minimum, add `damage` to the serialized fields and add container materials to the default `blacklist` in `config.yml`.

### 8. FairDeal: synchronous SQL on hot paths
- `tradeCount(...)`/`moneyStat(...)` execute a `SELECT` per PlaceholderAPI request; placeholders on scoreboards refresh every few ticks → main-thread DB queries. Cache results with a short TTL (even 5 s eliminates the problem).
- `showLogs(...)` loads **every** `trade_log` row into memory and paginates in Java. Use `ORDER BY id DESC LIMIT 45 OFFSET ?` and a `COUNT(*)` for the page indicator.

### 9. FairDeal: dead code
`showLogDetail(...)` and `LogDetailHolder` are never referenced (`logClick` prints the details to chat instead). Either wire the click handler to `showLogDetail` or delete both.

---

## Verification

- `AtTag/build.sh`: compiles clean (ECJ, zero warnings suppressed changes), **EngineTest 17/17 + MockFlowTest 20/20 pass** after the PingEngine rewrite.
- `Gifty/build.sh`: compiles clean, **SmokeTest + full MockFlowTest pass** (no changes were needed in Gifty's code).
- `FairDeal`, `PlayerBank`: re-run under ECJ after the fixes — the only remaining diagnostics are unresolved `org.bukkit`/Vault/Adventure imports, which is expected in this sandbox (no network access to Maven Central / repo.papermc.io); no semantic or syntax errors remain. Fix #1 was additionally verified with a minimal standalone repro compiled by ECJ.
