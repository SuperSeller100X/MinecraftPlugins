# Void Totem resource pack

This pack gives the custom **Void Totem** model to the `voidtotem:void_totem`
item model id. Only VoidTotem-plugin items carry that id, so vanilla totems are
untouched.

## Files

- `pack.mcmeta` — pack metadata (bump `pack_format` if your client rejects 34)
- `assets/minecraft/items/totem_of_undying.json` — routes the `voidtotem:void_totem`
  item model to `models/item/void_totem.json`; everything else falls back to the
  vanilla totem
- `assets/minecraft/models/item/void_totem.json` — **your Blockbench model**
  (placeholder included)
- `assets/minecraft/textures/item/void_totem.png` — **your texture**
  (placeholder included)

## Use your own model

1. In Blockbench: `File → Export → Minecraft Java Item Model` → save as
   `assets/minecraft/models/item/void_totem.json` (overwrite the placeholder).
2. Export textures as PNG into `assets/minecraft/textures/item/` and make sure
   the `textures` paths inside `void_totem.json` point at them
   (the placeholder uses `layer0: minecraft:item/void_totem`).
3. Zip this folder (with `pack.mcmeta` at the root) and load it as a server or
   client resource pack.

Without this pack the totem still saves you from the void, but renders as a
vanilla totem (the `voidtotem:void_totem` model id is unknown to the client).
