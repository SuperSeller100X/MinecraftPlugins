# ShardTools assets (staging folder)

Drop-in staging area for resource-pack assets. On build, `mvn -B clean package`
copies everything under `minecraft/` into `resourcepack/assets/minecraft/`,
overwriting whatever is there — so what you stage here ends up in the shipped
resource pack automatically.

## Where to put what

```
minecraft/models/item/void_totem.json    the Blockbench "Java Block/Item" export
minecraft/textures/item/void_totem.png   the totem texture
void_totem.bbmodel                       the Blockbench source project (not copied)
```

## How the custom model is wired up

ShardTools stamps every shop-bought Void Totem with the `minecraft:item_model`
component `shardtools:void_totem`. The client resolves that ID directly to the
item model definition `resourcepack/assets/shardtools/items/void_totem.json`,
which points at `minecraft:item/void_totem` (=> `models/item/void_totem.json`),
and that model's textures reference `minecraft:item/void_totem` (=>
`textures/item/void_totem.png`). That `shardtools/items/` definition lives in
the resource pack itself and is not generated from this staging folder.

## Rules

- The model file **must** be named `void_totem.json` and the texture **must**
  be named `void_totem.png`. Keep those names and it just works.
- If your Blockbench export points its textures at a local file (e.g.
  `"C:/Users/.../totem_of_undying"`) or uses extra overlay textures, edit the
  model JSON here so every texture path is `minecraft:item/void_totem` (or
  drop the extra PNGs next to the base texture and reference them the same
  way).

You do **not** need to edit any Java — ShardTools already stamps shop-bought
Void Totems with the `shardtools:void_totem` model id via the `item-model:`
key in `config.yml`; only these files define how it looks.
