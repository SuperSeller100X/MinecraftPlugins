package dev.superseller.combattag.combat;

/** How a combat tag was applied. */
public enum CombatCause {
    /** Direct melee hit between players. */
    MELEE,
    /** Damage caused by a player's projectile (arrow, trident, snowball, ...). */
    PROJECTILE,
    /** Damage caused by a player's splash / lingering potion. */
    POTION,
    /** Damage caused by a player's tamed pet. */
    PET,
    /** Applied manually by an administrator or another plugin. */
    ADMIN
}
