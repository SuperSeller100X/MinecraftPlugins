package org.bukkit.loot;
import java.util.Collection;
import java.util.Random;
import org.bukkit.inventory.ItemStack;
public interface LootTable { Collection<ItemStack> populateLoot(Random random, LootContext context); }
