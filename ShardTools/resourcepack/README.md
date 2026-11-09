# ShardTools resource pack — Void Totem

Server resource pack that gives **only the Void Totem** (shard shop, 1,000
shards) its custom Blockbench model. Vanilla totems of undying stay vanilla:
the plugin stamps every shop-bought Void Totem with the
`minecraft:item_model` component set to `shardtools:void_totem`, and this pack
ships the item model definition that ID points at. Vanilla totems never get
that component, so they keep the client's built-in totem model.

## How it works

`minecraft:item_model` is resolved **directly** by the client: the value
`shardtools:void_totem` is looked up as an item model definition at
`assets/shardtools/items/void_totem.json` (the component value is a
`namespace:path` that maps to `assets/<namespace>/items/<path>.json`). That
definition is a plain `minecraft:model` reference to the Blockbench geometry.
If that file were missing, the client would render the **missing model**
(black/purple checkerboard) instead of falling back to the vanilla totem — so
the definition file is the whole trick.

```
pack.mcmeta                                  declared resource-format range 46 to 97.1
assets/shardtools/items/void_totem.json      resolves shardtools:void_totem -> item/void_totem
assets/minecraft/models/item/void_totem.json the Blockbench shard totem geometry
assets/minecraft/textures/item/void_totem.png the shard totem texture
```

There is intentionally **no** `assets/minecraft/items/totem_of_undying.json`
override: because the custom look is scoped by the `minecraft:item_model`
component (only shop-bought Void Totems carry it), vanilla totems need no
override to keep their vanilla model.

## Shipping it to players

Zip the contents of this folder (so that `pack.mcmeta` is at the root of the
zip) and host it somewhere reachable by players, then point your server at
it in `server.properties`:

```
resource-pack=https://your.host/shardtools-pack.zip
resource-pack-sha1=<sha1 of the zip>
```

Re-zipping after a build is enough: `mvn -B clean package` (in `ShardTools/`)
folds everything under `../assets/minecraft/` into `assets/minecraft/` here
before the jar is assembled, so the pack always matches the staged assets.
The `assets/shardtools/items/void_totem.json` definition is part of the pack
and is kept by hand (it is not under the `minecraft/` staging folder).

## Pack compatibility

The item-model definition layout was introduced in resource format 46. This
pack declares a range from 46 through **97.1** (Minecraft 26.3); format 88 is
used by 26.2. The legacy and modern range fields coexist so older clients can
read the metadata. See Mojang's [pack metadata changes](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-9)
and [26.3 technical notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3).
The declared range is not a substitute for testing the model with your actual
client and any other packs you use. Earlier copies of the ZIP declared format
34; replace the hosted ZIP and its SHA-1 if you need the updated metadata.
