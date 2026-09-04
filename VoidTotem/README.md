# VoidTotem 🟣

A custom **Void / Shard Totem** for Minecraft **26.2** (Paper / Purpur / Folia), Java 25.
It saves a player from the void: when they are about to die in the void while
carrying one, the totem is consumed and they are pulled back to safety.

It is sold for **1000 shards** in the **ShardTools** shard shop, and only *this*
totem uses your custom Blockbench model — every other (vanilla) totem stays
normal.

- `/vt give <player> [amount]` — hand out Void Totems (also called automatically
  by ShardTools when the shop item is bought)
- `/vt info` — what the totem does
- `/vt reload` — reload `config.yml`
- Permissions: `voidtotem.give`, `voidtotem.reload`, `voidtotem.*` (all default op)

Build with `mvn -B clean package` in `VoidTotem/` (JDK 25 required). Output:
`target/VoidTotem-1.0.0.jar`.

---

## How it works with ShardTools

On startup VoidTotem adds a `void_totem` entry to ShardTools' shop config
(price `shop-price` in `config.yml`, default **1000**) and rebuilds the catalog,
so it shows up in `/st shop` immediately. Buying it runs the console command
`voidtotem give %player% 1`, which hands out the properly modeled totem.

You do **not** need to touch ShardTools yourself. If you would rather manage the
entry manually, set `auto-register-in-shardtools: false` and add this to
ShardTools' `config.yml` `items:` section:

```yaml
  void_totem:
    material: TOTEM_OF_UNDYING
    name: "<gradient:#7a00ff:#00e5ff>Void Totem</gradient>"
    price: 1000
    lifetime-hours: 0
    behavior: NONE
    enchants: []
    lore:
      - "<gray>A shard-forged charm that catches you"
      - "<gray>at the edge of the void and drags you back.</gray>"
      - "<dark_gray>Breaks the void once per totem.</dark_gray>"
    command: "voidtotem give %player% 1"
```

---

## The custom model (resource pack)

The totem's look comes from a **resource pack**, not the plugin. The plugin
stamps the item with the custom model id `voidtotem:void_totem` (configurable
via `item-model`). The pack maps that id to your Blockbench model, and **only**
items carrying that id use it — vanilla totems fall back to the normal model.

A ready-to-use pack is in [`VoidTotem/resourcepack/`](resourcepack/):

```
resourcepack/
  pack.mcmeta
  assets/minecraft/items/totem_of_undying.json   # routes item_model -> your model
  assets/minecraft/models/item/void_totem.json    # <- your Blockbench model goes here
  assets/minecraft/textures/item/void_totem.png   # <- your texture goes here
```

### Install the pack

1. Zip the `resourcepack/` folder (so you get `pack.zip` with `pack.mcmeta` at
   the root).
2. Either:
   - **Server-wide:** put it on a web host and set `resource-pack=<url>` in
     `server.properties` (or enable it in your proxy/hosting panel), **or**
   - **Per-player:** give `pack.zip` to players to drop into their
     `.minecraft/resourcepacks/` folder.

> The custom model only shows up **if the client has the pack loaded**. Without
> it, the totem still works (it still saves you from the void) but looks like a
> vanilla totem, because the `voidtotem:void_totem` model id is unknown.

### Drop in your Blockbench model

Put your files in the **`assets/`** staging folder — the build copies them into
the resource pack automatically, so you never touch the pack by hand:

1. In Blockbench, open your `.bbmodel`.
2. Export as **Minecraft Java Item** (`File → Export → Minecraft Java Item
   Model`) — this produces a `.json`. (If you already have the `.json`, use
   that.)
3. Save it as `assets/minecraft/models/item/void_totem.json`
   (you can keep `void_totem.bbmodel` there too for editing).
4. Export the texture(s) as PNG into `assets/minecraft/textures/item/`
   named `void_totem.png` (or whatever your model's `layer0` / texture paths
   reference).
5. Rebuild with `mvn -B clean package` — the `copy-assets-to-resourcepack`
   step folds `assets/minecraft/**` into `resourcepack/assets/minecraft/**`,
   overwriting the placeholders. Then zip `resourcepack/` and reload the pack.

If your export uses custom texture paths or extra overlays, tell me and I'll
reconcile `void_totem.json` so the names line up.

> `pack.mcmeta` uses `pack_format: 34` (the format that introduced `item_model`
> selectors). If your 26.2 client rejects it as too old/new, bump the number to
> whatever your version expects.

---

## Config (`config.yml`)

| Key | Default | Meaning |
| --- | --- | --- |
| `item-model` | `voidtotem:void_totem` | Model id stamped on the totem; must match the pack's `when` key |
| `auto-register-in-shardtools` | `true` | Auto-add the shop entry to ShardTools |
| `shop-price` | `1000` | Shard price in the ShardTools shop |
| `rescue-mode` | `LAST_SAFE` | `LAST_SAFE` / `SPAWN` / `COORDS` — where to send the rescued player |
| `rescue-world` / `rescue-x/y/z` | `""` / `0.5,64,0.5` | Used when `rescue-mode: COORDS` |
| `heal-amount` | `20.0` | Health restored after rescue (20 = full) |
| `clear-harmful-effects` | `true` | Remove poison/wither/slowness/… on rescue |
| `resistance-seconds` / `slow-falling-seconds` | `5` / `5` | Safety buffs on arrival |
| `consume-from` | `ANY` | `ANY` = works from anywhere in inventory; `HAND` = must be held |
| `play-totem-animation` / `play-totem-sound` / `spawn-particles` | `true` | Flavour on rescue |
| `particle-id` / `particle-count` | `PORTAL` / `30` | Rescue particles |
| `rescue-cooldown-seconds` | `2` | Anti double-trigger per player |

After editing, run `/vt reload` (or restart).

---

## Notes

- The rescue triggers only on **void** death (a lethal `VOID` damage hit) while
  the player carries a Void Totem. It does **not** replace the vanilla totem for
  non-void deaths.
- Folia/region-safe: all rescue work runs on the player's own region thread.
- The shop display icon in `/st shop` shows a vanilla totem (ShardTools builds it
  without the custom model); the **purchased** item is the modeled one.
