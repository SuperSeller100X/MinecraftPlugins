# VoidTotem assets (staging folder)

Drop your Blockbench export here. On build, `mvn` copies everything under
`assets/minecraft/` into `resourcepack/assets/minecraft/`, overwriting the
placeholder model/texture — so the custom look ends up in the resource pack
automatically.

## Where to put what

```
assets/minecraft/models/item/void_totem.json   <-- your Blockbench "Minecraft Java Item" export
assets/minecraft/textures/item/void_totem.png  <-- your totem texture
assets/minecraft/models/item/void_totem.bbmodel <-- (optional) keep the source project here for editing
```

## Rules

- The model file **must** be named `void_totem.json` and the texture
  **must** be named `void_totem.png`. The resource pack's
  `totem_of_undying.json` routes the `voidtotem:void_totem` item model to
  `minecraft:item/void_totem` (=> `models/item/void_totem.json`), and the
  default model references `layer0: minecraft:item/void_totem`
  (=> `textures/item/void_totem.png`). Keep those names and it just works.
- If your Blockbench export uses its own texture path (e.g. `texture` instead
  of `layer0`, or extra overlay textures), tell me and I'll reconcile the
  `void_totem.json` so the paths line up.

You do **not** need to edit any Java — the plugin already stamps the item with
the `voidtotem:void_totem` model id; only the resource-pack files here define
how it looks.
