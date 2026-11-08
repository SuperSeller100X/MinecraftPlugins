package dev.superseller.veltryx.shards;

/** What a shard item actually does in-game. */
public enum Behavior {
    /** 3x3 plane mining with amethyst-like particles (shard pickaxe). */
    AREA_MINE,
    /** Whole-tree felling on logs (max chain configurable, default 128), 3x3 on everything else (shard axe). */
    TREE_AXE,
    /** Native Lunge enchantment with 90% less Lunge exhaustion (shard spear). */
    LUNGE_SPEAR,
    /** Riptide that works without water, 5 second dry cooldown (shard trident). */
    RIPTIDE_TRIDENT,
    /** Grants Haste II when drunk (shard potion of haste - dormant extra). */
    HASTE_POTION,
    /** Rescues the holder from a lethal void hit (void totem). */
    VOID_TOTEM,
    /** Plain item, no behaviour. */
    NONE
}
