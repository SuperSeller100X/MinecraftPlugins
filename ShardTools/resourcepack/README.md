# ShardTools resource pack — Void Totem

Server resource pack that gives **only the Void Totem** (shard shop, 1000
shards) its custom Blockbench model. Vanilla totems of undying stay vanilla:
the pack routes the `minecraft:item/totem_of_undying` item through an
`item_model` selector and only items stamped with the `shardtools:void_totem`
item model (which ShardTools puts on every shop-bought Void Totem) get the
custom look.

## Contents

```
pack.mcmeta                                    pack_format 34 (item-model selectors)
assets/minecraft/items/totem_of_undying.json   routes shardtools:void_totem -> item/void_totem
assets/minecraft/models/item/void_totem.json   the Blockbench shard totem geometry
assets/minecraft/textures/item/void_totem.png  the shard totem texture
```

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

## pack_format

`pack_format: 34` is the format that introduced `item_model` selectors and
the `assets/minecraft/items/` definitions. If a future client warns that the
pack was "made for an older version", bump the number in `pack.mcmeta` to
what that version expects — no other file needs to change.
