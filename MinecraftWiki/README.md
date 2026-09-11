# MinecraftWiki

An in-game encyclopedia for Paper servers. `/wiki` opens a browsable, searchable GUI whose
articles are generated **from the running server** - the material, entity, enchantment, effect,
potion, biome, structure, sound, particle, attribute, damage type, game event, villager
profession and dimension registries, plus the live game rules, command map, registry tags and
recipe book. Nothing in this plugin is a hardcoded list of Minecraft facts, and no wiki content
is invented: if the server cannot supply it, it is not in the wiki.

- Paper API `26.2`, Java `25`, Maven
- 34 categories (21 backed by live data and enabled by default), all independently toggleable
- Inverted-index search: no full scan per query
- Six configuration files, MiniMessage everywhere, English only (translations are never faked)
- No NMS, no shading, no third-party dependencies

## Build

```bash
cd MinecraftWiki
mvn clean verify          # compiles, runs the unit tests, packages the jar
mvn clean package -DskipTests
```

The jar lands in `target/`. Drop it in `plugins/`.

`ci/build.yml` is a ready GitHub Actions workflow for the same build. It lives outside
`.github/workflows/` because the automation account that pushes this branch is a GitHub App
without the `workflows` permission; copy it to the repository root to activate it.

> **Verification status of this checkout is documented in [Verification](#verification).**
> In short: the sources type-check against the real Paper 26.2 API and 34 unit tests pass,
> but `mvn clean verify` has never been run here and the plugin has never been loaded by a
> server. Read that section before trusting it.

## Commands

| Command | What it does |
| --- | --- |
| `/wiki` | Opens the wiki home (or the help list, if `behaviour.open-on-empty-arguments` is false) |
| `/wiki search <query> [category]` | Searches and opens the results menu; from the console it prints a list |
| `/wiki category <name>` | Opens one category, `list` prints every category |
| `/wiki article <id>` | Opens one article by id, or by exact title |
| `/wiki lang [code]` | Shows or sets the player's wiki language |
| `/wiki reload` | Reloads configuration and rebuilds the catalogue |
| `/wiki help` | Command list |
| `/wikiadmin info` | Article/category/provider/index/session counts and timings |
| `/wikiadmin rebuild` | Rebuilds the catalogue and search index without re-reading config |
| `/wikiadmin reload` | Same as `/wiki reload` |
| `/wikiadmin debug [on\|off]` | Toggles debug logging at runtime |
| `/wikiadmin article <id>` | Dumps one article's metadata to the console |
| `/wikiadmin provider [id]` | Lists providers and how many articles each contributed |

Aliases: `mcwiki`, `encyclopedia`, `mwikiadmin`, `wikia`. Tab completion covers subcommands,
category ids, article ids and languages (capped at 60 suggestions).

## Permissions

| Node | Grants |
| --- | --- |
| `minecraftwiki.use` | Opening the wiki |
| `minecraftwiki.search` | Searching |
| `minecraftwiki.lang` | Choosing a language |
| `minecraftwiki.reload` | `/wiki reload` |
| `minecraftwiki.admin` (+ `.reload`, `.info`, `.rebuild`, `.debug`, `.article`, `.provider`) | The admin command |
| `minecraftwiki.bypass.cooldown` | Ignoring the search cooldown |
| `minecraftwiki.category.<id>` | Browsing one category (registered automatically, default `true`) |
| `minecraftwiki.article.<id>` | Reading one article (opt-in, only when an article sets `permission`) |

Every node name is configurable in `permissions.yml`.

## Configuration

| File | Purpose |
| --- | --- |
| `config.yml` | Language, search limits/cooldown/weights, chat input, caching, index rebuild, enabled providers, navigation, logging |
| `messages.yml` | Every player-facing string, MiniMessage, with `<placeholders>` |
| `gui.yml` | Inventory sizes, slot layout, item icons, labels, sounds, animation |
| `categories.yml` | The 34 categories: id, title, description, icon, `enabled`, `order`, permission |
| `articles.yml` | **Ships empty.** Hand-written articles only; full schema documented in the file |
| `permissions.yml` | Permission node names |

Validation is strict and non-fatal: every problem is collected into a `ConfigIssue` (error,
warning or info) naming the file, the key path and what was done about it, then printed under
`[Wiki/Config]` at startup. Unknown materials fall back to a safe default with a warning; an
article pointing at a category that does not exist is reported as an error and skipped rather
than silently vanishing. A missing key anywhere falls back to the built-in default and says so.

### Categories

Enabled by default because live data exists for them:
`blocks`, `items`, `mobs`, `entities`, `biomes`, `dimensions`, `enchantments`, `potions`,
`effects`, `recipes`, `commands`, `gamerules`, `structures`, `tags`, `sounds`, `particles`,
`attributes`, `damage-types`, `game-events`, `villager-professions`, `advancements`
(provider off by default in `config.yml`).

Disabled by default, and why:

- `materials` - would duplicate `blocks` and `items` as one combined list.
- `villager-trades` - trades are generated per villager at runtime; there is no trade registry
  to read, so any page here would have to be invented.
- `game-mechanics`, `redstone`, `farming`, `combat`, `transportation`, `nether`, `end`,
  `brewing`, `fishing`, `mining`, `crafting` - thematic categories. They exist and are fully
  wired (toggle, permission, search, ordering), but they have no registry behind them, so they
  stay empty until you add articles to `articles.yml`.

## Architecture

```
MinecraftWikiPlugin          enable order, reload, MenuFactory, accessors
api/          MinecraftWikiApi
article/      Article, ArticleBuilder, ArticleId, ArticleIcon, ArticleKind,
              ArticlePage, ArticleSection, ArticleRef, ArticleRepository
config/       PluginSettings, GuiSettings, CategoryConfig, PermissionNames,
              GuiItemKey, GuiSlot, ItemSpec, TextSpec, SoundSpec, WikiCategory,
              ConfigFile, ConfigIssue(s), YamlReader
content/      Labels, ContentResolver(s), ArticleContentService   (lazy article bodies, LRU)
provider/     WikiProvider, ArticleProvider, ArticleSink, RegistrySnapshot,
              MinecraftRegistryProvider, RegistryArticleProvider, MaterialProvider,
              EntityProvider, RecipeProvider, DetailProviders, CustomArticleProvider,
              RegistryEntry, ProviderIds
search/       SearchIndex, SearchService, SearchScore, SearchQuery, SearchEntry,
              SearchMatch, SearchField, SearchResult
gui/          WikiMenu, HomeMenu, CategoryMenu, ArticleMenu, SearchResultsMenu,
              RecipeMenu, HelpMenu, MenuContext, MenuFactory, MenuHolder, GuiButton,
              GuiItem(Factory), EntryRenderer, Pagination, ClickInfo
session/      WikiSession, NavigationManager
input/        SearchInputManager
listener/     WikiGuiListener
scheduler/    PlatformScheduler
util/         Text
```

Two decisions carry most of the weight:

**`RegistrySnapshot` is the only thing that touches Bukkit registries, and it does so once.**
At enable (and again on `/wiki reload`) the plugin walks the registries on the main thread and
copies what it needs into an immutable graph of plain records - no `Material`, no `EntityType`,
no handles. Providers, the search index and the article bodies are all built from that snapshot,
which is what makes off-thread work legal.

**Menus are dumb; `MenuContext` is the only thing they talk to.** Every menu holds a context, a
player and a page number, renders into a slot map, and returns `GuiButton`s with click handlers.
No menu opens another menu directly - they all go through `MenuFactory`, which is what stops the
menu classes depending on each other in a cycle.

### Threading

All Bukkit object access happens on the entity's scheduler through `PlatformScheduler`, which
uses Paper's `RegionScheduler`/`EntityScheduler` when present and falls back to
`BukkitScheduler` otherwise. Nothing touches an `Inventory`, `ItemStack` or `Player` from a
worker thread; search and article-body resolution run off-thread only over the immutable
snapshot and return plain data.

### Folia, honestly

`plugin.yml` declares `folia-supported: true` and the scheduling is written for it. What has
**not** been done: Folia has no test server here, so the claim rests on code review -
per-entity scheduling for every GUI operation, no cross-region access, no global state mutated
from two regions. Treat Folia support as untested until someone loads the plugin on a Folia
server.

### Search

`SearchIndex` is an inverted index: `token -> postings`, built once per catalogue, with a sorted
token array and a binary search for prefix ranges. A query is folded and split into terms, every
term must match, the strongest `SearchMatch` per entry wins, and `SearchScore` ranks with the
weights from `config.yml` (default: exact title 1000 > prefix title 600 > partial title 300 >
exact keyword 250 > prefix keyword 150 > partial keyword 75 > category 40 > summary 20).
Results are capped, paginated in the results menu, and rate-limited per player with a cooldown
that is cleared on disconnect and on reload.

### Search input

Paper 26.2 exposes `io.papermc.paper.dialog.Dialog` and `DialogInput.text`, but no
`Player#showDialog` and no dialog response event in its public API, so there is no supported way
to open a text-input dialog from a plugin. Input is therefore taken from chat:

1. `/wiki search` with no query (or the search button) prints a prompt and remembers the open menu.
2. The next chat line is the query. Typing the cancel word (default `cancel`) restores the previous menu.
3. A line starting with `/` is never treated as a query; `search.input.reject-commands` decides
   whether the prompt stays open or is dropped and the line passed through untouched.
4. An empty line re-prompts. The prompt times out (default 30 s) and restores the previous menu.
5. Every message is configurable in `messages.yml` (`input-*`).

## Verification

What was actually run on this checkout:

| Check | Tool | Result |
| --- | --- | --- |
| Type-check of all 77 main sources against the real Paper 26.2 API sources, Adventure 5.2.0 and jspecify | Eclipse ECJ 3.45.0, `-source 25 -target 25` | 0 errors |
| Type-check of all 7 test sources | same harness + JUnit API stubs | 0 errors |
| Execution of the shipped unit tests (34) against the compiled plugin classes | a reflective runner in `/tmp`, since no JUnit jar is obtainable here | 34 passed, 0 failed, 1 not runnable |
| `mvn clean verify` | - | **never run**: no Maven in the sandbox and `repo.papermc.io` / Maven Central are unreachable from it |
| Loading the plugin on a server, any GUI, any click, any command in game | - | **never done** |
| MiniMessage tag rendering | - | **not executed here** - Adventure is a named module and its sealed `Tag.Argument` permits a type from another package, which is illegal in the unnamed module ECJ compiles in. `Text.mini` type-checks but was never rendered. |

The 34 tests cover `Pagination` (empty, exact-multiple, partial and clamped pages, and the
`total`/list-size mismatch), `Text` (sanitising, folding, prettifying, joining, namespace
stripping), `SearchQuery` (folding, terms, blank and null input, category filter),
`SearchScore` (relevance ordering, non-matches score zero, every-term-must-match, configurable
weights), `SearchIndex` (only reachable articles indexed; invisible articles, disabled
categories and unknown categories excluded; titles, keywords, summaries and categories all
searchable; querying does not grow the index) and `SearchService` (EMPTY/TOO_SHORT/TOO_LONG
/COOLDOWN/OK, relevance order, both-terms matching, category filter, result cap, cooldown
expiry, bypass, `forget`, NO_RESULTS, and in-place index swap on reload).

Three real bugs were found this way and fixed:

1. `Pagination.slice` could return entries beyond the declared `total`, disagreeing with `shown(page)`.
2. `Text.fold` lowercased but did not normalise whitespace, so `"diamond  sword"` failed to match the title `Diamond Sword`.
3. `SearchQuery.raw` kept untrimmed input, which was echoed straight into messages.

Everything else - that the GUI renders sensibly, that the icons are not ugly, that reload
behaves under load, that Folia regions cooperate - is **unverified**.

## Testing checklist

Manual pass, on a real server. None of these have been executed.

1. `/wiki` opens the home; every enabled category is on the first pages and disabled ones are absent.
2. Breadcrumbs read Home » Category » Article and each crumb is clickable.
3. Back, previous, next, page info, search, categories, related, help and close all work; filler slots do nothing.
4. A category with more than 28 articles paginates; the last page shows only what is left.
5. An article with several pages switches pages and keeps its scroll position sane.
6. Search from the command returns the same results as search from the GUI button.
7. Chat search prompt: cancel word restores the previous menu; timeout restores it too.
8. A line starting with `/` during a prompt is never used as a query.
9. `/wiki search diamond items` filters to items; `/wiki search diamond nope` says the category is unknown.
10. Searching twice inside the cooldown is refused and says how long to wait; `minecraftwiki.bypass.cooldown` skips it.
11. A search with no match prints the no-results message and opens the empty-results menu.
12. `/wiki category <id>` for a disabled category says it is disabled; an unknown id says it is unknown.
13. `/wiki article <id>` for an article with `permission` set denies a player without the node.
14. Recipe menu shows the real 3x3 grid, the arrow, the result and the recipe type; an item with several recipes can be paged.
15. `/wikiadmin info` counts match `/wikiadmin provider` totals.
16. `/wikiadmin rebuild` reports a time and the article count; a search immediately afterwards works.
17. `/wiki reload` with a menu open reopens the home; sessions do not point at stale menus.
18. Deliberately break `config.yml` (bad material, unknown provider id, `max-results: 0`, missing section) and confirm startup names the file, the key and the fallback.
19. Add a hand-written article to `articles.yml` with a bogus category and confirm startup reports it as an error and skips it.
20. Disconnect with a menu open, and reload while menus are open: no exceptions, no leaked sessions (`/wikiadmin info` sessions drop back to 0).

## Security review

Code review, not tested in game.

- **Clicks**: `WikiGuiListener` cancels every `InventoryClickEvent` whose top inventory holder is
  a `MenuHolder`, regardless of click type, so shift-click, number-key (`hotbar`), double-click
  collect and offhand swap cannot move items in or out. `ClickType.CREATIVE` is cancelled too.
- **Drags**: `InventoryDragEvent` is cancelled when any raw slot falls inside the top inventory.
- **Invalid slots**: buttons are looked up in a `Map<Integer, GuiButton>`; a click on a filler or
  border slot finds nothing and does nothing. Slot numbers come from `gui.yml` and are clamped to
  the inventory size, so a misconfigured slot cannot index out of bounds.
- **Permission bypass**: category and article permissions are checked when a menu is built *and*
  on every navigation action, not just once; the search index excludes articles in disabled
  categories, and article-level permission is re-checked before an article menu opens.
- **Input**: any player-typed string substituted into a MiniMessage template goes through
  `Text.sanitize`, which strips `<...>`, so a query like `<red>x</red>` cannot inject formatting
  or a `click:`/`hover:` event. Placeholders for known-safe values use `Placeholder.unparsed`.
- **NPEs**: nullable registries are read defensively, `Material` falls back on unknown names, and
  the article-body cache is bounded (default 1024) and cleared on reload and disable.
- **Threading**: no Bukkit object is touched off the main/entity thread; the search path only
  reads the immutable snapshot.
- **Reload/disable**: `onDisable` closes open menus, disposes them, clears sessions, cooldowns
  and the body cache, and cancels scheduled tasks; listeners are registered exactly once and
  managers are reconfigured in place rather than replaced, so no listener is registered twice and
  no stale reference survives a reload.

Residual risk worth naming: `RegistrySnapshot` walks the registries once at enable, so a plugin
that adds content later is not reflected until `/wikiadmin rebuild`. The article-body cache is
shared across players, so a body must never contain per-player data - it does not today, and
anything added there has to keep that property.

## Extending

```java
MinecraftWikiApi api = MinecraftWikiApi.get();
api.registerProvider(myProvider);   // WikiProvider -> contribute(ArticleSink)
api.registerResolver(myResolver);   // fills in article bodies for a kind of article
```

`WikiProvider` is the extension point: implement `id()` and
`contribute(ArticleSink)`, use `ArticleBuilder` for the metadata, and let a `ContentResolver`
produce the body from your own data. `MinecraftRegistryProvider` is a ready base class for
anything backed by a `Registry`. Providers take part in the next catalogue rebuild
(`/wikiadmin rebuild` or `/wiki reload`).
